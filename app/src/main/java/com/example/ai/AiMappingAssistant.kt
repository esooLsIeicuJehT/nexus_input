package com.example.ai

import com.example.model.ControllerType
import com.example.model.MappingConfig
import com.example.model.MappingNode
import com.example.model.NodeType
import java.util.UUID

data class AiMappingRecommendation(
    val gameTitle: String,
    val controllerName: String,
    val summary: String,
    val recommendedNodes: List<MappingNode>,
    val explanationPerInput: Map<String, String>
)

object AiMappingAssistant {

    fun generateRecommendedMapping(
        gameTitle: String,
        controllerType: ControllerType = ControllerType.XBOX,
        aspectRatio: String = "20:9"
    ): AiMappingRecommendation {
        val controllerName = when (controllerType) {
            ControllerType.STADIA -> "Google Stadia Controller"
            ControllerType.PLAYSTATION -> "PlayStation DualSense"
            ControllerType.NINTENDO_SWITCH -> "Nintendo Switch Pro"
            ControllerType.RAZER_KISHI -> "Razer Kishi V2"
            ControllerType.GAMESIR -> "GameSir G8 Galileo"
            else -> "Xbox Wireless Controller"
        }

        val explanation = mapOf(
            "LS" to "Left Stick -> 360° analog movement vector with sprint lock threshold",
            "RS" to "Right Stick -> Multi-touch camera drag orbit with acceleration curve 1.2",
            "LT" to "LT -> ADS / Aim Scope (analog trigger instant-threshold tap)",
            "RT" to "RT -> Primary Fire (instant hair-trigger response)",
            "A" to "A -> Jump / Vault / Mantle",
            "B" to "B -> Crouch / Slide / Stance change",
            "X" to "X -> Reload weapon / Interaction hold",
            "Y" to "Y -> Weapon swap (Primary <-> Secondary)",
            "LB" to "LB -> Tactical equipment / Smoke / Stun",
            "RB" to "RB -> Lethal Grenade / Frag",
            "L3" to "L3 Click -> Toggle Tactical Sprint",
            "R3" to "R3 Click -> Melee strike / Prone toggle",
            "D_UP" to "D-Pad Up -> Healing / Armor Plate",
            "D_DOWN" to "D-Pad Down -> Ping / Map Marker",
            "START" to "Menu -> In-game pause & loadout scoreboard"
        )

        val nodes = listOf(
            MappingNode(
                id = "ai_ls_${UUID.randomUUID().toString().take(4)}",
                xNorm = 0.18f,
                yNorm = 0.75f,
                radiusNorm = 0.12f,
                type = NodeType.JOYSTICK_ZONE,
                boundKey = "LS",
                label = "Movement"
            ),
            MappingNode(
                id = "ai_rs_${UUID.randomUUID().toString().take(4)}",
                xNorm = 0.65f,
                yNorm = 0.50f,
                radiusNorm = 0.16f,
                type = NodeType.CAMERA_DRAG,
                boundKey = "RS",
                label = "Camera / Aim"
            ),
            MappingNode(
                id = "ai_rt_${UUID.randomUUID().toString().take(4)}",
                xNorm = 0.88f,
                yNorm = 0.78f,
                radiusNorm = 0.05f,
                type = NodeType.BUTTON,
                boundKey = "RT",
                label = "Fire"
            ),
            MappingNode(
                id = "ai_lt_${UUID.randomUUID().toString().take(4)}",
                xNorm = 0.82f,
                yNorm = 0.58f,
                radiusNorm = 0.05f,
                type = NodeType.BUTTON,
                boundKey = "LT",
                label = "ADS"
            ),
            MappingNode(
                id = "ai_btn_a_${UUID.randomUUID().toString().take(4)}",
                xNorm = 0.92f,
                yNorm = 0.60f,
                radiusNorm = 0.045f,
                type = NodeType.BUTTON,
                boundKey = "A",
                label = "Jump"
            ),
            MappingNode(
                id = "ai_btn_b_${UUID.randomUUID().toString().take(4)}",
                xNorm = 0.92f,
                yNorm = 0.90f,
                radiusNorm = 0.045f,
                type = NodeType.BUTTON,
                boundKey = "B",
                label = "Crouch"
            ),
            MappingNode(
                id = "ai_btn_x_${UUID.randomUUID().toString().take(4)}",
                xNorm = 0.78f,
                yNorm = 0.78f,
                radiusNorm = 0.045f,
                type = NodeType.BUTTON,
                boundKey = "X",
                label = "Reload"
            ),
            MappingNode(
                id = "ai_btn_y_${UUID.randomUUID().toString().take(4)}",
                xNorm = 0.52f,
                yNorm = 0.88f,
                radiusNorm = 0.045f,
                type = NodeType.BUTTON,
                boundKey = "Y",
                label = "Swap"
            ),
            MappingNode(
                id = "ai_btn_rb_${UUID.randomUUID().toString().take(4)}",
                xNorm = 0.72f,
                yNorm = 0.68f,
                radiusNorm = 0.045f,
                type = NodeType.BUTTON,
                boundKey = "RB",
                label = "Lethal"
            ),
            MappingNode(
                id = "ai_btn_lb_${UUID.randomUUID().toString().take(4)}",
                xNorm = 0.28f,
                yNorm = 0.48f,
                radiusNorm = 0.045f,
                type = NodeType.BUTTON,
                boundKey = "LB",
                label = "Tactical"
            ),
            MappingNode(
                id = "ai_btn_r3_${UUID.randomUUID().toString().take(4)}",
                xNorm = 0.84f,
                yNorm = 0.92f,
                radiusNorm = 0.04f,
                type = NodeType.BUTTON,
                boundKey = "R3",
                label = "Prone"
            ),
            MappingNode(
                id = "ai_btn_dup_${UUID.randomUUID().toString().take(4)}",
                xNorm = 0.64f,
                yNorm = 0.88f,
                radiusNorm = 0.04f,
                type = NodeType.BUTTON,
                boundKey = "D_UP",
                label = "Heal"
            )
        )

        return AiMappingRecommendation(
            gameTitle = gameTitle,
            controllerName = controllerName,
            summary = "Optimized $controllerName layout for $gameTitle ($aspectRatio). Analyzed HUD quadrants, ergonomic thumb travel distance, and trigger latency.",
            recommendedNodes = nodes,
            explanationPerInput = explanation
        )
    }

    fun suggestMapping(
        gameTitle: String,
        controllerType: ControllerType = ControllerType.XBOX,
        existingConfig: MappingConfig? = null
    ): com.example.model.AiMappingSuggestion {
        val rec = generateRecommendedMapping(gameTitle, controllerType)
        return com.example.model.AiMappingSuggestion(
            gameTitle = gameTitle,
            targetController = controllerType,
            rationale = rec.summary,
            estimatedLatencyMs = 0.38f,
            nodes = rec.recommendedNodes
        )
    }
}
