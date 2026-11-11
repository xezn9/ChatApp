package com.chatapp.messenger.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * أشكال محرر "صنع فقاعة" (كلها جديدة وما مكررة من فقاعات التطبيق الأصلية).
 * كل شكل يُرسم كأن الشخصية على الجهة اليسرى، والنظام يعكسه حسب جهة الرسالة.
 * عند الدمج: الشكل الأول يُرسم بالجهة الأساسية والثاني معكوس بالجهة الثانية.
 * أشكال الأعلى مثبّتة عند 62% من العرض حتى ما تتراكب مع الشكل المدموج (ينعكس لـ 38%).
 * [draw] تستلم لون الشكل [accent] (الأشكال ذات الألوان الثابتة تتجاهله).
 * ⚠️ الـ id يُحفظ داخل ملف الفقاعة، لا تغيّره بعد النشر.
 */
class BubbleDecor(
    val id: String,
    val name: String,
    val sidePad: Dp = 0.dp,
    val topPad: Dp = 0.dp,
    /** هل لون الشكل قابل للتغيير من المحرر؟ */
    val tintable: Boolean = false,
    val draw: (DrawScope.(Float, Float, Color) -> Unit)? = null
)

private fun DrawScope.poly(color: Color, pts: List<Offset>) {
    val p = Path()
    p.moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) p.lineTo(pts[i].x, pts[i].y)
    p.close()
    drawPath(p, color)
}

