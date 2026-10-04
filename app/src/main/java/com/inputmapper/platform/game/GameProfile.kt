package com.inputmapper.platform.game

import android.view.KeyEvent
import android.view.MotionEvent

enum class TouchActionType { TAP, HOLD }
enum class StickActionType { VIRTUAL_JOYSTICK, CAMERA_DRAG }

data class ControllerButtonBinding(
    val keyCode: Int,
    val scanCode: Int
) {
    fun label(): String = if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
        KeyEvent.keyCodeToString(keyCode)
    } else {
        "SCAN_$scanCode"
    }

    fun matches(keyCode: Int, scanCode: Int): Boolean =
        if (this.keyCode != KeyEvent.KEYCODE_UNKNOWN) this.keyCode == keyCode
        else keyCode == KeyEvent.KEYCODE_UNKNOWN && this.scanCode == scanCode
}

data class TouchMapping(
    val id: String,
    val label: String,
    val input: ControllerButtonBinding,
    val action: TouchActionType,
    val xNorm: Float,
    val yNorm: Float,
    val slot: Int
)

data class StickMapping(
    val id: String,
    val label: String,
    val action: StickActionType,
    val axisX: Int,
    val axisY: Int,
    val xNorm: Float,
    val yNorm: Float,
    val radiusNorm: Float,
    val sensitivity: Float,
    val deadzone: Float,
    val invertY: Boolean,
    val slot: Int
)

data class GameProfile(
    val profileId: String,
    val displayName: String,
    val packageName: String,
    val controllerProfileId: String?,
    val preferredBackend: String?,
    val savedAtMillis: Long,
    val touchMappings: List<TouchMapping>,
    val stickMappings: List<StickMapping>
) {
    companion object {
        fun fresh(id: String, name: String, packageName: String): GameProfile = GameProfile(
            profileId = id,
            displayName = name,
            packageName = packageName,
            controllerProfileId = null,
            preferredBackend = null,
            savedAtMillis = System.currentTimeMillis(),
            touchMappings = emptyList(),
            stickMappings = listOf(
                StickMapping(
                    id = "left-stick",
                    label = "LS",
                    action = StickActionType.VIRTUAL_JOYSTICK,
                    axisX = MotionEvent.AXIS_X,
                    axisY = MotionEvent.AXIS_Y,
                    xNorm = 0.22f,
                    yNorm = 0.72f,
                    radiusNorm = 0.11f,
                    sensitivity = 1f,
                    deadzone = 0.12f,
                    invertY = false,
                    slot = 0
                ),
                StickMapping(
                    id = "right-camera",
                    label = "RS",
                    action = StickActionType.CAMERA_DRAG,
                    axisX = MotionEvent.AXIS_Z,
                    axisY = MotionEvent.AXIS_RZ,
                    xNorm = 0.74f,
                    yNorm = 0.56f,
                    radiusNorm = 0.18f,
                    sensitivity = 1f,
                    deadzone = 0.12f,
                    invertY = false,
                    slot = 1
                )
            )
        )
    }
}
