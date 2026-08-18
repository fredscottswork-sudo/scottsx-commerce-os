/**
 * ScottsTechX — Nylon Pay (payments API for Africa) module.
 *
 *   POST /api/v1/orders/checkout            (auth) create order + hosted payment link
 *   POST /api/v1/orders/:orderId/pay        (auth) get/recreate the payment link
 *   POST /api/v1/payments/nylonpay/webhook          verify signature + update order
 *
 * Uses the official SDK (@nile-squad/nylonpay-ts). Test vs live is chosen by
 * the key prefix (npk_sandbox_… = sandbox). Configure in .env:
 *   NYLON_PAY_API_KEY / NYLON_PAY_API_SECRET / NYLON_PAY_WEBHOOK_SECRET
 */
import type { FastifyInstance, FastifyRequest } from 'fastify';
import { z } from 'zod';
import { createNylonPay } from '@nile-squad/nylonpay-ts';
import { getPool } from '../../db.js';
import { requireAuth, authedUser } from '../../auth.js';
import { NotFoundError, ServiceUnavailableError } from '../../errors.js';
import { rawBodyOf } from './raw-body.js';

const CURRENCY = 'UGX';

export function nylonConfigured(): boolean {
  return Boolean(process.env.NYLON_PAY_API_KEY && process.env.NYLON_PAY_API_SECRET);
}

function client() {
  if (!nylonConfigured()) {
    throw new ServiceUnavailableError(
      'Nylon Pay is not configured — set NYLON_PAY_API_KEY and NYLON_PAY_API_SECRET in 12_Backend/.env'
    );
  }
  return createNylonPay({
    apiKey: process.env.NYLON_PAY_API_KEY!,
    apiSecret: process.env.NYLON_PAY_API_SECRET!,
    timeoutMs: 30_000,
    maxRetries: 2,
  });
}

/**
 * Create a payment for an order via Nylon Pay.
 *
 * Preferred: hosted invoice (payment link) — live keys only.
 * Fallback: mobile-money collect (STK push to the buyer's phone) — sandbox
 * and live. The SDK auto-approves in sandbox so the flow is testable.
 *
 * Returns { mode: 'invoice' | 'collect', paymentLink?, invoiceNumber?, reference, status }.
 */
async function createPaymentForOrder(order: any, buyerEmail: string, buyerName: string, buyerPhone: string) {
  const nylon = client();
  const unitPrice = Number(order.price_minor ?? order.priceMinor ?? order.amount ?? 0);
  const amount = unitPrice * Number(order.quantity ?? 1);
  const phone = (buyerPhone || order.buyer_phone || '').replace(/[^0-9]/g, '').replace(/^0/, '256');
  const customer = { name: buyerName || 'ScottsTechX Buyer', phoneNumber: phone || '256700000000' };

  // 1) Try the hosted invoice (works on live keys).
  try {
    const invoice = await nylon.createInvoice({
      amount,
      currency: CURRENCY,
      customerEmail: buyerEmail,
      customerName: customer.name,
      customerPhone: phone || undefined,
      description: order.product_title || order.title || 'ScottsTechX order',
      items: [{ name: order.product_title || order.title || 'Order item', quantity: Number(order.quantity ?? 1), unitPrice }],
      merchantReference: order.id,
      metadata: { orderId: order.id, productId: order.product_id ?? '', title: order.product_title ?? '' },
    });
    if (invoice.isOk && invoice.value?.paymentLink) {
      return {
        mode: 'invoice' as const,
        paymentLink: invoice.value.paymentLink,
        invoiceNumber: invoice.value.invoiceNumber ?? null,
        reference: order.id,
        status: 'pending',
      };
    }
    // If the failure is not about sandbox links, surface it.
    const errMsg = String(invoice.error ?? '');
    if (!/not available in sandbox/i.test(errMsg)) {
      throw new ServiceUnavailableError(`Nylon Pay invoice failed: ${errMsg}`);
    }
  } catch (err: any) {
    if (err?.name !== 'ServiceUnavailableError') throw err;
    const msg = String(err?.message ?? '');
    if (!/not available in sandbox/i.test(msg)) throw err;
    // fall through to collectPayment
  }

  // 2) Fallback: mobile-money collect (sandbox-safe; auto-succeeds in sandbox).
  const payment = await nylon.collectPayment({
    amount,
    currency: CURRENCY,
    description: order.product_title || order.title || 'ScottsTechX order',
    customer,
    reference: order.id, // our order UUID doubles as the payment reference
    metadata: { orderId: order.id, productId: order.product_id ?? '', title: order.product_title ?? '' },
  });
  return {
    mode: 'collect' as const,
    paymentLink: null,
    invoiceNumber: null,
    reference: payment.reference,
    status: payment.status ?? 'pending',
  };
}

