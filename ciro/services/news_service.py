# services/news_service.py
# Fetches Pakistan headlines from GNews API (free tier)
# Fallback: Dawn RSS feed via feedparser
# Caches results in Firestore `news_cache` collection (30 min TTL)

import logging
import os
import uuid
from datetime import datetime, timedelta
from typing import Any, Optional

import firebase_admin
from firebase_admin import firestore

logger = logging.getLogger(__name__)

GNEWS_API_KEY = os.getenv("GNEWS_API_KEY", "")
GNEWS_ENDPOINT = "https://gnews.io/api/v4/top-headlines"
DAWN_RSS = "https://www.dawn.com/feed"
CACHE_TTL_MINUTES = 30


class NewsService:
    """Fetches and caches Pakistan news headlines for the Intel tab."""

    def __init__(self):
        self._db = firestore.client()

    def get_headlines(self, max_items: int = 10) -> list[dict[str, Any]]:
        """Get cached headlines or fetch fresh ones."""
        # Check cache
        cached = self._get_cached()
        if cached:
            return cached[:max_items]

        # Try GNews API
        headlines = self._fetch_gnews(max_items)
        if not headlines:
            # Fallback: Dawn RSS
            headlines = self._fetch_dawn_rss(max_items)

        if headlines:
            self._write_cache(headlines)
        return headlines

    def _get_cached(self) -> Optional[list[dict]]:
        """Return cached headlines if TTL has not expired."""
        try:
            doc = self._db.collection("news_cache").document("latest").get()
            if doc.exists:
                data = doc.to_dict()
                cached_at = data.get("cached_at", "")
                if cached_at:
                    ts = datetime.fromisoformat(cached_at)
                    if datetime.utcnow() - ts < timedelta(minutes=CACHE_TTL_MINUTES):
                        return data.get("headlines", [])
            return None
        except Exception as e:
            logger.warning(f"News cache read failed: {e}")
            return None

    def _write_cache(self, headlines: list[dict]):
        """Write headlines to Firestore cache."""
        try:
            self._db.collection("news_cache").document("latest").set({
                "headlines": headlines,
                "cached_at": datetime.utcnow().isoformat(),
                "count": len(headlines),
            })
        except Exception as e:
            logger.warning(f"News cache write failed: {e}")

    def _fetch_gnews(self, max_items: int) -> list[dict]:
        """Fetch from GNews API (free tier: 100 req/day, 10 per request)."""
        if not GNEWS_API_KEY:
            logger.info("GNEWS_API_KEY not set, skipping GNews")
            return []
        try:
            import requests
            resp = requests.get(GNEWS_ENDPOINT, params={
                "country": "pk",
                "category": "general",
                "max": min(max_items, 10),
                "apikey": GNEWS_API_KEY,
            }, timeout=10)
            resp.raise_for_status()
            data = resp.json()
            articles = data.get("articles", [])
            return [
                {
                    "news_id": str(uuid.uuid4()),
                    "title": a.get("title", ""),
                    "description": a.get("description", ""),
                    "source": a.get("source", {}).get("name", "Unknown"),
                    "url": a.get("url", ""),
                    "image_url": a.get("image", ""),
                    "published_at": a.get("publishedAt", ""),
                    "urgency": _classify_urgency(a.get("title", "") + " " + a.get("description", "")),
                }
                for a in articles
            ]
        except Exception as e:
            logger.warning(f"GNews fetch failed: {e}")
            return []

    def _fetch_dawn_rss(self, max_items: int) -> list[dict]:
        """Fallback: Parse Dawn RSS feed."""
        try:
            import feedparser
            feed = feedparser.parse(DAWN_RSS)
            return [
                {
                    "news_id": str(uuid.uuid4()),
                    "title": entry.get("title", ""),
                    "description": entry.get("summary", "")[:200],
                    "source": "Dawn",
                    "url": entry.get("link", ""),
                    "image_url": "",
                    "published_at": entry.get("published", ""),
                    "urgency": _classify_urgency(entry.get("title", "")),
                }
                for entry in feed.entries[:max_items]
            ]
        except Exception as e:
            logger.warning(f"Dawn RSS fetch failed: {e}")
            return []


def _classify_urgency(text: str) -> str:
    """Quick keyword-based urgency classification for news headlines."""
    text_lower = text.lower()
    critical_words = ["flood", "earthquake", "killed", "dead", "emergency", "disaster",
                      "explosion", "attack", "collapse", "rescue", "crisis"]
    warning_words = ["warning", "alert", "heavy rain", "heatwave", "blocked",
                     "accident", "injured", "damage", "fire"]
    if any(w in text_lower for w in critical_words):
        return "critical"
    if any(w in text_lower for w in warning_words):
        return "warning"
    return "info"
