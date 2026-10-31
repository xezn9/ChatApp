package com.chatapp.messenger.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
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
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * حركة الفيديو (قائمة iOS تتحول من الزر) مأخوذة إطاراً إطاراً (res/raw/group_morph.bin: 43 إطار × 160 نقطة).
 *
 * - الإطارات الأصلية 30fps، ونعمل بينها interpolation مكعّب (Catmull-Rom) حتى تطلع سلسة على 60/90/120Hz.
 * - النافذة تنتهي بالمنتصف أفقياً (تنزاح من زاوية الزر للمنتصف أثناء الفتح).
 * - من إطار MORPH_END تتحول لزجاج Liquid Glass الحقيقي (blur + vibrancy + lens) مثل بقية التطبيق.
 * - زر (X) بالأعلى للإغلاق، والإغلاق = نفس الحركة معكوسة.
 * progress = رقم الإطار (0 = على الزر). يملكه MainScreen.
 */
private const val FRAMES = 43
private const val PTS = 160
private const val STEP_MS = 33
private const val START_FRAME = 2f
private const val SETTLED = 22f
private const val LENS_FROM = 12f       // من هنا يبدأ تدرّج الـ lens
private const val ROUND_FROM = 14f      // من هنا الشكل مستطيل مدوّر فنستعمل زجاج Rounded الحقيقي
private const val VBTN_R = 296f
private const val VBTN_T = 75f
private const val VBTN_SIZE = 42f
private const val CORNER = 36f          // نصف قطر زوايا النافذة بوحدات الفيديو

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

