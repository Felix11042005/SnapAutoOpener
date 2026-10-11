# Diagnosebericht und Korrektur 5.2.1

Ein vom Nutzer übermittelter Bericht aus 5.2 auf Android API 37 zeigt nach 3 Sekunden `AD_OR_MY_AI`, nach 16 Sekunden `INCOMPLETE_TREE`, bei 17 Sekunden Nutzer-STOP. Es gab keine Bedienaktionen. Dieser Bericht belegt sichtbare Snapchat-Fensterdaten während Teilen des Laufs; er identifiziert nicht das Android-Profil und belegt keine erfolgreiche Snap-Erkennung oder Screenshot-Aufnahme.

## Ursache

5.2 prüfte zuerst, ob irgendwo in den sichtbaren Labels My AI oder ein Werbemerkmal vorkommt, und sperrte dann den gesamten Bildschirm. Daher wurden Filter und Status-Zeilen überhaupt nicht ausgewertet. Die ausgegebenen Nullen waren Standardwerte, keine gemessene Zahl vorhandener Snaps. Der Bericht unterscheidet nicht, welches Label auslöste. Die My-AI-Zeile ist eine plausible, noch nicht am Gerät bestätigte Ursache.

`INCOMPLETE_TREE` konnte in 5.2 ungültige Fenstergrenzen, das Limit von 900 Knoten oder ein nicht verfügbares Kind bedeuten. Ohne weitere Zähler lässt sich die konkrete Ursache des gemeldeten Laufs nicht bestimmen.

## Korrektur

- Erst Listenstruktur, Header und Filter prüfen; Ausschlussmerkmale in eindeutig zugehörige vollständige Listenzeilen einordnen. Diese Zeilen einschließlich aller verschachtelten Statusknoten werden ausgeschlossen. Bei überlappenden Zeilen gilt der Ausschluss ebenfalls.
- Ein nicht zuordenbarer oder abgeschnittener My-AI-/Werbehinweis bleibt eine Bildsperre. Ein unvollständiger Baum wird weiterhin nicht zur Erkennung freigegeben.
- Bericht zeigt getrennte, anonyme Zähler für Header, Filter, Listen, Statuslabels und Ausschlüsse sowie Knotenlimit und fehlende Kinder. „Nicht ausgewertet“ ersetzt irreführende Nullwerte bei vorzeitiger Sperre.
- Bedienaktionen bleiben implementiert. Diagnose startet keine davon. Die bestehende ActionPolicy sperrt Interaktionen weiterhin auf dem gesamten Bildschirm, sobald ausgeschlossene Inhalte sichtbar sind; die neue Diagnosefreigabe lockert diese Grenze nicht.

## Erneuter Test

Nach Installation von 5.2.1 Diagnose starten, gewünschte Snapchat-Kopie manuell öffnen, Chatliste/Ungelesen anzeigen und einige Sekunden ruhig halten. Danach STOP und Bericht kopieren. Interessant sind `Header`, `Filterlabels`, `Listen`, `My-AI-Hinweise`, `Werbehinweise`, `ausgeschlossene Zeilen`, `Knotenlimit` und `fehlende Kinder`. Die neue Version kann weiterhin unbekannte Zustände melden; die zusätzlichen Daten sollen die nächste Änderung begründen, statt weitere Annahmen zu treffen.

Unit-Tests ergänzen den gemeldeten Ausschlussfall, verschachtelte Klickknoten, Werbezeilen, überlappende Zeilen, unvollständige Bäume und Datenschutz des Berichts. Erneute Pixel-Abnahme steht aus. APK-Bereitstellung erfolgt weiterhin über die bestehende Build-APK-Pipeline und `SnapAutoOpener-apk`.
