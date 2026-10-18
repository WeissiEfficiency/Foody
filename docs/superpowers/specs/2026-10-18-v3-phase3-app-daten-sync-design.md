# V3 Phase 3: App-Daten und Sync-Client

Stand: 2026-10-18 · Grundlage: [ADR 0011](../../adr/0011-v3-umfang-ein-nutzer.md), [`ToDoS/v3-plan.md`](../../../ToDoS/v3-plan.md)
(Phase 3), [Spec Phase 2](2026-10-17-v3-phase2-server-schlank-design.md) · im Chat abschnittsweise abgestimmt.

## Ziel

Die V3 bekommt ihre Datenschicht und ihren Sync-Client als eigene Module: `:core:data` (Room-Datenbank Version 10,
Migration 9 → 10, Sicherung, Fotos, Portionen-Nachtrag) und `:core:sync` (Client für den eigenen Foody-Sync-Server,
Protokoll 3 unter `/api/v3`). Beide sind **neu geschrieben**; die v1 dient als Vorlage für das Verhalten und bleibt
unverändert.

**Fertig, wenn** eine Kopie der echten v1-Datenbank aus „Foody Debug“ auf Version 10 migriert und danach mit dem
Test-Server vollständig synchronisiert – ohne neue Anmeldung, ohne verlorene oder doppelte Datensätze, ohne
Sync-Probleme.

**Rahmen:** Foody nutzt eine Person auf Handy und Tablet. Die Alltags-App ist seit dem 2026-10-18 die Release-Version
„Foody“ (`de.foody.app`, v1); „Foody Debug“ (`de.foody.app.debug`) ist frei für die V3. In Phase 8 wird die
Alltags-App **ohne Neuinstallation** von v1 auf V3 aktualisiert – Room wandelt dann die Datenbank auf dem Gerät um.

## Entscheidungen

| Frage | Entscheidung |
|---|---|
| Nachweis ohne Oberfläche | nur Tests; die App-Hülle kommt in Phase 4 |
| Echte Daten für den Migrationstest | Datenbankdatei aus „Foody Debug“ per adb, nie eingecheckt |
| Vorgehen | neu schreiben, v1 als Vorlage (verworfen: Kopie anpassen; v1-Code herauslösen) |
| Umfang | Datenbank, Sync, Sicherung, Fotos, Portionen-Nachtrag; die Repositories der Bereiche entstehen in Phase 5/6 mit ihrem Bereich |
| Sync-Server | weiterhin der eigene Foody-Server (Push/Pull), nur über Protokoll 3 |

## 1. Module und Grenzen

| Modul | Inhalt |
|---|---|
| `:core:data` (`android/core/data`) | Room `foody.db` Version 10: Entities, DAOs, Sync-Trigger, Migration 9 → 10; Schnittstelle für den Sync (Outbox lesen, Server-Daten anwenden, Revisionen, Probleme, Fotowünsche, Sync-Zustand); Sicherung (ZIP); Fotoablage; Portionen-Nachtrag; Hilt-Modul |
| `:core:sync` (`android/core/sync`) | HTTP-Client für `/api/v3`, Sync-Lauf, Verbinden/Trennen, Auslöser, Token-Speicher, Fehlerzustände; Hilt-Modul |

- Abhängigkeiten nur nach unten: `:core:sync` → `:core:data`, `:api-protocol`; `:core:data` → `:domain`,
  `:api-protocol`. Features sehen später nur Repositories, nie Room-Typen (ADR 0009).
- Beide Module nutzen die Convention-Plugins aus `build-logic/` (Android-Bibliothek).
- Die v1 (`:app`, `:sync-protocol`) wird nicht verändert. Ihre Tests und Abläufe sind die **Verhaltensliste** für die
  Neuentwicklung (Abschnitt 3).

## 2. Schema 10, Migration 9 → 10, Umstieg auf dem Gerät

### Schema 10

Schema 10 = Schema 9 (`android/app/schemas/de.foody.app.data.db.FoodyDatabase/9.json`) mit genau zwei Änderungen;
alle übrigen Tabellen, Spalten, Primärschlüssel und Indizes sind gleich:

1. `pantry_item` entfällt.
2. `sync_state.householdId` heißt `kontoId` (nullable). Der Wert wird nicht übernommen (Haushalts- ≠ Konto-ID), sondern
   bei der nächsten Anmeldung gesetzt; der Sync braucht ihn nicht.

Tabellen danach: `ingredient`, `recipe`, `recipe_ingredient`, `instruction_step`, `meal_slot`, `shopping_list`,
`shopping_item`, `shopping_item_source`, `tagebuch_eintrag`, `sync_outbox`, `sync_record_rev`, `sync_state`,
`sync_problem`, `sync_photo_local`, `sync_photo_wanted`. `:core:data` übernimmt die Schema-Dateien 1–9 als
Grundlage des Migrationstests; Datenbankname `foody.db`.

