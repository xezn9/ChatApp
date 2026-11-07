package com.chatapp.messenger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
//  ملصقات المحادثات (الخاصة والمجموعات).
//  الملصق يُرسل كرسالة نص عادية بصيغة  [[stk:s:<الحزمة>:<الاسم>]]
//  فما يحتاج أي تعديل بقواعد قاعدة البيانات، والرسالة تنعرض كملصق كبير بدون فقاعة.
// ============================================================================
internal object ChatSticker {
    private const val PREFIX = "[[stk:"
    private const val SUFFIX = "]]"

    fun encode(id: String): String = PREFIX + id + SUFFIX

    /** يرجع معرّف الملصق إذا النص رسالة ملصق، وإلا null */
    fun parse(text: String): String? {
        if (!text.startsWith(PREFIX) || !text.endsWith(SUFFIX)) return null
        val id = text.substring(PREFIX.length, text.length - SUFFIX.length)
        return if (id.startsWith("s:") && id.count { it == ':' } == 2) id else null
    }

    /** نص مختصر للقوائم والإشعارات والردود */
    fun preview(text: String): String = if (parse(text) != null) "🎭 ملصق" else text
}

/** الملصق داخل المحادثة. إذا حزمته مو موجودة عندي بعد (لسا تنزل) يظهر رمز بديل */
@Composable
internal fun StickerMessage(id: String, size: Dp = 132.dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val rev = StickerDownloader.version   // يعيد الرسم بعد تنزيل الحزم
    StickerRegistry.ensure(ctx)
    val packId = id.split(':').getOrNull(1).orEmpty()
    val known = StickerPacks.any { it.id == packId } || packId.startsWith("tg_")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (known) StickerBadgeView(id, size) else Text("🎭", fontSize = (size.value / 2.4f).sp)
    }
}
