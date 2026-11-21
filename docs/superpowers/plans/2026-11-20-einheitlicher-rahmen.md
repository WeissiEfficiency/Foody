# Einheitlicher Rahmen über alle Stile – Umsetzungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Zahnrad, Stilkacheln, Navigationsleiste, Einkauf-Menü und Ausklappmenüs sehen in allen sieben Stilen
gleich aus bzw. sitzen gleich, ohne den Stilen ihren Charakter zu nehmen.

**Architecture:** Zwei neue Bausteine in `:core:designsystem` (`EinstellungenKnopf`, `FoodyMenue`), ein neues Feld
`leiste` in `StilWerte`; die Szenen in `:feature:heute`, `:feature:einkauf`, `:feature:einstellungen` und die Menüs in
allen Features rufen sie auf. Keine neuen Module, keine Daten- oder Serveränderung.

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2026.09.00, Material3 1.4), Robolectric, Roborazzi.

**Spec:** `docs/superpowers/specs/2026-11-20-einheitlicher-rahmen-design.md`

## Global Constraints

- Bezeichner, KDoc und Texte auf Deutsch; Code liest sich wie der umgebende Code (CLAUDE.md).
- `allWarningsAsErrors` ist an; `./gradlew ktlintPruefen` muss grün sein (keine ungenutzten Importe).
- Zielflächen mindestens 48 dp; Kontrast WCAG 2.2 AA (`KontrastTest` grün).
- Bedienhilfen nutzen die normalen Begriffe („Einstellungen“, „Weitere Aktionen“, „Einkaufslisten“).
- Milchglas-Bilder ändern sich nur am Zahnrad (Person → Zahnrad) und an den Menüs.
- Gradle unter Windows mit `JAVA_HOME=C:\Users\Stefan\.jdks\jbr-21.0.11`, Befehle aus `C:\Dev\Foody` mit `./gradlew`.
- Nach gewollten Bildänderungen `./gradlew :<modul>:recordRoborazziDebug`, danach `verifyRoborazziDebug` grün; die
  neuen PNGs gehören in den Commit.
- Jeder Task ist ein eigener PR gegen `main` (Auto-Merge an), Server-Rebuild: nein.

## Review Focus

1. Anime auf 360 dp mit langem Datum („Mi · 30.12.“): Zahnrad bleibt oben rechts, Titel wird kleiner, nichts wird
   abgeschnitten → Test in Task 1 mit diesem Datum.
2. Sage Immersiv: Zahnrad über dem Feuerbild bleibt bei Statusleiste sichtbar und antippbar → Lagetest in Task 1 deckt
   beide Varianten ab.
3. Stilwahl bei großer Schrift (`fontScale` 1.3): Kacheln bleiben gleich groß untereinander, Namen werden nicht
   abgeschnitten (zwei Zeilen) → Test in Task 2.
4. Einkauf ohne aktuelle Liste (leer): Listen-Seite ohne „Liste löschen“, kein Absturz → Test in Task 3.
5. Menü mit vielen Einträgen und Anime-Konturen: Menü bleibt lesbar (Text auf deckender Fläche) → `KontrastTest`-Fall in
   Task 4.

---

### Task 1: Einstellungen-Knopf einheitlich, Lage bei 360 dp geprüft

**Files:**
- Create: `android/core/designsystem/src/main/kotlin/de/foody/core/designsystem/komponenten/EinstellungenKnopf.kt`
- Modify: `android/feature/heute/src/main/kotlin/de/foody/feature/heute/HeuteBereiche.kt` (`HeuteKopf`, Z. 62–77)
- Modify: `…/feature/heute/stil/HeuteSage.kt` (`SchlichterKopf` Z. 157, `RastAmFeuer` Z. 193)
- Modify: `…/feature/heute/stil/HeuteAnime.kt` (`Kopf` Z. 135–162)
- Modify: `…/feature/heute/stil/HeuteEdo.kt` (`HeuteEdo`, `SchlichterKopf`, `Knoepfe` Z. 466–480)
- Modify: `…/feature/heute/stil/HeuteBayrisch.kt` (`Kopf` Z. 141–170)
- Modify: `…/feature/heute/stil/HeuteGrauen.kt` (Z. 167), `…/stil/HeuteKreuzer.kt` (Z. 195)
- Test: `android/feature/heute/src/test/kotlin/de/foody/feature/heute/stil/EinstellungenKnopfLageTest.kt` (neu)
- Test: `android/feature/heute/src/test/kotlin/de/foody/feature/heute/HeuteScreenshotTest.kt` (neuer Test `stileSchmal`)

