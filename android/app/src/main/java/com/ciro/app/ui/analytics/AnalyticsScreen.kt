package com.ciro.app.ui.analytics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciro.app.data.model.PipelineMetric
import com.ciro.app.ui.theme.CiroColors
import com.ciro.app.viewmodel.PipelineStageBreakdown
import com.ciro.app.viewmodel.SpeedComparisonItem

/**
 * Analytics dashboard — showcases CIRO AI pipeline performance.
 * The "wow" screen for hackathon judges.
 */
@Composable
fun AnalyticsScreen(
    metrics: List<PipelineMetric>,
    stageBreakdown: PipelineStageBreakdown,
    accuracyRate: Float,
    speedComparison: List<SpeedComparisonItem>,
    avgLatencyMs: Long,
    falsePositiveCount: Int,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CiroColors.Surface)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(Modifier.height(12.dp)) }

        // ── Header ───────────────────────────────────────────────
        item {
            Text(
                text = "PIPELINE ANALYTICS",
                color = CiroColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "AI performance metrics",
                color = CiroColors.TextSecondary,
                fontSize = 13.sp,
            )
        }

        // ── Hero: Speed Comparison ───────────────────────────────
        item {
            HeroSpeedCard(avgLatencyMs = avgLatencyMs, totalRuns = metrics.size)
        }

        // ── Pipeline Stage Breakdown ─────────────────────────────
        item {
            StageBreakdownCard(breakdown = stageBreakdown)
        }

        // ── Accuracy + False Positives ───────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AccuracyCard(rate = accuracyRate, modifier = Modifier.weight(1f))
                FalsePositiveCard(count = falsePositiveCount, total = metrics.size, modifier = Modifier.weight(1f))
            }
        }

        // ── Per-Run Speed Comparison ─────────────────────────────
        if (speedComparison.isNotEmpty()) {
            item {
                Text(
                    text = "CIRO vs MANUAL — PER RUN",
                    color = CiroColors.TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
            }
            items(speedComparison) { item ->
                SpeedComparisonRow(item = item)
            }
        }

        // ── Empty state ──────────────────────────────────────────
        if (metrics.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("📊", fontSize = 32.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No metrics yet", color = CiroColors.TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Pipeline data will appear after the first crisis is processed", color = CiroColors.TextMuted, fontSize = 11.sp)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ── Hero Speed Card ──────────────────────────────────────────────────────────

@Composable
private fun HeroSpeedCard(avgLatencyMs: Long, totalRuns: Int) {
    val manualMs = 600_000L
    val ratio = if (avgLatencyMs > 0) manualMs.toFloat() / avgLatencyMs else 0f

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            CiroColors.AccentCyan.copy(alpha = 0.12f),
                            CiroColors.SurfaceCard,
                        )
                    )
                )
                .padding(24.dp),
        ) {
            Column {
                Text(
                    text = "⚡ AI RESPONSE SPEED",
                    color = CiroColors.AccentCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                )
                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    // CIRO time
                    Column {
                        Text("CIRO", color = CiroColors.AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = formatMs(avgLatencyMs),
                            color = CiroColors.AccentCyan,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                        )
                    }

                    // vs
                    Text(
                        text = "vs",
                        color = CiroColors.TextMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )

                    // Manual time
                    Column(horizontalAlignment = Alignment.End) {
                        Text("MANUAL", color = CiroColors.AccentRed.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "10 min",
                            color = CiroColors.AccentRed.copy(alpha = 0.6f),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Ratio badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${String.format("%.0f", ratio)}× faster",
                        color = CiroColors.AccentGreen,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .background(
                                CiroColors.AccentGreen.copy(alpha = 0.1f),
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "across $totalRuns pipeline runs",
                        color = CiroColors.TextMuted,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

// ── Stage Breakdown ──────────────────────────────────────────────────────────

@Composable
private fun StageBreakdownCard(breakdown: PipelineStageBreakdown) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "PIPELINE STAGES",
                color = CiroColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(14.dp))

            val total = (breakdown.avgDetectionMs + breakdown.avgAllocationMs + breakdown.avgNotificationMs).coerceAtLeast(1)

            StageBar("🔍 Detection", breakdown.avgDetectionMs, total, CiroColors.AccentCyan)
            Spacer(Modifier.height(10.dp))
            StageBar("🚑 Allocation", breakdown.avgAllocationMs, total, CiroColors.AccentOrange)
            Spacer(Modifier.height(10.dp))
            StageBar("📢 Notification", breakdown.avgNotificationMs, total, CiroColors.AccentGreen)
        }
    }
}

@Composable
private fun StageBar(label: String, ms: Long, totalMs: Long, color: Color) {
    val fraction = (ms.toFloat() / totalMs).coerceIn(0.02f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(800),
        label = "stage",
    )

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, color = CiroColors.TextSecondary, fontSize = 12.sp)
            Text(text = formatMs(ms), color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CiroColors.SurfaceBorder.copy(alpha = 0.2f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(color),
            )
        }
    }
}

// ── Accuracy Card ────────────────────────────────────────────────────────────

@Composable
private fun AccuracyCard(rate: Float, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("ACCURACY", color = CiroColors.TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "${String.format("%.0f", rate * 100)}%",
                color = if (rate >= 0.8f) CiroColors.AccentGreen else CiroColors.AccentOrange,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
            )
            Text("detection accuracy", color = CiroColors.TextMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun FalsePositiveCard(count: Int, total: Int, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("FALSE POSITIVES", color = CiroColors.TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$count",
                    color = if (count > 0) CiroColors.AccentOrange else CiroColors.AccentGreen,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "/$total",
                    color = CiroColors.TextMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
            Text("caught & retracted", color = CiroColors.TextMuted, fontSize = 10.sp)
        }
    }
}

// ── Per-Run Comparison ───────────────────────────────────────────────────────

@Composable
private fun SpeedComparisonRow(item: SpeedComparisonItem) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.label, color = CiroColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "${String.format("%.0f", item.ratio)}× faster",
                    color = CiroColors.AccentGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(CiroColors.AccentGreen.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(8.dp))

            // CIRO bar
            val maxMs = item.manualMs.toFloat().coerceAtLeast(1f)
            val ciroFraction by animateFloatAsState(
                targetValue = (item.ciroMs.toFloat() / maxMs).coerceIn(0.02f, 1f),
                animationSpec = tween(600),
                label = "ciro",
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("CIRO", color = CiroColors.AccentCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(50.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(CiroColors.SurfaceBorder.copy(alpha = 0.15f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ciroFraction)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(3.dp))
                            .background(CiroColors.AccentCyan),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(formatMs(item.ciroMs), color = CiroColors.TextMuted, fontSize = 9.sp, modifier = Modifier.width(44.dp))
            }

            Spacer(Modifier.height(4.dp))

            // Manual bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Manual", color = CiroColors.AccentRed.copy(alpha = 0.6f), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(50.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(CiroColors.SurfaceBorder.copy(alpha = 0.15f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(3.dp))
                            .background(CiroColors.AccentRed.copy(alpha = 0.3f)),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("10min", color = CiroColors.TextMuted, fontSize = 9.sp, modifier = Modifier.width(44.dp))
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun formatMs(ms: Long): String = when {
    ms <= 0 -> "—"
    ms < 1000 -> "${ms}ms"
    ms < 60_000 -> "${String.format("%.1f", ms / 1000.0)}s"
    else -> "${ms / 60_000}min"
}
