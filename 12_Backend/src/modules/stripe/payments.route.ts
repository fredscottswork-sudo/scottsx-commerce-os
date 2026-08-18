/**
 * ScottsTechX — Stripe skeleton (keys empty in .env for now).
 *
 * NOTE: POST /api/v1/orders/:orderId/pay is now owned by the Nylon Pay module
 * (src/modules/payments/nylonpay.route.ts). The Stripe webhook stays reserved
 * here for when STRIPE_SECRET_KEY is configured.
 */
import type { FastifyInstance } from 'fastify';

export default async function registerStripeRoute(app: FastifyInstance) {
  app.post('/api/v1/stripe/webhook', async () => {
    // Verify signature with STRIPE_WEBHOOK_SECRET and handle events here.
    return { received: true };
  });
}
