# Foody

Native, offline-first Android-App für Rezepte, Nährwertberechnung, Essensplanung und automatisch
konsolidierte Einkaufslisten.

`Rezept → Portionierung → Essensplan → Zutatenaggregation → Vorratsabzug → Einkaufsliste`

## Funktionen

- **Rezepte:** anlegen, bearbeiten, duplizieren, archivieren, **Favoriten**; suchen nach Name, Tags und
  **Zutaten** („Zucchini“); Bild über den System-Photo-Picker oder **eigenes Foto mit der Kamera**, Portionen, Zeiten, Zutaten mit
  Menge/Einheit/Hinweis/optional, Arbeitsschritte, Notizen.
- **Startseite:** drei „Rezepte des Tages“, Bildraster, Filter nach Tags und Favoriten; **„Aus dem Vorrat“** zeigt,
  was sich mit dem Vorrat kochen lässt (höchstens eine fehlende Zutat). Auf Tablets Liste und Rezept nebeneinander.
- **Kochmodus:** ein Schritt pro Seite, Display bleibt an; „Du brauchst“ zeigt die im Schritt genannte Teilmenge
  („0,25 l von 0,5 l Bier“); erkannte Zeitangaben („20 Minuten“) als **Timer**, die auch bei ausgeschaltetem
  Bildschirm oder im Hintergrund weiterlaufen und das Ende als Benachrichtigung melden; nach dem letzten
  Schritt die Einladung zum ersten eigenen Foto.
- **Portionen skalieren** in der Detailansicht und im Planer.
- **Nährwerte:** Energie (kJ/kcal), Eiweiß, Kohlenhydrate, Fett, Ballaststoffe, Zucker, Salz – pro Rezept und pro
  Portion, mit Vollständigkeitsanzeige („zu 82 % vollständig“) statt stiller Nullen.
- **Zutatenstamm** mit Dichte und Stückgewicht für sichere Umrechnungen; Startdatensatz mit häufigen Zutaten.
  **Synonyme** vereinheitlichen Namen („Mehl“ → „Weizenmehl“), Dubletten lassen sich zusammenführen.
- **Planer:** 1, 2, 3, 7 oder beliebig viele Tage; frei benennbare Mahlzeiten-Slots; verschieben; „gekocht“ bucht
  den Verbrauch vom Vorrat ab.
  **„Leere Tage füllen“** schlägt Abendessen für freie Tage vor – zuerst, was der Vorrat hergibt, dann Favoriten;
  ohne Wiederholung der letzten zwei Wochen; mit Vorschau und „Neu mischen“.
- **Einkaufsliste:** Vorschau mit Abwählen vorhandener Artikel, Vorratsabzug, Snapshot, manuelle Einträge,
  Abhaken, Löschen mit Rückgängig, Herkunftsanzeige („300 g Curry + 150 g Reispfanne“), Neuberechnung mit Diff.
  Gruppiert nach Supermarkt-Abteilung; Rezepte lassen sich auch **direkt** auf die Liste setzen. Wasser wird nie
  eingekauft.
- **Teilen** der Liste als Text über das Android-Sharesheet (z. B. an Bring!).
- **Vorrat** mit Menge, Einheit und MHD; bald Ablaufendes zuerst und farbig markiert.
- **Rezept-Import aus Markdown** (z. B. Web-Clipper-Export von Chefkoch): Titel, Quelle, Zutaten inkl. Gruppen,
  Mengen/Einheiten, Zubereitung; mehrere Dateien oder ein **ganzer Ordner** auf einmal. Mengen ohne Zahl werden
  „nach Bedarf“ (n. B.). Bereits importierte Quellen werden übersprungen.
- **Sicherung als ZIP** (Daten und Fotos) und Wiederherstellung; vollständige lokale Löschung.

## Synchronisierung (optional)

Foody funktioniert ohne Server. Wer mehrere Geräte oder einen Haushalt teilen möchte, betreibt einen eigenen
Sync-Server (Docker, hinter Traefik/HTTPS) und verbindet die App unter Einstellungen → Synchronisierung. Aufbau und
Betrieb: [`server/README.md`](server/README.md); Entscheidung: [`docs/adr/0006-optional-self-hosted-sync.md`](docs/adr/0006-optional-self-hosted-sync.md).

## Projektstruktur

| Ordner | Modul | Inhalt |
|---|---|---|
| `android/app/` | `:app` | Handy-App: Compose-UI, ViewModels, Room, Hilt, Repositories, Backup |
| `android/baselineprofile/` | `:baselineprofile` | Generator für das Baseline-Profil (schnellerer Start) |
| `shared/domain/` | `:domain` | Reines Kotlin (keine Android-Abhängigkeiten): Einheiten, Skalierung, Nährwerte, Einkaufsaggregation, Diff, Import, Einordnung – von App und Server genutzt |
| `shared/sync-protocol/` | `:sync-protocol` | Gemeinsame DTOs, Validierung und Protokollversion von App und Server |
| `server/sync/` | `:server-sync` | Optionaler Sync-Server (Ktor + SQLite): API, Konten, Datenbank, Sync, Fotos, Admin-CLI |
| `server/web/` | `:server-web` | Web-Oberfläche des Servers (im Aufbau) |
| `server/start/` | `:server` | Startpunkt (`main`), verbindet Sync-Server und Web-Oberfläche; Docker-Image |

Änderungen unter `shared/` betreffen App **und** Server: danach den Server neu bauen (`server/README.md`, Update).

Details: [`docs/architecture.md`](docs/architecture.md), fachliche Invarianten: [`docs/domain-rules.md`](docs/domain-rules.md), Sicherheit: [`docs/security.md`](docs/security.md).

## Bauen & Testen

Voraussetzungen: JDK 17+ (z. B. das JBR von Android Studio), Android SDK (compileSdk 37). AGP 9, Gradle 9.

```bash
./gradlew :domain:test            # schnelle JVM-Tests des Rechenkerns
./gradlew check                   # alle Unit-Tests + Lint
./gradlew :app:assembleDebug
./gradlew :app:connectedCheck     # Room-, Migrations- und End-to-End-Tests auf Gerät/Emulator
```

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

Kein Konto, kein Server, kein Internetzugriff. Berechtigungen nur für Kochtimer im Hintergrund
(Benachrichtigungen – ab Android 13 abfragbar und ablehnbar –, Vordergrunddienst, Wakelock bis zum Timer-Ende). Alle Daten liegen lokal in Room; kein automatisches Cloud-Backup.
Cleartext-Traffic ist per Network Security Config verboten.
