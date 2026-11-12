package com.chatapp.messenger.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.chatapp.messenger.R

/** لون تطبيق قابل للاختيار */
class AccentOption(val name: String, val color: Color)

/** أيقونة تطبيق قابلة للتبديل. [alias] اسم الـ activity-alias بالـ Manifest */
class AppIconOption(val alias: String, val name: String, val previewRes: Int)

/**
 * إعدادات الثيم المركزية (تنحفظ بالجهاز وتتطبق فوراً على كل التطبيق):
 *  - المظهر: داكن / فاتح (وإذا ما اختار المستخدم شي يتبع النظام)
 *  - لون الأزرار والتطبيق
 *  - إعدادات Liquid Glass: الانكسار، الضبابية، الشفافية
 *  - أيقونة التطبيق
 */
object ThemeSettings {
    private const val PREFS = "theme_settings"
    private var sp: SharedPreferences? = null

    val accents: List<AccentOption> = listOf(
        AccentOption("أزرق سماوي", Color(0xFF1AA7FF)),
        AccentOption("أزرق ملكي", Color(0xFF2F6BFF)),
        AccentOption("نيلي", Color(0xFF5E5CE6)),
        AccentOption("بنفسجي", Color(0xFF8A5CFF)),
        AccentOption("أرجواني", Color(0xFFB44CFF)),
        AccentOption("وردي", Color(0xFFFF2D92)),
        AccentOption("وردي فاتح", Color(0xFFFF6FB5)),
        AccentOption("أحمر", Color(0xFFFF453A)),
        AccentOption("مرجاني", Color(0xFFFF6B5E)),
        AccentOption("برتقالي", Color(0xFFFF9F0A)),
        AccentOption("عنبري", Color(0xFFFFB800)),
        AccentOption("أصفر", Color(0xFFFFD60A)),
        AccentOption("ليموني", Color(0xFFB8E600)),
        AccentOption("أخضر", Color(0xFF30D158)),
        AccentOption("أخضر زمردي", Color(0xFF00C27A)),
        AccentOption("نعناعي", Color(0xFF00D4AA)),
        AccentOption("تركوازي", Color(0xFF00C7BE)),
        AccentOption("سماوي", Color(0xFF00E5FF)),
        AccentOption("أزرق جليدي", Color(0xFF64D2FF)),
        AccentOption("لافندر", Color(0xFFA78BFA)),
        AccentOption("خوخي", Color(0xFFFF8A65)),
        AccentOption("بني", Color(0xFFB07A4F)),
        AccentOption("رمادي فولاذي", Color(0xFF8E9AAF)),
        AccentOption("ذهبي", Color(0xFFE0B040))
    )

    val icons: List<AppIconOption> = listOf(
        AppIconOption("IconDefault", "الأساسية", R.drawable.ic_prev_default),
        AppIconOption("IconBubble", "فقاعة", R.drawable.ic_prev_bubble),
        AppIconOption("IconPlane", "طيارة", R.drawable.ic_prev_plane),
        AppIconOption("IconChecks", "تم الاستلام", R.drawable.ic_prev_checks),
        AppIconOption("IconDuo", "محادثة", R.drawable.ic_prev_duo),
        AppIconOption("IconRing", "حلقة", R.drawable.ic_prev_ring)
    )

    const val DEFAULT_GLASS = 0.5f

    /** 0 = يتبع النظام، 1 = داكن، 2 = فاتح */
    var mode by mutableIntStateOf(0)
        private set
    var accentIndex by mutableIntStateOf(0)
        private set
    /** لون مخصّص من لوحة "كل الألوان" (ARGB). null = نستعمل اللون الجاهز حسب accentIndex */
    var customAccent by mutableStateOf<Int?>(null)
        private set
    var refraction by mutableFloatStateOf(DEFAULT_GLASS)
        private set
    var blur by mutableFloatStateOf(DEFAULT_GLASS)
        private set
    /** 0..1 : كل ما زادت صار الزجاج أشفّ */
    var transparency by mutableFloatStateOf(DEFAULT_GLASS)
        private set
    var iconIndex by mutableIntStateOf(0)
        private set
    /** true = المستخدم الغى شاشة السبلاش (ما تظهر عند فتح التطبيق) */
    var skipSplash by mutableStateOf(false)
        private set

