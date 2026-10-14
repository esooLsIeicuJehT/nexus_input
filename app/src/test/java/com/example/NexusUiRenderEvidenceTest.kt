package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.example.data.BuiltInProfiles
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rendered Android-layout evidence, not approved reference goldens or physical-device acceptance.
 * All captures use the actual application's empty/disconnected/unavailable observations. Existing
 * NexusShellUiTest captures Home, System and Mapper; this suite covers its missing screen states.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-port-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
class NexusUiRenderEvidenceTest : NexusUiEvidenceHost() {
    private fun capture(name: String) {
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/ui-reference-pass/$name.png",
            RoborazziOptions(taskType = RoborazziTaskType.Record))
    }

    @Test fun actualColdStartSplash() {
        compose.onNodeWithText("NEXUS INPUT").assertIsDisplayed()
        compose.onNodeWithText("Gamepad to Touch").assertIsDisplayed()
        capture("splash-portrait")
    }

    @Test fun actualSavedLibraryShowsTheShippedUnverifiedPreset() {
        openShell()
        openRoute("profiles")
        awaitObservedState { viewModel.profiles.value.any { it.id == BuiltInProfiles.DELTA_FORCE_PROFILE_ID } }
        compose.onNodeWithText(BuiltInProfiles.deltaForce.profileName).assertIsDisplayed()
        capture("profiles-shipped-unverified-preset-portrait")
    }

    @Test fun actualEmptyGamesFilterDoesNotManufactureLibraryEntries() {
        openShell()
        openRoute("profiles")
        awaitObservedState { viewModel.profiles.value.any { it.id == BuiltInProfiles.DELTA_FORCE_PROFILE_ID } }
        compose.onNodeWithText("Games").performClick()
        awaitObservedState { compose.onAllNodesWithTag("profiles_empty_state").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("profiles_empty_state").assertIsDisplayed()
        capture("profiles-empty-games-filter-portrait")
    }

    @Test fun actualDisconnectedControllerTester() {
        openShell()
        openRoute("devices")
        compose.onNodeWithTag("devices_connection_status").assertIsDisplayed()
            .assertTextContains("No matching physical input devices")
        capture("devices-no-controller-portrait")
    }

    @Test fun actualCalibrationWithoutControllerSamples() {
        openShell()
        openRoute("calibration")
        capture("calibration-no-controller-portrait")
    }

    @Test fun profileDetailWithoutAnInventedSelectedGame() {
        openShell()
        openRoute("profile_detail")
        capture("profile-detail-no-game-portrait")
    }

    @Test @Config(qualifiers = "w915dp-h412dp-land-xhdpi")
    fun landscapeProfileDetailWithoutAnInventedSelectedGame() {
        openShell()
        openRoute("profile_detail")
        compose.onNodeWithTag("tab_profiles").assertIsDisplayed().assertIsSelected()
        capture("profile-detail-no-game-landscape")
    }

    @Test fun realReadOnlyModuleLauncherWithoutRootClaims() {
        openShell()
        openRoute("root_webui")
        compose.onNodeWithText("Open KernelSU WebUI").assertIsDisplayed()
        capture("module-launcher-portrait")
    }

    @Test fun actualPanicWithoutCaptureServiceRemainsUnconfirmed() {
        openShell()
        compose.onNodeWithTag("appbar_panic_button").performClick()
        openRoute("system")
        scrollIntoView("system_panic_status").assertIsDisplayed()
        capture("system-panic-unconfirmed-portrait")
    }
}
