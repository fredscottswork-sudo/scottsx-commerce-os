# ScottsTechX — Commerce OS

**Ugandan e-commerce marketplace** — Android (Kotlin + Jetpack Compose) app + Fastify (Node.js + TypeScript) backend.
Founder: **Kato Fred, Ugandan cybersecurity analyst, web dev and software dev.**

```
ScottsTechX/
├── 12_Backend/        Fastify + TypeScript + embedded PostgreSQL (port 5433) + API on :3001
│                     (+ Firebase Cloud Functions entry: src/firebase-entry.ts)
├── scottsx-android/   Android app (Kotlin 1.9 / Compose BOM 2024.06.00 / minSdk 30 / targetSdk 35)
├── web/               React + Vite web app — SAME backend, role dashboards (buyer/seller/admin)
└── firebase.json      Functions + Hosting + emulators config (.firebaserc → scottstechx-52bab)
```

**One backend, one database — three clients:** the Android app and the web app are both
clients of the same Fastify + Postgres API and see the same real-time data. The web app adds
a platform **admin** role (only `scottstechx@gmail.com`, signs in with the normal email code / Google) with user management and
product moderation endpoints (`/api/v1/admin/*`, admin-only, backend-enforced).

---

## 1. Quickstart — backend (runs anywhere with Node 20+)

```bash
cd 12_Backend
npm install                 # pulls embedded-postgres linux binaries automatically
npm run dev                 # boots embedded PG on 127.0.0.1:5433 + API on 0.0.0.0:3001
```

On first boot the server:
1. initialises an embedded PostgreSQL 18 cluster in `.pgdata/` (user `app` / password `app`)
2. creates the `scottstechx` database
3. runs `migrations/*.sql` in order (tracked in `schema_migrations`)
4. auto-seeds **6 sellers + 24 products** when the products table is empty
   (or run manually anytime: `node seed_marketplace.mjs`)

Smoke-test:

```bash
curl http://127.0.0.1:3001/healthz                 # {"ok":true}
curl http://127.0.0.1:3001/api/v1/products         # 24 products with Unsplash imageUrl
npm run smoke                                      # 6/6 checks
```

### Environment (`12_Backend/.env`)

| Var | Purpose |
|---|---|
| `PORT` | API port (3001) |
| `DATABASE_URL` | Leave empty for embedded PG; set to a managed Postgres to skip it |
| `PG_DATA_DIR` | Embedded cluster directory (`.pgdata`) |
| `JWT_SECRET` | HS256 signing secret — **change in production** |
| `APP_DEEP_LINK` | Firebase email-verification deep link |
| `LLM_API_KEY` | OpenRouter key for `/ai/v2/ask` (without any key the AI returns a deterministic offline fallback) |
| `AI_MODEL` | Default `meta-llama/llama-3.3-70b-instruct` |
| `AI_PROVIDER` | `openrouter` (default) or `apifreellm` |
| `APIFREELLM_API_KEY` | apifreellm key — free tier blocks datacenter/cloud IPs |
| `GOOGLE_CLIENT_ID` | Validates Google idTokens |

### Firebase ✅ configured

Your service account is installed at `12_Backend/secrets/firebase-admin-key.json` (git-ignored).
The full Firebase path is **verified end-to-end against the real project**:

```bash
node firebase_e2e_test.mjs    # 8 checks: real user → sign-in → verification email →
                              # unverified-blocked upgrade → Admin-SDK verify → upgrade → cleanup
```

The app's `google-services.json` API key is used for Identity Toolkit REST calls in that test;
the key pasted from the GCP console belongs to a different project and is only stored as
`GOOGLE_API_KEY` in `.env` for future Google Cloud API use.

---

## 2. Quickstart — Web (React + Vite)

```bash
cd web
npm install
npm run dev        # http://localhost:5173 — proxies /api to the local backend on :3001
npm run build      # web/dist (static SPA for Firebase Hosting)
```

- Role dashboards: `/admin` (platform ops), `/seller` (store), `/buyer` (shopper).
- Deploy: `firebase deploy --only hosting` (needs `firebase target:apply hosting web scottstechx-52bab-web` once).
- Production API URL goes in `web/.env.production` (`VITE_API_URL=...cloudfunctions.net/api`).

## 2b. Deploy backend to Firebase Cloud Functions

```bash
cd 12_Backend
./scripts/deploy-firebase.ps1 -DatabaseUrl postgresql://user:pass@host/db   # Windows
# or
./scripts/deploy-firebase.sh postgresql://user:pass@host/db                # macOS/Linux
```

The script pushes every env secret to Firebase Secret Manager, sets config params
(`ai.provider`, `ai.model`, `app.deeplink`), and deploys `api` + `apiWebhook`
functions. **`DATABASE_URL` is required** — embedded Postgres doesn't run in the cloud;
use Supabase/Neon/Cloud SQL. Firebase routes self-configure in the cloud via ambient
credentials (no service-account file needed).

## 3. Quickstart — Android

Requirements: **JDK 17.0.19** (see `scottsx-android/android-toolchain/` on the original machine) + Android SDK.

```bash
cd scottsx-android
set JAVA_HOME=<path-to-jdk17>
set PATH=%JAVA_HOME%\bin;%PATH%
gradlew --no-daemon --offline :app:assembleDebug     # or without --offline on first run
# APK -> app/build/outputs/apk/debug/app-debug.apk

adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <serial> shell wm dismiss-keyguard
adb -s <serial> shell am start -n com.scottsx.app/com.scottsx.app.MainActivity
```

