package com.chatapp.messenger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.Profile
import com.chatapp.messenger.Repo
import com.chatapp.messenger.UserItem
import com.chatapp.messenger.ui.glass.GlassButton
import com.chatapp.messenger.ui.theme.AppFont
import com.chatapp.messenger.ui.theme.isAppDark
import com.kyant.backdrop.Backdrop
import androidx.compose.foundation.layout.heightIn
import com.chatapp.messenger.ui.theme.GlassFx
import com.chatapp.messenger.ui.theme.glassSurfaceLayer
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.drawBackdrop
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.launch

/** نص مقدمة الدردشة الافتراضي */
internal const val DEFAULT_INTRO = "قل مرحباً، اسألني أي شيء، أو سولف وياي"

/** "قبل 5 د" / "قبل 3 س" / "قبل 2 يوم" */
internal fun agoText(at: Long): String {
    if (at <= 0L) return ""
    val m = (System.currentTimeMillis() - at) / 60_000L
    return when {
        m < 1 -> "الآن"
        m < 60 -> "قبل $m د"
        m < 60 * 24 -> "قبل ${m / 60} س"
        else -> "قبل ${m / (60 * 24)} يوم"
    }
}

// ============================================================================
//  قائمة الضغط المطوّل على محادثة (Liquid Glass صافي: انكسار + ضبابية خفيفة + شفافية)
// ============================================================================
@Composable
internal fun ChatActionSheet(
    backdrop: Backdrop,
    title: String,
    pinned: Boolean,
    starred: Boolean,
    muted: Boolean,
    restricted: Boolean,
    blocked: Boolean,
    onPin: () -> Unit,
    onStar: () -> Unit,
    onMute: () -> Unit,
    onRestrict: () -> Unit,
    onSelect: () -> Unit,
    onBlock: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val dark = isAppDark()
    val fg = glassFg(dark)
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 420f)) }
    val shape = RoundedCornerShape(32.dp)
    val danger = Color(0xFFFF5A5F)
    BackHandler(onBack = onDismiss)
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = enter.value.coerceIn(0f, 1f) }
            .background(Color.Black.copy(alpha = 0.22f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(horizontal = 32.dp, vertical = 24.dp).widthIn(max = 340.dp).fillMaxWidth()
                .graphicsLayer {
                    val s = 0.82f + 0.18f * enter.value
                    scaleX = s; scaleY = s
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        // ضبابية خفيفة + انكسار واضح عند الحواف
                        blur((4f.dp.toPx()) * GlassFx.blurK)
                        lens((16f.dp.toPx()) * GlassFx.refractK, (32f.dp.toPx()) * GlassFx.refractK)
                    },
                    onDrawSurface = {
                        glassSurfaceLayer {
                            drawRect(if (dark) Color.White.copy(0.07f) else Color.White.copy(0.28f))
                            drawRect(
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(0.10f), Color.Transparent), endY = size.height * 0.45f
                                )
                            )
                        }
                    }
                )
                .border(0.8.dp, glassBorderBrush(0.60f), shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(vertical = 10.dp)
        ) {
            Text(
                title, color = fg.copy(0.7f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)
            )
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                SheetRow(AppIcons.Pin, if (pinned) "إلغاء التثبيت" else "تثبيت", fg) { onPin() }
                SheetRow(if (starred) AppIcons.StarFilled else AppIcons.StarOutline,
                    if (starred) "إزالة النجمة" else "تمييز بنجمة", fg,
                    iconTint = if (starred) Color(0xFFFFC107) else null) { onStar() }
                SheetRow(if (muted) AppIcons.Bell else AppIcons.BellOff, if (muted) "إلغاء الكتم" else "كتم الإشعارات", fg) { onMute() }
                SheetRow(AppIcons.Restrict, if (restricted) "إلغاء التقييد" else "تقييد", fg) { onRestrict() }
                SheetRow(AppIcons.CheckBox, "تحديد", fg) { onSelect() }
                SheetDivider(fg)
                SheetRow(AppIcons.Block, if (blocked) "إلغاء الحظر" else "حظر", danger) { onBlock() }
                SheetRow(AppIcons.Trash, "حذف المحادثة", danger) { onDelete() }
            }
        }
    }
}

@Composable
private fun SheetDivider(fg: Color) {
    Box(Modifier.padding(horizontal = 22.dp, vertical = 4.dp).fillMaxWidth().height(0.7.dp).background(fg.copy(0.14f)))
}

@Composable
private fun SheetRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    iconTint: Color? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = iconTint ?: color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = color, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}

