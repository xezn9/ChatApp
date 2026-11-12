package com.chatapp.messenger.ui

import com.chatapp.messenger.ui.theme.AppFont

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.chatapp.messenger.ui.theme.GlassFx
import com.chatapp.messenger.ui.theme.glassSurfaceLayer
import com.chatapp.messenger.ui.theme.isAppDark
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.roundToInt

private class AppFeature(val emoji: String, val title: String, val text: String, val c1: Color, val c2: Color)

private val appFeatures = listOf(
    AppFeature("💬", "محادثات سريعة", "رسائل فورية مع ردود وتعديل وحذف وتثبيت، وحالة اتصال وآخر ظهور.", Color(0xFF3D7BFF), Color(0xFF5B8CFF)),
    AppFeature("👥", "مجموعات كاملة", "أنشئ مجموعات وأضف مشرفين بصلاحيات، مع وضع بطيء وتقييد للأعضاء.", Color(0xFF8A5CFF), Color(0xFFFF2D92)),
    AppFeature("🎙️", "صوت وصور وفيديو", "أرسل رسائل صوتية وصور وفيديوهات بجودة عالية ومعاينة فورية.", Color(0xFFFF9F0A), Color(0xFFFF453A)),
    AppFeature("✨", "قصص وحالات", "شارك لحظاتك بقصة تختفي، وشوف قصص أصدقائك وتفاعل معها.", Color(0xFF00D4AA), Color(0xFF30D158)),
    AppFeature("🔒", "خصوصية بيدك", "تحكّم بمن يشوف اتصالك والمقروء، وفعّل طلبات الرسائل وحظر من تريد.", Color(0xFF30D158), Color(0xFF00D4AA)),
    AppFeature("🎨", "تخصيص كامل", "ثيمات فاتحة وغامقة وألوان وأشكال فقاعات تعبّر عن ذوقك.", Color(0xFFFF2D92), Color(0xFF8A5CFF))
)

