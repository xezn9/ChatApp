package com.chatapp.messenger

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.chatapp.messenger.ui.GroupAvatar
import com.chatapp.messenger.ui.theme.ChatTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay

/** شاشة المكالمة الجماعية (فوق شاشة القفل) */
class GroupCallActivity : ComponentActivity() {

    private var afterPermission: (() -> Unit)? = null

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val next = afterPermission
        afterPermission = null
        if (ok) next?.invoke() else {
            Toast.makeText(this, "لازم تسمح باستخدام المايك حتى تشتغل المكالمة", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        volumeControlStream = android.media.AudioManager.STREAM_VOICE_CALL

        if (!handle(intent)) { finish(); return }
        setContent { ChatTheme { GroupCallScreen(onClose = { finish() }) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!handle(intent)) finish()
    }

    private fun handle(i: Intent?): Boolean {
        val g = i?.getStringExtra(EXTRA_GID)
        if (g != null && !GroupCallManager.isBusy) {
            if (CallManager.isBusy) {
                Toast.makeText(this, "عندك مكالمة ثانية شغالة", Toast.LENGTH_LONG).show()
                return false
            }
            val name = i.getStringExtra(EXTRA_NAME).orEmpty()
            i.removeExtra(EXTRA_GID)
            withMic { GroupCallManager.join(this, g, name) }
            return true
        }
        return GroupCallManager.isBusy || GroupCallManager.phase == GroupCallManager.Phase.ENDED
    }

    private fun withMic(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            afterPermission = action
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    companion object {
        const val EXTRA_GID = "gid"
        const val EXTRA_NAME = "gname"

        /** يبدأ مكالمة جماعية أو ينضم للموجودة */
        fun open(ctx: Context, gid: String, name: String) {
            ctx.startActivity(
                Intent(ctx, GroupCallActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_GID, gid)
                    .putExtra(EXTRA_NAME, name)
            )
        }
    }
}

@Composable
private fun GroupCallScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val phase = GroupCallManager.phase
    var everBusy by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showList by remember { mutableStateOf(false) }
    val backdrop = rememberLayerBackdrop()

    LaunchedEffect(phase) {
        if (phase == GroupCallManager.Phase.ACTIVE) everBusy = true
        if (phase == GroupCallManager.Phase.IDLE && everBusy) onClose()
        while (phase == GroupCallManager.Phase.ACTIVE) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val notice = GroupCallManager.notice
    LaunchedEffect(notice) {
        if (notice.isNotBlank()) {
            Toast.makeText(ctx, notice, Toast.LENGTH_SHORT).show()
            GroupCallManager.clearNotice()
        }
    }

    val self = GroupCallManager.selfMember
    val total = GroupCallManager.members.size + 1
    val anyLevel = maxOf(self?.level ?: 0f, GroupCallManager.members.maxOfOrNull { it.level } ?: 0f)
    val glow by animateFloatAsState(anyLevel, tween(250), label = "glow")
    val ended = phase == GroupCallManager.Phase.ENDED
    val redShift by animateFloatAsState(if (ended) 1f else 0f, tween(500), label = "red")

    val status = when (phase) {
        GroupCallManager.Phase.ACTIVE -> formatCallTime(now - GroupCallManager.activeSince)
        GroupCallManager.Phase.ENDED -> GroupCallManager.endReason.ifBlank { "انتهت المكالمة" }
        GroupCallManager.Phase.IDLE -> ""
    }

    Box(Modifier.fillMaxSize()) {
        // الخلفية المتحركة = مصدر الزجاج (تتوهج لما أي واحد يحچي)
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            CallAurora(glow = glow, red = redShift)
        }

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            GroupHeader(backdrop, status, total, phase == GroupCallManager.Phase.ACTIVE)
            Spacer(Modifier.height(14.dp))

            // شبكة المشاركين: ترتيب تلقائي حسب العدد
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (phase == GroupCallManager.Phase.ACTIVE || ended) {
                    ParticipantGrid(
                        backdrop = backdrop, self = self, members = GroupCallManager.members,
                        hostUid = GroupCallManager.hostUid, myMuted = GroupCallManager.muted
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            if (phase == GroupCallManager.Phase.ACTIVE) {
                ControlBar(backdrop, total, showList, onMembers = { showList = true })
            }
            Spacer(Modifier.height(14.dp))
        }

        // قائمة الأعضاء (تنزلق من تحت فوق ستارة معتمة)
        AnimatedVisibility(
            visible = showList && phase == GroupCallManager.Phase.ACTIVE,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200))
        ) {
            Box(
                Modifier.fillMaxSize().background(Color(0x99000000))
                    .pointerInput(Unit) { detectTapGestures { showList = false } }
            )
        }
        AnimatedVisibility(
            visible = showList && phase == GroupCallManager.Phase.ACTIVE,
            enter = slideInVertically(spring(0.82f, 300f)) { it } + fadeIn(tween(150)),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            MembersSheet(backdrop)
        }
    }
}

/** رأس زجاجي: صورة المجموعة + الاسم + المؤقت وعدد المشاركين */
@Composable
private fun GroupHeader(backdrop: Backdrop, status: String, total: Int, active: Boolean) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(0.7f, 260f)) }
    Row(
        Modifier.fillMaxWidth()
            .graphicsLayer {
                val e = enter.value
                alpha = e.coerceIn(0f, 1f)
                translationY = (e - 1f) * 40.dp.toPx()
            }
            .callGlass(backdrop, RoundedCornerShape(28.dp), Color.White.copy(alpha = 0.10f), blurDp = 10f)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GroupAvatar(
            GroupCallManager.groupAvatar, 42.dp,
            GroupCallManager.gid, GroupCallManager.groupAvatarGifV
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                GroupCallManager.groupName.ifBlank { "مكالمة جماعية" },
                color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (active) {
                    LiveDot(d = 7.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    if (active) "$status  •  $total مشارك" else status,
                    color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp
                )
            }
        }
    }
}

