plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.ciro.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ciro.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ── Google Maps API Key ──────────────────────────────────────
        // Option 1: Set in local.properties (recommended):
        //   MAPS_API_KEY=AIzaSy...
        // Option 2: Replace the placeholder below directly:
        manifestPlaceholders["MAPS_API_KEY"] = project.findProperty("MAPS_API_KEY") as? String
            ?: "YOUR_GOOGLE_MAPS_API_KEY_HERE"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // ── Compose BOM ──────────────────────────────────────────────────
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // ── Lifecycle + ViewModel ────────────────────────────────────────
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.activity:activity-compose:1.10.1")

    // ── Navigation Compose ───────────────────────────────────────────
    implementation("androidx.navigation:navigation-compose:2.9.0")

    // ── Firebase BOM (NO -ktx suffix — KTX merged since v34) ────────
    implementation(platform("com.google.firebase:firebase-bom:34.13.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-auth")

    // ── Google Maps Compose ──────────────────────────────────────────
    implementation("com.google.maps.android:maps-compose:8.3.0")
    implementation("com.google.maps.android:maps-compose-utils:8.3.0")

    // ── Vico Charts (Compose + Material 3) ───────────────────────────
    implementation("com.patrykandpatrick.vico:compose:3.1.0")
    implementation("com.patrykandpatrick.vico:compose-m3:3.1.0")

    // ── Coroutines ──────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // ── Core ─────────────────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.16.0")

    // ── Testing ──────────────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
