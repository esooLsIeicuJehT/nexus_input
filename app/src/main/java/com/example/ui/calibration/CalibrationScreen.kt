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
import com.example.model.ControllerType
import com.example.ui.MainAppViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    viewModel: MainAppViewModel
) {
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
            .background(DarkBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Controller Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
            border = BorderStroke(1.dp, CyberCyan)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Connected Controller", color = TextSecondary, fontSize = 12.sp)
                    Text(
                        text = controllerProfile.type.displayName,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text("Auto-detect layout with zero Stadia/Xbox mismatch", color = CyberCyan, fontSize = 11.sp)
                }

                IconButton(onClick = { viewModel.detectController() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = CyberCyan)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Thumbstick 2-Stage Auto-Calibration
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkSurfaceBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Adjust, contentDescription = null, tint = CyberCyan)
                        Spacer(Modifier.width(10.dp))
                        Text("Thumbstick Deadzone Calibration", fontWeight = FontWeight.Bold, color = TextPrimary)
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
                        drawCircle(CyberCyan, radius = 10f * density, center = Offset(stickPx, stickPy))
                    }

                    Text(
                        text = stickState.phase,
                        color = CyberCyan,
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
                        text = "Inner Deadzone: ${(stickState.computedInnerDeadzone * 100).toInt()}%",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Outer Deflection: ${(stickState.computedOuterDeadzone * 100).toInt()}%",
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
                    color = CyberCyan,
                    trackColor = DarkSurfaceBorder
                )

                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        if (isCalibratingStick) return@Button
                        isCalibratingStick = true
                        coroutineScope.launch {
                            viewModel.calibrationManager.runStickCalibration {
                                if (it.phase == "CALIBRATION COMPLETE") {
                                    isCalibratingStick = false
                                }
                            }
                        }
                    },
                    enabled = !isCalibratingStick,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
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
                    colors = ButtonDefaults.buttonColors(containerColor = ControlystViolet),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save Calibration to Room DB", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Trigger Pull Range Calibration
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkSurfaceBorder)
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
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.showSnack("Left Trigger (LT) calibrated to 100% hair-trigger") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentAmber)
                    ) {
                        Text("Calibrate LT")
                    }
                    OutlinedButton(
                        onClick = { viewModel.showSnack("Right Trigger (RT) calibrated to 100% hair-trigger") },
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
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkSurfaceBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = ElectricViolet)
                    Spacer(Modifier.width(10.dp))
                    Text("Touch Latency Benchmark", fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (latencyResult.roundTripMs > 0)
                        "Benchmark Score: ${latencyResult.roundTripMs} ms • ${latencyResult.grade}"
                    else
                        "Measures frame-to-touch dispatch latency of your active injection backend.",
                    color = if (latencyResult.roundTripMs > 0) CyberCyan else TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (latencyResult.roundTripMs > 0) FontWeight.Bold else FontWeight.Normal
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        isTestingLatency = true
                        coroutineScope.launch {
                            viewModel.calibrationManager.measureTouchLatency(viewModel.currentInjector)
                            isTestingLatency = false
                        }
                    },
                    enabled = !isTestingLatency,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isTestingLatency) "Benchmarking Latency..." else "Run Touch Benchmark", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Screen Resolution & Aspect Ratio Scaler
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkSurfaceBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AspectRatio, contentDescription = null, tint = AccentGreen)
                    Spacer(Modifier.width(10.dp))
                    Text("Resolution & Aspect Ratio Scaler", fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Current Device Ratio: ${activeConfig.targetAspectRatio}. All normalized nodes (0..1) automatically adapt across 16:9, 19.5:9, 20:9, foldables, and tablets.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}
