# CIRO — Person 1: Data Ingestion Layer + Signal Fusion Agent
## Antigravity Prompt

---

> **Your job:** Build everything that touches external data sources and produces normalized, credibility-scored signals into Firestore. Person 2 reads your Firestore output to do detection. Person 3 reads your schemas to build the orchestrator. You are the foundation — if your schemas drift, everything breaks. Do not change schemas after handing off.

---

## CONTEXT

You are building the data ingestion layer and Signal Fusion Agent (Agent 1) for CIRO — Crisis Intelligence & Response Orchestrator. This is a production-grade multi-agent system built with Google ADK 2.0. The system detects urban crises (floods, heatwaves, accidents, infrastructure failures) in Islamabad, Pakistan by fusing signals from multiple real data sources.

**What you own:**
- All external API integrations (weather, traffic, social media, GDACS, field reports)
- Credibility scoring engine
- Signal normalization into a unified schema
- Writing signals to Firestore `signals` collection
- Publishing to Google Cloud Pub/Sub topic `ciro-signals`
- Mock social stream generator (used only in demo scenario and as fallback when Apify is rate-limited)
- Resource seeding script (one-time Firestore population)
- Fallback chains for every API

**What you do NOT own:**
- Crisis classification (Person 2)
- Resource allocation (Person 2)
- Orchestrator agent (Person 3)
- Kotlin app (Person 3)

---

## ENVIRONMENT (already set up)

```
.env already exists with:
GOOGLE_API_KEY=<gemini key from AI Studio>
GOOGLE_MAPS_API_KEY=<maps demo key>
GOOGLE_CLOUD_PROJECT=ciro-hackathon-2025
APIFY_API_TOKEN=<apify token>
OWM_API_KEY=<openweathermap key>
FIREBASE_CREDENTIALS_PATH=./firebase-admin-sdk.json

Firestore: initialized, Spark plan, asia-south1
Pub/Sub topics already created: ciro-signals, ciro-incidents, ciro-notifications
Python venv: active, google-adk installed
```

---

## TECH STACK

- Python 3.11+
- Google ADK 2.0 Beta (`from google.adk.agents import Agent`)
- Gemini 2.5 Flash (`model="gemini-2.5-flash"`)
- Firebase Admin SDK for Firestore writes
- `google-cloud-pubsub` for Pub/Sub publishing
- `httpx` for all async HTTP calls
- `pydantic` v2 for all data models
- `apify-client` for Twitter/X scraping

---

## PROJECT STRUCTURE TO CREATE

```
ciro/
├── .env                          (already exists)
├── firebase-admin-sdk.json       (already exists)
├── requirements.txt
│
├── schemas/
│   ├── __init__.py
│   └── signal.py                 # Pydantic Signal model — THIS IS THE TEAM CONTRACT
│
├── services/
│   ├── __init__.py
│   ├── firestore_service.py      # write_signal(), get_signals_for_incident(), mark_processed()
│   └── pubsub_service.py         # publish_signal(), subscribe_signals()
│
├── agents/
│   └── signal_fusion/
│       ├── __init__.py
│       ├── agent.py              # ADK Agent 1: Signal Fusion Agent
│       └── tools/
│           ├── __init__.py
│           ├── weather_tool.py   # Google Weather API + fallback chain
│           ├── traffic_tool.py   # Google Maps Routes API
│           ├── social_tool.py    # Apify Twitter scraper
│           ├── gdacs_tool.py     # GDACS RSS/JSON feed
│           ├── geocoding_tool.py # Google Geocoding API
│           └── credibility_scorer.py  # Scoring engine
│
└── ingestion/
    ├── __init__.py
    ├── signal_collector.py       # Orchestrates all tools, runs on a loop
    ├── mock_social_stream.py     # Realistic mock tweets for demo scenario only
    └── seed_resources.py         # One-time Firestore resource seeding
```

---

## FIRESTORE SIGNAL SCHEMA (TEAM CONTRACT — DO NOT CHANGE)

This is what Person 2 reads. Every field must be present on every write, even if null.

