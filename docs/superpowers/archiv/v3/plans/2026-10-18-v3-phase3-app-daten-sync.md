# V3 Phase 3: App-Daten und Sync-Client – Umsetzungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Neu geschriebene Module `:core:data` (Room 10, Migration 9 → 10, Sync-Trigger, Sync-Speicher, Fotos, Sicherung, Portionen-Nachtrag) und `:core:sync` (Client für Protokoll 3 des eigenen Sync-Servers), sodass eine echte v1-Datenbank migriert und ohne neue Anmeldung mit dem Test-Server synchronisiert.

**Architecture:** Zwei Android-Bibliotheken unter `android/core/` mit dem Convention-Plugin `foody.android.library`. Das Schema 10 ist Schema 9 ohne Vorrat und mit `kontoId`; ein Schema-Vergleichstest erzwingt das. Der Sync-Client folgt dem Verhalten der v1 und wird gegen den echten `:server-sync` (Protokoll 3) im Test geprüft. Die v1 (`:app`, `:sync-protocol`) bleibt unverändert.

**Tech Stack:** Kotlin, Room 2.8.5 (KSP), Hilt 2.60.1, WorkManager, Ktor-Client (OkHttp), kotlinx.serialization, Robolectric 4.16.1, JUnit4/kotlin-test, Gradle Managed Device.

**Spec:** [`docs/superpowers/specs/2026-10-18-v3-phase3-app-daten-sync-design.md`](../specs/2026-10-18-v3-phase3-app-daten-sync-design.md)

## Global Constraints

- Neu schreiben; v1-Code (`android/app`) nur lesen. `:app`, `:sync-protocol`, `android/app/src/main` werden nicht verändert.
- Module: `:core:data` = `android/core/data`, Paket `de.foody.core.data`; `:core:sync` = `android/core/sync`, Paket `de.foody.core.sync`. Abhängigkeiten: `:core:sync` → `:core:data`, `:api-protocol`; `:core:data` → `:domain`, `:api-protocol`.
- Datenbankname `foody.db`, Version 10. Schema 10 = Schema 9 (`android/app/schemas/de.foody.app.data.db.FoodyDatabase/9.json`) ohne `pantry_item` und mit `sync_state.kontoId` (nullable) statt `householdId`; sonst Spalten, Typen, Nullbarkeit, Primärschlüssel und Indizes identisch.
- Nur Migration 9 → 10; nie `fallbackToDestructiveMigration`.
- Token-Ablage wie v1: SharedPreferences `foody_sync`, Eintrag `token`, Keystore-Alias `foody_sync_token` (AES/GCM wie `android/app/.../sync/TokenStore.kt`).
- v1-Auftrag beim Start abmelden: `WorkManager.cancelUniqueWork("foody-sync-periodic")`.
- Prefs `foody`, Schlüssel unverändert: `seedVersion` (alt `seeded`), `dailyKcalGoal`, `themeMode`, `shoppingListView`, `onlineProduktsuche`, `fotosVerkleinert`. Fotoordner `recipe_images`.
- Sync: `/api/v3`, Header `X-Foody-Protocol: 3` (`de.foody.api.protocol.Protocol`), Push höchstens `Protocol.MAX_PUSH_RECORDS` (500) bzw. `MAX_PUSH_BYTES` (5 MB). Kein periodischer Auftrag; Auslöser: Vordergrund, Outbox-Änderung entprellt 5 s (einmaliger WorkManager-Auftrag mit Netz), „Jetzt synchronisieren“.
- `lastError` nur feste Kennungen: `offline`, `anmeldung_abgelaufen`, `app_zu_alt`, `server_zu_alt`, `cursor_haengt`, `server_fehler`; nie Token oder Antwort-Bodies.
- Fotos: höchstens 1.600 px längere Seite, 600 KB, JPEG 85.
- Debug-Builds erlauben `http` nur für `10.0.2.2` und `localhost`.
- Echte Daten nie ins Repo; `.gitignore` erhält `*.foody-echt.db*`.
- Kommentare, Bezeichner und Meldungen auf Deutsch wie im übrigen Code; `allWarningsAsErrors` bleibt an.
- PR mit Auto-Merge; „Server aktualisieren? Nein“.

## Review Focus

