#!/usr/bin/env bash
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/var/backups/yxoct-mail}"
STALWART_RETENTION_DAYS="${STALWART_RETENTION_DAYS:-30}"
MYSQL_RETENTION_DAYS="${MYSQL_RETENTION_DAYS:-30}"

[[ -d "$BACKUP_DIR" ]] || {
  echo "Backup directory does not exist: $BACKUP_DIR" >&2
  exit 1
}

echo "[$(date --iso-8601=seconds)] Cleaning remote YxOct Mail backups..."

find "$BACKUP_DIR" \
  -maxdepth 1 \
  -type f \
  \( -name 'stalwart-*.tar.gz' -o -name 'stalwart-*.tar.gz.sha256' \) \
  -mtime "+$STALWART_RETENTION_DAYS" \
  -print \
  -delete

if [[ -d "$BACKUP_DIR/mysql" ]]; then
  find "$BACKUP_DIR/mysql" \
    -maxdepth 1 \
    -type f \
    -name 'yxoct-mail-mysql-*.sql.gz' \
    -mtime "+$MYSQL_RETENTION_DAYS" \
    -print \
    -delete
fi

echo "[$(date --iso-8601=seconds)] Cleanup finished."
