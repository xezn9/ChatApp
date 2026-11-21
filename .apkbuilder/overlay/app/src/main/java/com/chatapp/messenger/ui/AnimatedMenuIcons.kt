package com.chatapp.messenger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * أيقونات قائمة ⋮ المتحركة (موشن غرافك).
 *
 * الحالة الطبيعية: الأيقونة ثابتة ومكتملة.
 * الحركة تشتغل فقط لما تتغير قيمة [play] (مثلاً عدّاد يزيد عند الضغط).
 */
enum class MenuIconKind(
    /** مدة الحركة بالميلي ثانية */
    val durationMs: Int,
    /** آخر نقطة بالتايم لاين الأصلي تكتمل عندها الأيقونة */
    internal val end: Float,
) {
    Checkbox(700, 0.62f),
    Menu(750, 0.80f),
    CalendarClock(950, 0.85f),
}

@Composable
fun AnimatedMenuIcon(
    kind: MenuIconKind,
    play: Int,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    color: Color = Color.White,
) {
    val prog = remember { Animatable(1f) } // 1 = مكتملة (الوضع الطبيعي)
    LaunchedEffect(play) {
        if (play > 0) {
            prog.snapTo(0f)
            prog.animateTo(1f, tween(kind.durationMs, easing = LinearEasing))
        }
    }
    val shapes = remember { MenuIconShapes() }

    Canvas(modifier.size(size)) {
        val t = prog.value * kind.end
        val k = this.size.minDimension / 100f
        withTransform({ scale(k, k, Offset.Zero) }) {
            when (kind) {
                MenuIconKind.Checkbox -> drawCheckbox(t, color, shapes)
                MenuIconKind.Menu -> drawMenu(t, color)
                MenuIconKind.CalendarClock -> drawCalendarClock(t, color, shapes)
            }
        }
    }
}

// ---------------------------------------------------------------- أدوات مشتركة
private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun easeOutCubic(x: Float) = 1f - (1f - x).pow(3)
private fun easeInOutCubic(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
private fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f; val c3 = c1 + 1f
    return 1f + c3 * (x - 1f).pow(3) + c1 * (x - 1f).pow(2)
}
private fun Path.trimmed(m: PathMeasure, p: Float): Path = Path().also { m.getSegment(0f, m.length * p, it, true) }
private fun roundStroke(w: Float) = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)

private class MenuIconShapes {
    val box = Path().apply {
        moveTo(70f, 22.5f); lineTo(32.6f, 22.5f)
        arcTo(Rect(20.6f, 22.5f, 44.6f, 46.5f), 270f, -90f, false)
        lineTo(20.6f, 64f)
        arcTo(Rect(20.6f, 52f, 44.6f, 76f), 180f, -90f, false)
        lineTo(64f, 76f)
        arcTo(Rect(52f, 52f, 76f, 76f), 90f, -90f, false)
        lineTo(76f, 54f)
    }
    val check = Path().apply { moveTo(35f, 47f); lineTo(50f, 63f); lineTo(84f, 30f) }
    val body = Path().apply {
        moveTo(78f, 51f); lineTo(78f, 40.5f)
        arcTo(Rect(56f, 29.5f, 78f, 51.5f), 0f, -90f, false)
        lineTo(34.5f, 29.5f)
        arcTo(Rect(23.5f, 29.5f, 45.5f, 51.5f), 270f, -90f, false)
        lineTo(23.5f, 69f)
        arcTo(Rect(23.5f, 58f, 45.5f, 80f), 180f, -90f, false)
        lineTo(49.5f, 80f)
    }
    val hand = Path().apply { moveTo(66.2f, 62.8f); lineTo(66.2f, 67.6f); lineTo(70.2f, 67.6f) }
    val boxM = PathMeasure().apply { setPath(box, false) }
    val checkM = PathMeasure().apply { setPath(check, false) }
    val bodyM = PathMeasure().apply { setPath(body, false) }
    val dots = listOf(47.3f to 4, 57.3f to 3, 67.3f to 2).flatMap { (y, n) ->
        listOf(35.5f, 45.7f, 55.9f, 66.1f).take(n).map { x -> Offset(x, y) }
    }
}

// ---------------------------------------------------------------- 1) مربع الصح
private fun DrawScope.drawCheckbox(t: Float, c: Color, s: MenuIconShapes) {
    val pop = 1f + 0.08f * sin(PI.toFloat() * seg(t, 0.50f, 0.62f))
    val pb = easeInOutCubic(seg(t, 0f, 0.35f))
    val pc = easeOutCubic(seg(t, 0.28f, 0.50f))
    withTransform({ scale(pop, pop, Offset(50f, 50f)) }) {
        if (pb > 0.001f) drawPath(s.box.trimmed(s.boxM, pb), c, style = roundStroke(5.5f))
        if (pc > 0.001f) drawPath(s.check.trimmed(s.checkM, pc), c, style = roundStroke(11f))
    }
}

// ---------------------------------------------------------------- 2) الخطوط الثلاثة
private fun DrawScope.drawMenu(t: Float, c: Color) {
    listOf(25f, 50f, 75f).forEachIndexed { i, y ->
        val p = easeOutCubic(seg(t, 0.05f + 0.08f * i, 0.35f + 0.08f * i))
        val dy = -3f * sin(PI.toFloat() * seg(t, 0.50f + 0.06f * i, 0.68f + 0.06f * i))
        if (p > 0.001f) {
            drawLine(
                color = c.copy(alpha = c.alpha * (p * 6f).coerceAtMost(1f)),
                start = Offset(12.5f, y + dy),
                end = Offset(12.5f + 75f * p, y + dy),
                strokeWidth = 12.5f,
                cap = StrokeCap.Round,
            )
        }
    }
}

// ---------------------------------------------------------------- 3) تقويم + ساعة
private fun DrawScope.drawCalendarClock(t: Float, c: Color, s: MenuIconShapes) {
    val center = Offset(66.6f, 67.9f)
    val r = 11.8f

    val pb = easeInOutCubic(seg(t, 0f, 0.30f))
    if (pb > 0.001f) drawPath(s.body.trimmed(s.bodyM, pb), c, style = roundStroke(4f))

    listOf(35.5f, 50.5f, 65.5f).forEachIndexed { i, x ->
        val p = easeOutCubic(seg(t, 0.18f + 0.05f * i, 0.34f + 0.05f * i))
        if (p > 0.001f) {
            val o = -10f * (1f - p)
            drawLine(c.copy(alpha = c.alpha * (p * 3f).coerceAtMost(1f)), Offset(x, 24f + o), Offset(x, 35f + o), 4f, StrokeCap.Round)
        }
    }

    s.dots.forEachIndexed { i, o ->
        val e = easeOutBack(seg(t, 0.30f + 0.025f * i, 0.42f + 0.025f * i))
        if (e > 0f) drawCircle(c, 2.3f * e, o)
    }

    val pc = easeInOutCubic(seg(t, 0.45f, 0.65f))
    if (pc > 0.001f) {
        drawArc(
            color = c, startAngle = 270f, sweepAngle = 360f * pc, useCenter = false,
            topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2),
            style = roundStroke(3.8f),
        )
    }

    val ha = seg(t, 0.60f, 0.68f)
    if (ha > 0f) {
        val ang = 360f * easeInOutCubic(seg(t, 0.62f, 0.85f))
        withTransform({ rotate(ang, center) }) {
            drawPath(s.hand, c.copy(alpha = c.alpha * ha), style = roundStroke(3.8f))
        }
    }
}