/**
 * ترتيب المشاركين:
 *  1 = بطاقة كبيرة وحدها (بانتظار الباقين) • 2 = بطاقتين فوق بعض •
 *  3 وأكثر = عمودين، والبطاقة الأخيرة تاخذ العرض كامل إذا العدد فردي.
 */
@Composable
private fun ParticipantGrid(
    backdrop: Backdrop,
    self: GroupCallManager.Member?,
    members: List<GroupCallManager.Member>,
    hostUid: String,
    myMuted: Boolean
) {
    val all = buildList {
        if (self != null) add(self)
        addAll(members)
    }
    val n = all.size
    if (n == 0) return
    val cols = if (n <= 2) 1 else 2
    val rows = (n + cols - 1) / cols
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (r in 0 until rows) {
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (c in 0 until cols) {
                    val idx = r * cols + c
                    if (idx < n) {
                        val m = all[idx]
                        key(m.uid) {
                            ParticipantTile(
                                backdrop = backdrop, m = m,
                                isSelf = m === self, isHost = hostUid == m.uid,
                                muted = m === self && myMuted,
                                compact = n > 4,
                                hint = if (n == 1) "بانتظار انضمام الأعضاء" else "",
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }
}

/** بطاقة مشارك: زجاج + صورة تنبض مع الصوت + اسم + حالة */
@Composable
private fun ParticipantTile(
    backdrop: Backdrop,
    m: GroupCallManager.Member,
    isSelf: Boolean,
    isHost: Boolean,
    muted: Boolean,
    compact: Boolean,
    hint: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(30.dp)
    val lvl by animateFloatAsState(m.level, tween(140), label = "tileLvl")
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(0.62f, 280f)) }
    val t = rememberInfiniteTransition(label = "tile")
    val blink by t.animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(750, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "blink"
    )

    BoxWithConstraints(
        modifier
            .graphicsLayer {
                val e = enter.value
                alpha = e.coerceIn(0f, 1f)
                val s = 0.78f + 0.22f * e
                scaleX = s; scaleY = s
            }
            // توهج خارجي لما يحچي
            .drawBehind {
                if (lvl > 0.02f) {
                    val cr = CornerRadius(30.dp.toPx())
                    for (i in 1..3) {
                        val pad = i * 5.dp.toPx()
                        drawRoundRect(
                            CallTeal.copy(alpha = 0.16f * lvl / i),
                            topLeft = Offset(-pad, -pad),
                            size = Size(this.size.width + pad * 2, this.size.height + pad * 2),
                            cornerRadius = CornerRadius(cr.x + pad),
                            style = Stroke(4.dp.toPx())
                        )
                    }
                }
            }
            .callGlass(backdrop, shape, Color.White.copy(alpha = 0.09f), blurDp = 12f)
            .border((2.5f * lvl).dp, CallTeal.copy(alpha = (0.95f * lvl).coerceIn(0f, 1f)), shape)
    ) {
        val av = (minOf(maxWidth, maxHeight) * 0.50f).coerceIn(52.dp, 150.dp)
        Column(
            Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            SpeakingRing(lvl, av) { AvatarBox(m.avatar, m.name.ifBlank { "؟" }, av) }
            Spacer(Modifier.height(if (compact) 2.dp else 6.dp))
            Text(
                if (isSelf) "أنت" else m.name.ifBlank { "مستخدم" },
                color = Color.White, fontSize = if (compact) 14.sp else 17.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
            )
            val sub = when {
                hint.isNotBlank() -> hint
                isHost -> "صاحب المكالمة"
                !isSelf && !m.connected -> "يتصل…"
                else -> ""
            }
            if (sub.isNotBlank()) {
                val waiting = hint.isNotBlank() || (!isSelf && !m.connected)
                Text(
                    sub,
                    color = (if (isHost && hint.isBlank()) Color(0xFFFFD66B) else Color.White).copy(alpha = if (waiting) 0.55f * blink + 0.2f else 0.7f),
                    fontSize = if (compact) 11.sp else 13.sp
                )
            }
        }
        // مؤشر المايك المكتوم
        if (muted) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(10.dp).size(30.dp)
                    .clip(CircleShape).background(CallRed.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) { Icon(CallIcons.MicOff, null, tint = Color.White, modifier = Modifier.size(17.dp)) }
        }
    }
}

/** شريط التحكم العائم: أعضاء • كتم • مغادرة • سماعة */
@Composable
private fun ControlBar(backdrop: Backdrop, total: Int, listOpen: Boolean, onMembers: () -> Unit) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(0.72f, 240f)) }
    Row(
        Modifier.fillMaxWidth()
            .graphicsLayer {
                val e = enter.value
                alpha = e.coerceIn(0f, 1f)
                translationY = (1f - e) * 90.dp.toPx()
            }
            .callGlass(backdrop, RoundedCornerShape(40.dp), Color.White.copy(alpha = 0.08f), blurDp = 14f)
            .padding(horizontal = 10.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        GlassCallButton(
            backdrop, CallIcons.People, "الأعضاء", onMembers,
            size = 60.dp, active = listOpen, fx = ButtonFx.POP, enterIndex = 1, badge = total
        )
        GlassCallButton(
            backdrop, if (GroupCallManager.muted) CallIcons.MicOff else CallIcons.Mic, "كتم",
            { GroupCallManager.toggleMute() },
            size = 60.dp, active = GroupCallManager.muted, fx = ButtonFx.POP, enterIndex = 2
        )
        GlassCallButton(
            backdrop, CallIcons.Phone, "مغادرة", { GroupCallManager.leave() },
            size = 70.dp, tint = CallRed, iconRotate = 135f, fx = ButtonFx.SWING, enterIndex = 3
        )
        GlassCallButton(
            backdrop, if (GroupCallManager.speaker) CallIcons.VolumeUp else CallIcons.VolumeDown, "سماعة",
            { GroupCallManager.toggleSpeaker() },
            size = 60.dp, active = GroupCallManager.speaker, fx = ButtonFx.POP, enterIndex = 4
        )
    }
}