1. **Update mit ungesendeten Änderungen inkl. Löschungen** – nach 9 → 10 gehen Outbox-Einträge (auch `deleted = 1`) über Protokoll 3 hinaus und verschwinden danach aus der Outbox (Test in Task 11: `outboxAusV1WirdNachUpdateGesendet`).
2. **Datenbank mit nicht eingecheckten WAL-Seiten** – die echte DB kommt als `foody.db` + `foody.db-wal`; die Migration muss den WAL-Inhalt sehen (Task 13: Kopie immer mit `-wal`, Test zählt Zeilen gegen `sqlite3`-Zählung der Quelle).
3. **Keystore-Schlüssel fehlt oder Token nicht entschlüsselbar** (z. B. nach Sicherung/Wiederherstellung des Geräts) – Zustand „Anmeldung abgelaufen“, kein Absturz, Token wird verworfen (Test in Task 10: `unlesbaresTokenGiltAlsAbgemeldet`).
4. **Rezeptfoto als Galerie-Link (`content://`) statt eigener Datei** – wird nie hochgeladen, bleibt beim Sync erhalten (Test in Task 7: `galerieLinkWirdNichtGesendet`).
5. **ZIP aus unbekanntem Format oder beschädigt** – klare Fehlermeldung, nichts halb importiert (Test in Task 8: `unbekanntesFormatWirdAbgelehnt`, `abgebrochenerImportAendertNichts`).

---

### Task 1: Modul-Gerüst `:core:data` und `:core:sync`

**Files:**
- Create: `android/core/data/build.gradle.kts`, `android/core/sync/build.gradle.kts`, je `src/main/AndroidManifest.xml` (leer), `android/core/data/src/test/kotlin/de/foody/core/data/GeruestTest.kt`
- Copy: `android/app/schemas/de.foody.app.data.db.FoodyDatabase/{1..9}.json` → `android/core/data/schemas/de.foody.core.data.db.FoodyDatenbank/{1..9}.json` (Kopie, Dateiinhalt unverändert)
- Modify: `settings.gradle.kts` (`include(":core:data", ":core:sync")`, `projectDir` auf `android/core/...`, im Block ohne `FOODY_SERVER_ONLY`), `.gitignore` (`*.foody-echt.db*`)

**Interfaces:**
- Produces: Module `:core:data` (Plugins `foody.android.library`, `ksp`, `hilt`, `room`; `room { schemaDirectory("$projectDir/schemas") }`; Managed Device `ciDevice` wie in `android/app/build.gradle.kts`; androidTest-Assets aus `schemas`) und `:core:sync` (Plugins `foody.android.library`, `ksp`, `hilt`, `kotlin.serialization`; `testImplementation(project(":server-sync"))`).

- [ ] **Step 1:** Build-Dateien und Settings anlegen; Test `GeruestTest.schemaNeunIstDa()`: liest `schemas/de.foody.core.data.db.FoodyDatenbank/9.json` über `File` relativ zum Modul und prüft `"version": 9`.
- [ ] **Step 2:** Run `./gradlew :core:data:testDebugUnitTest :core:sync:assembleDebug` → PASS.
- [ ] **Step 3:** Commit `"V3 Phase 3: Gerüst core:data und core:sync"`.

---

### Task 2: Entities und DAOs Version 10, Schema-Vergleich

**Files:**
- Create: `android/core/data/src/main/kotlin/de/foody/core/data/db/{FoodyDatenbank,Entities,SyncEntities,Daos,SyncDao,Converters}.kt`, `android/core/data/src/main/kotlin/de/foody/core/data/Einstellungsschluessel.kt`
- Test: `android/core/data/src/test/kotlin/de/foody/core/data/db/SchemaVergleichTest.kt`, `DaoTest.kt`, `android/core/data/src/test/kotlin/de/foody/core/data/EinstellungsschluesselTest.kt`

**Interfaces:**
- Produces: `@Database(version = 10, exportSchema = true) abstract class FoodyDatenbank : RoomDatabase` mit `companion object { const val NAME = "foody.db" }`; Entities für alle 15 Tabellen der Spec (Tabellennamen exakt wie Schema 9); DAOs je Tabelle mit den Abfragen, die Sync und Sicherung brauchen (`alle()`, `nachId(id)`, `upsert(e)`, `loeschen(id)`); `SyncDao` für `sync_outbox`, `sync_record_rev`, `sync_state`, `sync_problem`, `sync_photo_local`, `sync_photo_wanted`. `sync_state` hat `kontoId: String?` statt `householdId`. `object Einstellungsschluessel { const val PREFS = "foody"; const val SEED_VERSION = "seedVersion"; const val SEEDED_ALT = "seeded"; const val TAGESZIEL = "dailyKcalGoal"; const val DARSTELLUNG = "themeMode"; const val EINKAUF_ANSICHT = "shoppingListView"; const val ONLINE_SUCHE = "onlineProduktsuche"; const val FOTOS_VERKLEINERT = "fotosVerkleinert"; const val FOTO_ORDNER = "recipe_images" }`.

