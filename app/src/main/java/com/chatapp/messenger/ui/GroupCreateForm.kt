package com.chatapp.messenger.ui

import com.chatapp.messenger.ui.theme.AppFont

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.Profile
import androidx.compose.ui.zIndex
import com.chatapp.messenger.Repo

private val GroupViolet = Color(0xFF8A5CFF)
private val GroupMint = Color(0xFF00D4AA)

/**
 * نموذج إنشاء مجموعة (يوضع داخل نافذة الـ morph):
 * صورة المجموعة، اسم المجموعة، معرّف المجموعة، نبذة المجموعة، دعوة أصدقاء.
 * خفيف عمداً (بدون زجاج متداخل) حتى ما يثقّل الحركة.
 */
@Composable
fun GroupCreateForm(dark: Boolean, modifier: Modifier = Modifier, onCreated: () -> Unit = {}) {
    val fg = if (dark) Color.White else Color(0xFF101418)
    var name by remember { mutableStateOf("") }
    var gid by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var avatarB64 by remember { mutableStateOf("") }
    // صورة المجموعة المتحركة (GIF) + البانر (ثابت أو GIF)
    var avatarGif by remember { mutableStateOf<ByteArray?>(null) }
    var avatarGifFile by remember { mutableStateOf<java.io.File?>(null) }
    var bannerB64 by remember { mutableStateOf("") }
    var bannerGif by remember { mutableStateOf<ByteArray?>(null) }
    var bannerGifFile by remember { mutableStateOf<java.io.File?>(null) }
    var bfx by remember { mutableFloatStateOf(0f) }
    var bfy by remember { mutableFloatStateOf(0f) }
    var showBannerCrop by remember { mutableStateOf(false) }
    var invited by remember { mutableStateOf(setOf<String>()) }
    var friendsOpen by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // أي صورة تنختار: نفتح شاشة تحريك الصورة حتى تختار الجزء الي تريده
    var cropSrc by remember { mutableStateOf<String?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // يحفظ GIF بملف مؤقت للمعاينة المتحركة قبل الإنشاء
    fun stageGif(bytes: ByteArray, tag: String, done: (java.io.File?) -> Unit) {
        scope.launch {
            done(kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    java.io.File(ctx.cacheDir, "gif_groups").apply { mkdirs() }
                        .let { d -> java.io.File(d, "pending_${tag}_${System.currentTimeMillis()}.gif").also { it.writeBytes(bytes) } }
                }.getOrNull()
            })
        }
    }
    val pickAvatar = rememberImagePicker(
        maxSide = 800, quality = 80,
        gifLimit = GifUtil.MAX_GROUP_AVATAR_BYTES,
        onGif = { bytes, still ->
            if (still.length > 390_000) AppToast.show("الصورة كبيرة، جرّب GIF أصغر")
            else {
                // GIF ما ينقص (يبقى متحرك)، فنتخطى شاشة القص
                avatarB64 = still; avatarGif = bytes; avatarGifFile = null
                stageGif(bytes, "avatar") { avatarGifFile = it }
            }
        }
    ) { b64 ->
        avatarGif = null; avatarGifFile = null
        cropSrc = b64
    }
    val pickBanner = rememberImagePicker(
        maxSide = 800, quality = 60,
        onGif = { bytes, still ->
            if (still.length > 390_000) AppToast.show("الصورة كبيرة، جرّب GIF أصغر")
            else {
                bannerB64 = still; bannerGif = bytes; bannerGifFile = null
                bfx = 0f; bfy = 0f; showBannerCrop = true
                stageGif(bytes, "banner") { bannerGifFile = it }
            }
        }
    ) { b64 ->
        if (b64.length > 390_000) AppToast.show("الصورة كبيرة، جرّب صورة أصغر")
        else {
            bannerB64 = b64; bannerGif = null; bannerGifFile = null
            bfx = 0f; bfy = 0f; showBannerCrop = true
        }
    }
    val avatar = rememberB64Image(avatarB64)
    val bannerImg = rememberB64Image(bannerB64)
    val idOk = gid.isBlank() || Repo.isValidUsername(gid)
    val canCreate = name.isNotBlank() && idOk && !creating

    // الكيبورد: نقلّص منطقة التمرير فقط بمقدار الجزء الي يغطي النافذة فعلاً (مو ارتفاع الكيبورد كله)
    val view = LocalView.current
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    var boxBottom by remember { mutableFloatStateOf(0f) }
    val kbPad = if (imeBottom > 0) {
        val overlap = boxBottom - (view.rootView.height - imeBottom)
        with(density) { overlap.coerceAtLeast(0f).toDp() }
    } else 0.dp

    Box(modifier.fillMaxSize().onGloballyPositioned { boxBottom = it.boundsInWindow().bottom }) {
        cropSrc?.let { src ->
            // فوق كل شي بالنافذة
            Box(Modifier.fillMaxSize().zIndex(10f)) {
                AvatarCropOverlay(srcB64 = src, onCancel = { cropSrc = null }, onDone = { avatarB64 = it; cropSrc = null })
            }
        }
        if (showBannerCrop && bannerB64.isNotEmpty()) {
            Box(Modifier.fillMaxSize().zIndex(10f)) {
                BannerCropOverlay(
                    still = bannerImg, gif = bannerGifFile, initFx = bfx, initFy = bfy,
                    fixedHeightDp = 170f,
                    onCancel = { showBannerCrop = false },
                    onDone = { x, y -> bfx = x; bfy = y; showBannerCrop = false }
                )
            }
        }
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 18.dp + kbPad),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // العنوان جزء من المحتوى: ينسحب مع النافذة (زر X يبقى ثابت بالزاوية)
            Text("مجموعة جديدة", color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(22.dp))

            // ---- بانر المجموعة (يقبل GIF) ----
            val bannerShape = RoundedCornerShape(20.dp)
            Box(
                Modifier.fillMaxWidth().height(110.dp).clip(bannerShape)
                    .background(fg.copy(alpha = 0.10f))
                    .border(1.dp, fg.copy(alpha = 0.18f), bannerShape)
                    .clickable(onClick = pickBanner),
                contentAlignment = Alignment.Center
            ) {
                if (bannerImg != null) {
                    AnimatedBannerImage(bannerGifFile, bannerImg, Modifier.fillMaxSize(), ContentScale.Crop, androidx.compose.ui.BiasAlignment(bfx, bfy))
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(AppIcons.Image, null, tint = fg.copy(alpha = 0.55f), modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("بانر المجموعة (يقبل GIF)", color = fg.copy(alpha = 0.65f), fontSize = 12.sp)
                    }
                }
            }
            if (bannerB64.isNotEmpty()) {
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "تحريك", color = AccentBlue, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp))
                            .clickable { showBannerCrop = true }.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    Text(
                        "إزالة البانر", color = Color(0xFFFF5A5F), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp))
                            .clickable { bannerB64 = ""; bannerGif = null; bannerGifFile = null; bfx = 0f; bfy = 0f }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            // ---- صورة المجموعة ----
            Box(Modifier.size(100.dp)) {
                Box(
                    Modifier.fillMaxSize().clip(CircleShape)
                        .background(fg.copy(alpha = 0.10f))
                        .border(1.dp, fg.copy(alpha = 0.18f), CircleShape)
                        .clickable(onClick = pickAvatar),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarGifFile != null) AnimatedGifImage(avatarGifFile!!, Modifier.fillMaxSize(), ContentScale.Crop, key = avatarGifFile!!.path)
                    else if (avatar != null) Image(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(AppIcons.Image, null, tint = fg.copy(alpha = 0.55f), modifier = Modifier.size(36.dp))
                }
                Box(
                    Modifier.align(Alignment.BottomEnd).size(32.dp).clip(CircleShape)
                        .background(AccentBlue)
                        .border(2.dp, if (dark) Color(0xFF1B2733) else Color.White, CircleShape)
                        .clickable(onClick = pickAvatar),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Edit, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            }
            Spacer(Modifier.height(8.dp))
            if (avatarB64.isEmpty()) {
                Text("صورة المجموعة (تقبل GIF)", color = fg.copy(alpha = 0.65f), fontSize = 13.sp)
            } else {
                Text(
                    "إزالة الصورة", color = Color(0xFFFF5A5F), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                        .clickable { avatarB64 = ""; avatarGif = null; avatarGifFile = null }.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            // ---- اسم المجموعة ----
            GroupField(
                label = "اسم المجموعة", value = name, tint = AccentBlue, fg = fg, maxLen = 40,
                icon = AppIcons.GroupAdd,
                onValueChange = { name = it.take(40) }
            )
            Spacer(Modifier.height(10.dp))
            // ---- معرّف المجموعة ----
            GroupField(
                label = "معرّف المجموعة", value = gid, tint = GroupViolet, fg = fg, maxLen = 24,
                iconText = "@", ltr = true,
                onValueChange = { v ->
                    gid = v.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '.' }.take(24)
                }
            )
            if (!idOk) {
                Text(
                    "المعرّف لازم يكون 3 حروف أو أرقام على الأقل",
                    fontSize = 12.sp, color = Color(0xFFFF5A5F), textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 8.dp, end = 8.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            // ---- نبذة المجموعة ----
            GroupField(
                label = "نبذة المجموعة", value = bio, tint = GroupMint, fg = fg, maxLen = 150,
                icon = AppIcons.ChatBubble, singleLine = false, minLines = 2, maxLines = 4,
                onValueChange = { bio = it.take(150) }
            )

            Spacer(Modifier.height(10.dp))
            // ---- دعوة أصدقاء ----
            val shape = RoundedCornerShape(22.dp)
            Row(
                Modifier.fillMaxWidth().clip(shape)
                    .background(fg.copy(alpha = 0.08f))
                    .border(1.dp, fg.copy(alpha = 0.14f), shape)
                    .clickable { friendsOpen = !friendsOpen }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFFFA62B).copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) { Icon(AppIcons.GroupAdd, null, tint = Color(0xFFFFA62B), modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("دعوة أصدقاء", color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (invited.isEmpty()) "اختر من أصدقائك" else "تم اختيار ${invited.size}",
                        color = fg.copy(alpha = 0.6f), fontSize = 12.sp
                    )
                }
                Icon(
                    if (friendsOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    null, tint = fg.copy(alpha = 0.7f), modifier = Modifier.size(24.dp)
                )
            }
            AnimatedVisibility(
                visible = friendsOpen,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                FriendPicker(
                    fg = fg, selected = invited,
                    onToggle = { uid -> invited = if (uid in invited) invited - uid else invited + uid }
                )
            }

            Spacer(Modifier.height(16.dp))
            // ---- زر الإنشاء ----
            val pill = RoundedCornerShape(26.dp)
            Box(
                Modifier.fillMaxWidth().height(50.dp).clip(pill)
                    .background(if (canCreate) AccentBlue else AccentBlue.copy(alpha = 0.35f))
                    .clickable(enabled = canCreate) {
                        creating = true
                        scope.launch {
                            try {
                                Repo.createGroup(
                                    name, gid, bio, avatarB64, invited,
                                    avatarGif = avatarGif,
                                    bannerB64 = bannerB64, bannerGif = bannerGif, bannerFx = bfx, bannerFy = bfy
                                )
                                AppToast.show("تم إنشاء المجموعة")
                                // نفرّغ النموذج حتى يكون جاهز لمجموعة ثانية
                                name = ""; gid = ""; bio = ""; avatarB64 = ""; invited = emptySet(); friendsOpen = false
                                avatarGif = null; avatarGifFile = null
                                bannerB64 = ""; bannerGif = null; bannerGifFile = null; bfx = 0f; bfy = 0f
                                onCreated()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                AppToast.show(e.message?.takeIf { it.isNotBlank() } ?: "تعذّر إنشاء المجموعة، جرّب مرة ثانية")
                            } finally {
                                creating = false
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (creating) "جاري الإنشاء..." else "إنشاء المجموعة",
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** حقل نص خفيف: أيقونة ملونة + عنوان صغير + عداد أحرف + حد يتلوّن عند التركيز */
@Composable
private fun GroupField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    tint: Color,
    fg: Color,
    maxLen: Int,
    icon: ImageVector? = null,
    iconText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    ltr: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(fg.copy(alpha = 0.08f))
            .border(if (focused) 1.6.dp else 1.dp, if (focused) tint else fg.copy(alpha = 0.14f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            else Text(iconText ?: "", color = tint, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = if (focused) tint else fg.copy(alpha = 0.6f), fontSize = 12.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                minLines = minLines,
                maxLines = maxLines,
                textStyle = TextStyle(fontFamily = AppFont, color = fg, fontSize = 16.sp,
                    textAlign = if (ltr) TextAlign.Left else TextAlign.Start
                ),
                cursorBrush = SolidColor(tint),
                keyboardOptions = KeyboardOptions(
                    capitalization = if (ltr) KeyboardCapitalization.None else KeyboardCapitalization.Sentences
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp).onFocusChanged { focused = it.isFocused }
            )
        }
        if (focused) {
            Spacer(Modifier.width(8.dp))
            Text("${value.length}/$maxLen", color = fg.copy(alpha = 0.45f), fontSize = 11.sp)
        }
    }
}

/** قائمة الأصدقاء للاختيار (تتحمّل فقط لما تنفتح) */
@Composable
private fun FriendPicker(fg: Color, selected: Set<String>, onToggle: (String) -> Unit) {
    val friends by remember { Repo.friendIdsFlow() }.collectAsState(initial = emptySet())
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (friends.isEmpty()) {
            Text(
                "ما عندك أصدقاء حالياً", color = fg.copy(alpha = 0.6f), fontSize = 14.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            )
        } else {
            friends.toList().forEach { uid ->
                FriendPickRow(uid, fg, uid in selected) { onToggle(uid) }
            }
        }
    }
}

@Composable
internal fun FriendPickRow(uid: String, fg: Color, checked: Boolean, onClick: () -> Unit) {
    val prof by remember(uid) { Repo.basicProfileFlow(uid) }.collectAsState(initial = Profile())
    val avatar = rememberB64Image(prof.avatar)
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (checked) AccentBlue.copy(alpha = 0.16f) else fg.copy(alpha = 0.05f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF2B2F36)),
            contentAlignment = Alignment.Center
        ) {
            if (avatar != null) Image(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Filled.Person, null, tint = Color(0xFF8A8F98), modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                prof.name.ifBlank { "مستخدم" }, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (prof.username.isNotBlank()) {
                Text("@${prof.username}", color = fg.copy(alpha = 0.55f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(
            Modifier.size(24.dp).clip(CircleShape)
                .background(if (checked) AccentBlue else Color.Transparent)
                .border(1.5.dp, if (checked) AccentBlue else fg.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
    }
}
