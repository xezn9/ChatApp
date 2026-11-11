package com.chatapp.messenger.ui

import android.content.Context
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

// ============================================================================
//  تنزيل حزم الملصقات الإضافية تلقائياً وبشكل خفي أول ما يفتح التطبيق.
//
//  - يتنزل ملف Stickers.zip من GitHub Releases مرة وحدة بالخلفية (بدون أي واجهة).
//  - إذا انقطع النت أو انغلق التطبيق نص التنزيل، يكمل من نفس المكان (Range) بالتشغيل الجاي.
//  - بعد التنزيل يتفك الضغط: كل مجلد داخل الـ zip يصير حزمة بـ files/tgpacks/<اسم المجلد>/
//    ويظهر تلقائياً بقائمة الملصقات (StickerRegistry يقرأ هذا المسار). وينحفظ بذاكرة الجهاز للأبد.
//  - بعد النجاح ينحفظ علم "done" وما ينعاد التنزيل (حتى لو المستخدم حذف حزمة).
//    إذا تريد تحدّث الحزم لاحقاً: غيّر [REVISION] وينزل من جديد مرة وحدة.
// ============================================================================
internal object StickerDownloader {
    private const val ZIP_URL = "https://github.com/xezn9/Noway/releases/download/Stickers/Stickers.zip"

    /** غيّر الرقم لما ترفع نسخة جديدة من الملف حتى ينزلها الكل مرة ثانية */
    private const val REVISION = 2

    /** true = ينزل فقط على الواي فاي أو الإنترنت غير المحدود */
    private const val UNMETERED_ONLY = false

    private const val PREFS = "sticker_auto_dl"
    private val running = AtomicBoolean(false)

    /** يزيد بعد ما تنضاف حزم جديدة، حتى تتحدث الواجهات المفتوحة */
    var version by mutableIntStateOf(0)
        private set

    /** جاري التنزيل؟ وهل فشل آخر محاولة؟ (للوحة الملصقات) */
    var busy by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set

    /** إعادة محاولة فورية (زر "إعادة المحاولة" أو أول فتح للوحة بدون ملصقات) */
    fun retry(context: Context) {
        val app = context.applicationContext
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("done_rev", 0).apply()
        start(app, immediate = true)
    }

    fun start(context: Context, immediate: Boolean = false) {
        val app = context.applicationContext
        val sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (sp.getInt("done_rev", 0) >= REVISION) return
        if (!running.compareAndSet(false, true)) return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            Handler(Looper.getMainLooper()).post { busy = true; failed = false }
            try {
                if (!immediate) delay(3_000)   // نخلي التطبيق يفتح أول
                var attempt = 0
                while (attempt < 8) {
                    val ok = if (!networkAllowed(app)) false else runCatching { downloadAndInstall(app) }.getOrDefault(false)
                    if (ok) {
                        sp.edit().putInt("done_rev", REVISION).apply()
                        break
                    }
                    attempt++
                    if (immediate && attempt >= 2) break   // فتح اللوحة: نحاول مرتين بس ونظهر زر إعادة المحاولة
                    delay(minOf(120_000L, 8_000L * attempt * attempt))
                }
            } finally {
                val done = sp.getInt("done_rev", 0) >= REVISION
                running.set(false)
                Handler(Looper.getMainLooper()).post { busy = false; failed = !done }
            }
        }
    }

    private fun networkAllowed(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        if (cm.activeNetworkInfo?.isConnected != true) return false
        return !(UNMETERED_ONLY && cm.isActiveNetworkMetered)
    }

    private fun downloadAndInstall(ctx: Context): Boolean {
        val dir = File(ctx.filesDir, "stickers_dl").apply { mkdirs() }
        val part = File(dir, "Stickers.zip.part")
        val zip = File(dir, "Stickers.zip")
        if (!zip.exists()) {
            download(part)
            zip.delete()
            if (!part.renameTo(zip)) return false
        }
        val ok = try { install(ctx, zip) } catch (_: Exception) { false }
        // ملف تالف: نحذفه حتى ينعاد تنزيله من الصفر
        zip.delete()
        part.delete()
        return ok
    }

    /** تنزيل مع استئناف: إذا الملف الجزئي موجود نكمل منه */
    private fun download(part: File) {
        val existing = if (part.exists()) part.length() else 0L
        val c = URL(ZIP_URL).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 20_000
            c.readTimeout = 30_000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "ChatApp")
            if (existing > 0) c.setRequestProperty("Range", "bytes=$existing-")
            val code = c.responseCode
            if (code == 416) return                                 // الملف مكتمل من قبل
            if (code != 200 && code != 206) throw IOException("HTTP $code")
            FileOutputStream(part, code == 206).use { out ->
                c.inputStream.use { it.copyTo(out, 64 * 1024) }
            }
        } finally {
            c.disconnect()
        }
    }

    /**
     * فك الضغط: كل ملصقات الزيب (حتى لو داخل مجلدات) تنحط ببساطة بمجلد واحد files/chat_stickers/
     * وتصير "ملصقات الدردشة" فقط. ما تظهر برموز الاسم ولا كأقسام.
     */
    private fun install(ctx: Context, zip: File): Boolean {
        val target = File(ctx.filesDir, "chat_stickers")
        val tmp = File(ctx.filesDir, "chat_stickers_tmp").apply { deleteRecursively(); mkdirs() }
        val exts = setOf("tgs", "webp", "png", "jpg", "jpeg")
        var count = 0
        ZipInputStream(BufferedInputStream(FileInputStream(zip))).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                if (!e.isDirectory) {
                    val parts = e.name.replace('\\', '/').split('/').filter { it.isNotBlank() && it != "." }
                    val bad = parts.any { it == ".." || it.startsWith(".") || it.startsWith("__MACOSX") }
                    if (parts.isNotEmpty() && !bad) {
                        val file = parts.last()
                        val ext = file.substringAfterLast('.', "").lowercase()
                        if (ext in exts) {
                            // اسم نظيف بدون ":" أو فراغات (لأن المعرّف s:chat:<الاسم>)
                            fun clean(x: String) = x.replace(Regex("[:\\s/]+"), "_")
                            var base = clean(file.substringBeforeLast('.'))
                            if (base.isBlank()) base = "s${count}"
                            var out = File(tmp, "$base.$ext")
                            if (out.exists() || File(tmp, "$base.tgs").exists() || File(tmp, "$base.webp").exists() ||
                                File(tmp, "$base.png").exists() || File(tmp, "$base.jpg").exists() || File(tmp, "$base.jpeg").exists()) {
                                val folder = if (parts.size >= 2) clean(parts[parts.size - 2]) else "x"
                                base = "${folder}_$base"
                                out = File(tmp, "$base.$ext")
                            }
                            if (!out.exists()) {
                                FileOutputStream(out).use { zin.copyTo(it) }
                                count++
                            }
                        }
                    }
                }
                zin.closeEntry()
                e = zin.nextEntry
            }
        }
        if (count == 0) { tmp.deleteRecursively(); return false }
        if (target.exists()) target.deleteRecursively()
        if (!tmp.renameTo(target)) { tmp.deleteRecursively(); return false }
        // تنظيف: الحزم اللي نزلت بالنسخة القديمة من نفس الزيب (كرموز اسم) تنشال، وحزم تيليجرام (tg_) تبقى
        TelegramStickers.root(ctx).listFiles()?.filter { it.isDirectory && !it.name.startsWith("tg_") }?.forEach { it.deleteRecursively() }
        StickerRegistry.refresh(ctx)
        Handler(Looper.getMainLooper()).post { version++ }
        return true
    }
}
