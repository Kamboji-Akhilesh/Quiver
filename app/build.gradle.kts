import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Cartesia TTS key for the reminder-call voice. Put `CARTESIA_API_KEY=...` in
// local.properties (gitignored) or set it as an env var in CI. Empty by default,
// in which case the call falls back to on-device system TTS.
val cartesiaApiKey: String = run {
    val props = Properties()
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
    props.getProperty("CARTESIA_API_KEY") ?: System.getenv("CARTESIA_API_KEY") ?: ""
}

// HuggingFace read token, only needed to download a GATED model repo. Put
// `HF_TOKEN=hf_...` in local.properties (gitignored) or set it as a CI env var.
// Empty by default — public repos (the shipped model) download without it.
val hfToken: String = run {
    val props = Properties()
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
    props.getProperty("HF_TOKEN") ?: System.getenv("HF_TOKEN") ?: ""
}

android {
    namespace = "com.kamboji.quiver"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kamboji.quiver"
        minSdk = 29
        targetSdk = 36
        // CI overrides these per build (via the GitHub Actions run number) so each
        // release has a higher versionCode and installs as an update.
        versionCode = (System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1)
        versionName = System.getenv("VERSION_NAME") ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Real phones are arm64; shipping one ABI of MediaPipe's/ML Kit's native
        // libraries keeps the APK slim.
        ndk { abiFilters += "arm64-v8a" }

        buildConfigField("String", "CARTESIA_API_KEY", "\"$cartesiaApiKey\"")
        buildConfigField("String", "HF_TOKEN", "\"$hfToken\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        // Fixed, checked-in debug keystore so every build (local and CI) is
        // signed with the SAME key — required for installing updates over a
        // previous version without uninstalling. Debug-only; not sensitive.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

// AppFunctions KSP: aggregate this module's @AppFunction declarations into the
// metadata the OS assistant (Gemini) reads.
ksp {
    arg("appfunctions:aggregateAppFunctions", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.work.runtime.ktx)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Jetpack Compose (hub, currency, calendar mini-apps)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Home-screen widgets (agenda / quick actions / month spend)
    implementation(libs.androidx.glance.appwidget)

    // On-device LLM runtime: runs the Gemma 3 1B .task (MediaPipe LLM Inference).
    implementation(libs.mediapipe.tasks.genai)

    // On-device OCR for screenshot search (bundled Latin + Devanagari models)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.devanagari)

    // AppFunctions — expose Quiver actions to the OS assistant (Gemini, SDK 36+).
    // Alpha API, pinned; all usage isolated in the appfunctions/ package.
    implementation(libs.androidx.appfunctions)
    implementation(libs.androidx.appfunctions.service)
    ksp(libs.androidx.appfunctions.compiler)

    // Currency networking
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}