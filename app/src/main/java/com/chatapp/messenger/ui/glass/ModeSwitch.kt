package com.chatapp.messenger.ui.glass

import com.chatapp.messenger.ui.theme.AppFont

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import com.chatapp.messenger.ui.theme.isAppDark
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule

/**
 * مبدّل المحادثات/المجموعات: تصميم مستقل (مو نفس الشريط السفلي).
 * - كبسولة صغيرة وناعمة، الأيقونة جنب النص بصف أفقي (مو فوقه)
 * - مؤشر ملوّن متدرّج ينزلق بين الخيارين (بدل العدسة الزجاجية الكبيرة)
 * - يشتغل باللمس وبالسحب الأفقي
 */
@Composable
fun ModeSwitch(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    backdrop: Backdrop,
    items: List<Pair<Painter, String>>,
    modifier: Modifier = Modifier
) {
    val isLight = !isAppDark()
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val count = items.size

    val trackColor = if (isLight) Color.White.copy(0.30f) else Color.Black.copy(0.28f)
    val borderColor = if (isLight) Color.Black.copy(0.07f) else Color.White.copy(0.12f)
    val accentA = Color(0xFF3AA0FF)
    val accentB = Color(0xFF0A6CFF)
    val inactive = if (isLight) Color.Black.copy(0.62f) else Color.White.copy(0.70f)

    val pos by animateFloatAsState(
        selectedIndex.toFloat(),
        spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "modeSwitchPos"
    )

    Box(
        modifier
            .height(44.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(6f.dp.toPx())
                },
                onDrawSurface = { drawRect(trackColor) }
            )
            .border(1.dp, borderColor, CircleShape)
            .pointerInput(count, isLtr, selectedIndex) {
                var acc = 0f
                detectHorizontalDragGestures(
                    onDragStart = { acc = 0f },
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        acc += dx
                    },
                    onDragEnd = {
                        val step = size.width / count / 3f
                        // اتجاه "التالي" يعتمد على اتجاه الواجهة
                        val forward = if (isLtr) acc > step else acc < -step
                        val backward = if (isLtr) acc < -step else acc > step
                        when {
                            forward && selectedIndex < count - 1 -> onSelected(selectedIndex + 1)
                            backward && selectedIndex > 0 -> onSelected(selectedIndex - 1)
                        }
                    }
                )
            }
            .padding(4.dp)
    ) {
        // المؤشر المنزلق
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(1f / count)
                .fillMaxHeight()
                .graphicsLayer {
                    // العرض الكامل للمؤشر = عرض خانة واحدة
                    translationX = (if (isLtr) 1f else -1f) * pos * size.width
                }
                .shadow(8.dp, CircleShape, ambientColor = accentB, spotColor = accentB)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(accentA, accentB)))
        )

        // الخانات (النص والأيقونة)
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            items.forEachIndexed { index, (painter, label) ->
                val selected = index == selectedIndex
                val tint by animateColorAsState(
                    if (selected) Color.White else inactive,
                    label = "modeSwitchTint$index"
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelected(index) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier
                                .size(18.dp)
                                .paint(painter, colorFilter = ColorFilter.tint(tint))
                        )
                        BasicText(
                            label,
                            style = TextStyle(fontFamily = AppFont, color = tint,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }
}
