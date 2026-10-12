# 5.8.1: Ungelesen und mehrseitiger Bestand

Der separat gestartete Modus **Bestand mit Ungelesen und Scrollen – bereitstellen** erweitert den sichtbaren Bestandslauf. Die anderen Modi bleiben verfügbar. Der Diagnosemodus führt keine Bedienaktionen aus.

## Ablauf

1. Modus in SnapAutoOpener bereitstellen, gewünschte Snapchat-Instanz selbst öffnen und für eine vollständige Erfassung am Listenanfang stehen. Innerhalb 30 Sekunden START im schwebenden Bedienfeld drücken. Ein vollständiges, eindeutiges Chatlistenfenster ist Voraussetzung. Bei START mitten in der Liste werden Zeilen darüber nicht erfasst.
2. Exakten DE/EN-Ungelesen-Filter finden. Native Auswahlsemantik hat Vorrang; bei unbekannter Semantik kann das dunkle Layout anhand des an diesem Label verankerten grauen Chips erkannt werden. Widersprüchliche Evidenz bleibt unbekannt. Zwei Bilder mit gleichem Filterzustand und unverändertem vollständigem Accessibility-Baum sind nötig. Einen bereits ausgewählten Filter nicht antippen; einen eindeutig nicht ausgewählten Filter höchstens einmal aktivieren und anschließend erneut prüfen. Unbekannt bedeutet STOP.
3. Ab der sichtbaren Seite sofort abwärts durch die Liste gehen und ausschließlich semantisch zugeordnete Snap-Zeilen mit in beiden Bildern bestätigtem gefülltem Symbol oder exaktem nativem Snap-Status-Klickziel erfassen. Keine vorgeschaltete Aufwärtssuche. Kontakte und Statuslabels werden über Seitenwechsel nur als In-Memory-Hashes gespeichert. Überschneidende Seiten werden dedupliziert. Doppelte Namen oder veränderte Snap-Zahlen werden ausgeschlossen.
4. Erst nach Abschluss dieser Erfassung wird die Zielmenge versiegelt. Noch auf der unteren Seite ursprüngliche passende Ziele öffnen und für verbleibende Ziele aufwärts zurückscrollen. Neue Kontakte kommen nicht mehr hinzu. Noch nicht sichtbare ursprüngliche Kontakte bleiben vorgemerkt; geänderte, mehrdeutige oder nicht erneut bestätigte Ziele werden übersprungen. Ein Symbol-Tipp verlangt weiterhin zwei identische Zielzeilenbilder einschließlich Symbolposition; gelockerte Navigationsbilder allein erlauben keinen Tipp.
5. Jede Zielzeile erhält höchstens einen Öffnungsversuch. Nach abgeschlossener angenommener Öffnungsaktion mindestens fünf Sekunden warten, gegebenenfalls einmal kontextbezogen Android-Zurück ausführen und wiederholte Chatlistenerkennung abwarten. Keine Viewer-Tipps oder Viewer-Screenshots. STOP beendet ausstehende Arbeit und entwertet alte Callbacks.

## Scrollen und Grenzen

Die frisch aufgelöste eindeutige vertikale Liste wird immer über eine begrenzte Geste gescrollt. Native Scrollaktionen werden seit 5.8.1 nicht mehr bevorzugt, weil sie die Überlappung zwischen Seiten verlieren können. Es erfolgt eine 450-ms-Wischbewegung von der Mitte der erkannten Listenfläche: Finger zu 25 Prozent = weiter unten in der Liste; Finger zu 75 Prozent = zurück nach oben. Filter und untere Navigation liegen außerhalb. Die Endpunkte dürfen nicht in einer ausgeschlossenen Werbe-/My-AI-Zeile liegen. Jede Aktion prüft Sitzung, Modus, Fenster, unveränderten Baum, Bildbeweis und Timeout nochmals unmittelbar vor dem Android-Aufruf. Ablehnung einer Filter-/Wischaktion, Ausnahme oder fehlender Abschluss stoppt ohne Ersatzversuch.

Das Listenende/-anfang ist eine **Heuristik**: zwei Scrollversuche ohne relevante Bewegung eindeutig zugeordneter Textanker innerhalb der Liste. Zittern unter 0,5 Prozent der Fensterhöhe (mindestens vier Pixel) und veränderte Dekorationen setzen diesen Zähler nicht zurück. Gegenläufige Bewegung stoppt mit `SCROLL_DIRECTION_REVERSED`, nicht belegbare Bewegung mit `SCROLL_MOVEMENT_UNVERIFIED`. Fehlen gemeinsame eindeutige Anker, können nur identische Semantik und Listenpixel Stillstand belegen. Eine angenommene, aber wirkungslose Geste kann ein Listenende oder einen Fehler bedeuten. Deshalb kann die Erfassung unvollständig enden; abgelehnte Gesten stoppen sofort. Maximal 50 unterschiedliche Erfassungsseiten, 200 eindeutige Zielzeilen, 300 Scrollversuche und 20 Minuten Gesamtlaufzeit; jeder einzelne Vorgang hat weiter eine 30-Sekunden-Frist. Bei Erfassungslimits wird vor dem Öffnen abgebrochen. Bei einem späteren Limit wird der Rest nicht weiter bedient. Große Listen können einen neuen manuellen Durchlauf benötigen.

