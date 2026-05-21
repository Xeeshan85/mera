# CIRO — Crisis Intelligence & Response Orchestrator
## Antigravity Master Prompt + Team Setup Guide

---

## TEAM DIVISION OF WORK

### Person 1 — You (Data Ingestion Layer + Signal Fusion Agent)
- Signal collector service (Weather, Traffic, Apify/Twitter, GDACS, mock sensors)
- Unified signal schema + Pub/Sub bus
- Credibility scoring engine
- Signal Fusion ADK Agent (Agent 1)
- Firestore signal document writes
- Fallback/degraded mode logic per source
- Mock social stream generator (for demo)

### Person 2 — Crisis Detection + Prediction + Resource Allocation
- Crisis Detection & Classification ADK Agent (Agent 2)
- Severity & Evolution Prediction ADK Agent (Agent 3)
- Resource Allocation & Optimization ADK Agent (Agent 4)
- Firestore incident state machine
- False-positive/false-negative recovery loop
- Historical baseline seeding

### Person 3 — Orchestration + Notification + Kotlin Mobile App
- Orchestrator ADK Agent (Agent 0)
- Stakeholder Notification & Action Simulation ADK Agent (Agent 5)
- Admin web dashboard (React + Vite, optional but strongly recommended for judges)
- Kotlin Android app (mandatory): Firestore live listeners, Google Maps SDK, FCM push notifications
- Antigravity trace display setup
- Demo video recording

---

## HOW TO COMBINE MODULES

The integration point between all three modules is **Firestore + Pub/Sub**. Here is the contract:

**Person 1 writes → Firestore `signals` collection + publishes to Pub/Sub topic `ciro-signals`**  
**Person 2 reads from Pub/Sub → writes to Firestore `incidents` collection**  
**Person 3 reads `incidents` → Orchestrator triggers agents → writes notifications to Firestore `notifications` collection → Kotlin app reads `incidents` and `notifications` in real time via snapshot listeners**

No direct function calls between modules. Everyone works against shared Firestore schemas defined below. Agree on those schemas on day 1 and do not change them without notifying the team.

---

## API KEY SETUP (DO THIS FIRST — ALL THREE PEOPLE NEED THESE)

### Step 1: Google Cloud Project

```bash
# Install gcloud CLI if not installed
# https://cloud.google.com/sdk/docs/install

gcloud auth login
gcloud projects create ciro-hackathon-2025 --name="CIRO"
gcloud config set project ciro-hackathon-2025

# Enable all required APIs in one command
gcloud services enable \
  aiplatform.googleapis.com \
  pubsub.googleapis.com \
  firestore.googleapis.com \
  maps-backend.googleapis.com \
  weather.googleapis.com \
  places-backend.googleapis.com \
  geocoding-backend.googleapis.com \
  routes.googleapis.com \
  run.googleapis.com \
  cloudbuild.googleapis.com \
  artifactregistry.googleapis.com \
  firebase.googleapis.com \
  fcm.googleapis.com
```

### Step 2: Google Maps + Weather API Key

