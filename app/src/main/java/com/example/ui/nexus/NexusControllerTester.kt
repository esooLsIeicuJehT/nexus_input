package com.example.ui.nexus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.input.ControllerInputMonitor
import com.example.ui.MainAppViewModel
import com.example.ui.theme.*

/** Generic controller diagram; highlights come only from the observed Android event stream. */
@Composable
fun NexusControllerTester(viewModel: MainAppViewModel) {
    val live by ControllerInputMonitor.state.collectAsState()
    val controller by viewModel.controllerProfile.collectAsState()
    val inventory = rememberInputDevices(viewModel::detectController)
    val observedDevice = inventory.devices.firstOrNull { it.id == live.deviceId }
    val pressed = remember(live.pressedButtons, live.axes) { NexusScreenPresentation.pressedButtonLabels(live.pressedButtons, live.axes) }
    val accent = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("CONTROLLER TEST", color = accent, fontSize = 24.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        Column(Modifier.fillMaxWidth().nexusGlass(16.dp, accent).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("CONNECTED DEVICE", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(observedDevice?.name ?: controller.deviceName ?: "No controller detected", color = TextPrimary, fontSize = 17.sp)
            Text(if (live.deviceId != null) "Observed event device: ${live.deviceId}" else "Waiting for Android controller events", color = TextSecondary, fontSize = 12.sp)
            Text("Vendor: ${observedDevice?.vendorId?.let { "0x%04X".format(it) } ?: controller.vendorId?.let { "0x%04X".format(it) } ?: "Not observed"}  ·  Product: ${observedDevice?.productId?.let { "0x%04X".format(it) } ?: controller.productId?.let { "0x%04X".format(it) } ?: "Not observed"}", color = TextSecondary, fontSize = 11.sp)
            Text("Source: ${live.lastSource?.let { "0x${it.toString(16)}" } ?: "Not observed"}  ·  Transport: not observed", color = TextMuted, fontSize = 10.sp)
            inventory.error?.let { Text(it, color = AccentRose) }
            TextButton(onClick = viewModel::detectController) { Text("Refresh controller") }
        }
        Canvas(Modifier.fillMaxWidth().height(190.dp)) {
            val w = size.width; val h = size.height
            val body = Path().apply {
                moveTo(w * .22f, h * .12f); lineTo(w * .78f, h * .12f)
                cubicTo(w * .9f, h * .12f, w * .99f, h * .85f, w * .85f, h * .9f)
                cubicTo(w * .78f, h * .94f, w * .74f, h * .58f, w * .61f, h * .59f)
                lineTo(w * .39f, h * .59f)
                cubicTo(w * .26f, h * .58f, w * .22f, h * .94f, w * .15f, h * .9f)
                cubicTo(w * .01f, h * .85f, w * .1f, h * .12f, w * .22f, h * .12f); close()
            }
            drawPath(body, accent.copy(alpha = .055f)); drawPath(body, accent.copy(alpha = .68f), style = Stroke(1.5.dp.toPx()))
            fun stick(cx: Float, cy: Float, x: Float?, y: Float?) {
                val center = Offset(w * cx, h * cy); val radius = h * .13f
                drawCircle(accent.copy(alpha = .4f), radius, center, style = Stroke(1.dp.toPx()))
                if (x != null && y != null) {
                    val point = center + Offset(x.coerceIn(-1f, 1f) * radius, y.coerceIn(-1f, 1f) * radius)
                    drawLine(accent, center, point, 2.dp.toPx()); drawCircle(accent, 4.dp.toPx(), point)
                }
            }
            stick(.28f, .35f, live.normalizedAxes["LX"], live.normalizedAxes["LY"])
            stick(.59f, .53f, live.normalizedAxes["RX"], live.normalizedAxes["RY"])
            listOf(Triple("A", .76f, .42f), Triple("B", .82f, .3f), Triple("X", .70f, .3f), Triple("Y", .76f, .18f)).forEach { (label, x, y) ->
                drawCircle(if (label in pressed) accent else accent.copy(alpha = .15f), h * .045f, Offset(w * x, h * y))
                drawCircle(accent.copy(alpha = .5f), h * .045f, Offset(w * x, h * y), style = Stroke(1.dp.toPx()))
            }
            val dc = Offset(w * .4f, h * .55f); val dr = h * .06f
            drawLine(accent.copy(alpha = .5f), dc - Offset(dr, 0f), dc + Offset(dr, 0f), 5.dp.toPx())
            drawLine(accent.copy(alpha = .5f), dc - Offset(0f, dr), dc + Offset(0f, dr), 5.dp.toPx())
        }
        Column(Modifier.fillMaxWidth().nexusGlass(16.dp, accent).padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("LIVE BUTTON INDICATORS", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            listOf("A", "B", "X", "Y", "LB", "RB", "LT", "RT", "↑", "↓", "←", "→", "START", "SELECT", "L3", "R3").chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { label ->
                        Surface(Modifier.weight(1f), shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                            color = if (label in pressed) accent.copy(alpha = .25f) else Color.White.copy(alpha = .035f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (label in pressed) accent else Color.White.copy(alpha = .12f))) {
                            Text(label, color = if (label in pressed) accent else TextSecondary, fontSize = 11.sp,
                                modifier = Modifier.padding(vertical = 9.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
            Text(if (live.lastEventUptimeMs == 0L) "No controller events observed. Unlit indicators do not confirm released inputs." else "Highlights reflect the most recent observed events.", color = TextMuted, fontSize = 10.sp)
        }
        Column(Modifier.fillMaxWidth().nexusGlass(16.dp, accent).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ANALOG DISPLAY", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StickObservation("LEFT STICK", live.normalizedAxes["LX"], live.normalizedAxes["LY"], Modifier.weight(1f))
                StickObservation("RIGHT STICK", live.normalizedAxes["RX"], live.normalizedAxes["RY"], Modifier.weight(1f))
            }
            listOf("LX", "LY", "RX", "RY", "LT", "RT", "BRAKE", "GAS", "HAT_X", "HAT_Y").forEach { axis ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(axis, color = TextSecondary, fontSize = 11.sp)
                    Text(NexusScreenPresentation.axisText(live.axes[axis]), color = if (live.axes[axis] != null) accent else TextMuted, fontSize = 11.sp)
                }
            }
        }
        Column(Modifier.fillMaxWidth().nexusGlass(16.dp, accent).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("RAW INPUT PANEL", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            if (live.eventLog.isEmpty()) Text("No Android controller events received", color = TextMuted, fontSize = 11.sp)
            else Text(live.eventLog.takeLast(10).joinToString("\n"), color = TextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun StickObservation(label: String, x: Float?, y: Float?, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(100.dp)) {
            val center = Offset(size.width / 2, size.height / 2); val radius = size.minDimension * .45f
            drawCircle(Color.White.copy(alpha = .035f), radius, center)
            for (scale in listOf(.33f, .66f, 1f)) drawCircle(accent.copy(alpha = .2f), radius * scale, center, style = Stroke(1.dp.toPx()))
            drawLine(accent.copy(alpha = .3f), center - Offset(radius, 0f), center + Offset(radius, 0f), 1.dp.toPx())
            drawLine(accent.copy(alpha = .3f), center - Offset(0f, radius), center + Offset(0f, radius), 1.dp.toPx())
            if (x != null && y != null) {
                val point = center + Offset(x.coerceIn(-1f, 1f) * radius, y.coerceIn(-1f, 1f) * radius)
                drawLine(accent, center, point, 2.dp.toPx()); drawCircle(accent, 4.dp.toPx(), point)
            }
        }
        Text(label, color = accent, fontSize = 10.sp)
        if (x == null || y == null) Text("Not observed", color = TextMuted, fontSize = 10.sp)
    }
}
