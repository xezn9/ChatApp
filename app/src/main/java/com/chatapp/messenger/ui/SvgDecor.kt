package com.chatapp.messenger.ui

import android.content.Context
import android.graphics.Matrix
import android.util.Xml
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * جزء واحد من شكل SVG: مسار + تعبئة و/أو إطار.
 * [fill] / [stroke]: null = بدون، 0 = "لون الشكل" (يتغيّر من المحرر)، غير ذلك لون ثابت ARGB.
 * [sw] سماكة الإطار بوحدات الـ viewBox (بعد تطبيق التحويل). [m] = تحويل [a b c d e f] أو null.
 */
class SvgPath(
    val d: String, val fill: Int?, val stroke: Int?, val sw: Float,
    val cap: Int, val join: Int, val evenOdd: Boolean, val m: FloatArray?
)

class SvgDecor(val vbX: Float, val vbY: Float, val vbW: Float, val vbH: Float, val paths: List<SvgPath>) {
    val hasTint: Boolean get() = paths.any { it.fill == 0 || it.stroke == 0 }

    /** بصمة الشكل (للمقارنة بين شكلين). */
    val sig: Int by lazy {
        var h = 17
        paths.forEach { h = h * 31 + it.d.hashCode(); h = h * 31 + (it.fill ?: -1); h = h * 31 + (it.stroke ?: -1) }
        h * 31 + vbW.hashCode()
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("vb", JSONArray().apply { put(vbX.toDouble()); put(vbY.toDouble()); put(vbW.toDouble()); put(vbH.toDouble()) })
        put("p", JSONArray().apply {
            paths.forEach { p ->
                put(JSONArray().apply {
                    put(p.d)
                    put(colorStr(p.fill))
                    put(if (p.evenOdd) 1 else 0)
                    if (p.m != null) put(JSONArray().apply { p.m.forEach { put(it.toDouble()) } }) else put(JSONObject.NULL)
                    put(colorStr(p.stroke))
                    put(p.sw.toDouble())
                    put(p.cap * 3 + p.join)
                })
            }
        })
    }

    /** يحوّله لـ BubbleDecor قابل للرسم: شخصية بالجهة اليسرى (النظام يعكسه حسب الجهة). */
    private val decorCached: BubbleDecor by lazy { buildDecor() }

    fun asDecor(): BubbleDecor = decorCached

