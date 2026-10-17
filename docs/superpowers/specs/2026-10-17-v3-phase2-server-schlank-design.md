# V3 Phase 2: Server schlank

Stand: 2026-10-17 · Grundlage: [ADR 0011](../../adr/0011-v3-umfang-ein-nutzer.md), [`ToDoS/v3-plan.md`](../../../ToDoS/v3-plan.md)
(Phase 2) · im Chat abschnittsweise abgestimmt.

## Ziel

Der Server in `main` kennt nur noch, was V3 braucht: **ein Konto statt Haushalten**, Push/Pull, Fotos und ein Gerüst
für die KI. Web-Oberfläche, Haushalte, Einladungen, Registrierung und Geräteliste fallen weg. Die bestehenden Daten
werden beim ersten Start einmalig umgewandelt.

**Fertig, wenn** zwei Geräte desselben Kontos über die API v3 gegenseitig ihre Änderungen sehen (Server-Test) und ein
Probelauf mit der echten Datenbank auf dem Test-Server alle 370 lebenden Datensätze behält.

**Rahmen:** Foody nutzt eine Person auf Handy und Tablet. Bis zum Umstieg (Phase 8) läuft die Alltags-App v1 gegen den
echten Server weiter; sie und ihr Server ändern sich nicht mehr.

## Entscheidungen

| Frage | Entscheidung |
|---|---|
| Übergang | **Harter Schnitt:** `main` bekommt den schlanken Server; der echte Server bleibt bis Phase 8 auf dem eingefrorenen Stand `v1-server` |
| v1-App | `:sync-protocol` (Protokoll 2) bleibt **eingefroren** für die v1-App; v3 bekommt ein neues Modul `:api-protocol` |
| Datenmodell | **Konto statt Haushalt**, sauber umbenannt (verworfen: Haushalt intern behalten; neue Datenbank mit Import) |
| Konten | nur über `foody-admin`; keine Registrierung, keine Admin-Rechte in der API |
| KI | nur Konfiguration, Kontingent und `GET /ki/status`; Modellaufrufe in Phase 7 |

## 1. Übergang und Module

### Eingefrorener Stand für den echten Server

- Vor dem ersten Phase-2-PR: Tag **`v1-server`** auf `main` und ein Branch gleichen Namens. Beide nach GitHub **und**
  Forgejo – der Server-Checkout holt von Forgejo.
- `~/docker/foody` (echter Server) wird auf den Branch `v1-server` umgestellt; ein `git pull` dort bringt nie den
  v3-Server.
- Fehlerbehebungen für den echten Server bis Phase 8: Cherry-Pick auf `v1-server`.

### Module in `main`

| Modul | Phase 2 |
|---|---|
| `:sync-protocol` (v2) | unverändert, nur noch von `:app` (v1) genutzt; entfällt in Phase 8 |
| `:api-protocol` (neu) | Protokoll 3: Login, Sync, Fotos, KI-Status – ohne Haushalte, Einladungen, Geräteliste, `pantry_item` |
| `:server-sync` | nutzt `:api-protocol`; Haushalts-, Einladungs-, Registrier- und Geräte-Routen entfallen |
| `:server-web` | entfernt (samt `RezeptSchreiber` … `TagebuchSchreiber`) |
| `:server` (start) | ohne Web-Oberfläche |
| `:app` (v1) | Produktionscode unverändert. `KtorSyncApiTest` und `TestSyncServer` werden entfernt (samt `testImplementation(:server-sync, :server-web)`): v1-Client und v1-Server sind am Tag `v1-server` gemeinsam getestet und ändern sich nicht mehr |

`:api-protocol` übernimmt aus `:sync-protocol`, was gleich bleibt (Datensätze, Payloads, Validator, `ZahlGrenzen`,
Fotos), als Kopie mit eigenem Paket `de.foody.api.protocol`. Gemeinsamer Code zwischen beiden Modulen ist nicht das
Ziel: `:sync-protocol` ist eingefroren.

### Version und Pfad

