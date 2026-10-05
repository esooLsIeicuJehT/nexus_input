package com.example.frames

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FrameOverlaySettings(val enabled: Boolean=false,val refreshToken: Long=0)
data class FrameMonitorState(val gamePackage: String?=null,val layer: String?=null,val layers: List<String> = emptyList(),
    val stats: PresentedFrameStats?=null,val status: String="Frame overlay disabled",val error: String?=null)

object FrameMonitor {
    private val _settings=MutableStateFlow(FrameOverlaySettings())
    val settings=_settings.asStateFlow()
    private val _state=MutableStateFlow(FrameMonitorState())
    val state=_state.asStateFlow()
    private fun preferences(context:Context)=context.getSharedPreferences("nexus_input_preferences",Context.MODE_PRIVATE)
    fun initialize(context:Context) { _settings.value=FrameOverlaySettings(preferences(context).getBoolean("frame_overlay_enabled",false)) }
    fun setEnabled(context:Context,enabled:Boolean) {
        check(preferences(context).edit().putBoolean("frame_overlay_enabled",enabled).commit()) { "Frame-overlay preference could not be saved" }
        _settings.value=_settings.value.copy(enabled=enabled)
    }
    fun savedLayer(context:Context,gamePackage:String):String?=preferences(context).getString("frame_layer:$gamePackage",null)
    fun chooseLayer(context:Context,layer:String) {
        val current=_state.value
        require(layer in current.layers && current.gamePackage!=null) { "Select an actually observed game layer" }
        check(preferences(context).edit().putString("frame_layer:${current.gamePackage}",layer).commit()) { "Layer preference could not be saved" }
        refresh()
    }
    fun refresh() { _settings.value=_settings.value.copy(refreshToken=_settings.value.refreshToken+1) }
    fun update(state:FrameMonitorState) {
        if(state.error!=null && state.error!=_state.value.error) android.util.Log.e("NexusFrames",state.error)
        _state.value=state
    }
    fun label(state:FrameMonitorState):String=state.stats?.let {
        String.format(java.util.Locale.ROOT,"Presented %.1f FPS · %.2f ms\np95 %.2f ms · %d intervals",it.fps,it.meanIntervalMs,it.p95IntervalMs,it.sampleIntervals)
    } ?: state.error ?: state.status
}