    private fun buildDecor(): BubbleDecor {
        class Compiled(val path: Path, val p: SvgPath)
        val compiled = paths.mapNotNull { p ->
            runCatching {
                val path = PathParser().parsePathString(p.d).toPath()
                path.fillType = if (p.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero
                if (p.m != null) path.transform(composeMatrix(p.m))
                Compiled(path, p)
            }.getOrNull()
        }
        val vx = vbX; val vy = vbY; val vw = vbW; val vh = vbH
        return BubbleDecor(
            id = "svg", name = "شكلي", sidePad = 36.dp, tintable = hasTint,
            draw = { _, h, tint ->
                val u = 1.dp.toPx()
                val box = 34 * u
                val sc = min(box / vw, box / vh)
                val ox = 1 * u + (box - vw * sc) / 2f - vx * sc
                val oy = h / 2f - box / 2f + (box - vh * sc) / 2f - vy * sc
                withTransform({ translate(ox, oy); scale(sc, sc, Offset.Zero) }) {
                    compiled.forEach { c ->
                        val p = c.p
                        p.fill?.let { drawPath(c.path, if (it == 0) tint else Color(it)) }
                        p.stroke?.let {
                            drawPath(
                                c.path, if (it == 0) tint else Color(it),
                                style = Stroke(
                                    width = max(p.sw, 0.01f),
                                    cap = when (p.cap) { 1 -> StrokeCap.Round; 2 -> StrokeCap.Square; else -> StrokeCap.Butt },
                                    join = when (p.join) { 1 -> StrokeJoin.Round; 2 -> StrokeJoin.Bevel; else -> StrokeJoin.Miter }
                                )
                            )
                        }
                    }
                }
            }
        )
    }

    companion object {
        const val MAX_PATHS = 300
        const val MAX_TOTAL_D = 30000

        private fun colorStr(c: Int?): String = when (c) {
            null -> "-"
            0 -> ""
            else -> String.format("#%08X", c)
        }

        private fun parseColorStr(s: String): Int? = when (s) {
            "-" -> null
            "" -> 0
            else -> runCatching { android.graphics.Color.parseColor(s) }.getOrDefault(0xFF000000.toInt())
        }

        internal fun composeMatrix(m: FloatArray): androidx.compose.ui.graphics.Matrix {
            val mx = androidx.compose.ui.graphics.Matrix()
            mx.values[0] = m[0]; mx.values[1] = m[1]   // a, b
            mx.values[4] = m[2]; mx.values[5] = m[3]   // c, d
            mx.values[12] = m[4]; mx.values[13] = m[5] // e, f
            return mx
        }

        fun fromJson(o: JSONObject?): SvgDecor? {
            if (o == null) return null
            return runCatching {
                val vb = o.getJSONArray("vb")
                val w = vb.getDouble(2).toFloat(); val h = vb.getDouble(3).toFloat()
                if (!(w > 0f && h > 0f)) return null
                val arr = o.getJSONArray("p")
                val out = ArrayList<SvgPath>()
                var total = 0
                for (i in 0 until minOf(arr.length(), MAX_PATHS)) {
                    val e = arr.getJSONArray(i)
                    val d = e.getString(0)
                    total += d.length
                    if (total > MAX_TOTAL_D) break
                    val fill = parseColorStr(e.optString(1, ""))
                    val m = e.optJSONArray(3)?.takeIf { it.length() == 6 }?.let { a -> FloatArray(6) { a.getDouble(it).toFloat() } }
                    val stroke = if (e.length() > 4) parseColorStr(e.optString(4, "-")) else null
                    val sw = e.optDouble(5, 0.0).toFloat().coerceIn(0f, 100000f)
                    val cj = e.optInt(6, 0).coerceIn(0, 8)
                    out.add(SvgPath(d, fill, stroke, sw, cj / 3, cj % 3, e.optInt(2) == 1, m))
                }
                if (out.isEmpty()) null else SvgDecor(vb.getDouble(0).toFloat(), vb.getDouble(1).toFloat(), w, h, out)
            }.getOrNull()
        }
    }
}

/** شكل SVG محفوظ بمكتبة المستخدم (له اسم ويمكن تغييره). */
class SvgEntry(val id: String, val name: String, val svg: SvgDecor) {
    val decor: BubbleDecor by lazy { svg.asDecor() }
}

/** مكتبة أشكال SVG المحلية: تنحفظ على الجهاز، وتقدر تغيّر اسم أي شكل أو تحذفه. */
object SvgStore {
    private const val PREFS = "svg_shapes"
    private const val KEY = "list"
    private const val MAX_ITEMS = 80

    val items = mutableStateListOf<SvgEntry>()
    private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val raw = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val svg = SvgDecor.fromJson(o.optJSONObject("svg")) ?: continue
                items.add(SvgEntry(o.optString("id").ifBlank { UUID.randomUUID().toString().take(8) }, cleanName(o.optString("name")), svg))
            }
        }
    }

    private fun cleanName(n: String) = n.trim().take(20).ifBlank { "شكلي" }

    /** يضيف الشكل للمكتبة. إذا موجود نفس الشكل من قبل يرجع الموجود. يرجع null إذا المكتبة ممتلئة. */
    fun add(ctx: Context, name: String, svg: SvgDecor): SvgEntry? {
        items.firstOrNull { it.svg.sig == svg.sig }?.let { return it }
        if (items.size >= MAX_ITEMS) return null
        val e = SvgEntry(UUID.randomUUID().toString().take(8), cleanName(name), svg)
        items.add(e)
        persist(ctx)
        return e
    }

    fun rename(ctx: Context, id: String, name: String) {
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return
        items[i] = SvgEntry(id, cleanName(name), items[i].svg)
        persist(ctx)
    }

    fun delete(ctx: Context, id: String) {
        items.removeAll { it.id == id }
        persist(ctx)
    }

    private fun persist(ctx: Context) {
        val arr = JSONArray().apply {
            items.forEach { e -> put(JSONObject().apply { put("id", e.id); put("name", e.name); put("svg", e.svg.toJson()) }) }
        }
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }
}