private fun DrawScope.spark(cx: Float, cy: Float, r: Float, color: Color) {
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

// ---------------------------- شخصيات (جهة الشخصية) ----------------------------

private fun DrawScope.shPanda(w: Float, h: Float, c: Color) {
    val u = 1.dp.toPx(); val cy = h / 2
    val ink = Color(0xFF222222)
    drawCircle(ink, 5 * u, Offset(7 * u, cy - 10 * u))
    drawCircle(ink, 5 * u, Offset(27 * u, cy - 10 * u))
    drawCircle(Color.White, 13 * u, Offset(17 * u, cy))
    drawCircle(Color(0xFFD5D5D5), 13 * u, Offset(17 * u, cy), style = Stroke(1 * u))
    drawOval(ink, Offset(8.5f * u, cy - 6 * u), Size(5.5f * u, 7.5f * u))
    drawOval(ink, Offset(20 * u, cy - 6 * u), Size(5.5f * u, 7.5f * u))
    drawCircle(Color.White, 1.3f * u, Offset(11.2f * u, cy - 3 * u))
    drawCircle(Color.White, 1.3f * u, Offset(22.8f * u, cy - 3 * u))
    drawOval(ink, Offset(14.5f * u, cy + 3 * u), Size(5 * u, 3.5f * u))
    drawArc(ink, 20f, 140f, false, Offset(13 * u, cy + 4 * u), Size(8 * u, 6 * u), style = Stroke(1.2f * u, cap = StrokeCap.Round))
}

private fun DrawScope.shFox(w: Float, h: Float, c: Color) {
    val u = 1.dp.toPx(); val cy = h / 2
    val orange = Color(0xFFFF8A3D); val dark = Color(0xFF4A2410)
    poly(orange, listOf(Offset(3 * u, cy - 17 * u), Offset(12 * u, cy - 6 * u), Offset(23 * u, cy - 6 * u), Offset(32 * u, cy - 17 * u), Offset(33 * u, cy + 3 * u), Offset(17.5f * u, cy + 14 * u), Offset(2 * u, cy + 3 * u)))
    poly(Color(0xFFFFC9A0), listOf(Offset(6 * u, cy - 13 * u), Offset(11 * u, cy - 7 * u), Offset(6 * u, cy - 6 * u)))
    poly(Color(0xFFFFC9A0), listOf(Offset(29 * u, cy - 13 * u), Offset(24 * u, cy - 7 * u), Offset(29 * u, cy - 6 * u)))
    poly(Color.White, listOf(Offset(2 * u, cy + 3 * u), Offset(17.5f * u, cy + 14 * u), Offset(33 * u, cy + 3 * u), Offset(17.5f * u, cy + 1 * u)))
    drawCircle(dark, 1.8f * u, Offset(11 * u, cy - 2 * u))
    drawCircle(dark, 1.8f * u, Offset(24 * u, cy - 2 * u))
    drawCircle(dark, 2.2f * u, Offset(17.5f * u, cy + 11.5f * u))
}

private fun DrawScope.shAlien(w: Float, h: Float, c: Color) {
    val u = 1.dp.toPx(); val cy = h / 2
    val g = Color(0xFF6FD66F); val ink = Color(0xFF10261A)
    drawLine(Color(0xFF3FA66B), Offset(11 * u, cy - 11 * u), Offset(7 * u, cy - 19 * u), 1.5f * u, StrokeCap.Round)
    drawLine(Color(0xFF3FA66B), Offset(23 * u, cy - 11 * u), Offset(27 * u, cy - 19 * u), 1.5f * u, StrokeCap.Round)
    drawCircle(Color(0xFFFF4DA6), 2.2f * u, Offset(7 * u, cy - 19 * u))
    drawCircle(Color(0xFFFF4DA6), 2.2f * u, Offset(27 * u, cy - 19 * u))
    drawOval(g, Offset(3 * u, cy - 13 * u), Size(28 * u, 26 * u))
    drawOval(ink, Offset(7 * u, cy - 5 * u), Size(7 * u, 10 * u))
    drawOval(ink, Offset(20 * u, cy - 5 * u), Size(7 * u, 10 * u))
    drawCircle(Color.White, 1.3f * u, Offset(10 * u, cy - 2 * u))
    drawCircle(Color.White, 1.3f * u, Offset(23 * u, cy - 2 * u))
    drawLine(ink, Offset(14 * u, cy + 8 * u), Offset(20 * u, cy + 8 * u), 1.3f * u, StrokeCap.Round)
}

private fun DrawScope.shCactus(w: Float, h: Float, c: Color) {
    val u = 1.dp.toPx(); val cy = h / 2
    val g = Color(0xFF3FA05A); val dg = Color(0xFF2B7A42)
    drawRoundRect(g, Offset(5 * u, cy - 5 * u), Size(9 * u, 5 * u), CornerRadius(2.5f * u))
    drawRoundRect(g, Offset(5 * u, cy - 11 * u), Size(5 * u, 11 * u), CornerRadius(2.5f * u))
    drawRoundRect(g, Offset(22 * u, cy - 1 * u), Size(9 * u, 5 * u), CornerRadius(2.5f * u))
    drawRoundRect(g, Offset(26 * u, cy - 8 * u), Size(5 * u, 11 * u), CornerRadius(2.5f * u))
    drawRoundRect(g, Offset(12 * u, cy - 14 * u), Size(11 * u, 25 * u), CornerRadius(5.5f * u))
    drawLine(dg, Offset(17.5f * u, cy - 11 * u), Offset(17.5f * u, cy + 8 * u), 1f * u)
    drawCircle(Color(0xFFFF5C8A), 3f * u, Offset(17.5f * u, cy - 15 * u))
    drawCircle(Color(0xFFFFD54F), 1.2f * u, Offset(17.5f * u, cy - 15 * u))
    drawRoundRect(Color(0xFFC96A3A), Offset(10 * u, cy + 10 * u), Size(15 * u, 7 * u), CornerRadius(2 * u))
}

private fun DrawScope.shMushroom(w: Float, h: Float, c: Color) {
    val u = 1.dp.toPx(); val cy = h / 2
    drawRoundRect(Color(0xFFFFF1DC), Offset(12 * u, cy - 3 * u), Size(11 * u, 16 * u), CornerRadius(4 * u))
    drawArc(Color(0xFFE53935), 180f, 180f, true, Offset(2 * u, cy - 16 * u), Size(31 * u, 26 * u))
    drawCircle(Color.White, 2.5f * u, Offset(10 * u, cy - 9 * u))
    drawCircle(Color.White, 2f * u, Offset(19 * u, cy - 12 * u))
    drawCircle(Color.White, 2f * u, Offset(26 * u, cy - 8 * u))
    drawCircle(Color.White, 1.4f * u, Offset(17.5f * u, cy - 6 * u))
}

private fun DrawScope.shButterfly(w: Float, h: Float, c: Color) {
    val u = 1.dp.toPx(); val cy = h / 2
    val c1 = Color(0xFFFF8A65); val c2 = Color(0xFFBA68C8); val dark = Color(0xFF3E2723)
    drawOval(c1, Offset(2 * u, cy - 15 * u), Size(16 * u, 17 * u))
    drawOval(c1, Offset(19 * u, cy - 15 * u), Size(16 * u, 17 * u))
    drawOval(c2, Offset(5 * u, cy), Size(13 * u, 14 * u))
    drawOval(c2, Offset(19 * u, cy), Size(13 * u, 14 * u))
    drawCircle(Color.White.copy(alpha = 0.7f), 2.5f * u, Offset(10 * u, cy - 7 * u))
    drawCircle(Color.White.copy(alpha = 0.7f), 2.5f * u, Offset(27 * u, cy - 7 * u))
    drawRoundRect(dark, Offset(17 * u, cy - 12 * u), Size(3 * u, 24 * u), CornerRadius(1.5f * u))
    drawLine(dark, Offset(18 * u, cy - 12 * u), Offset(14 * u, cy - 18 * u), 1f * u, StrokeCap.Round)
    drawLine(dark, Offset(19.5f * u, cy - 12 * u), Offset(23 * u, cy - 18 * u), 1f * u, StrokeCap.Round)
}

private fun DrawScope.shRocket(w: Float, h: Float, c: Color) {
    val u = 1.dp.toPx(); val cy = h / 2
    val red = Color(0xFFE53935)
    val ctr = Offset(19 * u, cy)
    withTransform({ rotate(40f, ctr); scale(0.85f, 0.85f, ctr) }) {
        poly(Color(0xFFFFA726), listOf(Offset(15 * u, cy + 12 * u), Offset(23 * u, cy + 12 * u), Offset(19 * u, cy + 21 * u)))
        poly(Color(0xFFFFEB3B), listOf(Offset(17 * u, cy + 12 * u), Offset(21 * u, cy + 12 * u), Offset(19 * u, cy + 17 * u)))
        poly(red, listOf(Offset(13 * u, cy + 2 * u), Offset(7 * u, cy + 13 * u), Offset(13 * u, cy + 11 * u)))
        poly(red, listOf(Offset(25 * u, cy + 2 * u), Offset(31 * u, cy + 13 * u), Offset(25 * u, cy + 11 * u)))
        drawOval(Color(0xFFECEFF1), Offset(13 * u, cy - 14 * u), Size(12 * u, 27 * u))
        val nose = Path().apply {
            moveTo(13.5f * u, cy - 8 * u); quadraticTo(19 * u, cy - 24 * u, 24.5f * u, cy - 8 * u); close()
        }
        drawPath(nose, red)
        drawCircle(Color(0xFF29B6F6), 3f * u, Offset(19 * u, cy - 3 * u))
        drawCircle(Color(0xFF90A4AE), 3f * u, Offset(19 * u, cy - 3 * u), style = Stroke(1f * u))
    }
}

private fun DrawScope.shPalm(w: Float, h: Float, c: Color) {
    val u = 1.dp.toPx(); val cy = h / 2
    val trunk = Path().apply { moveTo(17 * u, cy + 15 * u); quadraticTo(19.5f * u, cy + 3 * u, 18 * u, cy - 7 * u) }
    drawPath(trunk, Color(0xFF8D5A2B), style = Stroke(3.2f * u, cap = StrokeCap.Round))
    val leaf = Color(0xFF2E9E4F)
    listOf(
        floatArrayOf(5f, -19f, 1.5f, -3f), floatArrayOf(10f, -22f, 5f, -13f), floatArrayOf(18f, -24f, 18f, -16f),
        floatArrayOf(26f, -22f, 31f, -13f), floatArrayOf(31f, -19f, 34.5f, -3f)
    ).forEach { f ->
        val p = Path().apply { moveTo(18 * u, cy - 7 * u); quadraticTo(f[0] * u, cy + f[1] * u, f[2] * u, cy + f[3] * u) }
        drawPath(p, leaf, style = Stroke(2.4f * u, cap = StrokeCap.Round))
    }
    val date = Color(0xFF6D3F1A)
    drawCircle(date, 1.6f * u, Offset(15.5f * u, cy - 5 * u))
    drawCircle(date, 1.6f * u, Offset(20.5f * u, cy - 5 * u))
    drawCircle(date, 1.6f * u, Offset(18 * u, cy - 3 * u))
}

// ---------------------------- أشكال الأعلى والزوايا ----------------------------

private fun DrawScope.shDino(w: Float, h: Float, c: Color) {
    val u = 1.dp.toPx()
    val n = ((w - 24 * u) / (13 * u)).toInt().coerceIn(2, 9)
    val step = (w - 24 * u) / (n - 1)
    for (i in 0 until n) {
        val x = 12 * u + i * step
        poly(Color(0xFF3FA66B), listOf(Offset(x - 5 * u, 1 * u), Offset(x, -8 * u), Offset(x + 5 * u, 1 * u)))
        poly(Color(0xFF86E0A8), listOf(Offset(x - 1.5f * u, 0f), Offset(x, -5 * u), Offset(x + 1.5f * u, 0f)))
    }
}

private fun DrawScope.shCrown(w: Float, h: Float, c: Color) {
    val u = 1.dp.toPx(); val x0 = w * 0.62f - 14 * u
    poly(Color(0xFFFFC107), listOf(Offset(x0, 3 * u), Offset(x0, -7 * u), Offset(x0 + 7 * u, -1 * u), Offset(x0 + 14 * u, -11 * u), Offset(x0 + 21 * u, -1 * u), Offset(x0 + 28 * u, -7 * u), Offset(x0 + 28 * u, 3 * u)))
    drawRect(Color(0xFFFFA000), Offset(x0, 0f), Size(28 * u, 3 * u))
    drawCircle(Color(0xFFE0245E), 1.8f * u, Offset(x0 + 14 * u, -7 * u))
    drawCircle(Color.White, 1.6f * u, Offset(x0, -7 * u))
    drawCircle(Color.White, 1.6f * u, Offset(x0 + 28 * u, -7 * u))
}

private fun DrawScope.flame(x: Float, y0: Float, wd: Float, hg: Float, color: Color) {
    val p = Path().apply {
        moveTo(x - wd, y0)
        cubicTo(x - wd * 1.2f, y0 - hg * 0.5f, x - wd * 0.2f, y0 - hg * 0.6f, x, y0 - hg)
        cubicTo(x + wd * 0.2f, y0 - hg * 0.6f, x + wd * 1.2f, y0 - hg * 0.5f, x + wd, y0)
        close()
    }
    drawPath(p, color)
}

private fun DrawScope.shFlames(w: Float, h: Float, c: Color) {
    val u = 1.dp.toPx()
    val n = ((w - 20 * u) / (16 * u)).toInt().coerceIn(2, 7)
    val step = (w - 20 * u) / (n - 1)
    for (i in 0 until n) {
        val x = 10 * u + i * step
        val hg = (if (i % 2 == 0) 14 else 10) * u
        flame(x, 3 * u, 5 * u, hg, Color(0xFFFF7A1A))
        flame(x, 3 * u, 2.8f * u, hg * 0.62f, Color(0xFFFFEB3B))
    }
}

private fun DrawScope.bolt(cx: Float, cy: Float, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(cx + 0.25f * s, cy - s); lineTo(cx - 0.55f * s, cy + 0.1f * s); lineTo(cx - 0.05f * s, cy + 0.1f * s)
        lineTo(cx - 0.3f * s, cy + s); lineTo(cx + 0.6f * s, cy - 0.25f * s); lineTo(cx + 0.05f * s, cy - 0.25f * s); close()
    }
    drawPath(p, color)
}

