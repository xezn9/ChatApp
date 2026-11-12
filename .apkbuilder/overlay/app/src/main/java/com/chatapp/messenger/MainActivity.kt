package com.chatapp.messenger

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.chatapp.messenger.ui.App
import com.chatapp.messenger.ui.Nav
import com.chatapp.messenger.ui.SplashScreen
import com.chatapp.messenger.ui.theme.ChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // ثيم الإقلاع (خلفية داكنة تطابق السبلاش) يرجع للثيم العادي قبل بناء الواجهة
        setTheme(R.style.Theme_Chat)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            ChatTheme {
                // السبلاش يظهر مرة وحدة عند فتح التطبيق (rememberSaveable عشان تدوير الشاشة ما يعيده)
                var showSplash by rememberSaveable { mutableStateOf(!com.chatapp.messenger.ui.theme.ThemeSettings.skipSplash) }
                Box(Modifier.fillMaxSize()) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        App()
                    }
                    if (showSplash) {
                        SplashScreen(onFinished = { showSplash = false })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        AppState.foreground = true
        ConnectionKeeper.onForeground()
    }

    override fun onStop() {
        super.onStop()
        AppState.foreground = false
        ConnectionKeeper.onBackground()
    }

    /** ضغطة على إشعار رسالة = افتح محادثة المرسل */
    private fun handleIntent(i: Intent?) {
        // رابط حساب من باركود: chatapp://user/<uid> أو https://.../u/<uid>
        i?.data?.let { d ->
            val seg = d.pathSegments
            val uid = when {
                d.scheme == "chatapp" && d.host == "user" -> seg.firstOrNull()
                seg.size >= 2 && seg[0] == "u" -> seg[1]
                else -> null
            }
            if (uid != null && Regex("^[A-Za-z0-9_-]{1,128}$").matches(uid)) {
                Nav.openProfileUid = uid
                i.data = null
            }
        }
        i?.getStringExtra(ChatMessagingService.EXTRA_OPEN_CHAT)?.let {
            Nav.openChatUid = it
            i.removeExtra(ChatMessagingService.EXTRA_OPEN_CHAT)
        }
    }
}
