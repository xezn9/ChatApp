package com.chatapp.messenger.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.ui.glass.GlassButton
import com.kyant.backdrop.Backdrop

/**
 * زر "حفظ" متحرك (Liquid Glass):
 *  - جاهز: علامة ✓ تنبض بهدوء + لمعة تمر على الزر كل كم ثانية
 *  - جاري الحفظ: ✓ تتحول لدائرة تلف، والنص ينزلق لـ "جاري الحفظ" والزر يتمدد بنعومة
 *  - تم: الدائرة تتحول لعلامة ✓ ترسم نفسها بحركة نطّة، واللون يتحول للأخضر
 *
 * [canSave] = البيانات صالحة للحفظ، [saving] = قيد الحفظ، [done] = انحفظ بنجاح.
 */
@Composable
internal fun AnimatedSaveButton(
    backdrop: Backdrop,
    canSave: Boolean,
    saving: Boolean,
    done: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    // 0 = جاهز، 1 = جاري الحفظ، 2 = تم
    val state = if (done) 2 else if (saving) 1 else 0
    val busy = saving || done

    val tint by animateColorAsState(if (done) Color(0xFF34C759) else AccentBlue, tween(380), label = "saveTint")

    // ---- حركات مستمرة (نقراها داخل draw/graphicsLayer فقط حتى ما نعيد التركيب كل فريم) ----
    val inf = rememberInfiniteTransition(label = "saveInf")
    // اللمعة: تمر بأول ~1.1 ثانية وبعدها استراحة
    val shimmer = inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes { durationMillis = 3400; 0f at 0 using FastOutSlowInEasing; 1f at 1100; 1f at 3400 }
        ),
        label = "shimmer"
    )
    val pulse = inf.animateFloat(
        initialValue = 1f, targetValue = 1.16f,
        animationSpec = infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val spin = inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "spin"
    )
    val sweep = inf.animateFloat(
        initialValue = 50f, targetValue = 250f,
        animationSpec = infiniteRepeatable(tween(750, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sweep"
    )

    // ---- نجاح: رسم علامة ✓ + نطّة ----
    val checkDraw by animateFloatAsState(if (done) 1f else 0f, tween(420, easing = FastOutSlowInEasing), label = "checkDraw")
    val pop = remember { Animatable(1f) }
    LaunchedEffect(done) {
        if (done) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            pop.snapTo(0.55f)
            pop.animateTo(1f, spring(0.32f, 520f))
        }
    }

    GlassButton(
        backdrop = backdrop,
        tint = tint,
        enabled = canSave || busy,          // أثناء الحفظ ما نخفّت الزر (نخليه واضح بحركته)
        pressScale = 1.08f,
        onClick = { if (canSave && !busy) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() } },
        modifier = modifier.height(48.dp).drawWithContent {
            drawContent()
            if (state == 0 && canSave) {
                // لمعة قطرية تمر فوق الزر (مقصوصة على شكل الكبسولة)
                val w = size.width
                val band = w * 0.5f
                val x = -band + (w + band * 2f) * shimmer.value
                val capsule = Path().apply {
                    addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(size.height / 2f)))
                }
                clipPath(capsule) {
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.42f), Color.Transparent),
                            start = Offset(x, 0f), end = Offset(x + band, size.height * 0.7f)
                        )
                    )
                }
            }
        }
    ) {
        Spacer(Modifier.width(18.dp))
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.fillMaxSize().graphicsLayer {
                    val s = when (state) { 0 -> pulse.value; 2 -> pop.value; else -> 1f }
                    scaleX = s; scaleY = s
                }
            ) {
                val stroke = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val w = size.width
                val h = size.height
                if (state == 1) {
                    // دائرة التحميل: قوس يلف ويكبر ويصغر
                    val inset = stroke.width / 2f + 1.dp.toPx()
                    drawArc(
                        Color.White.copy(alpha = 0.25f), 0f, 360f, false,
                        Offset(inset, inset), Size(w - inset * 2, h - inset * 2), style = stroke
                    )
                    drawArc(
                        Color.White, spin.value, sweep.value, false,
                        Offset(inset, inset), Size(w - inset * 2, h - inset * 2), style = stroke
                    )
                } else {
                    // علامة ✓: بالنجاح ترسم نفسها تدريجياً
                    val full = Path().apply {
                        moveTo(w * 0.18f, h * 0.54f)
                        lineTo(w * 0.42f, h * 0.76f)
                        lineTo(w * 0.82f, h * 0.28f)
                    }
                    val p = if (state == 2) checkDraw else 1f
                    val shown = if (p >= 1f) full else Path().also { seg ->
                        val pm = PathMeasure().apply { setPath(full, false) }
                        pm.getSegment(0f, pm.length * p, seg, true)
                    }
                    drawPath(shown, Color.White, style = stroke)
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                (slideInVertically(spring(0.8f, 500f)) { it / 2 } + fadeIn(tween(180))) togetherWith
                    (slideOutVertically(tween(160)) { -it / 2 } + fadeOut(tween(120)))
            },
            label = "saveText"
        ) { s ->
            Text(
                when (s) { 0 -> "حفظ"; 1 -> "جاري الحفظ"; else -> "تم الحفظ" },
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(20.dp))
    }
}
