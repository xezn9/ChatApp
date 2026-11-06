package com.chatapp.messenger.ui

import com.chatapp.messenger.ui.theme.AppFont

import androidx.compose.material.icons.filled.Star
import androidx.compose.animation.core.tween
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import com.chatapp.messenger.ActiveStory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.chatapp.messenger.GroupItem
import com.chatapp.messenger.GroupPreview
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.ChatItem
import com.chatapp.messenger.Profile
import com.chatapp.messenger.Repo
import com.chatapp.messenger.UserItem
import com.chatapp.messenger.MuteStore
import com.chatapp.messenger.StarStore
import com.chatapp.messenger.RestrictStore
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberUpdatedState
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import com.chatapp.messenger.ui.theme.isAppDark
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** محادثة مضغوطة ضغطة مطولة (لقائمة كتم/تحديد/حذف) */
private data class ChatTarget(val uid: String, val name: String, val lastAt: Long)

/** يتذكر آخر واجهة واتجاه التبديل (1 = للأمام، -1 = للخلف) لحركة الدوّار بين المحادثات/المجموعات/الطلبات */
private class ModeDir(var cur: Int, var dir: Float = 1f)

/**
 * تبويب Home:
 * - بدون بحث: قائمة المحادثات (صورة + اسم الشخص + آخر رسالة)
 * - بالبحث (اسم أو يوزر): نتائج المستخدمين، ضغطة تفتح بروفايله وفيه زر مراسلة
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTab(
    onOpenChat: (UserItem) -> Unit, onOpenProfile: (UserItem) -> Unit, onRequestLogout: () -> Unit,
    onRequestDeleteChat: (uid: String, name: String, lastAt: Long) -> Unit = { _, _, _ -> },
    onOverlayChange: (Boolean) -> Unit = {},
    // 0 = المحادثات العادية، 1 = طلبات الرسائل (المجموعات انتقلت لتبويب BDF) (المبدّل نفسه يرسم من MainScreen فوق هذي الصفحة)
    homeMode: Int = 0,
    // فتح محادثة مجموعة
    onOpenGroup: (GroupItem) -> Unit = {},
    // فتح محادثة الدعم (معرّف صاحب المحادثة: أنا للمستخدم، أو المستخدم الي راسل للمطور)
    onOpenSupport: (String) -> Unit = {},
    // أوامر قائمة ⋮ (الزر والقائمة يُرسمان من MainScreen فوق المبدّل): 1 تحديد، 2 مقدمة، 3 نشاط
    menuCmd: Int = 0,
    onMenuCmdHandled: () -> Unit = {},
    onSelectingChange: (Boolean) -> Unit = {}
) {
    val myUid = remember { Repo.auth.currentUser?.uid ?: "" }
    val users by remember { Repo.usersFlow() }.collectAsState(initial = emptyList())
    val chats by remember(myUid) { Repo.chatsFlow(myUid) }.collectAsState(initial = emptyList())
    val pinned by remember(myUid) { Repo.pinnedFlow(myUid) }.collectAsState(initial = emptySet())
    // null = لسا ما وصلت قائمة المحذوفات: ما نعرض أي محادثة (حتى ما تبين المحذوفة/القديمة لحظة)
    val clearedState by remember(myUid) { Repo.clearedFlow(myUid) }.collectAsState(initial = null as Map<String, Long>?)
    var clearedTimeout by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(2500); clearedTimeout = true }
    val clearedReady = clearedState != null || clearedTimeout
    val cleared = clearedState ?: emptyMap()
    // معاينة بديلة إذا حذفت (من عندي) آخر رسالة بمحادثة
    val previewOverrides by remember(myUid) { Repo.previewOverridesFlow(myUid) }.collectAsState(initial = emptyMap())
    // لكل واجهة بحثها المستقل (حتى ما يتلخبط البحث أثناء حركة التبديل)
    var queryChats by remember { mutableStateOf("") }
    // قصص كل المستخدمين (مع قصتي أولاً)
    val stories by remember { Repo.activeStoriesFlow() }.collectAsState(initial = emptyList())
    var viewIndex by remember { mutableStateOf<Int?>(null) }
    var storySnapshot by remember { mutableStateOf<List<ActiveStory>>(emptyList()) }
    var lastIdx by remember { mutableStateOf(0) }
    // ⋮ أعلى اليسار: تحديد الدردشات / مقدمة الدردشة / النشاط + كتم المحادثات
    val ctx = LocalContext.current
    val bd = rememberLayerBackdrop()
    LaunchedEffect(Unit) { MuteStore.init(ctx) }
    val mutedAll by MuteStore.muted.collectAsState()
    val myMuted = remember(mutedAll, myUid) {
        mutedAll.filter { it.startsWith("$myUid:") }.map { it.removePrefix("$myUid:") }.toSet()
    }
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var actionTarget by remember { mutableStateOf<ChatTarget?>(null) }
    var blockTarget by remember { mutableStateOf<ChatTarget?>(null) }
    LaunchedEffect(Unit) { StarStore.store.init(ctx); RestrictStore.store.init(ctx) }
    val starredAll by StarStore.store.flags.collectAsState()
    val restrictedAll by RestrictStore.store.flags.collectAsState()
    val myStarred = remember(starredAll, myUid) {
        starredAll.filter { it.startsWith("$myUid:") }.map { it.removePrefix("$myUid:") }.toSet()
    }
    val myRestricted = remember(restrictedAll, myUid) {
        restrictedAll.filter { it.startsWith("$myUid:") }.map { it.removePrefix("$myUid:") }.toSet()
    }
    var bulkDelete by remember { mutableStateOf(false) }
    var showIntro by remember { mutableStateOf(false) }
    var showActivity by remember { mutableStateOf(false) }
    LaunchedEffect(menuCmd) {
        when (menuCmd) {
            1 -> { selecting = true; selectedIds = emptySet() }
            2 -> showIntro = true
            3 -> showActivity = true
        }
        if (menuCmd != 0) onMenuCmdHandled()
    }
    LaunchedEffect(selecting) { onSelectingChange(selecting) }
    fun exitSelection() { selecting = false; selectedIds = emptySet() }
    fun toggleSel(uid: String) {
        selectedIds = if (uid in selectedIds) selectedIds - uid else selectedIds + uid
        if (selectedIds.isEmpty()) selecting = false
    }
    BackHandler(enabled = selecting && actionTarget == null && !bulkDelete) { exitSelection() }
    val anyOverlay = viewIndex != null || showIntro || showActivity
    LaunchedEffect(anyOverlay) { onOverlayChange(anyOverlay) }
    DisposableEffect(Unit) { onDispose { onOverlayChange(false) } }

    // المثبتين (الأشخاص المميزين) أولاً، حتى لو ما عندهم رسائل بعد
    val shownChats = remember(chats, pinned, cleared, clearedReady) {
        if (!clearedReady) return@remember emptyList<ChatItem>()
        // المحادثات المحذوفة من عندي تنخفي (لين توصل رسالة جديدة)
        // (وطلبات الرسائل ما تظهر هنا: لها واجهتها الخاصة لين أوافق عليها)
        val visible = chats.filter { !it.req && it.lastAt > (cleared[it.uid] ?: 0L) }
        val byUid = visible.associateBy { it.uid }
        val top = pinned.map { byUid[it] ?: ChatItem(it, "", 0L) }.sortedByDescending { it.lastAt }
        top + visible.filter { it.uid !in pinned }
    }

    // طلبات الرسائل: أول رسالة من شخص جديد وأنا مشغّل "طلبات الرسائل"، بانتظار موافقتي
    val requests = remember(chats, cleared, clearedReady) {
        if (!clearedReady) emptyList() else chats.filter { it.req && it.lastAt > (cleared[it.uid] ?: 0L) }
    }


    // الدعم الفني: محادثتي مع "ChatApp" (إذا راسلت أو وصلني رد)، وللمطور صندوق دعم بكل المستخدمين
    val isDev = rememberIsDev()
    val myThread by remember(myUid) { Support.threadFlow(myUid) }.collectAsState(initial = null)
    val devThreads by remember(isDev) {
        if (isDev) Support.allThreadsFlow() else kotlinx.coroutines.flow.flowOf(emptyList<SupportThread>())
    }.collectAsState(initial = emptyList())
    val myShownThread = myThread?.takeIf { clearedReady && it.lastAt > 0 && it.lastAt > (cleared["support:$myUid"] ?: 0L) }
    val inbox = if (!clearedReady) emptyList() else devThreads.filter { it.uid != myUid && it.lastAt > (cleared["support:${it.uid}"] ?: 0L) }

    // اتجاه التبديل بين الواجهات (يزيد = للأمام): نحسبه أثناء التركيب حتى تعرف الواجهتان جهة حركتهما
    val modeDir = remember { ModeDir(homeMode) }
    if (modeDir.cur != homeMode) {
        modeDir.dir = if (homeMode > modeDir.cur) 1f else -1f
        modeDir.cur = homeMode
    }

    Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().layerBackdrop(bd)) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = homeMode,
                        transitionSpec = {
                            (slideInVertically(spring(0.85f, 420f)) { it / 2 } + fadeIn(tween(200))) togetherWith
                                (slideOutVertically(tween(160)) { -it / 2 } + fadeOut(tween(120)))
                        },
                        label = "homeTitle"
                    ) { m -> Text(if (selecting && m == 0) "تم تحديد ${selectedIds.size}" else when (m) { 1 -> "طلبات الرسائل"; else -> "المحادثات" }) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // مكان المبدّل (يرسم من MainScreen): 68dp من فوق + ارتفاعه 44dp
            Spacer(Modifier.height(56.dp))
            // حركة التبديل: الواجهتان تدوران بشكل ثلاثي الأبعاد مثل دوّار (Carousel):
            // الي يطلع ينزلق للجنب ويدور ويصغر ويخفت، والي يدخل يجي من الجهة المقابلة بنبضة نابضية
            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            AnimatedContent(
                targetState = homeMode,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    (fadeIn(tween(240, delayMillis = 50)) togetherWith fadeOut(tween(150)))
                        .using(SizeTransform(clip = false))
                },
                label = "homeMode"
            ) { mode ->
                // الواجهة الي تطلع تنزلق لجهة، والي تدخل تجي من الجهة المقابلة (حسب اتجاه التبديل واتجاه الواجهة)
                val entering = mode == homeMode
                val side = (if (entering) -modeDir.dir else modeDir.dir) * (if (rtl) 1f else -1f)
                val p by transition.animateFloat(
                    transitionSpec = { spring(dampingRatio = 0.78f, stiffness = 240f) },
                    label = "homeFlip"
                ) { st -> if (st == EnterExitState.Visible) 0f else side }
                val query = queryChats
                val q = query.trim().removePrefix("@").lowercase()
                val results = if (q.isEmpty()) emptyList() else users.filter {
                    it.uid != myUid && (it.name.lowercase().contains(q) || it.username.lowercase().contains(q))
                }
                Column(
                    Modifier.fillMaxSize().graphicsLayer {
                        val a = kotlin.math.abs(p)
                        translationX = p * size.width * 0.42f
                        rotationY = -p * 36f
                        cameraDistance = 14f * density
                        val sc = 1f - 0.12f * a
                        scaleX = sc
                        scaleY = sc
                    }
                ) {
            if (mode != 1) SearchField(query, "ابحث بالاسم أو اليوزر") { queryChats = it }
            if (q.isEmpty() && mode == 0) {
                StoriesRow(stories, myUid) { i ->
                    storySnapshot = stories
                    viewIndex = i
                }
            }

            when {
                // واجهة طلبات الرسائل: أول رسائل الأشخاص الجدد (ضغطة تفتح الطلب وفيه قبول/حذف)
                mode == 1 -> if (requests.isEmpty()) {
                    CenterText("ما عندك طلبات رسائل.\nإذا شغّلت \"طلبات الرسائل\" من الإعدادات > الخصوصية، رسائل الأشخاص الجدد تجيك هنا.")
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 130.dp)) {
                        item(key = "requestsHint") {
                            Text(
                                "هذي رسائل من أشخاص ما راسلتهم قبل. ما يشوفون إنك قريت الرسالة، وما يگدرون يرسلون غيرها لين توافق.",
                                fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                            )
                        }
                        items(requests, key = { it.uid }) { c ->
                            val u = users.firstOrNull { it.uid == c.uid }
                            val toItem = { p: Profile ->
                                UserItem(c.uid, p.name.ifBlank { u?.name ?: "" }, u?.email ?: "", p.username.ifBlank { u?.username ?: "" })
                            }
                            UserRow(
                                uid = c.uid,
                                fallbackName = u?.name ?: "",
                                secondary = { c.lastText },
                                unread = c.unread,
                                onLongClick = { p ->
                                    onRequestDeleteChat(c.uid, p.name.ifBlank { u?.name ?: "" }.ifBlank { "مستخدم" }, c.lastAt)
                                },
                                onClick = { p -> onOpenChat(toItem(p)) },
                                onAvatarClick = { p -> onOpenProfile(toItem(p)) }
                            )
                        }
                    }
                }
                q.isNotEmpty() -> {
                    if (results.isEmpty()) {
                        CenterText("ما لقيت أحد بهذا الاسم أو اليوزر")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 130.dp)) {
                            items(results, key = { it.uid }) { u ->
                                UserRow(
                                    uid = u.uid,
                                    fallbackName = u.name,
                                    secondary = { p -> "@" + p.username.ifBlank { u.username } },
                                    onClick = { onOpenProfile(u) },
                                    onAvatarClick = { onOpenProfile(u) }
                                )
                            }
                        }
                    }
                }
                !clearedReady -> Box(Modifier.fillMaxSize())
                shownChats.isEmpty() && myShownThread == null && inbox.isEmpty() -> CenterText("ما عندك محادثات بعد.\nابحث عن شخص بالاسم أو اليوزر حتى تبدأ محادثة.")
                else -> {
                    LazyColumn(
                        // مسافة بالأسفل حتى ما يختفي آخر عنصر خلف الشريط الزجاجي
                        contentPadding = PaddingValues(bottom = 130.dp)
                    ) {
                        if (myShownThread != null) {
                            item(key = "supportMine") { SupportChatRow(myShownThread, onLongClick = { onRequestDeleteChat("support:$myUid", "ChatApp", myShownThread.lastAt) }) { onOpenSupport(myUid) } }
                        }
                        items(inbox, key = { "support_" + it.uid }) { th ->
                            SupportInboxRow(th, onLongClick = { onRequestDeleteChat("support:${th.uid}", th.name.ifBlank { th.username }.ifBlank { "مستخدم" }, th.lastAt) }) { onOpenSupport(th.uid) }
                        }
                        items(shownChats, key = { it.uid }) { c ->
                            val u = users.firstOrNull { it.uid == c.uid }
                            val toItem = { p: Profile ->
                                UserItem(
                                    c.uid,
                                    p.name.ifBlank { u?.name ?: "" },
                                    u?.email ?: "",
                                    p.username.ifBlank { u?.username ?: "" }
                                )
                            }
                            UserRow(
                                uid = c.uid,
                                fallbackName = u?.name ?: "",
                                secondary = {
                                    val o = previewOverrides[c.uid]
                                    // تنطبق فقط إذا آخر نص ما زال نفس الي حذفته ولا وصلت رسالة جديدة بعده
                                    if (o != null && c.lastText == o.forText && c.lastAt <= o.at) o.text else c.lastText
                                },
                                pinned = c.uid in pinned,
                                unread = c.unread,
                                showPresence = true,
                                muted = c.uid in myMuted,
                                starred = c.uid in myStarred,
                                restricted = c.uid in myRestricted,
                                selectMode = selecting,
                                selected = c.uid in selectedIds,
                                // ضغطة مطولة = قائمة (كتم / تحديد / حذف)
                                onLongClick = { p ->
                                    if (selecting) toggleSel(c.uid)
                                    else actionTarget = ChatTarget(
                                        c.uid,
                                        p.name.ifBlank { u?.name ?: "" }.ifBlank { "مستخدم" },
                                        c.lastAt
                                    )
                                },
                                onClick = { p -> if (selecting) toggleSel(c.uid) else onOpenChat(toItem(p)) },
                                onAvatarClick = { p -> if (selecting) toggleSel(c.uid) else onOpenProfile(toItem(p)) }
                            )
                        }
                    }
                }
            }
                }
            }
        }
    }
    }

    // ⋮ (أعلى اليسار) أو أزرار التحديد المتعدد: دايماً بالجهة اليسرى الفعلية
    // ملاحظة: align() يتحدد باتجاه الأب (RTL) مو باتجاه الـ Row، فنختار الطرف حسب اتجاه الشاشة الحقيقي
    val screenRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            Modifier.align(if (screenRtl) Alignment.TopEnd else Alignment.TopStart)
                .statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val fgc = MaterialTheme.colorScheme.onBackground
            if (selecting) {
                val allMuted = selectedIds.isNotEmpty() && selectedIds.all { it in myMuted }
                SelectPill("✕", fgc) { exitSelection() }
                SelectPill(if (allMuted) "🔔 إلغاء الكتم" else "🔕 كتم", fgc, enabled = selectedIds.isNotEmpty()) {
                    MuteStore.setMany(ctx, selectedIds, !allMuted)
                    AppToast.show(if (allMuted) "تم إلغاء الكتم" else "تم كتم ${selectedIds.size} محادثة")
                    exitSelection()
                }
                SelectPill("🗑️ حذف", Color(0xFFFF5A5F), enabled = selectedIds.isNotEmpty()) { bulkDelete = true }
            }
        }
    }

    // عارض القصص بملء الشاشة (يتنقل للقصة التالية تلقائياً)
    val vi = viewIndex
    if (vi != null) lastIdx = vi
    AnimatedVisibility(
        visible = vi != null,
        enter = fadeIn(tween(180)) + scaleIn(spring(0.85f, 400f), initialScale = 0.88f),
        exit = fadeOut(tween(160)) + scaleOut(tween(180), targetScale = 0.92f)
    ) {
        storySnapshot.getOrNull(lastIdx)?.let { a ->
            val st = a.profile.story
            if (st != null) {
                key(a.uid) {
                    StoryViewerScreen(
                        story = st,
                        owner = a.profile,
                        mine = a.uid == myUid,
                        showMenu = false,
                        onDismiss = { viewIndex = null },
                        ownerUid = a.uid,
                        onOpenViewer = { u -> viewIndex = null; onOpenProfile(u) },
                        // ضغطة على صورة/اسم صاحب القصة تفتح ملفه الشخصي
                        onOpenProfile = {
                            viewIndex = null
                            onOpenProfile(UserItem(a.uid, a.profile.name, "", a.profile.username))
                        },
                        onFinished = {
                            val n = lastIdx + 1
                            viewIndex = if (n < storySnapshot.size) n else null
                        }
                    )
                }
            }
        }
    }

    // قائمة الضغط المطوّل على محادثة
    actionTarget?.let { t ->
        val isBlocked by remember(t.uid) { Repo.blockedFlow(myUid, t.uid) }.collectAsState(initial = false)
        ChatActionSheet(
            backdrop = bd,
            title = t.name,
            pinned = t.uid in pinned,
            starred = t.uid in myStarred,
            muted = t.uid in myMuted,
            restricted = t.uid in myRestricted,
            blocked = isBlocked,
            onPin = {
                val now = t.uid !in pinned
                Repo.setPinned(t.uid, now)
                AppToast.show(if (now) "تم تثبيت ${t.name}" else "تم إلغاء تثبيت ${t.name}")
                actionTarget = null
            },
            onStar = {
                val now = t.uid !in myStarred
                StarStore.store.set(ctx, t.uid, now)
                AppToast.show(if (now) "تم تمييز ${t.name} بنجمة" else "تمت إزالة النجمة عن ${t.name}")
                actionTarget = null
            },
            onMute = {
                val nowMuted = t.uid !in myMuted
                MuteStore.setMuted(ctx, t.uid, nowMuted)
                AppToast.show(if (nowMuted) "تم كتم ${t.name}" else "تم إلغاء كتم ${t.name}")
                actionTarget = null
            },
            onRestrict = {
                val now = t.uid !in myRestricted
                RestrictStore.store.set(ctx, t.uid, now)
                AppToast.show(if (now) "تم تقييد ${t.name}" else "تم إلغاء تقييد ${t.name}")
                actionTarget = null
            },
            onSelect = { selecting = true; selectedIds = setOf(t.uid); actionTarget = null },
            onBlock = {
                if (isBlocked) {
                    Repo.setBlocked(t.uid, false)
                    AppToast.show("تم إلغاء حظر ${t.name}")
                } else {
                    blockTarget = t
                }
                actionTarget = null
            },
            onDelete = { actionTarget = null; onRequestDeleteChat(t.uid, t.name, t.lastAt) },
            onDismiss = { actionTarget = null }
        )
    }
    // تأكيد حظر شخص
    blockTarget?.let { t ->
        GlassConfirmDialog(
            backdrop = bd,
            dark = isAppDark(),
            title = "حظر ${t.name}",
            message = "ما راح يقدر يراسلك طول ما هو محظور. تقدر تلغي الحظر بأي وقت.",
            confirmText = "حظر",
            icon = AppIcons.Block,
            onConfirm = {
                Repo.setBlocked(t.uid, true)
                AppToast.show("تم حظر ${t.name}")
                blockTarget = null
            },
            onDismiss = { blockTarget = null }
        )
    }
    // تأكيد حذف عدة محادثات (من عندي فقط)
    if (bulkDelete) {
        GlassConfirmDialog(
            backdrop = bd,
            dark = isAppDark(),
            title = "حذف المحادثات",
            message = "راح تنحذف ${selectedIds.size} محادثة من عندك فقط. الطرف الثاني تبقى عنده المحادثات كاملة.",
            confirmText = "حذف",
            icon = Icons.Filled.Delete,
            onConfirm = {
                val ids = selectedIds.toList()
                bulkDelete = false
                ids.forEach { id ->
                    val c = shownChats.firstOrNull { it.uid == id }
                    Repo.clearChat(myUid, id, c?.lastAt ?: 0L)
                }
                exitSelection()
            },
            onDismiss = { bulkDelete = false }
        )
    }
    // مقدمة الدردشة
    AnimatedVisibility(
        visible = showIntro,
        enter = slideInHorizontally(spring(0.86f, 420f)) { it } + fadeIn(tween(180)),
        exit = slideOutHorizontally(tween(240)) { it } + fadeOut(tween(200))
    ) { ChatIntroScreen(onDismiss = { showIntro = false }) }
    // النشاط: آخر زيارات / إعجابات القصص / متابعين
    AnimatedVisibility(
        visible = showActivity,
        enter = slideInHorizontally(spring(0.86f, 420f)) { it } + fadeIn(tween(180)),
        exit = slideOutHorizontally(tween(240)) { it } + fadeOut(tween(200))
    ) { ActivityScreen(onDismiss = { showActivity = false }, onOpenProfile = { onOpenProfile(it) }) }
    }
}

/**
 * تبويب BDF = المجموعات: مجموعاتي الحقيقية + بحث عن مجموعة (بالاسم أو اليوزر) + انضمام للمجموعات العامة.
 * (زر إنشاء مجموعة ونافذته يرسمهم MainScreen فوق هذا التبويب)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsTab(onOpenGroup: (GroupItem) -> Unit, onRequestLogout: () -> Unit) {
    val myUid = remember { Repo.auth.currentUser?.uid ?: "" }
    val groups by remember(myUid) { Repo.groupsFlow(myUid) }.collectAsState(initial = emptyList())
    var queryGroups by remember { mutableStateOf("") }
    // نتيجة البحث عن مجموعة (باليوزر) من كل المجموعات، مو بس مجموعاتي
    var remoteGroup by remember { mutableStateOf<GroupPreview?>(null) }
    var remoteSearching by remember { mutableStateOf(false) }
    var joiningId by remember { mutableStateOf<String?>(null) }
    val homeScope = rememberCoroutineScope()
    LaunchedEffect(queryGroups) {
        val h = queryGroups.trim().removePrefix("@").lowercase()
        remoteGroup = null
        if (!Repo.isValidUsername(h)) { remoteSearching = false; return@LaunchedEffect }
        remoteSearching = true
        kotlinx.coroutines.delay(400)
        remoteGroup = try { Repo.findGroupByHandle(h) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
        remoteSearching = false
    }
    val q = queryGroups.trim().removePrefix("@").lowercase()
    val shownGroups = if (q.isEmpty()) groups else groups.filter {
        it.name.lowercase().contains(q) || it.handle.lowercase().contains(q)
    }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("المجموعات") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(queryGroups, "ابحث عن مجموعة") { queryGroups = it }
            run {
                    // المجموعة الي لقيتها باليوزر (إذا مو من مجموعاتي أصلاً)
                    val remote = remoteGroup?.takeIf { r -> groups.none { it.id == r.id } }
                    if (shownGroups.isEmpty() && remote == null) {
                        CenterText(
                            when {
                                q.isEmpty() -> "ما عندك مجموعات بعد.\nاضغط زر إنشاء مجموعة حتى تبدأ أول مجموعة."
                                remoteSearching -> "جاري البحث..."
                                else -> "ما لقيت مجموعة بهذا الاسم أو اليوزر"
                            }
                        )
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 130.dp)) {
                            items(shownGroups, key = { it.id }) { g ->
                                GroupRow(g, onClick = { onOpenGroup(g) })
                            }
                            if (remote != null) {
                                item(key = "remote_" + remote.id) {
                                    RemoteGroupRow(
                                        r = remote,
                                        joining = joiningId == remote.id,
                                        onJoin = {
                                            joiningId = remote.id
                                            homeScope.launch {
                                                try {
                                                    Repo.joinGroup(remote.id)
                                                    AppToast.show("انضممت للمجموعة")
                                                    onOpenGroup(GroupItem(remote.id, remote.name, remote.handle, remote.bio, remote.avatar, "", 1, 0L))
                                                } catch (e: CancellationException) {
                                                    throw e
                                                } catch (e: Exception) {
                                                    AppToast.show(e.message?.takeIf { it.isNotBlank() } ?: "ما قدرت أنضم، جرّب مرة ثانية")
                                                } finally {
                                                    joiningId = null
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                            }
        }
    }
}

@Composable
private fun CenterText(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
    }
}

/** مربع بحث بستايل iOS */
@Composable
private fun SearchField(value: String, hint: String = "ابحث بالاسم أو اليوزر", onChange: (String) -> Unit) {
    val fg = MaterialTheme.colorScheme.onBackground
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(shape).background(fg.copy(alpha = 0.08f))
            .border(0.8.dp, fg.copy(alpha = 0.12f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, null, tint = fg.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(hint, color = fg.copy(alpha = 0.5f), fontSize = 16.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(fontFamily = AppFont, color = fg, fontSize = 16.sp),
                cursorBrush = SolidColor(AccentBlue),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Filled.Close, null, tint = fg.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp).clickable { onChange("") }
            )
        }
    }
}

