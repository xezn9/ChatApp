package com.chatapp.messenger.ui

import com.chatapp.messenger.ui.theme.AppFont

import com.chatapp.messenger.ui.theme.GlassFx
import com.chatapp.messenger.ui.theme.glassSurfaceLayer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.ui.glass.GlassButton
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/** لون النص المناسب للوضع (فاتح/غامق) فوق الزجاج */
fun glassFg(dark: Boolean): Color = if (dark) Color.White else Color(0xFF101418)

/** حدّ زجاجي ناعم (نفس حدود أشرطة المحادثة) */
fun glassBorderBrush(strength: Float = 0.40f): Brush =
    Brush.verticalGradient(listOf(Color.White.copy(strength), Color.White.copy(0.06f)))

/**
 * سطح Liquid Glass حقيقي (backdrop + blur + lens) مع حدّ زجاجي.
 * يُستعمل لبطاقات شاشة معلومات المجموعة.
 */
fun Modifier.glassSurface(
    backdrop: Backdrop,
    dark: Boolean,
    shape: Shape = RoundedCornerShape(24.dp),
    blurDp: Float = 8f
): Modifier = this
    .drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur((blurDp.dp.toPx()) * GlassFx.blurK)
            lens((10f.dp.toPx()) * GlassFx.refractK, (20f.dp.toPx()) * GlassFx.refractK)
        },
        onDrawSurface = { glassSurfaceLayer { drawRect(if (dark) Color.Black.copy(0.32f) else Color.White.copy(0.45f)) } }
    )
    .border(0.8.dp, glassBorderBrush(), shape)

/**
 * نافذة Liquid Glass عامة بمنتصف الشاشة (بدل AlertDialog).
 * [content] تنرسم داخل عمود قابل للتمرير، ولون النص الافتراضي مضبوط حسب الوضع.
 * [confirmText] = null يعني بدون زر تأكيد (زر إغلاق فقط).
 */
@Composable
fun GlassDialog(
    backdrop: Backdrop,
    dark: Boolean,
    title: String,
    onDismiss: () -> Unit,
    confirmText: String? = null,
    confirmEnabled: Boolean = true,
    confirmColor: Color = AccentBlue,
    dismissText: String = "إلغاء",
    onConfirm: () -> Unit = {},
    // clear = زجاج صافي: ضبابية قليلة + انكسار أقوى + سطح شفاف (بدل الزجاج الكثيف)
    clear: Boolean = false,
    content: @Composable () -> Unit
) {
    val fg = glassFg(dark)
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) }
    val shape = RoundedCornerShape(32.dp)

    BackHandler(onBack = onDismiss)

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = enter.value.coerceIn(0f, 1f) }
            .background(Color.Black.copy(alpha = if (clear) 0.28f else 0.40f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(24.dp).widthIn(max = 380.dp).fillMaxWidth()
                .graphicsLayer {
                    val s = 0.78f + 0.22f * enter.value
                    scaleX = s; scaleY = s
                    alpha = enter.value.coerceIn(0f, 1f)
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(((if (clear) 3f else 16f).dp.toPx()) * GlassFx.blurK)
                        lens(((if (clear) 18f else 12f).dp.toPx()) * GlassFx.refractK, ((if (clear) 36f else 24f).dp.toPx()) * GlassFx.refractK)
                    },
                    onDrawSurface = {
                        glassSurfaceLayer {
                            if (clear) {
                                drawRect(if (dark) Color.White.copy(0.08f) else Color.White.copy(0.30f))
                                // لمعة خفيفة من فوق (انعكاس الزجاج)
                                drawRect(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        listOf(Color.White.copy(0.10f), Color.Transparent), endY = size.height * 0.5f
                                    )
                                )
                            } else {
                                drawRect(if (dark) Color.Black.copy(0.42f) else Color.White.copy(0.55f))
                            }
                        }
                    }
                )
                .border(0.8.dp, glassBorderBrush(if (clear) 0.65f else 0.45f), shape)
                // يمنع الضغط داخل النافذة من إغلاقها
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp)
        ) {
            CompositionLocalProvider(LocalContentColor provides fg) {
                Text(title, color = fg, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Start)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f, fill = false).heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    content()
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton(
                        onClick = onDismiss, backdrop = backdrop, pressScale = 1.05f,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text(if (confirmText == null) "إغلاق" else dismissText, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                    if (confirmText != null) {
                        GlassButton(
                            onClick = onConfirm, backdrop = backdrop, tint = confirmColor,
                            enabled = confirmEnabled, pressScale = 1.05f,
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text(confirmText, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}

/** حقل كتابة بشكل زجاجي خفيف (بدل OutlinedTextField) */
@Composable
fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    fg: Color,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 4
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier.fillMaxWidth().clip(shape)
            .background(fg.copy(alpha = 0.10f))
            .border(0.8.dp, glassBorderBrush(0.30f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (value.isEmpty()) Text(placeholder, color = fg.copy(alpha = 0.5f), fontSize = 15.sp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            textStyle = TextStyle(fontFamily = AppFont, color = fg, fontSize = 16.sp),
            cursorBrush = SolidColor(AccentBlue),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
