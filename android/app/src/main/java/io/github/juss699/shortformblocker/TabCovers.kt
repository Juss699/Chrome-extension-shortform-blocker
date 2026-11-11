package io.github.juss699.shortformblocker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Hides the Shorts/Reels button in an app's bottom navigation bar.
 *
 * Android doesn't let one app remove views from another, so this is the
 * closest equivalent: an accessibility overlay laid exactly over the button,
 * filled with the bar's own colour, that swallows taps. It is only shown while
 * the app is in front and nothing else (keyboard, notification shade, another
 * app) is on top of the button.
 */
class TabCovers(
    private val service: AccessibilityService,
    private val shouldCover: (Platform) -> Boolean,
    private val onBlockedTap: (Platform) -> Unit,
) {
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var covered: Platform? = null
    private var view: View? = null
    private var shownAt: Rect? = null
    private var sampledColor: Int? = null
    private var lastSampleAt = 0L

    /**
     * While a cover is up, keep re-checking: leaving the app (to the home
     * screen, another app, recents) sends this service no events, because it
     * only listens to YouTube and Instagram.
     */
    private val poll = object : Runnable {
        override fun run() {
            val platform = covered ?: return
            update(platform)
            if (view != null) handler.postDelayed(this, POLL_MS)
        }
    }

    /** Covers [platform]'s Shorts/Reels button if it's on screen, otherwise removes the cover. */
    fun update(platform: Platform): Boolean {
        val rect = if (shouldCover(platform)) locate(platform) else null
        if (rect == null) {
            if (covered == platform) hide()
            return false
        }
        show(platform, rect)
        return true
    }

    fun hide() {
        handler.removeCallbacks(poll)
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        shownAt = null
        covered = null
    }

    /** Light/dark mode changed, so the bar's colour probably did too. */
    fun onThemeChanged() {
        sampledColor = null
        lastSampleAt = 0L
        val platform = covered ?: return
        view?.setBackgroundColor(guessColor(platform))
        shownAt?.let { sampleColor(platform, it) }
    }

    // -------------------------------------------------------------------------
    // Drawing the cover
    // -------------------------------------------------------------------------

    private fun show(platform: Platform, rect: Rect) {
        if (covered != platform) {
            hide()
            covered = platform
            sampledColor = null
        }
        val existing = view
        if (existing != null) {
            if (rect != shownAt) {
                runCatching { windowManager.updateViewLayout(existing, layoutParams(rect)) }
                shownAt = Rect(rect)
            }
            return
        }

        val cover = View(service).apply {
            setBackgroundColor(sampledColor ?: guessColor(platform))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            // Swallow every touch so the button underneath can't be pressed.
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) covered?.let(onBlockedTap)
                true
            }
        }
        try {
            windowManager.addView(cover, layoutParams(rect))
        } catch (e: RuntimeException) {
            covered = null
            return
        }
        view = cover
        shownAt = Rect(rect)
        handler.postDelayed(poll, POLL_MS)
        sampleColor(platform, rect)
    }

    private fun layoutParams(rect: Rect) = WindowManager.LayoutParams(
        rect.width(),
        rect.height(),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.OPAQUE,
    ).apply {
        // Position in absolute screen coordinates, exactly where the button is.
        gravity = Gravity.TOP or Gravity.START
        x = rect.left
        y = rect.top
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
    }

    /** Best guess before a colour sample is available: the app follows the system theme. */
    private fun guessColor(platform: Platform): Int {
        val night = (service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return if (night) platform.darkBarColor else Color.WHITE
    }

    /**
     * On Android 11+, read the bar's real colour from a pixel just beside the
     * button (outside the cover), so the cover blends in whatever theme the
     * app uses. Only that one pixel is looked at.
     */
    private fun sampleColor(platform: Platform, rect: Rect) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastSampleAt < SAMPLE_INTERVAL_MS) return
        lastSampleAt = now
        val target = Rect(rect)

        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val buffer = screenshot.hardwareBuffer
                    var bitmap: Bitmap? = null
                    try {
                        val hardware = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                        bitmap = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                        hardware?.recycle()
                    } finally {
                        buffer.close()
                    }
                    val pixels = bitmap ?: return
                    val y = (target.top + EDGE_PX).coerceIn(0, pixels.height - 1)
                    val x = listOf(target.left - EDGE_PX, target.right + EDGE_PX)
                        .firstOrNull { it in 0 until pixels.width }
                    val color = x?.let { pixels.getPixel(it, y) }
                    pixels.recycle()
                    if (color == null || covered != platform) return
                    val opaque = color or 0xFF000000.toInt()
                    sampledColor = opaque
                    view?.setBackgroundColor(opaque)
                }

                override fun onFailure(errorCode: Int) = Unit
            },
        )
    }

    // -------------------------------------------------------------------------
    // Finding the button
    // -------------------------------------------------------------------------

    /** Screen rectangle of the button, if the app is in front and nothing covers it. */
    private fun locate(platform: Platform): Rect? {
        val windows = service.windows
        var app: AccessibilityWindowInfo? = null
        var appRoot: AccessibilityNodeInfo? = null
        for (window in windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val root = window.root ?: continue
            if (root.packageName?.toString() != platform.packageName) continue
            if (app == null || window.layer > app.layer) {
                app = window
                appRoot = root
            }
        }
        if (app == null || appRoot == null || app.isInPictureInPictureMode) return null

        val appBounds = Rect()
        app.getBoundsInScreen(appBounds)
        val rect = findButton(platform, appRoot, appBounds) ?: return null

        // Is anything on top of the button?
        val screenHeight = service.resources.displayMetrics.heightPixels
        val bounds = Rect()
        for (window in windows) {
            if (window.layer <= app.layer) continue
            if (window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
            window.getBoundsInScreen(bounds)
            // The status and navigation bars are thin system strips above every
            // app; the notification shade and recents fill the screen.
            if (window.type == AccessibilityWindowInfo.TYPE_SYSTEM && bounds.height() < screenHeight * 0.3f) continue
            if (Rect.intersects(bounds, rect)) return null
        }
        return rect
    }

    private fun findButton(platform: Platform, root: AccessibilityNodeInfo, appBounds: Rect): Rect? {
        val rect = Rect()

        // 1. Known view ids.
        for (id in platform.tabViewIds) {
            for (node in root.findAccessibilityNodeInfosByViewId("${platform.packageName}:id/$id")) {
                if (!node.isVisibleToUser) continue
                node.getBoundsInScreen(rect)
                if (!rect.isEmpty) return Rect(rect)
            }
        }

        // 2. A tab labelled exactly "Shorts"/"Reels" in a bottom navigation bar.
        for (node in root.findAccessibilityNodeInfosByText(platform.tabLabel)) {
            if (!node.isVisibleToUser) continue
            val labelled = listOf(node.contentDescription, node.text)
                .any { it?.toString()?.trim().equals(platform.tabLabel, ignoreCase = true) }
            if (!labelled) continue
            val tab = clickableSelfOrAncestor(node) ?: continue
            tab.getBoundsInScreen(rect)
            if (isNavigationTab(tab, rect, appBounds)) return Rect(rect)
        }
        return null
    }

    private fun clickableSelfOrAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        repeat(4) {
            val candidate = current ?: return null
            if (candidate.isClickable) return candidate
            current = candidate.parent
        }
        return null
    }

    /**
     * A navigation tab is small, sits at the bottom of the app's window, and is
     * one of at least three items in a row spanning most of the width. This
     * rules out a "Shorts" heading in the feed.
     */
    private fun isNavigationTab(tab: AccessibilityNodeInfo, rect: Rect, appBounds: Rect): Boolean {
        if (rect.isEmpty) return false
        if (rect.height() > appBounds.height() * 0.15f || rect.width() > appBounds.width() * 0.4f) return false
        if (rect.bottom < appBounds.bottom - appBounds.height() * 0.2f) return false

        var row: AccessibilityNodeInfo? = tab.parent
        val rowBounds = Rect()
        repeat(2) {
            val candidate = row ?: return false
            candidate.getBoundsInScreen(rowBounds)
            if (candidate.childCount >= 3 && rowBounds.width() >= appBounds.width() * 0.7f) return true
            row = candidate.parent
        }
        return false
    }

    private companion object {
        const val POLL_MS = 500L
        const val SAMPLE_INTERVAL_MS = 1_000L
        const val EDGE_PX = 4
    }
}
