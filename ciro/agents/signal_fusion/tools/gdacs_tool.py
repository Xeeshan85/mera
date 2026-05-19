# agents/signal_fusion/tools/gdacs_tool.py
# GDACS (Global Disaster Alert and Coordination System) integration.
# Primary: JSON API, Fallback: RSS feed.

import logging
from datetime import datetime

import feedparser
import httpx
from geopy.distance import geodesic
from dotenv import load_dotenv

from schemas.signal import Signal, SignalLocation

load_dotenv()
logger = logging.getLogger(__name__)

# Map GDACS event types to CIRO crisis types
EVENT_TYPE_MAP = {
    "FL": "flood",
    "TC": "infrastructure_failure",
    "EQ": "infrastructure_failure",
    "VO": "infrastructure_failure",
    "DR": "heatwave",
    "WF": "fire",
}

# Urgency by alert level
ALERT_URGENCY = {
    "Green": 0.3,
    "Orange": 0.7,
    "Red": 0.95,
}


async def get_gdacs_signals(lat: float, lng: float, radius_km: float = 100) -> list[dict]:
    """
    Fetch disaster alerts from GDACS within a radius of the given location.
    Primary: GDACS JSON API. Fallback: GDACS RSS feed.

    Args:
        lat: Latitude of the center location.
        lng: Longitude of the center location.
        radius_km: Search radius in kilometers (default 100km).

    Returns:
        A list of dicts with signal data for each nearby GDACS alert.
    """
    # Try primary: GDACS JSON API
    signals = await _try_gdacs_json(lat, lng, radius_km)
    if signals is not None:
        return [s.to_firestore_dict() for s in signals]

    # Fallback: GDACS RSS feed
    logger.info("GDACS JSON API failed, trying RSS feed...")
    signals = await _try_gdacs_rss(lat, lng, radius_km)
    if signals is not None:
        return [s.to_firestore_dict() for s in signals]

    # Both failed
    logger.warning("Both GDACS JSON and RSS failed. Returning empty list.")
    return []


async def _try_gdacs_json(lat: float, lng: float, radius_km: float) -> list[Signal] | None:
    """Try GDACS JSON API for disaster events."""
    try:
        url = "https://www.gdacs.org/gdacsapi/api/events/geteventlist/SEARCH"
        params = {
            "limit": 20,
            "alertlevel": "Orange,Red",
        }

        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.get(url, params=params)
            if response.status_code != 200:
                logger.warning(f"GDACS JSON API returned {response.status_code}")
                return None

            data = response.json()

        # Navigate the GDACS response structure
        features = []
        if isinstance(data, dict):
            features = data.get("features", [])
            if not features:
                # Try alternate structure
                features = data.get("events", [])

        signals = []
        for feature in features:
            event_signal = _parse_json_event(feature, lat, lng, radius_km)
            if event_signal:
                signals.append(event_signal)

        logger.info(f"GDACS JSON API: {len(signals)} events within {radius_km}km radius")
        return signals

    except Exception as e:
        logger.warning(f"GDACS JSON API failed: {e}")
        return None


def _parse_json_event(feature: dict, center_lat: float, center_lng: float, radius_km: float) -> Signal | None:
    """Parse a single GDACS JSON event and check if within radius."""
    try:
        # Extract coordinates from GeoJSON-like structure
        geometry = feature.get("geometry", {})
        properties = feature.get("properties", {})

        event_lat = None
        event_lng = None

        if geometry:
            coords = geometry.get("coordinates", [])
            if isinstance(coords, list) and len(coords) >= 2:
                event_lng = coords[0]
                event_lat = coords[1]

        # Try properties for coordinates
        if event_lat is None:
            event_lat = properties.get("lat") or properties.get("latitude")
        if event_lng is None:
            event_lng = properties.get("lng") or properties.get("longitude") or properties.get("lon")

        if event_lat is None or event_lng is None:
            return None

        event_lat = float(event_lat)
        event_lng = float(event_lng)

        # Check distance
        distance = geodesic((center_lat, center_lng), (event_lat, event_lng)).km
        if distance > radius_km:
            return None

        # Extract event details
        event_type = str(properties.get("eventtype", "") or properties.get("type", "")).upper()[:2]
        alert_level = properties.get("alertlevel", "Green") or "Green"
        event_name = properties.get("name", "") or properties.get("eventname", "") or "Unknown Event"
        country = properties.get("country", "") or properties.get("iso3", "")
        gdacs_url = properties.get("url", "") or properties.get("link", "")

        # Map to CIRO crisis type
        crisis_type = EVENT_TYPE_MAP.get(event_type, "infrastructure_failure")

        raw_payload = {
            "event_type": event_type,
            "crisis_type": crisis_type,
            "alert_level": alert_level,
            "event_name": event_name,
            "affected_country": country,
            "gdacs_url": gdacs_url,
            "distance_km": round(distance, 1),
            "source": "gdacs_json",
        }

        return Signal(
            source_type="official",
            source_name="gdacs",
            timestamp=datetime.utcnow(),
            location=SignalLocation(
                lat=event_lat,
                lng=event_lng,
                area_name=event_name,
                geolocation_confidence=0.85,
                radius_m=distance * 1000,
            ),
            raw_payload=raw_payload,
            credibility_score=0.95,
            urgency_language_score=ALERT_URGENCY.get(alert_level, 0.3),
            mention_velocity=0,
            degraded_mode=False,
        )

    except Exception as e:
        logger.warning(f"Failed to parse GDACS JSON event: {e}")
        return None


