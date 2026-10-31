package com.chatapp.messenger.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.chatapp.messenger.R
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * نسخة طبق الأصل من حركة الفيديو (قائمة iOS التي تتحول من الزر).
 *
 * الشكل (الـ silhouette) مأخوذ إطاراً إطاراً من الفيديو نفسه (30 إطار/ثانية) ومخزّن كنقاط في
 * res/raw/group_morph.bin: 43 إطار × 160 نقطة. كل إطار فيه شكل الزر ← القطرة ← المستطيل الممطوط ← التجاوز ← الاستقرار
 * بنفس حواف الفيديو الخشنة (roughen edges) وبنفس التوقيت. بين الإطارات نعمل interpolation فتطلع الحركة سلسة بأي معدل شاشة.
 *
 * فوق الشكل نركّب نفس طبقات الفيديو:
 *  1) drop shadow   2) glass fill + overlay (بلور الخلفية + طبقة بيضاء تخف مع الكبر)
 *  3) المحتوى يتمدد مع صندوق الشكل ويكون ضبابي (مسحوب أفقياً) ثم يتوضح   4) rim/border الخفيف
 *
 * progress = رقم الإطار (0 = على الزر، FRAMES-1 = مستقرة). يملكه MainScreen.
 */
private const val FRAMES = 43
private const val PTS = 160
private const val STEP_MS = 33          // 30fps مثل الفيديو
private const val START_FRAME = 2f      // أول إطار فعلي بالحركة (الضغطة)
private const val SETTLED = 22f         // من هذا الإطار الشكل ثابت تقريباً
private const val VBTN_R = 296f         // يمين زر الفيديو
private const val VBTN_T = 75f          // أعلى زر الفيديو
private const val VBTN_SIZE = 42f       // قطر زر الفيديو

private var morphCache: FloatArray? = null
private fun morphData(ctx: Context): FloatArray {
    morphCache?.let { return it }
    val bytes = ctx.resources.openRawResource(R.raw.group_morph).use { it.readBytes() }
    val sb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
    val out = FloatArray(sb.remaining())
    for (i in out.indices) out[i] = sb.get(i) / 16f
    morphCache = out
    return out
}

private class PathShape(val path: Path) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(path)
}

private class Geo(val u: Float, val btnR: Float, val btnB: Float, val w: Float, val rtl: Boolean) {
    fun x(vx: Float): Float {
        val x = btnR - (VBTN_R - vx) * u
        return if (rtl) w - x else x
    }
    fun y(vy: Float): Float = btnB - (vy - VBTN_T) * u
}

private class Fr(
    val path: Path,
    val minX: Float, val minY: Float, val maxX: Float, val maxY: Float,
    val cx: Float, val cy: Float
) {
    val w get() = maxX - minX
    val h get() = maxY - minY
    val midX get() = (minX + maxX) / 2f
    val midY get() = (minY + maxY) / 2f
}

private fun frame(d: FloatArray, g: Geo, f: Float): Fr {
    val ff = f.coerceIn(0f, FRAMES - 1f)
    val i0 = ff.toInt().coerceAtMost(FRAMES - 1)
    val i1 = min(i0 + 1, FRAMES - 1)
    val a = if (i0 == FRAMES - 1) 0f else ff - i0
    val xs = FloatArray(PTS)
    val ys = FloatArray(PTS)
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    var sx = 0f; var sy = 0f
    for (j in 0 until PTS) {
        val o0 = (i0 * PTS + j) * 2
        val o1 = (i1 * PTS + j) * 2
        val vx = d[o0] + (d[o1] - d[o0]) * a
        val vy = d[o0 + 1] + (d[o1 + 1] - d[o0 + 1]) * a
        val px = g.x(vx); val py = g.y(vy)
        xs[j] = px; ys[j] = py
        minX = min(minX, px); maxX = max(maxX, px)
        minY = min(minY, py); maxY = max(maxY, py)
        sx += px; sy += py
    }
    val p = Path()
    p.moveTo((xs[PTS - 1] + xs[0]) / 2f, (ys[PTS - 1] + ys[0]) / 2f)
    for (j in 0 until PTS) {
        val n = (j + 1) % PTS
        p.quadraticTo(xs[j], ys[j], (xs[j] + xs[n]) / 2f, (ys[j] + ys[n]) / 2f)
    }
    p.close()
    return Fr(p, minX, minY, maxX, maxY, sx / PTS, sy / PTS)
}

