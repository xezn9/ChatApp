package com.chatapp.messenger.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import kotlinx.coroutines.delay

/**
 * تخفيف اللاق: الشاشة الي تنغطّى بشاشة كاملة ومعتمة فوقها ما لها داعي ترسم نفسها (blur/lens/خلفيات).
 * بعد ما تخلص حركة الدخول ([settleMs]) نوقف رسم الشاشة التحتانية بالكامل (التركيب والحالة يبقون: scroll/نص/تبويب).
 * بمجرد ما [covered] ترجع false ترجع ترسم فوراً بنفس الإطار (بدون وميض) حتى حركة الرجوع تبين كاملة.
 */
@Composable
fun Modifier.hideWhenCovered(covered: Boolean, settleMs: Long = 520L): Modifier {
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(covered) {
        if (covered) { delay(settleMs); settled = true } else settled = false
    }
    val skip = covered && settled
    return if (skip) this.drawWithContent { /* لا نرسم شي */ } else this
}

/**
 * ساعة بطيئة للخلفيات الي تسبح ببطء (دورة 36-40 ثانية): تتحدث 10 مرات بالثانية بدل كل فريم.
 * كل تحديث للخلفية يعني إعادة حساب كل الزجاج (blur/lens) الي فوقها، والحركة بطيئة لدرجة ما تنفرق عن 60 فريم.
 * تتوقف لما [active] = false (الصفحة مغطاة) أو التطبيق بالخلفية، وتكمل من نفس المكان.
 */
@Composable
fun rememberSlowPhase(periodMs: Int, stepMs: Long = 100L, active: Boolean = true): androidx.compose.runtime.State<Float> {
    val st = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    LaunchedEffect(periodMs, active) {
        if (!active) return@LaunchedEffect
        val twoPi = (2.0 * Math.PI)
        val offsetMs = (st.floatValue / twoPi * periodMs).toLong()
        val start = System.nanoTime() / 1_000_000L - offsetMs
        while (true) {
            if (com.chatapp.messenger.AppState.foreground) {
                val el = (System.nanoTime() / 1_000_000L - start) % periodMs
                st.floatValue = (el.toDouble() / periodMs * twoPi).toFloat()
            }
            delay(stepMs)
        }
    }
    return st
}
