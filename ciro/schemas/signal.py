# schemas/signal.py
# TEAM CONTRACT — DO NOT CHANGE
# This is what Person 2 reads. Every field must be present on every write, even if null.

from pydantic import BaseModel, Field
from typing import Optional, Any
from datetime import datetime
import uuid


class SignalLocation(BaseModel):
    lat: float
    lng: float
    area_name: str
    geolocation_confidence: float  # 0.0-1.0
    radius_m: Optional[float] = None


class Signal(BaseModel):
    signal_id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    source_type: str   # "weather" | "traffic" | "social" | "field_report" | "sensor" | "official"
    source_name: str   # "google_weather" | "owm" | "open_meteo" | "maps_traffic" | "apify_twitter" | "gdacs" | "mock_social"
    timestamp: datetime
    location: SignalLocation
    raw_payload: dict[str, Any]    # full original API response stored here
    credibility_score: float       # 0.0-1.0, final computed score
    urgency_language_score: float  # 0.0-1.0, NLP urgency score
    mention_velocity: int          # tweets/posts per 30 min window (0 for non-social)
    contradiction_flag: bool = False  # True if contradicted by higher-credibility source
    related_incident_id: Optional[str] = None
    processed: bool = False        # Person 2 flips this to True after processing
    degraded_mode: bool = False    # True if this signal came from a fallback source
    created_at: datetime = Field(default_factory=datetime.utcnow)

    def to_firestore_dict(self) -> dict[str, Any]:
        """Convert to a dict suitable for Firestore writes with proper datetime serialization."""
        data = self.model_dump()
        # Convert datetime objects to ISO strings for Firestore
        data["timestamp"] = self.timestamp.isoformat()
        data["created_at"] = self.created_at.isoformat()
        return data

    @classmethod
    def from_firestore_dict(cls, data: dict[str, Any]) -> "Signal":
        """Reconstruct a Signal from a Firestore document dict."""
        if isinstance(data.get("timestamp"), str):
            data["timestamp"] = datetime.fromisoformat(data["timestamp"])
        if isinstance(data.get("created_at"), str):
            data["created_at"] = datetime.fromisoformat(data["created_at"])
        return cls(**data)