/**
 * قارئ SVG شامل: path/rect/circle/ellipse/line/polyline/polygon، التعبئة والإطار (stroke)،
 * التحويلات، التدرّجات (تتحول للون متوسط)، CSS داخل <style>، وعناصر <use>/<symbol>.
 * إذا الشكل كبير نقلّل دقة الأرقام تدريجياً، وآخر حل نحذف التفاصيل الصغيرة حتى ينقبل أي ملف.
 */
object SvgImport {
    class Result(val svg: SvgDecor?, val error: String?)

    private val NUM = Regex("-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?")
    private val NON_RENDER = setOf(
        "defs", "clippath", "mask", "symbol", "style", "metadata", "title", "desc", "lineargradient",
        "radialgradient", "pattern", "filter", "marker", "image", "text", "script", "foreignobject", "switch"
    )

    private class Node(val tag: String, val attrs: HashMap<String, String>) {
        val kids = ArrayList<Node>()
        val text = StringBuilder()
    }

    private class Ctx(
        val fill: String?, val stroke: String?, val sw: Float, val cap: Int, val join: Int,
        val rule: String?, val op: Float, val fop: Float, val sop: Float, val m: Matrix
    )

    private class Raw(
        val d: String, val fill: Int?, val stroke: Int?, val sw: Float,
        val cap: Int, val join: Int, val eo: Boolean, val m: FloatArray?
    )

    private class Rule(val sel: String, val props: Map<String, String>)

