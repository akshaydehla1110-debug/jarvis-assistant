import { ValidationScenario } from '../types/jarvis';

export interface AndroidSourceFileItem {
  id: string;
  filePath: string;
  language: 'kotlin' | 'xml' | 'yaml' | 'gradle';
  title: string;
  responsibility: string;
  code: string;
}

export const ANDROID_SOURCE_FILES: AndroidSourceFileItem[] = [
  {
    id: 'manifest',
    filePath: 'android-jarvis/app/src/main/AndroidManifest.xml',
    language: 'xml',
    title: '1. AndroidManifest.xml (Android 11–14+ Permissions, Foreground Service & Accessibility)',
    responsibility:
      'Declares FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE, RECORD_AUDIO, POST_NOTIFICATIONS, SYSTEM_ALERT_WINDOW, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, WAKE_LOCK, READ_CONTACTS, READ_MEDIA_IMAGES/VIDEO, JarvisWakeWordService, and JarvisDeviceAccessibilityService.',
    code: `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <!-- Core Foreground Service & 24/7 Always-On Voice Recognition Permissions (Android 11 to 14+ / SDK 34) -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- Battery Optimization Bypass & Wake Lock to prevent Doze Mode background kill -->
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <!-- Liquid Glass Dynamic Island Overlay Permission (TYPE_APPLICATION_OVERLAY) -->
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

    <!-- Permission-Gated Device, Contacts, Media & Installed Apps Orchestration -->
    <uses-permission android:name="android.permission.READ_CONTACTS" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
    <uses-permission
        android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.FLASHLIGHT" />
    <uses-permission
        android:name="android.permission.QUERY_ALL_PACKAGES"
        tools:ignore="QueryAllPackagesPermission" />

    <application
        android:allowBackup="false"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.JarvisLiquidGlass">

        <activity
            android:name=".ui.PermissionsAndSetupActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:showWhenLocked="false">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.JarvisWakeWordService"
            android:enabled="true"
            android:exported="false"
            android:foregroundServiceType="microphone"
            android:stopWithTask="false" />

        <service
            android:name=".service.JarvisDeviceAccessibilityService"
            android:exported="false"
            android:label="@string/app_name"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/jarvis_accessibility_service" />
        </service>
    </application>
</manifest>`,
  },
  {
    id: 'service',
    filePath:
      'android-jarvis/app/src/main/java/com/jarvis/assistant/service/JarvisWakeWordService.kt',
    language: 'kotlin',
    title: '2. JarvisWakeWordService.kt (24/7 Foreground Service, Audio Chime & State Machine)',
    responsibility:
      'Foreground service with foregroundServiceType="microphone", ongoing notification ("JARVIS is active and listening"), Stage 1 offline wake-word listener -> Stage 2 audio chime -> Dynamic Island expansion -> Indian English ("en-IN", pitch 0.78f, rate 0.98f) greeting ("Hi Sir/Ma\'am, welcome to the JARVIS world, how can I help you?") -> active SpeechRecognizer.',
    code: `// Full production implementation in:
// android-jarvis/app/src/main/java/com/jarvis/assistant/service/JarvisWakeWordService.kt
//
// Highlights:
// 1. Starts Foreground Service with ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
// 2. Attaches sleek top-center Liquid Glass Dynamic Island pill on start (showCompactStandbyPill)
// 3. Stage 1: WakeWordEngineAdapter 16kHz PCM offline cadence & F0 pitch detector
// 4. Stage 2: Plays ToneGenerator chime, expands Dynamic Island, speaks "Hi Sir/Ma'am, welcome to the JARVIS world, how can I help you?"
// 5. Releases AudioRecord BEFORE starting SpeechRecognizer on Main Looper (zero silent mic mute bugs)
// 6. Integrates TaskGuidanceEngine + LiveNavigationOverlayController + JarvisGeminiBrain`,
  },
  {
    id: 'wake-word-engine',
    filePath:
      'android-jarvis/app/src/main/java/com/jarvis/assistant/audio/WakeWordEngineAdapter.kt',
    language: 'kotlin',
    title: '3. WakeWordEngineAdapter.kt (24/7 Offline 16kHz PCM Wake-Word & Vocal Pitch Detector)',
    responsibility:
      'Stage 1 offline 16kHz PCM AudioRecord engine that tracks adaptive noise floor, detects 2-3 syllable "Hey JARVIS" voiced cadence without using SpeechRecognizer in a background loop, estimates F0 Hz for Sir (<165Hz) vs Ma\'am (>=165Hz), and synchronously releases AudioRecord before Stage 2.',
    code: `// Full production implementation in:
// android-jarvis/app/src/main/java/com/jarvis/assistant/audio/WakeWordEngineAdapter.kt
//
// Highlights:
// - Uses AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, CHANNEL_IN_MONO, ENCODING_PCM_16BIT)
// - Evaluates RMS energy against adaptive exponential moving average noise floor
// - Estimates fundamental vocal pitch F0 (85Hz–280Hz) to classify "Sir" vs "Ma'am"
// - Synchronously calls recorder.stop() and recorder.release() before triggering Stage 2 SpeechRecognizer`,
  },
  {
    id: 'dynamic-island-overlay',
    filePath:
      'android-jarvis/app/src/main/java/com/jarvis/assistant/overlay/FloatingOrbOverlayManager.kt',
    language: 'kotlin',
    title: '4. FloatingOrbOverlayManager.kt + overlay_dynamic_island.xml (Liquid Glass Dynamic Island)',
    responsibility:
      'WindowManager TYPE_APPLICATION_OVERLAY floating top-center pill that smoothly expands with TransitionManager, renders a 9-bar animated neon audio waveform, displays honorific badge & AI response text, and collapses back to a sleek pill.',
    code: `// Full production implementation in:
// - android-jarvis/app/src/main/java/com/jarvis/assistant/overlay/FloatingOrbOverlayManager.kt
// - android-jarvis/app/src/main/res/layout/overlay_dynamic_island.xml
//
// Highlights:
// - Gravity.TOP or Gravity.CENTER_HORIZONTAL (y = 28)
// - FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL
// - Smoothly transitions between STANDBY (compact pill) and LISTENING / THINKING / SPEAKING / NAVIGATING (expanded card)`,
  },
  {
    id: 'accessibility-service',
    filePath:
      'android-jarvis/app/src/main/java/com/jarvis/assistant/service/JarvisDeviceAccessibilityService.kt',
    language: 'kotlin',
    title: '5. JarvisDeviceAccessibilityService.kt (Cross-App Actions & Live Line Navigation Bounds)',
    responsibility:
      'Registered AccessibilityService that reads active on-screen nodes (skipping password fields), executes global actions (Home, Back, Recents, Notifications), clicks elements by text, scrolls screens, and supplies Rect bounds to LiveLineNavigationOverlayView.',
    code: `// Full production implementation in:
// android-jarvis/app/src/main/java/com/jarvis/assistant/service/JarvisDeviceAccessibilityService.kt
//
// Highlights:
// - performSystemAction("home" | "back" | "recents" | "notifications")
// - clickElementByText(targetText) via ACTION_CLICK or GestureDescription tap
// - scrollActiveWindow(scrollDown = true/false)
// - findElementBounds(textQueries, viewIdQueries) for Live Screen Line Navigation`,
  },
  {
    id: 'build-gradle',
    filePath: 'android-jarvis/app/build.gradle.kts',
    language: 'gradle',
    title: '6. app/build.gradle.kts (Android SDK 34 Configuration & Dependencies)',
    responsibility:
      'Configures compileSdk = 34, minSdk = 26, targetSdk = 34, Java 17, Kotlin 17, BuildConfig generation, and AndroidX / Material / Coroutines dependencies.',
    code: `plugins {
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
        versionName = "3.0.0-LiquidGlass"

        val geminiKey = System.getenv("GEMINI_API_KEY")
            ?: (project.findProperty("GEMINI_API_KEY") as? String)
            ?: ""
        buildConfigField("String", "GEMINI_API_KEY", "\\"$geminiKey\\"")
    }

    buildFeatures {
        buildConfig = true
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
}`,
  },
  {
    id: 'github-workflow',
    filePath: '.github/workflows/build-android-apk.yml',
    language: 'yaml',
    title: '7. build-android-apk.yml (GitHub Actions Automatic APK Builder)',
    responsibility:
      'Compiles the Android project on GitHub Actions using JDK 17 + Gradle 8.7 and uploads the ready-to-install JARVIS-Liquid-Glass-Android-APK (app-debug.apk) artifact.',
    code: `name: Build JARVIS Android APK

on:
  push:
    branches: [ "main", "master" ]
  pull_request:
    branches: [ "main", "master" ]
  workflow_dispatch:

jobs:
  build-apk:
    name: Compile Android APK (Liquid Glass Dynamic Island)
    runs-on: ubuntu-latest

    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3
        with:
          gradle-version: '8.7'

      - name: Build Debug APK with Gradle
        working-directory: ./android-jarvis
        env:
          GEMINI_API_KEY: \${{ secrets.GEMINI_API_KEY }}
        run: |
          gradle wrapper --gradle-version 8.7
          chmod +x ./gradlew
          ./gradlew assembleDebug --no-daemon --stacktrace

      - name: Upload Ready-to-Install JARVIS APK Artifact
        uses: actions/upload-artifact@v4
        with:
          name: JARVIS-Liquid-Glass-Android-APK
          path: android-jarvis/app/build/outputs/apk/debug/app-debug.apk
          if-no-files-found: error`,
  },
];

