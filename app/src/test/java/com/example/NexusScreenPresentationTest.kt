package com.example

import android.view.InputDevice
import com.example.data.ControlystRepository
import com.example.data.entity.ConfigProfileEntity
import com.example.data.entity.GameEntity
import com.example.model.MappingConfig
import com.example.model.PrivilegeMethod
import com.example.service.MappingRuntimeState
import com.example.ui.nexus.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.InputDeviceBuilder

/** Explicit Android/Room-format fixtures; no physical input or in-game delivery is verified. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NexusScreenPresentationTest {
    private fun profile(id: String, pkg: String, name: String, updated: Long = 1L): ConfigProfileEntity {
        val config = MappingConfig(id = id, profileName = name, gamePackage = pkg, gameTitle = "Fixture title")
        return ConfigProfileEntity(id, pkg, name, ControlystRepository.serializeConfigToJson(config), updatedAt = updated)
    }

    @Test fun profileSearchUsesSavedNamesPackagesAndActualLibraryTitles() {
        val shooter = profile("a", "fixture.shooter", "Precision")
        val app = profile("b", "fixture.editor", "Brush")
        val games = listOf(GameEntity("fixture.shooter", "Observed game fixture"), GameEntity("fixture.editor", "Editor fixture", isGameTag = false))
        assertEquals(listOf("a"), NexusScreenPresentation.profileRows(listOf(shooter, app), games, " observed GAME ", ProfileBrowserFilter.ALL).map { it.profile.id })
        assertEquals(listOf("b"), NexusScreenPresentation.profileRows(listOf(shooter, app), games, "editor", ProfileBrowserFilter.ALL).map { it.profile.id })
        assertEquals(listOf("a"), NexusScreenPresentation.profileRows(listOf(shooter, app), games, "precision", ProfileBrowserFilter.ALL).map { it.profile.id })
    }

    @Test fun typeFiltersRequireRealLibraryMetadataAndRecentUsesSavedTimestamp() {
        val game = profile("game", "fixture.game", "A", 3)
        val app = profile("app", "fixture.app", "B", 9)
        val orphan = profile("orphan", "fixture.unknown", "C", 5)
        val profiles = listOf(game, app, orphan)
        val library = listOf(GameEntity("fixture.game", "Game"), GameEntity("fixture.app", "App", isGameTag = false))
        assertEquals(listOf("game"), NexusScreenPresentation.profileRows(profiles, library, "", ProfileBrowserFilter.GAMES).map { it.profile.id })
        assertEquals(listOf("app"), NexusScreenPresentation.profileRows(profiles, library, "", ProfileBrowserFilter.APPS).map { it.profile.id })
        assertEquals(listOf("app", "orphan", "game"), NexusScreenPresentation.profileRows(profiles, library, "", ProfileBrowserFilter.RECENT).map { it.profile.id })
        assertEquals(3, NexusScreenPresentation.profileRows(profiles, library, "", ProfileBrowserFilter.ALL).size)
    }

    @Test fun unsavedLibraryEntriesRespectTypeSearchAndDoNotInventRecentSaveDates() {
        val saved = profile("saved", "fixture.saved", "Saved")
        val games = listOf(GameEntity("fixture.saved", "Saved game"), GameEntity("fixture.game", "Unsaved game"),
            GameEntity("fixture.app", "Unsaved editor", isGameTag = false))
        assertEquals(listOf("fixture.game"), NexusScreenPresentation.unsavedLibraryGames(games, listOf(saved), "", ProfileBrowserFilter.GAMES).map { it.packageName })
        assertEquals(listOf("fixture.app"), NexusScreenPresentation.unsavedLibraryGames(games, listOf(saved), " editor ", ProfileBrowserFilter.APPS).map { it.packageName })
        assertTrue(NexusScreenPresentation.unsavedLibraryGames(games, listOf(saved), "", ProfileBrowserFilter.RECENT).isEmpty())
    }

    @Test fun invalidStoredProfilesRemainVisibleAndAreNeverProjectedAsUsable() {
        val invalid = profile("broken", "fixture.game", "Broken").copy(jsonBlob = "{}")
        val wrongIdentity = profile("other", "fixture.game", "Other").copy(id = "mismatched")
        val rows = NexusScreenPresentation.profileRows(listOf(invalid, wrongIdentity), emptyList(), "", ProfileBrowserFilter.ALL)
        assertEquals(2, rows.size)
        rows.forEach { assertNull(it.config); assertNotNull(it.error); assertTrue(it.error!!.startsWith("Stored profile is invalid:")) }
    }

    @Test fun backendAndControllerLayoutAreTakenFromExactPersistedConfig() {
        val source = profile("saved", "fixture.game", "Profile")
        val config = ControlystRepository.deserializeJsonToConfig(source.jsonBlob).copy(preferredBackend = PrivilegeMethod.SHIZUKU)
        val stored = source.copy(jsonBlob = ControlystRepository.serializeConfigToJson(config))
        val row = NexusScreenPresentation.profileRows(listOf(stored), emptyList(), "", ProfileBrowserFilter.ALL).single()
        assertEquals(config, row.config); assertNull(row.error); assertNull(row.game)
    }

    @Test fun runtimeStatusNeverTurnsAvailableOrArmedIntoDeliverySuccess() {
        assertEquals("Stopped", NexusScreenPresentation.mappingStatus(MappingRuntimeState()))
        val armed = MappingRuntimeState(armed = true, backend = PrivilegeMethod.SHIZUKU)
        assertEquals("Armed · backend not ready", NexusScreenPresentation.mappingStatus(armed))
        assertEquals("Armed · waiting for game", NexusScreenPresentation.mappingStatus(armed.copy(backendReady = true)))
        assertTrue(NexusScreenPresentation.mappingStatus(armed.copy(backendReady = true, targetForeground = true)).contains("needs device verification"))
        assertEquals("Error — denied", NexusScreenPresentation.mappingStatus(armed.copy(backendReady = true, targetForeground = true, error = "denied")))
    }

    @Test fun missingOrInvalidAxesAreExplicitAndObservedZeroRemainsDistinct() {
        assertEquals("Not observed", NexusScreenPresentation.axisText(null))
        assertEquals("Invalid observation", NexusScreenPresentation.axisText(Float.NaN))
        assertEquals("Invalid observation", NexusScreenPresentation.axisText(Float.POSITIVE_INFINITY))
        assertEquals("+0.00", NexusScreenPresentation.axisText(0f))
        assertEquals("-0.65", NexusScreenPresentation.axisText(-.65f))
    }

    @Test fun onlyActualButtonsAndHatDirectionsLightControllerIndicators() {
        assertTrue(NexusScreenPresentation.pressedButtonLabels(emptySet(), emptyMap()).isEmpty())
        assertEquals(setOf("A", "LB", "L3", "R3", "START", "SELECT", "↑", "←"), NexusScreenPresentation.pressedButtonLabels(
            setOf("BUTTON_A", "BUTTON_L1", "BUTTON_THUMBL", "BUTTON_THUMBR", "BUTTON_START", "BUTTON_SELECT", "UNRECOGNIZED"),
            mapOf("HAT_X" to -1f, "HAT_Y" to -1f)))
        assertTrue(NexusScreenPresentation.pressedButtonLabels(emptySet(), mapOf("HAT_X" to 0f, "HAT_Y" to Float.NaN)).isEmpty())
    }

    @Test fun physicalInventoryFilterDoesNotInventControllerCapabilities() {
        val gamepad = InputDeviceBuilder.newBuilder().setId(91).setName("Fixture gamepad").setDescriptor("fixture-gamepad")
            .setSources(InputDevice.SOURCE_GAMEPAD).build()
        val mouse = InputDeviceBuilder.newBuilder().setId(92).setName("Fixture mouse").setDescriptor("fixture-mouse")
            .setSources(InputDevice.SOURCE_MOUSE).build()
        val touchscreen = InputDeviceBuilder.newBuilder().setId(93).setName("Fixture touchscreen").setDescriptor("fixture-touch")
            .setSources(InputDevice.SOURCE_TOUCHSCREEN).build()
        assertTrue(isNexusPhysicalInput(gamepad)); assertTrue(isNexusPhysicalInput(mouse)); assertFalse(isNexusPhysicalInput(touchscreen))
    }
}
