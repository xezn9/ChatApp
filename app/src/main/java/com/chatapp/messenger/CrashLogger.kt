package com.chatapp.messenger

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * عند أي كراش: يكتب الخطأ ومكانه بالضبط (الملف + رقم السطر) بملف txt داخل مجلد التنزيلات (Downloads).
 * اسم الملف: ChatApp_crash_التاريخ.txt
 */
object CrashLogger {
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                write(app, thread, e)
            } catch (_: Throwable) {
                // لا نخلي الكتابة نفسها تسبب كراش ثاني
            }
            previous?.uncaughtException(thread, e)
        }
    }

    private fun write(ctx: Context, thread: Thread, e: Throwable) {
        val now = Date()
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(now)
        val name = "ChatApp_crash_$stamp.txt"
        val text = buildReport(ctx, thread, e, now)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                return
            }
        } else {
            try {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                File(dir, name).writeText(text)
                return
            } catch (_: Throwable) {
            }
        }
        // احتياط: لو ما نفع التنزيلات نحفظه بمجلد التطبيق الخاص
        File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, name).writeText(text)
    }

    private fun buildReport(ctx: Context, thread: Thread, e: Throwable, now: Date): String {
        val sb = StringBuilder()
        sb.append("=== تقرير كراش ChatApp ===\n")
        sb.append("الوقت: ").append(now).append('\n')
        sb.append("الجهاز: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            .append(" | أندرويد ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        try {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            sb.append("إصدار التطبيق: ").append(pi.versionName).append('\n')
        } catch (_: Throwable) {
        }
        sb.append("الـ Thread: ").append(thread.name).append("\n\n")

        // المكان بالضبط: أول سطر من كود التطبيق نفسه بالـ stack trace
        val root = generateSequence(e) { it.cause }.last()
        val where = (root.stackTrace.toList() + e.stackTrace.toList())
            .firstOrNull { it.className.startsWith("com.chatapp.messenger") }
        sb.append("الخطأ: ").append(root.javaClass.name).append(": ").append(root.message).append('\n')
        if (where != null) {
            sb.append("المكان: ").append(where.fileName).append(" سطر ").append(where.lineNumber)
                .append(" (").append(where.className).append('.').append(where.methodName).append(")\n")
        } else {
            sb.append("المكان: ما انلقى سطر من كود التطبيق (الخطأ من مكتبة/النظام) - شوف التفاصيل تحت\n")
        }
        sb.append("\n--- التفاصيل الكاملة ---\n")
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        sb.append(sw.toString())
        return sb.toString()
    }
}
