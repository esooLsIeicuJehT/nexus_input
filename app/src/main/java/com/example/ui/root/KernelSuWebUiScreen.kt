package com.example.ui.root

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.module.KernelSuModuleManager
import com.example.ui.MainAppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun KernelSuWebUiScreen(viewModel: MainAppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val status by KernelSuModuleManager.moduleStatus.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("KernelSU companion", style = MaterialTheme.typography.headlineSmall)
        Text("The module's WebUI runs in KernelSU. Input injection remains in the app's libsu RootService and JNI /dev/uinput transport.")
        Text(status.lastActionLog)
        Button(enabled = !busy, onClick = { scope.launch { withContext(Dispatchers.IO) { KernelSuModuleManager.checkInstallationStatus() } } }) { Text("Check installation") }
        Button(enabled = !busy, onClick = {
            val intent = listOf("me.weishu.kernelsu", "com.rifsxd.ksunext").firstNotNullOfOrNull {
                context.packageManager.getLaunchIntentForPackage(it)
            }
            if (intent == null) viewModel.showSnack("KernelSU manager is not installed or not visible to Android.")
            else runCatching { context.startActivity(intent) }.onFailure { viewModel.showSnack("Cannot open KernelSU: ${it.message}") }
        }) { Text("Open KernelSU manager → companion WebUI") }
        Button(enabled = !busy, onClick = { scope.launch {
            busy = true
            try {
                val installed = KernelSuModuleManager.directInstallViaRoot()
                viewModel.showSnack(KernelSuModuleManager.moduleStatus.value.lastActionLog)
                if (installed) viewModel.showSnack("Installation staged. Reboot to activate the companion.")
            } finally { busy = false }
        } }) { Text("Stage companion installation") }
        OutlinedButton(enabled = !busy, onClick = { scope.launch {
            busy = true
            try {
                val zip = KernelSuModuleManager.generateModuleZip(context)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zip)
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Export NEXUS INPUT companion"))
            } catch (error: Exception) { viewModel.showSnack("Companion export failed: ${error.message}") }
            finally { busy = false }
        } }) { Text("Export companion ZIP") }
        TextButton(onClick = onBack) { Text("Back") }
    }
}
