package com.example.model

enum class MacroEventType {
    BUTTON_DOWN,
    BUTTON_UP,
    JOYSTICK_MOVE,
    SWIPE,
    TAP,
    DELAY
}

data class MacroTimelineEvent(
    val id: String,
    val timestampMs: Long,
    val type: MacroEventType,
    val targetKey: String = "A",
    val xNorm: Float = 0.5f,
    val yNorm: Float = 0.5f,
    val endXNorm: Float = 0.5f,
    val endYNorm: Float = 0.5f,
    val durationMs: Long = 50L,
    val colorHex: Long = 0xFF00E5FF
)

data class MacroSequence(
    val id: String,
    val name: String,
    val totalDurationMs: Long = 1000L,
    val isLooping: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val events: List<MacroTimelineEvent> = emptyList(),
    val antiCheatWarningDismissed: Boolean = false
)
