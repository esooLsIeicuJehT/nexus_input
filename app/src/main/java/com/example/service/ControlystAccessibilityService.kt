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
    private val gestures = com.example.input.GestureLedger()
    fun awaitGestureIdle(): Boolean = gestures.awaitIdle(200)
    val pendingGestureCount: Int get() = gestures.count

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

    private val failedCleanup = java.util.concurrent.ConcurrentHashMap.newKeySet<InputInjector>()

    @Volatile
    private var runtimePreparing = false

    private var pendingForegroundTeardown: Runnable? = null

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
                    cancelPendingForegroundTeardown()
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
                    cancelPendingForegroundTeardown()
                    prepareRuntimeAsync()
                } else if (refreshed.armed) {
                    scheduleForegroundTeardown(refreshed.sessionId)
                } else {
                    cancelPendingForegroundTeardown()
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
        // Android can deliver several configuration changes while a landscape game is
        // relaunching. Tearing down here destroys the live /dev/uinput touchscreen and
        // stops the libsu RootService mid-transition. Keep the prepared backend for the
        // armed session; normalized mapping coordinates continue to use current metrics.
        Log.i(TAG, "Configuration changed; preserving active mapper backend for session " +
            MappingRuntimeBridge.state.value.sessionId)
    }

    override fun onInterrupt() {
        Log.w(TAG, "NEXUS accessibility service interrupted")
        MappingRuntimeBridge.disarm("Accessibility capture interrupted; mapper disarmed")
        teardownInjectorAsync()
    }

    override fun onDestroy() {
        getSystemService(android.hardware.input.InputManager::class.java).unregisterInputDeviceListener(inputListener)
        MappingRuntimeBridge.disarm("Accessibility capture disconnected; mapper disarmed")
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
        if(failedCleanup.isNotEmpty()) {
            MappingRuntimeBridge.disarm("Previous backend release is unconfirmed. Use panic to retry cleanup before restarting mapping.")
            return
        }
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
                    if (!liveState.armed || liveState.sessionId != expectedState.sessionId) {
                        cleanupCandidate(candidate)
                        return@execute
                    }

                    // Backend preparation can take several seconds on a cold libsu/KernelSU
                    // launch. During that time SplashActivity -> GameActivity and rotation
                    // can transiently make targetForeground false. Do not destroy a
                    // successfully prepared uinput backend for the still-armed session.
                    activeInjector?.let(::cleanupRuntime)
                    activeInjector = candidate
                    val notices = listOfNotNull(
                        failures.takeIf { it.isNotEmpty() }?.joinToString("; ")?.let { "Auto selected $method after: $it" },
                        candidate.readinessDetails(),
                        if (!liveState.targetForeground) "Backend prepared; waiting for target game foreground" else null
                    )
                    MappingRuntimeBridge.setBackend(method, true, null,
                        notices.takeIf { it.isNotEmpty() }?.joinToString("; "))
                    if (liveState.targetForeground) {
                        cancelPendingForegroundTeardown()
                    } else {
                        scheduleForegroundTeardown(liveState.sessionId)
                    }
                    Log.i(TAG, "Mapper backend ready: $method for ${liveState.gamePackage}; targetForeground=${liveState.targetForeground}")
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
        failedCleanup.remove(injector)
        true
    } catch(error: Exception) {
        failedCleanup.add(injector)
        Log.e(TAG,"Backend cleanup failed",error)
        MappingRuntimeBridge.disarm("Backend cleanup failed: ${error.message}")
        false
    }

    private fun cleanupRuntime(injector: InputInjector): Boolean {
        capturedDevices.clear()
        val released=mappingRuntime.releaseAll(injector)
        val cleaned=cleanupCandidate(injector)
        if(released && cleaned) { failedCleanup.remove(injector);return true }
        failedCleanup.add(injector)
        MappingRuntimeBridge.disarm("Backend contact release or cleanup could not be confirmed. Panic can retry the retained backend.")
        return false
    }

    private fun cancelPendingForegroundTeardown() {
        pendingForegroundTeardown?.let(mainHandler::removeCallbacks)
        pendingForegroundTeardown = null
    }

    private fun scheduleForegroundTeardown(sessionId: Long) {
        if (pendingForegroundTeardown != null) return
        val task = Runnable {
            pendingForegroundTeardown = null
            val state = MappingRuntimeBridge.state.value
            if (state.armed && !state.targetForeground && state.sessionId == sessionId) {
                Log.i(TAG, "Target left foreground beyond grace window; releasing mapper backend for session $sessionId")
                teardownInjectorAsync()
            }
        }
        pendingForegroundTeardown = task
        mainHandler.postDelayed(task, FOREGROUND_EXIT_GRACE_MS)
    }

    private fun teardownInjectorAsync() {
        if (backendExecutor.isShutdown) return
        val current = activeInjector ?: return
        activeInjector = null
        MappingRuntimeBridge.clearBackendReady()
        backendExecutor.execute {
            cleanupRuntime(current)
        }
    }

    fun currentFrameSource(): InputInjector? = activeInjector

    fun emergencyRelease(onComplete: (Boolean) -> Unit) {
        val injector = activeInjector
        activeInjector = null
        MappingRuntimeBridge.disarm("Emergency stop requested")
        if (backendExecutor.isShutdown) {
            MappingRuntimeBridge.disarm("Emergency contact release cannot be confirmed: backend executor is closed")
            onComplete(false)
            return
        }
        backendExecutor.execute {
            // Teardowns queued before panic complete first; retained failures must also be retried.
            val targets=failedCleanup.toMutableSet().apply { if(injector!=null) add(injector) }
            var released=true
            targets.forEach { if(!cleanupRuntime(it)) released=false }
            if(!gestures.awaitIdle(200)) released=false
            onComplete(released && failedCleanup.isEmpty())
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
        return dispatchObservedGesture(gesture)
    }

    fun performDrag(points: List<PointF>, durationMs: Long): Boolean {
        if (points.size < 2 || durationMs <= 0L || points.any { !it.x.isFinite() || !it.y.isFinite() }) return false
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchObservedGesture(gesture)
    }

    private fun dispatchObservedGesture(gesture: GestureDescription): Boolean {
        val token=gestures.begin()
        val session=MappingRuntimeBridge.state.value.sessionId
        val callback=object : GestureResultCallback() {
            override fun onCompleted(description: GestureDescription?) { gestures.complete(token) }
            override fun onCancelled(description: GestureDescription?) {
                gestures.complete(token)
                Log.e(TAG,"Android cancelled an Accessibility gesture")
                val state=MappingRuntimeBridge.state.value
                if(state.sessionId==session) {
                    if(state.armed) { MappingRuntimeBridge.disarm("Android cancelled the Accessibility gesture; mapping stopped");teardownInjectorAsync() }
                    else MappingRuntimeBridge.reportError("Android cancelled the Accessibility gesture")
                }
            }
        }
        return try {
            dispatchGesture(gesture,callback,mainHandler).also { accepted -> if(!accepted) gestures.complete(token) }
        } catch(error:Exception) {
            gestures.complete(token);Log.e(TAG,"Accessibility gesture dispatch failed",error);false
        }
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
        packageName,
        "android",
        "com.android.systemui",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller"
    )

    companion object {
        private const val TAG = "NexusAccessibility"
        private const val FOREGROUND_EXIT_GRACE_MS = 8_000L

        @Volatile
        private var currentInstance: ControlystAccessibilityService? = null

        fun getInstance(): ControlystAccessibilityService? = currentInstance

        fun isServiceRunning(): Boolean = currentInstance != null
    }
}
