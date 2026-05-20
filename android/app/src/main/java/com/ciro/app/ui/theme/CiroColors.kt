package com.ciro.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * CIRO brand colour palette — dark command-center aesthetic.
 *
 * Severity levels follow the spec:
 *   5 = red, 4 = orange, 3 = yellow, 2 = blue, 1 = green
 */
object CiroColors {
    // ── Background tiers ─────────────────────────────────────────────
    val Surface = Color(0xFF0D1117)        // GitHub-dark background
    val SurfaceCard = Color(0xFF161B22)    // Elevated card
    val SurfaceElevated = Color(0xFF1C2128) // Higher elevation
    val SurfaceTerminal = Color(0xFF0A0E14) // Terminal/console bg
    val SurfaceBorder = Color(0xFF30363D)  // Subtle border
    val SurfaceGlass = Color(0xFF161B22).copy(alpha = 0.7f)  // Glassmorphism
    val SurfaceOverlay = Color(0xFF000000).copy(alpha = 0.6f) // Modal overlay

    // ── Accent ───────────────────────────────────────────────────────
    val AccentCyan = Color(0xFF58A6FF)     // Primary action
    val AccentGreen = Color(0xFF3FB950)    // Success / available
    val AccentOrange = Color(0xFFF0883E)   // Warning / shadow commit
    val AccentRed = Color(0xFFF85149)      // Critical / dispatched
    val AccentPurple = Color(0xFFBC8CFF)   // AI agent / trace
    val AccentGold = Color(0xFFE3B341)     // Premium / highlight
    val AccentTeal = Color(0xFF2EA043)     // Confirmation

    // ── Pakistan Flag Colors ─────────────────────────────────────────
    val PakistanGreen = Color(0xFF01411C)
    val PakistanWhite = Color(0xFFFFFFFF)

    // ── Text ─────────────────────────────────────────────────────────
    val TextPrimary = Color(0xFFE6EDF3)
    val TextSecondary = Color(0xFF8B949E)
    val TextMuted = Color(0xFF484F58)
    val TextTerminalGreen = Color(0xFF39D353)
    val TextOnAccent = Color(0xFF0D1117)   // Dark text on bright accent bg

    // ── Severity colours (spec: 5=red, 4=orange, 3=yellow, 2=blue, 1=green)
    val Severity5 = Color(0xFFFF4D4F)      // Catastrophic — red
    val Severity4 = Color(0xFFFFA940)      // Severe — orange
    val Severity3 = Color(0xFFFADB14)      // Significant — yellow
    val Severity2 = Color(0xFF1890FF)      // Moderate — blue
    val Severity1 = Color(0xFF52C41A)      // Minor — green

    /** Map 1–5 severity to colour. */
    fun severityColor(level: Int): Color = when (level) {
        5 -> Severity5
        4 -> Severity4
        3 -> Severity3
        2 -> Severity2
        1 -> Severity1
        else -> TextMuted
    }

    /** Severity label. */
    fun severityLabel(level: Int): String = when (level) {
        5 -> "CRITICAL"
        4 -> "SEVERE"
        3 -> "SIGNIFICANT"
        2 -> "MODERATE"
        1 -> "MINOR"
        else -> "UNKNOWN"
    }

    // ── Crisis type colours ──────────────────────────────────────────
    fun crisisTypeColor(type: String): Color = when (type.lowercase()) {
        "flood" -> Color(0xFF1890FF)
        "heatwave" -> Color(0xFFFF7A45)
        "accident" -> Color(0xFFFF4D4F)
        "infrastructure_failure" -> Color(0xFF722ED1)
        "power_outage" -> Color(0xFFFADB14)
        "protest" -> Color(0xFFFA8C16)
        "disease_cluster" -> Color(0xFF13C2C2)
        "water_main_burst" -> Color(0xFF2F54EB)
        "road_blockage" -> Color(0xFF8B949E)
        else -> AccentCyan
    }

    /** Crisis type emoji. */
    fun crisisTypeIcon(type: String): String = when (type.lowercase()) {
        "flood" -> "🌊"
        "heatwave" -> "🔥"
        "accident" -> "💥"
        "infrastructure_failure" -> "🏗️"
        "power_outage" -> "⚡"
        "protest" -> "📢"
        "disease_cluster" -> "🦠"
        "water_main_burst" -> "💧"
        "road_blockage" -> "🚧"
        else -> "⚠️"
    }

    // ── Incident state colours ───────────────────────────────────────
    val StateMonitoring = Color(0xFF8B949E)
    val StateHypothesis = Color(0xFFFFA940)
    val StateVerification = Color(0xFF1890FF)
    val StateConfirmed = Color(0xFFFF4D4F)
    val StateRetracted = Color(0xFF484F58)
    val StateResolved = Color(0xFF52C41A)

    fun stateColor(state: String): Color = when (state) {
        "MONITORING" -> StateMonitoring
        "HYPOTHESIS" -> StateHypothesis
        "VERIFICATION_REQUESTED" -> StateVerification
        "CONFIRMED" -> StateConfirmed
        "RETRACTED" -> StateRetracted
        "RESOLVED" -> StateResolved
        else -> TextMuted
    }

    // ── Resource state colours ───────────────────────────────────────
    fun resourceStateColor(state: String): Color = when (state) {
        "AVAILABLE" -> AccentGreen
        "DISPATCHED" -> AccentRed
        "SHADOW_COMMITTED" -> AccentOrange
        "UNAVAILABLE" -> TextMuted
        else -> TextSecondary
    }

    // ── Gradient helpers ─────────────────────────────────────────────

    /** Vertical gradient for severity-coded cards. */
    fun severityGradient(level: Int): Brush {
        val color = severityColor(level)
        return Brush.verticalGradient(
            listOf(color.copy(alpha = 0.25f), color.copy(alpha = 0.05f))
        )
    }

    /** Horizontal accent gradient for headers and highlights. */
    val AccentGradient = Brush.horizontalGradient(
        listOf(AccentCyan, AccentPurple)
    )

    /** Glassmorphism card gradient. */
    val GlassGradient = Brush.verticalGradient(
        listOf(
            Color(0xFF1C2128).copy(alpha = 0.8f),
            Color(0xFF161B22).copy(alpha = 0.6f),
        )
    )

    /** Crisis alert pulsing gradient. */
    val CrisisGradient = Brush.verticalGradient(
        listOf(
            AccentRed.copy(alpha = 0.3f),
            AccentRed.copy(alpha = 0.05f),
        )
    )

    /** Subtle card border gradient. */
    val BorderGradient = Brush.horizontalGradient(
        listOf(
            SurfaceBorder.copy(alpha = 0.5f),
            SurfaceBorder.copy(alpha = 0.1f),
            SurfaceBorder.copy(alpha = 0.5f),
        )
    )
}
