# CIRO — Person 2: Crisis Detection + Severity Prediction + Resource Allocation
## Antigravity Prompt

---

> **Your dependency:** Person 1 must hand off before you start wiring agents. You need:
> - `schemas/signal.py` (the Signal Pydantic model)
> - `services/firestore_service.py` (read_signals, mark_processed)
> - `services/pubsub_service.py` (subscribe_signals)
> - Firestore `signals` collection populated with test data
> - Firestore `resources` collection seeded with 28 resources
>
> You can start writing your agent files before Person 1 finishes — just mock the Firestore reads with local test fixtures. Wire the real reads only after handoff.

---

## CONTEXT

You are building three ADK agents for CIRO:
- **Agent 2:** Crisis Detection & Classification — reads Person 1's signals, classifies the crisis
- **Agent 3:** Severity & Evolution Prediction — predicts how the crisis will evolve over time
- **Agent 4:** Resource Allocation & Optimization — allocates constrained resources across simultaneous crises

Person 3 (Orchestrator) will call your agents as sub-agents. You do not build the orchestrator — you build the agents that the orchestrator invokes. Your output is written to Firestore `incidents` collection. Person 3 reads from there.

---

## ENVIRONMENT

```
Same .env as Person 1:
GOOGLE_API_KEY=<gemini key>
GOOGLE_MAPS_API_KEY=<maps key>
GOOGLE_CLOUD_PROJECT=ciro-hackathon-2025
FIREBASE_CREDENTIALS_PATH=./firebase-admin-sdk.json

Firestore collections you READ: signals, resources
Firestore collections you WRITE: incidents, agent_traces
```

---

## PROJECT STRUCTURE TO CREATE

```
ciro/
├── schemas/
│   ├── incident.py          # Pydantic Incident model — TEAM CONTRACT for Person 3
│   └── resource.py          # Pydantic Resource model
│
├── agents/
│   ├── crisis_detection/
│   │   ├── __init__.py
│   │   └── agent.py         # Agent 2
│   ├── severity_prediction/
│   │   ├── __init__.py
│   │   ├── agent.py         # Agent 3
│   │   └── tools/
│   │       ├── historical_tool.py      # Query past incidents from Firestore
│   │       ├── weather_forecast_tool.py # Google Weather 12h forecast
│   │       ├── vulnerable_pop_tool.py   # Google Places API near crisis zone
│   │       └── congestion_spread_tool.py # Routes API spread prediction
│   └── resource_allocation/
│       ├── __init__.py
│       ├── agent.py          # Agent 4
│       └── tools/
│           ├── travel_time_tool.py     # Google Route Matrix API
│           └── optimizer.py            # Constraint satisfaction allocator
```

---

## FIRESTORE INCIDENT SCHEMA (TEAM CONTRACT — Person 3 reads this)

```python
# schemas/incident.py
from pydantic import BaseModel, Field
from typing import Optional, Any
from datetime import datetime
import uuid

class IncidentLocation(BaseModel):
    lat: float
    lng: float
    area_name: str
    affected_radius_km: float

class ConflictingHypothesis(BaseModel):
    crisis_type: str
    confidence: float
    evidence_signal_ids: list[str]
    evidence_summary: str

class SeverityForecast(BaseModel):
    t_plus_1h: int    # severity level 1-5
    t_plus_2h: int
    t_plus_6h: int
    uncertainty_range: int  # ±N severity levels
    peak_impact_time: Optional[datetime] = None

class ResponseAction(BaseModel):
    action_id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    action_type: str  # "traffic_reroute|emergency_dispatch|hospital_prep|utility_escalation|public_alert"
    before_state: dict[str, Any]
    response_action: str
    expected_after_state: dict[str, Any]
    response_time_improvement_minutes: float
    resource_cost: str
    possible_side_effects: list[str]
    simulated_at: datetime = Field(default_factory=datetime.utcnow)

class AuditEntry(BaseModel):
    timestamp: datetime = Field(default_factory=datetime.utcnow)
    action: str
    from_state: Optional[str] = None
    to_state: Optional[str] = None
    reason: str
    agent: str

class Incident(BaseModel):
    incident_id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    state: str = "MONITORING"  # MONITORING|HYPOTHESIS|VERIFICATION_REQUESTED|CONFIRMED|RETRACTED|RESOLVED
    crisis_type: str   # flood|heatwave|accident|infrastructure_failure|power_outage|protest|disease_cluster|water_main_burst
    severity_level: int  # 1-5
    confidence_score: float
    conflicting_hypothesis: Optional[ConflictingHypothesis] = None
    location: IncidentLocation
    affected_population_estimate: int
    expected_duration_hours: float
    spread_risk: str  # low|medium|high
    severity_forecast: Optional[SeverityForecast] = None
    resources_allocated: list[str] = []         # resource_ids
    stakeholder_notifications_sent: list[str] = []
    signal_ids: list[str] = []
    response_actions: list[ResponseAction] = []
    trade_off_narrative: Optional[str] = None   # filled when 2 incidents compete for resources
    audit_log: list[AuditEntry] = []
    created_at: datetime = Field(default_factory=datetime.utcnow)
    updated_at: datetime = Field(default_factory=datetime.utcnow)
```

