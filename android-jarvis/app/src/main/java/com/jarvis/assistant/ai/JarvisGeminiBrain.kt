package com.jarvis.assistant.ai

import com.jarvis.assistant.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

data class JarvisAiReply(
    val spokenResponse: String,
    val detectedLanguage: String,
    val newLanguageBcp47: String,
    val intelligenceDomain: String,
    val targetPackageHint: String
)

/**
 * Connects the Android Dynamic Island service to Gemini Omni Intelligence:
 * 1. First uses direct Gemini API if BuildConfig.GEMINI_API_KEY is configured, OR
 * 2. Connects directly to the live JARVIS Cloud Run proxy (/api/jarvis/intelligence) so the APK
 *    works out-of-the-box without requiring manual GitHub Secrets setup!
 * 3. Includes an offline multilingual conversational engine fallback when the phone has no internet.
 */
class JarvisGeminiBrain {

    private val executor = Executors.newSingleThreadExecutor()

    companion object {
        private const val CLOUD_PROXY_ENDPOINT =
            "https://ais-pre-hqb3a5dusfmnkreppjsu7c-7031346790.asia-southeast1.run.app/api/jarvis/intelligence"
    }

    fun askJarvisAsync(
        utterance: String,
        preferredLanguageBcp47: String,
        honorific: String = "Sir",
        personaStyle: String = "deep_male",
        onResult: (JarvisAiReply) -> Unit
    ) {
        executor.execute {
            val apiKey = BuildConfig.GEMINI_API_KEY

            // Path A: Try Cloud Run Proxy first when local BuildConfig key is not set
            if (apiKey.isBlank()) {
                val proxyReply = callCloudRunProxy(utterance, preferredLanguageBcp47, honorific, personaStyle)
                if (proxyReply != null) {
                    onResult(proxyReply)
                    return@execute
                }
                // Offline intelligent multilingual response
                onResult(buildOfflineSmartResponse(utterance, preferredLanguageBcp47, honorific))
                return@execute
            }

            // Path B: Direct Gemini API call when BuildConfig.GEMINI_API_KEY is present
            try {
                val endpoint =
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
                val url = URL(endpoint)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    connectTimeout = 10000
                    readTimeout = 15000
                    doOutput = true
                }

                val systemPrompt = """
                    You are JARVIS, a Siri + Gemini style Liquid Glass Dynamic Island voice assistant on Android.
                    Address the user respectfully as "$honorific".
                    Voice Persona Style: $personaStyle.
                    Current language setting: $preferredLanguageBcp47.
                    Reply in concise, natural spoken sentences (2-3 sentences max) in the exact language the user spoke (Hindi, Hinglish, English, or any of the 24 Indian languages).
                """.trimIndent()

                val payload = JSONObject().apply {
                    put(
                        "systemInstruction",
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", systemPrompt))
                        )
                    )
                    put(
                        "contents",
                        JSONArray().put(
                            JSONObject().put(
                                "parts",
                                JSONArray().put(JSONObject().put("text", utterance))
                            )
                        )
                    )
                }

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val responseText = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                    it.readText()
                }

                val root = JSONObject(responseText)
                val text = root.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?.trim()
                    .orEmpty()

                onResult(
                    JarvisAiReply(
                        spokenResponse = if (text.isNotBlank()) text else "Completed, $honorific.",
                        detectedLanguage = preferredLanguageBcp47,
                        newLanguageBcp47 = "",
                        intelligenceDomain = "OMNI_INTELLIGENCE",
                        targetPackageHint = ""
                    )
                )
            } catch (_: Exception) {
                val proxyFallback = callCloudRunProxy(utterance, preferredLanguageBcp47, honorific, personaStyle)
                onResult(proxyFallback ?: buildOfflineSmartResponse(utterance, preferredLanguageBcp47, honorific))
            }
        }
    }

    private fun callCloudRunProxy(
        utterance: String,
        preferredLanguageBcp47: String,
        honorific: String,
        personaStyle: String
    ): JarvisAiReply? {
        return try {
            val url = URL(CLOUD_PROXY_ENDPOINT)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connectTimeout = 9000
                readTimeout = 14000
                doOutput = true
            }

            val reqBody = JSONObject().apply {
                put("utterance", utterance)
                put("preferredLanguage", preferredLanguageBcp47)
                put("userHonorific", honorific)
                put("voicePersona", personaStyle)
                put("intelligenceFocus", "OMNI")
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(reqBody.toString())
                writer.flush()
            }

            if (conn.responseCode !in 200..299) return null

            val responseText = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                it.readText()
            }
            val json = JSONObject(responseText)
            val spoken = json.optString("spokenResponse", "").trim()
            if (spoken.isBlank()) return null

            JarvisAiReply(
                spokenResponse = spoken,
                detectedLanguage = json.optString("detectedLanguage", preferredLanguageBcp47),
                newLanguageBcp47 = json.optString("newLanguageBcp47", ""),
                intelligenceDomain = json.optString("intelligenceDomain", "OMNI_CLOUD"),
                targetPackageHint = json.optString("targetPackageHint", "")
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun buildOfflineSmartResponse(
        utterance: String,
        preferredLanguageBcp47: String,
        honorific: String
    ): JarvisAiReply {
        val lower = utterance.lowercase(Locale.ROOT)
        val timeNow = SimpleDateFormat("h:mm a", Locale.US).format(Date())
        val reply = when {
            lower.contains("kaise ho") || lower.contains("how are you") ->
                "Hi $honorific, main bilkul taiyaar hoon! All Liquid Glass Dynamic Island systems are online. Bataiye main aapki kya madad karun?"
            lower.contains("time") || lower.contains("samay") || lower.contains("baje") ->
                "$honorific, abhi samay $timeNow ho raha hai."
            lower.contains("who are you") || lower.contains("kaun ho") ->
                "Hi $honorific, welcome to the JARVIS world! Main aapka 24/7 Liquid Glass Dynamic Island AI assistant hoon."
            else ->
                "$honorific, maine aapka command sun liya: \"$utterance\". Main aapke phone aur Dynamic Island par har kaam ke liye ready hoon."
        }
        return JarvisAiReply(
            spokenResponse = reply,
            detectedLanguage = preferredLanguageBcp47,
            newLanguageBcp47 = "",
            intelligenceDomain = "OFFLINE_NEURAL_CORE",
            targetPackageHint = ""
        )
    }
}
