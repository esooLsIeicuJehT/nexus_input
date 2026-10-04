package com.inputmapper.platform.mapper

import java.util.concurrent.CopyOnWriteArraySet

data class ControllerKeySample(
    val deviceId: Int,
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val action: Int,
    val keyCode: Int,
    val scanCode: Int,
    val eventTime: Long
)

data class ControllerAxisSample(
    val deviceId: Int,
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val values: Map<Int, Float>,
    val eventTime: Long
)

interface ControllerCaptureListener {
    fun onControllerKey(sample: ControllerKeySample) = Unit
    fun onControllerAxes(sample: ControllerAxisSample) = Unit
}

/**
 * In-process event bus fed by MapperAccessibilityService while explicit controller capture is on.
 * It carries only real Android InputDevice events and does not manufacture controller state.
 */
object ControllerCaptureBus {
    private val listeners = CopyOnWriteArraySet<ControllerCaptureListener>()

    fun add(listener: ControllerCaptureListener) {
        listeners.add(listener)
    }

    fun remove(listener: ControllerCaptureListener) {
        listeners.remove(listener)
    }

    internal fun publishKey(sample: ControllerKeySample) {
        listeners.forEach { it.onControllerKey(sample) }
    }

    internal fun publishAxes(sample: ControllerAxisSample) {
        listeners.forEach { it.onControllerAxes(sample) }
    }
}

/**
 * Controller motion capture is intentionally opt-in because Android 14+ AccessibilityService
 * motion-event sources are consumed from normal dispatch while requested. Calibration enables it
 * only while the calibration screen is active; the mapper will later own it while mapping is on.
 */
object ControllerCaptureControl {
    @Volatile var enabled: Boolean = false
        private set
    @Volatile var consumeKeys: Boolean = false
        private set

    fun set(enabled: Boolean, consumeKeys: Boolean) {
        this.enabled = enabled
        this.consumeKeys = enabled && consumeKeys
    }
}
