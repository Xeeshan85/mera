# services/pubsub_listener.py
# Listens to BOTH ciro-signals and ciro-incidents Pub/Sub topics
# and auto-triggers the orchestrator pipeline

import json
import logging
import os
import threading

from dotenv import load_dotenv
from google.cloud import pubsub_v1

load_dotenv()
logger = logging.getLogger(__name__)

PROJECT_ID = os.getenv("GOOGLE_CLOUD_PROJECT", "ciro-hackathon-2026-2")
SIGNALS_SUB = f"projects/{PROJECT_ID}/subscriptions/ciro-signals-sub"
INCIDENTS_SUB = f"projects/{PROJECT_ID}/subscriptions/ciro-incidents-sub"


def _handle_signal_message(message):
    """Process a new signal from ciro-signals-sub → run full pipeline."""
    try:
        data = json.loads(message.data.decode("utf-8"))
        logger.info(f"Signal received: {data.get('signal_id')} from {data.get('source_name')}")

        from agents.orchestrator.agent import run_pipeline

        location = data.get("location", {})
        lat = location.get("lat", 33.6844)
        lng = location.get("lng", 73.0479)
        area_name = location.get("area_name", "Islamabad")
        signal_id = data.get("signal_id", "unknown")

        summary = json.dumps({
            "source_type": data.get("source_type"),
            "source_name": data.get("source_name"),
            "credibility_score": data.get("credibility_score"),
            "urgency_language_score": data.get("urgency_language_score"),
            "mention_velocity": data.get("mention_velocity", 0),
            "contradiction_flag": data.get("contradiction_flag", False),
            "raw_payload": data.get("raw_payload", {}),
        })

        result = run_pipeline(
            signal_summary=summary,
            lat=lat, lng=lng,
            area_name=area_name,
            signal_ids=signal_id,
            triggered_by="new_signal",
        )
        logger.info(f"Pipeline result: {result.get('data', {}).get('routing')} for {area_name}")
        message.ack()

    except Exception as e:
        logger.error(f"Error processing signal message: {e}")
        message.nack()


def _handle_incident_message(message):
    """
    Process an incident state change from ciro-incidents-sub.
    Routes to the appropriate orchestrator function based on the new state.

    Phase 3 routing spec:
      MONITORING           → Log only
      HYPOTHESIS           → Severity prediction + shadow-commit field_team
      VERIFICATION_REQUESTED → Severity + resource allocation + internal alerts
      CONFIRMED            → Full response (all agents)
      RETRACTED            → Retraction flow (release resources, retraction notifications)
    """
    try:
        data = json.loads(message.data.decode("utf-8"))
        incident_id = data.get("incident_id", "unknown")
        new_state = data.get("new_state", "")
        reason = data.get("reason", "")
        logger.info(f"Incident state change: {incident_id} → {new_state}")

        from agents.orchestrator.agent import handle_state_change

        result = handle_state_change(
            incident_id=incident_id,
            new_state=new_state,
            reason=reason,
        )
        logger.info(f"State change handled: {incident_id} → {new_state}: {result.get('status')}")
        message.ack()

    except Exception as e:
        logger.error(f"Error processing incident message: {e}")
        message.nack()


def start_signals_listener():
    """Subscribe to ciro-signals-sub and trigger pipeline on each message."""
    try:
        subscriber = pubsub_v1.SubscriberClient()
        future = subscriber.subscribe(SIGNALS_SUB, callback=_handle_signal_message)
        logger.info(f"✅ Listening on {SIGNALS_SUB}")
        print(f"✅ CIRO signals listener started on {SIGNALS_SUB}")
        future.result()  # Blocks
    except KeyboardInterrupt:
        logger.info("Signals listener stopped")
    except Exception as e:
        logger.error(f"Signals listener failed to start: {e}")
        print(f"⚠️  Signals listener failed (non-blocking): {e}")


def start_incidents_listener():
    """Subscribe to ciro-incidents-sub and trigger state routing on each message."""
    try:
        subscriber = pubsub_v1.SubscriberClient()
        future = subscriber.subscribe(INCIDENTS_SUB, callback=_handle_incident_message)
        logger.info(f"✅ Listening on {INCIDENTS_SUB}")
        print(f"✅ CIRO incidents listener started on {INCIDENTS_SUB}")
        future.result()  # Blocks
    except KeyboardInterrupt:
        logger.info("Incidents listener stopped")
    except Exception as e:
        logger.error(f"Incidents listener failed to start: {e}")
        print(f"⚠️  Incidents listener failed (non-blocking): {e}")


def start_all_listeners():
    """Start both signal and incident listeners in separate threads. Called by main.py."""
    t1 = threading.Thread(target=start_signals_listener, daemon=True, name="signals-listener")
    t2 = threading.Thread(target=start_incidents_listener, daemon=True, name="incidents-listener")
    t1.start()
    t2.start()
    logger.info("Both Pub/Sub listeners started")
    # Block to keep threads alive (called from a daemon thread in main.py)
    t1.join()
    t2.join()


# Allow standalone execution: python -m services.pubsub_listener
if __name__ == "__main__":
    import sys
    logging.basicConfig(level=logging.INFO)
    sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
    start_all_listeners()
