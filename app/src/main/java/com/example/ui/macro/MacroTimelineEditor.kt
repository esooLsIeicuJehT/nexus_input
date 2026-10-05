package com.example.ui.macro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.MainAppViewModel

/** Edits the same touch steps consumed by GamepadMappingRuntime; no simulated playback. */
@Composable
fun MacroTimelineEditor(viewModel: MainAppViewModel, onBack: () -> Unit) {
    val config by viewModel.activeConfig.collectAsState()
    val saved=config.buttons.firstOrNull { it.type==NodeType.MACRO }
    var name by remember(config.id) { mutableStateOf(saved?.label ?: "") }
    var key by remember(config.id) { mutableStateOf(saved?.boundKey ?: "") }
    var steps by remember(config.id) { mutableStateOf(saved?.macroActions ?: emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Touch macro",style=MaterialTheme.typography.headlineSmall)
        Text("Each step targets normalized screen coordinates. HOLD must have a later RELEASE. Run the saved macro from its physical controller binding in the game.")
        OutlinedTextField(name,{name=it},label={Text("Name")},singleLine=true)
        OutlinedTextField(key,{key=it.uppercase()},label={Text("Physical trigger input")},singleLine=true)
        steps.forEachIndexed { index,step ->
            Card { Column(Modifier.padding(8.dp)) {
                Text("Step ${index+1}")
                Row { listOf("TAP","HOLD","RELEASE").forEach { action -> FilterChip(step.actionType==action,{steps=steps.toMutableList().apply { this[index]=step.copy(actionType=action) }},label={Text(action)}) } }
                OutlinedTextField(step.delayMs.toString(),{ value -> value.toLongOrNull()?.let { steps=steps.toMutableList().apply { this[index]=step.copy(delayMs=it) } } },label={Text("Delay before step (ms)")})
                OutlinedTextField(step.durationMs.toString(),{ value -> value.toLongOrNull()?.let { steps=steps.toMutableList().apply { this[index]=step.copy(durationMs=it) } } },label={Text("TAP duration (ms)")})
                Text("X ${(step.xNorm*100).toInt()}%")
                Slider(step.xNorm,{steps=steps.toMutableList().apply { this[index]=step.copy(xNorm=it) }})
                Text("Y ${(step.yNorm*100).toInt()}%")
                Slider(step.yNorm,{steps=steps.toMutableList().apply { this[index]=step.copy(yNorm=it) }})
                TextButton(onClick={steps=steps.filterIndexed { i,_ -> i!=index }}) { Text("Remove step") }
            } }
        }
        Button(onClick={steps=steps+MacroStep()},enabled=steps.size<100) { Text("Add step") }
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        Button(onClick={
            val node=MappingNode(saved?.id ?: java.util.UUID.randomUUID().toString(),.5f,.5f,type=NodeType.MACRO,boundKey=key.trim(),label=name.trim(),macroActions=steps,touchSlot=saved?.touchSlot)
            val updated=config.copy(buttons=config.buttons.filterNot { it.id==saved?.id }+node)
            val errors=com.example.input.ProfileValidator.errors(updated)
            if(errors.isNotEmpty()) error=errors.joinToString("; ")
            else viewModel.updateActiveConfig(updated) { viewModel.showSnack("Macro saved to the game profile");onBack() }
        }) { Text("Save macro") }
        TextButton(onClick=onBack) { Text("Cancel") }
    }
}
