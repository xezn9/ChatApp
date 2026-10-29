package com.chatapp.messenger.ui

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import com.kyant.backdrop.effects.lens
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.BackHandler
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.drawBackdrop
import android.os.Build
import android.view.ViewGroup
import android.graphics.drawable.ColorDrawable
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.filled.Menu
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.CompositionLocalProvider
import com.chatapp.messenger.Profile
import com.chatapp.messenger.Repo
import com.chatapp.messenger.Story
import com.chatapp.messenger.ui.glass.GlassButton
import com.chatapp.messenger.ui.theme.appBackground
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val StoryBlue = Brush.sweepGradient(
    listOf(Color(0xFF0A84FF), Color(0xFF00E5FF), Color(0xFF0A84FF))
)
internal val StoryColors = listOf(
    Color.White, Color.Black, Color(0xFFFFD60A), Color(0xFFFF453A),
    Color(0xFFFF2D92), Color(0xFF30D158), Color(0xFF00E5FF), Color(0xFFFF9F0A)
)

/** تبويب Account: ملف المستخدم (بانر، صورة، اسم، يوزر، نبذة) + القصة */
@Composable
fun AccountTab(
    onOverlayChange: (Boolean) -> Unit = {},
    /** فتح ملف شخصي لشخص (من قائمة الأصدقاء) */
    onOpenProfile: (com.chatapp.messenger.UserItem) -> Unit = {}
) {
    val me = Repo.auth.currentUser
    val myUid = me?.uid ?: ""
    val profile by remember(myUid) { Repo.profileFlow(myUid) }.collectAsState(initial = Profile())
    val fallbackName = me?.displayName?.takeIf { it.isNotBlank() } ?: me?.email?.substringBefore("@") ?: ""
    val name = profile.name.ifBlank { fallbackName }
    val story = profile.story

    // القصة تنتهي وتنحذف بعد 24 ساعة بالضبط من وقت نشرها
    var storyActive by remember(story?.createdAt) {
        mutableStateOf(story != null && System.currentTimeMillis() < story.expiresAt)
    }
    LaunchedEffect(story?.createdAt) {
        if (story != null) {
            // القصص المنشورة بنسخ قديمة تنضاف للفهرس العام حتى تظهر عند الكل
            if (System.currentTimeMillis() < story.expiresAt) runCatching { Repo.syncStoryIndex(story.createdAt) }
            val wait = story.expiresAt - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            storyActive = false
            runCatching { Repo.deleteStory() }
        }
    }

    var showEdit by remember { mutableStateOf(false) }
    var showStoryEditor by remember { mutableStateOf(false) }
    var showViewer by remember { mutableStateOf(false) }
    var showStatusEditor by remember { mutableStateOf(false) }
    var showFriends by remember { mutableStateOf(false) }
    var showBgSheet by remember { mutableStateOf(false) }
    // الصورة المعروضة بملء الشاشة (بانر أو صورة شخصية): null = مغلق
    var viewImage by remember { mutableStateOf<String?>(null) }
    var friendsCenter by remember { mutableStateOf<Offset?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val friends by remember(myUid) { Repo.friendsFlow(myUid) }.collectAsState(initial = emptyList())
    val requests by remember(myUid) { Repo.incomingRequestsFlow(myUid) }.collectAsState(initial = emptyList())
    // خلفية Account (محفوظة بالجهاز)
    var bgChoice by remember { mutableStateOf(AccountBgStore.choice(ctx)) }
    var bgVersion by remember { mutableStateOf(0) }
    val customBg by androidx.compose.runtime.produceState<ImageBitmap?>(null, bgVersion) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val f = AccountBgStore.customFile(ctx)
                if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
            }.getOrNull()
        }
    }
    val pickBg = rememberImagePicker(maxSide = 1600, quality = 80) { b64 ->
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { AccountBgStore.customFile(ctx).writeBytes(android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)) }
            AccountBgStore.setChoice(ctx, ACCOUNT_BG_CUSTOM)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                bgChoice = ACCOUNT_BG_CUSTOM
                bgVersion++
                showBgSheet = false
            }
        }
    }
    var statusSeed by remember { mutableStateOf<com.chatapp.messenger.Status?>(null) }
    // نسخة ثابتة من القصة تبقى طول حركة الإغلاق (حتى لو انحذفت أو تغيّرت القصة الأصلية)
    var editorSeed by remember { mutableStateOf<Story?>(null) }
    var viewerSeed by remember { mutableStateOf<Story?>(null) }

    // مواقع زر + والصورة الشخصية: منها تطلع الواجهات وإلها ترجع
    val rootView = LocalView.current
    var plusCenter by remember { mutableStateOf<Offset?>(null) }
    var avatarCenter by remember { mutableStateOf<Offset?>(null) }
    fun originOf(c: Offset?, fallback: TransformOrigin): TransformOrigin =
        if (c == null || rootView.width <= 0 || rootView.height <= 0) fallback
        else TransformOrigin(
            (c.x / rootView.width).coerceIn(0f, 1f),
            (c.y / rootView.height).coerceIn(0f, 1f)
        )

    // نخبر الشاشة الرئيسية حتى تخفي الشريط السفلي أثناء التعديل
    val overlay = showEdit || showStoryEditor || showViewer || showStatusEditor || showFriends || showBgSheet || viewImage != null
    LaunchedEffect(overlay) { onOverlayChange(overlay) }
    DisposableEffect(Unit) { onDispose { onOverlayChange(false) } }

    val bannerImg = rememberB64Image(profile.banner)
    val avatarImg = rememberB64Image(profile.avatar)
    val gray = Color(0xFFB0B3B8)

    // الأحجام كلها نسبة من عرض الشاشة (مرجع التصميم: عرض 1440) حتى تطلع نفس الصورة بكل الأجهزة
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val u = maxWidth / 1440f
            val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            // البانر مثل تيك توك: قصير، يبدأ من أعلى الشاشة (خلف شريط الحالة) ويذوب بالخلفية
            val bannerH = u * 620f + statusTop
            val glass = rememberLayerBackdrop()

            Box(Modifier.fillMaxSize().layerBackdrop(glass)) {
                AccountBackground(bgChoice, customBg, Modifier.matchParentSize())
                Box(
                    Modifier.fillMaxWidth().height(bannerH)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.verticalGradient(
                                    0f to Color.Black,
                                    0.6f to Color.Black,
                                    1f to Color.Transparent
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                ) {
                    if (bannerImg != null) {
                        Image(bannerImg, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    } else {
                        Box(
                            Modifier.matchParentSize().background(
                                Brush.verticalGradient(listOf(Color(0xFF3B4A57), Color(0xFF14181D)))
                            )
                        )
                    }
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(0.30f),
                                0.5f to Color.Black.copy(0.05f),
                                1f to Color.Black.copy(0.25f)
                            )
                        )
                    )
                }
            }

            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(
                    Modifier.fillMaxWidth().statusBarsPadding()
                        .heightIn(min = u * 900f)
                        .padding(bottom = 140.dp)
                        .navigationBarsPadding()
                ) {
                    // ضغطة عادية أو مطولة على البانر = يفتح الصورة بملء الشاشة (إذا فيه بانر)
                    Box(
                        Modifier.fillMaxWidth().height(u * 620f).align(Alignment.TopStart)
                            .pointerInput(profile.banner) {
                                detectTapGestures(
                                    onTap = { if (profile.banner.isNotBlank()) viewImage = profile.banner },
                                    onLongPress = { if (profile.banner.isNotBlank()) viewImage = profile.banner }
                                )
                            }
                    )
                    // الأزرار العلوية: قائمة يسار، قلم يمين
                    Row(
                        Modifier.fillMaxWidth().padding(start = u * 22f, end = u * 20f, top = u * 20f),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        RoundIconButton(Icons.Filled.Menu, glass, u * 150f) { showBgSheet = true }
                        RoundIconButton(Icons.Filled.Edit, glass, u * 150f) { showEdit = true }
                    }
                    // الصورة الشخصية يسار
                    Box(Modifier.align(Alignment.TopStart).padding(start = u * 47f, top = u * 352f)) {
                        AvatarWithStory(
                            avatar = avatarImg,
                            hasStory = storyActive,
                            backdrop = glass,
                            u = u,
                            onAvatarClick = {
                                if (storyActive && story != null) {
                                    viewerSeed = story
                                    showViewer = true
                                } else if (profile.avatar.isNotBlank()) {
                                    viewImage = profile.avatar
                                }
                            },
                            onAvatarLongClick = { if (profile.avatar.isNotBlank()) viewImage = profile.avatar },
                            onPlus = {
                                editorSeed = if (storyActive) story else null
                                showStoryEditor = true
                            },
                            onPlusPos = { plusCenter = it },
                            onAvatarPos = { avatarCenter = it }
                        )
                    }
                    // الحالة النصية: فقاعة فوق الصورة (ضغطة = كتابة/تعديل/حذف)
                    val activeStatus = profile.status?.takeIf { System.currentTimeMillis() < it.expiresAt }
                    Box(
                        Modifier.align(Alignment.TopStart)
                            .padding(start = u * 60f, top = u * 190f)
                            .height(u * 240f),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        StatusBubble(activeStatus?.text, glass, u) {
                            statusSeed = activeStatus
                            showStatusEditor = true
                        }
                    }
                    // الاسم واليوزر يمين
                    Column(
                        Modifier.align(Alignment.TopEnd).padding(end = u * 52f, top = u * 445f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            name, color = Color.White, fontSize = (u.value * 86f).sp,
                            fontWeight = FontWeight.Bold, textAlign = TextAlign.End
                        )
                        if (profile.username.isNotBlank()) {
                            Text(
                                "@${profile.username}", color = gray, fontSize = (u.value * 44f).sp,
                                fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = u * 8f)
                            )
                        }
                    }
                    // النبذة + زر الأصدقاء (يتحرك تحت النبذة)
                    Column(
                        Modifier.align(Alignment.TopEnd).fillMaxWidth()
                            .padding(top = u * 705f, end = u * 35f, start = u * 35f),
                        horizontalAlignment = Alignment.End
                    ) {
                        if (profile.bio.isNotBlank()) {
                            Text(
                                profile.bio, color = Color.White, fontSize = (u.value * 52f).sp,
                                textAlign = TextAlign.End
                            )
                            Spacer(Modifier.height(18.dp))
                        }
                        Box(Modifier.onGloballyPositioned { friendsCenter = it.boundsInRoot().center }) {
                            GlassButton(
                                onClick = { showFriends = true },
                                backdrop = glass,
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.padding(start = 18.dp).size(22.dp))
                                Text(
                                    "الأصدقاء", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                                Text(
                                    "${friends.size}", color = Color(0xFF7FD4FF), fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 8.dp, end = 20.dp)
                                )
                            }
                            // نقطة حمراء بعدد طلبات الصداقة الجديدة
                            if (requests.isNotEmpty()) {
                                Box(
                                    Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp)
                                        .size(22.dp).clip(CircleShape).background(Color(0xFFFF453A)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${requests.size}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // الشاشة تتفتح وتكبر من زر القلم (أعلى اليمين) وترجع تصغر إله لما تسكّرها
    val penOrigin = TransformOrigin(0.93f, 0.075f)
    AnimatedVisibility(
        visible = showEdit,
        enter = scaleIn(spring(dampingRatio = 0.84f, stiffness = 300f), initialScale = 0.03f, transformOrigin = penOrigin) +
            fadeIn(tween(200)),
        exit = scaleOut(tween(300, easing = FastOutSlowInEasing), targetScale = 0.03f, transformOrigin = penOrigin) +
            fadeOut(tween(230, delayMillis = 40))
    ) {
        // الحواف تبدأ دائرية كبيرة (مثل الزر) وتتحول تدريجياً لشاشة كاملة
        val edge by transition.animateFloat(
            transitionSpec = { tween(320, easing = FastOutSlowInEasing) },
            label = "editEdge"
        ) { if (it == EnterExitState.Visible) 0f else 1f }
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                shape = RoundedCornerShape((edge * 120f).dp)
                clip = edge > 0.001f
            }
        ) {
            EditProfileDialog(
                initName = name,
                initUsername = profile.username,
                initBio = profile.bio,
                initAvatar = profile.avatar,
                initBanner = profile.banner,
                onDismiss = { showEdit = false }
            )
        }
    }
    // نافذة الحالة النصية
    AnimatedVisibility(
        visible = showStatusEditor,
        enter = fadeIn(tween(150)) + scaleIn(spring(0.85f, 420f), initialScale = 0.85f, transformOrigin = TransformOrigin(0.3f, 0.3f)),
        exit = fadeOut(tween(130)) + scaleOut(tween(150), targetScale = 0.9f, transformOrigin = TransformOrigin(0.3f, 0.3f))
    ) {
        StatusEditorSheet(existing = statusSeed, onDismiss = { showStatusEditor = false })
    }
    // شاشة نشر القصة: تطلع من زر + وترجع إله
    OriginOverlay(
        visible = showStoryEditor,
        origin = originOf(plusCenter, TransformOrigin(0.08f, 0.30f))
    ) {
        StoryEditorScreen(existing = editorSeed, onDismiss = { showStoryEditor = false })
    }
    // عرض القصة: يطلع من الصورة الشخصية ويرجع إلها
    OriginOverlay(
        visible = showViewer,
        origin = originOf(avatarCenter, TransformOrigin(0.18f, 0.27f)),
        startRadius = 200f
    ) {
        viewerSeed?.let { st ->
            StoryViewerScreen(
                story = st,
                owner = profile,
                mine = true,
                showMenu = true,
                onDismiss = { showViewer = false },
                onEdit = {
                    // نسكّر العارض أول، وبعدها يطلع المحرر من زر +
                    showViewer = false
                    scope.launch {
                        delay(330)
                        editorSeed = st
                        showStoryEditor = true
                    }
                }
            )
        }
    }
    // شاشة الأصدقاء: تنبعث من زر الأصدقاء وترجع إله
    OriginOverlay(
        visible = showFriends,
        origin = originOf(friendsCenter, TransformOrigin(0.85f, 0.55f))
    ) {
        FriendsScreen(
            onDismiss = { showFriends = false },
            onOpenProfile = onOpenProfile
        )
    }
    // لوحة اختيار خلفية Account
    AnimatedVisibility(
        visible = showBgSheet,
        enter = androidx.compose.animation.slideInVertically(spring(0.85f, 400f)) { it } + fadeIn(tween(180)),
        exit = androidx.compose.animation.slideOutVertically(tween(220)) { it } + fadeOut(tween(160))
    ) {
        BackHandler { showBgSheet = false }
        val sheetBackdrop = rememberLayerBackdrop()
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.matchParentSize().layerBackdrop(sheetBackdrop)) {
                AccountBackground(bgChoice, customBg, Modifier.matchParentSize())
            }
            AccountBgSheet(
                choice = bgChoice,
                custom = customBg,
                backdrop = sheetBackdrop,
                onPick = { i ->
                    bgChoice = i
                    AccountBgStore.setChoice(ctx, i)
                },
                onPickCustom = {
                    // إذا عندك صورة محفوظة من قبل تنطبق مباشرة، وإلا يفتح المعرض
                    if (customBg != null && bgChoice != ACCOUNT_BG_CUSTOM) {
                        bgChoice = ACCOUNT_BG_CUSTOM
                        AccountBgStore.setChoice(ctx, ACCOUNT_BG_CUSTOM)
                    } else pickBg()
                },
                onDismiss = { showBgSheet = false }
            )
        }
    }
    // عرض البانر / الصورة الشخصية بملء الشاشة مع حفظ بالمعرض
    ImageViewerHost(viewImage) { viewImage = null }
}


