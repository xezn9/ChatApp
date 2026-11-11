package com.chatapp.messenger

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * يطلب من الـ Worker (Cloudflare، مجاني) يرسل إشعار FCM للمستلم بعد ما تنرسل الرسالة.
 * إذا PUSH_URL فاضي ما يسوي شي.
 */
object Push {
    /** رابط الـ Worker بعد النشر، مثل: https://chatapp-push.اسمك.workers.dev */
    const val PUSH_URL = "https://chatapp-push.heseneinh202.workers.dev"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** الرسائل الي وصل إشعارها فعلاً من هذا الجهاز (حتى ما يكررها الـ Worker الاحتياطي) */
    private val sent = ConcurrentHashMap.newKeySet<String>()

    internal fun alreadySent(key: String) = sent.contains(key)
    internal fun markSent(key: String) { if (sent.size > 500) sent.clear(); sent.add(key) }

    /**
     * طلب إشعار رسالة خاصة. محاولة فورية (سريعة)، + مهمة WorkManager احتياطية تضمن الإرسال
     * حتى لو انقتلت العملية أو سكّرت التطبيق أو النت ضعيف بهاللحظة (تعيد المحاولة لين يرجع النت).
     */
    fun notify(to: String, msgId: String) {
        enqueue(JSONObject().put("to", to).put("msgId", msgId), "msg:$msgId")
        durable("msg", to, msgId)
    }

    private fun durable(kind: String, target: String, msgId: String) {
        val ctx = runCatching { FirebaseApp.getInstance().applicationContext }.getOrNull() ?: return
        runCatching {
            val req = OneTimeWorkRequestBuilder<PushWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(8, TimeUnit.SECONDS)   // نعطي المحاولة الفورية فرصة أول
                .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.SECONDS)
                .setInputData(
                    workDataOf(
                        "kind" to kind, "target" to target, "msgId" to msgId,
                        "at" to System.currentTimeMillis()
                    )
                )
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("push:$kind:$msgId", ExistingWorkPolicy.KEEP, req)
        }
    }

    /** يستدعيها PushWorker: إرسال طلب إشعار رسالة خاصة (true = تم أو رفض نهائي) */
    internal suspend fun sendOne(to: String, msgId: String): Boolean =
        post(JSONObject().put("to", to).put("msgId", msgId))

    /** يستدعيها PushWorker: إشعار رسالة مجموعة لكل الأعضاء (على دفعات). true = خلصوا */
    internal suspend fun sendGroupAll(gid: String, msgId: String): Boolean {
        var offset = 0
        repeat(MAX_GROUP_ROUNDS) {
            val next = runCatching { postGroup(gid, msgId, offset) }.getOrDefault(FAILED)
            if (next == FAILED) return false
            if (next < 0) return true
            offset = next
        }
        return true
    }

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
        durable("gmsg", gid, msgId)
        scope.launch {
            var offset = 0
            repeat(MAX_GROUP_ROUNDS) {
                var next = FAILED
                for (attempt in 0..1) {
                    next = runCatching { postGroup(gid, msgId, offset) }.getOrDefault(FAILED)
                    if (next != FAILED) break
                    if (attempt == 0) kotlinx.coroutines.delay(2000)
                }
                if (next < 0) {                  // خلصوا، أو فشل نهائي
                    if (next == DONE) markSent("gmsg:$msgId")
                    return@launch
                }
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

    private fun enqueue(body: JSONObject, sentKey: String? = null) {
        if (PUSH_URL.isBlank()) return
        scope.launch {
            // 3 محاولات (2 ثانية ثم 5 ثواني): نت ضعيف أو خطأ مؤقت من السيرفر ما لازم يضيّع الإشعار
            repeat(3) { attempt ->
                if (runCatching { post(body) }.getOrDefault(false)) {
                    sentKey?.let { markSent(it) }
                    return@launch
                }
                if (attempt < 2) kotlinx.coroutines.delay(if (attempt == 0) 2000L else 5000L)
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


/**
 * احتياطي مضمون لإرسال طلب الإشعار: يشتغل حتى لو العملية انقتلت بعد إرسال الرسالة،
 * وينتظر النت إذا مقطوع، ويعيد المحاولة تلقائياً. إذا المحاولة الفورية نجحت ما يسوي شي.
 */
class PushWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val kind = inputData.getString("kind") ?: return Result.success()
        val target = inputData.getString("target") ?: return Result.success()
        val msgId = inputData.getString("msgId") ?: return Result.success()
        val at = inputData.getLong("at", 0L)
        // إشعار قديم أكثر من 10 دقايق ما له فايدة
        if (at > 0 && System.currentTimeMillis() - at > 10 * 60_000L) return Result.success()
        val key = "$kind:$msgId"
        if (Push.alreadySent(key)) return Result.success()
        val ok = runCatching {
            if (kind == "gmsg") Push.sendGroupAll(target, msgId) else Push.sendOne(target, msgId)
        }.getOrDefault(false)
        if (ok) {
            Push.markSent(key)
            return Result.success()
        }
        return if (runAttemptCount < 6) Result.retry() else Result.success()
    }
}
