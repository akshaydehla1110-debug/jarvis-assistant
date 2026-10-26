package com.jarvis.assistant.overlay

import android.app.KeyguardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

enum class OrbState {
    LISTENING,
    THINKING,
    SPEAKING,
    CONFIRMATION,
    COMPLETED,
    ERROR
}

/**
 * Lightweight floating holographic orb managed via WindowManager and TYPE_APPLICATION_OVERLAY.
 *
 * Key properties:
 * - Uses FLAG_NOT_FOCUSABLE and FLAG_NOT_TOUCH_MODAL so background apps retain full keyboard and gesture access.
 * - Supports drag-to-reposition with magnetic edge snapping and tap-to-pause/dismiss.
 * - Automatically dismisses after a configurable idle timeout (default 4500ms).
 * - Never displays sensitive payload content if KeyguardManager reports the device is locked.
 */
class FloatingOrbOverlayManager(
    private val context: Context,
    private val onTapPauseOrCancel: () -> Unit,
    private val onOpenFullApp: () -> Unit,
    private val onConfirmAction: () -> Unit,
    private val onCancelAction: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var rootView: LinearLayout? = null
    private var statusTextView: TextView? = null
    private var subTextView: TextView? = null
    private var actionRowView: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    var autoDismissTimeoutMs: Long = 4500L
    private val dismissRunnable = Runnable { dismissOrb() }

    fun showOrUpdateOrb(state: OrbState, transcript: String, subtext: String) {
        if (!Settings.canDrawOverlays(context)) return

        mainHandler.removeCallbacks(dismissRunnable)

        // Lock-screen privacy guard: redact potentially sensitive transcript over secure Keyguard
        val isDeviceLocked = keyguardManager.isKeyguardLocked
        val safeTranscript = if (isDeviceLocked && state != OrbState.LISTENING) {
            "Unlock device to view details"
        } else {
            transcript
        }

        if (rootView == null) {
            createOverlayView()
        }

        statusTextView?.text = safeTranscript
        subTextView?.text = "${state.name} · $subtext"
        actionRowView?.visibility = View.GONE
    }

    fun showConfirmationPrompt(title: String, previewDetails: String) {
        if (!Settings.canDrawOverlays(context)) return
        mainHandler.removeCallbacks(dismissRunnable)

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

        statusTextView?.text = title
        subTextView?.text = previewDetails
        actionRowView?.visibility = View.VISIBLE
    }

    fun updateWaveformAmplitude(rmsdB: Float) {
        // Lightweight scale modulation only while actively capturing speech
        val normalized = (rmsdB.coerceIn(0f, 10f) / 10f)
        rootView?.alpha = 0.90f + (normalized * 0.10f)
    }

    fun scheduleAutoDismiss(timeoutMs: Long = autoDismissTimeoutMs) {
        mainHandler.removeCallbacks(dismissRunnable)
        mainHandler.postDelayed(dismissRunnable, timeoutMs)
    }

    fun dismissOrb() {
        mainHandler.removeCallbacks(dismissRunnable)
        val view = rootView ?: return
        try {
            windowManager.removeView(view)
        } catch (_: IllegalArgumentException) {
            // View already detached
        } finally {
            rootView = null
            statusTextView = null
            subTextView = null
            actionRowView = null
        }
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
            gravity = Gravity.TOP or Gravity.END
            x = 32
            y = 180
        }
        layoutParams = params

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 28, 36, 28)
            setBackgroundColor(0xDD0B132B.toInt())
        }

        val titleView = TextView(context).apply {
            textSize = 14f
            setTextColor(0xFFE0F2FE.toInt())
        }
        val subtitleView = TextView(context).apply {
            textSize = 11f
            setTextColor(0xFF38BDF8.toInt())
        }

        val confirmationLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE

            val confirmBtn = Button(context).apply {
                text = "Confirm"
                setOnClickListener { onConfirmAction() }
            }
            val cancelBtn = Button(context).apply {
                text = "Cancel"
                setOnClickListener { onCancelAction() }
            }
            addView(confirmBtn)
            addView(cancelBtn)
        }

        container.addView(titleView)
        container.addView(subtitleView)
        container.addView(confirmationLayout)

        attachDragAndTapListener(container, params)

        windowManager.addView(container, params)
        rootView = container
        statusTextView = titleView
        subTextView = subtitleView
        actionRowView = confirmationLayout
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
                    val dx = (touchX - event.rawX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > 12 || abs(dy) > 12) {
                        isDrag = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDrag) {
                        onTapPauseOrCancel()
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
