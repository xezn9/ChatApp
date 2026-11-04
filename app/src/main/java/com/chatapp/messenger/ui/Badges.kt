package com.chatapp.messenger.ui

import com.chatapp.messenger.ui.theme.AppFont

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.Profile
import com.chatapp.messenger.Repo
import com.chatapp.messenger.ui.theme.isAppDark
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop

// ============================================================================
//  رموز الاسم المتحركة (مثل حالة الإيموجي بتيليجرام): تظهر جنب اسمك عند كل الناس.
//  الرمز يتخزن بـ profiles/<uid>.badge (معرّف نصي)، والرسم والحركة كلها من الكود.
// ============================================================================

internal enum class BadgeAnim { PULSE, SPIN, BOUNCE, SWING, FLICKER, FLASH, FLOAT, SHAKE, BEAT, FLAP, BLINK, SHIMMER }

internal class Pal(val light: Color, val mid: Color, val dark: Color)

private val Silver = Pal(Color(0xFFFFFFFF), Color(0xFFA7B0BE), Color(0xFF5B6472))
private val Gold = Pal(Color(0xFFFFF1A8), Color(0xFFF5B301), Color(0xFF9A6700))
private val Blood = Pal(Color(0xFFFF9A9A), Color(0xFFE11D2A), Color(0xFF6B0010))
private val Ice = Pal(Color(0xFFE6FAFF), Color(0xFF38BDF8), Color(0xFF075985))
private val Violet = Pal(Color(0xFFF3E8FF), Color(0xFFA855F7), Color(0xFF581C87))
private val Rose = Pal(Color(0xFFFFE4F1), Color(0xFFF43F9D), Color(0xFF831843))
private val Mint = Pal(Color(0xFFE0FFF4), Color(0xFF10D9A0), Color(0xFF065F46))

/** kind: shape = شكل مرسوم بالكود بتدرج معدني ولمعة، glyph = رمز نصي بتدرج معدني، emoji = إيموجي متحرك */
internal class BadgeDef(val id: String, val kind: String, val data: String, val pal: Pal, val anim: BadgeAnim)

internal object Badges {
    val metal: List<BadgeDef>
    val emoji: List<BadgeDef>
    val all: List<BadgeDef>

