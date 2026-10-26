package com.jarvis.assistant.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jarvis.assistant.ai.JarvisGeminiBrain
import com.jarvis.assistant.audio.PrivacyAudioRingBuffer
import com.jarvis.assistant.audio.WakeWordEngineAdapter
import com.jarvis.assistant.navigation.LiveNavigationOverlayController
import com.jarvis.assistant.navigation.TaskGuidanceEngine
import com.jarvis.assistant.overlay.FloatingOrbOverlayManager
import com.jarvis.assistant.overlay.OrbState
import com.jarvis.assistant.router.CommandDecision
import com.jarvis.assistant.router.DeterministicCommandRouter
import com.jarvis.assistant.ui.PermissionsAndSetupActivity
import java.util.Locale

/**
 * 24/7 Always-Ready Wake-Word & Liquid Glass Dynamic Island Foreground Service.
 *
 * Architecture:
 * - Stage 1: Offline 16kHz PCM WakeWordEngineAdapter listens continuously for "Hey JARVIS"
 *   without using SpeechRecognizer in a loop (preventing timeouts and background crashes).
 * - Stage 2: On wake trigger (or pill tap), plays futuristic audio chime, expands the top-center
 *   Liquid Glass Dynamic Island, greets the user ("Hi Sir/Ma'am, welcome to the JARVIS world,
 *   how can I help you?"), and activates SpeechRecognizer to record and execute the command.
 */
