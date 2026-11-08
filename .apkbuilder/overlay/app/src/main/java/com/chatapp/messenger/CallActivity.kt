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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.chatapp.messenger.ui.theme.ChatTheme
import kotlinx.coroutines.delay

/** شاشة المكالمة (صادرة / واردة / جارية). تظهر فوق شاشة القفل. */
class CallActivity : ComponentActivity() {

    private var afterPermission: (() -> Unit)? = null

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val next = afterPermission
        afterPermission = null
        if (ok) {
            next?.invoke()
        } else {
            Toast.makeText(this, "لازم تسمح باستخدام المايك حتى تشتغل المكالمة", Toast.LENGTH_LONG).show()
            if (CallManager.phase == CallManager.Phase.INCOMING) CallManager.decline()
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
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (!handle(intent)) { finish(); return }

        setContent {
            ChatTheme {
                CallScreen(onClose = { finish() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!handle(intent)) finish()
    }

    /** يرجع false إذا ما فيه شي نعرضه */
    private fun handle(i: Intent?): Boolean {
        val peer = i?.getStringExtra(EXTRA_PEER)
        if (peer != null && !CallManager.isBusy) {
            val name = i.getStringExtra(EXTRA_NAME).orEmpty()
            i.removeExtra(EXTRA_PEER)
            withMic { CallManager.startCall(this, peer, name) }
            return true
        }
        if (i?.getBooleanExtra(EXTRA_ANSWER, false) == true && CallManager.phase == CallManager.Phase.INCOMING) {
            i.removeExtra(EXTRA_ANSWER)
            withMic { CallManager.accept(this) }
            return true
        }
        return CallManager.isBusy || CallManager.phase == CallManager.Phase.ENDED
    }

    private fun withMic(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            afterPermission = action
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    /** زر القبول بالشاشة */
    fun answerFromUi() = withMic { CallManager.accept(this) }

    companion object {
        const val EXTRA_ANSWER = "answer"
        const val EXTRA_PEER = "peer"
        const val EXTRA_NAME = "name"

        /** يبدأ مكالمة صادرة (من شاشة المحادثة) */
        fun dial(ctx: Context, peerUid: String, name: String) {
            ctx.startActivity(
                Intent(ctx, CallActivity::class.java)
                    .putExtra(EXTRA_PEER, peerUid)
                    .putExtra(EXTRA_NAME, name)
            )
        }
    }
}

@Composable
private fun CallScreen(onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val phase = CallManager.phase
    var everBusy by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(phase) {
        if (phase != CallManager.Phase.IDLE) everBusy = true
        if (phase == CallManager.Phase.IDLE && everBusy) onClose()
        while (phase == CallManager.Phase.ACTIVE) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }

    val status = when (phase) {
        CallManager.Phase.OUTGOING -> "يرن…"
        CallManager.Phase.INCOMING -> "مكالمة صوتية واردة"
        CallManager.Phase.CONNECTING -> "جاري الاتصال…"
        CallManager.Phase.ACTIVE -> {
            val s = ((now - CallManager.activeSince) / 1000).coerceAtLeast(0)
            "%02d:%02d".format(s / 60, s % 60)
        }
        CallManager.Phase.ENDED -> CallManager.endReason.ifBlank { "انتهت المكالمة" }
        CallManager.Phase.IDLE -> ""
    }
    val name = CallManager.peerName.ifBlank { "مستخدم" }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF16213E), Color(0xFF0B0F1A)))
        )
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))
            Box(
                Modifier.size(132.dp).clip(CircleShape).background(Color(0xFF2B3550)),
                contentAlignment = Alignment.Center
            ) {
                Text(name.trim().take(1), color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, maxLines = 2
            )
            Spacer(Modifier.height(8.dp))
            Text(status, color = Color.White.copy(alpha = 0.7f), fontSize = 17.sp)

            Spacer(Modifier.weight(1f))

            when (phase) {
                CallManager.Phase.INCOMING -> {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 48.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        RoundButton(Color(0xFFE5484D), "رفض", rotate = 135f) { CallManager.decline() }
                        RoundButton(Color(0xFF34C759), "رد", rotate = 0f) { (ctx as? CallActivity)?.answerFromUi() }
                    }
                }
                CallManager.Phase.ENDED, CallManager.Phase.IDLE -> Spacer(Modifier.height(48.dp))
                else -> {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 48.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        ToggleButton(if (CallManager.muted) "🔇" else "🎤", "كتم", CallManager.muted) { CallManager.toggleMute() }
                        RoundButton(Color(0xFFE5484D), "إنهاء", rotate = 135f) { CallManager.hangup() }
                        ToggleButton(if (CallManager.speaker) "🔊" else "🔈", "سماعة", CallManager.speaker) { CallManager.toggleSpeaker() }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundButton(color: Color, label: String, rotate: Float, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(color).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(CallIcons.Phone, null, tint = Color.White, modifier = Modifier.size(32.dp).rotate(rotate))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
    }
}

@Composable
private fun ToggleButton(emoji: String, label: String, on: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp).clip(CircleShape)
                .background(if (on) Color.White else Color.White.copy(alpha = 0.16f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 26.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
    }
}
