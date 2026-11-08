package com.chatapp.messenger

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * محرك المكالمات الصوتية (مجاني): WebRTC للصوت، Realtime Database للإشارة (calls/{id})،
 * والـ Worker يرسل FCM للرنين. مكالمة وحدة بنفس الوقت.
 */
object CallManager {
    enum class Phase { IDLE, OUTGOING, INCOMING, CONNECTING, ACTIVE, ENDED }

    var phase by mutableStateOf(Phase.IDLE); private set
    var peerUid by mutableStateOf(""); private set
    var peerName by mutableStateOf(""); private set
    var endReason by mutableStateOf(""); private set
    var muted by mutableStateOf(false); private set
    var speaker by mutableStateOf(false); private set
    var activeSince by mutableStateOf(0L); private set

    var callId: String = ""; private set
    private var isCaller = false
    private var appCtx: Context? = null
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var factory: PeerConnectionFactory? = null
    private var pc: PeerConnection? = null
    private var audioTrack: AudioTrack? = null
    private var audioSource: org.webrtc.AudioSource? = null

    private var stateListener: ValueEventListener? = null
    private var iceListener: ChildEventListener? = null
    private var timeoutJob: Job? = null
    private var disconnectJob: Job? = null
    private var setupJob: Job? = null

    private var remoteSet = false          // الوصف البعيد انطبق فعلاً (نكدر نضيف مرشحات)
    private var answerHandled = false
    private val pendingRemoteIce = mutableListOf<IceCandidate>()
    private val pendingLocalIce = mutableListOf<IceCandidate>()
    private var canSendIce = false
    private var finishing = false

    private fun ref(path: String) = Repo.rtdb.getReference(path)
    val isBusy: Boolean get() = phase != Phase.IDLE && phase != Phase.ENDED

    // ------------------------------------------------------------------ بدء مكالمة (المتصل)

    fun startCall(ctx: Context, peer: String, name: String) {
        if (isBusy) return
        val me = Repo.auth.currentUser?.uid ?: return
        resetState()
        appCtx = ctx.applicationContext
        isCaller = true
        peerUid = peer
        peerName = name
        phase = Phase.OUTGOING
        val id = ref("calls").push().key
        if (id == null) { finish("تعذّر بدء المكالمة"); return }
        callId = id
        CallService.start(appCtx!!, name)
        setupAudioRouting()

        setupJob = scope.launch {
            try {
                val pc = createPeer()
                val offer = pc.createOfferSuspend()
                pc.setLocalSuspend(offer)
                ref("calls/$id").setValue(
                    mapOf(
                        "from" to me, "to" to peer, "state" to "ringing",
                        "createdAt" to System.currentTimeMillis(), "offer" to offer.description
                    )
                ).await()
                ref("incomingCalls/$peer").setValue(
                    mapOf("callId" to id, "from" to me, "at" to System.currentTimeMillis())
                ).await()
                Push.call(peer, id)
                listenCall(id)
                flushLocalIce()
                timeoutJob = launch {
                    delay(CallConfig.RING_TIMEOUT_MS)
                    if (phase == Phase.OUTGOING) endLocal("missed", "ما رد")
                }
            } catch (e: Exception) {
                finish("تعذّر بدء المكالمة")
            }
        }
    }

    // ------------------------------------------------------------------ مكالمة واردة (المستلم)

    /**
     * يتحقق إن المكالمة لسه ترن ويجهّز الحالة. يرجع true إذا لازم نظهر رنين.
     * (يُستدعى من خدمة FCM)
     */
    private val incomingMutex = kotlinx.coroutines.sync.Mutex()

    suspend fun prepareIncoming(ctx: Context, id: String, from: String, nameHint: String): Boolean =
        incomingMutex.withLock { prepareIncomingLocked(ctx, id, from, nameHint) }

