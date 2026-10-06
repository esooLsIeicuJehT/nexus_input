package com.inputmapper.platform.root

import android.content.Context
import com.inputmapper.platform.core.InjectionResult
import com.inputmapper.platform.core.InputInjector
import com.inputmapper.platform.core.TimedTouchPoint

class KernelSUInjector(
    context: Context,
    width: Int,
    height: Int,
    maxSlots: Int = 10
) : InputInjector {
    private val delegate = RootUinputInjector(context, width, height, maxSlots, "KernelSU/InputManager", useInputManager = true)
    override val backendName: String get() = delegate.backendName
    fun connect(): InjectionResult = delegate.connect()
    fun readSurfaceLayers() = delegate.readSurfaceLayers()
    fun readSurfaceLatency(layer: String) = delegate.readSurfaceLatency(layer)
    fun health(): String = delegate.health()
    override fun injectTap(x: Float, y: Float) = delegate.injectTap(x, y)
    override fun injectDrag(path: List<TimedTouchPoint>, durationMillis: Long) = delegate.injectDrag(path, durationMillis)
    override fun injectKeyEvent(keyCode: Int, action: Int) = delegate.injectKeyEvent(keyCode, action)
    override fun beginTouch(pointerId: Int, x: Float, y: Float) = delegate.beginTouch(pointerId, x, y)
    override fun moveTouch(pointerId: Int, x: Float, y: Float) = delegate.moveTouch(pointerId, x, y)
    override fun endTouch(pointerId: Int) = delegate.endTouch(pointerId)
    override fun publishRuntimeState(state: String) = delegate.publishRuntimeState(state)
    override fun cleanup() = delegate.cleanup()
}
