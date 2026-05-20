package com.ciro.app.ui.components

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Animated number counter — smoothly counts up/down when the target value changes.
 *
 * Used for severity counts, resource numbers, latency values on the dashboard.
 */
@Composable
fun AnimatedCounter(
    targetValue: Int,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    fontSize: TextUnit = 24.sp,
    fontWeight: FontWeight = FontWeight.Black,
    durationMs: Int = 600,
) {
    val animatedValue by animateIntAsState(
        targetValue = targetValue,
        animationSpec = tween(durationMillis = durationMs),
        label = "counter",
    )

    Text(
        text = "$animatedValue",
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        modifier = modifier,
    )
}
