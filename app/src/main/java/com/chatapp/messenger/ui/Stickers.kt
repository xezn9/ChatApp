package com.chatapp.messenger.ui

import android.content.Context
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.chatapp.messenger.ui.theme.GlassFx
import com.chatapp.messenger.ui.theme.glassSurfaceLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import com.chatapp.messenger.Profile
import com.chatapp.messenger.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.zip.GZIPInputStream
import kotlin.math.min
import kotlin.math.roundToInt

// ============================================================================
//  ملصقات كرموز بجانب الاسم.  معرّف الرمز: "s:<pack>:<name>"
//
//  اكتشاف تلقائي: أي مجلد تضيفه داخل assets/stickers/ يظهر لحاله كقسم جديد بقائمة رموز الاسم
//  ويصير قابل للاستعمال، بدون ما تعدّل أي كود. الصيغ المدعومة داخل المجلد:
//   - tgs  : أنيميشن Lottie (يتحول مرة وحدة لشيت ويتخزن بذاكرة الجهاز)
//   - webp / png / jpg / jpeg : صورة ثابتة، أو شيت إطارات إذا اسمه <اسم>_<عدد الإطارات> وأبعاده مطابقة
//     (6 أعمدة، كل خانة مربعة) مثل 4527294_17.webp
//  إذا نفس الاسم موجود بأكثر من صيغة تُفضَّل tgs ثم webp ثم png ثم jpg.
//  حزم معروفة تحتفظ بترتيبها وألوانها، والباقي يترتب أبجدياً ويتولّد له لون واسم تلقائي.
// ============================================================================

internal class StickerPack(val id: String, val title: String, val color: Color)

private val KnownPacks = listOf(
    StickerPack("crayons", "Crayons", Color(0xFFFF9F0A)),
    StickerPack("hemoni", "Hemoni", Color(0xFF8A5CFF)),
    StickerPack("hzidis", "Hzidis", Color(0xFF00D4AA)),
    StickerPack("stikbot", "StikBot", Color(0xFFFF2D92)),
    StickerPack("nnetfiix", "Nnetfiix", Color(0xFFE50914)),
    StickerPack("syunde", "Syunde", Color(0xFF4DA3FF)),
    StickerPack("nicepack", "NicePack", Color(0xFFFFD60A)),
    StickerPack("esoteric", "Esoteric", Color(0xFFB08968))
)

internal object StickerRegistry {
    private val EXTS = listOf("tgs", "webp", "png", "jpg", "jpeg")   // الترتيب = الأفضلية
    @Volatile private var scanned = false
    @Volatile private var packList: List<StickerPack> = emptyList()
    @Volatile private var fileMap: Map<String, Map<String, String>> = emptyMap()   // pack -> (name -> fileName)

    val packs: List<StickerPack> get() = packList

    @Synchronized
    fun ensure(ctx: Context) {
        if (scanned) return
        val am = ctx.applicationContext.assets
        val dirs = try { am.list("stickers")?.toList() ?: emptyList() } catch (_: Exception) { emptyList() }
        val map = LinkedHashMap<String, Map<String, String>>()
        for (d in dirs) {
            if (d.contains(':') || d.isBlank()) continue
            val names = try { am.list("stickers/$d")?.toList() ?: emptyList() } catch (_: Exception) { emptyList() }
            val byName = LinkedHashMap<String, String>()
            names.filter { it.substringAfterLast('.', "").lowercase() in EXTS && !it.substringBeforeLast('.').contains(':') }
                .sortedBy { EXTS.indexOf(it.substringAfterLast('.').lowercase()) }
                .forEach { byName.putIfAbsent(it.substringBeforeLast('.'), it) }
            if (byName.isNotEmpty()) map[d] = byName
        }
        val known = KnownPacks.filter { map.containsKey(it.id) }
        val extra = map.keys.filter { k -> KnownPacks.none { it.id == k } }.sortedBy { it.lowercase() }.map { autoPack(it) }
        fileMap = map
        packList = known + extra
        scanned = true
    }

