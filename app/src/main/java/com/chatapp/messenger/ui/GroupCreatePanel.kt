package com.chatapp.messenger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/**
 * نافذة إنشاء مجموعة (فارغة مؤقتاً) بحركة "تحوّل الزر":
 * تبدأ من نفس مكان وحجم زر إنشاء مجموعة (دائرة 56dp)، تتمدد للأعلى وباتجاه الداخل بنبضة نابضية
 * وهي ضبابية (بلور) وتتوضح تدريجياً، وعند الإغلاق ترجع بنفس الحركة معكوسة وتنكمش داخل الزر.
 *
 * [progress] يُملك من الخارج حتى الزر يقدر يختفي أثناء الحركة ويرجع بنفس اللحظة الي تنتهي فيها النافذة.
 * 0 = مطوية على الزر، 1 = مفتوحة بالكامل.
 *
 * لازم توضع بعد الزر بنفس الـ Box (فوقه) وبنفس هوامش الزر (end=20dp, bottom=112dp).
 */
@Composable
fun GroupCreatePanel(
    progress: Animatable<Float, AnimationVector1D>,
    open: Boolean,
    backdrop: Backdrop,
    dark: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {}
) {
    LaunchedEffect(open) {
        if (open) {
            // فتح: نبضة نابضية خفيفة (تتجاوز 1 شوي ثم تستقر)
            progress.animateTo(1f, spring(dampingRatio = 0.80f, stiffness = 300f))
        } else {
            // إغلاق: بدون ارتداد حتى ما ينزل الحجم تحت حجم الزر
            progress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 380f))
        }
    }

    val p = progress.value
    // ما نرسم شي إذا النافذة مطوية بالكامل ومغلقة (الزر الأصلي هو الظاهر)
    if (p <= 0.001f && !open) return

    val pc = p.coerceIn(0f, 1f)
    val interaction = remember { MutableInteractionSource() }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val targetW = minOf(maxWidth - 40.dp, 340.dp)
        val targetH = 380.dp
        val w = lerp(56.dp, targetW, p).coerceAtLeast(56.dp)
        val h = lerp(56.dp, targetH, p).coerceAtLeast(56.dp)
        val corner = lerp(28.dp, 34.dp, pc)
        val shape = RoundedCornerShape(corner)

        // تعتيم خفيف للخلفية، ضغطة عليه تغلق النافذة
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.30f * pc))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = open,
                    onClick = onDismiss
                )
        )

        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 112.dp)
                .size(w, h)
                .graphicsLayer {
                    // ضبابية تبدأ قوية وتختفي مع الفتح (وترجع مع الإغلاق) — أندرويد 12+ فقط، وغيره يتجاهلها
                    val b = (1f - pc) * 18.dp.toPx()
                    if (b > 0.5f) renderEffect = BlurEffect(b, b)
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(16f.dp.toPx())
                        lens(12f.dp.toPx(), 24f.dp.toPx())
                    },
                    onDrawSurface = {
                        drawRect(if (dark) Color.Black.copy(0.38f) else Color.White.copy(0.50f))
                    }
                )
                .border(
                    0.8.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(0.45f), Color.White.copy(0.08f))),
                    shape
                )
                // يمنع الضغط داخل النافذة من إغلاقها
                .clickable(interactionSource = interaction, indication = null) {}
        ) {
            // أيقونة الزر تذوب بأول الحركة (وتظهر بآخر الإغلاق) بنفس مكان الزر
            Box(
                Modifier.align(Alignment.BottomEnd).size(56.dp)
                    .graphicsLayer { alpha = (1f - pc * 4f).coerceIn(0f, 1f) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    AppIcons.GroupAdd, null,
                    tint = if (dark) Color.White else Color.Black,
                    modifier = Modifier.size(26.dp)
                )
            }
            // المحتوى (فارغ مؤقتاً) يظهر متأخراً شوي عن حركة التمدد
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = ((pc - 0.55f) / 0.45f).coerceIn(0f, 1f) },
                content = content
            )
        }
    }
}
