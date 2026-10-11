package de.example.snapauto

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() { render(); handler.postDelayed(this, 500) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val content = findViewById<android.view.View>(R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        findViewById<TextView>(R.id.title).text = "SnapAutoOpener ${BuildConfig.VERSION_NAME}"
        findViewById<Button>(R.id.accessibilityAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.startDiagnostic).setOnClickListener {
            SnapAccessibilityService.connected?.startDiagnostic()
            render()
        }
        findViewById<Button>(R.id.stopDiagnostic).setOnClickListener {
            SnapAccessibilityService.connected?.stopDiagnostic()
            render()
        }
        findViewById<Button>(R.id.resetDiagnostic).setOnClickListener {
            SnapAccessibilityService.connected?.resetReport()
            getSharedPreferences("snapauto_diag", MODE_PRIVATE).edit().clear().apply()
            render()
        }
        findViewById<Button>(R.id.copyDiagnostic).setOnClickListener {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(
                "SnapAuto Diagnose", findViewById<TextView>(R.id.diagnosticReport).text))
        }
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }

    private fun render() {
        val service = SnapAccessibilityService.connected
        findViewById<Button>(R.id.startDiagnostic).isEnabled = service != null && !service.running
        findViewById<Button>(R.id.stopDiagnostic).isEnabled = service?.running == true
        findViewById<TextView>(R.id.serviceStatus).text = if (service == null)
            "Bedienungshilfe nicht verbunden. START ist gesperrt." else service.status
        val local = packageManager.getLaunchIntentForPackage(ScreenRecognizer.TARGET_PACKAGE) != null
        findViewById<TextView>(R.id.profileStatus).text =
            "Snapchat im aufrufenden Profil sichtbar: ${if (local) "ja" else "nein"}. " +
            "Das identifiziert nicht das vertrauliche Profil. Gewünschte Instanz selbst im Launcher öffnen."
        findViewById<TextView>(R.id.diagnosticReport).text = service?.report ?:
            getSharedPreferences("snapauto_diag", MODE_PRIVATE).getString("last_report", "Noch keine Diagnose. Gespeicherte Berichte beweisen keine aktuelle Dienstverbindung.")
    }
}
