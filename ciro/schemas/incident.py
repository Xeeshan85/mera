# schemas/incident.py
# TEAM CONTRACT — DO NOT CHANGE WITHOUT CONSENSUS
from pydantic import BaseModel, Field
from typing import Optional, Any
from datetime import datetime
import uuid


class IncidentLocation(BaseModel):
    lat: float
    lng: float
    area_name: str
    affected_radius_km: float = 1.0


class ConflictingHypothesis(BaseModel):
    type: str
    confidence: float
    evidence_signal_ids: list[str] = []
    evidence_summary: str = ""


class SeverityForecast(BaseModel):
    t_plus_1h: int  # 1-5
    t_plus_2h: int
    t_plus_6h: int
    uncertainty_range: int = 1


class AuditLogEntry(BaseModel):
    timestamp: str
    action: str
    from_state: Optional[str] = None
    to_state: Optional[str] = None
    reason: str
    agent: str


class Incident(BaseModel):
    incident_id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    state: str = "MONITORING"  # MONITORING | HYPOTHESIS | VERIFICATION_REQUESTED | CONFIRMED | RETRACTED | RESOLVED
    crisis_type: str  # flood | heatwave | accident | infrastructure_failure | power_outage | water_main_burst | road_blockage
    severity_level: int = 1  # 1-5
    confidence_score: float = 0.0
    conflicting_hypothesis: Optional[ConflictingHypothesis] = None
    location: IncidentLocation
    affected_population_estimate: int = 0
    expected_duration_hours: float = 2.0
    peak_impact_time: Optional[str] = None
    spread_risk: str = "low"  # low | medium | high
    severity_forecast: Optional[SeverityForecast] = None
    resources_allocated: list[str] = []
    stakeholder_notifications_sent: list[str] = []
    signal_ids: list[str] = []
    response_actions: list[dict] = []
    audit_log: list[AuditLogEntry] = []
    created_at: str = Field(default_factory=lambda: datetime.utcnow().isoformat())
    updated_at: str = Field(default_factory=lambda: datetime.utcnow().isoformat())

    def to_firestore_dict(self) -> dict[str, Any]:
        data = self.model_dump()
        data["updated_at"] = datetime.utcnow().isoformat()
        return data

    @classmethod
    def from_firestore_dict(cls, data: dict[str, Any]) -> "Incident":
        return cls(**data)
