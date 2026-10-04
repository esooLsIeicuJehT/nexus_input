package com.inputmapper.platform.input

import android.view.InputDevice

/** Snapshot of Android-visible input devices. No controller is invented when none is present. */
data class InputDeviceSnapshot(
    val id: Int,
    val name: String,
    val descriptor: String,
    val vendorId: Int,
    val productId: Int,
    val sources: Int,
    val axes: List<AxisSnapshot>
)

data class AxisSnapshot(
    val axis: Int,
    val source: Int,
    val min: Float,
    val max: Float,
    val flat: Float,
    val fuzz: Float,
    val resolution: Float
)

object ControllerInventory {
    fun snapshot(): List<InputDeviceSnapshot> = InputDevice.getDeviceIds()
        .map { id -> InputDevice.getDevice(id) }
        .filterNotNull()
        .filter(::isInteresting)
        .map { device ->
            InputDeviceSnapshot(
                id = device.id,
                name = device.name ?: "Unnamed input device",
                descriptor = device.descriptor ?: "",
                vendorId = device.vendorId,
                productId = device.productId,
                sources = device.sources,
                axes = device.motionRanges.map { range ->
                    AxisSnapshot(
                        axis = range.axis,
                        source = range.source,
                        min = range.getMin(),
                        max = range.getMax(),
                        flat = range.getFlat(),
                        fuzz = range.getFuzz(),
                        resolution = range.getResolution()
                    )
                }
            )
        }

    private fun isInteresting(device: InputDevice): Boolean {
        val sources = device.sources
        return sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            sources and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE ||
            sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD
    }
}
