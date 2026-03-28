#!/usr/bin/env bash
set -euo pipefail

TAG="${1:-alex-backend:validate}"
NO_CACHE="${2:-}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
DOCKERFILE="${REPO_ROOT}/backend/Dockerfile"
BUILD_CONTEXT="${REPO_ROOT}/backend"

if [[ ! -f "${DOCKERFILE}" ]]; then
  echo "Backend Dockerfile not found: ${DOCKERFILE}" >&2
  exit 1
fi

BUILD_ARGS=(build -f "${DOCKERFILE}" -t "${TAG}")
if [[ "${NO_CACHE}" == "--no-cache" ]]; then
  BUILD_ARGS+=(--no-cache)
fi
BUILD_ARGS+=("${BUILD_CONTEXT}")

DOCKER_BUILDKIT=1 docker "${BUILD_ARGS[@]}"
docker run --rm --entrypoint sh "${TAG}" -c "test -s /app/app.jar"
docker image inspect "${TAG}" --format '{{.Id}}'
