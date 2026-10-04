# Sync Etappe 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die App erfasst jede lokale Änderung zuverlässig für den Sync (Outbox), kann daraus Push-Datensätze bauen und vom Server gezogene Datensätze konfliktsicher anwenden – noch ohne Netzwerk und ohne UI.

**Architecture:** Room-Migration v3 → v4 legt Sync-Tabellen an. Lokale Änderungen landen über **SQLite-Trigger** in `sync_outbox` – in derselben Transaktion wie die Änderung und unabhängig davon, welches Repository schreibt (Editor, Import, Zusammenführen, Startdaten, Wiederherstellen). Trigger feuern nur bei aktivem Sync und nicht, während Server-Daten angewendet werden. `SyncMapper` übersetzt Entities ↔ `:sync-protocol`-Payloads, `SyncLocalStore` liefert Push-Datensätze, `SyncApplier` wendet Pull-Ergebnisse an. Dazu eine kleine Server-Änderung (201 Created).

**Tech Stack:** Kotlin, Room 2.8.5 (SupportSQLite-Migrationen, `RoomDatabase.Callback`), kotlinx.serialization, `:sync-protocol` aus Etappe 1, Ktor-Server (nur Task 1), JUnit (JVM) und AndroidX-Instrumented-Tests.

**Spec:** `docs/superpowers/specs/2026-10-04-sync-server-design.md` (§3.3, §4.1–4.5, §8 Etappe 2). Entscheidungen aus Issue #59: 201 Created für neue Haushalte/Einladungen.

## Abweichungen von der Spec (bewusst)

- **Migration heißt v3 → v4** (main ist bei Room-Version 3).
- **Outbox per Trigger statt in jedem Repository** (Spec 4.5 „Repositories schreiben Outbox-Einträge“): gleiche Garantie (selbe Transaktion), aber keine Schreibstelle kann vergessen werden.
- **`baseRev` wird beim Push aus `sync_record_rev` gelesen**, nicht in der Outbox gespeichert. Gleichwertig, weil der Pull einen Datensatz mit offenem Outbox-Eintrag nicht anfasst und seine Revision dann auch nicht ändert.
- **Pull wird komplett gesammelt und in einer Transaktion angewendet** (statt seitenweise). Der Server liefert je Datensatz nur die neueste Revision; ein Rezept auf Seite 1 kann auf eine Zutat zeigen, die erst auf Seite 2 kommt. Haushaltsmengen sind klein (Hunderte bis wenige Tausend Datensätze).
- **Fotos bleiben in Etappe 2 außen vor:** `RecipePayload.photo` wird mit `null` gesendet und beim Anwenden ignoriert (lokales `imageUri` bleibt).

## Global Constraints

- Room-Version **4**; Migration `MIGRATION_3_4` in `ALL_MIGRATIONS`; Schema-Export `app/schemas/.../4.json` eingecheckt; kein destruktiver Fallback; Migrationstest Pflicht.
- Neue Tabellen exakt: `sync_outbox(type TEXT NOT NULL, recordId TEXT NOT NULL, deleted INTEGER NOT NULL, queuedAt INTEGER NOT NULL, PRIMARY KEY(type, recordId))`, `sync_record_rev(type TEXT NOT NULL, recordId TEXT NOT NULL, rev INTEGER NOT NULL, PRIMARY KEY(type, recordId))`, `sync_state(id INTEGER NOT NULL PRIMARY KEY, active INTEGER NOT NULL DEFAULT 0, applyingRemote INTEGER NOT NULL DEFAULT 0, serverUrl TEXT, householdId TEXT, cursor INTEGER NOT NULL DEFAULT 0, lastSyncAt INTEGER, lastError TEXT)` mit genau einer Zeile `id = 1`, `sync_problem(type TEXT NOT NULL, recordId TEXT NOT NULL, code TEXT NOT NULL, at INTEGER NOT NULL, PRIMARY KEY(type, recordId))`.
- `shopping_item` neue Spalten `updatedAt INTEGER NOT NULL DEFAULT 0`, `checkedChangedAt INTEGER NOT NULL DEFAULT 0`.
- `type` in Sync-Tabellen = `RecordType.wire` (`ingredient`, `recipe`, `meal_slot`, `pantry_item`, `shopping_list`, `shopping_item`).
- Trigger-Namen beginnen mit `sync_`; Bedingung „aktiv“ = `(SELECT active = 1 AND applyingRemote = 0 FROM sync_state WHERE id = 1)`; Zeitstempel in Triggern = `CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)` (Epoch-ms).
- Zeilen von Kindtabellen (`recipe_ingredient`, `instruction_step` → `recipe`; `shopping_item_source` → `shopping_item`) erzeugen einen Outbox-Eintrag für den **Elterndatensatz** – nur wenn der Elterndatensatz noch existiert.
- Zahlen in Payloads `BigDecimal.toPlainString()`, Datumswerte ISO-8601, Einheiten `MeasureUnit.name`, Nährwertbasis `NutrientBasis.name` – wie `BackupRepository`.
- Push-Batch höchstens `Protocol.MAX_PUSH_RECORDS` (500), Reihenfolge = `RecordType`-Reihenfolge (Abhängigkeiten zuerst), Löschungen nach allen lebenden Datensätzen in umgekehrter Reihenfolge.
- Keine `INTERNET`-Berechtigung in dieser Etappe; kein UI; Sync bleibt ohne `activate(...)` vollständig inaktiv.
- Code-Stil: deutsche KDoc, englische Bezeichner, `allWarningsAsErrors` wo im Modul gesetzt.

