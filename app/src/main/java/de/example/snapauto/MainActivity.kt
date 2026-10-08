package de.example.snapauto

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val prefs = getSharedPreferences("snapauto", MODE_PRIVATE)
        val toggle = findViewById<Switch>(R.id.enabledSwitch)
        toggle.isChecked = prefs.getBoolean("enabled", false)
        toggle.setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("enabled", checked).apply() }

        findViewById<Button>(R.id.notificationAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<Button>(R.id.accessibilityAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.openExisting).setOnClickListener {
            SnapState.batchMode = true
            SnapState.batchStopRequested = false
            SnapState.openedInBatch = 0
            packageManager.getLaunchIntentForPackage("com.snapchat.android")?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(it)
                Toast.makeText(this, "Bestehende Snaps werden gesucht. Zum Stoppen zur App zurückkehren.", Toast.LENGTH_LONG).show()
            } ?: Toast.makeText(this, "Snapchat wurde nicht gefunden.", Toast.LENGTH_LONG).show()
        }
        findViewById<Button>(R.id.stopBatch).setOnClickListener {
            SnapState.batchStopRequested = true
            SnapState.batchMode = false
            Toast.makeText(this, "Durchlauf gestoppt. Geöffnet: ${SnapState.openedInBatch}", Toast.LENGTH_SHORT).show()
        }
    }
}