Notes:
- `app/google-services.json` is already in place (your real Firebase project).
- The API base URL is `http://127.0.0.1:3001/api/v1` in `data/remote/V2Client.kt` —
  for a **physical phone** replace it with your PC's LAN IP; the emulator uses `10.0.2.2`
  (already whitelisted in `res/xml/network_security_config.xml`).
- `gradle.properties` sets `kotlin.compiler.execution.strategy=in-process` so builds
  fit inside a 4 GB Gradle JVM.

---

## 3. How the two halves talk

```
Android ── http://127.0.0.1:3001/api/v1/... ──► Fastify backend ──► PostgreSQL :5433
```

- **Every** network call goes through one file: `scottsx-android/.../data/remote/V2Client.kt`
  (one OkHttpClient, one bearer token from `SessionCache`, typed models only).
- Auth: Firebase idToken → `POST /api/v1/auth/firebase/sign-in` → backend mints its own
  HS256 JWT (24 h) → all later calls use `Authorization: Bearer <jwt>`.
- Local email/password auth also exists (`/auth/register`, `/auth/login`) for demo/dev.

---

## 4. API surface (all under `/api/v1`)

```
AUTH        POST /auth/register · /auth/login · /auth/google
            POST /auth/firebase/sign-in · /auth/firebase/send-verification-email
            GET  /auth/firebase/me · POST /auth/firebase/upgrade-to-seller
            POST /auth/upgrade-to-seller (local path)
            GET/PATCH /auth/me · PATCH /me/location
PRODUCTS    GET /products · GET /products/:id
            POST/GET/DELETE /seller/products
SELLERS     GET /sellers/nearby?lat&lng&radiusKm · GET /sellers/:id
            GET /seller/profile · GET/PATCH /seller/store-settings (9 sections)
            GET /seller/dashboard/stats
USER        /me/addresses · /me/payment-methods · /me/bookmarks (+toggle)
            /me/orders · /me/refunds · /me/support/tickets · /me/faqs
            /me/notifications (+ /:id/read) · /me/preferences
            /me/locations · /me/change-password
AI          POST /ai/v2/ask · POST /ai/v2/generate-product · GET /ai/status
CHAT        GET/POST /conversations · GET/POST /conversations/:id/messages
            POST /conversations/:id/read
CMS         GET /cms/:slug (terms · privacy · buyer-protection · about)
HEALTH      GET /healthz
```

---

## 5. Verification checklist (Phase 10 of the build doc)

Backend — all verified live in this build:
- [x] `GET /healthz` → `{ok:true}`
- [x] `GET /api/v1/products` → **24 items**, every `imageUrl` starts `https://images.unsplash.com/`
- [x] `POST /auth/register` → user + JWT; login roundtrip works
- [x] Seller login (`techhub@scottstechx.ug` / `Seller123!`), store-settings GET/PATCH
- [x] `/sellers/nearby` Haversine distance + city chips (6 Ugandan cities)
- [x] `/cms/about` contains `Kato Fred, Ugandan cybersecurity analyst, web dev and software dev.`
- [x] Buyer↔Seller messaging: create thread, send, reply, read receipts, unread counts
- [x] User-full: addresses, bookmarks, orders, refunds, tickets, FAQs,
      notifications (+read), preferences, locations, change-password
- [x] AI: offline fallback without a key; OpenRouter or apifreellm when configured
- [x] Firebase: real idToken → JWT exchange, verification-email link generation,
      upgrade-to-seller guard (403 until verified) — verified live with the real project

Android — source tree complete; build/install on device with the JDK 17 toolchain:
- [ ] App launches → buyer home (API data, falls back to `MarketplaceDataSource`)
- [ ] Product photos render (real Unsplash URLs; Coil global loader)
- [ ] Buyer bottom nav: Home / Nearby / [AI FAB] / Wishlist / Profile
- [ ] Seller bottom nav: Home / AI / [Add FAB] / Messages / Analytics (balanced 2-half layout)
- [ ] SignUp: Firebase "Send verification email" → "I've verified — continue"
- [ ] AddProduct: "✨ AI suggest from photo" produces title + description + category
- [ ] Messaging: read receipts ✓✓, typing bubble, ⭐ quick replies, greyed send when empty
- [ ] Nearby: category chips, sort pill (Nearest→Top rated→Most products), verified toggle, 1–100 km slider
- [ ] Theme screen light/dark/system; CMS about page with founder bio

---

## 6. Known gotchas (from the master build doc)

- Unsplash images load only when (a) `imageUrl` is a real URL (never `"phone"`),
  (b) `ProductImage` uses the global Coil loader (`Coil.imageLoader(ctx)`, done in
  `ui/components/ProductImage.kt`), (c) the phone has internet.
- Buyer home shows live API data; falls back to `MarketplaceDataSource.products` when empty.
- Signup verification is the Firebase **email-link** model, not a 6-digit code.
- Seeded seller login: any seed email + `Seller123!`.
- On this Linux sandbox `npm install` pulls the `@embedded-postgres/linux-x64` binaries
  automatically (the package.json `allowScripts` field is for npm 11+).
- First Gradle build downloads the Android Gradle Plugin + dependencies — run without
  `--offline` the first time.

## 7. Deployment (production checklist)

Backend: set `DATABASE_URL` to managed Postgres · configure SendGrid if scaling past
Firebase SMTP · lock CORS origin · add HTTPS (Caddy/Nginx/Cloudflare).
Android: release signing key · prod `google-services.json` · replace demo Unsplash URLs ·
update `APP_DEEP_LINK`.

---

*Built from the ScottsTechX master build prompt (v0.22.1), 2026-08-14.*
