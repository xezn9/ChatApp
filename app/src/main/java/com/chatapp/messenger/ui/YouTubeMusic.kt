package com.chatapp.messenger.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * بحث يوتيوب + استخراج رابط الصوت (بدون مفتاح API) عبر واجهة Innertube الداخلية.
 * ملاحظة: هذي واجهة غير رسمية، يوتيوب ممكن يغيّرها بأي وقت. لهذا نخزّن بالقصة "yt:VIDEO_ID"
 * وليس الرابط المباشر (الروابط المباشرة تنتهي بعد ساعات)، ونستخرج الرابط وقت التشغيل.
 */
object YouTubeMusic {
    const val PREFIX = "yt:"
    private const val BASE = "https://www.youtube.com/youtubei/v1"
    private const val MAX_SECONDS = 600

    private class Client(val name: String, val version: String, val ua: String, val extra: JSONObject.() -> Unit)

    private val webClient = Client(
        "WEB", "2.20250301.01.00",
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
    ) { }

    // عملاء استخراج الصوت: نجرّبهم بالترتيب لين أحدهم يعطي رابط مباشر
    private val playerClients = listOf(
        Client(
            "ANDROID_VR", "1.60.19",
            "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
        ) {
            put("deviceMake", "Oculus"); put("deviceModel", "Quest 3")
            put("osName", "Android"); put("osVersion", "12L"); put("androidSdkVersion", 32)
        },
        Client(
            "ANDROID", "20.10.38",
            "com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip"
        ) {
            put("osName", "Android"); put("osVersion", "14"); put("androidSdkVersion", 34)
        }
    )

    class StreamInfo(val url: String, val userAgent: String, val bitrate: Int = 0)

