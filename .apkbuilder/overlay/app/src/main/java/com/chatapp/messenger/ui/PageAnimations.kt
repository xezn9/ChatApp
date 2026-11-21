package com.chatapp.messenger.ui

/*
 * PageAnimations.kt — حركات الصفحات (عناصر الصفحة + فتح الصفحات الفرعية).
 * منقولة من HyperSpring (HyperSpringAnimations.kt) بنفس المنحنيات والمدد:
 *
 *  - Cascade + Reveal : ظهور عناصر الصفحة بتتابع (يرتفع شوي ويتلاشى للداخل)        — صفحة الإعدادات الرئيسية
 *  - SplitTitle       : عنوان كبير يدخل حرف حرف (كلمة كلمة للعربي حتى ما تتفكك الحروف) — عنوان "الإعدادات"
 *  - Emerge / FadeIn  : عناصر الصفحة الفرعية تطلع من نقطة الفتح، صغيرة وضبابية، وتنحدر لمكانها
 *  - PushMorph        : فتح صفحة فرعية: القديمة تذوب (blur + تصغير + تلاشي)، والصف المضغوط يكبر ليصير الصفحة،
 *                       والعناصر تطلع منه. الرجوع: الصفحة تنزلق للجنب والقديمة ترجع.
 *
 * StaggerIn (Anim.kt) صار يستعمل Emerge، فكل الصفحات الي تستعمله تاخذ الحركة الجديدة تلقائياً.
 * يحتاج أندرويد 12+ للـ blur (قبله: نفس الحركة بدون تغبيش).
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.ui.theme.ThemeSettings
import com.chatapp.messenger.ui.theme.isAppDark
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

// =====================================================================
//  الثوابت والمنحنيات (Motion.java / PushMorph.java)
// =====================================================================
object PageEase {
    /** PathInterpolator(0.2, 0, 0, 1): "SETTLE" */
    val SETTLE = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Motion.EXPO_OUT */
    val EXPO_OUT = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** Motion.POWER4_OUT */
    val POWER4_OUT = CubicBezierEasing(0.165f, 0.84f, 0.44f, 1f)
}

private fun pgSeg(t: Float, a: Float, b: Float): Float = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun pgLerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** تغبيش طبقة (أندرويد 12+). تحت نص بكسل يُلغى (نفس قاعدة PushMorph.blur). */
private fun pgBlur(r: Float): RenderEffect? =
    if (android.os.Build.VERSION.SDK_INT < 31 || r < 0.5f) null else BlurEffect(r, r, TileMode.Decal)

// =====================================================================
//  Cascade + Reveal
// =====================================================================

/**
 * Motion.cascade: كل عنصر يبدأ بعد اللي قبله بشوي.
 * البطاقات الي بيها صفوف ([rows]) صفوفها تظهر وراها مباشرة.
 */
class Cascade(private val base: Int) {
    private var d = base

    /** التأخير (ms) الي لازم يبدأ عنده العنصر التالي */
    fun take(rows: Int = 0): Int {
        val mine = d
        d += minOf(rows, 8) * 30
        d += 55
        if (d - base > 700) d = base + 700   // بالصفحات الطويلة ما يطوّل
        return mine
    }
}

/** Motion.reveal: يتلاشى للداخل وهو يرتفع شوي (760 ms, power4.out) */
@Composable
fun Reveal(
    delayMs: Int,
    modifier: Modifier = Modifier,
    dy: Dp = 22.dp,
    content: @Composable () -> Unit
) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        a.animateTo(1f, tween(760, easing = PageEase.POWER4_OUT))
    }
    Box(
        modifier.graphicsLayer {
            alpha = a.value
            translationY = (1f - a.value) * dy.toPx()
        }
    ) { content() }
}

// =====================================================================
//  SplitTitle
// =====================================================================
private const val SPLIT_CHAR_MS = 820
private const val SPLIT_LETTER_STAGGER = 26
private const val SPLIT_WORD_STAGGER = 90

private class SplitUnit(val rect: Rect, val glyph: TextLayoutResult, val line: Int)

private fun pgIsArabicScript(s: String): Boolean = s.any {
    it.code in 0x0600..0x06FF || it.code in 0x0750..0x077F || it.code in 0xFB50..0xFDFF || it.code in 0xFE70..0xFEFF
}

