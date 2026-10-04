# Sync-Server Etappe 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ein lauffähiger, per Docker Compose hinter Traefik betreibbarer Foody-Sync-Server mit Konten, Haushalten, Einladungen und Push/Pull-Sync (ohne Fotos), plus das gemeinsame Protokollmodul.

**Architecture:** Neues reines Kotlin-Modul `:sync-protocol` (DTOs, Validierung, Konstanten) wird vom neuen Modul `:server` (Ktor 3, SQLite über JDBC) genutzt; die App bindet es erst in Etappe 2 ein. Der Server speichert Datensätze als JSON je Haushalt mit streng steigender Revision und wendet die Konfliktregeln aus Spec 4.3 an. Docker-Image wird aus dem Repo-Root gebaut; `settings.gradle.kts` lässt die Android-Module per Umgebungsvariable weg, damit der Build im Container kein Android-SDK braucht.

**Tech Stack:** Kotlin 2.4.20 (JVM, Java-17-Bytecode), kotlinx.serialization 1.11.0, Ktor Server 3.x (≥ 3.4.1, Netty), xerial sqlite-jdbc 3.53.1.0, Bouncy Castle `bcprov-jdk18on` 1.86 (Argon2id), Logback, JUnit 5 über `kotlin-test`, Ktor `ktor-server-test-host`; Docker (`eclipse-temurin:21-jdk` Build, `eclipse-temurin:21-jre` Laufzeit), Compose v2.

**Spec:** `docs/superpowers/specs/2026-10-04-sync-server-design.md` (Abschnitte 2.1, 2.2, 3, 4.1–4.4, 6, 7, 8 – Etappe 1)

## Abweichungen von der Spec (bewusst)

- **Kein Exposed, kein Flyway:** Plain JDBC und eine eigene Migrationsliste über `PRAGMA user_version` (wie Room). Für eine SQLite-Datei mit einem Dutzend Tabellen sind beide Bibliotheken mehr Abhängigkeit als Nutzen; Flyways SQLite-Modulzuschnitt ist zudem versionsabhängig.
- **Protokollversion als Header** `X-Foody-Protocol` an jeder `/api/v1`-Anfrage statt nur im Push-Body – eine Prüfstelle für alle Endpunkte.
- **Push-Ergebnis trägt den Server-Stand** (`current`) bei `merged`/`rejected` durch eine Konfliktregel. Sonst behielte ein Gerät, das die Löschmarkierung wegen eines offenen Outbox-Eintrags übersprungen hat, den Datensatz für immer (Spec 4.2 Punkt 4).
- **Hinweis für Etappe 2:** `main` ist inzwischen bei Room-Version 3 (`rating`, `shopping_item.note`). Die App-Migration der Spec heißt dort also v3 → v4.

## Global Constraints

- Protokoll: `PROTOCOL_VERSION = 1`, `MIN_PROTOCOL_VERSION = 1`, Header `X-Foody-Protocol`.
- Fehlt der Header oder ist er < 1 → `409 {"code":"protocol_too_old"}`; > 1 → `409 {"code":"server_too_old"}`. `/health` braucht keinen Header.
- Push: höchstens **500** Datensätze und **5 MB** Body → sonst `413 {"code":"too_large"}`. Pull: `limit` Standard und Maximum **500**.
- Passwort: mindestens **10**, höchstens **200** Zeichen. Benutzername: 3–32 Zeichen `[A-Za-z0-9._-]`, eindeutig ohne Groß-/Kleinschreibung. Gerätename ≤ 64, Haushaltsname 1–100 Zeichen.
- Argon2id: Speicher **19456 KiB**, Iterationen **2**, Parallelität **1**, Salt 16 Byte, Hash 32 Byte; Format `$argon2id$v=19$m=19456,t=2,p=1$<salt b64>$<hash b64>` (Base64 ohne Padding).
- Gerätetoken: 32 Byte `SecureRandom`, Base64url ohne Padding; gespeichert nur SHA-256 (hex).
- Einladungscode: `FOODY-XXXX-XXXX`, Crockford-Base32 (`0123456789ABCDEFGHJKMNPQRSTVWXYZ`, 40 Bit Zufall); Eingabe normalisieren (Großbuchstaben, Leerzeichen/Bindestriche weg, `O→0`, `I`/`L→1`, Präfix `FOODY` weg); gespeichert nur SHA-256 der 8 Zeichen; gültig **7 Tage**, einmal nutzbar.
- Login-Drossel: **5** Fehlversuche je (IP, Benutzername klein) → **15 min** gesperrt (`429 {"code":"throttled"}`); Erfolg setzt zurück. Fehlertext unterscheidet nicht zwischen unbekanntem Benutzer und falschem Passwort (`401 {"code":"invalid_credentials"}`).
- Zahlen in Payloads: Strings, Länge ≤ 40, Skala −6…20, Präzision ≤ 30 (wie `BackupRepository.parseDecimal`). Namen ≤ 200, Notizen/Schritttexte ≤ 10 000, Tags ≤ 1 000, IDs als UUID.
- Einheiten: `MILLIGRAM, GRAM, KILOGRAM, MILLILITER, CENTILITER, LITER, TEASPOON, TABLESPOON, PIECE, PACKAGE, CAN`; Nährwertbasis `PER_100_G, PER_100_ML`; Datumswerte ISO-8601 `LocalDate`.
- Löschmarkierungen werden nach **90 Tagen** kompaktiert (beim Start und alle 24 h).
- Haushalts-ID kommt **immer aus dem Gerät** (Token), nie aus Pfad/Body einer Sync-Anfrage.
- Container: Port 8080 nur intern, Nicht-Root (UID 10001), `read_only`, `cap_drop: [ALL]`, `no-new-privileges`.
- Code-Stil wie `:domain`: `allWarningsAsErrors`, Java-17-Bytecode, deutsche KDoc-Kommentare, englische Bezeichner.

## Review Focus

1. **Benutzername mit anderer Groß-/Kleinschreibung** („Stefan“ vs. „stefan“): Login klappt, zweite Registrierung wird als `409 username_taken` abgelehnt → Test in Task 3 und Task 4.
2. **Derselbe Datensatz zweimal in einem Push** (Gerät hat schnell zweimal gespeichert): beide werden der Reihe nach angewendet, der zweite gewinnt, zwei Revisionen → Test in Task 5.
3. **Unbekannte Felder im Payload** einer neueren App-Version: Validierung ignoriert sie, gespeichert und beim Pull ausgeliefert wird das Original-JSON unverändert → Test in Task 5.
4. **Pull mit `since` jenseits der aktuellen Revision** (z. B. nach Server-Wiederherstellung aus älterer Sicherung): leere Liste, `nextCursor = since`, kein Fehler; `hasMore = false` → Test in Task 5.
5. **Gerät ohne gewählten Haushalt ruft Sync auf**: `409 {"code":"no_household"}` statt 500 oder leerer Daten → Test in Task 5.

