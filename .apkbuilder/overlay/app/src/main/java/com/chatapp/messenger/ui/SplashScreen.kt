package com.chatapp.messenger.ui

import android.app.Activity
import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.chatapp.messenger.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// ───────── سبلاش التطبيق (منقول من Chat Splash Preview.html) ─────────
// كل الانيميشن مبني على وقت واحد t بالثواني، وكل عنصر يحسب حالته منه (نفس توقيتات الـ CSS)

private val BG = Color(0xFF00061F)
private val SKY = Color(0xFF8FCAFF)
private val BLUE = Color(0xFF0A6CFF)

private val EaseOut: Easing = CubicBezierEasing(0f, 0f, .58f, 1f)
private val EaseInOut: Easing = CubicBezierEasing(.42f, 0f, .58f, 1f)
private val Pop: Easing = CubicBezierEasing(.34f, 1.45f, .6f, 1f)
private val DropE: Easing = CubicBezierEasing(.3f, 1.5f, .5f, 1f)
private val FillE: Easing = CubicBezierEasing(.5f, 0f, .2f, 1f)
private val LiftE: Easing = CubicBezierEasing(.6f, 0f, .9f, .4f)

private const val OUT_START = 2.6f      // يبدأ رفع الشعار والاختفاء
private const val FADE_START = 2.9f     // يبدأ تلاشي الشاشة كلها
private const val FADE_DUR = .35f
private const val TOTAL = FADE_START + FADE_DUR

private fun prog(t: Float, delay: Float, dur: Float, e: Easing = EaseOut): Float =
    e.transform(((t - delay) / dur).coerceIn(0f, 1f))

private fun lerpF(a: Float, b: Float, p: Float) = a + (b - a) * p

private fun rgba(r: Int, g: Int, b: Int, a: Float) =
    Color(r / 255f, g / 255f, b / 255f, a.coerceIn(0f, 1f))

private class SParticle(
    val a: Float, val rr: Float, val s: Float, val sp: Float, val d: Float, val tw: Float
) {
    var px = 0f
    var py = 0f
}

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    // التصميم إنجليزي (Chat) فنثبّت الاتجاه LTR عشان الحروف ما تنعكس بالواجهة العربية
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        val tState = remember { mutableFloatStateOf(0f) }
        val T: () -> Float = { tState.floatValue }
        val finished by rememberUpdatedState(onFinished)

        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) {
                val t = (withFrameNanos { it } - start) / 1_000_000_000f
                tState.floatValue = t
                if (t >= TOTAL) break
            }
            finished()
        }

        // أيقونات شريط الحالة/التنقل فاتحة فوق الخلفية الداكنة، وترجع لحالتها بعد السبلاش
        val view = LocalView.current
        DisposableEffect(Unit) {
            val window = (view.context as? Activity)?.window
            val ctrl = window?.let { WindowCompat.getInsetsController(it, view) }
            val prevStatus = ctrl?.isAppearanceLightStatusBars
            val prevNav = ctrl?.isAppearanceLightNavigationBars
            ctrl?.isAppearanceLightStatusBars = false
            ctrl?.isAppearanceLightNavigationBars = false
            onDispose {
                if (ctrl != null && prevStatus != null && prevNav != null) {
                    ctrl.isAppearanceLightStatusBars = prevStatus
                    ctrl.isAppearanceLightNavigationBars = prevNav
                }
            }
        }

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(BG)
                .graphicsLayer { alpha = 1f - prog(T(), FADE_START, FADE_DUR) }
                // يمنع اللمس من النفاذ للتطبيق اللي تحت السبلاش
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
        ) {
            val markSize = minOf(maxWidth * .46f, 210.dp)
            val gap = (maxHeight * .06f).coerceIn(26.dp, 46.dp)
            val wordSp = (maxWidth.value * .12f).coerceIn(40f, 58f).sp
            val tagSp = (maxWidth.value * .036f).coerceIn(13f, 16f).sp

            Aurora(T)
            Rays(T)
            Particles(T)
            Flash(T)

            Column(
                Modifier
                    .align(Alignment.Center)
                    .offset(y = -(maxHeight * .04f))
                    .graphicsLayer {
                        val x = prog(T(), OUT_START, .45f, LiftE)
                        alpha = 1f - x
                        scaleX = 1f + .18f * x
                        scaleY = 1f + .18f * x
                        renderEffect = if (x > 0f && Build.VERSION.SDK_INT >= 31)
                            BlurEffect(10f * x * density, 10f * x * density) else null
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                Mark(T, markSize)
                Word(T, wordSp)
                BasicText(
                    "START TALKING",
                    style = TextStyle(
                        color = SKY, fontSize = tagSp, fontWeight = FontWeight.Medium, letterSpacing = .38.em
                    ),
                    modifier = Modifier
                        .offset(y = (-18).dp)
                        .graphicsLayer {
                            val p = prog(T(), 1.4f, .6f)
                            alpha = .9f * p
                            translationY = 8.dp.toPx() * (1f - p)
                        }
                )
            }

            // شريط التحميل
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = maxHeight * .09f)
                    .width(minOf(maxWidth * .36f, 150.dp))
                    .height(3.dp)
                    .graphicsLayer {
                        val t = T()
                        alpha = if (t >= OUT_START) 0f else prog(t, 1.5f, .3f)
                    }
                    .clip(RoundedCornerShape(3.dp))
                    .background(rgba(120, 180, 255, .15f))
                    .drawBehind {
                        val p = prog(T(), 1.55f, .9f, FillE)
                        if (p > 0f) drawRect(
                            Brush.horizontalGradient(listOf(BLUE, SKY)),
                            size = Size(size.width * p, size.height)
                        )
                    }
            )
        }
    }
}