- [ ] **Step 1: Test `SchemaVergleichTest`** (JSON-Vergleich, kein Room-Laufzeittest):

```kotlin
@Test fun schemaZehnIstSchemaNeunOhneVorrat() {
    val neun = tabellen(".../9.json")          // name -> (Spalten(name, affinity, notNull), PK, Indizes(name, spalten, unique))
    val zehn = tabellen(".../10.json")
    val erwartet = neun - "pantry_item" + ("sync_state" to neun.getValue("sync_state").spalteUmbenannt("householdId", "kontoId"))
    assertEquals(erwartet, zehn)
}
```
- [ ] **Step 2:** Run `./gradlew :core:data:kspDebugKotlin :core:data:testDebugUnitTest` → FAIL (10.json fehlt/abweichend).
- [ ] **Step 3:** Entities/DAOs schreiben, bis `10.json` exportiert wird und der Vergleich grün ist. Namen der Indizes wie Schema 9 (`index_<tabelle>_<spalte>`).
- [ ] **Step 4: `DaoTest`** (Robolectric, In-Memory): je Tabelle Einfügen/Lesen; `upsert` auf `recipe` löst kein DELETE aus (Zeilenzahl bleibt 1, Outbox-Trigger kommen in Task 3).
- [ ] **Step 5: `EinstellungsschluesselTest.gleicheSchluesselWieVEins`**: vergleicht jede Konstante mit dem Wert, den die v1 verwendet (Liste in Global Constraints) – schützt gegen Tippfehler, die nach dem Update Einstellungen verlieren würden.
- [ ] **Step 6:** Run → PASS. Commit `"V3 Phase 3: Entities und DAOs Version 10"`.

---

### Task 3: Sync-Trigger Version 10

**Files:**
- Create: `android/core/data/src/main/kotlin/de/foody/core/data/db/SyncTrigger.kt`, Callback in `FoodyDatenbank` (onCreate legt Trigger an)
- Test: `android/core/data/src/test/kotlin/de/foody/core/data/db/SyncTriggerTest.kt`

**Interfaces:**
- Produces: `object SyncTrigger { val anweisungen: List<String>; val namen: List<String> }` – Wurzeln `ingredient`, `recipe`, `meal_slot`, `shopping_list`, `shopping_item`, `tagebuch_eintrag`; Kinder `recipe_ingredient`/`instruction_step` → `recipe` (`recipeId`), `shopping_item_source` → `shopping_item` (`shoppingItemId`); Pflege-Trigger für `shopping_item` wie v1 (`android/app/.../db/SyncTriggers.kt` als Vorlage). Triggernamen `sync_<tabelle>_ai|au|ad`.

- [ ] **Step 1: Tests**

```kotlin
@Test fun inaktiverSyncSchreibtKeineOutbox()       // sync_state.active = 0 → Rezept anlegen → Outbox leer
@Test fun aenderungMerktWurzelVor()                // active = 1 → Zutat ändern → Outbox (ingredient, id, deleted = 0)
@Test fun loeschenMerktLoeschungVor()              // → deleted = 1
@Test fun kindMerktElternRezeptVor()               // recipe_ingredient ändern → Outbox (recipe, recipeId)
@Test fun anwendenVomServerSchreibtKeineOutbox()   // applyingRemote = 1 → Änderung → Outbox leer
@Test fun keinEndlosTrigger()                      // shopping_item abhaken → genau ein Outbox-Eintrag, Abfrage endet
@Test fun keinVorratsTrigger()                     // SyncTrigger.namen enthält nichts mit "pantry"
```
- [ ] **Step 2:** Run `./gradlew :core:data:testDebugUnitTest --tests '*SyncTriggerTest*'` → FAIL.
- [ ] **Step 3:** Umsetzen. Step 4: PASS. Step 5: Commit `"V3 Phase 3: Sync-Trigger"`.

---

### Task 4: Migration 9 → 10

**Files:**
- Create: `android/core/data/src/main/kotlin/de/foody/core/data/db/Migration9auf10.kt`; Registrierung in der Datenbank-Fabrik `android/core/data/src/main/kotlin/de/foody/core/data/DatenModul.kt` (Hilt, `Room.databaseBuilder(..., FoodyDatenbank.NAME).addMigrations(MIGRATION_9_10)`, ohne Destructive-Fallback)
- Test: `android/core/data/src/androidTest/kotlin/de/foody/core/data/db/MigrationTest.kt`, `EchteDatenMigrationTest.kt`

