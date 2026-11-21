package com.chatapp.messenger.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * أيقونة البروفايل المتحركة.
 *
 * التسلسل عند اختيار التبويب:
 *  1. الأيقونة كلها تنفخ (pop)
 *  2. الجسم يطلع من تحت بارتداد (squash & stretch)
 *  3. الراس ينزل من فوق ويكبر بارتداد
 *  4. موجة دائرية تنتشر حول الأيقونة
 *  5. الراس يهز هزة ترحيب خفيفة (nod)
 *
 * @param replayKey       غيّر قيمته لو تبي تعيد الحركة والتبويب مختار أصلاً
 * @param animateOnStart  لو false والتبويب مختار من أول فتح التطبيق، ما تشتغل الحركة أول مرة
 */
@Composable
fun AnimatedProfileIcon(
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
        label = "profileIconColor"
    )

    val startHidden = selected && animateOnStart
    val firstRun = remember { mutableStateOf(true) }

    val body = remember { Animatable(if (startHidden) 0f else 1f) }
    val head = remember { Animatable(if (startHidden) 0f else 1f) }
    val nod = remember { Animatable(0f) }     // هزة الراس بالدرجات
    val pop = remember { Animatable(1f) }     // تكبير الأيقونة كلها
    val ring = remember { Animatable(1f) }    // 1 = خلصت الموجة

    LaunchedEffect(selected, replayKey) {
        val isFirst = firstRun.value
        firstRun.value = false

        if (!selected) {
            launch { pop.animateTo(1f, tween(150)) }
            return@LaunchedEffect
        }
        if (isFirst && !animateOnStart) return@LaunchedEffect

        body.snapTo(0f); head.snapTo(0f); nod.snapTo(0f); ring.snapTo(1f)

        // 1) النفخة
        launch {
            pop.snapTo(0.88f)
            pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        }
        // 2) الجسم
        launch { body.animateTo(1f, spring(0.55f, 400f)) }
        // 3) الراس
        launch {
            delay(100)
            head.animateTo(1f, spring(0.4f, 500f))
        }
        // 4) الموجة
        launch {
            delay(150)
            ring.snapTo(0f)
            ring.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        }
        // 5) هزة الراس
        launch {
            delay(420)
            nod.animateTo(-10f, tween(120))
            nod.animateTo(0f, spring(0.3f, 220f))
        }
    }

    // الأشكال مأخوذة من الصورة (إحداثيات 512×512)
    val bodyPath = remember {
        Path().apply {
            moveTo(130f, 345f)
            cubicTo(130f, 290f, 190f, 265f, 253f, 265f)
            cubicTo(316f, 265f, 377f, 290f, 377f, 345f)
            cubicTo(377f, 375f, 360f, 390f, 330f, 390f)
            lineTo(177f, 390f)
            cubicTo(147f, 390f, 130f, 375f, 130f, 345f)
            close()
        }
    }

    Canvas(modifier.size(size)) {
        val s = this.size.width / 512f
        val iconColor = color

        withTransform({
            scale(s, s, Offset.Zero)
            scale(pop.value, pop.value, Offset(256f, 256f))
        }) {
            // الموجة
            val r = ring.value
            if (r > 0.001f && r < 0.999f) {
                drawCircle(
                    color = glowColor.copy(alpha = (1f - r) * 0.5f),
                    radius = 90f + 100f * r,
                    center = Offset(253f, 255f),
                    style = Stroke(width = 12f * (1f - r) + 2f)
                )
            }

            // الجسم (يطلع من تحت + squash & stretch)
            val b = body.value
            withTransform({
                translate(0f, (1f - b) * 70f)
                scale(1f, 0.7f + 0.3f * b, Offset(253f, 390f))
            }) {
                drawPath(bodyPath, iconColor, alpha = b.coerceIn(0f, 1f))
            }

            // الراس (ينزل من فوق + يكبر + يهز)
            val h = head.value
            val hs = h.coerceAtLeast(0.001f)
            withTransform({
                translate(0f, (1f - h.coerceIn(0f, 1.2f)) * -40f)
                rotate(nod.value, Offset(253f, 265f))
                scale(hs, hs, Offset(253f, 166f))
            }) {
                drawCircle(
                    color = iconColor,
                    radius = 66f,
                    center = Offset(253f, 166f),
                    alpha = h.coerceIn(0f, 1f)
                )
            }
        }
    }
}
