package com.example.ui.nexus

import com.example.data.ControlystRepository
import com.example.data.entity.ConfigProfileEntity
import com.example.data.entity.GameEntity
import com.example.input.ProfileValidator
import com.example.model.MappingConfig
import com.example.service.MappingRuntimeState

/** Screen projections retain missing observations and invalid stored profiles as explicit states. */
internal enum class ProfileBrowserFilter(val label: String) { ALL("All"), GAMES("Games"), APPS("Apps"), RECENT("Recent") }
internal data class ProfileBrowserRow(
    val profile: ConfigProfileEntity,
    val game: GameEntity?,
    val config: MappingConfig?,
    val error: String?
)
internal object NexusScreenPresentation {
    fun profileRows(profiles: List<ConfigProfileEntity>, games: List<GameEntity>, query: String,
                    filter: ProfileBrowserFilter): List<ProfileBrowserRow> {
        val library = games.associateBy { it.packageName }
        val matching = profiles.filter { profile ->
            val game = library[profile.gamePackage]
            val matchesType = when (filter) {
                ProfileBrowserFilter.GAMES -> game?.isGameTag == true
                ProfileBrowserFilter.APPS -> game?.isGameTag == false
                else -> true
            }
            matchesType && listOf(profile.profileName, profile.gamePackage, game?.displayName.orEmpty())
                .any { it.contains(query.trim(), ignoreCase = true) }
        }
        val ordered = if (filter == ProfileBrowserFilter.RECENT) matching.sortedByDescending { it.updatedAt }
            else matching.sortedWith(compareBy({ library[it.gamePackage]?.displayName ?: it.gamePackage }, { it.profileName }))
        return ordered.map { profile ->
            try {
                val config = ControlystRepository.deserializeJsonToConfig(profile.jsonBlob)
                val errors = ProfileValidator.errors(config, profile.gamePackage, profile.id, requireBindings = false)
                require(errors.isEmpty()) { errors.joinToString("; ") }
                ProfileBrowserRow(profile, library[profile.gamePackage], config, null)
            } catch (error: Exception) {
                ProfileBrowserRow(profile, library[profile.gamePackage], null,
                    "Stored profile is invalid: ${error.message ?: error.javaClass.simpleName}")
            }
        }
    }

    fun unsavedLibraryGames(games: List<GameEntity>, profiles: List<ConfigProfileEntity>, query: String,
                            filter: ProfileBrowserFilter): List<GameEntity> = games.filter { game ->
        val typeMatches = when (filter) {
            ProfileBrowserFilter.GAMES -> game.isGameTag
            ProfileBrowserFilter.APPS -> !game.isGameTag
            ProfileBrowserFilter.RECENT -> false // No profile save timestamp exists for these rows.
            ProfileBrowserFilter.ALL -> true
        }
        typeMatches && profiles.none { it.gamePackage == game.packageName } &&
            (game.displayName.contains(query.trim(), true) || game.packageName.contains(query.trim(), true))
    }

    fun mappingStatus(runtime: MappingRuntimeState): String = when {
        runtime.error != null -> "Error — ${runtime.error}"
        !runtime.armed -> "Stopped"
        !runtime.backendReady -> "Armed · backend not ready"
        !runtime.targetForeground -> "Armed · waiting for game"
        else -> "Running · delivery needs device verification"
    }

    fun axisText(value: Float?): String = when {
        value == null -> "Not observed"
        !value.isFinite() -> "Invalid observation"
        else -> java.lang.String.format(java.util.Locale.ROOT, "%+.2f", value)
    }

    fun pressedButtonLabels(pressed: Set<String>, axes: Map<String, Float>): Set<String> = buildSet {
        val aliases = mapOf("BUTTON_A" to "A", "BUTTON_B" to "B", "BUTTON_X" to "X", "BUTTON_Y" to "Y",
            "BUTTON_L1" to "LB", "BUTTON_R1" to "RB", "BUTTON_L2" to "LT", "BUTTON_R2" to "RT",
            "BUTTON_THUMBL" to "L3", "BUTTON_THUMBR" to "R3", "BUTTON_START" to "START", "BUTTON_SELECT" to "SELECT",
            "DPAD_UP" to "↑", "DPAD_DOWN" to "↓", "DPAD_LEFT" to "←", "DPAD_RIGHT" to "→")
        pressed.forEach { key -> aliases[key]?.let(::add) }
        axes["HAT_X"]?.let { if (it < -.5f) add("←") else if (it > .5f) add("→") }
        axes["HAT_Y"]?.let { if (it < -.5f) add("↑") else if (it > .5f) add("↓") }
    }
}
