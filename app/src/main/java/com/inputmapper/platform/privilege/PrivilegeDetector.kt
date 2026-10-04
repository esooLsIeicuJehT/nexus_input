package com.inputmapper.platform.privilege

import android.content.Context
import com.inputmapper.platform.accessibility.MapperAccessibilityService
import com.inputmapper.platform.core.AvailabilityState
import com.inputmapper.platform.core.BackendAvailability
import com.inputmapper.platform.core.BackendKind
import com.inputmapper.platform.shizuku.ShizukuAccess

class PrivilegeDetector(
    private val context: Context,
    private val shell: ShellExecutor = ProcessShellExecutor()
) {
    fun detectAll(): List<BackendAvailability> {
        val rootVersion = shell.run(listOf("su", "-v"))
        val rootVersionCode = shell.run(listOf("su", "-V"))
        val rootId = shell.run(listOf("su", "-c", "id"))
        val rootUsable = rootId.succeeded && rootId.stdout.contains("uid=0")
        val versionText = rootVersion.stdout.trim()
        val versionCode = rootVersionCode.stdout.trim().toLongOrNull()

        return listOf(
            detectShizuku(),
            detectMagisk(rootUsable, versionText, versionCode),
            detectKernelSu(rootUsable, versionText, versionCode),
            detectAPatch(rootUsable, versionText, versionCode),
            detectAccessibility()
        )
    }

    private fun detectShizuku(): BackendAvailability {
        return try {
            val state = ShizukuAccess.snapshot(context)
            if (!state.binderAlive) {
                return BackendAvailability(
                    BackendKind.SHIZUKU,
                    if (state.managerInstalled) AvailabilityState.INSTALLED_NOT_RUNNING else AvailabilityState.NOT_FOUND,
                    versionName = state.managerVersion,
                    details = if (state.managerInstalled) {
                        "Shizuku manager is installed but the Shizuku/Sui binder is not reachable yet"
                    } else {
                        "No Shizuku/Sui binder is reachable and the Shizuku manager package is not installed"
                    }
                )
            }

            val details = buildString {
                append("binder=alive")
                append(" serverApi=").append(state.serverApi ?: -1)
                append(" uid=").append(state.serverUid ?: -1)
                append(" selinux=").append(state.selinuxContext ?: "unknown")
                append("; app permission=")
                append(
                    when {
                        state.permissionGranted -> "granted"
                        state.permissionBlocked -> "blocked; re-enable NEXUS INPUT in Shizuku Authorized applications"
                        else -> "requestable"
                    }
                )
            }
            BackendAvailability(
                BackendKind.SHIZUKU,
                if (state.permissionGranted) AvailabilityState.AVAILABLE else AvailabilityState.PERMISSION_REQUIRED,
                versionName = state.managerVersion ?: "Shizuku/Sui",
                details = details
            )
        } catch (t: Throwable) {
            BackendAvailability(
                BackendKind.SHIZUKU,
                AvailabilityState.ERROR,
                details = "Shizuku/Sui probe failed: ${t.javaClass.simpleName}: ${t.message}"
            )
        }
    }

    private fun detectMagisk(rootUsable: Boolean, version: String, code: Long?): BackendAvailability {
        if (!rootUsable) return BackendAvailability(BackendKind.MAGISK, AvailabilityState.NOT_FOUND, details = "No usable uid=0 su session")
        val magiskCli = shell.run(listOf("su", "-c", "magisk -V"))
        val isMagisk = version.contains("magisk", ignoreCase = true) || magiskCli.succeeded
        return if (isMagisk) {
            BackendAvailability(
                BackendKind.MAGISK,
                AvailabilityState.AVAILABLE,
                versionName = version.ifBlank { "Magisk" },
                versionCode = magiskCli.stdout.trim().toLongOrNull() ?: code,
                details = "uid=0 confirmed; MagiskSU -c contract available"
            )
        } else {
            BackendAvailability(BackendKind.MAGISK, AvailabilityState.NOT_FOUND, details = "su is present but did not identify as Magisk")
        }
    }

    private fun detectKernelSu(rootUsable: Boolean, version: String, code: Long?): BackendAvailability {
        if (!rootUsable) return BackendAvailability(BackendKind.KERNEL_SU, AvailabilityState.NOT_FOUND, details = "No usable uid=0 su session")
        val isKernelSu = version.contains("KernelSU", ignoreCase = true)
        if (!isKernelSu) return BackendAvailability(BackendKind.KERNEL_SU, AvailabilityState.NOT_FOUND, details = "su is present but did not identify as KernelSU")
        val uinput = shell.run(listOf("su", "-c", "test -r /dev/uinput -a -w /dev/uinput"))
        return BackendAvailability(
            BackendKind.KERNEL_SU,
            if (uinput.succeeded) AvailabilityState.AVAILABLE else AvailabilityState.DENIED,
            versionName = version,
            versionCode = code,
            details = if (uinput.succeeded) {
                "uid=0 confirmed and /dev/uinput is readable+writable"
            } else {
                "KernelSU root works, but /dev/uinput open prerequisites failed: ${uinput.stderr.ifBlank { "exit=${uinput.exitCode}" }}"
            }
        )
    }

    private fun detectAPatch(rootUsable: Boolean, version: String, code: Long?): BackendAvailability {
        if (!rootUsable) return BackendAvailability(BackendKind.APATCH, AvailabilityState.NOT_FOUND, details = "No usable uid=0 su session")
        val apd = shell.run(listOf("su", "-c", "command -v apd"))
        val isAPatch = version.contains("APatch", ignoreCase = true) || apd.succeeded
        return if (isAPatch) {
            BackendAvailability(
                BackendKind.APATCH,
                AvailabilityState.AVAILABLE,
                versionName = version.ifBlank { "APatch/apd detected" },
                versionCode = code,
                details = "uid=0 confirmed and APatch apd/su environment detected. Hardware path is not verified in Phase 0 test devices."
            )
        } else {
            BackendAvailability(BackendKind.APATCH, AvailabilityState.NOT_FOUND, details = "APatch apd was not found")
        }
    }

    private fun detectAccessibility(): BackendAvailability {
        return BackendAvailability(
            BackendKind.ACCESSIBILITY,
            if (MapperAccessibilityService.current != null) AvailabilityState.AVAILABLE else AvailabilityState.PERMISSION_REQUIRED,
            details = if (MapperAccessibilityService.current != null) "Accessibility service connected" else "Accessibility service is not enabled"
        )
    }
}
