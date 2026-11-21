package com.chatapp.messenger.ui

/*
 * TabReveal.kt — انتقال التبويبات بموجة الزجاج السائل (Liquid-glass wave).
 * منقول من HyperSpring (TabReveal.java / HyperSpringAnimations.kt) بنفس المنحنيات والمدد والـ AGSL shaders.
 *
 * الفرق عن النسخة الأصلية: التبويبات هنا تبقى كلها مركّبة وحيّة بالخلفية (مثل ما كان عندك قبل)،
 * فما تنعاد بنائها ولا تضيع حالتها (التمرير، البحث...). الموجة بس تتحكم بطريقة رسمها.
 *
 * الفكرة:
 *  - الصفحة الجديدة تولد كدائرة من زر التبويب المضغوط وتكبر، وحافتها مثل حافة زجاج سميك (تثني الصورة وتلمع).
 *  - الصفحة القديمة تنزاح للخارج قدام الموجة (مثل ماي مزاح) وتغرق: تصغر وتخفت وتتغبّش.
 *
 * يحتاج أندرويد 12+ للـ blur، و 13+ لـ shaders الزجاج (قبل هيچ: دائرة + حلقة ضوء بس).
 */

import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.hypot
import kotlin.math.max

/** TabReveal.WAVE */
private val TAB_WAVE = CubicBezierEasing(0.4f, 0.1f, 0.15f, 1f)

/** مدة الموجة (TabReveal: 900 ms) */
private const val TAB_REVEAL_MS = 900

/** حافة الزجاج للصفحة الجديدة (TabReveal.RIM_SHADER) */
private const val TAB_RIM_SHADER = """
uniform shader content;
uniform float2 center;
uniform float radius;
uniform float width;
uniform float amount;
half4 main(float2 p) {
  float2 d = p - center;
  float dist = length(d);
  float e = radius - dist;
  if (e < 0.0 || e > width) return content.eval(p);
  float2 n = d / max(dist, 1.0);
  float k = 1.0 - e / width;
  half4 c = content.eval(p - n * amount * k * k);
  float shine = 0.22 * k * k * k;
  c.rgb = min(c.rgb + half3(shine) * c.a, c.aaa);
  return c;
}
"""

/** دفع الصفحة القديمة قدام الموجة (TabReveal.PUSH_SHADER) */
private const val TAB_PUSH_SHADER = """
uniform shader content;
uniform float2 center;
uniform float radius;
uniform float width;
uniform float amount;
half4 main(float2 p) {
  float2 d = p - center;
  float dist = length(d);
  float x = (dist - radius) / width;
  if (x < -1.0 || x > 5.0) return content.eval(p);
  float2 n = d / max(dist, 1.0);
  float push = amount * exp(-0.9 * max(x, 0.0));
  return content.eval(p - n * push);
}
"""

private class TabCircleAt(val c: Offset, val r: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(Path().apply { addOval(Rect(c, r)) })
}

/** الـ shaders (أندرويد 13+). إذا فشل تجميعها لأي سبب نرجع للدائرة + الحلقة بس. */
@RequiresApi(33)
private class TabShaders {
    val rim = RuntimeShader(TAB_RIM_SHADER)
    val push = RuntimeShader(TAB_PUSH_SHADER)
}

@RequiresApi(33)
private fun TabShaders.rimEffect(cx: Float, cy: Float, radius: Float, width: Float, amount: Float): android.graphics.RenderEffect {
    rim.setFloatUniform("center", cx, cy)
    rim.setFloatUniform("width", width)
    rim.setFloatUniform("radius", radius)
    rim.setFloatUniform("amount", amount)
    return android.graphics.RenderEffect.createRuntimeShaderEffect(rim, "content")
}

@RequiresApi(33)
private fun TabShaders.pushEffect(cx: Float, cy: Float, radius: Float, width: Float, amount: Float): android.graphics.RenderEffect {
    push.setFloatUniform("center", cx, cy)
    push.setFloatUniform("width", width)
    push.setFloatUniform("radius", radius)
    push.setFloatUniform("amount", amount)
    return android.graphics.RenderEffect.createRuntimeShaderEffect(push, "content")
}

