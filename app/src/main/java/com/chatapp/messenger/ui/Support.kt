package com.chatapp.messenger.ui

import com.chatapp.messenger.ui.theme.AppFont

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.R
import com.chatapp.messenger.Profile
import com.chatapp.messenger.Repo
import com.chatapp.messenger.ui.theme.appBackground
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ============================================================================
//  الدعم الفني: المستخدم يراسل "التطبيق" والرسالة توصل لمطور التطبيق (xezn99) فقط.
//  رد المطور يوصل للمستخدم بمحادثة باسم وصورة التطبيق (مو باسم وصورة حساب المطور).
//  التخزين: support/{uid} (ملخص المحادثة) + support/{uid}/messages/{id}
// ============================================================================

const val SUPPORT_APP_NAME = "ChatApp"
const val SUPPORT_DEV_USERNAME = "xezn99"

data class SupportThread(
    val uid: String,
    val name: String,
    val username: String,
    val lastText: String,
    val lastAt: Long,
    val lastFrom: String,
    val userReadAt: Long,
    val devReadAt: Long,
    /** وقت حذف المحادثة من عندي (المحادثة تنخفي والرسائل الأقدم ما تظهر) */
    val userClearedAt: Long = 0L,
    val devClearedAt: Long = 0L
) {
    val hiddenForUser get() = lastAt <= userClearedAt
    val hiddenForDev get() = lastAt <= devClearedAt
    val unreadForUser get() = lastFrom == "dev" && lastAt > userReadAt
    val unreadForDev get() = lastFrom == "user" && lastAt > devReadAt
}

data class SupportMsg(val id: String, val from: String, val text: String, val createdAt: Long)

object Support {
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private fun DocumentSnapshot.toThread() = SupportThread(
        uid = id,
        name = getString("name").orEmpty(),
        username = getString("username").orEmpty(),
        lastText = getString("lastText").orEmpty(),
        lastAt = getLong("lastAt") ?: 0L,
        lastFrom = getString("lastFrom").orEmpty(),
        userReadAt = getLong("userReadAt") ?: 0L,
        devReadAt = getLong("devReadAt") ?: 0L,
        userClearedAt = getLong("userClearedAt") ?: 0L,
        devClearedAt = getLong("devClearedAt") ?: 0L
    )

    /** هل الحساب الحالي هو حساب المطور؟ (صاحب اليوزر المحجوز xezn99) */
    suspend fun isDev(): Boolean = try {
        val me = Repo.auth.currentUser?.uid ?: ""
        me.isNotBlank() && db.collection("usernames").document(SUPPORT_DEV_USERNAME).get().await().getString("uid") == me
    } catch (_: Exception) {
        false
    }

    fun threadFlow(uid: String): Flow<SupportThread?> = callbackFlow {
        if (uid.isBlank()) { trySend(null); close(); return@callbackFlow }
        val reg = db.collection("support").document(uid).addSnapshotListener { d, err ->
            if (err != null || d == null || !d.exists()) trySend(null) else trySend(d.toThread())
        }
        awaitClose { reg.remove() }
    }

    /** للمطور فقط: كل محادثات الدعم، الأحدث أولاً */
    fun allThreadsFlow(): Flow<List<SupportThread>> = callbackFlow {
        val reg = db.collection("support").addSnapshotListener { s, err ->
            if (err != null || s == null) trySend(emptyList())
            else trySend(s.documents.map { it.toThread() }.filter { it.lastAt > 0 }.sortedByDescending { it.lastAt })
        }
        awaitClose { reg.remove() }
    }

    fun messagesFlow(uid: String): Flow<List<SupportMsg>> = callbackFlow {
        if (uid.isBlank()) { trySend(emptyList()); close(); return@callbackFlow }
        val reg = db.collection("support").document(uid).collection("messages")
            .addSnapshotListener { s, err ->
                if (err != null || s == null) trySend(emptyList())
                else trySend(
                    s.documents.map {
                        SupportMsg(it.id, it.getString("from").orEmpty(), it.getString("text").orEmpty(), it.getLong("createdAt") ?: 0L)
                    }.sortedBy { it.createdAt }
                )
            }
        awaitClose { reg.remove() }
    }

    suspend fun send(threadUid: String, text: String, asDev: Boolean, name: String = "", username: String = "") {
        val now = System.currentTimeMillis()
        val ref = db.collection("support").document(threadUid)
        val t = mutableMapOf<String, Any>(
            "lastText" to text, "lastAt" to now, "lastFrom" to if (asDev) "dev" else "user"
        )
        if (asDev) {
            t["devReadAt"] = now
        } else {
            t["uid"] = threadUid; t["name"] = name; t["username"] = username; t["userReadAt"] = now
        }
        val batch = db.batch()
        batch.set(
            ref.collection("messages").document(),
            mapOf("from" to if (asDev) "dev" else "user", "text" to text, "createdAt" to now)
        )
        batch.set(ref, t, SetOptions.merge())
        batch.commit().await()
    }

