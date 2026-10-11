# Technische Bestandsaufnahme und Entwicklungsplan

Stand: 11.10.2026, Ausgangspunkt `054a5a061fcb10684c4ae7f3000617bc6a4cc3f5` (main).
Alle 16 versionierten Dateien einschließlich Ressourcen und Build-Konfiguration gelesen.

## Verifizierter Ausgangsstand

- Kotlin 2.1.0, AGP 8.7.3, Gradle 8.9 in Actions, Java 17; SDK 35, minSdk 26.
- Version 5.1-safe-diagnostics, versionCode 27. Letzte Änderung: START startet über PackageManager die Snapchat-Kopie im aufrufenden Profil.
- Actions Build APK #121–125 erfolgreich. #125: https://github.com/Felix11042005/SnapAutoOpener/actions/runs/38149054640
- Artifact `SnapAutoOpener-apk`, ID 11706680378, nicht abgelaufen, Ablauf 09.01.2027. Bisheriger Workflow kompiliert, führt aber keine Tests/Lint aus. Ein grüner Build belegt keine Gerätefunktion.

## Befunde

1. **Hohe Priorität: STOP/Lebenszyklus.** STOP setzt `diagnosticRequested` nicht zurück. Diagnose endet nur im erfolgreichen Screenshot-Callback; ohne Events/bei Fehlern fehlt ein unabhängiger Ablauf. `onInterrupt` ist leer, `onDestroy` entfernt nur Heartbeat. Alte Callbacks können neue Berichte überschreiben. Laufzeiten verwenden veränderliche Wanduhr.
2. **Hohe Priorität: latente Aktionen.** Der normale Batch-Pfad ist Diagnose, trotzdem existieren Einzeltest, Viewer-Tipps, BACK und Wischmethoden samt Gestenberechtigung. Nachvollziehbare strukturelle Trennung fehlt.
3. **Hohe Priorität: Bildzuordnung.** Display-Screenshot und mehrfach unabhängig gelesene Roots haben weder gemeinsame Fensteridentität noch Stale-Result-Prüfung. Nach App-Wechsel können falsche Inhalte untersucht werden. Bildanalyse läuft im Hauptthread.
4. **Erkennung.** Starre Prozentstreifen für Filter/Icon und unspezifische Teilstrings (`Chat`, `neue` + `snap`) sind keine sicheren Selektoren. Dunkel/hell beweist weder den Ungelesen-Text noch seine Auswahl. Farbquadrate beweisen keinen ungeöffneten Snap. `Antworten` ist kein Viewer-Nachweis. Keine Gerätefixtures, Tests oder Fehlerquoten.
5. **Profil.** Gleicher Paketname identifiziert kein Android-Profil. Ein PackageManager-Launch wählt das aktuelle Profil. README verspricht bisher ungetestet, dass der Dienst im Private Space aktivierbar ist. Die Statusanzeige kann nur lokale Paket-Sichtbarkeit belegen.
6. **Zustand/UX.** Viele globale Flags, tote Zähler und mehrfacher SharedPreferences-Zustand. UI zeigt V5.0/Schnellmodus und V3-Berichte trotz V5.1. Persistierte Heartbeats sind kein sicherer Nachweis einer aktuellen Verbindung. Benachrichtigungsdienst ist wirkungslos, Zugriff unnötig. STOP liegt nur in der eigenen Activity.
7. **Build.** Kein Wrapper, keine Tests, kein Lint-Gate. Release-Signierung vorhanden, Debug-Fallback bei fehlenden Secrets. Bestehender Artifact-Name soll erhalten bleiben.

## Private Space: Möglichkeiten und Grenzen

Private Space ist ein separates Profil. Gesperrt werden dessen Apps gestoppt. Entsperren gibt normalen Apps im Hauptprofil keinen allgemeinen profilübergreifenden Zugriff. Offizielle Launcher-Integration benötigt sowohl die Standard-Launcher-Rolle `ROLE_HOME` als auch `ACCESS_HIDDEN_PROFILES` und vollständige Sperr-/Sichtbarkeitsbehandlung. SnapAutoOpener ist kein Launcher; ein Rollenwechsel wäre ein eigenes Produktprojekt.

Ein normaler Launch aus einer im selben Profil installierten App ist grundsätzlich der passende Mechanismus für diese Profilkopie. Ob Installation, Aktivierung und Auslieferung von Accessibility-Daten dort auf diesem Pixel möglich sind, ist separat zu testen; gleiche Installation allein garantiert keinen funktionierenden Dienst. Eine normale App kann nicht zuverlässig behaupten, ihr Profil sei Private Space. Kein Root, Shell-Start unter fremder User-ID, versteckte API oder Sicherheitsumgehung wird eingebaut.

Diese Iteration startet Snapchat nicht automatisch: Nutzer öffnet die gewünschte Kopie über den Pixel Launcher. Die Diagnose berichtet tatsächliche Fenster-Sichtbarkeit, keine behauptete Profilidentität.