    init {
        val m = mutableListOf<BadgeDef>()
        fun shape(id: String, sh: String, p: Pal, a: BadgeAnim = BadgeAnim.SHIMMER) = m.add(BadgeDef(id, "shape", sh, p, a))
        fun glyph(id: String, g: String, p: Pal, a: BadgeAnim = BadgeAnim.SHIMMER) = m.add(BadgeDef(id, "glyph", g, p, a))

        // صلبان (مثل الي بالصورة)
        shape("x_gothic_silver", "gothic", Silver)
        shape("x_gothic_gold", "gothic", Gold)
        shape("x_gothic_blood", "gothic", Blood)
        shape("x_gothic_ice", "gothic", Ice)
        shape("x_gothic_violet", "gothic", Violet)
        shape("x_round_silver", "round", Silver)
        shape("x_round_gold", "round", Gold)
        shape("x_round_rose", "round", Rose)
        shape("x_sword_silver", "sword", Silver)
        shape("x_sword_blood", "sword", Blood)
        shape("x_sword_violet", "sword", Violet)
        shape("x_sword_gold", "sword", Gold, BadgeAnim.FLOAT)
        // نجوم ولمعات
        shape("x_spark_gold", "spark", Gold, BadgeAnim.SPIN)
        shape("x_spark_silver", "spark", Silver, BadgeAnim.SPIN)
        shape("x_spark_rose", "spark", Rose, BadgeAnim.PULSE)
        shape("x_spark_ice", "spark", Ice, BadgeAnim.PULSE)
        // جواهر
        shape("x_gem_ice", "gem", Ice)
        shape("x_gem_rose", "gem", Rose, BadgeAnim.PULSE)
        shape("x_gem_mint", "gem", Mint, BadgeAnim.PULSE)
        shape("x_gem_violet", "gem", Violet)
        // قلوب
        shape("x_heart_blood", "heart", Blood, BadgeAnim.BEAT)
        shape("x_heart_rose", "heart", Rose, BadgeAnim.BEAT)
        shape("x_heart_violet", "heart", Violet, BadgeAnim.BEAT)
        shape("x_heart_ice", "heart", Ice, BadgeAnim.BEAT)
        // تيجان وصواعق وأقمار
        shape("x_crown_gold", "crown", Gold, BadgeAnim.BOUNCE)
        shape("x_crown_silver", "crown", Silver, BadgeAnim.BOUNCE)
        shape("x_crown_violet", "crown", Violet, BadgeAnim.BOUNCE)
        shape("x_bolt_gold", "bolt", Gold, BadgeAnim.FLASH)
        shape("x_bolt_ice", "bolt", Ice, BadgeAnim.FLASH)
        shape("x_bolt_violet", "bolt", Violet, BadgeAnim.FLASH)
        shape("x_moon_silver", "moon", Silver, BadgeAnim.SWING)
        shape("x_moon_gold", "moon", Gold, BadgeAnim.SWING)
        // رموز نصية معدنية
        glyph("g_fleur_gold", "⚜", Gold)
        glyph("g_fleur_silver", "⚜", Silver)
        glyph("g_star_silver", "✦", Silver, BadgeAnim.SPIN)
        glyph("g_star_gold", "✦", Gold, BadgeAnim.SPIN)
        glyph("g_star_ice", "✧", Ice, BadgeAnim.PULSE)
        glyph("g_dia_violet", "❖", Violet, BadgeAnim.SPIN)
        glyph("g_dia_rose", "❖", Rose, BadgeAnim.SPIN)
        glyph("g_skull_silver", "☠", Silver, BadgeAnim.SWING)
        glyph("g_skull_blood", "☠", Blood, BadgeAnim.SWING)
        glyph("g_swords_silver", "⚔", Silver, BadgeAnim.SHAKE)
        glyph("g_swords_gold", "⚔", Gold, BadgeAnim.SHAKE)
        glyph("g_ankh_gold", "☥", Gold)
        glyph("g_ankh_silver", "☥", Silver)
        glyph("g_trident_ice", "♆", Ice, BadgeAnim.BOUNCE)
        glyph("g_queen_gold", "♛", Gold, BadgeAnim.BOUNCE)
        glyph("g_queen_violet", "♛", Violet, BadgeAnim.BOUNCE)
        metal = m

        val e = mutableListOf<BadgeDef>()
        fun em(id: String, g: String, a: BadgeAnim) = e.add(BadgeDef("e_$id", "emoji", g, Silver, a))
        em("crown", "👑", BadgeAnim.BOUNCE); em("diamond", "💎", BadgeAnim.PULSE); em("fire", "🔥", BadgeAnim.FLICKER)
        em("bolt", "⚡", BadgeAnim.FLASH); em("skull", "💀", BadgeAnim.SWING); em("ghost", "👻", BadgeAnim.FLOAT)
        em("moon", "🌙", BadgeAnim.SWING); em("sun", "☀️", BadgeAnim.SPIN); em("heart", "❤️", BadgeAnim.BEAT)
        em("purple", "💜", BadgeAnim.BEAT); em("blue", "💙", BadgeAnim.BEAT); em("black", "🖤", BadgeAnim.BEAT)
        em("eagle", "🦅", BadgeAnim.FLOAT); em("wolf", "🐺", BadgeAnim.SHAKE); em("butterfly", "🦋", BadgeAnim.FLAP)
        em("rose", "🌹", BadgeAnim.SWING); em("star", "⭐", BadgeAnim.SPIN); em("glow", "🌟", BadgeAnim.PULSE)
        em("rocket", "🚀", BadgeAnim.FLOAT); em("music", "🎧", BadgeAnim.BOUNCE); em("game", "🎮", BadgeAnim.SHAKE)
        em("eye", "👁️", BadgeAnim.BLINK); em("devil", "😈", BadgeAnim.SHAKE); em("wilted", "🥀", BadgeAnim.SWING)
        em("blossom", "🌸", BadgeAnim.SPIN); em("clover", "🍀", BadgeAnim.SPIN); em("nazar", "🧿", BadgeAnim.PULSE)
        em("crossbones", "☠️", BadgeAnim.SWING); em("trident", "🔱", BadgeAnim.BOUNCE); em("dove", "🕊️", BadgeAnim.FLOAT)
        em("scorpion", "🦂", BadgeAnim.SHAKE); em("dragon", "🐉", BadgeAnim.FLOAT); em("planet", "🪐", BadgeAnim.SPIN)
        em("dizzy", "💫", BadgeAnim.SPIN); em("masks", "🎭", BadgeAnim.SWING); em("swords", "⚔️", BadgeAnim.SHAKE)
        em("shield", "🛡️", BadgeAnim.PULSE); em("trophy", "🏆", BadgeAnim.BOUNCE); em("lion", "🦁", BadgeAnim.PULSE)
        em("snake", "🐍", BadgeAnim.SWING); em("bat", "🦇", BadgeAnim.FLAP); em("spider", "🕷️", BadgeAnim.BOUNCE)
        em("cat", "😼", BadgeAnim.SWING); em("alien", "👽", BadgeAnim.FLOAT); em("robot", "🤖", BadgeAnim.SHAKE)
        em("rainbow", "🌈", BadgeAnim.PULSE); em("snow", "❄️", BadgeAnim.SPIN); em("wave", "🌊", BadgeAnim.SWING)
        em("tornado", "🌪️", BadgeAnim.SPIN); em("comet", "☄️", BadgeAnim.FLOAT); em("cherry", "🍒", BadgeAnim.BOUNCE)
        em("strawberry", "🍓", BadgeAnim.PULSE); em("pizza", "🍕", BadgeAnim.SWING); em("coffee", "☕", BadgeAnim.FLOAT)
        em("sparkles", "✨", BadgeAnim.FLASH); em("bomb", "💣", BadgeAnim.SHAKE); em("ring", "💍", BadgeAnim.PULSE)
        em("key", "🗝️", BadgeAnim.SWING); em("bell", "🔔", BadgeAnim.SHAKE); em("peace", "✌️", BadgeAnim.SWING)
        em("clown", "🤡", BadgeAnim.BOUNCE); em("cool", "😎", BadgeAnim.PULSE); em("hundred", "💯", BadgeAnim.PULSE)
        emoji = e
        all = m + e
    }

