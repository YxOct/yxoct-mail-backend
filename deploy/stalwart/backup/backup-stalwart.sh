#!/usr/bin/env bash
set -euo pipefail

STALWART_DIR="${STALWART_DIR:-/home/ubuntu/stalwart}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/stalwart}"
DATE="$(date '+%Y-%m-%d_%H-%M-%S')"
BACKUP_FILE="$BACKUP_DIR/stalwart-$DATE.tar.gz"
TEMP_FILE="$BACKUP_FILE.tmp"
CHECKSUM_FILE="$BACKUP_FILE.sha256"

REMOTE_HOST="${REMOTE_HOST:?REMOTE_HOST must be configured}"
REMOTE_USER="${REMOTE_USER:-ubuntu}"
REMOTE_PATH="${REMOTE_PATH:-.}"
SSH_KEY="${SSH_KEY:-/home/ubuntu/.ssh/stalwart_backup}"
LOCAL_RETENTION_DAYS="${LOCAL_RETENTION_DAYS:-7}"
LOCK_FILE="${LOCK_FILE:-/run/lock/stalwart-backup.lock}"
BACKUP_OWNER="${BACKUP_OWNER:-ubuntu:ubuntu}"

mkdir -p "$(dirname "$LOCK_FILE")" "$BACKUP_DIR"

if [[ "$REMOTE_PATH" == /* || "$REMOTE_PATH" == *".."* ]]; then
    echo "REMOTE_PATH must stay within the forced rrsync root: $REMOTE_PATH" >&2
    exit 1
fi

exec 9>"$LOCK_FILE"
if ! flock -n 9; then
    echo "[$(date --iso-8601=seconds)] Another backup is already running; exiting."
    exit 0
fi

for command in docker tar rsync ssh sha256sum; do
    command -v "$command" >/dev/null || {
        echo "Required command not found: $command" >&2
        exit 1
    }
done

[[ -f "$STALWART_DIR/compose.yaml" ]] || {
    echo "Missing $STALWART_DIR/compose.yaml" >&2
    exit 1
}

[[ -r "$SSH_KEY" ]] || {
    echo "SSH key is not readable: $SSH_KEY" >&2
    exit 1
}

log() {
    echo "[$(date --iso-8601=seconds)] $*"
}

log "Starting Stalwart backup..."

cd "$STALWART_DIR"

stopped=false
cleanup() {
    status=$?
    rm -f -- "$TEMP_FILE"
    if [[ "${stopped:-false}" == "true" ]]; then
        log "Starting Stalwart..."
        if ! docker compose start stalwart; then
            log "ERROR: failed to restart Stalwart."
            exit 1
        fi
    fi
    exit "$status"
}
trap cleanup EXIT

log "Stopping Stalwart..."
docker compose stop stalwart
stopped=true

log "Creating local backup..."

tar -czf "$TEMP_FILE" \
    compose.yaml \
    etc \
    data

tar -tzf "$TEMP_FILE" >/dev/null
chmod 600 "$TEMP_FILE"
mv -- "$TEMP_FILE" "$BACKUP_FILE"

(
    cd "$BACKUP_DIR"
    sha256sum "$(basename "$BACKUP_FILE")" > "$(basename "$CHECKSUM_FILE")"
)
chown "$BACKUP_OWNER" "$BACKUP_FILE" "$CHECKSUM_FILE"
chmod 600 "$BACKUP_FILE" "$CHECKSUM_FILE"

log "Backup created: $BACKUP_FILE"

log "Syncing backups to remote server..."

rsync -az --partial-dir=.rsync-partial \
    -e "ssh -i $SSH_KEY -o BatchMode=yes -o ConnectTimeout=15" \
    "$BACKUP_FILE" \
    "$CHECKSUM_FILE" \
    "$REMOTE_USER@$REMOTE_HOST:$REMOTE_PATH/"

log "Remote sync completed."
log "Removing local backups older than $LOCAL_RETENTION_DAYS days..."

find "$BACKUP_DIR" \
    -maxdepth 1 \
    -type f \
    \( -name 'stalwart-*.tar.gz' -o -name 'stalwart-*.tar.gz.sha256' \) \
    -mtime "+$LOCAL_RETENTION_DAYS" \
    -delete

log "Backup finished successfully."