    fun parse(textIn: String): Result {
        val root: Node = (try { buildTree(clean(textIn)) } catch (e: Exception) { null })
            ?: return Result(null, "الملف مو SVG صالح")
        val all = ArrayList<Node>()
        fun collect(n: Node) { all.add(n); n.kids.forEach { collect(it) } }
        collect(root)
        val idMap = HashMap<String, Node>()
        all.forEach { n -> n.attrs["id"]?.let { idMap.putIfAbsent(it, n) } }
        val rules = ArrayList<Rule>()
        all.filter { it.tag == "style" }.forEach { rules.addAll(parseCss(it.text.toString())) }

        val raws = ArrayList<Raw>()
        var budget = 4000
        var sawText = false

        fun resolve(s: String?, op: Float): Int? {
            val v = s?.trim()?.lowercase() ?: return withAlpha(0xFF000000.toInt(), op)
            if (v == "none" || v == "transparent" || v.isEmpty()) return null
            if (v == "currentcolor" || v == "inherit") return 0
            if (v.startsWith("url(")) {
                val id = Regex("#([^)'\"\\s]+)").find(v)?.groupValues?.get(1)
                val g = id?.let { idMap[it] }
                val c = g?.let { gradientColor(it, idMap, 0) }
                return if (c != null) withAlpha(c, op) else 0
            }
            return parseColorValue(v, op)
        }

        fun walk(n: Node, parent: Ctx, depth: Int) {
            if (depth > 40 || budget-- <= 0) return
            val tag = n.tag
            if (tag in NON_RENDER) { if (tag == "text") sawText = true; return }
            val props = HashMap<String, String>(n.attrs)
            val cls = n.attrs["class"]?.split(Regex("\\s+"))?.filter { it.isNotEmpty() } ?: emptyList()
            rules.forEach { r -> if (matches(r.sel, n, cls)) props.putAll(r.props) }
            n.attrs["style"]?.let { props.putAll(parseDecls(it)) }
            if (props["display"]?.trim() == "none" || props["visibility"]?.trim() == "hidden") return

            val m = Matrix(parent.m)
            props["transform"]?.let { applyTransform(m, it) }
            val ctx = Ctx(
                fill = props["fill"] ?: parent.fill,
                stroke = props["stroke"] ?: parent.stroke,
                sw = props["stroke-width"]?.let { NUM.find(it)?.value?.toFloatOrNull() } ?: parent.sw,
                cap = when (props["stroke-linecap"]?.trim()) { "round" -> 1; "square" -> 2; null -> parent.cap; else -> 0 },
                join = when (props["stroke-linejoin"]?.trim()) { "round" -> 1; "bevel" -> 2; null -> parent.join; else -> 0 },
                rule = props["fill-rule"] ?: parent.rule,
                op = parent.op * (props["opacity"]?.toFloatOrNull() ?: 1f),
                fop = props["fill-opacity"]?.toFloatOrNull() ?: parent.fop,
                sop = props["stroke-opacity"]?.toFloatOrNull() ?: parent.sop,
                m = m
            )

            when (tag) {
                "g", "svg", "a" -> n.kids.forEach { walk(it, ctx, depth + 1) }
                "use" -> {
                    val ref = (props["href"] ?: props["xlink:href"])?.trim()?.removePrefix("#") ?: return
                    val target = idMap[ref] ?: return
                    val um = Matrix(ctx.m)
                    um.preTranslate(
                        props["x"]?.let { NUM.find(it)?.value?.toFloatOrNull() } ?: 0f,
                        props["y"]?.let { NUM.find(it)?.value?.toFloatOrNull() } ?: 0f
                    )
                    val uc = Ctx(ctx.fill, ctx.stroke, ctx.sw, ctx.cap, ctx.join, ctx.rule, ctx.op, ctx.fop, ctx.sop, um)
                    if (target.tag == "symbol") target.kids.forEach { walk(it, uc, depth + 1) } else walk(target, uc, depth + 1)
                }
                else -> {
                    val d = shapeToPath(tag, props) ?: return
                    val isLine = tag == "line"
                    val fill = if (isLine) null else resolve(ctx.fill, ctx.op * ctx.fop)
                    val stroke = resolve(if (ctx.stroke == null) "none" else ctx.stroke, ctx.op * ctx.sop)
                    if (fill == null && stroke == null) return
                    val mv = matrixValues(ctx.m)
                    val scale = if (mv == null) 1f else sqrt(abs(mv[0] * mv[3] - mv[1] * mv[2]))
                    raws.add(
                        Raw(
                            d, fill, stroke, if (stroke != null) ctx.sw * scale else 0f,
                            ctx.cap, ctx.join, ctx.rule?.trim() == "evenodd", mv
                        )
                    )
                }
            }
        }

        val base = Ctx("black", null, 1f, 0, 0, null, 1f, 1f, 1f, Matrix())
        walk(root, base, 0)
        if (raws.isEmpty()) {
            return Result(null, if (sawText) "هذا الملف نص (text) مو شكل. حوّل النص لمسارات (Convert to outlines) وجرّب" else "ما لقيت أي شكل مرسوم بالملف")
        }

        // ---- الـ viewBox ----
        var vb: FloatArray? = null
        root.attrs["viewbox"]?.trim()?.split(Regex("[\\s,]+"))?.mapNotNull { it.toFloatOrNull() }?.let {
            if (it.size == 4 && it[2] > 0 && it[3] > 0) vb = it.toFloatArray()
        }
        if (vb == null) {
            val w = root.attrs["width"]?.let { NUM.find(it)?.value?.toFloatOrNull() }
            val h = root.attrs["height"]?.let { NUM.find(it)?.value?.toFloatOrNull() }
            if (w != null && h != null && w > 0 && h > 0) vb = floatArrayOf(0f, 0f, w, h)
        }
        // شكل بلا أبعاد: نحسبها من حدود الرسم الفعلية
        val bounds = HashMap<Raw, Rect?>()
        fun boundsOf(r: Raw): Rect? = bounds.getOrPut(r) {
            runCatching {
                val p = PathParser().parsePathString(r.d).toPath()
                if (r.m != null) p.transform(SvgDecor.composeMatrix(r.m))
                val b = p.getBounds()
                val pad = r.sw / 2f
                Rect(b.left - pad, b.top - pad, b.right + pad, b.bottom + pad)
            }.getOrNull()
        }
        if (vb == null) {
            var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var rr = -Float.MAX_VALUE; var bb = -Float.MAX_VALUE
            raws.forEach { r -> boundsOf(r)?.let { l = min(l, it.left); t = min(t, it.top); rr = max(rr, it.right); bb = max(bb, it.bottom) } }
            vb = if (rr > l && bb > t) floatArrayOf(l, t, rr - l, bb - t) else floatArrayOf(0f, 0f, 24f, 24f)
        }
        val box = vb!!

        // ---- ضغط البيانات حتى تنزل تحت الحد ----
        var list: List<Raw> = raws
        if (list.size > SvgDecor.MAX_PATHS) {
            list = list.sortedByDescending { r -> boundsOf(r)?.let { it.width * it.height } ?: 0f }.take(SvgDecor.MAX_PATHS)
                .let { keep -> raws.filter { it in keep } }
        }
        var dec = max(0, kotlin.math.ceil(-kotlin.math.log10(box[2] / 2000f)).toInt().coerceAtMost(3))
        while (true) {
            val compact = list.mapNotNull { r ->
                val d = compactD(r.d, dec)?.takeIf { s -> runCatching { PathParser().parsePathString(s) }.isSuccess } ?: return@mapNotNull null
                r to d
            }
            val total = compact.sumOf { it.second.length }
            if (total <= SvgDecor.MAX_TOTAL_D || (dec == 0 && list.size <= 1)) {
                if (compact.isEmpty()) return Result(null, "ما قدرت أقرا مسارات الملف")
                val paths = compact.map { (r, d) -> SvgPath(d, r.fill, r.stroke, r.sw, r.cap, r.join, r.eo, r.m) }
                return Result(SvgDecor(box[0], box[1], box[2], box[3], paths), null)
            }
            if (dec > 0) { dec--; continue }
            // آخر حل: نحذف أقل التفاصيل أهمية (مساحة صغيرة مقابل حجم بيانات كبير) بدفعات
            val ranked = compact.sortedBy { (r, d) -> ((boundsOf(r)?.let { it.width * it.height } ?: 0f) / d.length) }
            val drop = max(1, ranked.size / 8)
            val dropped = ranked.take(drop).map { it.first }.toSet()
            list = list.filter { it !in dropped }
            if (list.isEmpty()) return Result(null, "ما قدرت أصغّر الشكل")
        }
    }

