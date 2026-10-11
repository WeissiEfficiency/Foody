# Web-Oberfläche Teil 3 (Planen und Einkaufen) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wochenplan und Einkaufslisten im Browser pflegen – mit genau denselben Regeln wie in der App.

**Architecture:** Vier Regeln aus den App-Repositories wandern als reine Funktionen nach `shared/domain` (dazu die
Eingabebildung für Bedarf und Vorschläge); App und Server rufen sie auf. Der Server liest über `HaushaltsDaten`, schreibt
nur über `SyncService.push` in den neuen Klassen `PlanSchreiber` und `EinkaufSchreiber`, Seiten mit kotlinx.html + htmx.

**Tech Stack:** Kotlin, Ktor-Server, kotlinx.html, htmx, Room (App), JUnit/kotlin.test, Robolectric (E2E).

**Spec:** `docs/superpowers/specs/2026-10-11-web-oberflaeche-teil3-design.md`

## Global Constraints

- Alle Texte für Nutzer auf Deutsch; Bezeichner wie im umgebenden Code (Web-Modul deutsch, App-Repositories englisch).
- Schreiben nur über `SyncService.push`; Datensätze aus dem **aktuell gespeicherten** Roh-JSON bauen (unbekannte Felder bleiben).
- Alle neuen Routen über `webGet`/`webPost`; jeder POST prüft CSRF (`s.csrfGueltig(call.csrfAus(form))`, sonst 403) und liest das Formular mit `call.formular()`.
- Fremde/unbekannte IDs → 404 (`call.nichtGefunden`), nichts geschrieben. Inzwischen gelöschter Eintrag → zurück mit Hinweis „Eintrag gibt es nicht mehr“.
- Abgelehnter Push (`PushStatus.REJECTED`) → Seite „Das ging nicht – bitte neu laden.“ (Status 409).
- Grenzen: Portionen 1–99; Datum ±1 Jahr um heute; von ≤ bis, höchstens 31 Tage; Listenname 1–200 Zeichen (getrimmt); Freitext 1–200; Notiz ≤ 1000; Mahlzeit nur `Mahlzeit.entries`.
- Nach jedem erfolgreichen POST 303 auf eine GET-Seite (wie Teil 2).
- Größen: Plan mit 14 Tagen und Einkaufsansicht mit 75 Einträgen < 30 KB; Bearbeiten-Modus mit 75 Einträgen < 50 KB.
- Ohne JavaScript voll bedienbar; htmx nur für Abhaken (gibt es) und die Rezeptsuche auf `/plan/neu`.
- Instrumentierte App-Tests nur auf dem AVD `Foody_Test` (vorher `adb -s <serial> emu avd name` prüfen), nie auf `Medium_Phone`.
- Nach Abschluss: PR gegen main mit Auto-Merge; im PR „Server aktualisieren? Ja“ (`git pull && docker compose up -d --build`).

## Review Focus

1. Planeintrag zeigt auf ein archiviertes Rezept (zählt wie in der App mit) oder ein gelöschtes (wird ignoriert) → Liste, Abgleich und Planseite ohne 500 (Test in Task 6).
2. „Gekocht“ doppelt (Doppelklick oder Handy hat schon gekocht) → Vorrat wird nur einmal abgezogen (Test in Task 4).
3. Neu berechnen fasst manuelle Einträge (Freitext, „Rezept auf Liste“) nie an (Test in Task 6).
4. HTML-Sonderzeichen in Freitext, Notiz und Listenname werden escaped angezeigt und überschreiten keine Grenze (Test in Task 7).
5. Vorschlag, wenn alle Tage belegt sind oder es keine aktiven Rezepte gibt → Hinweis „Nichts vorzuschlagen“, kein leeres Übernehmen-Formular (Test in Task 5).

---

### Task 1: Gemeinsame Regeln in `shared/domain`

**Files:**
- Create: `shared/domain/src/main/kotlin/de/foody/domain/Einkaufsregeln.kt`
- Create: `shared/domain/src/main/kotlin/de/foody/domain/Kochen.kt`
- Create: `shared/domain/src/main/kotlin/de/foody/domain/PlanVorschlag.kt`
- Test: `shared/domain/src/test/kotlin/de/foody/domain/EinkaufsregelnTest.kt`, `KochenTest.kt`, `PlanVorschlagTest.kt`

