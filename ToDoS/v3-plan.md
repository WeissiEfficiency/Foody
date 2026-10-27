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
android/core/<bereich>      :core:designsystem, :core:data, :core:sync, :core:scan, :core:ki
shared/domain               :domain              bleibt, wird ergänzt und korrigiert
shared/sync-protocol        :sync-protocol       Protokoll 2, eingefroren für die v1-App, entfällt in Phase 8
shared/api-protocol         :api-protocol        Protokoll 3: ein Konto, ohne Haushalte und Vorrat
server/sync                 :server-sync         Konto, Push/Pull, Fotos, KI-Endpunkte
server/start                :server              main, Docker
build-logic/                                     Convention-Plugins
```

Scan liegt in `:core:scan` und wird von Tagebuch (`:feature:heute`) und Zutatenkatalog (`:feature:zutaten`) genutzt
([ADR 0013](../docs/adr/0013-core-scan-fuer-tagebuch-und-katalog.md)); Timer liegt in `:feature:kochen`.
`:core:testing` erst, wenn zwei Module dieselben Test-Helfer brauchen. Abhängigkeiten nur nach unten; Features kennen sich nicht.

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
Spec: [`2026-10-19-v3-phase4-designsystem-design.md`](../docs/superpowers/specs/2026-10-19-v3-phase4-designsystem-design.md)
(Richtung „Ofenwarm“, Glas mit Haze, Katalog-App `:katalog`).
- [x] `:core:designsystem`: zwei Glasstufen, Kachel, Rezeptkarte, Nährwertbalken, Sync-Status, leere Zustände
- [x] Katalog-Screen im Debug-Build, hell und dunkel, Handy und Tablet (eigene Debug-App `:katalog`)

**Fertig, wenn** jede Komponente im Katalog WCAG 2.2 AA einhält, auch auf Glas.

**Ergebnis** (2026-10-24): `:core:designsystem` und `:katalog` stehen (ADR 0012). AA belegt durch `KontrastTest` (jedes
Farbpaar, hell und dunkel, Glas ohne Unschärfe über dem ungünstigsten Verlaufspunkt, Primär direkt auf dem Verlauf,
alles über Fotos; Safran auf `#BD741B`, Primär hell auf `#A8341A` abgedunkelt, Leiste über Fotos als Stufe BILD), Komponententests (≥ 48 dp, TalkBack-Texte, nichts abgeschnitten bei 200 % Schrift) und 40
Roborazzi-Referenzbilder. Sichtprüfung mit echter Unschärfe auf dem Emulator (Handy hell/dunkel, Tablet). Beim Ansehen
der Bilder gefunden und behoben: fehlende deutsche Silbentrennung, Platzhalter-Symbol hinter der Glas-Leiste,
überlappende Abzeichen bei großer Schrift.

### Phase 5 – Navigation und „Heute“ (≈ 1 Woche)
Spec: [`2026-10-24-v3-phase5-heute-design.md`](../docs/superpowers/specs/2026-10-24-v3-phase5-heute-design.md)
(App-Modul `:app3` als „Foody Debug“, ganzes Tagebuch ohne Scan, schlichtes „Server verbinden“).
- [x] Vier Tabs (Heute · Rezepte · Woche · Einkauf), adaptive Navigation für das Tablet, Einstellungen über das
      Profilsymbol
- [x] `:feature:heute`: nächste Mahlzeit, Tagebuch des Tages, offener Einkauf; eine Rezept-Inspiration nur, wenn
      nichts geplant ist
- [x] Deep Links: Rezept, Einkaufsliste, Kochmodus (die Timer-Benachrichtigung nutzt ab Phase 6 denselben Link)

