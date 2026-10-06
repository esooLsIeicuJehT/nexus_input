package com.example.input

import android.view.KeyEvent
import com.example.model.*

object TouchSlotAllocator {
    const val MAX_SLOTS = 32
    fun assign(config: MappingConfig): Map<String, Int> {
        require(config.buttons.size <= MAX_SLOTS) { "At most 32 simultaneous mapping slots are supported" }
        require(config.buttons.map { it.id }.distinct().size == config.buttons.size) { "Duplicate node IDs" }
        val specified = config.buttons.mapNotNull { it.touchSlot }
        require(specified.all { it in 0 until MAX_SLOTS }) { "Touch slots must be 0..31" }
        require(specified.distinct().size == specified.size) { "Duplicate touch slots" }
        val used = specified.toMutableSet()
        return config.buttons.associate { node ->
            val slot = node.touchSlot ?: (0 until MAX_SLOTS).first { it !in used }.also { used += it }
            node.id to slot
        }
    }
}

object ProfileValidator {
    fun runtimeErrors(config: MappingConfig, expectedPackage: String? = null, expectedId: String? = null): List<String> =
        errors(config,expectedPackage,expectedId) + buildList {
            if(config.joystick.sprintLockEnabled) add("Stored sprint lock is unsupported in v1; disable it before mapping")
            if(config.antiRecoilEnabled) add("Stored anti-recoil is unsupported in v1; disable it before mapping")
            if(config.camera.mouseDpiScale != 1f) add("Mouse mapping is unsupported in this gamepad v1; set mouse DPI scale to 1")
        }

