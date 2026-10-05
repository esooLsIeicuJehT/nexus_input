package com.example.diagnostics

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.InputDevice
import com.example.BuildConfig
import com.example.data.ControlystDatabase
import com.example.injector.PrivilegeDetector
import com.example.input.ControllerInputMonitor
import com.example.input.ProfileValidator
import com.example.service.*
import org.json.JSONArray
import org.json.JSONObject

/** Read-only observations. This report does not assert device injection or hardware verification. */
object ReleaseDiagnostics {
    suspend fun collect(context: Context): String {
        val config = MappingRuntimeBridge.config.value
        val runtime = MappingRuntimeBridge.state.value
        val panic = PanicKillSwitch.state.value
        val devices = JSONArray()
        InputDevice.getDeviceIds().forEach { id -> InputDevice.getDevice(id)?.let { device ->
            devices.put(JSONObject().put("id",id).put("name",device.name).put("descriptor",device.descriptor)
                .put("sources",device.sources).put("vendorId",device.vendorId).put("productId",device.productId)
                .put("isVirtual",device.isVirtual).put("ranges",JSONArray().apply { device.motionRanges.forEach { range ->
                    put(JSONObject().put("axis",range.axis).put("source",range.source).put("min",range.min)
                        .put("max",range.max).put("flat",range.flat).put("fuzz",range.fuzz))
                } }))
        } }
        val probes = PrivilegeDetector(context).probeAll()
        val db = ControlystDatabase.getDatabase(context)
        val storage = runCatching { db.openHelper.readableDatabase.query("PRAGMA user_version").use { cursor ->
            check(cursor.moveToFirst()); cursor.getInt(0)
        } }
        return JSONObject().put("app",BuildConfig.APPLICATION_ID).put("version",BuildConfig.VERSION_NAME)
            .put("versionCode",BuildConfig.VERSION_CODE).put("sdk",Build.VERSION.SDK_INT)
            .put("manufacturer",Build.MANUFACTURER).put("model",Build.MODEL).put("abis",JSONArray(Build.SUPPORTED_ABIS.toList()))
            .put("kernel",System.getProperty("os.version") ?: JSONObject.NULL)
            .put("hardwareVerification","Not established by this self-check; perform the device checklist")
            .put("capture",JSONObject().put("connected",ControlystAccessibilityService.isServiceRunning())
                .put("globalMotionApiAvailable",Build.VERSION.SDK_INT >= 34).put("screenshotApiAvailable",Build.VERSION.SDK_INT >= 30)
                .put("overlayPermission",Settings.canDrawOverlays(context)))
            .put("runtime",JSONObject().put("armed",runtime.armed).put("gamePackage",runtime.gamePackage ?: JSONObject.NULL)
                .put("profileId",runtime.configId ?: JSONObject.NULL).put("foreground",runtime.targetForeground)
                .put("backend",runtime.backend?.name ?: JSONObject.NULL).put("backendReady",runtime.backendReady)
                .put("error",runtime.error ?: JSONObject.NULL).put("selectionNotice",runtime.notice ?: JSONObject.NULL)
                .put("profileValidation",JSONArray(config?.let { ProfileValidator.errors(it,runtime.gamePackage,runtime.configId) } ?: listOf("No armed profile"))))
            .put("panic",JSONObject().put("requested",panic.isKilled).put("releaseAcknowledged",panic.releaseConfirmed)
                .put("error",panic.error ?: JSONObject.NULL).put("timestamp",panic.lastTriggerTime))
            .put("storage",JSONObject().put("roomVersion",storage.getOrNull() ?: JSONObject.NULL)
                .put("error",storage.exceptionOrNull()?.message ?: JSONObject.NULL))
            .put("backends",JSONArray().apply { probes.forEach { probe ->
                put(JSONObject().put("method",probe.method.name).put("state",probe.state.name).put("detail",probe.statusDetail))
            } }).put("inputDevices",devices)
            .put("frameOverlay",JSONObject().put("enabled",com.example.frames.FrameMonitor.settings.value.enabled)
                .put("layer",com.example.frames.FrameMonitor.state.value.layer ?: JSONObject.NULL)
                .put("status",com.example.frames.FrameMonitor.state.value.status)
                .put("error",com.example.frames.FrameMonitor.state.value.error ?: JSONObject.NULL)
                .put("presentedFps",com.example.frames.FrameMonitor.state.value.stats?.fps ?: JSONObject.NULL))
            .put("lastControllerEventUptimeMs",ControllerInputMonitor.state.value.lastEventUptimeMs).toString(2)
    }
}
