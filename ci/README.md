# CI workflows — one manual step required

These two files are ready to use but live here instead of `.github/workflows/`.

**Why:** the GitHub App used to push this branch does not hold the `workflows`
permission, so any push that creates or edits a file under `.github/workflows/`
is rejected outright:

```
! [remote rejected] refusing to allow a GitHub App to create or update
  workflow `.github/workflows/ci.yml` without `workflows` permission
```

Rather than drop the workflows, they are committed here. Moving them is a
one-line step you run yourself:

```bash
mkdir -p .github/workflows
git mv ci/github-workflows/ci.yml             .github/workflows/ci.yml
git mv ci/github-workflows/android-release.yml .github/workflows/android-release.yml
git commit -m "Enable CI workflows"
git push
```

Do that from your own machine (a normal user push, not the app) and both
workflows activate immediately. You can also paste them straight into the
GitHub UI via **Actions > New workflow > set up a workflow yourself**.

## What each one does

| File | Trigger | Purpose |
|---|---|---|
| `ci.yml` | every push + PR | Builds backend and web, runs all six suites against a real Postgres service container, and asserts the gazetteer asset reached `dist/`. Also builds a debug APK. |
| `android-release.yml` | manual, or a `v*` tag | Builds a release APK against your live HTTPS API and uploads it as an artifact. |

## If the release APK build failed with "No API base URL"

That was this error:

```
Error: No API base URL. Set the API_BASE_URL secret or pass it to the workflow.
```

The workflow refused to build unless an `API_BASE_URL` secret existed, so the
very first run could never succeed — you had to know to create a secret before
pressing the button. **Fixed:** it now falls back to the deployed API
(`https://scottstechx-api.onrender.com/api/v1`) and prints a warning naming the
URL it used.

Copy the updated `android-release.yml` across (the `git mv` above) and re-run.
No secret is required to get an APK.

Set `API_BASE_URL` anyway once you have a custom API domain — the fallback is a
convenience, not the right long-term answer. Two guards remain and still fail
the build: the URL must be `https://` (a shipped app cannot use cleartext) and
must end in `/api/v1`.

### The APK will be debug-signed

Without the four `ANDROID_KEYSTORE_*` secrets the build still succeeds, but the
APK is debug-signed: installable for testing, **not** acceptable for the Play
Store. The run logs a warning saying so.

Both were structurally checked before committing (indentation, step depth,
tabs, `run:` block nesting), and the URL-resolution logic in
`android-release.yml` was executed directly against every case: no secret, a
secret, a manual input overriding the secret, a cleartext URL, and one missing
`/api/v1`. See
`DEPLOYMENT.md` for the secrets `android-release.yml` expects, or
`DEPLOY-STEPS.md` for the full click-by-click deployment walkthrough.