    // ---------------------------- DOM ----------------------------

    private fun clean(t: String): String {
        var s = t.removePrefix("﻿")
        s = Regex("<!DOCTYPE[^\\[>]*(\\[[^\\]]*\\])?\\s*>", RegexOption.IGNORE_CASE).replace(s, "")
        s = Regex("&(?!(?:amp|lt|gt|quot|apos|#\\d+|#x[0-9a-fA-F]+);)[A-Za-z_][\\w.-]*;").replace(s, "")
        return s
    }

    private fun buildTree(text: String): Node? {
        val p = Xml.newPullParser()
        p.setInput(java.io.StringReader(text))
        val stack = ArrayList<Node>()
        var root: Node? = null
        var svgRoot: Node? = null
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    val a = HashMap<String, String>()
                    for (i in 0 until p.attributeCount) a[p.getAttributeName(i).lowercase()] = p.getAttributeValue(i)
                    val n = Node(p.name.lowercase().substringAfter(':'), a)
                    if (stack.isEmpty()) { if (root == null) root = n } else stack.last().kids.add(n)
                    if (n.tag == "svg" && svgRoot == null) svgRoot = n
                    stack.add(n)
                }
                XmlPullParser.END_TAG -> if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (stack.isNotEmpty() && stack.last().tag == "style") stack.last().text.append(p.text)
            }
            ev = p.next()
        }
        return svgRoot ?: root
    }

    private fun parseDecls(s: String): Map<String, String> {
        val out = HashMap<String, String>()
        s.split(';').forEach { kv ->
            val i = kv.indexOf(':')
            if (i > 0) out[kv.substring(0, i).trim().lowercase()] = kv.substring(i + 1).replace("!important", "").trim()
        }
        return out
    }

    private fun parseCss(css: String): List<Rule> {
        val out = ArrayList<Rule>()
        val stripped = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL).replace(css, "")
        Regex("([^{}@]+)\\{([^{}]*)\\}").findAll(stripped).forEach { m ->
            val props = parseDecls(m.groupValues[2])
            if (props.isNotEmpty()) m.groupValues[1].split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { out.add(Rule(it, props)) }
        }
        return out
    }

    private fun matches(sel: String, n: Node, cls: List<String>): Boolean {
        if (sel == "*") return true
        if (sel.startsWith("#")) return n.attrs["id"] == sel.substring(1)
        val dot = sel.indexOf('.')
        if (dot < 0) return n.tag == sel.lowercase()
        val tag = sel.substring(0, dot).lowercase()
        val c = sel.substring(dot + 1)
        if (c.contains('.') || c.contains(' ') || c.contains('>') || c.contains(':')) return false
        return (tag.isEmpty() || tag == n.tag) && c in cls
    }

    /** لون متوسط لتدرّج (linear/radial) من نقاط التوقف، مع اتباع href. */
    private fun gradientColor(g: Node, idMap: Map<String, Node>, depth: Int): Int? {
        if (depth > 6) return null
        val stops = g.kids.filter { it.tag == "stop" }
        if (stops.isEmpty()) {
            val ref = (g.attrs["href"] ?: g.attrs["xlink:href"])?.removePrefix("#") ?: return null
            return idMap[ref]?.let { gradientColor(it, idMap, depth + 1) }
        }
        var r = 0; var gr = 0; var b = 0; var a = 0
        stops.forEach { st ->
            val props = HashMap<String, String>(st.attrs)
            st.attrs["style"]?.let { props.putAll(parseDecls(it)) }
            val c = parseColorValue((props["stop-color"] ?: "black").trim().lowercase(), props["stop-opacity"]?.toFloatOrNull() ?: 1f) ?: 0xFF000000.toInt()
            a += c ushr 24; r += (c shr 16) and 0xFF; gr += (c shr 8) and 0xFF; b += c and 0xFF
        }
        val n = stops.size
        return ((a / n) shl 24) or ((r / n) shl 16) or ((gr / n) shl 8) or (b / n)
    }

    // ---------------------------- ألوان ----------------------------

    private val NAMED = mapOf(
        "black" to 0xFF000000.toInt(), "white" to 0xFFFFFFFF.toInt(), "red" to 0xFFFF0000.toInt(),
        "green" to 0xFF008000.toInt(), "blue" to 0xFF0000FF.toInt(), "yellow" to 0xFFFFFF00.toInt(),
        "orange" to 0xFFFFA500.toInt(), "gray" to 0xFF808080.toInt(), "grey" to 0xFF808080.toInt(),
        "purple" to 0xFF800080.toInt(), "pink" to 0xFFFFC0CB.toInt(), "brown" to 0xFFA52A2A.toInt(),
        "gold" to 0xFFFFD700.toInt(), "silver" to 0xFFC0C0C0.toInt(), "teal" to 0xFF008080.toInt(),
        "cyan" to 0xFF00FFFF.toInt(), "aqua" to 0xFF00FFFF.toInt(), "magenta" to 0xFFFF00FF.toInt(),
        "lime" to 0xFF00FF00.toInt(), "navy" to 0xFF000080.toInt(), "maroon" to 0xFF800000.toInt()
    )

    private fun parseColorValue(v: String, op: Float): Int? {
        NAMED[v]?.let { return withAlpha(it, op) }
        if (v.startsWith("rgb")) {
            val n = NUM.findAll(v).map { it.value.toFloat() }.toList()
            if (n.size >= 3) {
                val pct = v.contains('%')
                fun ch(x: Float) = (if (pct) x * 2.55f else x).toInt().coerceIn(0, 255)
                val a = if (n.size >= 4) n[3].coerceIn(0f, 1f) else 1f
                return withAlpha((0xFF shl 24) or (ch(n[0]) shl 16) or (ch(n[1]) shl 8) or ch(n[2]), op * a)
            }
        }
        val c = runCatching {
            var h = v
            if (h.startsWith("#") && (h.length == 4 || h.length == 5)) h = "#" + h.substring(1).map { "$it$it" }.joinToString("")
            android.graphics.Color.parseColor(h)
        }.getOrNull() ?: return withAlpha(0xFF000000.toInt(), op)
        // parseColor يدعم #RRGGBBAA كـ #AARRGGBB، وهذا مطابق لصيغة SVG المعتمدة بأندرويد
        return withAlpha(c, op)
    }

    private fun withAlpha(c: Int, op: Float): Int {
        val a = ((c ushr 24) * op.coerceIn(0f, 1f)).toInt().coerceIn(1, 255)
        return (a shl 24) or (c and 0xFFFFFF)
    }

    // ---------------------------- أشكال → مسارات ----------------------------

    private fun matrixValues(m: Matrix): FloatArray? {
        if (m.isIdentity) return null
        val v = FloatArray(9); m.getValues(v)
        return floatArrayOf(v[Matrix.MSCALE_X], v[Matrix.MSKEW_Y], v[Matrix.MSKEW_X], v[Matrix.MSCALE_Y], v[Matrix.MTRANS_X], v[Matrix.MTRANS_Y])
    }

    private fun f(a: Map<String, String>, k: String, def: Float = 0f): Float =
        a[k]?.let { NUM.find(it)?.value?.toFloatOrNull() } ?: def

    private fun shapeToPath(tag: String, a: Map<String, String>): String? = when (tag) {
        "path" -> a["d"]?.takeIf { it.isNotBlank() }
        "rect" -> {
            val x = f(a, "x"); val y = f(a, "y"); val w = f(a, "width"); val h = f(a, "height")
            var rx = f(a, "rx", -1f); var ry = f(a, "ry", -1f)
            if (rx < 0 && ry >= 0) rx = ry
            if (ry < 0 && rx >= 0) ry = rx
            rx = rx.coerceIn(0f, w / 2); ry = ry.coerceIn(0f, h / 2)
            if (w <= 0 || h <= 0) null
            else if (rx <= 0f || ry <= 0f) "M$x $y H${x + w} V${y + h} H$x Z"
            else "M${x + rx} $y H${x + w - rx} A$rx $ry 0 0 1 ${x + w} ${y + ry} V${y + h - ry} " +
                "A$rx $ry 0 0 1 ${x + w - rx} ${y + h} H${x + rx} A$rx $ry 0 0 1 $x ${y + h - ry} V${y + ry} A$rx $ry 0 0 1 ${x + rx} $y Z"
        }
        "circle" -> ellipse(f(a, "cx"), f(a, "cy"), f(a, "r"), f(a, "r"))
        "ellipse" -> ellipse(f(a, "cx"), f(a, "cy"), f(a, "rx"), f(a, "ry"))
        "line" -> "M${f(a, "x1")} ${f(a, "y1")} L${f(a, "x2")} ${f(a, "y2")}"
        "polygon", "polyline" -> {
            val n = NUM.findAll(a["points"] ?: "").map { it.value }.toList()
            if (n.size < 4) null
            else "M" + n.chunked(2).filter { it.size == 2 }.joinToString(" L") { "${it[0]} ${it[1]}" } + if (tag == "polygon") " Z" else ""
        }
        else -> null
    }

    private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float): String? =
        if (rx <= 0 || ry <= 0) null
        else "M${cx - rx} $cy A$rx $ry 0 1 0 ${cx + rx} $cy A$rx $ry 0 1 0 ${cx - rx} $cy Z"

    /** يطبّق transform="translate(..) scale(..) rotate(..) matrix(..) skewX/skewY" على [m]. */
    private fun applyTransform(m: Matrix, t: String) {
        Regex("(\\w+)\\s*\\(([^)]*)\\)").findAll(t).forEach { r ->
            val n = NUM.findAll(r.groupValues[2]).map { it.value.toFloat() }.toList()
            when (r.groupValues[1].lowercase()) {
                "translate" -> if (n.isNotEmpty()) m.preTranslate(n[0], n.getOrElse(1) { 0f })
                "scale" -> if (n.isNotEmpty()) m.preScale(n[0], n.getOrElse(1) { n[0] })
                "rotate" -> if (n.isNotEmpty()) {
                    if (n.size >= 3) m.preRotate(n[0], n[1], n[2]) else m.preRotate(n[0])
                }
                "skewx" -> if (n.isNotEmpty()) m.preSkew(kotlin.math.tan(Math.toRadians(n[0].toDouble())).toFloat(), 0f)
                "skewy" -> if (n.isNotEmpty()) m.preSkew(0f, kotlin.math.tan(Math.toRadians(n[0].toDouble())).toFloat())
                "matrix" -> if (n.size == 6) {
                    val l = Matrix()
                    l.setValues(floatArrayOf(n[0], n[2], n[4], n[1], n[3], n[5], 0f, 0f, 1f))
                    m.preConcat(l)
                }
            }
        }
    }

    /**
     * يعيد كتابة بيانات المسار بشكل مضغوط ودقة [dec] خانة عشرية.
     * يتعامل صح مع علامات الأقواس (arc flags) الملصوقة بالأرقام. يرجع null إذا البيانات تالفة.
     */
    private fun compactD(d: String, dec: Int): String? {
        val sb = StringBuilder()
        var i = 0
        val n = d.length
        var cmd = ' '
        var argIdx = 0
        fun fmt(v: Double): String {
            var s = String.format(java.util.Locale.US, "%.${dec}f", v)
            if (s.contains('.')) s = s.trimEnd('0').trimEnd('.')
            if (s == "-0" || s.isEmpty()) s = "0"
            if (s.startsWith("0.")) s = s.substring(1) else if (s.startsWith("-0.")) s = "-" + s.substring(2)
            return s
        }
        fun emit(s: String) {
            if (sb.isNotEmpty() && !sb.last().isLetter() && !s.startsWith("-")) sb.append(' ')
            sb.append(s)
        }
        while (i < n) {
            val c = d[i]
            if (c == ' ' || c == ',' || c == '\n' || c == '\t' || c == '\r') { i++; continue }
            if (c.isLetter() && c != 'e' && c != 'E') {
                if ("MmLlHhVvCcSsQqTtAaZz".indexOf(c) < 0) return null
                cmd = c; argIdx = 0; i++; sb.append(c); continue
            }
            if (cmd == ' ') return null
            val isArc = cmd == 'a' || cmd == 'A'
            val pos = if (isArc) argIdx % 7 else -1
            if (isArc && (pos == 3 || pos == 4)) {
                if (c != '0' && c != '1') return null
                emit(c.toString()); i++; argIdx++
                continue
            }
            val m = NUM.find(d, i) ?: return null
            if (m.range.first != i) return null
            val v = m.value.toDoubleOrNull() ?: return null
            emit(fmt(v))
            i = m.range.last + 1
            argIdx++
        }
        return sb.toString().takeIf { it.isNotEmpty() }
    }
}
