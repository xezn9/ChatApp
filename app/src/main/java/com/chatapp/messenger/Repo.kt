package com.chatapp.messenger

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.firebase.FirebaseApp
import com.google.firebase.database.ChildEventListener
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
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

data class UserItem(val uid: String, val name: String, val email: String, val username: String = "")

/** مجموعة: بياناتها من groups/{id}/meta وعدد الأعضاء من groups/{id}/members */
data class GroupItem(
    val id: String,
    val name: String,
    val handle: String,
    val bio: String,
    val avatar: String,
    val owner: String,
    val memberCount: Int,
    val createdAt: Long,
    /** بانر المجموعة (صورة ثابتة base64، تنعرض إذا ما فيه GIF أو لحد ما ينزل) */
    val banner: String = "",
    /** موضع القص للبانر: -1..1 لكل محور (0 = المنتصف) */
    val bannerFx: Float = 0f,
    val bannerFy: Float = 0f,
    /** رقم نسخة GIF البانر المخزّن بـ RTDB (0 = ما فيه GIF) */
    val bannerGifV: Long = 0L,
    /** رقم نسخة GIF صورة المجموعة المخزّن بـ RTDB (0 = صورة ثابتة) */
    val avatarGifV: Long = 0L
)

/** تغيير ملف GIF لمجموعة: بدون تغيير / حذف / رفع جديد */
sealed class GifChange {
    object Keep : GifChange()
    object Remove : GifChange()
    class Set(val bytes: ByteArray) : GifChange()
}

/** صلاحيات مشرف (مثل تيليجرام) */
data class GroupPerms(
    val changeInfo: Boolean = false,
    val deleteMessages: Boolean = false,
    val banUsers: Boolean = false,
    val inviteUsers: Boolean = false,
    val pinMessages: Boolean = false
)

data class GroupAdmin(val uid: String, val perms: GroupPerms, val title: String = "")

/** صلاحيات الأعضاء العاديين (الافتراضية للمجموعة) */
data class GroupSettings(
    val sendMessages: Boolean = true,
    val inviteUsers: Boolean = false,
    val pinMessages: Boolean = false,
    val changeInfo: Boolean = false,
    /** الوضع البطيء: أقل فاصل (بالثواني) بين رسالتين للعضو العادي، 0 = مطفي */
    val slowMode: Int = 0,
    /** مشاركة المجموعة: أي شخص يلگاها بالبحث (باليوزر) أو من رابط المشاركة ينضم الها بدون دعوة */
    val publicJoin: Boolean = false
)

/** معاينة مجموعة (من نتائج البحث باليوزر) قبل الانضمام الها */
data class GroupPreview(
    val id: String, val name: String, val handle: String, val bio: String,
    val avatar: String, val publicJoin: Boolean
)

/** تقييد (كتم) عضو: until = وقت انتهاء التقييد بالميلي ثانية، 0 = دائم */
data class GroupRestriction(val uid: String, val until: Long, val by: String)

data class GroupPin(val id: String, val from: String, val text: String)

// key = مفتاح العرض الثابت (يطابق id غالباً). يمنع إعادة انيميشن الرسالة إذا رجعت من السيرفر بمعرّف جديد
data class GroupMsg(
    val id: String, val from: String, val text: String, val createdAt: Long, val key: String = id,
    /** رسالة صوتية: mediaType = "audio" + معرّف الملف (groupMedia/<gid>/<mediaId>) + المدة بالثواني + الموجة */
    val mediaType: String = "", val mediaId: String = "", val mediaDur: Int = 0, val wave: String = "",
    /** صور/فيديو/ملفات بالمجموعة: صورة مصغّرة + الأبعاد + اسم الملف وحجمه */
    val thumb: String = "", val mediaW: Int = 0, val mediaH: Int = 0,
    val fileName: String = "", val fileSize: Long = 0L,
    /** رد على رسالة: معرّفها + نص مقتبس + صاحبها (نفس المحادثة الخاصة) */
    val replyId: String = "", val replyText: String = "", val replyFrom: String = "",
    /** التفاعلات: uid -> ايموجي (ضغطتين = ❤️، ضغطة مطولة = اختيار ايموجي) */
    val reactions: Map<String, String> = emptyMap()
)

/** كل بيانات المجموعة بنفس اللحظة (للأعضاء فقط) */
data class GroupFull(
    val item: GroupItem,
    val members: List<String>,
    val admins: Map<String, GroupAdmin>,
    val settings: GroupSettings,
    val restricted: Map<String, GroupRestriction>,
    val banned: List<String>,
    val pinned: GroupPin?
)

/** يحسب شنو يقدر يسوي شخص معين داخل المجموعة (نفس منطق قواعد قاعدة البيانات) */
class GroupAccess(val g: GroupFull, val me: String) {
    val isOwner = g.item.owner == me
    private val admin = g.admins[me]
    val isAdmin = isOwner || admin != null
    private fun perm(f: (GroupPerms) -> Boolean) = isOwner || (admin != null && f(admin.perms))

    val canChangeInfo = perm { it.changeInfo } || g.settings.changeInfo
    val canDelete = perm { it.deleteMessages }
    val canBan = perm { it.banUsers }
    val canInvite = perm { it.inviteUsers } || g.settings.inviteUsers
    val canPin = perm { it.pinMessages } || g.settings.pinMessages

    /** هل أنا مقيّد (كتم) حالياً */
    val isRestricted: Boolean = g.restricted[me]?.let { it.until == 0L || it.until > System.currentTimeMillis() } == true
    val canSend = !isRestricted && (isAdmin || g.settings.sendMessages)

    /** هل أقدر أطبّق إجراءات الإدارة (تقييد/طرد/حظر) على هذا الشخص */
    fun canManage(target: String): Boolean =
        target != me && target != g.item.owner && (isOwner || (canBan && target !in g.admins))
}

/** عنصر بقائمة المحادثات: الطرف الثاني + آخر رسالة */
/** [req] = طلب رسالة بانتظار موافقتي (الطرف الثاني راسلني أول مرة وأنا مشغّل "طلبات الرسائل") */
data class ChatItem(val uid: String, val lastText: String, val lastAt: Long, val unread: Int = 0, val req: Boolean = false)
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
    /** التفاعلات: uid -> ايموجي (ضغطتين = ❤️، ضغطة مطولة = اختيار ايموجي) */
    val reactions: Map<String, String> = emptyMap(),
    /** الرد: معرّف الرسالة الأصلية + نص مختصر + صاحبها */
    val replyId: String = "",
    val replyText: String = "",
    val replyFrom: String = "",
    /** رسالة وسائط: "image" أو "video" (فاضي = رسالة نص عادية) */
    val mediaType: String = "",
    /** معرّف الملف بمسار media/<chatId>/<mediaId> (يتنزّل عند الضغط فقط) */
    val mediaId: String = "",
    /** صورة مصغّرة صغيرة (base64) تظهر بالفقاعة مباشرة */
    val thumb: String = "",
    val mediaW: Int = 0,
    val mediaH: Int = 0,
    /** مدة الفيديو أو الرسالة الصوتية بالثواني */
    val mediaDur: Int = 0,
    /** موجة الرسالة الصوتية: نص أرقام 1..9 (ارتفاع كل عمود) */
    val wave: String = "",
    /** رسالة ملف (mediaType = "file"): اسم الملف وحجمه بالبايت */
    val fileName: String = "",
    val fileSize: Long = 0L
)

/**
 * معاينة بديلة بقائمة المحادثات عندي فقط: إذا حذفت (من عندي) آخر رسالة بالمحادثة.
 * تنطبق فقط إذا آخر نص بالمحادثة ما زال [forText] ولا وصلت رسالة جديدة بعد [at].
 */
data class HiddenPreview(val forText: String, val text: String, val at: Long)

/** حالة الاتصال كما هي مخزنة بالسيرفر (الحساب النهائي "متصل؟" يصير بالواجهة مع فحص التقادم) */
data class Presence(val online: Boolean = false, val lastSeen: Long = 0L, val hideLastSeen: Boolean = false)

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
    /** نهاية الجزء المحدد من الأغنية بالثواني (0 = بداية + مدة القصة) */
    val musicEnd: Int = 0,
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

/** حدث بنشاط ملفي: زيارة أو إعجاب بالقصة */
data class ActivityEvent(val uid: String, val type: String, val at: Long)

/** علاقتي بشخص: لا شي / أنا أتابعه فقط / هو يتابعني فقط / أصدقاء (متابعة متبادلة) */
enum class Relation { NONE, SENT, RECEIVED, FRIENDS }