    fun errors(config: MappingConfig, expectedPackage: String? = null, expectedId: String? = null,
               requireBindings: Boolean = true): List<String> = buildList {
        if (config.schemaVersion !in 1..3) add("Unsupported profile schema ${config.schemaVersion}")
        if (config.id.isBlank()) add("Profile ID is required")
        if (expectedId != null && config.id != expectedId) add("Profile ID does not match requested ID")
        if (config.profileName.isBlank()) add("Profile name is required")
        if (!config.gamePackage.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))) add("Exact Android game package is required")
        if (expectedPackage != null && config.gamePackage != expectedPackage) add("Profile targets ${config.gamePackage}, not $expectedPackage")
        if (config.lastUpdated < 0) add("Profile timestamp cannot be negative")
        if (config.controllerProfileId != null && config.controllerProfileId.isBlank()) add("Controller profile ID cannot be blank")
        if (requireBindings && config.buttons.isEmpty()) add("Profile has no input bindings")
        runCatching { TouchSlotAllocator.assign(config) }.exceptionOrNull()?.let { add(it.message ?: "Invalid touch slots") }
        val inputs = mutableSetOf<String>()
        val axes = mutableSetOf<Int>()
        fun finiteRange(value: Float, range: ClosedFloatingPointRange<Float>, label: String) {
            if(!value.isFinite() || value !in range) add("Invalid $label")
        }
        finiteRange(config.joystick.innerDeadzone,0f..0.9f,"joystick inner deadzone")
        finiteRange(config.joystick.outerDeadzone,.01f..1f,"joystick outer deadzone")
        if(config.joystick.innerDeadzone >= config.joystick.outerDeadzone) add("Invalid joystick deadzone interval")
        finiteRange(config.joystick.runThresholdNorm,.05f..0.99f,"joystick run threshold")
        finiteRange(config.joystick.walkRadiusScale,.1f..1f,"joystick walk radius scale")
        finiteRange(config.joystick.runRadiusScale,.1f..1.5f,"joystick run radius scale")
        if(config.joystick.walkRadiusScale >= config.joystick.runRadiusScale) add("Walk radius scale must be lower than run radius scale")
        finiteRange(config.joystick.curveExponent,.1f..4f,"joystick response curve")
        finiteRange(config.camera.horizontalSensitivity,.01f..10f,"camera horizontal sensitivity")
        finiteRange(config.camera.verticalSensitivity,.01f..10f,"camera vertical sensitivity")
        finiteRange(config.camera.accelerationCurve,.1f..4f,"camera response curve")
        finiteRange(config.camera.verticalRatio,.3f..1.5f,"camera vertical ratio")
        finiteRange(config.camera.fastTurnBoost,1f..3f,"camera fast-turn boost")
        finiteRange(config.camera.mouseDpiScale,.01f..10f,"mouse DPI scale")
        if(config.camera.smoothingFrames !in 1..30) add("Camera smoothing must be 1..30 frames")
        finiteRange(config.antiRecoilVerticalPull,0f..1f,"anti-recoil pull")
        finiteRange(config.rating,0f..5f,"rating")
        if(config.downloadCount < 0) add("Download count cannot be negative")
        val crosshair=config.crosshair
        finiteRange(crosshair.sizeDp,1f..100f,"reticle size")
        finiteRange(crosshair.thicknessDp,.1f..20f,"reticle thickness")
        finiteRange(crosshair.gapDp,0f..100f,"reticle gap")
        finiteRange(crosshair.opacity,0f..1f,"reticle opacity")
        finiteRange(crosshair.outlineThicknessDp,0f..20f,"reticle outline thickness")
        finiteRange(crosshair.offsetX,-1000f..1000f,"reticle X offset")
        finiteRange(crosshair.offsetY,-1000f..1000f,"reticle Y offset")
        finiteRange(crosshair.currentSpreadMultiplier,1f..2f,"reticle spread")
        if(!crosshair.colorHex.matches(Regex("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?")) ||
            !crosshair.outlineColorHex.matches(Regex("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?"))) add("Invalid reticle hex color")
        config.buttons.forEach { node ->
            val label = node.label.ifBlank { node.id }
            if (node.id.isBlank()) add("Node ID is required")
            if (!node.xNorm.isFinite() || node.xNorm !in 0f..1f || !node.yNorm.isFinite() || node.yNorm !in 0f..1f) add("$label: invalid coordinates")
            if (!node.radiusNorm.isFinite() || node.radiusNorm <= 0f || node.radiusNorm > .5f) add("$label: invalid radius")
            if (!node.sensitivity.isFinite() || node.sensitivity !in .01f..10f) add("$label: invalid sensitivity")
            if (!node.deadzoneInner.isFinite() || !node.deadzoneOuter.isFinite() ||
                node.deadzoneInner !in 0f..0.9f || node.deadzoneOuter !in .01f..1f ||
                node.deadzoneInner >= node.deadzoneOuter) add("$label: invalid deadzone interval")
            if (node.inputKeyCode != null && node.inputKeyCode !in 0..KeyEvent.getMaxKeyCode()) add("$label: invalid Android key code")
            if (node.inputScanCode != null && (node.inputScanCode < 0 || (node.inputKeyCode == null || node.inputKeyCode == KeyEvent.KEYCODE_UNKNOWN) && node.inputScanCode == 0)) add("$label: invalid scan code")
            if ((node.axisX == null) != (node.axisY == null)) add("$label: both stick axes must be set")
            if (node.axisX != null && (node.axisX !in 0..63 || node.axisY !in 0..63 || node.axisX == node.axisY)) add("$label: invalid stick axes")
            if (node.inputKeyCode == KeyEvent.KEYCODE_UNKNOWN && (node.inputScanCode == null || node.inputScanCode <= 0)) add("$label: unknown key requires a positive scan code")
            val canonical = ControllerBindingAliases.canonical(node.boundKey)
            val input = if (node.inputKeyCode != null && node.inputKeyCode != KeyEvent.KEYCODE_UNKNOWN) {
                ControllerBindingAliases.forKeyCode(node.inputKeyCode).firstOrNull()?.let(ControllerBindingAliases::canonical)
                    ?: "KEY_${node.inputKeyCode}"
            } else if (node.inputScanCode != null) "SCAN_${node.inputScanCode}" else canonical
            if (node.type == NodeType.JOYSTICK_ZONE && canonical != "LS" && node.axisX == null) add("$label: joystick must bind LS or explicit axes")
            if (node.type == NodeType.CAMERA_DRAG && canonical != "RS" && node.axisX == null) add("$label: camera must bind RS or explicit axes")
            val isStick=node.type in setOf(NodeType.JOYSTICK_ZONE,NodeType.CAMERA_DRAG)
            if(!isStick && node.inputKeyCode==null && node.inputScanCode==null && canonical in setOf("LS","RS")) add("$label: LS/RS require stick mapping; use L3/R3 for stick clicks")
            if(!isStick && node.axisX!=null) add("$label: button bindings cannot use stick axes")
            if(isStick && (node.inputKeyCode!=null || node.inputScanCode!=null)) add("$label: stick bindings cannot use key/scan codes")
            val stickAxes = if (isStick && node.axisX != null && node.axisY != null) listOf(node.axisX, node.axisY)
                else when (node.type) { NodeType.JOYSTICK_ZONE -> listOf(0, 1); NodeType.CAMERA_DRAG -> listOf(11, 14); else -> emptyList() }
            if (stickAxes.any { !axes.add(it) }) add("$label: duplicate physical stick axis")
            val identity = if (stickAxes.isNotEmpty()) "AXES_${stickAxes.sorted().joinToString("_")}" else input
            if (identity.isBlank() || (node.inputKeyCode == null && node.inputScanCode == null && node.axisX == null && canonical !in ControllerBindingAliases.supported)) add("$label: unsupported physical input '${node.boundKey}'")
            if (!inputs.add(identity)) add("$label: duplicate physical input $identity")
            if(!node.triggerPressThreshold.isFinite() || !node.triggerReleaseThreshold.isFinite() ||
                node.triggerReleaseThreshold !in 0f..1f || node.triggerPressThreshold !in 0f..1f ||
                node.triggerReleaseThreshold >= node.triggerPressThreshold) add("$label: invalid trigger hysteresis")
            if (node.type == NodeType.TURBO && node.turboHz !in 2..30) add("$label: turbo must be 2..30 Hz")
            if (node.type == NodeType.MACRO) {
                if (node.macroActions.isEmpty() || node.macroActions.size > 100) add("$label: macro must contain 1..100 actions")
                var held = false
                var duration = 0L
                node.macroActions.forEach { step ->
                    if (!step.xNorm.isFinite() || step.xNorm !in 0f..1f || !step.yNorm.isFinite() || step.yNorm !in 0f..1f) add("$label: invalid macro coordinates")
                    if (step.delayMs !in 0..10000 || step.durationMs !in 1..10000) add("$label: invalid macro duration")
                    duration += step.delayMs.coerceIn(0, 10001) + if(step.actionType.uppercase() in setOf("TAP","SWIPE")) step.durationMs.coerceIn(0, 10001) else 0
                    when (step.actionType.uppercase()) {
                        "SWIPE" -> {
                            if(held) add("$label: SWIPE while macro touch held")
                            if(step.endXNorm==null || !step.endXNorm.isFinite() || step.endXNorm !in 0f..1f ||
                                step.endYNorm==null || !step.endYNorm.isFinite() || step.endYNorm !in 0f..1f) add("$label: invalid swipe destination")
                        }
                        "TAP" -> if (held) add("$label: TAP while macro touch held")
                        "HOLD" -> { if (held) add("$label: repeated macro HOLD"); held = true }
                        "RELEASE" -> { if (!held) add("$label: RELEASE without HOLD"); held = false }
                        else -> add("$label: unknown macro action ${step.actionType}")
                    }
                }
                if (held) add("$label: macro must release its held touch")
                if (duration > 60000) add("$label: macro exceeds 60 seconds")
            }
        }
    }
}
