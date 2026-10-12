# Pixel-8-Abnahme: 5.8.1-overlap-scroll

Diese Tests wurden nicht auf einem echten Pixel durchgeführt. Unit-Tests verwenden synthetische Daten und isolierte Symbolausschnitte aus den bereitgestellten Aufnahmen. Sie belegen weder die Erkennungsqualität ganzer Fenster noch Private-Space-Kompatibilität oder Vertragskonformität. Live-Nutzung mit Snap klären (siehe Bestandsaufnahme).

Der echte Öffnungsversuch und seine zusätzlichen Bildprüfungen sind in [OPENING_MODE.md](OPENING_MODE.md) beschrieben. Die folgenden Erkennungsprüfungen beziehen sich auf den separat wählbaren Diagnosemodus; dieser muss ohne Bedienaktionen bleiben.

Der neue mehrseitige Modus hat eine eigene [Abnahme für Filter, Scrollen, Erfassung und Öffnen](FILTER_SCROLL.md#pixel-abnahme). Filter-Tipp, Scrollrichtung, unverändertes Listenende, neue Ziele nach Erfassung und STOP in jeder Phase zuerst mit wenigen bekannten Zielen prüfen. Der bisherige sichtbare Bestandslauf hat eine eigene [Abnahme für Öffnen → 5 Sekunden → Zurück → ursprüngliches nächstes Ziel](VISIBLE_BATCH.md#pixel-abnahme). Nur dieser separat gestartete Modus darf mehrere ursprüngliche Zeilen und kontextbezogene Rücknavigation bedienen. STOP während der Wartezeit, fehlende Rückkehr, neue andere Zeilen und geänderte ursprüngliche Statuszahlen müssen separat geprüft werden.

## Installation und Profil

1. Neueste erfolgreiche **Build APK**-Ausführung für main öffnen, Commit prüfen, unter **Artifacts → SnapAutoOpener-apk** die ZIP laden und `SnapAutoOpener.apk` entpacken. PR-Artifacts sind Debug-Validierungsbuilds, main verwendet wie bisher vorhandene Signiersecrets. Nicht zum Umgehen eines Signaturfehlers blind deinstallieren; den passenden signierten main-Build verwenden.
2. Android-Version, Snapchat-Version, Sprache, Theme, Schrift-/Displaygröße und Installationsprofil notieren. APK zeigt 5.8.1-overlap-scroll (38). Für den gesamten Bestand vor START am Listenanfang stehen; 5.8 erfasst ab der sichtbaren Seite nach unten und öffnet danach von unten auf dem Rückweg.
3. Bedienungshilfe in den Android-Einstellungen aktivieren, soweit Android dies für diese Installation zulässt. Kein Benachrichtigungszugriff nötig. Wenn Android eine Aktivierung verhindert: Ergebnis notieren; keine Profil-/Sicherheitsumgehung.
4. Hauptprofil und entsperrtes Private Space getrennt prüfen. Eine Paket-Sichtbarkeitsmeldung beweist nicht, welche Profilkopie beobachtet wird. Im Private Space installierte Dienstkopie kann andere Möglichkeiten haben; nicht als bereits unterstützt voraussetzen.

## START / STOP / Lebenszyklus

| Test | Erwartung |
|---|---|
| Bedienungshilfe aus | START deaktiviert, keine alte Heartbeat-Meldung als verbunden |
| Diagnose-START in SnapAutoOpener | Snapchat startet nicht selbst; STOP-Overlay erscheint; 30-s-Frist beginnt |
| Ohne Snapchat warten | Timeout auch ohne ein einziges Snapchat-Event, Overlay verschwindet |
| Gewünschte Snapchat-Kopie selbst im Launcher öffnen | Diagnose nur, wenn deren Fenster tatsächlich für den Dienst sichtbar ist |
| Overlay-STOP während Aufnahme; danach erneut START | Alter Bericht/Callback verändert neuen Lauf nicht; keine Aktion |
| STOP in Activity / Diagnose löschen | Sitzung endet, Overlay verschwindet, Löschen leert gespeicherten Bericht |
| Display sperren/Private Space sperren | Sitzung stoppt bzw. Prozess wird beendet; nach Entsperren kein automatischer Neustart |
| Dienst abschalten / Prozess beenden | Keine Fortsetzung nach Neuverbindung/Neustart |
| App wechseln während Screenshot | Alte Aufnahme verworfen, fremde App nicht als Snapchat erfasst |

## Erkennung

- Chatliste mit semantisch sichtbarem Header, Filter und Listenstruktur: `CHAT_LIST`; zwei identische Beobachtungen nötig. Fehlende/mehrdeutige Merkmale: `UNKNOWN`, keine geschätzten Ersatzkoordinaten.
- Ungelesen aus/an vergleichen: `SELECTED` nur bei positiv gemeldeter Auswahl; `NOT_SELECTED` nur bei explizit prüfbarem, nicht aktiviertem Steuerelement. Sonst `UNKNOWN`. Liefert Snapchat keine Auswahlsemantik, gibt es keine Bilddiagnose. Bitte genau diesen Fall melden.
- Normalen Chat, Suche, Snap-Ansicht und Tastatur manuell öffnen: editierbare Felder sperren; fehlende Listenevidenz bleibt unbekannt. Es gibt keine automatische Rücknavigation.
- Sichtbare Werbung oder My AI: In einer verifizierten Chatliste wird die vollständige zugehörige Listenzeile von der Bildauswertung ausgeschlossen; andere Zeilen dürfen diagnostiziert werden. Fehlt die eindeutige Zuordnung, bleibt `UNSCOPED_AD_OR_MY_AI` eine Sperre der Aufnahme. Keine Bedienung dieser Elemente.
- `INCOMPLETE_TREE`: Knotenanzahl, Knotenlimit und fehlende Kinder im Bericht vergleichen. Vorzeitig gesperrte Erkennung meldet `Status-Zeilen=nicht ausgewertet`; dies ist kein Nachweis einer leeren Chatliste.
- Helles/dunkles Theme, große Schrift, Drehung, Displayzoom, Split-Screen testen. Geänderte Geometrie setzt Stabilität zurück. Abweichende Bild-/Fenstergröße: `GEOMETRY_MISMATCH`, keine Positionszuordnung.
- Rote/lila gefüllte Quadrate sind **Farbhinweise**, keine bestätigten Snaps. Blaue Symbole, Umrisse und große Avatare sollen im synthetischen Test aussortiert werden. Gleichförmige Dekorationen bleiben ein bekannter Fehlpositivfall.
- API <34: ausschließlich Baumdiagnose, kein Display-Screenshot-Fallback. Sichere Fenster/Screenshotfehler werden respektiert; maximal drei Fehler bzw. 5-s-Aufnahmetimeout.

## Bericht zurückgeben

Über „Diagnose kopieren“ den Bericht mit Geräte-/Layoutangaben und beobachtetem Soll-/Ist-Verhalten übermitteln. Der Bericht enthält keine Kontakte, Texte, Bilder oder Accountdaten. Bitte selbst keine Namen/Snap-Inhalte hinzufügen. Rohtexte werden nur flüchtig im Speicher für die Erkennung verarbeitet. In den Diagnoseprüfungen müssen Bedienaktionen **0** bleiben. Der separat gestartete Öffnungsversuch darf höchstens einen Klick/Tipp ausführen; Android-Annahme ist kein Nachweis der Snap-Öffnung.

## Bewusste Grenzen

STOP verhindert neue Anforderungen und verwirft verspätete Ergebnisse. Bereits vom Betriebssystem angenommene Aufnahmen können nicht rückwirkend gelöscht werden, ihr Ergebnis wird freigegeben/verworfen. Im Öffnungsmodus gilt entsprechend: ein bereits an Android übergebener kurzer Tipp ist nicht synchron zurückrufbar. Der Diagnosemodus dispatcht keine Gesten. Overlay-Erreichbarkeit, Pixel-Latenz und Profilzugriff sind erst nach Gerätetest belegt.
