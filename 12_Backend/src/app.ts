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
import registerProductsRoute from './modules/products/products.route.js';
import registerStoreSettingsRoute from './modules/seller/store-settings.route.js';
import registerSellerPublicRoute from './modules/seller/seller-public.route.js';
import registerUserFullRoute from './modules/user/user-full.route.js';
import registerAiRoute from './modules/ai/assistant.route.js';
import registerChatRoute from './modules/chat/chat.route.js';
import registerCmsRoute from './modules/cms/cms.route.js';
import registerStripeRoute from './modules/stripe/payments.route.js';
import registerNylonPayRoute from './modules/payments/nylonpay.route.js';
import registerAdminRoute from './modules/admin/admin.route.js';
import registerUploadsRoute from './modules/uploads/photo.route.js';
import registerImageUploadRoute from './modules/uploads/image.route.js';
import registerSocialRoute from './modules/social/social.route.js';
import registerSupportRoute from './modules/support/support.route.js';
import registerGeoRoute from './modules/geo/geo.route.js';
import registerSitemapRoute from './modules/seo/sitemap.route.js';
import { installRawBodyParser } from './modules/payments/raw-body.js';

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
  await app.register(multipart, { limits: { fileSize: 3 * 1024 * 1024 } });

  // Stash raw bodies so Nylon Pay webhook signatures can be verified over the
  // exact bytes sent (JSON parsing itself is unchanged for every other route).
  installRawBodyParser(app);

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

  // Landing page for the API root. Opening the API host in a browser used to
  // return a bare {"error":"Not Found"}, which reads as "the site is broken"
  // when it actually means "this is the API, the website is elsewhere".
  app.get('/', async (_request, reply) =>
    reply.type('text/html').send(`<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>ScottsTechX API</title>
<style>
 body{margin:0;min-height:100vh;display:grid;place-items:center;background:#05070d;
      color:#eef2fb;font:16px/1.6 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
 .card{max-width:34rem;padding:2.5rem;text-align:center}
 h1{margin:0 0 .5rem;font-size:1.5rem}
 p{margin:.4rem 0;color:#9fb0cc}
 code{background:#121a2f;border:1px solid #1e2a45;border-radius:6px;padding:.15rem .45rem;
      font-size:.9em;color:#eef2fb}
 a{color:#2b7cff}
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
  registerProductsRoute(app);
  registerStoreSettingsRoute(app);
  registerSellerPublicRoute(app);
  registerUserFullRoute(app);
  registerAiRoute(app);
  registerChatRoute(app);
  registerCmsRoute(app);
  registerStripeRoute(app);
  registerNylonPayRoute(app);
  registerAdminRoute(app);
  registerUploadsRoute(app);
  await registerImageUploadRoute(app);
  registerSocialRoute(app);
  registerSupportRoute(app);
  registerGeoRoute(app);
  registerSitemapRoute(app);

  return app;
}
