package com.example.injector

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import rikka.sui.Sui

/**
 * Read-only snapshot of the Shizuku/Sui binder and app authorization state.
 *
 * Binder availability and app authorization are intentionally separate states.
 * Sui identity is observational only; readiness still requires a live Shizuku API
 * binder and an explicit permission result.
 */
data class ShizukuAccessSnapshot(
    val binderAlive: Boolean,
    val suiActive: Boolean,
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

    val transportLabel: String
        get() = if (suiActive) "SUI" else "SHIZUKU"

    fun summary(): String = when {
        !binderAlive && suiActive -> "SUI INITIALIZED • BINDER NOT AVAILABLE"
        !binderAlive -> "SHIZUKU/SUI BINDER NOT AVAILABLE"
        permissionGranted -> "$transportLabel READY • uid=${serverUid ?: -1} • API ${serverApi ?: -1}"
        permissionBlocked -> "$transportLabel AUTHORIZATION BLOCKED"
        else -> "$transportLabel PERMISSION NEEDED"
    }
}

object ShizukuAccess {
    const val MANAGER_PACKAGE = "moe.shizuku.privileged.api"

    fun snapshot(context: Context): ShizukuAccessSnapshot {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(MANAGER_PACKAGE, 0)
        }.getOrNull()
        val suiActive = runCatching { Sui.isSui() }.getOrDefault(false)
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

        if (!binderAlive) {
            return ShizukuAccessSnapshot(
                binderAlive = false,
                suiActive = suiActive,
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
            suiActive = suiActive,
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