### Sync-Trigger

Wie in der v1: SQLite-Trigger merken jede lokale Änderung der Wurzeltabellen (`ingredient`, `recipe`, `meal_slot`,
`shopping_list`, `shopping_item`, `tagebuch_eintrag`) und ihrer Kindtabellen (`recipe_ingredient`, `instruction_step`
→ `recipe`; `shopping_item_source` → `shopping_item`) in `sync_outbox` vor – nur wenn `sync_state.active = 1` und
`applyingRemote = 0`. Neu geschrieben, ohne Vorrat; dieselben Regeln zu Rekursion und `@Upsert` statt `REPLACE`.

### Migration 9 → 10 (eine Room-Migration, eine Transaktion)

1. Vorrats-Trigger und Tabelle `pantry_item` entfernen.
2. Zeilen mit `type = 'pantry_item'` aus `sync_outbox`, `sync_record_rev`, `sync_problem` löschen.
3. `sync_state` mit `kontoId` statt `householdId` neu anlegen; `id`, `active`, `applyingRemote`, `serverUrl`,
   `cursor`, `lastSyncAt`, `lastError` übernehmen, `kontoId = NULL`.
4. Alle Sync-Trigger durch die Fassung für Version 10 ersetzen.

Ungesendete Änderungen in der Outbox bleiben und gehen nach dem Update über Protokoll 3 hinaus. Unterstützt wird nur
9 → 10; ältere Versionen scheitern mit klarer Meldung – **nie** `fallbackToDestructiveMigration`.

### Umstieg in Phase 8 (gleiche App-ID `de.foody.app`)

| Thema | Regel |
|---|---|
| Token | gleiche Ablage wie v1: SharedPreferences `foody_sync`, Eintrag `token`, AES-Schlüssel im Android-Keystore unter dem Alias `foody_sync_token`. Der Server hat die Geräte-Tokens übernommen → keine neue Anmeldung |
| Sync-Stand | Cursor bleibt (der Server hat die Revisionen behalten) → kein Voll-Abgleich |
| Periodischer Sync der v1 | beim ersten Start `WorkManager.cancelUniqueWork("foody-sync-periodic")` |
| Fotos | Ordner `recipe_images` (gleicher App-Speicher) |
| Einstellungen | Prefs `foody` mit denselben Schlüsseln: `seedVersion` (alt `seeded`), `dailyKcalGoal`, `themeMode`, `shoppingListView`, `onlineProduktsuche`, `fotosVerkleinert` |
| Reihenfolge | erst Server auf v3, dann Apps. V3 gegen alten Server: `404` auf `/api/v3` → „Server zu alt“; v1 gegen neuen Server: „Bitte App aktualisieren“ |

## 3. Sync-Client, Sicherung, Portionen, Tests

### Sync-Client (`:core:sync`)

- **Verbinden:** Server-Adresse (`https`; in Debug-Builds zusätzlich `http://10.0.2.2:<port>` und `localhost`),
  Anmelden (`POST /api/v3/auth/login` → `token`, `kontoId`), Aktivieren mit Wahl „Zusammenführen“ (alle lokalen Daten
  in die Outbox, dann holen) oder „Ersetzen“ (lokale Daten verwerfen, alles holen).
- **Sync-Lauf** (nur einer zur Zeit, gemeinsame Sperre mit Trennen):
  1. Outbox in Paketen senden (höchstens `Protocol.MAX_PUSH_RECORDS` = 500 Datensätze bzw. `MAX_PUSH_BYTES` = 5 MB);
     fehlende Fotos vorher hochladen (`POST /photos/missing`, `PUT /photos/{sha256}`).
  2. Ergebnisse auswerten: angenommen/zusammengeführt → Revision merken, aus der Outbox nehmen, falls unverändert;
     abgelehnt → `sync_problem`; fehlende Verweisziele lokal nachreichen.
  3. Holen (`GET /sync/pull?since&limit`), alle Seiten, dann mit `applyingRemote = 1` anwenden (Trigger aus).
  4. `410` → Voll-Abgleich (ohne lokale, noch nicht gesendete oder abgelehnte Daten zu verlieren).
  5. Gewünschte Fotos herunterladen und verknüpfen.
- **Auslöser:** App kommt in den Vordergrund; nach Änderungen (Outbox wächst) entprellt um 5 Sekunden als einmaliger
  WorkManager-Auftrag mit Netz-Bedingung; „Jetzt synchronisieren“. Kein periodischer Auftrag. Inaktiver Sync tut nichts.
