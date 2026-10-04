# Foody-Sync-Server

Selbst gehosteter Sync-Server (Ktor + SQLite) für die Foody-App, gedacht für den Betrieb hinter Traefik.

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
- Sicherung: `docker compose exec foody-server foody-admin backup /data/backup-$(date +%F).db`
- Update: `git pull && docker compose up -d --build`

Weitere Admin-Befehle: `reset-password`, `compact` (siehe `foody-admin` ohne Argumente).

## Fotos

Rezeptfotos liegen als `<FOODY_PHOTO_DIR>/<Haushalt>/<sha256>.jpg` (Standard `/data/photos`, eigenes Docker-Volume
`foody-photos`; in die Sicherung der Datenbank sind sie **nicht** eingeschlossen, das Volume gesondert sichern).
Upload nur als JPEG bis 10 MB (`PUT /api/v1/photos/{sha256}`). Fotos, die kein lebendes Rezept mehr verwendet,
löscht die tägliche Kompaktierung (und `foody-admin compact`) nach 30 Tagen.

## Hinweis zu Traefik (wichtig)

Die Begrenzung von Fehlversuchen beim Login richtet sich nach der Client-IP, die Traefik per `X-Forwarded-For`
setzt. Clients dürfen diesen Header deshalb nicht selbst setzen können: Am Entrypoint weder
`forwardedHeaders.insecure` noch `forwardedHeaders.trustedIPs` mit Client-Netzen konfigurieren.
