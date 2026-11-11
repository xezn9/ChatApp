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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.chatapp.messenger.ui.theme.ChatTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
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
        volumeControlStream = android.media.AudioManager.STREAM_VOICE_CALL

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
    val ctx = LocalContext.current
    val phase = CallManager.phase
    var everBusy by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var dots by remember { mutableIntStateOf(0) }
    val backdrop = rememberLayerBackdrop()

    LaunchedEffect(phase) {
        if (phase != CallManager.Phase.IDLE) everBusy = true
        if (phase == CallManager.Phase.IDLE && everBusy) onClose()
        while (phase == CallManager.Phase.ACTIVE) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }

    val ringing = phase == CallManager.Phase.OUTGOING ||
        phase == CallManager.Phase.INCOMING ||
        phase == CallManager.Phase.CONNECTING
    val ended = phase == CallManager.Phase.ENDED
    // نقاط "يرن…" المتحركة
    LaunchedEffect(ringing) {
        while (ringing) {
            delay(420)
            dots = (dots + 1) % 4
        }
    }
    val redShift by animateFloatAsState(if (ended) 1f else 0f, tween(500), label = "red")

    fun statusOf(p: CallManager.Phase): String {
        // مسافة بعرض الرقم حتى النص ما يهتز مع تغير النقاط
        val dotStr = ".".repeat(dots) + "\u2007".repeat(3 - dots)
        return when (p) {
            CallManager.Phase.OUTGOING -> "يرن$dotStr"
            CallManager.Phase.INCOMING -> "مكالمة صوتية واردة"
            CallManager.Phase.CONNECTING -> "جاري الاتصال$dotStr"
            CallManager.Phase.ACTIVE -> formatCallTime(now - CallManager.activeSince)
            CallManager.Phase.ENDED -> CallManager.endReason.ifBlank { "انتهت المكالمة" }
            CallManager.Phase.IDLE -> ""
        }
    }
    val name = CallManager.peerName.ifBlank { "مستخدم" }

    Box(Modifier.fillMaxSize()) {
        // الخلفية المتحركة = مصدر الزجاج
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            CallAurora(glow = if (phase == CallManager.Phase.ACTIVE) 0.35f else 0f, red = redShift)
        }

        // كل العناصر الزجاجية خارج طبقة layerBackdrop (وإلا الزجاج يرسم نفسه = كراش)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            GlassPill(backdrop) {
                Icon(CallIcons.Phone, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(15.dp))
                Text("مكالمة صوتية", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                if (phase == CallManager.Phase.ACTIVE) LiveDot()
            }

            Spacer(Modifier.weight(0.8f))

            CallAvatar(CallManager.peerAvatar, name, 140.dp, ringing = ringing, ended = ended)
            Spacer(Modifier.height(4.dp))
            Text(
                name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            AnimatedContent(
                targetState = phase,
                transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
                label = "status"
            ) { p ->
                Text(
                    statusOf(p),
                    color = Color.White.copy(alpha = if (p == CallManager.Phase.ENDED) 0.9f else 0.7f),
                    fontSize = 17.sp
                )
            }

            Spacer(Modifier.weight(1f))

            // أزرار التحكم: تتبدل حسب الحالة، وكل زر يدخل بحركة نابضية متدرّجة
            val group = when (phase) {
                CallManager.Phase.INCOMING -> 0
                CallManager.Phase.ENDED, CallManager.Phase.IDLE -> 2
                else -> 1
            }
            AnimatedContent(
                targetState = group,
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                label = "controls"
            ) { g ->
                when (g) {
                    0 -> Row(
                        Modifier.fillMaxWidth().padding(bottom = 48.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        GlassCallButton(
                            backdrop, CallIcons.Phone, "رفض", { CallManager.decline() },
                            size = 78.dp, tint = CallRed, iconRotate = 135f, fx = ButtonFx.SWING, enterIndex = 0
                        )
                        GlassCallButton(
                            backdrop, CallIcons.Phone, "رد", { (ctx as? CallActivity)?.answerFromUi() },
                            size = 78.dp, tint = CallGreen, fx = ButtonFx.WIGGLE, enterIndex = 1
                        )
                    }
                    1 -> Row(
                        Modifier.fillMaxWidth().padding(bottom = 48.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        GlassCallButton(
                            backdrop, if (CallManager.muted) CallIcons.MicOff else CallIcons.Mic, "كتم",
                            { CallManager.toggleMute() },
                            active = CallManager.muted, fx = ButtonFx.POP, enterIndex = 0
                        )
                        GlassCallButton(
                            backdrop, CallIcons.Phone, "إنهاء", { CallManager.hangup() },
                            size = 76.dp, tint = CallRed, iconRotate = 135f, fx = ButtonFx.SWING, enterIndex = 1
                        )
                        GlassCallButton(
                            backdrop, if (CallManager.speaker) CallIcons.VolumeUp else CallIcons.VolumeDown, "سماعة",
                            { CallManager.toggleSpeaker() },
                            active = CallManager.speaker, fx = ButtonFx.POP, enterIndex = 2
                        )
                    }
                    else -> Spacer(Modifier.height(48.dp))
                }
            }
        }
    }
}