/** غلاف حركة: الشاشة تكبر من نقطة (زر) وترجع تصغر إلها، وحوافها تتحول من دائرية لشاشة كاملة */
@Composable
internal fun OriginOverlay(
    visible: Boolean,
    origin: TransformOrigin,
    startRadius: Float = 120f,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(spring(dampingRatio = 0.84f, stiffness = 300f), initialScale = 0.03f, transformOrigin = origin) +
            fadeIn(tween(200)),
        exit = scaleOut(tween(300, easing = FastOutSlowInEasing), targetScale = 0.03f, transformOrigin = origin) +
            fadeOut(tween(230, delayMillis = 40))
    ) {
        val edge by transition.animateFloat(
            transitionSpec = { tween(320, easing = FastOutSlowInEasing) },
            label = "overlayEdge"
        ) { if (it == EnterExitState.Visible) 0f else 1f }
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                shape = RoundedCornerShape((edge * startRadius).dp)
                clip = edge > 0.001f
            }
        ) { content() }
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    backdrop: Backdrop,
    size: Dp,
    onClick: () -> Unit
) {
    GlassButton(onClick = onClick, backdrop = backdrop, modifier = Modifier.size(size)) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.52f))
    }
}

/** صورة الملف الشخصي: حلقة رمادية (زرقاء إذا فيه قصة) + زر + زجاجي لإضافة قصة */
@Composable
private fun AvatarWithStory(
    avatar: ImageBitmap?,
    hasStory: Boolean,
    backdrop: Backdrop,
    u: Dp,
    onAvatarClick: () -> Unit,
    onAvatarLongClick: () -> Unit = {},
    onPlus: () -> Unit,
    onPlusPos: (Offset) -> Unit = {},
    onAvatarPos: (Offset) -> Unit = {}
) {
    val clickNow by rememberUpdatedState(onAvatarClick)
    val longClickNow by rememberUpdatedState(onAvatarLongClick)
    Box(Modifier.size(u * 375f).onGloballyPositioned { onAvatarPos(it.boundsInRoot().center) }) {
        val ringW = u * 14f
        val ring = if (hasStory) Modifier.border(ringW, StoryBlue, CircleShape)
        else Modifier.border(ringW, Color(0xFF4F4F4F), CircleShape)
        Box(
            Modifier.fillMaxSize().then(ring).padding(ringW).background(Color.Black, CircleShape)
                .padding(u * 6f).clip(CircleShape)
                .background(Color(0xFF2B2F36))
                .pointerInput(Unit) { detectTapGestures(onTap = { clickNow() }, onLongPress = { longClickNow() }) },
            contentAlignment = Alignment.Center
        ) {
            if (avatar != null) {
                Image(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Filled.Person, null, tint = Color(0xFF8A8F98), modifier = Modifier.size(u * 160f))
            }
        }
        GlassButton(
            onClick = onPlus,
            backdrop = backdrop,
            tint = Color(0xFF1AA7FF),
            modifier = Modifier.align(Alignment.TopStart).offset(x = u * 15f, y = u * 271f)
                .size(u * 88f).border(u * 6f, Color.White, CircleShape)
                .onGloballyPositioned { onPlusPos(it.boundsInRoot().center) }
        ) {
            Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(u * 52f))
        }
    }
}

