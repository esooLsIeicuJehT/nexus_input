package com.example.input

import android.graphics.PointF
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.example.injector.InputInjector
import com.example.model.ButtonBehavior
import com.example.model.MacroStep
import com.example.model.MappingConfig
import com.example.model.MappingNode
import com.example.model.NodeType
import com.example.model.PrivilegeMethod
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.pow

/**
 * Serial controller-to-touch mapping engine used by the AccessibilityService.
 * It operates only on real Android KeyEvent/MotionEvent samples and a saved
 * MappingConfig. All touch injection is delegated to the selected backend.
 */
class GamepadMappingRuntime(
    private val screenSizeProvider: () -> Pair<Int, Int>?,
    private val onError: (String) -> Unit
) {
    private val executor = ScheduledThreadPoolExecutor(1).apply {
        removeOnCancelPolicy = true
    }
    private val activeSlots = mutableSetOf<Int>()
    private val cameraPositions = mutableMapOf<Int, Pair<Float, Float>>()
    private val turboTasks = mutableMapOf<String, ScheduledFuture<*>>()
    private val digitalAxisState = mutableMapOf<String, Boolean>()
    private val inputOwners = mutableMapOf<String, MutableSet<String>>()
    private val activeMacros = mutableSetOf<String>()
    private val smoothedCamera = mutableMapOf<Int,Pair<Float,Float>>()
    private val cameraLastTickNanos = mutableMapOf<Int,Long>()
    private var motionTask: ScheduledFuture<*>? = null
    private var latestMotion: Triple<MotionSnapshot, MappingConfig, InputInjector>? = null
    @Volatile private var generation = 0L

    private fun enqueue(action: () -> Unit) {
        if (executor.isShutdown) { onError("Mapper executor is shut down"); return }
        val token = generation
        executor.execute {
            if (token != generation) return@execute
            try { action() } catch (error: Exception) { onError("Mapper failed: ${error.javaClass.simpleName}: ${error.message}") }
        }
    }

    private fun schedule(delayMs: Long, action: () -> Unit) {
        val token=generation
        executor.schedule({
            if(token==generation) try { action() } catch(error:Exception) { onError("Scheduled touch failed: ${error.message}") }
        },delayMs,TimeUnit.MILLISECONDS)
    }

    internal fun awaitIdle() { executor.submit {}.get(2, TimeUnit.SECONDS) }

    fun handleKeyEvent(event: KeyEvent, config: MappingConfig, injector: InputInjector): Boolean {
        if (!ControllerSourceClassifier.accepts(event.source, event.device?.sources ?: 0)) return false
        val aliases = ControllerBindingAliases.forEvent(event)
        val nodes = config.buttons.filter { node ->
            val physicalMatches = when {
                node.inputKeyCode != null && node.inputKeyCode != KeyEvent.KEYCODE_UNKNOWN -> node.inputKeyCode == event.keyCode
                node.inputScanCode != null -> node.inputScanCode == event.scanCode
                else -> aliases.any { ControllerBindingAliases.canonical(it) == ControllerBindingAliases.canonical(node.boundKey) }
            }
            physicalMatches && node.type in setOf(NodeType.BUTTON, NodeType.TURBO, NodeType.MACRO)
        }
        if (nodes.isEmpty()) return false
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return false
        if (event.repeatCount > 0) return true
        val pressed = event.action == KeyEvent.ACTION_DOWN
        val channel = if(event.keyCode != KeyEvent.KEYCODE_UNKNOWN) "key_${event.deviceId}_${event.keyCode}" else "scan_${event.deviceId}_${event.scanCode}"
        enqueue { nodes.forEach { handleOwnedInput(channel, it, pressed, config, injector) } }
        return true
    }

    fun handleMotionEvent(event: MotionEvent, config: MappingConfig, injector: InputInjector) {
        if (!ControllerSourceClassifier.accepts(event.source, event.device?.sources ?: 0)) return
        handleMotionSnapshot(MotionSnapshot.from(event), config, injector)
    }

    internal fun handleMotionSnapshot(snapshot: MotionSnapshot, config: MappingConfig, injector: InputInjector) {
        enqueue {
            (listOfNotNull(snapshot.leftX,snapshot.leftY,snapshot.rightX,snapshot.rightY,
                snapshot.leftTrigger,snapshot.rightTrigger,snapshot.hatX,snapshot.hatY)+snapshot.axes.values).forEach { axis ->
                require(axis.raw.isFinite() && axis.minimum.isFinite() && axis.maximum.isFinite() && axis.flat.isFinite() && axis.minimum < axis.maximum) { "Invalid Android motion sample or axis range" }
            }
            snapshot.leftTrigger?.let {
                handleThreshold("left_trigger", it.normalizedTrigger(), ControllerBindingAliases.leftTrigger(), config, injector)
            }
            snapshot.rightTrigger?.let {
                handleThreshold("right_trigger", it.normalizedTrigger(), ControllerBindingAliases.rightTrigger(), config, injector)
            }
            val hatX = snapshot.hatX?.raw ?: 0f
            val hatY = snapshot.hatY?.raw ?: 0f
            handleDigital("hat_left", hatX < -0.5f, ControllerBindingAliases.dpadLeft(), config, injector)
            handleDigital("hat_right", hatX > 0.5f, ControllerBindingAliases.dpadRight(), config, injector)
            handleDigital("hat_up", hatY < -0.5f, ControllerBindingAliases.dpadUp(), config, injector)
            handleDigital("hat_down", hatY > 0.5f, ControllerBindingAliases.dpadDown(), config, injector)

            latestMotion = Triple(snapshot, config, injector)
            val hasJoystick = config.buttons.any { it.type == NodeType.JOYSTICK_ZONE }
            val hasCamera = config.buttons.any { it.type == NodeType.CAMERA_DRAG }
            if (hasJoystick || hasCamera) {
                // LS is position-based: one update per real MotionEvent is sufficient to
                // leave the touch held at its last position. RS camera drag is velocity-
                // based, so it keeps the 8 ms driver only while the camera stick is active.
                val cameraActive = handleSticks(
                    snapshot,
                    config,
                    injector,
                    processJoystick = hasJoystick,
                    processCamera = hasCamera
                )
                if (hasCamera && cameraActive) ensureCameraLoop()
                else if (!cameraActive) stopCameraLoop()
            }
        }
    }

    private fun ensureCameraLoop() {
        if (motionTask != null) return
        val token = generation
        motionTask = executor.scheduleWithFixedDelay({
            if (token == generation) {
                val current = latestMotion
                if (current == null) {
                    stopCameraLoop()
                } else {
                    val (sample, profile, backend) = current
                    try {
                        val active = handleSticks(
                            sample,
                            profile,
                            backend,
                            processJoystick = false,
                            processCamera = true
                        )
                        if (!active) stopCameraLoop()
                    } catch (error: Exception) {
                        onError("Stick mapping failed: ${error.message}")
                    }
                }
            }
        }, 8, 8, TimeUnit.MILLISECONDS)
    }

    private fun stopCameraLoop() {
        motionTask?.cancel(false)
        motionTask = null
    }

    fun requiresPersistentTouch(config: MappingConfig): Boolean = config.buttons.any { node ->
        when (node.type) {
            NodeType.JOYSTICK_ZONE, NodeType.CAMERA_DRAG -> true
            NodeType.BUTTON -> node.buttonBehavior == ButtonBehavior.HOLD
            NodeType.MACRO -> node.macroActions.any { it.actionType.uppercase() in setOf("HOLD","RELEASE") }
            NodeType.TURBO -> false
        }
    }

    @Synchronized
    fun releaseAll(injector: InputInjector, timeoutMillis: Long = 1_500): Boolean {
        if (executor.isShutdown) { onError("Cannot confirm release: mapper executor is shut down"); return false }
        generation++
        executor.queue.toList().forEach { (it as? java.util.concurrent.Future<*>)?.cancel(false) }
        executor.purge()
        val latch = CountDownLatch(1)
        var success = true
        executor.execute {
            try {
                executor.queue.toList().forEach { (it as? java.util.concurrent.Future<*>)?.cancel(false) }
                executor.purge()
                turboTasks.values.forEach { it.cancel(false) }; turboTasks.clear()
                stopCameraLoop(); latestMotion = null
                activeSlots.toList().asReversed().forEach { slot ->
                    val released = runCatching { injector.endTouch(slot) }.getOrElse {
                        onError("Panic release threw for slot $slot: ${it.message}"); false
                    }
                    if (released) activeSlots.remove(slot)
                    else { success = false; onError("Panic release failed for slot $slot") }
                }
                cameraPositions.clear(); smoothedCamera.clear(); cameraLastTickNanos.clear(); activeMacros.clear(); digitalAxisState.clear(); inputOwners.clear()
            } finally { latch.countDown() }
        }
        val completed = runCatching { latch.await(timeoutMillis, TimeUnit.MILLISECONDS) }.getOrDefault(false)
        if (!completed) onError("Panic release timed out after $timeoutMillis ms")
        return completed && success
    }

    fun shutdown(injector: InputInjector?) {
        if (injector != null) releaseAll(injector)
        executor.shutdownNow()
    }

    private fun matchingNodes(config: MappingConfig, aliases: Set<String>): List<MappingNode> {
        val normalizedAliases = aliases.mapTo(hashSetOf(), ControllerBindingAliases::canonical)
        return config.buttons.filter { node ->
            (if(node.inputKeyCode != null && node.inputKeyCode != KeyEvent.KEYCODE_UNKNOWN)
                ControllerBindingAliases.forKeyCode(node.inputKeyCode).any { ControllerBindingAliases.canonical(it) in normalizedAliases }
             else if(node.inputScanCode != null) false
             else ControllerBindingAliases.canonical(node.boundKey) in normalizedAliases) &&
                node.type in setOf(NodeType.BUTTON, NodeType.TURBO, NodeType.MACRO)
        }
    }

    private fun handleNodeInput(
        node: MappingNode,
        action: Int,
        repeatCount: Int,
        config: MappingConfig,
        injector: InputInjector
    ) {
        when (node.type) {
            NodeType.BUTTON -> when (node.buttonBehavior) {
                ButtonBehavior.TAP -> {
                    if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) pulse(node, config, injector, 35L)
                }
                ButtonBehavior.HOLD -> handleHold(node, action, repeatCount, config, injector)
            }
            NodeType.TURBO -> {
                if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) startTurbo(node, config, injector)
                if (action == KeyEvent.ACTION_UP) stopTurbo(node, config, injector)
            }
            NodeType.MACRO -> {
                if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) runMacro(node, config, injector)
            }
            NodeType.JOYSTICK_ZONE, NodeType.CAMERA_DRAG -> Unit
        }
    }

    private fun handleHold(
        node: MappingNode,
        action: Int,
        repeatCount: Int,
        config: MappingConfig,
        injector: InputInjector
    ) {
        if (injector.method == PrivilegeMethod.ACCESSIBILITY) {
            onError("${node.label.ifBlank { node.boundKey }} requires persistent touch, which Accessibility cannot provide")
            return
        }
        val slot = slotForNode(config, node) ?: return
        val point = screenPoint(node) ?: return
        if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) {
            if (activateSlot(slot)) {
                if (!injector.beginTouch(slot, point.x, point.y)) {
                    activeSlots.remove(slot)
                    onError("Touch down failed for ${node.label.ifBlank { node.boundKey }}")
                }
            }
        } else if (action == KeyEvent.ACTION_UP && activeSlots.contains(slot)) {
            if (injector.endTouch(slot)) activeSlots.remove(slot) else onError("Touch up failed for ${node.label.ifBlank { node.boundKey }}")
        }
    }

    private fun pulse(
        node: MappingNode,
        config: MappingConfig,
        injector: InputInjector,
        durationMillis: Long
    ) {
        val point = screenPoint(node) ?: return
        if (injector.method == PrivilegeMethod.ACCESSIBILITY) {
            if (!injector.injectTap(point.x, point.y)) onError("Accessibility tap failed for ${node.label.ifBlank { node.boundKey }}")
            return
        }
        val slot = slotForNode(config, node) ?: return
        if (!activateSlot(slot)) return
        if (!injector.beginTouch(slot, point.x, point.y)) {
            activeSlots.remove(slot)
            onError("Tap down failed for ${node.label.ifBlank { node.boundKey }}")
            return
        }
        schedule(durationMillis.coerceAtLeast(1L)) {
            if(activeSlots.contains(slot)) {
                if(injector.endTouch(slot)) activeSlots.remove(slot)
                else onError("Tap up failed for ${node.label.ifBlank { node.boundKey }}")
            }
        }
    }

    private fun startTurbo(node: MappingNode, config: MappingConfig, injector: InputInjector) {
        if (turboTasks.containsKey(node.id)) return
        val hz = node.turboHz.coerceIn(2, 30)
        val period = (1_000L / hz).coerceAtLeast(33L)
        val future = executor.scheduleWithFixedDelay(
            { try { pulse(node, config, injector, min(30L, period - 1L)) } catch(error:Exception) { onError("Turbo touch failed: ${error.message}") } },
            0L,
            period,
            TimeUnit.MILLISECONDS
        )
        turboTasks[node.id] = future
    }

    private fun stopTurbo(node: MappingNode, config: MappingConfig, injector: InputInjector) {
        turboTasks.remove(node.id)?.cancel(false)
        val slot = slotForNode(config, node) ?: return
        if (activeSlots.contains(slot)) {
            if (injector.endTouch(slot)) activeSlots.remove(slot) else onError("Touch release failed for slot $slot")
        }
    }

    private fun runMacro(node: MappingNode, config: MappingConfig, injector: InputInjector) {
        if (node.macroActions.isEmpty()) {
            onError("Macro '${node.label.ifBlank { node.boundKey }}' has no actions")
            return
        }
        val slot = slotForNode(config, node) ?: return
        if(!activeMacros.add(node.id)) { onError("Macro '${node.label.ifBlank { node.boundKey }}' is already running");return }
        var at = 0L
        node.macroActions.forEach { step ->
            at += step.delayMs.coerceAtLeast(0L)
            val scheduledAt = at
            schedule(scheduledAt) { executeMacroStep(node, slot, step, injector) }
            if (step.actionType.uppercase() in setOf("TAP","SWIPE")) {
                at += step.durationMs.coerceAtLeast(1L)
            }
        }
        fun finish() {
            if(slot !in activeSlots) activeMacros.remove(node.id)
            else schedule(16L) { finish() }
        }
        schedule(at + 1L) { finish() }
    }

    private fun executeMacroStep(node: MappingNode, slot: Int, step: MacroStep, injector: InputInjector) {
        val size = screenSizeProvider() ?: run {
            onError("Cannot execute macro '${node.label}': screen geometry unavailable")
            return
        }
        if (step.xNorm !in 0f..1f || step.yNorm !in 0f..1f) {
            onError("Macro '${node.label}' contains invalid normalized coordinate (${step.xNorm},${step.yNorm})")
            return
        }
        val x = step.xNorm * (size.first - 1)
        val y = step.yNorm * (size.second - 1)
        when (step.actionType.uppercase()) {
            "TAP" -> {
                if (injector.method == PrivilegeMethod.ACCESSIBILITY) {
                    if (!injector.injectTap(x, y)) onError("Macro tap failed for ${node.label}")
                } else if (activateSlot(slot)) {
                    if (!injector.beginTouch(slot, x, y)) {
                        activeSlots.remove(slot)
                        onError("Macro tap down failed for ${node.label}")
                    } else {
                        schedule(step.durationMs.coerceAtLeast(1L)) {
                            if(activeSlots.contains(slot)) {
                                if(injector.endTouch(slot)) activeSlots.remove(slot)
                                else onError("Macro tap up failed for ${node.label}")
                            }
                        }
                    }
                }
            }
            "SWIPE" -> {
                val destinationX=step.endXNorm ?: error("Swipe destination X is missing")
                val destinationY=step.endYNorm ?: error("Swipe destination Y is missing")
                require(destinationX.isFinite() && destinationX in 0f..1f && destinationY.isFinite() && destinationY in 0f..1f) { "Invalid swipe destination" }
                val endX=destinationX*(size.first-1);val endY=destinationY*(size.second-1)
                if(injector.method==PrivilegeMethod.ACCESSIBILITY) {
                    if(!injector.injectDrag(listOf(PointF(x,y),PointF(endX,endY)),step.durationMs)) onError("Accessibility swipe request failed")
                } else if(activateSlot(slot)) {
                    if(!injector.beginTouch(slot,x,y)) { activeSlots.remove(slot);onError("Swipe down failed") }
                    else {
                        val segments=((step.durationMs+15)/16).toInt().coerceAtLeast(1)
                        for(index in 1..segments) schedule(step.durationMs*index/segments) {
                            if(slot in activeSlots) {
                                val fraction=index.toFloat()/segments
                                if(!injector.moveTouch(slot,x+(endX-x)*fraction,y+(endY-y)*fraction)) onError("Swipe move failed")
                                if(index==segments) {
                                    if(injector.endTouch(slot)) activeSlots.remove(slot) else onError("Swipe up failed")
                                }
                            }
                        }
                    }
                }
            }
            "HOLD" -> {
                if (injector.method == PrivilegeMethod.ACCESSIBILITY) {
                    onError("Macro HOLD requires persistent touch; Accessibility is insufficient")
                } else if (activateSlot(slot) && !injector.beginTouch(slot, x, y)) {
                    activeSlots.remove(slot)
                    onError("Macro hold down failed for ${node.label}")
                }
            }
            "RELEASE" -> {
                if(activeSlots.contains(slot)) {
                    if(injector.endTouch(slot)) activeSlots.remove(slot)
                    else onError("Macro release failed for ${node.label}")
                }
            }
            else -> onError("Macro '${node.label}' has unsupported action '${step.actionType}'")
        }
    }

    private fun handleThreshold(
        id: String,
        value: Float,
        aliases: Set<String>,
        config: MappingConfig,
        injector: InputInjector
    ) {
        matchingNodes(config,aliases).forEach { node ->
            val channel = "${id}_${node.id}"
            val wasPressed = digitalAxisState[channel] == true
            val nowPressed = value > if(wasPressed) node.triggerReleaseThreshold else node.triggerPressThreshold
            if(wasPressed != nowPressed) {
                digitalAxisState[channel] = nowPressed
                handleOwnedInput(channel,node,nowPressed,config,injector)
            }
        }
    }

    private fun handleDigital(
        id: String,
        pressed: Boolean,
        aliases: Set<String>,
        config: MappingConfig,
        injector: InputInjector
    ) {
        val previous = digitalAxisState[id] == true
        if (previous == pressed) return
        digitalAxisState[id] = pressed
        val nodes = matchingNodes(config, aliases)
        nodes.forEach { handleOwnedInput(id, it, pressed, config, injector) }
    }

    private fun handleOwnedInput(channel: String, node: MappingNode, pressed: Boolean, config: MappingConfig, injector: InputInjector) {
        val owners = inputOwners.getOrPut(node.id) { mutableSetOf() }
        val wasPressed = owners.isNotEmpty()
        if (pressed) owners.add(channel) else owners.remove(channel)
        val isPressed = owners.isNotEmpty()
        if (wasPressed != isPressed) handleNodeInput(node,
            if (isPressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, 0, config, injector)
    }

    /**
     * Applies the latest stick sample. Joystick zones are position based and are
     * updated only for real MotionEvents. Camera drag is velocity based and may
     * be called by the 8 ms camera loop while the right stick remains deflected.
     * Returns true when at least one processed camera node remains active.
     */
    private fun handleSticks(
        snapshot: MotionSnapshot,
        config: MappingConfig,
        injector: InputInjector,
        processJoystick: Boolean,
        processCamera: Boolean
    ): Boolean {
        val size = screenSizeProvider() ?: run { onError("Stick geometry is unavailable");return false }
        val width = size.first.toFloat()
        val height = size.second.toFloat()
        val minDimension = min(width, height)
        if (width <= 0f || height <= 0f) { onError("Invalid stick screen geometry");return false }
        var cameraActive = false

        config.buttons.forEach { node ->
            if (node.type == NodeType.JOYSTICK_ZONE && !processJoystick) return@forEach
            if (node.type == NodeType.CAMERA_DRAG && !processCamera) return@forEach
            if (node.type != NodeType.JOYSTICK_ZONE && node.type != NodeType.CAMERA_DRAG) return@forEach
            if (injector.method == PrivilegeMethod.ACCESSIBILITY) {
                onError("${node.label.ifBlank { node.boundKey }} requires persistent touch, which Accessibility cannot provide")
                return@forEach
            }
            val pair = if (node.axisX != null && node.axisY != null) {
                val x = snapshot.axes[node.axisX]; val y = snapshot.axes[node.axisY]
                if (x != null && y != null) x to y else null
            } else if (node.type == NodeType.JOYSTICK_ZONE) snapshot.leftStick else snapshot.rightStick
            if (pair == null) { onError("${node.label.ifBlank { node.boundKey }}: controller does not expose required axes"); return@forEach }
            val processed = normalizeStickVector(pair.first, pair.second, node,
                if (node.type == NodeType.JOYSTICK_ZONE) config.joystick.curveExponent else config.camera.accelerationCurve,
                if (node.type == NodeType.CAMERA_DRAG) config.camera.fastTurnBoost else 1f,
                if (node.type == NodeType.CAMERA_DRAG) config.camera.verticalRatio else 1f)
            var x = processed.first
            var y = processed.second
            if (node.invertY || (node.type == NodeType.CAMERA_DRAG && config.camera.invertY)) y = -y

            val slot = slotForNode(config, node) ?: return@forEach
            val anchor = screenPoint(node) ?: return@forEach
            val radius = node.radiusNorm * minDimension
            if (!radius.isFinite() || radius <= 0f) {
                onError("${node.label.ifBlank { node.id }} has invalid radiusNorm=${node.radiusNorm}")
                return@forEach
            }

            if (x == 0f && y == 0f) {
                if (activeSlots.contains(slot)) {
                    if (injector.endTouch(slot)) activeSlots.remove(slot) else onError("Touch release failed for slot $slot")
                }
                cameraPositions.remove(slot)
                smoothedCamera.remove(slot)
                cameraLastTickNanos.remove(slot)
                return@forEach
            }

            when (node.type) {
                NodeType.JOYSTICK_ZONE -> {
                    if (activateSlot(slot) && !injector.beginTouch(slot, anchor.x, anchor.y)) {
                        activeSlots.remove(slot)
                        onError("Left-stick touch down failed")
                        return@forEach
                    }
                    val magnitude = hypot(x.toDouble(), y.toDouble()).toFloat().coerceIn(0f, 1f)
                    val runThreshold = config.joystick.runThresholdNorm.coerceIn(.05f, .99f)
                    val outputScale = if (magnitude >= runThreshold) config.joystick.runRadiusScale else config.joystick.walkRadiusScale
                    val tx = (anchor.x + x * radius * node.sensitivity * outputScale).coerceIn(0f, width - 1f)
                    val ty = (anchor.y + y * radius * node.sensitivity * outputScale).coerceIn(0f, height - 1f)
                    if (!injector.moveTouch(slot, tx, ty)) onError("Left-stick touch move failed")
                }
                NodeType.CAMERA_DRAG -> {
                    cameraActive = true
                    if (activateSlot(slot)) {
                        if (!injector.beginTouch(slot, anchor.x, anchor.y)) {
                            activeSlots.remove(slot)
                            onError("Camera touch down failed")
                            return@forEach
                        }
                        cameraPositions[slot] = anchor.x to anchor.y
                        cameraLastTickNanos[slot] = System.nanoTime()
                        return@forEach
                    }
                    val current = cameraPositions[slot] ?: (anchor.x to anchor.y)
                    val nowNanos = System.nanoTime()
                    val previousTick = cameraLastTickNanos.put(slot, nowNanos) ?: nowNanos
                    val dt = ((nowNanos - previousTick).coerceIn(0L, 64_000_000L) / 1_000_000_000f)
                    val speed = radius * 7.5f * node.sensitivity.coerceIn(.25f, 3f)
                    val previous=smoothedCamera[slot] ?: (0f to 0f)
                    val alpha=1f/config.camera.smoothingFrames
                    val sx=previous.first+(x-previous.first)*alpha
                    val sy=previous.second+(y-previous.second)*alpha
                    smoothedCamera[slot]=sx to sy
                    val deltaX=sx*speed*dt*config.camera.horizontalSensitivity
                    val deltaY=sy*speed*dt*config.camera.verticalSensitivity
                    var nextX = current.first + deltaX
                    var nextY = current.second + deltaY
                    val margin = max(24f, minDimension * .04f)
                    val outsideSafeBounds =
                        nextX < margin || nextX > width - 1f - margin ||
                        nextY < margin || nextY > height - 1f - margin
                    if (outsideSafeBounds) {
                        if (!injector.endTouch(slot)) {
                            onError("Camera release failed for slot $slot")
                            return@forEach
                        }
                        activeSlots.remove(slot)
                        cameraPositions.remove(slot)
                        smoothedCamera.remove(slot)
                        cameraLastTickNanos.remove(slot)
                        return@forEach
                    }
                    nextX = nextX.coerceIn(margin, width - 1f - margin)
                    nextY = nextY.coerceIn(margin, height - 1f - margin)
                    if (!injector.moveTouch(slot, nextX, nextY)) onError("Camera touch move failed")
                    cameraPositions[slot] = nextX to nextY
                }
                else -> Unit
            }
        }
        return cameraActive
    }

    private fun normalizeStickVector(xAxis: AxisValue, yAxis: AxisValue, node: MappingNode, exponent: Float, fastTurnBoost: Float, verticalRatio: Float): Pair<Float,Float> {
        fun raw(axis: AxisValue): Pair<Float,Float> {
            val center = if (axis.minimum < 0f && axis.maximum > 0f) 0f else (axis.minimum + axis.maximum) / 2f
            val span = max(abs(axis.maximum - center), abs(center - axis.minimum))
            require(axis.raw.isFinite() && span.isFinite() && span > 0f) { "Invalid stick sample or range" }
            return ((axis.raw - center) / span).coerceIn(-1f, 1f) to (axis.flat / span).coerceIn(0f, .9f)
        }
        val (rawX, flatX) = raw(xAxis)
        val (rawY, flatY) = raw(yAxis)
        val magnitude = hypot(rawX.toDouble(), rawY.toDouble()).toFloat()
        val inner = max(node.deadzoneInner.coerceIn(0f, .9f), max(flatX, flatY))
        val outer = max(inner + .01f, node.deadzoneOuter.coerceIn(.01f, 1f))
        if (magnitude <= inner || magnitude == 0f) return 0f to 0f
        val normalized = ((magnitude - inner) / (outer - inner)).coerceIn(0f, 1f)
        val curved = normalized.pow(exponent.coerceIn(.25f, 4f))
        val edge = ((normalized - .9f) / .1f).coerceIn(0f, 1f)
        val smoothEdge = edge * edge * (3f - 2f * edge)
        val boost = 1f + (fastTurnBoost.coerceIn(1f, 3f) - 1f) * smoothEdge
        val factor = curved * boost / magnitude
        return (rawX * factor).coerceIn(-1f, 1f) to
            (rawY * factor * verticalRatio.coerceIn(.3f, 1.5f)).coerceIn(-1.5f, 1.5f)
    }

    private fun activateSlot(slot:Int): Boolean {
        if(slot in activeSlots) return false
        if(activeSlots.size>=16) { onError("Android supports at most 16 simultaneous touch contacts");return false }
        return activeSlots.add(slot)
    }

    private fun slotForNode(config: MappingConfig, node: MappingNode): Int? = try {
        TouchSlotAllocator.assign(config)[node.id] ?: run { onError("Node '${node.id}' missing from profile"); null }
    } catch (error: IllegalArgumentException) { onError(error.message ?: "Invalid touch slots"); null }

    private fun screenPoint(node: MappingNode): PointF? {
        if (node.xNorm !in 0f..1f || node.yNorm !in 0f..1f) {
            onError("${node.label.ifBlank { node.id }} has invalid normalized coordinate (${node.xNorm},${node.yNorm})")
            return null
        }
        val size = screenSizeProvider() ?: run {
            onError("Screen geometry is unavailable")
            return null
        }
        if (size.first <= 0 || size.second <= 0) {
            onError("Invalid screen geometry ${size.first}x${size.second}")
            return null
        }
        return PointF(node.xNorm * (size.first - 1), node.yNorm * (size.second - 1))
    }

    internal data class AxisValue(
        val raw: Float,
        val minimum: Float,
        val maximum: Float,
        val flat: Float
    ) {
        fun normalizedTrigger(): Float {
            val span = maximum - minimum
            require(raw.isFinite() && span.isFinite() && span > 0f) { "Invalid trigger sample or range" }
            return ((raw - minimum) / span).coerceIn(0f, 1f)
        }
    }

    internal data class MotionSnapshot(
        val leftX: AxisValue?,
        val leftY: AxisValue?,
        val rightX: AxisValue?,
        val rightY: AxisValue?,
        val leftTrigger: AxisValue?,
        val rightTrigger: AxisValue?,
        val hatX: AxisValue?,
        val hatY: AxisValue?,
        val axes: Map<Int, AxisValue> = emptyMap()
    ) {
        val leftStick: Pair<AxisValue, AxisValue>?
            get() = if (leftX != null && leftY != null) leftX to leftY else null
        val rightStick: Pair<AxisValue, AxisValue>?
            get() = if (rightX != null && rightY != null) rightX to rightY else null

        companion object {
            fun from(event: MotionEvent): MotionSnapshot {
                val device = event.device
                fun axis(axis: Int): AxisValue? {
                    val range = device?.motionRanges?.firstOrNull { it.axis == axis } ?: return null
                    return AxisValue(
                        raw = event.getAxisValue(axis),
                        minimum = range.min,
                        maximum = range.max,
                        flat = range.flat
                    )
                }

                val z = axis(MotionEvent.AXIS_Z)
                val rz = axis(MotionEvent.AXIS_RZ)
                val rx = axis(MotionEvent.AXIS_RX)
                val ry = axis(MotionEvent.AXIS_RY)
                return MotionSnapshot(
                    leftX = axis(MotionEvent.AXIS_X),
                    leftY = axis(MotionEvent.AXIS_Y),
                    rightX = z ?: rx,
                    rightY = rz ?: ry,
                    leftTrigger = axis(MotionEvent.AXIS_LTRIGGER) ?: axis(MotionEvent.AXIS_BRAKE),
                    rightTrigger = axis(MotionEvent.AXIS_RTRIGGER) ?: axis(MotionEvent.AXIS_GAS),
                    hatX = axis(MotionEvent.AXIS_HAT_X),
                    hatY = axis(MotionEvent.AXIS_HAT_Y),
                    axes = device?.motionRanges.orEmpty().mapNotNull { range -> axis(range.axis)?.let { range.axis to it } }.toMap()
                )
            }
        }
    }
}
