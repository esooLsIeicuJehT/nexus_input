package com.example.injector

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Read-only snapshot of the Shizuku/Sui binder and app authorization state.
 *
 * This is adapted from the device-verified 0.6.2 hardening source. Binder
 * availability and app authorization are intentionally separate states.
 */
data class ShizukuAccessSnapshot(
    val binderAlive: Boolean,
    val permissionGranted: Boolean,
    val permissionBlocked: Boolean,
    val serverUid: Int?,
    val serverApi: Int?,
    val selinuxContext: String?,
    val managerInstalled: Boolean,
    val managerVersion: String?
) {
    val permissionRequestable: Boolean
        get() = binderAlive && !permissionGranted && !permissionBlocked

    fun summary(): String = when {
        !binderAlive -> "BINDER NOT AVAILABLE"
        permissionGranted -> "READY • uid=${serverUid ?: -1} • API ${serverApi ?: -1}"
        permissionBlocked -> "AUTHORIZATION BLOCKED • open Shizuku manager"
        else -> "PERMISSION NEEDED"
    }
}

object ShizukuAccess {
    const val MANAGER_PACKAGE = "moe.shizuku.privileged.api"

    fun snapshot(context: Context): ShizukuAccessSnapshot {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(MANAGER_PACKAGE, 0)
        }.getOrNull()

        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!binderAlive) {
            return ShizukuAccessSnapshot(
                binderAlive = false,
                permissionGranted = false,
                permissionBlocked = false,
                serverUid = null,
                serverApi = null,
                selinuxContext = null,
                managerInstalled = packageInfo != null,
                managerVersion = packageInfo?.versionName
            )
        }

        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

        val blocked = if (granted) {
            false
        } else {
            runCatching { Shizuku.shouldShowRequestPermissionRationale() }.getOrDefault(false)
        }

        return ShizukuAccessSnapshot(
            binderAlive = true,
            permissionGranted = granted,
            permissionBlocked = blocked,
            serverUid = runCatching { Shizuku.getUid() }.getOrNull(),
            serverApi = runCatching { Shizuku.getVersion() }.getOrNull(),
            selinuxContext = runCatching { Shizuku.getSELinuxContext() }.getOrNull(),
            managerInstalled = packageInfo != null,
            managerVersion = packageInfo?.versionName
        )
    }

    fun openManager(context: Context): Boolean {
        val intent: Intent = context.packageManager.getLaunchIntentForPackage(MANAGER_PACKAGE)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
}
