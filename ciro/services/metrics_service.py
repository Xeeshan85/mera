# services/metrics_service.py
import logging, os, uuid
from datetime import datetime
from dotenv import load_dotenv
load_dotenv()
logger = logging.getLogger(__name__)
MANUAL_DISPATCH_BENCHMARK_MS = 10 * 60 * 1000

class MetricsService:
    def __init__(self):
        from services.incident_service import IncidentService
        self._db = IncidentService()._db

    def record_pipeline_run(self, incident_id, pipeline_run_id,
        signal_to_detection_ms, detection_to_allocation_ms,
        allocation_to_notification_ms, agents_invoked,
        api_calls_made, fallbacks_triggered=[], false_positive=False):
        try:
            total_ms = signal_to_detection_ms + detection_to_allocation_ms + allocation_to_notification_ms
            metric = {
                "metric_id": str(uuid.uuid4()),
                "incident_id": incident_id,
                "pipeline_run_id": pipeline_run_id,
                "signal_to_detection_ms": signal_to_detection_ms,
                "detection_to_allocation_ms": detection_to_allocation_ms,
                "allocation_to_notification_ms": allocation_to_notification_ms,
                "total_end_to_end_ms": total_ms,
                "agents_invoked": agents_invoked,
                "api_calls_made": api_calls_made,
                "fallbacks_triggered": fallbacks_triggered,
                "false_positive": false_positive,
                "recorded_at": datetime.utcnow().isoformat(),
                "manual_benchmark_ms": MANUAL_DISPATCH_BENCHMARK_MS,
                "improvement_ratio": round(MANUAL_DISPATCH_BENCHMARK_MS / max(total_ms, 1), 1),
            }
            self._db.collection("metrics").add(metric)
            logger.info(f"Metrics recorded: {total_ms}ms total")
            return {"status": "success", "data": metric}
        except Exception as e:
            logger.error(f"Failed to record metrics: {e}")
            return {"status": "error", "error_message": str(e)}

    def get_summary(self):
        try:
            docs = list(self._db.collection("metrics").limit(100).stream())
            if not docs:
                return {"status": "success", "data": {"total_runs": 0, "avg_total_ms": 0, "improvement_ratio": 0, "false_positive_rate": 0}}
            records = [d.to_dict() for d in docs]
            total_runs = len(records)
            avg_ms = int(sum(r.get("total_end_to_end_ms", 0) for r in records) / total_runs)
            false_positives = sum(1 for r in records if r.get("false_positive"))
            improvement = round(MANUAL_DISPATCH_BENCHMARK_MS / max(avg_ms, 1), 1)
            return {"status": "success", "data": {
                "total_runs": total_runs,
                "avg_total_ms": avg_ms,
                "avg_total_seconds": round(avg_ms / 1000, 1),
                "improvement_ratio": improvement,
                "false_positive_rate": round(false_positives / total_runs, 2),
                "manual_benchmark_ms": MANUAL_DISPATCH_BENCHMARK_MS,
                "headline": f"CIRO is {improvement}x faster than manual dispatch",
            }}
        except Exception as e:
            return {"status": "error", "error_message": str(e)}
