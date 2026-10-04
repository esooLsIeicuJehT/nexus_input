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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    val controller by viewModel.controllerProfile.collectAsState()
    val activeConfig by viewModel.activeConfig.collectAsState()
    val activePrivilege by viewModel.activePrivilegeMethod.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current

    if (!isOverlayVisible) return

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val bubblePx = with(density) { 50.dp.toPx() }
        val marginPx = with(density) { 10.dp.toPx() }
        val menuWidthPx = with(density) { 244.dp.toPx() }
        val menuHeightEstimatePx = with(density) { 310.dp.toPx() }
        val gapPx = with(density) { 8.dp.toPx() }

        LaunchedEffect(widthPx, heightPx) {
            offsetX = offsetX.coerceIn(marginPx, (widthPx - bubblePx - marginPx).coerceAtLeast(marginPx))
            offsetY = offsetY.coerceIn(marginPx, (heightPx - bubblePx - marginPx).coerceAtLeast(marginPx))
        }

        val menuX = offsetX.coerceIn(
            marginPx,
            (widthPx - menuWidthPx - marginPx).coerceAtLeast(marginPx)
        )
        val roomBelow = heightPx - (offsetY + bubblePx + gapPx)
        val menuY = if (roomBelow >= menuHeightEstimatePx) {
            offsetY + bubblePx + gapPx
        } else {
            (offsetY - menuHeightEstimatePx - gapPx).coerceAtLeast(marginPx)
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(50.dp)
                .shadow(14.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(NexusCyan, NexusBlue, NexusViolet)
                    )
                )
                .border(1.5.dp, Color.White.copy(alpha = 0.75f), CircleShape)
                .pointerInput(widthPx, heightPx) {
                    detectDragGestures(
                        onDragEnd = {
                            val centerX = offsetX + bubblePx / 2f
                            offsetX = if (centerX < widthPx / 2f) {
                                marginPx
                            } else {
                                (widthPx - bubblePx - marginPx).coerceAtLeast(marginPx)
                            }
                            offsetY = offsetY.coerceIn(
                                marginPx,
                                (heightPx - bubblePx - marginPx).coerceAtLeast(marginPx)
                            )
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(
                            marginPx,
                            (widthPx - bubblePx - marginPx).coerceAtLeast(marginPx)
                        )
                        offsetY = (offsetY + dragAmount.y).coerceIn(
                            marginPx,
                            (heightPx - bubblePx - marginPx).coerceAtLeast(marginPx)
                        )
                    }
                }
                .clickable { isMenuOpen = !isMenuOpen },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF06121F)),
                contentAlignment = Alignment.Center
            ) {
                Text("N", color = NexusCyan, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (controller.connected) AccentGreen else AccentAmber)
                    .border(1.dp, Color(0xFF06121F), CircleShape)
            )
        }

        AnimatedVisibility(
            visible = isMenuOpen,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
            modifier = Modifier.offset {
                IntOffset(menuX.roundToInt(), menuY.roundToInt())
            }
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xF5081624)),
                border = BorderStroke(1.dp, NexusCyan.copy(alpha = 0.75f)),
                modifier = Modifier.width(244.dp)
            ) {
                Column(modifier = Modifier.padding(11.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("NEXUS INPUT", color = NexusCyan, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp)
                            Text(
                                activeConfig.profileName,
                                color = TextSecondary,
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { isMenuOpen = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close quick menu", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                        shape = RoundedCornerShape(9.dp),
                        color = Color(0xFF0A1C2B),
                        border = BorderStroke(1.dp, DarkSurfaceBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(7.dp).clip(CircleShape)
                                    .background(if (controller.connected) AccentGreen else AccentAmber)
                            )
                            Spacer(Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    controller.deviceName ?: "No controller detected",
                                    color = TextPrimary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(activePrivilege.badgeLabel, color = TextMuted, fontSize = 8.sp)
                            }
                        }
                    }

                    HorizontalDivider(color = DarkSurfaceBorder, modifier = Modifier.padding(vertical = 7.dp))

                    QuickMenuItem(
                        icon = Icons.Default.Tune,
                        title = "Live Visual Mapper",
                        subtitle = "Edit the active profile"
                    ) {
                        isMenuOpen = false
                        onOpenMapper()
                    }
                    QuickMenuItem(
                        icon = Icons.Default.CenterFocusStrong,
                        title = "Crosshair Settings",
                        subtitle = "Overlay position and style"
                    ) {
                        isMenuOpen = false
                        onOpenCrosshair()
                    }
                    QuickMenuItem(
                        icon = Icons.Default.Speed,
                        title = "Controller Calibration",
                        subtitle = "Deadzone and trigger tools"
                    ) {
                        isMenuOpen = false
                        onOpenCalibration()
                    }

                    onOpenRootWebUi?.let { openWebUi ->
                        QuickMenuItem(
                            icon = Icons.Default.Terminal,
                            title = "KernelSU WebUI",
                            subtitle = "Root companion controls"
                        ) {
                            isMenuOpen = false
                            openWebUi()
                        }
                    }

                    HorizontalDivider(color = DarkSurfaceBorder, modifier = Modifier.padding(vertical = 7.dp))

                    Button(
                        onClick = {
                            isMenuOpen = false
                            MappingForegroundService.triggerPanicKill(context)
                            viewModel.showSnack("Panic kill activated. Overlay and injection terminated.")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                        shape = RoundedCornerShape(9.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 7.dp)
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Panic Kill", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(30.dp),
            shape = RoundedCornerShape(8.dp),
            color = NexusCyan.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, NexusCyan.copy(alpha = 0.24f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = NexusCyan, modifier = Modifier.size(17.dp))
            }
        }
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, color = TextMuted, fontSize = 8.sp) }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
    }
}
