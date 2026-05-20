# CIRO Phase 3 — Walkthrough

All Phase 3 backend items implemented, verified, and working.

---

## Changes Made

### 1. `ciro/main.py` [NEW]
FastAPI + ADK server entrypoint:
- Firebase Admin SDK init (reuses existing `IncidentService` pattern — no duplicate init)
- ADK agent web UI mounted at `/adk` (auto-discovers `orchestrator_agent`)
- Pub/Sub listeners start automatically in a background thread on boot
- Health check at `/health`, pipeline status at `/api/status`, metrics at `/api/metrics`
- CORS open for demo

### 2. `services/pubsub_listener.py` [REWRITTEN]
Now has **two** listeners:
- `ciro-signals-sub` → triggers `run_pipeline()` (existing)
- `ciro-incidents-sub` → triggers `handle_state_change()` (**new** — the phase3 core feature)
- `start_all_listeners()` — unified entry point called by `main.py`

### 3. `agents/orchestrator/agent.py` [REWRITTEN]
Major additions:
- **`handle_state_change()`** — Routes based on incident state per spec:
  - MONITORING → log only
  - HYPOTHESIS → severity prediction + shadow-commit resources
  - VERIFICATION_REQUESTED → severity + allocation + internal alerts
  - CONFIRMED → full response (all agents + all notifications + response action simulation)
  - RETRACTED → retraction flow (release resources + retraction notifications)
- **Enriched trace format** matching phase3 spec: `triggered_by`, `total_duration_ms`, per-stage `started_at`/`completed_at`/`stage`/`status`, `false_alarm_recovery`
- **Metrics recording** — calls `MetricsService.record_pipeline_run()` at the end of every pipeline run
- `root_agent` alias for ADK auto-discovery

### 4. `agents/orchestrator/__init__.py` [FIXED]
Now exports `orchestrator_agent` for ADK web discovery.

### 5. `services/fcm_service.py` [NEW]
Dedicated FCM service with:
- `send_to_topic()` — Android-optimized (high priority, `ciro_alerts` channel, sound)
- `send_to_token()` — for direct device targeting

### 6. `requirements.txt` [UPDATED]
Added: `fastapi>=0.115.0`, `uvicorn>=0.32.0`, `python-multipart>=0.0.20`

---

## Verification Results

| Test | Result |
|:---|:---:|
| Syntax check (all 7 files) | ✅ |
| Full import chain | ✅ |
| `python main.py` startup | ✅ |
| ADK agent discovery | ✅ `ADK agent web UI mounted at /adk` |
| Signals Pub/Sub listener | ✅ Connected |
| Incidents Pub/Sub listener | ✅ Connected + already receiving real events |
| `handle_state_change` routing | ✅ Processes RETRACTED event live |

---

## How To Run

```bash
# From ciro/ directory:
python main.py

# Server starts at http://0.0.0.0:8080
# ADK web UI at http://localhost:8080/adk
# Health check: http://localhost:8080/health
# Pipeline status: http://localhost:8080/api/status
# Metrics: http://localhost:8080/api/metrics
```

## Manual Step (if not already done)

Ensure the `ciro-incidents-sub` Pub/Sub subscription exists:
```bash
gcloud pubsub subscriptions create ciro-incidents-sub \
  --topic=ciro-incidents \
  --project=ciro-hackathon-2026-2
```
(Based on the live test output, this subscription already exists and is receiving events ✅)
