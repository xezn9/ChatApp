package com.chatapp.messenger.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

// ============================================================================
//  إنشاء ملصق من صورة: اختيار الصورة من المعرض، وإطار قص تگدر تحركه وتكبره/تصغره
//  (حر أو مربع)، وبعدها يتحفظ بقسم "ملصقاتي" بلوحة الملصقات.
// ============================================================================

/** حامل حالة إنشاء الملصق: زر "إنشاء ملصق" ينادي [launchPicker]، والشاشة تظهر لما تنختار صورة */
internal class StickerCreator {
    var uri by mutableStateOf<Uri?>(null)
    var launchPicker: () -> Unit = {}
}

@Composable
internal fun rememberStickerCreator(): StickerCreator {
    val c = remember { StickerCreator() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) c.uri = u
    }
    c.launchPicker = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    return c
}

/** تنحط بجذر الشاشة (فوق كل شي): تعرض شاشة القص إذا اختار المستخدم صورة */
@Composable
internal fun StickerCreatorHost(creator: StickerCreator) {
    val u = creator.uri ?: return
    StickerMakerScreen(
        uri = u,
        onDone = { creator.uri = null },
        onCancel = { creator.uri = null }
    )
}

private class DragMode(val move: Boolean, val l: Boolean, val t: Boolean, val r: Boolean, val b: Boolean)

private suspend fun loadBitmap(ctx: android.content.Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var sample = 1
        while (max(o.outWidth, o.outHeight) / sample > 1800) sample *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) } ?: return@withContext null
        val rot = try {
            ctx.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
                ?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (e: Exception) { null } ?: ExifInterface.ORIENTATION_NORMAL
        val m = Matrix()
        when (rot) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            else -> return@withContext bmp
        }
        Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    } catch (e: Throwable) { null }
}

