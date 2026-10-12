# Einzelöffnungsmodus – Stand und Pixel-Prüfung

5.7 ergänzt separat [Ungelesen-Aktivierung und mehrseitigen Bestand mit Scrollen](FILTER_SCROLL.md). Der hier beschriebene bisherige Modus bleibt erhalten.

5.6 ergänzt den separat wählbaren [sichtbaren Bestandslauf](VISIBLE_BATCH.md) mit Wartezeit, einmaliger kontextbezogener Zurück-Aktion und bestätigter Listenrückkehr vor dem nächsten ursprünglichen Ziel. Der folgende Abschnitt beschreibt weiterhin den Einzelmodus. Ein erfolgreicher einzelner Snap wurde mit 5.5 vom Nutzer bestätigt; der neue Gesamtablauf ist damit noch nicht am Gerät geprüft.

## Tatsächlich implementiert

Der manuelle START über Snapchat friert den sichtbaren Accessibility-Bestand ein. Der Lauf bedient höchstens ein Ziel und ergänzt keine späteren Ziele. Unvollständige, unklare oder leere Bestände beenden ihn sofort. Änderungen am gesamten Baum oder Fenster vor der Aktion brechen ab. Vorbereitung und Öffnungsversuch haben jeweils 30 Sekunden Zeitlimit.

Ist der genaue „Neuer Snap“-Status selbst anklickbar und bietet ACTION_CLICK an, wird dieser Knoten über seinen Pfad frisch aufgelöst und einmal nativ angeklickt. Ein anklickbarer Vorfahr der Unterhaltung ist kein Ersatz.

Fehlt diese native Klickfähigkeit, kann 5.5 auf Android 14+ tatsächlich einmal tippen: Zwei Screenshots ausschließlich des identifizierten Snapchat-Fensters müssen ein einzelnes gefülltes rotes/violettes Quadrat innerhalb einer eindeutig zugeordneten Kandidatenzeile zeigen. Diese benötigt einen exakten Snap-Status oder einen gezählten abgeschnittenen Status wie „4 neue…“; Letzterer reicht ausschließlich für die anschließende visuelle Prüfung. Die Bilder müssen 300–2500 ms auseinanderliegen, mit gleichen Symbolgrenzen und identischen Pixeln in der ausgewählten Zeile. Der vollständige Baum muss unverändert bleiben. Erst dann wird die frisch erkannte Symbolmitte verwendet; es gibt keine festen Bildschirmkoordinaten oder einen Tipp auf die Mitte der Unterhaltung. STOP, Sitzungskennung, Timeout, aktuelle Fensterdaten und das Budget von einem Versuch werden unmittelbar vor dem dispatchGesture erneut geprüft.

My AI und Werbung werden einschließlich ihrer gesamten Zeile ausgeschlossen, auch wenn sie in der ersten Filterzeile stehen. Tastatur, Eingabefeld, unvollständiger Baum, verändertes/fehlendes Bild oder mehrere passende Symbole in derselben Zeile verhindern den Tipp. Nach abgelehntem Tipp oder Ausnahme gibt es keinen Ersatzversuch. STOP verwirft ausstehende Ergebnisse; ein bereits an Android übergebener 80-ms-Tipp kann noch fertiglaufen. Der Diagnosemodus bleibt ohne Bedienaktionen.

Farbe und Form sind keine eindeutige Identität. Eine gleich geformte Dekoration in einer passenden Zeile kann weiterhin falsch erkannt werden. Das Eckenprofil und die Symbolentscheidungen sind jetzt an den isolierten bereitgestellten Bild-/Videoausschnitten geprüft. Vollständige aktuelle Fenster und das tatsächliche Snapchat-Verhalten müssen am Gerät bestätigt werden. Ein gefülltes violettes Beispiel war in den Aufnahmen nicht enthalten und bleibt synthetisch getestet. Details: [REFERENCE_MEDIA.md](REFERENCE_MEDIA.md).

## Noch offen: gesamter vorhandener Bestand

Der Einzelmodus liefert einen echten Öffnungsversuch. Der neue sichtbare Bestandsmodus ergänzt Zurück und nächste ursprüngliche Zeile; Weiter-Tipps innerhalb eines Viewers, Scrollen zu ungesehenen Beständen, positive Viewer-Erkennung und stabile Identitäten einzelner Snaps fehlen weiterhin. Die Rücknavigation ist kontextbezogene Wiederherstellung, kein Snap-Nachweis. Details und Grenzen: [VISIBLE_BATCH.md](VISIBLE_BATCH.md).

Ein eingefrorener Accessibility-Baum ist keine serverseitige Liste einzelner Snap-IDs. Neue Inhalte derselben Unterhaltung können ohne unterscheidbare Informationen erscheinen. Deshalb ist keine strikte Alt/Neu-Trennung einzelner Snaps zugesichert. Snapchat beschreibt außerdem, dass [mehrere empfangene Snaps automatisch in Reihenfolge abgespielt werden](https://help.snapchat.com/hc/en-us/articles/7012316500372-How-do-I-view-Snaps). Ein Tipp garantiert daher nicht, dass nur ein Snap sichtbar wird. Die App überwacht keine eingehenden Nachrichten im Hintergrund.

## Pixel 8 testen

1. APK aktualisieren und Bedienungshilfe aktivieren. Bereits ungeöffneten Snap bereithalten.
2. „Öffnungsmodus bereitstellen“ drücken, gewünschte Snapchat-Instanz manuell öffnen und Chatliste anzeigen. Im vertraulichen Profil muss die Oberfläche tatsächlich zugänglich sein.
3. START im schwebenden Bedienfeld drücken und die Liste ruhig halten. Nach ungefähr zwei Bildprüfungen kann einmal das gefüllte Snap-Symbol angetippt werden. Prüfen, welche Ansicht erscheint: gewünschter Snap, normaler Chat oder keine Reaktion. Es wird keine Nachricht verschickt.
4. Der Bericht enthält START_EVIDENCE, INITIAL_SELECTION, VISUAL_HINT, VISUAL_CONFIRMATION und OPEN_RESULT. SECOND_IMAGE_REQUIRED zeigt die erste Bildprüfung, TWO_IMAGES_CONFIRMED die Übereinstimmung. TAP_DISPATCH_ACCEPTED bedeutet nur Android-Annahme, TAP_COMPLETION nur Gestenabschluss; beide beweisen keine Snap-Öffnung. Beim nativen Weg bleibt CLICK_ACCEPTED erhalten.
5. STOP während der Bildprüfung testen; außerdem Liste ändern, Tastatur öffnen und Bildschirm sperren. Es darf kein neuer Tipp aus ausstehenden Ergebnissen folgen. Diagnose separat testen: keine Aktionen.
6. Bei fehlender Reaktion Bericht kopieren. Keine Namen oder Snap-Inhalte nötig; zusätzlich genügt die Angabe, welche Ansicht nach dem Versuch erschien.

Native Fensteraufnahme, Gestenverhalten, Private Space und Snap-Viewer lassen sich durch Unit-Tests allein nicht bestätigen.
