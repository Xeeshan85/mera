package com.ciro.app.data.model

/**
 * Kotlin data class mirroring the Firestore `agent_traces` collection.
 * Each trace represents one agent's execution within a pipeline run.
 */
data class AgentTrace(
    val trace_id: String = "",
    val agent: String = "",
    val pipeline_run_id: String? = null,
    val incident_id: String? = null,
    val timestamp: String = "",
    val input_summary: String = "",
    val tool_calls: List<Map<String, Any>> = emptyList(),
    val gemini_reasoning: String? = null,
    val decision: String = "",
    val confidence_scores: Map<String, Any> = emptyMap(),
    val state_transition: Map<String, Any>? = null,
    val trade_off_narrative: String? = null,
    val duration_ms: Long = 0,
    // Orchestrator-level trace fields
    val stages: List<Map<String, Any>> = emptyList(),
    val confidence_at_detection: Double = 0.0,
    val routing_decision: String? = null,
    val resource_trade_off_narrative: String? = null,
    val actions_simulated: List<Map<String, Any>> = emptyList(),
    val fallbacks_triggered: List<String> = emptyList(),
) {
    /** Human-friendly agent name for display. */
    val agentDisplayName: String
        get() = when (agent) {
            "crisis_detection_agent" -> "🔍 Crisis Detection"
            "severity_prediction_agent" -> "📈 Severity Prediction"
            "resource_allocation_agent" -> "🚑 Resource Allocation"
            "stakeholder_notification_agent" -> "📢 Stakeholder Notification"
            "orchestrator_agent" -> "🧠 Orchestrator"
            "signal_fusion_agent" -> "📡 Signal Fusion"
            else -> "🤖 ${agent.replaceFirstChar { it.uppercase() }}"
        }
}

/**
 * Kotlin data class mirroring the Firestore `metrics` collection.
 */
data class PipelineMetric(
    val metric_id: String = "",
    val incident_id: String = "",
    val pipeline_run_id: String = "",
    val signal_to_detection_ms: Long = 0,
    val detection_to_allocation_ms: Long = 0,
    val allocation_to_notification_ms: Long = 0,
    val total_end_to_end_ms: Long = 0,
    val agents_invoked: List<String> = emptyList(),
    val api_calls_made: Map<String, Any> = emptyMap(),
    val fallbacks_triggered: List<String> = emptyList(),
    val false_positive: Boolean = false,
    val recorded_at: String = "",
    val manual_benchmark_ms: Long = 600_000, // 10 min default
    val improvement_ratio: Double = 0.0,
)
