package com.ciro.app.ui.brain

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciro.app.data.model.AgentTrace
import com.ciro.app.ui.theme.CiroColors

/**
 * AI Brain — Agent execution timeline with full transparency.
 * Shows what each Gemini-powered agent decided and why.
 */
@Composable
fun AIBrainScreen(
    traces: List<AgentTrace>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CiroColors.Surface)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(12.dp)) }

        // ── Header ───────────────────────────────────────────────
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🧠", fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "AI BRAIN",
                        color = CiroColors.TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                    Text(
                        text = "${traces.size} agent executions",
                        color = CiroColors.TextSecondary,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        // ── Agent Summary ────────────────────────────────────────
        if (traces.isNotEmpty()) {
            item {
                val agentCounts = traces.groupBy { it.agent }.mapValues { it.value.size }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    agentCounts.entries.take(4).forEach { (agent, count) ->
                        AgentCountChip(
                            emoji = agentEmoji(agent),
                            name = agentShortName(agent),
                            count = count,
                            color = agentColor(agent),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // ── Timeline ─────────────────────────────────────────────
        if (traces.isEmpty()) {
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
                        Text("🧠", fontSize = 32.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No traces yet", color = CiroColors.TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Agent executions will appear here", color = CiroColors.TextMuted, fontSize = 11.sp)
                    }
                }
            }
        } else {
            items(traces, key = { it.trace_id }) { trace ->
                AgentTraceCard(trace = trace)
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ── Agent Count Chip ─────────────────────────────────────────────────────────

@Composable
private fun AgentCountChip(
    emoji: String,
    name: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(emoji, fontSize = 16.sp)
            Text("$count", color = color, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text(name, color = CiroColors.TextMuted, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ── Agent Trace Card ─────────────────────────────────────────────────────────

@Composable
private fun AgentTraceCard(trace: AgentTrace) {
    var expanded by remember { mutableStateOf(false) }
    val color = agentColor(trace.agent)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
        modifier = Modifier.clickable { expanded = !expanded },
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left accent
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(if (expanded) 200.dp else 72.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                    .background(color),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp),
            ) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Agent emoji circle
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(agentEmoji(trace.agent), fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = trace.agentDisplayName,
                            color = CiroColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row {
                            Text(
                                text = fmtTime(trace.timestamp),
                                color = CiroColors.TextMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                            if (trace.duration_ms > 0) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${trace.duration_ms}ms",
                                    color = color,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand",
                        tint = CiroColors.TextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }

                // Decision preview
                if (trace.decision.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "→ ${trace.decision}",
                        color = CiroColors.AccentGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Expanded content
                AnimatedVisibility(visible = expanded) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        // Reasoning
                        if (!trace.gemini_reasoning.isNullOrBlank()) {
                            Text(
                                text = "GEMINI REASONING",
                                color = CiroColors.TextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = trace.gemini_reasoning!!,
                                color = CiroColors.TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CiroColors.SurfaceTerminal)
                                    .padding(10.dp),
                            )
                        }

                        // Tool calls
                        if (trace.tool_calls.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "TOOL CALLS (${trace.tool_calls.size})",
                                color = CiroColors.TextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            )
                            trace.tool_calls.forEach { call ->
                                val toolName = call["tool"] as? String ?: call["name"] as? String ?: "unknown"
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "  ⚙️ $toolName",
                                    color = CiroColors.AccentPurple,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }

                        // Input summary
                        if (trace.input_summary.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "INPUT",
                                color = CiroColors.TextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = trace.input_summary,
                                color = CiroColors.TextSecondary,
                                fontSize = 10.sp,
                                lineHeight = 15.sp,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        // Trade-off narrative
                        if (!trace.trade_off_narrative.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "⚖️ ${trace.trade_off_narrative}",
                                color = CiroColors.AccentOrange,
                                fontSize = 10.sp,
                                lineHeight = 15.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun agentEmoji(agent: String): String = when (agent) {
    "crisis_detection_agent" -> "🔍"
    "severity_prediction_agent" -> "📈"
    "resource_allocation_agent" -> "🚑"
    "stakeholder_notification_agent" -> "📢"
    "orchestrator_agent" -> "🧠"
    "signal_fusion_agent" -> "📡"
    else -> "🤖"
}

private fun agentShortName(agent: String): String = when (agent) {
    "crisis_detection_agent" -> "Detect"
    "severity_prediction_agent" -> "Predict"
    "resource_allocation_agent" -> "Allocate"
    "stakeholder_notification_agent" -> "Notify"
    "orchestrator_agent" -> "Brain"
    "signal_fusion_agent" -> "Fusion"
    else -> agent.take(6)
}

private fun agentColor(agent: String): Color = when (agent) {
    "crisis_detection_agent" -> CiroColors.AccentCyan
    "severity_prediction_agent" -> CiroColors.AccentOrange
    "resource_allocation_agent" -> CiroColors.AccentGreen
    "stakeholder_notification_agent" -> CiroColors.AccentGold
    "orchestrator_agent" -> CiroColors.AccentPurple
    "signal_fusion_agent" -> CiroColors.AccentTeal
    else -> CiroColors.TextSecondary
}

private fun fmtTime(iso: String): String = try {
    iso.substringAfter("T").substringBefore(".").take(8)
} catch (_: Exception) { iso.takeLast(8) }
