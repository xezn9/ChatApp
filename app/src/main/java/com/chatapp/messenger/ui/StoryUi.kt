package com.chatapp.messenger.ui

import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.ActiveStory
import com.chatapp.messenger.Profile
import com.chatapp.messenger.Repo
import com.chatapp.messenger.STORY_MS
import com.chatapp.messenger.Story
import com.chatapp.messenger.Status
import com.kyant.backdrop.Backdrop
import androidx.compose.ui.unit.Dp
import com.chatapp.messenger.ui.glass.GlassButton
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** أقصى مدة للقصة بالثواني (وأقصى مدة للموسيقى عليها) */
const val STORY_SECONDS = 15

// ======================= الخلفيات والنص =======================

/** خلفيات القصة النصية (تدرجات). رقم الخلفية يتخزن بالقصة بحقل bg */
internal val StoryBgs = listOf(
    listOf(Color(0xFF3B4A57), Color(0xFF14181D)),
    listOf(Color(0xFF8A5CFF), Color(0xFFFF2D92)),
    listOf(Color(0xFF0A84FF), Color(0xFF00E5FF)),
    listOf(Color(0xFFFF9F0A), Color(0xFFFF453A)),
    listOf(Color(0xFF30D158), Color(0xFF0B6E4F)),
    listOf(Color(0xFF1B1464), Color(0xFF6A11CB)),
    listOf(Color(0xFFFF512F), Color(0xFFDD2476)),
    listOf(Color(0xFF0F2027), Color(0xFF2C5364))
)

internal fun storyBgBrush(i: Int): Brush =
    Brush.linearGradient(StoryBgs[i.coerceIn(0, StoryBgs.lastIndex)])

/** حجم الخط يصغر تدريجياً كل ما طال النص (مثل تيك توك) */
internal fun storyFontSize(len: Int) = when {
    len <= 30 -> 36.sp
    len <= 80 -> 30.sp
    len <= 140 -> 24.sp
    else -> 20.sp
}

internal val StoryTextShadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(0.45f), blurRadius = 10f)

// ======================= الفلاتر =======================

/**
 * فلتر صورة (مصفوفة ألوان). ملاحظة: فلاتر تيك توك الأصلية ملك لتيك توك وما تنتاح للتطبيقات،
 * فهذي فلاتر مبنية بنفس الأسماء/التصنيفات وتعطي أجواء قريبة منها. تقدر تضيف فلتر جديد بسطر واحد بالقائمة.
 */
class StoryFilter(val id: String, val name: String, val cat: String, val matrix: ColorMatrix)

/** مصفوفة ألوان: تشبّع، تباين، سطوع، ومضاعف لكل قناة (دفء/برودة) */
private fun cm(
    sat: Float = 1f, con: Float = 1f, bri: Float = 0f,
    r: Float = 1f, g: Float = 1f, b: Float = 1f
): ColorMatrix {
    val ir = 0.213f
    val ig = 0.715f
    val ib = 0.072f
    val rows = arrayOf(
        floatArrayOf(ir * (1 - sat) + sat, ig * (1 - sat), ib * (1 - sat)),
        floatArrayOf(ir * (1 - sat), ig * (1 - sat) + sat, ib * (1 - sat)),
        floatArrayOf(ir * (1 - sat), ig * (1 - sat), ib * (1 - sat) + sat)
    )
    val gains = floatArrayOf(r, g, b)
    val off = 128f * (1f - con) + bri * 255f
    val v = FloatArray(20)
    for (i in 0..2) {
        val k = con * gains[i]
        v[i * 5 + 0] = rows[i][0] * k
        v[i * 5 + 1] = rows[i][1] * k
        v[i * 5 + 2] = rows[i][2] * k
        v[i * 5 + 4] = off
    }
    v[18] = 1f
    return ColorMatrix(v)
}

internal val FilterCats = listOf(
    "الرائج", "صورة شخصية", "منظر طبيعي", "طعام", "Life", "كلاسيكي", "Limit", "أبيض وأسود"
)

