package com.example

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.service.ControlystAccessibilityService
import com.example.data.BuiltInProfiles
import com.example.service.MappingRuntimeBridge
import com.example.service.PanicKillSwitch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Actual activity/state/action regressions; these do not establish hardware injection behavior. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-port-xhdpi")
class NexusUiRegressionTest : NexusUiEvidenceHost() {
    @Test fun secondaryScreensRetainTheirOneCorrectParentTab() {
        openShell()
        val parents = linkedMapOf(
            "profile_detail" to "profiles",
            "macro" to "mapper",
            "calibration" to "devices",
            "root_webui" to "system"
        )
        for ((route, parent) in parents) {
            openRoute(route)
            for (tab in listOf("home", "profiles", "mapper", "devices", "system")) {
                compose.onAllNodesWithTag("tab_$tab").assertCountEquals(1)
                val node = compose.onNodeWithTag("tab_$tab").assertIsDisplayed()
                if (tab == parent) node.assertIsSelected() else node.assertIsNotSelected()
            }
        }
    }

    @Test fun emptyGamesFilterAndDisconnectedDevicesStayObservable() {
        openShell()
        compose.onNodeWithTag("tab_profiles").performClick()
        awaitObservedState { viewModel.profiles.value.any { it.id == BuiltInProfiles.DELTA_FORCE_PROFILE_ID } }
        compose.onNodeWithText("Games").performClick()
        settleUiFrame()
        compose.onNodeWithTag("profiles_empty_state").assertIsDisplayed()
        compose.onNodeWithTag("tab_devices").performClick()
        settleUiFrame()
        compose.onNodeWithTag("devices_connection_status").assertIsDisplayed()
            .assertTextContains("No matching physical input devices")
        assertFalse(viewModel.controllerProfile.value.connected)
    }

    @Test fun panicActionInvokesTheRealStopPathAndDisplaysItsUnconfirmedRelease() {
        openShell()
        assertNull("This host test has no running capture service", ControlystAccessibilityService.getInstance())
        compose.onNodeWithTag("appbar_panic_button").performClick()
        val panic = PanicKillSwitch.state.value
        assertTrue(panic.isKilled)
        assertFalse(panic.releaseConfirmed)
        assertNotNull(panic.error)
        assertFalse(MappingRuntimeBridge.state.value.armed)

        compose.onNodeWithTag("tab_system").performClick()
        settleUiFrame()
        compose.onNodeWithTag("system_panic_status").performScrollTo().assertIsDisplayed()
            .assertTextContains("Panic release NOT confirmed")
        compose.onNodeWithTag("system_panic_error").performScrollTo().assertIsDisplayed()
            .assertTextContains(panic.error!!)
    }

    @Test fun screenshotAndGameDependentActionsCannotInventMappingInput() {
        openShell()
        openRoute("mapper")
        assertTrue(viewModel.activeConfig.value.gamePackage.isBlank())
        assertNull(viewModel.screenshot.value)
        compose.onNodeWithTag("mapper_add_input").assertIsNotEnabled()
        compose.onNodeWithTag("mapper_save").assertIsNotEnabled()
        compose.onNodeWithTag("mapper_more").performClick()
        settleUiFrame()
        compose.onNodeWithTag("mapper_hud_scan").assertIsNotEnabled()
        compose.onNodeWithTag("mapper_capture").assertIsNotEnabled()
        assertTrue(viewModel.aiHudCandidates.value.isEmpty())
        assertTrue(viewModel.activeConfig.value.buttons.isEmpty())
    }
}
