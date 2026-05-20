# agents/severity_prediction/agent.py
# Agent 3: Severity & Evolution Prediction
# Tools: Google Weather API, Places API, Maps Route Matrix

import json
import logging
import os
from datetime import datetime

import httpx
from dotenv import load_dotenv
from google.adk.agents import Agent
from google.genai import types

from services.incident_service import IncidentService
from google.cloud.firestore_v1.base_query import FieldFilter
from schemas.incident import SeverityForecast

load_dotenv()
logger = logging.getLogger(__name__)
incident_service = IncidentService()

MAPS_KEY = os.getenv("GOOGLE_MAPS_API_KEY", "")


def get_weather_forecast(lat: float, lng: float) -> dict:
    """
    Fetch 6-hour precipitation and temperature forecast from Google Weather API.
    Falls back to Open-Meteo if Google Weather fails.

    Args:
        lat: Latitude
        lng: Longitude

    Returns:
        dict with status and weather forecast data
    """
    try:
        url = "https://weather.googleapis.com/v1/forecast/hours:lookup"
        params = {
            "key": MAPS_KEY,
            "location.latitude": lat,
            "location.longitude": lng,
            "hours": 6,
        }
        with httpx.Client(timeout=10) as client:
            resp = client.get(url, params=params)

        if resp.status_code == 200:
            data = resp.json()
            hours = data.get("forecastHours", [])
            parsed = []
            for h in hours[:6]:
                parsed.append({
                    "hour": h.get("displayDateTime", ""),
                    "precipitation_probability": h.get("precipitationProbability", 0),
                    "qpf_mm": h.get("qpf", {}).get("quantity", 0),
                    "temperature_c": h.get("temperature", {}).get("degrees", 0),
                    "thunderstorm_probability": h.get("thunderstormProbability", 0),
                    "wind_speed_kmh": h.get("wind", {}).get("speed", {}).get("value", 0),
                })
            return {
                "status": "success",
                "data": {
                    "source": "google_weather",
                    "forecast_hours": parsed,
                    "max_precip_probability": max((h["precipitation_probability"] for h in parsed), default=0),
                    "total_qpf_mm": sum(h["qpf_mm"] for h in parsed),
                },
            }

        # Fallback: Open-Meteo (free, no key)
        logger.warning(f"Google Weather API failed ({resp.status_code}), falling back to Open-Meteo")
        fallback_url = "https://api.open-meteo.com/v1/forecast"
        fallback_params = {
            "latitude": lat,
            "longitude": lng,
            "hourly": "precipitation_probability,precipitation,temperature_2m,wind_speed_10m",
            "forecast_days": 1,
        }
        with httpx.Client(timeout=10) as client:
            fb_resp = client.get(fallback_url, params=fallback_params)
        fb_data = fb_resp.json()
        hourly = fb_data.get("hourly", {})
        precip_prob = hourly.get("precipitation_probability", [0] * 6)[:6]
        precip = hourly.get("precipitation", [0] * 6)[:6]
        return {
            "status": "degraded",
            "data": {
                "source": "open_meteo",
                "forecast_hours": [
                    {"hour": i, "precipitation_probability": precip_prob[i], "qpf_mm": precip[i]}
                    for i in range(6)
                ],
                "max_precip_probability": max(precip_prob, default=0),
                "total_qpf_mm": sum(precip),
            },
        }

    except Exception as e:
        logger.error(f"get_weather_forecast failed: {e}")
        return {"status": "error", "error_message": str(e)}


def get_vulnerable_facilities(lat: float, lng: float, radius_m: int = 2000) -> dict:
    """
    Find nearby hospitals, schools, and shelters using Google Places API (New).

    Args:
        lat: Latitude of crisis center
        lng: Longitude of crisis center
        radius_m: Search radius in meters (default 2000)

    Returns:
        dict with status and list of vulnerable facilities
    """
    try:
        url = "https://places.googleapis.com/v1/places:searchNearby"
        headers = {
            "Content-Type": "application/json",
            "X-Goog-Api-Key": MAPS_KEY,
            "X-Goog-FieldMask": "places.displayName,places.types,places.location,places.formattedAddress",
        }
        body = {
            "includedTypes": ["hospital", "school", "fire_station", "police"],
            "maxResultCount": 20,
            "locationRestriction": {
                "circle": {
                    "center": {"latitude": lat, "longitude": lng},
                    "radius": float(radius_m),
                }
            },
        }
        with httpx.Client(timeout=10) as client:
            resp = client.post(url, headers=headers, json=body)

        if resp.status_code == 200:
            places = resp.json().get("places", [])
            facilities = []
            for p in places:
                facilities.append({
                    "name": p.get("displayName", {}).get("text", "Unknown"),
                    "types": p.get("types", []),
                    "lat": p.get("location", {}).get("latitude"),
                    "lng": p.get("location", {}).get("longitude"),
                    "address": p.get("formattedAddress", ""),
                })
            hospitals = [f for f in facilities if "hospital" in f["types"]]
            schools = [f for f in facilities if "school" in f["types"]]
            return {
                "status": "success",
                "data": {
                    "total_facilities": len(facilities),
                    "hospitals": hospitals,
                    "schools": schools,
                    "all_facilities": facilities,
                    "high_vulnerability": len(hospitals) > 2 or len(schools) > 3,
                },
            }
        else:
            logger.warning(f"Places API failed: {resp.status_code} {resp.text}")
            return {
                "status": "degraded",
                "data": {
                    "total_facilities": 0,
                    "hospitals": [],
                    "schools": [],
                    "all_facilities": [],
                    "high_vulnerability": False,
                    "note": "Places API unavailable — vulnerability assessment limited",
                },
            }

    except Exception as e:
        logger.error(f"get_vulnerable_facilities failed: {e}")
        return {"status": "error", "error_message": str(e)}


