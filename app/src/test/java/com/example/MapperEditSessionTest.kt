package com.example

import com.example.input.MapperEditSession
import com.example.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class MapperEditSessionTest {
    private fun config()=MappingConfig(id="game",profileName="Game",gamePackage="com.test.game",preferredBackend=PrivilegeMethod.SHIZUKU,
        buttons=listOf(MappingNode("a",.2f,.3f,boundKey="A",touchSlot=7)))
    @Test fun editsAreDetachedAndUseExactScreenBoundsAcrossOrientations() {
        val original=config();val edit=MapperEditSession(original)
        edit.move("a",999f,499f,1000,500)
        assertEquals(1f,edit.draft.buttons[0].xNorm,0f);assertEquals(1f,edit.draft.buttons[0].yNorm,0f)
        assertEquals(.2f,original.buttons[0].xNorm,0f)
        edit.move("a",-40f,999f,500,1000)
        assertEquals(0f,edit.draft.buttons[0].xNorm,0f);assertEquals(1f,edit.draft.buttons[0].yNorm,0f)
        assertEquals(PrivilegeMethod.SHIZUKU,edit.validated().preferredBackend)
        assertEquals(7,edit.validated().buttons[0].touchSlot)
        assertTrue(runCatching { edit.move("a",Float.NaN,0f,1000,500) }.isFailure)
    }
    @Test fun duplicatePhysicalBindingsAndEmptyLayoutsCannotBeSaved() {
        val edit=MapperEditSession(config());val duplicate=edit.add("A")
        assertTrue(runCatching { edit.validated() }.isFailure)
        edit.bind(duplicate,"RT",ButtonBehavior.HOLD)
        assertEquals(ButtonBehavior.HOLD,edit.validated().buttons[1].buttonBehavior)
        edit.remove("a");edit.remove(duplicate)
        assertTrue(runCatching { edit.validated() }.isFailure)
    }
    @Test fun stickBindingsCreateRealRuntimeNodeTypesAndRebindingClearsExplicitIdentity() {
        val edit=MapperEditSession(config());val ls=edit.add("LS");val rs=edit.add("RS")
        assertEquals(NodeType.JOYSTICK_ZONE,edit.validated().buttons.first { it.id==ls }.type)
        assertEquals(NodeType.CAMERA_DRAG,edit.validated().buttons.first { it.id==rs }.type)
        edit.bind(ls,"B",ButtonBehavior.HOLD)
        assertEquals(NodeType.BUTTON,edit.validated().buttons.first { it.id==ls }.type)
        assertNull(edit.validated().buttons.first { it.id==ls }.axisX)
    }
    @Test fun resizeChangesRealNodeRadiusAndClampsToEditorLimits() {
        val edit=MapperEditSession(config())
        edit.resize("a",.04f)
        assertEquals(.09f,edit.draft.buttons.single().radiusNorm,.0001f)
        edit.resize("a",10f)
        assertEquals(.3f,edit.draft.buttons.single().radiusNorm,.0001f)
        edit.resize("a",-10f)
        assertEquals(.02f,edit.draft.buttons.single().radiusNorm,.0001f)
        assertTrue(runCatching { edit.resize("a",Float.NaN) }.isFailure)
    }
    @Test fun floatingWindowsRemainReachableAcrossDragAndRotation() {
        assertEquals(0 to 0,com.example.service.OverlayBounds.position(-50,-80,52,52,1000,500))
        assertEquals(948 to 448,com.example.service.OverlayBounds.position(3000,1000,52,52,1000,500))
        assertEquals(448 to 948,com.example.service.OverlayBounds.position(948,948,52,52,500,1000))
        assertEquals(0 to 0,com.example.service.OverlayBounds.position(100,100,300,300,200,200))
    }

    @Test fun rebindPreservesMacroAndTurboBehavior() {
        val macro=config().copy(buttons=listOf(MappingNode("m",.2f,.3f,type=NodeType.MACRO,boundKey="A",macroActions=listOf(MacroStep()))))
        val edit=MapperEditSession(macro);edit.bind("m","B",ButtonBehavior.TAP)
        assertEquals(NodeType.MACRO,edit.validated().buttons.single().type)
        assertEquals(macro.buttons.single().macroActions,edit.validated().buttons.single().macroActions)
        val turbo=MapperEditSession(config().copy(buttons=listOf(MappingNode("t",.2f,.3f,type=NodeType.TURBO,boundKey="A",turboHz=15))))
        turbo.bind("t","B",ButtonBehavior.TAP);assertEquals(15,turbo.validated().buttons.single().turboHz)
        assertEquals(NodeType.TURBO,turbo.validated().buttons.single().type)
    }

    @Test fun duplicateRequiresANewPhysicalInputAndAllocatesAnIndependentSlot() {
        val edit=MapperEditSession(config())
        val before=edit.draft
        assertTrue(runCatching { edit.duplicate("a","A",ButtonBehavior.TAP) }.isFailure)
        assertEquals(before,edit.draft)
        val id=edit.duplicate("a","RT",ButtonBehavior.HOLD)
        val copy=edit.validated().buttons.first { it.id==id }
        assertNull(copy.touchSlot);assertNull(copy.inputKeyCode);assertNull(copy.inputScanCode)
        assertEquals(ButtonBehavior.HOLD,copy.buttonBehavior)
        assertEquals(.24f,copy.xNorm,.0001f)
        val slots=com.example.input.TouchSlotAllocator.assign(edit.draft)
        assertNotEquals(slots["a"],slots[id])
        assertEquals(7,slots["a"])
    }

    @Test fun duplicateClearsObservedIdentityAndKeepsMacroStepsDetached() {
        val source=config().copy(buttons=listOf(MappingNode("macro",.2f,.3f,type=NodeType.MACRO,
            inputKeyCode=android.view.KeyEvent.KEYCODE_BUTTON_A,inputScanCode=304,touchSlot=7,
            macroActions=listOf(MacroStep(actionType="SWIPE",endXNorm=.7f,endYNorm=.8f)))))
        val edit=MapperEditSession(source)
        val id=edit.duplicate("macro","B",ButtonBehavior.TAP)
        val copy=edit.validated().buttons.first { it.id==id }
        assertEquals(NodeType.MACRO,copy.type)
        assertEquals(source.buttons.single().macroActions,copy.macroActions)
        assertNotSame(source.buttons.single().macroActions,copy.macroActions)
        assertNull(copy.inputKeyCode);assertNull(copy.inputScanCode)
    }

    @Test fun gridSnapsRealCoordinatesAndResetRestoresTheWholeDraft() {
        val original=config();val edit=MapperEditSession(original)
        edit.move("a",233f,317f,1001,1001,true)
        assertEquals(.25f,edit.draft.buttons.single().xNorm,.0001f)
        assertEquals(.3f,edit.draft.buttons.single().yNorm,.0001f)
        edit.resize("a",.02f)
        edit.replaceDraft(edit.draft.copy(camera=edit.draft.camera.copy(invertY=true)))
        edit.reset()
        assertEquals(original,edit.draft)
        assertNotSame(original.buttons.single(),edit.draft.buttons.single())
        assertTrue(runCatching { edit.replaceDraft(original.copy(gamePackage="com.wrong.game")) }.isFailure)
        assertEquals(original,edit.draft)
    }
    @Test fun cameraInversionCanBeDisabledEvenWhenALegacyNodeFlagWasEnabled() {
        val original=config().copy(camera=CameraSettings(invertY=true),buttons=listOf(
            MappingNode("ls",.2f,.3f,type=NodeType.JOYSTICK_ZONE,boundKey="LS",invertY=true),
            MappingNode("rs",.6f,.3f,type=NodeType.CAMERA_DRAG,boundKey="RS",invertY=true)))
        val edit=MapperEditSession(original)
        edit.setCameraInversion(false)
        assertFalse(edit.validated().camera.invertY)
        assertFalse(edit.draft.buttons.first { it.id=="rs" }.invertY)
        assertTrue(edit.draft.buttons.first { it.id=="ls" }.invertY)
        assertTrue(original.camera.invertY)
        edit.setCameraInversion(true)
        assertTrue(edit.validated().camera.invertY)
        assertFalse(edit.draft.buttons.first { it.id=="rs" }.invertY)
    }
    @Test fun inspectorTuningChangesTheDraftWhilePreservingObservedTriggerIdentity() {
        val original=config().copy(buttons=listOf(MappingNode("rt",.2f,.3f,boundKey="RT",
            inputKeyCode=android.view.KeyEvent.KEYCODE_BUTTON_R2,inputScanCode=312,touchSlot=7)))
        val edit=MapperEditSession(original)
        edit.replaceDraft(edit.draft.copy(buttons=edit.draft.buttons.map {
            it.copy(buttonBehavior=ButtonBehavior.HOLD,triggerPressThreshold=.6f,triggerReleaseThreshold=.2f)
        },camera=edit.draft.camera.copy(smoothingFrames=5,fastTurnBoost=1.5f),
            joystick=edit.draft.joystick.copy(walkRadiusScale=.4f,runRadiusScale=.9f)))
        val saved=edit.validated()
        assertEquals(android.view.KeyEvent.KEYCODE_BUTTON_R2,saved.buttons.single().inputKeyCode)
        assertEquals(312,saved.buttons.single().inputScanCode)
        assertEquals(7,saved.buttons.single().touchSlot)
        assertEquals(.6f,saved.buttons.single().triggerPressThreshold,.0001f)
        assertEquals(.2f,saved.buttons.single().triggerReleaseThreshold,.0001f)
        assertEquals(5,saved.camera.smoothingFrames)
        assertEquals(.4f,saved.joystick.walkRadiusScale,.0001f)
        assertEquals(.55f,original.buttons.single().triggerPressThreshold,.0001f)
    }

    @Test fun inspectorUpdatesRejectDuplicateSlotsAndInvalidTriggerIntervalsWithoutChangingTheDraft() {
        val edit=MapperEditSession(config())
        val other=edit.add("RT")
        val before=edit.draft
        val duplicateSlot=before.copy(buttons=before.buttons.map { if(it.id==other) it.copy(touchSlot=7) else it })
        assertTrue(runCatching { edit.replaceDraft(duplicateSlot) }.isFailure)
        assertEquals(before,edit.draft)
        val invalidThreshold=before.copy(buttons=before.buttons.map {
            if(it.id==other) it.copy(triggerPressThreshold=.2f,triggerReleaseThreshold=.8f) else it
        })
        assertTrue(runCatching { edit.replaceDraft(invalidThreshold) }.isFailure)
        assertEquals(before,edit.draft)
    }

}
