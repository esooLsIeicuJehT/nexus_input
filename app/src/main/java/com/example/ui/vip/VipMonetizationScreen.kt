package com.example.ui.vip

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainAppViewModel
import com.example.ui.theme.*

@Composable
fun VipMonetizationScreen(
    viewModel: MainAppViewModel
) {
    val monetization by viewModel.monetizationState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // VIP Status Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
            border = BorderStroke(1.5.dp, if (monetization.isVipActive) CyberCyan else DarkSurfaceBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                if (monetization.isVipActive) CyberCyan.copy(alpha = 0.15f) else Color.Transparent,
                                Color.Transparent
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (monetization.isVipActive) CyberCyan.copy(alpha = 0.2f) else DarkSurfaceBorder),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.WorkspacePremium,
                                    contentDescription = null,
                                    tint = if (monetization.isVipActive) CyberCyan else TextMuted,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = if (monetization.isVipActive) "VIP MEMBER ACTIVE" else "Standard Tier",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (monetization.isVipActive) CyberCyan else TextPrimary,
                                    fontSize = 17.sp
                                )
                                Text(
                                    text = "User ID: ${monetization.userId}",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (monetization.isVipActive) AccentGreen.copy(alpha = 0.15f) else Color(0xFF334155)
                        ) {
                            Text(
                                text = if (monetization.isVipActive) "UNLIMITED" else "FREE",
                                color = if (monetization.isVipActive) AccentGreen else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "VIP unlocks: zero-latency direct kernel injection, unlimited cloud config backups, custom crosshair imports, and zero interstitial ads.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.monetizationManager.purchaseVipMonthly() },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Unlock 30 Days VIP for $4.99", color = Color(0xFF00363D), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // Ad-Coin Economy Card (SSV Verified)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkSurfaceBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = AccentAmber)
                        Spacer(Modifier.width(10.dp))
                        Text("Ad-Coin Economy (SSV Verified)", fontWeight = FontWeight.Bold, color = TextPrimary)
                    }

                    Text(
                        text = "${monetization.coinBalance} / 250 Coins",
                        color = AccentAmber,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Watch short sponsored partner streams with Server-Side Verification (SSV) callback to prevent timer manipulation. Earn 5–10 coins per view; auto-grants 7 days of VIP at 250 coins.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                Spacer(Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { (monetization.coinBalance / 250f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = AccentAmber,
                    trackColor = DarkSurfaceBorder
                )

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = {
                        viewModel.monetizationManager.verifyRewardedAdCompletion(
                            adDurationSec = 30,
                            callbackToken = "ssv_token_${System.currentTimeMillis()}"
                        ) { earned ->
                            viewModel.showSnack("Verified SSV Reward: +$earned Coins!")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayCircleOutline, contentDescription = null, tint = Color(0xFF332000), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Watch Ad for 5–10 Coins", color = Color(0xFF332000), fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // Referrals & Cumulative Playtime Tracking Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkSurfaceBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GroupAdd, contentDescription = null, tint = ElectricViolet)
                        Spacer(Modifier.width(10.dp))
                        Text("Friend Referral Rewards", fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Text(
                        text = "${monetization.successfulReferralCount} Referrals",
                        color = ElectricViolet,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Threshold: ${monetization.targetThresholdHours.toInt()}h cumulative in-game time. When an invited friend crosses this milestone (tracked via server heartbeat), you both receive 7 days VIP.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                Spacer(Modifier.height(14.dp))

                // Referral Code Box
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurfaceElevated,
                    border = BorderStroke(1.dp, DarkSurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Your Invite Code", color = TextMuted, fontSize = 11.sp)
                            Text(monetization.referralCode, color = CyberCyan, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Controlyst Referral", monetization.referralCode))
                                viewModel.showSnack("Invite code copied!")
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Simulate Friend Progress Ping button
                OutlinedButton(
                    onClick = {
                        viewModel.monetizationManager.addInvitedUserHours(12.0f)
                        viewModel.showSnack("Simulated server ping: Friend logged +12h play time!")
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricViolet)
                ) {
                    Text("Simulate Friend Session (+12h)")
                }
            }
        }
    }
}
