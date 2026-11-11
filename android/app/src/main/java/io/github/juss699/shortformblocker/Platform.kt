package io.github.juss699.shortformblocker

/**
 * An app whose short-form content gets blocked.
 *
 * [tabLabel]/[tabViewIds] identify its Shorts/Reels button in the bottom
 * navigation bar, which [TabCovers] hides. [darkBarColor] is the bar's colour
 * in dark mode, used until the real colour has been sampled.
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
    /** Opens the app on its home feed (used when Back doesn't close the player). */
    val homeUrl: String,
    val tabLabel: String,
    val tabViewIds: List<String>,
    val darkBarColor: Int,
    playerViewIds: List<String>,
) {
    YOUTUBE(
        packageName = "com.google.android.youtube",
        itemName = "Short",
        homeUrl = "https://www.youtube.com/",
        tabLabel = "Shorts",
        tabViewIds = emptyList(),
        darkBarColor = 0xFF0F0F0F.toInt(),
        playerViewIds = listOf("reel_recycler", "reel_player_page_container"),
    ),
    INSTAGRAM(
        packageName = "com.instagram.android",
        itemName = "Reel",
        homeUrl = "https://www.instagram.com/",
        tabLabel = "Reels",
        tabViewIds = listOf("clips_tab"),
        darkBarColor = 0xFF000000.toInt(),
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
