# JARVIS Hands-Free Assistant: Android OS Limitations & Edge-Case Architecture

## 1. Android 14+ (API 34) Microphone Foreground Service Restrictions
- **While-In-Use Enforcement**: Starting from Android 11 (API 30) and strictly enforced in Android 14 (API 34), a service with `android:foregroundServiceType="microphone"` **cannot** be started from the background unless the app is currently visible in the foreground or holds a specific system exemption (`ROLE_ASSISTANT` / `VoiceInteractionService`).
- **Architectural Handling**: `JarvisWakeWordService` only starts when the user explicitly taps **Enable Hands-Free Service** inside `PermissionsAndSetupActivity`. If Android blocks startup (`ForegroundServiceStartNotAllowedException` or `SecurityException`), the service catches the exception and posts a clear recovery notification prompting the user to bring the app to the foreground once.

## 2. Microphone Sharing, Conflicts & OS Privacy Indicator
- **OS Privacy Indicator**: On Android 12+, whenever `AudioRecord` or `SpeechRecognizer` is active, Android renders the green microphone privacy indicator in the status bar. JARVIS never attempts to hide or bypass this indicator.
- **Audio Focus & Concurrent Recording**: When a phone call, VoIP call, or exclusive recorder starts, Android may silence background `AudioRecord` streams. `JarvisWakeWordService` registers an `AudioManager.AudioRecordingCallback` to detect competing audio sessions, updates the ongoing notification to `"Paused (Microphone in use by another app)"`, and exposes a **Push-to-Talk** notification action as a reliable fallback.

## 3. Floating Overlay (`SYSTEM_ALERT_WINDOW`) Constraints
- **Official Settings Flow Only**: `SYSTEM_ALERT_WINDOW` cannot be granted silently via `requestPermissions()`. The app directs the user to `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`.
- **Graceful Degradation**: If the user denies overlay permission, hands-free wake-word detection and TTS voice responses continue working through the ongoing foreground notification without crashing.
- **Non-Blocking Window Flags**: The floating holographic orb uses `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL` so it never steals keyboard input or blocks back-gestures in underlying apps.

## 4. Lock Screen (`KeyguardManager`) & Protected Operations
- **No Sensitive Lock-Screen Overlay**: Ordinary `TYPE_APPLICATION_OVERLAY` windows are hidden or restricted over secure lock screens and system permission dialogs.
- **Keyguard Gating**: `DeterministicCommandRouter` inspects `KeyguardManager.isKeyguardLocked`. Safe hardware toggles (e.g., flashlight, volume, current time) can respond via TTS if microphone access is available, whereas protected actions (opening apps, reading/creating notes, accessing contacts, preparing calls or SMS) are blocked until the owner unlocks the device via PIN or biometrics.

## 5. Doze Mode, OEM Battery Savers & Low-Memory Termination
- **Honest Guarantees**: Android may suspend CPU scheduling or reclaim `START_STICKY` services under extreme memory pressure (`onLowMemory`) or aggressive OEM battery savers. The ongoing notification transparently reports if battery optimization is active and never secretly restarts the service if the user explicitly stopped it.
