package com.chatapp.messenger.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.chatapp.messenger.ui.glass.GlassButton
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** عارض فيديو بملء الشاشة: ضغطة = إيقاف/تشغيل، شريط تقدم، وزر حفظ بالمعرض */
@Composable
fun VideoViewerScreen(file: File, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val bd = rememberLayerBackdrop()
    val viewRef = remember { arrayOfNulls<VideoView>(1) }
    var playing by remember { mutableStateOf(true) }
    var pos by remember { mutableIntStateOf(0) }
    var dur by remember { mutableIntStateOf(1) }
    var saving by remember { mutableStateOf(false) }

    fun doSave() {
        if (saving) return
        saving = true
        scope.launch {
            val ok = MediaUtil.saveVideoToGallery(ctx, file, "ChatApp_" + System.currentTimeMillis())
            saving = false
            AppToast.show(if (ok) "انحفظ الفيديو بالمعرض" else "تعذّر حفظ الفيديو")
        }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) doSave() else AppToast.show("لازم تسمح بصلاحية التخزين حتى ينحفظ الفيديو", long = true)
    }
    fun save() {
        if (Build.VERSION.SDK_INT < 29 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) permLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else doSave()
    }

    fun toggle() {
        val v = viewRef[0] ?: return
        if (v.isPlaying) { v.pause(); playing = false }
        else {
            if (dur > 1 && v.currentPosition >= dur - 100) v.seekTo(0)
            v.start(); playing = true
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            viewRef[0]?.let { v -> runCatching { pos = v.currentPosition } }
            delay(150)
        }
    }
    DisposableEffect(Unit) { onDispose { runCatching { viewRef[0]?.stopPlayback() } } }
    BackHandler(onBack = onDismiss)

    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures(onTap = { toggle() }) }) {
        Box(Modifier.fillMaxSize().layerBackdrop(bd)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    val vv = VideoView(c)
                    vv.setVideoPath(file.path)
                    vv.setOnPreparedListener { mp -> dur = mp.duration.coerceAtLeast(1); vv.start() }
                    vv.setOnCompletionListener { playing = false }
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
        // شريط التقدم
        Box(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp).fillMaxWidth().height(4.dp)
                .clip(CircleShape).background(Color.White.copy(0.28f))
        ) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth((pos.toFloat() / dur).coerceIn(0f, 1f))
                    .clip(CircleShape).background(Color.White)
            )
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
                enabled = !saving, modifier = Modifier.height(48.dp)
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
