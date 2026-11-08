package com.chatapp.messenger

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * مكالمات جماعية صوتية (mesh): كل مشارك يتصل مباشرة بكل واحد من الباقين (WebRTC).
 * الإشارة عبر Realtime Database:
 *   groupCalls/{gid}/active           = {by, startedAt}   (يكتبه الي بدأ المكالمة، والـ Worker يتأكد منه للرنين)
 *   groupCalls/{gid}/peers/{uid}      = وقت الانضمام      (حضور؛ ينحذف تلقائياً عند الانقطاع)
 *   groupCalls/{gid}/sig/{to}/{from}  = {offer|answer, t, ice/...}
 * الي ينضم متأخر هو الي يرسل الـ offer للباقين. الحد الأقصى 8 مشاركين.
 */
object GroupCallManager {
    enum class Phase { IDLE, ACTIVE, ENDED }

    const val MAX_PARTICIPANTS = 8

    class Member(val uid: String) {
        var name by mutableStateOf("")
        var avatar by mutableStateOf<Bitmap?>(null)
        var connected by mutableStateOf(false)
        /** المايك مكتوم (يتزامن عبر groupCalls/{gid}/muted/{uid}) */
        var muted by mutableStateOf(false)
        /** مستوى الصوت 0..1 (للأنيميشن حول الصورة) */
        var level by mutableFloatStateOf(0f)
    }

    var phase by mutableStateOf(Phase.IDLE); private set
    var gid by mutableStateOf(""); private set
    var groupName by mutableStateOf(""); private set
    var muted by mutableStateOf(false); private set
    var speaker by mutableStateOf(false); private set
    var activeSince by mutableStateOf(0L); private set
    var endReason by mutableStateOf(""); private set
    val members = mutableStateListOf<Member>()
    var hostUid by mutableStateOf(""); private set
    var groupAvatar by mutableStateOf(""); private set
    var groupAvatarGifV by mutableStateOf(0L); private set
    var selfMember by mutableStateOf<Member?>(null); private set
    var notice by mutableStateOf(""); private set

    fun clearNotice() { notice = "" }

    /** صاحب المكالمة (المضيف) */
    val host: Member?
        get() = if (hostUid.isNotBlank() && hostUid == myUid) selfMember else members.firstOrNull { it.uid == hostUid }
    val iAmHost: Boolean get() = hostUid.isNotBlank() && hostUid == myUid

    val isBusy: Boolean get() = phase == Phase.ACTIVE

    private var appCtx: Context? = null
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var myUid = ""   // (يُقرأ من host/iAmHost)
    private var myT = 0L
    private var factory: PeerConnectionFactory? = null
    private var audioSource: org.webrtc.AudioSource? = null
    private var localTrack: AudioTrack? = null
    private var peersListener: ChildEventListener? = null
    private var joinJob: Job? = null
    private var activeListener: ValueEventListener? = null
    private var ctlListener: ValueEventListener? = null
    private var mutedListener: ChildEventListener? = null
    private var ctlInit = false
    private var ctlSeen = -1L
    private var finishing = false
    private var isStarter = false        // أنا الي بدأت المكالمة (ما كان فيه أحد)
    private var everHadOthers = false    // انضم لها أحد غيري (لو لا = مكالمة فائتة للكل)
    private val links = HashMap<String, Link>()

    private fun ref(path: String) = Repo.rtdb.getReference(path)

    // ------------------------------------------------------------------ مراقبة وجود مكالمة بالمجموعة

