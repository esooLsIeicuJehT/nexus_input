package com.example.ui.theme

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
fun Modifier.nexusGlass(radius: Dp = 20.dp, accent: Color = NexusCyan): Modifier = this
    .background(NexusPanelGradient, RoundedCornerShape(radius))
    .drawWithCache {
        val corner = CornerRadius(radius.toPx())
        val edge = Brush.linearGradient(listOf(Color.White.copy(alpha = .32f),
            accent.copy(alpha = .65f), accent.copy(alpha = .24f)))
        onDrawBehind {
            // Layered translucent strokes approximate edge bloom without offscreen blur.
            drawRoundRect(accent.copy(alpha = .045f), cornerRadius = corner,
                style = Stroke(width = 5.dp.toPx()))
            drawRoundRect(edge, cornerRadius = corner, style = Stroke(1.dp.toPx()))
            // A faint inner highlight gives depth without a blur layer or shader.
            drawRoundRect(Color.White.copy(alpha = .035f), topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                size = androidx.compose.ui.geometry.Size((size.width - 4.dp.toPx()).coerceAtLeast(0f),
                    (size.height - 4.dp.toPx()).coerceAtLeast(0f)), cornerRadius = corner,
                style = Stroke(.5.dp.toPx()))
        }
    }

@Composable
fun NexusAtmosphere(accent: Color = LocalNexusAccent.current) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(GraphiteFoundation).drawWithCache {
        val haze = Brush.radialGradient(listOf(accent.copy(alpha = .095f), Color.Transparent),
            center = Offset(size.width * .9f, size.height * .18f), radius = size.width * .85f)
        val base = Brush.radialGradient(listOf(NexusBlue.copy(alpha = .055f), Color.Transparent),
            center = Offset(0f, size.height * .8f), radius = size.width * .8f)
        onDrawBehind {
            drawRect(haze)
            drawRect(base)
        }
    })
}
