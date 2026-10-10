package com.example.ui.mapper

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.input.ControllerBindingAliases
import com.example.model.*
import com.example.ui.MainAppViewModel
import com.example.ui.theme.*
import kotlin.math.hypot

/**
 * NEXUS screenshot mapper.
 *
 * The mapper edits the real saved profile. No preview-only state is used for bindings,
 * stick tuning, trigger thresholds, sizing, or camera inversion.
 */
@Composable
fun ScreenshotMapperScreen(viewModel: MainAppViewModel) {
    val config by viewModel.activeConfig.collectAsState()
    val live by com.example.input.ControllerInputMonitor.state.collectAsState()
    var learning by remember { mutableStateOf(false) }
    var learnAfter by remember { mutableStateOf(0L) }
    var learnedKey by remember { mutableStateOf<Int?>(null) }
    var learnedScan by remember { mutableStateOf<Int?>(null) }
    val inspectorHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * .30f)
        .coerceIn(100f, 220f).dp
    val screenshot by viewModel.screenshot.collectAsState()
    val candidates by viewModel.aiHudCandidates.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(viewModel::importScreenshot)
    }

    var selected by remember(config.id) { mutableStateOf<String?>(null) }
    var showBinding by remember { mutableStateOf(false) }
    var binding by remember { mutableStateOf("A") }
    var behavior by remember { mutableStateOf(ButtonBehavior.TAP) }
    var turbo by remember { mutableStateOf(false) }
    var snap by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(showBinding, learning, live.lastPress?.sequence) {
        val press = live.lastPress
        if (showBinding && learning && press != null && press.sequence > learnAfter &&
            (press.keyCode != android.view.KeyEvent.KEYCODE_UNKNOWN || press.scanCode > 0)) {
            val code = press.keyCode
            val aliases = press.aliases
            if (aliases.isNotEmpty()) binding = ControllerBindingAliases.canonical(aliases.first())
            // Preserve raw identity for vendor keys rather than assigning a guessed code.
            learnedKey = code
            learnedScan = press.scanCode.takeIf { it > 0 }
            learning = false
        }
    }

    val currentConfig by rememberUpdatedState(config)
    val node = config.buttons.firstOrNull { it.id == selected }
    val density = LocalDensity.current

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        NexusMapperHeader(
            profileName = config.profileName,
            gameTitle = config.gameTitle.ifBlank { config.gamePackage.ifBlank { "NO GAME SELECTED" } },
            nodeCount = config.buttons.size,
            hasScreenshot = screenshot != null,
            gridEnabled = snap,
            invertedY = config.camera.invertY,
            onImport = { picker.launch(arrayOf("image/*")) },
            onCapture = viewModel::captureScreenshot,
            onFindRegions = viewModel::runAiHudScan,
            onAddInput = {
                learnedKey = null; learnedScan = null; learning = false
                binding = "A"
                behavior = ButtonBehavior.TAP
                turbo = false
                selected = null
                showBinding = true
            },
            onToggleGrid = { snap = !snap },
            onMacro = { viewModel.selectTab("macro") },
            onToggleInvertY = {
                viewModel.updateActiveConfig(
                    config.copy(camera = config.camera.copy(invertY = !config.camera.invertY))
                )
            },
            canEdit = config.gamePackage.isNotBlank(),
            canScan = screenshot != null
        )

        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            val ratio = screenshot?.let { it.width.toFloat() / it.height } ?: 16f / 9f
            val frameWidth = minOf(maxWidth, maxHeight * ratio)
            val frameHeight = frameWidth / ratio
            val frameShape = RoundedCornerShape(18.dp)

            Box(
                Modifier
                    .size(frameWidth, frameHeight)
                    .align(Alignment.Center)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                NexusViolet.copy(alpha = .78f),
                                NexusCyan.copy(alpha = .86f),
                                Color(0xFF246BFF).copy(alpha = .72f)
                            )
                        ),
                        frameShape
                    )
                    .padding(1.dp)
                    .background(Color(0xFF040914), frameShape)
                    .padding(3.dp)
                    .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(15.dp))
            ) {
                screenshot?.let {
                    Image(
                        it.asImageBitmap(),
                        "Real game screenshot",
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                }

                if (screenshot == null) {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "NEXUS MAPPING CANVAS",
                            color = NexusCyan,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Import a screenshot, capture the game, or place controls manually.",
                            color = TextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(config.id) {
                            detectTapGestures { point ->
                                selected = currentConfig.buttons.minByOrNull {
                                    hypot(
                                        it.xNorm * (size.width - 1) - point.x,
                                        it.yNorm * (size.height - 1) - point.y
                                    )
                                }?.takeIf {
                                    hypot(
                                        it.xNorm * (size.width - 1) - point.x,
                                        it.yNorm * (size.height - 1) - point.y
                                    ) < 32 * density.density
                                }?.id
                            }
                        }
                        .pointerInput(config.id, snap) {
                            detectDragGestures(
                                onDragStart = { point ->
                                    selected = currentConfig.buttons.minByOrNull {
                                        hypot(
                                            it.xNorm * (size.width - 1) - point.x,
                                            it.yNorm * (size.height - 1) - point.y
                                        )
                                    }?.takeIf {
                                        hypot(
                                            it.xNorm * (size.width - 1) - point.x,
                                            it.yNorm * (size.height - 1) - point.y
                                        ) < 32 * density.density
                                    }?.id
                                    drag = selected?.let { id ->
                                        currentConfig.buttons.first { it.id == id }.let {
                                            Offset(it.xNorm, it.yNorm)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    val position = drag
                                    currentConfig.buttons.firstOrNull { it.id == selected }?.let { target ->
                                        if (position != null) {
                                            viewModel.updateNode(
                                                target.copy(xNorm = position.x, yNorm = position.y)
                                            )
                                        }
                                    }
                                    drag = null
                                },
                                onDragCancel = { drag = null }
                            ) { change, amount ->
                                change.consume()
                                drag = drag?.let {
                                    var x = (it.x + amount.x / (size.width - 1)).coerceIn(0f, 1f)
                                    var y = (it.y + amount.y / (size.height - 1)).coerceIn(0f, 1f)
                                    if (snap) {
                                        x = kotlin.math.round(x * 20) / 20
                                        y = kotlin.math.round(y * 20) / 20
                                    }
                                    Offset(x, y)
                                }
                            }
                        }
                ) {
                    if (snap) {
                        for (i in 1..19) {
                            drawLine(
                                NexusCyan.copy(alpha = .12f),
                                Offset(size.width * i / 20, 0f),
                                Offset(size.width * i / 20, size.height)
                            )
                            drawLine(
                                NexusCyan.copy(alpha = .12f),
                                Offset(0f, size.height * i / 20),
                                Offset(size.width, size.height * i / 20)
                            )
                        }
                    }

                    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.WHITE
                        textSize = 12 * density.density
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = true
                    }

                    config.buttons.forEach { item ->
                        val norm = if (item.id == selected) {
                            drag ?: Offset(item.xNorm, item.yNorm)
                        } else {
                            Offset(item.xNorm, item.yNorm)
                        }
                        val center = Offset(norm.x * (size.width - 1), norm.y * (size.height - 1))
                        val radius = maxOf(
                            18 * density.density,
                            item.radiusNorm * minOf(size.width, size.height)
                        )
                        val selectedNow = item.id == selected

                        drawCircle(
                            if (selectedNow) NexusViolet.copy(alpha = .42f) else Color(0xFF071B2A).copy(alpha = .58f),
                            radius + if (selectedNow) 5 * density.density else 2 * density.density,
                            center
                        )
                        drawCircle(
                            if (selectedNow) Color.White else NexusCyan,
                            radius,
                            center,
                            style = Stroke(if (selectedNow) 3 * density.density else 2 * density.density)
                        )
                        if (selectedNow) {
                            drawCircle(
                                NexusCyan.copy(alpha = .34f),
                                radius + 7 * density.density,
                                center,
                                style = Stroke(1 * density.density)
                            )
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            item.boundKey,
                            center.x,
                            center.y + 4 * density.density,
                            textPaint
                        )
                    }
                }

                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MapperPill(if (snap) "GRID 5%" else "FREE MOVE", snap)
                    MapperPill("${config.buttons.size} INPUTS", true)
                }

                if (config.camera.invertY) {
                    Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                        MapperPill("RS Y INVERTED", true, accent = NexusViolet)
                    }
                }
            }
        }

        if (node != null) {
            SelectedControlInspector(
                modifier = Modifier.heightIn(max = inspectorHeight),
                config = config,
                node = node,
                onRebind = {
                    learnedKey = node.inputKeyCode; learnedScan = node.inputScanCode; learning = false
                    binding = node.boundKey
                    behavior = node.buttonBehavior
                    turbo = node.type == NodeType.TURBO
                    showBinding = true
                },
                onDelete = {
                    viewModel.removeNode(node.id)
                    selected = null
                },
                onResize = { newRadius ->
                    viewModel.updateNode(node.copy(radiusNorm = newRadius.coerceIn(.02f, .30f)))
                },
                onUpdateJoystick = { settings ->
                    viewModel.updateActiveConfig(config.copy(joystick = settings))
                },
                onUpdateCamera = { settings ->
                    viewModel.updateActiveConfig(config.copy(camera = settings))
                },
                onUpdateNode = viewModel::updateNode
            )
        } else {
            NexusHintBar(
                "Tap a mapped control to edit it. Drag directly on the screenshot to reposition it."
            )
        }
    }

    if (showBinding) {
        AlertDialog(
            onDismissRequest = { showBinding = false },
            containerColor = Color(0xFF07111D),
            title = {
                Text(
                    if (selected == null) "ADD PHYSICAL INPUT" else "REBIND INPUT",
                    color = NexusCyan,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Choose the controller input that will drive this touch target.",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(10.dp))
                    ControllerBindingAliases.supported.sorted().chunked(4).forEach { keys ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            keys.forEach { key ->
                                FilterChip(
                                    selected = binding == key,
                                    onClick = {
                                        learnedKey = null; learnedScan = null; learning = false
                                        binding = key
                                        behavior = if (key in setOf("LT", "RT")) {
                                            ButtonBehavior.HOLD
                                        } else {
                                            ButtonBehavior.TAP
                                        }
                                    },
                                    label = { Text(key) }
                                )
                            }
                        }
                    }
                    OutlinedButton(onClick = {
                        learnAfter = live.lastPress?.sequence ?: 0L
                        learning = true
                        learnedKey = null; learnedScan = null
                    }, enabled = binding !in setOf("LS", "RS")) {
                        Text(if (learning) "Press the physical button…" else "Learn physical button")
                    }
                    learnedKey?.let { Text("Observed key $it · scan ${learnedScan ?: "none"}", color = NexusCyan) }
                    Text("Home/Assistant may be reserved by Android or controller firmware. Learn uses only events received from your controller.",
                        color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    if (binding !in setOf("LS", "RS") && node?.type != NodeType.MACRO) {
                        MapperToggleRow(
                            label = "Turbo repeat",
                            detail = "${node?.turboHz ?: 10} Hz",
                            checked = turbo,
                            onCheckedChange = { turbo = it }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ButtonBehavior.entries.forEach { value ->
                            FilterChip(
                                selected = behavior == value,
                                onClick = { behavior = value },
                                label = { Text(value.name) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val type = when (binding) {
                        "LS" -> NodeType.JOYSTICK_ZONE
                        "RS" -> NodeType.CAMERA_DRAG
                        else -> if (node?.type == NodeType.MACRO) {
                            NodeType.MACRO
                        } else if (turbo) {
                            NodeType.TURBO
                        } else {
                            NodeType.BUTTON
                        }
                    }
                    val updated = node?.copy(
                        boundKey = binding,
                        type = type,
                        buttonBehavior = behavior,
                        inputKeyCode = learnedKey,
                        inputScanCode = learnedScan,
                        axisX = null,
                        axisY = null
                    ) ?: MappingNode(
                        java.util.UUID.randomUUID().toString(),
                        .5f,
                        .5f,
                        radiusNorm = if (type == NodeType.BUTTON) .05f else .12f,
                        type = type,
                        boundKey = binding,
                        buttonBehavior = behavior,
                        inputKeyCode = learnedKey,
                        inputScanCode = learnedScan
                    )
                    if (node == null) viewModel.addNode(updated) else viewModel.updateNode(updated)
                    selected = updated.id
                    showBinding = false
                }) {
                    Text("SAVE INPUT", color = NexusCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBinding = false }) { Text("Cancel") }
            }
        )
    }

    if (candidates.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAiHudCandidates,
            containerColor = Color(0xFF07111D),
            title = { Text("REVIEW DETECTED REGIONS", color = NexusCyan) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "These are image-contrast candidates, not guaranteed controls. Assign only regions you recognize.",
                        color = TextMuted
                    )
                    candidates.forEach { candidate ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${(candidate.xNorm * 100).toInt()}%, ${(candidate.yNorm * 100).toInt()}% · edge ${(candidate.confidence * 100).toInt()}%",
                            color = TextSecondary
                        )
                        OutlinedTextField(
                            candidate.recommendedKey,
                            { viewModel.assignHudCandidateInput(candidate.id, it) },
                            label = { Text("Physical input, e.g. A, RT, LS") },
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmAiHudCandidates(candidates) }) {
                    Text("APPLY ASSIGNED", color = NexusCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissAiHudCandidates) { Text("Discard") }
            }
        )
    }
}

@Composable
private fun NexusMapperHeader(
    profileName: String,
    gameTitle: String,
    nodeCount: Int,
    hasScreenshot: Boolean,
    gridEnabled: Boolean,
    invertedY: Boolean,
    onImport: () -> Unit,
    onCapture: () -> Unit,
    onFindRegions: () -> Unit,
    onAddInput: () -> Unit,
    onToggleGrid: () -> Unit,
    onMacro: () -> Unit,
    onToggleInvertY: () -> Unit,
    canEdit: Boolean,
    canScan: Boolean
) {
    var more by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(8.dp).nexusGlass(16.dp).padding(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("MAPPING STUDIO", color = NexusCyan, fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleSmall)
                Text("$profileName · $nodeCount maps", color = TextSecondary, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall)
            }
            MapperActionButton("IMPORT", onImport)
            Spacer(Modifier.width(6.dp))
            MapperActionButton("+ INPUT", onAddInput, canEdit, strong = true)
            Box {
                TextButton(onClick = { more = true }) { Text("•••", color = NexusCyan) }
                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                    Text(gameTitle, Modifier.padding(12.dp), color = TextSecondary, maxLines = 2)
                    DropdownMenuItem(text = { Text("Capture game screen") }, onClick = { more = false; onCapture() })
                    DropdownMenuItem(text = { Text("Scan HUD regions") }, enabled = canScan,
                        onClick = { more = false; onFindRegions() })
                    DropdownMenuItem(text = { Text(if (gridEnabled) "Turn grid off" else "Turn grid on") },
                        onClick = { more = false; onToggleGrid() })
                    DropdownMenuItem(text = { Text("Macro editor") }, enabled = canEdit,
                        onClick = { more = false; onMacro() })
                    DropdownMenuItem(text = { Text(if (invertedY) "Restore camera Y" else "Invert camera Y") }, enabled = canEdit,
                        onClick = { more = false; onToggleInvertY() })
                }
            }
        }
    }
}