def get_congestion_spread_prediction(
    incident_lat: float,
    incident_lng: float,
    radius_km: float = 3.0,
) -> dict:
    """
    Use Maps Route Matrix API to predict traffic failure cascades around the incident.

    Args:
        incident_lat: Incident latitude
        incident_lng: Incident longitude
        radius_km: Radius to check for traffic impact

    Returns:
        dict with congestion index and spread prediction
    """
    try:
        # Grid of surrounding points to check traffic impact
        offset = radius_km / 111.0  # degrees per km approx
        origins = [
            {"waypoint": {"location": {"latLng": {"latitude": incident_lat + offset, "longitude": incident_lng}}}},
            {"waypoint": {"location": {"latLng": {"latitude": incident_lat - offset, "longitude": incident_lng}}}},
            {"waypoint": {"location": {"latLng": {"latitude": incident_lat, "longitude": incident_lng + offset}}}},
            {"waypoint": {"location": {"latLng": {"latitude": incident_lat, "longitude": incident_lng - offset}}}},
        ]
        destinations = [
            {"waypoint": {"location": {"latLng": {"latitude": incident_lat, "longitude": incident_lng}}}}
        ]

        url = "https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix"
        headers = {
            "Content-Type": "application/json",
            "X-Goog-Api-Key": MAPS_KEY,
            "X-Goog-FieldMask": "originIndex,destinationIndex,duration,staticDuration,condition",
        }
        body = {
            "origins": origins,
            "destinations": destinations,
            "travelMode": "DRIVE",
            "routingPreference": "TRAFFIC_AWARE",
        }

        with httpx.Client(timeout=15) as client:
            resp = client.post(url, headers=headers, json=body)

        if resp.status_code == 200:
            results = resp.json()
            congestion_scores = []
            for route in results:
                if isinstance(route, dict) and "duration" in route and "staticDuration" in route:
                    live = int(route["duration"].replace("s", ""))
                    static = int(route["staticDuration"].replace("s", ""))
                    if static > 0:
                        congestion_scores.append(min((live / static) - 1.0, 1.0))

            avg_congestion = sum(congestion_scores) / len(congestion_scores) if congestion_scores else 0.3
            spread_risk = "high" if avg_congestion > 0.6 else "medium" if avg_congestion > 0.3 else "low"

            return {
                "status": "success",
                "data": {
                    "congestion_index": round(avg_congestion, 3),
                    "spread_risk": spread_risk,
                    "routes_analyzed": len(congestion_scores),
                    "traffic_failure_cascade_likely": avg_congestion > 0.5,
                },
            }
        else:
            logger.warning(f"Route Matrix API failed: {resp.status_code}")
            return {
                "status": "degraded",
                "data": {
                    "congestion_index": 0.3,
                    "spread_risk": "medium",
                    "routes_analyzed": 0,
                    "note": "Route Matrix API unavailable — using default estimate",
                },
            }

    except Exception as e:
        logger.error(f"get_congestion_spread_prediction failed: {e}")
        return {"status": "error", "error_message": str(e)}


def update_incident_severity(
    incident_id: str,
    severity_forecast_json: str,
    spread_risk: str,
    affected_population_estimate: int,
    peak_impact_time: str,
) -> dict:
    """
    Write severity forecast results back to the Firestore incident document.

    Args:
        incident_id: Firestore incident ID
        severity_forecast_json: JSON string with t_plus_1h, t_plus_2h, t_plus_6h, uncertainty_range
        spread_risk: low | medium | high
        affected_population_estimate: Estimated affected people
        peak_impact_time: ISO8601 datetime string

    Returns:
        dict with status
    """
    try:
        forecast = json.loads(severity_forecast_json)
        sf = SeverityForecast(
            t_plus_1h=forecast.get("t_plus_1h", 1),
            t_plus_2h=forecast.get("t_plus_2h", 1),
            t_plus_6h=forecast.get("t_plus_6h", 1),
            uncertainty_range=forecast.get("uncertainty_range", 1),
        )
        incident_service._db.collection("incidents").document(incident_id).update({
            "severity_forecast": sf.model_dump(),
            "spread_risk": spread_risk,
            "affected_population_estimate": affected_population_estimate,
            "peak_impact_time": peak_impact_time,
            "updated_at": datetime.utcnow().isoformat(),
        })
        logger.info(f"Severity forecast updated for incident {incident_id}")
        return {"status": "success", "data": {"incident_id": incident_id, "severity_forecast": sf.model_dump()}}
    except Exception as e:
        logger.error(f"update_incident_severity failed: {e}")
        return {"status": "error", "error_message": str(e)}



