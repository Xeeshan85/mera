# CIRO Phase 1: Data Ingestion Layer + Signal Fusion Agent

Build the complete data ingestion pipeline and Signal Fusion Agent (Agent 1) for the CIRO multi-agent crisis intelligence system.

## Environment Status

| Item | Status |
|------|--------|
| Python 3.11.14 in `.venv` | ✅ Ready |
| `google-adk==2.0.0` | ✅ Installed |
| `firebase-admin`, `google-cloud-firestore`, `google-cloud-pubsub` | ✅ Installed |
| `httpx`, `pydantic>=2`, `apify-client`, `python-dotenv` | ✅ Installed |
| `feedparser`, `geopy` | ❌ **Must install** |
| `firebase-admin-sdk.json` | ❌ **Missing** — will use Application Default Credentials fallback |
| `.env` keys | ✅ All present (GOOGLE_API_KEY, GOOGLE_MAPS_API_KEY, OWM_API_KEY, APIFY_API_TOKEN, etc.) |

> [!IMPORTANT]
> The `.env` file has `APIFY_API_TOKEN` defined **twice** (lines 1 and 6). This is harmless (last wins) but will be cleaned up.

> [!WARNING]
> `firebase-admin-sdk.json` does **not** exist on disk. The code will gracefully fall back to `firebase_admin.credentials.ApplicationDefault()` or environment-based auth. If you have the service account JSON, place it at `./firebase-admin-sdk.json` before running.

## Proposed Changes

### Dependencies

#### [NEW] [requirements.txt](file:///home/admin/CODES/seekho/ciro/requirements.txt)
Root-level `ciro/requirements.txt` with pinned versions for all Phase 1 deps. Also install `feedparser` and `geopy` into the venv.

---

### Schema (Team Contract)

#### [NEW] [schemas/\_\_init\_\_.py](file:///home/admin/CODES/seekho/ciro/schemas/__init__.py)
#### [NEW] [schemas/signal.py](file:///home/admin/CODES/seekho/ciro/schemas/signal.py)
Pydantic v2 `Signal` and `SignalLocation` models — exact contract from the spec. Uses `model_dump()` for serialization with datetime handling.

---

### Services

#### [NEW] [services/\_\_init\_\_.py](file:///home/admin/CODES/seekho/ciro/services/__init__.py)
#### [NEW] [services/firestore_service.py](file:///home/admin/CODES/seekho/ciro/services/firestore_service.py)
- `FirestoreService` class using Firebase Admin SDK (sync client wrapped in `run_in_executor` for async)
- `write_signal()` with 3x retry + exponential backoff + JSONL fallback
- `get_unprocessed_signals()`, `mark_signal_processed()`
- `get_cached_traffic()` / `update_traffic_cache()` with 15-min TTL
- Graceful init: tries service account JSON → falls back to ADC

#### [NEW] [services/pubsub_service.py](file:///home/admin/CODES/seekho/ciro/services/pubsub_service.py)
- Real Google Cloud Pub/Sub using `google-cloud-pubsub`
- `publish_signal()` — fire-and-forget with error logging
- `subscribe_signals()` — for Person 2's consumption

---

### Tools

#### [NEW] [agents/signal_fusion/tools/geocoding_tool.py](file:///home/admin/CODES/seekho/ciro/agents/signal_fusion/tools/geocoding_tool.py)
- Google Geocoding API with Islamabad bias (`bounds`, `region=pk`)
- Confidence mapping: ROOFTOP→0.95, RANGE_INTERPOLATED→0.80, GEOMETRIC_CENTER→0.70, APPROXIMATE→0.50
- Fallback: Islamabad center (33.6844, 73.0479) with confidence 0.20

#### [NEW] [agents/signal_fusion/tools/weather_tool.py](file:///home/admin/CODES/seekho/ciro/agents/signal_fusion/tools/weather_tool.py)
Three-tier fallback chain:
1. **Google Weather API** (`weather.googleapis.com/v1/currentConditions:lookup` + `forecast/hours:lookup`) → credibility 0.95
2. **OpenWeatherMap** → credibility 0.80, `degraded_mode=True`
3. **Open-Meteo** (no key) → credibility 0.70, `degraded_mode=True`
4. All fail → credibility 0.0, error payload

#### [NEW] [agents/signal_fusion/tools/traffic_tool.py](file:///home/admin/CODES/seekho/ciro/agents/signal_fusion/tools/traffic_tool.py)
- Google Maps Routes API `computeRouteMatrix` with 8 grid points
- Congestion index calculation (free-flow baseline at 50 km/h)
- Firestore traffic cache with 15-min TTL
- Fallback: cached data → zero-credibility signal