```python
# schemas/signal.py
from pydantic import BaseModel, Field
from typing import Optional, Any
from datetime import datetime
import uuid

class SignalLocation(BaseModel):
    lat: float
    lng: float
    area_name: str
    geolocation_confidence: float  # 0.0-1.0
    radius_m: Optional[float] = None

class Signal(BaseModel):
    signal_id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    source_type: str   # "weather" | "traffic" | "social" | "field_report" | "sensor" | "official"
    source_name: str   # "google_weather" | "owm" | "open_meteo" | "maps_traffic" | "apify_twitter" | "gdacs" | "mock_social"
    timestamp: datetime
    location: SignalLocation
    raw_payload: dict[str, Any]    # full original API response stored here
    credibility_score: float       # 0.0-1.0, final computed score
    urgency_language_score: float  # 0.0-1.0, NLP urgency score
    mention_velocity: int          # tweets/posts per 30 min window (0 for non-social)
    contradiction_flag: bool       # True if contradicted by higher-credibility source
    related_incident_id: Optional[str] = None
    processed: bool = False        # Person 2 flips this to True after processing
    degraded_mode: bool = False    # True if this signal came from a fallback source
    created_at: datetime = Field(default_factory=datetime.utcnow)
```

---

## FILE 1: requirements.txt

Generate a complete `requirements.txt` with pinned versions for:
- google-adk (latest stable)
- google-cloud-pubsub
- google-cloud-firestore
- firebase-admin
- httpx
- pydantic>=2.0
- apify-client
- python-dotenv
- feedparser (for GDACS RSS)
- geopy (for distance calculations)
- asyncio (stdlib, no pin needed)

---

## FILE 2: services/firestore_service.py

Build a `FirestoreService` class using Firebase Admin SDK. Initialize once using `FIREBASE_CREDENTIALS_PATH` from `.env`.

Implement these methods:
```python
async def write_signal(self, signal: Signal) -> str
    # Writes to signals/{signal_id}, returns document ID
    # Uses .to_dict() with datetime serialization
    # On failure: retry 3x with exponential backoff (1s, 2s, 4s)
    # On all retries failed: log to local file signals_fallback.jsonl and raise

async def get_unprocessed_signals(self, limit: int = 50) -> list[Signal]
    # Query: processed == False, order by created_at DESC, limit
    # Returns list of Signal objects

async def mark_signal_processed(self, signal_id: str, incident_id: str)
    # Update: processed=True, related_incident_id=incident_id

async def get_cached_traffic(self, area_name: str) -> Optional[dict]
    # Query traffic_cache/{area_name}, check if updated_at < 15 min ago
    # Returns raw_payload if fresh, None if stale

async def update_traffic_cache(self, area_name: str, data: dict)
    # Upsert traffic_cache/{area_name} with data + updated_at timestamp
```

Use only synchronous Firestore client wrapped in `asyncio.get_event_loop().run_in_executor()` for async compatibility — do NOT use the async Firestore client, it has known issues with ADK's event loop.

---

## FILE 3: services/pubsub_service.py

```python
# Implement real Google Cloud Pub/Sub using google-cloud-pubsub
# Topic: ciro-signals (already created)

class PubSubService:
    def __init__(self):
        # Initialize PublisherClient with credentials from GOOGLE_APPLICATION_CREDENTIALS
        # or use project from GOOGLE_CLOUD_PROJECT env var
        
    async def publish_signal(self, signal: Signal) -> str:
        # Serialize signal to JSON, publish to projects/{project}/topics/ciro-signals
        # Return message_id
        # On failure: log error, do NOT raise (fire-and-forget pattern)
        
    def subscribe_signals(self, callback: Callable[[Signal], None]):
        # Subscribe to ciro-signals-sub subscription
        # Deserialize message, call callback
        # Used by Person 2's crisis detection agent
```

---

## FILE 4: agents/signal_fusion/tools/geocoding_tool.py

Build `geocode_location(location_string: str) -> SignalLocation` using Google Geocoding API.

```
GET https://maps.googleapis.com/maps/api/geocode/json
    ?address={location_string}
    &key={GOOGLE_MAPS_API_KEY}
    &region=pk
    &bounds=33.5,72.8|33.9,73.3
```

The `bounds` parameter biases results to Islamabad. Extract lat/lng from the first result. Set `geolocation_confidence` based on `geometry.location_type`:
- `ROOFTOP` → 0.95
- `RANGE_INTERPOLATED` → 0.80
- `GEOMETRIC_CENTER` → 0.70
- `APPROXIMATE` → 0.50

If geocoding fails or returns zero results: return a default location centered on Islamabad (33.6844, 73.0479) with geolocation_confidence=0.20.

---

## FILE 5: agents/signal_fusion/tools/weather_tool.py

Build `get_weather_signal(lat: float, lng: float, area_name: str) -> Signal`

**Primary: Google Weather API**
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

Extract into `raw_payload`:
```json
{
  "temperature_c": 32.1,
  "feels_like_c": 38.0,
  "precipitation_probability_pct": 85,
  "qpf_mm": 12.4,
  "thunderstorm_probability_pct": 40,
  "wind_speed_kmh": 35,
  "humidity_pct": 88,
  "condition_type": "HEAVY_RAIN",
  "forecast_6h": [
    {"hour": 1, "precip_prob": 90, "qpf_mm": 5.2},
    ...
  ],
  "source": "google_weather"
}
```

