package com.example.module

import android.content.Context
import com.example.injector.NexusRuntimeContext
import com.example.injector.ProcessShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class KernelModuleStatus(
    val isInstalledInKernelSu: Boolean = false,
    val isInstalledInApatch: Boolean = false,
    val isInstalledInMagisk: Boolean = false,
    val activeUinputDevice: String = "/dev/uinput",
    val pollingRateHz: Int = 0,
    val isAntiRecoilActive: Boolean = false,
    val isZeroLatencyDirectInjection: Boolean = false,
    val lastActionLog: String = "Installation has not been checked"
)

/** Uses the canonical kernelsu-module source bundled as assets; no second installer or fake daemon. */
object KernelSuModuleManager {
    const val MODULE_ID = "gamepad.pro.root"
    private val _moduleStatus = MutableStateFlow(KernelModuleStatus())
    val moduleStatus = _moduleStatus.asStateFlow()
    private val files = listOf("module.prop", "service.sh", "customize.sh", "action.sh", "skip_mount",
        "update.sh", "update-lib.sh", "webroot/index.html", "webroot/app.js", "webroot/style.css")
    private fun asset(path: String) = NexusRuntimeContext.require().assets.open(path).bufferedReader().use { it.readText() }
    fun getModuleProp() = asset("module.prop")
    fun getServiceSh() = asset("service.sh")
    fun getWebUiHtml() = asset("webroot/index.html")
    fun checkInstallationStatus() {
        val result = ProcessShellExecutor().run(listOf("su", "-c", "test -d /data/adb/modules/$MODULE_ID"))
        _moduleStatus.value = _moduleStatus.value.copy(isInstalledInKernelSu = result.succeeded,
            lastActionLog = if (result.succeeded) "Companion directory exists; inspect its WebUI in KernelSU"
            else "Companion installation check failed: ${result.stderr.ifBlank { "exit=${result.exitCode}" }}")
    }
    suspend fun generateModuleZip(context: Context): File = withContext(Dispatchers.IO) {
        val version = getModuleProp().lineSequence().first { it.startsWith("version=") }.substringAfter('=')
        val out = File(context.cacheDir, "modules/NEXUS_INPUT-KernelSU-Companion-v$version.zip")
        check(out.parentFile!!.exists() || out.parentFile!!.mkdirs()) { "Cannot create module export directory" }
        ZipOutputStream(out.outputStream()).use { zip ->
            files.forEach { path ->
                zip.putNextEntry(ZipEntry(path))
                context.assets.open(path).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        _moduleStatus.value = _moduleStatus.value.copy(lastActionLog = "Exported canonical companion ZIP: ${out.name}")
        out
    }
    suspend fun directInstallViaRoot(): Boolean = withContext(Dispatchers.IO) {
        try {
            val zip = generateModuleZip(NexusRuntimeContext.require())
            val quoted = "'" + zip.absolutePath.replace("'", "'\\''") + "'"
            val result = ProcessShellExecutor().run(listOf("su", "-c", "ksud module install $quoted"))
            _moduleStatus.value = _moduleStatus.value.copy(lastActionLog = if (result.succeeded)
                "KernelSU staged companion installation. Reboot is required."
                else "KernelSU install failed: ${result.stderr.ifBlank { result.stdout }.ifBlank { "exit=${result.exitCode}" }}")
            result.succeeded
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            _moduleStatus.value = _moduleStatus.value.copy(lastActionLog = "KernelSU install failed: ${error.message}")
            android.util.Log.e("NexusModule", "Install failed", error)
            false
        }
    }
}
