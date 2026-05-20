# agents/orchestrator/agent.py
# Agent 0: Orchestrator — coordinates the full CIRO pipeline

import json
import logging
import os
import uuid
from datetime import datetime

from dotenv import load_dotenv
# pyrefly: ignore [missing-import]
from google.adk.agents import Agent
# pyrefly: ignore [missing-import]
from google.genai import types

from services.incident_service import IncidentService
from agents.crisis_detection.agent import classify_crisis, update_incident_state
from agents.severity_prediction.agent import (
    get_weather_forecast, get_vulnerable_facilities,
    get_congestion_spread_prediction, update_incident_severity,
)
from agents.resource_allocation.agent import compute_travel_times, allocate_resources
from agents.stakeholder_notification.agent import (
    generate_stakeholder_messages, simulate_response_action, trigger_retraction,
)

load_dotenv()
logger = logging.getLogger(__name__)
svc = IncidentService()


def run_pipeline(
    signal_summary: str,
    lat: float,
    lng: float,
    area_name: str,
    signal_ids: str,
) -> dict:
    """
    Run the full CIRO agent pipeline for a set of fused signals.
    Implements confidence-based routing:
      < 0.4  → MONITORING only
      0.4-0.6 → HYPOTHESIS + Agent 3 only
      0.6-0.8 → VERIFICATION_REQUESTED + Agents 3 & 4
      > 0.8  → CONFIRMED + all agents

    Args:
        signal_summary: JSON string of fused signals from Agent 1
        lat: Incident latitude
        lng: Incident longitude
        area_name: Human-readable area name
        signal_ids: Comma-separated signal IDs

    Returns:
        dict with full pipeline trace
    """
    pipeline_run_id = str(uuid.uuid4())
    trace = {
        "trace_id": str(uuid.uuid4()),
        "pipeline_run_id": pipeline_run_id,
        "incident_id": None,
        "timestamp": datetime.utcnow().isoformat(),
        "stages": [],
        "confidence_at_detection": 0.0,
        "routing_decision": None,
        "resource_trade_off_narrative": None,
        "actions_simulated": [],
        "fallbacks_triggered": [],
    }

    try:
        # ── STAGE 1: Crisis Detection (Agent 2) ───────────────────
        t0 = datetime.utcnow()
        detection = classify_crisis(
            signal_ids=signal_ids,
            lat=lat, lng=lng,
            area_name=area_name,
            signal_summary=signal_summary,
        )
        duration_ms = int((datetime.utcnow() - t0).total_seconds() * 1000)

        if detection["status"] != "success":
            trace["routing_decision"] = "DETECTION_FAILED"
            _write_trace(trace)
            return {"status": "error", "error_message": detection["error_message"], "trace": trace}

        d = detection["data"]
        incident_id = d["incident_id"]
        confidence = d["confidence_score"]
        crisis_type = d["crisis_type"]
        severity = d.get("severity_level", 1)
        state = d["state"]

        trace["incident_id"] = incident_id
        trace["confidence_at_detection"] = confidence
        trace["stages"].append({
            "agent": "crisis_detection_agent",
            "duration_ms": duration_ms,
            "output_summary": f"{crisis_type} detected, confidence={confidence}, state={state}",
        })

        # ── ROUTING LOGIC ─────────────────────────────────────────
        if confidence < 0.4:
            trace["routing_decision"] = "MONITORING"
            logger.info(f"Confidence {confidence} < 0.4 → MONITORING only")
            _write_trace(trace)
            return {"status": "success", "data": {"routing": "MONITORING", "incident_id": incident_id}, "trace": trace}

        # ── STAGE 2: Severity Prediction (Agent 3) ────────────────
        t0 = datetime.utcnow()
        weather = get_weather_forecast(lat, lng)
        facilities = get_vulnerable_facilities(lat, lng)
        congestion = get_congestion_spread_prediction(lat, lng)
        spread_risk = congestion.get("data", {}).get("spread_risk", "medium")

        severity_forecast = json.dumps({
            "t_plus_1h": min(severity + 1, 5),
            "t_plus_2h": min(severity + 1, 5),
            "t_plus_6h": max(severity - 1, 1),
            "uncertainty_range": 1,
        })
        update_incident_severity(
            incident_id=incident_id,
            severity_forecast_json=severity_forecast,
            spread_risk=spread_risk,
            affected_population_estimate=15000 if severity >= 3 else 5000,
            peak_impact_time=datetime.utcnow().isoformat(),
        )
        duration_ms = int((datetime.utcnow() - t0).total_seconds() * 1000)
        trace["stages"].append({
            "agent": "severity_prediction_agent",
            "duration_ms": duration_ms,
            "output_summary": f"spread_risk={spread_risk}, facilities={facilities['data']['total_facilities']}",
        })

        if 0.4 <= confidence < 0.6:
            trace["routing_decision"] = "HYPOTHESIS"
            # Shadow commit only — no public alerts
            travel = compute_travel_times(lat, lng, "ALL")
            allocate_resources(
                incident_id=incident_id,
                crisis_type=crisis_type,
                severity_level=severity,
                confirmed=False,
                travel_times_json=json.dumps(travel.get("data", {})),
            )
            _write_trace(trace)
            return {"status": "success", "data": {"routing": "HYPOTHESIS", "incident_id": incident_id}, "trace": trace}

        # ── STAGE 3: Resource Allocation (Agent 4) ────────────────
        t0 = datetime.utcnow()
        travel = compute_travel_times(lat, lng, "ALL")
        confirmed = confidence > 0.8
        allocation = allocate_resources(
            incident_id=incident_id,
            crisis_type=crisis_type,
            severity_level=severity,
            confirmed=confirmed,
            travel_times_json=json.dumps(travel.get("data", {})),
        )
        duration_ms = int((datetime.utcnow() - t0).total_seconds() * 1000)
        allocated = allocation.get("data", {}).get("allocated_count", 0)
        narrative = allocation.get("data", {}).get("narrative", "")
        trace["resource_trade_off_narrative"] = narrative
        trace["stages"].append({
            "agent": "resource_allocation_agent",
            "duration_ms": duration_ms,
            "output_summary": f"{allocated} units allocated, confirmed={confirmed}",
        })

        if 0.6 <= confidence < 0.8:
            trace["routing_decision"] = "VERIFICATION_REQUESTED"
            # Internal alerts only — no public notification
            generate_stakeholder_messages(
                incident_id=incident_id,
                crisis_type=crisis_type,
                severity_level=severity,
                area_name=area_name,
                affected_population=5000,
                resources_assigned=narrative,
            )
            _write_trace(trace)
            return {"status": "success", "data": {"routing": "VERIFICATION_REQUESTED", "incident_id": incident_id}, "trace": trace}

        # ── STAGE 4: Full Notifications (Agent 5) — confidence > 0.8
        t0 = datetime.utcnow()
        notifs = generate_stakeholder_messages(
            incident_id=incident_id,
            crisis_type=crisis_type,
            severity_level=severity,
            area_name=area_name,
            affected_population=15000,
            resources_assigned=narrative,
        )
        # Simulate traffic reroute action
        simulate_response_action(
            incident_id=incident_id,
            action_type="traffic_reroute",
            before_description=f"Normal traffic flow near {area_name}",
            before_metric_value=0.8,
            before_metric_unit="congestion_index",
            action_taken=f"Reroute traffic away from {area_name} crisis zone",
            expected_after_description="Traffic redistributed via alternate routes",
            expected_after_metric_value=0.4,
            response_time_improvement_minutes=5.0,
            resource_cost="2 police units x 2 hours",
            side_effects="Adjacent road congestion +0.15",
        )
        duration_ms = int((datetime.utcnow() - t0).total_seconds() * 1000)
        trace["stages"].append({
            "agent": "stakeholder_notification_agent",
            "duration_ms": duration_ms,
            "output_summary": f"Notifications sent: {notifs.get('data', {}).get('notifications_written', [])}",
        })

        trace["routing_decision"] = "FULL_RESPONSE"
        _write_trace(trace)
        return {
            "status": "success",
            "data": {
                "routing": "FULL_RESPONSE",
                "incident_id": incident_id,
                "crisis_type": crisis_type,
                "confidence": confidence,
                "resources_allocated": allocated,
                "notifications_sent": notifs.get("data", {}).get("notifications_written", []),
            },
            "trace": trace,
        }

    except Exception as e:
        logger.error(f"Pipeline failed: {e}")
        trace["routing_decision"] = "PIPELINE_ERROR"
        _write_trace(trace)
        return {"status": "error", "error_message": str(e), "trace": trace}


