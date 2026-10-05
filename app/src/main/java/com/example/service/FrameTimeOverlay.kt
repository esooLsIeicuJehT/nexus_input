package com.example.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.example.frames.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/** FPS/intervals come from the selected game's actual SurfaceFlinger presentation timestamps. */
class FrameTimeOverlay(private val context:Context) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val wm=context.getSystemService(WindowManager::class.java)
    private var view:TextView?=null
    fun start() {
        scope.launch {
            combine(FrameMonitor.settings,MappingRuntimeBridge.state) { settings,runtime -> settings to runtime }
                .collectLatest { (settings,runtime) ->
                    if(!settings.enabled || !runtime.armed || !runtime.targetForeground || !runtime.backendReady) {
                        view?.visibility=View.GONE
                        FrameMonitor.update(FrameMonitor.state.value.copy(stats=null,status=when {
                            !settings.enabled -> "Frame overlay disabled"
                            !runtime.armed -> "Launch a mapped game to observe presented frames"
                            !runtime.targetForeground -> "Waiting for game focus"
                            else -> "Waiting for the active mapping backend"
                        },error=if(settings.enabled && runtime.targetForeground) runtime.error else null))
                        return@collectLatest
                    }
                    val game=runtime.gamePackage ?: return@collectLatest
                    var previousPresent:Long?=null
                    while(isActive) {
                        val observed=withContext(Dispatchers.IO) {
                            runCatching {
                                val source=ControlystAccessibilityService.getInstance()?.currentFrameSource()
                                    ?: error("Capture service has no active frame source")
                                val layers=SurfaceFrameStats.matchingLayers(SurfaceFrameStats.commandOutput(source.readSurfaceLayers().getOrThrow()),game)
                                require(layers.isNotEmpty()) { "SurfaceFlinger exposed no layer for $game" }
                                val saved=FrameMonitor.savedLayer(context,game)
                                val layer=when {
                                    saved!=null -> saved.takeIf { it in layers } ?: error("Saved frame layer is no longer exposed; select an observed layer")
                                    layers.size==1 -> layers.single()
                                    else -> null
                                }
                                if(layer==null) FrameMonitorState(game,layers=layers,status="Select one of the observed game layers in System → Frame overlay")
                                else {
                                    val stats=SurfaceFrameStats.parse(SurfaceFrameStats.commandOutput(source.readSurfaceLatency(layer).getOrThrow()),System.nanoTime())
                                    if(stats.lastPresentNanos==previousPresent) FrameMonitorState(game,layer,layers,status="No new presented frames; game may be paused")
                                    else { previousPresent=stats.lastPresentNanos;FrameMonitorState(game,layer,layers,stats,"Observed SurfaceFlinger timestamps") }
                                }
                            }.getOrElse { error ->
                                val old=FrameMonitor.state.value.takeIf { it.gamePackage==game }
                                FrameMonitorState(game,old?.layer,old?.layers.orEmpty(),error="Frame data unavailable: ${error.message}")
                            }
                        }
                        FrameMonitor.update(observed)
                        render(observed)
                        delay(1000)
                    }
                }
        }
    }
    private fun render(state:FrameMonitorState) {
        if(!Settings.canDrawOverlays(context)) {
            FrameMonitor.update(state.copy(stats=null,error="Frame overlay permission is missing"));return
        }
        val current=view ?: TextView(context).apply {
            textSize=11f;setPadding(12,8,12,8);setBackgroundColor(0xBB071827.toInt())
            contentDescription="NEXUS INPUT presented-frame measurements"
        }.also { created ->
            val type=if(Build.VERSION.SDK_INT>=26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            val parameters=WindowManager.LayoutParams(-2,-2,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,PixelFormat.TRANSLUCENT)
                .apply { gravity=Gravity.TOP or Gravity.LEFT;y=(62*context.resources.displayMetrics.density).roundToInt();alpha=.7f }
            try { wm.addView(created,parameters);view=created }
            catch(error:Exception) { FrameMonitor.update(state.copy(stats=null,error="Frame window failed: ${error.message}")) }
        }
        if(view==null) return
        current.text=FrameMonitor.label(state)
        current.setTextColor(if(state.stats!=null) 0xFF20D5A4.toInt() else Color.YELLOW)
        current.visibility=View.VISIBLE
    }
    fun hide() {
        scope.cancel()
        view?.let { try { wm.removeView(it) } catch(error:Exception) {
            FrameMonitor.update(FrameMonitor.state.value.copy(stats=null,error="Frame window removal failed: ${error.message}"))
        } };view=null
    }
}
