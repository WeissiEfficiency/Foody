# Foody-Sync-Server

Selbst gehosteter Sync-Server (Ktor + SQLite) für die Foody-App, gedacht für den Betrieb hinter Traefik.

**Aufbau:** `sync/` (API, Konten, Datenbank, Sync, Admin-CLI), `start/` (`main`, aus dem das
Docker-Image entsteht). Der Server nutzt außerdem `../shared/` (gemeinsam mit der App) – Änderungen dort brauchen
ebenfalls ein Update des Servers.

## Voraussetzungen

- Docker mit Compose und ein laufender Traefik in einem externen Docker-Netzwerk (Name in `TRAEFIK_NETWORK`).
- Ein Host-Name (`FOODY_HOST`), den die Clients per DNS auflösen. WireGuard-Clients erreichen ihn über ihren
  WireGuard-DNS; der Server selbst veröffentlicht keine Ports.

## Installation

```sh
cd server
cp .env.example .env      # Werte anpassen (.env wird nicht eingecheckt)
docker compose up -d --build
```

Beim **ersten Start** müssen `FOODY_ADMIN_USER` und `FOODY_ADMIN_PASSWORD` in `.env` gesetzt sein; sie legen das
erste Konto an (ohne Sonderrechte). Danach beide Variablen leeren und mit `docker compose up -d` neu anwenden.

## Betrieb

- Neues Konto: `docker compose exec foody-server foody-admin konto-anlegen <name>` (gibt das Zufallspasswort
  einmal aus)
- Sicherung von Datenbank und Fotos: `FOODY_BACKUP_DIR=/pfad ./backup.sh` (im Ordner `server/`). Legt
  `foody-<Datum>.db` an, behält die letzten 14 Tage (`FOODY_BACKUP_KEEP`) und spiegelt die Fotos nach `fotos/`.
  Gedacht für einen Ordner, den ein OpenCloud-Client abgleicht. Nächtlich per Cron (`crontab -e`):
  `15 3 * * * cd /pfad/zu/Foody/server && FOODY_BACKUP_DIR=/pfad ./backup.sh >> /var/log/foody-backup.log 2>&1`
- Nur die Datenbank, von Hand: `docker compose exec foody-server foody-admin backup /data/backup-$(date +%F).db`
- Update: `git pull && docker compose up -d --build`
- Speicher: Der Container ist auf 512 MB begrenzt (die JVM nimmt davon 75 %); anders mit `FOODY_MEM_LIMIT` in `.env`.
- KI-Hilfe (ab Phase 7): `FOODY_KI_KEY` in `.env` setzen (leer = aus), Kontingent je Konto und Stunde mit
  `FOODY_KI_PRO_STUNDE` (Standard 30). Bisher meldet der Server nur, ob ein Schlüssel eingerichtet ist.

Weitere Admin-Befehle: `reset-password`, `compact` (siehe `foody-admin` ohne Argumente).

## Test-Server (Entwicklung)

Ein zweiter Server mit eigenen Daten für die Entwicklung, auf demselben Host. Er läuft aus einem eigenen Checkout,
damit ein Entwicklungs-Branch nie den echten Server berührt, und ist nur auf dem Host selbst erreichbar (kein Traefik).

```sh
git -C ~/docker/foody worktree add --detach ~/docker/foody-test main   # einmalig; Stand später mit git switch/checkout wechseln
cd ~/docker/foody-test/server
cp .env.example .env          # eigene .env des Test-Checkouts: FOODY_ADMIN_USER/-PASSWORD für den ersten Start
docker compose -p foody-test -f docker-compose.yml -f docker-compose.test.yml up -d --build
```

Vom PC: `ssh -N -L 8081:127.0.0.1:8081 homeserver.lan`, dann in der Debug-App (Emulator) die Adresse
`http://10.0.2.2:8081`. Admin-Befehle mit demselben `-p foody-test -f … -f …`. Aus `main` spricht der Test-Server
Protokoll 3; die v1-App verbindet sich damit nicht mehr (erst der Sync-Client der V3, Phase 3).

## Umstieg auf V3