class JarvisWakeWordService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val CHANNEL_ID = "jarvis_liquid_glass_channel"
        const val NOTIFICATION_ID = 4102

        const val ACTION_START_LISTENING = "com.jarvis.assistant.action.START_LISTENING"
        const val ACTION_PAUSE_LISTENING = "com.jarvis.assistant.action.PAUSE_LISTENING"
        const val ACTION_STOP_SERVICE = "com.jarvis.assistant.action.STOP_SERVICE"
        const val ACTION_TRIGGER_WAKE_NOW = "com.jarvis.assistant.action.TRIGGER_WAKE_NOW"
        const val ACTION_EXECUTE_TEXT_COMMAND = "com.jarvis.assistant.action.EXECUTE_TEXT_COMMAND"
        const val ACTION_START_GUIDED_TASK = "com.jarvis.assistant.action.START_GUIDED_TASK"
        const val ACTION_NAV_NEXT_STEP = "com.jarvis.assistant.action.NAV_NEXT_STEP"
        const val ACTION_NAV_STOP = "com.jarvis.assistant.action.NAV_STOP"
        const val ACTION_SET_LANGUAGE = "com.jarvis.assistant.action.SET_LANGUAGE"
        const val ACTION_SET_PERSONA = "com.jarvis.assistant.action.SET_PERSONA"
        const val ACTION_SET_THEME = "com.jarvis.assistant.action.SET_THEME"

        const val EXTRA_LANGUAGE_BCP47 = "extra_language_bcp47"
        const val EXTRA_HONORIFIC = "extra_honorific"
        const val EXTRA_TEXT_COMMAND = "extra_text_command"
        const val EXTRA_TASK_QUERY = "extra_task_query"
        const val EXTRA_PERSONA_ID = "extra_persona_id"
        const val EXTRA_PERSONA_PITCH = "extra_persona_pitch"
        const val EXTRA_PERSONA_RATE = "extra_persona_rate"
        const val EXTRA_THEME_HEX = "extra_theme_hex"

        const val ACTION_SERVICE_STATE_BROADCAST = "com.jarvis.assistant.action.SERVICE_STATE_BROADCAST"
        const val EXTRA_STATE_NAME = "extra_state_name"
        const val EXTRA_TRANSCRIPT = "extra_transcript"
        const val EXTRA_SUBTEXT = "extra_subtext"

        const val PREFS_NAME = "jarvis_service_prefs"
        const val KEY_USER_ENABLED = "user_explicitly_enabled"
        const val KEY_LANGUAGE_BCP47 = "selected_language_bcp47"
        const val KEY_PERSONA_ID = "selected_persona_id"
        const val KEY_PERSONA_PITCH = "selected_persona_pitch"
        const val KEY_PERSONA_RATE = "selected_persona_rate"
        const val KEY_THEME_HEX = "selected_theme_hex"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var notificationManager: NotificationManager
    private lateinit var audioManager: AudioManager
    private lateinit var overlayManager: FloatingOrbOverlayManager
    private lateinit var liveNavController: LiveNavigationOverlayController
    private lateinit var taskGuidanceEngine: TaskGuidanceEngine
    private lateinit var commandRouter: DeterministicCommandRouter
    private lateinit var geminiBrain: JarvisGeminiBrain
    private lateinit var ringBuffer: PrivacyAudioRingBuffer

    private var wakeWordEngine: WakeWordEngineAdapter? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var wakeLock: PowerManager.WakeLock? = null

    private var isPausedByUser = false
    private var isCapturingCommand = false
    private var detectedHonorific: String = "Sir"
    private var pendingConfirmationCommand: CommandDecision.RequiresConfirmation? = null

    private val audioRecordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
            super.onRecordingConfigChanged(configs)
            val otherAppUsingMic = configs?.any {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                    it.clientAudioSessionId != wakeWordEngine?.audioSessionId
            } ?: false

            if (otherAppUsingMic && !isPausedByUser && !isCapturingCommand) {
                updateNotificationState("Standby (Microphone in use by another app)", isPaused = true)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()

        ringBuffer = PrivacyAudioRingBuffer(maxDurationMs = 1500)
        commandRouter = DeterministicCommandRouter(applicationContext)
        geminiBrain = JarvisGeminiBrain()

        overlayManager = FloatingOrbOverlayManager(
            context = applicationContext,
            onTapTriggerOrPause = { handleOrbTap() },
            onOpenFullApp = { openSetupActivity() },
            onConfirmAction = { confirmPendingCommand() },
            onCancelAction = { cancelPendingCommand("Cancelled, $detectedHonorific.") }
        )
        overlayManager.setNeonThemeColor(getSavedThemeHex())

        liveNavController = LiveNavigationOverlayController(applicationContext)
        taskGuidanceEngine = TaskGuidanceEngine(
            context = applicationContext,
            overlayController = liveNavController,
            onSpeakInstruction = { instruction ->
                speakAndReturnToStandby(instruction)
            },
            onTaskProgressChanged = { taskTitle, step, total, status ->
                val sub = if (total > 0) "Step $step of $total · $status" else status
                overlayManager.showOrUpdateOrb(
                    state = if (status == "NAVIGATING") OrbState.NAVIGATING else OrbState.COMPLETED,
                    transcript = taskTitle,
                    subtext = sub,
                    honorific = detectedHonorific
                )
                broadcastStateToUi(status, taskTitle, sub)
            }
        )
        taskGuidanceEngine.neonHexColor = getSavedThemeHex()

        tts = TextToSpeech(applicationContext, this)
        audioManager.registerAudioRecordingCallback(audioRecordingCallback, mainHandler)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_LISTENING

        if (action == ACTION_STOP_SERVICE) {
            setUserExplicitlyEnabled(false)
            shutdownCleanly()
            return START_NOT_STICKY
        }

        if (action == ACTION_SET_THEME) {
            val hex = intent?.getStringExtra(EXTRA_THEME_HEX) ?: "#00F5D4"
            saveThemeHex(hex)
            overlayManager.setNeonThemeColor(hex)
            taskGuidanceEngine.neonHexColor = hex
            broadcastStateToUi("THEME_UPDATED", "Neon Theme Updated ($hex)", "Liquid Glass Dynamic Island")
            return START_STICKY
        }

        if (action == ACTION_SET_PERSONA) {
            val personaId = intent?.getStringExtra(EXTRA_PERSONA_ID) ?: "deep_male"
            val pitch = intent?.getFloatExtra(EXTRA_PERSONA_PITCH, 0.78f) ?: 0.78f
            val rate = intent?.getFloatExtra(EXTRA_PERSONA_RATE, 0.98f) ?: 0.98f
            savePersonaSettings(personaId, pitch, rate)
            applyVoicePersonaAndLocale(getSelectedLanguageBcp47(), pitch, rate)
            speakAndReturnToStandby("Hi $detectedHonorific, welcome to the JARVIS world! Voice persona updated.")
            return START_STICKY
        }

        if (action == ACTION_SET_LANGUAGE) {
            val newLang = intent?.getStringExtra(EXTRA_LANGUAGE_BCP47) ?: "en-IN"
            setSelectedLanguageBcp47(newLang)
            applyVoicePersonaAndLocale(newLang, getSavedPitch(), getSavedRate())
            speakAndReturnToStandby("Language updated to $newLang, $detectedHonorific.")
            return START_STICKY
        }

        if (!hasRecordAudioPermission()) {
            reportRecoveryNotification(
                "Microphone permission required",
                "Open JARVIS and tap Grant Permissions to activate 24/7 voice listening."
            )
            broadcastStateToUi("ERROR", "Microphone permission required", "Tap Grant Permissions in app")
            stopSelf()
            return START_NOT_STICKY
        }

        val promoted = promoteToForegroundSafe("JARVIS is active and listening")
        if (!promoted) {
            stopSelf()
            return START_NOT_STICKY
        }

        acquirePartialWakeLockSafe()

        when (action) {
            ACTION_START_LISTENING -> {
                isPausedByUser = false
                setUserExplicitlyEnabled(true)
                overlayManager.showCompactStandbyPill(detectedHonorific)
                startWakeWordDetection()
                broadcastStateToUi(
                    "STANDBY",
                    "JARVIS is active and listening",
                    "Say \"Hey JARVIS\" or tap Dynamic Island"
                )
            }
            ACTION_TRIGGER_WAKE_NOW -> {
                isPausedByUser = false
                setUserExplicitlyEnabled(true)
                val honorificExtra = intent?.getStringExtra(EXTRA_HONORIFIC)
                if (!honorificExtra.isNullOrBlank()) {
                    detectedHonorific = honorificExtra
                }
                val simulatedPitch = if (detectedHonorific == "Ma'am") 210 else 122
                onWakeWordDetected(honorific = detectedHonorific, pitchHz = simulatedPitch)
            }
            ACTION_EXECUTE_TEXT_COMMAND -> {
                val cmd = intent?.getStringExtra(EXTRA_TEXT_COMMAND).orEmpty()
                if (cmd.isNotBlank()) {
                    processRecognizedCommand(cmd)
                }
            }
            ACTION_START_GUIDED_TASK -> {
                val taskQuery = intent?.getStringExtra(EXTRA_TASK_QUERY) ?: "display"
                taskGuidanceEngine.startTaskByVoiceQuery(taskQuery)
            }
            ACTION_NAV_NEXT_STEP -> {
                taskGuidanceEngine.nextStep()
            }
            ACTION_NAV_STOP -> {
                taskGuidanceEngine.stopGuidance()
                overlayManager.showCompactStandbyPill(detectedHonorific)
            }
            ACTION_PAUSE_LISTENING -> {
                isPausedByUser = true
                stopAllAudioCapture()
                overlayManager.showCompactStandbyPill(detectedHonorific)
                updateNotificationState("Hands-free voice recognition paused", isPaused = true)
                broadcastStateToUi("PAUSED", "Voice listening paused", "Tap Resume to continue")
            }
        }

        return if (isUserExplicitlyEnabled()) START_STICKY else START_NOT_STICKY
    }

    private fun promoteToForegroundSafe(statusText: String): Boolean {
        return try {
            val notification = buildOngoingNotification(statusText, isPaused = isPausedByUser)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (_: Exception) {
            reportRecoveryNotification(
                "Open JARVIS to start voice recognition",
                "Android requires starting microphone service while app is visible on screen."
            )
            false
        }
    }

    private fun startWakeWordDetection() {
        if (isPausedByUser || isCapturingCommand) return
        if (!hasRecordAudioPermission()) {
            shutdownCleanly()
            return
        }

        if (wakeWordEngine == null) {
            wakeWordEngine = WakeWordEngineAdapter(
                context = applicationContext,
                ringBuffer = ringBuffer,
                onWakePhraseDetected = { _, honorific, pitchHz ->
                    mainHandler.post {
                        detectedHonorific = honorific
                        onWakeWordDetected(honorific = honorific, pitchHz = pitchHz)
                    }
                },
                onAudioError = { reason ->
                    mainHandler.post {
                        updateNotificationState("Voice Standby: $reason", isPaused = false)
                    }
                }
            )
        }

        wakeWordEngine?.startListening()
        updateNotificationState(
            "JARVIS is active and listening",
            isPaused = false
        )
    }

    /**
     * Stage 2 Trigger:
     * 1. Stops Stage 1 offline AudioRecord cleanly.
     * 2. Plays futuristic audio chime.
     * 3. Expands Liquid Glass Dynamic Island overlay.
     * 4. Speaks "Hi Sir/Ma'am, welcome to the JARVIS world, how can I help you?"
     * 5. Starts active SpeechRecognizer on Main Looper to capture user command.
     */
    private fun onWakeWordDetected(honorific: String, pitchHz: Int) {
        if (!hasRecordAudioPermission()) {
            shutdownCleanly()
            return
        }

        wakeWordEngine?.stopListening()
        isCapturingCommand = true
        playWakeAudioChime()

        val welcomeGreeting = "Hi $honorific, welcome to the JARVIS world, how can I help you?"

        overlayManager.showOrUpdateOrb(
            state = OrbState.SPEAKING,
            transcript = welcomeGreeting,
            subtext = "Detected Voice: $honorific (${pitchHz}Hz)",
            honorific = honorific
        )
        broadcastStateToUi("SPEAKING", welcomeGreeting, "Detected Voice: $honorific (${pitchHz}Hz)")

        speakPromptThenListenForCommand(welcomeGreeting)
    }

    private fun playWakeAudioChime() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 160)
            mainHandler.postDelayed({
                try {
                    toneGen.release()
                } catch (_: Exception) {
                }
            }, 250L)
        } catch (_: Exception) {
        }
    }

    private fun speakPromptThenListenForCommand(greeting: String) {
        if (!ttsReady || tts == null) {
            mainHandler.postDelayed({ startContinuousVoiceRecognition() }, 200L)
            return
        }
        val utteranceId = "wake_greeting_${System.currentTimeMillis()}"
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onError(id: String?) {
                mainHandler.post { startContinuousVoiceRecognition() }
            }
            override fun onDone(id: String?) {
                if (id == utteranceId) {
                    mainHandler.postDelayed({ startContinuousVoiceRecognition() }, 150L)
                }
            }
        })
        tts?.speak(greeting, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun startContinuousVoiceRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(applicationContext)) {
            isCapturingCommand = false
            val msg = "Speech Recognition service is unavailable on this device, $detectedHonorific."
            overlayManager.showOrUpdateOrb(OrbState.ERROR, msg, "Install Google Speech Services", detectedHonorific)
            broadcastStateToUi("ERROR", msg, "SpeechRecognizer unavailable")
            resumeStandbyIfAllowed()
            return
        }

        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    overlayManager.showOrUpdateOrb(
                        state = OrbState.LISTENING,
                        transcript = "Listening, $detectedHonorific... Speak any command",
                        subtext = "All 24 Indian & Global Languages Active",
                        honorific = detectedHonorific
                    )
                    broadcastStateToUi("LISTENING", "Listening, $detectedHonorific...", "Speak in any language")
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    overlayManager.updateWaveformAmplitude(rmsdB)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    overlayManager.showOrUpdateOrb(
                        state = OrbState.THINKING,
                        transcript = "Processing your command, $detectedHonorific...",
                        subtext = "Live Navigation · Cross-App · Omni AI",
                        honorific = detectedHonorific
                    )
                    broadcastStateToUi("THINKING", "Processing your command, $detectedHonorific...", "Omni AI Brain")
                }

                override fun onError(error: Int) {
                    isCapturingCommand = false
                    ringBuffer.wipeImmediate()
                    overlayManager.scheduleAutoDismiss(2500L)
                    broadcastStateToUi(
                        "STANDBY",
                        "Standby · Say \"Hey JARVIS\" or tap Dynamic Island",
                        "Ready for next command"
                    )
                    mainHandler.postDelayed({ resumeStandbyIfAllowed() }, 350L)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val rawText = matches?.firstOrNull()?.trim().orEmpty()
                    ringBuffer.wipeImmediate()
                    processRecognizedCommand(rawText)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        overlayManager.showOrUpdateOrb(
                            state = OrbState.LISTENING,
                            transcript = partial,
                            subtext = "Live Voice Stream",
                            honorific = detectedHonorific
                        )
                        broadcastStateToUi("LISTENING", partial, "Live Voice Stream")
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val selectedLang = getSelectedLanguageBcp47()
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            speechRecognizer?.startListening(recognizerIntent)
        } catch (_: Exception) {
            isCapturingCommand = false
            resumeStandbyIfAllowed()
        }
    }

    private fun processRecognizedCommand(utterance: String) {
        isCapturingCommand = false
        if (utterance.isBlank()) {
            overlayManager.scheduleAutoDismiss(2000L)
            resumeStandbyIfAllowed()
            return
        }

        val lower = utterance.lowercase(Locale.ROOT).trim()

        // 1. Direct "Hey Jarvis" Greeting
        if (lower == "hey jarvis" || lower == "jarvis" || lower == "हे जार्विस") {
            val welcomeMsg = "Hi $detectedHonorific, welcome to the JARVIS world, how can I help you?"
            overlayManager.showOrUpdateOrb(
                state = OrbState.SPEAKING,
                transcript = welcomeMsg,
                subtext = "Addressed as $detectedHonorific",
                honorific = detectedHonorific
            )
            broadcastStateToUi("SPEAKING", welcomeMsg, "Addressed as $detectedHonorific")
            speakPromptThenListenForCommand(welcomeMsg)
            return
        }

        // 2. Live Screen Line Navigation Voice Commands
        if (lower.contains("next step") || lower == "next" || lower.contains("agla step")) {
            taskGuidanceEngine.nextStep()
            return
        }
        if (lower.contains("previous step") || lower.contains("pichla step")) {
            taskGuidanceEngine.previousStep()
            return
        }
        if (lower.contains("stop navigation") || lower.contains("stop guide")) {
            taskGuidanceEngine.stopGuidance()
            return
        }
        if (lower.contains("guide me") || lower.contains("step by step") || lower.contains("kaise karu")) {
            val started = taskGuidanceEngine.startTaskByVoiceQuery(lower)
            if (started) return
        }

        // 3. Pending Confirmation Check
        val awaiting = pendingConfirmationCommand
        if (awaiting != null) {
            if (lower in listOf("yes", "confirm", "allow", "haan", "ha", "send", "proceed", "ok")) {
                confirmPendingCommand()
            } else {
                cancelPendingCommand("Action cancelled safely, $detectedHonorific.")
            }
            return
        }

        // 4. Deterministic Cross-App & Hardware Command Router
        when (val decision = commandRouter.evaluate(utterance, detectedHonorific)) {
            is CommandDecision.Executed -> {
                overlayManager.showOrUpdateOrb(
                    state = OrbState.SPEAKING,
                    transcript = decision.spokenResponse,
                    subtext = decision.categoryLabel,
                    honorific = detectedHonorific
                )
                broadcastStateToUi("SPEAKING", decision.spokenResponse, decision.categoryLabel)
                speakAndReturnToStandby(decision.spokenResponse)
            }

            is CommandDecision.RequiresConfirmation -> {
                pendingConfirmationCommand = decision
                overlayManager.showConfirmationPrompt(
                    title = decision.actionSummary,
                    previewDetails = decision.previewPayload,
                    honorific = detectedHonorific
                )
                broadcastStateToUi("CONFIRMATION", decision.actionSummary, decision.previewPayload)
                speakPromptThenListenForCommand(decision.confirmationPrompt)
            }

            is CommandDecision.BlockedByLockScreen -> {
                overlayManager.showOrUpdateOrb(
                    state = OrbState.ERROR,
                    transcript = decision.reason,
                    subtext = "Unlock device required",
                    honorific = detectedHonorific
                )
                broadcastStateToUi("ERROR", decision.reason, "Unlock device required")
                speakAndReturnToStandby(decision.reason)
            }

            is CommandDecision.Rejected -> {
                overlayManager.showOrUpdateOrb(
                    state = OrbState.THINKING,
                    transcript = "Asking JARVIS Neural Core: \"$utterance\"",
                    subtext = "Gemini Omni AI",
                    honorific = detectedHonorific
                )
                broadcastStateToUi("THINKING", utterance, "Gemini Omni AI")

                geminiBrain.askJarvisAsync(
                    utterance = utterance,
                    preferredLanguageBcp47 = getSelectedLanguageBcp47(),
                    honorific = detectedHonorific,
                    personaStyle = getSavedPersonaId()
                ) { aiReply ->
                    mainHandler.post {
                        overlayManager.showOrUpdateOrb(
                            state = OrbState.SPEAKING,
                            transcript = aiReply.spokenResponse,
                            subtext = "${aiReply.intelligenceDomain} · ${aiReply.detectedLanguage}",
                            honorific = detectedHonorific
                        )
                        broadcastStateToUi(
                            "SPEAKING",
                            aiReply.spokenResponse,
                            "${aiReply.intelligenceDomain} · ${aiReply.detectedLanguage}"
                        )
                        speakAndReturnToStandby(aiReply.spokenResponse)
                    }
                }
            }
        }
    }

    private fun confirmPendingCommand() {
        val pending = pendingConfirmationCommand ?: return
        pendingConfirmationCommand = null
        val outcome = pending.onConfirmExecute()
        overlayManager.showOrUpdateOrb(
            state = OrbState.COMPLETED,
            transcript = outcome,
            subtext = "Confirmed & Executed",
            honorific = detectedHonorific
        )
        broadcastStateToUi("COMPLETED", outcome, "Confirmed & Executed")
        speakAndReturnToStandby(outcome)
    }

    private fun cancelPendingCommand(reason: String) {
        pendingConfirmationCommand = null
        overlayManager.showOrUpdateOrb(
            state = OrbState.COMPLETED,
            transcript = reason,
            subtext = "Aborted safely",
            honorific = detectedHonorific
        )
        broadcastStateToUi("COMPLETED", reason, "Aborted safely")
        speakAndReturnToStandby(reason)
    }

    private fun speakAndReturnToStandby(text: String) {
        if (!ttsReady || tts == null) {
            overlayManager.scheduleAutoDismiss()
            resumeStandbyIfAllowed()
            return
        }
        val utteranceId = "reply_${System.currentTimeMillis()}"
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                mainHandler.post {
                    overlayManager.showOrUpdateOrb(
                        state = OrbState.SPEAKING,
                        transcript = text,
                        subtext = "JARVIS Voice Output ($detectedHonorific)",
                        honorific = detectedHonorific
                    )
                }
            }
            override fun onError(id: String?) {
                mainHandler.post {
                    overlayManager.scheduleAutoDismiss()
                    resumeStandbyIfAllowed()
                }
            }
            override fun onDone(id: String?) {
                if (id == utteranceId) {
                    mainHandler.post {
                        overlayManager.showOrUpdateOrb(
                            state = OrbState.COMPLETED,
                            transcript = text,
                            subtext = "Tap Dynamic Island or say \"Hey JARVIS\"",
                            honorific = detectedHonorific
                        )
                        overlayManager.scheduleAutoDismiss()
                        resumeStandbyIfAllowed()
                    }
                }
            }
        })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun resumeStandbyIfAllowed() {
        if (!isPausedByUser && isUserExplicitlyEnabled() && hasRecordAudioPermission()) {
            startWakeWordDetection()
        }
    }

    /**
     * Tapping the floating Dynamic Island pill:
     * - If currently in STANDBY or COMPLETED, immediately triggers Stage 2 ("Hey JARVIS" greeting + active listening)!
     * - If currently speaking or listening, pauses/collapses back to the standby pill.
     */
    private fun handleOrbTap() {
        if (pendingConfirmationCommand != null) {
            cancelPendingCommand("Cancelled, $detectedHonorific.")
            return
        }
        if (!isCapturingCommand) {
            onWakeWordDetected(honorific = detectedHonorific, pitchHz = 124)
        } else {
            stopAllAudioCapture()
            overlayManager.collapseToStandbyPill()
            resumeStandbyIfAllowed()
        }
    }

    private fun stopAllAudioCapture() {
        isCapturingCommand = false
        wakeWordEngine?.stopListening()
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
        ringBuffer.wipeImmediate()
        tts?.stop()
    }

    private fun shutdownCleanly() {
        stopAllAudioCapture()
        liveNavController.hideOverlay()
        overlayManager.dismissOrb()
        releaseWakeLockSafe()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        ringBuffer.wipeImmediate()
    }

    override fun onDestroy() {
        audioManager.unregisterAudioRecordingCallback(audioRecordingCallback)
        stopAllAudioCapture()
        liveNavController.hideOverlay()
        overlayManager.dismissOrb()
        releaseWakeLockSafe()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            applyVoicePersonaAndLocale(
                bcp47 = getSelectedLanguageBcp47(),
                pitch = getSavedPitch(),
                rate = getSavedRate()
            )
        }
    }

    /**
     * Configures Android TextToSpeech specifically for Indian English ("en-IN") deep male tone
     * (default pitch 0.78f, speech rate 0.98f) or user-selected persona.
     */
    private fun applyVoicePersonaAndLocale(bcp47: String, pitch: Float, rate: Float) {
        val parts = bcp47.split("-")
        val locale = if (parts.size >= 2) Locale(parts[0], parts[1]) else Locale("en", "IN")
        val res = tts?.setLanguage(locale)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale("en", "IN"))
        }
        tts?.setPitch(pitch.coerceIn(0.5f, 1.6f))
        tts?.setSpeechRate(rate.coerceIn(0.6f, 1.5f))
    }

    private fun acquirePartialWakeLockSafe() {
        if (wakeLock?.isHeld == true) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JarvisAssistant::WakeWordLock").apply {
                setReferenceCounted(false)
                acquire(4 * 60 * 60 * 1000L) // 4-hour renewable window
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseWakeLockSafe() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun broadcastStateToUi(stateName: String, transcript: String, subtext: String) {
        val bIntent = Intent(ACTION_SERVICE_STATE_BROADCAST).apply {
            setPackage(packageName)
            putExtra(EXTRA_STATE_NAME, stateName)
            putExtra(EXTRA_TRANSCRIPT, transcript)
            putExtra(EXTRA_SUBTEXT, subtext)
            putExtra(EXTRA_HONORIFIC, detectedHonorific)
        }
        sendBroadcast(bIntent)
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun setUserExplicitlyEnabled(enabled: Boolean) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_USER_ENABLED, enabled)
            .apply()
    }

    private fun isUserExplicitlyEnabled(): Boolean {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_USER_ENABLED, false)
    }

    private fun setSelectedLanguageBcp47(bcp47: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE_BCP47, bcp47)
            .apply()
    }

    private fun getSelectedLanguageBcp47(): String {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE_BCP47, "en-IN") ?: "en-IN"
    }

    private fun savePersonaSettings(id: String, pitch: Float, rate: Float) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PERSONA_ID, id)
            .putFloat(KEY_PERSONA_PITCH, pitch)
            .putFloat(KEY_PERSONA_RATE, rate)
            .apply()
    }

    private fun getSavedPersonaId(): String {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PERSONA_ID, "deep_male") ?: "deep_male"
    }

    private fun getSavedPitch(): Float {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_PERSONA_PITCH, 0.78f)
    }

    private fun getSavedRate(): Float {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_PERSONA_RATE, 0.98f)
    }

    private fun saveThemeHex(hex: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_HEX, hex)
            .apply()
    }

    private fun getSavedThemeHex(): String {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_HEX, "#00F5D4") ?: "#00F5D4"
    }

    private fun openSetupActivity() {
        val intent = Intent(this, PermissionsAndSetupActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS Liquid Glass Dynamic Island",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "24/7 Always-Ready Voice Recognition & Dynamic Island Overlay"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotificationState(statusText: String, isPaused: Boolean) {
        notificationManager.notify(
            NOTIFICATION_ID,
            buildOngoingNotification(statusText, isPaused)
        )
    }

    private fun buildOngoingNotification(statusText: String, isPaused: Boolean): Notification {
        val openAppPending = PendingIntent.getActivity(
            this,
            100,
            Intent(this, PermissionsAndSetupActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val wakeNowIntent = Intent(this, JarvisWakeWordService::class.java).apply {
            action = ACTION_TRIGGER_WAKE_NOW
        }
        val wakeNowPending = PendingIntent.getService(
            this,
            101,
            wakeNowIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopPending = PendingIntent.getService(
            this,
            102,
            Intent(this, JarvisWakeWordService::class.java).apply { action = ACTION_STOP_SERVICE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("JARVIS is active and listening")
            .setContentText(statusText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppPending)
            .addAction(0, "Wake JARVIS", wakeNowPending)
            .addAction(0, if (isPaused) "Resume" else "Stop", stopPending)
            .build()
    }

    private fun reportRecoveryNotification(title: String, message: String) {
        val recoveryIntent = PendingIntent.getActivity(
            this,
            200,
            Intent(this, PermissionsAndSetupActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(recoveryIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }
}
