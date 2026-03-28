#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${1:-.env.deploy}"
BACKUP_DIR="${2:-}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ENV_PATH="${REPO_ROOT}/${ENV_FILE}"
COMPOSE_FILE="${REPO_ROOT}/docker-compose.deploy.yml"
WAIT_SCRIPT="${SCRIPT_DIR}/wait-for-http.sh"

if [[ -z "${BACKUP_DIR}" ]]; then
  echo "Usage: restore.sh <env-file> <backup-dir>" >&2
  exit 1
fi

if [[ ! -f "${ENV_PATH}" ]]; then
  echo "Env file not found: ${ENV_PATH}" >&2
  exit 1
fi

if [[ ! -d "${BACKUP_DIR}" ]]; then
  echo "Backup directory not found: ${BACKUP_DIR}" >&2
  exit 1
fi

PROJECT_NAME="$(grep '^COMPOSE_PROJECT_NAME=' "${ENV_PATH}" | head -n1 | cut -d'=' -f2- || true)"
if [[ -z "${PROJECT_NAME}" ]]; then
  PROJECT_NAME="alex-prod"
fi

HEALTHCHECK_URL="$(grep '^ALEX_BACKEND_HEALTHCHECK_URL=' "${ENV_PATH}" | head -n1 | cut -d'=' -f2- || true)"
if [[ -z "${HEALTHCHECK_URL}" ]]; then
  HEALTHCHECK_URL="http://localhost:8080/actuator/health"
fi

VOLUMES=(
  "postgres-data"
  "cassandra-data"
  "minio-data"
  "caddy-data"
  "caddy-config"
  "prometheus-data"
  "alertmanager-data"
  "grafana-data"
)

docker compose --env-file "${ENV_PATH}" -f "${COMPOSE_FILE}" down

for volume in "${VOLUMES[@]}"; do
  ARCHIVE="${BACKUP_DIR}/${volume}.tgz"
  if [[ ! -f "${ARCHIVE}" ]]; then
    echo "Missing archive: ${ARCHIVE}" >&2
    exit 1
  fi

  FULL_VOLUME="${PROJECT_NAME}_${volume}"
  docker volume create "${FULL_VOLUME}" >/dev/null

  docker run --rm \
    -v "${FULL_VOLUME}:/target" \
    alpine:3.20 \
    sh -c "rm -rf /target/* /target/.[!.]* /target/..?* 2>/dev/null || true"

  docker run --rm \
    -v "${FULL_VOLUME}:/target" \
    -v "${BACKUP_DIR}:/backup:ro" \
    alpine:3.20 \
    sh -c "tar -xzf /backup/${volume}.tgz -C /target"
done

docker compose --env-file "${ENV_PATH}" -f "${COMPOSE_FILE}" up -d
"${WAIT_SCRIPT}" "${HEALTHCHECK_URL}" 180

echo "Restore completed from: ${BACKUP_DIR}"