---

## Dateistruktur

```
settings.gradle.kts                          (ändern: :sync-protocol, :server; Android-Module per FOODY_SERVER_ONLY weglassen)
gradle/libs.versions.toml                    (ändern: ktor, sqlite, bouncycastle, logback)
sync-protocol/build.gradle.kts
sync-protocol/src/main/kotlin/de/foody/sync/protocol/
    Protocol.kt        Konstanten, Header, Grenzwerte
    Records.kt         RecordType, SyncRecord, Push*/Pull*-DTOs
    Payloads.kt        typisierte Payloads je RecordType
    Accounts.kt        Auth-/Haushalts-/Geräte-DTOs
    Errors.kt          ErrorCode, ErrorDto
    PayloadValidator.kt Validierung + Referenzen je Payload
sync-protocol/src/test/kotlin/de/foody/sync/protocol/ProtocolTest.kt, PayloadValidatorTest.kt
server/build.gradle.kts
server/Dockerfile, server/docker-compose.yml, server/.env.example, server/README.md
server/src/main/kotlin/de/foody/server/
    Main.kt            Einstieg: serve | healthcheck | admin …
    ServerConfig.kt    Umgebungsvariablen
    ServerDeps.kt      Verdrahtung (Database, Clock, Stores, Services)
    Application.kt     Ktor-Modul: Plugins, Fehler, Protokoll-Header, Routen
    db/Database.kt     JDBC-Verbindung, WAL, Transaktionen
    db/Migrations.kt   Schema als nummerierte SQL-Liste
    auth/PasswordHasher.kt, auth/Tokens.kt, auth/LoginThrottle.kt, auth/InviteCodes.kt
    auth/AccountStore.kt   user, device, household, membership, invite
    auth/AuthRoutes.kt     login, register, password + Bearer-Auth
    household/HouseholdRoutes.kt  households, device/household, invites, devices, members
    sync/RecordStore.kt    sync_record, Revisionen
    sync/SyncService.kt    Push-Regeln (LWW, Feld-Merge, Löschen, Zutaten-Merge, Referenzen)
    sync/SyncRoutes.kt     push, pull
    sync/Compactor.kt      Löschmarkierungen kompaktieren
    admin/AdminCli.kt      reset-password, invite, backup, compact
server/src/main/resources/logback.xml
server/src/test/kotlin/de/foody/server/
    TestServer.kt (Testhilfe), DatabaseTest.kt, AuthTest.kt, HouseholdTest.kt, SyncTest.kt,
    ConflictTest.kt, CompactionTest.kt, AdminCliTest.kt
.github/workflows/ci.yml                     (ändern: Job server)
```

---

### Task 1: Modul `:sync-protocol`

**Files:**
- Create: `sync-protocol/build.gradle.kts`, die sechs Dateien unter `sync-protocol/src/main/kotlin/de/foody/sync/protocol/`
- Modify: `settings.gradle.kts`, `gradle/libs.versions.toml`, `build.gradle.kts` (nichts neu – Plugins sind schon deklariert)
- Test: `sync-protocol/src/test/kotlin/de/foody/sync/protocol/ProtocolTest.kt`, `PayloadValidatorTest.kt`

**Interfaces:**
- Produces:
  - `object Protocol { const val VERSION = 1; const val MIN_VERSION = 1; const val HEADER = "X-Foody-Protocol"; const val MAX_PUSH_RECORDS = 500; const val MAX_PUSH_BYTES = 5_242_880L; const val MAX_PULL_LIMIT = 500; val json: Json }` – `json` mit `ignoreUnknownKeys = true`, `explicitNulls = false`, `encodeDefaults = true`.
  - `enum class RecordType(val wire: String)` mit `@SerialName` = `wire`: `INGREDIENT("ingredient"), RECIPE("recipe"), MEAL_SLOT("meal_slot"), PANTRY_ITEM("pantry_item"), SHOPPING_LIST("shopping_list"), SHOPPING_ITEM("shopping_item")` – Deklarationsreihenfolge = Abhängigkeitsreihenfolge (Spec 4.3).
  - `@Serializable data class SyncRecord(val id: String, val type: RecordType, val deleted: Boolean = false, val updatedAt: Long, val baseRev: Long? = null, val rev: Long? = null, val payload: JsonObject? = null)`
  - `@Serializable data class PushRequest(val records: List<SyncRecord>)`
  - `enum class PushStatus { ACCEPTED, MERGED, REJECTED }` (Wire: `accepted`, `merged`, `rejected`)
  - `@Serializable data class PushResult(val id: String, val type: RecordType, val status: PushStatus, val rev: Long? = null, val canonicalId: String? = null, val code: ErrorCode? = null, val current: SyncRecord? = null)`
  - `@Serializable data class PushResponse(val results: List<PushResult>)`
  - `@Serializable data class PullResponse(val records: List<SyncRecord>, val nextCursor: Long, val hasMore: Boolean)`
  - Payloads (alle `@Serializable`, Zahlen als `String`): `IngredientPayload(name, category?, density?, pieceWeight?, basis?, energyKj?, protein?, carbs?, fat?, fiber?, sugar?, salt?, source?)`; `RecipePayload(name, servings: Int, prep: Int?, cook: Int?, photo: String?, notes?, tags: String, archivedAt: Long?, favorite: Boolean, sourceUrl?, rating: Int?, lines: List<Line>, steps: List<Step>)` mit `Line(id, ingredientId, amount, unit, sortOrder: Int, note?, optional: Boolean)` und `Step(id, position: Int, text)`; `MealSlotPayload(date, slotType, recipeId, servings: Int, cookedAt: Long?)`; `PantryItemPayload(ingredientId, amount, unit, bestBefore?)`; `ShoppingListPayload(name, start?, end?, version: Int)`; `ShoppingItemPayload(listId, ingredientId?, name, amount?, unit?, checked: Boolean, checkedChangedAt: Long, manual: Boolean, category?, sortOrder: Int, note?, sources: List<Source>)` mit `Source(id, mealSlotId, recipeIngredientId, recipeName, date, amount, unit)`. Feldnamen wie `BackupDto` in `app/.../BackupRepository.kt`.
  - `fun RecordType.decode(payload: JsonObject): Any` – dekodiert in den passenden Payload-Typ (wirft `SerializationException`).
  - Accounts: `LoginRequest(username, password, deviceName)`, `RegisterRequest(code, username, password, deviceName)`, `PasswordChangeRequest(oldPassword, newPassword)`, `AuthResponse(token, userId, householdId: String?)`, `HouseholdDto(id, name, role: String)`, `CreateHouseholdRequest(name)`, `SelectHouseholdRequest(householdId)`, `InviteDto(code, expiresAt: Long)`, `DeviceDto(id, name, lastSeenAt: Long?, current: Boolean)`.
  - `enum class ErrorCode` (Wire = snake_case): `PROTOCOL_TOO_OLD, SERVER_TOO_OLD, INVALID_CREDENTIALS, THROTTLED, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, NO_HOUSEHOLD, USERNAME_TAKEN, INVALID_INVITE, INVALID_INPUT, INVALID_PAYLOAD, MISSING_REFERENCE, TOO_LARGE, CURSOR_EXPIRED`; `@Serializable data class ErrorDto(val code: ErrorCode)`.
  - `object PayloadValidator { fun validate(record: SyncRecord): ErrorCode?; fun references(record: SyncRecord): List<Pair<RecordType, String>>; fun canonicalName(record: SyncRecord): String? }` – `validate` prüft Hülle (UUID, Payload vorhanden genau wenn `!deleted`) und Payload nach den Global Constraints; `references`: recipe → alle `lines[].ingredientId` (INGREDIENT); meal_slot → recipeId (RECIPE); pantry_item → ingredientId; shopping_item → listId (SHOPPING_LIST) + ingredientId falls gesetzt; `canonicalName`: nur INGREDIENT, `name.trim().lowercase()`.

