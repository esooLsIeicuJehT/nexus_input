package com.example.model

enum class DeviceClass(val displayName: String) {
    PHONE("Standard Phone (16:9 - 20:9)"),
    LARGE_PHONE("Large Phone (6.7\"+)"),
    TABLET("Tablet (16:10 / 4:3)"),
    FOLDABLE("Foldable (Inner Display)"),
    EXTERNAL_DISPLAY("External Display / TV (16:9)")
}

data class DeviceScreenMetrics(
    val deviceClass: DeviceClass = DeviceClass.PHONE,
    val resolutionWidth: Int = 1080,
    val resolutionHeight: Int = 2400,
    val aspectRatio: String = "20:9",
    val dpi: Int = 420,
    val hasCutout: Boolean = true
)

data class DeviceClassOverride(
    val deviceClass: DeviceClass,
    val buttonOverrides: Map<String, MappingNode> = emptyMap(),
    val joystickOverride: JoystickSettings? = null,
    val cameraOverride: CameraSettings? = null
)

data class ProfileInheritance(
    val parentProfileId: String? = null,
    val parentProfileName: String? = null,
    val overriddenNodeIds: Set<String> = emptySet(),
    val inheritJoystick: Boolean = true,
    val inheritCamera: Boolean = true,
    val inheritPerformanceProfile: Boolean = true
)

enum class ContextualTriggerType {
    PIXEL_COLOR,
    REGION_MATCH,
    ICON_VISIBILITY,
    USER_TOGGLE,
    ACCESSIBILITY_STATE
}

enum class ContextualActionType {
    TAP,
    DOUBLE_TAP,
    HOLD,
    HOLD_DURATION,
    PRESS_RELEASE,
    TOGGLE,
    CHORD
}

data class ContextualBinding(
    val id: String,
    val baseNodeId: String,
    val triggerType: ContextualTriggerType = ContextualTriggerType.USER_TOGGLE,
    val actionType: ContextualActionType = ContextualActionType.TAP,
    val holdDurationMs: Long = 300L,
    val chordKey: String? = null, // Secondary key for chord
    val secondaryBoundAction: String = "SECONDARY_ACTION",
    val conditionLabel: String = "Default condition",
    val isEnabled: Boolean = true
)