**Fallback 1 (if Google Weather returns non-200 or times out after 5s):**
OpenWeatherMap:
```
GET https://api.openweathermap.org/data/2.5/weather
    ?lat={lat}&lon={lng}&appid={OWM_API_KEY}&units=metric
```
Set `source_name="owm"`, `credibility_score` base = 0.80, `degraded_mode=True`

**Fallback 2 (if OWM also fails):**
Open-Meteo (no key needed):
```
GET https://api.open-meteo.com/v1/forecast
    ?latitude={lat}&longitude={lng}
    &current=temperature_2m,precipitation,rain,wind_speed_10m,relative_humidity_2m
    &hourly=precipitation_probability
    &forecast_days=1
```
Set `source_name="open_meteo"`, `credibility_score` base = 0.70, `degraded_mode=True`

**If all 3 fail:** Return a Signal with `credibility_score=0.0`, `degraded_mode=True`, `raw_payload={"error": "all_weather_sources_failed"}`. Do NOT raise an exception.

Credibility base scores: google_weather=0.95, owm=0.80, open_meteo=0.70

---

## FILE 6: agents/signal_fusion/tools/traffic_tool.py

Build `get_traffic_signal(lat: float, lng: float, area_name: str, radius_km: float = 2.0) -> Signal`

**Primary: Google Maps Routes API with traffic**

Generate 8 surrounding grid points at ~1km spacing around (lat, lng). Call Routes API Route Matrix:

```
POST https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix
Headers:
  X-Goog-Api-Key: {GOOGLE_MAPS_API_KEY}
  X-Goog-FieldMask: originIndex,destinationIndex,duration,distanceMeters,condition
Body:
{
  "origins": [{"waypoint": {"location": {"latLng": {"latitude": lat, "longitude": lng}}}}, ...8 grid points],
  "destinations": [{"waypoint": {"location": {"latLng": {"latitude": lat, "longitude": lng}}}}],
  "travelMode": "DRIVE",
  "routingPreference": "TRAFFIC_AWARE_OPTIMAL",
  "departureTime": "<current UTC ISO8601>"
}
```

Compute congestion_index (0.0–1.0) as:
```
baseline_duration = distance_m / (50km/h * 1000/3600)  # 50km/h free flow
actual_duration = routes_api_duration_seconds
congestion_ratio = actual_duration / baseline_duration
congestion_index = min((congestion_ratio - 1.0) / 2.0, 1.0)  # 0 = free, 1 = gridlock
```

Extract into `raw_payload`:
```json
{
  "congestion_index": 0.75,
  "avg_speed_kmh": 18,
  "affected_routes_count": 5,
  "route_details": [...],
  "source": "maps_traffic"
}
```

**Fallback:** If Routes API fails, call `FirestoreService.get_cached_traffic(area_name)`. If cache exists and < 15 min old, return that with `credibility_score=0.60`, `degraded_mode=True`. If no cache, return Signal with `credibility_score=0.0`, `degraded_mode=True`.

After every successful call, update cache: `FirestoreService.update_traffic_cache(area_name, raw_payload)`.

---

## FILE 7: agents/signal_fusion/tools/social_tool.py

Build `get_social_signals(search_query: str, location_name: str, max_results: int = 50) -> list[Signal]`

**Primary: Apify Twitter scraper**

```python
from apify_client import ApifyClient

client = ApifyClient(os.getenv("APIFY_API_TOKEN"))

run_input = {
    "searchTerms": [f"{search_query} {location_name}"],
    "maxItems": max_results,
    "sort": "Latest",
    "lang": "en,ur"  # English and Urdu
}

run = client.actor("kaitoeasyapi~twitter-x-data-tweet-scraper-pay-per-result-cheapest").call(
    run_input=run_input
)

items = list(client.dataset(run["defaultDatasetId"]).iterate_items())
```

For each tweet, build a Signal:
- `source_type = "social"`
- `source_name = "apify_twitter"`
- `timestamp` = tweet's created_at
- Geocode any location string in tweet text using `geocoding_tool.geocode_location()`
- If tweet has geo coordinates, use those directly with `geolocation_confidence=0.95`

