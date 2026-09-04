/**
 * ScottsTechX — product routes.
 *
 * PUBLIC (approved products only)
 *   GET    /api/v1/products                   full catalog + filters + paging
 *   GET    /api/v1/products/search            same, search-first shape
 *   GET    /api/v1/products/facets            categories / brands / price range
 *   GET    /api/v1/products/suggest?q=        typeahead
 *   GET    /api/v1/products/:id
 *   GET    /api/v1/products/:id/related
 *
 * SELLER (own listings, every status)
 *   GET    /api/v1/seller/products?status=
 *   POST   /api/v1/seller/products            → status 'pending' (needs approval)
 *   PATCH  /api/v1/seller/products/:id
 *   POST   /api/v1/seller/products/:id/submit
 *   DELETE /api/v1/seller/products/:id
 */
import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { getPool } from '../../db.js';
import { requireAuth, requireSeller, verifyJwt } from '../../auth.js';
import { ValidationError } from '../../errors.js';
import {
  searchProducts,
  getProductById,
  listSellerProducts,
  createProduct,
  updateProduct,
  submitForReview,
  deleteProduct,
  catalogFacets,
  suggest,
  relatedProducts,
  recordView,
} from './products.service.js';
import { notify } from '../notifications/notify.service.js';

/**
 * Postgres `integer` (and the `price_minor::int` casts every read uses) top out
 * at 2,147,483,647. Without this ceiling the INSERT commits and the *read back*
 * throws, so the seller sees a 500 while the product silently exists. Reject it
 * up front instead — 2.1bn UGX is far beyond any real listing.
 */
const INT4_MAX = 2147483647;

const money = () => z.number().int().nonnegative().max(INT4_MAX);
const count = () => z.number().int().nonnegative().max(INT4_MAX);

const newProductSchema = z.object({
  title: z.string().min(1),
  description: z.string().optional().default(''),
  category: z.string().optional().default('Other'),
  brand: z.string().optional().default(''),
  priceMinor: money(),
  oldPriceMinor: money().optional().nullable(),
  stockQuantity: count().optional().default(1),
  imageUrl: z.string().optional().default(''),
  mediaUrls: z.array(z.string()).optional().default([]),
  location: z.string().optional().default(''),
  isFlashDeal: z.boolean().optional().default(false),
  discountPercent: z.number().int().min(0).max(100).optional().default(0),
  /** Save without submitting for review. */
  asDraft: z.boolean().optional().default(false),
});

/**
 * PATCH must only carry the fields the caller actually sent.
 *
 * `newProductSchema.partial()` is NOT safe here: `.partial()` makes a key
 * optional but a `.default()` still fires when the key is absent, so
 * `PATCH {stockQuantity: 8}` would silently also write description '',
 * category 'Other', brand '', isFlashDeal false and discountPercent 0 —
 * wiping the listing's content and (because the category/description
 * "changed") knocking an approved product back into the review queue.
 * Declaring the update shape explicitly, with no defaults, keeps a partial
 * update partial.
 */
const updateProductSchema = z.object({
  title: z.string().min(1).optional(),
  description: z.string().optional(),
  category: z.string().optional(),
  brand: z.string().optional(),
  priceMinor: money().optional(),
  oldPriceMinor: money().nullable().optional(),
  stockQuantity: count().optional(),
  imageUrl: z.string().optional(),
  mediaUrls: z.array(z.string()).optional(),
  location: z.string().optional(),
  isFlashDeal: z.boolean().optional(),
  discountPercent: z.number().int().min(0).max(100).optional(),
});

const listQuerySchema = z.object({
  q: z.string().optional(),
  category: z.string().optional(),
  brand: z.string().optional(),
  minPrice: z.coerce.number().optional(),
  maxPrice: z.coerce.number().optional(),
  minRating: z.coerce.number().optional(),
  verifiedOnly: z.coerce.boolean().optional(),
  inStock: z.coerce.boolean().optional(),
  flashOnly: z.coerce.boolean().optional(),
  sellerId: z.string().optional(),
  sort: z.enum(['relevance', 'newest', 'price_asc', 'price_desc', 'rating', 'popular']).optional(),
  page: z.coerce.number().int().min(1).optional(),
  pageSize: z.coerce.number().int().min(1).max(100).optional(),
});

/**
 * Quality gate applied before a listing can enter the review queue.
 * Keeps junk out of the moderation queue and guarantees buyer-facing rows
 * always render a real image.
 */
function assertListingReady(input: {
  title?: string;
  imageUrl?: string;
  mediaUrls?: string[];
  priceMinor?: number;
}) {
  const image = input.imageUrl || input.mediaUrls?.[0] || '';
  // Either an external link or one of our own uploads. The upload path form is
  // accepted because sellers list from a phone, where the photo comes from the
  // camera roll and there is no public URL to paste.
  const isUpload = /^\/api\/v1\/uploads\/images\/[0-9a-f-]{36}$/i.test(image);
  if (!/^https?:\/\//i.test(image) && !isUpload) {
    throw new ValidationError('A product photo is required before submitting for review');
  }
  if (!input.title || input.title.trim().length < 3) {
    throw new ValidationError('Title must be at least 3 characters');
  }
  if (!input.priceMinor || input.priceMinor <= 0) {
    throw new ValidationError('Price must be greater than zero');
  }
}

