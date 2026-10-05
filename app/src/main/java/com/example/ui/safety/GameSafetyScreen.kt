package com.example.ui.safety

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun GameSafetyScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Game compatibility",style=MaterialTheme.typography.headlineSmall)
        Text("No device-verified game compatibility reports are bundled with this build.")
        Text("KernelSU and Shizuku injection, overlays and individual games require the documented device checks. APatch remains UNVERIFIED and disabled.")
        Text("NEXUS INPUT does not certify a game's publisher policy or anti-cheat compatibility.")
        TextButton(onClick=onBack) { Text("Back") }
    }
}
