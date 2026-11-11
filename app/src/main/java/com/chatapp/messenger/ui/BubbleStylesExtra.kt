package com.chatapp.messenger.ui

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------------------------------------------------------------------
// عشرين فقاعة جديدة:
// شفق، صبار، فطر، باندا، نحلة، قوس قزح، ألماس، بطيخ، جمجمة، سحر،
// ماتريكس، ريترو، دبدوب، بطريق، نبض، كوميك، جينز، شوكولاتة، شمس، هلال
// ---------------------------------------------------------------------------

// ----- أدوات مشتركة -----

/** رقم شبه عشوائي ثابت بين 0 و 1 (حتى ما يتغير الرسم مع كل إعادة رسم) */
private fun xr(i: Int): Float {
    val v = sin(i * 12.9898) * 43758.5453
    return (v - floor(v)).toFloat()
}

private fun DrawScope.xSpark(cx: Float, cy: Float, r: Float, color: Color) {
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

private fun DrawScope.xStar5(cx: Float, cy: Float, r: Float, color: Color) {
    val p = Path().apply {
        for (i in 0 until 10) {
            val rad = if (i % 2 == 0) r else r * 0.42f
            val a = (i * 36.0 - 90.0) * PI / 180.0
            val x = cx + (rad * cos(a)).toFloat()
            val y = cy + (rad * sin(a)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    drawPath(p, color)
}

private fun DrawScope.xHeart(x: Float, y: Float, s: Float, color: Color) {
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

private fun DrawScope.xCloud(cx: Float, cy: Float, s: Float, color: Color) {
    drawCircle(color, s * 0.55f, Offset(cx - s * 0.6f, cy))
    drawCircle(color, s * 0.75f, Offset(cx, cy - s * 0.25f))
    drawCircle(color, s * 0.55f, Offset(cx + s * 0.6f, cy))
    drawRoundRect(color, Offset(cx - s * 1.15f, cy - s * 0.1f), Size(s * 2.3f, s * 0.55f), CornerRadius(s * 0.27f))
}

// ===========================================================================
// 1) شفق قطبي
// ===========================================================================
private fun DrawScope.innerAurora(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cols = listOf(Color(0xFF39FFA0), Color(0xFF3DDCFF), Color(0xFFB57BFF))
    cols.forEachIndexed { i, c ->
        val base = h * (0.30f + 0.22f * i)
        val p = Path().apply {
            moveTo(0f, base)
            var x = 0f
            while (x <= w) {
                lineTo(x, base + 5 * u * sin(x / (11 * u) + i * 1.7f))
                x += 3 * u
            }
        }
        drawPath(p, c.copy(alpha = 0.14f), style = Stroke(12 * u, cap = StrokeCap.Round))
        drawPath(p, c.copy(alpha = 0.22f), style = Stroke(4 * u, cap = StrokeCap.Round))
    }
    for (i in 0 until 14) {
        drawCircle(
            Color.White.copy(alpha = 0.3f + 0.45f * xr(i + 50)),
            (0.5f + xr(i + 90)) * u,
            Offset(w * xr(i), h * xr(i + 20))
        )
    }
}

private fun DrawScope.decorAurora(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cy = h / 2
    // قمر
    drawCircle(Color(0xFFF3F7D8), 10 * u, Offset(17 * u, cy))
    drawCircle(Color(0xFFD5DBB0), 2.4f * u, Offset(13.5f * u, cy - 3 * u))
    drawCircle(Color(0xFFD5DBB0), 1.8f * u, Offset(20.5f * u, cy + 3 * u))
    drawCircle(Color(0xFFD5DBB0), 1.2f * u, Offset(19 * u, cy - 5 * u))
    drawCircle(Color(0xFF39FFA0).copy(alpha = 0.25f), 14 * u, Offset(17 * u, cy), style = Stroke(3 * u))
    xSpark(w - 9 * u, 8 * u, 5 * u, Color.White)
    xSpark(w * 0.55f, h - 6 * u, 3 * u, Color(0xFF9CFFD6))
}

// ===========================================================================
// 2) صبار
// ===========================================================================
private fun DrawScope.decorCactus(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cy = h / 2
    val g = Color(0xFF2E8B57)
    val dark = Color(0xFF1F6B42)
    // أذرع
    drawRoundRect(g, Offset(5 * u, cy - 3 * u), Size(10 * u, 4.5f * u), CornerRadius(2.2f * u))
    drawRoundRect(g, Offset(5 * u, cy - 10 * u), Size(4.5f * u, 11 * u), CornerRadius(2.2f * u))
    drawRoundRect(g, Offset(19 * u, cy - 6 * u), Size(10 * u, 4.5f * u), CornerRadius(2.2f * u))
    drawRoundRect(g, Offset(24.5f * u, cy - 13 * u), Size(4.5f * u, 11 * u), CornerRadius(2.2f * u))
    // الجسم
    drawRoundRect(g, Offset(11 * u, cy - 13 * u), Size(11 * u, 24 * u), CornerRadius(5.5f * u))
    drawLine(dark, Offset(16.5f * u, cy - 10 * u), Offset(16.5f * u, cy + 8 * u), 1.2f * u, StrokeCap.Round)
    // أشواك
    listOf(Offset(13 * u, cy - 6 * u), Offset(20 * u, cy - 2 * u), Offset(13 * u, cy + 3 * u), Offset(20 * u, cy + 6 * u)).forEach {
        drawLine(Color.White, it, Offset(it.x + 2 * u, it.y - 2 * u), 0.9f * u, StrokeCap.Round)
    }
    // زهرة
    drawCircle(Color(0xFFFF6FA5), 3.2f * u, Offset(16.5f * u, cy - 14 * u))
    drawCircle(Color(0xFFFFE066), 1.2f * u, Offset(16.5f * u, cy - 14 * u))
    // الأصيص
    drawRoundRect(Color(0xFFC8693A), Offset(8 * u, cy + 9 * u), Size(17 * u, 8 * u), CornerRadius(2 * u))
    drawRect(Color(0xFFA9532B), Offset(7 * u, cy + 8 * u), Size(19 * u, 3 * u))
    // شمس صغيرة بالزاوية
    drawCircle(Color(0xFFFFD54F), 4 * u, Offset(w - 8 * u, 7 * u))
    for (k in 0 until 8) {
        val a = k * PI / 4
        drawLine(
            Color(0xFFFFD54F),
            Offset(w - 8 * u + (6 * u * cos(a)).toFloat(), 7 * u + (6 * u * sin(a)).toFloat()),
            Offset(w - 8 * u + (8.5f * u * cos(a)).toFloat(), 7 * u + (8.5f * u * sin(a)).toFloat()),
            1.2f * u, StrokeCap.Round
        )
    }
}

// ===========================================================================
// 3) فطر
// ===========================================================================
private fun DrawScope.innerMushroom(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val spot = Color.White.copy(alpha = 0.3f)
    drawCircle(spot, 6 * u, Offset(w * 0.3f, 2 * u))
    drawCircle(spot, 4.5f * u, Offset(w * 0.52f, h * 0.2f))
    drawCircle(spot, 7 * u, Offset(w * 0.74f, 1 * u))
    drawCircle(spot, 3.5f * u, Offset(w * 0.9f, h * 0.35f))
    drawCircle(spot, 3 * u, Offset(w * 0.4f, h * 0.55f))
    // الشرائح تحت القبعة
    drawRect(Color(0xFFFFF3E0), Offset(0f, h - 4.5f * u), Size(w, 4.5f * u))
    var x = 3 * u
    while (x < w) {
        drawLine(Color(0xFFD7B98E), Offset(x, h - 4.5f * u), Offset(x, h), 1f * u)
        x += 5 * u
    }
}

private fun DrawScope.decorMushroom(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cy = h / 2
    // عشب
    var x = 4 * u
    while (x < w - 4 * u) {
        val p = Path().apply {
            moveTo(x - 1.6f * u, h + 2 * u); lineTo(x + 0.5f * u, h - 6 * u); lineTo(x + 1.8f * u, h + 2 * u); close()
        }
        drawPath(p, Color(0xFF43A047))
        x += 15 * u
    }
    // فطرة صغيرة
    drawRoundRect(Color(0xFFFFF3E0), Offset(12 * u, cy + 1 * u), Size(8 * u, 11 * u), CornerRadius(3 * u))
    drawArc(Color(0xFFFFC107), 180f, 180f, true, Offset(5 * u, cy - 9 * u), Size(22 * u, 22 * u))
    drawCircle(Color.White, 2 * u, Offset(11 * u, cy - 2 * u))
    drawCircle(Color.White, 1.6f * u, Offset(19 * u, cy - 4 * u))
    drawCircle(Color.White, 1.2f * u, Offset(15.5f * u, cy - 6.5f * u))
}

// ===========================================================================
// 4) باندا
// ===========================================================================
private fun DrawScope.decorPanda(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cy = h / 2
    val cx = 17 * u
    val ink = Color(0xFF111111)
    drawCircle(ink, 5 * u, Offset(cx - 9 * u, cy - 10 * u))
    drawCircle(ink, 5 * u, Offset(cx + 9 * u, cy - 10 * u))
    drawCircle(Color.White, 12 * u, Offset(cx, cy))
    drawCircle(ink, 12 * u, Offset(cx, cy), style = Stroke(1.6f * u))
    drawOval(ink, Offset(cx - 9.5f * u, cy - 6 * u), Size(7.5f * u, 9.5f * u))
    drawOval(ink, Offset(cx + 2 * u, cy - 6 * u), Size(7.5f * u, 9.5f * u))
    drawCircle(Color.White, 1.7f * u, Offset(cx - 5.5f * u, cy - 2 * u))
    drawCircle(Color.White, 1.7f * u, Offset(cx + 5.5f * u, cy - 2 * u))
    drawOval(ink, Offset(cx - 2.6f * u, cy + 3 * u), Size(5.2f * u, 3.6f * u))
    drawArc(ink, 20f, 140f, false, Offset(cx - 4 * u, cy + 4 * u), Size(8 * u, 5 * u), style = Stroke(1.2f * u, cap = StrokeCap.Round))
    // خيزران
    val gr = Color(0xFF66BB6A)
    drawRoundRect(gr, Offset(w - 9 * u, -8 * u), Size(5 * u, 24 * u), CornerRadius(2 * u))
    drawLine(Color(0xFF2E7D32), Offset(w - 9 * u, 3 * u), Offset(w - 4 * u, 3 * u), 1.1f * u)
    drawLine(Color(0xFF2E7D32), Offset(w - 9 * u, 10 * u), Offset(w - 4 * u, 10 * u), 1.1f * u)
    val leaf = Path().apply {
        moveTo(w - 4 * u, 0f); quadraticTo(w + 4 * u, -4 * u, w + 5 * u, -9 * u); quadraticTo(w - 1 * u, -6 * u, w - 4 * u, 0f); close()
    }
    drawPath(leaf, Color(0xFF81C784))
}

// ===========================================================================
// 5) نحلة
// ===========================================================================
private fun DrawScope.innerBee(w: Float, h: Float) {
    val u = 1.dp.toPx()
    var x = w * 0.28f
    while (x < w + h) {
        val p = Path().apply {
            moveTo(x, 0f); lineTo(x + 9 * u, 0f); lineTo(x + 9 * u - h * 0.5f, h); lineTo(x - h * 0.5f, h); close()
        }
        drawPath(p, Color(0xFF3A2A00).copy(alpha = 0.13f))
        x += 26 * u
    }
}

private fun DrawScope.decorBee(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cy = h / 2
    val ink = Color(0xFF2B1F00)
    val wing = Color(0xFFB3E5FC)
    // الأجنحة
    drawOval(wing.copy(alpha = 0.9f), Offset(9 * u, cy - 19 * u), Size(10 * u, 14 * u))
    drawOval(ink, Offset(9 * u, cy - 19 * u), Size(10 * u, 14 * u), style = Stroke(0.9f * u))
    drawOval(wing.copy(alpha = 0.9f), Offset(17 * u, cy - 21 * u), Size(10 * u, 15 * u))
    drawOval(ink, Offset(17 * u, cy - 21 * u), Size(10 * u, 15 * u), style = Stroke(0.9f * u))
    // الجسم
    val stinger = Path().apply { moveTo(4 * u, cy - 2 * u); lineTo(-2 * u, cy + 1 * u); lineTo(4 * u, cy + 3 * u); close() }
    drawPath(stinger, ink)
    drawOval(Color(0xFFFFEB3B), Offset(3 * u, cy - 7 * u), Size(24 * u, 15 * u))
    drawLine(ink, Offset(11 * u, cy - 6.3f * u), Offset(11 * u, cy + 6.5f * u), 3f * u, StrokeCap.Round)
    drawLine(ink, Offset(18 * u, cy - 6.5f * u), Offset(18 * u, cy + 6.7f * u), 3f * u, StrokeCap.Round)
    drawOval(ink, Offset(3 * u, cy - 7 * u), Size(24 * u, 15 * u), style = Stroke(1.3f * u))
    // الرأس
    drawCircle(Color(0xFFFFEB3B), 6 * u, Offset(28 * u, cy + 0.5f * u))
    drawCircle(ink, 6 * u, Offset(28 * u, cy + 0.5f * u), style = Stroke(1.3f * u))
    drawCircle(Color.White, 2 * u, Offset(29.5f * u, cy - 1 * u))
    drawCircle(ink, 1f * u, Offset(30 * u, cy - 1 * u))
    drawArc(ink, 20f, 120f, false, Offset(27 * u, cy + 0.5f * u), Size(5 * u, 4 * u), style = Stroke(1f * u, cap = StrokeCap.Round))
    drawLine(ink, Offset(27 * u, cy - 5 * u), Offset(25 * u, cy - 11 * u), 1.1f * u, StrokeCap.Round)
    drawLine(ink, Offset(30 * u, cy - 5 * u), Offset(33 * u, cy - 10 * u), 1.1f * u, StrokeCap.Round)
    // مسار الطيران
    for (k in 0 until 6) {
        drawCircle(ink.copy(alpha = 0.45f), 1.1f * u, Offset(w * 0.55f + k * 8 * u, -3 * u - 3 * u * sin(k * 1.1f)))
    }
}

// ===========================================================================
// 6) قوس قزح
// ===========================================================================
private fun DrawScope.innerRainbow(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cols = listOf(
        Color(0xFFFF5252), Color(0xFFFF9800), Color(0xFFFFEB3B),
        Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFF9C27B0)
    )
    val t = 1.7f * u
    cols.forEachIndexed { i, c ->
        drawRect(c.copy(alpha = 0.85f), Offset(0f, h - t * (cols.size - i)), Size(w, t))
    }
}

private fun DrawScope.decorRainbow(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cx = 17 * u
    val base = h / 2 + 9 * u
    val cols = listOf(
        Color(0xFFFF5252), Color(0xFFFF9800), Color(0xFFFFEB3B),
        Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFF9C27B0)
    )
    cols.forEachIndexed { i, c ->
        val r = (15 - i * 2.2f) * u
        drawArc(c, 180f, 180f, false, Offset(cx - r, base - r), Size(2 * r, 2 * r), style = Stroke(2.3f * u))
    }
    xCloud(cx - 12 * u, base + 1 * u, 4.5f * u, Color.White)
    xCloud(cx + 12 * u, base + 1 * u, 4.5f * u, Color.White)
    xSpark(w - 9 * u, 8 * u, 4.5f * u, Color(0xFFFFD54F))
    xSpark(w * 0.6f, 5 * u, 3 * u, Color(0xFFFF80AB))
}

// ===========================================================================
// 7) ألماس
// ===========================================================================
private fun DrawScope.innerDiamond(w: Float, h: Float) {
    val white = Color.White
    val pts = listOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f)
    for (i in 0 until pts.size - 1) {
        drawLine(white.copy(alpha = 0.16f), Offset(w * pts[i], 0f), Offset(w * pts[i + 1], h), 1.2f.dp.toPx())
        drawLine(white.copy(alpha = 0.16f), Offset(w * pts[i + 1], 0f), Offset(w * pts[i], h), 1.2f.dp.toPx())
    }
    drawRect(
        Brush.linearGradient(
            0f to Color.Transparent, 0.4f to Color.Transparent, 0.5f to white.copy(alpha = 0.35f),
            0.6f to Color.Transparent, 1f to Color.Transparent,
            start = Offset(0f, 0f), end = Offset(w * 0.8f, h * 2f)
        ),
        size = Size(w, h)
    )
}

private fun DrawScope.decorDiamond(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cy = h / 2
    val gem = Path().apply {
        moveTo(7 * u, cy - 4 * u); lineTo(12.5f * u, cy - 11 * u); lineTo(21.5f * u, cy - 11 * u)
        lineTo(27 * u, cy - 4 * u); lineTo(17 * u, cy + 12 * u); close()
    }
    drawPath(gem, Brush.linearGradient(listOf(Color.White, Color(0xFFB3E5FC), Color(0xFF81D4FA)), Offset(7 * u, cy - 11 * u), Offset(27 * u, cy + 12 * u)))
    val line = Color(0xFF29B6F6)
    drawLine(line, Offset(7 * u, cy - 4 * u), Offset(27 * u, cy - 4 * u), 1f * u)
    drawLine(line, Offset(12.5f * u, cy - 11 * u), Offset(14.5f * u, cy - 4 * u), 1f * u)
    drawLine(line, Offset(21.5f * u, cy - 11 * u), Offset(19.5f * u, cy - 4 * u), 1f * u)
    drawLine(line, Offset(14.5f * u, cy - 4 * u), Offset(17 * u, cy + 12 * u), 1f * u)
    drawLine(line, Offset(19.5f * u, cy - 4 * u), Offset(17 * u, cy + 12 * u), 1f * u)
    drawPath(gem, Color.White, style = Stroke(1.4f * u, join = StrokeJoin.Round))
    xSpark(5 * u, cy - 11 * u, 4 * u, Color.White)
    xSpark(w - 9 * u, 8 * u, 5 * u, Color.White)
    xSpark(w * 0.6f, h - 5 * u, 3 * u, Color(0xFFE1F5FE))
}

// ===========================================================================
// 8) بطيخ
// ===========================================================================
private fun DrawScope.seed(cx: Float, cy: Float, u: Float, rot: Float) {
    rotate(rot, Offset(cx, cy)) {
        drawOval(Color(0xFF1B1B1B), Offset(cx - 1.6f * u, cy - 2.6f * u), Size(3.2f * u, 5.2f * u))
    }
}

private fun DrawScope.innerWatermelon(w: Float, h: Float) {
    val u = 1.dp.toPx()
    drawRoundRect(Color(0xFFE8F5C8), Offset(0f, 0f), Size(w, h), CornerRadius(24 * u), style = Stroke(7 * u))
    seed(w * 0.3f, 7 * u, u, 20f)
    seed(w * 0.55f, h - 7 * u, u, -25f)
    seed(w * 0.78f, 7.5f * u, u, -15f)
    seed(w * 0.9f, h * 0.6f, u, 35f)
    seed(w * 0.42f, h * 0.5f, u, 10f)
}

private fun DrawScope.decorWatermelon(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cx = 17 * u
    val cy = h / 2 - 3 * u
    drawArc(Color(0xFF2E9E4F), 0f, 180f, true, Offset(cx - 14 * u, cy - 14 * u), Size(28 * u, 28 * u))
    drawArc(Color(0xFFE8F5C8), 0f, 180f, true, Offset(cx - 12 * u, cy - 12 * u), Size(24 * u, 24 * u))
    drawArc(Color(0xFFFF4D6D), 0f, 180f, true, Offset(cx - 10.5f * u, cy - 10.5f * u), Size(21 * u, 21 * u))
    seed(cx - 5 * u, cy + 3 * u, u, 20f)
    seed(cx + 1 * u, cy + 6 * u, u, -10f)
    seed(cx + 6 * u, cy + 2.5f * u, u, -25f)
    xSpark(w - 8 * u, h - 7 * u, 3.5f * u, Color.White.copy(alpha = 0.9f))
}

// ===========================================================================
// 9) جمجمة
// ===========================================================================
private fun DrawScope.innerSkull(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val crack = Color.White.copy(alpha = 0.10f)
    val p = Path().apply {
        moveTo(w * 0.62f, 0f); lineTo(w * 0.58f, h * 0.3f); lineTo(w * 0.66f, h * 0.5f); lineTo(w * 0.6f, h * 0.8f); lineTo(w * 0.64f, h)
    }
    drawPath(p, crack, style = Stroke(1.2f * u, join = StrokeJoin.Round))
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Color(0xFFB71C1C).copy(alpha = 0.35f)), startY = h * 0.5f, endY = h),
        size = Size(w, h)
    )
}