    /** حذف المحادثة من عندي فقط */
    suspend fun clear(threadUid: String, asDev: Boolean) {
        try {
            db.collection("support").document(threadUid)
                .set(mapOf((if (asDev) "devClearedAt" else "userClearedAt") to System.currentTimeMillis()), SetOptions.merge())
                .await()
        } catch (_: Exception) {
        }
    }

    suspend fun markRead(threadUid: String, asDev: Boolean) {
        try {
            db.collection("support").document(threadUid)
                .set(mapOf((if (asDev) "devReadAt" else "userReadAt") to System.currentTimeMillis()), SetOptions.merge())
                .await()
        } catch (_: Exception) {
        }
    }
}

// ---------------------------------------------------------------------------
//  صورة التطبيق (نفس أيقونة التطبيق) بشكل دائرة
// ---------------------------------------------------------------------------
@Composable
internal fun AppLogo(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val fg = ImageBitmap_fg()
    Box(
        modifier.size(size).clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF01144E), Color(0xFF000E3E)))),
        contentAlignment = Alignment.Center
    ) {
        // الأيقونة التكيفية تحتاج تكبير حتى تملا الدائرة
        Image(
            fg, null,
            Modifier.size(size * 1.5f),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun ImageBitmap_fg() = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.mipmap.ic_launcher_foreground)

// ---------------------------------------------------------------------------
//  صفوف قائمة المحادثات
// ---------------------------------------------------------------------------

/** صف "ChatApp" بقائمة محادثات المستخدم (يظهر بعد ما يراسل الدعم أو يوصله رد) */
@Composable
internal fun SupportChatRow(thread: SupportThread, onLongClick: () -> Unit = {}, onClick: () -> Unit) {
    SupportRowBase(
        title = SUPPORT_APP_NAME, subtitle = thread.lastText.ifBlank { "الدعم الفني" },
        unread = thread.unreadForUser, onClick = onClick, onLongClick = onLongClick, avatar = { AppLogo(52.dp) }
    )
}

/** صف للمطور: صندوق الدعم (محادثة لكل مستخدم راسل) */
@Composable
internal fun SupportInboxRow(thread: SupportThread, onLongClick: () -> Unit = {}, onClick: () -> Unit) {
    val prof by remember(thread.uid) { Repo.basicProfileFlow(thread.uid) }.collectAsState(initial = Profile())
    val avatar = rememberB64Image(prof.avatar)
    val shown = prof.name.ifBlank { thread.name }.ifBlank { thread.username }.ifBlank { "مستخدم" }
    SupportRowBase(
        title = "دعم · $shown", subtitle = thread.lastText,
        unread = thread.unreadForDev, onClick = onClick, onLongClick = onLongClick,
        avatar = {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF2B2F36)), contentAlignment = Alignment.Center) {
                if (avatar != null) Image(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Filled.Person, null, tint = Color(0xFF8A8F98), modifier = Modifier.size(28.dp))
            }
        }
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SupportRowBase(title: String, subtitle: String, unread: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, avatar: @Composable () -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth().combinedClickable(
            onClick = onClick,
            onLongClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onLongClick() }
        ).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        avatar()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (unread) 0.9f else 0.6f)
            )
        }
        if (unread) {
            Box(Modifier.padding(start = 8.dp).size(14.dp).clip(CircleShape).background(Color(0xFFFF3B5C)))
        }
    }
}

