package com.example.ui.nexus

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
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
import com.example.ui.community.CommunityShareScreen
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
    NexusNavItem("profiles", "Profiles", Icons.Default.GridView),
    NexusNavItem("mapper", "Mapper", Icons.Default.CenterFocusStrong),
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
    val activePrivilege by viewModel.activePrivilegeMethod.collectAsState()
    val runtime by com.example.service.MappingRuntimeBridge.state.collectAsState()
    val configuration = LocalConfiguration.current
    val immersiveLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
        currentTab in setOf("mapper", "profile_detail")

    androidx.compose.runtime.LaunchedEffect(runtime.error, runtime.notice) {
        runtime.error?.let { snackbarHostState.showSnackbar(it) }
        runtime.notice?.let { snackbarHostState.showSnackbar(it) }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().background(GraphiteFoundation),
        topBar = {
            if (!immersiveLandscape) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(34.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF071827),
                                border = BorderStroke(1.dp, NexusCyan)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("N", color = NexusCyan, fontWeight = FontWeight.Black, fontSize = 18.sp)
                                }
                            }
                            Spacer(Modifier.width(9.dp))
                            Column {
                                Text(
                                    "NEXUS",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 2.sp
                                )
                                Text(
                                    "INPUT",
                                    color = NexusCyan,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 4.sp
                                )
                            }
                            Spacer(Modifier.width(9.dp))
                            Surface(
                                shape = RoundedCornerShape(7.dp),
                                color = NexusCyan.copy(alpha = 0.1f),
                                border = BorderStroke(1.dp, NexusCyan.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    runtime.backend?.let { if (runtime.backendReady) "$it READY" else "$it NOT READY" } ?: "DISARMED",
                                    color = NexusCyan,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
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
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = GraphiteFoundation)
                )
            }
        },
        bottomBar = {
            if (!immersiveLandscape) {
                NavigationBar(
                    containerColor = Color(0xFF06121F),
                    tonalElevation = 0.dp,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    nexusNavItems.forEach { item ->
                        val selected = routeBelongsToTab(currentTab, item.route)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { viewModel.selectTab(item.route) },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NexusCyan,
                                selectedTextColor = NexusCyan,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = NexusViolet.copy(alpha = 0.22f)
                            ),
                            modifier = Modifier.testTag("tab_${item.route}")
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = GraphiteFoundation
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
                "crosshair" -> CrosshairStudioScreen(viewModel)
                "calibration" -> CalibrationScreen(viewModel)
                "community" -> CommunityShareScreen(viewModel)
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

private fun routeBelongsToTab(route: String, tab: String): Boolean = when (tab) {
    "home" -> route == "home"
    "profiles" -> route == "profiles" || route == "library" || route == "profile_detail"
    "mapper" -> route == "mapper" || route == "macro"
    "devices" -> route == "devices" || route == "calibration"
    "system" -> route in setOf("system", "root_webui", "crosshair", "community", "vip", "safety")
    else -> false
}