#### [NEW] [agents/signal_fusion/tools/social_tool.py](file:///home/admin/CODES/seekho/ciro/agents/signal_fusion/tools/social_tool.py)
- Apify Twitter scraper with urgency keyword scoring (4-tier: critical/high/medium/low)
- Per-tweet credibility based on followers, geo, retweets, verification
- Mention velocity (30-min window count)
- Fallback to `mock_social_stream.generate_signals()`

#### [NEW] [agents/signal_fusion/tools/gdacs_tool.py](file:///home/admin/CODES/seekho/ciro/agents/signal_fusion/tools/gdacs_tool.py)
- GDACS JSON API → filter by geospatial radius using `geopy.distance.geodesic`
- RSS feed fallback via `feedparser`
- Event type mapping (FL→flood, TC/EQ→infrastructure_failure, DR→heatwave)
- Credibility always 0.95

#### [NEW] [agents/signal_fusion/tools/credibility_scorer.py](file:///home/admin/CODES/seekho/ciro/agents/signal_fusion/tools/credibility_scorer.py)
Post-collection scoring pipeline:
1. Geolocation confidence boost (+0.10 if >0.85)
2. Mention velocity boost (social only)
3. Staleness penalty (−0.15 if >30 min old)
4. Contradiction detection (weather vs. social flood claims)
5. Corroboration boost (3+ source types agreeing)

---

### Agent

#### [NEW] [agents/signal_fusion/\_\_init\_\_.py](file:///home/admin/CODES/seekho/ciro/agents/signal_fusion/__init__.py)
ADK-compatible `__init__.py` that exports `agent` for `adk web` command.

#### [NEW] [agents/signal_fusion/agent.py](file:///home/admin/CODES/seekho/ciro/agents/signal_fusion/agent.py)
- ADK 2.0 `Agent` with `gemini-2.5-flash` model
- All 4 data collection tools registered
- Low temperature (0.1) for analytical tasks
- Detailed instruction for the LLM orchestration

---

### Ingestion

#### [NEW] [ingestion/\_\_init\_\_.py](file:///home/admin/CODES/seekho/ciro/ingestion/__init__.py)
#### [NEW] [ingestion/signal_collector.py](file:///home/admin/CODES/seekho/ciro/ingestion/signal_collector.py)
- Continuous polling loop (2-min interval)
- 4 monitored zones in Islamabad (G-10, G-11, I-10, F-10)
- Uses ADK `Runner` + `InMemorySessionService` + `run_async` event loop pattern

#### [NEW] [ingestion/mock_social_stream.py](file:///home/admin/CODES/seekho/ciro/ingestion/mock_social_stream.py)
- `flood_g10` and `heat_i10` mock scenarios
- `generate_signals()` for fallback
- `inject_demo_scenario()` for live demos with timed injection

#### [NEW] [ingestion/seed_resources.py](file:///home/admin/CODES/seekho/ciro/ingestion/seed_resources.py)
- 28 realistic Islamabad emergency resources
- Seeds `resources/{resource_id}` with `state=AVAILABLE`

---

## Key Design Decisions

1. **ADK 2.0 `run_async` yields events** — the signal collector iterates events and looks for `is_final_response()` rather than getting a direct return value.
2. **Sync Firestore client wrapped in executor** — avoids known ADK event loop conflicts with the async Firestore client.
3. **Firebase init fallback chain** — service account JSON → Application Default Credentials → environment variable auth.
4. **All tools are plain async functions** — ADK auto-wraps them as `FunctionTool` when passed to the `tools` list.

## Verification Plan

### Automated Tests
```bash
# 1. Install missing deps
pip install feedparser geopy

# 2. Test weather tool directly
python -c "
import asyncio
from agents.signal_fusion.tools.weather_tool import get_weather_signal
signal = asyncio.run(get_weather_signal(33.6844, 73.0479, 'G-10 Islamabad'))
print(signal.model_dump_json(indent=2))
"

# 3. Test social tool
python -c "
import asyncio
from agents.signal_fusion.tools.social_tool import get_social_signals
signals = asyncio.run(get_social_signals('flood G-10', 'G-10 Islamabad', max_results=5))
for s in signals: print(s.source_name, s.credibility_score, s.urgency_language_score)
"

# 4. Seed resources
python -m ingestion.seed_resources

# 5. Run full agent via ADK web
adk web agents/signal_fusion
```

### Manual Verification
- Check Firebase Console → Firestore for `signals` and `resources` collections
- Verify each signal has all required schema fields
- Confirm fallback chains work by temporarily invalidating API keys