/** حلقات صوت تنبض حول الصورة حسب مستوى الصوت */
@Composable
private fun SpeakingRing(level: Float, d: Dp, content: @Composable () -> Unit) {
    val lvl by animateFloatAsState(level, tween(140), label = "lvl")
    val t = rememberInfiniteTransition(label = "pulse")
    val pulse by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "p"
    )
    val extra = d * 0.3f
    Box(Modifier.size(d + extra * 2), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            if (lvl > 0.02f) {
                val base = d.toPx() / 2f
                val ext = extra.toPx()
                drawCircle(CallTeal.copy(alpha = 0.18f * lvl), radius = base + ext * (0.35f + 0.4f * lvl))
                drawCircle(
                    CallTeal.copy(alpha = 0.9f * lvl), radius = base + ext * 0.18f,
                    style = Stroke(width = 2.5.dp.toPx())
                )
                drawCircle(
                    CallTeal.copy(alpha = 0.55f * lvl * (1f - pulse)), radius = base + ext * (0.3f + 0.7f * pulse),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
        content()
    }
}

@Composable
private fun AvatarBox(avatar: android.graphics.Bitmap?, name: String, d: Dp) {
    Box(
        Modifier.size(d).clip(CircleShape).background(Color(0xFF2B3550))
            .border(
                1.5.dp,
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.1f))
                ),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (avatar != null) {
            Image(avatar.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(name.trim().take(1), color = Color.White, fontSize = (d.value * 0.4f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** قائمة الأعضاء المنضمين (زجاج). صاحب المكالمة يگدر يكتم أو يطرد أي واحد */
@Composable
private fun MembersSheet(backdrop: Backdrop) {
    val self = GroupCallManager.selfMember
    val iAmHost = GroupCallManager.iAmHost
    val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    Column(
        Modifier.fillMaxWidth().heightIn(max = 540.dp)
            .callGlass(backdrop, shape, Color.Black.copy(alpha = 0.32f), blurDp = 22f)
            .pointerInput(Unit) { detectTapGestures { } }
            .navigationBarsPadding()
            .padding(top = 10.dp, bottom = 12.dp)
    ) {
        Box(
            Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp)
                .clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.35f))
        )
        Text(
            "المنضمين (${GroupCallManager.members.size + 1})",
            color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
        )
        LazyColumn(Modifier.fillMaxWidth()) {
            if (self != null) {
                item(key = "me") {
                    MemberRow(backdrop, 0, self, "أنت", GroupCallManager.hostUid == self.uid, true, false)
                }
            }
            items(GroupCallManager.members, key = { it.uid }) { m ->
                val i = GroupCallManager.members.indexOf(m) + 1
                MemberRow(
                    backdrop, i, m, m.name.ifBlank { "مستخدم" },
                    GroupCallManager.hostUid == m.uid, m.connected, iAmHost
                )
            }
        }
    }
}

@Composable
private fun MemberRow(
    backdrop: Backdrop, index: Int,
    m: GroupCallManager.Member, label: String, isHost: Boolean, ready: Boolean, canManage: Boolean
) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index * 50L).coerceAtMost(400L))
        enter.animateTo(1f, spring(0.75f, 320f))
    }
    Row(
        Modifier.fillMaxWidth()
            .graphicsLayer {
                val e = enter.value
                alpha = e.coerceIn(0f, 1f)
                translationX = (1f - e) * 40.dp.toPx()
            }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpeakingRing(m.level, 44.dp) { AvatarBox(m.avatar, label, 44.dp) }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = when {
                isHost -> "صاحب المكالمة"
                !ready -> "يتصل…"
                else -> ""
            }
            if (sub.isNotBlank()) {
                Text(
                    sub, fontSize = 12.sp,
                    color = if (isHost) Color(0xFFFFD66B) else Color.White.copy(alpha = 0.6f)
                )
            }
        }
        if (canManage && !isHost) {
            GlassMiniButton(backdrop, CallIcons.MicOff, Color.White, { GroupCallManager.muteMember(m.uid) })
            Spacer(Modifier.width(8.dp))
            GlassMiniButton(backdrop, Icons.Filled.Close, Color(0xFFFF6B6B), { GroupCallManager.kickMember(m.uid) })
        }
    }
}
