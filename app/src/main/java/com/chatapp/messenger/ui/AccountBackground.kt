package com.chatapp.messenger.ui

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.ui.theme.appBackground
import com.kyant.backdrop.Backdrop
import java.io.File
import androidx.compose.foundation.layout.PaddingValues as LazyPadding

/** رقم الخلفية المخصصة (صورة من المعرض) */
internal const val ACCOUNT_BG_CUSTOM = -1

/** خلفية جاهزة: تدرج من فوق لتحت + 3 بقع ضوء تتحرك ببطء. classic = خلفية التطبيق القديمة بدون حركة */
internal class AccountBgPreset(
    val name: String,
    val top: Color,
    val bottom: Color,
    val blobs: List<Color>,
    val classic: Boolean = false
)

internal val AccountBgPresets = listOf(
    AccountBgPreset("أورورا", Color(0xFF071C1E), Color(0xFF0A1740), listOf(Color(0xFF1AA7FF), Color(0xFF7A5CFF), Color(0xFF2EE6A8))),
    AccountBgPreset("منتصف الليل", Color(0xFF04060F), Color(0xFF121838), listOf(Color(0xFF4C5BFF), Color(0xFF8A5CFF), Color(0xFF1AA7FF))),
    AccountBgPreset("غروب", Color(0xFF2A0F2E), Color(0xFF4A1A2A), listOf(Color(0xFFFF7A45), Color(0xFFFF4F81), Color(0xFFFFC145))),
    AccountBgPreset("محيط", Color(0xFF02202A), Color(0xFF05394D), listOf(Color(0xFF00D4AA), Color(0xFF1AA7FF), Color(0xFF4CC9F0))),
    AccountBgPreset("وردي", Color(0xFF2B0A1F), Color(0xFF38123A), listOf(Color(0xFFFF5C8A), Color(0xFFB25CFF), Color(0xFFFFB347))),
    AccountBgPreset("الأصلية", Color.Transparent, Color.Transparent, emptyList(), classic = true)
)

/** الخلفية المختارة تنحفظ على الجهاز (SharedPreferences) والصورة المخصصة كملف داخلي */
internal object AccountBgStore {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("account_bg", Context.MODE_PRIVATE)
    fun choice(ctx: Context): Int = prefs(ctx).getInt("choice", 0)
    fun setChoice(ctx: Context, c: Int) = prefs(ctx).edit().putInt("choice", c).apply()
    fun customFile(ctx: Context) = File(ctx.filesDir, "account_bg.jpg")
}

/** خلفية تبويب Account */
@Composable
internal fun AccountBackground(choice: Int, custom: ImageBitmap?, modifier: Modifier = Modifier) {
    if (choice == ACCOUNT_BG_CUSTOM && custom != null) {
        Box(modifier) {
            Image(custom, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            // تعتيم خفيف حتى تبقى الكتابة البيضاء مقروءة فوق أي صورة
            Box(Modifier.matchParentSize().background(Color.Black.copy(0.32f)))
        }
        return
    }
    val preset = AccountBgPresets.getOrNull(choice) ?: AccountBgPresets[0]
    if (preset.classic) {
        Box(modifier.background(appBackground()))
        return
    }
    val t = rememberInfiniteTransition(label = "accBg")
    val phase by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse),
        label = "accBgPhase"
    )
    Box(
        modifier.background(Brush.verticalGradient(listOf(preset.top, preset.bottom))).drawBehind {
            val w = size.width
            val h = size.height
            fun blob(c: Color, cx: Float, cy: Float, r: Float) = drawCircle(
                Brush.radialGradient(listOf(c.copy(alpha = 0.50f), Color.Transparent), center = Offset(cx, cy), radius = r),
                r, Offset(cx, cy)
            )
            blob(preset.blobs[0], w * (0.10f + 0.40f * phase), h * (0.18f + 0.08f * phase), w * 0.80f)
            blob(preset.blobs[1], w * (0.95f - 0.40f * phase), h * (0.50f + 0.10f * phase), w * 0.75f)
            blob(preset.blobs[2], w * (0.20f + 0.35f * phase), h * (0.95f - 0.12f * phase), w * 0.85f)
        }
    )
}

/** لوحة اختيار خلفية Account (تطلع من تحت): خلفيات جاهزة + صورة من المعرض */
@Composable
internal fun AccountBgSheet(
    choice: Int,
    custom: ImageBitmap?,
    backdrop: Backdrop,
    onPick: (Int) -> Unit,
    onPickCustom: () -> Unit,
    onDismiss: () -> Unit
) {
    val shape = RoundedCornerShape(34.dp)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(0.40f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)
                .liquidCard(backdrop, shape, dark = true)
                .background(Color.Black.copy(0.30f), shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(vertical = 20.dp)
        ) {
            Text(
                "خلفية الحساب", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 22.dp)
            )
            Text(
                "تظهر عندك أنت بس على هذا الجهاز.", color = Color.White.copy(0.65f), fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(14.dp))
            LazyRow(
                contentPadding = LazyPadding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(AccountBgPresets.size) { i ->
                    val p = AccountBgPresets[i]
                    val selected = choice == i
                    SwatchBox(selected, p.name, onClick = { onPick(i) }) {
                        if (p.classic) {
                            Box(Modifier.fillMaxSize().background(appBackground()))
                        } else {
                            Box(
                                Modifier.fillMaxSize().background(
                                    Brush.linearGradient(listOf(p.blobs[0], p.blobs[1], p.blobs[2].copy(0.8f), p.bottom))
                                )
                            )
                        }
                    }
                }
                item {
                    SwatchBox(choice == ACCOUNT_BG_CUSTOM && custom != null, "من المعرض", onClick = onPickCustom) {
                        if (custom != null) {
                            Image(custom, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Box(Modifier.fillMaxSize().background(Color.White.copy(0.10f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(30.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwatchBox(selected: Boolean, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(78.dp)) {
        Box(
            Modifier.size(78.dp, 108.dp)
                .clip(RoundedCornerShape(22.dp))
                .border(
                    BorderStroke(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color.White.copy(0.30f)),
                    RoundedCornerShape(22.dp)
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            content()
            if (selected) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(6.dp).size(24.dp)
                        .clip(RoundedCornerShape(12.dp)).background(Color(0xFF1AA7FF)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White.copy(0.85f), fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}
