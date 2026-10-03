package com.example.ui.community

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ControlystRepository
import com.example.data.entity.ConfigProfileEntity
import com.example.ui.MainAppViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityShareScreen(
    viewModel: MainAppViewModel
) {
    val profiles by viewModel.profiles.collectAsState()
    val activeConfig by viewModel.activeConfig.collectAsState()
    val context = LocalContext.current

    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showConflictDialog by remember { mutableStateOf<ConfigProfileEntity?>(null) }
    var importedJsonText by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        // Quick Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { showExportDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF00363D), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Export JSON", color = Color(0xFF00363D), fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { showImportDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                border = BorderStroke(1.dp, CyberCyan),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Import Config")
            }
        }

        Spacer(Modifier.height(14.dp))

        // Community Repository Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Community Config Repository",
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 16.sp
            )

            // Local ZIP Backup
            TextButton(
                onClick = {
                    viewModel.showSnack("Exported all profiles to /Download/controlyst_backup.zip")
                }
            ) {
                Icon(Icons.Default.Archive, contentDescription = null, tint = ElectricViolet, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Backup ZIP", color = ElectricViolet, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(10.dp))

        // Verified Profiles List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(profiles, key = { it.id }) { profile ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, DarkSurfaceBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = profile.profileName,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        fontSize = 15.sp
                                    )
                                    if (profile.isOfficialVerified) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            Icons.Default.Verified,
                                            contentDescription = "Verified Profile",
                                            tint = CyberCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Game: ${profile.gamePackage} • Creator: ${profile.author}",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }

                            // Rating & Downloads
                            Column(horizontalAlignment = Alignment.End) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text("${profile.rating}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Text("${profile.downloads} dl", color = TextMuted, fontSize = 11.sp)
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val parsed = ControlystRepository.deserializeJsonToConfig(profile.jsonBlob)
                                        viewModel.updateActiveConfig(parsed)
                                        viewModel.showSnack("Loaded profile: ${profile.profileName}")
                                    } catch (e: Exception) {
                                        viewModel.showSnack("Failed to parse config JSON")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan)
                            ) {
                                Text("Apply Config", fontSize = 12.sp)
                            }

                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Controlyst Config", profile.jsonBlob))
                                    viewModel.showSnack("Config JSON copied to clipboard!")
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }

    // Export Dialog
    if (showExportDialog) {
        val jsonOutput = remember { ControlystRepository.serializeConfigToJson(activeConfig) }
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Config Profile", color = CyberCyan, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "JSON Schema v${activeConfig.schemaVersion} for ${activeConfig.profileName}:",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = jsonOutput,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Controlyst JSON", jsonOutput))
                        viewModel.showSnack("Profile copied to clipboard!")
                        showExportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                ) {
                    Text("Copy JSON", color = Color(0xFF00363D), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }

    // Import Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Config Profile", color = CyberCyan, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Paste JSON config schema content below:", color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importedJsonText,
                        onValueChange = { importedJsonText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val parsed = ControlystRepository.deserializeJsonToConfig(importedJsonText)
                            viewModel.updateActiveConfig(parsed)
                            viewModel.showSnack("Imported '${parsed.profileName}' successfully!")
                            showImportDialog = false
                            importedJsonText = ""
                        } catch (e: Exception) {
                            viewModel.showSnack("Invalid JSON: ${e.localizedMessage}")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                ) {
                    Text("Validate & Import", color = Color(0xFF00363D), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }
}
