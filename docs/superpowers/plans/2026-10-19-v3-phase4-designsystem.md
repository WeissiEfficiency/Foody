# V3 Phase 4: Designsystem – Umsetzungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Neues Modul `:core:designsystem` (Ofenwarm-Tokens, zwei Glasstufen mit Haze, Komponenten) und Debug-App `:katalog`, mit per Test belegtem WCAG 2.2 AA – auch auf Glas ohne Unschärfe.

**Architecture:** `:core:designsystem` ist eine Compose-Bibliothek ohne Abhängigkeit zu `:core:data`/`:core:sync`; Tokens sind reine Kotlin-Werte (testbar ohne Android), `FoodyTheme` stellt sie per CompositionLocal bereit, `FoodyHintergrund` ist die Haze-Quelle, `GlasFlaeche` der Haze-Effekt. `:katalog` zeigt alle Komponenten und trägt die Roborazzi-Screenshot-Tests. Die v1 bleibt unverändert.

**Tech Stack:** Kotlin 2.4.20, Compose-BOM 2026.09.00, Material 3 (+ adaptive), Haze (`dev.chrisbanes.haze`), Roborazzi, Robolectric 4.16.1, JUnit4/kotlin-test.

**Spec:** [`docs/superpowers/specs/2026-10-19-v3-phase4-designsystem-design.md`](../specs/2026-10-19-v3-phase4-designsystem-design.md)

## Global Constraints

- Module: `:core:designsystem` = `android/core/designsystem`, Paket `de.foody.core.designsystem`; `:katalog` = `android/katalog`, Paket und App-ID `de.foody.katalog`. Kein Code aus `:core:data`, `:core:sync`, `:app`.
- v1 unverändert: `android/app/**`, `:sync-protocol`, `shared/**`.
- Farben exakt aus der Spec, Abschnitt 2 (Tabellen hell/dunkel, Glas-Startwerte); Änderungen nur, wenn der Kontrast-Test es erzwingt – dann Spec-Tabelle im selben Commit mitändern.
- Kontrast: Text ≥ 4,5:1; große Schrift (≥ 24 sp oder ≥ 18,66 sp fett) und Grafik/Kanten ≥ 3:1; gemessen ohne Unschärfe gegen den ungünstigsten Verlaufspunkt.
- Glas: Stufen `LEICHT` (Tönung hell `#FFFAF3` α 0,55 / dunkel `#2B201A` α 0,50) und `DICHT` (α 0,78 / 0,72), Unschärfe 24 dp, Lichtkante 1 dp.
- Schrift: Fraunces (Überschriften ab `titleLarge`), Figtree (alles andere), gebündelt unter `res/font` mit OFL-Lizenzdatei; Größen in `sp`.
- Formen 12/20/28 dp, Abstände im 4-dp-Raster; Berührflächen ≥ 48 dp; keine Dynamic Color.
- Texte, Bezeichner und Kommentare auf Deutsch wie im übrigen Code; `allWarningsAsErrors` bleibt an.
- Beispieldaten im Katalog erfunden; nie echte Datenbanken oder Fotos.
- PR gegen `main` mit Auto-Merge; Zeile „Server aktualisieren? Nein“.

## Review Focus

1. **Schrift 200 %** – kein Text wird abgeschnitten (Kachel, Navigation, Rezeptkarte, Knopf); Komponenten wachsen in die Höhe (Tests in Task 5, 6, 7 mit `fontScale = 2f`, Prüfung über `hasVisualOverflow`).
2. **Lange deutsche Titel** („Rinderrouladen-Schmorgericht mit Rotkohl und Klößen“) – Rezeptkarte kürzt sichtbar mit „…“, TalkBack liest den vollen Titel (Test in Task 6).
3. **Grenzwerte im Nährwertbalken** – 0 kcal, Ziel `null`, Ziel `0`, Werte über dem Ziel: keine Division durch null, kein Absturz, Text sagt „über Ziel“ (Test in Task 7).
4. **Keine Unschärfe / Animationen reduziert** – Glas zeigt nur Tönung (lesbar), `SyncStatus.Laeuft` dreht sich nicht (Tests in Task 4 und 7).
5. **Rezept ohne Bild** – Platzhalter (warmer Verlauf mit Besteck-Symbol) statt leerer Fläche, Kontrast der Glas-Leiste bleibt (Test in Task 6).

---

### Task 1: Modul-Gerüst, Abhängigkeiten, CI

