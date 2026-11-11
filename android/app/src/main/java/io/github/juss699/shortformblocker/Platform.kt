package io.github.juss699.shortformblocker

/**
 * An app whose short-form player gets blocked.
 *
 * [playerViewIds] are view ids that only exist while the full-screen,
 * swipeable Shorts/Reels player is open, so seeing one of them on screen is
 * the signal to leave. They are kept deliberately narrow: matching something
 * that also appears in the normal feed would kick people out of the whole app.
 * If an app update renames them, the diagnostics report shows the new ids.
 */
enum class Platform(
    val packageName: String,
    val itemName: String,
    playerViewIds: List<String>,
) {
    YOUTUBE(
        packageName = "com.google.android.youtube",
        itemName = "Short",
        playerViewIds = listOf("reel_recycler", "reel_player_page_container"),
    ),
    INSTAGRAM(
        packageName = "com.instagram.android",
        itemName = "Reel",
        playerViewIds = listOf("clips_viewer_view_pager"),
    );

    /** Fully qualified ids, as accessibility lookups expect them. */
    val fullViewIds: List<String> = playerViewIds.map { "$packageName:id/$it" }

    val prefKey: String get() = "block_${name.lowercase()}"
    val countKey: String get() = "count_${name.lowercase()}"
    val diagnosticsKey: String get() = "diagnostics_${name.lowercase()}"

    companion object {
        fun forPackage(pkg: CharSequence?): Platform? =
            entries.firstOrNull { it.packageName == pkg?.toString() }
    }
}
