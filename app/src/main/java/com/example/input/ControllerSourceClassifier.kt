package com.example.input

import android.view.InputDevice

/**
 * Classifies controller input without trusting only the event's source bits.
 *
 * Some Android gamepads report individual buttons (notably menu/D-pad style
 * keys) with keyboard-ish event sources even though the originating device
 * advertises GAMEPAD/JOYSTICK/DPAD capability. Accept those events only when
 * either the event source or the device's advertised sources are controller
 * sources, so ordinary keyboards are not captured as gamepads.
 */
object ControllerSourceClassifier {
    fun isControllerSource(source: Int): Boolean {
        val gamepad = source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
        val joystick = source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        val dpad = source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
        return gamepad || joystick || dpad
    }

    fun accepts(eventSource: Int, deviceSources: Int): Boolean =
        isControllerSource(eventSource) || isControllerSource(deviceSources)
}
