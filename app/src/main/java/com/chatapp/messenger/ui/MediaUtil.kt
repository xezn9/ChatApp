package com.chatapp.messenger.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

/** تجهيز الصور والفيديوهات للإرسال بالمحادثة (ضغط + صورة مصغّرة) وحفظ الفيديو بالمعرض */
object MediaUtil {
    /** أقصى مدة فيديو: دقيقة واحدة */
    const val MAX_VIDEO_MS = 60_000L
    /** أقصى حجم للملف بعد الضغط */
    private const val MAX_BYTES = 8_000_000L
    private const val VIDEO_HEIGHT = 480
    private const val VIDEO_BITRATE = 650_000

    /** ملف جاهز للرفع: نوعه + صورة مصغّرة + الأبعاد + المدة بالثواني */
    class Prepared(val file: File, val type: String, val thumb: String, val w: Int, val h: Int, val durSec: Int)

    fun mediaDir(ctx: Context): File = File(ctx.cacheDir, "chat_media").apply { mkdirs() }

    /** مكان نسخة الملف المحلية (نفس الاسم عند المرسل والمستلم بعد التنزيل) */
    fun mediaFile(ctx: Context, id: String, type: String): File =
        File(mediaDir(ctx), id + when (type) { "video" -> ".mp4"; "audio" -> ".m4a"; else -> ".jpg" })

    private fun tmpFile(ctx: Context, ext: String) = File(mediaDir(ctx), "tmp_${System.nanoTime()}.$ext")

    /** صورة مصغّرة (أطول ضلع 240) كنص base64 */
    private fun thumbOf(bmp: Bitmap): String {
        val scale = 240f / max(bmp.width, bmp.height)
        val t = if (scale < 1f) Bitmap.createScaledBitmap(
            bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true
        ) else bmp
        val out = ByteArrayOutputStream()
        t.compress(Bitmap.CompressFormat.JPEG, 60, out)
        if (t !== bmp) t.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /** صورة: تصغير لـ 1280 وضغط JPEG. يرمي IllegalStateException برسالة عربية عند الفشل */
    suspend fun prepareImage(ctx: Context, uri: Uri): Prepared {
        val bytes = ImageUtil.uriToJpeg(ctx, uri, 1280, 78)
            ?: throw IllegalStateException("ما گدرت أفتح الصورة")
        if (bytes.size > MAX_BYTES) throw IllegalStateException("الصورة كبيرة جداً")
        return withContext(Dispatchers.IO) {
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IllegalStateException("ما گدرت أفتح الصورة")
            val f = tmpFile(ctx, "jpg").also { it.writeBytes(bytes) }
            val p = Prepared(f, "image", thumbOf(bmp), bmp.width, bmp.height, 0)
            bmp.recycle()
            p
        }
    }

    private class VideoInfo(val durMs: Long, val w: Int, val h: Int)

    /** مدة الفيديو وأبعاده كما تظهر للمستخدم (بعد حساب التدوير) */
    private fun probe(ctx: Context, uri: Uri?, file: File?): VideoInfo {
        val r = MediaMetadataRetriever()
        try {
            if (file != null) r.setDataSource(file.path) else r.setDataSource(ctx, uri)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
            return VideoInfo(dur, w, h)
        } finally {
            runCatching { r.release() }
        }
    }

    /**
     * فيديو: يتحقق أن المدة ≤ دقيقة، يضغطه (480p، H.264) ثم يطلع صورة مصغّرة.
     * [onProgress] نسبة الضغط 0..1. يرمي IllegalStateException برسالة عربية عند الفشل.
     */
    suspend fun prepareVideo(ctx: Context, uri: Uri, onProgress: (Float) -> Unit): Prepared {
        val src = withContext(Dispatchers.IO) {
            runCatching { probe(ctx, uri, null) }.getOrNull()
        } ?: throw IllegalStateException("ما گدرت أفتح الفيديو")
        if (src.durMs > MAX_VIDEO_MS + 700) throw IllegalStateException("أقصى مدة للفيديو دقيقة واحدة")
        val out = tmpFile(ctx, "mp4")
        val ok = transcode(ctx, uri, out, src.h, onProgress)
        if (!ok || !out.exists() || out.length() == 0L) {
            out.delete()
            throw IllegalStateException("تعذّر تجهيز الفيديو، جرّب فيديو ثاني")
        }
        if (out.length() > MAX_BYTES) {
            out.delete()
            throw IllegalStateException("الفيديو كبير جداً")
        }
        return withContext(Dispatchers.IO) {
            val info = runCatching { probe(ctx, null, out) }.getOrNull() ?: src
            val r = MediaMetadataRetriever()
            val thumb = try {
                r.setDataSource(out.path)
                val frame = r.getFrameAtTime(
                    minOf(500_000L, info.durMs * 500L), MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: throw IllegalStateException("تعذّر تجهيز الفيديو")
                thumbOf(frame).also { frame.recycle() }
            } finally {
                runCatching { r.release() }
            }
            Prepared(out, "video", thumb, info.w, info.h, ((info.durMs + 999) / 1000).toInt().coerceAtLeast(1))
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun transcode(ctx: Context, uri: Uri, out: File, srcH: Int, onProgress: (Float) -> Unit): Boolean =
        withContext(Dispatchers.Main) {
            val done = CompletableDeferred<Boolean>()
            val fx = ArrayList<Effect>()
            if (srcH > VIDEO_HEIGHT) fx.add(Presentation.createForHeight(VIDEO_HEIGHT))
            val item = EditedMediaItem.Builder(MediaItem.fromUri(uri))
                .setEffects(Effects(emptyList(), fx)).build()
            val enc = DefaultEncoderFactory.Builder(ctx)
                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(VIDEO_BITRATE).build())
                .build()
            val t = Transformer.Builder(ctx)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(enc)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        done.complete(true)
                    }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        done.complete(false)
                    }
                })
                .build()
            try {
                t.start(item, out.absolutePath)
                val holder = ProgressHolder()
                while (!done.isCompleted) {
                    if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                    delay(300)
                }
                done.await()
            } catch (e: CancellationException) {
                runCatching { t.cancel() }
                throw e
            } catch (e: Exception) {
                runCatching { t.cancel() }
                false
            }
        }

    /** يحفظ فيديو بمعرض الجهاز داخل Movies/ChatApp. يرجع true إذا نجح */
    suspend fun saveVideoToGallery(ctx: Context, file: File, name: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, "$name.mp4")
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ChatApp")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                    val resolver = ctx.contentResolver
                    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                        ?: return@withContext false
                    resolver.openOutputStream(uri)?.use { o -> file.inputStream().use { it.copyTo(o) } }
                        ?: return@withContext false
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    true
                } else {
                    @Suppress("DEPRECATION")
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ChatApp")
                    dir.mkdirs()
                    val f = File(dir, "$name.mp4")
                    file.copyTo(f, overwrite = true)
                    android.media.MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath), arrayOf("video/mp4"), null)
                    true
                }
            } catch (e: Exception) {
                false
            }
        }
}