internal val StoryFilters: List<StoryFilter> = listOf(
    // الرائج
    StoryFilter("clean", "نقي", "الرائج", cm(sat = 1.05f, con = 1.05f, bri = 0.03f)),
    StoryFilter("quality", "Quality", "الرائج", cm(sat = 1.12f, con = 1.18f, bri = 0.01f)),
    StoryFilter("sunrise", "شروق الشمس", "الرائج", cm(sat = 1.15f, con = 1.02f, bri = 0.04f, r = 1.14f, g = 1.0f, b = 0.86f)),
    StoryFilter("bookstore", "Bookstore", "الرائج", cm(sat = 0.78f, con = 1.1f, bri = 0.0f, r = 1.08f, g = 1.0f, b = 0.88f)),
    StoryFilter("hawaii", "Hawaii", "الرائج", cm(sat = 1.4f, con = 1.05f, bri = 0.02f, r = 1.04f, g = 1.03f, b = 1.1f)),
    StoryFilter("dreamy", "حالم", "الرائج", cm(sat = 1.1f, con = 0.88f, bri = 0.09f, r = 1.06f, g = 0.98f, b = 1.08f)),
    StoryFilter("cool", "Cool", "الرائج", cm(sat = 1.05f, con = 1.08f, bri = 0.0f, r = 0.9f, g = 1.0f, b = 1.14f)),
    // صورة شخصية
    StoryFilter("glow", "Glow", "صورة شخصية", cm(sat = 1.05f, con = 0.94f, bri = 0.08f, r = 1.05f)),
    StoryFilter("rosy", "Rosy", "صورة شخصية", cm(sat = 1.12f, con = 1.0f, bri = 0.04f, r = 1.1f, g = 0.96f, b = 1.0f)),
    StoryFilter("soft", "ناعم", "صورة شخصية", cm(sat = 0.92f, con = 0.9f, bri = 0.08f)),
    StoryFilter("porcelain", "Porcelain", "صورة شخصية", cm(sat = 0.85f, con = 0.95f, bri = 0.1f, r = 1.02f, g = 1.0f, b = 1.02f)),
    StoryFilter("warmskin", "دافئ", "صورة شخصية", cm(sat = 1.1f, con = 1.04f, bri = 0.03f, r = 1.1f, g = 1.02f, b = 0.9f)),
    StoryFilter("bronze", "Bronze", "صورة شخصية", cm(sat = 1.2f, con = 1.1f, bri = -0.01f, r = 1.12f, g = 1.0f, b = 0.82f)),
    StoryFilter("pure", "Pure", "صورة شخصية", cm(sat = 1.0f, con = 1.08f, bri = 0.05f)),
    StoryFilter("cream", "Cream", "صورة شخصية", cm(sat = 0.88f, con = 0.96f, bri = 0.07f, r = 1.07f, g = 1.03f, b = 0.9f)),
    // منظر طبيعي
    StoryFilter("oslo", "Oslo", "منظر طبيعي", cm(sat = 1.1f, con = 1.1f, bri = 0.0f, r = 0.92f, g = 1.0f, b = 1.1f)),
    StoryFilter("lush", "Lush", "منظر طبيعي", cm(sat = 1.45f, con = 1.08f, bri = 0.0f, r = 0.96f, g = 1.1f, b = 0.94f)),
    StoryFilter("golden", "ساعة ذهبية", "منظر طبيعي", cm(sat = 1.25f, con = 1.05f, bri = 0.03f, r = 1.16f, g = 1.04f, b = 0.8f)),
    StoryFilter("misty", "ضباب", "منظر طبيعي", cm(sat = 0.82f, con = 0.84f, bri = 0.1f, r = 0.98f, g = 1.0f, b = 1.04f)),
    StoryFilter("alpine", "Alpine", "منظر طبيعي", cm(sat = 1.05f, con = 1.14f, bri = 0.02f, r = 0.94f, g = 1.02f, b = 1.12f)),
    StoryFilter("desert", "صحراء", "منظر طبيعي", cm(sat = 1.2f, con = 1.1f, bri = 0.02f, r = 1.12f, g = 1.0f, b = 0.86f)),
    StoryFilter("ocean", "محيط", "منظر طبيعي", cm(sat = 1.3f, con = 1.08f, bri = 0.0f, r = 0.88f, g = 1.05f, b = 1.16f)),
    StoryFilter("autumn", "خريف", "منظر طبيعي", cm(sat = 1.25f, con = 1.06f, bri = 0.0f, r = 1.1f, g = 0.98f, b = 0.84f)),
    // طعام
    StoryFilter("tasty", "Tasty", "طعام", cm(sat = 1.4f, con = 1.12f, bri = 0.02f, r = 1.06f, g = 1.0f, b = 0.94f)),
    StoryFilter("fresh", "Fresh", "طعام", cm(sat = 1.3f, con = 1.05f, bri = 0.05f, r = 0.98f, g = 1.06f, b = 1.0f)),
    StoryFilter("warmbite", "Warm", "طعام", cm(sat = 1.2f, con = 1.08f, bri = 0.03f, r = 1.12f, g = 1.02f, b = 0.86f)),
    StoryFilter("sweet", "Sweet", "طعام", cm(sat = 1.25f, con = 0.98f, bri = 0.07f, r = 1.08f, g = 0.98f, b = 1.04f)),
    StoryFilter("rich", "Rich", "طعام", cm(sat = 1.35f, con = 1.25f, bri = -0.03f, r = 1.05f, g = 1.0f, b = 0.92f)),
    StoryFilter("cafe", "Cafe", "طعام", cm(sat = 0.8f, con = 1.1f, bri = 0.0f, r = 1.1f, g = 1.0f, b = 0.8f)),
    // Life
    StoryFilter("daylight", "Daylight", "Life", cm(sat = 1.15f, con = 1.05f, bri = 0.04f)),
    StoryFilter("pastel", "Pastel", "Life", cm(sat = 0.8f, con = 0.9f, bri = 0.1f, r = 1.04f, g = 1.0f, b = 1.04f)),
    StoryFilter("chill", "Chill", "Life", cm(sat = 0.9f, con = 1.0f, bri = 0.02f, r = 0.95f, g = 1.0f, b = 1.08f)),
    StoryFilter("sunday", "Sunday", "Life", cm(sat = 1.05f, con = 0.95f, bri = 0.06f, r = 1.08f, g = 1.02f, b = 0.9f)),
    StoryFilter("neon", "Neon", "Life", cm(sat = 1.6f, con = 1.22f, bri = -0.03f, r = 1.0f, g = 0.95f, b = 1.15f)),
    StoryFilter("midnight", "Midnight", "Life", cm(sat = 1.1f, con = 1.18f, bri = -0.06f, r = 0.86f, g = 0.94f, b = 1.2f)),
    StoryFilter("moody", "Moody", "Life", cm(sat = 0.85f, con = 1.25f, bri = -0.07f, r = 0.96f, g = 1.0f, b = 1.06f)),
    StoryFilter("city", "City", "Life", cm(sat = 1.2f, con = 1.2f, bri = -0.02f, r = 1.04f, g = 0.96f, b = 1.1f)),
    // كلاسيكي
    StoryFilter("vintage", "Vintage", "كلاسيكي", cm(sat = 0.7f, con = 0.92f, bri = 0.06f, r = 1.1f, g = 1.02f, b = 0.82f)),
    StoryFilter("film", "Film", "كلاسيكي", cm(sat = 0.9f, con = 1.22f, bri = -0.02f, r = 1.04f, g = 1.0f, b = 0.94f)),
    StoryFilter("polaroid", "Polaroid", "كلاسيكي", cm(sat = 0.85f, con = 0.9f, bri = 0.08f, r = 1.08f, g = 1.0f, b = 0.9f)),
    StoryFilter("retro", "Retro", "كلاسيكي", cm(sat = 1.1f, con = 1.1f, bri = 0.0f, r = 1.1f, g = 0.96f, b = 0.85f)),
    StoryFilter("sepia", "Sepia", "كلاسيكي", cm(sat = 0.3f, con = 1.05f, bri = 0.02f, r = 1.22f, g = 1.0f, b = 0.7f)),
    StoryFilter("kodak", "Kodak", "كلاسيكي", cm(sat = 1.2f, con = 1.12f, bri = 0.0f, r = 1.08f, g = 1.0f, b = 0.88f)),
    // Limit (حسب الترتيب بتيك توك)
    StoryFilter("gothic", "Gothic", "Limit", cm(sat = 0.75f, con = 0.95f, bri = 0.07f, r = 0.94f, g = 1.0f, b = 1.08f)),
    StoryFilter("nokia", "Nokia", "Limit", cm(sat = 0.9f, con = 1.05f, bri = 0.0f, r = 0.9f, g = 0.97f, b = 1.18f)),
    StoryFilter("steel", "Steel", "Limit", cm(sat = 0.6f, con = 1.1f, bri = -0.02f, r = 0.88f, g = 0.98f, b = 1.12f)),
    StoryFilter("gotham", "Gotham", "Limit", cm(sat = 0.55f, con = 1.3f, bri = -0.08f, r = 0.9f, g = 0.98f, b = 1.1f)),
    StoryFilter("exposure", "Exposure", "Limit", cm(sat = 1.3f, con = 1.2f, bri = 0.08f, r = 1.05f, g = 1.0f, b = 0.95f)),
    StoryFilter("hawkeye", "Hawkeye", "Limit", cm(sat = 1.15f, con = 1.2f, bri = 0.0f, r = 0.95f, g = 1.02f, b = 1.08f)),
    StoryFilter("ice", "Ice", "Limit", cm(sat = 0.8f, con = 1.05f, bri = 0.08f, r = 0.88f, g = 1.02f, b = 1.15f)),
    StoryFilter("dusk", "Dusk", "Limit", cm(sat = 1.2f, con = 1.15f, bri = -0.04f, r = 1.1f, g = 0.92f, b = 1.1f)),
    // أبيض وأسود
    StoryFilter("bw", "B&W", "أبيض وأسود", cm(sat = 0f, con = 1.05f)),
    StoryFilter("noir", "Noir", "أبيض وأسود", cm(sat = 0f, con = 1.45f, bri = -0.03f)),
    StoryFilter("silver", "Silver", "أبيض وأسود", cm(sat = 0f, con = 0.92f, bri = 0.06f)),
    StoryFilter("fadedbw", "Faded", "أبيض وأسود", cm(sat = 0f, con = 0.8f, bri = 0.1f))
)

/** حبيبات (Grain) فوق الصورة لفلتر Nokia */
internal fun Modifier.storyGrain(filterId: String): Modifier =
    if (filterId != "nokia") this else this.drawWithContent {
        drawContent()
        val rnd = kotlin.random.Random(7)
        val w = size.width
        val h = size.height
        val light = List(1200) { Offset(rnd.nextFloat() * w, rnd.nextFloat() * h) }
        val darkPts = List(1200) { Offset(rnd.nextFloat() * w, rnd.nextFloat() * h) }
        drawPoints(light, PointMode.Points, Color.White.copy(0.22f), 2.5f)
        drawPoints(darkPts, PointMode.Points, Color.Black.copy(0.22f), 2.5f)
    }

internal fun storyFilterById(id: String): StoryFilter? = StoryFilters.firstOrNull { it.id == id }

