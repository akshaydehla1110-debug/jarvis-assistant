package com.jarvis.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * User-enabled AccessibilityService for JARVIS Liquid Glass Dynamic Island:
 * 1. Cross-App Voice Actions (Home, Back, Recents, Notifications, Click on-screen button by text, Scroll).
 * 2. Live Screen Line Navigation (Locates real clickable UI element bounds on screen so
 *    LiveLineNavigationOverlayView can draw accurate neon lines, arrows, and highlights).
 * 3. Privacy Guard: Never reads or captures password fields (node.isPassword == true is strictly skipped).
 */
class JarvisDeviceAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: JarvisDeviceAccessibilityService? = null
            private set

        @Volatile
        var currentForegroundPackage: String = "com.android.launcher3"
            private set

        fun isAccessibilityActive(): Boolean = instance != null

        fun performSystemGlobalAction(actionName: String): Boolean {
            val service = instance ?: return false
            return when (actionName.lowercase()) {
                "home" -> service.performGlobalAction(GLOBAL_ACTION_HOME)
                "back" -> service.performGlobalAction(GLOBAL_ACTION_BACK)
                "recents", "recent apps" -> service.performGlobalAction(GLOBAL_ACTION_RECENTS)
                "notifications" -> service.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                "quick settings" -> service.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
                "screenshot" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        service.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                    } else {
                        false
                    }
                }
                else -> false
            }
        }

        fun clickElementByLabel(targetLabel: String): Boolean {
            val service = instance ?: return false
            val root = service.rootInActiveWindow ?: return false
            val targetNode = findNodeByTextRecursive(root, targetLabel) ?: return false

            var clickableParent: AccessibilityNodeInfo? = targetNode
            while (clickableParent != null) {
                if (clickableParent.isClickable) {
                    return clickableParent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                clickableParent = clickableParent.parent
            }

            // Fallback: dispatch tap gesture at node center if API >= 24
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val bounds = Rect()
                targetNode.getBoundsInScreen(bounds)
                if (!bounds.isEmpty) {
                    val tapPath = Path().apply {
                        moveTo(bounds.exactCenterX(), bounds.exactCenterY())
                    }
                    val gesture = GestureDescription.Builder()
                        .addStroke(GestureDescription.StrokeDescription(tapPath, 0, 80))
                        .build()
                    return service.dispatchGesture(gesture, null, null)
                }
            }
            return false
        }

        fun scrollActiveWindow(scrollDown: Boolean): Boolean {
            val service = instance ?: return false
            val root = service.rootInActiveWindow ?: return false
            val scrollable = findScrollableNodeRecursive(root)
            if (scrollable != null) {
                val action = if (scrollDown) {
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                } else {
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                }
                return scrollable.performAction(action)
            }
            return false
        }

        fun findElementBounds(
            textQueries: List<String>,
            viewIdQueries: List<String> = emptyList()
        ): Rect? {
            val service = instance ?: return null
            val root = service.rootInActiveWindow ?: return null
            return searchNodeBoundsRecursive(root, textQueries, viewIdQueries)
        }

        fun summarizeVisibleScreenElements(): List<String> {
            val service = instance ?: return emptyList()
            val root = service.rootInActiveWindow ?: return emptyList()
            val labels = mutableListOf<String>()
            collectLabelsRecursive(root, labels)
            return labels.distinct().take(12)
        }

        private fun findNodeByTextRecursive(
            node: AccessibilityNodeInfo?,
            query: String
        ): AccessibilityNodeInfo? {
            if (node == null || !node.isVisibleToUser || node.isPassword) return null
            val text = node.text?.toString().orEmpty()
            val desc = node.contentDescription?.toString().orEmpty()
            if (text.contains(query, ignoreCase = true) || desc.contains(query, ignoreCase = true)) {
                return node
            }
            for (i in 0 until node.childCount) {
                val found = findNodeByTextRecursive(node.getChild(i), query)
                if (found != null) return found
            }
            return null
        }

        private fun findScrollableNodeRecursive(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null || !node.isVisibleToUser) return null
            if (node.isScrollable) return node
            for (i in 0 until node.childCount) {
                val found = findScrollableNodeRecursive(node.getChild(i))
                if (found != null) return found
            }
            return null
        }

        private fun searchNodeBoundsRecursive(
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
                val found = searchNodeBoundsRecursive(node.getChild(i), textQueries, viewIdQueries)
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
