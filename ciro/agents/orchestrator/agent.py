# agents/orchestrator/agent.py
# Agent 0: Orchestrator — coordinates the full CIRO pipeline
# Phase 3: enriched trace format, metrics recording, state-change routing

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


# ── Helpers ────────────────────────────────────────────────────────

def _now():
    return datetime.utcnow().isoformat()


def _ms_since(t0):
    return int((datetime.utcnow() - t0).total_seconds() * 1000)


def _make_stage(stage_name, agent_name, t0, result_summary, status="success", extra=None):
    """Build a single stage entry matching phase3 trace spec."""
    entry = {
        "stage": stage_name,
        "agent": agent_name,
        "started_at": t0.isoformat(),
        "completed_at": _now(),
        "duration_ms": _ms_since(t0),
        "result_summary": result_summary,
        "status": status,
    }
    if extra:
        entry.update(extra)
    return entry


def _write_trace(trace: dict):
    """Write Antigravity trace to Firestore agent_traces collection."""
    try:
        svc._db.collection("agent_traces").document(trace["trace_id"]).set(trace)
        logger.info(f"Trace {trace['trace_id']} written to Firestore")
    except Exception as e:
        logger.error(f"Failed to write trace: {e}")


def _write_live_update(headline: str, crisis_type: str, severity: int,
                       incident_id: str, location: str, is_breaking: bool = False,
                       source: str = "agent"):
    """Write a live update entry for the breaking ticker in the Android app."""
    try:
        update_id = str(uuid.uuid4())
        svc._db.collection("live_updates").document(update_id).set({
            "update_id": update_id,
            "headline": headline,
            "crisis_type": crisis_type,
            "severity_level": severity,
            "incident_id": incident_id,
            "source": source,
            "location": location,
            "timestamp": _now(),
            "is_breaking": is_breaking,
        })
        logger.info(f"Live update written: {headline[:50]}...")
    except Exception as e:
        logger.error(f"Failed to write live update: {e}")


def _send_fcm_notification(title: str, body: str, topic: str = "public_alerts",
                           incident_id: str = "", crisis_type: str = ""):
    """Send a real FCM push notification to subscribed devices."""
    try:
        from services.fcm_service import FCMService
        fcm = FCMService()
        result = fcm.send_to_topic(
            topic=topic,
            title=title,
            body=body,
            data={
                "incident_id": incident_id,
                "crisis_type": crisis_type,
                "click_action": "OPEN_INCIDENT",
            },
        )
        logger.info(f"FCM notification sent to '{topic}': {result}")
    except Exception as e:
        logger.error(f"FCM send failed: {e}")


def _record_metrics(trace: dict, false_positive: bool = False):
    """Record pipeline latency metrics to Firestore metrics collection."""
    try:
        from services.metrics_service import MetricsService

        stages = trace.get("stages", [])
        timings = {s["stage"]: s.get("duration_ms", 0) for s in stages}

        agents_invoked = [s["agent"] for s in stages]
        api_calls = {}
        for s in stages:
            for tc in s.get("tool_calls", []):
                api_calls[tc] = api_calls.get(tc, 0) + 1

        MetricsService().record_pipeline_run(
            incident_id=trace.get("incident_id", ""),
            pipeline_run_id=trace.get("pipeline_run_id", ""),
            signal_to_detection_ms=timings.get("crisis_detection", 0),
            detection_to_allocation_ms=(
                timings.get("severity_prediction", 0) + timings.get("resource_allocation", 0)
            ),
            allocation_to_notification_ms=timings.get("stakeholder_notification", 0),
            agents_invoked=agents_invoked,
            api_calls_made=api_calls,
            fallbacks_triggered=trace.get("fallbacks_triggered", []),
            false_positive=false_positive,
        )
    except Exception as e:
        logger.error(f"Failed to record metrics: {e}")


# ── Main Pipeline (triggered by new signals) ──────────────────────

