# barwaqt v2 — Story-Driven Crisis Intelligence Center

Transform barwaqt from a data dashboard into a **narrative command center** that tells the story of what's happening across Pakistan — and what the AI is doing about it in real time.

> [!IMPORTANT]
> Major rewrite of Android UI (all screens) + backend additions. ~5-6 hours. Review the tab structure, UX patterns, and admin panel before I start. The old AnalyticsScreen, ResourceHubScreen, and AIBrainScreen will be deprecated from public view.

---

## Proposed Architecture

### Navigation: 4 Public Tabs + Hidden Admin

```
Splash ──→ Main (4 tabs)
             ├─ Pulse     → Threat gauge, breaking ticker, situation cards
             ├─ Intel     → OSINT signal intelligence, social monitoring
             ├─ Map       → Pakistan map, province drill-down, incident markers
             └─ Response  → Agencies, resource management, active ops
                  │
         (long-press barwaqt logo)
                  │
             Admin Panel  → Simulate, pipeline metrics, agent logs, system health
```

---

## Tab Design (Story-Driven UX)

### Tab 1: PULSE — "The Heartbeat"

**Purpose**: Open the app → instantly know the state of the nation.

**Layout** (no lists — all visual):

```
┌──────────────────────────────────────┐
│  ◀◀  BREAKING: Flood alert in G-10  │  ← Auto-scrolling ticker (marquee)
│      Rescue 1122 dispatched 3 units  │
├──────────────────────────────────────┤
│                                      │
│       ┌──── THREAT LEVEL ────┐       │
│       │    ╭─────────╮       │       │  ← Animated circular gauge
│       │   ╱  ELEVATED ╲      │       │    (green→yellow→orange→red)
│       │  │    ████░░░  │     │       │    Computed from active severity
│       │   ╲   3 / 5   ╱     │       │
│       │    ╰─────────╯       │       │
│       └──────────────────────┘       │
│                                      │
│  ┌─ CITY SNAPSHOT ───────────────┐   │
│  │ 🌡 34°C   🌧 Heavy Rain        │   │  ← Compact situation card
│  │ 👥 2.1M at risk  ⚠ 3 active   │   │    (weather + pop + incidents)
│  │ 🚑 12/18 available             │   │
│  └────────────────────────────────┘   │
│                                      │
│  ◀ [FLOOD G-10] [HEATWAVE I-8] ▶   │  ← Horizontal swipeable incident
│    ┌─────────┐                       │    cards (not a list!)
│    │ 🌊 FLOOD│ Sev 4  │ 12m ago    │    Each card = mini summary
│    │ G-10    │ 85% AI │ 3 units    │    Tap → Incident Detail
│    └─────────┘                       │
│                                      │
│  ─── barwaqt AI ──────────────────   │
│  ● Monitoring 3 signal sources       │  ← Subtle activity indicator
│  ● Last pipeline: 14s ago            │    (pulsing dot when processing)
│  ● 38× faster than manual response   │
└──────────────────────────────────────┘
```

**Key UX decisions**:
- **Threat gauge** (Canvas arc) replaces severity count pills — one glance = national threat level
- **Horizontal card carousel** for incidents instead of vertical list — swipe to browse
- **Situation card** combines weather + population + resource availability in one compact view
- **Activity indicator** is subtle, not a full AI Brain tab

---

### Tab 2: INTEL — "Signal Intelligence Center"

**Purpose**: WorldMonitor-inspired OSINT dashboard. Shows *why* CIRO detected something — the underlying signals.

**Layout**:

