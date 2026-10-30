package com.chatapp.messenger

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Saves uncaught app crashes as readable TXT files in the public Downloads folder.
 * The original Android crash handler is always called afterwards so normal crash
 * reporting / process termination behavior is preserved.
 */
object CrashLogger {
    private const val TAG = "ChatAppCrashLogger"
    private val handlingCrash = AtomicBoolean(false)

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (handlingCrash.compareAndSet(false, true)) {
                try {
                    saveCrashReport(appContext, thread, throwable)
                } catch (writeError: Throwable) {
                    Log.e(TAG, "Could not save crash report", writeError)
                }
            }

            // Preserve Android's normal crash behavior after writing the report.
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    private fun saveCrashReport(context: Context, thread: Thread, error: Throwable) {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val fileName = "ChatApp_Crash_$stamp.txt"
        val report = buildString {
            appendLine("ChatApp - Crash Report")
            appendLine("======================")
            appendLine("Date: ${Date()}")
            appendLine("Thread: ${thread.name} (id=${thread.id})")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("App version: ${appVersion(context)}")
            appendLine()
            appendLine("Exception:")
            appendLine(error.stackTraceToString())
            var cause = error.cause
            var depth = 1
            while (cause != null && depth <= 10) {
                appendLine()
                appendLine("Caused by #$depth:")
                appendLine(cause.stackTraceToString())
                cause = cause.cause
                depth++
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ChatAppCrashLogs")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Downloads provider did not create a file")
            try {
                resolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use {
                    it.write(report)
                } ?: throw IllegalStateException("Could not open crash report output stream")
                val completed = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(uri, completed, null, null)
            } catch (e: Throwable) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            // Legacy Android fallback. WRITE_EXTERNAL_STORAGE is declared in the manifest.
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val folder = File(downloads, "ChatAppCrashLogs")
            if (!folder.exists() && !folder.mkdirs()) {
                throw IllegalStateException("Could not create Downloads/ChatAppCrashLogs")
            }
            File(folder, fileName).writeText(report, Charsets.UTF_8)
        }

        Log.e(TAG, "Crash report saved: Downloads/ChatAppCrashLogs/$fileName")
    }

    private fun appVersion(context: Context): String {
        return try {
            val info = if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            "${info.versionName} (${info.longVersionCode})"
        } catch (_: Exception) {
            "unknown"
        }
    }
}
