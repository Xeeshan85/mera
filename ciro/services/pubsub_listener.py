# services/pubsub_listener.py
# Listens to ciro-signals Pub/Sub topic and auto-triggers Agent 0 pipeline

import json
import logging
import os
from dotenv import load_dotenv
from google.cloud import pubsub_v1

load_dotenv()
logger = logging.getLogger(__name__)

PROJECT_ID = os.getenv("GOOGLE_CLOUD_PROJECT", "ciro-hackathon-2026-2")
SUBSCRIPTION = f"projects/{PROJECT_ID}/subscriptions/ciro-signals-sub"


def start_listener():
    """Subscribe to ciro-signals and trigger Agent 0 pipeline on each message."""
    from agents.orchestrator.agent import run_pipeline

    subscriber = pubsub_v1.SubscriberClient()

    def callback(message):
        try:
            data = json.loads(message.data.decode("utf-8"))
            logger.info(f"Received signal: {data.get('signal_id')} from {data.get('source_name')}")

            # Extract location from signal
            location = data.get("location", {})
            lat = location.get("lat", 33.6844)
            lng = location.get("lng", 73.0479)
            area_name = location.get("area_name", "Islamabad")
            signal_id = data.get("signal_id", "unknown")

            # Build minimal signal summary for Agent 0
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
                lat=lat,
                lng=lng,
                area_name=area_name,
                signal_ids=signal_id,
            )

            logger.info(f"Pipeline result: {result.get('data', {}).get('routing')} for {area_name}")
            message.ack()

        except Exception as e:
            logger.error(f"Error processing Pub/Sub message: {e}")
            message.nack()

    future = subscriber.subscribe(SUBSCRIPTION, callback=callback)
    logger.info(f"Listening on {SUBSCRIPTION}...")
    print(f"✅ CIRO Pub/Sub listener started on {SUBSCRIPTION}")

    try:
        future.result()
    except KeyboardInterrupt:
        future.cancel()
        print("Listener stopped.")


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    import sys
    sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
    start_listener()
