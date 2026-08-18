/**
 * ScottsTechX — Firebase Cloud Functions (v2) entry.
 *
 * Serves the same Fastify app via an onRequest HTTPS function.
 *
 * Environment comes from Firebase Secret Manager / config params:
 *   - Secrets (functions:secrets:set): DATABASE_URL, JWT_SECRET, LLM_API_KEY,
 *     APIFREELLM_API_KEY, NYLON_PAY_API_KEY, NYLON_PAY_API_SECRET,
 *     NYLON_PAY_WEBHOOK_SECRET, GOOGLE_CLIENT_ID, GOOGLE_API_KEY,
 *     STRIPE_SECRET_KEY, STRIPE_WEBHOOK_SECRET
 *   - Config strings (functions:config:set ai.provider=...): AI_PROVIDER,
 *     AI_MODEL, APP_DEEP_LINK
 *
 * In production DATABASE_URL MUST point at a managed Postgres (Supabase/Neon/
 * Cloud SQL) — embedded Postgres is only for local dev.
 */
import 'dotenv/config';
import { onRequest } from 'firebase-functions/v2/https';
import { defineSecret, defineString } from 'firebase-functions/params';
import { initDatabase } from './db.js';
import { buildApp, isServerless } from './app.js';
import type { FastifyInstance } from 'fastify';

// ── Config / secrets (declared so deploy wires them; each read is guarded) ──
const secrets = {
  DATABASE_URL: defineSecret('DATABASE_URL'),
  JWT_SECRET: defineSecret('JWT_SECRET'),
  LLM_API_KEY: defineSecret('LLM_API_KEY'),
  APIFREELLM_API_KEY: defineSecret('APIFREELLM_API_KEY'),
  NYLON_PAY_API_KEY: defineSecret('NYLON_PAY_API_KEY'),
  NYLON_PAY_API_SECRET: defineSecret('NYLON_PAY_API_SECRET'),
  NYLON_PAY_WEBHOOK_SECRET: defineSecret('NYLON_PAY_WEBHOOK_SECRET'),
  GOOGLE_CLIENT_ID: defineSecret('GOOGLE_CLIENT_ID'),
  GOOGLE_API_KEY: defineSecret('GOOGLE_API_KEY'),
  STRIPE_SECRET_KEY: defineSecret('STRIPE_SECRET_KEY'),
  STRIPE_WEBHOOK_SECRET: defineSecret('STRIPE_WEBHOOK_SECRET'),
};

const config = {
  AI_PROVIDER: defineString('AI_PROVIDER', { default: 'openrouter' }),
  AI_MODEL: defineString('AI_MODEL', { default: 'meta-llama/llama-3.3-70b-instruct' }),
  APP_DEEP_LINK: defineString('APP_DEEP_LINK', {
    default: 'https://scottstechx-52bab.firebaseapp.com/__/auth/action',
  }),
};

function applyEnvFromFirebase(): void {
  for (const [name, param] of Object.entries(secrets)) {
    try {
      const value = param.value();
      if (value) process.env[name] = value;
    } catch {
      // Secret not set yet — leave whatever .env / default provides.
    }
  }
  try {
    process.env.AI_PROVIDER = config.AI_PROVIDER.value();
    process.env.AI_MODEL = config.AI_MODEL.value();
    process.env.APP_DEEP_LINK = config.APP_DEEP_LINK.value();
  } catch {
    /* ignore */
  }
}

// ── Lazy, memoised bootstrap (runs once per warm instance) ─────────────────
let appPromise: Promise<FastifyInstance> | null = null;

function getApp(): Promise<FastifyInstance> {
  if (!appPromise) {
    appPromise = (async () => {
      applyEnvFromFirebase();
      if (isServerless() && !process.env.DATABASE_URL) {
        throw new Error(
          'DATABASE_URL is not set — Cloud Functions needs a managed Postgres (Supabase/Neon/Cloud SQL). ' +
            'Run: firebase functions:secrets:set DATABASE_URL'
        );
      }
      // Migrations + seed + admin run once per warm instance; schema_migrations
      // makes this cheap and idempotent.
      await initDatabase();
      return await buildApp();
    })();
  }
  return appPromise;
}

// ── The HTTPS function ──────────────────────────────────────────────────────
export const api = onRequest(
  {
    region: 'europe-west1',
    timeoutSeconds: 120,
    memory: '512MiB',
    secrets: Object.values(secrets).map((s) => s.name),
  },
  async (req, res) => {
    try {
      const app = await getApp();
      await app.ready();
      app.server.emit('request', req as never, res as never);
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      res.status(503).json({ error: message });
    }
  }
);

/** A second function for the Nylon Pay webhook — same app, no extra deps. */
export const apiWebhook = onRequest(
  {
    region: 'europe-west1',
    timeoutSeconds: 60,
    memory: '256MiB',
    secrets: Object.values(secrets).map((s) => s.name),
  },
  async (req, res) => {
    if (req.method !== 'POST') {
      res.status(405).json({ error: 'Method not allowed' });
      return;
    }
    try {
      const app = await getApp();
      await app.ready();
      app.server.emit('request', req as never, res as never);
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      res.status(503).json({ error: message });
    }
  }
);
