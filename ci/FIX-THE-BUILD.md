# Fix the APK build — one command

## What was actually wrong

The release build failed after ~46 seconds, every time, with **no error message**.
The log always stopped at the same place:

```
> Task :app:preBuild UP-TO-DATE
> Task :app:preReleaseBuild UP-TO-DATE
```

…and then nothing. No `e:` line, no `FAILURE:` block.

The task that runs at exactly that point is **`:app:checkReleaseAarMetadata`**.

**`compileSdk 35` requires Android Gradle Plugin 8.6.0 or newer. This project
pinned AGP 8.5.2.**

On 8.5.2, Gradle's configuration phase only *warns* about `compileSdk 35`
(that warning is visible in the run #6 log) and keeps going. The build then
hard-fails in the AAR metadata check, because the AndroidX libraries this app
uses are compiled against API 35 and refuse any consumer on an AGP older than
8.6.0. That check runs **before** Kotlin compiles — which is why no compiler
error was ever printed, and why the earlier guesses (missing icons, duplicate
imports, a non-exhaustive `when`) were all wrong. They were guesses at an
invisible log; this one is pinned down by *where the log stops*.

AGP 8.6.0 needs Gradle ≥ 8.7. The wrapper is already on `gradle-8.7-all`, so
this is a **one-line change with no wrapper upgrade**.

## Run this

From a clone of the repo, on `master`:

```bash
git fetch origin arena/01a01321-scottsx-commerce-os
git show origin/arena/01a01321-scottsx-commerce-os:ci/apply-workflow-fixes.sh > ci/apply-workflow-fixes.sh
bash ci/apply-workflow-fixes.sh
git commit -m "Fix APK build: AGP 8.6.0 for compileSdk 35"
git push
```

Then: **Actions → "Android release APK" → Run workflow**.

The APK appears in that run's **Artifacts**, as `scottsx-release-apk`.
Without keystore secrets it is debug-signed — fine for installing and testing,
not acceptable for the Play Store.

## Heads-up: the script used to install a stale workflow

The previous version only fetched `ci/github-workflows/` when that folder was
missing. `master` carries an *older* copy of `android-release.yml`, so the check
passed, the fetch was skipped, and the outdated workflow was installed —
throwing away the fixes. It now always fetches from the branch. This was found
by running the script against a real `master` clone, not by reading it.

## If it still fails, the error can no longer hide

Log truncation is why three rounds were lost to guessing. The error is now
captured three ways that cannot be truncated:

1. **`build-log` artifact** — the complete Gradle log, downloadable from the run
   page (uploaded even when the build fails).
2. **Job Summary** — the error lines and the last 80 lines are written to the
   run's summary page, which is never truncated.
3. **End of the step log** — the tail is echoed last, so it survives at the
   bottom.

Send me the **Summary page** text or the `build-log` artifact and the next fix
is exact rather than inferred.

## Verified before shipping

- Script run against a fresh `master` clone: AGP `8.5.2 → 8.6.0`, correct
  workflow installed (byte-identical to the fixed source), all 7 invoked tool
  files present.
- Run twice: idempotent, no double-edit.
- Failure diagnostics tested against a simulated Gradle failure that buries the
  real error under 500 lines of task output — the error still surfaced, and the
  step still exited 1.

## Not verified

A full APK cannot be built in my sandbox: `maven.google.com`, `dl.google.com`
and `services.gradle.org` are all blocked, so the Compose/AndroidX artifacts
can't be downloaded. I confirmed the AGP/`compileSdk` requirement from Google's
own compatibility matrix and matched it to the exact task where your build
stops. If a *different* error appears after this, it will now be legible.