    private fun autoPack(id: String): StickerPack {
        val title = id.replace('_', ' ').replace('-', ' ').trim().replaceFirstChar { it.uppercase() }.ifBlank { id }
        val hue = (Math.abs(id.hashCode()) % 360).toFloat()
        return StickerPack(id, title, Color.hsv(hue, 0.65f, 1f))
    }

    fun fileOf(packId: String, name: String): String? = fileMap[packId]?.get(name)

    /** ملفات الحزمة مرتبة ترتيب طبيعي (2 قبل 10) */
    fun filesOf(packId: String): List<String> = fileMap[packId]?.values?.sortedWith(natural) ?: emptyList()

    private val chunk = Regex("\\d+|\\D+")
    private val natural = Comparator<String> { a, b ->
        val x = chunk.findAll(a).map { it.value }.toList()
        val y = chunk.findAll(b).map { it.value }.toList()
        var r = 0
        for (i in 0 until min(x.size, y.size)) {
            val p = x[i]; val q = y[i]
            r = if (p[0].isDigit() && q[0].isDigit()) {
                val c = p.trimStart('0').length.compareTo(q.trimStart('0').length)
                if (c != 0) c else p.trimStart('0').compareTo(q.trimStart('0'))
            } else p.compareTo(q)
            if (r != 0) break
        }
        if (r != 0) r else x.size.compareTo(y.size)
    }
}

internal val StickerPacks: List<StickerPack> get() = StickerRegistry.packs

/** هل هذا الملصق tgs (Lottie)؟ حسب امتداد الملف الفعلي */
internal fun isLottieSticker(pack: StickerPack, name: String): Boolean =
    StickerRegistry.fileOf(pack.id, name)?.endsWith(".tgs", ignoreCase = true) == true

private const val SHEET_COLS = 6
private const val FRAME_MS = 100

internal object StickerStore {
    private val files = HashMap<String, List<String>>()
    private val jsonCache = LruCache<String, String>(48)
    private val sheetCache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    /** أسماء ملفات الحزمة مرتبة */
    @Synchronized
    fun files(ctx: Context, pack: StickerPack): List<String> {
        StickerRegistry.ensure(ctx)
        return files.getOrPut(pack.id) { StickerRegistry.filesOf(pack.id) }
    }

    fun idOf(pack: StickerPack, file: String) = "s:${pack.id}:${file.substringBeforeLast('.')}"

