# ScottsTechX Web

React + Vite + TypeScript web client for the **same** ScottsTechX backend the mobile app uses.

```
Mobile App ──┐
             ├──► Fastify backend (Postgres) ◄── Web App
Android UI   ┘                              └── React + Vite SPA
```

No separate backend, no separate database, no mock data. Every page talks to the
existing `/api/v1` endpoints through one typed client (`src/api/client.ts` + `src/api/services.ts`).

## Run locally

```bash
cd web
npm install
npm run dev        # http://localhost:5173  (proxies /api → http://127.0.0.1:3001)
```

The backend must be running: `cd ../12_Backend && npm run dev`.

## Roles

| Role | Web home | What they get |
|---|---|---|
| `admin`  | `/admin`        | Platform overview, user management, product moderation |
| `seller` | `/seller`       | Dashboard, inventory, add/bulk-import products, orders, analytics, store settings, AI |
| `buyer`  | `/buyer`        | Dashboard, orders, saved, addresses, payments, refunds, support, messaging, AI |

Every route is guarded **client-side for UX only** — the backend enforces the real
permission checks (admin endpoints return 403 for non-admins, etc.).

## Environment

| File | Purpose |
|---|---|
| `.env.development` | `VITE_API_URL=` → same-origin; Vite dev proxy sends `/api` to the local backend |
| `.env.production.example` | `VITE_API_URL=https://europe-west1-scottstechx-52bab.cloudfunctions.net/api` |

**Never put secrets in frontend env files — frontend code is public.**

## Build & deploy to Firebase Hosting

```bash
npm run build                       # outputs web/dist
# from repo root:
firebase target:apply hosting web scottstechx-52bab-web   # once
firebase deploy --only hosting     # SPA at https://scottstechx-52bab.web.app
```

The API itself deploys with the backend (`firebase deploy --only functions` from `12_Backend`,
see `12_Backend/scripts/deploy-firebase.ps1`).

## Feature notes

- **Messaging**: shared conversations + read receipts via the same chat API; polls every 3–10 s (the backend has no websocket yet — same refresh strategy as the mobile app's MessageStream).
- **Payments**: "Buy now" calls `POST /orders/checkout` → Nylon Pay (hosted link on live keys, MoMo push on sandbox keys).
- **Bulk import**: seller CSV import with parse → preview → validation → import through the existing product endpoint.
- **Photos**: profile photo uploads to Firebase Storage (`POST /me/photo`) with a URL-input fallback.
- **Theme**: light/dark/system persisted to `PATCH /me/preferences` — shared with mobile.
