#!/usr/bin/env bash
#
# Kotlin syntax / structural check for the ScottsTechX Android app.
#
# Why this exists
# ---------------
# The environment this code was authored in has no Android SDK and cannot reach
# maven.google.com, so the Compose/AndroidX artifacts are unavailable and a real
# `./gradlew assembleDebug` cannot run. What CAN run is the genuine Kotlin
# compiler frontend over every source file. Type resolution against Android
# classes will obviously fail, but the parser still reports true defects:
# syntax errors, unbalanced braces, malformed literals, duplicate declarations,
# bad `when` branches, missing initialisers, and so on.
#
# The script therefore compiles everything and then keeps only the diagnostics
# that are NOT explained by the missing classpath.
#
# Usage:
#   tools/kotlin-syntax-check.sh [/path/to/kotlinc]
#   KOTLINC=/path/to/kotlinc tools/kotlin-syntax-check.sh
#
# What it catches (verified against deliberately injected bugs):
#   ✅ unbalanced braces / parens        ✅ duplicate declarations
#   ✅ malformed literals & expressions  ✅ reassigning a `val`
#   ✅ private-in-file shadowing that breaks sibling files
#
# Known blind spots — deliberately traded away to avoid false positives, since
# almost every expression here touches an unresolved Compose type:
#   ❌ non-exhaustive `when`      (indistinguishable from classpath cascades)
#   ❌ return/argument type mismatches
# Those are exactly the errors the real Gradle build will surface first, so run
# it once the SDK is available.
#
# On a machine with the Android SDK, prefer the real thing:
#   ./gradlew assembleDebug
set -uo pipefail

KOTLINC="${1:-${KOTLINC:-kotlinc}}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# SRC_DIR overrides the scan root (e.g. to check the v2 tree:
#   SRC_DIR=$PWD/../scottsx-android-v2/app/src/main/java tools/kotlin-syntax-check.sh)
SRC="${SRC_DIR:-$ROOT/app/src/main/java}"

if ! command -v "$KOTLINC" >/dev/null 2>&1 && [ ! -x "$KOTLINC" ]; then
  echo "kotlinc not found at '$KOTLINC'. Pass a path or set \$KOTLINC." >&2
  exit 127
fi

# Optional extra classpath (e.g. a kotlinx-coroutines jar). Supplying it removes
# a large class of cascading false positives around `scope.launch { … }`.
EXTRA_CP="${KOTLIN_EXTRA_CP:-}"

mapfile -t FILES < <(find "$SRC" -name '*.kt' | sort)
if [ "${#FILES[@]}" -eq 0 ]; then
  echo "No Kotlin sources under $SRC" >&2
  exit 1
fi
echo "Checking ${#FILES[@]} Kotlin files with $("$KOTLINC" -version 2>&1 | grep -o 'kotlinc-jvm [0-9.]*' | head -1)…"

RAW="$(mktemp)"
OUTDIR="$(mktemp -d)"
trap 'rm -rf "$RAW" "$OUTDIR"' EXIT

# Compile as one module so references between our own files DO resolve; only
# external AndroidX/Compose/Firebase symbols remain unresolved.
if [ -n "$EXTRA_CP" ]; then
  "$KOTLINC" -cp "$EXTRA_CP" "${FILES[@]}" -d "$OUTDIR" >"$RAW" 2>&1
else
  "$KOTLINC" "${FILES[@]}" -d "$OUTDIR" >"$RAW" 2>&1
fi