**Interfaces:**
- Consumes: `SyncTrigger.anweisungen`, `SyncTrigger.namen` (Task 3).
- Produces: `val MIGRATION_9_10: Migration`.

- [ ] **Step 1: `MigrationTest`** (Room `MigrationTestHelper`, Schema 9 als Vorlage, Testdaten in allen v9-Tabellen inkl. 2 Vorrats-Zeilen, Outbox mit `pantry_item` und `recipe`, `sync_state(active = 1, serverUrl = "https://foody.homeserver.lan", householdId = "h1", cursor = 413)`):

```kotlin
@Test fun neunAufZehn() {
    helper.runMigrationsAndValidate(DB, 10, true, MIGRATION_9_10).use { db ->
        assertFalse(tabelleExistiert(db, "pantry_item"))
        assertEquals(zeilenV9OhneVorrat, zeilenzahlen(db))
        assertEquals(0, zahl(db, "SELECT count(*) FROM sync_outbox WHERE type = 'pantry_item'"))
        assertEquals(listOf(1, "https://foody.homeserver.lan", null, 413L), syncState(db))  // active, serverUrl, kontoId, cursor
        assertEquals(SyncTrigger.namen.toSet(), triggerNamen(db))
    }
}
@Test fun triggerWirkenNachMigration()   // nach Migration: Zutat ändern → Outbox-Eintrag
@Test fun aeltereVersionScheitert()      // DB Version 8 öffnen → IllegalStateException, Datei unverändert
```
- [ ] **Step 2: `EchteDatenMigrationTest`**: Pfad aus Instrumentation-Argument `echteDb` (`InstrumentationRegistry.getArguments()`); fehlt es → `assumeTrue(false)`. Kopiert `foody.db` **und** `foody.db-wal` (falls vorhanden) in den Test-Datenbankordner, öffnet über `DatenModul`-Fabrik (läuft Migration), vergleicht Zeilenzahlen je Tabelle mit den im Argument `echteDbZahlen` übergebenen Werten (Format `tabelle=zahl,…`), außer `pantry_item`.
- [ ] **Step 3:** Run `./gradlew :core:data:ciDeviceDebugAndroidTest -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect` → FAIL. Step 4: umsetzen (Reihenfolge laut Spec Abschnitt 2), PASS (Echtdaten-Test übersprungen). Step 5: Commit `"V3 Phase 3: Migration 9 → 10"`.

---

### Task 5: Abbildung Datensatz ↔ Payload

**Files:**
- Create: `android/core/data/src/main/kotlin/de/foody/core/data/sync/Abbildung.kt`
- Test: `android/core/data/src/test/kotlin/de/foody/core/data/sync/AbbildungTest.kt`

**Interfaces:**
- Consumes: Entities (Task 2); `de.foody.api.protocol.{RecordType, SyncRecord, *Payload}`.
- Produces: `object Abbildung { suspend fun zuSyncRecord(db: FoodyDatenbank, typ: RecordType, id: String, geloescht: Boolean): SyncRecord?; suspend fun anwenden(db: FoodyDatenbank, record: SyncRecord) }` – Rezepte mit Zeilen und Schritten als ein Datensatz, Einkaufsartikel mit Quellen; Fotofeld des Rezepts kommt aus Task 7 und bleibt hier `null`.

- [ ] **Step 1: Tests** (Vorlage v1 `SyncMapper`): `jederTypRundreise` (für alle 6 `RecordType` lokal → Record → anwenden in leere DB → gleiche Zeilen), `einordnungUndMahlzeitUeberstehenSync`, `strichcodeUeberstehtSync`, `rezeptKinderWerdenErsetzt` (Server-Rezept mit weniger Zeilen löscht überzählige lokale Zeilen), `loeschungEntferntMitKindern`.
- [ ] **Step 2–4:** ROT, umsetzen, GRÜN (`./gradlew :core:data:testDebugUnitTest --tests '*AbbildungTest*'`). Step 5: Commit `"V3 Phase 3: Abbildung Datensatz ↔ Payload"`.

---

### Task 6: Fotoablage

**Files:**
- Create: `android/core/data/src/main/kotlin/de/foody/core/data/fotos/{FotoAblage,FotoIndex}.kt`
- Test: `android/core/data/src/test/kotlin/de/foody/core/data/fotos/FotoAblageTest.kt`

