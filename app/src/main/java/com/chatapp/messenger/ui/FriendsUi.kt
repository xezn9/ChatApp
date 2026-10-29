package com.chatapp.messenger.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.Profile
import com.chatapp.messenger.Repo
import com.chatapp.messenger.UserItem
import com.chatapp.messenger.ui.glass.GlassButton
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.launch

/**
 * شاشة الأصدقاء: طلبات الصداقة الواردة (قبول/رفض) + قائمة أصدقائي.
 * ضغطة على صديق تفتح ملفه الشخصي، وزر "إلغاء" يلغي الصداقة.
 * تنفتح من زر الأصدقاء بحركة انبعاث (OriginOverlay).
 */
@Composable
internal fun FriendsScreen(
    onDismiss: () -> Unit,
    onOpenProfile: (UserItem) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val myUid = Repo.auth.currentUser?.uid ?: ""
    val friends by remember(myUid) { Repo.friendsFlow(myUid) }.collectAsState(initial = emptyList())
    val requests by remember(myUid) { Repo.incomingRequestsFlow(myUid) }.collectAsState(initial = emptyList())
    val fg = MaterialTheme.colorScheme.onBackground
    var removeTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var busyUid by remember { mutableStateOf<String?>(null) }

    BackHandler { if (removeTarget != null) removeTarget = null else onDismiss() }

    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        LiquidScene(banner = null) { bd ->
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)
            ) {
                StaggerIn(0, Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        GlassButton(onClick = onDismiss, backdrop = bd, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.Close, null, modifier = Modifier.size(24.dp))
                        }
                        Text(
                            "الأصدقاء", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f).padding(horizontal = 14.dp)
                        )
                        Text("${friends.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                        Spacer(Modifier.width(6.dp))
                    }
                }

                if (friends.isEmpty() && requests.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "ما عندك أصدقاء بعد.\nافتح ملف أي شخص واضغط \"إضافة صديق\".",
                            textAlign = TextAlign.Center, color = fg.copy(0.7f), fontSize = 16.sp
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (requests.isNotEmpty()) {
                            item(key = "hdr_req") {
                                Text(
                                    "طلبات الصداقة (${requests.size})", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    color = fg.copy(0.7f), modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                            items(requests, key = { "req_$it" }) { uid ->
                                FriendRow(
                                    uid = uid, backdrop = bd, onClick = { onOpenProfile(it) },
                                    trailing = { _ ->
                                        GlassButton(
                                            onClick = {
                                                if (busyUid == null) {
                                                    busyUid = uid
                                                    scope.launch {
                                                        try { Repo.acceptFriendRequest(uid) }
                                                        catch (e: Exception) {
                                                            Toast.makeText(ctx, "فشل القبول: ${e.message}", Toast.LENGTH_LONG).show()
                                                        }
                                                        busyUid = null
                                                    }
                                                }
                                            },
                                            backdrop = bd, tint = AccentBlue, pressScale = 1.06f,
                                            modifier = Modifier.height(40.dp)
                                        ) { Text("قبول", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp)) }
                                        Spacer(Modifier.width(8.dp))
                                        GlassButton(
                                            onClick = {
                                                if (busyUid == null) {
                                                    busyUid = uid
                                                    scope.launch {
                                                        try { Repo.declineFriendRequest(uid) }
                                                        catch (e: Exception) {
                                                            Toast.makeText(ctx, "فشل الرفض: ${e.message}", Toast.LENGTH_LONG).show()
                                                        }
                                                        busyUid = null
                                                    }
                                                }
                                            },
                                            backdrop = bd, pressScale = 1.06f,
                                            modifier = Modifier.height(40.dp)
                                        ) { Text("رفض", color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp)) }
                                    }
                                )
                            }
                            item(key = "hdr_friends") {
                                Text(
                                    "أصدقائي (${friends.size})", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    color = fg.copy(0.7f), modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                        }
                        items(friends, key = { "fr_$it" }) { uid ->
                            FriendRow(
                                uid = uid, backdrop = bd, onClick = { onOpenProfile(it) },
                                trailing = { name ->
                                    GlassButton(
                                        onClick = { removeTarget = uid to name },
                                        backdrop = bd, tint = Color(0xFFFF453A).copy(0.9f), pressScale = 1.06f,
                                        modifier = Modifier.height(40.dp)
                                    ) {
                                        Text(
                                            "إلغاء", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }

            removeTarget?.let { (uid, name) ->
                GlassConfirmDialog(
                    backdrop = bd,
                    dark = true,
                    title = "إلغاء الصداقة",
                    message = "راح تلغي صداقتك مع $name، وما يگدر يشوف قصتك ولا تشوف قصته لين ترجعوا أصدقاء. المراسلة تبقى عادية.",
                    confirmText = "إلغاء الصداقة",
                    icon = Icons.Filled.Person,
                    onConfirm = {
                        removeTarget = null
                        scope.launch {
                            try { Repo.removeFriend(uid) }
                            catch (e: Exception) {
                                Toast.makeText(ctx, "فشل الإلغاء: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onDismiss = { removeTarget = null },
                    cancelText = "رجوع"
                )
            }
        }
    }
}

/** صف صديق/طلب: صورة + اسم + يوزر + أزرار على الجانب. الضغط على الصف يفتح الملف الشخصي */
@Composable
private fun FriendRow(
    uid: String,
    backdrop: Backdrop,
    onClick: (UserItem) -> Unit,
    trailing: @Composable androidx.compose.foundation.layout.RowScope.(name: String) -> Unit
) {
    val prof by remember(uid) { Repo.basicProfileFlow(uid) }.collectAsState(initial = Profile())
    val avatar = rememberB64Image(prof.avatar)
    val name = prof.name.ifBlank { "مستخدم" }
    val fg = MaterialTheme.colorScheme.onBackground
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier.fillMaxWidth()
            .liquidCard(backdrop, shape, dark = true)
            .clip(shape)
            .clickable { onClick(UserItem(uid, prof.name, "", prof.username)) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF2B2F36)),
            contentAlignment = Alignment.Center
        ) {
            if (avatar != null) Image(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Filled.Person, null, tint = Color(0xFF8A8F98), modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (prof.username.isNotBlank()) {
                Text("@${prof.username}", fontSize = 13.sp, color = fg.copy(0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        trailing(name)
    }
}