**Urgency language scoring** — scan tweet text for these keyword groups:
```python
URGENCY_KEYWORDS = {
    "critical": ["help", "stuck", "trapped", "emergency", "SOS", "dying", "collapse", "mayday"],
    "high":     ["flood", "flooding", "fire", "accident", "blocked", "burst", "explosion"],
    "medium":   ["water", "rain", "road", "traffic", "broken", "outage", "heat"],
    "low":      ["weather", "wet", "slow", "delay", "warning"]
}
# Score: critical match = 0.9, high = 0.7, medium = 0.4, low = 0.2
# Multiple matches: take max, boost by 0.05 per additional match, cap at 1.0
```

**Credibility scoring per tweet:**
```python
base = 0.25
if tweet.user.followers_count > 100_000: base = 0.65
elif tweet.user.followers_count > 10_000: base = 0.55
elif tweet.user.followers_count > 1_000: base = 0.45
elif tweet.user.followers_count > 100: base = 0.35

if tweet.geo or tweet.place: base += 0.15  # has location data
if tweet.retweet_count > 100: base += 0.10
if tweet.user.verified: base += 0.10
credibility_score = min(base, 0.80)  # social max is 0.80
```

**Mention velocity:** Count tweets matching the query in the last 30 minutes. Set `mention_velocity` on every Signal from this batch.

**Fallback:** If Apify call fails or raises any exception, call `mock_social_stream.generate_signals(search_query, location_name, count=max_results)` and log that fallback was triggered. The mock is ONLY used as fallback here.

---

## FILE 8: agents/signal_fusion/tools/gdacs_tool.py

Build `get_gdacs_signals(lat: float, lng: float, radius_km: float = 100) -> list[Signal]`

```python
import feedparser
import httpx
from geopy.distance import geodesic

# Primary: GDACS JSON API
url = "https://www.gdacs.org/gdacsapi/api/events/geteventlist/SEARCH"
params = {"limit": 20, "alertlevel": "Orange,Red"}

response = await httpx.AsyncClient().get(url, params=params, timeout=10.0)
events = response.json()

# Filter: only events within radius_km of our location
# Use geopy.distance.geodesic((lat, lng), (event_lat, event_lng)).km
# For each in-range event, build a Signal with credibility_score=0.95

# Fallback: GDACS RSS feed
rss_url = "https://www.gdacs.org/xml/rss.xml"
feed = feedparser.parse(rss_url)
# Parse entries, extract geo:lat/geo:long from each entry
```

For each GDACS alert, extract:
```json
{
  "event_type": "FL" | "TC" | "EQ" | "VO" | "DR" | "WF",
  "alert_level": "Green" | "Orange" | "Red",
  "event_name": "...",
  "affected_country": "PK",
  "gdacs_url": "..."
}
```

Map GDACS event types to CIRO crisis types: FL→flood, TC→infrastructure_failure, EQ→infrastructure_failure, DR→heatwave.

`credibility_score = 0.95` always (official international system).
`urgency_language_score`: Green=0.3, Orange=0.7, Red=0.95.

If both JSON API and RSS fail: return empty list, log warning.

---

## FILE 9: agents/signal_fusion/tools/credibility_scorer.py

Build `apply_scoring_adjustments(signals: list[Signal]) -> list[Signal]`

This runs AFTER all tools have produced their signals. Apply these adjustments to each signal's `credibility_score`:

```python
def apply_scoring_adjustments(signals: list[Signal]) -> list[Signal]:
    # 1. Geolocation confidence boost
    for signal in signals:
        if signal.location.geolocation_confidence > 0.85:
            signal.credibility_score = min(signal.credibility_score + 0.10, 1.0)

    # 2. Mention velocity boost (social signals only)
    for signal in signals:
        if signal.source_type == "social":
            velocity_boost = min(signal.mention_velocity / 20, 1.0) * 0.05
            signal.credibility_score = min(signal.credibility_score + velocity_boost, 1.0)

    # 3. Staleness penalty
    now = datetime.utcnow()
    for signal in signals:
        age_minutes = (now - signal.timestamp).total_seconds() / 60
        if age_minutes > 30:
            signal.credibility_score = max(signal.credibility_score - 0.15, 0.0)

    # 4. Contradiction detection
    # Rule: if a high-credibility source (>0.85) has NO precipitation signal
    #       but social signals mention flooding, flag those social signals
    weather_signals = [s for s in signals if s.source_type == "weather"]
    social_signals = [s for s in signals if s.source_type == "social"]
    
    for weather in weather_signals:
        if weather.credibility_score > 0.85:
            precip = weather.raw_payload.get("precipitation_probability_pct", 100)
            if precip < 20:  # Weather says no rain
                flood_keywords = ["flood", "flooding", "water", "sewer"]
                for social in social_signals:
                    text = str(social.raw_payload.get("text", "")).lower()
                    if any(kw in text for kw in flood_keywords):
                        social.contradiction_flag = True
                        social.credibility_score = max(social.credibility_score - 0.20, 0.0)

    # 5. Corroboration boost
    # If 3+ sources of different types agree (have similar urgency scores > 0.5),
    # boost all their credibility by 0.05
    high_urgency = [s for s in signals if s.urgency_language_score > 0.5]
    source_types_agreeing = set(s.source_type for s in high_urgency)
    if len(source_types_agreeing) >= 3:
        for signal in high_urgency:
            signal.credibility_score = min(signal.credibility_score + 0.05, 1.0)

    return signals
```

