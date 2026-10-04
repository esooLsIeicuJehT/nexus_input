package com.inputmapper.platform.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.inputmapper.platform.accessibility.MapperAccessibilityService
import com.inputmapper.platform.mapper.ControllerAxisSample
import com.inputmapper.platform.mapper.ControllerCaptureBus
import com.inputmapper.platform.mapper.ControllerCaptureListener
import com.inputmapper.platform.mapper.ControllerKeySample
import com.inputmapper.platform.profile.AxisCalibration
import com.inputmapper.platform.profile.ButtonCalibration
import com.inputmapper.platform.profile.ControllerProfile
import com.inputmapper.platform.profile.ControllerProfileStore
import kotlin.math.max
import kotlin.math.min

/**
 * Real-device controller calibration recorder.
 *
 * Capture is explicit and temporary because AccessibilityService joystick motion capture consumes
 * SOURCE_JOYSTICK events from normal dispatch on Android 14+. The screen turns capture off whenever
 * it is paused/stopped.
 */
class ControllerCalibrationActivity : Activity(), ControllerCaptureListener {
    private lateinit var statusView: TextView
    private lateinit var samplesView: TextView
    private lateinit var startStopButton: Button
    private val axisStats = linkedMapOf<Int, AxisStats>()
    private val buttons = linkedMapOf<String, ButtonCalibration>()
    private var selectedDevice: InputDevice? = null
    private var axisFrameCount = 0
    private var capturing = false
    private var centerWindowEndsAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = text("Controller calibration", 28f, true)
        val subtitle = text(
            "This records the controller Android actually reports. Start capture, leave both sticks centered for a moment, then move both sticks in full circles, pull both triggers fully, press D-pad directions, and press every button once.",
            14f,
            false
        ).apply { setTextColor(Color.LTGRAY) }

        statusView = panel("Looking for a controller…")
        samplesView = panel("No calibration samples recorded yet.")

