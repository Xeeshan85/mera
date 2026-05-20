# agents/resource_allocation/agent.py
# Agent 4: Resource Allocation & Optimization
# Greedy constraint-satisfaction allocation with multi-incident trade-off narrative
# Implements: spec priority formula, crisis-type resource requirements,
#             trade_off_narrative to Firestore, audit log, agent traces

import json
import logging
import math
import os
import uuid
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

# Spec-defined resource requirements per crisis type
CRISIS_RESOURCE_REQUIREMENTS = {
    "flood": {"rescue_team": 2, "police_unit": 2, "water_tanker": 1, "ambulance": 2},
    "heatwave": {"ambulance": 3, "field_team": 1, "shelter": 1},
    "accident": {"ambulance": 2, "police_unit": 2, "rescue_team": 1},
    "infrastructure_failure": {"rescue_team": 2, "generator": 1, "police_unit": 2},
    "power_outage": {"generator": 2, "field_team": 1, "police_unit": 1},
    "water_main_burst": {"water_tanker": 1, "field_team": 1, "police_unit": 1},
    "road_blockage": {"police_unit": 2, "field_team": 1},
}

SPREAD_MULTIPLIER = {"low": 1.0, "medium": 1.5, "high": 2.0}


def compute_priority_score(severity: int, population: int, spread_risk: str, eta_minutes: float) -> float:
    """
    Spec formula: (severity × log10(max(population, 10)) × spread_multiplier) / max(eta, 1)
    """
    return (
        severity
        * math.log10(max(population, 10))
        * SPREAD_MULTIPLIER.get(spread_risk, 1.0)
    ) / max(eta_minutes, 1.0)


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
            # Fallback: straight-line distance / 40 km/h (Islamabad emergency vehicle speed)
            logger.warning(f"Route Matrix failed ({resp.status_code}), using straight-line estimate")
            for r in resources:
                dlat = r.current_location.lat - incident_lat
                dlng = r.current_location.lng - incident_lng
                dist_km = math.sqrt(dlat**2 + dlng**2) * 111
                eta = (dist_km / 40) * 60
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
    Allocate resources using crisis-type-specific requirements from the spec.
    Sets SHADOW_COMMITTED for unconfirmed incidents, DISPATCHED for confirmed.
    Writes trade_off_narrative to incident and appends audit log.

    Args:
        incident_id: Target incident ID
        crisis_type: Type of crisis (flood, heatwave, etc.)
        severity_level: 1-5 severity
        confirmed: True if incident state is CONFIRMED, False for HYPOTHESIS
        travel_times_json: JSON string from compute_travel_times output

    Returns:
        dict with allocated resources and narrative
    """
    t0 = datetime.utcnow()
    tool_calls = []

    try:
        travel_data = json.loads(travel_times_json)
        resources_with_eta = travel_data.get("resources_with_eta", [])

        # Get spec-defined requirements for this crisis type
        requirements = CRISIS_RESOURCE_REQUIREMENTS.get(
            crisis_type, {"ambulance": 1, "police_unit": 1, "rescue_team": 1}
        )

        # Group available resources by type, sorted by ETA
        by_type: dict[str, list] = {}
        for r in resources_with_eta:
            t = r["type"]
            by_type.setdefault(t, []).append(r)

        allocated = []
        narrative_parts = []
        shortages = []

        for rtype, count_needed in requirements.items():
            candidates = by_type.get(rtype, [])
            assigned_for_type = []

            for i in range(count_needed):
                if i < len(candidates):
                    best = candidates[i]
                    allocated.append(best)
                    assigned_for_type.append(best)

                    state = "DISPATCHED" if confirmed else "SHADOW_COMMITTED"
                    incident_service.update_resource_state(
                        resource_id=best["resource_id"],
                        new_state=state,
                        incident_id=incident_id,
                        eta_minutes=best["eta_minutes"],
                    )

            if assigned_for_type:
                etas = ", ".join(f"{r['eta_minutes']}min" for r in assigned_for_type)
                names = ", ".join(r["unit_name"] for r in assigned_for_type)
                narrative_parts.append(
                    f"{len(assigned_for_type)} {rtype}(s) ({names}) ETA: {etas}"
                )

            shortage = count_needed - len(assigned_for_type)
            if shortage > 0:
                shortages.append(f"SHORTAGE: {shortage} {rtype}(s) unavailable")

        # Build trade-off narrative
        resource_state = "DISPATCHED" if confirmed else "SHADOW_COMMITTED"
        total_required = sum(requirements.values())
        narrative = (
            f"Incident [{crisis_type} {incident_id[:8]}, severity {severity_level}]: "
            f"Allocated {len(allocated)}/{total_required} required units ({resource_state}). "
            + " | ".join(narrative_parts)
        )
        if shortages:
            narrative += " | " + " | ".join(shortages)
            narrative += " | Recommendation: Request mutual aid from adjacent sector."

        # Update incident with allocated resource IDs, trade_off_narrative, and audit log
        allocated_ids = [r["resource_id"] for r in allocated]
        from schemas.incident import AuditLogEntry
        audit_entry = AuditLogEntry(
            timestamp=datetime.utcnow().isoformat(),
            action="resource_allocation",
            from_state=None,
            to_state=resource_state,
            reason=f"Allocated {len(allocated)}/{total_required} units for {crisis_type}",
            agent="resource_allocation_agent",
        ).model_dump()

        from google.cloud.firestore_v1 import ArrayUnion
        incident_service._db.collection("incidents").document(incident_id).update({
            "resources_allocated": allocated_ids,
            "trade_off_narrative": narrative if shortages else None,
            "updated_at": datetime.utcnow().isoformat(),
            "audit_log": ArrayUnion([audit_entry]),
        })

        # Write agent trace
        duration_ms = int((datetime.utcnow() - t0).total_seconds() * 1000)
        incident_service.write_agent_trace({
            "trace_id": str(uuid.uuid4()),
            "agent": "resource_allocation_agent",
            "incident_id": incident_id,
            "timestamp": datetime.utcnow().isoformat(),
            "input_summary": f"Allocating for {crisis_type} severity {severity_level}, {len(resources_with_eta)} resources evaluated",
            "tool_calls": tool_calls,
            "gemini_reasoning": None,
            "decision": narrative,
            "confidence_scores": {},
            "state_transition": None,
            "trade_off_narrative": narrative if shortages else None,
            "duration_ms": duration_ms,
        })

        return {
            "status": "success",
            "data": {
                "incident_id": incident_id,
                "allocated_count": len(allocated),
                "total_required": total_required,
                "allocated_resources": allocated,
                "resource_state": resource_state,
                "narrative": narrative,
                "shortages": shortages,
            },
        }

    except Exception as e:
        logger.error(f"allocate_resources failed: {e}")
        return {"status": "error", "error_message": str(e)}


def resolve_resource_conflict(
    incident_a_id: str,
    incident_a_severity: int,
    incident_a_population: int,
    incident_a_type: str,
    incident_a_spread_risk: str,
    incident_b_id: str,
    incident_b_severity: int,
    incident_b_population: int,
    incident_b_type: str,
    incident_b_spread_risk: str,
    contested_resource_type: str,
    total_available: int,
    median_eta_a: float = 10.0,
    median_eta_b: float = 10.0,
) -> dict:
    """
    Produce an explicit trade-off narrative when two incidents compete for the same resource type.
    Uses the spec priority score formula for allocation.

    Args:
        incident_a_id: First incident ID
        incident_a_severity: Severity level 1-5
        incident_a_population: Affected population estimate
        incident_a_type: Crisis type
        incident_a_spread_risk: low|medium|high
        incident_b_id: Second incident ID
        incident_b_severity: Severity level 1-5
        incident_b_population: Affected population estimate
        incident_b_type: Crisis type
        incident_b_spread_risk: low|medium|high
        contested_resource_type: The resource type both incidents need
        total_available: Total units of that resource available
        median_eta_a: Median ETA to incident A
        median_eta_b: Median ETA to incident B

    Returns:
        dict with allocation decision and narrative
    """
    try:
        score_a = compute_priority_score(
            incident_a_severity, incident_a_population, incident_a_spread_risk, median_eta_a
        )
        score_b = compute_priority_score(
            incident_b_severity, incident_b_population, incident_b_spread_risk, median_eta_b
        )
        total_score = score_a + score_b

        units_a = round((score_a / total_score) * total_available) if total_score > 0 else total_available // 2
        units_b = total_available - units_a

        # Ensure at least 1 unit per incident if available
        if units_a == 0 and total_available >= 2:
            units_a = 1
            units_b = total_available - 1
        if units_b == 0 and total_available >= 2:
            units_b = 1
            units_a = total_available - 1

        narrative = (
            f"RESOURCE CONFLICT — {contested_resource_type.upper()}: "
            f"Incident A ({incident_a_type}, severity {incident_a_severity}, "
            f"population {incident_a_population}, priority {score_a:.1f}). "
            f"Incident B ({incident_b_type}, severity {incident_b_severity}, "
            f"population {incident_b_population}, priority {score_b:.1f}). "
            f"Allocating {units_a}/{total_available} to Incident A, "
            f"{units_b}/{total_available} to Incident B. "
        )
        if units_b == 0:
            narrative += "Recommend requesting mutual aid — Incident B under-resourced."

        # Write trade-off narrative to both incidents
        for iid, n in [(incident_a_id, narrative), (incident_b_id, narrative)]:
            try:
                incident_service._db.collection("incidents").document(iid).update({
                    "trade_off_narrative": n,
                    "updated_at": datetime.utcnow().isoformat(),
                })
            except Exception:
                pass

        return {
            "status": "success",
            "data": {
                "incident_a_units": units_a,
                "incident_b_units": units_b,
                "score_a": round(score_a, 2),
                "score_b": round(score_b, 2),
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
2. Call allocate_resources() with the travel times to assign units based on crisis-type requirements
3. If two incidents are competing for the same resources, call resolve_resource_conflict() to produce a trade-off narrative
4. Return the allocation result including which units were assigned and their ETAs

Rules:
- CONFIRMED incidents → set resources to DISPATCHED
- HYPOTHESIS incidents → set resources to SHADOW_COMMITTED (reserved but not physically moved)
- Each crisis type has specific resource requirements (e.g., flood needs 2 rescue teams, 2 police, 1 tanker, 2 ambulances)
- Always prefer lowest ETA for each required resource type
- Always produce an explicit narrative explaining allocation decisions
- If a resource type is unavailable, say so clearly and recommend mutual aid
"""

resource_allocation_agent = Agent(
    name="resource_allocation_agent",
    model="gemini-2.5-flash",
    instruction=AGENT_INSTRUCTION,
    tools=[compute_travel_times, allocate_resources, resolve_resource_conflict],
    generate_content_config=types.GenerateContentConfig(temperature=0.1),
)
