# CIRO — Person 3: Orchestrator + Stakeholder Notifications + Kotlin Android App
## Antigravity Prompt

---

> **Your dependency:** You need Person 1 and Person 2 to hand off before wiring the orchestrator. You can build the Kotlin app and notification templates in parallel while they work. What you need from them:
> - Person 1: `schemas/signal.py`, `services/firestore_service.py`, `services/pubsub_service.py`
> - Person 2: `schemas/incident.py`, `schemas/resource.py`, their agent files importable as sub-agents, Pub/Sub `ciro-incidents` topic publishing state changes
>
> Start with the Kotlin app and notification templates. Wire the orchestrator last.

---

## CONTEXT

You own three things:
1. **Agent 0 — Orchestrator:** The Manager Agent that listens to Pub/Sub, decides which sub-agents to invoke, enforces the confidence threshold routing logic, manages the full pipeline, and generates Antigravity traces visible to judges
2. **Agent 5 — Stakeholder Notification & Action Simulation:** Generates tailored messages per stakeholder type, simulates response actions with before/after states, handles staged public alerting, and manages the retraction flow
3. **Kotlin Android App:** Real-time incident dashboard, Google Maps overlay, push notifications, role-based views for admin/responder/public user

You also write the demo scenario runner and the README.

---

## ENVIRONMENT

```
Same .env as Person 1 and 2:
GOOGLE_API_KEY=<gemini key>
GOOGLE_MAPS_API_KEY=<maps key>
GOOGLE_CLOUD_PROJECT=ciro-hackathon-2025
FIREBASE_CREDENTIALS_PATH=./firebase-admin-sdk.json

Firestore collections you READ: incidents, resources, signals, agent_traces
Firestore collections you WRITE: notifications, metrics, agent_traces, system_events
Pub/Sub you SUBSCRIBE: ciro-incidents-sub (Person 2 publishes here on state changes)
Pub/Sub you SUBSCRIBE: ciro-signals-sub (Person 1 publishes here on new signals)
```

---

## PROJECT STRUCTURE TO CREATE

```
ciro/
├── agents/
│   ├── orchestrator/
│   │   ├── __init__.py
│   │   └── agent.py                    # Agent 0: Orchestrator Manager Agent
│   └── stakeholder_notification/
│       ├── __init__.py
│       ├── agent.py                    # Agent 5: Notification + Action Simulation
│       └── templates/
│           ├── public_alert.py
│           ├── emergency_services.py
│           ├── hospital.py
│           ├── utility.py
│           ├── transport.py
│           ├── media_command.py
│           └── retraction.py
│
├── services/
│   ├── fcm_service.py                  # Firebase Cloud Messaging push notifications
│   └── metrics_service.py             # Pipeline latency + cost tracking
│
├── main.py                             # FastAPI + ADK server entrypoint
│
├── demo/
│   └── run_demo_scenario.py            # Full demo orchestration script
│
└── android/
    └── app/
        ├── build.gradle.kts
        ├── google-services.json        # (from Firebase Console, already downloaded)
        └── src/main/
            ├── AndroidManifest.xml
            └── java/com/ciro/app/
                ├── CiroApplication.kt
                ├── data/
                │   ├── model/
                │   │   ├── Incident.kt
                │   │   ├── Resource.kt
                │   │   └── CiroNotification.kt
                │   └── repository/
                │       ├── IncidentRepository.kt
                │       ├── ResourceRepository.kt
                │       └── NotificationRepository.kt
                ├── viewmodel/
                │   ├── DashboardViewModel.kt
                │   ├── IncidentViewModel.kt
                │   └── MapViewModel.kt
                ├── ui/
                │   ├── theme/
                │   │   └── Theme.kt
                │   ├── dashboard/
                │   │   └── DashboardScreen.kt
                │   ├── map/
                │   │   └── MapScreen.kt
                │   ├── incidents/
                │   │   ├── IncidentListScreen.kt
                │   │   └── IncidentDetailScreen.kt
                │   └── notifications/
                │       └── NotificationsScreen.kt
                └── service/
                    └── CiroFirebaseMessagingService.kt
```

---

## AGENT 0: ORCHESTRATOR — FULL SPECIFICATION

### agents/orchestrator/agent.py

The Orchestrator is a Coordinator Agent using ADK 2.0. It does NOT do any LLM reasoning itself — it routes work to sub-agents based on rules. Use ADK's `SequentialAgent` or implement a `LlmAgent` with routing tools.

**Architecture:** ADK LlmAgent with Gemini 2.5 Flash. The agent receives events from Pub/Sub and decides which sub-agents to invoke using function tools.

