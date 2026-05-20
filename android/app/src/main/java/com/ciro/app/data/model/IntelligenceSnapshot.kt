package com.ciro.app.data.model

/**
 * Signal intelligence snapshot from the OSINT analysis.
 * Firestore collection: live_intelligence (document: "latest")
 */
data class IntelligenceSnapshot(
    val snapshot_id: String = "",
    val timestamp: String = "",
    val total_signals: Int = 0,
    val mention_velocity: MentionVelocity = MentionVelocity(),
    val sentiment: Sentiment = Sentiment(),
    val credibility: Credibility = Credibility(),
    val trending_keywords: List<TrendingKeyword> = emptyList(),
    val source_health: List<SourceHealth> = emptyList(),
    val signal_timeline: List<SignalTimelineEntry> = emptyList(),
)

data class MentionVelocity(
    val buckets: List<VelocityBucket> = emptyList(),
    val current_rate: Int = 0,
    val average_rate: Double = 0.0,
    val is_spike: Boolean = false,
    val trend: String = "stable",       // "rising" | "falling" | "stable"
)

data class VelocityBucket(
    val bucket: String = "",
    val count: Int = 0,
)

data class Sentiment(
    val score: Double = 0.5,
    val label: String = "neutral",      // "critical" | "negative" | "neutral"
    val negative_pct: Int = 0,
)

data class Credibility(
    val score: Double = 0.0,
    val stars: Int = 0,
    val verified_count: Int = 0,
    val total_sources: Int = 0,
)

data class TrendingKeyword(
    val keyword: String = "",
    val count: Int = 0,
    val is_crisis: Boolean = false,
)

data class SourceHealth(
    val source: String = "",
    val status: String = "inactive",    // "active" | "degraded" | "inactive"
    val signal_count: Int = 0,
    val last_signal: String = "",
)

data class SignalTimelineEntry(
    val time: String = "",
    val source: String = "",
    val area: String = "",
    val credibility: Double = 0.0,
    val urgency: Double = 0.0,
)