    suspend fun loadJson(ctx: Context, path: String): String? {
        jsonCache.get(path)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                GZIPInputStream(ctx.assets.open(path)).bufferedReader().use { it.readText() }
            } catch (_: Exception) { null }
        }?.also { jsonCache.put(path, it) }
    }

    // ---------- ملصقات tgs: تتحول مرة وحدة لصور متحركة (sprite) وتنحفظ بذاكرة الجهاز، وبعدها ما ينعاد تحميلها ----------
    private const val PX = 80
    private const val FPS = 12
    private const val MAX_FRAMES = 36
    private val renderLock = Mutex()
    private val waiting = AtomicInteger(0)
    private var prewarmStarted = false

    private fun cacheDir(ctx: Context, pack: StickerPack) = File(ctx.filesDir, "stickers/${pack.id}").also { it.mkdirs() }

    private fun cachedFile(dir: File, name: String): File? =
        dir.list()?.firstOrNull { it.startsWith("${name}_") && it.endsWith(".webp") && it.removePrefix("${name}_").removeSuffix(".webp").toIntOrNull() != null }?.let { File(dir, it) }

    private fun framesOf(f: File): Int = f.name.removeSuffix(".webp").substringAfterLast('_').toIntOrNull() ?: 1

    /** يرجع (الشيت، عدد الإطارات). [urgent]=true للي المستخدم يشوفه هسه (له أولوية على التحضير بالخلفية) */
    suspend fun lottieSheet(ctx: Context, pack: StickerPack, name: String, urgent: Boolean = true, keep: Boolean = true): Pair<ImageBitmap, Int>? {
        val dir = cacheDir(ctx, pack)
        val memKey = "L:${pack.id}/$name"
        cachedFile(dir, name)?.let { f ->
            val n = framesOf(f)
            sheetCache.get(memKey)?.let { return it to n }
            val bmp = withContext(Dispatchers.IO) {
                try { android.graphics.BitmapFactory.decodeFile(f.path)?.asImageBitmap() } catch (_: Exception) { null }
            }
            if (bmp != null) { if (keep) sheetCache.put(memKey, bmp); return bmp to n }
            f.delete()
        }
        if (urgent) waiting.incrementAndGet()
        try {
            return renderLock.withLock {
                cachedFile(dir, name)?.let { f -> // انرسم أثناء الانتظار
                    val bmp = android.graphics.BitmapFactory.decodeFile(f.path)?.asImageBitmap()
                    if (bmp != null) { if (keep) sheetCache.put(memKey, bmp); return@withLock bmp to framesOf(f) }
                }
                withContext(Dispatchers.Default) { renderLottie(ctx, pack, name, dir, memKey, keep) }
            }
        } finally {
            if (urgent) waiting.decrementAndGet()
        }
    }

    private suspend fun renderLottie(ctx: Context, pack: StickerPack, name: String, dir: File, memKey: String, keep: Boolean): Pair<ImageBitmap, Int>? {
        return try {
            val json = loadJson(ctx, "stickers/${pack.id}/$name.tgs") ?: return null
            val comp = LottieCompositionFactory.fromJsonStringSync(json, memKey).value ?: return null
            val n = ((comp.duration / 1000f) * FPS).roundToInt().coerceIn(1, MAX_FRAMES)
            val d = LottieDrawable()
            d.setComposition(comp)
            d.setBounds(0, 0, PX, PX)
            val cols = min(SHEET_COLS, n)
            val rows = (n + cols - 1) / cols
            val sheet = android.graphics.Bitmap.createBitmap(cols * PX, rows * PX, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(sheet)
            for (i in 0 until n) {
                c.save()
                c.translate((i % cols) * PX.toFloat(), (i / cols) * PX.toFloat())
                c.clipRect(0f, 0f, PX.toFloat(), PX.toFloat())
                d.progress = i.toFloat() / n
                d.draw(c)
                c.restore()
            }
            val out = File(dir, "${name}_$n.webp")
            val tmp = File(dir, "$name.tmp")
            FileOutputStream(tmp).use {
                val fmt = if (android.os.Build.VERSION.SDK_INT >= 30) android.graphics.Bitmap.CompressFormat.WEBP_LOSSY
                else @Suppress("DEPRECATION") android.graphics.Bitmap.CompressFormat.WEBP
                sheet.compress(fmt, 80, it)
            }
            tmp.renameTo(out)
            val img = sheet.asImageBitmap()
            if (keep) sheetCache.put(memKey, img)
            img to n
        } catch (_: Throwable) { null }
    }

    /** تحضير كل ملصقات tgs بالخلفية مرة وحدة (بأولوية منخفضة) حتى تصير فورية بعدين */
    fun prewarm(ctx: Context) {
        if (prewarmStarted) return
        prewarmStarted = true
        val app = ctx.applicationContext
        StickerRegistry.ensure(app)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try { Thread.currentThread().priority = Thread.MIN_PRIORITY } catch (_: Exception) {}
            for (pack in StickerPacks) {
                val dir = cacheDir(app, pack)
                for (file in files(app, pack).filter { it.endsWith(".tgs", ignoreCase = true) }) {
                    val name = file.substringBeforeLast('.')
                    if (cachedFile(dir, name) != null) continue
                    while (waiting.get() > 0) delay(120)   // خلّي المستخدم يمشي أول
                    lottieSheet(app, pack, name, urgent = false, keep = false)
                    delay(15)
                }
            }
        }
    }

    private val framesCache = HashMap<String, Int>()

    /** عدد إطارات شيت: من اللاحقة _N بالاسم وبشرط تطابق الأبعاد مع شبكة 6 أعمدة، وإلا صورة ثابتة (1) */
    fun framesFor(name: String, w: Int, h: Int): Int {
        val n = name.substringAfterLast('_', "").toIntOrNull() ?: return 1
        if (n < 2 || n > 600) return 1
        val cols = min(SHEET_COLS, n)
        val rows = (n + cols - 1) / cols
        if (w <= 0 || w % cols != 0) return 1
        return if (h == (w / cols) * rows) n else 1
    }

    /** يرجع (الصورة، عدد الإطارات). الصور الثابتة الكبيرة تُصغَّر حتى ما تاكل الذاكرة */
    suspend fun loadSheet(ctx: Context, path: String, name: String): Pair<ImageBitmap, Int>? {
        sheetCache.get(path)?.let { bmp -> return bmp to synchronized(framesCache) { framesCache[path] ?: 1 } }
        return withContext(Dispatchers.IO) {
            try {
                val b = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it, null, b) }
                val frames = framesFor(name, b.outWidth, b.outHeight)
                val o = android.graphics.BitmapFactory.Options()
                if (frames == 1) {
                    var sample = 1
                    while (maxOf(b.outWidth, b.outHeight) / (sample * 2) >= 256) sample *= 2
                    o.inSampleSize = sample
                }
                val bmp = ctx.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it, null, o) }?.asImageBitmap()
                bmp?.let { it to frames }
            } catch (_: Throwable) { null }
        }?.also { (bmp, frames) ->
            synchronized(framesCache) { framesCache[path] = frames }
            sheetCache.put(path, bmp)
        }
    }
}

