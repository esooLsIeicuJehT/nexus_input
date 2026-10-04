package com.example.injector

import android.content.Context
import android.provider.Settings
import com.example.model.PrivilegeMethod
import com.example.service.ControlystAccessibilityService

enum class PrivilegeAvailabilityState {
    AVAILABLE,
    PERMISSION_REQUIRED,
    INSTALLED_NOT_RUNNING,
    NOT_FOUND,
    DENIED,
    UNVERIFIED,
    ERROR
}

data class PrivilegeProbeResult(
    val method: PrivilegeMethod,
    val state: PrivilegeAvailabilityState,
    val statusDetail: String,
    val latencyScoreMs: Int? = null
) {
    val isDetected: Boolean
        get() = state == PrivilegeAvailabilityState.AVAILABLE
}

/**
 * Privilege/backend detector using the real contracts recovered from the
 * device-verified 0.6.2 hardening build.
 *
 * Detection is deliberately distinct from usability. In particular, an alive
 * Shizuku/Sui binder without app authorization is PERMISSION_REQUIRED, and an
 * APatch environment is UNVERIFIED until its injection path is proven on real
 * hardware.
 */
class PrivilegeDetector(
    private val context: Context,
    private val shell: ShellExecutor = ProcessShellExecutor()
) {

    fun probeAll(): List<PrivilegeProbeResult> {
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

    fun detectBestMethod(): PrivilegeMethod {
        val probes = probeAll()
        return PrivilegeBackendSelector.choose(probes)?.method ?: PrivilegeMethod.ACCESSIBILITY
    }

    private fun detectShizuku(): PrivilegeProbeResult {
        return try {
            val access = ShizukuAccess.snapshot(context)
            if (!access.binderAlive) {
                return PrivilegeProbeResult(
                    method = PrivilegeMethod.SHIZUKU,
                    state = if (access.managerInstalled) {
                        PrivilegeAvailabilityState.INSTALLED_NOT_RUNNING
                    } else {
                        PrivilegeAvailabilityState.NOT_FOUND
                    },
                    statusDetail = if (access.managerInstalled) {
                        "Shizuku manager is installed but the Shizuku/Sui binder is not reachable yet"
                    } else {
                        "No Shizuku/Sui binder is reachable and the Shizuku manager package is not installed"
                    }
                )
            }

            val detail = buildString {
                append("binder=alive")
                append(" serverApi=").append(access.serverApi ?: -1)
                append(" uid=").append(access.serverUid ?: -1)
                append(" selinux=").append(access.selinuxContext ?: "unknown")
                append("; app permission=")
                append(
                    when {
                        access.permissionGranted -> "granted"
                        access.permissionBlocked -> "blocked; re-enable NEXUS INPUT in Shizuku Authorized applications"
                        else -> "requestable"
                    }
                )
            }

            PrivilegeProbeResult(
                method = PrivilegeMethod.SHIZUKU,
                state = if (access.permissionGranted) {
                    PrivilegeAvailabilityState.AVAILABLE
                } else {
                    PrivilegeAvailabilityState.PERMISSION_REQUIRED
                },
                statusDetail = detail
            )
        } catch (t: Throwable) {
            PrivilegeProbeResult(
                method = PrivilegeMethod.SHIZUKU,
                state = PrivilegeAvailabilityState.ERROR,
                statusDetail = "Shizuku/Sui probe failed: ${t.javaClass.simpleName}: ${t.message}"
            )
        }
    }

    private fun detectMagisk(
        rootUsable: Boolean,
        version: String,
        code: Long?
    ): PrivilegeProbeResult {
        if (!rootUsable) {
            return PrivilegeProbeResult(
                PrivilegeMethod.MAGISK,
                PrivilegeAvailabilityState.NOT_FOUND,
                "No usable uid=0 su session"
            )
        }

        val magiskCli = shell.run(listOf("su", "-c", "magisk -V"))
        val isMagisk = version.contains("magisk", ignoreCase = true) || magiskCli.succeeded
        return if (isMagisk) {
            PrivilegeProbeResult(
                method = PrivilegeMethod.MAGISK,
                state = PrivilegeAvailabilityState.AVAILABLE,
                statusDetail = "uid=0 confirmed; MagiskSU -c contract available"
            )
        } else {
            PrivilegeProbeResult(
                PrivilegeMethod.MAGISK,
                PrivilegeAvailabilityState.NOT_FOUND,
                "su is present but did not identify as Magisk"
            )
        }
    }

    private fun detectKernelSu(
        rootUsable: Boolean,
        version: String,
        code: Long?
    ): PrivilegeProbeResult {
        if (!rootUsable) {
            return PrivilegeProbeResult(
                PrivilegeMethod.KERNELSU,
                PrivilegeAvailabilityState.NOT_FOUND,
                "No usable uid=0 su session"
            )
        }

        val isKernelSu = version.contains("KernelSU", ignoreCase = true)
        if (!isKernelSu) {
            return PrivilegeProbeResult(
                PrivilegeMethod.KERNELSU,
                PrivilegeAvailabilityState.NOT_FOUND,
                "su is present but did not identify as KernelSU"
            )
        }

        val uinput = shell.run(listOf("su", "-c", "test -r /dev/uinput -a -w /dev/uinput"))
        return PrivilegeProbeResult(
            method = PrivilegeMethod.KERNELSU,
            state = if (uinput.succeeded) {
                PrivilegeAvailabilityState.AVAILABLE
            } else {
                PrivilegeAvailabilityState.DENIED
            },
            statusDetail = if (uinput.succeeded) {
                "uid=0 confirmed and /dev/uinput is readable+writable"
            } else {
                "KernelSU root works, but /dev/uinput prerequisites failed: ${uinput.stderr.ifBlank { "exit=${uinput.exitCode}" }}"
            }
        )
    }

    private fun detectAPatch(
        rootUsable: Boolean,
        version: String,
        code: Long?
    ): PrivilegeProbeResult {
        if (!rootUsable) {
            return PrivilegeProbeResult(
                PrivilegeMethod.APATCH,
                PrivilegeAvailabilityState.NOT_FOUND,
                "No usable uid=0 su session"
            )
        }

        val apd = shell.run(listOf("su", "-c", "command -v apd"))
        val isAPatch = version.contains("APatch", ignoreCase = true) || apd.succeeded
        return if (isAPatch) {
            PrivilegeProbeResult(
                method = PrivilegeMethod.APATCH,
                state = PrivilegeAvailabilityState.UNVERIFIED,
                statusDetail = "APatch apd/su environment detected, but the Nexus injection path is not hardware-verified and will not be auto-selected"
            )
        } else {
            PrivilegeProbeResult(
                PrivilegeMethod.APATCH,
                PrivilegeAvailabilityState.NOT_FOUND,
                "APatch apd was not found"
            )
        }
    }

    private fun detectAccessibility(): PrivilegeProbeResult {
        val active = probeAccessibilityService()
        return PrivilegeProbeResult(
            method = PrivilegeMethod.ACCESSIBILITY,
            state = if (active) {
                PrivilegeAvailabilityState.AVAILABLE
            } else {
                PrivilegeAvailabilityState.PERMISSION_REQUIRED
            },
            statusDetail = if (active) {
                "Accessibility service connected"
            } else {
                "Accessibility service is not enabled"
            }
        )
    }

    private fun probeAccessibilityService(): Boolean {
        if (ControlystAccessibilityService.isServiceRunning()) return true
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            enabledServices.contains(context.packageName)
        } catch (_: Throwable) {
            false
        }
    }
}