**Interfaces:**
- Produces: `@Composable fun EinstellungenKnopf(onKlick: () -> Unit, modifier: Modifier = Modifier)` – ruft
  `RunderKnopf(Icons.Outlined.Settings, "Einstellungen", onKlick, modifier)` (48 dp Zielfläche, 40-dp-Kreis in der
  Fläche des Stils). KDoc: „Zahnrad oben rechts im Kopf von Heute, in allen Stilen gleich groß.“

- [ ] **Step 1: Lagetest schreiben** – `EinstellungenKnopfLageTest`, `@Config(sdk = [36], application =
  android.app.Application::class, qualifiers = "w360dp-h800dp")`, `createAndroidComposeRule<ComponentActivity>()`.
  Ein `mutableStateOf(FoodyStil.MILCHGLAS to StilVariante.IMMERSIV)` steuert
  `FoodyTheme(stil, variante, unschaerfe = false) { FoodyHintergrund { HeuteScreen(zustand, KEINE_AKTIONEN) } }`;
  `zustand = beispielZustand()` mit Datum 2026-12-30 (Mittwoch). Für jede Kombination aus `FoodyStil.entries` ×
  `StilVariante.entries`:

```kotlin
val b = compose.onNodeWithContentDescription("Einstellungen").getBoundsInRoot()
val breite = compose.onRoot().getBoundsInRoot().right
assertTrue("$stil/$variante rechts", b.right >= breite - 32.dp)
assertTrue("$stil/$variante oben", b.top <= 96.dp)
assertEquals(48.dp, b.right - b.left)
assertEquals(48.dp, b.bottom - b.top)
```

- [ ] **Step 2: Test laufen lassen** – `./gradlew :feature:heute:testDebugUnitTest --tests "*EinstellungenKnopfLageTest*"`
  Erwartet: FAIL (Anime: zu weit links/unten, Edo: zu weit unten, Milchglas findet „Einstellungen“ als Person ok,
  Grauen/Kreuzer/Sage evtl. ok).
- [ ] **Step 3: `EinstellungenKnopf` anlegen** (Interface oben).
- [ ] **Step 4: Köpfe umstellen**
  - Milchglas `HeuteKopf`: `RunderKnopf(Icons.Outlined.PersonOutline, …)` → `EinstellungenKnopf(onEinstellungen)`.
  - Sage: beide `IconButton(onEinstellungen) { Icon(Settings …) }` → `EinstellungenKnopf`, Modifier in
    `RastAmFeuer` bleibt (`align(TopEnd).statusBarsPadding().padding(4.dp)`).
  - Anime: `FlowRow` → `Row(verticalAlignment = CenterVertically)`; Titel mit `Modifier.weight(1f)`, `maxLines = 1`,
    `softWrap = false`, `autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = <bisherige Größe>)`;
    rechts Datumspille und `EinstellungenKnopf`. Der eigene weiße 44-dp-Kreis entfällt.
  - Edo: Zahnrad aus `Knoepfe` entfernen (zwei Knöpfe je `weight(1f)`). Immersiv: über `Rollbild` eine Zeile
    `Row(Modifier.fillMaxWidth().padding(end = 12.dp), horizontalArrangement = Arrangement.End) { EinstellungenKnopf }`.
    Ruhig: in der Titelzeile von `SchlichterKopf` nach dem Datum `EinstellungenKnopf`.
  - Bayrisch: eigene Box mit Rand → `EinstellungenKnopf`, gleiche Stelle.
  - Grauen, Kreuzer: `IconButton(…)` → `EinstellungenKnopf(onEinstellungen)`.
  - Ungenutzte Importe (`Settings`, `IconButton`, `FlowRow`, `PersonOutline`) entfernen.