private fun DrawScope.shBolt(w: Float, h: Float, c: Color) {
    val u = 1.dp.toPx()
    bolt(w - 7 * u, 3 * u, 9 * u, c)
    bolt(8 * u, h - 3 * u, 7 * u, c.copy(alpha = 0.85f))
}

private fun DrawScope.note(x: Float, y: Float, u: Float, color: Color) {
    drawOval(color, Offset(x - 4 * u, y - 3 * u), Size(8 * u, 6 * u))
    drawLine(color, Offset(x + 3.4f * u, y), Offset(x + 3.4f * u, y - 13 * u), 1.4f * u)
    val flag = Path().apply { moveTo(x + 3.4f * u, y - 13 * u); quadraticTo(x + 9 * u, y - 11 * u, x + 7 * u, y - 6 * u) }
    drawPath(flag, color, style = Stroke(1.4f * u, cap = StrokeCap.Round))
}

private fun DrawScope.shNotes(w: Float, h: Float, c: Color) {
    val u = 1.dp.toPx()
    note(w - 10 * u, 11 * u, u, c)
    note(10 * u, h - 6 * u, 0.8f * u, c.copy(alpha = 0.85f))
    note(w * 0.62f, 4 * u, 0.7f * u, c.copy(alpha = 0.7f))
}

