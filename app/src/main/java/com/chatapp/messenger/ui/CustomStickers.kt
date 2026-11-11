package com.chatapp.messenger.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

// ============================================================================
//  ملصقات المستخدم (من صوره):
//  - تنحفظ محلياً بمجلد my_stickers (WebP شفاف)، وتظهر بقسم "ملصقاتي" بلوحة الملصقات
//  - تنرسل كرسالة صورة صغيرة (mediaType = "image") ونصها علامة MARK، والصورة نفسها بحقل thumb
//    (حتى ما نحتاج أي تعديل بقواعد قاعدة البيانات ولا رفع ملفات)، وتنعرض بدون فقاعة مثل الملصقات العادية
// ============================================================================
internal object CustomStickers {
    /** نص الرسالة: يطابق معاينة ChatSticker حتى تطلع "🎭 ملصق" بالقوائم والإشعارات والردود */
    const val MARK = "🎭 ملصق"

    /** أقصى حجم للصورة المشفّرة (بايت). القاعدة تسمح 40000 حرف base64 ≈ 30000 بايت */
    private const val MAX_BYTES = 27_000
    private const val MAX_SIDE = 256

    /** يزيد عند إضافة/حذف ملصق حتى تنعاد رسم القائمة */
    val version = mutableIntStateOf(0)

    fun isCustom(mediaType: String, text: String, thumb: String): Boolean =
        mediaType == "image" && text == MARK && thumb.isNotEmpty()

    private fun dir(ctx: Context) = File(ctx.filesDir, "my_stickers").also { it.mkdirs() }

    fun list(ctx: Context): List<File> =
        dir(ctx).listFiles { f -> f.extension == "webp" }?.sortedByDescending { it.lastModified() } ?: emptyList()

    /** يضغط الصورة لحد ما يصغر حجمها عن الحد (ينزّل الجودة ثم الأبعاد) */
    private fun encode(src: Bitmap): ByteArray {
        var side = MAX_SIDE
        var last = ByteArray(0)
        while (side >= 96) {
            val scale = side.toFloat() / maxOf(src.width, src.height)
            val bmp = if (scale < 1f) Bitmap.createScaledBitmap(
                src, (src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1), true
            ) else src
            for (q in intArrayOf(85, 72, 58, 45)) {
                val out = ByteArrayOutputStream()
                @Suppress("DEPRECATION")
                val fmt = if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                bmp.compress(fmt, q, out)
                last = out.toByteArray()
                if (last.size <= MAX_BYTES) {
                    if (bmp !== src) bmp.recycle()
                    return last
                }
            }
            if (bmp !== src) bmp.recycle()
            side -= 40
        }
        return last
    }

    /** يحفظ الملصق الجديد ويرجع ملفه (null إذا فشل) */
    suspend fun save(ctx: Context, bmp: Bitmap): File? = withContext(Dispatchers.IO) {
        try {
            val bytes = encode(bmp)
            if (bytes.isEmpty() || bytes.size > MAX_BYTES + 3000) return@withContext null
            val f = File(dir(ctx), "stk_${System.currentTimeMillis()}.webp")
            f.writeBytes(bytes)
            f
        } catch (e: Exception) { null }
    }

    /** يحفظ ملصق وصلني (من رسالة) بملصقاتي. يرجع: 1 انحفظ، 0 موجود من قبل، -1 فشل */
    fun saveFromB64(ctx: Context, b64: String): Int = try {
        val bytes = Base64.decode(b64, Base64.NO_WRAP)
        val f = File(dir(ctx), "stk_r${b64.hashCode().toUInt()}_${b64.length}.webp")
        if (f.exists()) 0 else { f.writeBytes(bytes); version.intValue++; 1 }
    } catch (e: Exception) { -1 }

    fun delete(file: File) {
        runCatching { file.delete() }
        version.intValue++
    }

    /** (base64، العرض، الارتفاع) لإرسال الملصق */
    suspend fun readForSend(file: File): Triple<String, Int, Int>? = withContext(Dispatchers.IO) {
        try {
            val bytes = file.readBytes()
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            if (b64.length > 39_000) null else Triple(b64, o.outWidth.coerceAtLeast(1), o.outHeight.coerceAtLeast(1))
        } catch (e: Exception) { null }
    }

    private val cache = object : LruCache<String, ImageBitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    /** من الكاش فقط (بدون فك) حتى ما يرمش الملصق عند إعادة الرسم */
    fun decodeB64Cached(b64: String): ImageBitmap? = cache.get(b64)

    fun decodeB64(b64: String): ImageBitmap? {
        cache.get(b64)?.let { return it }
        return try {
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()?.also { cache.put(b64, it) }
        } catch (e: Exception) { null }
    }

    fun decodeFile(file: File): ImageBitmap? {
        val key = file.absolutePath + file.lastModified()
        cache.get(key)?.let { return it }
        return BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()?.also { cache.put(key, it) }
    }
}

/** ملصق مستخدم داخل المحادثة (من حقل thumb بالرسالة) */
@Composable
internal fun CustomStickerImage(b64: String, size: Dp = 140.dp, modifier: Modifier = Modifier) {
    val img by produceState<ImageBitmap?>(CustomStickers.decodeB64Cached(b64), b64) {
        value = withContext(Dispatchers.Default) { CustomStickers.decodeB64(b64) }
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        img?.let { Image(it, null, Modifier.size(size), contentScale = ContentScale.Fit) }
    }
}

/** ملصق مستخدم من ملف محلي (لوحة "ملصقاتي") */
@Composable
internal fun CustomStickerFileImage(file: File, size: Dp, modifier: Modifier = Modifier) {
    val img by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { CustomStickers.decodeFile(file) }
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        img?.let { Image(it, null, Modifier.size(size), contentScale = ContentScale.Fit) }
    }
}

/** يظهر فوق الملصق لما تضغط عليه بالمحادثة: زر حفظه بملصقاتي */
@Composable
internal fun SaveStickerPopup(b64: String, onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.ui.window.Popup(alignment = Alignment.Center, onDismissRequest = onDismiss) {
        Box(
            Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                .background(androidx.compose.ui.graphics.Color(0xFF4DA3FF))
                .clickable {
                    when (CustomStickers.saveFromB64(ctx, b64)) {
                        1 -> AppToast.show("انضاف الملصق لـ «ملصقاتي»")
                        0 -> AppToast.show("هذا الملصق موجود بملصقاتي")
                        else -> AppToast.show("تعذّر حفظ الملصق")
                    }
                    onDismiss()
                }
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            androidx.compose.material3.Text(
                "＋ حفظ بملصقاتي", color = androidx.compose.ui.graphics.Color.White,
                fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
        }
    }
}