    private fun post(endpoint: String, body: JSONObject, ua: String): String {
        val c = URL("$BASE/$endpoint?prettyPrint=false").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 10_000
        c.readTimeout = 12_000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("User-Agent", ua)
        return try {
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun context(cl: Client): JSONObject = JSONObject().put(
        "client",
        JSONObject().put("clientName", cl.name).put("clientVersion", cl.version)
            .put("hl", "ar").put("gl", "IQ").also(cl.extra)
    )

    private fun parseDuration(s: String): Int {
        if (s.isBlank()) return 0
        val parts = s.split(":").mapNotNull { it.trim().toIntOrNull() }
        var sec = 0
        for (p in parts) sec = sec * 60 + p
        return sec
    }

    /** نمشي بكل الـJSON ونجمع أي videoRenderer / reelItem / shortsLockup بدون ما نعتمد على شكل ثابت */
    /** كتل الإعلانات/الفيديوهات المموّلة بنتائج بحث يوتيوب: نتجاهلها بالكامل */
    private val adKeys = setOf(
        "adSlotRenderer", "promotedSparklesWebRenderer", "promotedSparklesTextSearchRenderer",
        "searchPyvRenderer", "promotedVideoRenderer", "displayAdRenderer", "videoMastheadAdV3Renderer",
        "inFeedAdLayoutRenderer", "compactPromotedVideoRenderer", "adSlotAndLayoutRenderer"
    )

    private fun collect(node: Any?, out: MutableList<MusicTrack>, seen: MutableSet<String>) {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("videoRenderer")?.takeIf { !it.has("adBadge") && !it.has("promotedSparklesWebRenderer") }?.let { v ->
                    val id = v.optString("videoId")
                    val title = v.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
                    val artist = v.optJSONObject("ownerText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
                    val len = parseDuration(v.optJSONObject("lengthText")?.optString("simpleText").orEmpty())
                    if (id.isNotBlank() && title.isNotBlank() && len in 1..MAX_SECONDS && seen.add(id)) {
                        out.add(MusicTrack(PREFIX + id, title, artist, "https://i.ytimg.com/vi/$id/mqdefault.jpg", PREFIX + id, "yt", len))
                    }
                }
                node.optJSONObject("shortsLockupViewModel")?.let { v ->
                    val id = v.optJSONObject("onTap")?.optJSONObject("innertubeCommand")
                        ?.optJSONObject("reelWatchEndpoint")?.optString("videoId").orEmpty()
                    val title = v.optJSONObject("overlayMetadata")?.optJSONObject("primaryText")?.optString("content").orEmpty()
                    if (id.isNotBlank() && title.isNotBlank() && seen.add(id)) {
                        out.add(MusicTrack(PREFIX + id, title, "Shorts", "https://i.ytimg.com/vi/$id/mqdefault.jpg", PREFIX + id, "yt", 60))
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k in adKeys) continue
                    collect(node.opt(k), out, seen)
                }
            }
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), out, seen)
        }
    }

    private fun searchRaw(q: String, params: String): List<MusicTrack> {
        val body = JSONObject()
            .put("context", context(webClient))
            .put("query", q)
            .put("params", params)
        val out = ArrayList<MusicTrack>()
        collect(JSONObject(post("search", body, webClient.ua)), out, HashSet())
        return out
    }

    /**
     * بحث يوتيوب: أغاني كاملة (لين 10 دقايق) + Shorts، كل نوع ببحث مستقل وبعدين ندمجهم.
     * ملاحظة: فلتر "فيديوهات" يشيل الـShorts، فبحث الـShorts يستخدم فلتر "أقل من 4 دقايق" (مثل أول نسخة).
     */
    suspend fun search(term: String): List<MusicTrack> = withContext(Dispatchers.IO) {
        coroutineScope {
            val full = async { runCatching { searchRaw(term, "EgIQAQ%3D%3D") }.getOrDefault(emptyList()) }
            val shorts = async { runCatching { searchRaw("$term #shorts", "EgIYAQ%3D%3D") }.getOrDefault(emptyList()) }
            val a = full.await().filter { it.durationSec > 90 }
            val b = shorts.await().filter { it.durationSec <= 90 }
            val seen = HashSet<String>()
            val out = ArrayList<MusicTrack>()
            for (i in 0 until maxOf(a.size, b.size)) {
                if (i < a.size && seen.add(a[i].id)) out.add(a[i])
                if (i < b.size && seen.add(b[i].id)) out.add(b[i])
            }
            out.take(36)
        }
    }

    /**
     * ينزّل بداية الأغنية فقط (حوالي [seconds] ثانية) لملف محلي: صغير (100-400KB) ويشتغل فوراً بدون بافر.
     * نستخدم باراميتر range اللي يستخدمه تطبيق يوتيوب نفسه.
     */
    fun downloadHead(info: StreamInfo, dest: File, seconds: Int = STORY_SECONDS + 10): Boolean {
        if (info.bitrate <= 0) return false
        val bytes = (info.bitrate / 8L * seconds * 5 / 4).coerceAtLeast(120_000L)
        val sep = if (info.url.contains("?")) "&" else "?"
        val c = URL("${info.url}${sep}range=0-${bytes - 1}").openConnection() as HttpURLConnection
        c.connectTimeout = 8_000
        c.readTimeout = 10_000
        c.setRequestProperty("User-Agent", info.userAgent)
        val tmp = File(dest.path + ".tmp")
        return try {
            c.inputStream.use { inp -> tmp.outputStream().use { out -> inp.copyTo(out) } }
            if (tmp.length() > 4096 && tmp.renameTo(dest)) true else { tmp.delete(); false }
        } catch (e: Exception) {
            tmp.delete(); false
        } finally {
            c.disconnect()
        }
    }

    /** تجهيز مسبق (بالخلفية) لأول نتائج يوتيوب: رابط الصوت + نسخة البداية، حتى يشتغل الضغط فوراً */
    suspend fun prefetch(ids: List<String>, headFile: (String) -> File?) = withContext(Dispatchers.IO) {
        val sem = Semaphore(3)
        coroutineScope {
            ids.map { id ->
                async {
                    sem.withPermit {
                        runCatching {
                            val f = headFile(id) ?: return@runCatching
                            if (f.exists() && f.length() > 4096) return@runCatching
                            val info = resolveCached(id) ?: return@runCatching
                            downloadHead(info, f)
                        }
                    }
                }
            }.awaitAll()
        }
        Unit
    }

    // نخزّن روابط الصوت بالذاكرة (صالحة عدة ساعات) حتى المعاينة ثم الاختيار ما يعيدون الطلب
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, StreamInfo>>()

    suspend fun resolveCached(videoId: String): StreamInfo? {
        val hit = cache[videoId]
        if (hit != null && System.currentTimeMillis() - hit.first < 2 * 3600_000L) return hit.second
        val r = resolve(videoId) ?: return null
        cache[videoId] = System.currentTimeMillis() to r
        return r
    }

    /** يرجّع رابط الصوت المباشر (أقل جودة مقبولة حتى يكون التحميل خفيف) */
    suspend fun resolve(videoId: String): StreamInfo? = withContext(Dispatchers.IO) {
        for (cl in playerClients) {
            val r = runCatching {
                val body = JSONObject()
                    .put("context", context(cl))
                    .put("videoId", videoId)
                    .put("contentCheckOk", true).put("racyCheckOk", true)
                val sd = JSONObject(post("player", body, cl.ua)).optJSONObject("streamingData") ?: return@runCatching null
                var best: String? = null
                var bestRate = Int.MAX_VALUE
                val adaptive = sd.optJSONArray("adaptiveFormats")
                if (adaptive != null) {
                    for (i in 0 until adaptive.length()) {
                        val f = adaptive.optJSONObject(i) ?: continue
                        val mime = f.optString("mimeType")
                        val url = f.optString("url")
                        // m4a فقط (MediaPlayer ما يدعم webm/opus بكل الأجهزة)
                        if (!mime.startsWith("audio/mp4") || url.isBlank()) continue
                        val rate = f.optInt("bitrate", Int.MAX_VALUE)
                        if (rate in 32_000 until bestRate) { best = url; bestRate = rate }
                    }
                }
                if (best == null) {
                    // احتياط: صيغة فيديو+صوت مدمجة
                    val muxed = sd.optJSONArray("formats")
                    if (muxed != null) for (i in 0 until muxed.length()) {
                        val u = muxed.optJSONObject(i)?.optString("url").orEmpty()
                        if (u.isNotBlank()) { best = u; break }
                    }
                }
                best?.let { StreamInfo(it, cl.ua, if (bestRate == Int.MAX_VALUE) 0 else bestRate) }
            }.getOrNull()
            if (r != null) return@withContext r
        }
        null
    }
}
