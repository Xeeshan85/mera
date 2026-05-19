# agents/signal_fusion/tools/traffic_tool.py
# Google Maps Routes API with Firestore traffic cache fallback.

import logging
import math
import os
from datetime import datetime, timedelta

import httpx
from dotenv import load_dotenv

from schemas.signal import Signal, SignalLocation
from services.firestore_service import FirestoreService

load_dotenv()
logger = logging.getLogger(__name__)

_firestore = None


def _get_firestore() -> FirestoreService:
    """Lazy-init FirestoreService to avoid circular imports at module level."""
    global _firestore
    if _firestore is None:
        _firestore = FirestoreService()
    return _firestore


def _generate_grid_points(lat: float, lng: float, spacing_km: float = 1.0, count: int = 8) -> list[dict]:
    """
    Generate surrounding grid points at ~spacing_km distance around (lat, lng).
    Uses 8 compass directions (N, NE, E, SE, S, SW, W, NW).
    """
    # Approximate: 1 degree lat ≈ 111 km, 1 degree lng ≈ 111 * cos(lat) km
    dlat = spacing_km / 111.0
    dlng = spacing_km / (111.0 * math.cos(math.radians(lat)))

    directions = [
        (dlat, 0),          # N
        (dlat, dlng),       # NE
        (0, dlng),          # E
        (-dlat, dlng),      # SE
        (-dlat, 0),         # S
        (-dlat, -dlng),     # SW
        (0, -dlng),         # W
        (dlat, -dlng),      # NW
    ]

    return [
        {"latitude": lat + d[0], "longitude": lng + d[1]}
        for d in directions
    ]


async def get_traffic_signal(lat: float, lng: float, area_name: str, radius_km: float = 2.0) -> dict:
    """
    Fetch traffic congestion data for a location using Google Maps Routes API.
    Generates 8 surrounding grid points and computes route matrix.
    Falls back to Firestore cache if API fails.

    Args:
        lat: Latitude of the center location.
        lng: Longitude of the center location.
        area_name: Human-readable area name (e.g., "G-10 Islamabad").
        radius_km: Radius in km for grid point generation.

    Returns:
        A dict with signal data including congestion index and traffic payload.
    """
    api_key = os.getenv("GOOGLE_MAPS_API_KEY", "")
    fs = _get_firestore()

    # Try primary: Google Maps Routes API
    if api_key:
        signal = await _try_routes_api(lat, lng, area_name, api_key)
        if signal:
            # Cache the result
            try:
                await fs.update_traffic_cache(area_name, signal.raw_payload)
            except Exception as e:
                logger.warning(f"Failed to update traffic cache: {e}")
            return signal.to_firestore_dict()

    # Fallback: Firestore cached data
    logger.info(f"Routes API failed for {area_name}, trying cached data...")
    cached = await fs.get_cached_traffic(area_name)
    if cached:
        logger.info(f"Using cached traffic data for {area_name}")
        signal = Signal(
            source_type="traffic",
            source_name="maps_traffic_cached",
            timestamp=datetime.utcnow(),
            location=SignalLocation(
                lat=lat, lng=lng, area_name=area_name, geolocation_confidence=1.0
            ),
            raw_payload=cached,
            credibility_score=0.60,
            urgency_language_score=_compute_traffic_urgency(cached),
            mention_velocity=0,
            degraded_mode=True,
        )
        return signal.to_firestore_dict()

    # No cache available
    logger.warning(f"No traffic data available for {area_name}")
    signal = Signal(
        source_type="traffic",
        source_name="maps_traffic",
        timestamp=datetime.utcnow(),
        location=SignalLocation(
            lat=lat, lng=lng, area_name=area_name, geolocation_confidence=1.0
        ),
        raw_payload={"error": "traffic_data_unavailable", "source": "maps_traffic"},
        credibility_score=0.0,
        urgency_language_score=0.0,
        mention_velocity=0,
        degraded_mode=True,
    )
    return signal.to_firestore_dict()


