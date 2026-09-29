package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = BrandRed,
    onPrimary = Surface,
    primaryContainer = BrandRedDark,
    onPrimaryContainer = Surface,
    secondary = SelectBlue,
    onSecondary = Surface,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = AppBackground,
    onSurfaceVariant = TextSecondary,
    outline = Outline,
    outlineVariant = Divider,
    error = BrandRed,
)

private val DarkColors = darkColorScheme(
    primary = BrandRed,
    onPrimary = Surface,
    primaryContainer = BrandRedDark,
    onPrimaryContainer = Surface,
    secondary = SelectBlue,
    onSecondary = Surface,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkDivider,
    outlineVariant = DarkDivider,
    error = BrandRed,
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
