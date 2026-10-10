# Projektstruktur: Handy-App, gemeinsamer Code, Sync-Server und Web-Oberfläche trennen

Stand: 2026-10-11 · Vorarbeit für die Web-Oberfläche (Ansatz A: im Server gerendert, siehe
`2026-10-11-web-oberflaeche-design.md`, folgt).

## Ziel

Auf einen Blick erkennen, was zur Handy-App, was zum Server (Sync bzw. Web-Oberfläche) und was zu beiden gehört.
**Reine Umstrukturierung:** kein Verhalten ändert sich, alle Tests, der Docker-Build und die CI laufen wie vorher.

## Zielstruktur

```text
Foody/
├─ android/
│  ├─ app/                  :app              Android-App
│  └─ baselineprofile/      :baselineprofile  Startprofil-Generator
├─ shared/
│  ├─ domain/               :domain           Rechenregeln, Import, Einkaufsliste, Einordnung …
│  └─ sync-protocol/        :sync-protocol    Datenformat und Validierung des Syncs
├─ server/
│  ├─ sync/                 :server-sync      API, Konten, Datenbank, Sync, Fotos, Admin-CLI
│  ├─ web/                  :server-web       Web-Oberfläche (zunächst nur das Modul-Gerüst)
│  ├─ start/                :server           main(): verbindet sync + web; installDist/Docker
│  └─ Dockerfile, docker-compose.yml, .env.example, README.md   (bleiben, wo sie sind)
├─ docs/, ToDoS/, gradle/
└─ build.gradle.kts, settings.gradle.kts, gradle.properties, local.properties
```

## Regeln

1. **Abhängigkeiten nur in eine Richtung:**
   `:server` → `:server-web` → `:server-sync` → `:sync-protocol`; `:server-web` zusätzlich → `:domain`.
   `:app` → `:domain`, `:sync-protocol`. Nie umgekehrt; `:server-sync` kennt die Web-Oberfläche nicht.
2. **Gradle-Namen bleiben** (`:app`, `:baselineprofile`, `:domain`, `:sync-protocol`, `:server`). Nur die Ordner
   wandern, per `project(":x").projectDir = file("…")` in `settings.gradle.kts`. Befehle wie
   `./gradlew :app:assembleDebug` oder `:server:installDist` bleiben gültig.
3. **Andocken der Web-Oberfläche ohne Zyklus:** `Application.foodyModule(deps, erweiterungen)` in `:server-sync`
   bekommt eine Liste zusätzlicher Routen-Installer (`Route.() -> Unit`). `:server` (start) übergibt die der
   Web-Oberfläche. Bis Teil 1 der Web-Oberfläche ist die Liste leer – der Server verhält sich exakt wie heute.
4. **`:server` (start) enthält nur den Startpunkt:** `Main.kt` mit Serverstart, Admin-Aufruf, Healthcheck,
   Admin-Anlage beim ersten Start und Start der täglichen Kompaktierung. Name des Programms bleibt
   `foody-server`, das Docker-Image bleibt gleich aufgebaut.

## Aufteilung des heutigen `server/`

| heute | danach |
|---|---|
| `server/src/main/kotlin/de/foody/server/Main.kt` | `server/start/src/main/kotlin/de/foody/server/Main.kt` |
| alle übrigen Quellen (`Application.kt`, `auth/`, `db/`, `sync/`, `photos/`, `household/`, `admin/`, …) | `server/sync/src/main/kotlin/…` (Paket unverändert `de.foody.server…`) |
| `server/src/test/…` (alle Tests) | `server/sync/src/test/…` |
| `server/build.gradle.kts` | aufgeteilt: `server/sync/build.gradle.kts` (Bibliothek), `server/start/build.gradle.kts` (`application`) |
| — | `server/web/build.gradle.kts` + leeres Paket `de.foody.server.web` |

Pakete und Klassennamen ändern sich nicht; `mainClass` bleibt `de.foody.server.MainKt`.

## Was angepasst wird

- `settings.gradle.kts`: neue Ordner per `projectDir`, neue Module `:server-sync`, `:server-web`;
  `FOODY_SERVER_ONLY` lässt weiter die Android-Module weg.
- `android/app/build.gradle.kts`: `testImplementation(project(":server-sync"))` statt `:server` (die Unit-Tests der
  App starten den echten Server im Prozess).
- `server/Dockerfile`: `COPY shared/ shared/` und `COPY server/ server/`; Ausgabe weiter aus
  `server/start/build/install/foody-server`.
- `.github/workflows/ci.yml`: Testaufruf `:server-sync:test`, Berichtspfad `android/app/build/reports/…`.
- `docs/architecture.md`, `README.md`, `server/README.md` (lokale Entwicklung: Installationsordner),
  `ToDoS/projektstruktur-regeln.md` (verweist auf diese Struktur), Kommentar zum Baseline-Profil-Befehl.
- Unverändert: `local.properties`, `keystore.properties` (Wurzel, über `rootProject.file`), Room-Schemas
  (`$projectDir/schemas` wandert mit), `server/.env` und Compose-Aufruf (`cd server && docker compose up -d --build`).

## Nachweis, dass nichts kaputt ist

1. `./gradlew check` grün (Unit-Tests aller Module, Lint, Server-Tests).
2. `FOODY_SERVER_ONLY=1 ./gradlew :server:installDist` und `docker build -f server/Dockerfile .` (in der CI).
3. Instrumented-Tests auf dem Test-Emulator vollständig grün.
4. `:app:assembleRelease` enthält weiter `assets/dexopt/baseline.prof`.
5. `git log --follow` zeigt die Historie verschobener Dateien.

## Hinweise für danach

- **Android Studio:** einmal „Sync Project with Gradle Files“; bei Fehlern `.idea/` schließen und neu öffnen.
- **Server auf dem Host:** `git pull && docker compose up -d --build` wie gewohnt; keine Änderung an `.env`.
- Ein PR, nur Verschiebungen und Pfade – keine Logikänderung.
