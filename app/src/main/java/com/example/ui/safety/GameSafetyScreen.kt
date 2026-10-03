package com.example.ui.safety

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GameSafetyReport
import com.example.model.SafetyStatus
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSafetyScreen(
    onBack: () -> Unit
) {
    val reports = remember {
        listOf(
            GameSafetyReport(
                gamePackage = "com.tencent.tmgp.df",
                gameTitle = "Delta Force Mobile",
                safetyStatus = SafetyStatus.NO_KNOWN_ISSUES,
                rootCompatibility = "KernelSU / APatch (/dev/uinput virtual OTG pad) verified.",
                overlayCompatibility = "Normal transparent HUD overlay supported.",
                shizukuCompatibility = "Direct adb uinput injection supported.",
                accessibilityCompatibility = "Fully supported without flag triggers.",
                antiCheatEngine = "ACE (Anti-Cheat Expert)",
                banRiskSummary = "Hardware /dev/uinput input behaves identically to official Bluetooth controller. Rapid macros discouraged in ranked queues.",
                userReportsCount = 342,
                maintainerNotes = "Verified safe with Controlyst v1.0.0. Use standard analog sensitivity."
            ),
            GameSafetyReport(
                gamePackage = "com.activision.callofduty.warzone",
                gameTitle = "COD: Warzone Mobile",
                safetyStatus = SafetyStatus.USE_CAUTION,
                rootCompatibility = "Requires KernelSU / APatch su hide or Magisk DenyList.",
                overlayCompatibility = "Floating overlay supported (disable screen-dim flags).",
                shizukuCompatibility = "Shizuku injection working normally.",
                accessibilityCompatibility = "Use caution: some accessibility hooks monitored.",
                antiCheatEngine = "RICOCHET Mobile Heuristics",
                banRiskSummary = "Virtual controller input is accepted. Do not enable turbo rapid-fire on single-action rifles.",
                userReportsCount = 518,
                maintainerNotes = "Root detection active in newer versions; ensure Zygisk/KernelSU umount module is active."
            ),
            GameSafetyReport(
                gamePackage = "com.riotgames.league.wildrift",
                gameTitle = "League of Legends: Wild Rift",
                safetyStatus = SafetyStatus.NO_KNOWN_ISSUES,
                rootCompatibility = "Clean /dev/uinput virtual driver accepted.",
                overlayCompatibility = "Overlay HUD supported.",
                shizukuCompatibility = "Verified smooth.",
                accessibilityCompatibility = "Touch accessibility service accepted.",
                antiCheatEngine = "Riot Vanguard Mobile",
                banRiskSummary = "Mapping touch to physical controller buttons is within acceptable accessibility bounds.",
                userReportsCount = 189,
                maintainerNotes = "Smart-cast macros should include at least 60ms delay between skill activations."
            ),
            GameSafetyReport(
                gamePackage = "com.epicgames.fortnite",
                gameTitle = "Fortnite Mobile",
                safetyStatus = SafetyStatus.INCOMPATIBLE_REPORTS,
                rootCompatibility = "Strict BattlEye / EasyAntiCheat kernel integrity check.",
                overlayCompatibility = "Overlay works.",
                shizukuCompatibility = "Shizuku touch injection may cause input lockup.",
                accessibilityCompatibility = "Accessibility touch events dropped in game loop.",
                antiCheatEngine = "BattlEye / Hyperion",
                banRiskSummary = "Device integrity bans reported on heavily modified kernels. Native Bluetooth controller recommended over root injection.",
                userReportsCount = 612,
                maintainerNotes = "Direct hardware Bluetooth mapping is safest. Root-level injection discouraged on this title."
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Game Safety & Anti-Cheat Hub", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("safety_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CyberCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Objective Policy Statement Banner
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, DarkSurfaceBorder)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Integrity & Anti-Cheat Disclaimer", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DarkTextPrimary)
                        Text("Controlyst does not bypass anti-cheat systems, spoof memory, or claim to be 'undetectable'. All assessments are community-verified reports based on /dev/uinput virtual gamepad standards.", fontSize = 10.sp, color = DarkTextSecondary)
                    }
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(reports) { report ->
                    SafetyReportCard(report)
                }
            }
        }
    }
}

@Composable
fun SafetyReportCard(report: GameSafetyReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, Color(report.safetyStatus.colorHex))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(report.gameTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkTextPrimary)
                    Text(report.gamePackage, fontSize = 10.sp, color = DarkTextSecondary)
                }
                Surface(
                    color = Color(report.safetyStatus.colorHex).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        report.safetyStatus.displayName,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(report.safetyStatus.colorHex)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Anti-Cheat Engine", fontSize = 10.sp, color = DarkTextSecondary)
                    Text(report.antiCheatEngine, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = DarkTextPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Community Reports", fontSize = 10.sp, color = DarkTextSecondary)
                    Text("${report.userReportsCount} verified devices", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = CyberCyan)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text("Root / Uinput:", fontSize = 10.sp, color = DarkTextSecondary)
            Text(report.rootCompatibility, fontSize = 11.sp, color = DarkTextPrimary)

            Spacer(modifier = Modifier.height(4.dp))

            Text("Policy Assessment:", fontSize = 10.sp, color = DarkTextSecondary)
            Text(report.banRiskSummary, fontSize = 11.sp, color = DarkTextPrimary)

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Maintainer: ${report.maintainerNotes}",
                    modifier = Modifier.padding(8.dp),
                    fontSize = 10.sp,
                    color = AccentGreen
                )
            }
        }
    }
}
