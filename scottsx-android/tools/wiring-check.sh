#!/usr/bin/env bash
# ScottsTechX — Android wiring check.
#
# The Kotlin syntax checker suppresses type-level diagnostics (no Android SDK
# here), so it cannot tell you that a screen calls a V2Client method that does
# not exist, or navigates to a route nobody defined. Those are exactly the
# mistakes that survive until the first Gradle build.
#
# This script closes that gap with cheap, exact cross-references:
#   1. every V2Client.<method> called is actually declared in V2Client.kt
#   2. every Routes.<NAME> referenced is declared, and none is dead
#   3. every screen file is reachable from AppNavigation.kt
#
# Usage:  tools/wiring-check.sh      (from scottsx-android/)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SRC="app/src/main/java"
CLIENT="$SRC/com/scottsx/app/data/remote/V2Client.kt"
NAV="$SRC/com/scottsx/app/navigation/AppNavigation.kt"
SCREENS="$SRC/com/scottsx/app/ui/screens"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

problems=0
note() { echo "  ✗ $*"; problems=$((problems + 1)); }

echo "Android wiring check"

# ── 1. V2Client ────────────────────────────────────────────────────────────
grep -rhoE "V2Client\.[a-zA-Z_][A-Za-z0-9_]*" "$SRC" --include=*.kt \
  | sed 's/V2Client\.//' | sort -u > "$TMP/used"
grep -oE "(suspend )?fun [a-zA-Z_][A-Za-z0-9_]*" "$CLIENT" \
  | awk '{print $NF}' | sort -u > "$TMP/declared"
# Constants and properties on the object are valid references too.
grep -oE "va[lr] [a-zA-Z_][A-Za-z0-9_]*" "$CLIENT" \
  | awk '{print $NF}' | sort -u >> "$TMP/declared"
sort -u -o "$TMP/declared" "$TMP/declared"

missing="$(comm -23 "$TMP/used" "$TMP/declared" || true)"
if [ -n "$missing" ]; then
  while read -r m; do
    [ -n "$m" ] && note "V2Client.$m is called but not declared in V2Client.kt"
  done <<< "$missing"
else
  echo "  ✓ all $(wc -l < "$TMP/used" | tr -d ' ') V2Client call sites resolve"
fi

# ── 2. Routes ──────────────────────────────────────────────────────────────
grep -rhoE "Routes\.[A-Z_][A-Z0-9_]*" "$SRC" --include=*.kt \
  | sed 's/Routes\.//' | sort -u > "$TMP/routes_used"
grep -oE "const val [A-Z_][A-Z0-9_]*" "$NAV" | awk '{print $NF}' | sort -u > "$TMP/routes_def"

undef="$(comm -23 "$TMP/routes_used" "$TMP/routes_def" || true)"
if [ -n "$undef" ]; then
  while read -r r; do
    [ -n "$r" ] && note "Routes.$r is referenced but never defined"
  done <<< "$undef"
fi

dead="$(comm -13 "$TMP/routes_used" "$TMP/routes_def" || true)"
if [ -n "$dead" ]; then
  while read -r r; do
    [ -n "$r" ] && note "Routes.$r is defined but never navigated to (dead route)"
  done <<< "$dead"
fi

[ -z "$undef$dead" ] && echo "  ✓ all $(wc -l < "$TMP/routes_def" | tr -d ' ') routes defined and used"

# ── 3. Screen reachability ─────────────────────────────────────────────────
unreachable=0
for f in "$SCREENS"/*.kt; do
  name="$(basename "$f" .kt)"
  if ! grep -q "\b$name\b" "$NAV"; then
    note "$name.kt is never referenced from AppNavigation.kt — unreachable"
    unreachable=$((unreachable + 1))
  fi
done
[ "$unreachable" -eq 0 ] && \
  echo "  ✓ all $(ls "$SCREENS"/*.kt | wc -l | tr -d ' ') screens reachable from AppNavigation"

echo
if [ "$problems" -gt 0 ]; then
  echo "❌ $problems wiring problem(s) — these WILL fail the Gradle build."
  exit 1
fi
echo "✅ Android wiring is consistent."
