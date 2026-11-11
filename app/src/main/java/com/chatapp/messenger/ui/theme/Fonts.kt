package com.chatapp.messenger.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * خط التطبيق (iOS 16 Emojis). مو داخل الـ APK ولا المشروع:
 * ينزل مرة وحدة أول ما تفتح التطبيق، ينحفظ بذاكرة التطبيق الداخلية، ويتطبق تلقائياً.
 * قبل ما يخلص التنزيل (أو لو فشل) التطبيق يشتغل بخط النظام عادي.
 *
 * AppFont يقرأ من State، فأي Text يستخدمه يتحدّث لحاله أول ما الخط يجهز.
 * الخط إيموجي فقط، فالحروف العربية والإنجليزية تظل بخط النظام تلقائياً.
 */
object AppFontState {
    var family: FontFamily? by mutableStateOf(null)
}

val AppFont: FontFamily get() = AppFontState.family ?: FontFamily.Default

object FontManager {
    /**
     * رابط الخط على Hugging Face. إذا حدّثت الخط: ارفع الملف الجديد، غيّر الرابط،
     * وغيّر رقم v1 → v2 بـ FONT_FILE، فينزل الجديد تلقائياً ويُحذف القديم.
     */
    private const val FONT_URL =
        "https://huggingface.co/datasets/hesenin1983/ios_emoji/resolve/main/iOS%2016%20Emojis%20UNofficial.ttf"
    private const val FONT_FILE = "ios_emoji_v1.ttf"
    private const val MIN_BYTES = 1_000_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var started = false

    fun init(ctx: Context) {
        if (started) return
        started = true
        val app = ctx.applicationContext
        val dir = File(app.filesDir, "fonts").apply { mkdirs() }
        val file = File(dir, FONT_FILE)

        // خطوط قديمة (نسخ سابقة) نحذفها عشان ما تاكل مساحة
        dir.listFiles()?.forEach {
            if (it.name != file.name && !it.name.endsWith(".part")) it.delete()
        }

        if (file.exists() && apply(file)) return          // محفوظ من قبل: تطبيق فوري

        scope.launch {
            repeat(4) { attempt ->
                try {
                    download(file)
                    if (apply(file)) return@launch
                    file.delete()
                } catch (_: Exception) {
                    // نعيد المحاولة؛ الجزء المحمّل ينحفظ ونكمل منه
                }
                delay(5_000L * (attempt + 1))
            }
            // لو فشل كله: يعيد المحاولة بالتشغيل الجاي
        }
    }

    private fun apply(file: File): Boolean = try {
        if (file.length() < MIN_BYTES) false
        else {
            val tf = Typeface.createFromFile(file)
            AppFontState.family = FontFamily(tf)
            true
        }
    } catch (_: Exception) {
        file.delete()
        false
    }

    private fun download(out: File) {
        val part = File(out.parentFile, out.name + ".part")
        val resumeFrom = if (part.exists()) part.length() else 0L
        val conn = URL(FONT_URL).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            if (resumeFrom > 0) conn.setRequestProperty("Range", "bytes=$resumeFrom-")
            conn.connect()
            val code = conn.responseCode
            val append = code == 206
            if (code != 200 && code != 206) {
                if (code == 416) part.delete()               // الجزء المحفوظ تالف، نبدأ من جديد
                throw IOException("HTTP $code")
            }
            conn.inputStream.use { input ->
                java.io.FileOutputStream(part, append).use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (part.length() < MIN_BYTES) { part.delete(); throw IOException("ملف الخط ناقص") }
        if (!part.renameTo(out)) throw IOException("ما گدرت أحفظ الخط")
    }
}
