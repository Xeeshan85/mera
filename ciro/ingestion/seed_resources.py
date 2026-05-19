# ingestion/seed_resources.py
# One-time Firestore resource seeding script.
# Run as: python -m ingestion.seed_resources

import asyncio
import logging
import os
import uuid
from datetime import datetime

from dotenv import load_dotenv

load_dotenv()

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)

# Realistic Islamabad emergency resources with real coordinates and unit names
RESOURCES = [
    # Ambulances - Rescue 15
    {"type": "ambulance", "unit_name": "Rescue-15-A1", "lat": 33.6938, "lng": 73.0651, "name": "Rescue 15 HQ Sector G-8"},
    {"type": "ambulance", "unit_name": "Rescue-15-A2", "lat": 33.7180, "lng": 73.0580, "name": "Rescue 15 Sub-Station F-6"},
    {"type": "ambulance", "unit_name": "Rescue-15-A3", "lat": 33.6720, "lng": 73.0820, "name": "Rescue 15 Sub-Station I-8"},
    {"type": "ambulance", "unit_name": "Rescue-15-A4", "lat": 33.7050, "lng": 73.0320, "name": "Rescue 15 Sub-Station G-11"},
    {"type": "ambulance", "unit_name": "PIMS-AMB-1",   "lat": 33.7215, "lng": 73.0433, "name": "PIMS Hospital"},
    {"type": "ambulance", "unit_name": "PIMS-AMB-2",   "lat": 33.7215, "lng": 73.0433, "name": "PIMS Hospital"},
    {"type": "ambulance", "unit_name": "POLY-AMB-1",   "lat": 33.7177, "lng": 73.0691, "name": "Polyclinic Hospital"},
    {"type": "ambulance", "unit_name": "POLY-AMB-2",   "lat": 33.7177, "lng": 73.0691, "name": "Polyclinic Hospital"},
    # Police Traffic Units
    {"type": "police_unit", "unit_name": "Traffic-G10", "lat": 33.6844, "lng": 73.0479, "name": "G-10 Police Station"},
    {"type": "police_unit", "unit_name": "Traffic-G6",  "lat": 33.7120, "lng": 73.0590, "name": "G-6 Police Station"},
    {"type": "police_unit", "unit_name": "Traffic-I8",  "lat": 33.6700, "lng": 73.0780, "name": "I-8 Police Station"},
    {"type": "police_unit", "unit_name": "Traffic-F10", "lat": 33.7100, "lng": 73.0200, "name": "F-10 Police Station"},
    {"type": "police_unit", "unit_name": "Traffic-CDA", "lat": 33.7380, "lng": 73.0850, "name": "CDA Headquarters"},
    {"type": "police_unit", "unit_name": "Traffic-ICT", "lat": 33.7295, "lng": 73.0931, "name": "ICT Police HQ"},
    # NDMA Rescue Teams
    {"type": "rescue_team", "unit_name": "NDMA-RT-1", "lat": 33.7215, "lng": 73.0433, "name": "NDMA HQ"},
    {"type": "rescue_team", "unit_name": "NDMA-RT-2", "lat": 33.7215, "lng": 73.0433, "name": "NDMA HQ"},
    {"type": "rescue_team", "unit_name": "CDA-RT-1",  "lat": 33.7380, "lng": 73.0850, "name": "CDA Emergency Cell"},
    {"type": "rescue_team", "unit_name": "CDA-RT-2",  "lat": 33.7380, "lng": 73.0850, "name": "CDA Emergency Cell"},
    # Water Tankers - CDA
    {"type": "water_tanker", "unit_name": "CDA-WT-1", "lat": 33.7350, "lng": 73.0800, "name": "CDA Water Depot Sector H-8"},
    {"type": "water_tanker", "unit_name": "CDA-WT-2", "lat": 33.6500, "lng": 73.1050, "name": "CDA Water Depot I-14"},
    {"type": "water_tanker", "unit_name": "CDA-WT-3", "lat": 33.7600, "lng": 73.0600, "name": "CDA Water Depot E-7"},
    # Field Verification Teams
    {"type": "field_team", "unit_name": "FT-1", "lat": 33.7000, "lng": 73.0600, "name": "Mobile Unit Alpha"},
    {"type": "field_team", "unit_name": "FT-2", "lat": 33.6900, "lng": 73.0700, "name": "Mobile Unit Bravo"},
    # Shelters
    {"type": "shelter", "unit_name": "Shelter-G10-Mosque",  "lat": 33.6860, "lng": 73.0490, "name": "Jamia Masjid G-10/3", "capacity": 300},
    {"type": "shelter", "unit_name": "Shelter-I10-School",  "lat": 33.6660, "lng": 73.0840, "name": "Govt School I-10/1",  "capacity": 500},
    {"type": "shelter", "unit_name": "Shelter-F10-School",  "lat": 33.7090, "lng": 73.0210, "name": "Islamabad Model School F-10", "capacity": 400},
    # Generators
    {"type": "generator", "unit_name": "GEN-1", "lat": 33.7380, "lng": 73.0850, "name": "CDA Utility Depot"},
    {"type": "generator", "unit_name": "GEN-2", "lat": 33.7380, "lng": 73.0850, "name": "CDA Utility Depot"},
    {"type": "generator", "unit_name": "GEN-3", "lat": 33.7215, "lng": 73.0433, "name": "NDMA Equipment Store"},
]


async def seed_resources():
    """Seed Firestore resources collection with Islamabad emergency resources."""
    from services.firestore_service import FirestoreService

    fs = FirestoreService()
    db = fs._db

    print("=" * 60)
    print("CIRO Resource Seeding Script")
    print(f"Seeding {len(RESOURCES)} resources to Firestore...")
    print("=" * 60)

    success_count = 0
    for resource in RESOURCES:
        resource_id = resource["unit_name"].lower().replace(" ", "-")

        doc_data = {
            "resource_id": resource_id,
            "type": resource["type"],
            "unit_name": resource["unit_name"],
            "name": resource["name"],
            "location": {
                "lat": resource["lat"],
                "lng": resource["lng"],
            },
            "state": "AVAILABLE",
            "assigned_incident_id": None,
            "eta_minutes": None,
            "last_updated": datetime.utcnow().isoformat(),
        }

        # Add capacity for shelters
        if "capacity" in resource:
            doc_data["capacity"] = resource["capacity"]

        try:
            doc_ref = db.collection("resources").document(resource_id)
            doc_ref.set(doc_data)
            print(f"  ✓ {resource['type']:15s} | {resource['unit_name']:20s} | {resource['name']}")
            success_count += 1
        except Exception as e:
            print(f"  ✗ {resource['unit_name']}: {e}")

    print("=" * 60)
    print(f"Seeding complete: {success_count}/{len(RESOURCES)} resources written")
    print("=" * 60)


if __name__ == "__main__":
    asyncio.run(seed_resources())
