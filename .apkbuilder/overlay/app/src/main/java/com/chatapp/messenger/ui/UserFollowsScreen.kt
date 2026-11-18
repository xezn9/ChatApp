package com.chatapp.messenger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.Repo
import com.chatapp.messenger.UserItem
import com.chatapp.messenger.ui.glass.GlassButton

/**
 * قائمة متابعين / أتابعهُ لشخص ثاني (قراءة فقط).
 * تنفتح من ملفه الشخصي فقط إذا حسابه عادي أو خاص وهو يتابعني.
 * إذا القواعد رفضت القراءة (مثلاً تغيّرت الخصوصية وأنا فاتح الشاشة) يطلع "الحساب خاص".
 */
@Composable
internal fun UserFollowsScreen(
    ownerUid: String,
    ownerName: String,
    initialFollowers: Boolean,
    onDismiss: () -> Unit,
    onOpenProfile: (UserItem) -> Unit
) {
    var showFollowers by remember { mutableStateOf(initialFollowers) }
    val followers by remember(ownerUid) { Repo.userFollowsFlow(ownerUid, followers = true) }.collectAsState(initial = emptyList())
    val following by remember(ownerUid) { Repo.userFollowsFlow(ownerUid, followers = false) }.collectAsState(initial = emptyList())
    val fg = MaterialTheme.colorScheme.onBackground
    val list = if (showFollowers) followers else following
    // null = القواعد رفضت القراءة (الحساب خاص)
    val denied = list == null

    BackHandler(onBack = onDismiss)

    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        LiquidScene(banner = null) { bd ->
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassButton(onClick = onDismiss, backdrop = bd, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, null, modifier = Modifier.size(24.dp))
                    }
                    Text(
                        ownerName,
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                        modifier = Modifier.weight(1f).padding(horizontal = 14.dp)
                    )
                    Text("${list?.size ?: 0}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                    Spacer(Modifier.width(6.dp))
                }

                // تبويبان: المتابعين | يتابعهُ
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple(false, "يتابعهُ", following?.size ?: 0),
                        Triple(true, "المتابعين", followers?.size ?: 0)
                    ).forEach { (isFollowers, label, n) ->
                        val sel = showFollowers == isFollowers
                        GlassButton(
                            onClick = { showFollowers = isFollowers }, backdrop = bd, pressScale = 1.04f,
                            tint = if (sel) AccentBlue else Color.Unspecified,
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Spacer(Modifier.weight(1f))
                            Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "  $n", color = if (sel) Color.White else Color(0xFF7FD4FF),
                                fontSize = 14.sp, fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }

                if (denied || list.isNullOrEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            when {
                                denied -> "الحساب خاص"
                                showFollowers -> "ما عنده متابعين لحد الآن."
                                else -> "ما يتابع أحد لحد الآن."
                            },
                            textAlign = TextAlign.Center, color = fg.copy(0.7f), fontSize = 16.sp
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(list.orEmpty(), key = { "${showFollowers}_$it" }) { uid ->
                            FriendRow(uid = uid, backdrop = bd, onClick = { onOpenProfile(it) }, trailing = { })
                        }
                    }
                }
            }
        }
    }
}
