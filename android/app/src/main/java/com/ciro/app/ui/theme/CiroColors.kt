package com.ciro.app.ui.theme

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
    val SurfaceTerminal = Color(0xFF0A0E14) // Terminal/console bg
    val SurfaceBorder = Color(0xFF30363D)  // Subtle border

    // ── Accent ───────────────────────────────────────────────────────
    val AccentCyan = Color(0xFF58A6FF)     // Primary action
    val AccentGreen = Color(0xFF3FB950)    // Success / available
    val AccentOrange = Color(0xFFF0883E)   // Warning / shadow commit
    val AccentRed = Color(0xFFF85149)      // Critical / dispatched
    val AccentPurple = Color(0xFFBC8CFF)   // AI agent / trace

    // ── Text ─────────────────────────────────────────────────────────
    val TextPrimary = Color(0xFFE6EDF3)
    val TextSecondary = Color(0xFF8B949E)
    val TextMuted = Color(0xFF484F58)
    val TextTerminalGreen = Color(0xFF39D353)

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
}
