package com.example.input

import android.view.KeyEvent
import android.view.MotionEvent

/** Canonical mapper labels accepted for Android controller key events. */
object ControllerBindingAliases {
    private const val STADIA_VENDOR_ID = 6353
    private const val STADIA_PRODUCT_ID = 37888

    fun forEvent(event: KeyEvent): Set<String> {
        if (!ControllerSourceClassifier.accepts(event.source, event.device?.sources ?: 0)) return emptySet()
        val device = event.device
        val stadia = device?.vendorId == STADIA_VENDOR_ID && device.productId == STADIA_PRODUCT_ID
        // Google's Stadia mapping uses BUTTON_1/2 for Assistant/Capture. Moto's
        // Generic.kl can instead deliver UNKNOWN with the same Linux scans.
        // Scope these extra buttons to Stadia; other controllers use them differently.
        if (stadia) when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_1 -> return setOf("ASSISTANT", "ASSIST")
            KeyEvent.KEYCODE_BUTTON_2 -> return setOf("CAPTURE", "SCREENSHOT")
        }
        val standard = forKeyCode(event.keyCode)
        if (standard.isNotEmpty()) return standard
        if (!stadia) return emptySet()
        return forStadiaScan(event.scanCode)
    }

    internal fun forStadiaScan(scanCode: Int): Set<String> = when (scanCode) {
            314 -> setOf("SELECT", "BACK")
            315 -> setOf("START")
            316 -> setOf("GUIDE", "MODE", "HOME")
            317 -> setOf("L3")
            318 -> setOf("R3")
            // Observed in the user's Moto tester (2026-10-11). AOSP's Stadia
            // bridge identifies BTN_TRIGGER_HAPPY1/2 as Assistant/Share, and
            // Linux defines those scans as 0x2c0/0x2c1 (704/705).
            704 -> setOf("ASSISTANT", "ASSIST")
            705 -> setOf("CAPTURE", "SCREENSHOT")
            else -> emptySet()
    }

    /** Real HAT directions for learning; never invent a KeyEvent or scan code. */
    fun forHatMotion(event: MotionEvent, historyIndex: Int? = null): Set<String> {
        if (event.actionMasked != MotionEvent.ACTION_MOVE ||
            !ControllerSourceClassifier.accepts(event.source, event.device?.sources ?: 0)) return emptySet()
        fun value(axis: Int): Float {
            val range = event.device?.motionRanges?.firstOrNull { it.axis == axis } ?: return 0f
            if (!range.min.isFinite() || !range.max.isFinite() || range.min >= range.max) return 0f
            return (if (historyIndex == null) event.getAxisValue(axis)
                else event.getHistoricalAxisValue(axis, historyIndex)).takeIf { it.isFinite() } ?: 0f
        }
        val x = value(MotionEvent.AXIS_HAT_X)
        val y = value(MotionEvent.AXIS_HAT_Y)
        return buildSet {
            if (x < -.5f) add("DPAD_LEFT")
            if (x > .5f) add("DPAD_RIGHT")
            if (y < -.5f) add("DPAD_UP")
            if (y > .5f) add("DPAD_DOWN")
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
        KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU -> setOf("START", "MENU")
        KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BACK -> setOf("SELECT", "BACK")
        KeyEvent.KEYCODE_BUTTON_MODE, KeyEvent.KEYCODE_HOME -> setOf("GUIDE", "MODE", "HOME")
        KeyEvent.KEYCODE_ASSIST, KeyEvent.KEYCODE_VOICE_ASSIST -> setOf("ASSISTANT", "ASSIST")
        KeyEvent.KEYCODE_SYSRQ, KeyEvent.KEYCODE_MEDIA_RECORD -> setOf("CAPTURE", "SCREENSHOT")
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
        "START", "SELECT", "GUIDE", "CAPTURE", "ASSISTANT", "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "LS", "RS")

    fun canonical(value: String): String = when (normalized(value)) {
        "CROSS" -> "A"; "CIRCLE" -> "B"; "SQUARE" -> "X"; "TRIANGLE" -> "Y"
        "L1" -> "LB"; "R1" -> "RB"; "L2" -> "LT"; "R2" -> "RT"
        "BACK" -> "SELECT"; "MODE", "HOME" -> "GUIDE"; "MENU" -> "START"
        "THUMBL" -> "L3"; "THUMBR" -> "R3"
        "ASSIST", "VOICE_ASSIST" -> "ASSISTANT"; "SCREENSHOT", "SYSRQ", "MEDIA_RECORD" -> "CAPTURE"
        "D_UP", "UP" -> "DPAD_UP"; "D_DOWN", "DOWN" -> "DPAD_DOWN"
        "D_LEFT", "LEFT" -> "DPAD_LEFT"; "D_RIGHT", "RIGHT" -> "DPAD_RIGHT"
        else -> normalized(value)
    }

    fun normalized(value: String): String = value.trim().uppercase()
        .removePrefix("KEYCODE_").removePrefix("BUTTON_")
        .replace('-', '_').replace(' ', '_')
}
