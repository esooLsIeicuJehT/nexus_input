package com.example.ui.nexus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ControlystLogoIcon
import com.example.ui.theme.NexusAtmosphere
import com.example.ui.theme.NexusCyan
import com.example.ui.theme.NexusVioletLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.nexusGlass

/** Brand handoff only. Backend readiness is observed on Home, never implied here. */
@Composable
fun NexusSplashScreen() {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val compact = maxHeight < 500.dp
        NexusAtmosphere(NexusCyan)
        Column(
            modifier = Modifier.padding(horizontal = 30.dp).widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.fillMaxWidth().height(if (compact) 220.dp else 340.dp).drawWithCache {
                    val edge = Brush.linearGradient(listOf(NexusCyan.copy(alpha = .55f), NexusVioletLight.copy(alpha = .45f)))
                    onDrawBehind {
                        val pad = 12.dp.toPx()
                        drawRoundRect(edge, topLeft = Offset(pad, pad),
                            size = androidx.compose.ui.geometry.Size(size.width - pad * 2, size.height - pad * 2),
                            cornerRadius = CornerRadius(25.dp.toPx()), style = Stroke(1.dp.toPx()))
                    }
                }.padding(if (compact) 12.dp else 22.dp).nexusGlass(22.dp, NexusCyan)
                    .padding(if (compact) 12.dp else 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ControlystLogoIcon(size = if (compact) 102.dp else 168.dp)
                    Spacer(Modifier.height(if (compact) 8.dp else 20.dp))
                    Text("NEXUS INPUT", color = TextPrimary, fontSize = if (compact) 20.sp else 26.sp,
                        fontWeight = FontWeight.ExtraBold, letterSpacing = 1.8.sp,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
                    Text("Gamepad to Touch", color = NexusCyan, fontSize = 16.sp,
                        fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(if (compact) 12.dp else 34.dp))
            Text("FULL CONTROL · ANY GAME", color = TextMuted, fontSize = 13.sp,
                letterSpacing = 1.4.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(if (compact) 12.dp else 28.dp))
            // Decorative edge line: no fabricated initialization percentage or probe state.
            Box(Modifier.fillMaxWidth(.66f).height(2.dp).background(
                Brush.horizontalGradient(listOf(Color.Transparent, NexusCyan, NexusVioletLight, Color.Transparent)),
                RoundedCornerShape(2.dp)))
        }
    }
}