**Ergebnis** (2026-10-24): Neues App-Modul `:app3` („Foody Debug“) mit Hilt, Startdaten, Sync-Auslösern, Navigation
(Leiste/Rail) und `foody://`-Deep-Links; „Heute“ und das ganze Tagebuch außer Scan in `:feature:heute`; „Server
verbinden“ in `:feature:einstellungen`. Belegt durch Repository-, Navigations-, Deep-Link- und Bildschirmtests (Schrift
200 %, Mitternacht, gelöschtes Rezept, ungültige Eingaben, offline) und 8 Roborazzi-Bilder. Sichtprüfung auf dem
Emulator gegen den Test-Server: verbinden, 112 Rezepte geholt, Tagebuch-Eintrag angelegt und gesendet. Beim Ansehen der
Bilder gefunden und behoben: Inhalte auf dem Tablet über die volle Breite (jetzt höchstens 720 dp, mittig).

### Phase 6 – Bereiche neu bauen (≈ 3 Wochen)
- [x] Rezepte (inkl. Tablet zweispaltig, Favorit + „Oft gekocht“) – Spec
      [`2026-10-24-v3-phase6a-rezepte-design.md`](../docs/superpowers/specs/2026-10-24-v3-phase6a-rezepte-design.md)
- [x] Kochen (inkl. „Änderungen ins Rezept übernehmen“ nach dem Kochen) – Spec
      [`2026-10-25-v3-phase6b-kochen-design.md`](../docs/superpowers/specs/2026-10-25-v3-phase6b-kochen-design.md)
- [x] Woche (eine fortlaufende Wochenansicht) – Spec
      [`2026-10-25-v3-phase6c-woche-design.md`](../docs/superpowers/specs/2026-10-25-v3-phase6c-woche-design.md)
- [x] Einkauf (eine Ansicht, ohne Vorratsabzug) – Spec
      [`2026-10-25-v3-phase6d-einkauf-design.md`](../docs/superpowers/specs/2026-10-25-v3-phase6d-einkauf-design.md)
- [x] Einstellungen 6e1 (Server/Anmelden/Status, Sicherung, Darstellung, Tagesziel) – Spec
      [`2026-10-25-v3-phase6e1-einstellungen-design.md`](../docs/superpowers/specs/2026-10-25-v3-phase6e1-einstellungen-design.md);
      „Erneut anmelden“ nach dem v1-Update (übernimmt nur Token und Konto-ID) gibt es seit Phase 5
- [x] Einstellungen 6e2: Zutatenkatalog – Spec
      [`2026-10-25-v3-phase6e2-zutaten-design.md`](../docs/superpowers/specs/2026-10-25-v3-phase6e2-zutaten-design.md)
- [x] Einstellungen 6e3: „Packung scannen“, Online-Produktsuche an/aus – Spec
      [`2026-10-26-v3-phase6e3-scan-design.md`](../docs/superpowers/specs/2026-10-26-v3-phase6e3-scan-design.md)

**Ergebnis 6a Rezepte** (2026-10-24): `:feature:rezepte` mit Bildraster, Suche und Filterchips, Detail mit
Portionen und Nährwerttabelle, Editor mit Zutaten als Freitext je Zeile und Foto, Markdown-Import (Dateien oder
Ordner, vorhandene Quellen übersprungen) und Tablet zweispaltig. Belegt durch Repository-, Import-, Zeilen-,
Bildschirm- und Navigationstests und 12 Roborazzi-Bilder. Sichtprüfung auf dem Emulator gegen den Test-Server:
Liste mit Serverdaten, Suche, Detail, Import (zweiter Import derselben Datei: „1 übersprungen“), neues Rezept mit
Foto. Dabei gefunden und behoben: Zurück-Pfeil auf dunklen Fotos kaum sichtbar (jetzt im Glaskreis), Energie und
Brennwert brachen auf dem Handy mitten in der Zahl um (jetzt eigene Zeilen).