- [ ] **Step 1: Gradle einrichten.** `libs.versions.toml`: Versionen `ktor` (neueste stabile 3.x, mindestens `3.4.1`, bei Umsetzung auf Maven Central prüfen), `sqliteJdbc = "3.53.1.0"`, `bouncycastle = "1.86"`, `logback` (neueste 1.5.x); Bibliotheken `ktor-server-core`, `ktor-server-netty`, `ktor-server-content-negotiation`, `ktor-serialization-kotlinx-json`, `ktor-server-status-pages`, `ktor-server-auth`, `ktor-server-forwarded-header`, `ktor-server-test-host`, `ktor-client-content-negotiation`, `sqlite-jdbc`, `bcprov` (`org.bouncycastle:bcprov-jdk18on`), `logback-classic`. `settings.gradle.kts`: `include(":sync-protocol")`, `include(":server")`, und `:app`/`:baselineprofile` nur einbinden, wenn `System.getenv("FOODY_SERVER_ONLY") == null` (Kommentar: Docker-Build ohne Android-SDK). `sync-protocol/build.gradle.kts` wie `domain/build.gradle.kts` plus Plugin `kotlin.serialization` und `api(libs.serialization.json)`.

- [ ] **Step 2: Failing tests schreiben** in `ProtocolTest.kt`:
  - `recordTypesSerializeAsWireNames` – `Protocol.json.encodeToString(RecordType.MEAL_SLOT) == "\"meal_slot\""`.
  - `recordTypeOrderIsDependencyOrder` – `RecordType.entries.map { it.wire } == listOf("ingredient","recipe","meal_slot","pantry_item","shopping_list","shopping_item")`.
  - `pushResponseRoundTrips` – `PushResponse` mit je einem `ACCEPTED(rev=7)`, `MERGED(canonicalId=…, current=SyncRecord(…))`, `REJECTED(code=MISSING_REFERENCE)` → encode/decode gleich; JSON enthält `"status":"merged"` und `"code":"missing_reference"`.
  - `everyPayloadRoundTripsThroughDecode` – je RecordType ein Beispiel-Payload als `JsonObject` → `type.decode(...)` liefert den richtigen Typ mit gleichen Werten.
  - `unknownFieldsAreIgnoredOnDecode` – `IngredientPayload`-JSON mit Zusatzfeld `"futureField":1` dekodiert ohne Fehler.

- [ ] **Step 3: Failing tests schreiben** in `PayloadValidatorTest.kt`:
  - `validRecordsPass` – gültiger Datensatz je Typ → `null`.
  - `deletedRecordMustNotCarryPayload` / `liveRecordNeedsPayload` → `INVALID_PAYLOAD`.
  - `idMustBeUuid` – `id = "../x"` → `INVALID_PAYLOAD`.
  - `decimalLimits` – `amount` `"1E999999999"`, `"1".repeat(41)`, Präzision 31 → `INVALID_PAYLOAD`; `"0.000001"` ok.
  - `textLimits` – Rezeptname 201 Zeichen, Schritttext 10 001 Zeichen → `INVALID_PAYLOAD`; genau 200 / 10 000 ok.
  - `unknownUnitIsRejected` – `unit = "BUCKET"` → `INVALID_PAYLOAD`; `bestBefore = "2026-13-01"` → `INVALID_PAYLOAD`.
  - `referencesFollowDependencyRules` – Rezept mit zwei Zeilen auf Zutaten `a`,`b` → `[(INGREDIENT,a),(INGREDIENT,b)]`; Einkaufseintrag ohne `ingredientId` → nur `(SHOPPING_LIST, listId)`.
  - `canonicalNameIsTrimmedLowercase` – `" Zwiebel "` → `"zwiebel"`; für RECIPE → `null`.

- [ ] **Step 4: Tests laufen lassen, Fehlschlag prüfen.** Run: `./gradlew :sync-protocol:test` → FAIL (Klassen fehlen).

- [ ] **Step 5: Implementieren** der Dateien nach den Interfaces oben.

- [ ] **Step 6: Tests grün.** Run: `./gradlew :sync-protocol:test :domain:test` → BUILD SUCCESSFUL.

- [ ] **Step 7: Commit.** `git add settings.gradle.kts gradle/libs.versions.toml sync-protocol && git commit -m "Sync-Protokoll: gemeinsame DTOs und Validierung"`

---

### Task 2: Server-Grundgerüst (Datenbank, Konfiguration, /health, Protokoll-Header)

**Files:**
- Create: `server/build.gradle.kts`, `Main.kt`, `ServerConfig.kt`, `ServerDeps.kt`, `Application.kt`, `db/Database.kt`, `db/Migrations.kt`, `server/src/main/resources/logback.xml`
- Test: `server/src/test/kotlin/de/foody/server/TestServer.kt`, `DatabaseTest.kt`