@Composable
internal fun StickerMakerScreen(uri: Uri, onDone: () -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var bmp by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var square by remember { mutableStateOf(false) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    var crop by remember(uri) { mutableStateOf<Rect?>(null) }
    val mode = remember { arrayOfNulls<DragMode>(1) }

    BackHandler(enabled = !saving, onBack = onCancel)
    LaunchedEffect(uri) {
        val b = loadBitmap(ctx, uri)
        if (b == null) { failed = true; AppToast.show("ما گدرت أفتح الصورة") } else bmp = b
    }
    LaunchedEffect(failed) { if (failed) onCancel() }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0C10)).pointerInput(Unit) { detectDragGestures { _, _ -> } }) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // الشريط العلوي
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(0.12f))
                        .clickable(enabled = !saving) { onCancel() },
                    contentAlignment = Alignment.Center
                ) { Text("✕", color = Color.White, fontSize = 16.sp) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("إنشاء ملصق", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.size(42.dp))
            }

            // منطقة القص
            Box(
                Modifier.weight(1f).fillMaxWidth().onSizeChanged { area = it },
                contentAlignment = Alignment.Center
            ) {
                val b = bmp
                if (b == null) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(34.dp))
                } else if (area.width > 0 && area.height > 0) {
                    val pad = with(density) { 18.dp.toPx() }
                    val availW = area.width - pad * 2
                    val availH = area.height - pad * 2
                    val scale = min(availW / b.width, availH / b.height)
                    val dw = b.width * scale
                    val dh = b.height * scale
                    val left = (area.width - dw) / 2f
                    val top = (area.height - dh) / 2f
                    val imgRect = Rect(left, top, left + dw, top + dh)
                    val minSz = with(density) { 70.dp.toPx() }.coerceAtMost(min(dw, dh))
                    val handle = with(density) { 40.dp.toPx() }
                    val edge = with(density) { 26.dp.toPx() }

                    // أول مرة أو تغيّر القياس: إطار يغطي أغلب الصورة
                    LaunchedEffect(imgRect) {
                        val inset = min(dw, dh) * 0.06f
                        crop = Rect(imgRect.left + inset, imgRect.top + inset, imgRect.right - inset, imgRect.bottom - inset)
                    }
                    val c = crop ?: imgRect

                    fun hit(p: Offset): DragMode? {
                        fun near(a: Float, b2: Float, r: Float) = kotlin.math.abs(a - b2) <= r
                        val nl = near(p.x, c.left, handle); val nr = near(p.x, c.right, handle)
                        val nt = near(p.y, c.top, handle); val nb = near(p.y, c.bottom, handle)
                        if ((nl || nr) && (nt || nb)) {
                            val dl = nl && (!nr || p.x < c.center.x)
                            val dt = nt && (!nb || p.y < c.center.y)
                            return DragMode(false, dl, dt, !dl, !dt)
                        }
                        if (!square) {
                            val inX = p.x in c.left..c.right; val inY = p.y in c.top..c.bottom
                            if (near(p.x, c.left, edge) && inY) return DragMode(false, true, false, false, false)
                            if (near(p.x, c.right, edge) && inY) return DragMode(false, false, false, true, false)
                            if (near(p.y, c.top, edge) && inX) return DragMode(false, false, true, false, false)
                            if (near(p.y, c.bottom, edge) && inX) return DragMode(false, false, false, false, true)
                        }
                        if (c.contains(p)) return DragMode(true, false, false, false, false)
                        return null
                    }

                    fun applyDrag(r: Rect, m: DragMode, d: Offset): Rect {
                        if (m.move) {
                            val dx = d.x.coerceIn(imgRect.left - r.left, imgRect.right - r.right)
                            val dy = d.y.coerceIn(imgRect.top - r.top, imgRect.bottom - r.bottom)
                            return r.translate(dx, dy)
                        }
                        var l = r.left; var t = r.top; var rr = r.right; var bt = r.bottom
                        if (m.l) l = (l + d.x).coerceIn(imgRect.left, rr - minSz)
                        if (m.r) rr = (rr + d.x).coerceIn(l + minSz, imgRect.right)
                        if (m.t) t = (t + d.y).coerceIn(imgRect.top, bt - minSz)
                        if (m.b) bt = (bt + d.y).coerceIn(t + minSz, imgRect.bottom)
                        if (square) {
                            // نثبّت الزاوية المقابلة ونخلي الإطار مربع
                            val s = min(rr - l, bt - t)
                            if (m.l) l = rr - s else rr = l + s
                            if (m.t) t = bt - s else bt = t + s
                        }
                        return Rect(l, t, rr, bt)
                    }

                    // تبديل حر/مربع: المربع يتوسّط الإطار الحالي
                    LaunchedEffect(square) {
                        if (square) crop?.let { r ->
                            val s = min(r.width, r.height)
                            crop = Rect(r.center.x - s / 2, r.center.y - s / 2, r.center.x + s / 2, r.center.y + s / 2)
                        }
                    }

                    Canvas(
                        Modifier.fillMaxSize().pointerInput(imgRect, square) {
                            detectDragGestures(
                                onDragStart = { p -> mode[0] = hit(p) },
                                onDragEnd = { mode[0] = null },
                                onDragCancel = { mode[0] = null }
                            ) { change, drag ->
                                val m = mode[0] ?: return@detectDragGestures
                                change.consume()
                                crop = applyDrag(crop ?: imgRect, m, drag)
                            }
                        }
                    ) {
                        // خلفية مربعات توضح الشفافية
                        val tile = 14.dp.toPx()
                        var yy = imgRect.top; var row = 0
                        while (yy < imgRect.bottom) {
                            var xx = imgRect.left; var col = row
                            while (xx < imgRect.right) {
                                drawRect(
                                    if (col % 2 == 0) Color(0xFF3A3C44) else Color(0xFF2A2C33),
                                    Offset(xx, yy), Size(min(tile, imgRect.right - xx), min(tile, imgRect.bottom - yy))
                                )
                                xx += tile; col++
                            }
                            yy += tile; row++
                        }
                        drawImage(
                            b.asImageBitmap(),
                            dstOffset = IntOffset(imgRect.left.toInt(), imgRect.top.toInt()),
                            dstSize = IntSize(imgRect.width.toInt(), imgRect.height.toInt())
                        )
                        // تعتيم خارج الإطار
                        val dim = Color.Black.copy(0.58f)
                        drawRect(dim, Offset(imgRect.left, imgRect.top), Size(imgRect.width, c.top - imgRect.top))
                        drawRect(dim, Offset(imgRect.left, c.bottom), Size(imgRect.width, imgRect.bottom - c.bottom))
                        drawRect(dim, Offset(imgRect.left, c.top), Size(c.left - imgRect.left, c.height))
                        drawRect(dim, Offset(c.right, c.top), Size(imgRect.right - c.right, c.height))
                        // الإطار + خطوط الثلث
                        val gl = Color.White.copy(0.30f)
                        for (i in 1..2) {
                            drawLine(gl, Offset(c.left + c.width * i / 3f, c.top), Offset(c.left + c.width * i / 3f, c.bottom), 1.dp.toPx())
                            drawLine(gl, Offset(c.left, c.top + c.height * i / 3f), Offset(c.right, c.top + c.height * i / 3f), 1.dp.toPx())
                        }
                        drawRect(Color.White, c.topLeft, c.size, style = Stroke(2.dp.toPx()))
                        // مقابض الزوايا (وأواسط الأضلاع بالوضع الحر)
                        val rad = 8.dp.toPx()
                        val pts = buildList {
                            add(c.topLeft); add(c.topRight); add(c.bottomLeft); add(c.bottomRight)
                            if (!square) { add(Offset(c.center.x, c.top)); add(Offset(c.center.x, c.bottom)); add(Offset(c.left, c.center.y)); add(Offset(c.right, c.center.y)) }
                        }
                        pts.forEach { drawCircle(Color.White, rad, it) }
                    }
                }
            }

            Text(
                "حرّك الإطار أو اسحب زواياه لتحديد الجزء اللي تريده",
                color = Color.White.copy(0.65f), fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            // الشريط السفلي: حر / مربع + حفظ
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ModeChip("حر", !square) { square = false }
                ModeChip("مربع", square) { square = true }
                Box(Modifier.weight(1f))
                Box(
                    Modifier.height(50.dp).clip(RoundedCornerShape(50)).background(Color(0xFF4DA3FF))
                        .clickable(enabled = !saving && bmp != null && crop != null) {
                            val b = bmp ?: return@clickable
                            val r = crop ?: return@clickable
                            val a = area
                            saving = true
                            scope.launch {
                                val out = withContext(Dispatchers.Default) {
                                    val padPx = with(density) { 18.dp.toPx() }
                                    val sc = min((a.width - padPx * 2) / b.width, (a.height - padPx * 2) / b.height)
                                    val left0 = (a.width - b.width * sc) / 2f
                                    val top0 = (a.height - b.height * sc) / 2f
                                    val x = ((r.left - left0) / sc).toInt().coerceIn(0, b.width - 1)
                                    val y = ((r.top - top0) / sc).toInt().coerceIn(0, b.height - 1)
                                    val w = (r.width / sc).toInt().coerceIn(1, b.width - x)
                                    val h = (r.height / sc).toInt().coerceIn(1, b.height - y)
                                    Bitmap.createBitmap(b, x, y, w, h)
                                }
                                val f = CustomStickers.save(ctx, out)
                                if (f != null) {
                                    CustomStickers.version.intValue++
                                    AppToast.show("انضاف الملصق لـ «ملصقاتي»")
                                    onDone()
                                } else {
                                    saving = false
                                    AppToast.show("تعذّر حفظ الملصق")
                                }
                            }
                        }
                        .padding(horizontal = 30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (saving) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    else Text("حفظ الملصق", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(42.dp).clip(RoundedCornerShape(50))
            .background(if (selected) Color.White else Color.White.copy(0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color(0xFF101418) else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
