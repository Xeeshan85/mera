package com.ciro.app.data.model

/**
 * News headline from GNews API / Dawn RSS.
 * Fetched via backend /api/news endpoint.
 */
data class NewsItem(
    val news_id: String = "",
    val title: String = "",
    val description: String = "",
    val source: String = "",
    val url: String = "",
    val image_url: String = "",
    val published_at: String = "",
    val urgency: String = "info",       // "critical" | "warning" | "info"
)
