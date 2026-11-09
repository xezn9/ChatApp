package com.chatapp.messenger.ui

import com.chatapp.messenger.ui.theme.AppFont

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
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.animation.Crossfade
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.chatapp.messenger.ui.theme.GlassFx
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.drawscope.scale as drawScale
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.TextUnit
import kotlin.math.roundToInt
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.chatapp.messenger.ui.theme.isAppDark
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredHeight
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
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.platform.LocalConfiguration
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
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** أقصى مدة للقصة بالثواني (وأقصى مدة للموسيقى عليها) */
const val STORY_SECONDS = 15

/**
 * مدة عرض القصة بالثواني: إذا فيها صوت مقصوص تتبع طول الصوت (لين دقيقة)،
 * وإذا فيديو بدون صوت إضافي تتبع طول الفيديو، وغير هيج 15 ثانية.
 */
internal fun storyLenSec(s: Story): Int = when {
    s.musicUrl.isNotBlank() && s.musicEnd > s.musicStart -> (s.musicEnd - s.musicStart).coerceIn(1, StoryMedia.MAX_SECONDS)
    s.videoId.isNotBlank() && s.videoDur > 0 -> s.videoDur.coerceIn(1, StoryMedia.MAX_SECONDS)
    else -> STORY_SECONDS
}

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