**Interfaces:**
- Produces: `class FotoAblage(context: Context)`: `suspend fun speichern(quelle: Uri): String` (Link `file:` in `filesDir/recipe_images`, verkleinert, EXIF-Drehung), `suspend fun verkleinereAlleEinmal(): Int` (Merker `fotosVerkleinert` in Prefs `foody`, Version 1), `fun istEigeneDatei(link: String): Boolean`, `fun datei(link: String): File?`. `class FotoIndex(dao: SyncDao)`: `suspend fun hash(link: String): String?` mit Cache in `sync_photo_local` (Schlüssel Link, Größe, Änderungszeit).

- [ ] **Step 1: Tests** (Vorlage v1 `PhotoShrinkTest`, `PhotoIndex`): `grossesKamerafotoWirdVerkleinertUndGedreht` (4000×3000 mit EXIF 90° → längere Seite 1600, ≤ 600 KB), `kleinesFotoBleibt`, `verkleinerungLaeuftNurEinmal`, `hashWirdGemerktBisDateiSichAendert`, `galerieLinkIstKeineEigeneDatei`.
- [ ] **Step 2–4:** ROT, umsetzen, GRÜN. Step 5: Commit `"V3 Phase 3: Fotoablage"`.

---

### Task 7: Sync-Speicher (Schnittstelle Daten ↔ Sync)

**Files:**
- Create: `android/core/data/src/main/kotlin/de/foody/core/data/sync/{SyncSpeicher,Paket}.kt`
- Test: `android/core/data/src/test/kotlin/de/foody/core/data/sync/SyncSpeicherTest.kt`

**Interfaces:**
- Consumes: `Abbildung` (Task 5), `FotoAblage`, `FotoIndex` (Task 6), `SyncDao`.
- Produces: `class SyncSpeicher` mit `val laufSperre: Mutex`; `suspend fun zustand(): SyncZustand` (`aktiv`, `serverUrl`, `kontoId`, `cursor`, `letzterSync`, `letzterFehler`); `suspend fun aktivieren(serverUrl: String, kontoId: String, allesHochladen: Boolean)`; `suspend fun deaktivieren()` (wartet auf `laufSperre`, leert Outbox/Revisionen/Probleme/Fotowünsche, `active = 0`); `suspend fun naechstesPaket(maxDatensaetze: Int, maxBytes: Long): List<Ausstehend>` mit `data class Ausstehend(val record: SyncRecord, val vorgemerktUm: Long, val fotoProblem: String?, val fotoHash: String?, val fotoDatei: File?)`; `suspend fun erledigt(a: Ausstehend, rev: Long)` (aus Outbox nur, wenn `vorgemerktUm` unverändert); `suspend fun abgelehnt(a: Ausstehend, code: String)`; `suspend fun anwenden(records: List<SyncRecord>, neuerCursor: Long)` (eine Transaktion, `applyingRemote = 1`, Revisionen merken, Fotowünsche für unbekannte Hashes); `suspend fun vollabgleich(serverDatensaetze: List<SyncRecord>, cursor: Long)` (Regeln aus Spec: behält benutzte Zutaten, verwiesene Rezepte, Listen mit wartenden Einträgen, abgelehnte lokale Datensätze); `suspend fun fotowuensche(): List<Pair<String, String>>`; `suspend fun fotoErhalten(rezeptId: String, sha256: String, bytes: ByteArray)`; `suspend fun fehler(kennung: String?)`.

- [ ] **Step 1: Tests** (Vorlage v1 `SyncLocalStore`/`SyncApplier`-Tests): `inaktiverSyncTutNichts`, `allesHochladenMerktAllesVor`, `nurHerunterladenMerktNichtsVor`, `paketHaeltGrenzenEin` (600 Zutaten → Paket 500), `geaendertWaehrendGesendetBleibtInOutbox`, `abgelehntWirdProblem`, `anwendenSchreibtKeineOutbox`, `bekanntesFotoWirdVerknuepft`, `unbekanntesFotoWirdGewuenscht`, `nullFotoBehaeltGalerieLink`, `nullFotoEntferntEigenesFoto`, `galerieLinkWirdNichtGesendet` (Review-Fokus 4), `fehlendeEigeneDateiSendetKeinFoto`, `uebergrosseEigeneDateiWirdOhneFotoGesendet` (`fotoProblem = "photo_unsyncable"`), `vollabgleichBehaeltBenutzteZutat`, `vollabgleichBehaeltVerwiesenesRezept`, `vollabgleichBehaeltListeMitWartendemEintrag`, `vollabgleichBehaeltAbgelehntenDatensatz`, `deaktivierenWartetAufLaufUndLeertFotowuensche`.
- [ ] **Step 2–4:** ROT, umsetzen, GRÜN. Step 5: Commit `"V3 Phase 3: Sync-Speicher"`.

