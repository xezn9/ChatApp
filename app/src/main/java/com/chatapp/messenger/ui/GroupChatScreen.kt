package com.chatapp.messenger.ui

import com.chatapp.messenger.ui.theme.AppFont

import com.chatapp.messenger.ui.theme.GlassFx
import com.chatapp.messenger.ui.theme.glassSurfaceLayer

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.LocalFocusManager
import com.chatapp.messenger.UserItem
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Popup
import androidx.compose.material.icons.filled.Add
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import com.chatapp.messenger.ui.theme.isAppDark
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.chatapp.messenger.ui.theme.appBackground
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.GroupAccess
import com.chatapp.messenger.GroupMsg
import com.chatapp.messenger.Msg
import com.chatapp.messenger.Profile
import com.chatapp.messenger.Repo
import com.chatapp.messenger.ui.glass.GlassButton
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private const val GROUP_AVATAR_GAP_MS = 2L * 60_000L

/** رسائل تحركت (انيميشن الظهور) مرة: تبقى بالذاكرة حتى ما تنعاد حتى لو انعاد بناء الشاشة */
private val animatedGroupMsgs = HashSet<String>()

/** الوقت يظهر فقط بعد غياب 5 دقايق أو أكثر (أو بأول رسالة) */
private const val GROUP_TIME_GAP_MS = 5L * 60_000L


/** لفّافة حتى نفرّق بين "لسه يحمّل" (null) و"انحذفت/انطردت" (g == null) */
internal class GroupLoaded(val g: com.chatapp.messenger.GroupFull?)

/** صورة المجموعة الدائرية (أو أيقونة افتراضية) */
@Composable
internal fun GroupAvatar(b64: String, size: Dp) {
    val img = rememberB64Image(b64)
    Box(
        Modifier.size(size).clip(CircleShape).background(Color(0xFF2B2F36)),
        contentAlignment = Alignment.Center
    ) {
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(AppIcons.GroupAdd, null, tint = Color(0xFF8A8F98), modifier = Modifier.size(size * 0.55f))
    }
}

/** أسماء مجموعة مستخدمين (مستمع واحد لكل شخص بدل مستمع لكل رسالة) */
@Composable
internal fun rememberNames(uids: List<String>): Map<String, String> {
    val map = remember { mutableStateMapOf<String, String>() }
    for (u in uids) key(u) {
        LaunchedEffect(u) { Repo.basicProfileFlow(u).collect { map[u] = it.name } }
    }
    return map
}

/** ملفات مجموعة مستخدمين (الاسم + الصورة + شكل الفقاعات) بمستمع واحد لكل شخص */
@Composable
internal fun rememberProfiles(uids: List<String>): Map<String, Profile> {
    val map = remember { mutableStateMapOf<String, Profile>() }
    for (u in uids) key(u) {
        LaunchedEffect(u) { Repo.basicProfileFlow(u).collect { map[u] = it } }
    }
    return map
}

/** صور المرسلين مفكوكة مسبقاً (مرة لكل شخص) حتى تطلع الصورة مع الرسالة فوراً بدون وميض */
@Composable
private fun rememberSenderAvatars(profiles: Map<String, Profile>, uids: List<String>): Map<String, ImageBitmap> {
    val map = remember { mutableStateMapOf<String, ImageBitmap>() }
    for (u in uids) key(u) {
        val img = rememberB64Image(profiles[u]?.avatar.orEmpty())
        LaunchedEffect(img) { if (img != null) map[u] = img else map.remove(u) }
    }
    return map
}

private val NamePalette = listOf(
    Color(0xFFFF7A59), Color(0xFF3AA0FF), Color(0xFF8A5CFF), Color(0xFF00C896),
    Color(0xFFFFA62B), Color(0xFFEF5DA8), Color(0xFF4CC9F0)
)

private fun nameColor(uid: String): Color = NamePalette[(uid.hashCode() and 0x7fffffff) % NamePalette.size]

private class GroupMenuTarget(val msg: GroupMsg, val mine: Boolean, val bounds: Rect)

/**
 * محادثة المجموعة بنفس تصميم المحادثة العادية (Liquid Glass):
 * خلفية خاصة بالمجموعة، فقاعات حسب شكل كل مرسل، شريط علوي وسفلي زجاجي، رسالة مثبتة، وقائمة ضغطة مطولة.
 * الصلاحيات (تقييد/إيقاف الإرسال/الوضع البطيء) نفس السابق، والحماية الفعلية بقواعد قاعدة البيانات.
 */
