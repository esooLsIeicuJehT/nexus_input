package com.example.ui.nexus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.GameEntity
import com.example.ui.MainAppViewModel
import com.example.ui.theme.*

private val NexusPanelShape = RoundedCornerShape(18.dp)
private val NexusPanelBorder = BorderStroke(1.dp, DarkSurfaceBorder)

@Composable
private fun NexusPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = NexusPanelShape,
        color = DarkSurface.copy(alpha = 0.96f),
        border = NexusPanelBorder,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun SectionTitle(kicker: String, title: String) {
    Column {
        Text(
            text = kicker.uppercase(),
            color = NexusCyan,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.3.sp
        )
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MetricCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    NexusPanel(modifier = modifier) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(8.dp))
        Text(value, color = accent, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text(label, color = TextMuted, fontSize = 9.sp)
    }
}

@Composable
fun NexusHomeScreen(
    viewModel: MainAppViewModel,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val games by viewModel.games.collectAsState()
    val selectedGame by viewModel.selectedGame.collectAsState()
    val activeConfig by viewModel.activeConfig.collectAsState()
    val controller by viewModel.controllerProfile.collectAsState()
    val activePrivilege by viewModel.activePrivilegeMethod.collectAsState()
    val privilegeResults by viewModel.privilegeResults.collectAsState()
    val latency by viewModel.touchLatencyResult.collectAsState()

    val activeGame = selectedGame ?: games.firstOrNull { it.packageName == activeConfig.gamePackage }
    val activeProbe = privilegeResults.firstOrNull { it.method == activePrivilege }
    val engineReady = activeProbe?.isDetected == true
    val latencyLabel = when {
        latency.isTesting -> "Testing"
        latency.roundTripMs > 0 -> "Legacy ${latency.roundTripMs} ms"
        else -> "Not measured"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GraphiteFoundation)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, if (engineReady) NexusCyan else AccentAmber),
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF11133D), Color(0xFF071827), Color(0xFF10143A))
                        )
                    )
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(SignatureGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Text("N", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (engineReady) "ENGINE READY" else "ENGINE NEEDS SETUP",
                        color = if (engineReady) AccentGreen else AccentAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text("Nexus Input", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text(
                        "Backend: ${activePrivilege.badgeLabel}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = { onNavigate("system") }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "System details", tint = NexusCyan)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                icon = Icons.Default.Speed,
                label = "Latency status",
                value = latencyLabel,
                accent = if (latency.roundTripMs > 0) AccentAmber else NexusCyan,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                icon = Icons.Default.Tune,
                label = "Configured polling",
                value = "${controller.pollingRateHz} Hz",
                accent = NexusVioletLight,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                icon = Icons.Default.ControlPointDuplicate,
                label = "Mapped controls",
                value = activeConfig.buttons.size.toString(),
                accent = AccentGreen,
                modifier = Modifier.weight(1f)
            )
        }

        NexusPanel(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.SportsEsports,
                    contentDescription = null,
                    tint = if (controller.connected) NexusCyan else TextMuted,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("CONNECTED INPUT", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(
                        controller.deviceName ?: "No controller detected",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (controller.connected) controller.type.displayName else "Connect a gamepad, mouse, or keyboard",
                        color = if (controller.connected) AccentGreen else TextSecondary,
                        fontSize = 10.sp
                    )
                }
                FilledTonalButton(onClick = {
                    viewModel.detectController()
                    onNavigate("devices")
                }) {
                    Text("Devices", fontSize = 10.sp)
                }
            }
        }

        NexusPanel(modifier = Modifier.fillMaxWidth()) {
            Text("ACTIVE PROFILE", color = NexusCyan, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        activeConfig.gameTitle.ifBlank { activeGame?.displayName ?: "No game selected" },
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(activeConfig.profileName, color = TextSecondary, fontSize = 10.sp)
                }
                OutlinedButton(onClick = { onNavigate("mapper") }) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Map")
                }
                Spacer(Modifier.width(6.dp))
                Button(
                    enabled = activeGame != null,
                    onClick = { activeGame?.let { viewModel.launchGameWithMapping(it, context) } },
                    colors = ButtonDefaults.buttonColors(containerColor = NexusViolet)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Launch")
                }
            }
        }

        SectionTitle("System integrations", "Backend status")
        privilegeResults.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { result ->
                    NexusPanel(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (result.isDetected) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (result.isDetected) AccentGreen else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(result.method.title, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(result.statusDetail, color = TextMuted, fontSize = 8.sp, maxLines = 2)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        SectionTitle("Quick access", "Command center")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAction("Profiles", Icons.Default.GridView, Modifier.weight(1f)) { onNavigate("profiles") }
            QuickAction("Mapper", Icons.Default.CenterFocusStrong, Modifier.weight(1f)) { onNavigate("mapper") }
            QuickAction("Devices", Icons.Default.SportsEsports, Modifier.weight(1f)) { onNavigate("devices") }
            QuickAction("System", Icons.Default.Settings, Modifier.weight(1f)) { onNavigate("system") }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = DarkSurface,
        border = NexusPanelBorder
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = NexusCyan, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(5.dp))
            Text(label, color = TextSecondary, fontSize = 9.sp)
        }
    }
}

