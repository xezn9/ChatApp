package com.chatapp.messenger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import kotlin.math.PI
import kotlin.math.sin

/** ثلاث نقاط تقفز بالتتابع (مثل تيك توك) */
@Composable
fun TypingDots(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typingDots")
    val phase = transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "typingPhase"
    )
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        val local = ((phase.value - i * 0.16f) % 1f + 1f) % 1f
                        val h = if (local < 0.5f) sin(local / 0.5f * PI.toFloat()) else 0f
                        translationY = -h * 5.dp.toPx()
                        alpha = 0.45f + 0.55f * h
                        val sc = 1f + 0.15f * h
                        scaleX = sc; scaleY = sc
                    }
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/** ظهور ناعم لصف "يكتب": يطلع من تحت بتكبير نابضي (مثل الرسالة الجديدة) */
@Composable
fun Modifier.typingAppear(): Modifier {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(0.72f, 380f)) }
    return this.graphicsLayer {
        val v = appear.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * 24.dp.toPx()
        val sc = 0.85f + 0.15f * v
        scaleX = sc; scaleY = sc
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
    }
}

/** رسالة "يكتب": نفس فقاعة الشخص (نفس الشكل والستايل) وداخلها ثلاث نقاط تقفز مثل تيك توك */
@Composable
fun TypingMessageBubble(
    style: BubbleStyle,
    dark: Boolean,
    fg: Color,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier
) {
    StyledBubble(style = style, mine = false, dark = dark, fg = fg, backdrop = backdrop, modifier = modifier) { textColor ->
        // نفس ارتفاع سطر نص عادي حتى تطلع الفقاعة بنفس حجم رسالة قصيرة
        Box(Modifier.height(24.dp).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
            TypingDots(textColor.copy(alpha = 0.8f))
        }
    }
}

/** نص مختصر للمجموعات: "علي يكتب" / "علي و سارة يكتبون" / "علي و 3 آخرين يكتبون" */
fun typingLabel(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> "${names[0]} يكتب"
    2 -> "${names[0]} و ${names[1]} يكتبون"
    else -> "${names[0]} و ${names.size - 1} آخرين يكتبون"
}
