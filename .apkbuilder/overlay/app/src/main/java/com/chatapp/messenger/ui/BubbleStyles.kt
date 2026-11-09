package com.chatapp.messenger.ui

import com.chatapp.messenger.ui.theme.GlassFx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Blue get() = AccentBlue
/**
 * شكل فقاعة (مثل فقاعات تيك توك): شكل + ألوان + رسمة شخصية صغيرة (decor).
 * decor ترسم والشخصية على الجهة اليسرى من الفقاعة (الإحداثيات بالبكسل)، والنظام يعكسها حسب جهة الرسالة.
 * sidePad: مساحة محجوزة جوه الفقاعة للشخصية حتى ما تغطي النص.
 * topPad: مساحة فوق الفقاعة للأشياء الي تطلع من فوق (أذن، عنكبوت...).
 */
class BubbleStyle(
    val id: String,
    val name: String,
    val glass: Boolean = false,
    val shape: Shape = RoundedCornerShape(22.dp),
    val fill: List<Color> = emptyList(),
    val text: Color = Color.White,
    val border: Dp = 0.dp,
    val borderColor: Color = Color.Transparent,
    val sidePad: Dp = 0.dp,
    val topPad: Dp = 0.dp,
    /** مساحة محجوزة على الجهة المعاكسة للشخصية (تُستعمل عند دمج شكلين) */
    val farPad: Dp = 0.dp,
    val decor: ((DrawScope, Float, Float) -> Unit)? = null,
    /** رسم داخل الفقاعة (مقصوص بحدود الشكل وتحت النص): خطوط، موجات، تزيين... */
    val inner: ((DrawScope, Float, Float) -> Unit)? = null
)

// ---------------------------------------------------------------------------
// رسومات الشخصيات
// ---------------------------------------------------------------------------

private fun DrawScope.decorGhost(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cy = h / 2
    drawOval(Color.Black, Offset(9 * u, cy - 9 * u), Size(7 * u, 10 * u))
    drawOval(Color.Black, Offset(21 * u, cy - 9 * u), Size(7 * u, 10 * u))
    drawOval(Color.Black, Offset(14 * u, cy + 4 * u), Size(9 * u, 8 * u))
}

private fun DrawScope.decorPumpkin(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val rib = Color(0x33000000)
    val ink = Color(0xFF3B1A05)
    drawLine(rib, Offset(w * 0.3f, 5 * u), Offset(w * 0.3f, h - 5 * u), 2 * u, StrokeCap.Round)
    drawLine(rib, Offset(w * 0.7f, 5 * u), Offset(w * 0.7f, h - 5 * u), 2 * u, StrokeCap.Round)
    drawRoundRect(Color(0xFF3E8E41), Offset(w / 2 - 4 * u, -6 * u), Size(8 * u, 9 * u), CornerRadius(3 * u))
    drawRoundRect(ink, Offset(9 * u, h / 2 - 9 * u), Size(7 * u, 8 * u), CornerRadius(2 * u))
    drawRoundRect(ink, Offset(21 * u, h / 2 - 9 * u), Size(7 * u, 8 * u), CornerRadius(2 * u))
    drawArc(ink, 0f, 180f, true, Offset(11 * u, h / 2 + 1 * u), Size(14 * u, 9 * u))
}

private fun DrawScope.decorSpider(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val c = Color.White
    // شبكة عنكبوت بالزاوية العليا
    val o = Offset(w - 2 * u, 2 * u)
    fun pt(r: Float, deg: Double) = Offset(
        o.x + (r * cos(deg * PI / 180.0)).toFloat(),
        o.y + (r * sin(deg * PI / 180.0)).toFloat()
    )
    val rays = listOf(180.0, 150.0, 120.0, 90.0)
    rays.forEach { drawLine(c, o, pt(28 * u, it), 1.2f * u) }
    listOf(10 * u, 19 * u).forEach { r ->
        for (i in 0 until rays.size - 1) drawLine(c, pt(r, rays[i]), pt(r, rays[i + 1]), 1.2f * u)
    }
    // عنكبوت معلّق
    val x = 16 * u
    drawLine(c, Offset(x, -14 * u), Offset(x, 8 * u), 1.2f * u)
    for (k in 0 until 4) {
        val dy = (k - 1.5f) * 4 * u
        drawLine(c, Offset(x, 12 * u), Offset(x - 10 * u, 12 * u + dy), 1.2f * u)
        drawLine(c, Offset(x, 12 * u), Offset(x + 10 * u, 12 * u + dy), 1.2f * u)
    }
    drawCircle(c, 5 * u, Offset(x, 13 * u))
    drawCircle(c, 3 * u, Offset(x, 8 * u))
    drawCircle(Color.Black, 1f * u, Offset(x - 1.2f * u, 7.5f * u))
    drawCircle(Color.Black, 1f * u, Offset(x + 1.2f * u, 7.5f * u))
}

private fun DrawScope.decorSketch(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val blue = Color(0xFF29B6F6)
    drawRoundRect(
        blue, Offset(2 * u, 2 * u), Size(w - 4 * u, h - 4 * u), CornerRadius(14 * u),
        style = Stroke(2.5f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9 * u, 6 * u)))
    )
    drawArc(blue, 180f, 260f, false, Offset(-6 * u, -6 * u), Size(14 * u, 14 * u), style = Stroke(2 * u, cap = StrokeCap.Round))
    drawArc(blue, 0f, 250f, false, Offset(w - 9 * u, h - 8 * u), Size(14 * u, 14 * u), style = Stroke(2 * u, cap = StrokeCap.Round))
}

private fun DrawScope.decorOtter(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val bub = Color(0xFF5BC8EA)
    drawCircle(bub, 6 * u, Offset(w - 6 * u, 5 * u))
    drawCircle(Color.White.copy(alpha = 0.6f), 2 * u, Offset(w - 8 * u, 3 * u))
    drawCircle(bub, 4 * u, Offset(w * 0.6f, 3 * u))
    val brown = Color(0xFFA9703F)
    drawOval(brown, Offset(2 * u, h - 20 * u), Size(30 * u, 18 * u))
    drawCircle(brown, 3 * u, Offset(7 * u, h - 30 * u))
    drawCircle(brown, 3 * u, Offset(21 * u, h - 30 * u))
    drawCircle(brown, 9 * u, Offset(14 * u, h - 23 * u))
    drawOval(Color(0xFFEBD0A8), Offset(8 * u, h - 22 * u), Size(12 * u, 8 * u))
    drawCircle(Color.Black, 1.6f * u, Offset(10.5f * u, h - 25 * u))
    drawCircle(Color.Black, 1.6f * u, Offset(17.5f * u, h - 25 * u))
    drawOval(Color(0xFF3B1A05), Offset(12 * u, h - 22 * u), Size(4 * u, 3 * u))
}

private fun DrawScope.flower(cx: Float, cy: Float, r: Float, petal: Color, center: Color) {
    for (i in 0 until 5) {
        val a = (i * 72.0 - 90.0) * PI / 180.0
        drawCircle(petal, r * 0.5f, Offset(cx + (r * 0.55f * cos(a)).toFloat(), cy + (r * 0.55f * sin(a)).toFloat()))
    }
    drawCircle(center, r * 0.3f, Offset(cx, cy))
}

private fun DrawScope.decorFlowers(w: Float, h: Float) {
    val u = 1.dp.toPx()
    flower(5 * u, 5 * u, 8 * u, Color(0xFFD6336C), Color(0xFFFFD54F))
    flower(w - 6 * u, h - 5 * u, 8 * u, Color(0xFFD6336C), Color(0xFFFFD54F))
    flower(w - 4 * u, 8 * u, 4 * u, Color(0xFFE85D8E), Color(0xFFFFD54F))
}

