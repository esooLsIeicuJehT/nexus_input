package com.example.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class NexusButtonVariant { Primary, Secondary, Ghost, Danger }
enum class NexusStatusTone { Neutral, Ready, Warning, Error }

@Composable
fun NexusGlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = 18.dp,
    accent: Color = LocalNexusAccent.current,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.nexusGlass(radius, accent).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
fun NexusNeonButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: NexusButtonVariant = NexusButtonVariant.Secondary,
    content: @Composable RowScope.() -> Unit
) {
    val accent = if (variant == NexusButtonVariant.Danger) NexusRed else LocalNexusAccent.current
    val filled = variant == NexusButtonVariant.Primary
    val ghost = variant == NexusButtonVariant.Ghost
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = if (ghost) null else BorderStroke(1.dp, accent.copy(alpha = if (enabled) .65f else .18f)),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (filled) accent else accent.copy(alpha = if (ghost) .025f else .09f),
            contentColor = if (filled) GraphiteFoundation else accent,
            disabledContainerColor = Color.White.copy(alpha = .025f),
            disabledContentColor = TextMuted
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        content = content
    )
}

/** Tone is supplied from observed state; this component never infers readiness. */
@Composable
fun NexusStatusChip(
    text: String,
    tone: NexusStatusTone = NexusStatusTone.Neutral,
    modifier: Modifier = Modifier
) {
    val color = when (tone) {
        NexusStatusTone.Neutral -> LocalNexusAccent.current
        NexusStatusTone.Ready -> NexusGreen
        NexusStatusTone.Warning -> NexusOrange
        NexusStatusTone.Error -> NexusRed
    }
    Surface(
        modifier = modifier,
        color = color.copy(alpha = .09f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, color.copy(alpha = .45f))
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}