/** رسم ملصق من معرّفه "s:pack:name". إذا الحزمة أو الملف مفقود ما يرسم شي */
@Composable
internal fun StickerBadgeView(id: String, size: Dp, modifier: Modifier = Modifier, fps: Int = 24) {
    StickerRegistry.ensure(LocalContext.current)
    val parts = remember(id) { id.split(':') }
    if (parts.size != 3) return
    val pack = StickerPacks.firstOrNull { it.id == parts[1] } ?: return
    val name = parts[2]
    if (isLottieSticker(pack, name)) CachedSpriteSticker(pack, name, size, modifier)
    else {
        val file = StickerRegistry.fileOf(pack.id, name) ?: return
        SpriteSticker("stickers/${pack.id}/$file", name, size, modifier)
    }
}

@Composable
private fun CachedSpriteSticker(pack: StickerPack, name: String, size: Dp, modifier: Modifier) {
    val ctx = LocalContext.current.applicationContext
    val res by produceState<Pair<ImageBitmap, Int>?>(null, pack.id, name) { value = StickerStore.lottieSheet(ctx, pack, name) }
    val r = res
    if (r == null) { Box(modifier.size(size)); return }
    SpriteView(r.first, r.second, size, modifier)
}

@Composable
private fun SpriteSticker(path: String, name: String, size: Dp, modifier: Modifier) {
    val ctx = LocalContext.current.applicationContext
    val sheet by produceState<Pair<ImageBitmap, Int>?>(null, path) { value = StickerStore.loadSheet(ctx, path, name) }
    val r = sheet
    if (r == null) { Box(modifier.size(size)); return }
    SpriteView(r.first, r.second, size, modifier)
}