private fun DrawScope.decorCapybara(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val dark = Color(0xFFB77B45)
    drawOval(dark, Offset(-4 * u, h / 2 - 15 * u), Size(36 * u, 30 * u))
    drawCircle(dark, 4 * u, Offset(22 * u, h / 2 - 15 * u))
    drawCircle(Color(0xFFFF8F00), 4 * u, Offset(10 * u, h / 2 - 16 * u))
    drawCircle(Color.Black, 2 * u, Offset(15 * u, h / 2 - 4 * u))
    drawOval(Color(0xFF4A2A12), Offset(-1 * u, h / 2 + 2 * u), Size(9 * u, 7 * u))
}

private fun DrawScope.decorDuckFrog(w: Float, h: Float) {
    val u = 1.dp.toPx()
    // ضفدع
    drawCircle(Color(0xFF5DBB63), 8 * u, Offset(w - 34 * u, 2 * u))
    drawCircle(Color.White, 3 * u, Offset(w - 38 * u, -3 * u))
    drawCircle(Color.White, 3 * u, Offset(w - 30 * u, -3 * u))
    drawCircle(Color.Black, 1.4f * u, Offset(w - 38 * u, -3 * u))
    drawCircle(Color.Black, 1.4f * u, Offset(w - 30 * u, -3 * u))
    // بطة
    drawCircle(Color(0xFFFFB300), 8 * u, Offset(w - 14 * u, 2 * u))
    drawOval(Color(0xFFFF7043), Offset(w - 9 * u, 0f), Size(9 * u, 5 * u))
    drawCircle(Color.Black, 1.5f * u, Offset(w - 16 * u, -1 * u))
    // غيمة صغيرة
    val cloud = Color(0xFFA5D4F5)
    drawCircle(cloud, 4 * u, Offset(6 * u, h - 3 * u))
    drawCircle(cloud, 5 * u, Offset(12 * u, h - 5 * u))
    drawCircle(cloud, 4 * u, Offset(18 * u, h - 3 * u))
}

private fun DrawScope.decorCat(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val ink = Color(0xFF3B2A1A)
    val cream = Color(0xFFFFF4E0)
    listOf(5 * u, w - 20 * u).forEach { x ->
        val ear = Path().apply {
            moveTo(x, 3 * u)
            lineTo(x + 7 * u, -9 * u)
            lineTo(x + 15 * u, 3 * u)
            close()
        }
        drawPath(ear, cream)
        drawPath(ear, ink, style = Stroke(2 * u))
    }
    drawCircle(ink, 2 * u, Offset(10 * u, h / 2 - 4 * u))
    drawCircle(ink, 2 * u, Offset(22 * u, h / 2 - 4 * u))
    drawOval(Color(0xFFF48FB1), Offset(14 * u, h / 2 + 1 * u), Size(4 * u, 3 * u))
    drawLine(ink, Offset(2 * u, h / 2 + 2 * u), Offset(9 * u, h / 2 + 3 * u), 1.2f * u)
    drawLine(ink, Offset(23 * u, h / 2 + 3 * u), Offset(30 * u, h / 2 + 2 * u), 1.2f * u)
}


// ---------------------------------------------------------------------------
// ستة أشكال جديدة: نيون، فضاء، قلوب، غيمة، ثلج، روبوت
// ---------------------------------------------------------------------------

/** نجمة لامعة بأربع رؤوس */
private fun DrawScope.sparkle(cx: Float, cy: Float, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(cx, cy - r)
        quadraticTo(cx, cy, cx + r, cy)
        quadraticTo(cx, cy, cx, cy + r)
        quadraticTo(cx, cy, cx - r, cy)
        quadraticTo(cx, cy, cx, cy - r)
        close()
    }
    drawPath(p, color)
}

/** قلب: (x,y) الزاوية العليا اليسرى، s الحجم */
private fun DrawScope.heart(x: Float, y: Float, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(x + s * 0.5f, y + s * 0.9f)
        cubicTo(x + s * 0.1f, y + s * 0.6f, x + s * 0.0f, y + s * 0.3f, x + s * 0.2f, y + s * 0.15f)
        cubicTo(x + s * 0.35f, y + s * 0.03f, x + s * 0.5f, y + s * 0.15f, x + s * 0.5f, y + s * 0.28f)
        cubicTo(x + s * 0.5f, y + s * 0.15f, x + s * 0.65f, y + s * 0.03f, x + s * 0.8f, y + s * 0.15f)
        cubicTo(x + s * 1.0f, y + s * 0.3f, x + s * 0.9f, y + s * 0.6f, x + s * 0.5f, y + s * 0.9f)
        close()
    }
    drawPath(p, color)
}

/** ندفة ثلج: ثلاث خطوط متقاطعة */
private fun DrawScope.snowflake(cx: Float, cy: Float, r: Float, color: Color, stroke: Float) {
    for (deg in listOf(0.0, 60.0, 120.0)) {
        val a = deg * PI / 180.0
        val dx = (r * cos(a)).toFloat()
        val dy = (r * sin(a)).toFloat()
        drawLine(color, Offset(cx - dx, cy - dy), Offset(cx + dx, cy + dy), stroke, StrokeCap.Round)
    }
    drawCircle(color, stroke * 0.9f, Offset(cx, cy))
}

private fun DrawScope.decorNeon(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cyan = Color(0xFF00F0FF)
    val pink = Color(0xFFFF2BD6)
    val r = CornerRadius(20 * u)
    // هالة التوهج الخارجية ثم الإطار المتدرج الحاد
    drawRoundRect(cyan.copy(alpha = 0.16f), Offset(0f, 0f), Size(w, h), r, style = Stroke(9 * u))
    drawRoundRect(pink.copy(alpha = 0.18f), Offset(1 * u, 1 * u), Size(w - 2 * u, h - 2 * u), r, style = Stroke(5 * u))
    drawRoundRect(
        Brush.linearGradient(listOf(cyan, pink), Offset(0f, 0f), Offset(w, h)),
        Offset(1.2f * u, 1.2f * u), Size(w - 2.4f * u, h - 2.4f * u), r, style = Stroke(2.2f * u)
    )
    sparkle(w - 4 * u, 3 * u, 6 * u, Color.White)
    sparkle(6 * u, h - 2 * u, 4 * u, cyan)
}

private fun DrawScope.decorSpace(w: Float, h: Float) {
    val u = 1.dp.toPx()
    // نجوم صغيرة
    listOf(
        Offset(w * 0.42f, 6 * u) to 1.4f, Offset(w * 0.58f, h - 6 * u) to 1.2f,
        Offset(w * 0.72f, 8 * u) to 1.6f, Offset(w - 8 * u, h * 0.55f) to 1.3f,
        Offset(w * 0.5f, h * 0.5f + 9 * u) to 1.0f
    ).forEach { (o, rr) -> drawCircle(Color.White.copy(alpha = 0.85f), rr * u, o) }
    sparkle(w - 8 * u, 8 * u, 5 * u, Color(0xFFFFF3B0))
    sparkle(w * 0.3f, h - 5 * u, 3.5f * u, Color.White)
    // كوكب بحلقة
    val c = Offset(19 * u, h / 2)
    val ring = Color(0xFFFFE0A3)
    drawOval(ring.copy(alpha = 0.55f), Offset(c.x - 16 * u, c.y - 4.5f * u), Size(32 * u, 9 * u), style = Stroke(1.8f * u))
    drawCircle(Brush.linearGradient(listOf(Color(0xFFFFB25B), Color(0xFFE5532D)), Offset(c.x - 9 * u, c.y - 9 * u), Offset(c.x + 9 * u, c.y + 9 * u)), 9.5f * u, c)
    drawArc(ring, 0f, 180f, false, Offset(c.x - 16 * u, c.y - 4.5f * u), Size(32 * u, 9 * u), style = Stroke(1.8f * u))
}

