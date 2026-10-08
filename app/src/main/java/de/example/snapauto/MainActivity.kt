package de.example.snapauto

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val snapchatPackage = "com.snapchat.android"

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

        findViewById<Button>(R.id.openExisting).setOnClickListener {
            val launchIntent = packageManager.getLaunchIntentForPackage(snapchatPackage)

            if (launchIntent == null) {
                SnapState.batchMode = false
                Toast.makeText(
                    this,
                    "Snapchat ist in diesem Android-Bereich nicht sichtbar. Installieren Sie SnapAutoOpener und Snapchat im selben vertraulichen Bereich.",
                    Toast.LENGTH_LONG
                ).show()
                updateProfileStatus(showToast = false)
                return@setOnClickListener
            }

            SnapState.batchMode = true
            SnapState.batchStopRequested = false
            SnapState.openedInBatch = 0

            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)

            Toast.makeText(
                this,
                "Snapchat aus demselben Android-Bereich wurde geöffnet. Der vertrauliche Bereich muss entsperrt bleiben.",
                Toast.LENGTH_LONG
            ).show()
        }

        findViewById<Button>(R.id.stopBatch).setOnClickListener {
            SnapState.batchStopRequested = true
            SnapState.batchMode = false
            Toast.makeText(
                this,
                "Durchlauf gestoppt. Geöffnet: ${SnapState.openedInBatch}",
                Toast.LENGTH_SHORT
            ).show()
        }

        updateProfileStatus(showToast = false)
    }

    override fun onResume() {
        super.onResume()
        updateProfileStatus(showToast = false)
    }

    private fun updateProfileStatus(showToast: Boolean) {
        val status = findViewById<TextView>(R.id.profileStatus)
        val snapchatAvailable =
            packageManager.getLaunchIntentForPackage(snapchatPackage) != null

        if (snapchatAvailable) {
            status.text =
                "✓ Snapchat ist in diesem Android-Bereich verfügbar. Starten Sie diese App aus dem vertraulichen Bereich, damit die private Snapchat-Kopie verwendet wird."
            if (showToast) {
                Toast.makeText(
                    this,
                    "Snapchat im selben Android-Bereich gefunden.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            status.text =
                "✗ Snapchat ist in diesem Android-Bereich nicht verfügbar. Öffnen bzw. installieren Sie SnapAutoOpener im selben vertraulichen Bereich wie Snapchat."
            if (showToast) {
                Toast.makeText(
                    this,
                    "Keine Snapchat-Instanz im selben Android-Bereich gefunden.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}