**Interfaces:**
- Produces (alle in Paket `de.foody.domain`):
  - `object EinkaufsBedarf { fun berechnen(range: DateRange, slots: List<MealSlot>, recipes: Map<String, Recipe>, ingredients: Map<String, Ingredient>, pantry: List<PantryItem>, ausgeschlossen: Set<String> = emptySet()): List<ShoppingNeed> }` — schließt zusätzlich alle Zutaten mit `IngredientCatalog.neverBuy(IngredientCatalog.canonicalName(name))` aus; liefert auch Bedarfe mit `toBuy == 0` (Vorschau zeigt sie als „aus Vorrat“).
  - `object EinkaufsAbgleich { fun bleibtAbgehakt(warAbgehakt: Boolean, alt: Quantity, neu: Quantity): Boolean; fun entfaelltLoeschen(warAbgehakt: Boolean): Boolean }` — abgehakt bleibt nur bei gleicher Dimension und `neu.baseAmount <= alt.baseAmount`; Entfällt löscht nur `!warAbgehakt`.
  - `object RezeptAufListe { data class Bestand(val id: String, val ingredientId: String?, val menge: Quantity?, val manuell: Boolean, val abgehakt: Boolean); sealed interface Aenderung { data class Erhoehen(val id: String, val neu: Quantity) : Aenderung; data class Neu(val bedarf: ShoppingNeed) : Aenderung }; fun bedarf(rezept: Recipe, portionen: Int, ingredients: Map<String, Ingredient>, heute: LocalDate): List<ShoppingNeed>; fun zusammenfuehren(bedarf: List<ShoppingNeed>, bestand: List<Bestand>): List<Aenderung> }` — `bedarf` = `EinkaufsBedarf.berechnen` über einen Pseudo-Slot `"recipe:<id>"` an `heute`, gefiltert auf `toBuy > 0`; `zusammenfuehren` addiert auf den ersten Bestand mit `manuell && !abgehakt && ingredientId == bedarf.ingredientId && menge != null && menge.dimension == toBuy.dimension`, sonst `Neu`.
  - `object EinkaufEingabe { data class Eintrag(val id: String, val name: String, val abgehakt: Boolean); sealed interface Aktion { data class Abhaken(val id: String) : Aktion; data class Zurueck(val id: String) : Aktion; data class Neu(val name: String, val abteilung: String) : Aktion }; fun entscheiden(text: String, eintraege: List<Eintrag>): Aktion? }` — leerer Text → null; bekannter Artikel (`ShoppingCatalog.find`) über `ShoppingCatalog.sameItem` abgleichen (offen → Abhaken, nur abgehakt → Zurueck, sonst Neu mit `ShoppingCatalog.sectionFor(null, known.name)` und dem Katalognamen); unbekannt → `Neu(text.trim(), ShoppingCatalog.OWN_ITEMS)`.
  - `object Kochen { data class Vorrat(val id: String, val ingredientId: String, val menge: BigDecimal, val einheit: MeasureUnit); fun vorratAbziehen(rezept: Recipe, portionen: Int, umrechnung: Map<String, ConversionInfo>, vorrat: List<Vorrat>): Map<String, BigDecimal> }` — Algorithmus 1:1 aus `PlanRepository.markCooked` (Pflichtzeilen, Zutat ohne Eintrag in `umrechnung` überspringen, nie unter 0, Rest in der Einheit des Vorrats); Ergebnis nur geänderte Einträge (id → neue Menge).
  - `object PlanVorschlag { data class Rezept(val id: String, val favorit: Boolean, val mahlzeiten: Set<Mahlzeit>, val pflichtZutaten: List<PantryCoverage.Requirement>); fun leereTage(tage: List<LocalDate>, heute: LocalDate, belegt: Map<LocalDate, List<String>>, mahlzeit: Mahlzeit): List<LocalDate>; fun vorschlagen(rezepte: List<Rezept>, leereTage: List<LocalDate>, vorratZutaten: Set<String>, eingeplant: List<Pair<String, LocalDate>>, bis: LocalDate, seed: Long, mahlzeit: Mahlzeit): Map<LocalDate, String> }` — `belegt` = slotTypes je Tag; `eingeplant` = (rezeptId, datum) aller Planeinträge; `lastPlanned` je Rezept = spätestes Datum in `[leereTage.min() - REPEAT_GAP_DAYS, bis]`; dann `MealSuggestions.suggest`.

