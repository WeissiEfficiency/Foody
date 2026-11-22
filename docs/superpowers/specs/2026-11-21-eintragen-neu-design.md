# Spec: Eintragen neu (Teil B)

Stand 2026-11-21, vom Nutzer freigegeben (samt „Meine Entscheidungen“). Status: umgesetzt (#297, #298, #299, Doku und
ADR 0021 in #300). Grundlage: Alltagstest von 3.9.0 auf dem Handy (OnePlus CPH2645, 361 dp breit, Schrift 90 %) und
das Gespräch nach Teil A („Einheitlicher Rahmen“, `2026-11-20-einheitlicher-rahmen-design.md`).

## Warum

Eintragen ist das, was der Nutzer am häufigsten tut, und es fühlt sich heute nicht wie ein Teil der App an:

- Jeder Stil baut es anders. Milchglas hat ein Blatt mit den Chips „Rezept · Zutat · Plan · Frei“, Scannen und Foto
  stecken unter „Frei“. Die Stile Q–V haben Reiter „Suchen · Scannen · Foto · Geplant“. Sage, Anime und Edo zeigen das
  als Blatt, Bayrisch, Grauen und Kreuzer als ganze Seite. Dazu kommen eigene Titel und Knopftexte je Stil.
- Die Mahlzeit (Frühstück/Mittagessen/Snack/Abendessen) steht als Chip-Reihe ganz oben und ist das Erste, was man
  sieht, obwohl sie fast immer schon passt.
- Für ein Foto braucht es drei Tipps: Reiter „Foto“, dann „Foto erkennen“, dann im Menü „Kamera“.
- Die Widgets „KI-Foto“ und „Scannen“ öffnen erst die App mit Heute und dem Eintragen-Blatt. Erst wenn das Blatt steht,
  startet die Kamera oder der Scanner, man sieht das Blatt also kurz aufblitzen.

Der Nutzer will: **erst erfassen (Bild oder KI aufrufen), dann erst benennen und kcal usw. anzeigen.**

## Ziele

1. **Zwei Schritte: Erfassen, dann Bestätigen.**
   - **Erfassen** zeigt die Wege: „Foto“ und „Scannen“ als große Knöpfe, darunter die Suche über Rezepte, Zutaten und
     den BLS und die noch offenen geplanten Mahlzeiten des Tages. Antippen eines Treffers führt zu Bestätigen.
   - **Bestätigen** zeigt das Ergebnis zum Prüfen und Ändern: Name, kcal, Eiweiß, Kohlenhydrate und Fett, je nach
     Quelle Portion oder Menge, die Mahlzeit und den Knopf „Eintragen“. Speichern schließt Eintragen und kehrt dorthin
     zurück, wo man herkam.
2. **Foto und Scannen sofort.** Ein Tipp auf „Foto“ öffnet die Kamera, ohne Zwischenknopf und ohne Menü; die Galerie
   ist ein kleiner Nebenweg (z. B. ein Textknopf neben oder unter „Foto“). „Scannen“ öffnet ebenso sofort den Scanner.
   Erst nach dem Foto bzw. Scan erscheint Bestätigen. Bricht der Nutzer Kamera oder Scanner ab, bleibt er, wo er war.
3. **Überall direkt.** Dieselben Sofort-Wege gelten für:
   - die Widgets „KI-Foto“ und „Scannen“: Kamera bzw. Scanner starten, bevor Heute gezeichnet wird (zu sehen ist nur
     kurz der Startbildschirm der App), danach Bestätigen;
   - das Widget „Schnell eintragen“: öffnet Erfassen;
   - die Knöpfe in den Heute-Szenen und im Tagebuch, die heute Eintragen öffnen.
4. **Mahlzeit im Schritt Bestätigen.** Eine Zeile „Mahlzeit  Mittagessen ⌄“ statt der Chip-Reihe oben, Auswahl über
   `FoodyMenue` (Teil A). Vorausgewählt wie heute: aus der angetippten Zeile (Heute, Tagebuch), sonst nach Uhrzeit
   (`mahlzeitNachUhrzeit`). Bei Geplantem steht die Mahlzeit des Plans fest (siehe Tabelle).
5. **Gleich in allen Stilen.** Eintragen ist in allen sieben Stilen und beiden Varianten dieselbe ganze Seite mit
   demselben Aufbau. Der Stil zeigt sich nur in Farben, Schrift, Formen und im Titel („Mahl eintragen“, „Eintragen!“,
   „Eitragn“, „Notieren“ … bleiben). Knöpfe und Beschriftungen sind überall die normalen Wörter („Foto“, „Scannen“,
   „Eintragen“; nicht mehr „Siegel“, „Deuten“, „Ins Bordbuch“, „Eintragen! ✓“).
6. **Eigenes Ziel in der Navigation.** Eintragen ist ein eigenes Ziel wie `RezeptDetail` oder `Kochen` (Ansatz 1),
   nicht mehr ein Blatt, das Heute und Tagebuch je selbst einbauen.

### Was Bestätigen je nach Quelle zeigt

| Quelle | Bestätigen zeigt |
|---|---|
| Foto (KI) | Foto klein, Name, kcal, E/KH/F aus der Erkennung (markiert, „Bitte prüfen“ bzw. „Wie letztes Mal“ wie heute), Portion (Chips wie heute), Mahlzeit. Während die Erkennung läuft: Bestätigen ist schon offen, mit Fortschritt und „Abbrechen“; die Felder füllen sich danach. |
| Scan, Produkt neu | die Packung wie heute (`PackungEingabe`: Name, Menge, je 100 g/ml, kcal, E/KH/F, „als Zutat speichern“), Mahlzeit. Während Open Food Facts gefragt wird: Bestätigen ist schon offen, mit Fortschritt. |
| Scan, Produkt bekannt | die Zutat mit Menge und Einheit, kcal dazu, Mahlzeit |
| Scan ohne Werte (`NichtGefunden`, `KeineTabelle`, `NichtVerfuegbar`, `Fehler`) | die Packung mit leeren Werten und dem Strichcode, darüber die Meldung wie heute und die Knöpfe „Foto der Nährwerttabelle“ und „Galerie“ (`fotoAnbieten`), die die Felder füllen; gilt auch, wenn der Scan aus Widget oder Heute-Szene kam |
| Rezept | Name, Portionen (− / Feld / +), kcal dazu, Mahlzeit |
| Zutat oder BLS-Treffer | Name, Menge und Einheit, kcal dazu, Mahlzeit |
| Geplant | Name, kcal und Mahlzeit aus dem Plan, „1 Portion“ – nur zur Ansicht, nicht änderbar (gespeichert wird wie heute über `ausPlan`: eine Portion, Mahlzeit und Datum aus dem Plan) |
| Frei | leere Felder Name, kcal, E/KH/F, Mahlzeit |

Skizze (Handy, 360 dp):

```
Eintragen                           ✕        Linsensuppe                         ‹
[ 📷 Foto ]   [ ▦ Scannen ]                  [Foto]
  Aus der Galerie · Nährwerttabelle          Name        Linsensuppe
🔍 Rezept oder Zutat suchen                  420 kcal · E 21 · KH 48 · F 12 g
Geplant: Linsensuppe · Mittag                Portion     ½   1   2
Nicht dabei? Frei eintragen                  Mahlzeit    Mittagessen ⌄
                                             [          Eintragen          ]
```

## Nicht-Ziele

- Keine Änderung an Daten, Schema, Sync oder Server (Server-Rebuild: nein). Was gespeichert wird, bleibt wie heute
  (`EintragenViewModel.schreiben`).
- Die Fotoerkennung selbst (Modell, `:core:erkennung`) und die Packungssuche bleiben, nur wann und von wo sie starten
  ändert sich.
- Bearbeiten eines vorhandenen Eintrags (`EintragBearbeiten`) bleibt, wie es ist.
- „Gegessen?“ für Geplantes direkt in Heute bleibt ein Tipp ohne Eintragen-Seite.
- Die Heute-Szenen behalten ihre Zeichnungen; geändert werden nur die Auslöser (siehe Meine Entscheidungen).

## Anti-Ziele

- Ein häufiger Weg wird länger als heute (z. B. Rezept suchen und eintragen braucht mehr Tipps).
- Eingaben, ein aufgenommenes Foto oder ein Scan-Ergebnis gehen bei Drehen oder Prozessende verloren, auch während die
  Kamera offen ist. Ausnahme, wie heute: Stirbt der Prozess, während der Google-Code-Scanner offen ist, kommt dessen
  Ergebnis nicht an (Rückruf im eigenen Prozess); der Nutzer steht dann wieder am Ausgangspunkt und scannt neu. Ein
  eigener Scanner ist nicht gewollt.
- Eintragen sieht in einem Stil anders aufgebaut aus als in den anderen.
- Kontrast unter WCAG 2.2 AA, Zielflächen unter 48 dp, abgeschnittene Texte bei Schrift 90–130 % (200 % ist für den
  Nutzer nicht relevant).

## Ansätze

1. **Eintragen als eigenes Navigationsziel mit zwei Schritten (gewählt vom Nutzer).** Ein Ziel in `Ziele.kt`, ein
   Bildschirm mit den Schritten Erfassen und Bestätigen in einem ViewModel. Foto und Scannen starten vom Auslöser aus
   (Knopf in Heute/Tagebuch, bei Widgets die `MainActivity`) und öffnen danach Bestätigen. Zurück führt natürlich
   Bestätigen → Erfassen → Herkunft. Kosten: größter Umbau; Heute und Tagebuch verlieren ihr eingebautes Blatt, Sage,
   Anime und Edo die Blattform.
2. Gleicher Ort, neuer Inhalt: Eintragen bleibt in Heute und Tagebuch eingebaut, wird überall eine ganze Seite mit den
   zwei Schritten, die Kamera startet beim Öffnen. Weniger Umbau, aber die Seite blitzt vor der Kamera kurz auf – genau
   das, was beim Widget stört. Verworfen.
3. Erfassen als Blatt, Bestätigen als Seite: widerspricht „gleich in allen Stilen“. Verworfen.

## Meine Entscheidungen (vom Nutzer zu prüfen)

- **Frei eintragen** (ohne Foto und ohne Treffer) bleibt als leiser Eintrag in Erfassen („Nicht dabei? Frei
  eintragen“), der zu Bestätigen mit leeren Feldern führt. Ohne Erkennungsmodell gibt es keinen Knopf „Foto“; dann steht
  „Frei eintragen“ an seiner Stelle.
- **Gescannte Produkte** erscheinen in der Suche wie andere Zutaten (heute eigene Liste im Reiter „Scannen“).
- **Kombinierte Szenen-Knöpfe** („Scannen · Foto“ bei Edo, „+ SCANNEN · FOTO“ bei Sage, der Kamera-Knopf „Scannen
  oder Foto“ bei Bayrisch) werden zu zwei Knöpfen „Scannen“ und „Foto“, die je direkt starten; „＋ Eintragen“ und
  ähnliche Knöpfe öffnen Erfassen, mit der Mahlzeit der Zeile, falls es eine gibt.
- **Nährwerttabelle ohne Strichcode** (heute im Scan-Menü): bleibt erreichbar als kleiner Nebenweg unter „Scannen“ in
  Erfassen („Nährwerttabelle fotografieren“), führt zu Bestätigen mit der Packung.
- **Widget bei laufender App:** wie heute zuerst zu Heute (`schnellstartAnfragen`), dann Kamera bzw. Scanner. Eine
  offene Eintragen-Seite wird dabei geschlossen, ihre Eingaben verworfen (seltener Fall). Abbrechen lässt den Nutzer auf
  Heute; beim Kaltstart ebenso (die App schließt sich nicht).
- **Geplantes** bleibt beim Speichern wie heute (eine Portion, Mahlzeit aus dem Plan); Bestätigen zeigt das nur an.
  Änderbare Portionen bei Geplantem wären eine Datenänderung in `:core:data` und gehören nicht in diesen Umbau.
- **kcal „dazu“** bei Rezept, Zutat und Geplantem: berechnet aus Menge bzw. Portionen, wie es die App beim Speichern
  ohnehin tut; ändert sich mit der Eingabe.
- **ADR:** Ja, „Eintragen als eigenes Ziel“ (Blatt/Seite je Stil aus ADR 0020 bzw. dem Stile-Plan entfällt).

## Dem Umsetzer überlassen

- Wo die Launcher für Kamera und Scanner liegen und wie ein laufender Kamera-Aufruf ein Prozessende übersteht (heute
  `ErkennungLauf.kameraGestartet` im `SavedStateHandle`), solange Ziel 2/3 und das Anti-Ziel „nichts geht verloren“
  halten. Heute und Tagebuch liegen beide in `:feature:heute`; die Widgets laufen über `:app`.
- Wie Foto-Pfad bzw. Scan-Ergebnis an das Ziel Eintragen übergeben werden (Argument des Ziels, gemeinsamer Halter …).
- Aufteilung in PRs (Vorschlag: 1. Ziel und zwei Schritte für alle Stile; 2. Sofort-Wege aus Heute, Tagebuch und den
  Szenen; 3. Widgets direkt; 4. Doku und ADR).
- Ob Erfassen bei „Schnell eintragen“ die Tastatur für die Suche gleich öffnet.

## Prüfung

- ViewModel-Tests: Schritte, Vorbelegung der Mahlzeit (Zeile, Plan, Uhrzeit), alle Quellen bis „Eintragen“, Werte
  wie vorher gespeichert; Prozessende in beiden Schritten und mit offenem Kamera-Aufruf
  (`SavedStateHandle.nachProzessende()`).
- Bildschirm-Tests (Robolectric): Foto-Knopf startet die Kamera ohne Zwischenschritt; Abbrechen lässt den Nutzer, wo
  er war; Speichern kehrt zur Herkunft zurück; in allen 14 Kombinationen aus Stil und Variante derselbe Aufbau (gleiche
  Knöpfe, gleiche Reihenfolge).
- Navigation: Widget-Links „KI-Foto“ und „Scannen“ starten Kamera bzw. Scanner ohne vorheriges Eintragen; „Schnell
  eintragen“ öffnet Erfassen.
- Roborazzi: Erfassen und Bestätigen (Foto mit Vorschlag, Rezept) je Stil bei 360 dp; alte Bilder des Eintragen-Blatts
  entfallen.
- `KontrastTest`, `./gradlew check` und `ktlintPruefen` grün; am Handy (361 dp) in allen Stilen ansehen, auch beide
  Widgets.
