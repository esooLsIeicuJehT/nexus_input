package com.example.ui.calibration

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.nexus.NexusControllerTester
import com.example.ui.MainAppViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    viewModel: MainAppViewModel
) {
    val accent = MaterialTheme.colorScheme.primary
    val stickState by viewModel.stickCalibrationState.collectAsState()
    val triggerState by viewModel.triggerCalibrationState.collectAsState()
    val latencyResult by viewModel.touchLatencyResult.collectAsState()
    val controllerProfile by viewModel.controllerProfile.collectAsState()
    val activeConfig by viewModel.activeConfig.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    var isCalibratingStick by remember { mutableStateOf(false) }
    var isTestingLatency by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        NexusControllerTester(viewModel)
        Spacer(Modifier.height(16.dp))
        Text("CALIBRATION TOOLS", color = accent, fontWeight = FontWeight.Bold)
        Text("Calibration samples Android motion events. Move the connected controller during each requested phase.", color = TextSecondary, fontSize = 12.sp)
        Text("Controller motor testing is unavailable in this build; no rumble commands are sent.", color = TextMuted, fontSize = 10.sp)
        Spacer(Modifier.height(12.dp))

        // Thumbstick 2-Stage Auto-Calibration
        Card(
            modifier = Modifier.fillMaxWidth().nexusGlass(16.dp, accent),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(0.dp, Color.Transparent)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Adjust, contentDescription = null, tint = accent)
                        Spacer(Modifier.width(10.dp))
                        Text("Left stick deadzone calibration", fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Stick Visualizer Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0D131F)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val maxR = 60f * density

                        // Outer boundary
                        drawCircle(Color(0x3300F0FF), radius = maxR, center = Offset(cx, cy), style = Stroke(2f))
                        // Inner deadzone
                        drawCircle(
                            Color(0x22F43F5E),
                            radius = maxR * stickState.computedInnerDeadzone,
                            center = Offset(cx, cy)
                        )
                        // Stick position
                        val stickPx = cx + (stickState.currentX * maxR)
                        val stickPy = cy + (stickState.currentY * maxR)
                        if (stickState.isMeasured || stickState.progressPercent > 0f) {
                            drawCircle(accent, radius = 10f * density, center = Offset(stickPx, stickPy))
                        }
                    }

                    Text(
                        text = stickState.error ?: stickState.phase,
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(8.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (stickState.isMeasured) "Inner deadzone: ${(stickState.computedInnerDeadzone * 100).toInt()}%" else "Inner deadzone: not measured",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = if (stickState.isMeasured) "Outer deflection: ${(stickState.computedOuterDeadzone * 100).toInt()}%" else "Outer deflection: not measured",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }

                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { stickState.progressPercent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = accent,
                    trackColor = DarkSurfaceBorder
                )

                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        if (isCalibratingStick) return@Button
                        isCalibratingStick = true
                        coroutineScope.launch {
                            try { viewModel.calibrationManager.runStickCalibration {
                                if (it.phase == "CALIBRATION COMPLETE") {
                                    isCalibratingStick = false
                                }
                            } } finally { isCalibratingStick = false }
                        }
                    },
                    enabled = !isCalibratingStick && controllerProfile.connected,
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isCalibratingStick) "Sampling Deadzone (Rest -> Deflect)..." else "Run Auto-Calibration",
                        color = Color(0xFF00363D),
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        viewModel.saveCalibrationToRoom(
                            stickState.computedInnerDeadzone,
                            stickState.computedOuterDeadzone
                        )
                    },
                    enabled = stickState.isMeasured,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save measured calibration", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Trigger Pull Range Calibration
        Card(
            modifier = Modifier.fillMaxWidth().nexusGlass(16.dp, accent),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(0.dp, Color.Transparent)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = AccentAmber)
                    Spacer(Modifier.width(10.dp))
                    Text("Trigger Pull Range (LT / RT)", fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Calibrate analog trigger travel to prevent dead-travel and trigger hair-trigger responsiveness.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Text(triggerState.phase + (if (triggerState.phase.startsWith("REST") || triggerState.phase.startsWith("PULL") || triggerState.isMeasured) " • ${triggerState.currentPull}" else "") + (triggerState.error?.let { " • $it" } ?: ""), color = if (triggerState.error != null) AccentRose else TextSecondary)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.calibrateTriggerAndSave(true) },
                        enabled = controllerProfile.connected,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentAmber)
                    ) {
                        Text("Calibrate LT")
                    }
                    OutlinedButton(
                        onClick = { viewModel.calibrateTriggerAndSave(false) },
                        enabled = controllerProfile.connected,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentAmber)
                    ) {
                        Text("Calibrate RT")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Touch Injection Latency Benchmark
        Card(
            modifier = Modifier.fillMaxWidth().nexusGlass(16.dp, accent),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(0.dp, Color.Transparent)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = ElectricViolet)
                    Spacer(Modifier.width(10.dp))
                    Text("Backend call timing", fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (latencyResult.roundTripMs > 0)
                        "Backend call: ${latencyResult.roundTripMs} ms • ${latencyResult.grade}"
                    else
                        latencyResult.grade + ". Includes backend preparation; does not measure screen presentation.",
                    color = if (latencyResult.roundTripMs > 0) accent else TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (latencyResult.roundTripMs > 0) FontWeight.Bold else FontWeight.Normal
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val injector=viewModel.currentInjector
                        if(injector==null) {
                            viewModel.showSnack("No backend is available to time.")
                            return@Button
                        }
                        isTestingLatency = true
                        coroutineScope.launch {
                            try { viewModel.calibrationManager.measureTouchLatency(injector) }
                            finally { isTestingLatency = false }
                        }
                    },
                    enabled = !isTestingLatency && viewModel.currentInjector != null,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isTestingLatency) "Timing request..." else "Time backend call", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Screen Resolution & Aspect Ratio Scaler
        Card(
            modifier = Modifier.fillMaxWidth().nexusGlass(16.dp, accent),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(0.dp, Color.Transparent)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AspectRatio, contentDescription = null, tint = AccentGreen)
                    Spacer(Modifier.width(10.dp))
                    Text("Resolution & Aspect Ratio Scaler", fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Profile target ratio: ${activeConfig.targetAspectRatio}. The mapper stores normalized node positions. Verify alignment in the actual game and orientation.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}
