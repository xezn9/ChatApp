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
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.BoxWithConstraints
import kotlin.math.cos
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
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.roundToInt
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

    // ───── شكل الباركود: النقاط + الزوايا + صورة الشكل (بعد القص) ─────
    fun loadDots(ctx: Context): String {
        val k = sp(ctx).getString("dots", "round") ?: "round"
        return if (qrDotShapes.any { it.first == k }) k else "round"
    }
    fun saveDots(ctx: Context, k: String) { sp(ctx).edit().putString("dots", k).apply() }

    fun loadEye(ctx: Context): String {
        val k = sp(ctx).getString("eye", "round") ?: "round"
        return if (qrEyeShapes.any { it.first == k }) k else "round"
    }
    fun saveEye(ctx: Context, k: String) { sp(ctx).edit().putString("eye", k).apply() }

    fun shapeDir(ctx: Context): File = File(ctx.filesDir, "qr_shape").apply { mkdirs() }
    fun customShapes(ctx: Context): List<File> =
        shapeDir(ctx).listFiles { f -> f.extension == "jpg" }?.sortedBy { it.lastModified() } ?: emptyList()

    /** اسم ملف صورة الشكل المختارة، أو "" إذا ماكو */
    fun loadShapeImg(ctx: Context): String {
        val k = sp(ctx).getString("shapeImg", "") ?: ""
        return if (k.isNotEmpty() && File(shapeDir(ctx), k).exists()) k else ""
    }
    fun saveShapeImg(ctx: Context, k: String) { sp(ctx).edit().putString("shapeImg", k).apply() }

    fun loadImgMode(ctx: Context): Int = sp(ctx).getInt("imgMode", 0).coerceIn(0, 1)
    fun saveImgMode(ctx: Context, m: Int) { sp(ctx).edit().putInt("imgMode", m).apply() }
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
/** ظهور مع ارتداد خفيف (easeOutBack): 0→1 */
private fun qrBackOut(t: Float): Float { val u = t - 1f; return 1f + 2.70158f * u * u * u + 1.70158f * u * u }

/* ───────────────────────── أشكال الباركود ───────────────────────── */

/**
 * شكل النقاط + شكل الزوايا + صورة اختيارية.
 * imgMode 0: النقاط تاخذ ألوان الصورة (غامقة حتى يبقى الباركود ينقرأ)، 1: الصورة باهتة خلف الباركود.
 */
private class QrShape(val dots: String = "round", val eye: String = "round", val img: ImageBitmap? = null, val imgMode: Int = 0)

private val qrDotShapes: List<Pair<String, String>> = listOf(
    "round" to "مدوّر", "square" to "مربع", "circle" to "دوائر", "dots" to "نقاط صغيرة", "liquid" to "سائل",
    "vbars" to "أعمدة", "hbars" to "خطوط", "diamond" to "معيّن", "hex" to "سداسي", "leaf" to "ورقة",
    "plus" to "زائد", "star" to "نجمة", "sparkle" to "لمعة", "heart" to "قلب"
)

private val qrEyeShapes: List<Pair<String, String>> = listOf(
    "round" to "مدوّر", "soft" to "ناعم", "square" to "مربع", "circle" to "دائرة", "leaf" to "ورقة"
)

private fun addHeart(path: Path, cx: Float, cy: Float, w: Float) {
    val s = w * 1.1f
    val x0 = cx - s / 2f
    val y0 = cy - s / 2f
    fun px(u: Float) = x0 + u * s
    fun py(v: Float) = y0 + v * s
    path.moveTo(px(0.5f), py(0.92f))
    path.cubicTo(px(0.10f), py(0.62f), px(0.00f), py(0.35f), px(0.12f), py(0.20f))
    path.cubicTo(px(0.24f), py(0.05f), px(0.43f), py(0.08f), px(0.50f), py(0.25f))
    path.cubicTo(px(0.57f), py(0.08f), px(0.76f), py(0.05f), px(0.88f), py(0.20f))
    path.cubicTo(px(1.00f), py(0.35f), px(0.90f), py(0.62f), px(0.50f), py(0.92f))
    path.close()
}