---

### Task 8: Sicherung (ZIP)

**Files:**
- Create: `android/core/data/src/main/kotlin/de/foody/core/data/sicherung/{Sicherung,SicherungFormat}.kt`
- Test: `android/core/data/src/test/kotlin/de/foody/core/data/sicherung/SicherungTest.kt` (v1-Testdatei wird im Test aus einem JSON-Text erzeugt, Vorlage `android/app/.../repo/BackupRepository.kt`, Format `backup.json` + `photos/`)

**Interfaces:**
- Consumes: DAOs, `FotoAblage`, `SyncSpeicher.zustand()`.
- Produces: `class Sicherung`: `suspend fun schreiben(ziel: OutputStream)` (Format 3: `backup.json` mit `"format": 3`, ohne Vorrat, `photos/<datei>`); `suspend fun lesen(quelle: InputStream): ImportErgebnis` mit `sealed interface ImportErgebnis { data class Fertig(val rezepte: Int, val zutaten: Int, val fotos: Int, val vorratUebersprungen: Int); data object SyncAktiv; data class Fehler(val grund: String) }`.

- [ ] **Step 1: Tests**: `rundreiseFormatDrei`, `vEinsWirdGelesenVorratUebersprungen` (Ergebnis `vorratUebersprungen = 2`), `fotosUeberstehenSicherung`, `importBeiAktivemSyncGesperrt`, `zipSlipWirdAbgelehnt` (`../evil` → `Fehler`), `zuGrosseDateiWirdAbgelehnt` (Grenzen wie v1 `ImportLimitsTest`), `unbekanntesFormatWirdAbgelehnt` (`"format": 99`), `abgebrochenerImportAendertNichts` (beschädigte Zip nach `backup.json` → Zeilenzahlen unverändert) – letzte zwei aus Review-Fokus 5.
- [ ] **Step 2–4:** ROT, umsetzen (Import in einer Transaktion), GRÜN. Step 5: Commit `"V3 Phase 3: Sicherung"`.

---

### Task 9: Portionen-Nachtrag

**Files:**
- Create: `android/core/data/src/main/kotlin/de/foody/core/data/import/PortionenNachtrag.kt`
- Test: `android/core/data/src/test/kotlin/de/foody/core/data/import/PortionenNachtragTest.kt`

**Interfaces:**
- Consumes: `de.foody.domain.RezeptImport.portionenNachtragen(alteNotiz, aktuellePortionen, datei)` → `Nachtrag(portionen, notiz)?`; `de.foody.domain.MarkdownRecipeImporter.parse`.
- Produces: `class PortionenNachtrag(db: FoodyDatenbank)` mit `suspend fun anwenden(dateien: List<Pair<String, String>>): NachtragErgebnis` und `data class NachtragErgebnis(val aktualisiert: Int, val uebersprungen: Int, val ohneRezept: Int)`.

- [ ] **Step 1: Tests**: `geschaetztesRezeptBekommtPortionenDerDatei` (Notiz mit „aus den Mengen auf 8 geschätzt“, Datei „Portionen: 4“ → `defaultServings = 4`, Hinweis weg, Mengen gleich), `vonHandGeaendertBleibt`, `ohnePassendesRezeptGezaehlt`, `aenderungLandetInOutbox` (Sync aktiv → Outbox `recipe`).
- [ ] **Step 2–4:** ROT, umsetzen (Zuordnung über `recipe.sourceUrl` = geparste Quelle), GRÜN. Step 5: Commit `"V3 Phase 3: Portionen-Nachtrag"`.

---

### Task 10: Server-Zugang und Token-Speicher (`:core:sync`)

**Files:**
- Create: `android/core/sync/src/main/kotlin/de/foody/core/sync/{ServerZugang,KtorServerZugang,ServerFehler,ServerAdresse,TokenSpeicher}.kt`
- Test: `android/core/sync/src/test/kotlin/de/foody/core/sync/{ServerZugangTest,ServerAdresseTest,TokenSpeicherTest}.kt`, Testhilfe `TestSyncServer.kt` (startet `foodyModule` aus `:server-sync` mit `testApplication`, Vorlage: gelöschte v1-Datei am Tag `v1-server` `android/app/src/test/java/de/foody/app/sync/TestSyncServer.kt`)