internal fun storyColorFilter(id: String): ColorFilter? =
    storyFilterById(id)?.let { ColorFilter.colorMatrix(it.matrix) }

// ======================= الموسيقى (iTunes Search API: مقاطع 30 ثانية مجانية بدون مفتاح) =======================

class MusicTrack(val id: String, val title: String, val artist: String, val cover: String, val previewUrl: String)

object MusicApi {
    private fun httpGet(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        return try {
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun parse(json: String): List<MusicTrack> {
        val arr = JSONObject(json).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<MusicTrack>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val preview = o.optString("previewUrl")
            if (preview.isBlank()) continue
            out.add(
                MusicTrack(
                    id = o.optString("trackId", preview),
                    title = o.optString("trackName"),
                    artist = o.optString("artistName"),
                    cover = o.optString("artworkUrl100").replace("100x100", "200x200"),
                    previewUrl = preview
                )
            )
        }
        return out
    }

    suspend fun search(term: String): List<MusicTrack> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(term, "UTF-8")
        parse(httpGet("https://itunes.apple.com/search?term=$q&media=music&entity=song&limit=30"))
    }

    /** الأكثر رواجاً: قائمة آبل الرسمية ثم نجيب مقاطع المعاينة لها، وإذا فشلت نبحث بكلمة عامة */
    suspend fun trending(): List<MusicTrack> = withContext(Dispatchers.IO) {
        try {
            val chart = JSONObject(
                httpGet("https://rss.applemarketingtools.com/api/v2/us/music/most-played/30/songs.json")
            ).getJSONObject("feed").getJSONArray("results")
            val ids = (0 until chart.length()).joinToString(",") { chart.getJSONObject(it).getString("id") }
            val tracks = parse(httpGet("https://itunes.apple.com/lookup?id=$ids&entity=song"))
            if (tracks.isEmpty()) search("top hits") else tracks
        } catch (e: Exception) {
            search("top hits")
        }
    }
}

/**
 * مشغّل مقاطع القصة: يشغّل من ثانية معينة ويوقف لحاله بعد [STORY_SECONDS].
 * المقطع ينزّل أول مرة لملف مؤقت بالكاش (حتى القفز لثانية البداية يصير فوري بدل ما ينتظر الشبكة)،
 * و[ready] تصير true بس يبدأ الصوت فعلاً (الواجهة تنتظرها حتى ما تسبق الأغنية القصة).
 */
class StoryPlayer(private val cacheDir: java.io.File? = null) {
    private var mp: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var stopper: Runnable? = null
    private var gen = 0
    var playingUrl by mutableStateOf<String?>(null)
        private set
    var ready by mutableStateOf(false)
        private set

    private fun cacheFile(url: String): java.io.File? =
        cacheDir?.let { java.io.File(it, "story_music_${url.hashCode().toUInt()}.m4a") }

    fun play(url: String, startSec: Int = 0, maxSec: Int = STORY_SECONDS) {
        stop()
        val myGen = ++gen
        playingUrl = url
        ready = false
        Thread {
            // 1) نحمّل المقطع كامل للكاش (30 ثانية فقط، حجمه صغير)
            var source: String = url
            val f = cacheFile(url)
            if (f != null) {
                runCatching {
                    if (!f.exists() || f.length() < 1024) {
                        val tmp = java.io.File(f.path + ".tmp")
                        URL(url).openStream().use { inp -> tmp.outputStream().use { out -> inp.copyTo(out) } }
                        tmp.renameTo(f)
                    }
                    source = f.path
                }
            }
            handler.post {
                if (myGen != gen) return@post   // تغيّر الطلب أو انوقف
                try {
                    val p = MediaPlayer()
                    p.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA).build()
                    )
                    p.setDataSource(source)
                    p.setOnPreparedListener { it.seekTo(startSec * 1000) }
                    p.setOnSeekCompleteListener {
                        if (myGen != gen) return@setOnSeekCompleteListener
                        it.start()
                        ready = true
                        // مؤقت الإيقاف يبدأ من لحظة بدء الصوت الفعلية
                        val r = Runnable { stop() }
                        stopper = r
                        handler.postDelayed(r, maxSec * 1000L + 1500L)
                    }
                    p.setOnCompletionListener { stop() }
                    p.setOnErrorListener { _, _, _ -> stop(); true }
                    p.prepareAsync()
                    mp = p
                } catch (e: Exception) {
                    stop()
                }
            }
        }.start()
    }

    private var pausedByUs = false

    fun pause() {
        runCatching {
            mp?.takeIf { it.isPlaying }?.let { it.pause(); pausedByUs = true }
        }
    }

    fun resume() {
        if (!pausedByUs) return
        pausedByUs = false
        runCatching { mp?.start() }
    }

    fun stop() {
        gen++
        stopper?.let { handler.removeCallbacks(it) }
        stopper = null
        pausedByUs = false
        runCatching { mp?.release() }
        mp = null
        playingUrl = null
        ready = false
    }
}

@Composable
fun rememberStoryPlayer(): StoryPlayer {
    val cache = LocalContext.current.cacheDir
    val p = remember { StoryPlayer(cache) }
    DisposableEffect(Unit) { onDispose { p.stop() } }
    return p
}