private fun DrawScope.decorSkull(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cy = h / 2
    val bone = Color(0xFFF5F5DC)
    val ink = Color(0xFF111111)
    // عظمتين متقاطعتين
    drawLine(bone, Offset(4 * u, cy + 5 * u), Offset(30 * u, cy + 14 * u), 3f * u, StrokeCap.Round)
    drawLine(bone, Offset(30 * u, cy + 5 * u), Offset(4 * u, cy + 14 * u), 3f * u, StrokeCap.Round)
    // الجمجمة
    drawCircle(bone, 10 * u, Offset(17 * u, cy - 3 * u))
    drawRoundRect(bone, Offset(11.5f * u, cy + 3 * u), Size(11 * u, 8 * u), CornerRadius(2.5f * u))
    drawCircle(ink, 3 * u, Offset(13 * u, cy - 3 * u))
    drawCircle(ink, 3 * u, Offset(21 * u, cy - 3 * u))
    drawCircle(Color(0xFFFF1744), 1f * u, Offset(13 * u, cy - 3 * u))
    drawCircle(Color(0xFFFF1744), 1f * u, Offset(21 * u, cy - 3 * u))
    val nose = Path().apply { moveTo(17 * u, cy + 0.5f * u); lineTo(15.4f * u, cy + 3.5f * u); lineTo(18.6f * u, cy + 3.5f * u); close() }
    drawPath(nose, ink)
    for (k in 0..3) drawLine(ink, Offset((13.2f + k * 2.5f) * u, cy + 6 * u), Offset((13.2f + k * 2.5f) * u, cy + 10.5f * u), 0.9f * u)
    // قطرات دم
    drawCircle(Color(0xFFD50000), 1.8f * u, Offset(w - 9 * u, 6 * u))
    drawCircle(Color(0xFFD50000), 1.3f * u, Offset(w - 16 * u, 4 * u))
}

