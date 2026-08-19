# Fix the APK build — one command

## Where things stand

Two separate bugs were stacked on top of each other. The first one hid the second.

**Bug 1 — AGP too old (FIXED, confirmed by your last run).**
The build died at 46s right after `:app:preReleaseBuild` with no error message at
all. `compileSdk 35` requires Android Gradle Plugin **8.6.0+**; the project pinned
**8.5.2**. AGP 8.5.2 only *warns* about compileSdk 35 during configuration, then
hard-fails in `:app:checkReleaseAarMetadata` — before Kotlin ever runs, which is
why nothing was printed.

Your last run proves the fix worked. It sailed through `checkReleaseAarMetadata`,
`processReleaseResources`, `mergeExtDexRelease` — **36 tasks, 2m19s** — instead of
dying at 46s.

**Bug 2 — a missing file (this is the remaining one).**
With the build finally reaching Kotlin, exactly one error appeared:

```
e: UiKit.kt:218:14 Unresolved reference: statusBarSpacer
```

`UiKit.kt` line 218 calls `.statusBarSpacer()`, but that extension is declared in
`ui/components/ScreenScaffold.kt` — **a file that was never copied to master.**
The call site was there without the definition. That file also declares
`topInset()`, `bottomInset()`, `navBarSpacer()` and `ScreenScaffold()`, which the
edge-to-edge (targetSdk 35) layout work needs.

## Run this

From a clone of the repo, on `master`:

```bash
git fetch origin arena/01a01321-scottsx-commerce-os
git show origin/arena/01a01321-scottsx-commerce-os:ci/apply-workflow-fixes.sh > ci/apply-workflow-fixes.sh
bash ci/apply-workflow-fixes.sh
git commit -m "Fix APK build: add missing ScreenScaffold.kt"
git push
```

Then: **Actions → "Android release APK" → Run workflow**.

The APK appears in that run's **Artifacts** as `scottsx-release-apk`. Without
keystore secrets it is debug-signed — fine for installing and testing, not
acceptable for the Play Store.

## Why every existing check missed this

- `kotlin-syntax-check.sh` has no Android SDK on its classpath, so an unresolved
  reference is indistinguishable from the normal Compose noise.
- The import checkers only read `import` lines. This call needed **no import** —
  `UiKit.kt` and `ScreenScaffold.kt` are in the same package, so the reference is
  resolved by package membership. The file just wasn't there.

New gate `scottsx-android/tools/orphan-symbol-check.mjs` closes that hole: it
flags any chained `.foo(` call matching no declaration anywhere in the app source
and not a known imported/framework symbol — the exact signature of "call site
copied without its defining file."

## Verified before shipping

- **Orphan gate falsified**: FAILS on master as-is (1 finding, correctly
  `UiKit.kt:218 .statusBarSpacer()`), PASSES once `ScreenScaffold.kt` is added,
  PASSES on the working branch. 56–59 files, zero false positives after
  whitelisting genuine OkHttp/Coil/NotificationCompat builders.
- **Script run against a real `master` clone**: adds `ScreenScaffold.kt`, AGP
  already at 8.6.0, correct workflow installed, all 7 invoked tool files present.
- **Run twice**: idempotent.
- **Fixed tree compiled** against a real `android-35/android.jar`: exactly one
  `statusBarSpacer` declaration, no duplicates, no syntax errors.

## Not verified

I still cannot produce an APK in my sandbox — `maven.google.com`, `dl.google.com`
and `services.gradle.org` are blocked, so the Compose/AndroidX artifacts can't be
downloaded. Bug 1 was confirmed by your run; bug 2 is confirmed by the compiler's
own message and by the gate reproducing and clearing it. If another error appears,
the full log is now uploaded as the **`build-log`** artifact and the error lines
are written to the run's **Summary** page — send me either one.
