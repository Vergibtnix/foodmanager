#!/bin/sh
# Tägliches Backup für den Foodmanager (läuft im Container "foodmanager-backup", siehe docker-compose.yml).
#
# Gesichert werden:
#  - die Datenbank (pg_dump, Format "custom", mit pg_restore wieder einspielbar)
#  - die hochgeladenen Bilder (./data/images)
#  - die Keycloak-Daten (Benutzer und Passwörter)
#
# Alles landet in ./backups auf dem Pi. Ältere Backups als BACKUP_KEEP_DAYS werden gelöscht.
# Tipp: ./backups zusätzlich regelmäßig auf einen anderen Rechner oder USB-Stick kopieren,
# sonst ist bei einem SD-Karten-Defekt auch das Backup weg.
set -eu

KEEP_DAYS="${BACKUP_KEEP_DAYS:-14}"
INTERVAL="${BACKUP_INTERVAL_SECONDS:-86400}"
TARGET=/backups

mkdir -p "$TARGET"

run_backup() {
	stamp=$(date +%Y-%m-%d_%H%M)
	echo "[backup] Start $stamp"

	# Erst in eine Zwischendatei schreiben, damit nie ein halbes Backup wie ein fertiges aussieht
	pg_dump -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" -Fc -f "$TARGET/db_$stamp.dump.part"
	mv "$TARGET/db_$stamp.dump.part" "$TARGET/db_$stamp.dump"

	if [ -d /data/images ]; then
		tar -czf "$TARGET/images_$stamp.tar.gz.part" -C /data images
		mv "$TARGET/images_$stamp.tar.gz.part" "$TARGET/images_$stamp.tar.gz"
	fi

	if [ -d /keycloak ]; then
		tar -czf "$TARGET/keycloak_$stamp.tar.gz.part" -C /keycloak .
		mv "$TARGET/keycloak_$stamp.tar.gz.part" "$TARGET/keycloak_$stamp.tar.gz"
	fi

	find "$TARGET" -maxdepth 1 -type f \( -name 'db_*' -o -name 'images_*' -o -name 'keycloak_*' \) \
		-mtime +"$KEEP_DAYS" -delete
	rm -f "$TARGET"/*.part

	echo "[backup] Fertig $stamp"
}

# Kurz warten, bis die Datenbank bereit ist
until pg_isready -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" >/dev/null 2>&1; do
	sleep 5
done

while true; do
	run_backup || echo "[backup] FEHLER, nächster Versuch beim nächsten Durchlauf"
	sleep "$INTERVAL"
done