/** يضيف نقطة وحدة من الباركود بالشكل المطلوب. up/down/left/right = هل المربع المجاور غامق (للأشكال المتصلة) */
private fun addDot(
    path: Path, kind: String, cx: Float, cy: Float, w: Float,
    up: Boolean, down: Boolean, left: Boolean, right: Boolean
) {
    val h = w / 2f
    val l = cx - h
    val t = cy - h
    fun rr(tl: Float, tr: Float, br: Float, bl: Float) {
        path.addRoundRect(RoundRect(l, t, l + w, t + w, CornerRadius(tl), CornerRadius(tr), CornerRadius(br), CornerRadius(bl)))
    }
    when (kind) {
        "square" -> path.addRect(Rect(l, t, l + w, t + w))
        "circle" -> path.addOval(Rect(Offset(cx, cy), h * 0.96f))
        "dots" -> path.addOval(Rect(Offset(cx, cy), h * 0.66f))
        "liquid" -> {
            val r = h
            fun c(a: Boolean, b: Boolean) = if (!a && !b) r else 0f
            rr(c(up, left), c(up, right), c(down, right), c(down, left))
        }
        "vbars" -> {
            val iw = w * 0.78f
            val r = iw / 2f
            val x0 = cx - r
            path.addRoundRect(
                RoundRect(
                    x0, t, x0 + iw, t + w,
                    CornerRadius(if (!up) r else 0f), CornerRadius(if (!up) r else 0f),
                    CornerRadius(if (!down) r else 0f), CornerRadius(if (!down) r else 0f)
                )
            )
        }
        "hbars" -> {
            val ih = w * 0.78f
            val r = ih / 2f
            val y0 = cy - r
            path.addRoundRect(
                RoundRect(
                    l, y0, l + w, y0 + ih,
                    CornerRadius(if (!left) r else 0f), CornerRadius(if (!right) r else 0f),
                    CornerRadius(if (!right) r else 0f), CornerRadius(if (!left) r else 0f)
                )
            )
        }
        "diamond" -> {
            val d = h * 1.08f
            path.moveTo(cx, cy - d); path.lineTo(cx + d, cy); path.lineTo(cx, cy + d); path.lineTo(cx - d, cy); path.close()
        }
        "hex" -> {
            val rad = h * 1.08f
            for (i in 0 until 6) {
                val a = (-PI / 2 + i * PI / 3).toFloat()
                val x = cx + cos(a) * rad
                val y = cy + sin(a) * rad
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        }
        "leaf" -> rr(h, w * 0.1f, h, w * 0.1f)
        "plus" -> {
            val a = w * 0.2f
            path.addRect(Rect(l, cy - a, l + w, cy + a))
            path.addRect(Rect(cx - a, t, cx + a, t + w))
        }
        "star" -> {
            val outer = h * 1.2f
            val inner = outer * 0.46f
            for (i in 0 until 10) {
                val a = (-PI / 2 + i * PI / 5).toFloat()
                val rad = if (i % 2 == 0) outer else inner
                val x = cx + cos(a) * rad
                val y = cy + sin(a) * rad
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        }
        "sparkle" -> {
            val r = h * 1.15f
            val k = r * 0.30f
            path.moveTo(cx, cy - r)
            path.quadraticBezierTo(cx + k, cy - k, cx + r, cy)
            path.quadraticBezierTo(cx + k, cy + k, cx, cy + r)
            path.quadraticBezierTo(cx - k, cy + k, cx - r, cy)
            path.quadraticBezierTo(cx - k, cy - k, cx, cy - r)
            path.close()
        }
        "heart" -> addHeart(path, cx, cy, w)
        else -> rr(w * 0.32f, w * 0.32f, w * 0.32f, w * 0.32f)
    }
}

/** مربع من مربعات علامة الزاوية (depth 0 = الحلقة الخارجية، 1 = داخل الحلقة (الثقب)، 2 = المربع الأوسط) */
private fun addEyeBox(path: Path, l: Float, t: Float, size: Float, style: String, c: Float, depth: Int, flip: Boolean) {
    val k = floatArrayOf(2.1f, 1.3f, 1.0f)[depth]
    var tl = 0f; var tr = 0f; var br = 0f; var bl = 0f
    when (style) {
        "round" -> { tl = k * c; tr = tl; br = tl; bl = tl }
        "soft" -> { tl = k * c * 0.4f; tr = tl; br = tl; bl = tl }
        "circle" -> { tl = size / 2f; tr = tl; br = tl; bl = tl }
        "leaf" -> {
            val big = size * 0.46f
            val sm = size * 0.08f
            if (!flip) { tl = big; br = big; tr = sm; bl = sm } else { tl = sm; br = sm; tr = big; bl = big }
        }
        else -> {}   // square
    }
    path.addRoundRect(RoundRect(l, t, l + size, t + size, CornerRadius(tl), CornerRadius(tr), CornerRadius(br), CornerRadius(bl)))
}

/** فرشاة تعبّي المسار بالصورة نفسها (الصورة تتمدد على كل الباركود) */
private fun imageBrush(img: ImageBitmap, side: Float): Brush {
    val sh = ImageShader(img, TileMode.Clamp, TileMode.Clamp)
    sh.setLocalMatrix(Matrix().apply { setScale(side / img.width, side / img.height) })
    return ShaderBrush(sh)
}

private fun DrawScope.drawQr(
    m: Array<BooleanArray>, side: Float, from: QrTheme, to: QrTheme,
    p: Float, origin: Offset, rMax: Float, intro: Boolean = false, shape: QrShape = QrShape()
) {
    val n = m.size
    val cell = side / n
    val logoCells = (((n * 0.27f).toInt()) or 1).coerceAtLeast(5)
    val logoFrom = (n - logoCells) / 2
    val logoTo = logoFrom + logoCells - 1
    val animating = p < 1f && rMax > 0f

    val modFrom = Path()
    val modTo = Path()
    val eyeFrom = Path().apply { fillType = PathFillType.EvenOdd }
    val eyeTo = Path().apply { fillType = PathFillType.EvenOdd }

    fun isEye(x: Int, y: Int) = (x < 8 && y < 8) || (x >= n - 8 && y < 8) || (x < 8 && y >= n - 8)
    fun isLogo(x: Int, y: Int) = x in logoFrom..logoTo && y in logoFrom..logoTo
    fun on(x: Int, y: Int) = x in 0 until n && y in 0 until n && m[y][x] && !isEye(x, y) && !isLogo(x, y)

    fun waveT(cx: Float, cy: Float): Float =
        if (!animating) 1f else ((p * (1f + WAVE) - hypot(cx - origin.x, cy - origin.y) / rMax) / WAVE).coerceIn(0f, 1f)

    for (y in 0 until n) for (x in 0 until n) {
        if (!on(x, y)) continue
        val cx = (x + 0.5f) * cell
        val cy = (y + 0.5f) * cell
        val t = waveT(cx, cy)
        if (intro && t <= 0f) continue   // الموجة ما وصلت لهالمربع بعد
        val s = if (intro) qrBackOut(t) else 1f - 0.55f * sin(PI.toFloat() * t)
        addDot(
            if (t >= 0.5f) modTo else modFrom, shape.dots, cx, cy, cell * s,
            on(x, y - 1), on(x, y + 1), on(x - 1, y), on(x + 1, y)
        )
    }
    // علامات الزوايا: حلقة خارجية + حلقة داخلية (ثقب) + مربع وسط بالشكل المختار
    for ((fx, fy, flip) in listOf(Triple(0, 0, false), Triple(n - 7, 0, true), Triple(0, n - 7, false))) {
        val cx = (fx + 3.5f) * cell
        val cy = (fy + 3.5f) * cell
        val t = waveT(cx, cy)
        if (intro && t <= 0f) continue
        val s = if (intro) qrBackOut(t) else 1f - 0.12f * sin(PI.toFloat() * t)
        val c = cell * s
        val ox = cx - 3.5f * c
        val oy = cy - 3.5f * c
        val path = if (t >= 0.5f) eyeTo else eyeFrom
        addEyeBox(path, ox, oy, 7 * c, shape.eye, c, 0, flip)
        addEyeBox(path, ox + c, oy + c, 5 * c, shape.eye, c, 1, flip)
        addEyeBox(path, ox + 2 * c, oy + 2 * c, 3 * c, shape.eye, c, 2, flip)
    }

    val img = shape.img
    if (img != null && shape.imgMode == 1) {
        // الصورة باهتة خلف الباركود
        drawImage(
            img, srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height),
            dstOffset = IntOffset.Zero, dstSize = IntSize(side.toInt(), side.toInt()),
            alpha = 0.42f, filterQuality = FilterQuality.Medium
        )
    }
    if (img != null && shape.imgMode == 0) {
        // النقاط والزوايا مصبوغة بالصورة نفسها، مع تغميق حتى يبقى التباين كافي للمسح
        val b = imageBrush(img, side)
        for (pt in listOf(modFrom, modTo, eyeFrom, eyeTo)) {
            drawPath(pt, b)
            drawPath(pt, Color.Black.copy(alpha = 0.5f))
        }
    } else {
        val bFrom = Brush.linearGradient(listOf(from.qrTop, from.qrBottom), Offset.Zero, Offset(side, side))
        val bTo = Brush.linearGradient(listOf(to.qrTop, to.qrBottom), Offset.Zero, Offset(side, side))
        if (animating) { drawPath(modFrom, bFrom); drawPath(eyeFrom, bFrom) }
        drawPath(modTo, bTo)
        drawPath(eyeTo, bTo)
    }
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

/** يقرأ الصورة من المعرض مصغّرة (≤ maxSide) ومصححة الدوران */
private fun loadOriented(ctx: Context, uri: Uri, maxSide: Int): Bitmap? = runCatching {
    val cr = ctx.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var s = 1
    while (max(bounds.outWidth, bounds.outHeight) / (s * 2) >= maxSide) s *= 2
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
    bmp
}.getOrNull()

/* ───────────────────────── قص الصورة (اختيار الجزء المطلوب) ───────────────────────── */

/** تحريك/تقريب الصورة داخل إطار مربع واختيار الجزء الي يصير شكل الباركود */
@Composable
private fun QrImageCropOverlay(src: Bitmap, fontFam: FontFamily, onCancel: () -> Unit, onDone: (Bitmap) -> Unit) {
    BackHandler(onBack = onCancel)
    val img = remember(src) { src.asImageBitmap() }
    var zoom by remember { mutableFloatStateOf(1f) }
    var off by remember { mutableStateOf(Offset.Zero) }
    var area by remember { mutableStateOf(IntSize.Zero) }

    fun frameOf(a: IntSize) = minOf(a.width, a.height) * 0.9f
    fun scaleOf(a: IntSize, z: Float) = frameOf(a) / minOf(src.width, src.height) * z
    // يمنع خروج الإطار عن حدود الصورة
    fun clampOff(o: Offset, z: Float, a: IntSize): Offset {
        if (a.width == 0 || a.height == 0) return o
        val s = scaleOf(a, z)
        val fs = frameOf(a)
        val mx = max(0f, (src.width * s - fs) / 2f)
        val my = max(0f, (src.height * s - fs) / 2f)
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }

    Box(
        Modifier.fillMaxSize().background(Color(0xFF0B0B10))
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding().navigationBarsPadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).springClickable(1.1f, onCancel).clip(CircleShape).background(Color.White.copy(0.14f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                Text(
                    "حدد الجزء المطلوب", color = Color.White, fontSize = 18.sp, fontFamily = fontFam, fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier.height(44.dp).springClickable(1.08f) {
                        val a = area
                        if (a.width == 0 || a.height == 0) return@springClickable
                        val fs = frameOf(a)
                        val s = scaleOf(a, zoom)
                        val left = a.width / 2f - src.width * s / 2f + off.x
                        val top = a.height / 2f - src.height * s / 2f + off.y
                        val sx = ((a.width - fs) / 2f - left) / s
                        val sy = ((a.height - fs) / 2f - top) / s
                        val sw = fs / s
                        val out = 800
                        val result = Bitmap.createBitmap(out, out, Bitmap.Config.ARGB_8888)
                        val cv = android.graphics.Canvas(result)
                        cv.drawColor(android.graphics.Color.WHITE)
                        val mtx = Matrix().apply { postTranslate(-sx, -sy); postScale(out / sw, out / sw) }
                        cv.drawBitmap(src, mtx, Paint(Paint.FILTER_BITMAP_FLAG))
                        onDone(result)
                    }.clip(CircleShape).background(Color.White).padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center
                ) { Text("استخدام", color = Color.Black, fontSize = 15.sp, fontFamily = fontFam, fontWeight = FontWeight.Bold) }
            }

            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .onSizeChanged { area = it }
                    .pointerInput(src) {
                        detectTransformGestures { _, pan, zm, _ ->
                            val nz = (zoom * zm).coerceIn(1f, 6f)
                            zoom = nz
                            off = clampOff(off + pan, nz, area)
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val fs = frameOf(IntSize(size.width.toInt(), size.height.toInt()))
                    val s = fs / minOf(src.width, src.height) * zoom
                    val dw = src.width * s
                    val dh = src.height * s
                    val left = size.width / 2f - dw / 2f + off.x
                    val top = size.height / 2f - dh / 2f + off.y
                    drawImage(
                        img, dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                        dstSize = IntSize(dw.roundToInt(), dh.roundToInt()), filterQuality = FilterQuality.Medium
                    )
                    val fl = (size.width - fs) / 2f
                    val ft = (size.height - fs) / 2f
                    val rad = CornerRadius(14.dp.toPx())
                    val hole = Path().apply { addRoundRect(RoundRect(fl, ft, fl + fs, ft + fs, rad)) }
                    clipPath(hole, clipOp = ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.66f)) }
                    for (k in 1..2) {
                        val d = fs * k / 3f
                        drawLine(Color.White.copy(0.30f), Offset(fl + d, ft), Offset(fl + d, ft + fs), 1.dp.toPx())
                        drawLine(Color.White.copy(0.30f), Offset(fl, ft + d), Offset(fl + fs, ft + d), 1.dp.toPx())
                    }
                    drawRoundRect(Color.White, Offset(fl, ft), Size(fs, fs), rad, style = Stroke(2.dp.toPx()))
                }
            }
            Text(
                "اسحب لتحريك الصورة، وقرّب بإصبعين للتكبير", color = Color.White.copy(0.65f), fontSize = 13.sp, fontFamily = fontFam,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp)
            )
        }
    }
}

