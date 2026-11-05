package com.chatapp.messenger.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import com.chatapp.messenger.ui.glass.InteractiveHighlight
import com.chatapp.messenger.ui.theme.GlassFx
import com.chatapp.messenger.ui.theme.glassSurfaceLayer
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.compose.ui.graphics.Canvas as GCanvas
import com.chatapp.messenger.Push
import com.chatapp.messenger.Repo
import com.chatapp.messenger.ui.glass.GlassButton
import com.chatapp.messenger.ui.theme.ThemeSettings
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/* ───────────────────────── رابط الحساب + مصفوفة الباركود ───────────────────────── */

object QrUtil {
    /** الرابط الي يطلع لما أحد يمسح الباركود (صفحة على الـ Worker تفتح التطبيق على حساب المستخدم) */
    fun profileLink(uid: String): String = Push.PUSH_URL.trimEnd('/') + "/u/" + uid

    /** مصفوفة الباركود (true = مربع غامق). مستوى تصحيح H حتى يتحمل الشعار بالنص */
    fun matrix(text: String): Array<BooleanArray>? = runCatching {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H
        )
        val m = Encoder.encode(text, ErrorCorrectionLevel.H, hints).matrix
        Array(m.height) { y -> BooleanArray(m.width) { x -> m.get(x, y).toInt() == 1 } }
    }.getOrNull()
}

/* ───────────────────────── الثيمات (30 لون + لون التطبيق) ───────────────────────── */

/** ثيم ألوان الباركود: تدرج الخلفية + تدرج لون الباركود نفسه */
private class QrTheme(val bgTop: Color, val bgBottom: Color, val qrTop: Color, val qrBottom: Color)

private fun themeFromBase(c: Color) = QrTheme(
    bgTop = lerp(c, Color.White, 0.08f),
    bgBottom = lerp(c, Color.Black, 0.60f),
    qrTop = lerp(c, Color.Black, 0.45f),
    qrBottom = lerp(c, Color.Black, 0.72f)
)

private val qrBaseColors: List<Color> = listOf(
    0xFF1AA7FF, 0xFF2F6BFF, 0xFF5E5CE6, 0xFF8A5CFF, 0xFFB44CFF, 0xFFD946EF, 0xFFFF2D92, 0xFFFF6FB5,
    0xFFFF453A, 0xFFFF6B5E, 0xFFFF8A65, 0xFFFF9F0A, 0xFFFFB800, 0xFFFFD60A, 0xFFB8E600, 0xFF84CC16,
    0xFF30D158, 0xFF00C27A, 0xFF00D4AA, 0xFF00C7BE, 0xFF00E5FF, 0xFF64D2FF, 0xFFA78BFA, 0xFFB07A4F,
    0xFF8E9AAF, 0xFFE0B040, 0xFF334155, 0xFF111827, 0xFFF43F5E, 0xFF0EA5E9
).map { Color(it) }

private val qrThemes: List<QrTheme> by lazy { qrBaseColors.map { themeFromBase(it) } }

/** عدد الخيارات: الأول يتبع لون التطبيق + 30 لون جاهز */
private val qrThemeCount: Int get() = qrBaseColors.size + 1

private fun themeAt(i: Int): QrTheme =
    if (i <= 0) themeFromBase(ThemeSettings.accent) else qrThemes[(i - 1).coerceIn(0, qrThemes.lastIndex)]

/* ───────────────────────── التخزين: الثيم + الخلفية + خلفياتي ───────────────────────── */

private object QrStyle {
    private const val PREFS = "qr_style"
    private fun sp(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadTheme(ctx: Context): Int = sp(ctx).getInt("theme", 0).coerceIn(0, qrThemeCount - 1)
    fun saveTheme(ctx: Context, i: Int) { sp(ctx).edit().putInt("theme", i).apply() }

    /** "grad" = تدرج الثيم، "p1/p2/p3" = أنماط جاهزة، "img:<اسم الملف>" = خلفية من المستخدم */
    fun loadBg(ctx: Context): String {
        val k = sp(ctx).getString("bg", "grad") ?: "grad"
        return if (k.startsWith("img:") && !File(bgDir(ctx), k.removePrefix("img:")).exists()) "grad" else k
    }
    fun saveBg(ctx: Context, k: String) { sp(ctx).edit().putString("bg", k).apply() }

    fun bgDir(ctx: Context): File = File(ctx.filesDir, "qr_bg").apply { mkdirs() }
    fun customBgs(ctx: Context): List<File> =
        bgDir(ctx).listFiles { f -> f.extension == "jpg" }?.sortedBy { it.lastModified() } ?: emptyList()
}

/* ───────────────────────── خط الأسماء (ينزل مرة وحدة وينحفظ) ───────────────────────── */

/**
 * خط عربي حلو لاسم المستخدم بالواجهة (Tajawal). ينزل أول مرة تفتح الواجهة وينحفظ بالجهاز؛
 * لحد ما ينزل (أو إذا ما كان في نت) تشتغل الواجهة بخط النظام عادي.
 * يتخزن بمجلد خاص حتى FontManager الأساسي ما يحذفه.
 */
private object QrFont {
    private const val URL_STR = "https://github.com/google/fonts/raw/main/ofl/tajawal/Tajawal-ExtraBold.ttf"
    private const val FILE = "tajawal_xb_v1.ttf"
    private const val MIN_BYTES = 20_000L

    var family: FontFamily? by mutableStateOf(null)
    @Volatile var typeface: Typeface? = null

    private fun apply(f: File): Boolean = runCatching {
        if (f.length() < MIN_BYTES) return false
        val tf = Typeface.createFromFile(f)
        typeface = tf
        family = FontFamily(tf)
        true
    }.getOrDefault(false)

