package com.chatapp.messenger.ui

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** تصغير وضغط الصور وتحويلها لنص base64 حتى تنخزن بـ Firestore بدون Firebase Storage */
object ImageUtil {
    suspend fun uriToB64(ctx: Context, uri: Uri, maxSide: Int, quality: Int): String? =
        uriToJpeg(ctx, uri, maxSide, quality)?.let { Base64.encodeToString(it, Base64.NO_WRAP) }

    /** يحفظ صورة (بايتات JPEG) بمعرض الجهاز داخل Pictures/ChatApp. يرجع true إذا نجح */
    suspend fun saveToGallery(ctx: Context, bytes: ByteArray, name: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ChatApp")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val resolver = ctx.contentResolver
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: return@withContext false
                    val out = resolver.openOutputStream(uri) ?: return@withContext false
                    out.use { it.write(bytes) }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    true
                } else {
                    @Suppress("DEPRECATION")
                    val dir = java.io.File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ChatApp"
                    )
                    dir.mkdirs()
                    val f = java.io.File(dir, "$name.jpg")
                    f.writeBytes(bytes)
                    MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath), arrayOf("image/jpeg"), null)
                    true
                }
            } catch (e: Exception) {
                false
            }
        }

    /** نفس الدالة أعلاه لكن ترجع بايتات JPEG مباشرة (للحفظ بملف محلي مثل خلفية المحادثة) */
    suspend fun uriToJpeg(ctx: Context, uri: Uri, maxSide: Int, quality: Int): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2

                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                var bmp = ctx.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                } ?: return@withContext null

                // تصحيح اتجاه الصورة (صور الكاميرا)
                val rotation = ctx.contentResolver.openInputStream(uri)?.use {
                    when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
                if (rotation != 0f) {
                    bmp = Bitmap.createBitmap(
                        bmp, 0, 0, bmp.width, bmp.height,
                        Matrix().apply { postRotate(rotation) }, true
                    )
                }

                val scale = maxSide.toFloat() / max(bmp.width, bmp.height)
                if (scale < 1f) {
                    bmp = Bitmap.createScaledBitmap(
                        bmp,
                        (bmp.width * scale).toInt().coerceAtLeast(1),
                        (bmp.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                }
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            } catch (e: Exception) {
                null
            }
        }
}

/** يفك نص base64 لصورة (خارج الخيط الرئيسي). يرجع null إذا فارغ */
@Composable
fun rememberB64Image(b64: String): ImageBitmap? {
    val img by produceState<ImageBitmap?>(null, b64) {
        value = if (b64.isBlank()) null else withContext(Dispatchers.Default) {
            runCatching {
                val bytes = Base64.decode(b64, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
    return img
}
