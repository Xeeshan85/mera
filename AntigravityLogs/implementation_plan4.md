# CIRO Phase 3 — Audit & Implementation Plan

> Audit of friend's Phase 3 work against [phase3.md](file:///home/admin/CODES/seekho/phase3.md), excluding Kotlin Android app (deferred)

---

## Audit Summary

| Component | Status | Notes |
|:---|:---:|:---|
| `agents/orchestrator/agent.py` | ⚠️ Exists, needs fixes | Missing metrics, trace format wrong, no `triggered_by` |
| `agents/orchestrator/__init__.py` | ❌ Empty | Must export agent for `adk web` |
| `agents/stakeholder_notification/agent.py` | ✅ Mostly correct | Works after Phase 2 fixes |
| `services/pubsub_listener.py` | ⚠️ Partial | Only `ciro-signals-sub` — missing `ciro-incidents-sub` |
| `services/metrics_service.py` | ✅ Exists | Works, but never called by orchestrator |
| `services/fcm_service.py` | ❌ Missing | FCM logic is inline in notification agent — no dedicated service |
| `ciro/main.py` | ❌ Missing | No FastAPI + ADK entrypoint at all |
| `templates/` dir | ❌ Missing | Spec wants separate template files; current approach uses Gemini prompts (actually better) |
| `demo/run_demo_scenario.py` | ✅ Exists | Working from Phase 2 |
| `requirements.txt` | ⚠️ Incomplete | Missing `fastapi`, `uvicorn` |

---

## What Needs To Be Done (Backend Only, No Android)

### 🔴 Must-Have (System won't run without these)

#### 1. [NEW] `ciro/main.py` — FastAPI + ADK Server Entrypoint
The spec requires a `main.py` that:
- Initializes Firebase Admin SDK once
- Mounts the ADK agent via `get_fast_api_app()`
- Starts Pub/Sub listeners in a background thread on startup
- Runs with `python main.py` on port 8080

Currently this file does **not exist**. Without it, neither the ADK web UI nor the Pub/Sub auto-trigger works.

#### 2. [MODIFY] `services/pubsub_listener.py` — Add Incidents Listener
Current file only listens to `ciro-signals-sub`. The spec requires a second listener on `ciro-incidents-sub` that triggers state-based routing (the phase3 spec's core feature — reacting to state changes).

Need to add:
- `start_incidents_listener()` that subscribes to `ciro-incidents-sub`
- Routing logic: HYPOTHESIS → severity only, VERIFICATION_REQUESTED → severity + allocation, CONFIRMED → full pipeline, RETRACTED → retraction flow
- A unified `start_all_listeners()` function that `main.py` calls

#### 3. [MODIFY] `agents/orchestrator/__init__.py` — Export Agent
Currently empty. Must export `orchestrator_agent` for ADK web discovery.

#### 4. [MODIFY] `agents/orchestrator/agent.py` — Fix Trace Format + Add Metrics
Current issues:
- Trace is missing spec fields: `triggered_by`, `total_duration_ms`, `false_alarm_recovery`, per-stage `started_at`/`completed_at`/`stage`/`status`
- `MetricsService.record_pipeline_run()` is never called — no latency data in Firestore
- Hardcoded population estimates (15000/5000) bypass actual agent data

Fixes:
- Enrich trace format to match phase3 spec
- Call `MetricsService.record_pipeline_run()` at end of each pipeline run
- Add `total_duration_ms` tracking

---

### 🟡 Should-Have (Improve quality + demo readiness)

#### 5. [NEW] `services/fcm_service.py` — Dedicated FCM Service
Spec defines a proper `FCMService` class with `send_to_topic()` and `send_to_token()`. Currently FCM is inlined in the notification agent. Extract to a shared service with Android-specific config (`priority: high`, `channel_id: ciro_alerts`).

#### 6. [MODIFY] `requirements.txt` — Add Missing Dependencies
Add `fastapi` and `uvicorn` for the new `main.py`.

---

### ⚪ Deferred (Per User Request)
- Kotlin Android App — all files under `android/`
- Notification templates dir — current Gemini-based approach is actually superior to static templates

---

## Proposed Changes

### 1. `ciro/main.py` [NEW]

FastAPI + ADK server entrypoint. Handles:
- Firebase init (using existing `IncidentService._init_firebase()` pattern)
- ADK agent mounting at root
- Pub/Sub listener startup in background thread
- Health check endpoint

### 2. `ciro/services/pubsub_listener.py` [MODIFY]

Add:
- `start_incidents_listener()` — handles `ciro-incidents-sub`
- State-based routing: reads `new_state` from Pub/Sub message, calls appropriate orchestrator functions
- `start_all_listeners()` — starts both signal and incident listeners in threads

### 3. `ciro/agents/orchestrator/__init__.py` [MODIFY]

Export `orchestrator_agent` for ADK web.

### 4. `ciro/agents/orchestrator/agent.py` [MODIFY]

- Enrich trace with spec fields
- Add per-stage `started_at`/`completed_at` timestamps
- Call `MetricsService` at end of pipeline
- Add `triggered_by` parameter

### 5. `ciro/services/fcm_service.py` [NEW]

Clean FCM service class with Android-optimized config.

### 6. `ciro/requirements.txt` [MODIFY]

Add `fastapi>=0.115.0` and `uvicorn>=0.32.0`.

---

## Verification Plan

1. **Syntax check**: All files parse without errors
2. **Import test**: Full import chain works (`main.py` → orchestrator → all agents → all services)
3. **`python main.py`**: Starts without crash (Pub/Sub warnings expected without ADC)

## Manual Steps You'll Need To Do

> [!IMPORTANT]
> 1. **No manual steps needed for code changes** — I'll handle everything
> 2. **To test the full system**: Run `python main.py` from the `ciro/` directory
> 3. **To run the demo**: `python demo/run_demo_scenario.py` (same as before)
> 4. **Pub/Sub subscriptions**: Ensure `ciro-incidents-sub` subscription exists in GCP (it should from Phase 1 setup). If not, create it:
>    ```bash
>    gcloud pubsub subscriptions create ciro-incidents-sub --topic=ciro-incidents --project=ciro-hackathon-2026-2
>    ```