- **Fehlerzustände** (feste Kennungen in `lastError`, nie Token oder Antwort-Bodies): offline/Zeitüberschreitung →
  vorübergehend, später erneut; `401` → „Anmeldung abgelaufen“; `409 protocol_too_old` → „Bitte App aktualisieren“;
  `409 server_too_old` oder `404` auf `/api/v3` → „Server zu alt“; Cursor rückt nicht vor → vorübergehender Fehler.
- **Trennen:** laufenden Lauf abwarten, `POST /api/v3/auth/abmelden` (falls erreichbar), dann Token, Sync-Tabellen und
  Fotowünsche lokal leeren, `active = 0`.

### Sicherung, Fotos, Portionen (`:core:data`)

- **ZIP schreiben:** Format 3 – `backup.json` (ohne Vorrat) + Ordner mit Fotos.
- **ZIP lesen:** Format der v1 (Vorrat wird übersprungen) und Format 3. Import gesperrt, solange Sync aktiv ist;
  Größengrenzen und Schutz gegen Pfad-Tricks (Zip-Slip) wie in der v1.
- **Fotoablage:** neu geschrieben; Verkleinerung auf höchstens 1.600 px an der längeren Seite und 600 KB, JPEG 85,
  einmal je Verkleinerungs-Version (`fotosVerkleinert`).
- **Portionen-Nachtrag:** Funktion bekommt Markdown-Dateien (Name + Inhalt), ordnet sie über `sourceUrl` den Rezepten
  zu und wendet `RezeptImport.portionenNachtragen` an (nur `defaultServings` und Notiz, Mengen nie). Ergebnis: Anzahl
  aktualisiert / übersprungen / ohne passendes Rezept. Änderungen gehen über die Outbox zum Server. Den Knopf dafür
  bekommt die App in Phase 6.

### Tests

- **Datenbank:** Unit-Tests für DAOs und Trigger (inaktiver Sync schreibt keine Outbox, Kindtabellen merken das
  Eltern-Rezept vor, `applyingRemote` schaltet Trigger ab, keine Endlos-Rekursion).
- **Migration:** Room-Migrationstest 9 → 10 auf dem Emulator (CI: Gradle Managed Device) mit Schema 9 als Vorlage:
  Zeilenzahlen gleich außer Vorrat, Outbox ohne Vorrat, Cursor/`active`/`serverUrl` unverändert, Trigger wirken.
- **Echte Daten:** derselbe Test mit der Datenbank aus „Foody Debug“; Pfad lokal übergeben, ohne Datei übersprungen,
  Datei nie im Repo.
- **Sync-Verhaltensliste:** die Sync-Testfälle der v1 werden für Protokoll 3 neu geschrieben und laufen gegen den
  echten `:server-sync` im Test – u. a. Fotos hoch/runter, nicht übertragbare Fotos, Voll-Abgleich (behält benutzte
  Zutaten, verwiesene Rezepte, Listen mit wartenden Einträgen, abgelehnte lokale Datensätze), festhängender Cursor,
  Trennen während eines Laufs, inaktiver Sync, Einordnung/Strichcode überstehen den Sync. Neu: Umstieg mit altem Token
  und Cursor, Abmelden, die Fehlerzustände oben.
- **Fertig-Kriterium:** echte Datenbank aus „Foody Debug“ migrieren, dann gegen den Test-Server synchronisieren (er hat
  die Geräte-Tokens aus der Kopie vom 2026-10-17): alle Zeilen erhalten, Outbox leer, keine Sync-Probleme, keine
  doppelten Datensätze.

## Nicht in Phase 3

Oberfläche und App-Hülle (Phase 4) · Repositories der Bereiche (Phase 5/6) · Knopf für den Portionen-Nachtrag (Phase 6)
· KI-Client (Phase 7) · Echtzeit-Sync · Umstieg selbst (Phase 8).

## Risiken

| Risiko | Gegenmaßnahme |
|---|---|
| Neu geschriebene Entities weichen still vom Schema 9 ab | Room-Migrationstest validiert Schema 10 gegen die Entities; Test mit echter Datenbank |
| Verhalten des v1-Syncs geht beim Neuschreiben verloren | Verhaltensliste der v1 als Pflicht-Tests gegen den echten Server |
| Nach dem Update neue Anmeldung nötig | gleiche Token-Ablage, Test „Umstieg mit altem Token“ |
| Verwaister periodischer v1-Auftrag | `cancelUniqueWork("foody-sync-periodic")` beim Start, Test |
| Echte Daten landen im Repo | Pfad nur lokal übergeben, `.gitignore`, Test überspringt ohne Datei |
