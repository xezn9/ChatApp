package com.chatapp.messenger.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.view.Gravity
import android.webkit.MimeTypeMap
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import androidx.compose.animation.core.FastOutLinearInEasing
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.chatapp.messenger.ui.glass.GlassButton
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.io.File

/**
 * شي مختار بانتظار التأكيد قبل الإرسال.
 * [kind]: "image" أو "video" أو "file". بالملفات [file] هو النسخة المحلية الي انسخت بالكاش.
 */
class PendingAttachment(
    val kind: String,
    val uri: Uri,
    val name: String = "",
    val size: Long = 0L,
    val file: File? = null
)

/** أدوات الملفات العادية (غير الصور والفيديوهات) */
object FileUtil {
    /** أقصى حجم للملف المرسل (نفس حد الصور والفيديو لأن التخزين على قاعدة البيانات) */
    const val MAX_BYTES = 8_000_000L

    /** اسم الملف وحجمه من الـ Uri */
    fun queryMeta(ctx: Context, uri: Uri): Pair<String, Long> {
        var name = ""
        var size = -1L
        runCatching {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0) name = c.getString(ni) ?: ""
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        }
        if (name.isBlank()) name = uri.lastPathSegment?.substringAfterLast('/') ?: "ملف"
        return name to size
    }

    /** اسم آمن للتخزين على القرص */
    fun safeName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").take(80).ifBlank { "file" }

    /** مكان نسخة الملف المنزّل (نفس الاسم عند المرسل والمستلم) */
    fun localFile(ctx: Context, id: String, name: String): File {
        val dir = File(MediaUtil.mediaDir(ctx), "files").apply { mkdirs() }
        return File(dir, id + "_" + safeName(name))
    }

    /** ينسخ الملف المختار للكاش (مع التحقق من الحجم). يرمي IllegalStateException برسالة عربية */
    fun copyToCache(ctx: Context, uri: Uri, name: String): File {
        val dir = File(MediaUtil.mediaDir(ctx), "files").apply { mkdirs() }
        val out = File(dir, "tmp_${System.nanoTime()}_" + safeName(name))
        try {
            ctx.contentResolver.openInputStream(uri)?.use { inp ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val r = inp.read(buf)
                        if (r < 0) break
                        total += r
                        if (total > MAX_BYTES) throw IllegalStateException("الملف كبير، الحد الأقصى ${formatSize(MAX_BYTES)}")
                        o.write(buf, 0, r)
                    }
                }
            } ?: throw IllegalStateException("ما گدرت أفتح الملف")
        } catch (e: Exception) {
            out.delete()
            throw e
        }
        return out
    }

    fun formatSize(b: Long): String = when {
        b < 1024 -> "$b B"
        b < 1024 * 1024 -> "%.0f KB".format(b / 1024.0)
        else -> "%.1f MB".format(b / (1024.0 * 1024.0))
    }

    /** يحفظ نسخة من الملف بمجلد التنزيلات (Downloads). يرجع true إذا نجح */
    suspend fun saveToDownloads(ctx: Context, src: File, displayName: String): Boolean =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val name = safeName(displayName)
                val ext = name.substringAfterLast('.', "").lowercase()
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
                        put(android.provider.MediaStore.Downloads.MIME_TYPE, mime)
                        put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                        put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val resolver = ctx.contentResolver
                    val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: return@withContext false
                    val ok = resolver.openOutputStream(uri)?.use { o -> src.inputStream().use { it.copyTo(o) }; true } ?: false
                    if (!ok) { runCatching { resolver.delete(uri, null, null) }; return@withContext false }
                    values.clear()
                    values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    true
                } else {
                    @Suppress("DEPRECATION")
                    val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    dir.mkdirs()
                    var out = File(dir, name)
                    var n = 1
                    while (out.exists()) {
                        val base = name.substringBeforeLast('.', name)
                        val e = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
                        out = File(dir, "$base ($n)$e"); n++
                    }
                    src.copyTo(out)
                    android.media.MediaScannerConnection.scanFile(ctx, arrayOf(out.absolutePath), arrayOf(mime), null)
                    true
                }
            } catch (e: Exception) {
                false
            }
        }

    /** يفتح الملف ببرنامج مناسب على الجهاز */
    fun open(ctx: Context, f: File, displayName: String) {
        try {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".updates", f)
            val ext = displayName.substringAfterLast('.', "").lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
            val i = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(i)
        } catch (e: Exception) {
            AppToast.show("ما أكو برنامج على جهازك يفتح هذا النوع من الملفات", long = true)
        }
    }
}

/** القائمة نفسها: pMedia / pFile = تقدم الانبعاث (0 = مطوية عند الزر، 1 = مفتوحة) لكل زر */
@Composable
private fun AttachMenu(
    pMedia: Float,
    pFile: Float,
    backdrop: Backdrop,
    fg: Color,
    onPickMedia: () -> Unit,
    onPickFile: () -> Unit
) {
    // أقصى اليمين الفيزيائي (Start بالعربي، End بالإنجليزي)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = if (rtl) Alignment.Start else Alignment.End
    ) {
        // الأبعد (ملفات) فوق، والأقرب للزر (صور وفيديوهات) تحته
        EmergeItem(pFile, travelDp = 124) { AttachItem(backdrop, fg, "ملفات", "📎", onPickFile) }
        EmergeItem(pMedia, travelDp = 62) { AttachItem(backdrop, fg, "صور وفيديوهات", "🖼", onPickMedia) }
    }
}

/**
 * نافذة القائمة فوق زر (+) بالضبط، ملتصقة بحافته اليمنى.
 * [open] = true: الزرّين ينبعثون من زر (+) بنبضة ارتداد. [open] = false: يرجعون للزر بنفس الحركة بالعكس ثم تختفي النافذة.
 */
