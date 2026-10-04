package com.inputmapper.platform.debug

import com.inputmapper.platform.game.GameProfile

/** Non-destructive validation for saved mapping profiles. */
object GameProfileValidator {
    fun validate(profile: GameProfile): List<String> {
        val errors = mutableListOf<String>()
        if (profile.profileId.isBlank()) errors += "profileId is blank"
        if (profile.displayName.isBlank()) errors += "displayName is blank"
        if (profile.packageName.isBlank()) errors += "packageName is blank"

        val allSlots = mutableMapOf<Int, String>()
        profile.touchMappings.forEach { mapping ->
            if (mapping.xNorm !in 0f..1f || mapping.yNorm !in 0f..1f) {
                errors += "button ${mapping.label}: coordinates outside normalized screen"
            }
            if (mapping.slot !in 0..31) errors += "button ${mapping.label}: slot ${mapping.slot} outside 0..31"
            allSlots.put(mapping.slot, "button ${mapping.label}")?.let { previous ->
                errors += "touch slot ${mapping.slot} is shared by $previous and button ${mapping.label}"
            }
        }
        profile.stickMappings.forEach { mapping ->
            if (mapping.xNorm !in 0f..1f || mapping.yNorm !in 0f..1f) {
                errors += "stick ${mapping.label}: coordinates outside normalized screen"
            }
            if (mapping.radiusNorm !in 0.02f..0.5f) errors += "stick ${mapping.label}: radius ${mapping.radiusNorm} outside 0.02..0.5"
            if (mapping.deadzone !in 0f..0.9f) errors += "stick ${mapping.label}: deadzone ${mapping.deadzone} outside 0..0.9"
            if (mapping.sensitivity !in 0.1f..5f) errors += "stick ${mapping.label}: sensitivity ${mapping.sensitivity} outside 0.1..5"
            if (mapping.slot !in 0..31) errors += "stick ${mapping.label}: slot ${mapping.slot} outside 0..31"
            allSlots.put(mapping.slot, "stick ${mapping.label}")?.let { previous ->
                errors += "touch slot ${mapping.slot} is shared by $previous and stick ${mapping.label}"
            }
        }

        profile.touchMappings
            .groupBy { it.input.keyCode to it.input.scanCode }
            .filterValues { it.size > 1 }
            .forEach { (input, mappings) ->
                errors += "input keyCode=${input.first} scanCode=${input.second} has ${mappings.size} mappings (${mappings.joinToString { it.label }})"
            }

        return errors
    }
}
