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

Beim **ersten Start** müssen `FOODY_ADMIN_USER` und `FOODY_ADMIN_PASSWORD` in `.env` gesetzt sein; sie legen den
Admin an. Danach beide Variablen leeren und mit `docker compose up -d` neu anwenden.

## Betrieb

- Einladung für ein neues **Konto ohne Haushalt**: `docker compose exec foody-server foody-admin invite`
  (keine Haushalts-Einladung; die erzeugen Mitglieder in der App über `POST /api/v1/invites`)
- Sicherung von Datenbank und Fotos: `FOODY_BACKUP_DIR=/pfad ./backup.sh` (im Ordner `server/`). Legt
  `foody-<Datum>.db` an, behält die letzten 14 Tage (`FOODY_BACKUP_KEEP`) und spiegelt die Fotos nach `fotos/`.
  Gedacht für einen Ordner, den ein OpenCloud-Client abgleicht. Nächtlich per Cron (`crontab -e`):
  `15 3 * * * cd /pfad/zu/Foody/server && FOODY_BACKUP_DIR=/pfad ./backup.sh >> /var/log/foody-backup.log 2>&1`
- Nur die Datenbank, von Hand: `docker compose exec foody-server foody-admin backup /data/backup-$(date +%F).db`
- Update: `git pull && docker compose up -d --build`
- Speicher: Der Container ist auf 512 MB begrenzt (die JVM nimmt davon 75 %); anders mit `FOODY_MEM_LIMIT` in `.env`.

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
`http://10.0.2.2:8081`. Admin-Befehle mit demselben `-p foody-test -f … -f …`.

## Web-Oberfläche

Die Web-Oberfläche ist mit V3 entfallen (ADR 0011).

## Fotos

Rezeptfotos liegen als `<FOODY_PHOTO_DIR>/<Haushalt>/<sha256>.jpg` (Standard `/data/photos`, eigenes Docker-Volume
`foody-photos`; in die Sicherung der Datenbank sind sie **nicht** eingeschlossen, das Volume gesondert sichern).
Upload nur als JPEG bis 10 MB (`PUT /api/v1/photos/{sha256}`). Fotos, die kein lebendes Rezept mehr verwendet,
löscht die tägliche Kompaktierung (und `foody-admin compact`) nach 30 Tagen.

## Lokale Entwicklung unter Windows

Das erzeugte `foody-server.bat` (`FOODY_SERVER_ONLY=1 ./gradlew :server:installDist`) scheitert dort mit „Zeile zu lang“.
Stattdessen im Installationsordner (`server/start/build/install/foody-server`) starten:

```sh
java -cp "lib/*" de.foody.server.MainKt
```

oder Docker verwenden. Manuelle API-Aufrufe (z. B. mit `curl`) brauchen den Header `X-Foody-Protocol: 2` (aktuelle `Protocol.VERSION`),
sonst antwortet der Server mit `protocol_too_old`.

## Hinweis zu Traefik (wichtig)

Die Begrenzung von Fehlversuchen beim Login richtet sich nach der Client-IP, die Traefik per `X-Forwarded-For`
setzt. Clients dürfen diesen Header deshalb nicht selbst setzen können: Am Entrypoint weder
`forwardedHeaders.insecure` noch `forwardedHeaders.trustedIPs` mit Client-Netzen konfigurieren.
