package com.example.model

data class JoystickSettings(
    val innerDeadzone: Float = 0.15f,
    val outerDeadzone: Float = 0.95f,
    val runThresholdNorm: Float = 0.75f,
    val sprintLockEnabled: Boolean = false,
    val walkRadiusScale: Float = 0.62f,
    val runRadiusScale: Float = 1.0f,
    val curveExponent: Float = 1.0f     // Linear 1.0, Exponential 1.4
)

data class CameraSettings(
    val horizontalSensitivity: Float = 1.0f,
    val verticalSensitivity: Float = 0.85f,
    val accelerationCurve: Float = 1.2f,
    val smoothingFrames: Int = 3,
    val verticalRatio: Float = 1.0f,
    val fastTurnBoost: Float = 1.0f,
    val invertY: Boolean = false,
    val mouseDpiScale: Float = 1.0f
)

data class MappingConfig(
    val schemaVersion: Int = 3,
    val id: String,
    val profileName: String,
    val gamePackage: String,
    val gameTitle: String = "",
    val controllerType: ControllerType = ControllerType.XBOX,
    val preferredBackend: PrivilegeMethod? = null,
    val controllerProfileId: String? = null,
    val targetAspectRatio: String = "19.5:9", // "16:9", "19.5:9", "20:9", "4:3"
    val joystick: JoystickSettings = JoystickSettings(),
    val camera: CameraSettings = CameraSettings(),
    val buttons: List<MappingNode> = emptyList(),
    val crosshair: CrosshairConfig = CrosshairConfig(),
    val antiRecoilEnabled: Boolean = false,
    val antiRecoilVerticalPull: Float = 0.0f,
    val tags: List<String> = emptyList(),
    val author: String = "Local user",
    val isOfficialVerified: Boolean = false,
    val downloadCount: Int = 0,
    val rating: Float = 0f,
    val lastUpdated: Long = System.currentTimeMillis()
)
