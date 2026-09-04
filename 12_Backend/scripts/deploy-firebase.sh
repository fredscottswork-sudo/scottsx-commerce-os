#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# ScottsTechX — Deploy backend to Firebase (macOS / Linux / CI)
#
#   ./scripts/deploy-firebase.sh [postgresql://user:pass@host/db]
#
# Reads values from 12_Backend/.env (or env vars), pushes them to Firebase
# Secret Manager, sets config params, and deploys the Cloud Functions.
# Requires: firebase CLI + `firebase login` once.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail
cd "$(dirname "$0")/.."

FIREBASE="npx --yes firebase-tools"
command -v firebase >/dev/null 2>&1 && FIREBASE="firebase"
PROJECT="scottstechx-52bab"
DB_URL="${1:-${DATABASE_URL:-}}"

read_env() {
  local key="$1"
  if [[ -f .env ]]; then
    sed -n "s/^${key}=//p" .env | head -1
  fi
}

SECRETS=(DATABASE_URL JWT_SECRET LLM_API_KEY APIFREELLM_API_KEY \
  GOOGLE_CLIENT_ID GOOGLE_API_KEY)

echo "== ScottsTechX → Firebase (${PROJECT}) =="

for name in "${SECRETS[@]}"; do
  value="${!name:-}"            # env override
  if [[ -z "$value" ]]; then
    value="$(read_env "$name")" # .env fallback
  fi
  if [[ -z "$value" ]]; then
    if [[ "$name" == "DATABASE_URL" ]]; then
      echo "!! DATABASE_URL is required (managed Postgres). Pass it as \$1 or set DATABASE_URL." >&2
    else
      echo "   skip ${name} (not set — optional)"
    fi
    continue
  fi
  echo "   setting secret ${name} ..."
  printf '%s' "$value" | $FIREBASE functions:secrets:set "$name" --project "$PROJECT" --force
done

$FIREBASE functions:config:set ai.provider="openrouter" ai.model="meta-llama/llama-3.3-70b-instruct" \
  app.deeplink="https://${PROJECT}.firebaseapp.com/__/auth/action" --project "$PROJECT"

echo "== Deploying Cloud Functions =="
$FIREBASE deploy --only functions --project "$PROJECT"
echo "Done. API base URL: https://europe-west1-${PROJECT}.cloudfunctions.net/api"
