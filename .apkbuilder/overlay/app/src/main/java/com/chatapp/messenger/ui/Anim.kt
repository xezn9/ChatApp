package com.chatapp.messenger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * ظهور متتابع لعناصر الصفحة: كل عنصر يتأخر شوية عن اللي قبله ويطلع من نقطة فتح الصفحة
 * (صغير وضبابي ثم يتحدّد بمكانه) — حركة Emerge من HyperSpring (PageAnimations.kt).
 * [index] ترتيب العنصر، [step] الفرق بين عنصر وعنصر بالملي ثانية.
 * نقطة الانبعاث تجي من LocalPushOrigin (تحددها PushMorphLayer / OriginOverlay).
 */
@Composable
fun StaggerIn(
    index: Int,
    modifier: Modifier = Modifier,
    step: Long = 45L,
    startDelay: Long = 60L,
    content: @Composable () -> Unit
) {
    // بالصفحات الطويلة التتابع ما يطوّل أكثر من 700ms (نفس Cascade)
    val delay = startDelay + minOf(index * step, 700L)
    Emerge(delayMs = delay.toInt(), modifier = modifier, content = content)
}