    suspend fun ensure(ctx: Context) = withContext(Dispatchers.IO) {
        if (typeface != null) return@withContext
        val dir = File(ctx.applicationContext.filesDir, "qr_fonts").apply { mkdirs() }
        val f = File(dir, FILE)
        if (f.exists() && apply(f)) return@withContext
        repeat(2) {
            runCatching {
                val part = File(dir, "$FILE.part")
                val c = URL(URL_STR).openConnection() as HttpURLConnection
                try {
                    c.connectTimeout = 15_000; c.readTimeout = 30_000; c.instanceFollowRedirects = true
                    if (c.responseCode == 200) {
                        c.inputStream.use { i -> part.outputStream().use { o -> i.copyTo(o) } }
                        if (part.length() >= MIN_BYTES && part.renameTo(f) && apply(f)) return@withContext
                    }
                } finally { c.disconnect(); part.delete() }
            }
        }
    }
}

/* ───────────────────────── الرسم: خلفية + باركود (يستخدم بالشاشة وبالصورة المشاركة) ───────────────────────── */

private const val WAVE = 0.18f

/** نصف قطر الانتشار الحالي لحركة تبديل اللون */
private fun revealRadius(p: Float, rMax: Float) = p * (1f + WAVE) * rMax

/** تدرج الخلفية: اللون القديم، وفوقه اللون الجديد ينتشر بدائرة من نقطة اللمس */
private fun DrawScope.drawQrBackground(from: QrTheme, to: QrTheme, p: Float, origin: Offset) {
    drawRect(Brush.verticalGradient(listOf(from.bgTop, from.bgBottom)))
    if (p >= 1f) {
        drawRect(Brush.verticalGradient(listOf(to.bgTop, to.bgBottom)))
        return
    }
    val rMax = maxOf(
        hypot(origin.x, origin.y), hypot(size.width - origin.x, origin.y),
        hypot(origin.x, size.height - origin.y), hypot(size.width - origin.x, size.height - origin.y)
    )
    val r = revealRadius(p, rMax)
    val circle = Path().apply { addOval(Rect(origin, r)) }
    clipPath(circle) { drawRect(Brush.verticalGradient(listOf(to.bgTop, to.bgBottom))) }
    // حلقة مضيئة على حافة الانتشار
    val fade = 1f - p
    drawCircle(Color.White.copy(alpha = 0.45f * fade), radius = r, center = origin, style = Stroke(width = 10f * fade + 1f))
    drawCircle(Color.White.copy(alpha = 0.18f * fade), radius = (r - 36f).coerceAtLeast(0f), center = origin, style = Stroke(width = 4f))
}

private fun DrawScope.drawImageCover(img: ImageBitmap) {
    val scale = max(size.width / img.width, size.height / img.height)
    val sw = (size.width / scale).toInt().coerceIn(1, img.width)
    val sh = (size.height / scale).toInt().coerceIn(1, img.height)
    drawImage(
        img,
        srcOffset = IntOffset((img.width - sw) / 2, (img.height - sh) / 2), srcSize = IntSize(sw, sh),
        dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        filterQuality = FilterQuality.Medium
    )
}

/** زخرفة الخلفية: أنماط جاهزة (p1 نقاط / p2 حلقات / p3 فقاعات) أو صورة المستخدم */
private fun DrawScope.drawQrDecor(kind: String, img: ImageBitmap?) {
    val u = size.width / 360f
    when {
        kind == "p1" -> {
            val step = 26f * u
            var j = 0
            var y = 0f
            while (y < size.height + step) {
                var x = if (j % 2 == 0) 0f else step / 2
                while (x < size.width + step) {
                    drawCircle(Color.White.copy(alpha = 0.14f), radius = 3.2f * u, center = Offset(x, y))
                    x += step
                }
                y += step * 0.86f; j++
            }
        }
        kind == "p2" -> {
            for ((c, n) in listOf(Offset(size.width * 0.92f, size.height * 0.10f) to 16, Offset(size.width * 0.05f, size.height * 0.95f) to 11)) {
                for (k in 1..n) {
                    drawCircle(Color.White.copy(alpha = (0.14f - k * 0.006f).coerceAtLeast(0.03f)), radius = k * 44f * u, center = c, style = Stroke(width = 1.6f * u))
                }
            }
        }
        kind == "p3" -> {
            val rnd = Random(11)
            repeat(20) {
                val c = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)
                val r = (14f + rnd.nextFloat() * 60f) * u
                drawCircle(Color.White.copy(alpha = 0.05f + rnd.nextFloat() * 0.06f), radius = r, center = c)
                drawCircle(Color.White.copy(alpha = 0.12f), radius = r, center = c, style = Stroke(width = 1.4f * u))
            }
        }
        kind.startsWith("img:") && img != null -> {
            drawImageCover(img)
            drawRect(Color.Black.copy(alpha = 0.24f))
        }
    }
}

/**
 * يرسم الباركود: مربعات دائرية الحواف شوي + علامات الزوايا دائرية + فراغ للشعار بالنص.
 * أثناء تبديل اللون (p<1) موجة تطلع من نقطة اللمس: كل مربع ينكمش ويرجع بلونه الجديد.
 * origin وrMax بإحداثيات الباركود نفسه.
 */
