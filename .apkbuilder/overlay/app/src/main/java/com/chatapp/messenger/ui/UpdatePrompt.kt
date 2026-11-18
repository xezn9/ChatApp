package com.chatapp.messenger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.repeatCount
import com.chatapp.messenger.InstallResult
import com.chatapp.messenger.UpdateInfo
import com.chatapp.messenger.Updater
import com.chatapp.messenger.ui.glass.GlassButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/**
 * شاشة "تحديث جديد" الزجاجية. تظهر مرة وحدة عند فتح التطبيق إذا versionCode المنشور
 * بـ Firestore (app_config/android) أكبر من المثبّت. التنزيل والتثبيت من داخل التطبيق،
 * والنظام يحدّث فوق النسخة القديمة بدون حذفها.
 *
 * صورة الإعلان (الحقل imageUrl بـ Firestore) تدعم PNG / JPG / WebP / GIF المتحرك (يتكرر للأبد).
 * الأسطر بحقل notes تظهر كنقاط تحت عنوان "ما الجديد" (كل سطر = نقطة).
 */
@Composable
fun UpdatePrompt() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var dismissed by rememberSaveable { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var downloading by remember { mutableStateOf(false) }
    var apk by remember { mutableStateOf<File?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var imageFailed by remember { mutableStateOf(false) }
    var imageLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { info = Updater.check(ctx) }

    val i = info ?: return
    if (dismissed && !i.force) return

    BackHandler(enabled = !i.force) {
        job?.cancel()
        dismissed = true
    }

    fun startInstall(file: File) {
        when (Updater.install(ctx, file)) {
            InstallResult.STARTED -> message = null
            InstallResult.NEED_PERMISSION ->
                message = "فعّل خيار «السماح بالتثبيت من هذا المصدر» ثم ارجع واضغط تثبيت"
            InstallResult.FAILED -> message = "ما گدرت أفتح المثبّت، جرّب مرة ثانية"
        }
    }

    fun startDownload() {
        if (downloading) return
        downloading = true
        progress = 0f
        message = null
        job = scope.launch {
            try {
                val f = Updater.download(ctx, i) { progress = it }
                apk = f
                downloading = false
                startInstall(f)
            } catch (e: CancellationException) {
                downloading = false
                throw e
            } catch (e: Exception) {
                downloading = false
                message = e.localizedMessage ?: "فشل التحديث، جرّب مرة ثانية"
            }
        }
    }

    // حركة دخول ناعمة (يطلع من تحت + يظهر تدريجياً)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val enter by animateFloatAsState(if (shown) 1f else 0f, tween(520), label = "enter")

    GlassAuthScaffold { backdrop, dark ->
        val fg = glassFg(dark)
        val hasImage = i.imageUrl.isNotBlank() && !imageFailed

        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = enter
                    translationY = (1f - enter) * 48.dp.toPx()
                    val s = 0.96f + 0.04f * enter
                    scaleX = s; scaleY = s
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ───────────── الصورة / الأيقونة ─────────────
            if (hasImage) {
                val shape = RoundedCornerShape(28.dp)
                // GIF و WebP المتحرك: نستخدم نفس ImageLoader حق التطبيق (يفك GIF) ونكرره للأبد
                val request = remember(i.imageUrl) {
                    ImageRequest.Builder(ctx)
                        .data(i.imageUrl)
                        .repeatCount(-1)
                        .crossfade(true)
                        .build()
                }
                Box(
                    Modifier
                        .widthIn(max = 320.dp)
                        .fillMaxWidth()
                        .shadow(22.dp, shape, ambientColor = GlassAccent.copy(0.35f), spotColor = GlassAccent.copy(0.45f))
                        .clip(shape)
                        .background(fg.copy(alpha = 0.06f))
                        .border(0.8.dp, glassBorderBrush(0.5f), shape)
                        .animateContentSize()
                        .heightIn(min = if (imageLoaded) 0.dp else 200.dp)
                ) {
                    AsyncImage(
                        model = request,
                        imageLoader = GifLoader.get(ctx),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        onSuccess = { imageLoaded = true },
                        onError = { imageFailed = true },
                        modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp)
                    )
                    if (!imageLoaded) {
                        CircularProgressIndicator(
                            color = GlassAccent,
                            strokeWidth = 3.dp,
                            modifier = Modifier.align(Alignment.Center).size(32.dp)
                        )
                    } else {
                        // شارة "جديد" فوق الصورة
                        Row(
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .clip(CircleShape)
                                .background(GlassAccent)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(AppIcons.Sparkles, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("جديد", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // أيقونة التنزيل مع موجات نبض هادئة (تتوقف إذا الجهاز بوضع الأداء الخفيف)
                val pulse = if (PerfGate.lite) null else rememberInfiniteTransition(label = "pulse")
                val t by (pulse?.animateFloat(
                    0f, 1f,
                    infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
                    label = "t"
                ) ?: remember { mutableStateOf(0f) })
                Box(
                    Modifier
                        .size(150.dp)
                        .drawBehind {
                            if (pulse == null) return@drawBehind
                            for (k in 0..1) {
                                val p = (t + k * 0.5f) % 1f
                                drawCircle(
                                    color = GlassAccent.copy(alpha = (1f - p) * 0.45f),
                                    radius = size.minDimension / 2f * (0.62f + 0.38f * p),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.size(92.dp).glassCircle(backdrop, dark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(AppIcons.Download, null, tint = GlassAccent, modifier = Modifier.size(44.dp))
                    }
                }
            }

            // ───────────── العنوان + الإصدارات ─────────────
            Spacer(Modifier.height(20.dp))
            Text(
                "تحديث جديد متوفر",
                color = fg, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            val now = Updater.installedVersionName(ctx)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VersionChip(
                    text = "الإصدار ${i.versionName.ifBlank { i.versionCode.toString() }}",
                    bg = GlassAccent, fg = Color.White, bold = true
                )
                if (now.isNotBlank()) {
                    VersionChip(
                        text = "عندك $now",
                        bg = fg.copy(alpha = 0.10f), fg = fg.copy(alpha = 0.75f), bold = false
                    )
                }
            }
            if (i.force) {
                Spacer(Modifier.height(10.dp))
                VersionChip(
                    text = "تحديث إلزامي لمتابعة استخدام التطبيق",
                    bg = Color(0xFFFF9F0A).copy(alpha = 0.18f),
                    fg = if (dark) Color(0xFFFFC266) else Color(0xFFB36B00),
                    bold = true
                )
            }
            Spacer(Modifier.height(20.dp))

            // ───────────── اللوحة الزجاجية ─────────────
            Column(
                Modifier.widthIn(max = 440.dp).fillMaxWidth().glassPanel(backdrop, dark).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ما الجديد
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Sparkles, null, tint = GlassAccent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ما الجديد", color = fg, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                val lines = remember(i.notes) {
                    i.notes.lines()
                        .map { it.trim().trimStart('-', '•', '*', '·').trim() }
                        .filter { it.isNotEmpty() }
                }
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 190.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when {
                        lines.isEmpty() -> Text(
                            "نسخة محسّنة من التطبيق. التحديث يتثبت فوق نسختك الحالية وما تفقد شي.",
                            color = fg.copy(alpha = 0.9f), fontSize = 15.sp, lineHeight = 23.sp
                        )
                        lines.size == 1 -> Text(
                            lines[0], color = fg.copy(alpha = 0.9f), fontSize = 15.sp, lineHeight = 23.sp
                        )
                        else -> lines.forEach { line ->
                            Row(verticalAlignment = Alignment.Top) {
                                Box(
                                    Modifier.padding(top = 9.dp).size(7.dp).clip(CircleShape).background(GlassAccent)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    line, color = fg.copy(alpha = 0.9f), fontSize = 15.sp,
                                    lineHeight = 23.sp, modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // شريط التقدم (يظهر وقت التنزيل فقط)
                if (downloading) {
                    val frac by animateFloatAsState(
                        progress.coerceIn(0f, 1f), tween(250, easing = LinearEasing), label = "prog"
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (progress < 0f) {
                            // حجم الملف مجهول: شريط يروح ويجي
                            val inf = rememberInfiniteTransition(label = "indet")
                            val x by inf.animateFloat(
                                -1f, 1f,
                                infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
                                label = "x"
                            )
                            Box(
                                Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(fg.copy(alpha = 0.12f)),
                                contentAlignment = BiasAlignment(x, 0f)
                            ) {
                                Box(Modifier.fillMaxWidth(0.3f).fillMaxHeight().clip(CircleShape).background(GlassAccent))
                            }
                            Text("جاري التنزيل...", color = fg.copy(alpha = 0.7f), fontSize = 13.sp)
                        } else {
                            Box(
                                Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(fg.copy(alpha = 0.12f))
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(frac.coerceAtLeast(0.03f))
                                        .fillMaxHeight()
                                        .clip(CircleShape)
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(GlassAccent.copy(alpha = 0.75f), GlassAccent)
                                            )
                                        )
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("جاري التنزيل...", color = fg.copy(alpha = 0.7f), fontSize = 13.sp)
                                Text(
                                    "${(progress * 100).toInt()}%",
                                    color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                message?.let {
                    Text(
                        it,
                        color = if (dark) Color(0xFFFF8A8A) else Color(0xFFC62828),
                        fontSize = 14.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // الأزرار
                GlassButton(
                    onClick = {
                        val f = apk
                        if (f != null && f.exists()) startInstall(f) else startDownload()
                    },
                    backdrop = backdrop,
                    enabled = !downloading,
                    tint = GlassAccent,
                    pressScale = 1.03f,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(AppIcons.Download, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            downloading -> "جاري التنزيل..."
                            apk != null -> "تثبيت"
                            message != null -> "أعد المحاولة"
                            else -> "تحديث الآن"
                        },
                        color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold
                    )
                }

                if (!i.force) {
                    GlassButton(
                        onClick = { job?.cancel(); dismissed = true },
                        backdrop = backdrop,
                        pressScale = 1.03f,
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text("لاحقاً", color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

/** كبسولة صغيرة للإصدار / الإلزامي */
@Composable
private fun VersionChip(text: String, bg: Color, fg: Color, bold: Boolean) {
    Text(
        text,
        color = fg,
        fontSize = 13.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}
