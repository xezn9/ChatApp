package com.chatapp.messenger.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.ui.glass.GlassButton
import com.chatapp.messenger.ui.glass.LiquidToggle
import com.chatapp.messenger.ui.theme.appBackground
import com.chatapp.messenger.ui.theme.isAppDark
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

// ---------------------------------------------------------------------------
//  خلفيات الواجهات الرئيسية (Home / Groups / Account): 10 جاهزة لكل واجهة + صورة/GIF من المعرض + ضباب
// ---------------------------------------------------------------------------

private fun pr(id: String, name: String, top: Long, bottom: Long, a: Long, b: Long, flip: Boolean = false) = ChatBgPreset(
    id, name, Color(top), Color(bottom),
    listOf(
        Triple(Offset(if (flip) 0.15f else 0.85f, 0.18f), 0.50f, Color(a).copy(alpha = 0.26f)),
        Triple(Offset(if (flip) 0.85f else 0.12f, 0.78f), 0.55f, Color(b).copy(alpha = 0.22f))
    )
)

/** 10 خلفيات لكل واجهة (بالنسخة الداكنة؛ بالثيم الفاتح تتحول تلقائياً لدرجات فاتحة حتى تبقى النصوص مقروءة) */
val AppBgPresets: List<List<ChatBgPreset>> = listOf(
    // Home: درجات زرقاء وباردة
    listOf(
        pr("h1", "ليل", 0xFF0B1026, 0xFF1B2A5A, 0xFF4B6BFF, 0xFF7A5CFF),
        pr("h2", "محيط", 0xFF041C32, 0xFF0F6C8C, 0xFF29B6F6, 0xFF00E5FF, true),
        pr("h3", "جليد", 0xFF0E2A47, 0xFF3F7CAC, 0xFF9AD9FF, 0xFF5EA8FF),
        pr("h4", "فجر", 0xFF14183E, 0xFF5B5BD6, 0xFFB39DFF, 0xFF63D2FF, true),
        pr("h5", "ملكي", 0xFF0A1B5C, 0xFF2F6BFF, 0xFF6FA0FF, 0xFF1AA7FF),
        pr("h6", "سماء", 0xFF0C2E5E, 0xFF4F9CE0, 0xFFBFE3FF, 0xFF7FC4FF, true),
        pr("h7", "بحيرة", 0xFF062B34, 0xFF1C7C88, 0xFF4DE0D0, 0xFF2AA9C9),
        pr("h8", "نيلي", 0xFF160F3A, 0xFF4A3FB0, 0xFF8A7DFF, 0xFF5E5CE6, true),
        pr("h9", "ضباب", 0xFF1A2230, 0xFF4B5C78, 0xFFA9B8D0, 0xFF7E93B5),
        pr("h10", "فيروز", 0xFF05303A, 0xFF11A5A0, 0xFF6BF2E3, 0xFF3ED0FF, true)
    ),
    // Groups: درجات دافئة وزاهية
    listOf(
        pr("g1", "غروب", 0xFF3B1240, 0xFFC2476A, 0xFFFFB347, 0xFFFF6A88),
        pr("g2", "وردي", 0xFF4A0F33, 0xFFE0408F, 0xFFFF8DC7, 0xFFFFB3D9, true),
        pr("g3", "مرجان", 0xFF4A1410, 0xFFE0634F, 0xFFFFA48F, 0xFFFFC857),
        pr("g4", "عنبر", 0xFF3D2204, 0xFFD9902B, 0xFFFFD36B, 0xFFFF9F0A, true),
        pr("g5", "توت", 0xFF2A0B3D, 0xFF8E2DA8, 0xFFE07BFF, 0xFFFF6FB5),
        pr("g6", "خوخ", 0xFF4A1E1A, 0xFFF08A6B, 0xFFFFD1B3, 0xFFFFA8A0, true),
        pr("g7", "ليلكي", 0xFF1E0B3A, 0xFF6A2FB0, 0xFFD16BFF, 0xFF8A5CFF),
        pr("g8", "شفق", 0xFF2B0F4A, 0xFFD6527C, 0xFFFFB86B, 0xFF9A6BFF, true),
        pr("g9", "كرز", 0xFF3A0A14, 0xFFB5173D, 0xFFFF5C7A, 0xFFFF8A5C),
        pr("g10", "ذهب", 0xFF33270A, 0xFFB38A1C, 0xFFFFE08A, 0xFFE0B040, true)
    ),
    // Account: أخضر ونعناعي وهادئ
    listOf(
        pr("a1", "غابة", 0xFF06241B, 0xFF14614A, 0xFF3DDC97, 0xFF1FA85A),
        pr("a2", "نعناع", 0xFF05302A, 0xFF1BA384, 0xFF7CF2D2, 0xFF00D4AA, true),
        pr("a3", "زمرد", 0xFF04281A, 0xFF0B8A55, 0xFF30D158, 0xFF00C27A),
        pr("a4", "واحة", 0xFF0A2E2A, 0xFF4FA36B, 0xFFC4F29A, 0xFF2EE6A8, true),
        pr("a5", "ربيع", 0xFF12301A, 0xFF69B34A, 0xFFD4F76B, 0xFF8BE0A0),
        pr("a6", "تركواز", 0xFF042B33, 0xFF0F9BA8, 0xFF64E6F0, 0xFF1AA7FF, true),
        pr("a7", "ليمون", 0xFF2A3308, 0xFF9DB81F, 0xFFE6F56B, 0xFFB8E600),
        pr("a8", "صحراء", 0xFF3A2A14, 0xFFB07A4F, 0xFFFFD8A8, 0xFFE0B040, true),
        pr("a9", "بنفسجي", 0xFF1A0F38, 0xFF5A3FA8, 0xFFB39DFF, 0xFF2EE6A8),
        pr("a10", "رمادي", 0xFF14181F, 0xFF4A5666, 0xFF9AA8BD, 0xFF6F8099, true)
    )
)

