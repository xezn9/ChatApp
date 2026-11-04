package com.chatapp.messenger.ui

import com.chatapp.messenger.ui.theme.GlassFx
import com.chatapp.messenger.ui.theme.glassSurfaceLayer

import android.util.Patterns
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Star
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import com.chatapp.messenger.ui.theme.isAppDark
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.Repo
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.chatapp.messenger.ui.theme.appBackground
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import com.chatapp.messenger.ui.glass.GlassButton
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ============================================================================
//  صفحة "الحساب": خيارين (تغيير البريد / تغيير كلمة السر) بواجهة Liquid Glass
//  التحقق برابط يوصل للبريد (تضغطه وينجح التحقق)
// ============================================================================

private val AcRed = Color(0xFFFF453A)

private enum class AcPage(val depth: Int, val parent: AcPage?) {
    Home(0, null), Email(1, Home), Password(1, Home), Manage(1, Home), Deactivate(2, Manage), Delete(2, Manage)
}

private val TrashIcon: ImageVector by lazy {
    ImageVector.Builder("Trash", 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = PathParser().parsePathString(
            "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z"
        ).toNodes(),
        pathFillType = PathFillType.NonZero,
        fill = SolidColor(Color.Black)
    ).build()
}

/** تأكيد كلمة السر الحالية قبل العمليات الحساسة */
private suspend fun reauthWith(password: String) {
    val u = Repo.auth.currentUser ?: throw IllegalStateException("لازم تسجّل دخول")
    val mail = u.email ?: throw IllegalStateException("الحساب بدون بريد")
    u.reauthenticate(EmailAuthProvider.getCredential(mail, password)).await()
}

// ---------------------------------------------------------------------------
//  Firebase Auth (روابط التحقق تنرسل بالإيميل، بدون رموز)
// ---------------------------------------------------------------------------

private fun friendly(e: Throwable): String = when (e) {
    is FirebaseAuthInvalidCredentialsException -> "كلمة السر الحالية غير صحيحة"
    is FirebaseAuthRecentLoginRequiredException -> "سجّل خروج ثم ادخل من جديد وجرّب مرة ثانية"
    is FirebaseAuthUserCollisionException -> "هذا البريد مستخدم بحساب ثاني"
    is FirebaseAuthInvalidUserException -> "الحساب غير موجود أو تعطّل"
    is FirebaseTooManyRequestsException -> "محاولات كثيرة، جرّب بعد شوية"
    is FirebaseNetworkException -> "ما في اتصال بالإنترنت"
    else -> e.localizedMessage?.takeIf { it.isNotBlank() } ?: "صار خطأ، جرّب مرة ثانية"
}

private fun maskEmail(e: String): String {
    val at = e.indexOf('@')
    if (at < 2) return e
    return e.substring(0, 2) + "•".repeat((at - 2).coerceIn(1, 6)) + e.substring(at)
}

/** يخلّي الإيميل يظهر LTR داخل نص عربي بدون ما تنقلب علامات الترقيم */
private fun ltr(s: String) = "\u202A$s\u202C"

// ---------------------------------------------------------------------------
//  الصفحة الرئيسية للحساب + التنقل بين الصفحات
// ---------------------------------------------------------------------------

/** واجهة الحساب: شاشة مستقلة بخلفيتها الخاصة (معتمة، فوق الإعدادات تماماً) */
@Composable
internal fun AccountSettingsPage(onBack: () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AccountScene { bd -> AccountSettingsContent(bd, onBack) }
    }
}

