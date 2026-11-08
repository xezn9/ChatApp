package com.chatapp.messenger

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * يطلب من الـ Worker (Cloudflare، مجاني) يرسل إشعار FCM للمستلم بعد ما تنرسل الرسالة.
 * إذا PUSH_URL فاضي ما يسوي شي.
 */
object Push {
    /** رابط الـ Worker بعد النشر، مثل: https://chatapp-push.اسمك.workers.dev */
    const val PUSH_URL = "https://chatapp-push.heseneinh202.workers.dev"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun notify(to: String, msgId: String) =
        enqueue(JSONObject().put("to", to).put("msgId", msgId))

    /** رنين مكالمة: الـ Worker يتأكد إن المكالمة موجودة وحالتها ringing ثم يرسل FCM للمستلم */
    fun call(to: String, callId: String) =
        enqueue(JSONObject().put("action", "call").put("to", to).put("callId", callId))

    /** إلغاء الرنين (بعد ما المتصل يسكّر المكالمة أو تنتهي مهلتها) */
    fun callCancel(to: String, callId: String) =
        enqueue(JSONObject().put("action", "call_cancel").put("to", to).put("callId", callId))

    /** رنين مكالمة جماعية: الـ Worker يتأكد إن المكالمة فعلاً بدأت ويرسل FCM للأعضاء (على دفعات) */
    fun groupCall(gid: String) {
        if (PUSH_URL.isBlank()) return
        scope.launch {
            var offset = 0
            repeat(MAX_GROUP_ROUNDS) {
                var next = FAILED
                for (attempt in 0..1) {
                    next = runCatching {
                        postPaged(JSONObject().put("action", "gcall").put("gid", gid).put("offset", offset))
                    }.getOrDefault(FAILED)
                    if (next != FAILED) break
                    if (attempt == 0) kotlinx.coroutines.delay(2000)
                }
                if (next < 0) return@launch
                offset = next
            }
        }
    }

    private suspend fun postPaged(json: JSONObject): Int {
        val user = FirebaseAuth.getInstance().currentUser ?: return DONE
        val token = user.getIdToken(false).await().token ?: return FAILED
        val c = URL(PUSH_URL).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 8000
            c.readTimeout = 15000
            c.doOutput = true
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(json.toString().toByteArray()) }
            val code = c.responseCode
            if (code in 200..299) {
                val txt = c.inputStream.bufferedReader().use { it.readText() }
                val next = JSONObject(txt).optInt("next", DONE)
                return if (next >= 0) next else DONE
            }
            return if (code in 400..499 && code != 401) DONE else FAILED
        } finally {
            c.disconnect()
        }
    }

    private const val DONE = -1
    private const val FAILED = -2
    private const val MAX_GROUP_ROUNDS = 30

    /**
     * إشعار رسالة مجموعة. الـ Worker يرسل لعدد محدود من الأعضاء بكل طلب (حد Cloudflare المجاني)،
     * ويرجع "next" إذا بعد فيه أعضاء، فنكرر الطلب لين يخلصون.
     */
    fun notifyGroup(gid: String, msgId: String) {
        if (PUSH_URL.isBlank()) return
        scope.launch {
            var offset = 0
            repeat(MAX_GROUP_ROUNDS) {
                var next = FAILED
                for (attempt in 0..1) {
                    next = runCatching { postGroup(gid, msgId, offset) }.getOrDefault(FAILED)
                    if (next != FAILED) break
                    if (attempt == 0) kotlinx.coroutines.delay(2000)
                }
                if (next < 0) return@launch      // خلصوا، أو فشل نهائي
                offset = next
            }
        }
    }

    /** يرجع الـ offset التالي، أو DONE إذا خلصوا/رفض نهائي، أو FAILED للمحاولة مرة ثانية */
    private suspend fun postGroup(gid: String, msgId: String, offset: Int): Int {
        val user = FirebaseAuth.getInstance().currentUser ?: return DONE
        val token = user.getIdToken(false).await().token ?: return FAILED
        val c = URL(PUSH_URL).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 8000
            c.readTimeout = 15000
            c.doOutput = true
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject().put("action", "gmsg").put("gid", gid)
                .put("msgId", msgId).put("offset", offset).toString()
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            if (code in 200..299) {
                val txt = c.inputStream.bufferedReader().use { it.readText() }
                val next = JSONObject(txt).optInt("next", DONE)
                return if (next >= 0) next else DONE
            }
            return if (code in 400..499 && code != 401) DONE else FAILED
        } finally {
            c.disconnect()
        }
    }

    private fun enqueue(body: JSONObject) {
        if (PUSH_URL.isBlank()) return
        scope.launch {
            // محاولتين: إذا فشلت الأولى (نت ضعيف) نعيد بعد ثانيتين
            repeat(2) { attempt ->
                if (runCatching { post(body) }.getOrDefault(false)) return@launch
                if (attempt == 0) kotlinx.coroutines.delay(2000)
            }
        }
    }

    private suspend fun post(json: JSONObject): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return true
        val token = user.getIdToken(false).await().token ?: return false
        val c = URL(PUSH_URL).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 8000
            c.readTimeout = 10000
            c.doOutput = true
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("Content-Type", "application/json")
            val body = json.toString()
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            // 4xx (غير 401) = رفض نهائي، ما نعيد المحاولة
            return code in 200..299 || (code in 400..499 && code != 401)
        } finally {
            c.disconnect()
        }
    }
}
