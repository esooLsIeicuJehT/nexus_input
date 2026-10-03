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
import androidx.compose.runtime.remember
import com.example.service.MappingForegroundService
import com.example.ui.MainAppViewModel
import com.example.ui.nexus.NexusAppShell
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.theme.NexusInputTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainAppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NexusInputTheme {
                val onboardingStep by viewModel.onboardingStep.collectAsState()
                val snackMessage by viewModel.snackMessage.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(snackMessage) {
                    snackMessage?.let { message ->
                        snackbarHostState.showSnackbar(message)
                        viewModel.clearSnack()
                    }
                }

                if (onboardingStep >= 0) {
                    OnboardingScreen(
                        viewModel = viewModel,
                        onFinish = { viewModel.completeOnboarding() }
                    )
                } else {
                    NexusAppShell(
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