// ===========================================================================
// 10) سحر
// ===========================================================================
private fun DrawScope.innerMagic(w: Float, h: Float) {
    val u = 1.dp.toPx()
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFB388FF).copy(alpha = 0.35f), Color.Transparent), center = Offset(w * 0.5f, h * 0.5f), radius = w * 0.45f),
        w * 0.45f, Offset(w * 0.5f, h * 0.5f)
    )
    for (i in 0 until 12) {
        drawCircle(
            Color(0xFFFFF59D).copy(alpha = 0.25f + 0.5f * xr(i + 7)),
            (0.5f + 0.9f * xr(i + 31)) * u,
            Offset(w * xr(i + 3), h * xr(i + 17))
        )
    }
}

private fun DrawScope.decorMagic(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val gold = Color(0xFFFFD54F)
    // قبعة الساحر
    rotate(-10f, Offset(20 * u, 3 * u)) {
        val cone = Path().apply {
            moveTo(8 * u, 3 * u); lineTo(32 * u, 3 * u)
            quadraticTo(30 * u, -7 * u, 26 * u, -14 * u)
            quadraticTo(27 * u, -17 * u, 23 * u, -19 * u)
            quadraticTo(14 * u, -9 * u, 8 * u, 3 * u)
            close()
        }
        drawPath(cone, Color(0xFF7E57C2))
        drawLine(gold, Offset(10 * u, 0f), Offset(31 * u, 0f), 3f * u)
        drawOval(Color(0xFF5E35B1), Offset(3 * u, 0f), Size(34 * u, 7 * u))
        drawOval(gold, Offset(3 * u, 0f), Size(34 * u, 7 * u), style = Stroke(1.1f * u))
        xStar5(21 * u, -8 * u, 3.6f * u, gold)
    }
    xSpark(w - 9 * u, 9 * u, 5 * u, gold)
    xSpark(w * 0.5f, h - 5 * u, 3.5f * u, Color(0xFFE1BEE7))
    xSpark(5 * u, h * 0.62f, 3f * u, Color(0xFFFFF59D))
}

// ===========================================================================
// 11) ماتريكس
// ===========================================================================
private fun DrawScope.innerMatrix(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val n = (w / (9 * u)).toInt()
    for (i in 0 until n) {
        val x = 4 * u + i * 9 * u
        drawLine(
            Color(0xFF00FF66).copy(alpha = 0.07f + 0.15f * xr(i + 5)),
            Offset(x, 0f), Offset(x, h), 2.4f * u,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf((2 + 3 * xr(i)) * u, (2 + 4 * xr(i + 40)) * u), xr(i + 80) * 20 * u)
        )
    }
}

private fun DrawScope.decorMatrix(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cy = h / 2
    val g = Color(0xFF00FF66)
    val k = 7 * u
    val t = 1.5f * u
    // أقواس الزوايا
    drawLine(g, Offset(0f, k), Offset(0f, 0f), t); drawLine(g, Offset(0f, 0f), Offset(k, 0f), t)
    drawLine(g, Offset(w - k, 0f), Offset(w, 0f), t); drawLine(g, Offset(w, 0f), Offset(w, k), t)
    drawLine(g, Offset(0f, h - k), Offset(0f, h), t); drawLine(g, Offset(0f, h), Offset(k, h), t)
    drawLine(g, Offset(w - k, h), Offset(w, h), t); drawLine(g, Offset(w, h), Offset(w, h - k), t)
    // موجّه الأوامر >_
    drawLine(g, Offset(8 * u, cy - 5 * u), Offset(15 * u, cy), 2f * u, StrokeCap.Round)
    drawLine(g, Offset(15 * u, cy), Offset(8 * u, cy + 5 * u), 2f * u, StrokeCap.Round)
    drawLine(g, Offset(18 * u, cy + 6 * u), Offset(26 * u, cy + 6 * u), 2f * u, StrokeCap.Round)
}

// ===========================================================================
// 12) ريترو (سينث ويف)
// ===========================================================================
private fun DrawScope.innerRetro(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val horizon = h * 0.62f
    val cyan = Color(0xFF00E5FF)
    for (i in 1..3) {
        val t = i / 3f
        val y = horizon + (h - horizon) * t * t
        drawLine(cyan.copy(alpha = 0.40f), Offset(0f, y), Offset(w, y), 1f * u)
    }
    for (k in -9..9) {
        drawLine(cyan.copy(alpha = 0.30f), Offset(w / 2 + k * 6 * u, horizon), Offset(w / 2 + k * 26 * u, h), 1f * u)
    }
    drawLine(cyan.copy(alpha = 0.55f), Offset(0f, horizon), Offset(w, horizon), 1.2f * u)
}

