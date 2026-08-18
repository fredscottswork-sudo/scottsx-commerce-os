# Deploying ScottsTechX

> **Want a click-by-click walkthrough instead?** See **[DEPLOY-STEPS.md](DEPLOY-STEPS.md)**
> — the same deployment as numbered steps with exact form fields, verification
> commands and a troubleshooting table. This file is the reference version.

Three artifacts, three targets:

| Piece | Goes to | Why |
|---|---|---|
| `12_Backend` (Fastify + Postgres) | **Render** (or Railway / Fly.io) | Needs a long-lived Node process and a real Postgres |
| `web` (React + Vite, static) | **Cloudflare Pages** (or Netlify / Vercel) | Pure static output; free tier is plenty |
| `scottsx-android` (APK) | **GitHub Actions** | Wired — see `ci/README.md` for the one-line activation step |

The backend must be deployed **first**: the web app and the APK both need its
public URL baked in at build time.

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
   fake products, and an admin with the *published* password `Admin123!`.
   For a real launch set `SEED_DATABASE=false` and a strong `ADMIN_PASSWORD`.
   For a demo, leave seeding on — but still change the admin password.

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
| `NYLON_PAY_API_KEY` / `_SECRET` | `POST /orders/checkout` stays **503**. Cash-on-delivery checkout is unaffected — it is the only buy path today, on both web and Android. |
| `GOOGLE_CLIENT_ID` | Email/password login still works; Google Sign-In returns 401. |
| Authorised JS origin in Google Cloud Console | Google button renders but rejects your deployed domain. Add your web origin there. |

---

## 0. Fastest route: the Blueprint

`render.yaml` at the repo root defines the database, the API and the website
together. In the Render dashboard: **New → Blueprint**, connect the repo, set
**Branch** to `arena/01a01321-scottsx-commerce-os`, and leave **Blueprint Path**
blank (it defaults to `render.yaml` at the root). Render prompts for
`ADMIN_EMAIL` and `ADMIN_PASSWORD`; `JWT_SECRET`, `DATABASE_URL` and the site's
`VITE_API_URL` are wired automatically.

Caveat: it requests free Postgres, which **Render deletes after 30 days**.
Switch `plan: free` to `plan: basic-256mb`, or drop the `databases:` block and
use Neon, before you put real data in it.

The sections below cover the same deployment done by hand.

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
   PUBLIC_WEB_URL=https://<your-web-host>   # no trailing slash
   ```

   `PUBLIC_WEB_URL` is the address of the **web app**, not this API. It is what
   `/sitemap.xml` uses to build absolute URLs. Leave it unset and the sitemap
   returns 503 rather than publishing links to a guessed domain; `robots.txt`
   still works, it just omits the `Sitemap:` line. Set it to your custom domain
   once you have one, since that is the origin Google should index.

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

## Search engines

The API serves both crawler files at the server root:

| URL | Purpose |
|---|---|
| `/robots.txt` | Crawl policy. Blocks dashboards, cart, inbox and the infinite `/search?` filter space; allows the public catalogue. |
| `/sitemap.xml` | Every publicly reachable URL, generated live from the database. |

The sitemap is generated per request rather than baked into the web build,
because the URLs worth indexing are product and storefront pages and those
change whenever an admin approves a listing. A file written at build time would
list products still awaiting review and keep advertising ones later suspended.

It contains only what a signed-out visitor can actually open: the public
routes, **approved** products, storefronts for sellers with at least one live
product, and the CMS pages. Listing a page that 404s wastes crawl budget and is
reported as an error in Search Console.

Both files live on the API origin. If you want them on the web domain instead
(usually what you want, since that is the origin you verify), proxy them —
e.g. on Cloudflare Pages or Netlify add a rewrite from `/sitemap.xml` and
`/robots.txt` to the API host. Otherwise submit the API URL directly in Search
Console.

See `SEARCH-CONSOLE.md` for ownership verification, including the Vercel
deployment-protection setting that currently blocks it.

## Product photos (image uploads)

Sellers upload photos from the phone camera/gallery or the web file picker; no
public image URL is needed any more (pasting a link still works).

Storage is chosen automatically at runtime:

| Firebase service account present | Where bytes go |
|---|---|
| yes | Firebase Storage |
| no  | Postgres `uploaded_images.data` (`bytea`) |

**Do not** switch this to local disk. Render and Cloud Run filesystems are
ephemeral, so every listing photo would 404 after the next deploy.

Limits enforced server-side (declared MIME types are never trusted — magic
bytes are sniffed and real dimensions read from the header):

- 3 MB hard limit, 900 KB soft warning (`oversized: true` in the response)
- JPEG / PNG / WebP / GIF only, minimum edge 16 px, max 8 photos per product
- Identical bytes from the same seller de-duplicate to one row

Served from `GET /api/v1/uploads/images/:id` — public, immutable, ETag +
304. The stored value is the API-relative path, so the same database row works
on localhost, a preview host and production; the web client and the Android
client each expand it to an absolute URL.

If you later move to Firebase Storage, existing Postgres-backed URLs keep
working; only new uploads go to the bucket.

## CI

`ci/github-workflows/ci.yml` (activate it as described in `ci/README.md`) runs
on every push: builds backend and web, then runs
all four suites (**568 checks**) against a real Postgres service container — no
mocks. It also asserts `dist/geo/gazetteer.bin` exists, because a missing
gazetteer is invisible at build time and only shows up as 503s from the geo
endpoints in production.