/** النسخة المناسبة للثيم: بالفاتح الألوان الأساسية تنزاح للأبيض حتى ما تضيع النصوص الداكنة */
fun ChatBgPreset.forTheme(dark: Boolean): ChatBgPreset =
    if (dark) this else ChatBgPreset(id, name, lerp(top, Color.White, 0.72f), lerp(bottom, Color.White, 0.55f), circles)

/** إعدادات خلفية كل واجهة (تنحفظ بالجهاز وتتطبق فوراً). tab: 0 Home، 1 Groups، 2 Account */
object AppBgSettings {
    private const val PREFS = "app_tab_bg"
    private var sp: SharedPreferences? = null

    private val kinds = mutableStateListOf("default", "default", "default")   // default | preset | image
    private val presets = mutableStateListOf(0, 0, 0)
    private val blurs = mutableStateListOf(false, false, false)
    private val versions = mutableStateListOf(0L, 0L, 0L)

    fun kind(tab: Int): String = kinds[tab.coerceIn(0, 2)]
    fun preset(tab: Int): Int = presets[tab.coerceIn(0, 2)]
    fun blur(tab: Int): Boolean = blurs[tab.coerceIn(0, 2)]
    fun version(tab: Int): Long = versions[tab.coerceIn(0, 2)]

    fun imageFile(ctx: Context, tab: Int): File = File(ctx.applicationContext.filesDir, "appbg_${tab.coerceIn(0, 2)}.jpg")

    fun init(ctx: Context) {
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp = p
        for (t in 0..2) {
            var k = p.getString("kind_$t", "default") ?: "default"
            // صورة محفوظة بس الملف انحذف: نرجع للافتراضي
            if (k == "image" && !imageFile(ctx, t).exists()) k = "default"
            kinds[t] = k
            presets[t] = p.getInt("preset_$t", 0).coerceIn(0, AppBgPresets[t].lastIndex)
            blurs[t] = p.getBoolean("blur_$t", false)
            versions[t] = p.getLong("ver_$t", 0L)
        }
    }

