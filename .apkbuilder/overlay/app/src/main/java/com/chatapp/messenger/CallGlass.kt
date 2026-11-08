package com.chatapp.messenger

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.ui.theme.GlassFx
import com.chatapp.messenger.ui.theme.glassSurfaceLayer
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

// ============================================================================
//  مكونات Liquid Glass المشتركة لشاشات المكالمات (الفردية والجماعية)
// ============================================================================

internal val CallGreen = Color(0xFF34C759)
internal val CallRed = Color(0xFFE5484D)
internal val CallTeal = Color(0xFF34D5C4)

/**
 * خلفية المكالمة: شفق متحرك (بقع ألوان تسبح ببطء + كرات ضوء صغيرة).
 * هذي الطبقة هي المصدر الي يكسره الزجاج (layerBackdrop) فكل ما تتحرك تتحرك انعكاسات الأزرار معها.
 * [glow] يزيد توهج الألوان لما أحد يحچي، [red] يصبغها بالأحمر عند انتهاء المكالمة.
 */
@Composable
internal fun CallAurora(modifier: Modifier = Modifier, glow: Float = 0f, red: Float = 0f) {
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(15000, easing = LinearEasing), RepeatMode.Reverse), label = "a"
    )
    val b by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(21000, easing = LinearEasing), RepeatMode.Reverse), label = "b"
    )
    val c by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(27000, easing = LinearEasing), RepeatMode.Reverse), label = "c"
    )
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF150B35), Color(0xFF08183A), Color(0xFF040813))))

        fun blob(color: Color, cx: Float, cy: Float, r: Float, alpha: Float) {
            drawCircle(
                Brush.radialGradient(
                    listOf(color.copy(alpha = alpha.coerceIn(0f, 1f)), Color.Transparent),
                    center = Offset(cx, cy), radius = r
                ),
                radius = r, center = Offset(cx, cy)
            )
        }
        blob(Color(0xFF7B4DFF), w * (0.12f + 0.40f * a), h * (0.10f + 0.22f * b), w * 0.95f, 0.55f + 0.25f * glow)
        blob(Color(0xFF19D3C5), w * (0.92f - 0.45f * b), h * (0.88f - 0.28f * a), w * 1.00f, 0.42f + 0.25f * glow)
        blob(Color(0xFFFF4DA6), w * (0.85f - 0.55f * c), h * (0.28f + 0.34f * c), w * 0.65f, 0.30f)
        blob(Color(0xFF2E7BFF), w * (0.15f + 0.35f * c), h * (0.72f - 0.22f * b), w * 0.75f, 0.32f)
        if (red > 0.01f) blob(Color(0xFFFF3B3B), w * 0.5f, h * 0.45f, w * 1.15f, 0.42f * red)

        // كرات ضوء صغيرة: تعطي الزجاج شي يكسره بوضوح
        drawCircle(Color.White.copy(alpha = 0.10f), w * 0.09f, Offset(w * (0.18f + 0.50f * b), h * (0.30f + 0.08f * a)))
        drawCircle(Color(0xFF8CF5E8).copy(alpha = 0.14f), w * 0.06f, Offset(w * (0.80f - 0.45f * a), h * (0.58f + 0.10f * c)))
        drawCircle(Color(0xFFFFB3E6).copy(alpha = 0.12f), w * 0.07f, Offset(w * (0.30f + 0.40f * c), h * (0.80f - 0.12f * b)))
        drawCircle(Color.White.copy(alpha = 0.08f), w * 0.045f, Offset(w * (0.70f - 0.30f * b), h * (0.18f + 0.12f * c)))
    }
}

/** سطح زجاجي (Liquid Glass) بأي شكل: لوحات، بطاقات، أشرطة. لازم يكون خارج طبقة layerBackdrop */
internal fun Modifier.callGlass(
    backdrop: Backdrop,
    shape: Shape,
    surface: Color = Color.White.copy(alpha = 0.10f),
    blurDp: Float = 14f
): Modifier = this
    .drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur((blurDp.dp.toPx()) * GlassFx.blurK)
            lens((12f.dp.toPx()) * GlassFx.refractK, (24f.dp.toPx()) * GlassFx.refractK)
        },
        onDrawSurface = { glassSurfaceLayer { drawRect(surface) } }
    )
    .border(
        0.8.dp,
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.50f), Color.White.copy(alpha = 0.06f))),
        shape
    )

/** نوع حركة الأيقونة عند الضغط */
internal enum class ButtonFx {
    /** نبضة تكبير سريعة (كتم، سماعة، أعضاء) */
    POP,

    /** الهاتف يتأرجح ويرجع بنطّة (إنهاء، رفض) */
    SWING,

    /** الهاتف يهتز باستمرار + هالة نابضة (رد) */
    WIGGLE,

    NONE
}