@Composable
private fun SelectedControlInspector(
    modifier: Modifier = Modifier,
    config: MappingConfig,
    node: MappingNode,
    onRebind: () -> Unit,
    onDelete: () -> Unit,
    onResize: (Float) -> Unit,
    onUpdateJoystick: (JoystickSettings) -> Unit,
    onUpdateCamera: (CameraSettings) -> Unit,
    onUpdateNode: (MappingNode) -> Unit
) {
    Column(
        modifier.fillMaxWidth().nexusGlass(18.dp)
            .verticalScroll(rememberScrollState()).padding(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "SELECTED  •  ${node.boundKey}",
                    color = NexusCyan,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "${node.type}  •  ${node.buttonBehavior}",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MapperActionButton("REBIND", onRebind)
                MapperActionButton("DELETE", onDelete)
            }
        }

        Spacer(Modifier.height(8.dp))

        Text("TOUCH TARGET SIZE", color = TextSecondary, fontWeight = FontWeight.SemiBold)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallSquareButton("−") { onResize(node.radiusNorm - .01f) }
            Text("${(node.radiusNorm * 100).toInt()}%", color = TextPrimary, fontWeight = FontWeight.Bold)
            Slider(
                value = node.radiusNorm,
                onValueChange = onResize,
                valueRange = .02f.. .30f,
                modifier = Modifier.weight(1f)
            )
            SmallSquareButton("+") { onResize(node.radiusNorm + .01f) }
        }

        if (node.type == NodeType.JOYSTICK_ZONE) {
            InspectorSectionTitle("LEFT STICK  •  WALK / RUN")
            LabeledMapperSlider(
                "Walk radius",
                config.joystick.walkRadiusScale,
                .10f.. .95f,
                "${(config.joystick.walkRadiusScale * 100).toInt()}%"
            ) {
                onUpdateJoystick(config.joystick.copy(walkRadiusScale = it))
            }
            LabeledMapperSlider(
                "Run radius",
                config.joystick.runRadiusScale,
                .20f..1.50f,
                "${(config.joystick.runRadiusScale * 100).toInt()}%"
            ) {
                onUpdateJoystick(config.joystick.copy(runRadiusScale = it))
            }
            LabeledMapperSlider(
                "Run threshold",
                config.joystick.runThresholdNorm,
                .05f.. .99f,
                "${(config.joystick.runThresholdNorm * 100).toInt()}%"
            ) {
                onUpdateJoystick(config.joystick.copy(runThresholdNorm = it))
            }
        }

        if (node.type == NodeType.CAMERA_DRAG) {
            InspectorSectionTitle("RIGHT STICK  •  CAMERA")
            MapperToggleRow(
                label = "Invert vertical aim",
                detail = if (config.camera.invertY) "UP = DOWN  •  DOWN = UP" else "UP = UP  •  DOWN = DOWN",
                checked = config.camera.invertY,
                onCheckedChange = { enabled ->
                    onUpdateCamera(config.camera.copy(invertY = enabled))
                }
            )
            LabeledMapperSlider(
                "Horizontal sensitivity",
                config.camera.horizontalSensitivity,
                .10f..3f,
                "%.2f".format(config.camera.horizontalSensitivity)
            ) {
                onUpdateCamera(config.camera.copy(horizontalSensitivity = it))
            }
            LabeledMapperSlider(
                "Vertical sensitivity",
                config.camera.verticalSensitivity,
                .10f..3f,
                "%.2f".format(config.camera.verticalSensitivity)
            ) {
                onUpdateCamera(config.camera.copy(verticalSensitivity = it))
            }
            LabeledMapperSlider(
                "Smoothing",
                config.camera.smoothingFrames.toFloat(),
                1f..12f,
                "${config.camera.smoothingFrames} frames",
                steps = 10
            ) {
                onUpdateCamera(config.camera.copy(smoothingFrames = it.toInt().coerceIn(1, 12)))
            }
            LabeledMapperSlider(
                "Fast turn boost",
                config.camera.fastTurnBoost,
                1f..3f,
                "%.2fx".format(config.camera.fastTurnBoost)
            ) {
                onUpdateCamera(config.camera.copy(fastTurnBoost = it))
            }
        }

        if (ControllerBindingAliases.canonical(node.boundKey) in setOf("LT", "RT")) {
            InspectorSectionTitle("TRIGGER HYSTERESIS")
            LabeledMapperSlider(
                "Press threshold",
                node.triggerPressThreshold,
                .10f..1f,
                "%.2f".format(node.triggerPressThreshold)
            ) { value ->
                val release = node.triggerReleaseThreshold.coerceAtMost(value - .05f)
                onUpdateNode(
                    node.copy(
                        triggerPressThreshold = value,
                        triggerReleaseThreshold = release
                    )
                )
            }
            LabeledMapperSlider(
                "Release threshold",
                node.triggerReleaseThreshold,
                0f.. .90f,
                "%.2f".format(node.triggerReleaseThreshold)
            ) { value ->
                onUpdateNode(
                    node.copy(
                        triggerReleaseThreshold = value.coerceAtMost(node.triggerPressThreshold - .05f)
                    )
                )
            }
        }
    }
}

