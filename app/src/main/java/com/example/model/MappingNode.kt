package com.example.model

enum class NodeType {
    BUTTON,
    JOYSTICK_ZONE,
    CAMERA_DRAG,
    TURBO,
    MACRO
}

enum class ButtonBehavior {
    TAP,
    HOLD
}

data class MappingNode(
    val id: String,
    var xNorm: Float,
    var yNorm: Float,
    var radiusNorm: Float = 0.05f,
    val type: NodeType = NodeType.BUTTON,
    val boundKey: String = "A",
    val label: String = "",
    val turboHz: Int = 10,
    val deadzoneInner: Float = 0.15f,
    val deadzoneOuter: Float = 0.95f,
    val sensitivity: Float = 1.0f,
    val macroActions: List<MacroStep> = emptyList(),
    val buttonBehavior: ButtonBehavior = if (
        boundKey.uppercase() in setOf("LT", "RT", "L2", "R2")
    ) ButtonBehavior.HOLD else ButtonBehavior.TAP,
    val inputKeyCode: Int? = null,
    val inputScanCode: Int? = null,
    val touchSlot: Int? = null,
    val axisX: Int? = null,
    val axisY: Int? = null,
    val invertY: Boolean = false,
    val triggerPressThreshold: Float = .55f,
    val triggerReleaseThreshold: Float = .35f
)

data class MacroStep(
    val delayMs: Long = 50,
    val actionType: String = "TAP",
    val xNorm: Float = 0.5f,
    val yNorm: Float = 0.5f,
    val durationMs: Long = 80,
    val endXNorm: Float? = null,
    val endYNorm: Float? = null
)