/**
 * SplitTitle.java: عنوان كبير يدخل حرف حرف: كل سطر "شباك" والحروف تطلع من تحته وهي تدور شوي.
 * بالعربي نقسم على الكلمات (مو الحروف) لأن الحرف المنفرد يتغيّر شكله ويتفكك الاتصال بين الحروف.
 *
 * @param gradient إذا انعطت ألوان يتلوّن العنوان بتدرج (قطري) على كل عرضه بدل [color]
 */
@Composable
fun SplitTitle(
    text: String,
    delayMs: Int,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 40.sp,
    color: Color = Color.White,
    gradient: List<Color>? = null,
    fontWeight: FontWeight = FontWeight.Bold,
    fontFamily: FontFamily? = null
) {
    val measurer = rememberTextMeasurer()
    val words = remember(text) { pgIsArabicScript(text) }
    val style = remember(fontSize, color, gradient, fontWeight, fontFamily, words) {
        val c = if (gradient != null) Color.White else color
        if (words) TextStyle(color = c, fontSize = fontSize, fontWeight = fontWeight, fontFamily = fontFamily)
        else TextStyle(color = c, fontSize = fontSize, fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = (-0.025f).em)
    }
    val stagger = if (words) SPLIT_WORD_STAGGER else SPLIT_LETTER_STAGGER
    val t = remember { Animatable(-1f) }

    BoxWithConstraints(modifier) {
        val maxW = constraints.maxWidth
        val layout = remember(text, maxW, style) {
            measurer.measure(text, style, constraints = Constraints(maxWidth = maxW))
        }
        val units = remember(layout, text, words) {
            val list = ArrayList<SplitUnit>()
            for (li in 0 until layout.lineCount) {
                val end = layout.getLineEnd(li)
                var i = layout.getLineStart(li)
                while (i < end) {
                    val ch = text[i]
                    if (ch == ' ' || ch == '\n') { i++; continue }
                    if (!words) {
                        list += SplitUnit(layout.getBoundingBox(i), measurer.measure(ch.toString(), style), li)
                        i++
                    } else {
                        var j = i
                        var r = layout.getBoundingBox(i)
                        while (j < end && text[j] != ' ' && text[j] != '\n') {
                            val b = layout.getBoundingBox(j)
                            r = Rect(min(r.left, b.left), min(r.top, b.top), max(r.right, b.right), max(r.bottom, b.bottom))
                            j++
                        }
                        list += SplitUnit(r, measurer.measure(text.substring(i, j), style), li)
                        i = j
                    }
                }
            }
            list
        }
        val total = SPLIT_CHAR_MS + stagger * units.size
        LaunchedEffect(text, total) {
            t.snapTo(-1f)
            delay(delayMs.toLong())
            t.snapTo(0f)
            t.animateTo(total.toFloat(), tween(total, easing = LinearEasing))
        }
        val hDp = with(LocalDensity.current) { layout.size.height.toDp() }

        Canvas(
            Modifier.fillMaxWidth().height(hDp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            val tt = t.value
            if (tt < 0f) return@Canvas                       // الحروف ما طلعت بعد
            if (tt >= total) {
                drawText(layout)                              // وصلت كلها: العنوان كامل مرة وحدة
            } else {
                for ((k, u) in units.withIndex()) {
                    val p = ((tt - k * stagger) / SPLIT_CHAR_MS).coerceIn(0f, 1f)
                    if (p <= 0f) continue
                    val e = PageEase.EXPO_OUT.transform(p)
                    val top = layout.getLineTop(u.line)
                    val lineH = layout.getLineBottom(u.line) - top
                    val dy = (1f - e) * lineH
                    // "شباك" السطر: الي تحته ما يبين (الحروف تطلع من هناك)
                    clipRect(0f, top - lineH * 0.2f, size.width, top + lineH) {
                        rotate(14f * (1f - e), pivot = Offset(u.rect.center.x, u.rect.bottom + dy)) {
                            drawText(
                                u.glyph,
                                topLeft = Offset(u.rect.left, u.rect.top + dy),
                                alpha = min(1f, p * 2.5f)
                            )
                        }
                    }
                }
            }
            if (gradient != null && gradient.size >= 2) {
                var l = Float.MAX_VALUE
                var r = 0f
                for (li in 0 until layout.lineCount) {
                    l = min(l, layout.getLineLeft(li)); r = max(r, layout.getLineRight(li))
                }
                if (r > l) {
                    val h = layout.size.height.toFloat()
                    drawRect(
                        Brush.linearGradient(gradient, start = Offset(l, 0f), end = Offset(r, h)),
                        topLeft = Offset(l, 0f), size = Size(r - l, h), blendMode = BlendMode.SrcIn
                    )
                }
            }
        }
    }
}

// =====================================================================
//  Emerge / FadeIn (عناصر الصفحة الفرعية)
// =====================================================================

/** من وين تطلع العناصر (مركز الي انضغط)، بإحداثيات الشاشة (root). Unspecified = بدون انزياح. */
val LocalPushOrigin = compositionLocalOf { Offset.Unspecified }

/**
 * عنصر يطلع من نقطة الفتح: من صغير وضبابي ومنزاح باتجاهها، لحاد بمكانه
 * (PushMorph.emerge: 680 ms SETTLE، تصغير 0.84، blur 16dp).
 */
@Composable
fun Emerge(
    delayMs: Int,
    modifier: Modifier = Modifier,
    sharpen: Boolean = true,
    content: @Composable () -> Unit
) {
    val origin = LocalPushOrigin.current
    val a = remember { Animatable(0f) }
    var center by remember { mutableStateOf(Offset.Unspecified) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        a.animateTo(1f, tween(680, easing = PageEase.SETTLE))
    }
    Box(
        modifier
            .onGloballyPositioned { if (center.isUnspecified) center = it.boundsInRoot().center }
            .graphicsLayer {
                val t = a.value
                val maxDy = 180.dp.toPx()
                var dx = 0f
                var dy = 0f
                if (origin.isSpecified && center.isSpecified) {
                    dx = (origin.x - center.x) * 0.25f
                    dy = ((origin.y - center.y) * 0.45f).coerceIn(-maxDy, maxDy)
                }
                alpha = minOf(1f, t * 1.8f)
                translationX = dx * (1f - t)
                translationY = dy * (1f - t)
                val s = 0.84f + 0.16f * t
                scaleX = s
                scaleY = s
                renderEffect = if (sharpen && !PerfGate.lite) pgBlur(16.dp.toPx() * (1f - t)) else null
            }
    ) { content() }
}

/** صف داخل بطاقة: يظهر وهو يتحدّد بدون ما يتحرك (البطاقة نفسها تتحرك). */
@Composable
fun FadeIn(delayMs: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        a.animateTo(1f, tween(520, easing = PageEase.SETTLE))
    }
    Box(
        modifier.graphicsLayer {
            alpha = a.value
            renderEffect = if (PerfGate.lite) null else pgBlur(10.dp.toPx() * (1f - a.value))
        }
    ) { content() }
}