    /** عدد المشاركين الحاليين بمكالمة المجموعة (0 = ما فيه مكالمة) */
    fun peersCountFlow(groupId: String): Flow<Int> = callbackFlow {
        val r = ref("groupCalls/$groupId/peers")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(s.childrenCount.toInt()) }
            override fun onCancelled(e: DatabaseError) { trySend(0) }
        }
        r.addValueEventListener(l)
        awaitClose { r.removeEventListener(l) }
    }

    /** هل المكالمة لسه فيها ناس؟ (للتأكد قبل إظهار الرنين) */
    suspend fun isAlive(groupId: String): Boolean {
        val s = withTimeoutOrNull(6000) {
            runCatching { ref("groupCalls/$groupId/peers").get().await() }.getOrNull()
        } ?: return false
        return s.childrenCount > 0
    }

    // ------------------------------------------------------------------ انضمام / بدء

    /** يبدأ مكالمة جديدة إذا ما فيه وحدة، أو ينضم للموجودة */
    fun join(ctx: Context, groupId: String, name: String) {
        if (isBusy || CallManager.isBusy) return
        val me = Repo.auth.currentUser?.uid ?: return
        reset()
        appCtx = ctx.applicationContext
        myUid = me
        gid = groupId
        groupName = name
        phase = Phase.ACTIVE
        activeSince = System.currentTimeMillis()
        CallNotifier.cancelGroup(appCtx!!)
        CallService.start(appCtx!!, name, group = true)
        setupAudioRouting()

        joinJob = scope.launch {
            try {
                val f = CallManager.ensureFactory(appCtx!!)
                factory = f
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                }
                val src = f.createAudioSource(constraints)
                val track = f.createAudioTrack("gaudio0", src)
                track.setEnabled(!muted)
                audioSource = src
                localTrack = track

                val existing = ref("groupCalls/$groupId/peers").get().await()
                    .children.mapNotNull { it.key }.filter { it != me }
                if (existing.size >= MAX_PARTICIPANTS - 1) {
                    finish("المكالمة ممتلئة (${MAX_PARTICIPANTS} كحد أقصى)")
                    return@launch
                }
                val kicked = runCatching {
                    ref("groupCalls/$groupId/kicked/$me").get().await().exists()
                }.getOrDefault(false)
                if (kicked) {
                    finish("طردك المضيف من هذه المكالمة")
                    return@launch
                }
                val starter = existing.isEmpty()
                isStarter = starter
                everHadOthers = !starter
                myT = System.currentTimeMillis()
                activeSince = myT
                selfMember = Member(me).also { loadProfile(it) }
                loadGroupAvatar(groupId)

                val myRef = ref("groupCalls/$groupId/peers/$me")
                myRef.onDisconnect().removeValue()
                myRef.setValue(myT).await()
                if (starter) {
                    ref("groupCalls/$groupId/active")
                        .setValue(mapOf("by" to me, "startedAt" to myT)).await()
                    Push.groupCall(groupId)
                }
                startActiveListener(groupId)
                startCtlListener(groupId, me)
                startMutedListener(groupId)
                publishMuted()
                startPeersListener(groupId)
                // قياس مستوى الصوت كل 300ms لأنيميشن الكلام
                launch {
                    while (true) {
                        delay(300)
                        pollStats()
                    }
                }
            } catch (e: Exception) {
                finish("تعذّر الانضمام للمكالمة")
            }
        }
    }

    private fun startActiveListener(g: String) {
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                hostUid = s.child("by").getValue(String::class.java).orEmpty()
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        activeListener = l
        ref("groupCalls/$g/active").addValueEventListener(l)
    }

    /** أوامر المضيف لي: كتم / طرد */
    private fun startCtlListener(g: String, me: String) {
        ctlInit = false
        ctlSeen = -1L
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val a = s.child("a").getValue(String::class.java)
                val at = s.child("at").getValue(Long::class.java) ?: 0L
                if (!ctlInit) { ctlInit = true; ctlSeen = at; return }   // أوامر قديمة قبل انضمامي: نتجاهلها
                if (a == null || at == ctlSeen) return
                ctlSeen = at
                when (a) {
                    "kick" -> leave("طردك المضيف من المكالمة")
                    "mute" -> {
                        if (!muted) { muted = true; localTrack?.setEnabled(false); publishMuted() }
                        notice = "المضيف كتم المايك حقك"
                    }
                }
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        ctlListener = l
        ref("groupCalls/$g/ctl/$me").addValueEventListener(l)
    }

    private fun loadGroupAvatar(g: String) {
        scope.launch {
            runCatching {
                val m = ref("groups/$g/meta").get().await()
                groupAvatar = m.child("avatar").getValue(String::class.java).orEmpty()
                groupAvatarGifV = m.child("avatarGifV").getValue(Long::class.java) ?: 0L
            }
        }
    }

    // ------------------------------------------------------------------ حالة الكتم المشتركة

    /** ننشر حالة كتم مايكي حتى الكل (وخصوصاً المضيف) يشوف الزر أحمر */
    private fun publishMuted() {
        val g = gid
        val me = myUid
        if (g.isBlank() || me.isBlank()) return
        val r = ref("groupCalls/$g/muted/$me")
        if (muted) {
            runCatching { r.onDisconnect().removeValue() }
            runCatching { r.setValue(true) }
        } else {
            runCatching { r.onDisconnect().cancel() }
            runCatching { r.removeValue() }
        }
    }

    private fun startMutedListener(g: String) {
        val l = object : ChildEventListener {
            private fun apply(s: DataSnapshot, v: Boolean) {
                val uid = s.key ?: return
                if (uid == myUid) return
                members.firstOrNull { it.uid == uid }?.muted = v
            }
            override fun onChildAdded(s: DataSnapshot, prev: String?) { apply(s, s.getValue(Boolean::class.java) == true) }
            override fun onChildChanged(s: DataSnapshot, prev: String?) { apply(s, s.getValue(Boolean::class.java) == true) }
            override fun onChildRemoved(s: DataSnapshot) { apply(s, false) }
            override fun onChildMoved(s: DataSnapshot, prev: String?) {}
            override fun onCancelled(e: DatabaseError) {}
        }
        mutedListener = l
        ref("groupCalls/$g/muted").addChildEventListener(l)
    }

    // ------------------------------------------------------------------ صلاحيات المضيف

    fun muteMember(uid: String) {
        if (!iAmHost) return
        val g = gid
        scope.launch {
            runCatching {
                ref("groupCalls/$g/ctl/$uid")
                    .setValue(mapOf("a" to "mute", "at" to System.currentTimeMillis())).await()
            }
        }
    }

    fun kickMember(uid: String) {
        if (!iAmHost) return
        val g = gid
        scope.launch {
            runCatching { ref("groupCalls/$g/kicked/$uid").setValue(true).await() }
            runCatching {
                ref("groupCalls/$g/ctl/$uid")
                    .setValue(mapOf("a" to "kick", "at" to System.currentTimeMillis())).await()
            }
            runCatching { ref("groupCalls/$g/peers/$uid").removeValue().await() }
        }
    }

    // ------------------------------------------------------------------ مستوى الصوت

    private fun norm(v: Double): Float {
        val x = (v * 5.0).coerceIn(0.0, 1.0).toFloat()
        return if (x < 0.08f) 0f else x
    }

    private fun pollStats() {
        if (finishing) return
        var first = true
        for (l in links.values.toList()) {
            l.pollStats(first)
            first = false
        }
        if (links.isEmpty()) selfMember?.level = 0f
    }

    private fun startPeersListener(groupId: String) {
        val l = object : ChildEventListener {
            override fun onChildAdded(s: DataSnapshot, prev: String?) { onPeer(s) }
            override fun onChildChanged(s: DataSnapshot, prev: String?) { onPeer(s) }
            override fun onChildRemoved(s: DataSnapshot) { s.key?.let { removePeer(it) } }
            override fun onChildMoved(s: DataSnapshot, prev: String?) {}
            override fun onCancelled(e: DatabaseError) {}
        }
        peersListener = l
        ref("groupCalls/$groupId/peers").addChildEventListener(l)
    }

    private fun onPeer(s: DataSnapshot) {
        if (finishing || phase != Phase.ACTIVE) return
        val uid = s.key ?: return
        if (uid == myUid) return
        val t = s.getValue(Long::class.java) ?: return
        val old = links[uid]
        if (old != null && old.theirT == t) return
        if (old != null) removePeer(uid)
        // الي انضم أخيراً هو الي يرسل الـ offer
        val offerer = myT > t || (myT == t && myUid > uid)
        everHadOthers = true
        val m = Member(uid)
        members.add(m)
        loadProfile(m)
        // الحالة ممكن توصل قبل ما يظهر العضو بالقائمة: نقراها مرة ثانية
        scope.launch {
            runCatching {
                m.muted = ref("groupCalls/$gid/muted/$uid").get().await().getValue(Boolean::class.java) == true
            }
        }
        val link = Link(uid, t, offerer, m)
        links[uid] = link
        link.start()
    }

    private fun removePeer(uid: String) {
        links.remove(uid)?.close()
        members.removeAll { it.uid == uid }
    }

    // ------------------------------------------------------------------ اتصال مباشر مع مشارك واحد

    private class Link(val uid: String, val theirT: Long, val offerer: Boolean, val member: Member) {
        private val tag = if (offerer) myT else theirT
        private val inPath = "groupCalls/$gid/sig/$myUid/$uid"
        private val outPath = "groupCalls/$gid/sig/$uid/$myUid"
        private var pc: PeerConnection? = null
        private var sigL: ValueEventListener? = null
        private val seen = HashSet<String>()
        private var remoteSet = false
        private var handled = false
        private var canSend = false
        private val pendingRemote = mutableListOf<IceCandidate>()
        private val pendingLocal = mutableListOf<IceCandidate>()
        private var dcJob: Job? = null

        fun start() {
            scope.launch {
                try {
                    val p = createPeer(this@Link)
                    pc = p
                    listen()
                    if (offerer) {
                        val o = p.createOfferSuspend()
                        p.setLocalSuspend(o)
                        ref(outPath).setValue(mapOf("offer" to o.description, "t" to tag)).await()
                        flush()
                    }
                } catch (e: Exception) {
                    member.connected = false
                }
            }
        }

        private fun listen() {
            val l = object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    if (s.child("t").getValue(Long::class.java) != tag) return
                    if (offerer) {
                        val ans = s.child("answer").getValue(String::class.java)
                        if (!ans.isNullOrEmpty() && !handled) {
                            handled = true
                            scope.launch {
                                runCatching {
                                    pc?.setRemoteSuspend(SessionDescription(SessionDescription.Type.ANSWER, ans))
                                    remoteSet = true
                                    drain()
                                }
                            }
                        }
                    } else {
                        val off = s.child("offer").getValue(String::class.java)
                        if (!off.isNullOrEmpty() && !handled) {
                            handled = true
                            scope.launch {
                                runCatching {
                                    val p = pc ?: return@runCatching
                                    p.setRemoteSuspend(SessionDescription(SessionDescription.Type.OFFER, off))
                                    remoteSet = true
                                    val a = p.createAnswerSuspend()
                                    p.setLocalSuspend(a)
                                    ref(outPath).setValue(mapOf("answer" to a.description, "t" to tag)).await()
                                    flush()
                                    drain()
                                }
                            }
                        }
                    }
                    for (c in s.child("ice").children) {
                        val k = c.key ?: continue
                        if (!seen.add(k)) continue
                        val txt = c.getValue(String::class.java) ?: continue
                        val cand = runCatching {
                            val j = JSONObject(txt)
                            IceCandidate(j.getString("m"), j.getInt("i"), j.getString("s"))
                        }.getOrNull() ?: continue
                        if (remoteSet && pc != null) pc?.addIceCandidate(cand) else pendingRemote += cand
                    }
                }

                override fun onCancelled(e: DatabaseError) {}
            }
            sigL = l
            ref(inPath).addValueEventListener(l)
        }

        private fun drain() {
            val p = pc ?: return
            val list = pendingRemote.toList()
            pendingRemote.clear()
            list.forEach { p.addIceCandidate(it) }
        }

        fun sendIce(c: IceCandidate) {
            if (finishing) return
            if (!canSend) { pendingLocal += c; return }
            write(c)
        }

        private fun write(c: IceCandidate) {
            val json = JSONObject().put("m", c.sdpMid).put("i", c.sdpMLineIndex).put("s", c.sdp).toString()
            runCatching { ref("$outPath/ice").push().setValue(json) }
        }

        private fun flush() {
            canSend = true
            val list = pendingLocal.toList()
            pendingLocal.clear()
            list.forEach { write(it) }
        }

        fun pollStats(wantLocal: Boolean) {
            val p = pc ?: return
            p.getStats { report ->
                var inLvl = 0.0
                var outLvl = 0.0
                for (st in report.statsMap.values) {
                    val kind = st.members["kind"] ?: st.members["mediaType"]
                    if (kind != "audio") continue
                    val lvl = (st.members["audioLevel"] as? Number)?.toDouble() ?: continue
                    when (st.type) {
                        "inbound-rtp" -> inLvl = maxOf(inLvl, lvl)
                        "media-source" -> outLvl = maxOf(outLvl, lvl)
                    }
                }
                main.post {
                    member.level = if (member.connected) norm(inLvl) else 0f
                    if (wantLocal) selfMember?.level = if (muted) 0f else norm(outLvl)
                }
            }
        }

        fun onIce(s: PeerConnection.IceConnectionState) {
            when (s) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> { dcJob?.cancel(); member.connected = true }
                PeerConnection.IceConnectionState.DISCONNECTED -> {
                    dcJob?.cancel()
                    dcJob = scope.launch { delay(8_000); member.connected = false }
                }
                PeerConnection.IceConnectionState.FAILED -> member.connected = false
                else -> {}
            }
        }

        fun close() {
            dcJob?.cancel()
            sigL?.let { runCatching { ref(inPath).removeEventListener(it) } }
            sigL = null
            runCatching { pc?.close() }
            runCatching { pc?.dispose() }
            pc = null
        }
    }

    private suspend fun createPeer(link: Link): PeerConnection {
        val f = factory ?: throw IllegalStateException("no factory")
        val cfg = PeerConnection.RTCConfiguration(CallConfig.iceServers()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val p = f.createPeerConnection(cfg, object : PeerConnection.Observer {
            override fun onIceCandidate(c: IceCandidate) { main.post { link.sendIce(c) } }
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState) { main.post { link.onIce(s) } }
            override fun onSignalingChange(s: PeerConnection.SignalingState) {}
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState) {}
            override fun onIceCandidatesRemoved(c: Array<out IceCandidate>) {}
            override fun onAddStream(s: MediaStream) {}
            override fun onRemoveStream(s: MediaStream) {}
            override fun onDataChannel(d: DataChannel) {}
            override fun onRenegotiationNeeded() {}
        }) ?: throw IllegalStateException("pc null")
        localTrack?.let { p.addTrack(it, listOf("gstream0")) }
        return p
    }

    // ------------------------------------------------------------------ تحكم

    fun toggleMute() {
        muted = !muted
        localTrack?.setEnabled(!muted)
        publishMuted()
    }

    fun toggleSpeaker() {
        speaker = !speaker
        val am = appCtx?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        am.isSpeakerphoneOn = speaker
    }

    /** مغادرة المكالمة. إذا كنت آخر واحد نحذف عقدة المكالمة بالكامل */
    fun leave(reason: String = "") {
        if (!isBusy) return
        val g = gid
        val me = myUid
        val wasHost = iAmHost
        // بدأت مكالمة جماعية وطلعت وما انضم لها أحد = "مكالمة جماعية فائتة" بالمجموعة
        if (isStarter && !everHadOthers && reason.isBlank()) {
            Repo.sendGroupCallLog(g, com.chatapp.messenger.ui.ChatCallLog.GROUP_MISSED)
        }
        scope.launch {
            runCatching { ref("groupCalls/$g/muted/$me").onDisconnect().cancel() }
            runCatching { ref("groupCalls/$g/muted/$me").removeValue().await() }
            runCatching { ref("groupCalls/$g/peers/$me").onDisconnect().cancel() }
            runCatching { ref("groupCalls/$g/peers/$me").removeValue().await() }
            val snap = runCatching { ref("groupCalls/$g/peers").get().await() }.getOrNull()
            if (snap != null && !snap.hasChildren()) {
                runCatching { ref("groupCalls/$g").removeValue().await() }
            } else if (wasHost && snap != null) {
                // المضيف طلع والمكالمة مستمرة: ننقل الإدارة لأقدم مشارك
                val next = snap.children.minByOrNull { it.getValue(Long::class.java) ?: Long.MAX_VALUE }?.key
                if (next != null) runCatching { ref("groupCalls/$g/active/by").setValue(next).await() }
            }
        }
        finish(reason)
    }

    private fun finish(reason: String) {
        if (finishing) return
        finishing = true
        joinJob?.cancel()
        val g = gid
        peersListener?.let { runCatching { ref("groupCalls/$g/peers").removeEventListener(it) } }
        peersListener = null
        activeListener?.let { runCatching { ref("groupCalls/$g/active").removeEventListener(it) } }
        activeListener = null
        ctlListener?.let { runCatching { ref("groupCalls/$g/ctl/$myUid").removeEventListener(it) } }
        ctlListener = null
        mutedListener?.let { runCatching { ref("groupCalls/$g/muted").removeEventListener(it) } }
        mutedListener = null
        links.values.toList().forEach { it.close() }
        links.clear()
        runCatching { localTrack?.dispose() }
        runCatching { audioSource?.dispose() }
        localTrack = null; audioSource = null
        appCtx?.let {
            CallService.stop(it)
            releaseAudioRouting()
        }
        endReason = reason
        phase = Phase.ENDED
        scope.launch {
            delay(if (reason.isBlank()) 400 else 1600)
            if (phase == Phase.ENDED) { phase = Phase.IDLE; reset() }
        }
    }

    private fun reset() {
        joinJob?.cancel()
        links.values.toList().forEach { it.close() }
        links.clear()
        members.clear()
        finishing = false
        isStarter = false
        everHadOthers = false
        muted = false
        speaker = false
        endReason = ""
        if (phase == Phase.IDLE) {
            gid = ""; groupName = ""; activeSince = 0L
            hostUid = ""; groupAvatar = ""; groupAvatarGifV = 0L; selfMember = null; notice = ""
        }
    }

    // ------------------------------------------------------------------ ملفات الأعضاء

    private fun loadProfile(m: Member) {
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val doc = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("profiles").document(m.uid).get().await()
                    val name = doc.getString("name").orEmpty()
                    val b64 = doc.getString("avatar").orEmpty()
                    var bmp: Bitmap? = null
                    if (b64.isNotBlank()) {
                        val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
                        val src = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (src != null) {
                            val side = minOf(src.width, src.height)
                            val sq = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
                            bmp = if (side > 256) Bitmap.createScaledBitmap(sq, 256, 256, true) else sq
                        }
                    }
                    name to bmp
                }.getOrNull()
            }
            if (r != null) { m.name = r.first; m.avatar = r.second }
        }
    }

    // ------------------------------------------------------------------ مسار الصوت

    private var prevMode = AudioManager.MODE_NORMAL
    private var prevSpeaker = false

    private fun setupAudioRouting() {
        val am = appCtx?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        prevMode = am.mode
        prevSpeaker = am.isSpeakerphoneOn
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        am.isSpeakerphoneOn = false
    }

    private fun releaseAudioRouting() {
        val am = appCtx?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        am.isSpeakerphoneOn = prevSpeaker
        am.mode = prevMode
    }

    // ------------------------------------------------------------------ مغلّفات SDP

    private abstract class SimpleSdp : SdpObserver {
        override fun onCreateSuccess(d: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(e: String?) {}
        override fun onSetFailure(e: String?) {}
    }

    private val audioConstraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
    }

    private suspend fun PeerConnection.createOfferSuspend(): SessionDescription =
        suspendCancellableCoroutine { k ->
            createOffer(object : SimpleSdp() {
                override fun onCreateSuccess(d: SessionDescription) { k.resume(d) }
                override fun onCreateFailure(e: String?) { k.resumeWithException(IllegalStateException(e ?: "offer")) }
            }, audioConstraints)
        }

    private suspend fun PeerConnection.createAnswerSuspend(): SessionDescription =
        suspendCancellableCoroutine { k ->
            createAnswer(object : SimpleSdp() {
                override fun onCreateSuccess(d: SessionDescription) { k.resume(d) }
                override fun onCreateFailure(e: String?) { k.resumeWithException(IllegalStateException(e ?: "answer")) }
            }, audioConstraints)
        }

    private suspend fun PeerConnection.setLocalSuspend(d: SessionDescription) =
        suspendCancellableCoroutine<Unit> { k ->
            setLocalDescription(object : SimpleSdp() {
                override fun onSetSuccess() { k.resume(Unit) }
                override fun onSetFailure(e: String?) { k.resumeWithException(IllegalStateException(e ?: "setLocal")) }
            }, d)
        }

    private suspend fun PeerConnection.setRemoteSuspend(d: SessionDescription) =
        suspendCancellableCoroutine<Unit> { k ->
            setRemoteDescription(object : SimpleSdp() {
                override fun onSetSuccess() { k.resume(Unit) }
                override fun onSetFailure(e: String?) { k.resumeWithException(IllegalStateException(e ?: "setRemote")) }
            }, d)
        }
}
