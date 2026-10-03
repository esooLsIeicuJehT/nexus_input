package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class CrosshairOverlayService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    companion object {
        private const val TAG = "CrosshairOverlayService"
        private const val CHANNEL_ID = "crosshair_overlay_channel"
        private const val NOTIF_ID = 2002

        const val ACTION_START = "com.example.action.START_CROSSHAIR"
        const val ACTION_STOP = "com.example.action.STOP_CROSSHAIR"
        const val EXTRA_ERROR_STATE = "extra_error_state"
    }

    sealed class OverlayError {
        object PermissionDenied : OverlayError()
        object WindowManagerError : OverlayError()
        object AlreadyRunning : OverlayError()

        override fun toString(): String {
            return when (this) {
                is PermissionDenied -> "SYSTEM_ALERT_WINDOW permission not granted"
                is WindowManagerError -> "WindowManager failed to add or update overlay view"
                is AlreadyRunning -> "Crosshair overlay service is already running"
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopOverlayService()
                return START_NOT_STICKY
            }
            else -> {
                val error = verifyAndStartOverlay()
                if (error != null) {
                    Log.e(TAG, "Failed to start CrosshairOverlayService: $error")
                    broadcastError(error)
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        return START_STICKY
    }

    private fun verifyAndStartOverlay(): OverlayError? {
        // 1. Explicitly verify SYSTEM_ALERT_WINDOW (canDrawOverlays)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Overlay permission (SYSTEM_ALERT_WINDOW) not granted.")
            return OverlayError.PermissionDenied
        }

        if (overlayView != null) {
            return OverlayError.AlreadyRunning
        }

        try {
            // 2. Start Foreground Service requirement for Android O+
            val notification = createForegroundNotification()
            startForeground(NOTIF_ID, notification)

            // 3. Configure WindowManager LayoutParams with secure layering & touch-through flags
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val windowFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or // Touch-through gameplay
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

            layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                windowFlags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                x = 0
                y = 0
            }

            // 4. Create Crosshair Render View
            val crosshairView = CrosshairRenderView(this)
            windowManager.addView(crosshairView, layoutParams)
            overlayView = crosshairView
            Log.i(TAG, "CrosshairOverlayService successfully started via WindowManager.")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Exception adding crosshair view to WindowManager: ${e.message}", e)
            return OverlayError.WindowManagerError
        }
    }

    private fun stopOverlayService() {
        try {
            overlayView?.let {
                windowManager.removeView(it)
                overlayView = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlay view: ${e.message}", e)
        }
        stopForeground(true)
        stopSelf()
    }

    private fun broadcastError(error: OverlayError) {
        val intent = Intent("com.example.CROSSHAIR_ERROR").apply {
            putExtra(EXTRA_ERROR_STATE, error.toString())
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Crosshair Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains persistent floating crosshair overlay during gameplay."
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Controlyst Crosshair Active")
            .setContentText("Floating aiming overlay is currently rendering.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        stopOverlayService()
        super.onDestroy()
    }
}

class CrosshairRenderView(context: Context) : View(context) {
    private val paint = Paint().apply {
        color = Color.parseColor("#00CFEB") // Controlyst Cyan
        strokeWidth = 4f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private val centerPaint = Paint().apply {
        color = Color.parseColor("#7C8CFF") // Controlyst Blue
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(80, 80)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val armLength = 24f
        val gap = 6f

        canvas.drawLine(cx, cy - gap - armLength, cx, cy - gap, paint)
        canvas.drawLine(cx, cy + gap, cx, cy + gap + armLength, paint)
        canvas.drawLine(cx - gap - armLength, cy, cx - gap, cy, paint)
        canvas.drawLine(cx + gap, cy, cx + gap + armLength, cy, paint)

        canvas.drawCircle(cx, cy, 3f, centerPaint)
    }
}
