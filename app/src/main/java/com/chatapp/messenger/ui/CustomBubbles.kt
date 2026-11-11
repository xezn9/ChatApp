package com.chatapp.messenger.ui

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * فقاعة من تصميم المستخدم.
 * تنحفظ محلياً بمكتبة المستخدم، وعند اختيارها تنرفع كنص JSON لملفه الشخصي (حقل customBubble)
 * فيرسمها الطرف الثاني عنده. تتحول لملف .bubble.json ويمكن استردادها عند أي شخص.
 *
 * الألوان ARGB (Int). كل القيم تُقصّ بحدود آمنة عند القراءة لأن الملف ممكن يجي من شخص ثاني.
 */
data class CustomBubble(
    val id: String,
    val name: String = "فقاعتي",
    val radius: Int = 20,
    val fill1: Int = 0xFF7C4DFF.toInt(),
    /** null = لون واحد، غير ذلك = تدرج بين fill1 و fill2 */
    val fill2: Int? = null,
    val text: Int = 0xFFFFFFFF.toInt(),
    val borderW: Float = 0f,
    val borderC: Int = 0xFFFFFFFF.toInt(),
    val decor: String = "none",
    val accent: Int = 0xFFFFD54F.toInt(),
    /** الشكل الثاني (للدمج): يظهر بالجهة المعاكسة للشكل الأول */
    val decor2: String = "none",
    val accent2: Int = 0xFFFFD54F.toInt(),
    /** أشكال SVG مستوردة: تُستعمل لما decor / decor2 = "svg" */
    val svg1: SvgDecor? = null,
    val svg2: SvgDecor? = null,
    /** null = بدون توهج */
    val glow: Int? = null
) {
    fun toJson(): String = JSONObject().apply {
        put("type", TYPE)
        put("v", 1)
        put("id", id)
        put("name", name)
        put("radius", radius)
        put("fill", JSONArray().apply { put(hex(fill1)); fill2?.let { put(hex(it)) } })
        put("text", hex(text))
        put("border", JSONObject().apply { put("w", borderW.toDouble()); put("c", hex(borderC)) })
        put("decor", decor)
        put("accent", hex(accent))
        put("decor2", decor2)
        put("accent2", hex(accent2))
        if (decor == "svg" && svg1 != null) put("svg1", svg1.toJson())
        if (decor2 == "svg" && svg2 != null) put("svg2", svg2.toJson())
        if (glow != null) put("glow", hex(glow))
    }.toString()

    companion object {
        const val TYPE = "chatapp-bubble"
        private const val MAX_JSON = 130000

        fun newId(): String = UUID.randomUUID().toString().take(8)

        private fun hex(c: Int) = String.format("#%06X", c and 0xFFFFFF)

        private fun parseColor(s: String?, fallback: Int): Int {
            if (s == null) return fallback
            return runCatching { android.graphics.Color.parseColor(s) or (0xFF shl 24) }.getOrDefault(fallback)
        }

        /** يرجع null إذا النص مو فقاعة صالحة. يقصّ كل القيم بحدود آمنة. */
        fun fromJson(json: String?): CustomBubble? {
            if (json.isNullOrBlank() || json.length > MAX_JSON) return null
            return runCatching {
                val o = JSONObject(json)
                if (o.optString("type") != TYPE) return null
                val fill = o.optJSONArray("fill")
                val f1 = parseColor(fill?.optString(0), 0xFF7C4DFF.toInt())
                val f2 = if (fill != null && fill.length() >= 2) parseColor(fill.optString(1), f1) else null
                val b = o.optJSONObject("border")
                val s1 = SvgDecor.fromJson(o.optJSONObject("svg1"))
                val s2 = SvgDecor.fromJson(o.optJSONObject("svg2"))
                fun okDecor(d: String, svg: SvgDecor?) =
                    if (d == "svg") (if (svg != null) "svg" else "none") else if (BubbleDecors.any { it.id == d }) d else "none"
                val decorId = okDecor(o.optString("decor", "none"), s1)
                CustomBubble(
                    id = o.optString("id").filter { it.isLetterOrDigit() || it == '-' }.take(36).ifBlank { newId() },
                    name = o.optString("name").trim().take(20).ifBlank { "فقاعة" },
                    radius = o.optInt("radius", 20).coerceIn(4, 30),
                    fill1 = f1,
                    fill2 = f2,
                    text = parseColor(o.optString("text"), 0xFFFFFFFF.toInt()),
                    borderW = (b?.optDouble("w", 0.0) ?: 0.0).toFloat().coerceIn(0f, 4f),
                    borderC = parseColor(b?.optString("c"), 0xFFFFFFFF.toInt()),
                    decor = decorId,
                    accent = parseColor(o.optString("accent"), 0xFFFFD54F.toInt()),
                    decor2 = okDecor(o.optString("decor2", "none"), s2),
                    svg1 = s1,
                    svg2 = s2,
                    accent2 = parseColor(o.optString("accent2"), 0xFFFFD54F.toInt()),
                    glow = if (o.has("glow")) parseColor(o.optString("glow"), 0xFF00F0FF.toInt()) else null
                )
            }.getOrNull()
        }
    }
}

