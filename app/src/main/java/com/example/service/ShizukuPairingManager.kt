package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ShizukuPairingState(
    val isHelperNotificationActive: Boolean = false,
    val lastEnteredCode: String? = null,
    val isPairingSuccessful: Boolean = false,
    val pairingPort: Int = 5555,
    val statusMessage: String = "Pairing helper idle"
)

object ShizukuPairingManager {
    const val CHANNEL_ID = "shizuku_wireless_pairing"
    const val NOTIFICATION_ID = 2001
    const val ACTION_SUBMIT_PAIRING_CODE = "com.example.action.SUBMIT_SHIZUKU_PAIRING_CODE"
    const val ACTION_STOP_PAIRING_HELPER = "com.example.action.STOP_SHIZUKU_PAIRING_HELPER"
    const val KEY_PAIRING_CODE = "key_shizuku_pairing_code"

    private val _pairingState = MutableStateFlow(ShizukuPairingState())
    val pairingState: StateFlow<ShizukuPairingState> = _pairingState.asStateFlow()

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Shizuku Wireless Pairing Helper",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows inline input box to type Wireless Debugging pairing code without exiting Developer Options"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showPairingNotification(context: Context, customPort: Int = 5555) {
        createNotificationChannel(context)
        _pairingState.value = _pairingState.value.copy(
            isHelperNotificationActive = true,
            pairingPort = customPort,
            statusMessage = "Notification helper active. Open Developer Options to view 6-digit code."
        )

        // Intent to open Developer Options directly
        val devSettingsIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val devSettingsPendingIntent = PendingIntent.getActivity(
            context,
            101,
            devSettingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // RemoteInput for typing the 6-digit pairing code directly in the notification
        val remoteInput = RemoteInput.Builder(KEY_PAIRING_CODE)
            .setLabel("Enter 6-digit code (e.g. 123456)")
            .build()

        // Broadcast intent for submission
        val submitIntent = Intent(context, ShizukuPairingReceiver::class.java).apply {
            action = ACTION_SUBMIT_PAIRING_CODE
            putExtra("port", customPort)
        }
        val submitPendingIntent = PendingIntent.getBroadcast(
            context,
            102,
            submitIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_input_add,
            "Enter Pairing Code",
            submitPendingIntent
        ).addRemoteInput(remoteInput)
            .build()

        // Stop helper intent
        val stopIntent = Intent(context, ShizukuPairingReceiver::class.java).apply {
            action = ACTION_STOP_PAIRING_HELPER
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            103,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Shizuku Wireless Pairing Helper")
            .setContentText("Enter the 6-digit pairing code below without leaving Developer Options!")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "DO NOT leave Developer Options or switch apps — doing so resets the pairing code!\n" +
                    "Pull down this notification shade, tap 'Enter Pairing Code', type the 6 digits and tap Send."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(replyAction)
            .addAction(android.R.drawable.ic_menu_preferences, "Open Dev Options", devSettingsPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", stopPendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun handlePairingCodeReceived(context: Context, code: String, port: Int): Job = scope.launch {
        val cleanCode = code.trim()
        val outcome = when {
            !cleanCode.matches(Regex("[0-9]{6}")) -> PairingOutcome(false, "Pairing code must contain exactly six ASCII digits.")
            port !in 1..65535 -> PairingOutcome(false, "Pairing port must be between 1 and 65535.")
            Build.VERSION.SDK_INT < 30 -> PairingOutcome(false, "Wireless ADB pairing requires Android 11 or later.")
            else -> executeAdbPair(cleanCode, port)
        }
        _pairingState.value = _pairingState.value.copy(
            isPairingSuccessful = outcome.success,
            lastEnteredCode = null,
            isHelperNotificationActive = false,
            statusMessage = outcome.message
        )
        if (!outcome.success) Log.e("NexusPairing", outcome.message)
        updateNotificationStatus(context,
            if (outcome.success) "ADB device paired" else "ADB pairing failed",
            outcome.message, outcome.success)
    }

    internal data class PairingOutcome(val success: Boolean, val message: String)

    internal fun evaluateAdbPair(exitCode: Int, output: String): PairingOutcome {
        val confirmed = exitCode == 0 && output.lineSequence().any {
            it.trim().startsWith("Successfully paired to ")
        }
        return if (confirmed) PairingOutcome(true,
            "ADB pairing confirmed. Start Shizuku in its manager, then grant NEXUS INPUT permission. Pairing does not authorize this app.")
        else PairingOutcome(false, "ADB pairing was not confirmed (exit $exitCode). Use Shizuku's wireless debugging pairing in its manager.")
    }

    private suspend fun executeAdbPair(code: String, port: Int): PairingOutcome = coroutineScope {
        var process: Process? = null
        try {
            // An actual adb executable is required; Android does not bundle adb.
            // Send the secret code through stdin, never a shell command or log.
            val child = ProcessBuilder("adb", "pair", "localhost:$port")
                .redirectErrorStream(true).start()
            process = child
            val output = async(Dispatchers.IO) {
                child.inputStream.bufferedReader().use { reader ->
                    val text = StringBuilder()
                    val buffer = CharArray(1024)
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        if (text.length < 16384) text.append(buffer, 0, minOf(count, 16384 - text.length))
                    }
                    text.toString()
                }
            }
            child.outputStream.bufferedWriter().use { it.write(code + "\n") }
            if (!child.waitFor(8, TimeUnit.SECONDS)) {
                child.destroyForcibly()
                output.cancel()
                PairingOutcome(false, "ADB pairing timed out. Pair using the Shizuku manager.")
            } else evaluateAdbPair(child.exitValue(), output.await())
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            PairingOutcome(false, "ADB pairing unavailable: ${error.javaClass.simpleName}. Pair using the Shizuku manager.")
        } finally {
            process?.destroy()
        }
    }

    fun dismissHelper(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
        _pairingState.value = _pairingState.value.copy(
            isHelperNotificationActive = false,
            statusMessage = "Pairing helper dismissed"
        )
    }

    private fun updateNotificationStatus(context: Context, title: String, message: String, isSuccess: Boolean) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }
}
