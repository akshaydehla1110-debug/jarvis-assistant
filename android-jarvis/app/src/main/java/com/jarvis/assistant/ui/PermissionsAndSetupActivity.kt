package com.jarvis.assistant.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jarvis.assistant.service.JarvisDeviceAccessibilityService
import com.jarvis.assistant.service.JarvisWakeWordService

/**
 * Main Liquid Glass Dynamic Island Command Center Activity for Android APK.
 *
 * Provides:
 * 1. Exact Liquid Glass Neon Dark Interface matching the web studio + embedded Live Cloud HUD tab.
 * 2. Live Top Dynamic Island Card synced in real time with JarvisWakeWordService broadcasts.
 * 3. Instant "Say 'Hey JARVIS' (Male · Sir)" & "Say 'Hey JARVIS' (Female · Ma'am)" wake triggers.
 * 4. Complete Android 11–14+ Permission & Battery Optimization Bypass (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).
 * 5. Live Screen Line Navigation launchers (Display Guide, Wi-Fi Guide, Next Step, Stop).
 * 6. Voice Persona selector (Deep Male en-IN 0.78 pitch / 0.98 rate + 6 more personas) & Neon Themes.
 */
class PermissionsAndSetupActivity : AppCompatActivity() {

    companion object {
        private const val REQ_PERMISSIONS = 501
        private const val HOSTED_HUD_URL =
            "https://ais-pre-hqb3a5dusfmnkreppjsu7c-7031346790.asia-southeast1.run.app"
    }

    private lateinit var liveIslandStateBadge: TextView
    private lateinit var liveIslandHonorificBadge: TextView
    private lateinit var liveIslandTranscriptText: TextView
    private lateinit var liveIslandSubText: TextView
    private lateinit var permissionMatrixText: TextView
    private lateinit var nativeScrollContainer: ScrollView
    private lateinit var webHudContainer: LinearLayout
    private var hudWebView: WebView? = null

    private var activeNeonHex: String = "#00F5D4"

    private data class PersonaOption(
        val id: String,
        val title: String,
        val pitch: Float,
        val rate: Float
    )

    private val voicePersonas = listOf(
        PersonaOption("deep_male", "Deep Male (JARVIS Classic Indian English · Pitch 0.78)", 0.78f, 0.98f),
        PersonaOption("female_ai", "Female (F.R.I.D.A.Y. Executive AI · Pitch 1.18)", 1.18f, 1.02f),
        PersonaOption("boy_energetic", "Boy (Young Energetic Prodigy · Pitch 1.10)", 1.10f, 1.08f),
        PersonaOption("girl_bright", "Girl (Bright Friendly Assistant · Pitch 1.28)", 1.28f, 1.05f),
        PersonaOption("soft_calm", "Soft & Calm (Soothing Voice · Pitch 0.95)", 0.95f, 0.91f),
        PersonaOption("funny_witty", "Funny & Witty (Desi Comedy Buddy · Pitch 1.14)", 1.14f, 1.06f),
        PersonaOption("chindi_tapori", "Chindi / Bambaiya Tapori ('Apun ka Jarvis' · Pitch 0.88)", 0.88f, 1.06f)
    )

    private val neonThemes = listOf(
        "Neon Cyan (#00F5D4)" to "#00F5D4",
        "Electric Purple (#A855F7)" to "#A855F7",
        "Solar Gold (#F59E0B)" to "#F59E0B",
        "Matrix Emerald (#10B981)" to "#10B981"
    )

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

