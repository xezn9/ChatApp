package com.chatapp.messenger.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.Msg
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import java.io.File
import kotlin.math.max

private val VBlue = Color(0xFF1AA7FF)
private val VRed = Color(0xFFE5484D)

/** مدة الضغط قبل ما يبدأ التسجيل ("ضغطة مطولة قليلاً") */
private const val HOLD_MS = 280L

/** يحوّل ميلي ثانية إلى 0:07 */
fun voiceTime(ms: Long): String {
    val s = (ms / 1000).toInt().coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** كبسولة زجاجية بنفس ستايل مربع الكتابة */
private fun Modifier.voiceGlass(backdrop: Backdrop, dark: Boolean): Modifier = this
    .drawBackdrop(
        backdrop = backdrop,
        shape = { Capsule() },
        effects = {
            vibrancy()
            blur(2f.dp.toPx())
            lens(12f.dp.toPx(), 24f.dp.toPx())
        },
        onDrawSurface = { drawRect(if (dark) Color.Black.copy(0.30f) else Color.White.copy(0.35f)) }
    )
    .border(
        0.8.dp,
        Brush.verticalGradient(listOf(Color.White.copy(0.40f), Color.White.copy(0.06f))),
        RoundedCornerShape(50)
    )

/**
 * زر المايك الزجاجي.
 * - ضغطة سريعة = [onTap] (تلميح)
 * - ضغطة مطولة (~0.3 ثانية) = [onHoldStart] (يبدأ التسجيل)، ولما ترفع إصبعك = [onHoldEnd]
 * أثناء التسجيل يكبر ويصير أحمر.
 */
@Composable
fun GlassMicButton(
    backdrop: Backdrop,
    recording: Boolean,
    enabled: Boolean,
    onTap: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (recording) VRed else VBlue
    val scale by animateFloatAsState(if (recording) 1.3f else 1f, spring(0.5f, 400f), label = "micScale")
    val tap by rememberUpdatedState(onTap)
    val start by rememberUpdatedState(onHoldStart)
    val end by rememberUpdatedState(onHoldEnd)
    val on by rememberUpdatedState(enabled)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .alpha(if (enabled) 1f else 0.5f)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(tint, blendMode = BlendMode.Hue)
                    drawRect(tint.copy(alpha = 0.75f))
                }
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    if (!on) {
                        waitForUpOrCancellation()
                        return@awaitEachGesture
                    }
                    var released = false
                    val up = withTimeoutOrNull(HOLD_MS) {
                        val u = waitForUpOrCancellation()
                        released = true
                        u
                    }
                    if (released) {
                        // رفع إصبعه قبل وقت الضغطة المطولة = ضغطة عادية
                        if (up != null) tap()
                    } else {
                        // ضغطة مطولة: يسجّل لين يرفع إصبعه (حتى لو طلع إصبعه برا الزر)
                        start()
                        while (true) {
                            val ev = awaitPointerEvent()
                            ev.changes.forEach { it.consume() }
                            if (ev.changes.all { !it.pressed }) break
                        }
                        end()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(AppIcons.Mic, null, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

/** شريط يظهر أثناء التسجيل: نقطة حمرا تنبض + الوقت + موجة حيّة */
@Composable
fun VoiceRecordingBar(
    backdrop: Backdrop,
    dark: Boolean,
    fg: Color,
    ms: Long,
    levels: List<Float>,
    modifier: Modifier = Modifier
) {
    val t = rememberInfiniteTransition(label = "recDot")
    val a by t.animateFloat(1f, 0.2f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "recDotAlpha")
    Row(
        modifier.heightIn(min = 52.dp).voiceGlass(backdrop, dark).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(11.dp).graphicsLayer { alpha = a }.clip(CircleShape).background(VRed))
        Text(voiceTime(ms), color = fg, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        LiveBars(levels, VBlue, Modifier.weight(1f).height(28.dp))
    }
}

/** موجة حيّة أثناء التسجيل: آخر مستويات الصوت، الأحدث على اليمين */
@Composable
private fun LiveBars(levels: List<Float>, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val n = 40
        val gap = 2.dp.toPx()
        val bw = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
        val offset = n - levels.size
        for (i in 0 until n) {
            val idx = i - offset
            val lv = if (idx >= 0 && idx < levels.size) levels[idx] else 0f
            val bh = max(size.height * (0.12f + 0.88f * lv), bw)
            drawRoundRect(
                color = if (idx >= 0) color else color.copy(alpha = 0.25f),
                topLeft = Offset(i * (bw + gap), (size.height - bh) / 2f),
                size = Size(bw, bh),
                cornerRadius = CornerRadius(bw / 2f)
            )
        }
    }
}

/**
 * موجة التسجيل (أعمدة). [wave] نص أرقام 1..9، [progress] من 0 إلى 1.
 * إذا [onSeek] موجود تگدر تضغط أو تسحب عليها للقفز لمكان بالتسجيل.
 */
@Composable
fun VoiceWave(
    wave: String,
    progress: Float,
    active: Color,
    inactive: Color,
    modifier: Modifier = Modifier,
    onSeek: ((Float) -> Unit)? = null
) {
    val src = wave.ifBlank { "3".repeat(VoiceUtil.WAVE_BARS) }
    val seek by rememberUpdatedState(onSeek)
    var m = modifier
    if (onSeek != null) {
        m = m
            .pointerInput(Unit) {
                detectTapGestures { off -> seek?.invoke(off.x / size.width) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    seek?.invoke(change.position.x / size.width)
                }
            }
    }
    Canvas(m) {
        val n = src.length
        val gap = 2.dp.toPx()
        val bw = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
        val cut = progress * n
        for (i in 0 until n) {
            val lv = (src[i] - '0').coerceIn(0, 9) / 9f
            val bh = max(size.height * (0.15f + 0.85f * lv), bw)
            drawRoundRect(
                color = if (i < cut) active else inactive,
                topLeft = Offset(i * (bw + gap), (size.height - bh) / 2f),
                size = Size(bw, bh),
                cornerRadius = CornerRadius(bw / 2f)
            )
        }
    }
}

/**
 * شريط المراجعة بعد التسجيل وقبل الإرسال: [حذف] [تشغيل/إيقاف] [موجة (تگدر تسحب عليها)] [الوقت].
 * زر الإرسال يكون خارج الشريط (بمكان زر الإرسال العادي).
 */
@Composable
fun VoiceReviewBar(
    backdrop: Backdrop,
    dark: Boolean,
    fg: Color,
    file: File?,
    durMs: Long,
    wave: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val id = VoiceUtil.PREVIEW_ID
    val active = VoicePlayer.playingId == id
    val playing = active && VoicePlayer.isPlaying
    val total = if (active && VoicePlayer.durationMs > 0L) VoicePlayer.durationMs else durMs
    val pos = if (active) VoicePlayer.positionMs else 0L
    val progress = if (total > 0L) (pos.toFloat() / total).coerceIn(0f, 1f) else 0f
    Row(
        modifier.heightIn(min = 52.dp).voiceGlass(backdrop, dark).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(VRed.copy(alpha = 0.16f)).clickable { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Delete, null, tint = VRed, modifier = Modifier.size(22.dp))
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(VBlue)
                .clickable(enabled = file != null) { if (file != null) VoicePlayer.toggle(id, file) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (playing) AppIcons.Pause else Icons.Filled.PlayArrow, null,
                tint = Color.White, modifier = Modifier.size(26.dp)
            )
        }
        VoiceWave(
            wave = wave, progress = progress,
            active = VBlue, inactive = fg.copy(alpha = 0.3f),
            modifier = Modifier.weight(1f).height(30.dp),
            onSeek = { f -> if (file != null) VoicePlayer.seekTo(id, file, f) }
        )
        Text(
            voiceTime(if (active) pos else durMs),
            color = fg.copy(alpha = 0.85f), fontSize = 13.sp,
            modifier = Modifier.padding(end = 8.dp)
        )
    }
}

/** فقاعة رسالة صوتية: زر تشغيل/إيقاف + موجة تتلوّن أثناء التشغيل + المدة */
@Composable
fun VoiceBubble(m: Msg, textColor: Color, onToggle: () -> Unit, onLongPress: () -> Unit) {
    val active = VoicePlayer.playingId == m.id
    val playing = active && VoicePlayer.isPlaying
    val total = m.mediaDur * 1000L
    val dur = if (active && VoicePlayer.durationMs > 0L) VoicePlayer.durationMs else total
    val pos = if (active) VoicePlayer.positionMs else 0L
    val progress = if (dur > 0L) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    Row(
        Modifier.width(220.dp)
            .pointerInput(m.id) { detectTapGestures(onTap = { onToggle() }, onLongPress = { onLongPress() }) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(textColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (playing) AppIcons.Pause else Icons.Filled.PlayArrow, null,
                tint = textColor, modifier = Modifier.size(26.dp)
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            VoiceWave(
                wave = m.wave, progress = progress,
                active = textColor, inactive = textColor.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth().height(28.dp)
            )
            Text(
                voiceTime(if (active) pos else total),
                color = textColor.copy(alpha = 0.75f), fontSize = 11.sp
            )
        }
    }
}
