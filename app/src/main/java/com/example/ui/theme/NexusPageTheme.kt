package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalNexusAccent = staticCompositionLocalOf { NexusCyan }

/** Route ownership is shared by navigation selection and accent transitions. */
fun nexusMainTabForRoute(route: String): String? = when (route) {
    "home" -> "home"
    "profiles", "library", "profile_detail" -> "profiles"
    "mapper", "macro" -> "mapper"
    "devices", "calibration" -> "devices"
    "system", "root_webui", "fps", "crosshair", "profile_files", "safety" -> "system"
    else -> null
}

fun nexusAccentForRoute(route: String): Color = when (nexusMainTabForRoute(route)) {
    "profiles" -> NexusVioletLight
    "mapper" -> NexusMagenta
    "devices" -> NexusGreen
    "system" -> NexusOrange
    else -> NexusCyan
}

/** A brief, finite transition. The foundation and glass surfaces never change hue. */
@Composable
fun NexusPageTheme(route: String, content: @Composable () -> Unit) {
    val accent by animateColorAsState(
        targetValue = nexusAccentForRoute(route),
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "pageAccent"
    )
    val scheme = MaterialTheme.colorScheme.copy(
        primary = accent,
        onPrimary = GraphiteFoundation,
        primaryContainer = accent.copy(alpha = .16f),
        onPrimaryContainer = accent,
        secondary = accent,
        secondaryContainer = accent.copy(alpha = .12f),
        onSecondaryContainer = accent,
        tertiary = accent,
        outline = accent.copy(alpha = .36f)
    )
    CompositionLocalProvider(LocalNexusAccent provides accent) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
