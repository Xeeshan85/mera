# agents/signal_fusion/tools/credibility_scorer.py
# Post-collection scoring pipeline: adjusts credibility scores across all signals.

import logging
from datetime import datetime

from schemas.signal import Signal

logger = logging.getLogger(__name__)


def apply_scoring_adjustments(signals: list[Signal]) -> list[Signal]:
    """
    Apply cross-signal credibility scoring adjustments.
    Runs AFTER all tools have produced their signals.

    Adjustments applied in order:
    1. Geolocation confidence boost
    2. Mention velocity boost (social only)
    3. Staleness penalty
    4. Contradiction detection (weather vs social flood claims)
    5. Corroboration boost (3+ source types agreeing)

    Args:
        signals: List of Signal objects from all data sources.

    Returns:
        The same list of signals with adjusted credibility_score and contradiction_flag.
    """
    if not signals:
        return signals

    # 1. Geolocation confidence boost
    for signal in signals:
        if signal.location.geolocation_confidence > 0.85:
            signal.credibility_score = min(signal.credibility_score + 0.10, 1.0)

    # 2. Mention velocity boost (social signals only)
    for signal in signals:
        if signal.source_type == "social":
            velocity_boost = min(signal.mention_velocity / 20, 1.0) * 0.05
            signal.credibility_score = min(signal.credibility_score + velocity_boost, 1.0)

    # 3. Staleness penalty
    now = datetime.utcnow()
    for signal in signals:
        age_minutes = (now - signal.timestamp).total_seconds() / 60
        if age_minutes > 30:
            signal.credibility_score = max(signal.credibility_score - 0.15, 0.0)

    # 4. Contradiction detection
    # Rule: if a high-credibility source (>0.85) has NO precipitation signal
    #       but social signals mention flooding, flag those social signals
    weather_signals = [s for s in signals if s.source_type == "weather"]
    social_signals = [s for s in signals if s.source_type == "social"]

    for weather in weather_signals:
        if weather.credibility_score > 0.85:
            precip = weather.raw_payload.get("precipitation_probability_pct", 100)
            if precip is not None and precip < 20:  # Weather says no rain
                flood_keywords = ["flood", "flooding", "water", "sewer"]
                for social in social_signals:
                    text = str(social.raw_payload.get("text", "")).lower()
                    if any(kw in text for kw in flood_keywords):
                        social.contradiction_flag = True
                        social.credibility_score = max(social.credibility_score - 0.20, 0.0)
                        logger.info(
                            f"Contradiction flagged: signal {social.signal_id} "
                            f"mentions flooding but weather shows {precip}% precip"
                        )

    # 5. Corroboration boost
    # If 3+ sources of different types agree (have similar urgency scores > 0.5),
    # boost all their credibility by 0.05
    high_urgency = [s for s in signals if s.urgency_language_score > 0.5]
    source_types_agreeing = set(s.source_type for s in high_urgency)
    if len(source_types_agreeing) >= 3:
        for signal in high_urgency:
            signal.credibility_score = min(signal.credibility_score + 0.05, 1.0)
        logger.info(
            f"Corroboration boost applied: {len(source_types_agreeing)} source types "
            f"agree on high urgency ({', '.join(source_types_agreeing)})"
        )

    return signals
