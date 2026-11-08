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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.chatapp.messenger.ui.GroupAvatar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.chatapp.messenger.ui.theme.ChatTheme
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

    val status = when (phase) {
        GroupCallManager.Phase.ACTIVE -> {
            val s = ((now - GroupCallManager.activeSince) / 1000).coerceAtLeast(0)
            "%02d:%02d".format(s / 60, s % 60)
        }
        GroupCallManager.Phase.ENDED -> GroupCallManager.endReason.ifBlank { "انتهت المكالمة" }
        GroupCallManager.Phase.IDLE -> ""
    }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1B1035), Color(0xFF0A1A2F), Color(0xFF05080F))))
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0xFF7B4DFF).copy(alpha = 0.28f + 0.22f * glow), Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 0.12f), radius = size.width * 0.95f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0xFF19D3C5).copy(alpha = 0.22f + 0.22f * glow), Color.Transparent),
                        center = Offset(size.width * 0.9f, size.height * 0.85f), radius = size.width * 1.0f
                    )
                )
            }
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))
            Text(
                GroupCallManager.groupName.ifBlank { "مكالمة جماعية" },
                color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (phase == GroupCallManager.Phase.ACTIVE) "$status  •  $total مشارك" else status,
                color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp
            )

            Spacer(Modifier.weight(1f))

            // صورة المجموعة (تنبض لما أي واحد يحچي)
            SpeakingRing(anyLevel, 170.dp) {
                GroupAvatar(
                    GroupCallManager.groupAvatar, 170.dp,
                    GroupCallManager.gid, GroupCallManager.groupAvatarGifV
                )
            }

            Spacer(Modifier.height(28.dp))

            // صاحب الاتصال
            val host = GroupCallManager.host
            if (host != null) {
                val isMe = host === self
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpeakingRing(host.level, 52.dp) { AvatarBox(host.avatar, host.name.ifBlank { "؟" }, 52.dp) }
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(
                            if (isMe) "أنت" else host.name.ifBlank { "مستخدم" },
                            color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text("صاحب المكالمة", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            if (phase == GroupCallManager.Phase.ACTIVE) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 40.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    ToggleButton(CallIcons.People, "الأعضاء ($total)", showList) { showList = true }
                    ToggleButton(
                        if (GroupCallManager.muted) CallIcons.MicOff else CallIcons.Mic,
                        "كتم", GroupCallManager.muted
                    ) { GroupCallManager.toggleMute() }
                    RoundButton(Color(0xFFE5484D), "مغادرة", rotate = 135f) { GroupCallManager.leave() }
                    ToggleButton(
                        if (GroupCallManager.speaker) CallIcons.VolumeUp else CallIcons.VolumeDown,
                        "سماعة", GroupCallManager.speaker
                    ) { GroupCallManager.toggleSpeaker() }
                }
            } else {
                Spacer(Modifier.height(48.dp))
            }
        }

        if (showList && phase == GroupCallManager.Phase.ACTIVE) {
            MembersSheet(onClose = { showList = false })
        }
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
    val ring = Color(0xFF34D5C4)
    Box(Modifier.size(d + extra * 2), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            if (lvl > 0.02f) {
                val base = d.toPx() / 2f
                val ext = extra.toPx()
                drawCircle(ring.copy(alpha = 0.18f * lvl), radius = base + ext * (0.35f + 0.4f * lvl))
                drawCircle(
                    ring.copy(alpha = 0.9f * lvl), radius = base + ext * 0.18f,
                    style = Stroke(width = 2.5.dp.toPx())
                )
                drawCircle(
                    ring.copy(alpha = 0.55f * lvl * (1f - pulse)), radius = base + ext * (0.3f + 0.7f * pulse),
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
        Modifier.size(d).clip(CircleShape).background(Color(0xFF2B3550)),
        contentAlignment = Alignment.Center
    ) {
        if (avatar != null) {
            Image(avatar.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(name.trim().take(1), color = Color.White, fontSize = (d.value * 0.4f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** قائمة الأعضاء المنضمين. صاحب المكالمة يگدر يكتم أو يطرد أي واحد */
@Composable
private fun MembersSheet(onClose: () -> Unit) {
    val self = GroupCallManager.selfMember
    val iAmHost = GroupCallManager.iAmHost
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000))
            .pointerInput(Unit) { detectTapGestures { onClose() } }
    ) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 520.dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Color(0xFF141B2D))
                .pointerInput(Unit) { detectTapGestures { } }
                .navigationBarsPadding()
                .padding(top = 16.dp, bottom = 12.dp)
        ) {
            Text(
                "المنضمين (${GroupCallManager.members.size + 1})",
                color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
            LazyColumn(Modifier.fillMaxWidth()) {
                if (self != null) {
                    item(key = "me") {
                        MemberRow(self, "أنت", GroupCallManager.hostUid == self.uid, true, false)
                    }
                }
                items(GroupCallManager.members, key = { it.uid }) { m ->
                    MemberRow(m, m.name.ifBlank { "مستخدم" }, GroupCallManager.hostUid == m.uid, m.connected, iAmHost)
                }
            }
        }
    }
}

@Composable
private fun MemberRow(m: GroupCallManager.Member, label: String, isHost: Boolean, ready: Boolean, canManage: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
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
            if (sub.isNotBlank()) Text(sub, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        if (canManage && !isHost) {
            SmallAction(CallIcons.MicOff, Color.White) { GroupCallManager.muteMember(m.uid) }
            Spacer(Modifier.width(8.dp))
            SmallAction(Icons.Filled.Close, Color(0xFFFF6B6B)) { GroupCallManager.kickMember(m.uid) }
        }
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}