@Composable
private fun MapperActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    strong: Boolean = false
) {
    val shape = RoundedCornerShape(9.dp)
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .background(
                if (strong) NexusCyan.copy(alpha = .12f) else Color(0xFF0A1927),
                shape
            )
            .border(
                1.dp,
                if (strong) NexusCyan.copy(alpha = .70f) else DarkSurfaceBorder,
                shape
            ),
        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 4.dp)
    ) {
        Text(
            text,
            color = when {
                !enabled -> TextMuted.copy(alpha = .45f)
                strong -> NexusCyan
                else -> TextSecondary
            },
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun SmallSquareButton(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .size(38.dp)
            .background(Color(0xFF0A1927), shape)
            .border(1.dp, NexusCyan.copy(alpha = .46f), shape),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text, color = NexusCyan, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MapperPill(
    text: String,
    active: Boolean,
    accent: Color = NexusCyan
) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .background(
                if (active) accent.copy(alpha = .12f) else Color(0xFF09121D),
                shape
            )
            .border(
                1.dp,
                if (active) accent.copy(alpha = .56f) else DarkSurfaceBorder,
                shape
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text,
            color = if (active) accent else TextMuted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun InspectorSectionTitle(text: String) {
    Spacer(Modifier.height(10.dp))
    Text(
        text,
        color = NexusViolet,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelLarge
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun LabeledMapperSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    displayValue: String,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = TextMuted, style = MaterialTheme.typography.bodySmall)
            Text(displayValue, color = NexusCyan, style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MapperToggleRow(
    label: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                if (checked) NexusViolet.copy(alpha = .10f) else Color(0xFF07111D),
                shape
            )
            .border(
                1.dp,
                if (checked) NexusViolet.copy(alpha = .50f) else DarkSurfaceBorder,
                shape
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(detail, color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NexusHintBar(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF06101A))
            .border(BorderStroke(1.dp, DarkSurfaceBorder))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("NEXUS", color = NexusCyan, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        Text(text, color = TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}
