package com.example.ui.nexus

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.InputDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.ConfigProfileEntity
import com.example.data.entity.GameEntity
import com.example.input.ControllerSourceClassifier
import com.example.model.PrivilegeMethod
import com.example.service.MappingRuntimeBridge
import com.example.ui.MainAppViewModel
import com.example.ui.theme.*

@Composable
private fun NexusPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    NexusGlassCard(modifier = modifier, accent = MaterialTheme.colorScheme.primary, content = content)
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Column {
        Text(title.uppercase(), color = MaterialTheme.colorScheme.primary, fontSize = 24.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        subtitle?.let { Text(it, color = TextSecondary, fontSize = 12.sp) }
    }
}

@Composable
private fun StatusLabel(label: String, value: String, ready: Boolean? = null, modifier: Modifier = Modifier) {
    Column(modifier.nexusGlass(12.dp, MaterialTheme.colorScheme.primary).padding(10.dp)) {
        Text(label, color = TextSecondary, fontSize = 10.sp)
        Text(value, color = when (ready) { true -> AccentGreen; false -> AccentAmber; null -> TextPrimary },
            fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
    }
}

@Composable
fun NexusHomeScreen(viewModel: MainAppViewModel, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val games by viewModel.games.collectAsState()
    val selectedGame by viewModel.selectedGame.collectAsState()
    val config by viewModel.activeConfig.collectAsState()
    val controller by viewModel.controllerProfile.collectAsState()
    val probes by viewModel.privilegeResults.collectAsState()
    val runtime by MappingRuntimeBridge.state.collectAsState()
    val permissions = rememberNexusPermissions()
    val activeGame = selectedGame ?: games.firstOrNull { it.packageName == config.gamePackage }
    val requested = config.preferredBackend?.title ?: "Automatic"
    val available = probes.count { it.isDetected }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("NEXUS INPUT", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text("GAMEPAD TO TOUCH", color = TextSecondary, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        NexusPanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SportsEsports, null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(54.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (controller.connected) "Connected controller" else "Controller disconnected",
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(controller.deviceName ?: "No physical controller detected", color = TextPrimary, fontSize = 16.sp)
                    Text(if (controller.connected) "Android input device · ${controller.type.displayName}" else "Connect a controller to begin",
                        color = TextSecondary, fontSize = 11.sp)
                    Text("Transport / radio latency: not exposed by this observation", color = TextMuted, fontSize = 10.sp)
                }
            }
        }
        Text("ACTIVE PROFILE", color = TextSecondary, fontSize = 11.sp)
        NexusPanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (config.gamePackage.isNotBlank()) NexusPackageIcon(config.gamePackage, Modifier.size(82.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(activeGame?.displayName ?: config.gameTitle.ifBlank { "Choose a game" },
                        color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text("Profile: ${config.profileName}", color = TextSecondary, fontSize = 12.sp)
                    Text("Requested backend: $requested", color = TextSecondary, fontSize = 12.sp)
                    Text("Mapping: ${NexusScreenPresentation.mappingStatus(runtime)}", color = if (runtime.error != null) AccentRose else TextSecondary,
                        fontSize = 11.sp)
                    NexusNeonButton(onClick = { activeGame?.let { viewModel.launchGameWithMapping(it, context) } },
                        enabled = activeGame != null, modifier = Modifier.fillMaxWidth(), variant = NexusButtonVariant.Primary) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp)); Text("LAUNCH GAME")
                    }
                }
            }
            runtime.notice?.let { Text(it, color = AccentAmber, fontSize = 11.sp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusLabel("Controller", if (controller.connected) "Connected" else "Disconnected", controller.connected, Modifier.weight(1f))
            StatusLabel("Backend", if (runtime.backendReady) "Prepared" else if (probes.isEmpty()) "Not checked" else "$available available", runtime.backendReady, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusLabel("Overlay", if (permissions.overlay) "Granted" else "Required", permissions.overlay, Modifier.weight(1f))
            StatusLabel("Accessibility", if (permissions.accessibility) "Service connected" else "Required", permissions.accessibility, Modifier.weight(1f))
        }
        permissions.error?.let { Text(it, color = AccentRose) }
        Text("QUICK ACTIONS", color = TextSecondary, fontSize = 11.sp)
        val actions = listOf(Triple("Mapper", Icons.Default.Map, "mapper"), Triple("Profiles", Icons.Default.List, "profiles"),
            Triple("Devices", Icons.Default.SportsEsports, "devices"), Triple("System", Icons.Default.Settings, "system"),
            Triple("Controller Test", Icons.Default.Adjust, "calibration"), Triple("Overlay", Icons.Default.Layers, "crosshair"))
        actions.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, icon, route) -> QuickAction(label, icon, Modifier.weight(1f)) { onNavigate(route) } }
            }
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.nexusGlass(16.dp, MaterialTheme.colorScheme.primary).clickable(onClick = onClick)
        .heightIn(min = 100.dp).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(10.dp)); Text(label, color = TextPrimary, fontSize = 12.sp)
    }
}

