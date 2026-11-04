package com.chatapp.messenger.ui

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import coil.request.repeatCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** نتيجة قراءة/نسخ ملف GIF من المعرض */
sealed class GifRead {
    class Ok(val bytes: ByteArray) : GifRead()
    object Saved : GifRead()
    object TooBig : GifRead()
    object Failed : GifRead()
}

/** أدوات الـ GIF: كشف النوع، قراءة بحد أقصى للحجم، وتخزين مؤقت للعرض */
object GifUtil {
    /**
     * أقصى حجم لـ GIF البانر. البانر يتخزن base64 داخل وثيقة Firestore (حدها 1MB للوثيقة كلها)
     * و base64 يكبّر الحجم 33%، فنخلّي الحد 500KB حتى ما تتجاوز الوثيقة الحد مع الصورة الشخصية والنبذة.
     */
    const val MAX_BANNER_BYTES = 500_000

    /** أقصى حجم لـ GIF خلفية المحادثة (محلي بالجهاز فقط، فالحد أوسع) */
    const val MAX_BG_BYTES = 12_000_000

    /** "GIF8" بصيغة base64 تبدأ دائماً بـ R0lGOD (GIF87a و GIF89a) */
    fun isGifB64(b64: String): Boolean = b64.startsWith("R0lGOD")

    private fun hasGifHeader(h: ByteArray, n: Int): Boolean =
        n >= 4 && h[0] == 'G'.code.toByte() && h[1] == 'I'.code.toByte() &&
            h[2] == 'F'.code.toByte() && h[3] == '8'.code.toByte()

    fun isGifBytes(b: ByteArray): Boolean = hasGifHeader(b, b.size)

    /** هل الملف GIF؟ (نقرأ أول 4 بايتات فقط، لا نعتمد على الامتداد) */
    fun isGifFile(f: File): Boolean = try {
        f.inputStream().use { s ->
            val h = ByteArray(4)
            hasGifHeader(h, s.read(h))
        }
    } catch (e: Exception) { false }

    /** هل الملف المختار من المعرض GIF؟ (استدعيها من خيط IO) */
    fun isGifUri(ctx: Context, uri: Uri): Boolean = try {
        if (ctx.contentResolver.getType(uri) == "image/gif") true
        else ctx.contentResolver.openInputStream(uri)?.use { s ->
            val h = ByteArray(4)
            hasGifHeader(h, s.read(h))
        } ?: false
    } catch (e: Exception) { false }

    /** يقرأ الـ GIF كاملاً بالذاكرة، وإذا تجاوز [limit] يرجع TooBig بدون ما يكمل القراءة */
    suspend fun readLimited(ctx: Context, uri: Uri, limit: Int): GifRead = withContext(Dispatchers.IO) {
        try {
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0
            val s = ctx.contentResolver.openInputStream(uri) ?: return@withContext GifRead.Failed
            s.use {
                while (true) {
                    val n = it.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > limit) return@withContext GifRead.TooBig
                    out.write(buf, 0, n)
                }
            }
            if (total == 0) GifRead.Failed else GifRead.Ok(out.toByteArray())
        } catch (e: Exception) { GifRead.Failed }
    }

    /** ينسخ الـ GIF لملف [dest] بحد أقصى [limit] (عبر ملف مؤقت حتى ما يتخرب الملف القديم لو فشل النسخ) */
    suspend fun copyLimited(ctx: Context, uri: Uri, dest: File, limit: Int): GifRead = withContext(Dispatchers.IO) {
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        try {
            val buf = ByteArray(32 * 1024)
            var total = 0
            val s = ctx.contentResolver.openInputStream(uri) ?: return@withContext GifRead.Failed
            s.use { input ->
                FileOutputStream(tmp).use { output ->
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > limit) { tmp.delete(); return@withContext GifRead.TooBig }
                        output.write(buf, 0, n)
                    }
                }
            }
            if (total == 0) { tmp.delete(); return@withContext GifRead.Failed }
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) { tmp.delete(); return@withContext GifRead.Failed }
            GifRead.Saved
        } catch (e: Exception) {
            tmp.delete()
            GifRead.Failed
        }
    }

    /** ملف مؤقت لـ GIF محفوظ كنص base64 (Coil يقرأ من ملف). يُنشأ مرة وحدة لكل صورة. استدعيها من خيط IO */
    fun fileForB64(ctx: Context, b64: String): File {
        val dir = File(ctx.cacheDir, "gif_banners").apply { mkdirs() }
        val f = File(dir, "b_${b64.length}_${b64.hashCode()}_${b64.takeLast(24).hashCode()}.gif")
        if (!f.exists() || f.length() == 0L) {
            val tmp = File(dir, f.name + ".tmp")
            tmp.writeBytes(Base64.decode(b64, Base64.NO_WRAP))
            if (!tmp.renameTo(f)) { tmp.delete(); throw java.io.IOException("cache write failed") }
            trimCache(dir)
        }
        return f
    }

    /** يمنع الكاش من التضخم: إذا تجاوز 30MB نحذف الأقدم حتى ينزل لـ 20MB */
    private fun trimCache(dir: File) {
        val files = dir.listFiles() ?: return
        var total = files.sumOf { it.length() }
        if (total <= 30_000_000L) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= 20_000_000L) break
            total -= f.length()
            f.delete()
        }
    }
}

