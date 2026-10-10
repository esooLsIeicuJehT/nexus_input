package com.example.ui.nexus

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.input.ControllerInputMonitor
import com.example.ui.MainAppViewModel
import com.example.ui.mapper.ScreenshotMapperScreen
import com.example.ui.theme.*
import kotlin.math.abs

@Composable
fun NexusMapperRoute(viewModel: MainAppViewModel) {
    // The screenshot workspace owns its one compact toolbar. Keep all primary
    // navigation in the shell's bottom dock in both orientations.
    ScreenshotMapperScreen(viewModel = viewModel)
}

@Composable
fun NexusDevicesRoute(
    viewModel: MainAppViewModel,
    onNavigate: (String) -> Unit
) {
    val live by ControllerInputMonitor.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
        Box(modifier = Modifier.weight(1f)) {
            NexusDevicesScreen(viewModel = viewModel, onNavigate = onNavigate)
        }
        LiveInputStrip(
            sourceName = live.connectedEventSource,
            axes = live.axes,
            pressedButtons = live.pressedButtons,
            lastKey = listOfNotNull(live.lastKeyName, live.lastKeyCode?.let { "code=$it" }, live.lastScanCode?.let { "scan=$it" }, live.lastSource?.let { "src=0x${it.toString(16)}" }).joinToString(" · "),
            eventLog = live.eventLog
        )
    }
}

@Composable
private fun LiveInputStrip(
    sourceName: String?,
    axes: Map<String, Float>,
    pressedButtons: Set<String>,
    lastKey: String,
    eventLog: List<String>
) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(max = 190.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, DarkSurfaceBorder),
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "LIVE ANDROID INPUT",
                    color = NexusCyan,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    sourceName ?: "Waiting for controller events",
                    color = if (sourceName == null) TextMuted else AccentGreen,
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(7.dp))
            if (axes.isEmpty() && pressedButtons.isEmpty()) {
                Text(
                    "Move a stick, pull a trigger, press the D-pad, or tap a controller button. Only events Android actually delivers are shown here.",
                    color = TextSecondary,
                    fontSize = 9.sp
                )
            } else {
                listOf("LX", "LY", "RX", "RY", "LT", "RT", "BRAKE", "GAS", "HAT_X", "HAT_Y")
                    .mapNotNull { name -> axes[name]?.let { name to it } }
                    .forEach { (name, value) -> LiveAxisRow(name, value) }
                var expandedAxes by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                TextButton(onClick = { expandedAxes = !expandedAxes }) {
                    Text(if (expandedAxes) "Hide raw axes" else "All raw axes & events")
                }
                if (expandedAxes) axes.filterKeys { it.startsWith("AXIS_") }.toSortedMap()
                    .forEach { (name, value) -> LiveAxisRow(name, value) }

                if (pressedButtons.isNotEmpty() || lastKey.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = buildString { if(pressedButtons.isNotEmpty()) append("Pressed: ${pressedButtons.sorted().joinToString(" • ")}"); if(lastKey.isNotBlank()) { if(isNotEmpty()) append("\n"); append("Last key: $lastKey") } },
                        color = NexusVioletLight,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if(eventLog.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("RAW EVENT LOG",color=NexusCyan,fontSize=8.sp,fontWeight=FontWeight.ExtraBold)
                    Text(eventLog.takeLast(8).joinToString("\n"),color=TextMuted,fontSize=7.sp,maxLines=8,overflow=TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun LiveAxisRow(name: String, value: Float) {
    val normalized = if (name == "LT" || name == "RT") {
        value.coerceIn(0f, 1f)
    } else {
        abs(value).coerceIn(0f, 1f)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, color = NexusCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(42.dp))
        LinearProgressIndicator(
            progress = { normalized },
            modifier = Modifier.weight(1f).height(5.dp),
            color = if (value < 0f) NexusVioletLight else NexusCyan,
            trackColor = Color(0xFF12263A)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            String.format("%+.2f", value),
            color = TextSecondary,
            fontSize = 8.sp,
            modifier = Modifier.width(38.dp)
        )
    }
}

@Composable
fun NexusProfileDetailRoute(
    viewModel: MainAppViewModel,
    onNavigate: (String) -> Unit
) {
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (!landscape) {
        NexusProfileDetailScreen(viewModel = viewModel, onNavigate = onNavigate)
        return
    }

    val context = LocalContext.current
    val game by viewModel.selectedGame.collectAsState()
    val config by viewModel.activeConfig.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.weight(1.55f).fillMaxHeight()) {
            NexusProfileDetailScreen(viewModel = viewModel, onNavigate = onNavigate)
        }

        Surface(
            modifier = Modifier
                .widthIn(min = 230.dp, max = 310.dp)
                .fillMaxHeight(),
            shape = RoundedCornerShape(18.dp),
            color = DarkSurface,
            border = BorderStroke(1.dp, NexusViolet.copy(alpha = 0.65f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("LANDSCAPE PROFILE", color = NexusCyan, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    game?.displayName ?: config.gameTitle.ifBlank { "No game selected" },
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(config.profileName, color = TextSecondary, fontSize = 10.sp)

                HorizontalDivider(color = DarkSurfaceBorder)
                LandscapeMetric("Bindings", config.buttons.size.toString())
                LandscapeMetric("Controller", config.controllerType.displayName)
                LandscapeMetric("Aspect", config.targetAspectRatio)

                Spacer(Modifier.weight(1f))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = game != null,
                    onClick = { game?.let { viewModel.launchGameWithMapping(it, context) } },
                    colors = ButtonDefaults.buttonColors(containerColor = NexusViolet)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Launch")
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onNavigate("mapper") }
                ) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Open mapper")
                }
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onNavigate("profiles") }
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Profiles")
                }
            }
        }
    }
}

@Composable
private fun LandscapeMetric(label: String, value: String) {
    Column {
        Text(label.uppercase(), color = TextMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        Text(
            value,
            color = TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
