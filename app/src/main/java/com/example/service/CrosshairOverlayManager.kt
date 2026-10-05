package com.example.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.model.CrosshairConfig
import com.example.ui.crosshair.drawCustomCrosshair
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow

/** Supplies both owners Compose requires for a window hosted outside an Activity. */
internal class OverlayOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
    init {
        controller.performAttach();controller.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
    fun destroy() { registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE);registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP);registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY) }
}

class CrosshairOverlayManager(private val context: Context) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var overlayView: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var scope: CoroutineScope? = null

    fun showOverlay(configFlow: StateFlow<CrosshairConfig>) {
        if (overlayView != null) return
        if (!Settings.canDrawOverlays(context)) { fail("Crosshair overlay permission is missing");return }
        val lifecycleOwner = OverlayOwner()
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                val config by configFlow.collectAsState()
                Canvas(Modifier.size((config.sizeDp * 3).dp)) { drawCustomCrosshair(config,size.width/2,size.height/2) }
            }
        }
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(-2,-2,type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply { gravity=Gravity.CENTER;alpha=.7f }
        try {
            windowManager.addView(view,params);overlayView=view;owner=lifecycleOwner
            scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate).also { current -> current.launch {
                configFlow.collect { config -> view.visibility=if(config.isEnabled) View.VISIBLE else View.GONE }
            } }
        } catch(error:Exception) { view.disposeComposition();lifecycleOwner.destroy();fail("Crosshair window failed: ${error.message}",error) }
    }
    fun hideOverlay() {
        scope?.cancel();scope=null
        overlayView?.let { view ->
            try { windowManager.removeView(view) } catch(error:Exception) { fail("Crosshair removal failed: ${error.message}",error) }
            view.disposeComposition()
        }
        owner?.destroy();owner=null;overlayView=null
    }
    fun isShowing() = overlayView != null
    private fun fail(message: String, error: Throwable? = null) {
        android.util.Log.e("NexusCrosshair",message,error)
        MappingRuntimeBridge.reportError(message)
    }
}
