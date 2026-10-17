# V3 Phase 2: Server schlank – Umsetzungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Der Server in `main` kennt nur noch Konten statt Haushalten, Push/Pull, Fotos und ein KI-Gerüst unter `/api/v3`; bestehende Daten werden beim ersten Start umgewandelt.

**Architecture:** Harter Schnitt: Der echte Server bleibt auf dem eingefrorenen Branch `v1-server`; `:sync-protocol` bleibt unverändert für die v1-App, der Server wechselt auf das neue Modul `:api-protocol`. Die Umwandlung ist Server-Migration 1 → 2 (eine Transaktion) mit automatischer Sicherung davor und einem fortsetzbaren Fotoumzug danach.

**Tech Stack:** Kotlin/JVM 17-Bytecode, Ktor 3 (Netty, testApplication), SQLite (sqlite-jdbc), kotlinx.serialization, kotlin.test/JUnit 5, Gradle 9.

**Spec:** [`docs/superpowers/specs/2026-10-17-v3-phase2-server-schlank-design.md`](../specs/2026-10-17-v3-phase2-server-schlank-design.md)

## Global Constraints

- Routen unter `/api/v3`; `Protocol.VERSION = 3`, `MIN_VERSION = 3`, Header `X-Foody-Protocol`.
- `/api/v1/*` und Header < 3 → `409` mit `protocol_too_old`; Header > 3 → `409` mit `server_too_old`.
- `:sync-protocol` und der Produktionscode von `:app` werden **nicht** verändert.
- Kein Phase-2-PR wird gemergt, bevor Task 1 erledigt ist (echter Server auf `v1-server`).
- Konto = Zeile in `user`; die Konto-ID ist `user.id`.
- Sicherung vor Migration: `vor-migration-<ziel>-<JJJJ-MM-TT-HHMM>.db` neben der Datenbankdatei, per `VACUUM INTO`.
- `ErrorCode` in v3 ohne `no_household`, `username_taken`, `invalid_invite`, `forbidden`.
- KI: `FOODY_KI_KEY`, `FOODY_KI_PRO_STUNDE` (Standard 30); kein Modellaufruf.
- Kotlin mit `allWarningsAsErrors`; Kommentare und Meldungen auf Deutsch wie im übrigen Server-Code.
- Jeder PR: Auto-Merge an, Zeile „Server aktualisieren?“ – hier immer „nur Test-Server“.
- Builds unter dem Pfad mit „ö“: `android.overridePathCheck=true` steht in `~/.gradle/gradle.properties`; `JAVA_HOME` = JBR von Android Studio.

## Review Focus

1. **Zweiter Start nach erfolgreicher Migration** – keine zweite Sicherung, kein erneuter Fotoumzug, keine Fehlermeldung (Tests in Task 4 und 6).
2. **Sicherung scheitert** (Zieldatei existiert schon / nicht schreibbar) – dann darf **nicht** migriert werden; `user_version` bleibt 1 (Test in Task 4).
3. **Apps mit altem Sync-Stand** – `rev` und `last_rev` bleiben erhalten, ein Pull mit dem alten `since` liefert nichts doppelt und nichts fehlt (Test in Task 5).
4. **Konto ohne Haushalt** (der Admin aus der Ersteinrichtung) – bleibt anmeldbar, hat 0 Datensätze (Test in Task 5).
5. **Widerrufene Geräte** – bleiben nach der Migration widerrufen; ihr Token wird mit `401` abgewiesen (Test in Task 5).

---

### Task 1: Eingefrorener Stand `v1-server`

Betrieb, kein Code. **Voraussetzung:** Stefan hat sich bei Forgejo neu angemeldet (`git push forgejo main` im E:-Ordner fragt dann nach Zugangsdaten). Ohne das: hier anhalten und fragen.

- [ ] **Step 1: Tag und Branch auf dem aktuellen `main` anlegen**

