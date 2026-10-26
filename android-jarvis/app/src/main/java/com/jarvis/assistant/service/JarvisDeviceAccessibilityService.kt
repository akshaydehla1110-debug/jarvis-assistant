package com.jarvis.assistant.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * User-enabled AccessibilityService for JARVIS ULTRA Live Line Navigation & Screen Understanding.
 *
 * Security & Privacy Guarantees:
 * - Only active after the user manually enables it in Android Settings.
 * - Never reads or captures password fields (node.isPassword == true is strictly skipped).
 * - Locates real clickable UI element bounds on screen so LiveLineNavigationOverlayView can draw
 *   accurate lines, arrows, and highlights.
 * - Returns null when an element cannot be reliably identified (e.g. custom game canvas), triggering
 *   the honest manual instruction fallback instead of drawing a fake line.
 */
class JarvisDeviceAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: JarvisDeviceAccessibilityService? = null
            private set

        @Volatile
        var currentForegroundPackage: String = "com.android.launcher3"
            private set

        fun findElementBounds(
            textQueries: List<String>,
            viewIdQueries: List<String> = emptyList()
        ): Rect? {
            val service = instance ?: return null
            val root = service.rootInActiveWindow ?: return null
            return searchNodeRecursive(root, textQueries, viewIdQueries)
        }

        fun summarizeVisibleScreenElements(): List<String> {
            val service = instance ?: return emptyList()
            val root = service.rootInActiveWindow ?: return emptyList()
            val labels = mutableListOf<String>()
            collectLabelsRecursive(root, labels)
            return labels.distinct().take(12)
        }

        private fun searchNodeRecursive(
            node: AccessibilityNodeInfo?,
            textQueries: List<String>,
            viewIdQueries: List<String>
        ): Rect? {
            if (node == null || !node.isVisibleToUser || node.isPassword) return null

            val nodeText = node.text?.toString().orEmpty()
            val nodeDesc = node.contentDescription?.toString().orEmpty()
            val nodeId = node.viewIdResourceName.orEmpty()

            val matchesText = textQueries.any { q ->
                nodeText.contains(q, ignoreCase = true) || nodeDesc.contains(q, ignoreCase = true)
            }
            val matchesId = viewIdQueries.any { id ->
                nodeId.contains(id, ignoreCase = true)
            }

            if (matchesText || matchesId) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (!rect.isEmpty && rect.width() > 16 && rect.height() > 16) {
                    return rect
                }
            }

            for (i in 0 until node.childCount) {
                val found = searchNodeRecursive(node.getChild(i), textQueries, viewIdQueries)
                if (found != null) return found
            }
            return null
        }

        private fun collectLabelsRecursive(node: AccessibilityNodeInfo?, out: MutableList<String>) {
            if (node == null || !node.isVisibleToUser || node.isPassword || out.size >= 16) return
            val text = node.text?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()
            if (!text.isNullOrBlank() && text.length <= 48) {
                out.add(text)
            } else if (!desc.isNullOrBlank() && desc.length <= 48) {
                out.add(desc)
            }
            for (i in 0 until node.childCount) {
                collectLabelsRecursive(node.getChild(i), out)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString()
        if (!pkg.isNullOrBlank() && pkg != "com.jarvis.assistant") {
            currentForegroundPackage = pkg
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }
}
