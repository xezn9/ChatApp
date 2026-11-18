package com.chatapp.messenger.ui

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// ───────── 5 سبلاشات إضافية: لكل أيقونة تطبيق سبلاش خاص ─────────
// 1 = مصاص دماء | 2 = زجاجي | 3 = هواء ورعد | 4 = البيئة | 5 = برمجي  (0 = الأساسي بملف SplashScreen.kt)
// نفس أسلوب السبلاش الأساسي: كل شي يحسب حالته من وقت واحد t بالثواني

private val EaseOut: Easing = CubicBezierEasing(0f, 0f, .58f, 1f)
private val EaseInOut: Easing = CubicBezierEasing(.42f, 0f, .58f, 1f)
private val Pop: Easing = CubicBezierEasing(.34f, 1.45f, .6f, 1f)
private val LiftE: Easing = CubicBezierEasing(.6f, 0f, .9f, .4f)
private val FillE: Easing = CubicBezierEasing(.5f, 0f, .2f, 1f)

private const val OUT_START = 2.6f
private const val FADE_START = 2.9f
private const val FADE_DUR = .35f

private fun spProg(t: Float, delay: Float, dur: Float, e: Easing = EaseOut): Float =
    e.transform(((t - delay) / dur).coerceIn(0f, 1f))

private fun spLerp(a: Float, b: Float, p: Float) = a + (b - a) * p

private fun spRgba(r: Int, g: Int, b: Int, a: Float) =
    Color(r / 255f, g / 255f, b / 255f, a.coerceIn(0f, 1f))

private fun spA(c: Color, a: Float) = c.copy(alpha = (c.alpha * a).coerceIn(0f, 1f))

private class SpStyle(
    val bg: Color,
    val tagline: String,
    val iconRes: Int,
    val sky: Color,
    val accent: Color,
    val word2: Color
)

private fun styleOf(i: Int): SpStyle = when (i) {
    1 -> SpStyle(Color(0xFF0A0003), "TALK AFTER DARK", R.drawable.ic_prev_bubble, Color(0xFFFF8A8A), Color(0xFFE0102A), Color(0xFFFF7A7A))
    2 -> SpStyle(Color(0xFF020B1E), "CLEAR AS GLASS", R.drawable.ic_prev_plane, Color(0xFFBFE6FF), Color(0xFF5CC8FF), Color(0xFFB9E3FF))
    3 -> SpStyle(Color(0xFF04091A), "FEEL THE STORM", R.drawable.ic_prev_checks, Color(0xFF9CC4FF), Color(0xFF4D8CFF), Color(0xFF9EC2FF))
    4 -> SpStyle(Color(0xFF02140F), "GROW NATURALLY", R.drawable.ic_prev_duo, Color(0xFFA8F0C8), Color(0xFF2FD38A), Color(0xFFB4F5CF))
    else -> SpStyle(Color(0xFF010A0B), "while (true) talk();", R.drawable.ic_prev_ring, Color(0xFF7DFFCF), Color(0xFF00E5A8), Color(0xFF00E5A8))
}

/** نبضة قلب (مصاص الدماء): قيمة 0..1 تتكرر كل ~0.8 ثانية بعد الثانية الأولى */
private fun vBeat(t: Float): Float {
    if (t < 1f) return 0f
    val s = sin((t - 1f) * 7.85f)
    return if (s > 0f) s * s * s else 0f
}

