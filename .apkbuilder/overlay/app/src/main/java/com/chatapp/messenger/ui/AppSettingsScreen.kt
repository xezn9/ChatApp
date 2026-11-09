package com.chatapp.messenger.ui

import com.chatapp.messenger.ui.theme.AppFont

import com.chatapp.messenger.ui.theme.GlassFx
import com.chatapp.messenger.ui.theme.glassSurfaceLayer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import com.chatapp.messenger.ui.theme.isAppDark
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.chatapp.messenger.Profile
import com.chatapp.messenger.ui.glass.GlassButton
import com.chatapp.messenger.ui.theme.appBackground
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

// ============================================================================
//  إعدادات التطبيق: واجهة بملء الشاشة (بدون هوامش) تنبعث من زر القائمة بدائرة تكبر
// ============================================================================

internal val SetViolet = Color(0xFF8A5CFF)
internal val SetMint = Color(0xFF00D4AA)
internal val SetPink = Color(0xFFFF2D92)
internal val SetOrange = Color(0xFFFF9F0A)
internal val SetGreen = Color(0xFF30D158)
internal val SetSky = Color(0xFF64D2FF)
internal val SetIndigo = Color(0xFF5E5CE6)

private fun vec(name: String, path: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = PathParser().parsePathString(path).toNodes(),
        pathFillType = PathFillType.NonZero,
        fill = SolidColor(Color.Black)
    ).build()

private val PaletteIcon: ImageVector by lazy {
    vec(
        "Palette",
        "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9c0.83,0 1.5,-0.67 1.5,-1.5 0,-0.39 -0.15,-0.74 -0.39,-1.01 -0.23,-0.26 -0.38,-0.61 -0.38,-0.99 0,-0.83 0.67,-1.5 1.5,-1.5L16,16c2.76,0 5,-2.24 5,-5 0,-4.42 -4.03,-8 -9,-8zM6.5,12c-0.83,0 -1.5,-0.67 -1.5,-1.5S5.67,9 6.5,9 8,9.67 8,10.5 7.33,12 6.5,12zM9.5,8C8.67,8 8,7.33 8,6.5S8.67,5 9.5,5s1.5,0.67 1.5,1.5S10.33,8 9.5,8zM14.5,8c-0.83,0 -1.5,-0.67 -1.5,-1.5S13.67,5 14.5,5s1.5,0.67 1.5,1.5S15.33,8 14.5,8zM17.5,12c-0.83,0 -1.5,-0.67 -1.5,-1.5S16.67,9 17.5,9s1.5,0.67 1.5,1.5S18.33,12 17.5,12z"
    )
}

private val CodeIcon: ImageVector by lazy {
    vec("Code", "M9.4,16.6L4.8,12l4.6,-4.6L8,6l-6,6 6,6 1.4,-1.4zM14.6,16.6l4.6,-4.6 -4.6,-4.6L16,6l6,6 -6,6 -1.4,-1.4z")
}

/** سهم صغير يشير لليسار (اتجاه "التالي" بالعربي) */
private val ChevronLeft: ImageVector by lazy {
    vec("ChevronLeft", "M15.41,16.59L10.83,12l4.58,-4.59L14,6l-6,6 6,6z")
}

private class AppSettingsSection(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val c1: Color,
    val c2: Color
)

private val settingsSections: List<AppSettingsSection> get() = run {
    listOf(
        AppSettingsSection("الحساب", "الاسم واليوزر والبريد وكلمة السر", Icons.Filled.Person, AccentBlue, Color(0xFF5B8CFF)),
        AppSettingsSection("الخصوصية", "حالة الاتصال والمقروء وطلبات الرسائل", AppIcons.Lock, SetGreen, SetMint),
        AppSettingsSection("ثيم التطبيق", "الألوان والمظهر الفاتح والغامق", PaletteIcon, SetViolet, SetPink),
        AppSettingsSection("الإشعارات", "فحص وصول الإشعارات وإصلاح المشاكل", AppIcons.Bell, SetOrange, SetPink),
        AppSettingsSection("حول التطبيق", "النسخة والمعلومات والدعم", Icons.Filled.Info, SetSky, SetIndigo),
        AppSettingsSection("مطور التطبيق", "معلومات المطوّر وطرق التواصل", CodeIcon, Color(0xFF00E5FF), Color(0xFF3D5AFE))
    )
}