@Composable
private fun SpriteView(img: ImageBitmap, frames: Int, size: Dp, modifier: Modifier) {
    val n = frames.coerceAtLeast(1)
    val t = rememberInfiniteTransition(label = "sprite")
    val f by t.animateFloat(
        0f, n.toFloat(),
        infiniteRepeatable(tween(n * FRAME_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "spriteF"
    )
    // نعيد الرسم فقط لما يتغير الإطار (10 مرات بالثانية) مو كل إطار شاشة
    val frame by remember(n) { androidx.compose.runtime.derivedStateOf { if (n > 1) f.toInt().coerceIn(0, n - 1) else 0 } }
    Canvas(modifier.size(size)) {
        if (n == 1) {   // صورة ثابتة: تنرسم كاملة بالنص مع الحفاظ على النسبة
            val box = this.size.width
            val sc = minOf(box / img.width, box / img.height)
            val dw = (img.width * sc).roundToInt().coerceAtLeast(1)
            val dh = (img.height * sc).roundToInt().coerceAtLeast(1)
            drawImage(
                img,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(img.width, img.height),
                dstOffset = IntOffset(((box - dw) / 2f).roundToInt(), ((box - dh) / 2f).roundToInt()),
                dstSize = IntSize(dw, dh),
                filterQuality = FilterQuality.Medium
            )
            return@Canvas
        }
        val cols = min(SHEET_COLS, n)
        val px = img.width / cols
        val dst = this.size.width.roundToInt()
        drawImage(
            img,
            srcOffset = IntOffset((frame % cols) * px, (frame / cols) * px),
            srcSize = IntSize(px, px),
            dstSize = IntSize(dst, dst),
            filterQuality = FilterQuality.Medium
        )
    }
}

// ---------------------------------------------------------------------------
//  محتوى نافذة اختيار الرمز (يوضع داخل GroupCreatePanel): أقسام الملصقات، والقسم يفتح كل ملصقاته
// ---------------------------------------------------------------------------
@Composable
internal fun StickerPickerContent(dark: Boolean, onDone: () -> Unit) {
    val fg = if (dark) Color.White else Color(0xFF101418)
    val ctx = LocalContext.current
    StickerRegistry.ensure(ctx)
    val me = remember { Repo.auth.currentUser?.uid ?: "" }
    val prof by remember(me) { Repo.basicProfileFlow(me) }.collectAsState(initial = Profile())
    var pack by remember { mutableStateOf<StickerPack?>(null) }
    var previewId by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = pack != null) { pack = null }
    previewId?.let { BadgePreviewDialog(it) { previewId = null } }

    AnimatedContent(
        targetState = pack,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val deeper = targetState != null
            (slideInHorizontally(spring(0.85f, 360f)) { if (deeper) -it else it } + fadeIn(tween(200))) togetherWith
                (slideOutHorizontally(tween(220)) { if (deeper) it / 3 else -it / 3 } + fadeOut(tween(150)))
        },
        label = "stickerPacks"
    ) { p ->
        if (p == null) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("رمز الاسم", color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("اختر ملصق متحرك يظهر جنب اسمك عند الجميع", color = fg.copy(0.6f), fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(16.dp))
                // بدون رمز
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(fg.copy(if (prof.badge.isBlank()) 0.16f else 0.08f))
                        .clickable { Repo.setBadge(""); onDone() }.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) { Text(if (prof.badge.isBlank()) "بدون رمز ✓" else "بدون رمز", color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(14.dp))
                // الأقسام (2 بكل صف) والصفحة تنسحب إذا الأقسام أكثر من الشاشة
                StickerPacks.chunked(2).forEach { rowPacks ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowPacks.forEach { sp ->
                            PackCard(sp, fg, Modifier.weight(1f).height(160.dp)) { pack = sp }
                        }
                        if (rowPacks.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        } else {
            val list = remember(p) { StickerStore.files(ctx, p) }
            Column(Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 20.dp)) {
                Text(
                    p.title, color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${list.size} ملصق", color = fg.copy(0.6f), fontSize = 12.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(fg.copy(0.10f))
                        .clickable { pack = null }.padding(horizontal = 14.dp, vertical = 6.dp)
                ) { Text("‹ الأقسام", color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(10.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(list, key = { it }) { file ->
                        val id = StickerStore.idOf(p, file)
                        val sel = prof.badge == id
                        val shape = RoundedCornerShape(16.dp)
                        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
                                .background(fg.copy(if (sel) 0.18f else 0.07f))
                                .border(if (sel) 2.dp else 0.8.dp, if (sel) p.color else fg.copy(0.14f), shape)
                                .clickable { Repo.setBadge(id); onDone() }
                                .onLongPressPreview {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    previewId = id
                                },
                            contentAlignment = Alignment.Center
                        ) { StickerBadgeView(id, 40.dp, fps = 12) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PackCard(sp: StickerPack, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val list = remember(sp) { StickerStore.files(ctx, sp) }
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.clip(shape).background(fg.copy(0.08f))
            .border(1.dp, sp.color.copy(0.45f), shape)
            .clickable { onClick() }.padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // معاينة: أول ملصقين متحركين
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                list.take(2).forEach { f -> StickerBadgeView(StickerStore.idOf(sp, f), 46.dp, fps = 12) }
            }
        }
        Text(sp.title, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text("${list.size} ملصق", color = fg.copy(0.6f), fontSize = 11.5.sp)
    }
}


// ---------------------------------------------------------------------------
//  نافذة منبثقة خفيفة: تكبر من نقطة الرمز نفسه (origin) وتختفي لنفس النقطة.
//  ما فيها blur ولا زجاج ثقيل، والمحتوى ينحمّل بعد ما تخلص الحركة (حتى ما يعلّق التطبيق)
// ---------------------------------------------------------------------------
@Composable
internal fun BadgePopup(open: Boolean, origin: androidx.compose.ui.geometry.Offset, dark: Boolean, onDismiss: () -> Unit) {
    val p = remember { androidx.compose.animation.core.Animatable(0f) }
    var ready by remember { mutableStateOf(false) }
    val appCtx = LocalContext.current
    LaunchedEffect(open) {
        if (open) {
            StickerStore.prewarm(appCtx)
            p.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 420f))
            ready = true
        } else {
            ready = false
            p.animateTo(0f, tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        }
    }
    val shown by remember(open) { androidx.compose.runtime.derivedStateOf { open || p.value > 0.001f } }
    if (!shown) return

    val fg = if (dark) Color.White else Color(0xFF101418)
    val shape = RoundedCornerShape(34.dp)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val wDp = maxWidth - 28.dp
        val hDp = maxHeight * 0.78f
        val density = androidx.compose.ui.platform.LocalDensity.current
        val wPx = with(density) { wDp.toPx() }
        val hPx = with(density) { hDp.toPx() }
        val left = (with(density) { maxWidth.toPx() } - wPx) / 2f
        val top = (with(density) { maxHeight.toPx() } - hPx) / 2f
        val fx = if (wPx > 0f) (origin.x - left) / wPx else 0.5f
        val fy = if (hPx > 0f) (origin.y - top) / hPx else 0.5f

        // ستارة التعتيم
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = p.value.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
        Box(
            Modifier.align(Alignment.Center).size(wDp, hDp)
                .graphicsLayer {
                    val s = 0.06f + 0.94f * p.value
                    scaleX = s; scaleY = s
                    alpha = (p.value * 2f).coerceIn(0f, 1f)
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(fx, fy)
                }
                .clip(shape)
                .background(if (dark) Color(0xF2171A2B) else Color(0xF2FFFFFF))
                .border(
                    1.dp,
                    androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White.copy(0.55f), Color.White.copy(0.08f))),
                    shape
                )
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            if (ready) {
                StickerPickerContent(dark = dark, onDone = onDismiss)
            } else {
                Text("رمز الاسم", color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp))
            }
            // زر الإغلاق
            Box(
                Modifier.align(Alignment.TopEnd).padding(14.dp).size(36.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(fg.copy(0.12f)).clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) { Text("✕", color = fg, fontSize = 15.sp) }
        }
    }
}


// ---------------------------------------------------------------------------
//  ضغطة مطولة على الرمز = استعراض كبير. ما تمنع الضغطة العادية (الأب clickable يشتغل طبيعي)،
//  وبعد الضغط المطول تنبلع رفعة الإصبع حتى ما ينفذ الضغط العادي.
//  لازم تنحط بعد .clickable بالسلسلة (يعني تصير داخلية) حتى تلحق تبلع الرفعة
// ---------------------------------------------------------------------------
internal fun Modifier.onLongPressPreview(onLong: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var released = false
        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            waitForUpOrCancellation()
            released = true
        }
        if (!released) {
            onLong()
            waitForUpOrCancellation()?.consume()
        }
    }
}

/** ملصق Lottie بحجم كبير وحاد (يرسم الـ tgs مباشرة مو من الشيت الصغير) — للمعاينة فقط */
@Composable
private fun LottiePreview(pack: StickerPack, name: String, size: Dp) {
    val ctx = LocalContext.current.applicationContext
    val comp by produceState<com.airbnb.lottie.LottieComposition?>(null, pack.id, name) {
        value = withContext(Dispatchers.Default) {
            StickerStore.loadJson(ctx, "stickers/${pack.id}/$name.tgs")
                ?.let { LottieCompositionFactory.fromJsonStringSync(it, "P:${pack.id}/$name").value }
        }
    }
    val c = comp
    if (c == null) { Box(Modifier.size(size)); return }
    val drawable = remember(c) { LottieDrawable().also { it.setComposition(c) } }
    val t = rememberInfiniteTransition(label = "lottiePreview")
    val prog by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(c.duration.toInt().coerceAtLeast(500), easing = LinearEasing), RepeatMode.Restart),
        label = "lottiePreviewP"
    )
    Canvas(Modifier.size(size)) {
        drawIntoCanvas { cv ->
            drawable.setBounds(0, 0, this.size.width.roundToInt(), this.size.height.roundToInt())
            drawable.progress = prog
            drawable.draw(cv.nativeCanvas)
        }
    }
}

