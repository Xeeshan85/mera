# agents/stakeholder_notification/agent.py
# Agent 5: Stakeholder Notification & Action Simulation

import json
import logging
import os
import uuid
from datetime import datetime

import httpx
from dotenv import load_dotenv
from google.adk.agents import Agent
from google.genai import types

from services.incident_service import IncidentService
from schemas.notification import Notification

load_dotenv()
logger = logging.getLogger(__name__)
incident_service = IncidentService()
MAPS_KEY = os.getenv("GOOGLE_MAPS_API_KEY", "")


def generate_stakeholder_messages(
    incident_id: str,
    crisis_type: str,
    severity_level: int,
    area_name: str,
    affected_population: int,
    resources_assigned: str,
    is_retraction: bool = False,
    retraction_reason: str = "",
) -> dict:
    """
    Generate tailored messages for all stakeholder types using Gemini.

    Args:
        incident_id: Firestore incident ID
        crisis_type: flood | heatwave | etc.
        severity_level: 1-5
        area_name: Human-readable location
        affected_population: Estimated affected people
        resources_assigned: Comma-separated list of assigned unit names
        is_retraction: True if this is a false alarm retraction
        retraction_reason: Explanation for retraction

    Returns:
        dict with status and generated messages per stakeholder type
    """
    try:
        from google import genai
        client = genai.Client(api_key=os.getenv("GOOGLE_API_KEY"))

        if is_retraction:
            prompt = f"""
Generate retraction messages for a cancelled emergency alert in Islamabad, Pakistan.

Original alert: {crisis_type} in {area_name}
Retraction reason: {retraction_reason}

Return ONLY valid JSON (no markdown):
{{
  "public": "✅ UPDATE: Previous {crisis_type} alert for {area_name} cancelled. [reason in plain Urdu/English]. Area safe.",
  "public_urdu": "✅ اپ ڈیٹ: {area_name} کے لیے پچھلی الرٹ منسوخ کر دی گئی ہے۔ علاقہ محفوظ ہے۔",
  "emergency_services": "STAND DOWN: {crisis_type} incident {area_name} retracted. Return to base. Log reason: {retraction_reason}",
  "hospital": "CANCEL SURGE PREP: {crisis_type} alert for {area_name} was false alarm. Resume normal operations.",
  "media": "CORRECTION: Earlier {crisis_type} report for {area_name} was incorrect. Confirmed: {retraction_reason}. No casualties."
}}
"""
        else:
            prompt = f"""
Generate emergency alert messages for a {crisis_type} in {area_name}, Islamabad, Pakistan.

Severity: {severity_level}/5
Affected population: ~{affected_population}
Resources dispatched: {resources_assigned}

Return ONLY valid JSON (no markdown):
{{
  "public": "<max 160 chars, plain English, what+where+what to do>",
  "public_urdu": "<max 160 chars Urdu translation>",
  "emergency_services": "<full operational brief: incident type, coords, severity, rendezvous point, assigned units>",
  "hospital": "<patient surge forecast: expected types, estimated volume in 2h, bed prep recommendation>",
  "utility": "<infrastructure risk specific to {crisis_type}: what to protect, estimated impact>",
  "transport": "<specific road closure request with alternate route recommendation>",
  "media": "<full incident brief with confidence, timeline, resource status>"
}}
"""

        response = client.models.generate_content(model="gemini-2.5-flash", contents=prompt)
        raw = response.text.strip()
        if raw.startswith("```"):
            raw = raw.split("```")[1]
            if raw.startswith("json"):
                raw = raw[4:]
        messages = json.loads(raw.strip())

        # Write notifications to Firestore
        stakeholder_map = {
            "public": "public",
            "emergency_services": "emergency_services",
            "hospital": "hospital",
            "utility": "utility",
            "transport": "transport",
            "media": "command_center",
        }
        written = []
        for key, stakeholder_type in stakeholder_map.items():
            if key not in messages:
                continue
            notif = Notification(
                incident_id=incident_id,
                stakeholder_type=stakeholder_type,
                channel="fcm" if stakeholder_type == "public" else "dashboard",
                message_title=f"{'✅ RETRACTION' if is_retraction else '⚠️ ALERT'}: {crisis_type.upper()} — {area_name}",
                message_body=messages[key],
                is_retraction=is_retraction,
            )
            incident_service.write_notification(notif)
            written.append(stakeholder_type)

        # Update incident with notifications sent
        incident_service._db.collection("incidents").document(incident_id).update({
            "stakeholder_notifications_sent": written,
            "updated_at": datetime.utcnow().isoformat(),
        })

        # Fire FCM push notifications
        try:
            from firebase_admin import messaging
            if not is_retraction:
                # Public alert → public_alerts topic
                pub_msg = messages.get("public", "")
                if pub_msg:
                    messaging.send(messaging.Message(
                        notification=messaging.Notification(
                            title=f"⚠️ {crisis_type.upper()} ALERT — {area_name}",
                            body=pub_msg[:160],
                        ),
                        data={"incident_id": incident_id, "crisis_type": crisis_type},
                        topic="public_alerts",
                    ))
                # Emergency services alert
                es_msg = messages.get("emergency_services", "")
                if es_msg:
                    messaging.send(messaging.Message(
                        notification=messaging.Notification(
                            title=f"🚨 DISPATCH: {crisis_type.upper()} — {area_name}",
                            body=es_msg[:160],
                        ),
                        data={"incident_id": incident_id},
                        topic="emergency_services",
                    ))
            else:
                messaging.send(messaging.Message(
                    notification=messaging.Notification(
                        title=f"✅ CANCELLED: {crisis_type.upper()} alert — {area_name}",
                        body=messages.get("public", "Alert cancelled. Area safe.")[:160],
                    ),
                    data={"incident_id": incident_id, "is_retraction": "true"},
                    topic="public_alerts",
                ))
        except Exception as fcm_err:
            logger.warning(f"FCM push failed (non-blocking): {fcm_err}")


        return {
            "status": "success",
            "data": {
                "incident_id": incident_id,
                "notifications_written": written,
                "messages": messages,
                "is_retraction": is_retraction,
            },
        }

    except Exception as e:
        logger.error(f"generate_stakeholder_messages failed: {e}")
        return {"status": "error", "error_message": str(e)}


