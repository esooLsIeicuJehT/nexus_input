package com.example

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
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
import com.example.input.ControllerInputMonitor
import com.example.ui.MainAppViewModel
import com.example.ui.nexus.NexusAppShell
import com.example.ui.nexus.NexusSplashScreen
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.theme.NexusInputTheme
import kotlinx.coroutines.delay
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val viewModel: MainAppViewModel by viewModels()

    private val inputListener = object : android.hardware.input.InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) { viewModel.detectController() }
        override fun onInputDeviceChanged(deviceId: Int) { viewModel.detectController() }
        override fun onInputDeviceRemoved(deviceId: Int) {
            ControllerInputMonitor.onDeviceRemoved(deviceId)
            viewModel.detectController()
        }
    }

    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        viewModel.refreshPrivileges()
    }

    private val shizukuBinderDeadListener = Shizuku.OnBinderDeadListener {
        viewModel.refreshPrivileges()
    }

    private val shizukuPermissionResultListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        viewModel.refreshPrivileges()
    }

    private fun testingInput(source: Int, deviceSources: Int) =
        viewModel.currentTab.value in setOf("devices", "calibration", "mapper") &&
            com.example.input.ControllerSourceClassifier.accepts(source, deviceSources)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        ControllerInputMonitor.onMotionEvent(event)
        return if (testingInput(event.source, event.device?.sources ?: 0)) true else super.dispatchGenericMotionEvent(event)
    }

    // Android Activity's public input callback must forward unconsumed events to ComponentActivity.
    // AndroidX annotates its implementation as library-restricted; the framework override remains public.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        ControllerInputMonitor.onKeyEvent(event)
        return if (testingInput(event.source, event.device?.sources ?: 0)) true else super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener)
        Shizuku.addBinderDeadListener(shizukuBinderDeadListener)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionResultListener)

        getSystemService(android.hardware.input.InputManager::class.java)
            .registerInputDeviceListener(inputListener, android.os.Handler(android.os.Looper.getMainLooper()))
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
                            com.example.service.PanicKillSwitch.triggerPanic(this@MainActivity)
                            viewModel.showSnack("Emergency stop requested; inspect release status in System.")
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
        Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionResultListener)
        getSystemService(android.hardware.input.InputManager::class.java).unregisterInputDeviceListener(inputListener)
        super.onDestroy()
    }
}
