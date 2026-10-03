package com.example.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.GameEntity
import com.example.model.AntiCheatSeverity
import com.example.ui.MainAppViewModel
import com.example.ui.dashboard.ControlystPhoneDashboard
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameLibraryScreen(
    viewModel: MainAppViewModel,
    onNavigateToMapper: () -> Unit
) {
    val games by viewModel.games.collectAsState()
    val selectedGame by viewModel.selectedGame.collectAsState()
    val activePrivilege by viewModel.activePrivilegeMethod.collectAsState()
    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }
    var safetyWarningGame by remember { mutableStateOf<GameEntity?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredGames = remember(games, searchQuery) {
        if (searchQuery.isBlank()) games
        else games.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Phone Dashboard matching final.jpeg (Profile Switcher, Telemetry, Performance Modes, Status Pills)
        item(key = "phone_dashboard") {
            ControlystPhoneDashboard(viewModel = viewModel)
        }

        // 2. Search Bar & Add Game button
        item(key = "search_and_add") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("game_search_field"),
                    placeholder = { Text("Search installed games...", color = TextMuted, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ControlystCyan,
                        unfocusedBorderColor = DarkSurfaceBorder,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    )
                )

                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = ControlystCyan,
                    contentColor = Color(0xFF00363D),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("add_game_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Game")
                }
            }
        }

        // 3. Section Header for Games Library
        item(key = "games_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Configured Game Profiles",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${filteredGames.size} Games",
                    color = ControlystCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // 4. Games List Items
        items(filteredGames, key = { it.packageName }) { game ->
            GameItemCard(
                game = game,
                isSelected = selectedGame?.packageName == game.packageName,
                onSelect = { viewModel.selectGame(game) },
                onLaunch = {
                    if (game.antiCheatSeverity == AntiCheatSeverity.HIGH_ALERT && activePrivilege.requiresRoot) {
                        safetyWarningGame = game
                    } else {
                        viewModel.launchGameWithMapping(game, context)
                    }
                },
                onEditMapping = {
                    viewModel.selectGame(game)
                    onNavigateToMapper()
                }
            )
        }
    }

    // Anti-Cheat Safety Dialog
    safetyWarningGame?.let { game ->
        AlertDialog(
            onDismissRequest = { safetyWarningGame = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRose, modifier = Modifier.size(36.dp)) },
            title = { Text("Anti-Cheat Advisory", color = AccentRose, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "${game.displayName} employs strict anti-cheat (${game.antiCheatNotes}).",
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Running with direct root su injection may risk game account sanctions. We strongly recommend switching to Shizuku or Accessibility fallback.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = safetyWarningGame
                        safetyWarningGame = null
                        target?.let { viewModel.launchGameWithMapping(it, context) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
                ) {
                    Text("Launch Anyway", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { safetyWarningGame = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }

    // Add/Scan Installed Games Dialog
    if (showAddDialog) {
        val installedApps = remember { viewModel.repository.scanInstalledApps() }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add / Tag Installed Games", color = CyberCyan, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                ) {
                    items(installedApps) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.addGame(app.copy(isGameTag = true))
                                    showAddDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.SportsEsports, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.displayName, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text(app.packageName, color = TextMuted, fontSize = 11.sp)
                            }
                            Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = CyberCyan)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Done", color = CyberCyan)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }
}

@Composable
fun GameItemCard(
    game: GameEntity,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onLaunch: () -> Unit,
    onEditMapping: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onSelect() }
            .testTag("game_card_${game.packageName}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) DarkSurfaceElevated else DarkSurface
        ),
        border = BorderStroke(
            width = if (isSelected) 1.6.dp else 1.dp,
            color = if (isSelected) CyberCyan else DarkSurfaceBorder
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Game Thumbnail Box
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when (game.antiCheatSeverity) {
                                AntiCheatSeverity.SAFE -> AccentGreen.copy(alpha = 0.2f)
                                AntiCheatSeverity.MODERATE -> AccentAmber.copy(alpha = 0.2f)
                                AntiCheatSeverity.HIGH_ALERT -> AccentRose.copy(alpha = 0.2f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = when (game.antiCheatSeverity) {
                            AntiCheatSeverity.SAFE -> AccentGreen
                            AntiCheatSeverity.MODERATE -> AccentAmber
                            AntiCheatSeverity.HIGH_ALERT -> AccentRose
                        },
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = game.displayName,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = game.packageName,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                // Anti-Cheat Status Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (game.antiCheatSeverity) {
                        AntiCheatSeverity.SAFE -> AccentGreen.copy(alpha = 0.15f)
                        AntiCheatSeverity.MODERATE -> AccentAmber.copy(alpha = 0.15f)
                        AntiCheatSeverity.HIGH_ALERT -> AccentRose.copy(alpha = 0.15f)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (game.antiCheatSeverity) {
                                AntiCheatSeverity.SAFE -> Icons.Default.Shield
                                AntiCheatSeverity.MODERATE -> Icons.Default.Info
                                AntiCheatSeverity.HIGH_ALERT -> Icons.Default.Warning
                            },
                            contentDescription = null,
                            tint = when (game.antiCheatSeverity) {
                                AntiCheatSeverity.SAFE -> AccentGreen
                                AntiCheatSeverity.MODERATE -> AccentAmber
                                AntiCheatSeverity.HIGH_ALERT -> AccentRose
                            },
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = game.antiCheatSeverity.name,
                            color = when (game.antiCheatSeverity) {
                                AntiCheatSeverity.SAFE -> AccentGreen
                                AntiCheatSeverity.MODERATE -> AccentAmber
                                AntiCheatSeverity.HIGH_ALERT -> AccentRose
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onEditMapping,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                    border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Visual Mapper")
                }

                Button(
                    onClick = onLaunch,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF00363D), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Launch & Map", color = Color(0xFF00363D), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
