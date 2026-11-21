package com.chatapp.messenger.ui

import android.animation.ValueAnimator
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * عجلة العناصر: نقل حرفي لـ FeatureWheel.java من HyperSpring (نفس الثوابت ونفس الفيزياء).
 * العناصر على أسطوانة تدور عمودياً وتلف (بعد الأخير يرجع الأول). العنصر بالنص حاد وكامل الحجم
 * داخل إطار "عدسة"، والباقي كل ما بعد عن النص: يلتف أكثر، يصغر، يشفّ، ويتمّوه. فوق وتحت يذوبون بالخلفية.
 * السحب 1:1، وعند الإفلات بسرعة تكمل لفّ وتبطئ ثم نابض يثبّتها على أقرب عنصر، ونقرة اهتزاز لكل عنصر يمرّ بالنص.
 * اللمس على عنصر جانبي يجيبه للنص (ما يختاره)، واللمس على عنصر النص يمرّ للعنصر نفسه (اختيار / ضغط مطوّل).
 */

/** درجات الأسطوانة بين عنصر والتالي. */
private const val STEP_DEG = 26f
/** أبعد من هذي الزاوية العنصر ما يُرى (صار بظهر الأسطوانة). */
private const val MAX_DEG = 82f
/** الفاصل بين العناصر بالنص نسبةً لارتفاع العنصر. */
private const val GAP = 1.06f
/** النابض الي يثبّت العنصر بالنص. */
private const val SNAP_K = 170f
private const val SNAP_ZETA = 0.82f
/** احتكاك اللف المفلوت (1/ث). */
private const val FRICTION = 3.2f
/** تحت هذي السرعة (عنصر/ث) يوقف اللف ويثبّت. */
private const val SETTLE_SPEED = 2.2f

// منحنيات الحركة من Motion.java / SettingsActivity.java
internal val WheelEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)
internal val WheelPower3Out = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)
internal val WheelPower4Out = CubicBezierEasing(0.165f, 0.84f, 0.44f, 1f)
internal val WheelBackOut = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

private fun animationsEnabled(): Boolean =
    if (Build.VERSION.SDK_INT >= 26) ValueAnimator.areAnimatorsEnabled() else true

