package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.MappingForegroundService
import com.example.ui.MainAppViewModel
import com.example.ui.calibration.CalibrationScreen
import com.example.ui.community.CommunityShareScreen
import com.example.ui.crosshair.CrosshairStudioScreen
import com.example.ui.library.GameLibraryScreen
import com.example.ui.mapper.ScreenshotMapperScreen
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.overlay.FloatingOverlayHUD
import com.example.ui.root.KernelSuWebUiScreen
import com.example.ui.macro.MacroTimelineEditor
import com.example.ui.safety.GameSafetyScreen
import com.example.ui.theme.*
import com.example.ui.vip.VipMonetizationScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MainAppViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ControlystTheme {
                val onboardingStep by viewModel.onboardingStep.collectAsState()
                val currentTab by viewModel.currentTab.collectAsState()
                val snackMessage by viewModel.snackMessage.collectAsState()
                val activePrivilege by viewModel.activePrivilegeMethod.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(snackMessage) {
                    snackMessage?.let {
                        snackbarHostState.showSnackbar(it)
                        viewModel.clearSnack()
                    }
                }

                if (onboardingStep >= 0) {
                    OnboardingScreen(
                        viewModel = viewModel,
                        onFinish = { viewModel.completeOnboarding() }
                    )
                } else {
                    Scaffold(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DarkBackground),
                        topBar = {
                            TopAppBar(
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        ControlystLogoIcon(
                                            modifier = Modifier.size(26.dp),
                                            size = 26.dp,
                                            animated = true
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = "CONTROLYST",
                                            fontWeight = FontWeight.Black,
                                            color = TextPrimary,
                                            fontSize = 17.sp,
                                            letterSpacing = 3.sp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = ControlystCyan.copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, ControlystCyan.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                text = activePrivilege.badgeLabel,
                                                color = ControlystCyan,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                },
                                actions = {
                                    // Panic Kill-Switch in App Bar
                                    IconButton(
                                        onClick = {
                                            MappingForegroundService.triggerPanicKill(this@MainActivity)
                                            viewModel.showSnack("Panic Kill executed! All mapping halted.")
                                        },
                                        modifier = Modifier.testTag("appbar_panic_button")
                                    ) {
                                        Icon(
                                            Icons.Default.PowerSettingsNew,
                                            contentDescription = "Panic Kill",
                                            tint = AccentRose
                                        )
                                    }

                                    // Reset/Restart Onboarding button
                                    IconButton(
                                        onClick = { viewModel.restartOnboarding() },
                                        modifier = Modifier.testTag("appbar_help_button")
                                    ) {
                                        Icon(
                                            Icons.Default.HelpOutline,
                                            contentDescription = "Onboarding Guide",
                                            tint = TextSecondary
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = GraphiteFoundation)
                            )
                        },
                        bottomBar = {
                            NavigationBar(
                                containerColor = DarkSurface,
                                tonalElevation = 8.dp,
                                modifier = Modifier.navigationBarsPadding()
                            ) {
                                NavigationBarItem(
                                    selected = currentTab == "library",
                                    onClick = { viewModel.selectTab("library") },
                                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                                    label = { Text("Dashboard", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = ControlystCyan,
                                        selectedTextColor = ControlystCyan,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted,
                                        indicatorColor = ControlystViolet.copy(alpha = 0.25f)
                                    ),
                                    modifier = Modifier.testTag("tab_library")
                                )

                                NavigationBarItem(
                                    selected = currentTab == "mapper",
                                    onClick = { viewModel.selectTab("mapper") },
                                    icon = { Icon(Icons.Default.Tune, contentDescription = "Mapper") },
                                    label = { Text("Mapper", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = ControlystCyan,
                                        selectedTextColor = ControlystCyan,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted,
                                        indicatorColor = ControlystViolet.copy(alpha = 0.25f)
                                    ),
                                    modifier = Modifier.testTag("tab_mapper")
                                )

                                NavigationBarItem(
                                    selected = currentTab == "crosshair",
                                    onClick = { viewModel.selectTab("crosshair") },
                                    icon = { Icon(Icons.Default.CenterFocusStrong, contentDescription = "Overlays") },
                                    label = { Text("Overlays", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = ControlystCyan,
                                        selectedTextColor = ControlystCyan,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted,
                                        indicatorColor = ControlystViolet.copy(alpha = 0.25f)
                                    ),
                                    modifier = Modifier.testTag("tab_crosshair")
                                )

                                NavigationBarItem(
                                    selected = currentTab == "root_webui",
                                    onClick = { viewModel.selectTab("root_webui") },
                                    icon = { Icon(Icons.Default.Terminal, contentDescription = "Root WebUI") },
                                    label = { Text("Root WebUI", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = ControlystCyan,
                                        selectedTextColor = ControlystCyan,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted,
                                        indicatorColor = ControlystViolet.copy(alpha = 0.25f)
                                    ),
                                    modifier = Modifier.testTag("tab_root_webui")
                                )
                            }
                        },
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        containerColor = DarkBackground
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when (currentTab) {
                                "library" -> GameLibraryScreen(
                                    viewModel = viewModel,
                                    onNavigateToMapper = { viewModel.selectTab("mapper") }
                                )
                                "mapper" -> ScreenshotMapperScreen(viewModel = viewModel)
                                "crosshair" -> CrosshairStudioScreen(viewModel = viewModel)
                                "calibration" -> CalibrationScreen(viewModel = viewModel)
                                "community" -> CommunityShareScreen(viewModel = viewModel)
                                "vip" -> VipMonetizationScreen(viewModel = viewModel)
                                "root_webui" -> KernelSuWebUiScreen(
                                    viewModel = viewModel,
                                    onBack = { viewModel.selectTab("library") }
                                )
                                "macro" -> MacroTimelineEditor(
                                    onBack = { viewModel.selectTab("mapper") },
                                    onSaveMacro = { viewModel.showSnack("Saved Macro: ${it.name}") }
                                )
                                "safety" -> GameSafetyScreen(
                                    onBack = { viewModel.selectTab("library") }
                                )
                            }

                            // Interactive In-App Floating Overlay HUD Preview
                            FloatingOverlayHUD(
                                viewModel = viewModel,
                                onOpenMapper = { viewModel.selectTab("mapper") },
                                onOpenCrosshair = { viewModel.selectTab("crosshair") },
                                onOpenCalibration = { viewModel.selectTab("calibration") },
                                onOpenRootWebUi = { viewModel.selectTab("root_webui") }
                            )
                        }
                    }
                }
            }
        }
    }
}