    val accent: Color get() = customAccent?.let { Color(it) } ?: accents[accentIndex.coerceIn(0, accents.lastIndex)].color

    // المعاملات الفعلية (1.0 = الشكل الأصلي للتطبيق)
    val refractK: Float get() = refraction * 2f
    val blurK: Float get() = blur * 2f
    val surfaceK: Float get() = (1f - transparency) * 2f

    fun init(ctx: Context) {
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp = p
        mode = p.getInt("mode", 0)
        accentIndex = p.getInt("accent", 0).coerceIn(0, accents.lastIndex)
        customAccent = if (p.contains("accent_custom")) p.getInt("accent_custom", 0) else null
        refraction = p.getFloat("refraction", DEFAULT_GLASS)
        blur = p.getFloat("blur", DEFAULT_GLASS)
        transparency = p.getFloat("transparency", DEFAULT_GLASS)
        iconIndex = p.getInt("icon", 0).coerceIn(0, icons.lastIndex)
        skipSplash = p.getBoolean("skip_splash", false)
    }

    fun applySkipSplash(v: Boolean) { skipSplash = v; sp?.edit()?.putBoolean("skip_splash", v)?.apply() }

    fun applyMode(v: Int) { mode = v; sp?.edit()?.putInt("mode", v)?.apply() }
    fun setAccent(i: Int) {
        accentIndex = i; customAccent = null
        sp?.edit()?.putInt("accent", i)?.remove("accent_custom")?.apply()
    }
    /** لون حر من لوحة كل الألوان (يتحفظ ويتطبق على كل التطبيق) */
    fun setCustomAccent(argb: Int) {
        val c = argb or (0xFF shl 24)
        customAccent = c
        sp?.edit()?.putInt("accent_custom", c)?.apply()
    }
    // السلايدر يرسل قيمة كل فريم: الحفظ بالقرص يتأجل لين يوقف السحب (القيمة الحية تتحدث فوراً)
    private val glassHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val persistGlass = Runnable {
        sp?.edit()?.putFloat("refraction", refraction)?.putFloat("blur", blur)?.putFloat("transparency", transparency)?.apply()
    }
    private fun persistGlassLater() {
        glassHandler.removeCallbacks(persistGlass)
        glassHandler.postDelayed(persistGlass, 350L)
    }
    fun applyRefraction(v: Float) { refraction = v.coerceIn(0f, 1f); persistGlassLater() }
    fun applyBlur(v: Float) { blur = v.coerceIn(0f, 1f); persistGlassLater() }
    fun applyTransparency(v: Float) { transparency = v.coerceIn(0f, 1f); persistGlassLater() }

    fun resetGlass() {
        applyRefraction(DEFAULT_GLASS); applyBlur(DEFAULT_GLASS); applyTransparency(DEFAULT_GLASS)
    }

    /** يبدّل أيقونة التطبيق: يفعّل الـ alias الجديد أولاً ثم يعطّل الباقي */
    fun setIcon(ctx: Context, index: Int) {
        val i = index.coerceIn(0, icons.lastIndex)
        val pm = ctx.packageManager
        fun cn(alias: String) = ComponentName(ctx.packageName, "com.chatapp.messenger.$alias")
        pm.setComponentEnabledSetting(cn(icons[i].alias), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        icons.forEachIndexed { k, o ->
            if (k != i) pm.setComponentEnabledSetting(cn(o.alias), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
        iconIndex = i
        sp?.edit()?.putInt("icon", i)?.apply()
    }

    @Composable
    fun isDark(): Boolean {
        val sys = isSystemInDarkTheme()
        return when (mode) { 1 -> true; 2 -> false; else -> sys }
    }
}

/** بديل isSystemInDarkTheme(): يحترم اختيار المستخدم (داكن/فاتح) */
@Composable
fun isAppDark(): Boolean = ThemeSettings.isDark()
