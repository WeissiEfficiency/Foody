# Projektstruktur Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ordner nach Handy-App (`android/`), gemeinsamem Code (`shared/`) und Server (`server/sync`, `server/web`, `server/start`) trennen – ohne Verhaltensänderung.

**Architecture:** Gradle-Namen bleiben, nur `projectDir` ändert sich. Der heutige `:server` wird in die Bibliothek `:server-sync` (alles außer `Main.kt`) und den Startpunkt `:server` (nur `Main.kt`) geteilt; `:server-web` ist ein leeres Modul, das über eine Liste von Routen-Installern in `foodyModule` andockt.

**Tech Stack:** Gradle Kotlin DSL (AGP 9.4, Kotlin JVM), Ktor-Server, Docker.

**Spec:** `docs/superpowers/specs/2026-10-11-projektstruktur-design.md`

## Global Constraints

- Gradle-Namen unverändert: `:app`, `:baselineprofile`, `:domain`, `:sync-protocol`, `:server`; neu `:server-sync`, `:server-web`.
- Abhängigkeiten nur: `:server` → `:server-web` → `:server-sync` → `:sync-protocol`; `:server-web` → `:domain`; `:app` → `:domain`, `:sync-protocol`.
- Pakete/Klassennamen unverändert; `mainClass = "de.foody.server.MainKt"`, `applicationName = "foody-server"`.
- `FOODY_SERVER_ONLY` lässt weiter `:app` und `:baselineprofile` weg.
- Verschieben immer mit `git mv` (Historie über `git log --follow`).
- Jede Task endet mit grünem Build der betroffenen Module; keine Logikänderung außer dem Routen-Hook (Task 3).

## Review Focus

1. Docker-Build aus dem Repo-Root (`docker build -f server/Dockerfile .`) findet `shared/` und `server/` – erwartet: Image baut, `foody-server healthcheck` vorhanden (CI-Job `server`).
2. `FOODY_SERVER_ONLY=1 ./gradlew :server:installDist` ohne Android-SDK – erwartet: läuft, Ausgabe in `server/start/build/install/foody-server`.
3. App-Unit-Tests, die den echten Server im Prozess starten (`SyncEndToEndTest`, `KtorSyncApiTest`, …) – erwartet: grün mit `:server-sync`.
4. CI-Berichtspfad der Managed-Device-Tests – erwartet: `android/app/build/reports/androidTests/managedDevice/` wird hochgeladen.
5. Release-APK enthält weiter das Baseline-Profil – erwartet: `assets/dexopt/baseline.prof` in `:app:assembleRelease`.

---

### Task 1: Gemeinsamen Code nach `shared/`

**Files:**
- Move: `domain/` → `shared/domain/`, `sync-protocol/` → `shared/sync-protocol/`
- Modify: `settings.gradle.kts`, `server/Dockerfile` (`COPY domain/ …`, `COPY sync-protocol/ …` → `COPY shared/ shared/`)

- [ ] **Step 1:** `git mv domain shared/domain` und `git mv sync-protocol shared/sync-protocol`.
- [ ] **Step 2:** In `settings.gradle.kts` nach den `include`s: `project(":domain").projectDir = file("shared/domain")`, `project(":sync-protocol").projectDir = file("shared/sync-protocol")`.
- [ ] **Step 3:** Run `./gradlew :domain:test :sync-protocol:test :server:test :app:testDebugUnitTest -q` – Expected: BUILD SUCCESSFUL.
- [ ] **Step 4:** Dockerfile anpassen; Run `FOODY_SERVER_ONLY=1 ./gradlew :server:installDist -q` – Expected: erfolgreich.
- [ ] **Step 5:** Commit „Struktur: domain und sync-protocol nach shared/“.

### Task 2: Handy-App nach `android/`

**Files:**
- Move: `app/` → `android/app/`, `baselineprofile/` → `android/baselineprofile/`
- Modify: `settings.gradle.kts`, `.github/workflows/ci.yml` (Upload-Pfad `app/build/reports/…` → `android/app/build/reports/…`), Kommentar in `android/baselineprofile/build.gradle.kts` (Pfade `app/src/release/…` → `android/app/src/release/…`)

- [ ] **Step 1:** `git mv app android/app`, `git mv baselineprofile android/baselineprofile`.
- [ ] **Step 2:** In `settings.gradle.kts` innerhalb des `FOODY_SERVER_ONLY == null`-Blocks: `project(":app").projectDir = file("android/app")`, `project(":baselineprofile").projectDir = file("android/baselineprofile")`.
- [ ] **Step 3:** Run `./gradlew check :app:assembleRelease -q` – Expected: grün; `unzip -l android/app/build/outputs/apk/release/*.apk | grep baseline.prof` zeigt `assets/dexopt/baseline.prof`.
- [ ] **Step 4:** CI-Pfad und Kommentar anpassen.
- [ ] **Step 5:** Commit „Struktur: App und Baseline-Profil nach android/“.

### Task 3: Server teilen in `sync`, `web`, `start`