```python
from google.adk.agents import Agent
from google.adk.runners import Runner
from google.adk.sessions import InMemorySessionService
from google.genai import types
from google.cloud import pubsub_v1
import asyncio, json, os
from dotenv import load_dotenv

load_dotenv()

ORCHESTRATOR_INSTRUCTION = """
You are the Orchestrator for CIRO — Crisis Intelligence & Response Orchestrator.
You coordinate all sub-agents based on incoming signals and incident state changes.

When you receive a new signal batch event:
1. Invoke signal_fusion_agent with the location data from the event
2. Check the fusion result for contradiction_flags

When you receive an incident state change event:
- MONITORING: Log only. No sub-agent invocation.
- HYPOTHESIS: Invoke severity_prediction_agent for preliminary forecast.
  Dispatch field verification team (shadow-commit a field_team resource).
  Do NOT invoke notification agent yet.
- VERIFICATION_REQUESTED: Invoke severity_prediction_agent + resource_allocation_agent.
  Send internal alert to emergency_services only via notification_agent.
- CONFIRMED: Invoke severity_prediction_agent + resource_allocation_agent + notification_agent.
  Send all stakeholder notifications. Use staged alerting for public.
- RETRACTED: Invoke notification_agent with retraction=True.
  Release all resources. Create audit log. Write retraction trace.

At every step, write a structured agent_trace to Firestore.
Track pipeline latency from first signal to each milestone.
Log all decisions with reasoning so judges can see the Antigravity traces.
"""
```

**Tools the Orchestrator calls (implement as ADK FunctionTools):**

```python
async def invoke_signal_fusion(lat: float, lng: float, area_name: str, search_query: str) -> dict:
    """Invoke Agent 1 and return fusion result summary."""

async def invoke_crisis_detection(signal_ids: list[str], location: dict) -> dict:
    """Invoke Agent 2 and return incident classification."""

async def invoke_severity_prediction(incident_id: str) -> dict:
    """Invoke Agent 3 and return severity forecast."""

async def invoke_resource_allocation(incident_ids: list[str]) -> dict:
    """Invoke Agent 4 and return allocation plan + trade-off narrative."""

async def invoke_stakeholder_notification(incident_id: str, retraction: bool = False) -> dict:
    """Invoke Agent 5 and return notification summary."""

async def get_active_incidents_summary() -> dict:
    """Read Firestore active incidents. Used for context in multi-incident routing."""

async def write_orchestrator_trace(trace: dict) -> str:
    """Write full pipeline trace to Firestore agent_traces collection."""

async def track_pipeline_metrics(run_id: str, milestones: dict) -> None:
    """Write latency metrics to Firestore metrics collection."""
```

**Pub/Sub listener loop (runs in main.py):**

```python
def start_pubsub_listeners():
    """
    Start two background listeners:
    1. ciro-signals-sub: triggers signal fusion when new signals arrive
    2. ciro-incidents-sub: triggers orchestration when incident state changes
    """
    subscriber = pubsub_v1.SubscriberClient()
    
    signals_sub = subscriber.subscription_path(
        os.getenv("GOOGLE_CLOUD_PROJECT"), "ciro-signals-sub"
    )
    incidents_sub = subscriber.subscription_path(
        os.getenv("GOOGLE_CLOUD_PROJECT"), "ciro-incidents-sub"
    )
    
    def handle_signal_message(message):
        data = json.loads(message.data.decode("utf-8"))
        # Trigger orchestrator run for this location
        asyncio.create_task(run_orchestrator_for_signal(data))
        message.ack()
    
    def handle_incident_message(message):
        data = json.loads(message.data.decode("utf-8"))
        # Trigger orchestrator routing for this state change
        asyncio.create_task(run_orchestrator_for_state_change(data))
        message.ack()
    
    subscriber.subscribe(signals_sub, callback=handle_signal_message)
    subscriber.subscribe(incidents_sub, callback=handle_incident_message)
```

**Full pipeline trace format (write to Firestore `agent_traces` on every run):**

```json
{
  "trace_id": "uuid",
  "pipeline_run_id": "uuid",
  "incident_id": "uuid or null",
  "triggered_by": "new_signal | state_change | manual",
  "timestamp": "ISO8601",
  "stages": [
    {
      "stage": "signal_fusion",
      "agent": "signal_fusion_agent",
      "started_at": "ISO8601",
      "completed_at": "ISO8601",
      "duration_ms": 2100,
      "tool_calls": ["get_weather_signal", "get_traffic_signal", "get_social_signals"],
      "result_summary": "4 signals collected, 1 contradiction flag",
      "status": "success | degraded | failed"
    },
    {
      "stage": "crisis_detection",
      "agent": "crisis_detection_agent",
      "duration_ms": 3400,
      "confidence_score": 0.82,
      "crisis_type": "flood",
      "state_transition": "HYPOTHESIS → CONFIRMED",
      "status": "success"
    },
    {
      "stage": "resource_allocation",
      "agent": "resource_allocation_agent",
      "duration_ms": 2800,
      "resources_allocated": 7,
      "trade_off_triggered": false,
      "status": "success"
    },
    {
      "stage": "stakeholder_notification",
      "agent": "stakeholder_notification_agent",
      "duration_ms": 1900,
      "notifications_sent": 6,
      "staged_alerting": true,
      "status": "success"
    }
  ],
  "total_duration_ms": 10200,
  "routing_decision": "FULL_RESPONSE",
  "fallbacks_triggered": [],
  "false_alarm_recovery": false
}
```