private fun DrawScope.decorHearts(w: Float, h: Float) {
    val u = 1.dp.toPx()
    heart(-6 * u, -9 * u, 20 * u, Color(0xFFE0245E))
    heart(w - 12 * u, h - 13 * u, 16 * u, Color(0xFFFFD1DF))
    heart(w - 18 * u, -6 * u, 11 * u, Color(0xFFFFE3EC))
    heart(14 * u, h - 8 * u, 9 * u, Color(0xFFFFC2D6))
    drawCircle(Color.White.copy(alpha = 0.55f), 1.6f * u, Offset(w * 0.55f, 5 * u))
}

private fun DrawScope.decorCloud(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val white = Color.White
    // سحابة بارزة من أعلى الفقاعة
    val x = w * 0.42f
    drawCircle(white, 6 * u, Offset(x, 2 * u))
    drawCircle(white, 9 * u, Offset(x + 10 * u, -1 * u))
    drawCircle(white, 7 * u, Offset(x + 21 * u, 2 * u))
    drawRoundRect(white, Offset(x - 5 * u, 1 * u), Size(33 * u, 8 * u), CornerRadius(4 * u))
    // شمس صغيرة باليسار
    val c = Offset(17 * u, h / 2)
    for (i in 0 until 8) {
        val a = i * 45.0 * PI / 180.0
        drawLine(
            Color(0xFFFFC53D),
            Offset(c.x + (10 * u * cos(a)).toFloat(), c.y + (10 * u * sin(a)).toFloat()),
            Offset(c.x + (14 * u * cos(a)).toFloat(), c.y + (14 * u * sin(a)).toFloat()),
            1.8f * u, StrokeCap.Round
        )
    }
    drawCircle(Color(0xFFFFD54F), 7 * u, c)
    drawCircle(Color(0xFFFFF3B0), 3 * u, Offset(c.x - 2 * u, c.y - 2 * u))
    // سحابة صغيرة تحت اليمين
    drawCircle(white.copy(alpha = 0.9f), 4 * u, Offset(w - 16 * u, h - 2 * u))
    drawCircle(white.copy(alpha = 0.9f), 5 * u, Offset(w - 10 * u, h - 4 * u))
    drawCircle(white.copy(alpha = 0.9f), 4 * u, Offset(w - 4 * u, h - 2 * u))
}

private fun DrawScope.decorSnow(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val snow = Color.White
    val ice = Color(0xFF4FB0EE)
    // قبعة ثلج فوق الفقاعة مع قطرات
    drawRoundRect(snow, Offset(6 * u, -5 * u), Size(w * 0.55f, 10 * u), CornerRadius(5 * u))
    drawRoundRect(Color(0xFFBFE3FA), Offset(6 * u, -5 * u), Size(w * 0.55f, 10 * u), CornerRadius(5 * u), style = Stroke(1.2f * u))
    drawCircle(snow, 3.2f * u, Offset(14 * u, 6 * u))
    drawCircle(snow, 2.6f * u, Offset(w * 0.3f, 7 * u))
    drawCircle(snow, 3f * u, Offset(w * 0.5f, 6 * u))
    // ندف
    snowflake(17 * u, h / 2, 9.5f * u, ice, 1.7f * u)
    snowflake(w - 10 * u, h - 8 * u, 5.5f * u, ice.copy(alpha = 0.75f), 1.3f * u)
    drawCircle(ice.copy(alpha = 0.6f), 1.5f * u, Offset(w * 0.62f, h - 4 * u))
    drawCircle(ice.copy(alpha = 0.6f), 1.2f * u, Offset(w - 6 * u, 9 * u))
}

private fun DrawScope.decorRobot(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val dark = Color(0xFF1E2A35)
    // هوائي
    drawLine(Color(0xFF4A5663), Offset(19 * u, 1 * u), Offset(19 * u, -8 * u), 2f * u, StrokeCap.Round)
    drawCircle(Color(0xFFFF4D4D), 3.6f * u, Offset(19 * u, -10 * u))
    drawCircle(Color.White.copy(alpha = 0.7f), 1.2f * u, Offset(18 * u, -11 * u))
    // شاشة العيون
    drawRoundRect(dark, Offset(6 * u, h / 2 - 10 * u), Size(26 * u, 20 * u), CornerRadius(6 * u))
    val glow = Color(0xFF3DF2FF)
    drawCircle(glow.copy(alpha = 0.28f), 6 * u, Offset(14 * u, h / 2 - 2 * u))
    drawCircle(glow.copy(alpha = 0.28f), 6 * u, Offset(24 * u, h / 2 - 2 * u))
    drawCircle(glow, 3.2f * u, Offset(14 * u, h / 2 - 2 * u))
    drawCircle(glow, 3.2f * u, Offset(24 * u, h / 2 - 2 * u))
    drawLine(glow, Offset(14 * u, h / 2 + 5 * u), Offset(24 * u, h / 2 + 5 * u), 1.6f * u, StrokeCap.Round)
    // براغي
    val screw = Color(0xFF5E6B79)
    listOf(Offset(w - 6 * u, 6 * u), Offset(w - 6 * u, h - 6 * u)).forEach {
        drawCircle(screw, 2.2f * u, it)
        drawLine(Color(0xFFD5DCE3), Offset(it.x - 1.2f * u, it.y), Offset(it.x + 1.2f * u, it.y), 0.9f * u)
    }
}


// ---------------------------------------------------------------------------
// عشر فقاعات جديدة: نار، ساكورا، دونات، حلوى، بكسل، ملكي، صاعقة، دعسوقة، ورقة، موج
// ---------------------------------------------------------------------------

/** حدود بدرجات سلّم (مثل ألعاب 8-bit) */
private class PixelShape(private val stepDp: Float = 3.5f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val s = stepDp * density.density
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(2 * s, 0f); lineTo(w - 2 * s, 0f); lineTo(w - 2 * s, s); lineTo(w - s, s); lineTo(w - s, 2 * s); lineTo(w, 2 * s)
            lineTo(w, h - 2 * s); lineTo(w - s, h - 2 * s); lineTo(w - s, h - s); lineTo(w - 2 * s, h - s); lineTo(w - 2 * s, h)
            lineTo(2 * s, h); lineTo(2 * s, h - s); lineTo(s, h - s); lineTo(s, h - 2 * s); lineTo(0f, h - 2 * s)
            lineTo(0f, 2 * s); lineTo(s, 2 * s); lineTo(s, s); lineTo(2 * s, s)
            close()
        }
        return Outline.Generic(p)
    }
}

/** ورقة ملاحظة: الزاوية السفلية اليمنى مقصوصة (مطوية) */
private class NoteShape(private val cutDp: Float = 16f, private val rDp: Float = 6f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = cutDp * density.density
        val r = rDp * density.density
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(r, 0f); lineTo(w - r, 0f); quadraticTo(w, 0f, w, r)
            lineTo(w, h - c); lineTo(w - c, h)
            lineTo(r, h); quadraticTo(0f, h, 0f, h - r)
            lineTo(0f, r); quadraticTo(0f, 0f, r, 0f)
            close()
        }
        return Outline.Generic(p)
    }
}