// ───────────────────────────── السبلاش حسب الستايل ─────────────────────────────
@Composable
internal fun StyledSplash(style: Int, T: () -> Float, modifier: Modifier, useInsets: Boolean, lite: Boolean) {
    val st = remember(style) { styleOf(style) }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(st.bg)
            .graphicsLayer { alpha = 1f - spProg(T(), FADE_START, FADE_DUR) }
    ) {
        val markSize = minOf(maxWidth * .46f, 210.dp)
        val gap = (maxHeight * .06f).coerceIn(26.dp, 46.dp)
        val wordSp = (maxWidth.value * .12f).coerceIn(40f, 58f).sp
        val tagSp = (maxWidth.value * .036f).coerceIn(13f, 16f).sp
        val isCode = style == 5

        when (style) {
            1 -> VampireBg(T, lite)
            2 -> GlassBg(T, lite)
            3 -> StormBg(T, lite)
            4 -> NatureBg(T, lite)
            else -> CodeBg(T, lite, st.bg)
        }

        // طبقة الخروج بحجم الشاشة كلها حتى ما تقص التوهج حول الشعار
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val x = spProg(T(), OUT_START, .45f, LiftE)
                    alpha = 1f - x
                    scaleX = 1f + .18f * x
                    scaleY = 1f + .18f * x
                    renderEffect = if (x > 0f && Build.VERSION.SDK_INT >= 31)
                        BlurEffect(10f * x * density, 10f * x * density) else null
                }
        ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .offset(y = -(maxHeight * .04f)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            StyleMark(style, st, T, markSize)
            StyleWord(style, st, T, wordSp)
            BasicText(
                st.tagline,
                style = TextStyle(
                    color = st.sky, fontSize = tagSp, fontWeight = FontWeight.Medium,
                    letterSpacing = if (isCode) 0.em else .38.em,
                    fontFamily = if (isCode) FontFamily.Monospace else FontFamily.Default
                ),
                modifier = Modifier
                    .offset(y = (-18).dp)
                    .graphicsLayer {
                        val p = spProg(T(), 1.4f, .6f)
                        alpha = .9f * p
                        translationY = 8.dp.toPx() * (1f - p)
                    }
            )
        }
        }

        // شريط التحميل
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .then(if (useInsets) Modifier.windowInsetsPadding(WindowInsets.navigationBars) else Modifier)
                .padding(bottom = maxHeight * .09f)
                .width(minOf(maxWidth * .36f, 150.dp))
                .height(3.dp)
                .graphicsLayer {
                    val t = T()
                    alpha = if (t >= OUT_START) 0f else spProg(t, 1.5f, .3f)
                }
                .clip(RoundedCornerShape(3.dp))
                .background(spA(st.accent, .18f))
                .drawBehind {
                    val p = spProg(T(), 1.55f, .9f, FillE)
                    if (p > 0f) drawRect(
                        Brush.horizontalGradient(listOf(st.accent, st.sky)),
                        size = Size(size.width * p, size.height)
                    )
                }
        )

        // طبقة فوق كل شي (وميض البرق)
        if (style == 3) StormFlash(T)
    }
}