private fun DrawScope.gem(cx: Float, cy: Float, s: Float, color: Color, u: Float) {
    poly(color, listOf(Offset(cx - s, cy - 0.25f * s), Offset(cx - 0.55f * s, cy - 0.8f * s), Offset(cx + 0.55f * s, cy - 0.8f * s), Offset(cx + s, cy - 0.25f * s), Offset(cx, cy + s)))
    val f = Color.White.copy(alpha = 0.55f)
    drawLine(f, Offset(cx - s, cy - 0.25f * s), Offset(cx + s, cy - 0.25f * s), 0.9f * u)
    drawLine(f, Offset(cx - 0.55f * s, cy - 0.8f * s), Offset(cx - 0.25f * s, cy - 0.25f * s), 0.9f * u)
    drawLine(f, Offset(cx + 0.55f * s, cy - 0.8f * s), Offset(cx + 0.25f * s, cy - 0.25f * s), 0.9f * u)
    drawLine(f, Offset(cx - 0.25f * s, cy - 0.25f * s), Offset(cx, cy + s), 0.9f * u)
    drawLine(f, Offset(cx + 0.25f * s, cy - 0.25f * s), Offset(cx, cy + s), 0.9f * u)
}

private fun DrawScope.shGems(w: Float, h: Float, c: Color) {
    val u = 1.dp.toPx()
    gem(w - 9 * u, 6 * u, 7 * u, c, u)
    gem(9 * u, h - 5 * u, 6 * u, c.copy(alpha = 0.85f), u)
    gem(w * 0.62f, 2 * u, 4 * u, c.copy(alpha = 0.75f), u)
}

