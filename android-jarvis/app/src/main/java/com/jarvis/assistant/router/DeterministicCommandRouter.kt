package com.jarvis.assistant.router

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import com.jarvis.assistant.service.JarvisDeviceAccessibilityService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class CommandDecision {
    data class Executed(
        val categoryLabel: String,
        val spokenResponse: String
    ) : CommandDecision()

    data class RequiresConfirmation(
        val actionSummary: String,
        val previewPayload: String,
        val confirmationPrompt: String,
        val onConfirmExecute: () -> String
    ) : CommandDecision()

    data class BlockedByLockScreen(
        val reason: String
    ) : CommandDecision()

    data class Rejected(
        val reason: String
    ) : CommandDecision()
}

/**
 * Deterministic & Cross-App Device Command Router.
 *
 * Handles:
 * - Hardware controls (Flashlight, Volume Up/Down/Mute, Time/Date)
 * - Cross-App Accessibility Actions (Go Home, Go Back, Recents, Notifications, Click button, Scroll)
 * - Launching Installed Apps (WhatsApp, Instagram, YouTube, Gmail, Photos, Chrome, Settings, Spotify, etc.)
 * - Preparing Calls / WhatsApp / SMS messages
 */
class DeterministicCommandRouter(private val context: Context) {

    private val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val knownAppPackages = mapOf(
        "whatsapp" to "com.whatsapp",
        "instagram" to "com.instagram.android",
        "youtube" to "com.google.android.youtube",
        "gmail" to "com.google.android.gm",
        "photos" to "com.google.android.apps.photos",
        "gallery" to "com.google.android.apps.photos",
        "chrome" to "com.android.chrome",
        "calculator" to "com.google.android.calculator",
        "calendar" to "com.google.android.calendar",
        "clock" to "com.google.android.deskclock",
        "maps" to "com.google.android.apps.maps",
        "spotify" to "com.spotify.music",
        "camera" to "com.android.camera"
    )

    private val localNotesAndReminders = mutableListOf(
        "Review Q4 architecture packet at 4:00 PM",
        "Pick up hardware prototype from lab bench 3"
    )

