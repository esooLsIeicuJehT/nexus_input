package com.example.diagnostics

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.InputDevice
import com.example.BuildConfig
import com.example.data.ControlystDatabase
import com.example.injector.PrivilegeDetector
import com.example.input.ControllerInputMonitor
import com.example.input.ControllerRoutingTrace
import com.example.input.ProfileValidator
import com.example.service.*
import org.json.JSONArray
import org.json.JSONObject

/** Read-only observations. This report does not assert device injection or hardware verification. */
object ReleaseDiagnostics {
    /** Share routing evidence without the raw motion log crowding out the saved bindings. */
    fun inputReport(report: String): String? = runCatching {
        val full = JSONObject(report)
        val routing = full.getJSONObject("controllerRouting")
        val compact = JSONObject()
        for (key in listOf("app", "version", "model", "sdk", "capture", "runtime", "backends")) {
            compact.put(key, full.opt(key) ?: JSONObject.NULL)
        }
        val routes = routing.optJSONArray("recentRoutes") ?: JSONArray()
        val recent = JSONArray()
        for (index in maxOf(0, routes.length() - 24) until routes.length()) recent.put(routes.get(index))
        compact.put("controllerRouting", JSONObject().apply {
            for (key in listOf("deviceId", "keyCode", "scanCode", "source", "selectedProfile", "bindingsProfile", "bindings")) {
                put(key, routing.opt(key) ?: JSONObject.NULL)
            }
            put("recentRoutes", recent)
        }).toString(2)
    }.getOrNull()

    suspend fun collect(context: Context, selectedProfile: com.example.model.MappingConfig? = null): String {
        val config = MappingRuntimeBridge.config.value
        val runtime = MappingRuntimeBridge.state.value
        val panic = PanicKillSwitch.state.value
        val devices = JSONArray()
        InputDevice.getDeviceIds().forEach { id -> InputDevice.getDevice(id)?.let { device ->
            // Capability observations only. A delivered button/touch does not
            // imply Android exposes a controller's motors or game rumble feedback.
            val vibration = runCatching {
                if (Build.VERSION.SDK_INT >= 31) device.vibratorManager.vibratorIds.isNotEmpty()
                else {
                    @Suppress("DEPRECATION")
                    device.vibrator.hasVibrator()
                }
            }
            devices.put(JSONObject().put("id",id).put("name",device.name).put("descriptor",device.descriptor)
                .put("sources",device.sources).put("vendorId",device.vendorId).put("productId",device.productId)
                .put("vibratorReported",vibration.getOrNull() ?: JSONObject.NULL)
                .put("vibratorObservationError",vibration.exceptionOrNull()?.message ?: JSONObject.NULL)
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
        val live = ControllerInputMonitor.state.value
        // A newly selected editor profile can differ from the armed game's profile.
        // Diagnose the actual runtime targets first, while labeling both identities.
        val profile = config ?: selectedProfile
        val slots = profile?.let { runCatching { com.example.input.TouchSlotAllocator.assign(it) }.getOrNull() }
        return JSONObject().put("app",BuildConfig.APPLICATION_ID).put("version",BuildConfig.VERSION_NAME)
            .put("versionCode",BuildConfig.VERSION_CODE).put("sdk",Build.VERSION.SDK_INT)
            .put("manufacturer",Build.MANUFACTURER).put("model",Build.MODEL).put("abis",JSONArray(Build.SUPPORTED_ABIS.toList()))
            .put("kernel",System.getProperty("os.version") ?: JSONObject.NULL)
            .put("hardwareVerification","Not established by this self-check; perform the device checklist")
            .put("controllerRumble",JSONObject().put("gameFeedbackRelayImplemented",false)
                .put("note","Controller motor capability is reported per input device. No rumble pulse is sent by this self-check."))
            .put("capture",JSONObject().put("connected",ControlystAccessibilityService.isServiceRunning())
                .put("pendingAccessibilityGestures",ControlystAccessibilityService.getInstance()?.pendingGestureCount ?: JSONObject.NULL)
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
            .put("lastControllerEventUptimeMs",live.lastEventUptimeMs)
            .put("controllerRouting",JSONObject().put("deviceId",live.deviceId ?: JSONObject.NULL)
                .put("keyCode",live.lastKeyCode ?: JSONObject.NULL).put("scanCode",live.lastScanCode ?: JSONObject.NULL)
                .put("source",live.lastSource ?: JSONObject.NULL).put("axes",JSONObject(live.axes))
                .put("events",JSONArray(live.eventLog)).put("selectedProfile",selectedProfile?.id ?: profile?.id ?: JSONObject.NULL)
                .put("bindingsProfile",profile?.id ?: JSONObject.NULL)
                .put("recentRoutes",JSONArray().apply { ControllerRoutingTrace.snapshot().forEach { route ->
                    put(JSONObject().put("uptimeMs",route.uptimeMs).put("profileId",route.profileId)
                        .put("input",route.input).put("result",route.result)
                        .put("backend",route.backend ?: JSONObject.NULL))
                } })
                .put("bindings",JSONArray().apply { profile?.buttons?.forEach { node ->
                    put(JSONObject().put("id",node.id).put("binding",node.boundKey).put("type",node.type.name)
                        .put("keyCode",node.inputKeyCode ?: JSONObject.NULL).put("scanCode",node.inputScanCode ?: JSONObject.NULL)
                        .put("slot",node.touchSlot ?: JSONObject.NULL).put("resolvedSlot",slots?.get(node.id) ?: JSONObject.NULL)
                        .put("behavior",node.buttonBehavior.name).put("x",node.xNorm).put("y",node.yNorm))
                } })).toString(2)
    }
}