/**
 * زر مكالمة زجاجي. كل زر له حركته:
 *  - دخول متدرّج (ينطّ من تحت بنابض) حسب [enterIndex]
 *  - ضغط: ينضغط ويرجع بارتداد + موجة ضوء تتوسع + اهتزاز خفيف
 *  - الأيقونة تتبدل بتكبير/تصغير ناعم (كتم ↔ فتح المايك)
 *  - [active] يعبّي الزجاج أبيض بانتقال سلس (زر مفعّل)
 *  - [fx] حركة خاصة للأيقونة
 */
@Composable
internal fun GlassCallButton(
    backdrop: Backdrop,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 68.dp,
    tint: Color = Color.Unspecified,
    active: Boolean = false,
    iconRotate: Float = 0f,
    fx: ButtonFx = ButtonFx.POP,
    enterIndex: Int = 0,
    badge: Int = 0,
    showLabel: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(0.45f, 520f), label = "press")
    val activeA by animateFloatAsState(if (active) 1f else 0f, tween(280), label = "active")
    val kick = remember { Animatable(0f) }
    val ripple = remember { Animatable(0f) }
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(enterIndex * 70L)
        enter.animateTo(1f, spring(0.62f, 280f))
    }

    val inf = rememberInfiniteTransition(label = "btnfx")
    val breathe by inf.animateFloat(
        1f, 1.06f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "br"
    )
    val wiggle by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "wg")
    val halo by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1700, easing = LinearEasing)), label = "halo")

    val ringColor = if (tint.isSpecified) tint else Color.White
    val iconTint = lerp(Color.White, Color(0xFF0B0F1A), activeA)

    Column(
        modifier.graphicsLayer {
            val e = enter.value
            alpha = e.coerceIn(0f, 1f)
            translationY = (1f - e) * 56.dp.toPx()
            val s = 0.55f + 0.45f * e
            scaleX = s; scaleY = s
        },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(size)
                    .graphicsLayer {
                        val base = if (fx == ButtonFx.WIGGLE) breathe else 1f
                        scaleX = base - 0.10f * press
                        scaleY = base - 0.15f * press
                    }
                    .drawBehind {
                        val r = this.size.minDimension / 2f
                        // هالة نابضة مستمرة لزر الرد
                        if (fx == ButtonFx.WIGGLE) {
                            for (i in 0..1) {
                                val p = (halo + i * 0.5f) % 1f
                                drawCircle(
                                    ringColor.copy(alpha = (1f - p) * 0.40f),
                                    radius = r * (1f + 0.65f * p),
                                    style = Stroke(2.dp.toPx())
                                )
                            }
                        }
                        // موجة الضغط
                        val rp = ripple.value
                        if (rp > 0.001f && rp < 0.999f) {
                            drawCircle(
                                ringColor.copy(alpha = (1f - rp) * 0.55f),
                                radius = r * (1f + 0.85f * rp),
                                style = Stroke(2.5.dp.toPx())
                            )
                            drawCircle(ringColor.copy(alpha = (1f - rp) * 0.14f), radius = r * (1f + 0.55f * rp))
                        }
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { CircleShape },
                        effects = {
                            vibrancy()
                            blur((2f.dp.toPx()) * GlassFx.blurK)
                            lens((12f.dp.toPx()) * GlassFx.refractK, (24f.dp.toPx()) * GlassFx.refractK)
                        },
                        onDrawSurface = {
                            if (tint.isSpecified) {
                                drawRect(tint, blendMode = BlendMode.Hue)
                                drawRect(tint.copy(alpha = 0.72f))
                            } else {
                                drawRect(Color.White.copy(alpha = 0.14f + 0.70f * activeA))
                            }
                        }
                    )
                    .border(
                        0.8.dp,
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.08f))),
                        CircleShape
                    )
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch { ripple.snapTo(0f); ripple.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
                        scope.launch {
                            kick.snapTo(0f)
                            kick.animateTo(1f, tween(110))
                            kick.animateTo(0f, spring(0.32f, 260f))
                        }
                        onClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = icon,
                    transitionSpec = {
                        (fadeIn(tween(180)) + scaleIn(spring(0.5f, 500f), initialScale = 0.3f)) togetherWith
                            (fadeOut(tween(110)) + scaleOut(targetScale = 0.3f))
                    },
                    label = "icon"
                ) { ic ->
                    Icon(
                        ic, null, tint = iconTint,
                        modifier = Modifier
                            .size(size * 0.44f)
                            .graphicsLayer {
                                val k = kick.value
                                var rot = iconRotate
                                var sc = 1f
                                when (fx) {
                                    ButtonFx.POP -> sc = 1f + 0.40f * k
                                    ButtonFx.SWING -> { rot = iconRotate - 45f * k; sc = 1f + 0.10f * k }
                                    ButtonFx.WIGGLE -> {
                                        // اهتزاز لمدة 45% من الدورة ثم سكون
                                        if (wiggle < 0.45f) {
                                            val p = wiggle / 0.45f
                                            rot += sin(p * 2.0 * PI * 3.0).toFloat() * 16f * (1f - 0.3f * p)
                                        }
                                        rot += 25f * k
                                        sc = 1f + 0.25f * k
                                    }
                                    ButtonFx.NONE -> {}
                                }
                                rotationZ = rot
                                scaleX = sc; scaleY = sc
                            }
                    )
                }
            }
            if (badge > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-2).dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(CallTeal),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = badge,
                        transitionSpec = {
                            (fadeIn(tween(120)) + scaleIn(spring(0.4f, 500f), initialScale = 0.3f)) togetherWith
                                (fadeOut(tween(90)) + scaleOut(targetScale = 0.3f))
                        },
                        label = "badge"
                    ) { n ->
                        Text(n.toString(), color = Color(0xFF06231F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(8.dp))
            Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
        }
    }
}

