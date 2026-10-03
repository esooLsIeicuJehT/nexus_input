package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ControlystColorScheme = darkColorScheme(
    primary = ControlystCyan,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF1E2235),
    onPrimaryContainer = ControlystCyan,
    secondary = ControlystViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF282548),
    onSecondaryContainer = ControlystBlue,
    tertiary = ControlystBlue,
    background = GraphiteFoundation,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = DarkSurfaceBorder,
    error = ControlystRed,
    onError = Color.White
)

@Composable
fun ControlystTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ControlystColorScheme,
        typography = Typography,
        content = content
    )
}