/** يرجع الشكل حسب الـ id، أو شكل SVG المستورد إذا الـ id = "svg". */
fun decorFor(id: String, svg: SvgDecor?): BubbleDecor =
    if (id == "svg" && svg != null) svg.asDecor() else bubbleDecorById(id)

/** يحوّل التصميم لـ BubbleStyle جاهز لـ StyledBubble (نفس نظام الفقاعات الأصلية). */
fun CustomBubble.toStyle(): BubbleStyle {
    val d1 = decorFor(decor, svg1)
    val d2 = decorFor(decor2, svg2)
    val acc1 = Color(accent)
    val acc2 = Color(accent2)
    val glowColor = glow?.let { Color(it) }
    val r = radius
    val draw1 = d1.draw
    val draw2 = d2.draw
    val hasDecor = draw1 != null || draw2 != null || glowColor != null
    val decorLambda: (DrawScope, Float, Float) -> Unit = { scope, w, h ->
        with(scope) {
            if (glowColor != null) {
                val u = 1.dp.toPx()
                drawRoundRect(
                    glowColor.copy(alpha = 0.16f), Offset(-3 * u, -3 * u), Size(w + 6 * u, h + 6 * u),
                    CornerRadius((r + 3) * u), style = Stroke(9 * u)
                )
                drawRoundRect(
                    glowColor.copy(alpha = 0.9f), Offset(0f, 0f), Size(w, h),
                    CornerRadius(r * u), style = Stroke(2f * u)
                )
            }
            if (draw1 != null) draw1(this, w, h, acc1)
            // الشكل الثاني (دمج) معكوس بالجهة الثانية
            if (draw2 != null) {
                withTransform({ scale(-1f, 1f, Offset(w / 2f, h / 2f)) }) { draw2(this, w, h, acc2) }
            }
        }
    }
    return BubbleStyle(
        id = "custom:$id",
        name = name,
        shape = RoundedCornerShape(r.dp),
        fill = listOfNotNull(Color(fill1), fill2?.let { Color(it) }),
        text = Color(text),
        border = borderW.dp,
        borderColor = Color(borderC),
        sidePad = d1.sidePad,
        farPad = d2.sidePad,
        topPad = maxOf(d1.topPad, d2.topPad, if (glowColor != null) 4.dp else 0.dp),
        decor = if (hasDecor) decorLambda else null
    )
}

/**
 * دمج فقاعتين بفقاعة جديدة: الجسم (الشكل/الإطار/النص) من [a]، واللون يتمازج (تدرج من لون a للون b)،
 * والشكل الأول من a والثاني من b. أي واحد ناقصه شكل ياخذ من الثاني.
 */
fun mergeBubbles(a: CustomBubble, b: CustomBubble): CustomBubble {
    class P(val id: String, val accent: Int, val svg: SvgDecor?)
    val pa = listOf(P(a.decor, a.accent, a.svg1), P(a.decor2, a.accent2, a.svg2)).filter { it.id != "none" }
    val pb = listOf(P(b.decor, b.accent, b.svg1), P(b.decor2, b.accent2, b.svg2)).filter { it.id != "none" }
    val first = pa.firstOrNull() ?: pb.firstOrNull()
    // شكل SVG ما نقارنه بالـ id لأن كل SVG يختلف عن الثاني
    val second = pb.firstOrNull { it !== first && (it.id == "svg" || it.id != first?.id) } ?: pa.getOrNull(1)
    return a.copy(
        id = CustomBubble.newId(),
        name = "${a.name}+${b.name}".take(20),
        radius = (a.radius + b.radius) / 2,
        fill2 = a.fill2 ?: b.fill1,
        decor = first?.id ?: "none",
        accent = first?.accent ?: a.accent,
        svg1 = first?.svg,
        decor2 = second?.id ?: "none",
        accent2 = second?.accent ?: a.accent2,
        svg2 = second?.svg,
        glow = a.glow ?: b.glow
    )
}