**Files:**
- Move: `server/src/main/kotlin/de/foody/server/Main.kt` → `server/start/src/main/kotlin/de/foody/server/Main.kt`; übriges `server/src/` → `server/sync/src/`
- Create: `server/sync/build.gradle.kts` (heutige `server/build.gradle.kts` ohne `application`-Block; Plugin `kotlin.jvm` + `kotlin.serialization`, `api(project(":sync-protocol"))` und die Ktor-/SQLite-/bcprov-/logback-Abhängigkeiten als `api`, damit `start` und `web` sie sehen), `server/start/build.gradle.kts` (`application` mit `mainClass`/`applicationName` wie heute; `implementation(project(":server-sync"))`, `implementation(project(":server-web"))`), `server/web/build.gradle.kts` (`implementation(project(":server-sync"))`, `implementation(project(":domain"))`), `server/web/src/main/kotlin/de/foody/server/web/WebOberflaeche.kt`
- Delete: `server/build.gradle.kts`
- Modify: `settings.gradle.kts`, `server/sync/src/main/kotlin/de/foody/server/Application.kt`, `android/app/build.gradle.kts:182`, `server/Dockerfile`, `.github/workflows/ci.yml`
- Test: `server/sync/src/test/kotlin/de/foody/server/ErweiterungTest.kt`

**Interfaces:**
- Produces: `fun Application.foodyModule(deps: ServerDeps, erweiterungen: List<Route.() -> Unit> = emptyList())` – installiert jeden Eintrag einmal im `routing`-Block **außerhalb** von `/api/v1`.
- Produces: `object WebOberflaeche { val routen: List<Route.() -> Unit> = emptyList() }` in `:server-web` (Paket `de.foody.server.web`); `Main.kt` ruft `foodyModule(deps, WebOberflaeche.routen)`.

- [ ] **Step 1:** Ordner verschieben (`git mv`), Build-Dateien anlegen, `settings.gradle.kts`: `include(":server-sync")`, `include(":server-web")`, `project(":server").projectDir = file("server/start")`, `project(":server-sync").projectDir = file("server/sync")`, `project(":server-web").projectDir = file("server/web")`.
- [ ] **Step 2: Failing test** `ErweiterungTest`:
  - `erweiterungWirdEingehaengt`: `testApplication { application { foodyModule(deps, listOf({ get("/probe") { call.respondText("ok") } })) } }` → `GET /probe` liefert 200 und `"ok"`.
  - `ohneErweiterungBleibtAllesWieHeute`: `foodyModule(deps)` → `GET /probe` 404, `GET /health` 200.
  (`deps` wie in `TestServer.kt` erzeugt.)
- [ ] **Step 3:** Run `./gradlew :server-sync:test --tests "de.foody.server.ErweiterungTest"` – Expected: FAIL (Parameter `erweiterungen` fehlt).
- [ ] **Step 4:** Parameter in `foodyModule` ergänzen; `WebOberflaeche` anlegen; `Main.kt` übergibt `WebOberflaeche.routen`.
- [ ] **Step 5:** `android/app/build.gradle.kts`: `testImplementation(project(":server-sync"))`. Dockerfile: `COPY --from=build /src/server/start/build/install/foody-server /opt/foody`. CI: `./gradlew :sync-protocol:test :server-sync:test`.
- [ ] **Step 6:** Run `./gradlew :server-sync:test :server:installDist :app:testDebugUnitTest -q` – Expected: grün; `server/start/build/install/foody-server/bin/foody-server` existiert.
- [ ] **Step 7:** Commit „Struktur: Server in sync, web und start geteilt“.

### Task 4: Dokumentation und Gesamtprüfung

**Files:**
- Modify: `docs/architecture.md` (Modul-Übersicht und Pfade), `README.md` (Projektstruktur, Build-Befehle), `server/README.md` (lokale Entwicklung: `server/start/build/install/foody-server`), `ToDoS/projektstruktur-regeln.md` (Verweis auf die Spec, neue Ordner), `docs/security.md` (Pfade, falls genannt)

- [ ] **Step 1:** Alle Pfadnennungen finden: `grep -rn "server/src\|app/src\|domain/src\|sync-protocol/src\|baselineprofile/" --include=*.md docs README.md server ToDoS` (ohne `docs/superpowers/` – Historie bleibt) und anpassen.
- [ ] **Step 2:** Run `./gradlew check -q` – Expected: exit 0.
- [ ] **Step 3:** Instrumented-Tests auf dem Test-Emulator (vorher `adb emu avd name` = `Foody_Test`): `./gradlew :app:connectedDebugAndroidTest` – Expected: alle grün.
- [ ] **Step 4:** `git log --follow --oneline -3 -- shared/domain/src/main/kotlin/de/foody/domain/Tagebuch.kt` zeigt Commits von vor der Verschiebung.
- [ ] **Step 5:** Commit „Struktur: Dokumentation angepasst“; PR mit Hinweis „Android Studio: einmal Gradle-Sync“ und „Server: `git pull && docker compose up -d --build` wie gewohnt“, Auto-Merge.