---

## AGENT 5: STAKEHOLDER NOTIFICATION & ACTION SIMULATION

### agents/stakeholder_notification/agent.py

**Agent system instruction:**
```
You are the Stakeholder Notification Agent for CIRO emergency management system
in Islamabad, Pakistan. Given a confirmed incident, generate tailored messages
for each stakeholder type and simulate the response actions with realistic
before/after state modeling.

For each message, be specific:
- Public alerts: Bilingual (English + Urdu), max 160 chars, actionable
- Emergency services: Operational briefing with coordinates and rendezvous point
- Hospital: Patient surge forecast with estimated volume and injury types
- Utility: Specific infrastructure risk with circuit/pipeline identifiers
- Transport: Exact road names with Routes API-validated alternate routes
- Media/command: Full brief with confidence score and uncertainty ranges

For action simulation, use realistic metrics for Islamabad:
- Free-flow speed on arterial roads: 50km/h
- Congested speed: 15-20km/h
- Emergency vehicle response time baseline: 8-12 minutes in Islamabad urban sectors
```

**Tool: generate_stakeholder_messages(incident: dict) -> dict**

Calls Gemini to generate all 6 stakeholder messages simultaneously using a single prompt. Structure the prompt to produce all 6 in one call to minimize latency:

```python
NOTIFICATION_PROMPT_TEMPLATE = """
Generate emergency notifications for the following incident:

Incident Type: {crisis_type}
Severity: {severity_level}/5
Location: {area_name} (lat: {lat}, lng: {lng})
Affected Population: {affected_population}
Expected Duration: {expected_duration_hours} hours
Spread Risk: {spread_risk}
Resources Allocated: {resources_summary}
Confidence Score: {confidence_score}

Generate one message per stakeholder type. Return ONLY a JSON object with these keys:
public_alert, emergency_services, hospital, utility_company, transport_authority, media_command_center

Rules:
- public_alert: bilingual English + Urdu, under 160 chars, start with ⚠️ emoji
- emergency_services: include GPS coordinates {lat},{lng} and rendezvous point
- hospital: include estimated patient count and injury types expected
- utility_company: include specific infrastructure risk for {crisis_type}
- transport_authority: include specific road names and alternate routes
- media_command_center: include confidence score and uncertainty range

For a {crisis_type} in {area_name} Islamabad, use these real road names:
G-10 exits: Kashmir Highway (north), Margalla Road (east), IJP Road (south)
I-10 exits: Murree Road (east), Jinnah Avenue (west), IJP Road (south)
"""
```

**Tool: get_alternate_routes(blocked_road: str, incident_lat: float, incident_lng: float) -> list[str]**

Call Google Maps Routes API to find 3 alternate routes around the incident zone. Return human-readable route descriptions for inclusion in transport authority messages.

**Tool: simulate_response_actions(incident: dict, resources_allocated: list) -> list[dict]**

For each response action, produce a structured simulation. Build these action types based on crisis type:

For FLOOD:
```python
actions = [
    {
        "action_type": "traffic_reroute",
        "before_state": {
            "description": "Margalla Road G-10 open, 3 lanes",
            "metric_value": congestion_index_current,
            "metric_unit": "congestion_index"
        },
        "response_action": "Close eastbound lane Margalla Road G-10, activate variable message signs, redirect via Kashmir Highway",
        "expected_after_state": {
            "description": "Margalla Road G-10 closed eastbound, Kashmir Highway absorbs traffic",
            "metric_value": congestion_index_current * 0.5,
            "metric_unit": "congestion_index"
        },
        "response_time_improvement_minutes": 4.0,
        "resource_cost": "2 police units × 3 hours",
        "possible_side_effects": [
            "Kashmir Highway congestion index increase +0.20",
            "Faizabad interchange secondary bottleneck risk"
        ]
    },
    {
        "action_type": "emergency_dispatch",
        "before_state": {"description": "No rescue presence in G-10", "metric_value": 0, "metric_unit": "rescue_teams_present"},
        "response_action": "Dispatch 2 rescue teams to G-10/3 flood zone with inflatable boats",
        "expected_after_state": {"description": "2 rescue teams on site", "metric_value": 2, "metric_unit": "rescue_teams_present"},
        "response_time_improvement_minutes": 0,
        "resource_cost": "2 rescue teams × 4 hours",
        "possible_side_effects": ["NDMA HQ coverage reduced during deployment"]
    },
    # hospital_prep action
    # utility_escalation action (sewer overflow risk)
]
```

