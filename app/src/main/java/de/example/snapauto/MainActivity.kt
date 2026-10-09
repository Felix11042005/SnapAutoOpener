package de.example.snapauto

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val snapchatPackage = "com.snapchat.android"
    private val uiHandler = Handler(Looper.getMainLooper())

    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateDiagnosticReport()
            uiHandler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("snapauto", MODE_PRIVATE)
        val toggle = findViewById<Switch>(R.id.enabledSwitch)

        toggle.isChecked = prefs.getBoolean("enabled", false)
        toggle.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("enabled", checked).apply()
        }

        findViewById<Button>(R.id.notificationAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        findViewById<Button>(R.id.accessibilityAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.checkProfile).setOnClickListener {
            updateProfileStatus(showToast = true)
        }

        findViewById<Button>(R.id.runDiagnostic).setOnClickListener {
            val now = System.currentTimeMillis()
            val diagPrefs = getSharedPreferences("snapauto_diag", MODE_PRIVATE)
            diagPrefs.edit()
                .putBoolean("diagnostic_running", true)
                .putLong("diagnostic_started_at", now)
                .putString("diagnostic_report", "Profil-Test läuft 30 Sekunden. Öffnen Sie Snapchat im ARBEITSPROFIL manuell und wechseln Sie zur Chatliste. Es werden keine Gesten ausgeführt.")
                .putInt("diagnostic_score", -1)
                .putInt("event_count", 0)
                .putInt("snap_event_count", 0)
                .putBoolean("screenshot_ok", false)
                .putInt("visual_candidates", 0)
                .remove("last_event_package")
                .remove("last_event_type")
                .remove("last_event_at")
                .remove("last_snap_event_at")
                .remove("root_package")
                .remove("root_available")
                .apply()

            SnapState.batchMode = false
            SnapState.batchStopRequested = true
            SnapState.pendingUntil = 0L
            SnapState.diagnosticReport = "Profil-Test gestartet. Snapchat im Arbeitsprofil manuell öffnen."
            SnapState.diagnosticBestScore = -1
            SnapState.diagnosticUntil = now + 30_000
            SnapState.diagnosticRequested = true

            Toast.makeText(this, "30 Sekunden: Jetzt Snapchat im ARBEITSPROFIL manuell öffnen.", Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.copyDiagnostic).setOnClickListener {
            val status = findViewById<TextView>(R.id.serviceStatus).text.toString()
            val report = findViewById<TextView>(R.id.diagnosticReport).text.toString()
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("SnapAuto Diagnose", "$status\n\n$report"))
            Toast.makeText(this, "Diagnose kopiert.", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.openExisting).setOnClickListener {
            val launchIntent = packageManager.getLaunchIntentForPackage(snapchatPackage)
            if (launchIntent == null) {
                SnapState.batchMode = false
                Toast.makeText(this, "Snapchat ist in diesem Android-Profil nicht sichtbar.", Toast.LENGTH_LONG).show()
                updateProfileStatus(false)
                return@setOnClickListener
            }

            SnapState.batchMode = true
            SnapState.batchStopRequested = false
            SnapState.openedInBatch = 0
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        }

        findViewById<Button>(R.id.stopBatch).setOnClickListener {
            SnapState.batchStopRequested = true
            SnapState.batchMode = false
            Toast.makeText(this, "Durchlauf gestoppt. Geöffnet: ${SnapState.openedInBatch}", Toast.LENGTH_SHORT).show()
        }

        updateProfileStatus(false)
        updateDiagnosticReport()
    }

    override fun onResume() {
        super.onResume()
        updateProfileStatus(false)
        uiHandler.removeCallbacks(refreshRunnable)
        uiHandler.post(refreshRunnable)
    }

    override fun onPause() {
        uiHandler.removeCallbacks(refreshRunnable)
        super.onPause()
    }

    private fun updateDiagnosticReport() {
        val prefs = getSharedPreferences("snapauto_diag", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val connected = prefs.getBoolean("service_connected", false)
        val heartbeat = prefs.getLong("heartbeat_at", 0L)
        val alive = connected && now - heartbeat < 2500
        val lastEvent = prefs.getLong("last_event_at", 0L)
        val lastSnapEvent = prefs.getLong("last_snap_event_at", 0L)
        val rootAvailable = prefs.getBoolean("root_available", false)
        val rootPackage = prefs.getString("root_package", "-") ?: "-"
        val screenshotOk = prefs.getBoolean("screenshot_ok", false)
        val visualCandidates = prefs.getInt("visual_candidates", 0)
        val visualOpened = prefs.getInt("visual_opened", 0)
        val visualStatus = prefs.getString("visual_status", "-") ?: "-"
        val events = prefs.getInt("event_count", 0)
        val snapEvents = prefs.getInt("snap_event_count", 0)
        val startedAt = prefs.getLong("diagnostic_started_at", 0L)
        val diagnosticAge = if (startedAt > 0) now - startedAt else 0L
        val report = prefs.getString("diagnostic_report", SnapState.diagnosticReport)
            ?: SnapState.diagnosticReport

        fun time(value: Long): String {
            if (value <= 0) return "-"
            return SimpleDateFormat("HH:mm:ss", Locale.GERMANY).format(Date(value))
        }

        val verdict = when {
            !alive -> "ERGEBNIS: Accessibility-Service ist NICHT verbunden."
            startedAt > 0 && diagnosticAge > 30_000 && snapEvents == 0 ->
                "ERGEBNIS: Service läuft, aber es kam KEIN Snapchat-Accessibility-Event an."
            screenshotOk ->
                "ERGEBNIS: V3 Screenshot-Zugriff funktioniert."
            snapEvents > 0 ->
                "ERGEBNIS: Snapchat-Events kommen an. V3 wartet auf Screenshot-Diagnose."
            else -> "ERGEBNIS: Service läuft. Diagnose starten und Snapchat-Chatliste öffnen."
        }

        findViewById<TextView>(R.id.serviceStatus).text = buildString {
            appendLine(verdict)
            appendLine()
            appendLine("Service verbunden: ${if (alive) "JA" else "NEIN"}")
            appendLine("Letztes Event: ${time(lastEvent)}")
            appendLine("Letztes Snapchat-Event: ${time(lastSnapEvent)}")
            appendLine("Events / Snapchat: $events / $snapEvents")
            appendLine("Letztes Event-Paket: ${prefs.getString("last_event_package", "-") ?: "-"}")
            appendLine("Screenshot: ${if (screenshotOk) "JA" else "NEIN"}")
            appendLine("Visuelle Kandidaten: $visualCandidates")
            appendLine("Visuell geöffnet: $visualOpened")
            append("V3-Status: $visualStatus")
        }

        findViewById<TextView>(R.id.diagnosticReport).text = report
    }

    private fun updateProfileStatus(showToast: Boolean) {
        val status = findViewById<TextView>(R.id.profileStatus)
        val snapchatAvailable = packageManager.getLaunchIntentForPackage(snapchatPackage) != null

        status.text = if (snapchatAvailable) {
            "✓ Snapchat ist in diesem Android-Profil verfügbar."
        } else {
            "✗ Snapchat ist in diesem Android-Profil nicht verfügbar."
        }

        if (showToast) {
            Toast.makeText(
                this,
                if (snapchatAvailable) "Snapchat im selben Android-Profil gefunden." else "Keine Snapchat-Instanz im selben Android-Profil gefunden.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
