#!/usr/bin/env bash
#
# ScottsTechX — run every automated check in one go.
#
#   ./verify.sh
#
# Requires the backend running on :3001 (cd 12_Backend && npm run dev).
#
# The Kotlin checks are skipped unless a JDK + kotlinc are available; see
# scottsx-android/tools/README.md for how to obtain them without root.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API="${API_BASE:-http://127.0.0.1:3001}"
FAILED=0

hr() { printf '\n\033[1m═══ %s ═══\033[0m\n' "$1"; }
note() { printf '   \033[2m%s\033[0m\n' "$1"; }

if ! curl -sf -m 5 "$API/healthz" >/dev/null; then
  echo "Backend not reachable at $API — start it with: cd 12_Backend && npm run dev" >&2
  exit 1
fi

hr "1/9  Backend end-to-end"
(cd "$ROOT/12_Backend" && node tests/e2e.mjs) || FAILED=1

hr "2/9  Google Sign-In (local IdP, no egress)"
(cd "$ROOT/12_Backend" && node tests/google-auth.mjs) || FAILED=1

hr "3/9  Android ⇆ backend contract"
(cd "$ROOT/12_Backend" && node tests/android-contract.mjs) || FAILED=1

hr "4/9  Web UI (real bundle, real backend)"
(cd "$ROOT/web" && npm run build >/dev/null 2>&1 && node tests/ui.mjs) || FAILED=1

hr "5/9  TypeScript"
(cd "$ROOT/12_Backend" && npx tsc --noEmit && echo "backend: clean") || FAILED=1
(cd "$ROOT/web" && npx tsc --noEmit -p tsconfig.json && echo "web: clean") || FAILED=1

hr "6/9  Android wiring (routes, client calls, screen reachability)"
(cd "$ROOT/scottsx-android" && ./tools/wiring-check.sh) || FAILED=1

hr "7/9  Android resources (icons, colours, themes)"
(cd "$ROOT/scottsx-android" && ./tools/res-check.sh) || FAILED=1

KOTLINC="${KOTLINC:-$(command -v kotlinc || true)}"
if [ -n "$KOTLINC" ] && [ -n "${JAVA_HOME:-}" ]; then
  hr "8/9  Kotlin syntax"
  (cd "$ROOT/scottsx-android" && ./tools/kotlin-syntax-check.sh "$KOTLINC") || FAILED=1

  hr "9/9  Kotlin parsers vs real API JSON"
  (cd "$ROOT/scottsx-android" && ./tools/parser-check/run.sh "$KOTLINC") || FAILED=1
else
  hr "8-9/9  Kotlin checks"
  note "skipped — set \$JAVA_HOME and \$KOTLINC (see scottsx-android/tools/README.md)"
  note "on a machine with the Android SDK, run ./gradlew assembleDebug instead"
fi

printf '\n'
if [ "$FAILED" -eq 0 ]; then
  printf '\033[32m\033[1mAll checks passed.\033[0m\n'
else
  printf '\033[31m\033[1mSome checks FAILED.\033[0m\n'
fi
exit "$FAILED"
