package com.chatapp.messenger.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import com.chatapp.messenger.ui.theme.isAppDark
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.chatapp.messenger.ui.theme.appBackground

/** true = شاشة كاملة تغطي الرئيسية، فالحركات الخلفية الدائمة توقف مؤقتاً */
val LocalAmbientPaused = staticCompositionLocalOf { false }

/** ألوان كل تبويب: Home / BDF / Account. تتبدل بسلاسة عند التنقل */
private val TabBlobs get() = listOf(
    listOf(Color(0xFF1E4FD8), Color(0xFF2B6CB0), Color(0xFF3B4FA8)), // Home (درجات أزرق هادئة)
    listOf(Color(0xFFFF5C8A), Color(0xFFFFB347), Color(0xFF8A5CFF)), // BDF
    listOf(Color(0xFF2EE6A8), AccentBlue, Color(0xFFFFD166)),  // Account
    listOf(Color(0xFF4D7CFF), Color(0xFFB45CFF), Color(0xFFFF5C8A)), // Settings (معاينة الافتراضية فقط)
    listOf(Color(0xFFB45CFF), Color(0xFF3B6BFF), Color(0xFF00D4AA))  // Activity
)

/**
 * خلفية الواجهات الرئيسية: بقع ضوء ملونة تتحرك ببطء (نفس روح شاشة الدخول)
 * ولكل تبويب لون مختلف. توضع داخل layerBackdrop حتى يكسرها الزجاج.
 */
@Composable
fun AnimatedAppBackground(tab: Int, modifier: Modifier = Modifier) {
    val dark = isAppDark()
    val set = TabBlobs[tab.coerceIn(0, TabBlobs.lastIndex)]
    val c1 by animateColorAsState(set[0], tween(900), label = "b1")
    val c2 by animateColorAsState(set[1], tween(900), label = "b2")
    val c3 by animateColorAsState(set[2], tween(900), label = "b3")
    // ساعة خاصة بدل InfiniteTransition: توقف تماماً لما شاشة كاملة (محادثة/إعدادات) تغطي الرئيسية،
    // حتى ما تنرسم الخلفية وتنعاد معالجتها كل فريم وراها بدون ما أحد يشوفها. تكمل من نفس المكان.
    // نفس الحركة القديمة بالضبط: ذهاب وإياب خطي بدورة 18 ثانية لكل اتجاه.
    val paused by androidx.compose.runtime.rememberUpdatedState(LocalAmbientPaused.current)
    var phase by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        var clock = 0f
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last != 0L && !paused) {
                    clock = (clock + (now - last) / 1_000_000_000f) % 36f
                    phase = if (clock < 18f) clock / 18f else (36f - clock) / 18f
                }
                last = now
            }
        }
    }
    val a = (if (dark) 0.42f else 0.50f) * (if (tab == 0) 0.8f else 1f)

    Box(
        modifier.background(appBackground()).drawBehind {
            fun blob(color: Color, cx: Float, cy: Float, r: Float) = drawBlob(color, cx, cy, r)
            val w = size.width
            val h = size.height
            blob(c1.copy(alpha = a), w * (0.10f + 0.35f * phase), h * (0.10f + 0.08f * phase), w * 0.75f)
            blob(c2.copy(alpha = a), w * (0.95f - 0.35f * phase), h * (0.45f + 0.10f * phase), w * 0.70f)
            blob(c3.copy(alpha = a), w * (0.20f + 0.30f * phase), h * (0.98f - 0.12f * phase), w * 0.80f)
        }
    )
}

private fun DrawScope.drawBlob(color: Color, cx: Float, cy: Float, r: Float) {
    drawCircle(
        brush = Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(cx, cy), radius = r),
        radius = r,
        center = Offset(cx, cy)
    )
}