@Composable
fun NexusProfilesScreen(viewModel: MainAppViewModel, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val games by viewModel.games.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val config by viewModel.activeConfig.collectAsState()
    val runtime by MappingRuntimeBridge.state.collectAsState()
    val installed by viewModel.installedApps.collectAsState()
    val loading by viewModel.appInventoryLoading.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ProfileBrowserFilter.ALL) }
    var showApps by remember { mutableStateOf(false) }
    var installedQuery by remember { mutableStateOf("") }
    var cloneTarget by remember { mutableStateOf<ConfigProfileEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<ConfigProfileEntity?>(null) }
    var cloneName by remember { mutableStateOf("") }
    val rows = remember(profiles, games, query, filter) { NexusScreenPresentation.profileRows(profiles, games, query, filter) }
    val selected = profiles.firstOrNull { it.id == config.id }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        SectionTitle("Profiles")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(query, { query = it }, leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Search profiles…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProfileBrowserFilter.entries.forEach { item ->
                FilterChip(selected = item == filter, onClick = { filter = item }, label = { Text(item.label, fontSize = 11.sp) }, modifier = Modifier.weight(1f))
            }
        }
        if (filter == ProfileBrowserFilter.RECENT) Text("Sorted by the actual saved timestamp", color = TextMuted, fontSize = 10.sp)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (rows.isEmpty()) item {
                NexusPanel(Modifier.fillMaxWidth().testTag("profiles_empty_state")) {
                    Text(if (profiles.isEmpty()) "No saved profiles" else "No profiles match this filter", color = TextPrimary)
                    Text("Add an installed game, then create and save its mapping.", color = TextSecondary, fontSize = 12.sp)
                }
            }
            items(rows, key = { it.profile.id }) { row ->
                val launchGame = row.game ?: GameEntity(row.profile.gamePackage, row.config?.gameTitle?.ifBlank { row.profile.gamePackage } ?: row.profile.gamePackage)
                SavedProfileCard(row, selected = row.profile.id == config.id,
                    armed = runtime.armed && runtime.configId == row.profile.id,
                    onEdit = { viewModel.selectSavedProfile(row.profile) { onNavigate("mapper") } },
                    onLaunch = { viewModel.selectSavedProfile(row.profile) { viewModel.launchGameWithMapping(launchGame, context) } },
                    onDetail = { viewModel.selectSavedProfile(row.profile) { onNavigate("profile_detail") } },
                    onClone = { cloneTarget = row.profile; cloneName = "${row.profile.profileName} copy" },
                    onDelete = { deleteTarget = row.profile })
            }
            val unsaved = NexusScreenPresentation.unsavedLibraryGames(games, profiles, query, filter)
            if (unsaved.isNotEmpty()) item { Text("LIBRARY · NO SAVED PROFILE", color = TextSecondary, fontSize = 11.sp) }
            items(unsaved, key = { "game:${it.packageName}" }) { game ->
                NexusPanel(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NexusPackageIcon(game.packageName, Modifier.size(42.dp)); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) { Text(game.displayName, color = TextPrimary); Text(game.packageName, color = TextMuted, fontSize = 10.sp) }
                        TextButton(onClick = { viewModel.selectGame(game); onNavigate("profile_detail") }) { Text("Create") }
                    }
                }
            }
        }
        Text("ACTIONS", color = TextSecondary, fontSize = 11.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NexusNeonButton(onClick = { showApps = true; viewModel.refreshInstalledApps() }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Text("Add profile", fontSize = 12.sp) }
            NexusNeonButton(onClick = { onNavigate("profile_files") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Download, null); Text("Import profile", fontSize = 12.sp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NexusNeonButton(onClick = { selected?.let { cloneTarget = it; cloneName = "${it.profileName} copy" } }, enabled = selected != null, modifier = Modifier.weight(1f)) { Text("Clone selected", fontSize = 12.sp) }
            NexusNeonButton(onClick = { deleteTarget = selected }, enabled = selected != null, modifier = Modifier.weight(1f), variant = NexusButtonVariant.Danger) { Text("Delete selected", fontSize = 12.sp) }
        }
    }
    if (showApps) AlertDialog(onDismissRequest = { showApps = false }, title = { Text("Choose an installed app") }, text = {
        Column {
            OutlinedTextField(installedQuery, { installedQuery = it }, label = { Text("Search name or package") })
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(installed.filter { it.displayName.contains(installedQuery, true) || it.packageName.contains(installedQuery, true) }, key = { it.packageName }) { app ->
                    TextButton(onClick = { viewModel.addGame(app) { showApps = false; onNavigate("profile_detail") } }) {
                        Column { Text(app.displayName); Text(app.packageName, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            if (!loading && installed.isEmpty()) Text("Android returned no launchable apps. Check package visibility and logs.")
        }
    }, confirmButton = { TextButton(onClick = { showApps = false }) { Text("Close") } })
    cloneTarget?.let { target ->
        AlertDialog(onDismissRequest = { cloneTarget = null }, title = { Text("Clone profile") },
            text = { OutlinedTextField(cloneName, { cloneName = it }, label = { Text("New profile name") }) },
            confirmButton = { TextButton(onClick = { viewModel.cloneSavedProfile(target, cloneName) { cloneTarget = null } }, enabled = cloneName.isNotBlank()) { Text("Clone") } },
            dismissButton = { TextButton(onClick = { cloneTarget = null }) { Text("Cancel") } })
    }
    deleteTarget?.let { target ->
        AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("Delete ${target.profileName}?") },
            text = { Text("Deletes this saved mapping from Room. Stop mapping before deleting a profile.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteSavedProfile(target) { deleteTarget = null } }) { Text("Delete", color = AccentRose) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } })
    }
}

@Composable
private fun SavedProfileCard(row: ProfileBrowserRow, selected: Boolean, armed: Boolean,
    onEdit: () -> Unit, onLaunch: () -> Unit, onDetail: () -> Unit, onClone: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    NexusPanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NexusPackageIcon(row.profile.gamePackage, Modifier.size(48.dp))
            Column(Modifier.weight(1f)) {
                Text(row.game?.displayName ?: row.config?.gameTitle?.ifBlank { row.profile.gamePackage } ?: row.profile.gamePackage,
                    color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(row.profile.gamePackage, color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (armed || selected || row.profile.isDefault) NexusStatusChip(
                text = if (armed) "ARMED" else if (selected) "SELECTED" else "DEFAULT",
                modifier = Modifier.clickable(onClick = onDetail))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) { Text("Profile", color = TextMuted, fontSize = 10.sp); Text(row.profile.profileName, color = TextPrimary, fontSize = 13.sp) }
            Column(Modifier.weight(1f)) { Text("Controller layout", color = TextMuted, fontSize = 10.sp); Text(row.config?.controllerType?.displayName ?: "Unavailable · invalid profile", color = TextSecondary, fontSize = 11.sp) }
        }
        Text("Backend: ${row.config?.preferredBackend?.title ?: if (row.config != null) "Automatic" else "Unavailable"}", color = TextSecondary, fontSize = 10.sp)
        row.error?.let { Text(it, color = AccentRose, fontSize = 11.sp) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NexusNeonButton(onClick = onEdit, enabled = row.error == null, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp)); Text("EDIT") }
            NexusNeonButton(onClick = onLaunch, enabled = row.error == null, modifier = Modifier.weight(1f)) { Text("LAUNCH") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Profile actions") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Details") }, onClick = { menu = false; onDetail() }, enabled = row.error == null)
                    DropdownMenuItem(text = { Text("Clone") }, onClick = { menu = false; onClone() }, enabled = row.error == null)
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
fun NexusProfileDetailScreen(viewModel: MainAppViewModel, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val game by viewModel.selectedGame.collectAsState()
    val config by viewModel.activeConfig.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = { onNavigate("profiles") }) { Icon(Icons.Default.ArrowBack, null); Text("Profiles") }
        NexusPanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (config.gamePackage.isNotBlank()) NexusPackageIcon(config.gamePackage, Modifier.size(60.dp))
                Column { SectionTitle(game?.displayName ?: config.gameTitle.ifBlank { "No game selected" }); Text(game?.packageName ?: config.gamePackage, color = TextSecondary, fontSize = 11.sp) }
            }
            Text(config.profileName, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Requested backend: ${config.preferredBackend?.title ?: "Automatic"}", color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NexusNeonButton(onClick = { game?.let { viewModel.launchGameWithMapping(it, context) } }, enabled = game != null, modifier = Modifier.weight(1f), variant = NexusButtonVariant.Primary) { Text("Launch") }
                NexusNeonButton(onClick = { onNavigate("mapper") }, modifier = Modifier.weight(1f)) { Text("Edit mapping") }
            }
        }
        if (config.joystick.sprintLockEnabled || config.antiRecoilEnabled || config.camera.mouseDpiScale != 1f) NexusPanel(Modifier.fillMaxWidth()) {
            Text("Unsupported stored options", color = AccentAmber, fontWeight = FontWeight.Bold)
            Text("Sprint lock, anti-recoil and mouse mapping are unavailable in this gamepad release. Disable these options to launch this profile.", color = TextSecondary)
            NexusNeonButton(onClick = { viewModel.updateActiveConfig(config.copy(joystick = config.joystick.copy(sprintLockEnabled = false), antiRecoilEnabled = false, camera = config.camera.copy(mouseDpiScale = 1f))) }) { Text("Disable unsupported options") }
        }
        NexusPanel(Modifier.fillMaxWidth()) {
            Text("SAVED PROFILES", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            profiles.filter { it.gamePackage == config.gamePackage }.forEach { profile ->
                TextButton(onClick = { viewModel.selectSavedProfile(profile) }) { Text(if (profile.id == config.id) "${profile.profileName} · selected" else profile.profileName) }
            }
            NexusNeonButton(onClick = { showCreate = true }, enabled = game != null) { Text("New profile") }
        }
        NexusPanel(Modifier.fillMaxWidth()) {
            Text("MAPPING SUMMARY", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            DeviceSetting("Bindings", config.buttons.size.toString())
            DeviceSetting("Controller layout", config.controllerType.displayName)
            DeviceSetting("Target aspect ratio", config.targetAspectRatio)
            DeviceSetting("Profile schema", config.schemaVersion.toString())
            NexusNeonButton(onClick = { onNavigate("profile_files") }, modifier = Modifier.fillMaxWidth()) { Text("Export / backup profiles") }
        }
    }
    if (showCreate) AlertDialog(onDismissRequest = { showCreate = false }, title = { Text("New game profile") },
        text = { OutlinedTextField(newName, { newName = it }, label = { Text("Profile name") }) },
        confirmButton = { TextButton(onClick = { viewModel.createProfile(newName) { showCreate = false; newName = "" } }, enabled = newName.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } })
}

@Composable
fun NexusDevicesScreen(viewModel: MainAppViewModel, onNavigate: (String) -> Unit) {
    var refreshKey by remember { mutableIntStateOf(0) }
    val inventory = rememberInputDevices(viewModel::detectController, refreshKey)
    val live by com.example.input.ControllerInputMonitor.state.collectAsState()
    var onlyControllers by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Devices"); Spacer(Modifier.weight(1f))
            NexusNeonButton(onClick = { onNavigate("calibration") }) { Icon(Icons.Default.SportsEsports, null, modifier = Modifier.size(16.dp)); Text("Controller Test", fontSize = 11.sp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !onlyControllers, onClick = { onlyControllers = false }, label = { Text("All physical inputs") })
            FilterChip(selected = onlyControllers, onClick = { onlyControllers = true }, label = { Text("Controllers") })
        }
        Text("Android InputDevice inventory · transport, radio strength and battery are not measured here", color = TextMuted, fontSize = 11.sp)
        inventory.error?.let { Text(it, color = AccentRose) }
        val devices = inventory.devices.filter { !onlyControllers || ControllerSourceClassifier.isControllerSource(it.sources) }
        if (devices.isEmpty()) NexusPanel(Modifier.fillMaxWidth().testTag("devices_empty_state")) {
            Text("No matching physical input devices", color = TextPrimary, modifier = Modifier.testTag("devices_connection_status"))
            Text("Connect a controller and refresh. Android must expose the device before Nexus can test it.", color = TextSecondary, fontSize = 12.sp)
        }
        devices.forEach { device ->
            NexusPanel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.SportsEsports, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                    Column(Modifier.weight(1f)) {
                        Text(device.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Text(if (ControllerSourceClassifier.isControllerSource(device.sources)) "Android controller sources" else "Android physical input", color = TextSecondary, fontSize = 12.sp)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = .18f))
                DeviceSetting("Status", if (live.deviceId == device.id) "Connected · events observed" else "Connected · waiting for events")
                DeviceSetting("Vendor ID", "0x%04X".format(device.vendorId))
                DeviceSetting("Product ID", "0x%04X".format(device.productId))
                DeviceSetting("Device / sources", "${device.id} / 0x${device.sources.toString(16)}")
                DeviceSetting("Transport / battery", "Not observed")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NexusNeonButton(onClick = { onNavigate("calibration") }, modifier = Modifier.weight(1f)) { Text("Test / calibrate") }
                    NexusNeonButton(onClick = { refreshKey++ }, modifier = Modifier.weight(1f)) { Text("Refresh") }
                }
            }
        }
    }
}

