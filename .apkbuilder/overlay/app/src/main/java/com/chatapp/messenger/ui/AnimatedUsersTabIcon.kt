package com.chatapp.messenger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Animated "users" tab icon.
 * Plays once (1.2s) every time [selected] becomes true (استخدم animateOnStart=false لو ما تبيها تشتغل أول تركيب).
 *
 * Usage inside a NavigationBarItem:
 *   icon = { AnimatedUsersTabIcon(selected = currentRoute == "users", tint = LocalContentColor.current) }
 */
@Composable
fun AnimatedUsersTabIcon(
    selected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    tint: Color = Color.White,
    replayKey: Int = 0,
    animateOnStart: Boolean = true,
) {
    // animation clock in "frames" (30 fps, 36 frames total); rests on the final frame
    val frame = remember { Animatable(if (selected && animateOnStart) 0f else USERS_FRAMES) }
    val firstRun = remember { mutableStateOf(true) }

    LaunchedEffect(selected, replayKey) {
        val isFirst = firstRun.value
        firstRun.value = false
        if (selected && !(isFirst && !animateOnStart)) {
            frame.snapTo(0f)
            frame.animateTo(USERS_FRAMES, tween(durationMillis = 1200, easing = LinearEasing))
        } else {
            frame.snapTo(USERS_FRAMES)
        }
    }

    Canvas(modifier.size(size)) {
        val f = frame.value
        scale(scaleX = this.size.width / USERS_VIEW, scaleY = this.size.height / USERS_VIEW, pivot = Offset.Zero) {
            translate(-USERS_VIEW_X, -USERS_VIEW_Y) {
            USERS_PARTS.forEachIndexed { i, p ->
                val sc = usersSample(p.scale, f) / 100f
                val a = usersSample(p.alpha, f) / 100f
                val dx = usersSample(p.dx, f)
                val dy = usersSample(p.dy, f)
                translate(dx, dy) {
                    scale(sc, sc, pivot = Offset(p.ax, p.ay)) {
                        when (i) {
                            0 -> drawPath(USERS_BACK_BODY, tint, alpha = a)
                            1 -> drawCircle(tint, radius = 46.0f, center = Offset(278.0f, 147.0f), alpha = a)
                            2 -> drawPath(USERS_FRONT_BODY, tint, alpha = a)
                            else -> drawCircle(tint, radius = 55.0f, center = Offset(168.0f, 156.0f), alpha = a)
                        }
                    }
                }
            }
            }
        }
    }
}

private const val USERS_VIEW = 350f      // مساحة العرض (مقصوصة حول الرسمة لتملأ الصندوق)
private const val USERS_VIEW_X = 49f
private const val USERS_VIEW_Y = 46.5f
private const val USERS_FRAMES = 36f
private val USERS_EASE = CubicBezierEasing(0.25f, 0.0f, 0.15f, 1.0f)

private class UsersPart(
    val ax: Float, val ay: Float,
    val scale: List<Pair<Float, Float>>,
    val alpha: List<Pair<Float, Float>>,
    val dx: List<Pair<Float, Float>>,
    val dy: List<Pair<Float, Float>>,
)

/** Keyframe sampler: eased interpolation between (frame to value) pairs. */
private fun usersSample(kfs: List<Pair<Float, Float>>, f: Float): Float {
    if (f <= kfs.first().first) return kfs.first().second
    if (f >= kfs.last().first) return kfs.last().second
    for (i in 0 until kfs.size - 1) {
        val (f0, v0) = kfs[i]
        val (f1, v1) = kfs[i + 1]
        if (f in f0..f1) {
            val t = USERS_EASE.transform((f - f0) / (f1 - f0))
            return v0 + (v1 - v0) * t
        }
    }
    return kfs.last().second
}

