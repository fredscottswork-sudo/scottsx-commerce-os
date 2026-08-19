#!/usr/bin/env bash
#
# Apply the CI fixes to master, from your own machine.
#
#   bash ci/apply-workflow-fixes.sh
#
# WHY THIS SCRIPT EXISTS
# ----------------------
# The agent that maintains ci/github-workflows/*.yml cannot push files under
# .github/workflows/ -- GitHub rejects that without the `workflows` permission.
# So the corrected workflows live in ci/github-workflows/ and this script copies
# them into place using your credentials, which do have that permission.
#
# It also fixes the gradlew file mode, which is the actual cause of
# "./gradlew: Permission denied" (exit 126) on a fresh CI runner.
#
# Run it from a clone of the branch that HAS these files, while checked out on
# the branch you want to fix (normally master).

set -euo pipefail

say() { printf '\n\033[1m%s\033[0m\n' "$1"; }

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# The Kotlin import fixes apply to any checkout. The workflow copies need the
# corrected files, which only exist on the arena branch -- so if they are not
# here (you are on master), fetch them instead of refusing to run. That branch
# is where the fixes are maintained; nothing else about it is merged.
BRANCH=arena/01a01321-scottsx-commerce-os
WF_SRC=ci/github-workflows

# ALWAYS fetch the workflows from the maintaining branch, even if a copy of
# ci/github-workflows/ already exists in this checkout. master carries an OLDER
# snapshot of android-release.yml, and the previous version of this script saw
# that file, decided it was good enough, and installed the STALE workflow --
# silently discarding every fix made since. Fetch first; fall back to whatever
# is on disk only when the network or the branch is unavailable.
say "0. Fetching the corrected workflows from $BRANCH"
if git fetch -q origin "$BRANCH" 2>/dev/null; then
  TMP_WF="$(mktemp -d)"
  got=0
  for f in android-release.yml ci.yml; do
    if git cat-file -e "origin/$BRANCH:ci/github-workflows/$f" 2>/dev/null; then
      git show "origin/$BRANCH:ci/github-workflows/$f" > "$TMP_WF/$f"
      echo "   fetched $f"
      got=$((got + 1))
    fi
  done
  if [ "$got" -gt 0 ]; then
    WF_SRC="$TMP_WF"
  else
    echo "   ! branch has no workflow files - falling back to this checkout"
  fi
else
  echo "   ! could not fetch $BRANCH - falling back to this checkout"
fi

if [ ! -f "$WF_SRC/android-release.yml" ]; then
  echo "   ! no android-release.yml available - skipping the workflow copy."
  echo "   ! The Kotlin and AGP fixes below still apply."
  WF_SRC=""
fi

say "1. Making the Gradle wrapper executable in Git"
# THIS is the fix for exit 126. The mode is stored in Git itself, so it
# survives a fresh checkout on the runner -- no chmod step required.
#
# Note the path: the wrapper is in scottsx-android/, not the repo root.
if [ "$(git ls-files -s scottsx-android/gradlew | cut -d' ' -f1)" = "100755" ]; then
  echo "   already executable (100755) - nothing to do"
else
  git update-index --chmod=+x scottsx-android/gradlew
  echo "   scottsx-android/gradlew -> 100755"
fi
chmod +x scottsx-android/gradlew 2>/dev/null || true

say "2. Installing the corrected workflows"
if [ -n "$WF_SRC" ]; then
  mkdir -p .github/workflows
  for f in android-release.yml ci.yml; do
    if [ -f "$WF_SRC/$f" ]; then
      cp "$WF_SRC/$f" ".github/workflows/$f"
      echo "   .github/workflows/$f"
    fi
  done
else
  echo "   skipped (workflows unavailable)"
fi
echo
echo "   These fix three things the current workflows get wrong:"
echo "     - android-release.yml had no 'chmod +x ./gradlew'  (exit 126)"
echo "     - android-release.yml hard-failed when API_BASE_URL was unset;"
echo "       it now falls back to the deployed Render API with a warning"
echo "     - ci.yml's DATABASE_URL password had been replaced with literal"
echo "       '***', copied out of a masked log - that breaks the CI database"

say "2b. Copying the test tools the workflows run"
# The corrected ci.yml runs gates that only exist on the working branch. Copying
# the workflow without them gives:
#     ./tools/wiring-check.sh: No such file or directory   (exit 127)
# Each of these is self-contained -- Node built-ins and POSIX shell only -- so
# they run anywhere without extra dependencies.
if [ -n "$WF_SRC" ]; then
  copied=0
  for f in \
    scottsx-android/tools/wiring-check.sh \
    scottsx-android/tools/layout-check.mjs \
    scottsx-android/tools/compose-contract-check.mjs \
    scottsx-android/tools/res-check.sh \
    12_Backend/tests/firebase-auth.mjs \
    12_Backend/tests/production-safety.mjs \
    web/scripts/generate-sitemap.mjs
  do
    if git cat-file -e "origin/$BRANCH:$f" 2>/dev/null; then
      mkdir -p "$(dirname "$f")"
      git show "origin/$BRANCH:$f" > "$f"
      case "$f" in *.sh) chmod +x "$f"; git update-index --add --chmod=+x "$f" 2>/dev/null || true ;; esac
      echo "   + $f"
      copied=$((copied + 1))
    fi
  done
  echo "   $copied file(s) in place"