export default async function registerNylonPayRoute(app: FastifyInstance) {
  const pool = getPool();

  const checkoutSchema = z.object({
    productId: z.string().min(1),
    quantity: z.number().int().min(1).max(99).optional().default(1),
    buyerPhone: z.string().optional().default(''),
  });

  // ── Checkout: create the order, then return a hosted payment link ────────
  app.post('/api/v1/orders/checkout', { preHandler: requireAuth }, async (request, reply) => {
    const me = authedUser(request);
    const body = checkoutSchema.parse(request.body);

    const buyerRow = await pool.query('SELECT phone FROM users WHERE id = $1', [me.id]);
    const buyerPhone = body.buyerPhone || buyerRow.rows[0]?.phone || '';

    const product = await pool.query(
      `SELECT id, seller_id AS "sellerId", title, price_minor::int AS price, stock_quantity AS stock
       FROM products WHERE id = $1`,
      [body.productId]
    );
    if (!product.rows[0]) throw new NotFoundError('Product not found');
    const p = product.rows[0];
    if (p.stock < body.quantity) throw new ServiceUnavailableError('Not enough stock for this quantity');

    const { rows } = await pool.query(
      `INSERT INTO orders (buyer_id, seller_id, product_id, product_title, price_minor, quantity, status, payment_provider)
       VALUES ($1, $2, $3, $4, $5, $6, 'pending', 'nylonpay')
       RETURNING id, seller_id AS "sellerId", product_id AS "productId", product_title AS title,
                 price_minor::int AS amount, quantity, status, created_at AS "createdAt"`,
      [me.id, p.sellerId, p.id, p.title, p.price, body.quantity]
    );
    const order = rows[0];

    try {
      const payment = await createPaymentForOrder(order, me.email, me.name, buyerPhone);
      await pool.query(
        `UPDATE orders SET payment_link = $2, payment_reference = $3, updated_at = now() WHERE id = $1`,
        [order.id, payment.mode === 'invoice' ? payment.paymentLink : null, payment.reference]
      );
      return reply.code(201).send({
        order: {
          ...order,
          paymentLink: payment.paymentLink,
          invoiceNumber: payment.invoiceNumber,
          paymentMode: payment.mode,
        },
        paymentMode: payment.mode,
        paymentLink: payment.paymentLink,
        invoiceNumber: payment.invoiceNumber,
        paymentReference: payment.reference,
        status: payment.status,
      });
    } catch (err) {
      // Keep the pending order for a retry; surface the payment error clearly.
      throw err;
    }
  });

  // ── Pay an existing order (idempotent — reuses the stored link) ──────────
  app.post('/api/v1/orders/:orderId/pay', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { orderId } = request.params as { orderId: string };
    const { rows } = await pool.query(
      `SELECT * FROM orders WHERE id = $1 AND buyer_id = $2`,
      [orderId, me.id]
    );
    if (!rows[0]) throw new NotFoundError('Order not found');
    const order = rows[0];

    if (order.payment_link) {
      return { order, paymentLink: order.payment_link, reused: true };
    }
    const buyerRow = await pool.query('SELECT phone FROM users WHERE id = $1', [me.id]);
    const payment = await createPaymentForOrder(order, me.email, me.name, buyerRow.rows[0]?.phone || '');
    await pool.query(
      `UPDATE orders SET payment_link = $2, payment_reference = $3, updated_at = now() WHERE id = $1`,
      [order.id, payment.mode === 'invoice' ? payment.paymentLink : null, payment.reference]
    );
    return {
      order: { ...order, paymentLink: payment.paymentLink },
      paymentLink: payment.paymentLink,
      paymentMode: payment.mode,
      paymentReference: payment.reference,
      reused: false,
    };
  });

  // ── Webhook: verify signature, then update the order ─────────────────────
  app.post('/api/v1/payments/nylonpay/webhook', async (request: FastifyRequest, reply) => {
    const secret = process.env.NYLON_PAY_WEBHOOK_SECRET;
    if (!secret) {
      throw new ServiceUnavailableError('Nylon Pay webhook secret not configured — set NYLON_PAY_WEBHOOK_SECRET in .env');
    }
    const raw = rawBodyOf(request);
    if (!raw) throw new ServiceUnavailableError('Raw body unavailable for signature verification');

    const signature = String(request.headers['x-nylon-signature'] ?? '');
    const nylon = client();
    const valid = nylon.verifyWebhookSignature({ payload: raw, signature, secret });
    if (!valid) {
      return reply.code(401).send({ error: 'Invalid webhook signature' });
    }

    const body = request.body as any;
    const event = body?.event ?? '';
    const payload = body?.payload ?? {};

    // merchantReference = our order id -> payload.reference should equal the order id.
    const reference = String(payload.reference ?? '');
    const update =
      event === 'transaction.successful'
        ? { status: 'paid', nylon_transaction_id: payload.transactionId ?? null }
        : event === 'transaction.failed' || event === 'transaction.cancelled'
          ? { status: 'cancelled', nylon_transaction_id: payload.transactionId ?? null }
          : { status: 'pending', nylon_transaction_id: payload.transactionId ?? null };

    const res = await pool.query(
      `UPDATE orders SET status = $2, nylon_transaction_id = $3, updated_at = now()
       WHERE (payment_reference = $1 OR id::text = $1) AND status <> 'paid'
       RETURNING id`,
      [reference, update.status, update.nylon_transaction_id]
    );

    if (event === 'transaction.successful' && (res.rowCount ?? 0) > 0) {
      // Decrement stock after a confirmed payment.
      await pool.query(
        `UPDATE products p SET stock_quantity = GREATEST(stock_quantity - o.quantity, 0)
         FROM orders o WHERE o.id = $1 AND o.product_id = p.id`,
        [res.rows[0].id]
      );
    }

    return { received: true, event, reference, matched: (res.rowCount ?? 0) > 0 };
  });

  // ── Status helper: resolve an order's Nylon transaction state ────────────
  app.get('/api/v1/orders/:orderId/payment-status', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { orderId } = request.params as { orderId: string };
    const { rows } = await pool.query(
      `SELECT id, status, payment_reference AS "paymentReference", payment_link AS "paymentLink"
       FROM orders WHERE id = $1 AND buyer_id = $2`,
      [orderId, me.id]
    );
    if (!rows[0]) throw new NotFoundError('Order not found');
    const order = rows[0];

    let nylonStatus: string | null = null;
    if (nylonConfigured() && order.paymentReference) {
      const status = await client().getStatus({ reference: order.paymentReference });
      if (status.isOk) nylonStatus = status.value.status;
    }
    return { order, nylonStatus };
  });
}
