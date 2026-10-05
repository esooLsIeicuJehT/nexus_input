package com.example

import android.graphics.PointF
import android.view.InputDevice
import android.view.KeyEvent
import com.example.injector.*
import com.example.input.*
import com.example.model.*
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Recording transport is a unit-test fixture. It does not verify Android injection. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class RuntimeTest {
    private class Recording : InputInjector {
        override val method=PrivilegeMethod.KERNELSU
        val calls=CopyOnWriteArrayList<Triple<String,Int,Pair<Float,Float>>>()
        var failDown=false;var failUp=false
        override fun isAvailable()=true
        override fun injectTap(x:Float,y:Float)=false
        override fun injectDrag(path:List<PointF>,durationMs:Long)=false
        override fun injectKeyEvent(keyCode:Int,action:Int)=false
        override fun beginTouch(pointerId:Int,x:Float,y:Float):Boolean { calls+=Triple("down",pointerId,x to y);return !failDown }
        override fun moveTouch(pointerId:Int,x:Float,y:Float):Boolean { calls+=Triple("move",pointerId,x to y);return true }
        override fun endTouch(pointerId:Int):Boolean { calls+=Triple("up",pointerId,0f to 0f);return !failUp }
        override fun cleanup() {}
    }
    private fun config(vararg nodes:MappingNode)=MappingConfig(id="test",profileName="Test",gamePackage="com.test.game",buttons=nodes.toList())
    private fun key(action:Int,code:Int)=KeyEvent(0,0,action,code,0,0,1,0,0,InputDevice.SOURCE_GAMEPAD)
    private fun axis(raw:Float)=GamepadMappingRuntime.AxisValue(raw,-1f,1f,0f)
    private fun trigger(raw:Float)=GamepadMappingRuntime.AxisValue(raw,0f,1f,0f)
    private fun sample(lx:Float=0f,ly:Float=0f,rx:Float=0f,ry:Float=0f,lt:Float=0f,rt:Float=0f,hx:Float=0f,hy:Float=0f)=
        GamepadMappingRuntime.MotionSnapshot(axis(lx),axis(ly),axis(rx),axis(ry),trigger(lt),trigger(rt),axis(hx),axis(hy))
    @Test fun triggerHoldUsesHysteresisAndDoesNotReleaseUntilBothSourcesRelease() {
        val errors=CopyOnWriteArrayList<String>();val r=GamepadMappingRuntime({1000 to 500},errors::add);val backend=Recording()
        val c=config(MappingNode("rt",.7f,.8f,boundKey="RT",touchSlot=5))
        r.handleMotionSnapshot(sample(rt=.7f),c,backend);r.awaitIdle()
        r.handleKeyEvent(key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_R2),c,backend)
        r.handleMotionSnapshot(sample(rt=.5f),c,backend);r.awaitIdle()
        assertEquals(1,backend.calls.count { it.first=="down" })
        r.handleMotionSnapshot(sample(rt=.1f),c,backend);r.awaitIdle()
        assertEquals(0,backend.calls.count { it.first=="up" })
        r.handleKeyEvent(key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_R2),c,backend);r.awaitIdle()
        assertEquals(1,backend.calls.count { it.first=="up" });assertEquals(5,backend.calls.first().second)
        assertTrue(errors.isEmpty());r.shutdown(backend)
    }
    @Test fun hatDiagonalAndNeutralProduceHeldDpadContacts() {
        val r=GamepadMappingRuntime({1000 to 500},{ fail(it) });val b=Recording()
        val c=config(MappingNode("left",.2f,.3f,boundKey="D_LEFT",buttonBehavior=ButtonBehavior.HOLD),
            MappingNode("up",.3f,.2f,boundKey="DPAD_UP",buttonBehavior=ButtonBehavior.HOLD))
        r.handleMotionSnapshot(sample(hx=-1f,hy=-1f),c,b);r.awaitIdle()
        assertEquals(2,b.calls.count { it.first=="down" })
        r.handleMotionSnapshot(sample(),c,b);r.awaitIdle()
        assertEquals(2,b.calls.count { it.first=="up" });r.shutdown(b)
    }
    @Test fun leftAndRightStickKeepSeparateSlotsAndStayWithinScreen() {
        val r=GamepadMappingRuntime({1000 to 500},{ fail(it) });val b=Recording()
        val c=config(MappingNode("ls",.15f,.75f,.15f,NodeType.JOYSTICK_ZONE,"LS",touchSlot=3),
            MappingNode("rs",.7f,.5f,.2f,NodeType.CAMERA_DRAG,"RS",touchSlot=9,invertY=true))
        r.handleMotionSnapshot(sample(lx=1f,rx=1f,ry=.7f),c,b)
        val deadline=System.nanoTime()+1_000_000_000
        while(b.calls.count { it.first=="move" }<2 && System.nanoTime()<deadline) Thread.sleep(5)
        assertEquals(setOf(3,9),b.calls.filter { it.first=="down" }.map { it.second }.toSet())
        assertTrue(b.calls.filter { it.first=="move" }.all { it.third.first in 0f..999f && it.third.second in 0f..499f })
        assertTrue(r.releaseAll(b));r.shutdown(null)
    }
    @Test fun panicCancelsDelayedMacroAndTurboAndReportsReleaseFailure() {
        val errors=CopyOnWriteArrayList<String>();val r=GamepadMappingRuntime({1000 to 500},errors::add);val b=Recording()
        val macro=MappingNode("m",.3f,.3f,type=NodeType.MACRO,boundKey="A",
            macroActions=listOf(MacroStep(80,"HOLD"),MacroStep(80,"RELEASE")))
        r.handleKeyEvent(key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A),config(macro),b);r.awaitIdle()
        assertTrue(r.releaseAll(b));Thread.sleep(200)
        assertTrue(b.calls.isEmpty())
        val hold=MappingNode("h",.3f,.3f,boundKey="B",buttonBehavior=ButtonBehavior.HOLD)
        r.handleKeyEvent(key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_B),config(hold),b);r.awaitIdle()
        b.failUp=true;assertFalse(r.releaseAll(b));assertTrue(errors.any { it.contains("release failed") })
        b.failUp=false;assertTrue(r.releaseAll(b));r.shutdown(null)
    }
    @Test fun explicitAndroidKeyAndScanCodesOverrideLabelAndFailedDownIsObservable() {
        val errors=CopyOnWriteArrayList<String>();val r=GamepadMappingRuntime({1000 to 500},errors::add);val b=Recording()
        val node=MappingNode("x",1f,1f,boundKey="B",inputKeyCode=KeyEvent.KEYCODE_BUTTON_A,
            buttonBehavior=ButtonBehavior.HOLD,touchSlot=31,inputScanCode=0)
        assertFalse(r.handleKeyEvent(key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_B),config(node),b))
        b.failDown=true;assertTrue(r.handleKeyEvent(key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A),config(node),b));r.awaitIdle()
        assertTrue(errors.any { it.contains("down failed") });assertEquals(999f,b.calls.first().third.first,0f)
        assertEquals(31,b.calls.first().second);r.shutdown(b)
    }
    @Test fun persistentTouchRequirementsExcludeAccessibilityForSticksAndHold() {
        val r=GamepadMappingRuntime({1000 to 500},{})
        assertTrue(r.requiresPersistentTouch(config(MappingNode("a",.2f,.3f,boundKey="RT"))))
        assertFalse(r.requiresPersistentTouch(config(MappingNode("a",.2f,.3f,boundKey="A"))))
        r.shutdown(null)
    }    @Test fun failedTapReleaseRetainsItsSlotForPanicRetryAndMacroCornersStayInBounds() {
        val errors=CopyOnWriteArrayList<String>();val runtime=GamepadMappingRuntime({1000 to 500},errors::add);val backend=Recording()
        val tap=MappingNode("tap",1f,1f,boundKey="A",touchSlot=31)
        backend.failUp=true;runtime.handleKeyEvent(key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A),config(tap),backend)
        val deadline=System.nanoTime()+1_000_000_000
        while(backend.calls.none { it.first=="up" } && System.nanoTime()<deadline) Thread.sleep(5)
        assertTrue(errors.any { it.contains("Tap up failed") })
        backend.failUp=false;assertTrue(runtime.releaseAll(backend))
        assertEquals(2,backend.calls.count { it.first=="up" })
        backend.calls.clear()
        val macro=MappingNode("macro",.2f,.3f,type=NodeType.MACRO,boundKey="B",macroActions=listOf(MacroStep(0,"TAP",1f,1f,20)))
        runtime.handleKeyEvent(key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_B),config(macro),backend)
        val deadline2=System.nanoTime()+1_000_000_000
        while(backend.calls.none { it.first=="down" } && System.nanoTime()<deadline2) Thread.sleep(5)
        assertEquals(999f,backend.calls.first().third.first,0f);assertEquals(499f,backend.calls.first().third.second,0f)
        runtime.shutdown(backend)
    }
    @Test fun turboStopsAfterReleaseAndPanicCancelsItsFuturePulses() {
        val errors=CopyOnWriteArrayList<String>();val runtime=GamepadMappingRuntime({1000 to 500},errors::add);val backend=Recording()
        val turbo=MappingNode("turbo",.3f,.4f,type=NodeType.TURBO,boundKey="A",turboHz=30)
        runtime.handleKeyEvent(key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A),config(turbo),backend)
        val deadline=System.nanoTime()+1_000_000_000
        while(backend.calls.count { it.first=="down" }<3 && System.nanoTime()<deadline) Thread.sleep(5)
        assertTrue(backend.calls.count { it.first=="down" }>=3)
        runtime.handleKeyEvent(key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_A),config(turbo),backend);runtime.awaitIdle()
        val stopped=backend.calls.count { it.first=="down" };Thread.sleep(120)
        assertEquals(stopped,backend.calls.count { it.first=="down" })
        runtime.handleKeyEvent(key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A),config(turbo),backend);runtime.awaitIdle()
        assertTrue(runtime.releaseAll(backend));val panic=backend.calls.size;Thread.sleep(120)
        assertEquals(panic,backend.calls.size);assertTrue(errors.isEmpty());runtime.shutdown(null)
    }
    @Test fun explicitPhysicalKeysOverrideAnalogLabelsAndUnknownScanBindingsRequireUnknownKey() {
        val errors=CopyOnWriteArrayList<String>();val runtime=GamepadMappingRuntime({1000 to 500},errors::add);val backend=Recording()
        val node=MappingNode("explicit",.3f,.4f,boundKey="RT",inputKeyCode=KeyEvent.KEYCODE_BUTTON_A,buttonBehavior=ButtonBehavior.HOLD)
        runtime.handleMotionSnapshot(sample(rt=1f),config(node),backend);runtime.awaitIdle()
        assertTrue(backend.calls.isEmpty())
        val scan=node.copy(inputKeyCode=KeyEvent.KEYCODE_UNKNOWN,inputScanCode=310)
        val unknown=KeyEvent(0,0,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_UNKNOWN,0,0,1,310,0,InputDevice.SOURCE_GAMEPAD)
        assertTrue(runtime.handleKeyEvent(unknown,config(scan),backend));runtime.awaitIdle()
        assertEquals(1,backend.calls.count { it.first=="down" })
        assertFalse(runtime.handleKeyEvent(key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A),config(scan),backend))
        runtime.shutdown(backend)
    }
    @Test fun invalidMotionSamplesFailExplicitlyAndStickNeutralReleasesContacts() {
        val errors=CopyOnWriteArrayList<String>();val runtime=GamepadMappingRuntime({1000 to 500},errors::add);val backend=Recording()
        val c=config(MappingNode("ls",.2f,.7f,.12f,NodeType.JOYSTICK_ZONE,"LS"))
        runtime.handleMotionSnapshot(sample(lx=Float.NaN),c,backend);runtime.awaitIdle()
        assertTrue(errors.any { it.contains("Invalid Android motion") });assertTrue(backend.calls.isEmpty())
        runtime.handleMotionSnapshot(sample(lx=1f),c,backend)
        val deadline=System.nanoTime()+1_000_000_000
        while(backend.calls.none { it.first=="down" } && System.nanoTime()<deadline) Thread.sleep(5)
        runtime.handleMotionSnapshot(sample(),c,backend);runtime.awaitIdle()
        val deadline2=System.nanoTime()+1_000_000_000
        while(backend.calls.none { it.first=="up" } && System.nanoTime()<deadline2) Thread.sleep(5)
        assertTrue(backend.calls.any { it.first=="up" });runtime.shutdown(backend)
    }
    @Test fun savedTriggerHysteresisControlsTheActualHoldLifecycle() {
        val runtime=GamepadMappingRuntime({1000 to 500},{fail(it)});val backend=Recording()
        val c=config(MappingNode("calibrated",.3f,.4f,boundKey="RT",triggerPressThreshold=.8f,triggerReleaseThreshold=.6f))
        runtime.handleMotionSnapshot(sample(rt=.7f),c,backend);runtime.awaitIdle();assertTrue(backend.calls.isEmpty())
        runtime.handleMotionSnapshot(sample(rt=.9f),c,backend);runtime.awaitIdle();assertEquals(1,backend.calls.count { it.first=="down" })
        runtime.handleMotionSnapshot(sample(rt=.7f),c,backend);runtime.awaitIdle();assertEquals(0,backend.calls.count { it.first=="up" })
        runtime.handleMotionSnapshot(sample(rt=.5f),c,backend);runtime.awaitIdle();assertEquals(1,backend.calls.count { it.first=="up" })
        runtime.shutdown(null)
    }

    @Test fun joystickCurveAndCameraSensitivitySmoothingChangeInjectedCoordinates() {
        fun firstMove(profile: MappingConfig,snapshot: GamepadMappingRuntime.MotionSnapshot): Pair<Float,Float> {
            val runtime=GamepadMappingRuntime({1000 to 500},{fail(it)});val backend=Recording()
            try {
                runtime.handleMotionSnapshot(snapshot,profile,backend)
                val deadline=System.nanoTime()+1_000_000_000
                while(backend.calls.none { it.first=="move" } && System.nanoTime()<deadline) Thread.sleep(5)
                return backend.calls.first { it.first=="move" }.third
            } finally { runtime.shutdown(backend) }
        }
        val ls=config(MappingNode("ls",.2f,.7f,.2f,NodeType.JOYSTICK_ZONE,"LS",deadzoneInner=0f,deadzoneOuter=1f))
            .copy(joystick=JoystickSettings(curveExponent=2f))
        assertEquals(224.8f,firstMove(ls,sample(lx=.5f)).first,.01f)
        val rs=config(MappingNode("rs",.5f,.5f,.2f,NodeType.CAMERA_DRAG,"RS",deadzoneInner=0f,deadzoneOuter=1f))
            .copy(camera=CameraSettings(2f,.5f,2f,2))
        val position=firstMove(rs,sample(rx=.5f,ry=.5f))
        assertEquals(505f,position.first,.01f);assertEquals(250.875f,position.second,.01f)
    }
    @Test fun aMacroCannotRetriggerWhileItsPreviousStepsArePending() {
        val errors=CopyOnWriteArrayList<String>();val runtime=GamepadMappingRuntime({1000 to 500},errors::add);val backend=Recording()
        val profile=config(MappingNode("macro",.2f,.3f,type=NodeType.MACRO,boundKey="A",macroActions=listOf(MacroStep(500,"TAP"))))
        try {
            runtime.handleKeyEvent(key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A),profile,backend)
            runtime.handleKeyEvent(key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_A),profile,backend)
            runtime.handleKeyEvent(key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A),profile,backend)
            runtime.awaitIdle()
            assertTrue(errors.any { it.contains("already running") });assertTrue(backend.calls.isEmpty())
            assertTrue(runtime.releaseAll(backend))
        } finally { runtime.shutdown(null) }
    }

    @Test fun pointerIdsUpTo31AreAllowedButContactCountNeverExceedsAndroidLimit() {
        val errors=CopyOnWriteArrayList<String>();val runtime=GamepadMappingRuntime({1000 to 500},errors::add);val backend=Recording()
        val nodes=(0 until 17).map { id -> MappingNode("h$id",.2f,.3f,boundKey="A",inputKeyCode=KeyEvent.KEYCODE_BUTTON_A+id,buttonBehavior=ButtonBehavior.HOLD,touchSlot=if(id==0)31 else id-1) }
        val profile=config(*nodes.toTypedArray())
        try {
            nodes.forEach { runtime.handleKeyEvent(key(KeyEvent.ACTION_DOWN,it.inputKeyCode!!),profile,backend) }
            runtime.awaitIdle();assertEquals(16,backend.calls.count { it.first=="down" })
            assertTrue(backend.calls.any { it.first=="down" && it.second==31 })
            assertTrue(errors.any { it.contains("16 simultaneous") });assertTrue(runtime.releaseAll(backend))
            assertEquals(16,backend.calls.count { it.first=="up" })
        } finally { runtime.shutdown(null) }
    }

    @Test fun swipeUsesRealContactMovesAndPanicCancelsTheRemainingTrajectory() {
        val errors=CopyOnWriteArrayList<String>();val runtime=GamepadMappingRuntime({1000 to 500},errors::add);val backend=Recording()
        val swipe=config(MappingNode("swipe",.2f,.3f,type=NodeType.MACRO,boundKey="A",macroActions=listOf(MacroStep(0,"SWIPE",.2f,.3f,100,.8f,.6f))))
        try {
            runtime.handleKeyEvent(key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A),swipe,backend)
            var deadline=System.nanoTime()+1_000_000_000
            while(backend.calls.none { it.first=="up" } && System.nanoTime()<deadline) Thread.sleep(5)
            assertEquals(1,backend.calls.count { it.first=="down" });assertTrue(backend.calls.count { it.first=="move" }>=2)
            val last=backend.calls.last { it.first=="move" }.third
            assertEquals(799.2f,last.first,.01f);assertEquals(299.4f,last.second,.01f)
            backend.calls.clear()
            val longSwipe=swipe.copy(buttons=listOf(swipe.buttons.single().copy(macroActions=listOf(swipe.buttons.single().macroActions.single().copy(durationMs=1000)))))
            runtime.handleKeyEvent(key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_A),longSwipe,backend)
            runtime.handleKeyEvent(key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A),longSwipe,backend)
            deadline=System.nanoTime()+1_000_000_000
            while(backend.calls.none { it.first=="down" } && System.nanoTime()<deadline) Thread.sleep(5)
            assertTrue(runtime.releaseAll(backend));val stopped=backend.calls.size;Thread.sleep(80)
            assertEquals(stopped,backend.calls.size);assertTrue(errors.isEmpty())
        } finally { runtime.shutdown(null) }
    }

}
