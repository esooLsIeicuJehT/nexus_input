package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NexusColorScheme = darkColorScheme(
    primary = NexusCyan,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF0B2234),
    onPrimaryContainer = NexusCyan,
    secondary = NexusViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF241D4D),
    onSecondaryContainer = NexusVioletLight,
    tertiary = NexusBlue,
    background = GraphiteFoundation,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = DarkSurfaceBorder,
    error = NexusRed,
    onError = Color.White
)

@Composable
fun NexusInputTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NexusColorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * Compatibility wrapper while legacy screens and tests still reference the old
 * theme function name. New code should use NexusInputTheme.
 */
@Composable
fun ControlystTheme(content: @Composable () -> Unit) = NexusInputTheme(content)
