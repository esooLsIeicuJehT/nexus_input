package com.example.calibration

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import com.example.injector.InputInjector
import com.example.model.ControllerProfile
import com.example.model.ControllerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class StickCalibrationState(
    val phase: String = "REST",
    val restSamples: List<Float> = emptyList(),
    val maxSamples: List<Float> = emptyList(),
    val computedInnerDeadzone: Float = 0.12f,
    val computedOuterDeadzone: Float = 0.98f,
    val currentX: Float = 0f,
    val currentY: Float = 0f,
    val progressPercent: Float = 0f
)

data class TriggerCalibrationState(
    val phase: String = "REST",
    val restValue: Float = 0f,
    val maxPullValue: Float = 1.0f,
    val currentPull: Float = 0f,
    val progressPercent: Float = 0f
)

data class TouchLatencyResult(
    val roundTripMs: Long = 0,
    val grade: String = "EXCELLENT",
    val isTesting: Boolean = false
)

class CalibrationManager(private val context: Context) {

    private val _stickState = MutableStateFlow(StickCalibrationState())
    val stickState: StateFlow<StickCalibrationState> = _stickState.asStateFlow()

    private val _triggerState = MutableStateFlow(TriggerCalibrationState())
    val triggerState: StateFlow<TriggerCalibrationState> = _triggerState.asStateFlow()

    private val _latencyResult = MutableStateFlow(TouchLatencyResult())
    val latencyResult: StateFlow<TouchLatencyResult> = _latencyResult.asStateFlow()

    /**
     * Probes Android's real InputDevice inventory. No controller is reported when
     * Android exposes no gamepad/joystick device.
     */
    fun detectConnectedController(): ControllerProfile {
        val deviceIds = InputDevice.getDeviceIds()

        for (id in deviceIds) {
            val dev = InputDevice.getDevice(id) ?: continue
            val sources = dev.sources
            val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK

            if (!isGamepad) continue

            val name = dev.name.lowercase()
            val detectedType = when {
                name.contains("stadia") -> ControllerType.STADIA
                name.contains("dualsense") || name.contains("wireless controller") ||
                    name.contains("playstation") || name.contains("sony") -> ControllerType.PLAYSTATION
                name.contains("switch") || name.contains("pro controller") -> ControllerType.NINTENDO_SWITCH
                name.contains("gamesir") -> ControllerType.GAMESIR
                name.contains("kishi") || name.contains("razer") -> ControllerType.RAZER_KISHI
                name.contains("backbone") -> ControllerType.BACKBONE
                name.contains("8bitdo") -> ControllerType.EIGHT_BIT_DO
                name.contains("xbox") || name.contains("microsoft") -> ControllerType.XBOX
                else -> ControllerType.GENERIC_HID
            }

            return ControllerProfile(
                type = detectedType,
                connected = true,
                deviceName = dev.name,
                vendorId = dev.vendorId.takeIf { it != 0 },
                productId = dev.productId.takeIf { it != 0 },
                manualOverride = false,
                swapAB = detectedType == ControllerType.NINTENDO_SWITCH
            )
        }

        for (id in deviceIds) {
            val dev = InputDevice.getDevice(id) ?: continue
            val sources = dev.sources
            val hasMouse = (sources and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE
            val hasKeyboard = dev.keyboardType != InputDevice.KEYBOARD_TYPE_NONE
            if (hasMouse || hasKeyboard) {
                return ControllerProfile(
                    type = ControllerType.MOUSE_KEYBOARD,
                    connected = true,
                    deviceName = dev.name,
                    vendorId = dev.vendorId.takeIf { it != 0 },
                    productId = dev.productId.takeIf { it != 0 },
                    manualOverride = false
                )
            }
        }

        return ControllerProfile(
            type = ControllerType.GENERIC_HID,
            connected = false,
            deviceName = null,
            vendorId = null,
            productId = null,
            manualOverride = false
        )
    }

    /**
     * Legacy calibration path. This currently does not consume raw MotionEvent
     * samples from the Activity, so its values MUST NOT be presented as measured
     * hardware data in Nexus Input UI. Live-device calibration will replace this
     * path when raw event sampling is wired end-to-end.
     */
    suspend fun runStickCalibration(
        onUpdate: (StickCalibrationState) -> Unit
    ) = withContext(Dispatchers.Default) {
        for (i in 1..20) {
            delay(100)
            val simulatedNoise = (0.01f + (Math.random().toFloat() * 0.04f))
            val progress = i / 40f
            _stickState.value = _stickState.value.copy(
                phase = "REST (Legacy simulation; raw input sampling not wired)",
                currentX = simulatedNoise,
                currentY = -simulatedNoise,
                progressPercent = progress
            )
            onUpdate(_stickState.value)
        }

        for (i in 21..40) {
            delay(100)
            val maxRadius = 0.94f + (Math.random().toFloat() * 0.05f)
            val progress = i / 40f
            _stickState.value = _stickState.value.copy(
                phase = "MAX EXTENSION (Legacy simulation; raw input sampling not wired)",
                currentX = maxRadius * 0.707f,
                currentY = maxRadius * 0.707f,
                progressPercent = progress
            )
            onUpdate(_stickState.value)
        }

        _stickState.value = _stickState.value.copy(
            phase = "LEGACY CALIBRATION COMPLETE — NOT HARDWARE VERIFIED",
            computedInnerDeadzone = 0.08f,
            computedOuterDeadzone = 0.98f,
            progressPercent = 1.0f
        )
        onUpdate(_stickState.value)
    }

    /**
     * Legacy latency helper. The elapsed value includes an artificial delay and is
     * not a frame-present or device-to-photon measurement. Nexus UI does not label
     * it as real input latency.
     */
    suspend fun measureTouchLatency(injector: InputInjector): TouchLatencyResult = withContext(Dispatchers.Default) {
        _latencyResult.value = TouchLatencyResult(isTesting = true)
        val startTime = SystemClock.uptimeMillis()

        injector.injectTap(5f, 5f)
        delay(35)

        val elapsed = (SystemClock.uptimeMillis() - startTime).coerceAtLeast(6L)
        val result = TouchLatencyResult(
            roundTripMs = elapsed,
            grade = "LEGACY TIMING — NOT HARDWARE VERIFIED",
            isTesting = false
        )
        _latencyResult.value = result
        result
    }
}
