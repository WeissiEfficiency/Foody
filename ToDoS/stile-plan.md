# Foody – wählbare Stile Q bis V

Stand: 2026-11-11, Phase 1 und der Kern von Phase 2/3 umgesetzt (Branch `claude/stile-q-v`), siehe „Ergebnis“. Grundlage sind die Entwürfe im Design-Canvas
„Foody Design“ (privat, https://claude.ai/artifact/4GQ6pcboqoVPJCfJmyFdHj). Q bis V haben dort je 14 Screens als
„Version 1“, jeder Screen mit den Varianten **Immersiv** und **Ruhig**. Die Phasen werden der Reihe nach abgearbeitet.
Die nächste beginnt, wenn die vorige abgehakt ist.

## Ziel

Unter Einstellungen › Darstellung wählt man neben dem heutigen Milchglas einen von sechs Stilen. Pro Stil gibt es:
- eine Variante: Immersiv (volle Inszenierung) oder Ruhig (Farben und Schrift des Stils, schlichtes Layout);
- eine Akzentfarbe aus den vier Farben, die der Entwurf vorsieht.

Funktionen, Daten und Abläufe bleiben gleich. Es ändert sich nur das Aussehen und bei Immersiv ein Teil der Wörter.

| Stil | Grund | Schriften (Google Fonts, OFL) | Akzent (Standard + 3) | Navigation Immersiv |
|---|---|---|---|---|
| **Q · Düstere Sage** | dunkel | Cinzel, EB Garamond | Gold `#C8A45C`, Silber, Glut, Moos | Heute · Rezepte · Woche · Einkauf |
| **R · Anime** | hell, Rasterpunkte | Dela Gothic One, M PLUS Rounded 1c | Rosa `#FF7AAE`, Türkis, Orange, Lila | wie Standard |
| **S · Edo** | hell, Papier | Kaisei Decol, Zen Kaku Gothic New | Zinnober `#B8322A`, Indigo, Moos, Ocker | wie Standard |
| **T · Bayrisch** | hell, Himmel → Papier | UnifrakturCook (nur Titel), Alegreya, Alegreya Sans | Blau `#1C6CB0`, Grün, Rot, Braun | Heid · Rezepte · Wochn · Eikaufn |
| **U · Kosmisches Grauen** | Immersiv dunkel, Ruhig Pergament | IM Fell English (+ SC), Special Elite | Glut `#8FD1A0`, Rosé, Blau, Lila | wie Standard |
| **V · Sternenkreuzer** | dunkel | Exo 2, Share Tech Mono | Amber `#FFB000` + Holo `#5CE1FF` | Brücke · Archiv · Kurs · Fracht |

Die Nährwertfarben (`MakroFarben`) bleiben in allen Stilen gleich, so wie es die Entwürfe schon vorsehen.

## Entwurf → App

Die 14 Entwürfe je Stil decken alle Bildschirme der App ab:

| Entwurf | App |
|---|---|
| Heute | `:feature:heute` `HeuteScreen` |
| Eintragen (Suche, Scan, Foto, Geplant) | `:feature:heute` Eintragen-Ablauf, `:core:scan`, `:core:erkennung` (nur Oberfläche) |
| Tagebuch | `:feature:heute` `tagebuch/TagebuchTagScreen` |
| Rezepte | `:feature:rezepte` `liste/` |
| Rezept | `:feature:rezepte` `detail/` |
| Editor | `:feature:rezepte` `editor/` |
| Kochen | `:feature:kochen` |
| Woche | `:feature:woche` `WocheScreen` |
| Planen | `:feature:woche` `FuellenBlatt`, `MahlzeitBlatt` |
| Einkauf | `:feature:einkauf` `EinkaufScreen` |
| Listen | `:feature:einkauf` `FruehereBlatt` |
| Einstellungen | `:feature:einstellungen` (mit `VerbindenScreen`) |
| Zutaten | `:feature:zutaten` (Liste und Editor) |
| Widgets | `:app` `widget/` (siehe Phase 7) |

## Rahmen (vor Phase 1 bestätigen)

- **Milchglas bleibt Standard.** Ohne Wahl sieht die App aus wie heute. Die Referenzbilder von Milchglas ändern sich
  durch den Umbau nicht, und genau das prüft die CI.
- **Grund je Stil fest.** Die Entwürfe haben keinen Hell/Dunkel-Wechsel (Q und V nur dunkel, R, S und T nur hell,
  U je nach Variante). Deshalb gilt „Modus“ (System/Hell/Dunkel) nur für Milchglas und ist bei Stilen ausgeblendet.
  Die Systemleisten folgen dem Grund des Stils (`SystemleistenPassend`).
- **Farbwelten nur für Milchglas.** Bei einem Stil zeigt „Farbe“ dessen vier Akzentfarben.
- **Wörter:** In Immersiv ändern sich nur Überschriften, Knöpfe, Navigation und Leertexte („Rast am Feuer“, „Pack
  ma's!“, „Mission starten“). Namen, Mengen, kcal und Fehlermeldungen bleiben wörtlich gleich.
  Bedienhilfen (`contentDescription`, TalkBack) nutzen immer die normalen Begriffe. Ruhig verwendet die
  Standardtexte.
- **Barrierefreiheit:** Kontrast WCAG 2.2 AA wie bisher, auch auf den Szenen. Animationen (Flamme, Auge, Orbits)
  ruhen, wenn das System Animationen abschaltet, wie heute schon die Unschärfe (`animationenErlaubt()`). Fraktur und
  Schreibmaschine nur für Titel und kurze Zeilen, Fließtext in der lesbaren Schrift des Stils. Ziele mindestens 48 dp,
  große Schrift (200 %) darf nichts abschneiden.
- **Keine fremden Marken.** Q und V bleiben allgemeines Genre (dunkle Fantasy, Raumschiff), ohne Namen, Logos oder
  Motive aus Spielen oder Filmen, so wie in den Entwürfen.
- **Kein Sync der Wahl**, wie bei der Farbwelt (ADR 0015). Server-Rebuild: nein.
- **Tablet:** Die Entwürfe gibt es nur für das Handy (390 dp). Auf dem Tablet bleibt das zweispaltige Layout. Der Stil
  liefert dort Farben, Schrift und Formen (wie Ruhig), die Szenen stehen mittig in der Lesebreite.

## Aufbau im Code

Abhängigkeiten bleiben wie gehabt (`:app` → `:feature:*` → `:core:*`). Es kommt kein neues Modul dazu, alles
Gemeinsame liegt in `:core:designsystem`.

1. **`stil/FoodyStil.kt`** (`:core:designsystem`): Enum `FoodyStil { MILCHGLAS, SAGE, ANIME, EDO, BAYRISCH,
   GRAUEN, KREUZER }` und `StilVariante { IMMERSIV, RUHIG }`. `ausName()` ist robust gegen fehlende oder unbekannte
   Namen, wie `Farbwelt.ausName`.
2. **`StilWerte`** je Stil und Variante:
   - Farben als `FoodyFarben` (damit Material und bestehende Komponenten sofort passen);
   - Typografie und Formen (Q kantig, R dicke Konturen mit Versatzschatten, S fast eckig, T rund);
   - Flächenart statt fest Glas (`Glas`, `Papier`, `Tafel`, `Panel` …), Hintergrund, Navigationsart, Akzentfarben.
   `FoodyFormen` und die Typografie werden dafür über das Theme gelesen statt als festes Objekt.
3. **Theme:** `FoodyTheme(stil, variante, akzent, dunkel, farbwelt, unschaerfe)`. Neu sind `FoodyTheme.stil` und
   `FoodyTheme.stilWerte`. Bei Milchglas ist alles wie heute.
4. **Komponenten stilfähig:** `GlasFlaeche`, `FoodyHintergrund`, `FoodyNavigation`, `FoodyKnopf`, `Kachel`,
   `Seitenkopf`, `Naehrwertbalken`, `Tagesleiste`, `Suchfeld`, `FoodyChip`, `Rezeptkarte`, `LeererZustand` lesen die
   Stilwerte. Das ergibt den größten Teil von **Ruhig** fast ohne Arbeit in den Features. Rund 80 feste Ecken in den
   Features (`RoundedCornerShape`, `CircleShape`) werden auf `FoodyTheme.formen` umgestellt.
5. **Szenen (Immersiv):** Jeder Feature-Screen bekommt Plätze für Szenen, die ein Stil füllen kann, etwa Kopf,
   Tagesbilanz, Liste, Tag und Posten. Beispiele: Q Lagerfeuer, U Auge, V Planetensystem, T Maibaum.
   - Die Szenen liegen im Feature unter `stil/` (z. B. `feature/heute/stil/HeuteSzeneSage.kt`) und bekommen denselben
     `Zustand` und dieselben `Aktionen` wie der normale Screen. So bleibt die Logik an einer Stelle, und die
     ViewModel-Tests gelten für alle Stile.
   - Heute, Kochen und Rezept sind schon so getrennt (`…Aktionen`). Woche, Einkauf, Rezeptliste und Zutaten werden
     vorher getrennt; das ist ein eigener kleiner Umbau-PR je Bereich.
   - Was mehrere Features brauchen (Sternenfeld, Pergament, Rautenband, Holzschnitt-Landschaft, Stempel), kommt nach
     `:core:designsystem/stil/<stil>/`. Die SVG-Szenen werden als Compose-`Canvas` bzw. `ImageVector` nachgebaut.
6. **Texte:** Nach ADR 0016 stehen Texte im Code. Neu ist `StilTexte` im Designsystem für Navigation, Seitentitel
   und Hauptknöpfe. Texte, die nur ein Feature betrifft, kommen in dessen `…Texte.kt` mit
   `stilText(standard, sage = …, bayrisch = …)`.
7. **Schriften:** 14 Familien kommen dazu, heute sind es 2 (140 KB).
   - Die japanischen Schriften (Kaisei Decol, Zen Kaku Gothic New, M PLUS Rounded 1c, Dela Gothic One) sind ohne
     Beschnitt mehrere MB groß. Sie werden auf Latein und die benutzten Zeichen (済, 一 二 三 …) beschnitten, mit
     einem Skript in `werkzeuge/`. Den Aufräumpunkt Q8 (Fraunces beschneiden) gleich mit erledigen.
   - Budget: höchstens 2 MB mehr APK.
   - Keine nachladbaren Schriften, damit die App offline bleibt.
8. **Speicher** (`:core:data`): `DarstellungSpeicher.stil()`, `stilVariante()`, `stilAkzent()` mit Startwert
   sofort aus den Prefs `foody`. Neue Schlüssel: `stil`, `stilVariante`, `stilAkzent`. Fehlt ein Wert, gilt Milchglas.
9. **Einstellungen:** Der Abschnitt „Darstellung“ bekommt drei Teile:
   - „Stil“: sieben Musterkacheln mit Namen, als Optionsfeld-Gruppe;
   - „Variante“: Immersiv/Ruhig, nur bei Stilen;
   - „Farbe“: Farbwelten bei Milchglas, sonst die Akzente des Stils.
   „Modus“ gibt es nur bei Milchglas. `MainActivity` gibt die Wahl an das Theme weiter, wie heute die Farbwelt.
10. **Tests:**
    - `KontrastTest`: über alle Stile, Varianten und Akzente, auch Text auf den Szenen.
    - `StilTest`: `ausName`, Standard Milchglas.
    - Speicher- und ViewModel-Tests wie bei der Farbwelt.
    - Roborazzi: Milchglas unverändert. Pro Stil kommen nur die vier Hauptscreens in Immersiv und Ruhig dazu (6 × 4
      × 2 = 48 Bilder), die übrigen Screens einmal je Stil in Immersiv. Nicht jede Kombination bekommt ein Bild.
    - `EinstellungenScreenTest`: Stil wählen speichert ihn, die Variante erscheint, und „Modus“ verschwindet.

## Phasen

### Phase 0 – Entscheidungen und Entwurf prüfen
- [ ] Mit dem Nutzer die Entwürfe im Canvas durchsehen. Sie sind nie gerendert geprüft worden, Abstände und frei
      platzierte Elemente können danebenliegen.
- [ ] Rahmen oben bestätigen (fester Grund je Stil, Wörter in Immersiv, Tablet).
- [x] Pilot festgelegt (Nutzer, 2026-11-11): **Q · Düstere Sage**. Dunkel, lateinische Schriften (Cinzel,
      EB Garamond), kantige Formen.
- [ ] Festlegen, welche Stile außer Q in die App kommen und in welcher Reihenfolge.
- [ ] ADR 0020 „Wählbare Stile“ (erweitert ADR 0015, Milchglas bleibt Standard) und Spec in
      `docs/superpowers/specs/`.

### Phase 1 – Grundgerüst, ohne sichtbare Änderung
- [ ] `FoodyStil`, `StilVariante` und `StilWerte`. Milchglas wird zu einem Stil mit den heutigen Werten.
- [ ] `FoodyFormen` und Typografie über das Theme, Speicher, Weitergabe in `MainActivity`.
- [ ] Einstellungen: Auswahl „Stil“, vorerst nur Milchglas freigeschaltet (oder hinter dem Debug-Build).
- [ ] Beweis: Alle Roborazzi-Bilder bleiben gleich (`verifyRoborazziDebug`), `./gradlew check` ist grün.

### Phase 2 – Komponenten stilfähig, Pilot Ruhig
- [ ] Die Komponenten aus „Aufbau“ Punkt 4 lesen die Stilwerte. Feste Ecken in den Features werden umgestellt.
- [ ] Schriften des Pilots einbinden (Cinzel, EB Garamond).
- [ ] Q in **Ruhig** auf allen Screens. `KontrastTest` und Roborazzi bekommen den Pilot dazu.
- [ ] Auf dem Debug-Build am Handy und Tablet ansehen.

### Phase 3 – Ruhig für die übrigen Stile
- [ ] Werkzeug zum Beschneiden der Schriften, danach die Schriften für R, S, T, U, V (APK-Größe vorher und nachher
      messen).
- [ ] `StilWerte` Ruhig für R, S, T, U, V. Danach sind alle Stile wählbar, Immersiv zeigt bis Phase 6 noch Ruhig.
- [ ] Danach kann man schon alle Stile im Alltag benutzen. Ab hier könnte man aufhören, falls Immersiv zu viel ist.

### Phase 4 – Immersiv Pilot, Hauptscreens
- [x] Zustand und Anzeige trennen: war nicht nötig, die Szenen hängen sich in die bestehenden Routen ein.
- [ ] Die Szenen-Plätze (erledigt: `StilWerte.immersiv`) und `StilTexte` einführen.
- [x] Q Immersiv für Heute (Rast am Feuer), Rezepte (Grimoire), Woche (Karte der Woche) und Einkauf (Händler), 2026-11-11.
      Flamme und Glut ruhen, wenn Animationen aus sind.
- [ ] Leistung: In Listen keine Unschärfe oder teuren Zeichnungen pro Zeile. Baseline-Profil prüfen.

### Phase 5 – Immersiv Pilot, restliche Screens
- [ ] Eintragen, Tagebuch, Rezept, Editor, Kochen, Planen, Listen, Einstellungen, Zutaten. Erledigt: Rezept, Kochen,
      Tagebuch; die übrigen über Bausteine und Wörter (2026-11-12).
- [ ] Abnahme des Pilots auf dem Handy. Erst danach geht es mit den nächsten Stilen weiter.

### Phase 6 – Immersiv für die übrigen Stile
- [ ] Ein Stil nach dem anderen, je Stil zwei bis drei PRs (Hauptscreens, dann Rest). Die Reihenfolge legt der Nutzer
      fest.
- [ ] Mundart (T) und die Texte der Szenen einmal vom Nutzer gegenlesen lassen.
- Erledigt: Wörter R–V (Navigation, Titel, Knöpfe) und die Heute-Szenen aller Stile (U „Das Auge“, V
  „Tagessystem“, T „Maibaum“, R „HEUTE!“, S „Rollbild“). Alle Hauptscreens haben in R–V ihre Szene
  (Heute, Einkauf, Woche, Rezeptliste, Rezept, Tagebuch, Kochen), so weit wie bei der Sage. Editor, Einstellungen,
  Eintragen, Listen, Planen und Zutaten zeigen wie bei der Sage die Bausteine und Wörter des Stils. Knopf, Seitenkopf, Blatt-Titel, Chip und Blatt folgen dem
  Stil seit „Bausteine R–V“ und „Chips und Blätter R–V“.

### Phase 7 – Widgets und Abschluss
- [x] Die bestehenden Knopf-Widgets (Scannen, Foto) bekommen Farbe und Form des gewählten Stils. Es sind
      `RemoteViews`, Szenen gehen dort nicht.
- [x] Die neuen Widgets aus den Entwürfen (Heute, Kalorien, Einkauf, Woche, Schnell eintragen) mit eigenem Plan
      (`docs/superpowers/plans/2026-11-13-widgets.md`).
- [x] Doku: `docs/architecture.md` (Navigation und Widgets), „Ergebnis“ hier. In `docs/design/` gibt es keine Tabelle
      der Widgets.

## Aufwand und Risiken

- **Grob:**
  - Phase 1 bis 3 sind überschaubar: etwa 6 bis 8 PRs, meist Designsystem, wenig in den Features.
  - Immersiv ist der große Teil: 6 Stile × 14 Screens mit eigenen Zeichnungen, etwa 15 bis 20 PRs.
- **Pflege:** Jede künftige Änderung an einem Screen muss in bis zu sieben Stilen mitgezogen werden. Bei Ruhig passiert
  das fast von selbst, bei Immersiv nicht. Weniger Stile oder nur Ruhig halten den Aufwand klein.
- **Kontrast:** Mehrere Entwürfe nutzen helle Nebentexte auf dunklem Grund und Text auf Bildern. Ungeprüft, einzelne
  Farben werden sich ändern müssen.
- **APK-Größe:** durch die Schriften, siehe Budget.
- **Entwürfe sind Beispiele:** Daten wie KW 43 sind erfunden. Lange Rezeptnamen, leere Tage, 30 Einkaufsposten und
  große Schrift zeigen die Entwürfe nicht. Die Szenen müssen dafür robust werden (abschneiden, scrollen,
  ausweichen).

## Ergebnis

**2026-11-11 – Grundgerüst und alle Stile wählbar** (Branch `claude/stile-q-v`, Nutzerwunsch: alle Stile Q bis V
gleich, nicht nur der Pilot):
- `:core:designsystem/stil/`: `FoodyStil` (Milchglas + Q bis V, je vier Akzente aus dem Canvas), `StilVariante`,
  `StilWerte`. Farben, Formen (Q kantig, R Pillen mit Versatzschatten, V abgeschnittene Ecken …), Schrift,
  deckende Karten (`Flaechenart.DECKEND`) und Hintergrundmuster (Punkte, Fasern, Rautenband, Sterne, nur Immersiv).
  Die Farben der Entwürfe werden beim Ableiten so weit verschoben, bis sie AA halten (`angleichen`).
- `FoodyTheme(stil, variante, akzent)`; `FoodyTheme.stil`, `.formen`, `.augenbraue`. `FoodyFormen` ist jetzt eine
  Datenklasse (`FoodyFormen.Rund` = Milchglas, neu `knopf`), die Typografie eine Fabrik (`foodyTypografie`).
- Speicher: Schlüssel `stil`, `stilVariante`, `stilAkzent` (`DarstellungSpeicher.stil()` als `StilWahl`).
- Einstellungen › Darstellung: Stil (Musterkacheln), bei Milchglas Farbe und Modus wie bisher, sonst Variante und
  Akzent. `MainActivity` gibt die Wahl ans Theme, die Systemleisten folgen dem festen Grund.
- Tests: `KontrastTest` über alle Stile × Varianten × Akzente, `StilTest`, Milchglas unverändert (Werte und alle
  Referenzbilder außer Einstellungen, dort kam die Stilwahl dazu), Speicher-, ViewModel- und Screen-Tests;
  neue Referenzbilder `feature/heute/.../stil_<stil>_<variante>.png`.

**2026-11-11 – Pilot Q Immersiv auf „Heute“** (Branch `claude/stile-q-heute`, baut auf dem Grundgerüst auf):
- Schriften Cinzel und EB Garamond (variabel, noch unbeschnitten, zusammen 976 KB) in `res/font`, Lizenzen in
  `core/designsystem/lizenzen/`. Q nutzt sie in Immersiv und Ruhig.
- Szenen-Platz: `StilWerte.immersiv(stil)`; `HeuteScreen` zeigt bei Q Immersiv `stil/HeuteSage.kt` mit demselben
  Zustand und denselben Aktionen. Szene „Rast am Feuer“ als Compose-`Canvas` aus den SVG-Pfaden des Entwurfs,
  Flamme und Glut ruhen ohne Animationen. Darunter Vitalität und das Menü „Was tun?“ (`sageMenue`) mit Hauptknopf;
  „Gegessen?“, Eintragen, Tagebuch, Einkauf und Einstellungen bleiben erreichbar. TalkBack liest die normalen Begriffe.
- Tests: `SageMenueTest`, `HeuteSageTest`, Referenzbild `stil_sage_immersiv`.

**2026-11-11 – Q Immersiv: Rezepte, Woche, Einkauf** (Branch `claude/stile-q-haupt`):
- Gemeinsame Bausteine der Sage in `:core:designsystem/stil/sage/SageBausteine.kt` (Schriften in den Größen des
  Entwurfs, Seitenkopf, Hauptknopf, Nebenknopf, Raute); „Heute“ nutzt sie jetzt auch.
- Rezepte als **Grimoire** (`feature/rezepte/liste/stil/RezepteSage.kt`): Suche und alle Filter der normalen Liste,
  Treffer einzeln aufgeschlagen mit Emblem, Art, Zeit, kcal, „gekocht“ und einem Satz aus den Zutaten (statt eines
  erfundenen Textes); „‹ Aufschlagen ›“, „+“ für Neu/Import.
- Woche als **Karte der Woche** (`feature/woche/stil/WocheSage.kt`): Weg durch die sieben Tage (heute leuchtet,
  leere Tage hohl), darunter der gewählte Tag; Gerichte öffnen das Mahlzeit-Blatt, „+ Mahl hinzufügen“ und „Leere
  Tage füllen“ nutzen dieselben Blätter wie die normale Ansicht. Ein eigener Umbau von `WocheRoute` war nicht nötig.
- Einkauf als **Der Händler** (`feature/einkauf/stil/EinkaufSage.kt`): Kopf mit erledigt/gesamt, „Listen“ und Menü,
  der Laden mit dem Spruch des Händlers, Posten als Felder mit Raute (Antippen hakt ab und wieder auf, Langdruck =
  Details). Eingabe, Abgleich-Hinweis, „Zuletzt“ und Katalog bleiben wie gehabt.
- Tests: je Bereich Szene im Screen-Test (Blättern, Öffnen, Abhaken, Blätter), `GrimoireTexteTest`,
  `HaendlerSpruchTest`; Referenzbilder `sage_liste_handy`, `sage_woche_handy`, `sage_einkauf_handy`.

**2026-11-11 – Q Immersiv: Rezept, Kochen, Tagebuch** (Branch `claude/stile-q-rezept`):
- Rezept „Aus dem Grimoire“ (`feature/rezepte/detail/stil/RezeptSage.kt`): Raute mit Anfangsbuchstaben, Titel,
  Angaben und Einordnung, Portionen, Reiter Zutaten · Ritual · Werte (römische Schritte, Nährwerttabelle, Notiz,
  Quelle), unten „Am Feuer kochen“, „Auf die Liste“ und die Favoriten-Raute; im Menü „⋯“ Neu niederschreiben,
  Abschrift anfertigen, Ins Archiv verbannen. „In die Woche“ aus dem Entwurf fehlt, die App hat dafür keine Funktion.
- Kochen (`feature/kochen/stil/KochenSage.kt`): „Vorbereitung“/„Schritt 2 / 5“ über dem Rezept, Segmente, „Lege
  bereit“ mit Rauten, je Schritt die römische Zahl groß, Zeitangaben als Sanduhr, unten „‹“ und Beginnen/Weiter/
  Vollenden. Pager, Timerleiste und Fertig-Blatt bleiben gemeinsam.
- Tagebuch als „Chronik“ (`feature/heute/stil/TagebuchSage.kt`): Tageswahl mit „gestern“/„vor zwei Tagen“ (Antippen
  öffnet die Datumswahl), Ring und Makros, Gruppen „Des Morgens“ …, Antippen eines Eintrags öffnet „Eintrag
  berichtigen“ (Ändern im bekannten Dialog, Tilgen mit „Rückgängig“), Raute „+“ zum Eintragen.
- Designsystem: `Sage.roemisch`, `SageHauptknopf(schrift, aktiv)`, `SageNebenknopf(farbe)`.
- Tests: Szenen in den Screen-Tests (auch 200 % Schrift), `ChronikTexteTest`; Bilder `sage_detail_handy`,
  `sage_bereitlegen_handy`, `sage_schritt_handy`, `sage_tagebuch_handy`.

**2026-11-12 – Q Immersiv: übrige Screens über die Bausteine** (Branch `claude/stile-q-rest`):
- Gemeinsame Bausteine kennen die Sage in Immersiv: `FoodyKnopf` (eckig, Cinzel, Primär mit Goldschimmer),
  `FoodyChip` (Rahmen im Akzent statt gefüllt, `beschreibung` für TalkBack), `GlasBlatt` (eckig, dunkler Verlauf,
  Goldlinie oben), `Seitenkopf` (Cinzel im Akzent, `beschreibung`), neu `BlattTitel`.
- `stilText(standard, sage = …, anime = …, …)` für Wörter je Stil (Punkt 6 des Aufbaus).
- Wörter der Entwürfe: Eintragen („Mahl eintragen“, Morgen/Mittag/Zwischen/Abend, „In die Chronik“), Planen („Leere
  Tage bestücken“, „Am Feuer kochen“, „Von der Karte nehmen“), Listen („Listen des Händlers“, „Mit dem Weg
  abgleichen“, „Liste schreiben“), Editor („Neue Seite“/„Berichtigen“, „Ins Grimoire“), Zutaten („Verzeichnis“),
  Einstellungen („Das Lager“, Abschnitte Botendienst, Gestalt, Vitalität, Wissen, Die Seherin). TalkBack liest
  überall die normalen Begriffe.
- Bilder `sage_einstellungen_handy`, `sage_zutaten_handy`, `sage_editor_handy`, `sage_listen_handy`,
  `sage_fuellen_handy`; Test „Eintragen-Blatt im Stil“.

**2026-11-12 – R–V: Wörter der Entwürfe** (Branch `claude/stile-woerter`):
- In Immersiv zeigen Navigation, Seitentitel, Blätter und Knöpfe die Wörter je Stil (Bayrisch „Heid · Rezepte ·
  Wochn · Eikaufn“, Sternenkreuzer „Brücke · Archiv · Kurs · Fracht“, Grauen „Das Auge“, „Mondphasen“, „Logbuch“,
  Anime „Heute!“, „Quest!“ …). `NavZiel.beschreibung` und `beschreibung` am Seitenkopf: TalkBack liest die normalen
  Begriffe. Test `StilTexteTest`.

**2026-11-12 – U Immersiv: „Das Auge“** (Branch `claude/stile-u-heute`):
- Heute (`feature/heute/stil/HeuteGrauen.kt`): Titel und Datum in Schreibmaschine, das Auge mit Ring um die Iris
  (kcal gegen das Ziel, in der Glut), schwebt und blinzelt (ruht ohne Animationen), Satz „Es hat 860 von 2.000 kcal
  gesehen.“; Tafel „Das Ritual des Abends“ mit doppeltem Rahmen und „Das Ritual beginnen“ (ohne Plan „Eine
  Eingebung“); Protokoll „Was bisher dargebracht wurde“ je Mahlzeit, leer mit „+ notieren“, Geplantes mit
  „Gegessen?“; Verweis „Expeditionsliste“. Fangarme aus der Tiefe am unteren Rand.
- Der Entwurf zeigt im Protokoll Uhrzeiten; die App speichert keine, dort steht die Mahlzeit.
- Designsystem: `stil/grauen/GrauenBausteine.kt` (`Grauen.titel/text/maschine`, `GrauenKnopf`, `GrauenNotizknopf`),
  Systemschriften Serif und Monospace statt IM Fell English und Special Elite.
- Tests `HeuteGrauenTest` (Aktionen, TalkBack, 200 % Schrift, Texte); Bild `stil_grauen_immersiv` neu.

**2026-11-12 – V Immersiv: „Tagessystem“** (Branch `claude/stile-v-heute`):
- Heute (`feature/heute/stil/HeuteKreuzer.kt`): Kopf „SA 24.10. · LOGBUCH 297“, Titel und „43 % ERREICHT“; die
  Sonne ist das Tagesziel, jede Mahlzeit ein Planet auf ihrer Bahn (gelandet = gegessen, gestrichelt Amber =
  geplant, grau = frei). Antippen wählt, darunter „ORBIT 3 · ABEND“ mit Gericht und dem Weg weiter (Bordbuch,
  Systemdaten/Gelandet?, Proviant erfassen). Rechts der Treibstoff (kcal gegen das Ziel), darunter Makros, die
  Karte „Nächste Mission“ mit abgeschnittenen Ecken („Mission starten“, „+ Eintragen“) und „Frachtraum“. Bahn dreht
  sich, die Korona pulsiert (ruhen ohne Animationen).
- Uhrzeiten des Entwurfs („19:00“) fehlen, die App speichert keine.
- Designsystem: `stil/kreuzer/KreuzerBausteine.kt` (`Kreuzer.titel/mono/ecke`, `KreuzerKnopf`, `KreuzerVerweis`,
  `kreuzerAmber`), Systemschriften statt Exo 2 und Share Tech Mono.
- Tests `HeuteKreuzerTest` (Planeten wählen, alle Wege, TalkBack, 200 % Schrift, Texte); Bild `stil_kreuzer_immersiv`.

**2026-11-12 – T Immersiv: „Heid“ mit dem Maibaum** (Branch `claude/stile-t-heute`, Entwurf `T-Maibaum`):
- Heute (`feature/heute/stil/HeuteBayrisch.kt`): links der Maibaum mit weiß-blauem Stamm, Kranz (= Tagesziel) und
  Fahne; der Stand der kcal klettert als Ring mit Schild hinauf, die Mahlzeiten hängen als Schilder daran
  (Friahstück unten, Brotzeit oben; gegessen weiß, offen gestrichelt, „no nix“). Bei großer Schrift rücken die
  Schilder auseinander und der Baum wird höher (eigenes Layout). Rechts „Griaß di!“, „Heid“, „Heid auf d'Nacht“ mit
  „Pack ma's!“, die Bilanz „Passt scho – no 1.140 frei“ mit den Nährwerten, „+ Brotzeit eitragn“ und Kamera,
  „Eikaufszettl“. Hinten Himmel, Berge und Kirchturm. TalkBack liest den Baum als einen Satz in normalen Worten.
- Der Titel „Heid“ wächst mit der Systemschrift nur bis 130 % (wie große Schrift unter Android 14).
- Designsystem: `stil/bayrisch/BayrischBausteine.kt` (`Bayrisch.titel/text`, Formen, `BayrischKnopf`,
  `bayrischBlau`), Systemschriften statt UnifrakturCook und Alegreya.
- Tests `HeuteBayrischTest` (alle Wege, 200 % Schrift, Texte); Bild `stil_bayrisch_immersiv` neu.
- Die Mundart bitte einmal gegenlesen (Phase 6).

**2026-11-12 – R Immersiv: „HEUTE!“** (Branch `claude/stile-r-heute`):
- Heute (`feature/heute/stil/HeuteAnime.kt`): schräges „HEUTE!“ mit Schatten im Akzent, gelbes Datumsabzeichen;
  Foody das Reisbällchen mit Sprechblase („Heute Abend gibt's Ofengemüse! Noch 1.140 kcal frei – das passt
  perfekt.“, Texte je Lage); die nächste Mahlzeit als Karte im Akzent mit Speedlines, hartem Schatten, Angaben als
  Pillen und „LOS GEHT'S! ▶“; „ENERGIE · Tagebuch ▸“ mit zehn schrägen Segmenten und den Nährwerten; gegessene
  Mahlzeiten als schief geklebte Zettel neben der Kachel „+ Eintragen“; „QUEST · 9 offen ▸“.
- Der Titel wächst mit der Systemschrift nur bis 130 %.
- Designsystem: `stil/anime/AnimeBausteine.kt` (`Anime.titel/text`, Gelb, Türkis, `animeSchatten`, `AnimeKnopf`,
  `AnimePille`), Systemschrift statt Dela Gothic One und M PLUS Rounded 1c.
- Tests `HeuteAnimeTest` (alle Wege, 200 % Schrift, Sprechblase, Texte); Bild `stil_anime_immersiv` neu.

**2026-11-12 – S Immersiv: Heute als Rollbild** (Branch `claude/stile-s-heute`):
- Heute (`feature/heute/stil/HeuteEdo.kt`): Rollbild mit Holzstäben, Indigo-Montierung und Quasten; auf dem
  Papier eine Tuschelandschaft (Sonne im Zinnober, Berge, Nebel, Vögel, der Wochentag senkrecht, Siegel), darunter
  „HEUTE ABEND · 24. OKTOBER“, das Gericht und „KOCHEN“. „Speisefolge ›“ mit kcal gegen das Ziel, Nährwertbalken
  und je Mahlzeit einer Zeile mit Stempel (gegessen 済 im Zinnober, offen gestrichelt). Neu gegenüber dem Entwurf:
  den leeren Stempel einer geplanten Mahlzeit antippen fragt „Gegessen?“. Unten „Einkaufszettel“, „＋ Eintragen“,
  „Scannen · Foto“ und die Einstellungen.
- Das Zeichen 済 bleibt so groß wie sein Stempel; TalkBack liest „gegessen“ an der Zeile.
- Designsystem: `stil/edo/EdoBausteine.kt` (`Edo.titel/text/kopf`, Papier-, Holz- und Indigofarben, `EdoKnopf`,
  `edoShu`), Systemschriften statt Kaisei Decol und Zen Kaku Gothic New.
- Tests `HeuteEdoTest` (alle Wege, Stempel, 200 % Schrift, Texte); Bild `stil_edo_immersiv` neu.

**2026-11-12 – Bausteine R–V** (Branch `claude/stile-bausteine`):
- `FoodyKnopf` zeichnet in R–V (Immersiv) den Knopf des Stils: Primär voll, Umriss als helle oder umrandete
  Variante, Text als Verweis in der Schrift des Stils (`stil/StilBausteine.kt`, `StilKnopf`). Inaktiv blasst er ab
  und ist nicht klickbar; immer mindestens 48 dp hoch.
- `Seitenkopf` und `BlattTitel` nehmen die Titelschrift des Stils (`stilTitel`), in den Größen der normalen Ansicht.
  Damit tragen alle Screens ohne eigene Szene (Rezepte, Woche, Einkauf, Einstellungen …) Schrift und Knöpfe des Stils.
- Die Knöpfe der fünf Stile haben dafür `aktiv` bekommen, Grauen einen Umriss, Anime eine weiße Pille.
- Tests `StilKnopfTest` (jeder Stil, jede Art klickt und hat 48 dp; inaktiv nicht klickbar); Galerie
  `StilBausteineScreenshotTest` im Katalog, Bilder `stil_bausteine_*`.

**2026-11-12 – Chips und Blätter R–V** (Branch `claude/stile-chips-blatt`):
- `FoodyChip` in R–V (Immersiv): gewählt wie der volle Knopf des Stils, sonst wie sein Umriss (`stilChip`).
- `GlasBlatt` in R–V (Immersiv) mit Grund und Kante des Stils (`stilBlatt`): Tintenrand und Pillengriff (Anime),
  Indigoband mit Holzlinie (Edo), weiß-blaue Rautenreihe (Bayrisch), doppelte Glutlinie (Grauen), Amberlinie mit
  abgeschnittener Ecke oben rechts (Kreuzer). Der Grund ist der des Stils, damit der Text AA hält.
- Tests: `StilKnopfTest` prüft auch den Chip (Auswahl für TalkBack, 48 dp); Galerie mit Chips, neue
  `StilBlattScreenshotTest` (Bilder `stil_blatt_*`).

**2026-11-12 – Einkauf R–V** (Branch `claude/stile-einkauf`):
- Wie bei der Sage ersetzt die Szene Kopf und Posten; Eingabe, „Zuletzt“ und Katalog bleiben. Antippen hakt ab (und
  wieder auf), Langdruck öffnet die Details, der Knopf im Kopf die früheren Listen (`stil/EinkaufStile.kt`).
- R „QUEST!“ mit XP-Balken, Abteilungen als weiße Karten mit Abzeichen, gekaufte mit „GET!“ (`EinkaufAnime.kt`).
- S Tuschering für den Fortschritt, Zeilen mit Ensō und Pinselstrich, Abteilungen „Vom Markt“ … (`EinkaufEdo.kt`).
- T Markise, Schild „'s Standl“ mit „no 3 offn“, Abteilungen als Holzkisten („Vom Gmiasstandl“) (`EinkaufBayrisch.kt`).
- U „Expeditionsliste“ auf schiefem Pergament, nummeriert, Stempel „Besorgt“, die Liste flüstert (`EinkaufGrauen.kt`).
- V „Frachtraum“ mit Ladeanzeige in Segmenten, Posten als Kisten mit Code, Band „GELADEN“ (`EinkaufKreuzer.kt`).
- Die Abteilungsnamen der Stile hören TalkBack in normalen Worten; Titel wachsen bis 130 %.
- Tests `EinkaufStileTest` (Abhaken, Details, frühere Listen, 200 % Schrift, Texte); Bilder `stil_*_einkauf_handy`.
- Die Mundart („Vom Gmiasstandl“, „no 3 offn“, „Zettl“) bitte mit gegenlesen.

**2026-11-12 – Woche R–V** (Branch `claude/stile-woche`):
- Wie bei der Sage zeigt die Szene die ganze Woche; die Blätter (Mahlzeit, Mahlzeitwahl, Füllen) bleiben dieselben.
  Jede Szene: Kopf mit „6 / 7“, Blättern (‹ › und „Heute“), die sieben Tage zum Wählen, der gewählte Tag mit seinen
  Gerichten (Antippen öffnet das Mahlzeit-Blatt), „+ Mahlzeit“ und „Leere Tage füllen“ (`stil/WocheStile.kt`).
- R „WOCHE!“, die Tage als Comic-Panels in drei Reihen, heute im Akzent mit Speedlines, leere mit „?!“.
- S Votivtafeln an zwei Holzleisten, heute im Zinnober; Papier des Tages, „絵馬 · Leere Tage vorschlagen lassen“.
- T „Wochn“ mit Wimpelkette (blau-weiß, heute rot), Karte mit doppeltem Rahmen, alle Tage als Liste („heid“).
- U „Mondphasen“: je mehr Mahlzeiten geplant sind, desto voller der Mond; Tafel mit Notiz, „Die dunklen Nächte …“.
- V „Flugplan“: Sprungroute aus Rauten (vergangen Holo, heute Amber pulsierend), Sprungkarte, Liste mit Status.
- Tests `WocheStileTest` (Tag wählen, Eintrag, + Mahlzeit, Füllen, Blättern, 200 % Schrift, Texte); Bilder
  `stil_*_woche_handy`. Robolectric hängt mit `w411dp` und offenem Dialog; der Test nimmt deshalb die Standardbreite.
- Mundart: „Wochn“, „plana“, „heid“, „Sunda“, „no frei“, „Noch nix gplant“, „+ Essn planen“, „Leere Tag füllen“.

**2026-11-12 – Rezeptliste R–V** (Branch `claude/stile-rezepte`):
- Wie beim Grimoire der Sage dieselbe Suche, dieselben Filter und Ziele (öffnen, neu, importieren). Die Treffer
  kommen in Seiten zu sechs (‹ Seite 1 / 3 ›), das gewählte Rezept übersteht das Drehen und beginnt bei jeder neuen
  Suche vorn (`liste/stil/RezepteStile.kt`). Neuer Baustein `StilAktion`: Knopf des Stils mit eigener Beschreibung.
- R „REZEPTE“ schräg, die Treffer als Comic-Karten zu zweit (Kopf mit Speedlines, Sterne für „gekocht“, FAV/FIX/NEU);
  Antippen öffnet direkt.
- S Fächer aus Papierblättern (das gewählte in Zinnober), Papierkarte mit „Öffnen“.
- T Lebkuchenherzen an Holzstangen („Vom Lebkuchenstand“), Karte mit doppeltem Rahmen und „Pack ma's!“.
- U „Die Bibliothek“: Bände auf dem Regal, der gewählte steht heraus und glüht; Pergamentseite, „Aufschlagen“.
- V „Sternkarte“: Systeme nach kcal groß, Favoriten in Amber, das gewählte mit Ring; Karte mit FLUGZEIT, ENERGIE,
  BESUCHE und „Kurs setzen“.
- Der Entwurf zeigt „Kochen“ und „In die Woche“ auf der Karte; die Liste hat diese Ziele nicht, der Knopf öffnet das
  Rezept (dort geht beides). Die Texte des Entwurfs je Rezept sind erfunden; die App zeigt den Zutatensatz.
- Tests `RezepteStileTest` (wählen, öffnen, Seiten, Menü, leer, 200 % Schrift, Texte); Bilder `stil_*_liste_handy`.
- Mundart: „Rezept oder Zutat suacha“, „Pack ma's!“, „Aus da Kuchl“.

**2026-11-12 – Rezept R–V** (Branch `claude/stile-rezept`):
- Alle fünf Entwürfe haben denselben Aufbau, daher ein gemeinsames Gerüst (`detail/stil/RezeptStile.kt`) mit Wörtern
  und Schriften je Stil (`RezeptArt`): Kopf mit ‹ und Menü (Bearbeiten, Duplizieren, Archivieren), Hinweis
  „archiviert“ mit Zurückholen, Portionen, die Reiter Zutaten · Zubereitung · Nährwerte (für TalkBack immer diese
  Wörter), unten Kochen, Einkaufsliste und Favorit. Die Knopfleiste wächst mit der Schrift, der Inhalt rückt mit.
- Titelblöcke je Stil (`RezeptHelden.kt`): R Comic-Panel mit Speedlines, Pille und Abzeichen ZEIT/KCAL; S Papier mit
  Zinnober-Stempel; T Rautenband und Herz; U glühende Pergamentseite; V Karte mit FLUGZEIT, ENERGIE, EIWEISS.
- Der Entwurf zeigt „In die Woche“; das Rezept hat dieses Ziel nicht, an seiner Stelle steht die Einkaufsliste.
- Die Einordnungszeile der Sage nutzt jetzt dieselbe Funktion.
- Tests `RezeptStileTest` (Reiter, Portionen, alle Aktionen, Archiv, 200 % Schrift, Texte); Bilder
  `stil_*_rezept_handy`.
- Mundart: „Zuadaten“, „So geht's“, „Portiona“, „Eikaufn“, „zruckholn“.

**2026-11-12 – Tagebuch R–V** (Branch `claude/stile-tagebuch`):
- Gemeinsames Gerüst (`feature/heute/stil/TagebuchStile.kt`) mit Wörtern und Schriften je Stil (`TagebuchArt`):
  Titel, Tag mit ‹ › und Datumswahl, Ring mit kcal und Makros, je Mahlzeit eine Gruppe; Antippen eines Eintrags
  öffnet darunter „Ändern“ und „Löschen“ (U „Tilgen“), „+“ unten rechts trägt ein. Eine leere Mahlzeit trägt dort ein.
- R „TAGEBUCH!“ schräg, die Mahlzeiten als farbige Pillen, „Noch leer – tippe auf +“.
- S schlicht auf Papier; T „Tagebiachl“, „heid“, „Friahstück“, „Brotzeit“, „Passt scho · no 1.140 frei“, „no nix“.
- U „Logbuch – Aufzeichnungen eines Hungrigen“ mit einer Notiz zum Tag aus dem, was verzeichnet ist.
- V „Bordbuch – LOGBUCH · VERSORGUNG“, „HEUTE · LOGBUCH 297“, 20 Segmente statt Ring, „kein Kontakt“.
- Der Entwurf hat getrennte Knöpfe „Menge“ und „Mahlzeit“; beides ändert derselbe Dialog, daher ein „Ändern“. Die
  Uhrzeiten (U) fehlen, die App speichert keine; den Stempel „済“ (S) zeigt die App nicht.
- Tests `TagebuchStileTest` (öffnen und löschen, blättern, leere Mahlzeit und „+“ öffnen das Blatt, 200 % Schrift,
  Texte); Bilder `stil_*_tagebuch_handy`.
- Mundart: „Tagebiachl“, „heid“, „vorgestern“, „morgn“, „Friahstück“, „Passt scho · no … frei“, „Gnua für heid · …
  drüber“, „no nix“.

**2026-11-12 – Kochen R–V** (Branch `claude/stile-kochen`):
- Gemeinsame Teile (`feature/kochen/stil/KochenStile.kt`) mit Wörtern und Schriften je Stil (`KochArt`): Kopf mit ✕,
  Überzeile, Rezept und Portionen, Segmente; „Bereitlegen“ mit Kästchen zum Abhaken, „Von vorn“ und Portionen; je
  Schritt die Nummer groß im Akzent, der Text groß, die genannten Zutaten, Zeitkarten mit Start und „Als Nächstes“;
  unten Zurück und Beginnen/Weiter/Fertig. Pager, Timerleiste und Fertig-Blatt bleiben dieselben.
- R „BEREITLEGEN!“, „LOS GEHT'S! ▶“, „⏱ Start“; S schlicht in Zinnober; T „Herrichten“, „Pack ma's!“, „Weida“,
  „Zruck“, „Feddig!“, „Uhr aufziehn“; U römische Zahlen, „Was das Ritual verlangt“, „Die Zeit beginnen“, „Vollenden“;
  V „MISSION · REISTOPF · CREW 2“, „PHASE 2 / 3“, Countdown T-03 … T-01, „Countdown starten“, „Mission abschließen“.
- Den Kreisbogen des laufenden Timers (V) und die Schriftzeichen im Hintergrund (S) zeigt die App nicht; die
  laufenden Timer stehen wie sonst in der Timerleiste.
- Tests `KochenStileTest` (Abhaken, Weiter, Zurück, Schließen, Timer, 200 % Schrift, Texte); Bilder
  `stil_*_bereitlegen_handy`, `stil_*_schritt_handy`.
- Mundart: „Herrichten“, „Des brauchst“, „Weida“, „Zruck“, „Feddig!“, „Uhr aufziehn“, „Danach“.

**2026-11-12 – Zeilen und Stempel R–V** (Branch `claude/stile-felder`):
- Neu `WahlZeile` (`komponenten/WahlZeile.kt`): Treffer- und Listenzeile mit Titel, Info, Wert und Platz rechts
  (`ende`). In R–V (Immersiv) wie in den Entwürfen: umrandete Karte mit Akzentfläche (Anime), Haarlinie mit
  Zinnobermarke (Edo), gestrichelte Linie mit Punkt (Bayrisch), Linie mit Glutpunkt (Grauen), Linie mit Amberleiste
  (Kreuzer); sonst die normale Zeile mit Haken. TalkBack: Optionsfeld oder Knopf.
- `BlattTitel(zeichen = …)`: im Edo-Stil ein schräger Zinnoberstempel vor dem Titel („記“ beim Eintragen).
- Eintragen (Treffer, Lebensmittelschlüssel, gescannte Produkte), Planen (Rezept wählen), Listen (frühere Listen),
  Zutaten (Liste und „Zusammenführen mit …“) nehmen die Zeile; Milchglas, Ruhig und Sage bleiben unverändert.
- Tests `EintragenStileTest` (Auswahl, 200 % Schrift); Bilder `stil_*_eintragen_handy`, `stil_*_listen_handy`,
  `stil_*_zutaten_handy`.

**2026-11-12 – Planen R–V** (Branch `claude/stile-planen`):
- „Leere Tage füllen“: über den Vorschlägen ein Satz je Stil wie im Entwurf; jeder Vorschlag als `WahlZeile` mit
  dem Tag des Stils links (Anime buntes Kästchen, Edo „月 火 水 …“ in Zinnober, Bayrisch blau, Grauen in der Glut,
  Kreuzer „MO“ in Amber) und „↻“ rechts. Antippen wählt ab und wieder an (TalkBack: Kontrollkästchen „MI 28.10.
  übernehmen“), ohne Vorschlag ist die Zeile gesperrt (`stil/PlanenStile.kt`).
- `WahlZeile` kann dafür `haken` (Kontrollkästchen), `aktiv` und `anfang` (Platz links).
- Mahlzeit-Blatt: Portionen mit „−“ und „+“ als Knöpfe des Stils und „2 Portionen“ dazwischen.
- Tests `PlanenStileTest` (abwählen, neu ziehen, Portionen, 200 % Schrift, Texte); Bilder `stil_*_fuellen_handy`,
  `stil_*_mahlzeit_handy`.
- Mundart: „Foody füllt die freien Tag mit Rezepten, die länger nimma dran warn.“

**2026-11-12 – Seitenkopf ohne Worttrennung, Bilder Editor und Einstellungen R–V** (Branch `claude/stile-editor`):
- `Seitenkopf` legt Zurück, Titel und Aktionen selbst aus: Passt das längste Wort des Titels nicht mehr neben die
  Aktionen, rücken sie in eine Zeile darunter, statt das Wort zu trennen. Vorher brach „Bearbeiten“ im Editor
  (Bayrisch „Obacht, speichern!“, Grauen „In die Bibliothek“) als „Bearbeite/n“ um, in Milchglas bei 320 dp
  „Zutaten-/katalog“. Tests in `EntwurfsbausteineTest` (kurze Aktion daneben, lange darunter, Titel einzeilig).
- Neue Bilder `stil_*_editor_handy` und `stil_*_einstellungen_handy`. Editor und Einstellungen tragen wie bei der
  Sage Knöpfe, Felder, Chips, Kopf und Wörter des Stils; eigene Szenen haben sie nicht.

**2026-11-13 – Editor R–V** (Branch `claude/stile-editor-szene`):
- Der Nutzer legt fest: Die Entwürfe im Canvas sind der Soll-Zustand, auch für Editor und Einstellungen.
- Editor in R–V (Immersiv) wie in den Entwürfen (`feature/rezepte/editor/stil/EditorStile.kt`): Kopf mit ✕, Überzeile
  und Titel des Stils (Anime „Rezept-Edit!“, Bayrisch „Neis Rezept“, Grauen „Neue Abschrift“/„Abschrift berichtigen“,
  Kreuzer „Rezept anlegen“), Edo mit Stempel „新“ bei neuen Rezepten; Grunddaten auf einer Karte mit Etiketten über den
  Feldern (Kreuzer „CREW“, Bayrisch „Portiona“); Foto, Einordnung; Abschnitte „Zutaten“ (Bayrisch „Zuadaten“, Kreuzer
  „RESSOURCEN“) mit den erkannten Zeilen (Name, „neu im Katalog“ oder Fehler, Menge rechts) und „Zubereitung“
  („So geht’s“, „MISSIONSABLAUF“); unten „Verwerfen“ und „Speichern“ (Bayrisch „Obacht, speichern!“, Grauen „In die
  Bibliothek stellen“, Kreuzer „Ins Archiv speichern“).
- Nicht übernommen: die Reiter „Selbst · Link · Foto · Text“ der Entwürfe – Einlesen läuft in der App über den Import der
  Rezeptliste; Zutaten bleiben ein Textfeld mit Zeilenvorschau statt einzelner Zeilen mit ✕.
- Designsystem: `StilFeld` (Etikett über dem Feld, Aussehen je Stil), `StilAbschnitt`, `StilKarte`.
- Tests `EditorStileTest` (neu anlegen und speichern, Speichern gesperrt ohne Namen, Verwerfen fragt, 200 % Schrift,
  Texte); Bilder `stil_*_editor_handy` neu.
- `EintragenStileTest` wählt die Mahlzeit jetzt fest; ohne Wahl hingen die Bilder von der Uhrzeit ab.

**2026-11-13 – Einstellungen R–V** (Branch `claude/stile-einstellungen`):
- Einstellungen in R–V (Immersiv) wie in den Entwürfen (`feature/einstellungen/EinstellungenStile.kt`): Abschnitte als
  Karten (Anime mit Versatzschatten, Bayrisch) oder durch Linien getrennt (Edo, Grauen, Kreuzer); der Abgleich als
  eigene Karte (Edo Papier, Grauen Tafel, Kreuzer Amberkarte mit Schnittecken) mit Marke „verbunden“ (Edo Stempel
  „繋“, Punkt, Kreuzer grünes Quadrat). Wörter: Anime „EINSTELLUNGEN“/„SYNC“, Edo „Aussehen“/„Abgleich mit dem
  Server“, Bayrisch „Wia’s ausschaut“/„Mit’m Server abgleicha“, Grauen Überzeile „Die Instrumente justieren“,
  „Der Schein“, „Verbindung zum fernen Archiv“, „Verzeichnis der Substanzen“, Kreuzer Überzeile „BORDSYSTEME“,
  „ANZEIGE“, „Energieziel“ „KCAL / TAG“, „Ressourcen-Datenbank“, „VERBINDUNG ZUR BASIS“, Schalter „AN“/„AUS“.
- Zeilen mit gepunkteter (Anime, Edo), gestrichelter (Bayrisch) oder durchgezogener Linie und Schalter in der Form des
  Stils; Variante als Leiste (Optionsfelder), Akzente als Farbmuster (Kreis, Quadrat, Kreuzer mit Schnittecken).
- Tagesziel mit − und + (Schritte von 50, 500 bis 10.000) und Feld dazwischen; leeres Feld = kein Ziel wie bisher.
  `StilFeld` kann das Etikett nur für TalkBack tragen, mittig schreiben und „Fertig“ melden.
- Nicht übernommen: „Jetzt abgleichen“ und „Trennen“ direkt auf der Karte und das Anmeldeformular – das bleibt auf der
  Seite „Server verbinden“, die Zeile öffnet sie; Modus Hell/Dunkel/System gibt es in den Stilen nicht (fester Grund),
  an seiner Stelle steht die Variante. Stilwahl, Fotoerkennung, Sicherung, Daten und Hinweise behalten ihre Abschnitte.
- Tests `EinstellungenStileTest` (Abgleich öffnet, Farbe und Variante wählen, Ziel mit −/+ und Feld, Schalter, 200 %
  Schrift); Bilder `stil_*_einstellungen_handy` neu (verbunden, mit dem gewählten Stil).

**2026-11-13 – Knopf-Widgets im Stil** (Branch `claude/stile-widgets`):
- „Scannen“ und „KI-Foto“ tragen Fläche, Schrift, Rand, Rundung und Schatten des gewählten Stils wie in den
  Widget-Entwürfen (`app/widget/KnopfAussehen.kt`): Sage dunkel mit Goldrand und „SIEGEL“/„DEUTEN“, Anime Türkis und
  Gelb mit Tintenrand, Versatzschatten und „SCAN!“/„FOTO!“, Edo Tusche mit Zinnober-Symbol, Bayrisch im Akzent mit
  dunklem Rand, Grauen dunkel mit Glutrand, Kreuzer Amber. Milchglas bleibt in den Farben des Hintergrundbilds.
  Wörter nur in Immersiv, Ruhig ohne Schatten.
- `RemoteViews` kennen keine Szenen: Schatten, Rand und Knopf liegen als drei Flächen übereinander, eingefärbt und
  gerundet über `setBackgroundTintList` und den Umriss (`setViewOutlinePreferredRadius`). Ein Wechsel des Stils
  zeichnet vorhandene Widgets neu (`alleAktualisieren` aus der `MainActivity`).
- Tests `WidgetStilTest` (Milchglas wie bisher, Anime mit Wort, Schatten, Farbe und Rundung, Ruhig, Wörter und Akzent,
  Neuzeichnen nach dem Wechsel).
- Die übrigen Widgets der Entwürfe folgen mit eigenem Plan, siehe unten.

**2026-11-13 – Widgets mit Daten** (Branches `claude/widgets-einkauf`, `claude/widgets-heute`, `claude/widgets-woche`;
Spec `docs/superpowers/specs/2026-11-13-widgets-design.md`, Plan `docs/superpowers/plans/2026-11-13-widgets.md`):
- Neue Widgets „Einkauf“ (2×2), „Schnell eintragen“ (2×2), „Heute“ (4×2), „Kalorien“ (2×2) und „Woche“ (4×1) im
  gewählten Stil wie in den Widget-Entwürfen: Wörter je Stil in Immersiv (etwa „DER HÄNDLER“, „QUEST!“, „Fracht“,
  „ABENDMAHL“, „NÄCHSTE MISSION“), Ring bei Edo, Bayrisch und Grauen, Tank bei Kreuzer, Balken sonst; in der Woche
  Kanji bei Edo, Boxen bei Anime, Wimpel bei Bayrisch, Rauten bei Sage und Kreuzer, leuchtender Kreis bei Grauen.
- Daten aus den Repositories (`WidgetDatenQuelle`), neu gezeichnet bei jeder Änderung (`WidgetBeobachter`), beim
  Stilwechsel und alle 30 Minuten. Links `foody://eintragen` und `foody://woche` neu.
- Tests `WidgetStilTest`, `DatenWidgetTest`, `HeuteWidgetTest`, `WocheWidgetTest` (Texte, Beschreibungen für
  TalkBack, Links, Ansicht je Stil, Bitmaps), Manifest in `WidgetTest`, Links in `DeepLinkTest`.
- Nicht prüfbar ohne Gerät: das Abrunden über den Umriss im Launcher (Robolectric zeichnet ohne) und der
  `WidgetBeobachter` über Hilt.

Offen:
- Schriften beschneiden (Werkzeug mit fonttools, Installation braucht die Freigabe des Nutzers).
- Schriften der übrigen Entwürfe (Dela Gothic One, M PLUS Rounded 1c, Kaisei Decol, Zen Kaku Gothic
  New, UnifrakturCook, Alegreya, Alegreya Sans, IM Fell English, Special Elite, Exo 2, Share Tech Mono). Bis dahin
  Systemschriften der passenden Art (Serife, Grotesk, Monospace). Download braucht die Freigabe des Nutzers.
- Szenen und Wörter von Immersiv (Phase 4 bis 6), ADR 0020, Abschnitt in `docs/architecture.md`.
