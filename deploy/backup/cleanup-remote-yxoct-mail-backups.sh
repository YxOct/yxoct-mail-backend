#!/usr/bin/env bash
set -Eeuo pipefail

readonly BACKUP_ROOT="/var/backups/yxoct-mail"
readonly STALWART_RETENTION_DAYS="${STALWART_RETENTION_DAYS:-30}"
readonly MYSQL_RETENTION_DAYS="${MYSQL_RETENTION_DAYS:-30}"

echo "[$(date --iso-8601=seconds)] Cleaning remote YxOct Mail backups..."

find "$BACKUP_ROOT" \
  -maxdepth 1 \
  -type f \
  -name 'stalwart-*.tar.gz' \
  -mtime "+$STALWART_RETENTION_DAYS" \
  -print \
  -delete

if [[ -d "$BACKUP_ROOT/mysql" ]]; then
  find "$BACKUP_ROOT/mysql" \
    -maxdepth 1 \
    -type f \
    -name 'yxoct-mail-mysql-*.sql.gz' \
    -mtime "+$MYSQL_RETENTION_DAYS" \
    -print \
    -delete
fi

echo "[$(date --iso-8601=seconds)] Remote backup cleanup finished."
