package com.chatapp.messenger

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.tasks.await

data class UserItem(val uid: String, val name: String, val email: String, val username: String = "")

/** عنصر بقائمة المحادثات: الطرف الثاني + آخر رسالة */
data class ChatItem(val uid: String, val lastText: String, val lastAt: Long, val unread: Int = 0)
data class Msg(
    val id: String,
    val from: String,
    val text: String,
    val createdAt: Long,
    /** الرسالة انحذفت (تظهر "رسالة مَحذوفة" بمكانها) */
    val deleted: Boolean = false,
    /** الرسالة انعدلت */
    val edited: Boolean = false,
    /** مثبتة بأعلى المحادثة (تظهر للطرفين) */
    val pinned: Boolean = false,
    val pinnedAt: Long = 0L,
    /** الرد: معرّف الرسالة الأصلية + نص مختصر + صاحبها */
    val replyId: String = "",
    val replyText: String = "",
    val replyFrom: String = ""
)

/**
 * معاينة بديلة بقائمة المحادثات عندي فقط: إذا حذفت (من عندي) آخر رسالة بالمحادثة.
 * تنطبق فقط إذا آخر نص بالمحادثة ما زال [forText] ولا وصلت رسالة جديدة بعد [at].
 */
data class HiddenPreview(val forText: String, val text: String, val at: Long)

/** حالة الاتصال كما هي مخزنة بالسيرفر (الحساب النهائي "متصل؟" يصير بالواجهة مع فحص التقادم) */
data class Presence(val online: Boolean = false, val lastSeen: Long = 0L)

/** حظر الحساب من الإدارة: until = وقت انتهاء الحظر بالميلي ثانية */
data class Ban(val until: Long = 0L, val days: Int = 0, val reason: String = "")

/** مدة القصة: 24 ساعة بالضبط من لحظة النشر */
const val STORY_MS = 24L * 60 * 60 * 1000

/**
 * قصة المستخدم (24 ساعة).
 * bg = رقم الخلفية (تدرج)، filter = معرّف فلتر الصورة ("" = بدون)،
 * موسيقى اختيارية: رابط مقطع 30 ثانية + عنوان + فنان + غلاف + ثانية البداية (القصة 15 ثانية كحد أقصى).
 */
data class Story(
    val text: String,
    val color: Int,
    val image: String,
    val createdAt: Long,
    val bg: Int = 0,
    val filter: String = "",
    val musicUrl: String = "",
    val musicTitle: String = "",
    val musicArtist: String = "",
    val musicCover: String = "",
    val musicStart: Int = 0,
    /** true = عامة (تطلع لكل المستخدمين)، false = خاصة (للأصدقاء فقط) */
    val isPublic: Boolean = false
) {
    val expiresAt: Long get() = createdAt + STORY_MS
}

/** حالة نصية قصيرة تظهر كفقاعة فوق صورة الملف الشخصي (24 ساعة) */
data class Status(val text: String, val createdAt: Long) {
    val expiresAt: Long get() = createdAt + STORY_MS
}

/** مشاهدة قصة: من شاف القصة وهل أعجبته (تظهر لصاحب القصة فقط) */
data class StoryView(val uid: String, val liked: Boolean, val at: Long)

/** قصة فعّالة لمستخدم (تظهر بقائمة القصص عند الكل) */
data class ActiveStory(val uid: String, val profile: Profile)

/** علاقتي بشخص: لا شي / أرسلت له طلب / أرسل لي طلب / أصدقاء */
enum class Relation { NONE, SENT, RECEIVED, FRIENDS }

data class Profile(
    val name: String = "",
    val username: String = "",
    val bio: String = "",
    val avatar: String = "",
    val banner: String = "",
    val story: Story? = null,
    val status: Status? = null,
    /** شكل فقاعات الرسائل المختار (يطبق على كل محادثات المستخدم ويظهر للطرفين) */
    val bubbleStyle: String = "glass",
    /** true إذا هذا المستخدم حاظرني (ما أقدر أشوف ملفه) */
    val restricted: Boolean = false
)

/** كل التعامل مع Firebase هنا. الواجهات فقط تستدعي هذي الدوال. */
object Repo {
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    /**
     * Realtime Database: الرسائل والمحادثات وعداد غير المقروء (بدون حد على عدد القراءات/الكتابات).
     * إذا قاعدة البيانات مو بمنطقة us-central1 وتطلع رسالة "Can't determine Firebase Database URL"،
     * حط رابطها هنا (تلقاه بصفحة Realtime Database بالـ Console)، أو نزّل google-services.json من جديد.
     */
    private const val RTDB_URL = ""
    val rtdb: FirebaseDatabase by lazy {
        val inst = if (RTDB_URL.isBlank()) FirebaseDatabase.getInstance() else FirebaseDatabase.getInstance(RTDB_URL)
        // كاش على القرص + الرسائل المكتوبة بدون نت تنرسل تلقائياً لما يرجع الاتصال
        runCatching { inst.setPersistenceEnabled(true) }
        inst
    }

    private fun DataSnapshot.num(name: String): Long = (child(name).value as? Number)?.toLong() ?: 0L
    private fun DataSnapshot.str(name: String): String = child(name).value as? String ?: ""
    private fun DataSnapshot.flag(name: String): Boolean = child(name).value == true

