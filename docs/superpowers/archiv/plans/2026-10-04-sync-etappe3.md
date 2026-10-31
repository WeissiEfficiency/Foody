# Sync Etappe 3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die App synchronisiert tatsächlich mit dem Server: Netzwerk-Client, sicherer Token-Speicher, ein Sync-Lauf (Push → Ergebnisse verarbeiten → Pull → Anwenden, Voll-Abgleich bei 410) und Hintergrund-Sync per WorkManager – bewiesen durch einen Ende-zu-Ende-Test mit zwei Geräten gegen den echten Server.

**Architecture:** `SyncApi` (Interface) mit Ktor-Client-Implementierung kapselt alle HTTP-Aufrufe und übersetzt Fehlercodes in Ausnahmen. `SyncEngine` orchestriert einen Lauf mit `SyncLocalStore`, `SyncApplier` und `SyncApi`. `TokenStore` verschlüsselt das Gerätetoken mit einem AES-Schlüssel aus dem Android Keystore. `SyncWorker` (Hilt-WorkManager) startet Läufe periodisch und kurz nach lokalen Änderungen. Der Ende-zu-Ende-Test läuft auf der JVM: Robolectric stellt Room/Android bereit, der echte `:server` läuft per Ktor `testApplication` im selben Prozess.

**Tech Stack:** Ktor Client 3.5.0 (`ktor-client-okhttp`, Content-Negotiation), WorkManager (`androidx.work:work-runtime-ktx`, neueste stabile 2.x, mindestens 2.10 – beim Umsetzen auf Google Maven prüfen), `androidx.hilt:hilt-work` + `androidx.hilt:hilt-compiler` 1.4.0 (gleiche Version wie `hiltLifecycleViewmodelCompose`), Robolectric 4.16.1 (Tests mit `@Config(sdk = [34])`, weil die CI JDK 17 nutzt), Android Keystore (`AndroidKeyStore`, AES/GCM).

