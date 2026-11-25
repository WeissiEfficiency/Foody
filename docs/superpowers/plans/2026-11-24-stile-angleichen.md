# Übrige Stile an die Anime-Runde angleichen – Plan

Stand 2026-11-24, nach Release 3.11.0. Grundlage sind die Wünsche aus dem Anime-Feinschliff
(`2026-11-24-anime-feinschliff.md`, #305–#313), der Ist-Stand der Roborazzi-Bilder je Stil und das, was auf dem Handy
auffiel. Betroffen sind Düstere Sage, Edo, Bayrisch, Kosmisches Grauen, Sternenkreuzer und, wo es passt, Milchglas.
Ziel bleibt: wenig neuer Code, gemeinsame Bausteine statt fünf Einzellösungen.

## Anforderungen aus der Anime-Runde

| # | Anforderung | Anime | gilt schon für alle? |
|---|---|---|---|
| A1 | Keine grauen Glasflächen auf dem Stil-Hintergrund | `StilWerte.deckend` = Weiß | nein, nur Anime |
| A2 | Eintragen: Leiste sichtbar, vier Kacheln mit Symbol, Mahlzeit als Pillen | #306 | **ja** (Bilder: alle Stile zeichnen die Kacheln im Stil) |
| A3 | Stückgewicht geschätzt, Eintragen nur mit kcal | #307 | **ja** (Logik) |
| A4 | Rezepte: kein Tag-Chip, Sortierknopf neben der Suche, „+ Neu“ öffnet direkt, Import in den Einstellungen | #308 | **ja** |
| A5 | Rezepte endlos statt Seiten, Antippen öffnet direkt | #308 | nein: Szenen zeigen 3 je Bild mit ‹ › |
| A6 | Einkauf ruhig: nur senkrecht scrollen, Kacheln, Abteilungen als Pillen statt 13 Klappzeilen | #310 | nein: alle Szenen haben die lange Klappliste |
| A7 | Kein „+“ neben der Suche im Einkauf; Listen-Knopf passend zum Stil | #309 | „+“ ja; Knopf: Textknöpfe im Stil |
| A8 | Heute ohne Tagesziel: Hinweis „Tagesziel festlegen ▸“ statt leerer Anzeige | #311 | nein |
| A9 | Energie-Anzeige zeigt Eiweiß, KH und Fett in ihren Farben | #312 | teils (Edo, Kreuzer, Sage haben Makro-Balken) |
| A10 | Maskottchen lebt (Blinzeln) | #312/#313 | nur Anime hat eins; Grauen-Auge blinzelt schon |

## Ist-Stand je Stil (Bilder `*_handy.png`, `stil_*_immersiv.png`)

| | Sage | Edo | Bayrisch | Grauen | Kreuzer |
|---|---|---|---|---|---|
| Einkauf, Posten | Händler-Kacheln | Notizliste | Kisten | Expeditionszettel | Fracht-Kacheln |
| Einkauf, Katalog | 13 Klappzeilen | 13 Klappzeilen | 13 Klappzeilen | 13 Klappzeilen | 13 Klappzeilen |
| Rezepte | Grimoire, 1 je Seite | Fächer, 3 | Herzen, 3 | Regal, 3 | Sternkarte, 3 |
| Heute ohne Ziel | Balken leer | „/ – kcal“ | Maibaum „Ziel –“ | Auge ohne Ring | Sonne „–“ |
| Energie in Makro-Farben | ja (Unterstriche) | ja (Balken) | nur Rauten-Liste | nur Text | ja (Balken) |

## Schritte (je ein kleiner PR gegen `main`)

### 1 · Einkauf: Abteilungs-Pillen für alle Szenen (A6)
- `animeHinzufuegen` wird ein gemeinsamer Baustein `hinzufuegenMitPillen` in `EinkaufKacheln.kt`: Zuletzt, Abteilungen,
  Eigene Artikel als umbrechende Pillen, darunter die Kacheln der gewählten. Die Pille kommt über einen Slot wie der
  Klappkopf (`PilleInhalt`); Standard ist `FoodyChip`, der je Stil schon richtig zeichnet (`stilChip`). Anime behält
  seine kompakte Pille.
- `zuletztKacheln`/`katalogKacheln` mit Klappköpfen fallen in den Szenen weg; die Posten-Bilder (Händler, Zettel,
  Kisten, Expedition, Fracht) bleiben, sie sind der Flair des Stils.
- Test: je Stil „Pille antippen zeigt deren Kacheln, nichts klappt, nichts scrollt waagerecht“ (Schleife wie
  `keinMenueInDenStilen`). Bilder neu.

### 2 · Rezepte: Bühne plus endlose Liste (A5)
- Die Szene (Fächer, Herzen, Regal, Sternkarte, Grimoire) zeigt oben weiter drei Rezepte, aber fest: die ersten drei
  Treffer (bei „Alle“ also nach Sortierung). Darunter alle Treffer als endlose Liste in einer schlichten Zeile des
  Stils (Name, Zeit, kcal); Antippen öffnet direkt, wie in Anime. ‹ Seite › und die Karte des gewählten Rezepts
  entfallen.
- Gemeinsam: die `LazyColumn` aus `AnimeListe` wird `StilListe(oben, zeile)`; jeder Stil liefert nur seine Zeile.
- Test: `RezepteStileTest` je Stil „letztes Rezept per Scrollen erreichbar und öffnet“ (wie `animeEndlos`).

### 3 · Heute: ohne Ziel und Makro-Farben (A8, A9)
- Ohne Ziel zeigt jede Szene statt der leeren Anzeige den Hinweis im Stil (Sage „Ziel festlegen ▸“ im Balken, Edo
  im Kopf der Speisefolge, Bayrisch am Maibaum, Grauen unter dem Auge, Kreuzer in der Sonne); alle führen zu
  `onEinstellungen`.
- Bayrisch und Grauen: die kcal-Anzeige bekommt die drei Makro-Farben wie Anime (`makroGrenzen` wandert dazu aus
  `HeuteAnime.kt` in eine gemeinsame Datei).
- Test je Stil mit `ziel = null`: Hinweis da, Antippen ruft Einstellungen.

### 4 · Glas auf dem Handy prüfen (A1)
- Je Stil in Foody Debug durchklicken (Heute, Eintragen, Rezepte, Einkauf, Einstellungen) und graue Flächen notieren.
  Wo Glas auf hellem Grund grau wirkt (vermutlich Edo, Bayrisch), `StilWerte.deckend` mit der Papierfarbe des Stils
  setzen; auf den dunklen Stilen bleibt die Tönung.

### 5 · Kleinkram
- Anime-Rezepte: „+ Neu“ bricht auf schmalen Handys in eine eigene Zeile (offen aus dem Feinschliff).
- Listen-Knopf (A7): die Textknöpfe („Listen“, „Zettl“, „Archiv“, „Ladelisten“) passen zum jeweiligen Stil und
  bleiben, außer der Nutzer will überall einen runden Knopf.

## Entscheidungen (2026-11-24, „Ja, loslegen“)
1. Milchglas behält die Klappliste.
2. Rezepte: Bühne mit drei Rezepten oben, darunter die endlose Liste.
3. Listen-Knopf: die Textknöpfe im Stil bleiben.

## Ergebnis

### Schritt 1 · Einkauf-Pillen
- Gemeinsamer Baustein `hinzufuegenMitPillen` (`EinkaufHinzufuegen.kt`) statt `animeHinzufuegen`; die Klappköpfe
  der Szenen (`sageKlappkopf` … `kreuzerKlappkopf`, `Klappzeile`) sind entfernt.
- Auf Wunsch des Nutzers („für jedes Design individuell“) liefert jede Szene ein `PillenAussehen` mit eigener
  Überschrift, eigenen Abteilungsnamen und eigener Pille: Sage „Die Waren des Händlers“ ohne Emoji, Edo „Noch
  besorgen“ mit Marktnamen, Bayrisch „Wos brauch ma no?“ mit Emoji und bairischen Namen, Grauen „Was die Expedition
  noch braucht“, Kreuzer „FRACHT ANFORDERN“ mit Kisten-Code, Anime wie bisher.
- Test `EinkaufStileTest.abteilungPerPille` (alle sechs Szenen: Pille wählt, nur deren Kacheln, nichts klappt,
  nichts scrollt waagerecht); Bilder der fünf Szenen neu; auf dem Handy in Sage geprüft.

### Runde 2 · S5 Sage-Einstellungen
- Bei 361 dp (Handy des Nutzers) passten nur drei der vier Farben in eine Reihe, die vierte rutschte allein in eine
  neue Zeile. Akzente jetzt 64 dp breit, passen sie nicht, 2 + 2 (`rasterSpalten`,
  `EinstellungenStileTest.sageFarbenInEinerReihe`). Gilt auch für die Ruhig-Varianten.

## Runde 2 · Immersive Stile eigenständig (Wunsch 2026-11-24)

Der Nutzer will keine Gleichmacherei: Jeder immersive Stil soll auf jeder Seite (Heute mit Energie, Eintragen,
Rezepte mit Liste und Rezept, Woche, Einkauf) **seine eigene Form** haben, so wie Anime mit dem Reisbällchen. Zuerst
**Düstere Sage** und **Kosmisches Grauen**; Edo, Bayrisch und Sternenkreuzer später, Milchglas passt, die ruhigen
Varianten bleiben unangetastet. Anime: „Hinzufügen“ im Einkauf bleibt. Damit ersetzt Runde 2 für Sage und Grauen den
Schritt 2 oben (Bühne plus Liste).

### Kosmisches Grauen
- **G1 Bibliothek als Bücherregal:** Bretter mit je vier Bänden untereinander, endlos nach unten scrollbar (nur die
  Bretter im Bild werden gezeichnet). Auf dem Rücken steht der Titel schräg bzw. längs wie auf einem Buch, statt des
  Anfangsbuchstabens. Antippen öffnet den Band. Seiten (‹ ›) und die Pergamentkarte darunter entfallen.
- **G2 Rezept als Buch:** Das Rezept öffnet als aufgeschlagenes Buch; Zutaten, Zubereitung und Nährwerte sind Seiten,
  die man umblättert. Im Kochmodus ist jeder Schritt eine Seite mit Umblättern statt Durchschalten. *Erledigt:*
  `GrauenSeite` und `umblaettern` in `:core:designsystem`; Rezept (Wischen oder Reiter) und Kochmodus blättern um
  den Bund, Seitenzahlen römisch. Ruhig bleibt bei Reitern und Gleiten.
- **G3 Mondphasen (Woche):** Das Wochenumschalten oben (‹ ›) entfällt, die Monde selbst wählen den Tag, Wischen über
  den Mondbogen wechselt die Woche. „2/7 planen“ oben verschwindet und geht im Ritual unten auf („Die dunklen Nächte
  füllen“ mit dem Stand „2 von 7“).
- **G4 Auge (Heute):** Die kcal-Zeile unter dem Auge öffnet das Energietagebuch; Makro-Farben wie Anime (Schritt 3).
- **G5 Expeditionsliste (Einkauf):** später prüfen, ob sie eigenständiger werden soll.
- **G6 Buch ausbauen** (Wunsch 2026-11-25): vier Ideen – (1) Logbuch als Blätter, (2) Buch aus dem Regal (Übergang
  Bibliothek → Rezept), (3) Buchschmuck im Rezept (Initiale, Lesebändchen, Randnotiz, Eselsohr), (4) Woche als
  Almanach. Zuerst 1 und 3. *Erledigt (1):* Im Logbuch ist jeder Tag ein Blatt (`LogbuchBlaetter`, `GrauenSeite`) mit
  dem Tag des Jahres als römischer Seitenzahl (`roemischVoll`); Wischen und „‹ ›“ blättern um den Bund.

### Düstere Sage (mehr Dark-Fantasy, „Elden-Ring-Gefühl“)
- **S1 Rast am Feuer (Heute):** Vitalität zeigt Eiweiß, KH und Fett in ihren Farben – als drei Leisten wie Leben,
  Fokus und Ausdauer eines Helden. „+ Scannen“/„+ Foto“ unten auf der Startseite entfallen (Scannen gehört zu
  Eintragen). Vitalität öffnet das Energietagebuch.
- **S2 Mahl eintragen:** neu gestalten – Foto, Scannen, Galerie, Nährwerttabelle als Gegenstände in Inventarfeldern,
  die Treffer als Liste im Stil statt der nackten Zeilen. *Erledigt:* vier Inventarfelder unter „Ausrüstung“ (Foto,
  Scannen, Galerie, Tabelle), Treffer als Zeilen mit Rune und goldenem Pfeil; Ruhig behält die großen Knöpfe.
- **S3 Grimoire als Inventar:** Rezepte als Raster aus Inventarfeldern (scrollbar), oben die Beschreibung des
  gewählten Gegenstands mit Werten (Zeit, kcal, gekocht) und „Aufschlagen“.
- **S4 Der Händler:** zwei, drei Bildvarianten zum Vergleich zeichnen; die Liste als Papierrolle, die sich nach unten
  ausrollt. *Erledigt:* drei Entwürfe (Gnadenort, Ruinentor, Karren); gewählt **Ruinentor** (Nutzer, 2026-11-25).
- **S5 Einstellungen:** Das Layout verschiebt sich; auf dem Handy nachstellen und beheben.

### Später · Übergänge
Übergänge zwischen Seiten im Stil, z. B. Grauen: Tipp aufs Auge → das Auge öffnet sich und zoomt auf den Betrachter
zu, dahinter ploppt das Energietagebuch auf. Erst nach den Seiten von Runde 2 (Wunsch 2026-11-24).
*Erledigt (Grauen, Auge → Tagebuch):* Lid und Pupille öffnen sich, der Blick stürzt heran und versinkt im Dunkel der
Pupille, daraus taucht das Tagebuch auf. Weitere Übergänge je Stil offen.

### Reihenfolge Runde 2
G1 → S1 → G4 → G3 → S3 → S5 → G2 → S2 → S4; je ein kleiner PR mit Tests, Bildern und Blick aufs Handy.

## Reihenfolge und Abnahme
1 → 2 → 3 → 4 → 5; jeder Schritt mit Tests, Bildern und einem Blick auf dem Handy in allen fünf Stilen. Danach ein
Release (3.12).

## Ergebnis Runde 2

### Runde 2 · G3 Mondphasen
- Grauen Immersiv: ‹ › und „2/7 planen ›“ oben entfallen. Monde wählen den Tag, Wischen über den Bogen blättert,
  leise ‹ › in den oberen Ecken, „Heute ›“ im Kopf nur in einer anderen Woche, Ø kcal unter dem Titel. Das Ritual
  unten zeigt „2 von 7 Nächten erhellt“ (`WocheStileTest.grauenMondbogenWischen`, `grauenBlaettern`).
