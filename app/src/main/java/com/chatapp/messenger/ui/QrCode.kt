package com.chatapp.messenger.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AColor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.Push
import com.chatapp.messenger.Repo
import com.chatapp.messenger.ui.glass.GlassButton
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * باركود الحساب: رابط خاص بكل مستخدم (يعتمد على uid الخاص به).
 * ينصنع أول مرة بس وينحفظ كصورة PNG بذاكرة التطبيق، وبعدها يطلع نفسه كل مرة.
 */
object QrUtil {
    private const val SIZE = 900

    /** الرابط الي يطلع لما أحد يمسح الباركود (صفحة على الـ Worker تفتح التطبيق على حساب المستخدم) */
    fun profileLink(uid: String): String = Push.PUSH_URL.trimEnd('/') + "/u/" + uid

    private fun fileFor(ctx: Context, uid: String, link: String): File {
        val dir = File(ctx.filesDir, "qr").apply { mkdirs() }
        return File(dir, "qr_${uid}_${link.hashCode().toUInt()}.png")
    }

    /** يرجع الباركود المحفوظ، وإذا ما موجود يصنعه ويحفظه (مرة وحدة) */
    suspend fun getOrCreate(ctx: Context, uid: String): Bitmap? = withContext(Dispatchers.IO) {
        if (uid.isBlank()) return@withContext null
        val link = profileLink(uid)
        val f = fileFor(ctx, uid, link)
        if (f.exists() && f.length() > 0L) {
            BitmapFactory.decodeFile(f.absolutePath)?.let { return@withContext it }
        }
        val bmp = runCatching { render(link) }.getOrNull() ?: return@withContext null
        runCatching {
            // نحذف أي باركود قديم لنفس المستخدم (إذا تغيّر الرابط) ونحفظ الجديد
            f.parentFile?.listFiles { x -> x.name.startsWith("qr_${uid}_") }?.forEach { it.delete() }
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        bmp
    }

    private fun render(text: String): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, SIZE, SIZE, hints)
        val w = m.width
        val h = m.height
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) px[y * w + x] = if (m[x, y]) AColor.BLACK else AColor.WHITE
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }
}

/** شاشة الباركود: بطاقة بيضاء بالباركود + الاسم + نسخ ومشاركة الرابط */
@Composable
fun QrCodeScreen(name: String, username: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val uid = remember { Repo.auth.currentUser?.uid ?: "" }
    val link = remember(uid) { QrUtil.profileLink(uid) }
    val qr by produceState<Bitmap?>(null, uid) { value = QrUtil.getOrCreate(ctx, uid) }

    BackHandler(onBack = onDismiss)
    GlassDialogRoot { bd ->
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }
                .statusBarsPadding().navigationBarsPadding()
        ) {
            Box(Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 12.dp)) {
                GlassButton(onClick = onDismiss, backdrop = bd, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(28.dp)).background(Color.White).padding(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val b = qr
                    if (b != null) {
                        Image(
                            bitmap = b.asImageBitmap(), contentDescription = "QR",
                            contentScale = ContentScale.Fit, modifier = Modifier.size(270.dp)
                        )
                    } else {
                        Box(Modifier.size(270.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF444444))
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
                Text(name.ifBlank { "حسابي" }, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (username.isNotBlank()) {
                    Text("@$username", color = Color(0xFFB0B3B8), fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Text(
                    "امسح الباركود بكاميرا الجوال أو كوكل ليفتح حسابي بالتطبيق",
                    color = Color(0xFFB0B3B8), fontSize = 13.sp,
                    modifier = Modifier.padding(top = 14.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassButton(
                        onClick = {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("link", link))
                            AppToast.show("اننسخ الرابط")
                        },
                        backdrop = bd, modifier = Modifier.height(46.dp)
                    ) {
                        Text("نسخ الرابط", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 22.dp))
                    }
                    GlassButton(
                        onClick = {
                            runCatching {
                                val i = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, link)
                                }
                                ctx.startActivity(Intent.createChooser(i, "مشاركة الحساب").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        },
                        backdrop = bd, modifier = Modifier.height(46.dp)
                    ) {
                        Text("مشاركة", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 22.dp))
                    }
                }
            }
        }
    }
}
