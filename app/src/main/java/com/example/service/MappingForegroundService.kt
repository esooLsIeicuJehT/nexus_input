package com.example.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.injector.InputInjector
import com.example.injector.InputInjectorFactory
import com.example.injector.PrivilegeDetector
import com.example.model.MappingConfig
import com.example.model.PrivilegeMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.example.model.CrosshairConfig

class MappingForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "controlyst_mapping_service_channel"
        const val NOTIFICATION_ID = 202

        const val ACTION_START_MAPPING = "com.example.action.START_MAPPING"
        const val ACTION_STOP_MAPPING = "com.example.action.STOP_MAPPING"
        const val ACTION_PANIC_KILL = "com.example.action.PANIC_KILL"
        const val EXTRA_GAME_PACKAGE = "extra_game_package"
        const val EXTRA_CONFIG_ID = "extra_config_id"

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

        private val _activeGamePackage = MutableStateFlow<String?>(null)
        val activeGamePackage: StateFlow<String?> = _activeGamePackage.asStateFlow()

        private val _isOverlayVisible = MutableStateFlow(true)
        val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible.asStateFlow()

        val currentCrosshairConfig = MutableStateFlow(CrosshairConfig())

        fun setOverlayVisibility(visible: Boolean) {
            _isOverlayVisible.value = visible
        }

        fun triggerPanicKill(context: Context) {
            val intent = Intent(context, MappingForegroundService::class.java).apply {
                action = ACTION_PANIC_KILL
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private var exitWatcherJob: Job? = null
    private var activeInjector: InputInjector? = null
    private var crosshairOverlayManager: CrosshairOverlayManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_MAPPING -> {
                stopMapping()
                stopSelf()
            }
            ACTION_PANIC_KILL -> {
                // Instantly dismiss overlay and stop touch injection
                _isOverlayVisible.value = false
                activeInjector?.cleanup()
                stopMapping()
                stopSelf()
            }
            else -> {
                val gamePkg = intent?.getStringExtra(EXTRA_GAME_PACKAGE) ?: "com.proxima.dfm"
                startMapping(gamePkg)
            }
        }
        return START_NOT_STICKY
    }

    private fun startMapping(gamePkg: String) {
        _isServiceActive.value = true
        _activeGamePackage.value = gamePkg
        _isOverlayVisible.value = true

        val notification = buildNotification(gamePkg)
        startForeground(NOTIFICATION_ID, notification)

        // Initialize active injector using dynamic PrivilegeDetector best method
        val bestMethod = PrivilegeDetector(this).detectBestMethod()
        activeInjector = InputInjectorFactory.createInjector(bestMethod)

        // Start floating crosshair overlay manager if permitted
        if (android.provider.Settings.canDrawOverlays(this)) {
            crosshairOverlayManager?.hideOverlay()
            crosshairOverlayManager = CrosshairOverlayManager(this).apply {
                showOverlay(currentCrosshairConfig)
            }
        }

        // Start background game exit monitoring (checks if game process is alive)
        exitWatcherJob?.cancel()
        exitWatcherJob = serviceScope.launch {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            while (_isServiceActive.value) {
                delay(4000)
                am?.runningAppProcesses?.let { processes ->
                    val isRunning = processes.any { it.processName == gamePkg || it.pkgList?.contains(gamePkg) == true }
                    // If target game process is no longer running in foreground/background, auto-stop mapping
                    if (!isRunning && processes.isNotEmpty()) {
                        // Game exited
                        stopMapping()
                        stopSelf()
                        break
                    }
                }
            }
        }
    }

    private fun stopMapping() {
        _isServiceActive.value = false
        _activeGamePackage.value = null
        exitWatcherJob?.cancel()
        activeInjector?.cleanup()
        activeInjector = null
        crosshairOverlayManager?.hideOverlay()
        crosshairOverlayManager = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMapping()
    }

    private fun buildNotification(gamePkg: String): Notification {
        val stopIntent = Intent(this, MappingForegroundService::class.java).apply {
            action = ACTION_STOP_MAPPING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val panicIntent = Intent(this, MappingForegroundService::class.java).apply {
            action = ACTION_PANIC_KILL
        }
        val panicPendingIntent = PendingIntent.getService(
            this,
            2,
            panicIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Controlyst Active: $gamePkg")
            .setContentText("Floating HUD & Touch Remapper running (Panic Kill ready)")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Panic Kill", panicPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Controlyst Mapping Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active status when Controlyst is mapping inputs for games."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
