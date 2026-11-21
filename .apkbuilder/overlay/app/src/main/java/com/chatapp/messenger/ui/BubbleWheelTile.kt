package com.chatapp.messenger.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.animation.OvershootInterpolator
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import kotlin.math.max

private val SinkOvershoot = Easing { OvershootInterpolator(2.2f).getInterpolation(it) }

/**
 * بطاقة فقاعة داخل [ItemWheel]. نفس بطاقة HyperSpring (featureTile):
 * أساس داكن بحد رفيع + دائرة لون تنفتح من نقطة اللمس عند الاختيار (Spot، 620ms Power4Out وتنغلق 420ms Power3Out)
 * + حد بلون الأكسنت يطلع لما الدائرة تقارب تمتلي + انضغاط 0.96 عند اللمس يرجع بارتداد + موجة انكماش على الباقي.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BubbleWheelTile(
    style: BubbleStyle,
    selected: Boolean,
    index: Int,
    wheel: WheelState,
    accent: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(20.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = if (pressed) tween(140, easing = WheelEase) else tween(380, easing = SinkOvershoot),
        label = "sink"
    )
    val spotT by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(if (selected) 620 else 420, easing = if (selected) WheelPower4Out else WheelPower3Out),
        label = "spot"
    )
    // الحد يوصل لما الدائرة تقارب تمتلي (نفس clamp01((v-0.55)/0.45))
    val edgeA = ((spotT - 0.55f) / 0.45f).coerceIn(0f, 1f)
    var touch by remember { mutableStateOf(Offset.Unspecified) }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(Color(0xFF1C2733).copy(alpha = 0.75f))
            .drawBehind {
                if (spotT <= 0f) return@drawBehind
                val c = if (touch.isSpecified) touch else Offset(size.width / 2f, size.height / 2f)
                // نصف قطر يوصل لأبعد زاوية: مع t = 1 البطاقة تمتلي
                val far = hypot(max(c.x, size.width - c.x), max(c.y, size.height - c.y))
                drawCircle(accent.copy(alpha = 0x12 / 255f), radius = far * spotT, center = c)
            }
            .border(0.8.dp, Color.White.copy(alpha = 0x17 / 255f), shape)
            .border(1.dp, accent.copy(alpha = 0x66 / 255f * edgeA), shape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val d = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touch = d.position
                }
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    if (!selected) {
                        view.performHapticFeedback(
                            if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.TOGGLE_ON
                            else HapticFeedbackConstants.KEYBOARD_TAP
                        )
                        wheel.ripple(index)
                    }
                    onClick()
                },
                onLongClick = onLongClick?.let { lc ->
                    {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        lc()
                    }
                }
            )
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.weight(1.5f), contentAlignment = Alignment.Center) {
                StyledBubble(
                    style, mine = false, dark = true, fg = Color.White, backdrop = null,
                    // بدون هذا الفقاعات الي بيها شكلين (يمين ويسار) ينضغط النص بمربع المعاينة الضيّق
                    modifier = Modifier.wrapContentWidth(unbounded = true)
                ) { BubbleSampleText("مرحبا", it) }
            }
            Text(
                style.name, fontSize = 14.sp, textAlign = TextAlign.Center,
                color = if (selected) accent else Color.White.copy(alpha = 0.85f),
                modifier = Modifier.weight(1f)
            )
        }
    }
}