/** خلفية الحساب: تدرج التطبيق + بقع ألوان تسبح ببطء + كرات زجاجية تكسر الضوء */
@Composable
internal fun AccountScene(content: @Composable (Backdrop) -> Unit) {
    val bd = rememberLayerBackdrop()
    val t = rememberInfiniteTransition(label = "accountScene")
    val phase by t.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(36_000, easing = LinearEasing), RepeatMode.Restart),
        label = "accountPhase"
    )
    // pointerInput: يمنع مرور اللمسات للإعدادات اللي تحت
    BoxWithConstraints(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }

        Box(Modifier.matchParentSize().layerBackdrop(bd).background(appBackground())) {
            Canvas(Modifier.matchParentSize()) {
                val a = phase
                fun blob(c: Color, cx: Float, cy: Float, r: Float, alpha: Float) {
                    drawCircle(
                        Brush.radialGradient(listOf(c.copy(alpha = alpha), Color.Transparent), center = Offset(cx, cy), radius = r),
                        r, Offset(cx, cy)
                    )
                }
                blob(AccentBlue, w * (0.80f + 0.14f * sin(a)), h * (0.14f + 0.06f * cos(a * 2)), w * 0.75f, 0.55f)
                blob(SetViolet, w * (0.12f + 0.12f * cos(a)), h * (0.48f + 0.08f * sin(a)), w * 0.70f, 0.50f)
                blob(SetMint, w * (0.75f + 0.14f * sin(a * 2)), h * (0.80f + 0.05f * cos(a)), w * 0.70f, 0.42f)
                blob(SetPink, w * (0.30f + 0.12f * cos(a * 2)), h * (0.98f - 0.04f * sin(a * 2)), w * 0.55f, 0.28f)
            }
        }

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
        Orb(150, 0.08f, 0.12f, 0.05f, 0.02f, 1, 0.5f)
        Orb(64, 0.82f, 0.46f, 0.04f, 0.03f, 2, 2.1f)
        Orb(120, 0.55f, 0.82f, 0.07f, 0.03f, 1, 4.0f)

        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            content(bd)
        }
    }
}

@Composable
private fun AccountSettingsContent(bd: Backdrop, onBack: () -> Unit) {
    var page by remember { mutableStateOf(AcPage.Home) }
    BackHandler(enabled = page != AcPage.Home) { page = page.parent ?: AcPage.Home }

    val title = when (page) {
        AcPage.Home -> "الحساب"
        AcPage.Email -> "تغيير البريد"
        AcPage.Password -> "تغيير كلمة السر"
        AcPage.Manage -> "تعطيل أو حذف"
        AcPage.Deactivate -> "تعطيل الحساب"
        AcPage.Delete -> "حذف الحساب"
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        AppSettingsHeader(title, bd) { if (page == AcPage.Home) onBack() else page = page.parent ?: AcPage.Home }

        AnimatedContent(
            targetState = page,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val toSub = targetState.depth > initialState.depth
                (slideInHorizontally(spring(0.85f, 330f)) { if (toSub) -it else it } +
                    fadeIn(tween(240)) + scaleIn(spring(0.9f, 330f), initialScale = 0.94f)) togetherWith
                    (slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { if (toSub) it / 3 else -it / 3 } +
                        fadeOut(tween(170)))
            },
            label = "accountPage"
        ) { p ->
            when (p) {
                AcPage.Home -> AccountHome(bd) { page = it }
                AcPage.Email -> ChangeEmailFlow(bd) { page = AcPage.Home }
                AcPage.Password -> ChangePasswordFlow(bd) { page = AcPage.Home }
                AcPage.Manage -> ManageAccountPage(bd) { page = it }
                AcPage.Deactivate -> DeactivateFlow(bd)
                AcPage.Delete -> DeleteFlow(bd)
            }
        }
    }
}