/** صف مستخدم: صورة الملف الشخصي + الاسم + سطر ثانوي (آخر رسالة أو @اليوزر) */
@Composable
private fun UserRow(
    uid: String,
    fallbackName: String,
    secondary: (Profile) -> String,
    onClick: (Profile) -> Unit,
    onAvatarClick: (Profile) -> Unit,
    pinned: Boolean = false,
    unread: Int = 0,
    showPresence: Boolean = false,
    muted: Boolean = false,
    starred: Boolean = false,
    restricted: Boolean = false,
    selectMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: ((Profile) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    // نستعمل آخر نسخة من الـ lambdas (وضع التحديد يتغير بدون ما يتغير مفتاح الـ pointerInput)
    val clickNow by rememberUpdatedState(onClick)
    val longNow by rememberUpdatedState(onLongClick)
    val prof by remember(uid) { Repo.basicProfileFlow(uid) }.collectAsState(initial = Profile())
    val avatar = rememberB64Image(prof.avatar)
    val shown = prof.name.ifBlank { fallbackName }.ifBlank { "مستخدم" }
    // نقطة "متصل" الخضراء (فقط لصفوف المحادثات، حتى ما نفتح مستمع لكل نتيجة بحث)
    val online = showPresence && rememberPresenceStatus(uid).online

    val rowMod = if (onLongClick != null) {
        // ضغطة = فتح المحادثة، ضغطة مطولة = حذف المحادثة
        Modifier.fillMaxWidth().pointerInput(uid, prof) {
            detectTapGestures(
                onTap = { clickNow(prof) },
                onLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    longNow?.invoke(prof)
                }
            )
        }
    } else Modifier.fillMaxWidth().clickable { onClick(prof) }
    Row(
        rowMod.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // دائرة التحديد (وضع تحديد الدردشات)
        if (selectMode) {
            Box(
                Modifier.padding(end = 12.dp).size(24.dp).clip(CircleShape)
                    .background(if (selected) AccentBlue else Color.Transparent)
                    .border(2.dp, if (selected) AccentBlue else MaterialTheme.colorScheme.onBackground.copy(0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) { if (selected) Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
        Box(Modifier.size(52.dp)) {
            Box(
                Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFF2B2F36)).clickable { onAvatarClick(prof) },
                contentAlignment = Alignment.Center
            ) {
                if (avatar != null) {
                    Image(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Filled.Person, null, tint = Color(0xFF8A8F98), modifier = Modifier.size(28.dp))
                }
            }
            OnlineDot(online)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    shown, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                )
                BadgeAfterName(prof, 18.dp)
                val dim = MaterialTheme.colorScheme.onBackground.copy(0.55f)
                if (muted) Icon(AppIcons.BellOff, null, tint = dim, modifier = Modifier.padding(start = 6.dp).size(14.dp))
                if (restricted) Icon(AppIcons.Restrict, null, tint = dim, modifier = Modifier.padding(start = 6.dp).size(14.dp))
                if (starred) Icon(AppIcons.StarFilled, null, tint = Color(0xFFFFC107), modifier = Modifier.padding(start = 6.dp).size(15.dp))
                if (pinned) Icon(AppIcons.Pin, null, tint = dim, modifier = Modifier.padding(start = 6.dp).size(14.dp))
            }
            Text(
                secondary(prof), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (unread > 0) 0.9f else 0.6f)
            )
        }
        // شارة عدد الرسائل الجديدة (مثل تيك توك)
        AnimatedVisibility(
            visible = unread > 0,
            enter = scaleIn(spring(0.45f, 500f)) + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Box(
                Modifier.padding(start = 8.dp).defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
                    .clip(CircleShape).background(Color(0xFFFF3B5C)).padding(horizontal = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (unread > 99) "99+" else unread.toString(),
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** صف مجموعة: صورة المجموعة + الاسم + آخر رسالة (أو @المعرّف وعدد الأعضاء) */
/** نتيجة بحث عن مجموعة مو أنا عضو فيها: زر انضمام إذا مشاركة، وإلا "تحتاج دعوة" */
@Composable
private fun RemoteGroupRow(r: GroupPreview, joining: Boolean, onJoin: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GroupAvatar(r.avatar, 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "@" + r.handle + if (r.bio.isNotBlank()) " · " + r.bio else "",
                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        Spacer(Modifier.width(8.dp))
        if (r.publicJoin) {
            Text(
                if (joining) "..." else "انضمام",
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(CircleShape)
                    .background(Color(0xFF2F80FF))
                    .clickable(enabled = !joining, onClick = onJoin)
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            )
        } else {
            Text(
                "خاصة · تحتاج دعوة", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
private fun GroupRow(g: GroupItem, onClick: () -> Unit) {
    val last by remember(g.id) { Repo.groupLastMessageFlow(g.id) }.collectAsState(initial = null)
    val members = if (g.memberCount == 1) "عضو واحد" else "${g.memberCount} أعضاء"
    val secondary = last?.text?.takeIf { it.isNotBlank() } ?: buildString {
        if (g.handle.isNotBlank()) append("@").append(g.handle).append(" · ")
        append(members)
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GroupAvatar(g.avatar, 52.dp, g.id, g.avatarGifV)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                g.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                secondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

/** زر نصي صغير بشريط التحديد المتعدد */
@Composable
private fun SelectPill(label: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.height(40.dp).clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onBackground.copy(if (enabled) 0.12f else 0.06f))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, color = color.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
}
