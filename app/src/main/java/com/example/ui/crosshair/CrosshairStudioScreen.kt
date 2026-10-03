package com.example.ui.crosshair

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CrosshairConfig
import com.example.model.CrosshairShape
import com.example.service.MappingForegroundService
import com.example.ui.MainAppViewModel
import com.example.ui.theme.*

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext

@Composable
fun CrosshairStudioScreen(
    viewModel: MainAppViewModel
) {
    val activeConfig by viewModel.activeConfig.collectAsState()
    val crosshair = activeConfig.crosshair
    val context = LocalContext.current
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Master Toggle Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
            border = BorderStroke(1.dp, if (crosshair.isEnabled) CyberCyan else DarkSurfaceBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(CyberCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CenterFocusStrong, contentDescription = null, tint = CyberCyan)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Crosshair HUD Reticle", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                            Text("Always-on-top tactical reticle", color = TextSecondary, fontSize = 12.sp)
                        }
                    }

                    Switch(
                        checked = crosshair.isEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && !Settings.canDrawOverlays(context)) {
                                viewModel.showSnack("Please grant 'Display over other apps' permission.")
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                context.startActivity(intent)
                            } else {
                                viewModel.updateCrosshair(crosshair.copy(isEnabled = enabled))
                                MappingForegroundService.currentCrosshairConfig.value = crosshair.copy(isEnabled = enabled)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyberCyan,
                            checkedTrackColor = CyberCyan.copy(alpha = 0.3f)
                        )
                    )
                }

                if (!hasOverlayPermission) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Grant 'Display Over Other Apps' Permission", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Reticle Live Preview Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0F172A))
                .border(1.5.dp, DarkSurfaceBorder, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Simulated game target backdrop lines
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                drawLine(Color(0x15FFFFFF), Offset(cx, 0f), Offset(cx, size.height), 1f)
                drawLine(Color(0x15FFFFFF), Offset(0f, cy), Offset(size.width, cy), 1f)
                drawCircle(Color(0x12FFFFFF), radius = 60f, center = Offset(cx, cy), style = Stroke(1f))
                drawCircle(Color(0x12FFFFFF), radius = 120f, center = Offset(cx, cy), style = Stroke(1f))

                // Draw configured reticle
                drawCustomCrosshair(crosshair, cx, cy)
            }

            Text(
                text = "RETICLE PREVIEW (Offset: X=${crosshair.offsetX.toInt()}, Y=${crosshair.offsetY.toInt()})",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )
        }

        Spacer(Modifier.height(18.dp))

        // Shape Picker
        Text("Reticle Shape", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CrosshairShape.values().take(3).forEach { shape ->
                ShapeCard(
                    shape = shape,
                    isSelected = crosshair.shape == shape,
                    onClick = { viewModel.updateCrosshair(crosshair.copy(shape = shape)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CrosshairShape.values().drop(3).forEach { shape ->
                ShapeCard(
                    shape = shape,
                    isSelected = crosshair.shape == shape,
                    onClick = { viewModel.updateCrosshair(crosshair.copy(shape = shape)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // Color Palettes
        Text("Reticle Color", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf(
                "#00F0FF", // Neon Cyan
                "#10B981", // Emerald Green
                "#F43F5E", // Tactical Red
                "#FBBF24", // Warm Yellow
                "#A855F7", // Electric Violet
                "#FFFFFF"  // Clean White
            ).forEach { hex ->
                val isSelected = crosshair.colorHex.equals(hex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(parseHexColor(hex))
                        .border(if (isSelected) 2.5.dp else 1.dp, if (isSelected) Color.White else Color.Transparent, CircleShape)
                        .clickable { viewModel.updateCrosshair(crosshair.copy(colorHex = hex)) }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Size & Thickness Sliders
        Text("Size: ${crosshair.sizeDp.toInt()} dp", color = TextSecondary, fontSize = 13.sp)
        Slider(
            value = crosshair.sizeDp,
            onValueChange = { viewModel.updateCrosshair(crosshair.copy(sizeDp = it)) },
            valueRange = 8f..60f,
            colors = SliderDefaults.colors(thumbColor = CyberCyan, activeTrackColor = CyberCyan)
        )

        Text("Thickness: ${"%.1f".format(crosshair.thicknessDp)} dp", color = TextSecondary, fontSize = 13.sp)
        Slider(
            value = crosshair.thicknessDp,
            onValueChange = { viewModel.updateCrosshair(crosshair.copy(thicknessDp = it)) },
            valueRange = 1f..8f,
            colors = SliderDefaults.colors(thumbColor = CyberCyan, activeTrackColor = CyberCyan)
        )

        Text("Center Gap: ${crosshair.gapDp.toInt()} dp", color = TextSecondary, fontSize = 13.sp)
        Slider(
            value = crosshair.gapDp,
            onValueChange = { viewModel.updateCrosshair(crosshair.copy(gapDp = it)) },
            valueRange = 0f..20f,
            colors = SliderDefaults.colors(thumbColor = CyberCyan, activeTrackColor = CyberCyan)
        )

        // Center Offset (for games with off-center aim points)
        Text("Center Offset X: ${crosshair.offsetX.toInt()} dp", color = TextSecondary, fontSize = 13.sp)
        Slider(
            value = crosshair.offsetX,
            onValueChange = { viewModel.updateCrosshair(crosshair.copy(offsetX = it)) },
            valueRange = -50f..50f,
            colors = SliderDefaults.colors(thumbColor = ElectricViolet, activeTrackColor = ElectricViolet)
        )

        Text("Center Offset Y: ${crosshair.offsetY.toInt()} dp", color = TextSecondary, fontSize = 13.sp)
        Slider(
            value = crosshair.offsetY,
            onValueChange = { viewModel.updateCrosshair(crosshair.copy(offsetY = it)) },
            valueRange = -50f..50f,
            colors = SliderDefaults.colors(thumbColor = ElectricViolet, activeTrackColor = ElectricViolet)
        )

        // Dynamic Spread on Stick Deflection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Dynamic Aim Spread", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                Text("Reticle blooms outward during stick movement", color = TextSecondary, fontSize = 12.sp)
            }
            Switch(
                checked = crosshair.dynamicSpread,
                onCheckedChange = { viewModel.updateCrosshair(crosshair.copy(dynamicSpread = it)) },
                colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan)
            )
        }
    }
}

@Composable
fun ShapeCard(
    shape: CrosshairShape,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) DarkSurfaceElevated else DarkSurface
        ),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) CyberCyan else DarkSurfaceBorder)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = shape.displayName,
                color = if (isSelected) CyberCyan else TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

fun DrawScope.drawCustomCrosshair(cfg: CrosshairConfig, centerX: Float, centerY: Float) {
    val cx = centerX + cfg.offsetX
    val cy = centerY + cfg.offsetY
    val reticleColor = parseHexColor(cfg.colorHex).copy(alpha = cfg.opacity)
    val halfSize = cfg.sizeDp * density
    val strokeWidth = cfg.thicknessDp * density
    val gap = cfg.gapDp * density

    when (cfg.shape) {
        CrosshairShape.DOT -> {
            drawCircle(reticleColor, radius = strokeWidth * 2f, center = Offset(cx, cy))
        }
        CrosshairShape.CLASSIC_CROSS -> {
            // Top
            drawLine(reticleColor, Offset(cx, cy - gap - halfSize), Offset(cx, cy - gap), strokeWidth)
            // Bottom
            drawLine(reticleColor, Offset(cx, cy + gap), Offset(cx, cy + gap + halfSize), strokeWidth)
            // Left
            drawLine(reticleColor, Offset(cx - gap - halfSize, cy), Offset(cx - gap, cy), strokeWidth)
            // Right
            drawLine(reticleColor, Offset(cx + gap, cy), Offset(cx + gap + halfSize, cy), strokeWidth)
        }
        CrosshairShape.CIRCLE_DOT -> {
            drawCircle(reticleColor, radius = 3f, center = Offset(cx, cy))
            drawCircle(reticleColor, radius = halfSize, center = Offset(cx, cy), style = Stroke(strokeWidth))
        }
        CrosshairShape.T_SHAPE -> {
            // Bottom, Left, Right
            drawLine(reticleColor, Offset(cx, cy + gap), Offset(cx, cy + gap + halfSize), strokeWidth)
            drawLine(reticleColor, Offset(cx - gap - halfSize, cy), Offset(cx - gap, cy), strokeWidth)
            drawLine(reticleColor, Offset(cx + gap, cy), Offset(cx + gap + halfSize, cy), strokeWidth)
        }
        CrosshairShape.CHEVRON -> {
            drawLine(reticleColor, Offset(cx - halfSize, cy + halfSize), Offset(cx, cy), strokeWidth)
            drawLine(reticleColor, Offset(cx, cy), Offset(cx + halfSize, cy + halfSize), strokeWidth)
        }
        CrosshairShape.TRI_LINE -> {
            drawLine(reticleColor, Offset(cx - gap - halfSize, cy), Offset(cx - gap, cy), strokeWidth)
            drawLine(reticleColor, Offset(cx + gap, cy), Offset(cx + gap + halfSize, cy), strokeWidth)
            drawLine(reticleColor, Offset(cx, cy + gap), Offset(cx, cy + gap + halfSize), strokeWidth)
        }
    }
}

fun parseHexColor(hex: String): Color {
    return try {
        val clean = hex.replace("#", "")
        val longVal = clean.toLong(16)
        if (clean.length == 6) {
            Color((0xFF000000 or longVal).toInt())
        } else {
            Color(longVal.toInt())
        }
    } catch (e: Exception) {
        CyberCyan
    }
}
