# services/pubsub_service.py
# Real Google Cloud Pub/Sub integration.
# Topic: ciro-signals (already created)

import json
import logging
import os
from typing import Callable

from google.cloud import pubsub_v1
from dotenv import load_dotenv

from schemas.signal import Signal

load_dotenv()
logger = logging.getLogger(__name__)


class PubSubService:
    """Google Cloud Pub/Sub service for signal publishing and subscribing."""

    def __init__(self):
        self._project_id = os.getenv("GOOGLE_CLOUD_PROJECT", "ciro-hackathon-2026-2")
        self._topic_name = f"projects/{self._project_id}/topics/ciro-signals"
        self._subscription_name = f"projects/{self._project_id}/subscriptions/ciro-signals-sub"

        try:
            self._publisher = pubsub_v1.PublisherClient()
            logger.info(f"Pub/Sub publisher initialized for topic: {self._topic_name}")
        except Exception as e:
            logger.warning(f"Failed to initialize Pub/Sub publisher: {e}")
            self._publisher = None

    async def publish_signal(self, signal: Signal) -> str:
        """
        Serialize signal to JSON and publish to ciro-signals topic.
        Returns message_id on success.
        Fire-and-forget pattern: logs errors but does NOT raise.
        """
        if self._publisher is None:
            logger.warning("Pub/Sub publisher not available. Signal not published.")
            return ""

        try:
            data = json.dumps(signal.to_firestore_dict(), default=str).encode("utf-8")
            future = self._publisher.publish(
                self._topic_name,
                data,
                signal_id=signal.signal_id,
                source_type=signal.source_type,
                source_name=signal.source_name,
            )
            message_id = future.result(timeout=10)
            logger.info(f"Signal {signal.signal_id} published to Pub/Sub (msg_id: {message_id})")
            return message_id
        except Exception as e:
            logger.error(f"Failed to publish signal {signal.signal_id} to Pub/Sub: {e}")
            return ""

    def subscribe_signals(self, callback: Callable[[Signal], None]):
        """
        Subscribe to ciro-signals-sub subscription.
        Deserializes message and calls callback with Signal object.
        Used by Person 2's crisis detection agent.
        """
        if self._publisher is None:
            logger.warning("Pub/Sub not available. Cannot subscribe.")
            return

        subscriber = pubsub_v1.SubscriberClient()

        def _message_handler(message):
            try:
                data = json.loads(message.data.decode("utf-8"))
                signal = Signal.from_firestore_dict(data)
                callback(signal)
                message.ack()
                logger.debug(f"Processed Pub/Sub message for signal {signal.signal_id}")
            except Exception as e:
                logger.error(f"Error processing Pub/Sub message: {e}")
                message.nack()

        streaming_pull_future = subscriber.subscribe(
            self._subscription_name,
            callback=_message_handler,
        )
        logger.info(f"Listening on {self._subscription_name}...")

        try:
            streaming_pull_future.result()
        except KeyboardInterrupt:
            streaming_pull_future.cancel()
            streaming_pull_future.result()
            logger.info("Pub/Sub subscriber stopped.")
