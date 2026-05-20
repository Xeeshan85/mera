# services/fcm_service.py
# Dedicated Firebase Cloud Messaging service for CIRO push notifications

import logging
from typing import Optional

logger = logging.getLogger(__name__)


class FCMService:
    """Sends FCM push notifications to Android app subscribers."""

    def send_to_topic(
        self,
        topic: str,
        title: str,
        body: str,
        data: Optional[dict[str, str]] = None,
    ) -> dict:
        """
        Send FCM notification to a topic.

        Topics: public_alerts, emergency_services, admin_alerts, hospital_prep,
                alerts_g10, alerts_i10, alerts_f10, alerts_g11

        Returns dict with status and message_id.
        """
        try:
            from firebase_admin import messaging

            message = messaging.Message(
                notification=messaging.Notification(title=title, body=body),
                data=data or {},
                topic=topic,
                android=messaging.AndroidConfig(
                    priority="high",
                    notification=messaging.AndroidNotification(
                        sound="default",
                        channel_id="ciro_alerts",
                    ),
                ),
            )
            message_id = messaging.send(message)
            logger.info(f"FCM sent to topic '{topic}': {message_id}")
            return {"status": "success", "data": {"message_id": message_id, "topic": topic}}
        except Exception as e:
            logger.error(f"FCM send_to_topic failed: {e}")
            return {"status": "error", "error_message": str(e)}

    def send_to_token(
        self,
        token: str,
        title: str,
        body: str,
        data: Optional[dict[str, str]] = None,
    ) -> dict:
        """Send FCM notification to a specific device token."""
        try:
            from firebase_admin import messaging

            message = messaging.Message(
                notification=messaging.Notification(title=title, body=body),
                data=data or {},
                token=token,
                android=messaging.AndroidConfig(
                    priority="high",
                    notification=messaging.AndroidNotification(
                        sound="default",
                        channel_id="ciro_alerts",
                    ),
                ),
            )
            message_id = messaging.send(message)
            logger.info(f"FCM sent to token: {message_id}")
            return {"status": "success", "data": {"message_id": message_id}}
        except Exception as e:
            logger.error(f"FCM send_to_token failed: {e}")
            return {"status": "error", "error_message": str(e)}
