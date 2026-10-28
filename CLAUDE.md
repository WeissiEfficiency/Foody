# Foody – Hinweise für Claude

Offline-first Android-App für Rezepte, Nährwerte, Wochenplan und Einkaufsliste, mit optionalem eigenem Sync-Server
(Ktor). Ein Nutzer, zwei Geräte (Handy, Tablet). Überblick und Modultabelle: `README.md`.

## Wo neue Arbeit hingehört

- **V3 im Bau** (`ToDoS/v3-plan.md`, Abnahme `ToDoS/v3-abnahme.md`, ADR 0011): Phasen 1–6 fertig, als Nächstes
  Phase 7 (KI), dann Phase 8 (Umstieg).
- Neuer App-Code nur in `:app3`, `:core:*` und `:feature:*` (unter `android/`).
- **`:app` (v1) und `:sync-protocol` sind eingefroren** – nicht ändern, außer der Nutzer verlangt es ausdrücklich.
  Sie dienen als Vorlage für das Verhalten und entfallen in Phase 8.
- Abhängigkeiten nur nach unten: `:app3` → `:feature:*` → `:core:*` → `:domain`/`:api-protocol`.
  **Features kennen sich nicht**; was zwei Features brauchen, kommt in ein `:core`-Modul (Beispiel ADR 0013).
- Rechenlogik gehört nach `:domain` (reines Kotlin), jede Korrektur mit einem Test, der vorher fehlschlägt.
- Neues Modul: in `settings.gradle.kts` eintragen (`include` + `projectDir`), Plugin aus `build-logic/`
  (`foody.android.library`, mit Compose `foody.android.feature`).

## Bauen und Testen

JDK 21+ (JBR von Android Studio), Android SDK 37. Befehle in PowerShell mit `.\gradlew`.

```bash
./gradlew :domain:test                        # Rechenkern, schnell
./gradlew :feature:<bereich>:test             # ein Bereich (Robolectric)
./gradlew :feature:<bereich>:verifyRoborazziDebug   # Bildvergleich wie in der CI
./gradlew :feature:<bereich>:recordRoborazziDebug   # Bilder nach gewollter UI-Änderung neu aufnehmen
./gradlew check                               # alles + Lint, wie die CI
./gradlew :core:data:connectedCheck           # Room/Migration auf dem Emulator
./gradlew :api-protocol:test :server-sync:test   # Server
```

- Emulator-Sync gegen den Test-Server: `server/README.md`, Abschnitt „Test-Server“.
- `:app3` und `:app` haben dieselbe App-ID. Ein `:app3:installDebug` auf dem Alltags-Handy ersetzt die v1 – nur auf dem
  Emulator installieren.

## Konventionen

- V3-Code: **Bezeichner, Kommentare (KDoc) und Texte auf Deutsch** (`Sicherung`, `ZutatenRepository`). In der v1 und in älterem
  `:domain`-Code stehen englische Bezeichner; bestehende nicht umbenennen. Code liest sich wie der umgebende Code.
- `allWarningsAsErrors` ist überall an; Bytecode Java 17.
- Tests mit ViewModels und Room im Speicher: ViewModels in einen `ViewModelHalter` (`:core:testing`) geben, in `@After`
  erst `schliessen()`, dann `db.close()` – sonst fragt ein laufender Flow die geschlossene Datenbank ab (ADR 0014).
- Eingaben, die ein Prozessende überstehen müssen, liegen im `SavedStateHandle`; Tests prüfen das mit
  `SavedStateHandle.nachProzessende()` aus `:core:testing` (Werte gehen durch die Java-Serialisierung wie im Bundle).
- Room: nie einen destruktiven Fallback; jede Schemaänderung mit Migration und Migrationstest.
- Änderungen unter `shared/api-protocol/` oder `server/` → der Server muss neu gebaut werden; im PR sagen, ob ein
  Server-Rebuild nötig ist (`server/README.md`, Update).
- Kleine PRs gegen `main`.

## Doku mitpflegen

- Jede Phase: Spec in `docs/superpowers/specs/`, Plan in `docs/superpowers/plans/`, danach „Ergebnis“ in
  `ToDoS/v3-plan.md` und ein Abschnitt in `docs/architecture.md`.
- Strukturentscheidungen (neues Modul, Umzug, gestrichene Funktion) als ADR in `docs/adr/`.
- Fachliche Regeln mit zugehörigen Tests: `docs/domain-rules.md`. Sicherheit: `docs/security.md`.
- Neue Module in die Tabelle in `README.md` und die Befehle oben.
- Dokumente mit dem Hinweis „Abgelöst“ (v2-Plan, Web-Pläne und -Specs) beschreiben einen alten Stand.