@Composable
fun AttachMenuPopup(
    open: Boolean,
    backdrop: Backdrop,
    fg: Color,
    onDismiss: () -> Unit,
    onPickMedia: () -> Unit,
    onPickFile: () -> Unit
) {
    val pMedia = remember { Animatable(0f) }
    val pFile = remember { Animatable(0f) }
    var composed by remember { mutableStateOf(open) }
    LaunchedEffect(open) {
        if (open) {
            composed = true
            coroutineScope {
                launch { pMedia.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 330f)) }
                launch { delay(60); pFile.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 330f)) }
            }
        } else if (composed) {
            // الرجوع: «ملفات» (الأبعد) يرجع أول، وبعده «صور وفيديوهات»، وكلهم يدخلون بزر (+)
            coroutineScope {
                launch { pFile.animateTo(0f, tween(190, easing = FastOutLinearInEasing)) }
                launch { delay(60); pMedia.animateTo(0f, tween(190, easing = FastOutLinearInEasing)) }
            }
            composed = false
        }
    }
    if (!composed) return

    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val dy = with(LocalDensity.current) { -64.dp.roundToPx() }
    Popup(
        alignment = if (rtl) Alignment.BottomStart else Alignment.BottomEnd,
        offset = IntOffset(0, dy),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        AttachMenu(
            pMedia = pMedia.value, pFile = pFile.value, backdrop = backdrop, fg = fg,
            onPickMedia = { if (open) onPickMedia() },
            onPickFile = { if (open) onPickFile() }
        )
    }
}

/** يطلع من زر (+) ويرجع إليه: يتصغّر ويتحرك لتحت (جهة الزر) حسب [p] */
@Composable
private fun EmergeItem(p: Float, travelDp: Int, content: @Composable () -> Unit) {
    Box(
        Modifier.graphicsLayer {
            alpha = (p * 3f).coerceIn(0f, 1f)
            val sc = (0.15f + 0.85f * p).coerceAtLeast(0f)
            scaleX = sc
            scaleY = sc
            translationY = (1f - p) * travelDp.dp.toPx()
            // نقطة الانبعاث: الزاوية السفلية اليمنى (جهة زر +)
            transformOrigin = TransformOrigin(1f, 1f)
        }
    ) { content() }
}

@Composable
private fun AttachItem(backdrop: Backdrop, fg: Color, label: String, emoji: String, onClick: () -> Unit) {
    GlassButton(onClick = onClick, backdrop = backdrop, pressScale = 1.05f, modifier = Modifier.height(50.dp)) {
        Row(
            Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(emoji, fontSize = 20.sp)
            Text(label, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** معاينة بملء الشاشة قبل الإرسال: صورة، فيديو (تشغيل بالضغط)، أو بطاقة ملف */
@Composable
fun AttachmentPreviewScreen(
    item: PendingAttachment,
    sending: Boolean,
    onSend: () -> Unit,
    onCancel: () -> Unit
) {
    val ctx = LocalContext.current
    val bd = rememberLayerBackdrop()
    BackHandler(enabled = !sending, onBack = onCancel)

    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { } }) {
        Box(Modifier.fillMaxSize().layerBackdrop(bd)) {
            when (item.kind) {
                "image" -> ImagePreview(item.uri)
                "video" -> VideoPreview(item.uri)
                else -> FilePreview(item)
            }
        }
        // زر الإلغاء
        Row(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp).align(Alignment.TopStart)) {
            GlassButton(onClick = onCancel, backdrop = bd, enabled = !sending, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
        // زر الإرسال
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 22.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassButton(
                onClick = onSend, backdrop = bd, tint = AccentBlue, enabled = !sending,
                modifier = Modifier.height(56.dp).width(150.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Text("إرسال", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ImagePreview(uri: Uri) {
    val ctx = LocalContext.current
    var bmp by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        val bytes = ImageUtil.uriToJpeg(ctx, uri, 1600, 85)
        bmp = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        if (bmp == null) AppToast.show("ما گدرت أعرض الصورة")
    }
    val b = bmp
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (b != null) Image(
            b.asImageBitmap(), null,
            Modifier.fillMaxSize().padding(vertical = 70.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun VideoPreview(uri: Uri) {
    val viewRef = remember { arrayOfNulls<VideoView>(1) }
    var playing by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { runCatching { viewRef[0]?.stopPlayback() } } }
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onTap = {
                val v = viewRef[0] ?: return@detectTapGestures
                if (v.isPlaying) { v.pause(); playing = false } else { v.start(); playing = true }
            })
        }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(vertical = 70.dp),
            factory = { c ->
                val vv = VideoView(c)
                vv.setVideoURI(uri)
                vv.setOnPreparedListener { mp -> mp.isLooping = true; vv.start() }
                vv.setOnErrorListener { _, _, _ -> AppToast.show("تعذّر تشغيل الفيديو"); true }
                viewRef[0] = vv
                FrameLayout(c).also {
                    it.addView(
                        vv,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER
                        )
                    )
                }
            }
        )
        if (!playing) {
            Box(
                Modifier.align(Alignment.Center).size(72.dp).clip(CircleShape).background(Color.Black.copy(0.45f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(44.dp)) }
        }
    }
}

@Composable
private fun FilePreview(item: PendingAttachment) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(horizontal = 32.dp).fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)).background(Color.White.copy(0.08f))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("📄", fontSize = 56.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                item.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 3, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(FileUtil.formatSize(item.size), color = Color.White.copy(0.65f), fontSize = 14.sp)
        }
    }
}
