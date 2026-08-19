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

if [ ! -f ci/github-workflows/android-release.yml ]; then
  echo "error: ci/github-workflows/android-release.yml not found." >&2
  echo "Run this from a checkout that contains the corrected workflows." >&2
  exit 1
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
mkdir -p .github/workflows
cp ci/github-workflows/android-release.yml .github/workflows/android-release.yml
cp ci/github-workflows/ci.yml             .github/workflows/ci.yml
echo "   .github/workflows/android-release.yml"
echo "   .github/workflows/ci.yml"
echo
echo "   These fix three things the current workflows get wrong:"
echo "     - android-release.yml had no 'chmod +x ./gradlew'  (exit 126)"
echo "     - android-release.yml hard-failed when API_BASE_URL was unset;"
echo "       it now falls back to the deployed Render API with a warning"
echo "     - ci.yml's DATABASE_URL password had been replaced with literal"
echo "       '***', copied out of a masked log - that breaks the CI database"

say "3. Staging"
git add scottsx-android/gradlew .github/workflows/android-release.yml .github/workflows/ci.yml
git status --short

cat <<'NEXT'

Done. Now commit and push:

    git commit -m "Fix CI: executable gradlew, release-APK chmod, unmasked DATABASE_URL"
    git push

Then run the workflow:  Actions -> "Android release APK" -> Run workflow

The APK lands in that run's Artifacts as "scottsx-release-apk".
With no keystore secrets it is debug-signed: installable for testing,
not acceptable for the Play Store.
NEXT
