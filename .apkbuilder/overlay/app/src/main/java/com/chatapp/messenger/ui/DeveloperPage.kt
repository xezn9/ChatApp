package com.chatapp.messenger.ui

import com.chatapp.messenger.ui.theme.AppFont

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.R
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
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// ============================================================================
//  صفحة مطور التطبيق: صورتي + تيليجرام + تيك توك + لمسات إبداعية
//  غيّر الحسابات من هنا:
// ============================================================================
private const val DEV_TELEGRAM = "xezn9"
private const val DEV_TIKTOK = "xezn99"

private fun devVec(name: String, path: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = PathParser().parsePathString(path).toNodes(),
        pathFillType = PathFillType.NonZero,
        fill = SolidColor(Color.Black)
    ).build()

private val PlaneIcon: ImageVector by lazy { devVec("Plane", "M2.01,21L23,12 2.01,3 2,10l15,2 -15,2z") }
private val NoteIcon: ImageVector by lazy {
    devVec("Note", "M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z")
}

private class FloatHeart(val id: Int, val dx: Float, val emoji: String)

private val devLines = listOf(
    "مطوّر تطبيقات أندرويد",
    "أعشق الزجاج السائل",
    "صنعت Pigeon بشغف",
    "Kotlin · Compose · Firebase"
)

private const val codeText =
    "val app = PigeonApp()\napp.design = LiquidGlass\napp.privacy = YourControl\napp.run()  // with love"

