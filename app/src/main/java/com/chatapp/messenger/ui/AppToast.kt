package com.chatapp.messenger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay

/**
 * رسائل قصيرة داخل التطبيق (بدل Toast أندرويد): كبسولة زجاج سائل بنفس ستايل التطبيق.
 * الاستعمال: AppToast.show("نص") من أي مكان. المضيف AppToastHost موجود بـ App().
 */
object AppToast {
    internal class Item(val id: Long, val text: String, val long: Boolean)

    internal var current by mutableStateOf<Item?>(null)
    private var counter = 0L

    fun show(text: String, long: Boolean = false) {
        current = Item(++counter, text, long)
    }
}

/** يرسم الرسالة فوق كل شي. [backdrop] = خلفية التطبيق حتى يشتغل تأثير الزجاج */
@Composable
internal fun AppToastHost(backdrop: Backdrop, modifier: Modifier = Modifier) {
    val item = AppToast.current
    // نتذكر آخر نص حتى ما يختفي النص قبل ما تخلص حركة الإغلاق
    var last by remember { mutableStateOf<AppToast.Item?>(null) }
    if (item != null) last = item
    LaunchedEffect(item?.id) {
        if (item != null) {
            delay(if (item.long) 4000L else 2200L)
            if (AppToast.current?.id == item.id) AppToast.current = null
        }
    }
    Box(modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 118.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = item != null,
            enter = slideInVertically(spring(0.75f, 420f)) { it / 2 } + fadeIn(tween(160)) +
                scaleIn(spring(0.7f, 450f), initialScale = 0.8f),
            exit = slideOutVertically(tween(180)) { it / 3 } + fadeOut(tween(160)) + scaleOut(tween(180), targetScale = 0.9f)
        ) {
            val shape = RoundedCornerShape(26.dp)
            Box(
                Modifier.padding(horizontal = 24.dp).widthIn(max = 340.dp)
                    .liquidCard(backdrop, shape, dark = true)
                    .background(Color.Black.copy(0.30f), shape)
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    last?.text ?: "", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