// ───────────────────────────── الشعار ─────────────────────────────
@Composable
private fun StyleMark(style: Int, st: SpStyle, T: () -> Float, markSize: Dp) {
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
                // التوهّج خلف الأيقونة
                val gp = ((t - .5f) / 1f).coerceIn(0f, 1f)
                if (gp > 0f) {
                    val pulse = if (style == 1) vBeat(t) * .35f else 0f
                    val q = EaseOut.transform(gp)
                    val sc = spLerp(.2f, 1.1f, q)
                    val op = q * (if (gp < .5f) 1f else .7f) + pulse
                    val rad = size.width * 1.77f * sc
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to spA(st.accent, .6f * op),
                            .34f to spA(st.accent, .2f * op),
                            .64f to Color.Transparent,
                            center = center,
                            radius = rad
                        ),
                        radius = size.width * 1.25f * sc,
                        center = center
                    )
                }
                // ثلاث حلقات تتوسع وتختفي
                val d0 = if (style == 1) .6f else .55f
                for (d in floatArrayOf(d0, d0 + .15f, d0 + .3f)) {
                    if (t < d) continue
                    val q = EaseOut.transform(((t - d) / 1.1f).coerceIn(0f, 1f))
                    val a = spLerp(.9f, 0f, q)
                    if (a <= 0f) continue
                    val s = spLerp(1f, 2.5f, q)
                    val w = size.width * s
                    val h = size.height * s
                    val tl = Offset(center.x - w / 2f, center.y - h / 2f)
                    drawRoundRect(
                        color = spA(st.sky, .12f * a), topLeft = tl, size = Size(w, h),
                        cornerRadius = CornerRadius(w * .24f), style = Stroke(8.dp.toPx() * s)
                    )
                    drawRoundRect(
                        color = spA(st.sky, .7f * a), topLeft = tl, size = Size(w, h),
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
                    val t = T()
                    when (style) {
                        1 -> { // يظهر من الظلام وينبض كالقلب
                            val p = spProg(t, .1f, .9f)
                            val s = p * (1f + .06f * vBeat(t))
                            scaleX = s; scaleY = s; alpha = p
                        }
                        2 -> { // يتبلور: يبدأ كبير وضبابي ثم يثبت
                            val p = spProg(t, .05f, 1f)
                            val s = spLerp(1.5f, 1f, p)
                            scaleX = s; scaleY = s
                            alpha = (p * 2f).coerceAtMost(1f)
                            val r = 18f * (1f - p) * density
                            renderEffect = if (r > 0.5f && Build.VERSION.SDK_INT >= 31) BlurEffect(r, r) else null
                        }
                        3 -> { // يضرب مع البرق ويرتج
                            val p = spProg(t, .38f, .5f, Pop)
                            val s = spLerp(1.45f, 1f, p)
                            scaleX = s; scaleY = s
                            alpha = if (t < .38f) 0f else 1f
                            translationX = if (t in .75f..1.1f)
                                sin(t * 110f) * 5.dp.toPx() * (1f - (t - .75f) / .35f) else 0f
                        }
                        4 -> { // ينمو من الجذع مثل النبتة
                            val p = spProg(t, .1f, .9f, Pop)
                            scaleX = p; scaleY = p
                            rotationZ = -25f * (1f - p)
                            alpha = (p * 3f).coerceIn(0f, 1f)
                            transformOrigin = TransformOrigin(.5f, .9f)
                        }
                        else -> { // برمجي: يرتعش (glitch) ثم يثبت
                            val p = spProg(t, .1f, .5f, Pop)
                            val s = spLerp(.8f, 1f, p)
                            scaleX = s; scaleY = s
                            alpha = if (t < .15f) 0f else if (t < 1f && ((t * 26f).toInt() % 3 == 0)) .35f else 1f
                            translationX = if (t < 1f) (((t * 41f).toInt() % 5) - 2) * 4.dp.toPx() * (1f - t) else 0f
                        }
                    }
                }
                .clip(shape)
                .border(1.dp, spA(st.sky, .25f), shape)
                .drawWithContent {
                    drawContent()
                    // لمعة تمر على الأيقونة
                    val t = T()
                    val p = spProg(t, .85f, .7f, EaseInOut)
                    if (t >= .85f && p < 1f) {
                        val bw = size.width * 1.5f
                        val bh = size.height * 1.5f
                        val left = -size.width * .25f + spLerp(-1.3f, 1.3f, p) * bw
                        val top = -size.height * .25f
                        val ang = (105.0 * PI / 180.0)
                        val dx = sin(ang).toFloat()
                        val dy = (-cos(ang)).toFloat()
                        val len = abs(bw * dx) + abs(bh * dy)
                        val c = Offset(left + bw / 2f, top + bh / 2f)
                        drawRect(
                            Brush.linearGradient(
                                .4f to Color.Transparent,
                                .5f to Color.White.copy(alpha = .55f),
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
                painter = painterResource(st.iconRes),
                contentDescription = "Chat",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

// ───────────────────────────── كلمة Chat ─────────────────────────────
@Composable
private fun StyleWord(style: Int, st: SpStyle, T: () -> Float, fontSize: TextUnit) {
    if (style == 5) {
        CodeWord(st, T, fontSize)
        return
    }
    val padH = (fontSize.value * .08f).dp
    val padB = (fontSize.value * .12f).dp
    Row(
        Modifier
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val t = T()
                val p = spProg(t, 1.45f, .7f, EaseInOut)
                if (t >= 1.45f && p < 1f) {
                    val ang = (100.0 * PI / 180.0)
                    val dx = sin(ang).toFloat()
                    val dy = (-cos(ang)).toFloat()
                    val len = abs(size.width * dx) + abs(size.height * dy)
                    val left = spLerp(-1.2f, 1.2f, p) * size.width
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
                    brush = Brush.verticalGradient(.3f to Color.White, 1f to st.word2),
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .03.em
                ),
                modifier = Modifier.graphicsLayer {
                    val t = T()
                    when (style) {
                        1 -> { // كل حرف يطلع من تحت
                            val p = spProg(t, 1.0f + i * .12f, .55f)
                            translationY = (1f - p) * size.height * .9f
                            alpha = p
                            val s = spLerp(.85f, 1f, p)
                            scaleX = s; scaleY = s
                        }
                        2 -> { // حروف زجاجية تتكوّن تدريجياً
                            val p = spProg(t, 1.0f + i * .1f, .7f)
                            val s = spLerp(1.35f, 1f, p)
                            scaleX = s; scaleY = s
                            alpha = p
                            translationX = (1f - p) * -14.dp.toPx()
                        }
                        3 -> { // الحروف تهب من اليسار مثل الريح
                            val p = spProg(t, 1.0f + i * .07f, .4f)
                            translationX = -(1f - p) * size.width * (i + 1.5f)
                            alpha = if (p > 0f) 1f else 0f
                        }
                        else -> { // الحروف تنمو للأعلى مثل الأغصان
                            val p = spProg(t, 1.0f + i * .1f, .6f, Pop)
                            scaleY = p.coerceAtLeast(0f)
                            scaleX = spLerp(.7f, 1f, p.coerceIn(0f, 1f))
                            alpha = (p * 3f).coerceIn(0f, 1f)
                            transformOrigin = TransformOrigin(.5f, 1f)
                        }
                    }
                }
            )
        }
    }
}

/** البرمجي: الكلمة تنكتب حرف حرف ومؤشر يومض */
@Composable
private fun CodeWord(st: SpStyle, T: () -> Float, fontSize: TextUnit) {
    val n by remember {
        derivedStateOf {
            val t = T()
            if (t < 1f) 0 else (((t - 1f) / .2f).toInt() + 1).coerceIn(0, 4)
        }
    }
    val typed = TextStyle(
        brush = Brush.verticalGradient(.2f to Color.White, 1f to st.accent),
        fontSize = fontSize, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
    )
    Box(Modifier.padding(bottom = (fontSize.value * .12f).dp)) {
        // يحجز مكان الكلمة كاملة حتى ما تتحرك الواجهة أثناء الكتابة
        BasicText(
            "Chat_",
            style = TextStyle(color = Color.Transparent, fontSize = fontSize, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        )
        Row {
            BasicText("Chat".take(n), style = typed)
            BasicText(
                "_",
                style = TextStyle(color = st.accent, fontSize = fontSize, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                modifier = Modifier.graphicsLayer {
                    val t = T()
                    alpha = if (t < .95f || t > OUT_START) 0f else if (((t * 3.4f).toInt() % 2) == 0) 1f else 0f
                }
            )
        }
    }
}

// ───────────────────────────── مساعدات رسم ─────────────────────────────
private fun DrawScope.glowBlob(cx: Float, cy: Float, rx: Float, ry: Float, c: Color) {
    if (c.alpha <= 0f) return
    withTransform({
        translate(cx, cy)
        scale(1f, ry / rx, pivot = Offset.Zero)
    }) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to c, .7f to Color.Transparent,
                center = Offset.Zero, radius = rx
            ),
            radius = rx, center = Offset.Zero
        )
    }
}

private fun outFade(t: Float): Float = if (t > 2.5f) max(0f, 1f - (t - 2.5f) * 2.5f) else 1f

// ───────────────────────────── 1) مصاص دماء ─────────────────────────────
private class SpDrop(val x: Float, val sp: Float, val ph: Float, val len: Float, val w: Float)
private class SpMote(val x: Float, val sp: Float, val ph: Float, val s: Float, val sw: Float)

@Composable
private fun VampireBg(T: () -> Float, lite: Boolean) {
    val drops = remember {
        val r = Random(11)
        List(if (lite) 12 else 26) { SpDrop(r.nextFloat(), .12f + r.nextFloat() * .22f, r.nextFloat(), .06f + r.nextFloat() * .12f, .6f + r.nextFloat() * 1.6f) }
    }
    val motes = remember {
        val r = Random(12)
        List(if (lite) 16 else 34) { SpMote(r.nextFloat(), .1f + r.nextFloat() * .18f, r.nextFloat(), .8f + r.nextFloat() * 1.8f, r.nextFloat()) }
    }
    Canvas(Modifier.fillMaxSize()) {
        val t = T()
        val w = size.width
        val h = size.height
        val dm = density
        val fade = spProg(t, 0f, .7f)
        val out = outFade(t)
        val b = vBeat(t)
        glowBlob(w * .5f, h * .46f, w * .9f, h * .45f, spRgba(190, 0, 30, (.30f + .18f * b) * fade))
        glowBlob(w * .15f, h * .10f, w * .6f, h * .30f, spRgba(120, 0, 20, .25f * fade))
        glowBlob(w * .90f, h * .90f, w * .7f, h * .35f, spRgba(150, 0, 25, .25f * fade))

        // قطرات دم تنزل
        val dropA = spProg(t, .25f, .6f) * out
        if (dropA > 0f) {
            for (d in drops) {
                val cyc = 1.3f
                val k = ((t * d.sp + d.ph) % cyc) / cyc
                val y = k * (h * 1.25f) - h * .15f
                val x = d.x * w
                val len = d.len * h
                drawLine(
                    Brush.verticalGradient(listOf(Color.Transparent, spRgba(220, 20, 40, .55f * dropA)), startY = y - len, endY = y),
                    Offset(x, y - len), Offset(x, y), strokeWidth = d.w * dm, cap = StrokeCap.Round
                )
                drawCircle(spRgba(235, 30, 50, .75f * dropA), radius = d.w * dm * 1.5f, center = Offset(x, y))
            }
        }
        // جمرات حمراء تطلع
        for (m in motes) {
            val k = (t * m.sp + m.ph) % 1f
            val y = h * (1.05f - k * 1.1f)
            val x = (m.x + .03f * sin(t * 1.3f + m.sw * 6f)) * w
            val al = sin(k * PI.toFloat()) * (.5f + .5f * sin(t * 5f + m.sw * 9f)) * fade * out
            if (al > .01f) drawCircle(spRgba(255, 90, 60, .8f * al), radius = m.s * dm, center = Offset(x, y))
        }
        // تعتيم الأطراف
        drawRect(
            Brush.radialGradient(
                0f to Color.Transparent, .6f to Color.Transparent, 1f to spRgba(0, 0, 0, .65f),
                center = Offset(w / 2f, h / 2f), radius = hypot(w, h) / 2f
            )
        )
    }
}

// ───────────────────────────── 2) زجاجي ─────────────────────────────
private class SpBubble(val x: Float, val sp: Float, val ph: Float, val r: Float, val sw: Float)

@Composable
private fun GlassBg(T: () -> Float, lite: Boolean) {
    val bubbles = remember {
        val r = Random(21)
        List(if (lite) 9 else 16) { SpBubble(r.nextFloat(), .05f + r.nextFloat() * .08f, r.nextFloat(), 14f + r.nextFloat() * 46f, r.nextFloat() * 6f) }
    }
    Canvas(Modifier.fillMaxSize()) {
        val t = T()
        val w = size.width
        val h = size.height
        val dm = density
        val fade = spProg(t, 0f, .6f)
        val out = outFade(t)
        val u = (t / 6f) % 2f
        val pp = EaseInOut.transform(if (u < 1f) u else 2f - u)
        glowBlob(w * (.25f + .05f * pp), h * .30f, w * .6f, h * .35f, spRgba(40, 150, 255, .30f * fade))
        glowBlob(w * (.80f - .05f * pp), h * .72f, w * .65f, h * .35f, spRgba(90, 120, 255, .26f * fade))
        glowBlob(w * .60f, h * .12f, w * .5f, h * .22f, spRgba(120, 230, 255, .18f * fade))

        // أشعة ضوء مائلة بطيئة
        val c = Offset(w / 2f, h / 2f)
        rotate(degrees = 18f + 6f * pp, pivot = c) {
            for (i in 0 until 2) {
                val x0 = w * (.25f + i * .38f)
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, spRgba(180, 225, 255, .07f * fade), Color.Transparent),
                        startX = x0, endX = x0 + w * .3f
                    ),
                    topLeft = Offset(x0, -h * .3f), size = Size(w * .3f, h * 1.6f)
                )
            }
        }

        // فقاعات زجاجية تطلع
        val bubA = spProg(t, .2f, .6f) * out
        if (bubA > 0f) {
            for (b in bubbles) {
                val k = (t * b.sp + b.ph) % 1.2f
                val y = h * (1.1f - k / 1.2f * 1.25f)
                val x = (b.x + .02f * sin(t * .9f + b.sw)) * w
                val r = b.r * dm
                drawCircle(
                    Brush.radialGradient(
                        0f to Color.Transparent, .75f to spRgba(160, 215, 255, .05f * bubA), 1f to spRgba(190, 235, 255, .22f * bubA),
                        center = Offset(x, y), radius = r
                    ),
                    radius = r, center = Offset(x, y)
                )
                drawCircle(spRgba(210, 240, 255, .35f * bubA), radius = r, center = Offset(x, y), style = Stroke(1.2f * dm))
                drawArc(
                    color = spRgba(255, 255, 255, .75f * bubA), startAngle = 200f, sweepAngle = 45f, useCenter = false,
                    topLeft = Offset(x - r * .8f, y - r * .8f), size = Size(r * 1.6f, r * 1.6f),
                    style = Stroke(1.8f * dm, cap = StrokeCap.Round)
                )
            }
        }

        // لمعة عريضة تعبر الشاشة
        val sp = spProg(t, .9f, .9f, EaseInOut)
        if (t >= .9f && sp < 1f) {
            val bw = w * .5f
            val left = spLerp(-bw, w, sp)
            rotate(degrees = 20f, pivot = c) {
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, spRgba(220, 242, 255, .16f), Color.Transparent),
                        startX = left, endX = left + bw
                    ),
                    topLeft = Offset(left, -h * .3f), size = Size(bw, h * 1.6f)
                )
            }
        }
    }
}

