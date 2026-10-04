package com.inputmapper.platform.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.InputDevice
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.inputmapper.platform.accessibility.MapperAccessibilityService
import com.inputmapper.platform.core.AvailabilityState
import com.inputmapper.platform.core.BackendKind
import com.inputmapper.platform.game.GameProfileStore
import com.inputmapper.platform.privilege.PrivilegeDetector
import com.inputmapper.platform.profile.ControllerProfileStore
import com.inputmapper.platform.setup.SetupActivity
import com.inputmapper.platform.shizuku.ShizukuAccess

class MainActivity : Activity() {
    private lateinit var engineValue: TextView
    private lateinit var controllerValue: TextView
    private lateinit var controllerProfileValue: TextView
    private lateinit var gameProfileValue: TextView
    private lateinit var runtimeValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        val gameStore = GameProfileStore(this)
        val activeProfile = gameStore.activeProfile()
        val content = NexusUi.page(this)
        content.addView(NexusUi.eyebrow(this, "Nexus Input"))
        content.addView(NexusUi.title(this, "Play Your Way."))
        content.addView(NexusUi.body(this, "Persistent controller-to-touch mapping with KernelSU first, Shizuku/Sui fallback, per-game profiles, and a live in-game overlay editor."))

        val engineCard = NexusUi.card(this)
        engineCard.addView(NexusUi.eyebrow(this, "Engine"))
        engineValue = NexusUi.body(this, "Checking privileged backends…", 15f)
        engineCard.addView(engineValue, NexusUi.marginParams(this, top = 8))
        runtimeValue = NexusUi.body(this, MapperAccessibilityService.current?.runtimeStatus() ?: "Accessibility service disconnected", 13f)
        engineCard.addView(runtimeValue, NexusUi.marginParams(this, top = 8))
        content.addView(engineCard)

        val controllerCard = NexusUi.card(this)
        controllerCard.addView(NexusUi.eyebrow(this, "Connected Controller"))
        controllerValue = NexusUi.body(this, "Checking…", 15f)
        controllerCard.addView(controllerValue, NexusUi.marginParams(this, top = 8))
        controllerProfileValue = NexusUi.body(this, "", 13f)
        controllerCard.addView(controllerProfileValue, NexusUi.marginParams(this, top = 8))
        content.addView(controllerCard)

        val profileCard = NexusUi.card(this)
        profileCard.addView(NexusUi.eyebrow(this, "Active Profile"))
        gameProfileValue = NexusUi.body(
            this,
            activeProfile?.let { "${it.displayName}\n${it.packageName}\n${it.touchMappings.size} buttons • ${it.stickMappings.size} sticks" }
                ?: "No game profile selected yet.",
            14f
        )
        profileCard.addView(gameProfileValue, NexusUi.marginParams(this, top = 8))
        content.addView(profileCard)

        content.addView(NexusUi.primaryButton(this, "Profiles & In-Game Layout Editor") {
            startActivity(Intent(this, ProfilesActivity::class.java))
        })

        if (activeProfile != null) {
            content.addView(NexusUi.secondaryButton(this, "Arm & Launch ${activeProfile.displayName}") {
                val service = MapperAccessibilityService.current
                if (service == null) {
                    Toast.makeText(this, "Enable Accessibility before starting the mapper.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, service.enableMapping(activeProfile.profileId), Toast.LENGTH_SHORT).show()
                    packageManager.getLaunchIntentForPackage(activeProfile.packageName)?.let { startActivity(it) }
                }
            })
            content.addView(NexusUi.secondaryButton(this, "Screenshot Mapper for ${activeProfile.displayName}") {
                startActivity(Intent(this, ScreenshotMapperActivity::class.java).putExtra(ScreenshotMapperActivity.EXTRA_PROFILE_ID, activeProfile.profileId))
            })
            content.addView(NexusUi.secondaryButton(this, "Edit Active Layout over Game") {
                val service = MapperAccessibilityService.current
                if (service == null) {
                    Toast.makeText(this, "Enable Accessibility before starting the overlay editor.", Toast.LENGTH_LONG).show()
                } else {
                    service.showOverlayEditor(activeProfile.profileId)
                    packageManager.getLaunchIntentForPackage(activeProfile.packageName)?.let { startActivity(it) }
                }
            })
        }

        content.addView(NexusUi.secondaryButton(this, "Controller Calibration") {
            startActivity(Intent(this, ControllerCalibrationActivity::class.java))
        })
        content.addView(NexusUi.secondaryButton(this, "Controller Tester") {
            startActivity(Intent(this, ControllerLiveActivity::class.java))
        })
        content.addView(NexusUi.secondaryButton(this, "Engine & System Diagnostics") {
            startActivity(Intent(this, Phase0DiagnosticsActivity::class.java))
        })
        content.addView(NexusUi.secondaryButton(this, "Permissions / Setup") {
            startActivity(Intent(this, SetupActivity::class.java))
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(NexusUi.bg)
            addView(content)
        })
    }

