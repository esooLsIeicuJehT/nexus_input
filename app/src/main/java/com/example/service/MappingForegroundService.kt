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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.ControlystDatabase
import com.example.data.ControlystRepository
import com.example.model.CrosshairConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MappingForegroundService : Service() {

    companion object {
        private const val TAG = "NexusMappingService"

        const val CHANNEL_ID = "nexus_mapping_service_channel"
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

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Default)
    private var exitWatcherJob: Job? = null
    private var crosshairOverlayManager: CrosshairOverlayManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MAPPING -> {
                val gamePkg = intent.getStringExtra(EXTRA_GAME_PACKAGE)
                val configId = intent.getStringExtra(EXTRA_CONFIG_ID)
                if (gamePkg.isNullOrBlank() || configId.isNullOrBlank()) {
                    failStart("Mapping start rejected: game package and config ID are required")
                } else {
                    startMapping(gamePkg, configId)
                }
            }
            ACTION_STOP_MAPPING -> {
                stopMapping()
                stopSelf()
            }
            ACTION_PANIC_KILL -> {
                _isOverlayVisible.value = false
                stopMapping()
                stopSelf()
            }
            else -> {
                failStart("Mapping service received an unsupported or missing action: ${intent?.action}")
            }
        }
        return START_NOT_STICKY
    }

    private fun startMapping(gamePkg: String, configId: String) {
        stopMapping(clearNotification = false)

        _isServiceActive.value = true
        _activeGamePackage.value = gamePkg
        _isOverlayVisible.value = true

        startForeground(NOTIFICATION_ID, buildNotification(gamePkg, "Loading mapping profile…"))

        serviceScope.launch(Dispatchers.IO) {
            val db = ControlystDatabase.getDatabase(applicationContext)
            val entity = runCatching { db.configProfileDao().getProfileById(configId) }
                .getOrElse { error ->
                    failLoadedProfile("Unable to load profile $configId: ${error.javaClass.simpleName}: ${error.message}")
                    return@launch
                }

            if (entity == null) {
                failLoadedProfile(
                    "Mapping profile '$configId' was not found. Save the mapper profile before launching the game."
                )
                return@launch
            }

            val config = runCatching { ControlystRepository.deserializeJsonToConfig(entity.jsonBlob) }
                .getOrElse { error ->
                    failLoadedProfile("Profile '$configId' is invalid: ${error.javaClass.simpleName}: ${error.message}")
                    return@launch
                }

            if (config.gamePackage.isNotBlank() && config.gamePackage != gamePkg) {
                failLoadedProfile(
                    "Profile '${config.profileName}' targets ${config.gamePackage}, not $gamePkg"
                )
                return@launch
            }

            currentCrosshairConfig.value = config.crosshair
            MappingRuntimeBridge.arm(gamePkg, config)

            withContext(Dispatchers.Main) {
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(
                    NOTIFICATION_ID,
                    buildNotification(gamePkg, "${config.profileName} armed • waiting for game focus")
                )

                if (android.provider.Settings.canDrawOverlays(this@MappingForegroundService)) {
                    crosshairOverlayManager?.hideOverlay()
                    crosshairOverlayManager = CrosshairOverlayManager(this@MappingForegroundService).apply {
                        showOverlay(currentCrosshairConfig)
                    }
                }
            }

            startExitWatcher(gamePkg)
        }
    }

    private fun startExitWatcher(gamePkg: String) {
        exitWatcherJob?.cancel()
        exitWatcherJob = serviceScope.launch {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            while (_isServiceActive.value) {
                delay(4_000)
                val processes = runCatching { am?.runningAppProcesses }.getOrNull().orEmpty()
                if (processes.isEmpty()) continue
                val isRunning = processes.any {
                    it.processName == gamePkg || it.pkgList?.contains(gamePkg) == true
                }
                if (!isRunning) {
                    Log.i(TAG, "Target process $gamePkg is no longer visible; stopping mapper")
                    stopMapping()
                    stopSelf()
                    break
                }
            }
        }
    }

    private suspend fun failLoadedProfile(message: String) {
        Log.e(TAG, message)
        MappingRuntimeBridge.disarm(message)
        _isServiceActive.value = false
        _activeGamePackage.value = null
        withContext(Dispatchers.Main) {
            getSystemService(NotificationManager::class.java).notify(
                NOTIFICATION_ID,
                buildNotification("NEXUS INPUT", message)
            )
            stopSelf()
        }
    }

    private fun failStart(message: String) {
        Log.e(TAG, message)
        MappingRuntimeBridge.disarm(message)
        _isServiceActive.value = false
        _activeGamePackage.value = null
        stopSelf()
    }

    private fun stopMapping(clearNotification: Boolean = true) {
        _isServiceActive.value = false
        _activeGamePackage.value = null
        exitWatcherJob?.cancel()
        exitWatcherJob = null
        MappingRuntimeBridge.disarm()
        crosshairOverlayManager?.hideOverlay()
        crosshairOverlayManager = null
        if (clearNotification) {
            getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        }
    }

    override fun onDestroy() {
        stopMapping()
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun buildNotification(gamePkg: String, status: String): Notification {
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

        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NEXUS INPUT • $gamePkg")
            .setContentText(status)
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
                "NEXUS INPUT Mapping",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the active NEXUS INPUT game mapping runtime."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