**Ergebnis 6b Kochen** (2026-10-25): `:feature:kochen` mit Seite „Bereitlegen“, einem Schritt pro Seite
(„Du brauchst“ mit Teilmengen), Timern als Vordergrunddienst (überstehen Neustart), gespeicherter Sitzung
(12 h), Fertig-Blatt mit Foto und „Rezept anpassen“ (Editor mit Portionen-Hinweis) und Seitenliste auf dem
Tablet; „Fertig“ markiert den heutigen Plan-Eintrag als gekocht. Belegt durch Repository-, Timer-,
ViewModel-, Bildschirm- und Navigationstests und 10 Roborazzi-Bilder. Sichtprüfung auf dem Emulator gegen den
Test-Server: Timer „1 Minute“ bei ausgeschaltetem Bildschirm gemeldet, Tipp auf die Meldung öffnet denselben
Schritt, Fertig-Blatt und „Rezept anpassen“. Beim Ansehen der Bilder behoben: Namen unter „Du brauchst“
versetzt, Timer-Leiste unter den Knöpfen, Fortschrittsspur in Material-Lila.

**Ergebnis 6c Woche** (2026-10-25): `:feature:woche` mit Woche zum Blättern (KW, „Heute“), Tagen mit kcal,
Makros und Tagesziel-Balken, „+ Mahlzeit“ mit eigenen Namen, Mahlzeit-Blatt (Portionen, Gekocht, Kochen,
Verschieben, Umbenennen, Entfernen mit Rückgängig), „Leere Tage füllen“ mit Vorschau je Tag und Tablet
zweispaltig; `WocheRepository` in `:core:data`. Belegt durch Repository-, ViewModel-, Bildschirm- und
Navigationstests und 8 Roborazzi-Bilder. Sichtprüfung auf dem Emulator gegen den Test-Server: Woche mit
Serverdaten, Eintrag angelegt, Kochen → „Fertig“ setzt den Haken „gekocht“ (Gerätebeleg aus 6b), Leere Tage
füllen mit sieben Vorschlägen und „↻“ je Tag. Beim Ansehen der Bilder behoben: „Leere Tage füllen“ stand am
Listenende statt fest über der Leiste, Blätter öffneten nur halb. Notiert für Phase 7/8: der Vorschlag nahm
„Pizzateig“ (Grundrezept) als Abendessen.

**Ergebnis 6d Einkauf** (2026-10-25): `:feature:einkauf` mit Kacheln (offen rot je Abteilung, „Zuletzt
verwendet“, Katalog; Antippen = gekauft, Langdruck = Details), Eingabe „Was willst du einkaufen?“, „Neue Liste aus
dem Plan“ mit Zeitraum und Vorschau zum Abwählen, Hinweis „Plan geändert – abgleichen“ mit Diff, Details mit
Herkunft, Löschen mit „Rückgängig“, Frühere Listen und Tablet mit Katalog rechts; „Auf die Einkaufsliste“ im
Rezeptdetail; `EinkaufRepository` in `:core:data`. Heute und Einkauf nehmen dieselbe Liste (zuletzt angelegt).
Belegt durch Repository-, ViewModel-, Nebenläufigkeits-, Bildschirm- und Navigationstests und 12 Roborazzi-Bilder.
Sichtprüfung auf dem Emulator gegen den Test-Server: Serverliste mit „Zuletzt verwendet“, neue Liste aus dem Plan,
Rezept → „7 Artikel auf der Einkaufsliste“ → „Ansehen“, Portionen in der Woche geändert → Hinweis → Abgleich,
zurückgestellt → Hinweis weg. Dabei gefunden und behoben: Gramm mit Nachkommastellen („Butter 217,71 g“), „Ansehen“
im eingebetteten Rezeptdetail auf dem Tablet wirkungslos; beim Ansehen der Bilder: „auf der Liste“ im hellen Modus
kaum sichtbar (jetzt rot), Abteilungsköpfe zu wuchtig.

