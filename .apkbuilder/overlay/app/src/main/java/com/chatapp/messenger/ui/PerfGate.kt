package com.chatapp.messenger.ui

import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.delay

/**
 * ============================================================================
 *  PerfGate: المرجع الوحيد لقرار "هل نحرّك الخلفيات الآن؟"
 * ============================================================================
 * المشكلة: كل خلفية متحركة (blur/lens تحتها زجاج) كل تحديث لها = إعادة حساب كل قطع الزجاج الي فوقها.
 * الحل: الخلفية تتحرك فقط إذا كل الشروط صحيحة:
 *   1) التطبيق بالواجهة.
 *   2) المستخدم ما لمس الشاشة ولا مرر آخر [SETTLE_MS] (حتى التمرير والسحب والـ fling يكون الزجاج ثابت = بدون إعادة حساب).
 *   3) ما كو انتقال صفحات جاري ([hold]): لحظة فتح/إغلاق الشاشات هي الأثقل، فنجمّد الخلفية خلالها.
 * وإذا الجهاز ضعيف فعلاً ([PerfMonitor] شاف تقطيع مستمر) يدخل "وضع خفيف": الخلفية أبطأ، والـ blur أقل.
 */
object PerfGate {
    private const val SETTLE_MS = 350L

    @Volatile private var lastInteractionNs = 0L
    @Volatile private var holdUntilNs = 0L

    /** وضع خفيف تلقائي (State: تغييره يعيد رسم الزجاج مرة وحدة بالـ blur الجديد) */
    var lite by mutableStateOf(false)
        internal set

    /** يُستدعى من لمس/تمرير المستخدم */
    fun markInteraction() { lastInteractionNs = System.nanoTime() }

    /** يجمّد حركة الخلفيات [ms] ملّي ثانية (يستعمل وقت انتقال الشاشات) */
    fun hold(ms: Long) {
        val until = System.nanoTime() + ms * 1_000_000L
        if (until > holdUntilNs) holdUntilNs = until
    }

    /** هل مسموح تتحرك الخلفيات هسه؟ (قراءة سريعة بدون حالة compose؛ تُستعمل من حلقات الساعة) */
    val ambientRunning: Boolean
        get() {
            if (!com.chatapp.messenger.AppState.foreground) return false
            val now = System.nanoTime()
            if (now < holdUntilNs) return false
            return now - lastInteractionNs > SETTLE_MS * 1_000_000L
        }

    internal val scrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            markInteraction(); return Offset.Zero
        }
        override suspend fun onPreFling(available: Velocity): Velocity {
            markInteraction(); return Velocity.Zero
        }
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            markInteraction(); return Velocity.Zero
        }
    }
}

/**
 * يوضع مرة وحدة على جذر التطبيق: يراقب اللمس والتمرير (بدون ما يستهلك أي حدث) ويخبر [PerfGate].
 * الإصبع الواقف بدون حركة ما يحسب تفاعل (ما يجي حدث)، فالخلفية ترجع تتحرك بعد لحظة.
 */
fun Modifier.perfTracker(): Modifier = this
    .nestedScroll(PerfGate.scrollConnection)
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                if (e.changes.any { it.pressed }) PerfGate.markInteraction()
            }
        }
    }

/**
 * تخفيف اللاق: الشاشة الي تنغطّى بشاشة كاملة ومعتمة فوقها ما لها داعي ترسم نفسها (blur/lens/خلفيات).
 * بعد ما تخلص حركة الدخول ([settleMs]) نوقف رسم الشاشة التحتانية بالكامل (التركيب والحالة يبقون: scroll/نص/تبويب).
 * بمجرد ما [covered] ترجع false ترجع ترسم فوراً بنفس الإطار (بدون وميض) حتى حركة الرجوع تبين كاملة.
 */
@Composable
fun Modifier.hideWhenCovered(covered: Boolean, settleMs: Long = 520L): Modifier {
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(covered) {
        if (covered) { delay(settleMs); settled = true } else settled = false
    }
    val skip = covered && settled
    return if (skip) this.drawWithContent { /* لا نرسم شي */ } else this
}

/**
 * ساعة بطيئة للخلفيات الي تسبح ببطء (دورة 36-40 ثانية): تتحدث 10 مرات بالثانية بدل كل فريم.
 * تتوقف حسب [PerfGate] (خلفية/لمس/تمرير/انتقال)، وتكمل من نفس المكان بدون قفزة
 * (نجمع الزمن خطوة خطوة بدل ما نحسبه من وقت البداية، حتى التوقف ما يسبب "نطة" عند الاستئناف).
 */
