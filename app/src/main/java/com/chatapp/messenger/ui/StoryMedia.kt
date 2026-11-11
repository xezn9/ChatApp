package com.chatapp.messenger.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

/** أدوات وسائط القصص: صوت من المعرض (من فيديو أو ملف صوت) + قص الصوت + صورة غلاف للفيديو */
object StoryMedia {
    /** أقصى مدة للقصة (فيديو أو صوت) بالثواني */
    const val MAX_SECONDS = 60

    /** بادئة أغنية مرفوعة مع القصة: story:<uid>/<id> */
    const val AUDIO_PREFIX = "story:"

    class AudioInfo(val durSec: Int, val hasAudio: Boolean)

    /** مدة الملف (صوت أو فيديو) وهل فيه صوت */
    suspend fun probeAudio(ctx: Context, uri: Uri): AudioInfo? = withContext(Dispatchers.IO) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val has = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            AudioInfo(((ms + 999) / 1000).toInt(), has)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    /** اسم الملف بدون الامتداد (يصير عنوان الأغنية) */
    fun displayName(ctx: Context, uri: Uri): String {
        val raw = runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: ""
        val n = raw.substringBeforeLast('.', raw).trim()
        return n.ifBlank { "صوت من المعرض" }
    }

    /** صورة غلاف (لقطة من الفيديو) بحجم مناسب للقصة كنص base64 */
    suspend fun posterOf(file: File, maxSide: Int = 720): String = withContext(Dispatchers.IO) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.path)
            val frame = r.getFrameAtTime(300_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: r.getFrameAtTime(0L) ?: throw IllegalStateException("تعذّر قراءة الفيديو")
            val scale = maxSide.toFloat() / max(frame.width, frame.height)
            val b = if (scale < 1f) Bitmap.createScaledBitmap(
                frame, (frame.width * scale).toInt().coerceAtLeast(1), (frame.height * scale).toInt().coerceAtLeast(1), true
            ) else frame
            val out = ByteArrayOutputStream()
            b.compress(Bitmap.CompressFormat.JPEG, 70, out)
            if (b !== frame) b.recycle()
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } finally {
            runCatching { r.release() }
        }
    }

    /**
     * ياخذ الصوت فقط من [uri] (فيديو أو ملف صوت) ويقص الجزء [startMs, endMs] بصيغة AAC/m4a.
     * يرجع true إذا نجح.
     */
    @OptIn(UnstableApi::class)
    suspend fun trimAudio(ctx: Context, uri: Uri, startMs: Long, endMs: Long, out: File, onProgress: (Float) -> Unit): Boolean =
        withContext(Dispatchers.Main) {
            val done = CompletableDeferred<Boolean>()
            val clip = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs).setEndPositionMs(endMs).build()
            val mi = MediaItem.Builder().setUri(uri).setClippingConfiguration(clip).build()
            val item = EditedMediaItem.Builder(mi).setRemoveVideo(true).build()
            val t = Transformer.Builder(ctx)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
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
                if (out.exists()) out.delete()
                t.start(item, out.absolutePath)
                val holder = ProgressHolder()
                while (!done.isCompleted) {
                    if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                    delay(200)
                }
                done.await() && out.exists() && out.length() > 0L
            } catch (e: CancellationException) {
                runCatching { t.cancel() }
                throw e
            } catch (e: Exception) {
                runCatching { t.cancel() }
                false
            }
        }
}
