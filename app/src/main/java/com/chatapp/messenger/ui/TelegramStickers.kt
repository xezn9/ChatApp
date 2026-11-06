package com.chatapp.messenger.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

// ============================================================================
//  تنزيل حزم ملصقات تيليجرام وإضافتها كأقسام جديدة بقائمة رموز الاسم.
//
//  الحزمة تنحفظ بذاكرة الجهاز:  files/tgpacks/tg_<اسم الحزمة>/   (ملفات s001.tgs ... + meta.json)
//  وتظهر تلقائياً مع باقي الأقسام (StickerRegistry يقرأ هذا المجلد). الحذف = حذف المجلد.
//
//  التنزيل يتم عبر Telegram Bot API، ولازم توكن بوت (تسويه مجاناً من @BotFather).
//  تقدر تحطه مرة وحدة بالكود (DEFAULT_TOKEN) أو يدخله المستخدم أول مرة بالنافذة وينحفظ.
// ============================================================================

internal class TgException(val code: Int, message: String) : Exception(message)

internal object TelegramStickers {
    /** توكن البوت مدمج بالتطبيق، فالنافذة ما تطلبه من المستخدم */
    private const val DEFAULT_TOKEN = "7554143101:AAHP7rKOrGP7YJuP-b3UiGMCOGzGV3RqqzY"

    private const val PREFS = "tg_stickers"
    private const val API = "https://api.telegram.org"
    private val OK_EXT = setOf("tgs", "webp", "png", "jpg", "jpeg")

    fun root(ctx: Context) = File(ctx.applicationContext.filesDir, "tgpacks")

    fun token(ctx: Context): String {
        val t = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("token", "") ?: ""
        return t.ifBlank { DEFAULT_TOKEN }
    }

    fun saveToken(ctx: Context, token: String) {
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("token", token).apply()
    }

    private val linkRx = Regex("(?:addstickers|addemoji)/([A-Za-z0-9_]+)", RegexOption.IGNORE_CASE)
    private val setRx = Regex("[?&]set=([A-Za-z0-9_]+)", RegexOption.IGNORE_CASE)
    private val plainRx = Regex("^[A-Za-z0-9_]{3,64}$")

    /** يستخرج اسم الحزمة من: t.me/addstickers/NAME أو tg://addstickers?set=NAME أو الاسم مباشرة */
    fun parseName(input: String): String? {
        val s = input.trim()
        if (s.isEmpty()) return null
        linkRx.find(s)?.let { return it.groupValues[1] }
        setRx.find(s)?.let { return it.groupValues[1] }
        return if (plainRx.matches(s)) s else null
    }

    private fun httpGet(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        try {
            val code = c.responseCode
            val bytes = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
            if (code !in 200..299) {
                val body = String(bytes)
                throw TgException(
                    code,
                    when {
                        code == 401 || code == 404 && body.contains("Unauthorized", true) -> "التوكن غير صحيح"
                        body.contains("STICKERSET_INVALID", true) || body.contains("not found", true) -> "حزمة الملصقات غير موجودة، تأكد من الرابط"
                        code == 429 -> "تيليجرام يطلب الانتظار شوي، حاول بعد دقيقة"
                        else -> "فشل التنزيل (خطأ $code)"
                    }
                )
            }
            return bytes
        } finally {
            c.disconnect()
        }
    }

    /**
     * ينزّل الحزمة كاملة. [onProgress] (المنزّل، الكل). يرمي [TgException] برسالة عربية عند الفشل.
     * يرجع اسم المجلد النهائي (id الحزمة).
     */
    suspend fun download(ctx: Context, name: String, token: String, onProgress: (Int, Int) -> Unit): String = withContext(Dispatchers.IO) {
        val id = "tg_$name"
        val base = root(ctx).also { it.mkdirs() }
        val tmp = File(base, ".tmp_$name")
        tmp.deleteRecursively()
        tmp.mkdirs()
        try {
            val res = JSONObject(String(httpGet("$API/bot$token/getStickerSet?name=$name"))).optJSONObject("result")
                ?: throw TgException(400, "حزمة الملصقات غير موجودة، تأكد من الرابط")
            val title = res.optString("title", name).ifBlank { name }
            val arr = res.optJSONArray("stickers") ?: throw TgException(400, "الحزمة فاضية")
            val total = arr.length()
            if (total == 0) throw TgException(400, "الحزمة فاضية")
            val counter = AtomicInteger(0)
            onProgress(0, total)
            val gate = Semaphore(4)
            coroutineScope {
                (0 until total).map { i ->
                    async {
                        gate.withPermit {
                            val o = arr.getJSONObject(i)
                            // ملصقات الفيديو (webm) ما نقدر نعرضها: نأخذ صورتها المصغرة الثابتة
                            val fileId = if (o.optBoolean("is_video", false))
                                (o.optJSONObject("thumbnail") ?: o.optJSONObject("thumb"))?.optString("file_id").orEmpty()
                            else o.optString("file_id")
                            if (fileId.isNotBlank()) {
                                try {
                                    val path = JSONObject(String(httpGet("$API/bot$token/getFile?file_id=$fileId")))
                                        .getJSONObject("result").getString("file_path")
                                    val ext = path.substringAfterLast('.', "webp").lowercase()
                                    if (ext in OK_EXT) {
                                        val bytes = httpGet("$API/file/bot$token/$path")
                                        File(tmp, "s%03d.%s".format(i + 1, ext)).writeBytes(bytes)
                                    }
                                } catch (e: TgException) {
                                    if (e.code == 401) throw e   // توكن خطأ: نوقف الكل
                                } catch (_: Exception) {
                                    // ملصق واحد فشل: نكمل البقية
                                }
                            }
                            onProgress(counter.incrementAndGet(), total)
                        }
                    }
                }.awaitAll()
            }
            if (tmp.list()?.none { it.substringAfterLast('.') in OK_EXT } != false) {
                throw TgException(0, "ما قدرت أنزّل أي ملصق من هذي الحزمة")
            }
            File(tmp, "meta.json").writeText(JSONObject().put("title", title).put("name", name).toString())
            val dest = File(base, id)
            dest.deleteRecursively()
            if (!tmp.renameTo(dest)) throw TgException(0, "تعذّر حفظ الحزمة")
            id
        } finally {
            if (tmp.exists()) tmp.deleteRecursively()
        }
    }

