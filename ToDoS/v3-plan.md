# Foody V3 – Bauplan

Stand: 2026-10-17 · Entscheidung: [ADR 0011](../docs/adr/0011-v3-umfang-ein-nutzer.md) (baut auf
[ADR 0009](../docs/adr/0009-neubau-v2-module-navigation.md) auf, löst [ADR 0010](../docs/adr/0010-sync-v2-haushaltsdienst.md)
weitgehend ab) · Abnahme: [`v3-abnahme.md`](v3-abnahme.md) · Löst [`v2-plan.md`](v2-plan.md) ab.

V3 ist der Neubau aus dem v2-Plan, aber nur mit den Funktionen, die das Review bestätigt hat, plus KI-Hilfe.

## Ergebnis des Reviews (2026-10-17)

| Thema | Antwort | Folge für V3 |
|---|---|---|
| Was ist V3? | Review fließt in den Neubau | v2-Plan wird zu diesem Plan |
| Nutzer | nur ich, Handy + Tablet | keine Haushalte, Einladungen, Geräteliste; Tablet-Layout bleibt |
| Wöchentlich genutzt | Rezepte, Kochmodus, Wochenplan, Einkauf, Tagebuch, Scan | Vorrat und Web-App nicht genutzt |
| Einkaufen im Laden | Foody-App | Teilen als Text entfällt |
| Zeitrahmen | Qualität vor Tempo | keine Abkürzungen bei Tests und Migration |
| Server | offline-first, Server ist der Normalfall | KI darf den Server voraussetzen |
| Vorrat | streichen | mit ihm: „Aus dem Vorrat“, Abbuchen bei „Gekocht“, Vorratsabzug, MHD-Erinnerungen |
| Web-App | streichen | `:server-web` und Haushaltsdienst entfallen |
| Sync | schlanke API | Push/Pull bleibt, ein Konto, Last-Writer-Wins je Datensatz |
| KI | erfassen, anpassen, Wochenplan, Nährwerte | neue Phase 7 |
| Durchspielen | Einkauf, Kochen, Import laufen gut | Planen hakt: kcal je Portion unzuverlässig, Auswahl passt schlecht |
| Verteilung | APK in OpenCloud | Release-Aufgabe legt die signierte APK dort ab |
| Messen | Android Studio reicht | kein Macrobenchmark-Aufwand |
| Server | Speicherlimit, nächtliche Sicherung, kein Monitoring | kleine Änderungen in Phase 0 |

Gestrichen außerdem: Sterne (Favorit + „Oft gekocht“ reichen), Planer-Modi (eine fortlaufende Woche), die zweite
Einkaufsansicht, Echtzeit-Einkauf, Ladenreihenfolge, Kochnotizen (stattdessen „Änderungen ins Rezept übernehmen“),
Design-Token-Generator.

Später, nicht in V3: Rezept-Import per Web-Link, Wochenplan wiederholen/als Vorlage, Startbildschirm-Widget.

## Ziele

1. **Zahlen, denen man traut:** kcal je Portion stimmen überall – Rezept, Planer, Tagebuch.
2. **Planen, das passt:** „Leere Tage füllen“ wählt sinnvoll aus, auf Wunsch mit KI.
3. **Weniger, aber gut:** nur bestätigte Funktionen; jede gestrichene ist auch aus Datenbank, Sync und Server weg.
4. **Bereit zum Ausbauen:** ein Modul je Bereich, Screens höchstens 300 Zeilen.
5. **Schlanker Server:** Konto, Push/Pull, Fotos, KI – sonst nichts.

## Module

```text
android/app                 :app                 Navigation, Hilt-Zusammenbau
android/feature/<bereich>   :feature:heute, :feature:rezepte, :feature:kochen, :feature:woche,
                            :feature:einkauf, :feature:einstellungen
android/core/<bereich>      :core:designsystem, :core:data, :core:sync, :core:ki
shared/domain               :domain              bleibt, wird ergänzt und korrigiert
shared/sync-protocol        :sync-protocol       Protokoll 2, eingefroren für die v1-App, entfällt in Phase 8
shared/api-protocol         :api-protocol        Protokoll 3: ein Konto, ohne Haushalte und Vorrat
server/sync                 :server-sync         Konto, Push/Pull, Fotos, KI-Endpunkte
server/start                :server              main, Docker
build-logic/                                     Convention-Plugins
```

Scan liegt in `:feature:heute` (Tagebuch) und wird vom Katalog in `:feature:einstellungen` über eine schmale
Schnittstelle in `:core:data` genutzt; Timer liegt in `:feature:kochen`. `:core:testing` erst, wenn zwei Module
dieselben Test-Helfer brauchen. Abhängigkeiten nur nach unten; Features kennen sich nicht.