// ============================================================================
//  مقدمة الدردشة: البطاقة (تنعرض بالمعاينة وعند الطرف الي يفتح محادثة جديدة)
// ============================================================================
@Composable
internal fun ChatIntroCard(
    name: String,
    avatar: ImageBitmap?,
    text: String,
    sticker: String,
    fg: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(Color(0xFF2B2F36)),
            contentAlignment = Alignment.Center
        ) {
            if (avatar != null) Image(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Filled.Person, null, tint = Color(0xFF8A8F98), modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(name, color = fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.height(14.dp))
        val shape = RoundedCornerShape(28.dp)
        Column(
            Modifier.widthIn(max = 260.dp)
                .clip(shape).background(fg.copy(0.10f)).border(0.8.dp, fg.copy(0.12f), shape)
                .padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (sticker.isBlank()) Text("👋", fontSize = 44.sp)
            else BadgeView(sticker, 64.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                text.ifBlank { DEFAULT_INTRO }, color = fg, fontSize = 15.sp, textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/** شاشة تخصيص مقدمة الدردشة (من قائمة ⋮ بصفحة Home) */
@Composable
internal fun ChatIntroScreen(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val me = remember { Repo.auth.currentUser?.uid ?: "" }
    val prof by remember(me) { Repo.basicProfileFlow(me) }.collectAsState(initial = Profile())
    val avatar = rememberB64Image(prof.avatar)
    var inited by remember { mutableStateOf(false) }
    var on by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(DEFAULT_INTRO) }
    var sticker by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var pickerOrigin by remember { mutableStateOf(Offset.Zero) }

    // نقرأ القيم المحفوظة مرة وحدة (أو بعد ثانية ونص إذا ما وصل الملف)
    LaunchedEffect(prof) {
        if (!inited && (prof.name.isNotBlank() || prof.username.isNotBlank())) {
            on = prof.introOn; text = prof.intro.ifBlank { DEFAULT_INTRO }; sticker = prof.introSticker; inited = true
        }
    }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(1500); inited = true }
    val dirty = inited && (on != prof.introOn || text.trim() != prof.intro.ifBlank { DEFAULT_INTRO } || sticker != prof.introSticker)

    BackHandler(enabled = !pickerOpen) { onDismiss() }
    val dark = isAppDark()
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        LiquidScene(banner = null) { bd ->
            val fg = MaterialTheme.colorScheme.onBackground
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    GlassButton(onClick = onDismiss, backdrop = bd, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, null, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        "مقدمة الدردشة", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(horizontal = 14.dp)
                    )
                    GlassButton(
                        backdrop = bd, tint = AccentBlue, modifier = Modifier.height(48.dp),
                        enabled = dirty && !saving,
                        onClick = {
                            saving = true
                            scope.launch {
                                try {
                                    if (sticker.startsWith("s:tg_")) BadgeSync.ensureUploaded(ctx, sticker)
                                    Repo.saveIntro(on, text, sticker)
                                    AppToast.show("تم حفظ مقدمة الدردشة")
                                } catch (e: Exception) {
                                    AppToast.show("فشل الحفظ: ${e.message}", long = true)
                                }
                                saving = false
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.padding(start = 16.dp).size(20.dp))
                        Text(
                            if (saving) "..." else "حفظ", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 6.dp, end = 20.dp)
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))
                // المعاينة: كيف تبين لمن يفتح محادثة جديدة معك
                Box(
                    Modifier.fillMaxWidth()
                        .glassSurface(bd, dark, RoundedCornerShape(36.dp), blurDp = 10f)
                        .padding(vertical = 26.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ChatIntroCard(
                        name = prof.name.ifBlank { "اسمك" }, avatar = avatar,
                        text = text, sticker = sticker, fg = fg,
                        modifier = Modifier.graphicsLayer { alpha = if (on) 1f else 0.45f }
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "لن يرى هذا إلا الأشخاص الذين يفتحون دردشة جديدة معك. لن تتأثر الدردشات الحالية.",
                    color = fg.copy(0.75f), fontSize = 15.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )
                Spacer(Modifier.height(20.dp))

                Row(
                    Modifier.fillMaxWidth().glassSurface(bd, dark, RoundedCornerShape(22.dp), blurDp = 10f)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("إظهار مقدمة الدردشة", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Switch(
                        checked = on, onCheckedChange = { on = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue, checkedThumbColor = Color.White)
                    )
                }
                Spacer(Modifier.height(12.dp))

                Column(
                    Modifier.fillMaxWidth().glassSurface(bd, dark, RoundedCornerShape(22.dp), blurDp = 10f)
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    Text("نص المقدمة", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = fg.copy(0.6f))
                    Spacer(Modifier.height(6.dp))
                    BasicTextField(
                        value = text, onValueChange = { text = it.take(100) },
                        textStyle = TextStyle(fontFamily = AppFont, color = fg, fontSize = 17.sp),
                        cursorBrush = SolidColor(AccentBlue), maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("${text.length}/100", fontSize = 11.sp, color = fg.copy(0.4f), modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("الملصق", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (sticker.isNotBlank()) {
                            Box(
                                Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFFF5A5F).copy(0.16f))
                                    .clickable { sticker = "" }.padding(horizontal = 14.dp, vertical = 8.dp)
                            ) { Text("إزالة", color = Color(0xFFFF5A5F), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                            Spacer(Modifier.width(8.dp))
                        }
                        Box(
                            Modifier.onGloballyPositioned { pickerOrigin = it.boundsInRoot().center }
                                .clip(RoundedCornerShape(50)).background(fg.copy(0.10f))
                                .clickable { pickerOpen = true }.padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (sticker.isBlank()) Text("👋", fontSize = 22.sp) else BadgeView(sticker, 28.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("تغيير", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
            // اختيار ملصق من ملصقات رموز الاسم
            BadgePopup(
                open = pickerOpen, origin = pickerOrigin, dark = dark, backdrop = bd,
                onDismiss = { pickerOpen = false },
                onPick = { sticker = it },
                currentId = sticker,
                heading = "ملصق المقدمة",
                subheading = "اختر ملصق يظهر بمقدمة الدردشة"
            )
        }
    }
}

// ============================================================================
//  النشاط: آخر من زار ملفي، آخر من أعجب بقصصي، آخر من تابعني
// ============================================================================
@Composable
internal fun ActivityScreen(onDismiss: () -> Unit, onOpenProfile: (UserItem) -> Unit) {
    val me = remember { Repo.auth.currentUser?.uid ?: "" }
    val events by remember(me) { Repo.activityFlow(me) }.collectAsState(initial = emptyList())
    val followers by remember(me) { Repo.followersTimedFlow(me) }.collectAsState(initial = emptyList())
    val fg = MaterialTheme.colorScheme.onBackground
    val visits = events.filter { it.type == "visit" }.take(20)
    val likes = events.filter { it.type == "like" }.take(20)
    val follows = followers.take(20)

    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        LiquidScene(banner = null) { bd ->
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
                StaggerIn(0, Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        GlassButton(onClick = onDismiss, backdrop = bd, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.Close, null, modifier = Modifier.size(24.dp))
                        }
                        Text(
                            "النشاط", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f).padding(horizontal = 14.dp)
                        )
                    }
                }
                LazyColumn(
                    contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    section("👁️  آخر من زار ملفك", fg, visits.isEmpty(), "ما أحد زار ملفك لحد الآن")
                    items(visits, key = { "v_${it.uid}" }) { e ->
                        FriendRow(e.uid, bd, onClick = onOpenProfile, trailing = { _ -> AgoText(e.at, fg) })
                    }
                    section("❤️  آخر من أعجب بقصصك", fg, likes.isEmpty(), "ما أحد أعجب بقصصك لحد الآن")
                    items(likes, key = { "l_${it.uid}" }) { e ->
                        FriendRow(e.uid, bd, onClick = onOpenProfile, trailing = { _ -> AgoText(e.at, fg) })
                    }
                    section("➕  آخر من تابعك", fg, follows.isEmpty(), "ما أحد تابعك لحد الآن")
                    items(follows, key = { "f_${it.first}" }) { (uid, at) ->
                        FriendRow(uid, bd, onClick = onOpenProfile, trailing = { _ -> AgoText(at, fg) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AgoText(at: Long, fg: Color) {
    Text(agoText(at), color = fg.copy(0.6f), fontSize = 12.sp)
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(title: String, fg: Color, empty: Boolean, emptyText: String) {
    item(key = "hdr_$title") {
        Text(
            title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = fg.copy(0.85f),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
        )
    }
    if (empty) item(key = "empty_$title") {
        Text(emptyText, fontSize = 14.sp, color = fg.copy(0.5f), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}


// ============================================================================
//  قائمة ⋮ بصفحة Home — Liquid Glass (تظهر تحت الزر من اليسار الفعلي)
// ============================================================================
@Composable
internal fun HomeMoreMenu(
    backdrop: Backdrop,
    dark: Boolean,
    onDismiss: () -> Unit,
    onSelect: () -> Unit,
    onIntro: () -> Unit,
    onActivity: () -> Unit
) {
    val fg = glassFg(dark)
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 480f)) }
    val shape = RoundedCornerShape(26.dp)
    val screenRtl = androidx.compose.ui.platform.LocalLayoutDirection.current ==
        androidx.compose.ui.unit.LayoutDirection.Rtl
    BackHandler(onBack = onDismiss)

    // الموضع بالاتجاه الفعلي (يسار) حتى لو التطبيق RTL
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
    ) {
        Box(
            Modifier.fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
        ) {
            Column(
                Modifier
                    .statusBarsPadding()
                    // تحت الزر: 10 (top) + 44 (الزر) + 8 فراغ
                    .padding(start = 12.dp, top = 62.dp)
                    .graphicsLayer {
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        val sc = 0.55f + 0.45f * enter.value
                        scaleX = sc; scaleY = sc
                        alpha = enter.value.coerceIn(0f, 1f)
                    }
                    .width(230.dp)
                    .glassSurface(backdrop, dark, shape, blurDp = 16f)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(8.dp)
            ) {
                // نص عربي: نرجّع RTL لمحتوى القائمة فقط
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl
                ) {
                    HomeMenuRow("☑️", "تحديد الدردشات", fg, onSelect)
                    HomeMenuRow("👋", "مقدمة الدردشة", fg, onIntro)
                    HomeMenuRow("📈", "النشاط", fg, onActivity)
                }
            }
        }
    }
}

@Composable
private fun HomeMenuRow(icon: String, label: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(50.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 20.sp)
        Spacer(Modifier.width(14.dp))
        Text(label, color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}