---

## FILE 10: agents/signal_fusion/agent.py

Build the Signal Fusion Agent using Google ADK 2.0.

```python
from google.adk.agents import Agent
from google.adk.tools import FunctionTool
from google.genai import types
import os
from dotenv import load_dotenv

load_dotenv()

# Import all tools
from agents.signal_fusion.tools.weather_tool import get_weather_signal
from agents.signal_fusion.tools.traffic_tool import get_traffic_signal
from agents.signal_fusion.tools.social_tool import get_social_signals
from agents.signal_fusion.tools.gdacs_tool import get_gdacs_signals
from agents.signal_fusion.tools.credibility_scorer import apply_scoring_adjustments
from services.firestore_service import FirestoreService
from services.pubsub_service import PubSubService

firestore = FirestoreService()
pubsub = PubSubService()

AGENT_INSTRUCTION = """
You are the Signal Fusion Agent for CIRO — Crisis Intelligence & Response Orchestrator.
Your job is to collect signals from all available sources for a given location,
normalize them, score their credibility, detect contradictions, and produce a
fused signal set ready for crisis detection.

When called with a location (lat, lng, area_name, search_query):
1. Call get_weather_signal to get current conditions and forecast
2. Call get_traffic_signal to get congestion data  
3. Call get_social_signals to get citizen reports from Twitter/X
4. Call get_gdacs_signals to get official disaster alerts
5. Apply credibility scoring and contradiction detection across all signals
6. Write each signal to Firestore and publish to Pub/Sub
7. Return a structured summary: total signals collected, source breakdown,
   highest credibility score, contradiction flags raised, and degraded_mode status

Always collect from ALL sources even if one fails. Never abort early.
Report which sources succeeded and which fell back to alternatives.
"""

signal_fusion_agent = Agent(
    name="signal_fusion_agent",
    model="gemini-2.5-flash",
    instruction=AGENT_INSTRUCTION,
    tools=[
        get_weather_signal,
        get_traffic_signal,
        get_social_signals,
        get_gdacs_signals,
    ],
    generate_content_config=types.GenerateContentConfig(
        temperature=0.1,  # Low temperature — this is analytical, not creative
    )
)
```

The agent takes this input structure when invoked:
```json
{
  "lat": 33.6844,
  "lng": 73.0479,
  "area_name": "G-10 Islamabad",
  "search_query": "flood flooding water G-10 Islamabad",
  "trigger_reason": "weather_threshold_exceeded | scheduled_poll | manual"
}
```

And produces this output written to Firestore `fusion_results/{run_id}`:
```json
{
  "run_id": "uuid",
  "location": {...},
  "signal_ids": ["uuid1", "uuid2", ...],
  "source_summary": {
    "weather": {"source_used": "google_weather", "degraded": false},
    "traffic": {"source_used": "maps_traffic", "degraded": false},
    "social": {"count": 12, "source_used": "apify_twitter", "degraded": false},
    "gdacs": {"count": 0, "degraded": false}
  },
  "contradiction_flags_raised": 1,
  "highest_credibility": 0.95,
  "overall_degraded_mode": false,
  "completed_at": "ISO8601"
}
```

---

## FILE 11: ingestion/signal_collector.py

Build the main ingestion loop that runs continuously and triggers the signal fusion agent.

