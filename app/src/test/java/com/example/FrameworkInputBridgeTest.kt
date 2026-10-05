package com.example

import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import com.inputmapper.platform.shizuku.FrameworkInputBridge
import com.inputmapper.platform.shizuku.ShizukuInputUserService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Reflection fixtures exercise bridge contracts only; they do not inject into Android hardware. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FrameworkInputBridgeTest {
    class RecordingManager {
        var accepted = true
        var receivedEvent: InputEvent? = null
        var receivedMode: Int? = null

        fun injectInputEvent(event: InputEvent, mode: Int): Boolean {
            receivedEvent = event
            receivedMode = mode
            return accepted
        }

        companion object {
            @JvmField var instance = RecordingManager()
            @JvmStatic fun getInstance(): RecordingManager = instance
        }
    }

    class RejectingManager {
        fun injectInputEvent(event: InputEvent, mode: Int): Boolean {
            throw SecurityException("fixture permission rejection: event=${event.javaClass.simpleName}, mode=$mode")
        }

        companion object {
            @JvmStatic fun getInstance(): RejectingManager = RejectingManager()
        }
    }

    class MissingInjectionMethod {
        companion object {
            @JvmStatic fun getInstance(): MissingInjectionMethod = MissingInjectionMethod()
        }
    }

    class NullManager {
        fun injectInputEvent(event: InputEvent, mode: Int): Boolean = event is KeyEvent && mode == -1

        companion object {
            @JvmStatic fun getInstance(): NullManager? = null
        }
    }

    class WrongReturnManager {
        fun injectInputEvent(event: InputEvent, mode: Int): String = "fixture wrong return ${event.javaClass.simpleName} $mode"

        companion object {
            @JvmStatic fun getInstance(): WrongReturnManager = WrongReturnManager()
        }
    }

    class FailingInstanceManager {
        fun injectInputEvent(event: InputEvent, mode: Int): Boolean = event is KeyEvent && mode == -1

        companion object {
            @JvmStatic fun getInstance(): FailingInstanceManager = throw IllegalStateException("fixture manager unavailable")
        }
    }

    @Test fun choosesGlobalFromAndroid14WithoutGuessingAnApplicationContext() {
        listOf(24, 29, 30, 32, 33).forEach { sdk ->
            assertEquals("android.hardware.input.InputManager", FrameworkInputBridge.classNameForSdk(sdk))
        }
        listOf(34, 35, 36).forEach { sdk ->
            assertEquals("android.hardware.input.InputManagerGlobal", FrameworkInputBridge.classNameForSdk(sdk))
        }
    }

    @Test fun resolvesOnlyTheSelectedClassAndUsesWaitForResultForTheOriginalEvent() {
        RecordingManager.instance = RecordingManager()
        val requestedNames = mutableListOf<String>()
        val bridge = FrameworkInputBridge.resolve(36) { name ->
            requestedNames += name
            RecordingManager::class.java
        }
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A)

        assertEquals(listOf("android.hardware.input.InputManagerGlobal"), requestedNames)
        assertEquals("InputManagerGlobal", bridge.name)
        assertTrue(bridge.inject(event))
        assertSame(event, RecordingManager.instance.receivedEvent)
        assertEquals(1, FrameworkInputBridge.WAIT_FOR_RESULT)
        assertEquals(FrameworkInputBridge.WAIT_FOR_RESULT, RecordingManager.instance.receivedMode)
    }

    @Test fun frameworkFalseIsReturnedAsFalse() {
        RecordingManager.instance = RecordingManager().apply { accepted = false }
        val bridge = FrameworkInputBridge.resolve(33) { RecordingManager::class.java }
        assertEquals("InputManager", bridge.name)
        assertFalse(bridge.inject(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A)))
    }

    @Test fun reflectionPreservesTheActualSecurityException() {
        val bridge = FrameworkInputBridge.resolve(36) { RejectingManager::class.java }
        val thrown = assertThrows(SecurityException::class.java) {
            bridge.inject(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A))
        }
        assertTrue(thrown.message!!.contains("fixture permission rejection"))
        assertTrue(thrown.message!!.contains("mode=1"))
    }

    @Test fun missingFrameworkClassNeverFallsBackToAnotherClass() {
        val requestedNames = mutableListOf<String>()
        assertThrows(ClassNotFoundException::class.java) {
            FrameworkInputBridge.resolve(36) { name ->
                requestedNames += name
                throw ClassNotFoundException("fixture missing $name")
            }
        }
        assertEquals(listOf("android.hardware.input.InputManagerGlobal"), requestedNames)
    }

    @Test fun missingInjectionMethodAndNullManagerFailExplicitly() {
        assertThrows(NoSuchMethodException::class.java) {
            FrameworkInputBridge.resolve(36) { MissingInjectionMethod::class.java }
        }
        assertThrows(IllegalStateException::class.java) {
            FrameworkInputBridge.resolve(36) { NullManager::class.java }
        }
    }

    @Test fun wrongReturnTypeCannotBecomeAnAcceptedInjection() {
        assertThrows(IllegalStateException::class.java) {
            val bridge = FrameworkInputBridge.resolve(36) { WrongReturnManager::class.java }
            bridge.inject(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A))
        }
    }

    @Test fun reflectionUnwrapsManagerInitializationFailure() {
        val thrown = assertThrows(IllegalStateException::class.java) {
            FrameworkInputBridge.resolve(36) { FailingInstanceManager::class.java }
        }
        assertEquals("fixture manager unavailable", thrown.message)
    }

    @Test fun serviceInitializationFailureIsObservableThroughItsBinderContract() {
        val service = ShizukuInputUserService(Result.failure(ClassNotFoundException("fixture absent input manager")))
        val diagnostic = service.selfTest()
        assertTrue(diagnostic.startsWith("ERROR BRIDGE_INIT sdk=34"))
        assertTrue(diagnostic.contains("ClassNotFoundException: fixture absent input manager"))
        assertFalse(diagnostic.contains("probe=BRIDGE_ONLY"))

        val reply = service.injectKey(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.ACTION_DOWN, 100, 100, 0, 0)
        assertEquals("ERROR ClassNotFoundException: fixture absent input manager", reply)
    }

    @Test fun serviceSelfTestReportsResolutionOnlyAndDoesNotSendAProbeTouch() {
        RecordingManager.instance = RecordingManager()
        val bridge = FrameworkInputBridge.resolve(34) { RecordingManager::class.java }
        val service = ShizukuInputUserService(Result.success(bridge))
        val diagnostic = service.selfTest()

        assertTrue(diagnostic.startsWith("OK uid="))
        assertTrue(diagnostic.contains("bridge=InputManagerGlobal sdk=34"))
        assertTrue(diagnostic.contains("mode=WAIT_FOR_RESULT(1) probe=BRIDGE_ONLY"))
        assertNull(RecordingManager.instance.receivedEvent)
    }

    @Test fun serviceFalseDispatchIsAnErrorForBothTouchAndKey() {
        RecordingManager.instance = RecordingManager().apply { accepted = false }
        val bridge = FrameworkInputBridge.resolve(34) { RecordingManager::class.java }
        val service = ShizukuInputUserService(Result.success(bridge))

        val touchReply = service.injectTouch(MotionEvent.ACTION_DOWN, 100, 100, 1, intArrayOf(0), floatArrayOf(20f), floatArrayOf(30f))
        assertTrue(touchReply.startsWith("ERROR INJECTION_REJECTED InputManagerGlobal.injectInputEvent"))
        assertTrue(touchReply.contains("WAIT_FOR_RESULT returned false"))
        assertEquals(FrameworkInputBridge.WAIT_FOR_RESULT, RecordingManager.instance.receivedMode)

        val keyReply = service.injectKey(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.ACTION_DOWN, 100, 100, 0, 0)
        assertTrue(keyReply.startsWith("ERROR INJECTION_REJECTED InputManagerGlobal.injectInputEvent"))
        assertEquals(FrameworkInputBridge.WAIT_FOR_RESULT, RecordingManager.instance.receivedMode)
    }

    @Test fun servicePreservesFrameworkSecurityFailureInItsTouchAndKeyReplies() {
        val bridge = FrameworkInputBridge.resolve(34) { RejectingManager::class.java }
        val service = ShizukuInputUserService(Result.success(bridge))
        val touchReply = service.injectTouch(MotionEvent.ACTION_DOWN, 100, 100, 1, intArrayOf(0), floatArrayOf(20f), floatArrayOf(30f))
        val keyReply = service.injectKey(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.ACTION_DOWN, 100, 100, 0, 0)

        assertTrue(touchReply.startsWith("ERROR SecurityException: fixture permission rejection:"))
        assertTrue(keyReply.startsWith("ERROR SecurityException: fixture permission rejection:"))
        assertTrue(touchReply.contains("mode=1"))
        assertTrue(keyReply.contains("mode=1"))
        assertFalse(touchReply.contains("InvocationTargetException"))
        assertFalse(keyReply.contains("InvocationTargetException"))
    }
}