private fun Shape.toClipPath(size: Size, dir: LayoutDirection, d: Density): Path = when (val o = createOutline(size, dir, d)) {
    is Outline.Generic -> o.path
    is Outline.Rounded -> Path().apply { addRoundRect(o.roundRect) }
    is Outline.Rectangle -> Path().apply { addRect(o.rect) }
}

// ----- نار -----
private fun DrawScope.fireTongue(cx: Float, baseY: Float, wd: Float, hg: Float, c: Color) {
    val p = Path().apply {
        moveTo(cx - wd / 2, baseY)
        cubicTo(cx - wd * 0.65f, baseY - hg * 0.45f, cx - wd * 0.1f, baseY - hg * 0.5f, cx + wd * 0.02f, baseY - hg)
        cubicTo(cx + wd * 0.15f, baseY - hg * 0.55f, cx + wd * 0.65f, baseY - hg * 0.4f, cx + wd / 2, baseY)
        close()
    }
    drawPath(p, c)
}

private fun DrawScope.fireFlame(cx: Float, baseY: Float, wd: Float, hg: Float) {
    fireTongue(cx, baseY, wd, hg, Color(0xFFFF3B1F))
    fireTongue(cx, baseY, wd * 0.68f, hg * 0.72f, Color(0xFFFF9A1F))
    fireTongue(cx, baseY, wd * 0.38f, hg * 0.42f, Color(0xFFFFE066))
}

private fun DrawScope.innerFire(w: Float, h: Float) {
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Color(0xFFFF5A00).copy(alpha = 0.42f)), startY = h * 0.15f, endY = h),
        size = Size(w, h)
    )
}

private fun DrawScope.decorFire(w: Float, h: Float) {
    val u = 1.dp.toPx()
    drawRoundRect(Color(0xFFFF7A1A).copy(alpha = 0.22f), Offset(-1 * u, -1 * u), Size(w + 2 * u, h + 2 * u), CornerRadius(23 * u), style = Stroke(6 * u))
    // ألسنة لهب على الحافة العليا
    fireFlame(w * 0.40f, 3 * u, 9 * u, 14 * u)
    fireFlame(w * 0.56f, 3 * u, 12 * u, 21 * u)
    fireFlame(w * 0.73f, 3 * u, 9 * u, 13 * u)
    fireFlame(w - 14 * u, 3 * u, 7 * u, 9 * u)
    // لهبة كبيرة بالجهة اليسرى
    fireFlame(17 * u, h / 2 + 13 * u, 22 * u, 31 * u)
    // شرارات
    val ember = Color(0xFFFFD166)
    drawCircle(ember, 1.5f * u, Offset(w * 0.47f, -9 * u))
    drawCircle(ember.copy(alpha = 0.8f), 1.1f * u, Offset(w * 0.66f, -12 * u))
    drawCircle(ember.copy(alpha = 0.7f), 1.3f * u, Offset(32 * u, -4 * u))
    drawCircle(ember, 1.2f * u, Offset(w - 6 * u, h * 0.4f))
}

// ----- ساكورا -----
private fun DrawScope.blossom(cx: Float, cy: Float, r: Float, rot: Float = 0f) {
    val c = Offset(cx, cy)
    rotate(rot, c) {
        for (i in 0 until 5) {
            rotate(i * 72f, c) {
                drawOval(Color(0xFFFFB3CB), Offset(cx - r * 0.34f, cy - r), Size(r * 0.68f, r * 0.95f))
                drawOval(Color(0xFFFF8FB3).copy(alpha = 0.55f), Offset(cx - r * 0.1f, cy - r * 0.62f), Size(r * 0.2f, r * 0.45f))
            }
        }
        for (i in 0 until 5) {
            val a = (i * 72.0 - 90.0) * PI / 180.0
            val tip = Offset(cx + (r * 0.42f * cos(a)).toFloat(), cy + (r * 0.42f * sin(a)).toFloat())
            drawLine(Color(0xFFD63A73), c, tip, r * 0.06f)
            drawCircle(Color(0xFFFFE08A), r * 0.08f, tip)
        }
        drawCircle(Color(0xFFD63A73), r * 0.14f, c)
    }
}

private fun DrawScope.petal(cx: Float, cy: Float, r: Float, rot: Float, a: Float) {
    rotate(rot, Offset(cx, cy)) {
        drawOval(Color(0xFFFFA8C5).copy(alpha = a), Offset(cx - r * 0.45f, cy - r), Size(r * 0.9f, r * 1.7f))
    }
}

private fun DrawScope.innerSakura(w: Float, h: Float) {
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.75f), Color.Transparent), center = Offset(w * 0.25f, 0f), radius = w * 0.55f),
        w * 0.55f, Offset(w * 0.25f, 0f)
    )
}

private fun DrawScope.decorSakura(w: Float, h: Float) {
    val u = 1.dp.toPx()
    // غصن فوق الفقاعة
    val branch = Path().apply {
        moveTo(-6 * u, -1 * u)
        quadraticTo(14 * u, -12 * u, 44 * u, -1 * u)
    }
    drawPath(branch, Color(0xFF8D5A44), style = Stroke(2.2f * u, cap = StrokeCap.Round))
    blossom(11 * u, -7 * u, 7.5f * u, 12f)
    blossom(30 * u, -6 * u, 6f * u, -20f)
    drawCircle(Color(0xFFFF6F9F), 2.4f * u, Offset(43 * u, -2 * u))
    // زهرة كبيرة باليسار
    blossom(17 * u, h / 2 + 3 * u, 11.5f * u, 8f)
    // بتلات متساقطة
    petal(w - 9 * u, 9 * u, 4.5f * u, 35f, 0.9f)
    petal(w - 22 * u, h - 6 * u, 3.8f * u, -50f, 0.8f)
    petal(w * 0.58f, h - 3 * u, 3.2f * u, 70f, 0.7f)
    petal(w * 0.7f, -4 * u, 3.4f * u, -15f, 0.85f)
}

// ----- دونات -----
private fun DrawScope.sprinkle(c: Offset, deg: Double, len: Float, color: Color, wd: Float) {
    val dx = (len * cos(deg * PI / 180.0)).toFloat()
    val dy = (len * sin(deg * PI / 180.0)).toFloat()
    drawLine(color, Offset(c.x - dx, c.y - dy), Offset(c.x + dx, c.y + dy), wd, StrokeCap.Round)
}

private fun DrawScope.innerDonut(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val base = h * 0.46f
    val icing = Path().apply {
        moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, base)
        var x = w
        while (x > 0f) {
            x -= 2 * u
            val drip = sin(x / (17 * u) + 1f).coerceAtLeast(0f)
            lineTo(x, base + 4 * u * sin(x / (6 * u)) + 8 * u * drip * drip * drip)
        }
        lineTo(0f, base); close()
    }
    drawPath(icing, Brush.verticalGradient(listOf(Color(0xFFFF8DB8), Color(0xFFFF6FA3)), startY = 0f, endY = base + 8 * u))
    // لمعة على الكريمة
    drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(w * 0.3f, 3.5f * u), Size(w * 0.34f, 2.4f * u), CornerRadius(1.2f * u))
    // رشّات
    val cols = listOf(Color(0xFFFFFFFF), Color(0xFFFFE066), Color(0xFF5CE1E6), Color(0xFF9CFF6B), Color(0xFFFFFFFF), Color(0xFFB794FF))
    listOf(
        Triple(0.34f, 0.30f, 20.0), Triple(0.42f, 0.62f, 110.0), Triple(0.5f, 0.2f, 70.0),
        Triple(0.58f, 0.55f, 160.0), Triple(0.66f, 0.28f, 35.0), Triple(0.74f, 0.6f, 130.0),
        Triple(0.82f, 0.22f, 90.0), Triple(0.9f, 0.5f, 15.0), Triple(0.26f, 0.7f, 60.0)
    ).forEachIndexed { i, (fx, fy, deg) -> sprinkle(Offset(w * fx, base * fy), deg, 2.6f * u, cols[i % cols.size], 1.9f * u) }
}