**Spec:** `docs/superpowers/specs/2026-10-04-sync-server-design.md` (§2.3, §4.2–4.4, §6, §7, §8 Etappe 3). Vorarbeiten: Etappe 1 (#58), Etappe 2 (#60).

## Abweichungen / Entscheidungen

- **Ende-zu-Ende-Test auf der JVM (Robolectric + `testApplication`)** statt instrumentiert: Der Server nutzt sqlite-jdbc (JVM-nativ) und läuft nicht auf Android; so testen wir App-Code gegen den echten Server ohne Netzwerk und ohne Emulator, und der Test läuft in der CI mit.
- **Outbox-Reihenfolge monoton:** Migration v4 → v5 erneuert die Trigger so, dass ein erneutes Vormerken `queuedAt = MAX(now, queuedAt + 1)` setzt. Damit erkennt der Push zuverlässig, ob ein Datensatz während des Laufs erneut geändert wurde (gleiche Millisekunde inklusive). Schema sonst unverändert.
- **INTERNET-Berechtigung kommt in dieser Etappe** (der Client braucht sie); Klartext bleibt per `network_security_config` verboten, keine Nutzer-CAs. `docs/security.md` wird mitgepflegt. ADR 0006 folgt mit der UI in Etappe 5.
- **Kein UI**; `SyncEngine` und Konto-Aufrufe werden in Etappe 5 an die Einstellungen angeschlossen.

## Global Constraints

- Protokoll-Header `X-Foody-Protocol: 1` an jeder `/api/v1`-Anfrage; `Authorization: Bearer <token>` an allen authentifizierten.
- Fehlerabbildung (`SyncApiException` mit `code: ErrorCode?`, `status: Int`): 401 → `Unauthorized`, 409 `protocol_too_old`/`server_too_old` → `ProtocolMismatch`, 409 `no_household` → `NoHousehold`, 410 → `CursorExpired`, 413 → `TooLarge`, 429 → `Throttled`, sonst 4xx → `ClientError`, 5xx/IO/Timeout → `Transient`.
- Timeouts: Verbindung 15 s, Anfrage 60 s.
- Push in Batches à `Protocol.MAX_PUSH_RECORDS` (500), höchstens 20 Batches je Lauf; Pull mit `limit = 500` bis `hasMore = false`, alle Seiten sammeln, dann **einmal** `SyncApplier.apply`.
- Outbox-Eintrag wird nach `ACCEPTED`/`MERGED` nur entfernt, wenn `queuedAt` unverändert ist; sonst bleibt er, aber `sync_record_rev` wird auf die neue Revision gesetzt.
- `REJECTED(missing_reference)` → Eintrag bleibt in der Outbox (nächster Lauf versucht es erneut), `sync_problem` gesetzt; andere `REJECTED` → Eintrag entfernen, `sync_problem` gesetzt. Erfolgreich angewendete/akzeptierte Datensätze löschen ihr `sync_problem`.
- Token nur verschlüsselt (AES-256/GCM, Schlüssel im `AndroidKeyStore`, Alias `foody_sync_token`) in SharedPreferences `foody_sync`; nie loggen.
- WorkManager: eindeutige Arbeit `foody-sync-periodic` (15 min, `NetworkType.CONNECTED`, `ExistingPeriodicWorkPolicy.KEEP`) und `foody-sync-now` (einmalig, 5 s Verzögerung, `ExistingWorkPolicy.REPLACE`), Backoff exponentiell ab 30 s.
- Room-Version **5**, `MIGRATION_4_5` nur Trigger neu anlegen (`DROP TRIGGER IF EXISTS` + `SyncTriggers.create`), Schema-Export 5.json, Migrationstest. Trigger bleiben SQLite-3.18-kompatibel (JVM-Kompatibilitätstest erweitern).
- Room setzt `recursive_triggers = 1`; kein `OnConflictStrategy.REPLACE` auf Wurzeltabellen.
- Deutsche KDoc, englische Bezeichner.

## Review Focus

1. **Bearbeitung während eines laufenden Sync** (Nutzer hakt etwas ab, während der Push läuft): Änderung geht nicht verloren, Outbox-Eintrag bleibt → Test in Task 4.
2. **Ein kaputter Datensatz vom Server** (Constraint-Fehler beim Schreiben): nur dieser wird als Problem vermerkt, alle anderen kommen an, Cursor rückt vor → Test in Task 1.
3. **Token widerrufen** (Gerät in den Einstellungen eines anderen Geräts abgemeldet): Lauf endet sauber mit Status „abgemeldet“, lokale Daten und Outbox bleiben → Test in Task 4.
4. **Gerät war über 90 Tage offline** (410): Voll-Abgleich, lokale Datensätze ohne Server-Gegenstück und ohne offene Änderung verschwinden, offene Änderungen bleiben → Test in Task 4.
5. **Zwei Geräte legen offline dieselbe Zutat an**: nach beiden Läufen genau eine Zutat auf beiden Geräten, Rezepte zeigen auf sie → Test in Task 6.

---

## Dateistruktur

```
gradle/libs.versions.toml, app/build.gradle.kts            (Ktor-Client, WorkManager, hilt-work, Robolectric, testImplementation(project(":server")))
app/src/main/AndroidManifest.xml                          (INTERNET; WorkManager-Initializer für Hilt)
app/src/main/res/xml/network_security_config.xml          (Klartext aus, keine Nutzer-CAs – prüfen/ergänzen)
app/src/main/java/de/foody/app/data/db/
    FoodyDatabase.kt (v5, MIGRATION_4_5), SyncTriggers.kt (queuedAt monoton)
app/src/main/java/de/foody/app/sync/
    SyncApplier.kt       (Savepoint je Datensatz)
    SyncApi.kt           Interface + SyncApiException
    KtorSyncApi.kt       Ktor-Implementierung
    TokenStore.kt        Keystore-Verschlüsselung
    SyncEngine.kt        ein Sync-Lauf
    SyncWorker.kt        HiltWorker + SyncScheduler
app/src/main/java/de/foody/app/FoodyApp.kt                (HiltWorkerFactory / Configuration.Provider)
app/src/test/java/de/foody/app/sync/
    KtorSyncApiTest.kt, SyncEngineTest.kt (Robolectric + Fake-API), SyncEndToEndTest.kt (Robolectric + :server)
app/src/androidTest/java/de/foody/app/TokenStoreTest.kt, MigrationTest.kt (4→5), SyncApplierTest.kt (Savepoint)
docs/security.md, docs/architecture.md
```

Instrumented-Tests wie in Etappe 2 auf dem schreibgeschützten Emulator `emulator-5558` (siehe Plan Etappe 2), Klassen einzeln ausführen.

---

### Task 1: Pflichtpunkt – Savepoint je Datensatz im `SyncApplier`

**Files:** Modify `app/src/main/java/de/foody/app/sync/SyncApplier.kt`; Test `app/src/androidTest/java/de/foody/app/SyncApplierTest.kt`

**Interfaces:** Produces: unverändertes `apply(records, nextCursor): ApplyResult`; neuer Problemcode `"apply_failed"`.

- [ ] **Step 1: Failing test** `writeConstraintErrorOnlySkipsThatRecord` – lokal Rezept A mit Zeile `id = "x"`; Pull: Rezept B (gültiges Payload, aber Zeile `id = "x"` → Primärschlüssel-Kollision) und Zutat C → C angelegt, B nicht, `problems()` enthält `(recipe, B, "apply_failed")`, `cursor == nextCursor`, A unverändert. Zweiter Test `problemIsClearedAfterLaterSuccess` – Datensatz zuerst `missing_reference`, im nächsten Pull mit vorhandener Referenz → angelegt und kein Problem mehr.
- [ ] **Step 2: Run** (Emulator) `SyncApplierTest` → FAIL (Ausnahme bricht Transaktion ab).
- [ ] **Step 3: Implementieren:** innerhalb der Apply-Transaktion je lebendem Datensatz und je Löschung `SAVEPOINT sync_rec` über `db.openHelper.writableDatabase.execSQL`; bei `SQLiteException` `ROLLBACK TO sync_rec` + `RELEASE sync_rec`, Problem `apply_failed`; sonst `RELEASE sync_rec`. Nach erfolgreichem Schreiben `sync_problem` des Datensatzes löschen (neue DAO-Methode `clearProblem(type, id)`).
- [ ] **Step 4: Run** `SyncApplierTest` → PASS; gesamte Instrumented-Suite einmal.
- [ ] **Step 5: Commit** `git commit -m "Sync: Schreibfehler eines Datensatzes blockieren den Pull nicht mehr"`

---

### Task 2: Datenbank v5 – monotones `queuedAt`

**Files:** Modify `SyncTriggers.kt`, `FoodyDatabase.kt`; Create `app/schemas/.../5.json` (Build); Test `MigrationTest.kt`, `SyncTriggersTest.kt`, `app/src/test/.../SyncTriggersSqliteCompatTest.kt`

**Interfaces:** Produces: `MIGRATION_4_5`, Version 5; Trigger setzen im UPDATE-Zweig `queuedAt = MAX(<now>, queuedAt + 1)`.

- [ ] **Step 1: Failing tests:** `SyncTriggersTest.requeueAlwaysAdvancesQueuedAt` – Zutat aktiv anlegen, `queuedAt` merken, `UPDATE sync_outbox SET queuedAt = <jetzt + 10^9>` setzen, Zutat ändern → `queuedAt == alt + 1`; `MigrationTest.migrate4To5RecreatesTriggers` – v4-DB mit Outbox-Zeile → nach Migration Zeile erhalten, Trigger vorhanden, Schema validiert; Kompatibilitätstest auf sqlite-jdbc 3.18.0 deckt das neue SQL ab.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implementieren** (Migration: alle `sync_%`-Trigger `DROP TRIGGER IF EXISTS`, dann `SyncTriggers.create`; `docs/architecture.md` Tabellenzeile 5).
- [ ] **Step 4: Run** `MigrationTest`, `SyncTriggersTest` (Emulator), `:app:testDebugUnitTest` → PASS.
- [ ] **Step 5: Commit** `git commit -m "Sync: Datenbank v5 – Outbox-Zeitstempel streng steigend"`

---

### Task 3: `SyncApi` + Ktor-Client, Abhängigkeiten, INTERNET

**Files:** Create `SyncApi.kt`, `KtorSyncApi.kt`; Modify `libs.versions.toml`, `app/build.gradle.kts`, `AndroidManifest.xml`, `network_security_config.xml`, `docs/security.md`, ProGuard-Regeln falls R8 es verlangt; Test `app/src/test/.../KtorSyncApiTest.kt`

**Interfaces:**
- Produces:
  - `interface SyncApi { suspend fun login(req: LoginRequest): AuthResponse; suspend fun register(req: RegisterRequest): AuthResponse; suspend fun households(): List<HouseholdDto>; suspend fun createHousehold(name: String): HouseholdDto; suspend fun selectHousehold(id: String); suspend fun createInvite(): InviteDto; suspend fun devices(): List<DeviceDto>; suspend fun revokeDevice(id: String); suspend fun push(records: List<SyncRecord>): PushResponse; suspend fun pull(since: Long, limit: Int): PullResponse }`
  - `sealed class SyncApiException(val status: Int, val code: ErrorCode?) : Exception()` mit Unterklassen aus den Global Constraints (`Unauthorized`, `ProtocolMismatch`, `NoHousehold`, `CursorExpired`, `TooLarge`, `Throttled`, `ClientError`, `Transient`).
  - `class KtorSyncApi(private val baseUrl: String, private val client: HttpClient, private val token: () -> String?) : SyncApi` – `baseUrl` ohne abschließenden `/`; Header nach Global Constraints; JSON über `Protocol.json`. Fabrik `fun defaultHttpClient(): HttpClient` (OkHttp, Timeouts).
- Abhängigkeiten: `ktor-client-core`, `ktor-client-okhttp`, `ktor-client-content-negotiation` (vorhanden), `ktor-serialization-kotlinx-json` (vorhanden) in `implementation`; `robolectric` 4.16.1, `ktor-server-test-host` und `testImplementation(project(":server"))` in `testImplementation`; WorkManager/hilt-work erst in Task 5.

- [ ] **Step 1: Failing tests** (`KtorSyncApiTest`, JVM, Ktor `testApplication` mit dem echten `foodyModule` aus `:server` und `TestServer`-ähnlicher Einrichtung; `KtorSyncApi` bekommt den `client` der Testanwendung):
  - `loginSendsProtocolHeaderAndReturnsToken` – Admin-Bootstrap, `login` → Token nicht leer.
  - `pushAndPullRoundTrip` – Haushalt anlegen, Zutat pushen → `ACCEPTED`, Pull → Datensatz.
  - `errorsMapToTypedExceptions` – ungültiges Token → `Unauthorized`; Gerät ohne Haushalt pusht → `NoHousehold`; `since` hinter kompaktierter Revision (Kompaktierung im Test erzwingen) → `CursorExpired`.
  - `baseUrlWithTrailingSlashWorks` – `"https://x/"` und `"https://x"` erzeugen gleiche Pfade.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*KtorSyncApiTest'` → FAIL.
- [ ] **Step 3: Implementieren**; Manifest `INTERNET`; `network_security_config`: `cleartextTrafficPermitted="false"`, nur System-CAs; `docs/security.md`: Netzwerk-Zeile („nur HTTPS zum selbst gehosteten Sync-Server, optional“), neue Zeile Token-Speicherung (folgt in Task 4).
- [ ] **Step 4: Run** `:app:testDebugUnitTest`, `:app:assembleRelease` (R8!) → PASS.
- [ ] **Step 5: Commit** `git commit -m "Sync: Netzwerk-Client für den Sync-Server"`

---

### Task 4: `TokenStore` und `SyncEngine`

**Files:** Create `TokenStore.kt`, `SyncEngine.kt`; Modify `SyncDao.kt` (falls Abfragen fehlen); Tests `app/src/androidTest/.../TokenStoreTest.kt`, `app/src/test/.../SyncEngineTest.kt` (Robolectric `@Config(sdk = [34])`, Fake-`SyncApi`)

**Interfaces:**
- Produces:
  - `@Singleton class TokenStore @Inject constructor(@ApplicationContext ctx)`: `fun save(token: String)`, `fun load(): String?`, `fun clear()` – AES/GCM, Schlüssel im `AndroidKeyStore` (Alias `foody_sync_token`), IV + Chiffrat Base64 in SharedPreferences `foody_sync`; nicht entschlüsselbar → `null` (und Eintrag löschen).
  - `class SyncEngine(private val db: FoodyDatabase, private val store: SyncLocalStore, private val applier: SyncApplier, private val api: SyncApi, private val clock: Clock)` mit `suspend fun run(): SyncOutcome`; `sealed interface SyncOutcome { data class Success(val pushed: Int, val pulled: Int, val problems: Int); data object Unauthorized; data class ProtocolMismatch(val serverTooOld: Boolean); data object NoHousehold; data class Failed(val transient: Boolean, val message: String) }`. `run()` schreibt `lastSyncAt`/`lastError` in `sync_state`; inaktiver Sync → `Success(0,0,0)` ohne Netzwerk.
  - Ablauf `run()`: (1) Push-Schleife: `pendingRecords()` → `api.push` → je Ergebnis: `ACCEPTED` → `setRev`, Problem löschen, dequeue wenn `queuedAt` unverändert; `MERGED` → `setRev`, dequeue wenn unverändert, dann `current` per `applier.apply(listOf(current), keepCursor)` anwenden (Zutaten-Duplikate führt Regel 4 dort zusammen); `REJECTED` → nach Global Constraints. (2) Pull: Seiten sammeln ab `cursor`, `applier.apply(all, nextCursor)`. (3) `CursorExpired` → Voll-Abgleich: alle Seiten ab 0, anwenden, danach lokale Wurzeldatensätze ohne Server-Gegenstück in diesem Abzug und ohne Outbox-Eintrag löschen (mit `applyingRemote = 1`).
  - `SyncApplier.apply` bekommt einen Parameter `updateCursor: Boolean = true` (für das Anwenden einzelner `current`-Datensätze).

- [ ] **Step 1: Failing tests:**
  - `TokenStoreTest` (Instrumented): `roundTrip`, `clearRemoves`, `corruptedValueReturnsNull`, `storedValueIsNotPlaintext` (SharedPreferences-Inhalt enthält das Token nicht).
  - `SyncEngineTest` (Robolectric, In-Memory-Room mit `SYNC_CALLBACK`, Fake-API mit Protokoll der Aufrufe):
    - `inactiveSyncDoesNothing` – kein API-Aufruf.
    - `acceptedRecordsAreDequeued` – Outbox mit Zutat → Fake antwortet `ACCEPTED(rev=3)` → Outbox leer, `revOf == 3`.
    - `editDuringPushKeepsEntry` – Fake ändert innerhalb von `push` die Zutat lokal (simuliert Nutzeraktion) → Outbox-Eintrag bleibt, `revOf == 3` (Review Focus 1).
    - `mergedAppliesCurrent` – Einkaufseintrag gepusht, Fake antwortet `MERGED(current = checked=true)` → lokal `checked = true`, Outbox leer.
    - `rejectedMissingReferenceStaysQueued` / `rejectedInvalidIsDropped`.
    - `unauthorizedStopsCleanly` – Fake wirft `Unauthorized` → `SyncOutcome.Unauthorized`, Outbox unverändert, `lastError = "unauthorized"` (Review Focus 3).
    - `cursorExpiredTriggersFullResync` – lokal Zutaten A (Server kennt sie), B (Server kennt sie nicht, keine Outbox), C (Outbox offen); Pull wirft `CursorExpired`, Voll-Abzug liefert nur A → A bleibt, B gelöscht, C bleibt (Review Focus 4).
    - `pullIsAppliedOnceAfterAllPages` – Fake liefert 3 Seiten → `applier` einmal mit allen Datensätzen, Cursor = letzte Seite.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implementieren**; `docs/security.md` Zeile Token-Speicherung.
- [ ] **Step 4: Run** `:app:testDebugUnitTest`, `TokenStoreTest` (Emulator) → PASS.
- [ ] **Step 5: Commit** `git commit -m "Sync: Token sicher speichern und Sync-Lauf"`

---

### Task 5: `SyncWorker` und Planung

**Files:** Create `SyncWorker.kt` (inkl. `SyncScheduler`); Modify `FoodyApp.kt`, `AndroidManifest.xml`, `libs.versions.toml`, `app/build.gradle.kts`; Test `app/src/test/.../SyncWorkerTest.kt` (Robolectric + `work-testing`)

**Interfaces:**
- Produces:
  - `@HiltWorker class SyncWorker @AssistedInject constructor(@Assisted ctx, @Assisted params, private val engineFactory: SyncEngineFactory) : CoroutineWorker` – `doWork`: `Success`/`NoHousehold`/`Unauthorized`/`ProtocolMismatch` → `Result.success()` (Zustand steht in `sync_state`), `Failed(transient = true)` → `Result.retry()`, sonst `Result.failure()`.
  - `@Singleton class SyncEngineFactory @Inject constructor(db, store, applier, tokenStore)` mit `fun create(): SyncEngine?` (null, wenn Sync inaktiv oder kein Token/URL).
  - `@Singleton class SyncScheduler @Inject constructor(@ApplicationContext ctx, db: FoodyDatabase)`: `fun schedulePeriodic()`, `fun requestSoon()`, `fun cancelAll()`, `fun startObservingOutbox(scope: CoroutineScope)` – beobachtet `sync_outbox` (Room `InvalidationTracker`/Flow auf `SELECT COUNT(*)`), ruft bei Zuwachs `requestSoon()`; nur wenn Sync aktiv.
  - `FoodyApp` implementiert `Configuration.Provider` mit `HiltWorkerFactory`; Manifest entfernt den Standard-`WorkManagerInitializer` (`tools:node="remove"` im `InitializationProvider`). Beim App-Start: wenn Sync aktiv → `schedulePeriodic()` + `startObservingOutbox`.
- [ ] **Step 1: Failing tests** (`SyncWorkerTest`, `TestListenableWorkerBuilder` mit Fake-Engine-Factory): `transientFailureRetries`, `unauthorizedSucceedsWithoutRetry`, `inactiveSyncSucceeds`; `SyncSchedulerTest`: `requestSoonEnqueuesUniqueWorkWithDelay` (WorkManager-Testinitialisierung, `getWorkInfosForUniqueWork("foody-sync-now")` → 1 Eintrag, Status ENQUEUED), `periodicIsKeptOnSecondCall`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implementieren.**
- [ ] **Step 4: Run** `:app:testDebugUnitTest`, `:app:assembleRelease` → PASS; App einmal auf dem Emulator starten (Smoke: kein Absturz beim Start mit inaktivem Sync) – `adb -s emulator-5558 install` + `am start`, Logcat ohne `FATAL`.
- [ ] **Step 5: Commit** `git commit -m "Sync: Hintergrund-Sync mit WorkManager"`

---

### Task 6: Ende-zu-Ende-Test mit zwei Geräten gegen den echten Server

**Files:** Create `app/src/test/java/de/foody/app/sync/SyncEndToEndTest.kt`

**Interfaces:** Consumes alles aus Task 1–4; Server über `testApplication { application { foodyModule(deps) } }` mit `ServerDeps.create` und In-Memory-SQLite; zwei Robolectric-Room-Datenbanken (`inMemoryDatabaseBuilder` + `SYNC_CALLBACK`), je `SyncLocalStore`, `SyncApplier`, `KtorSyncApi(client = testApplication.client)`, `SyncEngine`.

- [ ] **Step 1: Tests** (`@Config(sdk = [34])`):
  - `recipeTravelsBetweenDevices` – A: Haushalt anlegen, `activate(uploadExisting = true)`, Rezept mit 2 Zutaten speichern, `run()`; B: per Einladung registrieren, `activate(uploadExisting = false)`, `run()` → B hat Rezept, Zeilen, Zutaten.
  - `concurrentCheckAndAmountChangeMerge` – Einkaufseintrag auf beiden; A hakt ab, B ändert Menge (beide offline), A `run()`, B `run()`, A `run()` → beide: abgehakt **und** neue Menge.
  - `sameIngredientOfflineOnBothDevicesBecomesOne` – A und B legen offline „Zwiebel“ an und nutzen sie in je einem Rezept; A `run()`, B `run()`, A `run()` → beide genau eine „Zwiebel“, beide Rezepte zeigen auf dieselbe ID (Review Focus 5).
  - `deleteOnOneDeviceRemovesOnOther` – A löscht Rezept, beide `run()` → bei B weg (inkl. Planpositionen).
  - `revokedDeviceStopsSyncing` – A widerruft B per `revokeDevice`; B `run()` → `Unauthorized`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*SyncEndToEndTest'` → zuerst rot, wo die Engine noch Lücken hat; Lücken in den betroffenen Klassen schließen (nicht im Test).
- [ ] **Step 3: Run** gesamte Suite `:sync-protocol:test :server:test :domain:test :app:testDebugUnitTest` → PASS.
- [ ] **Step 4: Commit** `git commit -m "Sync: Ende-zu-Ende-Test mit zwei Geräten gegen den Server"`

---

## Abschluss

- [ ] Gesamtlauf JVM + gesamte Instrumented-Suite (Emulator 5558) + `:app:assembleRelease` grün.
- [ ] PR gegen `main` (Etappe 3 von 5) mit Auto-Merge. Etappe 4 (Fotos) und 5 (Einstellungen-UI, ADR 0006) folgen.
