# Validierung

Stand: 11.10.2026. Ausgangspunkt: `054a5a0`, vorheriger erfolgreicher main-Build #125.

- Erstprüfung der neuen Architektur auf `54e0286`: [Actions #126](https://github.com/Felix11042005/SnapAutoOpener/actions/runs/38152164479), erfolgreich. `testDebugUnitTest`, `lintDebug`, `assembleDebug` ausgeführt, APK und Validierungsberichte erzeugt.
- Anschließend wurde die Unterscheidung zwischen unbekanntem und explizit nicht gewähltem Filter ergänzt. Die Testsuite umfasst jetzt **35 Tests**. Der Workflow prüft zusätzlich die erzeugte APK mit `apksigner verify` und protokolliert ihren SHA-256-Wert.
- Für jeden ausgelieferten Commit sind dessen Actions-Ergebnis und die zugehörigen `validation-reports` maßgeblich. Erst nach erfolgreicher PR-Prüfung wird main aktualisiert; der main-Lauf prüft erneut und baut mit der vorhandenen Signierung.
- Lokales `git diff --check` und Quellprüfung durchgeführt. Lokal fehlen JDK/Android-SDK; keine Behauptung eines lokalen Gradle-Builds. CI verwendet die bereits etablierte Java-17-/Gradle-8.9-/Android-SDK-Umgebung.
- Keine echten Snapchat-Bilder und kein Pixel-Gerät verfügbar. UI-Erkennungsquote, Accessibility im Private Space, STOP-Overlay-Erreichbarkeit und Gerätelebenszyklus sind **noch nicht am Gerät abgenommen**. Anleitung: [PIXEL_TEST_PLAN.md](PIXEL_TEST_PLAN.md).
- Bedienaktionen bleiben implementiert und zentral abgesichert. In dieser APK ist ausschließlich Diagnose per START erreichbar. Quelltests prüfen diese Grenze; reine Zustandstests prüfen STOP/Timeout/Neustart. Sie ersetzen keine Android-Integrationstests.