    /** يحذف الحزمة المنزّلة مع الكاش المولّد لها */
    fun delete(ctx: Context, packId: String) {
        File(root(ctx), packId).deleteRecursively()
        File(ctx.applicationContext.filesDir, "stickers/$packId").deleteRecursively()
    }
}

// ---------------------------------------------------------------------------
//  نافذة التنزيل: رابط الحزمة (+ التوكن أول مرة فقط)
// ---------------------------------------------------------------------------
@Composable
internal fun StickerDownloadDialog(dark: Boolean, onDismiss: () -> Unit, onDownloaded: () -> Unit) {
    val fg = if (dark) Color.White else Color(0xFF101418)
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf("") }
    var savedToken by remember { mutableStateOf(TelegramStickers.token(ctx)) }
    var tokenInput by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun start() {
        val name = TelegramStickers.parseName(link)
        if (name == null) { error = "الرابط غير صحيح. مثال: t.me/addstickers/اسم_الحزمة"; return }
        val tk = if (savedToken.isNotBlank()) savedToken else tokenInput.trim()
        if (tk.isBlank()) { error = "أدخل توكن البوت (من @BotFather)"; return }
        error = null; busy = true; done = 0; total = 0
        job = scope.launch {
            try {
                TelegramStickers.download(ctx, name, tk) { d, t -> done = d; total = t }
                if (savedToken.isBlank()) TelegramStickers.saveToken(ctx, tk)
                onDownloaded()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: TgException) {
                error = e.message
                if (e.code == 401) { TelegramStickers.saveToken(ctx, ""); savedToken = TelegramStickers.token(ctx) }
            } catch (_: Exception) {
                error = "تعذّر التنزيل، تأكد من الإنترنت"
            } finally {
                busy = false
            }
        }
    }

    PickerOverlay(dark, locked = busy, onDismiss = onDismiss) {
            Text("تنزيل ملصقات من تيليجرام", color = fg, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                "الصق رابط حزمة الملصقات وراح تنضاف كقسم جديد",
                color = fg.copy(0.6f), fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(14.dp))
            DlField(link, { link = it; error = null }, "https://t.me/addstickers/...", fg, !busy, KeyboardType.Uri)
            if (savedToken.isBlank()) {
                Spacer(Modifier.height(8.dp))
                DlField(tokenInput, { tokenInput = it; error = null }, "توكن البوت (من @BotFather) — مرة وحدة فقط", fg, !busy, KeyboardType.Ascii)
            }
            if (busy) {
                Spacer(Modifier.height(14.dp))
                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { done.toFloat() / total },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
                        color = Color(0xFF4DA3FF), trackColor = fg.copy(0.12f)
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
                        color = Color(0xFF4DA3FF), trackColor = fg.copy(0.12f)
                    )
                }
                Text(
                    if (total > 0) "جاري التنزيل  $done / $total" else "جاري الاتصال...",
                    color = fg.copy(0.7f), fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp)
                )
            }
            error?.let {
                Text(it, color = Color(0xFFFF5A5F), fontSize = 12.5.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(fg.copy(0.10f))
                        .clickable { if (busy) { job?.cancel(); busy = false } else onDismiss() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text(if (busy) "إيقاف" else "إلغاء", color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF4DA3FF).copy(if (busy) 0.35f else 1f))
                        .clickable(enabled = !busy) { start() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text("تنزيل", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
    }
}

/**
 * طبقة حوار داخل نافذة الرموز (مو Dialog مستقل، حتى تبقى جزء من الزجاج): تعتيم + بطاقة نصف شفافة بحد زجاجي.
 * [locked] = أثناء التنزيل: الضغط بالخارج وزر الرجوع ما يسكّرونها.
 */
@Composable
internal fun PickerOverlay(dark: Boolean, locked: Boolean = false, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    androidx.activity.compose.BackHandler(enabled = true) { if (!locked) onDismiss() }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (!locked) onDismiss() }
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(20.dp).fillMaxWidth().clip(shape)
                .background(if (dark) Color(0xE61A1E33) else Color(0xF2FFFFFF))
                .border(0.8.dp, glassBorderBrush(0.45f), shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

/** زر بسيط داخل [PickerOverlay] (يتوزّع بالتساوي بالصف) */
@Composable
internal fun RowScope.OverlayBtn(text: String, bg: Color, textColor: Color, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(bg).clickable { onClick() }.padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
internal fun DlField(value: String, onChange: (String) -> Unit, hint: String, fg: Color, enabled: Boolean, kb: KeyboardType, ltr: Boolean = true) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.fillMaxWidth().clip(shape).background(fg.copy(0.08f)).border(0.8.dp, fg.copy(0.14f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (value.isEmpty()) Text(hint, color = fg.copy(0.4f), fontSize = 13.sp, style = TextStyle(textDirection = TextDirection.Content))
        BasicTextField(
            value = value, onValueChange = onChange, enabled = enabled, singleLine = true,
            textStyle = TextStyle(color = fg, fontSize = 14.sp, textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content),
            cursorBrush = SolidColor(fg),
            keyboardOptions = KeyboardOptions(keyboardType = kb),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
