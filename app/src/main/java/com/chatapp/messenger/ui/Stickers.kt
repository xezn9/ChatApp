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
import com.kyant.backdrop.Backdrop
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

internal const val CHAT_PACK_ID = "chat"

internal class StickerPack(val id: String, val title: String, val color: Color, val dir: File? = null) {
    /** حزمة منزّلة من تيليجرام (ملفاتها بذاكرة الجهاز مو بالـ assets) */
    val downloaded: Boolean get() = dir != null
}

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

    /** ملصقات الدردشة (من الزيب): منفصلة عن أقسام رموز الاسم، ما تظهر بقائمتها */
    @Volatile private var chatPackRef: StickerPack? = null
    val chat: StickerPack? get() = chatPackRef
    /** كل الحزم للرسم (تشمل ملصقات الدردشة) */
    fun packById(id: String): StickerPack? = if (id == CHAT_PACK_ID) chatPackRef else packList.firstOrNull { it.id == id }
    fun allPacks(): List<StickerPack> = packList + listOfNotNull(chatPackRef)

    // أسماء الأقسام المخصّصة (يحددها المستخدم): packId -> الاسم
    @Volatile private var custom: Map<String, String> = emptyMap()
    private fun namePrefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("sticker_names", Context.MODE_PRIVATE)
    private fun loadCustom(ctx: Context) {
        custom = namePrefs(ctx).all.mapNotNull { (k, v) -> (v as? String)?.takeIf { it.isNotBlank() }?.let { k to it } }.toMap()
    }

    /** اسم القسم المعروض: المخصّص إذا موجود وإلا الافتراضي */
    fun titleOf(p: StickerPack): String = custom[p.id] ?: p.title

    /** تسمية قسم. الاسم الفاضي يرجّع الاسم الأصلي */
    fun rename(ctx: Context, id: String, name: String) {
        val n = name.trim().take(30)
        namePrefs(ctx).edit().apply { if (n.isBlank()) remove(id) else putString(id, n) }.apply()
        loadCustom(ctx)
    }

    @Synchronized
    fun ensure(ctx: Context) {
        if (scanned) return
        loadCustom(ctx)
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
        val extra = map.keys.filter { k -> k != CHAT_PACK_ID && KnownPacks.none { it.id == k } }.sortedBy { it.lowercase() }.map { autoPack(it) }
        // الحزم المنزّلة من تيليجرام: files/tgpacks/tg_<اسم>/
        val dl = ArrayList<StickerPack>()
        TelegramStickers.root(ctx).listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") && !it.name.contains(':') }
            ?.sortedBy { it.name.lowercase() }?.forEach { d ->
                if (map.containsKey(d.name)) return@forEach   // نفس اسم حزمة مدمجة بالتطبيق: نتجاهل المنزّلة
                val byName = LinkedHashMap<String, String>()
                (d.list()?.toList() ?: emptyList())
                    .filter { it.substringAfterLast('.', "").lowercase() in EXTS && !it.substringBeforeLast('.').contains(':') }
                    .sortedBy { EXTS.indexOf(it.substringAfterLast('.').lowercase()) }
                    .forEach { byName.putIfAbsent(it.substringBeforeLast('.'), it) }
                if (byName.isNotEmpty()) {
                    val auto = autoPack(d.name)
                    val title = try { org.json.JSONObject(File(d, "meta.json").readText()).optString("title") } catch (_: Exception) { "" }
                    map[d.name] = byName
                    dl += StickerPack(d.name, title.ifBlank { d.name.removePrefix("tg_") }, auto.color, d)
                }
            }
        // ملصقات الدردشة: files/chat_stickers/ (مجلد واحد بدون أقسام)
        val chatDir = File(ctx.filesDir, "chat_stickers")
        val chatNames = LinkedHashMap<String, String>()
        (chatDir.list()?.toList() ?: emptyList())
            .filter { it.substringAfterLast('.', "").lowercase() in EXTS && !it.substringBeforeLast('.').contains(':') }
            .sortedBy { EXTS.indexOf(it.substringAfterLast('.').lowercase()) }
            .forEach { chatNames.putIfAbsent(it.substringBeforeLast('.'), it) }
        if (chatNames.isNotEmpty()) {
            map[CHAT_PACK_ID] = chatNames
            chatPackRef = StickerPack(CHAT_PACK_ID, "ملصقات الدردشة", Color(0xFFFF2D92), chatDir)
        } else chatPackRef = null
        fileMap = map
        packList = known + extra + dl
        scanned = true
    }

    /** إعادة قراءة الأقسام (بعد تنزيل أو حذف حزمة) */
    fun refresh(ctx: Context) {
        scanned = false
        StickerStore.reset()
        ensure(ctx)
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

    /** مسح الكاشات بعد تغيّر الأقسام */
    @Synchronized
    fun reset() {
        files.clear()
        jsonCache.evictAll()
        sheetCache.evictAll()
        synchronized(framesCache) { framesCache.clear() }
    }

    /** فتح ملف ملصق: من ذاكرة الجهاز (حزم تيليجرام) أو من الـ assets */
    fun open(ctx: Context, pack: StickerPack, file: String): java.io.InputStream =
        pack.dir?.let { File(it, file).inputStream() } ?: ctx.assets.open("stickers/${pack.id}/$file")

    /** أسماء ملفات الحزمة مرتبة */
    @Synchronized
    fun files(ctx: Context, pack: StickerPack): List<String> {
        StickerRegistry.ensure(ctx)
        return files.getOrPut(pack.id) { StickerRegistry.filesOf(pack.id) }
    }

    fun idOf(pack: StickerPack, file: String) = "s:${pack.id}:${file.substringBeforeLast('.')}"

    suspend fun loadJson(ctx: Context, pack: StickerPack, file: String): String? {
        val path = "${pack.id}/$file"
        jsonCache.get(path)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                GZIPInputStream(open(ctx, pack, file)).bufferedReader().use { it.readText() }
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
            val json = loadJson(ctx, pack, "$name.tgs") ?: return null
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
            for (pack in StickerRegistry.allPacks()) {
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
    suspend fun loadSheet(ctx: Context, pack: StickerPack, file: String, name: String): Pair<ImageBitmap, Int>? {
        val path = "${pack.id}/$file"
        sheetCache.get(path)?.let { bmp -> return bmp to synchronized(framesCache) { framesCache[path] ?: 1 } }
        return withContext(Dispatchers.IO) {
            try {
                val b = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                open(ctx, pack, file).use { android.graphics.BitmapFactory.decodeStream(it, null, b) }
                val frames = framesFor(name, b.outWidth, b.outHeight)
                val o = android.graphics.BitmapFactory.Options()
                if (frames == 1) {
                    var sample = 1
                    while (maxOf(b.outWidth, b.outHeight) / (sample * 2) >= 256) sample *= 2
                    o.inSampleSize = sample
                }
                val bmp = open(ctx, pack, file).use { android.graphics.BitmapFactory.decodeStream(it, null, o) }?.asImageBitmap()
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
    val pack = StickerRegistry.packById(parts[1])
    if (pack == null) {
        // حزمة منزّلة من تيليجرام وما موجودة عندي: أسحب الرمز من السيرفر (رفعه صاحبه)
        if (parts[1].startsWith("tg_")) RemoteSticker(id, size, modifier)
        return
    }
    val name = parts[2]
    if (isLottieSticker(pack, name)) CachedSpriteSticker(pack, name, size, modifier)
    else {
        val file = StickerRegistry.fileOf(pack.id, name) ?: return
        SpriteSticker(pack, file, name, size, modifier)
    }
}

@Composable
private fun RemoteSticker(id: String, size: Dp, modifier: Modifier) {
    val ctx = LocalContext.current.applicationContext
    val res by produceState<Pair<ImageBitmap, Int>?>(null, id) { value = BadgeSync.fetch(ctx, id) }
    val r = res
    if (r == null) { Box(modifier.size(size)); return }
    SpriteView(r.first, r.second, size, modifier)
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
private fun SpriteSticker(pack: StickerPack, file: String, name: String, size: Dp, modifier: Modifier) {
    val ctx = LocalContext.current.applicationContext
    val sheet by produceState<Pair<ImageBitmap, Int>?>(null, pack.id, file) { value = StickerStore.loadSheet(ctx, pack, file, name) }
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
internal fun StickerPickerContent(
    dark: Boolean,
    onDone: () -> Unit,
    // إذا مُرّر: الاختيار يرجع للمستدعي بدل ما يصير رمز الاسم (مثلاً ملصق مقدمة الدردشة)
    onPick: ((String) -> Unit)? = null,
    currentId: String? = null,
    heading: String = "رمز الاسم",
    subheading: String = "اختر ملصق متحرك يظهر جنب اسمك عند الجميع"
) {
    val fg = if (dark) Color.White else Color(0xFF101418)
    val ctx = LocalContext.current
    StickerRegistry.ensure(ctx)
    val me = remember { Repo.auth.currentUser?.uid ?: "" }
    val prof by remember(me) { Repo.basicProfileFlow(me) }.collectAsState(initial = Profile())
    val curId = currentId ?: prof.badge
    fun pick(id: String) { if (onPick != null) onPick(id) else Repo.setBadge(id) }
    var pack by remember { mutableStateOf<StickerPack?>(null) }
    var previewId by remember { mutableStateOf<String?>(null) }
    var rev by remember { mutableStateOf(0) }                       // يتغير بعد تنزيل/حذف حزمة لتحديث القوائم
    var showDownload by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<StickerPack?>(null) }
    var renameTarget by remember { mutableStateOf<StickerPack?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    BackHandler(enabled = pack != null) { pack = null }
    // رمزي الحالي إذا من حزمة منزّلة: نتأكد إنه مرفوع حتى يشوفه الكل (يغطي الي اختاروه قبل هذي الميزة)
    LaunchedEffect(curId) { if (curId.startsWith("s:tg_")) BadgeSync.ensureUploaded(ctx, curId) }
    previewId?.let { BadgePreviewDialog(it) { previewId = null } }
    val red = Color(0xFFFF5A5F)
    Box(Modifier.fillMaxSize()) {
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
                Text(heading, color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(subheading, color = fg.copy(0.6f), fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(16.dp))
                // بدون رمز
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(fg.copy(if (curId.isBlank()) 0.16f else 0.08f))
                        .clickable { pick(""); onDone() }.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) { Text(if (curId.isBlank()) "بدون ملصق ✓" else "بدون ملصق", color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(10.dp))
                // تنزيل ملصقات من تيليجرام (رابط الحزمة) وتنضاف كقسم جديد
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF4DA3FF).copy(0.16f))
                        .border(1.dp, Color(0xFF4DA3FF).copy(0.5f), RoundedCornerShape(18.dp))
                        .clickable { showDownload = true }.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) { Text("⬇  تنزيل ملصقات من تيليجرام", color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(14.dp))
                // الأقسام (2 بكل صف) والصفحة تنسحب إذا الأقسام أكثر من الشاشة
                val allPacks = remember(rev) { StickerPacks }
                allPacks.chunked(2).forEach { rowPacks ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowPacks.forEach { sp ->
                            PackCard(sp, StickerRegistry.titleOf(sp), fg, Modifier.weight(1f).height(160.dp)) { pack = sp }
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
                    remember(p, rev) { StickerRegistry.titleOf(p) }, color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${list.size} ملصق", color = fg.copy(0.6f), fontSize = 12.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(fg.copy(0.10f))
                            .clickable { pack = null }.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) { Text("‹ الأقسام", color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(fg.copy(0.10f))
                            .clickable { renameTarget = p }.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) { Text("✏ تسمية", color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    if (p.downloaded) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(red.copy(0.16f))
                                .clickable { confirmDelete = p }.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) { Text("🗑 حذف", color = red, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
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
                        val sel = curId == id
                        val shape = RoundedCornerShape(16.dp)
                        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
                                .background(fg.copy(if (sel) 0.18f else 0.07f))
                                .border(if (sel) 2.dp else 0.8.dp, if (sel) p.color else fg.copy(0.14f), shape)
                                .clickable { pick(id); BadgeSync.ensureUploaded(ctx, id); onDone() }
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

    if (showDownload) {
        StickerDownloadDialog(
            dark = dark,
            onDismiss = { showDownload = false },
            onDownloaded = { StickerRegistry.refresh(ctx); rev++; showDownload = false }
        )
    }
    renameTarget?.let { t ->
        var text by remember(t.id) { mutableStateOf(StickerRegistry.titleOf(t)) }
        PickerOverlay(dark, onDismiss = { renameTarget = null }) {
            Text("تسمية القسم", color = fg, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("اتركه فاضي للرجوع للاسم الأصلي", color = fg.copy(0.6f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(14.dp))
            DlField(text, { text = it }, "اسم القسم", fg, true, androidx.compose.ui.text.input.KeyboardType.Text, ltr = false)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OverlayBtn("إلغاء", fg.copy(0.10f), fg) { renameTarget = null }
                OverlayBtn("حفظ", Color(0xFF4DA3FF), Color.White) {
                    StickerRegistry.rename(ctx, t.id, text)
                    renameTarget = null
                    rev++
                }
            }
        }
    }
    confirmDelete?.let { target ->
        PickerOverlay(dark, onDismiss = { confirmDelete = null }) {
            Text("حذف الحزمة", color = fg, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                "تنحذف حزمة \"${StickerRegistry.titleOf(target)}\" من جهازك. تقدر تنزّلها مرة ثانية بأي وقت.",
                color = fg.copy(0.7f), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OverlayBtn("إلغاء", fg.copy(0.10f), fg) { confirmDelete = null }
                OverlayBtn("حذف", red, Color.White) {
                    confirmDelete = null
                    scope.launch {
                        // إذا رمزي الحالي من هذي الحزمة نرجعه بدون رمز
                        if (prof.badge.startsWith("s:${target.id}:")) Repo.setBadge("")
                        withContext(Dispatchers.IO) { TelegramStickers.delete(ctx, target.id) }
                        StickerRegistry.rename(ctx, target.id, "")
                        StickerRegistry.refresh(ctx)
                        pack = null
                        rev++
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun PackCard(sp: StickerPack, title: String, fg: Color, modifier: Modifier, onClick: () -> Unit) {
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
        Text(title, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text("${list.size} ملصق", color = fg.copy(0.6f), fontSize = 11.5.sp)
    }
}


// ---------------------------------------------------------------------------
//  نافذة منبثقة خفيفة: تكبر من نقطة الرمز نفسه (origin) وتختفي لنفس النقطة.
//  ما فيها blur ولا زجاج ثقيل، والمحتوى ينحمّل بعد ما تخلص الحركة (حتى ما يعلّق التطبيق)
// ---------------------------------------------------------------------------
@Composable
internal fun BadgePopup(
    open: Boolean, origin: androidx.compose.ui.geometry.Offset, dark: Boolean, backdrop: Backdrop, onDismiss: () -> Unit,
    onPick: ((String) -> Unit)? = null,
    currentId: String? = null,
    heading: String = "رمز الاسم",
    subheading: String = "اختر ملصق متحرك يظهر جنب اسمك عند الجميع"
) {
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
                // Liquid Glass حقيقي (نفس نوافذ التطبيق): blur + vibrancy + lens فوق خلفية التطبيق
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur((16f.dp.toPx()) * GlassFx.blurK)
                        lens((12f.dp.toPx()) * GlassFx.refractK, (24f.dp.toPx()) * GlassFx.refractK)
                    },
                    onDrawSurface = { glassSurfaceLayer { drawRect(if (dark) Color.Black.copy(0.42f) else Color.White.copy(0.55f)) } }
                )
                .border(0.8.dp, glassBorderBrush(0.45f), shape)
                .clip(shape)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            if (ready) {
                StickerPickerContent(dark = dark, onDone = onDismiss, onPick = onPick, currentId = currentId, heading = heading, subheading = subheading)
            } else {
                Text(heading, color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold,
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
            StickerStore.loadJson(ctx, pack, "$name.tgs")
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
