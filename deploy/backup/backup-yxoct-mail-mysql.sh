#!/usr/bin/env bash
set -Eeuo pipefail

readonly CONFIG_FILE="${YXOCT_MAIL_BACKUP_CONFIG:-/etc/yxoct-mail/mysql-backup.conf}"
readonly LOCK_FILE="/run/lock/yxoct-mail-mysql-backup.lock"

if [[ ! -r "$CONFIG_FILE" ]]; then
  echo "Backup configuration is not readable: $CONFIG_FILE" >&2
  exit 1
fi

# shellcheck disable=SC1090
source "$CONFIG_FILE"

: "${PROJECT_DIR:?PROJECT_DIR is required}"
: "${COMPOSE_FILE:?COMPOSE_FILE is required}"
: "${COMPOSE_ENV_FILE:?COMPOSE_ENV_FILE is required}"
: "${BACKUP_DIR:?BACKUP_DIR is required}"
: "${LOCAL_RETENTION_DAYS:=7}"
: "${REMOTE_ENABLED:=false}"

exec 9>"$LOCK_FILE"
if ! flock -n 9; then
  echo "Another MySQL backup is already running." >&2
  exit 1
fi

umask 077
mkdir -p "$BACKUP_DIR"

timestamp="$(date '+%Y-%m-%d_%H-%M-%S')"
readonly timestamp
readonly backup_file="$BACKUP_DIR/yxoct-mail-mysql-$timestamp.sql.gz"
readonly temporary_file="$backup_file.tmp"

cleanup() {
  rm -f -- "$temporary_file"
}
trap cleanup EXIT

compose() {
  docker compose \
    --project-directory "$PROJECT_DIR" \
    --env-file "$COMPOSE_ENV_FILE" \
    -f "$COMPOSE_FILE" \
    "$@"
}

echo "[$(date --iso-8601=seconds)] Creating MySQL backup..."

# Variables in this single-quoted script are intentionally expanded inside the MySQL container.
# shellcheck disable=SC2016
compose exec -T mysql sh -ec '
  export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
  exec mysqldump \
    --user=root \
    --single-transaction \
    --quick \
    --routines \
    --triggers \
    --events \
    --hex-blob \
    --no-tablespaces \
    --set-gtid-purged=OFF \
    --add-drop-database \
    --databases "$MYSQL_DATABASE"
' | gzip -c >"$temporary_file"

gzip -t "$temporary_file"
dump_header="$(gzip -cd "$temporary_file" | sed -n '1,40p')"
if [[ "$dump_header" != *"-- MySQL dump"* ]]; then
  echo "Backup does not contain a MySQL dump header." >&2
  exit 1
fi

chmod 600 "$temporary_file"
mv -- "$temporary_file" "$backup_file"
echo "[$(date --iso-8601=seconds)] Backup created: $backup_file"

find "$BACKUP_DIR" \
  -maxdepth 1 \
  -type f \
  -name 'yxoct-mail-mysql-*.sql.gz' \
  -mtime "+$LOCAL_RETENTION_DAYS" \
  -print \
  -delete

if [[ "$REMOTE_ENABLED" == "true" ]]; then
  : "${REMOTE_USER:?REMOTE_USER is required when remote sync is enabled}"
  : "${REMOTE_HOST:?REMOTE_HOST is required when remote sync is enabled}"
  : "${REMOTE_SUBDIR:=mysql}"
  : "${SSH_KEY:?SSH_KEY is required when remote sync is enabled}"

  echo "[$(date --iso-8601=seconds)] Syncing backup to restricted remote storage..."
  rsync -az \
    -e "ssh -i $SSH_KEY -o BatchMode=yes -o ConnectTimeout=15" \
    "$backup_file" \
    "$REMOTE_USER@$REMOTE_HOST:$REMOTE_SUBDIR/"
  echo "[$(date --iso-8601=seconds)] Remote sync completed."
fi

echo "[$(date --iso-8601=seconds)] MySQL backup finished successfully."