- [ ] **Step 1: Tests schreiben**

```kotlin
// EinkaufsregelnTest
@Test fun abgehaktBleibtBeiWenigerOderGleich() { assertTrue(EinkaufsAbgleich.bleibtAbgehakt(true, g(500), g(400))); assertTrue(EinkaufsAbgleich.bleibtAbgehakt(true, g(500), g(500))) }
@Test fun abgehaktKommtZurueckBeiMehrOderAndererDimension() { assertFalse(EinkaufsAbgleich.bleibtAbgehakt(true, g(500), g(600))); assertFalse(EinkaufsAbgleich.bleibtAbgehakt(true, g(500), stk(1))) }
@Test fun entfaelltLoeschtNurOffene() { assertTrue(EinkaufsAbgleich.entfaelltLoeschen(false)); assertFalse(EinkaufsAbgleich.entfaelltLoeschen(true)) }
@Test fun bedarfOhneWasserUndOhneNachBedarf()  // Rezept mit Leitungswasser 500 ml, Salz 0 g, Reis 200 g → nur Reis
@Test fun rezeptAufListeAddiertInBasiseinheit() // Bestand manuell Reis 0,5 kg offen + Bedarf 200 g → Erhoehen(id, 700 g)
@Test fun rezeptAufListeNichtAufAbgehaktOderGeneriert() // abgehakter bzw. nicht manueller Reis → Neu
@Test fun eingabeBekannterArtikelOffenWirdAbgehakt() // „milch“ bei offenem „Milch“ → Abhaken(id)
@Test fun eingabeGekaufterArtikelKommtZurueck() // nur abgehaktes „Milch“ → Zurueck(id)
@Test fun eingabeNeuMitAbteilung() // „Milch“ ohne Eintrag → Neu("Milch", ShoppingCatalog.sectionFor(null, "Milch")); „Grillkohle XL“ → Neu(…, OWN_ITEMS); „  “ → null
// KochenTest
@Test fun ziehtUmgerechnetAbNieUnterNull() // Vorrat 1 Packung Mehl (packageWeightG 1000), Rezept 1200 g für 2, gekocht für 2 → Packung 0
@Test fun optionaleUndUnbekannteZeilenBleiben() // optionale Zeile und Zutat ohne Umrechnungseintrag → Map leer
@Test fun portionenSkalieren() // Rezept 200 g Reis für 2, gekocht für 4, Vorrat 1000 g → 600
// PlanVorschlagTest
@Test fun leereTageNurAbHeuteUndOhneMahlzeit()
@Test fun gleicherSeedGleicherVorschlag_wieMealSuggestions() // Ergebnis == MealSuggestions.suggest mit von Hand gebauten Kandidaten
@Test fun kuerzlichEingeplantesFaelltWeg() // Rezept vor 3 Tagen eingeplant → nicht vorgeschlagen
```

- [ ] **Step 2: Laufen lassen** — Run: `./gradlew :domain:test` · Expected: FAIL (unresolved references)
- [ ] **Step 3: Implementieren** in den drei Dateien gemäß Interfaces.
- [ ] **Step 4: Laufen lassen** — Run: `./gradlew :domain:test` · Expected: PASS
- [ ] **Step 5: Commit** — `git commit -m "Domain: gemeinsame Regeln für Einkauf, Kochen und Vorschläge"`

### Task 2: App ruft die gemeinsamen Regeln

**Files:**
- Modify: `android/app/src/main/java/de/foody/app/data/repo/ShoppingRepository.kt` (`preview`, `applyDiff`, `addRecipe`)
- Modify: `android/app/src/main/java/de/foody/app/data/repo/Repositories.kt` (`PlanRepository.markCooked`)
- Modify: `android/app/src/main/java/de/foody/app/ui/shopping/ShoppingViewModel.kt` (`addFromSearch`)
- Modify: `android/app/src/main/java/de/foody/app/ui/planner/PlannerViewModel.kt` (`suggest`)

**Interfaces:**
- Consumes: alles aus Task 1. Keine Signatur der App ändert sich.