class MusicTrack(
    val id: String, val title: String, val artist: String, val cover: String, val previewUrl: String,
    val source: String = "itunes", val durationSec: Int = 30
)

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

    /** بحث موحّد: يوتيوب (مقاطع قصيرة) + iTunes، النتائج متداخلة. إذا واحد فشل يظهر الثاني */
    suspend fun searchAll(term: String?): List<MusicTrack> = withContext(Dispatchers.IO) {
        val t = term?.trim().orEmpty()
        val yt = async {
            runCatching { YouTubeMusic.search(if (t.isEmpty()) "trending music" else t) }.getOrDefault(emptyList())
        }
        val itn = async {
            runCatching { if (t.isEmpty()) trending() else search(t) }.getOrDefault(emptyList())
        }
        // تيك توك: مصدر إضافي، فشله (أو بطؤه) ما يأثر على الباقي
        val tt = async {
            runCatching { TikTokMusic.search(if (t.isEmpty()) "trending music" else t) }.getOrDefault(emptyList())
        }
        val a = yt.await(); val b = itn.await(); val c = tt.await()
        if (a.isEmpty() && b.isEmpty() && c.isEmpty()) throw java.io.IOException("no results")
        val out = ArrayList<MusicTrack>()
        var ia = 0; var ib = 0; var ic = 0
        while (ia < a.size || ib < b.size || ic < c.size) {
            repeat(2) { if (ia < a.size) out.add(a[ia++]) }
            if (ic < c.size) out.add(c[ic++])
            if (ib < b.size) out.add(b[ib++])
        }
        out
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
class StoryPlayer(private val cacheDir: java.io.File? = null, private val appContext: android.content.Context? = null) {
    private var mp: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var stopper: Runnable? = null
    private var gen = 0
    var playingUrl by mutableStateOf<String?>(null)
        private set
    var ready by mutableStateOf(false)
        private set

    /** ملف بداية أغنية يوتيوب (يستعمله التجهيز المسبق والتشغيل) */
    fun headFile(url: String): java.io.File? = cacheFile("$url#head")

    private fun cacheFile(url: String): java.io.File? =
        cacheDir?.let { java.io.File(it, "story_music_${url.hashCode().toUInt()}.m4a") }

    fun play(url: String, startSec: Int = 0, maxSec: Int = STORY_SECONDS) {
        stop()
        val myGen = ++gen
        playingUrl = url
        ready = false
        Thread {
            // iTunes: مقطع 30 ثانية صغير، ننزّله للكاش. يوتيوب: بث مباشر بدون تنزيل (حتى تبدأ الأغنية بسرعة)
            var source: String = url
            var headers: Map<String, String>? = null
            val isYt = url.startsWith(YouTubeMusic.PREFIX)
            val f = cacheFile(url)
            if (url.startsWith(StoryMedia.AUDIO_PREFIX)) {
                // صوت مرفوع مع القصة: ننزّله مرة وحدة للكاش (story:<uid>/<id>)
                val spec = url.removePrefix(StoryMedia.AUDIO_PREFIX)
                val owner = spec.substringBefore('/')
                val id = spec.substringAfter('/')
                val sf = cacheDir?.let { java.io.File(it, "story_audio_$id.m4a") }
                val ok = sf != null && runCatching {
                    if (!sf.exists() || sf.length() < 512) {
                        kotlinx.coroutines.runBlocking { Repo.downloadStoryMedia(owner, id, sf) }
                    }
                    sf.exists()
                }.getOrDefault(false)
                if (!ok || sf == null) {
                    handler.post { if (myGen == gen) stop() }
                    return@Thread
                }
                source = sf.path
            } else if (url.startsWith("content:")) {
                source = url   // ملف من معرض الجهاز (معاينة بالمحرر)
            } else if (isYt) {
                // البداية (ثانية 0) = ملف محلي صغير جاهز أو ننزّله هسه (أسرع بكثير من بث HTTP). غير البداية = بث مباشر
                val head = if (startSec == 0) headFile(url) else null
                if (head != null && head.exists() && head.length() > 4096) {
                    source = head.path
                } else {
                    val info = runCatching {
                        kotlinx.coroutines.runBlocking { YouTubeMusic.resolveCached(url.removePrefix(YouTubeMusic.PREFIX)) }
                    }.getOrNull()
                    if (info == null) {
                        handler.post { if (myGen == gen) stop() }
                        return@Thread
                    }
                    if (head != null && runCatching { YouTubeMusic.downloadHead(info, head) }.getOrDefault(false)) {
                        source = head.path
                    } else {
                        source = info.url
                        headers = mapOf("User-Agent" to info.userAgent)
                    }
                }
            } else if (url.startsWith(TikTokMusic.PREFIX)) {
                // تيك توك: نحلّ رابط الصوت ونحمّله للكاش (mp3 صغير). إذا فشل التحميل نبثّه مباشرة
                val direct = runCatching {
                    kotlinx.coroutines.runBlocking { TikTokMusic.resolve(url.removePrefix(TikTokMusic.PREFIX)) }
                }.getOrNull()
                if (direct == null) {
                    handler.post { if (myGen == gen) stop() }
                    return@Thread
                }
                source = direct
                if (f != null) runCatching {
                    if (!f.exists() || f.length() < 1024) {
                        val tmp = java.io.File(f.path + ".tmp")
                        val c = URL(direct).openConnection() as java.net.HttpURLConnection
                        c.connectTimeout = 8_000; c.readTimeout = 10_000
                        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                        c.inputStream.use { inp -> tmp.outputStream().use { out -> inp.copyTo(out) } }
                        c.disconnect()
                        tmp.renameTo(f)
                    }
                    if (f.exists() && f.length() > 1024) source = f.path
                }
            } else if (f != null) {
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
                    if ((headers != null || source.startsWith("content:")) && appContext != null) {
                        p.setDataSource(appContext, android.net.Uri.parse(source), headers)
                    } else {
                        p.setDataSource(source)
                    }
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
    val ctx = LocalContext.current.applicationContext
    val p = remember { StoryPlayer(ctx.cacheDir, ctx) }
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
            else Icon(AppIcons.MusicNote, null, tint = Color.White, modifier = Modifier.size(18.dp))
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
        Modifier.width(76.dp).bounceClick(0.88f, onClick = onClick),
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
    val dark = isAppDark()
    val fg = MaterialTheme.colorScheme.onBackground
    val player = rememberStoryPlayer()

    var imageB64 by remember { mutableStateOf(existing?.image ?: "") }
    var filterId by remember { mutableStateOf(existing?.filter ?: "") }
    var musicUrl by remember { mutableStateOf(existing?.musicUrl ?: "") }
    var musicTitle by remember { mutableStateOf(existing?.musicTitle ?: "") }
    var musicArtist by remember { mutableStateOf(existing?.musicArtist ?: "") }
    var startF by remember { mutableStateOf((existing?.musicStart ?: 0).toFloat()) }
    var endF by remember {
        mutableStateOf(
            (existing?.let { if (it.musicEnd > it.musicStart) it.musicEnd else it.musicStart + STORY_SECONDS } ?: STORY_SECONDS).toFloat()
        )
    }
    var musicCover by remember { mutableStateOf("") }
    var musicDur by remember {
        val em = existing?.musicUrl ?: ""
        mutableStateOf(
            when {
                em.startsWith(StoryMedia.AUDIO_PREFIX) -> ((existing?.musicEnd ?: 0) - (existing?.musicStart ?: 0)).coerceAtLeast(1)
                em.startsWith(YouTubeMusic.PREFIX) -> 60
                em.startsWith(TikTokMusic.PREFIX) -> (existing?.musicEnd ?: 30).coerceAtLeast(30)
                else -> 30
            }
        )
    }
    // فيديو القصة: ملف جديد جاهز للرفع، أو معرّف فيديو موجود (عند التعديل)
    var videoFile by remember { mutableStateOf<java.io.File?>(null) }
    var videoId by remember { mutableStateOf(existing?.videoId ?: "") }
    var videoDur by remember { mutableStateOf(existing?.videoDur ?: 0) }
    var pubStatus by remember { mutableStateOf("") }
    var pubProgress by remember { mutableStateOf(0f) }
    val isVideoStory = videoFile != null || videoId.isNotEmpty()
    var chipSel by remember { mutableStateOf("الأكثر رواجاً") }
    // 0 = اللوحة مسكرة (البطاقة كبيرة)، 1 = فلاتر، 2 = موسيقى
    var tab by remember { mutableStateOf(if (existing == null || existing.videoId.isNotEmpty()) 0 else 1) }
    var kindSel by remember { mutableStateOf(0) }   // 0 الكل، 1 أغاني، 2 Shorts
    var lastTab by remember { mutableStateOf(1) }
    if (tab != 0) lastTab = tab
    val shownTab = if (tab != 0) tab else lastTab
    var filterCat by remember { mutableStateOf(storyFilterById(existing?.filter ?: "")?.cat ?: FilterCats[0]) }
    var busy by remember { mutableStateOf(false) }
    // true = المستخدم ضغط "تغيير الأغنية": نعرض البحث والأغنية القديمة تبقى لين يختار غيرها
    var changing by remember { mutableStateOf(false) }
    LaunchedEffect(tab) { if (tab != 2) changing = false }
    // اللوحة ما تتركّب أصلاً وهي مسكرة (أخف) وتبقى شوي حتى تخلص حركة الإغلاق
    var panelVisible by remember { mutableStateOf(tab != 0) }
    LaunchedEffect(tab) { if (tab != 0) panelVisible = true else { delay(550); panelVisible = false } }

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
                results = MusicApi.searchAll(term)
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
    // تجهيز مسبق لأول نتائج يوتيوب بالخلفية: لما تضغط تشتغل فوراً
    LaunchedEffect(results, tab) {
        if (tab == 2 && results.isNotEmpty()) {
            val ids = results.filter { it.source == "yt" }.take(6).map { it.id.removePrefix(YouTubeMusic.PREFIX) }
            runCatching { YouTubeMusic.prefetch(ids) { player.headFile(YouTubeMusic.PREFIX + it) } }
        }
    }

    // صورة أو فيديو من المعرض
    val mediaLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) scope.launch {
            val isVid = (ctx.contentResolver.getType(uri) ?: "").startsWith("video")
            try {
                if (isVid) {
                    busy = true; pubStatus = "تجهيز"; pubProgress = 0f
                    val p = MediaUtil.prepareVideo(ctx, uri) { pubProgress = it }
                    val poster = StoryMedia.posterOf(p.file)
                    videoFile?.delete()
                    videoFile = p.file; videoDur = p.durSec; videoId = ""; imageB64 = poster; filterId = ""
                    if (tab == 1) tab = 0
                } else {
                    val b64 = ImageUtil.uriToB64(ctx, uri, 1200, 70)
                    if (b64 != null) {
                        imageB64 = b64; videoFile?.delete(); videoFile = null; videoId = ""; videoDur = 0
                    } else AppToast.show("تعذّر قراءة الصورة")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppToast.show(e.message ?: "تعذّر تجهيز الملف", long = true)
            } finally {
                busy = false; pubStatus = ""
            }
        }
    }
    val pickImage: () -> Unit = {
        mediaLauncher.launch(
            androidx.activity.result.PickVisualMediaRequest(
                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageAndVideo
            )
        )
    }
    // صوت من المعرض: فيديو (ناخذ صوته بس) أو ملف صوت. بعدها نختار الجزء ونقصه
    val audioLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            val info = StoryMedia.probeAudio(ctx, uri)
            if (info == null || info.durSec <= 0) { AppToast.show("تعذّر قراءة الملف"); return@launch }
            if (!info.hasAudio) { AppToast.show("هذا الملف ما بيه صوت"); return@launch }
            player.stop()
            musicUrl = uri.toString(); musicTitle = StoryMedia.displayName(ctx, uri); musicArtist = "من المعرض"
            musicCover = ""; musicDur = info.durSec; startF = 0f; changing = false
            val len = minOf(info.durSec, 30)
            endF = len.toFloat()
            player.play(musicUrl, 0, len)
        }
    }
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
        StoryScene(image) { bd ->
            val sheetH = (LocalConfiguration.current.screenHeightDp * 0.50f).dp.coerceAtMost(420.dp)
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
                            backdrop = bd, tint = AccentBlue,
                            modifier = Modifier.height(48.dp),
                            enabled = canPublish,
                            onClick = {
                                busy = true
                                scope.launch {
                                    try {
                                        val myUid = Repo.auth.currentUser?.uid ?: throw IllegalStateException("سجّل الدخول أول")
                                        var vId = videoId
                                        var mUrl = musicUrl
                                        var mStart = startF.roundToInt()
                                        var mEnd = endF.roundToInt()
                                        // صوت من المعرض: ناخذ الصوت بس، نقص الجزء المحدد، ونرفعه
                                        if (mUrl.startsWith("content:")) {
                                            pubStatus = "قص الصوت"; pubProgress = 0f
                                            val tmp = java.io.File(MediaUtil.mediaDir(ctx), "story_audio_${System.nanoTime()}.m4a")
                                            val ok = StoryMedia.trimAudio(
                                                ctx, android.net.Uri.parse(mUrl), mStart * 1000L, mEnd * 1000L, tmp
                                            ) { pubProgress = it }
                                            if (!ok) { tmp.delete(); throw IllegalStateException("تعذّر قص الصوت، جرّب ملف ثاني") }
                                            pubStatus = "رفع الصوت"; pubProgress = 0f
                                            val aId = try {
                                                Repo.uploadStoryMedia(tmp, "audio") { pubProgress = it }
                                            } finally {
                                                tmp.delete()
                                            }
                                            mUrl = StoryMedia.AUDIO_PREFIX + myUid + "/" + aId
                                            mEnd = (mEnd - mStart).coerceAtLeast(1); mStart = 0
                                        }
                                        val vf = videoFile
                                        if (vf != null) {
                                            pubStatus = "رفع الفيديو"; pubProgress = 0f
                                            vId = Repo.uploadStoryMedia(vf, "video") { pubProgress = it }
                                            // نخلي نسخة محلية حتى ما ينزّلها صاحب القصة من جديد
                                            vf.renameTo(java.io.File(MediaUtil.mediaDir(ctx), "story_$vId.mp4"))
                                        }
                                        pubStatus = "نشر"; pubProgress = 0f
                                        // كل نشر (جديد أو تعديل) يبدأ مدة 24 ساعة من الآن
                                        Repo.saveStory(
                                            Story(
                                                text = "", color = -1, image = imageB64,
                                                createdAt = System.currentTimeMillis(), bg = 0,
                                                filter = if (vId.isNotEmpty()) "" else filterId,
                                                musicUrl = mUrl, musicTitle = musicTitle, musicArtist = musicArtist,
                                                musicCover = "", musicStart = mStart, musicEnd = mEnd,
                                                videoId = vId, videoDur = if (vId.isNotEmpty()) videoDur else 0,
                                                isPublic = existing?.isPublic ?: false
                                            )
                                        )
                                        val keep = HashSet<String>()
                                        if (vId.isNotEmpty()) keep.add(vId)
                                        if (mUrl.startsWith(StoryMedia.AUDIO_PREFIX)) keep.add(mUrl.substringAfterLast('/'))
                                        Repo.cleanupStoryMedia(keep)
                                        player.stop()
                                        onDismiss()
                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        busy = false; pubStatus = ""
                                        AppToast.show("فشل النشر: ${e.message}", long = true)
                                    }
                                }
                            }
                        ) {
                            Text(
                                if (busy) {
                                    if (pubStatus.isEmpty()) "..."
                                    else pubStatus + (if (pubProgress > 0f && pubProgress < 1f) " ${(pubProgress * 100).roundToInt()}%" else "")
                                } else if (existing == null) "نشر" else "نشر من جديد",
                                color = Color.White, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = if (busy) 14.dp else 26.dp)
                            )
                        }
                    }
                }

                // منطقة البطاقة + الأدوات + اللوحة.
                // البطاقة والأدوات واللوحة تتحرك بـ graphicsLayer فقط (بدون إعادة قياس كل فريم) = سلاسة 120Hz
                StaggerIn(1, Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp)) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val areaH = maxHeight
                        val toolsW = 60.dp
                        val toolsGap = 10.dp
                        val chromeH = 70.dp   // ارتفاع إطار اللوحة (عنوان + حشوات) غير المحتوى
                        val cardH0 = minOf(areaH - 12.dp, (maxWidth - toolsW - toolsGap) * 16f / 9f)
                        val cardW0 = cardH0 * 9f / 16f
                        val cardShape = RoundedCornerShape(34.dp)
                        // 0 = اللوحة مسكرة، 1 = مفتوحة (نابض بارتداد خفيف). تنقرأ داخل graphicsLayer فقط
                        val openP by animateFloatAsState(
                            if (tab != 0) 1f else 0f,
                            spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow), label = "panelOpen"
                        )
                        val panelH by animateDpAsState(
                            if (shownTab == 2) (if (musicUrl.isNotEmpty() && !changing) 330.dp else sheetH) else 150.dp,
                            spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow), label = "panelH"
                        )

                        // البطاقة
                        Box(
                            Modifier.align(Alignment.CenterStart).size(cardW0, cardH0)
                                .graphicsLayer {
                                    val (sc, ty) = storyCardShift(this, areaH, cardH0, panelH + chromeH, openP)
                                    scaleX = sc; scaleY = sc; translationY = ty
                                }
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
                                Column(horizontalAlignment = CenterHorizontally, modifier = Modifier.padding(horizontal = 12.dp)) {
                                    Box(
                                        Modifier.size(84.dp).clip(CircleShape)
                                            .background(Brush.linearGradient(listOf(AccentBlue.copy(0.85f), Color(0xFF8E5CFF).copy(0.85f)))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(44.dp))
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    Text("اختر صورة أو فيديو لقصتك", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                    Spacer(Modifier.height(4.dp))
                                    Text("ثم أضف فلتر وموسيقى", color = Color.White.copy(0.6f), fontSize = 13.sp, textAlign = TextAlign.Center)
                                }
                            }

                            if (isVideoStory && image != null) {
                                Row(
                                    Modifier.align(Alignment.TopStart).padding(12.dp)
                                        .glassPill(bd, CircleShape, Color.Transparent, dark)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("فيديو ${videoDur}ث", color = Color.White, fontSize = 12.sp)
                                }
                            }

                            // شريحة الأغنية المختارة (تظهر لك بالمحرر فقط، مو على القصة المنشورة)
                            if (musicUrl.isNotEmpty()) {
                                Row(
                                    Modifier.align(Alignment.BottomCenter).padding(12.dp)
                                        .bounceClick(0.9f) { tab = 2 }
                                        .glassPill(bd, CircleShape, Color.Transparent, dark)
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(AppIcons.MusicNote, null, tint = Color.White, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        musicTitle, color = Color.White, fontSize = 12.sp, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = (cardW0 - 80.dp).coerceAtLeast(40.dp))
                                    )
                                }
                            }
                        }

                        // أدوات التحرير: برا البطاقة، وتنزاح مع مركزها
                        Column(
                            Modifier.align(Alignment.CenterEnd).width(toolsW)
                                .wrapContentHeight(align = Alignment.CenterVertically, unbounded = true)
                                .graphicsLayer {
                                    translationY = storyCardShift(this, areaH, cardH0, panelH + chromeH, openP).second
                                },
                            horizontalAlignment = CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(if (tab != 0) 10.dp else 14.dp)
                        ) {
                            StoryToolButton(AppIcons.Image, "صورة", false, fg, bd, dark, 0, tab == 0) { pickImage() }
                            StoryToolButton(AppIcons.Sparkles, "فلاتر", tab == 1, fg, bd, dark, 1, tab == 0) {
                                if (isVideoStory) AppToast.show("الفلاتر للصور فقط")
                                else { player.stop(); tab = if (tab == 1) 0 else 1 }
                            }
                            StoryToolButton(AppIcons.MusicNote, "موسيقى", tab == 2 || musicUrl.isNotEmpty(), fg, bd, dark, 2, tab == 0) { tab = if (tab == 2) 0 else 2 }
                        }

                        // لوحة الأداة المختارة: تنزلق من تحت بـ translationY (ما تتقاس من جديد كل فريم)
                        if (panelVisible) {
                            Column(
                                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 12.dp)
                                    .graphicsLayer {
                                        translationY = (1f - openP) * (size.height + 40.dp.toPx())
                                        alpha = openP.coerceIn(0f, 1f)
                                    }
                                    .liquidCard(bd, RoundedCornerShape(28.dp), dark).padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (shownTab == 1) "الفلاتر" else "الموسيقى",
                                fontSize = 17.sp, fontWeight = FontWeight.Bold, color = fg,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                Modifier.bounceClick(0.8f) { player.stop(); tab = 0 }
                                    .size(32.dp).glassPill(bd, CircleShape, Color.Transparent, dark),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Close, null, tint = fg.copy(0.85f), modifier = Modifier.size(18.dp))
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(panelH)) {
                            when (shownTab) {
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
                                                modifier = Modifier.bounceClick(0.88f) { filterCat = c }
                                            ) {
                                                Text(
                                                    c, fontSize = 15.sp,
                                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (sel) fg else fg.copy(0.55f)
                                                )
                                                Box(
                                                    Modifier.padding(top = 4.dp).height(2.dp).width(
                                                        animateDpAsState(
                                                            if (sel) 28.dp else 0.dp,
                                                            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "uw"
                                                        ).value
                                                    )
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

                                else -> MusicPanel(
                                    bd = bd, fg = fg, player = player,
                                    musicUrl = musicUrl, musicTitle = musicTitle, musicArtist = musicArtist,
                                    musicCover = musicCover, musicDur = musicDur,
                                    changing = changing,
                                    onChangeSong = { player.stop(); changing = true; if (results.isEmpty()) loadMusic(null) },
                                    startF = startF, endF = endF,
                                    onRangeChange = { st, en -> startF = st; endF = en },
                                    onRangeFinished = { player.play(musicUrl, startF.roundToInt(), (endF - startF).roundToInt().coerceAtLeast(1)) },
                                    query = query, onQueryChange = { query = it },
                                    chipSel = chipSel, onChip = { label, term -> chipSel = label; query = ""; loadMusic(term) },
                                    onSearch = { chipSel = ""; loadMusic(query.trim()) },
                                    results = results, loading = loading, failed = failed,
                                    kindSel = kindSel, onKind = { kindSel = it },
                                    maxLen = if (musicUrl.startsWith("content:") || musicUrl.startsWith(StoryMedia.AUDIO_PREFIX) || musicUrl.startsWith(TikTokMusic.PREFIX)) StoryMedia.MAX_SECONDS else STORY_SECONDS,
                                    onPickFile = { audioLauncher.launch(arrayOf("audio/*", "video/*")) },
                                    onPick = { t ->
                                        musicUrl = t.previewUrl; musicTitle = t.title; musicArtist = t.artist
                                        musicCover = t.cover; musicDur = t.durationSec; startF = 0f; changing = false
                                        // تيك توك: الصوت كامل (لين أقصى مدة قصة)، وتكدر تقص منه. الباقي 15 ثانية
                                        val initLen = if (t.source == TikTokMusic.SOURCE) minOf(StoryMedia.MAX_SECONDS, t.durationSec)
                                                      else minOf(STORY_SECONDS, t.durationSec)
                                        endF = initLen.toFloat()
                                        player.play(t.previewUrl, 0, initLen)
                                    },
                                    onClear = {
                                        player.stop()
                                        musicUrl = ""; musicTitle = ""; musicArtist = ""; musicCover = ""; startF = 0f; endF = STORY_SECONDS.toFloat()
                                        changing = false
                                        tab = 0
                                    }
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

// ======================= لوحة اختيار الموسيقى =======================

/** ضغطة بارتداد: يصغر لما تضغط ويرجع بنابض. لازم يجي أول المودفاير حتى يتحجم كل اللي بعده */
private fun Modifier.bounceClick(pressedScale: Float = 0.86f, enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "bounceClick"
    )
    this.graphicsLayer { scaleX = sc; scaleY = sc }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

/**
 * زجاج سائل لأي عنصر: تمويه خفيف + انكسار الحواف (lens) + سطح شفاف + حد لامع.
 * tint شفاف (alpha=0) = زجاج صافي، وإذا فيه لون يصبغ الزجاج بدون ما يخفي الانكسار.
 */
private fun Modifier.glassPill(bd: Backdrop, shape: Shape, tint: Color, dark: Boolean): Modifier =
    this.drawBackdrop(
        backdrop = bd,
        shape = { shape },
        effects = {
            vibrancy()
            blur((3f.dp.toPx()) * GlassFx.blurK)
            lens((10f.dp.toPx()) * GlassFx.refractK, (20f.dp.toPx()) * GlassFx.refractK)
        },
        onDrawSurface = {
            drawRect(if (dark) Color.White.copy(0.10f) else Color.White.copy(0.30f))
            if (tint.alpha > 0f) drawRect(tint.copy(alpha = tint.alpha * 0.62f))
        }
    ).border(
        0.8.dp,
        Brush.linearGradient(listOf(Color.White.copy(0.75f), Color.White.copy(0.06f), Color.White.copy(0.40f))),
        shape
    )

/** حلقة تحميل دوّارة (قوس متدرج) */
@Composable
private fun LoadingRing(modifier: Modifier = Modifier, color: Color = Color.White) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "ring")
    val ang by t.animateFloat(
        0f, 360f,
        androidx.compose.animation.core.infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "ringA"
    )
    Canvas(modifier) {
        val sw = size.minDimension * 0.13f
        val d = size.minDimension - sw
        val tl = Offset(sw / 2f, sw / 2f)
        val sz = androidx.compose.ui.geometry.Size(d, d)
        drawArc(color.copy(0.22f), 0f, 360f, false, tl, sz, style = Stroke(sw))
        rotate(ang) {
            drawArc(
                Brush.sweepGradient(listOf(Color.Transparent, color)),
                0f, 300f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round)
            )
        }
    }
}

/** لمعة تمرّ على العنصر وهو يحمّل */
private fun Modifier.shimmer(active: Boolean): Modifier = if (!active) this else composed {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        -0.5f, 1.5f,
        androidx.compose.animation.core.infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "shimmerX"
    )
    drawWithContent {
        drawContent()
        val w = size.width
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(0.28f), Color.Transparent),
                start = Offset(w * x - w * 0.25f, 0f), end = Offset(w * x + w * 0.25f, size.height * 0.5f)
            )
        )
    }
}