- [ ] **Step 5: Test erneut** – gleicher Befehl, Erwartet: PASS.
- [ ] **Step 6: Bildtest bei 360 dp** – in `HeuteScreenshotTest` neuer Test `stileSchmal()` mit
  `@Config(qualifiers = "w360dp-h800dp")`: für alle `FoodyStil.entries` in `IMMERSIV`
  `captureRoboImage("src/test/screenshots/heute_360_${stil.name.lowercase()}.png", roborazziOptions = vergleich)`
  mit demselben Inhalt wie `stile()`.
- [ ] **Step 7: Bilder neu aufnehmen und prüfen** – `./gradlew :feature:heute:recordRoborazziDebug` und
  `./gradlew :feature:heute:verifyRoborazziDebug :feature:heute:testDebugUnitTest ktlintPruefen` → grün. Die
  geänderten Bilder ansehen: Zahnrad überall oben rechts, sonst nichts verschoben.
- [ ] **Step 8: Commit + PR** „Zahnrad in allen Stilen oben rechts und gleich groß“.

### Task 2: Stilkacheln mit fester Größe, Anime-Leiste weiß

**Files:**
- Modify: `android/feature/einstellungen/src/main/kotlin/de/foody/feature/einstellungen/StilWahl.kt` (`Raster`,
  `StilMuster`, `AkzentMuster`)
- Modify: `android/core/designsystem/src/main/kotlin/de/foody/core/designsystem/stil/StilWerte.kt` (Feld `leiste`)
- Modify: `…/core/designsystem/glas/GlasFlaeche.kt` (Parameter `deckend`)
- Modify: `…/core/designsystem/komponenten/FoodyNavigation.kt` (`FoodyNavigationsleiste`, `FoodyNavigationsRail`)
- Test: `android/feature/einstellungen/src/test/kotlin/de/foody/feature/einstellungen/EinstellungenStileTest.kt`
- Test: `android/core/designsystem/src/test/kotlin/de/foody/core/designsystem/stil/StilTest.kt`

**Interfaces:**
- Produces: `StilWerte.leiste: Color?` – „Fläche der Navigationsleiste; `null` = Glas wie die übrigen Flächen.“
  ANIME (Immersiv und Ruhig) = `Color.White`, alle anderen `null`.
- Produces: `GlasFlaeche(stufe, modifier, form, unschaerfeErlaubt, deckend: Color? = null, inhalt)` – mit `deckend`
  statt Tönung/Unschärfe `background(deckend)`; Rand und Versatzschatten wie bisher.

- [ ] **Step 1: Tests schreiben**
  - `StilTest.leisteNurBeiAnimeWeiss`: für alle Stile und Varianten
    `assertEquals(if (stil == FoodyStil.ANIME) Color.White else null, stilWerte(stil, variante, …).leiste)`.
  - `EinstellungenStileTest.kachelnBehaltenIhreGroesse`: Einstellungen wie in den vorhandenen Tests dieser Datei
    zeigen, Stil per `mutableStateOf` erst `BAYRISCH`/`IMMERSIV`, dann `GRAUEN`/`IMMERSIV`; die Grenzen der Kachel
    „Milchglas“ (`onNode(hasText("Milchglas") and isSelectable())`) messen:
    `assertEquals(breiteBayrisch, breiteGrauen)` und `assertEquals(hoeheBayrisch, hoeheGrauen)`.
  - `EinstellungenStileTest.kachelnBeiGrosserSchrift`: `fontScale = 1.3f`; alle sieben Kacheln haben dieselbe Breite
    und Höhe, `onNodeWithText("Kosmisches Grauen").assertIsDisplayed()`.
