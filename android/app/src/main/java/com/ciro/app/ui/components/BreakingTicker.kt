package com.ciro.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciro.app.data.model.LiveUpdate
import com.ciro.app.ui.theme.CiroColors

/**
 * Auto-scrolling breaking news ticker with pulsing red dot for breaking items.
 * Uses basicMarquee for continuous horizontal scrolling of each headline,
 * plus AnimatedContent for smooth transitions between headlines.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BreakingTicker(
    updates: List<LiveUpdate>,
    currentIndex: Int,
    onTap: (LiveUpdate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (updates.isEmpty()) return

    val current = updates[currentIndex.coerceIn(0, updates.lastIndex)]
    val isBreaking = current.is_breaking

    val pulse = rememberInfiniteTransition(label = "ticker_pulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "dot",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isBreaking) CiroColors.AccentRed.copy(alpha = 0.08f)
                else CiroColors.SurfaceCard,
            )
            .clickable { onTap(current) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Pulsing indicator dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (isBreaking) CiroColors.AccentRed.copy(alpha = dotAlpha)
                    else CiroColors.AccentOrange.copy(alpha = 0.6f),
                ),
        )
        Spacer(Modifier.width(8.dp))

        // Label
        Text(
            text = if (isBreaking) "BREAKING" else "UPDATE",
            color = if (isBreaking) CiroColors.AccentRed else CiroColors.AccentOrange,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.width(8.dp))

        // Headline text — AnimatedContent slides between headlines,
        // basicMarquee scrolls long text horizontally within each headline.
        AnimatedContent(
            targetState = currentIndex,
            transitionSpec = {
                slideInHorizontally { width -> width } togetherWith
                    slideOutHorizontally { width -> -width }
            },
            modifier = Modifier.weight(1f),
            label = "ticker_headline",
        ) { idx ->
            val update = updates.getOrElse(idx) { current }
            Text(
                text = update.headline,
                color = CiroColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    velocity = 40.dp,
                ),
            )
        }

        // Severity badge
        if (current.severity_level > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "S${current.severity_level}",
                color = CiroColors.severityColor(current.severity_level),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(
                        CiroColors.severityColor(current.severity_level).copy(alpha = 0.1f),
                        RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}
