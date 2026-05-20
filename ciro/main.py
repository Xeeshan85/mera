# main.py — FastAPI + ADK server entrypoint for CIRO
# Run with: python main.py

import logging
import os
import sys
import threading
from contextlib import asynccontextmanager

from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

# Load .env from the project root (one level above ciro/)
_project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
load_dotenv(os.path.join(_project_root, ".env"))

# Ensure ciro/ is on the path so agents/services resolve
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(name)s] %(levelname)s: %(message)s",
)
logger = logging.getLogger("ciro.main")

# ── Firebase initialisation (once, before anything else) ───────────
from services.incident_service import IncidentService

_svc = IncidentService()  # triggers _init_firebase() via class flag
logger.info("Firebase Admin SDK initialised")


# ── Lifespan: start Pub/Sub listeners on boot ─────────────────────
@asynccontextmanager
async def lifespan(app: FastAPI):
    from services.pubsub_listener import start_all_listeners

    listener_thread = threading.Thread(target=start_all_listeners, daemon=True)
    listener_thread.start()
    logger.info("✅ Pub/Sub listeners started in background thread")
    yield
    logger.info("Shutting down CIRO…")


# ── Build the FastAPI app ──────────────────────────────────────────
app = FastAPI(
    title="CIRO — Crisis Intelligence & Response Orchestrator",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ── Mount ADK agent web UI ─────────────────────────────────────────
try:
    from google.adk.cli.fast_api import get_fast_api_app

    adk_app = get_fast_api_app(
        agents_dir=os.path.join(os.path.dirname(__file__), "agents", "orchestrator"),
        session_service_uri=None,   # InMemory for hackathon
        allow_origins=["*"],
        web=True,
    )
    app.mount("/adk", adk_app)
    logger.info("ADK agent web UI mounted at /adk")
except Exception as e:
    logger.warning(f"ADK web UI mount failed (non-blocking): {e}")


# ── Health / status endpoints ──────────────────────────────────────
@app.get("/")
async def root():
    return {"service": "CIRO", "status": "running"}


@app.get("/health")
async def health():
    return {"status": "ok"}


@app.get("/api/status")
async def pipeline_status():
    from agents.orchestrator.agent import get_pipeline_status
    return get_pipeline_status()


@app.get("/api/metrics")
async def metrics_summary():
    from services.metrics_service import MetricsService
    return MetricsService().get_summary()


# ── Entrypoint ─────────────────────────────────────────────────────
if __name__ == "__main__":
    import uvicorn

    port = int(os.getenv("PORT", "8080"))
    logger.info(f"Starting CIRO on port {port}")
    uvicorn.run(app, host="0.0.0.0", port=port)
