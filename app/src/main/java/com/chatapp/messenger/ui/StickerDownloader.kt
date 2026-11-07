package com.chatapp.messenger.ui

import android.content.Context
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    private const val REVISION = 1

    /** true = ينزل فقط على الواي فاي أو الإنترنت غير المحدود */
    private const val UNMETERED_ONLY = false

    private const val PREFS = "sticker_auto_dl"
    private val running = AtomicBoolean(false)

    /** يزيد بعد ما تنضاف حزم جديدة، حتى تتحدث الواجهات المفتوحة */
    var version by mutableIntStateOf(0)
        private set

    fun start(context: Context) {
        val app = context.applicationContext
        val sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (sp.getInt("done_rev", 0) >= REVISION) return
        if (!running.compareAndSet(false, true)) return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                delay(3_000)   // نخلي التطبيق يفتح أول
                var attempt = 0
                while (attempt < 8) {
                    val ok = if (!networkAllowed(app)) false else runCatching { downloadAndInstall(app) }.getOrDefault(false)
                    if (ok) {
                        sp.edit().putInt("done_rev", REVISION).apply()
                        break
                    }
                    attempt++
                    delay(minOf(120_000L, 8_000L * attempt * attempt))
                }
            } finally {
                running.set(false)
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

    /** فك الضغط لمجلد مؤقت ثم نقل كل حزمة لمكانها (حتى ما تظهر حزمة ناقصة) */
    private fun install(ctx: Context, zip: File): Boolean {
        val root = TelegramStickers.root(ctx).apply { mkdirs() }
        val tmp = File(root, ".dl_tmp").apply { deleteRecursively(); mkdirs() }
        val exts = setOf("tgs", "webp", "png", "jpg", "jpeg")
        val builtIn = try { ctx.assets.list("stickers")?.toSet() ?: emptySet() } catch (_: Exception) { emptySet() }
        val packs = LinkedHashSet<String>()
        ZipInputStream(BufferedInputStream(FileInputStream(zip))).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                if (!e.isDirectory) {
                    val parts = e.name.replace('\\', '/').split('/').filter { it.isNotBlank() && it != "." }
                    val bad = parts.any { it == ".." || it.startsWith(".") || it.startsWith("__MACOSX") }
                    if (parts.size >= 2 && !bad) {
                        val file = parts.last()
                        val pack = parts[parts.size - 2]
                        val ext = file.substringAfterLast('.', "").lowercase()
                        val okName = !pack.contains(':') && !file.substringBeforeLast('.').contains(':')
                        if (ext in exts && okName && pack !in builtIn) {
                            val outDir = File(tmp, pack).apply { mkdirs() }
                            FileOutputStream(File(outDir, file)).use { zin.copyTo(it) }
                            packs += pack
                        }
                    }
                }
                zin.closeEntry()
                e = zin.nextEntry
            }
        }
        if (packs.isEmpty()) { tmp.deleteRecursively(); return false }
        for (p in packs) {
            val target = File(root, p)
            if (target.exists()) target.deleteRecursively()
            File(tmp, p).renameTo(target)
        }
        tmp.deleteRecursively()
        StickerRegistry.refresh(ctx)
        Handler(Looper.getMainLooper()).post { version++ }
        return true
    }
}
