plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jarvis.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jarvis.assistant"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "3.0.0-LiquidGlass-Ultra"

        // Reads GEMINI_API_KEY from environment or gradle properties during build
        val geminiKey = System.getenv("GEMINI_API_KEY")
            ?: (project.findProperty("GEMINI_API_KEY") as? String)
            ?: ""
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")

        // Cloud proxy endpoint so the APK works out-of-the-box without requiring manual key entry
        val cloudEndpoint = System.getenv("JARVIS_CLOUD_ENDPOINT")
            ?: "https://ais-pre-hqb3a5dusfmnkreppjsu7c-7031346790.asia-southeast1.run.app/api/jarvis/intelligence"
        buildConfigField("String", "JARVIS_CLOUD_ENDPOINT", "\"$cloudEndpoint\"")
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