```python
# schemas/resource.py
from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime
import uuid

class ResourceLocation(BaseModel):
    lat: float
    lng: float
    name: str

class Resource(BaseModel):
    resource_id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    type: str  # ambulance|police_unit|rescue_team|water_tanker|shelter|generator|field_team
    state: str = "AVAILABLE"  # AVAILABLE|SHADOW_COMMITTED|DISPATCHED|UNAVAILABLE
    current_location: ResourceLocation
    assigned_incident_id: Optional[str] = None
    eta_minutes: Optional[float] = None
    capacity: int = 1
    unit_name: str
    contact: Optional[str] = None
    last_updated: datetime = Field(default_factory=datetime.utcnow)
```

---

## AGENT 2: CRISIS DETECTION & CLASSIFICATION

### agents/crisis_detection/agent.py

The agent reads unprocessed signals from Firestore (written by Person 1), groups signals by geographic proximity (cluster signals within 3km of each other as one incident), then uses Gemini to classify the most likely crisis.

**Input:** Called by the Orchestrator (Person 3) with a list of signal_ids to process, or it self-triggers by polling Firestore for unprocessed signals.

**Gemini structured output — enforce this JSON schema strictly:**
```json
{
  "primary_classification": {
    "crisis_type": "string",
    "severity_level": "integer 1-5",
    "confidence_score": "float 0-1",
    "reasoning": "string — explain exactly which signals drove this classification"
  },
  "conflicting_hypothesis": {
    "crisis_type": "string or null",
    "confidence": "float 0-1",
    "evidence_signal_ids": ["string"],
    "evidence_summary": "string"
  },
  "affected_radius_km": "float",
  "affected_population_estimate": "integer",
  "expected_duration_hours": "float",
  "spread_risk": "low|medium|high",
  "verification_needed": "boolean",
  "recommended_state": "MONITORING|HYPOTHESIS|VERIFICATION_REQUESTED|CONFIRMED"
}
```

**Agent system instruction:**
```
You are a crisis classification specialist for an urban emergency management system
in Islamabad, Pakistan. You receive normalized, credibility-scored signals from
multiple sources. Classify the most likely crisis type with a confidence score.

Classification rules:
- flood: precipitation_probability > 40% AND (social mentions of flooding OR traffic congestion > 0.6 in same area)
- heatwave: temperature > 40°C AND expected_duration > 3h AND affects vulnerable population
- water_main_burst: social mentions of "pipe burst"/"water gushing" WITHOUT significant precipitation signal
- infrastructure_failure: official source OR multiple social posts about structural/power/bridge issues
- accident: traffic congestion spike + social mentions of collision/crash without weather cause

Confidence scoring:
- Start at 0.3 base
- +0.2 for each corroborating source type (max 4 source types = +0.8)
- +0.1 if highest credibility signal > 0.85
- -0.2 for each active contradiction_flag
- -0.1 if only social signals with no official/weather corroboration
- Cap at 0.95 (never be 100% certain without field confirmation)

When two hypotheses are plausible (e.g. flood vs water main burst):
- Output BOTH with separate confidence scores
- Set verification_needed = true
- Recommend HYPOTHESIS state, not CONFIRMED
- Never escalate to full response when hypotheses conflict

Be conservative. A missed real crisis is worse than a delayed response.
A false alarm that wastes resources is also costly. When uncertain, verify first.
```

