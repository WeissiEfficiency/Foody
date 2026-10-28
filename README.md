# Foody

Native, offline-first Android-App für Rezepte, Nährwertberechnung, Essensplanung und automatisch
konsolidierte Einkaufslisten.

`Rezept → Portionierung → Wochenplan → Zutatenaggregation → Einkaufsliste`

**Stand (2026-10-28):** Foody wird als **V3** neu gebaut (Plan [`ToDoS/v3-plan.md`](ToDoS/v3-plan.md), Umfang
[ADR 0011](docs/adr/0011-v3-umfang-ein-nutzer.md)). Die V3 (`:app3`) läuft bis zum Umstieg in Phase 8 neben der
**v1** (`:app`), die im Alltag genutzt und **nicht mehr verändert** wird. Neue Arbeit gehört nach `:app3`, `:core:*`
und `:feature:*`; in Phase 8 entfallen `:app` und `:sync-protocol`, und `:app3` wird zu `:app`.

## Funktionen (V3)

Phasen 1–6 sind fertig; als Nächstes kommt die KI-Hilfe (Phase 7). Abnahmeliste: [`ToDoS/v3-abnahme.md`](ToDoS/v3-abnahme.md).

- **Heute:** Plan für heute und morgen mit kcal je Portion, Rezeptvorschläge, offene Einkaufsartikel; **Tagebuch** mit
  beim Eintragen festgehaltenen Nährwerten (ADR 0007) und Tagesziel.
- **Rezepte:** Liste mit Suche und Filtern, Favoriten und „Oft gekocht“, Detail mit Portionen skalieren, Editor,
  **Import aus Markdown** (einzelne Dateien oder ganzer Ordner, bereits importierte Quellen werden übersprungen);
  auf Tablets zweispaltig.
- **Kochen:** ein Schritt pro Seite mit den im Schritt genannten Teilmengen, erkannte Zeitangaben als Timer, nach dem
  Kochen „Änderungen ins Rezept übernehmen“.
- **Woche:** eine fortlaufende Wochenansicht; „Leere Tage füllen“ schlägt Hauptspeisen ohne Wiederholung vor.
- **Einkauf:** eine Ansicht; Vorschau mit Abwählen vorhandener Artikel, gruppiert nach Abteilung, Rezepte auch direkt
  auf die Liste. Wasser wird nie eingekauft.
- **Einstellungen:** Server und Anmeldung, Sicherung als ZIP, Darstellung, Tagesziel, **Zutatenkatalog** (Synonyme,
  Zusammenführen) und **Packung scannen** (Strichcode, Open Food Facts abschaltbar, Foto der Nährwerttabelle; ADR 0013).

Gestrichen gegenüber der v1 (ADR 0011): Vorrat mit MHD, „Aus dem Vorrat“, Abbuchen bei „Gekocht“, Liste als Text
teilen, Planer-Modi 1/2/3/7 Tage, Web-App. Was die v1 kann, beschreibt [`docs/architecture.md`](docs/architecture.md).

## Synchronisierung (optional)

Foody funktioniert ohne Server. Wer Handy und Tablet abgleichen möchte, betreibt einen eigenen
Sync-Server (Docker, hinter Traefik/HTTPS) und verbindet die App unter Einstellungen → Synchronisierung. Aufbau und
Betrieb: [`server/README.md`](server/README.md); Entscheidung: [`docs/adr/0006-optional-self-hosted-sync.md`](docs/adr/0006-optional-self-hosted-sync.md).

Die Web-Oberfläche ist mit V3 entfallen (ADR 0011).

## Projektstruktur

| Ordner | Modul | Inhalt |
|---|---|---|
| `android/app3/` | `:app3` | **V3-App** (Hülle): Hilt, Navigation mit vier Tabs, Deep Links; bindet alle `:core:*` und `:feature:*` ein |
| `android/feature/<bereich>/` | `:feature:heute`, `:rezepte`, `:kochen`, `:woche`, `:einkauf`, `:einstellungen`, `:zutaten` | ein Modul je Bereich (Bildschirme, ViewModels); Features kennen sich nicht |
| `android/core/data/` | `:core:data` | Room (`foody.db` v10), Repositories, Sync-Speicher, Fotos, Sicherung |
| `android/core/sync/` | `:core:sync` | Sync-Client: Server-Zugang, Sync-Lauf, Verbinden/Trennen, Token |
| `android/core/scan/` | `:core:scan` | Packung scannen für Tagebuch und Zutatenkatalog (ADR 0013) |
| `android/core/designsystem/` | `:core:designsystem` | Tokens, Glas-Flächen, Komponenten, Theme „Ofenwarm“ (ADR 0012) |
| `android/core/testing/` | `:core:testing` | nur für Tests: `ViewModelHalter` beendet ViewModels vor `db.close()` (ADR 0014); jedes Feature bekommt es als `testImplementation` |
| `android/katalog/` | `:katalog` | kleine App zum Ansehen des Designsystems |
| `android/app/` | `:app` | **v1-App, eingefroren** (Compose, Room, Hilt, Vorrat); entfällt in Phase 8 |
| `android/baselineprofile/` | `:baselineprofile` | Generator für das Baseline-Profil der v1 |
| `build-logic/` | – | Convention-Plugins (`foody.android.application`, `.library`, `.feature`): SDK-Stände, Java 17, Kotlin ohne Warnungen |
| `shared/domain/` | `:domain` | Reines Kotlin (keine Android-Abhängigkeiten): Einheiten, Skalierung, Nährwerte, Einkaufsaggregation, Diff, Import, Einordnung – von v1, V3 und Server genutzt |
| `shared/api-protocol/` | `:api-protocol` | Protokoll 3 des Servers (`/api/v3`): DTOs, Validierung, Version |
| `shared/sync-protocol/` | `:sync-protocol` | Protokoll 2 der v1-App; eingefroren, entfällt in Phase 8 |
| `server/sync/` | `:server-sync` | Optionaler Sync-Server (Ktor + SQLite): API, Konten, Datenbank, Sync, Fotos, Admin-CLI |
| `server/start/` | `:server` | Startpunkt (`main`), startet den Sync-Server; Docker-Image |