private fun DrawScope.decorRetro(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cy = h / 2
    val c = Offset(17 * u, cy)
    drawCircle(Brush.verticalGradient(listOf(Color(0xFFFFEB3B), Color(0xFFFF2E93)), startY = cy - 13 * u, endY = cy + 13 * u), 13 * u, c)
    val cut = Color(0xFF2B0A5E).copy(alpha = 0.85f)
    drawLine(cut, Offset(4 * u, cy + 3 * u), Offset(30 * u, cy + 3 * u), 1.2f * u)
    drawLine(cut, Offset(5 * u, cy + 7 * u), Offset(29 * u, cy + 7 * u), 1.8f * u)
    drawLine(cut, Offset(7 * u, cy + 11 * u), Offset(27 * u, cy + 11 * u), 2.4f * u)
    drawCircle(Color(0xFF00E5FF), 13 * u, c, style = Stroke(1.2f * u))
    xSpark(w - 9 * u, 8 * u, 5 * u, Color(0xFF00E5FF))
    xSpark(w * 0.55f, 5 * u, 3 * u, Color.White)
}

// ===========================================================================
// 13) دبدوب
// ===========================================================================
private fun DrawScope.innerTeddy(w: Float, h: Float) {
    val u = 1.dp.toPx()
    drawRoundRect(
        Color(0xFFFFF3E0).copy(alpha = 0.85f), Offset(4 * u, 4 * u), Size(w - 8 * u, h - 8 * u), CornerRadius(14 * u),
        style = Stroke(1.3f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4 * u, 3 * u)))
    )
}

private fun DrawScope.decorTeddy(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cy = h / 2
    val cx = 17 * u
    val fur = Color(0xFFB9763F)
    val dark = Color(0xFF6B3E1B)
    drawCircle(fur, 5 * u, Offset(cx - 10 * u, cy - 10 * u))
    drawCircle(fur, 5 * u, Offset(cx + 10 * u, cy - 10 * u))
    drawCircle(Color(0xFFF2B8A0), 2.6f * u, Offset(cx - 10 * u, cy - 10 * u))
    drawCircle(Color(0xFFF2B8A0), 2.6f * u, Offset(cx + 10 * u, cy - 10 * u))
    drawCircle(fur, 12.5f * u, Offset(cx, cy - 1 * u))
    drawCircle(dark, 12.5f * u, Offset(cx, cy - 1 * u), style = Stroke(1.2f * u))
    drawOval(Color(0xFFFFE0B2), Offset(cx - 6 * u, cy + 1 * u), Size(12 * u, 8.5f * u))
    drawCircle(Color(0xFF1B1B1B), 1.6f * u, Offset(cx - 5 * u, cy - 4 * u))
    drawCircle(Color(0xFF1B1B1B), 1.6f * u, Offset(cx + 5 * u, cy - 4 * u))
    drawOval(dark, Offset(cx - 2.4f * u, cy + 1.5f * u), Size(4.8f * u, 3.4f * u))
    drawArc(dark, 20f, 140f, false, Offset(cx - 3.5f * u, cy + 3.5f * u), Size(7 * u, 4.5f * u), style = Stroke(1f * u, cap = StrokeCap.Round))
    // ربطة
    val bow = Path().apply {
        moveTo(cx, cy + 13 * u); lineTo(cx - 8 * u, cy + 9 * u); lineTo(cx - 8 * u, cy + 17 * u); close()
        moveTo(cx, cy + 13 * u); lineTo(cx + 8 * u, cy + 9 * u); lineTo(cx + 8 * u, cy + 17 * u); close()
    }
    drawPath(bow, Color(0xFFE53935))
    drawCircle(Color(0xFFB71C1C), 2.2f * u, Offset(cx, cy + 13 * u))
    xHeart(w - 13 * u, 1 * u, 9 * u, Color(0xFFE57373))
}

// ===========================================================================
// 14) بطريق
// ===========================================================================
private fun DrawScope.innerPenguin(w: Float, h: Float) {
    val u = 1.dp.toPx()
    for (i in 0 until 16) {
        drawCircle(
            Color.White.copy(alpha = 0.25f + 0.5f * xr(i + 11)),
            (0.7f + 1.2f * xr(i + 61)) * u,
            Offset(w * xr(i + 2), h * xr(i + 27))
        )
    }
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF9ED8F5).copy(alpha = 0.22f)), startY = h * 0.6f, endY = h),
        size = Size(w, h)
    )
}

private fun DrawScope.decorPenguin(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cy = h / 2
    val cx = 17 * u
    val rim = Color(0xFF9ED8F5)
    drawOval(Color(0xFFFF9800), Offset(cx - 11 * u, cy + 11 * u), Size(8 * u, 4 * u))
    drawOval(Color(0xFFFF9800), Offset(cx + 3 * u, cy + 11 * u), Size(8 * u, 4 * u))
    drawOval(Color(0xFF0B1626), Offset(cx - 11 * u, cy - 14 * u), Size(22 * u, 27 * u))
    drawOval(rim, Offset(cx - 11 * u, cy - 14 * u), Size(22 * u, 27 * u), style = Stroke(1.1f * u))
    drawOval(Color.White, Offset(cx - 7 * u, cy - 3 * u), Size(14 * u, 15 * u))
    drawCircle(Color.White, 3 * u, Offset(cx - 3.5f * u, cy - 7 * u))
    drawCircle(Color.White, 3 * u, Offset(cx + 3.5f * u, cy - 7 * u))
    drawCircle(Color.Black, 1.2f * u, Offset(cx - 3 * u, cy - 7 * u))
    drawCircle(Color.Black, 1.2f * u, Offset(cx + 4 * u, cy - 7 * u))
    val beak = Path().apply { moveTo(cx - 2.5f * u, cy - 4 * u); lineTo(cx + 2.5f * u, cy - 4 * u); lineTo(cx, cy - 0.5f * u); close() }
    drawPath(beak, Color(0xFFFF9800))
    // وشاح
    drawRoundRect(Color(0xFFE53935), Offset(cx - 9 * u, cy - 2 * u), Size(18 * u, 3.2f * u), CornerRadius(1.6f * u))
    drawRoundRect(Color(0xFFE53935), Offset(cx + 3 * u, cy - 1 * u), Size(3.2f * u, 8 * u), CornerRadius(1.6f * u))
    xSpark(w - 9 * u, 8 * u, 4.5f * u, Color.White)
}

// ===========================================================================
// 15) نبض
// ===========================================================================
private fun DrawScope.innerPulse(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val y = h - 7 * u
    val p = Path().apply {
        moveTo(0f, y)
        for (x1 in listOf(w * 0.42f, w * 0.80f)) {
            lineTo(x1 - 14 * u, y); lineTo(x1 - 10 * u, y - 3 * u); lineTo(x1 - 7 * u, y)
            lineTo(x1 - 4 * u, y); lineTo(x1 - 2 * u, y + 4 * u); lineTo(x1 + 1 * u, y - 12 * u)
            lineTo(x1 + 4 * u, y + 5 * u); lineTo(x1 + 6 * u, y)
        }
        lineTo(w, y)
    }
    drawPath(p, Color(0xFFFF3B5C).copy(alpha = 0.22f), style = Stroke(6 * u, join = StrokeJoin.Round, cap = StrokeCap.Round))
    drawPath(p, Color(0xFFFF5C7A).copy(alpha = 0.75f), style = Stroke(1.5f * u, join = StrokeJoin.Round, cap = StrokeCap.Round))
}

private fun DrawScope.decorPulse(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cy = h / 2
    drawCircle(Color(0xFFFF3B5C).copy(alpha = 0.18f), 15 * u, Offset(17 * u, cy), style = Stroke(2 * u))
    drawCircle(Color(0xFFFF3B5C).copy(alpha = 0.32f), 12 * u, Offset(17 * u, cy), style = Stroke(1.5f * u))
    xHeart(5 * u, cy - 12 * u, 24 * u, Color(0xFFFF1744))
    xSpark(12 * u, cy - 5 * u, 2.6f * u, Color.White.copy(alpha = 0.85f))
    drawCircle(Color(0xFFFF3B5C), 2.2f * u, Offset(w - 7 * u, 7 * u))
}

// ===========================================================================
// 16) كوميك (حدود انفجار مسننة)
// ===========================================================================
private class BurstShape(private val spikeDp: Float = 3.6f, private val stepDp: Float = 10f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val d = spikeDp * density.density
        val step = stepDp * density.density
        val w = size.width
        val h = size.height
        var nx = max(2, (w / step).roundToInt()); if (nx % 2 == 1) nx++
        var ny = max(2, (h / step).roundToInt()); if (ny % 2 == 1) ny++
        val p = Path().apply {
            moveTo(0f, 0f)
            for (i in 1..nx) lineTo(w * i / nx, if (i % 2 == 0) 0f else d)
            for (j in 1..ny) lineTo(w - (if (j % 2 == 0) 0f else d), h * j / ny)
            for (i in 1..nx) lineTo(w - w * i / nx, h - (if (i % 2 == 0) 0f else d))
            for (j in 1..ny) lineTo(if (j % 2 == 0) 0f else d, h - h * j / ny)
            close()
        }
        return Outline.Generic(p)
    }
}