**After Gemini classification, determine incident state:**
```python
def determine_state(confidence: float, verification_needed: bool) -> str:
    if confidence < 0.4:
        return "MONITORING"
    elif confidence < 0.6 or verification_needed:
        return "HYPOTHESIS"
    elif confidence < 0.8:
        return "VERIFICATION_REQUESTED"
    else:
        return "CONFIRMED"
```

**Check for duplicate incidents:**
Before creating a new incident, query Firestore `incidents` where:
- `state` not in `["RESOLVED", "RETRACTED"]`
- Location within 3km of new classification
- `crisis_type` matches
- `created_at` within last 2 hours

If duplicate found: update existing incident's `signal_ids`, `confidence_score`, and `updated_at` instead of creating new one.

**Write result to Firestore `incidents` collection.**
Mark all processed signal_ids as `processed=True` in Firestore.

---

## AGENT 3: SEVERITY & EVOLUTION PREDICTION

### agents/severity_prediction/tools/historical_tool.py

```python
async def get_historical_incidents(
    lat: float, lng: float, crisis_type: str,
    radius_km: float = 5.0, days_back: int = 30
) -> dict:
    """
    Query Firestore incidents collection for past incidents of same type
    near this location. Returns statistical summary for baseline calibration.
    """
    # Query: crisis_type == crisis_type AND state in [RESOLVED, RETRACTED]
    # AND created_at > (now - days_back days)
    # Filter in Python: only include if geodesic distance < radius_km
    
    # Return:
    # {
    #   "count": int,
    #   "avg_severity": float,
    #   "avg_duration_hours": float,
    #   "avg_affected_population": int,
    #   "recurrence_rate": float,  # incidents per week
    #   "historical_base_probability": float  # 0-1, higher = more likely to be real
    # }
    #
    # If no history: return count=0, historical_base_probability=0.1
```

### agents/severity_prediction/tools/weather_forecast_tool.py

```python
async def get_12h_weather_forecast(lat: float, lng: float) -> dict:
    """
    Google Weather API hourly forecast for next 12 hours.
    Returns precipitation probability per hour and temperature trend.
    Used to predict severity escalation.
    """
    # GET https://weather.googleapis.com/v1/forecast/hours:lookup
    #     ?key={GOOGLE_MAPS_API_KEY}
    #     &location.latitude={lat}
    #     &location.longitude={lng}
    #     &hours=12
    
    # Extract per hour: precipitation_probability_pct, temperature_c, condition_type
    
    # Compute:
    # peak_precip_hour: which hour has highest precipitation probability
    # sustained_heavy_rain: True if >3 consecutive hours with precip_prob > 70%
    # temperature_trend: "rising"|"falling"|"stable"
    # heatwave_risk_score: 0-1 based on temperature and humidity forecast
```

### agents/severity_prediction/tools/vulnerable_pop_tool.py

```python
async def get_vulnerable_facilities(lat: float, lng: float, radius_km: float) -> dict:
    """
    Google Maps Places API (New) to find hospitals, schools, elderly care near crisis.
    Feeds vulnerability scoring.
    """
    # Call Places API (New) Nearby Search for each type:
    # POST https://places.googleapis.com/v1/places:searchNearby
    # Headers: X-Goog-Api-Key, X-Goog-FieldMask: places.displayName,places.location,places.types
    # Body: {
    #   "includedTypes": ["hospital"],
    #   "locationRestriction": {
    #     "circle": {"center": {"latitude": lat, "longitude": lng}, "radius": radius_km * 1000}
    #   }
    # }
    
    # Repeat for: hospital, school, pharmacy, fire_station, police, mosque (shelters)
    
    # Return:
    # {
    #   "hospitals": [{"name": str, "distance_km": float, "lat": float, "lng": float}],
    #   "schools": [...],
    #   "vulnerability_score": float  # 0-1: more hospitals/schools = higher impact if disrupted
    # }
```

