package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Foundation & Suggested Tones from Design Spec (#090B10 Foundation)
val GraphiteFoundation = Color(0xFF090B10)
val DarkBackground = GraphiteFoundation
val DarkSurface = Color(0xFF131722)
val DarkSurfaceElevated = Color(0xFF1A1F2E)
val DarkSurfaceBorder = Color(0xFF272F44)

// Brand & Interaction Palette
val ControlystViolet = Color(0xFF5043EB) // Controlyst, intelligence, AI, selected interaction
val ControlystVioletLight = Color(0xFF8A98FF)
val ControlystBlue = Color(0xFF7C8CFF)   // Mapping/input
val ControlystCyan = Color(0xFF00CFEB)   // Measurable hardware performance and telemetry
val ControlystGreen = Color(0xFF00E676)  // Healthy/connected/success
val ControlystOrange = Color(0xFFFF9100) // Attention warranted (only when needed)
val ControlystRed = Color(0xFFFF1744)    // Genuine danger, failure, or conflict only

// Aliases for compatibility
val CyberCyan = ControlystCyan
val NeonCyanLight = Color(0xFF38BDF8)
val ElectricViolet = ControlystViolet
val DeepIndigo = Color(0xFF6366F1)
val AccentGreen = ControlystGreen
val AccentAmber = ControlystOrange
val AccentRose = ControlystRed

val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

val DarkTextPrimary = TextPrimary
val DarkTextSecondary = TextSecondary

// The Signature Gradient (#5043EB -> #7C8CFF -> #00CFEB)
val SignatureGradient = Brush.horizontalGradient(
    listOf(
        ControlystViolet,
        ControlystBlue,
        ControlystCyan
    )
)

val SignatureGradientVertical = Brush.verticalGradient(
    listOf(
        ControlystViolet,
        ControlystBlue,
        ControlystCyan
    )
)