## Review Focus

1. **Nutzer ohne Server** (Normalfall heute): Sync nie aktiviert → jede Bearbeitung erzeugt **keine** Outbox-Zeile, App unverändert schnell → Test in Task 2.
2. **Rezept löschen**: Room kaskadiert Zutatenzeilen, Schritte und Planpositionen → Outbox enthält `recipe` gelöscht **und** die Planpositionen gelöscht, aber kein „lebendes“ Rezept → Test in Task 2.
3. **App-Update mit vorhandener Einkaufsliste**: Migration behält alle Einträge, neue Spalten stehen auf 0 → Test in Task 2.
4. **Offline bearbeitetes Rezept, gleichzeitig Server-Version im Pull**: lokale Version bleibt, `sync_record_rev` unverändert (der Push klärt später) → Test in Task 5.
5. **Zwei Geräte legen offline „Zwiebel“ bzw. „zwiebel“ an**: Pull bringt die Server-Zutat → lokale wird zusammengeführt, kein Absturz am eindeutigen Namensindex → Test in Task 5.

---

## Dateistruktur

```
server/src/main/kotlin/de/foody/server/household/HouseholdRoutes.kt   (Task 1: 201)
server/src/test/kotlin/de/foody/server/HouseholdTest.kt               (Task 1)
app/build.gradle.kts                          (implementation(project(":sync-protocol")))
app/src/main/java/de/foody/app/data/db/
    SyncEntities.kt      SyncOutboxEntity, SyncRecordRevEntity, SyncStateEntity, SyncProblemEntity
    SyncDao.kt           Zugriff auf Sync-Tabellen
    SyncTriggers.kt      Trigger-SQL + Callback
    FoodyDatabase.kt     (Version 4, Entities, syncDao(), MIGRATION_3_4)
    Entities.kt          (ShoppingItemEntity: updatedAt, checkedChangedAt)
app/src/main/java/de/foody/app/di/DatabaseModule.kt   (Callback registrieren)
app/src/main/java/de/foody/app/data/repo/IngredientMerge.kt  (mergeInto aus IngredientRepository herausgelöst)
app/src/main/java/de/foody/app/sync/
    SyncMapper.kt        Entity ↔ Payload
    SyncLocalStore.kt    activate/deactivate, enqueueAll, pendingRecords
    SyncApplier.kt       Pull-Ergebnisse anwenden
app/src/test/java/de/foody/app/sync/SyncMapperTest.kt
app/src/androidTest/java/de/foody/app/SyncTestDb.kt (Hilfsfunktion), SyncTriggersTest.kt, SyncLocalStoreTest.kt, SyncApplierTest.kt
app/src/androidTest/java/de/foody/app/MigrationTest.kt (erweitert)
docs/architecture.md (Datenbanktabelle v4, Abschnitt Sync)
```

