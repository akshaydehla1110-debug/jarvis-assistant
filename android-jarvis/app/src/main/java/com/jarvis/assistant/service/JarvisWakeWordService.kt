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
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
import com.jarvis.assistant.overlay.FloatingOrbOverlayManager
import com.jarvis.assistant.overlay.OrbState
import com.jarvis.assistant.router.CommandDecision
import com.jarvis.assistant.router.DeterministicCommandRouter
import com.jarvis.assistant.ui.PermissionsAndSetupActivity
import java.util.Locale

/**
 * Always-Ready Continuous Voice Recognition & Liquid Glass Dynamic Island Service.
 *
 * When the user says "Hey Jarvis", it detects the speaker's voice register ("Sir" or "Ma'am")
 * and greets them: "Hi Sir/Ma'am, welcome to the JARVIS world, how can I help you?"
 */
class JarvisWakeWordService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val CHANNEL_ID = "jarvis_liquid_glass_channel"
        const val NOTIFICATION_ID = 4102

        const val ACTION_START_LISTENING = "com.jarvis.assistant.action.START_LISTENING"
        const val ACTION_PAUSE_LISTENING = "com.jarvis.assistant.action.PAUSE_LISTENING"
        const val ACTION_STOP_SERVICE = "com.jarvis.assistant.action.STOP_SERVICE"
        const val ACTION_SET_LANGUAGE = "com.jarvis.assistant.action.SET_LANGUAGE"
        const val EXTRA_LANGUAGE_BCP47 = "extra_language_bcp47"

        const val PREFS_NAME = "jarvis_service_prefs"
        const val KEY_USER_ENABLED = "user_explicitly_enabled"
        const val KEY_LANGUAGE_BCP47 = "selected_language_bcp47"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var notificationManager: NotificationManager
    private lateinit var audioManager: AudioManager
    private lateinit var overlayManager: FloatingOrbOverlayManager
    private lateinit var commandRouter: DeterministicCommandRouter
    private lateinit var geminiBrain: JarvisGeminiBrain
    private lateinit var ringBuffer: PrivacyAudioRingBuffer
    private var wakeWordEngine: WakeWordEngineAdapter? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

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
                updateNotificationState("Paused (Microphone in use by another app)", isPaused = true)
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
            onTapPauseOrCancel = { handleOrbTap() },
            onOpenFullApp = { openSetupActivity() },
            onConfirmAction = { confirmPendingCommand() },
            onCancelAction = { cancelPendingCommand("Cancelled, $detectedHonorific.") }
        )

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

        if (action == ACTION_SET_LANGUAGE) {
            val newLang = intent?.getStringExtra(EXTRA_LANGUAGE_BCP47) ?: "en-IN"
            setSelectedLanguageBcp47(newLang)
            applyDeepMaleIndianVoiceLocale(newLang)
            speakAndReturnToStandby("Language updated to $newLang.")
            return START_STICKY
        }

        if (!hasRecordAudioPermission()) {
            reportRecoveryNotification(
                "Microphone permission required",
                "Tap to grant microphone access for hands-free JARVIS voice recognition."
            )
            stopSelf()
            return START_NOT_STICKY
        }

        val promoted = promoteToForegroundSafe("Liquid Glass Dynamic Island · Say \"Hey Jarvis\"")
        if (!promoted) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (action) {
            ACTION_START_LISTENING -> {
                isPausedByUser = false
                setUserExplicitlyEnabled(true)
                startWakeWordDetection()
            }
            ACTION_PAUSE_LISTENING -> {
                isPausedByUser = true
                stopAllAudioCapture()
                overlayManager.dismissOrb()
                updateNotificationState("Hands-free voice recognition paused", isPaused = true)
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
        } catch (e: Exception) {
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
                        updateNotificationState("Voice Standby: $reason", isPaused = true)
                    }
                }
            )
        }

        wakeWordEngine?.startListening()
        updateNotificationState(
            "Say \"Hey Jarvis\" · Dynamic Island Ready",
            isPaused = false
        )
    }

    private fun onWakeWordDetected(honorific: String, pitchHz: Int) {
        if (!hasRecordAudioPermission()) {
            shutdownCleanly()
            return
        }

        wakeWordEngine?.stopListening()
        isCapturingCommand = true

        val welcomeGreeting = "Hi $honorific, welcome to the JARVIS world, how can I help you?"

        if (Settings.canDrawOverlays(this)) {
            overlayManager.showOrUpdateOrb(
                state = OrbState.SPEAKING,
                transcript = welcomeGreeting,
                subtext = "Detected Voice: $honorific (${pitchHz}Hz)"
            )
        }

        speakPromptThenListenForCommand(welcomeGreeting)
    }

    private fun speakPromptThenListenForCommand(greeting: String) {
        if (!ttsReady || tts == null) {
            startContinuousVoiceRecognition()
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
                    mainHandler.post { startContinuousVoiceRecognition() }
                }
            }
        })
        tts?.speak(greeting, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun startContinuousVoiceRecognition() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    overlayManager.showOrUpdateOrb(
                        state = OrbState.LISTENING,
                        transcript = "Listening, $detectedHonorific...",
                        subtext = "Speak in any language"
                    )
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    overlayManager.updateWaveformAmplitude(rmsdB)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    overlayManager.showOrUpdateOrb(
                        state = OrbState.THINKING,
                        transcript = "Processing your request, $detectedHonorific...",
                        subtext = "Live Navigation · Gaming · Editing · Creator"
                    )
                }

                override fun onError(error: Int) {
                    isCapturingCommand = false
                    ringBuffer.wipeImmediate()
                    overlayManager.scheduleAutoDismiss()
                    resumeStandbyIfAllowed()
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
                            subtext = "Voice Stream Active"
                        )
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

        speechRecognizer?.startListening(recognizerIntent)
    }

    private fun processRecognizedCommand(utterance: String) {
        isCapturingCommand = false

        val normalizedWakeOnly = utterance.lowercase(Locale.ROOT).trim()
        if (normalizedWakeOnly == "hey jarvis" || normalizedWakeOnly == "jarvis" || normalizedWakeOnly == "हे जार्विस") {
            val welcomeMsg = "Hi $detectedHonorific, welcome to the JARVIS world, how can I help you?"
            overlayManager.showOrUpdateOrb(
                state = OrbState.SPEAKING,
                transcript = welcomeMsg,
                subtext = "Addressed as $detectedHonorific"
            )
            speakPromptThenListenForCommand(welcomeMsg)
            return
        }

        val awaiting = pendingConfirmationCommand
        if (awaiting != null) {
            val normalized = utterance.lowercase(Locale.ROOT)
            if (normalized in listOf("yes", "confirm", "allow", "haan", "ha", "send", "proceed")) {
                confirmPendingCommand()
            } else {
                cancelPendingCommand("Action cancelled safely, $detectedHonorific.")
            }
            return
        }

        when (val decision = commandRouter.evaluate(utterance)) {
            is CommandDecision.Executed -> {
                overlayManager.showOrUpdateOrb(
                    state = OrbState.SPEAKING,
                    transcript = decision.spokenResponse,
                    subtext = decision.categoryLabel
                )
                speakAndReturnToStandby(decision.spokenResponse)
            }

            is CommandDecision.RequiresConfirmation -> {
                pendingConfirmationCommand = decision
                overlayManager.showConfirmationPrompt(
                    title = decision.actionSummary,
                    previewDetails = decision.previewPayload
                )
                speakPromptThenListenForCommand(decision.confirmationPrompt)
            }

            is CommandDecision.BlockedByLockScreen -> {
                overlayManager.showOrUpdateOrb(
                    state = OrbState.ERROR,
                    transcript = decision.reason,
                    subtext = "Unlock device required"
                )
                speakAndReturnToStandby(decision.reason)
            }

            is CommandDecision.Rejected -> {
                geminiBrain.askJarvisAsync(utterance, getSelectedLanguageBcp47()) { aiReply ->
                    mainHandler.post {
                        overlayManager.showOrUpdateOrb(
                            state = OrbState.SPEAKING,
                            transcript = aiReply.spokenResponse,
                            subtext = "${aiReply.intelligenceDomain} · ${aiReply.detectedLanguage}"
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
            subtext = "Confirmed & Executed"
        )
        speakAndReturnToStandby(outcome)
    }

    private fun cancelPendingCommand(reason: String) {
        pendingConfirmationCommand = null
        overlayManager.showOrUpdateOrb(
            state = OrbState.COMPLETED,
            transcript = reason,
            subtext = "Aborted safely"
        )
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
                        subtext = "JARVIS Voice Output"
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
                            subtext = "Voice Recognition Standby"
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

    private fun handleOrbTap() {
        if (pendingConfirmationCommand != null) {
            cancelPendingCommand("Cancelled.")
            return
        }
        stopAllAudioCapture()
        overlayManager.dismissOrb()
        resumeStandbyIfAllowed()
    }

    private fun stopAllAudioCapture() {
        isCapturingCommand = false
        wakeWordEngine?.stopListening()
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        ringBuffer.wipeImmediate()
        tts?.stop()
    }

    private fun shutdownCleanly() {
        stopAllAudioCapture()
        overlayManager.dismissOrb()
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
        overlayManager.dismissOrb()
    }

    override fun onDestroy() {
        audioManager.unregisterAudioRecordingCallback(audioRecordingCallback)
        stopAllAudioCapture()
        overlayManager.dismissOrb()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            applyDeepMaleIndianVoiceLocale(getSelectedLanguageBcp47())
        }
    }

    private fun applyDeepMaleIndianVoiceLocale(bcp47: String) {
        val parts = bcp47.split("-")
        val locale = if (parts.size >= 2) Locale(parts[0], parts[1]) else Locale("en", "IN")
        tts?.language = locale
        tts?.setPitch(0.80f)
        tts?.setSpeechRate(0.98f)
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
                "JARVIS Liquid Glass Voice Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Always-Ready Voice Recognition & Dynamic Island Overlay"
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

        val toggleActionIntent = Intent(this, JarvisWakeWordService::class.java).apply {
            action = if (isPaused) ACTION_START_LISTENING else ACTION_PAUSE_LISTENING
        }
        val togglePending = PendingIntent.getService(
            this,
            101,
            toggleActionIntent,
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
            .setContentTitle("JARVIS Liquid Glass Dynamic Island")
            .setContentText(statusText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppPending)
            .addAction(
                0,
                if (isPaused) "Resume Voice" else "Pause Voice",
                togglePending
            )
            .addAction(0, "Stop", stopPending)
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