@Composable
fun GroupChatScreen(groupId: String, onBack: () -> Unit, onOpenInfo: () -> Unit, onOpenProfile: (UserItem) -> Unit = {}) {
    val me = remember { Repo.auth.currentUser?.uid ?: "" }
    val ctx = LocalContext.current
    val st by remember(groupId) { Repo.groupFullFlow(groupId).map { GroupLoaded(it) } }
        .collectAsState(initial = null)
    val messages by remember(groupId) { Repo.groupMessagesFlow(groupId) }
        .collectAsState(initial = emptyList())
    val loaded = st
    val g = loaded?.g
    // انحذفت المجموعة أو انطردت منها: نرجع للقائمة
    LaunchedEffect(loaded) { if (loaded != null && loaded.g == null) onBack() }

    val access = remember(g, me) { if (g != null) GroupAccess(g, me) else null }
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf<GroupMenuTarget?>(null) }
    var lastSentAt by remember { mutableLongStateOf(0L) }
    var showSettings by remember { mutableStateOf(false) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var flashId by remember { mutableStateOf<String?>(null) }

    // ---- مؤشر الكتابة ----
    val typingPath = remember(groupId) { "groupTyping/$groupId" }
    val hideTyping = com.chatapp.messenger.PrivacyState.value.hideOnline
    val typers by remember(typingPath) { Repo.typingFlow(typingPath, me) }.collectAsState(initial = emptySet())
    val typerList = if (hideTyping) emptyList() else typers.toList().take(4)
    val typerProfiles = rememberProfiles(typerList)
    val typingNames = typerList.map { typerProfiles[it]?.name?.takeIf { n -> n.isNotBlank() } ?: "عضو" }
    val someoneTyping = typerList.isNotEmpty()
    val typerAvatar = rememberB64Image(typerProfiles[typerList.firstOrNull()]?.avatar.orEmpty())
    LaunchedEffect(text, hideTyping) {
        if (text.isNotBlank() && !hideTyping) {
            Repo.setTyping(typingPath, true)
            delay(4000)
        }
        Repo.setTyping(typingPath, false)
    }
    DisposableEffect(typingPath) { onDispose { Repo.setTyping(typingPath, false) } }
    val openedAt = remember { System.currentTimeMillis() }
    val fly = remember { Animatable(0f) }

    // ---- خلفية هذي المجموعة (محلية وخاصة بي) ----
    val prefs = remember { ChatPrefs(ctx) }
    val bgKey = "g_$groupId"
    var bgCfg by remember(groupId) { mutableStateOf(prefs.load(me, bgKey)) }
    val updateBg = { nb: ChatBg -> bgCfg = nb; prefs.save(me, bgKey, nb) }
    val bgFile = remember(groupId) { prefs.imageFile(me, bgKey) }
    val hasImage = remember(bgCfg.version) { prefs.hasImage(me, bgKey) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val err = saveChatBackgroundFromUri(ctx, uri, bgFile)
            if (err != null) AppToast.show(err)
            else updateBg(bgCfg.copy(kind = "image", version = System.currentTimeMillis()))
        }
    }

    val custom = bgCfg.custom
    val dark = custom || isAppDark()
    val fg = if (custom) Color.White else MaterialTheme.colorScheme.onBackground
    val bgBackdrop = rememberLayerBackdrop()      // الخلفية فقط (الفقاعات تستخدمها)
    val contentBackdrop = rememberLayerBackdrop() // الخلفية + الرسائل (الأشرطة تستخدمها)

    // ملفات المرسلين (اسم + صورة + شكل الفقاعة)
    val senderIds = remember(messages) { (messages.map { it.from } + me).distinct() }
    val profiles = rememberProfiles(senderIds)
    val avatars = rememberSenderAvatars(profiles, senderIds)
    val myStyleId = profiles[me]?.bubbleStyle ?: "glass"

    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.ime.union(WindowInsets.navigationBars).asPaddingValues().calculateBottomPadding()
    var barHeightPx by remember { mutableIntStateOf(0) }
    val listBottomPadding = if (barHeightPx > 0) with(density) { barHeightPx.toDp() } + 16.dp
    else bottomInset + 52.dp + 28.dp

    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    // مفتاح كل رسالة ثابت (معرّفها)، فما تنعاد حركتها لما السيرفر يأكد الإرسال
    val stable = messages
    val rev = remember(stable) { stable.asReversed() }
    val listState = rememberLazyListState()

    fun act(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppToast.show(e.message?.takeIf { it.isNotBlank() } ?: "صار خطأ، جرّب مرة ثانية")
            }
        }
    }

    fun send() {
        val gg = g ?: return
        val a = access ?: return
        val t = text.trim()
        if (t.isEmpty()) return
        val slow = gg.settings.slowMode
        val now = System.currentTimeMillis()
        if (!a.isAdmin && slow > 0 && now - lastSentAt < slow * 1000L) {
            val left = (slow * 1000L - (now - lastSentAt)) / 1000 + 1
            AppToast.show("الوضع البطيء: انتظر $left ثانية")
            return
        }
        text = ""
        lastSentAt = now
        scope.launch {
            fly.animateTo(1f, tween(170))
            fly.snapTo(-1f)
            fly.animateTo(0f, spring(0.6f, 500f))
        }
        Repo.sendGroupMessage(groupId, t) {
            AppToast.show("ما انرسلت الرسالة: ممنوع عليك الإرسال هسه")
        }
    }

    // ---- رسالة صوتية: ضغطة مطولة على المايك = تسجيل، وبعدها تسمعها أو تحذفها أو ترسلها ----
    var mediaStatus by remember { mutableStateOf<String?>(null) }
    var mediaProgress by remember { mutableFloatStateOf(-1f) }
    // ---- صور / فيديوهات / ملفات بالمجموعة (مع معاينة قبل الإرسال) ----
    var viewImage by remember { mutableStateOf<String?>(null) }
    var viewVideo by remember { mutableStateOf<java.io.File?>(null) }
    var pending by remember { mutableStateOf<PendingAttachment?>(null) }
    var showAttach by remember { mutableStateOf(false) }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && mediaStatus == null) {
            val isVideo = (ctx.contentResolver.getType(uri) ?: "").startsWith("video")
            pending = PendingAttachment(if (isVideo) "video" else "image", uri)
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && mediaStatus == null) scope.launch {
            try {
                val (name, size) = withContext(Dispatchers.IO) { FileUtil.queryMeta(ctx, uri) }
                if (size > FileUtil.MAX_BYTES) {
                    AppToast.show("الملف كبير، الحد الأقصى ${FileUtil.formatSize(FileUtil.MAX_BYTES)}", long = true)
                    return@launch
                }
                mediaProgress = -1f
                mediaStatus = "جاري تجهيز الملف..."
                val f = withContext(Dispatchers.IO) { FileUtil.copyToCache(ctx, uri, name) }
                pending = PendingAttachment("file", uri, name, f.length(), f)
            } catch (e: IllegalStateException) {
                AppToast.show(e.message ?: "تعذّر فتح الملف", long = true)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppToast.show("تعذّر فتح الملف", long = true)
            } finally {
                mediaStatus = null
            }
        }
    }
    fun cancelPending() {
        pending?.file?.delete()
        pending = null
    }
    fun sendPending() {
        val item = pending ?: return
        val gg = g ?: return
        val a = access ?: return
        if (mediaStatus != null) return
        val slow = gg.settings.slowMode
        val now = System.currentTimeMillis()
        if (!a.isAdmin && slow > 0 && now - lastSentAt < slow * 1000L) {
            val left = (slow * 1000L - (now - lastSentAt)) / 1000 + 1
            AppToast.show("الوضع البطيء: انتظر $left ثانية")
            return
        }
        pending = null
        scope.launch {
            var prepared: MediaUtil.Prepared? = null
            val onFail: (Exception) -> Unit = { AppToast.show("ما انرسلت الرسالة: ممنوع عليك الإرسال هسه") }
            try {
                if (item.kind == "file") {
                    val f = item.file ?: throw IllegalStateException("تعذّر فتح الملف")
                    mediaProgress = 0f
                    mediaStatus = "جاري إرسال الملف..."
                    val id = Repo.uploadGroupMedia(groupId, me, f, "file") { mediaProgress = it }
                    runCatching { f.copyTo(FileUtil.localFile(ctx, id, item.name), overwrite = true) }
                    lastSentAt = System.currentTimeMillis()
                    Repo.sendGroupFile(groupId, id, item.name, item.size, onFail)
                } else {
                    val isVideo = item.kind == "video"
                    mediaProgress = -1f
                    mediaStatus = if (isVideo) "جاري تجهيز الفيديو (ضغط)..." else "جاري تجهيز الصورة..."
                    val p = if (isVideo) MediaUtil.prepareVideo(ctx, item.uri) { mediaProgress = it }
                    else MediaUtil.prepareImage(ctx, item.uri)
                    prepared = p
                    mediaStatus = "جاري الإرسال..."
                    mediaProgress = 0f
                    val id = Repo.uploadGroupMedia(groupId, me, p.file, p.type) { mediaProgress = it }
                    runCatching { p.file.copyTo(MediaUtil.mediaFile(ctx, id, p.type), overwrite = true) }
                    lastSentAt = System.currentTimeMillis()
                    Repo.sendGroupMedia(groupId, p.type, id, p.thumb, p.w, p.h, p.durSec, onFail)
                }
                listState.scrollToItem(0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                AppToast.show(e.message ?: "فشل الإرسال", long = true)
            } catch (e: Exception) {
                AppToast.show("فشل الإرسال، تأكد من الإنترنت", long = true)
            } finally {
                prepared?.file?.delete()
                item.file?.delete()
                mediaStatus = null
            }
        }
    }

    /** فتح صورة/فيديو/ملف: ينزل أول مرة وينحفظ بالكاش */
    fun openMedia(m: GroupMsg) {
        if (mediaStatus != null) return
        if (m.mediaType == "file") {
            val name = m.fileName.ifBlank { "file" }
            val lf = FileUtil.localFile(ctx, m.mediaId, name)
            if (lf.exists() && lf.length() > 0L) { FileUtil.open(ctx, lf, name); return }
            scope.launch {
                try {
                    mediaProgress = -1f
                    mediaStatus = "جاري تحميل الملف..."
                    Repo.downloadGroupMedia(groupId, m.mediaId, lf) { mediaProgress = it }
                    FileUtil.open(ctx, lf, name)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppToast.show("تعذّر تحميل الملف (يمكن انحذف أو الإنترنت ضعيف)", long = true)
                } finally {
                    mediaStatus = null
                }
            }
            return
        }
        scope.launch {
            try {
                mediaProgress = -1f
                mediaStatus = "جاري التحميل..."
                val f = MediaUtil.mediaFile(ctx, m.mediaId, m.mediaType)
                if (!f.exists() || f.length() == 0L) {
                    Repo.downloadGroupMedia(groupId, m.mediaId, f) { mediaProgress = it }
                }
                if (m.mediaType == "video") viewVideo = f
                else viewImage = withContext(Dispatchers.IO) {
                    android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppToast.show("تعذّر فتح الملف (يمكن انحذف أو الإنترنت ضعيف)", long = true)
            } finally {
                mediaStatus = null
            }
        }
    }

    val recorder = remember { VoiceRecorder(ctx) }
    var voiceMode by remember { mutableIntStateOf(0) }   // 0 = عادي، 1 = يسجّل، 2 = مراجعة قبل الإرسال
    var recMs by remember { mutableLongStateOf(0L) }
    val recLevels = remember { mutableStateListOf<Float>() }
    var voiceDraft by remember { mutableStateOf<VoiceUtil.Recorded?>(null) }
    val voiceHaptic = LocalHapticFeedback.current
    val voiceFocus = LocalFocusManager.current
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) AppToast.show("هسه اضغط مطولاً على المايك وتكلم")
        else AppToast.show("لازم تسمح باستخدام المايك حتى تسجل رسالة صوتية", long = true)
    }

    fun startRecording() {
        if (voiceMode != 0 || mediaStatus != null) return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        VoicePlayer.stop()
        recLevels.clear()
        if (!recorder.start()) {
            AppToast.show("تعذّر بدء التسجيل", long = true)
            return
        }
        voiceFocus.clearFocus()
        recMs = 0L
        voiceMode = 1
        voiceHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /** رفع الإصبع (أو وصول لأقصى مدة): يوقف التسجيل وينقلك لشريط المعاينة */
    fun finishRecording() {
        if (voiceMode != 1) return
        val r = recorder.stop()
        if (r == null) {
            voiceMode = 0
            AppToast.show("التسجيل قصير جداً، اضغط مطولاً وتكلم")
        } else {
            voiceDraft = r
            voiceMode = 2
        }
    }

    fun discardVoice() {
        VoicePlayer.stop()
        voiceDraft?.file?.delete()
        voiceDraft = null
        voiceMode = 0
    }

    fun sendVoice() {
        val d = voiceDraft ?: return
        val gg = g ?: return
        val a = access ?: return
        if (mediaStatus != null) return
        val slow = gg.settings.slowMode
        val now = System.currentTimeMillis()
        if (!a.isAdmin && slow > 0 && now - lastSentAt < slow * 1000L) {
            val left = (slow * 1000L - (now - lastSentAt)) / 1000 + 1
            AppToast.show("الوضع البطيء: انتظر $left ثانية")
            return
        }
        VoicePlayer.stop()
        scope.launch {
            try {
                mediaProgress = 0f
                mediaStatus = "جاري إرسال الرسالة الصوتية..."
                val id = Repo.uploadGroupMedia(groupId, me, d.file, "audio") { mediaProgress = it }
                // نسخة محلية عند المرسل حتى تشتغل فوراً بدون تنزيل
                runCatching { d.file.copyTo(MediaUtil.mediaFile(ctx, id, "audio"), overwrite = true) }
                lastSentAt = System.currentTimeMillis()
                Repo.sendGroupVoice(groupId, id, ((d.durMs + 500) / 1000).toInt().coerceAtLeast(1), d.wave) {
                    AppToast.show("ما انرسلت الرسالة: ممنوع عليك الإرسال هسه")
                }
                d.file.delete()
                voiceDraft = null
                voiceMode = 0
                listState.scrollToItem(0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // التسجيل يبقى محفوظ حتى تعيد المحاولة
                AppToast.show("فشل الإرسال، تأكد من الإنترنت وجرّب مرة ثانية", long = true)
            } finally {
                mediaStatus = null
            }
        }
    }

    /** تشغيل/إيقاف رسالة صوتية (تنزل أول مرة وتنحفظ بالكاش) */
    fun toggleVoice(m: GroupMsg) {
        if (mediaStatus != null) return
        val af = MediaUtil.mediaFile(ctx, m.mediaId, "audio")
        if (VoicePlayer.playingId == m.id || (af.exists() && af.length() > 0L)) {
            VoicePlayer.toggle(m.id, af)
            return
        }
        scope.launch {
            try {
                mediaProgress = -1f
                mediaStatus = "جاري التحميل..."
                Repo.downloadGroupMedia(groupId, m.mediaId, af) { mediaProgress = it }
                VoicePlayer.toggle(m.id, af)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppToast.show("تعذّر فتح الملف (يمكن انحذف أو الإنترنت ضعيف)", long = true)
            } finally {
                mediaStatus = null
            }
        }
    }

    // أثناء التسجيل: الوقت + مستوى الصوت (للموجة الحيّة وللموجة المحفوظة مع الرسالة)
    LaunchedEffect(voiceMode) {
        if (voiceMode == 1) {
            while (true) {
                recMs = recorder.elapsedMs()
                recLevels.add(recorder.sample())
                if (recLevels.size > 40) recLevels.removeAt(0)
                delay(80)
            }
        }
    }
    DisposableEffect(Unit) {
        recorder.onMaxReached = { finishRecording() }
        onDispose {
            recorder.onMaxReached = null
            recorder.cancel()
            VoicePlayer.stop()
            voiceDraft?.file?.delete()
        }
    }

    // رسالة جديدة (مني: دائماً، من غيري: إذا كنت أقرأ آخر المحادثة) → انزل لها
    val newest = stable.lastOrNull()
    LaunchedEffect(newest?.key) {
        if (newest != null && (newest.from == me || listState.firstVisibleItemIndex <= 1)) {
            listState.scrollToItem(0)
        }
    }
    val imeShown = WindowInsets.ime.getBottom(density) > 0
    LaunchedEffect(imeShown) {
        if (imeShown && listState.firstVisibleItemIndex <= 1) listState.scrollToItem(0)
    }

    BackHandler(enabled = menu != null) { menu = null }

    // الرسالة المثبتة (تتحول لـ Msg حتى نستعمل نفس شريط المحادثة العادية)
    val pin = g?.pinned
    val pinnedList = remember(pin) { if (pin != null) listOf(Msg(pin.id, pin.from, pin.text, 0L)) else emptyList() }
    val pinnedPad by animateDpAsState(if (pinnedList.isNotEmpty()) 58.dp else 0.dp, spring(0.85f, 400f), label = "groupPinnedPad")
    val typingPad by animateDpAsState(if (someoneTyping) 78.dp else 0.dp, spring(0.85f, 400f), label = "typingPad")

    fun jumpToPinned() {
        val p = pin ?: return
        val idx = rev.indexOfFirst { it.id == p.id }
        if (idx < 0) {
            AppToast.show("الرسالة المثبتة قديمة أو انحذفت")
            return
        }
        scope.launch {
            listState.animateScrollToItem(idx)
            flashId = p.id
            delay(900)
            flashId = null
        }
    }

    CompositionLocalProvider(LocalContentColor provides fg) {
        Box(Modifier.fillMaxSize().onSizeChanged { rootSize = it }) {
            Box(Modifier.fillMaxSize().layerBackdrop(contentBackdrop)) {
                // تغيير الخلفية بتلاشي ناعم
                // قاعدة صلبة تحت التلاشي حتى ما تبين الشاشة الي وراها وقت تغيير الخلفية
                Box(Modifier.fillMaxSize().layerBackdrop(bgBackdrop).background(appBackground())) {
                    Crossfade(
                        targetState = bgCfg.copy(blur = false),
                        animationSpec = tween(500),
                        label = "groupBg"
                    ) { c ->
                        ChatBackground(c.copy(blur = bgCfg.blur), bgFile, Modifier.fillMaxSize())
                    }
                }
                if (stable.isEmpty() && g != null) {
                    Text(
                        "ما فيه رسائل بعد.\nابدأ المحادثة!",
                        color = fg.copy(alpha = 0.6f), fontSize = 15.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = true,
                    contentPadding = PaddingValues(
                        start = 12.dp, end = 12.dp,
                        top = statusTop + 76.dp + pinnedPad,
                        bottom = listBottomPadding + typingPad
                    )
                ) {
                    itemsIndexed(rev, key = { _, m -> m.key }) { i, m ->
                        val newer = rev.getOrNull(i - 1)
                        val older = rev.getOrNull(i + 1)
                        val mine = m.from == me
                        // صورة المرسل تظهر جنب كل رسالة (حتى رسائلي)
                        val showAvatar = true
                        // الوقت فوق الرسالة: بأول رسالة أو بعد غياب 5 دقايق أو أكثر
                        val showTime = m.createdAt > 0 &&
                            (older == null || m.createdAt - older.createdAt >= GROUP_TIME_GAP_MS)
                        val tight = older != null && older.from == m.from &&
                            m.createdAt - older.createdAt < GROUP_AVATAR_GAP_MS
                        val fresh = remember(m.key) { m.createdAt >= openedAt - 1500L && animatedGroupMsgs.add(groupId + "/" + m.key) }
                        val prof = profiles[m.from]
                        GroupMessageRow(
                            m = m, mine = mine,
                            avatar = avatars[m.from],
                            showAvatar = showAvatar, tight = tight,
                            time = if (showTime) timeLabel(m.createdAt) else "",
                            style = resolveBubbleStyle(prof?.bubbleStyle ?: "glass", prof?.customBubble ?: ""),
                            dark = dark, fg = fg, backdrop = bgBackdrop,
                            animateIn = fresh,
                            flash = flashId == m.id,
                            modifier = Modifier,
                            onAvatarClick = {
                                // ضغطة على صورة أي عضو تفتح حسابه (صورتي أنا ما تفتح)
                                if (!mine) onOpenProfile(UserItem(m.from, prof?.name.orEmpty(), "", prof?.username.orEmpty()))
                            },
                            onToggleVoice = { toggleVoice(m) },
                            onOpenMedia = { openMedia(m) },
                            onLongPress = { rect ->
                                val a = access
                                if (a != null && (a.canPin || mine || a.canDelete)) {
                                    menu = GroupMenuTarget(m, mine, rect)
                                }
                            }
                        )
                    }
                }

            }

            // ----- مؤشر "يكتب": رسالة فيها ثلاث نقاط بشكل رسالة الكاتب، وتحتها/فوقها اسمه -----
            if (someoneTyping) {
                val first = typerList.first()
                val fp = typerProfiles[first]
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = listBottomPadding - 6.dp)
                        .typingAppear()
                ) {
                    Text(
                        typingLabel(typingNames),
                        fontSize = 11.sp, color = fg.copy(alpha = 0.6f), maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 40.dp, bottom = 2.dp)
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        SenderAvatar(typerAvatar)
                        Spacer(Modifier.width(8.dp))
                        TypingMessageBubble(
                            resolveBubbleStyle(fp?.bubbleStyle ?: "glass", fp?.customBubble ?: ""),
                            dark, fg, bgBackdrop
                        )
                    }
                }
            }

            // ----- شريط الرسالة المثبتة -----
            PinnedBanner(
                backdrop = contentBackdrop, dark = dark, fg = fg, pinned = pinnedList,
                onClick = { jumpToPinned() },
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .padding(top = statusTop + 64.dp, start = 12.dp, end = 12.dp),
                trailing = {
                    if (access?.canPin == true) {
                        Box(
                            Modifier.size(32.dp).clip(CircleShape)
                                .background(fg.copy(alpha = 0.12f))
                                .clickable { act { Repo.pinGroupMessage(groupId, null) } },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Close, "إلغاء التثبيت", tint = fg.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            )

            // ----- الشريط العلوي: رجوع + بطاقة المجموعة + إعدادات -----
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassButton(onClick = onBack, backdrop = contentBackdrop, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(24.dp))
                }
                GlassButton(
                    onClick = onOpenInfo, backdrop = contentBackdrop,
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                ) {
                    GroupAvatar(g?.item?.avatar ?: "", 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(
                            g?.item?.name ?: "", fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (g != null) {
                            if (someoneTyping) {
                                Text(typingLabel(typingNames) + "…", fontSize = 11.sp, color = OnlineGreen, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            } else {
                                Text("${g.item.memberCount} عضو", fontSize = 11.sp, color = fg.copy(alpha = 0.6f), maxLines = 1)
                            }
                        }
                    }
                }
                GlassButton(onClick = { showSettings = true }, backdrop = contentBackdrop, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.Settings, null, modifier = Modifier.size(22.dp))
                }
            }

            // ----- الشريط السفلي: مربع الكتابة + زر الإرسال (حسب الصلاحيات) -----
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .onSizeChanged { barHeightPx = it.height }
                    .navigationBarsPadding().imePadding().padding(12.dp)
            ) {
                // حالة رفع/تنزيل رسالة صوتية
                mediaStatus?.let { st ->
                    MediaStatusPill(backdrop = contentBackdrop, dark = dark, fg = fg, text = st, progress = mediaProgress)
                }
                if (access != null) {
                    if (access.canSend) {
                        val slow = g?.settings?.slowMode ?: 0
                        val rtlNow = LocalLayoutDirection.current == LayoutDirection.Rtl
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (voiceMode == 1) {
                                VoiceRecordingBar(
                                    backdrop = contentBackdrop, dark = dark, fg = fg,
                                    ms = recMs, levels = recLevels,
                                    modifier = Modifier.weight(1f)
                                )
                            } else if (voiceMode == 2) {
                                VoiceReviewBar(
                                    backdrop = contentBackdrop, dark = dark, fg = fg,
                                    file = voiceDraft?.file, durMs = voiceDraft?.durMs ?: 0L, wave = voiceDraft?.wave ?: "",
                                    onDelete = { discardVoice() },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                            Box {
                                GlassButton(
                                    onClick = { showAttach = !showAttach },
                                    backdrop = contentBackdrop,
                                    enabled = mediaStatus == null,
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Add, null, tint = fg,
                                        modifier = Modifier.size(26.dp).graphicsLayer { rotationZ = if (showAttach) 45f else 0f }
                                    )
                                }
                                AttachMenuPopup(
                open = showAttach,
                                        backdrop = contentBackdrop, fg = fg,
                                        onDismiss = { showAttach = false },
                                        onPickMedia = {
                                                showAttach = false
                                                mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                            },
                                        onPickFile = {
                                                showAttach = false
                                                filePicker.launch(arrayOf("*/*"))
                                            }
                                    )
                            }
                            Box(
                                Modifier.weight(1f).heightIn(min = 52.dp)
                                    .drawBackdrop(
                                        backdrop = contentBackdrop,
                                        shape = { Capsule() },
                                        effects = {
                                            vibrancy()
                                            blur((2f.dp.toPx()) * GlassFx.blurK)
                                            lens((12f.dp.toPx()) * GlassFx.refractK, (24f.dp.toPx()) * GlassFx.refractK)
                                        },
                                        onDrawSurface = { glassSurfaceLayer { drawRect(if (dark) Color.Black.copy(0.30f) else Color.White.copy(0.35f)) } }
                                    )
                                    .border(0.8.dp, glassBorderBrush(), RoundedCornerShape(50))
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (text.isEmpty()) {
                                    Text(
                                        if (slow > 0 && !access.isAdmin) "اكتب رسالة (الوضع البطيء: $slow ث)" else "اكتب رسالة",
                                        color = fg.copy(alpha = 0.5f), fontSize = 16.sp
                                    )
                                }
                                BasicTextField(
                                    value = text,
                                    onValueChange = { text = it.take(2000) },
                                    maxLines = 4,
                                    textStyle = TextStyle(fontFamily = AppFont, color = fg, fontSize = 16.sp),
                                    cursorBrush = SolidColor(AccentBlue),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            }
                            // الكتابة فاضية = زر المايك (ضغطة مطولة تسجل)، فيه نص = زر الإرسال، وبالمراجعة = إرسال التسجيل
                            val showMic = voiceMode == 1 || (voiceMode == 0 && text.isBlank())
                            if (showMic) {
                                GlassMicButton(
                                    backdrop = contentBackdrop,
                                    recording = voiceMode == 1,
                                    enabled = mediaStatus == null,
                                    onTap = { if (voiceMode == 0) AppToast.show("اضغط مطولاً على المايك للتسجيل") },
                                    onHoldStart = { startRecording() },
                                    onHoldEnd = { finishRecording() },
                                    modifier = Modifier.size(52.dp)
                                )
                            } else                             GlassButton(
                                onClick = { if (voiceMode == 2) sendVoice() else send() },
                                backdrop = contentBackdrop,
                                tint = AccentBlue,
                                enabled = if (voiceMode == 2) mediaStatus == null else text.isNotBlank(),
                                modifier = Modifier.size(52.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send, null, tint = Color.White,
                                    modifier = Modifier.size(24.dp).graphicsLayer {
                                        val dir = if (rtlNow) -1f else 1f
                                        translationX = fly.value * 22.dp.toPx() * dir
                                        translationY = -fly.value * 22.dp.toPx()
                                        alpha = (1f - abs(fly.value)).coerceIn(0f, 1f)
                                    }
                                )
                            }
                        }
                    } else {
                        Box(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                .drawBackdrop(
                                    backdrop = contentBackdrop,
                                    shape = { Capsule() },
                                    effects = { vibrancy(); blur((2f.dp.toPx()) * GlassFx.blurK); lens((12f.dp.toPx()) * GlassFx.refractK, (24f.dp.toPx()) * GlassFx.refractK) },
                                    onDrawSurface = { glassSurfaceLayer { drawRect(if (dark) Color.Black.copy(0.30f) else Color.White.copy(0.35f)) } }
                                )
                                .border(0.8.dp, glassBorderBrush(), RoundedCornerShape(50))
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (access.isRestricted) "تم تقييدك من الإرسال بهذي المجموعة"
                                else "الإرسال متاح للمشرفين فقط",
                                color = fg.copy(alpha = 0.8f), fontSize = 15.sp, textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // ----- عارض الصورة / الفيديو + معاينة قبل الإرسال -----
            ImageViewerHost(viewImage) { viewImage = null }
            viewVideo?.let { vf -> VideoViewerScreen(vf) { viewVideo = null } }
            pending?.let { pa ->
                AttachmentPreviewScreen(pa, sending = mediaStatus != null, onSend = { sendPending() }, onCancel = { cancelPending() })
            }

            // ----- إعدادات المجموعة: خلفية + فقاعات (نفس لوحة المحادثة العادية) -----
            AnimatedVisibility(
                visible = showSettings,
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(230))
            ) {
                ChatSettingsPanel(
                    backdrop = contentBackdrop,
                    dark = dark,
                    otherName = "",
                    bg = bgCfg,
                    hasImage = hasImage,
                    onBgChange = updateBg,
                    onPickImage = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    styleId = myStyleId,
                    onStyleChange = { Repo.setBubbleStyle(it) },
                    customJson = profiles[me]?.customBubble ?: "",
                    onCustomSelect = { Repo.setCustomBubble(it) },
                    pinned = false,
                    onTogglePin = {},
                    blockedByMe = false,
                    onToggleBlock = {},
                    onReport = {},
                    onDismiss = { showSettings = false },
                    title = "إعدادات المجموعة",
                    personActions = false
                )
            }

            // ----- قائمة الضغطة المطولة على رسالة (Liquid Glass) -----
            val mt = menu
            if (mt != null) {
                val mm = mt.msg
                val a = access
                val actions = buildList<MsgAction> {
                    if (mm.mediaType.isEmpty() && Translator.looksEnglish(mm.text) && mm.id !in Translator.loading) {
                        val shown = Translator.results.containsKey(mm.id)
                        add(MsgAction(AppIcons.Translate, if (shown) "إخفاء الترجمة" else "ترجمة", Color(0xFF00BFA5)) {
                            menu = null
                            Translator.toggle(scope, mm.id, mm.text)
                        })
                    }
                    if (a != null && a.canPin) {
                        val isPinned = pin?.id == mm.id
                        add(MsgAction(AppIcons.Pin, if (isPinned) "إلغاء التثبيت" else "تثبيت", Color(0xFFFFC107)) {
                            menu = null
                            act { Repo.pinGroupMessage(groupId, if (isPinned) null else mm) }
                        })
                    }
                    if (a != null && (mm.from == me || a.canDelete)) {
                        add(MsgAction(Icons.Filled.Delete, "حذف الرسالة", Color(0xFFE5484D)) {
                            menu = null
                            act { Repo.deleteGroupMessage(groupId, mm.id, mm.mediaId) }
                        })
                    }
                }
                val menuProf = profiles[mm.from]
                GlassMessageMenu(
                    backdrop = contentBackdrop, dark = dark,
                    anchorTop = mt.bounds.top, anchorBottom = mt.bounds.bottom,
                    anchorStart = mt.bounds.left, anchorEnd = mt.bounds.right,
                    screenWidth = rootSize.width.toFloat(), screenHeight = rootSize.height.toFloat(),
                    actions = actions,
                    onDismiss = { menu = null }
                ) {
                    StyledBubble(
                        style = resolveBubbleStyle(menuProf?.bubbleStyle ?: "glass", menuProf?.customBubble ?: ""), mine = mt.mine,
                        dark = dark, fg = fg, backdrop = bgBackdrop,
                        modifier = Modifier.widthIn(max = 260.dp)
                    ) { textColor ->
                        Column {
                        Text(mm.text, color = textColor, fontSize = 16.sp)
                        TranslationBlock(mm.id, textColor)
                    }
                    }
                }
            }
        }
    }
}

/** عنصر قائمة داخل نافذة زجاجية (لون النص يتبع الوضع) */
@Composable
internal fun MenuAction(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (danger) Color(0xFFE5484D) else LocalContentColor.current,
        fontSize = 16.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 14.dp)
    )
}

/**
 * سطر رسالة بالمجموعة: صورة المرسل (لآخر رسالة بالتتابع) + فقاعة بشكل المرسل.
 * ضغطة مطولة = قائمة الخيارات (تُرجع حدود الفقاعة).
 */
@Composable
private fun GroupMessageRow(
    m: GroupMsg,
    mine: Boolean,
    avatar: ImageBitmap?,
    showAvatar: Boolean,
    tight: Boolean,
    time: String,
    style: BubbleStyle,
    dark: Boolean,
    fg: Color,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    animateIn: Boolean = false,
    flash: Boolean = false,
    onAvatarClick: () -> Unit = {},
    onToggleVoice: () -> Unit = {},
    onOpenMedia: () -> Unit = {},
    onLongPress: (Rect) -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val boundsHolder = remember { arrayOf(Rect.Zero) }
    fun fireLongPress() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onLongPress(boundsHolder[0])
    }

    // ظهور رسالة جديدة: تطلع من تحت بتكبير نابضي
    val appear = remember { Animatable(if (animateIn) 0f else 1f) }
    LaunchedEffect(Unit) { if (animateIn) appear.animateTo(1f, spring(0.72f, 380f)) }

    // نبضة عند القفز للرسالة (من الشريط المثبت)
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(flash) {
        if (flash) {
            pulse.animateTo(1.08f, tween(160))
            pulse.animateTo(1f, spring(0.4f, 400f))
        }
    }

    Column(
        modifier.fillMaxWidth()
            .padding(top = if (tight) 2.dp else 10.dp)
            .graphicsLayer {
                alpha = appear.value.coerceIn(0f, 1f)
                translationY = (1f - appear.value) * 36.dp.toPx()
                val sc = 0.88f + 0.12f * appear.value
                scaleX = sc; scaleY = sc
            }
    ) {
        // الوقت فوق الرسالة (بأول رسالة أو بعد غياب 5 دقايق أو أكثر)
        if (time.isNotEmpty()) {
            Text(
                time,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                color = fg.copy(alpha = 0.6f)
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            if (!mine) {
                SenderAvatar(avatar, onClick = onAvatarClick)
                Spacer(Modifier.width(8.dp))
            }
            StyledBubble(
                style = style, mine = mine, dark = dark, fg = fg, backdrop = backdrop,
                modifier = Modifier.widthIn(max = 260.dp)
                    .onGloballyPositioned { boundsHolder[0] = it.boundsInRoot() }
                    .graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }
                    .pointerInput(m.id) {
                        detectTapGestures(onLongPress = { fireLongPress() })
                    }
            ) { textColor ->
                // الاسم ما يظهر بالفقاعة: بس صورة المرسل جنبها (ضغطة عليها تفتح حسابه)
                if (m.mediaType == "audio") {
                    VoiceBubble(
                        id = m.id, durSec = m.mediaDur, wave = m.wave, textColor = textColor,
                        onToggle = onToggleVoice, onLongPress = { fireLongPress() }
                    )
                } else if (m.mediaType == "image" || m.mediaType == "video" || m.mediaType == "file") {
                    // نفس فقاعات المحادثة الخاصة (نحوّل الرسالة لشكل Msg)
                    val asMsg = Msg(
                        id = m.id, from = m.from, text = m.text, createdAt = m.createdAt,
                        mediaType = m.mediaType, mediaId = m.mediaId, thumb = m.thumb,
                        mediaW = m.mediaW, mediaH = m.mediaH, mediaDur = m.mediaDur,
                        fileName = m.fileName, fileSize = m.fileSize
                    )
                    if (m.mediaType == "file") FileBubble(asMsg, textColor, onOpenMedia, { fireLongPress() })
                    else MediaThumb(asMsg, textColor, onOpenMedia, { fireLongPress() })
                } else {
                    Column {
                        Text(m.text, color = textColor, fontSize = 16.sp)
                        TranslationBlock(m.id, textColor)
                    }
                }
            }
            if (mine) {
                Spacer(Modifier.width(8.dp))
                SenderAvatar(avatar, onClick = null)
            }
        }
    }
}

/** صورة المرسل الدائرية (أيقونة شخص إذا ما عنده صورة) */
@Composable
private fun SenderAvatar(avatar: ImageBitmap?, onClick: (() -> Unit)? = null) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF2B2F36))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (avatar != null) {
            Image(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(
                Icons.Filled.Person, null,
                tint = Color(0xFF8A8F98), modifier = Modifier.size(18.dp)
            )
        }
    }
}
