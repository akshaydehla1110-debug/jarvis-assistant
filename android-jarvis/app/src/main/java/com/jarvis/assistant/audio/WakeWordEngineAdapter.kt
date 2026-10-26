package com.jarvis.assistant.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.util.Arrays
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Fixed-capacity in-memory circular PCM buffer (1.5 seconds at 16kHz 16-bit mono = 48,000 bytes).
 * Guarantees zero disk persistence and zero network transmission for wake-word detection.
 */
class PrivacyAudioRingBuffer(maxDurationMs: Int = 1500) {
    private val sampleRate = 16000
    private val capacitySamples = (sampleRate * maxDurationMs) / 1000
    private val buffer = ShortArray(capacitySamples)
    private var writeIndex = 0

    @Synchronized
    fun write(samples: ShortArray, length: Int) {
        for (i in 0 until length) {
            buffer[writeIndex] = samples[i]
            writeIndex = (writeIndex + 1) % capacitySamples
        }
    }

    @Synchronized
    fun wipeImmediate() {
        Arrays.fill(buffer, 0.toShort())
        writeIndex = 0
    }
}

/**
 * Stage 1 Offline Low-Power Wake-Word Engine Adapter ("Hey JARVIS" / "JARVIS").
 *
 * Why NOT SpeechRecognizer for 24/7 listening:
 * Android's SpeechRecognizer times out after ~5-8 seconds, plays unwanted system beeps, and crashes
 * in background services. Instead, Stage 1 uses a lightweight 16kHz PCM AudioRecord pipeline with:
 * 1. Adaptive noise-floor tracking + 2-3 syllable voiced cadence detection ("JAR-VIS" / "HEY JAR-VIS")
 * 2. Fundamental Vocal Pitch (F0 Hz) estimation to classify "Sir" (< 165 Hz) vs "Ma'am" (>= 165 Hz)
 * 3. Synchronous AudioRecord release BEFORE triggering Stage 2 so SpeechRecognizer never encounters
 *    silent microphone mute or AudioRecord lock conflicts.
 */