private fun DrawScope.innerComic(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val step = 6 * u
    var row = 0
    var y = 0f
    while (y < h + step) {
        var x = if (row % 2 == 0) 0f else step / 2
        while (x < w + step) {
            val fade = (x / w).coerceIn(0f, 1f)
            drawCircle(Color(0xFFFF1744).copy(alpha = 0.10f + 0.30f * fade), (0.8f + 1.5f * fade) * u, Offset(x, y))
            x += step
        }
        y += step * 0.75f
        row++
    }
}

private fun DrawScope.decorComic(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cy = h / 2
    val ink = Color(0xFF111111)
    drawRoundRect(Color(0xFFFF1744), Offset(10 * u, cy - 12 * u), Size(8 * u, 17 * u), CornerRadius(4 * u))
    drawRoundRect(ink, Offset(10 * u, cy - 12 * u), Size(8 * u, 17 * u), CornerRadius(4 * u), style = Stroke(1.6f * u))
    drawCircle(Color(0xFFFF1744), 4 * u, Offset(14 * u, cy + 11 * u))
    drawCircle(ink, 4 * u, Offset(14 * u, cy + 11 * u), style = Stroke(1.6f * u))
    // خطوط الحركة
    for (k in 0 until 3) {
        val a = (-35.0 + k * 35.0) * PI / 180.0
        drawLine(
            ink,
            Offset(w - 6 * u - (4 * u * cos(a)).toFloat(), 8 * u + (4 * u * sin(a)).toFloat()),
            Offset(w - 6 * u - (11 * u * cos(a)).toFloat(), 8 * u + (11 * u * sin(a)).toFloat()),
            1.6f * u, StrokeCap.Round
        )
    }
}

// ===========================================================================
// 17) جينز
// ===========================================================================
private fun DrawScope.innerDenim(w: Float, h: Float) {
    val u = 1.dp.toPx()
    var x = -h
    while (x < w) {
        drawLine(Color.White.copy(alpha = 0.06f), Offset(x, 0f), Offset(x + h, h), 1f * u)
        x += 3 * u
    }
    drawRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), endY = h * 0.5f),
        size = Size(w, h)
    )
}

private fun DrawScope.decorDenim(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cy = h / 2
    val thread = Color(0xFFFFB74D)
    // خياطة على الحافة
    drawRoundRect(
        thread, Offset(3 * u, 3 * u), Size(w - 6 * u, h - 6 * u), CornerRadius(10 * u),
        style = Stroke(1.2f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5 * u, 3 * u)))
    )
    // رقعة
    rotate(-7f, Offset(17 * u, cy)) {
        drawRoundRect(Color(0xFFFFCDD2), Offset(7 * u, cy - 10 * u), Size(20 * u, 20 * u), CornerRadius(3 * u))
        drawRoundRect(
            Color(0xFFD84315), Offset(8.7f * u, cy - 8.3f * u), Size(16.6f * u, 16.6f * u), CornerRadius(2 * u),
            style = Stroke(1f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5f * u, 2 * u)))
        )
        xHeart(10.5f * u, cy - 7 * u, 13 * u, Color(0xFFE53935))
    }
    // برشام
    drawCircle(Color(0xFFB87333), 3.4f * u, Offset(w - 8 * u, 8 * u))
    drawCircle(Color(0xFFF0B27A), 1.3f * u, Offset(w - 9 * u, 7 * u))
}

// ===========================================================================
// 18) شوكولاتة
// ===========================================================================
private fun DrawScope.innerChoco(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cell = 26 * u
    val nx = max(1, (w / cell).roundToInt())
    val ny = max(1, (h / cell).roundToInt())
    val cw = w / nx
    val ch = h / ny
    for (i in 0 until nx) {
        for (j in 0 until ny) {
            val x = i * cw
            val y = j * ch
            drawRoundRect(Color.White.copy(alpha = 0.06f), Offset(x + 2 * u, y + 2 * u), Size(cw - 4 * u, ch - 4 * u), CornerRadius(3 * u))
            drawRoundRect(
                Color(0xFF1E0F05).copy(alpha = 0.55f), Offset(x + 1 * u, y + 1 * u), Size(cw - 2 * u, ch - 2 * u), CornerRadius(3 * u),
                style = Stroke(1.6f * u)
            )
        }
    }
}

private fun DrawScope.decorChoco(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cy = h / 2
    // حبة كاكاو
    rotate(-25f, Offset(15 * u, cy)) {
        drawOval(Color(0xFF8D5524), Offset(5 * u, cy - 9 * u), Size(20 * u, 18 * u))
        drawOval(Color(0xFF4E2A0D), Offset(5 * u, cy - 9 * u), Size(20 * u, 18 * u), style = Stroke(1.2f * u))
        drawLine(Color(0xFF4E2A0D), Offset(7 * u, cy), Offset(23 * u, cy), 1.3f * u, StrokeCap.Round)
        drawOval(Color.White.copy(alpha = 0.3f), Offset(8 * u, cy - 7 * u), Size(6 * u, 3 * u))
    }
    // ورق ألمنيوم ذهبي عند الزاوية
    val c = 14 * u
    val flap = Path().apply { moveTo(w - c, h); lineTo(w, h - c); lineTo(w - c, h - c); close() }
    drawPath(flap, Brush.linearGradient(listOf(Color(0xFFFFD54F), Color(0xFFFFF8E1)), Offset(w, h - c), Offset(w - c, h)))
    drawPath(flap, Color(0xFF8D6E00).copy(alpha = 0.6f), style = Stroke(1f * u, join = StrokeJoin.Round))
    xSpark(w - 9 * u, 7 * u, 3.5f * u, Color(0xFFFFE0B2))
}

// ===========================================================================
// 19) شمس
// ===========================================================================
private fun DrawScope.innerSun(w: Float, h: Float) {
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent), center = Offset(w * 0.85f, h), radius = w * 0.5f),
        w * 0.5f, Offset(w * 0.85f, h)
    )
}

private fun DrawScope.decorSun(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cy = h / 2
    val c = Offset(19 * u, cy)
    val orange = Color(0xFFE65100)
    for (k in 0 until 12) {
        val a = k * PI / 6
        drawLine(
            orange,
            Offset(c.x + (14 * u * cos(a)).toFloat(), c.y + (14 * u * sin(a)).toFloat()),
            Offset(c.x + (18.5f * u * cos(a)).toFloat(), c.y + (18.5f * u * sin(a)).toFloat()),
            2.2f * u, StrokeCap.Round
        )
    }
    drawCircle(Color(0xFFFFEB3B), 12.5f * u, c)
    drawCircle(orange, 12.5f * u, c, style = Stroke(1.5f * u))
    // نظارة
    drawRoundRect(Color(0xFF212121), Offset(c.x - 10 * u, c.y - 5 * u), Size(9 * u, 6.5f * u), CornerRadius(2.5f * u))
    drawRoundRect(Color(0xFF212121), Offset(c.x + 1 * u, c.y - 5 * u), Size(9 * u, 6.5f * u), CornerRadius(2.5f * u))
    drawLine(Color(0xFF212121), Offset(c.x - 1.5f * u, c.y - 3.5f * u), Offset(c.x + 1.5f * u, c.y - 3.5f * u), 1.3f * u)
    drawLine(Color.White.copy(alpha = 0.5f), Offset(c.x - 8.5f * u, c.y - 3.8f * u), Offset(c.x - 6 * u, c.y - 3.8f * u), 1f * u, StrokeCap.Round)
    drawArc(orange, 15f, 150f, false, Offset(c.x - 5 * u, c.y + 1 * u), Size(10 * u, 7 * u), style = Stroke(1.4f * u, cap = StrokeCap.Round))
    drawCircle(Color(0xFFFF8A65).copy(alpha = 0.6f), 2 * u, Offset(c.x - 8 * u, c.y + 4 * u))
    drawCircle(Color(0xFFFF8A65).copy(alpha = 0.6f), 2 * u, Offset(c.x + 8 * u, c.y + 4 * u))
}

// ===========================================================================
// 20) هلال (إسلامي)
// ===========================================================================
private fun DrawScope.innerHilal(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val gold = Color(0xFFFFC93C).copy(alpha = 0.13f)
    val s = 22 * u
    var gx = 0f
    while (gx < w + s) {
        var gy = 0f
        while (gy < h + s) {
            val c = Offset(gx, gy)
            drawRect(gold, Offset(c.x - 5 * u, c.y - 5 * u), Size(10 * u, 10 * u), style = Stroke(0.9f * u))
            rotate(45f, c) {
                drawRect(gold, Offset(c.x - 5 * u, c.y - 5 * u), Size(10 * u, 10 * u), style = Stroke(0.9f * u))
            }
            gy += s
        }
        gx += s
    }
}

