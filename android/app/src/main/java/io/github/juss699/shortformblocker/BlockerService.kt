package io.github.juss699.shortformblocker

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * Watches the YouTube and Instagram apps (the only packages it is subscribed
 * to, see res/xml/blocker_service.xml).
 *
 * - Hides the Shorts/Reels button in the bottom navigation bar by covering
 *   it (see [TabCovers]).
 * - Optionally ([Prefs.closePlayers]), when the full-screen Shorts/Reels
 *   player opens some other way, it pauses playback and presses Back, which
 *   returns to wherever the player was opened from. If the player shrinks
 *   into the app's miniplayer or a picture-in-picture window instead of
 *   closing, it is paused there. It never presses Home: leaving an app while
 *   a video plays is exactly what sends YouTube into picture-in-picture.
 */
class BlockerService : AccessibilityService() {

    private enum class Player { NONE, MINIMIZED, FULL_SCREEN }

    private lateinit var prefs: Prefs
    private lateinit var audio: AudioManager
    private lateinit var covers: TabCovers
    private val handler = Handler(Looper.getMainLooper())

    private var lastActionAt = 0L
    private var burstStartedAt = 0L
    private var burstActions = 0
    private val minimizedPlayers = mutableSetOf<Platform>()
    private val lastSnapshotAt = mutableMapOf<Platform, Long>()

    private var focusRequest: AudioFocusRequest? = null
    private val releaseFocus = Runnable { focusRequest?.let { audio.abandonAudioFocusRequest(it) } }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ensureInitialized()
    }

    private fun ensureInitialized() {
        if (!::prefs.isInitialized) prefs = Prefs(this)
        if (!::audio.isInitialized) audio = getSystemService(AUDIO_SERVICE) as AudioManager
        if (!::covers.isInitialized) {
            covers = TabCovers(
                service = this,
                shouldCover = { platform -> prefs.isBlocking(platform) && !prefs.isPaused },
                onBlockedTap = { platform ->
                    prefs.incrementBlockCount(platform)
                    Toast.makeText(this, getString(R.string.tab_blocked_toast, platform.tabLabel), Toast.LENGTH_SHORT).show()
                },
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val platform = Platform.forPackage(event?.packageName) ?: return
        ensureInitialized()

        val tabCovered = covers.update(platform)

        val front = rootInActiveWindow?.takeIf { it.packageName?.toString() == platform.packageName }
        val frontPlayer = front?.let { playerIn(platform, it) } ?: Player.NONE
        if (front != null && prefs.diagnostics) recordSnapshot(platform, front, frontPlayer, tabCovered)

        if (!prefs.isBlocking(platform) || prefs.isPaused || !prefs.closePlayers) return

        if (frontPlayer == Player.FULL_SCREEN) {
            leavePlayer(platform)
            return
        }

        // Is the player still around, shrunk into a miniplayer or a
        // picture-in-picture window (which is not the active window)?
        val shrunk = frontPlayer == Player.MINIMIZED || windows.any { window ->
            val root = window.root
            root != null &&
                root.packageName?.toString() == platform.packageName &&
                playerIn(platform, root) != Player.NONE
        }
        // Pause once each time it shows up shrunk, rather than on every event,
        // so music playing in another app isn't paused over and over.
        if (shrunk && minimizedPlayers.add(platform)) pausePlayback()
        if (!shrunk) minimizedPlayers.remove(platform)
    }

    /** Whether the Shorts/Reels player is in this window, and how big it is. */
    private fun playerIn(platform: Platform, root: AccessibilityNodeInfo): Player {
        val bounds = Rect()
        var tallest = 0
        for (id in platform.fullViewIds) {
            for (node in root.findAccessibilityNodeInfosByViewId(id)) {
                if (!node.isVisibleToUser) continue
                node.getBoundsInScreen(bounds)
                tallest = maxOf(tallest, bounds.height())
            }
        }
        if (tallest == 0) return Player.NONE
        val screenHeight = resources.displayMetrics.heightPixels
        return if (tallest >= screenHeight * FULL_SCREEN_FRACTION) Player.FULL_SCREEN else Player.MINIMIZED
    }

    private fun leavePlayer(platform: Platform) {
        val now = SystemClock.elapsedRealtime()
        // After acting, let the app finish its transition before deciding the
        // player is still there; pressing Back again too early leaves the app.
        if (now - lastActionAt < SETTLE_MS) return

        if (now - burstStartedAt > BURST_WINDOW_MS) {
            burstStartedAt = now
            burstActions = 0
        }
        burstActions++
        lastActionAt = now

        pausePlayback()
        // Give the pause a moment to land before navigating.
        when (burstActions) {
            1, 2 -> handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, PAUSE_FIRST_MS)
            // Back didn't close it: open the app's home feed instead, which
            // stays inside the app.
            3 -> handler.postDelayed({
                if (!openAppHome(platform)) performGlobalAction(GLOBAL_ACTION_BACK)
            }, PAUSE_FIRST_MS)
            // Still there: leave it paused rather than throw the user out.
            else -> Unit
        }

        if (burstActions == 1) {
            prefs.incrementBlockCount(platform)
            Toast.makeText(this, getString(R.string.blocked_toast, platform.itemName), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Stops the Short/Reel that is playing, in two independent ways:
     * a media "pause" key, which goes to the playing app's media session, and
     * briefly taking audio focus, which players respond to by pausing. Taking
     * permanent focus means they don't resume when it's released.
     */
    private fun pausePlayback() {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))

        val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build(),
            )
            .setOnAudioFocusChangeListener { }
            .build()
            .also { focusRequest = it }
        if (audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            handler.removeCallbacks(releaseFocus)
            handler.postDelayed(releaseFocus, FOCUS_HOLD_MS)
        }
    }

    /** Opens the app's own home feed. Returns false if the app can't handle it. */
    private fun openAppHome(platform: Platform): Boolean =
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(platform.homeUrl))
                    .setPackage(platform.packageName)
                    // NO_USER_ACTION: don't count as the user leaving, which
                    // could otherwise trigger picture-in-picture.
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            )
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }

    private fun recordSnapshot(platform: Platform, root: AccessibilityNodeInfo, player: Player, tabCovered: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (now - (lastSnapshotAt[platform] ?: 0L) < SNAPSHOT_INTERVAL_MS) return
        lastSnapshotAt[platform] = now
        val status = "tab=${if (tabCovered) "covered" else "not found"} player=${player.name.lowercase()}"
        val snapshot = Diagnostics.snapshot(platform, root, status, resources.displayMetrics.heightPixels)
        prefs.setDiagnosticsLog(platform, Diagnostics.append(prefs.diagnosticsLog(platform), snapshot))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::covers.isInitialized) covers.onThemeChanged()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (::covers.isInitialized) covers.hide()
        handler.removeCallbacksAndMessages(null)
        if (::audio.isInitialized) releaseFocus.run()
        super.onDestroy()
    }

    private companion object {
        /** A player at least this share of the screen height counts as full screen. */
        const val FULL_SCREEN_FRACTION = 0.5f
        const val SETTLE_MS = 1_500L
        const val BURST_WINDOW_MS = 10_000L
        const val PAUSE_FIRST_MS = 150L
        const val FOCUS_HOLD_MS = 2_000L
        const val SNAPSHOT_INTERVAL_MS = 2_000L
    }
}
