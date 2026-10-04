package com.inputmapper.platform.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.InputDevice
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.inputmapper.platform.core.BackendKind
import com.inputmapper.platform.core.FactoryResult
import com.inputmapper.platform.core.InjectionResult
import com.inputmapper.platform.core.InjectorFactory
import com.inputmapper.platform.core.InjectorSessionManager
import com.inputmapper.platform.debug.SystemSelfCheck
import com.inputmapper.platform.input.ControllerInventory
import com.inputmapper.platform.privilege.PrivilegeDetector
import com.inputmapper.platform.root.KernelSUInjector
import com.inputmapper.platform.setup.SetupActivity
import com.inputmapper.platform.shizuku.ShizukuAccess
import rikka.shizuku.Shizuku

/**
 * Hardware/system validation dashboard.
 *
 * Self Check is read-only. Explicit injection-test buttons remain available for targeted hardware
 * validation, but the app never runs those tests automatically.
 */
class Phase0DiagnosticsActivity : Activity() {
    private val sessions = InjectorSessionManager()
    private lateinit var summaryView: TextView
    private lateinit var logView: TextView
    private lateinit var targetButton: Button
    private var targetHitCount = 0
    private var lastSelfCheckReport: String = "No self-check report has been generated yet."

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_REQUEST) {
            val state = if (grantResult == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"
            runOnUiThread {
                feedback("Shizuku permission: $state")
                detect()
            }
        }
    }

    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread {
            feedback("Shizuku/Sui binder received")
            detect()
        }
    }

    private val shizukuBinderDeadListener = Shizuku.OnBinderDeadListener {
        runOnUiThread {
            feedback("Shizuku/Sui binder disconnected")
            detect()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = NexusUi.page(this)
        content.addView(NexusUi.eyebrow(this, "Engine & System"))
        content.addView(NexusUi.title(this, "Diagnostics", 28f))
        content.addView(
            NexusUi.body(
                this,
                "Read-only Self Check audits permissions, backends, controllers, calibration, profiles and mapper runtime. Injection tests run only when you explicitly press them."
            )
        )

        val summaryCard = NexusUi.card(this)
        summaryCard.addView(NexusUi.eyebrow(this, "Backend Matrix"))
        summaryView = NexusUi.body(this, "Reading live state…", 13f)
        summaryCard.addView(summaryView, NexusUi.marginParams(this, top = 8))
        content.addView(summaryCard)

        targetButton = NexusUi.primaryButton(this, "Injection target: 0 hits") {
            targetHitCount += 1
            targetButton.text = "Injection target: $targetHitCount hit${if (targetHitCount == 1) "" else "s"}"
            feedback("TARGET HIT #$targetHitCount")
        }
        content.addView(targetButton)

        content.addView(NexusUi.primaryButton(this, "Run full read-only self-check") { runSelfCheck() })
        content.addView(NexusUi.secondaryButton(this, "Copy self-check report") { copySelfCheckReport() })
        content.addView(NexusUi.secondaryButton(this, "Permissions / first-run setup") {
            startActivity(Intent(this, SetupActivity::class.java))
        })
        content.addView(NexusUi.secondaryButton(this, "Refresh backend status") { immediate("Refreshing system status") { detect() } })
        content.addView(NexusUi.secondaryButton(this, "Detect controllers / mouse / keyboard") { immediate("Scanning Android input devices") { detectInputDevices() } })
        content.addView(NexusUi.secondaryButton(this, "Open live controller input") {
            startActivity(Intent(this, ControllerLiveActivity::class.java))
        })
        content.addView(NexusUi.secondaryButton(this, "Authorize / repair Shizuku") { repairShizukuAuthorization() })
        content.addView(NexusUi.secondaryButton(this, "Open Shizuku manager") {
            if (!ShizukuAccess.openManager(this)) {
                feedback("Shizuku manager launch intent is unavailable. KernelSU remains usable.")
            }
        })
        content.addView(NexusUi.secondaryButton(this, "Connect KernelSU root engine") { immediate("Connecting KernelSU root engine") { connectOnly(BackendKind.KERNEL_SU) } })
        content.addView(NexusUi.secondaryButton(this, "Test KernelSU on target") { immediate("Injecting KernelSU touch at target") { connectAndTapTarget(BackendKind.KERNEL_SU) } })
        content.addView(NexusUi.secondaryButton(this, "Test Shizuku on target") { immediate("Injecting Shizuku touch at target") { connectAndTapTarget(BackendKind.SHIZUKU) } })
        content.addView(NexusUi.dangerButton(this, "Stop active diagnostic engine") { immediate("Stopping active engine") { runOffMain("cleanup") { sessions.stop() } } })

        val logCard = NexusUi.card(this)
        logCard.addView(NexusUi.eyebrow(this, "Operation Log"))
        logView = NexusUi.body(this, "Diagnostics ready.\n", 12f).apply {
            typeface = android.graphics.Typeface.MONOSPACE
        }
        logCard.addView(logView, NexusUi.marginParams(this, top = 8))
        content.addView(logCard)
        content.addView(NexusUi.secondaryButton(this, "Back") { finish() })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(NexusUi.bg)
            addView(content)
        })

        // Sticky binder callbacks may run immediately. Register only after the dashboard exists.
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener)
        Shizuku.addBinderDeadListener(shizukuBinderDeadListener)
        detect()
    }

    private fun immediate(message: String, action: () -> Unit) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        action()
    }

    private fun detect() {
        Thread {
            val results = PrivilegeDetector(this).detectAll()
            val shizuku = ShizukuAccess.snapshot(this)
            val text = buildString {
                results.forEach {
                    append(it.kind).append(": ").append(it.state)
                    it.versionName?.let { version -> append("  ").append(version) }
                    append('\n').append("  ").append(it.details).append("\n\n")
                }
                append("Shizuku authorization detail: ").append(shizuku.summary())
            }.trim()
            runOnUiThread { summaryView.text = text }
        }.start()
    }

    private fun runSelfCheck() {
        logView.text = "Running read-only self-check…\n"
        Thread {
            val report = runCatching { SystemSelfCheck(this).run() }
                .getOrElse { "SELF CHECK FAILED\n${it.javaClass.simpleName}: ${it.message ?: "unknown"}" }
            lastSelfCheckReport = report
            runOnUiThread {
                logView.text = report
                Toast.makeText(this, "Self-check complete", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun copySelfCheckReport() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        if (clipboard == null) {
            feedback("Clipboard service is unavailable")
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("NEXUS INPUT self-check", lastSelfCheckReport))
        Toast.makeText(this, "Self-check copied", Toast.LENGTH_SHORT).show()
    }

    private fun detectInputDevices() {
        Thread {
            val devices = ControllerInventory.snapshot()
            val text = if (devices.isEmpty()) {
                "No Android-visible gamepad, joystick, mouse, or keyboard devices found."
            } else {
                devices.joinToString("\n\n") { device ->
                    val sourceNames = buildList {
                        if (device.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) add("GAMEPAD")
                        if (device.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) add("JOYSTICK")
                        if (device.sources and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE) add("MOUSE")
                        if (device.sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD) add("KEYBOARD")
                        if (device.sources and InputDevice.SOURCE_TOUCHSCREEN == InputDevice.SOURCE_TOUCHSCREEN) add("TOUCHSCREEN")
                    }.joinToString("+").ifBlank { "0x${device.sources.toString(16)}" }
                    buildString {
                        append("#").append(device.id).append(' ').append(device.name).append('\n')
                        append("VID:PID %04x:%04x".format(device.vendorId, device.productId))
                        append("  sources=").append(sourceNames)
                        if (device.axes.isNotEmpty()) {
                            append('\n').append("axes: ")
                            append(device.axes.joinToString { axis ->
                                "${axis.axis}[${axis.min}..${axis.max} flat=${axis.flat} fuzz=${axis.fuzz}]"
                            })
                        }
                    }
                }
            }
            runOnUiThread {
                appendLog(text)
                Toast.makeText(this, "Input scan complete: ${devices.size} matching device(s)", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun repairShizukuAuthorization() {
        try {
            val state = ShizukuAccess.snapshot(this)
            when {
                !state.binderAlive -> feedback("Shizuku/Sui binder is unavailable. Start Shizuku/Sui or use KernelSU.")
                state.permissionGranted -> feedback("Shizuku/Sui permission is already granted (uid=${state.serverUid ?: -1}).")
                state.permissionBlocked -> {
                    feedback("Shizuku authorization is blocked. Open Shizuku > Authorized applications and enable NEXUS INPUT.")
                    if (!ShizukuAccess.openManager(this)) feedback("Shizuku manager launch intent is unavailable.")
                }
                else -> {
                    Shizuku.requestPermission(SHIZUKU_REQUEST)
                    feedback("Shizuku permission request sent.")
                }
            }
        } catch (t: Throwable) {
            feedback("Shizuku permission request failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun connectOnly(kind: BackendKind) {
        Thread {
            val metrics = resources.displayMetrics
            when (val result = InjectorFactory(this).create(kind, metrics.widthPixels, metrics.heightPixels)) {
                is FactoryResult.Error -> runOnUiThread { feedback("$kind connect failed: ${result.message}") }
                is FactoryResult.Ready -> {
                    val replace = sessions.replace(result.injector)
                    if (replace !is InjectionResult.Success) {
                        runOnUiThread { feedback("$kind session replace failed: $replace") }
                    } else {
                        val health = (result.injector as? KernelSUInjector)?.health()
                        runOnUiThread {
                            feedback("$kind connected via ${result.injector.backendName}")
                            if (health != null) appendLog("root health: $health")
                        }
                    }
                }
            }
        }.start()
    }

    private fun connectAndTapTarget(kind: BackendKind) {
        val location = IntArray(2)
        targetButton.getLocationOnScreen(location)
        val x = location[0] + targetButton.width / 2f
        val y = location[1] + targetButton.height / 2f
        if (targetButton.width <= 0 || targetButton.height <= 0) {
            feedback("Injection target is not laid out yet; retry after the screen settles.")
            return
        }

        Thread {
            val metrics = resources.displayMetrics
            when (val factoryResult = InjectorFactory(this).create(kind, metrics.widthPixels, metrics.heightPixels)) {
                is FactoryResult.Error -> runOnUiThread { feedback("$kind connect failed: ${factoryResult.message}") }
                is FactoryResult.Ready -> {
                    val replace = sessions.replace(factoryResult.injector)
                    if (replace !is InjectionResult.Success) {
                        runOnUiThread { feedback("$kind session replace failed: $replace") }
                        return@Thread
                    }
                    val result = factoryResult.injector.injectTap(x, y)
                    val health = (factoryResult.injector as? KernelSUInjector)?.health()
                    runOnUiThread {
                        feedback("$kind target tap (${x.toInt()},${y.toInt()}): $result")
                        if (health != null) appendLog("root health: $health")
                    }
                }
            }
        }.start()
    }

    private fun runOffMain(label: String, block: () -> InjectionResult) {
        Thread {
            val result = block()
            runOnUiThread { feedback("$label: $result") }
        }.start()
    }

    private fun feedback(line: String) {
        appendLog(line)
        Toast.makeText(this, line.take(180), Toast.LENGTH_SHORT).show()
    }

    private fun appendLog(line: String) {
        logView.append("\n$line\n")
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
        Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
        sessions.stop()
        super.onDestroy()
    }

    companion object {
        private const val SHIZUKU_REQUEST = 4001
    }
}
