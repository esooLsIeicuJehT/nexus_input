package com.inputmapper.platform.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.input.InputManager
import android.os.Bundle
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs

/**
 * Foreground controller telemetry/calibration screen.
 *
 * This screen intentionally reports the Android events actually delivered by the connected
 * controller instead of assuming an Xbox/PlayStation layout. The root daemon will later consume
 * evdev directly for system-wide mapping; this activity establishes the real Android-visible
 * button/axis contract for profile calibration.
 */
class ControllerLiveActivity : Activity(), InputManager.InputDeviceListener {
    private lateinit var inputManager: InputManager
    private lateinit var deviceView: TextView
    private lateinit var eventView: TextView
    private lateinit var axesView: TextView
    private val pressedKeys = linkedSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inputManager = getSystemService(InputManager::class.java)

        val title = TextView(this).apply {
            text = "Live controller input"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        val subtitle = TextView(this).apply {
            text = "Press buttons, move sticks, D-pad and triggers. Values below are real events from Android, not a guessed controller profile."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 6, 0, 18)
        }
        deviceView = panel("Controller inventory")
        eventView = panel("Last button event\nNone yet")
        axesView = panel("Live axes\nMove a stick or trigger")

        val back = Button(this).apply {
            text = "Back to diagnostics"
            isAllCaps = false
            setOnClickListener { finish() }
        }
        val refresh = Button(this).apply {
            text = "Refresh controller inventory"
            isAllCaps = false
            setOnClickListener { refreshInventory() }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(26, 46, 26, 46)
            setBackgroundColor(Color.rgb(10, 17, 29))
            addView(title, matchWrap())
            addView(subtitle, matchWrap())
            addView(deviceView, matchWrap())
            addView(eventView, matchWrap())
            addView(axesView, matchWrap())
            addView(refresh, matchWrap())
            addView(back, matchWrap())
        }

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(10, 17, 29))
            addView(content)
        })
        refreshInventory()
    }

    override fun onResume() {
        super.onResume()
        inputManager.registerInputDeviceListener(this, null)
        refreshInventory()
    }

    override fun onPause() {
        inputManager.unregisterInputDeviceListener(this)
        super.onPause()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val device = event.device
        if (device != null && isController(device)) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> pressedKeys.add(event.keyCode)
                KeyEvent.ACTION_UP -> pressedKeys.remove(event.keyCode)
            }
            eventView.text = buildString {
                append("Last button event\n")
                append(device.name).append("  id=").append(event.deviceId).append('\n')
                append(if (event.action == KeyEvent.ACTION_DOWN) "DOWN" else "UP")
                append("  ").append(KeyEvent.keyCodeToString(event.keyCode))
                append("  keyCode=").append(event.keyCode)
                append(" scanCode=").append(event.scanCode)
                append(" repeat=").append(event.repeatCount).append('\n')
                append("Pressed: ")
                if (pressedKeys.isEmpty()) {
                    append("none")
                } else {
                    append(pressedKeys.joinToString { KeyEvent.keyCodeToString(it) })
                }
            }
            // This activity is explicitly an input tester, so consume controller keys (including
            // controller BACK/B mappings) rather than letting Android navigate away mid-test.
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val device = event.device
        if (device != null && isJoystickEvent(event)) {
            val ranges = device.motionRanges
                .filter { range -> range.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK }
                .sortedBy { it.axis }

            axesView.text = buildString {
                append("Live axes\n")
                append(device.name).append("  id=").append(event.deviceId).append('\n')
                ranges.forEach { range ->
                    val raw = event.getAxisValue(range.axis)
                    val normalized = normalize(raw, range.getMin(), range.getMax(), range.getFlat())
                    append(MotionEvent.axisToString(range.axis))
                        .append(" (").append(range.axis).append(")")
                        .append(" raw=").append("%.4f".format(raw))
                        .append(" norm=").append("%.4f".format(normalized))
                        .append(" range=").append("%.3f..%.3f".format(range.getMin(), range.getMax()))
                        .append(" flat=").append("%.4f".format(range.getFlat()))
                        .append('\n')
                }
            }.trimEnd()
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun refreshInventory() {
        val devices = InputDevice.getDeviceIds()
            .map { InputDevice.getDevice(it) }
            .filterNotNull()
            .filter(::isController)

        deviceView.text = if (devices.isEmpty()) {
            "Controller inventory\nNo GAMEPAD/JOYSTICK device is currently visible to Android."
        } else {
            buildString {
                append("Controller inventory\n")
                devices.forEachIndexed { index, device ->
                    if (index > 0) append("\n")
                    append("#").append(device.id).append(' ').append(device.name).append('\n')
                    append("VID:PID %04x:%04x".format(device.vendorId, device.productId))
                    append(" descriptor=").append(device.descriptor).append('\n')
                    append("sources=").append(sourceNames(device.sources)).append('\n')
                    val ranges = device.motionRanges
                        .filter { it.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK }
                        .sortedBy { it.axis }
                    append("axes=")
                    if (ranges.isEmpty()) append("none") else append(ranges.joinToString { MotionEvent.axisToString(it.axis) })
                    append('\n')
                }
            }.trimEnd()
        }
    }

    private fun panel(initial: String) = TextView(this).apply {
        text = initial
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

    private fun isController(device: InputDevice): Boolean {
        val sources = device.sources
        return sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
    }

    private fun isJoystickEvent(event: MotionEvent): Boolean =
        event.action == MotionEvent.ACTION_MOVE &&
            event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK

    private fun sourceNames(sources: Int): String = buildList {
        if (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) add("GAMEPAD")
        if (sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) add("JOYSTICK")
        if (sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD) add("KEYBOARD")
    }.joinToString("+").ifBlank { "0x${sources.toString(16)}" }

    /**
     * Report a range-derived normalized value without inventing a device-specific center.
     * Bipolar axes (-1..1 style sticks) normalize around zero. Unipolar axes (0..1 triggers)
     * normalize from min to max. `flat` is applied only where the documented range crosses zero.
     */
    private fun normalize(value: Float, min: Float, max: Float, flat: Float): Float {
        if (!value.isFinite() || !min.isFinite() || !max.isFinite() || max <= min) return 0f
        return if (min < 0f && max > 0f) {
            val denominator = maxOf(abs(min), abs(max))
            if (denominator <= 0f) return 0f
            if (abs(value) <= flat) 0f else (value / denominator).coerceIn(-1f, 1f)
        } else {
            ((value - min) / (max - min)).coerceIn(0f, 1f)
        }
    }

    override fun onInputDeviceAdded(deviceId: Int) = refreshInventory()
    override fun onInputDeviceRemoved(deviceId: Int) = refreshInventory()
    override fun onInputDeviceChanged(deviceId: Int) = refreshInventory()
}
