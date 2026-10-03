package com.example.ui.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PerformanceMode
import com.example.ui.MainAppViewModel
import com.example.ui.theme.*

/**
 * Android APK Dashboard component matching final.jpeg phone screen layout:
 * - Clean, engineered typography
 * - Profile switcher dropdown with #5043EB, #7C8CFF, #00CFEB color tags
 * - Telemetry 2x4 engineered numerical grid
 * - Performance modes with soft rounded panels (16-22dp)
 * - Gaming mode highlighted with The Signature Gradient
 * - Circular activation motion gauge (300-400ms ring)
 * - STATUS PILLS: CONNECTED, ROOTED, 1000Hz
 */
@Composable
fun ControlystPhoneDashboard(
    viewModel: MainAppViewModel,
    modifier: Modifier = Modifier
) {
    val activeProfile by viewModel.activeConfig.collectAsState()
    val activePrivilege by viewModel.activePrivilegeMethod.collectAsState()
    var selectedMode by remember { mutableStateOf(PerformanceMode.GAMING) }
    var showProfileMenu by remember { mutableStateOf(false) }

    // Activation motion animation (300-400 ms loop or trigger)
    val infiniteTransition = rememberInfiniteTransition(label = "ring_anim")
    val ringAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rot"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        // 1. Profile Switcher Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dashboard_profile_switcher"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkSurfaceBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showProfileMenu = !showProfileMenu },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Profile switcher",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        if (showProfileMenu) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Dropdown",
                        tint = TextSecondary
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Color Tags (#5043EB, #7C8CFF, #00CFEB) as shown in final.jpeg
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ColorTagPill(
                        label = "#5043EB",
                        color = ControlystViolet,
                        active = selectedMode == PerformanceMode.GAMING
                    ) {
                        selectedMode = PerformanceMode.GAMING
                        viewModel.setPerformanceProfile(PerformanceMode.GAMING)
                    }
                    ColorTagPill(
                        label = "#7C8CFF",
                        color = ControlystBlue,
                        active = false
                    ) {
                        viewModel.showSnack("Input Layer #7C8CFF engaged")
                    }
                    ColorTagPill(
                        label = "#00CFEB",
                        color = ControlystCyan,
                        active = false
                    ) {
                        viewModel.showSnack("Telemetry Stream #00CFEB synced")
                    }
                }

                if (showProfileMenu) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Active: ${activeProfile.profileName} • ${activeProfile.buttons.size} Mappings",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 1.5 Firebase Cloud Sync & Auth Card
        FirebaseCloudSyncCard(viewModel = viewModel)

        Spacer(Modifier.height(14.dp))

        // 2. Telemetry Card (Engineered 2x4 Metric Grid)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dashboard_telemetry_card"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkSurfaceBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Telemetry",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(12.dp))

                // Row 1: 119 FPS, 8.4 ms, 2.84 GHz GPU, 940 MHz GPU
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TelemetryMetricItem(value = "119", unit = "FPS", color = ControlystCyan, modifier = Modifier.weight(1f))
                    TelemetryMetricItem(value = "8.4", unit = "ms", color = ControlystCyan, modifier = Modifier.weight(1f))
                    TelemetryMetricItem(value = "2.84", unit = "GHz GPU", color = ControlystCyan, modifier = Modifier.weight(1.1f))
                    TelemetryMetricItem(value = "940", unit = "MHz GPU", color = ControlystCyan, modifier = Modifier.weight(1.1f))
                }

                Spacer(Modifier.height(14.dp))

                // Row 2: 3.6 MAX, 3.7 RAM, 41.2 TEMP, 6.8 GR RAM
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TelemetryMetricItem(value = "3.6", unit = "MAX", color = ControlystCyan, modifier = Modifier.weight(1f))
                    TelemetryMetricItem(value = "3.7", unit = "RAM", color = ControlystCyan, modifier = Modifier.weight(1f))
                    TelemetryMetricItem(value = "41.2", unit = "TEMP °C", color = ControlystOrange, modifier = Modifier.weight(1.1f))
                    TelemetryMetricItem(value = "6.8", unit = "GB RAM", color = ControlystCyan, modifier = Modifier.weight(1.1f))
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 3. Performance Modes Section
        Text(
            text = "Performance modes",
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        Spacer(Modifier.height(10.dp))

        // Grid of 16-22dp soft rounded panels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Gaming Mode (Active with The Signature Gradient)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (selectedMode == PerformanceMode.GAMING) SignatureGradient
                        else Brush.linearGradient(listOf(DarkSurfaceElevated, DarkSurfaceElevated))
                    )
                    .border(
                        1.dp,
                        if (selectedMode == PerformanceMode.GAMING) Color.Transparent else DarkSurfaceBorder,
                        RoundedCornerShape(18.dp)
                    )
                    .clickable {
                        selectedMode = PerformanceMode.GAMING
                        viewModel.setPerformanceProfile(PerformanceMode.GAMING)
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.SportsEsports,
                        contentDescription = "Gaming",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Gaming",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            // Balanced Mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (selectedMode == PerformanceMode.BALANCED) SignatureGradient
                        else Brush.linearGradient(listOf(DarkSurface, DarkSurface))
                    )
                    .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(18.dp))
                    .clickable {
                        selectedMode = PerformanceMode.BALANCED
                        viewModel.setPerformanceProfile(PerformanceMode.BALANCED)
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Balance,
                        contentDescription = "Balanced",
                        tint = ControlystBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Balanced",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Eco Mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (selectedMode == PerformanceMode.ECO) SignatureGradient
                        else Brush.linearGradient(listOf(DarkSurface, DarkSurface))
                    )
                    .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(18.dp))
                    .clickable {
                        selectedMode = PerformanceMode.ECO
                        viewModel.setPerformanceProfile(PerformanceMode.ECO)
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Eco,
                        contentDescription = "Eco",
                        tint = ControlystGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Eco",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }

            // Extreme Mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (selectedMode == PerformanceMode.EXTREME) SignatureGradient
                        else Brush.linearGradient(listOf(DarkSurface, DarkSurface))
                    )
                    .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(18.dp))
                    .clickable {
                        selectedMode = PerformanceMode.EXTREME
                        viewModel.setPerformanceProfile(PerformanceMode.EXTREME)
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocalFireDepartment,
                        contentDescription = "Extreme",
                        tint = ControlystOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Extreme",
                        color = ControlystOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Custom Mode + Activation Motion Ring
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Custom Card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(18.dp))
                    .clickable {
                        viewModel.showSnack("Custom frequency scheduler unlocked")
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.DirectionsRun,
                        contentDescription = "Custom",
                        tint = ControlystViolet,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Custom",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }

            // Activation Motion Gauge Ring (One recognizable activation motion 300-400 ms)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(44.dp)) {
                    val strokeW = 4.dp.toPx()
                    // Track
                    drawCircle(
                        color = Color(0x225043EB),
                        radius = size.width / 2f - strokeW,
                        style = Stroke(width = strokeW)
                    )
                    // Glowing Gradient Arc
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(ControlystViolet, ControlystBlue, ControlystCyan, ControlystViolet)
                        ),
                        startAngle = ringAngle,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(strokeW, strokeW),
                        size = Size(size.width - strokeW * 2, size.height - strokeW * 2),
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 4. STATUS PILLS
        Text(
            text = "STATUS PILLS",
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        Spacer(Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // CONNECTED Pill
            StatusPill(
                label = "CONNECTED",
                accentColor = ControlystGreen
            )

            // ROOTED Pill
            StatusPill(
                label = activePrivilege.badgeLabel,
                accentColor = ControlystCyan
            )

            // 1000Hz POLLING Pill
            StatusPill(
                label = "1000Hz POLLING",
                accentColor = ControlystViolet
            )
        }
    }
}

