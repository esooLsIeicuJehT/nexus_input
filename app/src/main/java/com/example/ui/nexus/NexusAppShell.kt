package com.example.ui.nexus

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainAppViewModel
import com.example.ui.calibration.CalibrationScreen
import com.example.ui.profiles.LocalProfileFilesScreen
import com.example.ui.crosshair.CrosshairStudioScreen
import com.example.ui.macro.MacroTimelineEditor
import com.example.ui.root.KernelSuWebUiScreen
import com.example.ui.safety.GameSafetyScreen
import com.example.ui.theme.*

private data class NexusNavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val nexusNavItems = listOf(
    NexusNavItem("home", "Home", Icons.Default.Home),
    NexusNavItem("profiles", "Profiles", Icons.Default.Layers),
    NexusNavItem("mapper", "Mapper", Icons.Default.Map),
    NexusNavItem("devices", "Devices", Icons.Default.SportsEsports),
    NexusNavItem("system", "System", Icons.Default.Settings)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexusAppShell(
    viewModel: MainAppViewModel,
    snackbarHostState: SnackbarHostState,
    onPanicKill: () -> Unit
) {
    val currentTab by viewModel.currentTab.collectAsState()
    val runtime by com.example.service.MappingRuntimeBridge.state.collectAsState()
    val configuration = LocalConfiguration.current
    val immersiveLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
        currentTab in setOf("mapper", "profile_detail")

    androidx.compose.runtime.LaunchedEffect(runtime.error, runtime.notice) {
        runtime.error?.let { snackbarHostState.showSnackbar(it) }
        runtime.notice?.let { snackbarHostState.showSnackbar(it) }
    }
    NexusPageTheme(currentTab) {
        val accent = LocalNexusAccent.current
        Box(Modifier.fillMaxSize()) {
            NexusAtmosphere()
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    if (!immersiveLandscape) {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ControlystLogoIcon(size = 42.dp)
                                    Spacer(Modifier.width(9.dp))
                                    Column {
                                        Text(
                                            "NEXUS INPUT",
                                            color = TextPrimary,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.2.sp
                                        )
                                        NexusStatusChip(
                                            text = runtime.backend?.let { if (runtime.backendReady) "$it READY" else "$it NOT READY" } ?: "DISARMED"
                                        )
                                    }
                                    Spacer(Modifier.width(9.dp))
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = onPanicKill,
                                    modifier = Modifier.testTag("appbar_panic_button")
                                ) {
                                    Icon(Icons.Default.PowerSettingsNew, contentDescription = "Panic kill", tint = AccentRose)
                                }
                                IconButton(
                                    onClick = { viewModel.restartOnboarding() },
                                    modifier = Modifier.testTag("appbar_help_button")
                                ) {
                                    Icon(Icons.Default.HelpOutline, contentDescription = "Setup guide", tint = TextSecondary)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )
                    }
                },
                bottomBar = {
                        NavigationBar(
                            containerColor = Color(0xC0081423),
                            tonalElevation = 0.dp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                .nexusGlass(22.dp, accent).padding(horizontal = 3.dp),
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            nexusNavItems.forEach { item ->
                                val selected = nexusMainTabForRoute(currentTab) == item.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(item.route) },
                                    icon = {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Box(Modifier.width(30.dp).height(2.dp)
                                                .background(if (selected) accent else Color.Transparent, RoundedCornerShape(4.dp)))
                                            Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(23.dp))
                                        }
                                    },
                                    label = { Text(item.label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = accent,
                                        selectedTextColor = accent,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted,
                                        indicatorColor = Color.Transparent
                                    ),
                                    modifier = Modifier.testTag("tab_${item.route}")
                                )
                            }
                        }
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = Color.Transparent
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentTab) {
                        "home" -> NexusHomeScreen(viewModel, viewModel::selectTab)
                        "profiles", "library" -> NexusProfilesScreen(viewModel, viewModel::selectTab)
                        "profile_detail" -> NexusProfileDetailRoute(viewModel, viewModel::selectTab)
                        "mapper" -> NexusMapperRoute(viewModel)
                        "devices" -> NexusDevicesRoute(viewModel, viewModel::selectTab)
                        "system" -> NexusSystemScreen(viewModel, viewModel::selectTab)
                        "fps" -> com.example.ui.frames.FrameOverlayScreen(viewModel)
                        "crosshair" -> CrosshairStudioScreen(viewModel)
                        "calibration" -> CalibrationScreen(viewModel)
                        "profile_files" -> LocalProfileFilesScreen(viewModel)
                        "root_webui" -> KernelSuWebUiScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.selectTab("system") }
                        )
                        "macro" -> MacroTimelineEditor(
                            viewModel = viewModel,
                            onBack = { viewModel.selectTab("mapper") }
                        )
                        "safety" -> GameSafetyScreen(onBack = { viewModel.selectTab("system") })
                        else -> NexusHomeScreen(viewModel, viewModel::selectTab)
                    }


                }
            }
        }
    }
}