**Testumgebung für Instrumented-Tests:** separates schreibgeschütztes Emulator-Image, damit Nutzerdaten erhalten bleiben:
`emulator -avd Medium_Phone -read-only -port 5558 -no-window -gpu swiftshader_indirect -no-snapshot -no-audio -no-boot-anim` (im Hintergrund), dann `ANDROID_SERIAL=emulator-5558 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.foody.app.<Klasse>`, danach `adb -s emulator-5558 emu kill`. Läuft auf 5558 schon ein Emulator, wiederverwenden.

---

### Task 1: Server – 201 Created für neue Haushalte und Einladungen

**Files:**
- Modify: `server/src/main/kotlin/de/foody/server/household/HouseholdRoutes.kt`
- Test: `server/src/test/kotlin/de/foody/server/HouseholdTest.kt`

**Interfaces:**
- Produces: `POST /api/v1/households` und `POST /api/v1/invites` antworten mit **201** und unverändertem Body (`HouseholdDto` bzw. `InviteDto`).

- [ ] **Step 1: Tests anpassen/ergänzen:** `createHouseholdReturns201` (Status 201, Body enthält `name` und Rolle `owner`), `createInviteReturns201` (Status 201, `code` matcht `^FOODY-…`); bestehende Tests, die 200 erwarten, auf 201 umstellen.
- [ ] **Step 2: Run** `./gradlew :server:test --tests '*HouseholdTest'` → FAIL (200 ≠ 201).
- [ ] **Step 3: Implementieren:** `call.respond(HttpStatusCode.Created, …)` an beiden Stellen.
- [ ] **Step 4: Run** `./gradlew :server:test` → PASS.
- [ ] **Step 5: Commit** `git commit -m "Sync-Server: 201 Created für neue Haushalte und Einladungen"`

---

### Task 2: Migration v4, Sync-Tabellen, Trigger

**Files:**
- Create: `app/src/main/java/de/foody/app/data/db/SyncEntities.kt`, `SyncDao.kt`, `SyncTriggers.kt`
- Modify: `FoodyDatabase.kt`, `Entities.kt` (ShoppingItemEntity), `di/DatabaseModule.kt`, `app/build.gradle.kts`, `docs/architecture.md`
- Create (Test): `app/src/androidTest/java/de/foody/app/SyncTestDb.kt`, `SyncTriggersTest.kt`
- Modify (Test): `app/src/androidTest/java/de/foody/app/MigrationTest.kt`

