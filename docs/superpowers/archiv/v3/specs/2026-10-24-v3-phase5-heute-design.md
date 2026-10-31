# V3 Phase 5: Navigation, „Heute“ und Tagebuch

Stand: 2026-10-24 · Grundlage: [ADR 0011](../../../../adr/0011-v3-umfang-ein-nutzer.md), [ADR 0012](../../../../adr/0012-designsystem-v3-ofenwarm-haze.md),
[`ToDoS/v3-plan.md`](../../../../../ToDoS/archiv/v3-plan.md) (Phase 5), [`ToDoS/v3-abnahme.md`](../../../../../ToDoS/archiv/v3-abnahme.md)
(„Heute und Tagebuch“) · im Chat abschnittsweise abgestimmt.

## Ziel

Die V3 bekommt ihre App-Hülle: ein eigenes App-Modul als „Foody Debug“ mit vier Tabs, die Startseite „Heute“, das
ganze Tagebuch (ohne Scan) und den schlichten Teil von „Server verbinden“, damit die App gegen den Test-Server mit
echten Daten ausprobiert werden kann. Rezepte, Woche, Einkauf und Kochmodus sind Ziele mit Route und Deep Link, aber
noch Platzhalter (Phase 6).

**Fertig, wenn** alle Abnahmepunkte unter „Heute und Tagebuch“ außer „Packung scannen“ abgehakt sind, ebenso „Sync
beim Öffnen/Fortsetzen und nach Änderungen“, kein Bildschirm über 300 Zeilen hat und „Foody Debug“ gegen den
Test-Server mit echten Daten läuft.

## Entscheidungen

| Frage | Entscheidung |
|---|---|
| App-Modul | neues `:app3` neben der v1, gleiche App-ID `de.foody.app`, Debug = „Foody Debug“; Release-Build bis Phase 8 aus; in Phase 8 wird `:app3` zu `:app` (verworfen: Variante in `:app`; eigene App-ID) |
| Tagebuch | vollständig in Phase 5, **ohne** „Packung scannen“ (wandert in Phase 6) |
| Daten zum Ausprobieren | schlichtes „Server verbinden“ schon in Phase 5 (`:feature:einstellungen`); die übrigen Einstellungen in Phase 6 |
| Navigation | Navigation Compose mit typisierten Routen; Handy `FoodyNavigationsleiste`, ab 840 dp `FoodyNavigationsRail` |
| Deep Links | neues Schema `foody://` (die v1 hatte keine) |
| v1, Server | unverändert; die V3 spricht mit dem Test-Server, der echte Server bleibt auf `v1-server` |

## 1. Module und App-Hülle

| Modul | Inhalt |
|---|---|
| `:app3` (`android/app3`, Paket `de.foody.app3`, Plugin `foody.android.application`) | `MainActivity`, Navigation, Hilt-Zusammenbau, Startdaten, Sync-Verdrahtung |
| `:feature:heute` (`android/feature/heute`, vorhanden) | Startseite „Heute“, Tagebuch (Eintragen, Ändern, „Alle Einträge“) |
| `:feature:einstellungen` (`android/feature/einstellungen`, neu) | in Phase 5 nur „Server verbinden“ |
| `:core:data` | neue Lesezugriffe als Flows, `HeuteRepository`, `TagebuchRepository`; kein Schema-Wechsel |

- **App-ID und Builds:** `applicationId = "de.foody.app"`, Debug mit `applicationIdSuffix = ".debug"` und Name „Foody
  Debug“. Der Release-Build von `:app3` ist bis Phase 8 abgeschaltet (nicht baubar), damit nie die Alltags-App
  überschrieben wird. Weil die ID der alten „Foody Debug“ gleicht, migriert eine vorhandene Debug-Datenbank 9 → 10 beim
  ersten Start.
- **App-Start:** `FoodyTheme` + `FoodyHintergrund`; `SyncKonfiguration` (http zu `10.0.2.2`/`localhost` nur im
  Debug-Build); `HiltWorkerFactory` als WorkManager-Konfiguration; `SyncAusloeser.beimStart()` beim Start und
  `appImVordergrund()` bei jedem Wechsel in den Vordergrund; Startdaten (Zutatenkatalog) bei leerer Datenbank nach
  `SeedData.VERSION` wie die v1 (Prefs `foody`, Schlüssel `seedVersion`).
- **Navigation:** vier Tabs Heute · Rezepte · Woche · Einkauf; Handy: schwebende `FoodyNavigationsleiste` unten;
  Breite ≥ 840 dp: `FoodyNavigationsRail` links. Einstellungen über das Profilsymbol in „Heute“. Ziele: `Heute`,
  `Rezepte`, `Woche`, `Einkauf`, `RezeptDetail(id)`, `Kochen(rezeptId, portionen)`, `EinkaufListe(listeId)`,
  `TagebuchTag(datum)`, `Einstellungen`.