Abhängigkeiten laufen nur nach unten: `:app3` → `:feature:*` → `:core:*` → `:domain`, `:api-protocol`.

Änderungen unter `shared/api-protocol/` betreffen den Server: danach den Server neu bauen (`server/README.md`, Update).

Details: [`docs/architecture.md`](docs/architecture.md), fachliche Invarianten: [`docs/domain-rules.md`](docs/domain-rules.md), Sicherheit: [`docs/security.md`](docs/security.md).

## Bauen & Testen

Voraussetzungen: JDK 21+ (z. B. das JBR von Android Studio; Robolectric braucht es für Android 16), Android SDK (compileSdk 37). AGP 9, Gradle 9.
Die App läuft ab **Android 16** (`minSdk` 36, `targetSdk` 37; ADR 0009).

```bash
./gradlew :domain:test                 # schnelle JVM-Tests des Rechenkerns
./gradlew :feature:rezepte:test        # Tests eines Bereichs (Robolectric, Roborazzi-Bilder)
./gradlew check                        # alle Unit-Tests + Lint (so prüft auch die CI)
./gradlew :app3:assembleDebug          # V3 als „Foody Debug“
./gradlew :app3:installDebug           # V3 auf Emulator/Gerät
./gradlew :core:data:connectedCheck    # Room- und Migrationstests der V3 auf Gerät/Emulator
./gradlew :app:connectedCheck          # Tests der v1 auf Gerät/Emulator
```

`:app3` und `:app` haben dieselbe App-ID (`de.foody.app`, Debug `de.foody.app.debug`): Ein Debug-Build der V3 ersetzt
eine installierte „Foody Debug“ der v1 samt Migration 9 → 10. Auf dem Alltags-Handy daher erst in Phase 8.
Sync gegen einen Test-Server: [`server/README.md`](server/README.md), Abschnitt „Test-Server“.

**Baseline-Profil** (schnellerer App-Start): liegt erzeugt unter
`android/app/src/release/generated/baselineProfiles/baseline-prof.txt` und kommt automatisch in jeden Release-Build.
Nach größeren UI-Änderungen neu erzeugen (Emulator/Gerät mit Android 13+ verbunden):

```powershell
.\gradlew :app:generateReleaseBaselineProfile
```

Unter Windows scheitert dabei mitunter das Kopieren vom Gerät (Pfad mit „(AVD)“). Dann die Datei
`android\baselineprofile\build\outputs\connected_android_test_additional_output\nonMinifiedRelease\connected\*\BaselineProfileGenerator_generate-startup-prof.txt`
als `baseline-prof.txt` an die obige Stelle kopieren. Startzeit messen (am aussagekräftigsten auf dem Handy):

```powershell
.\gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=de.foody.baselineprofile.StartupBenchmark"
```

Debug-Builds heißen auf dem Gerät „Foody Debug“ (App-ID `de.foody.app.debug`) und laufen neben der
Alltagsversion, ohne deren Daten zu berühren.

## Auf dem eigenen Handy installieren (signierter Release-Build)

Gilt bis Phase 8 für die v1 (`:app`); die V3 hat noch keinen Release-Build.

Einmalig einen Signaturschlüssel erzeugen (PowerShell; `keytool` fragt nach Passwort und Namen):

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -keystore "$env:USERPROFILE\.android\foody-release.jks" -alias foody -keyalg RSA -keysize 4096 -validity 10000
```

Dann `keystore.properties.example` als `keystore.properties` kopieren und Pfad sowie Passwörter eintragen.
Die Datei ist in `.gitignore` und landet nie im Repository. **Schlüssel und Passwort sichern** (z. B. im
Passwortmanager): Ohne sie lassen sich Updates nicht mehr installieren, ohne die App samt Daten zu entfernen.

```powershell
.\gradlew :app:assembleRelease   # → android\app\build\outputs\apk\release\app-release.apk
```

Die APK aufs Handy kopieren und öffnen (einmalig „Installation aus dieser Quelle zulassen“) oder per Kabel:
`adb install -r android\app\build\outputs\apk\release\app-release.apk`. Ohne `keystore.properties` entsteht
`app-release-unsigned.apk`, die sich nicht installieren lässt – so baut auch die CI.

## Datenschutz

Kein Konto bei Dritten, keine Werbung, keine Analyse. Alle Daten liegen lokal in Room; kein automatisches
Cloud-Backup. Internet nutzt die App nur für
- den **optionalen Sync** mit dem eigenen Server (nur HTTPS, Cleartext per Network Security Config verboten),
- die **Online-Produktsuche** beim Scannen (nur die Produktnummer an Open Food Facts, abschaltbar),
- künftig die **KI-Hilfe** (Phase 7), nur über den eigenen Server und nur auf Knopfdruck (ADR 0011).

Berechtigungen nur für Kochtimer im Hintergrund (Benachrichtigungen, ablehnbar; Vordergrunddienst; Wakelock bis zum
Timer-Ende). Details: [`docs/security.md`](docs/security.md).