- [ ] **Step 1: Basislauf** — Run: `./gradlew :app:testDebugUnitTest` · Expected: PASS (Ausgangslage festhalten)
- [ ] **Step 2: Umstellen.** `preview` → `EinkaufsBedarf.berechnen`; `applyDiff` → `EinkaufsAbgleich` für `checked` und Löschen; `addRecipe` → `RezeptAufListe.bedarf` + `zusammenfuehren` (Bestand aus `dao.getItems`); `markCooked` → `Kochen.vorratAbziehen`, dann die zurückgegebenen Mengen upserten; `addFromSearch`/`tapCatalog` → `EinkaufEingabe.entscheiden` (bestehende Kachel-Logik `tapCatalog` bleibt für Kacheln, nutzt dieselbe Entscheidung); `suggest` → `PlanVorschlag.leereTage` + `vorschlagen`.
- [ ] **Step 3: Unit-Tests** — Run: `./gradlew :app:testDebugUnitTest` · Expected: PASS
- [ ] **Step 4: Instrumentierte Tests auf Foody_Test** — `adb devices`; für jede Seriennummer `adb -s <serial> emu avd name` = `Foody_Test` bestätigen; Run: `ANDROID_SERIAL=<serial> ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.foody.app.ShoppingFlowTest,de.foody.app.RecipeToShoppingTest,de.foody.app.ShoppingTilesTest` · Expected: alle PASS (enthält `applyDiffKeepsCheckedWhenLessIsNeeded`, `markCookedDeductsPantryAcrossUnits`)
- [ ] **Step 5: Commit** — `git commit -m "App: Einkauf, Kochen und Vorschläge über gemeinsame Regeln"`

### Task 3: Server liest Vorrat und Planstatus, Zeitzone, 7/14 Tage

**Files:**
- Modify: `server/web/src/main/kotlin/de/foody/server/web/PayloadZuDomain.kt`, `HaushaltsDaten.kt`, `WebOberflaeche.kt`, `seiten/PlanTagebuchSeiten.kt`
- Modify: `server/start/src/main/kotlin/de/foody/server/Main.kt` (keine Änderung nötig, wenn `zone` Standardwert hat – prüfen)
- Test: `server/web/src/test/kotlin/de/foody/server/web/PayloadZuDomainTest.kt`, `PlanTagebuchTest.kt`

**Interfaces:**
- Produces:
  - `WebPlanEintrag(id, rev: Long, datum, mahlzeit, rezeptId, portionen, gekochtAm: Long?)`
  - `WebEinkaufsliste(id, rev: Long, name, geaendert, von: LocalDate?, bis: LocalDate?)`
  - `internal data class WebVorrat(val id: String, val rev: Long, val zutatId: String, val menge: BigDecimal, val einheit: MeasureUnit)`; `Haushalt.vorrat: List<WebVorrat>`; `PayloadZuDomain.vorrat(r: SyncRecord): WebVorrat?`
  - `WebOberflaeche.routen(deps: ServerDeps, zone: ZoneId = standardZone()): List<Route.() -> Unit>`; `internal fun standardZone(): ZoneId` = `FOODY_ZEITZONE` oder `Europe/Berlin` (ungültiger Wert → Warnung im Log, Europe/Berlin); `internal fun ServerDeps.heute(zone: ZoneId): LocalDate = clock.instant().atZone(zone).toLocalDate()` — ersetzt alle `LocalDate.now(deps.clock)` im Web-Modul.
  - `/plan` nimmt `tage=7|14` (anderes → 7), Navigationslinks behalten `tage`; jeder Tag hat `id="tag-JJJJ-MM-TT"`.

- [ ] **Step 1: Tests**

```kotlin
@Test fun vorratWirdGelesen() // PANTRY_ITEM-Datensatz → WebVorrat mit Menge/Einheit
@Test fun heuteInBerlin() = webServer { env -> env.uhr.jetzt = Instant.parse("2026-10-11T23:30:00Z"); /* /plan zeigt Mo 12.10. als Heute */ }
@Test fun planMitVierzehnTagen() // /plan?tage=14 zeigt 14 Tagesüberschriften; tage=99 → 7
```

- [ ] **Step 2:** Run `./gradlew :server-web:test` · Expected: FAIL
- [ ] **Step 3:** Implementieren; README `server/README.md` um `FOODY_ZEITZONE` ergänzen.
- [ ] **Step 4:** Run `./gradlew :server-web:test` · Expected: PASS
- [ ] **Step 5: Commit** — `git commit -m "Web: Vorrat lesen, Zeitzone für heute, Plan mit 7/14 Tagen"`