Seit V3 Phase 2 kennt der Server in `main` nur noch ein Konto je Person, Push/Pull, Fotos und das KI-Gerüst
(API v3, Protokoll 3). Haushalte, Einladungen, Registrierung, Geräteliste und Web-Oberfläche gibt es nicht mehr
(ADR 0011, Spec `docs/superpowers/specs/2026-10-17-v3-phase2-server-schlank-design.md`).

- **Echter Server bis zum Umstieg:** `~/docker/foody` läuft bis Phase 8 auf dem eingefrorenen Branch `v1-server`
  (Protokoll 2, für die v1-App); ein `git pull` dort bringt nie den v3-Server. Fehlerbehebungen per Cherry-Pick auf
  `v1-server`. V3 läuft bis dahin nur auf dem Test-Server.
- **Erster Start mit V3:** Der Server sichert die Datenbank automatisch (`vor-migration-2-<JJJJ-MM-TT-HHMM>.db`
  neben der Datenbankdatei, per `VACUUM INTO`), prüft vorab (jeder Haushalt hat genau ein Mitglied, kein Konto ist in
  mehreren Haushalten, keine verwaisten Datensätze) und wandelt dann in einer Transaktion mit Gegenzählung um: alles
  gehört dem Konto, Vorrats-Datensätze werden gelöscht, angemeldete Geräte bleiben angemeldet. Danach zieht er die Fotoordner von
  `<Haushalt-ID>/` nach `<Konto-ID>/` um; ein abgebrochener Umzug wird beim nächsten Start fortgesetzt, ein fehlender
  Ordner nur als Warnung gemeldet.
- **Scheitert die Vorprüfung oder die Umwandlung,** startet der Server nicht; der Grund steht im Log
  (`docker compose logs foody-server`), die Datenbank bleibt unverändert. Nach einer Korrektur von Hand legt der
  nächste Start **keine** frische Sicherung an – es bleibt die von vor dem ersten Versuch. Vor dem erneuten Start
  deshalb bei Bedarf selbst sichern, als Dateikopie bei gestopptem Server:
  `docker compose run --rm --no-deps --entrypoint sh foody-server -c 'cp /data/foody.db /data/vor-neustart.db'`.
  `foody-admin backup` aus dem V3-Image taugt dafür nicht: Es öffnet die Datenbank und migriert sie dabei.
- Künftige Migrationen sichern genauso vorher; ohne ausstehende Migration entsteht keine Sicherung.
- `/api/v1/…` und Anfragen mit `X-Foody-Protocol` unter 3 beantwortet der Server mit `409 protocol_too_old` – eine
  vergessene v1-App zeigt „Bitte App aktualisieren“.

## Fotos

Rezeptfotos liegen als `<FOODY_PHOTO_DIR>/<Konto-ID>/<sha256>.jpg` (Standard `/data/photos`, eigenes Docker-Volume
`foody-photos`; in die Sicherung der Datenbank sind sie **nicht** eingeschlossen, das Volume gesondert sichern).
Upload nur als JPEG bis 10 MB (`PUT /api/v3/photos/{sha256}`). Fotos, die kein lebendes Rezept mehr verwendet,
löscht die tägliche Kompaktierung (und `foody-admin compact`) nach 30 Tagen.

## Lokale Entwicklung unter Windows

Das erzeugte `foody-server.bat` (`FOODY_SERVER_ONLY=1 ./gradlew :server:installDist`) scheitert dort mit „Zeile zu lang“.
Stattdessen im Installationsordner (`server/start/build/install/foody-server`) starten:

```sh
java -cp "lib/*" de.foody.server.MainKt
```

oder Docker verwenden. Manuelle API-Aufrufe (z. B. mit `curl`) gehen an `/api/v3/…` und brauchen den Header
`X-Foody-Protocol: 3` (aktuelle `Protocol.VERSION`), sonst antwortet der Server mit `protocol_too_old`; `/api/v1/…`
meldet immer `protocol_too_old`.

## Hinweis zu Traefik (wichtig)

Die Begrenzung von Fehlversuchen beim Login richtet sich nach der Client-IP, die Traefik per `X-Forwarded-For`
setzt. Clients dürfen diesen Header deshalb nicht selbst setzen können: Am Entrypoint weder
`forwardedHeaders.insecure` noch `forwardedHeaders.trustedIPs` mit Client-Netzen konfigurieren.
