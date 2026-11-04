package com.chatapp.messenger.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope

/** معاملات الزجاج العامة (تتحدث لحظياً من صفحة الثيم). 1.0 = الشكل الأصلي */
object GlassFx {
    val refractK: Float get() = ThemeSettings.refractK
    val blurK: Float get() = ThemeSettings.blurK
    val surfaceK: Float get() = ThemeSettings.surfaceK
}

/**
 * يرسم طبقة سطح الزجاج (التلوين/الضباب) بحسب "الشفافية" المختارة:
 * أقل من 1 = أشف، أكثر من 1 = أكثف (تنرسم مرتين).
 */
inline fun DrawScope.glassSurfaceLayer(block: DrawScope.() -> Unit) {
    val k = GlassFx.surfaceK
    if (k > 0.98f && k < 1.02f) { block(); return }
    if (k <= 0.01f) return
    val bounds = Rect(Offset.Zero, size)
    val canvas = drawContext.canvas
    if (k < 1f) {
        canvas.saveLayer(bounds, Paint().apply { alpha = k })
        block()
        canvas.restore()
    } else {
        block()
        canvas.saveLayer(bounds, Paint().apply { alpha = (k - 1f).coerceIn(0f, 1f) })
        block()
        canvas.restore()
    }
}
