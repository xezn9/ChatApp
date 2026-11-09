package com.chatapp.messenger.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * بحث أصوات تيك توك (مصدر إضافي بعد يوتيوب). مجاني وبدون مفتاح عبر خدمة tikwm (غير رسمية).
 *
 * مصمَّم حتى ما يفشل البحث أبداً: كل شي داخل runCatching + مهلة قصيرة، وإذا الخدمة وقعت أو تغيّرت
 * ترجع قائمة فارغة والبحث يكمل بيوتيوب/iTunes عادي.
 *
 * لو الخدمة تغيّر عنوانها: غيّر [API] بس.
 *
 * نخزّن بالقصة "tt:VIDEO_ID" (مو الرابط المباشر لأنه ينتهي)، ونحلّه وقت التشغيل.
 */
object TikTokMusic {
    const val PREFIX = "tt:"
    const val SOURCE = "tt"

    /** عنوان الخدمة - مكان واحد للتغيير إذا انكسر */
    private const val API = "https://www.tikwm.com/api"
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
    private const val SEARCH_TIMEOUT_MS = 9_000L

    // الخدمة المجانية تسمح تقريباً بطلب كل ثانية: نفرض فاصل بين الطلبات حتى ما ننحظر
    private val gate = Mutex()
    private var lastCall = 0L

    /** حالة آخر بحث بتيك توك (تظهر بلوحة الموسيقى لتشخيص أي خلل بدون Logcat) */
    var status by mutableStateOf("")
        private set

    private val direct = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, String>>()

    private suspend fun get(url: String): JSONObject? = gate.withLock {
        val wait = 1_100L - (System.currentTimeMillis() - lastCall)
        if (wait > 0) delay(wait)
        lastCall = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 7_000
            c.readTimeout = 7_000
            c.setRequestProperty("User-Agent", UA)
            try {
                val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                if (j.optInt("code", -1) == 0) j else {
                    val msg = j.optString("msg").ifBlank { j.toString().take(80) }
                    android.util.Log.w("TikTokMusic", "رد غير ناجح: " + j.toString().take(200))
                    throw java.io.IOException("رد الموقع: $msg")
                }
            } finally {
                c.disconnect()
            }
        }
    }

    private fun abs(u: String): String = when {
        u.isBlank() -> ""
        u.startsWith("http") -> u
        u.startsWith("//") -> "https:$u"
        else -> "https://www.tikwm.com$u"
    }

    /** بحث: أصوات (مو فيديوهات) مدمجة بدون تكرار. أي خلل = قائمة فارغة */
    suspend fun search(term: String): List<MusicTrack> {
        if (term.isBlank()) return emptyList()
        status = "تيك توك: جاري البحث..."
        val res = withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
            runCatching {
                val q = URLEncoder.encode(term, "UTF-8")
                val data = get("$API/feed/search?keywords=$q&count=20&cursor=0&web=1&hd=0")?.optJSONObject("data")
                val vids = data?.optJSONArray("videos")
                if (vids == null) {
                    status = "تيك توك: الرد وصل بس بدون نتائج (الموقع غيّر الصيغة؟)"
                    return@runCatching emptyList<MusicTrack>()
                }
                val out = ArrayList<MusicTrack>()
                val seen = HashSet<String>()
                for (i in 0 until vids.length()) {
                    val v = vids.optJSONObject(i) ?: continue
                    val id = v.optString("video_id").ifBlank { v.optString("aweme_id") }
                    val mi = v.optJSONObject("music_info")
                    val play = abs(mi?.optString("play").orEmpty().ifBlank { v.optString("music") })
                    if (id.isBlank() || play.isBlank()) continue
                    val soundKey = mi?.optString("id").orEmpty().ifBlank { id }
                    if (!seen.add(soundKey)) continue
                    val title = mi?.optString("title").orEmpty().ifBlank { v.optString("title") }.trim()
                    if (title.isBlank()) continue
                    val artist = mi?.optString("author").orEmpty()
                        .ifBlank { v.optJSONObject("author")?.optString("nickname").orEmpty() }
                    val cover = abs(mi?.optString("cover").orEmpty().ifBlank { v.optString("cover") })
                    val dur = (mi?.optInt("duration", 0) ?: 0).let { if (it > 0) it else v.optInt("duration", 30) }
                    direct[id] = System.currentTimeMillis() to play
                    out.add(MusicTrack(PREFIX + id, title, artist, cover, PREFIX + id, SOURCE, dur.coerceIn(5, 600)))
                }
                out
            }.onFailure {
                android.util.Log.w("TikTokMusic", "فشل البحث: $it")
                status = when (it) {
                    is java.net.UnknownHostException -> "تيك توك: ما كدرت أوصل للموقع (محجوب أو ما في إنترنت)"
                    is java.net.SocketTimeoutException -> "تيك توك: الموقع بطيء/ما رد (مهلة)"
                    else -> "تيك توك: " + (it.message ?: it.javaClass.simpleName).take(90)
                }
            }.getOrDefault(emptyList())
        }
        if (res == null) status = "تيك توك: انتهت المهلة (النت بطيء أو الموقع محجوب)"
        else if (res.isNotEmpty()) status = "تيك توك: ${res.size} نتيجة"
        else if (status.endsWith("جاري البحث...")) status = "تيك توك: ما لقيت نتائج لهذا البحث"
        return res ?: emptyList()
    }

    /** يرجّع رابط الصوت المباشر لفيديو تيك توك (من نتائج البحث المخزّنة أو من الخدمة) */
    suspend fun resolve(videoId: String): String? {
        val hit = direct[videoId]
        if (hit != null && System.currentTimeMillis() - hit.first < 3_600_000L) return hit.second
        return runCatching {
            val u = URLEncoder.encode("https://www.tiktok.com/@_/video/$videoId", "UTF-8")
            val d = get("$API/?url=$u&hd=0")?.optJSONObject("data") ?: return@runCatching null
            val play = abs(d.optJSONObject("music_info")?.optString("play").orEmpty().ifBlank { d.optString("music") })
            if (play.isBlank()) null else { direct[videoId] = System.currentTimeMillis() to play; play }
        }.getOrNull()
    }
}