private fun DrawScope.decorDonut(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val c = Offset(19 * u, h / 2)
    drawCircle(Color(0xFFD9964B), 13.5f * u, c)
    drawCircle(Color(0xFF7A4023), 12f * u, c)
    drawCircle(Color(0xFFFFEBCC), 4.4f * u, c)
    drawCircle(Color(0xFFB5702C), 4.4f * u, c, style = Stroke(1.1f * u))
    drawArc(Color.White.copy(alpha = 0.45f), 200f, 55f, false, Offset(c.x - 9 * u, c.y - 9 * u), Size(18 * u, 18 * u), style = Stroke(1.8f * u, cap = StrokeCap.Round))
    val cols = listOf(Color(0xFFFF5C93), Color(0xFFFFE066), Color(0xFF5CE1E6), Color(0xFF9CFF6B), Color(0xFFFFFFFF), Color(0xFFFF9F43))
    for (i in 0 until 8) {
        val a = (i * 45.0 + 12.0) * PI / 180.0
        val p = Offset(c.x + (8.3f * u * cos(a)).toFloat(), c.y + (8.3f * u * sin(a)).toFloat())
        sprinkle(p, i * 45.0 + 70.0, 1.7f * u, cols[i % cols.size], 1.6f * u)
    }
}

// ----- حلوى -----
private fun DrawScope.candyEnd(h: Float, u: Float) {
    val cy = h / 2
    val fan = Path().apply {
        moveTo(22 * u, cy - 1.5f * u)
        lineTo(1 * u, cy - 12 * u)
        quadraticTo(6 * u, cy, 1 * u, cy + 12 * u)
        lineTo(22 * u, cy + 1.5f * u)
        close()
    }
    drawPath(fan, Color(0xFFFFD6E8))
    drawPath(fan, Color(0xFFE8317F), style = Stroke(1.2f * u, join = StrokeJoin.Round))
    for (k in -1..1) drawLine(Color(0xFFE8317F).copy(alpha = 0.7f), Offset(21 * u, cy + k * 0.8f * u), Offset(4 * u, cy + k * 7.5f * u), 1f * u)
}

private fun DrawScope.innerCandy(w: Float, h: Float) {
    val u = 1.dp.toPx()
    var x = -h
    while (x < w) {
        drawLine(Color.White.copy(alpha = 0.20f), Offset(x, h + 2 * u), Offset(x + h + 4 * u, -2 * u), 6 * u)
        x += 15 * u
    }
    drawRoundRect(Color.White.copy(alpha = 0.24f), Offset(h * 0.45f, 3 * u), Size(w - h * 0.9f, h * 0.2f), CornerRadius(h * 0.1f))
}

private fun DrawScope.decorCandy(w: Float, h: Float) {
    val u = 1.dp.toPx()
    candyEnd(h, u)
    withTransform({ scale(-1f, 1f, Offset(w / 2f, h / 2f)) }) { candyEnd(h, u) }
    sparkle(w * 0.5f, -3 * u, 4 * u, Color.White)
}

// ----- بكسل -----
private fun DrawScope.pixelGrid(rows: List<String>, x: Float, y: Float, cell: Float, colorFor: (Char) -> Color?) {
    rows.forEachIndexed { r, line ->
        line.forEachIndexed { c, ch ->
            colorFor(ch)?.let { drawRect(it, Offset(x + c * cell, y + r * cell), Size(cell, cell)) }
        }
    }
}

private fun DrawScope.pixelStar(cx: Float, cy: Float, cell: Float, color: Color) {
    drawRect(color, Offset(cx - cell / 2, cy - cell / 2), Size(cell, cell))
    drawRect(color, Offset(cx - cell * 1.5f, cy - cell / 2), Size(cell, cell))
    drawRect(color, Offset(cx + cell / 2, cy - cell / 2), Size(cell, cell))
    drawRect(color, Offset(cx - cell / 2, cy - cell * 1.5f), Size(cell, cell))
    drawRect(color, Offset(cx - cell / 2, cy + cell / 2), Size(cell, cell))
}

private fun DrawScope.innerPixel(w: Float, h: Float) {
    val u = 1.dp.toPx()
    // خطوط شاشة CRT
    var y = 0f
    while (y < h) {
        drawRect(Color.Black.copy(alpha = 0.22f), Offset(0f, y), Size(w, 1.2f * u))
        y += 3 * u
    }
    drawRect(Brush.horizontalGradient(listOf(Color(0xFF7CFF9B).copy(alpha = 0.16f), Color.Transparent), endX = w * 0.5f), size = Size(w, h))
}

private fun DrawScope.decorPixel(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cell = 3 * u
    pixelGrid(
        listOf(".RR.RR.", "RWRRRRR", "RRRRRRr", ".RRRRr.", "..Rrr..", "...r..."),
        5 * u, h / 2 - 9 * u, cell
    ) { ch ->
        when (ch) {
            'R' -> Color(0xFFFF3B5C)
            'r' -> Color(0xFFB8143A)
            'W' -> Color.White
            else -> null
        }
    }
    pixelStar(w - 9 * u, 8 * u, 2 * u, Color(0xFFFFE66D))
    pixelStar(w * 0.55f, h - 5 * u, 1.5f * u, Color(0xFF7CFF9B))
}

// ----- ملكي -----
private fun DrawScope.innerRoyal(w: Float, h: Float) {
    val u = 1.dp.toPx()
    drawRect(
        Brush.linearGradient(
            0f to Color.Transparent, 0.42f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.42f),
            0.58f to Color.Transparent, 1f to Color.Transparent,
            start = Offset(0f, 0f), end = Offset(w * 0.9f, h * 2.2f)
        ),
        size = Size(w, h)
    )
    drawRoundRect(Color(0xFFFFF6C4).copy(alpha = 0.7f), Offset(3 * u, 3 * u), Size(w - 6 * u, h - 6 * u), CornerRadius(17 * u), style = Stroke(1f * u))
}

private fun DrawScope.decorRoyal(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val gold = Color(0xFFFFC93C)
    val deep = Color(0xFF8A5A00)
    val x0 = 8 * u
    val cw = 32 * u
    val crown = Path().apply {
        moveTo(x0, 4 * u)
        lineTo(x0, -7 * u)
        lineTo(x0 + cw * 0.25f, -1.5f * u)
        lineTo(x0 + cw * 0.5f, -11 * u)
        lineTo(x0 + cw * 0.75f, -1.5f * u)
        lineTo(x0 + cw, -7 * u)
        lineTo(x0 + cw, 4 * u)
        close()
    }
    drawPath(crown, Brush.verticalGradient(listOf(Color(0xFFFFEB99), gold), startY = -11 * u, endY = 4 * u))
    drawPath(crown, deep, style = Stroke(1.4f * u, join = StrokeJoin.Round))
    drawRect(deep, Offset(x0, 0.2f * u), Size(cw, 1.2f * u))
    drawCircle(Color(0xFFE53935), 2.3f * u, Offset(x0, -7 * u))
    drawCircle(Color(0xFF29B6F6), 2.6f * u, Offset(x0 + cw * 0.5f, -11 * u))
    drawCircle(Color(0xFF43A047), 2.3f * u, Offset(x0 + cw, -7 * u))
    drawCircle(Color(0xFFFFFFFF).copy(alpha = 0.8f), 0.8f * u, Offset(x0 + cw * 0.5f - 0.8f * u, -11.8f * u))
    sparkle(w - 9 * u, 7 * u, 5 * u, Color.White)
    sparkle(w * 0.62f, h - 5 * u, 3.5f * u, Color(0xFFFFF3B0))
}