// ───────────────────────────── 3) هواء ورعد ─────────────────────────────
private class SpStreak(val y: Float, val sp: Float, val ph: Float, val len: Float, val w: Float, val a: Float)

private fun boltPoints(seed: Int, x0: Float, x1: Float, y1: Float): List<Offset> {
    val r = Random(seed)
    val n = 9
    val pts = ArrayList<Offset>()
    for (i in 0..n) {
        val k = i / n.toFloat()
        val jag = if (i == 0 || i == n) 0f else (r.nextFloat() - .5f) * .14f
        pts.add(Offset(spLerp(x0, x1, k) + jag, spLerp(-.02f, y1, k)))
    }
    return pts
}

private val Bolt1 = boltPoints(5, .62f, .50f, .40f)
private val Bolt2 = boltPoints(9, .25f, .38f, .30f)

private fun flashAt(t: Float, start: Float, dur: Float): Float {
    if (t < start) return 0f
    val x = ((t - start) / dur).coerceIn(0f, 1f)
    return (1f - x).pow(2)
}

@Composable
private fun StormBg(T: () -> Float, lite: Boolean) {
    val streaks = remember {
        val r = Random(31)
        List(if (lite) 14 else 28) { SpStreak(r.nextFloat(), .5f + r.nextFloat() * 1.1f, r.nextFloat(), .10f + r.nextFloat() * .25f, .8f + r.nextFloat() * 1.8f, .15f + r.nextFloat() * .3f) }
    }
    Canvas(Modifier.fillMaxSize()) {
        val t = T()
        val w = size.width
        val h = size.height
        val dm = density
        val fade = spProg(t, 0f, .6f)
        val out = outFade(t)
        // غيوم داكنة تتحرك ببطء
        val drift = (t * .02f) % 1f
        glowBlob(w * (.2f + drift * .2f), h * .12f, w * .7f, h * .22f, spRgba(40, 70, 140, .40f * fade))
        glowBlob(w * (.8f - drift * .2f), h * .22f, w * .7f, h * .20f, spRgba(30, 50, 120, .35f * fade))
        glowBlob(w * .5f, h * .55f, w * .8f, h * .35f, spRgba(20, 80, 200, .22f * fade))

        // خطوط الريح
        val windA = spProg(t, .1f, .5f) * out
        if (windA > 0f) {
            for (s in streaks) {
                val span = w * 1.6f
                val x = ((t * s.sp * w * .9f + s.ph * span) % span) - w * .3f
                val len = s.len * w
                val y = s.y * h
                drawLine(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, spRgba(160, 200, 255, s.a * windA)),
                        startX = x - len, endX = x
                    ),
                    Offset(x - len, y), Offset(x, y), strokeWidth = s.w * dm, cap = StrokeCap.Round
                )
            }
        }
    }
}

