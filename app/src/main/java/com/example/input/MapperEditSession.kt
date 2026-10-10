package com.example.input

import com.example.model.*
import java.util.UUID

/** A detached draft. Cancel cannot mutate the armed or saved profile's mutable node positions. */
class MapperEditSession(val original: MappingConfig) {
    var draft: MappingConfig = original.copy(buttons = original.buttons.map { it.copy() })
        private set

    fun move(id: String, pixelX: Float, pixelY: Float, width: Int, height: Int) {
        require(width > 1 && height > 1 && pixelX.isFinite() && pixelY.isFinite()) { "Invalid editor geometry" }
        draft = draft.copy(buttons = draft.buttons.map { node ->
            if (node.id == id) node.copy(xNorm = (pixelX / (width - 1)).coerceIn(0f,1f),
                yNorm = (pixelY / (height - 1)).coerceIn(0f,1f)) else node
        })
    }
    fun resize(id: String, delta: Float) {
        require(delta.isFinite()) { "Invalid resize delta" }
        draft = draft.copy(buttons = draft.buttons.map { node ->
            if (node.id == id) node.copy(radiusNorm = (node.radiusNorm + delta).coerceIn(.02f, .3f)) else node
        })
    }

    fun add(binding: String): String {
        val id = UUID.randomUUID().toString()
        val canonical = ControllerBindingAliases.canonical(binding)
        require(canonical in ControllerBindingAliases.supported) { "Unsupported physical input" }
        val type = when(canonical) { "LS" -> NodeType.JOYSTICK_ZONE; "RS" -> NodeType.CAMERA_DRAG; else -> NodeType.BUTTON }
        draft = draft.copy(buttons = draft.buttons + MappingNode(id,.5f,.5f,
            radiusNorm = if (type == NodeType.BUTTON) .05f else .12f, type = type, boundKey = canonical))
        return id
    }
    fun bind(id: String, binding: String, behavior: ButtonBehavior) {
        val canonical = ControllerBindingAliases.canonical(binding)
        require(canonical in ControllerBindingAliases.supported) { "Unsupported physical input" }
        draft = draft.copy(buttons = draft.buttons.map { if(it.id != id) it else it.copy(boundKey=canonical,
            type = when(canonical) { "LS" -> NodeType.JOYSTICK_ZONE; "RS" -> NodeType.CAMERA_DRAG; else -> if(it.type in setOf(NodeType.MACRO,NodeType.TURBO)) it.type else NodeType.BUTTON },
            buttonBehavior=behavior,inputKeyCode=null,inputScanCode=null,axisX=null,axisY=null) })
    }
    fun bindObserved(id: String, event: android.view.KeyEvent) {
        require(ControllerSourceClassifier.accepts(event.source,event.device?.sources ?: 0)) { "Not a controller event" }
        require(event.keyCode != android.view.KeyEvent.KEYCODE_UNKNOWN || event.scanCode > 0) { "Input has no usable key or scan code" }
        val alias=ControllerBindingAliases.forEvent(event).firstOrNull()?.let(ControllerBindingAliases::canonical)
        draft=draft.copy(buttons=draft.buttons.map { node ->
            if(node.id != id) node else node.copy(boundKey=alias ?: node.boundKey,
                inputKeyCode=event.keyCode,inputScanCode=event.scanCode.takeIf { it > 0 })
        })
    }
    fun remove(id: String) { draft = draft.copy(buttons = draft.buttons.filterNot { it.id == id }) }
    fun validated(): MappingConfig {
        val errors = ProfileValidator.errors(draft, original.gamePackage, original.id)
        require(errors.isEmpty()) { errors.joinToString("; ") }
        return draft.copy(lastUpdated = System.currentTimeMillis())
    }
}