@Composable
private fun AccountHome(bd: Backdrop, onOpen: (AcPage) -> Unit) {
    val dark = isAppDark()
    val fg = MaterialTheme.colorScheme.onBackground
    val email = Repo.auth.currentUser?.email.orEmpty()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StaggerIn(0, Modifier.padding(top = 4.dp), startDelay = 120L) { AccountHero(bd) }

        StaggerIn(1, Modifier.fillMaxWidth().padding(top = 4.dp), startDelay = 120L) {
            Row(
                Modifier.fillMaxWidth().liquidCard(bd, RoundedCornerShape(26.dp), dark).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(AppIcons.Email, null, tint = AccentBlue, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("بريدك الحالي", fontSize = 12.sp, color = fg.copy(0.6f))
                    Text(ltr(email), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(22.dp))

        StaggerIn(2, Modifier.fillMaxWidth().padding(vertical = 6.dp), startDelay = 120L, step = 80L) {
            AccountActionCard(
                "تغيير البريد الإلكتروني", "نرسل رابط تحقق للبريد الجديد وتضغطه",
                AppIcons.Email, AccentBlue, SetViolet, bd, dark
            ) { onOpen(AcPage.Email) }
        }
        StaggerIn(3, Modifier.fillMaxWidth().padding(vertical = 6.dp), startDelay = 120L, step = 80L) {
            AccountActionCard(
                "تغيير كلمة السر", "نرسل رابط لبريدك وتختار كلمة سر جديدة",
                AppIcons.Lock, SetPink, SetOrange, bd, dark
            ) { onOpen(AcPage.Password) }
        }
        StaggerIn(4, Modifier.fillMaxWidth().padding(vertical = 6.dp), startDelay = 120L, step = 80L) {
            AccountActionCard(
                "تعطيل الحساب أو حذفه", "إخفاء حسابك مؤقتاً أو حذفه نهائياً",
                AppIcons.Block, AcRed, SetOrange, bd, dark
            ) { onOpen(AcPage.Manage) }
        }
        Spacer(Modifier.height(20.dp))
        StaggerIn(5, Modifier.fillMaxWidth().padding(horizontal = 12.dp), startDelay = 120L, step = 80L) {
            Text(
                "إذا ما وصلك الرابط شوف مجلد Spam.",
                fontSize = 13.sp, color = fg.copy(0.5f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun AccountHero(
    bd: Backdrop,
    icon: ImageVector = Icons.Filled.Person,
    c1: Color = AccentBlue,
    c2: Color = SetViolet
) {
    val dark = isAppDark()
    val t = rememberInfiniteTransition(label = "acHero")
    val wave by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart), label = "acWave"
    )
    val breathe by t.animateFloat(
        0.96f, 1.05f, infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "acBreathe"
    )
    Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            for (k in 0..2) {
                val q = (wave + k / 3f) % 1f
                drawCircle(
                    c1.copy(alpha = 0.32f * (1f - q)),
                    radius = size.width * (0.28f + 0.22f * q),
                    style = Stroke(2.dp.toPx())
                )
            }
        }
        Box(
            Modifier.size(104.dp)
                .graphicsLayer { scaleX = breathe; scaleY = breathe }
                .liquidCard(bd, CircleShape, dark)
                .drawBehind {
                    drawCircle(Brush.radialGradient(listOf(c1.copy(0.55f), c2.copy(0.18f), Color.Transparent)))
                },
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(50.dp)) }
    }
}

/** بطاقة خيار: كبسولة زجاجية، أيقونة متوهجة، وتوهج لوني يتبع لمستك */
@Composable
private fun AccountActionCard(
    title: String, subtitle: String, icon: ImageVector, c1: Color, c2: Color,
    bd: Backdrop, dark: Boolean, onClick: () -> Unit
) {
    val shape = RoundedCornerShape(30.dp)
    var pressed by remember { mutableStateOf(false) }
    var touch by remember { mutableStateOf(Offset.Zero) }
    val scale by animateFloatAsState(if (pressed) 0.965f else 1f, spring(dampingRatio = 0.5f, stiffness = 420f), label = "acCardScale")
    val glow by animateFloatAsState(if (pressed) 1f else 0f, tween(260), label = "acCardGlow")
    val fg = MaterialTheme.colorScheme.onBackground

    Row(
        Modifier.fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .liquidCard(bd, shape, dark)
            .clip(shape)
            .drawWithContent {
                drawContent()
                if (glow > 0.01f) {
                    drawRect(
                        Brush.radialGradient(
                            listOf(c1.copy(alpha = 0.40f * glow), Color.Transparent),
                            center = touch, radius = size.width * 0.65f
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
            .padding(horizontal = 16.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(54.dp)
                .drawBehind {
                    drawCircle(
                        Brush.radialGradient(listOf(c1.copy(alpha = 0.55f), Color.Transparent), radius = size.width * 0.95f),
                        radius = size.width * 0.95f
                    )
                }
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(c1, c2)))
                .border(1.dp, Color.White.copy(0.45f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(27.dp)) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 13.sp, color = fg.copy(alpha = 0.62f), modifier = Modifier.padding(top = 2.dp))
        }
    }
}

// ---------------------------------------------------------------------------
//  مكونات مشتركة
// ---------------------------------------------------------------------------

@Composable
private fun StepColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        content = content
    )
}

@Composable
private fun FlowPanel(bd: Backdrop, content: @Composable ColumnScope.() -> Unit) {
    val dark = isAppDark()
    Column(
        Modifier.fillMaxWidth().liquidCard(bd, RoundedCornerShape(32.dp), dark).padding(18.dp),
        content = content
    )
}

@Composable
private fun FlowHeader(icon: ImageVector, c1: Color, c2: Color, title: String, desc: String) {
    val fg = MaterialTheme.colorScheme.onBackground
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp, top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(76.dp)
                .drawBehind {
                    drawCircle(
                        Brush.radialGradient(listOf(c1.copy(alpha = 0.55f), Color.Transparent), radius = size.width * 0.95f),
                        radius = size.width * 0.95f
                    )
                }
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(c1, c2)))
                .border(1.dp, Color.White.copy(0.45f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
        Spacer(Modifier.height(14.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(desc, fontSize = 14.sp, color = fg.copy(0.68f), textAlign = TextAlign.Center)
    }
}

@Composable
private fun ErrorLine(error: String?) {
    AnimatedVisibility(
        visible = error != null,
        enter = fadeIn(tween(180)) + slideInVertically(spring(0.8f, 400f)) { -it / 2 }
    ) {
        Text(
            error.orEmpty(), color = AcRed, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )
    }
}

@Composable
private fun AcButton(
    text: String, bd: Backdrop, tint: Color, loading: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit
) {
    GlassButton(
        onClick = { if (!loading) onClick() },
        backdrop = bd, tint = tint, enabled = !loading, pressScale = 1.03f,
        modifier = modifier.fillMaxWidth().height(56.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
        } else {
            Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** شاشة النجاح: دائرة زجاجية تنبض، علامة صح ترتسم، وموجات تنتشر */
@Composable
private fun SuccessView(bd: Backdrop, title: String, subtitle: String, onDone: () -> Unit) {
    val dark = isAppDark()
    val fg = MaterialTheme.colorScheme.onBackground
    val check = remember { Animatable(0f) }
    val pop = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { pop.animateTo(1f, spring(0.5f, 260f)) }
        delay(250)
        launch { burst.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
        check.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    StepColumn {
        Spacer(Modifier.height(36.dp))
        Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(230.dp)) {
                val q = burst.value
                for (k in 0..1) {
                    val r = (q - k * 0.18f).coerceIn(0f, 1f)
                    drawCircle(
                        SetGreen.copy(alpha = 0.5f * (1f - r)),
                        radius = size.width * (0.26f + 0.24f * r),
                        style = Stroke(2.5.dp.toPx())
                    )
                }
            }
            Box(
                Modifier.size(122.dp)
                    .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                    .liquidCard(bd, CircleShape, dark)
                    .drawBehind {
                        drawCircle(Brush.radialGradient(listOf(SetGreen.copy(0.55f), SetMint.copy(0.15f), Color.Transparent)))
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(122.dp)) {
                    val s = size.width
                    val a = Offset(s * 0.30f, s * 0.52f)
                    val b = Offset(s * 0.44f, s * 0.66f)
                    val c = Offset(s * 0.72f, s * 0.36f)
                    val t = check.value
                    val t1 = (t / 0.4f).coerceIn(0f, 1f)
                    val t2 = ((t - 0.4f) / 0.6f).coerceIn(0f, 1f)
                    val w = 9.dp.toPx()
                    drawLine(
                        Color.White, a, Offset(a.x + (b.x - a.x) * t1, a.y + (b.y - a.y) * t1),
                        strokeWidth = w, cap = StrokeCap.Round
                    )
                    if (t2 > 0f) {
                        drawLine(
                            Color.White, b, Offset(b.x + (c.x - b.x) * t2, b.y + (c.y - b.y) * t2),
                            strokeWidth = w, cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
        Text(
            title, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle, fontSize = 15.sp, color = fg.copy(0.68f), textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        )
        Spacer(Modifier.height(28.dp))
        AcButton("تم", bd, SetGreen, loading = false, onClick = onDone)
    }
}

private fun stepTransition(): ContentTransform =
    (slideInVertically(spring(0.85f, 350f)) { it / 7 } + fadeIn(tween(240))) togetherWith fadeOut(tween(120))

// ---------------------------------------------------------------------------
//  تغيير البريد الإلكتروني: رابط تحقق يوصل للبريد الجديد، ولما تضغطه يتغير
// ---------------------------------------------------------------------------

@Composable
private fun ChangeEmailFlow(bd: Backdrop, onDone: () -> Unit) {
    val dark = isAppDark()
    val fg = MaterialTheme.colorScheme.onBackground
    val scope = rememberCoroutineScope()
    val current = remember { Repo.auth.currentUser?.email.orEmpty() }

    var step by remember { mutableIntStateOf(0) }
    var newEmail by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var cooldown by remember { mutableIntStateOf(0) }

    LaunchedEffect(cooldown) {
        if (cooldown > 0) { delay(1000); cooldown-- }
    }

    /** هل ضغط المستخدم الرابط وانتغير البريد؟ */
    suspend fun checkDone(): Boolean {
        val mail = newEmail.trim().lowercase()
        try {
            Repo.auth.currentUser?.reload()?.await()
            val u = Repo.auth.currentUser
            if (u != null && u.email.equals(mail, ignoreCase = true)) {
                runCatching { u.getIdToken(true).await() }
                return true
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // تغيير البريد يلغي الجلسة القديمة، فنجرب ندخل بالبريد الجديد (ينجح بس إذا انتغير فعلاً)
            try {
                Repo.auth.signInWithEmailAndPassword(mail, pass).await()
                return true
            } catch (e2: Exception) {
                if (e2 is CancellationException) throw e2
            }
        }
        return false
    }

    suspend fun saveEmailDoc() {
        val u = Repo.auth.currentUser ?: return
        runCatching {
            FirebaseFirestore.getInstance().collection("users").document(u.uid)
                .set(mapOf("email" to newEmail.trim().lowercase()), SetOptions.merge()).await()
        }
    }

    fun sendLink(initial: Boolean) {
        error = null; info = null
        val mail = newEmail.trim().lowercase()
        if (initial) {
            if (!Patterns.EMAIL_ADDRESS.matcher(mail).matches()) { error = "اكتب بريد صحيح"; return }
            if (mail.equals(current, ignoreCase = true)) { error = "هذا هو بريدك الحالي"; return }
            if (pass.isBlank()) { error = "اكتب كلمة السر الحالية"; return }
        }
        scope.launch {
            loading = true
            try {
                val u = Repo.auth.currentUser ?: throw IllegalStateException("لازم تسجّل دخول")
                // تأكيد كلمة السر الحالية (حماية قبل تغيير البريد)
                u.reauthenticate(EmailAuthProvider.getCredential(current, pass)).await()
                u.verifyBeforeUpdateEmail(mail).await()
                cooldown = 45
                if (initial) step = 1 else info = "تم إرسال الرابط من جديد."
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = friendly(e)
            } finally {
                loading = false
            }
        }
    }

    // يفحص كل 3 ثواني تلقائياً إذا انضغط الرابط
    LaunchedEffect(step) {
        if (step == 1) {
            while (step == 1) {
                delay(3000)
                if (checkDone()) { saveEmailDoc(); step = 2 }
            }
        }
    }

    BackHandler(enabled = step == 1 && !loading) { step = 0; error = null; info = null }

    AnimatedContent(targetState = step, modifier = Modifier.fillMaxSize(), transitionSpec = { stepTransition() }, label = "emailStep") { s ->
        when (s) {
            0 -> StepColumn {
                StaggerIn(0, startDelay = 40L) {
                    FlowHeader(
                        AppIcons.Email, AccentBlue, SetViolet, "بريد جديد",
                        "اكتب بريدك الجديد وراح يوصله رابط تحقق. اضغط الرابط وينغيّر البريد."
                    )
                }
                StaggerIn(1, Modifier.fillMaxWidth(), startDelay = 40L) {
                    FlowPanel(bd) {
                        GlassField(
                            newEmail, { newEmail = it; error = null }, "البريد الجديد", AppIcons.Email, bd, dark,
                            keyboardType = KeyboardType.Email
                        )
                        Spacer(Modifier.height(12.dp))
                        GlassField(
                            pass, { pass = it; error = null }, "كلمة السر الحالية", AppIcons.Lock, bd, dark,
                            password = true, imeAction = ImeAction.Done, onDone = { sendLink(true) }
                        )
                        ErrorLine(error)
                        Spacer(Modifier.height(16.dp))
                        AcButton("إرسال رابط التحقق", bd, AccentBlue, loading) { sendLink(true) }
                    }
                }
            }
            1 -> StepColumn {
                StaggerIn(0, startDelay = 40L) {
                    FlowHeader(
                        AppIcons.Email, SetMint, AccentBlue, "تفقّد بريدك الجديد",
                        "أرسلنا رابط التحقق إلى:\n${ltr(newEmail.trim().lowercase())}\nاضغط الرابط (شوف مجلد Spam أيضاً) ثم ارجع هنا."
                    )
                }
                StaggerIn(1, Modifier.fillMaxWidth(), startDelay = 40L) {
                    FlowPanel(bd) {
                        Text(
                            "بعد ما تضغط الرابط راح يتأكد التطبيق تلقائياً ويكمل.",
                            fontSize = 14.sp, color = fg.copy(0.7f), textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        info?.let {
                            Text(it, color = AccentBlue, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                        }
                        ErrorLine(error)
                        Spacer(Modifier.height(16.dp))
                        AcButton("تحقّقت، تابع", bd, SetGreen, loading) {
                            scope.launch {
                                loading = true; error = null; info = null
                                try {
                                    if (checkDone()) { saveEmailDoc(); step = 2 }
                                    else error = "لسه ما انفعل. اضغط الرابط بالإيميل."
                                } finally { loading = false }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (cooldown > 0) "إعادة الإرسال بعد ${cooldown}ث" else "إعادة إرسال الرابط",
                            color = if (cooldown > 0) fg.copy(0.45f) else AccentBlue,
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                                .clickable(enabled = cooldown == 0 && !loading) { sendLink(false) }
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            }
            else -> SuccessView(bd, "تم تغيير بريدك", "بريدك الجديد: ${ltr(newEmail.trim().lowercase())}", onDone)
        }
    }
}

// ---------------------------------------------------------------------------
//  تغيير كلمة السر: رابط يوصل للبريد، تضغطه وتختار كلمة سر جديدة
// ---------------------------------------------------------------------------

@Composable
private fun ChangePasswordFlow(bd: Backdrop, onDone: () -> Unit) {
    val fg = MaterialTheme.colorScheme.onBackground
    val scope = rememberCoroutineScope()
    val current = remember { Repo.auth.currentUser?.email.orEmpty() }

    var step by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var cooldown by remember { mutableIntStateOf(0) }

    LaunchedEffect(cooldown) {
        if (cooldown > 0) { delay(1000); cooldown-- }
    }

    fun sendLink(initial: Boolean) {
        error = null; info = null
        scope.launch {
            loading = true
            try {
                Repo.auth.sendPasswordResetEmail(current).await()
                cooldown = 45
                if (initial) step = 1 else info = "تم إرسال الرابط من جديد."
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = friendly(e)
            } finally {
                loading = false
            }
        }
    }

    BackHandler(enabled = step == 1) { step = 0; error = null; info = null }

    AnimatedContent(targetState = step, modifier = Modifier.fillMaxSize(), transitionSpec = { stepTransition() }, label = "passStep") { s ->
        when (s) {
            0 -> StepColumn {
                StaggerIn(0, startDelay = 40L) {
                    FlowHeader(
                        AppIcons.Lock, SetPink, SetOrange, "كلمة سر جديدة",
                        "راح نرسل رابط لبريدك حتى تتأكد أنك صاحب الحساب وتختار كلمة سر جديدة."
                    )
                }
                StaggerIn(1, Modifier.fillMaxWidth(), startDelay = 40L) {
                    FlowPanel(bd) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.Email, null, tint = AccentBlue, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(ltr(current), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                        ErrorLine(error)
                        Spacer(Modifier.height(16.dp))
                        AcButton("إرسال رابط التحقق", bd, SetPink, loading) { sendLink(true) }
                    }
                }
            }
            else -> StepColumn {
                StaggerIn(0, startDelay = 40L) {
                    FlowHeader(
                        AppIcons.Email, SetMint, AccentBlue, "تفقّد بريدك",
                        "أرسلنا الرابط إلى:\n${ltr(current)}\nاضغطه (شوف مجلد Spam أيضاً) واختر كلمة السر الجديدة، وبعدها سجّل دخول فيها."
                    )
                }
                StaggerIn(1, Modifier.fillMaxWidth(), startDelay = 40L) {
                    FlowPanel(bd) {
                        info?.let {
                            Text(it, color = AccentBlue, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
                        }
                        ErrorLine(error)
                        AcButton("تم", bd, SetGreen, loading = false, onClick = onDone)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (cooldown > 0) "إعادة الإرسال بعد ${cooldown}ث" else "إعادة إرسال الرابط",
                            color = if (cooldown > 0) fg.copy(0.45f) else AccentBlue,
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                                .clickable(enabled = cooldown == 0 && !loading) { sendLink(false) }
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  تعطيل الحساب أو حذفه
// ---------------------------------------------------------------------------

@Composable
private fun ManageAccountPage(bd: Backdrop, onOpen: (AcPage) -> Unit) {
    val dark = isAppDark()
    StepColumn {
        StaggerIn(0, startDelay = 40L) {
            FlowHeader(
                AppIcons.Block, AcRed, SetOrange, "تعطيل أو حذف",
                "اختر ما تريد. العمليتين تحتاجان كلمة السر للتأكيد."
            )
        }
        StaggerIn(1, Modifier.fillMaxWidth().padding(vertical = 6.dp), startDelay = 40L, step = 80L) {
            AccountActionCard(
                "تعطيل الحساب", "يختفي عن المستخدمين وتگدر ترجعه بتسجيل الدخول",
                AppIcons.Pause, SetOrange, Color(0xFFFFC53D), bd, dark
            ) { onOpen(AcPage.Deactivate) }
        }
        StaggerIn(2, Modifier.fillMaxWidth().padding(vertical = 6.dp), startDelay = 40L, step = 80L) {
            AccountActionCard(
                "حذف الحساب", "حذف نهائي، ما يمكن استرجاعه",
                TrashIcon, AcRed, SetPink, bd, dark
            ) { onOpen(AcPage.Delete) }
        }
    }
}

/** مربع تأكيد زجاجي: علامة صح تظهر بنبضة */
@Composable
private fun ConfirmCheck(checked: Boolean, text: String, bd: Backdrop, onToggle: () -> Unit) {
    val dark = isAppDark()
    val fg = MaterialTheme.colorScheme.onBackground
    val p by animateFloatAsState(if (checked) 1f else 0f, spring(0.5f, 420f), label = "confirmCheck")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onToggle() }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(28.dp)
                .liquidCard(bd, RoundedCornerShape(9.dp), dark, borderColor = if (checked) AcRed else null)
                .drawBehind { drawRoundRect(AcRed.copy(alpha = 0.85f * p.coerceIn(0f, 1f)), cornerRadius = androidx.compose.ui.geometry.CornerRadius(9.dp.toPx())) },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(28.dp)) {
                val w = size.width
                val t = p.coerceIn(0f, 1f)
                if (t > 0.01f) {
                    val a = Offset(w * 0.26f, w * 0.52f)
                    val b = Offset(w * 0.43f, w * 0.68f)
                    val c = Offset(w * 0.74f, w * 0.34f)
                    drawLine(Color.White, a, b, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round, alpha = t)
                    drawLine(Color.White, b, c, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round, alpha = t)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 14.sp, color = fg.copy(0.85f), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DeactivateFlow(bd: Backdrop) {
    val dark = isAppDark()
    val scope = rememberCoroutineScope()
    var pass by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun go() {
        error = null
        if (pass.isBlank()) { error = "اكتب كلمة السر"; return }
        scope.launch {
            loading = true
            try {
                reauthWith(pass)
                Repo.deactivateAccount()
                AppToast.show("تم تعطيل حسابك. سجّل دخول بأي وقت لترجعه", long = true)
                Repo.signOut()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = friendly(e)
                loading = false
            }
        }
    }

    StepColumn {
        StaggerIn(0, startDelay = 40L) {
            FlowHeader(
                AppIcons.Pause, SetOrange, Color(0xFFFFC53D), "تعطيل الحساب",
                "حسابك يختفي عن كل المستخدمين (القوائم والبحث والملف الشخصي)، وتبقى بياناتك محفوظة. تسجّل دخول من جديد بأي وقت وحسابك يرجع."
            )
        }
        StaggerIn(1, Modifier.fillMaxWidth(), startDelay = 40L) {
            FlowPanel(bd) {
                GlassField(
                    pass, { pass = it; error = null }, "كلمة السر", AppIcons.Lock, bd, dark,
                    password = true, imeAction = ImeAction.Done, onDone = { go() }
                )
                ErrorLine(error)
                Spacer(Modifier.height(16.dp))
                AcButton("تعطيل حسابي", bd, SetOrange, loading) { go() }
            }
        }
    }
}

@Composable
private fun DeleteFlow(bd: Backdrop) {
    val dark = isAppDark()
    val fg = MaterialTheme.colorScheme.onBackground
    val scope = rememberCoroutineScope()
    var pass by remember { mutableStateOf("") }
    var sure by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun go() {
        error = null
        if (pass.isBlank()) { error = "اكتب كلمة السر"; return }
        if (!sure) { error = "لازم تأكد أنك فاهم أن الحذف نهائي"; return }
        scope.launch {
            loading = true
            try {
                reauthWith(pass)
                Repo.deleteAccountPermanently()
                AppToast.show("تم حذف حسابك نهائياً", long = true)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = friendly(e)
                loading = false
            }
        }
    }

    StepColumn {
        StaggerIn(0, startDelay = 40L) {
            FlowHeader(
                TrashIcon, AcRed, SetPink, "حذف الحساب نهائياً",
                "راح ينحذف ملفك وقصتك وصداقاتك ويوزرك وحساب الدخول، ولا يمكن استرجاعها أبداً."
            )
        }
        StaggerIn(1, Modifier.fillMaxWidth(), startDelay = 40L) {
            FlowPanel(bd) {
                Text(
                    "الرسائل اللي أرسلتها تبقى عند الطرف الثاني، ويظهر حسابك عندهم \"حساب محذوف\".",
                    fontSize = 13.sp, color = fg.copy(0.65f), modifier = Modifier.padding(bottom = 14.dp)
                )
                GlassField(
                    pass, { pass = it; error = null }, "كلمة السر", AppIcons.Lock, bd, dark,
                    password = true, imeAction = ImeAction.Done
                )
                Spacer(Modifier.height(10.dp))
                ConfirmCheck(sure, "أفهم أن الحذف نهائي ولا يمكن التراجع عنه", bd) { sure = !sure; error = null }
                ErrorLine(error)
                Spacer(Modifier.height(14.dp))
                AcButton("حذف حسابي نهائياً", bd, AcRed, loading) { go() }
            }
        }
    }
}
