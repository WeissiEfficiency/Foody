# Foody – Hinweise für Claude

Offline-first Android-App für Rezepte, Nährwerte, Wochenplan und Einkaufsliste, mit optionalem eigenem Sync-Server
(Ktor). Ein Nutzer, zwei Geräte (Handy, Tablet). Überblick und Modultabelle: `README.md`.

## Wo neue Arbeit hingehört

- **V3** (ADR 0011): fertig, Umstieg am 2026-10-28. Plan und Abnahme sind archiviert (`ToDoS/archiv/v3-plan.md`,
  `v3-abnahme.md`); was offen ist, steht in `ToDoS/aufraeumplan.md`.
  Die KI-Hilfe ist ein eigenes Projekt danach (`ToDoS/ki-plan.md`); die Fotoerkennung auf dem Gerät hat einen eigenen
  Plan (`ToDoS/foto-erkennung-plan.md`).
- App-Code in `:app` (Hülle), `:core:*` und `:feature:*` (unter `android/`). Die v1 ist aus `main` entfernt; der
  echte Server läuft seit dem Umstieg von `main` (letzter v1-Stand: Tag `v1-final`).
- Abhängigkeiten nur nach unten: `:app` → `:feature:*` → `:core:*` → `:domain`/`:api-protocol`.
  **Features kennen sich nicht**; was zwei Features brauchen, kommt in ein `:core`-Modul (Beispiel ADR 0013).
- Rechenlogik gehört nach `:domain` (reines Kotlin), jede Korrektur mit einem Test, der vorher fehlschlägt.
- Neues Modul: in `settings.gradle.kts` mit `modul(name, ordner)` in seiner Gruppe eintragen, Plugin aus `build-logic/`
  (`foody.android.library`, mit Compose `foody.android.feature`).

## Bauen und Testen

JDK 21+ (JBR von Android Studio), Android SDK 37. Befehle in PowerShell mit `.\gradlew`.

```bash
./gradlew :domain:test                        # Rechenkern, schnell
./gradlew :feature:<bereich>:test             # ein Bereich (Robolectric)
./gradlew :feature:<bereich>:verifyRoborazziDebug   # Bildvergleich wie in der CI
./gradlew :feature:<bereich>:recordRoborazziDebug   # Bilder nach gewollter UI-Änderung neu aufnehmen
./gradlew check                               # alles + Lint, wie die CI
./gradlew ktlintPruefen                       # Kotlin-Stil (Regeln in .editorconfig), wie die CI
./gradlew ktlintKorrigieren                   # behebt, was ktlint selbst kann
./gradlew :core:data:connectedCheck           # Room/Migration auf dem Emulator
./gradlew :api-protocol:test :server-sync:test   # Server
```

- Emulator-Sync gegen den Test-Server: `server/README.md`, Abschnitt „Test-Server“.
- Varianten „Foody“, „Foody Vorschau“ (`.vorschau`), „Foody Debug“ (`.debug`); APKs, Schlüssel und Umstieg:
  `docs/installation.md`. Release-Builds nie aufs Alltags-Handy installieren, das macht der Nutzer selbst.

## Konventionen

- V3-Code: **Bezeichner, Kommentare (KDoc) und Texte auf Deutsch** (`Sicherung`, `ZutatenRepository`). In älterem
  `:domain`-Code stehen englische Bezeichner; bestehende nicht umbenennen. Code liest sich wie der umgebende Code.
- `allWarningsAsErrors` ist überall an; Bytecode Java 17.
- Tests mit ViewModels und Room im Speicher: ViewModels in einen `ViewModelHalter` (`:core:testing`) geben, in `@After`
  erst `schliessen()`, dann `db.close()` – sonst fragt ein laufender Flow die geschlossene Datenbank ab (ADR 0014).
- Eingaben, die ein Prozessende überstehen müssen, liegen im `SavedStateHandle`; Tests prüfen das mit
  `SavedStateHandle.nachProzessende()` aus `:core:testing` (Werte und `saveable`-Zustände gehen wie beim Sichern durch ein Parcel).
- Room: nie einen destruktiven Fallback; jede Schemaänderung mit Migration und Migrationstest.
- Änderungen unter `shared/api-protocol/` oder `server/` → der Server muss neu gebaut werden; im PR sagen, ob ein
  Server-Rebuild nötig ist (`server/README.md`, Update).
- Kleine PRs gegen `main`.

## Doku mitpflegen

- Jede Phase: Spec in `docs/superpowers/specs/`, Plan in `docs/superpowers/plans/`, danach „Ergebnis“ im Projektplan
  (für die KI-Hilfe `ToDoS/ki-plan.md`) und ein Abschnitt in `docs/architecture.md`. Specs und Pläne der V3-Phasen liegen
  in `docs/superpowers/archiv/v3/`.
- Strukturentscheidungen (neues Modul, Umzug, gestrichene Funktion) als ADR in `docs/adr/`.
- Fachliche Regeln mit zugehörigen Tests: `docs/domain-rules.md`. Sicherheit: `docs/security.md`.
- Neue Module in die Tabelle in `README.md` und die Befehle oben.
- Pläne und Specs der v1, v2 und Web-App sind gelöscht (Git-Verlauf). In `docs/superpowers/archiv/v3/` und `ToDoS/archiv/`
  liegen nur noch die der V3; sie beschreiben Zwischenstände, den Ist-Stand beschreibt `docs/architecture.md`.