def get_historical_incidents(
    lat: float,
    lng: float,
    crisis_type: str,
    radius_km: float = 5.0,
    days_back: int = 30,
) -> dict:
    """
    Query Firestore for past incidents near this location to compute baseline probability.

    Args:
        lat: Latitude
        lng: Longitude
        crisis_type: Type of crisis to look up
        radius_km: Search radius in km
        days_back: How many days back to look

    Returns:
        dict with historical stats
    """
    try:
        from datetime import timedelta
        cutoff = (datetime.utcnow() - timedelta(days=days_back)).isoformat()
        docs = list(
            incident_service._db.collection("incidents")
            .where(filter=FieldFilter("crisis_type", "==", crisis_type))
            .where(filter=FieldFilter("created_at", ">=", cutoff))
            .stream()
        )
        if not docs:
            return {
                "status": "success",
                "data": {
                    "count": 0,
                    "avg_severity": 0,
                    "avg_duration_hours": 0,
                    "avg_affected_population": 0,
                    "historical_base_probability": 0.1,
                    "note": "No historical incidents found — using default baseline"
                }
            }

        severities = []
        durations = []
        populations = []
        for doc in docs:
            d = doc.to_dict()
            # Filter by rough radius
            dlat = d.get("location", {}).get("lat", 0) - lat
            dlng = d.get("location", {}).get("lng", 0) - lng
            dist_km = ((dlat**2 + dlng**2) ** 0.5) * 111
            if dist_km <= radius_km:
                severities.append(d.get("severity_level", 1))
                durations.append(d.get("expected_duration_hours", 2))
                populations.append(d.get("affected_population_estimate", 0))

        count = len(severities)
        if count == 0:
            return {
                "status": "success",
                "data": {
                    "count": 0,
                    "avg_severity": 0,
                    "avg_duration_hours": 0,
                    "avg_affected_population": 0,
                    "historical_base_probability": 0.1,
                    "note": "No nearby historical incidents"
                }
            }

        return {
            "status": "success",
            "data": {
                "count": count,
                "avg_severity": round(sum(severities) / count, 1),
                "avg_duration_hours": round(sum(durations) / count, 1),
                "avg_affected_population": int(sum(populations) / count),
                "historical_base_probability": min(count / 10, 0.9),
            }
        }
    except Exception as e:
        logger.error(f"get_historical_incidents failed: {e}")
        return {"status": "error", "error_message": str(e)}


AGENT_INSTRUCTION = """\
You are the Severity & Evolution Prediction Agent (Agent 3) for CIRO.

Given an incident_id, crisis_type, and location, your job is to predict how bad the crisis will get.

Steps:
1. Call get_weather_forecast(lat, lng) — get 6-hour precipitation/temperature trends
2. Call get_vulnerable_facilities(lat, lng) — find hospitals, schools, shelters at risk
3. Call get_congestion_spread_prediction(lat, lng) — predict traffic cascade failures
4. Based on all tool results, determine:
   - severity_forecast: severity level at T+1h, T+2h, T+6h (scale 1-5)
   - spread_risk: low/medium/high
   - affected_population_estimate: integer
   - peak_impact_time: ISO8601 string
5. Call update_incident_severity() to persist results to Firestore

Severity scoring guide:
- Level 1: Minor, localized, no vulnerable facilities affected
- Level 2: Moderate, some disruption, low congestion
- Level 3: Significant, vulnerable facilities at risk, medium congestion
- Level 4: Severe, hospitals/schools threatened, high congestion cascade
- Level 5: Catastrophic, multiple facilities, city-wide impact

For floods: rising precipitation + high congestion = escalating severity
For heatwaves: high temperature forecast + vulnerable elderly facilities = escalating severity
Always call update_incident_severity() at the end to save results.
"""

severity_prediction_agent = Agent(
    name="severity_prediction_agent",
    model="gemini-2.5-flash",
    instruction=AGENT_INSTRUCTION,
    tools=[
        get_historical_incidents,
        get_weather_forecast,
        get_vulnerable_facilities,
        get_congestion_spread_prediction,
        update_incident_severity,
    ],
    generate_content_config=types.GenerateContentConfig(temperature=0.1),
)
