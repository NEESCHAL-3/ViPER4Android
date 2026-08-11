package com.nees.audio.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val LightPalette =
    lightColorScheme(
        primary = Color(0xFF3769F5),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE7EDFF),
        onPrimaryContainer = Color(0xFF142B63),
        secondary = Color(0xFF53637A),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE6EAF1),
        onSecondaryContainer = Color(0xFF202A38),
        tertiary = Color(0xFF745BE7),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFEDE8FF),
        onTertiaryContainer = Color(0xFF2A215C),
        background = Color(0xFFF6F7FA),
        onBackground = Color(0xFF111216),
        surface = Color(0xFFFBFCFE),
        onSurface = Color(0xFF111216),
        surfaceVariant = Color(0xFFECEEF3),
        onSurfaceVariant = Color(0xFF626772),
        outline = Color(0xFF9197A2),
        outlineVariant = Color(0xFFD9DDE5),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
    )

private val DarkPalette =
    darkColorScheme(
        primary = Color(0xFF91B6FF),
        onPrimary = Color(0xFF062D61),
        primaryContainer = Color(0xFF183F76),
        onPrimaryContainer = Color(0xFFD9E5FF),
        secondary = Color(0xFFBEC7D7),
        onSecondary = Color(0xFF263140),
        secondaryContainer = Color(0xFF3A4655),
        onSecondaryContainer = Color(0xFFDAE3F3),
        tertiary = Color(0xFFC7B7FF),
        onTertiary = Color(0xFF34275F),
        tertiaryContainer = Color(0xFF4A3C79),
        onTertiaryContainer = Color(0xFFE8DEFF),
        background = Color(0xFF080A0E),
        onBackground = Color(0xFFF2F3F6),
        surface = Color(0xFF101319),
        onSurface = Color(0xFFF2F3F6),
        surfaceVariant = Color(0xFF242832),
        onSurfaceVariant = Color(0xFFB9BDC7),
        outline = Color(0xFF8D929B),
        outlineVariant = Color(0xFF383C45),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
    )

private val ColorWaveShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(17.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )

private val ColorWaveTypography =
    Typography(
        headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
        headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 27.sp, lineHeight = 33.sp, letterSpacing = (-0.25).sp),
        headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 29.sp),
        titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
        titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
        titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
        labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp),
        labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 17.sp),
        labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp),
    )

@Composable
fun ViperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkPalette else LightPalette
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        shapes = ColorWaveShapes,
        typography = ColorWaveTypography,
        content = content,
    )
}