    override fun onResume() {
        super.onResume()
        if (!getSharedPreferences("gamepad_pro_setup", Context.MODE_PRIVATE).getBoolean("setup_seen", false)) {
            startActivity(Intent(this, SetupActivity::class.java))
            return
        }
        if (::engineValue.isInitialized) {
            refreshControllerState()
            refreshBackendState()
            val store = GameProfileStore(this)
            gameProfileValue.text = store.activeProfile()?.let {
                "${it.displayName}\n${it.packageName}\n${it.touchMappings.size} buttons • ${it.stickMappings.size} sticks"
            } ?: "No game profile selected yet."
            runtimeValue.text = MapperAccessibilityService.current?.runtimeStatus() ?: "Accessibility service disconnected"
        }
    }

    private fun refreshBackendState() {
        engineValue.text = "Checking privileged backends…"
        Thread {
            val all = PrivilegeDetector(this).detectAll()
            val ksu = all.firstOrNull { it.kind == BackendKind.KERNEL_SU }
            val shizuku = all.firstOrNull { it.kind == BackendKind.SHIZUKU }
            val shizukuAccess = ShizukuAccess.snapshot(this)
            runOnUiThread {
                val ksuText = if (ksu?.state == AvailabilityState.AVAILABLE) "KernelSU ready" else "KernelSU ${ksu?.state ?: "unknown"}"
                val shizukuText = when {
                    shizuku?.state == AvailabilityState.AVAILABLE -> "Shizuku/Sui ready"
                    shizukuAccess.permissionBlocked -> "Shizuku/Sui authorization blocked • repair in Setup"
                    shizukuAccess.binderAlive -> "Shizuku/Sui permission needed"
                    else -> "Shizuku/Sui ${shizuku?.state ?: "unknown"}"
                }
                engineValue.text = "$ksuText\n$shizukuText"
                engineValue.setTextColor(if (ksu?.state == AvailabilityState.AVAILABLE || shizuku?.state == AvailabilityState.AVAILABLE) NexusUi.green else NexusUi.amber)
            }
        }.start()
    }

    private fun refreshControllerState() {
        val controller = InputDevice.getDeviceIds()
            .map { InputDevice.getDevice(it) }
            .filterNotNull()
            .firstOrNull { device ->
                val sources = device.sources
                sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                    sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
            }

        if (controller == null) {
            controllerValue.text = "No GAMEPAD/JOYSTICK controller connected"
            controllerValue.setTextColor(NexusUi.amber)
            controllerProfileValue.text = "Connect a controller to calibrate or map it."
            return
        }

        controllerValue.text = "${controller.name}\nVID:PID %04x:%04x".format(controller.vendorId, controller.productId)
        controllerValue.setTextColor(NexusUi.text)

        val profiles = ControllerProfileStore(this).list()
        val id = ControllerProfileStore.stableProfileId(controller.vendorId, controller.productId, controller.descriptor ?: "")
        val matching = profiles.firstOrNull { it.profileId == id }
        controllerProfileValue.text = if (matching == null) {
            "No saved calibration for this controller"
        } else {
            "Calibrated: ${matching.axes.size} axes • ${matching.buttons.size} buttons"
        }
        controllerProfileValue.setTextColor(if (matching != null) NexusUi.green else NexusUi.amber)
    }
}
