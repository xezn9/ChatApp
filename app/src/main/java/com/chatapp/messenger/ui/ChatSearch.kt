package com.chatapp.messenger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.ui.glass.GlassButton
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay

/**
 * البحث عن رسالة داخل محادثة (خاصة أو مجموعة).
 * - مطابقة متسامحة: تتجاهل التشكيل والتطويل، وتوحّد (أ إ آ ا) و (ى ي) و (ة ه) والأرقام العربية، وحالة الأحرف الإنجليزية.
 * - الرسائل تكون مرتبة من الأحدث للأقدم، فأول نتيجة = أحدث رسالة مطابقة.
 */
object ChatSearch {
    /** عدد الرسائل المحمّلة عند فتح البحث (بدل 50 بالخاصة و300 بالمجموعة) حتى يشمل البحث تاريخ أطول */
    const val PRIVATE_HISTORY = 2000
    const val GROUP_HISTORY = 3000

    class Folded(val text: String, val map: IntArray)

    private fun foldChar(c: Char): Char? = when (c) {
        in '\u064B'..'\u065F', '\u0670', '\u0640' -> null          // تشكيل + ألف خنجرية + تطويل
        'أ', 'إ', 'آ', 'ٱ' -> 'ا'
        'ى' -> 'ي'
        'ة' -> 'ه'
        'ؤ' -> 'و'
        'ئ' -> 'ي'
        in '\u0660'..'\u0669' -> '0' + (c - '\u0660')               // أرقام عربية
        in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')               // أرقام فارسية
        else -> c.lowercaseChar()
    }

    /** النص بعد التوحيد + خريطة من موضع كل حرف فيه إلى موضعه بالنص الأصلي (للتظليل) */
    fun fold(s: String): Folded {
        val sb = StringBuilder(s.length)
        val map = IntArray(s.length)
        for (i in s.indices) {
            val f = foldChar(s[i]) ?: continue
            map[sb.length] = i
            sb.append(f)
        }
        return Folded(sb.toString(), map)
    }

    fun foldQuery(q: String): String = fold(q.trim()).text

    fun contains(text: String, foldedQuery: String): Boolean =
        foldedQuery.isNotEmpty() && fold(text).text.contains(foldedQuery)

    /** مواضع المطابقة بالنص الأصلي */
    fun ranges(text: String, foldedQuery: String): List<IntRange> {
        if (foldedQuery.isEmpty()) return emptyList()
        val f = fold(text)
        val out = ArrayList<IntRange>()
        var from = 0
        while (true) {
            val i = f.text.indexOf(foldedQuery, from)
            if (i < 0) break
            val last = i + foldedQuery.length - 1
            out += f.map[i]..f.map[last]
            from = i + foldedQuery.length
        }
        return out
    }
}

/** حالة البحث لمحادثة وحدة */
@Stable
class MessageSearch {
    var open by mutableStateOf(false)
    var query by mutableStateOf("")
    /** الرسالة الحالية (المحدّدة) من النتائج */
    var curId by mutableStateOf<String?>(null)
    internal var tick by mutableIntStateOf(0)
    /** معرّفات النتائج، الأحدث أولاً */
    var ids by mutableStateOf<List<String>>(emptyList())
        internal set

    val folded: String get() = if (open) ChatSearch.foldQuery(query) else ""
    /** النص الي يتظلل بالفقاعات (فاضي = بدون تظليل) */
    val highlight: String get() = if (open) query.trim() else ""
    val total: Int get() = ids.size
    val position: Int get() = curId?.let { ids.indexOf(it) + 1 } ?: 0

    fun show() { open = true }
    fun close() { open = false; query = ""; curId = null }

    /** أقدم نتيجة بعد الحالية */
    fun older() {
        if (ids.isEmpty()) return
        val i = curId?.let { ids.indexOf(it) } ?: -1
        curId = ids[(i + 1) % ids.size]; tick++
    }

    /** أحدث نتيجة قبل الحالية */
    fun newer() {
        if (ids.isEmpty()) return
        val i = curId?.let { ids.indexOf(it) } ?: 0
        curId = ids[if (i <= 0) ids.size - 1 else i - 1]; tick++
    }
}