    fun byId(id: String): BadgeDef? = if (id.isBlank()) null else all.firstOrNull { it.id == id }
}

private fun specFor(a: BadgeAnim): InfiniteRepeatableSpec<Float> = when (a) {
    BadgeAnim.PULSE -> infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    BadgeAnim.SPIN -> infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart)
    BadgeAnim.BOUNCE -> infiniteRepeatable(tween(480, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    BadgeAnim.SWING -> infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    BadgeAnim.FLICKER -> infiniteRepeatable(tween(230, easing = LinearEasing), RepeatMode.Reverse)
    BadgeAnim.FLASH -> infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse)
    BadgeAnim.FLOAT -> infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    BadgeAnim.FLAP -> infiniteRepeatable(tween(360, easing = LinearEasing), RepeatMode.Reverse)
    BadgeAnim.SHAKE -> infiniteRepeatable(keyframes {
        durationMillis = 1500
        0.5f at 0; 0f at 70; 1f at 140; 0f at 210; 1f at 280; 0.5f at 350; 0.5f at 1500
    })
    BadgeAnim.BEAT -> infiniteRepeatable(keyframes {
        durationMillis = 1100
        0f at 0; 1f at 140; 0f at 300; 0.75f at 440; 0f at 620; 0f at 1100
    })
    BadgeAnim.BLINK -> infiniteRepeatable(keyframes {
        durationMillis = 2600
        0f at 0; 0f at 2350; 1f at 2450; 0f at 2550; 0f at 2600
    })
    BadgeAnim.SHIMMER -> infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart)
}

