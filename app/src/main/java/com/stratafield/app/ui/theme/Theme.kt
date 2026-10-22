package com.stratafield.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Nothing Phone aesthetic - clean white, black, red accent
val NothingWhite = Color(0xFFFFFFFF)
val NothingBlack = Color(0xFF000000)
val NothingRed = Color(0xFFD0021B)
val NothingLightGray = Color(0xFFF5F5F5)
val NothingMediumGray = Color(0xFFE0E0E0)
val NothingDarkGray = Color(0xFF333333)
val NothingTextGray = Color(0xFF666666)
val NothingBorderGray = Color(0xFFCCCCCC)
val NothingSuccess = Color(0xFF2E7D32)
val NothingWarning = Color(0xFFF57C00)
val NothingInfo = Color(0xFF1565C0)

private val LightColors = lightColorScheme(
    primary = NothingBlack,
    onPrimary = NothingWhite,
    primaryContainer = NothingLightGray,
    onPrimaryContainer = NothingBlack,
    secondary = NothingRed,
    onSecondary = NothingWhite,
    secondaryContainer = Color(0xFFFFE5E8),
    onSecondaryContainer = NothingRed,
    tertiary = NothingDarkGray,
    onTertiary = NothingWhite,
    background = NothingWhite,
    onBackground = NothingBlack,
    surface = NothingWhite,
    onSurface = NothingBlack,
    surfaceVariant = NothingLightGray,
    onSurfaceVariant = NothingTextGray,
    outline = NothingBorderGray,
    outlineVariant = NothingMediumGray,
    error = NothingRed,
    onError = NothingWhite
)

private val DarkColors = darkColorScheme(
    primary = NothingWhite,
    onPrimary = NothingBlack,
    primaryContainer = NothingDarkGray,
    onPrimaryContainer = NothingWhite,
    secondary = NothingRed,
    onSecondary = NothingWhite,
    secondaryContainer = Color(0xFF4A0010),
    onSecondaryContainer = Color(0xFFFFB3C1),
    tertiary = NothingLightGray,
    onTertiary = NothingBlack,
    background = Color(0xFF121212),
    onBackground = NothingWhite,
    surface = Color(0xFF1E1E1E),
    onSurface = NothingWhite,
    surfaceVariant = Color(0xFF2D2D2D),
    onSurfaceVariant = Color(0xFFBDBDBD),
    outline = Color(0xFF555555),
    outlineVariant = Color(0xFF3D3D3D),
    error = Color(0xFFFF6B6B),
    onError = NothingBlack
)

@Composable
fun StrataFieldTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}