For HEATWAVE: generate ambulance dispatch, cooling center activation, medical outreach actions.
For WATER_MAIN_BURST: generate utility dispatch, road closure, water supply rerouting actions.

Write all actions to Firestore `incidents/{id}/response_actions` subcollection.

**Staged public alerting logic:**

```python
async def send_staged_public_alerts(incident: Incident):
    """
    Zone A = immediate crisis area (within affected_radius_km)
    Zone B = adjacent sectors (1.5× affected_radius_km)
    Zone C = evacuation destination sectors
    
    Send Zone A alert first via FCM topic "public_alerts_zone_a_{sector}"
    Wait 15 minutes (or until Routes API shows Kashmir Highway congestion < 0.6)
    Send Zone B alert — update alternate routes if Zone A evacuation caused congestion
    Wait 10 minutes
    Send Zone C preparedness notice
    """
    await fcm_service.send_to_topic(
        topic=f"public_alerts_{incident.location.area_name.replace(' ', '_').lower()}",
        title=messages["public_alert"]["title"],
        body=messages["public_alert"]["body"],
        data={"incident_id": incident.incident_id, "zone": "A"}
    )
    
    # Write to Firestore notifications collection
    await firestore.write_notification(
        incident_id=incident.incident_id,
        stakeholder_type="public",
        channel="fcm",
        message_title=...,
        message_body=...,
        is_retraction=False
    )
```

**Retraction flow:**

```python
async def send_retraction_notifications(incident_id: str):
    """
    Called when incident state changes to RETRACTED.
    
    1. Load incident from Firestore — get stakeholder_notifications_sent list
    2. For each stakeholder who received an alert, generate retraction message
    3. Public retraction must acknowledge the false alarm explicitly
    4. Send via FCM to same topics
    5. Write retraction notification documents to Firestore
    6. Append to incident audit_log
    """
    public_retraction = """✅ ALERT CANCELLED: Previous {crisis_type} alert for {area_name} 
has been cancelled following field verification. 
{correction_reason}. Area is safe. We apologize for concern caused.
اطلاع منسوخ: {area_name} کا {crisis_type} الرٹ فیلڈ تصدیق کے بعد منسوخ کر دیا گیا ہے۔"""
```

---

## services/fcm_service.py

```python
import firebase_admin
from firebase_admin import messaging
from typing import Optional

class FCMService:
    def __init__(self):
        # firebase_admin already initialized in firestore_service.py
        # No re-initialization needed
        pass
    
    async def send_to_topic(
        self,
        topic: str,
        title: str,
        body: str,
        data: Optional[dict[str, str]] = None
    ) -> str:
        """Send FCM notification to a topic. Returns message_id."""
        message = messaging.Message(
            notification=messaging.Notification(title=title, body=body),
            data=data or {},
            topic=topic,
            android=messaging.AndroidConfig(
                priority="high",
                notification=messaging.AndroidNotification(
                    sound="default",
                    channel_id="ciro_alerts"
                )
            )
        )
        return messaging.send(message)
    
    async def send_to_token(self, token: str, title: str, body: str, data: dict = {}) -> str:
        """Send to specific device token."""
        message = messaging.Message(
            notification=messaging.Notification(title=title, body=body),
            data=data,
            token=token
        )
        return messaging.send(message)
```

FCM Topics to use:
- `public_alerts` — all public users
- `emergency_services` — police, rescue, ambulance responders
- `admin_alerts` — command center and admin users
- `hospital_prep` — hospital staff
- Area-specific: `alerts_g10`, `alerts_i10`, `alerts_f10`, `alerts_g11`

---

## services/metrics_service.py

```python
class MetricsService:
    async def record_pipeline_run(
        self,
        incident_id: str,
        pipeline_run_id: str,
        stage_timings: dict[str, float],  # {"signal_fusion_ms": 2100, ...}
        api_calls: dict[str, int],         # {"google_weather": 2, "gemini": 8, ...}
        fallbacks_triggered: list[str],
        false_positive: bool
    ):
        """Write to Firestore metrics collection."""
        total_ms = sum(stage_timings.values())
        
        metrics = {
            "metric_id": str(uuid.uuid4()),
            "incident_id": incident_id,
            "pipeline_run_id": pipeline_run_id,
            "signal_to_detection_ms": stage_timings.get("signal_fusion_ms", 0) + stage_timings.get("crisis_detection_ms", 0),
            "detection_to_allocation_ms": stage_timings.get("severity_prediction_ms", 0) + stage_timings.get("resource_allocation_ms", 0),
            "allocation_to_notification_ms": stage_timings.get("stakeholder_notification_ms", 0),
            "total_end_to_end_ms": total_ms,
            "api_calls_made": api_calls,
            "fallbacks_triggered": fallbacks_triggered,
            "false_positive": false_positive,
            "recorded_at": datetime.utcnow().isoformat()
        }
        await self.firestore.write_metrics(metrics)
    
    async def get_dashboard_summary(self) -> dict:
        """
        Aggregate metrics for admin dashboard display.
        Returns: avg latency, total incidents today, false positive rate,
        resource utilization rate, API call costs estimate
        """
```