private fun DrawScope.decorHilal(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cy = h / 2
    val gold = Color(0xFFFFC93C)
    // هلال
    val cx = 14 * u
    val r = 11 * u
    val a1 = -60.0 * PI / 180.0
    val a2 = 60.0 * PI / 180.0
    val p1 = Offset(cx + (r * cos(a1)).toFloat(), cy + (r * sin(a1)).toFloat())
    val p2 = Offset(cx + (r * cos(a2)).toFloat(), cy + (r * sin(a2)).toFloat())
    val moon = Path().apply {
        moveTo(p1.x, p1.y)
        arcTo(Rect(cx - r, cy - r, cx + r, cy + r), -60f, -240f, false)
        quadraticTo(cx - 1.4f * r, cy, p1.x, p1.y)
        close()
    }
    drawPath(moon, Brush.verticalGradient(listOf(Color(0xFFFFF3B0), gold), startY = cy - r, endY = cy + r))
    xStar5(27 * u, cy - 5 * u, 4.2f * u, gold)
    // فانوس رمضان
    val lx = w - 18 * u
    drawLine(gold, Offset(lx + 5 * u, -16 * u), Offset(lx + 5 * u, -11 * u), 1f * u)
    drawRoundRect(gold, Offset(lx + 1.5f * u, -11 * u), Size(7 * u, 2.5f * u), CornerRadius(1 * u))
    drawRoundRect(Color(0xFFFF8F00), Offset(lx, -8.5f * u), Size(10 * u, 11 * u), CornerRadius(3 * u))
    drawRoundRect(Color(0xFFFFF59D), Offset(lx + 2.5f * u, -6 * u), Size(5 * u, 6.5f * u), CornerRadius(2 * u))
    drawRoundRect(gold, Offset(lx + 1.5f * u, 2.5f * u), Size(7 * u, 2 * u), CornerRadius(1 * u))
    xSpark(w * 0.5f, h - 5 * u, 3 * u, Color(0xFFFFF3B0))
}

// ===========================================================================
// 21) فراولة
// ===========================================================================
private fun DrawScope.innerStrawberry(w: Float, h: Float) {
    val u = 1.dp.toPx()
    for (i in 0 until 16) {
        val x = w * xr(i + 200)
        val y = h * xr(i + 230)
        rotate(-20f + 40f * xr(i + 260), Offset(x, y)) {
            drawOval(Color(0xFFE53956).copy(alpha = 0.22f), Offset(x - 1.2f * u, y - 2f * u), Size(2.4f * u, 4f * u))
        }
    }
}

private fun DrawScope.decorStrawberry(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cy = h / 2
    // الثمرة
    val body = Path().apply {
        moveTo(17 * u, cy + 13 * u)
        cubicTo(4 * u, cy + 6 * u, 3 * u, cy - 6 * u, 10 * u, cy - 8 * u)
        cubicTo(14 * u, cy - 9.5f * u, 20 * u, cy - 9.5f * u, 24 * u, cy - 8 * u)
        cubicTo(31 * u, cy - 6 * u, 30 * u, cy + 6 * u, 17 * u, cy + 13 * u)
        close()
    }
    drawPath(body, Brush.verticalGradient(listOf(Color(0xFFFF5A6E), Color(0xFFD81B45)), startY = cy - 9 * u, endY = cy + 13 * u))
    drawPath(body, Color(0xFF9E1030), style = Stroke(1f * u, join = StrokeJoin.Round))
    // البذور
    listOf(
        Offset(11 * u, cy - 3 * u), Offset(17 * u, cy - 5 * u), Offset(23 * u, cy - 3 * u),
        Offset(13 * u, cy + 2 * u), Offset(20 * u, cy + 2 * u), Offset(17 * u, cy + 7 * u)
    ).forEach { drawOval(Color(0xFFFFF176), Offset(it.x - 1 * u, it.y - 1.6f * u), Size(2 * u, 3.2f * u)) }
    // الأوراق
    val leaf = Path().apply {
        moveTo(17 * u, cy - 8 * u)
        lineTo(9 * u, cy - 12 * u); lineTo(13 * u, cy - 8.5f * u)
        lineTo(10 * u, cy - 6 * u); lineTo(15 * u, cy - 7.5f * u)
        lineTo(17 * u, cy - 4 * u); lineTo(19 * u, cy - 7.5f * u)
        lineTo(24 * u, cy - 6 * u); lineTo(21 * u, cy - 8.5f * u)
        lineTo(25 * u, cy - 12 * u)
        close()
    }
    drawPath(leaf, Color(0xFF43A047))
    drawLine(Color(0xFF2E7D32), Offset(17 * u, cy - 8 * u), Offset(17 * u, cy - 16 * u), 1.8f * u, StrokeCap.Round)
    // لمعة
    drawOval(Color.White.copy(alpha = 0.45f), Offset(8 * u, cy - 5 * u), Size(3 * u, 6 * u))
    xHeart(w - 13 * u, 1 * u, 9 * u, Color(0xFFFF8FA3))
}

// ===========================================================================
// 22) صاروخ
// ===========================================================================
private fun DrawScope.innerRocket(w: Float, h: Float) {
    val u = 1.dp.toPx()
    for (i in 0 until 22) {
        drawCircle(
            Color.White.copy(alpha = 0.25f + 0.55f * xr(i + 300)),
            (0.4f + 0.9f * xr(i + 330)) * u,
            Offset(w * xr(i + 360), h * xr(i + 390))
        )
    }
    xSpark(w * 0.7f, h * 0.28f, 3 * u, Color(0xFFFFE082))
}

private fun DrawScope.decorRocket(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cy = h / 2
    rotate(-40f, Offset(17 * u, cy)) {
        // اللهب
        val flame = Path().apply {
            moveTo(13 * u, cy + 10 * u); lineTo(17 * u, cy + 21 * u); lineTo(21 * u, cy + 10 * u); close()
        }
        drawPath(flame, Brush.verticalGradient(listOf(Color(0xFFFFEB3B), Color(0xFFFF5722)), startY = cy + 10 * u, endY = cy + 21 * u))
        // الجناحان
        val wingL = Path().apply { moveTo(12 * u, cy + 1 * u); lineTo(5 * u, cy + 12 * u); lineTo(12 * u, cy + 10 * u); close() }
        val wingR = Path().apply { moveTo(22 * u, cy + 1 * u); lineTo(29 * u, cy + 12 * u); lineTo(22 * u, cy + 10 * u); close() }
        drawPath(wingL, Color(0xFFE53935)); drawPath(wingR, Color(0xFFE53935))
        // الجسم
        val body = Path().apply {
            moveTo(17 * u, cy - 15 * u)
            cubicTo(26 * u, cy - 8 * u, 24 * u, cy + 4 * u, 22 * u, cy + 11 * u)
            lineTo(12 * u, cy + 11 * u)
            cubicTo(10 * u, cy + 4 * u, 8 * u, cy - 8 * u, 17 * u, cy - 15 * u)
            close()
        }
        drawPath(body, Brush.horizontalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFCFD8DC)), startX = 10 * u, endX = 24 * u))
        drawPath(body, Color(0xFF455A64), style = Stroke(1f * u, join = StrokeJoin.Round))
        // المقدمة والنافذة
        drawArc(Color(0xFFE53935), -125f, 70f, true, Offset(10 * u, cy - 15 * u), Size(14 * u, 14 * u))
        drawCircle(Color(0xFF4FC3F7), 3.4f * u, Offset(17 * u, cy - 2 * u))
        drawCircle(Color(0xFF37474F), 3.4f * u, Offset(17 * u, cy - 2 * u), style = Stroke(1.1f * u))
        drawCircle(Color.White.copy(alpha = 0.7f), 1f * u, Offset(16 * u, cy - 3 * u))
    }
    xStar5(w - 9 * u, 8 * u, 4 * u, Color(0xFFFFE082))
    xSpark(w * 0.55f, h - 6 * u, 3 * u, Color.White)
}

// ===========================================================================
// 23) بومة
// ===========================================================================
private fun DrawScope.innerOwl(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val c = Color.White.copy(alpha = 0.07f)
    var y = 0f
    var row = 0
    while (y < h + 6 * u) {
        var x = if (row % 2 == 0) 0f else 4 * u
        while (x < w + 8 * u) {
            drawArc(c, 0f, 180f, false, Offset(x - 4 * u, y - 4 * u), Size(8 * u, 8 * u), style = Stroke(1f * u))
            x += 8 * u
        }
        y += 4 * u
        row++
    }
}

