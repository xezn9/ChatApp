package com.chatapp.messenger.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.ui.glass.LiquidToggle
import com.chatapp.messenger.ui.theme.AppIconOption
import com.chatapp.messenger.ui.theme.GlassFx
import com.chatapp.messenger.ui.theme.ThemeSettings
import com.chatapp.messenger.ui.theme.glassSurfaceLayer
import com.chatapp.messenger.ui.theme.isAppDark
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidSlider
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ============================================================================
//  صفحة "ثيم التطبيق" (بملء الشاشة، تنبعث من زر القسم):
//   1) ثيم داكن / فاتح   2) لون الأزرار والتطبيق (24 لون)
//   3) تحكم حقيقي بالـ Liquid Glass (انكسار / ضبابية / شفافية) مع نموذج حي
//   4) تبديل أيقونة التطبيق (6 أيقونات)
// ============================================================================

private fun Color.shiftHue(deg: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    hsv[0] = (hsv[0] + deg + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun Color.lighter(f: Float): Color = androidx.compose.ui.graphics.lerp(this, Color.White, f)

@Composable
internal fun ThemeSettingsPage(onBack: () -> Unit) {
    val bd = rememberLayerBackdrop()
    val dark = isAppDark()
    val accent = AccentBlue
    val fg = MaterialTheme.colorScheme.onBackground
    val scroll = androidx.compose.foundation.rememberScrollState()
    // حركة الخلفية (40 ثانية للدورة) تقف لحظياً أثناء التمرير وسحب السلايدر: كل قطع الزجاج تقرأ هالخلفية،
    // فلو تتحرك وقت اللمس كل الزجاج ينعاد حسابه كل فريم. تكمل من نفس المكان بعد ما توقف الحركة.
    var phase by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        var lastFrame = 0L
        var lastGlassChange = 0L
        launch {
            androidx.compose.runtime.snapshotFlow {
                ThemeSettings.refraction + ThemeSettings.blur * 3f + ThemeSettings.transparency * 7f
            }.collect { lastGlassChange = System.nanoTime() }
        }
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                val busy = scroll.isScrollInProgress || System.nanoTime() - lastGlassChange < 400_000_000L
                if (lastFrame != 0L && !busy) {
                    phase = (phase + (now - lastFrame) / 40_000_000_000f * (2 * PI).toFloat()) % (2 * PI).toFloat()
                }
                lastFrame = now
            }
        }
    }
    val sky = if (dark) listOf(Color(0xFF0B1030), Color(0xFF1B1040), Color(0xFF2A1248))
    else listOf(Color(0xFFDDE6FF), Color(0xFFF1DCFF), Color(0xFFFFE3D6))
    val a2 = accent.shiftHue(60f)
    val a3 = accent.shiftHue(-70f)

    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        Box(Modifier.matchParentSize().layerBackdrop(bd).background(Brush.verticalGradient(sky))) {
            Canvas(Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                val a = phase
                fun blob(c: Color, cx: Float, cy: Float, r: Float, alpha: Float) {
                    drawCircle(
                        Brush.radialGradient(listOf(c.copy(alpha = alpha), Color.Transparent), center = Offset(cx, cy), radius = r),
                        r, Offset(cx, cy)
                    )
                }
                blob(accent, w * (0.18f + 0.14f * sin(a)), h * (0.12f + 0.05f * cos(a * 2)), w * 0.80f, 0.55f)
                blob(a2, w * (0.88f + 0.10f * cos(a)), h * (0.38f + 0.07f * sin(a)), w * 0.70f, 0.45f)
                blob(a3, w * (0.12f + 0.12f * cos(a * 2)), h * (0.68f + 0.06f * sin(a * 3)), w * 0.62f, 0.40f)
                blob(accent, w * (0.65f + 0.14f * sin(a * 2)), h * (0.97f + 0.03f * cos(a)), w * 0.80f, 0.45f)
            }
        }
        CompositionLocalProvider(LocalContentColor provides fg) {
            ThemeSettingsContent(bd, dark, accent, fg, onBack, scroll)
        }
    }
}

