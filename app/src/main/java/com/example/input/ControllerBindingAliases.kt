package com.example.input

import android.view.KeyEvent

/** Canonical mapper labels accepted for Android controller key events. */
object ControllerBindingAliases {
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
        KeyEvent.KEYCODE_DPAD_UP -> setOf("DPAD_UP", "UP")
        KeyEvent.KEYCODE_DPAD_DOWN -> setOf("DPAD_DOWN", "DOWN")
        KeyEvent.KEYCODE_DPAD_LEFT -> setOf("DPAD_LEFT", "LEFT")
        KeyEvent.KEYCODE_DPAD_RIGHT -> setOf("DPAD_RIGHT", "RIGHT")
        else -> emptySet()
    }

    fun leftTrigger(): Set<String> = setOf("LT", "L2")
    fun rightTrigger(): Set<String> = setOf("RT", "R2")
    fun dpadUp(): Set<String> = setOf("DPAD_UP", "UP")
    fun dpadDown(): Set<String> = setOf("DPAD_DOWN", "DOWN")
    fun dpadLeft(): Set<String> = setOf("DPAD_LEFT", "LEFT")
    fun dpadRight(): Set<String> = setOf("DPAD_RIGHT", "RIGHT")

    fun normalized(value: String): String = value.trim().uppercase()
}