```
┌──────────────────────────────────────┐
│  SIGNAL INTELLIGENCE                 │
│  Monitoring 5 sources across Pakistan│
├──────────────────────────────────────┤
│                                      │
│  ┌─ MENTION VELOCITY ────────────┐   │
│  │  ▂▃▅▇█▇▅▃▂▁▂▃▅▇█████         │   │  ← Sparkline graph showing
│  │  ↑ Spike 15 min ago            │   │    crisis-keyword velocity
│  │  "flood" "G-10" "rescue"       │   │    over last 2 hours
│  └────────────────────────────────┘   │
│                                      │
│  ┌─ SENTIMENT ───┐ ┌─ CREDIBILITY ┐  │
│  │   😰 72%       │ │    ★★★★☆     │  │  ← Side-by-side gauges
│  │   Negative     │ │   High (4.2)  │  │    Sentiment = emoji + %
│  │   ↑ from 45%   │ │   3 verified  │  │    Credibility = star rating
│  └────────────────┘ └───────────────┘  │
│                                      │
│  ── LIVE NEWS FEED ──────────────── │
│  ┌────────────────────────────────┐   │
│  │ 🔴 Dawn: "G-10 flooding..."   │   │  ← Real news from GNews API
│  │ 🟡 Tribune: "Heatwave..."     │   │    (Pakistan headlines)
│  │ 🟢 Geo: "Rescue ops underway" │   │    Color = urgency
│  └────────────────────────────────┘   │
│                                      │
│  ── SIGNAL SOURCES ──────────────── │
│  ◀ [Weather ✓] [Traffic ✓] [Social │  ← Horizontal chips showing
│     ✓] [Sensors ⚠] [Field ✓]    ▶  │    source health status
│                                      │
│  ── TRENDING KEYWORDS ───────────── │
│  ┌────────────────────────────────┐   │
│  │ flood(47) rescue(32) G-10(28) │   │  ← Tag cloud / pill layout
│  │ rain(24) blocked(19) alert(15)│   │    Size = frequency
│  └────────────────────────────────┘   │
└──────────────────────────────────────┘
```

**Data sources**:
- **GNews API** (free, `country=pk`, 100 req/day) for real headlines
- **Firestore `signals`** collection for mention velocity + trending keywords
- **AI-generated** sentiment + credibility from the signal_fusion agent
- Backed by new `live_intelligence` Firestore collection

---

### Tab 3: MAP — "Situational Awareness"

Same as before but richer:

```
┌──────────────────────────────────────┐
│ ◀ All Pakistan                       │
│ [All] [Punjab] [Sindh] [KP] [ICT]   │  ← Province filter chips
│  [Balochistan] [GB] [AJK]           │
├──────────────────────────────────────┤
│                                      │
│         ╱╲    Pakistan Map           │
│        ╱  ╲   (Google Maps)          │
│       ╱ ●  ╲                         │  ← Markers on map:
│      ╱  ▲   ╲                        │    ● Circle = Flood
│     ╱ ■  ◆   ╲                       │    ▲ Triangle = Fire/Heatwave
│    ╱__________╲                      │    ■ Square = Accident
│                                      │    ◆ Diamond = Infrastructure
│   🚑 🚔 🏥 (resource overlay)       │    Color = severity level
│                                      │    Size = affected population
├──────────────────────────────────────┤
│  ── LEGEND ──────────────────────── │
│  ● Flood  ▲ Fire  ■ Accident        │  ← Collapsible legend
│  ◆ Infra  ★ Shelter                 │
│  ■ Sev 1  ■ Sev 3  ■ Sev 5         │
│  Toggle: [Incidents] [Resources]     │
└──────────────────────────────────────┘
```

---

### Tab 4: RESPONSE — "Operations Center"

**Purpose**: Agency coordination + resource management. Not a list of agencies — an **operations dashboard**.

**Layout**:

