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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
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

/** عنقود لون من البانر: درجة اللون (h) + التشبع + السطوع + نسبة حضوره بين الألوان الملونة */
private class HueCluster(val h: Float, val s: Float, val v: Float, val share: Float)

/**
 * يحلل صورة البانر ويطلع منها ألوان الخلفية تلقائياً (بدون مكتبات خارجية):
 * - يحلل الجزء الظاهر فعلاً من البانر فقط (نفس قص ContentScale.Crop + موضع التركيز)،
 *   لأن الألوان اللي خارج القص ما يشوفها المستخدم وكانت تغيّر الخلفية بدون سبب.
 * - يجمع البكسلات الملونة في عناقيد حسب درجة اللون (متوسط دائري، فما ينكسر اللون الأحمر عند 0°/360°).
 * - اللون الثاني والثالث ما ينضافون إلا إذا لهم حضور حقيقي في البانر؛ غير كذا نشتقهم
 *   من اللون الأساسي بإزاحة بسيطة، فتبقى الخلفية بنفس جو البانر (بانر أزرق = خلفية زرقاء).
 * الخلفية تبقى غامقة بما يكفي حتى تبقى الكتابة البيضاء مقروءة.
 *
 * [boxAspect] = عرض/ارتفاع البانر المعروض (0 = نحلل الصورة كاملة)،
 * [focusX]/[focusY] = نفس قيم BiasAlignment المستعملة في عرض البانر (من -1 إلى 1).
 */
internal fun extractBannerPalette(
    img: ImageBitmap,
    boxAspect: Float = 0f,
    focusX: Float = 0f,
    focusY: Float = 0f
): AccountPalette {
    val src = img.asAndroidBitmap()

    // الجزء الظاهر من الصورة (نفس حساب ContentScale.Crop مع BiasAlignment)
    var sx = 0; var sy = 0; var sw = src.width; var sh = src.height
    if (boxAspect > 0f && sw > 0 && sh > 0) {
        val imgAspect = sw.toFloat() / sh
        if (imgAspect > boxAspect) {
            sw = (sh * boxAspect).toInt().coerceIn(1, src.width)
            sx = ((src.width - sw) * (focusX.coerceIn(-1f, 1f) + 1f) / 2f).toInt()
        } else {
            sh = (sw / boxAspect).toInt().coerceIn(1, src.height)
            sy = ((src.height - sh) * (focusY.coerceIn(-1f, 1f) + 1f) / 2f).toInt()
        }
    }
    val n = 64
    val small = android.graphics.Bitmap.createBitmap(n, n, android.graphics.Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(small).drawBitmap(
        src,
        android.graphics.Rect(sx, sy, sx + sw, sy + sh),
        android.graphics.Rect(0, 0, n, n),
        android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    )
    val px = IntArray(n * n)
    small.getPixels(px, 0, n, 0, 0, n, n)
    small.recycle()

    val bins = 24
    val wSum = FloatArray(bins)
    val cosSum = FloatArray(bins)
    val sinSum = FloatArray(bins)
    val sSum = FloatArray(bins)
    val vSum = FloatArray(bins)
    var avgV = 0f
    val hsvArr = FloatArray(3)
    for ((i, c) in px.withIndex()) {
        android.graphics.Color.colorToHSV(c, hsvArr)
        val h = hsvArr[0]; val s = hsvArr[1]; val v = hsvArr[2]
        avgV += v
        // البكسلات الرمادية أو الغامقة جداً ما تحدد لون الخلفية
        if (s < 0.15f || v < 0.15f) continue
        // الجزء السفلي من البانر هو اللي يذوب بالخلفية، فوزنه أعلى شوي
        val y = (i / n) / (n - 1f)
        val w = s * (0.35f + v) * (0.6f + 0.8f * y)
        val b = ((h / 360f) * bins).toInt().coerceIn(0, bins - 1)
        val rad = h * (PI.toFloat() / 180f)
        wSum[b] += w; cosSum[b] += cos(rad) * w; sinSum[b] += sin(rad) * w
        sSum[b] += s * w; vSum[b] += v * w
    }
    avgV /= px.size

    val total = wSum.sum()
    if (total < 0.0026f * px.size) {
        // بانر رمادي/أسود/أبيض تقريباً: خلفية محايدة مائلة لدرجة سطوع الصورة
        val g = (0.10f + avgV * 0.12f)
        return AccountPalette(
            Color(g, g, g * 1.1f), Color(g + 0.07f, g + 0.07f, (g + 0.07f) * 1.1f),
            listOf(Color(0xFF5A6477), Color(0xFF3F4757), Color(0xFF6B7488)), hue = 0f
        )
    }

    // نختار أقوى 3 عناقيد ألوان مختلفة (فرق 45° على الأقل)، وكل لون ثانوي لازم يكون له حضور فعلي
    val clusters = ArrayList<HueCluster>()
    while (clusters.size < 3) {
        var best = -1; var bestW = 0f
        for (b in 0 until bins) {
            val w = wSum[(b + bins - 1) % bins] + wSum[b] + wSum[(b + 1) % bins]
            if (w > bestW) { bestW = w; best = b }
        }
        if (best < 0) break
        val minShare = when (clusters.size) { 0 -> 0f; 1 -> 0.15f; else -> 0.12f }
        if (bestW / total < minShare) break
        var w = 0f; var cs = 0f; var sn = 0f; var s = 0f; var v = 0f
        for (d in -2..2) {
            val b = (best + d + bins) % bins
            w += wSum[b]; cs += cosSum[b]; sn += sinSum[b]; s += sSum[b]; v += vSum[b]
        }
        if (w <= 0f) break
        val hue = ((atan2(sn, cs) * 180f / PI.toFloat()) + 360f) % 360f
        clusters.add(HueCluster(hue, s / w, v / w, w / total))
        for (d in -3..3) {
            val b = (best + d + bins) % bins
            wSum[b] = 0f; cosSum[b] = 0f; sinSum[b] = 0f; sSum[b] = 0f; vSum[b] = 0f
        }
    }
    if (clusters.isEmpty()) return NeutralAccountPalette

    val main = clusters[0]
    // إذا البانر ما فيه لون ثاني/ثالث واضح نشتقهم من الأساسي بإزاحة بسيطة (تدرج من نفس العائلة)
    fun analog(off: Float) = HueCluster(((main.h + off) % 360f + 360f) % 360f, main.s, main.v, 0f)
    val second = clusters.getOrNull(1) ?: analog(20f)
    val third = clusters.getOrNull(2) ?: analog(-20f)
    fun blob(c: HueCluster) = hsv(c.h, max(c.s, 0.55f), max(c.v, 0.80f))
    return AccountPalette(
        top = hsv(main.h, (main.s * 0.9f).coerceIn(0.35f, 0.85f), 0.17f),
        bottom = hsv(second.h, (second.s * 0.9f).coerceIn(0.35f, 0.85f), 0.27f),
        blobs = listOf(blob(main), blob(second), blob(third)),
        hue = main.h
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
    val phaseState: State<Float> = rememberGatedPingPong(16_000, active = running, initial = 0.5f)
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