### Task 4: Planeinträge im Browser ändern und einplanen

**Files:**
- Create: `server/web/src/main/kotlin/de/foody/server/web/PlanSchreiber.kt`
- Create: `server/web/src/main/kotlin/de/foody/server/web/seiten/PlanSeiten.kt` (Auswahlseite `/plan/neu`, Aktionen); Plan-Anzeige bleibt in `PlanTagebuchSeiten.kt`, bekommt je Eintrag das Aktionsformular und je Tag „+ Hinzufügen“
- Modify: `seiten/RezeptSeiten.kt` (Formular „Einplanen“), `WebOberflaeche.kt`
- Test: `server/web/src/test/kotlin/de/foody/server/web/PlanSchreibenTest.kt`

**Interfaces:**
- Consumes: Task 1 `Kochen.vorratAbziehen`; Task 3 `WebPlanEintrag`, `WebVorrat`, `heute`.
- Produces:
  - `internal sealed interface Schreiben { data object Ok; data object Weg; data object Abgelehnt }` (in `PlanSchreiber.kt`, auch von `EinkaufSchreiber` genutzt)
  - `internal class PlanSchreiber(deps: ServerDeps, daten: HaushaltsDaten)` mit `einplanen(household, datum: LocalDate, mahlzeit: Mahlzeit, rezeptId: String, portionen: Int): Schreiben` (Rezept fremd/archiviert → `Weg`), `aendern(household, id: String, aktion: String): Schreiben` (`minus|plus|frueher|spaeter|loeschen`; Portionen bleiben in 1–99), `gekocht(household, id: String): Schreiben` (schon `cookedAt` gesetzt → `Ok` ohne Abzug; sonst Vorrat-Pushes, dann Slot mit `cookedAt = clock.millis()`).
  - Routen: `POST /plan/{id}` (Feld `aktion`, `ab`, `tage`), `GET /plan/neu?tag=&mahlzeit=&alle=&q=`, `GET /plan/neu/liste` (htmx-Teil der Rezeptliste), `POST /plan/einplanen` (tag, mahlzeit, rezept, portionen, zurueck=`plan|rezept`).
  - Fremde Planeintrag-ID → 404; Eintrag weg → 303 auf `/plan?…&hinweis=weg`, Seite zeigt „Eintrag gibt es nicht mehr“.

- [ ] **Step 1: Tests**

```kotlin
@Test fun einplanenUndAnzeigen()        // POST /plan/einplanen → 303 /plan?ab=<tag>#tag-<tag>; Seite zeigt Rezept; Slot-Payload: slotType "ABENDESSEN", servings 3
@Test fun portionenVerschiebenLoeschen() // plus → 3; minus bei 1 bleibt 1; spaeter → Datum +1; loeschen → Datensatz gelöscht
@Test fun gekochtZiehtVorratAbEinmal()   // Vorrat Reis 1000 g, Rezept 200 g/2 Portionen, Slot 2 Portionen: gekocht → 800; zweites gekocht → weiter 800 (Review Focus 2)
@Test fun auswahlseiteFiltertNachMahlzeit() // Rezept mahlzeiten=FRUEHSTUECK fehlt bei mahlzeit=ABENDESSEN, erscheint mit alle=1
@Test fun auswahlVorgabeErsteFehlendeMahlzeit() // Tag mit Frühstück → Vorgabe MITTAGESSEN
@Test fun ungueltigeEingabenBehaltenFormular() // portionen=0, datum +2 Jahre, mahlzeit=BRUNCH → Meldung, nichts geschrieben
@Test fun fremdeIdsSind404()             // fremder Slot bei POST /plan/{id}, fremdes Rezept bei einplanen → 404, fremder Haushalt unverändert
@Test fun neuePostsOhneCsrfSind403()
@Test fun inzwischenGeloeschtGibtHinweis() // Slot per Push gelöscht, dann POST plus → 303 mit hinweis=weg, Seite zeigt „Eintrag gibt es nicht mehr“
@Test fun planVierzehnTageUnter30KB()    // 14 Tage × 3 Einträge
```

