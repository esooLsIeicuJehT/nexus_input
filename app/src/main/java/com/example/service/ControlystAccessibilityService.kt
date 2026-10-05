package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.res.Configuration
import android.graphics.Path
import android.graphics.PointF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.example.injector.InputInjector
import com.example.injector.InputInjectorFactory
import com.example.injector.PrivilegeDetector
import com.example.input.ControllerInputMonitor
import com.example.input.ControllerSessionDevices
import com.example.injector.PrivilegeBackendSelector
import com.example.input.GamepadMappingRuntime
import com.example.model.PrivilegeMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class ControlystAccessibilityService : AccessibilityService() {
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val backendExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mappingRuntime = GamepadMappingRuntime(
        screenSizeProvider = ::screenSize,
        onError = { message ->
            Log.e(TAG, message)
            MappingRuntimeBridge.disarm(message)
            teardownInjectorAsync()
        }
    )

    @Volatile
    private var activeInjector: InputInjector? = null

    @Volatile
    private var runtimePreparing = false

    private val capturedDevices = ControllerSessionDevices()

    private val inputListener = object : android.hardware.input.InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = Unit
        override fun onInputDeviceChanged(deviceId: Int) = Unit
        override fun onInputDeviceRemoved(deviceId: Int) {
            val used=capturedDevices.remove(deviceId)
            ControllerInputMonitor.onDeviceRemoved(deviceId)
            if(used && MappingRuntimeBridge.state.value.armed) PanicKillSwitch.triggerPanic(this@ControlystAccessibilityService,"Controller disconnected")
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        currentInstance = this
        getSystemService(android.hardware.input.InputManager::class.java).registerInputDeviceListener(inputListener,mainHandler)

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.flags = info.flags or
            AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            info.motionEventSources = InputDevice.SOURCE_JOYSTICK
        }
        serviceInfo = info

        runtimeScope.launch {
            MappingRuntimeBridge.state.collectLatest { state ->
                if (!state.armed) {
                    teardownInjectorAsync()
                    return@collectLatest
                }

                if (!state.targetForeground) {
                    val foreground = rootInActiveWindow?.packageName?.toString()
                    if (!foreground.isNullOrBlank() && !isTransientSystemPackage(foreground)) {
                        MappingRuntimeBridge.setForegroundPackage(foreground)
                    }
                }

                val refreshed = MappingRuntimeBridge.state.value
                if (refreshed.armed && refreshed.targetForeground) {
                    prepareRuntimeAsync()
                } else {
                    teardownInjectorAsync()
                }
            }
        }

        Log.i(TAG, "NEXUS accessibility controller capture connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val pkg = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        if (isTransientSystemPackage(pkg)) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            MappingRuntimeBridge.setForegroundPackage(pkg)
        } else {
            val state = MappingRuntimeBridge.state.value
            if (state.armed && pkg == state.gamePackage && !state.targetForeground) {
                MappingRuntimeBridge.setForegroundPackage(pkg)
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        ControllerInputMonitor.onKeyEvent(event)
        if (!isControllerSource(event.source)) return false

        val state = MappingRuntimeBridge.state.value
        val config = MappingRuntimeBridge.config.value
        val injector = activeInjector
        if (!state.armed || !state.targetForeground || !state.backendReady || config == null || injector == null) {
            if (state.armed && state.targetForeground) prepareRuntimeAsync()
            return false
        }

        val handled = mappingRuntime.handleKeyEvent(event, config, injector)
        if (handled) capturedDevices.record(event.deviceId)
        return handled
    }

    override fun onMotionEvent(event: MotionEvent) {
        ControllerInputMonitor.onMotionEvent(event)
        if (!isControllerSource(event.source)) return

        val state = MappingRuntimeBridge.state.value
        val config = MappingRuntimeBridge.config.value
        val injector = activeInjector
        if (!state.armed || !state.targetForeground || !state.backendReady || config == null || injector == null) {
            if (state.armed && state.targetForeground) prepareRuntimeAsync()
            return
        }
        capturedDevices.record(event.deviceId)
        mappingRuntime.handleMotionEvent(event, config, injector)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        reconnectForGeometryChange()
    }

    override fun onInterrupt() {
        Log.w(TAG, "NEXUS accessibility service interrupted")
        MappingRuntimeBridge.disarm("Accessibility capture interrupted; mapper disarmed")
        teardownInjectorAsync()
    }

    override fun onDestroy() {
        getSystemService(android.hardware.input.InputManager::class.java).unregisterInputDeviceListener(inputListener)
        val current = activeInjector
        activeInjector = null
        if (current != null) {
            cleanupRuntime(current)
        }
        mappingRuntime.shutdown(null)
        backendExecutor.shutdownNow()
        runtimeScope.cancel()
        if (currentInstance === this) currentInstance = null
        super.onDestroy()
    }

    private fun prepareRuntimeAsync() {
        if (activeInjector != null || runtimePreparing || backendExecutor.isShutdown) return
        runtimePreparing = true
        backendExecutor.execute {
            try {
                val expectedState = MappingRuntimeBridge.state.value
                val config = MappingRuntimeBridge.config.value
                if (!expectedState.armed || !expectedState.targetForeground || config == null) return@execute

                if (Build.VERSION.SDK_INT < 34 && config.buttons.any { it.type in setOf(com.example.model.NodeType.JOYSTICK_ZONE,com.example.model.NodeType.CAMERA_DRAG) }) {
                    MappingRuntimeBridge.reportError("Android 14 or newer is required for global stick motion capture. Button-only profiles can use older Android versions.")
                    return@execute
                }
                val probes = PrivilegeDetector(this).probeAll()
                val requiresPersistent = mappingRuntime.requiresPersistentTouch(config)
                val order = PrivilegeBackendSelector.order(config.preferredBackend)

                val failures = mutableListOf<String>()
                for (method in order) {
                    val probe = probes.firstOrNull { it.method == method } ?: continue
                    if (!probe.isDetected || method == PrivilegeMethod.APATCH) {
                        failures += "$method unavailable: ${probe.state}"
                        continue
                    }
                    if (method == PrivilegeMethod.ACCESSIBILITY && requiresPersistent) {
                        failures += "Accessibility cannot satisfy persistent-touch requirements"
                        continue
                    }

                    val candidate = try {
                        InputInjectorFactory.createInjector(method)
                    } catch (t: Throwable) {
                        failures += "$method creation failed: ${t.javaClass.simpleName}: ${t.message}"
                        continue
                    }

                    val prepared = try { candidate.prepare() } catch(error: Exception) {
                        failures += "$method prepare threw: ${error.javaClass.simpleName}: ${error.message}"
                        false
                    }
                    if (!prepared) {
                        failures += "$method prepare failed"
                        if (!cleanupCandidate(candidate)) return@execute
                        continue
                    }

                    val liveState = MappingRuntimeBridge.state.value
                    if (!liveState.armed || !liveState.targetForeground || liveState.sessionId != expectedState.sessionId) {
                        cleanupCandidate(candidate)
                        return@execute
                    }

                    activeInjector?.let(::cleanupRuntime)
                    activeInjector = candidate
                    MappingRuntimeBridge.setBackend(method, true, null,
                        failures.takeIf { it.isNotEmpty() }?.joinToString("; ")?.let { "Auto selected $method after: $it" })
                    Log.i(TAG, "Mapper backend ready: $method for ${liveState.gamePackage}")
                    return@execute
                }

                val availableSummary = probes.joinToString { "${it.method}=${it.state}" }
                val detail = buildString {
                    append("No compatible injection backend could be prepared. ")
                    append(availableSummary)
                    if (failures.isNotEmpty()) append("; ").append(failures.joinToString("; "))
                }
                Log.e(TAG, detail)
                MappingRuntimeBridge.reportError(detail)
            } catch(error: Exception) {
                Log.e(TAG,"Backend preparation failed",error)
                MappingRuntimeBridge.reportError("Backend preparation failed: ${error.javaClass.simpleName}: ${error.message}")
            } finally {
                runtimePreparing = false
            }
        }
    }

    private fun cleanupCandidate(injector: InputInjector): Boolean = try {
        injector.cleanup()
        true
    } catch(error: Exception) {
        Log.e(TAG,"Backend cleanup failed",error)
        MappingRuntimeBridge.disarm("Backend cleanup failed: ${error.message}")
        false
    }

    private fun cleanupRuntime(injector: InputInjector) {
        capturedDevices.clear()
        val released=mappingRuntime.releaseAll(injector)
        if(!released) MappingRuntimeBridge.reportError("Backend contact release could not be confirmed")
        runCatching { injector.cleanup() }.onFailure {
            Log.e(TAG,"Backend cleanup failed",it)
            MappingRuntimeBridge.reportError("Backend cleanup failed: ${it.message}")
        }
    }

    private fun teardownInjectorAsync() {
        if (backendExecutor.isShutdown) return
        val current = activeInjector ?: return
        activeInjector = null
        backendExecutor.execute {
            cleanupRuntime(current)
        }
    }

    private fun reconnectForGeometryChange() {
        if (backendExecutor.isShutdown) return
        val current = activeInjector
        activeInjector = null
        val method = current?.method
        if (method != null) {
            MappingRuntimeBridge.setBackend(method, false, null)
        }
        backendExecutor.execute {
            if (current != null) {
                cleanupRuntime(current)
            }
            runtimePreparing = false
            val state = MappingRuntimeBridge.state.value
            if (state.armed && state.targetForeground) prepareRuntimeAsync()
        }
    }

    fun emergencyRelease(onComplete: (Boolean) -> Unit) {
        val injector = activeInjector
        activeInjector = null
        MappingRuntimeBridge.disarm("Emergency stop requested")
        if (backendExecutor.isShutdown) { onComplete(injector == null); return }
        backendExecutor.execute {
            var released = true
            if (injector != null) {
                released = mappingRuntime.releaseAll(injector)
                runCatching { injector.cleanup() }.onFailure {
                    Log.e(TAG, "Backend cleanup failed during panic", it)
                    released = false
                }
            }
            onComplete(released)
        }
    }

    fun captureScreenshot(onResult: (Result<android.graphics.Bitmap>) -> Unit) {
        if (Build.VERSION.SDK_INT < 30) {
            onResult(Result.failure(IllegalStateException("Screenshot capture requires Android 11 or later; import an image instead.")))
            return
        }
        try {
            takeScreenshot(android.view.Display.DEFAULT_DISPLAY, mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val buffer = result.hardwareBuffer
                        val bitmap = runCatching {
                            val hardware = android.graphics.Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                                ?: error("Screenshot buffer could not be decoded")
                            try { hardware.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                                ?: error("Screenshot copy failed") } finally { hardware.recycle() }
                        }
                        buffer.close()
                        onResult(bitmap)
                    }
                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "Screenshot capture failed: code=$errorCode (secure windows cannot be captured)")
                        onResult(Result.failure(IllegalStateException("Android rejected screenshot capture (code=$errorCode); protected content cannot be captured.")))
                    }
                })
        } catch (error: Exception) { onResult(Result.failure(error)) }
    }

    fun performTap(x: Float, y: Float, durationMs: Long = 50L): Boolean {
        if (!x.isFinite() || !y.isFinite()) return false
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, mainHandler)
    }

    fun performDrag(points: List<PointF>, durationMs: Long): Boolean {
        if (points.size < 2 || durationMs <= 0L || points.any { !it.x.isFinite() || !it.y.isFinite() }) return false
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, mainHandler)
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int>? {
        val wm = getSystemService(WindowManager::class.java) ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            if (bounds.width() > 0 && bounds.height() > 0) bounds.width() to bounds.height() else null
        } else {
            val metrics = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(metrics)
            if (metrics.widthPixels > 0 && metrics.heightPixels > 0) metrics.widthPixels to metrics.heightPixels else null
        }
    }

    private fun isControllerSource(source: Int): Boolean {
        val gamepad = source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
        val joystick = source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        val dpad = source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
        return gamepad || joystick || dpad
    }

    private fun isTransientSystemPackage(pkg: String): Boolean = pkg in setOf(
        "android",
        "com.android.systemui",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller"
    )

    companion object {
        private const val TAG = "NexusAccessibility"

        @Volatile
        private var currentInstance: ControlystAccessibilityService? = null

        fun getInstance(): ControlystAccessibilityService? = currentInstance

        fun isServiceRunning(): Boolean = currentInstance != null
    }
}
