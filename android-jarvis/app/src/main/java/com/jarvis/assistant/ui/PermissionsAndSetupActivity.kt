package com.jarvis.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jarvis.assistant.service.JarvisWakeWordService

/**
 * Main Setup Activity for JARVIS Liquid Glass Dynamic Island Assistant.
 * Configures:
 * 1. Always-On Voice Recognition (No Push-to-Talk)
 * 2. Liquid Glass Dynamic Island Overlay Permission (SYSTEM_ALERT_WINDOW)
 * 3. Cross-App Private Accessibility Permission
 * 4. All 24 Indian Languages & Deep Male Indian English Accent
 */
class PermissionsAndSetupActivity : AppCompatActivity() {

    companion object {
        private const val REQ_PERMISSIONS = 501
    }

    private lateinit var statusSummaryView: TextView

    private val indianLanguages = listOf(
        "Indian English (Deep Male) — en-IN" to "en-IN",
        "Hinglish — hi-IN" to "hi-IN",
        "Hindi (हिन्दी) — hi-IN" to "hi-IN",
        "Bengali (বাংলা) — bn-IN" to "bn-IN",
        "Telugu (తెలుగు) — te-IN" to "te-IN",
        "Marathi (मराठी) — mr-IN" to "mr-IN",
        "Tamil (தமிழ்) — ta-IN" to "ta-IN",
        "Urdu (اردو) — ur-IN" to "ur-IN",
        "Gujarati (ગુજરાતી) — gu-IN" to "gu-IN",
        "Kannada (ಕನ್ನಡ) — kn-IN" to "kn-IN",
        "Odia (ଓଡ଼ିଆ) — or-IN" to "or-IN",
        "Malayalam (മലയാളം) — ml-IN" to "ml-IN",
        "Punjabi (ਪੰਜਾਬੀ) — pa-IN" to "pa-IN",
        "Assamese (অসমীয়া) — as-IN" to "as-IN",
        "Maithili (मैथिली) — mai-IN" to "mai-IN",
        "Sanskrit (संस्कृतम्) — sa-IN" to "sa-IN",
        "Konkani (कोंकणी) — kok-IN" to "kok-IN",
        "Nepali (नेपाली) — ne-IN" to "ne-IN",
        "Sindhi (सिन्धी) — sd-IN" to "sd-IN",
        "Dogri (डोगरी) — doi-IN" to "doi-IN",
        "Kashmiri (कॉशुर) — ks-IN" to "ks-IN",
        "Manipuri (মৈতৈলোন্) — mni-IN" to "mni-IN",
        "Santali (ᱥᱟᱱᱛᱟᱲᱤ) — sat-IN" to "sat-IN",
        "Bodo (बर’) — brx-IN" to "brx-IN"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#060913"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 64)
        }

        val titleView = TextView(this).apply {
            text = "JARVIS Liquid Glass Dynamic Island"
            textSize = 20f
            setTextColor(Color.WHITE)
        }

        statusSummaryView = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, 24, 0, 32)
        }

        val languageSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@PermissionsAndSetupActivity,
                android.R.layout.simple_spinner_dropdown_item,
                indianLanguages.map { it.first }
            )
        }

        val applyLanguageButton = Button(this).apply {
            text = "Apply Selected Indian Language & Deep Male Accent"
            setOnClickListener {
                val selectedCode = indianLanguages[languageSpinner.selectedItemPosition].second
                val intent = Intent(this@PermissionsAndSetupActivity, JarvisWakeWordService::class.java).apply {
                    action = JarvisWakeWordService.ACTION_SET_LANGUAGE
                    putExtra(JarvisWakeWordService.EXTRA_LANGUAGE_BCP47, selectedCode)
                }
                ContextCompat.startForegroundService(this@PermissionsAndSetupActivity, intent)
            }
        }

        val grantPermissionsButton = Button(this).apply {
            text = "1. Grant Microphone, Contacts & Media Permissions"
            setOnClickListener { requestRuntimePermissions() }
        }

        val grantOverlayButton = Button(this).apply {
            text = "2. Enable Liquid Glass Dynamic Island Overlay"
            setOnClickListener { openOverlayPermissionSettings() }
        }

        val grantAccessibilityButton = Button(this).apply {
            text = "3. Enable Cross-App Access (Accessibility Settings)"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val startHandsFreeButton = Button(this).apply {
            text = "4. Start Always-On Voice Recognition (\"Hey Jarvis\")"
            setOnClickListener { startHandsFreeService() }
        }

        val stopServiceButton = Button(this).apply {
            text = "Stop Hands-Free Voice Service"
            setOnClickListener { stopHandsFreeService() }
        }

        root.addView(titleView)
        root.addView(statusSummaryView)
        root.addView(languageSpinner)
        root.addView(applyLanguageButton)
        root.addView(grantPermissionsButton)
        root.addView(grantOverlayButton)
        root.addView(grantAccessibilityButton)
        root.addView(startHandsFreeButton)
        root.addView(stopServiceButton)

        scrollView.addView(root)
        setContentView(scrollView)
        refreshPermissionStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
    }

    private fun refreshPermissionStatus() {
        val micGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val overlayGranted = Settings.canDrawOverlays(this)

        statusSummaryView.text = buildString {
            appendLine("• Voice Recognition Mode: 100% Command-Based (Zero Push-to-Talk)")
            appendLine("• Voice Persona: Deep Male Clear Indian English + 24 Indian Languages")
            appendLine("• Microphone Permission: ${if (micGranted) "Granted" else "Required"}")
            appendLine("• Dynamic Island Overlay: ${if (overlayGranted) "Granted" else "Required"}")
            appendLine("• Intelligence Cores: Gaming, Editing, Creator & World Omni AI")
        }
    }

    private fun requestRuntimePermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
            perms.add(Manifest.permission.READ_MEDIA_VIDEO)
        }
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), REQ_PERMISSIONS)
    }

    private fun openOverlayPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun startHandsFreeService() {
        val serviceIntent = Intent(this, JarvisWakeWordService::class.java).apply {
            action = JarvisWakeWordService.ACTION_START_LISTENING
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun stopHandsFreeService() {
        val serviceIntent = Intent(this, JarvisWakeWordService::class.java).apply {
            action = JarvisWakeWordService.ACTION_STOP_SERVICE
        }
        startService(serviceIntent)
    }
}
