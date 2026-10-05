package com.example

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.inputmapper.platform.core.InjectionResult
import com.inputmapper.platform.shizuku.IShizukuInputService
import com.inputmapper.platform.shizuku.ShizukuInjector
import com.inputmapper.platform.shizuku.ShizukuRuntime
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rikka.shizuku.Shizuku

/** Explicit local binder/runtime fixtures test lifecycle logic, not privilege or hardware injection. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShizukuConnectionTest {
    private class Receiver : IShizukuInputService.Stub() {
        var handshake = "OK uid=2000 bridge=InputManagerGlobal sdk=34 mode=WAIT_FOR_RESULT(1) probe=BRIDGE_ONLY serviceVersion=4"
        var alive = true
        var rejectUp = false
        var selfTestCalls = 0
        var keyCalls = 0
        val touchActions = mutableListOf<Int>()

        override fun isBinderAlive(): Boolean = alive
        override fun pingBinder(): Boolean = alive
        override fun selfTest(): String {
            selfTestCalls++
            return handshake
        }

        override fun injectTouch(action: Int, downTime: Long, eventTime: Long, pointerCount: Int, pointerIds: IntArray, xs: FloatArray, ys: FloatArray): String {
            touchActions += action
            val masked = action and MotionEvent.ACTION_MASK
            return if (rejectUp && masked in setOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP)) {
                "ERROR INJECTION_REJECTED fixture UP failure"
            } else "OK"
        }

        override fun injectKey(keyCode: Int, action: Int, downTime: Long, eventTime: Long, metaState: Int, repeatCount: Int): String {
            keyCalls++
            return "ERROR unused fixture operation"
        }

        override fun readSurfaceLayers(): String = "ERROR fixture has no frame source"
        override fun readSurfaceLatency(layer: String): String = "ERROR fixture has no frame source"
        override fun destroy() = Unit
    }

    private class Runtime(var receiver: Receiver) : ShizukuRuntime {
        var deliverImmediately = true
        var running = true
        var permission = PackageManager.PERMISSION_GRANTED
        var bindCalls = 0
        var unbindCalls = 0
        val connections = mutableListOf<ServiceConnection>()

        override fun pingBinder(): Boolean = running
        override fun checkSelfPermission(): Int = permission
        override fun bindUserService(args: Shizuku.UserServiceArgs, connection: ServiceConnection) {
            bindCalls++
            connections += connection
            if (deliverImmediately) connection.onServiceConnected(null, receiver)
        }

        override fun unbindUserService(args: Shizuku.UserServiceArgs, connection: ServiceConnection, remove: Boolean) {
            unbindCalls++
        }
    }

    private fun client(runtime: Runtime): ShizukuInjector =
        ShizukuInjector(ApplicationProvider.getApplicationContext<Context>(), runtime)

    @Test fun nonPrivilegedOrErrorHandshakeIsDiscardedAndCannotCacheSuccess() {
        listOf(
            "OK uid=10089 bridge=InputManagerGlobal sdk=34 mode=WAIT_FOR_RESULT(1) probe=BRIDGE_ONLY serviceVersion=4",
            "ERROR SecurityException fixture bridge unavailable"
        ).forEach { rejected ->
            val receiver = Receiver().apply { handshake = rejected }
            val runtime = Runtime(receiver)
            val injector = client(runtime)

            assertTrue(injector.connect(100) is InjectionResult.Failure)
            assertNull(injector.connectionDetails)
            assertTrue(injector.beginTouch(0, 30f, 40f) is InjectionResult.Failure)
            assertTrue(receiver.touchActions.isEmpty())

            receiver.handshake = "OK uid=2000 bridge=InputManagerGlobal sdk=34 mode=WAIT_FOR_RESULT(1) probe=BRIDGE_ONLY serviceVersion=4"
            assertEquals(InjectionResult.Success, injector.connect(100))
            assertEquals(receiver.handshake, injector.connectionDetails)
            assertEquals(2, receiver.selfTestCalls)
            assertEquals(2, runtime.bindCalls)
            assertEquals(InjectionResult.Success, injector.cleanup())
        }
    }

    @Test fun cachedBinderRechecksHandshakeAndClearsPreviouslyGreenDetails() {
        val receiver = Receiver()
        val runtime = Runtime(receiver)
        val injector = client(runtime)
        assertEquals(InjectionResult.Success, injector.connect(100))
        assertEquals(receiver.handshake, injector.connectionDetails)

        receiver.handshake = "ERROR fixture bridge no longer resolves"
        assertTrue(injector.connect(100) is InjectionResult.Failure)
        assertEquals(2, receiver.selfTestCalls)
        assertNull(injector.connectionDetails)
        assertTrue(injector.beginTouch(0, 30f, 40f) is InjectionResult.Failure)
        assertTrue(receiver.touchActions.isEmpty())
    }

    @Test fun staleVersionWrongBridgeOrAsyncModeCannotPassInitialization() {
        val good="OK uid=2000 bridge=InputManagerGlobal sdk=34 mode=WAIT_FOR_RESULT(1) probe=BRIDGE_ONLY serviceVersion=4"
        listOf(good.replace("serviceVersion=4","serviceVersion=3"),
            good.replace("bridge=InputManagerGlobal","bridge=InputManager"),
            good.replace("sdk=34","sdk=33"), good.replace("mode=WAIT_FOR_RESULT(1)","mode=ASYNC(0)")
        ).forEach { stale ->
            val receiver=Receiver().apply { handshake=stale }
            val injector=client(Runtime(receiver))
            assertTrue(injector.connect(100) is InjectionResult.Failure)
            assertNull(injector.connectionDetails)
            assertTrue(receiver.touchActions.isEmpty())
            assertEquals(InjectionResult.Success,injector.cleanup())
        }
    }

    @Test fun serviceLossWithUnconfirmedContactsCannotSilentlyReconnectIntoANewGesture() {
        val runtime=Runtime(Receiver());val injector=client(runtime)
        assertEquals(InjectionResult.Success,injector.connect(100))
        assertEquals(InjectionResult.Success,injector.beginTouch(1,30f,40f))
        runtime.connections.single().onServiceDisconnected(null)
        val failure=injector.connect(100)
        assertTrue(failure is InjectionResult.Failure)
        assertTrue((failure as InjectionResult.Failure).message.contains("Unconfirmed contacts"))
        assertEquals(1,runtime.bindCalls)
        assertTrue(injector.cleanup() is InjectionResult.Failure)
    }

    @Test fun deadBinderCannotReuseAnEarlierSuccessfulHandshake() {
        val receiver = Receiver()
        val runtime = Runtime(receiver)
        val injector = client(runtime)
        assertEquals(InjectionResult.Success, injector.connect(100))
        receiver.alive = false

        assertTrue(injector.connect(100) is InjectionResult.Failure)
        assertNull(injector.connectionDetails)
        assertTrue(injector.beginTouch(0, 30f, 40f) is InjectionResult.Failure)
        assertTrue(receiver.touchActions.isEmpty())
    }

    @Test fun timeoutLateBinderCannotResurrectAnUnusableConnection() {
        val receiver = Receiver()
        val runtime = Runtime(receiver).apply { deliverImmediately = false }
        val injector = client(runtime)
        assertTrue(injector.connect(0) is InjectionResult.Failure)
        assertNull(injector.connectionDetails)
        val expiredConnection = runtime.connections.single()

        expiredConnection.onServiceConnected(null, receiver)
        assertNull(injector.connectionDetails)
        assertTrue(injector.beginTouch(0, 30f, 40f) is InjectionResult.Failure)
        assertTrue(receiver.touchActions.isEmpty())
        assertTrue("The expired connection must be unbound", runtime.unbindCalls > 0)

        runtime.deliverImmediately = true
        assertEquals(InjectionResult.Success, injector.connect(100))
        assertEquals(receiver.handshake, injector.connectionDetails)
        assertEquals(2, runtime.bindCalls)
        assertEquals(InjectionResult.Success, injector.cleanup())
    }

    @Test fun failedUpPreservesTheConnectedServiceForPanicRetry() {
        val receiver = Receiver()
        val runtime = Runtime(receiver)
        val injector = client(runtime)
        assertEquals(InjectionResult.Success, injector.connect(100))
        assertEquals(InjectionResult.Success, injector.beginTouch(31, 30f, 40f))
        receiver.rejectUp = true

        assertTrue(injector.cleanup() is InjectionResult.Failure)
        assertEquals("Failed release must not tear down its only retry transport", 0, runtime.unbindCalls)
        receiver.rejectUp = false
        assertEquals(InjectionResult.Success, injector.endTouch(31))
        assertEquals(InjectionResult.Success, injector.cleanup())
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_UP), receiver.touchActions)
        assertEquals(1, runtime.unbindCalls)
        assertNull(injector.connectionDetails)
    }

    @Test fun permissionLossOrDisconnectionClearsCachedBridgeEvidence() {
        val receiver = Receiver()
        val runtime = Runtime(receiver)
        val injector = client(runtime)
        assertEquals(InjectionResult.Success, injector.connect(100))
        runtime.connections.single().onServiceDisconnected(ComponentName("fixture.package", "fixture.Service"))
        assertNull(injector.connectionDetails)
        assertTrue(injector.beginTouch(0, 30f, 40f) is InjectionResult.Failure)

        assertEquals(InjectionResult.Success, injector.connect(100))
        runtime.permission = PackageManager.PERMISSION_DENIED
        assertTrue(injector.connect(100) is InjectionResult.Failure)
        assertNull(injector.connectionDetails)
        assertEquals(InjectionResult.Success, injector.cleanup())
    }
}