@Composable
private fun DeviceSetting(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
fun NexusSystemScreen(viewModel: MainAppViewModel, onNavigate: (String) -> Unit) {
    val config by viewModel.activeConfig.collectAsState()
    val probes by viewModel.privilegeResults.collectAsState()
    val runtime by MappingRuntimeBridge.state.collectAsState()
    val panic by com.example.service.PanicKillSwitch.state.collectAsState()
    val diagnostics by viewModel.diagnostics.collectAsState()
    val context = LocalContext.current
    val permissions = rememberNexusPermissions()
    var query by remember { mutableStateOf("") }
    fun show(section: String) = section.contains(query, true)
    fun settings(intent: Intent) {
        try { context.startActivity(intent) }
        catch (error: Exception) { viewModel.showSnack("Cannot open Android settings: ${error.message}") }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("System", "Mapping permissions, runtime and app tools")
        OutlinedTextField(query, { query = it }, placeholder = { Text("Search settings…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (show("Permissions Accessibility Overlay Shizuku KernelSU")) NexusPanel(Modifier.fillMaxWidth()) {
            PanelHeading("Permissions", Icons.Default.Security)
            SystemAction("Accessibility", if (permissions.accessibility) "Service connected" else "Service not connected", Icons.Default.Accessibility) { settings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            SystemAction("Overlay", if (permissions.overlay) "Permission granted" else "Permission required", Icons.Default.Layers) { settings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }
            SystemAction("Shizuku setup", probes.firstOrNull { it.method == PrivilegeMethod.SHIZUKU }?.statusDetail ?: "Not checked", Icons.Default.Link) { viewModel.restartOnboarding() }
            permissions.error?.let { Text(it, color = AccentRose) }
        }
        if (show("Input backend Automatic Shizuku KernelSU APatch Magisk")) NexusPanel(Modifier.fillMaxWidth()) {
            PanelHeading("Input backend", Icons.Default.Input)
            Text("Profile: ${config.profileName}", color = TextSecondary)
            Text("Requested: ${config.preferredBackend?.title ?: "Automatic"}", color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(if (config.preferredBackend == null) "Failed automatic candidates are reported by the runtime." else "This profile stops when its requested backend cannot prepare.", color = TextSecondary, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = config.preferredBackend == null, onClick = { viewModel.overridePrivilegeMethod(null) }, label = { Text("Automatic") })
                listOf(PrivilegeMethod.SHIZUKU, PrivilegeMethod.KERNELSU).forEach { method ->
                    FilterChip(selected = config.preferredBackend == method, onClick = { viewModel.overridePrivilegeMethod(method) }, label = { Text(if (method == PrivilegeMethod.KERNELSU) "KernelSU" else "Shizuku") }, enabled = probes.any { it.method == method && it.isDetected })
                }
            }
            NexusNeonButton(onClick = viewModel::refreshPrivileges) { Icon(Icons.Default.Refresh, null); Text("Re-check backends") }
            probes.forEach { probe ->
                Text("${probe.method.title}: ${probe.state}", color = if (probe.isDetected) AccentGreen else AccentAmber, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(probe.statusDetail, color = TextSecondary, fontSize = 10.sp)
                if (probe.isDetected && config.preferredBackend != probe.method && probe.method !in listOf(PrivilegeMethod.SHIZUKU, PrivilegeMethod.KERNELSU)) TextButton(onClick = { viewModel.overridePrivilegeMethod(probe.method) }) { Text("Use ${probe.method.title}") }
            }
        }
        if (show("Mapping status runtime panic release")) NexusPanel(Modifier.fillMaxWidth()) {
            PanelHeading("Mapping runtime", Icons.Default.SportsEsports)
            Text(NexusScreenPresentation.mappingStatus(runtime), color = if (runtime.error != null) AccentRose else TextPrimary, modifier = Modifier.testTag("system_mapping_status"))
            DeviceSetting("Target foreground", runtime.targetForeground.toString())
            DeviceSetting("Prepared backend", if (runtime.backendReady) runtime.backend?.title ?: "Unknown" else "None prepared")
            runtime.notice?.let { Text(it, color = AccentAmber, fontSize = 11.sp) }
            if (panic.isKilled) Text(if (panic.releaseConfirmed) "Panic release acknowledged by backend" else "Panic release NOT confirmed", color = AccentAmber, modifier = Modifier.testTag("system_panic_status"))
            panic.error?.let { Text(it, color = AccentRose, fontSize = 11.sp, modifier = Modifier.testTag("system_panic_error")) }
            NexusNeonButton(onClick = viewModel::triggerPanicKillSwitch, variant = NexusButtonVariant.Danger, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.PowerSettingsNew, null); Text("Panic release / stop") }
        }
        if (show("Controller calibration test")) NexusPanel(Modifier.fillMaxWidth()) {
            PanelHeading("Controller", Icons.Default.SportsEsports)
            SystemAction("Test / calibrate", "Actual Android buttons, axes and calibration samples", Icons.Default.Adjust) { onNavigate("calibration") }
        }
        if (show("Overlay crosshair FPS frame time")) NexusPanel(Modifier.fillMaxWidth()) {
            PanelHeading("Overlay", Icons.Default.Layers)
            SystemAction("Crosshair studio", "Edit the supported crosshair overlay", Icons.Default.CenterFocusStrong) { onNavigate("crosshair") }
            SystemAction("Frame overlay", "Presented FPS and measured frame intervals", Icons.Default.Speed) { onNavigate("fps") }
        }
        if (show("Advanced diagnostics logs backup restore safety")) NexusPanel(Modifier.fillMaxWidth()) {
            PanelHeading("Advanced", Icons.Default.Tune)
            SystemAction("Backup / restore profiles", "Validated local JSON import, export and ZIP backup", Icons.Default.Archive) { onNavigate("profile_files") }
            SystemAction("Game safety", "Compatibility and anti-cheat information", Icons.Default.Security) { onNavigate("safety") }
            NexusNeonButton(onClick = viewModel::runSelfCheck) { Icon(Icons.Default.BugReport, null); Text("Collect diagnostics") }
            Text("Observational self-check; no test touches are injected.", color = TextMuted, fontSize = 10.sp)
            diagnostics?.let { report ->
                if (report.startsWith("Self-check failed:")) Text(report, color = AccentRose)
                NexusNeonButton(onClick = {
                    try {
                        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                        checkNotNull(clipboard) { "Android clipboard is unavailable" }
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("NEXUS INPUT diagnostics", report))
                        viewModel.showSnack("Diagnostics copied")
                    } catch (error: Exception) { viewModel.showSnack("Diagnostics copy failed: ${error.message}") }
                }) { Text("Copy report") }
                com.example.diagnostics.ReleaseDiagnostics.inputReport(report)?.let { input ->
                    NexusNeonButton(onClick = {
                        try {
                            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                            checkNotNull(clipboard) { "Android clipboard is unavailable" }
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("NEXUS INPUT input report", input))
                            viewModel.showSnack("Input report copied")
                        } catch (error: Exception) { viewModel.showSnack("Input report copy failed: ${error.message}") }
                    }) { Text("Copy input report") }
                }
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Box(Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(rememberScrollState()).padding(8.dp)) {
                        Text(report, color = TextSecondary, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
            }
        }
        if (show("KernelSU WebUI root module")) NexusPanel(Modifier.fillMaxWidth()) {
            PanelHeading("Root module", Icons.Default.Terminal)
            Text("Kernel and performance controls belong to the module WebUI.", color = TextSecondary, fontSize = 11.sp)
            NexusNeonButton(onClick = { onNavigate("root_webui") }, modifier = Modifier.fillMaxWidth()) { Text("Open KernelSU WebUI") }
        }
    }
}

@Composable
private fun PanelHeading(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
        Text(title.uppercase(), color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SystemAction(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) { Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); Text(subtitle, color = TextSecondary, fontSize = 10.sp) }
        Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
    }
}