- [ ] **Step 2:** Run `./gradlew :server-web:test --tests '*PlanSchreibenTest*'` · Expected: FAIL
- [ ] **Step 3:** Implementieren. Ein Formular je Eintrag mit mehreren `button(name="aktion", value=…)`; „gekocht“-Knopf nur ohne `gekochtAm`.
- [ ] **Step 4:** Run `./gradlew :server-web:test` · Expected: PASS
- [ ] **Step 5: Commit** — `git commit -m "Web: Wochenplan bearbeiten, einplanen, gekocht"`

### Task 5: Vorschläge

**Files:**
- Modify: `seiten/PlanSeiten.kt`, `PlanSchreiber.kt`, `WebOberflaeche.kt`, `seiten/PlanTagebuchSeiten.kt` (Formular „Vorschlagen“ mit Mahlzeit)
- Test: `server/web/src/test/kotlin/de/foody/server/web/VorschlagTest.kt`

**Interfaces:**
- Consumes: Task 1 `PlanVorschlag`; Task 4 `PlanSchreiber.einplanen`.
- Produces: `GET /plan/vorschlag?ab=&tage=&mahlzeit=&seed=` (seed fehlt → `clock.millis()`), `POST /plan/vorschlag` mit `mahlzeit`, `ab`, `tage`, `tag_0..n`/`rezept_0..n`; `PlanSchreiber.vorschlagUebernehmen(household, mahlzeit, paare: List<Pair<LocalDate, String>>, von: LocalDate, bis: LocalDate): Schreiben` — jedes Paar geprüft (Rezept aktiv im Haushalt, Tag in `[von, bis]` und ±1 Jahr), sonst ganze Übernahme 400-Seite ohne Schreiben; Portionen = Rezeptportionen.

- [ ] **Step 1: Tests**

```kotlin
@Test fun gleicherSeedGleicheSeite()       // zwei GETs mit seed=7 liefern identische Paare; seed=8 „Neu mischen“-Link vorhanden
@Test fun uebernimmtGenauDieAngezeigtenPaare() // Paare aus der Seite parsen, POST → genau diese Slots
@Test fun manipuliertesPaarAbgelehnt()     // rezept_0 = fremdes/archiviertes Rezept → 400, keine Slots
@Test fun nichtsVorzuschlagen()            // alle Tage haben ABENDESSEN, bzw. keine aktiven Rezepte → „Nichts vorzuschlagen“, kein Übernehmen-Knopf (Review Focus 5)
@Test fun vorschlagPostOhneCsrfIst403()
```

- [ ] **Step 2:** Run `./gradlew :server-web:test --tests '*VorschlagTest*'` · Expected: FAIL
- [ ] **Step 3:** Implementieren (Eingaben: `PlanVorschlag.Rezept` aus aktiven `WebRezept` mit `einordnung.mahlzeiten`, Pflichtzutaten aus nicht-optionalen Zeilen mit Zutatennamen; Vorrat-Zutaten mit Menge > 0).
- [ ] **Step 4:** Run `./gradlew :server-web:test` · Expected: PASS
- [ ] **Step 5: Commit** — `git commit -m "Web: Vorschläge für den Wochenplan"`

### Task 6: Einkaufsliste aus dem Plan und Neu berechnen

**Files:**
- Create: `server/web/src/main/kotlin/de/foody/server/web/EinkaufSchreiber.kt`
- Create: `server/web/src/main/kotlin/de/foody/server/web/seiten/ListeAusPlanSeiten.kt`
- Modify: `seiten/EinkaufSeite.kt` (Knöpfe „Neue Liste aus Plan“, „Neu berechnen“ bei Listen mit Zeitraum), `WebOberflaeche.kt`
- Test: `server/web/src/test/kotlin/de/foody/server/web/ListeAusPlanTest.kt`

