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

/** تبويبات شاشة المتابعة */
internal enum class FollowTab { FOLLOWERS, FOLLOWING, FRIENDS }

/**
 * شاشة المتابعة: ثلاث قوائم
 * - المتابعين: يتابعوني وأنا ما أتابعهم (زر "متابعة" يرجّع المتابعة فيصيرون أصدقاء)
 * - أتابعه: أتابعهم وما يتابعوني (زر "إلغاء" يلغي متابعتي)
 * - الأصدقاء: متابعة متبادلة
 * ضغطة على أي حساب تفتح ملفه الشخصي. تنفتح بحركة انبعاث (OriginOverlay).
 */
@Composable
internal fun FriendsScreen(
    initialTab: FollowTab = FollowTab.FRIENDS,
    onDismiss: () -> Unit,
    onOpenProfile: (UserItem) -> Unit
) {
    val scope = rememberCoroutineScope()
    val myUid = remember { Repo.auth.currentUser?.uid ?: "" }
    val following by remember(myUid) { Repo.followingFlow(myUid) }.collectAsState(initial = emptyList())
    val followers by remember(myUid) { Repo.followersFlow(myUid) }.collectAsState(initial = emptyList())
    val fg = MaterialTheme.colorScheme.onBackground
    var tab by remember { mutableStateOf(initialTab) }
    var unfollowTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var busyUid by remember { mutableStateOf<String?>(null) }

    val followingSet = following.toSet()
    val followersSet = followers.toSet()
    val onlyFollowers = followers.filter { it !in followingSet }
    val onlyFollowing = following.filter { it !in followersSet }
    val friends = following.filter { it in followersSet }
    val list = when (tab) {
        FollowTab.FOLLOWERS -> onlyFollowers
        FollowTab.FOLLOWING -> onlyFollowing
        FollowTab.FRIENDS -> friends
    }

    fun doFollow(uid: String) {
        if (busyUid != null) return
        busyUid = uid
        scope.launch {
            try { Repo.follow(uid) } catch (e: Exception) { AppToast.show("فشلت المتابعة: ${e.message}", long = true) }
            busyUid = null
        }
    }
    fun doUnfollow(uid: String) {
        if (busyUid != null) return
        busyUid = uid
        scope.launch {
            try { Repo.unfollow(uid) } catch (e: Exception) { AppToast.show("فشل الإلغاء: ${e.message}", long = true) }
            busyUid = null
        }
    }

    BackHandler { if (unfollowTarget != null) unfollowTarget = null else onDismiss() }

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
                            when (tab) {
                                FollowTab.FOLLOWERS -> "المتابعين"
                                FollowTab.FOLLOWING -> "أتابعهُ"
                                FollowTab.FRIENDS -> "الأصدقاء"
                            },
                            fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f).padding(horizontal = 14.dp)
                        )
                        Text("${list.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                        Spacer(Modifier.width(6.dp))
                    }
                }

                // شريط التبويبات: المتابعين | أتابعهُ | الأصدقاء (بالعربي من اليمين لليسار)
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple(FollowTab.FRIENDS, "الأصدقاء", friends.size),
                        Triple(FollowTab.FOLLOWING, "أتابعهُ", onlyFollowing.size),
                        Triple(FollowTab.FOLLOWERS, "المتابعين", onlyFollowers.size)
                    ).forEach { (t, label, n) ->
                        val sel = tab == t
                        GlassButton(
                            onClick = { tab = t }, backdrop = bd, pressScale = 1.04f,
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

                if (list.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            when (tab) {
                                FollowTab.FOLLOWERS -> "ما عندك متابعين جدد.\nمن يتابعك وأنت ما تتابعه يطلع هنا."
                                FollowTab.FOLLOWING -> "ما تتابع أحد لحد الآن.\nافتح ملف أي شخص واضغط \"إضافة صديق\"."
                                FollowTab.FRIENDS -> "ما عندك أصدقاء بعد.\nالصديق هو من تتابعه ويتابعك."
                            },
                            textAlign = TextAlign.Center, color = fg.copy(0.7f), fontSize = 16.sp
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(list, key = { "${tab.name}_$it" }) { uid ->
                            FriendRow(
                                uid = uid, backdrop = bd, onClick = { onOpenProfile(it) },
                                trailing = { name ->
                                    when (tab) {
                                        FollowTab.FOLLOWERS -> GlassButton(
                                            onClick = { doFollow(uid) },
                                            backdrop = bd, tint = AccentBlue, pressScale = 1.06f,
                                            modifier = Modifier.height(40.dp)
                                        ) {
                                            Text("متابعة", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp))
                                        }
                                        FollowTab.FOLLOWING -> GlassButton(
                                            onClick = { doUnfollow(uid) },
                                            backdrop = bd, pressScale = 1.06f,
                                            modifier = Modifier.height(40.dp)
                                        ) {
                                            Text("إلغاء", color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp))
                                        }
                                        FollowTab.FRIENDS -> GlassButton(
                                            onClick = { unfollowTarget = uid to name },
                                            backdrop = bd, tint = Color(0xFFFF453A).copy(0.9f), pressScale = 1.06f,
                                            modifier = Modifier.height(40.dp)
                                        ) {
                                            Text("إلغاء", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp))
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            unfollowTarget?.let { (uid, name) ->
                GlassConfirmDialog(
                    backdrop = bd,
                    dark = true,
                    title = "إلغاء الصداقة",
                    message = "راح تلغي متابعتك لـ $name، فما تبقون أصدقاء (يبقى هو يتابعك). وما تشوف قصته الخاصة بالأصدقاء. المراسلة تبقى عادية.",
                    confirmText = "إلغاء الصداقة",
                    icon = Icons.Filled.Person,
                    onConfirm = { unfollowTarget = null; doUnfollow(uid) },
                    onDismiss = { unfollowTarget = null },
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                BadgeAfterName(prof, 18.dp)
            }
            if (prof.username.isNotBlank()) {
                Text("@${prof.username}", fontSize = 13.sp, color = fg.copy(0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        trailing(name)
    }
}
