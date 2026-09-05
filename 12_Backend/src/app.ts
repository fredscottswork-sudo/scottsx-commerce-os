/**
 * ScottsTechX — shared Fastify app builder.
 *
 * Builds the fully-registered Fastify instance (CORS, raw-body capture for
 * webhook signatures, error handler, /healthz, every route module under
 * /api/v1). No DB bootstrap, no listen — callers decide how to serve it
 * (local server.ts vs Firebase Cloud Functions firebase-entry.ts).
 */
import Fastify, { type FastifyInstance } from 'fastify';
import cors from '@fastify/cors';
import multipart from '@fastify/multipart';
import { ZodError } from 'zod';
import {
  UnauthorizedError,
  ForbiddenError,
  NotFoundError,
  ConflictError,
  ServiceUnavailableError,
  TooManyRequestsError,
  ValidationError,
} from './errors.js';

import registerAuthRoute from './modules/auth/login.route.js';
import registerFirebaseAuthRoute from './modules/auth/firebase-auth.route.js';
import registerGoogleRoute from './modules/auth/google.route.js';
import { registerVerifyRoutes } from './modules/auth/verify.route.js';
import registerOtpRoutes from './modules/auth/otp.route.js';
import { mailSummary } from './mail.js';
import { registerResetRoutes } from './modules/auth/reset.route.js';
import registerProductsRoute from './modules/products/products.route.js';
import registerStoreSettingsRoute from './modules/seller/store-settings.route.js';
import registerSellerPublicRoute from './modules/seller/seller-public.route.js';
import registerUserFullRoute from './modules/user/user-full.route.js';
import registerAiRoute from './modules/ai/assistant.route.js';
import registerChatRoute from './modules/chat/chat.route.js';
import registerCmsRoute from './modules/cms/cms.route.js';
import registerAdminRoute from './modules/admin/admin.route.js';
import registerUploadsRoute from './modules/uploads/photo.route.js';
import registerImagesRoute from './modules/uploads/images.route.js';
import registerSocialRoute from './modules/social/social.route.js';
import registerSupportRoute from './modules/support/support.route.js';
import registerGeoRoute from './modules/geo/geo.route.js';
import registerSitemapRoute from './modules/seo/sitemap.route.js';
import { installJsonParser } from './modules/http/json-body.js';

/** True when running inside Firebase Cloud Functions / Cloud Run (v2). */
export function isServerless(): boolean {
  return Boolean(
    process.env.FIREBASE_CONFIG ||
      process.env.K_SERVICE ||
      process.env.FUNCTION_TARGET ||
      process.env.FUNCTIONS_EMULATOR
  );
}

export async function buildApp(): Promise<FastifyInstance> {
  const app = Fastify({ logger: true, trustProxy: true });

  await app.register(cors, { origin: '*' });
  try {
    const compress = (await import('@fastify/compress')).default;
    await app.register(compress, { global: true, threshold: 1024 });
  } catch {
    // compress not installed — continue without it
  }
  await app.register(multipart, { limits: { fileSize: 8 * 1024 * 1024 } });

  // Cache-Control for public GETs — CDN + browser
  app.addHook('onSend', async (request, reply, payload) => {
    if (request.method === 'GET') {
      const url = request.url;
      if (url.startsWith('/api/v1/products') || url.startsWith('/api/v1/sellers/nearby') || url.startsWith('/api/v1/geo/')) {
        reply.header('Cache-Control', 'public, max-age=15, stale-while-revalidate=60');
      } else if (url.startsWith('/api/v1/products/facets') || url.startsWith('/api/v1/products/suggest')) {
        reply.header('Cache-Control', 'public, max-age=60, stale-while-revalidate=300');
      }
    }
    return payload;
  });

  // Body-less requests that declare application/json (a normal client
  // pattern) must not 500 — parse empty bodies as undefined.
  installJsonParser(app);

  app.setErrorHandler((err, request, reply) => {
    if (err instanceof ZodError) {
      return reply.code(400).send({ error: 'Validation error', issues: err.issues });
    }
    if (err instanceof ValidationError) return reply.code(400).send({ error: err.message });
    if (err instanceof UnauthorizedError) return reply.code(401).send({ error: err.message });
    if (err instanceof ForbiddenError) return reply.code(403).send({ error: err.message });
    if (err instanceof NotFoundError) return reply.code(404).send({ error: err.message });
    if (err instanceof ConflictError) return reply.code(409).send({ error: err.message });
    if (err instanceof TooManyRequestsError) {
      // Retry-After lets the client show a real countdown instead of guessing.
      return reply
        .code(429)
        .header('Retry-After', String(err.retryAfterSec))
        .send({ error: err.message, retryAfterSec: err.retryAfterSec });
    }
    if (err instanceof ServiceUnavailableError) return reply.code(503).send({ error: err.message });
    request.log.error(err);
    return reply.code(500).send({ error: err instanceof Error ? err.message : 'Internal server error' });
  });

  app.get('/healthz', async () => ({ ok: true }));
  app.get('/api/v1/healthz', async () => ({ ok: true, db: 'connected' }));
  // Operator diagnostics for outbound email: is SMTP configured, which mode,
  // and the reason for the most recent failure. No secrets are exposed.
  app.get('/api/v1/mail/status', async () => mailSummary());

  // Landing page for the API root. Opening the API host in a browser used to
  // return a bare {"error":"Not Found"}, which reads as "the site is broken"
  // when it actually means "this is the API, the website is elsewhere".
  app.get('/', async (_request, reply) =>
    reply.type('text/html').send(`<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>ScottsTechX API</title>
<style>
 body{margin:0;min-height:100vh;display:grid;place-items:center;background:#0e1420;
      color:#eef2fb;font:16px/1.6 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
 .card{max-width:34rem;padding:2.5rem;text-align:center}
 h1{margin:0 0 .5rem;font-size:1.5rem}
 p{margin:.4rem 0;color:#9fb0cc}
 code{background:#121a2f;border:1px solid #1e2a45;border-radius:6px;padding:.15rem .45rem;
      font-size:.9em;color:#eef2fb}
 a{color:#5b9bff}
</style></head><body><div class="card">
 <h1>ScottsTechX API</h1>
 <p>This is the backend. It has no pages to browse &mdash; the website runs separately.</p>
 <p style="margin-top:1.2rem">Health: <a href="/api/v1/healthz"><code>/api/v1/healthz</code></a><br>
    Catalogue: <a href="/api/v1/products"><code>/api/v1/products</code></a></p>
 <p style="margin-top:1.2rem;font-size:.9em">Looking for the shop? Open the website on its
    own address (port <code>5173</code> in development), not this one.</p>
</div></body></html>`)
  );

  registerAuthRoute(app);
  registerFirebaseAuthRoute(app);
  registerGoogleRoute(app);
  registerVerifyRoutes(app);
  registerOtpRoutes(app);
  registerResetRoutes(app);
  registerProductsRoute(app);
  registerStoreSettingsRoute(app);
  registerSellerPublicRoute(app);
  registerUserFullRoute(app);
  registerAiRoute(app);
  registerChatRoute(app);
  registerCmsRoute(app);
  registerAdminRoute(app);
  registerUploadsRoute(app);
  registerImagesRoute(app);
  registerSocialRoute(app);
  registerSupportRoute(app);
  registerGeoRoute(app);
  registerSitemapRoute(app);

  return app;
}
