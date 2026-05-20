# services/agency_service.py
# Agency management — CRUD for emergency agencies + pre-seed data
# Each agency owns resources and tracks response statistics

import logging
import uuid
from datetime import datetime
from typing import Any, Optional

from firebase_admin import firestore

logger = logging.getLogger(__name__)

# Pre-seeded agencies for Pakistan (Islamabad-focused demo)
SEED_AGENCIES = [
    {
        "agency_id": "ndma-national",
        "name": "NDMA",
        "full_name": "National Disaster Management Authority",
        "type": "disaster_management",
        "logo_emoji": "🛡️",
        "jurisdiction": "National",
        "contact": "051-9205037",
        "description": "Federal agency responsible for disaster preparedness, response, and recovery across Pakistan.",
        "resources": [
            {"resource_id": "ndma-r1", "type": "rescue_team", "unit_name": "NDMA Urban SAR Team A", "state": "AVAILABLE", "capacity": 8},
            {"resource_id": "ndma-r2", "type": "rescue_team", "unit_name": "NDMA Flood Response Unit", "state": "AVAILABLE", "capacity": 12},
            {"resource_id": "ndma-r3", "type": "shelter", "unit_name": "NDMA Relief Camp F-9", "state": "AVAILABLE", "capacity": 500},
            {"resource_id": "ndma-r4", "type": "water_tanker", "unit_name": "Water Tanker WT-01", "state": "AVAILABLE", "capacity": 5000},
        ],
        "stats": {"incidents_responded": 47, "avg_response_time_min": 18.5, "resources_deployed_total": 312, "false_alarms_handled": 8},
    },
    {
        "agency_id": "rescue-1122-ict",
        "name": "Rescue 1122",
        "full_name": "Emergency Rescue Service 1122 — Islamabad",
        "type": "rescue",
        "logo_emoji": "🚑",
        "jurisdiction": "Islamabad Capital Territory",
        "contact": "1122",
        "description": "Primary emergency response service providing rescue, medical, and firefighting services.",
        "resources": [
            {"resource_id": "r1122-a1", "type": "ambulance", "unit_name": "Ambulance ICT-01", "state": "AVAILABLE", "capacity": 2},
            {"resource_id": "r1122-a2", "type": "ambulance", "unit_name": "Ambulance ICT-02", "state": "AVAILABLE", "capacity": 2},
            {"resource_id": "r1122-a3", "type": "ambulance", "unit_name": "Ambulance ICT-03", "state": "DISPATCHED", "capacity": 2},
            {"resource_id": "r1122-f1", "type": "fire_truck", "unit_name": "Fire Tender ICT-01", "state": "AVAILABLE", "capacity": 6},
            {"resource_id": "r1122-f2", "type": "fire_truck", "unit_name": "Fire Tender ICT-02", "state": "MAINTENANCE", "capacity": 6},
            {"resource_id": "r1122-t1", "type": "rescue_team", "unit_name": "Urban Rescue Team A", "state": "AVAILABLE", "capacity": 5},
        ],
        "stats": {"incidents_responded": 234, "avg_response_time_min": 7.2, "resources_deployed_total": 1450, "false_alarms_handled": 31},
    },
    {
        "agency_id": "police-ict",
        "name": "ICT Police",
        "full_name": "Islamabad Capital Territory Police",
        "type": "police",
        "logo_emoji": "🚔",
        "jurisdiction": "Islamabad Capital Territory",
        "contact": "15",
        "description": "Law enforcement and traffic management across Islamabad.",
        "resources": [
            {"resource_id": "pol-p1", "type": "police_unit", "unit_name": "Traffic Unit G-10", "state": "AVAILABLE", "capacity": 4},
            {"resource_id": "pol-p2", "type": "police_unit", "unit_name": "Traffic Unit I-8", "state": "AVAILABLE", "capacity": 4},
            {"resource_id": "pol-p3", "type": "police_unit", "unit_name": "Emergency Response Unit", "state": "AVAILABLE", "capacity": 8},
            {"resource_id": "pol-p4", "type": "police_unit", "unit_name": "Crowd Control Unit", "state": "OFF_DUTY", "capacity": 12},
        ],
        "stats": {"incidents_responded": 189, "avg_response_time_min": 11.3, "resources_deployed_total": 890, "false_alarms_handled": 22},
    },
    {
        "agency_id": "cda-emergency",
        "name": "CDA Emergency",
        "full_name": "Capital Development Authority — Emergency Cell",
        "type": "utility",
        "logo_emoji": "🏗️",
        "jurisdiction": "Islamabad Capital Territory",
        "contact": "051-9252756",
        "description": "Infrastructure repair, water supply, and urban services for Islamabad.",
        "resources": [
            {"resource_id": "cda-g1", "type": "generator", "unit_name": "Mobile Generator MG-01", "state": "AVAILABLE", "capacity": 1},
            {"resource_id": "cda-g2", "type": "generator", "unit_name": "Mobile Generator MG-02", "state": "AVAILABLE", "capacity": 1},
            {"resource_id": "cda-w1", "type": "water_tanker", "unit_name": "CDA Water Tanker WT-03", "state": "AVAILABLE", "capacity": 4000},
            {"resource_id": "cda-m1", "type": "repair_crew", "unit_name": "Road Repair Crew A", "state": "AVAILABLE", "capacity": 6},
        ],
        "stats": {"incidents_responded": 78, "avg_response_time_min": 25.0, "resources_deployed_total": 245, "false_alarms_handled": 5},
    },
    {
        "agency_id": "pims-hospital",
        "name": "PIMS Hospital",
        "full_name": "Pakistan Institute of Medical Sciences",
        "type": "hospital",
        "logo_emoji": "🏥",
        "jurisdiction": "Islamabad Capital Territory",
        "contact": "051-9261170",
        "description": "Islamabad's largest public hospital with emergency and trauma services.",
        "resources": [
            {"resource_id": "pims-b1", "type": "hospital_bed", "unit_name": "Emergency Ward Beds", "state": "AVAILABLE", "capacity": 30},
            {"resource_id": "pims-b2", "type": "hospital_bed", "unit_name": "Trauma ICU Beds", "state": "AVAILABLE", "capacity": 8},
            {"resource_id": "pims-a1", "type": "ambulance", "unit_name": "PIMS Ambulance P-01", "state": "AVAILABLE", "capacity": 2},
            {"resource_id": "pims-t1", "type": "medical_team", "unit_name": "Trauma Response Team", "state": "AVAILABLE", "capacity": 5},
        ],
        "stats": {"incidents_responded": 156, "avg_response_time_min": 14.0, "resources_deployed_total": 620, "false_alarms_handled": 12},
    },
    {
        "agency_id": "pdma-punjab",
        "name": "PDMA Punjab",
        "full_name": "Provincial Disaster Management Authority — Punjab",
        "type": "disaster_management",
        "logo_emoji": "🛡️",
        "jurisdiction": "Punjab",
        "contact": "042-99205367",
        "description": "Provincial disaster management for Pakistan's most populous province.",
        "resources": [
            {"resource_id": "pdma-r1", "type": "rescue_team", "unit_name": "Punjab Flood Response A", "state": "AVAILABLE", "capacity": 15},
            {"resource_id": "pdma-r2", "type": "shelter", "unit_name": "Relief Camp Rawalpindi", "state": "AVAILABLE", "capacity": 1000},
            {"resource_id": "pdma-w1", "type": "water_tanker", "unit_name": "PDMA Water Supply Unit", "state": "AVAILABLE", "capacity": 8000},
        ],
        "stats": {"incidents_responded": 92, "avg_response_time_min": 22.0, "resources_deployed_total": 510, "false_alarms_handled": 14},
    },
]


