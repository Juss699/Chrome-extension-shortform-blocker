package io.github.juss699.shortformblocker

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * Watches the YouTube and Instagram apps (the only packages it is subscribed
 * to, see res/xml/blocker_service.xml) and presses Back as soon as the
 * Shorts or Reels player appears on screen.
 */
class BlockerService : AccessibilityService() {

    private lateinit var prefs: Prefs

    private var lastActionAt = 0L
    private var burstStartedAt = 0L
    private var burstActions = 0
    private val lastSnapshotAt = mutableMapOf<Platform, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val platform = Platform.forPackage(event?.packageName) ?: return
        if (!::prefs.isInitialized) prefs = Prefs(this)

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != platform.packageName) return

        if (prefs.diagnostics) recordSnapshot(platform, root)
        if (!prefs.isBlocking(platform) || prefs.isPaused) return
        if (isPlayerOnScreen(platform, root)) leavePlayer(platform)
    }

    private fun isPlayerOnScreen(platform: Platform, root: AccessibilityNodeInfo): Boolean =
        platform.fullViewIds.any { id ->
            root.findAccessibilityNodeInfosByViewId(id).any { it.isVisibleToUser }
        }

    private fun leavePlayer(platform: Platform) {
        val now = SystemClock.elapsedRealtime()
        // The player stays on screen for a moment while Back animates; don't
        // press it again for every frame.
        if (now - lastActionAt < COOLDOWN_MS) return

        if (now - burstStartedAt > BURST_WINDOW_MS) {
            burstStartedAt = now
            burstActions = 0
        }
        burstActions++
        lastActionAt = now

        // Back returns to wherever the player was opened from. If the player is
        // still there after a couple of tries (e.g. the app opened straight
        // into it), go to the home screen instead.
        performGlobalAction(if (burstActions <= MAX_BACKS) GLOBAL_ACTION_BACK else GLOBAL_ACTION_HOME)

        if (burstActions == 1) {
            prefs.incrementBlockCount(platform)
            Toast.makeText(this, getString(R.string.blocked_toast, platform.itemName), Toast.LENGTH_SHORT).show()
        }
    }

    private fun recordSnapshot(platform: Platform, root: AccessibilityNodeInfo) {
        val now = SystemClock.elapsedRealtime()
        if (now - (lastSnapshotAt[platform] ?: 0L) < SNAPSHOT_INTERVAL_MS) return
        lastSnapshotAt[platform] = now
        val snapshot = Diagnostics.snapshot(platform, root)
        prefs.setDiagnosticsLog(platform, Diagnostics.append(prefs.diagnosticsLog(platform), snapshot))
    }

    override fun onInterrupt() = Unit

    private companion object {
        const val COOLDOWN_MS = 800L
        const val BURST_WINDOW_MS = 5_000L
        const val MAX_BACKS = 2
        const val SNAPSHOT_INTERVAL_MS = 2_000L
    }
}
