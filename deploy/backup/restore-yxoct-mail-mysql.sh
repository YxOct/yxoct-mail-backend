#!/usr/bin/env bash
set -Eeuo pipefail

readonly confirmation="--confirm-destroy-current-database"
if [[ $# -ne 2 || "$2" != "$confirmation" ]]; then
  echo "Usage: $0 <backup.sql.gz> $confirmation" >&2
  exit 2
fi

backup_file="$(realpath -- "$1")"
readonly backup_file
readonly CONFIG_FILE="${YXOCT_MAIL_BACKUP_CONFIG:-/etc/yxoct-mail/mysql-backup.conf}"

if [[ ! -f "$backup_file" || ! -r "$backup_file" ]]; then
  echo "Backup is not readable: $backup_file" >&2
  exit 1
fi
if [[ ! -r "$CONFIG_FILE" ]]; then
  echo "Backup configuration is not readable: $CONFIG_FILE" >&2
  exit 1
fi

# shellcheck disable=SC1090
source "$CONFIG_FILE"

: "${PROJECT_DIR:?PROJECT_DIR is required}"
: "${COMPOSE_FILE:?COMPOSE_FILE is required}"
: "${COMPOSE_ENV_FILE:?COMPOSE_ENV_FILE is required}"

gzip -t "$backup_file"
dump_header="$(gzip -cd "$backup_file" | sed -n '1,40p')"
if [[ "$dump_header" != *"-- MySQL dump"* ]]; then
  echo "Backup does not contain a MySQL dump header." >&2
  exit 1
fi

compose() {
  docker compose \
    --project-directory "$PROJECT_DIR" \
    --env-file "$COMPOSE_ENV_FILE" \
    -f "$COMPOSE_FILE" \
    "$@"
}

echo "[$(date --iso-8601=seconds)] Creating a pre-restore safety backup..."
"$(dirname -- "$0")/backup-yxoct-mail-mysql.sh"

backend_was_running="$(compose ps --status running --services | grep -Fx 'backend' || true)"
if [[ -n "$backend_was_running" ]]; then
  echo "[$(date --iso-8601=seconds)] Stopping backend writes..."
  compose stop backend
fi

restart_backend() {
  if [[ -n "$backend_was_running" ]]; then
    echo "[$(date --iso-8601=seconds)] Starting backend..."
    compose start backend
  fi
}
trap restart_backend EXIT

echo "[$(date --iso-8601=seconds)] Restoring MySQL backup: $backup_file"
# Variables in this single-quoted script are intentionally expanded inside the MySQL container.
# shellcheck disable=SC2016
gzip -cd "$backup_file" | compose exec -T mysql sh -ec '
  export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
  exec mysql --user=root
'

echo "[$(date --iso-8601=seconds)] Restore completed successfully."