// ----- صاعقة -----
private fun DrawScope.boltShape(x: Float, y: Float, s: Float): Path = Path().apply {
    moveTo(x + s * 0.60f, y); lineTo(x + s * 0.10f, y + s * 0.58f); lineTo(x + s * 0.42f, y + s * 0.58f)
    lineTo(x + s * 0.28f, y + s); lineTo(x + s * 0.90f, y + s * 0.38f); lineTo(x + s * 0.56f, y + s * 0.38f)
    lineTo(x + s * 0.78f, y)
    close()
}

private fun DrawScope.innerBolt(w: Float, h: Float) {
    val u = 1.dp.toPx()
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFFFD60A).copy(alpha = 0.30f), Color.Transparent), center = Offset(16 * u, h / 2), radius = 46 * u),
        46 * u, Offset(16 * u, h / 2)
    )
    drawLine(Color.White.copy(alpha = 0.06f), Offset(w * 0.4f, h), Offset(w * 0.55f, 0f), 9 * u)
    drawLine(Color.White.copy(alpha = 0.05f), Offset(w * 0.7f, h), Offset(w * 0.82f, 0f), 6 * u)
}

private fun DrawScope.decorBolt(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val yel = Color(0xFFFFD60A)
    drawRoundRect(yel.copy(alpha = 0.18f), Offset(0f, 0f), Size(w, h), CornerRadius(14 * u), style = Stroke(8 * u))
    drawRoundRect(yel.copy(alpha = 0.95f), Offset(0.8f * u, 0.8f * u), Size(w - 1.6f * u, h - 1.6f * u), CornerRadius(14 * u), style = Stroke(1.6f * u))
    // صاعقة كبيرة بالجهة اليسرى مع توهج
    val big = boltShape(4 * u, h / 2 - 14 * u, 28 * u)
    drawPath(big, yel.copy(alpha = 0.35f), style = Stroke(5 * u, join = StrokeJoin.Round))
    drawPath(big, Brush.verticalGradient(listOf(Color(0xFFFFF59D), Color(0xFFFFB300)), startY = h / 2 - 14 * u, endY = h / 2 + 14 * u))
    // خط برق متعرج عند الزاوية العليا
    val zig = Path().apply {
        moveTo(w - 36 * u, -6 * u); lineTo(w - 29 * u, 2 * u); lineTo(w - 33 * u, 3.5f * u); lineTo(w - 23 * u, 12 * u)
    }
    drawPath(zig, yel.copy(alpha = 0.4f), style = Stroke(4.5f * u, join = StrokeJoin.Miter))
    drawPath(zig, Color.White, style = Stroke(1.4f * u, join = StrokeJoin.Miter))
    drawPath(boltShape(w - 18 * u, h - 14 * u, 11 * u), yel)
}

// ----- دعسوقة -----
private fun DrawScope.innerLadybug(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val dot = Color(0xFF1A1A1A).copy(alpha = 0.85f)
    drawCircle(dot, 4.4f * u, Offset(w * 0.32f, 5 * u))
    drawCircle(dot, 3.6f * u, Offset(w * 0.5f, h - 4 * u))
    drawCircle(dot, 4.6f * u, Offset(w * 0.7f, 6 * u))
    drawCircle(dot, 3.8f * u, Offset(w * 0.86f, h - 7 * u))
    drawCircle(dot, 3.2f * u, Offset(w - 6 * u, h * 0.4f))
    drawRoundRect(Color.White.copy(alpha = 0.26f), Offset(w * 0.18f, 3.2f * u), Size(w * 0.5f, 3f * u), CornerRadius(1.5f * u))
}

private fun DrawScope.decorLadybug(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val ink = Color(0xFF1A1A1A)
    val cy = h / 2
    // قرون الاستشعار
    val a1 = Path().apply { moveTo(7 * u, cy - 8 * u); quadraticTo(3 * u, -2 * u, -3 * u, -9 * u) }
    val a2 = Path().apply { moveTo(15 * u, cy - 9 * u); quadraticTo(19 * u, -3 * u, 25 * u, -10 * u) }
    drawPath(a1, ink, style = Stroke(1.6f * u, cap = StrokeCap.Round))
    drawPath(a2, ink, style = Stroke(1.6f * u, cap = StrokeCap.Round))
    drawCircle(ink, 2.2f * u, Offset(-3 * u, -9 * u))
    drawCircle(ink, 2.2f * u, Offset(25 * u, -10 * u))
    // الرأس
    drawCircle(ink, 11.5f * u, Offset(11 * u, cy))
    drawCircle(Color.White, 3.2f * u, Offset(7.5f * u, cy - 2.5f * u))
    drawCircle(Color.White, 3.2f * u, Offset(15.5f * u, cy - 2.5f * u))
    drawCircle(ink, 1.5f * u, Offset(8.2f * u, cy - 2.3f * u))
    drawCircle(ink, 1.5f * u, Offset(16.2f * u, cy - 2.3f * u))
    drawArc(Color(0xFFFF8A80), 20f, 140f, false, Offset(7 * u, cy + 1 * u), Size(8 * u, 6 * u), style = Stroke(1.2f * u, cap = StrokeCap.Round))
}

// ----- ورقة ملاحظة -----
private fun DrawScope.innerNote(w: Float, h: Float) {
    val u = 1.dp.toPx()
    var y = 11 * u
    while (y < h - 4 * u) {
        drawLine(Color(0xFF4A90D9).copy(alpha = 0.16f), Offset(0f, y), Offset(w, y), 1f * u)
        y += 9 * u
    }
    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), endY = h * 0.5f), size = Size(w, h))
}

private fun DrawScope.decorNote(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val c = 16 * u
    // الطيّة
    val flap = Path().apply { moveTo(w - c, h); lineTo(w, h - c); lineTo(w - c, h - c); close() }
    drawPath(flap, Brush.linearGradient(listOf(Color(0xFFE6C200), Color(0xFFFFF176)), Offset(w, h - c), Offset(w - c, h)))
    drawPath(flap, Color(0xFFB89700).copy(alpha = 0.55f), style = Stroke(1f * u, join = StrokeJoin.Round))
    // شريط لاصق
    rotate(-5f, Offset(w * 0.4f, 0f)) {
        drawRect(Color(0xFFF1E3B4).copy(alpha = 0.92f), Offset(w * 0.4f - 18 * u, -6.5f * u), Size(36 * u, 12 * u))
        drawRect(Color(0xFFC8B26A).copy(alpha = 0.6f), Offset(w * 0.4f - 18 * u, -6.5f * u), Size(36 * u, 12 * u), style = Stroke(0.8f * u))
    }
    // خط تحت الكلام
    val sq = Path().apply {
        moveTo(w * 0.12f, h - 3.5f * u)
        var x = w * 0.12f
        var up = true
        while (x < w * 0.5f) { x += 4 * u; lineTo(x, h - (if (up) 5.5f else 2.5f) * u); up = !up }
    }
    drawPath(sq, Color(0xFFE53935).copy(alpha = 0.7f), style = Stroke(1.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// ----- موج -----
private fun DrawScope.waveLayer(w: Float, h: Float, base: Float, amp: Float, period: Float, phase: Float, color: Color, step: Float) {
    val p = Path().apply {
        moveTo(0f, h)
        lineTo(0f, base + amp * sin(phase))
        var x = 0f
        while (x <= w) { lineTo(x, base + amp * sin(x / period + phase)); x += step }
        lineTo(w, h)
        close()
    }
    drawPath(p, color)
}

private fun DrawScope.innerWaves(w: Float, h: Float) {
    val u = 1.dp.toPx()
    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), endY = h * 0.55f), size = Size(w, h))
    waveLayer(w, h, h * 0.62f, 3 * u, 6 * u, 0f, Color.White.copy(alpha = 0.16f), 2 * u)
    waveLayer(w, h, h * 0.75f, 2.6f * u, 5 * u, 2f, Color(0xFF0B3C8C).copy(alpha = 0.28f), 2 * u)
}

