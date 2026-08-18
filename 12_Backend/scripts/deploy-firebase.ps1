# ─────────────────────────────────────────────────────────────────────────────
# ScottsTechX — Deploy backend to Firebase (Windows / PowerShell)
#
#   ./scripts/deploy-firebase.ps1 [-DatabaseUrl "postgresql://user:pass@host/db"] [-SkipDeploy]
#
# What it does:
#   1. Reads values from 12_Backend/.env (already filled in) — or env vars.
#   2. Pushes each value to Firebase Secret Manager (functions:secrets:set).
#   3. Sets non-secret config params (ai.provider, ai.model, app.deeplink).
#   4. Deploys the Cloud Functions.
#
# Requirements: firebase CLI (npx firebase-tools) + `firebase login` once.
# DATABASE_URL is REQUIRED — embedded Postgres doesn't run on Cloud Functions.
# ─────────────────────────────────────────────────────────────────────────────
param(
  [string]$DatabaseUrl = "",
  [switch]$SkipDeploy
)
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

$firebase = "npx --yes firebase-tools"
if (Get-Command firebase -ErrorAction SilentlyContinue) { $firebase = "firebase" }

function Read-EnvValue([string]$key) {
  $line = Select-String -Path ".env" -Pattern "^$key=" -SimpleMatch -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($line) {
    $parts = $line.Line -split "=", 2
    if ($parts.Count -ge 2) { return $parts[1].Trim() }
  }
  return ""
}

$SECRETS = @(
  "DATABASE_URL", "JWT_SECRET", "LLM_API_KEY", "APIFREELLM_API_KEY",
  "NYLON_PAY_API_KEY", "NYLON_PAY_API_SECRET", "NYLON_PAY_WEBHOOK_SECRET",
  "GOOGLE_CLIENT_ID", "GOOGLE_API_KEY", "STRIPE_SECRET_KEY", "STRIPE_WEBHOOK_SECRET"
)

Write-Host "== ScottsTechX → Firebase (project scottstechx-52bab) =="

if ($DatabaseUrl) { $env:DATABASE_URL = $DatabaseUrl }

foreach ($name in $SECRETS) {
  $value = [Environment]::GetEnvironmentVariable($name)          # env override
  if (-not $value) { $value = Read-EnvValue $name }               # .env fallback

  if (-not $value) {
    if ($name -eq "DATABASE_URL") {
      Write-Host "!! DATABASE_URL is required (managed Postgres for Cloud Functions)."
      Write-Host "   Retry: .\scripts\deploy-firebase.ps1 -DatabaseUrl postgresql://user:pass@host/db"
    } else {
      Write-Host "   skip $name (not set — optional)"
    }
    continue
  }

  Write-Host "   setting secret $name ..."
  $value | & $firebase functions:secrets:set $name --project scottstechx-52bab --force
  if ($LASTEXITCODE -ne 0) { throw "Failed to set secret $name" }
}

Write-Host "   setting config params ..."
& $firebase functions:config:set ai.provider="openrouter" ai.model="meta-llama/llama-3.3-70b-instruct" app.deeplink="https://scottstechx-52bab.firebaseapp.com/__/auth/action" --project scottstechx-52bab

if (-not $SkipDeploy) {
  Write-Host "== Deploying Cloud Functions =="
  & $firebase deploy --only functions --project scottstechx-52bab
  Write-Host "Done. API base URL: https://europe-west1-scottstechx-52bab.cloudfunctions.net/api"
} else {
  Write-Host "Skipped deploy (-SkipDeploy). Run: npm run deploy"
}