@Stable
class WheelState internal constructor(
    internal val n: Int,
    initial: Int,
    internal val stepPx: Float,
    internal val radiusPx: Float,
    internal val cardPx: Float,
    private val scope: CoroutineScope,
    private val tickHaptic: () -> Unit,
    private val centerChanged: (Int) -> Unit
) {
    /** العنصر بالنص بشكل متصل (2.4 = بين الـ2 والـ3). دايماً بالمدى [0, n). */
    var pos by mutableFloatStateOf(initial.coerceIn(0, n - 1).toFloat())
        internal set
    var center by mutableIntStateOf(initial.coerceIn(0, n - 1))
        private set

    /** موجة الانكماش على باقي البطاقات عند الاختيار (ripple من SettingsActivity). */
    internal var rippleFrom = -1
    internal var rippleSeq by mutableIntStateOf(0)

    private var vel = 0f                      // عنصر/ث
    private var target = Float.NaN            // يثبّت باتجاه هذا (بدون لفّ)
    private var tickAt = Int.MIN_VALUE
    private var running = false
    private var lastNs = 0L
    private var job: Job? = null

    /** تُناديها البطاقة لما تنختار: باقي البطاقات تنكمش شوي وترجع، الأبعد أبطأ وأنعم. */
    fun ripple(from: Int) {
        rippleFrom = from
        rippleSeq++
    }

    internal fun wrap(p: Float): Float {
        var q = p % n
        if (q < 0f) q += n
        return q
    }

    /** بعد العنصر i عن النص بعدد عناصر، بالطريق الأقصر (يلف). */
    internal fun offset(i: Int, p: Float = pos): Float {
        var o = i - p
        o -= n * floor(o / n + 0.5f)
        return o
    }

    internal fun isMoving() = running && (abs(vel) > 0.5f || !target.isNaN())

    /** أي عنصر تحت ارتفاع معيّن من العجلة (عكس الأسطوانة). */
    internal fun indexAt(y: Float, height: Float): Int {
        val dy = y - height / 2f
        if (abs(dy) < cardPx / 2f) return Math.round(pos) % n
        val s = (dy / radiusPx).coerceIn(-1f, 1f)
        val o = Math.toDegrees(asin(s).toDouble()).toFloat() / STEP_DEG
        return ((Math.round(pos + o) % n) + n) % n
    }

    internal fun shortestTarget(idx: Int): Float = pos + offset(idx)

    internal fun dragBy(dy: Float) {
        pos = wrap(pos - dy / stepPx)
        update()
    }

    internal fun fling(v: Float) {
        if (!animationsEnabled()) {
            snapTo(Math.round(pos + v * 0.3f).toFloat())
            return
        }
        target = Float.NaN
        vel = v.coerceIn(-40f, 40f)
        start()
    }

    internal fun snapTo(t: Float) {
        if (!animationsEnabled()) {
            pos = wrap(t)
            vel = 0f
            target = Float.NaN
            update()
            return
        }
        target = t
        start()
    }

    internal fun stop() {
        running = false
        vel = 0f
        target = Float.NaN
        job?.cancel()
        job = null
    }

    private fun start() {
        if (running) return
        running = true
        lastNs = 0L
        job = scope.launch {
            while (running) {
                androidx.compose.runtime.withFrameNanos { frame(it) }
            }
        }
    }

    private fun frame(now: Long) {
        val dt = if (lastNs == 0L) 1f / 60f else min(1f / 30f, (now - lastNs) / 1e9f)
        lastNs = now
        var p = pos
        if (target.isNaN()) {
            // يلف مفلوت: يبطئ بالاحتكاك، ولما يهدّ يختار وين يثبّت (مع اتجاهه عشان ما يرجع)
            vel *= exp(-FRICTION * dt)
            p += vel * dt
            if (abs(vel) < SETTLE_SPEED) target = Math.round(p + vel * 0.12f).toFloat()
        } else {
            // نابض نحو العنصر (بخطوات صغيرة: مستقر حتى لو تأخر فريم)
            val c = 2f * SNAP_ZETA * sqrt(SNAP_K)
            var left = dt
            while (left > 0f) {
                val h = min(0.004f, left)
                vel += (SNAP_K * (target - p) - c * vel) * h
                p += vel * h
                left -= h
            }
            if (abs(target - p) < 0.0015f && abs(vel) < 0.02f) {
                p = target
                running = false
            }
        }
        val t = target
        val w = wrap(p)
        if (!t.isNaN()) target = t + (w - p)      // الهدف يتحرك مع اللفّة
        p = w
        if (!running) {
            p = wrap(Math.round(p).toFloat())
            vel = 0f
            target = Float.NaN
        }
        pos = p
        update()
    }

    internal fun update() {
        val c = Math.round(pos) % n
        if (c != tickAt) {
            val first = tickAt == Int.MIN_VALUE
            tickAt = c
            if (!first) tickHaptic()
        }
        if (c != center) {
            center = c
            centerChanged(c)
        }
    }

    internal fun release() {
        stop()
        pos = wrap(Math.round(pos).toFloat())
    }
}

/** تمويه عنصر: يتعاد بس لما يتغيّر بنقطة صحيحة (إعادته غالية). */
private class BlurCache {
    private var q = -1f
    private var fx: RenderEffect? = null
    fun get(r: Float): RenderEffect? {
        if (Build.VERSION.SDK_INT < 31) return null
        var qq = Math.round(r).toFloat()
        if (qq < 1f) qq = 0f
        if (qq == q) return fx
        q = qq
        fx = if (qq == 0f) null else BlurEffect(qq, qq, TileMode.Decal)
        return fx
    }
}

