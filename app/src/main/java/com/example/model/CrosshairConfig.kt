package com.example.model

enum class CrosshairShape(val displayName: String) {
    DOT("Center Dot"),
    CLASSIC_CROSS("Tactical Cross (+)"),
    CIRCLE_DOT("Circle with Dot (⊙)"),
    T_SHAPE("T-Reticle (T)"),
    CHEVRON("Sniper Chevron (^)"),
    TRI_LINE("Tri-Line Military")
}

data class CrosshairConfig(
    val isEnabled: Boolean = false,
    val shape: CrosshairShape = CrosshairShape.CLASSIC_CROSS,
    val sizeDp: Float = 24f,
    val thicknessDp: Float = 2.5f,
    val gapDp: Float = 4f,
    val colorHex: String = "#00F0FF",       // Vibrant Cyan default
    val opacity: Float = 0.9f,
    val outlineEnabled: Boolean = true,
    val outlineColorHex: String = "#000000",
    val outlineThicknessDp: Float = 1.0f,
    val offsetX: Float = 0f,                // Center offset in dp for games with off-center aim
    val offsetY: Float = 0f,
    val dynamicSpread: Boolean = true,      // Expands on analog stick deflection
    val currentSpreadMultiplier: Float = 1.0f
)