/* ───────────────────────── تبويب «الشكل» ───────────────────────── */

private class ShapeUi(
    val dots: String, val eye: String, val imgKey: String, val imgMode: Int, val images: List<File>,
    val onDots: (String) -> Unit, val onEye: (String) -> Unit, val onImg: (String) -> Unit,
    val onAddImg: () -> Unit, val onDeleteImg: (File) -> Unit, val onMode: (Int) -> Unit
)

private val qrPreviewPattern = arrayOf("11010", "10111", "01110", "11011", "01101")

@Composable
private fun ShapeLabel(text: String, fontFam: FontFamily) {
    Text(
        text, color = Color.White.copy(0.75f), fontSize = 12.5.sp, fontFamily = fontFam, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 6.dp)
    )
}

@Composable
private fun ShapeTile(
    label: String, selected: Boolean, fontFam: FontFamily, onClick: () -> Unit,
    onDelete: (() -> Unit)? = null, content: @Composable BoxScope.() -> Unit
) {
    val sel by animateFloatAsState(if (selected) 1f else 0f, spring(0.6f, 400f), label = "shapeSel")
    val shape = RoundedCornerShape(18.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp)
                .springClickable(1.08f, onClick)
                .graphicsLayer { val s = 1f + 0.05f * sel; scaleX = s; scaleY = s }
                .clip(shape)
                .background(Color.White.copy(0.14f))
                .border((1f + 2f * sel).dp, Color.White.copy(0.28f + 0.72f * sel), shape)
        ) {
            content()
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopStart).padding(4.dp).size(18.dp).clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(12.dp)) }
            }
            if (onDelete != null) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(3.dp).size(20.dp).clip(CircleShape)
                        .background(Color.Black.copy(0.5f)).clickable { onDelete() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(12.dp)) }
            }
        }
        Text(label, color = Color.White.copy(0.85f), fontSize = 11.sp, fontFamily = fontFam, maxLines = 1, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun DotPreview(kind: String) {
    Canvas(Modifier.fillMaxSize().padding(9.dp)) {
        val rows = qrPreviewPattern
        val n = rows.size
        val cell = size.minDimension / n
        fun on(x: Int, y: Int) = y in 0 until n && x in 0 until n && rows[y][x] == '1'
        val path = Path()
        for (y in 0 until n) for (x in 0 until n) {
            if (on(x, y)) addDot(path, kind, (x + 0.5f) * cell, (y + 0.5f) * cell, cell, on(x, y - 1), on(x, y + 1), on(x - 1, y), on(x + 1, y))
        }
        drawPath(path, Color.White)
    }
}

@Composable
private fun EyePreview(kind: String) {
    Canvas(Modifier.fillMaxSize().padding(11.dp)) {
        val c = size.minDimension / 7f
        val path = Path().apply { fillType = PathFillType.EvenOdd }
        addEyeBox(path, 0f, 0f, 7 * c, kind, c, 0, false)
        addEyeBox(path, c, c, 5 * c, kind, c, 1, false)
        addEyeBox(path, 2 * c, 2 * c, 3 * c, kind, c, 2, false)
        drawPath(path, Color.White)
    }
}

@Composable
private fun ShapeImageThumb(file: File) {
    val thumb by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { decodeScaled(file, 220)?.asImageBitmap() }
    }
    val t = thumb
    if (t != null) Image(bitmap = t, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
}

@Composable
private fun ShapeChip(label: String, selected: Boolean, fontFam: FontFamily, onClick: () -> Unit) {
    Box(
        Modifier.height(34.dp).springClickable(1.06f, onClick).clip(CircleShape)
            .background(Color.White.copy(if (selected) 0.30f else 0.10f))
            .border(0.8.dp, Color.White.copy(if (selected) 0.8f else 0.2f), CircleShape)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, color = Color.White, fontSize = 12.5.sp, fontFamily = fontFam, fontWeight = FontWeight.Bold) }
}

