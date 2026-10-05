package com.example.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-local monitor for physical controller events delivered to the Nexus Input activity.
 *
 * This is intentionally observational only: it does not synthesize input and it does not
 * manufacture values for axes a device does not expose. The Devices tester can therefore
 * distinguish real Android events from configured profile values.
 */
data class ControllerLiveState(
    val connectedEventSource: String? = null,
    val deviceId: Int? = null,
    val axes: Map<String, Float> = emptyMap(),
    val normalizedAxes: Map<String, Float> = emptyMap(),
    val pressedButtons: Set<String> = emptySet(),
    val lastEventUptimeMs: Long = 0L
)

object ControllerInputMonitor {
    private val _state = MutableStateFlow(ControllerLiveState())
    val state: StateFlow<ControllerLiveState> = _state.asStateFlow()

    fun onMotionEvent(event: MotionEvent) {
        if (!isControllerSource(event.source)) return

        val device = event.device
        val ranges = device?.motionRanges.orEmpty()
        val axes = linkedMapOf<String, Float>()
        val normalized = linkedMapOf<String, Float>()

        fun capture(axis: Int, label: String) {
            val range=ranges.firstOrNull { it.axis==axis && it.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK } ?: return
            val raw=event.getAxisValue(axis)
            if(!raw.isFinite() || !range.min.isFinite() || !range.max.isFinite() || range.min>=range.max) {
                android.util.Log.e("NexusInput","Invalid observed controller axis $axis on device ${event.deviceId}")
                return
            }
            axes[label] = raw
            val center=if(range.min<0f && range.max>0f) 0f else (range.min+range.max)/2f
            val span=maxOf(kotlin.math.abs(range.max-center),kotlin.math.abs(range.min-center))
            normalized[label]=((raw-center)/span).coerceIn(-1f,1f)
        }

        capture(MotionEvent.AXIS_X, "LX")
        capture(MotionEvent.AXIS_Y, "LY")
        capture(MotionEvent.AXIS_Z, "RX")
        capture(MotionEvent.AXIS_RZ, "RY")
        capture(MotionEvent.AXIS_LTRIGGER, "LT")
        capture(MotionEvent.AXIS_RTRIGGER, "RT")
        capture(MotionEvent.AXIS_BRAKE, "BRAKE")
        capture(MotionEvent.AXIS_GAS, "GAS")
        capture(MotionEvent.AXIS_HAT_X, "HAT_X")
        capture(MotionEvent.AXIS_HAT_Y, "HAT_Y")

        _state.value = _state.value.copy(
            connectedEventSource = device?.name,
            deviceId = device?.id,
            axes = axes,
            normalizedAxes = normalized,
            lastEventUptimeMs = event.eventTime
        )
    }

    fun onKeyEvent(event: KeyEvent) {
        if (!isControllerSource(event.source)) return

        val label = KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_")
        val updated = _state.value.pressedButtons.toMutableSet()
        when (event.action) {
            KeyEvent.ACTION_DOWN -> updated += label
            KeyEvent.ACTION_UP -> updated -= label
            else -> return
        }

        _state.value = _state.value.copy(
            connectedEventSource = event.device?.name,
            deviceId = event.device?.id,
            pressedButtons = updated,
            lastEventUptimeMs = event.eventTime
        )
    }

    fun onDeviceRemoved(id: Int) {
        if(_state.value.deviceId==id) _state.value=ControllerLiveState()
    }

    private fun isControllerSource(source: Int): Boolean {
        val gamepad = source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
        val joystick = source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        val dpad = source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
        return gamepad || joystick || dpad
    }
}

/** Independent of tester UI state so activity/service listener order cannot hide a disconnect. */
internal class ControllerSessionDevices {
    private val captured = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    fun record(deviceId: Int) { captured.add(deviceId) }
    fun remove(deviceId: Int): Boolean = captured.remove(deviceId)
    fun clear() = captured.clear()
}
