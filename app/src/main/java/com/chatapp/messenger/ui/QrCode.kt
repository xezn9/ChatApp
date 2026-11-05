package com.chatapp.messenger.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.Push
import com.chatapp.messenger.Repo
import com.chatapp.messenger.ui.glass.GlassButton
import com.chatapp.messenger.ui.theme.ThemeSettings
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** رابط الحساب: خاص بكل مستخدم (يعتمد على uid الخاص به) */
object QrUtil {
    /** الرابط الي يطلع لما أحد يمسح الباركود (صفحة على الـ Worker تفتح التطبيق على حساب المستخدم) */
    fun profileLink(uid: String): String = Push.PUSH_URL.trimEnd('/') + "/u/" + uid

    /** مصفوفة الباركود (true = مربع غامق). مستوى تصحيح H حتى يتحمل الشعار بالنص */
    fun matrix(text: String): Array<BooleanArray>? = runCatching {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H
        )
        val m = Encoder.encode(text, ErrorCorrectionLevel.H, hints).matrix
        Array(m.height) { y -> BooleanArray(m.width) { x -> m.get(x, y).toInt() == 1 } }
    }.getOrNull()
}

/** ثيم ألوان الباركود: تدرج الخلفية + تدرج لون الباركود نفسه (مثل تيليجرام) */
private class QrTheme(val bgTop: Color, val bgBottom: Color, val qrTop: Color, val qrBottom: Color)

private val qrThemes: List<QrTheme> = listOf(
    QrTheme(Color(0xFF2B7BE4), Color(0xFF16337F), Color(0xFF1B64D1), Color(0xFF0E2A6E)), // أزرق
    QrTheme(Color(0xFF0F2A4A), Color(0xFF050B18), Color(0xFF1558B8), Color(0xFF0A1F52)), // ليلي
    QrTheme(Color(0xFF8A5CFF), Color(0xFF3B1C96), Color(0xFF6A3BE0), Color(0xFF34168A)), // بنفسجي
    QrTheme(Color(0xFFFF6FB5), Color(0xFFB0145A), Color(0xFFD81B77), Color(0xFF86104A)), // وردي
    QrTheme(Color(0xFFFF7A6B), Color(0xFFB71C1C), Color(0xFFD32F2F), Color(0xFF831818)), // أحمر
    QrTheme(Color(0xFFFFB03A), Color(0xFFD84300), Color(0xFFE05A00), Color(0xFF953800)), // برتقالي
    QrTheme(Color(0xFF4BD98A), Color(0xFF0A7A44), Color(0xFF0C8A49), Color(0xFF065A30)), // أخضر
    QrTheme(Color(0xFF2FD6D2), Color(0xFF17798F), Color(0xFF0E8A9C), Color(0xFF0A5062)), // تركوازي
    QrTheme(Color(0xFF3A3A3F), Color(0xFF000000), Color(0xFF1C1C1E), Color(0xFF000000))  // أسود
)

/** الخيار الأول يتبع لون التطبيق الحالي */
private fun appAccentTheme(): QrTheme {
    val a = ThemeSettings.accent
    return QrTheme(a, lerp(a, Color.Black, 0.55f), lerp(a, Color.Black, 0.35f), lerp(a, Color.Black, 0.65f))
}

private fun themeAt(i: Int): QrTheme = if (i <= 0) appAccentTheme() else qrThemes[(i - 1).coerceIn(0, qrThemes.lastIndex)]

private object QrStyle {
    private const val PREFS = "qr_style"
    fun load(ctx: Context): Int = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("theme", 0)
    fun save(ctx: Context, i: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("theme", i).apply()
    }
}

/**
 * الباركود يترسم بالكانفاس (مو صورة) حتى يتغير لونه فوراً:
 * مربعات بزوايا مدورة شوي + علامات الزوايا الثلاث دائرية الحواف + فراغ بالنص للشعار.
 */
@Composable
private fun StyledQr(matrix: Array<BooleanArray>, top: Color, bottom: Color, size: androidx.compose.ui.unit.Dp) {
    val n = matrix.size
    // مساحة الشعار بالنص (عدد فردي حتى يتمركز بالضبط)
    val logoCells = (((n * 0.27f).toInt()) or 1).coerceAtLeast(5)
    val logoFrom = (n - logoCells) / 2
    val logoTo = logoFrom + logoCells - 1

    fun inFinder(x: Int, y: Int): Boolean {
        val a = x < 8 && y < 8
        val b = x >= n - 8 && y < 8
        val c = x < 8 && y >= n - 8
        return a || b || c
    }

    Canvas(Modifier.size(size)) {
        val cell = this.size.width / n
        val brush = Brush.linearGradient(
            listOf(top, bottom),
            start = Offset(0f, 0f),
            end = Offset(this.size.width, this.size.height)
        )
        // مربعات البيانات
        val data = Path()
        val r = cell * 0.32f
        for (y in 0 until n) for (x in 0 until n) {
            if (!matrix[y][x] || inFinder(x, y)) continue
            if (x in logoFrom..logoTo && y in logoFrom..logoTo) continue
            data.addRoundRect(RoundRect(x * cell, y * cell, (x + 1) * cell, (y + 1) * cell, CornerRadius(r, r)))
        }
        drawPath(data, brush)
        // علامات الزوايا: حلقة خارجية + مربع داخلي، كلها دائرية الحواف
        val corners = listOf(0 to 0, n - 7 to 0, 0 to n - 7)
        for ((cx, cy) in corners) {
            val ox = cx * cell
            val oy = cy * cell
            val ring = Path().apply {
                fillType = PathFillType.EvenOdd
                addRoundRect(RoundRect(ox, oy, ox + 7 * cell, oy + 7 * cell, CornerRadius(cell * 2.1f, cell * 2.1f)))
                addRoundRect(RoundRect(ox + cell, oy + cell, ox + 6 * cell, oy + 6 * cell, CornerRadius(cell * 1.3f, cell * 1.3f)))
            }
            drawPath(ring, brush)
            drawPath(
                Path().apply {
                    addRoundRect(RoundRect(ox + 2 * cell, oy + 2 * cell, ox + 5 * cell, oy + 5 * cell, CornerRadius(cell * 1.0f, cell * 1.0f)))
                },
                brush
            )
        }
    }
}

