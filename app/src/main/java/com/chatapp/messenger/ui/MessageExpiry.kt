package com.chatapp.messenger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.Repo
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay

/**
 * انتهاء الرسالة (رسائل تختفي): صاحب الرسالة يحدد مدة بالدقائق أو الساعات أو الاثنين،
 * وبعد ما تنتهي المدة (تُحسب من لحظة التفعيل) الرسالة تختفي عند الجميع وتنحذف من السيرفر.
 * وقت الانتهاء [expiresAt] محفوظ بالرسالة نفسها بتوقيت السيرفر.
 */
object MessageExpiry {
    /** أقصى مدة: 30 يوم */
    const val MAX_MINUTES = 30 * 24 * 60

    fun isExpired(expiresAt: Long, now: Long): Boolean = expiresAt > 0L && expiresAt <= now

    private fun hoursWord(h: Int) = when {
        h == 1 -> "ساعة"
        h == 2 -> "ساعتان"
        h in 3..10 -> "$h ساعات"
        else -> "$h ساعة"
    }

    private fun minutesWord(m: Int) = when {
        m == 1 -> "دقيقة"
        m == 2 -> "دقيقتان"
        m in 3..10 -> "$m دقائق"
        else -> "$m دقيقة"
    }

    /** "ساعة و 30 دقيقة" */
    fun durationText(totalMinutes: Int): String {
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h > 0 && m > 0 -> hoursWord(h) + " و " + minutesWord(m)
            h > 0 -> hoursWord(h)
            else -> minutesWord(m)
        }
    }

    /** نص مختصر للوقت المتبقي بالفقاعة: 2ي 3س / 2س 5د / 5د / 40ث */
    fun remainingText(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(1)
        val d = s / 86400
        val h = (s % 86400) / 3600
        val m = (s % 3600) / 60
        return when {
            d > 0 -> "${d}ي" + if (h > 0) " ${h}س" else ""
            h > 0 -> "${h}س" + if (m > 0) " ${m}د" else ""
            m > 0 -> "${m}د"
            else -> "${s}ث"
        }
    }
}

/**
 * وقت "الآن" بتوقيت السيرفر، يتحدّث لحظة انتهاء أقرب رسالة، فتختفي الرسائل المنتهية من القائمة بدون أي تفاعل.
 * [expiries] = أوقات الانتهاء لكل الرسائل الي لها وقت.
 */
@Composable
fun rememberExpiryNow(expiries: List<Long>): Long {
    var now by remember { mutableLongStateOf(Repo.serverNow()) }
    val next = expiries.filter { it > now }.minOrNull()
    LaunchedEffect(next, now) {
        if (next != null) {
            delay((next - Repo.serverNow()).coerceAtLeast(0L) + 50L)
            now = Repo.serverNow()
        }
    }
    return now
}

/** شارة صغيرة داخل الفقاعة: ⏱ + الوقت المتبقي (تتحدّث كل ثانية بآخر دقيقة، وبعدها كل 10 ثواني) */
@Composable
fun ExpiryBadge(expiresAt: Long, color: Color, modifier: Modifier = Modifier) {
    val now by produceState(Repo.serverNow(), expiresAt) {
        while (true) {
            value = Repo.serverNow()
            val left = expiresAt - value
            if (left <= 0L) break
            delay(if (left < 60_000L) 1000L else 10_000L)
        }
    }
    val left = expiresAt - now
    if (left > 0L) {
        Row(modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Clock, null, tint = color.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(MessageExpiry.remainingText(left), color = color.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1)
        }
    }
}

/** حوار اختيار مدة انتهاء الرسالة: اختيارات سريعة + ساعات + دقائق */
@Composable
fun MessageExpiryDialog(
    backdrop: Backdrop,
    dark: Boolean,
    hasExpiry: Boolean,
    onSet: (durationMs: Long) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val fg = glassFg(dark)
    var hours by remember { mutableIntStateOf(1) }
    var minutes by remember { mutableIntStateOf(0) }
    val total = (hours * 60 + minutes).coerceAtMost(MessageExpiry.MAX_MINUTES)

    GlassDialog(
        backdrop = backdrop, dark = dark, title = "انتهاء الرسالة", onDismiss = onDismiss,
        confirmText = "تفعيل", confirmEnabled = total >= 1,
        onConfirm = { onSet(total * 60_000L) }
    ) {
        Text(
            "تنحذف الرسالة تلقائياً عند الجميع بعد المدة الي تحددها، وتُحسب من الآن.",
            color = fg.copy(alpha = 0.7f), fontSize = 14.sp
        )
        Spacer(Modifier.height(14.dp))

        // اختيارات سريعة
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to "دقيقة", 5 to "5 د", 30 to "30 د", 60 to "ساعة", 360 to "6 س", 1440 to "24 س").forEach { (mins, label) ->
                val sel = total == mins
                Box(
                    Modifier.clip(RoundedCornerShape(14.dp))
                        .background(if (sel) AccentBlue.copy(alpha = 0.85f) else fg.copy(alpha = 0.10f))
                        .clickable { hours = mins / 60; minutes = mins % 60 }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) { Text(label, color = if (sel) Color.White else fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        Spacer(Modifier.height(16.dp))

        // ساعات + دقائق
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                NumberBox("ساعات", hours, 0, 720, fg) { hours = it }
            }
            Box(Modifier.weight(1f)) {
                NumberBox("دقائق", minutes, 0, 59, fg) { minutes = it }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            if (total >= 1) "تنتهي بعد: " + MessageExpiry.durationText(total) else "حدد مدة دقيقة على الأقل",
            color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
        if (hours * 60 + minutes > MessageExpiry.MAX_MINUTES) {
            Text("أقصى مدة 30 يوم", color = Color(0xFFFF8A3D), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        if (hasExpiry) {
            Spacer(Modifier.height(12.dp))
            Text(
                "إلغاء الانتهاء", color = Color(0xFFE5484D), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .clickable { onRemove() }.padding(vertical = 10.dp)
            )
        }
    }
}

/** خانة رقم بأزرار − و + (وتقدر تكتب الرقم مباشرة) */
@Composable
private fun NumberBox(label: String, value: Int, min: Int, max: Int, fg: Color, onChange: (Int) -> Unit) {
    var txt by remember(value) { mutableStateOf(value.toString()) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(fg.copy(alpha = 0.10f)).padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StepButton("−", fg) { onChange((value - 1).coerceIn(min, max)) }
            BasicTextField(
                value = txt,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }.take(3)
                    txt = digits
                    onChange((digits.toIntOrNull() ?: 0).coerceIn(min, max))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(color = fg, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(AccentBlue),
                modifier = Modifier.weight(1f)
            )
            StepButton("+", fg) { onChange((value + 1).coerceIn(min, max)) }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = fg.copy(alpha = 0.65f), fontSize = 12.sp)
    }
}

@Composable
private fun StepButton(symbol: String, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(fg.copy(alpha = 0.12f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(symbol, color = fg, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
}