**Interfaces:**
- Consumes: `Protocol`, `ErrorCode`, `ErrorDto` (Task 1)
- Produces:
  - `data class ServerConfig(val dbPath: String, val port: Int, val adminUser: String?, val adminPassword: String?) { companion object { fun fromEnv(env: Map<String, String> = System.getenv()): ServerConfig } }` – `FOODY_DB_PATH` (Standard `/data/foody.db`), `FOODY_PORT` (8080), `FOODY_ADMIN_USER`, `FOODY_ADMIN_PASSWORD`.
  - `class Database(url: String) : AutoCloseable { fun <T> tx(block: (Connection) -> T): T }` – eine Verbindung, `synchronized`, `autoCommit=false` in `tx`, Rollback bei Ausnahme; beim Öffnen `PRAGMA journal_mode=WAL` (nicht bei `:memory:`), `PRAGMA foreign_keys=ON`, `PRAGMA busy_timeout=5000`, dann `Migrations.apply(connection)`.
  - `object Migrations { val all: List<String>; fun apply(c: Connection) }` – Eintrag *n* hebt `user_version` von *n* auf *n+1*; jede Migration in eigener Transaktion.
  - `class ServerDeps(val config: ServerConfig, val db: Database, val clock: Clock)` – spätere Tasks ergänzen Felder (`accounts`, `throttle`, `sync`, …).
  - `fun Application.foodyModule(deps: ServerDeps)` – installiert `ContentNegotiation(Protocol.json)`, `XForwardedHeaders`, `StatusPages` (unbekannte Ausnahme → `500` ohne Details; `ApiException(status, code)` → `status` + `ErrorDto(code)`), Protokoll-Prüfung als Route-Plugin auf `/api/v1`, `GET /health` → `200 {"status":"ok"}`.
  - `class ApiException(val status: HttpStatusCode, val code: ErrorCode) : RuntimeException()`
  - `fun main(args: Array<String>)` – ohne Argumente Server starten (Netty, `0.0.0.0:port`), `healthcheck` → HTTP GET `http://127.0.0.1:$port/health`, Exit-Code 0/1; `admin …` folgt in Task 7.
  - Testhilfe `fun testServer(clock: MutableClock = MutableClock(), block: suspend ApplicationTestBuilder.(TestEnv) -> Unit)` mit `Database("jdbc:sqlite::memory:")`; `class MutableClock(var now: Instant = Instant.parse("2026-10-04T10:00:00Z")) : Clock` mit `advance(Duration)`; `fun HttpRequestBuilder.protocol()` setzt den Header.
- Schema Migration 1 (alle Tabellen der Etappe auf einmal, IDs `TEXT`, Zeiten `INTEGER` Epoch-ms):
  `user(id PK, username, username_lower UNIQUE, password_hash, display_name, is_admin, created_at)`,
  `household(id PK, name, created_by, created_at, last_rev INTEGER NOT NULL DEFAULT 0, compacted_before_rev INTEGER NOT NULL DEFAULT 0)`,
  `membership(user_id, household_id, role, created_at, PK(user_id, household_id))`,
  `device(id PK, user_id, household_id NULL, name, token_hash UNIQUE, created_at, last_seen_at, revoked_at NULL)`,
  `invite(id PK, code_hash UNIQUE, household_id NULL, created_by, expires_at, used_at NULL)`,
  `sync_record(household_id, type, id, rev, deleted, payload TEXT NULL, updated_at, deleted_at NULL, canonical_name NULL, PK(household_id, type, id))`,
  Indizes `sync_record(household_id, rev)` und `sync_record(household_id, type, canonical_name)`.

- [ ] **Step 1: `server/build.gradle.kts`** – Plugins `kotlin.jvm`, `kotlin.serialization`, `application`; `application { mainClass = "de.foody.server.MainKt"; applicationName = "foody-server" }`; Abhängigkeiten `project(":sync-protocol")`, Ktor-Server-Bibliotheken, `sqlite-jdbc`, `bcprov`, `logback-classic`; Tests: `kotlin-test`, `ktor-server-test-host`, `ktor-client-content-negotiation`, `coroutines-test`; Java/Kotlin-Optionen wie `domain/build.gradle.kts`; `tasks.test { useJUnitPlatform() }`.

- [ ] **Step 2: Failing tests** in `DatabaseTest.kt`:
  - `migrationsRaiseUserVersionToLatest` – frische `:memory:`-DB → `PRAGMA user_version == Migrations.all.size`; erneutes `Migrations.apply` ändert nichts.
  - `txRollsBackOnException` – Insert in `household`, dann Ausnahme in `tx` → Tabelle leer.
  - `healthNeedsNoProtocolHeader` – `GET /health` → 200, Body `{"status":"ok"}`.
  - `apiWithoutHeaderIsProtocolTooOld` – `GET /api/v1/households` ohne Header → 409 `protocol_too_old`; Header `2` → 409 `server_too_old`.
  - `configDefaults` – `ServerConfig.fromEnv(emptyMap())` → `/data/foody.db`, 8080, Admin `null`.

- [ ] **Step 3: Run** `./gradlew :server:test` → FAIL.
- [ ] **Step 4: Implementieren** nach Interfaces. Für `/api/v1/households` reicht in dieser Task ein Platzhalter-Routenblock, an dem die Protokoll-Prüfung hängt (Task 4 füllt ihn).
- [ ] **Step 5: Run** `./gradlew :server:test` → PASS.
- [ ] **Step 6: Commit** `git commit -m "Sync-Server: Grundgerüst mit SQLite, Migrationen und Healthcheck"`

---

### Task 3: Konten, Anmeldung, Gerätetoken, Drossel

**Files:**
- Create: `auth/PasswordHasher.kt`, `auth/Tokens.kt`, `auth/LoginThrottle.kt`, `auth/AccountStore.kt`, `auth/AuthRoutes.kt`
- Modify: `ServerDeps.kt` (Felder `accounts`, `throttle`, `hasher`), `Application.kt` (Bearer-Auth, Routen), `Main.kt` (Bootstrap-Admin vor Serverstart)
- Test: `server/src/test/kotlin/de/foody/server/AuthTest.kt`