**Files:**
- Modify: `settings.gradle.kts` (Includes `:core:designsystem` → `android/core/designsystem`, `:katalog` → `android/katalog`), `gradle/libs.versions.toml` (Haze, Roborazzi: Bibliotheken `haze`, `roborazzi`, `roborazzi-compose`, `roborazzi-junit-rule`, Plugin `roborazzi`), `.github/workflows/ci.yml` (Schritt „Screenshot-Vergleich“: `./gradlew :katalog:verifyRoborazziDebug` nach „Unit-Tests, Lint, Debug-Build“; zusätzlich `:katalog:assembleDebug` im bestehenden Build-Schritt)
- Create: `android/core/designsystem/build.gradle.kts` (Plugin `foody.android.feature` – Compose + Robolectric-Tests –, Namespace `de.foody.core.designsystem`, `implementation(libs.haze)`), `android/katalog/build.gradle.kts` (Plugins `foody.android.application`, `kotlin-compose`, `roborazzi`; `buildFeatures.compose = true`; `applicationId = "de.foody.katalog"`; `testOptions.unitTests.isIncludeAndroidResources = true`; Abhängigkeiten `:core:designsystem`, Compose-BOM, material3, material3-adaptive, activity-compose, Test: junit4, robolectric, compose-ui-test-junit4, roborazzi*), `android/katalog/src/main/AndroidManifest.xml` (eine Activity `KatalogActivity`, Launcher), `android/katalog/src/main/kotlin/de/foody/katalog/KatalogActivity.kt` (zeigt vorerst `Text("Katalog")`)
- Test: `android/core/designsystem/src/test/kotlin/de/foody/core/designsystem/GeruestTest.kt`

**Interfaces:**
- Produces: Module `:core:designsystem`, `:katalog`; Version-Catalog-Aliase `libs.haze`, `libs.roborazzi*`, `libs.plugins.roborazzi`.

- [ ] **Step 1:** Versionen bestimmen: neueste Haze- und Roborazzi-Version, die mit Compose-BOM 2026.09.00, Kotlin 2.4.20 und AGP 9.4.1 baut (Maven Central, Release-Notes prüfen); Lizenz Apache 2.0 bestätigen. Versionen im Bericht nennen.
- [ ] **Step 2: Test** `GeruestTest.modulBaut`: `assertEquals("de.foody.core.designsystem", BuildConfig.LIBRARY_PACKAGE_NAME)` – falls BuildConfig aus ist, stattdessen eine leere `internal object Designsystem` referenzieren. Rot sehen: `./gradlew :core:designsystem:testDebugUnitTest` → Fehler „unresolved“.
- [ ] **Step 3:** Gerüst wie oben anlegen.
- [ ] **Step 4:** `./gradlew :core:designsystem:testDebugUnitTest :katalog:assembleDebug check` → BUILD SUCCESSFUL.
- [ ] **Step 5:** Commit `"V3 Phase 4: Gerüst Designsystem und Katalog"`.

### Task 2: Farben, Glas- und Verlaufswerte, Kontrast-Test

**Files:**
- Create: `android/core/designsystem/src/main/kotlin/de/foody/core/designsystem/token/{FoodyFarben,GlasWerte,Verlauf,Kontrast}.kt`
- Test: `android/core/designsystem/src/test/kotlin/de/foody/core/designsystem/token/KontrastTest.kt`

**Interfaces:**
- Produces:
  - `data class FoodyFarben(grund, text, nebentext, primaer, aufPrimaer, primaerBehaelter, aufPrimaerBehaelter, oliv, safran, balkenSpur, lichtkante: Color)` mit `companion object { val Hell: FoodyFarben; val Dunkel: FoodyFarben }` – Werte aus Spec Abschnitt 2 (Lichtkante hell Weiß α 0,78, dunkel Weiß α 0,12).
  - `enum class GlasStufe { LEICHT, DICHT }`; `data class GlasWerte(val toenung: Color, val unschaerfe: Dp, val kante: Color)`; `fun glasWerte(stufe: GlasStufe, dunkel: Boolean): GlasWerte`.
  - `data class Verlauf(val oben: Color, val unten: Color)` (Farben mit Alpha); `val VerlaufHell`, `val VerlaufDunkel` (Spec-Werte).
  - `fun mischen(oben: Color, unten: Color): Color` (Alpha-Komposition, Ergebnis deckend); `fun kontrast(a: Color, b: Color): Double` (WCAG-Relativluminanz, sRGB-Linearisierung 0,04045).
  - `fun ungünstigsteUntergruende(farben: FoodyFarben, verlauf: Verlauf): List<Color>` = `[grund, mischen(verlauf.oben, grund), mischen(verlauf.unten, grund)]`.
  - `data class KontrastPaar(val name: String, val vorne: Color, val hinten: Color, val mindest: Double)`; `fun kontrastPaare(dunkel: Boolean): List<KontrastPaar>` – je Untergrund aus `ungünstigsteUntergruende` und je Glasstufe (`mischen(toenung, untergrund)`): Text 4,5; Nebentext 4,5; Primär als Text 4,5; aufPrimär auf Primär 4,5; aufPrimärBehälter auf PrimärBehälter 4,5; Oliv/Safran/Primär als Balkenfarbe gegen Glas `DICHT` 3,0; Balkenspur gegen Glas `DICHT` – keine Pflicht (Spur ist Hintergrund), nicht aufnehmen.

