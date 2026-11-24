# Anime-Feinschliff (Eintragen, Rezepte, Einkauf) – Plan

Stand 2026-11-24, gesichtet auf dem Handy (3.10.1, Anime Immersiv). Erst Anime, danach dieselbe Liste für die übrigen
Stile (letzter Abschnitt). Ziel: wenig neuer Code, Bestehendes umstellen statt neu bauen.

## Befund

| Stelle | Ist | Ursache im Code |
|---|---|---|
| Zurück-Knopf, Zahnrad, „Synchron“ | grau (RGB 233,233,235) | `GlasFlaeche(LEICHT)` in `RunderKnopf`, `SyncStatus` |
| Suchfeld, Einkauf-Kacheln, „hinzufügen“-Kachel | grau (246,246,247) | `GlasFlaeche(DICHT)` in `Suchfeld`, `Kachel` |
| Karten (Darstellung, Energie) | weiß | eigene Anime-Flächen |
| Eintragen | keine Leiste | `Hauptgeruest.kt:91`: `Eintragen` zählt als Vollbild |
| Eintragen | Foto/Scannen groß, Galerie und Nährwerttabelle nur Textlinks | `Erfassen.kt` |
| Eintragen, Döner | „Stk.“ wählbar, 1 Stück = keine kcal, Eintragen trotzdem aktiv | `EINTRAGEN_EINHEITEN` fest; `gueltig()` prüft nur Menge > 0 |
| Eintragen | Mahlzeit als Milchglas-Dropdown | Standard-Menü |
| Rezepte | 6 je Seite, „Seite 1 / 50“ | `RezepteStile.kt`, `SEITE = 6` |
| Rezepte, Filter | `#Ideen`, `#Import` … als Chips; Sortierung am Ende der Reihe | `FilterChips`, `haeufigeTags` |
| Rezepte, „+ Neu“ | Menü „Neues Rezept / Markdown importieren“ | `ListeBereiche.kt` |
| Einkauf | Abteilungen als Klappliste | `ListenSeite`/`EinkaufAnime` |
| Einkauf | „Listen ▸“-Pille, runder „+“ neben der Suche | `EinkaufKopf.kt`; „„x“ hinzufügen“-Kachel gibt es schon |

## Schritte (je ein kleiner PR gegen `main`)

### 1 · Anime-Flächen weiß
- `StilWerte.leiste` wird `StilWerte.deckend` (Anime: Weiß, sonst `null`); `GlasFlaeche` nimmt ihn als Vorgabe für
  `deckend`. Damit sind Zurück, Zahnrad, Synchron, Suchfeld und Kacheln in Anime weiß wie Leiste und Karten.