- Routen unter **`/api/v3`**, `Protocol.VERSION = 3`, `MIN_VERSION = 3`, Header weiter `X-Foody-Protocol`.
- `/api/v1/*` beantwortet der neue Server nur noch mit `409 protocol_too_old` (dieselbe Antwort wie heute) – eine vergessene v1-App zeigt so
  ihre vorhandene Meldung „Bitte App aktualisieren“.

## 2. Datenmodell, Umwandlung, API v3

### Schema nach Migration 1 → 2

- `user` (= Konto): neu `last_rev`, `compacted_before_rev`; `is_admin` entfällt.
- `sync_record`: Schlüssel `(konto_id, type, id)`; Indizes `(konto_id, rev)` und `(konto_id, type, canonical_name)`.
- `device`: ohne `household_id`. Tokens bleiben gültig – angemeldete Geräte bleiben angemeldet.
- `household`, `membership`, `invite`: entfallen.
- `haushalt_umzug(household_id, konto_id)`: nur bis der Fotoumzug erledigt ist.

`RecordStore`, `SyncService`, `Compactor` und `PhotoStore` arbeiten mit `konto` statt `household`;
`DevicePrincipal` wird `(deviceId, kontoId)`.

### Umwandlung beim ersten Start

1. **Sicherung:** Ist `PRAGMA user_version` kleiner als die Zahl der Migrationen, schreibt der Server vor der ersten
   Migration `vor-migration-<ziel>-<JJJJ-MM-TT-HHMM>.db` per `VACUUM INTO` neben die Datenbank. Gilt für alle
   künftigen Migrationen; ohne ausstehende Migration keine Sicherung.
2. **Vorprüfung:** Jeder Haushalt hat genau ein Mitglied, und kein Konto ist Mitglied mehrerer Haushalte. Sonst:
   Abbruch vor jeder Änderung, Meldung im Log (welcher Haushalt, wie viele Mitglieder), der Server startet nicht.
3. **Eine Transaktion:** Zähler vom Haushalt aufs Konto; `sync_record` neu mit `konto_id`; Datensätze vom Typ
   `pantry_item` löschen (Protokoll 3 kennt ihn nicht); `device` ohne Haushalt; alte Tabellen entfernen;
   `haushalt_umzug` füllen; `user_version = 2`.
4. **Fotoumzug** (Dateisystem, nach dem Commit): je Zeile in `haushalt_umzug` den Ordner `<root>/<household>/` nach
   `<root>/<konto>/` umbenennen, dann die Zeile löschen. Läuft bei jedem Start, bis die Tabelle leer ist – ein Absturz
   wird fortgesetzt. Existiert der alte Ordner nicht: Warnung, Zeile trotzdem löschen. Existieren beide: Inhalt
   zusammenführen (gleiche Namen = gleicher Inhalt, inhaltsadressiert), alten Ordner entfernen.

Bestand am 2026-10-17: 2 Konten (Admin ohne Haushalt, Stefan mit einem), 1 Haushalt mit 1 Mitglied, 3 aktive Geräte,
2 Einladungen, 370 lebende Datensätze, 0 Vorrat, 0 Fotos.

### API v3 (`/api/v3`)

| Route | Antwort |
|---|---|
| `POST /auth/login` `{username, password, deviceName}` | `{token, kontoId}` |
| `POST /auth/abmelden` | `204`; widerruft das eigene Token („Trennen“) |
| `POST /auth/password` | wie bisher |
| `POST /sync/push`, `GET /sync/pull?since&limit` | wie bisher, ohne Haushalt und ohne `pantry_item` |
| `POST /photos/missing`, `PUT /photos/{sha256}`, `GET /photos/{sha256}` | wie bisher |
| `GET /ki/status` | `{eingerichtet: Boolean}` |

`ErrorCode` ohne `no_household`, `username_taken`, `invalid_invite`, `forbidden`. Anmelde-Drosselung, Body-Grenzen,
Validator und Kompaktierung bleiben wie bisher.

**`foody-admin`:** `konto-anlegen <name>` (gibt ein zufälliges Passwort einmal aus) ersetzt `invite`;
`reset-password`, `backup`, `compact` bleiben. `FOODY_ADMIN_USER`/`FOODY_ADMIN_PASSWORD` legen beim ersten Start das
erste Konto an (ohne Sonderrechte).

