package com.example.module

import com.example.injector.ProcessShellExecutor
import com.example.injector.CommandResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class KernelModuleStatus(
    val installed: Boolean? = null,
    val lastActionLog: String = "Companion installation has not been checked"
) {
    companion object {
        fun fromProbe(result: CommandResult): KernelModuleStatus = when {
            result.succeeded -> KernelModuleStatus(true,"Module directory observed. Open its WebUI in KernelSU for root controls.")
            !result.timedOut && !result.outputTruncated && result.streamError == null && result.exitCode == 4 ->
                KernelModuleStatus(false,"NEXUS INPUT companion directory is absent")
            else -> KernelModuleStatus(null,"Module status unavailable: ${result.stderr.ifBlank { result.stdout }.ifBlank { "exit=${result.exitCode}, timeout=${result.timedOut}" }}")
        }
    }
}

/** Read-only companion readiness. All module management and tuning live in the module WebUI. */
object KernelSuModuleManager {
    const val MODULE_ID = "gamepad.pro.root"
    private val _moduleStatus = MutableStateFlow(KernelModuleStatus())
    val moduleStatus = _moduleStatus.asStateFlow()
    fun checkInstallationStatus() {
        val result = ProcessShellExecutor().run(listOf("su","-c",
            "if [ -d /data/adb/modules/$MODULE_ID ]; then exit 0; else exit 4; fi"))
        _moduleStatus.value=KernelModuleStatus.fromProbe(result)
        android.util.Log.i("NexusModule",_moduleStatus.value.lastActionLog)
    }
}
