#!/usr/bin/env bash
#
# ScottsTechX — run every automated check in one go.
#
#   ./verify.sh
#
# Requires the backend running on :3001 (cd 12_Backend && npm run dev).
# Verification resends are rate limited in production; tests/production-safety.mjs
# boots its own servers to cover both the limit and the supersede path, so this
# server can run with either setting.
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

hr "2b/9  Firebase Authentication (local JWKS, no egress)"
(cd "$ROOT/12_Backend" && node tests/firebase-auth.mjs) || FAILED=1

hr "2c/9  Production-mode safety (NODE_ENV=production)"
(cd "$ROOT/12_Backend" && node tests/production-safety.mjs) || FAILED=1

hr "3/9  Android ⇆ backend contract"
(cd "$ROOT/12_Backend" && node tests/android-contract.mjs) || FAILED=1

hr "4/9  Web UI (real bundle, real backend)"
# Build against the running API, otherwise generate-sitemap.mjs cannot reach it
# and dist/sitemap.xml silently degrades to the 6 static routes -- which still
# passes every assertion, so the product URLs would go untested forever.
(cd "$ROOT/web" && VITE_API_URL="$API" SITE_URL="${SITE_URL:-http://127.0.0.1:5173}" \
   npm run build >/dev/null 2>&1 && node tests/ui.mjs) || FAILED=1

hr "5/9  TypeScript"
(cd "$ROOT/12_Backend" && npx tsc --noEmit && echo "backend: clean") || FAILED=1
(cd "$ROOT/web" && npx tsc --noEmit -p tsconfig.json && echo "web: clean") || FAILED=1

hr "6/9  Android wiring (routes, client calls, screen reachability)"
(cd "$ROOT/scottsx-android" && ./tools/wiring-check.sh) || FAILED=1

hr "7/9  Android resources (icons, colours, themes)"
(cd "$ROOT/scottsx-android" && ./tools/res-check.sh) || FAILED=1

hr "4b/9 Web viewport audit (resolved CSS cascade at real widths)"
for WH in "320 780" "360 780" "390 844" "414 896" "768 1024" "1280 800" "360 640" "320 568"; do
  set -- $WH
  (cd "$ROOT/web" && node tests/viewport-audit.mjs "$1" "$2" >/dev/null) || {
    echo "  viewport audit FAILED at ${1}x${2} — rerun: (cd web && node tests/viewport-audit.mjs $1 $2)"
    FAILED=1
  }
done
echo "  audited 8 viewports incl. short screens (360x640, 320x568)"

hr "7b/9 Android layout (edge-to-edge insets, overflow, brand artwork)"
(cd "$ROOT/scottsx-android" && node ./tools/layout-check.mjs) || FAILED=1

hr "7c/9 Compose API contract (@Composable context, imports, call sites)"
(cd "$ROOT/scottsx-android" && node ./tools/compose-contract-check.mjs) || FAILED=1

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
