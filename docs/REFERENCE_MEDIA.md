# Referenzaufnahmen und Änderung 5.5

Die bereitgestellte PNG hat 864×1920 Pixel, das Video 1080×2400 Pixel und etwa 16,5 Sekunden Dauer. Die Medien wurden lokal ausgewertet. Im Repository liegen nur kleine isolierte, auf 16 Farben quantisierte Symbol-Testdaten, keine vollständigen Aufnahmen, Namen, Unterhaltungen oder Snap-Inhalte.

## Sichtbare Erkenntnisse

- Die PNG zeigt rote und violette Umrissquadrate mit etwa 30 % Farbfüllung sowie blaue Pfeile/Chat-Symbole. Kein sichtbares gefülltes rotes/violettes Statusquadrat darf daraus als ungeöffneter Snap ausgewählt werden.
- Das Video zeigt in der Chatliste drei gefüllte rote Quadrate mit etwa 93 % Farbfüllung und abgerundeten Ecken. Die daneben sichtbaren Statusangaben enthalten unter anderem eine Anzahl von 2 und abgeschnittene Anzahlen von 4 bzw. 9+. Die vollständigen Accessibility-Texte lassen sich daraus nicht rekonstruieren.
- „Ungelesen 9+“ ist im Video als graue Kapsel hervorgehoben. My AI ist ein benachbartes Element der Filterleiste, keine zwingend vorhandene My-AI-Unterhaltung. Die exakte Filterposition wird nicht fest einprogrammiert. Die Auswahlsemantik bleibt UNKNOWN, wenn Android keine eindeutige Auswahl meldet; eine graue Fläche allein wird nicht automatisch als Auswahlbeweis verwendet.
- Das Video zeigt V4.8, danach eine Bildansicht und anschließend eine Antworttastatur. Es belegt weder das Verhalten von 5.4/5.5 noch, ob manuelle oder automatische Eingaben die Übergänge verursachten. Es liefert keine verlässliche Viewer-/Rückkehrsemantik für eine Stapelschleife.

Snapchats [offizielle Symbolhilfe](https://help.snapchat.com/hc/de/articles/7012315702548-Was-bedeuten-die-Icons-auf-dem-Chat-Bildschirm) unterscheidet gefüllte empfangene Snap-Quadrate von betrachteten Umrissen und Chat-/Sende-Symbolen. Rot steht für einen Snap ohne Audio, Violett für einen Snap mit Audio; die Farbe allein ist keine sichere Foto-/Video-Unterscheidung.

## Implementierte Änderungen

1. Ein normalisiertes 16×16-Eckenprofil aus dem ersten gefüllten Videosymbol ergänzt Füllgrad, Seitenverhältnis und gefüllten Kern. Es enthält weder Bildschirmpositionen noch private Daten. Weitere tatsächliche Symbole und eine heruntergerechnete Variante prüfen die Erkennung unabhängig vom ersten Ausschnitt. Umrisse, blaue Symbole und der runde Benachrichtigungspunkt geben kein Öffnungsziel. Die Symbolmitte muss außerdem in der unteren Hälfte der erkannten ganzen Zeile liegen, entsprechend dem Status unterhalb des Kontaktnamens in den Aufnahmen. Farbige Namensdekorationen oberhalb dieser Grenze können kein Ziel ersetzen; andere Zeilenlayouts können dadurch vorsichtig abgelehnt werden.
2. Exakte DE/EN-Statuslabels erlauben jetzt Anzahlen mit „+“. Der Filter kann sein Anzahl-Badge im selben Label enthalten. „Weitere ungelesene Chats“ ist kein Filterlabel.
3. Ein ausschließlich gezähltes abgeschnittenes Label wie „4 neue…“ wird als unsicherer visueller Kandidat ausgewiesen. Es ist keine Snap-Bestätigung und darf niemals nativ angeklickt werden. Erst das eindeutige gefüllte Symbol, zwei übereinstimmende Zeilenbilder und unveränderte vollständige Fensterdaten können einen einmaligen Tipp erlauben. Neue Chats mit blauen Symbolen und geöffnete Umriss-Snaps bleiben ausgeschlossen.
4. Ein Statusknoten mit den Grenzen der ganzen Unterhaltung ist auch dann kein direktes Klickziel, wenn Android ACTION_CLICK meldet. Der native Weg erfordert einen separaten Statusbereich mit höchstens halber Zeilenhöhe. Dadurch wird die beobachtete Ganzzeilen-Struktur nicht als normaler Unterhaltungsklick verwendet.
5. Diagnose führt keine Aktionen aus. Der Öffnungsmodus dispatcht höchstens einen Klick/Tipp. STOP, Sitzung, Zeitlimit, Bild-Frische und die eingefrorene START-Auswahl bleiben Voraussetzung. Es gibt keinen weiteren Tipp, der in der Bildansicht ein Antwortfeld aktivieren könnte. Android/Snapchat-Reaktionen auf den einen Tipp müssen weiterhin am Gerät geprüft werden.

## Prüfung und offene Grenzen

Die Referenztests prüfen zehn tatsächliche Glyph-Ausschnitte, getrennt von synthetischen Varianten. Ein Integrationstest verbindet das Videosymbol mit einem abgeschnittenen, eingefrorenen Status, zwei Bildbestätigungen und genau einem Aufruf des Tippadapters. Das ist kein echter Android-Gestentest. Gefüllte violette Symbole sind nur synthetisch belegt. Helles Theme, andere Skalierungen und ganze aktuelle Snapchat-Fenster bleiben Geräteprüfungen.

Eine gleich geformte Dekoration in einer passenden Kandidatenzeile bleibt eine mögliche Verwechslung. Der Öffnungsmodus verarbeitet noch keinen vollständigen Bestand automatisch. Einzelne alte/neue Snaps innerhalb derselben Unterhaltung sind ohne stabile Snap-IDs nicht zuverlässig trennbar; Snapchat kann nach einem Öffnen selbst mehrere Snaps abspielen. Anleitung und APK-Pipeline: [OPENING_MODE.md](OPENING_MODE.md), [VALIDATION.md](VALIDATION.md).