    private fun save(t: Int) {
        sp?.edit()
            ?.putString("kind_$t", kinds[t])
            ?.putInt("preset_$t", presets[t])
            ?.putBoolean("blur_$t", blurs[t])
            ?.putLong("ver_$t", versions[t])
            ?.apply()
    }

    fun setDefault(tab: Int) { val t = tab.coerceIn(0, 2); kinds[t] = "default"; save(t) }
    fun setPreset(tab: Int, index: Int) {
        val t = tab.coerceIn(0, 2)
        presets[t] = index.coerceIn(0, AppBgPresets[t].lastIndex); kinds[t] = "preset"; save(t)
    }
    fun setBlur(tab: Int, on: Boolean) { val t = tab.coerceIn(0, 2); blurs[t] = on; save(t) }

    /** يحفظ صورة (أو GIF متحرك كما هو) من المعرض كخلفية للواجهة. يرجع نص الخطأ أو null إذا نجح */
    suspend fun setImage(ctx: Context, tab: Int, uri: Uri): String? {
        val t = tab.coerceIn(0, 2)
        val err = saveChatBackgroundFromUri(ctx, uri, imageFile(ctx, t))
        if (err != null) return err
        versions[t] = System.currentTimeMillis()
        kinds[t] = "image"
        save(t)
        return null
    }
}

/** تحميل صورة الخلفية الثابتة من الملف (softBlur: تمويه تقريبي للأجهزة الأقدم من أندرويد 12) */
@Composable
private fun rememberTabBgImage(path: String, version: Long, softBlur: Boolean): ImageBitmap? {
    val img by produceState<ImageBitmap?>(null, path, version, softBlur) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                var bmp = BitmapFactory.decodeFile(path) ?: return@runCatching null
                if (softBlur) {
                    bmp = Bitmap.createScaledBitmap(bmp, max(1, bmp.width / 14), max(1, bmp.height / 14), true)
                }
                bmp.asImageBitmap()
            }.getOrNull()
        }
    }
    return img
}

/**
 * خلفية واجهة رئيسية حسب اختيار المستخدم (افتراضية / جاهزة / صورة أو GIF) + ضباب اختياري.
 * توضع داخل layerBackdrop حتى يشتغل الزجاج فوقها.
 */
@Composable
fun AppTabBackground(tab: Int, modifier: Modifier = Modifier) {
    val t = tab.coerceIn(0, 2)
    val dark = isAppDark()
    val kind = AppBgSettings.kind(t)
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val blurOn = AppBgSettings.blur(t)
    val blurMod = if (blurOn && canBlur) Modifier.blur(18.dp, BlurredEdgeTreatment.Unbounded) else Modifier
    val scrim = if (dark) Color.Black.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.30f)

    Box(modifier.clipToBounds()) {
        when (kind) {
            "preset" -> {
                val p = AppBgPresets[t][AppBgSettings.preset(t)].forTheme(dark)
                Box(Modifier.fillMaxSize().then(blurMod).presetBackground(p))
            }
            "image" -> {
                val ctx = LocalContext.current
                val file = remember(t) { AppBgSettings.imageFile(ctx, t) }
                val ver = AppBgSettings.version(t)
                val animated by produceState<Boolean?>(null, file.path, ver) {
                    value = withContext(Dispatchers.IO) { GifUtil.isGifFile(file) }
                }
                val img = if (animated == false) rememberTabBgImage(file.path, ver, softBlur = blurOn && !canBlur) else null
                if (animated == true) {
                    // GIF يتكرر للأبد، والتمويه الحقيقي بأندرويد 12+ فقط
                    AnimatedGifImage(file, Modifier.fillMaxSize().then(blurMod), ContentScale.Crop, key = ver)
                    Box(Modifier.fillMaxSize().background(scrim))
                } else if (img != null) {
                    Image(
                        img, null,
                        modifier = Modifier.fillMaxSize().then(blurMod),
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.Medium
                    )
                    Box(Modifier.fillMaxSize().background(scrim))
                } else {
                    Box(Modifier.fillMaxSize().background(appBackground()))
                }
            }
            else -> AnimatedAppBackground(t, Modifier.fillMaxSize().then(blurMod))
        }
    }
}

