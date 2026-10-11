package de.example.snapauto

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Regression guard: Android interaction APIs must not leak back into diagnostics. */
class ArchitectureSafetyTest {
    private val main = File("src/main").let { if (it.exists()) it else File("app/src/main") }
    @Test fun onlyActionExecutorOwnsInteractionApis() {
        val interaction = Regex("\\b(dispatchGesture|performGlobalAction|performAction)\\s*\\(")
        main.resolve("java").walkTopDown().filter { it.extension == "kt" && it.name != "AndroidActionExecutor.kt" }
            .forEach { assertFalse(it.path, interaction.containsMatchIn(it.readText())) }
    }
    @Test fun activityAndServiceDoNotArmInteractiveModeOrCallActions() {
        for (file in listOf("MainActivity.kt", "SnapAccessibilityService.kt")) {
            val text = main.resolve("java/de/example/snapauto/$file").readText()
            assertFalse(text.contains("SessionMode.INTERACTIVE"))
            assertFalse(Regex("actions\\s*\\.\\s*execute").containsMatchIn(text))
        }
    }
    @Test fun screenshotsNeverFallBackToEntireDisplay() {
        val text = main.resolve("java/de/example/snapauto/SnapAccessibilityService.kt").readText()
        assertFalse(Regex("\\btakeScreenshot\\s*\\(").containsMatchIn(text))
    }
}