/** زجاج خفيف بدون backdrop (للقوائم والتمرير): سطح شفاف + حد لامع. أرخص بكثير من glassPill */
private fun Modifier.softGlass(shape: Shape, tint: Color, dark: Boolean): Modifier =
    this.background(if (dark) Color.White.copy(0.10f) else Color.White.copy(0.34f), shape)
        .background(tint.copy(alpha = tint.alpha * 0.78f), shape)
        .border(
            0.8.dp,
            Brush.linearGradient(listOf(Color.White.copy(0.55f), Color.White.copy(0.06f), Color.White.copy(0.30f))),
            shape
        )

/** (تصغير، إزاحة عمودية) للبطاقة لما تنفتح اللوحة: تنقرأ داخل graphicsLayer فقط */
private fun storyCardShift(d: Density, areaH: Dp, cardH0: Dp, panelTotal: Dp, openP: Float): Pair<Float, Float> = with(d) {
    val a = areaH.toPx()
    val avail = (a - panelTotal.toPx() - 24.dp.toPx()).coerceAtLeast(60.dp.toPx())
    val s1 = (avail / cardH0.toPx()).coerceIn(0.2f, 1f)
    val sc = 1f + (s1 - 1f) * openP
    val ty = (avail / 2f - a / 2f) * openP
    sc to ty
}