else
  echo "   skipped (branch unavailable)"
fi

say "3. Fixing the Kotlin compile errors"
# These three files use symbols they never import. The compiler reports:
#   Unresolved reference: ChatTurn / ChatTurnBubble   (RealAiChatScreen,
#                                                      SellerAIAssistantScreen)
#   Unresolved reference: Row                         (SupportScreen)
# The "Not enough information to infer type variable T" and "@Composable
# invocations can only happen from..." errors are cascades from those two, and
# clear on their own once the imports are present.
#
# Applied as targeted insertions rather than copying whole files, because this
# branch's screens have diverged from master in unrelated ways.
add_import() {  # file, import-line, anchor-line
  local file="$1" imp="$2" anchor="$3"
  [ -f "$file" ] || { echo "   ! missing: $file"; return; }
  if grep -qxF "$imp" "$file"; then
    echo "   = $(basename "$file"): already imports ${imp##*.}"
    return
  fi
  if ! grep -qxF "$anchor" "$file"; then
    echo "   ! $(basename "$file"): anchor not found, add by hand: $imp"
    return
  fi
  # Insert before the anchor so imports stay alphabetically sorted.
  awk -v imp="$imp" -v anc="$anchor" \
    '$0==anc && !done { print imp; done=1 } { print }' "$file" > "$file.tmp" \
    && mv "$file.tmp" "$file"
  echo "   + $(basename "$file"): ${imp##*.}"
}

S=scottsx-android/app/src/main/java/com/scottsx/app/ui/screens
add_import "$S/RealAiChatScreen.kt" \
  "import com.scottsx.app.ui.components.ChatTurn" \
  "import com.scottsx.app.ui.components.GradientHeader"
add_import "$S/RealAiChatScreen.kt" \
  "import com.scottsx.app.ui.components.ChatTurnBubble" \
  "import com.scottsx.app.ui.components.GradientHeader"
add_import "$S/SellerAIAssistantScreen.kt" \
  "import com.scottsx.app.ui.components.ChatTurn" \
  "import com.scottsx.app.ui.components.GradientHeader"
add_import "$S/SellerAIAssistantScreen.kt" \
  "import com.scottsx.app.ui.components.ChatTurnBubble" \
  "import com.scottsx.app.ui.components.GradientHeader"
add_import "$S/SupportScreen.kt" \
  "import androidx.compose.foundation.layout.Row" \
  "import androidx.compose.foundation.layout.Spacer"

say "3b. Bumping AGP to 8.6.0 (the compileSdk 35 minimum)"
# THE APK BUILD FAILURE.
#
# The release build died ~46s in, immediately after :app:preReleaseBuild, with
# no compiler output at all. That is :app:checkReleaseAarMetadata: the AndroidX
# artifacts this app depends on are built against API 35, and they refuse any
# consumer whose Android Gradle Plugin is older than 8.6.0. AGP 8.5.2 only
# WARNS about compileSdk 35 during configuration, then hard-fails in that
# metadata check -- before Kotlin runs, which is why no error line ever
# appeared in the log.
#
# AGP 8.6.0 requires Gradle >= 8.7 and the wrapper is already on 8.7, so this
# is a one-line change with no wrapper upgrade.
G=scottsx-android/build.gradle.kts
if [ -f "$G" ]; then
  if grep -q 'com.android.application") version "8.6.0"' "$G"; then
    echo "   already on AGP 8.6.0 - nothing to do"
  elif grep -q 'com.android.application") version "8.5.2"' "$G"; then
    sed -i.bak 's|id("com.android.application") version "8.5.2"|id("com.android.application") version "8.6.0"|' "$G"
    rm -f "$G.bak"
    echo "   AGP 8.5.2 -> 8.6.0"
  else
    echo "   ! unexpected AGP pin; check $G by hand"
    grep -n 'com.android.application' "$G" || true
  fi
else
  echo "   ! missing: $G"
fi

say "4. Staging"
git add -A scottsx-android 12_Backend/tests web/scripts
[ -n "$WF_SRC" ] && git add .github/workflows/android-release.yml .github/workflows/ci.yml
git status --short

cat <<'NEXT'

Done. Now commit and push:

    git commit -m "Fix CI: gradlew mode, release chmod, DATABASE_URL, missing Kotlin imports"
    git push

Then run the workflow:  Actions -> "Android release APK" -> Run workflow

The APK lands in that run's Artifacts as "scottsx-release-apk".
With no keystore secrets it is debug-signed: installable for testing,
not acceptable for the Play Store.
NEXT