def simulate_response_action(
    incident_id: str,
    action_type: str,
    before_description: str,
    before_metric_value: float,
    before_metric_unit: str,
    action_taken: str,
    expected_after_description: str,
    expected_after_metric_value: float,
    response_time_improvement_minutes: float,
    resource_cost: str,
    side_effects: str,
) -> dict:
    """
    Record a before/after action simulation to Firestore response_actions subcollection.

    Args:
        incident_id: Firestore incident ID
        action_type: traffic_reroute | emergency_dispatch | hospital_prep | public_alert | evacuation
        before_description: Description of current state
        before_metric_value: Numeric metric before action (e.g. 0.85 congestion)
        before_metric_unit: Unit of metric (e.g. congestion_index)
        action_taken: What action is being simulated
        expected_after_description: Description of expected result
        expected_after_metric_value: Expected metric after action
        response_time_improvement_minutes: Time saved in minutes
        resource_cost: Human-readable resource cost (e.g. "2 police units × 3 hours")
        side_effects: Comma-separated list of possible side effects

    Returns:
        dict with status and action record
    """
    try:
        action_record = {
            "action_id": str(uuid.uuid4()),
            "incident_id": incident_id,
            "action_type": action_type,
            "before_state": {
                "description": before_description,
                "metric_value": before_metric_value,
                "metric_unit": before_metric_unit,
            },
            "response_action": action_taken,
            "expected_after_state": {
                "description": expected_after_description,
                "metric_value": expected_after_metric_value,
                "metric_unit": before_metric_unit,
            },
            "response_time_improvement_minutes": response_time_improvement_minutes,
            "resource_cost": resource_cost,
            "possible_side_effects": [s.strip() for s in side_effects.split(",") if s.strip()],
            "simulated_at": datetime.utcnow().isoformat(),
        }

        result = incident_service.write_response_action(incident_id, action_record)
        return result

    except Exception as e:
        logger.error(f"simulate_response_action failed: {e}")
        return {"status": "error", "error_message": str(e)}


