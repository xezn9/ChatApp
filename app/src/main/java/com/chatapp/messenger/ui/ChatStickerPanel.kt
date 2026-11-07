package com.chatapp.messenger.ui

import androidx.activity.compose.BackHandler
import com.chatapp.messenger.ui.theme.GlassFx
import com.chatapp.messenger.ui.theme.glassSurfaceLayer
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableStateOf
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState

// ============================================================================
//  لوحة ملصقات الدردشة (على طريقة تيك توك):
//  - تنفتح تحت مربع الكتابة وتحل مكان الكيبورد، وزر الملصقات يتحول لزر كيبورد
//  - الضغط على ملصق يرسله واللوحة تبقى مفتوحة، وتسكّر بزر الكيبورد أو زر الرجوع
//  - فيها قسم «ملصقاتي» (ملصقات من صورك) وزر إنشاء ملصق
//  - الخلفية Liquid Glass (نفس زجاج قائمة الرسالة)
//  - الملصقات تنزل تلقائياً من Stickers.zip وتنحفظ بمجلد منفصل عن رموز الاسم
// ============================================================================
@Composable
internal fun ChatStickerPanel(
    open: Boolean,
    dark: Boolean,
    backdrop: Backdrop,
    /** ارتفاع اللوحة (نفس ارتفاع الكيبورد حتى تحل مكانه) */
    height: androidx.compose.ui.unit.Dp,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onPickCustom: (File) -> Unit,
    onCreate: () -> Unit
) {
    val ctx = LocalContext.current
    val fg = if (dark) Color.White else Color(0xFF101418)
    @Suppress("UNUSED_VARIABLE") val rev = StickerDownloader.version   // يعيد الرسم بعد التنزيل
    val myRev = CustomStickers.version.intValue
    StickerRegistry.ensure(ctx)
    val pack = StickerRegistry.chat
    BackHandler(enabled = open) { onDismiss() }
    LaunchedEffect(open) {
        if (open) {
            StickerStore.prewarm(ctx)
            if (StickerRegistry.chat == null && !StickerDownloader.busy) StickerDownloader.retry(ctx)
        }
    }
    // اللوحة تنفتح تحت مربع الكتابة وتحل مكان الكيبورد (بدون ما تغطي زر الكيبورد)
    AnimatedVisibility(
        visible = open,
        enter = expandVertically(tween(220)) + fadeIn(tween(160)),
        exit = shrinkVertically(tween(180)) + fadeOut(tween(120))
    ) {
        val sheetShape = RoundedCornerShape(28.dp)
        Column(
            Modifier.padding(top = 10.dp).fillMaxWidth().height(height)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { sheetShape },
                    effects = {
                        vibrancy()
                        blur((18f.dp.toPx()) * GlassFx.blurK)
                        lens((12f.dp.toPx()) * GlassFx.refractK, (24f.dp.toPx()) * GlassFx.refractK)
                    },
                    onDrawSurface = { glassSurfaceLayer { drawRect(if (dark) Color.Black.copy(0.38f) else Color.White.copy(0.50f)) } }
                )
                .border(
                    0.8.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(0.45f), Color.White.copy(0.06f))),
                    sheetShape
                )
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("الملصقات", color = fg, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (pack != null) Text(
                    "  ${StickerStore.files(ctx, pack).size}", color = fg.copy(0.5f), fontSize = 13.sp
                )
                Spacer(Modifier.weight(1f))
                // زر إنشاء ملصق من صورة
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF4DA3FF))
                        .clickable { onCreate() }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) { Text("＋ إنشاء ملصق", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            }
            val mine = remember(myRev) { CustomStickers.list(ctx) }
            var deleteTarget by remember { mutableStateOf<File?>(null) }
            val haptic = LocalHapticFeedback.current
            val showPack = pack != null
            if (!showPack && mine.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (StickerDownloader.busy) {
                            CircularProgressIndicator(color = fg, strokeWidth = 2.5.dp, modifier = Modifier.size(34.dp))
                            Spacer(Modifier.height(14.dp))
                            Text("جاري تنزيل الملصقات...", color = fg.copy(0.75f), fontSize = 14.sp)
                        } else {
                            Text(
                                if (StickerDownloader.failed) "تعذّر تنزيل الملصقات، تأكد من الإنترنت" else "ما موجود ملصقات",
                                color = fg.copy(0.75f), fontSize = 14.sp, textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(14.dp))
                            Box(
                                Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF4DA3FF))
                                    .clickable { StickerDownloader.retry(ctx) }
                                    .padding(horizontal = 22.dp, vertical = 10.dp)
                            ) { Text("إعادة المحاولة", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            } else {
                val list = remember(pack, rev) { if (pack != null) StickerStore.files(ctx, pack) else emptyList() }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (mine.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "hdr_mine") {
                            Text("ملصقاتي", color = fg.copy(0.6f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                        items(mine, key = { "my_" + it.name }) { f ->
                            MyStickerCell(
                                file = f, deleting = deleteTarget == f,
                                onClick = {
                                    if (deleteTarget != null) deleteTarget = null
                                    else { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onPickCustom(f) }
                                },
                                onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); deleteTarget = f },
                                onDelete = { deleteTarget = null; CustomStickers.delete(f) }
                            )
                        }
                        if (showPack) item(span = { GridItemSpan(maxLineSpan) }, key = "hdr_all") {
                            Text("كل الملصقات", color = fg.copy(0.6f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    if (pack != null) items(list, key = { it }) { file ->
                        val id = StickerStore.idOf(pack, file)
                        ChatStickerCell(id) {
                            deleteTarget = null
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPick(id)
                        }
                    }
                }
            }
        }
    }
}

/** ملصق من ملصقاتي: ضغطة = إرسال، ضغطة مطولة = تظهر علامة حذف */
@Composable
private fun MyStickerCell(file: File, deleting: Boolean, onClick: () -> Unit, onLongPress: () -> Unit, onDelete: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))
            .pointerInput(file) { detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress() }) },
        contentAlignment = Alignment.Center
    ) {
        CustomStickerFileImage(file, 72.dp)
        if (deleting) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.35f)))
            Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp).clip(CircleShape)
                    .background(Color(0xFFFF453A)).clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) { Text("✕", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ChatStickerCell(id: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.86f else 1f, spring(0.55f, 600f), label = "stkPress")
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f)
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { StickerBadgeView(id, 72.dp, fps = 12) }
}
