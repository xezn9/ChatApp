package com.chatapp.messenger.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.max

/**
 * ترجمة ذكية للرسائل الإنجليزية للعربية:
 *  - تترجم أي مقطع إنجليزي حتى لو داخل جملة عربية (العربي يبقى مثل ما هو).
 *  - تفهم اختصارات الدردشة (u, r, pls, idk, gonna ...) وتصلّح الحروف المكررة (helloooo).
 *  - إذا الكلمة مكتوبة غلط ولا انترجمت، تجرّب التصحيح الإملائي وتعيد المحاولة.
 */
object Translator {
    /** معرّف الرسالة -> الترجمة العربية (State حتى تتحدّث الفقاعة تلقائياً) */
    val results = mutableStateMapOf<String, String>()

    /** الرسائل الي تنترجم هسه (تظهر لها أنيميشن التحميل) */
    val loading: SnapshotStateList<String> = mutableStateListOf()

    /** ترجمات جديدة تنعرض مرة وحدة بأنيميشن كتابة تدريجية */
    internal val fresh = mutableSetOf<String>()

    private val latinRun = Regex("[A-Za-z][A-Za-z0-9'’\\-]*(?:[ \\t,.!?]+[A-Za-z][A-Za-z0-9'’\\-]*)*")
    private val repeated3 = Regex("(\\p{L})\\1{2,}")

    private val slang = mapOf(
        "u" to "you", "ur" to "your", "r" to "are", "pls" to "please", "plz" to "please",
        "thx" to "thanks", "thnx" to "thanks", "tnx" to "thanks", "ty" to "thank you",
        "gonna" to "going to", "wanna" to "want to", "gotta" to "have to", "lemme" to "let me",
        "idk" to "I don't know", "omg" to "oh my god", "brb" to "be right back",
        "btw" to "by the way", "k" to "okay", "kk" to "okay", "bc" to "because", "cuz" to "because",
        "tmrw" to "tomorrow", "tmr" to "tomorrow", "tho" to "though", "np" to "no problem",
        "nvm" to "never mind", "ily" to "I love you", "gm" to "good morning", "gn" to "good night",
        "hbu" to "how about you", "wbu" to "what about you", "wyd" to "what are you doing",
        "hru" to "how are you", "msg" to "message", "dm" to "message", "bro" to "brother",
        "sis" to "sister", "y" to "why", "n" to "and", "im" to "I am", "dont" to "don't",
        "cant" to "can't", "wont" to "won't", "ive" to "I have", "thats" to "that is",
        "whats" to "what is", "hows" to "how is", "ya" to "yes", "yep" to "yes", "yup" to "yes",
        "nope" to "no", "lol" to "haha", "lmao" to "haha", "rn" to "right now", "tbh" to "to be honest",
        "asap" to "as soon as possible", "bday" to "birthday", "pic" to "picture", "pics" to "pictures"
    )
    private val slangRegex = Regex("\\b(" + slang.keys.joinToString("|") + ")\\b", RegexOption.IGNORE_CASE)

    /** هل بالرسالة أي كلمة إنجليزية (حتى كلمة وحدة أو مكتوبة غلط)؟ */
    fun looksEnglish(text: String): Boolean = text.count { it in 'a'..'z' || it in 'A'..'Z' } >= 2

    private fun hasArabic(s: String) = s.any { it in '\u0600'..'\u06FF' }

    /** زر الترجمة: إذا الرسالة مترجمة يخفيها، وإلا يترجمها مع أنيميشن التحميل */
    fun toggle(scope: CoroutineScope, id: String, text: String) {
        if (results.containsKey(id)) { results.remove(id); return }
        if (id in loading) return
        loading.add(id)
        scope.launch {
            val started = System.currentTimeMillis()
            val r = try { smartTranslate(text) } catch (e: Exception) { null }
            // حد أدنى للأنيميشن حتى ما يومض ويختفي إذا الترجمة سريعة
            val wait = 700 - (System.currentTimeMillis() - started)
            if (wait > 0) delay(wait)
            if (r != null) { fresh.add(id); results[id] = r }
            else AppToast.show("ما گدرت أترجم الرسالة، تأكد من الإنترنت")
            loading.remove(id)
        }
    }

    /** يترجم كل مقطع إنجليزي بالنص ويخلي الباقي مثل ما هو. null = فشل الاتصال */
    suspend fun smartTranslate(text: String): String? = coroutineScope {
        val runs = latinRun.findAll(text).toList()
        if (runs.isEmpty()) return@coroutineScope null
        val out = runs.map { async(Dispatchers.IO) { translateRun(it.value) } }.awaitAll()
        if (out.all { it == null }) return@coroutineScope null
        val sb = StringBuilder()
        var last = 0
        runs.forEachIndexed { i, m ->
            sb.append(text, last, m.range.first)
            sb.append(out[i] ?: m.value)
            last = m.range.last + 1
        }
        sb.append(text, last, text.length)
        sb.toString().trim()
    }

