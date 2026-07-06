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

android {
    namespace = "com.kamboji.quiver"
    compileSdk = 36
    // Pinned to the NDK that builds the llama.cpp (GGUF) native engine.
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.kamboji.quiver"
        minSdk = 29
        targetSdk = 36
        // CI overrides these per build (via the GitHub Actions run number) so each
        // release has a higher versionCode and installs as an update.
        versionCode = (System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1)
        versionName = System.getenv("VERSION_NAME") ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Real phones are arm64; building llama.cpp for one ABI keeps the APK slim.
        ndk { abiFilters += "arm64-v8a" }

        buildConfigField("String", "CARTESIA_API_KEY", "\"$cartesiaApiKey\"")
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

    // GGUF / llama.cpp native engine. Wired only once the llama.cpp submodule is
    // present (see app/src/main/cpp/README.md), so the build is unaffected until
    // you add it. When present, it builds libllama-android.so for arm64-v8a.
    if (file("src/main/cpp/llama.cpp/CMakeLists.txt").exists()) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }
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

    // Currency networking
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}