package com.inputmapper.platform.setup

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.inputmapper.platform.accessibility.MapperAccessibilityService
import com.inputmapper.platform.ui.MainActivity
import com.inputmapper.platform.ui.NexusUi
import com.inputmapper.platform.ui.Phase0DiagnosticsActivity
import com.inputmapper.platform.shizuku.ShizukuAccess
import rikka.shizuku.Shizuku

/**
 * First-run setup coordinator.
 *
 * Android Accessibility is a special settings grant rather than a normal runtime permission.
 * NEXUS INPUT uses TYPE_ACCESSIBILITY_OVERLAY for its in-game editor and Accessibility events for
 * foreground-game awareness, so Draw-over-other-apps and Usage Access are not required.
 */
class SetupActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var statusView: TextView
    private lateinit var continueButton: Button
    private var promptVisible = false
    private var waitingForSettingsReturn = false
    private var onboardingStarted = false

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode != SHIZUKU_REQUEST) return@OnRequestPermissionResultListener
        runOnUiThread {
            promptVisible = false
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            Toast.makeText(this, "Shizuku permission ${if (granted) "granted" else "denied"}", Toast.LENGTH_SHORT).show()
            updateStatus()
            if (granted) {
                preferences().edit().remove(KEY_SKIP_SHIZUKU).apply()
                advanceSetup()
            } else {
                // Do not immediately loop into another permission request. The user can return to
                // setup later; Continue setup clears this temporary skip and re-evaluates the
                // documented Shizuku rationale state.
                preferences().edit().putBoolean(KEY_SKIP_SHIZUKU, true).apply()
                finishSetup()
            }
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread {
            updateStatus()
            if (onboardingStarted && !promptVisible && !waitingForSettingsReturn) {
                advanceSetup()
            }
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        runOnUiThread { updateStatus() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()

        val content = NexusUi.page(this)
        content.addView(NexusUi.eyebrow(this, "NEXUS INPUT"))
        content.addView(NexusUi.title(this, "Setup", 28f))
        content.addView(
            NexusUi.body(
                this,
                "One-time permissions and backend authorization. KernelSU remains usable even when the optional Shizuku/Sui backend is unavailable."
            )
        )

        val statusCard = NexusUi.card(this)
        statusCard.addView(NexusUi.eyebrow(this, "Readiness"))
        statusView = NexusUi.body(this, "Checking…", 14f)
        statusCard.addView(statusView, NexusUi.marginParams(this, top = 8))
        content.addView(statusCard)

        continueButton = NexusUi.primaryButton(this, "Continue setup") {
            onboardingStarted = true
            preferences().edit()
                .remove(KEY_SKIP_NOTIFICATIONS)
                .remove(KEY_SKIP_SHIZUKU)
                .apply()
            advanceSetup()
        }
        content.addView(continueButton)
        content.addView(NexusUi.secondaryButton(this, "Open diagnostics dashboard") { openDashboard() })
        content.addView(NexusUi.secondaryButton(this, "Back") { finish() })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(NexusUi.bg)
            addView(content)
        })

        // An already-running Shizuku/Sui may deliver the sticky binder callback immediately.
        // Register after the setup views exist so updateStatus() cannot race UI initialization.
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)

        updateStatus()
        if (!preferences().getBoolean(KEY_SETUP_SEEN, false)) {
            onboardingStarted = true
            handler.postDelayed({ advanceSetup() }, 450L)
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        if (waitingForSettingsReturn) {
            waitingForSettingsReturn = false
            handler.postDelayed({ advanceSetup() }, 350L)
        }
    }

    private fun advanceSetup() {
        if (promptVisible || waitingForSettingsReturn) return
        updateStatus()

        if (!isAccessibilityEnabled()) {
            showSettingsStep(
                title = "Enable Accessibility control",
                message = "NEXUS INPUT uses Accessibility as a non-root fallback and for Android-side input capture. Android does not provide a normal permission popup for this; the next button opens Accessibility settings.",
                positive = "Open Accessibility settings",
                intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            )
            return
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !preferences().getBoolean(KEY_SKIP_NOTIFICATIONS, false)
        ) {
            promptVisible = true
            AlertDialog.Builder(this)
                .setTitle("Allow notifications")
                .setMessage("NEXUS INPUT will use an ongoing notification for its mapper service so Android does not silently kill it during a game.")
                .setPositiveButton("Allow") { _, _ ->
                    promptVisible = false
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST)
                }
                .setNegativeButton("Not now") { _, _ ->
                    promptVisible = false
                    preferences().edit().putBoolean(KEY_SKIP_NOTIFICATIONS, true).apply()
                    advanceSetup()
                }
                .setOnCancelListener {
                    promptVisible = false
                    preferences().edit().putBoolean(KEY_SKIP_NOTIFICATIONS, true).apply()
                    advanceSetup()
                }
                .show()
            return
        }

        val shizuku = ShizukuAccess.snapshot(this)
        if (shizuku.binderAlive && !shizuku.permissionGranted && !preferences().getBoolean(KEY_SKIP_SHIZUKU, false)) {
            if (shizuku.permissionBlocked) {
                promptVisible = true
                AlertDialog.Builder(this)
                    .setTitle("Shizuku authorization is blocked")
                    .setMessage(
                        "Shizuku/Sui reports that NEXUS INPUT cannot show another permission request. " +
                            "Open Shizuku, choose Authorized applications, and enable NEXUS INPUT. " +
                            "KernelSU can still run NEXUS while Shizuku is unavailable."
                    )
                    .setPositiveButton("Open Shizuku") { _, _ ->
                        promptVisible = false
                        waitingForSettingsReturn = true
                        if (!ShizukuAccess.openManager(this)) {
                            waitingForSettingsReturn = false
                            Toast.makeText(this, "Shizuku manager is not installed. KernelSU remains available.", Toast.LENGTH_LONG).show()
                            finishSetup()
                        }
                    }
                    .setNegativeButton("Use KernelSU only") { _, _ ->
                        promptVisible = false
                        preferences().edit().putBoolean(KEY_SKIP_SHIZUKU, true).apply()
                        finishSetup()
                    }
                    .setOnCancelListener {
                        promptVisible = false
                        finishSetup()
                    }
                    .show()
                return
            }

            promptVisible = true
            AlertDialog.Builder(this)
                .setTitle("Authorize Shizuku / Sui")
                .setMessage("A Shizuku or Sui binder is available. Authorizing it gives NEXUS INPUT a secondary privileged input backend. KernelSU remains available if you skip this step.")
                .setPositiveButton("Authorize") { _, _ ->
                    promptVisible = false
                    try {
                        Shizuku.requestPermission(SHIZUKU_REQUEST)
                    } catch (t: Throwable) {
                        Toast.makeText(this, "Shizuku request failed: ${t.javaClass.simpleName}: ${t.message}", Toast.LENGTH_LONG).show()
                        advanceSetup()
                    }
                }
                .setNegativeButton("Use KernelSU only") { _, _ ->
                    promptVisible = false
                    preferences().edit().putBoolean(KEY_SKIP_SHIZUKU, true).apply()
                    finishSetup()
                }
                .setOnCancelListener {
                    promptVisible = false
                    finishSetup()
                }
                .show()
            return
        }

        finishSetup()
    }

    private fun showSettingsStep(
        title: String,
        message: String,
        positive: String,
        intent: Intent,
        optional: Boolean = false,
        skipKey: String? = null
    ) {
        promptVisible = true
        val builder = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positive) { _, _ ->
                promptVisible = false
                if (optional && skipKey != null) preferences().edit().putBoolean(skipKey, true).apply()
                waitingForSettingsReturn = true
                try {
                    startActivity(intent)
                } catch (t: Throwable) {
                    waitingForSettingsReturn = false
                    Toast.makeText(this, "Unable to open settings: ${t.message}", Toast.LENGTH_LONG).show()
                    advanceSetup()
                }
            }
            .setOnCancelListener {
                promptVisible = false
                if (optional) advanceSetup()
            }
        if (optional) {
            builder.setNegativeButton("Skip") { _, _ ->
                promptVisible = false
                if (skipKey != null) preferences().edit().putBoolean(skipKey, true).apply()
                advanceSetup()
            }
        }
        builder.show()
    }

    private fun finishSetup() {
        preferences().edit().putBoolean(KEY_SETUP_SEEN, true).apply()
        updateStatus()
        Toast.makeText(this, "Setup pass complete.", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun openDashboard() {
        startActivity(Intent(this, Phase0DiagnosticsActivity::class.java))
    }

    private fun updateStatus() {
        if (!::statusView.isInitialized) return
        val accessibility = isAccessibilityEnabled()
        val notifications = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val shizuku = ShizukuAccess.snapshot(this)

        statusView.text = buildString {
            append("Accessibility     ").append(if (accessibility) "READY" else "ACTION NEEDED").append('\n')
            append("Notifications     ").append(if (notifications) "READY" else "ACTION NEEDED").append('\n')
            append("Overlay editor    READY (Accessibility overlay)").append('\n')
            append("Game detection    READY (Accessibility events)").append('\n')
            append("Shizuku/Sui       ").append(
                when {
                    shizuku.permissionGranted -> "READY (uid=${shizuku.serverUid ?: -1}, API ${shizuku.serverApi ?: -1})"
                    shizuku.permissionBlocked -> "AUTHORIZATION BLOCKED - open Shizuku Authorized applications"
                    shizuku.binderAlive -> "PERMISSION NEEDED"
                    else -> "BINDER NOT AVAILABLE (optional on KernelSU)"
                }
            )
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        if (MapperAccessibilityService.current != null) return true
        val manager = getSystemService(AccessibilityManager::class.java) ?: return false
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info -> info.resolveInfo.serviceInfo.packageName == packageName && info.resolveInfo.serviceInfo.name == MapperAccessibilityService::class.java.name }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Mapper status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing NEXUS INPUT mapper and backend status"
            }
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_REQUEST) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            if (!granted) preferences().edit().putBoolean(KEY_SKIP_NOTIFICATIONS, true).apply()
            updateStatus()
            advanceSetup()
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        super.onDestroy()
    }

    private fun preferences() = getSharedPreferences("gamepad_pro_setup", Context.MODE_PRIVATE)

    companion object {
        private const val SHIZUKU_REQUEST = 4101
        private const val NOTIFICATION_REQUEST = 4102
        private const val CHANNEL_ID = "mapper_status"
        private const val KEY_SETUP_SEEN = "setup_seen"
        private const val KEY_SKIP_NOTIFICATIONS = "skip_notifications"
        private const val KEY_SKIP_SHIZUKU = "skip_shizuku"
    }
}
