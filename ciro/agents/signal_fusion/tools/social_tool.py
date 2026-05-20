# agents/signal_fusion/tools/social_tool.py
# Apify Twitter scraper with urgency keyword scoring.
# Falls back to mock_social_stream when Apify is unavailable.

import logging
import os
import re
from datetime import datetime, timedelta

from dotenv import load_dotenv

from schemas.signal import Signal, SignalLocation
from agents.signal_fusion.tools.geocoding_tool import geocode_location

load_dotenv()
logger = logging.getLogger(__name__)

# Urgency keyword groups with associated scores
URGENCY_KEYWORDS = {
    "critical": {
        "keywords": ["help", "stuck", "trapped", "emergency", "sos", "dying", "collapse", "mayday"],
        "score": 0.9,
    },
    "high": {
        "keywords": ["flood", "flooding", "fire", "accident", "blocked", "burst", "explosion"],
        "score": 0.7,
    },
    "medium": {
        "keywords": ["water", "rain", "road", "traffic", "broken", "outage", "heat"],
        "score": 0.4,
    },
    "low": {
        "keywords": ["weather", "wet", "slow", "delay", "warning"],
        "score": 0.2,
    },
}


def _compute_urgency_score(text: str) -> float:
    """
    Compute urgency score from tweet text using keyword matching.
    Score: take max from matching group, boost +0.05 per additional match, cap at 1.0.
    """
    text_lower = text.lower()
    max_score = 0.0
    total_matches = 0

    for level_data in URGENCY_KEYWORDS.values():
        for keyword in level_data["keywords"]:
            if keyword in text_lower:
                max_score = max(max_score, level_data["score"])
                total_matches += 1

    if total_matches > 1:
        max_score += (total_matches - 1) * 0.05

    return min(max_score, 1.0)


def _compute_tweet_credibility(tweet: dict) -> float:
    """
    Compute per-tweet credibility based on user metrics.
    Social max is 0.80.
    """
    base = 0.25

    # Follower-based scoring
    followers = 0
    author = tweet.get("author", {})
    if isinstance(author, dict):
        followers = author.get("followers", 0) or author.get("followersCount", 0) or 0
    if not followers:
        followers = tweet.get("user", {}).get("followers_count", 0) if isinstance(tweet.get("user"), dict) else 0
    if not followers:
        followers = tweet.get("followers_count", 0) or tweet.get("followersCount", 0) or 0

    if followers > 100_000:
        base = 0.65
    elif followers > 10_000:
        base = 0.55
    elif followers > 1_000:
        base = 0.45
    elif followers > 100:
        base = 0.35

    # Has location data boost
    has_geo = bool(
        tweet.get("geo")
        or tweet.get("place")
        or tweet.get("coordinates")
        or tweet.get("geoLocation")
    )
    if has_geo:
        base += 0.15

    # Retweet boost
    retweets = tweet.get("retweet_count", 0) or tweet.get("retweetCount", 0) or 0
    if retweets > 100:
        base += 0.10

    # Verification boost
    is_verified = (
        tweet.get("user", {}).get("verified", False)
        if isinstance(tweet.get("user"), dict) else False
    ) or (
        tweet.get("author", {}).get("isVerified", False)
        if isinstance(tweet.get("author"), dict) else False
    )
    if is_verified:
        base += 0.10

    return min(base, 0.80)


async def get_social_signals(search_query: str, location_name: str, max_results: int = 50) -> list[dict]:
    """
    Fetch social media signals from Twitter/X using Apify scraper.
    Falls back to mock social stream if Apify fails.

    Args:
        search_query: Search terms for finding relevant tweets (e.g., "flood G-10 Islamabad").
        location_name: Human-readable location name (e.g., "G-10 Islamabad").
        max_results: Maximum number of tweets to fetch.

    Returns:
        A list of dicts with signal data for each matching tweet.
    """
    # Try primary: Apify Twitter scraper
    try:
        signals = await _try_apify(search_query, location_name, max_results)
        if signals:
            return [s.to_firestore_dict() for s in signals]
    except Exception as e:
        logger.warning(f"Apify Twitter scraper failed: {e}")

    # Fallback: Mock social stream
    logger.info(f"Falling back to mock social stream for '{search_query}'")
    try:
        from ingestion.mock_social_stream import generate_signals
        mock_signals = generate_signals(
            search_query=search_query,
            location_name=location_name,
            count=max_results,
        )
        return [s.to_firestore_dict() for s in mock_signals]
    except Exception as e:
        logger.error(f"Mock social stream also failed: {e}")
        return []