private fun DrawScope.drawQr(
    m: Array<BooleanArray>, side: Float, from: QrTheme, to: QrTheme,
    p: Float, origin: Offset, rMax: Float
) {
    val n = m.size
    val cell = side / n
    val logoCells = (((n * 0.27f).toInt()) or 1).coerceAtLeast(5)
    val logoFrom = (n - logoCells) / 2
    val logoTo = logoFrom + logoCells - 1
    val animating = p < 1f && rMax > 0f

    val pFrom = Path().apply { fillType = PathFillType.EvenOdd }
    val pTo = Path().apply { fillType = PathFillType.EvenOdd }

    fun waveT(cx: Float, cy: Float): Float =
        if (!animating) 1f else ((p * (1f + WAVE) - hypot(cx - origin.x, cy - origin.y) / rMax) / WAVE).coerceIn(0f, 1f)

    fun addRound(path: Path, l: Float, t: Float, w: Float, rad: Float) {
        path.addRoundRect(RoundRect(l, t, l + w, t + w, CornerRadius(rad, rad)))
    }

    for (y in 0 until n) for (x in 0 until n) {
        if (!m[y][x]) continue
        if ((x < 8 && y < 8) || (x >= n - 8 && y < 8) || (x < 8 && y >= n - 8)) continue
        if (x in logoFrom..logoTo && y in logoFrom..logoTo) continue
        val cx = (x + 0.5f) * cell
        val cy = (y + 0.5f) * cell
        val t = waveT(cx, cy)
        val s = 1f - 0.55f * sin(PI.toFloat() * t)
        val w = cell * s
        addRound(if (t >= 0.5f) pTo else pFrom, cx - w / 2, cy - w / 2, w, cell * 0.32f * s)
    }
    // علامات الزوايا: حلقة خارجية + حلقة داخلية (ثقب) + مربع وسط، كلها دائرية
    for ((fx, fy) in listOf(0 to 0, n - 7 to 0, 0 to n - 7)) {
        val cx = (fx + 3.5f) * cell
        val cy = (fy + 3.5f) * cell
        val t = waveT(cx, cy)
        val s = 1f - 0.12f * sin(PI.toFloat() * t)
        val c = cell * s
        val ox = cx - 3.5f * c
        val oy = cy - 3.5f * c
        val path = if (t >= 0.5f) pTo else pFrom
        addRound(path, ox, oy, 7 * c, c * 2.1f)
        addRound(path, ox + c, oy + c, 5 * c, c * 1.3f)
        addRound(path, ox + 2 * c, oy + 2 * c, 3 * c, c * 1.0f)
    }
    val bFrom = Brush.linearGradient(listOf(from.qrTop, from.qrBottom), Offset.Zero, Offset(side, side))
    val bTo = Brush.linearGradient(listOf(to.qrTop, to.qrBottom), Offset.Zero, Offset(side, side))
    if (animating) drawPath(pFrom, bFrom)
    drawPath(pTo, bTo)
}

/* ───────────────────────── خلفيات المستخدم ───────────────────────── */

private fun decodeScaled(file: File, maxSide: Int): Bitmap? = runCatching {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, o)
    var s = 1
    while (max(o.outWidth, o.outHeight) / (s * 2) >= maxSide) s *= 2
    BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = s })
}.getOrNull()

/** ينسخ الصورة المختارة لمجلد التطبيق (مصغّرة + مصححة الدوران) حتى تبقى حتى لو انحذفت من المعرض */
private fun importBackground(ctx: Context, uri: Uri): File? = runCatching {
    val cr = ctx.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var s = 1
    while (max(bounds.outWidth, bounds.outHeight) / (s * 2) >= 1600) s *= 2
    var bmp = cr.openInputStream(uri)!!.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s })
    }!!
    val rot = runCatching {
        cr.openInputStream(uri)!!.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }
    }.getOrDefault(0f)
    if (rot != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot) }, true)
    val out = File(QrStyle.bgDir(ctx), "bg_${System.currentTimeMillis()}.jpg")
    out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    out
}.getOrNull()

/* ───────────────────────── صورة المشاركة ───────────────────────── */