/** شاشة الباركود: صورة المستخدم فوق + باركود ملوّن بشعار التطبيق + اختيار الألوان + نسخ ومشاركة */
@Composable
fun QrCodeScreen(name: String, username: String, avatar: String = "", onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val uid = remember { Repo.auth.currentUser?.uid ?: "" }
    val link = remember(uid) { QrUtil.profileLink(uid) }
    val matrix = remember(link) { if (uid.isBlank()) null else QrUtil.matrix(link) }
    val avatarImg = rememberB64Image(avatar)

    var themeIdx by remember { mutableIntStateOf(QrStyle.load(ctx).coerceIn(0, qrThemes.size)) }
    val th = themeAt(themeIdx)
    val bgTop by animateColorAsState(th.bgTop, tween(450), label = "bgTop")
    val bgBottom by animateColorAsState(th.bgBottom, tween(450), label = "bgBottom")
    val qrTop by animateColorAsState(th.qrTop, tween(450), label = "qrTop")
    val qrBottom by animateColorAsState(th.qrBottom, tween(450), label = "qrBottom")

    BackHandler(onBack = onDismiss)
    val bd = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.matchParentSize().layerBackdrop(bd)
                .background(Brush.verticalGradient(listOf(bgTop, bgBottom)))
        )
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
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(top = 64.dp, bottom = 20.dp, start = 28.dp, end = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // الصورة الشخصية فوق، نص منها داخل البطاقة
                Box(contentAlignment = Alignment.TopCenter) {
                    Box(
                        Modifier.padding(top = 48.dp)
                            .clip(RoundedCornerShape(30.dp)).background(Color.White)
                            .padding(start = 18.dp, end = 18.dp, top = 58.dp, bottom = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val m = matrix
                        Box(contentAlignment = Alignment.Center) {
                            if (m != null) {
                                StyledQr(m, qrTop, qrBottom, 260.dp)
                                // شعار التطبيق بالنص (حسب الأيقونة المختارة بالإعدادات)
                                Box(
                                    Modifier.size(62.dp).clip(RoundedCornerShape(18.dp)).background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(ThemeSettings.icons[ThemeSettings.iconIndex].previewRes),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(54.dp).clip(RoundedCornerShape(14.dp))
                                    )
                                }
                            } else {
                                Spacer(Modifier.size(260.dp))
                            }
                        }
                    }
                    // الصورة الشخصية (دائرة بإطار بلون البطاقة)
                    Box(
                        Modifier.size(104.dp).clip(CircleShape).background(Color.White).padding(5.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val a = avatarImg
                        if (a != null) {
                            Image(
                                bitmap = a, contentDescription = null,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                Modifier.fillMaxSize().background(Brush.linearGradient(listOf(qrTop, qrBottom))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    name.trim().take(1).ifBlank { "؟" }, color = Color.White,
                                    fontSize = 40.sp, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(name.ifBlank { "حسابي" }, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (username.isNotBlank()) {
                    Text("@$username", color = Color.White.copy(0.75f), fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Text(
                    "امسح الباركود بكاميرا الجوال أو كوكل ليفتح حسابي بالتطبيق",
                    color = Color.White.copy(0.7f), fontSize = 13.sp,
                    modifier = Modifier.padding(top = 12.dp), textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(22.dp))
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
                Spacer(Modifier.height(26.dp))
                Text("الألوان", color = Color.White.copy(0.8f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                // صف الألوان: أول خيار يتبع لون التطبيق، والباقي ثيمات جاهزة
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    for (i in 0..qrThemes.size) {
                        val t = themeAt(i)
                        val selected = i == themeIdx
                        Box(
                            Modifier.size(42.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(t.bgTop, t.bgBottom)))
                                .border(if (selected) 3.dp else 1.dp, Color.White.copy(if (selected) 1f else 0.35f), CircleShape)
                                .clickable { themeIdx = i; QrStyle.save(ctx, i) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
