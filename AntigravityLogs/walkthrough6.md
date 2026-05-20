# barwaqt v2 — Transformation Walkthrough

## Overview
Transformed the barwaqt Android app from a static 5-tab data dashboard into a **story-driven crisis intelligence command center** with real-time signal intelligence, breaking news ticker, animated threat gauge, and hidden admin operations panel.

---

## Backend Services (Python)

### [NEW] [news_service.py](file:///home/admin/CODES/seekho/ciro/services/news_service.py)
- GNews API integration (`country=pk`, free tier: 100 req/day)
- Dawn RSS fallback via `feedparser`
- Firestore cache with 30-minute TTL (`news_cache` collection)
- Keyword-based urgency classification (critical/warning/info)

### [NEW] [intelligence_service.py](file:///home/admin/CODES/seekho/ciro/services/intelligence_service.py)
- Computes OSINT metrics from `signals` collection
- **Mention velocity**: 15-minute time buckets with spike detection
- **Sentiment analysis**: Based on urgency language scores
- **Trending keywords**: Crisis-relevant keyword extraction
- **Source health**: Per-source status monitoring
- Writes snapshots to `live_intelligence` collection

### [NEW] [agency_service.py](file:///home/admin/CODES/seekho/ciro/services/agency_service.py)
- Pre-seeded 6 Pakistan agencies: NDMA, Rescue 1122, ICT Police, CDA Emergency, PIMS Hospital, PDMA Punjab
- Each agency has typed resources with states (AVAILABLE/DISPATCHED/MAINTENANCE/OFF_DUTY)
- Stats tracking: incidents responded, avg response time, total deployments

---

### [MODIFY] [main.py](file:///home/admin/CODES/seekho/ciro/main.py)
7 new API endpoints:
| Endpoint | Method | Purpose |
|---|---|---|
| `/api/news` | GET | Pakistan headlines |
| `/api/intelligence` | GET | Latest intel snapshot |
| `/api/intelligence/refresh` | POST | Force recompute |
| `/api/agencies` | GET | All agencies + resources |
| `/api/seed-agencies` | POST | Re-seed agencies |
| `/api/trigger-scenario` | POST | Admin scenario simulator |

Scenarios: `flood_g10`, `heatwave_i8`, `false_alarm`, `multi_crisis`

### [MODIFY] [orchestrator/agent.py](file:///home/admin/CODES/seekho/ciro/agents/orchestrator/agent.py)
- Added `_write_live_update()` — writes to `live_updates` Firestore collection for the breaking ticker
- Added `_send_fcm_notification()` — sends real FCM push notifications to `emergency_services` and `public_alerts` topics
- Integrated at 4 pipeline stages: MONITORING, HYPOTHESIS, CONFIRMED, RETRACTED

---

## Android Data Layer

