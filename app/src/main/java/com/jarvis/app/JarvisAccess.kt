package com.jarvis.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccess : AccessibilityService() {
    companion object {
        var instance: JarvisAccess? = null
        var fg: String = ""
    }

    override fun onServiceConnected() { instance = this }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            e.packageName?.let { fg = it.toString() }
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }

    fun tapText(t: String): Boolean {
        val root = rootInActiveWindow ?: return false
        for (n in root.findAccessibilityNodeInfosByText(t)) {
            var x: AccessibilityNodeInfo? = n
            while (x != null) {
                if (x.isClickable) { x.performAction(AccessibilityNodeInfo.ACTION_CLICK); return true }
                x = x.parent
            }
        }
        return false
    }

    private fun findScrollable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null) return null
        if (n.isScrollable) return n
        for (i in 0 until n.childCount) findScrollable(n.getChild(i))?.let { return it }
        return null
    }

    fun scroll(forward: Boolean): Boolean {
        val s = findScrollable(rootInActiveWindow) ?: return false
        return s.performAction(if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    private fun collect(n: AccessibilityNodeInfo?, out: StringBuilder) {
        if (n == null || out.length > 400) return
        n.text?.let { out.append(it).append(". ") }
        for (i in 0 until n.childCount) collect(n.getChild(i), out)
    }

    fun screenText(): String {
        val sb = StringBuilder()
        collect(rootInActiveWindow, sb)
        return sb.toString().take(400)
    }
}
