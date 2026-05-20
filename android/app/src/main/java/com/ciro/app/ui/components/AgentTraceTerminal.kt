package com.ciro.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciro.app.data.model.AgentTrace
import com.ciro.app.ui.theme.CiroColors

/**
 * Dark-themed, console-like terminal panel showing live AI agent reasoning logs.
 *
 * Scrolls automatically to the latest trace entry (newest at top).
 * Each entry shows:
 *   • Agent name (emoji + coloured)
 *   • Timestamp
 *   • Decision / reasoning text
 *   • Duration in ms
 *   • Tool calls made
 *   • Confidence scores
 *   • Routing decision (for orchestrator traces)
 *
 * Uses a monospace font and green-on-dark styling to evoke a command-line feel
 * that will impress judges watching real-time AI reasoning scroll by.
 */
@Composable
fun AgentTraceTerminal(
    traces: List<AgentTrace>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Auto-scroll to top when new traces arrive (newest first)
    LaunchedEffect(traces.size) {
        if (traces.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CiroColors.SurfaceTerminal)
    ) {
        // Terminal header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CiroColors.SurfaceBorder.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Fake traffic lights
            Box(Modifier.size(10.dp).clip(CircleShape).background(CiroColors.AccentRed))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(10.dp).clip(CircleShape).background(CiroColors.AccentOrange))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(10.dp).clip(CircleShape).background(CiroColors.AccentGreen))
            Spacer(Modifier.width(12.dp))
            Text(
                text = "ciro-agent-traces",
                color = CiroColors.TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${traces.size} entries",
                color = CiroColors.TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }

        // Trace entries
        if (traces.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$ waiting for agent traces..._",
                    color = CiroColors.TextTerminalGreen.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                items(traces, key = { it.trace_id }) { trace ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically(),
                    ) {
                        TraceEntry(trace = trace)
                    }
                }
            }
        }
    }
}

@Composable
private fun TraceEntry(trace: AgentTrace) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        // Line 1: timestamp + agent name
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatTimestamp(trace.timestamp),
                color = CiroColors.TextMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = trace.agentDisplayName,
                color = agentColor(trace.agent),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.weight(1f))
            if (trace.duration_ms > 0) {
                Text(
                    text = "${trace.duration_ms}ms",
                    color = if (trace.duration_ms < 5000) CiroColors.AccentGreen else CiroColors.AccentOrange,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .background(CiroColors.Surface, RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }

        // Line 2: decision / main output
        if (trace.decision.isNotBlank()) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = CiroColors.TextTerminalGreen)) {
                        append("→ ")
                    }
                    withStyle(SpanStyle(color = CiroColors.TextPrimary)) {
                        append(trace.decision)
                    }
                },
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }

        // Line 3: routing decision (orchestrator traces only)
        trace.routing_decision?.let { routing ->
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = CiroColors.AccentCyan)) {
                        append("  routing: ")
                    }
                    withStyle(SpanStyle(color = routingColor(routing), fontWeight = FontWeight.Bold)) {
                        append(routing)
                    }
                },
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        // Line 4: Gemini reasoning (if available)
        trace.gemini_reasoning?.takeIf { it.isNotBlank() }?.let { reasoning ->
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = CiroColors.AccentPurple)) {
                        append("  🧠 ")
                    }
                    withStyle(SpanStyle(color = CiroColors.TextSecondary)) {
                        append(reasoning.take(200))
                        if (reasoning.length > 200) append("…")
                    }
                },
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 14.sp,
                modifier = Modifier.padding(start = 4.dp, top = 1.dp),
            )
        }

        // Line 5: tool calls summary
        if (trace.tool_calls.isNotEmpty()) {
            val toolSummary = trace.tool_calls.mapNotNull { it["tool"] as? String }.joinToString(", ")
            Text(
                text = "  tools: [$toolSummary]",
                color = CiroColors.TextMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        // Line 6: trade-off narrative (resource allocation)
        trace.trade_off_narrative?.takeIf { it.isNotBlank() }?.let { narrative ->
            Text(
                text = "  ⚖️ $narrative",
                color = CiroColors.AccentOrange,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 13.sp,
                modifier = Modifier.padding(start = 4.dp, top = 1.dp),
            )
        }

        // Line 7: fallbacks triggered
        if (trace.fallbacks_triggered.isNotEmpty()) {
            Text(
                text = "  ⚠ fallbacks: ${trace.fallbacks_triggered.joinToString(", ")}",
                color = CiroColors.AccentRed,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = CiroColors.SurfaceBorder.copy(alpha = 0.3f), thickness = 0.5.dp)
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

/** Extract HH:mm:ss from an ISO8601 timestamp string. */
private fun formatTimestamp(iso: String): String {
    return try {
        // ISO format: 2026-05-20T08:32:01.123456
        val timePart = iso.substringAfter("T").substringBefore(".")
        timePart.take(8) // HH:mm:ss
    } catch (e: Exception) {
        iso.takeLast(8)
    }
}

/** Distinct colour per agent for visual differentiation. */
private fun agentColor(agent: String): androidx.compose.ui.graphics.Color = when (agent) {
    "crisis_detection_agent" -> CiroColors.AccentRed
    "severity_prediction_agent" -> CiroColors.AccentOrange
    "resource_allocation_agent" -> CiroColors.AccentCyan
    "stakeholder_notification_agent" -> CiroColors.AccentGreen
    "orchestrator_agent" -> CiroColors.AccentPurple
    "signal_fusion_agent" -> CiroColors.Severity3
    else -> CiroColors.TextSecondary
}

/** Colour-code routing decisions for visual impact. */
private fun routingColor(routing: String): androidx.compose.ui.graphics.Color = when (routing) {
    "FULL_RESPONSE" -> CiroColors.AccentRed
    "VERIFICATION_REQUESTED" -> CiroColors.AccentOrange
    "HYPOTHESIS" -> CiroColors.Severity3
    "MONITORING" -> CiroColors.TextMuted
    "DETECTION_FAILED", "PIPELINE_ERROR" -> CiroColors.AccentRed
    else -> CiroColors.TextSecondary
}
