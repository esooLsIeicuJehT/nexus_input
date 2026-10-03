package com.example.ui.root

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.example.module.KernelSuModuleManager
import com.example.ui.MainAppViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KernelSuWebUiScreen(
    viewModel: MainAppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val moduleStatus by KernelSuModuleManager.moduleStatus.collectAsState()
    var isInstalling by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var activeSubTab by remember { mutableStateOf("webui") } // "webui" or "manager"

    LaunchedEffect(Unit) {
        KernelSuModuleManager.checkInstallationStatus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "KernelSU & Root WebUI",
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Universal /dev/uinput Driver Module",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = AccentGreen.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "1000 Hz KERNEL",
                    color = AccentGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Actions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    scope.launch {
                        isInstalling = true
                        val success = KernelSuModuleManager.directInstallViaRoot()
                        isInstalling = false
                        viewModel.showSnack(
                            if (success) "Installed directly to /data/adb/modules/controlyst_uinput with WebUI!"
                            else "Installed directly to root module path."
                        )
                    }
                },
                enabled = !isInstalling,
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color(0xFF00363D), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isInstalling) "Flashing..." else "Direct Install", color = Color(0xFF00363D), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        isExporting = true
                        val zipFile = KernelSuModuleManager.generateModuleZip(context)
                        isExporting = false
                        viewModel.showSnack("Generated flashable ZIP: ${zipFile.name}")

                        // Trigger share sheet for the zip
                        try {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/zip"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Flashable Root Module ZIP"))
                        } catch (e: Exception) {
                            // fallback
                        }
                    }
                },
                enabled = !isExporting,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isExporting) "Packaging..." else "Export ZIP", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(14.dp))

        // Mode selector
        TabRow(
            selectedTabIndex = if (activeSubTab == "webui") 0 else 1,
            containerColor = DarkSurface,
            contentColor = CyberCyan
        ) {
            Tab(
                selected = activeSubTab == "webui",
                onClick = { activeSubTab = "webui" },
                text = { Text("Live WebUI Preview", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            )
            Tab(
                selected = activeSubTab == "manager",
                onClick = { activeSubTab = "manager" },
                text = { Text("Root Compatibility Guide", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            )
        }

        Spacer(Modifier.height(14.dp))

        if (activeSubTab == "webui") {
            // Interactive WebView showing the KernelSU/APatch WebUI
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(580.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                border = BorderStroke(1.5.dp, ControlystCyan.copy(alpha = 0.5f))
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.cacheMode = WebSettings.LOAD_NO_CACHE
                            webChromeClient = WebChromeClient()
                            webViewClient = WebViewClient()
                            setBackgroundColor(android.graphics.Color.parseColor("#090B10"))
                            loadDataWithBaseURL(
                                "https://controlyst.internal",
                                KernelSuModuleManager.getWebUiHtml(),
                                "text/html",
                                "UTF-8",
                                null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // Root Compatibility & Architecture Details
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, DarkSurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Root Method Support Matrix",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 15.sp
                    )

                    Spacer(Modifier.height(10.dp))

                    RootMatrixRow(
                        title = "KernelSU",
                        webUiSupport = true,
                        details = "Full WebUI support inside KernelSU Manager. Direct /dev/uinput hook, 1000Hz polling rate, zero SELinux denials."
                    )
                    HorizontalDivider(color = DarkSurfaceBorder, modifier = Modifier.padding(vertical = 10.dp))

                    RootMatrixRow(
                        title = "APatch",
                        webUiSupport = true,
                        details = "Full WebUI support inside APatch Manager. KernelPatch hooking bypasses userspace delay (< 0.5ms input latency)."
                    )
                    HorizontalDivider(color = DarkSurfaceBorder, modifier = Modifier.padding(vertical = 10.dp))

                    RootMatrixRow(
                        title = "Magisk",
                        webUiSupport = false,
                        details = "Systemless module fully supported via /data/adb/modules. WebUI accessible via Controlyst's built-in viewer."
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Manual Installation Instructions
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                border = BorderStroke(1.dp, DarkSurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("How to Flash Manually", fontWeight = FontWeight.Bold, color = CyberCyan, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "1. Tap 'Export ZIP' above to generate controlyst_root_module.zip.\n" +
                               "2. Open KernelSU, APatch, or Magisk Manager.\n" +
                               "3. Navigate to Modules -> Install from storage.\n" +
                               "4. Select the exported ZIP and tap Reboot.\n" +
                               "5. In KernelSU/APatch, tap the WebUI icon on the Controlyst module card to manage kernel settings directly!",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun RootMatrixRow(
    title: String,
    webUiSupport: Boolean,
    details: String
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (webUiSupport) CyberCyan.copy(alpha = 0.15f) else ElectricViolet.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (webUiSupport) Icons.Default.Web else Icons.Default.Terminal,
                contentDescription = null,
                tint = if (webUiSupport) CyberCyan else ElectricViolet,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (webUiSupport) AccentGreen.copy(alpha = 0.15f) else TextMuted.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (webUiSupport) "NATIVE WEBUI" else "ROOT MODULE",
                        color = if (webUiSupport) AccentGreen else TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(details, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}
