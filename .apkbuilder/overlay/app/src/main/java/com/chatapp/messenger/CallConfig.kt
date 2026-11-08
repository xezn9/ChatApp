package com.chatapp.messenger

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.webrtc.PeerConnection
import java.net.HttpURLConnection
import java.net.URL

/** إعدادات المكالمات الصوتية (WebRTC). STUN من Google مجاني. TURN اختياري (شوف push-relay/CALLS.md). */
object CallConfig {
    private val STUN = listOf(
        "stun:stun.l.google.com:19302",
        "stun:stun1.l.google.com:19302",
        "stun:stun2.l.google.com:19302"
    )

    /** رابط credentials من Metered Open Relay (مجاني). مثال: https://اسمك.metered.live/api/v1/turn/credentials?apiKey=XXXX */
    const val TURN_API_URL = ""

    /** أو بيانات ثابتة: روابط مفصولة بفاصلة، مثل "turn:host:80,turn:host:443?transport=tcp" */
    const val TURN_URLS = ""
    const val TURN_USER = ""
    const val TURN_PASS = ""

    /** كم ثانية يرن عند المتصل قبل ما تنعتبر "ما رد" */
    const val RING_TIMEOUT_MS = 45_000L

    private var cached: List<PeerConnection.IceServer>? = null
    private var cachedAt = 0L

    suspend fun iceServers(): List<PeerConnection.IceServer> {
        val c = cached
        if (c != null && System.currentTimeMillis() - cachedAt < 10 * 60_000L) return c

        val list = mutableListOf<PeerConnection.IceServer>()
        list += PeerConnection.IceServer.builder(STUN).createIceServer()

        if (TURN_URLS.isNotBlank()) {
            list += PeerConnection.IceServer.builder(TURN_URLS.split(',').map { it.trim() }.filter { it.isNotEmpty() })
                .setUsername(TURN_USER).setPassword(TURN_PASS).createIceServer()
        }
        if (TURN_API_URL.isNotBlank()) {
            runCatching { fetchTurn() }.getOrNull()?.let { list += it }
        }
        cached = list
        cachedAt = System.currentTimeMillis()
        return list
    }

    private suspend fun fetchTurn(): List<PeerConnection.IceServer> = withContext(Dispatchers.IO) {
        val c = URL(TURN_API_URL).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 4000
            c.readTimeout = 4000
            val arr = JSONArray(c.inputStream.bufferedReader().use { it.readText() })
            val out = mutableListOf<PeerConnection.IceServer>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val urls = mutableListOf<String>()
                when (val u = o.opt("urls") ?: o.opt("url")) {
                    is String -> urls += u
                    is JSONArray -> for (k in 0 until u.length()) urls += u.getString(k)
                }
                if (urls.isEmpty()) continue
                val b = PeerConnection.IceServer.builder(urls)
                if (o.has("username")) b.setUsername(o.getString("username"))
                if (o.has("credential")) b.setPassword(o.getString("credential"))
                out += b.createIceServer()
            }
            out
        } finally {
            c.disconnect()
        }
    }
}
