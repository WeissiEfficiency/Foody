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
- Erledigt: Wörter R–V (Navigation, Titel, Knöpfe), U „Das Auge“ (Heute). Weiter mit den Heute-Szenen von V, T, R, S.

### Phase 7 – Widgets und Abschluss
- [ ] Die bestehenden Knopf-Widgets (Scannen, Foto) bekommen Farbe und Form des gewählten Stils. Es sind
      `RemoteViews`, Szenen gehen dort nicht.
- [ ] Die neuen Widgets aus den Entwürfen (Heute, Kalorien, Einkauf, Woche) sind eigene Funktionen und bekommen einen
      eigenen Plan.
- [ ] Doku: `docs/architecture.md`, Tabelle in `docs/design/`, „Ergebnis“ hier.

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

Offen:
- Schriften beschneiden (Werkzeug mit fonttools, Installation braucht die Freigabe des Nutzers).
- Schriften der übrigen Entwürfe (Dela Gothic One, M PLUS Rounded 1c, Kaisei Decol, Zen Kaku Gothic
  New, UnifrakturCook, Alegreya, Alegreya Sans, IM Fell English, Special Elite, Exo 2, Share Tech Mono). Bis dahin
  Systemschriften der passenden Art (Serife, Grotesk, Monospace). Download braucht die Freigabe des Nutzers.
- Szenen und Wörter von Immersiv (Phase 4 bis 6), Widgets (Phase 7), ADR 0020, Abschnitt in `docs/architecture.md`.