/**
 * خلفية محرر القصة: هادئة وثابتة (بدون حركة) حتى الزجاج ما يعيد رسمه كل فريم.
 * إذا فيه صورة: تنعرض ضبابية وملوّنة خلف الزجاج (مثل Apple Music) فيكسرها الزجاج بشكل حقيقي.
 */
@Composable
private fun StoryScene(art: ImageBitmap?, content: @Composable (Backdrop) -> Unit) {
    val bd = rememberLayerBackdrop()
    val dark = isAppDark()
    // نصغّر الصورة لحجم صغير جداً وتتكبّر ناعمة = تمويه مجاني بدون تكلفة GPU
    val tiny = remember(art) {
        art?.let { runCatching { android.graphics.Bitmap.createScaledBitmap(it.asAndroidBitmap(), 18, 32, true).asImageBitmap() }.getOrNull() }
    }
    val base = if (dark) Brush.verticalGradient(listOf(Color(0xFF0B0A1F), Color(0xFF1A1038), Color(0xFF0A1B2B)))
    else Brush.verticalGradient(listOf(Color(0xFFF6EEFF), Color(0xFFFFEEF5), Color(0xFFE9F6FF)))
    val sat = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.5f) }) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().layerBackdrop(bd).background(base)) {
            Canvas(Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                fun blob(c: Color, al: Float, cx: Float, cy: Float, r: Float) {
                    drawCircle(
                        Brush.radialGradient(listOf(c.copy(alpha = al), c.copy(alpha = al * 0.4f), Color.Transparent), center = Offset(cx, cy), radius = r),
                        r, Offset(cx, cy)
                    )
                }
                val k = if (dark) 1f else 0.8f
                blob(Color(0xFF5B4BFF), 0.55f * k, w * 0.20f, h * 0.18f, w * 0.80f)
                blob(Color(0xFFD1409B), 0.40f * k, w * 0.98f, h * 0.44f, w * 0.65f)
                blob(Color(0xFF14B8C4), 0.38f * k, w * 0.08f, h * 0.82f, w * 0.75f)
                blob(Color(0xFFFF9F5A), 0.24f * k, w * 0.88f, h * 1.00f, w * 0.55f)
            }
            Crossfade(targetState = tiny, animationSpec = tween(700), label = "storyArt") { img ->
                if (img != null) {
                    Box(Modifier.fillMaxSize()) {
                        Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = sat, filterQuality = FilterQuality.Low)
                        Box(Modifier.fillMaxSize().background(if (dark) Color.Black.copy(0.42f) else Color.White.copy(0.35f)))
                    }
                } else {
                    Box(Modifier.fillMaxSize())
                }
            }
        }
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground
        ) { content(bd) }
    }
}