/**
 * نافذة استعراض كبيرة للرمز بـ Liquid Glass حقيقي:
 * خلفية أورورا بألوان الرمز نفسه (تتحرك ببطء) + بطاقة زجاجية تكسر الضوء فوقها،
 * توهج نابض خلف الرمز، والبطاقة تميل بالسحب (3D) وترجع بنابض. لمسة بالخلفية = إغلاق بحركة.
 */
@Composable
internal fun BadgePreviewDialog(id: String, onDismiss: () -> Unit) {
    val enter = remember { androidx.compose.animation.core.Animatable(0f) }
    val tiltX = remember { androidx.compose.animation.core.Animatable(0f) }
    val tiltY = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 340f)) }
    val close: () -> Unit = { scope.launch { enter.animateTo(0f, tween(150)); onDismiss() } }

    val def = remember(id) { Badges.byId(id)?.takeIf { it.kind != "emoji" } }
    val accent = def?.pal?.mid ?: Color(0xFF8B7CFF)
    val accent2 = def?.pal?.light ?: Color(0xFF5CC8FF)
    val accent3 = def?.pal?.dark ?: Color(0xFFE05CFF)

    val t = rememberInfiniteTransition(label = "badgePreviewFx")
    val drift by t.animateFloat(
        0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Restart), label = "drift"
    )
    val pulse by t.animateFloat(
        0.55f, 1f,
        infiniteRepeatable(tween(1900, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    val bd = rememberLayerBackdrop()
    val shape = RoundedCornerShape(46.dp)
    val density = androidx.compose.ui.platform.LocalDensity.current

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // الخلفية (هي اللي يكسرها الزجاج): تعتيم + كتل ضوء ملونة تدور ببطء
            Box(
                Modifier.fillMaxSize()
                    .layerBackdrop(bd)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null, onClick = close
                    )
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val e = enter.value.coerceIn(0f, 1f)
                    drawRect(Color(0xFF080A16).copy(alpha = 0.72f * e))
                    val w = size.width; val h = size.height
                    val r = size.minDimension * 0.62f
                    fun blob(c: Color, cx: Float, cy: Float, a: Float) = drawCircle(
                        Brush.radialGradient(listOf(c.copy(alpha = a * e), Color.Transparent), Offset(cx, cy), r),
                        r, Offset(cx, cy)
                    )
                    blob(accent, w * (0.5f + 0.28f * kotlin.math.cos(drift)), h * (0.42f + 0.10f * kotlin.math.sin(drift)), 0.55f)
                    blob(accent3, w * (0.5f + 0.30f * kotlin.math.cos(drift + 2.1f)), h * (0.58f + 0.10f * kotlin.math.sin(drift + 2.1f)), 0.45f)
                    blob(accent2, w * (0.5f + 0.26f * kotlin.math.cos(drift + 4.2f)), h * (0.50f + 0.14f * kotlin.math.sin(drift + 4.2f)), 0.35f)
                }
            }

            // البطاقة الزجاجية (خارج صندوق الـ layerBackdrop حتى ما يصير كراش)
            Box(
                Modifier.size(270.dp)
                    .graphicsLayer {
                        val s = 0.4f + 0.6f * enter.value
                        scaleX = s; scaleY = s
                        alpha = (enter.value * 2f).coerceIn(0f, 1f)
                        rotationX = tiltX.value
                        rotationY = tiltY.value
                        cameraDistance = 16f * density.density
                    }
                    .drawBackdrop(
                        backdrop = bd,
                        shape = { shape },
                        effects = {
                            vibrancy()
                            blur((6f.dp.toPx()) * GlassFx.blurK)
                            lens((24f.dp.toPx()) * GlassFx.refractK, (48f.dp.toPx()) * GlassFx.refractK)
                        },
                        onDrawSurface = {
                            glassSurfaceLayer {
                                drawRect(accent.copy(alpha = 0.10f))
                                drawRect(Color.White.copy(alpha = 0.07f))
                                drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.28f), 0.45f to Color.Transparent))
                            }
                        }
                    )
                    .border(
                        1.2.dp,
                        Brush.linearGradient(
                            listOf(Color.White.copy(0.85f), Color.White.copy(0.06f), accent.copy(0.55f)),
                            start = Offset(0f, 0f), end = Offset(400f, 700f)
                        ),
                        shape
                    )
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {}
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                scope.launch { tiltX.animateTo(0f, spring(0.4f, 180f)) }
                                scope.launch { tiltY.animateTo(0f, spring(0.4f, 180f)) }
                            },
                            onDragCancel = {
                                scope.launch { tiltX.animateTo(0f, spring(0.4f, 180f)) }
                                scope.launch { tiltY.animateTo(0f, spring(0.4f, 180f)) }
                            }
                        ) { change, d ->
                            change.consume()
                            scope.launch { tiltY.snapTo((tiltY.value + d.x * 0.14f).coerceIn(-20f, 20f)) }
                            scope.launch { tiltX.snapTo((tiltX.value - d.y * 0.14f).coerceIn(-20f, 20f)) }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // توهج نابض خلف الرمز
                Canvas(Modifier.size(230.dp)) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(accent2.copy(alpha = 0.50f * pulse), accent.copy(alpha = 0.22f * pulse), Color.Transparent),
                            center, size.minDimension / 2f
                        )
                    )
                }
                val parts = remember(id) { id.split(':') }
                val pack = if (parts.size == 3 && parts[0] == "s") StickerPacks.firstOrNull { it.id == parts[1] } else null
                if (pack != null && isLottieSticker(pack, parts[2])) LottiePreview(pack, parts[2], 190.dp)
                else BadgeView(id, 170.dp)
            }
        }
    }
}
