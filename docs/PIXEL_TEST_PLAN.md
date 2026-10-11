# Pixel-8-Abnahme: 5.2-safe-diagnostics

Diese Tests wurden nicht auf einem echten Pixel durchgeführt. Unit-Tests verwenden ausschließlich synthetische Daten. Sie belegen weder Snapchat-Erkennungsqualität noch Private-Space-Kompatibilität oder Vertragskonformität. Live-Nutzung mit Snap klären (siehe Bestandsaufnahme).

## Installation und Profil

1. Neueste erfolgreiche **Build APK**-Ausführung für main öffnen, Commit prüfen, unter **Artifacts → SnapAutoOpener-apk** die ZIP laden und `SnapAutoOpener.apk` entpacken. PR-Artifacts sind Debug-Validierungsbuilds, main verwendet wie bisher vorhandene Signiersecrets. Nicht zum Umgehen eines Signaturfehlers blind deinstallieren; den passenden signierten main-Build verwenden.
2. Android-Version, Snapchat-Version, Sprache, Theme, Schrift-/Displaygröße und Installationsprofil notieren. APK zeigt 5.2-safe-diagnostics (28).
3. Bedienungshilfe in den Android-Einstellungen aktivieren, soweit Android dies für diese Installation zulässt. Kein Benachrichtigungszugriff nötig. Wenn Android eine Aktivierung verhindert: Ergebnis notieren; keine Profil-/Sicherheitsumgehung.
4. Hauptprofil und entsperrtes Private Space getrennt prüfen. Eine Paket-Sichtbarkeitsmeldung beweist nicht, welche Profilkopie beobachtet wird. Im Private Space installierte Dienstkopie kann andere Möglichkeiten haben; nicht als bereits unterstützt voraussetzen.

## START / STOP / Lebenszyklus

| Test | Erwartung |
|---|---|
| Bedienungshilfe aus | START deaktiviert, keine alte Heartbeat-Meldung als verbunden |
| START in SnapAutoOpener | Snapchat startet nicht selbst; STOP-Overlay erscheint; 30-s-Frist beginnt |
| Ohne Snapchat warten | Timeout auch ohne ein einziges Snapchat-Event, Overlay verschwindet |
| Gewünschte Snapchat-Kopie selbst im Launcher öffnen | Diagnose nur, wenn deren Fenster tatsächlich für den Dienst sichtbar ist |
| Overlay-STOP während Aufnahme; danach erneut START | Alter Bericht/Callback verändert neuen Lauf nicht; keine Aktion |
| STOP in Activity / Diagnose löschen | Sitzung endet, Overlay verschwindet, Löschen leert gespeicherten Bericht |
| Display sperren/Private Space sperren | Sitzung stoppt bzw. Prozess wird beendet; nach Entsperren kein automatischer Neustart |
| Dienst abschalten / Prozess beenden | Keine Fortsetzung nach Neuverbindung/Neustart |
| App wechseln während Screenshot | Alte Aufnahme verworfen, fremde App nicht als Snapchat erfasst |

## Erkennung

- Chatliste mit semantisch sichtbarem Header, Filter und Listenstruktur: `CHAT_LIST`; zwei identische Beobachtungen nötig. Fehlende/mehrdeutige Merkmale: `UNKNOWN`, keine geschätzten Ersatzkoordinaten.
- Ungelesen aus/an vergleichen: `NOT_SELECTED` bzw. `SELECTED` nur bei tatsächlich gemeldetem Node-Zustand. Liefert Snapchat keine Auswahlsemantik, gibt es keine Bilddiagnose. Bitte genau diesen Fall melden.
- Normalen Chat, Suche, Snap-Ansicht und Tastatur manuell öffnen: editierbare Felder sperren; fehlende Listenevidenz bleibt unbekannt. Es gibt keine automatische Rücknavigation.
- Sichtbare Werbung oder My AI: konservative Sperre der ganzen Aufnahme. Auch harmlose Inhalte können diese Sperre auslösen. Keine Bedienung dieser Elemente.
- Helles/dunkles Theme, große Schrift, Drehung, Displayzoom, Split-Screen testen. Geänderte Geometrie setzt Stabilität zurück. Abweichende Bild-/Fenstergröße: `GEOMETRY_MISMATCH`, keine Positionszuordnung.
- Rote/lila gefüllte Quadrate sind **Farbhinweise**, keine bestätigten Snaps. Blaue Symbole, Umrisse und große Avatare sollen im synthetischen Test aussortiert werden. Gleichförmige Dekorationen bleiben ein bekannter Fehlpositivfall.
- API <34: ausschließlich Baumdiagnose, kein Display-Screenshot-Fallback. Sichere Fenster/Screenshotfehler werden respektiert; maximal drei Fehler bzw. 5-s-Aufnahmetimeout.

## Bericht zurückgeben

Über „Diagnose kopieren“ den Bericht mit Geräte-/Layoutangaben und beobachtetem Soll-/Ist-Verhalten übermitteln. Der Bericht enthält keine Kontakte, Texte, Bilder oder Accountdaten. Bitte selbst keine Namen/Snap-Inhalte hinzufügen. Rohtexte werden nur flüchtig im Speicher für die Erkennung verarbeitet. In allen Tests müssen automatisch ausgeführte Bedienaktionen **0** bleiben.

## Bewusste Grenzen

STOP verhindert neue Anforderungen und verwirft verspätete Ergebnisse. Bereits vom Betriebssystem angenommene Aufnahmen können nicht rückwirkend gelöscht werden, ihr Ergebnis wird freigegeben/verworfen. Bei einer späteren interaktiven Freigabe gilt entsprechend: bereits dispatchte kurze Gesten sind nicht synchron zurückrufbar. Diese Version dispatcht überhaupt keine Gesten. Overlay-Erreichbarkeit, Pixel-Latenz und Profilzugriff sind erst nach Gerätetest belegt.