private fun DrawScope.decorWaves(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cy = h / 2
    // السمكة
    val tail = Path().apply {
        moveTo(12 * u, cy); lineTo(2 * u, cy - 8 * u); lineTo(5.5f * u, cy); lineTo(2 * u, cy + 8 * u); close()
    }
    drawPath(tail, Color(0xFFFF7A2F))
    val fin = Path().apply { moveTo(15 * u, cy - 6 * u); lineTo(20 * u, cy - 12 * u); lineTo(24 * u, cy - 6 * u); close() }
    drawPath(fin, Color(0xFFFF7A2F))
    drawOval(Color(0xFFFFA24C), Offset(9 * u, cy - 7.5f * u), Size(21 * u, 15 * u))
    drawLine(Color.White.copy(alpha = 0.75f), Offset(17 * u, cy - 6 * u), Offset(17 * u, cy + 6 * u), 2f * u, StrokeCap.Round)
    drawCircle(Color.White, 2.4f * u, Offset(25 * u, cy - 2 * u))
    drawCircle(Color(0xFF0B1B3C), 1.2f * u, Offset(25.6f * u, cy - 2 * u))
    // فقاعات هواء
    val air = Color.White.copy(alpha = 0.85f)
    drawCircle(air, 2.6f * u, Offset(33 * u, cy - 10 * u), style = Stroke(1f * u))
    drawCircle(air, 1.8f * u, Offset(36 * u, cy - 17 * u), style = Stroke(1f * u))
    drawCircle(air, 3.2f * u, Offset(w - 10 * u, 9 * u), style = Stroke(1f * u))
    drawCircle(air, 2.0f * u, Offset(w - 17 * u, h - 8 * u), style = Stroke(1f * u))
}

// ---------------------------------------------------------------------------
// قائمة الأشكال (46 = 26 + 20 جديدة)
// ---------------------------------------------------------------------------

val BubbleStyles: List<BubbleStyle> = listOf(
    BubbleStyle(id = "glass", name = "زجاجي", glass = true),
    BubbleStyle(
        id = "ghost", name = "شبح", shape = RoundedCornerShape(26.dp),
        fill = listOf(Color.White), text = Color(0xFF111111),
        border = 2.5.dp, borderColor = Color.Black, sidePad = 34.dp, decor = DrawScope::decorGhost
    ),
    BubbleStyle(
        id = "pumpkin", name = "يقطينة", shape = RoundedCornerShape(28.dp),
        fill = listOf(Color(0xFFFF9A2E), Color(0xFFE0620F)), text = Color.White,
        border = 2.dp, borderColor = Color(0xFFB84A0A), sidePad = 34.dp, topPad = 6.dp,
        decor = DrawScope::decorPumpkin
    ),
    BubbleStyle(
        id = "spider", name = "عنكبوت", shape = RoundedCornerShape(22.dp),
        fill = listOf(Color.Black), text = Color.White,
        border = 1.5.dp, borderColor = Color.White, topPad = 14.dp, decor = DrawScope::decorSpider
    ),
    BubbleStyle(
        id = "sketch", name = "رسم تخطيطي", shape = RoundedCornerShape(16.dp),
        fill = listOf(Color(0xFFD8F4FF)), text = Color(0xFF0B4A66), decor = DrawScope::decorSketch
    ),
    BubbleStyle(
        id = "otter", name = "ثعلب الماء", shape = RoundedCornerShape(10.dp),
        fill = listOf(Color(0xFF8FE0F2)), text = Color(0xFF0B3F4F), sidePad = 36.dp, decor = DrawScope::decorOtter
    ),
    BubbleStyle(
        id = "flowers", name = "وردي", shape = RoundedCornerShape(16.dp),
        fill = listOf(Color(0xFFF4B6C8)), text = Color(0xFF5A1730), decor = DrawScope::decorFlowers
    ),
    BubbleStyle(
        id = "capybara", name = "كابيبارا", shape = RoundedCornerShape(28.dp),
        fill = listOf(Color(0xFFD9A066)), text = Color(0xFF3B2410), sidePad = 34.dp, decor = DrawScope::decorCapybara
    ),
    BubbleStyle(
        id = "duckfrog", name = "ضفدع وبطة", shape = RoundedCornerShape(12.dp),
        fill = listOf(Color(0xFFFFF59D)), text = Color(0xFF4A3F00), topPad = 10.dp, decor = DrawScope::decorDuckFrog
    ),
    BubbleStyle(
        id = "cat", name = "قطة", shape = RoundedCornerShape(18.dp),
        fill = listOf(Color(0xFFFFF4E0)), text = Color(0xFF3B2A1A),
        border = 2.dp, borderColor = Color(0xFF3B2A1A), sidePad = 34.dp, topPad = 10.dp,
        decor = DrawScope::decorCat
    ),
    BubbleStyle(
        id = "neon", name = "نيون", shape = RoundedCornerShape(20.dp),
        fill = listOf(Color(0xFF0E0B24), Color(0xFF1A1145)), text = Color(0xFFE9FEFF),
        topPad = 4.dp, decor = DrawScope::decorNeon
    ),
    BubbleStyle(
        id = "space", name = "فضاء", shape = RoundedCornerShape(26.dp),
        fill = listOf(Color(0xFF3A2480), Color(0xFF0C1B52)), text = Color.White,
        sidePad = 40.dp, decor = DrawScope::decorSpace
    ),
    BubbleStyle(
        id = "hearts", name = "قلوب", shape = RoundedCornerShape(24.dp),
        fill = listOf(Color(0xFFFF9DBA), Color(0xFFFF5C8A)), text = Color.White,
        topPad = 8.dp, decor = DrawScope::decorHearts
    ),
    BubbleStyle(
        id = "cloud", name = "غيمة", shape = RoundedCornerShape(26.dp),
        fill = listOf(Color(0xFFD3EEFF), Color(0xFF9FD3FA)), text = Color(0xFF0D3B66),
        sidePad = 34.dp, topPad = 10.dp, decor = DrawScope::decorCloud
    ),
    BubbleStyle(
        id = "snow", name = "ثلج", shape = RoundedCornerShape(18.dp),
        fill = listOf(Color(0xFFEFF9FF), Color(0xFFCDEBFF)), text = Color(0xFF0E3A5C),
        border = 1.5.dp, borderColor = Color(0xFFA6D6F5), sidePad = 34.dp, topPad = 6.dp,
        decor = DrawScope::decorSnow
    ),
    BubbleStyle(
        id = "robot", name = "روبوت", shape = RoundedCornerShape(10.dp),
        fill = listOf(Color(0xFFC3CEDA), Color(0xFF93A1B0)), text = Color(0xFF14202B),
        border = 2.dp, borderColor = Color(0xFF4A5663), sidePad = 38.dp, topPad = 12.dp,
        decor = DrawScope::decorRobot
    ),
    BubbleStyle(
        id = "fire", name = "نار", shape = RoundedCornerShape(22.dp),
        fill = listOf(Color(0xFF2A0A05), Color(0xFF6B1606)), text = Color(0xFFFFF1D6),
        border = 1.5.dp, borderColor = Color(0xFFFF7A1A), sidePad = 30.dp, topPad = 14.dp,
        decor = DrawScope::decorFire, inner = DrawScope::innerFire
    ),
    BubbleStyle(
        id = "sakura", name = "ساكورا", shape = RoundedCornerShape(24.dp),
        fill = listOf(Color(0xFFFFE9F0), Color(0xFFFFCBDC)), text = Color(0xFF5C1F36),
        border = 1.dp, borderColor = Color(0xFFFFA9C3), sidePad = 34.dp, topPad = 14.dp,
        decor = DrawScope::decorSakura, inner = DrawScope::innerSakura
    ),
    BubbleStyle(
        id = "donut", name = "دونات", shape = RoundedCornerShape(26.dp),
        fill = listOf(Color(0xFFFFF3E0), Color(0xFFFFE0B5)), text = Color(0xFF4A1230),
        border = 1.5.dp, borderColor = Color(0xFFE0A35A), sidePad = 38.dp, topPad = 4.dp,
        decor = DrawScope::decorDonut, inner = DrawScope::innerDonut
    ),
    BubbleStyle(
        id = "candy", name = "حلوى", shape = RoundedCornerShape(percent = 50),
        fill = listOf(Color(0xFFE8317F), Color(0xFFFF5F9E)), text = Color.White,
        sidePad = 24.dp, farPad = 24.dp, topPad = 4.dp,
        decor = DrawScope::decorCandy, inner = DrawScope::innerCandy
    ),
    BubbleStyle(
        id = "pixel", name = "بكسل", shape = PixelShape(),
        fill = listOf(Color(0xFF0B1220), Color(0xFF14233A)), text = Color(0xFF7CFF9B),
        border = 2.dp, borderColor = Color(0xFF7CFF9B), sidePad = 32.dp,
        decor = DrawScope::decorPixel, inner = DrawScope::innerPixel
    ),
    BubbleStyle(
        id = "royal", name = "ملكي", shape = RoundedCornerShape(20.dp),
        fill = listOf(Color(0xFFFFE680), Color(0xFFE5A91C), Color(0xFFFFD24D)), text = Color(0xFF3A2400),
        border = 2.dp, borderColor = Color(0xFF8A5A00), topPad = 14.dp,
        decor = DrawScope::decorRoyal, inner = DrawScope::innerRoyal
    ),
    BubbleStyle(
        id = "bolt", name = "صاعقة", shape = RoundedCornerShape(14.dp),
        fill = listOf(Color(0xFF120C2E), Color(0xFF2A1670)), text = Color(0xFFFFF7C2),
        sidePad = 32.dp, topPad = 8.dp,
        decor = DrawScope::decorBolt, inner = DrawScope::innerBolt
    ),
    BubbleStyle(
        id = "ladybug", name = "دعسوقة", shape = RoundedCornerShape(24.dp),
        fill = listOf(Color(0xFFFF4D4D), Color(0xFFC81D25)), text = Color.White,
        border = 2.dp, borderColor = Color(0xFF1A1A1A), sidePad = 34.dp, topPad = 11.dp,
        decor = DrawScope::decorLadybug, inner = DrawScope::innerLadybug
    ),
    BubbleStyle(
        id = "note", name = "ورقة", shape = NoteShape(),
        fill = listOf(Color(0xFFFFF59D), Color(0xFFFFEB66)), text = Color(0xFF3D3200),
        topPad = 8.dp,
        decor = DrawScope::decorNote, inner = DrawScope::innerNote
    ),
    BubbleStyle(
        id = "waves", name = "موج", shape = RoundedCornerShape(22.dp),
        fill = listOf(Color(0xFF2BC0E4), Color(0xFF1565C0)), text = Color.White,
        sidePad = 38.dp, topPad = 8.dp,
        decor = DrawScope::decorWaves, inner = DrawScope::innerWaves
    )
) + ExtraBubbleStyles // + 20 شكل جديد من BubbleStylesExtra.kt

