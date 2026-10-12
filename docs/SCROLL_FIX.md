# 5.8: gemeldete Scrollschleife und Vergleichsprojekte

Der 5.7-Gerätebericht bestätigt die erfolgreiche Ungelesen-Aktivierung und acht visuell erkannte gefüllte Snap-Symbole. Trotzdem folgen vier `TOP_SCAN / SEEK_LIST_TOP`-Aktionen ohne Erfassung oder Öffnung. Die Symbolpositionen verschieben sich nur um ein bis zwei Pixel. Die vorgeschaltete Suche nach dem Listenanfang und ein von exakten Rechtecken und Bildpixeln abhängiger Stillstandszähler verhindern den Phasenwechsel.

## Umsetzung

- Keine vorgeschaltete Suche nach oben. Ab der sichtbaren Startseite nach unten erfassen, anschließend die eingefrorenen Ziele von unten auf dem Rückweg öffnen. Für die gesamte Liste vor START oben stehen; dies ist in der App erklärt.
- Listenrichtung und Fingerrichtung explizit trennen: weiter nach unten bedeutet Finger nach oben. Die relative Wischbewegung beginnt in der Listenmitte und endet bei 25 beziehungsweise 75 Prozent der Fläche.
- Nach einer Aktion die tatsächliche Bewegung gemeinsamer eindeutiger Textanker vergleichen. Kleine Positionsschwankungen tolerieren; Gegenrichtung und unklare Bewegung stoppen. Zwei bestätigte Stillstände gelten weiterhin nur als heuristische Grenze.
- Navigation bestätigt vollständigen unveränderten Baum und Filterzustand in zwei Bildern. Dekorative Pixeländerungen blockieren den gesamten Lauf nicht mehr. Öffnung verlangt weiterhin identische Pixel der konkreten Zielzeile in beiden Bildern und frische Autorisierung direkt am Aktionsadapter.
- Bericht und Bedienfeld unterscheiden Erfassung, beobachtete Bewegung und Öffnung. Angenommene Android-Aktionen sind weiterhin kein Nachweis einer Snap-Anzeige.

## Gelesene Primärquellen

1. [PurrfectSnap: AutoOpenSnaps.kt](https://github.com/dblount513-dev/purrfectsnap/blob/dev/core/src/main/kotlin/me/eternal/purrfectsnap/core/features/impl/experiments/AutoOpenSnaps.kt) und [README](https://github.com/dblount513-dev/purrfectsnap/blob/dev/README.md): Das untersuchte Projekt verwendet Hooks und interne Conversation-/Message-IDs. Die Warteschlange führt unter anderem `MessageUpdate.READ` über den internen ConversationManager aus. Das ist kein Accessibility-Verfahren zum Scrollen oder Nachweis einer sichtbaren Wiedergabe. Übertragbar sind begrenzte Warteschlangen, Deduplizierung und getrennte Aktionsprüfung; diese Konzepte passen zum vorhandenen Bestandsmodell. Hooks, interne Nachrichten-APIs und direktes Gelesen-Markieren wurden nicht übernommen.
2. [openatx/uiautomator2: swipe.py](https://github.com/openatx/uiautomator2/blob/master/uiautomator2/swipe.py) und [README](https://github.com/openatx/uiautomator2): relative Wischflächen und die Trennung von Scrollrichtung und Fingerrichtung dienen als Orientierung für eine kurze überlappende Bewegung aus der Listenmitte.
3. [Appium UiAutomator2: Android mobile gestures](https://github.com/appium/appium-uiautomator2-driver/blob/master/docs/android-mobile-gestures.md): `scrollGesture` liefert Information über weiteres Scrollen. Accessibility stellt hier keinen gleichwertigen verlässlichen Rückgabewert bereit; daher trennt die App angenommene Aktion und anschließend beobachteten Listenfortschritt.

Kein fremder Implementierungscode wurde kopiert. Der bestehende Android-Accessibility-Adapter bleibt die einzige Stelle für Bedienaktionen. Snapchat wird nicht verändert.

## Prüfungen und Grenzen

204 lokale Kotlin/JUnit-Tests bestehen. Der neue `ReportedScrollLoopTest` bildet acht erkannte Symbole ohne native Klickziele, wechselnde Seitenpixel und 1–2 Pixel Zittern synthetisch nach. Er prüft die Abwärtsnavigation und erreicht nach zwei bestätigten Stillständen den tatsächlichen austauschbaren Symbol-Tippadapter. Weitere Tests prüfen verkehrte Richtung, unbekannte Bewegung, Seitenwechsel, STOP und getrennte Navigations-/Öffnungsbeweise.

Dies ist keine Wiedergabe des vollständigen Gerätedumps und kein Test echter Android-Gesten. Android-Kompilierung und Lint laufen über die bestehende GitHub-Actions-Pipeline. Die Pixel-Abnahme steht aus. Abgewiesenes Scrollen kann weiterhin wie eine Listengrenze aussehen, eine Erfassung ist nicht atomar und ein Öffnungsversuch pro Kontakt garantiert keine vollständige Wiedergabe jedes einzelnen Snaps. Siehe [Ablauf und Gerätetest](FILTER_SCROLL.md).