// ---------------------------------------------------------------------------
//  حركة الانبعاث: دائرة تكبر من الزر لين تغطي الشاشة كلها (وترجع تصغر له عند الإغلاق)
// ---------------------------------------------------------------------------

/** شكل يتحول من حجم الزر (دائرة) لشاشة كاملة، وحوافه تذوب بنعومة مثل قطرة سائلة تتمدد */
private class MorphShape(val from: Rect, val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val q = progress
        val l = lerp(from.left, 0f, q)
        val t = lerp(from.top, 0f, q)
        val r = lerp(from.right, size.width, q)
        val b = lerp(from.bottom, size.height, q)
        val w = (r - l).coerceAtLeast(1f)
        val h = (b - t).coerceAtLeast(1f)
        val bump = with(density) { 56.dp.toPx() }
        val q1 = q.coerceIn(0f, 1f)
        val rad = (from.height / 2f * (1f - q1) + bump * sin(PI.toFloat() * q1)).coerceIn(0f, minOf(w, h) / 2f)
        return Outline.Rounded(RoundRect(Rect(l, t, l + w, t + h), CornerRadius(rad)))
    }
}

/**
 * انبعاث سائل: الشاشة تتمدد من نفس مكان الزر (من حجمه وشكله الدائري) لين تغطي الشاشة كلها
 * بنابض ناعم فيه ارتداد خفيف، وترجع تنكمش للزر عند الإغلاق. المحتوى يظهر بعد ما تنفتح الشاشة.
 */
@Composable
internal fun LiquidRevealOverlay(
    visible: Boolean,
    origin: Rect?,
    content: @Composable () -> Unit
) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) p.animateTo(1f, spring(dampingRatio = 0.76f, stiffness = 170f, visibilityThreshold = 0.002f))
        else p.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 260f, visibilityThreshold = 0.002f))
    }
    if (!visible && p.value <= 0.002f) return

    var topLeft by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { topLeft = it.positionInRoot() }) {
        Box(
            Modifier.matchParentSize().graphicsLayer {
                val from = origin?.translate(-topLeft.x, -topLeft.y)
                    ?: Rect(Offset(size.width * 0.08f, size.height * 0.05f), 48.dp.toPx())
                shape = MorphShape(from, p.value)
                clip = true
            }
        ) {
            Box(Modifier.matchParentSize().graphicsLayer { alpha = (p.value * 4f).coerceIn(0f, 1f) }) { content() }
        }
    }
}

// ---------------------------------------------------------------------------
//  الخلفية: بقع ألوان تسبح + كرات زجاجية تتحرك وتكسر الضوء (حتى يبين الـ liquid glass)
// ---------------------------------------------------------------------------

@Composable
private fun AppSettingsScene(content: @Composable (Backdrop) -> Unit) {
    val bd = rememberLayerBackdrop()
    val t = rememberInfiniteTransition(label = "settingsScene")
    val phase by t.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart),
        label = "settingsPhase"
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }

        // خلفية "شفق غروب": سماء نيلية عميقة تتدرج لبنفسجي ثم وهج دافئ بالأسفل، وبقع شفق تسبح فوقها
        val dark = isAppDark()
        val sky = if (dark) listOf(Color(0xFF0B1030), Color(0xFF241046), Color(0xFF3A1550))
        else listOf(Color(0xFFDDE6FF), Color(0xFFF1DCFF), Color(0xFFFFE3D6))
        Box(Modifier.matchParentSize().layerBackdrop(bd).background(Brush.verticalGradient(sky))) {
            Canvas(Modifier.matchParentSize()) {
                val a = phase
                fun blob(c: Color, cx: Float, cy: Float, r: Float, alpha: Float) {
                    drawCircle(
                        Brush.radialGradient(listOf(c.copy(alpha = alpha), Color.Transparent), center = Offset(cx, cy), radius = r),
                        r, Offset(cx, cy)
                    )
                }
                blob(Color(0xFF4D7CFF), w * (0.18f + 0.14f * sin(a)), h * (0.12f + 0.05f * cos(a * 2)), w * 0.75f, 0.45f)
                blob(Color(0xFFB45CFF), w * (0.88f + 0.10f * cos(a)), h * (0.38f + 0.07f * sin(a)), w * 0.70f, 0.45f)
                blob(Color(0xFFFF5C8A), w * (0.12f + 0.12f * cos(a * 2)), h * (0.66f + 0.06f * sin(a * 3)), w * 0.60f, 0.38f)
                blob(Color(0xFFFFA24D), w * (0.65f + 0.14f * sin(a * 2)), h * (0.95f + 0.04f * cos(a)), w * 0.80f, 0.45f)
            }
        }

        // كرات زجاجية عايمة
        @Composable
        fun Orb(sizeDp: Int, bx: Float, by: Float, ax: Float, ay: Float, k: Int, ph: Float) {
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (w * bx + w * ax * sin(phase * k + ph)).roundToInt(),
                            (h * by + h * ay * cos(phase * k + ph)).roundToInt()
                        )
                    }
                    .size(sizeDp.dp)
                    .drawBackdrop(
                        backdrop = bd,
                        shape = { CircleShape },
                        effects = {
                            vibrancy()
                            blur((1.5f.dp.toPx()) * GlassFx.blurK)
                            lens((sizeDp.dp.toPx() * 0.22f) * GlassFx.refractK, (sizeDp.dp.toPx() * 0.45f) * GlassFx.refractK)
                        },
                        onDrawSurface = { glassSurfaceLayer { drawRect(Color.White.copy(alpha = 0.05f)) } }
                    )
                    .border(0.8.dp, glassBorderBrush(0.35f), CircleShape)
            )
        }
        Orb(120, 0.58f, 0.10f, 0.06f, 0.02f, 1, 0.0f)
        Orb(70, 0.06f, 0.40f, 0.04f, 0.03f, 2, 1.7f)
        Orb(160, 0.50f, 0.74f, 0.08f, 0.03f, 1, 3.4f)
        Orb(54, 0.82f, 0.62f, 0.05f, 0.04f, 3, 5.1f)

        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            content(bd)
        }
    }
}