async def _try_gdacs_rss(lat: float, lng: float, radius_km: float) -> list[Signal] | None:
    """Fallback: GDACS RSS feed."""
    try:
        rss_url = "https://www.gdacs.org/xml/rss.xml"

        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.get(rss_url)
            if response.status_code != 200:
                logger.warning(f"GDACS RSS returned {response.status_code}")
                return None

        feed = feedparser.parse(response.text)

        signals = []
        for entry in feed.entries:
            event_signal = _parse_rss_entry(entry, lat, lng, radius_km)
            if event_signal:
                signals.append(event_signal)

        logger.info(f"GDACS RSS: {len(signals)} events within {radius_km}km radius")
        return signals

    except Exception as e:
        logger.warning(f"GDACS RSS failed: {e}")
        return None


def _parse_rss_entry(entry, center_lat: float, center_lng: float, radius_km: float) -> Signal | None:
    """Parse a single GDACS RSS entry and check if within radius."""
    try:
        # Extract coordinates from RSS entry
        # GDACS RSS uses geo:lat and geo:long or gdacs:lat/gdacs:lon
        event_lat = None
        event_lng = None

        # Try various attribute names
        for lat_key in ["geo_lat", "gdacs_lat", "lat"]:
            val = getattr(entry, lat_key, None)
            if val:
                event_lat = float(val)
                break

        for lng_key in ["geo_long", "geo_lon", "gdacs_lon", "lon", "long"]:
            val = getattr(entry, lng_key, None)
            if val:
                event_lng = float(val)
                break

        # Try where dict
        if event_lat is None and hasattr(entry, "where"):
            where = entry.where
            if isinstance(where, dict):
                event_lat = float(where.get("lat", where.get("latitude", 0)))
                event_lng = float(where.get("long", where.get("lon", where.get("longitude", 0))))

        if event_lat is None or event_lng is None:
            return None

        # Check distance
        distance = geodesic((center_lat, center_lng), (event_lat, event_lng)).km
        if distance > radius_km:
            return None

        # Extract event details
        title = entry.get("title", "Unknown Event")
        link = entry.get("link", "")
        summary = entry.get("summary", "")

        # Try to determine alert level from title
        alert_level = "Orange"
        title_upper = title.upper()
        if "RED" in title_upper:
            alert_level = "Red"
        elif "GREEN" in title_upper:
            alert_level = "Green"

        # Try to determine event type from title
        event_type = ""
        if any(w in title_upper for w in ["FLOOD", "FL"]):
            event_type = "FL"
        elif any(w in title_upper for w in ["CYCLONE", "TC", "TYPHOON", "HURRICANE"]):
            event_type = "TC"
        elif any(w in title_upper for w in ["EARTHQUAKE", "EQ"]):
            event_type = "EQ"
        elif any(w in title_upper for w in ["DROUGHT", "DR"]):
            event_type = "DR"
        elif any(w in title_upper for w in ["WILDFIRE", "WF", "FIRE"]):
            event_type = "WF"
        elif any(w in title_upper for w in ["VOLCANO", "VO"]):
            event_type = "VO"

        crisis_type = EVENT_TYPE_MAP.get(event_type, "infrastructure_failure")

        raw_payload = {
            "event_type": event_type,
            "crisis_type": crisis_type,
            "alert_level": alert_level,
            "event_name": title,
            "affected_country": "PK",
            "gdacs_url": link,
            "summary": summary[:500],
            "distance_km": round(distance, 1),
            "source": "gdacs_rss",
        }

        return Signal(
            source_type="official",
            source_name="gdacs",
            timestamp=datetime.utcnow(),
            location=SignalLocation(
                lat=event_lat,
                lng=event_lng,
                area_name=title,
                geolocation_confidence=0.80,
                radius_m=distance * 1000,
            ),
            raw_payload=raw_payload,
            credibility_score=0.95,
            urgency_language_score=ALERT_URGENCY.get(alert_level, 0.3),
            mention_velocity=0,
            degraded_mode=False,
        )

    except Exception as e:
        logger.warning(f"Failed to parse GDACS RSS entry: {e}")
        return None