/**
 * @param accent لون الأكسنت للعدسة (الأصل: coral FF5F45)
 * @param initialIndex العنصر الي يبدأ بالنص
 * @param onCenter يُنادى لما يتغيّر العنصر الي بالنص
 * @param item محتوى العنصر؛ [WheelState.ripple] تُنادى عند اختياره لموجة الانكماش
 */
@Composable
fun ItemWheel(
    count: Int,
    cardHeight: Dp,
    accent: Color,
    modifier: Modifier = Modifier,
    initialIndex: Int = 0,
    onCenter: (Int) -> Unit = {},
    item: @Composable BoxScope.(index: Int, wheel: WheelState) -> Unit
) {
    if (count <= 0) return
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val onCenterNow = rememberUpdatedState(onCenter)

    val cardPx = with(density) { cardHeight.toPx() }
    val step = Math.toRadians(STEP_DEG.toDouble()).toFloat()
    val radius = cardPx * GAP / sin(step)
    val stepPx = radius * sin(step)
    // ارتفاع العجلة: عنصر النص + اللي يبان من الأسطوانة فوق وتحت
    val heightPx = 2f * radius * sin(Math.toRadians(64.0)).toFloat() + cardPx
    val heightDp = with(density) { heightPx.toDp() }

    val state = remember(count, cardPx) {
        WheelState(
            n = count, initial = initialIndex, stepPx = stepPx, radiusPx = radius, cardPx = cardPx, scope = scope,
            tickHaptic = {
                view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
                    else HapticFeedbackConstants.CLOCK_TICK
                )
            },
            centerChanged = { onCenterNow.value(it) }
        )
    }
    DisposableEffect(state) { onDispose { state.release() } }
    LaunchedEffect(state) { onCenterNow.value(state.center) }

    val lensR = with(density) { 19.dp.toPx() }
    val lensM = with(density) { 1.5.dp.toPx() }
    val lensPad = with(density) { 4.dp.toPx() }
    val lensStroke = with(density) { max(1f, 1.2.dp.toPx()) }
    val lensFill = accent.copy(alpha = 0x10 / 255f)
    val lensEdge = accent.copy(alpha = 0x59 / 255f)
    // فوق وتحت العناصر تذوب حتى تبان خلفية الصفحة الحقيقية (DST_OUT)
    val fadeBrush = remember {
        Brush.verticalGradient(
            0f to Color.Black, 0.32f to Color.Transparent, 0.68f to Color.Transparent, 1f to Color.Black
        )
    }

    val visible = remember(state.center, count) {
        (-4..4).map { ((state.center + it) % count + count) % count }.distinct()
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(heightDp)
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                // العدسة: إطار ناعم حول عنصر النص، تحت العناصر
                val top = size.height / 2f - cardPx / 2f - lensPad
                val bottom = size.height / 2f + cardPx / 2f + lensPad
                val tl = Offset(lensM, top)
                val sz = Size(size.width - 2f * lensM, bottom - top)
                drawRoundRect(lensFill, tl, sz, CornerRadius(lensR))
                drawRoundRect(lensEdge, tl, sz, CornerRadius(lensR), style = Stroke(lensStroke))
                drawContent()
                drawRect(brush = fadeBrush, blendMode = BlendMode.DstOut)
            }
            .pointerInput(state) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    // نفس onInterceptTouchEvent/onTouchEvent: نراقب بمرحلة Initial قبل الأبناء والـ scroll
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val h = size.height.toFloat()
                    val vt = VelocityTracker()
                    vt.addPointerInputChange(down)
                    val moving = state.isMoving()
                    state.stop()
                    val tapIndex = state.indexAt(down.position.y, h)
                    val tapOther = tapIndex != Math.round(state.pos) % state.n
                    // يلف: اللمس يوقفه بس. على عنصر ثاني: يجيبه للنص. بالحالتين ما ينختار شي.
                    if (moving || tapOther) down.consume()
                    val downY = down.position.y
                    var lastY = downY
                    var dragging = false
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        vt.addPointerInputChange(ch)
                        if (!ch.pressed) {
                            if (dragging) {
                                val vy = vt.calculateVelocity().y
                                state.fling(-vy / state.stepPx)
                                ch.consume()
                            } else if (tapOther) {
                                state.snapTo(state.shortestTarget(tapIndex))
                                ch.consume()
                            } else if (moving) {
                                state.snapTo(Math.round(state.pos).toFloat())
                                ch.consume()
                            }
                            break
                        }
                        val y = ch.position.y
                        if (!dragging && abs(y - downY) > slop) {
                            dragging = true
                            lastY = y
                        }
                        if (dragging) {
                            state.dragBy(y - lastY)
                            lastY = y
                            ch.consume()
                        } else if (moving || tapOther) {
                            ch.consume()
                        }
                    }
                }
            }
    ) {
        for (i in visible) {
            androidx.compose.runtime.key(i) {
                WheelSlot(i, state, cardHeight, ((i - state.center) % count + count) % count, count, item)
            }
        }
    }
}