Quellen: [Android 15](https://developer.android.com/about/versions/15/behavior-changes-all#private-space), [AOSP Private Space](https://source.android.com/docs/security/features/private-space), [LauncherApps](https://developer.android.com/reference/android/content/pm/LauncherApps).

## Snapchat-Nutzungsbedingungen

Die ab 21.09.2026 geltenden Snap Group Limited Terms, Abschnitt 8, untersagen automatisierten/halbautomatisierten Zugriff und Drittanbieter-Interaktionen ohne schriftliche Zustimmung. Ein eigener START-Knopf, ausschließlich empfangene Snaps oder die Verwendung offizieller Android-APIs begründen keine Ausnahme. Auch automatisierte Accessibility-/Screenshot-Diagnose ist dort nicht ausdrücklich freigegeben. Daher keine Zusicherung der Vertragskonformität; für Live-Nutzung ist eine Klärung mit Snap nötig. Offline-Tests mit synthetischen UI-Daten greifen nicht auf Snapchat zu. Manuelle Bedienung der offiziellen App bleibt der vorgesehene Weg. Quelle: https://www.snap.com/terms (Abschnitt 8, außerhalb USA).

## Geplante sichere Umsetzung

1. Eine begrenzte Sitzung mit monotoner Zeit, Lauf-ID und Abbruchgeneration. START nur bei verbundenem Dienst; STOP über Activity und Accessibility-Overlay; Timeout auch ohne Events. Neustart nie automatisch scharf.
2. Android-Adapter erzeugt begrenzte, unveränderliche Momentaufnahme. Reine Kotlin-Erkennung arbeitet auf dieser Momentaufnahme; Diagnosezustand und Darstellung getrennt. Nach ausdrücklicher Nutzerkorrektur bleiben Tipp-, Wisch- und Zurück-Implementierungen und Gestenfähigkeit erhalten. Ein zentraler ActionPolicy-Gate verweigert jede Aktion im Diagnosemodus. Die ausgelieferte UI startet ausschließlich Diagnose. Öffnen/Weiter/BACK bleiben zusätzlich mangels verifiziertem Snap-/Viewer-Nachweis gesperrt. Eine spätere interaktive Freigabe muss diese Nachweise implementieren; dies ist kein aktueller funktionsfähiger Auto-Opener.
3. Sichtbare exakte DE/EN-Semantik, tatsächlicher Auswahlzustand, Listenstruktur, Elementgrenzen und zwei gleiche Beobachtungen. Unbekannt/mehrdeutig bleibt unbekannt. Keine Filteraktivierung, keine Viewer-Erkennung aus Abwesenheit von Chatmerkmalen. Editierfeld, Werbung/My AI sperren konservativ die ganze Aufnahme.
4. Optionale Bildhinweise nur ab Android 14 aus dem identifizierten Fenster, nur bei semantisch plausibler Liste mit ausgewähltem Filter. Verbundene Farbflächen innerhalb semantisch gefundener Zeilen; Form/Füllung prüfen. Koordinaten nur als Messwert. Keine Vollbild-Ausweichaufnahme. Keine dauerhafte Bildspeicherung.
5. Hauptthread frei von Pixelanalyse; Callback prüft Lauf-ID, Frist, Fenster und unveränderte Evidenz erneut. Screenshot-Timeout und begrenzte Fehlversuche. Diagnoseprotokoll begrenzt, ohne Namen, Texte oder Bilder; Kopieren nur explizit.
6. Synthetische Unit-Tests für Zustandsmaschine, Grenzen, Selektoren, Ausschlüsse und Bildheuristik. Quell-/Manifest-Test gegen erneute Action-Pfade. Bestehenden Actions-Workflow um PR-Validierung, Tests und Lint ergänzen; nach erfolgreicher Prüfung main aktualisieren und Artifact verifizieren.

## Nächste Entwicklungsstufe (nicht Teil dieser Freigabe)

Anonymisierte Evidenz vom echten Pixel auswerten: Android-/Snapchat-Version, Sprache, Schriftgröße, Displaygröße, Theme, Drehung, Insets, mehrere Fenster und Profilwechsel. Fehlpositive getrennt von übersehenen Kandidaten messen. Resource-IDs erst nach Gerätebeobachtung zulassen; keine erfundenen Snapchat-IDs. Erst danach weitere Erkennungsregeln. Bedienaktionen benötigen eine gesonderte Entscheidung einschließlich Vertragsklärung und belastbarer Gerätetests.

## Übernommene technische Prinzipien

[Appium UiAutomator2](https://github.com/appium/appium-uiautomator2-driver): semantische Lokatoren und explizite Elementzustände statt Koordinaten. [AutoJs6](https://github.com/SuperMonster003/AutoJs6/blob/master/app/src/main/assets-app/docs/automator.html): selektorbasierte Suche und begrenztes Warten. Diese Ideen werden eigenständig implementiert; keine fremden Implementierungen oder Snapchat-IDs kopiert. Appium bleibt eine mögliche spätere Testumgebung, keine Runtime-Abhängigkeit der APK.