// ---------------------------------------------------------------------------
//  الشاشة
// ---------------------------------------------------------------------------

@Composable
internal fun AppSettingsScreen(profile: Profile, onDismiss: () -> Unit, onLogout: () -> Unit = {}) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        var selected by remember { mutableStateOf<AppSettingsSection?>(null) }
        var last by remember { mutableStateOf(settingsSections[0]) }
        // صفحة "ثيم التطبيق" تتمدد من مكان الصف لين تغطي الشاشة كلها
        var themeOpen by remember { mutableStateOf(false) }
        var themeOrigin by remember { mutableStateOf<Rect?>(null) }
        // محادثة الدعم (من زر "الدعم والشكاوي" بصفحة حول التطبيق)
        var supportOpen by remember { mutableStateOf(false) }
        BackHandler(enabled = !supportOpen) {
            if (themeOpen) themeOpen = false
            else if (selected != null) selected = null
            else onDismiss()
        }
        val myUidForSupport = remember { com.chatapp.messenger.Repo.auth.currentUser?.uid ?: "" }

        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
            AppSettingsScene { bd ->
                val shift by animateFloatAsState(
                    if (selected != null) 1f else 0f, spring(dampingRatio = 0.9f, stiffness = 260f), label = "homeShift"
                )
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        alpha = 1f - 0.7f * shift
                        val s = 1f - 0.06f * shift
                        scaleX = s; scaleY = s
                        translationX = shift * size.width * 0.12f
                    }
                ) {
                    AppSettingsHome(
                        profile = profile, bd = bd, onBack = onDismiss, onLogout = onLogout,
                        onOpen = { sec, rect ->
                            if (sec.title == "ثيم التطبيق") { themeOrigin = rect; themeOpen = true }
                            else if (sec.title == "الإشعارات") { NotifHealthUi.show = true }
                            else { last = sec; selected = sec }
                        }
                    )
                }
                AnimatedVisibility(
                    visible = selected != null,
                    enter = slideInHorizontally(spring(dampingRatio = 0.86f, stiffness = 320f)) { -it } + fadeIn(tween(200)),
                    exit = slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(220))
                ) {
                    if (last.title == "الحساب") AccountSettingsPage(onBack = { selected = null })
                    else if (last.title == "الخصوصية") PrivacySettingsPage(onBack = { selected = null })
                    else if (last.title == "مطور التطبيق") DeveloperPage(onBack = { selected = null })
                    else if (last.title == "حول التطبيق") AboutAppPage(parentBd = bd, onBack = { selected = null }, onOpenSupport = { supportOpen = true })
                    else AppSettingsSectionPage(section = last, bd = bd, onBack = { selected = null })
                }
                LiquidRevealOverlay(visible = themeOpen, origin = themeOrigin) {
                    ThemeSettingsPage(onBack = { themeOpen = false })
                }
                AnimatedVisibility(
                    visible = supportOpen,
                    enter = slideInHorizontally(spring(dampingRatio = 0.86f, stiffness = 320f)) { -it } + fadeIn(tween(200)),
                    exit = slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(200))
                ) {
                    SupportChatScreen(threadUid = myUidForSupport, asDev = false, onBack = { supportOpen = false })
                }
            }
        }
    }
}

