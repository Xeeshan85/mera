# CIRO Phase 1 — Walkthrough

## What Was Built

Complete data ingestion layer and Signal Fusion Agent for the CIRO crisis intelligence system — **17 Python files** across 5 packages inside `ciro/`.

### File Structure Created
```
ciro/
├── requirements.txt
├── schemas/
│   ├── __init__.py
│   └── signal.py                 # Pydantic Signal model (team contract)
├── services/
│   ├── __init__.py
│   ├── firestore_service.py      # Firestore CRUD with retry + fallback
│   └── pubsub_service.py         # Pub/Sub publish + subscribe
├── agents/
│   ├── __init__.py
│   └── signal_fusion/
│       ├── __init__.py            # ADK web-compatible export
│       ├── agent.py               # ADK 2.0 Agent with gemini-2.5-flash
│       └── tools/
│           ├── __init__.py
│           ├── weather_tool.py    # 3-tier: Google Weather → OWM → Open-Meteo
│           ├── traffic_tool.py    # Routes API + Firestore cache
│           ├── social_tool.py     # Apify Twitter + mock fallback
│           ├── gdacs_tool.py      # GDACS JSON + RSS fallback
│           ├── geocoding_tool.py  # Google Geocoding with Islamabad bias
│           └── credibility_scorer.py  # 5-stage scoring pipeline
└── ingestion/
    ├── __init__.py
    ├── signal_collector.py        # Continuous polling loop (4 zones)
    ├── mock_social_stream.py      # Demo scenarios (flood, heatwave)
    └── seed_resources.py          # 29 emergency resources seeded
```

## Testing Results

| Tool | Source Used | Real Data | Fallback Working |
|------|-----------|-----------|-----------------|
| Weather | OWM (Google Weather API not enabled) | ✅ 28.79°C | ✅ Open-Meteo tested too |
| Traffic | Google Maps Routes API | ✅ Congestion index 0.266 | ✅ Firestore cache |
| Social | Apify Twitter scraper | ✅ 15 real tweets | ✅ Mock fallback |
| GDACS | JSON API | ✅ 6 alerts (flood India, EQ Afghanistan) | ✅ RSS fallback |
| Credibility Scorer | — | ✅ 3 contradictions flagged | — |
| Firestore | Service account auth | ✅ All writes successful | ✅ JSONL fallback |
| Resources | Seed script | ✅ 29/29 written | — |

## Fixes Applied During Implementation

1. **Firebase credential path** — resolved relative to project root, not CWD
2. **Firebase init chain** — service account → ADC → credential-less (3 strategies)
3. **Apify `lang` param** — changed from invalid `"en,ur"` to `"en"`
4. **Routes API `departureTime`** — shifted to `now + 1 min` (API rejects past timestamps)
5. **Firestore `.where()` syntax** — migrated from deprecated positional args to `FieldFilter`
6. **Firestore `set(merge=True)`** — fixed merge keyword argument syntax
7. **`.env` cleanup** — removed duplicate `APIFY_API_TOKEN`, updated `GOOGLE_CLOUD_PROJECT` to match service account
8. **Composite index** — added `signals (processed ASC, created_at DESC)` to `firestore.indexes.json` and deployed

## Handoff Info for Person 2

- **Pub/Sub subscription**: `ciro-signals-sub`
- **Unprocessed signals query**: `processed == False, order by created_at DESC`
- **`.env` vars set**: `GOOGLE_API_KEY`, `GOOGLE_MAPS_API_KEY`, `GOOGLE_CLOUD_PROJECT`, `APIFY_API_TOKEN`, `OWM_API_KEY`, `FIREBASE_CREDENTIALS_PATH`
- **Signal schema**: See `ciro/schemas/signal.py` — this is the immutable team contract
