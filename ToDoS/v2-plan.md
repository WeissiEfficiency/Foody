# Foody v2 – Bauplan

> **Abgelöst** durch [`v3-plan.md`](v3-plan.md) (Review vom 2026-10-17, ADR 0011). Nur noch als Hintergrund.

Stand: 2026-10-13 · Entscheidungen: [ADR 0009](../docs/adr/0009-neubau-v2-module-navigation.md),
[ADR 0010](../docs/adr/0010-sync-v2-haushaltsdienst.md) · Abnahme: [`v2-abnahme.md`](v2-abnahme.md)

Neubau von Handy-App, Web-App und Sync. Der Funktionsumfang bleibt gleich und wird nach dem Release 2.0 ausgebaut.

## Entscheidungen (12./13.10.2026)

| Thema | Entscheidung |
|---|---|
| Umfang | Komplettumbau erlaubt, gleiche Funktionen; neue Funktionen erst nach 2.0 |
| Navigation | Heute · Rezepte · Woche · Einkauf · Küche; Tagebuch ist Teil von „Heute“ |
| Design | Glas-Optik und Kacheln bleiben, werden optimiert; gleiche Tokens für App und Web |
| Plattform | nur Android + Web; `minSdk` 36, `targetSdk` 37 |
| API | nur für die App; Web-App ruft den Haushaltsdienst direkt auf |
| Konflikte | feldweise zusammenführen für alle Typen (Stempel je Feld); Listen als Ganzes |
| Umstieg | an einem Tag, `/api/v1` entfällt |

## Ziele

1. **Alltag zuerst:** Startseite „Heute“ mit nächster Mahlzeit, Tagebuch des Tages und offenem Einkauf.
2. **Weniger Wege:** „Küche“ bündelt Vorrat, Zutaten und Scan.
3. **Bereit zum Ausbauen:** ein Modul je Bereich; Screens höchstens 300 Zeilen.
4. **Ein Design für App und Web** aus `shared/design-tokens`.
5. **Fachlogik auf dem Server:** Haushaltsdienst statt Datensatz-Ablage.
6. **Nichts geht verloren:** feldweises Zusammenführen, Sync-Stand in jedem Bereich sichtbar.

## Module

```text
android/app                 :app                 Navigation, Hilt-Zusammenbau
android/feature/<bereich>   :feature:heute, :feature:rezepte, :feature:kochen, :feature:woche,
                            :feature:einkauf, :feature:kueche, :feature:einstellungen
android/core/<bereich>      :core:designsystem, :core:data, :core:sync, :core:scan, :core:timer, :core:testing
shared/domain               :domain              bleibt, wird ergänzt (Feld-Merge)
shared/api-protocol         :api-protocol        bisher :sync-protocol
shared/design-tokens        :design-tokens       tokens.json → FoodyTokens.kt + tokens.css
server/api                  :server-api          /api/v2 für die App (bisher :server-sync)
server/dienst               :server-dienst       Haushaltsdienst
server/web                  :server-web          Web-App v2
server/start                :server              main, Docker
build-logic/                                     Convention-Plugins
```

Abhängigkeiten nur nach unten: `:app` → `:feature:*` → `:core:*` → `shared/`; `:server-api`, `:server-web` →
`:server-dienst` → `:domain`, `:api-protocol`. Features kennen sich gegenseitig nicht.

## Design: Glas und Kacheln

- **Glas:** zwei feste Stufen („Karte“, „Leiste“) mit Füllung und Lichtkante je Theme; Kontrast gegen die dunkelste
  Stelle des Verlaufs; Verlauf nur auf „Heute“ und im Kopf der Rezeptseite; kein Blur zur Laufzeit.
- **Kacheln:** ein Raster (3 Spalten Handy, 4–6 Tablet/Browser), quadratisch; Name oben (max. 2 Zeilen), Menge unten,
  Herkunft per langem Druck; abgehakt = Glas, ans Ende der Abteilung; gleiche Kachel in Einkauf, „Zuletzt verwendet“,
  Vorrat und Katalog.

## Phasen

Die v2 entsteht neben der v1. Bis zum Umstieg läuft die Alltagsversion unverändert, die v2 als „Foody Debug“ gegen
einen Test-Server. Phase 5 und 6 können parallel laufen. Aufwände: grobe Schätzung für eine Person.

### Phase 0 – Fundament (≈ 2–3 Tage)
- [x] ADR 0009, ADR 0010, dieser Plan, Abnahmeliste
- [x] `build-logic/` mit Convention-Plugins (`foody.android.application`, `.library`, `.feature`)
- [x] `minSdk` 36, `targetSdk` 37; Versionsabfragen für Android 13/14 entfernt; Robolectric-Tests und Managed Device
      auf API 36
- [x] Leeres `:feature:heute` mit Compose-Test (Robolectric)
- [ ] Emulator mit Android 17 (lokal sind nur Abbilder für API 36/36.1 installiert); zweiter Docker-Container als
      Test-Server

**Fertig, wenn** ein leeres `:feature:heute` mit dem neuen Build baut und getestet ist. In der App eingebunden wird
es erst in Phase 4 mit der neuen Navigation.

