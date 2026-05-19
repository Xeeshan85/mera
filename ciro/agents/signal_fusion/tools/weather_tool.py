# agents/signal_fusion/tools/weather_tool.py
# Three-tier fallback chain: Google Weather API → OpenWeatherMap → Open-Meteo

import logging
import os
from datetime import datetime

import httpx
from dotenv import load_dotenv

from schemas.signal import Signal, SignalLocation

load_dotenv()
logger = logging.getLogger(__name__)


async def get_weather_signal(lat: float, lng: float, area_name: str) -> dict:
    """
    Fetch current weather conditions and 6-hour forecast for a location.
    Uses a three-tier fallback chain:
    1. Google Weather API (credibility 0.95)
    2. OpenWeatherMap (credibility 0.80, degraded)
    3. Open-Meteo (credibility 0.70, degraded)

    Args:
        lat: Latitude of the location.
        lng: Longitude of the location.
        area_name: Human-readable area name (e.g., "G-10 Islamabad").

    Returns:
        A dict with signal data including source, credibility, and weather payload.
    """
    # Try Primary: Google Weather API
    signal = await _try_google_weather(lat, lng, area_name)
    if signal:
        return signal.to_firestore_dict()

    # Fallback 1: OpenWeatherMap
    signal = await _try_owm(lat, lng, area_name)
    if signal:
        return signal.to_firestore_dict()

    # Fallback 2: Open-Meteo
    signal = await _try_open_meteo(lat, lng, area_name)
    if signal:
        return signal.to_firestore_dict()

    # All failed
    logger.error(f"All weather sources failed for {area_name} ({lat}, {lng})")
    signal = Signal(
        source_type="weather",
        source_name="all_failed",
        timestamp=datetime.utcnow(),
        location=SignalLocation(
            lat=lat, lng=lng, area_name=area_name, geolocation_confidence=1.0
        ),
        raw_payload={"error": "all_weather_sources_failed"},
        credibility_score=0.0,
        urgency_language_score=0.0,
        mention_velocity=0,
        degraded_mode=True,
    )
    return signal.to_firestore_dict()


