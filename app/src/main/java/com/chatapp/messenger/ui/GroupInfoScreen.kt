package com.chatapp.messenger.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import com.chatapp.messenger.ui.theme.isAppDark
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.GroupAccess
import com.chatapp.messenger.GroupAdmin
import com.chatapp.messenger.GroupFull
import com.chatapp.messenger.GroupPerms
import com.chatapp.messenger.GroupSettings
import com.chatapp.messenger.Profile
import com.chatapp.messenger.Repo
import com.chatapp.messenger.UserItem
import com.chatapp.messenger.ui.glass.GlassButton
import com.chatapp.messenger.ui.glass.LiquidToggle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

private val DangerColor = Color(0xFFE5484D)

private class ConfirmSpec(
    val title: String,
    val message: String,
    val confirmText: String,
    val icon: ImageVector? = null,
    val onConfirm: () -> Unit
)

/**
 * معلومات المجموعة وإدارتها (مثل تيليجرام) بتصميم Liquid Glass كامل:
 * خلفية التطبيق + بطاقات زجاجية + نوافذ زجاجية + مفاتيح LiquidToggle.
 * الواجهة تُظهر الخيارات حسب صلاحياتك، وقواعد قاعدة البيانات هي الحماية الفعلية.
 */
@Composable
fun GroupInfoScreen(groupId: String, onBack: () -> Unit, onGone: () -> Unit, onOpenProfile: (UserItem) -> Unit = {}) {
    val me = remember { Repo.auth.currentUser?.uid ?: "" }
    val st by remember(groupId) { Repo.groupFullFlow(groupId).map { GroupLoaded(it) } }
        .collectAsState(initial = null)
    val loaded = st
    val g = loaded?.g
    LaunchedEffect(loaded) { if (loaded != null && loaded.g == null) onGone() }

    val dark = isAppDark()
    val fg = glassFg(dark)
    val backdrop = rememberLayerBackdrop()
    val scope = rememberCoroutineScope()

    val a = remember(g, me) { if (g != null) GroupAccess(g, me) else null }
    val names = rememberNames(remember(g) { if (g != null) (g.members + g.banned).distinct() else emptyList() })
    fun nameOf(uid: String) = names[uid].orEmpty().ifBlank { "مستخدم" }

    var showEdit by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showBanned by remember { mutableStateOf(false) }
    var memberDialog by remember { mutableStateOf<String?>(null) }
    var adminDialog by remember { mutableStateOf<String?>(null) }
    var restrictDialog by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<ConfirmSpec?>(null) }

    fun act(okMsg: String = "", block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                if (okMsg.isNotEmpty()) AppToast.show(okMsg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppToast.show(e.message?.takeIf { it.isNotBlank() } ?: "صار خطأ، جرّب مرة ثانية")
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // ---- طبقة الخلفية + المحتوى (الزجاج يعكس هذي الطبقة) ----
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            ChatBackground(ChatBg(), remember { File("") }, Modifier.fillMaxSize())
        }

        // المحتوى الزجاجي لازم يكون خارج طبقة layerBackdrop، وإلا الزجاج يرسم نفسه (تكرار لا نهائي = كراش)
        run {
            // الزر والعنوان جزء من الواجهة نفسها: يرتفعون وياها لما تسحب لفوق (مو شريط ثابت)
            val header: @Composable () -> Unit = {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassButton(onClick = onBack, backdrop = backdrop, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = fg, modifier = Modifier.size(24.dp))
                    }
                    Box(
                        Modifier.weight(1f).heightIn(min = 44.dp).padding(horizontal = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text("معلومات المجموعة", color = fg, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Column(Modifier.fillMaxSize()) {
                if (g == null || a == null) {
                    Column(Modifier.fillMaxSize().statusBarsPadding()) {
                        header()
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("جاري التحميل...", color = fg.copy(alpha = 0.6f))
                        }
                    }
                } else {
                    fun rank(uid: String) = when {
                        uid == g.item.owner -> 0
                        uid in g.admins -> 1
                        else -> 2
                    }
                    val sorted = g.members.sortedWith(compareBy<String>({ rank(it) }, { names[it].orEmpty() }))
                    val now = System.currentTimeMillis()

                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = androidx.compose.foundation.layout.WindowInsets.statusBars
                                .asPaddingValues().calculateTopPadding(),
                            bottom = 32.dp
                        )
                    ) {
                        // ---- الزر + العنوان (يتحركون مع الواجهة) ----
                        item { header() }

                        // ---- الرأس ----
                        item {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                                    .glassSurface(backdrop, dark, RoundedCornerShape(30.dp), blurDp = 10f)
                                    .padding(horizontal = 20.dp, vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                GroupAvatar(g.item.avatar, 96.dp)
                                Spacer(Modifier.height(12.dp))
                                Text(g.item.name, color = fg, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                if (g.item.handle.isNotBlank()) {
                                    Text("@${g.item.handle}", color = AccentBlue, fontSize = 14.sp)
                                }
                                if (g.item.bio.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(g.item.bio, color = fg.copy(alpha = 0.78f), fontSize = 14.sp, textAlign = TextAlign.Center)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "${g.item.memberCount} عضو · " + when {
                                        a.isOwner -> "أنت المالك"
                                        a.isAdmin -> "أنت مشرف"
                                        a.isRestricted -> "أنت مقيّد"
                                        else -> "أنت عضو"
                                    },
                                    color = fg.copy(alpha = 0.6f), fontSize = 12.sp
                                )
                            }
                        }

                        // ---- الإجراءات ----
                        item {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (a.canChangeInfo) {
                                    InfoAction(backdrop, dark, Icons.Filled.Edit, "تعديل المجموعة", "الاسم والنبذة والصورة", AccentBlue) { showEdit = true }
                                }
                                if (a.canInvite) {
                                    InfoAction(backdrop, dark, AppIcons.GroupAdd, "إضافة أعضاء", "من أصدقائك", Color(0xFFFFA62B)) { showAdd = true }
                                }
                                if (a.canBan) {
                                    InfoAction(backdrop, dark, Icons.Filled.Settings, "صلاحيات الأعضاء", "الإرسال، الدعوة، التثبيت، الوضع البطيء", Color(0xFF8A5CFF)) { showSettings = true }
                                    InfoAction(backdrop, dark, AppIcons.Block, "المحظورون", "${g.banned.size} محظور", DangerColor) { showBanned = true }
                                }
                            }
                        }

                        // ---- الأعضاء ----
                        item {
                            Text(
                                "الأعضاء (${g.members.size})",
                                color = fg.copy(alpha = 0.75f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                            )
                        }
                        items(sorted, key = { it }) { uid ->
                            val label = when {
                                uid == g.item.owner -> "المالك"
                                uid in g.admins -> g.admins[uid]?.title?.ifBlank { "مشرف" } ?: "مشرف"
                                else -> null
                            }
                            val r = g.restricted[uid]
                            val restrictedNow = r != null && (r.until == 0L || r.until > now)
                            MemberRow(
                                uid = uid,
                                me = me,
                                fg = fg,
                                label = label,
                                restricted = restrictedNow,
                                canManage = a.canManage(uid),
                                onManage = { memberDialog = uid },
                                onOpenProfile = onOpenProfile
                            )
                        }

                        // ---- مغادرة / حذف ----
                        item {
                            Spacer(Modifier.height(16.dp))
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding()) {
                                if (a.isOwner) {
                                    InfoAction(backdrop, dark, Icons.Filled.Delete, "حذف المجموعة", "يحذف المجموعة ورسائلها عند الجميع نهائياً", DangerColor) {
                                        confirm = ConfirmSpec(
                                            "حذف المجموعة",
                                            "راح تنحذف المجموعة \"${g.item.name}\" مع كل رسائلها وأعضائها نهائياً، وما تقدر ترجعها.",
                                            "حذف نهائي",
                                            Icons.Filled.Delete
                                        ) { act { Repo.deleteGroup(g); onGone() } }
                                    }
                                } else {
                                    InfoAction(backdrop, dark, AppIcons.Logout, "مغادرة المجموعة", null, DangerColor) {
                                        confirm = ConfirmSpec(
                                            "مغادرة المجموعة",
                                            "متأكد تريد تغادر \"${g.item.name}\"؟ ما راح توصلك رسائلها بعد.",
                                            "مغادرة",
                                            AppIcons.Logout
                                        ) { act { Repo.leaveGroup(groupId, me in g.admins); onGone() } }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ================= النوافذ الزجاجية (فوق طبقة الخلفية) =================
        if (g != null && a != null) {
            if (showEdit) {
                EditGroupDialog(
                    g = g, backdrop = backdrop, dark = dark,
                    onDismiss = { showEdit = false },
                    onSave = { name, bio, avatar ->
                        showEdit = false
                        act("تم حفظ التعديلات") { Repo.updateGroupInfo(groupId, name, bio, avatar) }
                    }
                )
            }

            if (showAdd) {
                AddMembersDialog(
                    g = g, backdrop = backdrop, dark = dark,
                    onDismiss = { showAdd = false },
                    onAdd = { picked ->
                        showAdd = false
                        act("تمت إضافة ${picked.size} للمجموعة") { Repo.addGroupMembers(groupId, picked) }
                    }
                )
            }

            if (showSettings) {
                SettingsDialog(
                    current = g.settings, backdrop = backdrop, dark = dark,
                    onDismiss = { showSettings = false },
                    onSave = { s ->
                        showSettings = false
                        act("تم حفظ الصلاحيات") { Repo.setGroupSettings(groupId, s) }
                    }
                )
            }

            if (showBanned) {
                GlassDialog(
                    backdrop = backdrop, dark = dark, title = "المحظورون",
                    onDismiss = { showBanned = false }
                ) {
                    if (g.banned.isEmpty()) {
                        Text("ما فيه أحد محظور", color = fg.copy(alpha = 0.8f), fontSize = 15.sp)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            g.banned.forEach { uid ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        nameOf(uid), color = fg, fontSize = 15.sp,
                                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "إلغاء الحظر", color = AccentBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                            .clickable { act("تم إلغاء الحظر") { Repo.unbanGroupMember(groupId, uid) } }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // إجراءات على عضو
            memberDialog?.let { uid ->
                if (!a.canManage(uid)) {
                    memberDialog = null
                } else {
                    val isAdminT = uid in g.admins
                    val hasRestriction = g.restricted[uid] != null
                    GlassDialog(
                        backdrop = backdrop, dark = dark, title = nameOf(uid),
                        onDismiss = { memberDialog = null }
                    ) {
                        Column {
                            if (a.isOwner) {
                                if (isAdminT) {
                                    MenuAction("تعديل صلاحيات المشرف") { memberDialog = null; adminDialog = uid }
                                    MenuAction("إزالة من الإشراف") {
                                        memberDialog = null
                                        act("تمت إزالته من الإشراف") { Repo.removeGroupAdmin(groupId, uid) }
                                    }
                                } else {
                                    MenuAction("تعيين كمشرف") { memberDialog = null; adminDialog = uid }
                                }
                            }
                            if (a.canBan) {
                                if (hasRestriction) {
                                    MenuAction("إلغاء التقييد") {
                                        memberDialog = null
                                        act("تم إلغاء التقييد") { Repo.unrestrictGroupMember(groupId, uid) }
                                    }
                                } else {
                                    MenuAction("تقييد (منعه من الإرسال)") { memberDialog = null; restrictDialog = uid }
                                }
                                MenuAction("طرد من المجموعة", danger = true) {
                                    memberDialog = null
                                    confirm = ConfirmSpec(
                                        "طرد ${nameOf(uid)}",
                                        "راح ينطرد من المجموعة، ويقدر ينضاف لها مرة ثانية.",
                                        "طرد",
                                        AppIcons.Block
                                    ) {
                                        act("تم الطرد") {
                                            Repo.removeGroupMember(groupId, uid, ban = false, wasAdmin = isAdminT, wasRestricted = hasRestriction)
                                        }
                                    }
                                }
                                MenuAction("حظر من المجموعة", danger = true) {
                                    memberDialog = null
                                    confirm = ConfirmSpec(
                                        "حظر ${nameOf(uid)}",
                                        "راح ينطرد ومحد يقدر يرجّعه لين تلغي الحظر من قائمة المحظورين.",
                                        "حظر",
                                        AppIcons.Block
                                    ) {
                                        act("تم الحظر") {
                                            Repo.removeGroupMember(groupId, uid, ban = true, wasAdmin = isAdminT, wasRestricted = hasRestriction)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            adminDialog?.let { uid ->
                AdminPermsDialog(
                    name = nameOf(uid),
                    existing = g.admins[uid],
                    backdrop = backdrop, dark = dark,
                    onDismiss = { adminDialog = null },
                    onSave = { perms, title ->
                        adminDialog = null
                        act("تم حفظ صلاحيات المشرف") { Repo.setGroupAdmin(groupId, uid, perms, title) }
                    }
                )
            }

            restrictDialog?.let { uid ->
                val hour = 3_600_000L
                val options = listOf(
                    "ساعة" to hour,
                    "يوم" to 24 * hour,
                    "أسبوع" to 7 * 24 * hour,
                    "دائم" to 0L
                )
                GlassDialog(
                    backdrop = backdrop, dark = dark, title = "تقييد ${nameOf(uid)}",
                    onDismiss = { restrictDialog = null }
                ) {
                    Column {
                        Text("ما راح يقدر يرسل رسائل طول مدة التقييد.", color = fg.copy(alpha = 0.8f), fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        options.forEach { (label, dur) ->
                            MenuAction(label) {
                                restrictDialog = null
                                val until = if (dur == 0L) 0L else System.currentTimeMillis() + dur
                                act("تم التقييد") { Repo.restrictGroupMember(groupId, uid, until) }
                            }
                        }
                    }
                }
            }
        }

        confirm?.let { c ->
            GlassConfirmDialog(
                backdrop = backdrop,
                dark = dark,
                title = c.title,
                message = c.message,
                confirmText = c.confirmText,
                icon = c.icon,
                onConfirm = { confirm = null; c.onConfirm() },
                onDismiss = { confirm = null }
            )
        }
    }
}

// ============================ عناصر واجهة ============================

@Composable
private fun InfoAction(
    backdrop: Backdrop, dark: Boolean,
    icon: ImageVector, title: String, subtitle: String?, tint: Color, onClick: () -> Unit
) {
    val fg = glassFg(dark)
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth()
            .glassSurface(backdrop, dark, shape, blurDp = 6f)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (tint == DangerColor) DangerColor else fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = fg.copy(alpha = 0.65f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun MemberRow(
    uid: String, me: String, fg: Color, label: String?, restricted: Boolean,
    canManage: Boolean, onManage: () -> Unit, onOpenProfile: (UserItem) -> Unit
) {
    val prof by remember(uid) { Repo.basicProfileFlow(uid) }.collectAsState(initial = Profile())
    val avatar = rememberB64Image(prof.avatar)
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(shape)
            .background(fg.copy(alpha = 0.07f))
            .border(0.8.dp, glassBorderBrush(0.28f), shape)
            // الضغطة على العضو/المشرف تفتح حسابه (نفسك ما تفتح)
            .then(if (uid != me) Modifier.clickable { onOpenProfile(UserItem(uid, prof.name, "", prof.username)) } else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF2B2F36)),
            contentAlignment = Alignment.Center
        ) {
            if (avatar != null) Image(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Filled.Person, null, tint = Color(0xFF8A8F98), modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    prof.name.ifBlank { "مستخدم" } + if (uid == me) " (أنت)" else "",
                    color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                )
                BadgeAfterName(prof, 17.dp)
            }
            if (prof.username.isNotBlank()) {
                Text("@${prof.username}", color = fg.copy(alpha = 0.55f), fontSize = 12.sp, maxLines = 1)
            }
        }
        if (restricted) {
            Badge("مقيّد", DangerColor)
            Spacer(Modifier.width(6.dp))
        }
        if (label != null) Badge(label, AccentBlue)
        // أدوات الإدارة (طرد/تقييد/إشراف) صارت بزر ⋮ منفصل حتى الضغطة على الصف تفتح الحساب
        if (canManage) {
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onManage),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MoreVert, null, tint = fg.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/** سطر صلاحية بمفتاح LiquidToggle (بدل Switch). الضغط/السحب على المفتاح نفسه */
@Composable
private fun SwitchRow(label: String, checked: Boolean, backdrop: Backdrop, dark: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = glassFg(dark), fontSize = 15.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        LiquidToggle(
            selected = { checked },
            onSelect = onChange,
            backdrop = backdrop,
            dark = dark,
            accent = AccentBlue
        )
    }
}

// ============================ الحوارات ============================

@Composable
private fun EditGroupDialog(
    g: GroupFull, backdrop: Backdrop, dark: Boolean,
    onDismiss: () -> Unit, onSave: (String, String, String?) -> Unit
) {
    val fg = glassFg(dark)
    var name by remember { mutableStateOf(g.item.name) }
    var bio by remember { mutableStateOf(g.item.bio) }
    var newAvatar by remember { mutableStateOf<String?>(null) }
    var cropSrc by remember { mutableStateOf<String?>(null) }
    val pick = rememberImagePicker(maxSide = 800, quality = 85) { b64 ->
        // أي صورة تنختار: تفتح شاشة تحريك الصورة لاختيار الجزء المطلوب
        cropSrc = b64
    }
    Box(Modifier.fillMaxSize()) {
    GlassDialog(
        backdrop = backdrop, dark = dark, title = "تعديل المجموعة",
        onDismiss = onDismiss,
        confirmText = "حفظ",
        confirmEnabled = name.isNotBlank(),
        onConfirm = { onSave(name, bio, newAvatar) }
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.clickable(onClick = pick)) { GroupAvatar(newAvatar ?: g.item.avatar, 84.dp) }
            Text(
                "اضغط لتغيير الصورة", color = fg.copy(alpha = 0.65f), fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )
            GlassField(value = name, onValueChange = { name = it.take(40) }, placeholder = "اسم المجموعة", fg = fg, singleLine = true)
            Spacer(Modifier.height(8.dp))
            GlassField(value = bio, onValueChange = { bio = it.take(150) }, placeholder = "نبذة المجموعة", fg = fg, minLines = 2, maxLines = 4)
        }
    }
    cropSrc?.let { src ->
        AvatarCropOverlay(srcB64 = src, onCancel = { cropSrc = null }, onDone = { newAvatar = it; cropSrc = null })
    }
    }
}

@Composable
private fun AddMembersDialog(g: GroupFull, backdrop: Backdrop, dark: Boolean, onDismiss: () -> Unit, onAdd: (Set<String>) -> Unit) {
    val fg = glassFg(dark)
    val friends by remember { Repo.friendIdsFlow() }.collectAsState(initial = emptySet())
    val candidates = friends.filter { it !in g.members && it !in g.banned }
    var picked by remember { mutableStateOf(setOf<String>()) }
    GlassDialog(
        backdrop = backdrop, dark = dark, title = "إضافة أعضاء",
        onDismiss = onDismiss,
        confirmText = if (candidates.isEmpty()) null else if (picked.isEmpty()) "إضافة" else "إضافة (${picked.size})",
        confirmEnabled = picked.isNotEmpty(),
        onConfirm = { onAdd(picked) }
    ) {
        if (candidates.isEmpty()) {
            Text("ما عندك أصدقاء جدد تضيفهم (كلهم أعضاء أو محظورين).", color = fg.copy(alpha = 0.8f), fontSize = 15.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                candidates.forEach { uid ->
                    FriendPickRow(uid, fg, uid in picked) {
                        picked = if (uid in picked) picked - uid else picked + uid
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDialog(current: GroupSettings, backdrop: Backdrop, dark: Boolean, onDismiss: () -> Unit, onSave: (GroupSettings) -> Unit) {
    val fg = glassFg(dark)
    var s by remember { mutableStateOf(current) }
    val slowOptions = listOf(0 to "مطفي", 10 to "10 ث", 30 to "30 ث", 60 to "دقيقة", 300 to "5 د", 900 to "15 د", 3600 to "ساعة")
    GlassDialog(
        backdrop = backdrop, dark = dark, title = "صلاحيات الأعضاء",
        onDismiss = onDismiss,
        confirmText = "حفظ",
        onConfirm = { onSave(s) }
    ) {
        Column {
            Text("شنو يقدر يسوي الأعضاء العاديين (المشرفون لهم صلاحياتهم الخاصة):", color = fg.copy(alpha = 0.8f), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            SwitchRow("إرسال الرسائل", s.sendMessages, backdrop, dark) { s = s.copy(sendMessages = it) }
            SwitchRow("إضافة أعضاء", s.inviteUsers, backdrop, dark) { s = s.copy(inviteUsers = it) }
            SwitchRow("تثبيت الرسائل", s.pinMessages, backdrop, dark) { s = s.copy(pinMessages = it) }
            SwitchRow("تغيير معلومات المجموعة", s.changeInfo, backdrop, dark) { s = s.copy(changeInfo = it) }
            Spacer(Modifier.height(10.dp))
            Text("الوضع البطيء (فاصل بين رسائل العضو الواحد)", color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                slowOptions.forEach { (sec, label) ->
                    val sel = s.slowMode == sec
                    Text(
                        label,
                        color = if (sel) Color.White else fg,
                        fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(CircleShape)
                            .background(if (sel) AccentBlue else fg.copy(alpha = 0.12f))
                            .clickable { s = s.copy(slowMode = sec) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminPermsDialog(
    name: String, existing: GroupAdmin?, backdrop: Backdrop, dark: Boolean,
    onDismiss: () -> Unit, onSave: (GroupPerms, String) -> Unit
) {
    val fg = glassFg(dark)
    var p by remember { mutableStateOf(existing?.perms ?: GroupPerms(deleteMessages = true, pinMessages = true)) }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    GlassDialog(
        backdrop = backdrop, dark = dark,
        title = if (existing == null) "تعيين $name كمشرف" else "صلاحيات $name",
        onDismiss = onDismiss,
        confirmText = if (existing == null) "تعيين" else "حفظ",
        onConfirm = { onSave(p, title) }
    ) {
        Column {
            GlassField(value = title, onValueChange = { title = it.take(16) }, placeholder = "اللقب (اختياري)", fg = fg, singleLine = true)
            Spacer(Modifier.height(8.dp))
            SwitchRow("تغيير معلومات المجموعة", p.changeInfo, backdrop, dark) { p = p.copy(changeInfo = it) }
            SwitchRow("حذف رسائل الأعضاء", p.deleteMessages, backdrop, dark) { p = p.copy(deleteMessages = it) }
            SwitchRow("تقييد وطرد وحظر الأعضاء", p.banUsers, backdrop, dark) { p = p.copy(banUsers = it) }
            SwitchRow("إضافة أعضاء", p.inviteUsers, backdrop, dark) { p = p.copy(inviteUsers = it) }
            SwitchRow("تثبيت الرسائل", p.pinMessages, backdrop, dark) { p = p.copy(pinMessages = it) }
        }
    }
}