@Composable
internal fun DeveloperPage(onBack: () -> Unit) {
    val dark = isAppDark()
    val fg = MaterialTheme.colorScheme.onBackground
    val ctx = LocalContext.current
    val bd = rememberLayerBackdrop()

    val t = rememberInfiniteTransition(label = "dev")
    val slow by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart), label = "slow")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "spin")
    val orbit by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(11_000, easing = LinearEasing), RepeatMode.Restart), label = "orbit")
    val shine by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(3400, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "shine")
    val beat by t.animateFloat(0.9f, 1.15f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "beat")

    // نجوم ثابتة المواقع (تلمع وتطفو)
    val stars = remember {
        val r = Random(7)
        List(46) { floatArrayOf(r.nextFloat(), r.nextFloat(), 1.2f + r.nextFloat() * 2.4f, r.nextFloat() * 6.28f) }
    }

    // ---- حالة الصورة: ميلان ثلاثي الأبعاد + قلوب تطلع عند اللمس ----
    var tilt by remember { mutableStateOf(Offset.Zero) }
    val tx by animateFloatAsState(tilt.x, spring(0.45f, 220f), label = "tx")
    val ty by animateFloatAsState(tilt.y, spring(0.45f, 220f), label = "ty")
    var hearts by remember { mutableStateOf(listOf<FloatHeart>()) }
    var nextId by remember { mutableStateOf(0) }
    var love by remember { mutableStateOf(0) }
    val loveScale by animateFloatAsState(1f + (love % 2) * 0.12f, spring(0.4f, 500f), label = "loveScale")

    // ---- كتابة تدريجية للسطر التعريفي ----
    var lineIdx by remember { mutableStateOf(0) }
    var shown by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val l = devLines[lineIdx]
            for (i in 1..l.length) { shown = l.take(i); delay(55) }
            delay(1500)
            for (i in l.length downTo 0) { shown = l.take(i); delay(22) }
            lineIdx = (lineIdx + 1) % devLines.size
        }
    }
    val cursor by t.animateFloat(0f, 1f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "cursor")

    // ---- كتابة الكود ----
    var code by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        delay(900)
        while (true) {
            for (i in 1..codeText.length) { code = codeText.take(i); delay(45) }
            delay(3000)
            code = ""
            delay(500)
        }
    }

    val photo = ImageBitmap_dev()

    Box(Modifier.fillMaxSize()) {
        // ---------- الخلفية (معتمة بملء الشاشة) + النجوم ----------
        val sky = if (dark) listOf(Color(0xFF070B24), Color(0xFF1C0E3D), Color(0xFF3A1550))
        else listOf(Color(0xFFDDE6FF), Color(0xFFF1DCFF), Color(0xFFFFE3D6))
        Box(Modifier.fillMaxSize().layerBackdrop(bd).background(Brush.verticalGradient(sky))) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                fun blob(c: Color, cx: Float, cy: Float, r: Float, a: Float) =
                    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), center = Offset(cx, cy), radius = r), r, Offset(cx, cy))
                blob(Color(0xFF00E5FF), w * (0.2f + 0.14f * sin(slow)), h * (0.18f + 0.05f * cos(slow * 2)), w * 0.8f, 0.30f)
                blob(Color(0xFFB45CFF), w * (0.85f + 0.10f * cos(slow)), h * (0.45f + 0.07f * sin(slow)), w * 0.75f, 0.42f)
                blob(Color(0xFFFF2D92), w * (0.15f + 0.12f * cos(slow * 2)), h * (0.72f + 0.06f * sin(slow * 3)), w * 0.65f, 0.35f)
                blob(Color(0xFFFFA24D), w * (0.65f + 0.14f * sin(slow * 2)), h * (0.97f + 0.03f * cos(slow)), w * 0.85f, 0.35f)
                val starColor = if (dark) Color.White else Color(0xFF6A4CFF)
                stars.forEach { s ->
                    val x = w * s[0]
                    val y = (h * s[1] + slow * 6f * s[2]) % h
                    val a = 0.25f + 0.75f * abs(sin(orbit * 2f + s[3]))
                    drawCircle(starColor.copy(alpha = a * 0.8f), s[2].dp.toPx() * 0.6f, Offset(x, y))
                }
            }
        }

        // ---------- المحتوى (الهيدر يتحرك مع الصفحة) ----------
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.statusBarsPadding())
            AppSettingsHeader("مطور التطبيق", bd, onBack)

            // ===== الصورة =====
            StaggerIn(0, Modifier.fillMaxWidth().padding(top = 6.dp), startDelay = 120L) {
                Box(
                    Modifier.fillMaxWidth().height(400.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                love++
                                val e = listOf("💙", "💜", "❤️", "✨", "💫").random()
                                hearts = hearts + FloatHeart(nextId++, (Random.nextFloat() - 0.5f) * 220f, e)
                            })
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = { tilt = Offset.Zero },
                                onDragCancel = { tilt = Offset.Zero }
                            ) { change, d ->
                                change.consume()
                                tilt = Offset((tilt.x + d.x / 260f).coerceIn(-1f, 1f), (tilt.y + d.y / 260f).coerceIn(-1f, 1f))
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // الحلقة الدوارة الملونة + التوهج
                    Canvas(Modifier.size(350.dp)) {
                        val r = size.width / 2f - 8.dp.toPx()
                        drawCircle(
                            Brush.radialGradient(listOf(Color(0xFF8A5CFF).copy(0.40f), Color.Transparent), radius = size.width * 0.62f),
                            size.width * 0.62f
                        )
                        rotate(spin) {
                            drawCircle(
                                Brush.sweepGradient(listOf(Color(0xFF00E5FF), Color(0xFF8A5CFF), Color(0xFFFF2D92), Color(0xFFFFA24D), Color(0xFF00E5FF))),
                                r, style = Stroke(4.dp.toPx())
                            )
                        }
                        rotate(-spin * 0.6f) {
                            drawCircle(
                                Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(0.7f), Color.Transparent)),
                                r - 14.dp.toPx(), style = Stroke(2.dp.toPx())
                            )
                        }
                    }

                    // بطاقة الصورة (تميل بإصبعك + لمعة تعبر عليها)
                    Box(
                        Modifier.size(width = 232.dp, height = 300.dp)
                            .graphicsLayer {
                                rotationY = tx * 20f
                                rotationX = -ty * 20f
                                cameraDistance = 14f * density
                            }
                            .clip(RoundedCornerShape(38.dp))
                            .border(
                                1.6.dp,
                                Brush.verticalGradient(listOf(Color.White.copy(0.9f), Color.White.copy(0.1f), Color(0xFFFF2D92).copy(0.6f))),
                                RoundedCornerShape(38.dp)
                            )
                    ) {
                        Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(
                            Modifier.fillMaxSize().drawBehind {
                                drawRect(Brush.verticalGradient(0.65f to Color.Transparent, 1f to Color.Black.copy(0.45f)))
                            }
                        )
                    }

                    // كرات زجاجية تدور حول الصورة
                    val density = LocalDensity.current
                    val rx = with(density) { 150.dp.toPx() }
                    val ry = with(density) { 185.dp.toPx() }
                    listOf(
                        Triple(60, 1f, 0f), Triple(40, -1.4f, 2.1f), Triple(30, 1.9f, 4.2f)
                    ).forEach { (sz, speed, ph) ->
                        Box(
                            Modifier
                                .absoluteOffset {
                                    IntOffset(
                                        (cos(orbit * speed + ph) * rx).roundToInt(),
                                        (sin(orbit * speed + ph) * ry).roundToInt()
                                    )
                                }
                                .size(sz.dp)
                                .drawBackdrop(
                                    backdrop = bd,
                                    shape = { CircleShape },
                                    effects = {
                                        vibrancy()
                                        blur((1.5f.dp.toPx()) * GlassFx.blurK)
                                        lens((sz.dp.toPx() * 0.3f) * GlassFx.refractK, (sz.dp.toPx() * 0.55f) * GlassFx.refractK)
                                    },
                                    onDrawSurface = { glassSurfaceLayer { drawRect(Color.White.copy(0.08f)) } }
                                )
                                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(0.9f), Color.White.copy(0.1f))), CircleShape)
                        )
                    }

                    // قلوب تطلع عند اللمس
                    hearts.forEach { h ->
                        androidx.compose.runtime.key(h.id) {
                            FloatingHeartView(h) { hearts = hearts.filter { it.id != h.id } }
                        }
                    }
                }
            }

            // عداد اللمسات
            StaggerIn(1, Modifier.fillMaxWidth(), startDelay = 120L) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.graphicsLayer { scaleX = loveScale; scaleY = loveScale }
                            .liquidCard(bd, RoundedCornerShape(50), dark)
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            if (love == 0) "المس الصورة أو حرّكها بإصبعك ✨" else "💙 $love",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg.copy(0.8f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            // ===== الاسم + السطر المكتوب تدريجياً =====
            StaggerIn(2, Modifier.fillMaxWidth(), startDelay = 120L) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "e4",
                        style = TextStyle(fontFamily = AppFont, brush = Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF8A5CFF), Color(0xFFFF2D92))),
                            fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp
                        )
                    )
                    Text("@xezn99", fontSize = 15.sp, color = fg.copy(0.6f))
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(shown, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, color = fg.copy(0.9f))
                        Box(Modifier.padding(start = 3.dp).width(2.dp).height(20.dp).background(AccentBlue.copy(alpha = cursor)))
                    }
                }
            }
            Spacer(Modifier.height(22.dp))

            // ===== الزرين =====
            StaggerIn(3, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), startDelay = 120L) {
                SocialButton(
                    bd, dark, "تيليجرام", "@$DEV_TELEGRAM", PlaneIcon,
                    Color(0xFF2AABEE), Color(0xFF229ED9), shine
                ) {
                    openLink(ctx, "tg://resolve?domain=$DEV_TELEGRAM", "https://t.me/$DEV_TELEGRAM")
                }
            }
            StaggerIn(4, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), startDelay = 120L) {
                SocialButton(
                    bd, dark, "تيك توك", "@$DEV_TIKTOK", NoteIcon,
                    Color(0xFFFF0050), Color(0xFF00F2EA), shine
                ) {
                    openLink(ctx, "https://www.tiktok.com/@$DEV_TIKTOK", "https://www.tiktok.com/@$DEV_TIKTOK")
                }
            }
            Spacer(Modifier.height(18.dp))

            // ===== بطاقة الكود (تنكتب لحالها) =====
            StaggerIn(5, Modifier.fillMaxWidth().padding(horizontal = 16.dp), startDelay = 120L) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(Modifier.fillMaxWidth().liquidCard(bd, RoundedCornerShape(28.dp), dark).padding(18.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf(Color(0xFFFF5F57), Color(0xFFFEBC2E), Color(0xFF28C840)).forEach {
                                Box(Modifier.size(11.dp).clip(CircleShape).background(it))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.height(112.dp)) {
                            Text(
                                code + if (cursor > 0.5f) "▌" else "",
                                fontFamily = FontFamily.Monospace, fontSize = 13.5.sp, lineHeight = 22.sp,
                                color = Color(0xFF5CFFC8), textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(26.dp))

            // ===== ختام =====
            StaggerIn(6, Modifier.fillMaxWidth(), startDelay = 120L) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("صُنع بكل ", fontSize = 14.sp, color = fg.copy(0.55f))
                    Text("💙", fontSize = 16.sp, modifier = Modifier.graphicsLayer { scaleX = beat; scaleY = beat })
                    Text(" بواسطة e4", fontSize = 14.sp, color = fg.copy(0.55f))
                }
            }
            Spacer(Modifier.height(30.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun ImageBitmap_dev() = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.dev_photo)

private fun openLink(ctx: android.content.Context, primary: String, fallback: String) {
    fun go(u: String) = ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    try { go(primary) } catch (_: Exception) { try { go(fallback) } catch (_: Exception) { } }
}

/** قلب/نجمة تطلع من الصورة وتتلاشى */
@Composable
private fun FloatingHeartView(h: FloatHeart, onDone: () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(1700, easing = FastOutSlowInEasing))
        onDone()
    }
    Text(
        h.emoji, fontSize = 30.sp,
        modifier = Modifier.graphicsLayer {
            translationY = -p.value * 240.dp.toPx()
            translationX = h.dx.dp.toPx() * p.value + sin(p.value * 9f) * 14.dp.toPx()
            alpha = (1f - p.value).coerceIn(0f, 1f)
            val s = 0.6f + 0.8f * (1f - p.value)
            scaleX = s; scaleY = s
        }
    )
}