private fun shapePath(name: String, s: Float): Path {
    val p = Path()
    fun poly(vararg v: Float) {
        p.moveTo(v[0] * s, v[1] * s)
        var i = 2
        while (i < v.size) { p.lineTo(v[i] * s, v[i + 1] * s); i += 2 }
        p.close()
    }
    when (name) {
        "gothic" -> poly(
            0.35f, 0.02f, 0.65f, 0.02f, 0.575f, 0.18f, 0.575f, 0.31f, 0.84f, 0.31f, 0.98f, 0.22f, 0.98f, 0.52f,
            0.84f, 0.43f, 0.575f, 0.43f, 0.575f, 0.82f, 0.66f, 0.98f, 0.34f, 0.98f, 0.425f, 0.82f, 0.425f, 0.43f,
            0.16f, 0.43f, 0.02f, 0.52f, 0.02f, 0.22f, 0.16f, 0.31f, 0.425f, 0.31f, 0.425f, 0.18f
        )
        "round" -> {
            p.addRoundRect(androidx.compose.ui.geometry.RoundRect(0.42f * s, 0.08f * s, 0.58f * s, 0.92f * s, androidx.compose.ui.geometry.CornerRadius(0.05f * s)))
            p.addRoundRect(androidx.compose.ui.geometry.RoundRect(0.1f * s, 0.28f * s, 0.9f * s, 0.44f * s, androidx.compose.ui.geometry.CornerRadius(0.05f * s)))
            listOf(0.5f to 0.1f, 0.5f to 0.9f, 0.12f to 0.36f, 0.88f to 0.36f).forEach { (x, y) ->
                p.addOval(androidx.compose.ui.geometry.Rect(Offset(x * s, y * s), 0.1f * s))
            }
        }
        "sword" -> poly(
            0.5f, 0.0f, 0.58f, 0.12f, 0.58f, 0.60f, 0.84f, 0.60f, 0.84f, 0.68f, 0.58f, 0.68f, 0.58f, 0.86f, 0.64f, 0.90f,
            0.64f, 0.98f, 0.36f, 0.98f, 0.36f, 0.90f, 0.42f, 0.86f, 0.42f, 0.68f, 0.16f, 0.68f, 0.16f, 0.60f, 0.42f, 0.60f, 0.42f, 0.12f
        )
        "spark" -> {
            p.moveTo(0.5f * s, 0f)
            p.quadraticBezierTo(0.5f * s, 0.5f * s, s, 0.5f * s)
            p.quadraticBezierTo(0.5f * s, 0.5f * s, 0.5f * s, s)
            p.quadraticBezierTo(0.5f * s, 0.5f * s, 0f, 0.5f * s)
            p.quadraticBezierTo(0.5f * s, 0.5f * s, 0.5f * s, 0f)
            p.close()
        }
        "gem" -> poly(0.2f, 0.2f, 0.8f, 0.2f, 0.98f, 0.42f, 0.5f, 0.96f, 0.02f, 0.42f)
        "heart" -> {
            p.moveTo(0.5f * s, 0.92f * s)
            p.cubicTo(0.0f * s, 0.55f * s, 0.02f * s, 0.1f * s, 0.28f * s, 0.1f * s)
            p.cubicTo(0.42f * s, 0.1f * s, 0.5f * s, 0.22f * s, 0.5f * s, 0.28f * s)
            p.cubicTo(0.5f * s, 0.22f * s, 0.58f * s, 0.1f * s, 0.72f * s, 0.1f * s)
            p.cubicTo(0.98f * s, 0.1f * s, 1.0f * s, 0.55f * s, 0.5f * s, 0.92f * s)
            p.close()
        }
        "crown" -> poly(0.05f, 0.85f, 0.0f, 0.25f, 0.25f, 0.5f, 0.5f, 0.1f, 0.75f, 0.5f, 1.0f, 0.25f, 0.95f, 0.85f)
        "bolt" -> poly(0.6f, 0.0f, 0.15f, 0.55f, 0.45f, 0.55f, 0.35f, 1.0f, 0.88f, 0.4f, 0.55f, 0.4f)
        "moon" -> {
            val a = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(0.5f * s, 0.5f * s), 0.44f * s)) }
            val b = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(0.7f * s, 0.4f * s), 0.36f * s)) }
            p.op(a, b, PathOperation.Difference)
        }
    }
    return p
}

