# CIRO Android App — Build Log

## Session: 2026-05-20T14:00 PKT

### Task
Build complete, runnable Kotlin Android project scaffold for the CIRO crisis management hackathon (Person 3). Teammate should be able to `git pull` → open `android/` in Android Studio → add `google-services.json` → Run.

---

### Files Generated — 32 total

#### Build System (8 files)
| File | Purpose |
|------|---------|
| `settings.gradle.kts` | Project name "CIRO", Google/Maven repos |
| `build.gradle.kts` (root) | Plugin versions: AGP 8.7.3, Kotlin 2.1.0, Compose, Google Services |
| `app/build.gradle.kts` | Full dependencies: Firebase BOM 34.13, Maps Compose 8.3, Vico 3.1, Compose BOM 2025.05 |
| `gradle.properties` | 2GB heap, parallel builds, AndroidX |
| `gradle/wrapper/gradle-wrapper.properties` | Points to Gradle 8.9 distribution |
| `gradlew` | Unix wrapper script (chmod +x) |
| `gradlew.bat` | Windows wrapper script |
| `app/proguard-rules.pro` | Keep rules for Firestore models + Vico |

#### Android Resources (7 files)
| File | Purpose |
|------|---------|
| `AndroidManifest.xml` | Permissions, Maps key placeholder, MainActivity, FCM service, CiroApplication |
| `res/values/strings.xml` | App name + tab labels |
| `res/values/colors.xml` | CIRO brand palette in XML |
| `res/values/themes.xml` | Dark theme (NoActionBar, CIRO colors) |
| `res/drawable/ic_ciro_foreground.xml` | Shield vector icon |
| `res/mipmap-anydpi-v26/ic_launcher.xml` | Adaptive icon |
| `res/mipmap-anydpi-v26/ic_launcher_round.xml` | Adaptive round icon |

#### Kotlin Source (17 files)
| File | Purpose |
|------|---------|
| `CiroApplication.kt` | Firebase init + FCM topic subscriptions (public_alerts, emergency_services, admin) |
| `MainActivity.kt` | 3-tab bottom nav (Dashboard, Map, Alerts) + ViewModel integration |
| `data/model/Incident.kt` | Firestore incidents schema — location, severity forecast, audit log |
| `data/model/Resource.kt` | Firestore resources schema — 30 units with type icons |
| `data/model/CiroNotification.kt` | Firestore notifications schema |
| `data/model/AgentTrace.kt` | Agent traces + pipeline metrics schemas |
| `data/repository/CiroRepository.kt` | 5 `callbackFlow` Firestore snapshot listeners |
| `viewmodel/DashboardViewModel.kt` | 6 `StateFlow`s + computed stats |
| `ui/theme/CiroColors.kt` | Dark command-center colour palette |
| `ui/dashboard/DashboardScreen.kt` | Command center — severity cards, resource bar, pipeline stats, incident list |
| `ui/map/CrisisMapScreen.kt` | Google Maps — severity markers, radius circles, resource markers, bottom sheet |
| `ui/components/SeverityForecastChart.kt` | Vico 3.1 bar chart (T+1h/T+2h/T+6h) |
| `ui/components/AgentTraceTerminal.kt` | Dark terminal — AI reasoning logs, routing decisions, tool calls |
| `ui/notifications/NotificationsScreen.kt` | Stakeholder alert feed with retraction styling |
| `service/CiroFirebaseMessagingService.kt` | FCM push handler with 2 notification channels |

#### Documentation (2 files)
| File | Purpose |
|------|---------|
| `README.md` | 3-step setup guide, project structure, tech stack, troubleshooting |
| `.gitignore` | Excludes google-services.json, local.properties, build artifacts |

---

### Architecture Decisions
1. **Complete Gradle scaffold** — teammate opens `android/` in AS, no project creation needed
2. **No version catalog** — kept simple with inline dependency versions in build.gradle.kts
3. **Maps API key via `local.properties`** — no secrets in git; falls back to placeholder string
4. **`gradle-wrapper.jar` not committed** — Android Studio auto-generates it on first open; avoids 60KB binary in git
5. **CiroApplication class** — handles Firebase init + FCM topic subscriptions on startup
6. **Single shared ViewModel** — all 3 tabs (dashboard/map/alerts) share the same DashboardViewModel

### Teammate Setup
```
git pull
# Add google-services.json to android/app/
# Add MAPS_API_KEY to android/local.properties
# Open android/ in Android Studio → Sync → Run
```
