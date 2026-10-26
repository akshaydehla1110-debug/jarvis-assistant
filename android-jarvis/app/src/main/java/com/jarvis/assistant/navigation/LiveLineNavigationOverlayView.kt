package com.jarvis.assistant.navigation

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Real Android WindowManager screen-guidance canvas overlay.
 *
 * Key Architecture:
 * - Uses WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCHABLE so the visual line,
 *   glowing dot, and target circle NEVER block the user's real finger taps on the underlying app!
 * - Draws a smooth cubic Bezier neon path from the Dynamic Island pill to the detected AccessibilityNodeInfo
 *   target Rect on screen.
 * - Animates a glowing traveling photon dot along the path using PathMeasure + ValueAnimator.
 * - Honest Fallback: If targetBounds is null (element not reliably detected), it does NOT draw a fake line;
 *   instead it renders a clear top-center guidance banner instructing the user or offering manual target selection.
 */
class LiveLineNavigationOverlayView(context: Context) : View(context) {

    var primaryNeonColor: Int = Color.parseColor("#00F5D4") // Neon Cyan default; customizable to Purple #A855F7
    var secondaryNeonColor: Int = Color.parseColor("#A855F7")

    private var stepIndex: Int = 1
    private var totalSteps: Int = 1
    private var instructionText: String = ""
    private var targetBounds: Rect? = null
    private var originX: Float = 540f
    private var originY: Float = 160f
    private var dotProgress: Float = 0f

    private val linePath = Path()
    private val pathMeasure = PathMeasure()
    private val currentDotPos = FloatArray(2)
    private val currentDotTan = FloatArray(2)