def trigger_retraction(incident_id: str, reason: str, crisis_type: str, area_name: str) -> dict:
    """
    Handle full retraction flow: release resources, send retraction messages, update state.

    Args:
        incident_id: Incident to retract
        reason: Why it's being retracted (e.g. "field team confirmed water main burst, not flood")
        crisis_type: Original crisis type
        area_name: Location name

    Returns:
        dict with status and retraction summary
    """
    try:
        # 1. Release all resources
        release_result = incident_service.release_resources_for_incident(incident_id)

        # 2. Transition state to RETRACTED
        state_result = incident_service.transition_state(
            incident_id=incident_id,
            new_state="RETRACTED",
            reason=reason,
            agent="stakeholder_notification_agent",
        )

        # 3. Send retraction messages
        notif_result = generate_stakeholder_messages(
            incident_id=incident_id,
            crisis_type=crisis_type,
            severity_level=1,
            area_name=area_name,
            affected_population=0,
            resources_assigned="",
            is_retraction=True,
            retraction_reason=reason,
        )

        return {
            "status": "success",
            "data": {
                "incident_id": incident_id,
                "resources_released": release_result.get("data", {}).get("released", []),
                "state_transition": state_result,
                "retraction_notifications": notif_result.get("data", {}).get("notifications_written", []),
            },
        }

    except Exception as e:
        logger.error(f"trigger_retraction failed: {e}")
        return {"status": "error", "error_message": str(e)}



def send_fcm_notification(title: str, body: str, topic: str, incident_id: str = "") -> dict:
    """
    Send FCM push notification to Android app subscribers.

    Args:
        title: Notification title
        body: Notification body
        topic: FCM topic — "public_alerts" | "emergency_services" | "admin"
        incident_id: Optional incident ID to include in data payload

    Returns:
        dict with status and message_id
    """
    try:
        from firebase_admin import messaging
        message = messaging.Message(
            notification=messaging.Notification(title=title, body=body),
            data={"incident_id": incident_id, "topic": topic},
            topic=topic,
        )
        message_id = messaging.send(message)
        logger.info(f"FCM sent to topic {topic}: {message_id}")
        return {"status": "success", "data": {"message_id": message_id, "topic": topic}}
    except Exception as e:
        logger.error(f"FCM send failed: {e}")
        return {"status": "error", "error_message": str(e)}


AGENT_INSTRUCTION = """\
You are the Stakeholder Notification & Action Simulation Agent (Agent 5) for CIRO.

For CONFIRMED incidents:
1. Call generate_stakeholder_messages() with incident details to create bilingual alerts
2. Call simulate_response_action() for each major response action (traffic reroute, dispatch, hospital prep)
3. Return a summary of all notifications sent and actions simulated

For RETRACTED incidents:
1. Call trigger_retraction() — this handles everything (resource release + retraction messages + state update)
2. Return confirmation of retraction

Staged alerting rules:
- Always send emergency_services FIRST
- Send public alert for Zone A (immediate area) immediately after
- Note in your response that Zone B alert should be sent 15 minutes later
- Include alternate route recommendations in transport alerts

Message tone:
- Public messages: calm, clear, actionable. Max 160 chars. Bilingual Urdu/English.
- Emergency services: precise, operational, no ambiguity
- Hospital: clinical, numbers-focused
- Always acknowledge uncertainty in media briefings

Never fabricate incident data — only use what is passed to you.
"""

stakeholder_notification_agent = Agent(
    name="stakeholder_notification_agent",
    model="gemini-2.5-flash",
    instruction=AGENT_INSTRUCTION,
    tools=[
        generate_stakeholder_messages,
        send_fcm_notification,
        simulate_response_action,
        trigger_retraction,
    ],
    generate_content_config=types.GenerateContentConfig(temperature=0.3),
)