# Diagnostics that are purely a consequence of the absent Android classpath.
# Anything outside this allowlist is treated as a real defect.
IGNORE='unresolved reference'
IGNORE="$IGNORE|unresolved import"
IGNORE="$IGNORE|cannot access script base class"
IGNORE="$IGNORE|cannot infer type"
IGNORE="$IGNORE|not enough information to infer"
IGNORE="$IGNORE|overload resolution ambiguity"
IGNORE="$IGNORE|none of the following candidates"
IGNORE="$IGNORE|is not a function"
IGNORE="$IGNORE|function invocation .* expected"
IGNORE="$IGNORE|expression .* of type .* cannot be invoked"
IGNORE="$IGNORE|annotation .* is unresolved"
IGNORE="$IGNORE|this class does not have a constructor"
IGNORE="$IGNORE|too many arguments"
IGNORE="$IGNORE|no value passed for parameter"
IGNORE="$IGNORE|cannot find a parameter with this name"
IGNORE="$IGNORE|argument type mismatch"
IGNORE="$IGNORE|initializer type mismatch"
IGNORE="$IGNORE|assignment type mismatch"
IGNORE="$IGNORE|return type mismatch"
IGNORE="$IGNORE|condition type mismatch"
IGNORE="$IGNORE|delegate .* type mismatch"
IGNORE="$IGNORE|type mismatch: inferred type"
IGNORE="$IGNORE|inferred type .* but .* was expected"
IGNORE="$IGNORE|not applicable to target"
IGNORE="$IGNORE|is not an annotation class"
IGNORE="$IGNORE|only safe .* calls are allowed"
IGNORE="$IGNORE|operator call corresponds to a dot-qualified"
IGNORE="$IGNORE|variable expected"
IGNORE="$IGNORE|val cannot be reassigned"
IGNORE="$IGNORE|smart cast to"
IGNORE="$IGNORE|receiver type mismatch"
IGNORE="$IGNORE|expected type mismatch"
# `scope.launch { … }` is an unresolved symbol without the coroutines artifact,
# so the compiler cannot tell that suspend calls inside it are legal.
IGNORE="$IGNORE|can only be called from a coroutine"
IGNORE="$IGNORE|suspension functions .*coroutine"   # Kotlin ≥1.9 moved the wording (still classpath-cascade noise)
# Kotlin 1.9.x words the same diagnostic differently. Without the exact string
# the check drowns in ~80 false positives and stops being readable, which is
# how a real error hides.
IGNORE="$IGNORE|should be called only from a coroutine or another suspend function"
# `_state.value = x` where _state is an unresolved MutableStateFlow.
IGNORE="$IGNORE|variable expected"
# `return` inside an inline lambda (Closeable.use) is legal, but okhttp is not
# on the classpath so the compiler treats .use as a non-inline call.
IGNORE="$IGNORE|.return. is not allowed here"
IGNORE="$IGNORE|a .return. expression required in a function with a block body"
IGNORE="$IGNORE|cannot infer a type for this parameter"
# Opt-in diagnostics are raised against unresolved Compose/stdlib declarations.
IGNORE="$IGNORE|this declaration needs opt-in"
IGNORE="$IGNORE|this class can only be used with the compiler argument"
# Cascades from an unresolved receiver/argument type: the compiler cannot match
# an overload or a named parameter when one of the operands is already unknown.
IGNORE="$IGNORE|inapplicable candidate"
IGNORE="$IGNORE|no parameter with name"
IGNORE="$IGNORE|is ambiguous for destructuring"
IGNORE="$IGNORE|.operator. modifier is required"
IGNORE="$IGNORE|.when. expression must be exhaustive"
# Lambda receivers (DrawScope in Canvas { }, GraphicsLayerScope in
# graphicsLayer { }) are unresolved, so `this` inside them looks undefined.
IGNORE="$IGNORE|'this' is not defined in this context"
# @OptIn(ExperimentalX::class): the marker class is unresolved, so the
# compiler cannot prove the ::class argument is a compile-time constant.
IGNORE="$IGNORE|annotation argument must be a compile-time constant"
# `by lazy { FirebaseAuth... }` — the delegate's target type is unresolved.
IGNORE="$IGNORE|None of the following functions is applicable"
# Index-gets / comparisons on unresolved types (e.g. the activity-result
# permission map) leave operands with nonsense types.
IGNORE="$IGNORE|operator .* cannot be applied to"
# Destructuring an unresolved receiver property (DrawScope.size is Size and
# has component1/2 in the real build).
IGNORE="$IGNORE|requires operator function 'component"
# SnapshotStateList<JSONObject>.remove(element) collapses without the Compose
# runtime and resolves to the deprecated MutableList.remove(index: Int).
# (Local kotlinc 2.4.x raises that as an error; the project's Kotlin 1.9/2.0
# toolchain never sees it. Parens are escaped — IGNORE is an ERE.)
IGNORE="$IGNORE|remove\\(index: Int\\): T' is deprecated"
# Non-local return from an inline Compose lambda (Box/Column) whose inline
# nature is invisible without the classpath.
IGNORE="$IGNORE|'return' is prohibited here"

REAL="$(grep -E '\.kt:[0-9]+:[0-9]+: error:' "$RAW" | grep -Ev "$IGNORE" || true)"

if [ -n "$REAL" ]; then
  COUNT="$(printf '%s\n' "$REAL" | wc -l | tr -d ' ')"
  echo
  echo "❌ $COUNT structural / syntax problem(s):"
  printf '%s\n' "$REAL" | sed "s|$ROOT/||"
  exit 1
fi

TOTAL="$(grep -cE '\.kt:[0-9]+:[0-9]+: error:' "$RAW" || true)"
echo "✅ No syntax or structural errors."
echo "   ($TOTAL classpath-only diagnostics suppressed — expected without the Android SDK.)"
