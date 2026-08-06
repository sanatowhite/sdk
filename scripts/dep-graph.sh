#!/usr/bin/env bash
#
# Human-readable dependency tree for one module. Wraps the Gradle command
# you'd otherwise have to remember/look up every time you're trying to
# figure out why some library got pulled in.
#
# Usage:
#   ./scripts/dep-graph.sh :app
#   ./scripts/dep-graph.sh :core-net --configuration debugRuntimeClasspath
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "Usage: $0 <module-path> [--configuration <name>]" >&2
  echo "  e.g. $0 :app" >&2
  echo "       $0 :core-net --configuration debugRuntimeClasspath" >&2
  exit 1
fi

MODULE="$1"
shift

CONFIGURATION="releaseRuntimeClasspath"
if [ "${1:-}" = "--configuration" ]; then
  CONFIGURATION="$2"
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

echo "==> Dependency tree for $MODULE (configuration: $CONFIGURATION)"
./gradlew "${MODULE}:dependencies" --configuration "$CONFIGURATION"