### Phase 1 – Haushaltsdienst und Sync v2 (Server, ≈ 1,5–2 Wochen)
- [ ] Feld-Merge mit Hybrid Logical Clock in `:domain` (JVM-Tests)
- [ ] `:server-dienst`: Regeln aus `:domain`, Logik der sechs Web-Schreiber zusammenführen
- [ ] `:server-api` mit `/api/v2`: Konto, Haushalt, Geräte, Einladungen, Sync, Fotos
- [ ] Einmalige Umwandlung der Server-Datenbank v1 → v2, getestet auf einer Kopie der echten Daten

**Fertig, wenn** Tests zeigen, dass zwei Geräte verschiedene Felder desselben Rezepts ändern und beide ankommen.

### Phase 2 – App-Daten und Sync-Client (App, ≈ 1–1,5 Wochen)
- [ ] `:core:data`: Room v10 mit Stempeln je Feld, Migration 9 → 10 inkl. Migrationstest
- [ ] `:core:sync`: Client für `/api/v2`, Hintergrund-Sync (WorkManager)
- [ ] `:core:scan`, `:core:timer` übernehmen und aufräumen
- [ ] Sicherung (ZIP) liest v1-Dateien weiter ein

**Fertig, wenn** eine Kopie der echten v1-Datenbank migriert und vollständig mit dem Test-Server synchronisiert.

### Phase 3 – Gemeinsames Designsystem (App + Web, ≈ 1 Woche)
- [ ] `shared/design-tokens` mit Generator für Kotlin und CSS
- [ ] `:core:designsystem`: Glas-Karte, Glas-Leiste, Kachel, Rezeptkarte, Nährwertbalken, Sync-Status, leere Zustände
- [ ] Katalog-Screen im Debug-Build, hell und dunkel

**Fertig, wenn** jede Komponente im Katalog WCAG 2.2 AA einhält, auch auf Glas.

### Phase 4 – Navigation und „Heute“ (App, ≈ 1 Woche)
- [ ] Tab-Leiste, Einstellungen über das Profilsymbol
- [ ] `:feature:heute` mit nächster Mahlzeit, Tagebuch und Einkauf
- [ ] Deep Links: Rezept, Einkaufsliste, Kochmodus (auch aus der Timer-Benachrichtigung)

**Fertig, wenn** „Heute und Tagebuch“ in der Abnahmeliste abgehakt ist.

### Phase 5 – App-Bereiche neu bauen (App, ≈ 3–4 Wochen)
- [ ] Je Bereich ein PR: Rezepte, Kochen, Woche, Einkauf, Küche, Einstellungen
- [ ] v1-Code dient als Vorlage für das Verhalten, nicht für die Struktur
- [ ] ViewModels mit Unit-Tests, wichtige Abläufe mit Compose-Tests

**Fertig, wenn** alle App-Abschnitte der Abnahmeliste abgehakt sind und kein Screen über 300 Zeilen hat.

### Phase 6 – Web-App v2 (Web, ≈ 1,5–2 Wochen)
- [ ] Layout mit Tab-Leiste (mobil) und Seitenleiste (breit), Glas und Kacheln aus `tokens.css`
- [ ] Alle Seiten direkt auf den Haushaltsdienst; Schreiber-Klassen entfernen
- [ ] Startseite „Heute“, Web-Manifest

**Fertig, wenn** „Web-App“ in der Abnahmeliste abgehakt ist und jede Seite unter 30 KB bleibt.

### Phase 7 – Umstieg und Release 2.0 (≈ 1 Woche)
- [ ] TalkBack-Durchlauf, Schriftgröße 200 %; Baseline-Profil neu erzeugen
- [ ] Probelauf des Umstiegs mit Kopien von Server- und Handy-Daten
- [ ] Umstieg: Sicherungen → Server v2 → Server-Daten umwandeln → alle Handys auf App v2
- [ ] v1-Code entfernen (alte `ui/`-Pakete, `/api/v1`, Schreiber-Klassen); `docs/architecture.md` und `README.md`
      aktualisieren

**Fertig, wenn** die Abnahmeliste vollständig ist und alle Geräte im Haushalt mit v2 synchronisieren.

## Leitplanken

- Offline-first (ADR 0001); Sync bleibt optional.
- Room ist die Quelle der App (ADR 0002); Daten kommen per Migration mit, nie per Neuinstallation.
- Rechenlogik bleibt in `:domain`.
- App-ID bleibt `de.foody.app`.
- Kein Funktionsabbau: Was nicht abgehakt ist, ist nicht fertig.
- Kleine PRs auf `main`, kein Langzeit-Branch.

## Risiken

| Risiko | Gewicht | Gegenmaßnahme |
|---|---|---|
| Beim Neubau geht eine kleine Funktion verloren | hoch | Abnahmeliste, jeder PR hakt seine Punkte ab |
| Umstieg ohne `/api/v1` scheitert mitten drin | hoch | Sicherungen, Probelauf mit Kopien, Umwandlung wiederholbar |
| Randfälle beim Feld-Merge | mittel | Listen als Ganzes, Regeln in `:domain` mit JVM-Tests |
| Umbau dauert und blockiert Fehlerbehebungen | mittel | v1 läuft bis zum Umstieg weiter |
| Glas verliert auf Fotos an Kontrast | gering | Kontrast-Check im Katalog, Screenshot-Tests hell/dunkel |

## Nach v2 (vorbereitet, nicht Teil von v2)

- Rezept-Import per Web-Link (Server holt die Seite, Import aus `:domain`)
- Einkauf in Echtzeit (Haushaltsdienst meldet Änderungen sofort)
- Erinnerungen (MHD, Einkaufen) als eigenes Modul