/** يفتح معرض الصور، يضغط الصورة ويرجعها كنص base64 */
@Composable
internal fun rememberImagePicker(maxSide: Int, quality: Int, onPicked: (String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cb by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val b64 = ImageUtil.uriToB64(ctx, uri, maxSide, quality)
            if (b64 != null) cb(b64) else Toast.makeText(ctx, "تعذّر قراءة الصورة", Toast.LENGTH_SHORT).show()
        }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/** خلفية الديالوجات: تدرج التطبيق + باكدروب تستخدمه الأزرار الزجاجية */
@Composable
private fun GlassDialogRoot(content: @Composable (Backdrop) -> Unit) {
    val bd = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().layerBackdrop(bd).background(appBackground()))
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            content(bd)
        }
    }
}

/** باج/حقل بستايل iOS 16 (بطاقة مدورة، عنوان صغير فوق القيمة) + زجاج سائل */
@Composable
private fun GlassField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = 1
) {
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(20.dp)
    val fg = MaterialTheme.colorScheme.onBackground
    Column(
        modifier.fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(10f.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(if (dark) Color.White.copy(0.08f) else Color.White.copy(0.45f))
                }
            )
            .border(
                0.8.dp,
                Brush.verticalGradient(listOf(Color.White.copy(0.40f), Color.White.copy(0.06f))),
                shape
            )
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = fg.copy(alpha = 0.6f))
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (prefix != null) Text(prefix, fontSize = 17.sp, color = fg.copy(alpha = 0.6f))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                maxLines = maxLines,
                textStyle = TextStyle(color = fg, fontSize = 17.sp),
                cursorBrush = SolidColor(Color(0xFF1AA7FF)),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ======================= تعديل الحساب (زر القلم) =======================