    private val glowLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
        alpha = 90
    }

    private val coreLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5.5f
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(22f, 12f), 0f)
    }

    private val targetHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val dotGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E6090D16")
    }

    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
        isFakeBoldText = true
    }

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1500L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            dotProgress = it.animatedValue as Float
            postInvalidateOnAnimation()
        }
    }

    fun updateGuidanceStep(
        step: Int,
        total: Int,
        instruction: String,
        detectedBounds: Rect?,
        islandCenterX: Float,
        islandBottomY: Float,
        neonColorHex: String = "#00F5D4"
    ) {
        stepIndex = step
        totalSteps = total
        instructionText = instruction
        targetBounds = detectedBounds
        originX = islandCenterX
        originY = islandBottomY

        try {
            primaryNeonColor = Color.parseColor(neonColorHex)
        } catch (_: Exception) {
            primaryNeonColor = Color.parseColor("#00F5D4")
        }

        glowLinePaint.color = primaryNeonColor
        coreLinePaint.color = primaryNeonColor
        targetHighlightPaint.color = primaryNeonColor
        dotGlowPaint.color = primaryNeonColor

        if (!animator.isStarted) {
            animator.start()
        }
        invalidate()
    }

    fun stopAnimationAndClear() {
        animator.cancel()
        targetBounds = null
        instructionText = ""
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (instructionText.isBlank()) return

        val bounds = targetBounds
        if (bounds == null || bounds.isEmpty) {
            // Honest Fallback: Target UI element could not be reliably located via AccessibilityNodeInfo.
            // Do NOT draw a fake line; show an honest manual instruction banner.
            val bannerRect = RectF(48f, originY + 30f, width - 48f, originY + 150f)
            canvas.drawRoundRect(bannerRect, 28f, 28f, badgeBgPaint)
            canvas.drawRoundRect(bannerRect, 28f, 28f, targetHighlightPaint)
            canvas.drawText(
                "Step $stepIndex/$totalSteps: $instructionText (Tap manually)",
                80f,
                originY + 102f,
                badgeTextPaint
            )
            return
        }

        val targetCenterX = bounds.exactCenterX()
        val targetCenterY = bounds.exactCenterY()

        // 1. Construct smooth cubic Bezier path from Dynamic Island to target UI control
        linePath.reset()
        linePath.moveTo(originX, originY)
        val controlY1 = originY + (targetCenterY - originY) * 0.38f
        val controlY2 = originY + (targetCenterY - originY) * 0.72f
        linePath.cubicTo(originX, controlY1, targetCenterX, controlY2, targetCenterX, bounds.top.toFloat() - 12f)

        // 2. Draw outer neon glow and dashed core line
        canvas.drawPath(linePath, glowLinePaint)
        canvas.drawPath(linePath, coreLinePaint)

        // 3. Draw moving glowing dot along the path
        pathMeasure.setPath(linePath, false)
        val distance = pathMeasure.length * dotProgress
        if (pathMeasure.getPosTan(distance, currentDotPos, currentDotTan)) {
            dotGlowPaint.alpha = 85
            canvas.drawCircle(currentDotPos[0], currentDotPos[1], 24f, dotGlowPaint)
            dotGlowPaint.alpha = 255
            canvas.drawCircle(currentDotPos[0], currentDotPos[1], 10f, dotGlowPaint)
        }

        // 4. Draw directional arrowhead at target entry point
        drawArrowHead(
            canvas,
            targetCenterX,
            bounds.top.toFloat() - 8f,
            atan2((bounds.top - controlY2).toDouble(), 0.0).toFloat()
        )

        // 5. Highlight target UI element bounds with pulsing neon rounded rectangle
        val pad = 12f
        val highlightRect = RectF(
            bounds.left - pad,
            bounds.top - pad,
            bounds.right + pad,
            bounds.bottom + pad
        )
        canvas.drawRoundRect(highlightRect, 24f, 24f, glowLinePaint)
        canvas.drawRoundRect(highlightRect, 24f, 24f, targetHighlightPaint)

        // 6. Draw numbered Step Pill ("Step X: Instruction") adjacent to the target
        val label = "Step $stepIndex: $instructionText"
        val textWidth = badgeTextPaint.measureText(label)
        val pillLeft = (targetCenterX - textWidth / 2f - 28f).coerceIn(24f, (width - textWidth - 80f).coerceAtLeast(24f))
        val pillTop = (bounds.top - 96f).coerceAtLeast(originY + 20f)
        val pillRect = RectF(pillLeft, pillTop, pillLeft + textWidth + 56f, pillTop + 68f)

        canvas.drawRoundRect(pillRect, 34f, 34f, badgeBgPaint)
        canvas.drawRoundRect(pillRect, 34f, 34f, targetHighlightPaint)
        canvas.drawText(label, pillLeft + 28f, pillTop + 45f, badgeTextPaint)
    }

    private fun drawArrowHead(canvas: Canvas, tipX: Float, tipY: Float, angleRad: Float) {
        val arrowSize = 24f
        val angle1 = angleRad - Math.toRadians(145.0).toFloat()
        val angle2 = angleRad + Math.toRadians(145.0).toFloat()

        val x1 = tipX + arrowSize * cos(angle1)
        val y1 = tipY + arrowSize * sin(angle1)
        val x2 = tipX + arrowSize * cos(angle2)
        val y2 = tipY + arrowSize * sin(angle2)

        val arrowPath = Path().apply {
            moveTo(tipX, tipY)
            lineTo(x1, y1)
            moveTo(tipX, tipY)
            lineTo(x2, y2)
        }
        canvas.drawPath(arrowPath, targetHighlightPaint)
    }
}

/**
 * Manages attaching/detaching LiveLineNavigationOverlayView on WindowManager.
 */
class LiveNavigationOverlayController(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: LiveLineNavigationOverlayView? = null

    fun showStepOverlay(
        step: Int,
        totalSteps: Int,
        instruction: String,
        targetBounds: Rect?,
        neonColorHex: String = "#00F5D4"
    ) {
        if (!Settings.canDrawOverlays(context)) return

        if (overlayView == null) {
            val view = LiveLineNavigationOverlayView(context)
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            // FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE ensures all user taps pass directly to the app underneath
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            windowManager.addView(view, params)
            overlayView = view
        }

        val displayMetrics = context.resources.displayMetrics
        val centerX = displayMetrics.widthPixels / 2f
        overlayView?.updateGuidanceStep(
            step = step,
            total = totalSteps,
            instruction = instruction,
            detectedBounds = targetBounds,
            islandCenterX = centerX,
            islandBottomY = 175f,
            neonColorHex = neonColorHex
        )
    }

    fun hideOverlay() {
        val view = overlayView ?: return
        view.stopAnimationAndClear()
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
        } finally {
            overlayView = null
        }
    }
}
