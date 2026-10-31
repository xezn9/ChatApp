package com.chatapp.messenger.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.chatapp.messenger.ui.glass.GlassButton
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

/**
 * عارض صورة بملء الشاشة (للبانر وصورة الملف الشخصي): تكبير بإصبعين، ضغطتين = تقريب،
 * وزر يحفظ الصورة بمعرض الجهاز. [b64] = الصورة (null = مخفي).
 */
@Composable
fun ImageViewerHost(b64: String?, onDismiss: () -> Unit) {
    // نتذكر آخر صورة حتى تشتغل حركة الإغلاق قبل ما تختفي
    var last by remember { mutableStateOf(b64 ?: "") }
    LaunchedEffect(b64) { if (b64 != null) last = b64 }
    AnimatedVisibility(
        visible = b64 != null,
        enter = fadeIn(tween(200)) + scaleIn(spring(0.85f, 400f), initialScale = 0.9f),
        exit = fadeOut(tween(170)) + scaleOut(tween(190), targetScale = 0.94f)
    ) {
        ImageViewerScreen(last, onDismiss)
    }
}

@Composable
private fun ImageViewerScreen(b64: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val img = rememberB64Image(b64)
    val bd = rememberLayerBackdrop()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var saving by remember { mutableStateOf(false) }

    fun doSave() {
        if (saving) return
        saving = true
        scope.launch {
            val ok = try {
                val bytes = Base64.decode(b64, Base64.NO_WRAP)
                ImageUtil.saveToGallery(ctx, bytes, "ChatApp_" + System.currentTimeMillis())
            } catch (e: Exception) {
                false
            }
            saving = false
            AppToast.show(if (ok) "انحفظت الصورة بالمعرض" else "تعذّر حفظ الصورة")
        }
    }

    // أندرويد 9 وأقل يحتاج صلاحية كتابة للحفظ بالمعرض
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) doSave() else AppToast.show("لازم تسمح بصلاحية التخزين حتى تنحفظ الصورة", long = true)
    }
    fun save() {
        if (Build.VERSION.SDK_INT < 29 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) permLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else doSave()
    }

    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { } }) {
        Box(Modifier.fillMaxSize().layerBackdrop(bd), contentAlignment = Alignment.Center) {
            if (img != null) {
                Image(
                    img, null,
                    Modifier.fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = {
                                if (scale > 1.05f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                            })
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                offset = if (scale <= 1.01f) Offset.Zero else offset + pan * scale
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale; scaleY = scale
                            translationX = offset.x; translationY = offset.y
                        },
                    contentScale = ContentScale.Fit
                )
            }
        }
        Row(
            Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp).align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassButton(onClick = onDismiss, backdrop = bd, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
        Row(
            Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp).align(Alignment.TopEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassButton(
                onClick = { save() }, backdrop = bd, tint = Color(0xFF1AA7FF),
                enabled = !saving && img != null,
                modifier = Modifier.height(48.dp)
            ) {
                Icon(AppIcons.Download, null, tint = Color.White, modifier = Modifier.padding(start = 18.dp).size(22.dp))
                Text(
                    if (saving) "..." else "حفظ", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp, end = 20.dp)
                )
            }
        }
    }
}
