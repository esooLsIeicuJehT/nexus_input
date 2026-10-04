package com.inputmapper.platform.debug

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.InputDevice
import android.view.accessibility.AccessibilityManager
import com.inputmapper.platform.BuildConfig
import com.inputmapper.platform.accessibility.MapperAccessibilityService
import com.inputmapper.platform.game.GameProfileStore
import com.inputmapper.platform.privilege.PrivilegeDetector
import com.inputmapper.platform.profile.ControllerProfileStore
import com.inputmapper.platform.shizuku.ShizukuAccess
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Read-only debug audit. It never injects input or changes permissions/settings.
 */
class SystemSelfCheck(private val context: Context) {
    fun run(): String {
        val app = context.applicationContext
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
        val lines = mutableListOf<String>()
        fun line(name: String, ok: Boolean, details: String) {
            lines += "${if (ok) "PASS" else "WARN"} | $name | $details"
        }

        lines += "NEXUS INPUT SELF CHECK"
        lines += "time=$timestamp"
        lines += "version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        lines += "android=${Build.VERSION.RELEASE} api=${Build.VERSION.SDK_INT} device=${Build.MANUFACTURER} ${Build.MODEL}"
        lines += ""

        val accessibility = isAccessibilityEnabled(app)
        line("Accessibility service", accessibility, if (accessibility) "connected/enabled" else "not connected")

        val notifications = Build.VERSION.SDK_INT < 33 || app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        line("Notification permission", notifications, if (notifications) "granted/not required" else "not granted")

        val shizuku = ShizukuAccess.snapshot(app)
        line(
            "Shizuku/Sui binder",
            shizuku.binderAlive,
            "alive=${shizuku.binderAlive} managerInstalled=${shizuku.managerInstalled} managerVersion=${shizuku.managerVersion ?: "unknown"}"
        )
        line(
            "Shizuku/Sui authorization",
            shizuku.permissionGranted,
            "granted=${shizuku.permissionGranted} blocked=${shizuku.permissionBlocked} uid=${shizuku.serverUid ?: -1} api=${shizuku.serverApi ?: -1} selinux=${shizuku.selinuxContext ?: "unknown"}"
        )

        PrivilegeDetector(app).detectAll().forEach { backend ->
            line("Backend ${backend.kind}", backend.state.name == "AVAILABLE", "${backend.state}: ${backend.details}")
        }

        val controllers = InputDevice.getDeviceIds()
            .map { InputDevice.getDevice(it) }
            .filterNotNull()
            .filter { device ->
                val sources = device.sources
                sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                    sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
            }
        line(
            "Controller detection",
            controllers.isNotEmpty(),
            if (controllers.isEmpty()) "no GAMEPAD/JOYSTICK device" else controllers.joinToString { "%s %04x:%04x".format(it.name, it.vendorId, it.productId) }
        )

        val controllerProfiles = ControllerProfileStore(app)
        controllers.forEach { device ->
            val id = ControllerProfileStore.stableProfileId(device.vendorId, device.productId, device.descriptor ?: "")
            val saved = controllerProfiles.load(id)
            line(
                "Calibration ${device.name}",
                saved != null,
                saved?.let { "${it.axes.size} axes, ${it.buttons.size} buttons" } ?: "no saved calibration"
            )
        }

        val gameStore = GameProfileStore(app)
        val profiles = gameStore.list()
        line("Game profiles", profiles.isNotEmpty(), "${profiles.size} saved")
        profiles.forEach { profile ->
            val errors = GameProfileValidator.validate(profile)
            val installed = runCatching { app.packageManager.getPackageInfo(profile.packageName, 0) }.isSuccess
            line(
                "Profile ${profile.displayName}",
                errors.isEmpty() && installed,
                "package=${profile.packageName} installed=$installed buttons=${profile.touchMappings.size} sticks=${profile.stickMappings.size}" +
                    if (errors.isEmpty()) "" else " issues=${errors.joinToString("; ")}"
            )
        }

        line(
            "Mapper runtime",
            MapperAccessibilityService.current != null,
            MapperAccessibilityService.current?.runtimeStatus() ?: "Accessibility runtime disconnected"
        )
        lines += "mapping_enabled=${gameStore.isMappingEnabled()} active_profile=${gameStore.activeProfile()?.displayName ?: "none"}"
        return lines.joinToString("\n")
    }

    private fun isAccessibilityEnabled(context: Context): Boolean {
        if (MapperAccessibilityService.current != null) return true
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                info.resolveInfo.serviceInfo.packageName == context.packageName &&
                    info.resolveInfo.serviceInfo.name == MapperAccessibilityService::class.java.name
            }
    }
}
