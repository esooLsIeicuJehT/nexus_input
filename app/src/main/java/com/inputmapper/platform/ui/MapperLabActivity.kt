package com.inputmapper.platform.ui

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.inputmapper.platform.accessibility.MapperAccessibilityService
import com.inputmapper.platform.core.BackendSelector
import com.inputmapper.platform.core.FactoryResult
import com.inputmapper.platform.core.InjectionResult
import com.inputmapper.platform.core.InjectorFactory
import com.inputmapper.platform.core.InputInjector
import com.inputmapper.platform.mapper.ControllerCaptureBus
import com.inputmapper.platform.mapper.ControllerCaptureListener
import com.inputmapper.platform.mapper.ControllerKeySample
import com.inputmapper.platform.privilege.PrivilegeDetector
import java.util.concurrent.Executors

/**
 * End-to-end mapper validation using real controller events and real privileged touch injection.
 *
 * This intentionally maps only controller buttons to on-screen tap targets. It is not presented as
 * the finished game overlay editor. The same capture/inject path will be reused by the full mapper.
 */
class MapperLabActivity : Activity(), ControllerCaptureListener {
    private data class Binding(val keyCode: Int, val scanCode: Int, val targetIndex: Int) {
        fun label(): String = if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
            KeyEvent.keyCodeToString(keyCode)
        } else {
            "SCAN_$scanCode"
        }

