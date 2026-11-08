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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
    val phase = GroupCallManager.phase
    var everBusy by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(phase) {
        if (phase == GroupCallManager.Phase.ACTIVE) everBusy = true
        if (phase == GroupCallManager.Phase.IDLE && everBusy) onClose()
        while (phase == GroupCallManager.Phase.ACTIVE) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }

    val status = when (phase) {
        GroupCallManager.Phase.ACTIVE -> {
            val s = ((now - GroupCallManager.activeSince) / 1000).coerceAtLeast(0)
            val n = GroupCallManager.members.size + 1
            "%02d:%02d  •  %d مشارك".format(s / 60, s % 60, n)
        }
        GroupCallManager.Phase.ENDED -> GroupCallManager.endReason.ifBlank { "انتهت المكالمة" }
        GroupCallManager.Phase.IDLE -> ""
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF16213E), Color(0xFF0B0F1A)))
        )
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
            Text(status, color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
            Spacer(Modifier.height(24.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item(key = "me") {
                    Participant(
                        name = "أنت", avatar = null,
                        subtitle = if (GroupCallManager.muted) "مكتوم" else "", ready = true
                    )
                }
                items(GroupCallManager.members, key = { it.uid }) { m ->
                    Participant(
                        name = m.name.ifBlank { "مستخدم" }, avatar = m.avatar,
                        subtitle = if (m.connected) "" else "يتصل…", ready = m.connected
                    )
                }
            }

            if (phase == GroupCallManager.Phase.ACTIVE) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 40.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
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
    }
}

@Composable
private fun Participant(name: String, avatar: android.graphics.Bitmap?, subtitle: String, ready: Boolean) {
    Column(
        Modifier.fillMaxWidth().alpha(if (ready) 1f else 0.55f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(76.dp).clip(CircleShape).background(Color(0xFF2B3550)),
            contentAlignment = Alignment.Center
        ) {
            if (avatar != null) {
                Image(avatar.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(name.trim().take(1), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            name, color = Color.White, fontSize = 13.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
        if (subtitle.isNotBlank()) {
            Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
        }
    }
}