@Composable
internal fun AboutAppPage(parentBd: Backdrop, onBack: () -> Unit, onOpenSupport: () -> Unit) {
    val dark = isAppDark()
    val fg = MaterialTheme.colorScheme.onBackground
    val ctx = LocalContext.current
    val version = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
    }
    val t = rememberInfiniteTransition(label = "about")
    val wave by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "aboutWave")
    val float by t.animateFloat(-7f, 7f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "aboutFloat")
    val glowA = AccentBlue
    // خلفية الصفحة نفسها (معتمة وتغطي الشاشة كلها) وهي الي ينكسر ضوءها بالزجاج
    val bd = rememberLayerBackdrop()
    val slow by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart), label = "aboutSlow")

    Box(Modifier.fillMaxSize()) {
        val sky = if (dark) listOf(Color(0xFF0B1030), Color(0xFF241046), Color(0xFF3A1550))
        else listOf(Color(0xFFDDE6FF), Color(0xFFF1DCFF), Color(0xFFFFE3D6))
        Box(Modifier.fillMaxSize().layerBackdrop(bd).background(Brush.verticalGradient(sky))) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                fun blob(c: Color, cx: Float, cy: Float, r: Float, a: Float) =
                    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), center = Offset(cx, cy), radius = r), r, Offset(cx, cy))
                blob(Color(0xFF4D7CFF), w * (0.2f + 0.14f * sin(slow)), h * (0.12f + 0.05f * cos(slow * 2)), w * 0.8f, 0.45f)
                blob(Color(0xFFB45CFF), w * (0.85f + 0.10f * cos(slow)), h * (0.40f + 0.07f * sin(slow)), w * 0.75f, 0.45f)
                blob(Color(0xFFFF5C8A), w * (0.15f + 0.12f * cos(slow * 2)), h * (0.68f + 0.06f * sin(slow * 3)), w * 0.65f, 0.38f)
                blob(Color(0xFFFFA24D), w * (0.65f + 0.14f * sin(slow * 2)), h * (0.95f + 0.04f * cos(slow)), w * 0.85f, 0.45f)
            }
        }
        // كل شي (حتى الهيدر وزر الرجوع) يتحرك ويصعد مع الصفحة، والصفحة بملء الشاشة
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.statusBarsPadding())
            AppSettingsHeader("حول التطبيق", bd, onBack)

            // ---- صورة التطبيق + الاسم + النسخة ----
            StaggerIn(0, Modifier.fillMaxWidth().padding(top = 8.dp), startDelay = 120L) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.matchParentSizeCompat()) {
                            for (k in 0..2) {
                                val q = (wave + k / 3f) % 1f
                                drawCircle(
                                    glowA.copy(alpha = 0.40f * (1f - q)),
                                    radius = size.width * (0.26f + 0.24f * q),
                                    style = Stroke(2.dp.toPx())
                                )
                            }
                        }
                        Box(
                            Modifier.size(132.dp).graphicsLayer { translationY = float.dp.toPx() }
                                .drawBehind {
                                    drawCircle(
                                        Brush.radialGradient(listOf(glowA.copy(0.55f), Color.Transparent), radius = size.width * 0.95f),
                                        radius = size.width * 0.95f
                                    )
                                }
                                .border(1.4.dp, Brush.verticalGradient(listOf(Color.White.copy(0.8f), Color.White.copy(0.1f))), CircleShape)
                                .padding(3.dp),
                            contentAlignment = Alignment.Center
                        ) { AppLogo(126.dp) }
                    }
                    Text(
                        SUPPORT_APP_NAME,
                        style = TextStyle(fontFamily = AppFont, brush = Brush.linearGradient(listOf(AccentBlue, SetViolet, SetPink)),
                            fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp
                        )
                    )
                    if (version.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier.liquidCard(bd, RoundedCornerShape(50), dark)
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) { Text("النسخة $version", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg.copy(0.8f)) }
                    }
                }
            }
            Spacer(Modifier.height(22.dp))

            // ---- الشرح ----
            StaggerIn(1, Modifier.fillMaxWidth().padding(horizontal = 16.dp), startDelay = 120L) {
                Column(
                    Modifier.fillMaxWidth().liquidCard(bd, RoundedCornerShape(30.dp), dark).padding(20.dp)
                ) {
                    Text("وش هو Pigeon؟", fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "تطبيق مراسلة عصري صُمّم حتى تحچي مع أصدقائك وعائلتك بسرعة وأمان وبشكل يبهر العين. " +
                            "كل شاشة فيه مبنية بتأثير الزجاج السائل، وكل حركة فيه ناعمة وحيّة، مع خصوصية تبقى تحت سيطرتك دايماً.",
                        fontSize = 16.sp, lineHeight = 29.sp, fontFamily = FontFamily.Serif,
                        color = fg.copy(0.85f)
                    )
                }
            }
            Spacer(Modifier.height(22.dp))

            // ---- المميزات ----
            StaggerIn(2, Modifier.fillMaxWidth().padding(horizontal = 24.dp), startDelay = 120L) {
                Text("المميزات", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Serif)
            }
            Spacer(Modifier.height(8.dp))
            appFeatures.forEachIndexed { i, f ->
                StaggerIn(3 + i, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), step = 70L, startDelay = 120L) {
                    Row(
                        Modifier.fillMaxWidth().liquidCard(bd, RoundedCornerShape(26.dp), dark).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(50.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(f.c1, f.c2)))
                                .border(1.dp, Color.White.copy(0.45f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Text(f.emoji, fontSize = 24.sp) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                            Text(f.text, fontSize = 13.5.sp, lineHeight = 21.sp, color = fg.copy(0.68f), modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            // ---- الـ Liquid Glass ----
            StaggerIn(3 + appFeatures.size, Modifier.fillMaxWidth().padding(horizontal = 16.dp), startDelay = 120L) {
                Column(Modifier.fillMaxWidth().liquidCard(bd, RoundedCornerShape(30.dp), dark).padding(20.dp)) {
                    Text("الزجاج السائل (Liquid Glass)", fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "كل الأزرار والبطاقات والأشرطة بالتطبيق مصنوعة من زجاج حقيقي يمزج ويكسر الضوء اللي خلفه: " +
                            "تمويه ناعم، انكسار عند الحواف، ولمعة تتفاعل مع لمستك. جرّب بنفسك: اسحب الكرة الزجاجية تحت وشوف كيف تشوّه الألوان خلفها.",
                        fontSize = 15.5.sp, lineHeight = 27.sp, fontFamily = FontFamily.Serif, color = fg.copy(0.85f)
                    )
                    Spacer(Modifier.height(14.dp))
                    GlassPlayground()
                }
            }
            Spacer(Modifier.height(22.dp))

            // ---- زر الدعم والشكاوي ----
            StaggerIn(4 + appFeatures.size, Modifier.fillMaxWidth().padding(horizontal = 16.dp), startDelay = 120L) {
                SupportGlassButton(bd, onOpenSupport)
            }
            Spacer(Modifier.height(10.dp))
            StaggerIn(5 + appFeatures.size, Modifier.fillMaxWidth(), startDelay = 120L) {
                Text(
                    "عندك مشكلة أو اقتراح؟ رسالتك توصل لمطور التطبيق مباشرة.",
                    fontSize = 13.sp, color = fg.copy(0.55f), textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
                )
            }
            Spacer(Modifier.height(34.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

private fun Modifier.matchParentSizeCompat(): Modifier = this.fillMaxSize()

/** محتوى الاستعراض: موجات ملوّنة ومربعات تدور على تدرج بحري، حتى يبين انكسار الكرة */
@Composable
private fun DemoBackdropContent(offset: Float) {
    val t = rememberInfiniteTransition(label = "demo")
    val p by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart), label = "demoP")
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF00C9A7), Color(0xFF0072FF), Color(0xFF2B1055))))
        // موجات
        val colors = listOf(Color(0xFFFFE259), Color(0xFFFF6B9D), Color(0xFFFFFFFF))
        for (k in 0..2) {
            val path = androidx.compose.ui.graphics.Path()
            val base = h * (0.30f + 0.20f * k)
            path.moveTo(0f, base)
            var x = 0f
            while (x <= w) {
                path.lineTo(x, base + h * 0.07f * sin(x / w * 2f * PI.toFloat() * (1.5f + k) + p * (1 + k)))
                x += 6f
            }
            drawPath(path, colors[k].copy(0.85f), style = Stroke(9.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        // مربعات تدور
        for (i in 0 until 4) {
            val cx = w * (0.15f + 0.23f * i)
            val cy = h * (0.5f + 0.28f * sin(p + i * 1.6f))
            val sz = h * 0.16f
            rotate(degrees = (p * 57.3f) * (if (i % 2 == 0) 1 else -1), pivot = Offset(cx, cy)) {
                drawRoundRect(
                    if (i % 2 == 0) Color.White.copy(0.9f) else Color(0xFF14083A).copy(0.85f),
                    topLeft = Offset(cx - sz, cy - sz), size = androidx.compose.ui.geometry.Size(sz * 2, sz * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(sz * 0.4f)
                )
            }
        }
    }
}

/** ساحة لعب: كرة زجاجية تنسحب بإصبعك (بنفس اتجاه إصبعك) وتكسر الألوان خلفها */
@Composable
private fun GlassPlayground() {
    val shape = RoundedCornerShape(24.dp)
    val localBd = rememberLayerBackdrop()
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(190.dp).clip(shape)
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(0.5f), Color.White.copy(0.06f))), shape)
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val orb = 104.dp
        val maxX = with(density) { ((maxWidth - orb) / 2).toPx() }
        val maxY = with(density) { ((maxHeight - orb) / 2).toPx() }
        var off by remember { mutableStateOf(Offset.Zero) }
        var down by remember { mutableStateOf(false) }
        val sc by animateFloatAsState(if (down) 1.15f else 1f, spring(0.45f, 380f), label = "orbScale")

        Box(Modifier.fillMaxSize().layerBackdrop(localBd)) { DemoBackdropContent(0f) }
        // السحب على الساحة الثابتة (مو على الكرة المتحركة) حتى ما ينعكس الاتجاه
        Box(
            Modifier.fillMaxSize().pointerInput(maxX, maxY) {
                detectDragGestures(
                    onDragStart = { down = true },
                    onDragEnd = { down = false },
                    onDragCancel = { down = false }
                ) { change, drag ->
                    change.consume()
                    off = Offset((off.x + drag.x).coerceIn(-maxX, maxX), (off.y + drag.y).coerceIn(-maxY, maxY))
                }
            }
        )
        Box(
            Modifier.align(Alignment.Center)
                // absoluteOffset: ما ينعكس بالواجهة العربية (RTL)
                .absoluteOffset { IntOffset(off.x.roundToInt(), off.y.roundToInt()) }
                .graphicsLayer { scaleX = sc; scaleY = sc }
                .size(orb)
                .drawBackdrop(
                    backdrop = localBd,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur((1.5f.dp.toPx()) * GlassFx.blurK)
                        lens((28f.dp.toPx()) * GlassFx.refractK, (56f.dp.toPx()) * GlassFx.refractK)
                    },
                    onDrawSurface = {
                        glassSurfaceLayer {
                            drawRect(Color.White.copy(alpha = 0.06f))
                            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.25f), 0.5f to Color.Transparent))
                        }
                    }
                )
                .border(1.2.dp, Brush.verticalGradient(listOf(Color.White.copy(0.85f), Color.White.copy(0.08f), AccentBlue.copy(0.5f))), CircleShape)
        )
    }
}

/** زر زجاجي كبير: الدعم والشكاوي */
@Composable
private fun SupportGlassButton(bd: Backdrop, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(30.dp)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(0.5f, 420f), label = "supBtn")
    Row(
        modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBackdrop(
                backdrop = bd,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur((3f.dp.toPx()) * GlassFx.blurK)
                    lens((16f.dp.toPx()) * GlassFx.refractK, (32f.dp.toPx()) * GlassFx.refractK)
                },
                onDrawSurface = {
                    glassSurfaceLayer {
                        drawRect(Brush.linearGradient(listOf(AccentBlue.copy(0.50f), SetViolet.copy(0.50f), SetPink.copy(0.45f))))
                        drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.28f), 0.5f to Color.Transparent))
                    }
                }
            )
            .border(1.2.dp, Brush.verticalGradient(listOf(Color.White.copy(0.85f), Color.White.copy(0.1f))), shape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { onClick() }
                )
            }
            .padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🛟", fontSize = 22.sp)
        Spacer(Modifier.width(10.dp))
        Text("الدعم والشكاوي", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}
