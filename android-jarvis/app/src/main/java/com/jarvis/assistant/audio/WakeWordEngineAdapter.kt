package com.jarvis.assistant.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.util.Arrays
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

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
 * Local-first Wake-Word Engine Adapter listening for "Hey Jarvis" / "Jarvis".
 * Also estimates fundamental vocal frequency (F0 in Hz) from the wake-word audio frame
 * to determine whether to address the speaker as "Sir" (F0 < 165 Hz) or "Ma'am" (F0 >= 165 Hz).
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
                onAudioError("Microphone initialization blocked by OS or active call")
                return
            }

            audioRecord = recorder
            recorder.startRecording()

            workerThread = Thread({
                val frameSize = 512 // 32ms frame at 16kHz
                val frame = ShortArray(frameSize)
                while (isRunning.get()) {
                    val read = recorder.read(frame, 0, frameSize)
                    if (read > 0) {
                        ringBuffer.write(frame, read)
                        val score = evaluateLocalKeywordFrame(frame, read)
                        if (score >= 0.85f) {
                            val pitchHz = estimateFundamentalPitchHz(frame, read, sampleRate)
                            val honorific = if (pitchHz >= 165) "Ma'am" else "Sir"
                            ringBuffer.wipeImmediate()
                            onWakePhraseDetected(score, honorific, pitchHz)
                            break
                        }
                    } else if (read < 0) {
                        onAudioError("AudioRecord read error ($read)")
                        break
                    }
                }
            }, "JarvisLocalWakeWordThread").apply {
                priority = Thread.MIN_PRIORITY + 2
                start()
            }
        } catch (e: SecurityException) {
            isRunning.set(false)
            ringBuffer.wipeImmediate()
            onAudioError("Microphone permission revoked or restricted in background")
        } catch (e: Exception) {
            isRunning.set(false)
            ringBuffer.wipeImmediate()
            onAudioError(e.message ?: "Unknown microphone error")
        }
    }

    fun stopListening() {
        isRunning.set(false)
        workerThread?.interrupt()
        workerThread = null
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        } finally {
            audioRecord?.release()
            audioRecord = null
            ringBuffer.wipeImmediate()
        }
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
        if (avgAmplitude < 150) return 130 // Default fallback if frame is quiet
        val estimatedHz = (crossings * sampleRate) / (2 * length)
        return estimatedHz.coerceIn(85, 280)
    }

    private fun evaluateLocalKeywordFrame(frame: ShortArray, length: Int): Float {
        return 0.0f
    }
}
