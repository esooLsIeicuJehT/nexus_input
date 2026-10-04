package com.inputmapper.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.inputmapper.platform.core.AvailabilityState
import com.inputmapper.platform.core.BackendKind
import com.inputmapper.platform.core.FactoryResult
import com.inputmapper.platform.core.InjectionResult
import com.inputmapper.platform.core.InjectorFactory
import com.inputmapper.platform.core.InputInjector
import com.inputmapper.platform.debug.GameProfileValidator
import com.inputmapper.platform.game.ControllerButtonBinding
import com.inputmapper.platform.game.ControllerNormalizer
import com.inputmapper.platform.game.GameProfile
import com.inputmapper.platform.game.GameProfileStore
import com.inputmapper.platform.game.StickActionType
import com.inputmapper.platform.game.StickMapping
import com.inputmapper.platform.game.TouchActionType
import com.inputmapper.platform.game.TouchMapping
import com.inputmapper.platform.mapper.ControllerAxisSample
import com.inputmapper.platform.mapper.ControllerCaptureBus
import com.inputmapper.platform.mapper.ControllerCaptureControl
import com.inputmapper.platform.mapper.ControllerKeySample
import com.inputmapper.platform.privilege.PrivilegeDetector
import com.inputmapper.platform.profile.ControllerProfileStore
import com.inputmapper.platform.ui.NexusUi
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.hypot

/**
 * Accessibility-backed global controller capture and persistent mapping runtime.
 *
 * High-rate injection is never routed through WebUI/shell text commands. Controller events are
 * normalized here and sent to the already-verified InputInjector backend over Binder/JNI.
 */
class MapperAccessibilityService : AccessibilityService() {
    private val injectionExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val profileStore by lazy { GameProfileStore(this) }
    private val controllerProfiles by lazy { ControllerProfileStore(this) }
    private val windowManager by lazy { getSystemService(WindowManager::class.java) }

    @Volatile private var injector: InputInjector? = null
    @Volatile private var backendName: String = "disconnected"
    @Volatile private var activeBackendKind: BackendKind? = null
    @Volatile private var backendRecoveryScheduled = false
    @Volatile private var runtimeProfile: GameProfile? = null
    @Volatile private var runtimeReady = false
    @Volatile private var backendConnecting = false
    @Volatile private var injectorWidth = 0
    @Volatile private var injectorHeight = 0
    @Volatile private var lastForegroundPackage: String? = null
    @Volatile private var pendingActivationProfileId: String? = null
    @Volatile private var activationGeneration: Long = 0L
    private var normalizer = ControllerNormalizer(null)

    private val activeHoldSlots = mutableSetOf<Int>()
    private val activeTapSlots = mutableSetOf<Int>()
    private val activeStickSlots = mutableSetOf<Int>()
    private val cameraPositions = mutableMapOf<Int, Pair<Float, Float>>()
    private val syntheticDpadPressed = mutableSetOf<Int>()

    private var editor: OverlayEditor? = null
    private var quickBubble: QuickBubble? = null
    private var lastRuntimeError: String? = null

    override fun onServiceConnected() {
        current = this
        createNotificationChannel()
        applyControllerCaptureMode()
        if (profileStore.isMappingEnabled()) {
            publishMapperNotification("Armed • ${profileStore.activeProfile()?.displayName ?: "profile"} • waiting for mapped game")
            // Restore foreground knowledge after AccessibilityService process/service recreation.
            // This is read-only and does not create an injector until the normal startup grace passes.
            mainHandler.postDelayed({
                refreshForegroundPackageFromRootWindow()?.let { pkg ->
                    profileStore.findByPackage(pkg)?.let { scheduleStableActivation(it, "service restore") }
                }
            }, 500L)
        }
    }

    fun setControllerCaptureEnabled(enabled: Boolean, consumeKeys: Boolean) {
        ControllerCaptureControl.set(enabled, consumeKeys)
        applyControllerCaptureMode()
    }

    fun enableMapping(profileId: String, assumeTargetForeground: Boolean = false): String {
        val profile = profileStore.load(profileId) ?: return "Profile not found: $profileId"
        val validation = GameProfileValidator.validate(profile)
        if (validation.isNotEmpty()) {
            return "Profile cannot start: ${validation.joinToString("; ")}"
        }
        profileStore.setActiveProfile(profile.profileId)
        profileStore.setMappingEnabled(true)
        removeQuickBubble()

        val visiblePackage = lastForegroundPackage ?: refreshForegroundPackageFromRootWindow()
        val targetIsForeground = assumeTargetForeground || visiblePackage == profile.packageName
        if (targetIsForeground) {
            scheduleStableActivation(profile, "manual activation")
            return "Armed ${profile.displayName}; waiting briefly for the game to settle before mapper activation"
        }

        runtimeProfile = null
        runtimeReady = false
        applyControllerCaptureMode()
        publishMapperNotification("Armed for ${profile.displayName}")
        return "Armed ${profile.displayName}; it activates when ${profile.packageName} is foreground"
    }

    fun disableMapping(): String {
        profileStore.setMappingEnabled(false)
        cancelPendingActivation()
        runtimeProfile = null
        runtimeReady = false
        releaseAllTouches()
        applyControllerCaptureMode()
        injector?.let { currentInjector -> injectionExecutor.execute { currentInjector.cleanup() } }
        injector = null
        activeBackendKind = null
        backendRecoveryScheduled = false
        syntheticDpadPressed.clear()
        backendName = "disconnected"
        getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        removeQuickBubble()
        injectorWidth = 0
        injectorHeight = 0
        return "Mapper disabled"
    }

