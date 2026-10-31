# Essenstracking (Tagebuch) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ein synchronisiertes Haushalts-Tagebuch mit Einträgen je Mahlzeit (aus Plan, Rezept, Katalog-Zutat oder frei) mit festgehaltenen Nährwerten, eigener Tab mit Wochenleiste; Vorrat wandert unter „Mehr“; Planer-Ø korrigiert.

**Architecture:** Rechenlogik (`Tagebuch`, `PlanDurchschnitt`) als reine Funktionen in `domain`. Room v8 mit Wurzeltabelle `tagebuch_eintrag` (lose Referenzen, Nährwerte als Snapshot). Neuer Sync-Typ `FOOD_LOG` durch alle `when (type)`-Stellen; Sicherung erweitert. UI: `TagebuchScreen`/`TagebuchViewModel` nach dem Muster von Planer und Rezeptliste.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room 2 + `MigrationTestHelper`, Hilt, kotlinx.serialization, Ktor-Server (`server`), kotlin.test.

**Spec:** `docs/superpowers/specs/2026-10-10-essenstracking-design.md`

## Global Constraints

- Neue Bezeichner auf Deutsch (`Tagebuch`, `TagebuchEintragEntity`, `TagebuchArt { REZEPT, ZUTAT, FREI }`, `Naehrwerte`, `TagesBilanz`, `PlanDurchschnitt`); bestehende englische Bezeichner bleiben.
- Mahlzeit immer als `Mahlzeit`-Enum-Name (Teil A).
- Referenzen `rezeptId`, `zutatId`, `planEintragId` **ohne** Fremdschlüssel.
- Nährwerte werden beim Speichern festgehalten, nie live neu berechnet.
- Portionen 0,5–10 in 0,5-Schritten, Standard 1. kJ = kcal × 4,184 (`NutritionResult.kjToKcal` für die Gegenrichtung).
- Tagesziel = `GoalPreferences.dailyKcal`.
- Keine destruktiven Migrationen; Migrationstest Pflicht.
- Build (Bash): `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`. Instrumentation: `emulator -avd Foody_Test -port 5556 -no-window -gpu host -no-snapshot`, dann je Klasse `ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.foody.app.<Test>` (eine Klasse je Aufruf; Kommalisten werden nicht übernommen). In Git-Bash für `adb shell` `MSYS_NO_PATHCONV=1` setzen.

## Review Focus

1. **„Gegessen“ zweimal schnell getippt** → genau ein Eintrag; der Vorschlag ist nach dem ersten Tipp weg. Test in Task 6.
2. **Rezept nach dem Eintragen bearbeitet oder gelöscht** → Eintrag zeigt unveränderte kcal. Test in Task 6.
3. **Zutat mit Einheit „Stück“ ohne Stückgewicht** → keine Werte, kein Absturz, Wechsel zu „Frei“ möglich. Test in Task 1 (`null`) und Task 6 (VM-Zustand).
4. **Sync: Tagebuch-Eintrag vom Server, dessen Rezept lokal fehlt** → wird trotzdem angewendet (keine Referenz-Prüfung). Test in Task 5.
5. **Wochenleiste an Monats-/Jahresgrenze** (gewählter Tag 2026-01-02) → 7 Tage 2025-12-27…2026-01-02. Test in Task 6.

---

### Task 1: `Tagebuch`-Rechenlogik

**Files:**
- Create: `domain/src/main/kotlin/de/foody/domain/Tagebuch.kt`
- Test: `domain/src/test/kotlin/de/foody/domain/TagebuchTest.kt`

**Interfaces:**
- Produces:
  - `enum class TagebuchArt { REZEPT, ZUTAT, FREI }`
  - `data class Naehrwerte(val energieKj: BigDecimal?, val eiweiss: BigDecimal?, val kohlenhydrate: BigDecimal?, val fett: BigDecimal?, val vollstaendig: Boolean)` mit `val kcal: Int?` (gerundet über `NutritionResult.kjToKcal`).
  - `data class EintragWerte(val mahlzeit: Mahlzeit, val werte: Naehrwerte)`
  - `data class Summe(val kcal: Int, val eiweiss: Int, val kohlenhydrate: Int, val fett: Int, val vollstaendig: Boolean)`
  - `data class TagesBilanz(val tag: Summe, val jeMahlzeit: Map<Mahlzeit, Summe>)` (nur Mahlzeiten mit Einträgen)
  - `object Tagebuch { fun naehrwerteRezept(rezept: Recipe, zutaten: Map<String, Ingredient>, portionen: BigDecimal): Naehrwerte; fun naehrwerteZutat(zutat: Ingredient, menge: BigDecimal, einheit: MeasureUnit): Naehrwerte?; fun bilanz(eintraege: List<EintragWerte>): TagesBilanz; fun <S> offeneVorschlaege(planEintraege: List<S>, id: (S) -> String, uebernommen: Set<String>): List<S> }`

