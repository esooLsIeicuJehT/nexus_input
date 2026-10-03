package com.example.model

data class JoystickSettings(
    val innerDeadzone: Float = 0.15f,
    val outerDeadzone: Float = 0.95f,
    val runThresholdNorm: Float = 0.75f,
    val sprintLockEnabled: Boolean = true,
    val curveExponent: Float = 1.0f     // Linear 1.0, Exponential 1.4
)

data class CameraSettings(
    val horizontalSensitivity: Float = 1.0f,
    val verticalSensitivity: Float = 0.85f,
    val accelerationCurve: Float = 1.2f,
    val smoothingFrames: Int = 3,
    val invertY: Boolean = false,
    val mouseDpiScale: Float = 1.0f
)

data class MappingConfig(
    val schemaVersion: Int = 2,
    val id: String,
    val profileName: String,
    val gamePackage: String,
    val gameTitle: String = "",
    val controllerType: ControllerType = ControllerType.XBOX,
    val targetAspectRatio: String = "19.5:9", // "16:9", "19.5:9", "20:9", "4:3"
    val joystick: JoystickSettings = JoystickSettings(),
    val camera: CameraSettings = CameraSettings(),
    val buttons: List<MappingNode> = emptyList(),
    val crosshair: CrosshairConfig = CrosshairConfig(),
    val antiRecoilEnabled: Boolean = false,
    val antiRecoilVerticalPull: Float = 0.0f,
    val tags: List<String> = emptyList(),
    val author: String = "Controlyst Community",
    val isOfficialVerified: Boolean = false,
    val downloadCount: Int = 0,
    val rating: Float = 4.8f,
    val lastUpdated: Long = System.currentTimeMillis()
)
