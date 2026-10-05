package com.example.ui.root

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.module.KernelSuModuleManager
import com.example.ui.MainAppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun KernelSuWebUiScreen(viewModel: MainAppViewModel, onBack: () -> Unit) {
    val context=LocalContext.current
    val status by KernelSuModuleManager.moduleStatus.collectAsState()
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { KernelSuModuleManager.checkInstallationStatus() } }
    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("KernelSU WebUI",style=MaterialTheme.typography.headlineSmall)
        Card {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(status.lastActionLog)
                Text("Opens KernelSU manager. Select NEXUS INPUT Root Companion, then WebUI. Root controls, safeguards and module updates are managed there.")
                Button(onClick={
                    val intent=listOf("me.weishu.kernelsu","com.rifsxd.ksunext").firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
                    if(intent==null) viewModel.showSnack("KernelSU manager is not installed or not visible to Android.")
                    else runCatching { context.startActivity(intent) }.onFailure { viewModel.showSnack("Cannot open KernelSU: ${it.message}") }
                }) { Text("Open KernelSU WebUI") }
            }
        }
        TextButton(onClick=onBack) { Text("Back") }
    }
}
