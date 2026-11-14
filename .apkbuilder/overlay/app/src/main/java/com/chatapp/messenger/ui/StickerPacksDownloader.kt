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
//  حزم ملصقات رموز الاسم (crayons / hemoni / nnetfiix ...): كانت داخل assets (حوالي 13MB تبطّأ البناء)،
//  هسه تنزل مرة وحدة من GitHub Releases أول ما يفتح التطبيق وتنحفظ بذاكرة الجهاز:
//      files/sticker_packs/<اسم المجلد>/<ملفات الملصقات>
//  - كل مجلد داخل الـ zip = قسم (حتى لو الـ zip فيه مجلد خارجي يلفّ الأقسام: نستعمل المجلد المباشر للملف).
//  - أسماء الملفات تبقى مثل ما هي بالضبط (معرّف الرمز s:<قسم>:<اسم> المحفوظ عند المستخدمين يبقى شغال).
//  - التنزيل يكمل من نفس المكان (Range) إذا انقطع النت أو انغلق التطبيق، ويعيد المحاولة تلقائياً.
//  - بعد النجاح ينحفظ علم "done" وما ينعاد التنزيل. إذا رفعت نسخة جديدة من الملف غيّر [REVISION].
//  (منفصل عن [StickerDownloader] الي يخص "ملصقات الدردشة".)
// ============================================================================
internal object StickerPacksDownloader {
    private const val ZIP_URL = "https://github.com/xezn9/Noway/releases/download/Stickers/stickers.zip"

    /** غيّر الرقم لما ترفع نسخة جديدة من الملف حتى ينزلها الكل مرة ثانية */
    private const val REVISION = 1

    private const val PREFS = "sticker_packs_dl"
    private val EXTS = setOf("tgs", "webp", "png", "jpg", "jpeg")
    private val running = AtomicBoolean(false)

    /** مجلد الحزم المنزّلة (يقرأه StickerRegistry) */
    fun root(ctx: Context) = File(ctx.applicationContext.filesDir, "sticker_packs")

    /** يزيد بعد ما تنضاف الحزم، حتى تتحدث الواجهات المفتوحة */
    var version by mutableIntStateOf(0)
        private set
    var busy by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set

    /** إعادة محاولة فورية (من لوحة الرموز إذا فاضية أو فشل التنزيل) */
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
                if (!immediate) delay(1_500)   // نخلي التطبيق يفتح أول
                var attempt = 0
                while (attempt < 8) {
                    val ok = if (!networkAvailable(app)) false else runCatching { downloadAndInstall(app) }.getOrDefault(false)
                    if (ok) {
                        sp.edit().putInt("done_rev", REVISION).apply()
                        break
                    }
                    attempt++
                    if (immediate && attempt >= 2) break   // فتح اللوحة: محاولتين بس ونظهر زر إعادة المحاولة
                    delay(minOf(120_000L, 8_000L * attempt * attempt))
                }
            } finally {
                val done = sp.getInt("done_rev", 0) >= REVISION
                running.set(false)
                Handler(Looper.getMainLooper()).post { busy = false; failed = !done }
            }
        }
    }

    private fun networkAvailable(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        @Suppress("DEPRECATION")
        return cm.activeNetworkInfo?.isConnected == true
    }

    private fun downloadAndInstall(ctx: Context): Boolean {
        val dir = File(ctx.filesDir, "sticker_packs_dl").apply { mkdirs() }
        val part = File(dir, "stickers.zip.part")
        val zip = File(dir, "stickers.zip")
        if (!zip.exists()) {
            download(part)
            zip.delete()
            if (!part.renameTo(zip)) return false
        }
        val ok = try { install(ctx, zip) } catch (_: Exception) { false }
        // الملف انفتح (نجح أو تالف): نحذفه حتى ما يبقى 13MB زيادة، والتالف ينعاد تنزيله من الصفر
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
     * فك الضغط: المجلد المباشر الي بيه الملف = اسم القسم، واسم الملف يبقى حرفياً.
     * نكتب بمجلد مؤقت ثم نبدّل المجلد كله مرة وحدة، حتى لو انقطع فك الضغط ما تتخرب الحزم الحالية.
     */
    private fun install(ctx: Context, zip: File): Boolean {
        val tmp = File(ctx.filesDir, "sticker_packs_tmp").apply { deleteRecursively(); mkdirs() }
        var count = 0
        ZipInputStream(BufferedInputStream(FileInputStream(zip))).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                if (!e.isDirectory) {
                    val parts = e.name.replace('\\', '/').split('/').filter { it.isNotBlank() && it != "." }
                    val bad = parts.any { it == ".." || it.startsWith(".") || it.startsWith("__MACOSX") }
                    if (parts.size >= 2 && !bad) {
                        val file = parts.last()
                        val ext = file.substringAfterLast('.', "").lowercase()
                        val pack = parts[parts.size - 2].replace(Regex("[:\\s]+"), "_").lowercase()
                        if (ext in EXTS && !file.contains(':') && pack.isNotBlank() && pack != CHAT_PACK_ID) {
                            val packDir = File(tmp, pack).apply { mkdirs() }
                            val out = File(packDir, file)
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
        val target = root(ctx)
        if (target.exists()) target.deleteRecursively()
        if (!tmp.renameTo(target)) { tmp.deleteRecursively(); return false }
        StickerRegistry.refresh(ctx)
        Handler(Looper.getMainLooper()).post { version++ }
        return true
    }
}