---

## main.py

```python
# FastAPI + ADK server entrypoint
import asyncio
import os
import threading
from contextlib import asynccontextmanager
from fastapi import FastAPI
from google.adk.cli.fast_api import get_fast_api_app
from dotenv import load_dotenv

load_dotenv()

# Import orchestrator agent
from agents.orchestrator.agent import orchestrator_agent, start_pubsub_listeners
from services.firestore_service import FirestoreService
import firebase_admin
from firebase_admin import credentials

# Initialize Firebase Admin SDK once
cred = credentials.Certificate(os.getenv("FIREBASE_CREDENTIALS_PATH"))
firebase_admin.initialize_app(cred)

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Start Pub/Sub listeners in background thread
    listener_thread = threading.Thread(target=start_pubsub_listeners, daemon=True)
    listener_thread.start()
    print("✓ Pub/Sub listeners started")
    yield
    print("Shutting down CIRO...")

# Mount ADK agent app
adk_app = get_fast_api_app(
    agent_dir="agents/orchestrator",
    session_service_uri=None,  # InMemory for hackathon
    allow_origins=["*"]        # CORS open for demo
)

# Add lifespan to handle Pub/Sub startup
adk_app.router.lifespan_context = lifespan

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(adk_app, host="0.0.0.0", port=8080)
```

Run with: `python main.py`

---

## demo/run_demo_scenario.py

```python
"""
Full demo scenario runner. Run this during the demo presentation.
Controls timing so the pipeline runs visibly step by step.
"""
import asyncio
import time
from ingestion.mock_social_stream import inject_demo_scenario

DEMO_STEPS = [
    (0,   "Starting CIRO demo..."),
    (2,   "STEP 1: Injecting weather signal (heavy rain G-10)"),
    (5,   "STEP 2: Starting mock social stream — flooding reports G-10"),
    (20,  "STEP 3: Traffic signal spike detected — watch Firestore incidents"),
    (35,  "STEP 4: Injecting contradicting signal (field report: pipe burst)"),
    (50,  "STEP 5: Crisis Detection Agent running — HYPOTHESIS state expected"),
    (65,  "STEP 6: Field verification dispatched — watch resources collection"),
    (80,  "STEP 7: Injecting field confirmation: BOTH flooding AND pipe burst"),
    (95,  "STEP 8: Crisis CONFIRMED — full response triggered"),
    (100, "STEP 9: Injecting simultaneous heat emergency in I-10"),
    (115, "STEP 10: Resource allocation with TWO active incidents — watch trade-off narrative"),
    (130, "STEP 11: Injecting retraction for I-10 (false alarm — power outage, not heat)"),
    (145, "STEP 12: Retraction flow — watch notifications + resource release"),
]

async def run_demo():
    print("\n" + "="*60)
    print("CIRO DEMO SCENARIO — ISLAMABAD CRISIS SIMULATION")
    print("="*60 + "\n")
    
    start = time.time()
    step_index = 0
    
    for delay, description in DEMO_STEPS:
        while time.time() - start < delay:
            await asyncio.sleep(0.5)
        print(f"\n[T+{delay:03d}s] {description}")
        
        if step_index == 1:
            # Trigger real weather API call via signal collector
            from agents.signal_fusion.tools.weather_tool import get_weather_signal
            from services.firestore_service import FirestoreService
            signal = await get_weather_signal(33.6844, 73.0479, "G-10 Islamabad")
            await FirestoreService().write_signal(signal)
            print(f"         Weather: {signal.raw_payload.get('condition_type', 'N/A')}, precip prob: {signal.raw_payload.get('precipitation_probability_pct')}%")
        
        elif step_index == 2:
            asyncio.create_task(
                inject_demo_scenario("flood_g10", "G-10 Islamabad")
            )
        
        elif step_index == 8:
            asyncio.create_task(
                inject_demo_scenario("heat_i10", "I-10 Islamabad")
            )
        
        step_index += 1
    
    print("\n" + "="*60)
    print("Demo scenario complete. Check Firebase Console for:")
    print("  - incidents collection: 2 incidents (1 confirmed flood, 1 retracted heat)")
    print("  - notifications collection: all stakeholder messages")
    print("  - agent_traces collection: full Antigravity trace logs")
    print("  - metrics collection: pipeline latency breakdown")
    print("="*60)

if __name__ == "__main__":
    asyncio.run(run_demo())
```

---

## KOTLIN ANDROID APP

### android/app/build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
    id("com.google.dagger.hilt.android")
    kotlin("kapt")
}