fun bubbleStyleById(id: String): BubbleStyle = BubbleStyles.firstOrNull { it.id == id } ?: BubbleStyles[0]

private fun List<Color>.toBrush(): Brush =
    if (size == 1) SolidColor(first()) else Brush.linearGradient(this)

/**
 * فقاعة بشكل مختار. [backdrop] = null يعني معاينة (بدون زجاج حقيقي).
 * [content] تستلم لون النص المناسب.
 */
@Composable
fun StyledBubble(
    style: BubbleStyle,
    mine: Boolean,
    dark: Boolean,
    fg: Color,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    content: @Composable (textColor: Color) -> Unit
) {
    val shape = style.shape
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // الشخصية تكون على الجهة البعيدة عن صورة صاحب الرسالة
    val charLeft = (mine && rtl) || (!mine && !rtl)
    val sp = style.sidePad
    val padLeft = if (charLeft) sp else style.farPad
    val padRight = if (!charLeft) sp else style.farPad
    val startPad = 14.dp + (if (rtl) padRight else padLeft)
    val endPad = 14.dp + (if (rtl) padLeft else padRight)

    val textColor = if (style.glass) (if (mine) Color.White else fg) else style.text

    var m: Modifier = modifier.padding(top = style.topPad)

    m = if (style.glass) {
        if (backdrop != null) {
            m.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur((8f.dp.toPx()) * GlassFx.blurK)
                },
                onDrawSurface = {
                    if (mine) {
                        drawRect(Blue, blendMode = BlendMode.Hue)
                        drawRect(Blue.copy(alpha = 0.65f))
                    } else {
                        drawRect(if (dark) Color.White.copy(0.10f) else Color.White.copy(0.50f))
                    }
                }
            )
        } else {
            m.background(if (mine) Blue.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.18f), shape)
        }
    } else {
        if (style.fill.isNotEmpty()) m.background(style.fill.toBrush(), shape) else m
    }

    m = if (style.glass) {
        m.border(
            0.8.dp,
            Brush.verticalGradient(listOf(Color.White.copy(0.45f), Color.White.copy(0.08f))),
            shape
        )
    } else if (style.border > 0.dp) {
        m.border(style.border, style.borderColor, shape)
    } else m

    // رسمة الشخصية فوق الفقاعة (تنعكس إذا الفقاعة على الجهة الثانية)
    // inner: تحت النص ومقصوصة بحدود الشكل، decor: فوق كل شي ويكدر يطلع برا الفقاعة
    val d = style.decor
    val inner = style.inner
    if (d != null || inner != null) {
        m = m.drawWithContent {
            if (inner != null) {
                val clip = shape.toClipPath(size, layoutDirection, this)
                clipPath(clip) {
                    if (charLeft) {
                        inner(this, size.width, size.height)
                    } else {
                        withTransform({ scale(-1f, 1f, Offset(size.width / 2f, size.height / 2f)) }) {
                            inner(this, size.width, size.height)
                        }
                    }
                }
            }
            drawContent()
            if (d != null) {
                if (charLeft) {
                    d(this, size.width, size.height)
                } else {
                    withTransform({ scale(-1f, 1f, Offset(size.width / 2f, size.height / 2f)) }) {
                        d(this, size.width, size.height)
                    }
                }
            }
        }
    }

    Box(m.padding(start = startPad, end = endPad, top = 9.dp, bottom = 9.dp)) {
        content(textColor)
    }
}

/** نص عينة للمعاينة داخل قائمة الأشكال */
@Composable
fun BubbleSampleText(text: String, color: Color) {
    Text(text, color = color, fontSize = 14.sp)
}

