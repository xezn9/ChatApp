package com.chatapp.messenger.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * ألوان خلفية Account: تدرج من فوق لتحت + 3 بقع ضوء تتحرك ببطء.
 * [hue] = اللون الأساسي (يستعمل حتى الرندم ما يعيد نفس اللون مرتين ورا بعض).
 */
internal class AccountPalette(
    val top: Color,
    val bottom: Color,
    val blobs: List<Color>,
    val hue: Float = 0f
)

/** لون محايد قصير المدة أثناء تحليل البانر */
internal val NeutralAccountPalette = AccountPalette(
    Color(0xFF0B0E14), Color(0xFF151A24),
    listOf(Color(0xFF3A4660), Color(0xFF2A3347), Color(0xFF46506B))
)

private fun hsv(h: Float, s: Float, v: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))

private fun hueDist(a: Float, b: Float): Float {
    val d = abs(a - b) % 360f
    return min(d, 360f - d)
}

/** خلفية عشوائية جديدة (بدون بانر). [avoidHue] = لون الخلفية السابقة حتى تتغير فعلاً */
internal fun randomAccountPalette(avoidHue: Float? = null): AccountPalette {
    var h = Random.nextFloat() * 360f
    var tries = 0
    while (avoidHue != null && hueDist(h, avoidHue) < 50f && tries++ < 20) h = Random.nextFloat() * 360f
    val spread = 35f + Random.nextFloat() * 30f
    return AccountPalette(
        top = hsv(h - 12f, 0.65f, 0.17f),
        bottom = hsv(h + 14f, 0.70f, 0.28f),
        blobs = listOf(hsv(h, 0.78f, 0.98f), hsv(h + spread, 0.72f, 0.96f), hsv(h - spread, 0.80f, 0.95f)),
        hue = h
    )
}

/**
 * يحلل صورة البانر ويطلع منها ألوان الخلفية تلقائياً (بدون مكتبات خارجية):
 * نصغّر الصورة، نجمع البكسلات الملونة حسب درجة اللون، ونأخذ أقوى 3 ألوان مختلفة.
 * الخلفية تبقى غامقة بما يكفي حتى تبقى الكتابة البيضاء مقروءة.
 */
internal fun extractBannerPalette(img: ImageBitmap): AccountPalette {
    val src = img.asAndroidBitmap()
    val small = android.graphics.Bitmap.createScaledBitmap(src, 48, 48, true)
    val px = IntArray(48 * 48)
    small.getPixels(px, 0, 48, 0, 0, 48, 48)
    if (small !== src) small.recycle()

    val bins = 24
    val wSum = FloatArray(bins)
    val hSum = FloatArray(bins)
    val sSum = FloatArray(bins)
    val vSum = FloatArray(bins)
    var avgV = 0f
    val hsvArr = FloatArray(3)
    for (c in px) {
        android.graphics.Color.colorToHSV(c, hsvArr)
        val h = hsvArr[0]; val s = hsvArr[1]; val v = hsvArr[2]
        avgV += v
        // البكسلات الرمادية أو الغامقة جداً ما تحدد لون الخلفية
        if (s < 0.18f || v < 0.15f) continue
        val w = s * (0.35f + v)
        val b = ((h / 360f) * bins).toInt().coerceIn(0, bins - 1)
        wSum[b] += w; hSum[b] += h * w; sSum[b] += s * w; vSum[b] += v * w
    }
    avgV /= px.size

    // أقوى الألوان (مع ابتعاد 40° على الأقل بين لون ولون)
    val order = (0 until bins).filter { wSum[it] > 0f }.sortedByDescending { wSum[it] }
    val picks = ArrayList<Triple<Float, Float, Float>>() // h, s, v
    for (b in order) {
        val h = hSum[b] / wSum[b]
        if (picks.none { hueDist(it.first, h) < 40f }) picks.add(Triple(h, sSum[b] / wSum[b], vSum[b] / wSum[b]))
        if (picks.size == 3) break
    }
    val total = wSum.sum()
    if (picks.isEmpty() || total < 6f) {
        // بانر رمادي/أسود/أبيض تقريباً: خلفية محايدة مائلة لدرجة سطوع الصورة
        val g = (0.10f + avgV * 0.12f)
        return AccountPalette(
            Color(g, g, g * 1.1f), Color(g + 0.07f, g + 0.07f, (g + 0.07f) * 1.1f),
            listOf(Color(0xFF5A6477), Color(0xFF3F4757), Color(0xFF6B7488)), hue = 0f
        )
    }
    val main = picks[0]
    val second = picks.getOrNull(1) ?: Triple((main.first + 32f) % 360f, main.second, main.third)
    val third = picks.getOrNull(2) ?: Triple(((main.first - 32f) + 360f) % 360f, main.second, main.third)
    fun blob(t: Triple<Float, Float, Float>) = hsv(t.first, max(t.second, 0.55f), max(t.third, 0.80f))
    return AccountPalette(
        top = hsv(main.first, (main.second * 0.9f).coerceIn(0.35f, 0.85f), 0.17f),
        bottom = hsv(second.first, (second.second * 0.9f).coerceIn(0.35f, 0.85f), 0.27f),
        blobs = listOf(blob(main), blob(second), blob(third)),
        hue = main.first
    )
}

/** خلفية تبويب Account: ألوانها تتبدل بنعومة عند تغيّر البانر أو عند الدخول للتبويب */
@Composable
internal fun AccountBackground(palette: AccountPalette, modifier: Modifier = Modifier, running: Boolean = true) {
    val spec = tween<Color>(800)
    val top by animateColorAsState(palette.top, spec, label = "accTop")
    val bottom by animateColorAsState(palette.bottom, spec, label = "accBottom")
    val b0 by animateColorAsState(palette.blobs[0], spec, label = "accB0")
    val b1 by animateColorAsState(palette.blobs[1], spec, label = "accB1")
    val b2 by animateColorAsState(palette.blobs[2], spec, label = "accB2")
    // الحركة تشتغل فقط والتبويب ظاهر (توفير للبطارية). القراءة داخل الرسم فقط حتى ما ينعاد التركيب كل إطار
    val phaseState: State<Float> = if (running) {
        rememberInfiniteTransition(label = "accBg").animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse),
            label = "accBgPhase"
        )
    } else remember { mutableStateOf(0.5f) }
    Box(
        modifier.background(Brush.verticalGradient(listOf(top, bottom))).drawBehind {
            val phase = phaseState.value
            val w = size.width
            val h = size.height
            fun blob(c: Color, cx: Float, cy: Float, r: Float) = drawCircle(
                Brush.radialGradient(listOf(c.copy(alpha = 0.50f), Color.Transparent), center = Offset(cx, cy), radius = r),
                r, Offset(cx, cy)
            )
            blob(b0, w * (0.10f + 0.40f * phase), h * (0.18f + 0.08f * phase), w * 0.80f)
            blob(b1, w * (0.95f - 0.40f * phase), h * (0.50f + 0.10f * phase), w * 0.75f)
            blob(b2, w * (0.20f + 0.35f * phase), h * (0.95f - 0.12f * phase), w * 0.85f)
        }
    )
}
