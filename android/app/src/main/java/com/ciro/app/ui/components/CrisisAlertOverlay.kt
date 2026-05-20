package com.ciro.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciro.app.data.model.Incident
import com.ciro.app.ui.theme.CiroColors
import com.ciro.app.util.IntentUtils

/**
 * Full-screen crisis alert — designed for urgency.
 * Compact layout, pulsing red glow, immediate action.
 */
@Composable
fun CrisisAlertOverlay(
    incident: Incident?,
    visible: Boolean,
    onDismiss: () -> Unit,
    onViewDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible && incident != null,
        enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.9f, animationSpec = tween(250)),
        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.9f, animationSpec = tween(150)),
        modifier = modifier,
    ) {
        incident?.let { inc ->
            val context = LocalContext.current
            val pulse = rememberInfiniteTransition(label = "pulse")
            val glowAlpha by pulse.animateFloat(
                initialValue = 0.3f, targetValue = 0.8f,
                animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
                label = "glow",
            )
            val iconScale by pulse.animateFloat(
                initialValue = 1f, targetValue = 1.08f,
                animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
                label = "scale",
            )

            val severityColor = CiroColors.severityColor(inc.severity_level)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CiroColors.SurfaceOverlay)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    CiroColors.AccentRed.copy(alpha = 0.08f),
                                    CiroColors.SurfaceCard,
                                    CiroColors.SurfaceCard,
                                )
                            )
                        )
                        .border(
                            2.dp,
                            CiroColors.AccentRed.copy(alpha = glowAlpha),
                            RoundedCornerShape(24.dp),
                        )
                        .clickable(enabled = false, onClick = {})
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // ── Red alert badge ──────────────────────────────
                    Text(
                        text = "⚠️ CRISIS DETECTED",
                        color = CiroColors.AccentRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        modifier = Modifier
                            .background(
                                CiroColors.AccentRed.copy(alpha = 0.1f),
                                RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )

                    Spacer(Modifier.height(18.dp))

                    // ── Icon with pulsing glow ───────────────────────
                    Box(contentAlignment = Alignment.Center) {
                        // Outer glow
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .scale(iconScale * 1.15f)
                                .clip(CircleShape)
                                .background(severityColor.copy(alpha = glowAlpha * 0.12f))
                        )
                        // Inner circle
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .scale(iconScale)
                                .clip(CircleShape)
                                .background(severityColor.copy(alpha = 0.1f))
                                .border(2.dp, severityColor.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = CiroColors.crisisTypeIcon(inc.crisis_type),
                                fontSize = 32.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // ── Crisis type ──────────────────────────────────
                    Text(
                        text = inc.crisis_type.uppercase().replace("_", " "),
                        color = CiroColors.TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = inc.location.area_name.ifBlank { "Islamabad" },
                        color = CiroColors.TextSecondary,
                        fontSize = 13.sp,
                    )

                    Spacer(Modifier.height(18.dp))

                    // ── Key metrics row ──────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CiroColors.Surface.copy(alpha = 0.5f))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        MetricItem(
                            value = "SEV ${inc.severity_level}",
                            label = "Severity",
                            color = severityColor,
                        )
                        // Vertical divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(32.dp)
                                .background(CiroColors.SurfaceBorder.copy(alpha = 0.3f))
                        )
                        MetricItem(
                            value = "${String.format("%.0f", inc.confidence_score * 100)}%",
                            label = "Confidence",
                            color = CiroColors.AccentCyan,
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(32.dp)
                                .background(CiroColors.SurfaceBorder.copy(alpha = 0.3f))
                        )
                        MetricItem(
                            value = formatPop(inc.affected_population_estimate),
                            label = "Affected",
                            color = CiroColors.AccentOrange,
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // ── Action buttons ────────────────────────────────
                    Button(
                        onClick = { onViewDetails(inc.incident_id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CiroColors.AccentRed,
                            contentColor = CiroColors.TextPrimary,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = "VIEW INCIDENT DETAILS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            letterSpacing = 1.sp,
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Navigate Now
                    Button(
                        onClick = {
                            IntentUtils.openNavigation(
                                context, inc.location.lat, inc.location.lng,
                                inc.location.area_name,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CiroColors.AccentGreen,
                            contentColor = CiroColors.Surface,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Navigation, "Navigate", modifier = androidx.compose.ui.Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "NAVIGATE NOW",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp,
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, CiroColors.SurfaceBorder.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = "Acknowledge",
                            color = CiroColors.TextSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricItem(value: String, label: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = color,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = CiroColors.TextMuted,
            fontSize = 10.sp,
        )
    }
}

private fun formatPop(pop: Int): String = when {
    pop >= 1_000_000 -> "${pop / 1_000_000}M"
    pop >= 1_000 -> "${pop / 1_000}K"
    else -> "$pop"
}
