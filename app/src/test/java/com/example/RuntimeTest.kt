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
    }
}
