# The APK builds. Now make it install.

## The build is fixed

Run `32306996100` **succeeded** and produced a **16.5 MB APK**. The two build
bugs (AGP 8.5.2 vs `compileSdk 35`, and the missing `ScreenScaffold.kt`) are
already on `master`. Nothing further is needed for the build itself.

## Why it says "There was a problem parsing the package"

There are two causes, and both are addressed below.

### 1. You are almost certainly installing the ZIP, not the APK

GitHub **always** wraps artifacts in a zip. The download is:

```
scottsx-release-apk.zip        <- this is what lands on your phone
└── app-release.apk            <- this is the thing to install
```

Tapping the `.zip` gives *exactly* "There was a problem parsing the package,"
because it is not a package. **Unzip it first, then install the `.apk` inside.**

On a phone: use any file manager (Files, RAR, ZArchiver) to extract the zip, then
tap the `.apk`. You will also need to allow "Install unknown apps" for whichever
app you install from.

### 2. minSdk was 30 — Android 11 and newer only

The same error appears when an APK's `minSdk` is higher than the device's Android
version: the package parser rejects it before installing. If your phone runs
Android 10 or older, that alone would explain it.

Nothing in the app actually needs API 30. I checked:

- every version-sensitive call is already guarded — notification channels behind
  API 26, `POST_NOTIFICATIONS` behind API 33
- no `java.time` / `java.nio.file` usage, so no desugaring needed
- no API-30-only calls (`windowInsetsController`, `setDecorFitsSystemWindows`,
  `WindowMetrics`) anywhere in the source
- dependency floor is 23 (firebase-bom 33.x)

So `minSdk` is now **24** — Android 7.0 and newer, instead of Android 11+.

## Run this

```bash
git fetch origin arena/01a01321-scottsx-commerce-os
git show origin/arena/01a01321-scottsx-commerce-os:ci/apply-workflow-fixes.sh > ci/apply-workflow-fixes.sh
bash ci/apply-workflow-fixes.sh
git commit -m "Install fix: minSdk 24, verify APK in CI"
git push
```

Then **Actions → "Android release APK" → Run workflow**, download the artifact,
**unzip it**, and install the `.apk`.

## The next run proves the APK is good before you download it

New step, **"Verify the APK is installable"**, runs before upload and hard-fails
on a bad package:

- `unzip -t` — the container is not corrupt
- `aapt2 dump badging` — Android's own parser can read the manifest; prints the
  package name, `minSdk` and `targetSdk`
- `apksigner verify` — the APK is genuinely signed (an unsigned APK will not
  install)

It also writes the package details, and the unzip-first instruction, to the run's
Summary page.

## Verified before shipping

- Script run against a real `master` clone: `minSdk 30 -> 24` applied, workflow
  installed, `ScreenScaffold.kt` and AGP 8.6.0 confirmed already present.
- Run twice — idempotent ("already minSdk 24 — nothing to do").
- Orphan-symbol gate passes on the fixed tree (57 files).
- The verify step's logic was tested locally, not assumed: `unzip -t` accepts a
  valid zip and rejects a corrupt one, and the `sed` that reads `sdkVersion:'NN'`
  parses real `aapt2 badging` output correctly (returns 24 and 30), with the
  warning firing only above 30.

## Not verified

I could not download and inspect your actual APK — GitHub serves artifacts from
`blob.core.windows.net`, which is blocked from my sandbox. So I could not confirm
first-hand *which* of the two causes hit you. If your phone runs Android 11+, it
was the zip; if it runs Android 10 or older, it was `minSdk`. The `minSdk` change
plus the CI verification covers both, and the next run will print the APK's real
`minSdk` and signature status to the Summary page.