private fun DrawScope.shBunny(w: Float, h: Float, c: Color) {
    val u = 1.dp.toPx(); val ax = w * 0.62f
    val pink = Color(0xFFFFB6C8)
    withTransform({ rotate(-12f, Offset(ax - 4 * u, 3 * u)) }) {
        drawOval(c, Offset(ax - 8 * u, -18 * u), Size(9 * u, 22 * u))
        drawOval(pink, Offset(ax - 6 * u, -14 * u), Size(5 * u, 15 * u))
    }
    withTransform({ rotate(12f, Offset(ax + 5 * u, 3 * u)) }) {
        drawOval(c, Offset(ax + 1 * u, -18 * u), Size(9 * u, 22 * u))
        drawOval(pink, Offset(ax + 3 * u, -14 * u), Size(5 * u, 15 * u))
    }
}

private fun DrawScope.shRainbow(w: Float, h: Float, c: Color) {
    val u = 1.dp.toPx(); val cx = w - 14 * u; val cy = 5 * u
    val cols = listOf(Color(0xFFE53935), Color(0xFFFF9800), Color(0xFFFFEB3B), Color(0xFF43A047), Color(0xFF1E88E5))
    cols.forEachIndexed { i, col ->
        val r = (13 - 2 * i) * u
        drawArc(col, 180f, 180f, false, Offset(cx - r, cy - r), Size(2 * r, 2 * r), style = Stroke(2.1f * u))
    }
    drawCircle(Color.White, 3f * u, Offset(cx - 12 * u, cy + 1 * u))
    drawCircle(Color.White, 3f * u, Offset(cx + 12 * u, cy + 1 * u))
}

