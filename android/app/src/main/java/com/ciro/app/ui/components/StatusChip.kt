package com.ciro.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciro.app.ui.theme.CiroColors

/**
 * Reusable colour-coded status chip.
 * Used for incident state, resource state, delivery status badges.
 */
@Composable
fun StatusChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = modifier
            .background(
                color.copy(alpha = 0.12f),
                RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * Stakeholder-type badge with emoji and label.
 */
@Composable
fun StakeholderBadge(
    stakeholderType: String,
    modifier: Modifier = Modifier,
) {
    val emoji = when (stakeholderType) {
        "public" -> "📢"
        "emergency_services" -> "🚨"
        "hospital" -> "🏥"
        "utility" -> "⚡"
        "transport" -> "🚗"
        "command_center", "media" -> "📡"
        else -> "📋"
    }
    val color = when (stakeholderType) {
        "public" -> CiroColors.AccentCyan
        "emergency_services" -> CiroColors.AccentRed
        "hospital" -> CiroColors.AccentGreen
        "utility" -> CiroColors.Severity3
        "transport" -> CiroColors.AccentOrange
        "command_center", "media" -> CiroColors.AccentPurple
        else -> CiroColors.TextSecondary
    }

    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = "$emoji ${stakeholderType.uppercase().replace("_", " ")}",
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
