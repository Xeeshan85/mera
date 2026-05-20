# agents/crisis_detection/agent.py
# Agent 2: Crisis Detection & Classification
# Reads fused signals, classifies crisis type, outputs confidence score + incident draft
# Implements: duplicate detection, signal marking, agent trace writing

import json
import logging
import os
import uuid
from datetime import datetime

from dotenv import load_dotenv
from google.adk.agents import Agent
from google.genai import types

from services.incident_service import IncidentService
from schemas.incident import Incident, IncidentLocation, ConflictingHypothesis

load_dotenv()
logger = logging.getLogger(__name__)

incident_service = IncidentService()

# ── State Machine ──────────────────────────────────────────────────
VALID_TRANSITIONS = {
    "MONITORING": ["HYPOTHESIS", "CONFIRMED", "RETRACTED", "RESOLVED"],
    "HYPOTHESIS": ["VERIFICATION_REQUESTED", "CONFIRMED", "RETRACTED", "MONITORING"],
    "VERIFICATION_REQUESTED": ["CONFIRMED", "RETRACTED", "HYPOTHESIS"],
    "CONFIRMED": ["RESOLVED", "RETRACTED"],
    "RETRACTED": [],
    "RESOLVED": [],
}


def classify_crisis(
    signal_ids: str,
    lat: float,
    lng: float,
    area_name: str,
    signal_summary: str,
) -> dict:
    """
    Classify a crisis from fused signal data and write an incident to Firestore.
    Checks for duplicate incidents within 3km before creating a new one.
    Marks all processed signal_ids as processed in Firestore.

    Args:
        signal_ids: Comma-separated list of signal IDs to associate
        lat: Latitude of incident location
        lng: Longitude of incident location
        area_name: Human-readable area name
        signal_summary: JSON string summarizing all signals from Agent 1

    Returns:
        dict with status, incident_id, state, confidence_score, crisis_type
    """
    t0 = datetime.utcnow()
    tool_calls = []

    try:
        from google import genai
        client = genai.Client(api_key=os.getenv("GOOGLE_API_KEY"))

        prompt = f"""
You are a crisis detection specialist for CIRO — an urban emergency management system for Islamabad, Pakistan.

Analyze the following fused signals and classify the crisis.

SIGNAL SUMMARY:
{signal_summary}

LOCATION: {area_name} (lat: {lat}, lng: {lng})

Classification rules:
- flood: precipitation_probability > 40% AND (social mentions of flooding OR traffic congestion > 0.6 in same area)
- heatwave: temperature > 40°C AND expected_duration > 3h AND affects vulnerable population
- water_main_burst: social mentions of "pipe burst"/"water gushing" WITHOUT significant precipitation signal
- infrastructure_failure: official source OR multiple social posts about structural/power/bridge issues
- accident: traffic congestion spike + social mentions of collision/crash without weather cause

Confidence scoring:
- Start at 0.3 base
- +0.2 for each corroborating source type (max 4 source types = +0.8)
- +0.1 if highest credibility signal > 0.85
- -0.2 for each active contradiction_flag
- -0.1 if only social signals with no official/weather corroboration
- Cap at 0.95 (never be 100% certain without field confirmation)

When two hypotheses are plausible (e.g. flood vs water main burst):
- Output BOTH with separate confidence scores
- Set verification_needed = true
- Use lower confidence
- Never escalate to full response when hypotheses conflict

Return ONLY valid JSON matching this exact schema (no markdown, no explanation):
{{
  "crisis_type": "flood|heatwave|accident|infrastructure_failure|power_outage|water_main_burst|road_blockage|none",
  "severity_level": <integer 1-5>,
  "confidence_score": <float 0.0-1.0>,
  "affected_population_estimate": <integer>,
  "expected_duration_hours": <float>,
  "spread_risk": "low|medium|high",
  "conflicting_hypothesis": {{
    "type": "<alternative crisis type or null>",
    "confidence": <float 0.0-1.0>,
    "evidence_signal_ids": [],
    "evidence_summary": "<explain why this alternative is plausible, or empty string>"
  }},
  "reasoning": "<one sentence explanation>",
  "verification_needed": <boolean>,
  "recommended_state": "MONITORING|HYPOTHESIS|VERIFICATION_REQUESTED|CONFIRMED"
}}

Rules:
- confidence < 0.4 → recommended_state: MONITORING
- confidence 0.4-0.6 or verification_needed → recommended_state: HYPOTHESIS
- confidence 0.6-0.8 → recommended_state: VERIFICATION_REQUESTED
- confidence > 0.8 → recommended_state: CONFIRMED
- If signals conflict, lower confidence and populate conflicting_hypothesis
- Be conservative: when in doubt, use lower confidence
"""

        t_llm = datetime.utcnow()
        response = client.models.generate_content(model="gemini-2.5-flash", contents=prompt)
        llm_ms = int((datetime.utcnow() - t_llm).total_seconds() * 1000)
        tool_calls.append({"tool": "gemini_classify", "duration_ms": llm_ms, "status": "success"})

        raw = response.text.strip()
        # Strip markdown fences if present
        if raw.startswith("```"):
            raw = raw.split("```")[1]
            if raw.startswith("json"):
                raw = raw[4:]
        classification = json.loads(raw.strip())

        # Determine state using spec rules
        confidence = classification["confidence_score"]
        verification_needed = classification.get("verification_needed", False)
        state = _determine_state(confidence, verification_needed)

        # Build incident data
        ids = [s.strip() for s in signal_ids.split(",") if s.strip()]
        conflicting = None
        ch = classification.get("conflicting_hypothesis", {})
        if ch.get("type"):
            conflicting = ConflictingHypothesis(
                type=ch["type"],
                confidence=ch.get("confidence", 0.0),
                evidence_signal_ids=ch.get("evidence_signal_ids", []),
                evidence_summary=ch.get("evidence_summary", ""),
            )

        crisis_type = classification["crisis_type"]

        # ── DUPLICATE DETECTION ──────────────────────────────────────
        t_dedup = datetime.utcnow()
        existing = incident_service.find_nearby_incident(lat, lng, crisis_type)
        dedup_ms = int((datetime.utcnow() - t_dedup).total_seconds() * 1000)
        tool_calls.append({"tool": "find_nearby_incident", "duration_ms": dedup_ms, "status": "success"})

        if existing:
            # Update existing incident instead of creating a new one
            new_signal_ids = list(set(existing.signal_ids + ids))
            update_data = {
                "signal_ids": new_signal_ids,
                "confidence_score": max(existing.confidence_score, confidence),
                "updated_at": datetime.utcnow().isoformat(),
            }
            # Upgrade state if new evidence is stronger
            if confidence > existing.confidence_score:
                new_state = _determine_state(confidence, verification_needed)
                if new_state in VALID_TRANSITIONS.get(existing.state, []):
                    update_data["state"] = new_state
                    update_data["severity_level"] = classification["severity_level"]

            incident_service._db.collection("incidents").document(existing.incident_id).update(update_data)
            incident_id = existing.incident_id
            logger.info(f"Duplicate detected — updated existing incident {incident_id}")
        else:
            # Create new incident
            incident = Incident(
                crisis_type=crisis_type,
                severity_level=classification["severity_level"],
                confidence_score=confidence,
                state=state,
                location=IncidentLocation(
                    lat=lat,
                    lng=lng,
                    area_name=area_name,
                    affected_radius_km=1.5,
                ),
                affected_population_estimate=classification["affected_population_estimate"],
                expected_duration_hours=classification["expected_duration_hours"],
                spread_risk=classification["spread_risk"],
                conflicting_hypothesis=conflicting,
                signal_ids=ids,
            )
            incident_id = incident_service.write_incident(incident)

        # ── MARK SIGNALS AS PROCESSED ────────────────────────────────
        incident_service.mark_signals_processed(ids, incident_id)

        # ── WRITE AGENT TRACE ────────────────────────────────────────
        duration_ms = int((datetime.utcnow() - t0).total_seconds() * 1000)
        incident_service.write_agent_trace({
            "trace_id": str(uuid.uuid4()),
            "agent": "crisis_detection_agent",
            "incident_id": incident_id,
            "timestamp": datetime.utcnow().isoformat(),
            "input_summary": f"Classified {len(ids)} signals at {area_name}",
            "tool_calls": tool_calls,
            "gemini_reasoning": classification.get("reasoning", ""),
            "decision": f"{crisis_type} detected, confidence={confidence}, state={state}",
            "confidence_scores": {"primary": confidence, "conflicting": ch.get("confidence", 0)},
            "state_transition": {"from": None, "to": state},
            "trade_off_narrative": None,
            "duration_ms": duration_ms,
        })

        logger.info(
            f"Crisis classified: {crisis_type} "
            f"(confidence: {confidence}, state: {state})"
        )

        return {
            "status": "success",
            "data": {
                "incident_id": incident_id,
                "crisis_type": crisis_type,
                "severity_level": classification["severity_level"],
                "confidence_score": confidence,
                "state": state,
                "spread_risk": classification["spread_risk"],
                "reasoning": classification.get("reasoning", ""),
                "conflicting_hypothesis": classification.get("conflicting_hypothesis"),
                "duplicate_of": existing.incident_id if existing else None,
            },
        }

    except Exception as e:
        logger.error(f"classify_crisis failed: {e}")
        return {"status": "error", "error_message": str(e)}


