# schemas/resource.py
from pydantic import BaseModel, Field
from typing import Optional, Any
from datetime import datetime
import uuid


class ResourceLocation(BaseModel):
    lat: float
    lng: float
    name: str = ""


class Resource(BaseModel):
    resource_id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    type: str
    state: str = "AVAILABLE"
    location: ResourceLocation
    name: str = ""
    assigned_incident_id: Optional[str] = None
    eta_minutes: Optional[float] = None
    capacity: int = 1
    unit_name: str
    contact: str = ""
    last_updated: str = Field(default_factory=lambda: datetime.utcnow().isoformat())

    @property
    def current_location(self):
        """Backwards-compatible accessor — returns location with name populated."""
        return self.location

    def to_firestore_dict(self) -> dict[str, Any]:
        data = self.model_dump()
        data["last_updated"] = datetime.utcnow().isoformat()
        return data

    @classmethod
    def from_firestore_dict(cls, data: dict[str, Any]) -> "Resource":
        # Migrate: old Firestore docs store name at top level, not inside location
        if "location" in data and "name" not in data.get("location", {}):
            data["location"]["name"] = data.get("name", "")
        return cls(**data)