android {
    namespace = "com.ciro.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ciro.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}

dependencies {
    // Firebase BOM
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")

    // Google Maps Compose
    implementation("com.google.maps.android:maps-compose:4.3.3")
    implementation("com.google.android.gms:play-services-maps:19.0.0")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")

    // Charts for severity forecast
    implementation("com.patrykandpatrick.vico:compose-m3:2.0.0-alpha.20")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
}
```

### android/app/src/main/java/com/ciro/app/data/model/Incident.kt

```kotlin
data class Incident(
    val incidentId: String = "",
    val state: String = "MONITORING",
    val crisisType: String = "",
    val severityLevel: Int = 1,
    val confidenceScore: Double = 0.0,
    val location: IncidentLocation = IncidentLocation(),
    val affectedPopulationEstimate: Int = 0,
    val expectedDurationHours: Double = 0.0,
    val spreadRisk: String = "low",
    val severityForecast: SeverityForecast? = null,
    val resourcesAllocated: List<String> = emptyList(),
    val tradeOffNarrative: String? = null,
    val createdAt: com.google.firebase.Timestamp? = null,
    val updatedAt: com.google.firebase.Timestamp? = null
)

data class IncidentLocation(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val areaName: String = "",
    val affectedRadiusKm: Double = 0.0
)

data class SeverityForecast(
    val tPlus1h: Int = 0,
    val tPlus2h: Int = 0,
    val tPlus6h: Int = 0,
    val uncertaintyRange: Int = 1
)

fun Incident.severityColor(): androidx.compose.ui.graphics.Color {
    return when (severityLevel) {
        5 -> androidx.compose.ui.graphics.Color(0xFFD32F2F)  // Red
        4 -> androidx.compose.ui.graphics.Color(0xFFF57C00)  // Orange
        3 -> androidx.compose.ui.graphics.Color(0xFFF9A825)  // Yellow
        2 -> androidx.compose.ui.graphics.Color(0xFF1976D2)  // Blue
        else -> androidx.compose.ui.graphics.Color(0xFF388E3C) // Green
    }
}

fun Incident.stateLabel(): String = when (state) {
    "MONITORING" -> "Monitoring"
    "HYPOTHESIS" -> "Unverified"
    "VERIFICATION_REQUESTED" -> "Verifying"
    "CONFIRMED" -> "ACTIVE"
    "RETRACTED" -> "False Alarm"
    "RESOLVED" -> "Resolved"
    else -> state
}
```

### android/app/src/main/java/com/ciro/app/data/repository/IncidentRepository.kt

```kotlin
@Singleton
class IncidentRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    fun observeActiveIncidents(): Flow<List<Incident>> = callbackFlow {
        val registration = firestore.collection("incidents")
            .whereIn("state", listOf("MONITORING", "HYPOTHESIS", "VERIFICATION_REQUESTED", "CONFIRMED"))
            .orderBy("severity_level", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val incidents = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Incident::class.java)?.copy(incidentId = doc.id)
                } ?: emptyList()
                trySend(incidents)
            }
        awaitClose { registration.remove() }
    }

    fun observeIncidentById(incidentId: String): Flow<Incident?> = callbackFlow {
        val registration = firestore.collection("incidents")
            .document(incidentId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val incident = snapshot?.toObject(Incident::class.java)
                    ?.copy(incidentId = snapshot.id)
                trySend(incident)
            }
        awaitClose { registration.remove() }
    }

    fun observeAllIncidents(): Flow<List<Incident>> = callbackFlow {
        val registration = firestore.collection("incidents")
            .orderBy("created_at", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val incidents = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Incident::class.java)?.copy(incidentId = doc.id)
                } ?: emptyList()
                trySend(incidents)
            }
        awaitClose { registration.remove() }
    }

    suspend fun getDashboardSummary(): DashboardSummary {
        val activeIncidents = firestore.collection("incidents")
            .whereIn("state", listOf("CONFIRMED", "VERIFICATION_REQUESTED"))
            .get().await()
        
        val metrics = firestore.collection("metrics")
            .orderBy("recorded_at", Query.Direction.DESCENDING)
            .limit(20).get().await()
        
        val avgLatencyMs = metrics.documents
            .mapNotNull { it.getLong("total_end_to_end_ms") }
            .average().toLong()
        
        val resources = firestore.collection("resources")
            .get().await()
        val dispatched = resources.documents.count { it.getString("state") == "DISPATCHED" }
        val total = resources.documents.size
        
        return DashboardSummary(
            activeIncidentCount = activeIncidents.size(),
            avgPipelineLatencyMs = avgLatencyMs,
            resourcesDeployed = dispatched,
            totalResources = total
        )
    }
}