@Composable
fun NexusProfilesScreen(
    viewModel: MainAppViewModel,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val games by viewModel.games.collectAsState()
    val selectedGame by viewModel.selectedGame.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(GraphiteFoundation),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionTitle("Profiles", "Game library") }

        if (games.isEmpty()) {
            item {
                NexusPanel(Modifier.fillMaxWidth()) {
                    Text("No game profiles yet", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("Add a game to create a mapping profile.", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }

        items(games, key = { it.packageName }) { game ->
            val selected = selectedGame?.packageName == game.packageName
            GameProfileCard(
                game = game,
                selected = selected,
                onSelect = {
                    viewModel.selectGame(game)
                    onNavigate("profile_detail")
                },
                onLaunch = {
                    viewModel.selectGame(game)
                    viewModel.launchGameWithMapping(game, context)
                },
                onMap = {
                    viewModel.selectGame(game)
                    onNavigate("mapper")
                }
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun GameProfileCard(
    game: GameEntity,
    selected: Boolean,
    onSelect: () -> Unit,
    onLaunch: () -> Unit,
    onMap: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        shape = RoundedCornerShape(16.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, if (selected) NexusCyan else DarkSurfaceBorder)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)).background(SignatureGradient),
                contentAlignment = Alignment.Center
            ) {
                Text(game.displayName.take(1).uppercase(), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(game.displayName, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(game.packageName, color = TextMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Text(
                    if (selected) "ACTIVE PROFILE" else "Tap for profile details",
                    color = if (selected) AccentGreen else NexusCyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onMap) {
                Icon(Icons.Default.Tune, contentDescription = "Edit mapping", tint = NexusCyan)
            }
            IconButton(onClick = onLaunch) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Launch game", tint = NexusVioletLight)
            }
        }
    }
}

@Composable
fun NexusProfileDetailScreen(
    viewModel: MainAppViewModel,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val game by viewModel.selectedGame.collectAsState()
    val config by viewModel.activeConfig.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().background(GraphiteFoundation).verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TextButton(onClick = { onNavigate("profiles") }) {
            Icon(Icons.Default.ArrowBack, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Profiles")
        }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, NexusViolet)
        ) {
            Column(
                modifier = Modifier.background(
                    Brush.horizontalGradient(listOf(Color(0xFF0B3348), Color(0xFF211447), Color(0xFF071827)))
                ).padding(18.dp)
            ) {
                Text("GAME PROFILE", color = NexusCyan, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                Text(game?.displayName ?: config.gameTitle.ifBlank { "No game selected" }, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text(game?.packageName ?: config.gamePackage, color = TextSecondary, fontSize = 10.sp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = game != null,
                onClick = { game?.let { viewModel.launchGameWithMapping(it, context) } },
                colors = ButtonDefaults.buttonColors(containerColor = NexusViolet)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text("Launch")
            }
            OutlinedButton(modifier = Modifier.weight(1f), onClick = { onNavigate("mapper") }) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Text("Edit mapping")
            }
        }

        NexusPanel(Modifier.fillMaxWidth()) {
            Text("LAYOUT MODE", color = NexusCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(config.profileName, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Target aspect ratio ${config.targetAspectRatio}", color = TextSecondary, fontSize = 10.sp)
        }

        NexusPanel(Modifier.fillMaxWidth()) {
            Text("MAPPING SUMMARY", color = NexusCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                SummaryMetric(config.buttons.size.toString(), "Bindings")
                SummaryMetric(config.controllerType.displayName, "Controller")
                SummaryMetric(config.schemaVersion.toString(), "Schema")
            }
            Spacer(Modifier.height(12.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigate("mapper") },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2850))
            ) {
                Text("OPEN VISUAL MAPPER", color = NexusVioletLight)
            }
        }
    }
}

@Composable
private fun SummaryMetric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 110.dp)) {
        Text(value, color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = TextMuted, fontSize = 8.sp)
    }
}

@Composable
fun NexusDevicesScreen(
    viewModel: MainAppViewModel,
    onNavigate: (String) -> Unit
) {
    val controller by viewModel.controllerProfile.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().background(GraphiteFoundation).verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionTitle("Devices", "Controller tester")

        NexusPanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(68.dp).clip(CircleShape).background(Color(0xFF0A253D)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.SportsEsports, contentDescription = null, tint = NexusCyan, modifier = Modifier.size(38.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (controller.connected) "CONNECTED" else "NO INPUT DEVICE",
                        color = if (controller.connected) AccentGreen else AccentAmber,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(controller.deviceName ?: "No controller detected", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(controller.type.displayName, color = TextSecondary, fontSize = 10.sp)
                }
                IconButton(onClick = { viewModel.detectController() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh devices", tint = NexusCyan)
                }
            }
        }

        if (controller.connected) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard(Icons.Default.Memory, "Vendor ID", controller.vendorId?.let { "0x%04X".format(it) } ?: "Unknown", NexusCyan, Modifier.weight(1f))
                MetricCard(Icons.Default.Tag, "Product ID", controller.productId?.let { "0x%04X".format(it) } ?: "Unknown", NexusVioletLight, Modifier.weight(1f))
            }
            NexusPanel(Modifier.fillMaxWidth()) {
                Text("INPUT CONFIGURATION", color = NexusCyan, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                DeviceSetting("Configured polling rate", "${controller.pollingRateHz} Hz")
                DeviceSetting("Inner deadzone", "${(controller.stickInnerDeadzone * 100).toInt()}%")
                DeviceSetting("Outer deadzone", "${(controller.stickOuterDeadzone * 100).toInt()}%")
                DeviceSetting("Gyro aiming", if (controller.gyroAimingEnabled) "Enabled" else "Disabled")
            }
        } else {
            NexusPanel(Modifier.fillMaxWidth()) {
                Text("Android is not currently exposing a gamepad, mouse, or physical keyboard to Nexus Input.", color = TextSecondary, fontSize = 11.sp)
            }
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onNavigate("calibration") }
        ) {
            Icon(Icons.Default.Tune, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Open calibration tools")
        }
        Text(
            "Calibration consumes real Android motion events. Missing events or insufficient travel produce a visible failure.",
            color = AccentAmber,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun DeviceSetting(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = 11.sp)
        Text(value, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

@Composable
fun NexusSystemScreen(
    viewModel: MainAppViewModel,
    onNavigate: (String) -> Unit
) {
    val activePrivilege by viewModel.activePrivilegeMethod.collectAsState()
    val probes by viewModel.privilegeResults.collectAsState()
    val runtime by com.example.service.MappingRuntimeBridge.state.collectAsState()
    val panic by com.example.service.PanicKillSwitch.state.collectAsState()
    val diagnostics by viewModel.diagnostics.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().background(GraphiteFoundation).verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionTitle("System", "Engine & integrations")

        NexusPanel(Modifier.fillMaxWidth()) {
            Text("REQUESTED INPUT BACKEND", color = NexusCyan, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
            Text(activePrivilege.title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text(activePrivilege.badgeLabel, color = TextSecondary, fontSize = 10.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { viewModel.refreshPrivileges() }) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Re-check backends")
            }
        }

        NexusPanel(Modifier.fillMaxWidth()) {
            Text("MAPPING STATUS", color = NexusCyan, fontWeight = FontWeight.Bold)
            Text(if (runtime.backendReady) "Backend ready: ${runtime.backend}" else if (runtime.armed) "Armed; backend not ready" else "Disarmed", color = TextPrimary)
            Text("Target in foreground: ${runtime.targetForeground}", color = TextSecondary)
            runtime.notice?.let { Text(it, color = AccentAmber) }
            runtime.error?.let { Text(it, color = AccentRose) }
            if (panic.isKilled) {
                Text(if (panic.releaseConfirmed) "Panic: backend acknowledged release" else "Panic: release not confirmed", color = AccentAmber)
                panic.error?.let { Text(it, color = AccentRose) }
            }
        }
        NexusPanel(Modifier.fillMaxWidth()) {
            Text("RELEASE SELF-CHECK", color = NexusCyan, fontWeight = FontWeight.Bold)
            Text("Reads device, permission, storage and backend observations. No test touches are injected.", color = TextSecondary)
            Button(onClick = viewModel::runSelfCheck) { Text("Collect diagnostics") }
            diagnostics?.let { report ->
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("NEXUS INPUT diagnostics",report))
                    viewModel.showSnack("Diagnostics copied")
                }) { Text("Copy report") }
                Text(report, color = TextSecondary, fontSize = 9.sp)
            }
        }

        probes.forEach { probe ->
            NexusPanel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (probe.isDetected) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (probe.isDetected) AccentGreen else TextMuted
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(probe.method.title, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(probe.statusDetail, color = TextSecondary, fontSize = 9.sp)
                    }
                    if (probe.isDetected && probe.method != activePrivilege) {
                        TextButton(onClick = { viewModel.overridePrivilegeMethod(probe.method) }) {
                            Text("Use")
                        }
                    }
                }
            }
        }

        SectionTitle("Tools", "System controls")
        SystemAction("KernelSU WebUI", "Open the module control center", Icons.Default.Terminal) { onNavigate("root_webui") }
        SystemAction("Overlay studio", "Crosshair and floating HUD controls", Icons.Default.CenterFocusStrong) { onNavigate("crosshair") }
        SystemAction("Safety", "Anti-cheat and game safety information", Icons.Default.Security) { onNavigate("safety") }
        SystemAction("Onboarding", "Run setup and permission checks again", Icons.Default.HelpOutline) { viewModel.restartOnboarding() }
    }
}

@Composable
private fun SystemAction(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = DarkSurface,
        border = NexusPanelBorder
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = NexusCyan)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(subtitle, color = TextMuted, fontSize = 9.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
        }
    }
}
