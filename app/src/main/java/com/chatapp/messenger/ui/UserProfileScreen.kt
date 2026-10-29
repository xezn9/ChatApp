package com.chatapp.messenger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Check
import com.chatapp.messenger.Relation
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.Profile
import com.chatapp.messenger.Repo
import com.chatapp.messenger.UserItem
import com.chatapp.messenger.ui.glass.GlassButton
import com.chatapp.messenger.ui.theme.appBackground
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** بروفايل مستخدم ثاني (قراءة فقط) بنفس تصميم Account + زر مراسلة */
@Composable
fun UserProfileScreen(user: UserItem, onBack: () -> Unit, onMessage: () -> Unit) {
    val profile by remember(user.uid) { Repo.profileFlow(user.uid) }.collectAsState(initial = Profile())
    val name = profile.name.ifBlank { user.name }.ifBlank { "مستخدم" }
    val username = profile.username.ifBlank { user.username }
    val bannerImg = rememberB64Image(profile.banner)
    val avatarImg = rememberB64Image(profile.avatar)
    val gray = Color(0xFFB0B3B8)

    // قصة هذا الشخص (إذا فعّالة): حلقة زرقاء حول صورته وضغطة تفتحها
    val story = profile.story
    val hasStory = story != null && !profile.restricted && System.currentTimeMillis() < story.expiresAt
    var showStory by remember { mutableStateOf(false) }
    // الصورة المعروضة بملء الشاشة (بانر أو صورة شخصية): null = مغلق
    var viewImage by remember { mutableStateOf<String?>(null) }

    // الصداقة: أضيفه / ألغي الطلب / أقبل أو أرفض / ألغي الصداقة
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val myUid = Repo.auth.currentUser?.uid ?: ""
    val isMe = user.uid == myUid
    val relation by remember(user.uid) { Repo.relationFlow(user.uid) }.collectAsState(initial = Relation.NONE)
    var relBusy by remember { mutableStateOf(false) }
    var confirmUnfriend by remember { mutableStateOf(false) }
    fun relAction(block: suspend () -> Unit) {
        if (relBusy) return
        relBusy = true
        scope.launch {
            try { block() } catch (e: Exception) {
                Toast.makeText(ctx, "فشلت العملية: ${e.message}", Toast.LENGTH_LONG).show()
            }
            relBusy = false
        }
    }

    BackHandler(enabled = !showStory && !confirmUnfriend, onBack = onBack)
    BackHandler(enabled = confirmUnfriend) { confirmUnfriend = false }

    Box(Modifier.fillMaxSize()) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(appBackground())
                // يمنع الضغطات من النفاذ للقائمة الي تحته
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            val u = maxWidth / 1440f
            val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val bannerH = u * 620f + statusTop
            val glass = rememberLayerBackdrop()

            Box(Modifier.fillMaxSize().layerBackdrop(glass).background(appBackground())) {
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
                        .padding(bottom = 40.dp)
                        .navigationBarsPadding()
                ) {
                    // ضغطة عادية أو مطولة على البانر = يفتح الصورة بملء الشاشة
                    Box(
                        Modifier.fillMaxWidth().height(u * 620f).align(Alignment.TopStart)
                            .pointerInput(profile.banner, profile.restricted) {
                                detectTapGestures(
                                    onTap = { if (profile.banner.isNotBlank() && !profile.restricted) viewImage = profile.banner },
                                    onLongPress = { if (profile.banner.isNotBlank() && !profile.restricted) viewImage = profile.banner }
                                )
                            }
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        GlassButton(onClick = onBack, backdrop = glass, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                    // الصورة الشخصية
                    Box(
                        Modifier.align(Alignment.TopStart)
                            .padding(start = u * 47f, top = u * 352f)
                            .size(u * 375f)
                            .border(u * 14f, if (hasStory) StoryBlue else SolidColor(Color(0xFF4F4F4F)), CircleShape)
                            .padding(u * 14f).background(Color.Black, CircleShape)
                            .padding(u * 6f).clip(CircleShape).background(Color(0xFF2B2F36))
                            .pointerInput(hasStory, profile.avatar, profile.restricted) {
                                detectTapGestures(
                                    onTap = {
                                        if (hasStory) showStory = true
                                        else if (profile.avatar.isNotBlank() && !profile.restricted) viewImage = profile.avatar
                                    },
                                    onLongPress = {
                                        if (profile.avatar.isNotBlank() && !profile.restricted) viewImage = profile.avatar
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarImg != null) {
                            Image(avatarImg, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Icon(Icons.Filled.Person, null, tint = Color(0xFF8A8F98), modifier = Modifier.size(u * 160f))
                        }
                    }
                    // حالة الشخص النصية (فقاعة فوق صورته)
                    val activeStatus = profile.status?.takeIf { !profile.restricted && System.currentTimeMillis() < it.expiresAt }
                    if (activeStatus != null) {
                        Box(
                            Modifier.align(Alignment.TopStart)
                                .padding(start = u * 60f, top = u * 190f)
                                .height(u * 240f),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            StatusBubble(activeStatus.text, glass, u, null)
                        }
                    }
                    // الاسم واليوزر
                    Column(
                        Modifier.align(Alignment.TopEnd).padding(end = u * 52f, top = u * 445f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            name, color = Color.White, fontSize = (u.value * 86f).sp,
                            fontWeight = FontWeight.Bold, textAlign = TextAlign.End
                        )
                        if (username.isNotBlank()) {
                            Text(
                                "@$username", color = gray, fontSize = (u.value * 44f).sp,
                                fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = u * 8f)
                            )
                        }
                    }
                    // النبذة + زر المراسلة
                    Column(
                        Modifier.align(Alignment.TopEnd).fillMaxWidth()
                            .padding(top = u * 800f, end = u * 35f, start = u * 35f),
                        horizontalAlignment = Alignment.End
                    ) {
                        if (profile.bio.isNotBlank()) {
                            Text(
                                profile.bio, color = Color.White, fontSize = (u.value * 52f).sp,
                                textAlign = TextAlign.End
                            )
                            Spacer(Modifier.height(28.dp))
                        }
                        if (profile.restricted) {
                            // هذا المستخدم حاظرني: ما أشوف ملفه ولا أقدر أراسله
                            Text(
                                "هذا المستخدم قام بحظرك، ما تگدر تشوف ملفه أو تراسله.",
                                color = gray, fontSize = 16.sp, textAlign = TextAlign.End
                            )
                        } else {
                            GlassButton(
                                onClick = onMessage,
                                backdrop = glass,
                                tint = Color(0xFF1AA7FF),
                                modifier = Modifier.fillMaxWidth().height(50.dp)
                            ) {
                                Text("مراسلة", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            }
                            if (!isMe) {
                                Spacer(Modifier.height(10.dp))
                                when (relation) {
                                    Relation.NONE -> GlassButton(
                                        onClick = { relAction { Repo.sendFriendRequest(user.uid) } },
                                        backdrop = glass, tint = Color(0xFF2EBD85), enabled = !relBusy,
                                        modifier = Modifier.fillMaxWidth().height(50.dp)
                                    ) {
                                        Text("إضافة صديق", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Relation.SENT -> GlassButton(
                                        onClick = { relAction { Repo.cancelFriendRequest(user.uid) } },
                                        backdrop = glass, enabled = !relBusy,
                                        modifier = Modifier.fillMaxWidth().height(50.dp)
                                    ) {
                                        Text("تم إرسال الطلب · إلغاء", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Relation.RECEIVED -> Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        GlassButton(
                                            onClick = { relAction { Repo.acceptFriendRequest(user.uid) } },
                                            backdrop = glass, tint = Color(0xFF2EBD85), enabled = !relBusy,
                                            modifier = Modifier.weight(1f).height(50.dp)
                                        ) { Text("قبول الصداقة", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                                        GlassButton(
                                            onClick = { relAction { Repo.declineFriendRequest(user.uid) } },
                                            backdrop = glass, enabled = !relBusy,
                                            modifier = Modifier.weight(1f).height(50.dp)
                                        ) { Text("رفض", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                                    }
                                    Relation.FRIENDS -> GlassButton(
                                        onClick = { confirmUnfriend = true },
                                        backdrop = glass, enabled = !relBusy,
                                        modifier = Modifier.fillMaxWidth().height(50.dp)
                                    ) {
                                        Icon(Icons.Filled.Check, null, tint = Color(0xFF5CE0A6), modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("صديق", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (confirmUnfriend) {
                GlassConfirmDialog(
                    backdrop = glass,
                    dark = true,
                    title = "إلغاء الصداقة",
                    message = "راح تلغي صداقتك مع $name، وما يگدر يشوف قصتك ولا تشوف قصته لين ترجعوا أصدقاء.",
                    confirmText = "إلغاء الصداقة",
                    icon = Icons.Filled.Person,
                    onConfirm = { confirmUnfriend = false; relAction { Repo.removeFriend(user.uid) } },
                    onDismiss = { confirmUnfriend = false },
                    cancelText = "رجوع"
                )
            }
        }
    }
    if (showStory && hasStory && story != null) {
        StoryViewerScreen(
            story = story, owner = profile, mine = false, showMenu = false,
            onDismiss = { showStory = false },
            ownerUid = user.uid,
            // أنا أصلاً على ملفه الشخصي: ضغطة على صورته بالقصة تسكّر القصة وتبين الملف
            onOpenProfile = { showStory = false }
        )
    }
    // عرض البانر / الصورة الشخصية بملء الشاشة مع حفظ بالمعرض
    ImageViewerHost(viewImage) { viewImage = null }
    }
}
