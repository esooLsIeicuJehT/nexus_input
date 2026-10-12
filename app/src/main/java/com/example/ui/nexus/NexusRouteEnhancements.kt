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
    NexusDevicesScreen(viewModel, onNavigate)
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
                .fillMaxHeight().nexusGlass(18.dp, MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(18.dp),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("LANDSCAPE PROFILE", color = MaterialTheme.colorScheme.primary, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
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

                LandscapeMetric("Requested backend", config.preferredBackend?.title ?: "Automatic")
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = game != null,
                    onClick = { game?.let { viewModel.launchGameWithMapping(it, context) } },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
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
