package com.example.calibration

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlin.math.hypot
import com.example.injector.InputInjector
import com.example.input.ControllerInputMonitor
import com.example.input.ControllerLiveState
import com.example.model.ControllerProfile
import com.example.model.ControllerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class StickCalibrationState(
    val phase: String = "NOT CALIBRATED",
    val restSamples: List<Float> = emptyList(),
    val maxSamples: List<Float> = emptyList(),
    val computedInnerDeadzone: Float = 0f,
    val computedOuterDeadzone: Float = 0f,
    val currentX: Float = 0f,
    val currentY: Float = 0f,
    val progressPercent: Float = 0f,
    val isMeasured: Boolean = false,
    val error: String? = null
)

data class TriggerCalibrationState(
    val phase: String = "NOT CALIBRATED",
    val restValue: Float = 0f,
    val maxPullValue: Float = 0f,
    val currentPull: Float = 0f,
    val progressPercent: Float = 0f,
    val isMeasured: Boolean = false,
    val error: String? = null,
    val pressThreshold: Float = .55f,
    val releaseThreshold: Float = .35f
)

data class TouchLatencyResult(
    val roundTripMs: Long = 0,
    val grade: String = "NOT MEASURED",
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

    private val calibrationLock = Mutex()

    /**
     * Activity and AccessibilityService both publish real controller MotionEvents to
     * ControllerInputMonitor. Android 14+ consumes motion sources requested by an
     * AccessibilityService instead of forwarding them to the foreground Activity, so
     * calibration must read the shared observed stream rather than an Activity-only flow.
     */
    fun onMotionEvent(event: MotionEvent) {
        ControllerInputMonitor.onMotionEvent(event)
    }

    private fun normalizedAxis(state: ControllerLiveState, axis: Int): Float? {
        val label = when (axis) {
            MotionEvent.AXIS_X -> "LX"
            MotionEvent.AXIS_Y -> "LY"
            MotionEvent.AXIS_Z -> "RX"
            MotionEvent.AXIS_RZ -> "RY"
            MotionEvent.AXIS_LTRIGGER -> "LT"
            MotionEvent.AXIS_RTRIGGER -> "RT"
            MotionEvent.AXIS_BRAKE -> "BRAKE"
            MotionEvent.AXIS_GAS -> "GAS"
            MotionEvent.AXIS_HAT_X -> "HAT_X"
            MotionEvent.AXIS_HAT_Y -> "HAT_Y"
            else -> null
        } ?: return null
        return state.normalizedAxes[label]
    }

    internal fun computeDeadzones(rest: List<Float>, extension: List<Float>): Pair<Float, Float> {
        require(rest.size >= 5 && extension.size >= 5) { "At least five real samples are required in each phase" }
        require((rest + extension).all { it.isFinite() && it in 0f..1.5f }) { "Invalid Android axis sample" }
        val inner = (rest.max() + .02f).coerceAtMost(.4f)
        val outer = extension.max().coerceAtMost(1f)
        require(rest.max() < .4f) { "Keep the stick centered during the rest phase" }
        require(outer > inner + .2f) { "Fully rotate the stick during extension sampling" }
        return inner to outer
    }

    suspend fun runStickCalibration(
        axisX: Int = MotionEvent.AXIS_X,
        axisY: Int = MotionEvent.AXIS_Y,
        onUpdate: (StickCalibrationState) -> Unit
    ): Boolean = withContext(Dispatchers.Default) {
        if (!calibrationLock.tryLock()) {
            _stickState.value = _stickState.value.copy(error = "Another controller calibration is already running")
            onUpdate(_stickState.value)
            return@withContext false
        }
        try {
            val device = InputDevice.getDeviceIds().map { InputDevice.getDevice(it) }.filterNotNull().firstOrNull {
                it.getMotionRange(axisX, InputDevice.SOURCE_JOYSTICK) != null &&
                    it.getMotionRange(axisY, InputDevice.SOURCE_JOYSTICK) != null
            } ?: error("No connected controller exposes the requested stick axes")
            require(normalizedAxis(ControllerLiveState(normalizedAxes = mapOf("LX" to 0f, "LY" to 0f)), axisX) != null || axisX in setOf(MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ)) {
                "Calibration does not support requested X axis=$axisX"
            }
            _stickState.value = StickCalibrationState(phase = "REST: center stick; move slightly and release")
            onUpdate(_stickState.value)
            val rest = mutableListOf<Float>()
            val extension = mutableListOf<Float>()
            suspend fun gather(target: MutableList<Float>, duration: Long, phase: String) {
                val start = SystemClock.uptimeMillis()
                var lastEvent = Long.MIN_VALUE
                withTimeoutOrNull(duration) {
                    ControllerInputMonitor.state.collect { sample ->
                        if (sample.deviceId != device.id || sample.lastEventUptimeMs == lastEvent) return@collect
                        val x = normalizedAxis(sample, axisX) ?: return@collect
                        val y = normalizedAxis(sample, axisY) ?: return@collect
                        lastEvent = sample.lastEventUptimeMs
                        val radius = hypot(x, y)
                        if (!radius.isFinite()) return@collect
                        target += radius
                        _stickState.value = _stickState.value.copy(
                            phase = phase,
                            currentX = x,
                            currentY = y,
                            progressPercent = ((SystemClock.uptimeMillis() - start).toFloat() / duration).coerceIn(0f, 1f)
                        )
                        onUpdate(_stickState.value)
                    }
                }
            }
            gather(rest, 2500, "REST: center stick; move slightly and release")
            _stickState.value = _stickState.value.copy(phase = "EXTENSION: rotate stick to its full edge")
            onUpdate(_stickState.value)
            gather(extension, 4000, "EXTENSION: rotate stick to its full edge")
            val (inner, outer) = computeDeadzones(rest, extension)
            require(InputDevice.getDevice(device.id)?.descriptor == device.descriptor) { "Controller disconnected or changed" }
            _stickState.value = _stickState.value.copy(
                phase = "CALIBRATION COMPLETE",
                restSamples = rest,
                maxSamples = extension,
                computedInnerDeadzone = inner,
                computedOuterDeadzone = outer,
                progressPercent = 1f,
                isMeasured = true,
                error = null
            )
            onUpdate(_stickState.value)
            true
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            _stickState.value = StickCalibrationState(phase = "CALIBRATION FAILED", error = error.message)
            android.util.Log.e("NexusCalibration", "Calibration failed", error)
            onUpdate(_stickState.value)
            false
        } finally {
            calibrationLock.unlock()
        }
    }

    suspend fun runTriggerCalibration(left: Boolean): Boolean = withContext(Dispatchers.Default) {
        if (!calibrationLock.tryLock()) {
            _triggerState.value = _triggerState.value.copy(error = "Another controller calibration is already running")
            return@withContext false
        }
        try {
            val preferred = if (left) MotionEvent.AXIS_LTRIGGER else MotionEvent.AXIS_RTRIGGER
            val alternate = if (left) MotionEvent.AXIS_BRAKE else MotionEvent.AXIS_GAS
            val device = InputDevice.getDeviceIds().map { InputDevice.getDevice(it) }.filterNotNull().firstOrNull {
                it.getMotionRange(preferred, InputDevice.SOURCE_JOYSTICK) != null ||
                    it.getMotionRange(alternate, InputDevice.SOURCE_JOYSTICK) != null
            } ?: error("No controller exposes this analog trigger")
            val axis = if (device.getMotionRange(preferred, InputDevice.SOURCE_JOYSTICK) != null) preferred else alternate
            val rest = mutableListOf<Float>()
            val pull = mutableListOf<Float>()
            suspend fun gather(target: MutableList<Float>, phase: String) {
                _triggerState.value = TriggerCalibrationState(phase = phase)
                var lastEvent = Long.MIN_VALUE
                withTimeoutOrNull(3000) {
                    ControllerInputMonitor.state.collect { sample ->
                        if (sample.deviceId != device.id || sample.lastEventUptimeMs == lastEvent) return@collect
                        val normalized = normalizedAxis(sample, axis) ?: return@collect
                        lastEvent = sample.lastEventUptimeMs
                        val value = (normalized + 1f) / 2f
                        if (value.isFinite()) {
                            target += value
                            _triggerState.value = _triggerState.value.copy(currentPull = value)
                        }
                    }
                }
            }
            gather(rest, "REST: release trigger, press slightly then release")
            gather(pull, "PULL: repeatedly pull trigger fully")
            val (press, release) = computeTriggerThresholds(rest, pull)
            require(InputDevice.getDevice(device.id)?.descriptor == device.descriptor) { "Controller disconnected or changed" }
            _triggerState.value = TriggerCalibrationState(
                "CALIBRATION COMPLETE",
                rest.max(),
                pull.max(),
                pull.last(),
                1f,
                true,
                null,
                press,
                release
            )
            true
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            _triggerState.value = TriggerCalibrationState(phase = "CALIBRATION FAILED", error = error.message)
            android.util.Log.e("NexusCalibration", "Trigger calibration failed", error)
            false
        } finally {
            calibrationLock.unlock()
        }
    }

    internal fun computeTriggerThresholds(rest: List<Float>, pull: List<Float>): Pair<Float, Float> {
        require(rest.size >= 3 && pull.size >= 3) { "Insufficient real trigger events" }
        require((rest + pull).all { it.isFinite() && it in 0f..1f }) { "Invalid Android trigger sample" }
        val minimum = rest.max()
        val maximum = pull.max()
        require(minimum < .4f && maximum - minimum > .5f) { "Release the trigger during rest and fully pull it during sampling" }
        val travel = maximum - minimum
        return minimum + travel * .55f to minimum + travel * .35f
    }

    // Measures the synchronous backend API call only; never device-to-photon latency.
    suspend fun measureTouchLatency(injector: InputInjector): TouchLatencyResult = withContext(Dispatchers.IO) {
        _latencyResult.value = TouchLatencyResult(isTesting = true)
        val start = SystemClock.elapsedRealtimeNanos()
        var ownsBackend = false
        var result: TouchLatencyResult
        try {
            check(!com.example.service.MappingRuntimeBridge.state.value.armed) { "Stop mapping before timing a separate backend call" }
            ownsBackend = true
            check(injector.prepare()) { "Backend not ready" }
            check(injector.injectTap(5f, 5f)) { "Backend rejected injection" }
            result = TouchLatencyResult(
                (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000,
                "BACKEND CALL DURATION; NOT TOUCH LATENCY"
            )
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            android.util.Log.e("NexusCalibration", "Injection timing failed", error)
            result = TouchLatencyResult(grade = "FAILED: ${error.message}")
        } finally {
            if (ownsBackend) try {
                injector.cleanup()
            } catch (error: Exception) {
                android.util.Log.e("NexusCalibration", "Timing backend cleanup failed", error)
                result = TouchLatencyResult(grade = "FAILED: backend cleanup: ${error.message}")
            }
        }
        _latencyResult.value = result
        result
    }
}
