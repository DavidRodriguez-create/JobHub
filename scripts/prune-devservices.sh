#!/usr/bin/env bash
#
# Prune leftover Quarkus DevServices / Testcontainers Postgres containers.
#
# Quarkus spins up a throwaway Postgres for `quarkus:dev` and `@QuarkusTest`.
# On Windows/podman there is no Ryuk reaper, so a hard-killed dev/test JVM can
# orphan its container. These hold only ephemeral test data and are safe to drop.
#
# This ONLY touches the DevServices image (postgres:18). It never removes the
# real app stack (`jobhub-db` runs postgres:16-alpine), the kind clusters, or
# the jobhub-*-service containers.
#
# Usage:
#   scripts/prune-devservices.sh          # remove leftovers
#   scripts/prune-devservices.sh --dry    # list what would be removed
set -euo pipefail

IMAGE="docker.io/library/postgres:18"

# Prefer podman; fall back to docker.
if command -v podman >/dev/null 2>&1; then
  ENGINE=podman
elif command -v docker >/dev/null 2>&1; then
  ENGINE=docker
else
  echo "error: neither podman nor docker found on PATH" >&2
  exit 1
fi

ids=$("$ENGINE" ps -aq --filter "ancestor=$IMAGE" || true)

if [ -z "$ids" ]; then
  echo "No DevServices ($IMAGE) containers to prune."
  exit 0
fi

count=$(printf '%s\n' "$ids" | wc -l | tr -d ' ')

if [ "${1:-}" = "--dry" ] || [ "${1:-}" = "-n" ]; then
  echo "Would remove $count DevServices container(s):"
  "$ENGINE" ps -a --filter "ancestor=$IMAGE" --format '  {{.Names}}\t{{.Status}}'
  exit 0
fi

echo "Removing $count DevServices ($IMAGE) container(s)..."
# shellcheck disable=SC2086
"$ENGINE" rm -f $ids >/dev/null
echo "Done. Remaining: $("$ENGINE" ps -aq --filter "ancestor=$IMAGE" | wc -l | tr -d ' ')"
