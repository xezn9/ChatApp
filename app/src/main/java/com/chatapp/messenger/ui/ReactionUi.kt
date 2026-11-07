package com.chatapp.messenger.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** ايموجيات التفاعل (تطلع بالضغطة المطولة) */
internal val REACTION_EMOJIS = listOf("❤️", "👍", "😂", "😮", "😢", "🔥")

/** كل الايموجيات (تطلع بزر +) */
internal val ALL_EMOJIS: List<String> = (
    "😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😙 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔 " +
    "🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 🤥 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🤧 🥵 🥶 🥴 😵 🤯 🤠 🥳 😎 🤓 " +
    "🧐 😕 😟 🙁 ☹️ 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 " +
    "👿 💀 ☠️ 💩 🤡 👹 👺 👻 👽 👾 🤖 🙈 🙉 🙊 💋 💌 💘 💝 💖 💗 💓 💞 💕 💟 ❣️ 💔 ❤️ 🧡 💛 " +
    "💚 💙 💜 🤎 🖤 🤍 💯 💢 💥 💫 💦 💨 🕳️ 💬 💭 💤 👋 🤚 🖐️ ✋ 🖖 👌 🤌 🤏 ✌️ 🤞 🤟 🤘 🤙 " +
    "👈 👉 👆 👇 ☝️ 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 👐 🤲 🤝 🙏 ✍️ 💅 🤳 💪 🦾 👀 👁️ 👅 👄 🧠 🫀 " +
    "🔥 ✨ ⭐ 🌟 ⚡ ☀️ 🌈 ☁️ ❄️ 🌹 🌸 🍀 🎉 🎊 🎈 🎁 🏆 🥇 ⚽ 🏀 🎮 🎵 🎶 🎤 🍕 🍔 🍟 🍩 🍫 " +
    "☕ 🍉 🍓 🐶 🐱 🐼 🦁 🐸 🦋 🌙 🌍 🚀 💎 👑 💸 📌 ✅ ❌ ⚠️ ❓ ❗ 💡 🔔 🎯 🫡 🫶 🥹 🫠 🤝"
    ).split(" ").filter { it.isNotBlank() }.distinct()

/** تفاعل الضغطتين */
internal const val HEART = "❤️"

/**
 * شارة التفاعلات تحت الفقاعة.
 * - ظهور: تنبثق بنطة نابضية (تكبير مع ارتداد)
 * - تغيير الايموجي: الايموجي القديم ينكمش والجديد ينبثق
 * - اختفاء: تنكمش وتتلاشى بنعومة
 */
@Composable
internal fun ReactionBadge(
    reactions: Map<String, String>,
    dark: Boolean,
    fg: Color,
    modifier: Modifier = Modifier
) {
    // نحتفظ بآخر حالة غير فاضية حتى تكمل حركة الاختفاء
    var last by remember { mutableStateOf(reactions) }
    if (reactions.isNotEmpty()) last = reactions
    val shown = last
    AnimatedVisibility(
        visible = reactions.isNotEmpty(),
        modifier = modifier,
        enter = scaleIn(spring(dampingRatio = 0.42f, stiffness = 520f), initialScale = 0.15f) + fadeIn(tween(120)),
        exit = scaleOut(tween(190), targetScale = 0.15f) + fadeOut(tween(170))
    ) {
        val emojis = shown.values.groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.map { it.key }.take(3)
        Row(
            Modifier.clip(CircleShape)
                .background(if (dark) Color(0xFF2A2F3A) else Color.White)
                .border(0.8.dp, fg.copy(alpha = 0.15f), CircleShape)
                .padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedContent(
                targetState = emojis,
                transitionSpec = {
                    (scaleIn(spring(0.4f, 560f), initialScale = 0.3f) + fadeIn(tween(100))) togetherWith
                        (scaleOut(tween(130), targetScale = 0.3f) + fadeOut(tween(110)))
                },
                label = "reactionEmojis"
            ) { list ->
                Text(list.joinToString(""), fontSize = 13.sp)
            }
            if (shown.size > 1) {
                Spacer(Modifier.width(3.dp))
                Text("${shown.size}", fontSize = 11.sp, color = fg.copy(alpha = 0.8f))
            }
        }
    }
}

/** قلب (أو ايموجي) كبير ينبض وسط الفقاعة عند الضغطتين: يطلع بارتداد ثم يطير للأعلى ويتلاشى */
@Composable
internal fun BoxScope.ReactionBurst(p: Float, emoji: String = HEART) {
    if (p <= 0f || p >= 1f) return
    Text(
        emoji,
        fontSize = 56.sp,
        modifier = Modifier.align(Alignment.Center).graphicsLayer {
            val sc = if (p < 0.3f) 0.3f + (p / 0.3f) * 1.0f else 1.3f - (p - 0.3f) * 0.3f
            scaleX = sc; scaleY = sc
            alpha = if (p < 0.6f) 1f else (1f - (p - 0.6f) / 0.4f).coerceIn(0f, 1f)
            translationY = -p * 18.dp.toPx()
            transformOrigin = TransformOrigin.Center
        }
    )
}

/**
 * زر ايموجي بصف التفاعلات: يطلع بالتتابع بارتداد، ويكبر عند الضغط.
 * [onDoubleClick]: الضغطتين على اي ايموجي يلغي تفاعلي (اذا انعطت)
 */
@Composable
internal fun ReactionPick(
    emoji: String,
    selected: Boolean,
    delayMs: Long,
    fg: Color,
    animate: Boolean = true,
    onDoubleClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val appear = remember { Animatable(if (animate) 0f else 1f) }
    if (animate) {
        LaunchedEffect(Unit) {
            delay(delayMs)
            appear.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 380f))
        }
    }
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(if (pressed) 1.4f else 1f, spring(0.5f, 500f), label = "reactionPress")
    Box(
        Modifier.size(42.dp)
            .graphicsLayer {
                val s = appear.value * press
                scaleX = s; scaleY = s
                alpha = appear.value.coerceIn(0f, 1f)
                translationY = (1f - appear.value.coerceIn(0f, 1f)) * 14.dp.toPx()
            }
            .clip(CircleShape)
            .background(if (selected) fg.copy(alpha = 0.18f) else Color.Transparent)
            .pointerInput(onDoubleClick) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onDoubleTap = onDoubleClick?.let { cb -> { _: androidx.compose.ui.geometry.Offset -> cb() } },
                    onTap = { onClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = 24.sp)
    }
}