    fun evaluate(rawUtterance: String): CommandDecision {
        val text = rawUtterance.trim()
        val normalized = text.lowercase(Locale.ROOT)
            .removePrefix("hey jarvis")
            .removePrefix("jarvis")
            .trim()

        if (normalized.isBlank()) {
            return CommandDecision.Rejected("No command detected.")
        }

        // 1. Flashlight control
        if (normalized.contains("flashlight on") || normalized.contains("turn on flashlight") || normalized.contains("torch on") || normalized.contains("टॉर्च ऑन")) {
            val toggled = setFlashlight(true)
            return CommandDecision.Executed(
                categoryLabel = "Hardware · Flashlight",
                spokenResponse = if (toggled) "Flashlight turned on." else "Flashlight hardware unavailable."
            )
        }
        if (normalized.contains("flashlight off") || normalized.contains("turn off flashlight") || normalized.contains("torch off") || normalized.contains("टॉर्च बंद")) {
            val toggled = setFlashlight(false)
            return CommandDecision.Executed(
                categoryLabel = "Hardware · Flashlight",
                spokenResponse = if (toggled) "Flashlight turned off." else "Flashlight hardware unavailable."
            )
        }

        // 2. Volume adjustment
        if (normalized.contains("volume up") || normalized.contains("increase volume") || normalized.contains("आवाज बढ़ाओ")) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            return CommandDecision.Executed("Hardware · Audio", "Media volume increased.")
        }
        if (normalized.contains("volume down") || normalized.contains("decrease volume") || normalized.contains("आवाज कम")) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            return CommandDecision.Executed("Hardware · Audio", "Media volume decreased.")
        }
        if (normalized.contains("mute volume") || normalized.contains("mute audio")) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            return CommandDecision.Executed("Hardware · Audio", "Media audio muted.")
        }

        // 3. Local questions (Time, Date)
        if (normalized.contains("what time") || normalized == "time" || normalized.contains("समय क्या") || normalized.contains("kitne baje")) {
            val formatted = SimpleDateFormat("h:mm a", Locale.US).format(Date())
            return CommandDecision.Executed("Local Query · Time", "It is currently $formatted.")
        }

        // 4. Cross-App Accessibility Global Actions (Home, Back, Recents, Notifications, Click, Scroll)
        if (normalized == "go home" || normalized == "home screen" || normalized == "होम स्क्रीन") {
            val ok = JarvisDeviceAccessibilityService.performSystemGlobalAction("home")
            return if (ok) {
                CommandDecision.Executed("Accessibility · System", "Going to Home screen.")
            } else {
                openHomeIntent()
                CommandDecision.Executed("System Launcher", "Navigating to Home screen.")
            }
        }
        if (normalized == "go back" || normalized == "back" || normalized == "पीछे जाओ") {
            val ok = JarvisDeviceAccessibilityService.performSystemGlobalAction("back")
            return CommandDecision.Executed(
                "Accessibility · System",
                if (ok) "Navigated back." else "Enable JARVIS Accessibility Service to trigger Back across apps."
            )
        }
        if (normalized.contains("recent apps") || normalized == "recents") {
            val ok = JarvisDeviceAccessibilityService.performSystemGlobalAction("recents")
            return CommandDecision.Executed(
                "Accessibility · System",
                if (ok) "Opened Recent Apps." else "Enable Accessibility Service to open Recent Apps."
            )
        }
        if (normalized.contains("open notifications") || normalized == "notifications") {
            val ok = JarvisDeviceAccessibilityService.performSystemGlobalAction("notifications")
            return CommandDecision.Executed(
                "Accessibility · System",
                if (ok) "Opened Notifications shade." else "Enable Accessibility Service to open Notifications."
            )
        }
        if (normalized.startsWith("click ") || normalized.startsWith("tap ")) {
            val label = text.replaceFirst(Regex("(?i)^(hey\\s+jarvis\\s+|jarvis\\s+)?(click|tap)\\s+"), "").trim()
            if (label.isNotEmpty()) {
                val clicked = JarvisDeviceAccessibilityService.clickElementByLabel(label)
                return CommandDecision.Executed(
                    "Accessibility · Node Click",
                    if (clicked) "Tapped '$label' on screen." else "Could not find '$label' on the active screen."
                )
            }
        }
        if (normalized.contains("scroll down") || normalized.contains("नीचे स्क्रॉल")) {
            val scrolled = JarvisDeviceAccessibilityService.scrollActiveWindow(scrollDown = true)
            return CommandDecision.Executed(
                "Accessibility · Scroll",
                if (scrolled) "Scrolled down." else "No scrollable view detected or Accessibility Service not enabled."
            )
        }
        if (normalized.contains("scroll up") || normalized.contains("ऊपर स्क्रॉल")) {
            val scrolled = JarvisDeviceAccessibilityService.scrollActiveWindow(scrollDown = false)
            return CommandDecision.Executed(
                "Accessibility · Scroll",
                if (scrolled) "Scrolled up." else "No scrollable view detected or Accessibility Service not enabled."
            )
        }

        // Guard protected operations behind device unlock
        if (keyguardManager.isKeyguardLocked) {
            return CommandDecision.BlockedByLockScreen(
                "Device is locked. Please unlock to access apps, contacts, or messages."
            )
        }

        // 5. Open Settings or Installed Applications
        if (normalized.startsWith("open ") || normalized.endsWith(" kholo") || normalized.endsWith(" खोलो")) {
            val target = normalized
                .removePrefix("open ")
                .removeSuffix(" kholo")
                .removeSuffix(" खोलो")
                .trim()

            if (target.contains("wifi") || target.contains("wi-fi")) {
                openSettingsIntent(Settings.ACTION_WIFI_SETTINGS)
                return CommandDecision.Executed("System Settings", "Opening Wi-Fi settings.")
            }
            if (target.contains("bluetooth")) {
                openSettingsIntent(Settings.ACTION_BLUETOOTH_SETTINGS)
                return CommandDecision.Executed("System Settings", "Opening Bluetooth settings.")
            }
            if (target.contains("display") || target.contains("brightness")) {
                openSettingsIntent(Settings.ACTION_DISPLAY_SETTINGS)
                return CommandDecision.Executed("System Settings", "Opening Display settings.")
            }
            if (target.contains("settings") || target.contains("सेटिंग")) {
                openSettingsIntent(Settings.ACTION_SETTINGS)
                return CommandDecision.Executed("System Settings", "Opening Android Settings.")
            }

            if (launchInstalledAppByName(target)) {
                return CommandDecision.Executed("App Launch", "Opening $target.")
            }
        }

        // 6. WhatsApp Direct Message Intent
        if (normalized.contains("whatsapp") && (normalized.contains("message") || normalized.contains("send") || normalized.contains("bhejo"))) {
            val msgBody = text.replaceFirst(Regex("(?i).*?(message|send|bhejo)\\s*"), "").trim().ifBlank { "Hello from JARVIS!" }
            return CommandDecision.RequiresConfirmation(
                actionSummary = "Send WhatsApp Message",
                previewPayload = "Message: \"$msgBody\"",
                confirmationPrompt = "Open WhatsApp with message: $msgBody? Say Confirm or Cancel.",
                onConfirmExecute = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        setPackage("com.whatsapp")
                        putExtra(Intent.EXTRA_TEXT, msgBody)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(intent)
                        "Opened WhatsApp with your message."
                    } catch (_: Exception) {
                        val webUri = Uri.parse("https://wa.me/?text=${Uri.encode(msgBody)}")
                        context.startActivity(Intent(Intent.ACTION_VIEW, webUri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                        "Opened WhatsApp message composer."
                    }
                }
            )
        }

        // 7. Local Reminders & Notes
        if (normalized.startsWith("remind me to ") || normalized.startsWith("create note ")) {
            val content = text.replaceFirst(Regex("(?i)^(jarvis\\s+)?(remind me to|create note)\\s+"), "").trim()
            if (content.isNotEmpty()) {
                localNotesAndReminders.add(0, content)
                return CommandDecision.Executed("Local Storage · Reminder", "Saved reminder: $content.")
            }
        }

        if (normalized.contains("read my reminders") || normalized.contains("read reminders")) {
            val summary = localNotesAndReminders.take(3).joinToString("; ")
            return CommandDecision.Executed(
                "Local Storage · Reminder",
                "You have ${localNotesAndReminders.size} reminders: $summary."
            )
        }

        // 8. Prepare Phone Call via Dialer
        if (normalized.startsWith("call ") || normalized.startsWith("dial ")) {
            val contactName = text.replaceFirst(Regex("(?i)^(jarvis\\s+)?(call|dial)\\s+"), "").trim()
            return CommandDecision.RequiresConfirmation(
                actionSummary = "Prepare Dialer Call to $contactName",
                previewPayload = "Opens phone dialer for $contactName",
                confirmationPrompt = "Prepare a call to $contactName in the phone dialer? Say Confirm or Cancel.",
                onConfirmExecute = {
                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(dialIntent)
                    "Opened dialer for $contactName."
                }
            )
        }

        // 9. Prepare SMS Message
        if (normalized.startsWith("send message to ") || normalized.startsWith("text ")) {
            val payload = text.replaceFirst(Regex("(?i)^(jarvis\\s+)?(send message to|text)\\s+"), "").trim()
            return CommandDecision.RequiresConfirmation(
                actionSummary = "Prepare SMS Preview",
                previewPayload = "Draft: \"$payload\"",
                confirmationPrompt = "I prepared a message: $payload. Say Confirm to open SMS composer, or Cancel.",
                onConfirmExecute = {
                    val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).apply {
                        putExtra("sms_body", payload)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(smsIntent)
                    "Message draft opened in SMS app."
                }
            )
        }

        return CommandDecision.Rejected("Route to Gemini Omni Intelligence")
    }

    private fun launchInstalledAppByName(appNameQuery: String): Boolean {
        val pm = context.packageManager
        val directPkg = knownAppPackages.entries.firstOrNull { appNameQuery.contains(it.key) }?.value
        if (directPkg != null) {
            val intent = pm.getLaunchIntentForPackage(directPkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            }
        }

        return try {
            val installed = pm.getInstalledApplications(0)
            val matchedApp = installed.firstOrNull { appInfo ->
                val label = pm.getApplicationLabel(appInfo).toString().lowercase(Locale.ROOT)
                label.contains(appNameQuery) || appNameQuery.contains(label)
            }
            if (matchedApp != null) {
                val launchIntent = pm.getLaunchIntentForPackage(matchedApp.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return true
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun openHomeIntent() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(homeIntent)
    }

    private fun setFlashlight(enabled: Boolean): Boolean {
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return false
            cameraManager.setTorchMode(cameraId, enabled)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun openSettingsIntent(action: String) {
        val intent = Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
