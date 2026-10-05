package com.example.ui.frames

import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.frames.FrameMonitor
import com.example.ui.MainAppViewModel

@Composable
fun FrameOverlayScreen(viewModel:MainAppViewModel) {
    val context=LocalContext.current
    val settings by FrameMonitor.settings.collectAsState()
    val state by FrameMonitor.state.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Presented FPS / frame interval",style=MaterialTheme.typography.headlineSmall)
        Text("Reads actual SurfaceFlinger timestamps through the active root or Shizuku service. Accessibility alone cannot provide game frame data. These measurements describe presented frames and intervals, not game-engine CPU/GPU render time.")
        Row {
            Text("In-game frame overlay",Modifier.weight(1f))
            Switch(checked=settings.enabled,onCheckedChange={ enabled ->
                if(enabled && !Settings.canDrawOverlays(context)) viewModel.showSnack("Grant overlay permission in System before enabling frame measurements.")
                else runCatching { FrameMonitor.setEnabled(context,enabled) }.onFailure { viewModel.showSnack("Frame preference failed: ${it.message}") }
            })
        }
        Text(FrameMonitor.label(state))
        state.layer?.let { Text("Selected observed layer: $it") }
        Text("Observed game layers from the last game focus. With several layers, choose the game's rendering surface; verify it on the device.")
        state.layers.forEach { layer ->
            OutlinedButton(onClick={runCatching { FrameMonitor.chooseLayer(context,layer) }.onFailure { viewModel.showSnack("Frame layer selection failed: ${it.message}") }}) { Text(layer) }
        }
        Button(onClick=FrameMonitor::refresh) { Text("Refresh on next game focus") }
    }
}
