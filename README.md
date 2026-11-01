# Foody

Native, offline-first Android-App für Rezepte, Nährwertberechnung, Essensplanung und automatisch
konsolidierte Einkaufslisten.

`Rezept → Portionierung → Wochenplan → Zutatenaggregation → Einkaufsliste`

**Stand (2026-10-28):** Foody ist als **V3** neu gebaut (Plan [`v3-plan.md`](ToDoS/archiv/v3-plan.md), archiviert; Umfang
[ADR 0011](docs/adr/0011-v3-umfang-ein-nutzer.md)) und seit dem 2026-10-28 auf Handy, Tablet und dem echten Server im Einsatz (Umstieg, Phase 8). Die v1 ist aus
`main` entfernt; ihr letzter Stand ist der Tag `v1-final`.
**Installieren und aktualisieren** (App bauen, Handy, Tablet, Server mit Docker/Traefik, KI und Fotoerkennung): [`docs/installation.md`](docs/installation.md), kurz: [`docs/installation-kurz.md`](docs/installation-kurz.md).

## Funktionen (V3)

Alle Phasen und der Umstieg sind fertig; Offenes (Messungen, TalkBack, Review-Befunde) sammelt
[`ToDoS/aufraeumplan.md`](ToDoS/aufraeumplan.md). Danach kommt die KI-Hilfe als eigenes Projekt
([`ToDoS/ki-plan.md`](ToDoS/ki-plan.md)). Abnahmeliste: [`v3-abnahme.md`](ToDoS/archiv/v3-abnahme.md) (archiviert).

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
teilen, Planer-Modi 1/2/3/7 Tage, Web-App.

## Synchronisierung (optional)

Foody funktioniert ohne Server. Wer Handy und Tablet abgleichen möchte, betreibt einen eigenen
Sync-Server (Docker, hinter Traefik/HTTPS) und verbindet die App unter Einstellungen → Synchronisierung. Aufbau und
Betrieb: [`server/README.md`](server/README.md); Entscheidung: [`docs/adr/0006-optional-self-hosted-sync.md`](docs/adr/0006-optional-self-hosted-sync.md).

Die Web-Oberfläche ist mit V3 entfallen (ADR 0011).

## Projektstruktur

| Ordner | Modul | Inhalt |
|---|---|---|
| `android/app/` | `:app` | **App-Hülle**: Hilt, Navigation mit vier Tabs, Deep Links; bindet alle `:core:*` und `:feature:*` ein |
| `android/feature/<bereich>/` | `:feature:heute`, `:rezepte`, `:kochen`, `:woche`, `:einkauf`, `:einstellungen`, `:zutaten` | ein Modul je Bereich (Bildschirme, ViewModels); Features kennen sich nicht |
| `android/core/data/` | `:core:data` | Room (`foody.db` v10), Repositories, Sync-Speicher, Fotos, Sicherung |
| `android/core/sync/` | `:core:sync` | Sync-Client: Server-Zugang, Sync-Lauf, Verbinden/Trennen, Token |
| `android/core/scan/` | `:core:scan` | Packung scannen für Tagebuch und Zutatenkatalog (ADR 0013) |
| `android/core/designsystem/` | `:core:designsystem` | Tokens, Glas-Flächen, Komponenten, Theme „Ofenwarm“ (ADR 0012) |
| `android/core/testing/` | `:core:testing` | nur für Tests: `ViewModelHalter` beendet ViewModels vor `db.close()` (ADR 0014), `nachProzessende()` stellt ein Prozessende nach; jedes Feature bekommt es als `testImplementation` |
| `android/katalog/` | `:katalog` | kleine App zum Ansehen des Designsystems |
| `android/baselineprofile/` | `:baselineprofile` | Generator für das Baseline-Profil der App, Kurztest der R8-Variante (CI) |
| `build-logic/` | – | Convention-Plugins (`foody.android.application`, `.library`, `.feature`): SDK-Stände, Java 17, Kotlin ohne Warnungen |
| `shared/domain/` | `:domain` | Reines Kotlin (keine Android-Abhängigkeiten): Einheiten, Skalierung, Nährwerte, Einkaufsaggregation, Diff, Import, Einordnung – von App und Server genutzt |
| `shared/api-protocol/` | `:api-protocol` | Protokoll 3 des Servers (`/api/v3`): DTOs, Validierung, Version |
| `server/sync/` | `:server-sync` | Optionaler Sync-Server (Ktor + SQLite): API, Konten, Datenbank, Sync, Fotos, Admin-CLI |
| `server/start/` | `:server` | Startpunkt (`main`), startet den Sync-Server; Docker-Image |