internal val AccentBlue = Color(0xFF1AA7FF)
private val AccentViolet = Color(0xFF8A5CFF)
private val AccentMint = Color(0xFF00D4AA)

/** بطاقة زجاج سائل: تمويه + عدسة (انكسار الحواف) + حد لامع */
internal fun Modifier.liquidCard(
    backdrop: Backdrop,
    shape: Shape,
    dark: Boolean,
    borderWidth: Dp = 0.9.dp,
    borderColor: Color? = null
): Modifier {
    val brush: Brush = if (borderColor != null) SolidColor(borderColor)
    else Brush.verticalGradient(listOf(Color.White.copy(0.55f), Color.White.copy(0.08f)))
    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(14f.dp.toPx())
                lens(10f.dp.toPx(), 20f.dp.toPx())
            },
            onDrawSurface = { drawRect(if (dark) Color.Black.copy(0.26f) else Color.White.copy(0.42f)) }
        )
        .border(borderWidth, brush, shape)
}

/** خلفية الشاشة: تدرج التطبيق + صورة البانر الذايبة + فقاعات ألوان تتحرك ببطء، حتى يبين تأثير الزجاج */
@Composable
internal fun LiquidScene(banner: ImageBitmap?, content: @Composable (Backdrop) -> Unit) {
    val bd = rememberLayerBackdrop()
    val t = rememberInfiniteTransition(label = "blobs")
    val p by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse),
        label = "blobsP"
    )
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().layerBackdrop(bd).background(appBackground())) {
            if (banner != null) {
                Image(
                    banner, null,
                    Modifier.fillMaxWidth().height(360.dp)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = 0.85f }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.verticalGradient(0f to Color.Black, 0.5f to Color.Black, 1f to Color.Transparent),
                                blendMode = BlendMode.DstIn
                            )
                        },
                    contentScale = ContentScale.Crop
                )
            }
            Canvas(Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                fun blob(c: Color, cx: Float, cy: Float, r: Float) {
                    drawCircle(
                        Brush.radialGradient(listOf(c.copy(alpha = 0.55f), Color.Transparent), center = Offset(cx, cy), radius = r),
                        r, Offset(cx, cy)
                    )
                }
                blob(AccentBlue, w * (0.15f + 0.5f * p), h * (0.30f + 0.10f * p), w * 0.65f)
                blob(AccentViolet, w * (0.85f - 0.45f * p), h * (0.58f - 0.10f * p), w * 0.60f)
                blob(AccentMint, w * (0.30f + 0.30f * p), h * (0.88f - 0.15f * p), w * 0.65f)
            }
        }
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            content(bd)
        }
    }
}

