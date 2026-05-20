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

# ── Seed agencies on boot ──────────────────────────────────────────
try:
    from services.agency_service import AgencyService
    AgencyService().seed_agencies()
    logger.info("✅ Agencies seeded to Firestore")
except Exception as e:
    logger.warning(f"Agency seeding skipped: {e}")


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
    title="barwaqt — Crisis Intelligence & Response Orchestrator",
    version="2.0.0",
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
    return {"service": "barwaqt (CIRO)", "status": "running", "version": "2.0.0"}


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


# ── News API ───────────────────────────────────────────────────────
@app.get("/api/news")
async def get_news():
    """Pakistan headlines from GNews API with Dawn RSS fallback."""
    import asyncio
    from services.news_service import NewsService
    loop = asyncio.get_event_loop()
    headlines = await loop.run_in_executor(None, NewsService().get_headlines)
    return {"status": "ok", "headlines": headlines, "count": len(headlines)}


# ── Signal Intelligence ────────────────────────────────────────────
@app.get("/api/intelligence")
async def get_intelligence():
    """Current signal intelligence snapshot (velocity, sentiment, keywords)."""
    import asyncio
    from services.intelligence_service import IntelligenceService
    loop = asyncio.get_event_loop()
    snapshot = await loop.run_in_executor(None, IntelligenceService().get_latest_snapshot)
    return {"status": "ok", "data": snapshot}


@app.post("/api/intelligence/refresh")
async def refresh_intelligence():
    """Force recompute intelligence snapshot from latest signals."""
    import asyncio
    from services.intelligence_service import IntelligenceService
    loop = asyncio.get_event_loop()
    snapshot = await loop.run_in_executor(None, IntelligenceService().compute_snapshot)
    return {"status": "ok", "data": snapshot}


# ── Agencies ───────────────────────────────────────────────────────
@app.get("/api/agencies")
async def get_agencies():
    """List all emergency agencies with their resources and stats."""
    import asyncio
    from services.agency_service import AgencyService
    loop = asyncio.get_event_loop()
    agencies = await loop.run_in_executor(None, AgencyService().get_all_agencies)
    return {"status": "ok", "agencies": agencies, "count": len(agencies)}


@app.post("/api/seed-agencies")
async def seed_agencies():
    """Re-seed agencies to Firestore (idempotent)."""
    import asyncio
    from services.agency_service import AgencyService
    loop = asyncio.get_event_loop()
    await loop.run_in_executor(None, AgencyService().seed_agencies)
    return {"status": "ok", "message": "Agencies seeded"}


# ── Scenario Trigger (Admin) ──────────────────────────────────────
@app.post("/api/trigger-scenario")
async def trigger_scenario(scenario: dict):
    """
    Trigger a demo scenario from the admin panel.
    Body: { "scenario": "flood_g10" | "heatwave_i8" | "false_alarm" | "multi_crisis" }
    Runs the full agent pipeline in real-time.
    """
    import asyncio
    import json
    from agents.orchestrator.agent import run_pipeline

    scenario_name = scenario.get("scenario", "flood_g10")
    logger.info(f"🎬 Admin triggered scenario: {scenario_name}")

    scenarios = {
        "flood_g10": {
            "signal_summary": json.dumps({
                "weather": {"precipitation_probability": 85, "qpf_mm": 45, "wind_speed": 32},
                "social": {"mention_velocity": 18, "urgency_score": 0.9,
                           "posts": ["cars stuck in flood G-10", "road blocked water everywhere G-10"]},
                "traffic": {"congestion_index": 0.75, "speed_reduction_pct": 60},
            }),
            "lat": 33.6844, "lng": 73.0479, "area_name": "G-10 Islamabad",
            "signal_ids": "demo-flood-001,demo-flood-002,demo-flood-003",
        },
        "heatwave_i8": {
            "signal_summary": json.dumps({
                "weather": {"temperature_c": 46, "heat_index": 52, "humidity": 15},
                "social": {"mention_velocity": 12, "urgency_score": 0.75,
                           "posts": ["extreme heat in I-8", "people fainting near bus stop I-8"]},
                "health": {"hospital_admissions_spike": True, "heat_stroke_reports": 7},
            }),
            "lat": 33.6938, "lng": 73.0551, "area_name": "I-8 Islamabad",
            "signal_ids": "demo-heat-001,demo-heat-002",
        },
        "false_alarm": {
            "signal_summary": json.dumps({
                "social": {"mention_velocity": 3, "urgency_score": 0.35,
                           "posts": ["might be flooding in F-6?", "just rain nothing serious"]},
                "weather": {"precipitation_probability": 25, "qpf_mm": 5},
            }),
            "lat": 33.7294, "lng": 73.0931, "area_name": "F-6 Islamabad",
            "signal_ids": "demo-false-001",
        },
    }

    if scenario_name == "multi_crisis":
        # Run two scenarios back-to-back
        results = []
        for name in ["flood_g10", "heatwave_i8"]:
            params = scenarios[name]
            loop = asyncio.get_event_loop()
            result = await loop.run_in_executor(
                None,
                lambda p=params: run_pipeline(
                    signal_summary=p["signal_summary"],
                    lat=p["lat"], lng=p["lng"],
                    area_name=p["area_name"],
                    signal_ids=p["signal_ids"],
                    triggered_by="admin_simulation",
                ),
            )
            results.append(result)
        return {"status": "ok", "scenario": "multi_crisis", "results": results}

    params = scenarios.get(scenario_name, scenarios["flood_g10"])
    loop = asyncio.get_event_loop()
    result = await loop.run_in_executor(
        None,
        lambda: run_pipeline(
            signal_summary=params["signal_summary"],
            lat=params["lat"], lng=params["lng"],
            area_name=params["area_name"],
            signal_ids=params["signal_ids"],
            triggered_by="admin_simulation",
        ),
    )
    return {"status": "ok", "scenario": scenario_name, "result": result}


# ── Entrypoint ─────────────────────────────────────────────────────
if __name__ == "__main__":
    import uvicorn

    port = int(os.getenv("PORT", "8080"))
    logger.info(f"Starting barwaqt on port {port}")
    uvicorn.run(app, host="0.0.0.0", port=port)