    private suspend fun prepareIncomingLocked(ctx: Context, id: String, from: String, nameHint: String): Boolean {
        val me = Repo.auth.currentUser?.uid ?: return false
        if (id == callId && phase == Phase.INCOMING) return false
        if (isBusy) {
            runCatching { ref("calls/$id/state").setValue("busy").await() }
            return false
        }
        val snap = withTimeoutOrNull(6000) { runCatching { ref("calls/$id").get().await() }.getOrNull() } ?: return false
        if (snap.child("to").getValue(String::class.java) != me) return false
        if (snap.child("from").getValue(String::class.java) != from) return false
        if (snap.child("state").getValue(String::class.java) != "ringing") return false
        val created = snap.child("createdAt").getValue(Long::class.java) ?: 0L
        if (kotlin.math.abs(System.currentTimeMillis() - created) > 60_000L) return false

        resetState()
        appCtx = ctx.applicationContext
        isCaller = false
        callId = id
        peerUid = from
        peerName = nameHint.ifBlank { "مستخدم" }
        phase = Phase.INCOMING
        listenCall(id)
        timeoutJob = scope.launch {
            delay(CallConfig.RING_TIMEOUT_MS + 5000)
            if (phase == Phase.INCOMING) finish("فاتتك مكالمة")
        }
        return true
    }

    fun accept(ctx: Context) {
        if (phase != Phase.INCOMING) return
        val me = Repo.auth.currentUser?.uid ?: return
        val id = callId
        appCtx = ctx.applicationContext
        phase = Phase.CONNECTING
        timeoutJob?.cancel()
        CallNotifier.cancel(appCtx!!)
        CallService.start(appCtx!!, peerName)
        setupAudioRouting()

        setupJob = scope.launch {
            try {
                val snap = ref("calls/$id").get().await()
                val state = snap.child("state").getValue(String::class.java)
                val offer = snap.child("offer").getValue(String::class.java)
                if (state != "ringing" || offer.isNullOrEmpty()) {
                    finish("المكالمة انتهت"); return@launch
                }
                val pc = createPeer()
                pc.setRemoteSuspend(SessionDescription(SessionDescription.Type.OFFER, offer))
                remoteSet = true
                val answer = pc.createAnswerSuspend()
                pc.setLocalSuspend(answer)
                ref("calls/$id").updateChildren(mapOf("answer" to answer.description, "state" to "active")).await()
                runCatching { ref("incomingCalls/$me").removeValue().await() }
                canSendIce = true
                flushLocalIce()
                drainRemoteIce()
                // إذا ما اتصل الصوت خلال 25 ثانية نسكّر
                timeoutJob = launch {
                    delay(25_000)
                    if (phase == Phase.CONNECTING) endLocal("ended", "فشل الاتصال")
                }
            } catch (e: Exception) {
                endLocal("ended", "فشل الاتصال")
            }
        }
    }

    /** رفض من الواجهة */
    fun decline() {
        if (phase != Phase.INCOMING) return
        endLocal("declined", "")
    }

    /** رفض من زر الإشعار (قد تكون الحالة مو محمّلة بعد) */
    fun declineById(ctx: Context, id: String) {
        if (phase == Phase.INCOMING && callId == id) { decline(); return }
        CallNotifier.cancel(ctx)
        scope.launch { runCatching { ref("calls/$id/state").setValue("declined").await() } }
    }

    /** المتصل ألغى (وصلنا FCM call_cancel) */
    fun onRemoteCancel(id: String) {
        if (id == callId && phase == Phase.INCOMING) finish("فاتتك مكالمة")
    }

    /** زر إنهاء */
    fun hangup() {
        when (phase) {
            Phase.INCOMING -> decline()
            Phase.IDLE, Phase.ENDED -> {}
            else -> endLocal("ended", "")
        }
    }

    fun toggleMute() {
        muted = !muted
        audioTrack?.setEnabled(!muted)
    }

    fun toggleSpeaker() {
        speaker = !speaker
        applySpeaker()
    }

    // ------------------------------------------------------------------ WebRTC

