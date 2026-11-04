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
