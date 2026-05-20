# agents/crisis_detection/agent.py
# Agent 2: Crisis Detection & Classification
# Reads fused signals, classifies crisis type, outputs confidence score + incident draft

import json
import logging
import os
from datetime import datetime

from dotenv import load_dotenv
from google.adk.agents import Agent
from google.genai import types

from services.incident_service import IncidentService
from schemas.incident import Incident, IncidentLocation, ConflictingHypothesis

load_dotenv()
logger = logging.getLogger(__name__)

incident_service = IncidentService()


def classify_crisis(
    signal_ids: str,
    lat: float,
    lng: float,
    area_name: str,
    signal_summary: str,
) -> dict:
    """
    Classify a crisis from fused signal data and write an incident to Firestore.

    Args:
        signal_ids: Comma-separated list of signal IDs to associate
        lat: Latitude of incident location
        lng: Longitude of incident location
        area_name: Human-readable area name
        signal_summary: JSON string summarizing all signals from Agent 1

    Returns:
        dict with status, incident_id, state, confidence_score, crisis_type
    """
    try:
        from google import genai
        client = genai.Client(api_key=os.getenv("GOOGLE_API_KEY"))
        

        prompt = f"""
You are a crisis detection specialist for CIRO — an urban emergency management system for Islamabad, Pakistan.

Analyze the following fused signals and classify the crisis.

SIGNAL SUMMARY:
{signal_summary}

LOCATION: {area_name} (lat: {lat}, lng: {lng})

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
    "evidence_signal_ids": []
  }},
  "reasoning": "<one sentence explanation>",
  "recommended_state": "MONITORING|HYPOTHESIS|VERIFICATION_REQUESTED|CONFIRMED"
}}

Rules:
- confidence < 0.4 → recommended_state: MONITORING
- confidence 0.4-0.6 → recommended_state: HYPOTHESIS
- confidence 0.6-0.8 → recommended_state: VERIFICATION_REQUESTED
- confidence > 0.8 → recommended_state: CONFIRMED
- If signals conflict (e.g. social says flood but weather shows no rain), lower confidence and populate conflicting_hypothesis
- Be conservative: when in doubt, use lower confidence
"""

        response = client.models.generate_content(model="gemini-2.5-flash", contents=prompt)
        raw = response.text.strip()
        # Strip markdown fences if present
        if raw.startswith("```"):
            raw = raw.split("```")[1]
            if raw.startswith("json"):
                raw = raw[4:]
        classification = json.loads(raw.strip())

        # Build incident
        ids = [s.strip() for s in signal_ids.split(",") if s.strip()]
        conflicting = None
        if classification.get("conflicting_hypothesis", {}).get("type"):
            conflicting = ConflictingHypothesis(
                type=classification["conflicting_hypothesis"]["type"],
                confidence=classification["conflicting_hypothesis"]["confidence"],
                evidence_signal_ids=classification["conflicting_hypothesis"].get("evidence_signal_ids", []),
            )

        incident = Incident(
            crisis_type=classification["crisis_type"],
            severity_level=classification["severity_level"],
            confidence_score=classification["confidence_score"],
            state=classification["recommended_state"],
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

        logger.info(
            f"Crisis classified: {incident.crisis_type} "
            f"(confidence: {incident.confidence_score}, state: {incident.state})"
        )

        return {
            "status": "success",
            "data": {
                "incident_id": incident_id,
                "crisis_type": incident.crisis_type,
                "severity_level": incident.severity_level,
                "confidence_score": incident.confidence_score,
                "state": incident.state,
                "spread_risk": incident.spread_risk,
                "reasoning": classification.get("reasoning", ""),
                "conflicting_hypothesis": classification.get("conflicting_hypothesis"),
            },
        }

    except Exception as e:
        logger.error(f"classify_crisis failed: {e}")
        return {"status": "error", "error_message": str(e)}


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