**Ergebnis 6e1 Einstellungen** (2026-10-25): Einstellungsseite in `:feature:einstellungen` mit Konto (→ „Server
verbinden“), Darstellung System/Hell/Dunkel (wirkt sofort), Tagesziel (500–10.000 kcal), Sicherung als ZIP erstellen
und wiederherstellen (auch v1-Dateien) und „Alle Daten löschen“ (Startkatalog neu); beides gesperrt, solange der Sync
verbunden ist. Belegt durch Speicher-, Lösch-, ViewModel-, Bildschirm-, Navigations- und Darstellungstests und 9
Roborazzi-Bilder. Sichtprüfung auf dem Emulator gegen den Test-Server: Dunkel und Tagesziel (Heute zeigt „0 / 2.000
kcal“), Sicherung erstellt (225 kB), gesperrt solange verbunden; getrennt, alles gelöscht, wiederhergestellt
(„115 Rezepte, 249 Zutaten, 1 Foto“), mit „Zusammenführen“ verbunden: 115 Rezepte, keine doppelten, Outbox leer. Dabei
behoben: „1 Fotos“; beim Ansehen der Bilder: Tagesziel im ersten Bild leer, Konto-Zeile schnitt den ersten Buchstaben
ab. Nicht belegt: „Erneut anmelden“ nach einem v1-Update (kein v1-Stand auf dem Testgerät).

**Ergebnis 6e2 Zutatenkatalog** (2026-10-26): `:feature:zutaten` mit Liste (Suche auch über Katalognamen, Filter
„Ohne Nährwerte“, „Eigene“, „Unbenutzt“, „Mit Strichcode“), Editor (Name, Kategorie, Nährwerte, Dichte, Stückgewicht,
Strichcode, Synonyme zum Lesen), Anlegen, Löschen (nur unbenutzt), Zusammenführen und „Namen vereinheitlichen“ mit
Vorschau. Speichern schreibt nur geänderte Felder; der Startdaten-Lader überschreibt Bearbeitungen nicht mehr. Sync und
Katalog führen über dieselbe Funktion zusammen; zwei Zwei-Geräte-Tests belegen die Ankunft auf dem zweiten Gerät.
Sichtprüfung auf dem Emulator gegen den Test-Server (249 Zutaten): Filter, Suche „Zwiebeln“ → „Zwiebel“, Testzutat
anlegen und bearbeiten (Feldfehler „8 bis 14 Ziffern“, „Änderungen verwerfen?“), zwei Testzutaten zusammengeführt,
gelöscht, „In 45 Rezepten verwendet“ bei Zwiebel, Synchronisieren ohne Fehler. Dabei behoben: „+“ und „Speichern“ lagen
hinter der Navigationsleiste. „Namen vereinheitlichen“ auf echten Daten nur als Vorschau („schon einheitlich“).

**Ergebnis 6e3 Packung scannen** (2026-10-27): neues Modul `:core:scan` (ADR 0013) mit dem Ablauf der v1 – Strichcode
(Google Code Scanner) → eigener Katalog → Open Food Facts (abschaltbar) → Foto der Nährwerttabelle (Kamera oder
Galerie, ML Kit) – und dem Knopf „Packung scannen“. Scannen im Tagebuch „Frei“ (Packung mit Menge, markierten Werten
je 100 g/ml, Vorschau und Häkchen „Als Zutat speichern“ bzw. „Zutat „…“ aktualisieren“; „im Katalog“ → Weg „Zutat“),
im Zutateneditor (markierte Werte, „Online abrufen“, fremder Strichcode → Zusammenführen) und in „Neue Zutat“.
Unverändertes kcal speichert die genauen kJ der Packung; die Scan-Quelle gilt nicht als „Eigene Angabe“. Schalter
„Online-Produktsuche“ in den Einstellungen (v1-Schlüssel, Standard an). Belegt durch Daten-, Ablauf-, Open-Food-Facts-,
Knopf-, ViewModel- und Bildschirmtests mit einem Fake-Scan und 4 neuen Roborazzi-Bildern (die Einstellungsbilder zeigen
jetzt stabil die Katalog-Anzahl). Sichtprüfung auf dem Emulator gegen den Test-Server: folgt.

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