async def _try_routes_api(lat: float, lng: float, area_name: str, api_key: str) -> Signal | None:
    """Call Google Maps Routes API computeRouteMatrix."""
    try:
        grid_points = _generate_grid_points(lat, lng, spacing_km=1.0)

        # Build origins (center + 8 grid points) and destinations (center)
        origins = [
            {"waypoint": {"location": {"latLng": {"latitude": lat, "longitude": lng}}}}
        ]
        for gp in grid_points:
            origins.append({
                "waypoint": {"location": {"latLng": gp}}
            })

        destinations = [
            {"waypoint": {"location": {"latLng": {"latitude": lat, "longitude": lng}}}}
        ]

        body = {
            "origins": origins,
            "destinations": destinations,
            "travelMode": "DRIVE",
            "routingPreference": "TRAFFIC_AWARE",
            "departureTime": (datetime.utcnow() + timedelta(minutes=1)).strftime("%Y-%m-%dT%H:%M:%SZ"),
        }

        headers = {
            "X-Goog-Api-Key": api_key,
            "X-Goog-FieldMask": "originIndex,destinationIndex,duration,distanceMeters,condition",
            "Content-Type": "application/json",
        }

        async with httpx.AsyncClient(timeout=10.0) as client:
            resp = await client.post(
                "https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix",
                json=body,
                headers=headers,
            )

            if resp.status_code != 200:
                logger.warning(f"Routes API returned {resp.status_code}: {resp.text[:200]}")
                return None

            # The Routes API may return streaming JSON (one JSON object per line)
            # or a JSON array depending on the response format
            response_text = resp.text.strip()
            route_entries = []

            # Try parsing as JSON array first
            try:
                parsed = resp.json()
                if isinstance(parsed, list):
                    route_entries = parsed
                else:
                    route_entries = [parsed]
            except Exception:
                # Try line-by-line JSON parsing (streaming response format)
                import json
                for line in response_text.split("\n"):
                    line = line.strip().rstrip(",")
                    if line and line not in ["[", "]"]:
                        try:
                            route_entries.append(json.loads(line))
                        except Exception:
                            pass

        if not route_entries:
            logger.warning("Routes API returned no route entries")
            return None

        # Compute congestion metrics
        congestion_values = []
        route_details = []
        total_speed_sum = 0
        affected_routes = 0

        for entry in route_entries:
            distance_m = entry.get("distanceMeters", 0)
            duration_str = entry.get("duration", "0s")

            # Parse duration (format: "123s" or "1234s")
            actual_seconds = _parse_duration(duration_str)
            if distance_m <= 0 or actual_seconds <= 0:
                continue

            # Baseline: 50 km/h free flow
            baseline_seconds = distance_m / (50 * 1000 / 3600)  # 50 km/h in m/s
            congestion_ratio = actual_seconds / baseline_seconds if baseline_seconds > 0 else 1.0
            congestion_index = min(max((congestion_ratio - 1.0) / 2.0, 0.0), 1.0)

            avg_speed = (distance_m / actual_seconds) * 3.6  # m/s to km/h
            total_speed_sum += avg_speed

            congestion_values.append(congestion_index)
            route_details.append({
                "origin_index": entry.get("originIndex", 0),
                "distance_m": distance_m,
                "duration_s": actual_seconds,
                "congestion_index": round(congestion_index, 3),
                "avg_speed_kmh": round(avg_speed, 1),
                "condition": entry.get("condition", "UNKNOWN"),
            })

            if congestion_index > 0.3:
                affected_routes += 1

        overall_congestion = sum(congestion_values) / len(congestion_values) if congestion_values else 0
        avg_speed = total_speed_sum / len(congestion_values) if congestion_values else 50

        raw_payload = {
            "congestion_index": round(overall_congestion, 3),
            "avg_speed_kmh": round(avg_speed, 1),
            "affected_routes_count": affected_routes,
            "route_details": route_details,
            "source": "maps_traffic",
        }

        urgency = _compute_traffic_urgency(raw_payload)

        return Signal(
            source_type="traffic",
            source_name="maps_traffic",
            timestamp=datetime.utcnow(),
            location=SignalLocation(
                lat=lat, lng=lng, area_name=area_name, geolocation_confidence=1.0
            ),
            raw_payload=raw_payload,
            credibility_score=0.90,
            urgency_language_score=urgency,
            mention_velocity=0,
            degraded_mode=False,
        )

    except Exception as e:
        logger.warning(f"Routes API call failed: {e}")
        return None


def _parse_duration(duration_str) -> float:
    """Parse Google Routes API duration string (e.g., '123s') to seconds."""
    if isinstance(duration_str, (int, float)):
        return float(duration_str)
    if isinstance(duration_str, str):
        duration_str = duration_str.strip()
        if duration_str.endswith("s"):
            try:
                return float(duration_str[:-1])
            except ValueError:
                pass
    return 0.0


def _compute_traffic_urgency(payload: dict) -> float:
    """Compute urgency score based on traffic conditions."""
    congestion = payload.get("congestion_index", 0) or 0
    if congestion > 0.8:
        return 0.90
    elif congestion > 0.6:
        return 0.70
    elif congestion > 0.4:
        return 0.50
    elif congestion > 0.2:
        return 0.30
    return 0.10