/** زر أداة دائري على جنب بطاقة القصة: يطلع بارتداد، يصغر عند الضغط، وأيقونته تلف شوي لما يتفعل */
@Composable
private fun StoryToolButton(
    icon: ImageVector, label: String, active: Boolean, fg: Color, bd: Backdrop, dark: Boolean,
    index: Int, showLabel: Boolean, onClick: () -> Unit
) {
    val bg by animateColorAsState(if (active) AccentBlue else Color.Transparent, label = "toolBg")
    val sc by animateFloatAsState(
        if (active) 1.08f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "toolScale"
    )
    val rot by animateFloatAsState(
        if (active) 15f else 0f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMediumLow), label = "toolRot"
    )
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(180L + index * 90L)
        pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
    }
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = pop.value.coerceIn(0f, 1f) }
            .bounceClick(0.82f, onClick = onClick)
    ) {
        Box(
            Modifier.size(46.dp).graphicsLayer { scaleX = sc; scaleY = sc }.glassPill(bd, CircleShape, bg, dark),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, null, tint = if (active) Color.White else fg,
                modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = rot }
            )
        }
        if (showLabel) Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = fg, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
    }
}

/** زر صغير للضبط الدقيق (± ثانية) بأيقونة */
@Composable
private fun FineStepButton(icon: ImageVector, label: String, fg: Color, bd: Backdrop, dark: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.bounceClick(0.84f, onClick = onClick).glassPill(bd, CircleShape, Color.Transparent, dark)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = fg.copy(0.9f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(3.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = fg.copy(0.9f), maxLines = 1, softWrap = false)
    }
}

/**
 * نص عنوان/فنان: العربي يمشي طبيعي، واللاتيني يُرسم LTR كامل ويلتصق باليمين.
 * (قبل: العناوين الإنجليزية الطويلة بواجهة RTL كانت تنقطع بدري وتختفي أول حروفها)
 */
@Composable
private fun BidiText(
    text: String, fontSize: TextUnit, color: Color,
    fontWeight: FontWeight? = null, modifier: Modifier = Modifier
) {
    val hasArabic = text.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }
    if (hasArabic) {
        Text(text, fontSize = fontSize, color = color, fontWeight = fontWeight, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
    } else {
        androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                text, fontSize = fontSize, color = color, fontWeight = fontWeight, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Right, modifier = modifier.fillMaxWidth()
            )
        }
    }
}

/** موجات صوتية متحركة (تظهر على الأغنية اللي تشتغل) */
@Composable
private fun Equalizer(color: Color, modifier: Modifier = Modifier) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "eq")
    val hs = listOf(380, 520, 440).mapIndexed { i, ms ->
        t.animateFloat(
            0.25f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(
                tween(ms, easing = LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse,
                androidx.compose.animation.core.StartOffset(i * 120)
            ), label = "b$i"
        )
    }
    Row(modifier.height(16.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        hs.forEach { h -> Box(Modifier.width(3.dp).fillMaxHeight(h.value).clip(CircleShape).background(color)) }
    }
}

private fun fmtSec(t: Int) = "${t / 60}:${"%02d".format(t % 60)}"

private fun isShort(t: MusicTrack) = t.source == "yt" && t.durationSec <= 90

@Composable
private fun SourceBadge(t: MusicTrack) {
    val short = isShort(t)
    val (label, color) = when {
        short -> "Shorts" to Color(0xFFFF5A36)
        t.source == "yt" -> "YouTube" to Color(0xFFE53935)
        t.source == TikTokMusic.SOURCE -> "TikTok" to Color(0xFFFE2C55)
        else -> "iTunes" to Color(0xFF7C7C8A)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White,
            modifier = Modifier.clip(CircleShape).background(color).padding(horizontal = 7.dp, vertical = 2.dp)
        )
        if ((t.source == "yt" || t.source == TikTokMusic.SOURCE) && t.durationSec > 0) {
            Text("  " + fmtSec(t.durationSec), fontSize = 11.sp, color = Color.White.copy(0.6f))
        }
    }
}

/**
 * اختيار جزء الأغنية: شريط موجات، والمربع الملوّن = الجزء اللي راح ينحط بالقصة.
 * - السحب: المربع يتبع إصبعك مباشرة.
 * - اللمس بمكان أو أزرار 5/10/15 و±1: المربع ينزلق للمكان الجديد بحركة نابضة (ارتداد).
 * - عند اللمس يتمدد المربع شوي ويرجع بارتداد.
 */
