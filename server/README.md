# Foody-Sync-Server

Selbst gehosteter Sync-Server (Ktor + SQLite) für die Foody-App, gedacht für den Betrieb hinter Traefik.

**Aufbau:** `sync/` (API, Konten, Datenbank, Sync, Admin-CLI), `start/` (`main`, aus dem das
Docker-Image entsteht). Der Server nutzt außerdem `shared/api-protocol` (gemeinsam mit der App) – Änderungen dort
brauchen einen Neubau des Servers.

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

Weitere Admin-Befehle (`docker compose exec foody-server foody-admin …`, Übersicht ohne Argumente):

- `geraete`: alle Geräte mit letzter Anfrage; `abmelden-ungenutzt <tage>` widerruft Geräte, die sich so lange nicht
  gemeldet haben (Tokens laufen sonst nie ab).
- `konto-loeschen <name>`: nur für Konten ohne Datensätze und Fotos.
- `reset-password <name>`, `compact`, `pruefen <datei>` (Sicherung prüfen), `wiederhergestellt` (siehe unten).

### Sicherung einspielen

Befehle im Ordner `server/`; die Volume-Namen (hier `foody_foody-data`, `foody_foody-photos`) vorher mit
`docker volume ls` prüfen, `<sicherung>` ist der Ordner aus `FOODY_BACKUP_DIR`.

1. Server stoppen: `docker compose stop foody-server`.
2. Jetzigen Stand aufheben: `docker compose run --rm --no-deps foody-server admin backup /data/vor-einspielen.db`.
3. Sicherung ins Volume legen und prüfen (Ausgabe `ok`):
   ```sh
   docker run --rm --user 0 --entrypoint sh -v foody_foody-data:/data -v <sicherung>:/einspielen:ro foody-server:local -c \
     'cp /einspielen/foody-<JJJJ-MM-TT-HHMM>.db /data/einspielen.db && chown 10001:10001 /data/einspielen.db'
   docker compose run --rm --no-deps foody-server admin pruefen /data/einspielen.db
   ```
4. Einspielen und WAL-Reste entfernen:
   ```sh
   docker run --rm --user 0 --entrypoint sh -v foody_foody-data:/data foody-server:local -c \
     'rm -f /data/foody.db-wal /data/foody.db-shm && mv /data/einspielen.db /data/foody.db'
   ```
5. Fotos zurückkopieren (vorhandene bleiben, Namen sind Prüfsummen):
   ```sh
   docker run --rm --user 0 --entrypoint sh -v foody_foody-photos:/data/photos -v <sicherung>/fotos:/einspielen:ro \
     foody-server:local -c 'cp -rn /einspielen/. /data/photos/ && chown -R 10001:10001 /data/photos'
   ```
6. **Vor dem Start:** `docker compose run --rm --no-deps foody-server admin wiederhergestellt`. Danach gleichen alle
   Geräte beim nächsten Sync voll ab und senden zurück, was nach der Sicherung entstanden ist. Ohne diesen Schritt
   gingen Änderungen der Geräte seit der Sicherung verloren. Stammt die Sicherung von einer älteren Version, stellt der
   Befehl die Datenbank vorher um (mit `vor-migration-*.db` wie beim Start).
7. Starten: `docker compose up -d`.

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
Protokoll 3.

## Datenbank und Migrationen

Der Server kennt nur ein Konto je Person, Push/Pull, Fotos und das KI-Gerüst (API v3, Protokoll 3; ADR 0011).
Haushalte, Einladungen, Registrierung, Geräteliste und Web-Oberfläche gibt es nicht mehr. Der Umstieg von der v1 lief
am 2026-10-28; der Rückweg dorthin ist seit dem 2026-10-31 geschlossen (Stand davor: Tag `v1-final`).

- **Neue Datenbank:** Sie bekommt in einem Schritt das Grundschema in Version 2 (Konten), danach die übrigen
  Migrationen (`Migrations.kt`). Eine Datenbank des v1-Servers (Version 1, Haushalte) wandelt dieser Server nicht mehr
  um; er startet dann nicht und nennt den Grund im Log.
- **Vor jeder ausstehenden Migration** sichert der Server die Datenbank automatisch (`vor-migration-<Ziel>-<JJJJ-MM-TT-HHMM>.db`
  neben der Datenbankdatei, per `VACUUM INTO`); ohne ausstehende Migration entsteht keine Sicherung. Migration 3 → 4
  entfernt den Rest der Umwandlung auf Konten (`haushalt_umzug`) und bricht ab, falls dort noch Fotoordner offen sind.
- **Scheitert eine Migration,** startet der Server nicht; der Grund steht als eine Zeile im Log
  (`docker compose logs foody-server`), die Datenbank bleibt unverändert. Innerhalb von 24 Stunden nach der ersten
  Sicherung legt ein erneuter Start **keine** frische Sicherung an (eine Neustart-Schleife soll keine Sicherungen
  anhäufen) – es bleibt die von vor dem ersten Versuch; leere oder halb geschriebene Sicherungen zählen nicht. Nach einer
  Korrektur von Hand vor dem erneuten Start deshalb bei Bedarf selbst sichern, bei gestopptem Server:
  `docker compose run --rm --no-deps foody-server admin backup /data/vor-neustart.db`. `foody-admin` migriert nie:
  Solange eine Migration aussteht, geht nur `backup`; die übrigen Befehle brechen mit „Datenbank auf Version …“ ab.
  Für den Test-Server dieselben Befehle mit `-p foody-test -f docker-compose.yml -f docker-compose.test.yml`.
- Anfragen mit `X-Foody-Protocol` unter 3 beantwortet der Server mit `409 protocol_too_old` (die App zeigt „Bitte App
  aktualisieren“); unter `/api/v1/…` antwortet nichts mehr (`404`).

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
`X-Foody-Protocol: 3` (aktuelle `Protocol.VERSION`), sonst antwortet der Server mit `protocol_too_old`.

## Hinweis zu Traefik (wichtig)

Die Begrenzung von Fehlversuchen beim Login richtet sich nach der Client-IP, die Traefik per `X-Forwarded-For`
setzt. Clients dürfen diesen Header deshalb nicht selbst setzen können: Am Entrypoint weder
`forwardedHeaders.insecure` noch `forwardedHeaders.trustedIPs` mit Client-Netzen konfigurieren.
