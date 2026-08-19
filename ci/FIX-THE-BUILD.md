# Get the APK building — Codespace instructions

You are on `master` in a Codespace. Copy-paste these blocks in order.

Every command below was executed against a real clone of `master` before being
written here, so the output you see should match.

---

## Step 1 — get the fix script

The script lives on the working branch, not on `master`. Pull just that one
file across:

```bash
git fetch origin arena/01a01321-scottsx-commerce-os
git checkout origin/arena/01a01321-scottsx-commerce-os -- ci/apply-workflow-fixes.sh
```

Nothing else from that branch comes with it.

---

## Step 2 — run it

```bash
bash ci/apply-workflow-fixes.sh
```

Expected output:

```
0. Fetching the corrected workflows from arena/01a01321-...
1. Making the Gradle wrapper executable in Git
   scottsx-android/gradlew -> 100755
2. Installing the corrected workflows
   .github/workflows/android-release.yml
   .github/workflows/ci.yml
3. Fixing the Kotlin compile errors
   + RealAiChatScreen.kt: ChatTurn
   + RealAiChatScreen.kt: ChatTurnBubble
   + SellerAIAssistantScreen.kt: ChatTurn
   + SellerAIAssistantScreen.kt: ChatTurnBubble
   + SupportScreen.kt: Row
4. Staging
```

Re-running it is safe — it reports `= already imports ...` and changes nothing.

---

## Step 3 — commit and push

```bash
git commit -m "Fix CI: gradlew mode, release chmod, DATABASE_URL, missing Kotlin imports"
git push
```

If the push is rejected because the workflow files need extra permission, see
**"If the push is rejected"** at the bottom.

---

## Step 4 — build the APK

On GitHub: **Actions → "Android release APK" → Run workflow → Run workflow**.

When it finishes, the APK is at the bottom of the run page under **Artifacts**,
named `scottsx-release-apk`. Download, unzip, install on your phone.

It is **debug-signed** because no keystore secret is configured. That installs
and runs fine for testing; it cannot go to the Play Store. Signing needs
`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and
`ANDROID_KEY_PASSWORD` as repository secrets.

---

## If you already ran this and hit `exit code 127`

`./tools/wiring-check.sh: No such file or directory` means you copied the
workflow before the script knew to bring its tools along. Just re-run it:

```bash
git checkout origin/arena/01a01321-scottsx-commerce-os -- ci/apply-workflow-fixes.sh
bash ci/apply-workflow-fixes.sh
git commit -m "Fix CI: copy the test tools the workflows run"
git push
```

Step **2b** now copies the seven files `master` was missing.

---

## What the script actually changes

| Change | Why |
|---|---|
| `scottsx-android/gradlew` mode `100644` → `100755` | The cause of `./gradlew: Permission denied` (exit 126). Stored in Git, so it survives a fresh runner checkout. |
| `chmod +x ./gradlew` added to `android-release.yml` | Belt and braces. The earlier fix on `master` added this to `ci.yml` (the *debug* build) — the release workflow never got it, which is why the error kept coming back. |
| `API_BASE_URL` no longer required | The workflow used to hard-fail when the secret was unset. It now falls back to `https://scottstechx-api.onrender.com/api/v1` and logs a warning. |
| `ci.yml` `DATABASE_URL` restored | The password had been replaced with a literal `***`, copied out of a masked log. That breaks the CI database. |
| 7 test tools copied across | `master` never had `wiring-check.sh`, `layout-check.mjs`, `compose-contract-check.mjs`, `res-check.sh`, `firebase-auth.mjs`, `production-safety.mjs` or `generate-sitemap.mjs`. The workflow runs them, hence exit 127. |
| 5 missing Kotlin imports | `ChatTurn` / `ChatTurnBubble` in `RealAiChatScreen` + `SellerAIAssistantScreen`, and `Row` in `SupportScreen`. |

The 17 compile errors in the last run come from just those 5 imports. The
`Not enough information to infer type variable T` and `@Composable invocations
can only happen from...` messages are cascades — they clear on their own once
the imports resolve.

---

## If the push is rejected

Some tokens cannot write `.github/workflows/`. If that happens:

```bash
git reset --soft HEAD~1
git restore --staged .github
git checkout -- .github
git commit -m "Fix CI: gradlew mode and missing Kotlin imports"
git push
```

(`git checkout -- .github` puts the workflow files back to master's committed
versions. Do **not** `rm -rf .github` — those files are tracked on `master`,
and deleting them would commit their removal.)

That pushes the Kotlin and `gradlew` fixes, which alone clear both the
permission error and the compile errors. Then edit
`.github/workflows/android-release.yml` in the GitHub web UI and add
`chmod +x ./gradlew && ` in front of `./gradlew --no-daemon assembleRelease`.

---

## If the next run shows new compile errors

Likely, and not a setback. The Kotlin compiler stops after the first batch of
failing files, so a second wave can appear behind the first.

Paste the new `e: ...` lines into the chat. They can now be checked against a
real Kotlin 1.9.24 compiler rather than by inspection.