private class Geo(
    val d: FloatArray, val u: Float, val btnR: Float, val btnB: Float, val w: Float, val rtl: Boolean
) {
    fun x(vx: Float): Float {
        val x = btnR - (VBTN_R - vx) * u
        return if (rtl) w - x else x
    }
    fun y(vy: Float): Float = btnB - (vy - VBTN_T) * u

    var finH = 1f
    var shift = 0f

    init {
        var mnx = Float.MAX_VALUE; var mxx = -Float.MAX_VALUE
        var mny = Float.MAX_VALUE; var mxy = -Float.MAX_VALUE
        val base = (FRAMES - 1) * PTS * 2
        for (j in 0 until PTS) {
            val px = x(d[base + j * 2]); val py = y(d[base + j * 2 + 1])
            mnx = min(mnx, px); mxx = max(mxx, px); mny = min(mny, py); mxy = max(mxy, py)
        }
        finH = max(mxy - mny, 1f)
        shift = w / 2f - (mnx + mxx) / 2f     // لتصير النافذة بالمنتصف
    }
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

private fun cr(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
    val t2 = t * t; val t3 = t2 * t
    return 0.5f * ((2f * p1) + (-p0 + p2) * t + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 + (-p0 + 3f * p1 - 3f * p2 + p3) * t3)
}

private fun frame(g: Geo, f: Float): Fr {
    val d = g.d
    val ff = f.coerceIn(0f, FRAMES - 1f)
    val i1 = ff.toInt().coerceAtMost(FRAMES - 1)
    val a = if (i1 >= FRAMES - 1) 0f else ff - i1
    val i0 = max(i1 - 1, 0)
    val i2 = min(i1 + 1, FRAMES - 1)
    val i3 = min(i1 + 2, FRAMES - 1)
    val xs = FloatArray(PTS)
    val ys = FloatArray(PTS)
    var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    for (j in 0 until PTS) {
        val vx = cr(d[(i0 * PTS + j) * 2], d[(i1 * PTS + j) * 2], d[(i2 * PTS + j) * 2], d[(i3 * PTS + j) * 2], a)
        val vy = cr(d[(i0 * PTS + j) * 2 + 1], d[(i1 * PTS + j) * 2 + 1], d[(i2 * PTS + j) * 2 + 1], d[(i3 * PTS + j) * 2 + 1], a)
        xs[j] = g.x(vx); ys[j] = g.y(vy)
        minY = min(minY, ys[j]); maxY = max(maxY, ys[j])
    }
    // انزياح تدريجي للمنتصف مع كبر الشكل
    val s = ((maxY - minY) / g.finH).coerceIn(0f, 1f)
    val e = s * s * (3f - 2f * s)
    val dx = g.shift * e
    var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
    var sx = 0f; var sy = 0f
    for (j in 0 until PTS) {
        xs[j] += dx
        minX = min(minX, xs[j]); maxX = max(maxX, xs[j])
        sx += xs[j]; sy += ys[j]
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

private val C_K = floatArrayOf(6f, 7f, 8f, 9f, 10f, 11f, 12f, 13f, 14f, 15f, 16f, 17f)
private val C_A = floatArrayOf(0f, .15f, .30f, .35f, .80f, 1f, 1f, 1f, 1f, 1f, 1f, 1f)
private val C_BX = floatArrayOf(14f, 14f, 20f, 24f, 18f, 26f, 8f, 3.5f, 1.8f, 1f, .5f, 0f)
private val C_BY = floatArrayOf(10f, 10f, 10f, 8f, 12f, 1.5f, 3f, 2f, 1.2f, .8f, .5f, 0f)
private val I_K = floatArrayOf(4f, 5f, 6f, 7f)
private val I_A = floatArrayOf(1f, 1f, .6f, 0f)
private val X_K = floatArrayOf(14f, 19f)
private val X_A = floatArrayOf(0f, 1f)

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
            if (progress.value > SETTLED) progress.snapTo(SETTLED)
            val ms = ((progress.value - START_FRAME) * STEP_MS).toInt().coerceAtLeast(1)
            progress.animateTo(START_FRAME, tween(ms, easing = LinearEasing))
            progress.snapTo(0f)
        }
    }

    // ما نقرأ progress هنا (حتى ما تعيد التركيب كل إطار): فقط يتغير لما تبدأ/تنتهي الحركة
    val shown by remember(open) { derivedStateOf { open || progress.value > 0.001f } }
    if (!shown) return

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
            val geo = remember(wPx, hPx, navB, topI, rtl, dp1) {
                val margin = 8f * dp1
                val btnR = wPx - 20f * dp1
                val btnB = hPx - navB - 112f * dp1
                val uFull = 56f * dp1 / VBTN_SIZE
                val u = min(uFull, min((btnR - margin) / (VBTN_R - 40f), (btnB - topI - margin) / (560.5f - VBTN_T)))
                Geo(d, u, btnR, btnB, wPx, rtl)
            }
            val u = geo.u
            val fin = remember(geo) { frame(geo, FRAMES - 1f) }

            // من هنا نقرأ progress (التركيب يعاد بس لهذا الجزء)
            val f = progress.value
            val fr = frame(geo, f)
            val rounded = f >= ROUND_FROM
            val lensK = ((f - LENS_FROM) / 6f).coerceIn(0f, 1f)
            val cornerPx = (40f + (CORNER - 40f) * ((f - ROUND_FROM) / 8f).coerceIn(0f, 1f)) * u
            val pathRel = Path().apply { addPath(fr.path, Offset(-fr.minX, -fr.minY)) }

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
            ) {
                // (A) الزجاج: عقدة بحجم صندوق الشكل فقط (أخف بكثير من ملء الشاشة)
                Box(
                    Modifier
                        .offset { IntOffset(fr.minX.roundToInt(), fr.minY.roundToInt()) }
                        .size(
                            with(density) { fr.w.coerceAtLeast(1f).toDp() },
                            with(density) { fr.h.coerceAtLeast(1f).toDp() }
                        )
                        // drop shadow خارج الشكل فقط
                        .drawBehind {
                            clipPath(pathRel, ClipOp.Difference) {
                                val n = 16
                                for (i in n downTo 1) {
                                    drawPath(
                                        pathRel,
                                        Color.Black.copy(alpha = 0.0085f),
                                        style = Stroke(width = 24f * u * i / n * 2f)
                                    )
                                }
                            }
                        }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { if (rounded) RoundedCornerShape(CornerSize(cornerPx)) else PathShape(pathRel) },
                            effects = {
                                vibrancy()
                                blur(16f.dp.toPx())
                                if (rounded && lensK > 0.01f) lens(12f.dp.toPx() * lensK, 24f.dp.toPx() * lensK)
                            },
                            onDrawSurface = {
                                val s = (fr.h / max(fin.h, 1f)).coerceIn(0f, 1.2f)
                                val vid = if (s < 0.2f) 0.74f
                                else 0.74f + (0.555f - 0.74f) * ((s - 0.2f) / 0.75f).coerceIn(0f, 1f)
                                // مثل بقية التطبيق: طبقة خفيفة فوق الزجاج
                                if (dark) drawRect(Color.Black.copy(alpha = (0.30f + (vid - 0.555f) * 0.4f).coerceIn(0f, 1f)))
                                else drawRect(Color.White.copy(alpha = (0.42f + (vid - 0.555f) * 0.7f).coerceIn(0f, 1f)))
                            }
                        )
                        .drawWithContent {
                            drawContent()
                            val rim = if (dark) 0.5f else 1f
                            clipPath(pathRel) {
                                drawPath(pathRel, Color.White.copy(alpha = 0.05f * rim), style = Stroke(width = 9f * u))
                                drawPath(pathRel, Color.White.copy(alpha = 0.07f * rim), style = Stroke(width = 5f * u))
                                drawPath(pathRel, Color.White.copy(alpha = 0.10f * rim), style = Stroke(width = 2f * u))
                            }
                            drawPath(pathRel, Color.White.copy(alpha = 0.55f), style = Stroke(width = 0.8f * dp1))
                        }
                )

                // (B) أيقونة الزر + المحتوى + زر X، مقصوصة بالشكل
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawWithContent { clipPath(fr.path) { this@drawWithContent.drawContent() } }
                ) {
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

                    val ca = table(f, C_K, C_A)
                    if (ca > 0.005f) {
                        val bx = table(f, C_K, C_BX) * u
                        val by = table(f, C_K, C_BY) * u
                        Box(
                            Modifier
                                .offset { IntOffset(fin.minX.roundToInt(), fin.minY.roundToInt()) }
                                .size(with(density) { fin.w.toDp() }, with(density) { fin.h.toDp() })
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

                    // زر الإغلاق (X) بالزاوية العليا (جهة بداية اللغة: يمين بالعربي)
                    val xa = table(f, X_K, X_A)
                    if (xa > 0.01f) {
                        val sizePx = 36f * dp1
                        val pad = 14f * dp1
                        val left = if (rtl) fin.maxX - pad - sizePx else fin.minX + pad
                        Box(
                            Modifier
                                .offset { IntOffset(left.roundToInt(), (fin.minY + pad).roundToInt()) }
                                .size(36.dp)
                                .graphicsLayer { alpha = xa }
                                .clip(CircleShape)
                                .background(if (dark) Color.White.copy(0.16f) else Color.Black.copy(0.10f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = open && xa > 0.6f,
                                    onClick = onDismiss
                                ),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Close, "إغلاق",
                                tint = if (dark) Color.White else Color(0xFF101418),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