- **Platzhalter** (Rezepte, Woche, Einkauf, `RezeptDetail`, `Kochen`, `EinkaufListe`): `LeererZustandFlaeche`
  „Kommt in Phase 6“, zeigen die übergebenen Argumente (für Tests und Sichtprüfung).
- **Fotos:** Rezeptbilder über Coil (`file:`-Links aus der Fotoablage), sonst `RezeptPlatzhalter`.

## 2. Startseite „Heute“

Von oben nach unten, nur Komponenten aus `:core:designsystem`:

- **Kopf:** Augenbraue mit Datum („SAMSTAG, 24. OKTOBER“), Titel „Heute“, rechts `SyncStatus` (nur wenn verbunden) und
  Profilsymbol (öffnet Einstellungen). Abbildung `lastError`/Zustand → `SyncAnzeige`: läuft → `Laeuft`, kein Fehler →
  `Synchron`, `offline` → `Wartet`, `anmeldung_abgelaufen` → `Fehler("Anmeldung abgelaufen")`, `app_zu_alt` →
  `Fehler("App zu alt")`, `server_zu_alt` → `Fehler("Server zu alt")`, sonst → `Fehler("Sync-Fehler")`.
- **Nächste Mahlzeit:** `RezeptkarteGross` mit Augenbraue „ABENDESSEN · HEUTE“ (bzw. „· MORGEN“), Metazeile
  Portionen · kcal je Portion · Zeit, Aktion „Kochen“ → `Kochen(rezeptId, portionen)`. Regel (reine Funktion
  `naechsteMahlzeit(plan, jetzt)`): der erste nicht gekochte Planeintrag von heute, dessen Zeitfenster noch nicht vorbei
  ist – Frühstück bis 10:00, Mittagessen bis 14:30, Snack bis 17:00, Abendessen und unbekannte Slots bis 23:59 –,
  sortiert nach Fensterende; ist heute nichts mehr offen, der erste Eintrag von morgen in derselben Ordnung; sonst keine
  Karte. Slot-Namen über `Mahlzeit.ausText`.
- **Rezept-Inspiration:** nur wenn für heute **kein** Planeintrag existiert: eine Karte aus `DailyPicks.pick(…, count = 1)`
  über alle nicht archivierten Rezepte (stabil über den Tag), Aktion „Ansehen“ → `RezeptDetail(id)`.
- **Tagebuch des Tages** auf `GlasFlaeche(DICHT)`: `Naehrwertbalken` gegen das Tagesziel (`dailyKcalGoal`, fehlt es,
  ohne Ziel); darunter die Mahlzeiten Frühstück, Mittagessen, Snack, Abendessen mit ihren Einträgen; leere Mahlzeit als
  `LeererZustandZeile` („Noch kein Frühstück“ · „Eintragen“); geplante, noch nicht eingetragene Mahlzeiten von heute
  als Vorschlag „Gegessen?“ (`Tagebuch.offeneVorschlaege`), ein Tipp trägt sie ein; Link „Alle Einträge“ →
  `TagebuchTag(heute)`.
- **Offener Einkauf:** `Abschnittskopf` „Einkauf“ mit „n offen“ und bis zu sechs `Kachel`n der offenen Artikel der
  zuletzt geänderten Liste; Tipp → `EinkaufListe(listeId)`. Ohne offene Artikel entfällt der Bereich.
- **Leere Datenbank** (keine Rezepte): `LeererZustandFlaeche` „Noch keine Rezepte“ mit „Server verbinden“.

## 3. Tagebuch (ohne Scan)

- **Eintragen** über ein Blatt von unten; Mahlzeit vorbelegt (aus der angetippten Zeile, sonst nach Uhrzeit mit
  denselben Zeitfenstern). Reiter:
  - **Rezept:** Suche nach Name, Portionen (Standard 1, Schritt 0,5) → `Tagebuch.naehrwerteRezept`.
  - **Zutat:** Suche im Katalog, Menge und Einheit → `Tagebuch.naehrwerteZutat`; ohne Nährwerte `vollstaendig = false`.
  - **Aus dem Plan:** die heutigen, noch nicht eingetragenen Planeinträge („Gegessen“), mit ihren Portionen.
  - **Frei:** Name, kcal (Pflicht), Eiweiß/KH/Fett optional → `Tagebuch.freiWerte`.
- **Werte festhalten** (ADR 0007): der Eintrag speichert seine Nährwerte; spätere Rezeptänderungen ändern ihn nicht.
- **Ändern:** Menge bzw. Portionen (Werte skalieren mit `Tagebuch.skaliert`), Mahlzeit, Tag; bei freien Einträgen die
  Werte selbst.
- **Löschen** mit Snackbar „Eintrag gelöscht“ · „Rückgängig“ (stellt den Eintrag mit derselben ID wieder her).
- **„Alle Einträge“** (`TagebuchTag(datum)`): Tag mit Vor/Zurück und Datumsauswahl, Tagesbilanz (`Tagebuch.bilanz`)
  mit `Naehrwertbalken`, Einträge je Mahlzeit mit denselben Aktionen.