### agents/severity_prediction/tools/congestion_spread_tool.py

```python
async def predict_congestion_spread(
    lat: float, lng: float, crisis_type: str, severity_level: int
) -> dict:
    """
    Use Google Route Matrix API to predict which roads saturate over time
    as crisis spreads or evacuation begins.
    """
    # Generate grid of 12 points around the crisis center at 1km spacing
    # Call Routes API Route Matrix: all 12 points → crisis center
    # With TRAFFIC_AWARE_OPTIMAL routing
    
    # POST https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix
    # Headers: X-Goog-Api-Key, X-Goog-FieldMask: originIndex,destinationIndex,duration,condition
    
    # For each route, compute congestion_index (0-1)
    # Identify which routes are already saturated (congestion_index > 0.7)
    # Model spread: routes adjacent to crisis center will saturate T+1h if crisis severity >= 3
    
    # Return:
    # {
    #   "currently_saturated_routes": int,
    #   "predicted_saturated_t1h": int,
    #   "predicted_saturated_t2h": int,
    #   "evacuation_route_available": bool,
    #   "recommended_evacuation_road": str  # name/description of least congested exit route
    # }
```

### agents/severity_prediction/agent.py

**Agent system instruction:**
```
You are a crisis severity prediction specialist. Given a confirmed or hypothesized
crisis type and location in Islamabad, Pakistan, predict how it will evolve over
the next 6 hours. Use all available data: weather forecast, historical incidents,
vulnerable facility proximity, and congestion spread prediction.

Produce a concrete severity forecast for T+1h, T+2h, T+6h with uncertainty range.
Identify the peak impact time. Estimate affected population using facility density
and historical population data.

Be specific about what drives escalation risk:
- For floods: sustained rainfall forecast, drainage capacity (assume Islamabad 
  urban drainage handles 20mm/hr before overflow)
- For heatwaves: sustained temperature > 42°C beyond 3 hours dramatically 
  increases elderly/infant risk
- For infrastructure failures: cascade risk (power → water pumps → hospitals)

Always include: recommended_response_window — how long responders have before
the situation becomes significantly harder to control.
```

**Output structure to write to Firestore `incidents/{id}` (update existing incident):**
```python
severity_forecast = SeverityForecast(
    t_plus_1h=...,
    t_plus_2h=...,
    t_plus_6h=...,
    uncertainty_range=1,
    peak_impact_time=...
)
# Also update:
# affected_population_estimate
# expected_duration_hours
# spread_risk
```

---

## AGENT 4: RESOURCE ALLOCATION & OPTIMIZATION

### agents/resource_allocation/tools/travel_time_tool.py

```python
async def compute_travel_times(
    resource_ids: list[str],
    incident_lat: float,
    incident_lng: float
) -> dict[str, float]:
    """
    For each resource_id, get current location from Firestore,
    then call Google Maps Route Matrix API to get real-time ETA
    to the incident location.
    
    Returns: {resource_id: eta_minutes}
    """
    # 1. Batch-fetch resource documents from Firestore
    # 2. Build Route Matrix request:
    #    origins = resource current locations
    #    destinations = [incident location]
    #    travelMode = DRIVE
    #    routingPreference = TRAFFIC_AWARE_OPTIMAL
    #    departureTime = now (UTC ISO8601)
    
    # POST https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix
    # Headers:
    #   X-Goog-Api-Key: {GOOGLE_MAPS_API_KEY}
    #   X-Goog-FieldMask: originIndex,destinationIndex,duration,distanceMeters
    
    # Parse response: duration field is in seconds, convert to minutes
    # Return dict mapping resource_id to eta_minutes
    
    # Fallback if Routes API fails:
    # Compute straight-line distance using geopy.distance.geodesic
    # Assume 40km/h average speed for emergency vehicles in Islamabad traffic
    # eta_minutes = (distance_km / 40) * 60
    # Mark as degraded estimate in return value
```

### agents/resource_allocation/tools/optimizer.py

