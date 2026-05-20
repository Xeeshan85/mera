# agents/resource_allocation/agent.py
# Agent 4: Resource Allocation & Optimization
# Greedy constraint-satisfaction allocation with multi-incident trade-off narrative

import json
import logging
import os
from datetime import datetime

import httpx
from dotenv import load_dotenv
from google.adk.agents import Agent
from google.genai import types

from services.incident_service import IncidentService

load_dotenv()
logger = logging.getLogger(__name__)
incident_service = IncidentService()

MAPS_KEY = os.getenv("GOOGLE_MAPS_API_KEY", "")

# Resource type → crisis type priority mapping
CRISIS_RESOURCE_PRIORITY = {
    "flood": ["rescue_team", "water_tanker", "ambulance", "police_unit"],
    "heatwave": ["ambulance", "shelter", "water_tanker", "field_team"],
    "accident": ["ambulance", "police_unit", "rescue_team"],
    "infrastructure_failure": ["rescue_team", "field_team", "police_unit"],
    "power_outage": ["generator", "field_team", "police_unit"],
    "water_main_burst": ["field_team", "water_tanker", "police_unit"],
    "road_blockage": ["police_unit", "field_team"],
}


def compute_travel_times(
    incident_lat: float,
    incident_lng: float,
    resource_ids: str,
) -> dict:
    """
    Compute travel times from all available resources to the incident using Route Matrix API.

    Args:
        incident_lat: Incident latitude
        incident_lng: Incident longitude
        resource_ids: Comma-separated resource IDs to evaluate (use 'ALL' for all available)

    Returns:
        dict with status and list of resources with ETA in minutes
    """
    try:
        if resource_ids.strip().upper() == "ALL":
            resources = incident_service.get_available_resources()
        else:
            ids = [r.strip() for r in resource_ids.split(",") if r.strip()]
            resources = []
            for rid in ids:
                doc = incident_service._db.collection("resources").document(rid).get()
                if doc.exists:
                    from schemas.resource import Resource
                    resources.append(Resource.from_firestore_dict(doc.to_dict()))

        if not resources:
            return {"status": "error", "error_message": "No available resources found"}

        # Build Route Matrix request
        origins = [
            {
                "waypoint": {
                    "location": {
                        "latLng": {
                            "latitude": r.current_location.lat,
                            "longitude": r.current_location.lng,
                        }
                    }
                }
            }
            for r in resources
        ]
        destinations = [
            {
                "waypoint": {
                    "location": {
                        "latLng": {
                            "latitude": incident_lat,
                            "longitude": incident_lng,
                        }
                    }
                }
            }
        ]

        url = "https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix"
        headers = {
            "Content-Type": "application/json",
            "X-Goog-Api-Key": MAPS_KEY,
            "X-Goog-FieldMask": "originIndex,destinationIndex,duration,staticDuration,distanceMeters,condition",
        }
        body = {
            "origins": origins,
            "destinations": destinations,
            "travelMode": "DRIVE",
            "routingPreference": "TRAFFIC_AWARE",
        }

        with httpx.Client(timeout=15) as client:
            resp = client.post(url, headers=headers, json=body)

        results = []
        if resp.status_code == 200:
            matrix = resp.json()
            for route in matrix:
                if not isinstance(route, dict):
                    continue
                idx = route.get("originIndex", 0)
                if idx >= len(resources):
                    continue
                resource = resources[idx]
                duration_s = int(route.get("duration", "300s").replace("s", ""))
                distance_m = route.get("distanceMeters", 0)
                results.append({
                    "resource_id": resource.resource_id,
                    "unit_name": resource.unit_name,
                    "type": resource.type,
                    "state": resource.state,
                    "eta_minutes": round(duration_s / 60, 1),
                    "distance_km": round(distance_m / 1000, 2),
                    "current_location": resource.current_location.name,
                })
        else:
            # Fallback: straight-line distance / 30 km/h
            logger.warning(f"Route Matrix failed ({resp.status_code}), using straight-line estimate")
            import math
            for r in resources:
                dlat = r.current_location.lat - incident_lat
                dlng = r.current_location.lng - incident_lng
                dist_km = math.sqrt(dlat**2 + dlng**2) * 111
                eta = (dist_km / 30) * 60
                results.append({
                    "resource_id": r.resource_id,
                    "unit_name": r.unit_name,
                    "type": r.type,
                    "state": r.state,
                    "eta_minutes": round(eta, 1),
                    "distance_km": round(dist_km, 2),
                    "current_location": r.current_location.name,
                    "note": "straight-line estimate",
                })

        results.sort(key=lambda x: x["eta_minutes"])
        return {"status": "success", "data": {"resources_with_eta": results}}

    except Exception as e:
        logger.error(f"compute_travel_times failed: {e}")
        return {"status": "error", "error_message": str(e)}


