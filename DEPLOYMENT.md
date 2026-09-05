# Deploying ScottsTechX

Three artifacts, three targets:

| Piece | Goes to | Why |
|---|---|---|
| `12_Backend` (Fastify + Postgres) | **Render** (or Railway / Fly.io) | Needs a long-lived Node process and a real Postgres |
| `web` (React + Vite, static) | **Cloudflare Pages** (or Netlify / Vercel) | Pure static output; free tier is plenty |
| `scottsx-android` (APK) | **GitHub Actions** | Wired — see `ci/README.md` for the one-line activation step |

The backend must be deployed **first**: the web app and the APK both need its
public URL baked in at build time.

> **Fastest path — `render.yaml` in the repo root.** Skip the manual backend and
> web setup below: **Render Dashboard → New → Blueprint → select this repo**.
> The blueprint creates the Postgres database, the API and the static site, and
> wires `VITE_API_URL` and `PUBLIC_WEB_URL` between the two services itself.
> Render prompts for the secrets marked `sync: false` (admin password, SMTP,
> AI and payment keys) and generates `JWT_SECRET` for you. Sections 1 and 2
> below remain the manual route, and are still worth reading for *what* each
> setting does.

---

## What is missing before you can deploy

Ordered by how hard they block you.

### Blocking — nothing deploys correctly without these

