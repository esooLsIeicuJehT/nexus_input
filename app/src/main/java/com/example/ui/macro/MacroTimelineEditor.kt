package com.example.ui.macro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MacroEventType
import com.example.model.MacroSequence
import com.example.model.MacroTimelineEvent
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroTimelineEditor(
    onBack: () -> Unit,
    onSaveMacro: (MacroSequence) -> Unit = {}
) {
    var macroName by remember { mutableStateOf("Fast Drop-Shot & Slide") }
    var isPlaying by remember { mutableStateOf(false) }
    var isLooping by remember { mutableStateOf(false) }
    var currentPlaybackMs by remember { mutableLongStateOf(150L) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    val totalDurationMs by remember { mutableLongStateOf(1000L) }
    var showSafetyWarning by remember { mutableStateOf(true) }

    // Sample timeline events
    var events by remember {
        mutableStateOf(
            listOf(
                MacroTimelineEvent("ev_1", 0L, MacroEventType.BUTTON_DOWN, targetKey = "B", durationMs = 120L, colorHex = 0xFFFFD600),
                MacroTimelineEvent("ev_2", 80L, MacroEventType.JOYSTICK_MOVE, targetKey = "LS", xNorm = 0.5f, yNorm = 0.9f, durationMs = 200L, colorHex = 0xFF00E5FF),
                MacroTimelineEvent("ev_3", 220L, MacroEventType.BUTTON_DOWN, targetKey = "LT", durationMs = 400L, colorHex = 0xFF00E676),
                MacroTimelineEvent("ev_4", 300L, MacroEventType.TAP, targetKey = "RT", durationMs = 60L, colorHex = 0xFFFF1744),
                MacroTimelineEvent("ev_5", 420L, MacroEventType.TAP, targetKey = "RT", durationMs = 60L, colorHex = 0xFFFF1744),
                MacroTimelineEvent("ev_6", 650L, MacroEventType.BUTTON_UP, targetKey = "LT", durationMs = 50L, colorHex = 0xFF9CA3AF)
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Macro & Combo Timeline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(macroName, style = MaterialTheme.typography.bodySmall, color = CyberCyan)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("macro_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CyberCyan)
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            onSaveMacro(
                                MacroSequence(
                                    id = "macro_${System.currentTimeMillis()}",
                                    name = macroName,
                                    totalDurationMs = totalDurationMs,
                                    isLooping = isLooping,
                                    playbackSpeed = playbackSpeed,
                                    events = events,
                                    antiCheatWarningDismissed = true
                                )
                            )
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D)),
                        modifier = Modifier.testTag("macro_save_button")
                    ) {
                        Text("Save Macro", fontWeight = FontWeight.Bold)
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
            // Anti-Cheat Compliance Warning Banner
            if (showSafetyWarning) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1C0A)),
                    border = BorderStroke(1.dp, Color(0xFFFFD600))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFD600), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Anti-Cheat Policy Notice", fontWeight = FontWeight.Bold, color = Color(0xFFFFD600), fontSize = 12.sp)
                            Text("Automated rapid combos or repetitive timed sequences may trigger heuristic server-side detection in online competitive titles. Use responsibly.", fontSize = 10.sp, color = DarkTextSecondary)
                        }
                        IconButton(onClick = { showSafetyWarning = false }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = DarkTextSecondary)
                        }
                    }
                }
            }

            // Timeline Scrub Bar Canvas (0ms, 100ms, 200ms... 1000ms)
            Card(
                modifier = Modifier.fillMaxWidth().height(130.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, DarkSurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Timeline Scrubber", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = CyberCyan)
                        Text("${currentPlaybackMs}ms / ${totalDurationMs}ms", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = AccentGreen)
                    }

                    // Scrubber visual canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .padding(top = 8.dp)
                            .background(Color(0xFF070B12), RoundedCornerShape(6.dp))
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    val ratio = (change.position.x / size.width).coerceIn(0f, 1f)
                                    currentPlaybackMs = (ratio * totalDurationMs).toLong()
                                }
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            // Draw timeline ticks (every 100ms)
                            for (ms in 0..totalDurationMs step 100) {
                                val x = (ms.toFloat() / totalDurationMs) * w
                                val tickHeight = if (ms % 500L == 0L) h * 0.4f else h * 0.2f
                                drawLine(
                                    color = Color(0xFF1F2937),
                                    start = Offset(x, 0f),
                                    end = Offset(x, tickHeight),
                                    strokeWidth = 1.5f
                                )
                            }

                            // Draw event blocks on timeline
                            events.forEach { ev ->
                                val startX = (ev.timestampMs.toFloat() / totalDurationMs) * w
                                val blockW = (ev.durationMs.toFloat() / totalDurationMs * w).coerceAtLeast(12f)
                                drawRoundRect(
                                    color = Color(ev.colorHex),
                                    topLeft = Offset(startX, h * 0.45f),
                                    size = androidx.compose.ui.geometry.Size(blockW, h * 0.35f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                                )
                            }

                            // Draw playhead cursor
                            val cursorX = (currentPlaybackMs.toFloat() / totalDurationMs) * w
                            drawLine(
                                color = Color.White,
                                start = Offset(cursorX, 0f),
                                end = Offset(cursorX, h),
                                strokeWidth = 2.5f
                            )
                            drawCircle(
                                color = CyberCyan,
                                radius = 6f,
                                center = Offset(cursorX, 0f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Playback Controls (Play, Pause, Step, Loop, Speed)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = { isPlaying = !isPlaying },
                        modifier = Modifier.size(36.dp).background(if (isPlaying) AccentGreen else CyberCyan, CircleShape)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color(0xFF00363D),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Step Forward (+50ms)
                    IconButton(
                        onClick = { currentPlaybackMs = (currentPlaybackMs + 50L).coerceAtMost(totalDurationMs) },
                        modifier = Modifier.size(36.dp).background(DarkSurface, CircleShape)
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Step", tint = DarkTextPrimary, modifier = Modifier.size(18.dp))
                    }

                    // Loop Toggle
                    IconButton(
                        onClick = { isLooping = !isLooping },
                        modifier = Modifier.size(36.dp).background(if (isLooping) Color(0x3300E5FF) else DarkSurface, CircleShape)
                    ) {
                        Icon(Icons.Default.Repeat, contentDescription = "Loop", tint = if (isLooping) CyberCyan else DarkTextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                // Speed selector: 0.5x, 1.0x, 2.0x
                Row(
                    modifier = Modifier
                        .background(DarkSurface, RoundedCornerShape(20.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(0.5f, 1.0f, 2.0f).forEach { speed ->
                        val isSelected = playbackSpeed == speed
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) CyberCyan else Color.Transparent)
                                .clickable { playbackSpeed = speed }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "${speed}x",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color(0xFF00363D) else DarkTextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Event Track List
            Text("Events Sequence (${events.size} actions)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DarkTextPrimary)

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(events) { ev ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, if (currentPlaybackMs in ev.timestampMs..(ev.timestampMs + ev.durationMs)) CyberCyan else DarkSurfaceBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(Color(ev.colorHex), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("${ev.type.name} -> [${ev.targetKey}]", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DarkTextPrimary)
                                    Text("At ${ev.timestampMs}ms (hold ${ev.durationMs}ms)", fontSize = 10.sp, color = DarkTextSecondary)
                                }
                            }
                            IconButton(
                                onClick = { events = events.filter { it.id != ev.id } },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Quick Add Action
            Button(
                onClick = {
                    val newEv = MacroTimelineEvent(
                        id = "ev_${System.currentTimeMillis()}",
                        timestampMs = currentPlaybackMs,
                        type = MacroEventType.TAP,
                        targetKey = "A",
                        durationMs = 80L,
                        colorHex = 0xFF00E5FF
                    )
                    events = (events + newEv).sortedBy { it.timestampMs }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurface, contentColor = CyberCyan),
                border = BorderStroke(1.dp, CyberCyan)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Action at ${currentPlaybackMs}ms", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