def _determine_state(confidence: float, verification_needed: bool) -> str:
    """Determine incident state based on confidence and verification flag."""
    if confidence < 0.4:
        return "MONITORING"
    elif confidence < 0.6 or verification_needed:
        return "HYPOTHESIS"
    elif confidence < 0.8:
        return "VERIFICATION_REQUESTED"
    else:
        return "CONFIRMED"


def update_incident_state(
    incident_id: str,
    new_state: str,
    reason: str,
    confidence_score: float = None,
) -> dict:
    """
    Transition an incident to a new state via the atomic state machine.

    Args:
        incident_id: Firestore incident document ID
        new_state: Target state (MONITORING/HYPOTHESIS/VERIFICATION_REQUESTED/CONFIRMED/RETRACTED)
        reason: Human-readable reason for the transition
        confidence_score: Updated confidence score (optional)

    Returns:
        dict with status and transition result
    """
    try:
        result = incident_service.transition_state(
            incident_id=incident_id,
            new_state=new_state,
            reason=reason,
            agent="crisis_detection_agent",
            confidence_score=confidence_score,
        )
        return result
    except Exception as e:
        logger.error(f"update_incident_state failed: {e}")
        return {"status": "error", "error_message": str(e)}


AGENT_INSTRUCTION = """\
You are the Crisis Detection & Classification Agent (Agent 2) for CIRO.

Your job:
1. Receive fused signal data from Agent 1 (signal_summary JSON + location)
2. Call classify_crisis() to analyze signals and create an incident in Firestore
3. Return the classification result including incident_id, crisis_type, confidence_score, and recommended state

Rules:
- Always call classify_crisis() — never guess or fabricate results
- If confidence < 0.4: state is MONITORING (no escalation)
- If confidence 0.4-0.6: state is HYPOTHESIS (shadow resources only)
- If confidence 0.6-0.8: state is VERIFICATION_REQUESTED (internal alerts)
- If confidence > 0.8: state is CONFIRMED (full response)
- When signals conflict, report both hypotheses and lower confidence
- Be conservative: false negatives are better than false positives for public trust

Always return the incident_id — it is needed by downstream agents.
"""

crisis_detection_agent = Agent(
    name="crisis_detection_agent",
    model="gemini-2.5-flash",
    instruction=AGENT_INSTRUCTION,
    tools=[classify_crisis, update_incident_state],
    generate_content_config=types.GenerateContentConfig(temperature=0.1),
)
