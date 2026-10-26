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
    id: 'github-workflow',
    filePath: '.github/workflows/build-android-apk.yml',
    language: 'yaml',
    title: 'build-android-apk.yml (GitHub Actions Automatic APK Builder)',
    responsibility:
      'Automatically compiles the Android project on GitHub Actions using JDK 17 + Android SDK + Gradle 8.7 and uploads the ready-to-install JARVIS-Liquid-Glass-Android-APK (app-debug.apk) artifact.',
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

      - name: Setup Android SDK
        uses: android-actions/setup-android@v3

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
  {
    id: 'live-line-overlay',
    filePath:
      'android-jarvis/app/src/main/java/com/jarvis/assistant/navigation/LiveLineNavigationOverlayView.kt',
    language: 'kotlin',
    title: 'LiveLineNavigationOverlayView.kt (Live Neon Line & Arrow Overlay)',
    responsibility:
      'WindowManager TYPE_APPLICATION_OVERLAY view with FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE. Draws cubic Bezier neon lines, traveling glowing photon dot via PathMeasure, arrowhead, target bounding box, and numbered step pill—or honest manual fallback when target bounds cannot be detected.',
    code: `package com.jarvis.assistant.navigation

// Full implementation in /android-jarvis/app/src/main/java/com/jarvis/assistant/navigation/LiveLineNavigationOverlayView.kt
// - Uses FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCHABLE so user taps pass directly through to the target app
// - Draws animated neon-cyan (#00F5D4) or purple (#A855F7) paths to AccessibilityNodeInfo target Rect
// - Shows moving glowing dot via PathMeasure + ValueAnimator
// - Never draws fake paths: falls back to manual instruction banner if targetBounds == null`,
  },
  {
    id: 'task-guidance-engine',
    filePath:
      'android-jarvis/app/src/main/java/com/jarvis/assistant/navigation/TaskGuidanceEngine.kt',
    language: 'kotlin',
    title: 'TaskGuidanceEngine.kt (Step-by-Step Task Guidance Engine)',
    responsibility:
      'Modular task guidance engine with built-in Settings Display & Wi-Fi workflows, Start/Pause/Resume/Next/Previous/Repeat/Stop controls, AccessibilityNodeInfo target matching, and manual fallback.',
    code: `package com.jarvis.assistant.navigation

// Full implementation in /android-jarvis/app/src/main/java/com/jarvis/assistant/navigation/TaskGuidanceEngine.kt
// - Supports "JARVIS, guide me to change display settings", "Next step", "Pause navigation", "Stop guidance"
// - Queries JarvisDeviceAccessibilityService.findElementBounds() to highlight real UI elements on screen`,
  },
  {
    id: 'accessibility-service',
    filePath:
      'android-jarvis/app/src/main/java/com/jarvis/assistant/service/JarvisDeviceAccessibilityService.kt',
    language: 'kotlin',
    title: 'JarvisDeviceAccessibilityService.kt (Accessibility Node Bounds Detector)',
    responsibility:
      'Reads rootInActiveWindow AccessibilityNodeInfo tree when manually enabled by the user, finds matching clickable UI controls, returns exact screen Rect bounds, and strictly skips password fields.',
    code: `package com.jarvis.assistant.service

// Full implementation in /android-jarvis/app/src/main/java/com/jarvis/assistant/service/JarvisDeviceAccessibilityService.kt
// - Skips node.isPassword == true for security
// - Extracts node.getBoundsInScreen(rect) for LiveLineNavigationOverlayView`,
  },
  {
    id: 'manifest',
    filePath: 'android-jarvis/app/src/main/AndroidManifest.xml',
    language: 'xml',
    title: 'AndroidManifest.xml',
    responsibility:
      'Declares FOREGROUND_SERVICE_MICROPHONE, RECORD_AUDIO, SYSTEM_ALERT_WINDOW, BIND_ACCESSIBILITY_SERVICE, JarvisWakeWordService, and JarvisDeviceAccessibilityService.',
    code: `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    <!-- Full manifest in /android-jarvis/app/src/main/AndroidManifest.xml -->
</manifest>`,
  },
  {
    id: 'service',
    filePath: 'android-jarvis/app/src/main/java/com/jarvis/assistant/service/JarvisWakeWordService.kt',
    language: 'kotlin',
    title: 'JarvisWakeWordService.kt (Voice personas, 24 Indian Languages & Omni AI)',
    responsibility:
      'Foreground microphone service with continuous voice recognition, multi-persona TTS (Deep Male, Female, Boy, Girl, Soft, Funny, Chindi/Tapori), and Dynamic Island integration.',
    code: `package com.jarvis.assistant.service
// Full implementation in /android-jarvis/app/src/main/java/com/jarvis/assistant/service/JarvisWakeWordService.kt`,
  },
];

export const INITIAL_VALIDATION_CHECKLIST: ValidationScenario[] = [
  {
    id: 'live-line-display-settings',
    title: '1. Live Line Navigation: Change Display Settings (Step 1 → 3)',
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
    id: 'honest-unidentified-target-fallback',
    title: '2. Honest Fallback When UI Target Cannot Be Detected',
    category: 'Honest Target Detection',
    adbOrManualSteps:
      'Trigger guidance on a custom canvas / unlisted element where AccessibilityNodeInfo has no matching node.',
    expectedOsBehavior:
      'Does NOT draw a fake navigation line. Displays a clear instruction banner and lets the user tap or confirm the step manually.',
    osLimitationNote:
      'Custom OpenGL/Vulkan games and secure windows do not expose AccessibilityNodeInfo trees.',
    verificationState: 'UNVERIFIED_ON_DEVICE',
    simulatorActionLabel: 'Test Unidentified Target Fallback',
  },
  {
    id: 'voice-step-controls',
    title: '3. Voice Navigation Controls (Next, Previous, Pause, Repeat, Stop)',
    category: 'Voice Task Engine',
    adbOrManualSteps:
      'During active guidance, say "Next step", "Previous step", "Repeat this instruction", "Pause navigation", or "Stop guidance".',
    expectedOsBehavior:
      'TaskGuidanceEngine updates the active step highlight, speaks the corresponding instruction, or cleanly removes the overlay on Stop.',
    osLimitationNote:
      'Works both via continuous voice recognition and via the expanded Dynamic Island pill controls.',
    verificationState: 'UNVERIFIED_ON_DEVICE',
    simulatorActionLabel: 'Test Next Step in Simulator',
  },
  {
    id: 'iron-man-3d-and-personas',
    title: '4. 3D Iron Man Mark 85 E.D.I.T.H. & Voice Personas (Deep, Female, Chindi/Tapori)',
    category: '3D & Voice Customization',
    adbOrManualSteps:
      'Switch Voice Persona to "Chindi / Tapori Street Style" or "Deep Male" and customize Dynamic Island Neon Theme.',
    expectedOsBehavior:
      'JARVIS responds in the chosen voice persona/pitch and updates the Dynamic Island & Live Line neon color immediately.',
    osLimitationNote:
      'Supports all 24 Indian languages plus 7 distinct voice personas.',
    verificationState: 'UNVERIFIED_ON_DEVICE',
    simulatorActionLabel: 'Test in Simulator',
  },
];