```python
def compute_priority_score(incident: Incident, eta_minutes: float) -> float:
    """
    Priority score determines which incident gets resources first
    when two incidents compete.
    
    Formula:
    spread_multiplier = {"low": 1.0, "medium": 1.5, "high": 2.0}
    score = (severity_level * log10(max(affected_population, 10)) 
             * spread_multiplier[spread_risk]) / max(eta_minutes, 1)
    """
    import math
    spread_multiplier = {"low": 1.0, "medium": 1.5, "high": 2.0}
    return (
        incident.severity_level
        * math.log10(max(incident.affected_population_estimate, 10))
        * spread_multiplier.get(incident.spread_risk, 1.0)
    ) / max(eta_minutes, 1.0)


def allocate_resources_optimally(
    active_incidents: list[Incident],
    available_resources: list[Resource],
    travel_times: dict[str, dict[str, float]]  # {incident_id: {resource_id: eta_minutes}}
) -> tuple[dict[str, list[str]], str]:
    """
    Greedy allocation with priority scoring.
    
    Returns:
    - allocation: {incident_id: [resource_ids]}
    - trade_off_narrative: human-readable explanation of any trade-offs made
    
    Rules:
    1. Compute priority_score for each incident (use median ETA across resource types)
    2. Sort incidents by priority_score descending
    3. For each incident, determine required resources by crisis type:
       - flood: 2 rescue_teams, 2 police_units, 1 water_tanker, 2 ambulances
       - heatwave: 3 ambulances, 1 field_team, 1 shelter
       - accident: 2 ambulances, 2 police_units, 1 rescue_team
       - infrastructure_failure: 2 rescue_teams, 1 generator, 2 police_units
       - water_main_burst: 1 water_tanker, 1 field_team, 1 police_unit
    4. Allocate lowest-ETA available resources first
    5. SHADOW_COMMITTED resources: only re-allocate if this incident's 
       priority_score > 1.5× the shadow-committed incident's score
    6. If a required resource type is exhausted, note the shortage in trade_off_narrative
    
    Trade-off narrative format:
    "Incident A [flood G-10, priority 8.2] allocated: 2 rescue teams (ETA 6min, 9min),
    2 police units (ETA 4min, 7min), 1 water tanker (ETA 12min), 2 ambulances (ETA 5min, 8min).
    Incident B [heatwave I-10, priority 5.1] allocated: 2 of 3 required ambulances (ETA 11min, 14min).
    SHORTAGE: 1 ambulance unavailable for Incident B — all remaining units allocated to Incident A.
    Estimated additional risk to Incident B: delayed medical response by ~8 minutes.
    Recommendation: Request mutual aid from adjacent sector."
    """
```

### agents/resource_allocation/agent.py

**Agent system instruction:**
```
You are a resource allocation optimizer for CIRO emergency management system
in Islamabad, Pakistan. You receive one or more active incidents with severity
scores, and a pool of available emergency resources with real-time travel times.

Your job:
1. Compute priority scores for each incident
2. Determine what resources each crisis type requires
3. Allocate optimally given constraints — lower ETA resources go to higher priority incidents
4. When resources are insufficient, produce a clear trade-off narrative explaining
   what shortage exists, which incident is under-resourced, and what the estimated
   impact of that shortage is
5. For incidents in HYPOTHESIS state: set matching resources to SHADOW_COMMITTED,
   do not physically dispatch yet
6. For CONFIRMED incidents: set resources to DISPATCHED, update ETAs

Always update resource state in Firestore atomically after allocation decisions.
Never leave a resource in an ambiguous state.

When only one incident exists: still verify resource sufficiency and flag shortages.
```

**Firestore writes after allocation:**
- Update each `resources/{id}` document: `state`, `assigned_incident_id`, `eta_minutes`, `last_updated`
- Update `incidents/{id}`: `resources_allocated`, `trade_off_narrative`, `updated_at`
- Append to `incidents/{id}.audit_log`: allocation decision entry

---

## FALSE POSITIVE / RETRACTION HANDLING

You own the state machine for incident transitions. Implement this in `agents/crisis_detection/agent.py`:

