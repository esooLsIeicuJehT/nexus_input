package com.example

import androidx.activity.compose.setContent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.ui.MainAppViewModel
import com.example.ui.nexus.NexusAppShell
import com.example.ui.theme.NexusInputTheme
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Rendered layout fixtures, independent of controller/hardware acceptance. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NexusShellUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test @Config(qualifiers = "w412dp-h915dp-port-xhdpi")
    fun portraitKeepsOneVisibleBottomDock() = reviewRoutes("portrait")

    @Test @Config(qualifiers = "w915dp-h412dp-land-xhdpi")
    fun landscapeMapperKeepsOneVisibleBottomDock() = reviewRoutes("landscape")

    private fun reviewRoutes(size: String) {
        val store = ViewModelStore()
        lateinit var vm: MainAppViewModel
        try {
            compose.runOnUiThread {
                vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory
                    .getInstance(compose.activity.application))[MainAppViewModel::class.java]
                vm.selectTab("home")
                compose.activity.setContent {
                    NexusInputTheme {
                        NexusAppShell(vm, remember { SnackbarHostState() }, {})
                    }
                }
            }
            for (route in listOf("home", "system", "mapper")) {
                compose.onNodeWithTag("tab_$route").performClick()
                for (tab in listOf("home", "profiles", "mapper", "devices", "system")) {
                    compose.onAllNodesWithTag("tab_$tab").assertCountEquals(1)
                    compose.onNodeWithTag("tab_$tab").assertIsDisplayed()
                }
                compose.onRoot().captureRoboImage("build/outputs/ui-review/$size-$route.png",
                    RoborazziOptions(taskType = RoborazziTaskType.Record))
            }
        } finally { compose.runOnUiThread { store.clear() } }
    }
}
