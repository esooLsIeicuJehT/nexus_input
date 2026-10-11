package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Nexus Input dark foundation.
val GraphiteFoundation = Color(0xFF03070D)
val DarkBackground = GraphiteFoundation
val DarkSurface = Color(0xB006111D)
val DarkSurfaceElevated = Color(0xC00A1A2A)
val DarkSurfaceBorder = Color(0xFF155073)

// Nexus Input interaction palette.
val NexusViolet = Color(0xFF8B3DFF)
val NexusVioletLight = Color(0xFFC05CFF)
val NexusBlue = Color(0xFF246BFF)
val NexusCyan = Color(0xFF16F1FF)
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
val TextMuted = Color(0xFF91A9BD)

val DarkTextPrimary = TextPrimary
val DarkTextSecondary = TextSecondary

val SignatureGradient = Brush.horizontalGradient(
    listOf(NexusViolet, NexusBlue, NexusCyan)
)

val SignatureGradientVertical = Brush.verticalGradient(
    listOf(NexusViolet, NexusBlue, NexusCyan)
)

val NexusPanelGradient = Brush.linearGradient(listOf(Color(0xAA183044), Color(0xB0081423), Color(0xA51D1840)))
val NexusGlowGradient = Brush.linearGradient(listOf(NexusCyan, NexusBlue, NexusVioletLight))