@Composable
private fun ThemeSettingsContent(bd: Backdrop, dark: Boolean, accent: Color, fg: Color, onBack: () -> Unit, scroll: androidx.compose.foundation.ScrollState) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(scroll).statusBarsPadding().navigationBarsPadding()) {
        AppSettingsHeader("ثيم التطبيق", bd, onBack)

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            // ------------------------------------------------------------ المظهر
            ThemeSection(0, "المظهر", "اختر الثيم الداكن أو الفاتح", fg) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModeCard(true, dark, accent, bd, dark, "ثيم داكن", Modifier.weight(1f)) { ThemeSettings.applyMode(1) }
                    ModeCard(false, !dark, accent, bd, dark, "ثيم فاتح", Modifier.weight(1f)) { ThemeSettings.applyMode(2) }
                }
            }

            // ------------------------------------------------------------ لون التطبيق
            ThemeSection(1, "لون التطبيق", "يتطبق على الأزرار والعناوين وعناصر التطبيق كلها", fg) {
                val shape = RoundedCornerShape(30.dp)
                Column(Modifier.fillMaxWidth().liquidCard(bd, shape, dark).padding(16.dp)) {
                    val all = ThemeSettings.accents
                    var openAll by remember { mutableStateOf(false) }
                    all.chunked(6).forEachIndexed { r, row ->
                        if (r > 0) Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            row.forEachIndexed { c, opt ->
                                val idx = r * 6 + c
                                ColorDot(opt.color, ThemeSettings.customAccent == null && ThemeSettings.accentIndex == idx) { ThemeSettings.setAccent(idx) }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "اللون الحالي: ${if (ThemeSettings.customAccent != null) "لون مخصّص" else all[ThemeSettings.accentIndex].name}",
                            fontSize = 14.sp, color = fg.copy(0.75f), modifier = Modifier.weight(1f)
                        )
                        // زر كل الألوان بتدرجاتها
                        Text("كل الألوان", fontSize = 13.sp, color = fg.copy(0.75f), modifier = Modifier.clickable { openAll = !openAll })
                        Spacer(Modifier.width(8.dp))
                        AllColorsButton(openAll, fg) { openAll = !openAll }
                    }
                    if (openAll) {
                        Spacer(Modifier.height(12.dp))
                        FullColorPanel(ThemeSettings.accent.toArgb(), fg) { ThemeSettings.setCustomAccent(it) }
                    }
                }
            }

            // ------------------------------------------------------------ Liquid Glass
            ThemeSection(2, "الزجاج السائل (Liquid Glass)", "تحكّم بالزجاج بكل التطبيق، والنموذج تحت يوريك النتيجة مباشرة", fg) {
                Column(Modifier.fillMaxWidth()) {
                    GlassPreview(dark, accent)
                    Spacer(Modifier.height(14.dp))
                    val shape = RoundedCornerShape(30.dp)
                    Column(Modifier.fillMaxWidth().liquidCard(bd, shape, dark).padding(horizontal = 18.dp, vertical = 14.dp)) {
                        GlassSliderRow(
                            "شدة الانكسار", "كمية انحناء الضوء عند حواف الزجاج",
                            { ThemeSettings.refraction }, { ThemeSettings.applyRefraction(it) }, bd, dark, accent, fg
                        )
                        GlassSliderRow(
                            "شدة الضبابية", "مقدار تمويه اللي ورا الزجاج",
                            { ThemeSettings.blur }, { ThemeSettings.applyBlur(it) }, bd, dark, accent, fg
                        )
                        GlassSliderRow(
                            "الشفافية", "كل ما زادت صار الزجاج أشفّ",
                            { ThemeSettings.transparency }, { ThemeSettings.applyTransparency(it) }, bd, dark, accent, fg
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "الوسط (50%) = الشكل الأصلي", fontSize = 12.sp, color = fg.copy(0.5f),
                                modifier = Modifier.weight(1f)
                            )
                            val pill = RoundedCornerShape(50)
                            Box(
                                Modifier.liquidCard(bd, pill, dark)
                                    .clip(pill)
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                        ThemeSettings.resetGlass()
                                    }
                                    .padding(horizontal = 18.dp, vertical = 9.dp)
                            ) { Text("إعادة الضبط", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = accent) }
                        }
                    }
                }
            }

            // ------------------------------------------------------------ خلفية الواجهات
            ThemeSection(3, "خلفية الواجهات", "خلفية مستقلة لـ Home و Groups و Account: جاهزة أو صورة/GIF من المعرض", fg) {
                AppBackgroundSection(bd, dark, accent, fg)
            }

            // ------------------------------------------------------------ أيقونة التطبيق
            ThemeSection(4, "أيقونة التطبيق", "بدّل شكل أيقونة التطبيق بشاشة الجهاز", fg) {
                val shape = RoundedCornerShape(30.dp)
                Column(Modifier.fillMaxWidth().liquidCard(bd, shape, dark).padding(horizontal = 10.dp, vertical = 12.dp)) {
                    ThemeSettings.icons.chunked(3).forEachIndexed { r, row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEachIndexed { c, opt ->
                                val idx = r * 3 + c
                                IconTile(opt, ThemeSettings.iconIndex == idx, accent, fg, Modifier.weight(1f)) {
                                    try { ThemeSettings.setIcon(ctx, idx) } catch (_: Throwable) { }
                                }
                            }
                        }
                    }
                    Text(
                        "ممكن تاخذ الأيقونة لحظات حتى تتبدل بالشاشة الرئيسية حسب لانشر جهازك.",
                        fontSize = 12.sp, color = fg.copy(0.55f), textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 8.dp, end = 8.dp)
                    )
                }
            }
            // ------------------------------------------------------------ شاشة البداية (Splash)
            ThemeSection(5, "شاشة البداية (Splash)", "للي ما يريد يشوف السبلاش عند فتح التطبيق", fg) {
                val shape = RoundedCornerShape(30.dp)
                val skip = ThemeSettings.skipSplash
                Row(
                    Modifier.fillMaxWidth()
                        .liquidCard(bd, shape, dark, borderColor = if (skip) accent.copy(alpha = 0.75f) else null)
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("إلغاء السبلاش", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "يفتح التطبيق مباشرة بدون شاشة البداية المتحركة",
                            fontSize = 13.sp, color = fg.copy(0.62f), modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    LiquidToggle(
                        selected = { ThemeSettings.skipSplash },
                        onSelect = { ThemeSettings.setSkipSplash(it) },
                        backdrop = bd, dark = dark, accent = accent
                    )
                }
            }
            Spacer(Modifier.height(36.dp))
        }
    }
}

