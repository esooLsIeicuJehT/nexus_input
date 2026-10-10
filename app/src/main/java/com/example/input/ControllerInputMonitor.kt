package com.example.input

import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Process-local monitor for physical controller events delivered to Nexus Input.
 *
 * This is intentionally observational only: it does not synthesize input and it does not
 * manufacture values for axes a device does not expose. The Devices tester and calibration
 * can therefore distinguish real Android events from configured profile values.
 */
data class ControllerLiveState(
    val connectedEventSource: String? = null,
    val deviceId: Int? = null,
    val axes: Map<String, Float> = emptyMap(),
    val normalizedAxes: Map<String, Float> = emptyMap(),
    val pressedButtons: Set<String> = emptySet(),
    val lastEventUptimeMs: Long = 0L,
    val observedEventSequence: Long = 0L,
    val lastKeyCode: Int? = null,
    val lastScanCode: Int? = null,
    val lastKeyName: String? = null,
    val lastSource: Int? = null,
    val lastKeyAction: Int? = null,
    val lastKeySequence: Long = 0L,
    val eventLog: List<String> = emptyList()
)

object ControllerInputMonitor {
    private val _state = MutableStateFlow(ControllerLiveState())
    val state: StateFlow<ControllerLiveState> = _state.asStateFlow()
    private val observedEvents = AtomicLong()
    private val eventLock = Any()
    private var lastKeyIdentity: KeyIdentity? = null

    private data class KeyIdentity(
        val deviceId: Int,
        val eventTime: Long,
        val action: Int,
        val keyCode: Int,
        val scanCode: Int,
        val source: Int
    )

    fun onMotionEvent(event: MotionEvent) {
        if (!ControllerSourceClassifier.accepts(event.source, event.device?.sources ?: 0)) return

        val device = event.device
        val ranges = device?.motionRanges.orEmpty()
        val axes = linkedMapOf<String, Float>()
        val normalized = linkedMapOf<String, Float>()

        fun capture(axis: Int, label: String) {
            val range=ranges.firstOrNull { it.axis==axis } ?: return
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
        ranges.distinctBy { it.axis }.forEach { range ->
            val label = "AXIS_${range.axis}"
            if (label !in axes) capture(range.axis, label)
        }
        val motionLine = "MOTION dev=${event.deviceId} src=0x${event.source.toString(16)} " +
            axes.entries.joinToString(" ") { "${it.key}=${"%.3f".format(it.value)}" }

        _state.value = _state.value.copy(
            connectedEventSource = device?.name,
            deviceId = device?.id,
            axes = axes,
            normalizedAxes = normalized,
            lastSource = event.source,
            eventLog = (_state.value.eventLog + motionLine).takeLast(64),
            lastEventUptimeMs = event.eventTime,
            observedEventSequence = observedEvents.incrementAndGet()
        )
    }

    fun onKeyEvent(event: KeyEvent) {
        if (!ControllerSourceClassifier.accepts(event.source, event.device?.sources ?: 0)) return
        val identity = KeyIdentity(event.deviceId,event.eventTime,event.action,event.keyCode,event.scanCode,event.source)
        synchronized(eventLock) {
            // The foreground Activity and AccessibilityService can both observe the same
            // physical KeyEvent. Android preserves eventTime/device/action/key/scan/source
            // across that delivery, so drop only an exact duplicate observation.
            if (lastKeyIdentity == identity) return
            lastKeyIdentity = identity
        }

        val label = KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_")
        val updated = _state.value.pressedButtons.toMutableSet()
        when (event.action) {
            KeyEvent.ACTION_DOWN -> updated += label
            KeyEvent.ACTION_UP -> updated -= label
            else -> return
        }

        val actionName = if (event.action == KeyEvent.ACTION_DOWN) "DOWN" else "UP"
        val line = "$actionName dev=${event.deviceId} src=0x${event.source.toString(16)} key=${event.keyCode} ${KeyEvent.keyCodeToString(event.keyCode)} scan=${event.scanCode}"
        val sequence = observedEvents.incrementAndGet()
        _state.value = _state.value.copy(
            connectedEventSource = event.device?.name,
            deviceId = event.device?.id,
            pressedButtons = updated,
            lastKeyCode = event.keyCode,
            lastScanCode = event.scanCode,
            lastKeyName = KeyEvent.keyCodeToString(event.keyCode),
            lastSource = event.source,
            lastKeyAction = event.action,
            lastKeySequence = sequence,
            eventLog = (_state.value.eventLog + line).takeLast(64),
            lastEventUptimeMs = event.eventTime,
            observedEventSequence = sequence
        )
    }

    fun onDeviceRemoved(id: Int) {
        if(_state.value.deviceId==id) {
            _state.value=ControllerLiveState(observedEventSequence = observedEvents.incrementAndGet())
        }
    }
}

/** Independent of tester UI state so activity/service listener order cannot hide a disconnect. */
internal class ControllerSessionDevices {
    private val captured = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    fun record(deviceId: Int) { captured.add(deviceId) }
    fun remove(deviceId: Int): Boolean = captured.remove(deviceId)
    fun clear() = captured.clear()
}