**Interfaces:**
- Consumes: Task 1 `EinkaufsBedarf`, `EinkaufsAbgleich`, `ShoppingDiff`; Task 3 `WebVorrat`, `WebEinkaufsliste.von/bis`; Task 4 `Schreiben`.
- Produces:
  - `internal class EinkaufSchreiber(deps: ServerDeps, daten: HaushaltsDaten)` mit `bedarf(household, von: LocalDate, bis: LocalDate, vorrat: Boolean, ausgeschlossen: Set<String>): List<ShoppingNeed>`, `anlegen(household, name: String, von, bis, vorrat: Boolean, ausgeschlossen: Set<String>): String?` (Listen-ID; null wenn nichts zu kaufen), `abgleich(household, listId: String, vorrat: Boolean): List<ShoppingDiffEntry<WebEintrag>>?` (null = Liste fremd/ohne Zeitraum), `abgleichAnwenden(household, listId, vorrat: Boolean): Schreiben`.
  - Domain-Eingaben aus `Haushalt`: Slots aus `plan` (nur Rezepte, die in `rezepte` vorkommen), Vorrat → `PantryItem`.
  - Einträge wie `ShoppingRepository.buildItems`: Menge in Basiseinheit, `manual=false`, Kategorie der Zutat, `sources` mit Rezeptname, Datum, Beitrag in Basiseinheit; Liste mit `start`/`end`, `version` 1; Liste vor Einträgen pushen.
  - Abgleich nur über nicht manuelle Einträge, Schlüssel `"$ingredientId|${dimension}"`; GEÄNDERT ersetzt `amount`, `unit`, `sources`, `checked` laut `EinkaufsAbgleich`; ENTFÄLLT löscht laut `entfaelltLoeschen`; NEU hinten an; Liste `version + 1`.
  - Routen: `GET /einkauf/neu?von=&bis=&vorrat=1|0`, `POST /einkauf/neu` (name, von, bis, vorrat, `mit`=zutatIds; ausgeschlossen = Bedarfe ohne `mit`), `GET /einkauf/{liste}/abgleich?vorrat=`, `POST /einkauf/{liste}/abgleich`.
  - Vorgabe-Name: `"Einkauf ${von.format("d.")}–${bis.format("d.M.")}"`, z. B. „Einkauf 12.–18.10.“.

- [ ] **Step 1: Tests**

```kotlin
@Test fun vorschauUndAnlegenWieInDerApp() // Plan 2 Slots; Liste == EinkaufsBedarf.berechnen(...) gefiltert toBuy>0 (Zutat, Menge, Einheit); Name-Vorgabe „Einkauf 12.–18.10.“
@Test fun abgewaehlteZutatFehlt()          // ohne mit=reis → kein Reis-Eintrag
@Test fun vorratWirdAbgezogen()            // Vorrat 150 g von 400 g → 250 g; vorrat=0 → 400 g
@Test fun nichtsZuKaufen()                 // leerer Zeitraum → Hinweis, keine Liste
@Test fun abgleichZeigtUndUebernimmt()     // Slot hinzugefügt → „Neu“; abgehakter Reis bei weniger bleibt abgehakt; bei mehr wieder offen; Entfällt löscht nur offene; version +1
@Test fun abgleichLaesstManuelleInRuhe()   // Freitext-Eintrag bleibt unverändert (Review Focus 3)
@Test fun archiviertesOderGeloeschtesRezeptImPlan() // Slot auf archiviertes Rezept zählt wie in der App mit; Slot auf gelöschtes Rezept ignoriert, keine 500 (Review Focus 1)
@Test fun zeitraumGrenzen()                // bis < von, > 31 Tage → Meldung
@Test fun fremdeListeIst404UndCsrf403()
```

- [ ] **Step 2:** Run `./gradlew :server-web:test --tests '*ListeAusPlanTest*'` · Expected: FAIL
- [ ] **Step 3:** Implementieren.
- [ ] **Step 4:** Run `./gradlew :server-web:test` · Expected: PASS
- [ ] **Step 5: Commit** — `git commit -m "Web: Einkaufsliste aus dem Plan, neu berechnen"`

### Task 7: Liste bearbeiten, Freitext, Rezept auf die Liste

**Files:**
- Modify: `EinkaufSchreiber.kt`, `seiten/EinkaufSeite.kt`, `seiten/RezeptSeiten.kt` („Auf die Einkaufsliste“), `WebOberflaeche.kt`, `web/foody.css` (nur falls nötig)
- Test: `server/web/src/test/kotlin/de/foody/server/web/ListeBearbeitenTest.kt`