// ---------------------------------------------------------------------------
//  شاشة محادثة الدعم (للمستخدم: مع "ChatApp"، وللمطور: مع المستخدم الي راسل)
// ---------------------------------------------------------------------------
@Composable
fun SupportChatScreen(threadUid: String, asDev: Boolean, onBack: () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val scope = rememberCoroutineScope()
        val fg = MaterialTheme.colorScheme.onBackground
        val me = Repo.auth.currentUser?.uid ?: ""
        val allMsgs by remember(threadUid) { Support.messagesFlow(threadUid) }.collectAsState(initial = emptyList())
        val thread by remember(threadUid) { Support.threadFlow(threadUid) }.collectAsState(initial = null)
        // الحذف يستعمل نفس نظام "حذف المحادثة" الموجود (settings/<uid>.cleared) فيشتغل لأي مستخدم بدون قواعد جديدة
        val clearedMap by remember(me) { Repo.clearedFlow(me) }.collectAsState(initial = emptyMap<String, Long>())
        val clearedAt = clearedMap["support:$threadUid"] ?: 0L
        val msgs = remember(allMsgs, clearedAt) { allMsgs.filter { it.createdAt > clearedAt } }
        val myProf by remember(me) { Repo.basicProfileFlow(me) }.collectAsState(initial = Profile())
        val otherProf by remember(threadUid) { Repo.basicProfileFlow(if (asDev) threadUid else "") }.collectAsState(initial = Profile())
        val otherAvatar = rememberB64Image(otherProf.avatar)
        var text by remember { mutableStateOf("") }
        var sending by remember { mutableStateOf(false) }
        BackHandler { onBack() }

        // أي رسالة وصلت وأنا فاتح المحادثة = مقروءة
        LaunchedEffect(thread?.lastAt, thread?.lastFrom) {
            val t = thread ?: return@LaunchedEffect
            if ((asDev && t.unreadForDev) || (!asDev && t.unreadForUser)) Support.markRead(threadUid, asDev)
        }

        val title = if (asDev) otherProf.name.ifBlank { thread?.name.orEmpty() }.ifBlank { "مستخدم" } else SUPPORT_APP_NAME
        val sub = if (asDev) "@" + otherProf.username.ifBlank { thread?.username.orEmpty() } else "الدعم الفني"

        Column(
            Modifier.fillMaxSize().background(appBackground()).systemBarsPadding().imePadding()
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            // الهيدر
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { onBack() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = fg, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(6.dp))
                if (asDev) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF2B2F36)), contentAlignment = Alignment.Center) {
                        if (otherAvatar != null) Image(otherAvatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Icon(Icons.Filled.Person, null, tint = Color(0xFF8A8F98), modifier = Modifier.size(24.dp))
                    }
                } else AppLogo(42.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(sub, fontSize = 12.sp, color = fg.copy(0.6f), maxLines = 1)
                }
            }

            // الرسائل
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (msgs.isEmpty()) {
                    Column(
                        Modifier.align(Alignment.Center).padding(horizontal = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AppLogo(84.dp)
                        Spacer(Modifier.height(14.dp))
                        Text("الدعم والشكاوي", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "اكتب مشكلتك أو اقتراحك وراح توصل لمطور التطبيق مباشرة، والرد يجيك هنا.",
                            fontSize = 14.sp, color = fg.copy(0.65f), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                LazyColumn(
                    Modifier.fillMaxSize(), reverseLayout = true,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(msgs.reversed(), key = { it.id }) { m ->
                        val mine = if (asDev) m.from == "dev" else m.from == "user"
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.Start else Arrangement.End, verticalAlignment = Alignment.Bottom) {
                            // رد المطور للمستخدم يطلع بصورة التطبيق
                            if (!mine && !asDev) { AppLogo(28.dp); Spacer(Modifier.width(6.dp)) }
                            Box(
                                Modifier.widthIn(max = 290.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (mine) Brush.linearGradient(listOf(AccentBlue, SetViolet))
                                        else SolidColor(fg.copy(alpha = 0.12f))
                                    )
                                    .border(0.8.dp, Color.White.copy(0.18f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 14.dp, vertical = 9.dp)
                            ) {
                                Text(m.text, fontSize = 15.5.sp, color = if (mine) Color.White else fg)
                            }
                        }
                    }
                }
            }

            // خانة الكتابة
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Bottom) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(24.dp))
                        .background(fg.copy(alpha = 0.10f))
                        .border(0.8.dp, Color.White.copy(0.2f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    if (text.isEmpty()) Text(if (asDev) "اكتب ردك..." else "اكتب رسالتك...", color = fg.copy(0.45f), fontSize = 15.sp)
                    BasicTextField(
                        value = text, onValueChange = { if (it.length <= 2000) text = it },
                        textStyle = TextStyle(fontFamily = AppFont, color = fg, fontSize = 15.sp),
                        cursorBrush = SolidColor(AccentBlue), maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(AccentBlue, SetViolet)))
                        .clickable(enabled = text.isNotBlank() && !sending) {
                            val body = text.trim()
                            text = ""
                            sending = true
                            scope.launch {
                                try {
                                    Support.send(
                                        threadUid, body, asDev,
                                        name = myProf.name, username = myProf.username
                                    )
                                } catch (_: Exception) {
                                    text = body
                                }
                                sending = false
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

/** هل الحساب الحالي هو المطور (يتحسب مرة وحدة) */
@Composable
internal fun rememberIsDev(): Boolean {
    val v by produceState(false) { value = Support.isDev() }
    return v
}