@Composable
private fun BoxScope.WheelSlot(
    i: Int,
    state: WheelState,
    cardHeight: Dp,
    ringDist: Int,
    count: Int,
    item: @Composable BoxScope.(Int, WheelState) -> Unit
) {
    val blur = remember { BlurCache() }
    val rs = remember { Animatable(1f) }
    val startSeq = remember { state.rippleSeq }
    LaunchedEffect(state.rippleSeq) {
        if (state.rippleSeq == startSeq || state.rippleFrom == i || !animationsEnabled()) return@LaunchedEffect
        // أبعد: أتأخر وأنعم (نفس معادلة ripple الأصلية)
        val oi = state.offset(i)
        val of = state.offset(state.rippleFrom)
        val ty = { o: Float -> state.radiusPx * sin(Math.toRadians((o * STEP_DEG).toDouble())).toFloat() }
        val d = abs(ty(oi) - ty(of)) / state.cardPx * 60f   // بالـ dp تقريباً لارتفاع 60dp الأصلي
        val k = 1f - 0.035f * max(0.35f, 1f - d / 420f)
        delay(Math.round(d * 0.9f).toLong())
        rs.animateTo(k, tween(130, easing = WheelEase))
        rs.animateTo(1f, tween(460, easing = WheelBackOut))
    }
    val isCenter = state.center == i
    val dist = min(ringDist, count - ringDist)

    Box(
        Modifier
            .align(Alignment.Center)
            .fillMaxWidth()
            .height(cardHeight)
            .padding(horizontal = 4.dp)
            .zIndex(10f - dist)                        // الي بالنص فوق
            .graphicsLayer {
                val o = state.offset(i)
                val deg = o * STEP_DEG
                if (abs(deg) > MAX_DEG) {
                    alpha = 0f
                    return@graphicsLayer
                }
                val a = Math.toRadians(deg.toDouble())
                val c = cos(a).toFloat()
                translationY = state.radiusPx * sin(a).toFloat()
                rotationX = -deg * 0.92f                // فوق: حافته العليا تبعد
                val k = 0.84f + 0.16f * c
                scaleX = k
                scaleY = k
                alpha = c.coerceIn(0f, 1f).pow(1.6f)
                cameraDistance = 9000f * density
                renderEffect = blur.get(min(9f, abs(o) * 4.5f) * density)
            }
            .then(if (isCenter) Modifier else Modifier.clearAndSetSemantics { })
    ) {
        Box(Modifier.matchParentSize().graphicsLayer { scaleX = rs.value; scaleY = rs.value }) {
            item(i, state)
        }
    }
}