```
┌──────────────────────────────────────┐
│  ACTIVE OPERATIONS                   │
│  3 operations in progress            │
├──────────────────────────────────────┤
│                                      │
│  ── OPERATIONS TIMELINE ──────────  │
│  ┌──────────────────────────────┐    │
│  │  ●──── Flood G-10 ──────●   │    │  ← Vertical timeline
│  │  │ 14:20  Signal detected    │    │    (not a list!)
│  │  │ 14:22  AI confirmed       │    │    Shows lifecycle of each
│  │  │ 14:23  Resources deployed │    │    active operation
│  │  │ 14:25  Alerts sent        │    │
│  │  ○  In progress...           │    │
│  └──────────────────────────────┘    │
│                                      │
│  ── RESPONDING AGENCIES ─────────── │
│  ◀ ┌─────────┐ ┌─────────┐       ▶ │  ← Horizontal swipeable
│    │ 🚑       │ │ 🚔       │        │    agency cards
│    │ Rescue   │ │ Police   │        │    Tap → Agency Profile
│    │ 1122     │ │ ICT      │        │
│    │ 4 units  │ │ 2 units  │        │
│    │ deployed │ │ deployed │        │
│    └─────────┘ └─────────┘        │
│                                      │
│  ── RESOURCE READINESS ──────────── │
│  ┌────────────────────────────────┐  │
│  │ ███████░░░  72% available     │  │  ← Progress bar (not numbers)
│  │ Ambulances: ████░  4/5        │  │
│  │ Police:     ██░░░  2/5        │  │
│  │ Shelters:   █████  5/5        │  │
│  └────────────────────────────────┘  │
└──────────────────────────────────────┘
```

---

## Admin Panel (Hidden — Long-Press Logo)

### Access
Long-press the barwaqt logo in the Pulse tab header (3 seconds) → navigates to Admin.

### Sections

#### 1. Simulate (Top Priority)
One-tap scenario buttons with visual feedback:
- **"Flood in G-10"** — Full pipeline: signal → detection → allocation → notification
- **"Heatwave in I-8"** — Second simultaneous crisis (tests multi-crisis)
- **"False Alarm"** — Tests retraction flow
- **"API Failure"** — Tests degraded mode / fallback

Each button triggers `/api/trigger-scenario` POST → runs agents live → app updates in real-time via Firestore listeners.

#### 2. Operational Intelligence (Creative Metrics)
Not just latency — **decision-support metrics** that would matter to a real NDMA commander:

