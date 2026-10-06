package com.example

import com.example.input.MapperEditSession
import com.example.model.*
import org.junit.Assert.*
import org.junit.Test

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

}
