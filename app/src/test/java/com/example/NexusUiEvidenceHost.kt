package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.ViewModelProvider
import com.example.service.MappingRuntimeBridge
import com.example.service.PanicKillSwitch
import com.example.ui.MainAppViewModel
import org.junit.After
import org.junit.Before
import org.junit.Rule

/** Uses the registered activity and its own ViewModel; no controller/backend readings are fabricated. */
abstract class NexusUiEvidenceHost {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    protected lateinit var viewModel: MainAppViewModel

    @Before fun observeTheActualActivity() {
        compose.mainClock.autoAdvance = false
        MappingRuntimeBridge.disarm()
        PanicKillSwitch.resetPanic()
        compose.runOnUiThread {
            viewModel = ViewModelProvider(compose.activity)[MainAppViewModel::class.java]
        }
    }

    protected fun openShell() {
        // Advance the real splash handoff and then retain a fixed animation frame for evidence.
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        // Also observe the actual handoff: a fixed frame advance alone is not evidence
        // that Activity/Looper work and the shell composition have completed.
        awaitObservedState { compose.onAllNodesWithTag("tab_home").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab_home").assertIsDisplayed()
    }

    protected fun openRoute(route: String) {
        compose.runOnUiThread { viewModel.selectTab(route) }
        settleUiFrame()
    }

    protected fun settleUiFrame() {
        // The production accent transition is 300 ms; capture its settled page color.
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
    }

    protected fun awaitObservedState(condition: () -> Boolean) {
        // Room/probe results arrive from actual background work. Do not substitute a test value
        // when it has not completed; allow frames and fail explicitly at the bounded deadline.
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.mainClock.advanceTimeBy(16)
            condition()
        }
        settleUiFrame()
    }

    @After fun clearOnlyProcessLocalStopState() {
        MappingRuntimeBridge.disarm()
        PanicKillSwitch.resetPanic()
    }
}