### New Models
| File | Purpose |
|---|---|
| [LiveUpdate.kt](file:///home/admin/CODES/seekho/android/app/src/main/java/com/ciro/app/data/model/LiveUpdate.kt) | Breaking ticker entries |
| [Agency.kt](file:///home/admin/CODES/seekho/android/app/src/main/java/com/ciro/app/data/model/Agency.kt) | Agency + resources + stats |
| [NewsItem.kt](file:///home/admin/CODES/seekho/android/app/src/main/java/com/ciro/app/data/model/NewsItem.kt) | GNews headlines |
| [IntelligenceSnapshot.kt](file:///home/admin/CODES/seekho/android/app/src/main/java/com/ciro/app/data/model/IntelligenceSnapshot.kt) | Full intel snapshot (velocity, sentiment, credibility, keywords) |

### [MODIFY] [CiroRepository.kt](file:///home/admin/CODES/seekho/android/app/src/main/java/com/ciro/app/data/repository/CiroRepository.kt)
- Added `observeLiveUpdates()` — real-time listener on `live_updates`
- Added `observeAgencies()` — real-time listener on `agencies`
- Added `observeIntelligence()` — real-time document listener on `live_intelligence/latest`
- Added 3 new Firestore → Kotlin mappers

### [MODIFY] [DashboardViewModel.kt](file:///home/admin/CODES/seekho/android/app/src/main/java/com/ciro/app/viewmodel/DashboardViewModel.kt)
- New flows: `liveUpdates`, `agencies`, `intelligence`, `news`
- Admin panel state: `showAdminPanel`, `openAdminPanel()`, `closeAdminPanel()`

---

## Android UI Components

### Custom Canvas Components
| Component | Description |
|---|---|
| [ThreatGauge.kt](file:///home/admin/CODES/seekho/android/app/src/main/java/com/ciro/app/ui/components/ThreatGauge.kt) | 270° arc gauge with sweep gradient, glow dot, animated fill |
| [BreakingTicker.kt](file:///home/admin/CODES/seekho/android/app/src/main/java/com/ciro/app/ui/components/BreakingTicker.kt) | Pulsing red dot + auto-sliding headline + severity badge |
| [PakistanSilhouette.kt](file:///home/admin/CODES/seekho/android/app/src/main/java/com/ciro/app/ui/components/PakistanSilhouette.kt) | Simplified Pakistan outline with glowing province zones |
| [SparklineChart.kt](file:///home/admin/CODES/seekho/android/app/src/main/java/com/ciro/app/ui/components/SparklineChart.kt) | Mini sparkline for inline trend visualization |

---

## Android Screens

### Navigation Structure (4 tabs + hidden admin)
```
Pulse → Intel → Map → Response
            └── (long-press logo) → Admin Panel
            └── Agency Detail (from Response)
            └── Incident Detail (from any)
```

### [NEW] [PulseScreen.kt](file:///home/admin/CODES/seekho/android/app/src/main/java/com/ciro/app/ui/pulse/PulseScreen.kt)
- **ThreatGauge**: Animated 0-5 level gauge computed from active incidents
- **BreakingTicker**: Auto-rotating headlines from `live_updates`
- **PakistanSilhouette**: Province zones glow based on incident geography
- **Situation Snapshot**: Active/At Risk/Available/Resolved stats
- **Incident Carousel**: Horizontal swipeable active incident cards
- **AI Activity Indicator**: Pulsing "barwaqt AI monitoring" status
- Long-press on logo triggers admin panel

### [NEW] [IntelScreen.kt](file:///home/admin/CODES/seekho/android/app/src/main/java/com/ciro/app/ui/intel/IntelScreen.kt)
- **Mention Velocity**: Sparkline chart with spike detection
- **Sentiment & Credibility**: Dual gauge cards (emoji + star rating)
- **Source Health**: Per-source status indicators (weather, traffic, social, sensor, field)
- **Trending Keywords**: FlowRow keyword cloud with crisis highlighting
- **Pakistan News Feed**: Live GNews/Dawn headlines

### [NEW] [ResponseScreen.kt](file:///home/admin/CODES/seekho/android/app/src/main/java/com/ciro/app/ui/response/ResponseScreen.kt)
- **Resource Readiness**: Overall % + per-type progress bars
- **Active Operations Timeline**: Vertical timeline with audit log events
- **Agency Cards**: Horizontal carousel with ready count + ops stats

### [NEW] [AgencyProfileScreen.kt](file:///home/admin/CODES/seekho/android/app/src/main/java/com/ciro/app/ui/agency/AgencyProfileScreen.kt)
- Agency info, performance stats, resource inventory, readiness gauge

### [NEW] [AdminPanel.kt](file:///home/admin/CODES/seekho/android/app/src/main/java/com/ciro/app/ui/admin/AdminPanel.kt)
- Terminal aesthetic (monospace, green text)
- Scenario simulator: 4 demo scenarios triggerable
- Pipeline metrics sparkline + speedup ratio
- Operational intelligence: confirmed/resolved/retracted/accuracy
- AI reasoning log viewer

### [MODIFY] [MainActivity.kt](file:///home/admin/CODES/seekho/android/app/src/main/java/com/ciro/app/MainActivity.kt)
- **4-tab nav**: Pulse (Radar icon), Intel (Sensors), Map, Response (Shield)
- Added routes: `agency/{agencyId}`, `admin`
- Admin panel slides up from bottom
- Agency detail slides in from right

---

## Real-Time Pipeline Flow
```
Signal → Orchestrator → detect → [live_update] → HYPOTHESIS → [live_update]
                                                 → CONFIRMED → [live_update + FCM]
                                                 → RETRACTED → [live_update + FCM]
```

## Dependencies Added
- `requests>=2.31.0` — GNews HTTP calls
- `feedparser>=6.0.0` — Dawn RSS fallback

## Firestore Collections (new)
- `live_updates` — Breaking ticker entries
- `agencies` — Emergency agency profiles
- `live_intelligence` — Signal intelligence snapshots
- `news_cache` — Cached GNews headlines