```bash
git fetch origin
git tag -a v1-server origin/main -m "Letzter Stand mit Haushalten und Protokoll 2 (echter Server bis V3 Phase 8)"
git branch v1-server origin/main
git push origin v1-server refs/tags/v1-server
```
Im E:-Checkout zusätzlich: `git push forgejo refs/heads/v1-server refs/tags/v1-server` – Erwartet: beide Refs ohne Fehler.

- [ ] **Step 2: Checkout des echten Servers umstellen** (`C:\Windows\System32\OpenSSH\ssh.exe homeserver.lan`)

```sh
cd ~/docker/foody && git fetch forgejo && git switch -c v1-server --track forgejo/v1-server && git log --oneline -1
```
Erwartet: derselbe Commit wie `origin/main` aus Step 1; `git status -sb` zeigt `## v1-server...forgejo/v1-server`. Kein Neubau nötig.

- [ ] **Step 3: Notiz `foody-server-deployed` (Memory) ergänzen:** echter Server auf `v1-server`, Fehlerbehebungen per Cherry-Pick.

---

### Task 2: Web-Oberfläche entfernen

**Files:**
- Delete: `server/web/` (ganzes Modul), `server/sync/src/test/kotlin/de/foody/server/ErweiterungTest.kt`
- Delete: `android/app/src/test/java/de/foody/app/sync/KtorSyncApiTest.kt`, `.../TestSyncServer.kt`
- Modify: `settings.gradle.kts` (`:server-web` raus), `server/start/build.gradle.kts`, `server/start/src/main/kotlin/de/foody/server/Main.kt`, `server/sync/src/main/kotlin/de/foody/server/Application.kt` (`foodyModule`), `android/app/build.gradle.kts:205-206`, `README.md`, `server/README.md` (Abschnitt „Web-Oberfläche“ entfernen)

**Interfaces:**
- Produces: `fun Application.foodyModule(deps: ServerDeps)` (ohne `erweiterungen`).

- [ ] **Step 1: Dateien löschen und Verweise entfernen** (Liste oben). In `Main.kt` `foodyModule(deps)`; im README die Web-Zeilen ersetzen durch „Die Web-Oberfläche ist mit V3 entfallen (ADR 0011).“ Die gelöschten v1-App-Tests bekommen keinen Ersatz: Sie laufen am Tag `v1-server` weiter.
- [ ] **Step 2: Prüfen**

Run: `./gradlew :server-sync:test :server:installDist :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; `grep -rn "server.web\|WebOberflaeche\|server-web" --include=*.kts --include=*.kt .` liefert nichts.

- [ ] **Step 3: Commit** – `git commit -m "V3 Phase 2: Web-Oberfläche entfernen"` (PR erst nach Task 1 mergen)

---

### Task 3: Modul `:api-protocol` (Protokoll 3)

**Files:**
- Create: `shared/api-protocol/build.gradle.kts` (wie `shared/sync-protocol/build.gradle.kts`), `shared/api-protocol/src/main/kotlin/de/foody/api/protocol/{Protocol,Errors,Records,Payloads,PayloadValidator,Photos,ZahlGrenzen,Accounts,Ki}.kt`
- Create: `shared/api-protocol/src/test/kotlin/de/foody/api/protocol/{PayloadValidatorTest,ProtocolTest,ZahlGrenzenTest}.kt` (Kopien, angepasst)
- Modify: `settings.gradle.kts` (`include(":api-protocol")`, `projectDir = file("shared/api-protocol")`)

**Interfaces:**
- Produces (Paket `de.foody.api.protocol`): `Protocol` (VERSION 3, MIN_VERSION 3, sonst wie v2), `RecordType` ohne `PANTRY_ITEM`, `ErrorCode` ohne die vier Codes aus Global Constraints, `LoginRequest(username, password, deviceName)`, `PasswordChangeRequest(oldPassword, newPassword)`, `AuthResponse(token: String, kontoId: String)`, `KiStatus(eingerichtet: Boolean)`; Payloads/Validator/Photos/ZahlGrenzen wie in `:sync-protocol` ohne Vorrat.

- [ ] **Step 1: Kopieren und anpassen.** Dateien aus `shared/sync-protocol/src/main/kotlin/de/foody/sync/protocol/` übernehmen, Paket umbenennen, Vorrats-Payload und dessen Validator-Zweig entfernen, `HouseholdDto`, `CreateHouseholdRequest`, `SelectHouseholdRequest`, `InviteDto`, `DeviceDto`, `RegisterRequest` weglassen. KDoc von `Protocol.VERSION`: „3: Konto statt Haushalt, ohne Vorrat (ADR 0011).“
- [ ] **Step 2: Tests anpassen und ergänzen** in `ProtocolTest`:

```kotlin
@Test fun version3() { assertEquals(3, Protocol.VERSION); assertEquals(3, Protocol.MIN_VERSION) }
@Test fun vorratGibtEsNichtMehr() {
    assertFailsWith<SerializationException> {
        Protocol.json.decodeFromString(RecordType.serializer(), "\"pantry_item\"")
    }
}
@Test fun authResponseHatKontoId() =
    assertEquals("""{"token":"t","kontoId":"k"}""", Protocol.json.encodeToString(AuthResponse.serializer(), AuthResponse("t", "k")))