@Composable
private fun ColorTagPill(
    label: String,
    color: Color,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (active) color.copy(alpha = 0.25f) else Color(0xFF191E2C),
        border = BorderStroke(1.dp, if (active) color else Color(0xFF262E42))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = if (active) color else TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TelemetryMetricItem(
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = value,
            color = color,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 22.sp
        )
        Text(
            text = unit,
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StatusPill(
    label: String,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = accentColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
    ) {
        Text(
            text = label,
            color = accentColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

@Composable
fun FirebaseCloudSyncCard(viewModel: MainAppViewModel) {
    val authState by viewModel.authState.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("firebase_cloud_sync_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkSurfaceBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = "Cloud",
                        tint = ControlystCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Firebase Cloud & Auth",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                StatusPill(
                    label = if (authState != null) "AUTH ACTIVE" else "GUEST",
                    accentColor = if (authState != null) ControlystGreen else TextMuted
                )
            }

            Spacer(Modifier.height(12.dp))

            if (authState != null) {
                Text(
                    text = "Signed in as: ${authState?.email ?: authState?.displayName ?: "Google User"} (UID: ${authState?.uid?.take(8)}...)",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.syncActiveConfigToFirestore() },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("firestore_sync_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = ControlystViolet),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sync to Firestore", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { viewModel.signOutFirebase() },
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("firebase_signout_button"),
                        border = BorderStroke(1.dp, DarkSurfaceBorder),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Sign Out", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            } else {
                Text(
                    text = "Securely identify with Google Sign-In and persist your custom mapping profiles to Cloud Firestore.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.signInWithGoogle() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("google_signin_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Google",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Sign in with Google",
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