/** وميض + صاعقة فوق كل شي */
@Composable
private fun StormFlash(T: () -> Float) {
    Canvas(Modifier.fillMaxSize()) {
        val t = T()
        val w = size.width
        val h = size.height
        val a = .55f * flashAt(t, .34f, .5f) + .30f * flashAt(t, 1.25f, .45f)
        if (a > 0f) drawRect(spRgba(190, 215, 255, a))
        fun bolt(pts: List<Offset>, alpha: Float) {
            val path = Path()
            pts.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(p.x * w, p.y * h) else path.lineTo(p.x * w, p.y * h)
            }
            drawPath(path, spRgba(120, 170, 255, .30f * alpha), style = Stroke(11f * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, spRgba(235, 245, 255, alpha), style = Stroke(3f * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (t in .34f..0.62f) bolt(Bolt1, if (((t * 30f).toInt() % 4) == 3) .5f else 1f)
        if (t in 1.25f..1.42f) bolt(Bolt2, if (((t * 30f).toInt() % 4) == 3) .5f else 1f)
    }
}

// ───────────────────────────── 4) البيئة ─────────────────────────────
private class SpLeaf(val x: Float, val sp: Float, val ph: Float, val r: Float, val sw: Float, val rot: Float, val col: Int)
private class SpFly(val x: Float, val y: Float, val ph: Float, val s: Float)

private val LeafColors = arrayOf(Color(0xFF2FD38A), Color(0xFF7BE05A), Color(0xFF14A06B))

@Composable
private fun NatureBg(T: () -> Float, lite: Boolean) {
    val leaves = remember {
        val r = Random(41)
        List(if (lite) 12 else 22) { SpLeaf(r.nextFloat(), .06f + r.nextFloat() * .08f, r.nextFloat(), 9f + r.nextFloat() * 14f, r.nextFloat() * 6f, 40f + r.nextFloat() * 90f, r.nextInt(3)) }
    }
    val flies = remember {
        val r = Random(42)
        List(if (lite) 10 else 20) { SpFly(r.nextFloat(), .15f + r.nextFloat() * .75f, r.nextFloat() * 6f, 1.2f + r.nextFloat() * 1.8f) }
    }
    val leafPath = remember {
        Path().apply {
            moveTo(0f, -1f)
            quadraticTo(.85f, -.2f, 0f, 1f)
            quadraticTo(-.85f, -.2f, 0f, -1f)
            close()
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val t = T()
        val w = size.width
        val h = size.height
        val dm = density
        val fade = spProg(t, 0f, .7f)
        val out = outFade(t)
        glowBlob(w * .5f, h * .50f, w * .9f, h * .45f, spRgba(20, 180, 110, .26f * fade))
        glowBlob(w * .12f, h * .85f, w * .6f, h * .30f, spRgba(40, 200, 90, .22f * fade))
        glowBlob(w * .92f, h * .15f, w * .6f, h * .28f, spRgba(120, 230, 120, .16f * fade))

        // أشعة شمس تنزل من فوق
        val sway = sin(t * .6f) * 4f
        val rayA = spProg(t, .4f, 1f)
        if (rayA > 0f) {
            val cx = w * .5f
            val cy = -h * .06f
            val rr = h * .95f
            val brush = Brush.radialGradient(
                0f to spRgba(190, 255, 200, .16f * rayA), 1f to Color.Transparent,
                center = Offset(cx, cy), radius = rr
            )
            rotate(degrees = sway, pivot = Offset(cx, cy)) {
                for (i in 0 until 6) {
                    drawArc(
                        brush = brush, startAngle = 55f + i * 14f, sweepAngle = 6f, useCenter = true,
                        topLeft = Offset(cx - rr, cy - rr), size = Size(rr * 2f, rr * 2f)
                    )
                }
            }
        }

        // أوراق شجر تتساقط وتدور
        val leafA = spProg(t, .2f, .6f) * out
        if (leafA > 0f) {
            for (l in leaves) {
                val k = (t * l.sp + l.ph) % 1.2f
                val y = (k / 1.2f) * (h * 1.2f) - h * .1f
                val x = (l.x + .04f * sin(t * .9f + l.sw)) * w
                val r = l.r * dm
                val rot = t * l.rot + l.sw * 40f
                withTransform({
                    translate(x, y)
                    rotate(rot, pivot = Offset.Zero)
                    scale(r, r, pivot = Offset.Zero)
                }) {
                    drawPath(leafPath, spA(LeafColors[l.col], .55f * leafA))
                    drawLine(Color.White.copy(alpha = .25f * leafA), Offset(0f, -.9f), Offset(0f, .9f), strokeWidth = .07f)
                }
            }
        }

        // يراعات متوهجة
        val flyA = spProg(t, .5f, .6f) * out
        if (flyA > 0f) {
            for (f in flies) {
                val x = (f.x + .03f * sin(t * .8f + f.ph)) * w
                val y = (f.y + .03f * cos(t * .7f + f.ph)) * h
                val al = (.5f + .5f * sin(t * 3f + f.ph * 2f)) * flyA
                drawCircle(spRgba(210, 255, 120, .18f * al), radius = f.s * dm * 4f, center = Offset(x, y))
                drawCircle(spRgba(230, 255, 160, .9f * al), radius = f.s * dm, center = Offset(x, y))
            }
        }
    }
}

// ───────────────────────────── 5) برمجي ─────────────────────────────
@Composable
private fun CodeBg(T: () -> Float, lite: Boolean, bg: Color) {
    val paint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
        }
    }
    val glyphs = remember { "01{}<>/;=+()[]#&*".toCharArray() }
    val colSp = remember { val r = Random(51); FloatArray(60) { 5f + r.nextFloat() * 8f } }
    val colOff = remember { val r = Random(52); FloatArray(60) { r.nextFloat() * 40f } }
    val colLen = remember { val r = Random(53); IntArray(60) { 7 + r.nextInt(8) } }
    Canvas(Modifier.fillMaxSize()) {
        val t = T()
        val w = size.width
        val h = size.height
        val dm = density
        val fade = spProg(t, .1f, .6f)
        val out = outFade(t)
        glowBlob(w * .5f, h * .46f, w * .9f, h * .45f, spRgba(0, 200, 150, .12f * fade))

        // مطر الشيفرة (Matrix)
        val cell = (if (lite) 20f else 15f) * dm
        val cols = minOf((w / cell).toInt(), 60)
        val rows = (h / cell).toInt() + 1
        paint.textSize = cell * .95f
        val rainA = fade * out
        if (rainA > 0f) {
            drawIntoCanvas { cv ->
                val nc = cv.nativeCanvas
                for (c in 0 until cols) {
                    val len = colLen[c]
                    val head = floor((t * colSp[c] + colOff[c]) % (rows + len)).toInt()
                    for (k in 0 until len) {
                        val row = head - k
                        if (row < 0 || row >= rows) continue
                        val a = (1f - k / len.toFloat()).pow(1.5f) * .55f * rainA
                        val gi = (c * 31 + row * 7 + (if (k == 0) (t * 9f).toInt() else 0)) % glyphs.size
                        paint.color = if (k == 0)
                            android.graphics.Color.argb((a * 1.6f).coerceAtMost(1f).times(255).toInt(), 200, 255, 235)
                        else
                            android.graphics.Color.argb((a * 255).toInt(), 0, 229, 168)
                        nc.drawText(glyphs, gi, 1, c * cell, (row + 1) * cell, paint)
                    }
                }
            }
        }

        // تعتيم خلف الشعار والكلمة عشان يبانون
        val cc = Offset(w / 2f, h * .46f)
        drawCircle(
            Brush.radialGradient(
                0f to spA(bg, .92f), .6f to spA(bg, .55f), 1f to Color.Transparent,
                center = cc, radius = w * .75f
            ),
            radius = w * .75f, center = cc
        )

        // خط مسح يمر من فوق لتحت
        val sp = spProg(t, .4f, 1f, EaseInOut)
        if (t >= .4f && sp < 1f) {
            val y = sp * h
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, spRgba(0, 229, 168, .22f)),
                    startY = y - 90f * dm, endY = y
                ),
                topLeft = Offset(0f, y - 90f * dm), size = Size(w, 90f * dm)
            )
            drawLine(spRgba(160, 255, 225, .8f), Offset(0f, y), Offset(w, y), strokeWidth = 1.5f * dm)
        }
    }
}

