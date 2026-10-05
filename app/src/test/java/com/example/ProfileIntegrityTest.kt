package com.example
import com.example.model.*
import com.example.input.*
import com.example.data.ControlystRepository
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class ProfileIntegrityTest {
    private fun config(vararg nodes: MappingNode) = MappingConfig(id="test", profileName="Test", gamePackage="com.test.game", buttons=nodes.toList())
    @Test fun aliasesAreTheSamePhysicalInput() {
        val a=MappingNode("a",.3f,.3f,boundKey="LT")
        assertTrue(ProfileValidator.errors(config(a,a.copy(id="b",boundKey="L2"))).any { it.contains("duplicate physical") })
        val d=MappingNode("d",.3f,.3f,boundKey="D_UP")
        assertTrue(ProfileValidator.errors(config(d,d.copy(id="e",boundKey="DPAD_UP"))).any { it.contains("duplicate physical") })
        assertTrue(ProfileValidator.errors(config(a), expectedId="other").isNotEmpty())
        assertTrue(ProfileValidator.errors(config(a), expectedPackage="com.other.game").isNotEmpty())
    }
    @Test fun explicitAndImplicitStickAxesCannotBindTheSamePhysicalAxis() {
        val implicit=MappingNode("ls",.3f,.3f,type=NodeType.JOYSTICK_ZONE,boundKey="LS")
        val explicit=implicit.copy(id="other",axisX=0,axisY=1)
        assertTrue(ProfileValidator.errors(config(implicit,explicit)).any { it.contains("duplicate physical") })
        assertTrue(ProfileValidator.errors(config(implicit,explicit.copy(axisX=1,axisY=11))).any { it.contains("duplicate physical") })
    }
    @Test fun exactSlotsAndInvalidNumbersAreRejected() {
        val a=MappingNode("a",.3f,.3f,touchSlot=2)
        val b=a.copy(id="b",boundKey="B")
        assertTrue(ProfileValidator.errors(config(a,b)).any { it.contains("Duplicate touch slots") })
        assertTrue(ProfileValidator.errors(config(a.copy(xNorm=Float.NaN))).isNotEmpty())
        assertTrue(ProfileValidator.errors(config(a.copy(deadzoneInner=.8f,deadzoneOuter=.5f))).isNotEmpty())
        assertTrue(ProfileValidator.errors(config(a.copy(boundKey="BAD_INPUT"))).isNotEmpty())
        assertTrue(ProfileValidator.errors(config(a.copy(triggerPressThreshold=.3f,triggerReleaseThreshold=.4f))).isNotEmpty())
        val slots=TouchSlotAllocator.assign(config(a,b.copy(touchSlot=null)))
        assertEquals(2,slots["a"]); assertEquals(0,slots["b"])
    }
    @Test fun allRuntimeFieldsSurviveRoomJsonRoundTrip() {
        val original=config(MappingNode("hold",.2f,.3f,boundKey="RT",buttonBehavior=ButtonBehavior.HOLD,
            touchSlot=7,inputKeyCode=105,inputScanCode=308,axisX=0,axisY=1,invertY=true,triggerPressThreshold=.7f,triggerReleaseThreshold=.4f,
            macroActions=listOf(MacroStep(10,"HOLD",.4f,.5f,80),MacroStep(50,"RELEASE",.4f,.5f,80))))
            .copy(preferredBackend=PrivilegeMethod.SHIZUKU, controllerProfileId="saved-controller", joystick=JoystickSettings(.07f,.88f,.8f,false,1.5f),
                camera=CameraSettings(1.7f,.9f,1.4f,6,true,1.2f),tags=listOf("custom"),antiRecoilVerticalPull=.4f)
        assertEquals(original,ControlystRepository.deserializeJsonToConfig(ControlystRepository.serializeConfigToJson(original)))
    }
    @Test fun unsupportedEnumsAndMalformedProfilesDoNotSilentlyBecomeButtons() {
        val text=ControlystRepository.serializeConfigToJson(config(MappingNode("a",.2f,.3f)))
        assertTrue(runCatching { ControlystRepository.deserializeJsonToConfig(text.replace("BUTTON","UNSUPPORTED")) }.isFailure)
        assertTrue(runCatching { ControlystRepository.deserializeJsonToConfig("{}") }.isFailure)
    }
    @Test fun invalidGlobalSettingsAndAmbiguousAxisBindingsAreRejected() {
        val base=config(MappingNode("a",.3f,.4f))
        assertTrue(ProfileValidator.errors(base.copy(camera=CameraSettings(horizontalSensitivity=Float.NaN))).isNotEmpty())
        assertTrue(ProfileValidator.errors(base.copy(crosshair=CrosshairConfig(colorHex="invalid"))).isNotEmpty())
        assertTrue(ProfileValidator.errors(base.copy(buttons=listOf(base.buttons.first().copy(axisX=0,axisY=1)))).any { it.contains("cannot use stick axes") })
        val stick=MappingNode("ls",.3f,.4f,type=NodeType.JOYSTICK_ZONE,boundKey="LS",inputKeyCode=96)
        assertTrue(ProfileValidator.errors(config(stick)).any { it.contains("cannot use key/scan") })
    }
    @Test fun unsupportedLegacyOptionsArePreservedButCannotArmSilently() {
        val base=config(MappingNode("a",.3f,.4f))
        val legacy=base.copy(joystick=base.joystick.copy(sprintLockEnabled=true),antiRecoilEnabled=true,camera=base.camera.copy(mouseDpiScale=2f))
        assertEquals(legacy,ControlystRepository.deserializeJsonToConfig(ControlystRepository.serializeConfigToJson(legacy)))
        assertTrue(ProfileValidator.errors(legacy).isEmpty())
        assertEquals(3,ProfileValidator.runtimeErrors(legacy).size)
        assertFalse(com.example.service.MappingRuntimeBridge.arm(base.gamePackage,legacy))
        assertFalse(com.example.service.MappingRuntimeBridge.state.value.armed)
        assertTrue(com.example.service.MappingRuntimeBridge.state.value.error!!.contains("unsupported"))
        assertTrue(com.example.service.MappingRuntimeBridge.arm(base.gamePackage,base))
        com.example.service.MappingRuntimeBridge.disarm()
    }

}