private fun DrawScope.decorOwl(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cy = h / 2
    val brown = Color(0xFF6D4C41)
    // أذنان
    val earL = Path().apply { moveTo(6 * u, cy - 8 * u); lineTo(8 * u, cy - 17 * u); lineTo(14 * u, cy - 10 * u); close() }
    val earR = Path().apply { moveTo(28 * u, cy - 8 * u); lineTo(26 * u, cy - 17 * u); lineTo(20 * u, cy - 10 * u); close() }
    drawPath(earL, brown); drawPath(earR, brown)
    // الوجه
    drawOval(Color(0xFF8D6E63), Offset(4 * u, cy - 12 * u), Size(26 * u, 24 * u))
    // العينان
    drawCircle(Color(0xFFFFF3E0), 6.5f * u, Offset(11 * u, cy - 3 * u))
    drawCircle(Color(0xFFFFF3E0), 6.5f * u, Offset(23 * u, cy - 3 * u))
    drawCircle(Color(0xFF3E2723), 6.5f * u, Offset(11 * u, cy - 3 * u), style = Stroke(1.2f * u))
    drawCircle(Color(0xFF3E2723), 6.5f * u, Offset(23 * u, cy - 3 * u), style = Stroke(1.2f * u))
    drawCircle(Color(0xFFFFB300), 3.6f * u, Offset(11 * u, cy - 3 * u))
    drawCircle(Color(0xFFFFB300), 3.6f * u, Offset(23 * u, cy - 3 * u))
    drawCircle(Color.Black, 2f * u, Offset(11 * u, cy - 3 * u))
    drawCircle(Color.Black, 2f * u, Offset(23 * u, cy - 3 * u))
    drawCircle(Color.White, 0.8f * u, Offset(10.2f * u, cy - 3.8f * u))
    drawCircle(Color.White, 0.8f * u, Offset(22.2f * u, cy - 3.8f * u))
    // المنقار
    val beak = Path().apply { moveTo(14.5f * u, cy + 1 * u); lineTo(19.5f * u, cy + 1 * u); lineTo(17 * u, cy + 6.5f * u); close() }
    drawPath(beak, Color(0xFFFF9800))
    xStar5(w - 8 * u, 8 * u, 3.5f * u, Color(0xFFFFE082))
}

// ===========================================================================
// 24) بوظة
// ===========================================================================
private fun DrawScope.innerIceCream(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cols = listOf(Color(0xFFFF5A8A), Color(0xFF42A5F5), Color(0xFFFFB300), Color(0xFF66BB6A), Color(0xFFAB47BC))
    for (i in 0 until 20) {
        val x = w * xr(i + 400)
        val y = h * xr(i + 430)
        rotate(180f * xr(i + 460), Offset(x, y)) {
            drawLine(cols[i % cols.size].copy(alpha = 0.55f), Offset(x - 2 * u, y), Offset(x + 2 * u, y), 1.5f * u, StrokeCap.Round)
        }
    }
}

private fun DrawScope.decorIceCream(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cy = h / 2
    // الكوب (قمع الوافل)
    val cone = Path().apply { moveTo(9 * u, cy + 1 * u); lineTo(25 * u, cy + 1 * u); lineTo(17 * u, cy + 17 * u); close() }
    drawPath(cone, Color(0xFFE0A85C))
    drawPath(cone, Color(0xFF9C6B2E), style = Stroke(1f * u, join = StrokeJoin.Round))
    drawLine(Color(0xFF9C6B2E), Offset(12 * u, cy + 3 * u), Offset(19 * u, cy + 12 * u), 0.9f * u)
    drawLine(Color(0xFF9C6B2E), Offset(22 * u, cy + 3 * u), Offset(15 * u, cy + 12 * u), 0.9f * u)
    // الكرتان
    drawCircle(Color(0xFFFF8FB1), 8.5f * u, Offset(17 * u, cy - 1 * u))
    drawCircle(Color(0xFF8FE3C0), 7.5f * u, Offset(17 * u, cy - 9 * u))
    drawArc(Color(0xFFFF8FB1), 0f, 180f, true, Offset(8 * u, cy - 3 * u), Size(18 * u, 8 * u))
    drawOval(Color.White.copy(alpha = 0.5f), Offset(12 * u, cy - 13 * u), Size(3 * u, 4 * u))
    // الكرز
    drawCircle(Color(0xFFD32F2F), 3 * u, Offset(17 * u, cy - 18 * u))
    drawLine(Color(0xFF558B2F), Offset(17 * u, cy - 20 * u), Offset(20 * u, cy - 24 * u), 1.1f * u, StrokeCap.Round)
    drawCircle(Color.White.copy(alpha = 0.7f), 0.9f * u, Offset(16 * u, cy - 19 * u))
    xHeart(w - 13 * u, 1 * u, 9 * u, Color(0xFFFF8FB1))
}

// ===========================================================================
// 25) قهوة
// ===========================================================================
private fun DrawScope.innerCoffee(w: Float, h: Float) {
    val u = 1.dp.toPx()
    for (i in 0 until 3) {
        val bx = w * (0.35f + 0.25f * i)
        val p = Path().apply {
            moveTo(bx, h)
            var y = h
            while (y > 0f) {
                y -= 3 * u
                lineTo(bx + 4 * u * sin(y / (6 * u) + i), y)
            }
        }
        drawPath(p, Color.White.copy(alpha = 0.07f), style = Stroke(3 * u, cap = StrokeCap.Round))
    }
    for (i in 0 until 5) {
        val x = w * xr(i + 500)
        val y = h * xr(i + 520)
        rotate(60f * i, Offset(x, y)) {
            drawOval(Color.Black.copy(alpha = 0.18f), Offset(x - 3 * u, y - 4 * u), Size(6 * u, 8 * u))
            drawLine(Color.White.copy(alpha = 0.10f), Offset(x, y - 3 * u), Offset(x, y + 3 * u), 0.9f * u)
        }
    }
}

private fun DrawScope.decorCoffee(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cy = h / 2
    // البخار
    for (i in 0 until 2) {
        val bx = 12 * u + i * 7 * u
        val p = Path().apply {
            moveTo(bx, cy - 4 * u)
            quadraticTo(bx - 3 * u, cy - 9 * u, bx, cy - 13 * u)
            quadraticTo(bx + 3 * u, cy - 17 * u, bx, cy - 21 * u)
        }
        drawPath(p, Color.White.copy(alpha = 0.7f), style = Stroke(1.5f * u, cap = StrokeCap.Round))
    }
    // الصحن
    drawOval(Color(0xFFF5E6D3), Offset(4 * u, cy + 11 * u), Size(26 * u, 6 * u))
    // الكوب
    val cup = Path().apply {
        moveTo(7 * u, cy - 3 * u); lineTo(27 * u, cy - 3 * u)
        lineTo(25 * u, cy + 11 * u)
        quadraticTo(17 * u, cy + 15 * u, 9 * u, cy + 11 * u)
        close()
    }
    drawPath(cup, Color(0xFFFFF8F0))
    drawPath(cup, Color(0xFF6D4C41), style = Stroke(1.2f * u, join = StrokeJoin.Round))
    drawOval(Color(0xFF4E342E), Offset(7 * u, cy - 5 * u), Size(20 * u, 4.5f * u))
    // المقبض
    drawArc(Color(0xFF6D4C41), -80f, 160f, false, Offset(22 * u, cy - 1 * u), Size(10 * u, 9 * u), style = Stroke(1.8f * u, cap = StrokeCap.Round))
    // قلب على الكوب
    xHeart(13 * u, cy + 1.5f * u, 8 * u, Color(0xFFE57373))
    xSpark(w - 9 * u, 8 * u, 4 * u, Color(0xFFFFE0B2))
}

// ---------------------------------------------------------------------------
// قائمة الأشكال الجديدة (25) - تُضاف بآخر قائمة BubbleStyles
// ---------------------------------------------------------------------------

