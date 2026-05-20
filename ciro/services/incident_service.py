# services/incident_service.py
# Firestore CRUD + atomic state machine for incidents and resources

import json
import logging
import math
import os
import uuid
from datetime import datetime, timedelta
from typing import Optional

import firebase_admin
from firebase_admin import credentials, firestore
from google.cloud.firestore_v1.base_query import FieldFilter
from google.cloud import pubsub_v1
from dotenv import load_dotenv

from schemas.incident import Incident, AuditLogEntry
from schemas.resource import Resource
from schemas.notification import Notification

load_dotenv()
logger = logging.getLogger(__name__)

VALID_TRANSITIONS = {
    "MONITORING": ["HYPOTHESIS", "CONFIRMED", "RETRACTED"],
    "HYPOTHESIS": ["VERIFICATION_REQUESTED", "CONFIRMED", "RETRACTED", "MONITORING"],
    "VERIFICATION_REQUESTED": ["CONFIRMED", "RETRACTED", "HYPOTHESIS"],
    "CONFIRMED": ["RESOLVED", "RETRACTED"],
    "RETRACTED": [],
    "RESOLVED": [],
}


class IncidentService:
    _initialized = False

    def __init__(self):
        if not IncidentService._initialized:
            self._init_firebase()
            IncidentService._initialized = True
        self._db = firestore.client()
        self._project_id = os.getenv("GOOGLE_CLOUD_PROJECT", "ciro-hackathon-2026-2")
        # Pub/Sub publisher for incident events
        try:
            self._publisher = pubsub_v1.PublisherClient()
            self._incidents_topic = f"projects/{self._project_id}/topics/ciro-incidents"
        except Exception as e:
            logger.warning(f"Pub/Sub publisher init failed: {e}")
            self._publisher = None
            self._incidents_topic = None

    @staticmethod
    def _init_firebase():
        if firebase_admin._apps:
            return
        project_id = os.getenv("GOOGLE_CLOUD_PROJECT", "ciro-hackathon-2026-2")
        cred_path = os.getenv("FIREBASE_CREDENTIALS_PATH", "./firebase-admin-sdk.json")
        # Try multiple path resolutions
        candidates = [
            cred_path,
            os.path.join(os.path.dirname(__file__), "..", cred_path),
            os.path.join(os.path.dirname(__file__), "..", "..", cred_path),
        ]
        for p in candidates:
            resolved = os.path.abspath(p)
            if os.path.exists(resolved):
                cred = credentials.Certificate(resolved)
                firebase_admin.initialize_app(cred, {"projectId": project_id})
                logger.info(f"Firebase initialized: {resolved}")
                return
        # Fallback to ADC
        firebase_admin.initialize_app(options={"projectId": project_id})
        logger.warning("Firebase initialized with ADC")

    # ── Incident CRUD ─────────────────────────────────────────────────

    def write_incident(self, incident: Incident) -> str:
        """Create or overwrite an incident document."""
        try:
            data = incident.to_firestore_dict()
            self._db.collection("incidents").document(incident.incident_id).set(data)
            logger.info(f"Incident {incident.incident_id} written (state: {incident.state})")
            return incident.incident_id
        except Exception as e:
            logger.error(f"Failed to write incident {incident.incident_id}: {e}")
            raise

    def get_incident(self, incident_id: str) -> Optional[Incident]:
        """Fetch a single incident by ID."""
        try:
            doc = self._db.collection("incidents").document(incident_id).get()
            if doc.exists:
                return Incident.from_firestore_dict(doc.to_dict())
            return None
        except Exception as e:
            logger.error(f"Failed to get incident {incident_id}: {e}")
            return None

    def get_active_incidents(self) -> list[Incident]:
        """Return all non-resolved, non-retracted incidents."""
        try:
            docs = (
                self._db.collection("incidents")
                .where(filter=FieldFilter("state", "not-in", ["RESOLVED", "RETRACTED"]))
                .stream()
            )
            return [Incident.from_firestore_dict(d.to_dict()) for d in docs]
        except Exception as e:
            logger.error(f"Failed to get active incidents: {e}")
            return []

    # ── State Machine ─────────────────────────────────────────────────

    def transition_state(
        self,
        incident_id: str,
        new_state: str,
        reason: str,
        agent: str,
        confidence_score: Optional[float] = None,
    ) -> dict:
        """
        Atomic state transition using Firestore transaction.
        Returns {"status": "success"|"error", "data": {...}|"error_message": str}
        """
        try:
            doc_ref = self._db.collection("incidents").document(incident_id)

            @firestore.transactional
            def _txn(transaction):
                snapshot = doc_ref.get(transaction=transaction)
                if not snapshot.exists:
                    raise ValueError(f"Incident {incident_id} not found")

                current = snapshot.to_dict()
                current_state = current.get("state", "MONITORING")

                if new_state not in VALID_TRANSITIONS.get(current_state, []):
                    raise ValueError(
                        f"Invalid transition: {current_state} → {new_state}. "
                        f"Allowed: {VALID_TRANSITIONS.get(current_state, [])}"
                    )

                audit_entry = AuditLogEntry(
                    timestamp=datetime.utcnow().isoformat(),
                    action="state_change",
                    from_state=current_state,
                    to_state=new_state,
                    reason=reason,
                    agent=agent,
                ).model_dump()

                update_data = {
                    "state": new_state,
                    "updated_at": datetime.utcnow().isoformat(),
                    "audit_log": firestore.ArrayUnion([audit_entry]),
                }
                if confidence_score is not None:
                    update_data["confidence_score"] = confidence_score

                transaction.update(doc_ref, update_data)
                return {"from": current_state, "to": new_state}

            txn = self._db.transaction()
            result = _txn(txn)
            logger.info(f"Incident {incident_id}: {result['from']} → {result['to']}")

            # Publish state change to Pub/Sub for Person 3's orchestrator
            self._publish_incident_event(incident_id, result['to'], reason)

            return {"status": "success", "data": result}

        except Exception as e:
            logger.error(f"State transition failed for {incident_id}: {e}")
            return {"status": "error", "error_message": str(e)}

    # ── Resource CRUD ─────────────────────────────────────────────────

    def write_resource(self, resource: Resource) -> str:
        try:
            self._db.collection("resources").document(resource.resource_id).set(
                resource.to_firestore_dict()
            )
            return resource.resource_id
        except Exception as e:
            logger.error(f"Failed to write resource {resource.resource_id}: {e}")
            raise

    def get_available_resources(self, resource_type: Optional[str] = None) -> list[Resource]:
        try:
            query = self._db.collection("resources").where(
                filter=FieldFilter("state", "==", "AVAILABLE")
            )
            if resource_type:
                query = query.where(filter=FieldFilter("type", "==", resource_type))
            docs = query.stream()
            return [Resource.from_firestore_dict(d.to_dict()) for d in docs]
        except Exception as e:
            logger.error(f"Failed to get available resources: {e}")
            return []

    def update_resource_state(
        self,
        resource_id: str,
        new_state: str,
        incident_id: Optional[str] = None,
        eta_minutes: Optional[float] = None,
    ) -> dict:
        try:
            update = {
                "state": new_state,
                "last_updated": datetime.utcnow().isoformat(),
            }
            if incident_id is not None:
                update["assigned_incident_id"] = incident_id
            if eta_minutes is not None:
                update["eta_minutes"] = eta_minutes
            self._db.collection("resources").document(resource_id).update(update)
            return {"status": "success", "data": {"resource_id": resource_id, "new_state": new_state}}
        except Exception as e:
            logger.error(f"Failed to update resource {resource_id}: {e}")
            return {"status": "error", "error_message": str(e)}

    def release_resources_for_incident(self, incident_id: str) -> dict:
        """Set all resources assigned to an incident back to AVAILABLE."""
        try:
            docs = (
                self._db.collection("resources")
                .where(filter=FieldFilter("assigned_incident_id", "==", incident_id))
                .stream()
            )
            released = []
            for doc in docs:
                doc.reference.update({
                    "state": "AVAILABLE",
                    "assigned_incident_id": None,
                    "eta_minutes": None,
                    "last_updated": datetime.utcnow().isoformat(),
                })
                released.append(doc.id)
            logger.info(f"Released {len(released)} resources for incident {incident_id}")
            return {"status": "success", "data": {"released": released}}
        except Exception as e:
            logger.error(f"Failed to release resources for {incident_id}: {e}")
            return {"status": "error", "error_message": str(e)}

    # ── Notifications ─────────────────────────────────────────────────

    def write_notification(self, notification: Notification) -> str:
        try:
            self._db.collection("notifications").document(notification.notification_id).set(
                notification.to_firestore_dict()
            )
            return notification.notification_id
        except Exception as e:
            logger.error(f"Failed to write notification: {e}")
            raise

    # ── Response Actions ──────────────────────────────────────────────

    def write_response_action(self, incident_id: str, action: dict) -> dict:
        try:
            self._db.collection("incidents").document(incident_id).collection(
                "response_actions"
            ).add(action)
            return {"status": "success", "data": action}
        except Exception as e:
            logger.error(f"Failed to write response action: {e}")
            return {"status": "error", "error_message": str(e)}

    # ── Pub/Sub Incident Events ──────────────────────────────────────

    def _publish_incident_event(self, incident_id: str, new_state: str, reason: str):
        """Publish state change event to ciro-incidents Pub/Sub topic."""
        if self._publisher is None or self._incidents_topic is None:
            logger.warning("Pub/Sub publisher not available. Incident event not published.")
            return
        try:
            data = json.dumps({
                "incident_id": incident_id,
                "new_state": new_state,
                "reason": reason,
                "timestamp": datetime.utcnow().isoformat(),
            }).encode("utf-8")
            future = self._publisher.publish(
                self._incidents_topic,
                data,
                incident_id=incident_id,
                new_state=new_state,
            )
            msg_id = future.result(timeout=10)
            logger.info(f"Incident event published: {incident_id} → {new_state} (msg_id: {msg_id})")
        except Exception as e:
            logger.error(f"Failed to publish incident event: {e}")

    # ── Agent Traces ─────────────────────────────────────────────

    def write_agent_trace(self, trace: dict):
        """Write agent execution trace to agent_traces/{trace_id}."""
        try:
            trace_id = trace.get("trace_id", str(uuid.uuid4()))
            self._db.collection("agent_traces").document(trace_id).set(trace)
            logger.info(f"Agent trace {trace_id} written")
        except Exception as e:
            logger.error(f"Failed to write agent trace: {e}")

    # ── Duplicate Incident Detection ─────────────────────────────

    def find_nearby_incident(
        self, lat: float, lng: float, crisis_type: str,
        radius_km: float = 3.0, hours_back: float = 2.0
    ) -> Optional[Incident]:
        """
        Find an existing active incident near this location with the same crisis type.
        Used to deduplicate: if found, update existing instead of creating new.
        """
        try:
            cutoff = (datetime.utcnow() - timedelta(hours=hours_back)).isoformat()
            docs = list(
                self._db.collection("incidents")
                .where(filter=FieldFilter("crisis_type", "==", crisis_type))
                .where(filter=FieldFilter("created_at", ">=", cutoff))
                .stream()
            )
            for doc in docs:
                d = doc.to_dict()
                state = d.get("state", "MONITORING")
                if state in ["RESOLVED", "RETRACTED"]:
                    continue
                loc = d.get("location", {})
                dlat = loc.get("lat", 0) - lat
                dlng = loc.get("lng", 0) - lng
                dist_km = math.sqrt(dlat**2 + dlng**2) * 111
                if dist_km <= radius_km:
                    return Incident.from_firestore_dict(d)
            return None
        except Exception as e:
            logger.error(f"find_nearby_incident failed: {e}")
            return None

    # ── Mark Signals Processed ───────────────────────────────────

    def mark_signals_processed(self, signal_ids: list[str], incident_id: str):
        """Mark signal documents as processed after classification."""
        for sid in signal_ids:
            try:
                doc_ref = self._db.collection("signals").document(sid)
                doc = doc_ref.get()
                if doc.exists:
                    doc_ref.update({
                        "processed": True,
                        "related_incident_id": incident_id,
                    })
                    logger.debug(f"Signal {sid} marked as processed")
            except Exception as e:
                logger.warning(f"Failed to mark signal {sid} as processed: {e}")