async def _try_google_weather(lat: float, lng: float, area_name: str) -> Signal | None:
    """Try Google Weather API for current conditions + 6h forecast."""
    api_key = os.getenv("GOOGLE_MAPS_API_KEY", "")
    if not api_key:
        logger.warning("GOOGLE_MAPS_API_KEY not set, skipping Google Weather API")
        return None

    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            # Current conditions
            current_url = "https://weather.googleapis.com/v1/currentConditions:lookup"
            current_params = {
                "key": api_key,
                "location.latitude": lat,
                "location.longitude": lng,
            }
            current_resp = await client.get(current_url, params=current_params)

            if current_resp.status_code != 200:
                logger.warning(
                    f"Google Weather currentConditions returned {current_resp.status_code}: "
                    f"{current_resp.text[:200]}"
                )
                return None

            current_data = current_resp.json()

            # Hourly forecast (6 hours)
            forecast_url = "https://weather.googleapis.com/v1/forecast/hours:lookup"
            forecast_params = {
                "key": api_key,
                "location.latitude": lat,
                "location.longitude": lng,
                "hours": 6,
            }
            forecast_resp = await client.get(forecast_url, params=forecast_params)
            forecast_data = forecast_resp.json() if forecast_resp.status_code == 200 else {}

        # Extract data from Google Weather API response
        temperature_c = _extract_nested(current_data, "temperature", "degrees")
        feels_like_c = _extract_nested(current_data, "feelsLikeTemperature", "degrees")
        humidity_pct = _extract_nested(current_data, "relativeHumidity")
        wind_speed_kmh = _extract_nested(current_data, "wind", "speed", "value")
        condition_type = current_data.get("weatherCondition", {}).get("type", "UNKNOWN") if isinstance(current_data.get("weatherCondition"), dict) else current_data.get("weatherCondition", "UNKNOWN")

        # Parse precipitation from current conditions
        precip_data = current_data.get("currentConditionsHistory", {}).get("precipitation", {})
        qpf_mm = precip_data.get("quantity", {}).get("value", 0) if isinstance(precip_data, dict) else 0

        # Parse forecast
        forecast_6h = []
        forecast_hours = forecast_data.get("forecastHours", [])
        precipitation_probability_pct = 0
        thunderstorm_probability_pct = 0

        for i, hour in enumerate(forecast_hours[:6]):
            precip_prob = hour.get("precipitation", {}).get("probability", {}).get("percent", 0)
            hour_qpf = hour.get("precipitation", {}).get("qpf", {}).get("quantity", {}).get("value", 0)

            forecast_6h.append({
                "hour": i + 1,
                "precip_prob": precip_prob,
                "qpf_mm": hour_qpf,
            })

            if precip_prob > precipitation_probability_pct:
                precipitation_probability_pct = precip_prob

            thunder_prob = hour.get("thunderstormProbability", 0)
            if isinstance(thunder_prob, dict):
                thunder_prob = thunder_prob.get("percent", 0)
            if thunder_prob > thunderstorm_probability_pct:
                thunderstorm_probability_pct = thunder_prob

        raw_payload = {
            "temperature_c": temperature_c,
            "feels_like_c": feels_like_c,
            "precipitation_probability_pct": precipitation_probability_pct,
            "qpf_mm": qpf_mm,
            "thunderstorm_probability_pct": thunderstorm_probability_pct,
            "wind_speed_kmh": wind_speed_kmh,
            "humidity_pct": humidity_pct,
            "condition_type": condition_type,
            "forecast_6h": forecast_6h,
            "source": "google_weather",
        }

        # Compute urgency score based on weather severity
        urgency = _compute_weather_urgency(raw_payload)

        return Signal(
            source_type="weather",
            source_name="google_weather",
            timestamp=datetime.utcnow(),
            location=SignalLocation(
                lat=lat, lng=lng, area_name=area_name, geolocation_confidence=1.0
            ),
            raw_payload=raw_payload,
            credibility_score=0.95,
            urgency_language_score=urgency,
            mention_velocity=0,
            degraded_mode=False,
        )

    except Exception as e:
        logger.warning(f"Google Weather API failed: {e}")
        return None


async def _try_owm(lat: float, lng: float, area_name: str) -> Signal | None:
    """Fallback 1: OpenWeatherMap API."""
    api_key = os.getenv("OWM_API_KEY", "")
    if not api_key:
        logger.warning("OWM_API_KEY not set, skipping OpenWeatherMap")
        return None

    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            url = "https://api.openweathermap.org/data/2.5/weather"
            params = {
                "lat": lat,
                "lon": lng,
                "appid": api_key,
                "units": "metric",
            }
            resp = await client.get(url, params=params)
            resp.raise_for_status()
            data = resp.json()

        main = data.get("main", {})
        wind = data.get("wind", {})
        weather_list = data.get("weather", [{}])
        weather_desc = weather_list[0] if weather_list else {}

        # Also fetch OWM forecast for precipitation probability
        precip_prob = 0
        rain_data = data.get("rain", {})
        if rain_data:
            precip_prob = 80  # OWM doesn't give probability directly; infer from rain presence

        raw_payload = {
            "temperature_c": main.get("temp"),
            "feels_like_c": main.get("feels_like"),
            "precipitation_probability_pct": precip_prob,
            "qpf_mm": rain_data.get("1h", 0) if rain_data else 0,
            "thunderstorm_probability_pct": 0,
            "wind_speed_kmh": (wind.get("speed", 0) or 0) * 3.6,  # m/s to km/h
            "humidity_pct": main.get("humidity"),
            "condition_type": weather_desc.get("main", "UNKNOWN"),
            "forecast_6h": [],
            "source": "owm",
        }

        urgency = _compute_weather_urgency(raw_payload)

        logger.info(f"Weather data from OWM for {area_name} (degraded mode)")
        return Signal(
            source_type="weather",
            source_name="owm",
            timestamp=datetime.utcnow(),
            location=SignalLocation(
                lat=lat, lng=lng, area_name=area_name, geolocation_confidence=1.0
            ),
            raw_payload=raw_payload,
            credibility_score=0.80,
            urgency_language_score=urgency,
            mention_velocity=0,
            degraded_mode=True,
        )

    except Exception as e:
        logger.warning(f"OpenWeatherMap API failed: {e}")
        return None