/** جدول خطي بسيط: نقاط (إطار -> قيمة) */
private fun table(f: Float, ks: FloatArray, vs: FloatArray): Float {
    if (f <= ks[0]) return vs[0]
    for (i in 1 until ks.size) {
        if (f <= ks[i]) {
            val t = (f - ks[i - 1]) / (ks[i] - ks[i - 1])
            return vs[i - 1] + (vs[i] - vs[i - 1]) * t
        }
    }
    return vs[vs.size - 1]
}

// طبقة المحتوى (ui text + warp) مأخوذة من الفيديو: ظهور + ضبابية تتوضح (X = سحب أفقي، Y = عمودي)
private val C_K = floatArrayOf(6f, 7f, 8f, 9f, 10f, 11f, 12f, 13f, 14f, 15f, 16f, 17f)
private val C_A = floatArrayOf(0f, .15f, .30f, .35f, .80f, 1f, 1f, 1f, 1f, 1f, 1f, 1f)
private val C_BX = floatArrayOf(14f, 14f, 20f, 24f, 18f, 26f, 8f, 3.5f, 1.8f, 1f, .5f, 0f)
private val C_BY = floatArrayOf(10f, 10f, 10f, 8f, 12f, 1.5f, 3f, 2f, 1.2f, .8f, .5f, 0f)
// أيقونة الزر تذوب أول الحركة
private val I_K = floatArrayOf(4f, 5f, 6f, 7f)
private val I_A = floatArrayOf(1f, 1f, .6f, 0f)