// ───────────────────────────── معاينة مصغرة متحركة تتكرر (تظهر بصفحة الإعدادات) ─────────────────────────────
/**
 * تعرض السبلاش الحقيقي مصغّراً وتعيده تلقائياً بحلقة.
 * الحلقة تشتغل فقط وقت ما المعاينة ظاهرة على الشاشة (حتى ما تثقل صفحة الإعدادات).
 */
@Composable
fun SplashPreview(style: Int, modifier: Modifier = Modifier) {
    val cfg = LocalConfiguration.current
    val density = LocalDensity.current.density
    val sw = cfg.screenWidthDp.toFloat().coerceAtLeast(320f)
    val sh = cfg.screenHeightDp.toFloat().coerceAtLeast(560f)
    val previewH = 330f
    val s = previewH / sh
    val previewW = sw * s
    val screenPx = sh * density

    val tState = remember { mutableFloatStateOf(0f) }
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(style, visible) {
        if (!visible) return@LaunchedEffect
        val loop = SPLASH_TOTAL + .9f
        val start = withFrameNanos { it }
        while (true) {
            val t = (withFrameNanos { it } - start) / 1_000_000_000f
            tState.floatValue = t % loop
        }
    }

    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .size(previewW.dp, previewH.dp)
            .onGloballyPositioned { c ->
                val b = c.boundsInWindow()
                val v = b.bottom > 0f && b.top < screenPx
                if (v != visible) visible = v
            }
            .clip(shape)
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = .18f), shape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .requiredSize(sw.dp, sh.dp)
                .graphicsLayer { scaleX = s; scaleY = s }
        ) {
            SplashContent(style, { tState.floatValue }, Modifier, false, true)
        }
    }
}