        startStopButton = Button(this).apply {
            text = "Start calibration capture"
            isAllCaps = false
            setOnClickListener {
                if (capturing) stopCapture("Capture stopped") else startCapture()
            }
        }
        val save = Button(this).apply {
            text = "Save controller profile"
            isAllCaps = false
            setOnClickListener { saveProfile() }
        }
        val reset = Button(this).apply {
            text = "Reset captured data"
            isAllCaps = false
            setOnClickListener {
                stopCapture("Capture stopped")
                resetData()
            }
        }
        val back = Button(this).apply {
            text = "Back"
            isAllCaps = false
            setOnClickListener { finish() }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(26, 46, 26, 46)
            setBackgroundColor(Color.rgb(10, 17, 29))
            addView(title, matchWrap())
            addView(subtitle, matchWrap())
            addView(statusView, matchWrap())
            addView(samplesView, matchWrap())
            addView(startStopButton, matchWrap())
            addView(save, matchWrap())
            addView(reset, matchWrap())
            addView(back, matchWrap())
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(10, 17, 29))
            addView(content)
        })

        selectController()
        render()
    }

    override fun onResume() {
        super.onResume()
        selectController()
        render()
    }

    override fun onPause() {
        stopCapture("Calibration capture paused")
        super.onPause()
    }

    private fun startCapture() {
        val service = MapperAccessibilityService.current
        if (service == null) {
            Toast.makeText(this, "Accessibility service is not connected. Enable NEXUS INPUT Accessibility first.", Toast.LENGTH_LONG).show()
            return
        }
        selectController()
        val device = selectedDevice
        if (device == null) {
            Toast.makeText(this, "No GAMEPAD/JOYSTICK controller is connected.", Toast.LENGTH_LONG).show()
            return
        }

        if (axisStats.isEmpty()) initializeAxisStats(device)
        ControllerCaptureBus.add(this)
        service.setControllerCaptureEnabled(true, consumeKeys = true)
        if (axisFrameCount == 0) centerWindowEndsAt = android.os.SystemClock.uptimeMillis() + 1200L
        capturing = true
        startStopButton.text = "Stop calibration capture"
        Toast.makeText(this, "Calibration capture active", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun stopCapture(message: String) {
        if (!capturing) return
        ControllerCaptureBus.remove(this)
        MapperAccessibilityService.current?.setControllerCaptureEnabled(false, consumeKeys = false)
        capturing = false
        startStopButton.text = "Start calibration capture"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        render()
    }

    private fun resetData() {
        axisStats.clear()
        buttons.clear()
        axisFrameCount = 0
        selectedDevice?.let(::initializeAxisStats)
        render()
    }

    override fun onControllerKey(sample: ControllerKeySample) {
        val device = selectedDevice ?: return
        if (sample.deviceId != device.id || sample.action != KeyEvent.ACTION_DOWN) return
        val key = "${sample.keyCode}:${sample.scanCode}"
        buttons[key] = ButtonCalibration(sample.keyCode, sample.scanCode)
        runOnUiThread { render() }
    }

    override fun onControllerAxes(sample: ControllerAxisSample) {
        val device = selectedDevice ?: return
        if (sample.deviceId != device.id) return
        sample.values.forEach { (axis, value) ->
            axisStats[axis]?.record(value, android.os.SystemClock.uptimeMillis() <= centerWindowEndsAt)
        }
        axisFrameCount += 1
        runOnUiThread { render() }
    }

    private fun saveProfile() {
        val device = selectedDevice
        if (device == null) {
            Toast.makeText(this, "No controller is connected; nothing was saved.", Toast.LENGTH_LONG).show()
            return
        }
        if (axisFrameCount == 0 || buttons.isEmpty()) {
            Toast.makeText(
                this,
                "Calibration is incomplete. Record joystick/trigger motion and at least one button before saving.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        stopCapture("Capture stopped for save")
        val calibratedAxes = axisStats.values.filter { it.samples > 0 }.map { stats ->
            AxisCalibration(
                axis = stats.axis,
                declaredMin = stats.declaredMin,
                declaredMax = stats.declaredMax,
                flat = stats.flat,
                fuzz = stats.fuzz,
                resolution = stats.resolution,
                center = stats.centerValue(),
                observedMin = stats.observedMin,
                observedMax = stats.observedMax
            )
        }.sortedBy { it.axis }

        val profileId = ControllerProfileStore.stableProfileId(device.vendorId, device.productId, device.descriptor ?: "")
        ControllerProfileStore(this).save(
            ControllerProfile(
                profileId = profileId,
                deviceName = device.name ?: "Unnamed controller",
                descriptor = device.descriptor ?: "",
                vendorId = device.vendorId,
                productId = device.productId,
                savedAtMillis = System.currentTimeMillis(),
                axes = calibratedAxes,
                buttons = buttons.values.toList()
            )
        )
        Toast.makeText(this, "Saved ${device.name}: ${calibratedAxes.size} axes, ${buttons.size} buttons", Toast.LENGTH_LONG).show()
        render()
    }

    private fun selectController() {
        val currentId = selectedDevice?.id
        val current = currentId?.let { InputDevice.getDevice(it) }
        if (current != null && isController(current)) {
            selectedDevice = current
            return
        }
        selectedDevice = InputDevice.getDeviceIds()
            .map { InputDevice.getDevice(it) }
            .filterNotNull()
            .firstOrNull(::isController)
        if (axisStats.isEmpty()) selectedDevice?.let(::initializeAxisStats)
    }

    private fun initializeAxisStats(device: InputDevice) {
        axisStats.clear()
        device.motionRanges
            .filter { it.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK }
            .sortedBy { it.axis }
            .forEach { range ->
                axisStats[range.axis] = AxisStats(
                    axis = range.axis,
                    declaredMin = range.getMin(),
                    declaredMax = range.getMax(),
                    flat = range.getFlat(),
                    fuzz = range.getFuzz(),
                    resolution = range.getResolution()
                )
            }
    }

    private fun render() {
        val device = selectedDevice
        statusView.text = if (device == null) {
            "Controller\nNo GAMEPAD/JOYSTICK device detected. Connect a controller and reopen/refresh this screen."
        } else {
            buildString {
                append("Controller\n")
                append(device.name).append("  id=").append(device.id).append('\n')
                append("VID:PID %04x:%04x".format(device.vendorId, device.productId)).append('\n')
                append("Capture: ").append(if (capturing) "ACTIVE (controller events are intentionally intercepted)" else "OFF")
            }
        }

        samplesView.text = buildString {
            append("Recorded calibration\n")
            append("motion frames=").append(axisFrameCount)
            append("  buttons=").append(buttons.size).append('\n')
            if (buttons.isNotEmpty()) {
                append("keys: ")
                append(buttons.values.sortedWith(compareBy<ButtonCalibration> { it.keyCode }.thenBy { it.scanCode }).joinToString { button ->
                    if (button.keyCode == KeyEvent.KEYCODE_UNKNOWN) "KEYCODE_UNKNOWN(scan=${button.scanCode})"
                    else "${KeyEvent.keyCodeToString(button.keyCode)}(scan=${button.scanCode})"
                })
                append('\n')
            }
            axisStats.values.forEach { stats ->
                append(MotionEvent.axisToString(stats.axis))
                    .append(" declared=")
                    .append("%.3f..%.3f".format(stats.declaredMin, stats.declaredMax))
                if (stats.samples > 0) {
                    append(" center=").append("%.4f".format(stats.centerValue()))
                    append(" observed=").append("%.4f..%.4f".format(stats.observedMin, stats.observedMax))
                    append(" samples=").append(stats.samples)
                } else {
                    append(" observed=none")
                }
                append('\n')
            }
        }.trimEnd()
    }

    private fun isController(device: InputDevice): Boolean {
        val sources = device.sources
        return sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
    }

    private fun text(value: String, size: Float, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.WHITE)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun panel(value: String) = TextView(this).apply {
        text = value
        textSize = 13f
        typeface = Typeface.MONOSPACE
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(18, 26, 39))
        setPadding(20, 20, 20, 20)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private data class AxisStats(
        val axis: Int,
        val declaredMin: Float,
        val declaredMax: Float,
        val flat: Float,
        val fuzz: Float,
        val resolution: Float,
        var samples: Int = 0,
        var centerSamples: Int = 0,
        var centerSum: Double = 0.0,
        var observedMin: Float = Float.POSITIVE_INFINITY,
        var observedMax: Float = Float.NEGATIVE_INFINITY
    ) {
        fun record(value: Float, centerWindow: Boolean) {
            if (!value.isFinite()) return
            if (centerWindow) {
                centerSum += value.toDouble()
                centerSamples += 1
            }
            observedMin = min(observedMin, value)
            observedMax = max(observedMax, value)
            samples += 1
        }

        fun centerValue(): Float = if (centerSamples > 0) (centerSum / centerSamples).toFloat() else 0f
    }
}