**Interfaces:**
- Consumes: Task 1 `EinkaufEingabe`, `RezeptAufListe`; Task 6 `EinkaufSchreiber`.
- Produces:
  - `EinkaufSchreiber.eingabe(household, listId: String?, text: String): Pair<Schreiben, String>` (Listen-ID; ohne Liste neue „Einkauf“), `loeschen(household, eintragId): Schreiben`, `notiz(household, eintragId, text: String): Schreiben` (leer → `note` null), `listeLoeschen(household, listId): Schreiben` (erst Einträge, dann Liste als Löschmarkierung), `rezeptAufListe(household, rezeptId, portionen: Int, listId: String?): Pair<Int, String>?` (Anzahl, Listen-ID; null = Rezept fremd).
  - Routen: `POST /einkauf/eingabe` (liste, text), `GET /einkauf?liste=&bearbeiten=1`, `POST /einkauf/eintrag/{id}/loeschen`, `POST /einkauf/eintrag/{id}/notiz`, `GET /einkauf/{liste}/loeschen` (Bestätigung), `POST /einkauf/{liste}/loeschen`, `POST /einkauf/rezept` (rezept, portionen, liste) → 303 `/einkauf?liste=…&hinzu=N`, Seite zeigt „1 Zutat hinzugefügt“ bzw. „N Zutaten hinzugefügt“.
  - Liste-Vorgabe auf der Rezeptseite: `listen.maxByOrNull { it.rev }`.
  - Einkaufsansicht straffen, bis 75 Einträge < 30 KB (z. B. kürzere Klassen, `aria-label` nur einmal je Knopf, kein Zeilenumbruch-Markup).

- [ ] **Step 1: Tests**

```kotlin
@Test fun freitextDreiFaelle()             // „Milch“ neu (Abteilung wie Katalog), nochmal → abgehakt, nochmal → wieder offen
@Test fun freitextOhneListeLegtListeAn()
@Test fun loeschenUndNotiz()               // Eintrag gelöscht; Notiz gesetzt, leer → entfernt; > 1000 Zeichen → Meldung
@Test fun listeLoeschenMitBestaetigung()   // GET zeigt Bestätigung; POST löscht Liste und alle Einträge (Löschmarkierungen)
@Test fun rezeptAufListeAddiert()           // manueller offener Reis 500 g + Rezept 200 g → 700 g; Hinweis „1 Zutat hinzugefügt“ (Mehrzahl „N Zutaten hinzugefügt“)
@Test fun sonderzeichenEscaped()           // Freitext „<b>Brot</b>“ erscheint als &lt;b&gt;; 201 Zeichen → Meldung (Review Focus 4)
@Test fun groessen()                       // 75 Einträge: Ansicht < 30_000 Bytes, bearbeiten=1 < 50_000
@Test fun fremdeIdsSind404UndCsrf403()      // alle neuen POSTs
@Test fun inzwischenGeloeschtGibtHinweis()
```

- [ ] **Step 2:** Run `./gradlew :server-web:test --tests '*ListeBearbeitenTest*'` · Expected: FAIL
- [ ] **Step 3:** Implementieren.
- [ ] **Step 4:** Run `./gradlew :server-web:test` · Expected: PASS
- [ ] **Step 5: Commit** — `git commit -m "Web: Liste bearbeiten, Freitext, Rezept auf die Einkaufsliste"`

### Task 8: Ende-zu-Ende und Doku

**Files:**
- Modify: `android/app/src/test/java/de/foody/app/sync/SyncEndToEndTest.kt`
- Modify: `docs/architecture.md`, `server/README.md`

**Interfaces:**
- Consumes: Routen aus Task 4 und 6 über den im Prozess laufenden `TestSyncServer` (mountet schon `WebOberflaeche.routen(deps)`).

- [ ] **Step 1: Test** `planUndListeAusDemBrowserKommenAufsHandy`: Handy synchronisiert Rezept + Zutat; Browser plant ein (`POST /plan/einplanen`) und legt Liste an (`POST /einkauf/neu`); Handy synchronisiert → Planeintrag in Room; Listeneinträge gleich dem, was `ShoppingRepository.preview` auf dem Handy für denselben Zeitraum liefert (Zutat, Menge, Einheit).
- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest --tests '*SyncEndToEndTest*'` · Expected: PASS (wenn rot: Fehler suchen, nicht den Test anpassen)
- [ ] **Step 3:** Doku: Architektur (gemeinsame Regeln, `PlanSchreiber`/`EinkaufSchreiber`), README (`FOODY_ZEITZONE`, neue Seiten).
- [ ] **Step 4:** Run `./gradlew check` · Expected: BUILD SUCCESSFUL
- [ ] **Step 5: Commit** — `git commit -m "E2E: Plan und Liste aus dem Browser; Doku"`
