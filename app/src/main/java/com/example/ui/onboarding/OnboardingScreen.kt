package com.example.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ControllerType
import com.example.model.PrivilegeMethod
import com.example.ui.MainAppViewModel
import com.example.ui.root.KernelSuWebUiScreen
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: MainAppViewModel,
    onFinish: () -> Unit
) {
    val step by viewModel.onboardingStep.collectAsState()
    val privilegeResults by viewModel.privilegeResults.collectAsState()
    val activeMethod by viewModel.activePrivilegeMethod.collectAsState()
    val controllerProfile by viewModel.controllerProfile.collectAsState()
    val context = LocalContext.current

    var showRationaleDialog by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ControlystLogoIcon(size = 24.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "CONTROLYST",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = 2.sp
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ControlystViolet.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, ControlystViolet.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "${step + 1}/6",
                                color = ControlystCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (step > 0) {
                        IconButton(
                            onClick = { viewModel.prevOnboardingStep() },
                            modifier = Modifier.testTag("onboarding_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    }
                },
                actions = {
                    TextButton(
                        onClick = onFinish,
                        modifier = Modifier.testTag("onboarding_skip_button")
                    ) {
                        Text("Skip", color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GraphiteFoundation)
            )
        },
        bottomBar = {
            Surface(
                color = DarkSurface,
                border = BorderStroke(1.dp, DarkSurfaceBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Indicator Dots
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (i in 0..5) {
                            Box(
                                modifier = Modifier
                                    .size(if (i == step) 22.dp else 8.dp, 8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (i == step) SignatureGradient
                                        else Brush.linearGradient(listOf(DarkSurfaceBorder, DarkSurfaceBorder))
                                    )
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (step < 5) {
                                viewModel.nextOnboardingStep()
                            } else {
                                onFinish()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .testTag("onboarding_next_button")
                            .height(44.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .background(SignatureGradient, RoundedCornerShape(14.dp))
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (step == 5) "Launch Controlyst" else "Continue",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = GraphiteFoundation
    ) { paddingValues ->
        var showWebUiSheet by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (step) {
                0 -> StepWelcome()
                1 -> StepRootDetection(
                    viewModel = viewModel,
                    results = privilegeResults,
                    selectedMethod = activeMethod,
                    onSelectMethod = { viewModel.overridePrivilegeMethod(it) },
                    onOpenWebUi = { showWebUiSheet = true }
                )
                2 -> StepPermissions(
                    onOpenRationale = { showRationaleDialog = it }
                )
                3 -> StepInputDevice(
                    profile = controllerProfile,
                    onRefresh = { viewModel.detectController() },
                    onOverride = { viewModel.overrideControllerType(it) }
                )
                4 -> StepCalibrationWalkthrough(viewModel = viewModel)
                5 -> StepMappingTutorial(viewModel = viewModel)
            }

            if (showWebUiSheet) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showWebUiSheet = false },
                    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        KernelSuWebUiScreen(
                            viewModel = viewModel,
                            onBack = { showWebUiSheet = false }
                        )
                    }
                }
            }
        }
    }

    // Rationale Dialog
    showRationaleDialog?.let { permKey ->
        val (title, rationale, intent) = when (permKey) {
            "ACCESSIBILITY" -> Triple(
                "Accessibility Service",
                "Controlyst uses Android Accessibility Service to perform synthetic tap and swipe gestures directly on game touchscreens without root permissions.",
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            )
            "OVERLAY" -> Triple(
                "Draw Over Other Apps",
                "Required to display the floating in-game mapping HUD, quick menu, and precision crosshairs over games.",
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            )
            "USAGE_STATS" -> Triple(
                "Game Process Detection",
                "Allows Controlyst to detect when your game starts and exits, automatically activating and cleaning up input injection services.",
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            )
            else -> Triple(
                "System Notifications",
                "Ensures the foreground mapping service stays alive in the background and sends your daily progress report.",
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            )
        }

        AlertDialog(
            onDismissRequest = { showRationaleDialog = null },
            title = { Text(title, color = CyberCyan, fontWeight = FontWeight.Bold) },
            text = { Text(rationale, color = TextPrimary) },
            confirmButton = {
                Button(
                    onClick = {
                        showRationaleDialog = null
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // ignore fallback
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                ) {
                    Text("Grant Permission", color = Color(0xFF00363D), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRationaleDialog = null }) {
                    Text("Later", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceElevated,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun StepWelcome() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))

        // Controlyst Hero Emblem with Signature Radial Glow
        Box(
            modifier = Modifier
                .size(116.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(ControlystViolet.copy(alpha = 0.35f), Color.Transparent)
                    )
                )
                .border(1.5.dp, Brush.linearGradient(listOf(ControlystViolet, ControlystCyan)), RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center
        ) {
            ControlystLogoIcon(size = 72.dp, animated = true)
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "CONTROLYST",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 3.sp
            ),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Android Game-Mapping & Performance-Tuning Engine",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = ControlystCyan,
                fontWeight = FontWeight.SemiBold
            ),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(14.dp))

        // Color tags (#5043EB, #7C8CFF, #00CFEB) from final.jpeg
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WelcomeColorTag(label = "#5043EB", name = "Control & AI", color = ControlystViolet)
            WelcomeColorTag(label = "#7C8CFF", name = "Input Layer", color = ControlystBlue)
            WelcomeColorTag(label = "#00CFEB", name = "Telemetry HUD", color = ControlystCyan)
        }

        Spacer(Modifier.height(24.dp))

        // 4 Core Architecture Pillars
        FeatureHighlightCard(
            icon = Icons.Default.Tune,
            iconTint = ControlystViolet,
            title = "In-game Mapping Editor",
            desc = "Floating relative nodes (Tap, Joystick, Camera, Swipe, Macro) that seamlessly blend into the game HUD."
        )
        Spacer(Modifier.height(10.dp))
        FeatureHighlightCard(
            icon = Icons.Default.AutoAwesome,
            iconTint = ControlystCyan,
            title = "AI-assisted HUD Detection",
            desc = "Automated scanlines and glowing ghost nodes highlighting on-screen virtual controls."
        )
        Spacer(Modifier.height(10.dp))
        FeatureHighlightCard(
            icon = Icons.Default.CenterFocusStrong,
            iconTint = ControlystBlue,
            title = "Crosshair & Live FPS Overlays",
            desc = "Tactical reticle studio and low-overhead floating performance telemetry (119 FPS, 8.4 ms)."
        )
        Spacer(Modifier.height(10.dp))
        FeatureHighlightCard(
            icon = Icons.Default.Terminal,
            iconTint = ControlystViolet,
            title = "Root WebUI Daemon",
            desc = "Embedded 17-subsystem manager for KernelSU, APatch, and Magisk CPU/GPU/thermal tuning."
        )

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun WelcomeColorTag(label: String, name: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(5.dp))
            Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun FeatureHighlightCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color = ControlystCyan,
    title: String,
    desc: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkSurfaceBorder)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                Spacer(Modifier.height(2.dp))
                Text(desc, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
fun StepRootDetection(
    viewModel: MainAppViewModel,
    results: List<com.example.injector.PrivilegeProbeResult>,
    selectedMethod: PrivilegeMethod,
    onSelectMethod: (PrivilegeMethod) -> Unit,
    onOpenWebUi: () -> Unit
) {
    val context = LocalContext.current
    val shizukuPairingState by viewModel.shizukuPairingState.collectAsState()
    var inlineCodeInput by remember { mutableStateOf("") }
    var inlinePortInput by remember { mutableStateOf("5555") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Device Privilege Detection",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Controlyst automatically detects which injection backend your device supports. You can also manually force a method below.",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
        )

        Spacer(Modifier.height(20.dp))

        results.forEach { probe ->
            val isSelected = probe.method == selectedMethod
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelectMethod(probe.method) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) DarkSurfaceElevated else DarkSurface
                ),
                border = BorderStroke(
                    width = if (isSelected) 1.8.dp else 1.dp,
                    color = if (isSelected) CyberCyan else DarkSurfaceBorder
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = probe.method.title,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) CyberCyan else TextPrimary,
                            fontSize = 15.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (probe.isDetected) AccentGreen.copy(alpha = 0.15f) else Color(0xFF334155)
                        ) {
                            Text(
                                text = if (probe.isDetected) "Detected" else "Not Present",
                                color = if (probe.isDetected) AccentGreen else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = probe.method.description,
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Status: ${probe.statusDetail}",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Est. Latency: ${probe.latencyScoreMs}ms",
                            color = CyberCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Special Shizuku Wireless Debugging Inline Notification Flow
                    if (probe.method == PrivilegeMethod.SHIZUKU && isSelected) {
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = DarkSurfaceBorder)
                        Spacer(Modifier.height(14.dp))

                        Text(
                            text = "Shizuku Wireless Debugging Helper",
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Notice: Switching away from Developer Options cancels and resets the 6-digit wireless pairing code! Controlyst posts an interactive notification with an inline text box so you can submit the code directly from the notification shade without leaving Developer Options.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )

                        Spacer(Modifier.height(10.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (shizukuPairingState.isPairingSuccessful) AccentGreen.copy(alpha = 0.15f)
                                   else if (shizukuPairingState.isHelperNotificationActive) CyberCyan.copy(alpha = 0.15f)
                                   else Color(0xFF1E293B),
                            border = BorderStroke(1.dp, if (shizukuPairingState.isPairingSuccessful) AccentGreen else DarkSurfaceBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (shizukuPairingState.isPairingSuccessful) Icons.Default.CheckCircle
                                    else if (shizukuPairingState.isHelperNotificationActive) Icons.Default.NotificationsActive
                                    else Icons.Default.Info,
                                    contentDescription = null,
                                    tint = if (shizukuPairingState.isPairingSuccessful) AccentGreen
                                           else if (shizukuPairingState.isHelperNotificationActive) CyberCyan
                                           else TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = shizukuPairingState.statusMessage,
                                    color = if (shizukuPairingState.isPairingSuccessful) AccentGreen else TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val port = inlinePortInput.toIntOrNull() ?: 5555
                                    viewModel.startShizukuPairingHelper(port)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFF00363D), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Start Notification Helper", color = Color(0xFF00363D), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.DeveloperMode, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Open Dev Options", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // In-App direct input option
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = inlineCodeInput,
                                onValueChange = { if (it.length <= 6) inlineCodeInput = it },
                                placeholder = { Text("Or enter 6 digits here", fontSize = 12.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary)
                            )
                            Button(
                                onClick = {
                                    if (inlineCodeInput.isNotBlank()) {
                                        val port = inlinePortInput.toIntOrNull() ?: 5555
                                        viewModel.submitShizukuPairingCode(inlineCodeInput, port)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                            ) {
                                Text("Pair", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    // Special KernelSU / APatch / Magisk Root Module & WebUI Flow
                    if ((probe.method == PrivilegeMethod.KERNELSU || probe.method == PrivilegeMethod.APATCH || probe.method == PrivilegeMethod.MAGISK) && isSelected) {
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = DarkSurfaceBorder)
                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "KernelSU / Root WebUI & Module",
                                    fontWeight = FontWeight.Bold,
                                    color = CyberCyan,
                                    fontSize = 14.sp
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Controlyst includes a universal /dev/uinput Kernel module with 1000Hz polling and native WebUI dashboard for KernelSU & APatch.",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onOpenWebUi,
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Web, contentDescription = null, tint = Color(0xFF00363D), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Open Root WebUI", color = Color(0xFF00363D), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = onOpenWebUi,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricViolet),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.SettingsSystemDaydream, contentDescription = null, tint = ElectricViolet, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Module Manager", color = ElectricViolet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StepPermissions(
    onOpenRationale: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Required Permissions",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Each permission has a specific purpose for input injection and in-game overlays. Tap each item to review why it's required.",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
        )

        Spacer(Modifier.height(20.dp))

        PermissionItemCard(
            title = "1. Accessibility Service",
            subtitle = "Simulates touches for non-root game mapping.",
            tag = "Essential",
            onClick = { onOpenRationale("ACCESSIBILITY") }
        )
        PermissionItemCard(
            title = "2. Draw Over Other Apps (Overlay)",
            subtitle = "Shows the floating HUD, crosshair & live editor over games.",
            tag = "Essential",
            onClick = { onOpenRationale("OVERLAY") }
        )
        PermissionItemCard(
            title = "3. Usage Access (Game Auto-Detect)",
            subtitle = "Detects game launch/exit to clean up touch injections.",
            tag = "Recommended",
            onClick = { onOpenRationale("USAGE_STATS") }
        )
        PermissionItemCard(
            title = "4. Notifications",
            subtitle = "Maintains persistent background service and sends daily progress.",
            tag = "Recommended",
            onClick = { onOpenRationale("NOTIFICATIONS") }
        )
    }
}

@Composable
fun PermissionItemCard(title: String, subtitle: String, tag: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkSurfaceBorder)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = CyberCyan.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = tag,
                            color = CyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = TextSecondary, fontSize = 13.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
        }
    }
}

@Composable
fun StepInputDevice(
    profile: com.example.model.ControllerProfile,
    onRefresh: () -> Unit,
    onOverride: (ControllerType) -> Unit
) {
    var expandedDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Input Device Detection",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Connect your gamepad via Bluetooth/USB, or switch to Mouse + Keyboard mode. If auto-detection misidentifies your layout (e.g. Stadia vs Xbox), select your controller manually.",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
        )

        Spacer(Modifier.height(20.dp))

        // Detected Controller Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
            border = BorderStroke(1.5.dp, CyberCyan)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CyberCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Gamepad, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Active Controller Profile", color = TextSecondary, fontSize = 12.sp)
                        Text(
                            text = profile.type.displayName,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onRefresh,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Re-Detect")
                    }

                    Button(
                        onClick = { expandedDropdown = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceBorder)
                    ) {
                        Text("Override Layout", color = TextPrimary)
                    }
                }
            }
        }

        // Layout Override Dropdown Menu
        if (expandedDropdown) {
            AlertDialog(
                onDismissRequest = { expandedDropdown = false },
                title = { Text("Select Controller Layout", color = CyberCyan, fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        ControllerType.values().forEach { type ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onOverride(type)
                                        expandedDropdown = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = profile.type == type,
                                    onClick = {
                                        onOverride(type)
                                        expandedDropdown = false
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = CyberCyan)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(type.displayName, color = TextPrimary, fontSize = 14.sp)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { expandedDropdown = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = DarkSurfaceElevated
            )
        }
    }
}

@Composable
fun StepCalibrationWalkthrough(viewModel: MainAppViewModel) {
    val stickState by viewModel.stickCalibrationState.collectAsState()
    val latencyResult by viewModel.touchLatencyResult.collectAsState()
    var isCalibrating by remember { mutableStateOf(false) }
    var isTestingLatency by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Quick Calibration",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Calibrate analog stick deadzone to eliminate stick drift, and benchmark touch latency for non-root injection.",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
        )

        Spacer(Modifier.height(20.dp))

        // Thumbstick Deadzone Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkSurfaceBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Adjust, contentDescription = null, tint = CyberCyan)
                    Spacer(Modifier.width(10.dp))
                    Text("Thumbstick Drift Calibration", fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Status: ${stickState.phase}",
                    color = CyberCyan,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { stickState.progressPercent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = CyberCyan,
                    trackColor = DarkSurfaceBorder
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        isCalibrating = true
                        viewModel.startStickCalibration {
                            isCalibrating = false
                        }
                    },
                    enabled = !isCalibrating,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isCalibrating) "Calibrating (Keep Centered)..." else "Start Auto Deadzone Test",
                        color = Color(0xFF00363D),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Latency Benchmark Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkSurfaceBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = ElectricViolet)
                    Spacer(Modifier.width(10.dp))
                    Text("Touch Latency Benchmark", fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (latencyResult.roundTripMs > 0) "Measured: ${latencyResult.roundTripMs}ms (${latencyResult.grade})" else "Tap to benchmark round-trip injection latency",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(14.dp))
                OutlinedButton(
                    onClick = {
                        isTestingLatency = true
                        viewModel.runLatencyBenchmark {
                            isTestingLatency = false
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricViolet)
                ) {
                    Text(if (isTestingLatency) "Benchmarking..." else "Benchmark Latency")
                }
            }
        }
    }
}

@Composable
fun StepMappingTutorial(viewModel: MainAppViewModel) {
    val activeConfig by viewModel.activeConfig.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Mini-Tutorial: Visual Mapping",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Nodes are normalized (0..1) so configs look identical on any phone, tablet, or foldable screen.",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
        )

        Spacer(Modifier.height(16.dp))

        // Sample interactive preview mini-box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0F172A))
                .border(1.5.dp, CyberCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Simulated HUD background
            Column(
                modifier = Modifier.fillMaxSize().padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("SAMPLE GAME HUD [Delta Force Mobile]", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("19.5:9 Scaled", color = CyberCyan, fontSize = 11.sp)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    // Left stick sample node
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(CyberCyan.copy(alpha = 0.2f))
                            .border(1.5.dp, CyberCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("LS Move", color = CyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    // Right fire sample node
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(AccentRose.copy(alpha = 0.25f))
                                .border(1.5.dp, AccentRose, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("RT Fire", color = AccentRose, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(AccentGreen.copy(alpha = 0.25f))
                                .border(1.5.dp, AccentGreen, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("A Jump", color = AccentGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "You're all set! Controlyst has initialized your layout profile with ${activeConfig.buttons.size} preset buttons. You can edit them at any time in the Visual Mapper tab.",
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}