```python
# signal_collector.py
# Runs as: python -m ingestion.signal_collector

import asyncio
import os
from datetime import datetime

# Monitored zones in Islamabad — extend as needed
MONITORED_ZONES = [
    {"area_name": "G-10", "lat": 33.6844, "lng": 73.0479,
     "search_query": "flood water rain G-10 Islamabad emergency"},
    {"area_name": "G-11", "lat": 33.6956, "lng": 73.0286,
     "search_query": "flood water rain G-11 Islamabad emergency"},
    {"area_name": "I-10", "lat": 33.6650, "lng": 73.0850,
     "search_query": "heat emergency hospital I-10 Islamabad"},
    {"area_name": "F-10", "lat": 33.7100, "lng": 73.0200,
     "search_query": "accident fire infrastructure F-10 Islamabad"},
]

POLL_INTERVAL_SECONDS = 120  # Poll every 2 minutes

async def run_collection_cycle():
    """Run one full collection cycle across all monitored zones."""
    from agents.signal_fusion.agent import signal_fusion_agent
    from google.adk.runners import Runner
    from google.adk.sessions import InMemorySessionService

    session_service = InMemorySessionService()
    runner = Runner(
        agent=signal_fusion_agent,
        app_name="ciro_ingestion",
        session_service=session_service
    )

    for zone in MONITORED_ZONES:
        print(f"[{datetime.utcnow().isoformat()}] Collecting signals for {zone['area_name']}")
        try:
            # Run agent for this zone
            session = await session_service.create_session(
                app_name="ciro_ingestion",
                user_id="collector"
            )
            result = await runner.run_async(
                user_id="collector",
                session_id=session.id,
                new_message=types.Content(
                    role="user",
                    parts=[types.Part(text=str(zone))]
                )
            )
            print(f"  ✓ {zone['area_name']}: {result}")
        except Exception as e:
            print(f"  ✗ {zone['area_name']} failed: {e}")
            # Log to Firestore system_events
            continue

async def main():
    print("CIRO Signal Collector starting...")
    while True:
        await run_collection_cycle()
        print(f"Cycle complete. Sleeping {POLL_INTERVAL_SECONDS}s...")
        await asyncio.sleep(POLL_INTERVAL_SECONDS)

if __name__ == "__main__":
    asyncio.run(main())
```

---

## FILE 12: ingestion/mock_social_stream.py

This is used for TWO purposes only:
1. As fallback when Apify is rate-limited
2. As the demo scenario injector

```python
# mock_social_stream.py
# NOT a replacement for real data. Used only for fallback and demo.

import asyncio
import uuid
from datetime import datetime, timedelta
import random
from schemas.signal import Signal, SignalLocation

MOCK_SCENARIOS = {
    "flood_g10": [
        # (delay_seconds, text, urgency, lat_offset, lng_offset, followers)
        (0,   "Heavy rain in G-10 sector, roads getting slippery #Islamabad", 0.3, 0.001, -0.002, 850),
        (30,  "Water logging on Margalla Road G-10, avoid if possible", 0.5, 0.002, 0.001, 2300),
        (60,  "G-10 Margalla road completely flooded! Cars stuck. @islamabadpolice", 0.8, -0.001, 0.002, 12000),
        (90,  "HELP needed G-10/3 house flooding water entering, calling 1122", 0.95, 0.003, -0.001, 340),
        (120, "Rescue teams needed G-10 sector multiple families stranded #NDMA", 0.9, -0.002, 0.003, 45000),
        (150, "My neighbor says pipe burst on main G-10 road, not flooding?", 0.3, 0.001, 0.001, 280),
        (180, "UPDATE: Both flooding AND pipe burst confirmed G-10 @CDA_Official", 0.85, 0.0, 0.0, 98000),
    ],
    "heat_i10": [
        (0,   "Extreme heat in I-10 today, multiple people feeling unwell", 0.5, 0.001, 0.001, 1200),
        (60,  "3 elderly people collapsed due to heat in I-10 market area", 0.85, -0.001, 0.002, 8900),
        (120, "PIMS hospital reporting increased heat stroke patients from I-10", 0.9, 0.0, 0.0, 125000),
    ]
}

def generate_signals(
    search_query: str,
    location_name: str,
    count: int = 10,
    scenario_key: str = None
) -> list[Signal]:
    """Generate mock social signals as fallback when Apify is unavailable."""
    
    # Determine which scenario based on keywords
    if scenario_key is None:
        if any(w in search_query.lower() for w in ["flood", "water", "rain"]):
            scenario_key = "flood_g10"
        elif any(w in search_query.lower() for w in ["heat", "temperature"]):
            scenario_key = "heat_i10"
        else:
            scenario_key = "flood_g10"

    base_coords = {
        "G-10 Islamabad": (33.6844, 73.0479),
        "I-10 Islamabad": (33.6650, 73.0850),
    }
    base_lat, base_lng = base_coords.get(location_name, (33.6844, 73.0479))
    
    signals = []
    scenario = MOCK_SCENARIOS.get(scenario_key, MOCK_SCENARIOS["flood_g10"])
    
    for i, (delay, text, urgency, dlat, dlng, followers) in enumerate(scenario[:count]):
        signals.append(Signal(
            source_type="social",
            source_name="mock_social",
            timestamp=datetime.utcnow() - timedelta(seconds=random.randint(0, 600)),
            location=SignalLocation(
                lat=base_lat + dlat,
                lng=base_lng + dlng,
                area_name=location_name,
                geolocation_confidence=0.65,
                radius_m=500
            ),
            raw_payload={
                "text": text,
                "followers_count": followers,
                "retweet_count": random.randint(0, 50),
                "like_count": random.randint(0, 200),
                "is_mock": True
            },
            credibility_score=min(0.25 + (followers / 200000), 0.65),
            urgency_language_score=urgency,
            mention_velocity=len(scenario),
            degraded_mode=True  # Always mark mock data as degraded
        ))
    
    return signals


async def inject_demo_scenario(scenario_key: str, location_name: str):
    """
    Inject a demo scenario with realistic timing for live demos.
    Writes directly to Firestore + publishes to Pub/Sub.
    Call this from demo/run_demo_scenario.py
    """
    from services.firestore_service import FirestoreService
    from services.pubsub_service import PubSubService
    
    firestore = FirestoreService()
    pubsub = PubSubService()
    scenario = MOCK_SCENARIOS.get(scenario_key, [])
    
    base_lat, base_lng = (33.6844, 73.0479) if "g10" in scenario_key else (33.6650, 73.0850)
    
    for delay, text, urgency, dlat, dlng, followers in scenario:
        await asyncio.sleep(delay if delay == 0 else 15)  # Accelerated for demo
        
        signal = Signal(
            source_type="social",
            source_name="mock_social",
            timestamp=datetime.utcnow(),
            location=SignalLocation(
                lat=base_lat + dlat,
                lng=base_lng + dlng,
                area_name=location_name,
                geolocation_confidence=0.65
            ),
            raw_payload={"text": text, "followers_count": followers, "is_mock": True},
            credibility_score=min(0.25 + (followers / 200000), 0.65),
            urgency_language_score=urgency,
            mention_velocity=len(scenario),
            degraded_mode=True
        )
        
        await firestore.write_signal(signal)
        await pubsub.publish_signal(signal)
        print(f"  [DEMO] Injected: {text[:60]}...")
```

