package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Nexus Input dark foundation.
val GraphiteFoundation = Color(0xFF03070D)
val DarkBackground = GraphiteFoundation
val DarkSurface = Color(0xFF071522)
val DarkSurfaceElevated = Color(0xFF0B1B2B)
val DarkSurfaceBorder = Color(0xFF1C4565)

// Nexus Input interaction palette.
val NexusViolet = Color(0xFF6D3CFF)
val NexusVioletLight = Color(0xFFB06CFF)
val NexusBlue = Color(0xFF2869FF)
val NexusCyan = Color(0xFF16E6FF)
val NexusGreen = Color(0xFF19F2A0)
val NexusOrange = Color(0xFFFFA726)
val NexusRed = Color(0xFFFF405D)

// Compatibility aliases. Existing screens can migrate incrementally without
// breaking the runtime while the product name changes from Controlyst to Nexus Input.
val ControlystViolet = NexusViolet
val ControlystVioletLight = NexusVioletLight
val ControlystBlue = NexusBlue
val ControlystCyan = NexusCyan
val ControlystGreen = NexusGreen
val ControlystOrange = NexusOrange
val ControlystRed = NexusRed

val CyberCyan = NexusCyan
val NeonCyanLight = Color(0xFF38BDF8)
val ElectricViolet = NexusViolet
val DeepIndigo = NexusBlue
val AccentGreen = NexusGreen
val AccentAmber = NexusOrange
val AccentRose = NexusRed

val TextPrimary = Color(0xFFF5FAFF)
val TextSecondary = Color(0xFF9CB4C8)
val TextMuted = Color(0xFF667F95)

val DarkTextPrimary = TextPrimary
val DarkTextSecondary = TextSecondary

val SignatureGradient = Brush.horizontalGradient(
    listOf(NexusViolet, NexusBlue, NexusCyan)
)

val SignatureGradientVertical = Brush.verticalGradient(
    listOf(NexusViolet, NexusBlue, NexusCyan)
)