// =====================================================================
//  PushMorph: فتح/إغلاق صفحة فرعية
// =====================================================================

/** حالة فتح صفحة فرعية (نفس PushController بس مربوطة بـ visible). */
@Stable
class PushMorphState {
    /** 0..1: الصفحة الي تحت تذوب (blur 22dp, تصغير 0.96, تلاشي). 380 ms SETTLE. */
    val under = Animatable(0f)

    /** 0..1: الصف الي يكبر / دائرة الضوء. 640 ms EXPO_OUT (700 ms SETTLE للدائرة). */
    val bloom = Animatable(0f)

    /** 0..1: الصفحة تنزلق للجنب عند الرجوع. */
    val pop = Animatable(0f)

    /** مكان الصف المضغوط (إحداثيات الشاشة) أو null */
    var origin by mutableStateOf<Rect?>(null)
        internal set

    /** الطبقة مركّبة (من لحظة الفتح لين تخلص حركة الرجوع) */
    var present by mutableStateOf(false)
        internal set
}

/**
 * يشغّل حركة الفتح عند [visible] = true (من [from]) وحركة الرجوع عند false.
 * استعمل [pushUnder] على الصفحة الي تحت، و [PushMorphLayer] للصفحة الفرعية.
 */
@Composable
fun rememberPushMorph(visible: Boolean, from: Rect?): PushMorphState {
    val s = remember { PushMorphState() }
    LaunchedEffect(visible) {
        if (visible) {
            s.origin = from
            s.present = true
            s.pop.snapTo(0f)
            s.under.snapTo(0f)
            s.bloom.snapTo(0f)
            coroutineScope {
                launch { s.under.animateTo(1f, tween(380, easing = PageEase.SETTLE)) }
                launch {
                    s.bloom.animateTo(
                        1f,
                        tween(
                            if (from != null) 640 else 700,
                            easing = if (from != null) PageEase.EXPO_OUT else PageEase.SETTLE
                        )
                    )
                }
            }
        } else if (s.present) {
            s.bloom.snapTo(1f)                               // فتح ناقص يكتمل
            coroutineScope {
                launch { s.under.animateTo(0f, tween(320, easing = PageEase.SETTLE)) }
                launch { s.pop.animateTo(1f, tween(320, easing = PageEase.SETTLE)) }
            }
            s.present = false
            s.pop.snapTo(0f)
        }
    }
    return s
}

