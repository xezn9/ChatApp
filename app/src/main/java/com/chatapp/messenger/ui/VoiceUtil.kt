package com.chatapp.messenger.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import kotlin.math.max
import kotlin.math.sqrt

/** ثوابت وأدوات الرسائل الصوتية */
object VoiceUtil {
    /** أقصى مدة للرسالة الصوتية: 5 دقائق */
    const val MAX_MS = 5 * 60_000L
    /** أقل من هذي المدة يُعتبر ضغطة بالغلط ويُتجاهل */
    const val MIN_MS = 700L
    /** عدد أعمدة الموجة المحفوظة مع الرسالة */
    const val WAVE_BARS = 40
    /** معرّف المشغّل أثناء الاستماع للتسجيل قبل الإرسال */
    const val PREVIEW_ID = "__voice_preview__"

    /** تسجيل جاهز: الملف + مدته + الموجة (كل حرف رقم 1..9 = ارتفاع عمود) */
    class Recorded(val file: File, val durMs: Long, val wave: String)
}

/** مسجّل صوت: AAC داخل m4a، مونو 16kHz بـ 32kbps (حوالي 240 كيلوبايت للدقيقة) */
class VoiceRecorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    private var file: File? = null
    private var startAt = 0L
    private val amps = ArrayList<Int>()

    /** يُستدعى إذا وصل التسجيل لأقصى مدة (التسجيل يتوقف تلقائياً) */
    var onMaxReached: (() -> Unit)? = null

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()

    fun start(): Boolean {
        cancel()
        val f = File(MediaUtil.mediaDir(ctx), "voice_${System.nanoTime()}.m4a")
        val r = newRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(16_000)
            r.setAudioEncodingBitRate(32_000)
            r.setMaxDuration(VoiceUtil.MAX_MS.toInt())
            r.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onMaxReached?.invoke()
            }
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            rec = r
            file = f
            startAt = SystemClock.elapsedRealtime()
            amps.clear()
            true
        } catch (e: Exception) {
            runCatching { r.release() }
            f.delete()
            false
        }
    }

    fun elapsedMs(): Long =
        if (rec == null) 0L else (SystemClock.elapsedRealtime() - startAt).coerceAtMost(VoiceUtil.MAX_MS)

    /** مستوى الصوت الحالي من 0 إلى 1 (يُستدعى بشكل دوري أثناء التسجيل، ويُحفظ لرسم الموجة النهائية) */
    fun sample(): Float {
        val a = runCatching { rec?.maxAmplitude ?: 0 }.getOrDefault(0)
        amps.add(a)
        return (sqrt(a / 32767f) * 1.6f).coerceIn(0.08f, 1f)
    }

    /** يوقف التسجيل. يرجع null إذا كان قصير جداً أو فشل */
    fun stop(): VoiceUtil.Recorded? {
        val r = rec ?: return null
        val f = file
        val dur = elapsedMs()
        rec = null
        file = null
        val stopped = try { r.stop(); true } catch (e: RuntimeException) { false }
        runCatching { r.release() }
        // إذا وصل لأقصى مدة المسجّل يكون وقّف نفسه، فـ stop() ممكن يرمي خطأ والملف سليم
        val ok = stopped || dur >= VoiceUtil.MAX_MS - 1500
        if (f == null || !ok || dur < VoiceUtil.MIN_MS || !f.exists() || f.length() == 0L) {
            f?.delete()
            return null
        }
        return VoiceUtil.Recorded(f, dur, buildWave())
    }

    /** يلغي التسجيل ويمسح الملف */
    fun cancel() {
        val r = rec ?: return
        val f = file
        rec = null
        file = null
        runCatching { r.stop() }
        runCatching { r.release() }
        f?.delete()
    }

    private fun buildWave(): String {
        val n = VoiceUtil.WAVE_BARS
        if (amps.isEmpty()) return "3".repeat(n)
        val out = FloatArray(n)
        for (i in 0 until n) {
            val from = i * amps.size / n
            val to = max(from + 1, (i + 1) * amps.size / n).coerceAtMost(amps.size)
            var m = 0
            for (j in from until to) m = max(m, amps[j])
            out[i] = sqrt(m / 32767f)
        }
        val peak = max(out.max(), 0.05f)
        return buildString {
            for (v in out) append(((v / peak).coerceIn(0f, 1f) * 9f).toInt().coerceIn(1, 9))
        }
    }
}

/**
 * مشغّل صوت واحد للتطبيق كله (رسالة وحدة تشتغل بالوقت).
 * الحالة (Compose State) تخلي الفقاعات وشريط المعاينة تتحدث لحالها.
 */
object VoicePlayer {
    /** المعرّف المحمّل بالمشغّل (يشتغل أو موقوف مؤقتاً). null = ما أكو شي */
    var playingId by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set

    private var mp: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val p = mp ?: return
            if (isPlaying) {
                positionMs = runCatching { p.currentPosition.toLong() }.getOrDefault(positionMs)
                handler.postDelayed(this, 50)
            }
        }
    }

    /** إذا نفس المعرّف: إيقاف/استئناف. غير هيچ: يبدأ من أول */
    fun toggle(id: String, file: File) {
        if (playingId == id && mp != null) {
            if (isPlaying) pause() else resume()
        } else {
            play(id, file)
        }
    }

    fun play(id: String, file: File) {
        stop()
        val p = MediaPlayer()
        try {
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            p.setDataSource(file.absolutePath)
            p.prepare()
            p.setOnCompletionListener { stop() }
            p.setOnErrorListener { _, _, _ -> stop(); true }
            mp = p
            playingId = id
            durationMs = p.duration.toLong().coerceAtLeast(0L)
            positionMs = 0L
            p.start()
            isPlaying = true
            handler.removeCallbacks(tick)
            handler.post(tick)
        } catch (e: Exception) {
            runCatching { p.release() }
            mp = null
            playingId = null
            isPlaying = false
            positionMs = 0L
            durationMs = 0L
        }
    }

    fun pause() {
        val p = mp ?: return
        runCatching { p.pause() }
        isPlaying = false
        handler.removeCallbacks(tick)
    }

    private fun resume() {
        val p = mp ?: return
        runCatching { p.start() }
        isPlaying = true
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    /** يقفز لنسبة [fraction] (0..1) من التسجيل. إذا مو محمّل يبدأ التشغيل أول */
    fun seekTo(id: String, file: File, fraction: Float) {
        if (playingId != id || mp == null) play(id, file)
        val p = mp ?: return
        if (durationMs <= 0L) return
        val t = (fraction.coerceIn(0f, 1f) * durationMs).toLong()
        runCatching { p.seekTo(t.toInt()) }
        positionMs = t
    }

    fun stop() {
        handler.removeCallbacks(tick)
        mp?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        mp = null
        playingId = null
        isPlaying = false
        positionMs = 0L
        durationMs = 0L
    }
}