## Phasen

Die V3 entsteht neben der v1 (die nicht mehr verändert wird) als „Foody Debug“ gegen einen Test-Server.
Aufwände: grobe Schätzung für eine Person.

### Phase 0 – Fundament und schnelle Gewinne (≈ 2 Tage, teils erledigt)
- [x] `build-logic/`, `minSdk` 36 / `targetSdk` 37, leeres `:feature:heute` (#89)
- [x] Zweiter Docker-Container als Test-Server (`server/docker-compose.test.yml`, eigener Checkout `~/docker/foody-test`,
      nur `127.0.0.1:8081`, Zugriff per SSH-Tunnel → Emulator `http://10.0.2.2:8081`)
- [x] Server: `mem_limit` in `docker-compose.yml` (512 MB, `FOODY_MEM_LIMIT`; die JVM nimmt davon 75 %)
- [x] Server: `server/backup.sh` – Datenbank datiert (14 Tage) und Fotos gespiegelt in einen Ordner, den ein
      OpenCloud-Client abgleicht; Cron-Zeile im `server/README.md`. Auf dem Server noch einzurichten.
- [x] Release-Aufgabe `./gradlew :app:apkNachOpenCloud` (Ziel `foody.apkZiel` in `local.properties`)
- [ ] **Alltags-App auf Release umstellen:** Im Alltag lief bisher „Foody Debug“ (Debug-Schlüssel, 24,7 MB, ohne R8
      und Baseline-Profil). Release-Schlüssel anlegen (`keystore.properties`), „Foody“ installieren, anmelden, Sync
      holt die Daten, „Foody Debug“ deinstallieren. Erst danach ist „Foody Debug“ frei für die V3.
- [x] `docs/roadmap.md` auf den echten Stand bringen (#90)

### Phase 1 – Rechenkern korrigieren (`:domain`, ≈ 3–4 Tage)
- [x] **kcal je Portion:** Der Import überlas die Portionenzahl der Datei und schätzte (#91); alte Rezepte bekommen
      sie bei der V3-Migration nachgetragen (#92, Einbau in Phase 3)
- [x] **„Leere Tage füllen“ neu:** `LeereTageFuellen` + `Abwechslung` in `:domain` (Regel 28) – zu Mittag/Abend nur
      Hauptspeisen, Favoriten ×3 und „Oft gekocht“ bis ×2 gewichtet statt sortiert, nicht zweimal hintereinander
      dieselbe Grundlage/Eiweißquelle, 14 Tage ohne Wiederholung. Wird in Phase 6 (Woche) eingebunden; v1 behält
      `MealSuggestions`/`PlanVorschlag` bis zum Entfernen in Phase 8. Tagesziel bewusst nicht berücksichtigt.
- Vorrats-Regeln entfernen: verschoben nach Phase 8 – v1-App und Server nutzen die Vorratslogik aus `:domain` bis
  zum Umstieg (Sync, Sicherung, Einkauf, Vorrat-Screen); vorher entfernen hieße die v1 umbauen.

**Fertig, wenn** die Szenarien „Sonntag: Woche planen“ mit echten Daten plausible kcal und Vorschläge liefert.

### Phase 2 – Server schlank (≈ 1 Woche)
Spec: [`2026-10-17-v3-phase2-server-schlank-design.md`](../docs/superpowers/specs/2026-10-17-v3-phase2-server-schlank-design.md)
(harter Schnitt: echter Server bleibt bis Phase 8 auf `v1-server`; `:sync-protocol` eingefroren, neu `:api-protocol`).
- [x] Protokoll 3: ein Konto, keine Haushalte; Typ „Vorrat“ entfällt – als neues Modul `:api-protocol`
      (`/api/v3`), `:sync-protocol` bleibt eingefroren (Spec, Abschnitt 1)
- [x] `:server-web` entfernen; Haushalts-, Einladungs-, Registrier- und Geräte-Routen entfernen; Anmeldung bleibt
      (Argon2id), Konten über `foody-admin konto-anlegen` (Spec, Abschnitt 2)
- [x] Einmalige Umwandlung der Server-Datenbank (Haushalt → Konto, Vorrat löschen) mit Sicherung vorher,
      Vorprüfung, einer Transaktion und Fotoumzug; JVM-getestet (Spec, Abschnitt 2; `server/README.md`, „Umstieg auf V3“)
- [x] Probelauf mit Kopie der echten Daten auf dem Test-Server (370 lebende Datensätze, 3 Geräte) – Kopie der echten
      DB vom 17.10.: 370 = 370 lebende, 384 = 384 gesamt, `last_rev` 413 = 413, 3 = 3 aktive Geräte; Fotoumzug mit
      echten Fotos noch nicht erprobt (0 Fotos)
- [x] KI-Gerüst: `FOODY_KI_KEY` nur aus der Server-Umgebung, `KiKontingent` (Anzahl je Konto und Stunde),
      `GET /ki/status`; `POST /ki/*` mit Größengrenzen folgt mit den Funktionen in Phase 7 (Spec, Abschnitt 3)

**Fertig, wenn** zwei Test-Geräte mit demselben Konto vollständig synchronisieren.

### Phase 3 – App-Daten und Sync-Client (≈ 1 Woche)
Spec: [`2026-10-18-v3-phase3-app-daten-sync-design.md`](../docs/superpowers/specs/2026-10-18-v3-phase3-app-daten-sync-design.md)
(neu geschrieben, v1 als Vorlage; Nachweis über Tests und eine echte v1-Datenbank, siehe Ergebnis).
- [x] `:core:data`: Room v10, Migration 9 → 10 (Vorratstabelle entfernen, Haushalt → Konto) mit Migrationstest
- [x] `:core:sync`: Client für Protokoll v3; Sync beim Öffnen/Fortsetzen und nach Änderungen (entprellt), kein
      periodischer Worker
- [x] ZIP-Sicherung: liest v1-Dateien (Vorrat wird übersprungen)
- [x] Portionen nachtragen (Funktion `PortionenNachtrag` in `:core:data`; das Angebot in der App kommt in Phase 6): Rezepte, die vor Regel 27 importiert wurden, haben noch die geschätzte Portionenzahl
      (oft 2–5× daneben). Die Migration selbst hat die Dateien nicht; nach Migration 9 → 10 einmalig anbieten,
      den Rezeptordner zu wählen, je Datei das Rezept über `sourceUrl` suchen und `RezeptImport.portionenNachtragen`
      anwenden (nur `defaultServings` und Notiz ändern, Mengen nie). Rezepte mit geänderten Portionen oder gelöschtem
      Schätzhinweis bleiben unverändert.

**Fertig, wenn** eine Kopie der echten v1-Datenbank migriert und mit dem Test-Server synchronisiert.

**Ergebnis** (2026-10-18):
- Umgesetzt und getestet: `:core:data` (Room 10, Migration 9 → 10 mit Migrationstest auf dem Emulator, Sync-Trigger,
  Sync-Speicher, Fotos, Sicherung Format 3 liest v1, Portionen-Nachtrag) und `:core:sync` (Protokoll 3, Sync-Lauf gegen
  den echten `:server-sync` im Test, Verbinden/Trennen, Auslöser ohne periodischen Auftrag, Token-Ablage wie v1);
  Überblick in `docs/architecture.md`, „App V3: Daten und Sync“.
- Echte Datenbank: „Foody Debug“ war auf dem Handy nicht mehr installiert, die Release-App lässt sich nicht auslesen.
  Stattdessen: Sicherung des echten Servers (2026-10-18, 375 lebende Datensätze, Revision 423) in den Test-Server
  eingespielt (migriert) und zusätzlich lokal in einen v1-Server geladen; die v1-Debug-App hat sich auf einem Emulator
  daraus synchronisiert und danach offline drei Änderungen gemacht (Rezept bewertet, Rezept gelöscht, Einkaufsartikel).
  Diese echte v1-Datenbank (Schema 9) war die Vorlage.
- Echtdaten-Migration (`EchteDatenMigrationTest`): bestanden – 15 Tabellen, 2.645 Zeilen wie erwartet, Sync-Zustand
  (aktiv, Cursor 423) erhalten, 33 Trigger.
- Echtdaten-Lauf (`EchteDatenSyncTest` gegen den Test-Server): bestanden – ein Lauf, 3 gesendet (inkl. Löschung),
  Cursor 423 → 426, Outbox leer, keine Sync-Probleme, lebende Datensätze lokal = Server (ingredient 249, recipe 112,
  meal_slot 2, shopping_list 1, shopping_item 8, tagebuch_eintrag 2), keine doppelten IDs. Der Test meldet sich neu
  an (Token im Hardware-Keystore, nicht kopierbar); „ohne neue Anmeldung“ auf dem Gerät prüft Phase 8.

### Phase 4 – Designsystem (≈ 4 Tage)
- [ ] `:core:designsystem`: zwei Glasstufen, Kachel, Rezeptkarte, Nährwertbalken, Sync-Status, leere Zustände
- [ ] Katalog-Screen im Debug-Build, hell und dunkel, Handy und Tablet

**Fertig, wenn** jede Komponente im Katalog WCAG 2.2 AA einhält, auch auf Glas.

### Phase 5 – Navigation und „Heute“ (≈ 1 Woche)
- [ ] Vier Tabs (Heute · Rezepte · Woche · Einkauf), adaptive Navigation für das Tablet, Einstellungen über das
      Profilsymbol
- [ ] `:feature:heute`: nächste Mahlzeit, Tagebuch des Tages, offener Einkauf; eine Rezept-Inspiration nur, wenn
      nichts geplant ist
- [ ] Deep Links: Rezept, Einkaufsliste, Kochmodus (auch aus der Timer-Benachrichtigung)

### Phase 6 – Bereiche neu bauen (≈ 3 Wochen)
- [ ] Rezepte (inkl. Tablet zweispaltig, Favorit + „Oft gekocht“)
- [ ] Kochen (inkl. „Änderungen ins Rezept übernehmen“ nach dem Kochen)
- [ ] Woche (eine fortlaufende Wochenansicht)
- [ ] Einkauf (eine Ansicht, ohne Vorratsabzug)
- [ ] Einstellungen (Server/Anmelden/Status, Zutatenkatalog, Sicherung, Darstellung, Tagesziel); nach dem Update aus
      der v1 ist `kontoId` leer: „Erneut verbinden“ nur über Anmelden (verbindet wie neu, Cursor 0)

**Fertig, wenn** alle App-Abschnitte der Abnahmeliste abgehakt sind und kein Screen über 300 Zeilen hat.

### Phase 7 – KI (≈ 1,5–2 Wochen)
- [ ] `:core:ki` (App) und `/ki/*` (Server); jedes Ergebnis als Vorschau im Rezept-Editor bzw. Planer, nie direkt
      gespeichert; Knöpfe nur sichtbar mit Server und Netz
- [ ] **Rezept erfassen:** aus Foto (Kochbuch, Zettel) oder Freitext → Zutaten mit Menge/Einheit, Schritte,
      Portionen; Zutaten über den Katalog vereinheitlicht
- [ ] **Rezept anpassen:** „vegetarisch“, „weniger kcal“, „ohne Ofen“ … → neue Variante als Kopie
- [ ] **Wochenplan vorschlagen:** ergänzt „Leere Tage füllen“ um einen KI-Vorschlag aus den eigenen Rezepten
- [ ] **Nährwerte schätzen:** fehlende Werte einer Zutat vorschlagen, als „geschätzt“ markiert
- [ ] Datenschutzhinweis in der App; ADR zur Modellwahl und Kosten

### Phase 8 – Messen, Umstieg, Release V3 (≈ 1 Woche)
- [ ] Android-Studio-Profiler auf Handy und Tablet (Start, Scrollen, Speicher); Baseline-Profil neu erzeugen
- [ ] TalkBack-Durchlauf, Schriftgröße 200 %
- [ ] Probelauf mit Kopien, dann Umstieg: Sicherungen → Server v3 → Umwandlung → beide Geräte auf V3
- [ ] v1-Code entfernen (`ui/`-Pakete, alte Routen); `README.md`, `docs/architecture.md` aktualisieren
- [ ] Vorrats-Regeln aus `docs/domain-rules.md` und `:domain` entfernen (8, 17, 21 anpassen), dazu
      `MealSuggestions`/`PlanVorschlag`/`PantryCoverage`; Tests nachziehen

## Leitplanken

- Offline-first (ADR 0001); Room ist die Quelle (ADR 0002); Daten kommen per Migration mit.
- Rechenlogik bleibt in `:domain`, jede Korrektur mit Test, der vorher fehlschlägt.
- KI-Ergebnisse sind Vorschläge, nie stille Änderungen.
- Build ressourcenschonend halten: Configuration- und Build-Cache an, keine unnötigen Module.
- App-ID bleibt `de.foody.app`. Kleine PRs auf `main`.

## Risiken

| Risiko | Gewicht | Gegenmaßnahme |
|---|---|---|
| Beim Neubau geht eine kleine Funktion verloren | hoch | Abnahmeliste, jeder PR hakt seine Punkte ab |
| Umwandlung der Server-Daten scheitert | hoch | Sicherungen, Probelauf mit Kopien, Umwandlung wiederholbar |
| KI liefert falsche Mengen oder Nährwerte | mittel | immer Vorschau, „geschätzt“-Markierung, Einheiten über `:domain` geprüft |
| KI-Kosten oder Ausfall des Dienstes | gering | Grenzen je Stunde, App funktioniert ohne KI vollständig |
| Gleichzeitige Änderung auf Handy und Tablet | gering | Last-Writer-Wins je Datensatz, Einkaufseinträge feldweise |