**Interfaces:**
- Consumes: `Database.tx`, `ApiException`, DTOs `LoginRequest`, `AuthResponse`, `PasswordChangeRequest`
- Produces:
  - `class PasswordHasher(random: SecureRandom = SecureRandom()) { fun hash(password: String): String; fun verify(password: String, encoded: String): Boolean }` – Bouncy Castle `Argon2BytesGenerator`, Parameter aus Global Constraints, Vergleich mit `MessageDigest.isEqual`.
  - `object Tokens { fun newToken(): String; fun sha256Hex(value: String): String }`
  - `class LoginThrottle(clock: Clock) { fun check(ip: String, username: String); fun failure(ip: String, username: String); fun success(ip: String, username: String) }` – `check` wirft `ApiException(429, THROTTLED)`.
  - `class AccountStore(db: Database, clock: Clock, hasher: PasswordHasher)` mit
    `fun bootstrapAdmin(username: String?, password: String?): Boolean` (nur wenn keine Benutzer existieren),
    `fun createUser(username: String, password: String, isAdmin: Boolean = false): String` (wirft `ApiException(409, USERNAME_TAKEN)` / `(400, INVALID_INPUT)`),
    `fun authenticate(username: String, password: String): String?` (User-ID; prüft auch bei unbekanntem Benutzer gegen einen Dummy-Hash, damit die Laufzeit nichts verrät),
    `fun createDevice(userId: String, householdId: String?, name: String): String` (liefert Klartext-Token),
    `fun deviceForToken(token: String): DevicePrincipal?` (aktualisiert `last_seen_at` höchstens einmal pro Minute),
    `fun changePassword(userId: String, currentDeviceId: String, old: String, new: String)` (widerruft alle anderen Geräte des Benutzers).
  - `data class DevicePrincipal(val deviceId: String, val userId: String, val householdId: String?)` (Ktor 3 braucht kein `Principal`-Interface mehr)
  - Routen: `POST /api/v1/auth/login` → `AuthResponse`; `POST /api/v1/auth/password` (authentifiziert) → 204. Bearer-Provider `"device"`: fehlender/ungültiger/widerrufener Token oder Mitgliedschaft im Haushalt weg → `401 UNAUTHORIZED`.

- [ ] **Step 1: Failing tests** in `AuthTest.kt`:
  - `hashUsesArgon2idParameters` – `hash("geheimgeheim")` beginnt mit `$argon2id$v=19$m=19456,t=2,p=1$`; `verify` richtig/falsch.
  - `bootstrapAdminOnlyOnEmptyDatabase` – erster Aufruf `true`, zweiter mit anderem Namen `false`; ohne Variablen `false` und kein Benutzer.
  - `loginReturnsTokenAndTokenAuthenticates` – Login → `AuthResponse(token, userId, householdId = null)`; `GET /api/v1/devices` mit Token → 200, ohne → 401.
  - `loginIsCaseInsensitiveOnUsername` – Benutzer „Stefan“, Login „stefan“ → 200 (Review Focus 1).
  - `wrongPasswordAndUnknownUserLookTheSame` – beide → 401 mit identischem Body `{"code":"invalid_credentials"}`.
  - `fiveFailuresLockForFifteenMinutes` – 5 × falsch → 6. Versuch (auch richtiges Passwort) 429; `clock.advance(15.minutes)` → richtiges Passwort 200.
  - `passwordPolicy` – `createUser` mit 9 Zeichen → `INVALID_INPUT`; Benutzername `"a b"` → `INVALID_INPUT`.
  - `changePasswordRevokesOtherDevices` – zwei Geräte; Gerät 1 ändert Passwort → Gerät 2 erhält 401, Gerät 1 weiter 200; altes Passwort beim Login 401.
  - `tokenIsStoredOnlyAsHash` – `SELECT token_hash FROM device` ≠ Klartext-Token, = `Tokens.sha256Hex(token)`.
  (`GET /api/v1/devices` als einfacher authentifizierter Endpunkt wird hier minimal angelegt und in Task 4 vervollständigt.)
- [ ] **Step 2: Run** `./gradlew :server:test --tests '*AuthTest'` → FAIL.
- [ ] **Step 3: Implementieren.**
- [ ] **Step 4: Run** `./gradlew :server:test` → PASS.
- [ ] **Step 5: Commit** `git commit -m "Sync-Server: Konten, Argon2id, Gerätetoken und Login-Drossel"`

---

### Task 4: Haushalte, Einladungen, Registrierung, Geräte, Mitglieder

**Files:**
- Create: `auth/InviteCodes.kt`, `household/HouseholdRoutes.kt`
- Modify: `auth/AccountStore.kt` (Haushalt-/Einladungs-/Mitgliedsfunktionen), `auth/AuthRoutes.kt` (`register`), `Application.kt`
- Test: `server/src/test/kotlin/de/foody/server/HouseholdTest.kt`

**Interfaces:**
- Consumes: `AccountStore`, `DevicePrincipal`, DTOs aus Task 1
- Produces:
  - `object InviteCodes { fun generate(random: SecureRandom): String /* "FOODY-XXXX-XXXX" */; fun normalize(input: String): String? /* 8 Zeichen oder null */ }`
  - `AccountStore`: `createHousehold(userId, name): HouseholdDto` (Rolle `owner`, setzt Haushalt am Gerät), `households(userId): List<HouseholdDto>`, `selectHousehold(deviceId, userId, householdId)` (403 `FORBIDDEN` ohne Mitgliedschaft), `createInvite(createdBy: String, householdId: String?): InviteDto`, `redeemInvite(code, username, password, deviceName): AuthResponse` (alles in einer Transaktion; `400 INVALID_INVITE` bei unbekannt/abgelaufen/benutzt), `devices(userId, currentDeviceId): List<DeviceDto>`, `revokeDevice(userId, deviceId)` (404 für fremde/unbekannte), `removeMember(ownerId, householdId, memberId)` (403 für Nicht-Owner; widerruft Geräte des Mitglieds mit diesem Haushalt).
  - Routen (alle authentifiziert außer `register`): `POST /api/v1/auth/register`, `GET /api/v1/households`, `POST /api/v1/households`, `POST /api/v1/device/household`, `POST /api/v1/invites` (409 `NO_HOUSEHOLD` ohne Haushalt), `GET /api/v1/devices`, `DELETE /api/v1/devices/{id}`, `DELETE /api/v1/households/{id}/members/{userId}`.

- [ ] **Step 1: Failing tests** in `HouseholdTest.kt`:
  - `inviteCodeFormatAndNormalization` – `generate` matcht `^FOODY-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}$`; `normalize("foody-abcd-efgh")`, `normalize("ABCD EFGH")` gleich; `normalize("OOOO-IIII")` = `"00001111"`; `normalize("zu kurz")` = `null`.
  - `ownerInvitesPartnerWhoJoinsHousehold` – A legt Haushalt „Zuhause“ an, erzeugt Einladung; B registriert sich mit Code → `AuthResponse.householdId` = Haushalt von A; `GET /api/v1/households` für B → Rolle `member`.
  - `inviteIsSingleUseAndExpires` – zweite Registrierung mit demselben Code → 400 `invalid_invite`; neuer Code, `clock.advance(7.days + 1.seconds)` → 400.
  - `registerRejectsTakenUsernameCaseInsensitive` – „stefan“ existiert, Registrierung „STEFAN“ → 409 `username_taken`; Einladung bleibt danach unbenutzt (Transaktion) (Review Focus 1).
  - `cannotSelectForeignHousehold` – B wählt Haushalt von C → 403.
  - `inviteWithoutHouseholdIsNoHousehold` – Gerät ohne Haushalt `POST /api/v1/invites` → 409 `no_household`.
  - `devicesListMarksCurrentAndRevokeWorks` – zwei Geräte; Liste zeigt `current=true` genau einmal; Widerruf des anderen → dessen Token 401; Widerruf eines fremden Geräts → 404.
  - `removingMemberRevokesTheirDevicesForThatHousehold` – Owner entfernt B → B 401; Member versucht Owner zu entfernen → 403.
