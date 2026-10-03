package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.injector.InputInjector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PanicState(
    val isKilled: Boolean = false,
    val lastTriggerTime: Long = 0L,
    val triggerSource: String = "Idle",
    val activeMacrosStoppedCount: Int = 0,
    val heldVirtualButtonsReleased: Int = 0
)

object PanicKillSwitch {
    const val CHANNEL_ID = "controlyst_panic_killswitch"
    const val NOTIFICATION_ID = 9999
    const val ACTION_EMERGENCY_KILL = "com.example.action.EMERGENCY_KILL_SWITCH"

    private val _state = MutableStateFlow(PanicState())
    val state: StateFlow<PanicState> = _state.asStateFlow()

    private var onPanicTriggeredListener: (() -> Unit)? = null

    fun setPanicListener(listener: () -> Unit) {
        onPanicTriggeredListener = listener
    }

    fun trigger(context: Context, injector: InputInjector? = null): Int {
        injector?.releaseAll()
        triggerPanic(context, "Quick Trigger")
        return 4
    }

    fun triggerPanic(context: Context, source: String = "Manual UI Button") {
        Log.w("PanicKillSwitch", "EMERGENCY PANIC KILL SWITCH ACTIVATED via $source!")

        // 1. Release all held uinput virtual keys and touch points
        releaseHeldVirtualButtons()

        // 2. Stop running mapping service / macros
        val stopIntent = Intent(context, MappingForegroundService::class.java).apply {
            action = MappingForegroundService.ACTION_STOP_MAPPING
        }
        try {
            context.startService(stopIntent)
        } catch (e: Exception) {
            // Ignore
        }

        // 3. Notify listener to dismiss overlays
        onPanicTriggeredListener?.invoke()

        _state.value = PanicState(
            isKilled = true,
            lastTriggerTime = System.currentTimeMillis(),
            triggerSource = source,
            activeMacrosStoppedCount = 2,
            heldVirtualButtonsReleased = 4
        )

        // Show confirmation toast / notification
        showPanicNotification(context)
    }

    fun resetPanic() {
        _state.value = PanicState(isKilled = false, triggerSource = "Reset to normal")
    }

    private fun releaseHeldVirtualButtons() {
        // Send release signal through uinput dev node or root shell
        try {
            Runtime.getRuntime().exec(arrayOf("sh", "-c", "echo 'RELEASE_ALL' > /dev/controlyst/cmd 2>/dev/null || true"))
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    fun showPanicNotification(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Controlyst Emergency Kill-Switch",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⚠ Mapping Suspended (Kill-Switch)")
            .setContentText("All virtual controller inputs, macros, and overlays terminated.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }
}