/**
 * @param items الرسائل مرتبة من الأحدث للأقدم
 * @param textOf النص القابل للبحث بالرسالة (null = الرسالة ما تنبحث: محذوفة، ملصق، سجل مكالمة، صورة بدون اسم)
 * @param onJump ينزل للرسالة ويومّضها
 */
@Composable
fun <T> rememberMessageSearch(
    key: Any,
    items: List<T>,
    idOf: (T) -> String,
    textOf: (T) -> String?,
    onJump: (String) -> Unit
): MessageSearch {
    val s = remember(key) { MessageSearch() }
    val q = s.folded
    val ids = remember(items, q) {
        if (q.isEmpty()) emptyList()
        else items.filter { m -> textOf(m)?.let { ChatSearch.contains(it, q) } == true }.map(idOf)
    }
    SideEffect { s.ids = ids }

    // أول نتيجة تنحدد تلقائياً بعد ما يوقف المستخدم عن الكتابة
    LaunchedEffect(q) {
        if (q.isEmpty()) { s.curId = null; return@LaunchedEffect }
        delay(250)
        s.curId = s.ids.firstOrNull()
        s.tick++
    }
    val jump by rememberUpdatedState(onJump)
    LaunchedEffect(s.curId, s.tick) { s.curId?.let { jump(it) } }

    BackHandler(enabled = s.open) { s.close() }
    return s
}

/** يظلّل كلمة البحث داخل نص الفقاعة */
@Composable
fun searchHighlighted(text: String, highlight: String): AnnotatedString {
    if (highlight.isEmpty()) return AnnotatedString(text)
    val q = ChatSearch.foldQuery(highlight)
    val rs = ChatSearch.ranges(text, q)
    if (rs.isEmpty()) return AnnotatedString(text)
    val hl = SpanStyle(background = Color(0xFFFFC107).copy(alpha = 0.55f), color = Color.Black)
    return buildAnnotatedString {
        append(text)
        rs.forEach { addStyle(hl, it.first, (it.last + 1).coerceAtMost(text.length)) }
    }
}

/** ارتفاع شريط البحث (40dp) + فراغ تحته، يُضاف لإزاحة كل شي يجي تحت الأزرار العلوية */
val SearchStripHeight = 48.dp

/**
 * شريط البحث الزجاجي: دائماً تحت الأزرار العلوية (ما يحل محلها).
 * ضغطة عليه تفتح البحث وتظهر أسهم التنقل وزر الإغلاق جنبه.
 * المكان يحدده المستدعي (padding top = statusTop + 64.dp).
 */
@Composable
fun MessageSearchBar(
    search: MessageSearch,
    backdrop: Backdrop,
    fg: Color,
    modifier: Modifier = Modifier
) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(search.open) { if (!search.open) focusManager.clearFocus() }

    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GlassButton(
            onClick = { search.show(); runCatching { focus.requestFocus() } }, backdrop = backdrop,
            modifier = Modifier.weight(1f).heightIn(min = 40.dp)
        ) {
            Icon(Icons.Filled.Search, null, tint = fg.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (search.query.isEmpty()) {
                    Text("ابحث في الرسائل…", color = fg.copy(alpha = 0.5f), fontSize = 15.sp, maxLines = 1)
                }
                BasicTextField(
                    value = search.query,
                    onValueChange = { search.query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = fg, fontSize = 16.sp),
                    cursorBrush = SolidColor(fg),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search.older() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                        .onFocusChanged { if (it.isFocused) search.show() }
                )
            }
            if (search.open && search.query.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    if (search.total == 0) "0" else "${search.position}/${search.total}",
                    color = fg.copy(alpha = 0.65f), fontSize = 13.sp, maxLines = 1
                )
            }
        }
        if (search.open) {
            // السهم لفوق = رسائل أقدم، لتحت = أحدث
            GlassButton(
                onClick = { search.older() }, backdrop = backdrop,
                enabled = search.total > 0, modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, "الأقدم", modifier = Modifier.size(22.dp))
            }
            GlassButton(
                onClick = { search.newer() }, backdrop = backdrop,
                enabled = search.total > 0, modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, "الأحدث", modifier = Modifier.size(22.dp))
            }
            GlassButton(onClick = { search.close() }, backdrop = backdrop, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Close, "إغلاق البحث", modifier = Modifier.size(20.dp))
            }
        }
    }
}
