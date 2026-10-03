package com.example.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import com.example.model.CrosshairConfig
import com.example.ui.crosshair.drawCustomCrosshair
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

class CrosshairOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null

    fun showOverlay(configFlow: StateFlow<CrosshairConfig>) {
        if (overlayView != null) return

        val view = ComposeView(context).apply {
            val lifecycleOwner = object : androidx.lifecycle.LifecycleOwner {
                private val lifecycleRegistry = LifecycleRegistry(this).apply {
                    handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                    handleLifecycleEvent(Lifecycle.Event.ON_START)
                    handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                }
                override val lifecycle: Lifecycle get() = lifecycleRegistry
            }
            setViewTreeLifecycleOwner(lifecycleOwner)

            setContent {
                val config by configFlow.collectAsState()
                val sizePx = (config.sizeDp * 3f).dp

                Box(
                    modifier = Modifier
                        .size(sizePx)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                params?.let { p ->
                                    p.x += dragAmount.x.roundToInt()
                                    p.y += dragAmount.y.roundToInt()
                                    try {
                                        windowManager.updateViewLayout(overlayView, p)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        drawCustomCrosshair(config, cx, cy)
                    }
                }
            }
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            x = 0
            y = 0
        }

        try {
            windowManager.addView(view, params)
            overlayView = view
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hideOverlay() {
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
            params = null
        }
    }

    fun isShowing(): Boolean = overlayView != null
}
