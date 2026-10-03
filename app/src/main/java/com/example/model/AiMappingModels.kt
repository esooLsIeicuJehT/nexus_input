package com.example.model

enum class HudElementCategory(val label: String) {
    COMBAT("Combat / Fire / Aim"),
    MOVEMENT("Movement / Stance"),
    ACTION("Actions / Abilities"),
    INTERACTION("Interact / Vehicle"),
    NAVIGATION("Minimap / Menu"),
    MOBA("MOBA Skills"),
    RACING("Racing Controls")
}

data class AiHudCandidate(
    val id: String,
    val xNorm: Float,
    val yNorm: Float,
    val widthNorm: Float,
    val heightNorm: Float,
    val confidence: Float,
    val predictedAction: String,
    val recommendedKey: String,
    val category: HudElementCategory,
    val isConfirmedByUser: Boolean = false,
    val isRejectedByUser: Boolean = false
)

enum class DiffStatus {
    UNCHANGED,
    MOVED,
    MISSING,
    NEW_DETECTED
}

data class ConfigDiffItem(
    val nodeLabel: String,
    val boundKey: String,
    val oldXNorm: Float,
    val oldYNorm: Float,
    val newXNorm: Float,
    val newYNorm: Float,
    val deltaDistance: Float,
    val status: DiffStatus,
    val isRepaired: Boolean = false
)

enum class GameGenre(val title: String, val defaultDesc: String) {
    FPS("First-Person Shooter", "Dual-stick aim/move, triggers for ADS and Fire"),
    TPS("Third-Person Shooter", "Wide camera drag, sprint lock and tactical abilities"),
    MOBA("MOBA", "Direct skill targeting, ability circles, shop quick-buy"),
    RACING("Racing / Driving", "Analog triggers for throttle/brake, d-pad or stick steering"),
    PLATFORMER("Platformer / 2D", "D-Pad directional control with jump and attack faces"),
    FIGHTING("Fighting / Brawler", "6-button arcade layout with directional special motions"),
    MMORPG("MMORPG", "Action bar skill wheel with camera orbit and target lock"),
    STRATEGY("Strategy / RTS", "Virtual mouse cursor drag, hotkeys for group selection"),
    EMULATOR("Retro Emulator", "Standard classic gamepad mapping (A/B/X/Y/L/R/Start/Select)"),
    CUSTOM("Custom Layout", "Completely unconstrained blank canvas")
}

data class AiMappingSuggestion(
    val gameTitle: String,
    val targetController: ControllerType,
    val rationale: String,
    val estimatedLatencyMs: Float,
    val nodes: List<MappingNode>
)

data class ConfigDiffResult(
    val unchangedCount: Int,
    val movedNodes: List<ConfigDiffItem>,
    val missingNodes: List<ConfigDiffItem>,
    val newDetectedNodes: List<ConfigDiffItem>
)