**Interfaces:**
- Produces: `interface ServerZugang { suspend fun anmelden(LoginRequest): AuthResponse; suspend fun abmelden(); suspend fun push(List<SyncRecord>): PushResponse; suspend fun pull(since: Long, limit: Int): PullResponse; suspend fun fotosFehlend(List<String>): List<String>; suspend fun fotoHochladen(sha256: String, bytes: ByteArray); suspend fun fotoLaden(sha256: String): ByteArray? }`; `sealed class ServerFehler(val kennung: String) : Exception()` mit `Offline` (`offline`), `AnmeldungAbgelaufen` (`anmeldung_abgelaufen`, 401), `AppZuAlt` (`app_zu_alt`, 409 `protocol_too_old`), `ServerZuAlt` (`server_zu_alt`, 409 `server_too_old` oder 404 auf `/api/v3`), `CursorAbgelaufen` (410), `ZuGross` (413), `Abgelehnt(code)` (sonstige 4xx), `Server` (`server_fehler`, 5xx); `object ServerAdresse { fun pruefen(eingabe: String, debug: Boolean): String? }`; `class TokenSpeicher(context)`: `fun laden(): String?`, `fun speichern(token: String)`, `fun loeschen()` – Ablage laut Global Constraints.

- [ ] **Step 1: Tests**: `anmeldenLiefertKontoId`, `headerDreiWirdGesendet`, `fehlerWerdenZugeordnet` (401, 409 beide Codes, 404, 410, 413, 500 je → passende Klasse), `netzfehlerIstOffline`, `keinTokenInFehlermeldung`, `adresseNurHttpsAusserDebugLokal` (`http://10.0.2.2:8081` nur mit `debug = true`), `tokenUeberlebtNeustart`, `unlesbaresTokenGiltAlsAbgemeldet` (Prefs-Wert verfälscht → `laden() == null`, Eintrag entfernt; Review-Fokus 3), `gleicheAblageWieVEins` (Prefs-Name/Eintrag/Alias als Konstanten geprüft).
- [ ] **Step 2–4:** ROT, umsetzen (Ktor OkHttp, `Protocol.json`, Zeitüberschreitungen wie v1 `KtorSyncApi`), GRÜN. Step 5: Commit `"V3 Phase 3: Server-Zugang und Token-Speicher"`.

---

### Task 11: Sync-Lauf

**Files:**
- Create: `android/core/sync/src/main/kotlin/de/foody/core/sync/{SyncLauf,SyncErgebnis}.kt`
- Test: `android/core/sync/src/test/kotlin/de/foody/core/sync/SyncLaufTest.kt` (gegen `TestSyncServer`, Robolectric-DB)

**Interfaces:**
- Consumes: `SyncSpeicher` (Task 7), `ServerZugang`, `ServerFehler` (Task 10).
- Produces: `class SyncLauf(speicher: SyncSpeicher, zugang: () -> ServerZugang?)` mit `suspend fun ausfuehren(): SyncErgebnis` und `sealed interface SyncErgebnis { data object Inaktiv; data class Fertig(val gesendet: Int, val geholt: Int); data class Fehlgeschlagen(val kennung: String, val voruebergehend: Boolean) }`.

- [ ] **Step 1: Tests** (Verhaltensliste aus der v1, für Protokoll 3 neu): `inaktiverSyncErfolgreichOhneAnfrage`, `rundreiseZweiGeraete`, `fehlendeFotosVorDemPushHochladen`, `uploadFehlerBehaeltOutboxVoruebergehend`, `gewuenschtesFotoWirdGeladenOhneOutboxEcho`, `fotoNichtAufServerBleibtGewuenscht`, `nichtJpegWirdOhneFotoGesendet`, `uploadClientFehlerBetrifftNurDiesesRezept`, `verschwundeneDateiHaeltNurDiesesRezeptZurueck`, `cursorAbgelaufenLoestVollabgleichAus`, `vollabgleichUebersteht­KaputtenServerDatensatz`, `festhaengenderCursorIstVoruebergehend` (`cursor_haengt`), `fehlendesVerweiszielWirdNachgereicht`, `abgelehntWirdProblem`, `anmeldungAbgelaufenSetztKennung`, `appZuAltSetztKennung`, `outboxAusVEinsWirdNachUpdateGesendet` (DB über `MIGRATION_9_10` aus v9-Vorlage mit Outbox inkl. Löschung → Lauf → Server hat Änderungen, Outbox leer; Review-Fokus 1), `alterTokenUndCursorReichen` (Gerät mit vorhandenem Token und Cursor > 0 → kein Voll-Abgleich, nur Neues geholt).
- [ ] **Step 2–4:** ROT, umsetzen (Ablauf laut Spec Abschnitt 3, alles unter `speicher.laufSperre`), GRÜN. Step 5: Commit `"V3 Phase 3: Sync-Lauf"`.

