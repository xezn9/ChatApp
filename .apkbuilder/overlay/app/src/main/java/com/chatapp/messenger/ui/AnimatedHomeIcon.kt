package com.chatapp.messenger.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * أيقونة الرئيسية المتحركة (موشن غرافك).
 *
 * التسلسل عند اختيار التبويب:
 *  1. الأيقونة كلها تنفخ (pop) وتوهج خفيف خلفها
 *  2. الجسم ينط (squash & stretch)
 *  3. السقف ينزل من فوق، يلف شوي ويرتد
 *  4. الباب ينفتح
 *  5. المدخنة تطلع ويطلع منها دخان
 *
 * @param selected        هل التبويب مختار؟ الحركة تشتغل لما يصير true
 * @param replayKey       غيّر قيمته (مثلاً عدّاد) لو تبي تعيد الحركة والتبويب مختار أصلاً
 * @param animateOnStart  لو false والتبويب مختار من أول فتح التطبيق، ما تشتغل الحركة أول مرة
 */
@Composable
fun AnimatedHomeIcon(
    selected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    selectedColor: Color = Color.White,
    unselectedColor: Color = Color.White.copy(alpha = 0.6f),
    glowColor: Color = selectedColor,
    replayKey: Int = 0,
    animateOnStart: Boolean = true
) {
    val color by animateColorAsState(
        targetValue = if (selected) selectedColor else unselectedColor,
        animationSpec = tween(250),
        label = "homeIconColor"
    )

    val startHidden = selected && animateOnStart
    val firstRun = remember { mutableStateOf(true) }

    val body = remember { Animatable(if (startHidden) 0f else 1f) }
    val roof = remember { Animatable(if (startHidden) 0f else 1f) }
    val door = remember { Animatable(if (startHidden) 0f else 1f) }
    val chimney = remember { Animatable(if (startHidden) 0f else 1f) }
    val wobble = remember { Animatable(0f) }      // لفة السقف بالدرجات
    val pop = remember { Animatable(1f) }         // تكبير الأيقونة كلها
    val glow = remember { Animatable(0f) }        // التوهج
    val smoke = remember { Animatable(1f) }       // 1 = خلص الدخان

    LaunchedEffect(selected, replayKey) {
        val isFirst = firstRun.value
        firstRun.value = false

        if (!selected) {
            // رجوع هادئ للحالة العادية
            launch { pop.animateTo(1f, tween(150)) }
            return@LaunchedEffect
        }
        if (isFirst && !animateOnStart) return@LaunchedEffect

        body.snapTo(0f); roof.snapTo(0f); door.snapTo(0f); chimney.snapTo(0f)
        wobble.snapTo(-10f); smoke.snapTo(1f); glow.snapTo(0f)

        // 1) النفخة + التوهج
        launch {
            pop.snapTo(0.85f)
            pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        }
        launch {
            glow.animateTo(1f, tween(200))
            glow.animateTo(0f, tween(600))
        }
        // 2) الجسم
        launch { body.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) }
        // 3) السقف
        launch {
            delay(90)
            launch { roof.animateTo(1f, spring(0.45f, 300f)) }
            wobble.animateTo(0f, spring(0.35f, 200f))
        }
        // 4) الباب
        launch {
            delay(230)
            door.animateTo(1f, spring(0.6f, 400f))
        }
        // 5) المدخنة + الدخان
        launch {
            delay(320)
            chimney.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
        }
        launch {
            delay(480)
            smoke.snapTo(0f)
            smoke.animateTo(1f, tween(950, easing = LinearEasing))
        }
    }

    val roofPath = remember {
        Path().apply {
            moveTo(205f, 390f)
            lineTo(360f, 243f)
            lineTo(516f, 390f)
        }
    }

    Canvas(modifier.size(size)) {
        val s = this.size.width / 380f
        val iconColor = color

        withTransform({
            scale(s, s, Offset.Zero)
            scale(pop.value, pop.value, Offset(190f, 190f))
            translate(-170f, -201f)
        }) {
            // التوهج خلف الأيقونة
            if (glow.value > 0.01f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor.copy(alpha = 0.35f * glow.value), Color.Transparent),
                        center = Offset(360f, 390f),
                        radius = 230f
                    ),
                    radius = 230f,
                    center = Offset(360f, 390f)
                )
            }

            // الدخان: ثلاث فقاعات تطلع وتتلاشى
            for (i in 0..2) {
                val p = ((smoke.value - i * 0.18f) / 0.64f)
                if (p > 0f && p < 1f) {
                    drawCircle(
                        color = iconColor.copy(alpha = (1f - p) * 0.55f),
                        radius = 10f + 16f * p,
                        center = Offset(
                            460f + sin(p * PI.toFloat() * 2f + i) * 12f,
                            222f - 120f * p
                        )
                    )
                }
            }

            // المدخنة
            val ch = chimney.value.coerceIn(0f, 1f)
            drawLine(
                color = iconColor,
                start = Offset(460f, 330f),
                end = Offset(460f, 330f - 73f * ch),
                strokeWidth = 40f,
                cap = StrokeCap.Round,
                alpha = ch
            )

            // السقف (ينزل + يلف حول القمة)
            withTransform({
                translate(0f, (1f - roof.value) * -120f)
                rotate(wobble.value, Offset(360f, 243f))
            }) {
                drawPath(
                    path = roofPath,
                    color = iconColor,
                    alpha = roof.value.coerceIn(0f, 1f),
                    style = Stroke(width = 40f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // الجسم + الباب
            val doorTop = 558f - 93f * door.value.coerceIn(0f, 1.15f)
            val bodyPath = Path().apply {
                moveTo(232f, 440f)
                lineTo(355f, 308f)
                quadraticBezierTo(360f, 303f, 365f, 308f)
                lineTo(490f, 440f)
                lineTo(490f, 520f)
                quadraticBezierTo(490f, 558f, 452f, 558f)
                lineTo(394f, 558f)
                lineTo(394f, doorTop)
                lineTo(327f, doorTop)
                lineTo(327f, 558f)
                lineTo(270f, 558f)
                quadraticBezierTo(232f, 558f, 232f, 520f)
                close()
            }
            withTransform({
                scale(1f, 0.6f + 0.4f * body.value, Offset(360f, 558f))
            }) {
                drawPath(bodyPath, iconColor, alpha = body.value.coerceIn(0f, 1f))
            }
        }
    }
}