export const INITIAL_VALIDATION_CHECKLIST: ValidationScenario[] = [
  {
    id: 'wake-word-stage1-stage2',
    title: '1. Two-Stage 24/7 Wake-Word -> Chime -> Sir/Ma\'am Greeting -> SpeechRecognizer',
    category: 'Wake-Word & Audio Hand-Off',
    adbOrManualSteps:
      'Say "Hey JARVIS" (or tap the top-center Dynamic Island pill) -> Hear audio chime -> Hear "Hi Sir/Ma\'am, welcome to the JARVIS world, how can I help you?" -> Speak command.',
    expectedOsBehavior:
      'Stage 1 offline AudioRecord releases microphone lock synchronously before Stage 2 SpeechRecognizer starts on Main Looper, preventing silent mic mute bugs.',
    osLimitationNote:
      'Requires RECORD_AUDIO permission and Battery Optimization bypass for uninterrupted background operation.',
    verificationState: 'UNVERIFIED_ON_DEVICE',
    simulatorActionLabel: 'Test Wake & Greeting in Simulator',
  },
  {
    id: 'live-line-display-settings',
    title: '2. Live Line Navigation: Change Display Settings (Step 1 → 3)',
    category: 'Live Screen Line Navigation',
    adbOrManualSteps:
      'Say "JARVIS, guide me to change display settings" -> Tap highlighted Step 1 (Display) -> Step 2 (Brightness) -> Step 3 (Dark Theme).',
    expectedOsBehavior:
      'JARVIS opens Settings, draws an animated neon line with a moving glowing dot from the Dynamic Island to the Display row, advances to Step 2 when tapped, removes the old line, and completes at Step 3.',
    osLimitationNote:
      'Requires SYSTEM_ALERT_WINDOW and user-enabled AccessibilityService for automatic node bounds.',
    verificationState: 'UNVERIFIED_ON_DEVICE',
    simulatorActionLabel: 'Run Live Line Demo in Simulator',
  },
  {
    id: 'cross-app-accessibility-actions',
    title: '3. Cross-App Accessibility Actions (Home, Back, Scroll, Click by Text)',
    category: 'Accessibility Service',
    adbOrManualSteps:
      'Enable JARVIS Accessibility Service -> Say "Scroll down", "Click Display", or "Go Home".',
    expectedOsBehavior:
      'JarvisDeviceAccessibilityService reads rootInActiveWindow nodes, performs ACTION_CLICK or GestureDescription taps/swipes, and executes GLOBAL_ACTION_HOME / BACK.',
    osLimitationNote:
      'Strictly skips password fields (node.isPassword == true) to protect user privacy.',
    verificationState: 'UNVERIFIED_ON_DEVICE',
    simulatorActionLabel: 'Test Cross-App Command in Simulator',
  },
];