    private fun ensureFactory(ctx: Context): PeerConnectionFactory {
        factory?.let { return it }
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(ctx).createInitializationOptions()
        )
        val adm = JavaAudioDeviceModule.builder(ctx)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        val f = PeerConnectionFactory.builder().setAudioDeviceModule(adm).createPeerConnectionFactory()
        factory = f
        return f
    }

    private suspend fun createPeer(): PeerConnection {
        val ctx = appCtx ?: throw IllegalStateException("no ctx")
        val f = ensureFactory(ctx)
        val cfg = PeerConnection.RTCConfiguration(CallConfig.iceServers()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val peer = f.createPeerConnection(cfg, object : PeerConnection.Observer {
            override fun onIceCandidate(c: IceCandidate) { main.post { sendLocalIce(c) } }
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState) { main.post { onIceState(s) } }
            override fun onSignalingChange(s: PeerConnection.SignalingState) {}
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState) {}
            override fun onIceCandidatesRemoved(c: Array<out IceCandidate>) {}
            override fun onAddStream(s: MediaStream) {}
            override fun onRemoveStream(s: MediaStream) {}
            override fun onDataChannel(d: org.webrtc.DataChannel) {}
            override fun onRenegotiationNeeded() {}
        }) ?: throw IllegalStateException("pc null")

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        }
        val src = f.createAudioSource(constraints)
        val track = f.createAudioTrack("audio0", src)
        track.setEnabled(!muted)
        peer.addTrack(track, listOf("stream0"))
        audioSource = src
        audioTrack = track
        pc = peer
        return peer
    }

    private fun onIceState(s: PeerConnection.IceConnectionState) {
        when (s) {
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> {
                disconnectJob?.cancel()
                if (phase == Phase.CONNECTING || phase == Phase.OUTGOING || phase == Phase.INCOMING) {
                    timeoutJob?.cancel()
                    phase = Phase.ACTIVE
                    activeSince = System.currentTimeMillis()
                }
            }
            PeerConnection.IceConnectionState.DISCONNECTED -> {
                // انقطاع مؤقت: نعطيه 10 ثواني يرجع
                disconnectJob?.cancel()
                disconnectJob = scope.launch {
                    delay(10_000)
                    if (phase == Phase.ACTIVE) endLocal("ended", "انقطع الاتصال")
                }
            }
            PeerConnection.IceConnectionState.FAILED -> {
                if (phase == Phase.ACTIVE || phase == Phase.CONNECTING) endLocal("ended", "فشل الاتصال")
            }
            else -> {}
        }
    }

    // ------------------------------------------------------------------ ICE عبر Realtime Database

    private fun myIcePath(id: String) = "calls/$id/ice/${if (isCaller) "from" else "to"}"
    private fun theirIcePath(id: String) = "calls/$id/ice/${if (isCaller) "to" else "from"}"

    private fun sendLocalIce(c: IceCandidate) {
        if (finishing || callId.isEmpty()) return
        if (!canSendIce) { pendingLocalIce += c; return }
        writeIce(c)
    }

    private fun writeIce(c: IceCandidate) {
        val json = JSONObject().put("m", c.sdpMid).put("i", c.sdpMLineIndex).put("s", c.sdp).toString()
        runCatching { ref(myIcePath(callId)).push().setValue(json) }
    }

    private fun flushLocalIce() {
        canSendIce = true
        val list = pendingLocalIce.toList()
        pendingLocalIce.clear()
        list.forEach { writeIce(it) }
    }

    private fun drainRemoteIce() {
        val p = pc ?: return
        val list = pendingRemoteIce.toList()
        pendingRemoteIce.clear()
        list.forEach { p.addIceCandidate(it) }
    }

    private fun listenCall(id: String) {
        val sl = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                if (callId != id || finishing) return
                val state = s.child("state").getValue(String::class.java) ?: return
                when (state) {
                    "ended", "declined", "busy", "missed" -> {
                        val reason = when {
                            state == "declined" -> "رفض المكالمة"
                            state == "busy" -> "مشغول"
                            state == "missed" && !isCaller -> "فاتتك مكالمة"
                            state == "missed" -> "ما رد"
                            phase == Phase.INCOMING -> "فاتتك مكالمة"
                            else -> ""
                        }
                        if (isCaller) cleanupCaller(id, peerUid)
                        finish(reason)
                        return
                    }
                }
                if (isCaller && !answerHandled) {
                    val ans = s.child("answer").getValue(String::class.java)
                    if (!ans.isNullOrEmpty() && pc != null) {
                        answerHandled = true
                        phase = Phase.CONNECTING
                        timeoutJob?.cancel()
                        timeoutJob = scope.launch {
                            delay(25_000)
                            if (phase == Phase.CONNECTING) endLocal("ended", "فشل الاتصال")
                        }
                        scope.launch {
                            runCatching {
                                pc?.setRemoteSuspend(SessionDescription(SessionDescription.Type.ANSWER, ans))
                                remoteSet = true
                                drainRemoteIce()
                            }.onFailure { endLocal("ended", "فشل الاتصال") }
                        }
                    }
                }
            }

            override fun onCancelled(e: DatabaseError) {}
        }
        stateListener = sl
        ref("calls/$id").addValueEventListener(sl)

        // مرشحات الطرف الثاني تبدأ بس بعد ما نصير نعرف نستقبلها
        startIceListener(id)
    }

    private fun startIceListener(id: String) {
        val il = object : ChildEventListener {
            override fun onChildAdded(s: DataSnapshot, prev: String?) {
                val txt = s.getValue(String::class.java) ?: return
                val c = runCatching {
                    val j = JSONObject(txt)
                    IceCandidate(j.getString("m"), j.getInt("i"), j.getString("s"))
                }.getOrNull() ?: return
                if (remoteSet && pc != null) pc?.addIceCandidate(c) else pendingRemoteIce += c
            }

            override fun onChildChanged(s: DataSnapshot, prev: String?) {}
            override fun onChildRemoved(s: DataSnapshot) {}
            override fun onChildMoved(s: DataSnapshot, prev: String?) {}
            override fun onCancelled(e: DatabaseError) {}
        }
        iceListener = il
        ref(theirIcePath(id)).addChildEventListener(il)
    }

    // ------------------------------------------------------------------ إنهاء

    /** نكتب الحالة بقاعدة البيانات ثم ننهي محلياً */
    private fun endLocal(state: String, reason: String) {
        if (finishing) return
        val id = callId
        val peer = peerUid
        val wasOutgoing = phase == Phase.OUTGOING
        if (id.isNotEmpty()) {
            scope.launch {
                runCatching { ref("calls/$id/state").setValue(state).await() }
                // الـ Worker يلغي الرنين بس إذا المكالمة سكّرت فعلاً (لهذا كتبنا الحالة أولاً)
                if (isCaller && wasOutgoing) Push.callCancel(peer, id)
            }
            if (isCaller) cleanupCaller(id, peer)
        }
        finish(reason)
    }

    /** المتصل ينظّف الرنين وعقدة المكالمة بعد ما تنتهي (بعد تأخير حتى الطرف الثاني يشوف الحالة) */
    private fun cleanupCaller(id: String, peer: String) {
        scope.launch {
            runCatching { ref("incomingCalls/$peer").removeValue().await() }
            delay(8000)
            runCatching { ref("calls/$id").removeValue().await() }
        }
    }

    private fun finish(reason: String) {
        if (finishing) return
        finishing = true
        timeoutJob?.cancel()
        disconnectJob?.cancel()
        val ctx = appCtx
        val id = callId
        stateListener?.let { runCatching { ref("calls/$id").removeEventListener(it) } }
        iceListener?.let { runCatching { ref(theirIcePath(id)).removeEventListener(it) } }
        stateListener = null
        iceListener = null
        runCatching { pc?.close() }
        runCatching { pc?.dispose() }
        runCatching { audioTrack?.dispose() }
        runCatching { audioSource?.dispose() }
        pc = null; audioTrack = null; audioSource = null
        if (ctx != null) {
            CallNotifier.cancel(ctx)
            CallService.stop(ctx)
            releaseAudioRouting()
        }
        endReason = reason
        phase = Phase.ENDED
        // نخلي الشاشة تعرض سبب الإنهاء ثانية ونص ثم نرجع للحالة الفارغة
        scope.launch {
            delay(if (reason.isBlank()) 400 else 1600)
            if (phase == Phase.ENDED) { phase = Phase.IDLE; resetState() }
        }
    }

    private fun resetState() {
        setupJob?.cancel()
        remoteSet = false
        answerHandled = false
        pendingRemoteIce.clear()
        pendingLocalIce.clear()
        canSendIce = false
        finishing = false
        muted = false
        speaker = false
        activeSince = 0L
        endReason = ""
        if (phase == Phase.IDLE) { callId = ""; peerUid = ""; peerName = "" }
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

    private fun applySpeaker() {
        val am = appCtx?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        am.isSpeakerphoneOn = speaker
    }

    private fun releaseAudioRouting() {
        val am = appCtx?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        am.isSpeakerphoneOn = prevSpeaker
        am.mode = prevMode
    }

    /** يفتح شاشة المكالمة */
    fun openUi(ctx: Context) {
        ctx.startActivity(Intent(ctx, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ------------------------------------------------------------------ مغلّفات coroutine لـ SdpObserver

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