/** الرمز المتحرك لمعرّف معيّن. إذا المعرّف فاضي أو غير معروف ما يرسم شي. */
@Composable
internal fun BadgeView(id: String, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    if (id.startsWith("s:")) { StickerBadgeView(id, size, modifier); return }
    val d = remember(id) { Badges.byId(id) } ?: return
    val t = rememberInfiniteTransition(label = "badge")
    val p by t.animateFloat(0f, 1f, specFor(d.anim), label = "badgeP")
    val sh by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "badgeSh")
    val sp = with(LocalDensity.current) { (size * 0.82f).toSp() }

    Box(
        modifier.size(size).graphicsLayer {
            val px = this.size.width
            when (d.anim) {
                BadgeAnim.PULSE -> { val sc = 0.88f + 0.24f * p; scaleX = sc; scaleY = sc }
                BadgeAnim.SPIN -> rotationZ = p * 360f
                BadgeAnim.BOUNCE -> translationY = -px * 0.18f * p
                BadgeAnim.SWING -> rotationZ = -16f + 32f * p
                BadgeAnim.FLICKER -> { scaleY = 0.92f + 0.18f * p; scaleX = 1.04f - 0.08f * p; alpha = 0.78f + 0.22f * p }
                BadgeAnim.FLASH -> { alpha = 0.4f + 0.6f * p; val sc = 0.9f + 0.2f * p; scaleX = sc; scaleY = sc }
                BadgeAnim.FLOAT -> translationY = px * 0.14f * (p - 0.5f) * 2f
                BadgeAnim.SHAKE -> rotationZ = -9f + 18f * p
                BadgeAnim.BEAT -> { val sc = 1f + 0.28f * p; scaleX = sc; scaleY = sc }
                BadgeAnim.FLAP -> scaleX = 1f - 0.65f * p
                BadgeAnim.BLINK -> scaleY = 1f - 0.9f * p
                BadgeAnim.SHIMMER -> {}
            }
        },
        contentAlignment = Alignment.Center
    ) {
        when (d.kind) {
            "shape" -> Canvas(Modifier.size(size)) {
                val s = this.size.minDimension
                val path = shapePath(d.data, s)
                drawPath(path, Brush.verticalGradient(listOf(d.pal.light, d.pal.mid, d.pal.dark)))
                drawPath(path, d.pal.light.copy(0.85f), style = Stroke(s * 0.035f, join = StrokeJoin.Round))
                val x = (-0.5f + 2f * sh) * s
                clipPath(path) {
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(0.9f), Color.Transparent),
                            start = Offset(x, 0f), end = Offset(x + 0.4f * s, 0.4f * s)
                        )
                    )
                }
            }
            "glyph" -> {
                val w = with(LocalDensity.current) { size.toPx() }
                val off = sh * w * 2f
                Text(
                    d.data,
                    style = TextStyle(fontFamily = AppFont, brush = Brush.linearGradient(
                            listOf(d.pal.dark, d.pal.light, d.pal.mid, d.pal.light, d.pal.dark),
                            start = Offset(off, 0f), end = Offset(off + w, w), tileMode = TileMode.Mirror
                        ),
                        fontSize = sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                    )
                )
            }
            else -> Text(d.data, fontSize = sp, textAlign = TextAlign.Center)
        }
    }
}

/** اسم + الرمز المتحرك جنبه (للاستعمال السريع) */
@Composable
internal fun BadgeAfterName(profile: Profile, size: Dp = 18.dp) {
    if (profile.badge.isNotBlank()) {
        var preview by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        Spacer(Modifier.width(5.dp))
        // ضغطة مطولة على الرمز = استعراض كبير (الضغطة العادية تبقى للأب، مثلاً فتح اختيار الرمز)
        BadgeView(
            profile.badge, size,
            Modifier.onLongPressPreview {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                preview = true
            }
        )
        if (preview) BadgePreviewDialog(profile.badge) { preview = false }
    }
}