    fun runtimeStatus(): String {
        val profile = runtimeProfile
        return if (!profileStore.isMappingEnabled()) {
            "OFF"
        } else if (profile == null) {
            "ARMED • ${profileStore.activeProfile()?.displayName ?: "profile"} • waiting for target app"
        } else {
            "${if (runtimeReady) "ACTIVE" else "CONNECTING"} • ${profile.displayName} • $backendName"
        }
    }

    fun showOverlayEditor(profileId: String): String {
        val profile = profileStore.load(profileId) ?: return "Profile not found"
        cancelPendingActivation()
        closeOverlayEditor(save = true)
        removeQuickBubble()
        editor = OverlayEditor(profile).also { it.show() }
        applyControllerCaptureMode()
        return "Overlay editor opened for ${profile.displayName}"
    }

    fun closeOverlayEditor(save: Boolean = true) {
        val local = editor ?: return
        if (save) local.save()
        local.close()
        editor = null
        applyControllerCaptureMode()
    }

    private fun applyControllerCaptureMode() {
        val updated = serviceInfo ?: AccessibilityServiceInfo()
        updated.flags = updated.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        updated.motionEventSources = if (captureWanted()) InputDevice.SOURCE_JOYSTICK else 0
        setServiceInfo(updated)
    }

    private fun captureWanted(): Boolean =
        ControllerCaptureControl.enabled || editor != null || (profileStore.isMappingEnabled() && runtimeProfile != null)

