# schemas/notification.py
from pydantic import BaseModel, Field
from typing import Any
from datetime import datetime
import uuid


class Notification(BaseModel):
    notification_id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    incident_id: str
    stakeholder_type: str  # public | emergency_services | hospital | utility | transport | media | command_center
    channel: str = "dashboard"  # fcm | sms | email | dashboard
    message_title: str
    message_body: str
    is_retraction: bool = False
    sent_at: str = Field(default_factory=lambda: datetime.utcnow().isoformat())
    delivery_status: str = "sent"  # sent | delivered | failed

    def to_firestore_dict(self) -> dict[str, Any]:
        return self.model_dump()

    @classmethod
    def from_firestore_dict(cls, data: dict[str, Any]) -> "Notification":
        return cls(**data)
