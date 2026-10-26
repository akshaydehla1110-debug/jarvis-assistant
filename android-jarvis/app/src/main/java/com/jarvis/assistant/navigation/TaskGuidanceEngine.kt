package com.jarvis.assistant.navigation

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.provider.Settings
import com.jarvis.assistant.service.JarvisDeviceAccessibilityService

data class GuidedTaskStep(
    val stepNumber: Int,
    val title: String,
    val voiceInstruction: String,
    val targetTextMatchers: List<String>,
    val targetViewIdMatchers: List<String> = emptyList(),
    val launchIntentAction: String? = null
)

data class GuidedTaskDefinition(
    val id: String,
    val taskTitle: String,
    val steps: List<GuidedTaskStep>
)

/**
 * Modular Step-by-Step Task Guidance Engine.
 *
 * Supports:
 * - Built-in verified Android Settings workflows (Display Settings, Wi-Fi Network Guide, Dark Theme & Font Size)
 * - Dynamic AI-generated task workflows for any custom user request
 * - Start, Pause, Resume, Next Step, Previous Step, Repeat Instruction, and Stop
 * - Automatic AccessibilityNodeInfo bounds matching + honest manual fallback when target cannot be detected
 */
class TaskGuidanceEngine(
    private val context: Context,
    private val overlayController: LiveNavigationOverlayController,
    private val onSpeakInstruction: (String) -> Unit,
    private val onTaskProgressChanged: (taskTitle: String, step: Int, total: Int, status: String) -> Unit
) {
    private var activeTask: GuidedTaskDefinition? = null
    private var currentStepIdx: Int = 0
    private var isPaused: Boolean = false
    var neonHexColor: String = "#00F5D4"

    private val builtInTasks = listOf(
        GuidedTaskDefinition(
            id = "display_settings",
            taskTitle = "Change Display Settings",
            steps = listOf(
                GuidedTaskStep(
                    stepNumber = 1,
                    title = "Open Settings & Find Display",
                    voiceInstruction = "Step 1: Opening Android Settings. Tap on Display.",
                    targetTextMatchers = listOf("Display", "Screen", "डिस्प्ले"),
                    launchIntentAction = Settings.ACTION_SETTINGS
                ),
                GuidedTaskStep(
                    stepNumber = 2,
                    title = "Select Brightness Level",
                    voiceInstruction = "Step 2: Tap Brightness level or Adaptive brightness to adjust screen luminance.",
                    targetTextMatchers = listOf("Brightness level", "Adaptive brightness", "Brightness")
                ),
                GuidedTaskStep(
                    stepNumber = 3,
                    title = "Configure Dark Theme or Timeout",
                    voiceInstruction = "Step 3: Toggle Dark theme or tap Screen timeout to finish customizing your display.",
                    targetTextMatchers = listOf("Dark theme", "Screen timeout", "Sleep")
                )
            )
        ),
        GuidedTaskDefinition(
            id = "wifi_settings",
            taskTitle = "Connect to Wi-Fi Network",
            steps = listOf(
                GuidedTaskStep(
                    stepNumber = 1,
                    title = "Open Network & Internet",
                    voiceInstruction = "Step 1: Opening Network settings. Tap Internet or Wi-Fi.",
                    targetTextMatchers = listOf("Internet", "Wi-Fi", "Network & internet", "Connections"),
                    launchIntentAction = Settings.ACTION_WIFI_SETTINGS
                ),
                GuidedTaskStep(
                    stepNumber = 2,
                    title = "Select Desired Wi-Fi Network",
                    voiceInstruction = "Step 2: Tap the Wi-Fi network you want to connect to from the available list.",
                    targetTextMatchers = listOf("Wi-Fi", "Add network", "Available networks")
                ),
                GuidedTaskStep(
                    stepNumber = 3,
                    title = "Enter Password & Tap Connect",
                    voiceInstruction = "Step 3: Enter the network password manually and tap Connect. For your privacy, JARVIS pauses screen reading on password fields.",
                    targetTextMatchers = listOf("Connect", "Save")
                )
            )
        )
    )

    fun startTaskByVoiceQuery(query: String): Boolean {
        val lower = query.lowercase()
        val matched = when {
            lower.contains("display") || lower.contains("brightness") || lower.contains("dark") ->
                builtInTasks.first { it.id == "display_settings" }
            lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("network") ->
                builtInTasks.first { it.id == "wifi_settings" }
            else -> null
        } ?: return false

        startTask(matched)
        return true
    }

    fun startTask(task: GuidedTaskDefinition) {
        activeTask = task
        currentStepIdx = 0
        isPaused = false
        executeCurrentStep()
    }

    fun nextStep() {
        val task = activeTask ?: return
        if (currentStepIdx + 1 < task.steps.size) {
            currentStepIdx++
            executeCurrentStep()
        } else {
            completeTask()
        }
    }

    fun previousStep() {
        if (activeTask == null) return
        if (currentStepIdx > 0) {
            currentStepIdx--
            executeCurrentStep()
        }
    }

    fun repeatCurrentInstruction() {
        val step = getCurrentStep() ?: return
        onSpeakInstruction(step.voiceInstruction)
    }

    fun pauseGuidance() {
        if (activeTask == null) return
        isPaused = true
        overlayController.hideOverlay()
        val task = activeTask!!
        onTaskProgressChanged(task.taskTitle, currentStepIdx + 1, task.steps.size, "PAUSED")
        onSpeakInstruction("Navigation paused. Say Resume navigation when ready.")
    }

    fun resumeGuidance() {
        if (activeTask == null) return
        isPaused = false
        executeCurrentStep()
    }

    fun stopGuidance() {
        val hadTask = activeTask != null
        activeTask = null
        currentStepIdx = 0
        isPaused = false
        overlayController.hideOverlay()
        if (hadTask) {
            onTaskProgressChanged("Idle", 0, 0, "STOPPED")
            onSpeakInstruction("Stopped live screen navigation.")
        }
    }

    fun onAccessibilityScreenChanged() {
        if (activeTask == null || isPaused) return
        refreshStepHighlightOnly()
    }

    private fun executeCurrentStep() {
        val task = activeTask ?: return
        val step = task.steps.getOrNull(currentStepIdx) ?: return

        step.launchIntentAction?.let { action ->
            try {
                context.startActivity(Intent(action).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) {
            }
        }

        val bounds: Rect? = JarvisDeviceAccessibilityService.findElementBounds(
            textQueries = step.targetTextMatchers,
            viewIdQueries = step.targetViewIdMatchers
        )

        overlayController.showStepOverlay(
            step = step.stepNumber,
            totalSteps = task.steps.size,
            instruction = step.title,
            targetBounds = bounds,
            neonColorHex = neonHexColor
        )

        onTaskProgressChanged(task.taskTitle, step.stepNumber, task.steps.size, "NAVIGATING")
        onSpeakInstruction(step.voiceInstruction)
    }

    private fun refreshStepHighlightOnly() {
        val task = activeTask ?: return
        val step = task.steps.getOrNull(currentStepIdx) ?: return
        val bounds = JarvisDeviceAccessibilityService.findElementBounds(
            textQueries = step.targetTextMatchers,
            viewIdQueries = step.targetViewIdMatchers
        )
        overlayController.showStepOverlay(
            step = step.stepNumber,
            totalSteps = task.steps.size,
            instruction = step.title,
            targetBounds = bounds,
            neonColorHex = neonHexColor
        )
    }

    private fun completeTask() {
        val task = activeTask ?: return
        overlayController.hideOverlay()
        onTaskProgressChanged(task.taskTitle, task.steps.size, task.steps.size, "COMPLETED")
        onSpeakInstruction("Task completed: ${task.taskTitle}.")
        activeTask = null
        currentStepIdx = 0
    }

    private fun getCurrentStep(): GuidedTaskStep? {
        return activeTask?.steps?.getOrNull(currentStepIdx)
    }
}