- [ ] **Step 1: Write the failing test** `TagebuchTest` (Zutaten wie in `LineEnergyTest`: eigene `Ingredient`-Objekte mit `NutrientProfile(PER_100_G, …)`):
  - `rezeptPortionenSkalieren`: Rezept 2 Portionen aus 200 g Zutat mit 400 kJ/100 g → `naehrwerteRezept(…, 1)` = 400 kJ, `(…, BigDecimal("0.5"))` = 200 kJ, `(…, 2)` = 800 kJ; `vollstaendig` true.
  - `rezeptUnvollstaendig`: zweite Zeile mit Zutat ohne Nährwerte → `vollstaendig` false, Energie = Teilsumme.
  - `zutatGrammMlUndStueck`: 150 g bei 400 kJ/100 g → 600 kJ; 200 ml mit `PER_100_ML` 250 kJ → 500 kJ; 2 Stück mit `pieceWeightG` 50 und 400 kJ/100 g → 400 kJ.
  - `zutatNichtUmrechenbar`: 2 Stück ohne Stückgewicht → `null`; Zutat ohne `nutrients` → `null`.
  - `bilanzSummiertUndGibtLueckenWeiter`: zwei Einträge Frühstück (100 kcal, 200 kcal), einer Abend unvollständig (300 kcal) → `tag.kcal == 600`, `tag.vollstaendig == false`, `jeMahlzeit[FRUEHSTUECK].kcal == 300 && vollstaendig`, keine Taste `MITTAGESSEN`. Eintrag mit `energieKj == null` zählt 0 kcal und macht unvollständig.
  - `offeneVorschlaegeOhneUebernommene`: Plan-IDs a, b, c, übernommen {b} → [a, c] in Eingangsreihenfolge.
- [ ] **Step 2: Run** `./gradlew :domain:test --tests de.foody.domain.TagebuchTest` → FAIL (unresolved).
- [ ] **Step 3: Implement.** `naehrwerteRezept`: `NutritionCalculator.calculate(rezept, zutaten)`, `perServing(n) × portionen` für Energie/Eiweiß/KH/Fett, `vollstaendig = isComplete(ENERGY_KJ)`. `naehrwerteZutat`: einzeiliges `Recipe(id = "", name = zutat.name, defaultServings = 1, ingredients = listOf(RecipeIngredient("z", zutat.id, menge, einheit)))` durch denselben Rechner; `null`, wenn `missing` die Zutat enthält und Energie fehlt (`perServing(ENERGY_KJ) == null`). Makros in `Summe` mit `HALF_UP` auf ganze Gramm.
- [ ] **Step 4: Run** → PASS; `./gradlew :domain:test` → alle PASS.
- [ ] **Step 5: Commit** `git commit -m "Tagebuch: Nährwerte je Eintrag und Tagesbilanz berechnen"`

---

### Task 2: Planer-Durchschnitt

**Files:**
- Create: `domain/src/main/kotlin/de/foody/domain/PlanDurchschnitt.kt`
- Test: `domain/src/test/kotlin/de/foody/domain/PlanDurchschnittTest.kt`
- Modify: `app/src/main/java/de/foody/app/ui/planner/PlannerViewModel.kt` (`PlannerUiState.durchschnitt: Durchschnitt?`), `PlannerScreen.kt` (Zeile `key = "average"`), `app/src/main/res/values/strings.xml` (`planner_average` um „≥“-Präfix-Argument)

**Interfaces:**
- Produces: `data class PlanTag(val slotTypes: List<String>, val naehrwerte: DayNutrition)`, `data class Durchschnitt(val kcal: Int, val tage: Int, val vollstaendig: Boolean)`, `object PlanDurchschnitt { fun kcal(tage: List<PlanTag>): Durchschnitt? }`

