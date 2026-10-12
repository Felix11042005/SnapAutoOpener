# 5.8.1: gezielte Korrektur des Scrollwegs

## Belegt durch den 5.8-Bericht

- Ungelesen wurde über den nativen Filterklick aktiviert und in zwei Bildern bestätigt.
- Acht visuelle Zielzeilen wurden in den Bestand aufgenommen.
- Der erste Abwärtsschritt verwendete `native=4096` (`ACTION_SCROLL_FORWARD`), keine begrenzte Wischbewegung.
- Die nächste erkannte Chatliste hatte andere Symbolpositionen, neun Statuszeilen und keinen sichtbaren Filter. Die Bewegungsprüfung lieferte `UNVERIFIED`; der Lauf stoppte vor jedem Öffnungsversuch.

Nicht belegt ist, ob die Seiten keine gemeinsamen Textanker hatten, auf einer Seite eindeutige Anker fehlten oder gemeinsame Anker widersprüchlich verschoben waren. Der alte Bericht unterscheidet diese Fälle nicht. Das einzelne mitgeschickte Bild ersetzt keinen Vorher-/Nachher-Vergleich. Es wird nicht ins Repository übernommen.

Der Nutzer bestätigt anschließend, dass einige Kontakte nach dem Scrollen weiterhin sichtbar waren. Fehlende sichtbare Überlappung ist deshalb keine ausreichende Erklärung. Die alte Zuordnung verwirft mehrfach auftretende Labels global, auch wenn mehrere Accessibility-Knoten denselben Namen innerhalb einer Zeile tragen. Ob genau diese Knotenstruktur auf dem Gerät vorliegt, ist ohne Baumdaten nicht bewiesen.

## Ursache im Entwurf und begrenzte Änderung

Die Bewegungsprüfung braucht gemeinsame eindeutige Textanker. Der bevorzugte native Scrollauftrag begrenzte jedoch die Scrollstrecke nicht und konnte dadurch die für die Prüfung nötige Überlappung verlieren. Die [Android-Referenz](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo.AccessibilityAction#ACTION_SCROLL_FORWARD) beschreibt eine Vorwärtsaktion; eine bestimmte Strecke wird mit dem bisherigen Aufruf nicht vereinbart.

Für gemeinsame Snap-Kontakte verwendet die Bewegungsprüfung jetzt zuerst die bereits zur Bestandserfassung verwendete Kontaktidentität je erkannter Zeile. Wiederholte Labels innerhalb derselben Zeile werden zusammengefasst, während gleiche Kontaktidentitäten in verschiedenen Zeilen mehrdeutig bleiben. Verglichen wird die Verschiebung der zugehörigen Zeilenmitte. Fehlen gemeinsame Kontaktzeilen, bleibt die konservative Prüfung einzelner Textanker verfügbar, etwa für Seiten mit normalen Chats. Widersprüchliche Bewegungen stoppen weiterhin.

Listen verwenden jetzt immer die bereits vorhandene relative 450-ms-Geste von der Mitte zu 25 beziehungsweise 75 Prozent der geprüften Listenfläche. Das gilt auch, wenn die Liste native Scrollaktionen anbietet. Filteraktivierung verwendet weiterhin einen exakt aufgelösten nativen Klick, falls vorhanden. Die tatsächliche Inhaltsbewegung bleibt von Snapchat abhängig und wird weiterhin nachträglich geprüft. Es gibt keinen zusätzlichen Ersatzversuch nach abgelehnter oder abgebrochener Geste.

`SCROLL_EVIDENCE` protokolliert die Quelle (`CONTACT_ROW` oder `NODE_LABEL`), die Anzahl gemeinsamer Kontaktzeilen und nur weitere Anzahlen: eindeutige Anker vorher/nachher, gemeinsame Anker, aufwärts/abwärts/stationär. Gründe sind `ANCHORS_MISSING`, `NO_COMMON_ANCHORS`, `CONFLICTING_MOVEMENT`, `DIRECTION_CONFIRMED`, `DIRECTION_REVERSED`, `ANCHORS_STATIONARY`, `IDENTICAL_PAGE` oder ungültige Oberfläche. Namen, Texte und Hashwerte werden nicht gespeichert. Die bestehende Abbruchbedingung wird nicht aufgeweicht.

Öffnungsmodus, eingefrorener Bestand, STOP, Zeitlimits und frische Zielprüfung bleiben erhalten. Die Änderung behauptet weder garantierte Überlappung noch einen funktionierenden Gesamtdurchlauf.

## Prüfung

Offline wird ein kompletter Austausch von acht Zeilen als `NO_COMMON_ANCHORS` nachvollzogen. Ein kürzerer Übergang mit sechs gemeinsamen Zeilen und verschwundenem Filter bestätigt dagegen die Abwärtsbewegung und erweitert den Bestand auf zehn Ziele, ohne bereits zu öffnen. Fehlende und gegensätzlich bewegte Anker werden getrennt getestet. Ein zusätzlicher Regressionstest verdoppelt Namensknoten innerhalb jeder Zeile auf der ersten Seite, entfernt diese Duplikate nach dem Scrollen und bestätigt weiterhin sechs gemeinsame Kontaktzeilen. Gleichnamige Kontakte in verschiedenen Zeilen bleiben ausgeschlossen. Diese Daten sind synthetisch; kein vollständiger Gerätedump liegt vor. Android-Build und Lint laufen über die bestehende Pipeline, Signierung bleibt unverändert.

Nächster Gerätetest: Chatliste oben, Modus „Bestand mit Ungelesen und Scrollen“, START. Nach dem ersten Scrollen muss `LIST_ROUTE kind=DOWN; gesture=...` erscheinen. Im Bericht sollte `SCROLL_EVIDENCE` gemeinsame Anker und `DIRECTION_CONFIRMED` zeigen. Falls der Lauf erneut abbricht, ist der genaue Grund mit Ankerzahlen entscheidend. Mit einer kurzen Aufnahme nur der Chatliste lässt sich prüfen, ob Kontakte überlappen. Bei falscher Oberfläche STOP; für diesen Test höchstens 60 Sekunden. Private Snap-Inhalte sind nicht erforderlich.
