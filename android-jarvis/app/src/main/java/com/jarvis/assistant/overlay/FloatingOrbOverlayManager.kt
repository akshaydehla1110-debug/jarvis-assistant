package com.jarvis.assistant.overlay

import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.jarvis.assistant.R
import kotlin.math.abs
import kotlin.math.sin

enum class OrbState {
    STANDBY_PILL,
    LISTENING,
    THINKING,
    SPEAKING,
    NAVIGATING,
    PAUSED,
    CONFIRMATION,
    COMPLETED,
    ERROR
}

/**
 * Custom real-time multi-bar audio waveform canvas view inside the expanded Dynamic Island.
 */
class WaveformVisualizerView(context: Context) : View(context) {
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#00F5D4")
    }
    private val barRect = RectF()
    private var currentRms: Float = 2.5f
    private var phase: Float = 0f
    private var isAnimating: Boolean = false

    private val waveAnimator = ValueAnimator.ofFloat(0f, 6.283f).apply {
        duration = 900L
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            phase = it.animatedValue as Float
            postInvalidateOnAnimation()
        }
    }

    fun setAccentColor(colorInt: Int) {
        barPaint.color = colorInt
        invalidate()
    }

    fun setActiveAmplitude(rmsdB: Float, active: Boolean) {
        currentRms = rmsdB.coerceIn(0.8f, 12f)
        if (active && !isAnimating) {
            isAnimating = true
            waveAnimator.start()
        } else if (!active && isAnimating) {
            isAnimating = false
            waveAnimator.cancel()
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val barCount = 18
        val totalW = width.toFloat().coerceAtLeast(100f)
        val totalH = height.toFloat().coerceAtLeast(24f)
        val spacing = totalW / (barCount * 1.8f)
        val barW = spacing * 0.95f
        val startX = (totalW - (barCount * (barW + spacing))) / 2f

        for (i in 0 until barCount) {
            val waveFactor = if (isAnimating) {
                val normalized = (currentRms / 10f).coerceIn(0.2f, 1.0f)
                val osc = (sin(phase + i * 0.55f) + 1f) / 2f
                (0.22f + normalized * 0.78f * osc).coerceIn(0.18f, 1.0f)
            } else {
                0.22f
            }
            val barH = (totalH * waveFactor).coerceAtLeast(6f)
            val left = startX + i * (barW + spacing)
            val top = (totalH - barH) / 2f
            barRect.set(left, top, left + barW, top + barH)
            canvas.drawRoundRect(barRect, barW / 2f, barW / 2f, barPaint)
        }
    }
}

/**
 * Liquid Glass Dynamic Island Floating Overlay managed via WindowManager and TYPE_APPLICATION_OVERLAY.
 *
 * Key Architecture:
 * - Default State: Sleek top-center compact floating pill ("JARVIS · Say 'Hey Jarvis'") that remains
 *   accessible across all apps without blocking normal touch interaction.
 * - Active State: Smoothly expands when the user says "Hey JARVIS" or taps the pill, displaying
 *   a glowing neon border, live multi-bar audio wave animation, spoken response text, and
 *   Step-by-Step Live Screen Line Navigation controls.
 */