def retract_incident(incident_id: str, reason: str, crisis_type: str, area_name: str) -> dict:
    """
    Retract a false alarm incident — releases resources and sends retraction notifications.

    Args:
        incident_id: Incident to retract
        reason: Why it is being retracted
        crisis_type: Original crisis type
        area_name: Location name

    Returns:
        dict with retraction result
    """
    try:
        result = trigger_retraction(
            incident_id=incident_id,
            reason=reason,
            crisis_type=crisis_type,
            area_name=area_name,
        )
        logger.info(f"Incident {incident_id} retracted: {reason}")
        return result
    except Exception as e:
        logger.error(f"retract_incident failed: {e}")
        return {"status": "error", "error_message": str(e)}


def get_pipeline_status() -> dict:
    """
    Get current status of all active incidents and available resources.

    Returns:
        dict with active incidents and resource summary
    """
    try:
        incidents = svc.get_active_incidents()
        resources = svc.get_available_resources()
        return {
            "status": "success",
            "data": {
                "active_incidents": [
                    {
                        "incident_id": i.incident_id,
                        "crisis_type": i.crisis_type,
                        "state": i.state,
                        "severity_level": i.severity_level,
                        "confidence_score": i.confidence_score,
                        "area_name": i.location.area_name,
                    }
                    for i in incidents
                ],
                "available_resources": len(resources),
                "total_active": len(incidents),
            },
        }
    except Exception as e:
        logger.error(f"get_pipeline_status failed: {e}")
        return {"status": "error", "error_message": str(e)}