/** نفس StaggerIn بس يبدي بعد ما تنفتح الشاشة */
@Composable
private fun SetIn(index: Int, modifier: Modifier = Modifier, step: Long = 55L, content: @Composable () -> Unit) {
    StaggerIn(index, modifier, step = step, startDelay = 240L, content = content)
}

/** زر رجوع Liquid Glass: عدسة قوية، لمعة، حافة ضوئية، ويستطيل ويتحرك السهم عند الضغط */
@Composable
internal fun LiquidBackButton(bd: Backdrop, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 1.18f else 1f, spring(0.42f, 380f), label = "backScale")
    val nudge by animateFloatAsState(if (pressed) 7f else 0f, spring(0.5f, 420f), label = "backNudge")
    Box(
        Modifier.size(54.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(AccentBlue.copy(alpha = 0.40f), Color.Transparent), radius = size.width * 0.95f),
                    radius = size.width * 0.95f
                )
            }
            .drawBackdrop(
                backdrop = bd,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    blur((3f.dp.toPx()) * GlassFx.blurK)
                    lens((18f.dp.toPx()) * GlassFx.refractK, (36f.dp.toPx()) * GlassFx.refractK)
                },
                onDrawSurface = { glassSurfaceLayer {
                    drawRect(Color.White.copy(alpha = 0.10f))
                    drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.30f), 0.5f to Color.Transparent))
                } }
            )
            .border(
                1.2.dp,
                Brush.verticalGradient(listOf(Color.White.copy(0.80f), Color.White.copy(0.06f), AccentBlue.copy(0.45f))),
                CircleShape
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { onClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White,
            modifier = Modifier.size(26.dp).graphicsLayer { translationX = nudge.dp.toPx() }
        )
    }
}