- [ ] **Step 2: Run** `./gradlew :server:test --tests '*HouseholdTest'` → FAIL.
- [ ] **Step 3: Implementieren.**
- [ ] **Step 4: Run** `./gradlew :server:test` → PASS.
- [ ] **Step 5: Commit** `git commit -m "Sync-Server: Haushalte, Einladungen, Registrierung und Geräteverwaltung"`

---

### Task 5: Push/Pull mit Revisionen, Validierung, Referenzen, Haushalts-Trennung

**Files:**
- Create: `sync/RecordStore.kt`, `sync/SyncService.kt`, `sync/SyncRoutes.kt`
- Modify: `ServerDeps.kt`, `Application.kt`
- Test: `server/src/test/kotlin/de/foody/server/SyncTest.kt`

**Interfaces:**
- Consumes: `PayloadValidator`, `SyncRecord`, `PushRequest/Response`, `PullResponse`, `DevicePrincipal`
- Produces:
  - `class RecordStore` (arbeitet auf einer übergebenen `Connection`): `fun get(c: Connection, household: String, type: RecordType, id: String): SyncRecord?`, `fun put(c: Connection, household: String, record: SyncRecord, rawPayload: String?, canonicalName: String?): Long` (vergibt `last_rev + 1`, schreibt `household.last_rev`, setzt `deleted_at` bei Löschung, liefert `rev`), `fun exists(c, household, type, id): Boolean` (lebend, nicht gelöscht), `fun findLiveByCanonicalName(c, household, name): SyncRecord?`, `fun page(c, household, since: Long, limit: Int): List<SyncRecord>` (nach `rev` aufsteigend).
  - `class SyncService(db: Database, store: RecordStore, clock: Clock) { fun push(household: String, records: List<SyncRecord>): PushResponse; fun pull(household: String, since: Long, limit: Int): PullResponse }` – Push verarbeitet Datensätze **der Reihe nach**, jeder in eigener Transaktion; Pull in einer Lesetransaktion. Rohes Payload-JSON wird unverändert gespeichert und ausgeliefert.
  - In dieser Task gilt für alle Typen reines LWW: `validate` → `REJECTED(INVALID_PAYLOAD)`; fehlende Referenz → `REJECTED(MISSING_REFERENCE)`; sonst speichern → `ACCEPTED(rev)`. Die Sonderregeln (Feld-Merge, Löschen vs. Bearbeiten, Zutaten-Merge) folgen in Task 6 als eigene Zweige in `SyncService.applyOne`.
  - Routen: `POST /api/v1/sync/push` (Body-Größe vor dem Parsen über `Content-Length`/gezähltes Lesen prüfen; > 500 Datensätze → 413), `GET /api/v1/sync/pull?since=&limit=` (`limit` auf 1…500 begrenzen, `since` < 0 → 400 `INVALID_INPUT`). Beide verlangen `principal.householdId != null`, sonst 409 `NO_HOUSEHOLD`.

- [ ] **Step 1: Failing tests** in `SyncTest.kt` (Testhilfe: `setupHousehold(): Pair<String /*token*/, String /*householdId*/>`, Beispiel-Datensätze `ingredient(id, name)`, `recipe(id, ingredientIds)`, `shoppingList(id)`, `shoppingItem(id, listId, checked, checkedChangedAt)`):
  - `pushThenPullReturnsRecordsWithIncreasingRevs` – Zutat + Rezept pushen → `ACCEPTED` mit rev 1, 2; Pull `since=0` → beide in Reihenfolge, `nextCursor=2`, `hasMore=false`.
  - `pullPagesWithLimit` – 5 Datensätze, `limit=2` → Seiten 2/2/1, `hasMore` true/true/false, Cursor 2/4/5.
  - `sameRecordTwiceInOnePushLastWins` – derselbe Datensatz mit `name` „A“ dann „B“ → zwei `ACCEPTED` (rev 1, 2); Pull liefert genau einen Datensatz mit „B“ und `rev=2` (Review Focus 2).
  - `unknownPayloadFieldsArePreserved` – Zutat mit `"futureField":"x"` → Pull liefert Payload mit `futureField` (Review Focus 3).
  - `pullBeyondHeadIsEmpty` – nach 2 Datensätzen Pull `since=10` → `records=[]`, `nextCursor=10`, `hasMore=false` (Review Focus 4).
  - `deviceWithoutHouseholdGetsNoHousehold` – frisch eingeloggtes Gerät ohne Haushalt: push und pull → 409 `no_household` (Review Focus 5).
  - `invalidRecordIsRejectedOthersAccepted` – drei Datensätze, mittlerer mit `amount="1E999999999"` → `[ACCEPTED, REJECTED(invalid_payload), ACCEPTED]`.
  - `missingReferenceIsRejected` – Rezept auf unbekannte Zutat → `REJECTED(missing_reference)`; dieselbe Zutat im **selben** Push davor → beide `ACCEPTED`.
  - `householdsAreIsolated` – Haushalt A pusht Zutat `x`; Gerät aus Haushalt B: Pull `since=0` leer; B pusht Rezept mit Referenz auf `x` → `missing_reference` (kein Durchgriff per geratener ID).
  - `revsAreCountedPerHousehold` – A und B pushen je einen Datensatz → beide `rev=1`.
  - `tooManyRecordsIsTooLarge` – 501 Datensätze → 413 `too_large`.
- [ ] **Step 2: Run** `./gradlew :server:test --tests '*SyncTest'` → FAIL.
- [ ] **Step 3: Implementieren.**
- [ ] **Step 4: Run** `./gradlew :server:test` → PASS.
- [ ] **Step 5: Commit** `git commit -m "Sync-Server: Push/Pull mit Revisionen, Validierung und Haushalts-Trennung"`

---

### Task 6: Konfliktregeln (Spec 4.3)

**Files:**
- Modify: `sync/SyncService.kt`
- Test: `server/src/test/kotlin/de/foody/server/ConflictTest.kt`

**Interfaces:**
- Consumes: `RecordStore`, `SyncService` (Task 5)
- Produces: geändertes Verhalten von `SyncService.push`; Konflikt = gespeicherter Datensatz existiert und `incoming.baseRev == null || incoming.baseRev < stored.rev`.

