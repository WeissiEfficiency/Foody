# Foody – wählbare Stile Q bis V

Stand: 2026-11-11, Entwurf des Plans, noch nichts umgesetzt. Grundlage sind die Entwürfe im Design-Canvas
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
- [ ] Zustand und Anzeige trennen, wo es noch fehlt: Woche, Einkauf, Rezeptliste (je ein kleiner PR).
- [ ] Die Szenen-Plätze und `StilTexte` einführen.
- [ ] Q Immersiv für Heute (Rast am Feuer), Rezepte (Grimoire), Woche (Karte der Woche) und Einkauf (Händler).
      Flamme und Glut ruhen, wenn Animationen aus sind.
- [ ] Leistung: In Listen keine Unschärfe oder teuren Zeichnungen pro Zeile. Baseline-Profil prüfen.

### Phase 5 – Immersiv Pilot, restliche Screens
- [ ] Eintragen, Tagebuch, Rezept, Editor, Kochen, Planen, Listen, Einstellungen, Zutaten.
- [ ] Abnahme des Pilots auf dem Handy. Erst danach geht es mit den nächsten Stilen weiter.

### Phase 6 – Immersiv für die übrigen Stile
- [ ] Ein Stil nach dem anderen, je Stil zwei bis drei PRs (Hauptscreens, dann Rest). Die Reihenfolge legt der Nutzer
      fest.
- [ ] Mundart (T) und die Texte der Szenen einmal vom Nutzer gegenlesen lassen.

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

(noch offen)
