package com.example.injector

import android.content.Context
import android.provider.Settings
import com.example.model.PrivilegeMethod
import com.example.service.ControlystAccessibilityService
import java.io.File

data class PrivilegeProbeResult(
    val method: PrivilegeMethod,
    val isDetected: Boolean,
    val statusDetail: String,
    val latencyScoreMs: Int
)

class PrivilegeDetector(private val context: Context? = null) {

    fun probeAll(): List<PrivilegeProbeResult> {
        val results = mutableListOf<PrivilegeProbeResult>()

        // 1. Shizuku
        val shizukuDetected = probeShizuku()
        results.add(
            PrivilegeProbeResult(
                method = PrivilegeMethod.SHIZUKU,
                isDetected = shizukuDetected,
                statusDetail = if (shizukuDetected) "Running (ADB Binder Active)" else "Not running or ADB not paired",
                latencyScoreMs = 3
            )
        )

        // 2. Magisk
        val magiskDetected = probeMagisk()
        results.add(
            PrivilegeProbeResult(
                method = PrivilegeMethod.MAGISK,
                isDetected = magiskDetected,
                statusDetail = if (magiskDetected) "su root binary detected" else "su not found in standard paths",
                latencyScoreMs = 1
            )
        )

        // 3. KernelSU
        val ksuDetected = probeKernelSU()
        results.add(
            PrivilegeProbeResult(
                method = PrivilegeMethod.KERNELSU,
                isDetected = ksuDetected,
                statusDetail = if (ksuDetected) "KernelSU userspace daemon active" else "KernelSU daemon socket inactive",
                latencyScoreMs = 1
            )
        )

        // 4. APatch
        val apatchDetected = probeAPatch()
        results.add(
            PrivilegeProbeResult(
                method = PrivilegeMethod.APATCH,
                isDetected = apatchDetected,
                statusDetail = if (apatchDetected) "APatch kernel module present" else "APatch path not found",
                latencyScoreMs = 1
            )
        )

        // 5. Accessibility Fallback
        val a11yActive = probeAccessibility()
        results.add(
            PrivilegeProbeResult(
                method = PrivilegeMethod.ACCESSIBILITY,
                isDetected = a11yActive,
                statusDetail = if (a11yActive) "Active & dispatchGesture enabled" else "Service turned off in Accessibility Settings",
                latencyScoreMs = 16
            )
        )

        return results
    }

    fun detectBestMethod(): PrivilegeMethod {
        val all = probeAll()
        // Priority order: Shizuku -> Magisk -> KernelSU -> APatch -> Accessibility
        return all.firstOrNull { it.isDetected }?.method ?: PrivilegeMethod.ACCESSIBILITY
    }

    private fun probeShizuku(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("sh -c 'which rish || which shizuku'")
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun probeMagisk(): Boolean {
        val paths = listOf(
            "/system/xbin/su",
            "/system/bin/su",
            "/sbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su"
        )
        return paths.any { File(it).exists() }
    }

    private fun probeKernelSU(): Boolean {
        val paths = listOf("/data/adb/ksud", "/data/adb/ksu", "/dev/ksu_daemon")
        return paths.any { File(it).exists() }
    }

    private fun probeAPatch(): Boolean {
        val paths = listOf("/data/adb/apd", "/data/adb/ap")
        return paths.any { File(it).exists() }
    }

    private fun probeAccessibility(): Boolean {
        if (ControlystAccessibilityService.isServiceRunning()) return true
        if (context == null) return false
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            enabledServices.contains(context.packageName)
        } catch (e: Exception) {
            false
        }
    }
}
