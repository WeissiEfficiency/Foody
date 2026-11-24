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

## Reihenfolge und Abnahme
1 → 2 → 3 → 4 → 5; jeder Schritt mit Tests, Bildern und einem Blick auf dem Handy in allen fünf Stilen. Danach ein
Release (3.12).