async def _try_open_meteo(lat: float, lng: float, area_name: str) -> Signal | None:
    """Fallback 2: Open-Meteo API (no key needed)."""
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            url = "https://api.open-meteo.com/v1/forecast"
            params = {
                "latitude": lat,
                "longitude": lng,
                "current": "temperature_2m,precipitation,rain,wind_speed_10m,relative_humidity_2m",
                "hourly": "precipitation_probability",
                "forecast_days": 1,
            }
            resp = await client.get(url, params=params)
            resp.raise_for_status()
            data = resp.json()

        current = data.get("current", {})
        hourly = data.get("hourly", {})

        # Get next 6 hours of precipitation probability
        precip_probs = hourly.get("precipitation_probability", [])[:6]
        max_precip_prob = max(precip_probs) if precip_probs else 0

        forecast_6h = [
            {"hour": i + 1, "precip_prob": p, "qpf_mm": 0}
            for i, p in enumerate(precip_probs)
        ]

        raw_payload = {
            "temperature_c": current.get("temperature_2m"),
            "feels_like_c": None,
            "precipitation_probability_pct": max_precip_prob,
            "qpf_mm": current.get("rain", 0) or current.get("precipitation", 0),
            "thunderstorm_probability_pct": 0,
            "wind_speed_kmh": current.get("wind_speed_10m"),
            "humidity_pct": current.get("relative_humidity_2m"),
            "condition_type": "RAIN" if (current.get("precipitation", 0) or 0) > 0 else "CLEAR",
            "forecast_6h": forecast_6h,
            "source": "open_meteo",
        }

        urgency = _compute_weather_urgency(raw_payload)

        logger.info(f"Weather data from Open-Meteo for {area_name} (degraded mode)")
        return Signal(
            source_type="weather",
            source_name="open_meteo",
            timestamp=datetime.utcnow(),
            location=SignalLocation(
                lat=lat, lng=lng, area_name=area_name, geolocation_confidence=1.0
            ),
            raw_payload=raw_payload,
            credibility_score=0.70,
            urgency_language_score=urgency,
            mention_velocity=0,
            degraded_mode=True,
        )

    except Exception as e:
        logger.warning(f"Open-Meteo API failed: {e}")
        return None


def _extract_nested(data: dict, *keys, default=None):
    """Safely extract a nested value from a dict."""
    current = data
    for key in keys:
        if isinstance(current, dict):
            current = current.get(key)
        else:
            return default
        if current is None:
            return default
    return current


def _compute_weather_urgency(payload: dict) -> float:
    """Compute urgency score based on weather conditions."""
    urgency = 0.0

    # Temperature extremes
    temp = payload.get("temperature_c")
    feels_like = payload.get("feels_like_c")
    effective_temp = feels_like if feels_like is not None else temp

    if effective_temp is not None:
        if effective_temp > 45:
            urgency = max(urgency, 0.95)
        elif effective_temp > 42:
            urgency = max(urgency, 0.80)
        elif effective_temp > 38:
            urgency = max(urgency, 0.50)

    # Precipitation
    precip_prob = payload.get("precipitation_probability_pct", 0) or 0
    qpf = payload.get("qpf_mm", 0) or 0

    if qpf > 30:
        urgency = max(urgency, 0.90)
    elif qpf > 15:
        urgency = max(urgency, 0.70)
    elif precip_prob > 80:
        urgency = max(urgency, 0.60)
    elif precip_prob > 50:
        urgency = max(urgency, 0.40)

    # Wind
    wind = payload.get("wind_speed_kmh", 0) or 0
    if wind > 80:
        urgency = max(urgency, 0.85)
    elif wind > 50:
        urgency = max(urgency, 0.60)

    # Thunderstorm
    thunder = payload.get("thunderstorm_probability_pct", 0) or 0
    if thunder > 60:
        urgency = max(urgency, 0.70)

    return urgency
