package com.example.input

import android.view.KeyEvent

/** Canonical mapper labels accepted for Android controller key events. */
object ControllerBindingAliases {
    private const val STADIA_VENDOR_ID = 6353
    private const val STADIA_PRODUCT_ID = 37888

    fun forEvent(event: KeyEvent): Set<String> {
        val standard = forKeyCode(event.keyCode)
        if (standard.isNotEmpty()) return standard
        val device = event.device
        if (device?.vendorId != STADIA_VENDOR_ID || device.productId != STADIA_PRODUCT_ID) return emptySet()
        return when (event.scanCode) {
            314 -> setOf("SELECT", "BACK")
            315 -> setOf("START")
            317 -> setOf("L3")
            318 -> setOf("R3")
            else -> emptySet()
        }
    }

    fun forKeyCode(keyCode: Int): Set<String> = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> setOf("A", "CROSS")
        KeyEvent.KEYCODE_BUTTON_B -> setOf("B", "CIRCLE")
        KeyEvent.KEYCODE_BUTTON_X -> setOf("X", "SQUARE")
        KeyEvent.KEYCODE_BUTTON_Y -> setOf("Y", "TRIANGLE")
        KeyEvent.KEYCODE_BUTTON_L1 -> setOf("LB", "L1")
        KeyEvent.KEYCODE_BUTTON_R1 -> setOf("RB", "R1")
        KeyEvent.KEYCODE_BUTTON_L2 -> setOf("LT", "L2")
        KeyEvent.KEYCODE_BUTTON_R2 -> setOf("RT", "R2")
        KeyEvent.KEYCODE_BUTTON_THUMBL -> setOf("L3")
        KeyEvent.KEYCODE_BUTTON_THUMBR -> setOf("R3")
        KeyEvent.KEYCODE_BUTTON_START -> setOf("START")
        KeyEvent.KEYCODE_BUTTON_SELECT -> setOf("SELECT", "BACK")
        KeyEvent.KEYCODE_BUTTON_MODE -> setOf("GUIDE", "MODE")
        KeyEvent.KEYCODE_DPAD_UP -> setOf("DPAD_UP", "UP", "D_UP")
        KeyEvent.KEYCODE_DPAD_DOWN -> setOf("DPAD_DOWN", "DOWN", "D_DOWN")
        KeyEvent.KEYCODE_DPAD_LEFT -> setOf("DPAD_LEFT", "LEFT", "D_LEFT")
        KeyEvent.KEYCODE_DPAD_RIGHT -> setOf("DPAD_RIGHT", "RIGHT", "D_RIGHT")
        else -> emptySet()
    }

    fun leftTrigger(): Set<String> = setOf("LT", "L2")
    fun rightTrigger(): Set<String> = setOf("RT", "R2")
    fun dpadUp(): Set<String> = setOf("DPAD_UP", "UP", "D_UP")
    fun dpadDown(): Set<String> = setOf("DPAD_DOWN", "DOWN", "D_DOWN")
    fun dpadLeft(): Set<String> = setOf("DPAD_LEFT", "LEFT", "D_LEFT")
    fun dpadRight(): Set<String> = setOf("DPAD_RIGHT", "RIGHT", "D_RIGHT")

    val supported = setOf("A", "B", "X", "Y", "LB", "RB", "LT", "RT", "L3", "R3",
        "START", "SELECT", "GUIDE", "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "LS", "RS")

    fun canonical(value: String): String = when (normalized(value)) {
        "CROSS" -> "A"; "CIRCLE" -> "B"; "SQUARE" -> "X"; "TRIANGLE" -> "Y"
        "L1" -> "LB"; "R1" -> "RB"; "L2" -> "LT"; "R2" -> "RT"
        "BACK" -> "SELECT"; "MODE" -> "GUIDE"
        "D_UP", "UP" -> "DPAD_UP"; "D_DOWN", "DOWN" -> "DPAD_DOWN"
        "D_LEFT", "LEFT" -> "DPAD_LEFT"; "D_RIGHT", "RIGHT" -> "DPAD_RIGHT"
        else -> normalized(value)
    }

    fun normalized(value: String): String = value.trim().uppercase()
}
