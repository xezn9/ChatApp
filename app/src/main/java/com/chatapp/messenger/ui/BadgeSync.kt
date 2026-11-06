package com.chatapp.messenger.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

// ============================================================================
//  مزامنة رموز الاسم المنزّلة من تيليجرام حتى يشوفها كل المستخدمين حتى لو ما نزّلوا الحزمة.
//
//  - لما تختار ملصق من حزمة منزّلة: ينرفع شيت الإطارات الجاهز (webp صغير) لـ Firestore: badgeAssets/<حزمة>__<ملصق>
//  - الشخص الثاني إذا ما عنده الحزمة، يسحب الشيت من Firestore وينحفظ بذاكرة جهازه (مرة وحدة) ويتعرض مثل أي رمز.
//  - المعرّف بالملف الشخصي يبقى نفسه "s:tg_<حزمة>:<ملصق>" فما يتغير شي بباقي التطبيق.
// ============================================================================
internal object BadgeSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val mem = LruCache<String, Pair<ImageBitmap, Int>>(40)
    private val checked = HashSet<String>()      // ما نعيد فحص/رفع نفس الرمز بنفس الجلسة
    private val missing = HashMap<String, Long>() // رموز مو موجودة بالسيرفر: ما نعيد المحاولة قبل دقيقتين

    /** "s:tg_x:s001" -> (pack, name) أو null */
    private fun split(id: String): Pair<String, String>? {
        val p = id.split(':')
        return if (p.size == 3 && p[0] == "s" && p[1].startsWith("tg_")) p[1] to p[2] else null
    }
    private fun docId(pack: String, name: String) = "${pack}__$name"

    /** يرفع شيت الرمز إذا الحزمة منزّلة عندي وما كان مرفوع. يشتغل بالخلفية وما يعطّل شي */
    fun ensureUploaded(ctx: Context, id: String) {
        val (packId, name) = split(id) ?: return
        val app = ctx.applicationContext
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        synchronized(checked) { if (!checked.add(id)) return }
        scope.launch {
            try {
                StickerRegistry.ensure(app)
                val pack = StickerPacks.firstOrNull { it.id == packId && it.downloaded } ?: run { synchronized(checked) { checked.remove(id) }; return@launch }
                val ref = db.collection("badgeAssets").document(docId(packId, name))
                if (try { ref.get().await().exists() } catch (_: Exception) { false }) return@launch
                val (bmp, frames) = (
                    if (isLottieSticker(pack, name)) StickerStore.lottieSheet(app, pack, name, urgent = false)
                    else StickerRegistry.fileOf(pack.id, name)?.let { StickerStore.loadSheet(app, pack, it, name) }
                ) ?: run { synchronized(checked) { checked.remove(id) }; return@launch }
                val bytes = encode(bmp.asAndroidBitmap()) ?: return@launch
                ref.set(mapOf("sheet" to Blob.fromBytes(bytes), "frames" to frames, "by" to uid, "at" to FieldValue.serverTimestamp())).await()
            } catch (_: Exception) {
                synchronized(checked) { checked.remove(id) }   // نعيد المحاولة لاحقاً
            }
        }
    }

    private fun encode(b: Bitmap): ByteArray? {
        for (q in intArrayOf(80, 60, 40)) {
            val out = ByteArrayOutputStream()
            val fmt = if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY
            else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
            b.compress(fmt, q, out)
            if (out.size() < 800_000) return out.toByteArray()
        }
        return null
    }

    /** يجيب شيت رمز مو موجود محلياً: من كاش الجهاز أو من Firestore. يرجع (الشيت، عدد الإطارات) */
    suspend fun fetch(ctx: Context, id: String): Pair<ImageBitmap, Int>? {
        val (packId, name) = split(id) ?: return null
        val key = docId(packId, name)
        mem.get(key)?.let { return it }
        val dir = File(ctx.applicationContext.filesDir, "remote_badges").also { it.mkdirs() }
        return withContext(Dispatchers.IO) {
            // كاش القرص: <key>_<frames>.webp
            dir.listFiles()?.firstOrNull { it.name.startsWith("${key}_") && it.name.endsWith(".webp") }?.let { f ->
                val n = f.name.removePrefix("${key}_").removeSuffix(".webp").toIntOrNull()
                val bmp = try { BitmapFactory.decodeFile(f.path)?.asImageBitmap() } catch (_: Exception) { null }
                if (bmp != null && n != null) return@withContext (bmp to n).also { mem.put(key, it) }
                f.delete()
            }
            val now = System.currentTimeMillis()
            synchronized(missing) { missing[key]?.let { if (now - it < 120_000) return@withContext null } }
            try {
                val d = db.collection("badgeAssets").document(key).get().await()
                val bytes = d.getBlob("sheet")?.toBytes()
                val frames = d.getLong("frames")?.toInt()
                if (bytes == null || frames == null) {
                    synchronized(missing) { missing[key] = now }
                    return@withContext null
                }
                File(dir, "${key}_$frames.webp").writeBytes(bytes)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() ?: return@withContext null
                (bmp to frames).also { mem.put(key, it) }
            } catch (_: Exception) {
                synchronized(missing) { missing[key] = now }
                null
            }
        }
    }
}
