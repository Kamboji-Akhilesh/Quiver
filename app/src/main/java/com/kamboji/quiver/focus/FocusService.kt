package com.kamboji.quiver.focus

import com.kamboji.quiver.R

import com.kamboji.quiver.ui.locale.tr
import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * Kicks the user out of YouTube Shorts / Instagram Reels the moment the player
 * appears. Privacy is enforced by the OS, not by promise: `focus_service.xml`
 * limits `packageNames` to those two apps, so Android never delivers this
 * service an event from anything else. Inside them it only asks "is a
 * view with this id visible?" — it never reads text, and stores only counts.
 */
class FocusService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastCheck = 0L
    private var lastActionAt = 0L
    private var lastAction = FocusPolicy.Action.None

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val target = FocusTarget.forPackage(event.packageName?.toString()) ?: return
        // Content-changed fires many times a second during playback; a few checks a second is plenty.
        if (SystemClock.uptimeMillis() - lastCheck < CHECK_INTERVAL_MS) return
        check(target)
    }

    private fun check(target: FocusTarget) {
        val now = SystemClock.uptimeMillis()
        lastCheck = now

        val prefs = FocusPrefs(this)
        if (!prefs.isBlocked(target)) return
        val root = rootInActiveWindow ?: return
        if (root.packageName != target.pkg) return // already left the app
        if (Log.isLoggable(TAG, Log.VERBOSE)) Log.v(TAG, "${target.pkg} ids: ${visibleIds(root)}")
        if (!isShowing(root, target)) return

        val action = FocusPolicy.decide(now, lastActionAt, lastAction, prefs.closeApp)
        if (action == FocusPolicy.Action.None) return
        if (FocusPolicy.isNewBlock(now, lastActionAt, lastAction)) {
            prefs.recordBlock()
            Toast.makeText(this, tr(R.string.focussvc_x_blocked_by_quiver, target.label), Toast.LENGTH_SHORT).show()
        }
        performGlobalAction(if (action == FocusPolicy.Action.Home) GLOBAL_ACTION_HOME else GLOBAL_ACTION_BACK)
        lastActionAt = now
        lastAction = action
        // A paused/static feed fires no further events, so verify Back worked rather than wait for one.
        if (action == FocusPolicy.Action.Back) handler.postDelayed({ check(target) }, FocusPolicy.SETTLE_MS + 100)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // Visible-only: apps keep the player alive off-screen in the back stack (e.g. after switching tabs).
    private fun isShowing(root: AccessibilityNodeInfo, target: FocusTarget): Boolean =
        target.fullIds.any { id -> root.findAccessibilityNodeInfosByViewId(id).any { it.isVisibleToUser } }

    /** Diagnostics for updating [FocusTarget.viewIds]: resource ids only, never text. */
    private fun visibleIds(node: AccessibilityNodeInfo?, out: MutableSet<String> = sortedSetOf()): Set<String> {
        if (node == null || !node.isVisibleToUser) return out
        node.viewIdResourceName?.let { out += it.substringAfter(":id/") }
        for (i in 0 until node.childCount) visibleIds(node.getChild(i), out)
        return out
    }

    override fun onInterrupt() = Unit

    private companion object {
        const val TAG = "QuiverFocus"
        const val CHECK_INTERVAL_MS = 250L
    }
}
