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
            val stickAxes = if (node.axisX != null && node.axisY != null) listOf(node.axisX, node.axisY)
                else when (node.type) { NodeType.JOYSTICK_ZONE -> listOf(0, 1); NodeType.CAMERA_DRAG -> listOf(11, 14); else -> emptyList() }
            if (stickAxes.any { !axes.add(it) }) add("$label: duplicate physical stick axis")
            val identity = if (stickAxes.isNotEmpty()) "AXES_${stickAxes.sorted().joinToString("_")}" else input
            if (identity.isBlank() || (node.inputKeyCode == null && node.inputScanCode == null && node.axisX == null && canonical !in ControllerBindingAliases.supported)) add("$label: unsupported physical input '${node.boundKey}'")
            if (!inputs.add(identity)) add("$label: duplicate physical input $identity")
            if (node.type == NodeType.TURBO && node.turboHz !in 2..30) add("$label: turbo must be 2..30 Hz")
            if (node.type == NodeType.MACRO) {
                if (node.macroActions.isEmpty() || node.macroActions.size > 100) add("$label: macro must contain 1..100 actions")
                var held = false
                var duration = 0L
                node.macroActions.forEach { step ->
                    if (!step.xNorm.isFinite() || step.xNorm !in 0f..1f || !step.yNorm.isFinite() || step.yNorm !in 0f..1f) add("$label: invalid macro coordinates")
                    if (step.delayMs !in 0..10000 || step.durationMs !in 1..10000) add("$label: invalid macro duration")
                    duration += step.delayMs.coerceIn(0, 10001) + step.durationMs.coerceIn(0, 10001)
                    when (step.actionType.uppercase()) {
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
