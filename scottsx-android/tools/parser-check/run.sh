#!/usr/bin/env bash
#
# Runs the app's real Kotlin `fromJson` parsers against real backend JSON.
# See README.md in this directory for why this exists.
#
#   tools/parser-check/run.sh [kotlinc] [org.json jar]
#
# Requires: a running backend (default http://127.0.0.1:3001), node, kotlinc,
# a JDK, and an org.json jar.
set -uo pipefail

KOTLINC="${1:-${KOTLINC:-kotlinc}}"
OJ="${2:-${ORG_JSON_JAR:-}}"
API="${API_BASE:-http://127.0.0.1:3001}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MODELS="$ROOT/app/src/main/java/com/scottsx/app/data/domain/DashboardModels.kt"

if [ -z "$OJ" ]; then
  # Try the PySpark-bundled copy documented in the README.
  OJ="$(ls /tmp/ps/pyspark-*/deps/jars/json-1.8.jar 2>/dev/null | head -1)"
fi
if [ -z "$OJ" ] || [ ! -f "$OJ" ]; then
  echo "org.json jar not found. Pass it as \$2 or set \$ORG_JSON_JAR." >&2
  echo "See tools/parser-check/README.md for how to obtain one." >&2
  exit 127
fi

STDLIB="$(dirname "$(command -v "$KOTLINC" || echo "$KOTLINC")")/../lib/kotlin-stdlib.jar"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "Capturing live backend responses from $API…"
node "$ROOT/tools/parser-check/capture.mjs" "$WORK" "$API" || exit 1

echo "Building the harness from the shipping model source…"
python3 "$ROOT/tools/parser-check/build_harness.py" "$MODELS" "$WORK" || exit 1

"$KOTLINC" -cp "$OJ" "$WORK/Parse.kt" -d "$WORK/out" 2>&1 | grep -E "error:" && exit 1

java -cp "$WORK/out:$OJ:$STDLIB" ParseKt "$WORK"
