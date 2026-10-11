package com.example.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Source-compatible entry point for the Nexus N + gamepad vector in reference 02.
 * The former rotating hub is removed. The emblem stays crisp and static while gaming.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun ControlystLogoIcon(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    animated: Boolean = false
) {
    Image(
        painter = painterResource(R.drawable.nexus_logo),
        contentDescription = "Nexus Input",
        modifier = modifier.size(size)
    )
}

/** Legacy callers keep their source contract while presenting Nexus branding. */
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
        ControlystLogoIcon(size = iconSize)
        Spacer(Modifier.width(10.dp))
        Text(
            text = "NEXUS INPUT",
            color = TextPrimary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = textSize.value.sp,
            letterSpacing = letterSpacingSp.sp
        )
    }
}
