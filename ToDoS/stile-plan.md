# Foody – wählbare Stile Q bis V

Stand: 2026-11-16, alle Phasen umgesetzt und in `main` (PRs #216–#249, Review-Funde #250–#268). Offen sind nur die
Abnahme auf dem Handy und Tablet, siehe „Offen“ unten. Grundlage sind die Entwürfe im Design-Canvas
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
  Schreibmaschine nur für Titel und kurze Zeilen, Fließtext in der lesbaren Schrift des Stils. Ziele mindestens 48 dp.
  Große Schrift (200 %) prüfen wir nicht mehr (Nutzer, 2026-11-15); seit 2026-11-24 sind auch die Tests dazu gelöscht.
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
- [x] Mit dem Nutzer die Entwürfe im Canvas durchsehen. Stattdessen gilt seit 2026-11-13: Die Entwürfe sind der
      Soll-Zustand. Der Abgleich der fertigen App mit dem Canvas ist erledigt (2026-11-17, siehe „Offen“).
- [x] Rahmen oben: so umgesetzt (fester Grund je Stil, Wörter in Immersiv, Tablet mit zweispaltigem Layout).
- [x] Pilot festgelegt (Nutzer, 2026-11-11): **Q · Düstere Sage**. Dunkel, lateinische Schriften (Cinzel,
      EB Garamond), kantige Formen.
- [x] Welche Stile: alle Q bis V gleich, nicht nur der Pilot (Nutzer, 2026-11-11).
- [x] ADR 0020 „Wählbare Stile“ (erweitert ADR 0015, Milchglas bleibt Standard) und Spec in
      `docs/superpowers/specs/`.

### Phase 1 – Grundgerüst, ohne sichtbare Änderung
- [x] `FoodyStil`, `StilVariante` und `StilWerte`. Milchglas wird zu einem Stil mit den heutigen Werten.
- [x] `FoodyFormen` und Typografie über das Theme, Speicher, Weitergabe in `MainActivity`.
- [x] Einstellungen: Auswahl „Stil“. Alle Stile waren gleich wählbar, nicht erst nur Milchglas.
- [x] Beweis: Die Milchglas-Bilder bleiben gleich, außer den Einstellungen (Stilwahl dazu); `./gradlew check` ist grün.

### Phase 2 – Komponenten stilfähig, Pilot Ruhig
- [x] Die Komponenten aus „Aufbau“ Punkt 4 lesen die Stilwerte. Feste Ecken in den Features werden umgestellt.
- [x] Schriften des Pilots einbinden (Cinzel, EB Garamond).
- [x] Q in **Ruhig** auf allen Screens. `KontrastTest` und Roborazzi bekommen den Pilot dazu.
- [x] Auf dem Debug-Build am Handy und Tablet ansehen, 2026-11-17 (siehe „Offen“).

### Phase 3 – Ruhig für die übrigen Stile
- [x] Werkzeug zum Beschneiden der Schriften, danach die Schriften für R, S, T, U, V (APK-Größe vorher und nachher
      messen), siehe „Ergebnis“ 2026-11-13.
- [x] `StilWerte` Ruhig für R, S, T, U, V. Danach sind alle Stile wählbar, Immersiv zeigt bis Phase 6 noch Ruhig.
- Danach kann man schon alle Stile im Alltag benutzen. Ab hier könnte man aufhören, falls Immersiv zu viel ist.

### Phase 4 – Immersiv Pilot, Hauptscreens
- [x] Zustand und Anzeige trennen: war nicht nötig, die Szenen hängen sich in die bestehenden Routen ein.
- [x] Die Szenen-Plätze (`StilWerte.immersiv`) und `StilTexte`/`stilText` einführen.
- [x] Q Immersiv für Heute (Rast am Feuer), Rezepte (Grimoire), Woche (Karte der Woche) und Einkauf (Händler), 2026-11-11.
      Flamme und Glut ruhen, wenn Animationen aus sind.
- [x] Leistung: Listen der Stile blättern in Seiten zu sechs, Animationen zeichnen nur den bewegten Teil (#258).
      Baseline-Profil mit allen Stilen neu erzeugt, siehe „Ergebnis“ 2026-11-16.

### Phase 5 – Immersiv Pilot, restliche Screens
- [x] Eintragen, Tagebuch, Rezept, Editor, Kochen, Planen, Listen, Einstellungen, Zutaten. Rezept, Kochen, Tagebuch,
      Editor und Einstellungen als Szene; die übrigen über Bausteine und Wörter.
- [x] Abnahme auf dem Handy. Der Nutzer wollte alle Stile ohne Abnahme des Pilots (2026-11-11); die Abnahme galt
      dann für alle Stile, erledigt 2026-11-17 (siehe „Offen“).

### Phase 6 – Immersiv für die übrigen Stile
- [x] Ein Stil nach dem anderen, je Stil zwei bis drei PRs (Hauptscreens, dann Rest). Umgesetzt je Bildschirm für
      alle fünf Stile R bis V zugleich.
- [x] Mundart (T) und die Texte der Szenen einmal vom Nutzer gegenlesen lassen, siehe „Ergebnis“ 2026-11-15.
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

### Offen (Stand 2026-11-18)
- [x] Silbentrennung in den Szenen (Gerätetest 2026-11-16, siehe „Ergebnis“): behoben mit `mitSilbentrennung()`.
- [x] Rezeptliste der Szenen: Nach dem Antippen rollt die Karte mit „Öffnen“ über die schwebende Navigation.
- [x] Händler (Sage): Unterzeilen der Posten zweizeilig statt abgeschnitten.
- [x] Abnahme auf dem Handy, Rest: Kochen, Editor, Eintragen, Planen, Listen und Zutaten in allen Stilen, Ruhig für
      alle Stile (2026-11-17, siehe „Ergebnis“).
- [x] Editor der Stile R–V: „Verwerfen“ und „Speichern“ über der schwebenden Navigation statt darunter.
- [x] Heute-Szenen: Ende über der Leiste auch bei Drei-Tasten-Navigation.
- [x] Edo: kurze Wörter brechen nicht mehr ohne Not um („Al-le“, „Vari-ante“).
- [x] Ein genauer Abgleich Bild für Bild mit dem Canvas, 2026-11-17, siehe „Ergebnis“.
- [x] Abweichungen vom Canvas, die der Nutzer entscheidet (Liste im „Ergebnis“ Canvas-Abgleich): Ruhig als
      schlichte Szenen statt normaler Ansicht, Eintragen/Planen/Listen als Seiten mit Reitern statt Blättern
      (Nutzer 2026-11-17: alle angehen). Alle erledigt bis 2026-11-18, siehe die Punkte darunter.
- [x] Reihenfolge in den Einstellungen wie im Entwurf (Abgleich unten), 2026-11-17, siehe „Ergebnis“.
- [x] Planen als Seite mit Reitern „Selbst wählen · Vorschlagen lassen“ (Q–V), 2026-11-17, siehe „Ergebnis“.
- [x] Listen als Seite mit Reitern „Neue Liste · Abgleich · Frühere“ (Q–V), 2026-11-17, siehe „Ergebnis“.
- [x] Eintragen mit Reitern „Suchen · Scannen · Foto · Geplant“ (Q–V; T–V als Seite), 2026-11-17, siehe „Ergebnis“.
- [x] Ruhig als schlichte Szenen wie in den Entwürfen, Stil für Stil (`RUHIG_ALS_SZENE`): Sage, Anime und
      Edo 2026-11-17, Bayrisch, Grauen und Kreuzer 2026-11-18, siehe „Ergebnis“.
- [x] Rezeptfilter wie im Entwurf: „Alle · Favoriten · ≤ 30 Min.“ mit den Wörtern des Stils, 2026-11-17, siehe „Ergebnis“.
- [x] Editor der Sage wie im Entwurf (Reiter „Grundlagen · Zutaten · Ritual“), 2026-11-17, siehe „Ergebnis“.
- [x] Tagebuch: „‹“ im Kopf zurück zu Heute in allen Stilen (Immersiv und Ruhig), 2026-11-17, siehe „Ergebnis“.
- [x] Tablet ansehen (zweispaltiges Layout mit Farben, Schrift und Formen des Stils), 2026-11-17, siehe „Ergebnis“.
- [x] Tablet: Rezeptliste der Szenen R–V öffnet das Rezept rechts; Knopfleisten von Editor und Rezept unten am Rand.
- [x] Widgets auf einem zweiten Launcher (Pixel-Launcher im Emulator), 2026-11-17, siehe „Ergebnis“.
- Bewusst nicht gemacht (Funktion fehlt in der App): „In die Woche“ auf Rezeptkarte und Rezept, Uhrzeiten in Heute und
  Tagebuch, die Reiter „Selbst · Link · Foto · Text“ im Editor, Timer-Bogen (V), „Jetzt abgleichen“/„Trennen“ auf der
  Karte in den Einstellungen.
- Aus dem Review offen: benannte Schriftskala je Stil (N2, Aufwand L), sechs Lint-Hinweise.

### Einheitlicher Rahmen (Alltagstest 3.9.0, 2026-11-20)
- [x] Teil A: Zahnrad, Stilkacheln, Anime-Leiste, Einkauf-Listen, Menüs und Abteilungen (Spec
      `docs/superpowers/specs/2026-11-20-einheitlicher-rahmen-design.md`), 2026-11-21, siehe „Ergebnis“.
- [x] Teil B: Eintragen neu – gleich und „nativ“ in allen Stilen, Mahlzeit wählen, Foto ohne Umweg, Widgets direkt
      zum Ziel statt erst ins Eintragen-Blatt (Spec `docs/superpowers/specs/2026-11-21-eintragen-neu-design.md`,
      ADR 0021), 2026-11-22, siehe „Ergebnis“.

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
- Mundart: „Wochn“, „plana“, „heid“, „Sunda“, „no frei“, „Noch nix gplant“, „+ Essn plana“, „Freie Tag fülln“ (angeglichen 2026-11-15).

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
  `WidgetBeobachter` über Hilt. Am 2026-11-15 auf dem Gerät nachgeholt, siehe „Ergebnis“.

**2026-11-13 – ADR 0020 und Doku:** `docs/adr/0020-waehlbare-stile.md`, Abschnitt `stil/` unter „Designsystem“ in
`docs/architecture.md`.

**2026-11-13 – Schriften der Entwürfe** (Branch `claude/stile-schriften`, Download mit Freigabe des Nutzers):
- `werkzeuge/schriften_beschneiden.py` (fonttools): Latein mit Latin-1 und Latin Extended-A, die übliche Typografie und
  alle Zeichen aus dem Code der App (済 月 籠 …); variable Schriften nur im genutzten Bereich von `wght`; Standard-Features,
  ohne Hinting. Schriften, die in ihren Namenseinträgen einen „Reserved Font Name“ nennen (IM Fell English,
  UnifrakturCook, Share Tech Mono), kopiert es unverändert.
- Neu in `res/font`: Dela Gothic One, M PLUS Rounded 1c (R), Kaisei Decol, Zen Kaku Gothic New (S), UnifrakturCook,
  Alegreya, Alegreya Sans (T), IM Fell English mit Kursiv und SC, Special Elite (U), Exo 2, Share Tech Mono (V);
  Cinzel und EB Garamond jetzt beschnitten. Zusammen 1,78 MB statt 0,98 MB, also 0,8 MB mehr (Budget 2 MB).
  Lizenzen in `core/designsystem/lizenzen/`.
- Familien in `token/FoodyTypo.kt`; `StilWerte` (Ruhig und Immersiv) und die Bausteine je Stil nutzen sie. Schriften mit
  nur einem Schnitt sind für alle Gewichte eingetragen, damit Android sie nicht künstlich fett rechnet.
- Bayrisch: Seitentitel in Immersiv in Fraktur (`Bayrisch.fraktur`) wie im Entwurf („Heid“, „’s Standl“, „Eistellunga“),
  sonst Alegreya und Alegreya Sans.
- Referenzbilder der Stile neu aufgenommen; Milchglas unverändert. Die Widgets bleiben bei Systemschriften.

**2026-11-15 – Mundart durchgesehen** (Branch `claude/stile-mundart`):
- Alle bayrischen Texte als Liste je Datei dem Nutzer vorgelegt und mit den Entwürfen abgeglichen. Was im Entwurf so
  steht, bleibt, auch wo es uneinheitlich wirkt („Zuadaten“ im Rezept, „Zuadatn“ im Zutatenkatalog; „Löschen“,
  „Nährwerte“ auf Hochdeutsch).
- Angeglichen, wo der Entwurf nichts vorgibt: „+ Essn plana“ (wie „plana ›“), „Freie Tag fülln“ (wie „die freien Tag“),
  „Eikaufszettl · no 3 offn ›“ (wie „no 3 offn“ im Einkauf).
- TalkBack-Texte bleiben Hochdeutsch.

**2026-11-15 – Widgets auf dem Gerät** (Branch `claude/widgets-geraet`, Debug-Variante per WLAN-Debugging auf dem
Handy des Nutzers, ColorOS-Launcher, 2×2-Zelle etwa 123 dp breit):
- Gut: Umriss, Glühen und Versatzschatten werden vom Launcher nicht abgeschnitten; ein Wechsel des Stils und neue Daten
  erreichen alle Widgets von selbst (`WidgetBeobachter`).
- Fehler 1: Nach einem Wechsel des Stils blieben Teile des vorigen stehen (Stempel 籠 aus Edo, Rautenband aus Bayrisch,
  gelbe Knöpfe aus Anime), nach dem Abhaken auch die alte Zeile im Einkauf. Ursache: Bei gleichem Layout wendet der
  Launcher die `RemoteViews` nur auf die alten Views an. Behoben mit `frischZeigen` (Hülle `widget_huelle`, Inhalt mit
  `removeAllViews` und `addView` jedes Mal neu). Die Tests sahen das nicht, weil sie jede Ansicht frisch aufbauen.
- Fehler 2: Der Ring im Widget „Kalorien“ (fest 110 dp) war seitlich abgeschnitten; jetzt füllt er die Fläche quadratisch.
- Fehler 3: Abgeschnittene Texte in engen Zellen („DER HÄ…“, „QU…“, „EINTRA…“, „Suchen · Scann…“, „Das Ritual des
  Ab…“). Titel und Fußzeile schrumpfen jetzt (`autoSizeTextType`), die Marke im Widget „Heute“ darf zwei Zeilen haben,
  die Fußzeile von „Schnell eintragen“ hat schmale Leerzeichen um die Punkte.
- Im Widget-Auswahlmenü von ColorOS zeigt jedes Foody-Widget nur das App-Symbol als Vorschau (kein `previewLayout`).
  Behoben am selben Tag, siehe unten.

**2026-11-15 – Vorschau in der Widget-Auswahl** (Branch `claude/widget-vorschau`):
- Die fünf Widgets mit Daten haben jetzt ein `android:previewImage` statt des leeren `previewLayout`.
- Die Bilder kommen aus dem neuen Bildtest `WidgetBildTest` in `:app` (Roborazzi, Milchglas mit Beispieldaten, volle
  Auflösung, Ecken gerundet). Erste Referenzbilder für Widgets überhaupt; `vorschauGleichReferenz` hält Vorschau und
  Referenz gleich.

**2026-11-16 – Gerätetest der Stile und Baseline-Profil** (Debug-Variante per WLAN-Debugging auf dem Handy des Nutzers,
ColorOS, 1264 × 2780 px; erfundene Testdaten aus einer Sicherung: 8 Rezepte, Wochenplan mit leeren Tagen, Einkauf mit
abgehakten Posten, Tagebuch mit einem Eintrag ohne vollständige Werte, Tagesziel 2.000 kcal):
- Geprüft: in allen sechs Stilen (Immersiv) Heute (oben und unten), Tagebuch, Rezepte, Rezept, Woche, Einkauf und
  Einstellungen, dazu Kreuzer in Ruhig. Kein Absturz, Daten und Rechnung stimmen in allen Stilen: „≥ 1.268“ bei
  unvollständigem Eintrag, „≤ 732 frei“ für den Rest, „≥ 63 % ERREICHT“ (Kreuzer).
- Fehler: In den Szenen fehlt die deutsche Silbentrennung. `Anime.titel/text`, `Kreuzer.titel/mono` usw. bauen einen
  eigenen `TextStyle` ohne `localeList = de-DE` und `hyphens = Hyphens.Auto` (anders als `FoodyTypo.kt`). Folge in
  schmalen Kacheln: „Kokosmilc|h“, „Ofengem|üse“ (Woche Anime), „Hähnchen-Cur|ry“ (Zettel auf Heute, Anime),
  „Rote-Linsen-S|uppe“ (Woche Edo), „Hähnchenbru|st“ (Frachtraum Kreuzer). Ruhig trennt richtig („Hähnchen-|brust“).
- Auffällig: In den Rezeptlisten von Edo, Bayrisch, Grauen und Kreuzer liegt die Karte des gewählten Rezepts mit dem
  Öffnen-Knopf beim ersten Blick unter der schwebenden Navigation. Im Händler (Sage) sind die Unterzeilen der Posten
  abgeschnitten („500 G · OBST & GEM…“). Unter der schwebenden Navigation scheint Inhalt bis in die Systemleiste durch.
- Hinweis: Solange Auge (Grauen) oder Sternensystem (Kreuzer) laufen, liefert UiAutomator keinen UI-Baum, weil die
  Oberfläche nie zur Ruhe kommt. Ob das Bedienhilfen wie TalkBack stört, ist nicht geprüft.
- Baseline-Profil neu erzeugt (Emulator): Der Generator geht neben dem Weg in Milchglas (`generate`, auch
  Startprofil) einmal durch jeden Stil (`stile`). Vorher stand kein Code der Stile im Profil; jetzt 33.377 statt
  26.217 Zeilen, Einträge aus Foody 5.124 statt 2.610. Start und Ruckeln sind nicht gemessen.
- Behoben am selben Tag: `TextStyle.mitSilbentrennung()` in `token/FoodyTypo.kt` für alle Schriften der Stile (Test
  `StilSchriftTest`); in der Rezeptliste rollt die Karte des gewählten Rezepts samt Platz für die Leiste ins Bild
  (`RezepteStileTest`, `…KarteInsBild` auf 360 × 640 dp); im Händler zweizeilige Unterzeilen bei gleich hohen Feldern.
  Auf dem Handy nachgeprüft (Edo, Kreuzer, Sage); sechs Referenzbilder der Stile neu, Milchglas unverändert.

**2026-11-17 – Gerätetest, zweiter Teil** (gleiches Handy und gleiche Testdaten; das Handy nutzt die
Drei-Tasten-Navigation, die Systemleiste ist 48 dp hoch):
- Geprüft in allen sechs Stilen (Immersiv): Planen („Leere Tage füllen“ und „Mahlzeit wählen“), frühere Listen,
  Kochmodus (Bereitlegen und ein Schritt), „Rezept hinzufügen“ und Editor (oben und unten), Eintragen von Heute aus,
  Zutatenkatalog und Zutat. Kein Absturz, die Wörter der Stile stimmen.
- Fehler: Im Editor der Stile R–V lag die feste Leiste „Verwerfen | Speichern“ genau unter der schwebenden Navigation;
  in Edo, Bayrisch, Grauen und Kreuzer scheint sie durch, in Anime deckt die Leiste sie ganz zu. „Speichern“ war nur
  über den Kopf (Edo „新“) oder gar nicht zu erreichen. Die Leiste rechnete nur die Systemleiste ein.
- Fehler: Der Rahmen der Heute-Szenen (`HeuteRahmen`) ließ unten Platz für die schwebende Leiste, aber nicht für die
  Systemleiste darunter. Bei Drei-Tasten-Navigation lag das Ende 48 dp unter der Leiste: in der Sage „Rezept ansehen“
  und „+ Scannen · Foto“, in Edo „＋ Eintragen“ (in der Systemleiste, ein Tipp traf „Zurück“), in Grauen die Zeile
  „Abend · Gegessen?“. Die anderen Bildschirme liegen in `safeDrawingPadding` und waren richtig.
- Ruhig geprüft für Sage, Anime, Edo, Bayrisch und Grauen (Heute, Rezepte, Rezept, Woche, Einkauf, Einstellungen):
  Farben und Schriften des Stils, sonst die normale Ansicht; kein Absturz.
- Fehler (Edo, Ruhig und Immersiv): Kurze Wörter in Zen Kaku brachen um, obwohl sie passten: „Al-le“ im gewählten
  Filter, „Vari-ante“ in den Einstellungen. Ursache ist die Schrift zusammen mit Silbentrennung und dem Umbruch für
  Absätze (`LineBreak.Paragraph`); ohne Trennung oder mit dem einfachen Umbruch bleibt das Wort einzeilig. Alle
  anderen Schriften sind davon frei (geprüft für alle Stile).
- Klein, nicht geändert: Bei leerem Vorschlag bricht „Übernehmen (0)“ (Anime) bzw. „0 Tage bestücken“ (Sage) in zwei
  Zeilen um; der Knopf ist dann ohnehin gesperrt.
- Behoben: `HeuteRahmen` mit `WindowInsets.navigationBars` unten (`HeuteRahmenTest` mit 48 dp Systemleiste, vorher
  104 dp statt 152 dp Abstand); der Editor der Stile hebt seine Leiste um `ueberLeiste(LEISTE_REGELHOEHE)` über die
  Navigation, bei offener Tastatur über diese (`EditorStileTest`, `…UeberNavigation` für R–V, vorher alle fünf rot);
  Zen Kaku bricht mit `LineBreak.Simple` um (`UMBRUCH_ZEN_KAKU` in `FoodyTypo.kt`, im Theme und in `Edo.text`), die
  Silbentrennung bleibt (`KurzeWoerterTest` für alle Stile, vorher Edo und Edo Immersiv rot). Acht Referenzbilder neu
  (Editor R–V, Edo Ruhig, Edo Zutaten; die alten Edo-Bilder enthielten „Fe-ta“ und „Zu-cchini“), Milchglas unverändert.
  Auf dem Handy nachgeprüft: Edo Ruhig (Filter, Einstellungen), Edo Immersiv (Ende von Heute, Editor).

**2026-11-17 – Gerätetest Tablet** (Lenovo TB336FU per WLAN-Debugging, quer 2560 × 1600 px, etwa 1170 × 731 dp,
Gestennavigation; gleiche Testdaten wie am Handy; Branch `claude/tablet-abnahme`):
- Geprüft in allen sechs Stilen, Immersiv und Ruhig: Heute, Tagebuch, Rezepte mit Rezept, Woche, Einkauf,
  Einstellungen; in Immersiv dazu Planen, frühere Listen, Kochmodus, Editor, Eintragen und Zutaten. Navigationsschiene
  links, zweispaltig ab 840 dp. Die Szenen stehen in Inhaltsbreite mittig, Sheets und Dialoge passen; kein Absturz.
  Ruhig ist die normale zweispaltige Ansicht in Farben und Schrift des Stils.
- Fehler: In den Rezeptlisten der Szenen R–V wählte ein Tipp nur das Rezept in der Szene; rechts blieb „Wähle ein
  Rezept“, erst „Öffnen“ zeigte es. Die Sage (Q) öffnete schon direkt.
- Fehler: Die festen Knopfleisten von Editor und Rezept hielten unten 88 dp für die schwebende Navigation frei, die es
  auf dem Tablet nicht gibt (dort ist die Schiene); die Leiste stand mitten über dem Rand.
- Behoben: `LocalMitLeiste` in `FoodyNavigation.kt` (das Hauptgerüst setzt es, ohne Leiste `false`) und
  `platzFuerLeiste()` für Editor (`EditorStile.kt`) und Rezept (`RezeptStile.kt`, `RezeptSage.kt`)
  (`EditorStileTest.ohneLeisteUntenAmRand`); die Rezeptliste öffnet neben dem Detail beim Wählen
  (`nebenDetail`, `RezepteBereichTest` mit 1280 × 800 dp für Edo, Bayrisch, Grauen und Kreuzer, vorher alle fünf
  Tests rot). Auf dem Tablet nachgeprüft (Edo: Rezept rechts, Leisten unten).
- Hinweis: Der halbe Kreis mit Stift am rechten Rand ist ein Fenster des Systems (Lenovo, `FloatingCollapseView`),
  nicht von Foody.

**2026-11-17 – Abgleich mit dem Canvas, Widgets im Pixel-Launcher** (Branch `claude/canvas-abgleich`):
- Vorgehen: Die 85 Entwürfe (Q–V je 14 Screens, dazu T-Maibaum) lokal gerendert und je Screen neben ein Bild der App
  gelegt. Die App-Bilder stammen aus dem Emulator mit den Maßen des Handys (OnePlus 13R: 1264 × 2780 px, Dichte 560,
  also 361 × 794 dp, Drei-Tasten-Navigation, Schrift 0,9, gleiche Testdaten); die Entwürfe sind 390 dp breit.
  Alle sechs Stile in Immersiv und Ruhig, alle 14 Screens.
- Hauptscreens in Immersiv (Heute, Tagebuch, Rezepte, Rezept, Woche, Einkauf, Kochen) entsprechen den Entwürfen in
  Aufbau, Szene, Farben und Wörtern. Bekannte, bewusste Lücken wie bisher: „In die Woche“, Uhrzeiten, Timer-Bogen.
- Behoben: Im Tagebuch nannten Grauen und Kreuzer die Mahlzeiten „Frühstück“ … statt wie im Entwurf „Bei
  Tagesanbruch“, „Zur Mittagsstunde“, „Nach Einbruch der Nacht“, „Zwischendurch“ bzw. „ORBIT 1“ bis „ORBIT 4“ (gleiche
  Bahnen wie auf der Brücke); im Kreuzer stehen die Gruppen nach Bahn (sonst „ORBIT 4“ vor „ORBIT 3“). TalkBack liest
  weiter die normalen Namen (`mahlzeitGrauen`, `mahlzeitKreuzer`, `gruppenFolge` in `TagebuchStile.kt`,
  `TagebuchStileTest.texte`, vorher rot); vier Referenzbilder neu (Tagebuch und Eintragen, Grauen und Kreuzer).
- Widgets im Pixel-Launcher (zweiter Launcher nach ColorOS): alle sieben Widgets lassen sich hinzufügen, zeigen Daten
  und folgen dem Stilwechsel in allen Stilen. Fehler: „Woche“ kam mit 80 dp Mindesthöhe als 4 × 2 und ließ sich nicht
  auf eine Zeile ziehen. Behoben mit 40 dp (`woche_widget_info.xml`); `WidgetGroesseTest` prüft für alle Widgets, dass
  die Mindestgröße in die Zielzellen passt (70 × n − 30 dp), vorher rot. Im Emulator nachgeprüft: 4 × 1.
- Abweichungen, offen zur Entscheidung (nicht geändert):
  - **Ruhig:** Die Entwürfe zeigen in Ruhig die Szenen-Layouts ohne Bilder (Menü in Heute, eine Rezeptkarte mit
    Blättern, Tageschips in der Woche, Posten als Felder im Einkauf). Die App zeigt in Ruhig wie geplant die normale
    Ansicht in Farben und Schrift des Stils (oben festgelegt: „schlichtes Layout“).
  - **Eintragen, Planen, Listen:** In den Entwürfen eigene Seiten mit Reitern (Eintragen „Suchen · Scannen · Foto ·
    Geplant“, Planen „Rezept wählen · Tage füllen“, Listen „Neue Liste · Abgleich · Frühere“). Die App nutzt ihre
    Blätter mit den Wörtern des Stils (wie im Plan: „Bausteine und Wörter“).
  - **Tagebuch:** Die Entwürfe haben im Kopf ein „‹“ zurück zu Heute (am selben Tag nachgezogen, siehe unten). Die
    Entwürfe ordnen Frühstück, Mittag, Abend, Snack; die App Frühstück, Mittag, Snack, Abend.
  - **Rezeptfilter:** Die Entwürfe haben drei Filter mit Wörtern je Stil (Sage „♦“ und „≤ 30′“, Bayrisch „Olle“,
    „♥ Leibgerichte“, „G’schwind“); am selben Tag nachgezogen, siehe unten.
  - **Editor der Sage:** Entwurf „Neu niederschreiben“ mit Reitern „Grundlagen · Zutaten · Ritual“ (am selben Tag
    nachgezogen, siehe unten).
  - **Einstellungen:** In den Entwürfen steht der Abgleich unten, in der App oben (dazu die Stilwahl, die die
    Entwürfe nicht zeigen).
  - Kleinigkeiten: Edo-Rezeptliste als Fächer aus Buchstabenkarten statt beschrifteter Fächerblätter; Grauen-Widget
    „Heute“ mit Ring statt Auge.
- Hinweis zum Werkzeug: Die Entwürfe ließen sich mit einer kleinen eigenen Laufzeit für `x-dc` (Platzhalter,
  `sc-if`, `sc-for`, `renderVals`) im Browser rendern; sie liegt nicht im Repo.

**2026-11-17 – Tagebuch mit Zurück** (Branch `claude/tagebuch-zurueck`, Entscheidung des Nutzers aus dem Canvas-Abgleich):
- Wie in den Entwürfen steht im Kopf des Tagebuchs links ein „‹“, das zurück zu Heute führt: in der Sage neben
  „Chronik“ (`Pfeil`), in R–V als `StilAktion` vor Überzeile und Titel, in Ruhig der runde Zurück-Knopf des
  `Seitenkopf`. Milchglas bleibt ohne (kein Entwurf). `TagebuchTagRoute(onZurueck)`, im NavHost `popBackStack()`.
- Tests: `TagebuchStileTest` (`…Zurueck` für alle sechs Stile und Ruhig, `milchglasOhneZurueck`); ohne den Knopf
  sind alle sieben rot. Sechs Referenzbilder neu (Tagebuch der Sage und R–V), Milchglas unverändert. Im Emulator mit
  den Maßen des Handys nachgeprüft (Edo, Sage: „‹“ führt zu Heute).

**2026-11-17 – Editor der Sage** (Branch `claude/sage-editor`, Entscheidung des Nutzers aus dem Canvas-Abgleich):
- Die Sage hat im Editor jetzt eine eigene Szene wie im Entwurf (`EditorSage.kt`, aus `EditorRoute` in Sage Immersiv):
  Kopf mit ✕ und Titel in Gold („Neue Seite“, beim Bearbeiten „Neu niederschreiben“; TalkBack „Neues Rezept“ bzw.
  „Bearbeiten“), Reiter „Grundlagen · Zutaten · Ritual“ mit goldenem Strich unter dem gewählten, unten „Ins Grimoire
  schreiben“ über der schwebenden Navigation und über der Tastatur.
- Grundlagen: Bild (eckig), Name, Portionen, Vorbereitung und Kochzeit, Tags, Einordnung. Zutaten: Feld und darunter
  „Was die Schrift verrät“ mit ✓ (erkannt) oder ! (Fehler). Ritual: Schritte, Notizen, Quelle, Hinweis auf die
  Sanduhren im Kochmodus. Felder mit Etikett in Cinzel darüber: `StilFeld` kennt dafür jetzt auch die Sage.
- Bewusst anders als im Entwurf: kein „Abschrift“-Import im Kopf (wie in R–V), Vorbereitung und Kochzeit getrennt statt
  eines Felds „Minuten“, „Ein Schritt je Zeile“ statt „je Absatz“ (so liest die App die Schritte).
- Tests: `EditorStileTest.sage`, `sageVerwerfen`, `sageUeberNavigation`, vorher rot. Referenzbild `sage_editor_handy`
  neu. Die Reiter liest TalkBack nur einmal mit dem normalen Namen („Zutaten“, „Zubereitung“).
- Im Emulator mit den Maßen des Handys nachgeprüft: alle drei Reiter, „Ins Grimoire schreiben“ über der Leiste und
  direkt über der Tastatur, ✓ und ! unter „Was die Schrift verrät“, ✕ fragt nach dem Verwerfen.

**2026-11-17 – Rezeptfilter je Stil** (Branch `claude/rezeptfilter-stil`, Entscheidung des Nutzers aus dem Canvas-Abgleich):
- Neuer Filter „≤ 30 Min.“ (`ListeFilter.schnell`): Vorbereitung und Kochzeit zusammen höchstens 30 Minuten, Rezepte
  ohne Zeitangabe fallen heraus. Er steht in allen Stilen als dritter Chip nach „Alle“ und „Favoriten“, wie in den
  Entwürfen (auch in Milchglas, damit der Filter überall gleich funktioniert).
- Die ersten drei Chips tragen in Immersiv die Wörter des Entwurfs: Sage „Alle · ♦ · ≤ 30′“, Anime „Schnell“,
  Bayrisch „Olle · ♥ Leibgerichte · G’schwind“, Grauen „♦ Favoriten“, Kreuzer „ALLE · ★ FAVORITEN · ≤ 30 MIN.“, Edo
  wie normal. TalkBack hört immer „Alle“, „Favoriten“, „Höchstens 30 Minuten“. Ruhig bleibt bei den normalen Wörtern.
- Bewusst nicht wie im Entwurf: Die übrigen Filter der App (Oft gekocht, Ernährung, Mahlzeit, Tags, Archiv,
  Sortierung) bleiben dahinter in der Reihe; die Entwürfe zeigen nur die drei.
- Tests: `ListeFilterTest.schnell`, `RezepteStileTest.…Filter` für alle sechs Stile (Wörter, Beschreibung, Wirkung).
  Zehn Referenzbilder der Rezeptliste neu (alle Stile, Milchglas hell/dunkel, Handy/Tablet).
- Im Emulator mit den Maßen des Handys nachgeprüft (Bayrisch): „Olle · ♥ Leibgerichte · G’schwind“, „G’schwind“
  lässt von acht Herzen die vier bis 30 Minuten hängen.

**2026-11-17 – Reihenfolge in den Einstellungen** (Branch `claude/einstellungen-reihenfolge`, Entscheidung des Nutzers aus dem Canvas-Abgleich):
- In den Stilen Q–V (Immersiv und Ruhig) steht der Abgleich wie in den Entwürfen nach Darstellung, Tagesziel,
  Zutatenkatalog und Fotoerkennung; danach folgen Sicherung, Löschen und Hinweise, die die Entwürfe nicht zeigen.
  Milchglas behält das Konto oben (kein Entwurf).
- Tests: `EinstellungenStileTest.…AbgleichUnten` für alle sechs Stile und Ruhig (vorher rot), `milchglasAbgleichOben`.
  Sechs Referenzbilder neu (Sage, R–V), Milchglas unverändert.
- Im Emulator mit den Maßen des Handys nachgeprüft (Sage: „Botendienst“ nach der Seherin, vor der Abschrift).

**2026-11-17 – Planen als Seite mit Reitern** (Branch `claude/planen-seite`, Entscheidung des Nutzers aus dem Canvas-Abgleich):
- In den Stilen Q–V (Immersiv und Ruhig) ist Planen wie in den Entwürfen eine eigene Seite statt Mahlzeitwahl,
  Rezeptwahl und Füllen-Blatt (`PlanenSeite.kt`): Kopf mit Zurück und Titel des Stils („Den Weg planen“, „Planen!“,
  „Wochn plana“, „Kurs plotten“ mit Überzeile „Navigation · KW 47“, Grauen „Die Sterne befragen“), Reiter „Selbst
  wählen · Vorschlagen lassen“ (Sage „Mahl wählen · Leere Tage füllen“, Anime „Rezept wählen · Tage füllen“,
  Bayrisch „Selber aussuacha · Vorschlagn lassn“, Grauen „… · Die Sterne fragen“, Kreuzer „Manuell · Navcomputer“).
- „Selbst wählen“: Tag der Woche, Mahlzeit oder eigener Name, Gericht mit Suche; fest unten Portionen (Vorgabe aus
  dem Rezept), die Zusammenfassung „Dienstag, 17.11. · Abendessen · Kartoffelsuppe“ und „Einplanen“ (Sage „Auf die
  Karte setzen“, Anime „In die Woche! ▶“, Bayrisch „Eiplana“, Grauen „Es sei beschlossen“, Kreuzer „Kurs
  bestätigen“). „Vorschlagen lassen“ zeigt den Inhalt von „Leere Tage füllen“ (Mahlzeit, Vorschläge mit ↻, „Neu
  mischen“, „Übernehmen“). „+“ und ein freier Platz öffnen „Selbst wählen“ mit Tag und Mahlzeit, „Leere Tage
  füllen“ den zweiten Reiter. Milchglas behält die Blätter (kein Entwurf).
- Neuer Baustein `Reiterleiste` im Designsystem (Anime und Bayrisch als Pillen, sonst Linie mit Strich im Akzent;
  TalkBack liest das normale Wort und „ausgewählt“), auch für Listen und Eintragen gedacht. `RezeptKarte.portionen`.
- Bewusst anders als im Entwurf: Gerichte als Liste mit Suche statt Kacheln (die App hat mehr Rezepte als sechs),
  kein „Von/Bis“ beim Füllen (die App füllt die leeren Tage der gezeigten Woche ab heute), Kreuzer-Strich im Akzent
  des Stils.
- Tests: `PlanenSeiteTest` (alle sechs Stile und Ruhig planen über „Selbst wählen“, Platz mit Portionen, Zurück,
  Übernehmen, Milchglas bleibt Blatt), `WocheStileTest.…Plus` und `WocheScreenTest.karteDerWoche…` angepasst. Sechs
  neue Referenzbilder `stil_*_planen_handy`, sechs `*_fuellen_handy` neu (jetzt die Seite).
- Im Emulator mit den Maßen des Handys nachgeprüft (Sage, Kreuzer): beide Reiter, Einplanen trägt ein und schließt,
  Portionen unten über dem Knopf, Zurück schließt ohne Eintrag.

**2026-11-17 – Listen als Seite mit Reitern** (Branch `claude/listen-seite`, Entscheidung des Nutzers aus dem Canvas-Abgleich):
- In den Stilen Q–V (Immersiv und Ruhig) sind Vorschau, Abgleich und Frühere Listen wie in den Entwürfen eine eigene
  Seite statt drei Blättern (`ListenSeite.kt`): Kopf mit Zurück und Titel des Stils („Listen des Händlers“,
  „Listen!“, „Eikaufszettl“, Grauen „Listen“ unter „Das Archiv der Expeditionen“, Kreuzer „Ladelisten“ unter
  „Fracht · Dock 7“), Reiter „Neue Liste · Abgleich · Frühere“ (Edo, Bayrisch, Grauen, Kreuzer „Vorschau“). Jeder
  Weg in eines der drei Blätter öffnet die Seite auf diesem Reiter; „Liste anlegen“ und „Übernehmen“ schließen sie.
  Der Inhalt ist der der Blätter (`BlattRahmen` zeichnet ihn auf der Seite ohne Blatt und Titel, Knopf fest unten).
- Ohne Zeitraum (Liste nicht aus dem Plan) sagt der Reiter „Abgleich“, warum es nichts abzugleichen gibt. Milchglas
  behält die Blätter (kein Entwurf). Bayrisch: weiße Schrift auf dem gewählten blauen Reiter (auch Planen).
- Tests: `ListenSeiteTest` (alle sechs Stile und Ruhig wechseln die Reiter und schließen mit Zurück, Liste anlegen,
  ohne Zeitraum, Frühere öffnet eine Liste, Milchglas bleibt Blatt), `EinkaufStileTest.…Frueher` angepasst. Sechs
  Referenzbilder `*_listen_handy` neu (jetzt die Seite), `stil_bayrisch_{fuellen,planen}_handy` neu (weiße Schrift).
- Im Emulator mit den Maßen des Handys nachgeprüft (Kreuzer): alle drei Reiter, Knopf fest unten über der Leiste,
  Zurück schließt die Seite.

**2026-11-17 – Eintragen mit Reitern** (Branch `claude/eintragen-reiter`, Entscheidung des Nutzers aus dem Canvas-Abgleich):
- In den Stilen Q–V (Immersiv und Ruhig) hat Eintragen wie in den Entwürfen die Reiter „Suchen · Scannen · Foto ·
  Geplant“ (Sage „Suchen · Siegel · Deuten · Geplant“; ohne Erkennungsmodell „Frei“ statt „Foto“) statt der Wege
  „Rezept · Zutat · Aus dem Plan · Frei“. Sage, Anime und Edo bleiben ein Blatt wie im Entwurf; Bayrisch, Grauen und
  Kreuzer sind eine eigene Seite mit Zurück (Grauen „Notieren“ unter „Was dargebracht wird“, Kreuzer unter „Proviant
  erfassen“), Portionen oder Menge und Knopf fest unten. Milchglas behält das Blatt mit den Wegen (kein Entwurf).
- „Suchen“ ist eine Suche über Rezepte, Zutaten und den BLS, die Quelle steht unter dem Namen („Rezept“, „Zutat“).
  „Scannen“: Scan-Knopf und die gescannten Produkte, nach dem Scan die Packung. „Foto“: Fotoerkennung und die freien
  Felder. „Geplant“: der offene Plan des Tages, Knopf „Als verzehrt eintragen“ (Sage), „Gegessen!“ (Anime), „Als
  gegessen eintragen“ (Edo). Die Widgets „Scannen“ und „KI-Foto“ öffnen den passenden Reiter.
- Bewusst anders als im Entwurf: kein Kamerabild im Reiter „Scannen“ (der Scan öffnet die Kamera wie bisher), keine
  kcal-Vorschau neben der Menge, Mahlzeiten mit den bisherigen Wörtern (Sage „Morgen · Mittag · Abend · Zwischen“).
- Tests: `EintragenStileTest` (alle sechs Stile und Ruhig suchen und wählen, Kreuzer wechselt Reiter und Zurück, Grauen
  trägt eine Zutat ein, Anime bleibt Blatt), `EintragenViewModelTest` (Reiter setzen den Weg, BLS auch bei Rezept).
  Fünf Referenzbilder `stil_*_eintragen_handy` neu (Reiter, Seiten mit Hintergrund).

**2026-11-17 – Ruhig als schlichte Szene: Sage** (Branch `claude/ruhig-szenen-q-r`, Entscheidung des Nutzers aus dem Canvas-Abgleich):
- Neu im Designsystem: `StilWerte.szene(stil)` (Szene in Immersiv und, für die Stile in `RUHIG_ALS_SZENE`, auch in
  Ruhig), `StilWerte.inszeniert` (Bilder, Schrägen, harte Schatten, Animationen nur in Immersiv) und
  `szenenWort(immersiv, ruhig)` für feste Wörter der Szenen. `szenenStil()` und die Weichen der Screens (Heute,
  Tagebuch, Rezepte, Rezept, Editor, Kochen, Woche) fragen `szene(…)`; Stil für Stil kommt in `RUHIG_ALS_SZENE`.
- Sage in Ruhig zeigt jetzt wie im Entwurf ihre Szenen schlicht: Heute mit dem Kopf „HEUTE“ und Datum statt „Rast
  am Feuer“, das Menü bleibt; Rezepte „SAMMLUNG · REZEPTE“, „Suchen“; Woche „WOCHE“ mit sieben Tageschips statt der
  Karte, „+ REZEPT WÄHLEN“, „TAGE FÜLLEN“; Einkauf „EINKAUF“ ohne den Laden und den Spruch des Händlers; Tagebuch
  „TAGEBUCH“, „FRÜHSTÜCK · MITTAG · ABEND · SNACK“, „heute“, „vor 2 Tagen“, „kein Eintrag“, „LÖSCHEN“; Rezept
  „REZEPT“, Reiter „ZUTATEN · ZUBEREITUNG · NÄHRWERTE“, „KOCHEN“, Menü „Bearbeiten · Duplizieren · Archivieren“,
  „Notiz: …“; Editor „ALLGEMEIN · ZUTATEN · SCHRITTE“, „SPEICHERN“, „VORSCHAU“, „pro Zeile“; Kochen „BEREITLEGEN“,
  „Timer: …“, „LOS GEHT’S“, „FERTIG“. Wo die Szenen schon `stilText` nutzen, kommen die normalen Wörter von selbst.
- Bewusst wie im Entwurf: Die Einträge des Menüs in Heute behalten in Ruhig ihre Wörter („ABENDMAHL BEREITEN“, „AM
  FEUER KOCHEN“), die Entwürfe zeigen sie in beiden Varianten gleich.
- Tests: `HeuteSageTest.ruhigZeigtSzeneSchlicht` (vorher normale Ansicht), `WocheStileTest.sageRuhigTageschips`.
  Referenzbild `stil_sage_ruhig` neu.
- Im Emulator mit den Maßen des Handys nachgeprüft: Heute, Rezepte, Rezept, Woche, Einkauf, Tagebuch in Sage Ruhig.

**2026-11-17 – Ruhig als schlichte Szene: Anime** (Branch `claude/ruhig-szenen-anime`, auf Sage aufgesetzt):
- Anime steht in `RUHIG_ALS_SZENE`. Neu `animeSzenenTitel(groesse)`: der große Titel der Szenen in Immersiv schräg mit
  Schatten im Akzent, in Ruhig gerade ohne Schatten. `stilTitel` gibt für Anime in Ruhig die Titelschrift ohne Schräge
  und Schatten (Seitenköpfe wie Einstellungen, Listen, Planen, Editor; so zeigen es die Entwürfe).
- Anime in Ruhig wie im Entwurf: Heute „Heute“ ohne Foody das Reisbällchen und die Sprechblase; Rezepte „Rezepte“;
  Woche „Woche“, die Panels gerade und ohne harten Schatten, das gewählte mit einem Ring im Akzent, die Tageskarte ohne
  Schatten; Einkauf „Einkauf“ ohne „XP“ und ohne Kartenschatten; Tagebuch „Tagebuch“; Rezept ohne Strahlen und
  Anfangsbuchstabe im Bild; Editor „Rezept bearbeiten“; Kochen „Bereitlegen“. Zettel, Abzeichen und Knöpfe bleiben,
  die Entwürfe zeigen sie in beiden Varianten gleich.
- Tests: `HeuteAnimeTest.ruhigZeigtSzeneSchlicht` (vorher normale Ansicht), `WocheStileTest.animeRuhigSchlicht`.
  Referenzbild `stil_anime_ruhig` neu.
- Im Emulator mit den Maßen des Handys nachgeprüft: Heute, Rezepte, Rezept, Woche, Einkauf, Tagebuch in Anime Ruhig.

**2026-11-17 – Ruhig als schlichte Szene: Edo** (Branch `claude/ruhig-szenen-edo`, auf Anime aufgesetzt):
- Edo steht in `RUHIG_ALS_SZENE` und zeigt in Ruhig wie im Entwurf `S-*`: Heute statt des Rollbilds „Heute“ mit
  Datum über einer Tuschelinie, darunter die nächste Mahlzeit schlicht („ABENDESSEN“, Gericht, Angaben, „KOCHEN“);
  Speisefolge und Stempel bleiben. Rezepte statt des Fächers die Treffer als Knöpfe zu zweit (Kurzname, das gewählte
  im Zinnober), darüber „8 REZEPTE“. Woche statt der Votivtafeln sieben Tageschips (heute im Zinnober, der gewählte in
  Tusche), „Leere Tage vorschlagen lassen“ ohne 絵馬. Einkauf ohne den Tuschering, Rezept ohne den Stempel mit dem
  Anfangsbuchstaben, Editor ohne „新“; Planen nennt die Tage „Mo“ statt „月“ (`planenTag(…, inszeniert)`).
- Tests: `HeuteEdoTest.ruhigZeigtSzeneSchlicht` (vorher normale Ansicht), `WocheStileTest.edoRuhigTageschips`.
  Referenzbild `stil_edo_ruhig` neu. `PlanenSeiteTest.platzMitPortionen` läuft jetzt in Kreuzer Ruhig: freie Plätze
  („Abendessen planen“) zeigt nur die normale Ansicht; wenn Kreuzer zur Szene wird, braucht der Test einen neuen Weg.

**2026-11-18 – Ruhig als schlichte Szene: Bayrisch** (Branch `claude/ruhig-szenen-bayrisch`):
- Bayrisch steht in `RUHIG_ALS_SZENE` und zeigt in Ruhig wie die Entwürfe `T-*`: die großen Titel in Alegreya statt
  Fraktur (`bayrischSzenenTitel`), der Dialekt bleibt („Heid“, „Wochn“, „Kochbiachl“, „Pack ma's!“). Heute behält den
  Maibaum ohne die Landschaft (der Entwurf `T-Heute` hat keine Ruhig-Fassung, also dieselbe Regel wie die übrigen
  T-Seiten). Woche ohne Wimpelkette, den Tag wählt man in der Liste. Einkauf ohne Markise und Holzkisten, Kopfschild
  weiß, „Einkauf“ und „Listen“. Rezepte statt der Lebkuchenherzen weiße Karten zu zweit. Rezept ohne Rautenband, mit
  „Zutaten / Zubereitung“ und „2 Portionen“; Tagebuch „Tagebuch“ und „X kcal übrig“; Editor in Standardwörtern;
  Kochen „Weiter“ und „Fertig“ (`tagebuchArt`, `rezeptArt`, `editorArt`, `kochArt` mit `inszeniert`).
- Tests: `HeuteBayrischTest.ruhigZeigtSzeneSchlicht` (vorher normale Ansicht), `WocheStileTest.bayrischRuhigOhneWimpel`,
  Wörter in `KochenStileTest` und `TagebuchStileTest`.
- Im Emulator mit den Maßen des Handys nachgeprüft: Heute, Tagebuch, Rezepte, Rezept, Kochen, Woche, Einkauf in
  Bayrisch Ruhig.

**2026-11-18 – Ruhig als schlichte Szene: Grauen** (Branch `claude/ruhig-szenen-grauen`):
- Grauen steht in `RUHIG_ALS_SZENE` und zeigt in Ruhig wie die Entwürfe `U-*` die Szenen auf dem Pergament mit
  normalen Wörtern. Heute: „Heute“, statt des Auges ein Ring mit „von 2.000 kcal“ und den Nährwerten, keine
  Fangarme, die Tafel hell mit der Mahlzeit beim Namen („Abendessen“) und „Kochen“, „Tagebuch ›“, „Einkauf · n offen ›“.
  Woche „Woche“ mit sieben Tageschips statt des Mondbogens, helle Tafel ohne Notiz, „Leere Tage vorschlagen lassen“.
  Rezepte „Rezepte“ als Zeilen mit Name und kcal statt des Regals, helle Seite, „Öffnen“; Rezept ohne Schein.
  Einkauf „Diese Woche“ über „Einkauf“, „Listen“, ein gerades helles Blatt mit Kästchen, gekaufte durchgestrichen,
  normale Abteilungen, ohne Stempel und Flüstern. Tagebuch, Rezept, Editor und Kochen in Standardwörtern.
- `GrauenKnopf` in Ruhig voll in Tinte mit hellem Papier (`Grauen.papierHell`), `grauenHell()` dort der Text selbst;
  Schrift in der Akzentfarbe nimmt in Ruhig `primaer` statt der Glut (die helle Glut ist auf Pergament zu blass).
- Tests: `HeuteGrauenTest.ruhigZeigtSzeneSchlicht` (vorher normale Ansicht), `WocheStileTest.grauenRuhigTageschips`,
  Wörter in `KochenStileTest` und `TagebuchStileTest`.
- Im Emulator mit den Maßen des Handys nachgeprüft: Heute, Tagebuch, Rezepte, Rezept, Kochen, Woche, Einkauf in
  Grauen Ruhig.

**2026-11-18 – Ruhig als schlichte Szene: Kreuzer** (Branch `claude/ruhig-szenen-kreuzer`):
- Kreuzer steht in `RUHIG_ALS_SZENE`; damit zeigen alle Stile auch in Ruhig ihre Szene. Wie die Entwürfe `V-*` bleibt
  es dunkel, Karten ohne abgeschnittene Ecken (`kreuzerForm()`, `Kreuzer.karteRuhig` und `randRuhig`) und ohne
  Verlauf. Heute: „Samstag, 24. Oktober“, „Heute“ mit „1.140 übrig“, statt des Tagessystems die Summe groß mit
  „/ 2.000 kcal“ und Balken, die Mahlzeiten als Liste (Name, Gericht, kcal; Antippen führt zu Tagebuch, Rezept oder
  Eintragen, Geplantes mit „Gegessen?“), die Karte „ABENDESSEN“ mit „Kochen“, „Einkauf · n offen ›“. Woche „Woche“
  mit „x / 7 geplant“, sieben Tageschips statt der Route, „Noch nichts geplant“, „+ Mahlzeit“ und „Vorschlagen
  lassen“, in der Liste kcal oder „heute“. Einkauf „Diese Woche“ über „Einkauf“, „x / y erledigt“, „Listen“, statt
  der Kisten eine Liste je Abteilung mit Kästchen, erledigte durchgestrichen. Rezepte „Rezepte“ als Zeilen mit Name,
  Zeit und kcal, das gewählte mit Amber am Rand, darunter eine Karte mit ZEIT, KCAL, GEKOCHT und „Öffnen“. Rezept
  „REZEPT“, Kochen „SCHRITT 1 / 3“ mit „01“, Tagebuch, Editor und Planen in Standardwörtern.
- Tests: `HeuteKreuzerTest.ruhigZeigtSzeneSchlicht` und `ruhigAlleWege` (vorher normale Ansicht),
  `WocheStileTest.kreuzerRuhigTageschips`, Wörter in `KochenStileTest` und `TagebuchStileTest`.
  `PlanenSeiteTest.plusMitPortionen` (vorher `platzMitPortionen`) geht über „+ Mahlzeit“ der Szene, denn freie Plätze
  der normalen Ansicht zeigt jetzt kein Stil mit Planen-Seite mehr.
- Im Emulator mit den Maßen des Handys nachgeprüft: Heute, Rezepte, Rezept, Kochen, Woche, Einkauf in Kreuzer Ruhig.
- Im Emulator mit den Maßen des Handys nachgeprüft: Heute, Tagebuch, Rezepte, Rezept, Woche, Einkauf in Edo Ruhig.

**2026-11-21 – Einheitlicher Rahmen über alle Stile, Teil A** (Spec
`docs/superpowers/specs/2026-11-20-einheitlicher-rahmen-design.md`, Plan
`docs/superpowers/plans/2026-11-20-einheitlicher-rahmen.md`; Anlass: Alltagstest von 3.9.0 auf dem Handy):
- Zahnrad (#290): `EinstellungenKnopf` in allen Stilen und beiden Varianten oben rechts im Kopf von Heute, gleich
  groß wie die übrigen runden Knöpfe. Milchglas zeigt das Zahnrad statt der Person, Edo hat es aus der Knopfreihe
  unten nach oben bekommen. Anime bricht den Kopf nicht mehr um: Der Titel wird bis zu einer festen Untergrenze
  kleiner, sonst rückt das Datum unter den Titel; abgeschnitten wird nichts, auch nicht bei Schrift 200 %. Neue
  Bilder aller Köpfe bei 360 dp. Nachgebessert nach dem Abschlussreview: In Edo und Sage Ruhig rückt das Datum unter
  den Titel, sobald es nicht mehr daneben passt („Heute“ bleibt bei 130 % einzeilig), und das Zahnrad sitzt in allen
  Köpfen höchstens 48 dp unter der Oberkante, bei 360 dp bis Schrift 200 %.
- Stilkacheln und Anime-Leiste (#291): Die Kacheln unter „Stil“ und „Akzent“ behalten beim Umschalten Spalte, Breite
  und Reihe (feste Zellbreite, gleicher Innenabstand, Name immer zweizeilig); die Höhe hängt noch um 1–4 dp an der
  Schrift des Stils. Die Spalten kommen aus der Seitenbreite abzüglich des
  größten Karteneinzugs aller Stile, nicht aus der Karte des gewählten Stils: Bei gleicher Breite und Schrift stehen
  überall gleich viele in einer Reihe, auf dem Tablet sechs Stile (nachgebessert nach dem Abschlussreview). Die
  schwebende Navigationsleiste ist bei Anime deckend weiß (`StilWerte.leiste`).
- Einkauf (#292): In Sage bis Kreuzer ist das ⋮-Menü weg; der Listen-Knopf ist der einzige Weg zur Listen-Seite.
  „Liste löschen“ ist dort ein Papierkorb im Kopf („Diese Liste löschen“, löscht die angezeigte Liste), nur wenn
  eine Liste angezeigt wird. Milchglas behält sein Menü.
- Menüs und Abteilungen (#293): `FoodyMenue` ersetzt alle zwölf `DropdownMenu` der App (deckende Fläche, Form, Rand
  und Schrift des Stils, `KontrastTest` für Text auf der Menüfläche). Die Sortierung der Rezepte zeigt die gewählte
  mit Haken. Die aufklappbaren Abteilungen im Einkauf haben in jeder Szene Schrift und Farben ihrer
  Abschnittsköpfe. Bewusst: Im Sage-Rezeptmenü steht „Ins Archiv verbannen“ jetzt in der normalen Textfarbe statt in
  Rot (Menüpunkte einheitlich).
- Offen: Teil B (Eintragen neu, siehe oben). Am Handy in allen Stilen ansehen (Debug-Build).

**2026-11-22 – Einheitlicher Rahmen über alle Stile, Teil B: Eintragen neu** (Spec
`docs/superpowers/specs/2026-11-21-eintragen-neu-design.md`, Plan `docs/superpowers/plans/2026-11-21-eintragen-neu.md`,
ADR 0021; Anlass: Alltagstest von 3.9.0 auf dem Handy):
- Eigenes Ziel mit zwei Schritten (#297): Eintragen ist ein Ziel der Navigation (`Eintragen(datum, mahlzeit?, sofort?,
  auftrag)`), eine ganze Seite ohne Navigationsleiste, in allen sieben Stilen und beiden Varianten gleich aufgebaut;
  nur der Titel folgt dem Stil. Erfassen: „Foto“ und „Scannen“ als große Knöpfe, „Aus der Galerie“ und
  „Nährwerttabelle fotografieren“, die Suche über Rezepte, Zutaten samt gescannten Produkten und den BLS, das offene
  Geplante und „Nicht dabei? Frei eintragen“. Bestätigen: je nach Quelle Portionen, Menge, Packung, Foto oder freie
  Felder, „= … kcal“, die Mahlzeit über `FoodyMenue` und unten „Eintragen“. Heute und Tagebuch öffnen das Ziel; das
  alte Blatt mit Reitern, Chips und Knopfwörtern je Stil ist weg (`EintragenBlatt`, `FotoErkennungKnopf`). Der
  Kurztest der R8-Variante (`ReleaseStartTest`) öffnet jetzt Eintragen und wartet auf „Scannen“. Neue Bilder
  `eintragen_<stil>_{erfassen,bestaetigen_foto,bestaetigen_rezept}` bei 360 dp, die Bilder des alten Blatts entfallen.
- Heute-Szenen (#298): Edo, Sage und Bayrisch haben statt des kombinierten Knopfs zwei Knöpfe „Scannen“ und „Foto“,
  die gleich Scanner bzw. Kamera öffnen; „Foto“ nur mit eingeschalteter Fotoerkennung und Modell.
- Widgets direkt (#299): „KI-Foto“ und „Scannen“ öffnen Kamera bzw. Scanner, ohne dass vorher Heute oder Erfassen zu
  sehen ist (`WidgetStart` statt `Schnellstart`); beim Kaltstart bleibt Heute unsichtbar, bis Eintragen obenauf liegt.
  „Schnell eintragen“ öffnet Erfassen. Ein Widget-Tipp nach einem Prozessende im Hintergrund geht nicht mehr verloren
  (`LinkEingang`), und Öffnen aus „Zuletzt verwendet“ startet den Scanner nicht erneut. Am Handy geprüft: Kaltstart,
  Abbrechen, Zurück, Prozessende und „Zuletzt verwendet“.
- Doku und ADR 0021 (#300).
- Nachbesserung nach dem Abschlussreview (Branch `claude/eintragen-nachbesserung`): Ein Doppeltipp auf einen
  Einstieg in Heute oder im Tagebuch öffnet nur eine Seite Eintragen (`eintragenOeffnen`); eine offene Galerie öffnet
  keine zweite; jede leere Packung (Scan ohne Werte, Fehler, ohne Play-Dienste, Prozessende während der Suche) zeigt
  die Knöpfe zum Tabellenfoto; nach einem Prozessende in Bestätigen blitzt Erfassen nicht mehr auf; das Ende einer
  abgebrochenen Fotoerkennung räumt die neue nicht mehr ab; die Treffer in Erfassen sind Knöpfe statt
  Optionsfelder. Neue Seitentests: Foto und Tabellenfoto überstehen Neuaufbau und Prozessende. Die Wörter
  „Blatt“ und „Frei“ sind aus Code, KDoc und Tests von Eintragen raus.
- Offen:
  - Am Handy ansehen (Nutzer): Heute blitzt beim Widget-Kaltstart nicht auf, Speichern blendet normal aus, „Schnell
    eintragen“ öffnet Erfassen.
  - Doppeltipp: Für Eintragen erledigt (s. o.); die übrigen `navigate`-Aufrufe der App (etwa Rezept öffnen) haben
    keinen Schutz.
  - Sage: „+ SCANNEN“ bricht bei Schrift 130 % zwischen „+“ und dem Wort um. Ein geschütztes Leerzeichen hilft
    nicht: Bei 360 dp ist der Knopf (einer von drei gleich breiten) 93 px breit, der Text braucht gut 100 px; dann
    bricht es mitten im Wort („+ SCAN / NEN“). Braucht eine Gestaltungsentscheidung (Laufweite, kleinere Schrift,
    ohne „+“, zwei Zeilen Knöpfe).
  - `verdeckt` in `MainActivity` erst bei RESUMED zurücknehmen? Unbestätigt, hängt an der Sichtprüfung am Handy: Bei
    offener Kamera wird die Seite nie RESUMED, die Oberfläche könnte unsichtbar bleiben.
  - Bayrisch und Grauen zeichnen auch vor Knopf-Zeilen (`WahlZeile(knopf = true)`, etwa die Treffer in Erfassen)
    den leeren Stil-Punkt; er sieht wie ein Optionsfeld aus.
  - Kosmetisch: Kommt die App per Widget von einer anderen Seite (etwa einem Rezept) nach vorne, blendet diese gut
    700 ms unter der leeren Seite Eintragen aus.
  - Reste des alten Blatts in `:core:erkennung` (die KDoc ist bereinigt): `ErkennungTexte.KNOPF` und `GALERIE` sind ungenutzt, `KAMERA` steht
    nur noch in einem Test, `erkennen(loeschen)` rufen nur Tests. `Erfassen` schreibt „Aus der Galerie“ fest, statt die
    Konstante zu nehmen.
  - Testlücken: kein Seitentest, dass ein Widget-Sofort-Start nach einem Neuaufbau nicht noch einmal startet (das
    prüft nur das ViewModel); kein automatischer Test für `verdeckt` in `MainActivity` (die Logik ließe sich als
    kleine Composable herausziehen); kein Szenenbild ohne Fotomodell (Tablet: Bayrisch mit einem runden Knopf, Edo
    „Scannen“ über die volle Breite); `pruefeNichtsAbgeschnitten` erkennt bei 130 % keinen Umbruch mitten im Wort.
  - Schwache Prüfungen: `TagebuchStileTest.blaetternUndEintragen` prüft die Mahlzeit nur mit
    `assertNotNull(start.mahlzeit)`, `HeuteScreenTest.eintragenAusZeile` wählt den Knopf über den Index `[2]`.
  - Bewusst so: Ein Task, den „Schnell eintragen“ gestartet hat, öffnet aus „Zuletzt verwendet“ auf Heute statt in
    Erfassen. Andere Deep Links (Rezept, Kochen) öffnen ihr Ziel von dort erneut; das ist harmlos.