def _write_trace(trace: dict):
    """Write Antigravity trace to Firestore agent_traces collection."""
    try:
        svc._db.collection("agent_traces").document(trace["trace_id"]).set(trace)
        logger.info(f"Trace {trace['trace_id']} written to Firestore")
    except Exception as e:
        logger.error(f"Failed to write trace: {e}")


AGENT_INSTRUCTION = """\
You are the Orchestrator Agent (Agent 0) for CIRO — Crisis Intelligence & Response Orchestrator.

You are the brain of the system. You coordinate all other agents.

When you receive fused signal data:
1. Call run_pipeline() with the signal summary and location
2. The pipeline automatically routes through agents based on confidence:
   - < 0.4: MONITORING only (log and wait)
   - 0.4-0.6: HYPOTHESIS (run severity prediction, shadow-commit resources)
   - 0.6-0.8: VERIFICATION_REQUESTED (allocate resources, internal alerts only)
   - > 0.8: CONFIRMED (full response — all agents, all notifications)
3. Report the routing decision and what actions were taken

When you receive a retraction request:
1. Call retract_incident() with the incident ID and reason
2. Resources will be released and retraction notifications sent automatically

To check system status:
1. Call get_pipeline_status() to see all active incidents and available resources

Always report:
- Which routing path was taken and why
- How many resources were allocated
- Which stakeholders were notified
- The incident ID for tracking
"""

orchestrator_agent = Agent(
    name="orchestrator_agent",
    model="gemini-2.5-flash",
    instruction=AGENT_INSTRUCTION,
    tools=[run_pipeline, retract_incident, get_pipeline_status],
    generate_content_config=types.GenerateContentConfig(temperature=0.1),
)
