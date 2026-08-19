#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <image-repository> <image-tag>" >&2
  exit 2
fi

readonly image_repository="$1"
readonly image_tag="$2"
readonly project_dir="${YXOCT_MAIL_PROJECT_DIR:-/opt/yxoct-mail-backend}"
readonly compose_file="$project_dir/compose.prod.yaml"
readonly environment_file="$project_dir/deploy/.env.prod"
readonly readiness_url="${YXOCT_MAIL_READINESS_URL:-http://127.0.0.1:8081/actuator/health/readiness}"
readonly readiness_attempts="${YXOCT_MAIL_READINESS_ATTEMPTS:-60}"
readonly readiness_interval="${YXOCT_MAIL_READINESS_INTERVAL:-2}"

[[ -d "$project_dir/.git" ]] || {
  echo "Git repository not found: $project_dir" >&2
  exit 1
}
[[ -r "$compose_file" ]] || {
  echo "Compose file is not readable: $compose_file" >&2
  exit 1
}
[[ -r "$environment_file" ]] || {
  echo "Production environment file is not readable: $environment_file" >&2
  exit 1
}
[[ "$readiness_attempts" =~ ^[1-9][0-9]*$ ]] || {
  echo "YXOCT_MAIL_READINESS_ATTEMPTS must be a positive integer" >&2
  exit 1
}
[[ "$readiness_interval" =~ ^[1-9][0-9]*$ ]] || {
  echo "YXOCT_MAIL_READINESS_INTERVAL must be a positive integer" >&2
  exit 1
}

cd "$project_dir"

compose() {
  BACKEND_IMAGE_REPOSITORY="$1" \
    BACKEND_IMAGE_TAG="$2" \
    docker compose \
      --env-file "$environment_file" \
      -f "$compose_file" \
      "${@:3}"
}

previous_image="$(docker inspect yxoct-mail-backend --format '{{.Config.Image}}' 2>/dev/null || true)"

echo "Validating production Compose configuration..."
compose "$image_repository" "$image_tag" config --quiet

echo "Pulling backend image: $image_repository:$image_tag"
compose "$image_repository" "$image_tag" pull backend

echo "Deploying backend image..."
compose "$image_repository" "$image_tag" up -d --no-deps backend

for ((attempt = 1; attempt <= readiness_attempts; attempt++)); do
  if curl --fail --silent "$readiness_url" >/dev/null 2>&1; then
    echo "Backend readiness check passed."
    compose "$image_repository" "$image_tag" ps backend
    exit 0
  fi
  sleep "$readiness_interval"
done

echo "Backend readiness check failed." >&2
compose "$image_repository" "$image_tag" logs --tail=150 backend >&2 || true

if [[ -n "$previous_image" && "$previous_image" == *:* ]]; then
  previous_repository="${previous_image%:*}"
  previous_tag="${previous_image##*:}"
  echo "Rolling back to previous backend image: $previous_image" >&2
  compose "$previous_repository" "$previous_tag" up -d --no-deps backend

  for ((attempt = 1; attempt <= readiness_attempts; attempt++)); do
    if curl --fail --silent "$readiness_url" >/dev/null 2>&1; then
      echo "Rollback readiness check passed." >&2
      exit 1
    fi
    sleep "$readiness_interval"
  done

  echo "Rollback readiness check also failed." >&2
  compose "$previous_repository" "$previous_tag" logs --tail=150 backend >&2 || true
fi

exit 1
