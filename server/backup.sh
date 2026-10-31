#!/bin/sh
# Sicherung von Datenbank und Fotos in einen Ordner auf dem Host – gedacht für einen Ordner, den ein OpenCloud-Client
# abgleicht. Die Datenbank kommt als datierte Kopie dazu (die letzten FOODY_BACKUP_KEEP Tage bleiben); die Fotos
# werden in fotos/ gespiegelt: Sie ändern sich nie (Name = Prüfsumme), der Client lädt also nur neue hoch.
#
# Aufruf im Ordner server/:   FOODY_BACKUP_DIR=/pfad/zum/sync-ordner ./backup.sh
# Nächtlich per Cron, z. B.:  15 3 * * * cd /pfad/zu/Foody/server && FOODY_BACKUP_DIR=/pfad ./backup.sh >> /var/log/foody-backup.log 2>&1
# `foody-admin backup` prüft die Kopie mit `PRAGMA integrity_check`; ist sie kaputt, bricht das Skript ab und die
# Sicherungen der Vortage bleiben. Einspielen: server/README.md, Abschnitt „Sicherung einspielen“.
set -eu

ziel="${FOODY_BACKUP_DIR:?FOODY_BACKUP_DIR fehlt: Zielordner der Sicherung}"
behalten="${FOODY_BACKUP_KEEP:-14}"
cd "$(dirname "$0")"

stempel=$(date +%F-%H%M)
im_container="/data/backup-$stempel.db"
aufraeumen() { docker compose exec -T foody-server rm -f "$im_container" || true; }
trap aufraeumen EXIT

mkdir -p "$ziel/fotos"
docker compose exec -T foody-server foody-admin backup "$im_container"
# Erst unter .part schreiben, dann umbenennen: Der Sync-Client sieht nie eine halbe Datei.
docker compose cp "foody-server:$im_container" "$ziel/foody-$stempel.db.part"
mv "$ziel/foody-$stempel.db.part" "$ziel/foody-$stempel.db"
docker compose cp "foody-server:/data/photos/." "$ziel/fotos/"

find "$ziel" -maxdepth 1 -name 'foody-*.db' -mtime +"$behalten" -delete
echo "$(date '+%F %T') Sicherung fertig: $ziel/foody-$stempel.db"
