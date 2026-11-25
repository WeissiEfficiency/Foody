# Kosmisches Grauen: zwei immersive Gesichter – Plan

Entwurf: `docs/superpowers/specs/2026-11-25-grauen-zwei-gesichter-design.md`, Entscheidung: ADR 0022.
Je Schritt ein kleiner PR gegen `main` mit Auto-Merge, kein Server-Rebuild. Nach jedem Schritt die Debug-App aufs
Handy und beide Gesichter ansehen; Fehler zuerst beheben, dann weiter.

## Schritte

### 1 · Varianten umdeuten (Grundlage)
- `StilWerte.inszeniert` ist beim Grauen in beiden Varianten `true`. Dazu kommt `StilWerte.grauenBuch` (Grauen und
  IMMERSIV).
- `FoodyStil.dunkel`: Das Grauen ist dunkel in RUHIG (Ominös), hell in IMMERSIV (Pergament).
- Anzeigenamen der Varianten je Stil: beim Grauen „Ominös“ / „Necronomicon“, sonst „Immersiv“ / „Ruhig“
  (Einstellungen, Stilwahl, Baseline-Profil).
- Beim Grauen gibt es keine Akzentfarbe: Die Zeile „Farbe“ entfällt, es gilt immer Glut.
- Übergang bis Schritt 4: Das Necronomicon zeigt noch das grüne Grauen; das schlichte Pergament gibt es nicht mehr.
- Tests: `StilWerteTest` (inszeniert, grauenBuch, dunkel) und Einstellungen (Namen, keine Farbe beim Grauen); die
  Bilder von Grauen Ruhig werden neu aufgenommen.

### 2 · Bedienelemente ohne Kästen (beide Gesichter, zuerst sichtbar in Ominös)
- In `:core:designsystem/stil/grauen/` neu:
  - `GrauenSiegel` (Wachs mit gewelltem Rand, Zeichen, Text daneben)
  - `GrauenZurueck` („↶ Ziel“) und `GrauenMenue` („∴“)
  - `GrauenMenge` (− Zahl +)
  - `GrauenReiter` (Kapitelwörter mit Schwung)
  - `GrauenSymbol` (Kerze, Rolle, Band, Feder, Auge als gezeichnete Pfade)
  - Zeigehand ☞ statt „›“
- Einsetzen in Rezept, Kochen, Heute/Logbuch, Woche/Planen, Einkauf, Editor, Einstellungen; `GrauenKnopf` mit Kasten
  bleibt nur, wo eine Eingabe einen Rahmen braucht (Suchfeld, Textfelder).
- Tests: Rollen und Beschreibungen (TalkBack), Tippflächen ≥ 48 dp, Bilder neu.

### 3 · Schmuck (beide Gesichter)
- Trennlinie mit Auge (`GrauenTrenner`) statt Karten und Linien.
- Fraktur-Initialen (`GrauenInitiale` mit Fraktur) für Rezepttitel und nächstes Gericht.
- „Ältestes Zeichen“ als blasses Wasserzeichen, Tentakel-Ranke am Rand.
- Tests: Bilder; das Wasserzeichen hat keine Semantik.

### 4 · Necronomicon: Grund, Farben, Leiste
- `StilWerte` für Grauen IMMERSIV: Tinte, Nebentext, Blut, Grünspan, Pergament, helle Statusleiste.
- Pergament als Textur: Fasern, Flecken, Schimmel und dunkle Ränder als vorgerechnetes Bild (`drawable-nodpi`, WebP,
  höchstens 300 KB), darüber die Seite. Muster neu: `Hintergrundmuster.PERGAMENT`.
- Leiste und Rail: Leder mit Messing, der aktive Punkt mit Messing-Strich.
- Siegel in schwarzem Wachs.
- Tests: `KontrastTest` für das Necronomicon (Text auf dem dunkelsten Fleck, auf Siegel, auf der Leiste), Bilder.

### 5 · Necronomicon je Seite
Jede Szene schaut auf `grauenBuch` und zeichnet in Tinte statt Glut; ein PR je Seite:
- Heute (Auge als Federzeichnung) und Logbuch
- Rezepte (Regal mit Bänden auf Pergament, „Aus dem Regal“ bleibt) und Rezept
- Kochen
- Woche (Monde als Federzeichnung) und Planen
- Einkauf
- Eintragen
- Einstellungen

Tests: je Seite die Bilder `stil_grauen_buch_*` (Handy, Tablet, 130 %).

### 6 · Widgets
Necronomicon-Widgets mit Pergament-Bitmap, Tinte und Messing; Ominös wie heute. Bilder mit `:app:recordRoborazziDebug`,
Vorschauen nach `res/drawable-nodpi/widget_vorschau_*`.

### 7 · Aufräumen und Doku
- Die Pfade des alten Grauen Ruhig (schlichtes Pergament) entfernen, samt Bildern und Tests.
- `docs/architecture.md` (Stile), `docs/domain-rules.md` falls nötig, ADR 0020 auf 0022 verweisen.
- „Ergebnis“ hier eintragen.

## Meine Wahl, wo der Entwurf nichts sagt
- **Regal im Necronomicon:** ein Regalbrett in Federzeichnung, die Bände als dunkle Lederrücken. Der Übergang „Aus dem
  Regal“ bleibt.
- **Auge auf Heute:** im Necronomicon eine Federzeichnung statt des leuchtenden Auges, der Blick ins Tagebuch bleibt.
- **Animationen:** wie bisher in beiden Gesichtern (ohne Animationen im System sofort).
- **Tablet:** derselbe Aufbau, das Pergament füllt die ganze Fläche; Doppelseiten wie im Almanach.

## Ergebnis

(folgt je Schritt)