/**
 * يحفظ صورة خلفية محادثة اختارها المستخدم من المعرض بالملف [dest].
 * GIF يُحفظ كما هو (حتى يبقى متحرك)، وغيره يتحول JPEG مثل قبل.
 * يرجع نص الخطأ للعرض بـ AppToast، أو null إذا نجح.
 *
 * ملاحظة: الملف يبقى بنفس الاسم (.jpg) لتبقى خلفيات المستخدمين القديمة شغالة؛
 * العارض يعرف النوع من محتوى الملف مو من الامتداد.
 */
suspend fun saveChatBackgroundFromUri(ctx: Context, uri: Uri, dest: File): String? {
    val gif = withContext(Dispatchers.IO) { GifUtil.isGifUri(ctx, uri) }
    if (gif) {
        return when (GifUtil.copyLimited(ctx, uri, dest, GifUtil.MAX_BG_BYTES)) {
            is GifRead.Saved -> null
            is GifRead.TooBig -> "ملف الـ GIF كبير، الحد الأقصى 12 ميگا"
            else -> "ما گدرت أفتح الصورة"
        }
    }
    val bytes = ImageUtil.uriToJpeg(ctx, uri, 1600, 82) ?: return "ما گدرت أفتح الصورة"
    withContext(Dispatchers.IO) { dest.writeBytes(bytes) }
    return null
}

/** ImageLoader واحد للتطبيق يعرف يفك GIF (ImageDecoder من أندرويد 9، و GifDecoder للأقدم) */
object GifLoader {
    @Volatile private var loader: ImageLoader? = null

    fun get(ctx: Context): ImageLoader = loader ?: synchronized(this) {
        loader ?: ImageLoader.Builder(ctx.applicationContext)
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .crossfade(false)
            .build()
            .also { loader = it }
    }
}

/** عدد التكرارات: -1 = للأبد (حتى لو الـ GIF نفسه مضبوط يتشغل مرة وحدة، أو كان قصير) */
private const val REPEAT_FOREVER = -1

/**
 * يعرض GIF من ملف ويكرره بلا نهاية.
 * [key] غيّره إذا تغيّر محتوى الملف بنفس المسار (مثل خلفية محادثة اتبدلت).
 */
@Composable
fun AnimatedGifImage(
    file: File,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    key: Any = 0
) {
    val ctx = LocalContext.current
    val request = remember(file.path, key) {
        ImageRequest.Builder(ctx)
            .data(file)
            .repeatCount(REPEAT_FOREVER)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        imageLoader = GifLoader.get(ctx),
        modifier = modifier,
        contentScale = contentScale
    )
}

/**
 * يعرض صورة مخزّنة كنص base64: إذا GIF يتحرك بتكرار لا نهائي، وإذا JPEG يعرضها ثابتة مثل قبل.
 * [bitmap] = النسخة الثابتة (أول فريم) من rememberB64Image، تظهر ثواني قليلة لحد ما يجهز الملف المتحرك.
 */
@Composable
fun AnimatedB64Image(
    b64: String,
    bitmap: ImageBitmap?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val ctx = LocalContext.current
    if (GifUtil.isGifB64(b64)) {
        val file by produceState<File?>(null, b64) {
            value = withContext(Dispatchers.IO) { runCatching { GifUtil.fileForB64(ctx, b64) }.getOrNull() }
        }
        val f = file
        if (f != null) AnimatedGifImage(f, modifier, contentScale, key = b64.length)
        else if (bitmap != null) Image(bitmap, null, modifier, contentScale = contentScale)
    } else if (bitmap != null) {
        Image(bitmap, null, modifier, contentScale = contentScale)
    }
}