- [ ] **Step 1: Tests** in `KontrastTest`: `kontrastBekannterWerte` (`kontrast(Color.Black, Color.White)` = 21,0 ± 0,01; `kontrast(Color(0xFF777777), Color.White)` = 4,48 ± 0,01), `mischenDeckt` (`mischen(Color.Black.copy(alpha = 0.5f), Color.White)` ≈ `Color(0xFF808080)` je Kanal ± 1/255), `allePaareHell`, `allePaareDunkel` (jede Zeile von `kontrastPaare(…)` ≥ `mindest`; Fehlermeldung enthält Name, gemessenen Wert, Mindestwert), `paareDeckenAlleRollenAb` (Namen enthalten mindestens „Text“, „Nebentext“, „Primär“, „aufPrimär“, „Behälter“, „Oliv“, „Safran“ für beide Glasstufen).
- [ ] **Step 2:** Rot sehen: `./gradlew :core:designsystem:testDebugUnitTest --tests '*KontrastTest*'`.
- [ ] **Step 3:** Umsetzen. Scheitert ein Paar mit den Spec-Werten: Farbe minimal abdunkeln/aufhellen bzw. Tönung erhöhen, bis es besteht; Spec-Tabelle im selben Commit angleichen und im Bericht jede Änderung mit altem/neuem Wert und gemessenem Kontrast nennen.
- [ ] **Step 4:** Grün: gleicher Befehl → PASS.
- [ ] **Step 5:** Commit `"V3 Phase 4: Farben, Glas und Kontrast-Test"`.

### Task 3: Schrift, Formen, Abstände, FoodyTheme

**Files:**
- Create: `android/core/designsystem/src/main/res/font/{fraunces_semibold,fraunces_bold,figtree_regular,figtree_medium,figtree_semibold,figtree_bold}.ttf` (statische Schnitte aus `google/fonts`, Ordner `ofl/fraunces` bzw. `ofl/figtree`; liegen dort nur variable Dateien, die variable Datei je Familie einbinden und Schnitte über `FontVariation.Settings` wählen), `android/core/designsystem/src/main/res/font/LIZENZ-OFL.txt` (beide Copyright-Zeilen + OFL 1.1), `…/token/{FoodyTypo,FoodyFormen,FoodyAbstaende}.kt`, `…/FoodyTheme.kt`
- Test: `android/core/designsystem/src/test/kotlin/de/foody/core/designsystem/FoodyThemeTest.kt`

**Interfaces:**
- Consumes: `FoodyFarben`, `GlasStufe`, `glasWerte` (Task 2).
- Produces:
  - `val FrauncesFamilie: FontFamily`, `val FigtreeFamilie: FontFamily`; `val FoodyTypografie: Typography` (Fraunces für `displayLarge…titleLarge`, Figtree für `titleMedium…labelSmall`, Material-3-Größen); `val AugenbraueStil: TextStyle` (Figtree 12 sp, Bold, Laufweite 1,2 sp).
  - `object FoodyFormen { val klein = RoundedCornerShape(12.dp); val mittel = RoundedCornerShape(20.dp); val gross = RoundedCornerShape(28.dp) }`; `object FoodyAbstaende { val s1 = 4.dp; val s2 = 8.dp; val s3 = 12.dp; val s4 = 16.dp; val s5 = 20.dp; val s6 = 24.dp }`.
  - `@Composable fun FoodyTheme(dunkel: Boolean = isSystemInDarkTheme(), unschaerfe: Boolean = unschaerfeErlaubt(), inhalt: @Composable () -> Unit)`; `object FoodyTheme { val farben: FoodyFarben; val dunkel: Boolean; val unschaerfe: Boolean }` (Getter `@Composable @ReadOnlyComposable`, aus CompositionLocals); `MaterialTheme`-ColorScheme aus `FoodyFarben` abgeleitet (primary/onPrimary/primaryContainer/onPrimaryContainer/background/surface/onSurface/onSurfaceVariant = nebentext/outline = nebentext); `@Composable fun animationenErlaubt(): Boolean` = `Settings.Global.ANIMATOR_DURATION_SCALE != 0f` (Fehler beim Lesen → `true`); `@Composable fun unschaerfeErlaubt(): Boolean = animationenErlaubt()`.