- [ ] **Step 2: Tests laufen lassen** – `./gradlew :core:designsystem:testDebugUnitTest --tests "*StilTest*"
  :feature:einstellungen:testDebugUnitTest --tests "*EinstellungenStileTest*"` → FAIL.
- [ ] **Step 3: `StilWahl.kt`**
  - `Raster`: Zellen haben feste Breite (`zelle` × Schriftfaktor wie bisher berechnet) statt `weight(1f)`; Reihen
    `Arrangement.spacedBy(abstand)`, linksbündig; Spalten = wie viele passen (1–4).
  - Stil-Raster `zelle = 88.dp`, Akzent-Raster bleibt `72.dp`.
  - `StilMuster`: Innenabstand immer `FoodyAbstaende.s1` (gewählt nur Rand 2 dp und Haken); Name mit
    `style = MaterialTheme.typography.labelMedium.copy(lineHeight = 16.sp)`, `minLines = 2`, `maxLines = 2`.
  - `AkzentMuster`: ebenfalls gleicher Innenabstand gewählt/nicht gewählt.
- [ ] **Step 4: Anime-Leiste** – `StilWerte.leiste` ergänzen; `GlasFlaeche(…, deckend = …)`;
  `FoodyNavigationsleiste` und `FoodyNavigationsRail` geben `deckend = FoodyTheme.stil.leiste` mit.
- [ ] **Step 5: Tests erneut** – gleicher Befehl → PASS.
- [ ] **Step 6: Bilder** – `./gradlew :feature:einstellungen:recordRoborazziDebug :katalog:recordRoborazziDebug
  :app:recordRoborazziDebug`, danach `verifyRoborazziDebug` derselben Module und `ktlintPruefen` grün. Geänderte Bilder
  ansehen (nur Stilwahl und ggf. Anime-Leiste).
- [ ] **Step 7: Commit + PR** „Stilkacheln mit fester Größe, Anime-Leiste weiß“.

### Task 3: Einkauf – ein Weg zu den Listen

**Files:**
- Modify: `android/feature/einkauf/src/main/kotlin/de/foody/feature/einkauf/EinkaufScreen.kt` (Z. 78–90, Listen-Seite,
  Löschfrage)
- Modify: `…/feature/einkauf/ListenSeite.kt` (Parameter `onLoeschen`)
- Modify: `…/feature/einkauf/stil/EinkaufSage.kt`, `EinkaufAnime.kt`, `EinkaufEdo.kt`, `EinkaufBayrisch.kt`,
  `EinkaufGrauen.kt`, `EinkaufKreuzer.kt` (Parameter `menue` entfernen)
- Test: `android/feature/einkauf/src/test/kotlin/de/foody/feature/einkauf/stil/EinkaufStileTest.kt`,
  `…/ListenSeiteTest.kt`

**Interfaces:**
- Produces: `ListenSeite(blatt, mitZeitraum, onReiter, onZurueck, onLoeschen: (() -> Unit)?, inhalt)` – mit
  `onLoeschen != null` unten auf der Seite `FoodyKnopf("Liste löschen", onLoeschen, art = KnopfArt.TEXT)`.
- Produces: `haendlerKopf/animeKopf/edoKopf/bayrischKopf/grauenKopf/kreuzerKopf(ansicht, onListen)` ohne `menue`.

- [ ] **Step 1: Tests schreiben**
  - `EinkaufStileTest.keinMenueInDenStilen`: für SAGE, ANIME, EDO, BAYRISCH, GRAUEN, KREUZER × beide Varianten:
    `onNodeWithContentDescription("Weitere Aktionen").assertDoesNotExist()` und
    `onNodeWithContentDescription("Einkaufslisten").assertExists()`.
  - `EinkaufStileTest.listenOeffnetNeueListe`: Tipp auf „Einkaufslisten“ (Anime) → der erste Reiter („Neue Liste“)
    ist gewählt (`onNode(hasText("Neue Liste") and isSelected()).assertExists()`).
  - `EinkaufStileTest.milchglasBehaeltMenue`: Milchglas → „Weitere Aktionen“ existiert.
  - `ListenSeiteTest.loeschenNurMitListe`: mit `onLoeschen = null` kein „Liste löschen“; mit Lambda vorhanden, Tipp
    ruft es auf.
  - `EinkaufStileTest.loeschenAufListenSeite`: Anime, Listen-Seite offen, Tipp „Liste löschen“ → Frage „Liste
    löschen?“ erscheint.