async function softUser(request: any): Promise<{ id: string; role: string } | undefined> {
  const header = request.headers?.authorization;
  if (!header?.startsWith('Bearer ')) return undefined;
  try {
    const payload = await verifyJwt(header.slice(7));
    return { id: String(payload.sub), role: String(payload.role ?? 'buyer') };
  } catch {
    return undefined;
  }
}

export default async function registerProductsRoute(app: FastifyInstance) {
  const pool = getPool();

  // ── Public catalog ────────────────────────────────────────────────────────
  app.get('/api/v1/products', async (request) => {
    const q = listQuerySchema.parse(request.query);
    const result = await searchProducts(pool, q);
    // Back-compat: older clients read `products` directly.
    return result;
  });

  app.get('/api/v1/products/search', async (request) => {
    const q = listQuerySchema.parse(request.query);
    const result = await searchProducts(pool, q);
    const who = await softUser(request);
    if (who && q.q) {
      pool
        .query('INSERT INTO search_history (user_id, query, mode, results) VALUES ($1,$2,$3,$4)', [
          who.id,
          q.q,
          'text',
          result.total,
        ])
        .catch(() => undefined);
    }
    return result;
  });

  app.get('/api/v1/products/facets', async () => catalogFacets(pool));

  app.get('/api/v1/products/suggest', async (request) => {
    const { q, limit } = z
      .object({ q: z.string().optional().default(''), limit: z.coerce.number().int().min(1).max(20).optional().default(8) })
      .parse(request.query);
    return { suggestions: await suggest(pool, q, limit) };
  });

  app.get('/api/v1/products/:id', async (request) => {
    const { id } = request.params as { id: string };
    const viewer = await softUser(request);
    const product = await getProductById(pool, id, viewer);
    void recordView(pool, id);
    return { product };
  });

  app.get('/api/v1/products/:id/related', async (request) => {
    const { id } = request.params as { id: string };
    return { products: await relatedProducts(pool, id, 8) };
  });

  // ── Seller listings ───────────────────────────────────────────────────────
  app.get('/api/v1/seller/products', { preHandler: requireAuth }, async (request) => {
    const seller = requireSeller(request);
    const { status } = z.object({ status: z.string().optional() }).parse(request.query);
    const products = await listSellerProducts(pool, seller.id, status);
    const counts = await pool.query(
      `SELECT status, COUNT(*)::int AS count FROM products WHERE seller_id = $1 GROUP BY status`,
      [seller.id]
    );
    const byStatus: Record<string, number> = {
      draft: 0,
      pending: 0,
      approved: 0,
      rejected: 0,
      suspended: 0,
    };
    for (const r of counts.rows) byStatus[r.status] = r.count;
    return { products, counts: byStatus };
  });

  app.post('/api/v1/seller/products', { preHandler: requireAuth }, async (request) => {
    const seller = requireSeller(request);
    const body = newProductSchema.parse(request.body);

    // A listing that goes to buyers must have a real photo. Drafts may be
    // saved without one so sellers can work incrementally.
    if (!body.asDraft) assertListingReady(body);

    const product = await createProduct(pool, seller.id, body, { asDraft: body.asDraft });

    if (product.status === 'pending') {
      // Tell the seller, and alert every admin that a listing needs review.
      await notify(pool, {
        userId: seller.id,
        title: 'Product submitted for review',
        body: `"${product.title}" is awaiting admin approval. You'll be notified once it's live.`,
        type: 'product_pending',
        data: { screen: 'product', id: product.id },
      });
      const admins = await pool.query(`SELECT id FROM users WHERE role = 'admin'`);
      for (const a of admins.rows) {
        await notify(pool, {
          userId: a.id,
          title: 'New product awaiting approval',
          body: `${seller.name || seller.email} submitted "${product.title}".`,
          type: 'general',
          data: { screen: 'admin_products', id: product.id },
        }).catch(() => undefined);
      }
    }
    return { product, message: product.status === 'pending' ? 'Submitted for admin approval' : 'Saved as draft' };
  });

  app.patch('/api/v1/seller/products/:id', { preHandler: requireAuth }, async (request) => {
    const seller = requireSeller(request);
    const { id } = request.params as { id: string };
    const body = updateProductSchema.parse(request.body);
    const product = await updateProduct(pool, seller.id, id, body);
    return { product };
  });

  app.post('/api/v1/seller/products/:id/submit', { preHandler: requireAuth }, async (request) => {
    const seller = requireSeller(request);
    const { id } = request.params as { id: string };
    const product = await submitForReview(pool, seller.id, id);
    const admins = await pool.query(`SELECT id FROM users WHERE role = 'admin'`);
    for (const a of admins.rows) {
      await notify(pool, {
        userId: a.id,
        title: 'Product awaiting approval',
        body: `"${product.title}" was submitted for review.`,
        type: 'general',
        data: { screen: 'admin_products', id: product.id },
      }).catch(() => undefined);
    }
    return { product };
  });

  app.delete('/api/v1/seller/products/:id', { preHandler: requireAuth }, async (request) => {
    const seller = requireSeller(request);
    const { id } = request.params as { id: string };
    await deleteProduct(pool, seller.id, id);
    return { ok: true };
  });
}
