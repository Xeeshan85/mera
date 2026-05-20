# CIRO Android App

**Crisis Intelligence & Response Orchestrator** — Kotlin + Jetpack Compose command-center mobile app.

---

## 🚀 Setup (3 steps)

### 1. Add `google-services.json`

Download from Firebase Console → **Project Settings** → **Android app**:
- Package name: `com.ciro.app`
- Firebase Project: `mira0911`

Place it at:
```
android/app/google-services.json
```

> ⚠️ This file is gitignored. Each developer must download their own copy.

### 2. Add Google Maps API Key

Create `android/local.properties` and add:
```properties
sdk.dir=/path/to/your/Android/Sdk
MAPS_API_KEY=AIzaSy_YOUR_KEY_HERE
```

The key is read from `local.properties` at build time and injected into the manifest.

Alternatively, hardcode it in `app/build.gradle.kts` line 28:
```kotlin
manifestPlaceholders["MAPS_API_KEY"] = "AIzaSy_YOUR_KEY_HERE"
```

### 3. Open & Run

1. **Open Android Studio** → **Open** → select the `android/` folder
2. Wait for Gradle sync to complete (first sync downloads ~500MB of dependencies)
3. If prompted about missing `gradle-wrapper.jar`:
   - Android Studio will auto-create it, OR
   - Run: `gradle wrapper --gradle-version 8.9` from terminal
4. Connect device or start emulator
5. Hit ▶️ **Run**

---

## 📁 Project Structure

```
android/
├── build.gradle.kts              ← Root: plugin versions (AGP 8.7.3, Kotlin 2.1.0)
├── settings.gradle.kts           ← Project name + repositories
├── gradle.properties             ← JVM args, AndroidX, parallel builds
├── gradlew / gradlew.bat         ← Gradle wrapper scripts
├── gradle/wrapper/
│   └── gradle-wrapper.properties ← Gradle 8.9 distribution URL
└── app/
    ├── build.gradle.kts          ← Dependencies (Firebase, Maps, Vico, Compose)
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/
        │   ├── values/strings.xml, colors.xml, themes.xml
        │   ├── drawable/ic_ciro_foreground.xml
        │   └── mipmap-anydpi-v26/ic_launcher*.xml
        └── java/com/ciro/app/
            ├── CiroApplication.kt           ← Firebase init + FCM topic subscriptions
            ├── MainActivity.kt              ← 3-tab bottom nav (Dashboard, Map, Alerts)
            ├── data/
            │   ├── model/
            │   │   ├── Incident.kt          ← Firestore incidents schema
            │   │   ├── Resource.kt          ← Firestore resources schema
            │   │   ├── CiroNotification.kt  ← Firestore notifications schema
            │   │   └── AgentTrace.kt        ← agent_traces + metrics schemas
            │   └── repository/
            │       └── CiroRepository.kt    ← 5 real-time Firestore listeners
            ├── viewmodel/
            │   └── DashboardViewModel.kt    ← 6 StateFlows + computed stats
            ├── ui/
            │   ├── theme/CiroColors.kt      ← Dark command-center palette
            │   ├── dashboard/DashboardScreen.kt  ← Cards, charts, traces
            │   ├── map/CrisisMapScreen.kt        ← Google Maps + markers
            │   ├── components/
            │   │   ├── SeverityForecastChart.kt  ← Vico bar chart
            │   │   └── AgentTraceTerminal.kt     ← AI reasoning terminal
            │   └── notifications/NotificationsScreen.kt
            └── service/
                └── CiroFirebaseMessagingService.kt  ← FCM push handler
```

---

## 🔧 Tech Stack

| Component | Version | Purpose |
|-----------|---------|---------|
| AGP | 8.7.3 | Android Gradle Plugin |
| Kotlin | 2.1.0 | Language |
| Compose BOM | 2025.05 | UI framework |
| Firebase BOM | 34.13.0 | Firestore, FCM, Auth |
| Maps Compose | 8.3.0 | Google Maps in Compose |
| Vico | 3.1.0 | Charts library |
| Gradle | 8.9 | Build system |
| Min SDK | 26 | Android 8.0+ |
| Target SDK | 35 | Android 15 |

---

## 🔌 How It Works

The Python backend agents write data to Firestore → this app listens in real-time via `onSnapshot` → UI updates instantly.

**Collections read:**
- `incidents` — active crises with severity, state, location
- `resources` — 30 emergency units (ambulances, police, rescue, etc.)
- `notifications` — stakeholder alerts per incident
- `agent_traces` — AI agent execution logs
- `metrics` — pipeline latency measurements

**Push notifications:**
The app subscribes to FCM topics `public_alerts`, `emergency_services`, and `admin` on startup.

---

## 🐛 Troubleshooting

| Issue | Fix |
|-------|-----|
| "google-services.json not found" | Download from Firebase Console, place in `android/app/` |
| "Maps API key not set" | Add `MAPS_API_KEY=...` to `local.properties` |
| "gradle-wrapper.jar missing" | Open in Android Studio (auto-generates) or run `gradle wrapper --gradle-version 8.9` |
| Firestore permission denied | Check `firestore.rules` — currently open until June 19, 2026 |
| Map shows grey tiles | Enable Maps SDK for Android in Google Cloud Console |
