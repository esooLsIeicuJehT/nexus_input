package com.example

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Launches the application's registered activity in both debug and release variants. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24,34])
class NexusLaunchTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun actualLaunchDisplaysBrandWithoutInventingBackendProgress() {
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("NEXUS INPUT").assertIsDisplayed()
        compose.onNodeWithText("Gamepad to Touch").assertIsDisplayed()
        compose.onNodeWithText("INITIALIZING SYSTEM…").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land-mdpi")
    fun actualShortLandscapeLaunchKeepsBrandAndPurposeVisible() {
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("NEXUS INPUT").assertIsDisplayed()
        compose.onNodeWithText("Gamepad to Touch").assertIsDisplayed()
    }
}