// ───────── الشفق الأزرق بالخلفية ─────────
@Composable
private fun Aurora(T: () -> Float) {
    Canvas(Modifier.fillMaxSize()) {
        val t = T()
        val fade = prog(t, 0f, .6f)
        if (fade <= 0f) return@Canvas
        val u = (t / 6f) % 2f
        val pp = EaseInOut.transform(if (u < 1f) u else 2f - u)
        val sc = 1f + .08f * pp
        val bw = size.width * 1.4f
        val bh = size.height * 1.4f
        val tx = bw * .03f * pp
        val ty = -bh * .02f * pp
        withTransform({
            translate(tx, ty)
            scale(sc, sc, pivot = center)
        }) {
            fun glow(fx: Float, fy: Float, rxp: Float, ryp: Float, c: Color) {
                val cx = -size.width * .2f + bw * fx
                val cy = -size.height * .2f + bh * fy
                val rx = bw * rxp
                val ry = bh * ryp
                withTransform({
                    translate(cx, cy)
                    scale(1f, ry / rx, pivot = Offset.Zero)
                }) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to c.copy(alpha = c.alpha * fade),
                            .7f to Color.Transparent,
                            center = Offset.Zero,
                            radius = rx
                        ),
                        radius = rx,
                        center = Offset.Zero
                    )
                }
            }
            glow(.25f, .30f, .40f, .30f, rgba(0, 110, 255, .35f))
            glow(.78f, .70f, .45f, .35f, rgba(60, 60, 255, .30f))
            glow(.60f, .15f, .35f, .25f, rgba(0, 190, 255, .18f))
        }
    }
}

// ───────── الأشعة الدوّارة ─────────
@Composable
private fun Rays(T: () -> Float) {
    Canvas(Modifier.fillMaxSize()) {
        val t = T()
        val p = if (t < .55f) 0f else {
            val x = ((t - .55f) / 1.6f).coerceIn(0f, 1f)
            if (x < .3f) EaseOut.transform(x / .3f)
            else lerpF(1f, .5f, EaseOut.transform((x - .3f) / .7f))
        }
        if (p <= 0f) return@Canvas
        val vmax = max(size.width, size.height) / 100f
        val cx = size.width / 2f
        val cy = size.height * .46f
        val r = 34f * vmax
        val brush = Brush.radialGradient(
            0f to rgba(60, 140, 255, .22f * p),
            1f to Color.Transparent,
            center = Offset(cx, cy),
            radius = r
        )
        rotate(degrees = t / 30f * 360f, pivot = Offset(cx, cy)) {
            for (i in 0 until 20) {
                drawArc(
                    brush = brush,
                    startAngle = i * 18f - 90f,
                    sweepAngle = 6f,
                    useCenter = true,
                    topLeft = Offset(cx - r, cy - r),
                    size = Size(r * 2f, r * 2f)
                )
            }
        }
    }
}

