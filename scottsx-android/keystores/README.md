# Shared debug keystore (pinned)

`debug.keystore` is a **debug-only** signing key committed on purpose.

## Why it is committed

Google sign-in on Android only works when the (package name, SHA-1)
pair of the *running* build is registered as an OAuth "Android" client
in the Google Cloud project. Gradle otherwise generates a debug key per
machine under `~/.android/debug.keystore` — every CI runner produced a
different fingerprint, so Google rejected sign-in attempts with
`RESULT_CANCELED`, which the app reported as "Google sign-in cancelled".

Pinning one shared debug key makes **every** debug APK (CI artifacts,
local builds) carry the same signature, so the registration below is
done **once**. Its twin lives at
`scottsx-android-v2/keystores/debug.keystore` — both apps share the
package `com.scottsx.app` and therefore the same registration.

A debug key grants no privileges: it cannot publish to Play (release
keys are separate, git-ignored `*.jks`) and it unlocks nothing a real
attacker could not already do with their own phone.

## Fingerprints to register (one-time, in Google Cloud Console)

In [console.cloud.google.com](https://console.cloud.google.com) →
project **scottstechx-52bab** → *APIs & Services → Credentials* →
*Create OAuth client ID → Android* (or edit the existing Android client):

- Package name: `com.scottsx.app`
- SHA-1:
  ```
  9A:3C:C2:03:AF:DC:91:F7:A3:61:F0:D3:7D:A6:53:1C:8B:CD:82:84
  ```
- SHA-256 (Firebase also accepts it):
  ```
  20:91:97:C4:5B:AD:D9:4B:E3:0C:C5:EB:D5:49:BA:6E:3E:3B:37:51:19:8C:23:A8:CB:CA:F9:A9:23:04:2B:99
  ```

Verify locally at any time:

```bash
keytool -list -v -keystore keystores/debug.keystore \
    -storepass android -alias androiddebugkey | grep -E 'SHA1:|SHA256:'
```

The Gradle build also prints both lines (prefixed `GOOGLE SIGN-IN`) into
the build log, including CI.

Until a registration exists for a given fingerprint, sign-in from an APK
signed with a *different* key will fail with code 10 (`DEVELOPER_ERROR`)
and the app now says exactly that instead of "cancelled".

## Regenerating (release rotation or accidental leak concerns)

```bash
keytool -genkeypair -v -keystore keystores/debug.keystore \
    -storepass android -keypass android -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10950 \
    -dname "CN=Android Debug,O=Android,C=US"
cp keystores/debug.keystore ../scottsx-android-v2/keystores/debug.keystore
```

then update the SHA-1/SHA-256 above **and** the OAuth client registration.