- [ ] **Step 1: Write the failing test** `PlanDurchschnittTest`:
  - `nurAbendZaehltNicht`: ein Tag nur `ABENDESSEN` (2000 kcal) → `null`.
  - `abendPlusWeitereZaehlt`: Tag A `[ABENDESSEN, MITTAGESSEN]` 2000 kcal, Tag B `["Abendessen", FRUEHSTUECK]` 1000 kcal, Tag C `[MITTAGESSEN]` 500 → `Durchschnitt(1500, 2, true)`.
  - `unvollstaendigMarkiert`: ein gezählter Tag `complete = false` → `vollstaendig == false`.
- [ ] **Step 2: Run** `./gradlew :domain:test --tests de.foody.domain.PlanDurchschnittTest` → FAIL.
- [ ] **Step 3: Implement** – gezählt, wenn `Mahlzeit.ausText` mind. einmal `ABENDESSEN` liefert und `slotTypes.size >= 2`; Ø gerundet `HALF_UP`. ViewModel füllt `durchschnitt` aus `slotsByDay` + `dayNutrition`; Screen zeigt die Zeile nur bei `durchschnitt != null`, mit „≥ “ bei `!vollstaendig`.
- [ ] **Step 4: Run** domain-Tests → PASS; `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `git commit -m "Planer: Ø kcal nur über volle Tage, ≥ bei Lücken"`

---

### Task 3: Datenbank v8 und Repository

**Files:**
- Modify: `app/src/main/java/de/foody/app/data/db/Entities.kt` (neu `TagebuchEintragEntity`), `Daos.kt` (neu `TagebuchDao`), `FoodyDatabase.kt` (Entity, `version = 8`, `tagebuchDao()`, `MIGRATION_7_8`, `ALL_MIGRATIONS`), `SyncTriggers.kt` (`roots` + `"tagebuch_eintrag"`), `app/src/main/java/de/foody/app/di/*` (Provider für `TagebuchDao`, wie die übrigen DAOs)
- Create: `app/src/main/java/de/foody/app/data/repo/TagebuchRepository.kt`, `app/schemas/de.foody.app.data.db.FoodyDatabase/8.json` (Build)
- Test: `app/src/androidTest/java/de/foody/app/MigrationTest.kt`

**Interfaces:**
- Consumes: Task 1 (`TagebuchArt`).
- Produces:
  - `@Entity(tableName = "tagebuch_eintrag", indices = [Index("datum"), Index("planEintragId")]) data class TagebuchEintragEntity(id: String, datum: LocalDate, mahlzeit: String, art: TagebuchArt, name: String, rezeptId: String? = null, planEintragId: String? = null, portionen: BigDecimal? = null, zutatId: String? = null, menge: BigDecimal? = null, einheit: MeasureUnit? = null, energieKj: BigDecimal? = null, eiweiss: BigDecimal? = null, kohlenhydrate: BigDecimal? = null, fett: BigDecimal? = null, vollstaendig: Boolean = true, createdAt: Long, updatedAt: Long)` (Converter für `TagebuchArt` in `Converters`, wie `MeasureUnit`).
  - `TagebuchDao`: `observeRange(start: LocalDate, end: LocalDate): Flow<List<TagebuchEintragEntity>>`, `get(id)`, `@Upsert upsert(e)`, `delete(id)`, `getAll()`, `ids(): List<String>`, `existsForPlan(planEintragId: String): Boolean`.
  - `TagebuchRepository(db, dao)`: `observeRange`, `save(e)` (setzt `updatedAt`), `delete(id)`, `suspend fun uebernehmen(slotId: String, eintrag: TagebuchEintragEntity): Boolean` – in einer Transaktion: `existsForPlan` → `false`, sonst `upsert` → `true` (Review Focus 1).
- [ ] **Step 1: Write the failing test** `MigrationTest.migrate7To8AddsTagebuchWithTriggers`: v7 mit einem `meal_slot`; nach `MIGRATION_7_8`: Insert in `tagebuch_eintrag` (Pflichtspalten) gelingt, `index_tagebuch_eintrag_datum` existiert, Trigger-Namen = `SyncTriggers.names`, darin `sync_tagebuch_eintrag_ai/_au/_ad`; `meal_slot` unverändert.
- [ ] **Step 2: Run** (Instrumentation, Global Constraints) → FAIL (Kompilierung).
- [ ] **Step 3: Implement** – `MIGRATION_7_8`: `CREATE TABLE` exakt wie Room es für die Entity erzeugt (aus `8.json` übernehmen), beide Indizes, `SyncTriggers.drop(db)`, `SyncTriggers.create(db)`. Dann `./gradlew :app:assembleDebug` für `8.json`.
- [ ] **Step 4: Run** `MigrationTest` → PASS (alle Migrationen).
- [ ] **Step 5: Commit** inkl. `8.json`: `git commit -m "DB v8: Tabelle tagebuch_eintrag, Sync-Trigger"`

---

### Task 4: Sync-Protokoll und Server

**Files:**
- Modify: `sync-protocol/src/main/kotlin/de/foody/sync/protocol/Records.kt` (`@SerialName("food_log") FOOD_LOG("food_log")`), `Payloads.kt` (`TagebuchPayload`, `decode`), `PayloadValidator.kt` (`check` für `TagebuchPayload`)
- Test: `sync-protocol/src/test/kotlin/de/foody/sync/protocol/PayloadValidatorTest.kt`, `server/src/test/kotlin/de/foody/server/SyncTest.kt`

**Interfaces:**
- Produces: `@Serializable data class TagebuchPayload(val datum: String, val mahlzeit: String, val art: String, val name: String, val rezeptId: String? = null, val planEintragId: String? = null, val portionen: String? = null, val zutatId: String? = null, val menge: String? = null, val einheit: String? = null, val energieKj: String? = null, val eiweiss: String? = null, val kohlenhydrate: String? = null, val fett: String? = null, val vollstaendig: Boolean = true)`.
- Validator: `date(datum)`; `mahlzeit` in `{FRUEHSTUECK, MITTAGESSEN, ABENDESSEN, SNACK}`; `art` in `{REZEPT, ZUTAT, FREI}`; `name` nicht leer, ≤ `MAX_NAME`; gesetzte IDs `isValidId`; Zahlen parsebar und ≥ 0; `references` → leer (lose Referenzen).

- [ ] **Step 1: Write the failing tests**: `PayloadValidatorTest.tagebuchGueltigUndUngueltig` (gültiger Payload → `null`; `mahlzeit = "Brunch"` → Fehlercode; `energieKj = "-1"` → Fehlercode; `references` leer). `SyncTest.foodLogRoundTrip`: Push eines `FOOD_LOG`-Records → Pull liefert ihn mit gleichem Payload (Muster der vorhandenen Tests).
- [ ] **Step 2: Run** `./gradlew :sync-protocol:test :server:test` → FAIL.
- [ ] **Step 3: Implement.** Zusätzliche `when (type)`-Zweige, die der Compiler im Server verlangt, analog `MEAL_SLOT`.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `git commit -m "Sync-Protokoll: Typ food_log für das Tagebuch"`

---

### Task 5: App-Sync und Sicherung

**Files:**
- Modify: `app/src/main/java/de/foody/app/sync/SyncMapper.kt` (`tagebuch(e)`, `tagebuch(id, p, updatedAt, existing)`), `SyncApplier.kt` (`Mapped.Tagebuch`, `map`, Schreiben, Existenz, Löschen), `SyncEngine.kt` (IDs, `deleteStale`, `DELETE_ORDER` – `FOOD_LOG` vor `SHOPPING_ITEM`), `SyncLocalStore.kt` (`build`), `app/src/main/java/de/foody/app/data/db/SyncDao.kt` (`tagebuchIds()`), `BackupRepository.kt` (`BackupDto.tagebuch: List<Tagebuch> = emptyList()`, Export, Import, `deleteAll`), `docs/architecture.md` (DB v8, `food_log`, „Server vor App“)
- Test: `app/src/androidTest/java/de/foody/app/SyncApplierTest.kt`, `BackupZipTest.kt`

**Interfaces:**
- Consumes: Task 3 (Entity, DAO), Task 4 (Payload, `RecordType.FOOD_LOG`).

- [ ] **Step 1: Write the failing tests**:
  - `SyncApplierTest.tagebuchOhneLokalesRezeptWirdAngewendet` (Review Focus 4): `FOOD_LOG`-Record mit `rezeptId = "fehlt"`, `art = "REZEPT"`, `energieKj = "1000"` → `result.problems == 0`, Entity vorhanden, `energieKj == 1000`; `SyncMapper.tagebuch(entity)` ergibt denselben Payload.
  - `SyncApplierTest.tagebuchLoeschen`: Lösch-Record entfernt die Zeile.
  - `BackupZipTest.tagebuchImBackup`: Eintrag speichern, exportieren, `deleteAll`, importieren → gleiche Werte; Sicherung ohne `tagebuch`-Schlüssel (Legacy-JSON-Muster aus `legacyJsonBackupStillImports`) importiert ohne Fehler.
- [ ] **Step 2: Run** beide Klassen → FAIL.
- [ ] **Step 3: Implement** – alle `when (type)`-Stellen; der Compiler meldet fehlende Zweige.
- [ ] **Step 4: Run** `SyncApplierTest`, `BackupZipTest`, `SyncLocalStoreTest`, `SyncTriggersTest` → PASS.
- [ ] **Step 5: Commit** `git commit -m "Sync/Backup: Tagebuch übertragen und sichern"`

---

### Task 6: `TagebuchViewModel`

**Files:**
- Create: `app/src/main/java/de/foody/app/ui/tagebuch/TagebuchViewModel.kt`
- Test: `app/src/androidTest/java/de/foody/app/TagebuchViewModelTest.kt`

**Interfaces:**
- Consumes: Task 1, Task 3 (`TagebuchRepository`), `PlanRepository`, `RecipeRepository`, `IngredientRepository`, `GoalPreferences`, Teil A (`Mahlzeit`, `RecipeEntity.einordnung()`).
- Produces:
  - `data class TagebuchUiState(val tag: LocalDate, val woche: List<Pair<LocalDate, Int>>, val ziel: Int?, val bilanz: TagesBilanz, val eintraege: Map<Mahlzeit, List<TagebuchEintragEntity>>, val vorschlaege: Map<Mahlzeit, List<Pair<MealSlotEntity, RecipeEntity>>>, val rezepte: List<RecipeEntity>, val zutaten: List<IngredientEntity>, val loading: Boolean)`
  - Aktionen: `zeigeTag(d: LocalDate)`, `verschiebe(tage: Long)`, `heute()`, `gegessen(slot: MealSlotEntity, portionen: BigDecimal = ONE)`, `rezeptEintragen(m: Mahlzeit, rezeptId: String, portionen: BigDecimal)`, `zutatVorschau(zutatId: String, menge: BigDecimal, einheit: MeasureUnit): Naehrwerte?`, `zutatEintragen(m, zutatId, menge, einheit): Boolean` (false = nicht berechenbar), `freiEintragen(m, name: String, kcal: Int, eiweiss: BigDecimal?, kohlenhydrate: BigDecimal?, fett: BigDecimal?)`, `bearbeiten(e: TagebuchEintragEntity)` (rechnet bei REZEPT/ZUTAT neu aus aktueller Menge/Portionen, sonst übernimmt Werte), `loeschen(id: String)`.
  - Vorschlags-Mahlzeit: `Mahlzeit.ausText(slotType) ?: ABENDESSEN`. Tag liegt in `SavedStateHandle["tag"]` (Epoch-Day).
- [ ] **Step 1: Write the failing test** `TagebuchViewModelTest` (Setup wie `PlannerSuggestTest`; Rezept „Curry“ 2 Portionen aus 200 g Reis mit 1464 kJ/100 g → 1464 kJ ≈ 350 kcal je Portion):
  - `gegessenUebernimmtVorschlagEinmal` (Focus 1): Slot heute `ABENDESSEN`; `gegessen(slot)` zweimal direkt hintereinander → genau ein Eintrag mit `planEintragId == slot.id`, `art == REZEPT`, kcal 350; `vorschlaege[ABENDESSEN]` leer.
  - `festgehaltenNachRezeptAenderung` (Focus 2): nach `gegessen` Rezept auf 4 Portionen speichern und danach löschen → Eintrag kcal weiterhin 350.
  - `zutatOhneStueckgewicht` (Focus 3): `zutatEintragen(SNACK, reis, 2, PIECE)` → `false`, kein Eintrag; mit 100 g → `true`, kcal 350.
  - `freiEintrag`: „Apfel“, 80 kcal → Bilanz Snack 80.
  - `wocheUeberJahresgrenze` (Focus 5): `zeigeTag(2026-01-02)` → `woche.map { it.first }` = 2025-12-27 … 2026-01-02.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement.** Wochenwerte aus `observeRange(tag - 6, tag)`; `gegessen` über `TagebuchRepository.uebernehmen`.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `git commit -m "Tagebuch: ViewModel mit Vorschlägen aus dem Plan und Einträgen"`

---

### Task 7: Tagebuch-Oberfläche und Navigation

**Files:**
- Create: `app/src/main/java/de/foody/app/ui/tagebuch/TagebuchScreen.kt` (Bildschirm, Wochenleiste, Abschnitte), `TagebuchDialoge.kt` (Hinzufügen mit Reitern Rezept/Zutat/Frei, Bearbeiten, Portionen)
- Modify: `app/src/main/java/de/foody/app/ui/FoodyRoot.kt` (`TagebuchRoute`, Tab `TAGEBUCH` an Stelle von `PANTRY`, `composable<PantryRoute>` mit `onBack`), `PantryScreen.kt` (`onBack: () -> Unit`, Zurück-Aktion im Kopf), `SettingsScreen.kt` (`onOpenPantry: () -> Unit`, Knopf „Vorrat“ neben „Zutaten & Nährwerte“), `strings.xml`, `docs/domain-rules.md`, `ToDoS/einkaufsliste-und-essenstracking.md`, Create `docs/adr/0007-tagebuch-festgehaltene-naehrwerte.md`
- Strings (neu): `nav_tagebuch` „Tagebuch“, `tagebuch_eyebrow` „TAGEBUCH“, `tagebuch_leer` „Noch nichts eingetragen. Tippe auf + bei einer Mahlzeit.“, `tagebuch_geplant` „Geplant: %1$s“, `tagebuch_gegessen` „Gegessen“, `tagebuch_hinzufuegen` „Hinzufügen“, `tagebuch_tab_rezept` „Rezept“, `tagebuch_tab_zutat` „Zutat“, `tagebuch_tab_frei` „Frei“, `tagebuch_keine_werte` „Keine Nährwerte – frei eintragen?“, `tagebuch_summe` „%1$s kcal“, `tagebuch_summe_ziel` „%1$s / %2$d kcal“, `tagebuch_makros` „E %1$d g · K %2$d g · F %3$d g“, `settings_vorrat` „Vorrat“.

**Interfaces:**
- Consumes: Task 6 (State und Aktionen), Teil A (`EinordnungChips`-Farben, `PlanAuswahl.filtern`, `Mahlzeit.label()`), vorhandene `ScreenHeader`, `HeaderAction`, `ServingsStepper` (für 0,5er eigener Stepper `PortionenStepper(wert: BigDecimal, onChange)` in `TagebuchDialoge.kt`).

- [ ] **Step 1: Write the failing test** `TagebuchScreenTest` (`createComposeRule`, VM wie Task 6): Abschnittstitel „Frühstück“, „Mittagessen“, „Snack“, „Abendessen“ sichtbar; geplanter Slot zeigt „Geplant: Curry“; Klick auf „Gegessen“ → Karte verschwindet, Zeile „Curry“ mit „350 kcal“ erscheint.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** Bildschirm, Dialoge, Navigation, Vorrat unter „Mehr“, Doku (ADR 0007 mit Alternativen ② live und ③ Plan-Flag; Regeln 24–25 in `domain-rules.md`; ToDo-Abschnitte 2, 4 und 7 als erledigt).
- [ ] **Step 4: Run** `TagebuchScreenTest` → PASS; volle Suite `./gradlew :domain:test :sync-protocol:test :server:test :app:testDebugUnitTest` und alle Instrumentation-Tests (`connectedDebugAndroidTest` ohne Klassenfilter) → PASS. Sichtprüfung im Emulator (Korpus nach `/sdcard/Download/Ideen` pushen, importieren): Tab-Leiste, Tagebuch hell/dunkel, Wochenleiste, Dialog alle drei Reiter, „Vorrat“ unter „Mehr“ mit Zurück.
- [ ] **Step 5: Commit** `git commit -m "Tagebuch: eigener Tab mit Wochenleiste, Vorrat unter „Mehr“"`