## 3. KI-Gerüst, Fehlerverhalten, Tests, Betrieb

### KI-Gerüst

- `FOODY_KI_KEY` in `.env`; fehlt er, meldet `GET /ki/status` `eingerichtet: false`.
- `KiKontingent`: Anfragen je Konto und Stunde (Standard 30, `FOODY_KI_PRO_STUNDE`), eigene Klasse mit Tests;
  in Phase 2 noch von keiner Route genutzt außer dem Status.
- Kein Modellaufruf, keine Modellwahl – Phase 7 mit eigener ADR.

### Fehlerverhalten

| Fall | Verhalten |
|---|---|
| `/api/v1/*` oder Header < 3 | `409 protocol_too_old`; Header > 3 → `409 server_too_old` |
| Vorprüfung oder Migration scheitert | Server startet nicht, Grund im Log, Datenbank unverändert, Sicherung liegt daneben |
| Fotoordner fehlt beim Umzug | Warnung, Eintrag erledigt; die App meldet fehlende Fotos wie bisher als nicht übertragbar |
| Token eines abgemeldeten Geräts | `401 unauthorized` |

### Tests (JVM, `:server-sync`)

- **Migration:** v1-Datenbank als Testvorlage (Haushalt, Mitglied, Admin ohne Haushalt, Geräte inkl. widerrufener,
  Datensätze, Löschmarkierungen, `pantry_item`, Fotoordner) → alles beim Konto, Zähler übernommen, alte Tokens
  gültig, Vorrat weg, Fotos umgezogen.
- **Vorprüfung:** Haushalt mit zwei Mitgliedern → Abbruch, Datei byte-gleich.
- **Fotoumzug:** Abbruch nach dem ersten von zwei Ordnern → Neustart erledigt den zweiten; beide Ordner vorhanden →
  zusammengeführt.
- **Sicherung:** angelegt bei ausstehender Migration, nicht bei aktueller Datenbank.
- **API:** Login/Abmelden, Push/Pull, Fotos, `/api/v1` und Header 2 → `protocol_too_old`; **zwei Geräte desselben
  Kontos** sehen gegenseitig ihre Änderungen (Fertig-Kriterium).
- **`KiKontingent`** und `GET /ki/status` mit und ohne Schlüssel.
- **Probelauf** (von Hand): Sicherheitskopie vom 2026-10-17 auf den Test-Server, Start, Zählung 370 lebende Datensätze
  und Geräteanzahl.

### Betrieb und Reihenfolge

1. **Voraussetzung:** Stefan meldet sich bei Forgejo neu an (Zugangsdaten werden seit 2026-10-17 abgelehnt).
2. Tag und Branch `v1-server` anlegen und nach GitHub und Forgejo pushen; `~/docker/foody` auf `v1-server`
   umstellen. **Erst danach** wird ein Phase-2-PR gemergt.
3. Phase-2-PRs auf `main`; ausprobiert auf dem **Test-Server** (`~/docker/foody-test`). Der echte Server bleibt auf
   `v1-server` bis Phase 8.

Server-Neubau je PR: nur der Test-Server.

## Nicht in Phase 2

Sync-Client der V3-App (Phase 3) · KI-Funktionen und Modellwahl (Phase 7) · Entfernen von `:app`-v1-Code und
`:sync-protocol` (Phase 8) · Echtzeit-Sync.

## Risiken

| Risiko | Gegenmaßnahme |
|---|---|
| Ein Phase-2-PR landet auf dem echten Server | Checkout auf `v1-server` **vor** dem ersten Merge; Hook pusht nur `main` nach Forgejo |
| Umwandlung verliert Daten | Sicherung vor jeder Migration, Vorprüfung, eine Transaktion, Probelauf mit echten Daten |
| Fotoumzug halb erledigt | Abschlussvermerk je Ordner, Fortsetzen beim Start |
| v1-Tests fehlen in `main` | v1 ist eingefroren; die Tests bleiben am Tag `v1-server` lauffähig |
