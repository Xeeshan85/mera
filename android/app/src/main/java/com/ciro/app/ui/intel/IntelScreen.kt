package com.ciro.app.ui.intel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciro.app.data.model.IntelligenceSnapshot
import com.ciro.app.data.model.NewsItem
import com.ciro.app.ui.components.SparklineChart
import com.ciro.app.ui.theme.CiroColors

/**
 * Intel Screen — Signal Intelligence Center (WorldMonitor-inspired).
 * Shows mention velocity, sentiment, credibility, news feed,
 * source health, and trending keywords.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntelScreen(
    intelligence: IntelligenceSnapshot,
    news: List<NewsItem>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CiroColors.Surface)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Spacer(Modifier.height(12.dp)) }

        // ── Header ───────────────────────────────────────────────
        item {
            Column {
                Text(
                    text = "SIGNAL INTELLIGENCE",
                    color = CiroColors.TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(
                    text = "Monitoring ${intelligence.total_signals} signals across Pakistan",
                    color = CiroColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }

        // ── Mention Velocity ─────────────────────────────────────
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "MENTION VELOCITY",
                            color = CiroColors.TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = when (intelligence.mention_velocity.trend) {
                                    "rising" -> "↑ RISING"
                                    "falling" -> "↓ FALLING"
                                    else -> "→ STABLE"
                                },
                                color = when (intelligence.mention_velocity.trend) {
                                    "rising" -> CiroColors.AccentRed
                                    "falling" -> CiroColors.AccentGreen
                                    else -> CiroColors.TextMuted
                                },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            if (intelligence.mention_velocity.is_spike) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "SPIKE",
                                    color = CiroColors.AccentRed,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier
                                        .background(CiroColors.AccentRed.copy(alpha = 0.1f), RoundedCornerShape(3.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    SparklineChart(
                        values = intelligence.mention_velocity.buckets.map { it.count },
                        lineColor = if (intelligence.mention_velocity.is_spike) CiroColors.AccentRed else CiroColors.AccentCyan,
                        height = 50.dp,
                        showDots = true,
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Current: ${intelligence.mention_velocity.current_rate}/15min",
                            color = CiroColors.TextMuted,
                            fontSize = 9.sp,
                        )
                        Text(
                            text = "Avg: ${intelligence.mention_velocity.average_rate}/15min",
                            color = CiroColors.TextMuted,
                            fontSize = 9.sp,
                        )
                    }
                }
            }
        }

        // ── Sentiment + Credibility ──────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Sentiment card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
                    modifier = Modifier.weight(1f),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "SENTIMENT",
                            color = CiroColors.TextMuted,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = when (intelligence.sentiment.label) {
                                "critical" -> "😰"
                                "negative" -> "😟"
                                else -> "😐"
                            },
                            fontSize = 28.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${intelligence.sentiment.negative_pct}% negative",
                            color = when (intelligence.sentiment.label) {
                                "critical" -> CiroColors.AccentRed
                                "negative" -> CiroColors.AccentOrange
                                else -> CiroColors.TextSecondary
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = intelligence.sentiment.label.uppercase(),
                            color = CiroColors.TextMuted,
                            fontSize = 9.sp,
                        )
                    }
                }

                // Credibility card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
                    modifier = Modifier.weight(1f),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "CREDIBILITY",
                            color = CiroColors.TextMuted,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        // Star rating
                        Text(
                            text = "★".repeat(intelligence.credibility.stars) + "☆".repeat(5 - intelligence.credibility.stars),
                            color = CiroColors.AccentOrange,
                            fontSize = 20.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = String.format("%.1f / 5.0", intelligence.credibility.score * 5),
                            color = CiroColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${intelligence.credibility.verified_count} verified",
                            color = CiroColors.TextMuted,
                            fontSize = 9.sp,
                        )
                    }
                }
            }
        }

        // ── Source Health ─────────────────────────────────────────
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "SIGNAL SOURCES",
                        color = CiroColors.TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        intelligence.source_health.forEach { source ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (source.status) {
                                                "active" -> CiroColors.AccentGreen
                                                "degraded" -> CiroColors.AccentOrange
                                                else -> CiroColors.TextMuted.copy(alpha = 0.3f)
                                            }
                                        ),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = source.source.take(7).uppercase(),
                                    color = CiroColors.TextSecondary,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "${source.signal_count}",
                                    color = CiroColors.TextMuted,
                                    fontSize = 8.sp,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Trending Keywords ────────────────────────────────────
        if (intelligence.trending_keywords.isNotEmpty()) {
            item {
                Text(
                    text = "TRENDING KEYWORDS",
                    color = CiroColors.TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    intelligence.trending_keywords.forEach { kw ->
                        val isCrisis = kw.is_crisis
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isCrisis) CiroColors.AccentRed.copy(alpha = 0.08f)
                                    else CiroColors.SurfaceCard,
                                )
                                .border(
                                    1.dp,
                                    if (isCrisis) CiroColors.AccentRed.copy(alpha = 0.2f)
                                    else CiroColors.SurfaceBorder.copy(alpha = 0.15f),
                                    RoundedCornerShape(12.dp),
                                )
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = kw.keyword,
                                color = if (isCrisis) CiroColors.AccentRed else CiroColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isCrisis) FontWeight.Bold else FontWeight.Normal,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${kw.count}",
                                color = CiroColors.TextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        // ── Live News Feed ───────────────────────────────────────
        if (news.isNotEmpty()) {
            item {
                Text(
                    text = "PAKISTAN NEWS FEED",
                    color = CiroColors.TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
            }
            items(news.take(8), key = { it.news_id }) { article ->
                NewsRow(article)
            }
        }

        // ── Empty state ──────────────────────────────────────────
        if (intelligence.total_signals == 0 && news.isEmpty()) {
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
                        Text("📡", fontSize = 32.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Initializing signal intelligence...", color = CiroColors.TextSecondary, fontSize = 13.sp)
                        Text("Data will appear as signals are processed", color = CiroColors.TextMuted, fontSize = 10.sp)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun NewsRow(article: NewsItem) {
    val urgencyColor = when (article.urgency) {
        "critical" -> CiroColors.AccentRed
        "warning" -> CiroColors.AccentOrange
        else -> CiroColors.AccentGreen
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CiroColors.SurfaceCard)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(urgencyColor),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = article.title,
                color = CiroColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(4.dp))
            Row {
                Text(
                    text = article.source,
                    color = CiroColors.AccentCyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatNewsTime(article.published_at),
                    color = CiroColors.TextMuted,
                    fontSize = 9.sp,
                )
            }
        }
    }
}

private fun formatNewsTime(iso: String): String {
    return try {
        val timePart = iso.substringAfter("T").substringBefore("Z").substringBefore("+").take(5)
        timePart
    } catch (_: Exception) {
        iso.takeLast(5)
    }
}
