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
}