- [ ] **Step 1: Tests** (Robolectric, `createComposeRule`): `themeLiefertHelleFarben` (in `FoodyTheme(dunkel = false)`: `FoodyTheme.farben == FoodyFarben.Hell`, `MaterialTheme.colorScheme.primary == FoodyFarben.Hell.primaer`), `themeLiefertDunkleFarben`, `ueberschriftenInFraunces` (`FoodyTypografie.titleLarge.fontFamily == FrauncesFamilie`, `bodyLarge.fontFamily == FigtreeFamilie`), `unschaerfeAusBeiAnimationenAus` (`Settings.Global.putFloat(…, ANIMATOR_DURATION_SCALE, 0f)` → `FoodyTheme.unschaerfe == false`), `schriftenLadenOhneFehler` (Text in `FoodyTypografie.headlineLarge` rendert, `onNodeWithText` existiert).
- [ ] **Step 2:** Rot sehen (`--tests '*FoodyThemeTest*'`).
- [ ] **Step 3:** Schriften herunterladen (nur aus `github.com/google/fonts`), Lizenzdatei anlegen, Tokens und Theme umsetzen.
- [ ] **Step 4:** Grün.
- [ ] **Step 5:** Commit `"V3 Phase 4: Schrift, Formen, Theme"`.

### Task 4: Hintergrund und Glasfläche

**Files:**
- Create: `…/glas/{FoodyHintergrund,GlasFlaeche}.kt`
- Test: `android/core/designsystem/src/test/kotlin/de/foody/core/designsystem/glas/GlasFlaecheTest.kt`

**Interfaces:**
- Consumes: `FoodyTheme`, `Verlauf`, `glasWerte` (Task 2/3).
- Produces: `@Composable fun FoodyHintergrund(modifier: Modifier = Modifier, inhalt: @Composable BoxScope.() -> Unit)` – zeichnet `grund` + zwei Radialverläufe (oben links Radius 0,85 × max. Kante, unten rechts 0,7 ×) und ist `hazeSource` eines `HazeState`, den es per `LocalHazeState` bereitstellt; `@Composable fun GlasFlaeche(stufe: GlasStufe, modifier: Modifier = Modifier, form: Shape = FoodyFormen.gross, inhalt: @Composable BoxScope.() -> Unit)` – mit Unschärfe: `hazeEffect(state, style = HazeStyle(tint = toenung, blurRadius = unschaerfe))`; ohne (`FoodyTheme.unschaerfe == false` oder kein `LocalHazeState`): nur `background(toenung)`; immer `border(1.dp, kante, form)` und `clip(form)`. Entscheidung in `enum class GlasModus { UNSCHAERFE, TOENUNG }` und `internal fun glasModus(unschaerfe: Boolean, hatHaze: Boolean): GlasModus` (nur beide wahr → `UNSCHAERFE`).

- [ ] **Step 1: Tests:** `glasModusJeKombination` (`glasModus(true, true) == UNSCHAERFE`, die drei anderen Kombinationen `TOENUNG`), `ohneHintergrundRendertTrotzdem` (GlasFlaeche außerhalb von FoodyHintergrund: keine Ausnahme, Inhalt sichtbar), `ohneUnschaerfeRendertTrotzdem` (in `FoodyTheme(unschaerfe = false)` über FoodyHintergrund: Inhalt sichtbar), `inhaltWirdGezeigt` (Text in GlasFlaeche über FoodyHintergrund auffindbar).
- [ ] **Step 2:** Rot sehen.
- [ ] **Step 3:** Umsetzen.
- [ ] **Step 4:** Grün.
- [ ] **Step 5:** Commit `"V3 Phase 4: Hintergrund und Glas"`.

### Task 5: Grundbausteine und Navigation

**Files:**
- Create: `…/komponenten/{FoodyKnopf,FoodyChip,Abschnittskopf,Augenbraue,FoodyNavigation}.kt`
- Test: `android/core/designsystem/src/test/kotlin/de/foody/core/designsystem/komponenten/GrundbausteineTest.kt`

