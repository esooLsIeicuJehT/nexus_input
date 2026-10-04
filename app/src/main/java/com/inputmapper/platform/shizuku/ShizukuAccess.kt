package com.inputmapper.platform.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Centralized snapshot of the Shizuku/Sui binder and authorization state.
 *
 * Shizuku's own API documents four distinct states that matter to callers:
 * binder unavailable, permission granted, permission requestable, and a denied state where
 * shouldShowRequestPermissionRationale() is true.  Keep those states separate so setup never
 * loops on a permission request that the manager will not show again.
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

    /** Opens the Shizuku manager when installed. Returns false when Android has no launch intent. */
    fun openManager(context: Context): Boolean {
        val intent: Intent = context.packageManager.getLaunchIntentForPackage(MANAGER_PACKAGE)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
}
