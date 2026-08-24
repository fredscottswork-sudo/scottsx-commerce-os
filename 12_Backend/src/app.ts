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
import { registerResetRoutes } from './modules/auth/reset.route.js';
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
import registerImagesRoute from './modules/uploads/images.route.js';
import registerSocialRoute from './modules/social/social.route.js';
import registerSupportRoute from './modules/support/support.route.js';
import registerGeoRoute from './modules/geo/geo.route.js';
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
  // 8 MB matches the product-image route's cap: with the old 3 MB limit an
  // oversized upload died as an opaque multipart protocol error before the
  // handler could answer with a readable 400.
  await app.register(multipart, { limits: { fileSize: 8 * 1024 * 1024 } });

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

  registerAuthRoute(app);
  registerFirebaseAuthRoute(app);
  registerGoogleRoute(app);
  registerVerifyRoutes(app);
  registerResetRoutes(app);
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
  registerImagesRoute(app);
  registerSocialRoute(app);
  registerSupportRoute(app);
  registerGeoRoute(app);

  return app;
}
