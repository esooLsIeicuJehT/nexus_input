package com.example.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * High-precision vector Controlyst emblem matching final.jpeg:
 * Central hub with radial prongs and satellite nodes on #090B10 foundation.
 */
@Composable
fun ControlystLogoIcon(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    animated: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_anim")
    val rotation by if (animated) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(12000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rot"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w / 2f, h / 2f)
        val outerRadius = w * 0.42f
        val innerRadius = w * 0.20f
        val coreRadius = w * 0.08f

        // Outer Orbit Ring
        drawCircle(
            color = ControlystViolet.copy(alpha = 0.85f),
            radius = outerRadius,
            center = center,
            style = Stroke(width = w * 0.045f)
        )

        // Center Hub with Signature Gradient
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(ControlystViolet, ControlystBlue, ControlystCyan),
                start = Offset(center.x - innerRadius, center.y - innerRadius),
                end = Offset(center.x + innerRadius, center.y + innerRadius)
            ),
            radius = innerRadius,
            center = center
        )

        // Center Cutout / Core Dot
        drawCircle(
            color = GraphiteFoundation,
            radius = innerRadius * 0.55f,
            center = center
        )
        drawCircle(
            color = ControlystCyan,
            radius = coreRadius,
            center = center
        )

        // 4 Radial Prongs ending in Satellite Nodes at 45, 135, 225, 315 degrees
        val angles = listOf(45f, 135f, 225f, 315f)
        val armColors = listOf(ControlystCyan, ControlystViolet, ControlystCyan, ControlystBlue)

        for (i in angles.indices) {
            val rad = Math.toRadians((angles[i] + rotation).toDouble())
            val startDist = innerRadius * 0.95f
            val endDist = outerRadius * 1.05f

            val startX = center.x + (startDist * Math.cos(rad)).toFloat()
            val startY = center.y + (startDist * Math.sin(rad)).toFloat()
            val endX = center.x + (endDist * Math.cos(rad)).toFloat()
            val endY = center.y + (endDist * Math.sin(rad)).toFloat()

            // Stem
            drawLine(
                color = armColors[i],
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = w * 0.06f
            )

            // Satellite node circle
            drawCircle(
                color = armColors[i],
                radius = w * 0.075f,
                center = Offset(endX, endY)
            )
        }

        // Cardinal Aim Tick Marks
        val tickDist = outerRadius * 0.72f
        val tickLength = w * 0.10f
        // Top
        drawLine(
            color = ControlystCyan,
            start = Offset(center.x, center.y - tickDist),
            end = Offset(center.x, center.y - tickDist + tickLength),
            strokeWidth = w * 0.045f
        )
        // Bottom
        drawLine(
            color = ControlystCyan,
            start = Offset(center.x, center.y + tickDist - tickLength),
            end = Offset(center.x, center.y + tickDist),
            strokeWidth = w * 0.045f
        )
        // Left
        drawLine(
            color = ControlystCyan,
            start = Offset(center.x - tickDist, center.y),
            end = Offset(center.x - tickDist + tickLength, center.y),
            strokeWidth = w * 0.045f
        )
        // Right
        drawLine(
            color = ControlystCyan,
            start = Offset(center.x + tickDist - tickLength, center.y),
            end = Offset(center.x + tickDist, center.y),
            strokeWidth = w * 0.045f
        )
    }
}

/**
 * Centered Header Logo with Engineered Typography "CONTROLYST" matching final.jpeg
 */
@Composable
fun ControlystLogoWithText(
    modifier: Modifier = Modifier,
    iconSize: Dp = 26.dp,
    textSize: Dp = 16.dp,
    letterSpacingSp: Float = 2.4f
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        ControlystLogoIcon(size = iconSize, animated = false)
        Spacer(Modifier.width(10.dp))
        Text(
            text = "CONTROLYST",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 17.sp,
            letterSpacing = letterSpacingSp.sp
        )
    }
}