/** يرسم بطاقة الباركود كاملة كصورة PNG (نفس شكل الشاشة) */
private fun renderShareImage(
    ctx: Context, matrix: Array<BooleanArray>, theme: QrTheme, bgKey: String, bgImg: ImageBitmap?,
    avatar: ImageBitmap?, name: String, username: String
): File? = runCatching {
    val w = 1080
    val h = 1700
    val img = ImageBitmap(w, h)
    val logo = BitmapFactory.decodeResource(ctx.resources, ThemeSettings.icons[ThemeSettings.iconIndex].previewRes).asImageBitmap()
    val tf = QrFont.typeface ?: Typeface.DEFAULT_BOLD

    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, GCanvas(img), Size(w.toFloat(), h.toFloat())) {
        drawQrBackground(theme, theme, 1f, Offset.Zero)
        drawQrDecor(bgKey, bgImg)

        val cardW = 820f
        val cardX = (w - cardW) / 2
        val cardTop = 333f
        val cardH = 931f
        drawRoundRect(Color.White, Offset(cardX, cardTop), Size(cardW, cardH), CornerRadius(83f))
        translate(cardX + 50f, cardTop + 161f) {
            drawQr(matrix, 720f, theme, theme, 1f, Offset.Zero, 0f)
        }
        // شعار التطبيق بالنص
        val lc = Offset(cardX + 50f + 360f, cardTop + 161f + 360f)
        drawRoundRect(Color.White, Offset(lc.x - 86f, lc.y - 86f), Size(172f, 172f), CornerRadius(50f))
        val logoPath = Path().apply {
            addRoundRect(RoundRect(lc.x - 75f, lc.y - 75f, lc.x + 75f, lc.y + 75f, CornerRadius(39f)))
        }
        clipPath(logoPath) {
            drawImage(
                logo, srcOffset = IntOffset.Zero, srcSize = IntSize(logo.width, logo.height),
                dstOffset = IntOffset((lc.x - 75f).toInt(), (lc.y - 75f).toInt()), dstSize = IntSize(150, 150),
                filterQuality = FilterQuality.High
            )
        }
        // الصورة الشخصية فوق
        val ac = Offset(w / 2f, 200f + 144f)
        drawCircle(Color.White, radius = 144f, center = ac)
        val avatarPath = Path().apply { addOval(Rect(ac, 130f)) }
        clipPath(avatarPath) {
            if (avatar != null) {
                translate(ac.x - 130f, ac.y - 130f) {
                    // نملأ الدائرة بقص مركزي
                    val sc = max(260f / avatar.width, 260f / avatar.height)
                    val sw = (260f / sc).toInt().coerceIn(1, avatar.width)
                    val sh = (260f / sc).toInt().coerceIn(1, avatar.height)
                    drawImage(
                        avatar, srcOffset = IntOffset((avatar.width - sw) / 2, (avatar.height - sh) / 2), srcSize = IntSize(sw, sh),
                        dstOffset = IntOffset.Zero, dstSize = IntSize(260, 260), filterQuality = FilterQuality.High
                    )
                }
            } else {
                drawCircle(Brush.linearGradient(listOf(theme.qrTop, theme.qrBottom), Offset(ac.x - 130f, ac.y - 130f), Offset(ac.x + 130f, ac.y + 130f)), radius = 130f, center = ac)
            }
        }
        if (avatar == null) {
            drawIntoCanvas {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE; textSize = 120f; typeface = tf; textAlign = Paint.Align.CENTER
                }
                it.nativeCanvas.drawText(name.trim().take(1).ifBlank { "؟" }, ac.x, ac.y + 42f, p)
            }
        }
        // النصوص
        drawIntoCanvas {
            val cv = it.nativeCanvas
            val pn = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE; textSize = 78f; typeface = tf; textAlign = Paint.Align.CENTER
            }
            cv.drawText(name.ifBlank { "حسابي" }, w / 2f, cardTop + cardH + 118f, pn)
            if (username.isNotBlank()) {
                val pu = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb(200, 255, 255, 255); textSize = 52f; typeface = tf; textAlign = Paint.Align.CENTER
                }
                cv.drawText("@$username", w / 2f, cardTop + cardH + 192f, pu)
            }
            val ph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(170, 255, 255, 255); textSize = 38f; typeface = tf; textAlign = Paint.Align.CENTER
            }
            cv.drawText("امسح الباركود لفتح حسابي بالتطبيق", w / 2f, cardTop + cardH + 270f, ph)
        }
    }
    val dir = File(ctx.cacheDir, "qr_share").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val f = File(dir, "qr_${System.currentTimeMillis()}.png")
    f.outputStream().use { img.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    f
}.getOrNull()

/* ───────────────────────── الشاشة (تصميم جديد: ماسح زجاجي) ───────────────────────── */

private enum class QrSheet { None, Bg, Share }

/** ضغطة بنابض (نفس إحساس GlassButton) */
@Composable
private fun Modifier.springClickable(pressScale: Float = 1.04f, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) pressScale else 1f, spring(0.5f, 400f), label = "springClick")
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}