@Composable
internal fun AppSettingsHeader(title: String, bd: Backdrop, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        LiquidBackButton(bd, onBack)
        Spacer(Modifier.width(14.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AppSettingsHome(
    profile: Profile, bd: Backdrop, onBack: () -> Unit, onLogout: () -> Unit, onOpen: (AppSettingsSection, Rect?) -> Unit
) {
    val dark = isAppDark()
    val fg = MaterialTheme.colorScheme.onBackground
    // كل شي (حتى زر الرجوع) يتمرر وياه بملء الشاشة، بدون قص عند شريط الحالة
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
    ) {
        SetIn(0, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth()) { LiquidBackButton(bd, onBack) }
        }
        SetIn(1, Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "الإعدادات",
                    style = TextStyle(fontFamily = AppFont, brush = Brush.linearGradient(listOf(AccentBlue, SetViolet, SetMint)),
                        fontSize = 38.sp, fontWeight = FontWeight.ExtraBold
                    )
                )
                Text("خصّص التطبيق على كيفك", fontSize = 15.sp, color = fg.copy(alpha = 0.65f), modifier = Modifier.padding(top = 2.dp))
            }
        }
        Spacer(Modifier.height(22.dp))

        // بطاقة الحساب
        SetIn(2, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            val avatar = rememberB64Image(profile.avatar)
            val shape = RoundedCornerShape(32.dp)
            Row(
                Modifier.fillMaxWidth().liquidCard(bd, shape, dark).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(68.dp)
                        .border(2.5.dp, Brush.sweepGradient(listOf(AccentBlue, SetViolet, SetPink, SetMint, AccentBlue)), CircleShape)
                        .padding(5.dp).clip(CircleShape)
                        .background(Color.White.copy(0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatar != null) {
                        Image(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Filled.Person, null, tint = fg.copy(0.7f), modifier = Modifier.size(32.dp))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(profile.name.ifBlank { "مستخدم" }, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        BadgeAfterName(profile, 20.dp)
                    }
                    if (profile.username.isNotBlank()) {
                        Text("@${profile.username}", fontSize = 14.sp, color = fg.copy(0.6f), maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))

        settingsSections.forEachIndexed { i, s ->
            SetIn(3 + i, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), step = 70L) {
                AppSettingsRow(s, bd, dark) { rect -> onOpen(s, rect) }
            }
        }

        // تسجيل الخروج: تحت مطور التطبيق (يفتح نافذة التأكيد)
        SetIn(3 + settingsSections.size, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), step = 70L) {
            AppSettingsRow(
                AppSettingsSection("تسجيل الخروج", "الخروج من حسابك على هذا الجهاز", AppIcons.Logout, Color(0xFFFF453A), Color(0xFFFF2D6F)),
                bd, dark
            ) { onLogout() }
        }

        Spacer(Modifier.height(28.dp))
        SetIn(4 + settingsSections.size, Modifier.fillMaxWidth()) {
            Text(
                "ChatApp", fontSize = 13.sp, color = fg.copy(0.4f),
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** صف قسم: كبسولة زجاجية، أيقونة متدرجة متوهجة، وتوهج لوني يتبع مكان لمستك */
@Composable
private fun AppSettingsRow(s: AppSettingsSection, bd: Backdrop, dark: Boolean, onClick: (Rect?) -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    var pressed by remember { mutableStateOf(false) }
    var touch by remember { mutableStateOf(Offset.Zero) }
    val scale by animateFloatAsState(if (pressed) 0.965f else 1f, spring(dampingRatio = 0.5f, stiffness = 420f), label = "rowScale")
    val glow by animateFloatAsState(if (pressed) 1f else 0f, tween(260), label = "rowGlow")
    val fg = MaterialTheme.colorScheme.onBackground
    var bounds by remember { mutableStateOf<Rect?>(null) }

    Row(
        Modifier.fillMaxWidth()
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .liquidCard(bd, shape, dark)
            .clip(shape)
            .drawWithContent {
                drawContent()
                if (glow > 0.01f) {
                    drawRect(
                        Brush.radialGradient(
                            listOf(s.c1.copy(alpha = 0.40f * glow), Color.Transparent),
                            center = touch, radius = size.width * 0.65f
                        )
                    )
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { off ->
                        touch = off
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick(bounds) }
                )
            }
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(52.dp)
                .drawBehind {
                    drawCircle(
                        Brush.radialGradient(listOf(s.c1.copy(alpha = 0.55f), Color.Transparent), radius = size.width * 0.95f),
                        radius = size.width * 0.95f
                    )
                }
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(s.c1, s.c2)))
                .border(1.dp, Color.White.copy(0.45f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(s.icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(s.subtitle, fontSize = 13.sp, color = fg.copy(alpha = 0.62f), modifier = Modifier.padding(top = 2.dp))
        }
        Icon(ChevronLeft, null, tint = fg.copy(alpha = 0.5f), modifier = Modifier.size(26.dp))
    }
}

/** صفحة قسم فارغة مؤقتاً: أيقونة كبيرة تنبض وحولها موجات */
@Composable
private fun AppSettingsSectionPage(section: AppSettingsSection, bd: Backdrop, onBack: () -> Unit) {
    val dark = isAppDark()
    val fg = MaterialTheme.colorScheme.onBackground
    val t = rememberInfiniteTransition(label = "sectionPulse")
    val wave by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "wave"
    )
    val breathe by t.animateFloat(
        0.96f, 1.05f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe"
    )
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        AppSettingsHeader(section.title, bd, onBack)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
                Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.matchParentSize()) {
                        for (k in 0..1) {
                            val q = (wave + k * 0.5f) % 1f
                            drawCircle(
                                section.c1.copy(alpha = 0.35f * (1f - q)),
                                radius = size.width * (0.28f + 0.22f * q),
                                style = Stroke(2.dp.toPx())
                            )
                        }
                    }
                    Box(
                        Modifier.size(112.dp)
                            .graphicsLayer { scaleX = breathe; scaleY = breathe }
                            .liquidCard(bd, CircleShape, dark)
                            .drawBehind {
                                drawCircle(
                                    Brush.radialGradient(
                                        listOf(section.c1.copy(0.55f), section.c2.copy(0.15f), Color.Transparent)
                                    )
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(section.icon, null, tint = Color.White, modifier = Modifier.size(52.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(section.title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "هذا القسم فارغ حالياً، وخياراته راح تنضاف بالتحديثات الجاية.",
                    fontSize = 15.sp, color = fg.copy(alpha = 0.65f), textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier.liquidCard(bd, RoundedCornerShape(50), dark)
                        .padding(horizontal = 22.dp, vertical = 9.dp)
                ) { Text("قريباً", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = section.c1) }
            }
        }
    }
}