```
Vorrats-Fälle aus `PayloadValidatorTest` entfernen.
- [ ] **Step 3: Prüfen** – Run: `./gradlew :api-protocol:test :sync-protocol:test` – Expected: PASS; `git diff --stat shared/sync-protocol` leer.
- [ ] **Step 4: Commit** – `"V3 Phase 2: Modul api-protocol (Protokoll 3)"`

---

### Task 4: Sicherung vor jeder Migration

**Files:**
- Modify: `server/sync/src/main/kotlin/de/foody/server/db/Database.kt`, `.../db/Migrations.kt`
- Test: `server/sync/src/test/kotlin/de/foody/server/MigrationSicherungTest.kt`

**Interfaces:**
- Produces: `class Database(url: String, clock: Clock = Clock.systemUTC(), migrationen: List<String> = Migrations.all)`; `Migrations.apply(c: Connection, liste: List<String> = all)`; `Migrations.aktuell(c: Connection): Int`; `class MigrationAbgebrochen(message: String) : IllegalStateException(message)`.

- [ ] **Step 1: Tests schreiben.** Datei-DB in `Files.createTempDirectory`, auf Version 1 gebracht mit `Database(url, migrationen = listOf(Migrations.all[0]))`; danach mit `migrationen = listOf(Migrations.all[0], "CREATE TABLE probe (x INTEGER)")` geöffnet (unabhängig von Task 5); feste Uhr `2026-10-17T10:00:00Z` (= 12:00 in Berlin):

```kotlin
@Test fun sicherungVorAusstehenderMigration()      // nach Database(url, fixedClock): Datei vor-migration-2-2026-10-17-1200.db existiert, user_version darin = 1
@Test fun keineSicherungOhneMigration()           // zweites Öffnen derselben Datei: keine weitere vor-migration-*.db
@Test fun sicherungScheitertKeineMigration()      // Zieldatei vorher anlegen → assertFailsWith<MigrationAbgebrochen>; user_version bleibt 1
@Test fun speicherDatenbankOhneSicherung()        // "jdbc:sqlite::memory:" → keine Datei, kein Fehler
```

- [ ] **Step 2: Rot prüfen** – Run: `./gradlew :server-sync:test --tests '*MigrationSicherungTest*'` – Expected: FAIL (Konstruktor/Parameter fehlen).
- [ ] **Step 3: Umsetzen.** `Database` liest den Dateipfad aus `jdbc:sqlite:<pfad>`; ist `aktuell < migrationen.size` und die Datei existiert und ist nicht leer: `VACUUM INTO` nach `<ordner>/vor-migration-<ziel>-<yyyy-MM-dd-HHmm>.db` (Zeit aus `clock`, Zone `Europe/Berlin`); jeder Fehler dabei → `MigrationAbgebrochen("Sicherung vor Migration fehlgeschlagen: …")`, ohne zu migrieren. `Database` reicht `migrationen` an `Migrations.apply` weiter.
- [ ] **Step 4: Grün prüfen** – gleiche Zeile → PASS; dazu `./gradlew :server-sync:test` PASS.
- [ ] **Step 5: Commit** – `"Server: Sicherung vor jeder Datenbank-Migration"`

---

### Task 5: Konto statt Haushalt (Migration 1 → 2 und Code)

**Files:**
- Modify: `server/sync/src/main/kotlin/de/foody/server/db/Migrations.kt` (Eintrag 2 + Vorprüfung), `auth/AccountStore.kt`, `auth/AuthRoutes.kt`, `sync/RecordStore.kt`, `sync/SyncService.kt`, `sync/SyncRoutes.kt`, `sync/Compactor.kt`, `photos/PhotoStore.kt`, `photos/PhotoRoutes.kt`, `admin/AdminCli.kt`, `Application.kt`, `server/start/.../Main.kt` (`bootstrapAdmin`)
- Delete: `household/HouseholdRoutes.kt`, `auth/InviteCodes.kt`, `test/.../HouseholdTest.kt`
- Test: `server/sync/src/test/kotlin/de/foody/server/MigrationKontoTest.kt`; `TestServer.kt` (`setupHousehold` → `setupKonto`), übrige Tests anpassen

**Interfaces:**
- Consumes: `Migrations.apply`, `MigrationAbgebrochen` (Task 4).
- Produces: `data class DevicePrincipal(val deviceId: String, val kontoId: String)`; `AccountStore.createDevice(kontoId: String, name: String): String`; `AccountStore.abmelden(deviceId: String)`; `AccountStore.createUser(username: String, password: String): String`; `RecordStore`/`SyncService`/`PhotoStore` mit Parameter `konto: String` statt `household`; `TestEnv.setupKonto(): Pair<String, String>` (Token, Konto-ID); Tabelle `haushalt_umzug(household_id TEXT PRIMARY KEY, konto_id TEXT NOT NULL)`.

- [ ] **Step 1: `MigrationKontoTest` schreiben.** Vorlage `v1Datenbank(ordner)`: Schema `all[0]`, Version 1; Konto `stefan` mit Haushalt `h1`, Konto `admin` ohne Haushalt; Geräte: 2 aktiv an `h1`, 1 widerrufen; Token-Hashes über `Tokens.sha256Hex("token-a")` usw.; `household.last_rev = 7`, `compacted_before_rev = 3`; Datensätze `ingredient` ×2 (rev 1–2), `recipe` ×1, eine Löschmarkierung, ein `pantry_item`; 2 Einladungen.

```kotlin
@Test fun datenGehenAufsKonto()          // sync_record: 4 Zeilen mit konto_id = stefan.id, rev unverändert; kein pantry_item
@Test fun zaehlerUebernommen()           // user stefan: last_rev = 7, compacted_before_rev = 3
@Test fun alteTabellenWeg()              // household, membership, invite existieren nicht; haushalt_umzug = [(h1, stefan.id)]
@Test fun tokensBleibenGueltig()         // AccountStore.deviceForToken("token-a") = DevicePrincipal(geraetA, stefan.id)
@Test fun widerrufeneBleibenWiderrufen() // deviceForToken("token-widerrufen") == null
@Test fun kontoOhneHaushaltBleibt()      // authenticate("admin", …) != null; 0 Datensätze
@Test fun vorpruefungZweiMitglieder()    // zweites Mitglied in h1 → assertFailsWith<MigrationAbgebrochen>, Meldung enthält "h1" und "2 Mitglieder"; danach user_version = 1, household/membership/sync_record unverändert (Zeilenzahlen)
@Test fun pullNachMigrationNahtlos()     // nach Migration: SyncService.pull(stefan.id, since = 2, …) liefert genau die Datensätze mit rev > 2
```

- [ ] **Step 2: Rot prüfen** – Run: `./gradlew :server-sync:test --tests '*MigrationKontoTest*'` – Expected: FAIL.
- [ ] **Step 3: Migration 2 umsetzen** (ein Eintrag in `Migrations.all`, Vorprüfung als `fun vorpruefenKonto(c: Connection)` – von `apply` vor Eintrag Index 1 aufgerufen):

Reihenfolge der Anweisungen: `ALTER TABLE user ADD COLUMN last_rev …/compacted_before_rev … DEFAULT 0` → Zähler per `UPDATE user SET … = (SELECT h… FROM household h JOIN membership m ON m.household_id = h.id WHERE m.user_id = user.id)` → `CREATE TABLE sync_record_neu (konto_id …, PRIMARY KEY (konto_id, type, id))` → `INSERT … SELECT m.user_id, … FROM sync_record r JOIN membership m ON m.household_id = r.household_id WHERE r.type <> 'pantry_item'` → `DROP`/`ALTER … RENAME` → beide Indizes neu → `device` ohne `household_id` ebenso neu anlegen und kopieren → `CREATE TABLE haushalt_umzug …` + `INSERT … SELECT household_id, user_id FROM membership` → `DROP TABLE invite, membership, household` → `ALTER TABLE user DROP COLUMN is_admin`.

- [ ] **Step 4: Code auf Konto umstellen.** Haushalts-, Einladungs-, Registrier-, Geräteliste- und Mitglieder-Routen und -Methoden entfernen; `deviceForToken` ohne Mitgliedschaftsprüfung; `RecordStore.put` zählt `user.last_rev` hoch; `Compactor` iteriert über `SELECT id FROM user`; `AdminCli`: `invite` → `konto-anlegen <name>` (gibt `Konto <name> angelegt, Passwort: <zufällig 20 Zeichen>` aus), Usage-Text anpassen. `bootstrapAdmin` legt das erste Konto ohne Sonderrechte an (Name bleibt). Tests: `setupHousehold` → `setupKonto`, `HouseholdTest` löschen, Registrier-Tests in `AuthTest` entfernen, `AdminCliTest` `invite` → `konto-anlegen`.
- [ ] **Step 5: Grün prüfen** – Run: `./gradlew :server-sync:test` – Expected: PASS; `grep -rni "household\|haushalt" server/sync/src/main` trifft nur `Migrations.kt` und `haushalt_umzug`.
- [ ] **Step 6: Commit** – `"V3 Phase 2: Konto statt Haushalt (Migration 1 → 2)"`

---

### Task 6: Fotoumzug

**Files:**
- Create: `server/sync/src/main/kotlin/de/foody/server/photos/FotoUmzug.kt`
- Modify: `server/start/.../Main.kt` (`startServer`: nach `ServerDeps.create` aufrufen, Ergebnis loggen)
- Test: `server/sync/src/test/kotlin/de/foody/server/FotoUmzugTest.kt`

**Interfaces:**
- Consumes: Tabelle `haushalt_umzug` (Task 5).
- Produces: `class FotoUmzug(db: Database, root: Path)` mit `fun ausfuehren(): Int` (Anzahl erledigter Einträge).

- [ ] **Step 1: Tests schreiben**

```kotlin
@Test fun ordnerWirdUmbenannt()         // root/h1/a.jpg → root/k1/a.jpg; haushalt_umzug leer; Rückgabe 1
@Test fun fehlenderOrdnerWirdErledigt() // kein root/h1 → Rückgabe 1, Eintrag weg, keine Ausnahme
@Test fun beideOrdnerWerdenVereint()    // root/h1/a.jpg und root/k1/b.jpg → root/k1/{a,b}.jpg, root/h1 weg
@Test fun abbruchWirdFortgesetzt()      // zwei Einträge; Ziel des zweiten (root/k2) ist vorher eine Datei → Rückgabe 1, Eintrag 2 bleibt; Datei löschen, zweiter Lauf → Rückgabe 1, Tabelle leer
@Test fun ohneEintraegeNichtsZuTun()    // Rückgabe 0
```
- [ ] **Step 2: Rot prüfen** – Run: `./gradlew :server-sync:test --tests '*FotoUmzugTest*'` – Expected: FAIL.
- [ ] **Step 3: Umsetzen** – Fehler eines Eintrags loggen (`log.error`) und mit dem nächsten weitermachen, Eintrag bleibt für den nächsten Start; je Eintrag: `Files.move` des Ordners; existiert das Ziel, Dateien einzeln verschieben (vorhandene gleichen Namens überspringen, inhaltsadressiert), dann alten Ordner löschen; danach Zeile löschen (eigene Transaktion je Eintrag). Fehlender Ordner → `log.warn("Fotoordner {} fehlt", …)`.
- [ ] **Step 4: Grün prüfen** – gleiche Zeile → PASS.
- [ ] **Step 5: Commit** – `"V3 Phase 2: Fotoordner vom Haushalt aufs Konto umziehen"`

---

### Task 7: API v3 auf `:api-protocol`

**Files:**
- Modify: `server/sync/build.gradle.kts` (`api(project(":api-protocol"))` statt `:sync-protocol`), alle Imports `de.foody.sync.protocol` → `de.foody.api.protocol` in `server/sync/src`, `Application.kt` (Route `/api/v3`, `/api/v1`-Abweisung), `auth/AuthRoutes.kt` (Login-Antwort, `POST /auth/abmelden`), `.github/workflows/ci.yml` (Rauchtest)
- Test: `server/sync/src/test/kotlin/de/foody/server/ApiV3Test.kt`; `TestServer.kt` (`protocol()` nutzt v3)

**Interfaces:**
- Consumes: `AccountStore.abmelden(deviceId)` (Task 5), `AuthResponse(token, kontoId)` (Task 3).
- Produces: Routen laut Spec-Tabelle „API v3“.

- [ ] **Step 1: Tests schreiben**

```kotlin
@Test fun loginLiefertKontoId()            // POST /api/v3/auth/login → 200, kontoId == setupKonto().second
@Test fun alterPfadZuAlt()                 // POST /api/v1/auth/login (Header 2) → 409, code protocol_too_old
@Test fun alterHeaderZuAlt()               // /api/v3/... mit Header 2 → 409 protocol_too_old
@Test fun neuererHeaderServerZuAlt()       // Header 4 → 409 server_too_old
@Test fun abmeldenWiderruftToken()         // POST /api/v3/auth/abmelden → 204; danach GET /api/v3/sync/pull → 401
@Test fun zweiGeraeteSehenEinander()       // zwei Logins desselben Kontos; Gerät A pusht ingredient i1, Gerät B pullt ab 0 → enthält i1; B pusht i2, A pullt ab A's Stand → enthält i2
@Test fun vorratWirdAbgelehnt()            // Push mit type "pantry_item" (rohes JSON) → 400 invalid_input
```
- [ ] **Step 2: Rot prüfen** – Run: `./gradlew :server-sync:test --tests '*ApiV3Test*'` – Expected: FAIL.
- [ ] **Step 3: Umsetzen.** `route("/api/v1/{...}") { handle { throw ApiException(HttpStatusCode.Conflict, ErrorCode.PROTOCOL_TOO_OLD) } }` (alle Methoden und Pfade); bestehende Routen unter `route("/api/v3")`. CI-Rauchtest: Header `X-Foody-Protocol: 3`, Pfad `/api/v3/auth/login`, `foody-admin konto-anlegen ci2 | grep -q "Passwort:"`.
- [ ] **Step 4: Grün prüfen** – Run: `./gradlew :api-protocol:test :server-sync:test :server:installDist` – Expected: PASS; `grep -rn "sync.protocol" server/` leer.
- [ ] **Step 5: Commit** – `"V3 Phase 2: API v3 auf api-protocol, /api/v1 meldet protocol_too_old"`

---

### Task 8: KI-Gerüst

**Files:**
- Create: `server/sync/src/main/kotlin/de/foody/server/ki/{KiKontingent,KiRoutes}.kt`
- Modify: `ServerConfig.kt` (`kiKey: String?`, `kiProStunde: Int = 30` aus `FOODY_KI_KEY`, `FOODY_KI_PRO_STUNDE`), `ServerDeps.kt` (Feld `ki: KiKontingent`), `Application.kt` (`kiRoutes(deps)` im `authenticate("device")`-Block), `server/.env.example`
- Test: `server/sync/src/test/kotlin/de/foody/server/KiTest.kt`

**Interfaces:**
- Produces: `class KiKontingent(clock: Clock, proStunde: Int)` mit `fun versuche(kontoId: String): Boolean` (gleitendes Fenster 60 Minuten); `GET /api/v3/ki/status` → `KiStatus(eingerichtet = config.kiKey != null)`.

- [ ] **Step 1: Tests schreiben**

```kotlin
@Test fun kontingentBegrenzt()         // proStunde = 2: versuche(k) true, true, false
@Test fun kontingentJeKonto()          // nach 2 Versuchen von k1 ist k2 weiter frei
@Test fun kontingentErneuertSich()     // MutableClock 61 min weiter → wieder true
@Test fun statusOhneSchluessel()       // GET /api/v3/ki/status → {"eingerichtet":false}
@Test fun statusMitSchluessel()        // ServerConfig(kiKey = "x") → {"eingerichtet":true}
@Test fun statusBrauchtAnmeldung()     // ohne Token → 401
```
- [ ] **Step 2: Rot prüfen** – Run: `./gradlew :server-sync:test --tests '*KiTest*'` – Expected: FAIL.
- [ ] **Step 3: Umsetzen** (Zeitstempel je Konto in einer `ArrayDeque`, synchronisiert). `.env.example`: `# KI-Hilfe (Phase 7): Schlüssel des Sprachmodell-Dienstes; leer = aus` / `FOODY_KI_KEY=` / `FOODY_KI_PRO_STUNDE=30`.
- [ ] **Step 4: Grün prüfen** – gleiche Zeile → PASS.
- [ ] **Step 5: Commit** – `"V3 Phase 2: KI-Gerüst (Kontingent, Status)"`