**Interfaces:**
- Consumes: Theme, Formen, Abstände, `GlasFlaeche`.
- Produces:
  - `enum class KnopfArt { PRIMAER, UMRISS, TEXT }`; `@Composable fun FoodyKnopf(text: String, onKlick: () -> Unit, modifier: Modifier = Modifier, art: KnopfArt = KnopfArt.PRIMAER, aktiv: Boolean = true)` – Mindesthöhe 48 dp, Form `CircleShape`.
  - `@Composable fun FoodyChip(text: String, ausgewaehlt: Boolean, onKlick: () -> Unit, modifier: Modifier = Modifier)` – Semantik `selected`.
  - `@Composable fun Abschnittskopf(titel: String, modifier: Modifier = Modifier, linkText: String? = null, onLink: (() -> Unit)? = null)` – Titel als `heading()`.
  - `@Composable fun Augenbraue(text: String, modifier: Modifier = Modifier, farbe: Color = FoodyTheme.farben.nebentext)` – zeigt `text.uppercase(Locale.GERMAN)`.
  - `data class NavZiel(val text: String, val symbol: ImageVector)`; `@Composable fun FoodyNavigationsleiste(ziele: List<NavZiel>, aktiv: Int, onWahl: (Int) -> Unit, modifier: Modifier = Modifier)` (schwebende `GlasFlaeche(LEICHT)`, Ecken 34 dp, Abstand 12 dp zum Rand) und `@Composable fun FoodyNavigationsRail(…gleiche Parameter…)` (Tablet, senkrecht); aktives Ziel in `primaer`, Semantik `selected`.

- [ ] **Step 1: Tests:** `knopfMindestens48dp` (`assertHeightIsAtLeast(48.dp)` für alle drei Arten), `knopfDeaktiviertMeldetDas` (`assertIsNotEnabled`), `chipMeldetAuswahl` (`assertIsSelected` / `assertIsNotSelected`), `abschnittskopfIstUeberschrift` (`SemanticsProperties.Heading` vorhanden), `augenbraueInGrossbuchstaben` („Abendessen · 18:30“ → Knoten mit „ABENDESSEN · 18:30“), `navigationWaehltZiel` (Klick auf „Woche“ ruft `onWahl(2)`; aktives Ziel `assertIsSelected`), `doppelteSchriftOhneAbschneiden` (`fontScale = 2f` über `LocalDensity`: Knopf-, Chip- und Navigationstexte ohne `hasVisualOverflow`).
- [ ] **Step 2:** Rot sehen.
- [ ] **Step 3:** Umsetzen.
- [ ] **Step 4:** Grün.
- [ ] **Step 5:** Commit `"V3 Phase 4: Grundbausteine und Navigation"`.

### Task 6: Rezeptkarte und Kachel

**Files:**
- Create: `…/komponenten/{Rezeptkarte,Kachel}.kt`
- Test: `android/core/designsystem/src/test/kotlin/de/foody/core/designsystem/komponenten/{RezeptkarteTest,KachelTest}.kt`

**Interfaces:**
- Consumes: Theme, `GlasFlaeche`, `FoodyKnopf`, `Augenbraue`.
- Produces:
  - `data class KartenAktion(val text: String, val onKlick: () -> Unit)`.
  - `@Composable fun RezeptkarteGross(titel: String, meta: String, onKlick: () -> Unit, modifier: Modifier = Modifier, augenbraue: String? = null, bild: (@Composable () -> Unit)? = null, aktion: KartenAktion? = null)` – Höhe mindestens 232 dp, Glas-Leiste `DICHT` unten (Abstand 10 dp), Titel in `titleLarge`, max. 2 Zeilen mit Ellipse.
  - `@Composable fun RezeptkarteRaster(titel: String, onKlick: () -> Unit, modifier: Modifier = Modifier, bild: (@Composable () -> Unit)? = null, favorit: Boolean = false, zeit: String? = null, kcal: String? = null)` – quadratisches Bild (Ecken 20 dp), Titel darunter, max. 2 Zeilen.
  - Ohne `bild`: `RezeptPlatzhalter()` (warmer Verlauf Tomate→Glut mit Besteck-Symbol, `contentDescription = null`).
  - Beide Karten: `contentDescription` der klickbaren Fläche = voller Titel + Meta (bzw. Zeit/kcal/„Favorit“).
  - `@Composable fun Kachel(name: String, menge: String, onKlick: () -> Unit, modifier: Modifier = Modifier, untertitel: String? = null, abgehakt: Boolean = false, symbol: (@Composable () -> Unit)? = null)` – `GlasFlaeche(DICHT)`, Mindesthöhe 92 dp, Monogramm = erster Buchstabe von `name` (groß) im Kreis 30 dp (`primaerBehaelter`/`aufPrimaerBehaelter`); abgehakt: Name durchgestrichen, Farbe `nebentext`, Semantik `stateDescription = "abgehakt"`.

