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
shared/sync-protocol        :sync-protocol       bleibt (Protokoll v3: ohne Haushalte)
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
- [ ] Zweiter Docker-Container als Test-Server
- [ ] Server: `mem_limit` in `docker-compose.yml` (z. B. 512 MB; die JVM nimmt davon 75 %)
- [ ] Server: nächtliche Sicherung per Cron (`foody-admin backup` + Fotos) mit Kopie nach OpenCloud
- [ ] Release-Aufgabe: signierte APK in den OpenCloud-Ordner kopieren
- [ ] `docs/roadmap.md` auf den echten Stand bringen

### Phase 1 – Rechenkern korrigieren (`:domain`, ≈ 3–4 Tage)
- [ ] **kcal je Portion:** Ursache finden (Portionszahl beim Import? Skalierung im Planer? Einheiten?), mit
      fehlschlagendem Test belegen, beheben; ein Test je Anzeigeort, dass dieselbe Zahl herauskommt
- [ ] **„Leere Tage füllen“ neu:** Regeln ohne Vorrat – Einordnung (Abendessen, Hauptgang), Favoriten und „Oft
      gekocht“ gewichtet, Abwechslung (keine Wiederholung in 14 Tagen, nicht zweimal dieselbe Hauptzutat
      hintereinander), optional Tagesziel; testbar als reine Funktion
- [ ] Vorrats-Regeln aus `docs/domain-rules.md` und `:domain` entfernen (8, 17, 21 anpassen), Tests nachziehen

**Fertig, wenn** die Szenarien „Sonntag: Woche planen“ mit echten Daten plausible kcal und Vorschläge liefert.

### Phase 2 – Server schlank (≈ 1 Woche)
- [ ] Protokoll v3 in `:sync-protocol`: ein Konto, keine Haushalte; Typ „Vorrat“ entfällt
- [ ] `:server-web` entfernen; Haushalts-, Einladungs- und Geräte-Routen entfernen; Anmeldung bleibt (Argon2id)
- [ ] Einmalige Umwandlung der Server-Datenbank (Haushalt → Konto, Vorrat löschen), wiederholbar, getestet auf einer
      Kopie der echten Daten
- [ ] Gerüst `POST /ki/*` mit Grenzen (Größe, Anzahl je Stunde), Schlüssel nur aus der Server-Umgebung

**Fertig, wenn** zwei Test-Geräte mit demselben Konto vollständig synchronisieren.

### Phase 3 – App-Daten und Sync-Client (≈ 1 Woche)
- [ ] `:core:data`: Room v10, Migration 9 → 10 (Vorratstabelle entfernen, Haushalt → Konto) mit Migrationstest
- [ ] `:core:sync`: Client für Protokoll v3; Sync beim Öffnen/Fortsetzen und nach Änderungen (entprellt), kein
      periodischer Worker
- [ ] ZIP-Sicherung: liest v1-Dateien (Vorrat wird übersprungen)
- [ ] Portionen nachtragen: Rezepte, die vor Regel 27 importiert wurden, haben noch die geschätzte Portionenzahl
      (oft 2–5× daneben). Die Migration selbst hat die Dateien nicht; nach Migration 9 → 10 einmalig anbieten,
      den Rezeptordner zu wählen, je Datei das Rezept über `sourceUrl` suchen und `RezeptImport.portionenNachtragen`
      anwenden (nur `defaultServings` und Notiz ändern, Mengen nie). Rezepte mit geänderten Portionen oder gelöschtem
      Schätzhinweis bleiben unverändert.

**Fertig, wenn** eine Kopie der echten v1-Datenbank migriert und mit dem Test-Server synchronisiert.

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
- [ ] Einstellungen (Server/Anmelden/Status, Zutatenkatalog, Sicherung, Darstellung, Tagesziel)

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