        fun matches(sample: ControllerKeySample): Boolean =
            if (keyCode != KeyEvent.KEYCODE_UNKNOWN) sample.keyCode == keyCode
            else sample.keyCode == KeyEvent.KEYCODE_UNKNOWN && sample.scanCode == scanCode
    }

    private lateinit var status: TextView
    private lateinit var bindingView: TextView
    private lateinit var mapperButton: Button
    private val targets = mutableListOf<Button>()
    private val hits = IntArray(4)
    private val bindings = linkedMapOf<Int, Binding>()
    private var pendingTarget: Int? = null
    private var mapperActive = false
    private var listenerRegistered = false
    private var injector: InputInjector? = null
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = NexusUi.page(this)
        content.addView(NexusUi.eyebrow(this, "Mapper"))
        content.addView(NexusUi.title(this, "Button → Touch Lab", 27f))
        content.addView(NexusUi.body(this,
            "Bind physical controller buttons to the four targets below, then start the mapper. This validates the real capture → backend → touch pipeline before we move it into the in-game overlay editor."
        ))

        status = NexusUi.body(this, "Idle", 14f)
        val statusCard = NexusUi.card(this)
        statusCard.addView(NexusUi.eyebrow(this, "Engine State"))
        statusCard.addView(status, NexusUi.marginParams(this, top = 8))
        content.addView(statusCard)

        val targetCard = NexusUi.card(this)
        targetCard.addView(NexusUi.eyebrow(this, "Touch Targets"))
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        repeat(4) { index ->
            val target = Button(this).apply {
                text = "Target ${index + 1}\n0 hits"
                isAllCaps = false
                textSize = 14f
                setTextColor(Color.BLACK)
                background = NexusUi.rounded(this@MapperLabActivity, if (index % 2 == 0) NexusUi.cyan else NexusUi.violet, 18)
                setOnClickListener {
                    hits[index] += 1
                    text = "Target ${index + 1}\n${hits[index]} hits"
                }
                layoutParams = LinearLayout.LayoutParams(0, NexusUi.dp(this@MapperLabActivity, 92), 1f).apply {
                    val m = NexusUi.dp(this@MapperLabActivity, 6)
                    setMargins(m, m, m, m)
                }
            }
            targets += target
            if (index < 2) row1.addView(target) else row2.addView(target)
        }
        targetCard.addView(row1)
        targetCard.addView(row2)
        content.addView(targetCard)

        bindingView = NexusUi.body(this, "No bindings yet.", 13f)
        val bindCard = NexusUi.card(this)
        bindCard.addView(NexusUi.eyebrow(this, "Bindings"))
        bindCard.addView(bindingView, NexusUi.marginParams(this, top = 8))
        repeat(4) { index ->
            bindCard.addView(NexusUi.secondaryButton(this, "Bind Target ${index + 1}") { armBinding(index) })
        }
        content.addView(bindCard)

        mapperButton = NexusUi.primaryButton(this, "Start Mapper") {
            if (mapperActive) stopMapper("Mapper stopped") else startMapper()
        }
        content.addView(mapperButton)
        content.addView(NexusUi.dangerButton(this, "Clear Bindings") {
            bindings.clear()
            pendingTarget = null
            renderBindings()
        })
        content.addView(NexusUi.secondaryButton(this, "Back") { finish() })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(NexusUi.bg)
            addView(content)
        })
        renderBindings()
    }

    override fun onPause() {
        stopMapper("Mapper paused")
        stopCapture()
        super.onPause()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        injector?.cleanup()
        injector = null
        super.onDestroy()
    }

    private fun armBinding(targetIndex: Int) {
        val service = MapperAccessibilityService.current
        if (service == null) {
            Toast.makeText(this, "Accessibility service is not connected.", Toast.LENGTH_LONG).show()
            return
        }
        pendingTarget = targetIndex
        ensureCapture()
        status.text = "Press the controller button to bind Target ${targetIndex + 1}."
        status.setTextColor(NexusUi.cyan)
    }

    private fun ensureCapture() {
        if (!listenerRegistered) {
            ControllerCaptureBus.add(this)
            listenerRegistered = true
        }
        MapperAccessibilityService.current?.setControllerCaptureEnabled(true, consumeKeys = true)
    }

    private fun stopCapture() {
        if (listenerRegistered) {
            ControllerCaptureBus.remove(this)
            listenerRegistered = false
        }
        MapperAccessibilityService.current?.setControllerCaptureEnabled(false, consumeKeys = false)
    }

    override fun onControllerKey(sample: ControllerKeySample) {
        if (sample.action != KeyEvent.ACTION_DOWN) return
        val target = pendingTarget
        if (target != null) {
            bindings[target] = Binding(sample.keyCode, sample.scanCode, target)
            pendingTarget = null
            runOnUiThread {
                status.text = "Bound ${bindings[target]?.label()} → Target ${target + 1}"
                status.setTextColor(NexusUi.green)
                renderBindings()
            }
            return
        }
        if (!mapperActive) return
        val binding = bindings.values.firstOrNull { it.matches(sample) } ?: return
        val targetView = targets[binding.targetIndex]
        val loc = IntArray(2)
        targetView.getLocationOnScreen(loc)
        val x = loc[0] + targetView.width / 2f
        val y = loc[1] + targetView.height / 2f
        val active = injector ?: return
        executor.submit {
            val result = active.injectTap(x, y)
            if (result is InjectionResult.Failure) {
                runOnUiThread {
                    status.text = "Injection failed: ${result.code} ${result.message}"
                    status.setTextColor(NexusUi.red)
                }
            }
        }
    }

    private fun startMapper() {
        if (bindings.isEmpty()) {
            Toast.makeText(this, "Bind at least one controller button first.", Toast.LENGTH_LONG).show()
            return
        }
        if (MapperAccessibilityService.current == null) {
            Toast.makeText(this, "Accessibility service is not connected.", Toast.LENGTH_LONG).show()
            return
        }
        status.text = "Connecting preferred privileged backend…"
        status.setTextColor(NexusUi.amber)
        mapperButton.isEnabled = false

        Thread {
            val availability = PrivilegeDetector(this).detectAll()
            val selected = BackendSelector.choose(availability)
            if (selected == null) {
                runOnUiThread {
                    mapperButton.isEnabled = true
                    status.text = "No verified injection backend is available."
                    status.setTextColor(NexusUi.red)
                }
                return@Thread
            }
            val display = display ?: run {
                runOnUiThread {
                    mapperButton.isEnabled = true
                    status.text = "Display is unavailable; mapper was not started."
                    status.setTextColor(NexusUi.red)
                }
                return@Thread
            }
            val metrics = android.util.DisplayMetrics()
            display.getRealMetrics(metrics)
            when (val created = InjectorFactory(this).create(selected.kind, metrics.widthPixels, metrics.heightPixels)) {
                is FactoryResult.Ready -> runOnUiThread {
                    injector?.cleanup()
                    injector = created.injector
                    mapperActive = true
                    ensureCapture()
                    mapperButton.isEnabled = true
                    mapperButton.text = "Stop Mapper"
                    status.text = "ACTIVE • ${created.injector.backendName} • controller keys intentionally consumed"
                    status.setTextColor(NexusUi.green)
                }
                is FactoryResult.Error -> runOnUiThread {
                    mapperButton.isEnabled = true
                    status.text = "Backend start failed: ${created.message}"
                    status.setTextColor(NexusUi.red)
                }
            }
        }.start()
    }

    private fun stopMapper(message: String) {
        if (!mapperActive && injector == null) return
        mapperActive = false
        val current = injector
        injector = null
        if (current != null) executor.submit { current.cleanup() }
        stopCapture()
        if (::mapperButton.isInitialized) {
            mapperButton.text = "Start Mapper"
            mapperButton.isEnabled = true
        }
        if (::status.isInitialized) {
            status.text = message
            status.setTextColor(NexusUi.muted)
        }
    }

    private fun renderBindings() {
        bindingView.text = if (bindings.isEmpty()) {
            "No bindings yet. Unknown Android key codes are matched by their real scan code, so special controller buttons are not silently discarded."
        } else {
            bindings.toSortedMap().entries.joinToString("\n") { (target, binding) ->
                "${binding.label()}  →  Target ${target + 1}"
            }
        }
    }
}