data class DashboardSummary(
    val activeIncidentCount: Int = 0,
    val avgPipelineLatencyMs: Long = 0L,
    val resourcesDeployed: Int = 0,
    val totalResources: Int = 0
)
```

### android/app/src/main/java/com/ciro/app/ui/dashboard/DashboardScreen.kt

Build a Compose screen with:

```kotlin
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onIncidentClick: (String) -> Unit,
    onMapClick: () -> Unit
) {
    val incidents by viewModel.activeIncidents.collectAsStateWithLifecycle()
    val summary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val traces by viewModel.recentTraces.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("CIRO Command Center") }) }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            
            // Metric cards row
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard("Active Crises", summary.activeIncidentCount.toString(), Color.Red, Modifier.weight(1f))
                    MetricCard("Avg Response", "${summary.avgPipelineLatencyMs / 1000}s", Color.Blue, Modifier.weight(1f))
                    MetricCard("Resources", "${summary.resourcesDeployed}/${summary.totalResources}", Color.Orange, Modifier.weight(1f))
                }
            }
            
            // Map button
            item {
                Button(onClick = onMapClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Icon(Icons.Default.Map, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open Incident Map")
                }
            }
            
            // Active incidents list
            item { Text("Active Incidents", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp)) }
            items(incidents, key = { it.incidentId }) { incident ->
                IncidentCard(incident = incident, onClick = { onIncidentClick(incident.incidentId) })
            }
            
            // Antigravity trace log (for judges)
            item { Text("Agent Trace Log", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp)) }
            items(traces) { trace ->
                TraceLogCard(trace = trace)
            }
        }
    }
}

@Composable
fun IncidentCard(incident: Incident, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = incident.severityColor().copy(alpha = 0.1f)),
        border = BorderStroke(2.dp, incident.severityColor())
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(12.dp).background(incident.severityColor(), CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(incident.crisisType.replace("_", " ").uppercase(), style = MaterialTheme.typography.titleSmall)
                Text(incident.location.areaName, style = MaterialTheme.typography.bodySmall)
                Text("Pop: ${incident.affectedPopulationEstimate} · Confidence: ${(incident.confidenceScore * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Chip(
                onClick = {},
                label = { Text(incident.stateLabel()) },
                colors = ChipDefaults.chipColors(containerColor = incident.severityColor().copy(alpha = 0.2f))
            )
        }
    }
}
```

### android/app/src/main/java/com/ciro/app/ui/map/MapScreen.kt

```kotlin
@Composable
fun MapScreen(
    viewModel: MapViewModel = hiltViewModel(),
    onIncidentClick: (String) -> Unit
) {
    val incidents by viewModel.activeIncidents.collectAsStateWithLifecycle()
    val resources by viewModel.resources.collectAsStateWithLifecycle()
    val selectedIncident by viewModel.selectedIncident.collectAsStateWithLifecycle()
    
    val islamabadLatLng = LatLng(33.6844, 73.0479)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(islamabadLatLng, 12f)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(zoomControlsEnabled = true)
        ) {
            // Incident markers + affected radius circles
            incidents.forEach { incident ->
                val position = LatLng(incident.location.lat, incident.location.lng)
                
                // Affected radius circle
                Circle(
                    center = position,
                    radius = incident.location.affectedRadiusKm * 1000,
                    fillColor = incident.severityColor().copy(alpha = 0.15f),
                    strokeColor = incident.severityColor().copy(alpha = 0.8f),
                    strokeWidth = 2f
                )
                
                // Incident marker
                Marker(
                    state = MarkerState(position = position),
                    title = "${incident.crisisType} - ${incident.location.areaName}",
                    snippet = "Severity ${incident.severityLevel} · ${incident.stateLabel()}",
                    onClick = { onIncidentClick(incident.incidentId); false }
                )
            }
            
            // Resource markers
            resources.forEach { resource ->
                if (resource.currentLocation.lat != 0.0) {
                    Marker(
                        state = MarkerState(LatLng(resource.currentLocation.lat, resource.currentLocation.lng)),
                        title = resource.unitName,
                        snippet = resource.state
                    )
                }
            }
        }
        
        // Bottom sheet for selected incident detail
        selectedIncident?.let { incident ->
            IncidentBottomSheet(incident = incident, onDismiss = { viewModel.clearSelection() })
        }
    }
}
```

### android/app/src/main/java/com/ciro/app/ui/incidents/IncidentDetailScreen.kt

Build a detail screen showing:
1. Header: crisis type badge, severity indicator, confidence score
2. Location info with "Open in Maps" button (intent to Google Maps with the incident coordinates)
3. **Severity forecast chart** using Vico library — line chart showing T+now, T+1h, T+2h, T+6h severity levels
4. Affected population estimate
5. Trade-off narrative card (if present) — styled distinctly, explains resource competition
6. Resources allocated: list with unit names and ETAs
7. Response actions: expandable cards with before/after state metrics
8. Stakeholder notifications timeline
9. Audit log: collapsible, shows every state change with timestamp and reason
10. If `state == "RETRACTED"`: show prominent red banner "FALSE ALARM — RETRACTED" at top

```kotlin
@Composable
fun IncidentDetailScreen(
    incidentId: String,
    viewModel: IncidentViewModel = hiltViewModel()
) {
    val incident by viewModel.incident.collectAsStateWithLifecycle()
    
    LaunchedEffect(incidentId) { viewModel.loadIncident(incidentId) }
    
    incident?.let { inc ->
        LazyColumn {
            // FALSE ALARM BANNER
            if (inc.state == "RETRACTED") {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Red),
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Text(
                            "⚠️ FALSE ALARM — THIS ALERT WAS RETRACTED",
                            color = Color.White,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
            
            // Header card, location, severity forecast chart, trade-off narrative,
            // resources, response actions, notifications timeline, audit log...
        }
    }
}
```

### android/app/src/main/java/com/ciro/app/service/CiroFirebaseMessagingService.kt

```kotlin
@AndroidEntryPoint
class CiroFirebaseMessagingService : FirebaseMessagingService() {
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title ?: "CIRO Alert"
        val body = remoteMessage.notification?.body ?: ""
        val incidentId = remoteMessage.data["incident_id"]
        val isRetraction = remoteMessage.data["is_retraction"] == "true"
        
        showNotification(title, body, incidentId, isRetraction)
    }
    
    override fun onNewToken(token: String) {
        // Send token to your backend to associate with user role
        // POST to your FastAPI: /api/register-device-token
    }
    
    private fun showNotification(title: String, body: String, incidentId: String?, isRetraction: Boolean) {
        val channelId = if (isRetraction) "ciro_retractions" else "ciro_alerts"
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            incidentId?.let { putExtra("incident_id", it) }
        }
        
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_alert)
            .setPriority(if (isRetraction) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
            .build()
        
        NotificationManagerCompat.from(this).notify(incidentId.hashCode(), notification)
    }
}
```

### FCM Topic Subscriptions (in CiroApplication.kt)

```kotlin
@HiltAndroidApp
class CiroApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Enable Firestore offline persistence
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        FirebaseFirestore.getInstance().firestoreSettings = settings
        
        // Subscribe to relevant FCM topics based on role
        // For demo: subscribe to all topics
        FirebaseMessaging.getInstance().apply {
            subscribeToTopic("public_alerts")
            subscribeToTopic("admin_alerts")
            subscribeToTopic("alerts_g10")
            subscribeToTopic("alerts_i10")
        }
    }
}
```

---

## ANDROID MANIFEST additions

```xml
<!-- In AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