/** زر اجتماعي زجاجي: أيقونة متدرجة متوهجة + لمعة تعبر + توهج يتبع اللمس */
@Composable
private fun SocialButton(
    bd: Backdrop, dark: Boolean, title: String, handle: String, icon: ImageVector,
    c1: Color, c2: Color, shine: Float, onClick: () -> Unit
) {
    val shape = RoundedCornerShape(30.dp)
    val fg = MaterialTheme.colorScheme.onBackground
    var pressed by remember { mutableStateOf(false) }
    var touch by remember { mutableStateOf(Offset.Zero) }
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(0.5f, 420f), label = "socScale")
    val glow by animateFloatAsState(if (pressed) 1f else 0f, tween(260), label = "socGlow")

    Row(
        Modifier.fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .liquidCard(bd, shape, dark)
            .clip(shape)
            .drawWithContent {
                drawContent()
                // لمعة تعبر الزر
                val x = size.width * (1f - (shine + 1f) / 3f)
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(0.16f), Color.Transparent),
                        start = Offset(x - 90.dp.toPx(), 0f), end = Offset(x + 30.dp.toPx(), size.height)
                    )
                )
                if (glow > 0.01f) {
                    drawRect(
                        Brush.radialGradient(
                            listOf(c1.copy(alpha = 0.45f * glow), Color.Transparent),
                            center = touch, radius = size.width * 0.7f
                        )
                    )
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { off -> touch = off; pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { onClick() }
                )
            }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(56.dp)
                .drawBehind {
                    drawCircle(
                        Brush.radialGradient(listOf(c1.copy(alpha = 0.6f), Color.Transparent), radius = size.width * 0.95f),
                        radius = size.width * 0.95f
                    )
                }
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(c1, c2)))
                .border(1.dp, Color.White.copy(0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(handle, fontSize = 14.sp, color = fg.copy(0.62f), modifier = Modifier.padding(top = 2.dp))
        }
        Text("فتح ↗", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg.copy(0.55f))
    }
}