def run_pipeline(
    signal_summary: str,
    lat: float,
    lng: float,
    area_name: str,
    signal_ids: str,
    triggered_by: str = "manual",
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
        triggered_by: new_signal | state_change | manual

    Returns:
        dict with full pipeline trace
    """
    pipeline_start = datetime.utcnow()
    pipeline_run_id = str(uuid.uuid4())
    trace = {
        "trace_id": str(uuid.uuid4()),
        "pipeline_run_id": pipeline_run_id,
        "incident_id": None,
        "triggered_by": triggered_by,
        "timestamp": _now(),
        "stages": [],
        "total_duration_ms": 0,
        "routing_decision": None,
        "fallbacks_triggered": [],
        "false_alarm_recovery": False,
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

        if detection["status"] != "success":
            trace["routing_decision"] = "DETECTION_FAILED"
            trace["total_duration_ms"] = _ms_since(pipeline_start)
            _write_trace(trace)
            return {"status": "error", "error_message": detection["error_message"], "trace": trace}

        d = detection["data"]
        incident_id = d["incident_id"]
        confidence = d["confidence_score"]
        crisis_type = d["crisis_type"]
        severity = d.get("severity_level", 1)
        state = d["state"]

        trace["incident_id"] = incident_id
        trace["stages"].append(_make_stage(
            "crisis_detection", "crisis_detection_agent", t0,
            f"{crisis_type} detected, confidence={confidence}, state={state}",
            extra={
                "confidence_score": confidence,
                "crisis_type": crisis_type,
                "state_transition": f"→ {state}",
                "tool_calls": ["classify_crisis"],
            },
        ))

        # ── ROUTING LOGIC ─────────────────────────────────────────
        if confidence < 0.4:
            trace["routing_decision"] = "MONITORING"
            trace["total_duration_ms"] = _ms_since(pipeline_start)
            logger.info(f"Confidence {confidence} < 0.4 → MONITORING only")
            _write_live_update(
                headline=f"Signal detected near {area_name}: {crisis_type} (low confidence)",
                crisis_type=crisis_type, severity=severity,
                incident_id=incident_id, location=area_name,
            )
            _write_trace(trace)
            _record_metrics(trace)
            return {"status": "success", "data": {"routing": "MONITORING", "incident_id": incident_id}, "trace": trace}

        # ── STAGE 2: Severity Prediction (Agent 3) ────────────────
        t0 = datetime.utcnow()
        tool_calls_3 = []

        weather = get_weather_forecast(lat, lng)
        tool_calls_3.append("get_weather_forecast")

        facilities = get_vulnerable_facilities(lat, lng)
        tool_calls_3.append("get_vulnerable_facilities")

        congestion = get_congestion_spread_prediction(lat, lng)
        tool_calls_3.append("get_congestion_spread_prediction")

        spread_risk = congestion.get("data", {}).get("spread_risk", "medium")
        pop_estimate = d.get("affected_population_estimate",
                             15000 if severity >= 3 else 5000)

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
            affected_population_estimate=pop_estimate,
            peak_impact_time=_now(),
        )
        tool_calls_3.append("update_incident_severity")

        total_facilities = facilities.get("data", {}).get("total_facilities", 0)
        trace["stages"].append(_make_stage(
            "severity_prediction", "severity_prediction_agent", t0,
            f"spread_risk={spread_risk}, facilities={total_facilities}",
            extra={"tool_calls": tool_calls_3},
        ))

        if 0.4 <= confidence < 0.6:
            trace["routing_decision"] = "HYPOTHESIS"
            # Shadow commit only — no public alerts
            t0 = datetime.utcnow()
            travel = compute_travel_times(lat, lng, "ALL")
            allocate_resources(
                incident_id=incident_id,
                crisis_type=crisis_type,
                severity_level=severity,
                confirmed=False,
                travel_times_json=json.dumps(travel.get("data", {})),
            )
            trace["stages"].append(_make_stage(
                "resource_allocation", "resource_allocation_agent", t0,
                "Shadow-committed resources (HYPOTHESIS)",
                extra={"tool_calls": ["compute_travel_times", "allocate_resources"]},
            ))
            trace["total_duration_ms"] = _ms_since(pipeline_start)
            _write_live_update(
                headline=f"Investigating: possible {crisis_type.replace('_', ' ')} near {area_name} — resources pre-staged",
                crisis_type=crisis_type, severity=severity,
                incident_id=incident_id, location=area_name,
            )
            _write_trace(trace)
            _record_metrics(trace)
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
        allocated = allocation.get("data", {}).get("allocated_count", 0)
        narrative = allocation.get("data", {}).get("narrative", "")
        trade_off = allocation.get("data", {}).get("shortages", [])

        trace["stages"].append(_make_stage(
            "resource_allocation", "resource_allocation_agent", t0,
            f"{allocated} units allocated, confirmed={confirmed}",
            extra={
                "resources_allocated": allocated,
                "trade_off_triggered": len(trade_off) > 0,
                "tool_calls": ["compute_travel_times", "allocate_resources"],
            },
        ))

        if 0.6 <= confidence < 0.8:
            trace["routing_decision"] = "VERIFICATION_REQUESTED"
            # Internal alerts only — emergency services
            t0 = datetime.utcnow()
            generate_stakeholder_messages(
                incident_id=incident_id,
                crisis_type=crisis_type,
                severity_level=severity,
                area_name=area_name,
                affected_population=pop_estimate,
                resources_assigned=narrative,
            )
            trace["stages"].append(_make_stage(
                "stakeholder_notification", "stakeholder_notification_agent", t0,
                "Internal alerts only (VERIFICATION_REQUESTED)",
                extra={"staged_alerting": False, "tool_calls": ["generate_stakeholder_messages"]},
            ))
            trace["total_duration_ms"] = _ms_since(pipeline_start)
            _write_trace(trace)
            _record_metrics(trace)
            return {"status": "success", "data": {"routing": "VERIFICATION_REQUESTED", "incident_id": incident_id}, "trace": trace}

        # ── STAGE 4: Full Notifications (Agent 5) — confidence > 0.8
        t0 = datetime.utcnow()
        notifs = generate_stakeholder_messages(
            incident_id=incident_id,
            crisis_type=crisis_type,
            severity_level=severity,
            area_name=area_name,
            affected_population=pop_estimate,
            resources_assigned=narrative,
        )

        # Simulate response actions
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
        simulate_response_action(
            incident_id=incident_id,
            action_type="emergency_dispatch",
            before_description=f"No rescue presence in {area_name}",
            before_metric_value=0,
            before_metric_unit="rescue_teams_present",
            action_taken=f"Dispatch rescue teams to {area_name} crisis zone",
            expected_after_description="Rescue teams on site",
            expected_after_metric_value=2,
            response_time_improvement_minutes=0,
            resource_cost="2 rescue teams x 4 hours",
            side_effects="NDMA HQ coverage reduced during deployment",
        )

        notifications_sent = notifs.get("data", {}).get("notifications_written", [])
        trace["stages"].append(_make_stage(
            "stakeholder_notification", "stakeholder_notification_agent", t0,
            f"Notifications sent: {notifications_sent}",
            extra={
                "notifications_sent": len(notifications_sent),
                "staged_alerting": True,
                "tool_calls": ["generate_stakeholder_messages", "simulate_response_action"],
            },
        ))

        trace["routing_decision"] = "FULL_RESPONSE"
        trace["total_duration_ms"] = _ms_since(pipeline_start)
        _write_trace(trace)
        _record_metrics(trace)

        return {
            "status": "success",
            "data": {
                "routing": "FULL_RESPONSE",
                "incident_id": incident_id,
                "crisis_type": crisis_type,
                "confidence": confidence,
                "resources_allocated": allocated,
                "notifications_sent": notifications_sent,
            },
            "trace": trace,
        }

    except Exception as e:
        logger.error(f"Pipeline failed: {e}")
        trace["routing_decision"] = "PIPELINE_ERROR"
        trace["total_duration_ms"] = _ms_since(pipeline_start)
        _write_trace(trace)
        return {"status": "error", "error_message": str(e), "trace": trace}


# ── State Change Handler (triggered by ciro-incidents-sub) ────────

def handle_state_change(incident_id: str, new_state: str, reason: str = "") -> dict:
    """
    Handle an incident state change event from Pub/Sub.

    Phase 3 routing spec:
      MONITORING              → Log only
      HYPOTHESIS              → Severity prediction + shadow-commit field_team
      VERIFICATION_REQUESTED  → Severity + resource allocation + internal alerts
      CONFIRMED               → Full response (all agents)
      RETRACTED               → Retraction flow

    Args:
        incident_id: The incident that changed state
        new_state: The new state
        reason: Reason for the transition

    Returns:
        dict with routing result
    """
    trace = {
        "trace_id": str(uuid.uuid4()),
        "pipeline_run_id": str(uuid.uuid4()),
        "incident_id": incident_id,
        "triggered_by": "state_change",
        "timestamp": _now(),
        "stages": [],
        "total_duration_ms": 0,
        "routing_decision": None,
        "fallbacks_triggered": [],
        "false_alarm_recovery": new_state == "RETRACTED",
    }
    t_start = datetime.utcnow()

    try:
        # Load incident from Firestore
        doc = svc._db.collection("incidents").document(incident_id).get()
        if not doc.exists:
            return {"status": "error", "error_message": f"Incident {incident_id} not found"}
        inc = doc.to_dict()
        crisis_type = inc.get("crisis_type", "unknown")
        severity = inc.get("severity_level", 1)
        loc = inc.get("location", {})
        lat = loc.get("lat", 33.6844)
        lng = loc.get("lng", 73.0479)
        area_name = loc.get("area_name", "Islamabad")
        pop = inc.get("affected_population_estimate", 5000)

        if new_state == "MONITORING":
            trace["routing_decision"] = "MONITORING"
            logger.info(f"Incident {incident_id} → MONITORING: log only")

        elif new_state == "HYPOTHESIS":
            trace["routing_decision"] = "HYPOTHESIS"
            # Severity prediction only
            t0 = datetime.utcnow()
            get_weather_forecast(lat, lng)
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
                affected_population_estimate=pop,
                peak_impact_time=_now(),
            )
            trace["stages"].append(_make_stage(
                "severity_prediction", "severity_prediction_agent", t0,
                f"HYPOTHESIS: preliminary forecast, spread_risk={spread_risk}",
            ))

            # Shadow-commit a field_team for verification
            t0 = datetime.utcnow()
            travel = compute_travel_times(lat, lng, "ALL")
            allocate_resources(
                incident_id=incident_id,
                crisis_type=crisis_type,
                severity_level=severity,
                confirmed=False,
                travel_times_json=json.dumps(travel.get("data", {})),
            )
            trace["stages"].append(_make_stage(
                "resource_allocation", "resource_allocation_agent", t0,
                "Shadow-committed resources for field verification",
            ))

        elif new_state == "VERIFICATION_REQUESTED":
            trace["routing_decision"] = "VERIFICATION_REQUESTED"
            # Severity + allocation + internal alerts
            t0 = datetime.utcnow()
            congestion = get_congestion_spread_prediction(lat, lng)
            spread_risk = congestion.get("data", {}).get("spread_risk", "medium")
            trace["stages"].append(_make_stage(
                "severity_prediction", "severity_prediction_agent", t0,
                f"Verification: spread_risk={spread_risk}",
            ))

            t0 = datetime.utcnow()
            travel = compute_travel_times(lat, lng, "ALL")
            allocation = allocate_resources(
                incident_id=incident_id,
                crisis_type=crisis_type,
                severity_level=severity,
                confirmed=False,
                travel_times_json=json.dumps(travel.get("data", {})),
            )
            narrative = allocation.get("data", {}).get("narrative", "")
            trace["stages"].append(_make_stage(
                "resource_allocation", "resource_allocation_agent", t0,
                f"Resources allocated for verification: {narrative[:100]}",
            ))

            # Internal alerts only (emergency_services)
            t0 = datetime.utcnow()
            generate_stakeholder_messages(
                incident_id=incident_id,
                crisis_type=crisis_type,
                severity_level=severity,
                area_name=area_name,
                affected_population=pop,
                resources_assigned=narrative,
            )
            trace["stages"].append(_make_stage(
                "stakeholder_notification", "stakeholder_notification_agent", t0,
                "Internal alerts sent to emergency services",
            ))

        elif new_state == "CONFIRMED":
            trace["routing_decision"] = "FULL_RESPONSE"
            # Full response: severity + allocation + all notifications
            t0 = datetime.utcnow()
            congestion = get_congestion_spread_prediction(lat, lng)
            spread_risk = congestion.get("data", {}).get("spread_risk", "medium")
            trace["stages"].append(_make_stage(
                "severity_prediction", "severity_prediction_agent", t0,
                f"Confirmed: spread_risk={spread_risk}",
            ))

            t0 = datetime.utcnow()
            travel = compute_travel_times(lat, lng, "ALL")
            allocation = allocate_resources(
                incident_id=incident_id,
                crisis_type=crisis_type,
                severity_level=severity,
                confirmed=True,
                travel_times_json=json.dumps(travel.get("data", {})),
            )
            narrative = allocation.get("data", {}).get("narrative", "")
            allocated = allocation.get("data", {}).get("allocated_count", 0)
            trace["stages"].append(_make_stage(
                "resource_allocation", "resource_allocation_agent", t0,
                f"DISPATCHED {allocated} units",
                extra={"resources_allocated": allocated},
            ))

            t0 = datetime.utcnow()
            notifs = generate_stakeholder_messages(
                incident_id=incident_id,
                crisis_type=crisis_type,
                severity_level=severity,
                area_name=area_name,
                affected_population=pop,
                resources_assigned=narrative,
            )
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
            notifications_sent = notifs.get("data", {}).get("notifications_written", [])
            trace["stages"].append(_make_stage(
                "stakeholder_notification", "stakeholder_notification_agent", t0,
                f"All stakeholder notifications sent: {notifications_sent}",
                extra={"notifications_sent": len(notifications_sent), "staged_alerting": True},
            ))

            # ── BREAKING TICKER + FCM PUSH ────────────────────
            headline = f"CONFIRMED: {crisis_type.upper().replace('_', ' ')} in {area_name} — SEV {severity}"
            _write_live_update(
                headline=headline, crisis_type=crisis_type, severity=severity,
                incident_id=incident_id, location=area_name, is_breaking=True,
            )
            _send_fcm_notification(
                title=f"⚠️ {crisis_type.upper().replace('_', ' ')} CONFIRMED",
                body=f"{area_name} — Severity {severity}. {allocated} units dispatched.",
                topic="emergency_services",
                incident_id=incident_id, crisis_type=crisis_type,
            )
            _send_fcm_notification(
                title=f"Crisis Alert: {area_name}",
                body=f"{crisis_type.replace('_', ' ').title()} confirmed. Stay safe and follow instructions.",
                topic="public_alerts",
                incident_id=incident_id, crisis_type=crisis_type,
            )

        elif new_state == "RETRACTED":
            trace["routing_decision"] = "RETRACTION"
            trace["false_alarm_recovery"] = True
            t0 = datetime.utcnow()
            result = trigger_retraction(
                incident_id=incident_id,
                reason=reason or "State changed to RETRACTED",
                crisis_type=crisis_type,
                area_name=area_name,
            )
            released = result.get("data", {}).get("resources_released", [])
            trace["stages"].append(_make_stage(
                "retraction", "stakeholder_notification_agent", t0,
                f"Retraction complete: {len(released)} resources released",
            ))

            # ── RETRACTION TICKER + FCM ───────────────────────
            _write_live_update(
                headline=f"RETRACTED: {crisis_type.replace('_', ' ').title()} in {area_name} — False alarm",
                crisis_type=crisis_type, severity=0,
                incident_id=incident_id, location=area_name, is_breaking=False,
            )
            _send_fcm_notification(
                title=f"Alert Retracted: {area_name}",
                body=f"{crisis_type.replace('_', ' ').title()} alert retracted. {len(released)} resources released.",
                topic="public_alerts",
                incident_id=incident_id, crisis_type=crisis_type,
            )

        else:
            trace["routing_decision"] = f"UNKNOWN_STATE_{new_state}"

        trace["total_duration_ms"] = _ms_since(t_start)
        _write_trace(trace)
        _record_metrics(trace, false_positive=(new_state == "RETRACTED"))

        return {
            "status": "success",
            "data": {
                "incident_id": incident_id,
                "new_state": new_state,
                "routing_decision": trace["routing_decision"],
            },
        }

    except Exception as e:
        logger.error(f"handle_state_change failed for {incident_id}: {e}")
        trace["routing_decision"] = "STATE_CHANGE_ERROR"
        trace["total_duration_ms"] = _ms_since(t_start)
        _write_trace(trace)
        return {"status": "error", "error_message": str(e)}


# ── Retraction (direct call from demo/other agents) ───────────────

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


# ── Status Query ──────────────────────────────────────────────────

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


# ── ADK Agent Definition ──────────────────────────────────────────

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

# ADK auto-discovery expects `root_agent`
root_agent = orchestrator_agent