Regeln (in dieser Reihenfolge in `applyOne`):
1. **Zutaten-Merge:** eingehende lebende Zutat, deren `canonicalName` einer **anderen** lebenden Zutat-ID im Haushalt gehört → nicht speichern, `MERGED(rev = stored.rev, canonicalId = stored.id, current = stored)`.
2. **Löschen vs. Bearbeiten:** gespeichert gelöscht, eingehend lebend → wenn `incoming.baseRev != null && incoming.baseRev >= stored.rev` speichern (`ACCEPTED`), sonst `MERGED(rev = stored.rev, current = stored)` ohne Speichern.
3. **`shopping_item` bei Konflikt** (beide lebend): Ergebnis = eingehender Payload, aber `checked`/`checkedChangedAt` vom Datensatz mit größerem `checkedChangedAt`; bei Gleichstand `checked = true`. Speichern, `MERGED(rev, current = gespeicherter Stand)`. Ohne Konflikt: `ACCEPTED`.
4. Alles andere: LWW wie Task 5 (`ACCEPTED`); eingehende Löschung wird immer gespeichert.
Das Zusammenbauen des Merge-Payloads verändert nur die beiden Felder im rohen `JsonObject`, damit unbekannte Felder erhalten bleiben.

- [ ] **Step 1: Failing tests** in `ConflictTest.kt`:
  - `checkedSurvivesConcurrentAmountChange` – Eintrag rev 1 (`checked=false, checkedChangedAt=100`); Gerät A pusht `checked=true, checkedChangedAt=200, baseRev=1` → `ACCEPTED`; Gerät B pusht `amount="3", checked=false, checkedChangedAt=100, baseRev=1` → `MERGED`; Pull: `amount="3"`, `checked=true`.
  - `laterUncheckWins` – nach dem Haken (200) pusht B `checked=false, checkedChangedAt=300, baseRev=1` → Ergebnis `checked=false`.
  - `checkedTieGoesToChecked` – beide `checkedChangedAt=200`, einer true, einer false (Konflikt) → `checked=true`.
  - `deleteWinsOverStaleEdit` – Rezept rev 1; A löscht (rev 2); B pusht Bearbeitung mit `baseRev=1` → `MERGED`, `current.deleted == true`; Pull liefert Löschung.
  - `editAfterSeenDeleteRevives` – wie oben, aber B pusht mit `baseRev=2` → `ACCEPTED`, Pull lebend.
  - `duplicateIngredientNameMergesToOlderId` – Zutat `a` „Zwiebel“; Push Zutat `b` „ zwiebel“ → `MERGED(canonicalId="a", current=a)`; Pull enthält kein `b`.
  - `renamingIngredientToSameNameAsItselfIsNoConflict` – Zutat `a` „Zwiebel“ erneut mit Kategorie geändert → `ACCEPTED`.
  - `deletedIngredientNameIsFree` – Zutat `a` „Zwiebel“ gelöscht; neue Zutat `b` „Zwiebel“ → `ACCEPTED`.
  - `plainLastWriterWins` – Planposition: A pusht `servings=2, baseRev=1`, B pusht `servings=4, baseRev=1` → beide `ACCEPTED`, Ergebnis 4.
  - `mergeKeepsUnknownFields` – Einkaufseintrag mit `"futureField"` im Konfliktfall → Feld bleibt im Ergebnis.
- [ ] **Step 2: Run** `./gradlew :server:test --tests '*ConflictTest'` → FAIL.
- [ ] **Step 3: Implementieren.**
- [ ] **Step 4: Run** `./gradlew :server:test` → PASS.
- [ ] **Step 5: Commit** `git commit -m "Sync-Server: Konfliktregeln für Abhaken, Löschen und doppelte Zutaten"`

---

### Task 7: Kompaktierung, `410`, Admin-CLI

**Files:**
- Create: `sync/Compactor.kt`, `admin/AdminCli.kt`
- Modify: `sync/SyncService.kt` (410), `Main.kt` (`admin`-Befehle, Kompaktierung beim Start + alle 24 h per Coroutine)
- Test: `server/src/test/kotlin/de/foody/server/CompactionTest.kt`, `AdminCliTest.kt`

**Interfaces:**
- Consumes: `Database`, `RecordStore`, `AccountStore`, `InviteCodes`
- Produces:
  - `class Compactor(db: Database, clock: Clock, retention: Duration = Duration.ofDays(90)) { fun run(): Int }` – löscht Löschmarkierungen mit `deleted_at < now − retention`, setzt je Haushalt `compacted_before_rev = max(compacted_before_rev, größte entfernte rev)`; liefert Anzahl.
  - `SyncService.pull`: `since != 0 && since < compacted_before_rev` → `ApiException(410, CURSOR_EXPIRED)`.
  - `class AdminCli(deps: ServerDeps, out: PrintStream) { fun run(args: List<String>): Int }` – Befehle: `reset-password <user>` (druckt neues Zufallspasswort, 20 Zeichen, widerruft alle Geräte des Benutzers), `invite` (Konto-Einladung ohne Haushalt, druckt Code), `backup <zieldatei>` (`VACUUM INTO`, Ziel darf nicht existieren), `compact`; unbekannt → Hilfe, Exit-Code 2. `main(["admin", …])` ruft `AdminCli` mit `ServerConfig.fromEnv()` auf.

- [ ] **Step 1: Failing tests:**
  - `CompactionTest.oldTombstonesAreRemovedAndCursorExpires` – Datensatz anlegen (rev 1), löschen (rev 2), `clock.advance(91.days)`, `run()` = 1; Pull `since=1` → 410 `cursor_expired`; Pull `since=0` → nur lebende Datensätze, kein Fehler; Pull `since=2` → 200.
  - `CompactionTest.recentTombstonesStay` – Löschung vor 89 Tagen → `run()` = 0, Pull `since=1` liefert die Löschung.
  - `AdminCliTest.resetPasswordPrintsWorkingPasswordAndRevokesDevices` – Ausgabe-Passwort loggt ein; altes Gerätetoken 401.
  - `AdminCliTest.inviteCreatesAccountInvite` – gedruckter Code registriert Konto mit `householdId = null`.
  - `AdminCliTest.backupWritesConsistentCopy` – Datei-DB in Temp-Ordner, `backup <ziel>` → Ziel öffnet als SQLite mit gleicher Benutzerzahl; vorhandenes Ziel → Exit-Code ≠ 0, Datei unverändert.
  - `AdminCliTest.unknownCommandShowsHelp` – Exit-Code 2, Ausgabe enthält `reset-password`.
- [ ] **Step 2: Run** `./gradlew :server:test` → FAIL.
- [ ] **Step 3: Implementieren.**
- [ ] **Step 4: Run** `./gradlew :server:test` → PASS.
- [ ] **Step 5: Commit** `git commit -m "Sync-Server: Kompaktierung von Löschmarkierungen und Admin-CLI"`

