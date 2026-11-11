package com.chatapp.messenger.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// الألوان الأساسية تتبع لون التطبيق اللي يختاره المستخدم من إعدادات "ثيم التطبيق"
@Composable
fun ChatTheme(content: @Composable () -> Unit) {
    val dark = isAppDark()
    val accent = ThemeSettings.accent
    val onAccent = if (accent.luminance() > 0.55f) Color(0xFF101418) else Color.White
    val scheme = if (dark) darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        surfaceVariant = Color(0xFF303134)
    ) else lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        surfaceVariant = Color(0xFFE8EAED)
    )

    // أيقونات شريط الحالة/التنقل تتبع الثيم المختار (مو ثيم النظام)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = scheme, typography = appTypography(), content = content)
}

/** خلفية التطبيق (الزجاج يستخدمها ليعمل التأثير) */
@Composable
fun appBackground(): Brush =
    Brush.verticalGradient(
        if (isAppDark()) listOf(Color(0xFF0A2420), Color(0xFF0A1B3A))
        else listOf(Color(0xFFD9F7EE), Color(0xFFCFE4FF))
    )

/** كل أنماط النص في Material تستخدم خط التطبيق */
private fun appTypography(): Typography {
    val d = Typography()
    fun TextStyle.f() = copy(fontFamily = AppFont)
    return d.copy(
        displayLarge = d.displayLarge.f(), displayMedium = d.displayMedium.f(), displaySmall = d.displaySmall.f(),
        headlineLarge = d.headlineLarge.f(), headlineMedium = d.headlineMedium.f(), headlineSmall = d.headlineSmall.f(),
        titleLarge = d.titleLarge.f(), titleMedium = d.titleMedium.f(), titleSmall = d.titleSmall.f(),
        bodyLarge = d.bodyLarge.f(), bodyMedium = d.bodyMedium.f(), bodySmall = d.bodySmall.f(),
        labelLarge = d.labelLarge.f(), labelMedium = d.labelMedium.f(), labelSmall = d.labelSmall.f()
    )
}