/** تأثير الصفحة القديمة: دفع (13+) ثم تغبيش (12+). */
@RequiresApi(31)
private fun oldPageEffect(
    sh: TabShaders?, cx: Float, cy: Float, radius: Float,
    pushWidth: Float, pushAmount: Float, blurPx: Float
): ComposeRenderEffect? {
    var e: android.graphics.RenderEffect? = null
    if (blurPx >= 0.5f) {
        e = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
    }
    if (sh != null && Build.VERSION.SDK_INT >= 33) {
        val pe = sh.pushEffect(cx, cy, radius, pushWidth, pushAmount)
        // الدفع أول، بعدين التغبيش
        e = if (e == null) pe else android.graphics.RenderEffect.createChainEffect(e, pe)
    }
    return e?.asComposeRenderEffect()
}

/** حالة الموجة: أي صفحة ظاهرة الآن، وأيها تغرق، وتقدّم الحركة 0..1. */
@Stable
class TabRevealState(initial: Int) {
    /** الصفحة الجديدة/الظاهرة */
    var current by mutableIntStateOf(initial)
        private set

    /** الصفحة القديمة أثناء الموجة (null إذا ما كو موجة) */
    var old by mutableStateOf<Int?>(null)
        private set

    /** 0..1 (بعد منحنى WAVE) */
    val progress = Animatable(0f)

    internal var hostSize by mutableStateOf(IntSize.Zero)
    internal var hostOrigin by mutableStateOf(Offset.Zero)
    internal var center by mutableStateOf(Offset.Zero)

    val animating: Boolean get() = old != null

    internal suspend fun switchTo(target: Int, from: Offset?) {
        if (target == current) {
            // رجعنا لنفس الصفحة وموجة ناقصة معلّقة: ننهيها
            if (old != null) { old = null; progress.snapTo(0f) }
            return
        }
        progress.snapTo(0f)
        if (from == null || hostSize == IntSize.Zero) {   // بدون موجة: تبديل عادي
            old = null
            current = target
            return
        }
        center = from
        old = current                                      // موجة ناقصة تنتهي هنا
        current = target
        progress.animateTo(1f, tween(TAB_REVEAL_MS, easing = TAB_WAVE))
        old = null
    }
}

@Composable
fun rememberTabRevealState(initial: Int): TabRevealState = remember { TabRevealState(initial) }

/**
 * يعرض [pageCount] صفحة (كلها تبقى مركّبة)، والصفحة [selected] هي الظاهرة.
 * عند تغيّر [selected] تشتغل الموجة من [tabCenterInRoot] (مركز زر التبويب المضغوط بإحداثيات الشاشة/root).
 * إذا رجعت null تطلع الموجة من أسفل الشاشة بالنص.
 *
 * @param page محتوى الصفحة [index] (الخلفية + المحتوى)
 */
@Composable
fun TabRevealHost(
    selected: Int,
    pageCount: Int,
    tabCenterInRoot: (Int) -> Offset?,
    modifier: Modifier = Modifier,
    state: TabRevealState = rememberTabRevealState(selected),
    // لون حلقة الضوء = لون التطبيق الي يختاره المستخدم من الإعدادات (جاهز أو مخصّص)، ويتغيّر فوراً مع الاختيار
    ringColor: Color = com.chatapp.messenger.ui.theme.ThemeSettings.accent,
    page: @Composable (index: Int) -> Unit
) {
    val shaders = remember {
        if (Build.VERSION.SDK_INT >= 33) runCatching { TabShaders() }.getOrNull() else null
    }
    val centerOf by rememberUpdatedState(tabCenterInRoot)

    LaunchedEffect(selected) {
        val c = centerOf(selected)?.let { it - state.hostOrigin }
            ?: if (state.hostSize != IntSize.Zero)
                Offset(state.hostSize.width / 2f, state.hostSize.height * 0.9f) else null
        state.switchTo(selected, c)
    }

    Box(
        modifier
            .onSizeChanged { state.hostSize = it }
            .onGloballyPositioned { state.hostOrigin = it.positionInRoot() }
    ) {
        for (i in 0 until pageCount) {
            key(i) {
                Box(
                    Modifier
                        .fillMaxSize()
                        // الصفحة الجديدة دائماً فوق
                        .zIndex(if (i == selected) 1f else 0f)
                        .graphicsLayer { applyReveal(state, shaders, i) }
                        // الصفحة المخفية تماماً ما ترسم ولا تسجّل شي (توفير blur/lens)
                        .drawWithContent {
                            if (i == state.current || i == state.old) drawContent()
                        }
                        // الصفحات غير المختارة ما تستقبل لمس (حتى ما تنضغط أزرارها بالغلط)
                        .then(
                            if (i == selected) Modifier else Modifier.pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                    }
                                }
                            }
                        )
                ) { page(i) }
            }
        }
        TabRevealRing(state, ringColor)
    }
}

