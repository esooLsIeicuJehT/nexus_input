package com.example.model

enum class NodeType {
    BUTTON,
    JOYSTICK_ZONE,
    CAMERA_DRAG,
    TURBO,
    MACRO
}

data class MappingNode(
    val id: String,
    var xNorm: Float,       // 0.0f to 1.0f (relative to screen width)
    var yNorm: Float,       // 0.0f to 1.0f (relative to screen height)
    var radiusNorm: Float = 0.05f,
    val type: NodeType = NodeType.BUTTON,
    val boundKey: String = "A",     // e.g. "A", "B", "X", "Y", "LB", "RB", "LT", "RT", "L3", "R3", "LS", "RS", "M_LEFT", "M_RIGHT", "SPACE"
    val label: String = "",
    val turboHz: Int = 10,          // 2 to 30 Hz for Turbo nodes
    val deadzoneInner: Float = 0.15f,
    val deadzoneOuter: Float = 0.95f,
    val sensitivity: Float = 1.0f,
    val macroActions: List<MacroStep> = emptyList()
)

data class MacroStep(
    val delayMs: Long = 50,
    val actionType: String = "TAP", // "TAP", "HOLD", "RELEASE"
    val xNorm: Float = 0.5f,
    val yNorm: Float = 0.5f,
    val durationMs: Long = 80
)