**Interfaces:**
- Produces:
  - Entities (Tabellen/Spalten exakt wie Global Constraints): `SyncOutboxEntity(type: String, recordId: String, deleted: Boolean, queuedAt: Long)`, `SyncRecordRevEntity(type: String, recordId: String, rev: Long)`, `SyncStateEntity(id: Int = 1, active: Boolean = false, applyingRemote: Boolean = false, serverUrl: String? = null, householdId: String? = null, cursor: Long = 0, lastSyncAt: Long? = null, lastError: String? = null)`, `SyncProblemEntity(type: String, recordId: String, code: String, at: Long)`.
  - `ShoppingItemEntity` + `val updatedAt: Long = 0`, `val checkedChangedAt: Long = 0` (`@ColumnInfo(defaultValue = "0")`).
  - `@Dao interface SyncDao`: `getState(): SyncStateEntity?`, `@Upsert upsertState(s)`, `@Query("UPDATE sync_state SET applyingRemote = :on WHERE id = 1") setApplyingRemote(on: Boolean)`, `outbox(): List<SyncOutboxEntity>`, `isQueued(type: String, id: String): Boolean`, `@Upsert enqueue(e: SyncOutboxEntity)`, `dequeue(type: String, id: String)`, `clearOutbox()`, `revOf(type: String, id: String): Long?`, `@Upsert setRev(e: SyncRecordRevEntity)`, `@Upsert addProblem(p: SyncProblemEntity)`, `problems(): List<SyncProblemEntity>` – alle `suspend`.
  - `object SyncTriggers { val statements: List<String>; fun create(db: SupportSQLiteDatabase) }` – `CREATE TRIGGER IF NOT EXISTS …` für:
    - Wurzeltabellen `ingredient`, `recipe`, `meal_slot`, `pantry_item`, `shopping_list`, `shopping_item`: `AFTER INSERT` und `AFTER UPDATE` → `INSERT OR REPLACE INTO sync_outbox VALUES('<type>', NEW.id, 0, <now>)`; `AFTER DELETE` → `(…, OLD.id, 1, <now>)`; jeweils `WHEN <aktiv>`.
    - Kindtabellen: `INSERT OR REPLACE INTO sync_outbox SELECT '<parentType>', <NEW|OLD>.<fk>, 0, <now> WHERE EXISTS (SELECT 1 FROM <parent> WHERE id = <NEW|OLD>.<fk>)` für INSERT/UPDATE/DELETE, `WHEN <aktiv>`.
    - `shopping_item`-Pflege (unabhängig von `active`, aber nicht bei `applyingRemote = 1`; fehlt die Zeile in `sync_state`, gilt „nicht anwenden“ = 0): `AFTER UPDATE OF checked … WHEN OLD.checked <> NEW.checked` setzt `checkedChangedAt = <now>`; `AFTER UPDATE` mit `WHEN NEW.updatedAt = OLD.updatedAt` setzt `updatedAt = <now>`; `AFTER INSERT WHEN NEW.updatedAt = 0` setzt `updatedAt = <now>`.
  - `val FoodyDatabase.Companion.SYNC_CALLBACK: RoomDatabase.Callback` (bzw. `object SyncCallback`) – `onCreate`: `INSERT OR IGNORE INTO sync_state(id) VALUES (1)` + `SyncTriggers.create(db)`.
  - `MIGRATION_3_4`: legt Tabellen und Spalten an, fügt Zeile `sync_state(id=1)` ein, ruft `SyncTriggers.create`. `ALL_MIGRATIONS` ergänzt.
  - Testhilfe `fun syncTestDb(): FoodyDatabase` – In-Memory-Room mit `addCallback(SYNC_CALLBACK)`; `suspend fun FoodyDatabase.activateSyncForTest()` setzt `active = 1`.
  - `app/build.gradle.kts`: `implementation(project(":sync-protocol"))`.

- [ ] **Step 1: Failing tests** in `SyncTriggersTest.kt` (Instrumented, `syncTestDb()`):
  - `inactiveSyncWritesNoOutbox` – ohne Aktivierung Zutat, Rezept (über `RecipeRepository.save`), Planposition, Vorrat, Einkaufsliste + Eintrag anlegen und ändern → `syncDao().outbox()` leer (Review Focus 1).
  - `rootWritesAreQueued` – aktiv: Zutat anlegen → `(ingredient, id, deleted=false)`; ändern → weiterhin genau eine Zeile; löschen → `deleted=true`.
  - `childWritesQueueTheParent` – aktiv: Rezept mit 2 Zeilen + 1 Schritt speichern, Outbox leeren, nur einen Schritt per DAO ändern → genau `(recipe, rid, false)`; analog `shopping_item_source` → `(shopping_item, iid, false)`.
  - `deletingRecipeQueuesRecipeAndCascadedSlots` – aktiv: Rezept + 2 Planpositionen, Outbox leeren, Rezept löschen → `(recipe, rid, true)`, beide `(meal_slot, sid, true)`, keine Zeile `(recipe, rid, false)` (Review Focus 2).
  - `applyingRemoteSuppressesQueue` – aktiv + `setApplyingRemote(true)`: Zutat anlegen → Outbox leer; danach `false` → nächste Änderung erscheint.
  - `checkedChangeStampsTime` – Eintrag anlegen (`checkedChangedAt = 0`), `ShoppingDao.setChecked(id, true)` → `checkedChangedAt > 0`; `setNote` → `checkedChangedAt` unverändert, `updatedAt` erhöht; bei `applyingRemote = 1` gesetztes `checked` behält das mitgelieferte `checkedChangedAt`.
