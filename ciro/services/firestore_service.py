# services/firestore_service.py
# Uses Firebase Admin SDK with synchronous Firestore client wrapped in
# asyncio.get_event_loop().run_in_executor() — the async Firestore client
# has known issues with ADK's event loop.

import asyncio
import json
import logging
import os
from datetime import datetime, timedelta
from functools import partial
from pathlib import Path
from typing import Optional

import firebase_admin
from firebase_admin import credentials, firestore
from google.cloud.firestore_v1.base_query import FieldFilter
from dotenv import load_dotenv

from schemas.signal import Signal

load_dotenv()
logger = logging.getLogger(__name__)


class FirestoreService:
    """Firestore service for signal read/write operations."""

    _initialized = False

    def __init__(self):
        if not FirestoreService._initialized:
            self._init_firebase()
            FirestoreService._initialized = True
        self._db = firestore.client()
        self._fallback_path = Path("signals_fallback.jsonl")

    @staticmethod
    def _init_firebase():
        """Initialize Firebase Admin SDK with credential fallback chain."""
        project_id = os.getenv("GOOGLE_CLOUD_PROJECT", "ciro-hackathon-2026-2")
        cred_path_raw = os.getenv("FIREBASE_CREDENTIALS_PATH", "./firebase-admin-sdk.json")

        # Resolve relative paths against the project root (where .env lives)
        # Try: raw path → relative to ciro/ parent → relative to script dir
        candidate_paths = [
            cred_path_raw,
            os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", cred_path_raw),
            os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "firebase-admin-sdk.json"),
        ]
        cred_path = None
        for p in candidate_paths:
            resolved = os.path.abspath(p)
            if os.path.exists(resolved):
                cred_path = resolved
                break

        # Strategy 1: Service account JSON file
        if cred_path and os.path.exists(cred_path):
            try:
                cred = credentials.Certificate(cred_path)
                firebase_admin.initialize_app(cred, {"projectId": project_id})
                logger.info(f"Firebase initialized with service account: {cred_path}")
                return
            except Exception as e:
                logger.warning(f"Service account init failed: {e}")

        # Strategy 2: Application Default Credentials (gcloud auth)
        try:
            cred = credentials.ApplicationDefault()
            firebase_admin.initialize_app(cred, {"projectId": project_id})
            logger.info("Firebase initialized with Application Default Credentials")
            return
        except Exception as e:
            logger.warning(f"ADC init failed: {e}")

        # Strategy 3: No credentials (for emulator / Spark plan with GOOGLE_CLOUD_PROJECT)
        try:
            firebase_admin.initialize_app(options={"projectId": project_id})
            logger.warning(
                "Firebase initialized WITHOUT credentials. "
                "Firestore operations may fail if not using emulator."
            )
        except Exception as e:
            logger.error(f"All Firebase init strategies failed: {e}")
            raise

    def _run_sync(self, func, *args, **kwargs):
        """Run a synchronous Firestore call in an executor to avoid blocking the event loop."""
        loop = asyncio.get_event_loop()
        return loop.run_in_executor(None, partial(func, *args, **kwargs))

    # ── Write Signal ──────────────────────────────────────────────────

    async def write_signal(self, signal: Signal) -> str:
        """
        Write a Signal to Firestore signals/{signal_id}.
        Retries 3x with exponential backoff (1s, 2s, 4s).
        On all retries failed: log to signals_fallback.jsonl and raise.
        """
        data = signal.to_firestore_dict()
        doc_ref = self._db.collection("signals").document(signal.signal_id)

        last_exc = None
        for attempt in range(3):
            try:
                await self._run_sync(doc_ref.set, data)
                logger.info(f"Signal {signal.signal_id} written to Firestore")
                return signal.signal_id
            except Exception as e:
                last_exc = e
                wait_time = 2 ** attempt  # 1, 2, 4 seconds
                logger.warning(
                    f"Firestore write attempt {attempt + 1}/3 failed: {e}. "
                    f"Retrying in {wait_time}s..."
                )
                await asyncio.sleep(wait_time)

        # All retries failed — write to local fallback file
        logger.error(
            f"All 3 Firestore write attempts failed for signal {signal.signal_id}. "
            f"Writing to {self._fallback_path}"
        )
        with open(self._fallback_path, "a") as f:
            f.write(json.dumps(data, default=str) + "\n")

        raise last_exc  # type: ignore

    # ── Read Unprocessed Signals ──────────────────────────────────────

    async def get_unprocessed_signals(self, limit: int = 50) -> list[Signal]:
        """Query: processed == False, order by created_at DESC, limit."""
        def _query():
            return list(
                self._db.collection("signals")
                .where(filter=FieldFilter("processed", "==", False))
                .order_by("created_at", direction=firestore.Query.DESCENDING)
                .limit(limit)
                .stream()
            )

        docs = await self._run_sync(_query)
        signals = []
        for doc in docs:
            try:
                signals.append(Signal.from_firestore_dict(doc.to_dict()))
            except Exception as e:
                logger.warning(f"Failed to parse signal {doc.id}: {e}")
        return signals

    # ── Mark Processed ────────────────────────────────────────────────

    async def mark_signal_processed(self, signal_id: str, incident_id: str):
        """Update: processed=True, related_incident_id=incident_id."""
        doc_ref = self._db.collection("signals").document(signal_id)
        await self._run_sync(
            doc_ref.update,
            {"processed": True, "related_incident_id": incident_id}
        )
        logger.info(f"Signal {signal_id} marked as processed (incident: {incident_id})")

    # ── Traffic Cache ─────────────────────────────────────────────────

    async def get_cached_traffic(self, area_name: str) -> Optional[dict]:
        """
        Query traffic_cache/{area_name}, check if updated_at < 15 min ago.
        Returns raw_payload if fresh, None if stale or missing.
        """
        def _get():
            doc = self._db.collection("traffic_cache").document(area_name).get()
            return doc.to_dict() if doc.exists else None

        data = await self._run_sync(_get)
        if data is None:
            return None

        updated_at = data.get("updated_at")
        if isinstance(updated_at, str):
            updated_at = datetime.fromisoformat(updated_at)

        if updated_at and (datetime.utcnow() - updated_at) < timedelta(minutes=15):
            return data.get("raw_payload", data)

        return None  # Stale cache

    async def update_traffic_cache(self, area_name: str, data: dict):
        """Upsert traffic_cache/{area_name} with data + updated_at timestamp."""
        doc_ref = self._db.collection("traffic_cache").document(area_name)
        cache_data = {
            "raw_payload": data,
            "updated_at": datetime.utcnow().isoformat(),
            "area_name": area_name,
        }
        await self._run_sync(lambda: doc_ref.set(cache_data, merge=True))
        logger.info(f"Traffic cache updated for {area_name}")

    # ── Fusion Results ────────────────────────────────────────────────

    async def write_fusion_result(self, run_id: str, result_data: dict):
        """Write a fusion run result to fusion_results/{run_id}."""
        doc_ref = self._db.collection("fusion_results").document(run_id)
        await self._run_sync(doc_ref.set, result_data)
        logger.info(f"Fusion result {run_id} written to Firestore")