@Composable
private fun ThemeSection(index: Int, title: String, subtitle: String, fg: Color, content: @Composable () -> Unit) {
    StaggerIn(index, Modifier.fillMaxWidth().padding(top = 18.dp), step = 70L, startDelay = 260L) {
        Column(Modifier.fillMaxWidth()) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 13.sp, color = fg.copy(0.62f), modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            content()
        }
    }
}

// ---------------------------------------------------------------------------
//  داكن / فاتح
// ---------------------------------------------------------------------------

@Composable
private fun ModeCard(
    darkCard: Boolean, selected: Boolean, accent: Color, bd: Backdrop, dark: Boolean,
    label: String, modifier: Modifier, onClick: () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    val sc by animateFloatAsState(if (selected) 1f else 0.96f, spring(0.6f, 380f), label = "modeScale")
    Column(
        modifier
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .liquidCard(
                bd, shape, dark,
                borderWidth = if (selected) 2.2.dp else 0.9.dp,
                borderColor = if (selected) accent else null
            )
            .clip(shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(20.dp)).background(
                Brush.verticalGradient(
                    if (darkCard) listOf(Color(0xFF0B1030), Color(0xFF2A1250))
                    else listOf(Color(0xFFDDE6FF), Color(0xFFFFE3D6))
                )
            )
        ) {
            Canvas(Modifier.matchParentSize()) {
                val c = Offset(size.width / 2f, size.height * 0.36f)
                val r = size.minDimension * 0.17f
                if (darkCard) {
                    val full = Path().apply { addOval(Rect(c, r)) }
                    val cut = Path().apply { addOval(Rect(Offset(c.x + r * 0.55f, c.y - r * 0.30f), r * 0.88f)) }
                    drawPath(Path.combine(PathOperation.Difference, full, cut), Color(0xFFFFE9A8))
                } else {
                    drawCircle(Color(0xFFFFB020), r * 0.78f, c)
                    for (k in 0 until 8) {
                        val ang = (k * PI / 4).toFloat()
                        val s = Offset(c.x + cos(ang) * r * 1.1f, c.y + sin(ang) * r * 1.1f)
                        val e = Offset(c.x + cos(ang) * r * 1.5f, c.y + sin(ang) * r * 1.5f)
                        drawLine(Color(0xFFFFB020), s, e, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
                // فقاعتين دردشة صغيرة
                val bw = size.width * 0.46f
                val bh = size.height * 0.13f
                drawRoundRect(
                    if (darkCard) Color.White.copy(0.16f) else Color.Black.copy(0.10f),
                    Offset(size.width * 0.10f, size.height * 0.66f), Size(bw, bh), CornerRadius(100f)
                )
                drawRoundRect(
                    accent.copy(0.95f),
                    Offset(size.width * 0.90f - bw * 0.8f, size.height * 0.82f), Size(bw * 0.8f, bh), CornerRadius(100f)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(6.dp))
            }
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---------------------------------------------------------------------------
//  لون
// ---------------------------------------------------------------------------

@Composable
private fun ColorDot(c: Color, selected: Boolean, onClick: () -> Unit) {
    val sc by animateFloatAsState(if (selected) 1.12f else 1f, spring(0.5f, 420f), label = "dotScale")
    val onC = if (c.luminance() > 0.55f) Color(0xFF101418) else Color.White
    Box(
        Modifier.size(44.dp)
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .drawBehind {
                if (selected) drawCircle(
                    Brush.radialGradient(listOf(c.copy(alpha = 0.55f), Color.Transparent), radius = size.width * 0.95f),
                    radius = size.width * 0.95f
                )
            }
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(c.lighter(0.30f), c)))
            .border(if (selected) 2.5.dp else 1.dp, Color.White.copy(if (selected) 0.95f else 0.40f), CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(Icons.Filled.Check, null, tint = onC, modifier = Modifier.size(22.dp))
    }
}

// ---------------------------------------------------------------------------
//  Liquid Glass: سلايدر + نموذج حي
// ---------------------------------------------------------------------------

@Composable
private fun GlassSliderRow(
    title: String, hint: String,
    value: () -> Float, onChange: (Float) -> Unit,
    bd: Backdrop, dark: Boolean, accent: Color, fg: Color
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${(value() * 100f).roundToInt()}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accent)
        }
        Text(hint, fontSize = 12.sp, color = fg.copy(0.55f))
        LiquidSlider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            backdrop = bd,
            modifier = Modifier.padding(top = 6.dp),
            dark = dark,
            accent = accent
        )
    }
}