/** تحميل صورة من رابط (غلاف الأغنية) بدون مكتبات خارجية */
@Composable
fun rememberRemoteImage(url: String): ImageBitmap? {
    var img by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        if (url.isNotBlank()) {
            img = withContext(Dispatchers.IO) {
                runCatching {
                    URL(url).openStream().use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    return img
}

/** شريحة الأغنية على القصة: غلاف + العنوان والفنان */
@Composable
internal fun MusicPill(title: String, artist: String, cover: String, modifier: Modifier = Modifier) {
    val img = rememberRemoteImage(cover)
    Row(
        modifier.clip(CircleShape).background(Color.Black.copy(0.45f))
            .border(0.8.dp, Color.White.copy(0.25f), CircleShape)
            .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF2B2F36)), contentAlignment = Alignment.Center) {
            if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text("♫", color = Color.White, fontSize = 16.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.widthIn(max = 200.dp)) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (artist.isNotBlank()) Text(artist, color = Color.White.copy(0.7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ======================= محرر القصة =======================

@Composable
private fun StoryTabPill(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val fg = MaterialTheme.colorScheme.onBackground
    Box(
        modifier.clip(CircleShape)
            .background(if (selected) AccentBlue.copy(0.50f) else Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) Color.White else fg.copy(0.75f)
        )
    }
}

private val FilterSampleBrush = Brush.linearGradient(
    listOf(Color(0xFF4FA3FF), Color(0xFFFFC27A), Color(0xFFE8765A), Color(0xFF3E8E41))
)

/** دائرة فلتر مع اسمه (الصورة مطبّق عليها الفلتر، أو عينة ألوان إذا ما فيه صورة) */
@Composable
private fun FilterThumb(name: String, filter: StoryFilter?, image: ImageBitmap?, selected: Boolean, onClick: () -> Unit) {
    val cf = filter?.let { ColorFilter.colorMatrix(it.matrix) }
    Column(
        Modifier.width(76.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = CenterHorizontally
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape)
                .border(if (selected) BorderStroke(3.dp, Color.White) else BorderStroke(1.dp, Color.White.copy(0.25f)), CircleShape)
        ) {
            if (image != null) {
                Image(
                    image, null, Modifier.fillMaxSize().storyGrain(filter?.id ?: ""),
                    contentScale = ContentScale.Crop, colorFilter = cf
                )
            } else {
                Box(Modifier.fillMaxSize().drawBehind { drawRect(FilterSampleBrush, colorFilter = cf) })
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(if (selected) 1f else 0.8f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
    }
}

/**
 * شاشة نشر القصة (مثل تيك توك): القصة = صورة فقط (بدون نص) + فلاتر + صوت أغنية (15 ثانية كحد أقصى).
 * النص صار "حالة" لحاله فوق صورة الملف الشخصي (StatusBubble).
 * الأغنية صوت فقط: ما يطلع غلافها ولا اسمها على الصورة.
 */
@Composable
internal fun StoryEditorScreen(existing: Story?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dark = isSystemInDarkTheme()
    val fg = MaterialTheme.colorScheme.onBackground
    val player = rememberStoryPlayer()

    var imageB64 by remember { mutableStateOf(existing?.image ?: "") }
    var filterId by remember { mutableStateOf(existing?.filter ?: "") }
    var musicUrl by remember { mutableStateOf(existing?.musicUrl ?: "") }
    var musicTitle by remember { mutableStateOf(existing?.musicTitle ?: "") }
    var musicArtist by remember { mutableStateOf(existing?.musicArtist ?: "") }
    var startF by remember { mutableStateOf((existing?.musicStart ?: 0).toFloat()) }
    var tab by remember { mutableStateOf(if (existing == null) 0 else 1) }
    var filterCat by remember { mutableStateOf(storyFilterById(existing?.filter ?: "")?.cat ?: FilterCats[0]) }
    var busy by remember { mutableStateOf(false) }

    // الموسيقى: بحث ونتائج
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    fun loadMusic(term: String?) {
        scope.launch {
            loading = true
            failed = false
            try {
                results = if (term.isNullOrBlank()) MusicApi.trending() else MusicApi.search(term)
            } catch (e: Exception) {
                failed = true
                results = emptyList()
            }
            loading = false
        }
    }
    LaunchedEffect(tab) {
        if (tab == 2 && results.isEmpty() && !loading) loadMusic(null)
    }

    val pickImage = rememberImagePicker(maxSide = 1200, quality = 70) { imageB64 = it }
    val image = rememberB64Image(imageB64)
    val canPublish = !busy && imageB64.isNotEmpty()

    // القصة الجديدة: معرض الصور يفتح تلقائياً بعد ما تخلص حركة الفتح
    LaunchedEffect(Unit) {
        if (existing == null) {
            delay(450)
            pickImage()
        }
    }

    BackHandler(onBack = { player.stop(); onDismiss() })
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        LiquidScene(banner = null) { bd ->
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                    .padding(horizontal = 16.dp)
            ) {
                // الشريط العلوي: إغلاق + عنوان + نشر
                StaggerIn(0, Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        GlassButton(onClick = { player.stop(); onDismiss() }, backdrop = bd, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.Close, null, modifier = Modifier.size(24.dp))
                        }
                        Text(
                            if (existing == null) "إضافة قصة" else "تعديل القصة",
                            fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f).padding(horizontal = 14.dp)
                        )
                        GlassButton(
                            backdrop = bd, tint = Color(0xFF1AA7FF),
                            modifier = Modifier.height(48.dp),
                            enabled = canPublish,
                            onClick = {
                                busy = true
                                scope.launch {
                                    try {
                                        // كل نشر (جديد أو تعديل) يبدأ مدة 24 ساعة من الآن
                                        Repo.saveStory(
                                            Story(
                                                text = "", color = -1, image = imageB64,
                                                createdAt = System.currentTimeMillis(), bg = 0,
                                                filter = filterId,
                                                musicUrl = musicUrl, musicTitle = musicTitle, musicArtist = musicArtist,
                                                musicCover = "", musicStart = startF.toInt(),
                                                isPublic = existing?.isPublic ?: false
                                            )
                                        )
                                        player.stop()
                                        onDismiss()
                                    } catch (e: Exception) {
                                        busy = false
                                        AppToast.show("فشل النشر: ${e.message}", long = true)
                                    }
                                }
                            }
                        ) {
                            Text(
                                if (busy) "..." else if (existing == null) "نشر" else "نشر من جديد",
                                color = Color.White, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 26.dp)
                            )
                        }
                    }
                }

                // بطاقة القصة (9:16): الصورة بفلترها
                StaggerIn(1, Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val cardShape = RoundedCornerShape(34.dp)
                        Box(
                            Modifier.fillMaxHeight().aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                                .clip(cardShape)
                                .background(if (image != null) Brush.verticalGradient(listOf(Color.Black, Color.Black)) else storyBgBrush(0))
                                .border(
                                    0.9.dp,
                                    Brush.verticalGradient(listOf(Color.White.copy(0.55f), Color.White.copy(0.08f))),
                                    cardShape
                                )
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    if (image == null) pickImage()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (image != null) {
                                Image(
                                    image, null, Modifier.fillMaxSize().storyGrain(filterId),
                                    contentScale = ContentScale.Fit,
                                    colorFilter = storyColorFilter(filterId)
                                )
                            } else {
                                Column(horizontalAlignment = CenterHorizontally) {
                                    Icon(Icons.Filled.Add, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(44.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("اضغط لاختيار صورة القصة", color = Color.White.copy(0.7f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                // لوحة الأدوات (زجاج سائل) بثلاث تبويبات
                StaggerIn(2, Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Column(
                        Modifier.fillMaxWidth().animateContentSize()
                            .liquidCard(bd, RoundedCornerShape(28.dp), dark).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("صورة", "فلاتر", "موسيقى").forEachIndexed { i, label ->
                                StoryTabPill(label, tab == i, Modifier.weight(1f)) { tab = i }
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(if (tab == 2) 250.dp else 150.dp)) {
                            when (tab) {
                                0 -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                                    Text(
                                        "القصة صورة فقط. النص تنشره كـ\"حالة\" فوق صورتك الشخصية.",
                                        fontSize = 13.sp, color = fg.copy(0.7f)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    GlassButton(onClick = pickImage, backdrop = bd, modifier = Modifier.height(46.dp)) {
                                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(20.dp))
                                        Text(
                                            if (imageB64.isEmpty()) "اختيار صورة" else "تغيير الصورة",
                                            modifier = Modifier.padding(start = 6.dp, end = 16.dp)
                                        )
                                    }
                                }

                                1 -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    // تصنيفات الفلاتر: نصوص مع خط تحت المختار (مثل تيك توك)
                                    Row(
                                        Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                                    ) {
                                        FilterCats.forEach { c ->
                                            val sel = c == filterCat
                                            Column(
                                                horizontalAlignment = CenterHorizontally,
                                                modifier = Modifier.clickable(
                                                    interactionSource = remember { MutableInteractionSource() }, indication = null
                                                ) { filterCat = c }
                                            ) {
                                                Text(
                                                    c, fontSize = 15.sp,
                                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (sel) fg else fg.copy(0.55f)
                                                )
                                                Box(
                                                    Modifier.padding(top = 4.dp).height(2.dp).width(if (sel) 28.dp else 0.dp)
                                                        .background(fg)
                                                )
                                            }
                                        }
                                    }
                                    if (image == null) {
                                        Text("الفلاتر تنطبق على الصورة، اختار صورة أول.", fontSize = 12.sp, color = fg.copy(0.6f))
                                    }
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        item {
                                            FilterThumb("بدون", null, image, filterId.isEmpty()) { filterId = "" }
                                        }
                                        items(StoryFilters.filter { it.cat == filterCat }, key = { it.id }) { f ->
                                            FilterThumb(f.name, f, image, filterId == f.id) { filterId = f.id }
                                        }
                                    }
                                }

                                else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (musicUrl.isNotEmpty()) {
                                        // أغنية مختارة (صوت فقط): نختار منين تبدأ (القصة 15 ثانية فقط)
                                        Text(
                                            "♫  $musicTitle" + if (musicArtist.isNotBlank()) " - $musicArtist" else "",
                                            color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth().clip(CircleShape)
                                                .background(fg.copy(0.08f)).padding(horizontal = 14.dp, vertical = 10.dp)
                                        )
                                        Text(
                                            "تبدأ من الثانية ${startF.toInt()} من المقطع (تشتغل ${STORY_SECONDS} ثانية)",
                                            fontSize = 13.sp, color = fg.copy(0.75f)
                                        )
                                        Slider(
                                            value = startF,
                                            onValueChange = { startF = it },
                                            onValueChangeFinished = { player.play(musicUrl, startF.toInt()) },
                                            valueRange = 0f..15f, steps = 14,
                                            colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue)
                                        )
                                        GlassButton(
                                            onClick = {
                                                player.stop()
                                                musicUrl = ""; musicTitle = ""; musicArtist = ""; startF = 0f
                                            },
                                            backdrop = bd, modifier = Modifier.height(42.dp)
                                        ) { Text("إزالة الأغنية", modifier = Modifier.padding(horizontal = 18.dp)) }
                                    } else {
                                        Row(
                                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                                .background(fg.copy(0.08f)).padding(horizontal = 12.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Filled.Search, null, tint = fg.copy(0.6f), modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Box(Modifier.weight(1f)) {
                                                if (query.isEmpty()) {
                                                    Text("ابحث عن أغنية أو فنان", color = fg.copy(0.5f), fontSize = 15.sp)
                                                }
                                                BasicTextField(
                                                    value = query, onValueChange = { query = it }, singleLine = true,
                                                    textStyle = TextStyle(color = fg, fontSize = 15.sp),
                                                    cursorBrush = SolidColor(AccentBlue),
                                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                                    keyboardActions = KeyboardActions(onSearch = { loadMusic(query.trim()) }),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                        Row(
                                            Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(
                                                "الأكثر رواجاً" to "", "عربي" to "arabic hits", "عراقي" to "iraqi",
                                                "Pop" to "pop hits", "Hip Hop" to "hip hop", "Rock" to "rock", "Chill" to "chill"
                                            ).forEach { (label, term) ->
                                                Text(
                                                    label, fontSize = 12.sp, color = fg.copy(0.85f),
                                                    modifier = Modifier.clip(CircleShape).background(fg.copy(0.08f))
                                                        .clickable { query = ""; loadMusic(term) }
                                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                                )
                                            }
                                        }
                                        Box(Modifier.weight(1f).fillMaxWidth()) {
                                            when {
                                                loading -> Text("جاري التحميل...", color = fg.copy(0.6f), fontSize = 13.sp, modifier = Modifier.align(Alignment.Center))
                                                failed -> Text("تعذّر تحميل الأغاني، تأكد من الإنترنت وجرّب مرة ثانية.", color = fg.copy(0.6f), fontSize = 13.sp, modifier = Modifier.align(Alignment.Center))
                                                results.isEmpty() -> Text("ما لقيت نتائج", color = fg.copy(0.6f), fontSize = 13.sp, modifier = Modifier.align(Alignment.Center))
                                                else -> LazyColumn(Modifier.fillMaxSize()) {
                                                    items(results) { t ->
                                                        val cover = rememberRemoteImage(t.cover)
                                                        val playing = player.playingUrl == t.previewUrl
                                                        Row(
                                                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                                                .clickable { if (playing) player.stop() else player.play(t.previewUrl, 0) }
                                                                .padding(vertical = 5.dp, horizontal = 4.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            // الغلاف يبين بقائمة الاختيار فقط (مو على القصة)
                                                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2B2F36)), contentAlignment = Alignment.Center) {
                                                                if (cover != null) Image(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                                                else Text("♫", color = Color.White, fontSize = 16.sp)
                                                            }
                                                            Spacer(Modifier.width(10.dp))
                                                            Column(Modifier.weight(1f)) {
                                                                Text(t.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = fg)
                                                                Text(t.artist, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = fg.copy(0.6f))
                                                            }
                                                            Text(if (playing) "❚❚" else "▶", color = fg.copy(0.8f), fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
                                                            Text(
                                                                "اختيار", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                                                modifier = Modifier.clip(CircleShape).background(AccentBlue.copy(0.9f))
                                                                    .clickable {
                                                                        musicUrl = t.previewUrl; musicTitle = t.title
                                                                        musicArtist = t.artist; startF = 0f
                                                                        player.play(t.previewUrl, 0)
                                                                    }
                                                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ======================= عارض القصة =======================

/** عنصر بقائمة الثلاث نقاط */
@Composable
private fun StoryMenuItem(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** شريط تقدم القصة (15 ثانية). القراءة هنا فقط حتى ما تنعاد تركيب الشاشة كاملة كل إطار */
@Composable
private fun StoryProgressBar(prog: Animatable<Float, *>) {
    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(0.28f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(prog.value.coerceIn(0f, 1f)).clip(CircleShape).background(Color.White))
    }
}

/**
 * عرض القصة بملء الشاشة (15 ثانية): خلفية/صورة بفلترها + النص + الأغنية.
 * [showMenu] = زر الثلاث نقاط (تعديل ونشر من جديد / حذف) لصاحب القصة فقط.
 * بنهاية الـ15 ثانية أو بالضغط على الشاشة ينادي [onFinished] (القصة التالية أو الإغلاق).
 */
@Composable
fun StoryViewerScreen(
    story: Story,
    owner: Profile?,
    mine: Boolean,
    showMenu: Boolean,
    onDismiss: () -> Unit,
    onFinished: () -> Unit = onDismiss,
    onEdit: () -> Unit = {},
    /** معرّف صاحب القصة: إذا موجود (وما هي قصتي) يطلع شريط "الرد على القصة" */
    ownerUid: String = "",
    /** ضغطة على صورة/اسم صاحب القصة تفتح ملفه الشخصي */
    onOpenProfile: (() -> Unit)? = null,
    /** ضغطة على مشاهد بقائمة المشاهدين (لصاحب القصة) تفتح ملفه الشخصي */
    onOpenViewer: ((com.chatapp.messenger.UserItem) -> Unit)? = null
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val image = rememberB64Image(story.image)
    val avatar = rememberB64Image(owner?.avatar ?: "")
    val bd = rememberLayerBackdrop()
    val player = rememberStoryPlayer()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val prog = remember { Animatable(0f) }
    val finishedCb by rememberUpdatedState(onFinished)
    val focusManager = LocalFocusManager.current
    var replyText by remember { mutableStateOf("") }
    var replyFocused by remember { mutableStateOf(false) }
    val canReply = !mine && ownerUid.isNotBlank()
    val myUid = remember { Repo.auth.currentUser?.uid ?: "" }
    val realOwner = if (mine) myUid else ownerUid
    // ضغطة مطولة على القصة = تتوقف لين ترفع إصبعك
    var holding by remember { mutableStateOf(false) }
    var viewersOpen by remember { mutableStateOf(false) }
    var shareOpen by remember { mutableStateOf(false) }
    // القصة تنتظر الأغنية تجهز (بحد أقصى 6 ثواني) حتى تبدأ الأغنية والقصة سوا من البداية
    var musicTimeout by remember(story.createdAt) { mutableStateOf(false) }
    val musicLoading = story.musicUrl.isNotBlank() && !player.ready && !musicTimeout
    val paused = menuOpen || confirmDelete || replyFocused || holding || viewersOpen || shareOpen || musicLoading
    // الإعجاب: أي صديق يگدر يعجب بقصة صاحبه. العدد والمشاهدين يظهرون لصاحب القصة فقط
    val liked by remember(realOwner, story.createdAt, mine) {
        if (!mine && realOwner.isNotBlank()) Repo.myStoryLikeFlow(realOwner, story.createdAt)
        else kotlinx.coroutines.flow.flowOf(false)
    }.collectAsState(initial = false)
    val views by remember(story.createdAt, mine) {
        if (mine) Repo.myStoryViewsFlow(story.createdAt) else kotlinx.coroutines.flow.flowOf(emptyList<com.chatapp.messenger.StoryView>())
    }.collectAsState(initial = emptyList<com.chatapp.messenger.StoryView>())
    val likesCount = views.count { it.liked }
    // تسجيل المشاهدة (مرة وحدة لكل قصة)
    LaunchedEffect(realOwner, story.createdAt) {
        if (!mine && realOwner.isNotBlank()) runCatching { Repo.markStoryViewed(realOwner, story.createdAt) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }
    LaunchedEffect(story.musicUrl) {
        if (story.musicUrl.isNotBlank()) {
            player.play(story.musicUrl, story.musicStart)
            delay(6_000)
            musicTimeout = true
        }
    }
    // التقدم يوقف لما تنفتح القائمة أو التأكيد، ويكمل بعدها
    LaunchedEffect(paused) {
        if (paused) {
            player.pause()
        } else {
            player.resume()
            val leftMs = ((1f - prog.value) * STORY_SECONDS * 1000).toInt().coerceAtLeast(1)
            prog.animateTo(1f, tween(leftMs, easing = LinearEasing))
            finishedCb()
        }
    }

    val subtitle = if (mine) {
        val remainMin = ((story.expiresAt - now).coerceAtLeast(0L)) / 60_000
        "تنتهي بعد ${remainMin / 60} س و ${remainMin % 60} د"
    } else {
        val agoMin = ((now - story.createdAt).coerceAtLeast(0L)) / 60_000
        if (agoMin < 1) "الآن" else if (agoMin < 60) "قبل $agoMin د" else "قبل ${agoMin / 60} س"
    }
    val ownerName = if (mine) "قصتي" else (owner?.name?.ifBlank { "مستخدم" } ?: "مستخدم")

    BackHandler {
        when {
            confirmDelete -> if (!deleting) confirmDelete = false
            viewersOpen -> viewersOpen = false
            shareOpen -> shareOpen = false
            menuOpen -> menuOpen = false
            replyFocused -> focusManager.clearFocus()
            else -> onDismiss()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { } }) {
        // محتوى القصة (هذا اللي تتموّه خلفه الأزرار الزجاجية)
        Box(
            Modifier.fillMaxSize().layerBackdrop(bd)
                .background(if (image != null) Brush.verticalGradient(listOf(Color.Black, Color.Black)) else storyBgBrush(story.bg))
                .pointerInput(Unit) {
                    var heldLong = false
                    detectTapGestures(
                        onPress = {
                            heldLong = false
                            // بعد ~0.18 ثانية من الضغط تتوقف القصة (والموسيقى) لين ترفع إصبعك
                            val job = scope.launch {
                                delay(180)
                                heldLong = true
                                holding = true
                            }
                            tryAwaitRelease()
                            job.cancel()
                            holding = false
                        },
                        onTap = {
                            if (!heldLong) {
                                if (menuOpen) menuOpen = false
                                else if (replyFocused) focusManager.clearFocus()
                                else finishedCb()
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (image != null) {
                // الصورة بحجمها الأصلي (بدون تكبير/قص): تظهر كاملة بنسبتها الحقيقية
                Image(
                    image, null, Modifier.fillMaxSize().storyGrain(story.filter),
                    contentScale = ContentScale.Fit,
                    colorFilter = storyColorFilter(story.filter)
                )
            }
            if (story.text.isNotBlank()) {
                Text(
                    story.text, color = Color(story.color), fontSize = storyFontSize(story.text.length),
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(28.dp),
                    style = TextStyle(shadow = StoryTextShadow)
                )
            }
        }
        // ظل خفيف أعلى الشاشة حتى تبين الأزرار
        Box(
            Modifier.fillMaxWidth().height(170.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(0.45f), Color.Transparent)))
        )
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            StoryProgressBar(prog)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassButton(onClick = onDismiss, backdrop = bd, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(10.dp))
                Row(
                    Modifier.weight(1f).then(
                        if (onOpenProfile != null && !mine) Modifier.clip(RoundedCornerShape(24.dp)).clickable {
                            player.stop()
                            onOpenProfile()
                        } else Modifier
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF2B2F36)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatar != null) Image(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Icon(Icons.Filled.Person, null, tint = Color(0xFF8A8F98), modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            ownerName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = TextStyle(shadow = StoryTextShadow)
                        )
                        Text(subtitle, color = Color.White.copy(0.85f), fontSize = 12.sp, style = TextStyle(shadow = StoryTextShadow))
                    }
                }
                if (showMenu) {
                    GlassButton(onClick = { menuOpen = !menuOpen }, backdrop = bd, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.MoreVert, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
            }
        }

        // الرد على القصة: يوصل كرسالة بالمحادثة مع صاحب القصة
        if (canReply) {
            val barShape = RoundedCornerShape(30.dp)
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .imePadding().navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.weight(1f).heightIn(min = 52.dp)
                        .liquidCard(bd, barShape, dark = true)
                        .background(Color.Black.copy(0.22f), barShape)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (replyText.isEmpty()) {
                        Text("رد على القصة...", color = Color.White.copy(0.65f), fontSize = 16.sp)
                    }
                    BasicTextField(
                        value = replyText,
                        onValueChange = { replyText = it.take(500) },
                        maxLines = 4,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(Color(0xFF1AA7FF)),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { replyFocused = it.isFocused }
                    )
                }
                if (replyText.isNotBlank()) {
                    Spacer(Modifier.width(10.dp))
                    GlassButton(
                        onClick = {
                            Repo.sendStoryReply(ownerUid, replyText.trim())
                            replyText = ""
                            focusManager.clearFocus()
                            AppToast.show("انرسل ردك")
                        },
                        backdrop = bd, tint = Color(0xFF1AA7FF),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(AppIcons.Reply, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                } else if (!replyFocused) {
                    // إعجاب + مشاركة
                    val heartScale by animateFloatAsState(if (liked) 1.18f else 1f, spring(0.35f, 500f), label = "heart")
                    Spacer(Modifier.width(10.dp))
                    GlassButton(
                        onClick = {
                            val now = !liked
                            scope.launch { runCatching { Repo.setStoryLike(ownerUid, story.createdAt, now) } }
                        },
                        backdrop = bd, modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null,
                            tint = if (liked) Color(0xFFFF3B5C) else Color.White,
                            modifier = Modifier.size(26.dp).graphicsLayer { scaleX = heartScale; scaleY = heartScale }
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    GlassButton(onClick = { shareOpen = true }, backdrop = bd, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Filled.Share, null, tint = Color.White, modifier = Modifier.size(23.dp))
                    }
                }
            }
        }

        // قصتي: عدد الإعجابات + المشاهدين (لصاحب القصة فقط) + مشاركة
        if (mine) {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ضغطة قصيرة على زر الإعجاب = قائمة من شاف القصة ومن أعجبه
                GlassButton(onClick = { viewersOpen = true }, backdrop = bd, modifier = Modifier.height(52.dp)) {
                    Icon(
                        Icons.Filled.Favorite, null, tint = Color(0xFFFF3B5C),
                        modifier = Modifier.padding(start = 18.dp).size(24.dp)
                    )
                    Text(
                        "$likesCount", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    Text(
                        "· ${views.size} مشاهدة", color = Color.White.copy(0.8f), fontSize = 13.sp,
                        modifier = Modifier.padding(start = 8.dp, end = 20.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                GlassButton(onClick = { shareOpen = true }, backdrop = bd, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Filled.Share, null, tint = Color.White, modifier = Modifier.size(23.dp))
                }
            }
        }

        // قائمة المشاهدين (صاحب القصة فقط)
        StorySheet(visible = viewersOpen && mine, backdrop = bd, title = "المشاهدون (${views.size})", onDismiss = { viewersOpen = false }) {
            if (views.isEmpty()) {
                Text(
                    "ما أحد شاف قصتك لحد الآن", color = Color.White.copy(0.7f), fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(views, key = { it.uid }) { v ->
                        val prof by remember(v.uid) { Repo.basicProfileFlow(v.uid) }.collectAsState(initial = Profile())
                        val av = rememberB64Image(prof.avatar)
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable(enabled = onOpenViewer != null) {
                                    // ضغطة على المشاهد = يفتح ملفه الشخصي
                                    player.stop()
                                    viewersOpen = false
                                    onOpenViewer?.invoke(com.chatapp.messenger.UserItem(v.uid, prof.name, "", prof.username))
                                }
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF2B2F36)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (av != null) Image(av, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                else Icon(Icons.Filled.Person, null, tint = Color(0xFF8A8F98), modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                prof.name.ifBlank { "مستخدم" }, color = Color.White, fontSize = 16.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (v.liked) {
                                Icon(Icons.Filled.Favorite, null, tint = Color(0xFFFF3B5C), modifier = Modifier.size(22.dp))
                            } else {
                                Text("شاهدها", color = Color.White.copy(0.55f), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // مشاركة القصة لأصدقائي
        StorySheet(visible = shareOpen, backdrop = bd, title = "شارك القصة مع أصدقائك", onDismiss = { shareOpen = false }) {
            val friendIds by remember(myUid) { Repo.friendsFlow(myUid) }.collectAsState(initial = emptyList())
            val picked = remember { mutableStateListOf<String>() }
            if (friendIds.isEmpty()) {
                Text(
                    "ما عندك أصدقاء بعد. ضيف أصدقاء من ملفاتهم الشخصية حتى تگدر تشارك القصة.",
                    color = Color.White.copy(0.7f), fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(friendIds, key = { it }) { fid ->
                        val prof by remember(fid) { Repo.basicProfileFlow(fid) }.collectAsState(initial = Profile())
                        val av = rememberB64Image(prof.avatar)
                        val on = fid in picked
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { if (on) picked.remove(fid) else picked.add(fid) }
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF2B2F36)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (av != null) Image(av, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                else Icon(Icons.Filled.Person, null, tint = Color(0xFF8A8F98), modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                prof.name.ifBlank { "مستخدم" }, color = Color.White, fontSize = 16.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                Modifier.size(26.dp).clip(CircleShape)
                                    .background(if (on) Color(0xFF1AA7FF) else Color.White.copy(0.14f))
                                    .border(1.dp, Color.White.copy(if (on) 0f else 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (on) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                GlassButton(
                    onClick = {
                        Repo.shareStory(picked.toList(), realOwner, if (mine) "" else (owner?.name ?: ""))
                        AppToast.show("انرسلت القصة")
                        picked.clear()
                        shareOpen = false
                    },
                    backdrop = bd, tint = Color(0xFF1AA7FF), enabled = picked.isNotEmpty(),
                    modifier = Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(50.dp)
                ) {
                    Text(
                        if (picked.isEmpty()) "اختر أصدقاء" else "إرسال (${picked.size})",
                        color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // الأغنية صوت فقط: ما يطلع غلافها ولا اسمها على القصة

        // قائمة الثلاث نقاط: تنفتح من الزر بحركة نابضية
        val menuOrigin = TransformOrigin(if (rtl) 0f else 1f, 0f)
        val menuShape = RoundedCornerShape(26.dp)
        AnimatedVisibility(
            visible = menuOpen && showMenu,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 78.dp, start = 16.dp, end = 16.dp),
            enter = scaleIn(spring(0.78f, 420f), initialScale = 0.3f, transformOrigin = menuOrigin) + fadeIn(tween(150)),
            exit = scaleOut(tween(160), targetScale = 0.3f, transformOrigin = menuOrigin) + fadeOut(tween(120))
        ) {
            Column(
                Modifier.width(270.dp)
                    .liquidCard(bd, menuShape, dark = true)
                    .background(Color.Black.copy(0.22f), menuShape)
                    .padding(8.dp)
            ) {
                StoryMenuItem(Icons.Filled.Edit, "تعديل القصة ونشرها من جديد", Color.White) {
                    menuOpen = false
                    player.stop()
                    onEdit()
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(0.6.dp).background(Color.White.copy(0.18f)))
                StoryMenuItem(
                    if (story.isPublic) AppIcons.Public else AppIcons.Lock,
                    if (story.isPublic) "القصة: عامة (اضغط لجعلها خاصة)" else "القصة: خاصة (اضغط لجعلها عامة)",
                    Color.White
                ) {
                    menuOpen = false
                    val target = !story.isPublic
                    scope.launch {
                        try {
                            Repo.setStoryPublic(target)
                            AppToast.show(if (target) "القصة صارت عامة، تطلع لكل المستخدمين" else "القصة صارت خاصة، تطلع للأصدقاء فقط")
                        } catch (e: Exception) {
                            AppToast.show("فشل التغيير: ${e.message}", long = true)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(0.6.dp).background(Color.White.copy(0.18f)))
                StoryMenuItem(Icons.Filled.Delete, "حذف القصة", Color(0xFFFF6B61)) {
                    menuOpen = false
                    confirmDelete = true
                }
            }
        }

        if (confirmDelete) {
            GlassConfirmDialog(
                backdrop = bd,
                dark = true,
                title = "حذف القصة؟",
                message = "راح تنحذف قصتك نهائياً وما تگدر ترجعها.",
                confirmText = if (deleting) "..." else "حذف",
                icon = Icons.Filled.Delete,
                onConfirm = {
                    if (!deleting) {
                        deleting = true
                        scope.launch {
                            try {
                                Repo.deleteStory()
                                player.stop()
                                onDismiss()
                            } catch (e: Exception) {
                                deleting = false
                                confirmDelete = false
                                AppToast.show("فشل الحذف: ${e.message}", long = true)
                            }
                        }
                    }
                },
                onDismiss = { if (!deleting) confirmDelete = false }
            )
        }
    }
}


/** لوحة زجاجية تطلع من تحت (للمشاهدين / المشاركة). الضغط خارجها يسكّرها */
@Composable
private fun StorySheet(
    visible: Boolean,
    backdrop: Backdrop,
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.slideInVertically(spring(0.85f, 400f)) { it } + fadeIn(tween(160)),
        exit = androidx.compose.animation.slideOutVertically(tween(200)) { it } + fadeOut(tween(140))
    ) {
        val shape = RoundedCornerShape(30.dp)
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(0.40f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(10.dp)
                    .liquidCard(backdrop, shape, dark = true)
                    .background(Color.Black.copy(0.34f), shape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .padding(vertical = 18.dp)
            ) {
                Text(
                    title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)
                )
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

// ======================= قائمة قصص الكل (Home) =======================

/** صف دوائر القصص أعلى Home: قصتي أولاً ثم قصص الباقين (الأحدث أولاً) */
@Composable
fun StoriesRow(stories: List<ActiveStory>, myUid: String, onOpen: (Int) -> Unit) {
    if (stories.isEmpty()) return
    val fg = MaterialTheme.colorScheme.onBackground
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        itemsIndexed(stories, key = { _, a -> a.uid }) { i, a ->
            val avatar = rememberB64Image(a.profile.avatar)
            val mine = a.uid == myUid
            Column(
                Modifier.width(72.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onOpen(i) },
                horizontalAlignment = CenterHorizontally
            ) {
                Box(
                    Modifier.size(68.dp).border(3.dp, StoryBlue, CircleShape).padding(5.dp)
                        .clip(CircleShape).background(Color(0xFF2B2F36)),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatar != null) Image(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(Icons.Filled.Person, null, tint = Color(0xFF8A8F98), modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (mine) "قصتي" else a.profile.name.ifBlank { "مستخدم" },
                    fontSize = 12.sp, color = fg.copy(0.85f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ======================= الحالة النصية (فقاعة فوق صورة الملف الشخصي) =======================

/** فقاعة الحالة فوق الصورة (مثل تيك توك). [text] = null تعني "أضف حالة" لصاحب الملف */
@Composable
fun StatusBubble(text: String?, backdrop: Backdrop, u: Dp, onClick: (() -> Unit)?) {
    // زر "أضف حالة": كبسولة دائرية الحواف بزجاج سائل (نفس أزرار التطبيق)
    if (text == null && onClick != null) {
        GlassButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = Modifier.height(u * 120f)
        ) {
            Text(
                "+ أضف حالة", color = Color.White, fontSize = (u.value * 46f).sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = u * 52f)
            )
        }
        return
    }
    val shape = RoundedCornerShape(u * 46f)
    Column(horizontalAlignment = Alignment.Start) {
        Box(
            Modifier.widthIn(max = u * 620f)
                .liquidCard(backdrop, shape, dark = true)
                .background(Color.Black.copy(0.25f), shape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = u * 44f, vertical = u * 28f)
        ) {
            if (text != null) {
                Text(
                    text, color = Color.White, fontSize = (u.value * 50f).sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    "+ أضف حالة", color = Color.White.copy(0.75f), fontSize = (u.value * 46f).sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        // ذيل الفقاعة (دائرتين صغار)
        Box(Modifier.padding(start = u * 80f, top = u * 8f).size(u * 30f).clip(CircleShape).background(Color.Black.copy(0.40f)))
        Box(Modifier.padding(start = u * 120f, top = u * 6f).size(u * 18f).clip(CircleShape).background(Color.Black.copy(0.40f)))
    }
}

/** نافذة كتابة الحالة: نص قصير (50 حرف) يظهر 24 ساعة، مع نشر وحذف */
@Composable
fun StatusEditorSheet(existing: Status?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(existing?.text ?: "") }
    var busy by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(250)
        runCatching { focus.requestFocus() }
    }
    val noRipple = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(32.dp)

    BackHandler(onBack = onDismiss)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }
            .imePadding().navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(24.dp).fillMaxWidth().widthIn(max = 420.dp)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFF1E2B3D), Color(0xFF111A28))))
                .border(0.9.dp, Brush.verticalGradient(listOf(Color.White.copy(0.45f), Color.White.copy(0.06f))), shape)
                .clickable(interactionSource = noRipple, indication = null) { }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("حالتك", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("تظهر كفقاعة فوق صورتك الشخصية عند كل الناس لمدة 24 ساعة.", color = Color.White.copy(0.65f), fontSize = 13.sp)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(0.08f))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (text.isEmpty()) Text("شنو تفكر؟", color = Color.White.copy(0.45f), fontSize = 17.sp)
                BasicTextField(
                    value = text, onValueChange = { text = it.take(50).replace("\n", " ") },
                    maxLines = 2,
                    textStyle = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(Color(0xFF1AA7FF)),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
            }
            Text("${text.length}/50", color = Color.White.copy(0.5f), fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                val canPost = !busy && text.isNotBlank()
                Text(
                    if (busy) "..." else "نشر", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(CircleShape)
                        .background(Color(0xFF1AA7FF).copy(if (canPost) 1f else 0.4f))
                        .clickable(enabled = canPost) {
                            busy = true
                            scope.launch {
                                try {
                                    Repo.saveStatus(text.trim())
                                    onDismiss()
                                } catch (e: Exception) {
                                    busy = false
                                    AppToast.show("فشل النشر: ${e.message}", long = true)
                                }
                            }
                        }
                        .padding(vertical = 13.dp)
                )
                if (existing != null) {
                    Text(
                        "حذف", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(CircleShape)
                            .background(Color(0xFFD93025).copy(if (busy) 0.4f else 0.9f))
                            .clickable(enabled = !busy) {
                                busy = true
                                scope.launch {
                                    try {
                                        Repo.deleteStatus()
                                        onDismiss()
                                    } catch (e: Exception) {
                                        busy = false
                                        AppToast.show("فشل الحذف: ${e.message}", long = true)
                                    }
                                }
                            }
                            .padding(vertical = 13.dp)
                    )
                }
            }
        }
    }
}


/**
 * فتح قصة شخص من المحادثة (ضغطة على اقتباس "قصة فلان" بالرسالة).
 * إذا القصة انتهت أو ما أگدر أشوفها (مو صديق صاحبها) تطلع رسالة وتنسكر.
 */
@Composable
fun StoryFromChat(ownerUid: String, onDismiss: () -> Unit) {
    val myUid = remember { Repo.auth.currentUser?.uid ?: "" }
    val profile by androidx.compose.runtime.produceState<Profile?>(null, ownerUid) {
        Repo.profileFlow(ownerUid).collect { value = it }
    }
    val p = profile ?: return
    val st = p.story?.takeIf { System.currentTimeMillis() < it.expiresAt }
    if (st == null) {
        LaunchedEffect(Unit) {
            AppToast.show("هذي القصة انتهت أو ما تگدر تشوفها")
            onDismiss()
        }
        return
    }
    androidx.compose.runtime.key(ownerUid, st.createdAt) {
        StoryViewerScreen(
            story = st, owner = p, mine = ownerUid == myUid, showMenu = false,
            onDismiss = onDismiss, ownerUid = ownerUid
        )
    }
}

/** نافذة الرد على حالة شخص (من ملفه الشخصي): يوصل رده كرسالة بالمحادثة معه */
@Composable
fun StatusReplySheet(
    ownerUid: String,
    ownerName: String,
    statusText: String,
    backdrop: Backdrop,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(250); runCatching { fr.requestFocus() } }
    BackHandler(onBack = onDismiss)
    val shape = RoundedCornerShape(30.dp)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(12.dp)
                .liquidCard(backdrop, shape, dark = true)
                .background(Color.Black.copy(0.34f), shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(18.dp)
        ) {
            Text("رد على حالة ${ownerName.ifBlank { "مستخدم" }}", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(0.10f))
                    .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            ) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(Color(0xFF1AA7FF)))
                Text(
                    statusText, color = Color.White.copy(0.9f), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).heightIn(min = 52.dp)
                        .liquidCard(backdrop, RoundedCornerShape(28.dp), dark = true)
                        .background(Color.Black.copy(0.22f), RoundedCornerShape(28.dp))
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (text.isEmpty()) Text("اكتب ردك...", color = Color.White.copy(0.6f), fontSize = 16.sp)
                    BasicTextField(
                        value = text, onValueChange = { text = it.take(500) }, maxLines = 4,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(Color(0xFF1AA7FF)),
                        modifier = Modifier.fillMaxWidth().focusRequester(fr)
                    )
                }
                Spacer(Modifier.width(10.dp))
                GlassButton(
                    onClick = {
                        Repo.sendStatusReply(ownerUid, statusText, text.trim())
                        AppToast.show("انرسل ردك")
                        onDismiss()
                    },
                    backdrop = backdrop, tint = Color(0xFF1AA7FF), enabled = text.isNotBlank(),
                    modifier = Modifier.size(52.dp)
                ) { Icon(AppIcons.Reply, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
            }
        }
    }
}