    /** يحول أي استعلام Realtime Database إلى Flow (يتحدث فوراً مع كل تغيير) */
    private fun <T> rtFlow(q: com.google.firebase.database.Query, map: (DataSnapshot) -> T): Flow<T> = callbackFlow {
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(map(s)) }
            override fun onCancelled(e: DatabaseError) {}
        }
        q.addValueEventListener(l)
        awaitClose { q.removeEventListener(l) }
    }

    /** نطاق يعيش بعمر التطبيق (حتى ما ينلغي الإرسال لو تغيرت الشاشة) */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun chatId(a: String, b: String) = listOf(a, b).sorted().joinToString("_")

    /** يحفظ بيانات المستخدم بعد تفعيل الإيميل */
    suspend fun saveProfile() {
        val u = auth.currentUser ?: return
        val data = mapOf(
            "uid" to u.uid,
            "email" to (u.email ?: ""),
            "name" to (u.displayName?.takeIf { it.isNotBlank() } ?: u.email?.substringBefore("@") ?: "")
        )
        db.collection("users").document(u.uid).set(data, SetOptions.merge()).await()
    }

    private fun parseUsers(snap: QuerySnapshot): List<UserItem> = snap.documents.mapNotNull { d ->
        val uid = d.getString("uid") ?: return@mapNotNull null
        UserItem(uid, d.getString("name") ?: "", d.getString("email") ?: "", d.getString("username") ?: "")
    }

    /**
     * قائمة المستخدمين: بدل listener دائم (كل تغيير بأي حساب = قراءة عند الكل)،
     * نعرض الكاش أولاً (مجاني) ونجلب من السيرفر مرة كل 15 دقيقة فقط.
     */
    fun usersFlow(): Flow<List<UserItem>> = callbackFlow {
        val col = db.collection("users")
        val prefs = FirebaseApp.getInstance().applicationContext
            .getSharedPreferences("repo_cache", android.content.Context.MODE_PRIVATE)
        runCatching { col.get(Source.CACHE).await() }.getOrNull()
            ?.takeIf { !it.isEmpty }?.let { trySend(parseUsers(it)) }
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("users_synced_at", 0L) > 15L * 60 * 1000) {
            runCatching { col.get(Source.SERVER).await() }.getOrNull()?.let {
                prefs.edit().putLong("users_synced_at", now).apply()
                trySend(parseUsers(it))
            }
        }
        awaitClose { }
    }

    /** آخر 50 رسالة، الأحدث أولاً */
    fun messagesFlow(me: String, other: String): Flow<List<Msg>> {
        if (me.isBlank()) return flowOf(emptyList())
        val q = rtdb.getReference("chats/${chatId(me, other)}/messages")
            .orderByChild("createdAt").limitToLast(50)
        return rtFlow(q) { snap ->
            snap.children.map { it.toMsg() }
                .sortedWith(compareByDescending<Msg> { it.createdAt }.thenByDescending { it.id })
        }
    }

    private fun DataSnapshot.toMsg() = Msg(
        id = key ?: "",
        from = str("from"),
        text = str("text"),
        // الرسالة الي توها انرسلت: الـ SDK يعطي وقت تقديري لين يأكد السيرفر
        createdAt = num("createdAt").takeIf { it > 0L } ?: System.currentTimeMillis(),
        deleted = flag("deleted"),
        edited = flag("edited"),
        pinned = flag("pinned"),
        pinnedAt = num("pinnedAt"),
        replyId = str("replyId"),
        replyText = str("replyText"),
        replyFrom = str("replyFrom")
    )

    /** الرسائل المثبتة بالمحادثة (حتى لو أقدم من آخر 50 رسالة)، الأحدث تثبيتاً أولاً */
    fun pinnedMessagesFlow(me: String, other: String): Flow<List<Msg>> {
        if (me.isBlank()) return flowOf(emptyList())
        val q = rtdb.getReference("chats/${chatId(me, other)}/messages")
            .orderByChild("pinned").equalTo(true)
        return rtFlow(q) { snap ->
            snap.children.map { it.toMsg() }.filter { !it.deleted }.sortedByDescending { it.pinnedAt }
        }
    }

    private fun msgRef(me: String, other: String, id: String) =
        rtdb.getReference("chats/${chatId(me, other)}/messages/$id")

    /** تحديث "آخر رسالة" بقائمة المحادثات عند الطرفين (يُستدعى إذا الرسالة المعدّلة/المحذوفة هي الأخيرة) */
    private fun updateLastText(me: String, other: String, text: String) {
        val t = text.take(100)
        rtdb.reference.updateChildren(
            mapOf<String, Any>(
                "userChats/$me/$other/lastText" to t,
                "userChats/$other/$me/lastText" to t
            )
        )
    }

    /** حذف ناعم: الرسالة تبقى بمكانها وتظهر "رسالة مَحذوفة"، ونص الرسالة الأصلي يتمسح */
    fun deleteMessage(me: String, other: String, id: String, isLast: Boolean, onFail: (Exception) -> Unit = {}) {
        msgRef(me, other, id).updateChildren(
            mapOf<String, Any>("deleted" to true, "text" to "", "pinned" to false, "edited" to false)
        ).addOnFailureListener(onFail)
        if (isLast) updateLastText(me, other, "رسالة مَحذوفة")
    }

    fun editMessage(me: String, other: String, id: String, newText: String, isLast: Boolean, onFail: (Exception) -> Unit = {}) {
        msgRef(me, other, id).updateChildren(mapOf<String, Any>("text" to newText, "edited" to true))
            .addOnFailureListener(onFail)
        if (isLast) updateLastText(me, other, newText)
    }

    fun setMessagePinned(me: String, other: String, id: String, pinned: Boolean, onFail: (Exception) -> Unit = {}) {
        msgRef(me, other, id).updateChildren(
            if (pinned) mapOf<String, Any>("pinned" to true, "pinnedAt" to ServerValue.TIMESTAMP, "pinnedBy" to me)
            else mapOf<String, Any>("pinned" to false)
        ).addOnFailureListener(onFail)
    }

    /** المحادثات الي أنا طرف فيها (تظهر بعد أول رسالة)، الأحدث أولاً */
    fun chatsFlow(me: String): Flow<List<ChatItem>> {
        if (me.isBlank()) return flowOf(emptyList())
        return rtFlow(rtdb.getReference("userChats/$me")) { snap ->
            snap.children.mapNotNull { c ->
                val other = c.key ?: return@mapNotNull null
                // عقدة بدون lastAt = ما صار فيها رسائل بعد (مثلاً فقط تصفير عداد)
                if (!c.hasChild("lastAt")) return@mapNotNull null
                ChatItem(other, c.str("lastText"), c.num("lastAt"), c.num("unread").toInt())
            }.sortedByDescending { it.lastAt }
        }
    }

    fun send(
        me: String, other: String, text: String,
        replyId: String = "", replyText: String = "", replyFrom: String = ""
    ) {
        val cid = chatId(me, other)
        val key = rtdb.getReference("chats/$cid/messages").push().key ?: return
        val msg = hashMapOf<String, Any>(
            "from" to me,
            "to" to other,
            "text" to text,
            "createdAt" to ServerValue.TIMESTAMP
        )
        if (replyId.isNotEmpty()) {
            msg["replyId"] = replyId
            msg["replyText"] = replyText.take(100)
            msg["replyFrom"] = replyFrom
        }
        val preview = text.take(100)
        // عملية واحدة ذرية: الرسالة + قائمة المحادثات عند الطرفين + عداد غير المقروء عند الطرف الثاني
        val updates = hashMapOf<String, Any>(
            "chats/$cid/messages/$key" to msg,
            "userChats/$other/$me/lastText" to preview,
            "userChats/$other/$me/lastAt" to ServerValue.TIMESTAMP,
            "userChats/$other/$me/unread" to ServerValue.increment(1),
            "userChats/$me/$other/lastText" to preview,
            "userChats/$me/$other/lastAt" to ServerValue.TIMESTAMP
        )
        rtdb.reference.updateChildren(updates)
    }


    // ---------- غير المقروء + حذف المحادثة من عندي فقط ----------

    /** أصفّر عداد الرسائل غير المقروءة لهذي المحادثة وأسجل وقت قراءتي (الطرف الثاني يشوف منه "مقروء") */
    fun markRead(me: String, other: String) {
        rtdb.reference.updateChildren(
            mapOf<String, Any>(
                "userChats/$me/$other/unread" to 0,
                "chats/${chatId(me, other)}/readAt/$me" to ServerValue.TIMESTAMP
            )
        )
    }

    /** وقت آخر مرة الطرف الثاني قرأ المحادثة (0 = ما قرأ بعد) */
    fun readAtFlow(me: String, other: String): Flow<Long> {
        if (me.isBlank()) return flowOf(0L)
        return rtFlow(rtdb.getReference("chats/${chatId(me, other)}/readAt/$other")) { snap ->
            (snap.value as? Number)?.toLong() ?: 0L
        }
    }

    /** لكل شخص: وقت آخر "حذف محادثة" عندي. الرسائل الأقدم منه تنخفي عندي فقط */
    fun clearedFlow(me: String): Flow<Map<String, Long>> = callbackFlow {
        if (me.isBlank()) { close(); return@callbackFlow }
        val reg = db.collection("settings").document(me).addSnapshotListener { d, _ ->
            if (d != null) {
                val m = (d.get("cleared") as? Map<*, *>)?.mapNotNull { (k, v) ->
                    (k as? String)?.let { it to ((v as? Number)?.toLong() ?: 0L) }
                }?.toMap() ?: emptyMap()
                trySend(m)
            }
        }
        awaitClose { reg.remove() }
    }

    // ---------- حذف رسالة من عندي فقط ----------
    // يتخزن بوثيقة settings/<uid> (قواعدها تسمح لصاحبها فقط) فما تحتاج تغيير للقواعد:
    //   hidden.<otherUid>   = قائمة معرّفات الرسائل المخفية عندي
    //   previews.<otherUid> = معاينة بديلة بقائمة المحادثات إذا الرسالة المخفية كانت الأخيرة

    /** معرّفات الرسائل الي حذفتها من عندي بهذي المحادثة */
    fun hiddenFlow(me: String, other: String): Flow<Set<String>> = callbackFlow {
        if (me.isBlank()) { close(); return@callbackFlow }
        val reg = db.collection("settings").document(me).addSnapshotListener { d, _ ->
            if (d != null) {
                val list = ((d.get("hidden") as? Map<*, *>)?.get(other) as? List<*>)
                    ?.filterIsInstance<String>() ?: emptyList()
                trySend(list.toSet())
            }
        }
        awaitClose { reg.remove() }
    }

    /** المعاينات البديلة لقائمة المحادثات (لكل شخص) */
    fun previewOverridesFlow(me: String): Flow<Map<String, HiddenPreview>> = callbackFlow {
        if (me.isBlank()) { close(); return@callbackFlow }
        val reg = db.collection("settings").document(me).addSnapshotListener { d, _ ->
            if (d != null) {
                val m = (d.get("previews") as? Map<*, *>)?.mapNotNull { (k, v) ->
                    val uid = k as? String ?: return@mapNotNull null
                    val o = v as? Map<*, *> ?: return@mapNotNull null
                    uid to HiddenPreview(
                        forText = o["forText"] as? String ?: "",
                        text = o["text"] as? String ?: "",
                        at = (o["at"] as? Number)?.toLong() ?: 0L
                    )
                }?.toMap() ?: emptyMap()
                trySend(m)
            }
        }
        awaitClose { reg.remove() }
    }

    /** حذف رسالة من عندي فقط (رسالتي أو رسالة الطرف الثاني). الطرف الثاني ما يتأثر أبداً */
    fun hideMessage(
        me: String, other: String, id: String,
        preview: HiddenPreview? = null,
        onFail: (Exception) -> Unit = {}
    ) {
        val data = mutableMapOf<String, Any>(
            "hidden" to mapOf(other to FieldValue.arrayUnion(id))
        )
        if (preview != null) {
            data["previews"] = mapOf(
                other to mapOf("forText" to preview.forText, "text" to preview.text, "at" to preview.at)
            )
        }
        db.collection("settings").document(me).set(data, SetOptions.merge())
            .addOnFailureListener(onFail)
    }

    /** حذف المحادثة من عندي فقط: الطرف الثاني تبقى عنده كاملة */
    fun clearChat(me: String, other: String, lastAt: Long) {
        val at = if (lastAt <= 0L || lastAt == Long.MAX_VALUE) System.currentTimeMillis() else lastAt
        db.collection("settings").document(me).set(
            mapOf(
                "cleared" to mapOf(other to at),
                // المحادثة تختفي كلياً من القائمة: نشيل التثبيت (المثبتين يظهرون حتى بدون رسائل)
                "pinned" to FieldValue.arrayRemove(other),
                // تنظيف سجل "حذف من عندي" لهذي المحادثة (ما عاد نحتاجه)
                "hidden" to mapOf(other to FieldValue.delete()),
                "previews" to mapOf(other to FieldValue.delete())
            ), SetOptions.merge()
        )
        markRead(me, other)
    }

    // ---------- متصل / آخر ظهور ----------
    // مجموعة presence/<uid> منفصلة عن profiles (حتى ما تنرسل صور base64 مع كل نبضة)

    /** أكتب حالتي: online=true مع وقت السيرفر، أو online=false = آخر ظهور الحين */
    private var lastPresenceKey = ""
    private var lastPresenceAt = 0L

    fun setPresence(online: Boolean) {
        val u = auth.currentUser ?: return
        val now = System.currentTimeMillis()
        val key = "${u.uid}:$online"
        // نفس الحالة خلال أقل من 60 ثانية (تبديل سريع بين التطبيقات): ما نكتب مرة ثانية
        if (key == lastPresenceKey && now - lastPresenceAt < 60_000L) return
        lastPresenceKey = key
        lastPresenceAt = now
        db.collection("presence").document(u.uid).set(
            mapOf("online" to online, "lastSeen" to FieldValue.serverTimestamp()),
            SetOptions.merge()
        )
    }

    fun presenceFlow(uid: String): Flow<Presence> = callbackFlow {
        if (uid.isBlank()) { close(); return@callbackFlow }
        val reg = db.collection("presence").document(uid).addSnapshotListener { d, err ->
            if (err != null) {
                // الصلاحيات ترفض القراءة إذا هذا المستخدم حاظرني
                trySend(Presence())
            } else if (d != null) {
                trySend(
                    Presence(
                        online = d.getBoolean("online") == true,
                        lastSeen = d.getTimestamp("lastSeen", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            ?.toDate()?.time ?: 0L
                    )
                )
            }
        }
        awaitClose { reg.remove() }
    }

    // ---------- الملف الشخصي والقصة (مجموعة profiles منفصلة حتى ما تثقل قائمة المستخدمين) ----------

    /** تحويل خريطة القصة المخزنة إلى كائن Story */
    private fun parseStory(it: Map<*, *>) = Story(
        text = it["text"] as? String ?: "",
        color = (it["color"] as? Number)?.toInt() ?: -1,
        image = it["image"] as? String ?: "",
        createdAt = (it["createdAt"] as? Number)?.toLong() ?: 0L,
        bg = (it["bg"] as? Number)?.toInt() ?: 0,
        filter = it["filter"] as? String ?: "",
        musicUrl = it["musicUrl"] as? String ?: "",
        musicTitle = it["musicTitle"] as? String ?: "",
        musicArtist = it["musicArtist"] as? String ?: "",
        musicCover = it["musicCover"] as? String ?: "",
        musicStart = (it["musicStart"] as? Number)?.toInt() ?: 0,
        isPublic = it["public"] as? Boolean ?: false
    )

    /** بيانات الملف الشخصي الأساسية فقط (بدون القصة): خفيفة، تنفع لصفوف القوائم */
    fun basicProfileFlow(uid: String): Flow<Profile> = callbackFlow {
        if (uid.isBlank()) { close(); return@callbackFlow }
        val reg = db.collection("profiles").document(uid).addSnapshotListener { d, err ->
            if (err != null) {
                // الصلاحيات ترفض القراءة إذا هذا المستخدم حاظرني
                trySend(Profile(restricted = true))
            } else if (d != null) {
                val stt = d.get("status") as? Map<*, *>
                val status = stt?.let {
                    Status(
                        text = it["text"] as? String ?: "",
                        createdAt = (it["createdAt"] as? Number)?.toLong() ?: 0L
                    )
                }
                trySend(
                    Profile(
                        name = d.getString("name") ?: "",
                        username = d.getString("username") ?: "",
                        bio = d.getString("bio") ?: "",
                        avatar = d.getString("avatar") ?: "",
                        banner = d.getString("banner") ?: "",
                        story = null,
                        status = status,
                        bubbleStyle = d.getString("bubbleStyle") ?: "glass"
                    )
                )
            }
        }
        awaitClose { reg.remove() }
    }

    /** القصة محفوظة بوثيقة منفصلة storyData/<uid> (القواعد تسمح بقراءتها لصاحبها وأصدقائه فقط) */
    private fun storyDocFlow(uid: String): Flow<Story?> = callbackFlow {
        if (uid.isBlank()) { close(); return@callbackFlow }
        val reg = db.collection("storyData").document(uid).addSnapshotListener { d, err ->
            if (err != null) trySend(null)
            else if (d != null) trySend(if (d.exists()) (d.data as? Map<*, *>)?.let { parseStory(it) } else null)
        }
        awaitClose { reg.remove() }
    }

    /** هل قصة هذا المستخدم عامة؟ (من الفهرس العام stories/<uid>) */
    private fun storyPublicFlow(uid: String): Flow<Boolean> = callbackFlow {
        if (uid.isBlank()) { close(); return@callbackFlow }
        val reg = db.collection("stories").document(uid).addSnapshotListener { d, err ->
            if (err != null) trySend(false)
            else if (d != null) trySend(d.exists() && d.getBoolean("public") == true)
        }
        awaitClose { reg.remove() }
    }

    /** الملف الشخصي + القصة. قصة غير الأصدقاء تنقرأ فقط إذا كانت عامة، وإلا تطلع null */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun profileFlow(uid: String): Flow<Profile> {
        val story: Flow<Story?> =
            if (uid == auth.currentUser?.uid) storyDocFlow(uid)
            else isFriendFlow(uid).flatMapLatest { friend ->
                // الصديق يشوف القصة دائماً. غير الصديق يشوفها فقط إذا القصة عامة (حسب الفهرس العام)
                if (friend) storyDocFlow(uid)
                else storyPublicFlow(uid).flatMapLatest { pub -> if (pub) storyDocFlow(uid) else flowOf(null) }
            }
        return combine(basicProfileFlow(uid), story) { p, s -> p.copy(story = s) }
    }

    /** ينقل قصة قديمة (كانت داخل profiles) للمكان الجديد المحمي، مرة وحدة */
    suspend fun migrateLegacyStory() {
        val u = auth.currentUser ?: return
        val d = db.collection("profiles").document(u.uid).get().await()
        val legacy = (d.get("story") as? Map<*, *>)?.let { parseStory(it) } ?: return
        if (System.currentTimeMillis() < legacy.expiresAt) saveStory(legacy)
        else db.collection("profiles").document(u.uid)
            .set(mapOf("story" to FieldValue.delete()), SetOptions.merge()).await()
    }

    // ---------- اليوزر: فريد لكل مستخدم (مجموعة usernames تحجز الاسم) ----------

    private fun genUsername(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return "user_" + (1..7).map { chars.random() }.joinToString("")
    }

    /** يحجز اليوزر للمستخدم. يرجع false إذا محجوز لشخص ثاني. يفك حجز اليوزر القديم */
    private suspend fun claimUsername(uid: String, username: String, old: String): Boolean {
        val ref = db.collection("usernames").document(username)
        val oldRef = if (old.isNotBlank() && old != username) db.collection("usernames").document(old) else null
        return db.runTransaction { tx ->
            val snap = tx.get(ref)
            val oldSnap = oldRef?.let { tx.get(it) }
            if (snap.exists() && snap.getString("uid") != uid) {
                false
            } else {
                tx.set(ref, mapOf("uid" to uid))
                if (oldRef != null && oldSnap != null && oldSnap.exists() && oldSnap.getString("uid") == uid) {
                    tx.delete(oldRef)
                }
                true
            }
        }.await()
    }

    /** إذا المستخدم ما عنده يوزر يعطيه يوزر افتراضي عشوائي فريد، وإذا عنده يتأكد انحجز */
    suspend fun ensureUsername() {
        val u = auth.currentUser ?: return
        val profRef = db.collection("profiles").document(u.uid)
        val current = profRef.get().await().getString("username") ?: ""
        if (current.isNotBlank()) {
            claimUsername(u.uid, current, "")
            db.collection("users").document(u.uid).set(mapOf("username" to current), SetOptions.merge()).await()
            return
        }
        repeat(8) {
            val cand = genUsername()
            if (claimUsername(u.uid, cand, "")) {
                profRef.set(mapOf("username" to cand), SetOptions.merge()).await()
                db.collection("users").document(u.uid).set(mapOf("username" to cand), SetOptions.merge()).await()
                return
            }
        }
    }

    /** حفظ الاسم/اليوزر/النبذة/الصور. الصور نص base64 مضغوط */
    suspend fun saveProfileInfo(name: String, username: String, bio: String, avatar: String, banner: String) {
        val u = auth.currentUser ?: return
        val old = db.collection("profiles").document(u.uid).get().await().getString("username") ?: ""
        val finalUser = username.ifBlank { old }
        if (finalUser != old) {
            if (finalUser.length < 3) throw Exception("اليوزر لازم يكون 3 حروف على الأقل")
            if (!claimUsername(u.uid, finalUser, old)) throw Exception("اليوزر محجوز، اختر غيره")
        }
        db.collection("profiles").document(u.uid).set(
            mapOf("name" to name, "username" to finalUser, "bio" to bio, "avatar" to avatar, "banner" to banner),
            SetOptions.merge()
        ).await()
        db.collection("users").document(u.uid).set(mapOf("name" to name, "username" to finalUser), SetOptions.merge()).await()
        // حتى saveProfile() ما يرجّع الاسم القديم عند كل فتح للتطبيق
        u.updateProfile(
            com.google.firebase.auth.UserProfileChangeRequest.Builder().setDisplayName(name).build()
        ).await()
    }

    /** نشر القصة: تنكتب بوثيقة محمية storyData/<uid> (للأصدقاء فقط) + فهرس عام `stories/<uid>` يحتوي الوقت فقط */
    suspend fun saveStory(st: Story) {
        val u = auth.currentUser ?: return
        val map = mapOf(
            "text" to st.text, "color" to st.color, "image" to st.image, "createdAt" to st.createdAt,
            "bg" to st.bg, "filter" to st.filter,
            "musicUrl" to st.musicUrl, "musicTitle" to st.musicTitle, "musicArtist" to st.musicArtist,
            "musicCover" to st.musicCover, "musicStart" to st.musicStart,
            "public" to st.isPublic
        )
        db.batch()
            .set(db.collection("storyData").document(u.uid), map)
            .set(db.collection("stories").document(u.uid), mapOf("uid" to u.uid, "createdAt" to st.createdAt, "public" to st.isPublic))
            // نشيل أي نسخة قديمة من القصة كانت مكشوفة داخل الملف الشخصي
            .set(db.collection("profiles").document(u.uid), mapOf("story" to FieldValue.delete()), SetOptions.merge())
            .commit().await()
        // مشاهدات/إعجابات القصة القديمة تنمسح (القصة الجديدة تبدأ من الصفر)
        runCatching { clearStoryViews(keepStoryAt = st.createdAt) }
    }

    /** الحالة النصية (فقاعة فوق الصورة) */
    suspend fun saveStatus(text: String) {
        val u = auth.currentUser ?: return
        db.collection("profiles").document(u.uid).set(
            mapOf("status" to mapOf("text" to text, "createdAt" to System.currentTimeMillis())),
            SetOptions.mergeFields("status")
        ).await()
    }

    suspend fun deleteStatus() {
        val u = auth.currentUser ?: return
        db.collection("profiles").document(u.uid).update("status", FieldValue.delete()).await()
    }

    /** يضمن وجود القصة الحالية بالفهرس العام (للقصص المنشورة بنسخ قديمة) */
    suspend fun syncStoryIndex(createdAt: Long, isPublic: Boolean = false) {
        val u = auth.currentUser ?: return
        db.collection("stories").document(u.uid)
            .set(mapOf("uid" to u.uid, "createdAt" to createdAt, "public" to isPublic), SetOptions.merge()).await()
    }

    /** تغيير خصوصية القصة الحالية: عامة (الكل) أو خاصة (الأصدقاء فقط) */
    suspend fun setStoryPublic(isPublic: Boolean) {
        val u = auth.currentUser ?: return
        db.batch()
            .set(db.collection("storyData").document(u.uid), mapOf("public" to isPublic), SetOptions.merge())
            .set(db.collection("stories").document(u.uid), mapOf("public" to isPublic), SetOptions.merge())
            .commit().await()
    }

    suspend fun deleteStory() {
        val u = auth.currentUser ?: return
        runCatching { clearStoryViews(keepStoryAt = -1L) }
        db.batch()
            .delete(db.collection("storyData").document(u.uid))
            .delete(db.collection("stories").document(u.uid))
            .set(db.collection("profiles").document(u.uid), mapOf("story" to FieldValue.delete()), SetOptions.merge())
            .commit().await()
    }

    /** معرّفات أصحاب القصص الفعّالة (آخر 24 ساعة) */
    private fun storyIndexFlow(): Flow<List<Pair<String, Boolean>>> = callbackFlow {
        val since = System.currentTimeMillis() - STORY_MS
        val reg = db.collection("stories").whereGreaterThan("createdAt", since)
            .addSnapshotListener { snap, err ->
                if (err != null) trySend(emptyList())
                else if (snap != null) trySend(snap.documents.map { it.id to (it.getBoolean("public") == true) })
            }
        awaitClose { reg.remove() }
    }

    /**
     * قصص الفعّالة: قصتي + قصص أصدقائي + القصص العامة (الأحدث أولاً، وقصتي أولاً).
     * من حاظرني تنرفض قراءة ملفه بالقواعد فتختفي قصته تلقائياً (restricted).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun activeStoriesFlow(): Flow<List<ActiveStory>> =
        combine(storyIndexFlow(), friendIdsFlow()) { uids, friends ->
            val me = auth.currentUser?.uid
            // قصتي + قصص الأصدقاء + أي قصة عامة
            uids.filter { (id, pub) -> id == me || id in friends || pub }.map { it.first }.sorted()
        }.distinctUntilChanged().flatMapLatest { uids ->
            if (uids.isEmpty()) flowOf(emptyList())
            else combine(uids.map { uid -> profileFlow(uid).map { ActiveStory(uid, it) } }) { arr ->
                val me = auth.currentUser?.uid
                val now = System.currentTimeMillis()
                arr.filter { a ->
                    val st = a.profile.story
                    !a.profile.restricted && st != null && now < st.expiresAt
                }.sortedWith(
                    compareByDescending<ActiveStory> { it.uid == me }
                        .thenByDescending { it.profile.story?.createdAt ?: 0L }
                )
            }
        }

    // ---------- مشاهدات وإعجابات القصة ----------
    // storyData/<owner>/views/<viewer> = { uid, storyAt, liked, at, likedAt }
    // الكتابة: المشاهد (صديق صاحب القصة) لوثيقته هو فقط. القراءة: صاحب القصة لكل المشاهدات، والمشاهد لوثيقته.

    private fun viewsCol(owner: String) =
        db.collection("storyData").document(owner).collection("views")

    /** يمسح مشاهدات القصص الأقدم (أو كلها إذا keepStoryAt = -1) */
    private suspend fun clearStoryViews(keepStoryAt: Long) {
        val u = auth.currentUser ?: return
        val snap = viewsCol(u.uid).get().await()
        val old = snap.documents.filter { ((it.get("storyAt") as? Number)?.toLong() ?: 0L) != keepStoryAt }
        old.chunked(400).forEach { part ->
            val b = db.batch()
            part.forEach { b.delete(it.reference) }
            b.commit().await()
        }
    }

    /** أنا شفت قصة [owner]: تتسجل مرة وحدة لكل قصة (وما تمسح إعجابي إذا رجعت شفتها) */
    suspend fun markStoryViewed(owner: String, storyAt: Long) {
        val me = auth.currentUser?.uid ?: return
        if (me == owner) return
        val ref = viewsCol(owner).document(me)
        val old = runCatching { ref.get().await() }.getOrNull()
        if (old != null && old.exists() && ((old.get("storyAt") as? Number)?.toLong() ?: 0L) == storyAt) return
        ref.set(
            mapOf("uid" to me, "storyAt" to storyAt, "liked" to false, "at" to System.currentTimeMillis())
        ).await()
    }

    /** هل أنا معجب بقصة [owner] الحالية؟ (تتحدث مباشرة) */
    fun myStoryLikeFlow(owner: String, storyAt: Long): Flow<Boolean> = callbackFlow {
        val me = auth.currentUser?.uid
        if (me == null || me == owner) { trySend(false); close(); return@callbackFlow }
        val reg = viewsCol(owner).document(me).addSnapshotListener { d, _ ->
            val same = ((d?.get("storyAt") as? Number)?.toLong() ?: 0L) == storyAt
            trySend(d != null && d.exists() && same && d.getBoolean("liked") == true)
        }
        awaitClose { reg.remove() }
    }

    /** إعجاب / إلغاء إعجاب بقصة [owner] */
    suspend fun setStoryLike(owner: String, storyAt: Long, liked: Boolean) {
        val me = auth.currentUser?.uid ?: return
        if (me == owner) return
        val now = System.currentTimeMillis()
        viewsCol(owner).document(me).set(
            mapOf("uid" to me, "storyAt" to storyAt, "liked" to liked, "at" to now, "likedAt" to if (liked) now else 0L)
        ).await()
    }

    /** مشاهدات قصتي الحالية (لصاحب القصة فقط): الأحدث أولاً */
    fun myStoryViewsFlow(storyAt: Long): Flow<List<StoryView>> = callbackFlow {
        val me = auth.currentUser?.uid
        if (me == null) { trySend(emptyList()); close(); return@callbackFlow }
        val reg = viewsCol(me).addSnapshotListener { snap, err ->
            if (err != null) trySend(emptyList())
            else if (snap != null) {
                trySend(
                    snap.documents.filter { ((it.get("storyAt") as? Number)?.toLong() ?: 0L) == storyAt }
                        .map { StoryView(it.id, it.getBoolean("liked") == true, (it.get("at") as? Number)?.toLong() ?: 0L) }
                        .sortedByDescending { it.at }
                )
            }
        }
        awaitClose { reg.remove() }
    }

    /** مشاركة قصة لأصدقائي: تنرسل لكل صديق كرسالة بالمحادثة (مع اقتباس "قصة فلان") */
    fun shareStory(to: Collection<String>, owner: String, ownerName: String) {
        val me = auth.currentUser?.uid ?: return
        val who = ownerName.ifBlank { "مستخدم" }
        val text = if (owner == me) "📸 شاركتلك قصتي" else "📸 شاركتلك قصة $who"
        to.forEach { friend ->
            send(me, friend, text, replyId = "story", replyText = if (owner == me) "قصتي" else "قصة $who", replyFrom = owner)
        }
    }

    /** رد على حالة شخص: يوصل كرسالة بالمحادثة معه (مع اقتباس نص الحالة) */
    fun sendStatusReply(owner: String, statusText: String, text: String) {
        val me = auth.currentUser?.uid ?: return
        send(me, owner, text, replyId = "status", replyText = statusText.take(100), replyFrom = owner)
    }

    /** رد على قصة: يوصل كرسالة بالمحادثة مع صاحب القصة (مع اقتباس "القصة") */
    fun sendStoryReply(owner: String, text: String) {
        val me = auth.currentUser?.uid ?: return
        send(me, owner, text, replyId = "story", replyText = "القصة", replyFrom = owner)
    }

    // ---------- الأصدقاء ----------
    // friendRequests/<from>_<to> = طلب صداقة، friendships/<uidA>_<uidB> (مرتبين) = صداقة قائمة

    private fun docExistsFlow(col: String, id: String): Flow<Boolean> = callbackFlow {
        val reg = db.collection(col).document(id).addSnapshotListener { d, err ->
            if (err != null) trySend(false) else if (d != null) trySend(d.exists())
        }
        awaitClose { reg.remove() }
    }

    /** هل [other] صديقي؟ */
    fun isFriendFlow(other: String): Flow<Boolean> {
        val me = auth.currentUser?.uid ?: return flowOf(false)
        return docExistsFlow("friendships", chatId(me, other))
    }

    /** أصدقائي (الأحدث صداقة أولاً) */
    fun friendsFlow(me: String): Flow<List<String>> = callbackFlow {
        if (me.isBlank()) { close(); return@callbackFlow }
        val reg = db.collection("friendships").whereArrayContains("users", me)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    trySend(
                        snap.documents.mapNotNull { d ->
                            val other = (d.get("users") as? List<*>)?.firstOrNull { it != me } as? String
                                ?: return@mapNotNull null
                            other to (d.getTimestamp("at", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                                ?.toDate()?.time ?: 0L)
                        }.sortedByDescending { it.second }.map { it.first }
                    )
                }
            }
        awaitClose { reg.remove() }
    }

    fun friendIdsFlow(): Flow<Set<String>> {
        val me = auth.currentUser?.uid ?: return flowOf(emptySet())
        return friendsFlow(me).map { it.toSet() }
    }

    /** طلبات الصداقة الواردة لي (معرّفات المرسلين) */
    fun incomingRequestsFlow(me: String): Flow<List<String>> = callbackFlow {
        if (me.isBlank()) { close(); return@callbackFlow }
        val reg = db.collection("friendRequests").whereEqualTo("to", me)
            .addSnapshotListener { snap, _ ->
                if (snap != null) trySend(snap.documents.mapNotNull { it.getString("from") })
            }
        awaitClose { reg.remove() }
    }

    /** علاقتي بهذا الشخص (تتحدث مباشرة) */
    fun relationFlow(other: String): Flow<Relation> {
        val me = auth.currentUser?.uid ?: return flowOf(Relation.NONE)
        return combine(
            docExistsFlow("friendships", chatId(me, other)),
            docExistsFlow("friendRequests", "${me}_$other"),
            docExistsFlow("friendRequests", "${other}_$me")
        ) { friend, sent, received ->
            when {
                friend -> Relation.FRIENDS
                received -> Relation.RECEIVED
                sent -> Relation.SENT
                else -> Relation.NONE
            }
        }
    }

    suspend fun sendFriendRequest(other: String) {
        val me = auth.currentUser?.uid ?: return
        db.collection("friendRequests").document("${me}_$other").set(
            mapOf("from" to me, "to" to other, "createdAt" to FieldValue.serverTimestamp())
        ).await()
    }

    /** إلغاء طلب أرسلته أنا */
    suspend fun cancelFriendRequest(other: String) {
        val me = auth.currentUser?.uid ?: return
        db.collection("friendRequests").document("${me}_$other").delete().await()
    }

    /** رفض طلب وارد لي */
    suspend fun declineFriendRequest(from: String) {
        val me = auth.currentUser?.uid ?: return
        db.collection("friendRequests").document("${from}_$me").delete().await()
    }

    /** قبول طلب وارد: تنخلق الصداقة (للطرفين) والطلب ينحذف */
    suspend fun acceptFriendRequest(from: String) {
        val me = auth.currentUser?.uid ?: return
        db.batch()
            .set(
                db.collection("friendships").document(chatId(me, from)),
                mapOf("users" to listOf(me, from), "at" to FieldValue.serverTimestamp())
            )
            .delete(db.collection("friendRequests").document("${from}_$me"))
            .commit().await()
        // لو كنت أنا مرسل له طلب بنفس الوقت ننظفه
        runCatching { db.collection("friendRequests").document("${me}_$from").delete().await() }
    }

    /** إلغاء الصداقة (من الطرفين) */
    suspend fun removeFriend(other: String) {
        val me = auth.currentUser?.uid ?: return
        db.collection("friendships").document(chatId(me, other)).delete().await()
    }

    // ---------- شكل الفقاعات (يتخزن بملفي الشخصي حتى يشوفه الطرف الثاني) ----------

    fun setBubbleStyle(id: String) {
        val u = auth.currentUser ?: return
        db.collection("profiles").document(u.uid).set(mapOf("bubbleStyle" to id), SetOptions.merge())
    }

    // ---------- الحظر ----------

    /** هل [owner] حاظر [target]؟ */
    fun blockedFlow(owner: String, target: String): Flow<Boolean> = callbackFlow {
        val reg = db.collection("blocks").document(owner).collection("users").document(target)
            .addSnapshotListener { d, err ->
                if (err != null) trySend(false) else if (d != null) trySend(d.exists())
            }
        awaitClose { reg.remove() }
    }

    fun setBlocked(other: String, blocked: Boolean) {
        val u = auth.currentUser ?: return
        val ref = db.collection("blocks").document(u.uid).collection("users").document(other)
        if (blocked) ref.set(mapOf("at" to FieldValue.serverTimestamp())) else ref.delete()
        // نسخة بـ Realtime Database حتى قواعد الرسائل تمنع المحظور من المراسلة
        val r = rtdb.getReference("blocks/${u.uid}/$other")
        if (blocked) r.setValue(true) else r.removeValue()
    }

    // ---------- التثبيت (شخص مميز) ----------

    fun pinnedFlow(me: String): Flow<Set<String>> = callbackFlow {
        if (me.isBlank()) { close(); return@callbackFlow }
        val reg = db.collection("settings").document(me).addSnapshotListener { d, _ ->
            if (d != null) {
                val list = (d.get("pinned") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                trySend(list.toSet())
            }
        }
        awaitClose { reg.remove() }
    }

    fun setPinned(other: String, pinned: Boolean) {
        val u = auth.currentUser ?: return
        db.collection("settings").document(u.uid).set(
            mapOf("pinned" to if (pinned) FieldValue.arrayUnion(other) else FieldValue.arrayRemove(other)),
            SetOptions.merge()
        )
    }

    // ---------- البلاغات والحظر من الإدارة ----------

    /** يرسل بلاغ. السيرفر (Cloud Function) هو الي يتحقق من الرسائل ويعاقب إذا ثبت الخطأ */
    suspend fun report(reported: String, reason: String, messageIds: List<String>) {
        val me = auth.currentUser?.uid ?: return
        db.collection("reports").add(
            mapOf(
                "reporter" to me,
                "reported" to reported,
                "chatId" to chatId(me, reported),
                "reason" to reason,
                "messageIds" to messageIds.take(15),
                "status" to "new",
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    fun banFlow(me: String): Flow<Ban> = callbackFlow {
        if (me.isBlank()) { close(); return@callbackFlow }
        val reg = db.collection("bans").document(me).addSnapshotListener { d, err ->
            if (err != null) {
                trySend(Ban())
            } else if (d != null) {
                trySend(
                    if (d.exists()) Ban(
                        until = (d.get("until") as? Number)?.toLong() ?: 0L,
                        days = (d.get("days") as? Number)?.toInt() ?: 0,
                        reason = d.getString("reason") ?: ""
                    ) else Ban()
                )
            }
        }
        awaitClose { reg.remove() }
    }

    // ---------- الإشعارات (FCM) ----------

    /** يسجل توكن هذا الجهاز لحساب المستخدم الحالي، حتى يوصله إشعار. بدون حساب = بدون توكن = ما توصل إشعارات */
    fun saveFcmToken(token: String) {
        val u = auth.currentUser ?: return
        db.collection("tokens").document(u.uid).collection("devices").document(token)
            .set(mapOf("at" to FieldValue.serverTimestamp()))
    }

    /** مرة وحدة لكل حساب: ينسخ قائمة الحظر الحالية من Firestore إلى Realtime Database */
    private fun syncBlocksToRtdb() {
        val u = auth.currentUser ?: return
        val prefs = FirebaseApp.getInstance().applicationContext
            .getSharedPreferences("repo_cache", android.content.Context.MODE_PRIVATE)
        val flag = "blocks_synced_${u.uid}"
        if (prefs.getBoolean(flag, false)) return
        appScope.launch {
            runCatching {
                val snap = db.collection("blocks").document(u.uid).collection("users").get().await()
                val up = HashMap<String, Any>()
                for (d in snap.documents) up["blocks/${u.uid}/${d.id}"] = true
                if (up.isNotEmpty()) rtdb.reference.updateChildren(up).await()
                prefs.edit().putBoolean(flag, true).apply()
            }
        }
    }

    fun registerFcmToken() {
        if (auth.currentUser == null) return
        syncBlocksToRtdb()
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnSuccessListener { saveFcmToken(it) }
    }

    /** تسجيل الخروج: يحذف توكن الجهاز أولاً حتى ما توصل إشعارات الحساب بعد الخروج */
    fun signOut() {
        val u = auth.currentUser
        if (u == null) {
            auth.signOut()
            return
        }
        appScope.launch {
            // قبل الخروج: أصير "غير متصل" (القواعد تحتاج الحساب لسا مسجل دخول)
            try {
                withTimeoutOrNull(3000) {
                    db.collection("presence").document(u.uid).set(
                        mapOf("online" to false, "lastSeen" to FieldValue.serverTimestamp()),
                        SetOptions.merge()
                    ).await()
                }
            } catch (_: Exception) {
            }
            try {
                val fm = com.google.firebase.messaging.FirebaseMessaging.getInstance()
                val token = withTimeoutOrNull(3000) { fm.token.await() }
                if (token != null) {
                    withTimeoutOrNull(3000) {
                        db.collection("tokens").document(u.uid).collection("devices").document(token).delete().await()
                    }
                }
                withTimeoutOrNull(3000) { fm.deleteToken().await() }
            } catch (_: Exception) {
            }
            auth.signOut()
        }
    }
}