def allocate_resources(
    incident_id: str,
    crisis_type: str,
    severity_level: int,
    confirmed: bool,
    travel_times_json: str,
) -> dict:
    """
    Greedily allocate resources to an incident based on ETA and priority.
    Sets SHADOW_COMMITTED for unconfirmed incidents, DISPATCHED for confirmed.

    Args:
        incident_id: Target incident ID
        crisis_type: Type of crisis (flood, heatwave, etc.)
        severity_level: 1-5 severity
        confirmed: True if incident state is CONFIRMED, False for HYPOTHESIS
        travel_times_json: JSON string from compute_travel_times output

    Returns:
        dict with allocated resources and narrative
    """
    try:
        travel_data = json.loads(travel_times_json)
        resources_with_eta = travel_data.get("resources_with_eta", [])

        priority_types = CRISIS_RESOURCE_PRIORITY.get(crisis_type, ["ambulance", "police_unit", "rescue_team"])
        units_needed = max(1, severity_level)  # severity 1=1 unit, severity 5=5 units

        # Group by type
        by_type: dict[str, list] = {}
        for r in resources_with_eta:
            t = r["type"]
            by_type.setdefault(t, []).append(r)

        allocated = []
        narrative_parts = []

        for rtype in priority_types:
            if len(allocated) >= units_needed:
                break
            candidates = by_type.get(rtype, [])
            if not candidates:
                narrative_parts.append(f"No {rtype} available")
                continue
            # Pick lowest ETA
            best = candidates[0]
            allocated.append(best)
            state = "DISPATCHED" if confirmed else "SHADOW_COMMITTED"
            incident_service.update_resource_state(
                resource_id=best["resource_id"],
                new_state=state,
                incident_id=incident_id,
                eta_minutes=best["eta_minutes"],
            )
            narrative_parts.append(
                f"{best['unit_name']} ({rtype}) → ETA {best['eta_minutes']}min from {best['current_location']}"
            )

        # Update incident with allocated resource IDs
        allocated_ids = [r["resource_id"] for r in allocated]
        incident_service._db.collection("incidents").document(incident_id).update({
            "resources_allocated": allocated_ids,
            "updated_at": datetime.utcnow().isoformat(),
        })

        narrative = f"Allocated {len(allocated)}/{units_needed} required units for {crisis_type} (severity {severity_level}). " + " | ".join(narrative_parts)

        return {
            "status": "success",
            "data": {
                "incident_id": incident_id,
                "allocated_count": len(allocated),
                "allocated_resources": allocated,
                "resource_state": "DISPATCHED" if confirmed else "SHADOW_COMMITTED",
                "narrative": narrative,
            },
        }

    except Exception as e:
        logger.error(f"allocate_resources failed: {e}")
        return {"status": "error", "error_message": str(e)}


def resolve_resource_conflict(
    incident_a_id: str,
    incident_a_severity: int,
    incident_a_type: str,
    incident_b_id: str,
    incident_b_severity: int,
    incident_b_type: str,
    contested_resource_type: str,
    total_available: int,
) -> dict:
    """
    Produce an explicit trade-off narrative when two incidents compete for the same resource type.

    Args:
        incident_a_id: First incident ID
        incident_a_severity: Severity level 1-5
        incident_a_type: Crisis type
        incident_b_id: Second incident ID
        incident_b_severity: Severity level 1-5
        incident_b_type: Crisis type
        contested_resource_type: The resource type both incidents need
        total_available: Total units of that resource available

    Returns:
        dict with allocation decision and narrative
    """
    try:
        # Priority score = severity * 2 + crisis_weight
        crisis_weight = {"flood": 3, "heatwave": 2, "accident": 2, "infrastructure_failure": 1}
        score_a = incident_a_severity * 2 + crisis_weight.get(incident_a_type, 1)
        score_b = incident_b_severity * 2 + crisis_weight.get(incident_b_type, 1)
        total_score = score_a + score_b

        units_a = round((score_a / total_score) * total_available)
        units_b = total_available - units_a

        narrative = (
            f"RESOURCE CONFLICT — {contested_resource_type.upper()}: "
            f"Incident A ({incident_a_type}, severity {incident_a_severity}) scores {score_a}. "
            f"Incident B ({incident_b_type}, severity {incident_b_severity}) scores {score_b}. "
            f"Allocating {units_a}/{total_available} {contested_resource_type}s to Incident A, "
            f"{units_b}/{total_available} to Incident B. "
            f"{'Recommend requesting mutual aid — Incident B under-resourced.' if units_b == 0 else ''}"
        )

        return {
            "status": "success",
            "data": {
                "incident_a_units": units_a,
                "incident_b_units": units_b,
                "narrative": narrative,
            },
        }
    except Exception as e:
        logger.error(f"resolve_resource_conflict failed: {e}")
        return {"status": "error", "error_message": str(e)}


AGENT_INSTRUCTION = """\
You are the Resource Allocation & Optimization Agent (Agent 4) for CIRO.

Given an incident, allocate the best available emergency resources.

Steps:
1. Call compute_travel_times(incident_lat, incident_lng, 'ALL') to get ETAs for all available resources
2. Call allocate_resources() with the travel times to greedily assign units (lowest ETA first)
3. If two incidents are competing for the same resources, call resolve_resource_conflict() to produce a trade-off narrative
4. Return the allocation result including which units were assigned and their ETAs

Rules:
- CONFIRMED incidents → set resources to DISPATCHED
- HYPOTHESIS incidents → set resources to SHADOW_COMMITTED (reserved but not physically moved)
- Always prefer lowest ETA for each required resource type
- Always produce an explicit narrative explaining allocation decisions
- If a resource type is unavailable, say so clearly and recommend mutual aid
- Severity 1-2: allocate 1-2 units. Severity 3: 3 units. Severity 4-5: 4-5 units
"""

resource_allocation_agent = Agent(
    name="resource_allocation_agent",
    model="gemini-2.5-flash",
    instruction=AGENT_INSTRUCTION,
    tools=[compute_travel_times, allocate_resources, resolve_resource_conflict],
    generate_content_config=types.GenerateContentConfig(temperature=0.1),
)
