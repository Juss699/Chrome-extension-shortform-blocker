package io.github.juss699.shortformblocker

import android.content.Context
import android.content.SharedPreferences

/** Settings shared by the settings screen and the blocking service. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun isBlocking(platform: Platform): Boolean = sp.getBoolean(platform.prefKey, true)

    fun setBlocking(platform: Platform, on: Boolean) {
        sp.edit().putBoolean(platform.prefKey, on).apply()
    }

    /** Wall-clock time (ms) until which blocking is paused; 0 when not paused. */
    var pausedUntil: Long
        get() = sp.getLong(KEY_PAUSED_UNTIL, 0L)
        set(value) = sp.edit().putLong(KEY_PAUSED_UNTIL, value).apply()

    val isPaused: Boolean
        get() = System.currentTimeMillis() < pausedUntil

    fun blockCount(platform: Platform): Int = sp.getInt(platform.countKey, 0)

    fun incrementBlockCount(platform: Platform) {
        sp.edit().putInt(platform.countKey, blockCount(platform) + 1).apply()
    }

    var diagnostics: Boolean
        get() = sp.getBoolean(KEY_DIAGNOSTICS, false)
        set(value) = sp.edit().putBoolean(KEY_DIAGNOSTICS, value).apply()

    fun diagnosticsLog(platform: Platform): String =
        sp.getString(platform.diagnosticsKey, null).orEmpty()

    fun setDiagnosticsLog(platform: Platform, log: String) {
        sp.edit().putString(platform.diagnosticsKey, log).apply()
    }

    private companion object {
        const val KEY_PAUSED_UNTIL = "paused_until"
        const val KEY_DIAGNOSTICS = "diagnostics"
    }
}
