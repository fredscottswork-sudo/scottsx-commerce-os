# ScottsTechX Backend (12_Backend)

Fastify 5 + TypeScript + embedded PostgreSQL 18 (port 5433). API on **0.0.0.0:3001**.

## Run

```bash
npm install
npm run dev        # tsx watch src/server.ts
npm run build      # tsc -> dist/
npm run start      # node dist/server.js
npm run smoke      # 6-check smoke test (server must be running)
```

On boot: embedded PG → migrations (`migrations/*.sql`, tracked in `schema_migrations`)
→ auto-seed marketplace (6 sellers + 24 products) if empty.

Manual re-seed: `node seed_marketplace.mjs`

## Layout

```
src/
  server.ts                  bootstrap + route registration + /healthz
  db.ts                      embedded-PG bootstrap, pool, migrations, auto-seed
  auth.ts                    bcrypt + jose JWT (HS256, 24h) + requireAuth/requireSeller
  errors.ts                  UnauthorizedError / ForbiddenError / NotFoundError / ...
  firebase/admin.ts          firebase-admin init (secrets/firebase-admin-key.json)
  smoke.ts                   npm run smoke
  modules/
    auth/      login.route.ts · firebase-auth.route.ts · google.route.ts
    products/  products.route.ts · products.service.ts
    seller/    store-settings.route.ts (8 sections) · seller-public.route.ts (nearby/stats)
    user/      user-full.route.ts (30+ endpoints)
    ai/        assistant.route.ts · assistant.service.ts (OpenRouter | apifreellm)
    chat/      chat.route.ts
    cms/       cms.route.ts
    http/      json-body.ts (empty-body-tolerant JSON parsing)
migrations/    0001..0021 SQL
seed_marketplace.mjs
.env            PORT / DATABASE_URL / JWT_SECRET / LLM_API_KEY / APP_DEEP_LINK / ...
```

## Demo accounts

- Seeded sellers: `techhub@scottstechx.ug`, `fashionhouse@scottstechx.ug`,
  `sneakerking@scottstechx.ug`, `homebeyond@scottstechx.ug`, `glamour@scottstechx.ug`,
  `ugandacrafts@scottstechx.ug` — password `Seller123!`
- Any registered buyer: `POST /auth/register` then `POST /auth/upgrade-to-seller`
  (local path) or the Firebase flow.

## Firebase

Place your service account at `secrets/firebase-admin-key.json` (git-ignored) to enable
`/auth/firebase/*`. Until then those routes return 503 with a clear message.

## AI providers

`AI_PROVIDER` selects the backend:
- `openrouter` (default) — uses `LLM_API_KEY`, model `AI_MODEL`.
- `apifreellm` — uses `APIFREELLM_API_KEY`, POSTs to `/api/v1/chat`.
  Note: apifreellm's free tier blocks datacenter/cloud IPs (403). Run the backend
  from a residential connection, upgrade to premium, or fall back to OpenRouter.
- Without any key the API returns a deterministic offline fallback.
