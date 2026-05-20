# CIRO Phase 2 — Comprehensive Audit Report

> Audit of your friend's Phase 2 implementation against [phase2.md](file:///home/admin/CODES/seekho/phase2.md)

---

## Audit Summary

| Category | Count | Severity |
|:---|:---:|:---:|
| 🔴 Critical Bugs (will crash/break) | 5 | Blocker |
| 🟠 Schema Mismatches (spec vs code) | 5 | High |
| 🟡 Missing Features (specified but unimplemented) | 6 | High |
| 🔵 Architectural / Design Issues | 4 | Medium |
| ⚪ Recommendations | 2 | Low |
| **Total** | **22** | |

---

## 🔴 CRITICAL BUGS

### 1. Project ID Mismatch — Split-Brain Firestore

> [!CAUTION]
> Phase 1 services (`firestore_service.py`, `pubsub_service.py`) hardcode `ciro-hackathon-2025` as the fallback project ID, but Phase 2 services (`incident_service.py`, `pubsub_listener.py`) hardcode `ciro-hackathon-2026-2`. The `.env` file has `ciro-hackathon-2026-2`.

If the `.env` file ever fails to load, Phase 1 and Phase 2 code would talk to **different GCP projects**.

| File | Hardcoded Fallback |
|:---|:---|
| [firestore_service.py](file:///home/admin/CODES/seekho/ciro/services/firestore_service.py#L41) | `ciro-hackathon-2025` ❌ |
| [pubsub_service.py](file:///home/admin/CODES/seekho/ciro/services/pubsub_service.py#L23) | `ciro-hackathon-2025` ❌ |
| [incident_service.py](file:///home/admin/CODES/seekho/ciro/services/incident_service.py#L44) | `ciro-hackathon-2026-2` ✅ |
| [pubsub_listener.py](file:///home/admin/CODES/seekho/ciro/services/pubsub_listener.py#L13) | `ciro-hackathon-2026-2` ✅ |

**Fix:** Unify all fallback defaults to `ciro-hackathon-2026-2`.

---

### 2. Duplicate Firebase Init — Race Condition

[incident_service.py](file:///home/admin/CODES/seekho/ciro/services/incident_service.py#L34-L61) has its **own** `_init_firebase()` method completely separate from [firestore_service.py](file:///home/admin/CODES/seekho/ciro/services/firestore_service.py#L38-L86). Both classes use `_initialized` class-level flags but they're **independent booleans**.

The `incident_service.py` does check `firebase_admin._apps` first (L42), which prevents a double-init crash, but the duplicated init logic is fragile and prone to drift. If `incident_service` is imported before `firestore_service`, it could initialize Firebase with a different project ID fallback.

**Fix:** Extract Firebase init to a single shared utility, or have `IncidentService` inherit from / delegate to `FirestoreService`.

---

### 3. Deprecated SDK (`google.generativeai`) Used in Notification Agent

[stakeholder_notification/agent.py L51-53](file:///home/admin/CODES/seekho/ciro/agents/stakeholder_notification/agent.py#L51-L53) uses:
```python
import google.generativeai as genai
genai.configure(api_key=os.getenv("GOOGLE_API_KEY"))
model = genai.GenerativeModel("gemini-2.5-flash")
```

But the crisis detection agent (L44-45) correctly uses:
```python
from google import genai
client = genai.Client(api_key=os.getenv("GOOGLE_API_KEY"))
```

`google.generativeai` is **deprecated** — this will cause import errors if only `google-genai` is installed (not `google-generativeai`), and it's not listed in `requirements.txt`.

**Fix:** Migrate to `google.genai.Client` pattern everywhere.

---

### 4. Duplicate FCM Block — Notifications Sent Twice

[stakeholder_notification/agent.py L129-203](file:///home/admin/CODES/seekho/ciro/agents/stakeholder_notification/agent.py#L129-L203) has the **exact same FCM push block duplicated** — lines 129–165 and lines 167–203 are identical copy-paste. Every notification will be sent **twice**.

**Fix:** Delete the second block (lines 167–203).

---

### 5. Resource `current_location` Property — Fragile Adapter

[resource.py L26-29](file:///home/admin/CODES/seekho/ciro/schemas/resource.py#L26-L29) uses a `SimpleNamespace` property hack:
```python
@property
def current_location(self):
    from types import SimpleNamespace
    return SimpleNamespace(lat=self.location.lat, lng=self.location.lng, name=self.name)
```

The spec has `current_location: ResourceLocation` as a proper field with `name`. But the actual Firestore data uses `location` (from `seed_resources.py`), and the code bridges with this property. The problem: `SimpleNamespace` is not serializable and the `name` field comes from the resource's top-level `name`, not from the location.

This works for now but is a hidden fragility — if anyone calls `r.model_dump()`, `current_location` won't appear since it's a property not a field.

---

## 🟠 SCHEMA MISMATCHES (Spec vs Implementation)

### 6. `ConflictingHypothesis` — Missing `evidence_summary` Field

Spec defines:
```python
class ConflictingHypothesis(BaseModel):
    crisis_type: str
    confidence: float
    evidence_signal_ids: list[str]
    evidence_summary: str  # ← MISSING
```

Implementation ([incident.py L16-19](file:///home/admin/CODES/seekho/ciro/schemas/incident.py#L16-L19)) has:
```python
class ConflictingHypothesis(BaseModel):
    type: str           # ← renamed from crisis_type
    confidence: float
    evidence_signal_ids: list[str] = []
                        # ← evidence_summary missing
```

Two issues: field renamed `crisis_type` → `type`, and `evidence_summary` is missing entirely.

---

### 7. `SeverityForecast` — Missing `peak_impact_time`

Spec defines `peak_impact_time: Optional[datetime] = None` inside `SeverityForecast`. Implementation ([incident.py L22-26](file:///home/admin/CODES/seekho/ciro/schemas/incident.py#L22-L26)) moves `peak_impact_time` to the Incident model as a top-level field. This works but diverges from the team contract.

---

### 8. `ResourceLocation` — Missing `name` Field

Spec defines:
```python
class ResourceLocation(BaseModel):
    lat: float
    lng: float
    name: str  # ← MISSING
```

Implementation ([resource.py L8-10](file:///home/admin/CODES/seekho/ciro/schemas/resource.py#L8-L10)) omits `name`. The `name` is stored at the Resource level instead. The `current_location` property bridges this, but the Firestore data in `seed_resources.py` only stores `lat/lng` in the `location` sub-document.

---

### 9. `AuditLogEntry` — Renamed from Spec

Spec: `AuditEntry`. Implementation: `AuditLogEntry`. Minor naming inconsistency but could break if Person 3's code imports `AuditEntry`.

---

### 10. `Incident` — Missing `ResponseAction` Class and `trade_off_narrative`

The spec defines a `ResponseAction` Pydantic model with rich structured fields. The implementation uses `response_actions: list[dict]` — untyped dicts. This loses type safety and validation.

The `trade_off_narrative` field exists in the schema but is **never written** by the resource allocation agent. It only appears in the orchestrator's trace, not in the Firestore incident document.

---

## 🟡 MISSING FEATURES (Specified but Unimplemented)

### 11. No Pub/Sub Publishing on State Change ⚠️

> [!IMPORTANT]
> The spec explicitly requires: *"Publish state change event to ciro-incidents Pub/Sub topic so Person 3's orchestrator can trigger notifications."*

The implementation's `transition_state()` in [incident_service.py](file:///home/admin/CODES/seekho/ciro/services/incident_service.py#L102-L159) performs the Firestore update but **never publishes to Pub/Sub**. There is no `ciro-incidents` topic publisher anywhere in the codebase.

**Impact:** Person 3's orchestrator won't receive real-time event notifications.

---

### 12. No Duplicate Incident Detection

The spec requires: *"Before creating a new incident, query Firestore incidents where state not in RESOLVED/RETRACTED, location within 3km, crisis_type matches, created_at within last 2 hours."*

The [crisis_detection/agent.py](file:///home/admin/CODES/seekho/ciro/agents/crisis_detection/agent.py#L93-L119) creates a new incident every single time. No deduplication logic exists. This will create duplicate incidents for the same ongoing crisis.

---

### 13. Signals Not Marked as Processed

The spec requires: *"Mark all processed signal_ids as processed=True in Firestore."*

The crisis detection agent never calls `mark_signal_processed()`. Signals will be re-processed on every polling cycle, creating infinite duplicate incidents.

---

### 14. No `tools/` Subdirectories for Severity Prediction and Resource Allocation

The spec calls for separate tool files:
```
severity_prediction/tools/historical_tool.py
severity_prediction/tools/weather_forecast_tool.py
severity_prediction/tools/vulnerable_pop_tool.py
severity_prediction/tools/congestion_spread_tool.py
resource_allocation/tools/travel_time_tool.py
resource_allocation/tools/optimizer.py
```

Instead, everything is inlined into the respective `agent.py` files. This is a **minor structural deviation** — the code works, but violates the project structure spec. This makes the codebase harder to maintain and test independently.

---

### 15. No Agent Trace Writing from Individual Agents

The spec requires writing to `agent_traces/{trace_id}` after **every** detection/prediction/allocation run. Only the orchestrator's `_write_trace()` writes traces. The individual agents (`crisis_detection_agent`, `severity_prediction_agent`, `resource_allocation_agent`) never write their own traces when called directly (e.g., from the demo script).

---

### 16. Resource Allocation — Incomplete Spec Implementation

Several spec features are missing from [resource_allocation/agent.py](file:///home/admin/CODES/seekho/ciro/agents/resource_allocation/agent.py):

| Spec Feature | Implemented? |
|:---|:---:|
| Priority score formula (`severity × log10(population) × spread_multiplier / ETA`) | ❌ Uses simplified `severity × 2 + crisis_weight` |
| Crisis-type-specific resource requirements (e.g., flood: 2 rescue, 2 police, 1 tanker, 2 ambulances) | ❌ Uses generic `max(1, severity)` units |
| SHADOW_COMMITTED re-allocation (1.5× priority threshold) | ❌ Not implemented |
| `trade_off_narrative` written to Firestore incident document | ❌ Not written |
| Audit log entry appended after allocation | ❌ Not implemented |

---

## 🔵 ARCHITECTURAL / DESIGN ISSUES

### 17. Classification: Agent vs Direct API Call

> [!NOTE]
> **Your question: "Is using an agent the best method for classification?"**

The current approach is **hybrid** — the ADK `Agent` wraps a tool function that internally makes its own `genai.Client().generate_content()` call. This means:
1. The outer ADK agent calls Gemini once to decide to call `classify_crisis()`
2. Inside `classify_crisis()`, Gemini is called **again** to do the actual classification
3. **Total: 2 LLM calls for 1 classification** — doubling latency and cost

**Recommendation:** For classification, you have two better options:

**Option A: Pure Function Tool (recommended for speed)**
Remove the inner Gemini call from `classify_crisis()`. Instead, put the classification logic in the **agent's system instruction** and have the agent return structured JSON directly. The ADK agent already calls Gemini — let it do the classification in a single call.

**Option B: Direct API Call (no agent)**
If classification doesn't need agentic reasoning (it doesn't — it's a single-shot analysis), skip the ADK Agent entirely for this step. Just call `genai.Client().generate_content()` directly from the orchestrator. This is simpler and faster.

**Given this is a hackathon demo**, Option A (single agent call) is the pragmatic choice — it keeps the "agentic" narrative while being efficient.

---

### 18. All Tools Are Synchronous — Blocking the Event Loop

All tool functions across all 3 agents use synchronous `def` (not `async def`). Per ADK 2.0 best practices, **tools should be `async def`** for:
- Parallel execution of multiple tool calls
- Non-blocking I/O (all tools make HTTP API calls)

The `httpx.Client` (sync) should be `httpx.AsyncClient`. The `incident_service` calls should be async.

**Impact:** When the severity prediction agent calls 3 tools (weather + facilities + congestion), they execute **sequentially** instead of in parallel, tripling latency.

---

### 19. Orchestrator Bypasses Agents — Directly Calls Tool Functions

The [orchestrator/agent.py](file:///home/admin/CODES/seekho/ciro/agents/orchestrator/agent.py) imports and directly calls the **tool functions** from each agent:
```python
from agents.crisis_detection.agent import classify_crisis, update_incident_state
from agents.severity_prediction.agent import get_weather_forecast, ...
```

This means the ADK agents (`crisis_detection_agent`, `severity_prediction_agent`, etc.) are **never actually used as agents**. The orchestrator's `run_pipeline()` function is just a regular Python function that calls other functions.

The ADK agent definitions at the bottom of each file are essentially dead code — they're defined but never invoked by the orchestrator.

**Impact:** You lose agent observability, tool-call tracing, and the ability to use ADK's built-in session/state management.

---

### 20. Hardcoded Population Estimates in Orchestrator

[orchestrator/agent.py L124](file:///home/admin/CODES/seekho/ciro/agents/orchestrator/agent.py#L124):
```python
affected_population_estimate=15000 if severity >= 3 else 5000,
```

This bypasses the severity prediction agent's actual population analysis. The agent's tools (Places API, historical data) should drive this number, not hardcoded values.

---

## ⚪ RECOMMENDATIONS

### 21. Model Selection for Classification

> [!TIP]
> **Your question: "If some other model is required, tell me how to set it up."**

`gemini-2.5-flash` is a good choice for this use case — it's fast, cheap, and handles structured output well. You don't need a different model. However:

- For the **classification prompt**, consider using `response_mime_type="application/json"` with a `response_schema` in `GenerateContentConfig` to enforce structured output natively instead of parsing raw text with regex/JSON.
- If you want higher accuracy for critical classifications, you could upgrade to `gemini-2.5-pro` for the crisis detection agent only (it's the decision-making bottleneck). All other agents can stay on flash.

To set up `gemini-2.5-pro`, just change the model string:
```python
crisis_detection_agent = Agent(
    name="crisis_detection_agent",
    model="gemini-2.5-pro",  # ← change here
    ...
)
```

No additional setup is needed — the same API key works for all Gemini models.

---

### 22. `seed_resources.py` — Missing `unit_name` Field in Some Resources

The seed script creates resources with `unit_name` but the Firestore data doesn't include the `capacity` field for non-shelter resources, and it omits `contact`. This is fine for demo purposes but means `Resource.from_firestore_dict()` must handle missing fields gracefully (which it does via defaults).

---

## Handoff Checklist Status (from spec)

| Checklist Item | Status |
|:---|:---:|
| `incidents` collection has test documents with all schema fields | ⚠️ Schema deviates from spec |
| State machine transitions work (MONITORING → HYPOTHESIS → CONFIRMED → RETRACTED) | ✅ Works via `incident_service.transition_state()` |
| Resource states update correctly after allocation | ✅ Works |
| `agent_traces` collection has traces after each agent run | ⚠️ Only orchestrator writes traces, not individual agents |
| Pub/Sub `ciro-incidents` topic receives events on state change | ❌ **Not implemented** |
| Person 3 subscribes to `ciro-incidents-sub` | ❌ No topic to subscribe to |
| Share Incident and Resource Pydantic models | ✅ Files exist but deviate from spec |
| Two-crisis scenario with trade-off narrative in Firestore | ⚠️ Narrative computed but not written to incident doc |

---

## Proposed Fix Priority

1. **P0 (Do first):** Fix project ID mismatch (#1), remove duplicate FCM block (#4), fix deprecated SDK import (#3)
2. **P1 (Critical features):** Implement duplicate incident detection (#12), mark signals as processed (#13), add Pub/Sub publishing on state change (#11)
3. **P2 (Schema alignment):** Fix `ConflictingHypothesis` fields (#6), add `evidence_summary`, align `ResourceLocation` (#8)
4. **P3 (Quality):** Write `trade_off_narrative` to Firestore (#10/#16), add per-agent trace writing (#15), implement proper resource requirements per crisis type (#16)
5. **P4 (Architecture):** Consider making tools async (#18), restructure orchestrator to use agents properly or embrace the direct-call pattern explicitly (#19)

---

## Open Questions

> [!IMPORTANT]
> 1. **Do you want me to fix all of these issues?** I can start with P0/P1 items and work through the list.
> 2. **Agent architecture:** The orchestrator currently bypasses the ADK agents entirely (issue #19). Do you want to:
>    - **(A)** Keep the direct-call pattern (simpler, faster for hackathon)
>    - **(B)** Rewire to actually use ADK agents as sub-agents (more "agentic", better for demo narrative)
> 3. **Classification double-call (#17):** Should I refactor to eliminate the redundant LLM call inside `classify_crisis()`?
> 4. **Pub/Sub for incidents (#11):** Is the `ciro-incidents` topic already created in GCP? If not, I'll create it.