@Composable
fun QrCodeScreen(name: String, username: String, avatar: String = "", onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val uid = remember { Repo.auth.currentUser?.uid ?: "" }
    val link = remember(uid) { QrUtil.profileLink(uid) }
    val matrix = remember(link) { if (uid.isBlank()) null else QrUtil.matrix(link) }
    val avatarImg = rememberB64Image(avatar)
    val fontFam = QrFont.family ?: FontFamily.Default
    LaunchedEffect(Unit) { QrFont.ensure(ctx) }

    var fromIdx by remember { mutableIntStateOf(QrStyle.loadTheme(ctx)) }
    var toIdx by remember { mutableIntStateOf(fromIdx) }
    val reveal = remember { Animatable(1f) }
    val pulse = remember { Animatable(1f) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var bgPos by remember { mutableStateOf(Offset.Zero) }
    var bgSize by remember { mutableStateOf(Size(1f, 1f)) }
    var qrPos by remember { mutableStateOf(Offset.Zero) }

    var bgKey by remember { mutableStateOf(QrStyle.loadBg(ctx)) }
    var customBgs by remember { mutableStateOf(QrStyle.customBgs(ctx)) }
    var sheet by remember { mutableStateOf(QrSheet.None) }
    var lastSheet by remember { mutableStateOf(QrSheet.Bg) }
    if (sheet != QrSheet.None) lastSheet = sheet
    var busyImage by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    val bgImage by produceState<ImageBitmap?>(null, bgKey) {
        value = if (!bgKey.startsWith("img:")) null else withContext(Dispatchers.IO) {
            decodeScaled(File(QrStyle.bgDir(ctx), bgKey.removePrefix("img:")), 1600)?.asImageBitmap()
        }
    }

    fun pickTheme(i: Int, at: Offset) {
        if (i == toIdx) return
        origin = at
        fromIdx = toIdx
        toIdx = i
        QrStyle.saveTheme(ctx, i)
        scope.launch { reveal.snapTo(0f); reveal.animateTo(1f, tween(950, easing = FastOutSlowInEasing)) }
        scope.launch { pulse.snapTo(0.94f); pulse.animateTo(1f, spring(0.38f, 240f)) }
    }
    fun pickBg(k: String) { bgKey = k; QrStyle.saveBg(ctx, k) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val f = withContext(Dispatchers.IO) { importBackground(ctx, uri) }
            if (f != null) {
                customBgs = QrStyle.customBgs(ctx)
                pickBg("img:" + f.name)
            } else AppToast.show("ما گدرت أضيف الصورة")
        }
    }

    BackHandler(onBack = onDismiss)
    BackHandler(enabled = sheet != QrSheet.None) { sheet = QrSheet.None }

    LaunchedEffect(copied) { if (copied) { delay(1600); copied = false } }

    val bd = rememberLayerBackdrop()
    val ffrom = themeAt(fromIdx)
    val fto = themeAt(toIdx)
    val accent by animateColorAsState(fto.qrTop, tween(700), label = "accent")

    // دخول متتابع
    val eHead = remember { Animatable(0f) }
    val ePanel = remember { Animatable(0f) }
    val eStrip = remember { Animatable(0f) }
    val eDock = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { eHead.animateTo(1f, spring(0.75f, Spring.StiffnessMediumLow)) }
        launch { delay(90); ePanel.animateTo(1f, spring(0.68f, Spring.StiffnessMediumLow)) }
        launch { delay(200); eStrip.animateTo(1f, spring(0.75f, Spring.StiffnessMediumLow)) }
        launch { delay(290); eDock.animateTo(1f, spring(0.7f, Spring.StiffnessMediumLow)) }
    }

    // حركات مستمرة
    val infinite = rememberInfiniteTransition(label = "qrIdle")
    val scan by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scan")
    val breath by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
    val shimmer by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(4600, easing = LinearEasing), RepeatMode.Restart), label = "shimmer")
    val ringAngle by infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart), label = "ring")

    val panelHl = remember(scope) { InteractiveHighlight(animationScope = scope) }
    val dockHl = remember(scope) { InteractiveHighlight(animationScope = scope) }
    val panelShape = RoundedCornerShape(40.dp)

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier.fillMaxSize().layerBackdrop(bd).onGloballyPositioned {
                bgPos = it.positionInRoot()
                bgSize = Size(it.size.width.toFloat(), it.size.height.toFloat())
            }
        ) {
            drawQrBackground(ffrom, fto, reveal.value, origin - bgPos)
            drawQrDecor(bgKey, bgImage)
        }

        Box(
            Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }
                .statusBarsPadding().navigationBarsPadding()
        ) {
            val contentScale by animateFloatAsState(if (sheet != QrSheet.None) 0.62f else 1f, spring(0.85f, 300f), label = "qrScale")
            Column(
                Modifier.fillMaxSize()
                    .graphicsLayer { scaleX = contentScale; scaleY = contentScale; transformOrigin = TransformOrigin(0.5f, 0f) }
                    .verticalScroll(rememberScrollState())
                    .padding(top = 70.dp, bottom = 18.dp, start = 18.dp, end = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ───── 1) رأس أفقي: صورة + اسم ─────
                Row(
                    Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 6.dp)
                        .graphicsLayer {
                            alpha = eHead.value.coerceIn(0f, 1f)
                            translationY = (1f - eHead.value) * -40.dp.toPx()
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(72.dp)
                            .drawBackdrop(
                                backdrop = bd,
                                shape = { CircleShape },
                                effects = {
                                    vibrancy()
                                    blur((4f.dp.toPx()) * GlassFx.blurK)
                                    lens((10f.dp.toPx()) * GlassFx.refractK, (20f.dp.toPx()) * GlassFx.refractK, chromaticAberration = true)
                                },
                                highlight = { Highlight.Default },
                                shadow = { Shadow(radius = 12.dp, color = Color.Black.copy(alpha = 0.25f)) },
                                innerShadow = { InnerShadow(radius = 5.dp, alpha = 0.35f) },
                                onDrawSurface = { drawRect(Color.White.copy(0.28f)) }
                            )
                            .drawBehind {
                                rotate(ringAngle) {
                                    drawCircle(
                                        Brush.sweepGradient(listOf(Color.White.copy(0.95f), Color.White.copy(0.04f), Color.White.copy(0.95f))),
                                        radius = size.minDimension / 2f - 1.5.dp.toPx(),
                                        style = Stroke(width = 3.dp.toPx())
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(62.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                            val a = avatarImg
                            if (a != null) {
                                Image(bitmap = a, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Box(
                                    Modifier.fillMaxSize().background(Brush.linearGradient(listOf(fto.qrTop, fto.qrBottom))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(name.trim().take(1).ifBlank { "؟" }, color = Color.White, fontSize = 26.sp, fontFamily = fontFam, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(
                            name.ifBlank { "حسابي" }, color = Color.White, fontSize = 24.sp, maxLines = 1,
                            fontFamily = fontFam, fontWeight = FontWeight.ExtraBold
                        )
                        if (username.isNotBlank()) {
                            Text(
                                "@$username", color = Color.White.copy(0.9f), fontSize = 14.sp, fontFamily = fontFam, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(0.20f))
                                    .border(0.8.dp, Color.White.copy(0.30f), RoundedCornerShape(50))
                                    .padding(horizontal = 12.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))

                // ───── 2) لوح الماسح: زجاج مصقّع كبير بعرض الشاشة ─────
                Column(
                    Modifier.widthIn(max = 420.dp).fillMaxWidth()
                        .graphicsLayer {
                            val e = ePanel.value
                            val p = panelHl.pressProgress
                            val s = (0.84f + 0.16f * e) * pulse.value * (1f + 0.025f * p)
                            scaleX = s; scaleY = s
                            alpha = e.coerceIn(0f, 1f)
                            translationY = (1f - e) * 80.dp.toPx()
                            cameraDistance = 16f * density
                            val w = size.width.coerceAtLeast(1f)
                            val h = size.height.coerceAtLeast(1f)
                            rotationY = (panelHl.offset.x / w) * 14f
                            rotationX = -(panelHl.offset.y / h) * 14f
                        }
                        .then(panelHl.gestureModifier)
                        .drawBackdrop(
                            backdrop = bd,
                            shape = { panelShape },
                            effects = {
                                vibrancy()
                                blur((18f.dp.toPx()) * GlassFx.blurK)
                                lens((14f.dp.toPx()) * GlassFx.refractK, (28f.dp.toPx()) * GlassFx.refractK)
                            },
                            highlight = { Highlight.Default.copy(alpha = 0.6f + 0.4f * panelHl.pressProgress) },
                            shadow = { Shadow(radius = 28.dp, color = Color.Black.copy(alpha = 0.25f)) },
                            innerShadow = { InnerShadow(radius = 10.dp, alpha = 0.25f + 0.3f * panelHl.pressProgress) },
                            onDrawSurface = {
                                // زجاج مصقّع فاتح حتى يبقى الباركود الغامق واضح ويتمسح
                                glassSurfaceLayer { drawRect(Color.White.copy(0.80f)) }
                                val w = size.width
                                val x = -w * 0.6f + (w * 2.2f) * shimmer
                                drawRect(
                                    Brush.linearGradient(
                                        listOf(Color.Transparent, Color.White.copy(0.55f), Color.Transparent),
                                        start = Offset(x - w * 0.2f, 0f), end = Offset(x + w * 0.2f, size.height * 0.5f)
                                    )
                                )
                            }
                        )
                        .then(panelHl.modifier)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                        val mx = matrix
                        if (mx != null) {
                            Canvas(Modifier.fillMaxSize().padding(16.dp).onGloballyPositioned { qrPos = it.positionInRoot() }) {
                                val ob = origin - bgPos
                                val rMax = maxOf(
                                    hypot(ob.x, ob.y), hypot(bgSize.width - ob.x, ob.y),
                                    hypot(ob.x, bgSize.height - ob.y), hypot(bgSize.width - ob.x, bgSize.height - ob.y)
                                ).coerceAtLeast(1f)
                                drawQr(mx, size.width, ffrom, fto, reveal.value, origin - qrPos, rMax)
                            }
                            Box(
                                Modifier.fillMaxWidth(0.21f).aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(ThemeSettings.icons[ThemeSettings.iconIndex].previewRes),
                                    contentDescription = null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(0.88f).clip(RoundedCornerShape(16.dp))
                                )
                            }
                        }
                        // زوايا الماسح (تتنفس) + خط مسح يطلع وينزل
                        Canvas(Modifier.fillMaxSize()) {
                            val inset = (1.dp.toPx()) + breath * 5.dp.toPx()
                            val len = 34.dp.toPx()
                            val sw = 4.5f.dp.toPx()
                            val c = accent
                            fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                                drawLine(c, Offset(x, y), Offset(x + dx * len, y), sw, StrokeCap.Round)
                                drawLine(c, Offset(x, y), Offset(x, y + dy * len), sw, StrokeCap.Round)
                            }
                            corner(inset, inset, 1f, 1f)
                            corner(size.width - inset, inset, -1f, 1f)
                            corner(inset, size.height - inset, 1f, -1f)
                            corner(size.width - inset, size.height - inset, -1f, -1f)
                            val pad = 16.dp.toPx()
                            val y = pad + (size.height - pad * 2) * scan
                            val tail = 70.dp.toPx()
                            val top = (y - tail).coerceAtLeast(pad)
                            drawRect(
                                Brush.verticalGradient(listOf(Color.Transparent, c.copy(alpha = 0.28f)), startY = y - tail, endY = y),
                                topLeft = Offset(pad, top), size = Size(size.width - pad * 2, y - top)
                            )
                            drawLine(c.copy(alpha = 0.85f), Offset(pad, y), Offset(size.width - pad, y), 3.dp.toPx(), StrokeCap.Round)
                        }
                    }
                    Text(
                        "امسح الباركود بكاميرا الجوال أو كوكل ليفتح حسابي بالتطبيق",
                        color = Color(0xFF1B1F27).copy(0.72f), fontSize = 12.5.sp, fontFamily = fontFam,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))

                // ───── 3) شريط الألوان الزجاجي (ثابت، بدون لوحة) ─────
                val stripState = rememberLazyListState(initialFirstVisibleItemIndex = (toIdx - 2).coerceAtLeast(0))
                Box(
                    Modifier.widthIn(max = 420.dp).fillMaxWidth().height(66.dp)
                        .graphicsLayer {
                            alpha = eStrip.value.coerceIn(0f, 1f)
                            translationY = (1f - eStrip.value) * 50.dp.toPx()
                        }
                        .drawBackdrop(
                            backdrop = bd,
                            shape = { Capsule() },
                            effects = {
                                vibrancy()
                                blur((8f.dp.toPx()) * GlassFx.blurK)
                                lens((16f.dp.toPx()) * GlassFx.refractK, (24f.dp.toPx()) * GlassFx.refractK)
                            },
                            highlight = { Highlight.Default.copy(alpha = 0.7f) },
                            shadow = { Shadow(radius = 14.dp, color = Color.Black.copy(alpha = 0.18f)) },
                            onDrawSurface = { glassSurfaceLayer { drawRect(Color.Black.copy(0.20f)) } }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    LazyRow(
                        state = stripState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(qrThemeCount) { i ->
                            val t = themeAt(i)
                            var center by remember { mutableStateOf(Offset.Zero) }
                            val sel = i == toIdx
                            val ring by animateFloatAsState(if (sel) 1f else 0f, spring(0.55f, 420f), label = "sw$i")
                            Box(
                                Modifier.size(42.dp)
                                    .springClickable(1.16f) { pickTheme(i, center) }
                                    .graphicsLayer { val s = 1f + 0.14f * ring; scaleX = s; scaleY = s }
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(t.bgTop, t.bgBottom)))
                                    .border((1f + 2.2f * ring).dp, Color.White.copy(0.35f + 0.65f * ring), CircleShape)
                                    .onGloballyPositioned { c -> center = c.positionInRoot() + Offset(c.size.width / 2f, c.size.height / 2f) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (sel) Icon(
                                    Icons.Filled.Check, null, tint = Color.White,
                                    modifier = Modifier.size(20.dp).graphicsLayer { scaleX = ring; scaleY = ring }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                // ───── 4) دوك زجاجي سفلي: التوهج يمشي مع إصبعك ─────
                Row(
                    Modifier.widthIn(max = 420.dp).fillMaxWidth().height(72.dp)
                        .graphicsLayer {
                            alpha = eDock.value.coerceIn(0f, 1f)
                            translationY = (1f - eDock.value) * 70.dp.toPx()
                            val p = dockHl.pressProgress
                            val s = 1f + 0.03f * p
                            scaleX = s; scaleY = s
                        }
                        .then(dockHl.gestureModifier)
                        .drawBackdrop(
                            backdrop = bd,
                            shape = { Capsule() },
                            effects = {
                                vibrancy()
                                blur((8f.dp.toPx()) * GlassFx.blurK)
                                lens((24f.dp.toPx()) * GlassFx.refractK, (24f.dp.toPx()) * GlassFx.refractK)
                            },
                            highlight = { Highlight.Default.copy(alpha = 0.6f + 0.4f * dockHl.pressProgress) },
                            shadow = { Shadow(radius = 18.dp, color = Color.Black.copy(alpha = 0.22f)) },
                            innerShadow = { InnerShadow(radius = 8.dp * dockHl.pressProgress, alpha = dockHl.pressProgress) },
                            onDrawSurface = { glassSurfaceLayer { drawRect(Color.White.copy(0.16f)) } }
                        )
                        .then(dockHl.modifier)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DockItem(AppIcons.PaintBrush, "الخلفية", fontFam, Modifier.weight(1f)) { sheet = QrSheet.Bg }
                    DockItem(AppIcons.Share, "مشاركة", fontFam, Modifier.weight(1f)) { sheet = QrSheet.Share }
                    DockItem(if (copied) Icons.Filled.Check else AppIcons.CopyIcon, if (copied) "اننسخ" else "نسخ", fontFam, Modifier.weight(1f), highlight = copied) {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("link", link))
                        copied = true
                    }
                }
            }

            // ───── لوحة سفلية زجاجية (خلفية / مشاركة) ─────
            val sheetShape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
            AnimatedVisibility(
                visible = sheet != QrSheet.None,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(spring(0.82f, 380f)) { it } + fadeIn(tween(160)),
                exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(160))
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .drawBackdrop(
                            backdrop = bd,
                            shape = { sheetShape },
                            effects = {
                                vibrancy()
                                blur((18f.dp.toPx()) * GlassFx.blurK)
                                lens((12f.dp.toPx()) * GlassFx.refractK, (24f.dp.toPx()) * GlassFx.refractK)
                            },
                            highlight = { Highlight.Default.copy(alpha = 0.6f) },
                            shadow = { Shadow(radius = 22.dp, color = Color.Black.copy(alpha = 0.25f)) },
                            onDrawSurface = { glassSurfaceLayer { drawRect(Color.Black.copy(0.32f)) } }
                        )
                        .border(0.8.dp, Brush.verticalGradient(listOf(Color.White.copy(0.45f), Color.White.copy(0.06f))), sheetShape)
                        .pointerInput(Unit) { detectTapGestures { } }
                        .padding(top = 10.dp, bottom = 12.dp)
                ) {
                    Box(
                        Modifier.align(Alignment.CenterHorizontally).width(38.dp).height(4.dp)
                            .clip(RoundedCornerShape(2.dp)).background(Color.White.copy(0.35f))
                    )
                    Spacer(Modifier.height(12.dp))
                    if (lastSheet == QrSheet.Bg) {
                        BgSheet(
                            bd = bd, fontFam = fontFam, theme = themeAt(toIdx), bgKey = bgKey, customBgs = customBgs,
                            onBg = { pickBg(it) },
                            onAddBg = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onDeleteBg = { f ->
                                val wasSelected = bgKey == "img:" + f.name
                                f.delete()
                                customBgs = QrStyle.customBgs(ctx)
                                if (wasSelected) pickBg("grad")
                            },
                            onDone = { sheet = QrSheet.None }
                        )
                    } else {
                        ShareSheet(
                            fontFam = fontFam, busy = busyImage,
                            onLink = {
                                sheet = QrSheet.None
                                runCatching {
                                    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, link) }
                                    ctx.startActivity(Intent.createChooser(i, "مشاركة الحساب").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            },
                            onImage = {
                                val mx = matrix ?: return@ShareSheet
                                if (busyImage) return@ShareSheet
                                busyImage = true
                                scope.launch {
                                    val f = withContext(Dispatchers.Default) {
                                        renderShareImage(ctx, mx, themeAt(toIdx), bgKey, bgImage, avatarImg, name, username)
                                    }
                                    busyImage = false
                                    if (f == null) { AppToast.show("ما گدرت أجهز الصورة"); return@launch }
                                    sheet = QrSheet.None
                                    runCatching {
                                        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", f)
                                        val i = Intent(Intent.ACTION_SEND).apply {
                                            type = "image/png"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            clipData = ClipData.newRawUri("", uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        ctx.startActivity(Intent.createChooser(i, "مشاركة الباركود").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    }
                                }
                            },
                            onCopy = {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("link", link))
                                AppToast.show("اننسخ الرابط")
                                sheet = QrSheet.None
                            }
                        )
                    }
                }
            }

            GlassButton(
                onClick = onDismiss, backdrop = bd, pressScale = 1.15f,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 10.dp).zIndex(10f).size(48.dp)
            ) {
                Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** عنصر الدوك: أيقونة + نص تحتها، ضغطة بنابض */
@Composable
private fun DockItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, fontFam: FontFamily,
    modifier: Modifier = Modifier, highlight: Boolean = false, onClick: () -> Unit
) {
    val pop by animateFloatAsState(if (highlight) 1.18f else 1f, spring(0.4f, 500f), label = "dockPop")
    Column(
        modifier.fillMaxHeight().springClickable(1.12f, onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(25.dp).graphicsLayer { scaleX = pop; scaleY = pop })
        Text(label, color = Color.White, fontSize = 11.5.sp, fontFamily = fontFam, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun BgSheet(
    bd: com.kyant.backdrop.Backdrop, fontFam: FontFamily, theme: QrTheme, bgKey: String, customBgs: List<File>,
    onBg: (String) -> Unit, onAddBg: () -> Unit, onDeleteBg: (File) -> Unit, onDone: () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("الخلفية", color = Color.White, fontSize = 18.sp, fontFamily = fontFam, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.weight(1f))
        GlassButton(onClick = onDone, backdrop = bd, pressScale = 1.1f, modifier = Modifier.height(40.dp).width(72.dp)) {
            Text("تم", color = Color.White, fontSize = 14.sp, fontFamily = fontFam, fontWeight = FontWeight.Bold)
        }
    }
    Spacer(Modifier.height(12.dp))
    LazyRow(
        Modifier.fillMaxWidth().height(190.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Box(
                Modifier.width(100.dp).fillMaxSize()
                    .springClickable(1.04f, onAddBg)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(0.10f))
                    .border(1.5.dp, Color.White.copy(0.35f), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(AppIcons.PlusIcon, null, tint = Color.White, modifier = Modifier.size(30.dp))
                    Text("من عندي", color = Color.White, fontSize = 12.sp, fontFamily = fontFam, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        val presets = listOf("grad" to "تدرج", "p1" to "نقاط", "p2" to "حلقات", "p3" to "فقاعات")
        items(presets.size) { i ->
            BgTile(presets[i].first, presets[i].second, theme, bgKey == presets[i].first, null, fontFam, { onBg(presets[i].first) }, null)
        }
        items(customBgs.size) { i ->
            val f = customBgs[i]
            val k = "img:" + f.name
            BgTile(k, "", theme, bgKey == k, f, fontFam, { onBg(k) }, { onDeleteBg(f) })
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun BgTile(
    key: String, label: String, theme: QrTheme, selected: Boolean, file: File?,
    fontFam: FontFamily, onClick: () -> Unit, onDelete: (() -> Unit)?
) {
    val thumb by produceState<ImageBitmap?>(null, file) {
        value = if (file == null) null else withContext(Dispatchers.IO) { decodeScaled(file, 360)?.asImageBitmap() }
    }
    val sel by animateFloatAsState(if (selected) 1f else 0f, spring(0.6f, 400f), label = "bgSel")
    Box(
        Modifier.width(100.dp).fillMaxSize()
            .springClickable(1.04f, onClick)
            .graphicsLayer { val s = 1f + 0.03f * sel; scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(22.dp))
            .border((1f + 2f * sel).dp, Color.White.copy(0.3f + 0.7f * sel), RoundedCornerShape(22.dp))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawQrBackground(theme, theme, 1f, Offset.Zero)
            drawQrDecor(key, thumb)
        }
        if (label.isNotEmpty()) {
            Text(
                label, color = Color.White, fontSize = 12.sp, fontFamily = fontFam, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
            )
        }
        if (selected) {
            Box(
                Modifier.align(Alignment.TopStart).padding(8.dp).size(22.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(14.dp)) }
        }
        if (onDelete != null) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp).clip(CircleShape)
                    .background(Color.Black.copy(0.45f)).clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(15.dp)) }
        }
    }
}

@Composable
private fun ShareSheet(fontFam: FontFamily, busy: Boolean, onLink: () -> Unit, onImage: () -> Unit, onCopy: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ShareRow(AppIcons.LinkChain, "مشاركة كرابط", "يوصل الرابط ويفتح حسابك بالتطبيق", fontFam, onLink)
        ShareRow(AppIcons.Image, if (busy) "جاري تجهيز الصورة..." else "مشاركة كصورة", "بطاقة الباركود بألوانك وخلفيتك", fontFam, onImage)
        ShareRow(AppIcons.CopyIcon, "نسخ الرابط", null, fontFam, onCopy)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ShareRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String?, fontFam: FontFamily, onClick: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth()
            .springClickable(1.025f, onClick)
            .clip(shape).background(Color.White.copy(0.09f))
            .border(0.8.dp, Brush.verticalGradient(listOf(Color.White.copy(0.30f), Color.White.copy(0.05f))), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(0.16f))
                .border(0.8.dp, Color.White.copy(0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontFamily = fontFam, fontWeight = FontWeight.Bold)
            if (sub != null) Text(sub, color = Color.White.copy(0.6f), fontSize = 12.sp, fontFamily = fontFam, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
