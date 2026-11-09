package com.chatapp.messenger

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/**
 * خدمة بالخلفية (Foreground Service) تسمع لمحادثات المستخدم بـ Realtime Database
 * وتطلع إشعار لما توصل رسالة جديدة والتطبيق مو مفتوح. بدون سيرفر وبدون Cloud Functions.
 *
 * - تشتغل فقط وانت مسجل دخول (تتوقف تلقائياً عند الخروج).
 * - لكل محادثة نحفظ "مؤشر" (وقت آخر رسالة معالجة) حتى ما تتكرر الإشعارات،
 *   والرسائل الي وصلت والخدمة واقفة تطلع لها إشعار لما ترجع.
 */
class MessageListenerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var prefs: SharedPreferences
    private val locks = ConcurrentHashMap<String, Mutex>()

    private var listenRef: DatabaseReference? = null
    private var listener: ValueEventListener? = null
    private var myUid: String? = null
    private var initialDone = false

    private val authListener = FirebaseAuth.AuthStateListener { a ->
        val u = a.currentUser
        if (u == null) {
            stopListening()
            myUid = null
            stopSelf()
        } else if (u.uid != myUid) {
            startListening()
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("notif_cursor_rt", Context.MODE_PRIVATE)
        Repo.auth.addAuthStateListener(authListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        if (Repo.auth.currentUser == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startListening()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Repo.auth.removeAuthStateListener(authListener)
        stopListening()
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------ إشعار الخدمة الدائم
    private fun goForeground() {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, ChatApp.CHANNEL_LISTENER)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(androidx.core.content.ContextCompat.getColor(this, R.color.notification_accent))
            .setContentTitle("متصل لاستقبال الرسائل")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(open)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    // ------------------------------------------------------------ الاستماع
    // ------------------------------------------------------------ إشعارات الدعم الفني (Firestore)
    private val supportRegs = java.util.concurrent.CopyOnWriteArrayList<ListenerRegistration>()

    private fun stopSupportListening() {
        supportRegs.forEach { it.remove() }
        supportRegs.clear()
    }

    private fun startSupportListening(me: String) {
        stopSupportListening()
        val fs = FirebaseFirestore.getInstance()
        // 1) رد المطور على محادثتي مع "ChatApp"
        var firstMine = true
        supportRegs.add(fs.collection("support").document(me).addSnapshotListener { d, err ->
            val baseline = firstMine
            firstMine = false
            if (err == null && d != null && d.exists()) handleSupport(me, d, false, baseline)
        })
        // 2) للمطور فقط: رسائل المستخدمين الجديدة
        scope.launch {
            if (!com.chatapp.messenger.ui.Support.isDev() || myUid != me) return@launch
            var first = true
            supportRegs.add(fs.collection("support").addSnapshotListener { s, err ->
                val baseline = first
                first = false
                if (err == null && s != null) s.documents.forEach { handleSupport(me, it, true, baseline) }
            })
        }
    }

    private fun handleSupport(me: String, d: DocumentSnapshot, asDev: Boolean, baseline: Boolean) {
        val uid = d.id
        val lastAt = d.getLong("lastAt") ?: return
        val from = d.getString("lastFrom") ?: return
        val key = "support_$uid"
        if (baseline && !prefs.contains(key)) { prefs.edit().putLong(key, lastAt).apply(); return }
        if (lastAt <= prefs.getLong(key, 0L)) return
        prefs.edit().putLong(key, lastAt).apply()
        val wanted = if (asDev) (from == "user" && uid != me) else from == "dev"
        if (!wanted || AppState.foreground) return
        val text = d.getString("lastText").orEmpty()
        if (text.isBlank()) return
        val title = if (asDev) "دعم · " + d.getString("name").orEmpty().ifBlank { d.getString("username").orEmpty() }.ifBlank { "مستخدم" }
        else "ChatApp"
        showSupportNotification(uid, title, text, fromApp = !asDev)
    }

    private fun showSupportNotification(threadUid: String, title: String, text: String, fromApp: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val id = ("support:" + threadUid).hashCode()
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ChatMessagingService.EXTRA_OPEN_CHAT, "support:$threadUid")
        }
        val pi = PendingIntent.getActivity(this, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val icon = if (fromApp) try {
            android.graphics.BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher_foreground)
        } catch (_: Exception) { null } else null
        val n = NotificationCompat.Builder(this, ChatApp.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .apply { icon?.let { setLargeIcon(it) } }
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        @Suppress("MissingPermission")
        androidx.core.app.NotificationManagerCompat.from(this).notify(id, n)
    }

    // ------------------------------------------------------------ رسائل المجموعات
    // نسمع لقائمة مجموعاتي (userGroups/<me>)، ولكل مجموعة نسمع للرسائل الأحدث من آخر مؤشر معالج.
    // الإشعار نفسه عبر ChatNotifier.showGroup (نفس الي تستخدمه FCM، مع منع التكرار).
    private var groupsRef: DatabaseReference? = null
    private var groupsListener: ValueEventListener? = null
    private val groupBusy = ConcurrentHashMap.newKeySet<String>()
    private val groupQueries = ConcurrentHashMap<String, Pair<Query, ChildEventListener>>()

    private fun stopGroupListening() {
        groupsListener?.let { groupsRef?.removeEventListener(it) }
        groupsListener = null
        groupsRef = null
        groupQueries.values.forEach { (q, l) -> q.removeEventListener(l) }
        groupQueries.clear()
        groupBusy.clear()
    }

    private fun startGroupListening(me: String) {
        stopGroupListening()
        val ref = Repo.rtdb.getReference("userGroups/$me")
        val l = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val ids = snap.children.mapNotNull { it.key }.take(MAX_GROUPS).toSet()
                groupBusy.filter { it !in ids }.forEach { detachGroup(it) }
                ids.filter { it !in groupBusy }.forEach { attachGroup(me, it) }
            }

            override fun onCancelled(e: DatabaseError) {}
        }
        groupsRef = ref
        groupsListener = l
        ref.addValueEventListener(l)
    }

    private fun detachGroup(gid: String) {
        groupQueries.remove(gid)?.let { (q, l) -> q.removeEventListener(l) }
        groupBusy.remove(gid)
    }

    private fun attachGroup(me: String, gid: String) {
        if (!groupBusy.add(gid)) return
        scope.launch {
            try {
                val key = "g_$gid"
                val msgs = Repo.rtdb.getReference("groupChats/$gid/messages")
                if (!prefs.contains(key)) {
                    // أول مرة نشوف المجموعة على هذا الجهاز: ما نطلع إشعار للتاريخ القديم
                    val last = msgs.orderByChild("createdAt").limitToLast(1).get().await()
                    val t = (last.children.firstOrNull()?.child("createdAt")?.value as? Number)?.toLong() ?: 0L
                    prefs.edit().putLong(key, t).apply()
                }
                // أول مرة نشوف المجموعة: الرسائل الجاية بعدها تظهر "غير مقروءة" لما أفتح المجموعة
                GroupReads.seed(applicationContext, me, gid, prefs.getLong(key, 0L))
                if (myUid != me || gid !in groupBusy) return@launch
                val q = msgs.orderByChild("createdAt").startAt((prefs.getLong(key, 0L) + 1).toDouble()).limitToLast(10)
                val l = object : ChildEventListener {
                    override fun onChildAdded(s: DataSnapshot, previous: String?) = onGroupMessage(me, gid, s)
                    override fun onChildChanged(s: DataSnapshot, previous: String?) {}
                    override fun onChildRemoved(s: DataSnapshot) {}
                    override fun onChildMoved(s: DataSnapshot, previous: String?) {}
                    override fun onCancelled(e: DatabaseError) {
                        groupQueries.remove(gid)
                        groupBusy.remove(gid)
                    }
                }
                groupQueries[gid] = q to l
                q.addChildEventListener(l)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                groupBusy.remove(gid)
                // فشل (نت ضعيف مثلاً): نعيد المحاولة بعد نص دقيقة
                delay(30_000)
                if (myUid == me) attachGroup(me, gid)
            }
        }
    }

    private fun onGroupMessage(me: String, gid: String, s: DataSnapshot) {
        val t = (s.child("createdAt").value as? Number)?.toLong() ?: return
        if (!ChatNotifier.advanceGroupCursor(applicationContext, gid, t)) return   // معالجة قبل
        val from = s.child("from").value as? String ?: return
        if (from == me || s.child("deleted").value == true) return
        val text = s.child("text").value as? String ?: return
        if (text.isBlank() || AppState.foreground) return          // التطبيق مفتوح: الرسالة تبين بالمحادثة
        val mid = s.key ?: return
        if (!ChatNotifier.claim("g:$gid:$mid")) return             // FCM سبقنا
        scope.launch {
            try {
                ChatNotifier.showGroup(applicationContext, gid, "", from, "", text)
            } catch (_: Exception) {
            }
        }
    }

    // ------------------------------------------------------------ مكالمات واردة (احتياطي بدون FCM)
    private var callRef: com.google.firebase.database.DatabaseReference? = null
    private var callListener: ValueEventListener? = null

    private fun stopCallListening() {
        callListener?.let { callRef?.removeEventListener(it) }
        callListener = null
        callRef = null
    }

    private fun startCallListening(me: String) {
        stopCallListening()
        val r = Repo.rtdb.getReference("incomingCalls/$me")
        val l = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val id = snap.child("callId").getValue(String::class.java) ?: return
                val from = snap.child("from").getValue(String::class.java) ?: return
                if (RestrictStore.store.has(applicationContext, from)) return
                scope.launch {
                    var name = ""
                    runCatching {
                        name = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("profiles").document(from).get()
                            .await()
                            .getString("name").orEmpty()
                    }
                    val ok = CallManager.prepareIncoming(applicationContext, id, from, name)
                    if (ok) {
                        CallNotifier.showIncoming(applicationContext, id, CallManager.peerName)
                        if (AppState.foreground) CallManager.openUi(applicationContext)
                    }
                }
            }

            override fun onCancelled(e: DatabaseError) {}
        }
        callRef = r
        callListener = l
        r.addValueEventListener(l)
    }

    private fun stopListening() {
        stopCallListening()
        stopSupportListening()
        stopGroupListening()
        listener?.let { listenRef?.removeEventListener(it) }
        listener = null
        listenRef = null
    }

    private fun startListening() {
        val me = Repo.auth.currentUser?.uid ?: return
        if (listener != null && myUid == me) return
        stopListening()
        myUid = me
        startCallListening(me)
        initialDone = false

        startSupportListening(me)
        startGroupListening(me)
        val ref = Repo.rtdb.getReference("userChats/$me")
        val l = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val first = !initialDone
                initialDone = true
                for (c in snap.children) {
                    val other = c.key ?: continue
                    val lastAt = (c.child("lastAt").value as? Number)?.toLong() ?: continue
                    val chatId = Repo.chatId(me, other)
                    val has = prefs.contains(chatId)
                    if (!has && first) {
                        // أول مرة نشوف المحادثة على هذا الجهاز: ما نطلع إشعار للتاريخ القديم
                        prefs.edit().putLong(chatId, lastAt).apply()
                        continue
                    }
                    if (lastAt <= prefs.getLong(chatId, 0L)) continue   // مثلاً تصفير عداد القراءة
                    trigger(chatId, lastAt, me, if (first) 0L else 300L)
                }
            }

            override fun onCancelled(e: DatabaseError) {
                // خطأ نهائي (مثلاً صلاحيات): نعيد المحاولة بعد دقيقة
                stopListening()
                scope.launch {
                    delay(60_000)
                    if (Repo.auth.currentUser?.uid == me) startListening()
                }
            }
        }
        listenRef = ref
        listener = l
        ref.addValueEventListener(l)
    }

    private fun trigger(chatId: String, lastAt: Long, me: String, delayMs: Long) {
        scope.launch {
            if (delayMs > 0) delay(delayMs)
            locks.getOrPut(chatId) { Mutex() }.withLock {
                try {
                    process(chatId, lastAt, me, 0)
                } catch (_: Exception) {
                }
            }
        }
    }

    private suspend fun process(chatId: String, lastAt: Long, me: String, attempt: Int) {
        var cursor = prefs.getLong(chatId, 0L)
        if (lastAt <= cursor) return

        val snap = Repo.rtdb.getReference("chats/$chatId/messages")
            .orderByChild("createdAt")
            .startAt((cursor + 1).toDouble())
            .limitToFirst(10)
            .get().await()

        if (!snap.hasChildren()) {
            if (attempt < 3) {
                delay(1500L * (attempt + 1))
                process(chatId, lastAt, me, attempt + 1)
            }
            return
        }
        for (doc in snap.children) {
            val t = (doc.child("createdAt").value as? Number)?.toLong() ?: continue
            cursor = t
            prefs.edit().putLong(chatId, t).apply()

            val from = doc.child("from").value as? String ?: continue
            if (from == me || doc.child("deleted").value == true) continue
            val text = doc.child("text").value as? String ?: continue
            if (AppState.foreground) continue            // التطبيق مفتوح: الرسالة تبين بالمحادثة
            val mid = doc.key
            if (mid != null && !ChatNotifier.claim("m:$mid")) continue   // FCM سبقنا وطلّع الإشعار
            ChatNotifier.show(applicationContext, from, text)
        }
    }

    companion object {
        private const val NOTIF_ID = 7001
        private const val MAX_GROUPS = 100

        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, MessageListenerService::class.java))
            } catch (_: Exception) {
            }
        }

        /** مرة وحدة: يطلب من أندرويد استثناء التطبيق من توفير البطارية (حتى ما تنقتل الخدمة) */
        fun askBatteryExemptionOnce(ctx: Context) {
            try {
                val p = ctx.getSharedPreferences("notif_cursor", Context.MODE_PRIVATE)
                if (p.getBoolean("battery_asked", false)) return
                val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (pm.isIgnoringBatteryOptimizations(ctx.packageName)) return
                p.edit().putBoolean("battery_asked", true).apply()
                ctx.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${ctx.packageName}")
                    }
                )
            } catch (_: Exception) {
            }
        }
    }
}