Abhängigkeiten laufen nur nach unten: `:app` → `:feature:*` → `:core:*` → `:domain`, `:api-protocol`.

Änderungen unter `shared/api-protocol/` betreffen den Server: danach den Server neu bauen (`server/README.md`, Update).

Details: [`docs/architecture.md`](docs/architecture.md), fachliche Invarianten: [`docs/domain-rules.md`](docs/domain-rules.md), Sicherheit: [`docs/security.md`](docs/security.md).

## Bauen & Testen

Voraussetzungen: JDK 21+ (z. B. das JBR von Android Studio; Robolectric braucht es für Android 16), Android SDK (compileSdk 37). AGP 9, Gradle 9.
Die App läuft ab **Android 16** (`minSdk` 36, `targetSdk` 37; ADR 0009).

```bash
./gradlew :domain:test                 # schnelle JVM-Tests des Rechenkerns
./gradlew :feature:rezepte:test        # Tests eines Bereichs (Robolectric, Roborazzi-Bilder)
./gradlew check                        # alle Unit-Tests + Lint (so prüft auch die CI)
./gradlew ktlintPruefen                # Kotlin-Stil (ktlint, Regeln in .editorconfig; auch in der CI)
./gradlew ktlintKorrigieren            # behebt, was ktlint selbst beheben kann
./gradlew :app:assembleDebug           # „Foody Debug“ (de.foody.app.debug)
./gradlew :app:installDebug            # auf Emulator/Gerät
./gradlew :app:assembleVorschau        # „Foody Vorschau“ (R8 wie ein Release, eigene App-ID) zum Testen
./gradlew :app:assembleRelease         # „Foody“ (Release, mit eigenem Schlüssel signiert)
./gradlew :core:data:connectedCheck    # Room- und Migrationstests auf Gerät/Emulator
```

Die drei Varianten heißen auf dem Gerät „Foody“ (`de.foody.app`), „Foody Debug“ (`.debug`) und „Foody Vorschau“
(`.vorschau`) und haben getrennte Daten. Ein Update von „Foody“ übernimmt die Datenbank einer installierten
v1-Release-App (Migration 9 → 10). Signaturschlüssel, APK-Ablage in OpenCloud, Erstinstallation, Update und
Umstieg von der v1: [`docs/installation.md`](docs/installation.md). Sync gegen einen Test-Server:
[`server/README.md`](server/README.md), Abschnitt „Test-Server“.

**Baseline-Profil** (schnellerer App-Start): Der Generator `:baselineprofile` geht einmal durch Heute, Rezepte, Woche,
Einkauf und Einstellungen; das Ergebnis liegt eingecheckt unter
`android/app/src/release/generated/baselineProfiles/baseline-prof.txt` und kommt automatisch in jeden Release-Build.
Neu erzeugen nach größeren UI-Änderungen: Befehle im Kopf von `android/baselineprofile/build.gradle.kts` (nur auf
einem Emulator ohne echte Daten).

**Kurztest der R8-Variante:** `ReleaseStartTest` startet „Foody Vorschau“ mit R8 wie im Release und öffnet „Frei“ im
Tagebuch – fehlende Keep-Regeln fielen sonst erst auf dem Handy auf (Z1). Die CI führt ihn auf einem Emulator aus
(`:baselineprofile:ciDeviceBenchmarkVorschauAndroidTest`).

## Datenschutz

Kein Konto bei Dritten, keine Werbung, keine Analyse. Alle Daten liegen lokal in Room; kein automatisches
Cloud-Backup. Internet nutzt die App nur für
- den **optionalen Sync** mit dem eigenen Server (nur HTTPS, Cleartext per Network Security Config verboten),
- die **Online-Produktsuche** beim Scannen (nur die Produktnummer an Open Food Facts, abschaltbar),
- künftig die **KI-Hilfe** (eigenes Projekt nach dem Umstieg), nur über den eigenen Server und nur auf Knopfdruck (ADR 0011).

Berechtigungen nur für Kochtimer im Hintergrund (Benachrichtigungen, ablehnbar; Vordergrunddienst; Wakelock bis zum
Timer-Ende). Details: [`docs/security.md`](docs/security.md).