internal val ExtraBubbleStyles: List<BubbleStyle> = listOf(
    BubbleStyle(
        id = "aurora", name = "شفق قطبي", shape = RoundedCornerShape(24.dp),
        fill = listOf(Color(0xFF0B1F3A), Color(0xFF1B3A5C)), text = Color(0xFFE8FFF5),
        border = 1.5.dp, borderColor = Color(0xFF39FFA0), sidePad = 36.dp,
        decor = DrawScope::decorAurora, inner = DrawScope::innerAurora
    ),
    BubbleStyle(
        id = "cactus", name = "صبار", shape = RoundedCornerShape(20.dp),
        fill = listOf(Color(0xFFD6F2B8), Color(0xFFB2DE8A)), text = Color(0xFF1E4620),
        border = 2.dp, borderColor = Color(0xFF5E9B3C), sidePad = 34.dp, topPad = 14.dp,
        decor = DrawScope::decorCactus
    ),
    BubbleStyle(
        id = "mushroom", name = "فطر", shape = RoundedCornerShape(30.dp, 30.dp, 8.dp, 8.dp),
        fill = listOf(Color(0xFFE53935), Color(0xFFB71C1C)), text = Color.White,
        border = 1.5.dp, borderColor = Color(0xFF7F1010), sidePad = 32.dp, topPad = 4.dp,
        decor = DrawScope::decorMushroom, inner = DrawScope::innerMushroom
    ),
    BubbleStyle(
        id = "panda", name = "باندا", shape = RoundedCornerShape(26.dp),
        fill = listOf(Color.White, Color(0xFFF1F1F1)), text = Color(0xFF111111),
        border = 2.5.dp, borderColor = Color(0xFF111111), sidePad = 36.dp, topPad = 10.dp,
        decor = DrawScope::decorPanda
    ),
    BubbleStyle(
        id = "bee", name = "نحلة", shape = RoundedCornerShape(22.dp),
        fill = listOf(Color(0xFFFFE066), Color(0xFFFFC107)), text = Color(0xFF3A2A00),
        border = 2.dp, borderColor = Color(0xFF2B1F00), sidePad = 38.dp, topPad = 14.dp,
        decor = DrawScope::decorBee, inner = DrawScope::innerBee
    ),
    BubbleStyle(
        id = "rainbow", name = "قوس قزح", shape = RoundedCornerShape(24.dp),
        fill = listOf(Color(0xFFDFF3FF), Color(0xFFF4FBFF)), text = Color(0xFF1F3B57),
        sidePad = 38.dp, topPad = 4.dp,
        decor = DrawScope::decorRainbow, inner = DrawScope::innerRainbow
    ),
    BubbleStyle(
        id = "diamond", name = "ألماس", shape = CutCornerShape(14.dp),
        fill = listOf(Color(0xFF4FC3F7), Color(0xFF1A73E8), Color(0xFF7C4DFF)), text = Color.White,
        border = 1.5.dp, borderColor = Color(0xFFE1F5FE), sidePad = 34.dp,
        decor = DrawScope::decorDiamond, inner = DrawScope::innerDiamond
    ),
    BubbleStyle(
        id = "watermelon", name = "بطيخ", shape = RoundedCornerShape(24.dp),
        fill = listOf(Color(0xFFFF5A6E), Color(0xFFE53956)), text = Color.White,
        border = 3.dp, borderColor = Color(0xFF2E9E4F), sidePad = 34.dp, topPad = 4.dp,
        decor = DrawScope::decorWatermelon, inner = DrawScope::innerWatermelon
    ),
    BubbleStyle(
        id = "skull", name = "جمجمة", shape = CutCornerShape(12.dp),
        fill = listOf(Color(0xFF1A1A1F), Color(0xFF2D2D35)), text = Color(0xFFF2F2F2),
        border = 1.5.dp, borderColor = Color(0xFFBDBDBD), sidePad = 34.dp,
        decor = DrawScope::decorSkull, inner = DrawScope::innerSkull
    ),
    BubbleStyle(
        id = "magic", name = "سحر", shape = RoundedCornerShape(22.dp),
        fill = listOf(Color(0xFF4A148C), Color(0xFF1A0B4D)), text = Color(0xFFF3E5FF),
        border = 1.5.dp, borderColor = Color(0xFFB388FF), topPad = 20.dp,
        decor = DrawScope::decorMagic, inner = DrawScope::innerMagic
    ),
    BubbleStyle(
        id = "matrix", name = "ماتريكس", shape = CutCornerShape(8.dp),
        fill = listOf(Color(0xFF020B05), Color(0xFF04210F)), text = Color(0xFF00FF66),
        border = 1.dp, borderColor = Color(0xFF00A845), sidePad = 32.dp,
        decor = DrawScope::decorMatrix, inner = DrawScope::innerMatrix
    ),
    BubbleStyle(
        id = "retro", name = "ريترو", shape = RoundedCornerShape(10.dp),
        fill = listOf(Color(0xFF2B0A5E), Color(0xFFFF2E93), Color(0xFFFF9A3C)), text = Color.White,
        border = 1.5.dp, borderColor = Color(0xFF00E5FF), sidePad = 36.dp,
        decor = DrawScope::decorRetro, inner = DrawScope::innerRetro
    ),
    BubbleStyle(
        id = "teddy", name = "دبدوب", shape = RoundedCornerShape(24.dp),
        fill = listOf(Color(0xFFE8B98A), Color(0xFFD79B6B)), text = Color(0xFF4A2A10),
        border = 2.dp, borderColor = Color(0xFF8B5A2B), sidePad = 38.dp, topPad = 10.dp,
        decor = DrawScope::decorTeddy, inner = DrawScope::innerTeddy
    ),
    BubbleStyle(
        id = "penguin", name = "بطريق", shape = RoundedCornerShape(22.dp),
        fill = listOf(Color(0xFF1F3A5F), Color(0xFF0E2038)), text = Color(0xFFEAF6FF),
        border = 2.dp, borderColor = Color(0xFF9ED8F5), sidePad = 34.dp,
        decor = DrawScope::decorPenguin, inner = DrawScope::innerPenguin
    ),
    BubbleStyle(
        id = "pulse", name = "نبض", shape = RoundedCornerShape(18.dp),
        fill = listOf(Color(0xFF1A0508), Color(0xFF3B0A12)), text = Color(0xFFFFE9EC),
        border = 1.5.dp, borderColor = Color(0xFFFF3B5C), sidePad = 34.dp,
        decor = DrawScope::decorPulse, inner = DrawScope::innerPulse
    ),
    BubbleStyle(
        id = "comic", name = "كوميك", shape = BurstShape(),
        fill = listOf(Color(0xFFFFEB3B), Color(0xFFFFC400)), text = Color(0xFF111111),
        border = 2.5.dp, borderColor = Color(0xFF111111), sidePad = 28.dp, topPad = 4.dp,
        decor = DrawScope::decorComic, inner = DrawScope::innerComic
    ),
    BubbleStyle(
        id = "denim", name = "جينز", shape = RoundedCornerShape(12.dp),
        fill = listOf(Color(0xFF3F6FB5), Color(0xFF2F5597)), text = Color.White,
        sidePad = 34.dp,
        decor = DrawScope::decorDenim, inner = DrawScope::innerDenim
    ),
    BubbleStyle(
        id = "choco", name = "شوكولاتة", shape = RoundedCornerShape(8.dp),
        fill = listOf(Color(0xFF5D3A1A), Color(0xFF3E2410)), text = Color(0xFFFFE9D0),
        border = 1.5.dp, borderColor = Color(0xFF2B1608), sidePad = 30.dp,
        decor = DrawScope::decorChoco, inner = DrawScope::innerChoco
    ),
    BubbleStyle(
        id = "sun", name = "شمس", shape = RoundedCornerShape(24.dp),
        fill = listOf(Color(0xFFFFD54F), Color(0xFFFF9800)), text = Color(0xFF4A2300),
        border = 2.dp, borderColor = Color(0xFFE65100), sidePad = 40.dp, topPad = 6.dp,
        decor = DrawScope::decorSun, inner = DrawScope::innerSun
    ),
    BubbleStyle(
        id = "hilal", name = "هلال", shape = RoundedCornerShape(22.dp),
        fill = listOf(Color(0xFF0B3D2E), Color(0xFF145A44)), text = Color(0xFFFFF4CC),
        border = 1.5.dp, borderColor = Color(0xFFFFC93C), sidePad = 34.dp, topPad = 16.dp,
        decor = DrawScope::decorHilal, inner = DrawScope::innerHilal
    ),
    BubbleStyle(
        id = "strawberry", name = "فراولة", shape = RoundedCornerShape(24.dp),
        fill = listOf(Color(0xFFFFE0E6), Color(0xFFFFC2CF)), text = Color(0xFF7A1530),
        border = 2.dp, borderColor = Color(0xFFE53956), sidePad = 36.dp, topPad = 12.dp,
        decor = DrawScope::decorStrawberry, inner = DrawScope::innerStrawberry
    ),
    BubbleStyle(
        id = "rocket", name = "صاروخ", shape = RoundedCornerShape(22.dp),
        fill = listOf(Color(0xFF0D1B3E), Color(0xFF2A1A5E)), text = Color(0xFFEAF2FF),
        border = 1.5.dp, borderColor = Color(0xFF7C9CFF), sidePad = 38.dp, topPad = 4.dp,
        decor = DrawScope::decorRocket, inner = DrawScope::innerRocket
    ),
    BubbleStyle(
        id = "owl", name = "بومة", shape = RoundedCornerShape(24.dp),
        fill = listOf(Color(0xFF5D4037), Color(0xFF3E2723)), text = Color(0xFFFFF3E0),
        border = 2.dp, borderColor = Color(0xFFBCAAA4), sidePad = 38.dp, topPad = 10.dp,
        decor = DrawScope::decorOwl, inner = DrawScope::innerOwl
    ),
    BubbleStyle(
        id = "icecream", name = "بوظة", shape = RoundedCornerShape(26.dp),
        fill = listOf(Color(0xFFFFF1F5), Color(0xFFFFE0EC)), text = Color(0xFF5A2A3C),
        border = 2.dp, borderColor = Color(0xFFFF8FB1), sidePad = 36.dp, topPad = 12.dp,
        decor = DrawScope::decorIceCream, inner = DrawScope::innerIceCream
    ),
    BubbleStyle(
        id = "coffee", name = "قهوة", shape = RoundedCornerShape(20.dp),
        fill = listOf(Color(0xFF6D4C41), Color(0xFF3E2723)), text = Color(0xFFFFEFD9),
        border = 1.5.dp, borderColor = Color(0xFFD7B899), sidePad = 36.dp, topPad = 8.dp,
        decor = DrawScope::decorCoffee, inner = DrawScope::innerCoffee
    )
)
