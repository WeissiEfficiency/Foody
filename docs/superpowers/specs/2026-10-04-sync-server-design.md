# Foody Sync-Server – Design

Stand: 2026-10-04 · Status: zur Prüfung

## 1. Ziel und Rahmen

**Ziel:** Mehrere Personen eines Haushalts nutzen Foody auf eigenen Geräten mit gemeinsamem Datenbestand.
Ein im Laden abgehakter Eintrag erscheint beim Partner nach dem nächsten Sync; ein auf dem Tablet bearbeitetes
Rezept erscheint samt Foto auf dem Handy; offline auf zwei Geräten gemachte Änderungen gehen nicht verloren.

**Vorgaben (vom Nutzer):**
- Selbst gehosteter Server als Docker Compose im Heimnetz.
- Erreichbar über den vorhandenen **Traefik** (Reverse-Proxy, TLS) und unterwegs über ein vorhandenes
  **WireGuard**-Always-on-VPN.
- Mehrere Personen mit eigenem Konto; Daten gehören einem **Haushalt**, in den eingeladen wird.
- Rezeptfotos werden mit synchronisiert.

**Annahmen:**
- Offline-first bleibt: Room ist Quelle der Wahrheit (ADR 0002); ohne Server funktioniert die App unverändert.
- Traefik liefert ein gültiges Zertifikat (z. B. Let's Encrypt per DNS-Challenge); die App spricht nur HTTPS.
- Keine offene Registrierung: Erstes Konto per Umgebungsvariable, weitere nur per Einladungscode.

**Nicht im Umfang:** Web-Oberfläche, Teilen einzelner Objekte zwischen Haushalten, Echtzeit-Push
(WebSocket/FCM), Server-seitige Fachlogik (Skalierung, Einkaufsberechnung bleiben auf dem Gerät).

## 2. Architektur

```
Android (Foody)                                     Docker Compose (Heimserver)
UI → Repositories → Room (+ sync_outbox)            Traefik (vorhanden, externes Netzwerk)
            SyncWorker (WorkManager)  ── HTTPS ──►     foody-server (Ktor, :8080 nur intern)
            SyncClient (Ktor-Client)                     ├─ SQLite  → Volume foody-data
            TokenStore (Android Keystore)                └─ Fotos   → Volume foody-photos
                  └──── :sync-protocol (DTOs, kotlinx.serialization) ────┘
```

### 2.1 Module

| Modul | Inhalt | Abhängigkeiten |
|---|---|---|
| `:sync-protocol` (neu) | DTOs für Push/Pull/Auth/Fotos, Datensatztypen, Fehlercodes, `PROTOCOL_VERSION` | kotlinx.serialization |
| `:server` (neu) | Ktor-Server, SQLite (Exposed + Flyway), Argon2id, Admin-CLI | `:sync-protocol` (nicht `:domain`) |
| `:app` | Sync-Client, Outbox, Migration v3, Einstellungen-UI | `:sync-protocol` |

### 2.2 Docker Compose (`server/docker-compose.yml`)

- Dienst `foody-server`, Image aus mehrstufigem `server/Dockerfile` (Gradle-Build → schlankes JRE 21).
- Hängt im vorhandenen Traefik-Netzwerk (`networks: traefik: external: true, name: ${TRAEFIK_NETWORK}`),
  **kein veröffentlichter Port**.
- Traefik-Labels: `Host(${FOODY_HOST})`, Entrypoint `${TRAEFIK_ENTRYPOINT:-websecure}`,
  `tls.certresolver=${TRAEFIK_CERTRESOLVER}`, Service-Port 8080.
- Volumes: `foody-data` (`/data/foody.db`), `foody-photos` (`/data/photos`).
- `server/.env.example`: `FOODY_HOST`, `TRAEFIK_NETWORK`, `TRAEFIK_ENTRYPOINT`, `TRAEFIK_CERTRESOLVER`,
  `FOODY_ADMIN_USER`, `FOODY_ADMIN_PASSWORD` (nur beim allerersten Start ausgewertet).
- Healthcheck `GET /health`; Container als Nicht-Root-Benutzer, `read_only: true` mit `tmpfs: /tmp`,
  `cap_drop: [ALL]`, `no-new-privileges`.
- Datensicherung des Servers: SQLite im WAL-Modus; Admin-CLI `foody-admin backup <ziel>` erzeugt eine
  konsistente Kopie (`VACUUM INTO`); Fotos sind reine Dateien.

### 2.3 Android

- Einstellungen → **„Server verbinden“**: Server-URL, Anmelden / Registrieren mit Einladungscode,
  Haushalt anlegen oder wählen, Status „zuletzt synchronisiert“, „Sync-Probleme (n)“, Geräte, Einladen, Trennen.
- `SyncWorker` (WorkManager): periodisch 15 min bei Netz, zusätzlich einmalig ~5 s nach lokalen Änderungen
  (zusammengefasst), sowie manuell „Jetzt synchronisieren“.
- Gerätetoken verschlüsselt mit einem AES-Schlüssel aus dem Android Keystore; Passwort wird nie gespeichert.
- Manifest: `INTERNET`-Berechtigung neu; `network_security_config` verbietet weiterhin Klartext.
- ADR 0006 „Optionaler selbst gehosteter Sync“ löst ADR 0001 ab; `security.md` wird ergänzt.

## 3. Konten, Haushalte, Anmeldung

### 3.1 Server-Tabellen

| Tabelle | Felder |
|---|---|
| `user` | id, username (eindeutig, Vergleich ohne Groß-/Kleinschreibung), passwordHash (Argon2id), displayName, isAdmin, createdAt |
| `household` | id, name, createdBy, createdAt |
| `membership` | userId, householdId, role (`owner`/`member`), createdAt |
| `device` | id, userId, householdId, name, tokenHash (SHA-256), createdAt, lastSeenAt, revokedAt |
| `invite` | id, codeHash, householdId (null = nur Konto), createdBy, expiresAt (Standard +7 Tage), usedAt |

### 3.2 Abläufe

1. **Erster Start:** Existiert kein Benutzer, wird aus `FOODY_ADMIN_USER`/`FOODY_ADMIN_PASSWORD` ein Admin angelegt.
   Fehlen die Variablen, startet der Server mit Hinweis im Log, nimmt aber keine Logins an.
2. **Einladen:** `POST /api/v1/invites` (Mitglied eines Haushalts) → Code im Format `FOODY-XXXX-XXXX`
   (Crockford-Base32, 40 Bit Zufall), einmal nutzbar, 7 Tage gültig. Gespeichert wird nur der Hash.
3. **Registrieren:** `POST /api/v1/auth/register` {code, username, password, deviceName} → Konto, Mitgliedschaft
   (falls Code an Haushalt gebunden), Gerätetoken.
4. **Anmelden:** `POST /api/v1/auth/login` {username, password, deviceName} → Gerätetoken (32 Zufallsbytes,
   Base64url). Danach `GET /api/v1/households` und `POST /api/v1/device/household` {householdId} bzw.
   `POST /api/v1/households` {name} zum Anlegen.
5. **Jede API-Anfrage:** `Authorization: Bearer <token>`. Server prüft Hash, `revokedAt`, Mitgliedschaft.
   Die Haushalts-ID kommt **immer aus dem Gerät**, nie aus der Anfrage.
6. **Geräte/Mitglieder:** `GET /api/v1/devices`, `DELETE /api/v1/devices/{id}` (eigene Geräte);
   `DELETE /api/v1/households/{id}/members/{userId}` (nur `owner`) widerruft deren Geräte für diesen Haushalt.
7. **Passwort ändern:** `POST /api/v1/auth/password` {old, new}; widerruft alle anderen Geräte des Benutzers.

Mindestlänge Passwort 10 Zeichen. Login/Registrierung gedrosselt: 5 Fehlversuche je IP + Benutzername →
15 min Sperre. Fehlermeldungen unterscheiden nicht zwischen „unbekannter Benutzer“ und „falsches Passwort“.
Admin-CLI im Container: `foody-admin reset-password <user>`, `foody-admin invite` (Konto-Einladung ohne Haushalt),
`foody-admin backup <ziel>`.

### 3.3 Erster Sync eines Geräts

- **Neuen Haushalt anlegen:** alle lokalen Daten werden in die Outbox gestellt und hochgeladen.
- **Bestehendem Haushalt beitreten** mit vorhandenen lokalen Daten: Dialog
  - *Zusammenführen*: lokale Daten hochladen (Zutaten-Duplikate nach 4.3), dann Pull.
  - *Gerät ersetzen*: Sicherung anbieten (`BackupRepository`), lokale Daten leeren, Voll-Pull.
- Ohne lokale Daten (außer Startdaten-Zutaten): direkt Voll-Pull; Startdaten-Zutaten werden über den Namen zugeordnet.

## 4. Sync-Protokoll

### 4.1 Datensätze

| Typ | Payload |
|---|---|
| `ingredient` | Zutat inkl. Nährwerte, Umrechnung, Kategorie |
| `recipe` | Rezept + Zutatenzeilen + Schritte + Foto-Hash (statt `imageUri`) |
| `meal_slot` | Planposition |
| `pantry_item` | Vorratseintrag |
| `shopping_list` | Listenkopf |
| `shopping_item` | Eintrag + Herkunftszeilen; Felder `checked`, `checkedChangedAt` |

Hülle: `{ id, type, deleted, updatedAt, baseRev?, payload? }`; Server ergänzt `rev` (je Haushalt streng steigend,
vergeben in der Schreibtransaktion). Zahlen als Strings (wie im Backup-Format), Datumswerte ISO-8601.

### 4.2 Ablauf eines Sync-Laufs

1. Fotos: `POST /api/v1/photos/missing` {hashes} → fehlende per `PUT /api/v1/photos/{sha256}` hochladen (5.).
2. **Push** `POST /api/v1/sync/push` {protocolVersion, records[≤500]} (max. 5 MB). Server je Datensatz in einer
   Transaktion: Validierung → Konfliktprüfung → Speichern → neue `rev`. Antwort je Datensatz
   `accepted(rev)` / `merged(rev, canonicalId?)` / `rejected(code)`. Bestätigte Outbox-Einträge werden gelöscht.
3. **Pull** `GET /api/v1/sync/pull?since={cursor}&limit=500` → `{records, nextCursor, hasMore}` inkl. Löschungen.
   Gerät schreibt je Seite in **einer** Room-Transaktion, **ohne** Outbox-Einträge zu erzeugen, und speichert den
   Cursor in derselben Transaktion. Danach fehlende Fotos laden.
4. Lokale Datensätze, die noch in der Outbox stehen, werden beim Pull nicht überschrieben; der nächste Push klärt den
   Konflikt serverseitig.

### 4.3 Konfliktregeln

- **Standard:** Last-Writer-Wins nach Ankunftsreihenfolge am Server.
- **`shopping_item`:** Feld-Merge. Mengen/Name nach LWW; `checked` nach dem jüngeren `checkedChangedAt`.
  Bei Gleichstand gewinnt `checked = true`.
- **Löschen vs. Bearbeiten:** Löschung gewinnt, außer die Bearbeitung kommt mit `baseRev` ≥ Lösch-`rev` an
  (Gerät hat die Löschung gesehen und bewusst neu angelegt).
- **`ingredient` mit gleichem `canonicalName`, anderer ID:** Server behält die ältere ID, antwortet
  `merged(canonicalId)`. Das Gerät führt lokal über das vorhandene „Zutaten zusammenführen“ zusammen
  (Verweise in Rezepten, Vorrat, Einkauf umhängen) und stellt betroffene Datensätze erneut in die Outbox.
- **Referenzen auf fehlende Datensätze** (z. B. Rezeptzeile auf unbekannte Zutat): Server lehnt ab
  (`rejected(missing_reference)`); das Gerät pusht in Abhängigkeitsreihenfolge (ingredient → recipe → meal_slot →
  pantry_item → shopping_list → shopping_item), sodass das nur bei echten Fehlern auftritt.

### 4.4 Löschungen und Kompaktierung

- Server speichert Löschungen als Datensatz mit `deleted = true` (ohne Payload).
- Löschmarkierungen älter als 90 Tage werden kompaktiert; der Server merkt sich `compactedBeforeRev`.
  Pull mit `since < compactedBeforeRev` → `410` → Gerät macht Voll-Abgleich (alle Datensätze, lokale Datensätze
  ohne Server-Gegenstück und ohne Outbox-Eintrag werden gelöscht).

### 4.5 App-Datenbank (Migration v2 → v3)

- Neue Tabellen: `sync_outbox` (type, recordId, baseRev, deleted, queuedAt; PK type+recordId),
  `sync_record_rev` (type, recordId, rev), `sync_state` (Singleton: serverUrl, householdId, cursor,
  lastSyncAt, lastError), `sync_problem` (type, recordId, code, at).
- `shopping_item`: neue Spalten `updatedAt`, `checkedChangedAt`.
- Repositories schreiben Outbox-Einträge **in derselben Transaktion** wie die Änderung, nur wenn Sync aktiv ist.
  Harte Löschungen bleiben lokal; die Outbox trägt `deleted = true`.
- Migrationstest v2 → v3 (MigrationTestHelper) ist Pflicht; kein destruktiver Fallback.

## 5. Fotos

- Adressierung über Inhalt: SHA-256 des JPEGs (hex, 64 Zeichen). `recipe.payload.photo` = Hash oder null.
- Upload `PUT /api/v1/photos/{sha256}`: Server prüft Pfadsegment gegen `^[0-9a-f]{64}$`, Größe ≤ 10 MB
  (gezählt beim Lesen), JPEG-Signatur (`FF D8 FF`), Hash des Inhalts = Pfad. Ablage `photos/<householdId>/<sha>.jpg`
  über temporäre Datei + atomares Umbenennen.
- Download `GET /api/v1/photos/{sha256}` nur innerhalb des eigenen Haushalts.
- Auf dem Gerät speichert der vorhandene `RecipePhotoStore` heruntergeladene Fotos unter neuer UUID; eine Tabelle
  bzw. Spalte ordnet lokale Datei ↔ Hash zu.
- Nur eigene Fotos (`file:` im Fotoordner) werden übertragen, keine `content:`-Verweise anderer Apps.
- Server löscht Fotos, die seit 30 Tagen von keinem lebenden `recipe`-Datensatz referenziert werden.

## 6. Fehlerbehandlung

| Situation | Verhalten |
|---|---|
| Kein Netz, Timeout, 5xx | WorkManager-Retry mit exponentiellem Backoff; Outbox bleibt |
| `401` | Sync pausiert, Einstellungen zeigen „Abgemeldet – erneut verbinden“; lokale Daten bleiben |
| `409 protocol_too_old` / `server_too_old` | Hinweis, welche Seite zu aktualisieren ist; Sync pausiert |
| `410` | Voll-Abgleich (4.4) |
| `413` / zu viele Datensätze | Client teilt kleiner auf |
| `rejected(code)` für einzelne Datensätze | Eintrag in `sync_problem`, Rest läuft weiter; Anzeige „Sync-Probleme (n)“ |

Server-Validierung wie beim Wiederherstellen einer Sicherung (`security.md`): Zahlen als Strings mit Länge ≤ 40,
Skala −6…20, Präzision ≤ 30; Textlängen begrenzt (Name ≤ 200, Notizen/Schritte ≤ 10 000); Enum-Werte geprüft;
IDs als UUID geprüft.

## 7. Tests

- **`:sync-protocol`:** Serialisierungs-Round-Trips aller Typen; Versionsprüfung.
- **`:server`** (Ktor `testApplication`, SQLite in-memory):
  Bootstrap-Admin; Login/Drosselung; Einladung (abgelaufen, doppelt genutzt); Haushalts-Trennung
  (Gerät aus Haushalt A erhält keine Daten/Fotos aus B, auch nicht per geratener ID); Widerruf;
  Push/Pull-Paging; alle Konfliktregeln aus 4.3; Zutaten-Merge; Löschen + Kompaktierung + `410`;
  Foto-Validierung (falscher Hash, kein JPEG, zu groß, ungültiges Pfadsegment).
- **App:** Unit-Tests Outbox-Erzeugung je Repository-Operation und Pull-Anwendung; Room-Migrationstest v2 → v3;
  instrumentierter Ende-zu-Ende-Test „zwei Geräte“ gegen einen im Testprozess gestarteten Server
  (zwei In-Memory-Datenbanken, gleichzeitiges Abhaken + Mengenänderung, Offline-Konflikt, Fotos).
- **CI:** Job `:server:test`; Docker-Build des Images; Smoke-Test `docker compose up` → `/health` → Login.

## 8. Etappen (für den Umsetzungsplan)

1. `:sync-protocol` + `:server` (Auth, Haushalte, Push/Pull ohne Fotos) + Docker Compose + Server-Tests.
2. App: Migration v3, Outbox in allen Repositories, Pull-Anwendung (ohne UI, per Test angesteuert).
3. App: SyncClient, TokenStore, SyncWorker, Ende-zu-Ende-Test.
4. Fotos (Server + App).
5. Einstellungen-UI (Verbinden, Registrieren, Haushalt, Einladen, Geräte, Probleme), ADR 0006, `security.md`.

Jede Etappe ist ein eigener PR gegen `main` und für sich lauffähig (Sync bleibt bis Etappe 5 ohne UI unsichtbar).