/** زر زجاجي صغير (للإجراءات داخل القوائم) مع ضغطة نابضية */
@Composable
internal fun GlassMiniButton(
    backdrop: Backdrop,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.82f else 1f, spring(0.4f, 500f), label = "mini")
    Box(
        modifier
            .size(size)
            .graphicsLayer { scaleX = s; scaleY = s }
            .callGlass(backdrop, CircleShape, Color.White.copy(alpha = 0.12f), blurDp = 6f)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/**
 * صورة المتصل: حلقات تتوسع أثناء الرنين، وحلقة صوت لما يحچي، ودخول نابضي.
 * الحجم الكلي أكبر من [d] حتى تتسع الحلقات.
 */
@Composable
internal fun CallAvatar(
    avatar: android.graphics.Bitmap?,
    name: String,
    d: Dp,
    ringing: Boolean,
    modifier: Modifier = Modifier,
    level: Float = 0f,
    ended: Boolean = false
) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(0.55f, 220f)) }
    val ringA by animateFloatAsState(if (ringing) 1f else 0f, tween(400), label = "ringA")
    val lvl by animateFloatAsState(level, tween(140), label = "avLvl")
    val endScale by animateFloatAsState(if (ended) 0.9f else 1f, spring(0.6f, 300f), label = "endS")
    val t = rememberInfiniteTransition(label = "avatar")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "p")
    val breathe by t.animateFloat(
        1f, 1.025f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b"
    )

    Box(
        modifier
            .size(d * 1.9f)
            .graphicsLayer {
                val e = enter.value
                val s = (0.5f + 0.5f * e) * endScale
                scaleX = s; scaleY = s
                alpha = e.coerceIn(0f, 1f) * (if (ended) 0.75f else 1f)
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = d.toPx() / 2f
            if (ringA > 0.01f) {
                for (i in 0..2) {
                    val q = (p + i / 3f) % 1f
                    drawCircle(
                        Color.White.copy(alpha = (1f - q) * 0.32f * ringA),
                        radius = r * (1.04f + 0.80f * q),
                        style = Stroke(2.dp.toPx())
                    )
                }
                drawCircle(Color.White.copy(alpha = 0.05f * ringA), radius = r * 1.25f)
            }
            if (lvl > 0.02f) {
                drawCircle(CallTeal.copy(alpha = 0.20f * lvl), radius = r * (1.15f + 0.25f * lvl))
                drawCircle(CallTeal.copy(alpha = 0.9f * lvl), radius = r * 1.07f, style = Stroke(2.5.dp.toPx()))
            }
        }
        Box(
            Modifier
                .size(d)
                .graphicsLayer { scaleX = breathe; scaleY = breathe }
                .clip(CircleShape)
                .background(Color(0xFF2B3550))
                .border(
                    2.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.12f))),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (avatar != null) {
                Image(avatar.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(
                    name.trim().take(1), color = Color.White,
                    fontSize = (d.value * 0.4f).sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** شارة زجاجية صغيرة (كبسولة) بأعلى الشاشة */
@Composable
internal fun GlassPill(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(0.7f, 260f)) }
    Row(
        modifier
            .graphicsLayer {
                val e = enter.value
                alpha = e.coerceIn(0f, 1f)
                translationY = (e - 1f) * 30.dp.toPx()
            }
            .callGlass(backdrop, RoundedCornerShape(50), Color.White.copy(alpha = 0.10f), blurDp = 8f)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

/** نقطة حيّة تنبض (مؤشر أن المكالمة شغالة) */
@Composable
internal fun LiveDot(color: Color = CallGreen, d: Dp = 8.dp) {
    val t = rememberInfiniteTransition(label = "dot")
    val a by t.animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a"
    )
    Box(Modifier.size(d).graphicsLayer { alpha = a }.clip(CircleShape).background(color))
}

internal fun formatCallTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    else "%02d:%02d".format(s / 60, s % 60)
}