/** تأثير الموجة على طبقة صفحة واحدة (يُنفّذ بمرحلة الرسم). */
private fun androidx.compose.ui.graphics.GraphicsLayerScope.applyReveal(
    state: TabRevealState, shaders: TabShaders?, index: Int
) {
    if (state.old == null) return
    val isNew = index == state.current
    val isOld = index == state.old
    if (!isNew && !isOld) return

    val t = state.progress.value
    val cx = state.center.x
    val cy = state.center.y
    val w = state.hostSize.width.toFloat().coerceAtLeast(1f)
    val h = state.hostSize.height.toFloat().coerceAtLeast(1f)
    val maxR = hypot(max(cx, w - cx), max(cy, h - cy)) + 40.dp.toPx()
    val startR = 26.dp.toPx()
    val r = startR + (maxR - startR) * t
    val force = 1f - t * t * t   // كاملة أغلب الطريق وتهدأ بالنهاية
    transformOrigin = TransformOrigin(cx / w, cy / h)

    if (isNew) {
        // الصفحة الجديدة تظهر داخل الدائرة بس
        val s = 1.04f - 0.04f * t
        scaleX = s
        scaleY = s
        clip = true
        shape = TabCircleAt(Offset(cx, cy), r)
        if (Build.VERSION.SDK_INT >= 33 && shaders != null) {
            renderEffect = shaders
                .rimEffect(cx, cy, r, 34.dp.toPx(), 26.dp.toPx() * force)
                .asComposeRenderEffect()
        }
    } else {
        val k = 1f - 0.08f * t
        scaleX = k
        scaleY = k
        alpha = 1f - 0.55f * t
        if (Build.VERSION.SDK_INT >= 31) {
            // نصف قطر الموجة بإحداثيات الصفحة القديمة (الي تصغر)
            renderEffect = oldPageEffect(
                shaders, cx, cy, r / k,
                pushWidth = 40.dp.toPx(), pushAmount = 48.dp.toPx() * force,
                blurPx = 10.dp.toPx() * t
            )
        }
    }
}

/** حلقة الضوء على مقدمة الموجة: هالة واسعة ناعمة + خط رفيع فوقها. */
@Composable
private fun TabRevealRing(state: TabRevealState, color: Color) {
    if (!state.animating) return
    Canvas(Modifier.fillMaxSize()) {
        val t = state.progress.value
        if (t >= 1f) return@Canvas
        val w = size.width
        val h = size.height
        val c = state.center
        val maxR = hypot(max(c.x, w - c.x), max(c.y, h - c.y)) + 40.dp.toPx()
        val startR = 26.dp.toPx()
        val r = startR + (maxR - startR) * t
        val fade = 1f - t
        drawCircle(color.copy(alpha = 55f / 255f * fade), radius = r, center = c, style = Stroke(16.dp.toPx()))
        drawCircle(color.copy(alpha = 200f / 255f * fade), radius = r, center = c, style = Stroke(2.dp.toPx()))
    }
}