- [ ] **Step 1: Tests:** `RezeptkarteTest`: `grosseKarteZeigtAlles` (Augenbraue, Titel, Meta, Aktion „Kochen“ klickbar → Callback), `langerTitelGekuerztAberVollVorgelesen` (Titel „Rinderrouladen-Schmorgericht mit Rotkohl und Klößen und Rahmsoße“: sichtbarer Text hat Ellipse – `hasVisualOverflow` true –, `contentDescription` enthält den vollen Titel), `ohneBildPlatzhalter` (Testtag `rezept-platzhalter` vorhanden), `rasterKarteFavoritVorgelesen` (contentDescription enthält „Favorit“), `doppelteSchriftWaechstStattAbzuschneiden` (`fontScale = 2f`: Meta-Text ohne Overflow, Karte höher als bei 1f). `KachelTest`: `kachelMindestens48dp`, `monogrammAusErstemBuchstaben` („paprika“ → „P“), `abgehaktGemeldet` (`stateDescription == "abgehakt"`), `klickRuftCallback`, `doppelteSchriftOhneAbschneiden`.
- [ ] **Step 2:** Rot sehen.
- [ ] **Step 3:** Umsetzen.
- [ ] **Step 4:** Grün.
- [ ] **Step 5:** Commit `"V3 Phase 4: Rezeptkarte und Kachel"`.

### Task 7: Nährwertbalken, Sync-Status, leere Zustände

**Files:**
- Create: `…/komponenten/{Naehrwertbalken,SyncStatus,LeererZustand}.kt`, `…/komponenten/Zahlen.kt` (deutsche Zahlformate)
- Test: `android/core/designsystem/src/test/kotlin/de/foody/core/designsystem/komponenten/{NaehrwertbalkenTest,SyncStatusTest,LeererZustandTest}.kt`