---

## FILE 13: ingestion/seed_resources.py

Seed Firestore `resources` collection with realistic Islamabad emergency resources. Run once before demo.

Use this exact resource data (real Islamabad coordinates and unit names):

```python
RESOURCES = [
    # Ambulances - Rescue 15
    {"type": "ambulance", "unit_name": "Rescue-15-A1", "lat": 33.6938, "lng": 73.0651, "name": "Rescue 15 HQ Sector G-8"},
    {"type": "ambulance", "unit_name": "Rescue-15-A2", "lat": 33.7180, "lng": 73.0580, "name": "Rescue 15 Sub-Station F-6"},
    {"type": "ambulance", "unit_name": "Rescue-15-A3", "lat": 33.6720, "lng": 73.0820, "name": "Rescue 15 Sub-Station I-8"},
    {"type": "ambulance", "unit_name": "Rescue-15-A4", "lat": 33.7050, "lng": 73.0320, "name": "Rescue 15 Sub-Station G-11"},
    {"type": "ambulance", "unit_name": "PIMS-AMB-1",   "lat": 33.7215, "lng": 73.0433, "name": "PIMS Hospital"},
    {"type": "ambulance", "unit_name": "PIMS-AMB-2",   "lat": 33.7215, "lng": 73.0433, "name": "PIMS Hospital"},
    {"type": "ambulance", "unit_name": "POLY-AMB-1",   "lat": 33.7177, "lng": 73.0691, "name": "Polyclinic Hospital"},
    {"type": "ambulance", "unit_name": "POLY-AMB-2",   "lat": 33.7177, "lng": 73.0691, "name": "Polyclinic Hospital"},
    # Police Traffic Units
    {"type": "police_unit", "unit_name": "Traffic-G10", "lat": 33.6844, "lng": 73.0479, "name": "G-10 Police Station"},
    {"type": "police_unit", "unit_name": "Traffic-G6",  "lat": 33.7120, "lng": 73.0590, "name": "G-6 Police Station"},
    {"type": "police_unit", "unit_name": "Traffic-I8",  "lat": 33.6700, "lng": 73.0780, "name": "I-8 Police Station"},
    {"type": "police_unit", "unit_name": "Traffic-F10", "lat": 33.7100, "lng": 73.0200, "name": "F-10 Police Station"},
    {"type": "police_unit", "unit_name": "Traffic-CDA", "lat": 33.7380, "lng": 73.0850, "name": "CDA Headquarters"},
    {"type": "police_unit", "unit_name": "Traffic-ICT", "lat": 33.7295, "lng": 73.0931, "name": "ICT Police HQ"},
    # NDMA Rescue Teams
    {"type": "rescue_team", "unit_name": "NDMA-RT-1", "lat": 33.7215, "lng": 73.0433, "name": "NDMA HQ"},
    {"type": "rescue_team", "unit_name": "NDMA-RT-2", "lat": 33.7215, "lng": 73.0433, "name": "NDMA HQ"},
    {"type": "rescue_team", "unit_name": "CDA-RT-1",  "lat": 33.7380, "lng": 73.0850, "name": "CDA Emergency Cell"},
    {"type": "rescue_team", "unit_name": "CDA-RT-2",  "lat": 33.7380, "lng": 73.0850, "name": "CDA Emergency Cell"},
    # Water Tankers - CDA
    {"type": "water_tanker", "unit_name": "CDA-WT-1", "lat": 33.7350, "lng": 73.0800, "name": "CDA Water Depot Sector H-8"},
    {"type": "water_tanker", "unit_name": "CDA-WT-2", "lat": 33.6500, "lng": 73.1050, "name": "CDA Water Depot I-14"},
    {"type": "water_tanker", "unit_name": "CDA-WT-3", "lat": 33.7600, "lng": 73.0600, "name": "CDA Water Depot E-7"},
    # Field Verification Teams
    {"type": "field_team", "unit_name": "FT-1", "lat": 33.7000, "lng": 73.0600, "name": "Mobile Unit Alpha"},
    {"type": "field_team", "unit_name": "FT-2", "lat": 33.6900, "lng": 73.0700, "name": "Mobile Unit Bravo"},
    # Shelters
    {"type": "shelter", "unit_name": "Shelter-G10-Mosque",  "lat": 33.6860, "lng": 73.0490, "name": "Jamia Masjid G-10/3", "capacity": 300},
    {"type": "shelter", "unit_name": "Shelter-I10-School",  "lat": 33.6660, "lng": 73.0840, "name": "Govt School I-10/1",  "capacity": 500},
    {"type": "shelter", "unit_name": "Shelter-F10-School",  "lat": 33.7090, "lng": 73.0210, "name": "Islamabad Model School F-10", "capacity": 400},
    # Generators
    {"type": "generator", "unit_name": "GEN-1", "lat": 33.7380, "lng": 73.0850, "name": "CDA Utility Depot"},
    {"type": "generator", "unit_name": "GEN-2", "lat": 33.7380, "lng": 73.0850, "name": "CDA Utility Depot"},
    {"type": "generator", "unit_name": "GEN-3", "lat": 33.7215, "lng": 73.0433, "name": "NDMA Equipment Store"},
]
```