---

### Task 8: Docker-Image, Compose, CI

**Files:**
- Create: `server/Dockerfile`, `server/docker-compose.yml`, `server/.env.example`, `server/README.md`, `.dockerignore` (Repo-Root)
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: `foody-server` (installDist), Befehle `healthcheck` und `admin` aus Task 2/7
- Produces: Image, das mit `FOODY_SERVER_ONLY=1` gebaut wird; Compose-Dienst `foody-server`.

Festlegungen:
- **Dockerfile** (Build-Kontext = Repo-Root): Stufe 1 `eclipse-temurin:21-jdk`, `ENV FOODY_SERVER_ONLY=1`, kopiert `gradlew`, `gradle/`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `domain/`, `sync-protocol/`, `server/`; `./gradlew --no-daemon :server:installDist`. Stufe 2 `eclipse-temurin:21-jre`, Benutzer `foody` (UID/GID 10001), `/opt/foody` ← `server/build/install/foody-server`, `/data` angelegt und `foody` gehörend, Skript `/usr/local/bin/foody-admin` (`exec /opt/foody/bin/foody-server admin "$@"`), `USER 10001`, `EXPOSE 8080`, `HEALTHCHECK --interval=60s --timeout=10s --start-period=30s CMD ["/opt/foody/bin/foody-server","healthcheck"]` (startet je Prüfung eine JVM – daher 60 s), `ENTRYPOINT ["/opt/foody/bin/foody-server"]`. `JAVA_OPTS` mit `-XX:MaxRAMPercentage=75`.
- **`.dockerignore`:** `**/build`, `.gradle`, `.git`, `app/`, `baselineprofile/`, `local.properties`, `*.jks`, `*.keystore`.
- **docker-compose.yml:** `services.foody-server` mit `build: { context: .., dockerfile: server/Dockerfile }`, `image: foody-server:local`, `restart: unless-stopped`, `env_file: .env`, `volumes: [foody-data:/data, foody-photos:/data/photos]`, `read_only: true`, `tmpfs: ["/tmp:exec,mode=1777"]` (sqlite-jdbc entpackt seine native Bibliothek nach `/tmp`; Dockers tmpfs ist sonst `noexec` und das Laden schlägt fehl), `cap_drop: [ALL]`, `security_opt: [no-new-privileges:true]`, `networks: [traefik]`, Labels:
  `traefik.enable=true`,
  `traefik.docker.network=${TRAEFIK_NETWORK}`,
  `traefik.http.routers.foody.rule=Host(\`${FOODY_HOST}\`)`,
  `traefik.http.routers.foody.entrypoints=${TRAEFIK_ENTRYPOINT:-websecure}`,
  `traefik.http.routers.foody.tls.certresolver=${TRAEFIK_CERTRESOLVER}`,
  `traefik.http.services.foody.loadbalancer.server.port=8080`;
  `networks.traefik: { external: true, name: ${TRAEFIK_NETWORK} }`; Volumes `foody-data`, `foody-photos`. Kein `ports:`.
- **`.env.example`:** `FOODY_HOST=foody.example.lan`, `TRAEFIK_NETWORK=traefik`, `TRAEFIK_ENTRYPOINT=websecure`, `TRAEFIK_CERTRESOLVER=letsencrypt`, `FOODY_ADMIN_USER=`, `FOODY_ADMIN_PASSWORD=` jeweils mit einzeiligem Kommentar.
- **README (deutsch, kurz):** Voraussetzungen (Traefik-Netzwerk, DNS/WireGuard), `cp .env.example .env`, `docker compose up -d --build`, erster Admin, Einladung per `docker compose exec foody-server foody-admin invite`, Sicherung per `foody-admin backup /data/backup-$(date +%F).db`, Update (`git pull && docker compose up -d --build`), Hinweis: Admin-Variablen nach dem ersten Start leeren.
- **CI-Job `server`** (wie bestehende Jobs: gepinnte Actions, `permissions: contents: read`): `./gradlew :sync-protocol:test :server:test`; `docker build -f server/Dockerfile -t foody-server:ci .`; Smoke-Test: `docker run -d --name foody -e FOODY_ADMIN_USER=admin -e FOODY_ADMIN_PASSWORD=ci-passwort-123 -e FOODY_DB_PATH=/tmp/foody.db -p 8080:8080 foody-server:ci` (Dateisystem hier beschreibbar), warten bis `curl -fsS localhost:8080/health`, dann `curl -fsS -H 'X-Foody-Protocol: 1' -H 'Content-Type: application/json' -d '{"username":"admin","password":"ci-passwort-123","deviceName":"ci"}' localhost:8080/api/v1/auth/login | grep -q token`; `docker compose -f server/docker-compose.yml --env-file server/.env.example config -q`.

- [ ] **Step 1: Dateien anlegen** nach Festlegungen.
- [ ] **Step 2: Compose-Datei prüfen.** Run: `docker compose -f server/docker-compose.yml --env-file server/.env.example config -q` → Exit 0, keine Ausgabe.
- [ ] **Step 3: Image bauen.** Run: `docker build -f server/Dockerfile -t foody-server:local .` → erfolgreich, ohne Android-SDK.
- [ ] **Step 4: Smoke-Test lokal** mit den `docker run`/`curl`-Befehlen aus dem CI-Job → `/health` 200, Login liefert `token`; `docker exec foody foody-admin invite` druckt `FOODY-…`; danach `docker rm -f foody`.
- [ ] **Step 5: Healthcheck und Nur-Lese-Betrieb prüfen.** Zusätzlich einmal per `docker compose up` mit einer Test-`.env` und einem vorher angelegten Netzwerk `docker network create traefik` starten: Container läuft mit `read_only` + tmpfs ohne Fehler beim Laden von sqlite-jdbc. Run: `docker inspect --format '{{.State.Health.Status}}' foody` nach spätestens 90 s → `healthy`.
- [ ] **Step 6: Bestehende Builds unverändert.** Run: `./gradlew :domain:test :app:testDebugUnitTest` → BUILD SUCCESSFUL (ohne `FOODY_SERVER_ONLY`).
- [ ] **Step 7: Commit** `git commit -m "Sync-Server: Docker-Image, Compose für Traefik und CI-Job"`

---

## Abschluss

- [ ] Gesamtlauf: `./gradlew :sync-protocol:test :server:test :domain:test :app:testDebugUnitTest` → grün.
- [ ] PR gegen `main` (Etappe 1 von 5); Beschreibung nennt die Abweichungen oben und den Hinweis zu Room v3 → v4 für Etappe 2.
