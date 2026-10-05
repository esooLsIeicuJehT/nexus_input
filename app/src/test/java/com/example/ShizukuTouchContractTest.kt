package com.example

import android.content.Context
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.inputmapper.platform.core.InjectionResult
import com.inputmapper.platform.shizuku.IShizukuInputService
import com.inputmapper.platform.shizuku.ShizukuInjector
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Binder receiver fixture validates payload construction, not Shizuku permissions/hardware. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class ShizukuTouchContractTest {
    private class Receiver : IShizukuInputService.Stub() {
        data class Event(val action:Int,val ids:List<Int>,val xs:List<Float>)
        val events=mutableListOf<Event>();var rejectUp=false
        override fun injectTouch(action:Int,downTime:Long,eventTime:Long,pointerCount:Int,pointerIds:IntArray,xs:FloatArray,ys:FloatArray):String {
            events+=Event(action,pointerIds.toList(),xs.toList())
            return if(rejectUp && action and MotionEvent.ACTION_MASK in setOf(MotionEvent.ACTION_UP,MotionEvent.ACTION_POINTER_UP)) "ERROR INJECTION_REJECTED fixture rejection" else "OK"
        }
        override fun injectKey(keyCode:Int,action:Int,downTime:Long,eventTime:Long,metaState:Int,repeatCount:Int)="ERROR unused fixture operation"
        override fun selfTest()="ERROR fixture is not a privileged service"
        override fun readSurfaceLayers()="ERROR fixture has no frame source"
        override fun readSurfaceLatency(layer:String)="ERROR fixture has no frame source"
        override fun destroy()=Unit
    }
    private fun client(receiver:Receiver)=ShizukuInjector(ApplicationProvider.getApplicationContext<Context>()).also {
        it.javaClass.getDeclaredField("remote").apply { isAccessible=true }.set(it,receiver)
    }
    @Test fun multiTouchUsesAndroidPointerIndexesAndPreservesOtherContacts() {
        val receiver=Receiver();val injector=client(receiver)
        assertEquals(InjectionResult.Success,injector.beginTouch(31,20f,30f))
        assertEquals(InjectionResult.Success,injector.beginTouch(5,40f,50f))
        assertEquals(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),receiver.events.last().action)
        assertEquals(listOf(31,5),receiver.events.last().ids)
        assertEquals(InjectionResult.Success,injector.moveTouch(31,60f,70f));assertEquals(listOf(60f,40f),receiver.events.last().xs)
        assertEquals(InjectionResult.Success,injector.endTouch(31))
        assertEquals(MotionEvent.ACTION_POINTER_UP,receiver.events.last().action)
        assertEquals(InjectionResult.Success,injector.endTouch(5));assertEquals(MotionEvent.ACTION_UP,receiver.events.last().action)
    }
    @Test fun rejectsDuplicateIdsTooManyContactsAndNeverReportsFailedCleanupAsSuccess() {
        val receiver=Receiver();val injector=client(receiver)
        (0 until 16).forEach { assertEquals(InjectionResult.Success,injector.beginTouch(it,20f,30f)) }
        val count=receiver.events.size
        assertTrue(injector.beginTouch(0,20f,30f) is InjectionResult.Failure)
        assertTrue(injector.beginTouch(31,20f,30f) is InjectionResult.Failure);assertEquals(count,receiver.events.size)
        receiver.rejectUp=true;assertTrue(injector.cleanup() is InjectionResult.Failure)
        receiver.rejectUp=false;assertEquals(InjectionResult.Success,injector.cleanup())
    }
}
