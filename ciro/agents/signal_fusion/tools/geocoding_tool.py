# agents/signal_fusion/tools/geocoding_tool.py
# Google Geocoding API with Islamabad bias.

import logging
import os

import httpx
from dotenv import load_dotenv

from schemas.signal import SignalLocation

load_dotenv()
logger = logging.getLogger(__name__)

# Islamabad default fallback
DEFAULT_ISLAMABAD = SignalLocation(
    lat=33.6844,
    lng=73.0479,
    area_name="Islamabad (default)",
    geolocation_confidence=0.20,
)

# Map Google location_type to confidence score
LOCATION_TYPE_CONFIDENCE = {
    "ROOFTOP": 0.95,
    "RANGE_INTERPOLATED": 0.80,
    "GEOMETRIC_CENTER": 0.70,
    "APPROXIMATE": 0.50,
}


async def geocode_location(location_string: str) -> SignalLocation:
    """
    Geocode a location string using Google Geocoding API.
    Biased towards Islamabad, Pakistan.
    Falls back to Islamabad center if geocoding fails.

    Args:
        location_string: A text description of the location to geocode.

    Returns:
        A SignalLocation with lat, lng, area_name, and geolocation_confidence.
    """
    api_key = os.getenv("GOOGLE_MAPS_API_KEY", "")
    if not api_key:
        logger.warning("GOOGLE_MAPS_API_KEY not set. Returning default Islamabad location.")
        return DEFAULT_ISLAMABAD

    url = "https://maps.googleapis.com/maps/api/geocode/json"
    params = {
        "address": location_string,
        "key": api_key,
        "region": "pk",
        "bounds": "33.5,72.8|33.9,73.3",  # Islamabad bounding box
    }

    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            response = await client.get(url, params=params)
            response.raise_for_status()
            data = response.json()

        if data.get("status") != "OK" or not data.get("results"):
            logger.warning(
                f"Geocoding returned no results for '{location_string}' "
                f"(status: {data.get('status')}). Using default."
            )
            return SignalLocation(
                lat=DEFAULT_ISLAMABAD.lat,
                lng=DEFAULT_ISLAMABAD.lng,
                area_name=location_string,
                geolocation_confidence=0.20,
            )

        result = data["results"][0]
        geometry = result.get("geometry", {})
        location = geometry.get("location", {})
        location_type = geometry.get("location_type", "APPROXIMATE")

        return SignalLocation(
            lat=location.get("lat", DEFAULT_ISLAMABAD.lat),
            lng=location.get("lng", DEFAULT_ISLAMABAD.lng),
            area_name=result.get("formatted_address", location_string),
            geolocation_confidence=LOCATION_TYPE_CONFIDENCE.get(location_type, 0.50),
        )

    except Exception as e:
        logger.error(f"Geocoding failed for '{location_string}': {e}")
        return SignalLocation(
            lat=DEFAULT_ISLAMABAD.lat,
            lng=DEFAULT_ISLAMABAD.lng,
            area_name=location_string,
            geolocation_confidence=0.20,
        )
