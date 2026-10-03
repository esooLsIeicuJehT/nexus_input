package com.example.model

enum class ControllerType(val displayName: String, val vendorIdMatch: List<Int> = emptyList()) {
    XBOX("Xbox Wireless / Elite Controller", listOf(0x045E)),
    PLAYSTATION("PlayStation DualSense / DS4", listOf(0x054C)),
    NINTENDO_SWITCH("Nintendo Switch Pro Controller", listOf(0x057E)),
    STADIA("Google Stadia Controller", listOf(0x18D1)),
    GAMESIR("GameSir G8 / X2 Pro", listOf(0x3537)),
    RAZER_KISHI("Razer Kishi / Edge", listOf(0x1532)),
    BACKBONE("Backbone One", listOf(0x358A)),
    EIGHT_BIT_DO("8BitDo Ultimate / Pro 2", listOf(0x2DC8)),
    GENERIC_HID("Generic HID / DirectInput", emptyList()),
    MOUSE_KEYBOARD("Mouse + Keyboard Combo", emptyList())
}

data class ControllerProfile(
    val id: String = "default_controller",
    val type: ControllerType = ControllerType.XBOX,
    val manualOverride: Boolean = false,
    val stickInnerDeadzone: Float = 0.12f,
    val stickOuterDeadzone: Float = 0.98f,
    val triggerDeadzone: Float = 0.05f,
    val triggerMaxPull: Float = 1.0f,
    val pollingRateHz: Int = 250,       // 125Hz, 250Hz, 500Hz, 1000Hz
    val swapAB: Boolean = false,        // Swap Nintendo vs Xbox layout
    val swapXY: Boolean = false,
    val gyroAimingEnabled: Boolean = false,
    val gyroSensitivity: Float = 1.0f
)