@Composable
fun rememberSlowPhase(periodMs: Int, stepMs: Long = 100L, active: Boolean = true): State<Float> {
    val st = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    LaunchedEffect(periodMs, active) {
        if (!active) return@LaunchedEffect
        val twoPi = (2.0 * Math.PI)
        var last = System.nanoTime()
        while (true) {
            // بالوضع الخفيف نبطّئ الخلفية للنص (الحركة بطيئة أصلاً فما تنلاحظ)
            delay(if (PerfGate.lite) stepMs * 2 else stepMs)
            val now = System.nanoTime()
            val dtMs = ((now - last) / 1_000_000L).coerceAtMost(stepMs * 6)
            last = now
            if (!PerfGate.ambientRunning) continue
            val next = st.floatValue + (dtMs.toDouble() / periodMs * twoPi)
            st.floatValue = (next % twoPi).toFloat()
        }
    }
    return st
}

/**
 * مؤشر 0..1 ذهاب وإياب خطي (نفس infiniteRepeatable + RepeatMode.Reverse) بس بدل 60 تحديث بالثانية:
 * تحديث كل [stepMs] وبنفس قواعد [PerfGate]. للخلفيات الي داخل layerBackdrop بالذات.
 */
@Composable
fun rememberGatedPingPong(halfPeriodMs: Int, stepMs: Long = 50L, active: Boolean = true, initial: Float = 0f): State<Float> {
    val st = remember { androidx.compose.runtime.mutableFloatStateOf(initial) }
    val clock = remember { longArrayOf((initial * halfPeriodMs).toLong()) }
    LaunchedEffect(halfPeriodMs, active) {
        if (!active) return@LaunchedEffect
        val full = halfPeriodMs * 2L
        var last = System.nanoTime()
        while (true) {
            delay(if (PerfGate.lite) stepMs * 2 else stepMs)
            val now = System.nanoTime()
            val dtMs = ((now - last) / 1_000_000L).coerceAtMost(stepMs * 6)
            last = now
            if (!PerfGate.ambientRunning) continue
            clock[0] = (clock[0] + dtMs) % full
            val c = clock[0]
            st.floatValue = if (c < halfPeriodMs) c.toFloat() / halfPeriodMs else (full - c).toFloat() / halfPeriodMs
        }
    }
    return st
}

/**
 * مراقب الأداء: يقيس تقطيع الفريمات الحقيقي (FrameMetrics) وإذا استمر التقطيع عدة نوافذ ورا بعض
 * يفعّل [PerfGate.lite] (blur أقل + خلفيات أبطأ) لباقي الجلسة. يتجاهل أول 8 ثواني (إقلاع/تجميع shaders).
 */
object PerfMonitor {
    private const val WINDOW = 90            // فريم لكل نافذة قياس
    private const val BAD_RATIO = 0.25f      // نسبة فريمات متقطعة تعتبر النافذة سيئة
    private const val BAD_WINDOWS = 3        // نوافذ سيئة متتالية قبل التخفيف
    private const val WARMUP_MS = 8_000L

    private var listener: Window.OnFrameMetricsAvailableListener? = null
    private var attachedTo: Window? = null
    private var startNs = 0L
    private var frames = 0
    private var janky = 0
    private var badRun = 0

    fun attach(window: Window) {
        detach()
        startNs = System.nanoTime()
        frames = 0; janky = 0; badRun = 0
        val l = Window.OnFrameMetricsAvailableListener { w, fm, _ ->
            if (System.nanoTime() - startNs < WARMUP_MS * 1_000_000L) return@OnFrameMetricsAvailableListener
            if (fm.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return@OnFrameMetricsAvailableListener
            val hz = w.decorView.display?.refreshRate ?: 60f
            val budgetNs = 1_000_000_000f / hz
            frames++
            if (fm.getMetric(FrameMetrics.TOTAL_DURATION) > budgetNs * 1.5f) janky++
            if (frames >= WINDOW) {
                val bad = janky.toFloat() / frames > BAD_RATIO
                badRun = if (bad) badRun + 1 else 0
                frames = 0; janky = 0
                if (badRun >= BAD_WINDOWS && !PerfGate.lite) PerfGate.lite = true
            }
        }
        listener = l
        attachedTo = window
        window.addOnFrameMetricsAvailableListener(l, Handler(Looper.getMainLooper()))
    }

    fun detach() {
        val l = listener ?: return
        try { attachedTo?.removeOnFrameMetricsAvailableListener(l) } catch (_: Exception) {}
        listener = null
        attachedTo = null
    }
}
