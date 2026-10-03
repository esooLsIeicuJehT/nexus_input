package com.example.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.MappingForegroundService
import com.example.ui.MainAppViewModel
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun FloatingOverlayHUD(
    viewModel: MainAppViewModel,
    onOpenMapper: () -> Unit,
    onOpenCrosshair: () -> Unit,
    onOpenCalibration: () -> Unit,
    onOpenRootWebUi: (() -> Unit)? = null
) {
    var offsetX by remember { mutableFloatStateOf(20f) }
    var offsetY by remember { mutableFloatStateOf(250f) }
    var isMenuOpen by remember { mutableStateOf(false) }
    val isOverlayVisible by MappingForegroundService.isOverlayVisible.collectAsState()
    val context = LocalContext.current

    if (!isOverlayVisible) return

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Floating Draggable Icon with Edge Snapping
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(54.dp)
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(CyberCyan, Color(0xFF005A66))
                    )
                )
                .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            // Edge snap simulation (snap to left or right screen border)
                            val screenW = 1000f // approximate container density boundary
                            offsetX = if (offsetX < screenW / 2f) 20f else (screenW - 120f).coerceAtLeast(20f)
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceAtLeast(10f)
                        offsetY = (offsetY + dragAmount.y).coerceIn(100f, 1800f)
                    }
                }
                .clickable { isMenuOpen = !isMenuOpen },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.SportsEsports,
                contentDescription = "Controlyst Quick HUD",
                tint = Color(0xFF00363D),
                modifier = Modifier.size(30.dp)
            )
        }

        // Radial / Quick Menu Dropdown
        AnimatedVisibility(
            visible = isMenuOpen,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), (offsetY + 65f).roundToInt()) }
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                border = BorderStroke(1.5.dp, CyberCyan),
                modifier = Modifier.width(220.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Controlyst HUD", color = CyberCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        IconButton(
                            onClick = { isMenuOpen = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }

                    HorizontalDivider(color = DarkSurfaceBorder, modifier = Modifier.padding(vertical = 6.dp))

                    QuickMenuItem(
                        icon = Icons.Default.Tune,
                        title = "Live Visual Mapper",
                        onClick = {
                            isMenuOpen = false
                            onOpenMapper()
                        }
                    )
                    QuickMenuItem(
                        icon = Icons.Default.CenterFocusStrong,
                        title = "Crosshair Settings",
                        onClick = {
                            isMenuOpen = false
                            onOpenCrosshair()
                        }
                    )
                    QuickMenuItem(
                        icon = Icons.Default.Speed,
                        title = "Calibration Lab",
                        onClick = {
                            isMenuOpen = false
                            onOpenCalibration()
                        }
                    )

                    onOpenRootWebUi?.let { openWebUi ->
                        QuickMenuItem(
                            icon = Icons.Default.Terminal,
                            title = "Root WebUI Daemon",
                            onClick = {
                                isMenuOpen = false
                                openWebUi()
                            }
                        )
                    }

                    HorizontalDivider(color = DarkSurfaceBorder, modifier = Modifier.padding(vertical = 6.dp))

                    // Panic Kill-Switch
                    Button(
                        onClick = {
                            isMenuOpen = false
                            MappingForegroundService.triggerPanicKill(context)
                            viewModel.showSnack("Panic Kill Activated! Overlay & injection terminated.")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Panic Kill-Switch", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun QuickMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