1. **A managed Postgres.** The repo boots an *embedded* Postgres for local dev
   only. Set `DATABASE_URL` in production and that whole path is skipped
   (verified). Free options: [Neon](https://neon.tech),
   [Supabase](https://supabase.com), or Render's own Postgres.
2. **A real `JWT_SECRET`.** It currently defaults to `dev-secret-change-me`
   (`12_Backend/src/auth.ts`). Anyone who reads this public repo can forge an
   admin token against a deployment that keeps the default. Generate one:
   ```bash
   node -e "console.log(require('crypto').randomBytes(48).toString('base64url'))"
   ```
3. **A decision about seeding.** `SEED_DATABASE` and `CREATE_ADMIN` default to
   **on**, so a fresh production database will auto-create 6 fake sellers, 24
   fake products. The admin account is `scottstechx@gmail.com` (override with
   `ADMIN_EMAILS`); it has no password and signs in with the normal email
   code / Google flow, so there is nothing to rotate. Any other row marked
   admin is demoted at boot. For a real launch set `SEED_DATABASE=false`.

### Blocking for the APK

4. **A release keystore.** Without one the APK is debug-signed: installable for
   testing, rejected by the Play Store. Create it once and keep it safe — losing
   it means you can never update the app:
   ```bash
   keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 \
           -validity 10000 -alias scottsx
   base64 -w0 release.jks   # paste into the ANDROID_KEYSTORE_BASE64 secret
   ```
5. **An HTTPS API URL.** `targetSdk 35` blocks cleartext HTTP to arbitrary
   hosts, so `http://…` will fail on a real phone even though it works in the
   emulator. The release workflow rejects a non-HTTPS URL rather than shipping
   a broken APK.

### Not blocking — features stay dormant until supplied

| Missing | Effect while absent |
|---|---|
| `12_Backend/secrets/firebase-admin-key.json` | Phone push never sends. In-app + web notifications still work and are stored. |
| `LLM_API_KEY` | AI falls back to the local catalogue-grounded engine. Search/agents still work. |
| `GOOGLE_CLIENT_ID` | Email/password login still works; Google Sign-In returns 401. |
| Authorised JS origin in Google Cloud Console | Google button renders but rejects your deployed domain. Add your web origin there. |

---

## 1. Backend → Render

Render is the least fiddly host for this shape of app (persistent Node process,
one Postgres, no Docker needed).

1. Create the database first: **New > Postgres**. Copy the *internal*
   connection string.
2. **New > Web Service**, point it at this repo, then set:

   | Field | Value |
   |---|---|
   | Root directory | `12_Backend` |
   | Build command | `npm ci && npm run build` |
   | Start command | `npm start` |
   | Health check path | `/api/v1/geo/status` |

3. Environment variables:

   ```
   DATABASE_URL=<the Postgres connection string>
   JWT_SECRET=<the generated secret>
   NODE_ENV=production
   SEED_DATABASE=false      # true only if you want the demo catalogue
   ADMIN_EMAIL=you@yourdomain.com
   ADMIN_PASSWORD=<a strong password>
   ```

   `PORT` is injected by Render and already respected by `src/server.ts`.

4. Deploy, then confirm:
   ```bash
   curl https://<your-api>.onrender.com/api/v1/geo/status
   # {"ready":true,"source":"offline-gazetteer","coverage":"global"}
   ```
   `ready:true` matters: it proves the 5 MB offline gazetteer shipped. If it
   says `false`, the build step did not run `scripts/copy-assets.mjs`.

Migrations run automatically on boot (14 of them, tracked in
`schema_migrations`, idempotent) — verified against a cold empty database.

> **Note on Render's free tier:** the service sleeps after ~15 minutes idle and
> takes ~30 s to wake. Fine for testing, visible to real users. Railway and
> Fly.io behave similarly on free plans.

### Firebase Functions is also wired, but read this first

`firebase.json` and `src/firebase-entry.ts` are already set up, so
`npm run deploy` works. Two caveats: Cloud Functions still needs an external
`DATABASE_URL` (there is no Firebase SQL), and it requires the **Blaze**
(pay-as-you-go) plan. Render is simpler unless you are already on Firebase.

---

## 2. Web → Cloudflare Pages

1. **Workers & Pages > Create > Pages > Connect to Git**, pick this repo.
2. Build settings:

   | Field | Value |
   |---|---|
   | Root directory | `web` |
   | Build command | `npm ci && npm run build` |
   | Output directory | `dist` |

3. Environment variables (build-time — Vite inlines them, so a change needs a
   rebuild, and **nothing secret** may go here):

   ```
   VITE_API_URL=https://<your-api>.onrender.com
   VITE_GOOGLE_CLIENT_ID=<your Google client id>   # optional
   ```

   Note there is no `/api/v1` suffix — the client appends it.

4. SPA routing: add a `web/public/_redirects` containing
   `/*  /index.html  200`, otherwise a hard refresh on `/nearby` 404s.
   (Netlify uses the same file; Vercel needs a `vercel.json` rewrite.)

CORS is already `origin: '*'` on the backend, so no extra configuration is
needed. Tighten it to your domain before a real launch.

---

## 3. APK → GitHub Actions

`ci/github-workflows/android-release.yml` builds it — **move it to
`.github/workflows/` first**, see `ci/README.md` (a one-line `git mv`; the push
token used to build this branch is not allowed to write workflow files).
Then add these repository secrets
under **Settings > Secrets and variables > Actions**:

| Secret | Value |
|---|---|
| `API_BASE_URL` | `https://<your-api>.onrender.com/api/v1` (must end `/api/v1`) |
| `ANDROID_KEYSTORE_BASE64` | output of `base64 -w0 release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | e.g. `scottsx` |
| `ANDROID_KEY_PASSWORD` | key password |

Then **Actions > Android release APK > Run workflow**, or push a `v*` tag.
Download the APK from the run's Artifacts section.

The API URL is no longer hardcoded in Kotlin: `V2Client` reads
`BuildConfig.API_BASE_URL`, set from `app/build.gradle.kts`. Build locally
against any host with:

```bash
cd scottsx-android
./gradlew assembleRelease -PapiBaseUrl=https://your-api.example.com/api/v1
```

The default (no flag) is `http://10.0.2.2:3001/api/v1` — the emulator's route to
your development machine.

> **First Gradle build will likely fail with type errors.** The Android app has
> never been compiled: there is no Android SDK in the environment it was written
> in. The Kotlin compiler frontend runs over all 56 files and the model parsers
> are executed against real captured API JSON, which catches syntax, structural
> and parsing bugs — but not every type mismatch. Expect a short fix-up pass on
> the first real build. See `scottsx-android/tools/README.md`.

---

## 4. Order of operations

```
1. Provision Postgres           -> DATABASE_URL
2. Deploy backend               -> https://<api>
3. curl /api/v1/geo/status      -> ready:true
4. Deploy web with VITE_API_URL -> https://<web>
5. Add the web origin to Google Cloud Console (if using Google Sign-In)
6. Set API_BASE_URL + keystore secrets -> run the APK workflow
```

## CI

`ci/github-workflows/ci.yml` (activate it as described in `ci/README.md`) runs
on every push: builds backend and web, then runs
all four suites (**568 checks**) against a real Postgres service container — no
mocks. It also asserts `dist/geo/gazetteer.bin` exists, because a missing
gazetteer is invisible at build time and only shows up as 503s from the geo
endpoints in production.