- [ ] **Step 2: Failing tests** in `MigrationTest.kt`: `migrate3To4KeepsShoppingItems` – v3-DB mit Liste + 2 Einträgen (einer abgehakt) → nach Migration beide vorhanden, `updatedAt = 0`, `checkedChangedAt = 0`, `sync_state` hat genau Zeile `id=1` mit `active=0`, Trigger existieren (`SELECT count(*) FROM sqlite_master WHERE type='trigger' AND name LIKE 'sync_%'` > 0) (Review Focus 3); `MigrationTestHelper.runMigrationsAndValidate(…, 4, true, MIGRATION_3_4)` validiert gegen das exportierte Schema.
- [ ] **Step 3: Run** (Emulator siehe oben) `…connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.foody.app.SyncTriggersTest` → FAIL (Kompilierfehler/fehlende Tabellen).
- [ ] **Step 4: Implementieren** nach Interfaces; Datenbankversion 4, Schema exportieren (Build erzeugt `app/schemas/de.foody.app.data.db.FoodyDatabase/4.json`), `DatabaseModule` registriert `SYNC_CALLBACK`. `docs/architecture.md`: Tabellenzeile „4 | Sync-Tabellen (`sync_outbox`, `sync_record_rev`, `sync_state`, `sync_problem`), `shopping_item.updatedAt/checkedChangedAt`, Outbox-Trigger“ und ein kurzer Absatz „Sync (vorbereitet)“: Trigger-Prinzip, inaktiv bis `activate`.
- [ ] **Step 5: Run** `./gradlew :app:testDebugUnitTest :domain:test` und die Instrumented-Klassen `SyncTriggersTest`, `MigrationTest`, dazu einmal die gesamte `connectedDebugAndroidTest`-Suite (bestehende Tests dürfen nicht brechen) → PASS.
- [ ] **Step 6: Commit** `git commit -m "Sync: Datenbank v4 mit Outbox-Triggern"`

---

### Task 3: `SyncMapper` – Entities ↔ Payloads

**Files:**
- Create: `app/src/main/java/de/foody/app/sync/SyncMapper.kt`
- Test: `app/src/test/java/de/foody/app/sync/SyncMapperTest.kt` (JVM)

**Interfaces:**
- Consumes: Entities (Task 2), Payloads aus `de.foody.sync.protocol`
- Produces (in `object SyncMapper`):
  - `fun ingredient(e: IngredientEntity): IngredientPayload`; `fun ingredient(id: String, p: IngredientPayload, updatedAt: Long, existing: IngredientEntity?): IngredientEntity` (`createdAt`/`version` aus `existing`, sonst `updatedAt`/1; `name` ↔ `canonicalName`, `source` ↔ `nutrientSource`).
  - `fun recipe(r: RecipeEntity, lines: List<RecipeIngredientEntity>, steps: List<InstructionStepEntity>): RecipePayload` (`photo = null`, `prep` ↔ `prepMinutes`, `cook` ↔ `cookMinutes`, `servings` ↔ `defaultServings`, Zeilen-`note` ↔ `preparationNote`); `fun recipe(id: String, p: RecipePayload, updatedAt: Long, existing: RecipeEntity?): RecipeParts` mit `data class RecipeParts(val recipe: RecipeEntity, val lines: List<RecipeIngredientEntity>, val steps: List<InstructionStepEntity>)` – `imageUri` aus `existing` (Fotos: Etappe 4).
  - `fun mealSlot(e: MealSlotEntity): MealSlotPayload` / `fun mealSlot(id, p, updatedAt, existing): MealSlotEntity`
  - `fun pantryItem(e): PantryItemPayload` / `fun pantryItem(id, p, updatedAt, existing): PantryItemEntity`
  - `fun shoppingList(e): ShoppingListPayload` (`version` ↔ `generationVersion`, `start`/`end` ↔ `rangeStart`/`rangeEnd`) / `fun shoppingList(id, p, updatedAt, existing): ShoppingListEntity`
  - `fun shoppingItem(i: ShoppingItemEntity, sources: List<ShoppingItemSourceEntity>): ShoppingItemPayload` / `fun shoppingItem(id, p, updatedAt, existing): ShoppingItemParts` mit `data class ShoppingItemParts(val item: ShoppingItemEntity, val sources: List<ShoppingItemSourceEntity>)` – `checkedChangedAt` wird übernommen.
  - `fun toJson(payload: Any): JsonObject` (über `Protocol.json` und den passenden Serializer; unbekannter Typ → `IllegalArgumentException`).