For each resource, write to Firestore `resources/{resource_id}` with:
- `state = "AVAILABLE"`
- `assigned_incident_id = null`
- `eta_minutes = null`
- `last_updated = now`

Print confirmation after each write.

---

## HOW TO TEST YOUR MODULE BEFORE HANDOFF

```bash
# Activate venv
source .venv/bin/activate

# Test weather tool directly
python -c "
import asyncio
from agents.signal_fusion.tools.weather_tool import get_weather_signal
signal = asyncio.run(get_weather_signal(33.6844, 73.0479, 'G-10 Islamabad'))
print(signal.model_dump_json(indent=2))
"

# Test social tool
python -c "
import asyncio
from agents.signal_fusion.tools.social_tool import get_social_signals
signals = asyncio.run(get_social_signals('flood G-10', 'G-10 Islamabad', max_results=5))
for s in signals: print(s.source_name, s.credibility_score, s.urgency_language_score)
"

# Run full agent locally
adk web agents/signal_fusion

# Seed resources (run once)
python -m ingestion.seed_resources

# Start full collection loop
python -m ingestion.signal_collector
```

After running, verify in Firebase Console → Firestore:
- `signals` collection has documents
- `resources` collection has 28 documents
- Each signal has all required fields (no null where not expected)

---

## HANDOFF CHECKLIST TO PERSON 2

Before handing off, confirm:
- [ ] `signals` collection in Firestore has test documents with all schema fields populated
- [ ] `resources` collection seeded with 28 resources, all state=AVAILABLE
- [ ] `signal_collector.py` runs for 5 minutes without crashing
- [ ] Weather tool falls back correctly: test by temporarily setting wrong API key
- [ ] Social tool falls back to mock when Apify token is invalid
- [ ] Tell Person 2: Pub/Sub subscription name is `ciro-signals-sub`
- [ ] Tell Person 2: Unprocessed signals query is `processed == False, order by created_at DESC`
- [ ] Share your `.env` values (except private keys — just confirm which vars are set)