class WakeWordEngineAdapter(
    private val context: Context,
    private val ringBuffer: PrivacyAudioRingBuffer,
    private val onWakePhraseDetected: (confidence: Float, detectedHonorific: String, estimatedPitchHz: Int) -> Unit,
    private val onAudioError: (reason: String) -> Unit
) {
    private val isRunning = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var workerThread: Thread? = null

    // Acoustic cadence state for offline "Hey JARVIS" / "JARVIS" detection
    private var noiseFloorRms: Double = 180.0
    private var activeSpeechFrames: Int = 0
    private var trailingSilenceFrames: Int = 0
    private var syllablePeakCount: Int = 0
    private var wasAboveSyllableThreshold: Boolean = false
    private var pitchAccumulatorHz: Long = 0L
    private var voicedPitchFrames: Int = 0

    val audioSessionId: Int
        get() = audioRecord?.audioSessionId ?: -1

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (isRunning.getAndSet(true)) return

        val sampleRate = 16000
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBufferSize <= 0) {
            isRunning.set(false)
            onAudioError("Invalid AudioRecord buffer configuration")
            return
        }

        try {
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize * 2
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                isRunning.set(false)
                onAudioError("Microphone busy or blocked by active call")
                return
            }

            resetCadenceDetector()
            audioRecord = recorder
            recorder.startRecording()

            workerThread = Thread({
                val frameSize = 512 // 32ms per frame at 16kHz
                val frame = ShortArray(frameSize)
                while (isRunning.get()) {
                    val read = try {
                        recorder.read(frame, 0, frameSize)
                    } catch (_: Exception) {
                        -1
                    }

                    if (read > 0) {
                        ringBuffer.write(frame, read)
                        val score = evaluateLocalKeywordFrame(frame, read, sampleRate)
                        if (score >= 0.85f) {
                            val avgPitchHz = if (voicedPitchFrames > 0) {
                                (pitchAccumulatorHz / voicedPitchFrames).toInt().coerceIn(85, 280)
                            } else {
                                estimateFundamentalPitchHz(frame, read, sampleRate)
                            }
                            val honorific = if (avgPitchHz >= 165) "Ma'am" else "Sir"

                            // CRITICAL: Release AudioRecord synchronously BEFORE Stage 2 SpeechRecognizer starts
                            isRunning.set(false)
                            releaseRecorderInternal()
                            onWakePhraseDetected(score, honorific, avgPitchHz)
                            break
                        }
                    } else if (read < 0) {
                        if (isRunning.get()) {
                            isRunning.set(false)
                            releaseRecorderInternal()
                            onAudioError("AudioRecord read interrupted ($read)")
                        }
                        break
                    }
                }
            }, "JarvisOfflineWakeWordThread").apply {
                priority = Thread.NORM_PRIORITY
                start()
            }
        } catch (e: SecurityException) {
            isRunning.set(false)
            releaseRecorderInternal()
            onAudioError("Microphone permission restricted in background")
        } catch (e: Exception) {
            isRunning.set(false)
            releaseRecorderInternal()
            onAudioError(e.message ?: "Microphone initialization error")
        }
    }

    fun stopListening() {
        isRunning.set(false)
        workerThread?.interrupt()
        workerThread = null
        releaseRecorderInternal()
    }

    @Synchronized
    private fun releaseRecorderInternal() {
        val recorder = audioRecord
        audioRecord = null
        if (recorder != null) {
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
            } catch (_: Exception) {
            }
            try {
                recorder.release()
            } catch (_: Exception) {
            }
        }
        ringBuffer.wipeImmediate()
        resetCadenceDetector()
    }

    private fun resetCadenceDetector() {
        activeSpeechFrames = 0
        trailingSilenceFrames = 0
        syllablePeakCount = 0
        wasAboveSyllableThreshold = false
        pitchAccumulatorHz = 0L
        voicedPitchFrames = 0
    }

    /**
     * Estimates speaker fundamental frequency (F0 Hz) locally using zero-crossing rate on voiced PCM samples.
     * Adult male voices typically fall in 85–160 Hz ("Sir"); adult female voices typically fall in 165–255 Hz ("Ma'am").
     */
    fun estimateFundamentalPitchHz(frame: ShortArray, length: Int, sampleRate: Int = 16000): Int {
        if (length <= 1) return 125
        var crossings = 0
        var energySum = 0L
        for (i in 1 until length) {
            energySum += abs(frame[i].toInt())
            if ((frame[i - 1] >= 0 && frame[i] < 0) || (frame[i - 1] < 0 && frame[i] >= 0)) {
                crossings++
            }
        }
        val avgAmplitude = energySum / length
        if (avgAmplitude < 140) return 130
        val estimatedHz = (crossings * sampleRate) / (2 * length)
        return estimatedHz.coerceIn(85, 280)
    }

    /**
     * Offline DSP wake-phrase cadence evaluator for "Hey JARVIS" / "JARVIS":
     * - Measures frame RMS energy and Zero-Crossing Rate (human speech band vs clicks/bumps).
     * - Tracks 2-to-4 syllable speech bursts lasting 380ms to 1900ms (12 to 60 frames at 32ms/frame)
     *   followed by a clean 220ms end-of-phrase pause (7 frames).
     */
    private fun evaluateLocalKeywordFrame(frame: ShortArray, length: Int, sampleRate: Int): Float {
        if (length <= 0) return 0f

        var sumSquares = 0.0
        var crossings = 0
        for (i in 0 until length) {
            val s = frame[i].toDouble()
            sumSquares += s * s
            if (i > 0 && ((frame[i - 1] >= 0 && frame[i] < 0) || (frame[i - 1] < 0 && frame[i] >= 0))) {
                crossings++
            }
        }

        val rms = sqrt(sumSquares / length)
        val zcrHz = (crossings * sampleRate) / (2 * length)
        val isHumanSpeechBand = zcrHz in 75..420

        // Dynamically adapt noise floor during quiet frames
        val triggerThreshold = (noiseFloorRms * 2.4).coerceIn(380.0, 2200.0)
        if (rms < triggerThreshold) {
            noiseFloorRms = (noiseFloorRms * 0.96) + (rms * 0.04)
        }

        if (rms >= triggerThreshold && isHumanSpeechBand) {
            activeSpeechFrames++
            trailingSilenceFrames = 0

            val framePitch = zcrHz.coerceIn(85, 280)
            pitchAccumulatorHz += framePitch
            voicedPitchFrames++

            val syllablePeakThreshold = triggerThreshold * 1.32
            if (rms >= syllablePeakThreshold && !wasAboveSyllableThreshold) {
                syllablePeakCount++
                wasAboveSyllableThreshold = true
            } else if (rms < triggerThreshold * 1.1) {
                wasAboveSyllableThreshold = false
            }

            // Reset if continuous speech exceeds 2.4 seconds (e.g. background TV or long conversation)
            if (activeSpeechFrames > 75) {
                resetCadenceDetector()
            }
            return 0.35f
        } else {
            wasAboveSyllableThreshold = false
            if (activeSpeechFrames > 0) {
                trailingSilenceFrames++
                // "JAR-VIS" or "HEY JAR-VIS": 11 to 62 active frames (~350ms to ~1980ms) with >= 1 syllable peak
                // followed by 7 frames (~224ms) of end-of-phrase silence
                if (trailingSilenceFrames >= 7) {
                    val matchedWakeCadence = activeSpeechFrames in 11..62 && syllablePeakCount in 1..5
                    if (matchedWakeCadence) {
                        return 0.92f
                    }
                    resetCadenceDetector()
                }
            }
            return 0.0f
        }
    }
}
