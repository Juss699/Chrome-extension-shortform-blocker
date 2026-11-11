package io.github.juss699.shortformblocker

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Optional, user-enabled record of which view ids are on screen in YouTube and
 * Instagram. When an app update renames its Shorts/Reels player, this report
 * shows the new ids so [Platform] can be updated. It records view ids and
 * the short labels of the bottom navigation buttons, never content, and it
 * stays on the phone unless shared.
 */
object Diagnostics {
    private const val MAX_NODES = 2_000
    private const val MAX_SNAPSHOTS = 4

    fun snapshot(platform: Platform, root: AccessibilityNodeInfo, status: String, screenHeight: Int): String {
        val prefix = "${platform.packageName}:id/"
        val ids = sortedSetOf<String>()
        val bottomLabels = sortedSetOf<String>()
        val bounds = android.graphics.Rect()
        val stack = java.util.ArrayDeque<AccessibilityNodeInfo>()
        stack.push(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_NODES) {
            val node = stack.pop()
            visited++
            if (node.isVisibleToUser) {
                node.viewIdResourceName?.let { ids.add(it.removePrefix(prefix)) }
                // Labels of buttons along the bottom edge (the navigation bar),
                // to help find a renamed Shorts/Reels tab. Short labels only.
                if (node.isClickable) {
                    node.getBoundsInScreen(bounds)
                    val label = (node.contentDescription ?: node.text)?.toString()?.trim().orEmpty()
                    if (bounds.top > screenHeight * 0.8f && label.isNotEmpty() && label.length <= 24) {
                        bottomLabels.add(label)
                    }
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.push(it) }
            }
        }
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return "[$time] $status | bottom bar: ${bottomLabels.joinToString(", ")} | ids: ${ids.joinToString(" ")}"
    }

    /** Keeps the most recent [MAX_SNAPSHOTS] snapshots. */
    fun append(log: String, snapshot: String): String =
        (log.lines().filter { it.isNotBlank() } + snapshot)
            .takeLast(MAX_SNAPSHOTS)
            .joinToString("\n")

    fun report(context: Context, prefs: Prefs): String = buildString {
        appendLine("Short-Form Blocker diagnostics")
        appendLine("Blocker ${versionOf(context, context.packageName)}")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
        for (platform in Platform.entries) {
            appendLine()
            appendLine("${platform.packageName} ${versionOf(context, platform.packageName)}")
            appendLine("Tab: \"${platform.tabLabel}\" ${platform.tabViewIds}; player: ${platform.fullViewIds.joinToString { it.substringAfter(":id/") }}")
            appendLine(prefs.diagnosticsLog(platform).ifBlank { "(nothing recorded yet)" })
        }
    }

    @Suppress("DEPRECATION")
    private fun versionOf(context: Context, pkg: String): String =
        try {
            context.packageManager.getPackageInfo(pkg, 0).versionName ?: "?"
        } catch (e: PackageManager.NameNotFoundException) {
            "(not installed)"
        }
}