- [ ] **Step 1: Failing tests** in `SyncMapperTest.kt`:
  - `ingredientRoundTrip` – Entity mit allen Nährwerten (`BigDecimal("0.15")`, `NutrientBasis.PER_100_ML`) → Payload (`energyKj = "1460"`, `basis = "PER_100_ML"`) → Entity: gleiche Felder, `createdAt`/`version` aus `existing`.
  - `recipeRoundTripKeepsLocalImage` – Rezept mit `imageUri = "file:/x.jpg"`, 2 Zeilen, 2 Schritten → Payload `photo == null`; zurück mit `existing` → `imageUri` bleibt `"file:/x.jpg"`, Zeilen/Schritte gleich (inkl. IDs, `sortOrder`, `optional`, `preparationNote`).
  - `shoppingItemRoundTripKeepsCheckStamp` – `checked = true, checkedChangedAt = 1234`, 1 Quelle → Payload und zurück gleich.
  - `datesAndUnitsUseWireFormats` – `MealSlotEntity(date = 2026-10-05)` → `date == "2026-10-05"`; Vorrat `MeasureUnit.TABLESPOON` → `"TABLESPOON"`; Payload `PayloadValidator.validate(SyncRecord(..., payload = toJson(p)))` ist `null` für jedes erzeugte Payload.
  - `newEntityFromRemoteUsesUpdatedAtAsCreatedAt` – `existing = null` → `createdAt == updatedAt`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*SyncMapperTest'` → FAIL.