// ---------------------------------------------------------------------------
//  قسم الإعدادات: داخل صفحة "ثيم التطبيق"
// ---------------------------------------------------------------------------

private val TabNames = listOf("Home", "Groups", "Account")

@Composable
internal fun AppBackgroundSection(bd: Backdrop, dark: Boolean, accent: Color, fg: Color) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var sel by remember { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val err = AppBgSettings.setImage(ctx, sel, uri)
            if (err != null) AppToast.show(err)
        }
    }
    val pill = RoundedCornerShape(50)
    val cardShape = RoundedCornerShape(30.dp)

    Column(Modifier.fillMaxWidth()) {
        // اختيار الواجهة
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabNames.forEachIndexed { i, name ->
                val on = sel == i
                Box(
                    Modifier.weight(1f).height(42.dp).clip(pill)
                        .then(if (on) Modifier.background(accent) else Modifier.liquidCard(bd, pill, dark))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { sel = i },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = if (on) Color.White else fg.copy(0.85f)
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // معاينة حية
        val prevShape = RoundedCornerShape(26.dp)
        Box(
            Modifier.fillMaxWidth().height(170.dp).clip(prevShape)
                .border(1.dp, Color.White.copy(0.3f), prevShape)
        ) {
            AppTabBackground(sel, Modifier.fillMaxSize())
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                    .fillMaxWidth(0.55f).height(28.dp).clip(pill).background(Color.White.copy(0.25f))
            )
        }
        Spacer(Modifier.height(14.dp))

        Column(Modifier.fillMaxWidth().liquidCard(bd, cardShape, dark).padding(16.dp)) {
            Text("الخلفيات الجاهزة", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))

            // الافتراضية + 10 جاهزة
            val kind = AppBgSettings.kind(sel)
            val items: List<Int> = (-1..AppBgPresets[sel].lastIndex).toList()
            items.chunked(4).forEachIndexed { r, row ->
                if (r > 0) Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { idx ->
                        val selected = if (idx < 0) kind == "default" else kind == "preset" && AppBgSettings.preset(sel) == idx
                        val shape = RoundedCornerShape(16.dp)
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.fillMaxWidth().height(74.dp).clip(shape)
                                    .then(
                                        if (idx < 0) Modifier.background(appBackground())
                                        else Modifier.presetBackground(AppBgPresets[sel][idx].forTheme(dark))
                                    )
                                    .border(
                                        if (selected) 2.5.dp else 1.dp,
                                        if (selected) accent else Color.White.copy(0.35f), shape
                                    )
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                        if (idx < 0) AppBgSettings.setDefault(sel) else AppBgSettings.setPreset(sel, idx)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) Icon(
                                    Icons.Filled.Check, null,
                                    tint = if (idx < 0) accent else Color.White, modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                if (idx < 0) "افتراضية" else AppBgPresets[sel][idx].name,
                                fontSize = 11.sp, color = fg.copy(0.75f), maxLines = 1,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    // نكمل الصف الأخير بفراغات حتى يبقى حجم المربعات موحد
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            Spacer(Modifier.height(16.dp))
            GlassButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                backdrop = bd, tint = accent,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(AppIcons.Image, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("اختيار صورة أو GIF من المعرض", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            if (kind == "image") {
                Text(
                    "الخلفية الحالية: صورتك", fontSize = 12.sp, color = fg.copy(0.6f), textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("خلفية ضبابية", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "تمويه الخلفية حتى يبرز الزجاج والرسائل"
                        else "على أندرويد أقدم من 12 يشتغل تمويه تقريبي للصور الثابتة فقط",
                        fontSize = 12.sp, color = fg.copy(0.62f)
                    )
                }
                // key: عند تبديل الواجهة يتبني المفتاح من جديد بحالته الصحيحة
                androidx.compose.runtime.key(sel) {
                    LiquidToggle(
                        selected = { AppBgSettings.blur(sel) },
                        onSelect = { AppBgSettings.setBlur(sel, it) },
                        backdrop = bd, dark = dark, accent = accent
                    )
                }
            }
        }
    }
}
