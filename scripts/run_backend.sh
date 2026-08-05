#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GATEWAY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
DOCKER_START_TIMEOUT_SECONDS=120

if ! command -v docker >/dev/null 2>&1; then
  echo "error: docker command not found. Install Docker Desktop first." >&2
  exit 69
fi

if ! docker info >/dev/null 2>&1; then
  if [[ "$(uname -s)" != "Darwin" ]] || ! command -v open >/dev/null 2>&1; then
    echo "error: Docker daemon is not running. Start Docker and retry." >&2
    exit 69
  fi

  echo "Docker daemon is not running. Starting Docker Desktop..."
  open -a Docker

  elapsed=0
  until docker info >/dev/null 2>&1; do
    if (( elapsed >= DOCKER_START_TIMEOUT_SECONDS )); then
      echo "error: Docker Desktop did not become ready within ${DOCKER_START_TIMEOUT_SECONDS} seconds." >&2
      exit 70
    fi

    sleep 2
    ((elapsed += 2))
  done

  echo "Docker Desktop is ready."
fi

required_env_files=(
  "${GATEWAY_DIR}/../togethertrip-server-main/src/main/resources/.env"
  "${GATEWAY_DIR}/../togethertrip-server-notification/src/main/resources/.env"
)

for env_file in "${required_env_files[@]}"; do
  if [[ ! -f "${env_file}" ]]; then
    echo "error: required local environment file not found: ${env_file}" >&2
    exit 66
  fi
done

cd "${GATEWAY_DIR}"
exec docker compose up --build "$@"