/** الصفحة الي تحت: تذوب مكانها (تتغبّش وتصغر وتخفت) لما تنفتح صفحة فرعية فوقها. */
fun Modifier.pushUnder(vararg states: PushMorphState): Modifier = graphicsLayer {
    var t = 0f
    for (s in states) t = max(t, s.under.value)
    if (t <= 0f) return@graphicsLayer
    val k = 1f - 0.04f * t
    scaleX = k
    scaleY = k
    alpha = 1f - t
    renderEffect = if (PerfGate.lite) null else pgBlur(22.dp.toPx() * t)
}

/**
 * طبقة الصفحة الفرعية: الصف المضغوط يكبر فوقها لين يملّي الشاشة (أو دائرة ضوء إذا ما نعرف الصف)،
 * وعناصرها (Emerge/StaggerIn) تطلع منه. [popDir]: اتجاه الانزلاق عند الرجوع (1 = يمين، -1 = يسار).
 */
@Composable
fun PushMorphLayer(
    state: PushMorphState,
    modifier: Modifier = Modifier,
    popDir: Float = 1f,
    accent: Color = ThemeSettings.accent,
    surface: Color = if (isAppDark()) Color(0xFF22222B) else Color(0xFFF2F2F7),
    background: Brush? = null,
    content: @Composable () -> Unit
) {
    if (!state.present) return
    var size by remember { mutableStateOf(IntSize.Zero) }
    var topLeft by remember { mutableStateOf(Offset.Zero) }
    val from = state.origin
    val origin = from?.center ?: Offset(topLeft.x + size.width / 2f, topLeft.y + size.height * 0.45f)

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .onGloballyPositioned { topLeft = it.positionInRoot() }
            .graphicsLayer {
                alpha = if (state.pop.value > 0f) 1f else minOf(1f, state.under.value * 1.4f)
                translationX = state.pop.value * size.width * popDir
            }
            .then(if (background != null) Modifier.background(background) else Modifier)
            .pointerInput(Unit) { detectTapGestures { } }     // يغطي الي تحته
    ) {
        CompositionLocalProvider(LocalPushOrigin provides origin) { content() }

        // الصف الي يكبر (Grow) أو دائرة الضوء (Bloom)، فوق الصفحة الجديدة
        Canvas(Modifier.fillMaxSize()) {
            val t = state.bloom.value
            if (t >= 1f) return@Canvas
            val dp = density
            if (from != null) {
                val a = 1f - pgSeg(t, 0.3f, 0.85f)
                if (a <= 0f) return@Canvas
                val f = from.translate(-topLeft.x, -topLeft.y)
                val left = pgLerp(f.left, 0f, t)
                val top = pgLerp(f.top, 0f, t)
                val right = pgLerp(f.right, this.size.width, t)
                val bottom = pgLerp(f.bottom, this.size.height, t)
                val rad = CornerRadius(pgLerp(18f, 34f, t) * dp)   // مثل زوايا الشاشة
                drawRoundRect(
                    surface.copy(alpha = 235f / 255f * a),
                    topLeft = Offset(left, top), size = Size(right - left, bottom - top), cornerRadius = rad
                )
                drawRoundRect(
                    accent.copy(alpha = 140f / 255f * a),
                    topLeft = Offset(left, top), size = Size(right - left, bottom - top), cornerRadius = rad,
                    style = Stroke(max(1f, dp))
                )
            } else {
                val ox = this.size.width / 2f
                val oy = this.size.height * 0.45f
                val rr = (24f + 190f * t) * dp
                val fade = 1f - t
                val core = accent.copy(alpha = 70f / 255f * fade)
                drawCircle(
                    Brush.radialGradient(
                        0.2f to core, 1f to core.copy(alpha = 0f),
                        center = Offset(ox, oy), radius = rr
                    ),
                    radius = rr, center = Offset(ox, oy)
                )
                drawCircle(
                    Color.White.copy(alpha = 90f / 255f * fade * fade),
                    radius = rr * 0.82f, center = Offset(ox, oy), style = Stroke(1.5f * dp)
                )
            }
        }
    }
}
