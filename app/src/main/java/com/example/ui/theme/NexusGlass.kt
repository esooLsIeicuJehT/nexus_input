package com.example.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Static glass/edge lighting: no blur render loop or gameplay input work. */
fun Modifier.nexusGlass(radius: Dp = 20.dp): Modifier = this
    .background(NexusPanelGradient, RoundedCornerShape(radius))
    .drawWithCache {
        val corner = CornerRadius(radius.toPx())
        val edge = Brush.linearGradient(listOf(NexusCyan.copy(alpha = .75f),
            Color.White.copy(alpha = .14f), NexusVioletLight.copy(alpha = .65f)))
        onDrawBehind {
            // Layered translucent strokes approximate edge bloom without offscreen blur.
            for (width in listOf(9f, 5f, 2f)) {
                drawRoundRect(NexusCyan.copy(alpha = .025f), cornerRadius = corner,
                    style = Stroke(width = width.dp.toPx()))
            }
            drawRoundRect(edge, cornerRadius = corner, style = Stroke(1.dp.toPx()))
        }
    }

@Composable
fun NexusAtmosphere() {
    Canvas(Modifier.fillMaxSize().background(GraphiteFoundation)) {
        drawRect(Brush.radialGradient(listOf(NexusViolet.copy(alpha = .24f), Color.Transparent),
            center = Offset(size.width * .9f, size.height * .15f), radius = size.width * .95f))
        drawRect(Brush.radialGradient(listOf(NexusCyan.copy(alpha = .12f), Color.Transparent),
            center = Offset(0f, size.height * .65f), radius = size.width * .8f))
        val step = 48.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(Color.White.copy(alpha = .018f), Offset(x, 0f), Offset(x, size.height))
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(Color.White.copy(alpha = .018f), Offset(0f, y), Offset(size.width, y))
            y += step
        }
    }
}
