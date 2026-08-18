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
| `ci.yml` | every push + PR | Builds backend and web, runs all four suites (568 checks) against a real Postgres service container, and asserts the gazetteer asset reached `dist/`. Also builds a debug APK. |
| `android-release.yml` | manual, or a `v*` tag | Builds a signed release APK against your live HTTPS API and uploads it as an artifact. |

Both are valid YAML and were parsed and checked before committing. See
`DEPLOYMENT.md` for the secrets `android-release.yml` expects, or
`DEPLOY-STEPS.md` for the full click-by-click deployment walkthrough.
