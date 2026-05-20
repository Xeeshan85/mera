# services/fallback_manager.py
import logging, uuid
from datetime import datetime
from dotenv import load_dotenv
load_dotenv()
logger = logging.getLogger(__name__)

class FallbackManager:
    def __init__(self):
        from services.incident_service import IncidentService
        self._db = IncidentService()._db

    def log_fallback(self, source_name, error_type, fallback_action, incident_id="", extra={}):
        try:
            event = {
                "event_id": str(uuid.uuid4()),
                "source_name": source_name,
                "error_type": error_type,
                "fallback_triggered": fallback_action,
                "incident_id": incident_id,
                "timestamp": datetime.utcnow().isoformat(),
                **extra,
            }
            self._db.collection("system_events").add(event)
            logger.warning(f"Fallback: {source_name} -> {fallback_action}")
        except Exception as e:
            logger.error(f"Failed to log fallback: {e}")

    def log_api_failure(self, api_name, status_code, incident_id=""):
        self.log_fallback(api_name, f"HTTP_{status_code}", f"{api_name}_fallback", incident_id)

    def log_firestore_failure(self, collection, doc_id, error):
        self.log_fallback("firestore", "write_failure", "local_jsonl_backup",
            extra={"collection": collection, "doc_id": doc_id, "error": error})

_fallback_manager = None
def get_fallback_manager():
    global _fallback_manager
    if _fallback_manager is None:
        _fallback_manager = FallbackManager()
    return _fallback_manager