@Composable
private fun ShapeTab(fontFam: FontFamily, ui: ShapeUi) {
    val pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
    Column(Modifier.fillMaxWidth().height(236.dp).verticalScroll(rememberScrollState())) {
        ShapeLabel("شكل النقاط", fontFam)
        LazyRow(Modifier.fillMaxWidth(), contentPadding = pad, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(qrDotShapes.size) { i ->
                val (k, l) = qrDotShapes[i]
                ShapeTile(l, ui.dots == k, fontFam, { ui.onDots(k) }) { DotPreview(k) }
            }
        }
        ShapeLabel("شكل الزوايا", fontFam)
        LazyRow(Modifier.fillMaxWidth(), contentPadding = pad, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(qrEyeShapes.size) { i ->
                val (k, l) = qrEyeShapes[i]
                ShapeTile(l, ui.eye == k, fontFam, { ui.onEye(k) }) { EyePreview(k) }
            }
        }
        ShapeLabel("شكل من صورة", fontFam)
        LazyRow(Modifier.fillMaxWidth(), contentPadding = pad, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                ShapeTile("من صورة", false, fontFam, ui.onAddImg) {
                    Icon(AppIcons.PlusIcon, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(28.dp))
                }
            }
            item {
                ShapeTile("بدون", ui.imgKey.isEmpty(), fontFam, { ui.onImg("") }) {
                    Icon(Icons.Filled.Close, null, tint = Color.White.copy(0.8f), modifier = Modifier.align(Alignment.Center).size(24.dp))
                }
            }
            items(ui.images.size) { i ->
                val f = ui.images[i]
                ShapeTile("صورة ${i + 1}", ui.imgKey == f.name, fontFam, { ui.onImg(f.name) }, { ui.onDeleteImg(f) }) { ShapeImageThumb(f) }
            }
        }
        if (ui.imgKey.isNotEmpty()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShapeChip("ألوان الصورة بالنقاط", ui.imgMode == 0, fontFam) { ui.onMode(0) }
                ShapeChip("الصورة خلف الباركود", ui.imgMode == 1, fontFam) { ui.onMode(1) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/* ───────────────────────── صورة المشاركة ───────────────────────── */

/** يرسم بطاقة الباركود كاملة كصورة PNG (نفس شكل الشاشة) */
private fun renderShareImage(
    ctx: Context, matrix: Array<BooleanArray>, theme: QrTheme, bgKey: String, bgImg: ImageBitmap?,
    avatar: ImageBitmap?, name: String, username: String, shape: QrShape = QrShape()
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
            drawQr(matrix, 720f, theme, theme, 1f, Offset.Zero, 0f, shape = shape)
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

/* ───────────────────────── الشاشة (الكرة الزجاجية) ───────────────────────── */

private enum class QrSheet { None, Brush, Share }

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

/** فقاعة زجاجية بزر + نص تحتها، تطفو وتطلع بنابض مرتد */
@Composable
private fun OrbAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, bd: com.kyant.backdrop.Backdrop, fontFam: FontFamily,
    enter: Float, bob: Float, phase: Float, tint: Color = Color.Unspecified, onClick: () -> Unit
) {
    Column(
        Modifier.graphicsLayer {
            val s = 0.25f + 0.75f * enter
            scaleX = s; scaleY = s
            alpha = enter.coerceIn(0f, 1f)
            translationY = sin((bob + phase) * 2f * PI.toFloat()) * 4.dp.toPx()
        },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GlassButton(onClick = onClick, backdrop = bd, tint = tint, pressScale = 1.2f, modifier = Modifier.size(66.dp)) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(27.dp))
        }
        Text(label, color = Color.White.copy(0.9f), fontSize = 12.sp, fontFamily = fontFam, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
    }
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
    var dotsKey by remember { mutableStateOf(QrStyle.loadDots(ctx)) }
    var eyeKey by remember { mutableStateOf(QrStyle.loadEye(ctx)) }
    var shapeImgKey by remember { mutableStateOf(QrStyle.loadShapeImg(ctx)) }
    var imgMode by remember { mutableIntStateOf(QrStyle.loadImgMode(ctx)) }
    var customShapes by remember { mutableStateOf(QrStyle.customShapes(ctx)) }
    var cropSrc by remember { mutableStateOf<Bitmap?>(null) }
    var sheet by remember { mutableStateOf(QrSheet.None) }
    var lastSheet by remember { mutableStateOf(QrSheet.Brush) }
    if (sheet != QrSheet.None) lastSheet = sheet
    var busyImage by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    val bgImage by produceState<ImageBitmap?>(null, bgKey) {
        value = if (!bgKey.startsWith("img:")) null else withContext(Dispatchers.IO) {
            decodeScaled(File(QrStyle.bgDir(ctx), bgKey.removePrefix("img:")), 1600)?.asImageBitmap()
        }
    }

    val shapeImage by produceState<ImageBitmap?>(null, shapeImgKey) {
        value = if (shapeImgKey.isEmpty()) null else withContext(Dispatchers.IO) {
            decodeScaled(File(QrStyle.shapeDir(ctx), shapeImgKey), 900)?.asImageBitmap()
        }
    }
    val qrShape = QrShape(dotsKey, eyeKey, shapeImage, imgMode)

    fun pickTheme(i: Int, at: Offset) {
        if (i == toIdx) return
        origin = at
        fromIdx = toIdx
        toIdx = i
        QrStyle.saveTheme(ctx, i)
        scope.launch { reveal.snapTo(0f); reveal.animateTo(1f, tween(950, easing = FastOutSlowInEasing)) }
        scope.launch { pulse.snapTo(0.90f); pulse.animateTo(1f, spring(0.34f, 220f)) }
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

    val shapePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val bmp = withContext(Dispatchers.IO) { loadOriented(ctx, uri, 1800) }
            if (bmp != null) cropSrc = bmp else AppToast.show("ما گدرت أفتح الصورة")
        }
    }

    BackHandler(onBack = onDismiss)
    BackHandler(enabled = sheet != QrSheet.None) { sheet = QrSheet.None }
    LaunchedEffect(copied) { if (copied) { delay(1700); copied = false } }

    val bd = rememberLayerBackdrop()
    val ffrom = themeAt(fromIdx)
    val fto = themeAt(toIdx)
    val accent by animateColorAsState(fto.qrTop, tween(700), label = "accent")

    // ───────── تسلسل الدخول ─────────
    // 1) الباج ينفخ من قطرة صغيرة لباج كامل (مع ارتداد)  2) صورة ← اسم ← يوزر
    // 3) قطرة تتكوّن تحت الباج وتنزل (تتمدد مثل قطرة ماي)  4) تنفجر لكرة زجاجية بتموّج وتناثر
    // 5) لوحة الباركود تطلع وبعدها مربعاته تظهر بموجة من النص  6) الفقاعات
    val eBadge = remember { Animatable(0f) }
    val eAvatar = remember { Animatable(0f) }
    val eName = remember { Animatable(0f) }
    val eUser = remember { Animatable(0f) }
    val dropForm = remember { Animatable(0f) }
    val fall = remember { Animatable(0f) }
    val landed = remember { Animatable(0f) }
    val ripple = remember { Animatable(0f) }
    val eOrb = remember { Animatable(0f) }
    val ePlate = remember { Animatable(0f) }
    val qrWave = remember { Animatable(0f) }
    val eHint = remember { Animatable(0f) }
    val eBtn = remember { List(3) { Animatable(0f) } }
    LaunchedEffect(Unit) {
        launch { eBadge.animateTo(1f, spring(0.52f, 170f)) }
        launch { delay(260); eAvatar.animateTo(1f, spring(0.45f, Spring.StiffnessLow)) }
        launch { delay(380); eName.animateTo(1f, spring(0.8f, Spring.StiffnessMediumLow)) }
        launch { delay(470); eUser.animateTo(1f, spring(0.8f, Spring.StiffnessMediumLow)) }
        delay(1050)
        dropForm.animateTo(1f, spring(0.55f, 420f))
        delay(60)
        fall.animateTo(1f, tween(520, easing = CubicBezierEasing(0.55f, 0f, 1f, 0.45f)))
        // لحظة الاصطدام
        launch { landed.animateTo(1f, tween(110)) }
        launch { ripple.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
        launch { eOrb.animateTo(1f, spring(0.36f, Spring.StiffnessLow)) }
        launch { delay(170); ePlate.animateTo(1f, spring(0.55f, Spring.StiffnessLow)) }
        launch { delay(380); qrWave.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) }
        launch { delay(650); eHint.animateTo(1f, spring(0.8f, Spring.StiffnessMediumLow)) }
        eBtn.forEachIndexed { i, a -> launch { delay(700L + i * 100L); a.animateTo(1f, spring(0.45f, Spring.StiffnessLow)) } }
    }

    // ── حركات مستمرة ──
    val inf = rememberInfiniteTransition(label = "orb")
    val morph by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "morph")
    val drift by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart), label = "drift")
    val bob by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart), label = "bob")
    val glint by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "glint")
    val ringAngle by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart), label = "ring")

    val hl = remember(scope) { InteractiveHighlight(animationScope = scope) }
    val badgeHl = remember(scope) { InteractiveHighlight(animationScope = scope) }

    Box(Modifier.fillMaxSize()) {
        // الخلفية + كتل ضوء عايمة
        Canvas(
            Modifier.fillMaxSize().layerBackdrop(bd).onGloballyPositioned {
                bgPos = it.positionInRoot()
                bgSize = Size(it.size.width.toFloat(), it.size.height.toFloat())
            }
        ) {
            drawQrBackground(ffrom, fto, reveal.value, origin - bgPos)
            drawQrDecor(bgKey, bgImage)
            val w = size.width
            val h = size.height
            val tt = drift * 2f * PI.toFloat()
            fun blob(cx: Float, cy: Float, r: Float, c: Color) =
                drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), center = Offset(cx, cy), radius = r), radius = r, center = Offset(cx, cy))
            blob(w * (0.22f + 0.14f * sin(tt)), h * (0.30f + 0.06f * cos(tt)), w * 0.60f, lerp(fto.bgTop, Color.White, 0.55f).copy(alpha = 0.60f))
            blob(w * (0.82f + 0.10f * cos(tt)), h * (0.52f + 0.08f * sin(tt)), w * 0.62f, lerp(fto.qrTop, Color.White, 0.25f).copy(alpha = 0.55f))
            blob(w * (0.45f + 0.16f * sin(tt + 2f)), h * (0.84f + 0.05f * cos(tt)), w * 0.55f, lerp(fto.bgTop, Color.White, 0.30f).copy(alpha = 0.45f))
        }

        Box(
            Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }
                .statusBarsPadding().navigationBarsPadding()
        ) {
            val contentScale by animateFloatAsState(if (sheet != QrSheet.None) 0.60f else 1f, spring(0.85f, 300f), label = "qrScale")
            Column(
                Modifier.fillMaxSize()
                    .graphicsLayer { scaleX = contentScale; scaleY = contentScale; transformOrigin = TransformOrigin(0.5f, 0f) }
                    .verticalScroll(rememberScrollState())
                    .padding(top = 64.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ───── الباج: صورة + اسم + يوزر بكبسولة زجاجية وحدها ─────
                Box(
                    Modifier
                        .graphicsLayer {
                            val e = eBadge.value
                            val p = badgeHl.pressProgress
                            val sq = sin(PI.toFloat() * e.coerceIn(0f, 1f))
                            val s = (0.45f + 0.55f * e.coerceIn(0f, 1.15f)) * (1f + 0.035f * p)
                            scaleX = s * (1f + 0.07f * sq)
                            scaleY = s * (1f - 0.10f * sq)
                            alpha = (e * 4f).coerceIn(0f, 1f)
                            translationY = (1f - e) * -56.dp.toPx()
                        }
                        .then(badgeHl.gestureModifier)
                        .drawBackdrop(
                            backdrop = bd,
                            shape = { Capsule() },
                            effects = {
                                vibrancy()
                                blur((6f.dp.toPx()) * GlassFx.blurK)
                                lens((12f.dp.toPx()) * GlassFx.refractK, (24f.dp.toPx()) * GlassFx.refractK, chromaticAberration = true)
                            },
                            highlight = { Highlight.Default.copy(alpha = 0.7f + 0.3f * badgeHl.pressProgress) },
                            shadow = { Shadow(radius = 18.dp, color = Color.Black.copy(alpha = 0.22f)) },
                            innerShadow = { InnerShadow(radius = 8.dp, alpha = 0.25f + 0.3f * badgeHl.pressProgress) },
                            onDrawSurface = {
                                glassSurfaceLayer { drawRect(Color.White.copy(0.16f)) }
                                val w = size.width
                                val x = -w * 0.5f + w * 2f * glint
                                drawRect(
                                    Brush.linearGradient(
                                        listOf(Color.Transparent, Color.White.copy(0.30f), Color.Transparent),
                                        start = Offset(x - w * 0.15f, 0f), end = Offset(x + w * 0.15f, size.height)
                                    )
                                )
                            }
                        )
                        .clip(RoundedCornerShape(50))
                        .then(badgeHl.modifier)
                        // العرض ينتفخ من دائرة (قطرة) لباج كامل، ويتجاوز شوي ثم يستقر (سائل)
                        .layout { measurable, constraints ->
                            val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                            val e = eBadge.value
                            val w = (p.height + (p.width - p.height) * e).toInt().coerceAtLeast(p.height / 2)
                            layout(w, p.height) { p.place((w - p.width) / 2, 0) }
                        }
                ) {
                    Row(
                        Modifier.padding(start = 8.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(56.dp)
                                .graphicsLayer {
                                    val s = eAvatar.value
                                    scaleX = s; scaleY = s
                                    rotationZ = (1f - s.coerceIn(0f, 1f)) * -120f
                                    alpha = s.coerceIn(0f, 1f)
                                }
                                .drawBackdrop(
                                    backdrop = bd,
                                    shape = { CircleShape },
                                    effects = {
                                        vibrancy()
                                        blur((3f.dp.toPx()) * GlassFx.blurK)
                                        lens((8f.dp.toPx()) * GlassFx.refractK, (16f.dp.toPx()) * GlassFx.refractK, chromaticAberration = true)
                                    },
                                    highlight = { Highlight.Default },
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
                            Box(Modifier.size(47.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                                val a = avatarImg
                                if (a != null) {
                                    Image(bitmap = a, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                } else {
                                    Box(
                                        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(fto.qrTop, fto.qrBottom))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(name.trim().take(1).ifBlank { "؟" }, color = Color.White, fontSize = 22.sp, fontFamily = fontFam, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(
                                name.ifBlank { "حسابي" }, color = Color.White, fontSize = 21.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontFamily = fontFam, fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.widthIn(max = 190.dp).graphicsLayer {
                                    alpha = eName.value.coerceIn(0f, 1f)
                                    translationY = (1f - eName.value) * 14.dp.toPx()
                                }
                            )
                            if (username.isNotBlank()) {
                                Text(
                                    "@$username", color = Color.White.copy(0.88f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    fontFamily = fontFam, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 1.dp).widthIn(max = 190.dp).graphicsLayer {
                                        alpha = eUser.value.coerceIn(0f, 1f)
                                        translationY = (1f - eUser.value) * 14.dp.toPx()
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                // ───── منطقة القطرة + الكرة ─────
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val d = minOf(300.dp, maxWidth - 56.dp)
                    val areaH = d + 44.dp
                    val plate = d * 0.64f
                    Box(Modifier.fillMaxWidth().height(areaH), contentAlignment = Alignment.Center) {

                        // موجات + رذاذ لحظة الاصطدام
                        Canvas(Modifier.fillMaxSize()) {
                            val r = ripple.value
                            if (r > 0f && r < 1f) {
                                val c = Offset(size.width / 2f, size.height / 2f)
                                val base = d.toPx() / 2f
                                for (k in 0..1) {
                                    val rk = ((r - k * 0.18f) / (1f - k * 0.18f)).coerceIn(0f, 1f)
                                    if (rk > 0f) drawCircle(
                                        Color.White.copy(alpha = 0.55f * (1f - rk)),
                                        radius = base + 90.dp.toPx() * rk, center = c,
                                        style = Stroke(width = (4f * (1f - rk) + 1f).dp.toPx())
                                    )
                                }
                                val ease = 1f - (1f - r) * (1f - r)
                                for (k in 0 until 10) {
                                    val ang = k * (2f * PI.toFloat() / 10f) + 0.3f
                                    val rr = base * 0.85f + 120.dp.toPx() * ease
                                    val pos = Offset(c.x + cos(ang) * rr, c.y + sin(ang) * rr * 0.92f)
                                    drawCircle(
                                        Color.White.copy(0.9f * (1f - r)),
                                        radius = ((3.5f + (k % 3)).dp.toPx()) * (1f - r) + 0.5f, center = pos
                                    )
                                }
                            }
                        }

                        // القطرة الزجاجية: تتكوّن تحت الباج، تتمدد بالسقوط، وتختفي لحظة الاصطدام
                        Box(
                            Modifier.size(24.dp)
                                .graphicsLayer {
                                    val form = dropForm.value
                                    val f = fall.value
                                    val travel = (areaH / 2 + 10.dp).toPx()
                                    translationY = -travel * (1f - f)
                                    scaleX = form * (1f - 0.38f * f)
                                    scaleY = form * (1f + 1.4f * f)
                                    alpha = if (form < 0.01f) 0f else (1f - landed.value).coerceIn(0f, 1f)
                                }
                                .drawBackdrop(
                                    backdrop = bd,
                                    shape = { CircleShape },
                                    effects = {
                                        vibrancy()
                                        blur((1f.dp.toPx()) * GlassFx.blurK)
                                        lens((6f.dp.toPx()) * GlassFx.refractK, (12f.dp.toPx()) * GlassFx.refractK, chromaticAberration = true)
                                    },
                                    highlight = { Highlight.Default },
                                    shadow = { Shadow(radius = 8.dp, color = Color.Black.copy(alpha = 0.22f)) },
                                    innerShadow = { InnerShadow(radius = 4.dp, alpha = 0.4f) },
                                    onDrawSurface = { drawRect(Color.White.copy(0.34f)) }
                                )
                        )

                        // الكرة الزجاجية: تنفجر من القطرة بارتداد جيلي، وشكلها يتنفس، وتميل مع إصبعك
                        Box(
                            Modifier.size(d)
                                .graphicsLayer {
                                    val e = eOrb.value
                                    val p = hl.pressProgress
                                    val w = size.width.coerceAtLeast(1f)
                                    val h = size.height.coerceAtLeast(1f)
                                    val dx = hl.offset.x / w
                                    val dy = hl.offset.y / h
                                    val base = (0.08f + 0.92f * e) * pulse.value * (1f + 0.05f * p)
                                    // ارتداد جيلي: لما يتجاوز النابض 1 تنضغط عمودياً وتتمدد أفقياً والعكس
                                    val jelly = ((e - 1f) * 1.3f).coerceIn(-0.25f, 0.25f)
                                    scaleX = base * (1f + jelly + 0.07f * kotlin.math.abs(dx) - 0.03f * kotlin.math.abs(dy))
                                    scaleY = base * (1f - jelly - 0.04f * kotlin.math.abs(dx) + 0.07f * kotlin.math.abs(dy))
                                    alpha = (e * 6f).coerceIn(0f, 1f)
                                    cameraDistance = 18f * density
                                    rotationY = dx * 22f
                                    rotationX = -dy * 22f
                                }
                                .then(hl.gestureModifier)
                                .drawBackdrop(
                                    backdrop = bd,
                                    shape = { RoundedCornerShape(percent = (50 - (1f - morph) * 14f).toInt().coerceIn(30, 50)) },
                                    effects = {
                                        vibrancy()
                                        blur((3f.dp.toPx()) * GlassFx.blurK)
                                        lens((26f.dp.toPx()) * GlassFx.refractK, (52f.dp.toPx()) * GlassFx.refractK, chromaticAberration = true)
                                    },
                                    highlight = { Highlight.Ambient.copy(alpha = 0.8f + 0.2f * hl.pressProgress) },
                                    shadow = { Shadow(radius = 30.dp, color = Color.Black.copy(alpha = 0.28f)) },
                                    innerShadow = { InnerShadow(radius = 14.dp, alpha = 0.35f + 0.3f * hl.pressProgress) },
                                    onDrawSurface = {
                                        glassSurfaceLayer { drawRect(Color.White.copy(0.10f)) }
                                        val w = size.width
                                        val h = size.height
                                        drawCircle(
                                            Brush.radialGradient(listOf(Color.White.copy(0.45f), Color.Transparent), center = Offset(w * 0.28f, h * 0.22f), radius = w * 0.38f),
                                            radius = w * 0.38f, center = Offset(w * 0.28f, h * 0.22f)
                                        )
                                        drawCircle(
                                            Brush.radialGradient(listOf(Color.Black.copy(0.16f), Color.Transparent), center = Offset(w * 0.78f, h * 0.84f), radius = w * 0.40f),
                                            radius = w * 0.40f, center = Offset(w * 0.78f, h * 0.84f)
                                        )
                                        val x = -w * 0.5f + w * 2f * glint
                                        drawRect(
                                            Brush.linearGradient(
                                                listOf(Color.Transparent, Color.White.copy(0.28f), Color.Transparent),
                                                start = Offset(x - w * 0.15f, 0f), end = Offset(x + w * 0.15f, h * 0.6f)
                                            )
                                        )
                                    }
                                )
                                .then(hl.modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            // لوحة الباركود: تطلع بارتداد، ومربعاتها تظهر بموجة سائلة من النص
                            Box(
                                Modifier.size(plate)
                                    .graphicsLayer {
                                        translationX = -hl.offset.x * 0.10f
                                        translationY = -hl.offset.y * 0.10f
                                        val e = ePlate.value
                                        val s = 0.3f + 0.7f * e
                                        scaleX = s; scaleY = s
                                        alpha = (e * 3f).coerceIn(0f, 1f)
                                    }
                                    .shadow(16.dp, RoundedCornerShape(26.dp), ambientColor = Color.Black.copy(0.35f), spotColor = Color.Black.copy(0.35f))
                                    .clip(RoundedCornerShape(26.dp)).background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                val mx = matrix
                                if (mx != null) {
                                    Canvas(Modifier.fillMaxSize().padding(11.dp).onGloballyPositioned { qrPos = it.positionInRoot() }) {
                                        val intro = qrWave.value < 1f
                                        if (intro) {
                                            drawQr(
                                                mx, size.width, fto, fto, qrWave.value,
                                                Offset(size.width / 2f, size.height / 2f), hypot(size.width, size.height) / 2f, true, qrShape
                                            )
                                        } else {
                                            val ob = origin - bgPos
                                            val rMax = maxOf(
                                                hypot(ob.x, ob.y), hypot(bgSize.width - ob.x, ob.y),
                                                hypot(ob.x, bgSize.height - ob.y), hypot(bgSize.width - ob.x, bgSize.height - ob.y)
                                            ).coerceAtLeast(1f)
                                            drawQr(mx, size.width, ffrom, fto, reveal.value, origin - qrPos, rMax, shape = qrShape)
                                        }
                                    }
                                    Box(
                                        Modifier.fillMaxWidth(0.22f).aspectRatio(1f)
                                            .graphicsLayer {
                                                val s = qrWave.value.coerceIn(0f, 1f)
                                                scaleX = s; scaleY = s; alpha = s
                                            }
                                            .clip(RoundedCornerShape(14.dp)).background(Color.White),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = painterResource(ThemeSettings.icons[ThemeSettings.iconIndex].previewRes),
                                            contentDescription = null, contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(0.88f).clip(RoundedCornerShape(11.dp))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    "امسح الباركود بكاميرا الجوال أو كوكل ليفتح حسابي بالتطبيق",
                    color = Color.White.copy(0.75f), fontSize = 12.5.sp, fontFamily = fontFam,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp).graphicsLayer {
                        alpha = eHint.value.coerceIn(0f, 1f)
                        translationY = (1f - eHint.value) * 16.dp.toPx()
                    }
                )
                Spacer(Modifier.height(16.dp))

                // ───── فقاعات زجاجية تطفو ─────
                Row(horizontalArrangement = Arrangement.spacedBy(30.dp), verticalAlignment = Alignment.Top) {
                    OrbAction(AppIcons.PaintBrush, "التصميم", bd, fontFam, eBtn[0].value, bob, 0f) { sheet = QrSheet.Brush }
                    OrbAction(AppIcons.Share, "مشاركة", bd, fontFam, eBtn[1].value, bob, 0.33f) { sheet = QrSheet.Share }
                    OrbAction(
                        if (copied) Icons.Filled.Check else AppIcons.CopyIcon, if (copied) "اننسخ" else "نسخ", bd, fontFam,
                        eBtn[2].value, bob, 0.66f, tint = if (copied) accent else Color.Unspecified
                    ) {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("link", link))
                        copied = true
                    }
                }
            }

            // ───── اللوحة السفلية الزجاجية ─────
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
                    if (lastSheet == QrSheet.Brush) {
                        BrushSheet(
                            bd = bd, fontFam = fontFam, selectedTheme = toIdx, bgKey = bgKey, customBgs = customBgs,
                            shape = ShapeUi(
                                dots = dotsKey, eye = eyeKey, imgKey = shapeImgKey, imgMode = imgMode, images = customShapes,
                                onDots = { dotsKey = it; QrStyle.saveDots(ctx, it) },
                                onEye = { eyeKey = it; QrStyle.saveEye(ctx, it) },
                                onImg = { shapeImgKey = it; QrStyle.saveShapeImg(ctx, it) },
                                onAddImg = { shapePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                onDeleteImg = { f ->
                                    val wasSelected = shapeImgKey == f.name
                                    f.delete()
                                    customShapes = QrStyle.customShapes(ctx)
                                    if (wasSelected) { shapeImgKey = ""; QrStyle.saveShapeImg(ctx, "") }
                                },
                                onMode = { imgMode = it; QrStyle.saveImgMode(ctx, it) }
                            ),
                            onTheme = { i, at -> pickTheme(i, at) },
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
                                        renderShareImage(ctx, mx, themeAt(toIdx), bgKey, bgImage, avatarImg, name, username, qrShape)
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

        // شاشة قص الصورة (تظهر فوق كل شي)
        cropSrc?.let { bmp ->
            Box(Modifier.fillMaxSize().zIndex(30f)) {
                QrImageCropOverlay(
                    src = bmp, fontFam = fontFam,
                    onCancel = { cropSrc = null },
                    onDone = { cropped ->
                        cropSrc = null
                        scope.launch {
                            val f = withContext(Dispatchers.IO) {
                                runCatching {
                                    val out = File(QrStyle.shapeDir(ctx), "sh_${System.currentTimeMillis()}.jpg")
                                    out.outputStream().use { cropped.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                                    out
                                }.getOrNull()
                            }
                            if (f != null) {
                                customShapes = QrStyle.customShapes(ctx)
                                shapeImgKey = f.name
                                QrStyle.saveShapeImg(ctx, f.name)
                            } else AppToast.show("ما گدرت أحفظ الصورة")
                        }
                    }
                )
            }
        }
    }
}

/** تبويب زجاجي: المؤشر ينزلق بنابض وعليه عدسة + لمعة (نفس تابات التطبيق) */
@Composable
private fun GlassSegmented(
    bd: com.kyant.backdrop.Backdrop, labels: List<String>, selected: Int, fontFam: FontFamily,
    onSelect: (Int) -> Unit, modifier: Modifier = Modifier
) {
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val pos by animateFloatAsState(selected.toFloat(), spring(0.72f, 420f), label = "seg")
    Box(
        modifier.height(42.dp).clip(CircleShape)
            .background(Color.White.copy(0.10f))
            .border(0.8.dp, Color.White.copy(0.18f), CircleShape)
            .padding(3.dp)
    ) {
        Box(
            Modifier.align(Alignment.CenterStart).fillMaxWidth(1f / labels.size).fillMaxHeight()
                .graphicsLayer { translationX = (if (isLtr) 1f else -1f) * pos * size.width }
                .drawBackdrop(
                    backdrop = bd,
                    shape = { Capsule() },
                    effects = {
                        lens((6f.dp.toPx()) * GlassFx.refractK, (10f.dp.toPx()) * GlassFx.refractK, chromaticAberration = true)
                    },
                    highlight = { Highlight.Default.copy(alpha = 0.9f) },
                    innerShadow = { InnerShadow(radius = 4.dp, alpha = 0.3f) },
                    onDrawSurface = { drawRect(Color.White.copy(0.22f)) }
                )
        )
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { i, l ->
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        l, color = Color.White.copy(if (i == selected) 1f else 0.62f), fontSize = 14.sp,
                        fontFamily = fontFam, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun BrushSheet(
    bd: com.kyant.backdrop.Backdrop, fontFam: FontFamily, selectedTheme: Int, bgKey: String, customBgs: List<File>, shape: ShapeUi,
    onTheme: (Int, Offset) -> Unit, onBg: (String) -> Unit, onAddBg: () -> Unit,
    onDeleteBg: (File) -> Unit, onDone: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassSegmented(bd, listOf("الألوان", "الشكل", "الخلفية"), tab, fontFam, { tab = it }, Modifier.width(270.dp))
        Spacer(Modifier.weight(1f))
        GlassButton(onClick = onDone, backdrop = bd, pressScale = 1.1f, modifier = Modifier.height(40.dp).width(72.dp)) {
            Text("تم", color = Color.White, fontSize = 14.sp, fontFamily = fontFam, fontWeight = FontWeight.Bold)
        }
    }
    Spacer(Modifier.height(12.dp))
    if (tab == 0) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(52.dp),
            modifier = Modifier.fillMaxWidth().height(208.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(qrThemeCount) { i ->
                val t = themeAt(i)
                var center by remember { mutableStateOf(Offset.Zero) }
                val sel = i == selectedTheme
                val ring by animateFloatAsState(if (sel) 1f else 0f, spring(0.6f, 400f), label = "ring$i")
                Box(
                    Modifier.size(48.dp)
                        .springClickable(1.14f) { onTheme(i, center) }
                        .graphicsLayer { val s = 1f + 0.08f * ring; scaleX = s; scaleY = s }
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(t.bgTop, t.bgBottom)))
                        .border((1f + 2f * ring).dp, Color.White.copy(0.3f + 0.7f * ring), CircleShape)
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
    } else if (tab == 1) {
        ShapeTab(fontFam, shape)
    } else {
        val preview = themeAt(selectedTheme)
        LazyRow(
            Modifier.fillMaxWidth().height(208.dp),
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
                BgTile(presets[i].first, presets[i].second, preview, bgKey == presets[i].first, null, fontFam, { onBg(presets[i].first) }, null)
            }
            items(customBgs.size) { i ->
                val f = customBgs[i]
                val k = "img:" + f.name
                BgTile(k, "", preview, bgKey == k, f, fontFam, { onBg(k) }, { onDeleteBg(f) })
            }
        }
    }
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
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontFamily = fontFam, fontWeight = FontWeight.Bold)
            if (sub != null) Text(sub, color = Color.White.copy(0.6f), fontSize = 12.sp, fontFamily = fontFam, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