- Erledigte Einkauf-Kacheln behalten ihr Abblassen (durchgestrichen), aber auf Weiß.
- Test: Pixel des Zurück-Knopfs in Anime = Weiß (wie der Leisten-Test aus #291). Bilder neu aufnehmen.

### 2 · Eintragen: Leiste und vier Kacheln
- `Eintragen` nicht mehr Vollbild (nur noch `Kochen`); in der Leiste ist „Eintragen“ markiert, solange die Seite offen ist.
- Vier gleich große Kacheln 2 × 2 mit Symbol: Foto (`PhotoCamera`), Aus der Galerie (`PhotoLibrary`), Strichcode
  scannen (`QrCodeScanner`), Nährwerttabelle (`TableChart`). Ohne Fotoerkennung wird „Foto“ zu „Frei eintragen“ wie heute.
- Mahlzeit als Reihe aus Pillen (Frühstück · Mittag · Abend · Snack) statt Dropdown – gilt für alle Stile, weil
  Pillen (`FoodyChip`) schon jeder Stil zeichnet.

### 3 · Stückgewicht schätzen
- Neue Schätzliste in `:domain` (`StueckSchaetzung`): Namensmuster → übliches Stückgewicht in g (Döner ≈ 350, Brötchen
  ≈ 60, Pizza ≈ 350, Banane ≈ 120 …). Grundlage: alle Zutaten mit Stückangabe aus den Rezepten (Rezeptkorpus und
  Startdaten) plus die schon vorhandenen `stueckG` der BLS-Kurznamen. Test mit Döner (vorher rot).
- Wählt man „Stk.“, erscheint darunter „1 Stück ≈ 350 g (geschätzt)“ als änderbares Feld; die kcal stehen dabei.
  Geänderter Wert landet in `pieceWeightG` der Zutat (keine Schemaänderung). Ohne Schätzung ist das Feld leer und
  Eintragen gesperrt, bis ein Gewicht drinsteht.
- ml/EL/TL nur zeigen, wenn sie sich umrechnen lassen (`naehrwerteZutat` ≠ `null`); `gueltig()` verlangt kcal.

### 4 · Rezepte: endlos scrollen, Filter, Neu
- Anime: ein `LazyVerticalGrid` über alle Treffer statt Seiten; gezeichnet wird nur, was sichtbar ist, Bilder laden
  beim Hinscrollen (nichts vorladen). Seitenleiste „‹ Seite ›“ entfällt in Anime.
- Tag-Chips (`#Ideen`, `#Import` …) raus; Archiv bleibt. Reihe: Alle · Favoriten · Schnell · Oft gekocht · Vegetarisch
  · Vegan · Eiweißreich · Low Carb · Leicht · Frühstück · Mittag · Abend · Snack · Archiv.
- Sortierung als runder Knopf rechts neben dem Suchfeld (Menü wie bisher).
- „+ Neu“ öffnet direkt den Editor.
- Markdown-Import zieht in die Einstellungen: neuer Abschnitt „Rezepte importieren“ über „Sicherung“. Das Blatt bleibt in
  `:feature:rezepte`; Einstellungen bekommt nur einen Rückruf, `:app` navigiert (Features kennen sich nicht).

### 5 · Einkauf Anime
- Runder „+“ neben der Suche entfällt (alle Stile); hinzufügen über die Kachel „„x“ hinzufügen“ bzw. Enter.
- „Listen ▸“ wird ein runder Anime-Knopf mit Klemmbrett-Symbol wie das Zahnrad (TalkBack „Listen“).
- Abteilungen als Comic-Panels: weiße Karte mit Kante und Versatzschatten, bunte Kopf-Pille, darin eine waagerecht
  wischbare Reihe Kacheln. Kein Auf- und Zuklappen mehr.

## Ergebnis

- 1: #305. 2: #306. 3: #307 (Döner 1 Stk. → 350 g geschätzt, 699 kcal; auf dem Handy geprüft).
- 4: #308. Anime-Liste endlos (`LazyColumn` aus Zweierreihen, behält die Stelle nach „Zurück“, Filter und Suche beginnen oben);
  Edo, Bayrisch, Grauen, Kreuzer behalten ihre Seiten. Tag-Chips und `haeufigeTags` entfernt (Suche findet Tags weiter).
  Sortierung als runder Knopf neben der Suche in allen Stilen; „+“ / „+ Neu“ öffnet überall direkt den Editor. Import
  in den Einstellungen (Abschnitt „Rezepte“ über „Sicherung“). Auf dem Handy mit 318 importierten Rezepten geprüft.
- 5: #309. Kein „+“ neben „Was willst du einkaufen?“ (alle Stile). Anime: runder Listen-Knopf mit Klemmbrett; Zuletzt
  und Katalog als Comic-Panels mit wischbarer Kachelreihe statt Klappen (`animeKlappkopf` entfällt). Auf dem Handy
  geprüft: Kachel antippen setzt auf die Liste, Reihe wischt, Listen-Knopf öffnet „Listen!“, „„Grillkohle“ hinzufügen“ trägt ein.
- 5b: Nach Rückmeldung („Überschrift, links und runter scrollen, zu unruhig“) neu: kleiner Kopf statt „QUEST!“-Banner
  und XP-Balken, Posten als Kacheln je Abteilung, „Hinzufügen“ mit Abteilungs-Pillen, die umbrechen; keine Panels,
  kein waagerechtes Wischen. Auf dem Handy geprüft (Abhaken, Pillenwahl, Zähler).

## Danach: übrige Stile

Schritte 2 (Leiste, Mahlzeit-Pillen), 3, 4 (Filter, Sortierung, Neu, Import) und der „+“ aus 5 gelten sofort für alle
Stile. Je Stil offen bleiben:

| | Milchglas | Sage | Edo | Bayrisch | Grauen | Kreuzer |
|---|---|---|---|---|---|---|
| Graue Glasflächen prüfen | gewollt (Glas) | prüfen | prüfen | prüfen | prüfen | prüfen |
| Eintragen-Kacheln im Stil | Standard | Szene | Szene | Szene | Szene | Szene |
| Rezepte endlos | ja (Liste) | prüfen | Fächer: Seiten oder Wischen | Herzen: Stangen endlos | Regal: endlos waagerecht | Sternkarte: Seiten behalten? |
| Abteilungen statt Klappliste | bleibt | eigenes Bild | eigenes Bild | eigenes Bild | eigenes Bild | eigenes Bild |
| Listen-Knopf im Stil | bleibt | Knopf | Knopf | Knopf | Knopf | Knopf |

Die Szenen von Edo, Bayrisch, Grauen und Kreuzer bauen auf einer festen Zahl Rezepte je Bild auf; dort pro Stil
entscheiden, ob endlos (waagerecht wischen) oder Seiten bleiben.