// Order = draw order, bottom to top: back body, back head, front body, front head
private val USERS_PARTS = listOf(
    UsersPart(297.0f, 318.0f,
        scale = listOf(8f to 70.0f, 22f to 104.0f, 30f to 100.0f),
        alpha = listOf(8f to 0.0f, 16f to 100.0f),
        dx = listOf(8f to -55.0f, 26f to 0.0f),
        dy = listOf(8f to 0.0f, 9f to 0.0f)),
    UsersPart(278.0f, 147.0f,
        scale = listOf(12f to 0.0f, 24f to 118.0f, 33f to 100.0f),
        alpha = listOf(12f to 0.0f, 13f to 100.0f),
        dx = listOf(12f to -45.0f, 28f to 0.0f),
        dy = listOf(12f to -20.0f, 28f to 0.0f)),
    UsersPart(168.0f, 342.0f,
        scale = listOf(0f to 60.0f, 14f to 106.0f, 22f to 100.0f),
        alpha = listOf(0f to 0.0f, 8f to 100.0f),
        dx = listOf(0f to 0.0f, 1f to 0.0f),
        dy = listOf(0f to 18.0f, 14f to 0.0f)),
    UsersPart(168.0f, 156.0f,
        scale = listOf(5f to 0.0f, 17f to 118.0f, 26f to 100.0f),
        alpha = listOf(5f to 0.0f, 6f to 100.0f),
        dx = listOf(0f to 0.0f, 1f to 0.0f),
        dy = listOf(5f to -30.0f, 17f to 0.0f)),
)

private val USERS_BACK_BODY = Path().apply {
    moveTo(216.0f, 233.0f)
    lineTo(230.0f, 238.0f)
    lineTo(241.0f, 244.0f)
    lineTo(252.0f, 252.0f)
    lineTo(265.0f, 265.0f)
    lineTo(277.0f, 282.0f)
    lineTo(283.0f, 294.0f)
    lineTo(290.0f, 317.0f)
    lineTo(365.0f, 317.0f)
    lineTo(371.0f, 314.0f)
    lineTo(376.0f, 307.0f)
    lineTo(377.0f, 304.0f)
    lineTo(377.0f, 295.0f)
    lineTo(376.0f, 294.0f)
    lineTo(375.0f, 286.0f)
    lineTo(371.0f, 274.0f)
    lineTo(367.0f, 267.0f)
    lineTo(367.0f, 265.0f)
    lineTo(356.0f, 247.0f)
    lineTo(352.0f, 242.0f)
    lineTo(333.0f, 224.0f)
    lineTo(313.0f, 213.0f)
    lineTo(304.0f, 210.0f)
    lineTo(301.0f, 210.0f)
    lineTo(296.0f, 208.0f)
    lineTo(290.0f, 208.0f)
    lineTo(289.0f, 207.0f)
    lineTo(271.0f, 207.0f)
    lineTo(270.0f, 208.0f)
    lineTo(259.0f, 209.0f)
    lineTo(255.0f, 211.0f)
    lineTo(249.0f, 212.0f)
    lineTo(234.0f, 219.0f)
    lineTo(221.0f, 228.0f)
    close()
}

private val USERS_FRONT_BODY = Path().apply {
    moveTo(70.0f, 320.0f)
    lineTo(70.0f, 326.0f)
    lineTo(73.0f, 334.0f)
    lineTo(78.0f, 339.0f)
    lineTo(82.0f, 341.0f)
    lineTo(253.0f, 341.0f)
    lineTo(257.0f, 339.0f)
    lineTo(262.0f, 334.0f)
    lineTo(265.0f, 327.0f)
    lineTo(264.0f, 315.0f)
    lineTo(258.0f, 296.0f)
    lineTo(250.0f, 280.0f)
    lineTo(240.0f, 266.0f)
    lineTo(226.0f, 252.0f)
    lineTo(213.0f, 243.0f)
    lineTo(201.0f, 237.0f)
    lineTo(192.0f, 234.0f)
    lineTo(189.0f, 234.0f)
    lineTo(184.0f, 232.0f)
    lineTo(178.0f, 232.0f)
    lineTo(177.0f, 231.0f)
    lineTo(159.0f, 231.0f)
    lineTo(158.0f, 232.0f)
    lineTo(152.0f, 232.0f)
    lineTo(151.0f, 233.0f)
    lineTo(143.0f, 234.0f)
    lineTo(122.0f, 243.0f)
    lineTo(109.0f, 252.0f)
    lineTo(95.0f, 266.0f)
    lineTo(84.0f, 282.0f)
    lineTo(74.0f, 304.0f)
    lineTo(74.0f, 307.0f)
    lineTo(72.0f, 311.0f)
    lineTo(71.0f, 319.0f)
    close()
}
