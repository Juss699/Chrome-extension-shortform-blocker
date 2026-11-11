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
 * shows the new ids so [Platform.fullViewIds] can be updated. It never
 * records text, only view ids, and it stays on the phone unless shared.
 */
object Diagnostics {
    private const val MAX_NODES = 2_000
    private const val MAX_SNAPSHOTS = 4

    fun snapshot(platform: Platform, root: AccessibilityNodeInfo): String {
        val prefix = "${platform.packageName}:id/"
        val ids = sortedSetOf<String>()
        val stack = java.util.ArrayDeque<AccessibilityNodeInfo>()
        stack.push(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_NODES) {
            val node = stack.pop()
            visited++
            if (node.isVisibleToUser) {
                node.viewIdResourceName?.let { ids.add(it.removePrefix(prefix)) }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.push(it) }
            }
        }
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return "[$time] ${ids.joinToString(" ")}"
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
            appendLine("Looking for: ${platform.fullViewIds.joinToString { it.substringAfter(":id/") }}")
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