```python
VALID_TRANSITIONS = {
    "MONITORING": ["HYPOTHESIS", "RESOLVED"],
    "HYPOTHESIS": ["VERIFICATION_REQUESTED", "MONITORING", "RETRACTED"],
    "VERIFICATION_REQUESTED": ["CONFIRMED", "RETRACTED"],
    "CONFIRMED": ["RESOLVED", "RETRACTED"],
    "RETRACTED": [],   # terminal state
    "RESOLVED": []     # terminal state
}

async def transition_incident_state(
    incident_id: str,
    new_state: str,
    reason: str,
    agent_name: str
):
    """
    Validate transition, update Firestore, append audit log entry.
    Publish state change event to ciro-incidents Pub/Sub topic
    so Person 3's orchestrator can trigger notifications.
    """
    incident = await firestore.get_incident(incident_id)
    
    if new_state not in VALID_TRANSITIONS[incident.state]:
        raise ValueError(f"Invalid transition: {incident.state} → {new_state}")
    
    # Special handling for RETRACTED:
    # - Release all DISPATCHED/SHADOW_COMMITTED resources back to AVAILABLE
    # - Set their assigned_incident_id = null
    if new_state == "RETRACTED":
        for resource_id in incident.resources_allocated:
            await firestore.update_resource_state(resource_id, "AVAILABLE", None)
    
    await firestore.update_incident_state(incident_id, new_state, reason, agent_name)
    await pubsub.publish_incident_event(incident_id, new_state, reason)
```

---

## ADD TO services/firestore_service.py (extend Person 1's file)

Add these methods to the existing `FirestoreService` class:

```python
async def write_incident(self, incident: Incident) -> str
    # Write to incidents/{incident_id}, return document ID

async def get_incident(self, incident_id: str) -> Incident
    # Read incidents/{incident_id}, return Incident object

async def update_incident_state(self, incident_id: str, new_state: str, reason: str, agent: str)
    # Update: state, updated_at, append to audit_log

async def get_active_incidents(self) -> list[Incident]
    # Query: state not in [RESOLVED, RETRACTED], order by severity_level DESC

async def get_available_resources(self, resource_type: str = None) -> list[Resource]
    # Query: state == AVAILABLE, optionally filter by type

async def update_resource_state(self, resource_id: str, state: str, incident_id: Optional[str])
    # Atomic update using Firestore transaction: state, assigned_incident_id, last_updated

async def write_agent_trace(self, trace: dict)
    # Write to agent_traces/{trace_id} collection
```

---

## AGENT TRACE FORMAT (write after every agent run)

Write to Firestore `agent_traces/{trace_id}` after every detection/prediction/allocation run:

```json
{
  "trace_id": "uuid",
  "agent": "crisis_detection_agent | severity_prediction_agent | resource_allocation_agent",
  "incident_id": "uuid or null",
  "timestamp": "ISO8601",
  "input_summary": "string — what signals/incidents were processed",
  "tool_calls": [
    {"tool": "tool_name", "duration_ms": 1200, "status": "success|fallback|failed"}
  ],
  "gemini_reasoning": "string — the model's reasoning chain",
  "decision": "string — what was decided and why",
  "confidence_scores": {},
  "state_transition": {"from": "HYPOTHESIS", "to": "CONFIRMED"},
  "trade_off_narrative": "string or null",
  "duration_ms": 4500
}
```

---

## HANDOFF CHECKLIST TO PERSON 3

Before handing off, confirm:
- [ ] `incidents` collection in Firestore has test documents with all schema fields
- [ ] State machine transitions work: test MONITORING → HYPOTHESIS → CONFIRMED → RETRACTED
- [ ] Resource states update correctly in Firestore after allocation
- [ ] `agent_traces` collection has trace documents after each agent run
- [ ] Pub/Sub `ciro-incidents` topic receives events on every state change
- [ ] Person 3 needs to subscribe to `ciro-incidents-sub` subscription
- [ ] Share the `Incident` and `Resource` Pydantic model files
- [ ] Run the two-crisis simultaneous scenario and confirm trade-off narrative appears in Firestore