| Metric | Visualization | What It Tells You |
|:-------|:-------------|:-----------------|
| **Resource Utilization** | Donut chart (deployed/available/maintenance) | Are we overextended? |
| **Capacity Forecast** | "At current pace, ambulances exhausted in 2.4h" | Predictive resource planning |
| **Coverage Gaps** | Mini-map with red zones (no resources within 10km) | Where are we blind? |
| **Response Time Trend** | Sparkline (last 10 incidents) | Are we getting faster? |
| **Inter-Agency Coordination** | Score out of 10 (# of agencies per incident) | How well are agencies cooperating? |
| **Signal Quality Index** | Gauge (% of signals with high credibility) | Can we trust our inputs? |
| **False Alarm Rate** | Trend line (last 20 detections) | Is AI accuracy improving? |
| **Population at Risk** | Big number with trend arrow | Total humans in active crisis zones |

#### 3. Pipeline Analytics (Moved from Public)
- CIRO vs Manual speed comparison (hero card)
- Per-stage breakdown (Detection → Allocation → Notification)
- Per-run comparison bars
- Accuracy rate

#### 4. Agent Logs (Moved from Public)
- AI Brain trace timeline (existing AIBrainScreen, relocated)
- Gemini reasoning panels
- Tool call logs

---

## Backend Changes

### [NEW] `ciro/services/news_service.py`
```python
# Fetches Pakistan headlines from GNews API (free tier: 100 req/day)
# Endpoint: https://gnews.io/api/v4/top-headlines?country=pk&category=general
# Caches in Firestore `news_cache` collection (TTL: 30 min)
# Falls back to Dawn RSS feed if GNews quota exceeded
```

### [NEW] `ciro/services/intelligence_service.py`
```python
# Generates social intelligence data for the Intel tab:
# - Mention velocity (from signals collection, count per 15-min bucket)
# - Trending keywords (extracted from signal text fields)
# - Sentiment scores (Gemini analysis of recent signals)
# - Source credibility (computed from signal source metadata)
# Writes to Firestore `live_intelligence` collection
```

### [NEW] `ciro/services/agency_service.py`
Firestore collection `agencies` with pre-seeded data for 6 agencies:
- NDMA (National), Rescue 1122 (Punjab/KP), Islamabad Police, CDA Emergency, PIMS Hospital, PDMA Punjab

### [MODIFY] `ciro/agents/orchestrator/agent.py`
- Write `live_updates` entries at each pipeline stage (for breaking ticker)
- Write `live_intelligence` entries (for Intel tab)
- Update `agencies` stats after each incident response

### [MODIFY] `ciro/main.py`
- `POST /api/trigger-scenario` — Admin simulation endpoint
- `GET /api/news` — Proxied GNews headlines (to avoid CORS + key exposure)
- `GET /api/intelligence` — Current signal intelligence snapshot

---

## Android Changes

### Phase A — Data Layer

| File | Status | Description |
|:-----|:-------|:------------|
| `data/model/LiveUpdate.kt` | NEW | Breaking ticker entries |
| `data/model/Agency.kt` | NEW | Agency with embedded resources + stats |
| `data/model/NewsItem.kt` | NEW | GNews headline model |
| `data/model/IntelligenceSnapshot.kt` | NEW | Mention velocity, sentiment, keywords |
| `data/repository/CiroRepository.kt` | MODIFY | Add observers for 4 new collections |
| `viewmodel/DashboardViewModel.kt` | MODIFY | Add new flows, remove public pipeline metrics |

### Phase B — New Screens

| File | Status | Description |
|:-----|:-------|:------------|
| `ui/pulse/PulseScreen.kt` | NEW | Threat gauge + ticker + situation cards + incident carousel |
| `ui/intel/IntelScreen.kt` | NEW | Signal intelligence dashboard (WorldMonitor-inspired) |
| `ui/map/PakistanMapScreen.kt` | REWRITE | Province drill-down + shape-coded markers + legend |
| `ui/response/ResponseScreen.kt` | NEW | Operations timeline + agency cards + resource readiness |
| `ui/agency/AgencyProfileScreen.kt` | NEW | Agency detail with resource management |
| `ui/admin/AdminPanel.kt` | NEW | Simulate + operational metrics + pipeline + agent logs |

### Phase C — New Components

| File | Status | Description |
|:-----|:-------|:------------|
| `ui/components/ThreatGauge.kt` | NEW | Animated Canvas circular arc gauge |
| `ui/components/BreakingTicker.kt` | NEW | Auto-scrolling marquee headline |
| `ui/components/SparklineChart.kt` | NEW | Mini line chart for trends |
| `ui/components/OperationsTimeline.kt` | NEW | Vertical timeline with dots + lines |
| `ui/components/MapMarkerShapes.kt` | NEW | Canvas-drawn shapes (circle, triangle, square, diamond) |
| `ui/components/ResourceReadinessBar.kt` | NEW | Segmented progress bars per resource type |

### Phase D — Modified

| File | Status | Description |
|:-----|:-------|:------------|
| `MainActivity.kt` | MODIFY | 4-tab nav (Pulse/Intel/Map/Response) + hidden admin route |
| `SplashScreen.kt` | MODIFY | Keep branding, remove admin hint text |
| `IncidentDetailScreen.kt` | MODIFY | Add responding agencies, AI timeline, remove raw latency |
| `CiroColors.kt` | MODIFY | Map marker colors, gauge gradient, intel styling |

---

## Verification Plan

### Build
- `./gradlew assembleDebug` — Compile check

### Demo Flow (3-5 min)
1. **Splash** → barwaqt branding → auto-navigate
2. **Pulse** → Ticker empty, threat gauge GREEN, "All clear"
3. **Intel** → News headlines loading from GNews, signal sources healthy
4. **Map** → Pakistan zoomed out, province chips, no incidents
5. **Admin** (long-press logo) → Tap "Flood in G-10"
6. **Watch**: Ticker updates → Threat gauge rises to ELEVATED → Incident card appears → Map shows blue circle marker → Response tab shows operation timeline → Intel shows mention velocity spike
7. **Admin** → Tap "Heatwave in I-8" (multi-crisis test)
8. **Watch**: Second marker on map, resource utilization rises, agencies deployed to both
9. **Admin** → Tap "False Alarm" → Retraction notice in ticker → Incident retracted → Gauge drops
10. **Response** → Tap agency card → See profile with resources + stats