// ───────── جزيئات تلتف وتنجذب للشعار ─────────
@Composable
private fun Particles(T: () -> Float) {
    val ps = remember {
        List(110) {
            SParticle(
                a = Random.nextFloat() * 6.283f,
                rr = .3f + Random.nextFloat() * .6f,
                s = .5f + Random.nextFloat() * 1.8f,
                sp = .5f + Random.nextFloat(),
                d = Random.nextFloat() * .3f,
                tw = Random.nextFloat() * 6f
            )
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val t = T()
        val w = size.width
        val h = size.height
        val dm = density
        val big = max(w, h)
        val fadeOut = if (t > 2.5f) max(0f, 1f - (t - 2.5f) * 2.5f) else 1f
        for (p in ps) {
            val k = ((t - p.d) / .7f).coerceIn(0f, 1f)
            val e = 1f - (1f - k).pow(3)
            val after = max(t - .7f, 0f)
            val r = big * p.rr * (1f - e * .9f) +
                (if (t > .65f) (t - .65f) * (80f + p.sp * 90f) * dm else 0f)
            val a = p.a + e * 2.6f * p.sp + after * .25f * p.sp
            val x = w / 2f + cos(a) * r
            val y = h * .46f + sin(a) * r * .9f
            val al = min(k * 1.5f, 1f) * (.4f + .5f * sin(t * 2.2f + p.tw)) * fadeOut
            if (al <= 0f) {
                p.px = x; p.py = y
                continue
            }
            val c = rgba(140, 200, 255, al)
            val sw = p.s * dm
            if (p.px != 0f) drawLine(c, Offset(p.px, p.py), Offset(x, y), strokeWidth = sw, cap = StrokeCap.Round)
            drawCircle(c, radius = sw * .8f, center = Offset(x, y))
            val dx = x - p.px
            val dy = y - p.py
            p.px = x - dx * .6f
            p.py = y - dy * .6f
        }
    }
}

// ───────── وميض ظهور الشعار ─────────
@Composable
private fun Flash(T: () -> Float) {
    Canvas(Modifier.fillMaxSize()) {
        val t = T()
        if (t < .55f) return@Canvas
        val x = ((t - .55f) / .6f).coerceIn(0f, 1f)
        val op = if (x < .2f) .7f * EaseOut.transform(x / .2f)
        else .7f * (1f - EaseOut.transform((x - .2f) / .8f))
        if (op <= 0f) return@Canvas
        val c = Offset(size.width / 2f, size.height * .46f)
        val rad = hypot(size.width / 2f, size.height * .54f)
        drawRect(
            Brush.radialGradient(
                0f to rgba(170, 215, 255, .85f * op),
                .25f to rgba(20, 110, 255, .35f * op),
                .55f to Color.Transparent,
                center = c,
                radius = rad
            )
        )
    }
}

// ───────── الشعار: توهّج + حلقات + أيقونة تدور وتطلع ─────────
@Composable
private fun Mark(T: () -> Float, markSize: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(markSize)
            .graphicsLayer {
                val t = T()
                if (t >= 1f) {
                    val u = (t - 1f) / 2.4f
                    translationY = -7.dp.toPx() * (1f - cos(2f * PI.toFloat() * u)) / 2f
                }
            }
            .drawBehind {
                val t = T()
                // التوهّج الأزرق خلف الأيقونة
                val gp = ((t - .5f) / 1f).coerceIn(0f, 1f)
                if (gp > 0f) {
                    val sc: Float
                    val op: Float
                    if (gp < .4f) {
                        val q = EaseOut.transform(gp / .4f)
                        sc = lerpF(.2f, 1.05f, q); op = q
                    } else {
                        val q = EaseOut.transform((gp - .4f) / .6f)
                        sc = lerpF(1.05f, 1.1f, q); op = lerpF(1f, .6f, q)
                    }
                    val rad = size.width * 1.77f * sc
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to rgba(20, 110, 255, .6f * op),
                            .34f to rgba(20, 110, 255, .2f * op),
                            .64f to Color.Transparent,
                            center = center,
                            radius = rad
                        ),
                        radius = size.width * 1.25f * sc,
                        center = center
                    )
                }
                // ثلاث حلقات تتوسع وتختفي
                for (d in floatArrayOf(.55f, .7f, .85f)) {
                    if (t < d) continue
                    val q = EaseOut.transform(((t - d) / 1.1f).coerceIn(0f, 1f))
                    val a = lerpF(.9f, 0f, q)
                    if (a <= 0f) continue
                    val s = lerpF(1f, 2.5f, q)
                    val w = size.width * s
                    val h = size.height * s
                    val tl = Offset(center.x - w / 2f, center.y - h / 2f)
                    drawRoundRect(
                        color = rgba(140, 200, 255, .12f * a), topLeft = tl, size = Size(w, h),
                        cornerRadius = CornerRadius(w * .24f), style = Stroke(8.dp.toPx() * s)
                    )
                    drawRoundRect(
                        color = rgba(140, 200, 255, .7f * a), topLeft = tl, size = Size(w, h),
                        cornerRadius = CornerRadius(w * .24f), style = Stroke(2.dp.toPx() * s)
                    )
                }
            }
    ) {
        val shape = RoundedCornerShape(22)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = ((T() - .05f) / .75f).coerceIn(0f, 1f)
                    val e = Pop.transform(p)
                    rotationZ = -200f * (1f - e)
                    scaleX = e
                    scaleY = e
                    alpha = Pop.transform((p / .3f).coerceIn(0f, 1f)).coerceIn(0f, 1f)
                }
                .clip(shape)
                .border(1.dp, rgba(140, 190, 255, .22f), shape)
                .drawWithContent {
                    drawContent()
                    // لمعة تمر على الأيقونة
                    val t = T()
                    val p = prog(t, .85f, .7f, EaseInOut)
                    if (t >= .85f && p < 1f) {
                        val bw = size.width * 1.5f
                        val bh = size.height * 1.5f
                        val left = -size.width * .25f + lerpF(-1.3f, 1.3f, p) * bw
                        val top = -size.height * .25f
                        val ang = (105.0 * PI / 180.0)
                        val dx = sin(ang).toFloat()
                        val dy = (-cos(ang)).toFloat()
                        val len = abs(bw * dx) + abs(bh * dy)
                        val c = Offset(left + bw / 2f, top + bh / 2f)
                        drawRect(
                            Brush.linearGradient(
                                .4f to Color.Transparent,
                                .5f to Color.White.copy(alpha = .6f),
                                .6f to Color.Transparent,
                                start = Offset(c.x - dx * len / 2f, c.y - dy * len / 2f),
                                end = Offset(c.x + dx * len / 2f, c.y + dy * len / 2f)
                            ),
                            topLeft = Offset(left, top), size = Size(bw, bh)
                        )
                    }
                }
        ) {
            Image(
                painter = painterResource(R.drawable.splash_icon),
                contentDescription = "Chat",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

// ───────── كلمة Chat: كل حرف ينزل من فوق + لمعة تمر عليها ─────────
@Composable
private fun Word(T: () -> Float, fontSize: androidx.compose.ui.unit.TextUnit) {
    val padH = (fontSize.value * .08f).dp
    val padB = (fontSize.value * .12f).dp
    Row(
        Modifier
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val t = T()
                val p = prog(t, 1.45f, .7f, EaseInOut)
                if (t >= 1.45f && p < 1f) {
                    val ang = (100.0 * PI / 180.0)
                    val dx = sin(ang).toFloat()
                    val dy = (-cos(ang)).toFloat()
                    val len = abs(size.width * dx) + abs(size.height * dy)
                    val left = lerpF(-1.2f, 1.2f, p) * size.width
                    val c = Offset(left + size.width / 2f, size.height / 2f)
                    drawRect(
                        Brush.linearGradient(
                            .4f to Color.Transparent,
                            .5f to Color.White.copy(alpha = .9f),
                            .6f to Color.Transparent,
                            start = Offset(c.x - dx * len / 2f, c.y - dy * len / 2f),
                            end = Offset(c.x + dx * len / 2f, c.y + dy * len / 2f)
                        ),
                        topLeft = Offset(left, 0f), size = size,
                        blendMode = BlendMode.SrcAtop
                    )
                }
            }
            .padding(start = padH, end = padH, bottom = padB)
    ) {
        "Chat".forEachIndexed { i, ch ->
            BasicText(
                ch.toString(),
                style = TextStyle(
                    brush = Brush.verticalGradient(.3f to Color.White, 1f to Color(0xFFA9D4FF)),
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .03.em
                ),
                modifier = Modifier.graphicsLayer {
                    val delay = .95f + i * .06f
                    val t = T()
                    if (t < delay) {
                        translationY = size.height * 1.15f
                    } else {
                        val p = DropE.transform(((t - delay) / .6f).coerceIn(0f, 1f))
                        translationY = lerpF(-1.3f, 0f, p) * size.height
                        rotationZ = lerpF(-12f, 0f, p)
                    }
                }
            )
        }
    }
}
