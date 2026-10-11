# Öffnungsmodus 5.3 – Stand und Pixel-Prüfung

## Tatsächlich implementiert

Der eigene START-Button ruft einen echten Android-`ACTION_CLICK` auf dem erkannten Snap-Status auf. Das ist kein simulierter Erfolg und keine reine Diagnose. Der native Adapter löst denselben Knoten über seinen Pfad im aktuell fokussierten Snapchat-Fenster neu auf, vergleicht dessen Merkmale und prüft anschließend Sitzung, STOP, Timeout und den gesamten aktuellen Baum erneut.

Der erste vollständig erkannte sichtbare Bestand wird eingefroren. Weitere Ziele werden während des Laufs nicht ergänzt. Eine veränderte Liste beendet den Versuch. Ein leeres Ergebnis wartet nicht auf neu eintreffende Snaps. Der Bestand entsteht erst, wenn die gewünschte Snapchat-Chatliste sichtbar ist, nicht schon beim START in der separaten App.

Es wird höchstens ein direkt anklickbarer Status bedient. My AI und Werbung sind ausgeschlossen; unklare Zuordnung, Tastatur, Eingabefeld, unvollständiger Baum oder fehlendes Klickziel verhindern die Bedienung. Ein Klick auf die ganze Unterhaltung oder eine geschätzte Bildschirmposition ist kein Ersatz.

## Noch nicht fertig: gesamter vorhandener Bestand

Das Ziel bleibt, die bei Sitzungsbeginn bereits vorhandenen ungeöffneten Snaps abzuarbeiten und später eintreffende auszuschließen. Version 5.3 liefert dafür den ersten echten Öffnungsversuch, noch keine Stapelverarbeitung. Der bisherige Gerätebericht enthält Listenelemente, aber keinen Nachweis für Klickfähigkeit oder die Struktur der Snap-Ansicht. Ein zuverlässiger automatischer Wechsel zum nächsten Snap und zurück ist damit nicht belegt.

Ein eingefrorener Accessibility-Baum ist zudem keine serverseitige Liste einzelner Snap-IDs. Neue Inhalte derselben Unterhaltung können ohne ausreichend unterscheidbare Informationen auftreten. Ohne solche Merkmale darf keine strikte Alt/Neu-Trennung einzelner Snaps behauptet werden. Die Anwendung überwacht keine eingehenden Nachrichten im Hintergrund.

Nächste Schritte: direkten Öffnungsversuch am Pixel bestätigen; eine datensparsame Beschreibung der Snap-Ansicht erfassen; eindeutige Viewer-Erkennung und bestätigte Rückkehr implementieren; vorhandene Einträge über stabile Identitäten zuordnen; endlich begrenzte Warteschlange testen. Bis diese Voraussetzungen erfüllt sind, bleiben automatische Weiter-/Zurück-Aktionen gesperrt.

## Pixel 8 testen

1. Bestehende APK aktualisieren, Bedienungshilfe aktivieren. Eine Unterhaltung mit bereits ungeöffnetem Snap für den Versuch bereithalten.
2. „START – einen Snap öffnen“ drücken und die gewünschte Snapchat-Instanz mit sichtbarer Chatliste öffnen. Im vertraulichen Profil muss dessen Oberfläche tatsächlich zugänglich sein.
3. Der Lauf erfasst den ersten erkannten sichtbaren Bestand und klickt höchstens ein direktes Snap-Statusziel. Prüfen, ob tatsächlich der Snap und kein normaler Chat geöffnet wurde. Es wird keine Nachricht versandt.
4. Der Bericht nennt `INITIAL_SELECTION`, `direkte Snap-Klickziele` und gegebenenfalls `OPEN_RESULT`. `CLICK_ACCEPTED` bedeutet nur, dass Android den Klick angenommen hat. Nicht als geöffnete-Snaps-Zähler auslegen.
5. STOP vor dem Klick testen; danach darf keine Aktion folgen. Zusätzlich während der Wartephase Liste ändern, Tastatur öffnen oder Bildschirm sperren. Der Lauf muss ohne Ersatzklick abbrechen.
6. Diagnosemodus separat prüfen: keine Klicks. Bei fehlendem Klickziel Bericht kopieren. Keine privaten Namen oder Snap-Inhalte nötig; zum Test des Öffnens genügt außerdem die Angabe, welche Ansicht erschien.

Native Knotenauflösung, tatsächliches Klickverhalten, Private Space und Snap-Viewer lassen sich durch die synthetischen Unit-Tests allein nicht bestätigen.
