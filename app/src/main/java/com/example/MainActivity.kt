package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.service.MappingForegroundService
import com.example.ui.MainAppViewModel
import com.example.ui.nexus.NexusAppShell
import com.example.ui.nexus.NexusSplashScreen
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.theme.NexusInputTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val viewModel: MainAppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NexusInputTheme {
                val onboardingStep by viewModel.onboardingStep.collectAsState()
                val currentTab by viewModel.currentTab.collectAsState()
                val snackMessage by viewModel.snackMessage.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }
                var showSplash by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    if (currentTab == "library") viewModel.selectTab("home")
                    // This is a minimum visual handoff delay, not a claim that every
                    // privilege/backend probe has completed. Those states remain live
                    // and are surfaced explicitly by the dashboard after launch.
                    delay(850)
                    showSplash = false
                }

                LaunchedEffect(snackMessage) {
                    snackMessage?.let { message ->
                        snackbarHostState.showSnackbar(message)
                        viewModel.clearSnack()
                    }
                }

                when {
                    showSplash -> NexusSplashScreen()
                    onboardingStep >= 0 -> OnboardingScreen(
                        viewModel = viewModel,
                        onFinish = { viewModel.completeOnboarding() }
                    )
                    else -> NexusAppShell(
                        viewModel = viewModel,
                        snackbarHostState = snackbarHostState,
                        onPanicKill = {
                            MappingForegroundService.triggerPanicKill(this@MainActivity)
                            viewModel.showSnack("Panic kill executed. Mapping halted.")
                        }
                    )
                }
            }
        }
    }
}
