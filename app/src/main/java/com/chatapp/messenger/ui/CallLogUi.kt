package com.chatapp.messenger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.CallIcons
import kotlinx.coroutines.delay

// ============================================================================
//  سجل المكالمات داخل المحادثة (مكالمة فائتة / مرفوضة).
//  يُرسل كرسالة نص عادية بصيغة  [[call:<النوع>]]  (نفس فكرة الملصقات)
//  فما يحتاج أي تعديل بقواعد قاعدة البيانات، وينعرض ككرت مكالمة بدل الفقاعة.
//  الي يكتبه هو المتصل: لو المستلم ما رد يطلع عند الطرفين.
// ============================================================================
internal object ChatCallLog {
    private const val PREFIX = "[[call:"
    private const val SUFFIX = "]]"

    /** مكالمة فردية فاتت (ما رد / المتصل لغى / مشغول) */
    const val MISSED = "missed"

    /** مكالمة فردية رفضها المستلم */
    const val DECLINED = "declined"

    /** مكالمة جماعية ما انضم لها أحد */
    const val GROUP_MISSED = "gmissed"

    private val KINDS = setOf(MISSED, DECLINED, GROUP_MISSED)

    fun encode(kind: String): String = PREFIX + kind + SUFFIX

    /** يرجع نوع السجل إذا النص رسالة مكالمة، وإلا null */
    fun parse(text: String): String? {
        if (!text.startsWith(PREFIX) || !text.endsWith(SUFFIX)) return null
        val k = text.substring(PREFIX.length, text.length - SUFFIX.length)
        return if (k in KINDS) k else null
    }

    /** نص مختصر للقوائم والإشعارات والردود (null = مو رسالة مكالمة) */
    fun preview(text: String): String? = when (parse(text)) {
        MISSED -> "📞 مكالمة فائتة"
        DECLINED -> "📞 مكالمة مرفوضة"
        GROUP_MISSED -> "📞 مكالمة جماعية فائتة"
        else -> null
    }
}

/**
 * كرت المكالمة الفائتة داخل المحادثة.
 * - عند الطرف الي فاتته المكالمة: أحمر، الأيقونة تهتز عند الظهور، وزر اتصال بحلقة نابضة.
 * - عند المتصل: رمادي هادئ "مكالمة صادرة • ما رد".
 * الضغط على الكرت = اتصال مرة ثانية (أو انضمام للمكالمة الجماعية).
 */
@Composable
internal fun CallLogCard(
    kind: String,
    mine: Boolean,
    dark: Boolean,
    fg: Color,
    onCallBack: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val group = kind == ChatCallLog.GROUP_MISSED
    val missedForMe = !mine
    val accent = if (missedForMe) Color(0xFFFF5A5F) else fg.copy(alpha = 0.75f)
    val title = when {
        group && mine -> "مكالمة جماعية"
        group -> "مكالمة جماعية فائتة"
        mine -> "مكالمة صادرة"
        kind == ChatCallLog.DECLINED -> "مكالمة مرفوضة"
        else -> "مكالمة فائتة"
    }
    val sub = when {
        group && mine -> "ما انضم أحد"
        group -> "اضغط للانضمام"
        mine && kind == ChatCallLog.DECLINED -> "رفض المكالمة"
        mine -> "ما رد"
        else -> "اضغط للاتصال مرة ثانية"
    }

    val cb = rememberUpdatedState(onCallBack)
    val lp = rememberUpdatedState(onLongPress)
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(if (pressed) 0.95f else 1f, spring(0.5f, 500f), label = "logPress")

    // اهتزاز الأيقونة مرة وحدة عند الظهور (للمكالمة الفائتة فقط)
    val shake = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (missedForMe) {
            delay(300)
            repeat(3) {
                shake.animateTo(1f, tween(70))
                shake.animateTo(-1f, tween(70))
            }
            shake.animateTo(0f, spring(0.5f, 400f))
        }
    }
    val inf = rememberInfiniteTransition(label = "logPulse")
    val pulse by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "p")

    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .widthIn(min = 210.dp)
            .graphicsLayer { scaleX = press; scaleY = press }
            .clip(shape)
            .background(fg.copy(alpha = if (dark) 0.10f else 0.07f))
            .border(1.dp, accent.copy(alpha = if (missedForMe) 0.40f else 0.18f), shape)
            .pointerInput(kind) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { cb.value() },
                    onLongPress = { lp.value() }
                )
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (group) CallIcons.People else CallIcons.Phone, null, tint = accent,
                modifier = Modifier.size(22.dp).graphicsLayer {
                    // الهاتف المقلوب = فائتة، والعادي = صادرة
                    rotationZ = (if (missedForMe && !group) 135f else 0f) + 14f * shake.value
                }
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                title, color = if (missedForMe) accent else fg,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
            )
            Text(sub, color = fg.copy(alpha = 0.65f), fontSize = 12.sp, maxLines = 1)
        }
        Spacer(Modifier.width(12.dp))
        // زر الاتصال: حلقة تنبض للمكالمة الفائتة
        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
            if (missedForMe) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = this.size.minDimension / 2f
                    drawCircle(
                        Color(0xFF34C759).copy(alpha = (1f - pulse) * 0.55f),
                        radius = r * (1f + 0.45f * pulse),
                        style = Stroke(2.dp.toPx())
                    )
                }
            }
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF34C759)),
                contentAlignment = Alignment.Center
            ) { Icon(CallIcons.Phone, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
    }
}
