package io.github.juss699.shortformblocker

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.Switch
import android.widget.TextView

/** The settings screen. */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var statusTitle: TextView
    private lateinit var statusBody: TextView
    private lateinit var openAccessibility: Button
    private lateinit var restrictedHelp: TextView
    private lateinit var openAppInfo: Button
    private lateinit var youtubeSwitch: Switch
    private lateinit var instagramSwitch: Switch
    private lateinit var pauseButton: Button
    private lateinit var countText: TextView
    private lateinit var diagnosticsSwitch: Switch
    private lateinit var shareDiagnostics: Button

    /** Re-renders every second while a pause is counting down. */
    private val ticker = object : Runnable {
        override fun run() {
            render()
            if (prefs.isPaused) handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        statusTitle = findViewById(R.id.status_title)
        statusBody = findViewById(R.id.status_body)
        openAccessibility = findViewById(R.id.open_accessibility)
        restrictedHelp = findViewById(R.id.restricted_help)
        openAppInfo = findViewById(R.id.open_app_info)
        youtubeSwitch = findViewById(R.id.switch_youtube)
        instagramSwitch = findViewById(R.id.switch_instagram)
        pauseButton = findViewById(R.id.pause_button)
        countText = findViewById(R.id.count_text)
        diagnosticsSwitch = findViewById(R.id.switch_diagnostics)
        shareDiagnostics = findViewById(R.id.share_diagnostics)

        openAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        openAppInfo.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
            )
        }
        youtubeSwitch.setOnCheckedChangeListener { _, on -> prefs.setBlocking(Platform.YOUTUBE, on) }
        instagramSwitch.setOnCheckedChangeListener { _, on -> prefs.setBlocking(Platform.INSTAGRAM, on) }
        pauseButton.setOnClickListener {
            prefs.pausedUntil = if (prefs.isPaused) 0L else System.currentTimeMillis() + PAUSE_MS
            restartTicker()
        }
        diagnosticsSwitch.setOnCheckedChangeListener { _, on ->
            prefs.diagnostics = on
            render()
        }
        shareDiagnostics.setOnClickListener { share(Diagnostics.report(this, prefs)) }
    }

    override fun onResume() {
        super.onResume()
        restartTicker()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    private fun restartTicker() {
        handler.removeCallbacks(ticker)
        ticker.run()
    }

    private fun render() {
        val serviceOn = isServiceEnabled()
        val paused = prefs.isPaused

        statusTitle.setText(
            when {
                !serviceOn -> R.string.status_off_title
                paused -> R.string.status_paused_title
                else -> R.string.status_on_title
            },
        )
        statusBody.text = when {
            !serviceOn -> getString(R.string.status_off_body)
            paused -> getString(R.string.status_paused_body, formatRemaining(prefs.pausedUntil - System.currentTimeMillis()))
            else -> getString(R.string.status_on_body)
        }
        val setupVisibility = if (serviceOn) View.GONE else View.VISIBLE
        openAccessibility.visibility = setupVisibility
        restrictedHelp.visibility = setupVisibility
        openAppInfo.visibility = setupVisibility

        youtubeSwitch.isChecked = prefs.isBlocking(Platform.YOUTUBE)
        instagramSwitch.isChecked = prefs.isBlocking(Platform.INSTAGRAM)

        pauseButton.isEnabled = serviceOn
        pauseButton.setText(if (paused) R.string.resume_now else R.string.pause_5_min)

        countText.text = getString(
            R.string.block_count,
            prefs.blockCount(Platform.YOUTUBE),
            prefs.blockCount(Platform.INSTAGRAM),
        )

        diagnosticsSwitch.isChecked = prefs.diagnostics
        shareDiagnostics.isEnabled = Platform.entries.any { prefs.diagnosticsLog(it).isNotBlank() }
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(this, BlockerService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.diagnostics_subject))
            .putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, getString(R.string.share_diagnostics)))
    }

    private fun formatRemaining(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0L) + 999L) / 1_000L
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    private companion object {
        const val PAUSE_MS = 5 * 60 * 1_000L
    }
}