- [ ] **Step 2: Tests laufen lassen** – `./gradlew :feature:einkauf:testDebugUnitTest` → die neuen FAIL.
- [ ] **Step 3: Umsetzen**
  - In den sechs Szenen `menue` aus Signatur und Kopf entfernen; Knopf „Listen“ (je Stil mit seinem Wort) bekommt
    `beschreibung = "Einkaufslisten"`.
  - `EinkaufScreen`: `onListen = { vm.blattOeffnen(Blatt.Vorschau) }`; `menue` nur noch im Zweig ohne Szene
    (Milchglas) für `EinkaufKopf`; `ListenSeite(…, onLoeschen = if (z.ansicht != null) ({ loeschenFrage = true })
    else null)`; die Löschfrage wird auch über der Listen-Seite gezeigt, nach dem Löschen `vm.blattSchliessen()`.
- [ ] **Step 4: Tests erneut** → PASS.
- [ ] **Step 5: Bilder** – `./gradlew :feature:einkauf:recordRoborazziDebug`, dann `verifyRoborazziDebug` und
  `ktlintPruefen` grün; in den Stilbildern fehlt nur das ⋮.
- [ ] **Step 6: Commit + PR** „Einkauf: ein Weg zu den Listen statt Menü und Knopf“.

### Task 4: Menüs und Abteilungen im Stil

Startet erst, wenn Task 3 in `main` ist (gleiche Einkauf-Dateien).

**Files:**
- Create: `android/core/designsystem/src/main/kotlin/de/foody/core/designsystem/komponenten/FoodyMenue.kt`
- Modify (alle `DropdownMenu`-Stellen): `core/scan/…/PackungScanKnopf.kt:96`, `feature/einkauf/…/EinkaufBlaetter.kt:35`,
  `feature/heute/…/tagebuch/FotoErkennungKnopf.kt:104`, `feature/rezepte/…/detail/DetailBereiche.kt:150`,
  `…/detail/stil/RezeptSage.kt:133`, `…/detail/stil/RezeptStile.kt:232`, `…/liste/ListeBereiche.kt:50` und `:116`,
  `…/liste/stil/RezepteSage.kt:263`, `…/liste/stil/RezepteStile.kt:224`, `feature/zutaten/…/ZutatEditorScreen.kt:152`,
  `…/ZutatenListeScreen.kt:164`
- Modify: `android/feature/einkauf/src/main/kotlin/de/foody/feature/einkauf/EinkaufKacheln.kt` (`Klappkopf`,
  `zuletztKacheln`, `katalogKacheln`), die sechs `stil/Einkauf*.kt`, `EinkaufScreen.kt`
- Test: `android/core/designsystem/src/test/kotlin/de/foody/core/designsystem/komponenten/FoodyMenueTest.kt` (neu),
  `KontrastTest` (Fall Menü), `feature/einkauf/…/stil/EinkaufStileTest.kt`

**Interfaces:**
- Produces: `@Composable fun FoodyMenue(offen: Boolean, onSchliessen: () -> Unit, modifier: Modifier = Modifier,
  inhalt: @Composable ColumnScope.() -> Unit)` – `DropdownMenu` mit `shape = FoodyTheme.formen.mittel`,
  `containerColor = FoodyTheme.stil.werte.glas` (deckend), `border = BorderStroke(FoodyTheme.stil.kante,
  FoodyTheme.farben.lichtkante)`.
- Produces: `@Composable fun FoodyMenuePunkt(text: String, onKlick: () -> Unit, modifier: Modifier = Modifier,
  symbol: ImageVector? = null, gewaehlt: Boolean = false)` – Text in `MaterialTheme.typography.bodyLarge`,
  `FoodyTheme.farben.text`; `gewaehlt` zeigt am Ende einen Haken (`Modifier.testTag("menue-haken")`).
