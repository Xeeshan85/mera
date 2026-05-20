# services/intelligence_service.py
# Signal Intelligence Center — computes OSINT metrics from signals collection
# Feeds the Intel tab with mention velocity, sentiment, keywords, credibility

import logging
import re
import uuid
from collections import Counter
from datetime import datetime, timedelta
from typing import Any

from firebase_admin import firestore

logger = logging.getLogger(__name__)

# Common stop words to exclude from trending keywords
STOP_WORDS = {
    "the", "a", "an", "in", "on", "at", "to", "for", "of", "is", "it", "and",
    "or", "but", "not", "no", "this", "that", "with", "from", "by", "has",
    "was", "are", "been", "were", "be", "have", "had", "do", "does", "did",
    "will", "would", "could", "should", "may", "might", "can", "shall",
}

# Crisis-relevant keywords to boost
CRISIS_KEYWORDS = {
    "flood", "rain", "water", "rescue", "fire", "heatwave", "accident",
    "blocked", "road", "hospital", "emergency", "alert", "power", "outage",
    "earthquake", "collapse", "damage", "injured", "dead", "killed",
    "evacuation", "shelter", "ambulance", "police", "ndma", "1122",
}


class IntelligenceService:
    """Computes signal intelligence metrics from Firestore signals collection."""

    def __init__(self):
        self._db = firestore.client()

    def compute_snapshot(self) -> dict[str, Any]:
        """Compute a full intelligence snapshot from recent signals."""
        signals = self._get_recent_signals(hours=2)

        snapshot = {
            "snapshot_id": str(uuid.uuid4()),
            "timestamp": datetime.utcnow().isoformat(),
            "total_signals": len(signals),
            "mention_velocity": self._compute_velocity(signals),
            "sentiment": self._compute_sentiment(signals),
            "credibility": self._compute_credibility(signals),
            "trending_keywords": self._extract_keywords(signals),
            "source_health": self._compute_source_health(signals),
            "signal_timeline": self._compute_timeline(signals),
        }

        # Write to Firestore
        self._write_snapshot(snapshot)
        return snapshot

    def get_latest_snapshot(self) -> dict[str, Any]:
        """Get the most recent intelligence snapshot from Firestore."""
        try:
            doc = self._db.collection("live_intelligence").document("latest").get()
            if doc.exists:
                return doc.to_dict()
            return self.compute_snapshot()
        except Exception as e:
            logger.warning(f"Intelligence snapshot read failed: {e}")
            return {"error": str(e)}

    def _get_recent_signals(self, hours: int = 2) -> list[dict]:
        """Fetch signals from the last N hours."""
        try:
            cutoff = (datetime.utcnow() - timedelta(hours=hours)).isoformat()
            docs = (
                self._db.collection("signals")
                .where("created_at", ">=", cutoff)
                .order_by("created_at", direction=firestore.Query.DESCENDING)
                .limit(200)
                .stream()
            )
            return [d.to_dict() for d in docs]
        except Exception as e:
            logger.warning(f"Signal fetch failed: {e}")
            return []

    def _compute_velocity(self, signals: list[dict]) -> dict:
        """Compute mention velocity in 15-minute buckets."""
        now = datetime.utcnow()
        buckets = []
        for i in range(8):  # Last 2 hours in 15-min buckets
            start = now - timedelta(minutes=15 * (i + 1))
            end = now - timedelta(minutes=15 * i)
            count = sum(
                1 for s in signals
                if start.isoformat() <= s.get("created_at", "") < end.isoformat()
            )
            buckets.append({"bucket": f"T-{15*(i+1)}m", "count": count})
        buckets.reverse()

        # Detect spike: last bucket > 2x average
        avg = max(1, sum(b["count"] for b in buckets) / len(buckets))
        latest = buckets[-1]["count"] if buckets else 0
        is_spike = latest > avg * 2

        return {
            "buckets": buckets,
            "current_rate": latest,
            "average_rate": round(avg, 1),
            "is_spike": is_spike,
            "trend": "rising" if latest > avg else ("falling" if latest < avg * 0.5 else "stable"),
        }

    def _compute_sentiment(self, signals: list[dict]) -> dict:
        """Aggregate sentiment from signal urgency scores."""
        if not signals:
            return {"score": 0.5, "label": "neutral", "negative_pct": 0}

        urgency_scores = [s.get("urgency_language_score", 0.5) for s in signals]
        avg_urgency = sum(urgency_scores) / len(urgency_scores)
        negative_pct = sum(1 for u in urgency_scores if u > 0.6) / len(urgency_scores) * 100

        label = "critical" if avg_urgency > 0.7 else ("negative" if avg_urgency > 0.5 else "neutral")
        return {
            "score": round(avg_urgency, 2),
            "label": label,
            "negative_pct": round(negative_pct),
        }

    def _compute_credibility(self, signals: list[dict]) -> dict:
        """Aggregate source credibility scores."""
        if not signals:
            return {"score": 0, "stars": 0, "verified_count": 0}

        scores = [s.get("credibility_score", 0.5) for s in signals]
        avg = sum(scores) / len(scores)
        verified = sum(1 for s in scores if s >= 0.7)

        return {
            "score": round(avg, 2),
            "stars": min(5, round(avg * 5)),
            "verified_count": verified,
            "total_sources": len(signals),
        }

    def _extract_keywords(self, signals: list[dict], top_n: int = 10) -> list[dict]:
        """Extract trending keywords from signal payloads."""
        word_counter = Counter()
        for s in signals:
            payload = s.get("raw_payload", {})
            text = ""
            # Extract text from social posts
            if "posts" in payload:
                text = " ".join(payload["posts"]) if isinstance(payload["posts"], list) else str(payload["posts"])
            # Extract text from field reports
            if "report" in payload:
                text += " " + str(payload["report"])
            if "description" in payload:
                text += " " + str(payload["description"])

            words = re.findall(r'\b[a-zA-Z]{3,}\b', text.lower())
            relevant = [w for w in words if w not in STOP_WORDS]
            word_counter.update(relevant)

        return [
            {
                "keyword": word,
                "count": count,
                "is_crisis": word in CRISIS_KEYWORDS,
            }
            for word, count in word_counter.most_common(top_n)
        ]

    def _compute_source_health(self, signals: list[dict]) -> list[dict]:
        """Check health of each signal source type."""
        source_types = ["weather", "traffic", "social", "sensor", "field_report"]
        health = []
        for src in source_types:
            matching = [s for s in signals if s.get("source_type") == src]
            degraded = any(s.get("degraded_mode", False) for s in matching)
            health.append({
                "source": src,
                "status": "degraded" if degraded else ("active" if matching else "inactive"),
                "signal_count": len(matching),
                "last_signal": matching[0].get("created_at", "") if matching else "",
            })
        return health

    def _compute_timeline(self, signals: list[dict], limit: int = 20) -> list[dict]:
        """Build a signal timeline for display."""
        sorted_signals = sorted(signals, key=lambda s: s.get("created_at", ""), reverse=True)
        return [
            {
                "time": s.get("created_at", ""),
                "source": s.get("source_type", "unknown"),
                "area": s.get("location", {}).get("area_name", ""),
                "credibility": s.get("credibility_score", 0),
                "urgency": s.get("urgency_language_score", 0),
            }
            for s in sorted_signals[:limit]
        ]

    def _write_snapshot(self, snapshot: dict):
        """Write intelligence snapshot to Firestore."""
        try:
            self._db.collection("live_intelligence").document("latest").set(snapshot)
            logger.info("Intelligence snapshot written to Firestore")
        except Exception as e:
            logger.warning(f"Intelligence snapshot write failed: {e}")
