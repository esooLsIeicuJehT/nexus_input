package com.example.ui.mapper

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MappingConfig
import com.example.model.MappingNode
import com.example.model.NodeType
import com.example.model.*
import com.example.ui.MainAppViewModel
import com.example.ui.theme.*
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotMapperScreen(
    viewModel: MainAppViewModel
) {
    val activeConfig by viewModel.activeConfig.collectAsState()
    var selectedNodeId by remember { mutableStateOf<String?>(null) }
    var activeTool by remember { mutableStateOf("tool") } // tool, collections, controls, snaps, devices
    var snapToGrid by remember { mutableStateOf(false) }
    var isAiHudScanActive by remember { mutableStateOf(false) }
    var isLiveTestMode by remember { mutableStateOf(false) }
    var testTappedFeedback by remember { mutableStateOf<String?>(null) }
    var showAddNodeDialog by remember { mutableStateOf(false) }

    // Scanline animation for AI HUD detection
    val infiniteTransition = rememberInfiniteTransition(label = "scanline")
    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanProgress"
    )
    val ghostPulse by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ghostPulse"
    )

    val selectedNode = activeConfig.buttons.firstOrNull { it.id == selectedNodeId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GraphiteFoundation)
    ) {
        // 1. Top Header Toolbar matching final.jpeg (Tool, Collections, Controls, Snaps, Devices)
        Surface(
            color = DarkSurface,
            border = BorderStroke(1.dp, DarkSurfaceBorder)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Profile & HUD Resolution info
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = activeConfig.profileName,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = ControlystCyan.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = activeConfig.targetAspectRatio,
                                    color = ControlystCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "${activeConfig.buttons.size} Mapped Nodes • Low Latency Direct Inject",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }

                    // Quick Actions
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // AI HUD Scan toggle
                        FilterChip(
                            selected = isAiHudScanActive,
                            onClick = {
                                isAiHudScanActive = !isAiHudScanActive
                                if (isAiHudScanActive) viewModel.runAiHudScan()
                            },
                            label = {
                                Text(
                                    if (isAiHudScanActive) "AI Scanning" else "AI HUD",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.AutoFixHigh,
                                    contentDescription = null,
                                    tint = if (isAiHudScanActive) ControlystCyan else TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ControlystCyan.copy(alpha = 0.2f),
                                selectedLabelColor = ControlystCyan,
                                containerColor = DarkSurfaceElevated,
                                labelColor = TextSecondary
                            ),
                            border = BorderStroke(1.dp, if (isAiHudScanActive) ControlystCyan else DarkSurfaceBorder)
                        )

                        // Live Test Button
                        IconButton(
                            onClick = { isLiveTestMode = !isLiveTestMode },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (isLiveTestMode) AccentGreen.copy(alpha = 0.2f) else DarkSurfaceElevated
                            )
                        ) {
                            Icon(
                                if (isLiveTestMode) Icons.Default.TouchApp else Icons.Default.PlayArrow,
                                contentDescription = "Live Test",
                                tint = if (isLiveTestMode) AccentGreen else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Panic Kill Switch
                        IconButton(
                            onClick = { viewModel.triggerPanicKillSwitch() },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = AccentRose.copy(alpha = 0.15f))
                        ) {
                            Icon(Icons.Default.FlashOff, contentDescription = "Panic Kill", tint = AccentRose, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // 5-Item Tool Navigation Bar matching final.jpeg
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    val tools = listOf(
                        Triple("tool", "Tool", Icons.Default.NearMe),
                        Triple("collections", "Collections", Icons.Default.AutoAwesomeMotion),
                        Triple("controls", "Controls", Icons.Default.SportsEsports),
                        Triple("snaps", "Snaps", Icons.Default.GridOn),
                        Triple("devices", "Devices", Icons.Default.Devices)
                    )

                    tools.forEach { (id, label, icon) ->
                        val isSelected = activeTool == id || (id == "snaps" && snapToGrid)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) ControlystViolet.copy(alpha = 0.25f) else Color.Transparent,
                            border = if (isSelected) BorderStroke(1.dp, ControlystViolet) else null,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    activeTool = id
                                    if (id == "snaps") snapToGrid = !snapToGrid
                                    if (id == "controls") showAddNodeDialog = true
                                    if (id == "collections") viewModel.showSnack("Profile: ${activeConfig.profileName} loaded")
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = label,
                                    tint = if (isSelected) ControlystCyan else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = label,
                                    color = if (isSelected) ControlystCyan else TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live Test Mode Banner if active
        if (isLiveTestMode) {
            Surface(
                color = AccentGreen.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, AccentGreen)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.TouchApp, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = testTappedFeedback ?: "Tap any mapped node on the canvas to test live synthetic input injection",
                        color = AccentGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 2. Game Viewport & Mapping Canvas matching final.jpeg
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF070B14))
                .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(14.dp))
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()

            // Underlying Tactical HUD Canvas (Grid, Background HUD lines, AI Scanline, Ghost regions)
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isLiveTestMode, snapToGrid, activeConfig.buttons) {
                        if (isLiveTestMode) {
                            detectTapGestures { offset ->
                                val xNorm = offset.x / size.width
                                val yNorm = offset.y / size.height
                                val hit = activeConfig.buttons.firstOrNull { node ->
                                    val dist = hypot(xNorm - node.xNorm, yNorm - node.yNorm)
                                    dist <= (node.radiusNorm * 1.5f)
                                }
                                if (hit != null) {
                                    viewModel.currentInjector.injectTap(offset.x, offset.y)
                                    testTappedFeedback = "Triggered: ${hit.boundKey} (${hit.label}) @ (${(xNorm * 100).toInt()}%, ${(yNorm * 100).toInt()}%)"
                                }
                            }
                        } else {
                            detectTapGestures { offset ->
                                val xNorm = offset.x / size.width
                                val yNorm = offset.y / size.height
                                val hit = activeConfig.buttons.firstOrNull { node ->
                                    val dist = hypot(xNorm - node.xNorm, yNorm - node.yNorm)
                                    dist <= (node.radiusNorm * 1.8f)
                                }
                                selectedNodeId = hit?.id
                            }
                        }
                    }
                    .pointerInput(isLiveTestMode, snapToGrid, selectedNodeId) {
                        if (!isLiveTestMode) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                selectedNodeId?.let { id ->
                                    val target = activeConfig.buttons.firstOrNull { it.id == id }
                                    if (target != null) {
                                        var newX = (target.xNorm + dragAmount.x / size.width).coerceIn(0.04f, 0.96f)
                                        var newY = (target.yNorm + dragAmount.y / size.height).coerceIn(0.04f, 0.96f)
                                        if (snapToGrid) {
                                            newX = (Math.round(newX * 20) / 20f)
                                            newY = (Math.round(newY * 20) / 20f)
                                        }
                                        viewModel.updateNode(target.copy(xNorm = newX, yNorm = newY))
                                    }
                                }
                            }
                        }
                    }
            ) {
                val canvasW = size.width
                val canvasH = size.height

                // Draw Tactical Snapping Grid if enabled
                if (snapToGrid) {
                    val stepX = canvasW / 20f
                    val stepY = canvasH / 20f
                    for (i in 1..19) {
                        drawLine(
                            color = ControlystCyan.copy(alpha = 0.10f),
                            start = Offset(i * stepX, 0f),
                            end = Offset(i * stepX, canvasH),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = ControlystCyan.copy(alpha = 0.10f),
                            start = Offset(0f, i * stepY),
                            end = Offset(canvasW, i * stepY),
                            strokeWidth = 1f
                        )
                    }
                }

                // Ares Legends simulated tactical game HUD background lines
                // Left thumbstick boundary guide
                drawCircle(
                    color = Color(0x187C8CFF),
                    radius = canvasW * 0.12f,
                    center = Offset(canvasW * 0.18f, canvasH * 0.70f),
                    style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f))
                )
                // Right action cluster guide
                drawCircle(
                    color = Color(0x1400CFEB),
                    radius = canvasW * 0.16f,
                    center = Offset(canvasW * 0.85f, canvasH * 0.70f),
                    style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f))
                )

                // AI-Assisted HUD Detection Regions & Scanline (matching final.jpeg)
                if (isAiHudScanActive) {
                    // Sweeping vertical scanline
                    val scanX = canvasW * scanProgress
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                ControlystCyan.copy(alpha = 0.5f),
                                Color.White.copy(alpha = 0.8f),
                                ControlystCyan.copy(alpha = 0.5f),
                                Color.Transparent
                            ),
                            startX = scanX - 40f,
                            endX = scanX + 40f
                        ),
                        start = Offset(scanX, 0f),
                        end = Offset(scanX, canvasH),
                        strokeWidth = 3f
                    )

                    // Detected HUD Bounding Boxes
                    val detectedZones = listOf(
                        Triple("FIRE", Offset(canvasW * 0.84f, canvasH * 0.70f), canvasW * 0.07f),
                        Triple("AIM", Offset(canvasW * 0.80f, canvasH * 0.40f), canvasW * 0.06f),
                        Triple("RELOAD", Offset(canvasW * 0.74f, canvasH * 0.84f), canvasW * 0.05f),
                        Triple("JUMP", Offset(canvasW * 0.90f, canvasH * 0.56f), canvasW * 0.05f),
                        Triple("SPRINT", Offset(canvasW * 0.18f, canvasH * 0.70f), canvasW * 0.13f)
                    )

                    detectedZones.forEach { (label, center, rad) ->
                        drawCircle(
                            color = ControlystCyan.copy(alpha = ghostPulse * 0.25f),
                            radius = rad,
                            center = center
                        )
                        drawCircle(
                            color = ControlystCyan.copy(alpha = ghostPulse * 0.7f),
                            radius = rad,
                            center = center,
                            style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f))
                        )
                    }
                }
            }

            // Floating Designed Mapping Nodes matching final.jpeg (Soft purple glowing circular nodes with crisp bold white letters)
            activeConfig.buttons.forEach { node ->
                val isSelected = node.id == selectedNodeId
                val nodeSizePx = (node.radiusNorm * 2f * widthPx).coerceIn(36f, 130f)
                val nodeSizeDp = (nodeSizePx / 2.5f).dp // approximate dp conversion

                Box(
                    modifier = Modifier
                        .offset(
                            x = (node.xNorm * widthPx - nodeSizePx / 2f).dp / 2.5f,
                            y = (node.yNorm * heightPx - nodeSizePx / 2f).dp / 2.5f
                        )
                        .size(nodeSizeDp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    ControlystViolet.copy(alpha = if (isSelected) 0.85f else 0.55f),
                                    ControlystBlue.copy(alpha = if (isSelected) 0.65f else 0.35f)
                                )
                            )
                        )
                        .border(
                            width = if (isSelected) 2.5.dp else 1.5.dp,
                            brush = Brush.linearGradient(
                                colors = if (isSelected) listOf(Color.White, ControlystCyan)
                                else listOf(ControlystVioletLight, ControlystBlue)
                            ),
                            shape = CircleShape
                        )
                        .clickable { selectedNodeId = node.id },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = node.boundKey,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = if (nodeSizeDp > 50.dp) 15.sp else 12.sp
                        )
                        if (node.label.isNotBlank() && nodeSizeDp > 55.dp) {
                            Text(
                                text = node.label,
                                color = TextMuted,
                                fontSize = 8.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Explanatory watermark caption when clean
            if (!isAiHudScanActive && selectedNode == null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .background(DarkSurface.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Game Content First: Floating mapping nodes with relative controls",
                        color = TextMuted,
                        fontSize = 9.sp
                    )
                }
            }
        }

        // 3. Control Types Panel matching final.jpeg
        Surface(
            color = DarkSurfaceElevated,
            border = BorderStroke(1.dp, DarkSurfaceBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Control types",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val controlTypes = listOf(
                        Triple("Tap", NodeType.BUTTON, Icons.Default.TouchApp),
                        Triple("Joystick", NodeType.JOYSTICK_ZONE, Icons.Default.RadioButtonChecked),
                        Triple("Camera", NodeType.CAMERA_DRAG, Icons.Default.Visibility),
                        Triple("Swipe", NodeType.TURBO, Icons.Default.Swipe),
                        Triple("Macro", NodeType.MACRO, Icons.Default.AltRoute)
                    )

                    controlTypes.forEach { (name, type, icon) ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    val newNode = MappingNode(
                                        id = "node_${System.currentTimeMillis()}",
                                        xNorm = 0.5f,
                                        yNorm = 0.5f,
                                        radiusNorm = if (type == NodeType.JOYSTICK_ZONE) 0.11f else 0.055f,
                                        type = type,
                                        boundKey = if (type == NodeType.JOYSTICK_ZONE) "LS" else "A",
                                        label = name
                                    )
                                    viewModel.addNode(newNode)
                                    selectedNodeId = newNode.id
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = DarkSurface,
                            border = BorderStroke(1.dp, DarkSurfaceBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = name,
                                    tint = ControlystCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = name,
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Selected Node Inspector Panel
        if (selectedNode != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, ControlystViolet)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ControlystViolet
                            ) {
                                Text(
                                    text = selectedNode.boundKey,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = selectedNode.label.ifEmpty { "Node ${selectedNode.id}" },
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        IconButton(
                            onClick = {
                                viewModel.removeNode(selectedNode.id)
                                selectedNodeId = null
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AccentRose, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Size Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Size", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.width(32.dp))
                        Slider(
                            value = selectedNode.radiusNorm,
                            onValueChange = { newRadius ->
                                viewModel.updateNode(selectedNode.copy(radiusNorm = newRadius))
                            },
                            valueRange = 0.03f..0.18f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = ControlystCyan,
                                activeTrackColor = ControlystCyan
                            )
                        )
                        Text(
                            text = "${(selectedNode.radiusNorm * 100).toInt()}%",
                            color = ControlystCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(32.dp)
                        )
                    }

                    // Key binding buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        listOf("A", "B", "X", "Y", "LT", "RT", "LB", "RB", "L3", "RS").forEach { key ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (selectedNode.boundKey == key) ControlystCyan else DarkSurfaceElevated,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        viewModel.updateNode(selectedNode.copy(boundKey = key))
                                    }
                            ) {
                                Text(
                                    text = key,
                                    color = if (selectedNode.boundKey == key) Color(0xFF00363D) else TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Node Dialog
    if (showAddNodeDialog) {
        var newBoundKey by remember { mutableStateOf("A") }
        var newLabel by remember { mutableStateOf("") }
        var newType by remember { mutableStateOf(NodeType.BUTTON) }

        AlertDialog(
            onDismissRequest = { showAddNodeDialog = false },
            title = { Text("Add Mapping Node", color = ControlystCyan, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newBoundKey,
                        onValueChange = { newBoundKey = it.uppercase() },
                        label = { Text("Bound Key / Button") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ControlystCyan)
                    )
                    OutlinedTextField(
                        value = newLabel,
                        onValueChange = { newLabel = it },
                        label = { Text("HUD Action Label (e.g. Fire, Jump)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ControlystCyan)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val node = MappingNode(
                            id = "btn_${System.currentTimeMillis()}",
                            xNorm = 0.5f,
                            yNorm = 0.5f,
                            radiusNorm = 0.055f,
                            type = newType,
                            boundKey = newBoundKey,
                            label = newLabel
                        )
                        viewModel.addNode(node)
                        showAddNodeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ControlystCyan)
                ) {
                    Text("Place Node", color = Color(0xFF00363D), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddNodeDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }

    // AI HUD Candidates Review Dialog
    val aiCandidates by viewModel.aiHudCandidates.collectAsState()
    if (aiCandidates.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAiHudCandidates() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = ControlystCyan)
                    Spacer(Modifier.width(8.dp))
                    Text("AI HUD Review (${aiCandidates.size} Elements)", color = ControlystCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "AI Vision analyzed the HUD layout and detected the following controls. Confirm or reject bindings:",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    aiCandidates.forEach { candidate ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = BorderStroke(1.dp, DarkSurfaceBorder)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(candidate.predictedAction, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimary)
                                    Text("Confidence ${(candidate.confidence * 100).toInt()}% • Pos: (${(candidate.xNorm * 100).toInt()}%, ${(candidate.yNorm * 100).toInt()}%)", fontSize = 10.sp, color = TextSecondary)
                                }
                                Surface(
                                    color = ControlystCyan,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        candidate.recommendedKey,
                                        color = Color(0xFF00363D),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmAiHudCandidates(aiCandidates) },
                    colors = ButtonDefaults.buttonColors(containerColor = ControlystCyan)
                ) {
                    Text("Apply All Detected", color = Color(0xFF00363D), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAiHudCandidates() }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }
}
