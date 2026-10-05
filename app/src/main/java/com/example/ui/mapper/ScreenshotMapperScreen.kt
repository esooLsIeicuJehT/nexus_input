package com.example.ui.mapper

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.input.ControllerBindingAliases
import com.example.model.*
import com.example.ui.MainAppViewModel
import com.example.ui.theme.*
import kotlin.math.hypot

/** Portrait and landscape editor over an actual imported/captured screenshot. */
@Composable
fun ScreenshotMapperScreen(viewModel: MainAppViewModel) {
    val config by viewModel.activeConfig.collectAsState()
    val screenshot by viewModel.screenshot.collectAsState()
    val candidates by viewModel.aiHudCandidates.collectAsState()
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(viewModel::importScreenshot) }
    var selected by remember(config.id) { mutableStateOf<String?>(null) }
    var showBinding by remember { mutableStateOf(false) }
    var binding by remember { mutableStateOf("A") }
    var behavior by remember { mutableStateOf(ButtonBehavior.TAP) }
    var turbo by remember { mutableStateOf(false) }
    var snap by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf<Offset?>(null) }
    val currentConfig by rememberUpdatedState(config)
    val node=config.buttons.firstOrNull { it.id==selected }
    val density=LocalDensity.current

    Column(Modifier.fillMaxSize().background(GraphiteFoundation)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(4.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={ picker.launch(arrayOf("image/*")) }) { Text("Import screenshot") }
            TextButton(onClick=viewModel::captureScreenshot) { Text("Capture screen") }
            TextButton(onClick=viewModel::runAiHudScan,enabled=screenshot!=null) { Text("Find regions") }
            TextButton(onClick={ binding="A";behavior=ButtonBehavior.TAP;turbo=false;selected=null;showBinding=true },enabled=config.gamePackage.isNotBlank()) { Text("Add input") }
            TextButton(onClick={ snap=!snap }) { Text(if(snap) "Grid on" else "Grid off") }
            TextButton(onClick={ viewModel.selectTab("macro") },enabled=config.gamePackage.isNotBlank()) { Text("Macro") }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF070B14))) {
            val ratio=screenshot?.let { it.width.toFloat()/it.height } ?: 16f/9f
            val frameWidth=minOf(maxWidth,maxHeight*ratio)
            val frameHeight=frameWidth/ratio
            Box(Modifier.size(frameWidth,frameHeight).align(Alignment.Center).border(1.dp,DarkSurfaceBorder)) {
                screenshot?.let { Image(it.asImageBitmap(),"Real game screenshot",Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds) }
                if(screenshot==null) Text("Import a game screenshot, capture a screen, or place inputs manually",color=TextMuted,modifier=Modifier.align(Alignment.Center).padding(16.dp))
                Canvas(Modifier.fillMaxSize()
                    .pointerInput(config.id) {
                        detectTapGestures { point ->
                            selected=currentConfig.buttons.minByOrNull { hypot(it.xNorm*(size.width-1)-point.x,it.yNorm*(size.height-1)-point.y) }
                                ?.takeIf { hypot(it.xNorm*(size.width-1)-point.x,it.yNorm*(size.height-1)-point.y)<32*density.density }?.id
                        }
                    }
                    .pointerInput(config.id,snap) {
                        detectDragGestures(onDragStart={ point ->
                            selected=currentConfig.buttons.minByOrNull { hypot(it.xNorm*(size.width-1)-point.x,it.yNorm*(size.height-1)-point.y) }
                                ?.takeIf { hypot(it.xNorm*(size.width-1)-point.x,it.yNorm*(size.height-1)-point.y)<32*density.density }?.id
                            drag=selected?.let { id -> currentConfig.buttons.first { it.id==id }.let { Offset(it.xNorm,it.yNorm) } }
                        },onDragEnd={
                            val position=drag
                            currentConfig.buttons.firstOrNull { it.id==selected }?.let { target ->
                                if(position!=null) viewModel.updateNode(target.copy(xNorm=position.x,yNorm=position.y))
                            };drag=null
                        },onDragCancel={drag=null}) { change,amount ->
                            change.consume();drag=drag?.let {
                                var x=(it.x+amount.x/(size.width-1)).coerceIn(0f,1f)
                                var y=(it.y+amount.y/(size.height-1)).coerceIn(0f,1f)
                                if(snap) { x=kotlin.math.round(x*20)/20;y=kotlin.math.round(y*20)/20 }
                                Offset(x,y)
                            }
                        }
                    }) {
                    if(snap) for(i in 1..19) {
                        drawLine(NexusCyan.copy(alpha=.15f),Offset(size.width*i/20,0f),Offset(size.width*i/20,size.height))
                        drawLine(NexusCyan.copy(alpha=.15f),Offset(0f,size.height*i/20),Offset(size.width,size.height*i/20))
                    }
                    val textPaint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.WHITE;textSize=12*density.density;textAlign=android.graphics.Paint.Align.CENTER }
                    config.buttons.forEach { item ->
                        val norm=if(item.id==selected) drag ?: Offset(item.xNorm,item.yNorm) else Offset(item.xNorm,item.yNorm)
                        val center=Offset(norm.x*(size.width-1),norm.y*(size.height-1))
                        val radius=maxOf(18*density.density,item.radiusNorm*minOf(size.width,size.height))
                        drawCircle(NexusViolet.copy(alpha=.4f),radius,center)
                        drawCircle(if(item.id==selected) Color.White else NexusCyan,radius,center,style=Stroke(2*density.density))
                        drawContext.canvas.nativeCanvas.drawText(item.boundKey,center.x,center.y+4*density.density,textPaint)
                    }
                }
            }
        }
        if(node!=null) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(4.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("${node.boundKey} · ${node.type} · ${node.buttonBehavior}",color=TextPrimary)
                TextButton(onClick={binding=node.boundKey;behavior=node.buttonBehavior;turbo=node.type==NodeType.TURBO;showBinding=true}) { Text("Rebind") }
                TextButton(onClick={viewModel.removeNode(node.id);selected=null}) { Text("Delete") }
                Text("Radius",color=TextSecondary)
                Slider(value=node.radiusNorm,onValueChange={viewModel.updateNode(node.copy(radiusNorm=it))},valueRange=.02f.. .3f,modifier=Modifier.width(140.dp))
            }
        }
        Text("Changes save to the selected game profile. Global stick capture requires Android 14+. Live testing is available over the game through the floating mapper.",color=TextMuted,modifier=Modifier.padding(8.dp),style=MaterialTheme.typography.bodySmall)
    }
    if(showBinding) AlertDialog(onDismissRequest={showBinding=false},title={Text(if(selected==null) "Add physical input" else "Rebind input")},text={
        Column(Modifier.verticalScroll(rememberScrollState())) {
            ControllerBindingAliases.supported.sorted().chunked(4).forEach { keys -> Row { keys.forEach { key ->
                FilterChip(selected=binding==key,onClick={binding=key;behavior=if(key in setOf("LT","RT")) ButtonBehavior.HOLD else ButtonBehavior.TAP},label={Text(key)})
            } } }
            if(binding !in setOf("LS","RS") && node?.type!=NodeType.MACRO) Row { Text("Turbo repeat (10 Hz)");Switch(turbo,{turbo=it}) }
            Row { ButtonBehavior.entries.forEach { value -> FilterChip(selected=behavior==value,onClick={behavior=value},label={Text(value.name)}) } }
        }
    },confirmButton={TextButton(onClick={
        val type=when(binding) { "LS" -> NodeType.JOYSTICK_ZONE; "RS" -> NodeType.CAMERA_DRAG; else -> if(node?.type==NodeType.MACRO) NodeType.MACRO else if(turbo) NodeType.TURBO else NodeType.BUTTON }
        val updated=node?.copy(boundKey=binding,type=type,buttonBehavior=behavior,inputKeyCode=null,inputScanCode=null,axisX=null,axisY=null)
            ?: MappingNode(java.util.UUID.randomUUID().toString(),.5f,.5f,radiusNorm=if(type==NodeType.BUTTON) .05f else .12f,type=type,boundKey=binding,buttonBehavior=behavior)
        if(node==null) viewModel.addNode(updated) else viewModel.updateNode(updated)
        selected=updated.id;showBinding=false
    }) { Text("Save input") }},dismissButton={TextButton(onClick={showBinding=false}) { Text("Cancel") }})
    if(candidates.isNotEmpty()) AlertDialog(onDismissRequest=viewModel::dismissAiHudCandidates,title={Text("Review image contrast regions")},text={
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("These regions may be scenery. Assign an input only to regions you recognize as controls.")
            candidates.forEach { candidate ->
                Text("${(candidate.xNorm*100).toInt()}%, ${(candidate.yNorm*100).toInt()}% · edge coverage ${(candidate.confidence*100).toInt()}%")
                OutlinedTextField(candidate.recommendedKey,{viewModel.assignHudCandidateInput(candidate.id,it)},label={Text("Physical input, e.g. A, RT, LS")},singleLine=true)
            }
        }
    },confirmButton={TextButton(onClick={viewModel.confirmAiHudCandidates(candidates)}) { Text("Apply assigned regions") }},dismissButton={TextButton(onClick=viewModel::dismissAiHudCandidates) { Text("Discard") }})
}