1. Go to [console.cloud.google.com/google/maps-apis/credentials](https://console.cloud.google.com/google/maps-apis/credentials)
2. Click **Create Credentials → API Key**
3. Restrict the key to: Weather API, Routes API, Places API (New), Geocoding API
4. Copy key → save as `GOOGLE_MAPS_API_KEY` in your `.env`

**Alternative for hackathon prototyping (no billing required):**
- Visit [developers.google.com/maps/documentation/weather/demo-key](https://developers.google.com/maps/documentation/weather/demo-key)
- Click **Get a Demo Key** — no credit card needed, works immediately for Weather API + Maps JS API
- Note: Demo key has daily usage limits; switch to full key before demo

### Step 3: Gemini API Key (for ADK agents)

```bash
# Option A: Via Vertex AI (recommended for production)
gcloud iam service-accounts create ciro-agent-sa \
  --description="CIRO ADK agents service account"

gcloud projects add-iam-policy-binding ciro-hackathon-2025 \
  --member="serviceAccount:ciro-agent-sa@ciro-hackathon-2025.iam.gserviceaccount.com" \
  --role="roles/aiplatform.user"

gcloud iam service-accounts keys create ./credentials.json \
  --iam-account=ciro-agent-sa@ciro-hackathon-2025.iam.gserviceaccount.com

export GOOGLE_APPLICATION_CREDENTIALS="./credentials.json"

# Option B: Gemini API key (simpler for hackathon)
# Go to https://aistudio.google.com/app/apikey
# Create API key → save as GOOGLE_API_KEY in .env
```

### Step 4: Firebase Setup

```bash
npm install -g firebase-tools
firebase login
firebase init  # Select: Firestore, Functions (optional), Hosting (optional)
# Select project: ciro-hackathon-2025
```

Then in Firebase Console:
1. Go to Project Settings → General → Add app → Android
2. Package name: `com.ciro.app`
3. Download `google-services.json` → put in `android/app/`
4. Enable **Cloud Messaging** in Firebase Console → Project Settings → Cloud Messaging

### Step 5: Pub/Sub Topics

```bash
gcloud pubsub topics create ciro-signals
gcloud pubsub topics create ciro-incidents
gcloud pubsub topics create ciro-notifications
gcloud pubsub topics create ciro-retraction

gcloud pubsub subscriptions create ciro-signals-sub \
  --topic=ciro-signals \
  --ack-deadline=60

gcloud pubsub subscriptions create ciro-incidents-sub \
  --topic=ciro-incidents \
  --ack-deadline=60
```

### Step 6: Apify (Twitter/X scraping)

1. Sign up at [apify.com](https://apify.com) — free plan gives $5/month credits
2. Go to Settings → Integrations → copy **API Token**
3. Save as `APIFY_API_TOKEN` in `.env`
4. Actor to use: `kaitoeasyapi~twitter-x-data-tweet-scraper-pay-per-result-cheapest`
   - Cost: $0.25 per 1,000 tweets, pay-per-result, no monthly fee
   - No Twitter API key needed
5. For demo, run it with search queries like: `"flooding Islamabad" OR "flood G-10" OR "heat emergency Islamabad"`

### Step 7: OpenWeatherMap (fallback weather)

1. Sign up at [openweathermap.org/api](https://openweathermap.org/api)
2. Free tier: 60 calls/minute, 1,000,000 calls/month
3. Go to API keys tab → copy key → save as `OWM_API_KEY`

### Step 8: GDACS (free, no key needed)

- RSS feed: `https://www.gdacs.org/xml/rss.xml`
- JSON API: `https://www.gdacs.org/gdacsapi/api/events/geteventlist/SEARCH?limit=20`
- No authentication required

### Step 9: Firestore Initial Setup

```bash
# Create database in Firebase Console or via gcloud
gcloud firestore databases create --location=asia-south1 --type=firestore-native
```

Then in Firebase Console → Firestore → Rules, paste:
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /incidents/{incidentId} {
      allow read: if request.auth != null;
      allow write: if request.auth.token.role == 'agent' || request.auth.token.role == 'admin';
    }
    match /signals/{signalId} {
      allow read: if request.auth != null;
      allow write: if request.auth.token.role == 'agent';
    }
    match /resources/{resourceId} {
      allow read, write: if request.auth != null;
    }
    match /notifications/{notifId} {
      allow read: if request.auth != null;
    }
    match /metrics/{metricId} {
      allow read: if request.auth != null;
    }
  }
}
```

### Step 10: ADK Installation

```bash
python -m venv .venv
source .venv/bin/activate  # Windows: .venv\Scripts\activate

# Install ADK stable
pip install google-adk

# Install ADK 2.0 Beta (for Workflow Runtime and Task API)
pip install --pre --force google-adk

# Verify
adk --version

# Required dependencies for CIRO
pip install \
  google-cloud-pubsub \
  google-cloud-firestore \
  google-maps-routing \
  firebase-admin \
  httpx \
  pydantic \
  apify-client \
  python-dotenv \
  fastapi \
  uvicorn

# Add ADK docs MCP server to Antigravity for best code generation:
# In Antigravity → ... menu → Manage MCP Servers → View raw config → paste:
# {
#   "mcpServers": {
#     "adk-docs-mcp": {
#       "command": "uvx",
#       "args": ["--from", "mcpdoc", "mcpdoc", "--urls",
#                "AgentDevelopmentKit:https://adk.dev/llms.txt", "--transport", "stdio"]
#     }
#   }
# }
```

---

## FIRESTORE SCHEMA (TEAM CONTRACT — DO NOT CHANGE WITHOUT CONSENSUS)

### Collection: `signals`
```json
{
  "signal_id": "uuid-v4",
  "source_type": "weather | traffic | social | field_report | sensor | official | news",
  "source_name": "google_weather | owm | open_meteo | maps_traffic | apify_twitter | gdacs | mock_sensor | field_team",
  "timestamp": "ISO8601",
  "location": {
    "lat": 33.6844,
    "lng": 73.0479,
    "area_name": "G-10 Islamabad",
    "geolocation_confidence": 0.85,
    "radius_m": 500
  },
  "raw_payload": {},
  "credibility_score": 0.75,
  "urgency_language_score": 0.6,
  "mention_velocity": 12,
  "contradiction_flag": false,
  "related_incident_id": null,
  "processed": false,
  "created_at": "ISO8601"
}
```

### Collection: `incidents`
```json
{
  "incident_id": "uuid-v4",
  "state": "MONITORING | HYPOTHESIS | VERIFICATION_REQUESTED | CONFIRMED | RETRACTED | RESOLVED",
  "crisis_type": "flood | heatwave | accident | infrastructure_failure | power_outage | protest | disease_cluster | water_main_burst | road_blockage",
  "severity_level": 3,
  "confidence_score": 0.82,
  "conflicting_hypothesis": {
    "type": "water_main_burst",
    "confidence": 0.41,
    "evidence_signal_ids": ["uuid1"]
  },
  "location": {
    "lat": 33.6844,
    "lng": 73.0479,
    "area_name": "G-10 Islamabad",
    "affected_radius_km": 2.5
  },
  "affected_population_estimate": 15000,
  "expected_duration_hours": 4,
  "peak_impact_time": "ISO8601",
  "spread_risk": "low | medium | high",
  "severity_forecast": {
    "t_plus_1h": 3,
    "t_plus_2h": 4,
    "t_plus_6h": 2,
    "uncertainty_range": 1
  },
  "resources_allocated": ["resource-uuid-1", "resource-uuid-2"],
  "stakeholder_notifications_sent": ["public", "emergency_services", "hospital"],
  "signal_ids": ["signal-uuid-1", "signal-uuid-2"],
  "response_actions": [],
  "audit_log": [
    {
      "timestamp": "ISO8601",
      "action": "state_change",
      "from": "HYPOTHESIS",
      "to": "CONFIRMED",
      "reason": "Field team confirmed flooding",
      "agent": "crisis_detection_agent"
    }
  ],
  "created_at": "ISO8601",
  "updated_at": "ISO8601"
}
```

### Collection: `resources`
```json
{
  "resource_id": "uuid-v4",
  "type": "ambulance | police_unit | rescue_team | water_tanker | shelter | generator | field_team | drone",
  "state": "AVAILABLE | SHADOW_COMMITTED | DISPATCHED | UNAVAILABLE",
  "current_location": { "lat": 33.7215, "lng": 73.0433, "name": "Rescue 15 HQ" },
  "assigned_incident_id": null,
  "eta_minutes": null,
  "capacity": 1,
  "unit_name": "Ambulance-07",
  "contact": "+92-51-XXXXXXX",
  "last_updated": "ISO8601"
}
```

### Collection: `notifications`
```json
{
  "notification_id": "uuid-v4",
  "incident_id": "uuid-v4",
  "stakeholder_type": "public | emergency_services | hospital | utility | transport | media | command_center",
  "channel": "fcm | sms | email | dashboard",
  "message_title": "Flood Alert: G-10",
  "message_body": "Flooding reported in G-10. Avoid Margalla Road. Use Kashmir Highway.",
  "is_retraction": false,
  "sent_at": "ISO8601",
  "delivery_status": "sent | delivered | failed"
}
```

### Collection: `metrics`
```json
{
  "metric_id": "uuid-v4",
  "incident_id": "uuid-v4",
  "pipeline_run_id": "uuid-v4",
  "signal_to_detection_ms": 4200,
  "detection_to_allocation_ms": 8100,
  "allocation_to_notification_ms": 3300,
  "total_end_to_end_ms": 15600,
  "agents_invoked": ["signal_fusion", "crisis_detection", "resource_allocation", "stakeholder_notification"],
  "api_calls_made": { "google_weather": 2, "maps_routes": 6, "gemini": 8, "apify": 1 },
  "fallbacks_triggered": [],
  "false_positive": false,
  "recorded_at": "ISO8601"
}
```

---

## ANTIGRAVITY MASTER PROMPT

> Copy everything below this line into Antigravity to start the project. Before pasting, make sure you have added the ADK docs MCP server (see Step 10 above) so Antigravity has current ADK knowledge.

---

```
You are building CIRO — Crisis Intelligence & Response Orchestrator — a production-grade, multi-agent AI system for real-time urban crisis detection and response. This is for a competitive hackathon. The system must be complete, robust, and demo-ready.

## PROJECT CONTEXT

City: Islamabad, Pakistan (demo city). Grid sectors G-10, G-11, F-10, I-10 are the primary demo zones.
Primary demo scenario: Urban flooding in G-10 + simultaneous heat emergency in I-10, with a false alarm (water-main burst) recovery scenario.

## TECH STACK (NON-NEGOTIABLE)

Backend:
- Python 3.11+
- Google ADK 2.0 Beta (pip install --pre --force google-adk) — MANDATORY for all agent orchestration
- Gemini 2.5 Flash as the LLM for all agents (model="gemini-2.5-flash")
- Google Cloud Pub/Sub — signal streaming bus (use ADK's built-in PubSubToolset from google.adk.tools.pubsub)
- Firebase Firestore — state persistence, incident tracking, audit logs, metrics
- Google Cloud Run — deployment target
- FastAPI — HTTP server wrapping ADK agents (ADK uses FastAPI natively)

APIs (primary):
- Google Weather API: https://weather.googleapis.com/v1/currentConditions:lookup and /forecast/hours:lookup
- Google Maps Routes API (TRAFFIC_AWARE_OPTIMAL routing)
- Google Maps Route Matrix API (batch travel time computation)
- Google Maps Places API (New) — find hospitals, shelters, fire stations near crisis zones
- Google Maps Geocoding API — normalize vague location strings to lat/lng
- Apify Twitter scraper (actor: kaitoeasyapi~twitter-x-data-tweet-scraper-pay-per-result-cheapest) — social signal source

APIs (fallback):
- OpenWeatherMap (OWM_API_KEY) — weather fallback if Google Weather API fails
- Open-Meteo (no key needed: https://api.open-meteo.com/v1/forecast) — second weather fallback
- GDACS RSS (https://www.gdacs.org/xml/rss.xml) — official disaster signal

Mobile:
- Kotlin Android (NOT Flutter — team has decided Kotlin)
- Firebase Firestore Kotlin KTX (snapshot listeners using callbackFlow pattern)
- Google Maps SDK for Android
- Firebase Cloud Messaging (FCM) for push notifications
- MVVM architecture with ViewModel + Repository pattern
- Coroutines + Flow for async

## ENVIRONMENT VARIABLES (.env)

GOOGLE_API_KEY=<gemini api key>
GOOGLE_MAPS_API_KEY=<maps/weather api key>
GOOGLE_CLOUD_PROJECT=ciro-hackathon-2025
APIFY_API_TOKEN=<apify token>
OWM_API_KEY=<openweathermap key>
FIREBASE_CREDENTIALS_PATH=./firebase-admin-sdk.json

## PROJECT STRUCTURE TO CREATE

```
ciro/
├── .env
├── requirements.txt
├── firebase-admin-sdk.json          # download from Firebase Console
├── google-services.json             # for Android app reference
│
├── agents/
│   ├── __init__.py
│   ├── orchestrator/
│   │   ├── __init__.py
│   │   └── agent.py                 # Agent 0: Orchestrator (Manager Agent)
│   ├── signal_fusion/
│   │   ├── __init__.py
│   │   ├── agent.py                 # Agent 1: Signal Fusion Agent
│   │   └── tools/
│   │       ├── weather_tool.py
│   │       ├── traffic_tool.py
│   │       ├── social_tool.py       # Apify wrapper
│   │       ├── gdacs_tool.py
│   │       ├── geocoding_tool.py
│   │       └── credibility_scorer.py
│   ├── crisis_detection/
│   │   ├── __init__.py
│   │   └── agent.py                 # Agent 2: Crisis Detection & Classification
│   ├── severity_prediction/
│   │   ├── __init__.py
│   │   └── agent.py                 # Agent 3: Severity & Evolution Prediction
│   ├── resource_allocation/
│   │   ├── __init__.py
│   │   ├── agent.py                 # Agent 4: Resource Allocation & Optimization
│   │   └── optimizer.py             # Constraint satisfaction allocation logic
│   └── stakeholder_notification/
│       ├── __init__.py
│       ├── agent.py                 # Agent 5: Stakeholder Notification & Action Simulation
│       └── templates/
│           ├── public_alert.py
│           ├── emergency_services.py
│           ├── hospital.py
│           ├── utility.py
│           ├── transport.py
│           └── retraction.py
│
├── ingestion/
│   ├── __init__.py
│   ├── signal_collector.py          # Polls all sources, publishes to Pub/Sub
│   ├── mock_social_stream.py        # Generates realistic mock social posts for demo
│   └── seed_resources.py            # Seeds Firestore with mock resource pool
│
├── schemas/
│   ├── signal.py                    # Pydantic models for all schemas
│   ├── incident.py
│   ├── resource.py
│   ├── notification.py
│   └── metrics.py
│
├── services/
│   ├── firestore_service.py         # All Firestore read/write operations
│   ├── pubsub_service.py            # Pub/Sub publish/subscribe helpers
│   └── metrics_service.py           # Pipeline latency + cost tracking
│
├── main.py                          # FastAPI + ADK server entrypoint
│
└── android/                         # Kotlin Android app
    └── app/
        ├── src/main/java/com/ciro/app/
        │   ├── ui/
        │   │   ├── dashboard/        # Admin/command center dashboard screen
        │   │   ├── incidents/        # Active incidents list + detail screen
        │   │   ├── map/              # Google Maps screen with incident overlays
        │   │   └── notifications/    # Notification history screen
        │   ├── data/
        │   │   ├── repository/
        │   │   │   ├── IncidentRepository.kt
        │   │   │   ├── ResourceRepository.kt
        │   │   │   └── NotificationRepository.kt
        │   │   └── model/
        │   │       ├── Incident.kt
        │   │       ├── Resource.kt
        │   │       └── CiroNotification.kt
        │   ├── viewmodel/
        │   │   ├── IncidentViewModel.kt
        │   │   └── DashboardViewModel.kt
        │   └── service/
        │       └── CiroFirebaseMessagingService.kt
        └── build.gradle.kts
```

## AGENT 0: ORCHESTRATOR — FULL SPECIFICATION

Build the Orchestrator as a Coordinator Agent using ADK 2.0's multi-agent pattern. It must:

1. Listen to the `ciro-signals` Pub/Sub topic via ADK's PubSubToolset
2. On receiving a batch of signals, invoke Agent 1 (Signal Fusion) as a sub-agent
3. Pass fused signal output to Agent 2 (Crisis Detection) as a sub-agent
4. Based on confidence threshold from Agent 2:
   - confidence < 0.4: write to Firestore signals collection, set incident state MONITORING, do nothing else
   - confidence 0.4–0.6: set incident state HYPOTHESIS, invoke Agent 3 for preliminary prediction, dispatch field verification team (set resource state SHADOW_COMMITTED), do NOT send public notification
   - confidence 0.6–0.8: set incident state VERIFICATION_REQUESTED, invoke Agent 3 + Agent 4, send internal alerts to emergency_services only
   - confidence > 0.8: set incident state CONFIRMED, invoke all agents, send all stakeholder notifications
5. Handle the RETRACTED state: when field team contradicts hypothesis, invoke Agent 5 to generate retraction messages, recall dispatched resources, update audit log
6. Write a structured Antigravity trace artifact for every pipeline run (see trace format below)
7. Track all pipeline metrics (latency per stage, API calls, fallbacks triggered) and write to Firestore `metrics` collection
8. Handle simultaneous incidents: maintain separate incident state for each, show explicit resource trade-off reasoning when two incidents compete for the same resource type

Use ADK 2.0 Workflow Runtime for deterministic execution flow. Show the graph-based workflow with routing logic between agents.

Antigravity trace format (write as structured JSON to Firestore `agent_traces` collection on every run):
```json
{
  "trace_id": "uuid",
  "pipeline_run_id": "uuid",
  "incident_id": "uuid",
  "timestamp": "ISO8601",
  "stages": [
    {
      "agent": "signal_fusion_agent",
      "input_signal_count": 5,
      "tool_calls": ["get_weather_signal", "get_traffic_signal", "get_social_signals"],
      "credibility_scores_assigned": [0.95, 0.85, 0.35, 0.40, 0.78],
      "contradiction_flags": 1,
      "duration_ms": 2100,
      "output_summary": "4 signals corroborating flood hypothesis, 1 contradicting (water main)"
    }
  ],
  "confidence_at_detection": 0.82,
  "routing_decision": "FULL_RESPONSE",
  "resource_trade_off_narrative": "...",
  "actions_simulated": [],
  "fallbacks_triggered": []
}
```

## AGENT 1: SIGNAL FUSION — FULL SPECIFICATION

Build as an ADK LLMAgent with the following function tools:

### Tool: get_weather_signal(lat, lng)
Primary: Call Google Weather API
```
GET https://weather.googleapis.com/v1/currentConditions:lookup
    ?key={GOOGLE_MAPS_API_KEY}
    &location.latitude={lat}
    &location.longitude={lng}
```
Also call hourly forecast for next 6 hours:
```
GET https://weather.googleapis.com/v1/forecast/hours:lookup
    ?key={GOOGLE_MAPS_API_KEY}
    &location.latitude={lat}
    &location.longitude={lng}
    &hours=6
```
Extract: precipitation probability, QPF (quantitative precipitation forecast), temperature, thunderstorm probability, wind speed.
Fallback chain:
  1. If Google Weather API returns non-200: try OpenWeatherMap `/data/2.5/weather?lat={lat}&lon={lng}&appid={OWM_API_KEY}`
  2. If OWM fails: try Open-Meteo `https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lng}&current=temperature_2m,precipitation,rain,wind_speed_10m&hourly=precipitation_probability`
  3. If all fail: return degraded_mode=True, credibility_score=0.0, log fallback_triggered event

Credibility scores: Google Weather=0.95, OWM=0.80, Open-Meteo=0.70, all failed=0.0

### Tool: get_traffic_signal(lat, lng, radius_km)
Call Google Maps Routes API to check congestion around the crisis zone.
Use Route Matrix API to get travel times from surrounding grid points to the crisis center.
Extract: average congestion index (0–1), number of route segments above threshold, estimated speed reduction %.
If Routes API fails: use cached last-known traffic data from Firestore (cache TTL: 15 minutes).
Credibility: live data=0.90, cached data=0.60

### Tool: get_social_signals(search_query, location_name, max_results=50)
Call Apify actor via HTTP POST:
```
POST https://api.apify.com/v2/acts/kaitoeasyapi~twitter-x-data-tweet-scraper-pay-per-result-cheapest/runs
     ?token={APIFY_API_TOKEN}
Body: {
  "searchTerms": ["{search_query} {location_name}"],
  "maxItems": max_results,
  "sort": "Latest"
}
```
Poll for results (actor runs asynchronously — poll /datasets/{runId}/items endpoint).
For each tweet, extract: text, created_at, user.followers_count, geo (if available), retweet_count, like_count.
Apply urgency language scoring: scan for keywords ["flood", "flooding", "water", "stuck", "help", "emergency", "trapped", "road blocked", "heat", "collapse", "fire", "burst pipe"] — score 0.0–1.0 based on match count and severity.
Compute mention_velocity: count of matching tweets in last 30 minutes.
Geocode any location mentions using Geocoding API.
Fallback: if Apify fails or quota exhausted, use mock_social_stream.py (always available).
Credibility: verified account (followers > 10k)=0.65, regular user + exact location=0.45, anonymous=0.25

### Tool: get_gdacs_signal(lat, lng, radius_km=50)
Fetch `https://www.gdacs.org/gdacsapi/api/events/geteventlist/SEARCH?limit=10` 
Parse XML/JSON response, filter events within radius_km of (lat, lng).
Credibility: 0.95 (official international disaster system)

### Tool: get_field_report(incident_id)
Query Firestore `signals` collection where source_type="field_report" AND related_incident_id=incident_id.
Return all unprocessed field reports for this incident.
Credibility: with_photo=0.80, text_only=0.65

### Credibility Scoring Logic (runs after all tools)
For each signal, assign final credibility_score using the base scores above, modified by:
- +0.10 if geolocation_confidence > 0.85
- +0.05 × min(mention_velocity / 20, 1.0) for social signals (velocity boost, capped at +0.05)
- -0.20 if contradiction_flag is True (contradicted by higher-credibility source)
- -0.15 if signal is older than 30 minutes

Output per signal: the full signal schema defined in the Firestore schema section above.
Write all signals to Firestore `signals` collection.
Publish to `ciro-signals` Pub/Sub topic.

### Contradiction Detection
After collecting all signals, run a contradiction check:
- If Google Weather shows precipitation < 10% BUT social posts mention flooding → contradiction_flag = True on social posts
- If official sensor shows normal water pressure BUT social posts mention flooding → set conflicting_hypothesis in the signal
- If GDACS shows no alert BUT field report says major incident → log discrepancy, do not auto-flag (field reports are local and GDACS covers regional events)

### Degraded Mode
If more than 2 of 4 signal sources are unavailable:
- Downgrade maximum possible confidence_score to 0.65 (cannot achieve CONFIRMED state without at least 2 corroborating sources)
- Add degraded_mode=True flag to output
- Automatically request field verification for any HYPOTHESIS incidents

## AGENT 2: CRISIS DETECTION & CLASSIFICATION — FULL SPECIFICATION

Build as an ADK LLMAgent with Gemini 2.5 Flash structured output (enforce JSON schema via response_schema parameter).

Input: list of fused signals from Agent 1
Output: incident classification JSON matching the `incidents` Firestore schema

System instruction for the agent:
"You are a crisis detection specialist for an urban emergency management system. You receive fused signals from multiple sources with credibility scores. Your job is to classify the most likely crisis type, assign a confidence score, identify conflicting hypotheses, and determine the appropriate response threshold. Be conservative: when signals conflict, output both hypotheses and wait for verification rather than escalating to full response. Always show your reasoning."

Structured output schema to enforce:
```json
{
  "primary_classification": {
    "crisis_type": "string",
    "severity_level": "integer 1-5",
    "confidence_score": "float 0.0-1.0",
    "reasoning": "string"
  },
  "conflicting_hypothesis": {
    "type": "string or null",
    "confidence": "float 0.0-1.0",
    "evidence": "string"
  },
  "affected_radius_km": "float",
  "affected_population_estimate": "integer",
  "expected_duration_hours": "float",
  "spread_risk": "low | medium | high",
  "verification_needed": "boolean",
  "recommended_threshold_action": "MONITOR | HYPOTHESIS | VERIFY | INTERNAL_ALERT | FULL_RESPONSE"
}
```

Crisis type classification rules (include these in agent instructions):
- flood: precipitation > 20mm/hr OR social mentions + traffic congestion > 0.7 in same area
- heatwave: temperature > 42°C AND humidity > 40% AND duration > 3h
- water_main_burst: social mentions of "pipe burst" OR "water gushing" without precipitation signal
- infrastructure_failure: multiple social posts about power/bridge/building + official source
- Confidence formula: weighted average of credibility_score × relevance_to_crisis_type for each signal

## AGENT 3: SEVERITY & EVOLUTION PREDICTION — FULL SPECIFICATION

Build as ADK LLMAgent with the following function tools:

### Tool: get_historical_incidents(lat, lng, crisis_type, radius_km=5, days_back=30)
Query Firestore: incidents collection WHERE crisis_type = crisis_type, resolved within days_back days, within radius_km of (lat, lng).
Return count, average severity, average duration, average affected population.
Use this to compute historical_base_probability.

### Tool: get_weather_forecast(lat, lng, hours=12)
Call Google Weather API hourly forecast endpoint for next 12 hours.
Extract precipitation probability per hour, temperature trend, thunderstorm risk.
Build a simple threshold model: if precipitation probability > 60% in next 2h → severity likely to increase.

### Tool: get_vulnerable_facilities(lat, lng, radius_km)
Call Google Maps Places API (New) with types: ["hospital", "school", "pharmacy", "fire_station", "police", "elderly_care_center", "shelter"].
Return count per type and nearest facility of each type with distance.
This feeds population vulnerability scoring.

### Tool: get_congestion_spread_prediction(lat, lng, radius_km, crisis_type)
Call Google Maps Route Matrix API with origins at crisis center and surrounding grid points, destinations at key evacuation routes.
Model congestion spread: if crisis is flood, model water flow direction using terrain (simplified: use elevation data from Open-Elevation API: https://api.open-elevation.com/api/v1/lookup).
Return: which roads will saturate at T+1h, T+2h, T+6h.

Output: populate the `severity_forecast` and `spread_risk` fields in the incident document.
Include uncertainty_range based on data quality (degraded mode = higher uncertainty).

## AGENT 4: RESOURCE ALLOCATION & OPTIMIZATION — FULL SPECIFICATION

Build as ADK LLMAgent with the following approach:

### Resource Pool (seed in Firestore via seed_resources.py)
Mock data for Islamabad, realistic locations:
- 8 ambulances (RESCUE 15): locations distributed across sectors
- 6 police traffic units: sectors F-6, G-6, G-10, I-8
- 4 rescue teams: NDMA HQ location
- 3 water tankers: CDA water depot locations
- 2 field verification teams: roving, last-known locations
- 5 shelters: mosques, schools (capacity 200–500 each)
- 3 portable generators: utility depot

### Tool: compute_travel_times(resource_ids, incident_location)
Call Google Maps Route Matrix API:
- Origins: current locations of each available resource
- Destinations: incident location
- Routing: TRAFFIC_AWARE_OPTIMAL
- Return: resource_id → eta_minutes mapping

### Tool: allocate_resources(incident_id, required_resources, priority_score)
Constraint satisfaction allocation:
1. Compute priority_score for the incident: severity_level × log(affected_population) × spread_risk_multiplier / eta_minutes
2. For AVAILABLE resources: allocate greedily by lowest ETA for the resource type needed
3. For SHADOW_COMMITTED resources: only re-allocate if this incident's priority_score is > 1.5× the shadow-committed incident's score
4. Update resource state in Firestore atomically (use Firestore transactions)
5. Return allocation plan with narrative

### Multi-crisis Trade-off Narrative
When two simultaneous incidents compete:
Produce explicit narrative: "Incident A (flood, severity 4) scores 8.2. Incident B (heatwave, severity 3) scores 5.1. Allocating 3/5 ambulances to Incident A. Incident B receives 2 ambulances. This leaves Incident B with estimated 18% higher risk of preventable heat casualties. Recommend requesting mutual aid from adjacent city sector."

Shadow Mode:
If confidence is HYPOTHESIS (0.4–0.6) for an incident, set nearby matching resources to SHADOW_COMMITTED state. They are not physically moved but are locked from other allocation for up to 30 minutes (configurable timeout).

## AGENT 5: STAKEHOLDER NOTIFICATION & ACTION SIMULATION — FULL SPECIFICATION

Build as ADK LLMAgent.

### Stakeholder Message Generation
For each confirmed incident, generate tailored messages for each stakeholder type using Gemini. Message templates:

public_alert: Max 160 characters. Plain Urdu/English bilingual. Include: what, where, what to do. Example: "⚠️ FLOOD ALERT G-10: Avoid Margalla Road. Use Kashmir Highway. بارش کی وجہ سے جی-10 میں سیلاب - مارگلہ روڈ سے گریز کریں"

emergency_services: Full operational brief. Include: incident type + coords + severity + resource assignment + rendezvous point + estimated ETA + hospital contact.

hospital: Patient surge forecast. Include: expected patient type (drowning risk, heat exhaustion, trauma), estimated volume in next 2h, recommended bed preparation.

utility_company: Specific infrastructure risk. For flood: sewer overflow risk, substation protection needed. For power outage: estimated affected circuits.

transport_authority: Specific road closure request with Routes API–generated alternate routes (include polyline or route description).

media_command_center: Full incident brief with confidence score, uncertainty range, timeline, resource deployment status.

### Staged Public Alerting
Do NOT send one mass alert. Implement staged alerting:
1. Alert Zone A (immediate flood zone) first
2. Wait 15 minutes
3. Alert Zone B (adjacent sectors likely to receive evacuees)
4. Monitor Routes API for congestion buildup on alert-triggered evacuation routes
5. If Kashmir Highway congestion index > 0.7 before Zone B alert, redirect Zone B to alternate route in the alert

### Action Simulation Output
For each response action, generate a structured simulation record:
```json
{
  "action_id": "uuid",
  "incident_id": "uuid",
  "action_type": "traffic_reroute | emergency_dispatch | hospital_prep | utility_escalation | public_alert | evacuation",
  "before_state": {
    "description": "...",
    "metric_value": 0.85,
    "metric_unit": "congestion_index"
  },
  "response_action": "Close eastbound lane G-10 Margalla Road, activate VMS, redirect via Kashmir Highway",
  "expected_after_state": {
    "description": "...",
    "metric_value": 0.45,
    "metric_unit": "congestion_index"
  },
  "response_time_improvement_minutes": 4,
  "resource_cost": "2 police units × 3 hours",
  "possible_side_effects": ["Kashmir Highway congestion +0.20", "Faizabad interchange secondary bottleneck risk"],
  "simulated_at": "ISO8601"
}
```
Write all action simulations to Firestore `incidents/{incidentId}/response_actions` subcollection.

### Retraction Flow
When incident state changes to RETRACTED:
1. Generate retraction messages for each stakeholder who received an alert
2. Public retraction must acknowledge the false alarm: "✅ UPDATE: Previous flood alert for G-10 cancelled. Investigation confirms water main burst, not flooding. Area safe. Utility crew dispatched."
3. Update all DISPATCHED resources back to AVAILABLE state
4. Create audit log entry with: original signal, classification reason, contradicting evidence, retraction timestamp, agent that detected contradiction
5. Write to Firestore `incidents/{id}/audit_log`

## KOTLIN ANDROID APP — FULL SPECIFICATION

### Architecture
MVVM + Clean Architecture. Use:
- Hilt for dependency injection
- Coroutines + Flow for async
- Firestore KTX snapshot listeners wrapped in callbackFlow
- Google Maps Compose (latest) for map screens
- Material 3 components

### Screens to Build

1. **Dashboard Screen (Admin/Command Center)**
   - Active incidents count by severity (cards)
   - Resources deployed vs available (progress bars)
   - Pipeline latency metric (average end-to-end ms)
   - Recent agent trace log (scrollable text, reads from `agent_traces` Firestore collection)
   - Color-coded severity: severity 5=red, 4=orange, 3=yellow, 2=blue, 1=green

2. **Incidents Map Screen**
   - Google Maps with incident markers (color by severity)
   - Tap marker → bottom sheet with incident detail
   - Show affected radius as circle overlay
   - Show resource locations as separate markers
   - Real-time updates via Firestore snapshot listener

3. **Incident Detail Screen**
   - Full incident info: type, severity, confidence, affected population, expected duration
   - Severity forecast chart (T+1h, T+2h, T+6h) — use MPAndroidChart or Vico
   - Resources allocated list
   - Response actions simulated (expandable cards)
   - Stakeholder notifications sent (timeline)
   - Audit log (collapsible)
   - If state = RETRACTED: show prominent retraction banner

4. **Notifications Screen**
   - List of all sent notifications grouped by incident
   - Filter by stakeholder type
   - Mark retractions with different styling

5. **User Roles**
   - Admin: sees everything, can manually update incident state, can mark false positive
   - Responder (police/ambulance): sees only assigned incidents and their own resource status
   - Public User: sees public alerts only, map with safe/unsafe zones
   - Implement via Firebase Auth custom claims (role claim set server-side)

### Firestore Kotlin Integration Pattern (use this pattern everywhere)
```kotlin
// In Repository
fun observeIncidents(): Flow<List<Incident>> = callbackFlow {
    val registration = db.collection("incidents")
        .whereIn("state", listOf("MONITORING", "HYPOTHESIS", "CONFIRMED"))
        .orderBy("severity_level", Query.Direction.DESCENDING)
        .addSnapshotListener { snapshot, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            val incidents = snapshot?.documents?.mapNotNull { 
                it.toObject(Incident::class.java) 
            } ?: emptyList()
            trySend(incidents)
        }
    awaitClose { registration.remove() }
}
```

### FCM Push Notifications
Send push notifications from Agent 5 via Firebase Admin SDK (Python):
```python
import firebase_admin
from firebase_admin import messaging

def send_push_notification(title, body, topic, data={}):
    message = messaging.Message(
        notification=messaging.Notification(title=title, body=body),
        data=data,
        topic=topic  # topics: "public_alerts", "emergency_services", "admin"
    )
    return messaging.send(message)
```
In Android app, subscribe to topics based on user role.

## MOCK SOCIAL STREAM (Person 1 builds this)

Build `ingestion/mock_social_stream.py` that generates realistic mock tweets published to `ciro-signals` Pub/Sub.

Scenario 1 (flooding G-10): emit 15–20 tweets over 10 minutes with escalating urgency:
- T+0: 2 tweets "heavy rain in G-10" (urgency=0.2)
- T+3min: 5 tweets "road flooding G-10 Margalla" (urgency=0.6)
- T+5min: 8 tweets "cars stuck G-10 flood HELP" (urgency=0.9)
- T+7min: 1 tweet "looks like a pipe burst not flood?" (urgency=0.3, contradiction flag)

Scenario 2 (heatwave I-10): emit 8 tweets about heat emergency, elderly affected.

Include realistic metadata: fake user names, follower counts (10–50,000), timestamps, geo tags (accurate Islamabad coords within ±0.005° of actual G-10 center: 33.6844°N, 73.0479°E).

## SEED RESOURCES (Person 1 or 2 builds this)

Build `ingestion/seed_resources.py` that populates Firestore `resources` collection with realistic Islamabad emergency resources. Use actual Islamabad coords for NDMA HQ (33.7215°N, 73.0433°E), Rescue 15 HQ (33.6938°N, 73.0651°E), PIMS Hospital (33.7215°N, 73.0433°E), Poly Clinic (33.7177°N, 73.0691°E), etc.

## DEGRADED MODE & FALLBACK LOGIC

Implement these fallback rules in a centralized `services/fallback_manager.py`:

| Failure | Fallback Action |
|---------|----------------|
| Google Weather API down | Try OWM → Try Open-Meteo → degraded_mode=True, cap confidence at 0.65 |
| Google Maps Routes API down | Use Firestore cached routes (TTL 15min) → if no cache, use straight-line distance / 30km/h |
| Apify Twitter fails | Use mock_social_stream.py instead |
| GDACS feed fails | Skip GDACS, reduce max confidence by 0.05 |
| Firestore write fails | Retry 3× with exponential backoff, then write to local JSON file + alert admin |
| All APIs down | Enter manual_escalation mode: alert admin via FCM, pause automated dispatch |

Log every fallback event to Firestore `system_events` collection with timestamp and reason.

## METRICS & OBSERVABILITY

Implement in `services/metrics_service.py`:

Track and store in Firestore `metrics` collection:
1. Pipeline latency: signal_to_detection, detection_to_allocation, allocation_to_notification, total_end_to_end
2. API call counts per run per API
3. Confidence score distribution (histogram bins)
4. False positive rate (manual admin feedback)
5. Resource utilization rate (dispatched / total)
6. Alert acknowledgment rate (via FCM delivery receipts)

Display on admin dashboard in Android app.

Baseline comparison (for README + judges):
- Manual dispatch benchmark: 5–20 minutes from signal to first resource dispatch
- CIRO target: < 60 seconds end-to-end
- Store both values and show improvement ratio in dashboard

## DEMO SCENARIO SCRIPT

Build a single script `demo/run_demo_scenario.py` that triggers the full demo in sequence:

```
Step 1: Start signal collector (background)
Step 2: Inject Scenario 1 (flood G-10) via mock social stream
Step 3: Inject weather signal (heavy rain, Google Weather API)
Step 4: Inject traffic signal (congestion spike G-10)
Step 5: Wait — watch agent pipeline run automatically
Step 6: Inject contradicting signal (field report: water main, not flood)
Step 7: Watch confidence fluctuate, HYPOTHESIS state triggered
Step 8: Inject field verification confirmation: "confirmed flooding, water main also burst"
Step 9: Watch transition to CONFIRMED, full response triggered
Step 10: Inject Scenario 2 (heat emergency I-10) simultaneously
Step 11: Watch resource trade-off narrative for competing incidents
Step 12: Inject retraction signal for I-10 (false alarm — it was a power outage, not heat emergency)
Step 13: Watch retraction flow, recall resources, retraction notifications
```

Add 5-second pauses between steps and print progress to console.

## ERROR HANDLING STANDARDS

All tool functions must:
1. Return a typed dict with `status: "success" | "error" | "degraded"` and `data` or `error_message`
2. Never raise exceptions that bubble up to the agent — catch all exceptions and return error status
3. Log all errors with structured JSON to Firestore `system_events` collection
4. Include `source_name`, `error_type`, `timestamp`, `fallback_triggered` in every error log

## INITIAL SETUP COMMANDS

Run these in order after setting up all API keys in .env:

```bash
# 1. Initialize Firestore collections and seed data
python ingestion/seed_resources.py

# 2. Run ADK agent locally to verify all tools work
adk run agents/orchestrator

# 3. Start full agent server locally
adk api_server agents/orchestrator --port 8080

# 4. Run demo scenario
python demo/run_demo_scenario.py

# 5. Deploy to Cloud Run (after local testing)
gcloud run deploy ciro-backend \
  --source . \
  --region asia-south1 \
  --allow-unauthenticated \
  --set-env-vars GOOGLE_API_KEY=${GOOGLE_API_KEY},GOOGLE_MAPS_API_KEY=${GOOGLE_MAPS_API_KEY}
```

## README SECTIONS TO WRITE (assign to Person 3)

1. Architecture diagram (include agent graph)
2. Data stream schemas (reference Firestore schema above)
3. Antigravity role + trace format
4. APIs/tools used with justification for each
5. Setup steps (reference this document)
6. Assumptions (mock social data, approximate population density, demo scale)
7. Privacy note (no real PII stored, all locations are public infrastructure)
8. Cost analysis: Google Weather (free preview), Maps Routes ($0 for <5000 calls/month), Gemini 2.5 Flash (Flex tier), Cloud Run ($0 idle), Apify ($0.25/1000 tweets), Pub/Sub (free tier 10GB/month) — total demo cost: <$2
9. Latency analysis: target <60s end-to-end vs 5–20min manual baseline
10. Scalability: describe Vertex AI Agent Engine autoscaling, Pub/Sub partition-per-zone approach for 100× scale
11. Limitations: mock social data, no real census API, hackathon scale only, Apify TOS considerations

## CRITICAL REMINDERS

- Use `gemini-2.5-flash` as the model string everywhere (NOT gemini-pro or older versions)
- ADK 2.0 import path: `from google.adk.agents import Agent` (not LlmAgent for basic agents)
- All ADK agents need `name`, `model`, `instruction`, `tools` parameters minimum
- Use `adk web` command to test agents locally with UI before wiring everything together
- PubSubToolset is at `from google.adk.tools.pubsub.pubsub_toolset import PubSubToolset`
- Firestore admin SDK init: `firebase_admin.initialize_app(credentials.Certificate(FIREBASE_CREDENTIALS_PATH))`
- For Android: google-services.json goes in app/ directory, not project root
- FCM topics for Android subscribe: call `FirebaseMessaging.getInstance().subscribeToTopic("public_alerts")`
- The Kotlin app must work offline with Firestore offline persistence enabled: `FirebaseFirestore.getInstance().firestoreSettings = FirebaseFirestoreSettings.Builder().setPersistenceEnabled(true).build()`

Now generate the complete project starting with:
1. requirements.txt
2. schemas/signal.py, schemas/incident.py, schemas/resource.py, schemas/notification.py, schemas/metrics.py (all Pydantic models)
3. services/firestore_service.py
4. services/fallback_manager.py
5. agents/signal_fusion/tools/ (all 5 tool files)
6. agents/signal_fusion/agent.py
7. ingestion/mock_social_stream.py
8. ingestion/seed_resources.py

Then proceed to agents/crisis_detection, agents/severity_prediction, agents/resource_allocation, agents/stakeholder_notification, agents/orchestrator in that order.

Then build the Android app starting with data models, then repositories, then ViewModels, then UI screens.
```

---

## API KEY QUICK REFERENCE TABLE

| Key | Where to get | Env var name | Cost |
|-----|-------------|-------------|------|
| Google Maps + Weather | [console.cloud.google.com/apis/credentials](https://console.cloud.google.com/apis/credentials) | `GOOGLE_MAPS_API_KEY` | Free (Weather API Preview), Routes ~$0 for demo |
| Gemini API | [aistudio.google.com/app/apikey](https://aistudio.google.com/app/apikey) | `GOOGLE_API_KEY` | Free tier available |
| Firebase Admin SDK | Firebase Console → Project Settings → Service Accounts → Generate key | `FIREBASE_CREDENTIALS_PATH` (path to JSON) | Free |
| Apify | [console.apify.com](https://console.apify.com) → Settings → Integrations | `APIFY_API_TOKEN` | $5/month free credits on free plan |
| OpenWeatherMap | [home.openweathermap.org/api_keys](https://home.openweathermap.org/api_keys) | `OWM_API_KEY` | Free tier (60 calls/min) |
| GDACS | No key needed | N/A | Free |
| Open-Meteo | No key needed | N/A | Free |

---

## FIRESTORE COMPOSITE INDEXES NEEDED

Create these in Firebase Console → Firestore → Indexes:

1. Collection: `incidents`, Fields: `state` ASC, `severity_level` DESC, `created_at` DESC
2. Collection: `signals`, Fields: `related_incident_id` ASC, `created_at` DESC
3. Collection: `signals`, Fields: `source_type` ASC, `processed` ASC, `created_at` DESC
4. Collection: `resources`, Fields: `type` ASC, `state` ASC
5. Collection: `metrics`, Fields: `incident_id` ASC, `recorded_at` DESC

---

## WHAT TO DO IN YOUR FIRST 4 HOURS (Person 1 — Data Ingestion)

1. Set up Google Cloud project and enable APIs (30 min)
2. Get all API keys (see table above) and create `.env` file (15 min)
3. Initialize Firestore database (10 min)
4. Create Pub/Sub topics and subscriptions (10 min)
5. Write `schemas/signal.py` Pydantic model (15 min)
6. Write `services/firestore_service.py` with write_signal() function (20 min)
7. Test Google Weather API with a raw curl call for Islamabad coords (10 min)
8. Write `agents/signal_fusion/tools/weather_tool.py` — primary + all fallbacks (45 min)
9. Write `ingestion/mock_social_stream.py` — this is your demo safety net (30 min)
10. Write `agents/signal_fusion/tools/social_tool.py` — Apify wrapper (30 min)
11. Wire `agents/signal_fusion/agent.py` using ADK — run `adk web` to test (45 min)
12. Hand off to Person 2: confirm Pub/Sub messages are flowing and signals are in Firestore

Total: ~4.5 hours. This unblocks Person 2 and Person 3 completely.