@Composable
fun GroupCreatePanel(
    progress: Animatable<Float, AnimationVector1D>,
    open: Boolean,
    backdrop: Backdrop,
    dark: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {}
) {
    LaunchedEffect(open) {
        if (open) {
            if (progress.value < START_FRAME) progress.snapTo(START_FRAME)
            val ms = ((FRAMES - 1f - progress.value) * STEP_MS).toInt().coerceAtLeast(1)
            progress.animateTo(FRAMES - 1f, tween(ms, easing = LinearEasing))
        } else {
            // الإغلاق = نفس الحركة معكوسة (نقفز لأول إطار مستقر حتى ما ننتظر الذيل الثابت)
            if (progress.value > SETTLED) progress.snapTo(SETTLED)
            val ms = ((progress.value - START_FRAME) * STEP_MS).toInt().coerceAtLeast(1)
            progress.animateTo(START_FRAME, tween(ms, easing = LinearEasing))
            progress.snapTo(0f)
        }
    }

    val f = progress.value
    if (f <= 0.001f && !open) return

    val ctx = LocalContext.current
    val d = remember { morphData(ctx) }
    val density = LocalDensity.current
    val realDir = LocalLayoutDirection.current
    val rtl = realDir == LayoutDirection.Rtl

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier.fillMaxSize()) {
            val wPx = constraints.maxWidth.toFloat()
            val hPx = constraints.maxHeight.toFloat()
            val dp1 = density.density
            val navB = WindowInsets.navigationBars.getBottom(density).toFloat()
            val topI = WindowInsets.statusBars.getTop(density).toFloat()
            val margin = 8f * dp1
            // مكان الزر (نفس هوامش MainScreen: end=20dp, bottom=112dp فوق شريط التنقل)
            val btnR = wPx - 20f * dp1
            val btnB = hPx - navB - 112f * dp1
            // مقياس الحركة: الزر 56dp = 42 وحدة بالفيديو. نصغّره فقط إذا الشاشة ما تكفي
            val uFull = 56f * dp1 / VBTN_SIZE
            val u = min(uFull, min((btnR - margin) / (VBTN_R - 40f), (btnB - topI - margin) / (560.5f - VBTN_T)))
            val g = Geo(u, btnR, btnB, wPx, rtl)

            val fr = frame(d, g, f)
            val fin = frame(d, g, FRAMES - 1f)

            // طبقة الشكل/الزجاج
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (open) Modifier.pointerInput(fin.minX, fin.minY) {
                            detectTapGestures { o ->
                                val inside = o.x in fin.minX..fin.maxX && o.y in fin.minY..fin.maxY
                                if (!inside) onDismiss()
                            }
                        } else Modifier
                    )
                    // 1) drop shadow: حلقة ناعمة خارج الشكل فقط (مثل طبقة shape drop shadow)
                    .drawBehind {
                        clipPath(fr.path, ClipOp.Difference) {
                            val n = 16
                            for (i in n downTo 1) {
                                val dist = 24f * u * i / n
                                drawPath(
                                    fr.path,
                                    Color.Black.copy(alpha = 0.0085f),
                                    style = Stroke(width = dist * 2f)
                                )
                            }
                        }
                    }
                    // 2) glass fill + overlay
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { PathShape(fr.path) },
                        effects = {
                            vibrancy()
                            blur(16f.dp.toPx())
                        },
                        onDrawSurface = {
                            // الفيديو: التعبئة أشد وهي صغيرة (≈0.74) وتخف للمستقر (≈0.555)
                            val s = (fr.h / max(fin.h, 1f)).coerceIn(0f, 1.2f)
                            val vid = if (s < 0.2f) 0.74f
                            else 0.74f + (0.555f - 0.74f) * ((s - 0.2f) / 0.75f).coerceIn(0f, 1f)
                            if (dark) drawRect(Color.Black.copy(alpha = (0.38f + (vid - 0.555f) * 0.5f).coerceIn(0f, 1f)))
                            else drawRect(Color.White.copy(alpha = (0.70f + (vid - 0.555f) * 0.75f).coerceIn(0f, 1f)))
                        }
                    )
                    // 3) المحتوى مقصوص بالشكل + 4) rim/border فوقه
                    .drawWithContent {
                        clipPath(fr.path) { this@drawWithContent.drawContent() }
                        val rim = if (dark) 0.5f else 1f
                        clipPath(fr.path) {
                            drawPath(fr.path, Color.White.copy(alpha = 0.05f * rim), style = Stroke(width = 9f * u))
                            drawPath(fr.path, Color.White.copy(alpha = 0.07f * rim), style = Stroke(width = 5f * u))
                            drawPath(fr.path, Color.White.copy(alpha = 0.10f * rim), style = Stroke(width = 2f * u))
                        }
                        drawPath(fr.path, Color.White.copy(alpha = 0.65f), style = Stroke(width = 0.8f * dp1))
                    }
            ) {
                // أيقونة الزر (تتحرك مع مركز الشكل وتذوب)
                val iconA = table(f, I_K, I_A)
                if (iconA > 0.01f) {
                    val iconPx = 26f * dp1
                    Icon(
                        AppIcons.GroupAdd, null,
                        tint = if (dark) Color.White else Color.Black,
                        modifier = Modifier
                            .offset { IntOffset((fr.cx - iconPx / 2f).roundToInt(), (fr.cy - iconPx / 2f).roundToInt()) }
                            .size(26.dp)
                            .graphicsLayer { alpha = iconA }
                    )
                }

                // المحتوى: مرسوم بحجمه النهائي ثم يتمدد/يتقلص مع صندوق الشكل (مثل طبقة ui text + warp)
                val ca = table(f, C_K, C_A)
                if (ca > 0.005f) {
                    val bx = table(f, C_K, C_BX) * u
                    val by = table(f, C_K, C_BY) * u
                    Box(
                        Modifier
                            .offset { IntOffset(fin.minX.roundToInt(), fin.minY.roundToInt()) }
                            .size(
                                with(density) { fin.w.toDp() },
                                with(density) { fin.h.toDp() }
                            )
                            .graphicsLayer {
                                transformOrigin = TransformOrigin.Center
                                scaleX = (fr.w / max(fin.w, 1f)).coerceAtLeast(0.01f)
                                scaleY = (fr.h / max(fin.h, 1f)).coerceAtLeast(0.01f)
                                translationX = fr.midX - fin.midX
                                translationY = fr.midY - fin.midY
                                alpha = ca
                                renderEffect = if (bx > 0.3f || by > 0.3f)
                                    BlurEffect(max(bx, 0.01f), max(by, 0.01f), TileMode.Decal) else null
                            }
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides realDir) { content() }
                    }
                }
            }
        }
    }
}