<application ...>
    <!-- FCM Service -->
    <service
        android:name=".service.CiroFirebaseMessagingService"
        android:exported="false">
        <intent-filter>
            <action android:name="com.google.firebase.MESSAGING_EVENT" />
        </intent-filter>
    </service>
    
    <!-- Google Maps API key -->
    <meta-data
        android:name="com.google.android.geo.API_KEY"
        android:value="${GOOGLE_MAPS_API_KEY}" />
    
    <!-- Notification channels -->
    <meta-data
        android:name="com.google.firebase.messaging.default_notification_channel_id"
        android:value="ciro_alerts" />
</application>
```

---

## HOW TO RUN EVERYTHING TOGETHER

```bash
# Terminal 1: Start backend
source .venv/bin/activate
python main.py
# Server running at http://localhost:8080

# Terminal 2: Start signal collector
source .venv/bin/activate
python -m ingestion.signal_collector

# Terminal 3: Run demo scenario (during presentation)
source .venv/bin/activate
python demo/run_demo_scenario.py

# Android Studio: Open android/ folder, run on device/emulator
# Make sure device is on same network as laptop for local backend
# Or point app to your machine's IP instead of localhost
```

---

## HANDOFF / DEMO CHECKLIST

Before the demo:
- [ ] `python -m ingestion.seed_resources` run once — 28 resources in Firestore
- [ ] `python main.py` runs without errors
- [ ] `python demo/run_demo_scenario.py` completes full scenario
- [ ] Firestore shows: incidents, signals, notifications, agent_traces, metrics collections all populated
- [ ] Android app installs and shows live incident data from Firestore
- [ ] FCM push notification arrives on device when demo scenario triggers CONFIRMED state
- [ ] Map screen shows incident markers with correct radius circles
- [ ] Incident detail shows severity forecast chart and trade-off narrative
- [ ] Retraction flow: false alarm incident shows RED BANNER in app
- [ ] `agent_traces` collection readable — this is what you show judges as "Antigravity traces"

Baseline comparison to mention in README and demo:
- Manual dispatch (current Islamabad system): 5–20 minutes from first signal to resource dispatch
- CIRO target: < 60 seconds end-to-end
- Show the `metrics` collection average `total_end_to_end_ms` on the dashboard