- Produces: `internal typealias KlappkopfInhalt = @Composable (titel: String, offen: Boolean, onKlick: () -> Unit) ->
  Unit` in `EinkaufKacheln.kt`, `internal val StandardKlappkopf: KlappkopfInhalt` (der bisherige `Klappkopf`);
  `zuletztKacheln(…, kopf: KlappkopfInhalt = StandardKlappkopf)`, `katalogKacheln(…, kopf: KlappkopfInhalt =
  StandardKlappkopf)`; je Szene `internal val sageKlappkopf`, `animeKlappkopf`, `edoKlappkopf`, `bayrischKlappkopf`,
  `grauenKlappkopf`, `kreuzerKlappkopf`.

- [ ] **Step 1: Tests schreiben**
  - `FoodyMenueTest.zeigtPunkteUndSchliesst`: `FoodyMenue(offen = true)` mit zwei Punkten; Tipp auf einen ruft
    dessen `onKlick`; `gewaehlt = true` zeigt den Haken (`onNodeWithTag("menue-haken")`).
  - `KontrastTest`: für alle Stile, Varianten und Akzente `farben.text` auf `werte.glas` ≥ 4.5.
  - `EinkaufStileTest.abteilungKlapptAuf`: je Stil Q–V (Immersiv) Tipp auf „Obst & Gemüse“ (Rolle Button, Label
    „Aufklappen“) → ein Katalogartikel der Abteilung wird sichtbar; erneuter Tipp → Label „Aufklappen“ wieder da.
- [ ] **Step 2: Tests laufen lassen** – `./gradlew :core:designsystem:testDebugUnitTest :feature:einkauf:testDebugUnitTest`
  → FAIL (FoodyMenue fehlt).
- [ ] **Step 3: `FoodyMenue` und `FoodyMenuePunkt` anlegen**, dann alle zwölf `DropdownMenu`/`DropdownMenuItem`
  ersetzen (Texte und Aktionen unverändert).
- [ ] **Step 4: Abteilungsköpfe** – Slot wie im Interface; jede Szene baut ihren Klappkopf aus dem Aussehen ihrer
  vorhandenen Abteilungsüberschrift der Posten (Schrift, Farbe, Form; bei Bedarf die private Überschrift `internal`
  machen) plus Pfeil in `farben.text`; mindestens 48 dp hoch, Rolle Button, `onClickLabel` „Aufklappen“/„Zuklappen“
  wie bisher. `EinkaufScreen` wählt den Kopf nach `szene`.
- [ ] **Step 5: Prüfen** – `grep -rn "DropdownMenu(" android --include=*.kt` findet nur `FoodyMenue.kt`;
  Tests aus Step 2 → PASS.
- [ ] **Step 6: Bilder** – `recordRoborazziDebug` für `:feature:einkauf :feature:rezepte :feature:zutaten
  :feature:heute :katalog`, dann `./gradlew check ktlintPruefen` grün.
- [ ] **Step 7: Commit + PR** „Menüs und Abteilungen im gewählten Stil“.

### Task 5: Doku und Abnahme

**Files:**
- Modify: `docs/architecture.md` (Abschnitt zu den Stilen: `EinstellungenKnopf`, `FoodyMenue`, `StilWerte.leiste`,
  Klappkopf-Slot im Einkauf)
- Modify: `ToDoS/stile-plan.md` („Ergebnis“: Teil A mit PR-Nummern, offen Teil B Eintragen)

- [ ] **Step 1:** Abschnitte schreiben (Ist-Stand, kurz).
- [ ] **Step 2:** Debug-Build `./gradlew :app:assembleDebug`; der Nutzer sieht ihn sich am Handy (361 dp) in allen
  Stilen an – „Foody Debug“ ist dort installiert, nur nach seinem OK per `adb install -r` aktualisieren.
- [ ] **Step 3: Commit + PR** „Doku: einheitlicher Rahmen über alle Stile“.