/**
 * يحدد شكل الفقاعة لأي مستخدم: الأصلية بالـ id، أو المخصّصة من JSON ملفه الشخصي.
 * [id] = "custom" يعني استعمل [customJson]. أي خلل بالـ JSON يرجع للشكل الافتراضي.
 */
fun resolveBubbleStyle(id: String, customJson: String): BubbleStyle {
    if (id == "custom") {
        // كاش صغير: بدونه كل إعادة رسم للدردشة تعيد قراءة الـ JSON وبناء مسارات الـ SVG (تسبب بطء)
        val key = customJson.hashCode().toLong() shl 32 or customJson.length.toLong()
        synchronized(styleCache) { styleCache[key] }?.let { return it }
        CustomBubble.fromJson(customJson)?.toStyle()?.let { st ->
            synchronized(styleCache) { styleCache[key] = st }
            return st
        }
    }
    return bubbleStyleById(id)
}

private val styleCache = object : LinkedHashMap<Long, BubbleStyle>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, BubbleStyle>?) = size > 12
}

/** مكتبة فقاعات المستخدم (محلية على جهازه). */
object CustomBubbleStore {
    private const val PREFS = "custom_bubbles"
    private const val KEY = "list"
    private const val MAX_ITEMS = 40

    val items = mutableStateListOf<CustomBubble>()
    private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val raw = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) CustomBubble.fromJson(arr.optString(i))?.let { items.add(it) }
        }
    }

    /** يضيف أو يحدّث (حسب الـ id). يرجع false إذا وصلت المكتبة للحد الأقصى. */
    fun upsert(ctx: Context, b: CustomBubble): Boolean {
        val i = items.indexOfFirst { it.id == b.id }
        if (i >= 0) items[i] = b else {
            if (items.size >= MAX_ITEMS) return false
            items.add(b)
        }
        persist(ctx)
        return true
    }

    fun delete(ctx: Context, id: String) {
        items.removeAll { it.id == id }
        persist(ctx)
    }

    private fun persist(ctx: Context) {
        val arr = JSONArray().apply { items.forEach { put(it.toJson()) } }
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }
}

/** تحويل الفقاعة لملف وحفظه بالتنزيلات، واسترداد فقاعة من ملف. */
object BubbleFiles {
    private const val MIME = "application/json"
    private const val MAX_BYTES = 130 * 1024

    /** يحفظ الفقاعة بمجلد التنزيلات باسم "<الاسم>.bubble.json". يرجع اسم الملف أو null إذا فشل. */
    suspend fun saveToDownloads(ctx: Context, b: CustomBubble): String? = withContext(Dispatchers.IO) {
        val safe = b.name.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").trim('_').ifBlank { "bubble" }
        val fileName = "$safe.bubble.json"
        val bytes = b.toJson().toByteArray(Charsets.UTF_8)
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, MIME)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = ctx.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
                resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return@withContext null
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                val f = File(dir, fileName)
                f.writeBytes(bytes)
                android.media.MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath), arrayOf(MIME), null)
            }
            fileName
        } catch (e: Exception) {
            null
        }
    }

    /** يقرأ ملف فقاعة. يرجع null إذا الملف مو فقاعة صالحة أو كبير. الـ id يتبدّل لواحد جديد حتى ما يتعارض مع فقاعاتي. */
    suspend fun readFromUri(ctx: Context, uri: Uri): CustomBubble? = withContext(Dispatchers.IO) {
        try {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readNBytesCompat(MAX_BYTES + 1) } ?: return@withContext null
            if (bytes.size > MAX_BYTES) return@withContext null
            CustomBubble.fromJson(String(bytes, Charsets.UTF_8))?.copy(id = CustomBubble.newId())
        } catch (e: Exception) {
            null
        }
    }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(2048)
        while (out.size() < max) {
            val n = read(buf, 0, minOf(buf.size, max - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
