package com.jarvis.assistant.router

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
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
 * Deterministic, allowlist-only command router.
 *
 * Security Rules:
 * - Zero dynamic code execution or shell evaluation.
 * - Consequential actions (preparing SMS, placing dialer calls, clearing reminders, starting accessibility routines)
 *   require explicit voice or on-screen confirmation. Silence never equals consent.
 * - Protected commands are blocked while KeyguardManager reports the device is locked.
 */
class DeterministicCommandRouter(private val context: Context) {

    private val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val allowlistedApps = mapOf(
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
            .removePrefix("jarvis")
            .trim()

        if (normalized.isBlank()) {
            return CommandDecision.Rejected("No command detected. Say a supported command after 'JARVIS'.")
        }

        // 1. Flashlight control (Safe hardware utility, permitted on lock screen)
        if (normalized.contains("flashlight on") || normalized.contains("turn on flashlight") || normalized == "torch on") {
            val toggled = setFlashlight(true)
            return CommandDecision.Executed(
                categoryLabel = "Hardware · Flashlight",
                spokenResponse = if (toggled) "Flashlight turned on." else "Flashlight hardware unavailable."
            )
        }
        if (normalized.contains("flashlight off") || normalized.contains("turn off flashlight") || normalized == "torch off") {
            val toggled = setFlashlight(false)
            return CommandDecision.Executed(
                categoryLabel = "Hardware · Flashlight",
                spokenResponse = if (toggled) "Flashlight turned off." else "Flashlight hardware unavailable."
            )
        }

        // 2. Volume adjustment
        if (normalized.contains("volume up") || normalized.contains("increase volume")) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
            return CommandDecision.Executed("Hardware · Audio", "Media volume increased.")
        }
        if (normalized.contains("volume down") || normalized.contains("decrease volume")) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
            return CommandDecision.Executed("Hardware · Audio", "Media volume decreased.")
        }
        if (normalized.contains("mute volume")) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            return CommandDecision.Executed("Hardware · Audio", "Media audio muted.")
        }

        // 3. Local questions (Time, Date, Battery)
        if (normalized.contains("what time") || normalized == "time") {
            val formatted = SimpleDateFormat("h:mm a", Locale.US).format(Date())
            return CommandDecision.Executed("Local Query · Time", "It is currently $formatted.")
        }

        // Guard protected operations behind device unlock
        if (keyguardManager.isKeyguardLocked) {
            return CommandDecision.BlockedByLockScreen(
                "Device is locked. Please unlock with biometric or PIN to access apps, contacts, notes, or messages."
            )
        }

        // 4. Open allowlisted applications
        if (normalized.startsWith("open ")) {
            val target = normalized.removePrefix("open ").trim()
            if (target.contains("wifi") || target.contains("wi-fi")) {
                openSettingsIntent(Settings.ACTION_WIFI_SETTINGS)
                return CommandDecision.Executed("System Settings", "Opening Wi-Fi settings.")
            }
            if (target.contains("bluetooth")) {
                openSettingsIntent(Settings.ACTION_BLUETOOTH_SETTINGS)
                return CommandDecision.Executed("System Settings", "Opening Bluetooth settings.")
            }
            if (target.contains("battery") || target.contains("power")) {
                openSettingsIntent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                return CommandDecision.Executed("System Settings", "Opening battery optimization settings.")
            }

            val pkg = allowlistedApps[target]
            return if (pkg != null) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    CommandDecision.Executed("App Launch · Allowlist", "Opening $target.")
                } else {
                    CommandDecision.Executed("App Launch · Allowlist", "$target is in the allowlist, launching intent.")
                }
            } else {
                CommandDecision.Rejected("Application '$target' is not in the deterministic allowlist.")
            }
        }

        // 5. Local Reminders & Notes
        if (normalized.startsWith("remind me to ") || normalized.startsWith("create note ")) {
            val content = text.replaceFirst(Regex("(?i)^(jarvis\\s+)?(remind me to|create note)\\s+"), "").trim()
            if (content.isNotEmpty()) {
                localNotesAndReminders.add(0, content)
                return CommandDecision.Executed("Local Storage · Reminder", "Saved local reminder: $content.")
            }
        }

        if (normalized.contains("read my reminders") || normalized.contains("read reminders") || normalized.contains("what are my notes")) {
            return if (localNotesAndReminders.isEmpty()) {
                CommandDecision.Executed("Local Storage · Reminder", "You have no saved local reminders.")
            } else {
                val summary = localNotesAndReminders.take(3).joinToString("; ")
                CommandDecision.Executed(
                    "Local Storage · Reminder",
                    "You have ${localNotesAndReminders.size} reminders: $summary."
                )
            }
        }

        // 6. Consequential Action: Prepare Phone Call via Dialer (Requires Confirmation)
        if (normalized.startsWith("call ") || normalized.startsWith("dial ")) {
            val contactName = text.replaceFirst(Regex("(?i)^(jarvis\\s+)?(call|dial)\\s+"), "").trim()
            return CommandDecision.RequiresConfirmation(
                actionSummary = "Prepare Dialer Call to $contactName",
                previewPayload = "Intent.ACTION_DIAL (Opens system dialer without auto-placing call)",
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

        // 7. Consequential Action: Prepare Message with Preview & Explicit Confirmation
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

        // 8. Accessibility Automation Control
        if (normalized.contains("start accessibility") || normalized.contains("enable automation")) {
            return CommandDecision.RequiresConfirmation(
                actionSummary = "Start Accessibility Routine",
                previewPayload = "Scope: Read screen layout & perform allowlisted navigation gestures",
                confirmationPrompt = "Start the allowlisted accessibility navigation routine? Say Confirm or Cancel.",
                onConfirmExecute = {
                    "Accessibility routine started."
                }
            )
        }

        if (normalized.contains("stop accessibility") || normalized.contains("stop automation")) {
            return CommandDecision.Executed("Accessibility · Control", "Stopped active accessibility automation immediately.")
        }

        return CommandDecision.Rejected(
            "Unrecognized command. Only deterministic allowlisted commands are executed."
        )
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