    /** مقطع إنجليزي واحد: تطبيع -> ترجمة -> (إذا ما انترجم) تصحيح إملائي -> إعادة محاولة */
    private fun translateRun(run: String): String? {
        var s = run.trim()
        s = slangRegex.replace(s) { slang[it.value.lowercase()] ?: it.value }
        s = repeated3.replace(s) { it.groupValues[1] + it.groupValues[1] }

        val first = call(s) ?: return null
        if (hasArabic(first.first)) return first.first

        // ما انترجم (غالباً غلط إملائي): جرّب التصحيح الي يقترحه المترجم
        val fixed = first.second
        if (!fixed.isNullOrBlank() && !fixed.equals(s, true)) {
            call(fixed)?.let { if (hasArabic(it.first)) return it.first }
        }
        // حروف مكررة (helloooo): جرّب تخليها حرف واحد
        val single = Regex("(\\p{L})\\1+").replace(s) { it.groupValues[1] }
        if (!single.equals(s, true)) {
            call(single)?.let { if (hasArabic(it.first)) return it.first }
        }
        return first.first   // أسماء/كلمات ما تنترجم تبقى مثل ما هي
    }

    /** ينادي المترجم: يرجّع (الترجمة، التصحيح الإملائي المقترح إن وجد) أو null إذا فشل */
    private fun call(q: String): Pair<String, String?>? {
        return try {
            val enc = URLEncoder.encode(q, "UTF-8")
            val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=ar&dt=t&dt=qca&q=$enc")
            val con = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            try {
                if (con.responseCode != 200) return null
                val body = con.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val root = JSONArray(body)
                val segs = root.getJSONArray(0)
                val sb = StringBuilder()
                for (i in 0 until segs.length()) sb.append(segs.getJSONArray(i).optString(0, ""))
                val fix = root.optJSONArray(7)?.optString(0, null)
                Pair(sb.toString().trim(), fix)
            } finally {
                con.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }
}

/** تحت نص الفقاعة: أنيميشن "جاري الترجمة" ثم الترجمة بكتابة تدريجية */
@Composable
fun TranslationBlock(msgId: String, textColor: Color) {
    val isLoading = msgId in Translator.loading
    val tr = Translator.results[msgId]
    val state = if (isLoading) 1 else if (tr != null) 2 else 0
    Crossfade(targetState = state, animationSpec = tween(260), label = "trBlock") { st ->
        when (st) {
            1 -> TranslatingShimmer(textColor)
            2 -> if (tr != null) TranslatedText(msgId, tr, textColor)
            else -> Spacer(Modifier.height(0.dp))
        }
    }
}

@Composable
private fun TranslatingShimmer(textColor: Color) {
    val t = rememberInfiniteTransition(label = "trLoad")
    val sweep by t.animateFloat(
        initialValue = -0.6f, targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "sweep"
    )
    val pulse by t.animateFloat(
        initialValue = 0.45f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse), label = "pulse"
    )
    val phase by t.animateFloat(
        initialValue = 0f, targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "dots"
    )
    val teal = Color(0xFF00BFA5)

    @Composable
    fun bar(frac: Float) {
        Spacer(
            Modifier.fillMaxWidth(frac).height(9.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(textColor.copy(alpha = 0.10f))
                .drawBehind {
                    val w = size.width
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, teal.copy(alpha = 0.65f), Color.Transparent),
                            startX = (sweep - 0.4f) * w, endX = (sweep + 0.4f) * w
                        ),
                        cornerRadius = CornerRadius(12f, 12f)
                    )
                }
        )
    }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                AppIcons.Translate, null, tint = teal,
                modifier = Modifier.size(15.dp).alpha(pulse)
                    .graphicsLayer { rotationZ = (pulse - 0.7f) * 24f; scaleX = 0.85f + 0.2f * pulse; scaleY = 0.85f + 0.2f * pulse }
            )
            Spacer(Modifier.width(6.dp))
            Text("جاري الترجمة", color = textColor.copy(alpha = 0.75f), fontSize = 12.sp)
            Spacer(Modifier.width(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (i in 0..2) {
                    Spacer(
                        Modifier.size(4.dp).clip(RoundedCornerShape(50))
                            .alpha(if (phase.toInt() == i) 1f else 0.3f)
                            .background(teal)
                    )
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        bar(1f)
        Spacer(Modifier.height(5.dp))
        bar(0.62f)
    }
}

@Composable
private fun TranslatedText(id: String, text: String, textColor: Color) {
    // الترجمة الجديدة تنكتب تدريجياً مرة وحدة، وبعدها (سكرول/إعادة رسم) تظهر كاملة
    val animate = remember(id) { Translator.fresh.remove(id) }
    var shown by remember(id, text) { mutableIntStateOf(if (animate) 0 else text.length) }
    LaunchedEffect(id, text) {
        if (animate) {
            val step = max(1, text.length / 30)
            while (shown < text.length) {
                delay(28)
                shown = (shown + step).coerceAtMost(text.length)
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(textColor.copy(alpha = 0.25f)))
        Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Translate, null, tint = Color(0xFF00BFA5), modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text("ترجمة", color = textColor.copy(alpha = 0.6f), fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            // زر إخفاء الترجمة داخل الترجمة نفسها
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(textColor.copy(alpha = 0.14f))
                    .clickable { Translator.results.remove(id) }
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Text("إخفاء", color = textColor.copy(alpha = 0.85f), fontSize = 11.sp)
            }
        }
        Text(
            text.take(shown), color = textColor.copy(alpha = 0.95f), fontSize = 15.sp,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp)
        )
    }
}
