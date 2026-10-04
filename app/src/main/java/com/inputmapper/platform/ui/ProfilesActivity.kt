package com.inputmapper.platform.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.view.InputDevice
import android.widget.ScrollView
import android.widget.Toast
import com.inputmapper.platform.accessibility.MapperAccessibilityService
import com.inputmapper.platform.core.BackendKind
import com.inputmapper.platform.debug.GameProfileValidator
import com.inputmapper.platform.game.GameProfile
import com.inputmapper.platform.game.GameProfileStore
import com.inputmapper.platform.profile.ControllerProfileStore

class ProfilesActivity : Activity() {
    private lateinit var store: GameProfileStore
    private lateinit var controllerStore: ControllerProfileStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = GameProfileStore(this)
        controllerStore = ControllerProfileStore(this)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized) render()
    }

    private fun render() {
        val content = NexusUi.page(this)
        content.addView(NexusUi.eyebrow(this, "Profiles"))
        content.addView(NexusUi.title(this, "Game Profiles", 27f))
        content.addView(NexusUi.body(this, "Profiles persist across app restarts. Edit Layout opens the real accessibility overlay over the selected game; Play arms automatic per-game switching."))

        val runtime = NexusUi.card(this)
        runtime.addView(NexusUi.eyebrow(this, "Mapper Runtime"))
        runtime.addView(NexusUi.body(this, MapperAccessibilityService.current?.runtimeStatus() ?: "Accessibility service is not connected", 14f), NexusUi.marginParams(this, top = 8))
        content.addView(runtime)

        content.addView(NexusUi.primaryButton(this, "Create Profile from Installed Game") { pickInstalledApp() })
        content.addView(NexusUi.secondaryButton(this, "Disable Mapper") {
            val message = MapperAccessibilityService.current?.disableMapping() ?: "Accessibility service is not connected"
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            render()
        })

        val profiles = store.list()
        if (profiles.isEmpty()) {
            val card = NexusUi.card(this)
            card.addView(NexusUi.body(this, "No game profiles yet. Create one, open its overlay, place controller mappings over the game's controls, then press Play."))
            content.addView(card)
        } else {
            profiles.forEach { profile -> addProfileCard(content, profile) }
        }

        content.addView(NexusUi.secondaryButton(this, "Back") { finish() })
        setContentView(ScrollView(this).apply {
            setBackgroundColor(NexusUi.bg)
            addView(content)
        })
    }

    private fun addProfileCard(content: android.widget.LinearLayout, profile: GameProfile) {
        val card = NexusUi.card(this)
        card.addView(NexusUi.title(this, profile.displayName, 19f))
        card.addView(NexusUi.body(this, profile.packageName, 12f), NexusUi.marginParams(this, top = 4))
        val validation = GameProfileValidator.validate(profile)
        val backendLabel = profile.preferredBackend?.let { runCatching { BackendKind.valueOf(it) }.getOrNull()?.name } ?: "AUTO_COMPAT"
        card.addView(
            NexusUi.body(
                this,
                "${profile.touchMappings.size} button mappings • ${profile.stickMappings.size} stick mappings" +
                    " • backend=$backendLabel" +
                    if (store.activeProfileId() == profile.profileId) " • SELECTED" else "",
                13f
            ),
            NexusUi.marginParams(this, top = 8)
        )
        if (validation.isNotEmpty()) {
            card.addView(
                NexusUi.body(this, "Profile warnings: ${validation.joinToString("; ")}", 12f).apply { setTextColor(NexusUi.amber) },
                NexusUi.marginParams(this, top = 8)
            )
        }
        card.addView(NexusUi.primaryButton(this, "Edit Layout over Game") { editOverGame(profile) })
        card.addView(NexusUi.secondaryButton(this, "Screenshot Mapper") {
            startActivity(Intent(this, ScreenshotMapperActivity::class.java).putExtra(ScreenshotMapperActivity.EXTRA_PROFILE_ID, profile.profileId))
        })
        card.addView(NexusUi.secondaryButton(this, "Backend: $backendLabel") { chooseBackend(profile) })
        card.addView(NexusUi.secondaryButton(this, "Play / Arm Profile") {
            val message = MapperAccessibilityService.current?.enableMapping(profile.profileId)
                ?: "Accessibility service is not connected"
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            launchGame(profile)
        })
        card.addView(NexusUi.secondaryButton(this, "Launch Game") { launchGame(profile) })
        card.addView(NexusUi.dangerButton(this, "Delete Profile") {
            AlertDialog.Builder(this)
                .setTitle("Delete ${profile.displayName}?")
                .setMessage("This permanently removes its saved mapping layout.")
                .setPositiveButton("Delete") { _, _ -> store.remove(profile.profileId); render() }
                .setNegativeButton("Cancel", null)
                .show()
        })
        content.addView(card)
    }

    private fun chooseBackend(profile: GameProfile) {
        val labels = arrayOf(
            "Auto / Compatibility (Shizuku then KernelSU fallback)",
            "KernelSU Native",
            "Shizuku / Sui",
            "Accessibility (limited fallback)"
        )
        val values = arrayOf<String?>(
            null,
            BackendKind.KERNEL_SU.name,
            BackendKind.SHIZUKU.name,
            BackendKind.ACCESSIBILITY.name
        )
        val current = values.indexOf(profile.preferredBackend).takeIf { it >= 0 } ?: 0
        AlertDialog.Builder(this)
            .setTitle("Injection backend for ${profile.displayName}")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                store.save(profile.copy(preferredBackend = values[which], savedAtMillis = System.currentTimeMillis()))
                dialog.dismiss()
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickInstalledApp() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        if (apps.isEmpty()) {
            Toast.makeText(this, "No launchable apps were returned by Android package visibility.", Toast.LENGTH_LONG).show()
            return
        }
        val labels = apps.map { "${it.loadLabel(packageManager)}\n${it.activityInfo.packageName}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Choose game / app")
            .setItems(labels) { _, which -> createProfile(apps[which]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createProfile(info: ResolveInfo) {
        val packageName = info.activityInfo.packageName
        val existing = store.findByPackage(packageName)
        if (existing != null) {
            Toast.makeText(this, "A profile already exists for ${existing.displayName}.", Toast.LENGTH_LONG).show()
            return
        }
        val name = info.loadLabel(packageManager).toString().ifBlank { packageName }
        var profile = store.create(name, packageName)
        val controllerId = currentControllerProfileId()
        if (controllerId != null) {
            profile = profile.copy(controllerProfileId = controllerId, savedAtMillis = System.currentTimeMillis())
            store.save(profile)
        }
        store.setActiveProfile(profile.profileId)
        render()
    }

    private fun currentControllerProfileId(): String? {
        val device = InputDevice.getDeviceIds().map { InputDevice.getDevice(it) }.filterNotNull().firstOrNull { input ->
            input.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                input.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        } ?: return null
        val id = ControllerProfileStore.stableProfileId(device.vendorId, device.productId, device.descriptor ?: "")
        return controllerStore.load(id)?.profileId
    }

    private fun editOverGame(profile: GameProfile) {
        val service = MapperAccessibilityService.current
        if (service == null) {
            Toast.makeText(this, "Accessibility service must be enabled before the overlay editor can run.", Toast.LENGTH_LONG).show()
            return
        }
        val result = service.showOverlayEditor(profile.profileId)
        Toast.makeText(this, result, Toast.LENGTH_SHORT).show()
        launchGame(profile)
    }

    private fun launchGame(profile: GameProfile) {
        val launch = packageManager.getLaunchIntentForPackage(profile.packageName)
        if (launch == null) {
            Toast.makeText(this, "Android did not return a launch intent for ${profile.packageName}.", Toast.LENGTH_LONG).show()
            return
        }
        startActivity(launch)
    }
}