    private fun consumeKeys(): Boolean =
        ControllerCaptureControl.consumeKeys || editor != null || (runtimeReady && runtimeProfile != null)

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!captureWanted() && !ControllerCaptureControl.enabled) return false
        val device = event.device ?: return false
        if (!isController(device)) return false

        val sample = ControllerKeySample(
            deviceId = event.deviceId,
            deviceName = device.name ?: "Unnamed controller",
            vendorId = device.vendorId,
            productId = device.productId,
            action = event.action,
            keyCode = event.keyCode,
            scanCode = event.scanCode,
            eventTime = event.eventTime
        )
        ControllerCaptureBus.publishKey(sample)

        val localEditor = editor
        if (localEditor != null) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && localEditor.captureButton(sample)) return true
            return true
        }

        val profile = runtimeProfile
        val activeInjector = injector
        if (profile != null && runtimeReady && activeInjector != null) {
            val mapping = profile.touchMappings.firstOrNull { it.input.matches(event.keyCode, event.scanCode) }
            if (mapping != null) {
                handleTouchMapping(activeInjector, mapping, event.action, event.repeatCount)
                return true
            }
        }
        return consumeKeys()
    }

    override fun onMotionEvent(event: MotionEvent) {
        if (!captureWanted()) return
        if (event.action != MotionEvent.ACTION_MOVE) return
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) return
        val device = event.device ?: return

        val values = device.motionRanges
            .asSequence()
            .filter { it.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK }
            .associate { range -> range.axis to event.getAxisValue(range.axis) }

        val sample = ControllerAxisSample(
            deviceId = event.deviceId,
            deviceName = device.name ?: "Unnamed controller",
            vendorId = device.vendorId,
            productId = device.productId,
            values = values,
            eventTime = event.eventTime
        )
        ControllerCaptureBus.publishAxes(sample)
        synthesizeDpadFromHat(sample)

        val profile = runtimeProfile
        val activeInjector = injector
        if (profile != null && runtimeReady && activeInjector != null && profile.stickMappings.isNotEmpty()) {
            injectionExecutor.execute { handleStickMappings(activeInjector, profile, sample) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return

        val transientSystemPackage = isTransientSystemPackage(pkg)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != packageName && !transientSystemPackage) {
            lastForegroundPackage = pkg
            if (pendingActivationProfileId != null && profileStore.findByPackage(pkg)?.profileId != pendingActivationProfileId) {
                cancelPendingActivation()
            }
        }

        // While editing we only observe the foreground package. Runtime injection stays off.
        if (editor != null || !profileStore.isMappingEnabled()) return
        if (pkg == packageName || transientSystemPackage) return

        val match = profileStore.findByPackage(pkg)
        val currentProfile = runtimeProfile
        if (match == null) {
            if (currentProfile != null && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                deactivateRuntimeProfile("Armed • waiting for mapped game")
            }
            return
        }
        if (currentProfile?.profileId != match.profileId && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            scheduleStableActivation(match, "foreground package")
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (runtimeProfile != null && profileStore.isMappingEnabled()) {
            ensureInjectorAsync(forceGeometry = true)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        closeOverlayEditor(save = true)
        removeQuickBubble()
        ControllerCaptureControl.set(false, false)
        releaseAllTouches()
        injector?.cleanup()
        injector = null
        injectionExecutor.shutdownNow()
        if (current === this) current = null
        super.onDestroy()
    }

    private fun handleTouchMapping(active: InputInjector, mapping: TouchMapping, action: Int, repeatCount: Int) {
        val (x, y) = screenPoint(mapping.xNorm, mapping.yNorm)
        when (mapping.action) {
            TouchActionType.TAP -> {
                if (action != KeyEvent.ACTION_DOWN || repeatCount != 0) return
                synchronized(activeTapSlots) {
                    if (!activeTapSlots.add(mapping.slot)) return
                }
                injectionExecutor.execute {
                    val down = active.beginTouch(mapping.slot, x, y)
                    if (down !is InjectionResult.Success) {
                        synchronized(activeTapSlots) { activeTapSlots.remove(mapping.slot) }
                        reportRuntimeFailure("tap down ${mapping.label}", down)
                        return@execute
                    }
                    injectionExecutor.schedule({
                        active.endTouch(mapping.slot)
                        synchronized(activeTapSlots) { activeTapSlots.remove(mapping.slot) }
                    }, 35L, TimeUnit.MILLISECONDS)
                }
            }
            TouchActionType.HOLD -> {
                if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) {
                    injectionExecutor.execute {
                        if (activeHoldSlots.add(mapping.slot)) {
                            val result = active.beginTouch(mapping.slot, x, y)
                            if (result !is InjectionResult.Success) {
                                activeHoldSlots.remove(mapping.slot)
                                reportRuntimeFailure("hold down ${mapping.label}", result)
                            }
                        }
                    }
                } else if (action == KeyEvent.ACTION_UP) {
                    injectionExecutor.execute {
                        if (activeHoldSlots.remove(mapping.slot)) active.endTouch(mapping.slot)
                    }
                }
            }
        }
    }

    private fun handleStickMappings(active: InputInjector, profile: GameProfile, sample: ControllerAxisSample) {
        val bounds = windowManager.currentWindowMetrics.bounds
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        val minDimension = minOf(width, height)

        profile.stickMappings.forEach { mapping ->
            val rawX = sample.values[mapping.axisX] ?: return@forEach
            val rawY = sample.values[mapping.axisY] ?: return@forEach
            var x = normalizer.normalize(mapping.axisX, rawX, mapping.deadzone)
            var y = normalizer.normalize(mapping.axisY, rawY, mapping.deadzone)
            if (mapping.invertY) y = -y
            val mag = hypot(x.toDouble(), y.toDouble()).toFloat()
            if (mag > 1f) {
                x /= mag
                y /= mag
            }
            val anchorX = mapping.xNorm * width
            val anchorY = mapping.yNorm * height
            val radius = mapping.radiusNorm * minDimension

            if (x == 0f && y == 0f) {
                if (activeStickSlots.remove(mapping.slot)) {
                    active.endTouch(mapping.slot)
                    cameraPositions.remove(mapping.slot)
                }
                return@forEach
            }

            when (mapping.action) {
                StickActionType.VIRTUAL_JOYSTICK -> {
                    if (activeStickSlots.add(mapping.slot)) {
                        val down = active.beginTouch(mapping.slot, anchorX, anchorY)
                        if (down !is InjectionResult.Success) {
                            activeStickSlots.remove(mapping.slot)
                            reportRuntimeFailure("left-stick down", down)
                            return@forEach
                        }
                    }
                    val tx = (anchorX + x * radius * mapping.sensitivity).coerceIn(0f, width - 1f)
                    val ty = (anchorY + y * radius * mapping.sensitivity).coerceIn(0f, height - 1f)
                    val move = active.moveTouch(mapping.slot, tx, ty)
                    if (move !is InjectionResult.Success) reportRuntimeFailure("left-stick move", move)
                }
                StickActionType.CAMERA_DRAG -> {
                    if (activeStickSlots.add(mapping.slot)) {
                        val down = active.beginTouch(mapping.slot, anchorX, anchorY)
                        if (down !is InjectionResult.Success) {
                            activeStickSlots.remove(mapping.slot)
                            reportRuntimeFailure("camera down", down)
                            return@forEach
                        }
                        cameraPositions[mapping.slot] = anchorX to anchorY
                    }
                    val current = cameraPositions[mapping.slot] ?: (anchorX to anchorY)
                    val step = 22f * mapping.sensitivity
                    var nextX = current.first + x * step
                    var nextY = current.second + y * step
                    val out = kotlin.math.abs(nextX - anchorX) > radius || kotlin.math.abs(nextY - anchorY) > radius
                    if (out) {
                        active.endTouch(mapping.slot)
                        active.beginTouch(mapping.slot, anchorX, anchorY)
                        nextX = anchorX + x * step
                        nextY = anchorY + y * step
                    }
                    nextX = nextX.coerceIn(0f, width - 1f)
                    nextY = nextY.coerceIn(0f, height - 1f)
                    val move = active.moveTouch(mapping.slot, nextX, nextY)
                    if (move !is InjectionResult.Success) reportRuntimeFailure("camera move", move)
                    cameraPositions[mapping.slot] = nextX to nextY
                }
            }
        }
    }

    private fun ensureInjectorAsync(
        forceGeometry: Boolean = false,
        excludedKinds: Set<BackendKind> = emptySet()
    ) {
        val boundsSnapshot = windowManager.currentWindowMetrics.bounds
        val targetWidth = boundsSnapshot.width()
        val targetHeight = boundsSnapshot.height()
        val geometryMatches = injector != null && injectorWidth == targetWidth && injectorHeight == targetHeight
        if (geometryMatches && excludedKinds.isEmpty() && !forceGeometry) {
            runtimeReady = runtimeProfile != null
            return
        }
        if (backendConnecting) return
        backendConnecting = true
        Thread {
            if (injector != null && (!geometryMatches || activeBackendKind in excludedKinds)) {
                val old = injector
                if (old != null) releaseAllTouchesNow(old)
                runCatching { old?.cleanup() }
                injector = null
                activeBackendKind = null
                injectorWidth = 0
                injectorHeight = 0
            }

            val availability = PrivilegeDetector(this).detectAll()
            val forced = runtimeProfile?.preferredBackend?.let { name ->
                runCatching { BackendKind.valueOf(name) }.getOrNull()
            }
            val orderedKinds = if (forced != null) {
                listOf(forced)
            } else {
                // Compatibility-first for games: framework injection does not hot-add an input
                // device. If Shizuku is unauthorized or its UserService fails, continue to the
                // already-verified KernelSU native backend rather than leaving the mapper dead.
                listOf(BackendKind.SHIZUKU, BackendKind.KERNEL_SU, BackendKind.MAGISK, BackendKind.ACCESSIBILITY)
            }
            val candidates = orderedKinds
                .filterNot { it in excludedKinds }
                .mapNotNull { kind -> availability.firstOrNull { it.kind == kind } }
                .filter { it.state == AvailabilityState.AVAILABLE }

            if (candidates.isEmpty()) {
                val forcedText = forced?.let { " for forced backend $it" } ?: ""
                backendName = "no available backend$forcedText"
                backendConnecting = false
                runtimeReady = false
                applyControllerCaptureMode()
                publishMapperNotification("No injection backend available$forcedText")
                return@Thread
            }

            val profileForCapability = runtimeProfile ?: profileStore.activeProfile()
            val liveBounds = windowManager.currentWindowMetrics.bounds
            val width = liveBounds.width()
            val height = liveBounds.height()
            val failures = mutableListOf<String>()
            var connected = false

            for (candidate in candidates) {
                if (candidate.kind == BackendKind.ACCESSIBILITY &&
                    profileForCapability != null &&
                    (profileForCapability.stickMappings.isNotEmpty() || profileForCapability.touchMappings.any { it.action == TouchActionType.HOLD })
                ) {
                    failures += "Accessibility: persistent multi-touch required by profile"
                    if (forced != null) break
                    continue
                }

                when (val created = InjectorFactory(this).create(candidate.kind, width, height)) {
                    is FactoryResult.Ready -> {
                        injector?.cleanup()
                        injector = created.injector
                        activeBackendKind = candidate.kind
                        injectorWidth = width
                        injectorHeight = height
                        backendName = created.injector.backendName
                        runtimeReady = runtimeProfile != null
                        backendRecoveryScheduled = false
                        lastRuntimeError = null
                        connected = true
                        val profileId = runtimeProfile?.profileId
                        mainHandler.postDelayed({
                            if (profileStore.isMappingEnabled() && runtimeReady && runtimeProfile?.profileId == profileId && lastForegroundPackage == runtimeProfile?.packageName) {
                                showQuickBubble()
                            }
                        }, BUBBLE_DELAY_MS)
                        break
                    }
                    is FactoryResult.Error -> {
                        failures += "${candidate.kind}: ${created.message}"
                        if (forced != null) break
                    }
                }
            }

            if (!connected) {
                activeBackendKind = null
                backendName = failures.joinToString(" | ").ifBlank { "all candidate backends failed" }
                runtimeReady = false
            }
            backendConnecting = false
            applyControllerCaptureMode()
            runtimeProfile?.let {
                publishMapperNotification("${if (runtimeReady) "Active" else "Unavailable"} • ${it.displayName} • $backendName")
            }
        }.start()
    }

    private fun activateProfile(profile: GameProfile, reason: String) {
        pendingActivationProfileId = null
        runtimeProfile = profile
        runtimeReady = false
        normalizer = ControllerNormalizer(findControllerCalibration(profile))
        profileStore.setActiveProfile(profile.profileId)
        removeQuickBubble()
        applyControllerCaptureMode()
        ensureInjectorAsync(forceGeometry = true)
        publishMapperNotification("Connecting • ${profile.displayName} • $reason")
    }

    private fun scheduleStableActivation(profile: GameProfile, reason: String) {
        if (runtimeProfile?.profileId == profile.profileId && runtimeReady) return
        if (pendingActivationProfileId == profile.profileId) return
        pendingActivationProfileId = profile.profileId
        val generation = ++activationGeneration
        runtimeProfile = null
        runtimeReady = false
        removeQuickBubble()
        applyControllerCaptureMode()
        publishMapperNotification("Waiting for ${profile.displayName} startup • mapper not injecting yet")
        mainHandler.postDelayed({
            if (generation != activationGeneration) return@postDelayed
            if (!profileStore.isMappingEnabled() || editor != null) return@postDelayed
            if (lastForegroundPackage != profile.packageName) {
                pendingActivationProfileId = null
                publishMapperNotification("Armed • ${profile.displayName} • waiting for mapped game")
                return@postDelayed
            }
            activateProfile(profile, "$reason after startup grace")
        }, GAME_STARTUP_GRACE_MS)
    }

    private fun cancelPendingActivation() {
        activationGeneration++
        pendingActivationProfileId = null
    }

    private fun refreshForegroundPackageFromRootWindow(): String? {
        val pkg = runCatching { rootInActiveWindow?.packageName?.toString() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it != packageName && !isTransientSystemPackage(it) }
        if (pkg != null) lastForegroundPackage = pkg
        return pkg
    }

    private fun isTransientSystemPackage(pkg: String): Boolean =
        pkg == "android" || pkg == "com.android.systemui" || pkg.startsWith("com.google.android.inputmethod")

    private fun deactivateRuntimeProfile(message: String) {
        runtimeProfile = null
        runtimeReady = false
        releaseAllTouches()
        val old = injector
        injector = null
        activeBackendKind = null
        backendRecoveryScheduled = false
        syntheticDpadPressed.clear()
        injectorWidth = 0
        injectorHeight = 0
        if (old != null) injectionExecutor.execute { old.cleanup() }
        applyControllerCaptureMode()
        publishMapperNotification(message)
    }

    private fun synthesizeDpadFromHat(sample: ControllerAxisSample) {
        val x = sample.values[MotionEvent.AXIS_HAT_X] ?: 0f
        val y = sample.values[MotionEvent.AXIS_HAT_Y] ?: 0f
        val wanted = linkedSetOf<Int>()
        if (x <= -0.5f) wanted += KeyEvent.KEYCODE_DPAD_LEFT
        if (x >= 0.5f) wanted += KeyEvent.KEYCODE_DPAD_RIGHT
        if (y <= -0.5f) wanted += KeyEvent.KEYCODE_DPAD_UP
        if (y >= 0.5f) wanted += KeyEvent.KEYCODE_DPAD_DOWN

        val releases = syntheticDpadPressed.filter { it !in wanted }
        val presses = wanted.filter { it !in syntheticDpadPressed }
        releases.forEach { dispatchSyntheticDpad(sample, it, KeyEvent.ACTION_UP) }
        presses.forEach { dispatchSyntheticDpad(sample, it, KeyEvent.ACTION_DOWN) }
        syntheticDpadPressed.clear()
        syntheticDpadPressed.addAll(wanted)
    }

    private fun dispatchSyntheticDpad(sample: ControllerAxisSample, keyCode: Int, action: Int) {
        val keySample = ControllerKeySample(
            deviceId = sample.deviceId,
            deviceName = sample.deviceName,
            vendorId = sample.vendorId,
            productId = sample.productId,
            action = action,
            keyCode = keyCode,
            scanCode = 0,
            eventTime = sample.eventTime
        )
        ControllerCaptureBus.publishKey(keySample)

        val localEditor = editor
        if (localEditor != null) {
            if (action == KeyEvent.ACTION_DOWN) localEditor.captureButton(keySample)
            return
        }

        val profile = runtimeProfile
        val active = injector
        if (profile != null && runtimeReady && active != null) {
            profile.touchMappings.firstOrNull { it.input.matches(keyCode, 0) }?.let { mapping ->
                handleTouchMapping(active, mapping, action, 0)
            }
        }
    }

    private fun reportRuntimeFailure(operation: String, result: InjectionResult) {
        val failure = result as? InjectionResult.Failure ?: return
        val signature = "$operation:${failure.code}:${failure.message}"
        if (signature == lastRuntimeError) return
        lastRuntimeError = signature
        runtimeReady = false
        publishMapperNotification("Mapper error • $operation • ${failure.code}: ${failure.message}")

        val profile = runtimeProfile ?: return
        val failedKind = activeBackendKind ?: return
        if (profile.preferredBackend != null || backendRecoveryScheduled || failedKind == BackendKind.ACCESSIBILITY) return

        // Auto mode is allowed to recover to another already-detected backend. A forced backend is
        // never silently replaced. This path is low-frequency and only runs after a real failure.
        backendRecoveryScheduled = true
        val failedInjector = injector
        injector = null
        activeBackendKind = null
        injectorWidth = 0
        injectorHeight = 0
        Thread {
            runCatching { failedInjector?.cleanup() }
            mainHandler.postDelayed({
                backendRecoveryScheduled = false
                if (profileStore.isMappingEnabled() && runtimeProfile?.profileId == profile.profileId && lastForegroundPackage == profile.packageName) {
                    publishMapperNotification("Recovering from $failedKind failure • trying alternate backend")
                    ensureInjectorAsync(forceGeometry = true, excludedKinds = setOf(failedKind))
                }
            }, 300L)
        }.start()
    }

    private fun releaseAllTouchesNow(active: InputInjector) {
        val slots = linkedSetOf<Int>().apply {
            addAll(activeHoldSlots)
            addAll(activeTapSlots)
            addAll(activeStickSlots)
        }.toList().sortedDescending()
        slots.forEach { runCatching { active.endTouch(it) } }
        activeHoldSlots.clear()
        activeTapSlots.clear()
        activeStickSlots.clear()
        cameraPositions.clear()
    }

    private fun releaseAllTouches() {
        val active = injector ?: return
        injectionExecutor.execute {
            val slots = linkedSetOf<Int>().apply {
                addAll(activeHoldSlots)
                addAll(activeTapSlots)
                addAll(activeStickSlots)
            }.toList().sortedDescending()
            slots.forEach { runCatching { active.endTouch(it) } }
            activeHoldSlots.clear()
            activeTapSlots.clear()
            activeStickSlots.clear()
            cameraPositions.clear()
        }
    }

    private fun findControllerCalibration(profile: GameProfile) =
        profile.controllerProfileId?.let(controllerProfiles::load)
            ?: controllerProfiles.list().firstOrNull { saved ->
                InputDevice.getDeviceIds().map { InputDevice.getDevice(it) }.filterNotNull().any { device ->
                    device.vendorId == saved.vendorId && device.productId == saved.productId
                }
            }

    private fun screenPoint(xNorm: Float, yNorm: Float): Pair<Float, Float> {
        val bounds = windowManager.currentWindowMetrics.bounds
        return (xNorm.coerceIn(0f, 1f) * bounds.width()) to (yNorm.coerceIn(0f, 1f) * bounds.height())
    }

    private fun isController(device: InputDevice): Boolean {
        val sources = device.sources
        return sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
    }

    private fun showQuickBubble() {
        if (quickBubble != null) return
        quickBubble = QuickBubble().also { it.show() }
    }

    private fun removeQuickBubble() {
        quickBubble?.close()
        quickBubble = null
    }

    private inner class QuickBubble {
        private var bubbleView: TextView? = null
        private var panelView: View? = null
        private var bubbleParams: WindowManager.LayoutParams? = null

        fun show() {
            if (bubbleView != null) return
            val size = NexusUi.dp(this@MapperAccessibilityService, 48)
            val view = TextView(this@MapperAccessibilityService).apply {
                text = "N"
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(android.graphics.Color.BLACK)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(NexusUi.cyan)
                    setStroke(NexusUi.dp(this@MapperAccessibilityService, 2), android.graphics.Color.WHITE)
                }
                elevation = NexusUi.dp(this@MapperAccessibilityService, 8).toFloat()
            }
            val bounds = windowManager.currentWindowMetrics.bounds
            val params = WindowManager.LayoutParams(
                size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = maxOf(0, bounds.width() - size - NexusUi.dp(this@MapperAccessibilityService, 12))
                y = maxOf(NexusUi.dp(this@MapperAccessibilityService, 120), bounds.height() / 3)
            }
            var downX = 0f
            var downY = 0f
            var startX = 0
            var startY = 0
            var moved = false
            view.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX; downY = event.rawY
                        startX = params.x; startY = params.y; moved = false; true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downX).toInt(); val dy = (event.rawY - downY).toInt()
                        if (kotlin.math.abs(dx) > 6 || kotlin.math.abs(dy) > 6) moved = true
                        params.x = (startX + dx).coerceIn(0, maxOf(0, bounds.width() - size))
                        params.y = (startY + dy).coerceIn(0, maxOf(0, bounds.height() - size))
                        windowManager.updateViewLayout(view, params); true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) togglePanel()
                        true
                    }
                    else -> false
                }
            }
            windowManager.addView(view, params)
            bubbleView = view
            bubbleParams = params
        }

        private fun togglePanel() {
            if (panelView != null) {
                closePanel(); return
            }
            val profile = runtimeProfile ?: profileStore.activeProfile()
            val panel = LinearLayout(this@MapperAccessibilityService).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(NexusUi.dp(this@MapperAccessibilityService, 12), NexusUi.dp(this@MapperAccessibilityService, 12), NexusUi.dp(this@MapperAccessibilityService, 12), NexusUi.dp(this@MapperAccessibilityService, 12))
                background = NexusUi.rounded(this@MapperAccessibilityService, NexusUi.panel, 18, NexusUi.cyan)
                addView(TextView(this@MapperAccessibilityService).apply {
                    text = "NEXUS INPUT\n${profile?.displayName ?: "No active profile"}\n${runtimeStatus()}"
                    textSize = 12f
                    setTextColor(NexusUi.text)
                })
            }
            fun action(label: String, block: () -> Unit) {
                panel.addView(Button(this@MapperAccessibilityService).apply {
                    text = label; isAllCaps = false; setOnClickListener { block() }
                })
            }
            action("Edit Layout") {
                closePanel()
                profile?.let { showOverlayEditor(it.profileId) }
            }
            action("Profiles / Screenshot Mapper") {
                closePanel()
                startActivity(Intent(this@MapperAccessibilityService, com.inputmapper.platform.ui.ProfilesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            action("Open NEXUS") {
                closePanel()
                startActivity(Intent(this@MapperAccessibilityService, com.inputmapper.platform.ui.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            action("Stop Mapper") {
                closePanel(); disableMapping()
            }
            action("Close Menu") { closePanel() }

            val params = WindowManager.LayoutParams(
                NexusUi.dp(this@MapperAccessibilityService, 270),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = NexusUi.dp(this@MapperAccessibilityService, 12)
                y = NexusUi.dp(this@MapperAccessibilityService, 100)
            }
            windowManager.addView(panel, params)
            panelView = panel
        }

        private fun closePanel() {
            panelView?.let { runCatching { windowManager.removeView(it) } }
            panelView = null
        }

        fun close() {
            closePanel()
            bubbleView?.let { runCatching { windowManager.removeView(it) } }
            bubbleView = null
            bubbleParams = null
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "NEXUS mapper", NotificationManager.IMPORTANCE_LOW))
    }

    private fun publishMapperNotification(message: String) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val notification = android.app.Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("NEXUS INPUT")
            .setContentText(message)
            .setOngoing(profileStore.isMappingEnabled())
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
        publishCompanionState(message)
    }

    private fun publishCompanionState(message: String) {
        val active = injector ?: return
        val profile = runtimeProfile ?: profileStore.activeProfile()
        val payload = buildString {
            append("timestamp=").append(System.currentTimeMillis()).append('\n')
            append("mapping_enabled=").append(profileStore.isMappingEnabled()).append('\n')
            append("runtime_ready=").append(runtimeReady).append('\n')
            append("backend=").append(backendName).append('\n')
            append("profile=").append(profile?.displayName ?: "none").append('\n')
            append("package=").append(profile?.packageName ?: "none").append('\n')
            append("status=").append(message.replace('\n', ' ')).append('\n')
        }
        injectionExecutor.execute { active.publishRuntimeState(payload) }
    }

    private inner class OverlayEditor(private var profile: GameProfile) {
        private val markerViews = linkedMapOf<String, Pair<View, WindowManager.LayoutParams>>()
        private var touchMappings = profile.touchMappings.toMutableList()
        private var stickMappings = profile.stickMappings.toMutableList()
        private var toolbar: View? = null
        private var pendingButton = false

        fun show() {
            addToolbar()
            touchMappings.forEach { addTouchMarker(it) }
            stickMappings.forEach { addStickMarker(it) }
            Toast.makeText(this@MapperAccessibilityService, "Edit ${profile.displayName}: drag markers, tap a button marker to switch TAP/HOLD, long-press to delete.", Toast.LENGTH_LONG).show()
        }

        fun captureButton(sample: ControllerKeySample): Boolean {
            if (!pendingButton) return false
            pendingButton = false
            val used = (touchMappings.map { it.slot } + stickMappings.map { it.slot }).toSet()
            val slot = (2..31).firstOrNull { it !in used }
            if (slot == null) {
                Toast.makeText(this@MapperAccessibilityService, "No free touch slots remain.", Toast.LENGTH_LONG).show()
                return true
            }
            val binding = ControllerButtonBinding(sample.keyCode, sample.scanCode)
            val mapping = TouchMapping(
                id = UUID.randomUUID().toString(),
                label = binding.label().removePrefix("KEYCODE_BUTTON_").removePrefix("KEYCODE_"),
                input = binding,
                action = TouchActionType.TAP,
                xNorm = 0.5f,
                yNorm = 0.5f,
                slot = slot
            )
            touchMappings.add(mapping)
            addTouchMarker(mapping)
            save()
            Toast.makeText(this@MapperAccessibilityService, "Added ${binding.label()}", Toast.LENGTH_SHORT).show()
            return true
        }

        fun save() {
            profile = profile.copy(
                savedAtMillis = System.currentTimeMillis(),
                touchMappings = touchMappings.toList(),
                stickMappings = stickMappings.toList()
            )
            profileStore.save(profile)
        }

        fun close() {
            markerViews.values.forEach { (view, _) -> runCatching { windowManager.removeView(view) } }
            markerViews.clear()
            toolbar?.let { runCatching { windowManager.removeView(it) } }
            toolbar = null
            pendingButton = false
        }

        private fun addToolbar() {
            val bar = LinearLayout(this@MapperAccessibilityService).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(NexusUi.dp(this@MapperAccessibilityService, 8), NexusUi.dp(this@MapperAccessibilityService, 6), NexusUi.dp(this@MapperAccessibilityService, 8), NexusUi.dp(this@MapperAccessibilityService, 6))
                background = NexusUi.rounded(this@MapperAccessibilityService, NexusUi.panel, 16, NexusUi.cyan)
            }
            fun add(label: String, action: () -> Unit) {
                bar.addView(Button(this@MapperAccessibilityService).apply {
                    text = label
                    isAllCaps = false
                    textSize = 11f
                    setOnClickListener { action() }
                }, LinearLayout.LayoutParams(0, NexusUi.dp(this@MapperAccessibilityService, 44), 1f))
            }
            add("+ Button") {
                pendingButton = true
                Toast.makeText(this@MapperAccessibilityService, "Press the controller button you want to place.", Toast.LENGTH_SHORT).show()
            }
            add("+ LS") { addDefaultStick(StickActionType.VIRTUAL_JOYSTICK) }
            add("+ RS") { addDefaultStick(StickActionType.CAMERA_DRAG) }
            add("Save") { save(); Toast.makeText(this@MapperAccessibilityService, "Profile saved", Toast.LENGTH_SHORT).show() }
            add("Play") {
                save()
                closeOverlayEditor(save = false)
                enableMapping(profile.profileId, assumeTargetForeground = true)
                Toast.makeText(this@MapperAccessibilityService, "Mapper armed for ${profile.displayName}", Toast.LENGTH_SHORT).show()
            }
            add("Close") { save(); closeOverlayEditor(save = false) }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = NexusUi.dp(this@MapperAccessibilityService, 44)
            }
            windowManager.addView(bar, params)
            toolbar = bar
        }

        private fun addDefaultStick(action: StickActionType) {
            if (stickMappings.any { it.action == action }) {
                Toast.makeText(this@MapperAccessibilityService, "That stick mapping already exists. Long-press its marker to remove it first.", Toast.LENGTH_SHORT).show()
                return
            }
            val used = (touchMappings.map { it.slot } + stickMappings.map { it.slot }).toSet()
            val slot = (0..1).firstOrNull { it !in used } ?: (0..31).firstOrNull { it !in used }
            if (slot == null) return
            val mapping = if (action == StickActionType.VIRTUAL_JOYSTICK) {
                StickMapping("left-stick", "LS", action, MotionEvent.AXIS_X, MotionEvent.AXIS_Y, 0.22f, 0.72f, 0.11f, 1f, 0.12f, false, slot)
            } else {
                StickMapping("right-camera", "RS", action, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ, 0.74f, 0.56f, 0.18f, 1f, 0.12f, false, slot)
            }
            stickMappings.add(mapping)
            addStickMarker(mapping)
            save()
        }

        private fun addTouchMarker(mapping: TouchMapping) {
            addMarker(mapping.id, "${mapping.label}\n${mapping.action.name}", mapping.xNorm, mapping.yNorm, NexusUi.cyan) { xNorm, yNorm, click, longPress ->
                val index = touchMappings.indexOfFirst { it.id == mapping.id }
                if (index >= 0) {
                    if (longPress) {
                        touchMappings.removeAt(index)
                        removeMarker(mapping.id)
                        save()
                    } else {
                        var updated = touchMappings[index].copy(xNorm = xNorm, yNorm = yNorm)
                        if (click) {
                            updated = updated.copy(action = if (updated.action == TouchActionType.TAP) TouchActionType.HOLD else TouchActionType.TAP)
                        }
                        touchMappings[index] = updated
                        updateMarkerText(mapping.id, "${updated.label}\n${updated.action.name}")
                    }
                }
            }
        }

        private fun addStickMarker(mapping: StickMapping) {
            addMarker(mapping.id, "${mapping.label}\n${if (mapping.action == StickActionType.VIRTUAL_JOYSTICK) "JOYSTICK" else "CAMERA"}", mapping.xNorm, mapping.yNorm, NexusUi.violet) { xNorm, yNorm, _, longPress ->
                val index = stickMappings.indexOfFirst { it.id == mapping.id }
                if (index >= 0) {
                    if (longPress) {
                        stickMappings.removeAt(index)
                        removeMarker(mapping.id)
                        save()
                    } else {
                        stickMappings[index] = stickMappings[index].copy(xNorm = xNorm, yNorm = yNorm)
                    }
                }
            }
        }

        private fun addMarker(
            id: String,
            label: String,
            xNorm: Float,
            yNorm: Float,
            color: Int,
            onUpdate: (Float, Float, Boolean, Boolean) -> Unit
        ) {
            val size = NexusUi.dp(this@MapperAccessibilityService, 72)
            val bounds = windowManager.currentWindowMetrics.bounds
            val view = TextView(this@MapperAccessibilityService).apply {
                text = label
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(android.graphics.Color.BLACK)
                alpha = 0.86f
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke(NexusUi.dp(this@MapperAccessibilityService, 2), android.graphics.Color.WHITE)
                }
            }
            val params = WindowManager.LayoutParams(
                size,
                size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (xNorm * bounds.width() - size / 2).toInt().coerceIn(0, maxOf(0, bounds.width() - size))
                y = (yNorm * bounds.height() - size / 2).toInt().coerceIn(NexusUi.dp(this@MapperAccessibilityService, 100), maxOf(NexusUi.dp(this@MapperAccessibilityService, 100), bounds.height() - size))
            }
            var downX = 0f
            var downY = 0f
            var startX = 0
            var startY = 0
            var downTime = 0L
            var moved = false
            view.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        startX = params.x
                        startY = params.y
                        downTime = SystemClock.uptimeMillis()
                        moved = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downX).toInt()
                        val dy = (event.rawY - downY).toInt()
                        if (kotlin.math.abs(dx) > 6 || kotlin.math.abs(dy) > 6) moved = true
                        params.x = (startX + dx).coerceIn(0, maxOf(0, bounds.width() - size))
                        params.y = (startY + dy).coerceIn(NexusUi.dp(this@MapperAccessibilityService, 100), maxOf(NexusUi.dp(this@MapperAccessibilityService, 100), bounds.height() - size))
                        windowManager.updateViewLayout(view, params)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val held = SystemClock.uptimeMillis() - downTime >= 650L && !moved
                        val click = !moved && !held
                        val cx = (params.x + size / 2f) / bounds.width().toFloat()
                        val cy = (params.y + size / 2f) / bounds.height().toFloat()
                        onUpdate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, 1f), click, held)
                        save()
                        true
                    }
                    else -> false
                }
            }
            windowManager.addView(view, params)
            markerViews[id] = view to params
        }

        private fun updateMarkerText(id: String, text: String) {
            (markerViews[id]?.first as? TextView)?.text = text
        }

        private fun removeMarker(id: String) {
            markerViews.remove(id)?.first?.let { runCatching { windowManager.removeView(it) } }
        }
    }

    companion object {
        private const val CHANNEL_ID = "nexus_mapper_runtime"
        private const val NOTIFICATION_ID = 5040
        private const val GAME_STARTUP_GRACE_MS = 3500L
        private const val BUBBLE_DELAY_MS = 900L

        @Volatile var current: MapperAccessibilityService? = null
            private set
    }
}