@Composable
private fun SegmentPicker(
    total: Int, start: Float, len: Float, seed: Int, bd: Backdrop, dark: Boolean,
    onStart: (Float) -> Unit, onFinished: () -> Unit
) {
    val accent = AccentBlue
    val fg = MaterialTheme.colorScheme.onBackground
    val bars = remember(seed) {
        val r = java.util.Random(seed.toLong())
        FloatArray(60) { 0.22f + r.nextFloat() * 0.78f }
    }
    val curLen by rememberUpdatedState(len)
    val curOnStart by rememberUpdatedState(onStart)
    val curFinished by rememberUpdatedState(onFinished)
    val minWin = with(androidx.compose.ui.platform.LocalDensity.current) { 36.dp.toPx() }

    // الموضع والعرض كنسب (0..1) حتى ما نحتاج عرض الشريط بالبكسل هنا
    val range = (total - len).coerceAtLeast(0f)
    val targetPos = if (range > 0f) (start / range).coerceIn(0f, 1f) else 0f
    val targetFrac = (len / total).coerceIn(0f, 1f)
    val pos = remember { Animatable(targetPos) }
    val frac = remember { Animatable(targetFrac) }
    var dragging by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(
        if (pressed) 1.06f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "segPress"
    )
    LaunchedEffect(targetPos, targetFrac, dragging) {
        if (dragging) {
            pos.snapTo(targetPos); frac.snapTo(targetFrac)
        } else {
            launch { pos.animateTo(targetPos, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
            launch { frac.animateTo(targetFrac, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)) }
        }
    }

    // الشريط دائماً من اليسار لليمين (الوقت يمشي يمين)، حتى ما يختلط مع اتجاه التطبيق
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Canvas(
            Modifier.fillMaxWidth().height(76.dp).softGlass(RoundedCornerShape(18.dp), Color.Transparent, dark).clip(RoundedCornerShape(18.dp))
                .pointerInput(total, minWin) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pressed = true
                        fun move(x: Float) {
                            val l = curLen
                            val vw = maxOf(l / total * size.width, minWin)
                            val rg = (total - l).coerceAtLeast(0f)
                            val f = ((x - vw / 2f) / (size.width - vw)).coerceIn(0f, 1f)
                            curOnStart(f * rg)
                        }
                        move(down.position.x)   // لمسة وحدة = ينزلق بنابض للمكان
                        horizontalDrag(down.id) { change ->
                            dragging = true     // سحب = يتبع الإصبع مباشرة
                            move(change.position.x)
                            change.consume()
                        }
                        dragging = false
                        pressed = false
                        curFinished()
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val vw = maxOf(frac.value * w, minWin)
            val left = pos.value * (w - vw)
            val sw = 2.5.dp.toPx()
            val inset = sw / 2f   // نرسم الإطار للداخل حتى ما ينقص نصه عند الحافة
            val corner = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx(), 14.dp.toPx())
            val pivot = Offset(left + vw / 2f, h / 2f)
            // خلفية الجزء المحدد
            drawScale(press, 1f, pivot) {
                drawRoundRect(
                    accent.copy(0.20f), Offset(left + inset, inset),
                    androidx.compose.ui.geometry.Size(vw - sw, h - sw), corner
                )
            }
            // الموجات
            val n = bars.size
            val gap = 3.dp.toPx()
            val bw = (w - gap * (n - 1)) / n
            for (i in 0 until n) {
                val x = i * (bw + gap)
                val bh = h * 0.68f * bars[i]
                val inWin = x + bw / 2f in left..(left + vw)
                drawRoundRect(
                    if (inWin) accent else fg.copy(0.28f),
                    Offset(x, (h - bh) / 2f),
                    androidx.compose.ui.geometry.Size(bw, bh),
                    androidx.compose.ui.geometry.CornerRadius(bw / 2f, bw / 2f)
                )
            }
            // إطار الجزء المحدد
            drawScale(press, 1f, pivot) {
                drawRoundRect(
                    accent, Offset(left + inset, inset),
                    androidx.compose.ui.geometry.Size(vw - sw, h - sw), corner,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(sw)
                )
            }
        }
    }
}

