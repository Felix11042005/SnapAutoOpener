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

## Gerätebericht 5.3.1 und Änderung 5.3.2

Der nächste Bericht zeigt stabil `UNSCOPED_AD_OR_MY_AI`, acht Snap-Statusknoten, je einen My-AI-/Werbehinweis, 137 Knoten, keine fehlenden Kinder und kein Knotenlimit. START führte keinen Klick aus. Damit ist die Ausschluss-Zuordnung die aktuelle erste Sperre; der Baum war bei den protokollierten Stichproben vollständig. „Direkte Snap-Klickziele=0“ war hier ebenfalls keine abgeschlossene Messung der Klickfähigkeit, sondern das Ergebnis des vorher gesperrten Erkenners.

Die Quellprüfung findet eine zu starre Annahme: Die zugehörige ganze Zeile musste unmittelbar unter dem erkannten Listenknoten liegen. Ein gemeinsamer großer Inhaltscontainer dazwischen führte zur Ablehnung. 5.3.2 unterstützt solche Container, wenn deren sichtbare Kindzweige vollständig enthalten sind und sich vertikal nicht überschneiden, oder es nur einen Kindzweig gibt. Kleine innere Labels werden nicht als Ersatz für eine ganze Zeile akzeptiert. My AI/Anzeige innerhalb derselben Zeile sperren weiterhin die ganze Zeile; überlappende oder nicht zuordenbare Inhalte bleiben gesperrt.

Das ist eine Korrektur einer durch synthetische Tests nachgewiesenen Schwäche, noch kein Beweis für die tatsächliche Hierarchie auf diesem Pixel. Der alte Bericht enthält keine Vorfahrenstruktur. 5.3.2 zählt deshalb `Statusfelder klickbar` und `Statusfelder mit ACTION_CLICK` auch bei gesperrtem Bildschirm; direkte Ziele werden dann als „nicht ausgewertet“ bezeichnet. Bei erfolglosem ausdrücklichem START wird einmal `ROW_STRUCTURE` mit Markerarten, Begrenzungsrechtecken und Vorfahrenflags protokolliert, ohne Namen, Texte, Nachrichten, Bilder oder Kontakt-IDs. `START_EVIDENCE` beschreibt die genau beim START gelesene Oberfläche.

Der bestehende echte Einzelklick bleibt vorhanden, einschließlich STOP, eingefrorener Auswahl und Frischeprüfung. Keine Koordinaten- oder Unterhaltungsklicks als Ersatz. Bleibt der Pixel-Lauf gesperrt, sind dessen neue Strukturangaben nötig; eine erfolgreiche Snap-Öffnung darf bis dahin nicht behauptet werden.

## Strukturbericht 5.3.2 und Änderung 5.4

Der Bericht vom ausdrücklichen START zeigt My AI unter zwei identischen Zeilencontainern (0,289,1080,415), direkt in einer Liste (0,289,1080,2153). Snap-Statusknoten belegen dagegen die ganzen Zeilen (0,421,1080,601) und (0,604,1080,784), jeweils mit gleich begrenztem Vorfahren. Weder Status noch deren Vorfahren bieten Klickfähigkeit oder ACTION_CLICK an. Ein nativer Direktklick kann dort deshalb nicht funktionieren.

Die Quellprüfung findet eine weitere Sperre: Jede Ausschlusszeile musste bisher unterhalb der Unterkante des Ungelesen-Filters beginnen. Die gemeldete erste Listenzeile kann aber die Filterchips selbst enthalten. 5.4 lässt Ausschlusszeilen unterhalb des Headers zu und schließt sie vollständig aus; Snap-Ziele müssen weiterhin unterhalb des Filters beginnen. Der Bericht enthält keine Filter-/Headergrenzen. Der Regressionstest übernimmt deshalb nur die gemeldeten Vorfahrenrechtecke und verwendet ausdrücklich synthetische Header-/Filterpositionen, statt einen vollständigen Geräte-Replay zu behaupten.

Die strukturelle Snap-Zuordnung setzt nun keine Klickfähigkeit mehr voraus. Der native Klickpfad bleibt streng getrennt. Für nicht anklickbare Statuszeilen kommt ein echter, einmaliger Symbol-Tipp mit zwei übereinstimmenden Zeilenbildern hinzu. [Snapchats Hilfe](https://help.snapchat.com/hc/en-us/articles/7012316500372-How-do-I-view-Snaps) beschreibt gefüllte rote/violette Quadrate als Hinweise auf empfangene Snaps. Farbe allein reicht nicht: exaktes Snap-Statuslabel, klare nicht ausgeschlossene Listenzeile, Form/Eindeutigkeit, Bildübereinstimmung und frische Sitzung/Fensterdaten sind gemeinsam erforderlich. Synthetische Tests dokumentieren auch die verbleibende Mehrdeutigkeit gleich geformter Dekorationen. Tatsächliche Öffnung am Pixel und automatische Alt/Neu-Trennung sind weiterhin nicht nachgewiesen.