**Interfaces:**
- Consumes: Theme, `GlasFlaeche`, `FoodyKnopf`.
- Produces:
  - `fun ganzzahlDeutsch(wert: Int): String` (Tausenderpunkt: 1240 → „1.240“).
  - `data class Balkenanteile(val eiweiss: Float, val kh: Float, val fett: Float, val ueberZiel: Boolean)`; `fun balkenanteile(kcal: Int, ziel: Int?, eiweissG: Int, khG: Int, fettG: Int): Balkenanteile` – Anteile nach Energie (4/4/9 kcal je g) skaliert auf `kcal / ziel` (höchstens 1,0 gesamt); `ziel` `null` oder ≤ 0 → Anteile füllen den Balken nach Energieverteilung; alle Gramm 0 → alle Anteile 0; `ueberZiel = ziel != null && ziel > 0 && kcal > ziel`.
  - `@Composable fun Naehrwertbalken(kcal: Int, ziel: Int?, eiweissG: Int, khG: Int, fettG: Int, modifier: Modifier = Modifier)` – Kopfzeile „1.240 / 2.000 kcal“ (ohne Ziel: „1.240 kcal“; über Ziel: „2.300 / 2.000 kcal · über Ziel“), Balken 12 dp (Spur `balkenSpur`, Segmente `oliv`/`safran`/`primaer`), Legende „Eiweiß 68 g · KH 142 g · Fett 41 g“ mit Farbpunkten; Balken-Semantik `contentDescription = "1.240 von 2.000 kcal: Eiweiß 68 g, Kohlenhydrate 142 g, Fett 41 g"` (Formen ohne Ziel / über Ziel analog).
  - `sealed interface SyncAnzeige { data object Synchron; data object Laeuft; data object Wartet; data class Fehler(val kurztext: String) }`; `@Composable fun SyncStatus(anzeige: SyncAnzeige, modifier: Modifier = Modifier)` – Chip auf `GlasFlaeche(LEICHT)`, Texte „Synchron“, „Läuft …“, „Wartet auf Netz“, `kurztext`; Symbole Haken, Kreispfeil (dreht nur, wenn `animationenErlaubt()` aus Task 3), Wolke durchgestrichen, Warndreieck; Semantik `liveRegion = Polite`.
  - `@Composable fun LeererZustandZeile(text: String, aktionText: String, onAktion: () -> Unit, modifier: Modifier = Modifier)` (gestrichelte Kante 1 dp, Farbe `nebentext` α 0,5 nur dekorativ) und `@Composable fun LeererZustandFlaeche(symbol: ImageVector, titel: String, text: String, aktionText: String?, onAktion: (() -> Unit)?, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Tests:** `NaehrwertbalkenTest`: `zahlenDeutsch` (1240 → „1.240“, 0 → „0“, 1000000 → „1.000.000“), `anteileNachEnergie` (`balkenanteile(1240, 2000, 68, 142, 41)` → Summe ≈ 0,62 ± 0,005; Verhältnis eiweiss:kh:fett = 272:568:369), `ohneZielFuellt` (`ziel = null` → Summe 1,0), `zielNullKeinAbsturz` (`ziel = 0` → wie ohne Ziel), `nichtsGegessen` (alles 0 → alle Anteile 0, Text „0 / 2.000 kcal“), `ueberZiel` (`kcal = 2300, ziel = 2000` → `ueberZiel`, Summe 1,0, Text enthält „über Ziel“), `beschreibungVollstaendig` (contentDescription exakt wie oben). `SyncStatusTest`: `textJeZustand` (vier Texte), `laeuftOhneDrehungBeiAnimationenAus` (ANIMATOR_DURATION_SCALE 0 → `animationenErlaubt() == false`, kein laufender Animator – über `mainClock.autoAdvance = false` und unveränderte Drehung nach 1 s), `fehlerZeigtKurztext`. `LeererZustandTest`: `zeileAktionKlickbar` (≥ 48 dp, Callback), `flaecheOhneAktion` (kein Knopf bei `aktionText = null`), `doppelteSchriftOhneAbschneiden`.
- [ ] **Step 2:** Rot sehen.
- [ ] **Step 3:** Umsetzen.
- [ ] **Step 4:** Grün.
- [ ] **Step 5:** Commit `"V3 Phase 4: Nährwertbalken, Sync-Status, leere Zustände"`.

### Task 8: Katalog-App

**Files:**
- Create: `android/katalog/src/main/kotlin/de/foody/katalog/{KatalogApp,KatalogSeite,Beispiele}.kt`, `android/katalog/src/main/kotlin/de/foody/katalog/seiten/*.kt` (eine Datei je Seite); Modify: `KatalogActivity.kt` (zeigt `KatalogApp()`)
- Test: `android/katalog/src/test/kotlin/de/foody/katalog/KatalogAppTest.kt`

**Interfaces:**
- Consumes: alle Komponenten aus Task 3–7.
- Produces:
  - `enum class KatalogSeite(val titel: String) { FARBEN_SCHRIFT("Farben und Schrift"), GLAS("Glas"), REZEPTKARTE("Rezeptkarte"), KACHEL("Kachel"), NAEHRWERTE("Nährwertbalken"), SYNC("Sync-Status"), LEER("Leere Zustände"), GRUNDBAUSTEINE("Grundbausteine") }`.
  - `data class KatalogEinstellung(val darstellung: Darstellung = Darstellung.SYSTEM, val schrift200: Boolean = false, val ohneUnschaerfe: Boolean = false)`; `enum class Darstellung { SYSTEM, HELL, DUNKEL }`.
  - `@Composable fun KatalogApp(start: KatalogSeite = KatalogSeite.FARBEN_SCHRIFT, einstellung: KatalogEinstellung = KatalogEinstellung())` – Breite ≥ 840 dp (`currentWindowAdaptiveInfo().windowSizeClass`): Seitenliste links, Inhalt rechts; sonst Seitenliste oben als `FoodyChip`-Reihe; Umschalter in einer Kopfzeile; Inhalt immer in `FoodyTheme(dunkel = …, unschaerfe = !ohneUnschaerfe)` über `FoodyHintergrund`; Schrift 200 % über `CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f))`.
  - `@Composable fun KatalogSeitenInhalt(seite: KatalogSeite)` – jede Seite zeigt alle Zustände ihrer Komponenten (Spec Abschnitt 3) mit erfundenen Beispielen aus `Beispiele.kt` (z. B. „Senf-Krusten-Braten“, „Milch 1 l“, 1.240/2.000 kcal; dazu die Grenzfälle aus Review Focus 1–5).
  - Farben-und-Schrift-Seite: Farbfelder mit Rollenname und Hex-Wert, Schriftstufen `displayLarge…labelSmall`.

- [ ] **Step 1: Tests:** `jedeSeiteRendert` (für jede `KatalogSeite`: `KatalogApp(start = seite)` zeigt den Seitentitel, keine Ausnahme), `umschalterWechseltDarstellung` (Klick „Dunkel“ → Knoten mit Testtag `katalog-inhalt` trägt Semantik-Eigenschaft `darstellung = "dunkel"` – eigene `SemanticsPropertyKey` im Katalog), `tabletZweispaltig` (`@Config(qualifiers = "w1280dp-h800dp")`: Seitenliste und Inhalt gleichzeitig sichtbar), `handyEinspaltig` (`w411dp-h891dp`: Chip-Reihe sichtbar).
- [ ] **Step 2:** Rot sehen: `./gradlew :katalog:testDebugUnitTest --tests '*KatalogAppTest*'`.
- [ ] **Step 3:** Umsetzen.
- [ ] **Step 4:** Grün; zusätzlich `./gradlew :katalog:assembleDebug`.
- [ ] **Step 5:** Commit `"V3 Phase 4: Katalog-App"`.

### Task 9: Screenshot-Tests

**Files:**
- Create: `android/katalog/src/test/kotlin/de/foody/katalog/KatalogScreenshotTest.kt`; Referenzbilder unter `android/katalog/src/test/screenshots/` (Roborazzi-Ausgabeordner per `roborazzi { outputDir.set(file("src/test/screenshots")) }`)
- Modify: `android/katalog/build.gradle.kts` (Roborazzi-Ausgabeordner, Vergleichstoleranz)

**Interfaces:**
- Consumes: `KatalogApp`, `KatalogSeite`, `KatalogEinstellung` (Task 8).

- [ ] **Step 1: Test** `KatalogScreenshotTest` (Robolectric `@GraphicsMode(NATIVE)`, parametrisiert über `KatalogSeite.entries` × {hell, dunkel} × {Handy `w411dp-h891dp`, Tablet `w1280dp-h800dp`} + {Handy, hell, Schrift 200 %}; Dateiname `<seite>_<hell|dunkel>_<handy|tablet>[_schrift200].png`; immer `ohneUnschaerfe = true`, weil Robolectric keine Unschärfe rendert).
- [ ] **Step 2:** Referenzbilder erzeugen: `./gradlew :katalog:recordRoborazziDebug`; Anzahl = 8 × 2 × 2 + 8 = 40 Dateien prüfen; jedes Bild einmal ansehen (keine leeren/abgeschnittenen Bilder) und im Bericht auffällige nennen.
- [ ] **Step 3:** Vergleich: `./gradlew :katalog:verifyRoborazziDebug` → PASS. Toleranz `changeThreshold` höchstens 0,5 %, im Build-Skript dokumentiert.
- [ ] **Step 4:** Gegenprobe: eine Farbe in `FoodyFarben.Hell` kurz ändern → `verifyRoborazziDebug` schlägt fehl; zurücksetzen.
- [ ] **Step 5:** Commit `"V3 Phase 4: Screenshot-Tests des Katalogs"`. Hinweis an den Controller: Schlägt der Vergleich in der CI (Linux) nur wegen Schriftglättung fehl, Bilder aus dem CI-Artefakt übernehmen oder Toleranz nach Rücksprache anheben – nie ohne Entscheidung.

### Task 10: Sichtprüfung, Doku, Abschluss

**Files:**
- Create: `docs/adr/0012-designsystem-v3-ofenwarm-haze.md`
- Modify: `docs/architecture.md` (Abschnitt „Designsystem V3“: Module, Tokens, Glasstufen, Kontrast-Regel, Katalog, Tests), `ToDoS/v3-plan.md` (Phase 4 abhaken mit Ergebnis), `ToDoS/v3-abnahme.md` (Punkte zu Darstellung/Barrierefreiheit nur, wenn durch Tests belegt)

- [ ] **Step 1 (Controller):** Katalog auf eigenem Emulator `Foody_Test` (nie auf dem des Nutzers) installieren, mit echter Unschärfe hell und dunkel, Handy und – per `wm size 2560x1600`/`wm density 320` – Tablet ansehen; Screenshots in den Scratchpad und dem Nutzer schicken; Auffälligkeiten als Befund.
- [ ] **Step 2:** ADR 0012 (Kontext: Spec; Entscheidung: Ofenwarm, Haze, gebündelte Schriften, Kontrast-Regel ohne Unschärfe; verworfene Alternativen aus der Spec-Tabelle; Folgen: neue Abhängigkeiten Haze/Roborazzi, v1 unverändert bis Phase 8).
- [ ] **Step 3:** Doku anpassen; `./gradlew check :core:designsystem:testDebugUnitTest :katalog:testDebugUnitTest :katalog:verifyRoborazziDebug :katalog:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 4:** Commit `"V3 Phase 4: Doku und Abschluss"`; PR gegen `main` mit Auto-Merge, Zeile „Server aktualisieren? Nein“.

---

## Reihenfolge

1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 (5, 6, 7 hängen nur an 3/4 und dürfen in beliebiger Reihenfolge laufen). Ein Branch (`claude/v3-phase4-designsystem`), ein PR am Ende.