@Composable
private fun MusicPanel(
    bd: Backdrop, fg: Color, player: StoryPlayer,
    musicUrl: String, musicTitle: String, musicArtist: String, musicCover: String, musicDur: Int,
    changing: Boolean, onChangeSong: () -> Unit,
    startF: Float, endF: Float, onRangeChange: (Float, Float) -> Unit, onRangeFinished: () -> Unit,
    query: String, onQueryChange: (String) -> Unit,
    chipSel: String, onChip: (String, String) -> Unit, onSearch: () -> Unit,
    results: List<MusicTrack>, loading: Boolean, failed: Boolean,
    kindSel: Int, onKind: (Int) -> Unit,
    onPick: (MusicTrack) -> Unit, onClear: () -> Unit,
    maxLen: Int = STORY_SECONDS, onPickFile: () -> Unit = {}
) {
    val dark = isAppDark()
    if (musicUrl.isNotEmpty() && !changing) {
        // ---------- أغنية مختارة + تحديد الجزء ----------
        val cover = rememberRemoteImage(musicCover)
        val playing = player.playingUrl == musicUrl
        val loading = playing && !player.ready
        val total = musicDur.coerceAtLeast(STORY_SECONDS)
        // طول الجزء بثواني صحيحة: قبل كان الفرق بين الفلوتات يتراكم فيه خطأ (14.99) فيطلع 0:14 ولا ينعلّم أي زر
        val len = (endF - startF).roundToInt().coerceAtLeast(1)
        val startR = startF.roundToInt()
        fun setStart(ns: Float, l: Int) {
            val s = ns.coerceIn(0f, (total - l).coerceAtLeast(0).toFloat())
            onRangeChange(s, s + l)
        }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth().softGlass(RoundedCornerShape(20.dp), Color.Transparent, dark).shimmer(loading).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF2B2F36)), contentAlignment = Alignment.Center) {
                    if (cover != null) Image(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(AppIcons.MusicNote, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    if (loading) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.45f)), contentAlignment = Alignment.Center) {
                            LoadingRing(Modifier.size(28.dp))
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    BidiText(musicTitle, 15.sp, fg, FontWeight.Bold)
                    BidiText(musicArtist, 12.sp, fg.copy(0.6f))
                }
                if (loading) {
                    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        LoadingRing(Modifier.size(18.dp), AccentBlue)
                        Spacer(Modifier.width(6.dp))
                        Text("جاري التحميل", fontSize = 11.sp, color = fg.copy(0.75f), maxLines = 1, softWrap = false)
                    }
                } else if (playing) Equalizer(AccentBlue, Modifier.padding(horizontal = 8.dp))
            }
            if (total > STORY_SECONDS) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("اسحب على الشريط لاختيار الجزء", fontSize = 12.sp, color = fg.copy(0.7f))
                        // الوقت دائماً LTR: "0:00 → 0:15" (قبل: بواجهة RTL ينعكس ويطلع 0:15 → 0:00)
                        androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                "${fmtSec(startR)} → ${fmtSec(startR + len)}",
                                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentBlue,
                                maxLines = 1, softWrap = false
                            )
                        }
                    }
                    SegmentPicker(
                        total = total, start = startF, len = len.toFloat(), seed = musicUrl.hashCode(), bd = bd, dark = dark,
                        onStart = { setStart(it, len) },
                        onFinished = onRangeFinished
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        // طول الجزء (الزر المختار يكبر بارتداد)
                        (listOf(5, 10, 15, 30, 60) + minOf(total, maxLen)).distinct().sorted().filter { it <= total && it <= maxLen }.forEach { l ->
                            val sel = len == l
                            val sc by animateFloatAsState(
                                if (sel) 1.1f else 1f,
                                spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "lenScale"
                            )
                            val chipBg by animateColorAsState(if (sel) AccentBlue else Color.Transparent, label = "lenBg")
                            Text(
                                "${l}ث", fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                color = if (sel) Color.White else fg.copy(0.85f), maxLines = 1, softWrap = false,
                                modifier = Modifier.graphicsLayer { scaleX = sc; scaleY = sc }
                                    .bounceClick(0.86f) { setStart(startF, l); onRangeFinished() }
                                    .glassPill(bd, CircleShape, chipBg, dark)
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        // ضبط دقيق بأيقونات (− / +) بدل نص "−1ث" اللي كان ينقلب وينكسر لسطرين
                        FineStepButton(AppIcons.Minus, "1ث", fg, bd, dark) { setStart(startR - 1f, len); onRangeFinished() }
                        FineStepButton(Icons.Filled.Add, "1ث", fg, bd, dark) { setStart(startR + 1f, len); onRangeFinished() }
                    }
                }
            } else {
                Text("المقطع قصير، يشتغل كامل.", fontSize = 13.sp, color = fg.copy(0.7f))
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(
                    onClick = onChangeSong, backdrop = bd,
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    Icon(AppIcons.MusicNote, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("تغيير الأغنية", fontWeight = FontWeight.Medium)
                }
                GlassButton(
                    onClick = { player.stop(); onClear() }, backdrop = bd, tint = Color(0xFFE53935),
                    modifier = Modifier.size(46.dp)
                ) { Icon(Icons.Filled.Delete, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            }
        }
        return
    }

    // ---------- بحث واختيار ----------
    val shown = when (kindSel) {
        1 -> results.filter { !isShort(it) }
        2 -> results.filter { isShort(it) }
        else -> results
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // صوت من المعرض (فيديو أو ملف صوت)
        Row(
            Modifier.fillMaxWidth().bounceClick(0.97f) { onPickFile() }
                .softGlass(RoundedCornerShape(18.dp), AccentBlue.copy(0.25f), dark)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(AppIcons.Image, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("من المعرض: فيديو أو ملف صوت", color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(
            Modifier.fillMaxWidth().softGlass(RoundedCornerShape(18.dp), Color.Transparent, dark)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, null, tint = fg.copy(0.6f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("ابحث عن أغنية أو Short...", color = fg.copy(0.45f), fontSize = 14.sp)
                BasicTextField(
                    value = query, onValueChange = onQueryChange, singleLine = true,
                    textStyle = TextStyle(fontFamily = AppFont, color = fg, fontSize = 15.sp),
                    cursorBrush = SolidColor(AccentBlue),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onSearch() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (loading) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AccentBlue
                )
            } else if (query.isNotEmpty()) {
                Icon(
                    Icons.Filled.Close, null, tint = fg.copy(0.6f),
                    modifier = Modifier.bounceClick(0.75f) { onQueryChange("") }.size(20.dp).clip(CircleShape)
                )
            }
        }
        // نوع النتائج: الكل / أغاني / Shorts
        Row(
            Modifier.fillMaxWidth().softGlass(CircleShape, Color.Transparent, dark).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            listOf("الكل", "أغاني", "Shorts").forEachIndexed { i, label ->
                val sel = kindSel == i
                val kbg by animateColorAsState(if (sel) AccentBlue else Color.Transparent, label = "kindBg")
                Text(
                    label, fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    color = if (sel) Color.White else fg.copy(0.8f), textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).bounceClick(0.92f) { onKind(i) }
                        .softGlass(CircleShape, kbg, dark).padding(vertical = 7.dp)
                )
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "الأكثر رواجاً" to "", "عربي" to "اغاني عربية", "عراقي" to "اغاني عراقية",
                "Pop" to "pop hits", "Hip Hop" to "hip hop", "Rock" to "rock", "Chill" to "chill lofi"
            ).forEach { (label, term) ->
                val sel = chipSel == label
                val cbg by animateColorAsState(if (sel) AccentBlue else Color.Transparent, label = "chipBg2")
                Text(
                    label, fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    color = if (sel) Color.White else fg.copy(0.85f),
                    modifier = Modifier.bounceClick(0.88f) { onChip(label, term) }
                        .softGlass(CircleShape, cbg, dark)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
        // حالة تيك توك (للتشخيص): تظهر سبب الفشل إذا ما طلعت نتائجه
        val ttStatus by TikTokMusic.status.collectAsState()
        if (ttStatus.isNotEmpty()) {
            Text(ttStatus, color = fg.copy(0.5f), fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading && results.isEmpty() -> Text("جاري البحث...", color = fg.copy(0.6f), fontSize = 13.sp, modifier = Modifier.align(Alignment.Center))
                failed -> Text("تعذّر تحميل الأغاني، تأكد من الإنترنت وجرّب مرة ثانية.", color = fg.copy(0.6f), fontSize = 13.sp, modifier = Modifier.align(Alignment.Center), textAlign = TextAlign.Center)
                shown.isEmpty() -> Text(
                    if (results.isEmpty()) "ما لقيت نتائج" else "ما فيه نتائج من هذا النوع، جرّب \"الكل\"",
                    color = fg.copy(0.6f), fontSize = 13.sp, modifier = Modifier.align(Alignment.Center), textAlign = TextAlign.Center
                )
                else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(shown, key = { it.source + it.id }) { t ->
                        val cover = rememberRemoteImage(t.cover)
                        val playing = player.playingUrl == t.previewUrl
                        val loading = playing && !player.ready
                        Row(
                            Modifier.fillMaxWidth()
                                .bounceClick(0.97f) { if (playing) player.stop() else player.play(t.previewUrl, 0) }
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (playing) AccentBlue.copy(0.14f) else Color.Transparent)
                                .shimmer(loading)
                                .padding(vertical = 6.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2B2F36)), contentAlignment = Alignment.Center) {
                                if (cover != null) Image(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                else Icon(AppIcons.MusicNote, null, tint = Color.White, modifier = Modifier.size(22.dp))
                                Box(Modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(0.55f)), contentAlignment = Alignment.Center) {
                                    if (loading) {
                                        LoadingRing(Modifier.size(20.dp))
                                    } else if (playing) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                            repeat(2) { Box(Modifier.width(3.5.dp).height(11.dp).clip(CircleShape).background(Color.White)) }
                                        }
                                    } else {
                                        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                BidiText(t.title, 14.sp, fg, FontWeight.SemiBold)
                                BidiText(t.artist, 12.sp, fg.copy(0.6f))
                                Spacer(Modifier.height(3.dp))
                                SourceBadge(t)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "اختيار", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.bounceClick(0.82f) { onPick(t) }
                                    .softGlass(CircleShape, AccentBlue, dark)
                                    .padding(horizontal = 16.dp, vertical = 9.dp)
                            )
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
    // فيديو القصة: ينزّل مرة وحدة للكاش، والقصة تنتظره يجهز
    val storySec = remember(story) { storyLenSec(story) }
    val isVideo = story.videoId.isNotBlank()
    val hasMusic = story.musicUrl.isNotBlank()
    var videoFile by remember(story.videoId) { mutableStateOf<java.io.File?>(null) }
    var videoReady by remember(story.videoId) { mutableStateOf(!isVideo) }
    val videoRef = remember { arrayOfNulls<android.widget.VideoView>(1) }
    LaunchedEffect(story.videoId) {
        if (isVideo) {
            val f = java.io.File(MediaUtil.mediaDir(ctx), "story_${story.videoId}.mp4")
            val ok = (f.exists() && f.length() > 1024) ||
                runCatching { Repo.downloadStoryMedia(realOwner, story.videoId, f) }.isSuccess
            if (ok && f.exists()) videoFile = f else { videoReady = true; AppToast.show("تعذّر تحميل الفيديو") }
        }
    }
    DisposableEffect(Unit) { onDispose { runCatching { videoRef[0]?.stopPlayback() } } }
    val paused = menuOpen || confirmDelete || replyFocused || holding || viewersOpen || shareOpen || musicLoading || !videoReady
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
            player.play(story.musicUrl, story.musicStart, storySec)
            delay(if (story.musicUrl.startsWith(StoryMedia.AUDIO_PREFIX)) 20_000 else 6_000)
            musicTimeout = true
        }
    }
    // التقدم يوقف لما تنفتح القائمة أو التأكيد، ويكمل بعدها
    LaunchedEffect(paused) {
        if (paused) {
            player.pause()
            runCatching { videoRef[0]?.pause() }
        } else {
            player.resume()
            runCatching { videoRef[0]?.start() }
            val leftMs = ((1f - prog.value) * storySec * 1000).toInt().coerceAtLeast(1)
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
            val vf = videoFile
            if (vf != null) {
                androidx.compose.ui.viewinterop.AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c ->
                        val vv = android.widget.VideoView(c)
                        vv.setVideoPath(vf.path)
                        vv.setOnPreparedListener { mp ->
                            mp.isLooping = true
                            // إذا القصة إلها أغنية، صوت الفيديو الأصلي ينكتم
                            if (hasMusic) mp.setVolume(0f, 0f)
                            videoReady = true
                        }
                        vv.setOnErrorListener { _, _, _ -> videoReady = true; AppToast.show("تعذّر تشغيل الفيديو"); true }
                        videoRef[0] = vv
                        android.widget.FrameLayout(c).also {
                            it.addView(
                                vv,
                                android.widget.FrameLayout.LayoutParams(
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                    android.view.Gravity.CENTER
                                )
                            )
                        }
                    }
                )
            }
            if (story.text.isNotBlank()) {
                Text(
                    story.text, color = Color(story.color), fontSize = storyFontSize(story.text.length),
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(28.dp),
                    style = TextStyle(fontFamily = AppFont, shadow = StoryTextShadow)
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
                            style = TextStyle(fontFamily = AppFont, shadow = StoryTextShadow)
                        )
                        Text(subtitle, color = Color.White.copy(0.85f), fontSize = 12.sp, style = TextStyle(fontFamily = AppFont, shadow = StoryTextShadow))
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
                        textStyle = TextStyle(fontFamily = AppFont, color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(AccentBlue),
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
                        backdrop = bd, tint = AccentBlue,
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
                                    .background(if (on) AccentBlue else Color.White.copy(0.14f))
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
                    backdrop = bd, tint = AccentBlue, enabled = picked.isNotEmpty(),
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

/** نافذة كتابة الحالة (Liquid Glass): معاينة مباشرة، اقتراحات سريعة، نشر وحذف. الأزرار مثبتة وما تنعجن مع الكيبورد */
@Composable
fun StatusEditorSheet(existing: Status?, backdrop: Backdrop, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(existing?.text ?: "") }
    var busy by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(250)
        runCatching { focus.requestFocus() }
    }
    val noRipple = remember { MutableInteractionSource() }
    val panelShape = RoundedCornerShape(32.dp)
    val maxLen = 50
    val accent = AccentBlue
    val canPost = !busy && text.isNotBlank()
    val nearLimit = text.length >= 42
    val fg = Color.White

    // الزجاج يكسر الشاشة الحقيقية اللي وراه (نفس backdrop صفحة الأكونت)
    val bd = backdrop

    fun post() {
        if (!canPost) return
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

    val suggestions = remember {
        listOf(
            "😎 رايق", "🎧 أسمع أغاني", "🎮 ألعب", "📚 أدرس",
            "💼 مشغول", "😴 نايم", "🍕 جوعان", "🚗 بالطريق"
        )
    }

    BackHandler(onBack = onDismiss)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(0.28f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }
            .statusBarsPadding().imePadding().navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        // لوحة زجاج سائل شفافة: تغبيش قليل + انكسار للشاشة اللي وراها
        Box(Modifier.padding(horizontal = 22.dp, vertical = 16.dp).fillMaxWidth().widthIn(max = 420.dp)) {
            Column(
                Modifier.fillMaxWidth()
                    .glassSurface(bd, dark = true, shape = panelShape, blurDp = 3f)
                    .clickable(interactionSource = noRipple, indication = null) { }
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                // ===== الرأس (ثابت) =====
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("حالتك", color = fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "تظهر فوق صورتك عند كل الناس، وتختفي بعد 24 ساعة",
                            color = fg.copy(0.65f), fontSize = 12.5.sp, lineHeight = 18.sp
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    GlassButton(onClick = onDismiss, backdrop = bd, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Close, null, tint = fg, modifier = Modifier.size(19.dp))
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ===== الجزء القابل للتمرير (يتقلص مع الكيبورد بدون ما يعجن الأزرار) =====
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // معاينة مباشرة للفقاعة
                    val bubble = RoundedCornerShape(22.dp)
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                        Box(
                            Modifier.widthIn(max = 290.dp)
                                .clip(bubble)
                                .background(Color.White.copy(0.12f))
                                .border(0.8.dp, glassBorderBrush(0.45f), bubble)
                                .padding(horizontal = 18.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text.ifBlank { "هيج راح تبين فوق صورتك" },
                                color = fg.copy(if (text.isBlank()) 0.45f else 1f),
                                fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(Modifier.padding(start = 26.dp, top = 4.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White.copy(0.16f)))
                            Box(Modifier.padding(start = 3.dp, top = 9.dp).size(6.dp).clip(CircleShape).background(Color.White.copy(0.16f)))
                        }
                    }

                    // حقل الكتابة + عدّاد دائري
                    val fieldShape = RoundedCornerShape(22.dp)
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(fieldShape)
                            .background(Color.White.copy(0.10f))
                            .border(
                                1.dp,
                                if (text.isNotEmpty()) SolidColor(accent.copy(0.85f)) else glassBorderBrush(0.40f),
                                fieldShape
                            )
                            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.weight(1f)) {
                            if (text.isEmpty()) Text("شنو تفكر؟", color = fg.copy(0.5f), fontSize = 17.sp)
                            BasicTextField(
                                value = text, onValueChange = { text = it.take(maxLen).replace("\n", " ") },
                                maxLines = 2,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { post() }),
                                textStyle = TextStyle(fontFamily = AppFont, color = fg, fontSize = 17.sp, fontWeight = FontWeight.Medium),
                                cursorBrush = SolidColor(accent),
                                modifier = Modifier.fillMaxWidth().focusRequester(focus)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        val progress by animateFloatAsState(text.length / maxLen.toFloat(), tween(120), label = "statusRing")
                        val ringColor = if (nearLimit) Color(0xFFFF6B5E) else accent
                        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxSize()) {
                                val stroke = 3.dp.toPx()
                                val inset = stroke / 2f
                                val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                                val topLeft = Offset(inset, inset)
                                drawArc(
                                    Color.White.copy(0.15f), 0f, 360f, false, topLeft, arcSize,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
                                )
                                drawArc(
                                    ringColor, -90f, 360f * progress, false, topLeft, arcSize,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )
                                )
                            }
                            if (nearLimit) Text(
                                "${maxLen - text.length}", color = ringColor, fontSize = 11.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // اقتراحات سريعة
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        suggestions.forEach { s ->
                            val picked = text == s
                            val chip = RoundedCornerShape(50)
                            Text(
                                s, color = fg.copy(if (picked) 1f else 0.88f), fontSize = 13.5.sp,
                                fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                modifier = Modifier.clip(chip)
                                    .background(if (picked) accent.copy(0.38f) else Color.White.copy(0.10f))
                                    .border(
                                        0.8.dp,
                                        if (picked) SolidColor(accent.copy(0.9f)) else Brush.verticalGradient(listOf(Color.White.copy(0.40f), Color.White.copy(0.06f))),
                                        chip
                                    )
                                    .clickable { text = s.take(maxLen) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ===== الأزرار (ثابتة بالأسفل، ارتفاعها مضمون) =====
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GlassButton(
                        onClick = { post() }, backdrop = bd, tint = accent, enabled = canPost, pressScale = 1.04f,
                        modifier = Modifier.weight(1f).requiredHeight(52.dp)
                    ) {
                        if (!busy) {
                            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            if (busy) "..." else if (existing != null) "تحديث الحالة" else "نشر",
                            color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    if (existing != null) {
                        GlassButton(
                            onClick = {
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
                            },
                            backdrop = bd, tint = Color(0xFFE5392B), enabled = !busy, pressScale = 1.08f,
                            modifier = Modifier.requiredSize(52.dp)
                        ) {
                            Icon(Icons.Filled.Delete, "حذف", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
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
                Box(Modifier.width(3.dp).fillMaxHeight().background(AccentBlue))
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
                        textStyle = TextStyle(fontFamily = AppFont, color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(AccentBlue),
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
                    backdrop = backdrop, tint = AccentBlue, enabled = text.isNotBlank(),
                    modifier = Modifier.size(52.dp)
                ) { Icon(AppIcons.Reply, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
            }
        }
    }
}