data class Profile(
    val name: String = "",
    val username: String = "",
    val bio: String = "",
    val avatar: String = "",
    val banner: String = "",
    /** رقم نسخة GIF البانر المخزّن بـ RTDB (0 = ما فيه GIF، البانر صورة ثابتة) */
    val bannerGifV: Long = 0L,
    /** موضع القص: أي جزء من البانر يظهر. -1..1 (0 = المنتصف) لكل محور */
    val bannerFx: Float = 0f,
    val bannerFy: Float = 0f,
    val story: Story? = null,
    val status: Status? = null,
    /** شكل فقاعات الرسائل المختار (يطبق على كل محادثات المستخدم ويظهر للطرفين) */
    val bubbleStyle: String = "glass",
    /** تصميم الفقاعة المخصّصة (JSON) — يُستعمل فقط إذا bubbleStyle == "custom" */
    val customBubble: String = "",
    /** معرّف الرمز المتحرك الي يظهر جنب الاسم عند الكل (فاضي = بدون رمز) */
    val badge: String = "",
    /** مقدمة الدردشة: رسالة ترحيب تظهر للي يفتح محادثة جديدة معي (فقط إذا introOn) */
    val introOn: Boolean = false,
    val intro: String = "",
    /** ملصق المقدمة (نفس معرّفات رموز الاسم، فاضي = 👋) */
    val introSticker: String = "",
    /** true إذا هذا المستخدم حاظرني (ما أقدر أشوف ملفه) */
    val restricted: Boolean = false,
    /** true إذا الحساب معطّل أو محذوف (ما يظهر ملفه ولا يمكن مراسلته) */
    val unavailable: Boolean = false
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

    /** التفاعلات: reactions/<uid> = ايموجي (ويقرأ القلوب القديمة hearts/<uid> = true كـ ❤️) */
    private fun DataSnapshot.readReactions(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        child("hearts").children.forEach { c -> if (c.value == true) c.key?.let { out[it] = "❤️" } }
        child("reactions").children.forEach { c ->
            val k = c.key; val v = c.value as? String
            if (k != null && !v.isNullOrEmpty()) out[k] = v
        }
        return out
    }

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
        // الحسابات المعطّلة والمحذوفة تختفي من قائمة المستخدمين
        if (d.getBoolean("disabled") == true || d.getBoolean("deleted") == true) return@mapNotNull null
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

    /**
     * آخر 50 رسالة، الأحدث أولاً. نفس حل المجموعات: ChildEventListener حتى كل رسالة تنضاف مرة وحدة
     * وتتحدث بمكانها (تأكيد السيرفر، تعديل، حذف، تثبيت) بدل لقطة كاملة جديدة كل مرة،
     * والترتيب بالمفتاح لأنه ثابت (createdAt يتغير لما السيرفر يأكد).
     */
    fun messagesFlow(me: String, other: String): Flow<List<Msg>> {
        if (me.isBlank()) return flowOf(emptyList())
        return callbackFlow {
            val q = rtdb.getReference("chats/${chatId(me, other)}/messages").orderByKey().limitToLast(50)
            val map = java.util.TreeMap<String, Msg>(java.util.Collections.reverseOrder())
            var ready = false
            fun push() { if (ready) trySend(map.values.toList()) }
            val child = object : ChildEventListener {
                override fun onChildAdded(s: DataSnapshot, prev: String?) { map[s.key ?: return] = s.toMsg(); push() }
                override fun onChildChanged(s: DataSnapshot, prev: String?) { map[s.key ?: return] = s.toMsg(); push() }
                override fun onChildRemoved(s: DataSnapshot) { s.key?.let { map.remove(it) }; push() }
                override fun onChildMoved(s: DataSnapshot, prev: String?) {}
                override fun onCancelled(e: DatabaseError) {}
            }
            val first = object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) { ready = true; trySend(map.values.toList()) }
                override fun onCancelled(e: DatabaseError) { ready = true; trySend(map.values.toList()) }
            }
            q.addChildEventListener(child)
            q.addListenerForSingleValueEvent(first)
            awaitClose { q.removeEventListener(child); q.removeEventListener(first) }
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
        reactions = readReactions(),
        replyId = str("replyId"),
        replyText = str("replyText"),
        replyFrom = str("replyFrom"),
        mediaType = str("mediaType"),
        mediaId = str("mediaId"),
        thumb = str("thumb"),
        mediaW = num("mediaW").toInt(),
        mediaH = num("mediaH").toInt(),
        mediaDur = num("mediaDur").toInt(),
        wave = str("wave"),
        fileName = str("fileName"),
        fileSize = num("fileSize")
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
    fun deleteMessage(
        me: String, other: String, id: String, isLast: Boolean,
        onFail: (Exception) -> Unit = {}, mediaId: String = ""
    ) {
        msgRef(me, other, id).updateChildren(
            mapOf<String, Any>("deleted" to true, "text" to "", "pinned" to false, "edited" to false)
        ).addOnFailureListener(onFail)
        // حذف للجميع يمسح ملف الصورة/الفيديو نفسه من السيرفر
        if (mediaId.isNotEmpty()) mediaRef(me, other, mediaId).removeValue()
        if (isLast) updateLastText(me, other, "رسالة مَحذوفة")
    }

    fun editMessage(me: String, other: String, id: String, newText: String, isLast: Boolean, onFail: (Exception) -> Unit = {}) {
        msgRef(me, other, id).updateChildren(mapOf<String, Any>("text" to newText, "edited" to true))
            .addOnFailureListener(onFail)
        if (isLast) updateLastText(me, other, newText)
    }

    /** تفاعل بايموجي على رسالة (لأي طرف). emoji=null يشيل تفاعلي */
    fun setMessageReaction(me: String, other: String, id: String, emoji: String?, onFail: (Exception) -> Unit = {}) {
        msgRef(me, other, id).child("reactions").child(me).setValue(emoji)
            .addOnFailureListener(onFail)
    }

    fun setMessagePinned(me: String, other: String, id: String, pinned: Boolean, onFail: (Exception) -> Unit = {}) {
        msgRef(me, other, id).updateChildren(
            if (pinned) mapOf<String, Any>("pinned" to true, "pinnedAt" to ServerValue.TIMESTAMP, "pinnedBy" to me)
            else mapOf<String, Any>("pinned" to false)
        ).addOnFailureListener(onFail)
    }


    // ===================== المجموعات =====================
    // groups/{id}/meta        : الاسم، المعرّف، النبذة، الصورة، المالك، تاريخ الإنشاء
    // groups/{id}/members/{uid} = true
    // userGroups/{uid}/{id}   = true   (قائمة مجموعات كل شخص)
    // groupHandles/{handle}   = id     (حتى يكون معرّف المجموعة فريد)

    /** ينشئ مجموعة حقيقية: أنا المالك + الأصدقاء المدعوين أعضاء. يرجع id المجموعة أو يرمي استثناء برسالة عربية */
    suspend fun createGroup(
        name: String, handle: String, bio: String, avatarB64: String, invited: Set<String>,
        avatarGif: ByteArray? = null,
        bannerB64: String = "", bannerGif: ByteArray? = null,
        bannerFx: Float = 0f, bannerFy: Float = 0f
    ): String {
        val me = auth.currentUser?.uid ?: throw IllegalStateException("لازم تسجّل دخول أول")
        val cleanName = name.trim()
        if (cleanName.isEmpty()) throw IllegalArgumentException("اكتب اسم المجموعة")
        val h = handle.trim().lowercase()
        if (h.isNotEmpty()) {
            if (!isValidUsername(h)) throw IllegalArgumentException("معرّف المجموعة غير صالح")
            val taken = withTimeout(15_000) { rtdb.getReference("groupHandles/$h").get().await() }
            if (taken.exists()) throw IllegalArgumentException("معرّف المجموعة محجوز، جرّب غيره")
        }
        val id = rtdb.getReference("groups").push().key ?: throw IllegalStateException("تعذّر إنشاء المجموعة")
        val members = (invited + me).filter { it.isNotBlank() }
        // الخطوة 1: بيانات المجموعة (القواعد تتحقق إني أنا المالك)
        val meta = hashMapOf<String, Any>(
            "name" to cleanName,
            "handle" to h,
            "bio" to bio.trim().take(150),
            "avatar" to avatarB64,
            "owner" to me,
            "createdAt" to ServerValue.TIMESTAMP
        )
        if (bannerB64.isNotEmpty()) {
            meta["banner"] = bannerB64
            meta["bannerFx"] = bannerFx.coerceIn(-1f, 1f).toDouble()
            meta["bannerFy"] = bannerFy.coerceIn(-1f, 1f).toDouble()
        }
        withTimeout(20_000) { rtdb.getReference("groups/$id/meta").setValue(meta).await() }
        // الخطوة 2: الأعضاء + قوائم مجموعاتهم + حجز المعرّف. إذا فشلت نمسح المجموعة حتى ما تنبقى نصف منشأة
        try {
            val updates = hashMapOf<String, Any>()
            members.forEach { uid ->
                updates["groups/$id/members/$uid"] = true
                updates["userGroups/$uid/$id"] = true
            }
            if (h.isNotEmpty()) updates["groupHandles/$h"] = id
            updates["groups/$id/settings"] = hashMapOf<String, Any>(
                "sendMessages" to true, "inviteUsers" to false,
                "pinMessages" to false, "changeInfo" to false, "slowMode" to 0,
                "publicJoin" to false
            )
            withTimeout(20_000) { rtdb.reference.updateChildren(updates).await() }
        } catch (e: Exception) {
            runCatching { rtdb.getReference("groups/$id/meta").removeValue() }
            throw e
        }
        // الخطوة 3: ملفات GIF (تحتاج المجموعة موجودة حتى القواعد تتحقق من المالك). إذا فشلت تبقى الصورة الثابتة
        val gifMeta = hashMapOf<String, Any>()
        if (avatarGif != null) runCatching { gifMeta["avatarGifV"] = uploadGroupGif(id, "avatar", avatarGif) }
        if (bannerGif != null && bannerB64.isNotEmpty()) runCatching { gifMeta["bannerGifV"] = uploadGroupGif(id, "banner", bannerGif) }
        if (gifMeta.isNotEmpty()) runCatching { gWrite(rtdb.getReference("groups/$id/meta").updateChildren(gifMeta)) }
        return id
    }

    // ---------- GIF المجموعة (صورة المجموعة + البانر): يتخزن بـ RTDB بالمسار groupGifs/{gid}/{avatar|banner} ----------

    /** يرفع GIF لمجموعة ويرجع رقم نسخته. [slot] = "avatar" أو "banner" */
    private suspend fun uploadGroupGif(gid: String, slot: String, bytes: ByteArray): Long {
        val v = System.currentTimeMillis()
        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        try {
            withTimeout(120_000) {
                rtdb.getReference("groupGifs/$gid/$slot").setValue(mapOf("v" to v, "data" to b64)).await()
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw IllegalStateException("انتهت المهلة أثناء رفع الـ GIF، تأكد من الاتصال بالإنترنت")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.message?.contains("ermission", ignoreCase = true) == true)
                throw IllegalStateException("ما عندك صلاحية لهذا الإجراء")
            throw e
        }
        return v
    }

    /**
     * ينزّل GIF [slot] لمجموعة [gid] مرة وحدة لكل نسخة [v] ويخزنه بالكاش.
     * يرجع null إذا ما انقرى (لا إنترنت، ما عندي صلاحية...) فتبقى الصورة الثابتة ظاهرة.
     */
    suspend fun groupGifFile(cacheDir: java.io.File, gid: String, slot: String, v: Long): java.io.File? {
        val dir = java.io.File(cacheDir, "gif_groups").apply { mkdirs() }
        val prefix = "g_${gid}_${slot}_"
        val cached = java.io.File(dir, "$prefix$v.gif")
        if (cached.exists() && cached.length() > 0) return cached
        val snap = withTimeout(120_000) { rtdb.getReference("groupGifs/$gid/$slot").get().await() }
        val data = snap.child("data").getValue(String::class.java) ?: return null
        val real = snap.child("v").getValue(Long::class.java) ?: v
        val bytes = android.util.Base64.decode(data, android.util.Base64.NO_WRAP)
        // نحذف النسخ القديمة لنفس الخانة
        dir.listFiles { f -> f.name.startsWith(prefix) }?.forEach { it.delete() }
        val out = java.io.File(dir, "$prefix$real.gif")
        val tmp = java.io.File(dir, out.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(out)) { tmp.delete(); return null }
        com.chatapp.messenger.ui.GifUtil.trimCache(dir)
        return out
    }

    /** مجموعة وحدة (null إذا انحذفت أو انطردت منها) */
    private fun groupFlow(id: String): Flow<GroupItem?> = groupFullFlow(id).map { it?.item }

    private fun parseGroupFull(id: String, s: DataSnapshot): GroupFull? {
        val m = s.child("meta")
        if (!m.exists()) return null
        val members = s.child("members").children.mapNotNull { it.key }
        val admins = s.child("admins").children.mapNotNull { a ->
            val uid = a.key ?: return@mapNotNull null
            uid to GroupAdmin(
                uid,
                GroupPerms(
                    changeInfo = a.flag("changeInfo"),
                    deleteMessages = a.flag("deleteMessages"),
                    banUsers = a.flag("banUsers"),
                    inviteUsers = a.flag("inviteUsers"),
                    pinMessages = a.flag("pinMessages")
                ),
                a.str("title")
            )
        }.toMap()
        val st = s.child("settings")
        val settings = GroupSettings(
            sendMessages = st.child("sendMessages").value != false,
            inviteUsers = st.flag("inviteUsers"),
            pinMessages = st.flag("pinMessages"),
            changeInfo = st.flag("changeInfo"),
            slowMode = st.num("slowMode").toInt(),
            publicJoin = st.flag("publicJoin")
        )
        val restricted = s.child("restricted").children.mapNotNull { r ->
            val uid = r.key ?: return@mapNotNull null
            uid to GroupRestriction(uid, r.num("until"), r.str("by"))
        }.toMap()
        val banned = s.child("banned").children.mapNotNull { it.key }
        val pin = s.child("pinned").let { p ->
            if (p.exists()) GroupPin(p.str("id"), p.str("from"), p.str("text")) else null
        }
        val item = GroupItem(
            id = id,
            name = m.str("name"),
            handle = m.str("handle"),
            bio = m.str("bio"),
            avatar = m.str("avatar"),
            owner = m.str("owner"),
            memberCount = members.size,
            createdAt = m.num("createdAt"),
            banner = m.str("banner"),
            bannerFx = ((m.child("bannerFx").value as? Number)?.toFloat() ?: 0f).coerceIn(-1f, 1f),
            bannerFy = ((m.child("bannerFy").value as? Number)?.toFloat() ?: 0f).coerceIn(-1f, 1f),
            bannerGifV = m.num("bannerGifV"),
            avatarGifV = m.num("avatarGifV")
        )
        return GroupFull(item, members, admins, settings, restricted, banned, pin)
    }

    /** كل بيانات المجموعة مباشرة. يرجع null إذا انحذفت أو ما عاد عندي صلاحية قراءتها (طردوني) */
    fun groupFullFlow(id: String): Flow<GroupFull?> = callbackFlow {
        val ref = rtdb.getReference("groups/$id")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(parseGroupFull(id, s)) }
            override fun onCancelled(e: DatabaseError) { trySend(null) }
        }
        ref.addValueEventListener(l)
        awaitClose { ref.removeEventListener(l) }
    }

    private fun DataSnapshot.toGroupMsg(): GroupMsg? {
        val id = key ?: return null
        // الرسالة الي توها انرسلت: الـ SDK يعطي وقت تقديري لين يأكد السيرفر (نفس المحادثة الخاصة)
        return GroupMsg(
            id, str("from"), str("text"), num("createdAt").takeIf { it > 0L } ?: System.currentTimeMillis(),
            mediaType = str("mediaType"), mediaId = str("mediaId"),
            mediaDur = num("mediaDur").toInt(), wave = str("wave"),
            thumb = str("thumb"), mediaW = num("mediaW").toInt(), mediaH = num("mediaH").toInt(),
            fileName = str("fileName"), fileSize = num("fileSize"),
            replyId = str("replyId"), replyText = str("replyText"), replyFrom = str("replyFrom"),
            reactions = readReactions()
        )
    }

    /** تفاعل بايموجي على رسالة مجموعة. emoji=null يشيل تفاعلي */
    fun setGroupMessageReaction(gid: String, mid: String, emoji: String?, onFail: (Exception) -> Unit = {}) {
        val me = auth.currentUser?.uid ?: return
        rtdb.getReference("groupChats/$gid/messages/$mid/reactions/$me")
            .setValue(emoji)
            .addOnFailureListener(onFail)
    }

    /** يضيف حقول الرد لرسالة مجموعة (إذا كانت ردّاً) */
    private fun putGroupReply(msg: MutableMap<String, Any>, replyId: String, replyText: String, replyFrom: String) {
        if (replyId.isEmpty()) return
        msg["replyId"] = replyId.take(100)
        msg["replyText"] = replyText.take(100)
        msg["replyFrom"] = replyFrom.take(100)
    }

    /**
     * آخر 300 رسالة بالمجموعة (الأقدم أولاً).
     * نستعمل ChildEventListener حتى كل رسالة تنضاف مرة وحدة بمفتاحها وتتحدث بمكانها (بدل ما نستلم
     * لقطة كاملة جديدة كل مرة، وهذا الي كان يخلي رسالتي تختفي وترجع بعد تأكيد السيرفر).
     * الترتيب بالمفتاح (push key) لأنه ثابت ومرتب زمنياً، بعكس createdAt الي يتغير لما السيرفر يأكد.
     */
    fun groupMessagesFlow(id: String): Flow<List<GroupMsg>> = callbackFlow {
        val q = rtdb.getReference("groupChats/$id/messages").orderByKey().limitToLast(300)
        val map = java.util.TreeMap<String, GroupMsg>()
        var ready = false
        fun push() { if (ready) trySend(map.values.toList()) }
        val child = object : ChildEventListener {
            override fun onChildAdded(s: DataSnapshot, prev: String?) { s.toGroupMsg()?.let { map[it.id] = it }; push() }
            override fun onChildChanged(s: DataSnapshot, prev: String?) { s.toGroupMsg()?.let { map[it.id] = it }; push() }
            override fun onChildRemoved(s: DataSnapshot) { s.key?.let { map.remove(it) }; push() }
            override fun onChildMoved(s: DataSnapshot, prev: String?) {}
            override fun onCancelled(e: DatabaseError) {}
        }
        // أحداث الأولاد الأولية توصل قبل هذا الحدث، فنرسل القائمة كاملة مرة وحدة
        val first = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { ready = true; trySend(map.values.toList()) }
            override fun onCancelled(e: DatabaseError) { ready = true; trySend(map.values.toList()) }
        }
        q.addChildEventListener(child)
        q.addListenerForSingleValueEvent(first)
        awaitClose { q.removeEventListener(child); q.removeEventListener(first) }
    }

    /** آخر رسالة بالمجموعة (للمعاينة بقائمة المجموعات) */
    fun groupLastMessageFlow(id: String): Flow<GroupMsg?> =
        rtFlow(rtdb.getReference("groupChats/$id/messages").orderByKey().limitToLast(1)) { snap ->
            snap.children.mapNotNull { it.toGroupMsg() }.lastOrNull()
        }

    /** المجموعات الي أنا عضو فيها، الأحدث أولاً (تتحدث فوراً) */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun groupsFlow(me: String): Flow<List<GroupItem>> {
        if (me.isBlank()) return flowOf(emptyList())
        return rtFlow(rtdb.getReference("userGroups/$me")) { snap -> snap.children.mapNotNull { it.key } }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids.isEmpty()) flowOf(emptyList())
                else combine(ids.map { groupFlow(it) }) { arr ->
                    arr.filterNotNull().sortedByDescending { it.createdAt }
                }
            }
    }

    // ---------- عمليات المجموعة (القواعد بقاعدة البيانات هي الي تحمي الصلاحيات فعلياً) ----------

    private fun meUid(): String = auth.currentUser?.uid ?: throw IllegalStateException("لازم تسجّل دخول أول")

    /** ينفّذ كتابة ويحوّل أخطاء Firebase لرسائل عربية مفهومة */
    private suspend fun gWrite(task: com.google.android.gms.tasks.Task<Void>) {
        try {
            withTimeout(20_000) { task.await() }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw IllegalStateException("انتهت المهلة، تأكد من الاتصال بالإنترنت")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.message?.contains("ermission", ignoreCase = true) == true)
                throw IllegalStateException("ما عندك صلاحية لهذا الإجراء")
            throw e
        }
    }

    /** يرسل رسالة للمجموعة (بدون انتظار، إذا فشلت تنادى onFail) */
    fun sendGroupMessage(
        gid: String, text: String, onFail: (Exception) -> Unit,
        replyId: String = "", replyText: String = "", replyFrom: String = ""
    ) {
        val me = auth.currentUser?.uid ?: return
        val t = text.trim().take(2000)
        if (t.isEmpty()) return
        val key = rtdb.getReference("groupChats/$gid/messages").push().key ?: return
        val msg = hashMapOf<String, Any>(
            "from" to me, "text" to t, "createdAt" to ServerValue.TIMESTAMP
        )
        putGroupReply(msg, replyId, replyText, replyFrom)
        val updates = hashMapOf<String, Any>(
            "groupChats/$gid/messages/$key" to msg,
            "groupChats/$gid/lastSent/$me" to ServerValue.TIMESTAMP
        )
        rtdb.reference.updateChildren(updates)
            .addOnSuccessListener { Push.notifyGroup(gid, key) }   // إشعار لباقي الأعضاء (حتى لو تطبيقهم مطفي)
            .addOnFailureListener { e -> onFail(e) }
    }

    /** رسالة صوتية بالمجموعة (بعد ما انرفع الملف بنوع "audio" لـ groupMedia/<gid>) */
    fun sendGroupVoice(
        gid: String, mediaId: String, durSec: Int, wave: String, onFail: (Exception) -> Unit,
        replyId: String = "", replyText: String = "", replyFrom: String = ""
    ) {
        val me = auth.currentUser?.uid ?: return
        val key = rtdb.getReference("groupChats/$gid/messages").push().key ?: return
        val msg = hashMapOf<String, Any>(
            "from" to me, "text" to "🎤 رسالة صوتية", "createdAt" to ServerValue.TIMESTAMP,
            "mediaType" to "audio", "mediaId" to mediaId, "mediaDur" to durSec, "wave" to wave
        )
        putGroupReply(msg, replyId, replyText, replyFrom)
        val updates = hashMapOf<String, Any>(
            "groupChats/$gid/messages/$key" to msg,
            "groupChats/$gid/lastSent/$me" to ServerValue.TIMESTAMP
        )
        rtdb.reference.updateChildren(updates)
            .addOnSuccessListener { Push.notifyGroup(gid, key) }   // إشعار لباقي الأعضاء (حتى لو تطبيقهم مطفي)
            .addOnFailureListener { e -> onFail(e) }
    }

    /** صورة/فيديو بالمجموعة (بعد ما انرفع الملف لـ groupMedia/<gid>) */
    fun sendGroupMedia(
        gid: String, type: String, mediaId: String, thumb: String, w: Int, h: Int, durSec: Int,
        onFail: (Exception) -> Unit,
        replyId: String = "", replyText: String = "", replyFrom: String = ""
    ) {
        val me = auth.currentUser?.uid ?: return
        val key = rtdb.getReference("groupChats/$gid/messages").push().key ?: return
        val msg = hashMapOf<String, Any>(
            "from" to me, "text" to (if (type == "video") "🎥 فيديو" else "📷 صورة"),
            "createdAt" to ServerValue.TIMESTAMP,
            "mediaType" to type, "mediaId" to mediaId, "thumb" to thumb, "mediaW" to w, "mediaH" to h
        )
        if (type == "video") msg["mediaDur"] = durSec
        putGroupReply(msg, replyId, replyText, replyFrom)
        val updates = hashMapOf<String, Any>(
            "groupChats/$gid/messages/$key" to msg,
            "groupChats/$gid/lastSent/$me" to ServerValue.TIMESTAMP
        )
        rtdb.reference.updateChildren(updates)
            .addOnSuccessListener { Push.notifyGroup(gid, key) }   // إشعار لباقي الأعضاء (حتى لو تطبيقهم مطفي)
            .addOnFailureListener { e -> onFail(e) }
    }

    /** ملف بالمجموعة (بعد ما انرفع بنوع "file") */
    fun sendGroupFile(
        gid: String, mediaId: String, name: String, size: Long, onFail: (Exception) -> Unit,
        replyId: String = "", replyText: String = "", replyFrom: String = ""
    ) {
        val me = auth.currentUser?.uid ?: return
        val key = rtdb.getReference("groupChats/$gid/messages").push().key ?: return
        val msg = hashMapOf<String, Any>(
            "from" to me, "text" to "📎 ملف", "createdAt" to ServerValue.TIMESTAMP,
            "mediaType" to "file", "mediaId" to mediaId,
            "fileName" to name.take(120), "fileSize" to size
        )
        putGroupReply(msg, replyId, replyText, replyFrom)
        val updates = hashMapOf<String, Any>(
            "groupChats/$gid/messages/$key" to msg,
            "groupChats/$gid/lastSent/$me" to ServerValue.TIMESTAMP
        )
        rtdb.reference.updateChildren(updates)
            .addOnSuccessListener { Push.notifyGroup(gid, key) }   // إشعار لباقي الأعضاء (حتى لو تطبيقهم مطفي)
            .addOnFailureListener { e -> onFail(e) }
    }

    suspend fun deleteGroupMessage(gid: String, mid: String, mediaId: String = "") {
        gWrite(rtdb.getReference("groupChats/$gid/messages/$mid").removeValue())
        // الرسالة الصوتية: نحذف ملفها حتى ما يبقى محفوظ بقاعدة البيانات
        if (mediaId.isNotEmpty()) runCatching { rtdb.getReference("groupMedia/$gid/$mediaId").removeValue() }
    }

    /** تثبيت رسالة (أو إلغاء التثبيت إذا msg = null) */
    suspend fun pinGroupMessage(gid: String, msg: GroupMsg?) {
        val ref = rtdb.getReference("groups/$gid/pinned")
        if (msg == null) gWrite(ref.removeValue())
        else gWrite(ref.setValue(hashMapOf<String, Any>("id" to msg.id, "from" to msg.from, "text" to msg.text.take(200))))
    }

    /**
     * تعديل اسم/نبذة/صورة/بانر المجموعة. (avatar / banner = null يعني بدون تغيير)
     * [avatarGif] / [bannerGif]: ملف GIF جديد أو حذفه أو بدون تغيير. [bannerFx]/[bannerFy] = موضع قص البانر.
     * إذا تغيّرت الصورة الثابتة بدون GIF، لازم تمرّر GifChange.Remove حتى ينحذف الـ GIF القديم.
     */
    suspend fun updateGroupInfo(
        gid: String, name: String, bio: String, avatar: String?,
        avatarGif: GifChange = GifChange.Keep,
        banner: String? = null, bannerGif: GifChange = GifChange.Keep,
        bannerFx: Float? = null, bannerFy: Float? = null
    ) {
        val n = name.trim()
        if (n.isEmpty()) throw IllegalArgumentException("اكتب اسم المجموعة")
        val m = hashMapOf<String, Any>("name" to n.take(40), "bio" to bio.trim().take(150))
        if (avatar != null) m["avatar"] = avatar
        if (banner != null) m["banner"] = banner
        if (bannerFx != null) m["bannerFx"] = bannerFx.coerceIn(-1f, 1f).toDouble()
        if (bannerFy != null) m["bannerFy"] = bannerFy.coerceIn(-1f, 1f).toDouble()
        // الـ GIF الجديد يرتفع أول، وبعدها نحفظ رقم نسخته مع باقي البيانات
        when (avatarGif) {
            is GifChange.Set -> m["avatarGifV"] = uploadGroupGif(gid, "avatar", avatarGif.bytes)
            GifChange.Remove -> m["avatarGifV"] = 0L
            GifChange.Keep -> {}
        }
        when (bannerGif) {
            is GifChange.Set -> m["bannerGifV"] = uploadGroupGif(gid, "banner", bannerGif.bytes)
            GifChange.Remove -> m["bannerGifV"] = 0L
            GifChange.Keep -> {}
        }
        gWrite(rtdb.getReference("groups/$gid/meta").updateChildren(m))
        // نمسح ملفات GIF المحذوفة (بعد نجاح حفظ البيانات)
        if (avatarGif === GifChange.Remove) runCatching { withTimeout(20_000) { rtdb.getReference("groupGifs/$gid/avatar").removeValue().await() } }
        if (bannerGif === GifChange.Remove) runCatching { withTimeout(20_000) { rtdb.getReference("groupGifs/$gid/banner").removeValue().await() } }
    }

    /** صلاحيات الأعضاء العاديين (الإرسال، الدعوة، التثبيت، تغيير المعلومات، الوضع البطيء) */
    suspend fun setGroupSettings(gid: String, st: GroupSettings) =
        gWrite(
            rtdb.getReference("groups/$gid/settings").setValue(
                hashMapOf<String, Any>(
                    "sendMessages" to st.sendMessages,
                    "inviteUsers" to st.inviteUsers,
                    "pinMessages" to st.pinMessages,
                    "changeInfo" to st.changeInfo,
                    "slowMode" to st.slowMode,
                    "publicJoin" to st.publicJoin
                )
            )
        )

    /** يدوّر على مجموعة باليوزر (بدون @). يرجع null إذا ما انوجدت. المعرّف فريد فالبحث بالضبط */
    suspend fun findGroupByHandle(handle: String): GroupPreview? {
        val h = handle.trim().removePrefix("@").lowercase()
        if (!isValidUsername(h)) return null
        val gid = withTimeout(15_000) { rtdb.getReference("groupHandles/$h").get().await() }.value as? String
            ?: return null
        val m = withTimeout(15_000) { rtdb.getReference("groups/$gid/meta").get().await() }
        if (!m.exists()) return null
        val pj = runCatching {
            withTimeout(15_000) { rtdb.getReference("groups/$gid/settings/publicJoin").get().await() }.value == true
        }.getOrDefault(false)
        return GroupPreview(gid, m.str("name"), m.str("handle"), m.str("bio"), m.str("avatar"), pj)
    }

    /** انضمام ذاتي لمجموعة مشاركة (publicJoin). القواعد ترفض إذا المجموعة غير مشاركة أو إذا أنا محظور منها */
    suspend fun joinGroup(gid: String) {
        val me = auth.currentUser?.uid ?: throw IllegalStateException("لازم تسجّل دخول أول")
        val updates = HashMap<String, Any?>()
        updates["groups/$gid/members/$me"] = true
        updates["userGroups/$me/$gid"] = true
        try {
            withTimeout(20_000) { rtdb.reference.updateChildren(updates).await() }
        } catch (e: com.google.firebase.database.DatabaseException) {
            throw Exception("ما تگدر تنضم لهذي المجموعة")
        } catch (e: Exception) {
            if (e.message?.contains("ermission", true) == true) throw Exception("ما تگدر تنضم لهذي المجموعة (خاصة أو محظور منها)")
            throw e
        }
    }

    suspend fun addGroupMembers(gid: String, uids: Collection<String>) {
        if (uids.isEmpty()) return
        val updates = HashMap<String, Any?>()
        uids.forEach { u ->
            updates["groups/$gid/members/$u"] = true
            updates["userGroups/$u/$gid"] = true
        }
        gWrite(rtdb.reference.updateChildren(updates))
    }

    /** طرد عضو (يقدر يرجع إذا انضاف مرة ثانية). ban = true يحظره من الرجوع لين يلغى الحظر */
    suspend fun removeGroupMember(
        gid: String, uid: String, ban: Boolean,
        wasAdmin: Boolean = false, wasRestricted: Boolean = false
    ) {
        val me = meUid()
        val updates = HashMap<String, Any?>()
        updates["groups/$gid/members/$uid"] = null
        updates["userGroups/$uid/$gid"] = null
        if (wasAdmin) updates["groups/$gid/admins/$uid"] = null
        if (wasRestricted) updates["groups/$gid/restricted/$uid"] = null
        if (ban) updates["groups/$gid/banned/$uid"] = hashMapOf<String, Any>("by" to me, "at" to ServerValue.TIMESTAMP)
        gWrite(rtdb.reference.updateChildren(updates))
    }

    suspend fun unbanGroupMember(gid: String, uid: String) =
        gWrite(rtdb.getReference("groups/$gid/banned/$uid").removeValue())

    /** تقييد (كتم) عضو: untilMs = 0 يعني دائم */
    suspend fun restrictGroupMember(gid: String, uid: String, untilMs: Long) =
        gWrite(
            rtdb.getReference("groups/$gid/restricted/$uid").setValue(
                hashMapOf<String, Any>("until" to untilMs, "by" to meUid(), "at" to ServerValue.TIMESTAMP)
            )
        )

    suspend fun unrestrictGroupMember(gid: String, uid: String) =
        gWrite(rtdb.getReference("groups/$gid/restricted/$uid").removeValue())

    suspend fun setGroupAdmin(gid: String, uid: String, perms: GroupPerms, title: String) =
        gWrite(
            rtdb.getReference("groups/$gid/admins/$uid").setValue(
                hashMapOf<String, Any>(
                    "changeInfo" to perms.changeInfo,
                    "deleteMessages" to perms.deleteMessages,
                    "banUsers" to perms.banUsers,
                    "inviteUsers" to perms.inviteUsers,
                    "pinMessages" to perms.pinMessages,
                    "title" to title.trim().take(16)
                )
            )
        )

    suspend fun removeGroupAdmin(gid: String, uid: String) =
        gWrite(rtdb.getReference("groups/$gid/admins/$uid").removeValue())

    /** مغادرة المجموعة (المالك ما يقدر يغادر: لازم يحذفها) */
    suspend fun leaveGroup(gid: String, wasAdmin: Boolean) {
        val me = meUid()
        val updates = HashMap<String, Any?>()
        updates["groups/$gid/members/$me"] = null
        updates["userGroups/$me/$gid"] = null
        if (wasAdmin) updates["groups/$gid/admins/$me"] = null
        gWrite(rtdb.reference.updateChildren(updates))
    }

    /** حذف المجموعة نهائياً (للمالك فقط): البيانات والرسائل وقوائم الأعضاء والمعرّف */
    suspend fun deleteGroup(g: GroupFull) {
        val gid = g.item.id
        runCatching { withTimeout(10_000) { rtdb.getReference("groupMedia/$gid").removeValue().await() } }
        val updates = HashMap<String, Any?>()
        updates["groups/$gid"] = null
        updates["groupChats/$gid"] = null
        updates["groupGifs/$gid"] = null
        g.members.forEach { updates["userGroups/$it/$gid"] = null }
        if (g.item.handle.isNotBlank()) updates["groupHandles/${g.item.handle}"] = null
        gWrite(rtdb.reference.updateChildren(updates))
    }

    /** أشخاص عندي معهم محادثة سابقة (تنملأ من chatsFlow) حتى ما نسأل السيرفر عن "أول تواصل" مع كل رسالة */
    private val knownChats: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet())

    /** المحادثات الي أنا طرف فيها (تظهر بعد أول رسالة)، الأحدث أولاً */
    fun chatsFlow(me: String): Flow<List<ChatItem>> {
        if (me.isBlank()) return flowOf(emptyList())
        return rtFlow(rtdb.getReference("userChats/$me")) { snap ->
            snap.children.mapNotNull { c ->
                val other = c.key ?: return@mapNotNull null
                // عقدة بدون lastAt = ما صار فيها رسائل بعد (مثلاً فقط تصفير عداد)
                if (!c.hasChild("lastAt")) return@mapNotNull null
                knownChats.add(other)
                ChatItem(other, c.str("lastText"), c.num("lastAt"), c.num("unread").toInt(), c.flag("req"))
            }.sortedByDescending { it.lastAt }
        }
    }

    // ---------- مؤشر الكتابة ----------
    // typing/<chatId>/<uid> للمحادثة الخاصة، groupTyping/<gid>/<uid> للمجموعات. القيمة = وقت آخر نبضة كتابة.
    private val typingSentAt = HashMap<String, Long>()

    /** أنا أكتب الحين (typing=true: نبضة كل 2.5 ثانية بالأكثر) أو وقفت (false). ينمسح تلقائياً إذا انقطع النت */
    fun setTyping(path: String, typing: Boolean) {
        val me = auth.currentUser?.uid ?: return
        val ref = rtdb.getReference("$path/$me")
        val now = System.currentTimeMillis()
        if (typing) {
            if (now - (typingSentAt[path] ?: 0L) < 2500L) return
            typingSentAt[path] = now
            runCatching { ref.onDisconnect().removeValue() }
            ref.setValue(ServerValue.TIMESTAMP)
        } else {
            if (typingSentAt.remove(path) == null) return
            ref.removeValue()
        }
    }

    /** منو يكتب الحين بهذا المسار (بدوني). إذا وقفت النبضات أكثر من 7 ثواني يختفي تلقائياً */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun typingFlow(path: String, me: String): Flow<Set<String>> =
        rtFlow(rtdb.getReference(path)) { snap -> snap.children.mapNotNull { it.key }.filter { it != me }.toSet() }
            .transformLatest { set ->
                emit(set)
                if (set.isNotEmpty()) {
                    delay(7000)
                    emit(emptySet())
                }
            }

    /** طابور الإرسال: يحافظ على ترتيب الرسائل حتى لو الرسالة الأولى احتاجت قراءة إعداد الطرف الثاني */
    private val sendMutex = kotlinx.coroutines.sync.Mutex()

    /** هل الطرف الثاني مشغّل "طلبات الرسائل"؟ (إعداد عام يقدر يقراه أي مستخدم مفعّل) */
    private suspend fun requiresRequest(other: String): Boolean = try {
        withTimeoutOrNull(4000) {
            rtdb.getReference("privacy/$other/msgReq").get().await().value == true
        } ?: false
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        false
    }

    fun send(
        me: String, other: String, text: String,
        replyId: String = "", replyText: String = "", replyFrom: String = "",
        extra: Map<String, Any> = emptyMap()
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
        msg.putAll(extra)
        val preview = text.take(100)
        appScope.launch {
            sendMutex.withLock {
                // أول تواصل بيني وبينه (ما عندي سجل للمحادثة) + هو مشغّل الطلبات = رسالتي تصير "طلب رسالة" عنده
                val firstContact = if (other in knownChats) false else try {
                    withTimeoutOrNull(3000) {
                        !rtdb.getReference("userChats/$me/$other/lastAt").get().await().exists()
                    } ?: false
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    false
                }
                val asRequest = firstContact && requiresRequest(other)

                // عملية واحدة ذرية: الرسالة + قائمة المحادثات عند الطرفين + عداد غير المقروء عند الطرف الثاني
                val updates = hashMapOf<String, Any?>(
                    "chats/$cid/messages/$key" to msg,
                    "userChats/$other/$me/lastText" to preview,
                    "userChats/$other/$me/lastAt" to ServerValue.TIMESTAMP,
                    "userChats/$other/$me/unread" to ServerValue.increment(1),
                    "userChats/$me/$other/lastText" to preview,
                    "userChats/$me/$other/lastAt" to ServerValue.TIMESTAMP,
                    // إذا كان عندي طلب منه بانتظاري وأرسلت له = قبلت طلبه تلقائياً
                    "userChats/$me/$other/req" to null
                )
                if (asRequest) updates["userChats/$other/$me/req"] = true
                knownChats.add(other)
                rtdb.reference.updateChildren(updates).addOnSuccessListener {
                    // الرسالة وصلت للسيرفر: نطلب إرسال الإشعار للمستلم (حتى لو تطبيقه مطفي)
                    Push.notify(other, key)
                }.addOnFailureListener {
                    // لو ما قدرنا نقرأ إعداد الطرف الثاني (نت ضعيف) وطلعت القواعد ترفض: نعيد مرة وحدة كطلب رسالة
                    if (firstContact && !asRequest) {
                        updates["userChats/$other/$me/req"] = true
                        rtdb.reference.updateChildren(updates).addOnSuccessListener { Push.notify(other, key) }
                    }
                }
            }
        }
    }

    // ---------- طلبات الرسائل ----------

    /** هل لدي طلب رسالة من [other] بانتظار موافقتي؟ */
    fun incomingRequestFlow(me: String, other: String): Flow<Boolean> {
        if (me.isBlank()) return flowOf(false)
        return rtFlow(rtdb.getReference("userChats/$me/$other/req")) { it.value == true }
    }

    /** هل رسالتي الأولى لـ [other] عنده كطلب ولسه ما وافق؟ (لين يوافق ما أگدر أرسل غيرها) */
    fun sentRequestPendingFlow(me: String, other: String): Flow<Boolean> = callbackFlow {
        val ref = rtdb.getReference("userChats/$other/$me/req")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(s.value == true) }
            override fun onCancelled(e: DatabaseError) { trySend(false) }
        }
        ref.addValueEventListener(l)
        awaitClose { ref.removeEventListener(l) }
    }

    /** أوافق على طلب الرسالة: تنتقل المحادثة لقائمة المحادثات العادية وتفتح له المراسلة */
    fun acceptRequest(me: String, other: String) {
        rtdb.getReference("userChats/$me/$other/req").removeValue()
    }

    /** عدد طلبات الرسائل (الي ما حذفتها) حتى يطلع على زر "الطلبات" */
    fun requestCountFlow(me: String): Flow<Int> =
        combine(chatsFlow(me), clearedFlow(me)) { chats, cleared ->
            chats.count { it.req && it.lastAt > (cleared[it.uid] ?: 0L) }
        }.distinctUntilChanged()

    // ---------- الصور والفيديوهات ----------
    // الملف نفسه يتخزن مقسّم (base64) بمسار media/<chatId>/<mediaId>/c/<i> + meta،
    // والرسالة تحمل فقط صورة مصغّرة صغيرة ومعرّف الملف. الطرف الثاني ينزّله عند الضغط ويحفظه بالكاش.

    private const val MEDIA_RAW_CHUNK = 375_000   // ينتج 500,000 حرف base64 بالضبط (بدون حشو)

    private fun mediaRef(me: String, other: String, id: String) =
        rtdb.getReference("media/${chatId(me, other)}/$id")

    /** يرفع الملف على دفعات ويرجّع معرّفه. [onProgress] من 0 إلى 1 */
    suspend fun uploadMedia(
        me: String, other: String, file: java.io.File, type: String, onProgress: (Float) -> Unit
    ): String = uploadMediaAt("media/${chatId(me, other)}", me, file, type, onProgress)

    /** نفس الرفع لكن لمجموعة (المسار groupMedia/<gid>) */
    suspend fun uploadGroupMedia(
        gid: String, me: String, file: java.io.File, type: String, onProgress: (Float) -> Unit
    ): String = uploadMediaAt("groupMedia/$gid", me, file, type, onProgress)

    private suspend fun uploadMediaAt(
        basePath: String, me: String, file: java.io.File, type: String, onProgress: (Float) -> Unit
    ): String {
        val key = rtdb.getReference(basePath).push().key
            ?: throw IllegalStateException("تعذّر الإرسال")
        val ref = rtdb.getReference("$basePath/$key")
        val total = file.length()
        val n = ((total + MEDIA_RAW_CHUNK - 1) / MEDIA_RAW_CHUNK).toInt().coerceAtLeast(1)
        try {
            withTimeout(30_000) {
                ref.child("meta").setValue(mapOf("from" to me, "type" to type, "n" to n, "size" to total)).await()
            }
            withContext(Dispatchers.IO) {
                file.inputStream().use { inp ->
                    val buf = ByteArray(MEDIA_RAW_CHUNK)
                    for (i in 0 until n) {
                        var read = 0
                        while (read < MEDIA_RAW_CHUNK) {
                            val r = inp.read(buf, read, MEDIA_RAW_CHUNK - read)
                            if (r < 0) break
                            read += r
                        }
                        val str = android.util.Base64.encodeToString(buf, 0, read, android.util.Base64.NO_WRAP)
                        withTimeout(90_000) { ref.child("c").child(i.toString()).setValue(str).await() }
                        onProgress((i + 1f) / n)
                    }
                }
            }
        } catch (e: Exception) {
            runCatching { ref.removeValue() }
            throw e
        }
        return key
    }

    /** ينزّل الملف ويكتبه بـ [dest]. يرمي خطأ إذا انحذف أو انقطع النت */
    suspend fun downloadMedia(
        me: String, other: String, id: String, dest: java.io.File, onProgress: (Float) -> Unit
    ) = downloadMediaFrom(mediaRef(me, other, id), dest, onProgress)

    /** تنزيل ملف مجموعة (مثل رسالة صوتية) */
    suspend fun downloadGroupMedia(gid: String, id: String, dest: java.io.File, onProgress: (Float) -> Unit) =
        downloadMediaFrom(rtdb.getReference("groupMedia/$gid/$id"), dest, onProgress)

    private suspend fun downloadMediaFrom(
        ref: com.google.firebase.database.DatabaseReference, dest: java.io.File, onProgress: (Float) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            val n = (withTimeout(30_000) { ref.child("meta").child("n").get().await() }.value as? Number)?.toInt()
                ?: throw IllegalStateException("الملف انحذف")
            val tmp = java.io.File(dest.path + ".part")
            try {
                tmp.outputStream().use { out ->
                    for (i in 0 until n) {
                        val str = withTimeout(90_000) { ref.child("c").child(i.toString()).get().await() }.value as? String
                            ?: throw IllegalStateException("الملف ناقص")
                        out.write(android.util.Base64.decode(str, android.util.Base64.NO_WRAP))
                        onProgress((i + 1f) / n)
                    }
                }
                if (!tmp.renameTo(dest)) throw IllegalStateException("تعذّر حفظ الملف")
            } finally {
                if (tmp.exists()) tmp.delete()
            }
        }
    }

    /** يرسل رسالة وسائط (بعد ما انرفع الملف). النص البديل يظهر بالإشعارات وقائمة المحادثات */
    fun sendMedia(
        me: String, other: String, type: String, mediaId: String, thumb: String,
        w: Int, h: Int, durSec: Int,
        replyId: String = "", replyText: String = "", replyFrom: String = ""
    ) {
        val extra = hashMapOf<String, Any>(
            "mediaType" to type, "mediaId" to mediaId, "thumb" to thumb,
            "mediaW" to w, "mediaH" to h
        )
        if (type == "video") extra["mediaDur"] = durSec
        send(
            me, other, if (type == "video") "🎥 فيديو" else "📷 صورة",
            replyId, replyText, replyFrom, extra
        )
    }

    /** يرسل رسالة ملف (بعد ما انرفع الملف بنوع "file") */
    fun sendFile(
        me: String, other: String, mediaId: String, name: String, size: Long,
        replyId: String = "", replyText: String = "", replyFrom: String = ""
    ) {
        val extra = hashMapOf<String, Any>(
            "mediaType" to "file", "mediaId" to mediaId,
            "fileName" to name.take(120), "fileSize" to size
        )
        send(me, other, "📎 ملف", replyId, replyText, replyFrom, extra)
    }

    /** يرسل رسالة صوتية (بعد ما انرفع الملف بنوع "audio"). المدة بالثواني والموجة نص أرقام */
    fun sendVoice(
        me: String, other: String, mediaId: String, durSec: Int, wave: String,
        replyId: String = "", replyText: String = "", replyFrom: String = ""
    ) {
        val extra = hashMapOf<String, Any>(
            "mediaType" to "audio", "mediaId" to mediaId,
            "mediaDur" to durSec, "wave" to wave
        )
        send(me, other, "🎤 رسالة صوتية", replyId, replyText, replyFrom, extra)
    }


    // ---------- غير المقروء + حذف المحادثة من عندي فقط ----------

    /**
     * أصفّر عداد الرسائل غير المقروءة لهذي المحادثة وأسجل وقت قراءتي (الطرف الثاني يشوف منه "مقروء").
     * [publish] = false أو "إخفاء حالة مقروء" مشغّل: أصفّر العداد عندي فقط وما أبيّن للطرف الثاني أني قريت.
     */
    fun markRead(me: String, other: String, publish: Boolean = true) {
        val updates = hashMapOf<String, Any>("userChats/$me/$other/unread" to 0)
        if (publish && !PrivacyState.value.hideRead) {
            updates["chats/${chatId(me, other)}/readAt/$me"] = ServerValue.TIMESTAMP
        }
        rtdb.reference.updateChildren(updates)
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

    /**
     * - "إخفاء حالة الاتصال": ما أكتب أبداً أني متصل (أكتب online=false)، وآخر ظهور يبقى وقت آخر مرة طلعت فيها فعلاً
     * - "إخفاء وقت الظهور": أرفع علم hideLastSeen والباقي ما يعرض وقتي
     */
    fun setPresence(online: Boolean) {
        val u = auth.currentUser ?: return
        val pv = PrivacyState.value
        val now = System.currentTimeMillis()
        val key = "${u.uid}:$online:${pv.hideOnline}:${pv.hideLastSeen}"
        // نفس الحالة خلال أقل من 60 ثانية (تبديل سريع بين التطبيقات): ما نكتب مرة ثانية
        if (key == lastPresenceKey && now - lastPresenceAt < 60_000L) return
        lastPresenceKey = key
        lastPresenceAt = now
        val data = hashMapOf<String, Any>("hideLastSeen" to pv.hideLastSeen)
        if (online && pv.hideOnline) {
            data["online"] = false
        } else {
            data["online"] = online
            data["lastSeen"] = FieldValue.serverTimestamp()
        }
        db.collection("presence").document(u.uid).set(data, SetOptions.merge())
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
                            ?.toDate()?.time ?: 0L,
                        hideLastSeen = d.getBoolean("hideLastSeen") == true
                    )
                )
            }
        }
        awaitClose { reg.remove() }
    }

    // ---------- إعدادات الخصوصية ----------

    /** إعدادات خصوصيتي (تتحدث فوراً وتنحفظ بالكاش لو ما في نت) */
    fun privacyFlow(me: String): Flow<Privacy> {
        if (me.isBlank()) return flowOf(Privacy())
        return rtFlow(rtdb.getReference("privacy/$me")) { s ->
            Privacy(
                hideOnline = s.flag("hideOnline"),
                hideRead = s.flag("hideRead"),
                hideLastSeen = s.flag("hideLastSeen"),
                msgReq = s.flag("msgReq")
            )
        }
    }

    /** يغيّر خيار خصوصية واحد. التغيير يطبق فوراً محلياً وينكتب بالسيرفر، وحالة الاتصال تنعاد كتابتها */
    fun setPrivacy(field: String, value: Boolean) {
        val u = auth.currentUser ?: return
        val cur = PrivacyState.value
        PrivacyState.value = when (field) {
            "hideOnline" -> cur.copy(hideOnline = value)
            "hideRead" -> cur.copy(hideRead = value)
            "hideLastSeen" -> cur.copy(hideLastSeen = value)
            "msgReq" -> cur.copy(msgReq = value)
            else -> cur
        }
        // نخزن فقط القيم الفعّالة (true)، والإطفاء يحذف الحقل
        rtdb.getReference("privacy/${u.uid}/$field").setValue(if (value) true else null)
        if (field == "hideOnline" || field == "hideLastSeen") {
            lastPresenceKey = ""   // إجبار الكتابة الفورية بالحالة الجديدة
            setPresence(AppState.foreground)
        }
        // "طلبات الرسائل" لما تتشغّل: المحادثات الموجودة أصلاً تبقى عادية (ما تتحول لطلبات)، وهذا يتحقق لأن الطلب
        // ينطبق فقط على أول تواصل جديد (ما فيه سجل محادثة سابق)
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
        musicEnd = (it["musicEnd"] as? Number)?.toInt() ?: 0,
        isPublic = it["public"] as? Boolean ?: false
    )

    /** بيانات الملف الشخصي الأساسية فقط (بدون القصة): خفيفة، تنفع لصفوف القوائم */
    fun basicProfileFlow(uid: String): Flow<Profile> = callbackFlow {
        if (uid.isBlank()) { close(); return@callbackFlow }
        val reg = db.collection("profiles").document(uid).addSnapshotListener { d, err ->
            if (err != null) {
                // الصلاحيات ترفض القراءة إذا هذا المستخدم حاظرني
                trySend(Profile(restricted = true))
            } else if (d != null && uid != auth.currentUser?.uid &&
                (d.getBoolean("disabled") == true || d.getBoolean("deleted") == true)
            ) {
                trySend(
                    Profile(
                        name = if (d.getBoolean("deleted") == true) "حساب محذوف" else "حساب معطّل",
                        restricted = true, unavailable = true
                    )
                )
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
                        bannerGifV = d.getLong("bannerGifV") ?: 0L,
                        bannerFx = (d.getDouble("bannerFx") ?: 0.0).toFloat(),
                        bannerFy = (d.getDouble("bannerFy") ?: 0.0).toFloat(),
                        story = null,
                        status = status,
                        bubbleStyle = d.getString("bubbleStyle") ?: "glass",
                        customBubble = d.getString("customBubble") ?: "",
                        badge = d.getString("badge") ?: "",
                        introOn = d.getBoolean("introOn") == true,
                        intro = d.getString("intro") ?: "",
                        introSticker = d.getString("introSticker") ?: ""
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

    /** اليوزر صالح إذا فيه 3 حروف أو أرقام على الأقل (الشرطة السفلية والنقطة ما تنحسب) */
    fun isValidUsername(u: String): Boolean = u.count { it in 'a'..'z' || it in '0'..'9' } >= 3

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
        if (current.isNotBlank() && isValidUsername(current)) {
            claimUsername(u.uid, current, "")
            db.collection("users").document(u.uid).set(mapOf("username" to current), SetOptions.merge()).await()
            return
        }
        repeat(8) {
            val cand = genUsername()
            // إذا اليوزر القديم قصير (أقل من 3) ينستبدل بيوزر جديد ويتفك حجز القديم
            if (claimUsername(u.uid, cand, current)) {
                profRef.set(mapOf("username" to cand), SetOptions.merge()).await()
                db.collection("users").document(u.uid).set(mapOf("username" to cand), SetOptions.merge()).await()
                return
            }
        }
    }

    /** حفظ الاسم/اليوزر/النبذة/الصور. الصور نص base64 مضغوط */
    suspend fun saveProfileInfo(name: String, username: String, bio: String, avatar: String, banner: String, bannerGifV: Long = 0L, bannerFx: Float = 0f, bannerFy: Float = 0f) {
        val u = auth.currentUser ?: return
        val old = db.collection("profiles").document(u.uid).get().await().getString("username") ?: ""
        val finalUser = username.ifBlank { old }
        if (!isValidUsername(finalUser)) throw Exception("اليوزر لازم يكون 3 حروف أو أرقام على الأقل")
        if (finalUser != old) {
            if (!claimUsername(u.uid, finalUser, old)) throw Exception("اليوزر محجوز، اختر غيره")
        }
        db.collection("profiles").document(u.uid).set(
            mapOf("name" to name, "username" to finalUser, "bio" to bio, "avatar" to avatar, "banner" to banner, "bannerGifV" to bannerGifV,
                "bannerFx" to bannerFx.coerceIn(-1f, 1f).toDouble(), "bannerFy" to bannerFy.coerceIn(-1f, 1f).toDouble()),
            SetOptions.merge()
        ).await()
        db.collection("users").document(u.uid).set(mapOf("name" to name, "username" to finalUser), SetOptions.merge()).await()
        // حتى saveProfile() ما يرجّع الاسم القديم عند كل فتح للتطبيق
        u.updateProfile(
            com.google.firebase.auth.UserProfileChangeRequest.Builder().setDisplayName(name).build()
        ).await()
    }

    // ---------- GIF البانر (يتخزن بـ Realtime Database لأن حد الكتابة فيه 10MB، بدل 1MB بـ Firestore) ----------

    /** يرفع GIF البانر ويرجع رقم نسخته (يُحفظ بالبروفايل حتى الأجهزة تعرف متى تنزّل من جديد) */
    suspend fun uploadBannerGif(bytes: ByteArray): Long {
        val u = auth.currentUser ?: throw IllegalStateException("لازم تسجّل دخول")
        val v = System.currentTimeMillis()
        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        withTimeout(120_000) {
            rtdb.getReference("bannerGifs/${u.uid}").setValue(mapOf("v" to v, "data" to b64)).await()
        }
        return v
    }

    suspend fun deleteBannerGif() {
        val u = auth.currentUser ?: return
        runCatching { withTimeout(20_000) { rtdb.getReference("bannerGifs/${u.uid}").removeValue().await() } }
    }

    /**
     * ينزّل GIF بانر [uid] مرة وحدة لكل نسخة [v] ويخزنه بالكاش. المرات الجاية يقرأ من الملف بدون إنترنت.
     * يرجع null إذا ما انقرى (حظر، لا إنترنت...) فيبقى البانر الثابت ظاهر.
     */
    suspend fun bannerGifFile(cacheDir: java.io.File, uid: String, v: Long): java.io.File? {
        val dir = java.io.File(cacheDir, "gif_banners").apply { mkdirs() }
        val cached = java.io.File(dir, "u_${uid}_$v.gif")
        if (cached.exists() && cached.length() > 0) return cached
        val snap = withTimeout(120_000) { rtdb.getReference("bannerGifs/$uid").get().await() }
        val data = snap.child("data").getValue(String::class.java) ?: return null
        val real = snap.child("v").getValue(Long::class.java) ?: v
        val bytes = android.util.Base64.decode(data, android.util.Base64.NO_WRAP)
        // نحذف النسخ القديمة لهذا المستخدم
        dir.listFiles { f -> f.name.startsWith("u_${uid}_") }?.forEach { it.delete() }
        val out = java.io.File(dir, "u_${uid}_$real.gif")
        val tmp = java.io.File(dir, out.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(out)) { tmp.delete(); return null }
        com.chatapp.messenger.ui.GifUtil.trimCache(dir)
        return out
    }

    /** نشر القصة: تنكتب بوثيقة محمية storyData/<uid> (للأصدقاء فقط) + فهرس عام `stories/<uid>` يحتوي الوقت فقط */
    suspend fun saveStory(st: Story) {
        val u = auth.currentUser ?: return
        val map = mapOf(
            "text" to st.text, "color" to st.color, "image" to st.image, "createdAt" to st.createdAt,
            "bg" to st.bg, "filter" to st.filter,
            "musicUrl" to st.musicUrl, "musicTitle" to st.musicTitle, "musicArtist" to st.musicArtist,
            "musicCover" to st.musicCover, "musicStart" to st.musicStart, "musicEnd" to st.musicEnd,
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
        // سجل النشاط عند صاحب القصة (يبقى حتى بعد انتهاء القصة)
        runCatching {
            val ev = db.collection("activity").document(owner).collection("events").document("like_$me")
            if (liked) ev.set(mapOf("type" to "like", "uid" to me, "at" to now)).await() else ev.delete().await()
        }
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

    // ---------- المتابعة والأصدقاء ----------
    // follows/<from>_<to> = "from" يتابع "to" (فوري، بدون طلب).
    // المتابعين = يتابعوني وأنا ما أتابعهم | أتابعه = أتابعهم وما يتابعوني | الأصدقاء = متابعة متبادلة

    private fun docExistsFlow(col: String, id: String): Flow<Boolean> = callbackFlow {
        val reg = db.collection(col).document(id).addSnapshotListener { d, err ->
            if (err != null) trySend(false) else if (d != null) trySend(d.exists())
        }
        awaitClose { reg.remove() }
    }

    /** (معرّف الطرف الثاني، وقت المتابعة) من استعلام متابعة، الأحدث أولاً */
    private fun followsTimedFlow(field: String, other: String, me: String): Flow<List<Pair<String, Long>>> = callbackFlow {
        if (me.isBlank()) { close(); return@callbackFlow }
        val reg = db.collection("follows").whereEqualTo(field, me)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    trySend(
                        snap.documents.mapNotNull { d ->
                            val uid = d.getString(other) ?: return@mapNotNull null
                            uid to (d.getTimestamp("at", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                                ?.toDate()?.time ?: 0L)
                        }.sortedByDescending { it.second }
                    )
                }
            }
        awaitClose { reg.remove() }
    }

    private fun followsQueryFlow(field: String, other: String, me: String): Flow<List<String>> =
        followsTimedFlow(field, other, me).map { l -> l.map { it.first } }

    /** من يتابعني مع وقت المتابعة (لصفحة النشاط) */
    fun followersTimedFlow(me: String): Flow<List<Pair<String, Long>>> = followsTimedFlow("to", "from", me)

    /** كل من أتابعهم (الأحدث أولاً) */
    fun followingFlow(me: String): Flow<List<String>> = followsQueryFlow("from", "to", me)

    /** كل من يتابعني (الأحدث أولاً) */
    fun followersFlow(me: String): Flow<List<String>> = followsQueryFlow("to", "from", me)

    /** الأصدقاء = أتابعهم ويتابعوني (متابعة متبادلة) */
    fun friendsFlow(me: String): Flow<List<String>> =
        combine(followingFlow(me), followersFlow(me)) { ing, ers ->
            val s = ers.toSet()
            ing.filter { it in s }
        }

    fun friendIdsFlow(): Flow<Set<String>> {
        val me = auth.currentUser?.uid ?: return flowOf(emptySet())
        return friendsFlow(me).map { it.toSet() }
    }

    /** هل [other] صديقي (متابعة متبادلة)؟ */
    fun isFriendFlow(other: String): Flow<Boolean> {
        val me = auth.currentUser?.uid ?: return flowOf(false)
        return combine(
            docExistsFlow("follows", "${me}_$other"),
            docExistsFlow("follows", "${other}_$me")
        ) { a, b -> a && b }
    }

    /**
     * علاقتي بهذا الشخص (تتحدث مباشرة):
     * NONE = ولا واحد يتابع الثاني | SENT = أنا أتابعه فقط | RECEIVED = هو يتابعني فقط | FRIENDS = متبادل
     */
    fun relationFlow(other: String): Flow<Relation> {
        val me = auth.currentUser?.uid ?: return flowOf(Relation.NONE)
        return combine(
            docExistsFlow("follows", "${me}_$other"),
            docExistsFlow("follows", "${other}_$me")
        ) { iFollow, heFollows ->
            when {
                iFollow && heFollows -> Relation.FRIENDS
                iFollow -> Relation.SENT
                heFollows -> Relation.RECEIVED
                else -> Relation.NONE
            }
        }
    }

    /** متابعة شخص (زر "إضافة صديق" = اختصار لهذي) */
    suspend fun follow(other: String) {
        val me = auth.currentUser?.uid ?: return
        if (me == other) return
        db.collection("follows").document("${me}_$other").set(
            mapOf("from" to me, "to" to other, "at" to FieldValue.serverTimestamp())
        ).await()
    }

    /** إلغاء متابعتي لشخص (إذا كنا أصدقاء نرجع هو يتابعني فقط) */
    suspend fun unfollow(other: String) {
        val me = auth.currentUser?.uid ?: return
        db.collection("follows").document("${me}_$other").delete().await()
    }

    /**
     * ترحيل الصداقات القديمة (friendships) لمتابعة متبادلة، مرة وحدة لكل صداقة.
     * القواعد تسمح لأي طرف يكتب وثيقتي المتابعة إذا الصداقة القديمة موجودة.
     */
    suspend fun migrateLegacyFriendships() {
        val me = auth.currentUser?.uid ?: return
        val snap = db.collection("friendships").whereArrayContains("users", me).get().await()
        for (d in snap.documents) {
            val other = (d.get("users") as? List<*>)?.firstOrNull { it != me } as? String ?: continue
            val at = d.get("at") ?: FieldValue.serverTimestamp()
            runCatching {
                db.batch()
                    .set(db.collection("follows").document("${me}_$other"), mapOf("from" to me, "to" to other, "at" to at))
                    .set(db.collection("follows").document("${other}_$me"), mapOf("from" to other, "to" to me, "at" to at))
                    .delete(d.reference)
                    .commit().await()
            }
        }
    }

    // ---------- مقدمة الدردشة ----------

    suspend fun saveIntro(on: Boolean, text: String, sticker: String) {
        val u = auth.currentUser ?: return
        db.collection("profiles").document(u.uid).set(
            mapOf("introOn" to on, "intro" to text.trim().take(150), "introSticker" to sticker),
            SetOptions.merge()
        ).await()
    }

    // ---------- النشاط ----------
    // activity/<owner>/events/<type>_<actor> = { type: visit|like, uid: actor, at }
    // الفاعل يكتب وثيقته هو فقط عند صاحب الملف/القصة، وصاحبها فقط يقرأها.

    private val visitSentAt = HashMap<String, Long>()

    /** أنا فتحت ملف [owner]: تتسجل بنشاطه (مرة كل 10 دقائق بالأكثر لنفس الشخص) */
    fun recordProfileVisit(owner: String) {
        val me = auth.currentUser?.uid ?: return
        if (owner.isBlank() || owner == me) return
        val now = System.currentTimeMillis()
        synchronized(visitSentAt) {
            if (now - (visitSentAt[owner] ?: 0L) < 10 * 60_000L) return
            visitSentAt[owner] = now
        }
        db.collection("activity").document(owner).collection("events").document("visit_$me")
            .set(mapOf("type" to "visit", "uid" to me, "at" to now))
    }

    /** نشاط ملفي: زيارات + إعجابات القصص (الأحدث أولاً) */
    fun activityFlow(me: String): Flow<List<ActivityEvent>> = callbackFlow {
        if (me.isBlank()) { close(); return@callbackFlow }
        val reg = db.collection("activity").document(me).collection("events")
            .addSnapshotListener { snap, err ->
                if (err != null) trySend(emptyList())
                else if (snap != null) {
                    trySend(
                        snap.documents.mapNotNull { d ->
                            val uid = d.getString("uid") ?: return@mapNotNull null
                            val type = d.getString("type") ?: return@mapNotNull null
                            ActivityEvent(uid, type, (d.get("at") as? Number)?.toLong() ?: 0L)
                        }.sortedByDescending { it.at }
                    )
                }
            }
        awaitClose { reg.remove() }
    }

    // ---------- شكل الفقاعات (يتخزن بملفي الشخصي حتى يشوفه الطرف الثاني) ----------

    fun setBubbleStyle(id: String) {
        val u = auth.currentUser ?: return
        // اختيار فقاعة أصلية يمسح التصميم المخصّص من ملفي
        db.collection("profiles").document(u.uid)
            .set(mapOf("bubbleStyle" to id, "customBubble" to FieldValue.delete()), SetOptions.merge())
    }

    /** يفعّل فقاعة مخصّصة (JSON التصميم) فتظهر عند الطرف الثاني */
    fun setCustomBubble(json: String) {
        val u = auth.currentUser ?: return
        db.collection("profiles").document(u.uid)
            .set(mapOf("bubbleStyle" to "custom", "customBubble" to json), SetOptions.merge())
    }

    // ---------- رمز الاسم المتحرك (يتخزن بملفي الشخصي حتى يشوفه الكل) ----------

    fun setBadge(id: String) {
        val u = auth.currentUser ?: return
        db.collection("profiles").document(u.uid).set(mapOf("badge" to id), SetOptions.merge())
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

    // ---------- تعطيل / حذف الحساب ----------

    /** تعطيل الحساب: يختفي عن المستخدمين، وتگدر ترجعه بتسجيل الدخول من جديد */
    suspend fun deactivateAccount() {
        val u = auth.currentUser ?: throw IllegalStateException("لازم تسجّل دخول")
        db.collection("users").document(u.uid)
            .set(mapOf("disabled" to true, "disabledAt" to System.currentTimeMillis()), SetOptions.merge()).await()
        db.collection("profiles").document(u.uid).set(mapOf("disabled" to true), SetOptions.merge()).await()
    }

    /** إذا الحساب معطّل ودخل صاحبه من جديد: يرجع يظهر تلقائياً */
    suspend fun reactivateIfDisabled() {
        val u = auth.currentUser ?: return
        val ref = db.collection("users").document(u.uid)
        if (ref.get().await().getBoolean("disabled") == true) {
            ref.set(mapOf("disabled" to false, "disabledAt" to FieldValue.delete()), SetOptions.merge()).await()
            db.collection("profiles").document(u.uid).set(mapOf("disabled" to false), SetOptions.merge()).await()
        }
    }

    private suspend fun deleteAllDocs(q: Query) {
        val snap = q.get().await()
        snap.documents.forEach { runCatching { it.reference.delete().await() } }
    }

    /**
     * حذف الحساب نهائياً: يمسح بياناته (الملف، القصة، الصداقات، الطلبات، اليوزر، المحادثات من قائمته...) ثم يحذف حساب الدخول.
     * ملاحظة: رسائل المحادثات تبقى عند الطرف الثاني (القواعد ما تسمح بمسح رسائل الغير) ويظهر حسابك عندهم "حساب محذوف".
     */
    suspend fun deleteAccountPermanently() {
        val u = auth.currentUser ?: throw IllegalStateException("لازم تسجّل دخول")
        val uid = u.uid

        // المجموعات: أطلع منها (إلا الي أنا مالكها تبقى)
        runCatching {
            val gids = rtdb.getReference("userGroups/$uid").get().await().children.mapNotNull { it.key }
            for (gid in gids) {
                runCatching {
                    val owner = rtdb.getReference("groups/$gid/meta/owner").get().await().value as? String
                    if (owner != uid) {
                        val up = HashMap<String, Any?>()
                        up["groups/$gid/members/$uid"] = null
                        up["groups/$gid/admins/$uid"] = null
                        up["userGroups/$uid/$gid"] = null
                        rtdb.reference.updateChildren(up).await()
                    }
                }
            }
        }
        // قائمة محادثاتي وقائمة الحظر بالـ Realtime Database
        runCatching {
            val up = HashMap<String, Any?>()
            rtdb.getReference("userChats/$uid").get().await().children.forEach { c -> c.key?.let { up["userChats/$uid/$it"] = null } }
            rtdb.getReference("blocks/$uid").get().await().children.forEach { c -> c.key?.let { up["blocks/$uid/$it"] = null } }
            up["privacy/$uid"] = null
            if (up.isNotEmpty()) rtdb.reference.updateChildren(up).await()
        }

        // الصداقات والطلبات والحظر
        runCatching { deleteAllDocs(db.collection("friendships").whereArrayContains("users", uid)) }
        runCatching { deleteAllDocs(db.collection("friendRequests").whereEqualTo("from", uid)) }
        runCatching { deleteAllDocs(db.collection("friendRequests").whereEqualTo("to", uid)) }
        runCatching { deleteAllDocs(db.collection("follows").whereEqualTo("from", uid)) }
        runCatching { deleteAllDocs(db.collection("follows").whereEqualTo("to", uid)) }
        runCatching { deleteAllDocs(db.collection("activity").document(uid).collection("events")) }
        runCatching { deleteAllDocs(db.collection("blocks").document(uid).collection("users")) }

        // القصة ومشاهداتها
        runCatching { deleteAllDocs(db.collection("storyData").document(uid).collection("views")) }
        runCatching { db.collection("storyData").document(uid).delete().await() }
        runCatching { db.collection("stories").document(uid).delete().await() }

        // اليوزر: يتحرر حتى يگدر غيري ياخذه
        runCatching {
            val username = db.collection("profiles").document(uid).get().await().getString("username").orEmpty()
            if (username.isNotBlank()) {
                val ref = db.collection("usernames").document(username)
                if (ref.get().await().getString("uid") == uid) ref.delete().await()
            }
        }

        // إعدادات وحالة الاتصال وتوكن الإشعارات
        runCatching { db.collection("presence").document(uid).delete().await() }
        runCatching { db.collection("settings").document(uid).delete().await() }
        runCatching {
            val fm = com.google.firebase.messaging.FirebaseMessaging.getInstance()
            val token = withTimeoutOrNull(3000) { fm.token.await() }
            if (token != null) withTimeoutOrNull(3000) {
                db.collection("tokens").document(uid).collection("devices").document(token).delete().await()
            }
            withTimeoutOrNull(3000) { fm.deleteToken().await() }
        }

        // الملف العام ووثيقة المستخدم: تنستبدل بعلامة "محذوف" بدون أي بيانات شخصية (القواعد ما تسمح بالحذف المباشر)
        db.collection("profiles").document(uid).set(mapOf("deleted" to true)).await()
        db.collection("users").document(uid).set(mapOf("uid" to uid, "deleted" to true)).await()

        // أخيراً: حذف حساب الدخول نفسه
        u.delete().await()
    }

    /** تسجيل الخروج: يحذف توكن الجهاز أولاً حتى ما توصل إشعارات الحساب بعد الخروج */
    fun signOut() {
        val u = auth.currentUser
        // نظّف حالة الحساب القديم (خصوصية + سجل المحادثات) حتى ما تتسرب لحساب ثاني
        PrivacyState.value = Privacy()
        knownChats.clear()
        lastPresenceKey = ""
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