---

### Task 12: Verbinden, Trennen, Auslöser

**Files:**
- Create: `android/core/sync/src/main/kotlin/de/foody/core/sync/{SyncKonto,SyncAusloeser,SyncAuftrag,SyncModul}.kt`
- Test: `android/core/sync/src/test/kotlin/de/foody/core/sync/{SyncKontoTest,SyncAusloeserTest}.kt` (WorkManager-Testhelfer `androidx-work-testing`)

**Interfaces:**
- Consumes: `SyncSpeicher`, `ServerZugang`, `TokenSpeicher`, `SyncLauf`.
- Produces: `class SyncKonto`: `suspend fun anmelden(adresse: String, benutzer: String, passwort: String, geraet: String): Ergebnis<Unit>`, `suspend fun hatLokaleDaten(): Boolean`, `suspend fun aktivieren(modus: ErsterSync)` mit `enum class ErsterSync { ZUSAMMENFUEHREN, ERSETZEN }`, `suspend fun trennen()`; `class SyncAusloeser`: `fun appImVordergrund()`, `fun jetzt()`, beobachtet Outbox und plant `SyncAuftrag` (einmalig, Netz-Bedingung, eindeutiger Name `foody-sync-v3`, 5 s Verzögerung, `ExistingWorkPolicy.REPLACE`), `fun beimStart()` meldet `foody-sync-periodic` ab; `class SyncAuftrag : CoroutineWorker` (Hilt) ruft `SyncLauf.ausfuehren()`, vorübergehende Fehler → `Result.retry()`.

- [ ] **Step 1: Tests**: `anmeldenUndZusammenfuehrenLaedtLokalesHoch`, `ersetzenVerwirftLokales`, `trennenMeldetAbUndLeert` (Server: Token danach 401; lokal: Token weg, Outbox leer, `active = 0`), `trennenOhneNetzLeertTrotzdem`, `outboxAenderungPlantEinenAuftrag` (drei Änderungen in 2 s → ein eingeplanter Auftrag), `inaktiverSyncPlantNichts`, `vordergrundStoesstLaufAn`, `alterPeriodischerAuftragWirdAbgemeldet`.
- [ ] **Step 2–4:** ROT, umsetzen, GRÜN. Step 5: Commit `"V3 Phase 3: Verbinden, Trennen, Auslöser"`.

---

### Task 13: Echte Daten, Doku, Abschluss

**Files:**
- Modify: `ToDoS/v3-plan.md` (Phase 3 abhaken mit Ergebnis), `docs/architecture.md` (Abschnitt „App V3: Daten und Sync“), `ToDoS/v3-abnahme.md` (Sync-App-Punkte, soweit erfüllt)
- Test: `android/core/sync/src/androidTest/kotlin/de/foody/core/sync/EchteDatenSyncTest.kt` (übersprungen ohne Argumente)

- [ ] **Step 1: Echte Datenbank holen** (Controller, nicht Subagent; Gerät mit „Foody Debug“ per USB): `adb -s <gerät> exec-out run-as de.foody.app.debug cat databases/foody.db > foody-v1.foody-echt.db`, ebenso `-wal` und `-shm`; Zeilenzahlen je Tabelle mit `sqlite3` (auf einer Kopie, die den WAL einliest) notieren. Dateien bleiben außerhalb des Repos.
- [ ] **Step 2: `EchteDatenMigrationTest`** (Task 4) mit `-Pandroid.testInstrumentationRunnerArguments.echteDb=/data/local/tmp/foody-v1.foody-echt.db` und `echteDbZahlen=…` auf dem Emulator → PASS.
- [ ] **Step 3: `EchteDatenSyncTest`**: migrierte echte DB, Argumente `testServer=http://10.0.2.2:8081` (SSH-Tunnel zum Test-Server), Lauf mit dem vorhandenen Token → `Fertig`, danach: Outbox leer, keine `sync_problem`-Zeilen, lebende Datensätze lokal = Server (`GET /sync/pull?since=0` zählen), keine doppelten IDs.
- [ ] **Step 4:** Doku anpassen; `./gradlew check :core:data:testDebugUnitTest :core:sync:testDebugUnitTest` → PASS.
- [ ] **Step 5:** Commit `"V3 Phase 3: echte Daten geprüft, Doku"`; PR gegen `main` mit Auto-Merge, Zeile „Server aktualisieren? Nein“.

---

## Reihenfolge

1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11 → 12 → 13 (8 und 9 hängen nur an 2/6 und dürfen auch nach 10 kommen). Ein Branch, ein PR am Ende.