- **Daten:** `TagebuchRepository` in `:core:data` (Flows je Datum, Eintragen/Ändern/Löschen/Wiederherstellen); die
  Sync-Trigger merken Änderungen vor, der Auslöser stößt den Sync an. Rechenarbeit bleibt in `:domain`.
- **Aufbau:** je Bildschirm ein ViewModel (Hilt); jede Datei unter 300 Zeilen.

## 4. Server verbinden, Deep Links, Tests

**Server verbinden** (`:feature:einstellungen`, über das Profilsymbol):

- Nicht verbunden: Adresse, Benutzername, Passwort, „Anmelden“ (`SyncKonto.anmelden`). Fehler als Klartext je Kennung:
  `falsche_zugangsdaten`, `ungueltige_adresse`, `zu_viele_versuche`, `offline`, `server_zu_alt`, `app_zu_alt`,
  `token_nicht_gespeichert`, sonst „Anmeldung fehlgeschlagen“.
- Nach dem Anmelden: gibt es lokale Daten (`SyncKonto.hatLokaleDaten`), Wahl „Zusammenführen“/„Ersetzen“ mit je einem
  Satz Erklärung (bei „Ersetzen“: Zutaten bleiben); sonst sofort aktivieren. Wiederanmeldung (gleiche Adresse) ohne
  Frage – `SyncKonto.aktivieren` erkennt sie.
- Verbunden: Adresse, „zuletzt synchronisiert“ (relativ), letzter Fehler als Klartext, „Jetzt synchronisieren“
  (`SyncAusloeser.jetzt`), „Trennen“ mit Rückfrage (`SyncKonto.trennen`).

**Deep Links** (`foody://`, im Manifest von `:app3`, Navigation-Compose-`deepLinks`):
`foody://rezept/{id}`, `foody://einkauf/{listeId}`, `foody://kochen/{rezeptId}?portionen={n}` (Standard: Portionen des
Rezepts), `foody://tagebuch/{datum}` (ISO-Datum). In Phase 5 enden sie auf den Platzhaltern bzw. „Alle Einträge“; die
Timer-Benachrichtigung (Phase 6) nutzt denselben Kochen-Link.

**Tests**

- Reine Funktionen: `naechsteMahlzeit` (Zeitfenster, Grenzen exakt auf der Minute, gekocht, Tageswechsel, unbekannte
  Slots), Sync-Abbildung, Mahlzeit nach Uhrzeit.
- Repositories und ViewModels mit Room im Speicher (Robolectric): Eintragen auf allen vier Wegen, Werte festgehalten
  (Rezeptänderung danach ändert den Eintrag nicht), Ändern skaliert, Löschen und Rückgängig, Outbox-Einträge bei
  aktivem Sync.
- Compose-UI-Tests: Heute je Datenlage (geplant / nichts geplant → Inspiration / leerer Einkauf / leere Datenbank),
  Eintragen-Blatt, Verbinden-Ablauf mit Ersatz-Zugang (Fehler, Zusammenführen/Ersetzen), Deep Links mit Argumenten,
  Navigation Handy (Leiste) / Tablet (Rail), Schrift 200 % ohne Abschneiden.
- Screenshots (Roborazzi) von „Heute“ und „Alle Einträge“, hell/dunkel, Handy/Tablet, erfundene Daten.
- Sichtprüfung: „Foody Debug“ auf dem eigenen Emulator gegen den Test-Server (SSH-Tunnel), echte Daten, echte
  Unschärfe; Screenshots für den Nutzer.

**Abschluss:** `./gradlew check` und alle Tests grün; CI baut `:app3:assembleDebug` und vergleicht die neuen
Screenshots; Doku (`docs/architecture.md`, `ToDoS/v3-plan.md`, `ToDoS/v3-abnahme.md`); PR gegen `main` mit
Auto-Merge, „Server aktualisieren? Nein“.

## Nicht in Phase 5

Packung scannen (Phase 6) · Rezepte, Kochen, Woche, Einkauf als echte Bereiche (Phase 6) · übrige Einstellungen
(Zutatenkatalog, Sicherung, Darstellung, Tagesziel, Online-Produktsuche; Phase 6) · KI (Phase 7) · Umstieg und
Release (Phase 8).

## Risiken

| Risiko | Gegenmaßnahme |
|---|---|
| Versehentlicher Release-Build von `:app3` überschreibt die Alltags-App | Release-Variante in `:app3` abgeschaltet; nur Debug installierbar |
| Alte „Foody Debug“-Daten auf einem Gerät | gewollt: Migration 9 → 10 läuft; Fehlschlag wäre ein Phase-3-Fehler und blockiert |
| Zeitzone/Tageswechsel bei „nächste Mahlzeit“ | Funktion bekommt `jetzt` als Parameter (Uhr injiziert), Tests über Mitternacht |
| Sync-Fehler im Alltag unsichtbar | Status-Chip auf „Heute“, Klartext in „Server verbinden“ |
| Bildschirme wachsen über 300 Zeilen | Blatt, Liste, Kopf als eigene Composables; Prüfung im Review |