**Kein atomarer START-Bestand:** Die erste Erfassung dauert und Snapchat liefert keine eindeutigen Snap-IDs. Während dieser Erfassung neu eintreffende Snaps können in den erfassten Bestand gelangen. Nach Versiegelung werden keine neuen Zeilen aufgenommen; derselbe Kontakt mit unverändertem/abgeschnittenem Status bleibt dennoch nicht sicher in alte und neue einzelne Snaps zerlegbar. Ein Kontakt wird nur einmal angetippt; Snapchat kann mehrere Snaps selbst nacheinander anzeigen. Vollständiges Abspielen aller einzelnen Snaps ist nicht belegt. Fünf Sekunden können ein Video verkürzen. Angenommene Aktionen sind keine bestätigten Öffnungen.

Das helle Theme wird visuell nicht geraten. Falls der Filter beim Scrollen verschwindet, bleibt der zuvor bestätigte Kontext nur im selben Fenster erhalten. Header, eindeutige Listenstruktur, vollständiger Baum und fehlendes Eingabefeld bleiben zwingend. Sobald der Filter wieder sichtbar ist, muss sein aktiver Zustand erneut bestätigt werden. Bildschirm-/Profilwechsel, Sperre oder Tastatur führen zum Stopp. Private Space wird weiterhin manuell geöffnet.

## Pixel-Abnahme

Noch nicht am Gerät durchgeführt. Zuerst mit wenigen eigenen empfangenen Snaps und bekannten Listenzeilen prüfen:

| Fall | Erwartung |
|---|---|
| Ungelesen aus | Ein Filter-Tipp, anschließend bestätigte Auswahl vor Scrollen |
| Ungelesen bereits aktiv | Kein Filter-Tipp, Filter bleibt an |
| Mehr als eine Bildschirmseite, Start oben | Zuerst abwärts erfassen (Finger nach oben); danach unten öffnen und aufwärts zurückkehren |
| Start mittig | Nur sichtbare Seite und Seiten darunter erfassen; frühere Zeilen bleiben außerhalb |
| Gemeldeter 5.7-Fall: acht Symbole, keine nativen Klickziele, 1–2 Pixel Zittern | `SCAN` statt `TOP_SCAN`; nach zwei Stillstandsprüfungen `OPEN` und bei gültigem Zielbeweis ein Symbol-Tipp |
| Seiten nur mit normalen ungelesenen Chats | Scrollen weiter erlaubt, keine Chat-Öffnung |
| Werbung/My AI zwischen Zielen | Keine Öffnung dieser Zeilen; unklare Zuordnung stoppt |
| Neue andere Snap-Zeile nach abgeschlossener Erfassung | Nicht öffnen |
| Geänderte Zahl bei einem ursprünglichen Kontakt | Überspringen |
| STOP beim Filter, Scrollen, Bildprüfung, Öffnen, Warten oder Zurück | Keine nachfolgende Aktion; bereits dispatchte kurze Geste kann enden |
| Tastatur, Appwechsel, Profil-/Display-Sperre | Sofortiger Stopp, kein automatischer Neustart |
| Filter unklar, Baum unvollständig, Scroll abgebrochen | Stopp ohne geratenen Ersatzklick |
| Listenende | Zwei unveränderte Seitenprüfungen; Bericht und tatsächliches Ende vergleichen |

Bericht enthält `UNREAD_CONFIRMED`, `LIST_ROUTE` (nativer Filterklick oder Wischrichtung), `SCROLL_EVIDENCE` (anonyme Ankerzahlen und genauer Bewegungsgrund), `LIST_NAVIGATION`, `INVENTORY_PAGE` mit beobachteter Bewegung und bestätigten Zielzahlen sowie getrennte Öffnungsversuche/angenommene Klicks. Das Bedienfeld zeigt während der Erfassung ausdrücklich „Noch keine Öffnung“. Namen/Bilder werden nicht protokolliert. Bericht zusammen mit beobachteter Filterwirkung, Scrollrichtung und Rückkehrverhalten zurückgeben.

Die bisherige Build-APK-Pipeline und Release-Signierung bleiben unverändert. Bestehende Einzel-/sichtbarer-Bestand-Tests laufen weiter. Neue Offline-Tests prüfen Filterausschnitte aus der Referenzaufnahme, Seitenbeweise, mehrseitiges Inventar, Deduplizierung, Limits und Navigation bis zur austauschbaren Aktionsgrenze. Sie ersetzen keine Android-/Pixel-Abnahme. Bisher ist vom Nutzer nur die tatsächliche Einzelöffnung mit 5.5 bestätigt.