class FloatingOrbOverlayManager(
    private val context: Context,
    private val onTapWakeTrigger: () -> Unit,
    private val onTapPauseOrCancel: () -> Unit,
    private val onOpenFullApp: () -> Unit,
    private val onStartQuickGuide: () -> Unit,
    private val onNavPrev: () -> Unit,
    private val onNavPauseResume: () -> Unit,
    private val onNavNext: () -> Unit,
    private val onNavStop: () -> Unit,
    private val onConfirmAction: () -> Unit,
    private val onCancelAction: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var rootView: View? = null
    private var rootContainer: LinearLayout? = null
    private var orbCoreDot: View? = null
    private var badgeTitleView: TextView? = null
    private var stateTagView: TextView? = null
    private var expandedSection: LinearLayout? = null
    private var waveformView: WaveformVisualizerView? = null
    private var transcriptView: TextView? = null
    private var subTextView: TextView? = null
    private var navRowView: LinearLayout? = null
    private var confirmRowView: LinearLayout? = null
    private var btnNavPauseResume: Button? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var currentState: OrbState = OrbState.STANDBY_PILL
    private var currentNeonColorHex: String = "#00F5D4"
    var autoCollapseTimeoutMs: Long = 6000L

    private val collapseToPillRunnable = Runnable {
        if (currentState != OrbState.NAVIGATING && currentState != OrbState.LISTENING) {
            collapseToCompactPill()
        }
    }

    fun setNeonThemeColor(hexColor: String) {
        currentNeonColorHex = hexColor
        applyNeonBorderStyling(currentState)
    }

    /**
     * Shows the default small sleek top-center Dynamic Island pill.
     */
    fun showCompactStandbyPill(statusLabel: String = "JARVIS · Say 'Hey Jarvis'") {
        if (!Settings.canDrawOverlays(context)) return
        mainHandler.removeCallbacks(collapseToPillRunnable)

        if (rootView == null) {
            createOverlayView()
        }

        currentState = OrbState.STANDBY_PILL
        badgeTitleView?.text = statusLabel
        stateTagView?.text = "24/7 READY"
        expandedSection?.visibility = View.GONE
        waveformView?.setActiveAmplitude(0f, false)
        applyNeonBorderStyling(OrbState.STANDBY_PILL)
    }

    fun collapseToCompactPill() {
        showCompactStandbyPill("JARVIS · Say 'Hey Jarvis'")
    }

    fun showOrUpdateOrb(
        state: OrbState,
        transcript: String,
        subtext: String,
        showNavigationControls: Boolean = false,
        isNavigationPaused: Boolean = false
    ) {
        if (!Settings.canDrawOverlays(context)) return
        mainHandler.removeCallbacks(collapseToPillRunnable)

        val isDeviceLocked = keyguardManager.isKeyguardLocked
        val safeTranscript = if (isDeviceLocked && state != OrbState.LISTENING && state != OrbState.SPEAKING) {
            "Unlock device to view private details"
        } else {
            transcript
        }

        if (rootView == null) {
            createOverlayView()
        }

        currentState = state
        badgeTitleView?.text = "JARVIS DYNAMIC ISLAND"
        stateTagView?.text = state.name
        expandedSection?.visibility = View.VISIBLE
        transcriptView?.text = safeTranscript
        subTextView?.text = subtext

        navRowView?.visibility = if (showNavigationControls || state == OrbState.NAVIGATING || state == OrbState.PAUSED) {
            View.VISIBLE
        } else {
            View.GONE
        }
        btnNavPauseResume?.text = if (isNavigationPaused || state == OrbState.PAUSED) "Resume" else "Pause"
        confirmRowView?.visibility = View.GONE

        val waveActive = state == OrbState.LISTENING || state == OrbState.SPEAKING || state == OrbState.THINKING
        waveformView?.setActiveAmplitude(if (state == OrbState.SPEAKING) 7.5f else 4.5f, waveActive)
        applyNeonBorderStyling(state)
    }

    fun showConfirmationPrompt(title: String, previewDetails: String) {
        if (!Settings.canDrawOverlays(context)) return
        mainHandler.removeCallbacks(collapseToPillRunnable)

        if (keyguardManager.isKeyguardLocked) {
            showOrUpdateOrb(
                state = OrbState.ERROR,
                transcript = "Unlock device to confirm sensitive action",
                subtext = "Protected operation blocked on lock screen"
            )
            scheduleAutoDismiss()
            return
        }

        if (rootView == null) {
            createOverlayView()
        }

        currentState = OrbState.CONFIRMATION
        badgeTitleView?.text = "SECURITY CONFIRMATION"
        stateTagView?.text = "CONFIRM"
        expandedSection?.visibility = View.VISIBLE
        transcriptView?.text = title
        subTextView?.text = previewDetails
        navRowView?.visibility = View.GONE
        confirmRowView?.visibility = View.VISIBLE
        waveformView?.setActiveAmplitude(5.0f, true)
        applyNeonBorderStyling(OrbState.CONFIRMATION)
    }

    fun updateWaveformAmplitude(rmsdB: Float) {
        waveformView?.setActiveAmplitude(rmsdB, true)
    }

    fun scheduleAutoDismiss(timeoutMs: Long = autoCollapseTimeoutMs) {
        mainHandler.removeCallbacks(collapseToPillRunnable)
        mainHandler.postDelayed(collapseToPillRunnable, timeoutMs)
    }

    fun dismissOrb() {
        mainHandler.removeCallbacks(collapseToPillRunnable)
        waveformView?.setActiveAmplitude(0f, false)
        val view = rootView ?: return
        try {
            windowManager.removeView(view)
        } catch (_: IllegalArgumentException) {
        } finally {
            rootView = null
            rootContainer = null
            waveformView = null
        }
    }

    private fun applyNeonBorderStyling(state: OrbState) {
        val accentColor = try {
            when (state) {
                OrbState.ERROR -> Color.parseColor("#F43F5E")
                OrbState.THINKING -> Color.parseColor("#A855F7")
                OrbState.COMPLETED -> Color.parseColor("#10B981")
                else -> Color.parseColor(currentNeonColorHex)
            }
        } catch (_: Exception) {
            Color.parseColor("#00F5D4")
        }

        val bgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = if (state == OrbState.STANDBY_PILL) 999f else 64f
            setColor(Color.parseColor("#EE050811"))
            setStroke(if (state == OrbState.STANDBY_PILL) 3 else 4, accentColor)
        }
        rootContainer?.background = bgDrawable

        val dotDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(accentColor)
        }
        orbCoreDot?.background = dotDrawable
        stateTagView?.setTextColor(accentColor)
        waveformView?.setAccentColor(accentColor)
    }

    private fun createOverlayView() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 28
        }
        layoutParams = params

        val inflated = LayoutInflater.from(context).inflate(R.layout.overlay_dynamic_island, null)
        rootContainer = inflated.findViewById(R.id.islandRootContainer)
        orbCoreDot = inflated.findViewById(R.id.orbCoreDot)
        badgeTitleView = inflated.findViewById(R.id.islandBadgeTitle)
        stateTagView = inflated.findViewById(R.id.islandStateTag)
        expandedSection = inflated.findViewById(R.id.expandedContentSection)
        transcriptView = inflated.findViewById(R.id.islandMainTranscript)
        subTextView = inflated.findViewById(R.id.islandSubText)
        navRowView = inflated.findViewById(R.id.navigationControlsRow)
        confirmRowView = inflated.findViewById(R.id.confirmationControlsRow)

        val waveHost = inflated.findViewById<FrameLayout>(R.id.waveformHostContainer)
        val wave = WaveformVisualizerView(context)
        waveHost.addView(
            wave,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        waveformView = wave

        inflated.findViewById<Button>(R.id.btnNavPrev).setOnClickListener { onNavPrev() }
        btnNavPauseResume = inflated.findViewById<Button>(R.id.btnNavPauseResume).apply {
            setOnClickListener { onNavPauseResume() }
        }
        inflated.findViewById<Button>(R.id.btnNavNext).setOnClickListener { onNavNext() }
        inflated.findViewById<Button>(R.id.btnNavStop).setOnClickListener { onNavStop() }

        inflated.findViewById<Button>(R.id.btnConfirmYes).setOnClickListener { onConfirmAction() }
        inflated.findViewById<Button>(R.id.btnConfirmCancel).setOnClickListener { onCancelAction() }

        inflated.findViewById<Button>(R.id.btnQuickSpeak).setOnClickListener { onTapWakeTrigger() }
        inflated.findViewById<Button>(R.id.btnQuickGuide).setOnClickListener { onStartQuickGuide() }
        inflated.findViewById<Button>(R.id.btnQuickCollapse).setOnClickListener { collapseToCompactPill() }

        attachDragAndTapListener(inflated, params)
        windowManager.addView(inflated, params)
        rootView = inflated
        applyNeonBorderStyling(currentState)
    }

    private fun attachDragAndTapListener(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var isDrag = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    isDrag = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > 12 || abs(dy) > 12) {
                        isDrag = true
                        params.x = initialX + dx
                        params.y = (initialY + dy).coerceAtLeast(12)
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDrag) {
                        if (currentState == OrbState.STANDBY_PILL) {
                            onTapWakeTrigger()
                        } else {
                            onTapPauseOrCancel()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        view.setOnLongClickListener {
            onOpenFullApp()
            true
        }
    }
}