    private val serviceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != JarvisWakeWordService.ACTION_SERVICE_STATE_BROADCAST) return
            val state = intent.getStringExtra(JarvisWakeWordService.EXTRA_STATE_NAME) ?: "STANDBY"
            val transcript = intent.getStringExtra(JarvisWakeWordService.EXTRA_TRANSCRIPT).orEmpty()
            val subtext = intent.getStringExtra(JarvisWakeWordService.EXTRA_SUBTEXT).orEmpty()
            val honorific = intent.getStringExtra(JarvisWakeWordService.EXTRA_HONORIFIC) ?: "Sir"

            liveIslandStateBadge.text = "● $state"
            liveIslandHonorificBadge.text = "VOICE: ${honorific.uppercase()}"
            if (transcript.isNotBlank()) {
                liveIslandTranscriptText.text = transcript
            }
            if (subtext.isNotBlank()) {
                liveIslandSubText.text = subtext
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val d = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * d).toInt()

        val masterRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#040711"))
        }

        // Top Mode Bar: Switch between Native Liquid Glass Command Center & Full Cloud Studio HUD
        val topModeBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(12), dp(14), dp(8))
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#080D1A"))
        }

        val btnNativeMode = createGlassButton("⚡ Native Liquid Glass APK", "#00F5D4", isPrimary = true)
        val btnWebHudMode = createGlassButton("🌐 Full Interactive Studio HUD", "#38BDF8", isPrimary = false)

        val tabParams1 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(6)
        }
        val tabParams2 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(6)
        }
        topModeBar.addView(btnNativeMode, tabParams1)
        topModeBar.addView(btnWebHudMode, tabParams2)
        masterRoot.addView(topModeBar)

        // Container 1: Native Liquid Glass ScrollView
        nativeScrollContainer = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        // Container 2: Embedded Full Interactive Studio WebView (so APK also has 100% of Web UI)
        webHudContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        btnNativeMode.setOnClickListener {
            nativeScrollContainer.visibility = View.VISIBLE
            webHudContainer.visibility = View.GONE
        }
        btnWebHudMode.setOnClickListener {
            nativeScrollContainer.visibility = View.GONE
            webHudContainer.visibility = View.VISIBLE
            ensureWebHudLoaded()
        }

        val contentRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(36))
        }

        // =========================================================================
        // 1. LIVE LIQUID GLASS DYNAMIC ISLAND CARD (Top-Center Preview + Instant Wake)
        // =========================================================================
        val islandCard = createLiquidGlassCard("#00F5D4").apply {
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }

        val islandHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        liveIslandStateBadge = TextView(this).apply {
            text = "● STANDBY · 24/7 WAKE ENGINE"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#00F5D4"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        liveIslandHonorificBadge = TextView(this).apply {
            text = "VOICE: SIR / MA'AM"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#38BDF8"))
        }

        islandHeaderRow.addView(liveIslandStateBadge)
        islandHeaderRow.addView(liveIslandHonorificBadge)

        val appTitle = TextView(this).apply {
            text = "JARVIS Liquid Glass Dynamic Island"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, dp(8), 0, dp(4))
        }

        liveIslandTranscriptText = TextView(this).apply {
            text = "\"Hi Sir/Ma'am, welcome to the JARVIS world, how can I help you?\""
            textSize = 14f
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(0, dp(4), 0, dp(4))
        }

        liveIslandSubText = TextView(this).apply {
            text = "Say \"Hey JARVIS\" anytime or tap a Wake button below to speak in any language."
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 0, 0, dp(12))
        }

        // Instant Wake Trigger Row ("Hey JARVIS" Male · Sir & Female · Ma'am)
        val wakeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val btnWakeSir = createGlassButton("🎙️ \"Hey JARVIS\" (Sir)", "#00F5D4", isPrimary = true).apply {
            setOnClickListener { triggerWakeNow("Sir") }
        }
        val btnWakeMaam = createGlassButton("🎙️ \"Hey JARVIS\" (Ma'am)", "#A855F7", isPrimary = true).apply {
            setOnClickListener { triggerWakeNow("Ma'am") }
        }

        wakeRow.addView(btnWakeSir, tabParams1)
        wakeRow.addView(btnWakeMaam, tabParams2)

        islandCard.addView(islandHeaderRow)
        islandCard.addView(appTitle)
        islandCard.addView(liveIslandTranscriptText)
        islandCard.addView(liveIslandSubText)
        islandCard.addView(wakeRow)
        contentRoot.addView(islandCard, cardMarginParams(dp(14)))

        // =========================================================================
        // 2. INSTANT VOICE / TEXT COMMAND & AI EXECUTION BAR
        // =========================================================================
        val commandCard = createLiquidGlassCard("#38BDF8").apply {
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        val cmdHeader = TextView(this).apply {
            text = "⚡ DIRECT COMMAND & MULTILINGUAL AI TESTER"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#38BDF8"))
        }

        val cmdInput = EditText(this).apply {
            hint = "Ask anything or type: Open YouTube, Guide Display, Torch On..."
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            textSize = 13f
            setBackgroundColor(Color.parseColor("#111827"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        val btnSendCommand = createGlassButton("🚀 Execute Command & Speak Reply", "#00F5D4", isPrimary = true).apply {
            setOnClickListener {
                val text = cmdInput.text?.toString()?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    sendCommandToService(text)
                    cmdInput.setText("")
                } else {
                    triggerWakeNow("Sir")
                }
            }
        }

        commandCard.addView(cmdHeader)
        commandCard.addView(cmdInput, cardMarginParams(dp(8)))
        commandCard.addView(btnSendCommand, cardMarginParams(dp(8)))
        contentRoot.addView(commandCard, cardMarginParams(dp(14)))

        // =========================================================================
        // 3. LIVE SCREEN LINE NAVIGATION (STEP-BY-STEP VISUAL OVERLAY)
        // =========================================================================
        val navCard = createLiquidGlassCard("#A855F7").apply {
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        val navHeader = TextView(this).apply {
            text = "🧭 LIVE SCREEN LINE NAVIGATION (STEP-BY-STEP GUIDE)"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#C084FC"))
        }

        val navDesc = TextView(this).apply {
            text = "Draws real animated neon paths, arrows & glowing photon dots over your phone screen to guide you step-by-step."
            textSize = 12f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, dp(4), 0, dp(10))
        }

        val navBtnRow1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnGuideDisplay = createGlassButton("🔆 Guide: Display Settings", "#00F5D4", isPrimary = false).apply {
            setOnClickListener { startGuidedTask("display") }
        }
        val btnGuideWifi = createGlassButton("📶 Guide: Wi-Fi Setup", "#A855F7", isPrimary = false).apply {
            setOnClickListener { startGuidedTask("wifi") }
        }
        navBtnRow1.addView(btnGuideDisplay, tabParams1)
        navBtnRow1.addView(btnGuideWifi, tabParams2)

        val navBtnRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        val btnNextStep = createGlassButton("⏭️ Next Step", "#10B981", isPrimary = false).apply {
            setOnClickListener {
                val i = Intent(this@PermissionsAndSetupActivity, JarvisWakeWordService::class.java).apply {
                    action = JarvisWakeWordService.ACTION_NAV_NEXT_STEP
                }
                ContextCompat.startForegroundService(this@PermissionsAndSetupActivity, i)
            }
        }
        val btnStopNav = createGlassButton("⏹️ Stop Line Guide", "#EF4444", isPrimary = false).apply {
            setOnClickListener {
                val i = Intent(this@PermissionsAndSetupActivity, JarvisWakeWordService::class.java).apply {
                    action = JarvisWakeWordService.ACTION_NAV_STOP
                }
                ContextCompat.startForegroundService(this@PermissionsAndSetupActivity, i)
            }
        }
        navBtnRow2.addView(btnNextStep, tabParams1)
        navBtnRow2.addView(btnStopNav, tabParams2)

        navCard.addView(navHeader)
        navCard.addView(navDesc)
        navCard.addView(navBtnRow1)
        navCard.addView(navBtnRow2)
        contentRoot.addView(navCard, cardMarginParams(dp(14)))

        // =========================================================================
        // 4. PERMISSION, OVERLAY, BATTERY DOZE BYPASS & ACCESSIBILITY MATRIX
        // =========================================================================
        val permCard = createLiquidGlassCard("#10B981").apply {
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        val permHeader = TextView(this).apply {
            text = "🛡️ SYSTEM PERMISSIONS & 24/7 BACKGROUND DOZE BYPASS"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#10B981"))
        }

        permissionMatrixText = TextView(this).apply {
            textSize = 12.5f
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(0, dp(8), 0, dp(10))
        }

        val btnGrantRuntime = createGlassButton(
            "1. Grant Mic, Notifications, Contacts & Media",
            "#00F5D4",
            isPrimary = true
        ).apply {
            setOnClickListener { requestRuntimePermissions() }
        }

        val btnGrantOverlay = createGlassButton(
            "2. Enable Liquid Glass Dynamic Island Overlay",
            "#38BDF8",
            isPrimary = false
        ).apply {
            setOnClickListener { openOverlayPermissionSettings() }
        }

        val btnBatteryBypass = createGlassButton(
            "3. Bypass Battery Optimization (Prevent Doze Kill)",
            "#F59E0B",
            isPrimary = false
        ).apply {
            setOnClickListener { requestIgnoreBatteryOptimizations() }
        }

        val btnAccessibility = createGlassButton(
            "4. Enable Cross-App Accessibility & Screen Line Guide",
            "#A855F7",
            isPrimary = false
        ).apply {
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val serviceActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        val btnStartService = createGlassButton("▶️ Start 24/7 JARVIS", "#10B981", isPrimary = true).apply {
            setOnClickListener { startHandsFreeService() }
        }
        val btnStopService = createGlassButton("⏹️ Stop Service", "#EF4444", isPrimary = false).apply {
            setOnClickListener { stopHandsFreeService() }
        }
        serviceActionRow.addView(btnStartService, tabParams1)
        serviceActionRow.addView(btnStopService, tabParams2)

        permCard.addView(permHeader)
        permCard.addView(permissionMatrixText)
        permCard.addView(btnGrantRuntime, cardMarginParams(dp(6)))
        permCard.addView(btnGrantOverlay, cardMarginParams(dp(6)))
        permCard.addView(btnBatteryBypass, cardMarginParams(dp(6)))
        permCard.addView(btnAccessibility, cardMarginParams(dp(6)))
        permCard.addView(serviceActionRow)
        contentRoot.addView(permCard, cardMarginParams(dp(14)))

        // =========================================================================
        // 5. VOICE PERSONA, 24 INDIAN LANGUAGES & NEON THEME CUSTOMIZER
        // =========================================================================
        val customizerCard = createLiquidGlassCard("#F59E0B").apply {
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        val customHeader = TextView(this).apply {
            text = "🎨 VOICE PERSONA, 24 INDIAN LANGUAGES & NEON THEME"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FBBF24"))
        }

        val personaSpinner = Spinner(this).apply {
            setBackgroundColor(Color.parseColor("#1E293B"))
            adapter = ArrayAdapter(
                this@PermissionsAndSetupActivity,
                android.R.layout.simple_spinner_dropdown_item,
                voicePersonas.map { it.title }
            )
        }

        val btnApplyPersona = createGlassButton("🎙️ Apply Selected Voice Persona", "#F59E0B", isPrimary = false).apply {
            setOnClickListener {
                val selected = voicePersonas[personaSpinner.selectedItemPosition]
                val intent = Intent(this@PermissionsAndSetupActivity, JarvisWakeWordService::class.java).apply {
                    action = JarvisWakeWordService.ACTION_SET_PERSONA
                    putExtra(JarvisWakeWordService.EXTRA_PERSONA_ID, selected.id)
                    putExtra(JarvisWakeWordService.EXTRA_PERSONA_PITCH, selected.pitch)
                    putExtra(JarvisWakeWordService.EXTRA_PERSONA_RATE, selected.rate)
                }
                ContextCompat.startForegroundService(this@PermissionsAndSetupActivity, intent)
            }
        }

        val languageSpinner = Spinner(this).apply {
            setBackgroundColor(Color.parseColor("#1E293B"))
            adapter = ArrayAdapter(
                this@PermissionsAndSetupActivity,
                android.R.layout.simple_spinner_dropdown_item,
                indianLanguages.map { it.first }
            )
        }

        val btnApplyLanguage = createGlassButton("🇮🇳 Apply Selected Indian Language", "#00F5D4", isPrimary = false).apply {
            setOnClickListener {
                val selectedCode = indianLanguages[languageSpinner.selectedItemPosition].second
                val intent = Intent(this@PermissionsAndSetupActivity, JarvisWakeWordService::class.java).apply {
                    action = JarvisWakeWordService.ACTION_SET_LANGUAGE
                    putExtra(JarvisWakeWordService.EXTRA_LANGUAGE_BCP47, selectedCode)
                }
                ContextCompat.startForegroundService(this@PermissionsAndSetupActivity, intent)
            }
        }

        // Neon Theme Horizontal Pill Selector
        val themeScroll = HorizontalScrollView(this).apply {
            setPadding(0, dp(8), 0, 0)
        }
        val themeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((label, hex) in neonThemes) {
            val btnTheme = createGlassButton(label, hex, isPrimary = false).apply {
                setOnClickListener {
                    activeNeonHex = hex
                    val i = Intent(this@PermissionsAndSetupActivity, JarvisWakeWordService::class.java).apply {
                        action = JarvisWakeWordService.ACTION_SET_THEME
                        putExtra(JarvisWakeWordService.EXTRA_THEME_HEX, hex)
                    }
                    ContextCompat.startForegroundService(this@PermissionsAndSetupActivity, i)
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
            themeRow.addView(btnTheme, lp)
        }
        themeScroll.addView(themeRow)

        customizerCard.addView(customHeader)
        customizerCard.addView(personaSpinner, cardMarginParams(dp(8)))
        customizerCard.addView(btnApplyPersona, cardMarginParams(dp(6)))
        customizerCard.addView(languageSpinner, cardMarginParams(dp(10)))
        customizerCard.addView(btnApplyLanguage, cardMarginParams(dp(6)))
        customizerCard.addView(themeScroll)
        contentRoot.addView(customizerCard, cardMarginParams(dp(14)))

        nativeScrollContainer.addView(contentRoot)
        masterRoot.addView(nativeScrollContainer)
        masterRoot.addView(webHudContainer)
        setContentView(masterRoot)

        refreshPermissionStatusAndAutoStart()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(JarvisWakeWordService.ACTION_SERVICE_STATE_BROADCAST)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(serviceStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(serviceStateReceiver, filter)
        }
    }

    override fun onStop() {
        try {
            unregisterReceiver(serviceStateReceiver)
        } catch (_: Exception) {
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatusAndAutoStart()
    }

    private fun refreshPermissionStatusAndAutoStart() {
        val micGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val overlayGranted = Settings.canDrawOverlays(this)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryBypassed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }
        val accessibilityEnabled = JarvisDeviceAccessibilityService.isAccessibilityActive()

        permissionMatrixText.text = buildString {
            appendLine("• Microphone (24/7 Wake & Voice): ${if (micGranted) "✅ GRANTED" else "⚠️ REQUIRED (Tap #1)"}")
            appendLine("• Dynamic Island Overlay: ${if (overlayGranted) "✅ GRANTED" else "⚠️ REQUIRED (Tap #2)"}")
            appendLine("• Battery Doze Optimization Bypass: ${if (batteryBypassed) "✅ ACTIVE" else "⚠️ RECOMMENDED (Tap #3)"}")
            appendLine("• Cross-App Accessibility & Line Guide: ${if (accessibilityEnabled) "✅ ACTIVE" else "⚡ OPTIONAL (Tap #4)"}")
            append("• TTS Engine: Indian English (en-IN) Deep Male (Pitch 0.78, Rate 0.98)")
        }

        // Automatically start the 24/7 Foreground Service + Dynamic Island Pill as soon as Mic is granted!
        if (micGranted) {
            startHandsFreeService()
        }
    }

    private fun triggerWakeNow(honorific: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestRuntimePermissions()
            return
        }
        val intent = Intent(this, JarvisWakeWordService::class.java).apply {
            action = JarvisWakeWordService.ACTION_TRIGGER_WAKE_NOW
            putExtra(JarvisWakeWordService.EXTRA_HONORIFIC, honorific)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun sendCommandToService(commandText: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestRuntimePermissions()
            return
        }
        val intent = Intent(this, JarvisWakeWordService::class.java).apply {
            action = JarvisWakeWordService.ACTION_EXECUTE_TEXT_COMMAND
            putExtra(JarvisWakeWordService.EXTRA_TEXT_COMMAND, commandText)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun startGuidedTask(taskQuery: String) {
        if (!Settings.canDrawOverlays(this)) {
            openOverlayPermissionSettings()
            return
        }
        val intent = Intent(this, JarvisWakeWordService::class.java).apply {
            action = JarvisWakeWordService.ACTION_START_GUIDED_TASK
            putExtra(JarvisWakeWordService.EXTRA_TASK_QUERY, taskQuery)
        }
        ContextCompat.startForegroundService(this, intent)
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
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), REQ_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSIONS) {
            refreshPermissionStatusAndAutoStart()
        }
    }

    private fun openOverlayPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
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

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebHudLoaded() {
        if (hudWebView != null) return
        val webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#040711"))
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.grant(request.resources)
                }
            }
            loadUrl(HOSTED_HUD_URL)
        }
        hudWebView = webView
        webHudContainer.addView(webView)
    }

    private fun createLiquidGlassCard(strokeHex: String): LinearLayout {
        val d = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f * d
                setColor(Color.parseColor("#E60B1120"))
                setStroke((1.5f * d).toInt(), Color.parseColor(strokeHex))
            }
        }
    }

    private fun createGlassButton(label: String, accentHex: String, isPrimary: Boolean): Button {
        val d = resources.displayMetrics.density
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isPrimary) Color.parseColor("#040711") else Color.WHITE)
            setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14f * d
                if (isPrimary) {
                    setColor(Color.parseColor(accentHex))
                } else {
                    setColor(Color.parseColor("#151F32"))
                    setStroke((1.3f * d).toInt(), Color.parseColor(accentHex))
                }
            }
        }
    }

    private fun cardMarginParams(topMarginPx: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = topMarginPx
        }
    }
}