/** حقل بزجاج سائل: أيقونة ملونة، عنوان صغير، عداد أحرف، وتوهج لما تضغط عليه */
@Composable
private fun LiquidField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    backdrop: Backdrop,
    tint: Color,
    maxLen: Int,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconText: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    minLines: Int = 1
) {
    val dark = isSystemInDarkTheme()
    val fg = MaterialTheme.colorScheme.onBackground
    val shape = RoundedCornerShape(26.dp)
    var focused by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    val glow by animateFloatAsState(if (focused) 1f else 0f, spring(0.8f, 400f), label = "fieldGlow")
    val pop by animateFloatAsState(if (focused) 1.015f else 1f, spring(0.6f, 450f), label = "fieldPop")
    val labelColor by animateColorAsState(if (focused) tint else fg.copy(alpha = 0.6f), tween(200), label = "fieldLabel")

    Row(
        modifier.fillMaxWidth()
            .graphicsLayer { scaleX = pop; scaleY = pop }
            .liquidCard(
                backdrop, shape, dark,
                borderWidth = (0.9f + 1.1f * glow).dp,
                borderColor = if (glow > 0.01f) lerp(Color.White.copy(alpha = 0.30f), tint, glow) else null
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { fr.requestFocus() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(tint.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            else Text(iconText ?: "", color = tint, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = labelColor)
            Spacer(Modifier.height(3.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                maxLines = maxLines,
                minLines = minLines,
                textStyle = TextStyle(color = fg, fontSize = 17.sp),
                cursorBrush = SolidColor(tint),
                modifier = Modifier.fillMaxWidth().focusRequester(fr).onFocusChanged { focused = it.isFocused }
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${value.length}/$maxLen", fontSize = 11.sp,
            color = fg.copy(alpha = if (focused) 0.7f else 0.4f)
        )
    }
}

@Composable
private fun EditProfileDialog(
    initName: String,
    initUsername: String,
    initBio: String,
    initAvatar: String,
    initBanner: String,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dark = isSystemInDarkTheme()
    val fg = MaterialTheme.colorScheme.onBackground
    var name by remember { mutableStateOf(initName) }
    var username by remember { mutableStateOf(initUsername) }
    var bio by remember { mutableStateOf(initBio) }
    var avatarB64 by remember { mutableStateOf(initAvatar) }
    var bannerB64 by remember { mutableStateOf(initBanner) }
    var saving by remember { mutableStateOf(false) }

    val pickBanner = rememberImagePicker(maxSide = 1000, quality = 70) { bannerB64 = it }
    var cropSrc by remember { mutableStateOf<String?>(null) }
    // نختار الصورة بدقة أعلى ثم المستخدم يحرّكها ويقص الجزء المطلوب
    val pickAvatar = rememberImagePicker(maxSide = 1000, quality = 85) { cropSrc = it }
    val banner = rememberB64Image(bannerB64)
    val avatar = rememberB64Image(avatarB64)

    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        LiquidScene(banner) { bd ->
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // ---- الشريط العلوي ----
                StaggerIn(0, Modifier.fillMaxWidth(), startDelay = 120L) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        GlassButton(onClick = onDismiss, backdrop = bd, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.Close, null, modifier = Modifier.size(22.dp))
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text("تعديل الحساب", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            Text("خصّص ملفك الشخصي", fontSize = 12.sp, color = fg.copy(alpha = 0.6f))
                        }
                        GlassButton(
                            backdrop = bd, tint = AccentBlue, modifier = Modifier.height(48.dp),
                            enabled = !saving && name.isNotBlank(),
                            onClick = {
                                saving = true
                                scope.launch {
                                    try {
                                        Repo.saveProfileInfo(name.trim(), username.trim(), bio.trim(), avatarB64, bannerB64)
                                        onDismiss()
                                    } catch (e: Exception) {
                                        saving = false
                                        Toast.makeText(ctx, "فشل الحفظ: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        ) {
                            if (!saving) {
                                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                if (saving) "..." else "حفظ", color = Color.White, fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = if (saving) 26.dp else 0.dp, end = 22.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ---- البانر + الصورة الشخصية ----
                StaggerIn(1, Modifier.fillMaxWidth(), startDelay = 120L) {
                    Box(Modifier.fillMaxWidth().height(226.dp)) {
                        val bannerShape = RoundedCornerShape(32.dp)
                        Box(
                            Modifier.fillMaxWidth().height(172.dp)
                                .liquidCard(bd, bannerShape, dark)
                                .clip(bannerShape)
                                .clickable(onClick = pickBanner),
                            contentAlignment = Alignment.Center
                        ) {
                            if (banner != null) {
                                Image(banner, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                Box(
                                    Modifier.matchParentSize().background(
                                        Brush.verticalGradient(0f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color.Black.copy(0.35f))
                                    )
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(AppIcons.Image, null, tint = fg.copy(alpha = 0.7f), modifier = Modifier.size(34.dp))
                                    Spacer(Modifier.height(6.dp))
                                    Text("اضغط لاختيار صورة البانر", fontSize = 14.sp, color = fg.copy(alpha = 0.75f))
                                }
                            }
                            Row(
                                Modifier.align(Alignment.TopEnd).padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (bannerB64.isNotEmpty()) {
                                    GlassButton(
                                        onClick = { bannerB64 = "" }, backdrop = bd, tint = Color(0xFFE5484D),
                                        modifier = Modifier.size(40.dp)
                                    ) { Icon(Icons.Filled.Delete, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                                }
                                GlassButton(onClick = pickBanner, backdrop = bd, modifier = Modifier.height(40.dp)) {
                                    Icon(AppIcons.Image, null, tint = Color.White, modifier = Modifier.padding(start = 14.dp).size(18.dp))
                                    Text(
                                        if (bannerB64.isEmpty()) "إضافة" else "تغيير", color = Color.White,
                                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(start = 6.dp, end = 16.dp)
                                    )
                                }
                            }
                        }

                        // الصورة الشخصية تتداخل مع أسفل البانر
                        Box(Modifier.align(Alignment.BottomStart).padding(start = 22.dp)) {
                            Box(
                                Modifier.size(108.dp)
                                    .border(
                                        3.dp,
                                        Brush.sweepGradient(listOf(AccentBlue, Color(0xFF00E5FF), AccentViolet, AccentBlue)),
                                        CircleShape
                                    )
                                    .padding(5.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2B2F36))
                                    .clickable(onClick = pickAvatar),
                                contentAlignment = Alignment.Center
                            ) {
                                if (avatar != null) Image(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                else Icon(Icons.Filled.Person, null, tint = Color(0xFF8A8F98), modifier = Modifier.size(48.dp))
                            }
                            GlassButton(
                                onClick = pickAvatar, backdrop = bd, tint = AccentBlue,
                                modifier = Modifier.align(Alignment.BottomEnd).size(36.dp).border(2.5.dp, Color.White, CircleShape)
                            ) { Icon(Icons.Filled.Edit, null, tint = Color.White, modifier = Modifier.size(17.dp)) }
                        }

                        if (avatarB64.isNotEmpty()) {
                            Row(
                                Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                GlassButton(onClick = { cropSrc = avatarB64 }, backdrop = bd, modifier = Modifier.height(40.dp)) {
                                    Text("تحريك", fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 16.dp))
                                }
                                GlassButton(
                                    onClick = { avatarB64 = "" }, backdrop = bd, tint = Color(0xFFE5484D),
                                    modifier = Modifier.height(40.dp)
                                ) {
                                    Text("إزالة", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 16.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                StaggerIn(2, Modifier.fillMaxWidth(), startDelay = 120L) {
                    LiquidField(
                        label = "اسم الحساب", value = name, backdrop = bd, tint = AccentBlue, maxLen = 30,
                        icon = Icons.Filled.Person,
                        onValueChange = { name = it.take(30) }
                    )
                }
                Spacer(Modifier.height(12.dp))
                StaggerIn(3, Modifier.fillMaxWidth(), startDelay = 120L) {
                    LiquidField(
                        label = "اسم المستخدم (اليوزر)", value = username, backdrop = bd, tint = AccentViolet, maxLen = 24,
                        iconText = "@",
                        onValueChange = { v ->
                            username = v.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '.' }.take(24)
                        }
                    )
                }
                Spacer(Modifier.height(12.dp))
                StaggerIn(4, Modifier.fillMaxWidth(), startDelay = 120L) {
                    LiquidField(
                        label = "النبذة", value = bio, backdrop = bd, tint = AccentMint, maxLen = 150,
                        icon = AppIcons.ChatBubble, singleLine = false, maxLines = 4, minLines = 2,
                        onValueChange = { bio = it.take(150) }
                    )
                }
                Spacer(Modifier.height(28.dp))
            }
        }
        val src = cropSrc
        if (src != null) {
            AvatarCropOverlay(srcB64 = src, onCancel = { cropSrc = null }, onDone = { avatarB64 = it; cropSrc = null })
        }
    }
}

/** تحريك/تقريب الصورة داخل دائرة واختيار الجزء المطلوب (ضمن حدود الصورة) */
@Composable
private fun AvatarCropOverlay(srcB64: String, onCancel: () -> Unit, onDone: (String) -> Unit) {
    BackHandler(onBack = onCancel)
    val bmp = rememberB64Image(srcB64)
    var zoom by remember { mutableFloatStateOf(1f) }
    var off by remember { mutableStateOf(Offset.Zero) }
    var viewPx by remember { mutableFloatStateOf(0f) }

    // يمنع خروج الصورة عن حدود الدائرة
    fun clampOff(o: Offset, z: Float): Offset {
        val b = bmp ?: return Offset.Zero
        if (viewPx <= 0f) return o
        val s = kotlin.math.max(viewPx / b.width, viewPx / b.height) * z
        val mx = kotlin.math.max(0f, (b.width * s - viewPx) / 2f)
        val my = kotlin.math.max(0f, (b.height * s - viewPx) / 2f)
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }

    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        GlassDialogRoot { bd ->
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassButton(onClick = onCancel, backdrop = bd, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Close, null)
                    }
                    Text("حرّك الصورة", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                    GlassButton(
                        backdrop = bd, tint = Color(0xFF1AA7FF), modifier = Modifier.height(40.dp),
                        enabled = bmp != null,
                        onClick = {
                            val b = bmp
                            if (b != null && viewPx > 0f) {
                                val out = 400
                                val k = out / viewPx
                                val s = kotlin.math.max(viewPx / b.width, viewPx / b.height) * zoom
                                val left = (viewPx - b.width * s) / 2f + off.x
                                val top = (viewPx - b.height * s) / 2f + off.y
                                val result = android.graphics.Bitmap.createBitmap(out, out, android.graphics.Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(result)
                                canvas.drawColor(android.graphics.Color.BLACK)
                                val m = android.graphics.Matrix()
                                m.postScale(s * k, s * k)
                                m.postTranslate(left * k, top * k)
                                canvas.drawBitmap(b.asAndroidBitmap(), m, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                                val os = java.io.ByteArrayOutputStream()
                                result.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, os)
                                onDone(android.util.Base64.encodeToString(os.toByteArray(), android.util.Base64.NO_WRAP))
                            }
                        }
                    ) { Text("تم", color = Color.White, modifier = Modifier.padding(horizontal = 22.dp)) }
                }

                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val side = minOf(maxWidth - 16.dp, maxHeight, 340.dp)
                    Box(
                        Modifier.size(side)
                            .onSizeChanged { viewPx = it.width.toFloat() }
                            .pointerInput(bmp) {
                                detectTransformGestures { _, pan, zm, _ ->
                                    val nz = (zoom * zm).coerceIn(1f, 4f)
                                    zoom = nz
                                    off = clampOff(off + pan, nz)
                                }
                            }
                    ) {
                        Canvas(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFF2B2F36))) {
                            val b = bmp
                            if (b != null) {
                                val sz = size.width
                                val s = kotlin.math.max(sz / b.width, sz / b.height) * zoom
                                val dw = b.width * s
                                val dh = b.height * s
                                val left = (sz - dw) / 2f + off.x
                                val top = (sz - dh) / 2f + off.y
                                drawImage(
                                    b,
                                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                                    dstSize = IntSize(dw.roundToInt(), dh.roundToInt())
                                )
                            }
                        }
                        Box(Modifier.fillMaxSize().border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape))
                    }
                }
                Text(
                    "اسحب لتحريك الصورة، وقرّب بإصبعين للتكبير",
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }
    }
}
