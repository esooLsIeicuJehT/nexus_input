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

    fun handleKeyEvent(event: KeyEvent, config: MappingConfig, injector: InputInjector): Boolean {
        val aliases = ControllerBindingAliases.forKeyCode(event.keyCode)
        if (aliases.isEmpty()) return false
        val nodes = matchingNodes(config, aliases)
        if (nodes.isEmpty()) return false
        val action = event.action
        val repeat = event.repeatCount
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) return false
        executor.execute {
            nodes.forEach { node -> handleNodeInput(node, action, repeat, config, injector) }
        }
        return true
    }

    fun handleMotionEvent(event: MotionEvent, config: MappingConfig, injector: InputInjector) {
        if (!isControllerSource(event.source)) return
        val snapshot = MotionSnapshot.from(event)
        executor.execute {
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
            handleSticks(snapshot, config, injector)
        }
    }

    fun requiresPersistentTouch(config: MappingConfig): Boolean = config.buttons.any { node ->
        when (node.type) {
            NodeType.JOYSTICK_ZONE, NodeType.CAMERA_DRAG -> true
            NodeType.BUTTON -> node.buttonBehavior == ButtonBehavior.HOLD
            NodeType.MACRO -> node.macroActions.any { !it.actionType.equals("TAP", ignoreCase = true) }
            NodeType.TURBO -> false
        }
    }

    fun releaseAll(injector: InputInjector, timeoutMillis: Long = 1_500): Boolean {
        if (executor.isShutdown) return true
        val latch = CountDownLatch(1)
        executor.execute {
            turboTasks.values.forEach { it.cancel(false) }
            turboTasks.clear()
            activeSlots.toList().asReversed().forEach { slot ->
                runCatching { injector.endTouch(slot) }
            }
            activeSlots.clear()
            cameraPositions.clear()
            digitalAxisState.clear()
            latch.countDown()
        }
        return runCatching { latch.await(timeoutMillis, TimeUnit.MILLISECONDS) }.getOrDefault(false)
    }

    fun shutdown(injector: InputInjector?) {
        if (injector != null) releaseAll(injector)
        executor.shutdownNow()
    }

    private fun matchingNodes(config: MappingConfig, aliases: Set<String>): List<MappingNode> {
        val normalizedAliases = aliases.mapTo(hashSetOf(), ControllerBindingAliases::normalized)
        return config.buttons.filter { node ->
            ControllerBindingAliases.normalized(node.boundKey) in normalizedAliases &&
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
            if (activeSlots.add(slot)) {
                if (!injector.beginTouch(slot, point.x, point.y)) {
                    activeSlots.remove(slot)
                    onError("Touch down failed for ${node.label.ifBlank { node.boundKey }}")
                }
            }
        } else if (action == KeyEvent.ACTION_UP && activeSlots.remove(slot)) {
            if (!injector.endTouch(slot)) onError("Touch up failed for ${node.label.ifBlank { node.boundKey }}")
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
        if (!activeSlots.add(slot)) return
        if (!injector.beginTouch(slot, point.x, point.y)) {
            activeSlots.remove(slot)
            onError("Tap down failed for ${node.label.ifBlank { node.boundKey }}")
            return
        }
        executor.schedule({
            if (activeSlots.remove(slot) && !injector.endTouch(slot)) {
                onError("Tap up failed for ${node.label.ifBlank { node.boundKey }}")
            }
        }, durationMillis.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
    }

    private fun startTurbo(node: MappingNode, config: MappingConfig, injector: InputInjector) {
        if (turboTasks.containsKey(node.id)) return
        val hz = node.turboHz.coerceIn(2, 30)
        val period = (1_000L / hz).coerceAtLeast(33L)
        val future = executor.scheduleAtFixedRate(
            { pulse(node, config, injector, min(30L, period - 1L)) },
            0L,
            period,
            TimeUnit.MILLISECONDS
        )
        turboTasks[node.id] = future
    }

    private fun stopTurbo(node: MappingNode, config: MappingConfig, injector: InputInjector) {
        turboTasks.remove(node.id)?.cancel(false)
        val slot = slotForNode(config, node) ?: return
        if (activeSlots.remove(slot)) injector.endTouch(slot)
    }

    private fun runMacro(node: MappingNode, config: MappingConfig, injector: InputInjector) {
        if (node.macroActions.isEmpty()) {
            onError("Macro '${node.label.ifBlank { node.boundKey }}' has no actions")
            return
        }
        val slot = slotForNode(config, node) ?: return
        var at = 0L
        node.macroActions.forEach { step ->
            at += step.delayMs.coerceAtLeast(0L)
            val scheduledAt = at
            executor.schedule({ executeMacroStep(node, slot, step, injector) }, scheduledAt, TimeUnit.MILLISECONDS)
            if (step.actionType.equals("TAP", ignoreCase = true)) {
                at += step.durationMs.coerceAtLeast(1L)
            }
        }
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
        val x = step.xNorm * size.first
        val y = step.yNorm * size.second
        when (step.actionType.uppercase()) {
            "TAP" -> {
                if (injector.method == PrivilegeMethod.ACCESSIBILITY) {
                    if (!injector.injectTap(x, y)) onError("Macro tap failed for ${node.label}")
                } else if (activeSlots.add(slot)) {
                    if (!injector.beginTouch(slot, x, y)) {
                        activeSlots.remove(slot)
                        onError("Macro tap down failed for ${node.label}")
                    } else {
                        executor.schedule({
                            if (activeSlots.remove(slot) && !injector.endTouch(slot)) {
                                onError("Macro tap up failed for ${node.label}")
                            }
                        }, step.durationMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
                    }
                }
            }
            "HOLD" -> {
                if (injector.method == PrivilegeMethod.ACCESSIBILITY) {
                    onError("Macro HOLD requires persistent touch; Accessibility is insufficient")
                } else if (activeSlots.add(slot) && !injector.beginTouch(slot, x, y)) {
                    activeSlots.remove(slot)
                    onError("Macro hold down failed for ${node.label}")
                }
            }
            "RELEASE" -> {
                if (activeSlots.remove(slot) && !injector.endTouch(slot)) {
                    onError("Macro release failed for ${node.label}")
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
        val wasPressed = digitalAxisState[id] == true
        val nowPressed = if (wasPressed) value > 0.35f else value > 0.55f
        handleDigital(id, nowPressed, aliases, config, injector)
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
        val action = if (pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
        nodes.forEach { handleNodeInput(it, action, 0, config, injector) }
    }

    private fun handleSticks(snapshot: MotionSnapshot, config: MappingConfig, injector: InputInjector) {
        val size = screenSizeProvider() ?: return
        val width = size.first.toFloat()
        val height = size.second.toFloat()
        val minDimension = min(width, height)
        if (width <= 0f || height <= 0f) return

        config.buttons.forEach { node ->
            if (node.type != NodeType.JOYSTICK_ZONE && node.type != NodeType.CAMERA_DRAG) return@forEach
            if (injector.method == PrivilegeMethod.ACCESSIBILITY) {
                onError("${node.label.ifBlank { node.boundKey }} requires persistent touch, which Accessibility cannot provide")
                return@forEach
            }
            val pair = if (node.type == NodeType.JOYSTICK_ZONE) snapshot.leftStick else snapshot.rightStick
            if (pair == null) return@forEach
            var x = normalizeAxis(pair.first, node)
            var y = normalizeAxis(pair.second, node)
            val magnitude = hypot(x.toDouble(), y.toDouble()).toFloat()
            if (magnitude > 1f) {
                x /= magnitude
                y /= magnitude
            }

            val slot = slotForNode(config, node) ?: return@forEach
            val anchor = screenPoint(node) ?: return@forEach
            val radius = node.radiusNorm * minDimension
            if (!radius.isFinite() || radius <= 0f) {
                onError("${node.label.ifBlank { node.id }} has invalid radiusNorm=${node.radiusNorm}")
                return@forEach
            }

            if (x == 0f && y == 0f) {
                if (activeSlots.remove(slot)) injector.endTouch(slot)
                cameraPositions.remove(slot)
                return@forEach
            }

            when (node.type) {
                NodeType.JOYSTICK_ZONE -> {
                    if (activeSlots.add(slot) && !injector.beginTouch(slot, anchor.x, anchor.y)) {
                        activeSlots.remove(slot)
                        onError("Left-stick touch down failed")
                        return@forEach
                    }
                    val tx = (anchor.x + x * radius * node.sensitivity).coerceIn(0f, width - 1f)
                    val ty = (anchor.y + y * radius * node.sensitivity).coerceIn(0f, height - 1f)
                    if (!injector.moveTouch(slot, tx, ty)) onError("Left-stick touch move failed")
                }
                NodeType.CAMERA_DRAG -> {
                    if (activeSlots.add(slot)) {
                        if (!injector.beginTouch(slot, anchor.x, anchor.y)) {
                            activeSlots.remove(slot)
                            onError("Camera touch down failed")
                            return@forEach
                        }
                        cameraPositions[slot] = anchor.x to anchor.y
                    }
                    val current = cameraPositions[slot] ?: (anchor.x to anchor.y)
                    val step = 22f * node.sensitivity
                    var nextX = current.first + x * step
                    var nextY = current.second + y * step
                    val outside = abs(nextX - anchor.x) > radius || abs(nextY - anchor.y) > radius
                    if (outside) {
                        injector.endTouch(slot)
                        if (!injector.beginTouch(slot, anchor.x, anchor.y)) {
                            activeSlots.remove(slot)
                            cameraPositions.remove(slot)
                            onError("Camera touch reset failed")
                            return@forEach
                        }
                        nextX = anchor.x + x * step
                        nextY = anchor.y + y * step
                    }
                    nextX = nextX.coerceIn(0f, width - 1f)
                    nextY = nextY.coerceIn(0f, height - 1f)
                    if (!injector.moveTouch(slot, nextX, nextY)) onError("Camera touch move failed")
                    cameraPositions[slot] = nextX to nextY
                }
                else -> Unit
            }
        }
    }

    private fun normalizeAxis(axis: AxisValue, node: MappingNode): Float {
        val center = if (axis.minimum < 0f && axis.maximum > 0f) 0f else (axis.minimum + axis.maximum) / 2f
        val span = max(abs(axis.maximum - center), abs(center - axis.minimum))
        if (!span.isFinite() || span <= 0f) return 0f
        val raw = ((axis.raw - center) / span).coerceIn(-1f, 1f)
        val flat = (axis.flat / span).coerceIn(0f, 0.9f)
        val inner = max(node.deadzoneInner.coerceIn(0f, 0.9f), flat)
        val outer = max(inner + 0.01f, node.deadzoneOuter.coerceIn(0.01f, 1f))
        val magnitude = abs(raw)
        if (magnitude <= inner) return 0f
        val scaled = ((magnitude - inner) / (outer - inner)).coerceIn(0f, 1f)
        return sign(raw) * scaled
    }

    private fun slotForNode(config: MappingConfig, node: MappingNode): Int? {
        val index = config.buttons.indexOfFirst { it.id == node.id }
        if (index < 0) {
            onError("Mapping node '${node.id}' is not part of active config")
            return null
        }
        if (index > 31) {
            onError("Active config requires touch slot $index, but the verified runtime supports slots 0..31")
            return null
        }
        return index
    }

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
        return PointF(node.xNorm * size.first, node.yNorm * size.second)
    }

    private fun isControllerSource(source: Int): Boolean {
        val gamepad = source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
        val joystick = source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        val dpad = source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
        return gamepad || joystick || dpad
    }

    private data class AxisValue(
        val raw: Float,
        val minimum: Float,
        val maximum: Float,
        val flat: Float
    ) {
        fun normalizedTrigger(): Float {
            val span = maximum - minimum
            if (!span.isFinite() || span <= 0f) return 0f
            return ((raw - minimum) / span).coerceIn(0f, 1f)
        }
    }

    private data class MotionSnapshot(
        val leftX: AxisValue?,
        val leftY: AxisValue?,
        val rightX: AxisValue?,
        val rightY: AxisValue?,
        val leftTrigger: AxisValue?,
        val rightTrigger: AxisValue?,
        val hatX: AxisValue?,
        val hatY: AxisValue?
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
                    hatY = axis(MotionEvent.AXIS_HAT_Y)
                )
            }
        }
    }
}