class AgencyService:
    """Agency CRUD and statistics management."""

    def __init__(self):
        self._db = firestore.client()

    def seed_agencies(self):
        """Write pre-seeded agencies to Firestore (idempotent)."""
        batch = self._db.batch()
        for agency in SEED_AGENCIES:
            ref = self._db.collection("agencies").document(agency["agency_id"])
            agency_data = {**agency, "seeded_at": datetime.utcnow().isoformat()}
            batch.set(ref, agency_data, merge=True)
        batch.commit()
        logger.info(f"Seeded {len(SEED_AGENCIES)} agencies to Firestore")

    def get_all_agencies(self) -> list[dict]:
        """Get all agencies from Firestore."""
        try:
            docs = self._db.collection("agencies").stream()
            return [d.to_dict() for d in docs]
        except Exception as e:
            logger.error(f"Failed to get agencies: {e}")
            return []

    def get_agency(self, agency_id: str) -> Optional[dict]:
        """Get a single agency by ID."""
        try:
            doc = self._db.collection("agencies").document(agency_id).get()
            return doc.to_dict() if doc.exists else None
        except Exception as e:
            logger.error(f"Failed to get agency {agency_id}: {e}")
            return None

    def update_resource_state(self, agency_id: str, resource_id: str, new_state: str) -> bool:
        """Update a resource's state within an agency (AVAILABLE, DISPATCHED, MAINTENANCE, OFF_DUTY)."""
        try:
            ref = self._db.collection("agencies").document(agency_id)
            doc = ref.get()
            if not doc.exists:
                return False
            data = doc.to_dict()
            resources = data.get("resources", [])
            for r in resources:
                if r.get("resource_id") == resource_id:
                    r["state"] = new_state
                    break
            ref.update({"resources": resources})
            return True
        except Exception as e:
            logger.error(f"Failed to update resource state: {e}")
            return False

    def increment_stats(self, agency_id: str, incident_responded: bool = True, false_alarm: bool = False):
        """Increment agency stats after an incident response."""
        try:
            ref = self._db.collection("agencies").document(agency_id)
            doc = ref.get()
            if not doc.exists:
                return
            data = doc.to_dict()
            stats = data.get("stats", {})
            if incident_responded:
                stats["incidents_responded"] = stats.get("incidents_responded", 0) + 1
                stats["resources_deployed_total"] = stats.get("resources_deployed_total", 0) + 1
            if false_alarm:
                stats["false_alarms_handled"] = stats.get("false_alarms_handled", 0) + 1
            ref.update({"stats": stats})
        except Exception as e:
            logger.error(f"Failed to update agency stats: {e}")
