package com.example.ai

import com.example.model.AiHudCandidate
import com.example.model.ConfigDiffItem
import com.example.model.DiffStatus
import com.example.model.MappingConfig
import com.example.model.MappingNode
import kotlin.math.sqrt

object ConfigDiffEngine {

    /**
     * Compares an existing MappingConfig with newly detected HUD elements from an updated screenshot.
     */
    fun calculateDiff(
        oldConfig: MappingConfig,
        newHudElements: List<AiHudCandidate>,
        movementThresholdNorm: Float = 0.035f // > 3.5% screen distance considered moved
    ): List<ConfigDiffItem> {
        val diffList = mutableListOf<ConfigDiffItem>()
        val matchedHudIds = mutableSetOf<String>()

        for (node in oldConfig.buttons) {
            // Find closest candidate with matching or compatible action
            var bestMatch: AiHudCandidate? = null
            var minDistance = Float.MAX_VALUE

            for (hud in newHudElements) {
                val dx = node.xNorm - hud.xNorm
                val dy = node.yNorm - hud.yNorm
                val dist = sqrt(dx * dx + dy * dy)

                val keyMatches = hud.recommendedKey.equals(node.boundKey, ignoreCase = true) ||
                        hud.predictedAction.contains(node.label, ignoreCase = true)

                val weight = if (keyMatches) 0.6f else 1.0f
                val weightedDist = dist * weight

                if (weightedDist < minDistance) {
                    minDistance = weightedDist
                    bestMatch = hud
                }
            }

            if (bestMatch != null && minDistance < 0.25f) {
                matchedHudIds.add(bestMatch.id)
                val rawDx = bestMatch.xNorm - node.xNorm
                val rawDy = bestMatch.yNorm - node.yNorm
                val actualDistance = sqrt(rawDx * rawDx + rawDy * rawDy)

                if (actualDistance > movementThresholdNorm) {
                    diffList.add(
                        ConfigDiffItem(
                            nodeLabel = node.label.ifBlank { node.boundKey },
                            boundKey = node.boundKey,
                            oldXNorm = node.xNorm,
                            oldYNorm = node.yNorm,
                            newXNorm = bestMatch.xNorm,
                            newYNorm = bestMatch.yNorm,
                            deltaDistance = actualDistance,
                            status = DiffStatus.MOVED
                        )
                    )
                } else {
                    diffList.add(
                        ConfigDiffItem(
                            nodeLabel = node.label.ifBlank { node.boundKey },
                            boundKey = node.boundKey,
                            oldXNorm = node.xNorm,
                            oldYNorm = node.yNorm,
                            newXNorm = bestMatch.xNorm,
                            newYNorm = bestMatch.yNorm,
                            deltaDistance = actualDistance,
                            status = DiffStatus.UNCHANGED
                        )
                    )
                }
            } else {
                diffList.add(
                    ConfigDiffItem(
                        nodeLabel = node.label.ifBlank { node.boundKey },
                        boundKey = node.boundKey,
                        oldXNorm = node.xNorm,
                        oldYNorm = node.yNorm,
                        newXNorm = node.xNorm,
                        newYNorm = node.yNorm,
                        deltaDistance = 0f,
                        status = DiffStatus.MISSING
                    )
                )
            }
        }

        // Check for new detected controls not in old config
        for (hud in newHudElements) {
            if (!matchedHudIds.contains(hud.id)) {
                diffList.add(
                    ConfigDiffItem(
                        nodeLabel = hud.predictedAction,
                        boundKey = hud.recommendedKey,
                        oldXNorm = hud.xNorm,
                        oldYNorm = hud.yNorm,
                        newXNorm = hud.xNorm,
                        newYNorm = hud.yNorm,
                        deltaDistance = 0f,
                        status = DiffStatus.NEW_DETECTED
                    )
                )
            }
        }

        return diffList
    }

    /**
     * Applies repairs automatically by updating moved nodes without rebuilding the config.
     */
    fun repairExistingConfig(
        oldConfig: MappingConfig,
        diffItems: List<ConfigDiffItem>
    ): MappingConfig {
        val movedMap = diffItems
            .filter { it.status == DiffStatus.MOVED }
            .associateBy({ it.boundKey }, { it })

        val repairedNodes = oldConfig.buttons.map { node ->
            val diff = movedMap[node.boundKey]
            if (diff != null) {
                node.copy(xNorm = diff.newXNorm, yNorm = diff.newYNorm)
            } else {
                node
            }
        }

        return oldConfig.copy(
            buttons = repairedNodes,
            lastUpdated = System.currentTimeMillis()
        )
    }
}
