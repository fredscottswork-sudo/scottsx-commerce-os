# Get a test APK

Your dispatched build (run `32141473269`) failed at **Build release APK** with
**exit code 126**. That code means "command found but not executable": on
`master`, `scottsx-android/gradlew` is committed as mode `100644`, so the
runner cannot run `./gradlew`.

Two things must happen. Only the first needs your hands.

---

## Step 1 — fix the executable bit (one command, ~10 seconds)

I cannot push this myself: the workflow files and the file mode both sit behind
a `workflows` permission my token does not have. On your machine:

```bash
git checkout master
git pull
git update-index --chmod=+x scottsx-android/gradlew
git commit -m "Make gradlew executable so CI can run it"
git push
```

Verify it worked — the mode must read `100755`:

```bash
git ls-tree HEAD scottsx-android/gradlew
```

## Step 2 — refresh the workflows

The activated copies in `.github/workflows/` are the older versions. The
updated ones are in `ci/github-workflows/` on this branch and add:

- `chmod +x ./gradlew` before every Gradle call, so this cannot recur even if
  the mode is lost again (Windows checkouts drop it routinely)
- an `apiBaseUrl` input on CI, so the test APK can point at a live backend
- artifact renamed `scottsx-test-apk`, kept for 30 days

```bash
git checkout master
cp ci/github-workflows/ci.yml .github/workflows/ci.yml
cp ci/github-workflows/android-release.yml .github/workflows/android-release.yml
git commit -am "Update workflows: chmod gradlew, allow apiBaseUrl for the test APK"
git push
```

---

## Then build it

### The easy way — a test APK, no secrets needed

GitHub → **Actions** → **CI** → **Run workflow**.

Leave `apiBaseUrl` blank and it builds against the emulator loopback
(`http://10.0.2.2:3001/api/v1`) — fine for an emulator on your PC, useless on a
real phone.

For a **real handset**, put your backend's address in the `apiBaseUrl` box. It
must end in `/api/v1`:

- backend on your laptop, phone on the same Wi-Fi:
  `http://192.168.1.20:3001/api/v1` (use your PC's actual LAN IP —
  `ip addr` on Linux, `ipconfig` on Windows)
- backend deployed: `https://your-api.onrender.com/api/v1`

Download the APK from the run's **Artifacts** section → `scottsx-test-apk`.

Cleartext `http://` works here because I added a debug-only network security
config. Release builds remain HTTPS-only.

### The signed way — a release APK

GitHub → **Actions** → **Android release APK** → **Run workflow**.

This one requires `API_BASE_URL` (an **https://** URL ending `/api/v1`) as a
repository secret or a workflow input. Without the four keystore secrets the
APK is debug-signed: installable for testing, not acceptable for the Play
Store.

---

## Installing it

1. Copy the `.apk` to the phone (USB, Drive, or email it to yourself).
2. Tap it. Android will ask you to allow installs from that app — accept.
3. If it refuses with "App not installed", uninstall any previous ScottsTechX
   build first; debug and release signatures cannot replace one another.

## If sign-in fails after installing

Almost always the API URL. Check, in order:

- **Does it end in `/api/v1`?** The workflow rejects a URL that does not, but a
  URL baked in by other means may not have been checked.
- **Is the phone on the same Wi-Fi as the backend?** Mobile data cannot reach a
  `192.168.x.x` address.
- **Is the backend listening beyond localhost?** A server bound to `127.0.0.1`
  is unreachable from the phone. It must bind `0.0.0.0`.
- **Is your PC's firewall blocking port 3001?** This is the usual culprit on
  Windows.

Quick check from the phone's browser — open
`http://<your-pc-ip>:3001/api/v1/geo/status`. If that shows JSON, the app can
reach it too.

---

## One thing I could not verify

The Android app has **never been compiled** — this sandbox has no Android SDK
and cannot reach `dl.google.com` or `maven.google.com`, so Gradle cannot
resolve a single AndroidX artifact. What I *have* run over all 56 Kotlin files
is the real Kotlin compiler frontend, which catches syntax errors, unbalanced
braces, bad `when` branches and duplicate declarations, plus a contract test
asserting every endpoint and JSON field the Kotlin models read against the
live backend.

That is not the same as a green build. Expect the first CI run to surface
ordinary compile errors — a missing import, an unresolved symbol. Paste the
failing log and I will fix them.