private fun DrawScope.shCrescent(w: Float, h: Float, c: Color) {
    val u = 1.dp.toPx(); val cx = w - 14 * u; val cy = 6 * u
    val outer = Path().apply { addOval(Rect(Offset(cx - 8 * u, cy - 8 * u), Size(16 * u, 16 * u))) }
    val cut = Path().apply { addOval(Rect(Offset(cx - 4 * u, cy - 9 * u), Size(15 * u, 15 * u))) }
    drawPath(Path.combine(PathOperation.Difference, outer, cut), c)
    spark(cx + 8 * u, cy - 5 * u, 3.4f * u, c)
    spark(10 * u, h - 4 * u, 3f * u, c.copy(alpha = 0.8f))
}

private fun DrawScope.shHalo(w: Float, h: Float, c: Color) {
    val u = 1.dp.toPx(); val ax = w * 0.62f
    drawOval(c.copy(alpha = 0.28f), Offset(ax - 11 * u, -9 * u), Size(22 * u, 8 * u), style = Stroke(4f * u))
    drawOval(c, Offset(ax - 10 * u, -8 * u), Size(20 * u, 6 * u), style = Stroke(2f * u))
}

/** كل أشكال المحرر. أول عنصر = بدون شكل. */
val BubbleDecors: List<BubbleDecor> = listOf(
    BubbleDecor("none", "بدون"),
    BubbleDecor("panda", "باندا", sidePad = 36.dp, draw = { w, h, c -> shPanda(w, h, c) }),
    BubbleDecor("fox", "ثعلب", sidePad = 36.dp, draw = { w, h, c -> shFox(w, h, c) }),
    BubbleDecor("alien", "فضائي", sidePad = 34.dp, topPad = 8.dp, draw = { w, h, c -> shAlien(w, h, c) }),
    BubbleDecor("cactus", "صبّار", sidePad = 34.dp, topPad = 4.dp, draw = { w, h, c -> shCactus(w, h, c) }),
    BubbleDecor("mushroom", "فطر", sidePad = 34.dp, draw = { w, h, c -> shMushroom(w, h, c) }),
    BubbleDecor("butterfly", "فراشة", sidePad = 38.dp, topPad = 4.dp, draw = { w, h, c -> shButterfly(w, h, c) }),
    BubbleDecor("rocket", "صاروخ", sidePad = 36.dp, topPad = 4.dp, draw = { w, h, c -> shRocket(w, h, c) }),
    BubbleDecor("palm", "نخلة", sidePad = 36.dp, topPad = 8.dp, draw = { w, h, c -> shPalm(w, h, c) }),
    BubbleDecor("dino", "ديناصور", topPad = 9.dp, draw = { w, h, c -> shDino(w, h, c) }),
    BubbleDecor("crown", "تاج", topPad = 13.dp, draw = { w, h, c -> shCrown(w, h, c) }),
    BubbleDecor("flames", "نار", topPad = 14.dp, draw = { w, h, c -> shFlames(w, h, c) }),
    BubbleDecor("bolt", "صواعق", topPad = 8.dp, tintable = true, draw = { w, h, c -> shBolt(w, h, c) }),
    BubbleDecor("notes", "نوتات", topPad = 10.dp, tintable = true, draw = { w, h, c -> shNotes(w, h, c) }),
    BubbleDecor("gems", "جواهر", topPad = 6.dp, tintable = true, draw = { w, h, c -> shGems(w, h, c) }),
    BubbleDecor("bunny", "أرنب", topPad = 18.dp, tintable = true, draw = { w, h, c -> shBunny(w, h, c) }),
    BubbleDecor("rainbow", "قوس قزح", topPad = 10.dp, draw = { w, h, c -> shRainbow(w, h, c) }),
    BubbleDecor("crescent", "هلال ونجمة", topPad = 8.dp, tintable = true, draw = { w, h, c -> shCrescent(w, h, c) }),
    BubbleDecor("halo", "هالة", topPad = 10.dp, tintable = true, draw = { w, h, c -> shHalo(w, h, c) })
)

fun bubbleDecorById(id: String): BubbleDecor = BubbleDecors.firstOrNull { it.id == id } ?: BubbleDecors[0]