---

### Task 9: Doku und Probelauf auf dem Test-Server

**Files:**
- Modify: `server/README.md` (Betrieb: `konto-anlegen`, Abschnitt „Umstieg auf V3“ mit Hinweis auf automatische Sicherung, keine Einladungen/Haushalte), `docs/architecture.md` (Server-Teil), `ToDoS/v3-plan.md` (Phase 2 abhaken), `ToDoS/v3-abnahme.md` (Sync-Punkte, soweit Server)

- [ ] **Step 1: Doku anpassen** wie oben; Spec-Verweis im v3-Plan bleibt.
- [ ] **Step 2: Probelauf** (nach dem Merge der PRs, Test-Server):

```sh
cd ~/docker/foody-test && rm -f server/docker-compose.test.yml && git fetch forgejo && git checkout --detach forgejo/main
cd server && docker compose -p foody-test -f docker-compose.yml -f docker-compose.test.yml stop
docker run --rm --user 0 --entrypoint sh -v foody-test_foody-data:/data -v ~/foody-vor-update-2026-10-17.db:/q.db:ro foody-server:test -c 'rm -f /data/foody.db*; cp /q.db /data/foody.db; chown 10001:10001 /data/foody.db'
docker compose -p foody-test -f docker-compose.yml -f docker-compose.test.yml up -d --build
```
Expected: Log enthält die Sicherung `vor-migration-2-…` und keinen Fehler; `python3`-Zählung in der Test-Datenbank: `SELECT count(*) FROM sync_record WHERE deleted = 0` = **370**, `SELECT count(*) FROM device WHERE revoked_at IS NULL` = **3**; `curl -s -H 'X-Foody-Protocol: 3' http://127.0.0.1:8081/api/v3/ki/status` → `401` (ohne Token).
- [ ] **Step 3: Commit + PR** – `"V3 Phase 2: Doku und Abschluss"`; Memory `foody-test-server` ergänzen (läuft mit Protokoll 3).

---

## Reihenfolge und PRs

Task 1 zuerst (ohne PR). Danach je Task ein kleiner PR auf `main` in der Reihenfolge 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9; Task 5 und 6 dürfen ein PR sein. Erst nach Task 9 hat der Test-Server Protokoll 3.
