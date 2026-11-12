package com.chatapp.messenger.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * بحث أصوات تيك توك (مصدر إضافي بعد يوتيوب) عبر tikwmapi.com (بمفتاح API).
 *
 * مصمَّم حتى ما يفشل البحث أبداً: كل شي داخل runCatching + مهلة، وإذا الخدمة وقعت أو انتهت الحصة
 * ترجع قائمة فارغة والبحث يكمل بيوتيوب/iTunes عادي، وسبب الفشل يظهر بسطر صغير بلوحة الموسيقى.
 *
 * نخزّن بالقصة "tt:VIDEO_ID" (مو الرابط المباشر لأنه ينتهي)، ونحلّه وقت التشغيل.
 */
object TikTokMusic {
    const val PREFIX = "tt:"
    const val SOURCE = "tt"

    private const val API = "https://api.tikwmapi.com"

    /**
     * مفتاح الـAPI. تنبيه: أي مفتاح داخل التطبيق يكدر أي أحد يفكّه ويستعمله (يستهلك حصتك).
     * الحل الأمن لاحقاً: ينحط بسيرفر وسيط (مثل push-relay) والتطبيق يطلبه منه.
     */
    private const val API_KEY = "57ad5dae0a3e48c47349ce43e85c862f"

    private const val SEARCH_TIMEOUT_MS = 9_000L

    /** حالة آخر بحث بتيك توك (تظهر بلوحة الموسيقى لتشخيص أي خلل). StateFlow آمن بأي thread. */
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    private val direct = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, String>>()

    private class ApiError(msg: String) : java.io.IOException(msg)

    private suspend fun get(url: String): JSONObject = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 7_000
        c.readTimeout = 7_000
        c.setRequestProperty("x-tikwmapi-key", API_KEY)
        c.setRequestProperty("User-Agent", "Pigeon/2.24")
        try {
            // أخطاء 4xx (حد الطلبات/انتهاء الحصة) ترجع JSON بـerrorStream
            val stream = if (c.responseCode in 200..299) c.inputStream else (c.errorStream ?: c.inputStream)
            val j = JSONObject(stream.bufferedReader().use { it.readText() })
            val code = j.optInt("code", -1)
            if (code != 0) {
                throw ApiError(
                    when (code) {
                        10401 -> "المفتاح مفقود أو غلط"
                        10429 -> "طلبات كثيرة بالدقيقة"
                        10430 -> "انتهت حصة الـAPI"
                        else -> j.optString("msg").ifBlank { "كود $code" }
                    }
                )
            }
            j
        } finally {
            c.disconnect()
        }
    }

    private fun abs(u: String): String = when {
        u.isBlank() -> ""
        u.startsWith("http") -> u
        u.startsWith("//") -> "https:$u"
        else -> ""
    }

    /** بحث: أصوات (مو فيديوهات) بدون تكرار. أي خلل = قائمة فارغة + سبب بـ[status] */
    suspend fun search(term: String): List<MusicTrack> {
        if (term.isBlank()) return emptyList()
        _status.value = "تيك توك: جاري البحث..."
        var failure: String? = null
        val res = withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
            runCatching {
                val q = URLEncoder.encode(term, "UTF-8")
                val vids = get("$API/feed/search?keywords=$q&count=30&cursor=0")
                    .optJSONObject("data")?.optJSONArray("videos")
                if (vids == null) {
                    failure = "الرد وصل بس بدون نتائج"
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
                failure = when (it) {
                    is java.net.UnknownHostException -> "ما كدرت أوصل للموقع (محجوب أو ما في إنترنت)"
                    is java.net.SocketTimeoutException -> "الموقع بطيء/ما رد"
                    is ApiError -> it.message
                    else -> (it.message ?: it.javaClass.simpleName).take(90)
                }
            }.getOrDefault(emptyList())
        }
        _status.value = when {
            res == null -> "تيك توك: انتهت المهلة (النت بطيء)"
            res.isNotEmpty() -> "تيك توك: ${res.size} نتيجة"
            failure != null -> "تيك توك: $failure"
            else -> "تيك توك: ما لقيت نتائج لهذا البحث"
        }
        return res ?: emptyList()
    }

    /** يرجّع رابط الصوت المباشر لفيديو تيك توك (من نتائج البحث المخزّنة أو من الخدمة) */
    suspend fun resolve(videoId: String): String? {
        val hit = direct[videoId]
        if (hit != null && System.currentTimeMillis() - hit.first < 3_600_000L) return hit.second
        return runCatching {
            val d = get("$API/?url=$videoId").optJSONObject("data") ?: return@runCatching null
            val play = abs(d.optJSONObject("music_info")?.optString("play").orEmpty().ifBlank { d.optString("music") })
            if (play.isBlank()) null else { direct[videoId] = System.currentTimeMillis() to play; play }
        }.getOrNull()
    }
}
