package com.example

import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.example.injector.ShizukuInjector
import com.inputmapper.platform.shizuku.IShizukuInputService
import com.inputmapper.platform.shizuku.ShizukuRuntime
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rikka.shizuku.Shizuku

/** Explicit binder fixtures verify adapter cleanup ownership, never Android privileges. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class ShizukuAdapterTest {
    private class Receiver : IShizukuInputService.Stub() {
        var handshake="ERROR fixture resolver unavailable"
        override fun selfTest()=handshake
        override fun injectTouch(action:Int,downTime:Long,eventTime:Long,pointerCount:Int,pointerIds:IntArray,xs:FloatArray,ys:FloatArray)="ERROR unused fixture touch"
        override fun injectKey(keyCode:Int,action:Int,downTime:Long,eventTime:Long,metaState:Int,repeatCount:Int)="ERROR unused fixture key"
        override fun readSurfaceLayers()="ERROR no fixture frame source"
        override fun readSurfaceLatency(layer:String)="ERROR no fixture frame source"
        override fun destroy()=Unit
    }
    private class Runtime(val receiver:Receiver):ShizukuRuntime {
        var rejectUnbind=false
        var unbinds=0
        override fun pingBinder()=true
        override fun checkSelfPermission()=PackageManager.PERMISSION_GRANTED
        override fun bindUserService(args:Shizuku.UserServiceArgs,connection:ServiceConnection) { connection.onServiceConnected(null,receiver) }
        override fun unbindUserService(args:Shizuku.UserServiceArgs,connection:ServiceConnection,remove:Boolean) {
            unbinds++
            if(rejectUnbind) throw IllegalStateException("Fixture unbind rejected")
        }
    }
    private fun adapter(runtime:Runtime):ShizukuInjector {
        val context=ApplicationProvider.getApplicationContext<Context>()
        return ShizukuInjector(context,{ com.inputmapper.platform.shizuku.ShizukuInjector(it,runtime) },{ true })
    }
    @Test fun failedPreparationAndCleanupRetainsTheResourceUntilAcknowledgedRetry() {
        val receiver=Receiver();val runtime=Runtime(receiver).apply { rejectUnbind=true }
        val injector=adapter(runtime)
        val failure=assertThrows(IllegalStateException::class.java) { injector.prepare() }
        assertTrue(failure.message!!.contains("cleanup remains unconfirmed"))
        assertFalse(injector.prepare())
        assertNull(injector.readinessDetails())
        assertThrows(IllegalStateException::class.java) { injector.cleanup() }
        runtime.rejectUnbind=false
        injector.cleanup()
        receiver.handshake="OK uid=2000 bridge=InputManagerGlobal sdk=34 mode=WAIT_FOR_RESULT(1) probe=BRIDGE_ONLY serviceVersion=4"
        assertTrue(injector.prepare())
        assertTrue(injector.readinessDetails()!!.contains(receiver.handshake))
        assertTrue(injector.readinessDetails()!!.contains("unverified"))
        injector.cleanup()
        assertNull(injector.readinessDetails())
    }
    @Test fun rejectedHandshakeNeverBecomesPreparedWhenCleanupSucceeds() {
        val runtime=Runtime(Receiver());val injector=adapter(runtime)
        assertFalse(injector.prepare())
        assertNull(injector.readinessDetails())
        assertTrue(runtime.unbinds>0)
        injector.cleanup()
    }
}