- [ ] **Step 3: Implementieren.**
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest` → PASS.
- [ ] **Step 5: Commit** `git commit -m "Sync: Abbildung zwischen Datenbank und Protokoll"`

---

### Task 4: `SyncLocalStore` – Aktivieren und Push-Datensätze bauen

**Files:**
- Create: `app/src/main/java/de/foody/app/sync/SyncLocalStore.kt`
- Test: `app/src/androidTest/java/de/foody/app/SyncLocalStoreTest.kt`

**Interfaces:**
- Consumes: `SyncDao`, DAOs, `SyncMapper` (Task 2/3)
- Produces: `@Singleton class SyncLocalStore @Inject constructor(private val db: FoodyDatabase)` mit
  - `suspend fun activate(serverUrl: String, householdId: String, uploadExisting: Boolean)` – setzt `sync_state` (`active = 1`, URL, Haushalt, `cursor = 0`); bei `uploadExisting` danach `enqueueAll()`; alles in einer Transaktion.
  - `suspend fun deactivate()` – `active = 0`, Outbox, `sync_record_rev`, `sync_problem` leeren, `cursor = 0`, URL/Haushalt `null`.
  - `suspend fun enqueueAll()` – je Wurzeldatensatz aller sechs Typen ein Outbox-Eintrag `deleted = false`.
  - `suspend fun pendingRecords(limit: Int = Protocol.MAX_PUSH_RECORDS): List<SyncRecord>` – liest die Outbox; lebende Einträge in `RecordType`-Reihenfolge (innerhalb eines Typs nach `queuedAt`), danach Löschungen in umgekehrter Typreihenfolge; je Eintrag `SyncRecord(id, type, deleted, updatedAt, baseRev = revOf(...), payload = …)`. Lebender Eintrag, dessen Zeile inzwischen fehlt → als Löschung senden. `updatedAt` = `updatedAt` der Entity (Rezept/Zutat/Plan/Vorrat/Liste/Eintrag), für Löschungen `queuedAt`. Höchstens `limit` Einträge.

- [ ] **Step 1: Failing tests** in `SyncLocalStoreTest.kt`:
  - `activateWithUploadQueuesEverything` – 2 Zutaten, 1 Rezept, 1 Planposition, 1 Vorrat, 1 Liste mit 1 Eintrag → `activate(…, uploadExisting = true)` → Outbox hat 7 Zeilen, alle `deleted = false`.
  - `activateWithoutUploadQueuesNothing` – gleiche Daten, `uploadExisting = false` → Outbox leer; danach eine Änderung → 1 Zeile.
  - `pendingRecordsAreInDependencyOrder` – Outbox mit Einkaufseintrag, Rezept, Zutat (in dieser Einfügereihenfolge) → `pendingRecords().map { it.type }` = `[INGREDIENT, RECIPE, SHOPPING_ITEM]`; jedes Payload besteht `PayloadValidator.validate`.
  - `deletionsComeLastInReverseOrder` – Zutat und Rezept löschen, eine Liste anlegen → lebende zuerst, dann `[RECIPE (deleted), INGREDIENT (deleted)]`, Löschungen ohne Payload.
  - `baseRevComesFromRecordRev` – `setRev(recipe, rid, 42)`, Rezept ändern → `pendingRecords().single().baseRev == 42`; ohne Eintrag → `null`.
  - `vanishedRowIsSentAsDeletion` – Outbox-Zeile `(recipe, rid, false)`, Rezept per SQL ohne Trigger entfernen (vorher `setApplyingRemote(true)`) → Datensatz `deleted = true`.
  - `limitIsRespected` – 3 Zutaten, `pendingRecords(limit = 2)` → 2 Datensätze.
  - `deactivateClearsSyncTables` – nach `activate` + Änderungen + `setRev` → `deactivate()` → Outbox, Revisionen, Probleme leer, `active = false`.
- [ ] **Step 2: Run** Instrumented `de.foody.app.SyncLocalStoreTest` → FAIL.
- [ ] **Step 3: Implementieren.**
- [ ] **Step 4: Run** `SyncLocalStoreTest` → PASS.
- [ ] **Step 5: Commit** `git commit -m "Sync: Outbox aktivieren und Push-Datensätze bauen"`

---

### Task 5: `SyncApplier` – Pull-Ergebnisse anwenden

**Files:**
- Create: `app/src/main/java/de/foody/app/data/repo/IngredientMerge.kt`, `app/src/main/java/de/foody/app/sync/SyncApplier.kt`
- Modify: `app/src/main/java/de/foody/app/data/repo/Repositories.kt` (IngredientRepository nutzt `mergeIngredient`)
- Test: `app/src/androidTest/java/de/foody/app/SyncApplierTest.kt`

**Interfaces:**
- Consumes: `SyncDao`, DAOs, `SyncMapper`, `RecordType.decode`
- Produces:
  - `suspend fun mergeIngredient(dao: IngredientDao, from: IngredientEntity, into: IngredientEntity, now: Long)` (aus `IngredientRepository.mergeInto` herausgelöst, Verhalten unverändert; `IngredientRepository` ruft sie auf).
  - `@Singleton class SyncApplier @Inject constructor(private val db: FoodyDatabase)` mit `suspend fun apply(records: List<SyncRecord>, nextCursor: Long): ApplyResult`; `data class ApplyResult(val applied: Int, val skippedPending: Int, val revived: Int, val merged: Int, val problems: Int)`.

Regeln (alles in **einer** Room-Transaktion; zu Beginn `setApplyingRemote(true)`, am Ende `false` und `cursor = nextCursor`, `lastSyncAt = now`):
1. **Offener lokaler Eintrag:** `isQueued(type, id)` → Datensatz überspringen, `sync_record_rev` **nicht** ändern (`skippedPending`).
2. **Reihenfolge:** zuerst alle lebenden Datensätze in `RecordType`-Reihenfolge (innerhalb nach `rev`), dann alle Löschungen in umgekehrter Reihenfolge.
3. **Lebend:** Payload dekodieren (Fehler → `sync_problem(type, id, "invalid_payload")`); Referenzen (`PayloadValidator.references`) müssen lokal existieren, sonst `sync_problem(…, "missing_reference")` und überspringen; sonst Entity (und Kinder) schreiben – Rezept: Zeilen und Schritte ersetzen; Einkaufseintrag: Quellen ersetzen; `setRev(type, id, rev)`.
4. **Zutat mit gleichem Namen, andere ID** (lokal `findByName` ohne Groß-/Kleinschreibung): lokale Zutat vorübergehend in `canonicalName + "#" + id` umbenennen (eindeutiger Namensindex), Server-Zutat schreiben, dann mit `setApplyingRemote(false)` `mergeIngredient(local → remote)` aufrufen (damit Trigger die umgehängten Rezepte/Vorräte/Einträge in die Outbox stellen), anschließend wieder `true` und `dequeue(ingredient, localId)`; `merged++`.
5. **Löschung:** lokale Zeile löschen (Kinder per Kaskade). Ausnahme **Zutat noch von Rezeptzeilen verwendet** (FK `RESTRICT`): nicht löschen, `setRev(ingredient, id, rev)` und Outbox-Eintrag `deleted = false` setzen, damit der nächste Push sie mit `baseRev = rev` wiederbelebt (Spec 4.3 „Löschen vs. Bearbeiten“); `revived++`.
6. Löschung eines lokal unbekannten Datensatzes: nur `setRev`.

- [ ] **Step 1: Failing tests** in `SyncApplierTest.kt` (Datensätze per `SyncMapper.toJson` bauen; `activateSyncForTest()`):
  - `appliesAllTypesInDependencyOrder` – Liste in umgekehrter Reihenfolge übergeben (Einkaufseintrag, Liste, Vorrat, Plan, Rezept, Zutat) → alles angelegt, `applied = 6`, Outbox leer, `cursor = nextCursor`.
  - `pendingLocalEditWins` – lokales Rezept geändert (Outbox-Eintrag), Pull bringt anderes Rezept-Payload mit `rev = 9` → lokaler Name bleibt, `revOf(recipe, rid) == null` (bzw. alter Wert), `skippedPending = 1` (Review Focus 4).
  - `remoteUpdateReplacesLinesAndSteps` – lokales Rezept mit 3 Zeilen, Pull mit 1 Zeile + 2 Schritten → genau 1 Zeile, 2 Schritte; Outbox leer.
  - `sameNameDifferentCaseMergesIntoRemote` – lokal „zwiebel“ (ID `L`) mit Vorrat und Rezeptzeile; Pull Zutat „Zwiebel“ (ID `R`) → `L` weg, Vorrat und Zeile zeigen auf `R`, Outbox enthält das Rezept und den Vorrat (`deleted = false`), keinen Eintrag für `L`, `merged = 1` (Review Focus 5).
  - `deleteOfReferencedIngredientRevivesIt` – Rezept nutzt Zutat `I`; Pull Löschung `I` (`rev = 7`) → `I` existiert, `revOf(ingredient, I) == 7`, Outbox `(ingredient, I, false)`, `revived = 1`.
  - `remoteDeleteCascadesWithoutQueueing` – Pull Löschung eines Rezepts mit 2 Planpositionen → Rezept und Positionen weg, Outbox leer.
  - `missingReferenceIsRecordedAsProblem` – Pull Planposition auf unbekanntes Rezept → nicht angelegt, `problems()` enthält `(meal_slot, id, "missing_reference")`.
  - `remoteCheckStampIsKept` – Pull Einkaufseintrag `checked = true, checkedChangedAt = 555` → lokal genau 555 (Trigger überschreibt nicht).
  - `invalidPayloadBecomesProblemOthersApply` – Pull mit einer gültigen Zutat und einem Rezept, dessen Roh-JSON eine Zeile mit Einheit `"BUCKET"` enthält → Zutat angelegt, Rezept nicht, `problems()` enthält `(recipe, id, "invalid_payload")`, `getState().applyingRemote == false`.
- [ ] **Step 2: Run** Instrumented `de.foody.app.SyncApplierTest` → FAIL.
- [ ] **Step 3: Implementieren** (`mergeIngredient` herauslösen, bestehende Tests `IngredientHarmonizeTest`/`FavoritesAndDedupTest` bleiben grün).
- [ ] **Step 4: Run** `SyncApplierTest`, `IngredientHarmonizeTest`, danach gesamte `connectedDebugAndroidTest` und `:app:testDebugUnitTest` → PASS.
- [ ] **Step 5: Commit** `git commit -m "Sync: Server-Daten konfliktsicher anwenden"`

---

## Abschluss

- [ ] Gesamtlauf: `./gradlew :sync-protocol:test :server:test :domain:test :app:testDebugUnitTest` und `ANDROID_SERIAL=emulator-5558 ./gradlew :app:connectedDebugAndroidTest` → grün.
- [ ] PR gegen `main` (Etappe 2 von 5); Beschreibung nennt die Abweichungen oben. Hinweis: Etappe 3 (SyncClient, TokenStore, SyncWorker, Push-Ergebnisse auswerten, Voll-Abgleich bei 410) baut auf `SyncLocalStore.pendingRecords` und `SyncApplier.apply` auf.