/** نموذج: خلفية ملونة بخطوط ونص، وفوقها قطع زجاج تتحرك. كلها تقرأ نفس قيم الإعدادات الحقيقية */
@Composable
private fun GlassPreview(dark: Boolean, accent: Color) {
    val pbd = rememberLayerBackdrop()
    val t = rememberInfiniteTransition(label = "glassPreview")
    val p by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(6500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glassPreviewP"
    )
    val a2 = accent.shiftHue(70f)
    val a3 = accent.shiftHue(-70f)
    val outer = RoundedCornerShape(30.dp)

    BoxWithConstraints(Modifier.fillMaxWidth().height(240.dp).clip(outer).border(0.9.dp, glassBorderBrush(), outer)) {
        val boxW = maxWidth
        Box(Modifier.matchParentSize().layerBackdrop(pbd)) {
            Canvas(Modifier.matchParentSize()) {
                drawRect(Brush.linearGradient(listOf(accent, a2, a3)))
                val n = 16
                val sw = size.width / n
                for (i in 0 until n step 2) drawRect(Color.White.copy(0.20f), Offset(i * sw, 0f), Size(sw, size.height))
                for (k in 1..5) drawLine(Color.White.copy(0.30f), Offset(0f, size.height * k / 6f), Offset(size.width, size.height * k / 6f), 1.5.dp.toPx())
                drawCircle(Color(0xFFFFD166), size.height * 0.20f, Offset(size.width * 0.78f, size.height * 0.30f))
                drawCircle(Color.White.copy(0.9f), size.height * 0.08f, Offset(size.width * 0.20f, size.height * 0.78f))
                drawCircle(Color(0xFF101418).copy(0.55f), size.height * 0.12f, Offset(size.width * 0.55f, size.height * 0.85f))
            }
            Text(
                "Aa 123 زجاج", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 14.dp)
            )
            Text(
                "Liquid Glass", color = Color.White.copy(0.95f), fontSize = 26.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 14.dp)
            )
        }

        val surface = if (dark) Color.Black.copy(0.26f) else Color.White.copy(0.42f)

        // البطاقة الكبيرة
        val cardShape = RoundedCornerShape(34.dp)
        Box(
            Modifier
                .offset { IntOffset((boxW.toPx() * (0.05f + 0.30f * p)).roundToInt(), 52.dp.roundToPx()) }
                .size(176.dp, 112.dp)
                .drawBackdrop(
                    backdrop = pbd,
                    shape = { cardShape },
                    effects = {
                        vibrancy()
                        blur(14f.dp.toPx() * GlassFx.blurK)
                        lens(14f.dp.toPx() * GlassFx.refractK, 28f.dp.toPx() * GlassFx.refractK)
                    },
                    onDrawSurface = { glassSurfaceLayer { drawRect(surface) } }
                )
                .border(0.9.dp, glassBorderBrush(0.5f), cardShape)
        )
        // الكرة
        Box(
            Modifier
                .offset { IntOffset((boxW.toPx() * (0.74f - 0.38f * p)).roundToInt(), 118.dp.roundToPx()) }
                .size(86.dp)
                .drawBackdrop(
                    backdrop = pbd,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(2f.dp.toPx() * GlassFx.blurK)
                        lens(86f.dp.toPx() * 0.22f * GlassFx.refractK, 86f.dp.toPx() * 0.45f * GlassFx.refractK)
                    },
                    onDrawSurface = { glassSurfaceLayer { drawRect(surface) } }
                )
                .border(0.9.dp, glassBorderBrush(0.5f), CircleShape)
        )
        // زر كبسولة
        val pill = RoundedCornerShape(50)
        Box(
            Modifier
                .offset { IntOffset((boxW.toPx() * (0.07f + 0.10f * p)).roundToInt(), 184.dp.roundToPx()) }
                .size(124.dp, 44.dp)
                .drawBackdrop(
                    backdrop = pbd,
                    shape = { pill },
                    effects = {
                        vibrancy()
                        blur(2f.dp.toPx() * GlassFx.blurK)
                        lens(12f.dp.toPx() * GlassFx.refractK, 24f.dp.toPx() * GlassFx.refractK)
                    },
                    onDrawSurface = { glassSurfaceLayer { drawRect(surface) } }
                )
                .border(0.9.dp, glassBorderBrush(0.5f), pill),
            contentAlignment = Alignment.Center
        ) { Text("زر زجاجي", color = glassFg(dark), fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
    }
}

// ---------------------------------------------------------------------------
//  أيقونة التطبيق
// ---------------------------------------------------------------------------

@Composable
private fun IconTile(opt: AppIconOption, selected: Boolean, accent: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    val sc by animateFloatAsState(if (selected) 1.05f else 1f, spring(0.55f, 400f), label = "iconScale")
    val sh = RoundedCornerShape(20.dp)
    Column(
        modifier
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Image(
                painterResource(opt.previewRes), null,
                Modifier.size(78.dp).clip(sh)
                    .border(if (selected) 3.dp else 1.dp, if (selected) accent else Color.White.copy(0.30f), sh)
            )
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).size(24.dp)
                        .clip(CircleShape).background(accent).border(1.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp)) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            opt.name, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) accent else fg.copy(0.8f)
        )
    }
}