async def _try_apify(search_query: str, location_name: str, max_results: int) -> list[Signal] | None:
    """Try Apify Twitter scraper to get real tweets."""
    api_token = os.getenv("APIFY_API_TOKEN", "")
    if not api_token:
        logger.warning("APIFY_API_TOKEN not set, skipping Apify")
        return None

    try:
        from apify_client import ApifyClient

        client = ApifyClient(api_token)

        run_input = {
            "searchTerms": [f"{search_query} {location_name}"],
            "maxItems": max_results,
            "sort": "Latest",
            "lang": "en",  # English (Apify scraper takes single lang code)
        }

        logger.info(f"Starting Apify Twitter scraper for: {search_query}")
        run = client.actor(
            "kaitoeasyapi~twitter-x-data-tweet-scraper-pay-per-result-cheapest"
        ).call(run_input=run_input)

        dataset_id = run.default_dataset_id if hasattr(run, "default_dataset_id") else run["defaultDatasetId"]
        items = list(client.dataset(dataset_id).iterate_items())

        if not items:
            logger.warning("Apify returned 0 tweets")
            return None

        logger.info(f"Apify returned {len(items)} tweets")

        # Compute mention velocity: count tweets in last 30 minutes
        now = datetime.utcnow()
        thirty_min_ago = now - timedelta(minutes=30)
        recent_count = 0
        for item in items:
            tweet_time = _parse_tweet_timestamp(item)
            if tweet_time and tweet_time > thirty_min_ago:
                recent_count += 1
        mention_velocity = recent_count

        signals = []
        for item in items:
            text = item.get("text", "") or item.get("full_text", "") or item.get("tweetText", "") or ""
            tweet_time = _parse_tweet_timestamp(item) or now

            # Determine location
            location = await _extract_tweet_location(item, text, location_name)

            # Compute scores
            urgency = _compute_urgency_score(text)
            credibility = _compute_tweet_credibility(item)

            signals.append(Signal(
                source_type="social",
                source_name="apify_twitter",
                timestamp=tweet_time,
                location=location,
                raw_payload={
                    "text": text,
                    "tweet_id": item.get("id", "") or item.get("tweetId", ""),
                    "followers_count": _get_followers(item),
                    "retweet_count": item.get("retweet_count", 0) or item.get("retweetCount", 0) or 0,
                    "like_count": item.get("favorite_count", 0) or item.get("likeCount", 0) or 0,
                    "is_mock": False,
                },
                credibility_score=credibility,
                urgency_language_score=urgency,
                mention_velocity=mention_velocity,
                degraded_mode=False,
            ))

        return signals

    except Exception as e:
        logger.error(f"Apify call failed: {e}")
        return None


def _parse_tweet_timestamp(tweet: dict) -> datetime | None:
    """Parse tweet timestamp from various Apify response formats."""
    for field in ["created_at", "createdAt", "timestamp"]:
        val = tweet.get(field)
        if val:
            if isinstance(val, datetime):
                return val
            try:
                return datetime.fromisoformat(str(val).replace("Z", "+00:00").replace("+00:00", ""))
            except Exception:
                pass
            try:
                # Twitter format: "Wed Oct 10 20:19:24 +0000 2018"
                return datetime.strptime(str(val), "%a %b %d %H:%M:%S %z %Y").replace(tzinfo=None)
            except Exception:
                pass
    return None


def _get_followers(tweet: dict) -> int:
    """Extract follower count from various Apify response formats."""
    author = tweet.get("author", {})
    if isinstance(author, dict):
        fc = author.get("followers", 0) or author.get("followersCount", 0)
        if fc:
            return fc
    user = tweet.get("user", {})
    if isinstance(user, dict):
        fc = user.get("followers_count", 0) or user.get("followersCount", 0)
        if fc:
            return fc
    return tweet.get("followers_count", 0) or tweet.get("followersCount", 0) or 0


async def _extract_tweet_location(tweet: dict, text: str, default_location: str) -> SignalLocation:
    """Extract geo coordinates from tweet or geocode from text."""
    # Check for explicit geo coordinates
    geo = tweet.get("geo") or tweet.get("coordinates") or tweet.get("geoLocation")
    if geo:
        if isinstance(geo, dict):
            coords = geo.get("coordinates")
            if isinstance(coords, list) and len(coords) >= 2:
                return SignalLocation(
                    lat=coords[0],
                    lng=coords[1],
                    area_name=default_location,
                    geolocation_confidence=0.95,
                )
            lat = geo.get("latitude") or geo.get("lat")
            lng = geo.get("longitude") or geo.get("lng") or geo.get("lon")
            if lat and lng:
                return SignalLocation(
                    lat=float(lat),
                    lng=float(lng),
                    area_name=default_location,
                    geolocation_confidence=0.95,
                )

    # Check for place data
    place = tweet.get("place")
    if place and isinstance(place, dict):
        place_name = place.get("full_name", "") or place.get("name", "")
        if place_name:
            return await geocode_location(place_name)

    # Try to extract location from text — look for sector names
    sector_pattern = r'\b([A-Z]-\d+(?:/\d+)?)\b'
    matches = re.findall(sector_pattern, text)
    if matches:
        location_str = f"{matches[0]} Islamabad"
        return await geocode_location(location_str)

    # Default: geocode the location_name
    return await geocode_location(default_location)
