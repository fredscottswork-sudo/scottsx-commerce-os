/**
 * ScottsTechX — product routes.
 *
 *   GET    /api/v1/products
 *   GET    /api/v1/products/:id
 *   POST   /api/v1/seller/products        (auth, seller)
 *   GET    /api/v1/seller/products        (auth, seller)
 *   DELETE /api/v1/seller/products/:id    (auth, seller)
 */
import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { getPool } from '../../db.js';
import { requireAuth, requireSeller } from '../../auth.js';
import {
  listProducts,
  getProductById,
  listSellerProducts,
  createProduct,
  deleteProduct,
} from './products.service.js';

const newProductSchema = z.object({
  title: z.string().min(1),
  description: z.string().optional().default(''),
  category: z.string().optional().default('Other'),
  brand: z.string().optional().default(''),
  priceMinor: z.number().int().nonnegative(),
  oldPriceMinor: z.number().int().nonnegative().optional().nullable(),
  stockQuantity: z.number().int().nonnegative().optional().default(1),
  imageUrl: z.string().optional().default(''),
  mediaUrls: z.array(z.string()).optional().default([]),
  location: z.string().optional().default(''),
  isFlashDeal: z.boolean().optional().default(false),
  discountPercent: z.number().int().min(0).max(100).optional().default(0),
});

export default async function registerProductsRoute(app: FastifyInstance) {
  const pool = getPool();

  app.get('/api/v1/products', async () => {
    return { products: await listProducts(pool) };
  });

  app.get('/api/v1/products/:id', async (request) => {
    const { id } = request.params as { id: string };
    return { product: await getProductById(pool, id) };
  });

  app.post('/api/v1/seller/products', { preHandler: requireAuth }, async (request) => {
    const seller = requireSeller(request);
    const body = newProductSchema.parse(request.body);
    const product = await createProduct(pool, seller.id, body);
    return { product };
  });

  app.get('/api/v1/seller/products', { preHandler: requireAuth }, async (request) => {
    const seller = requireSeller(request);
    return { products: await listSellerProducts(pool, seller.id) };
  });

  app.delete('/api/v1/seller/products/:id', { preHandler: requireAuth }, async (request) => {
    const seller = requireSeller(request);
    const { id } = request.params as { id: string };
    await deleteProduct(pool, seller.id, id);
    return { ok: true };
  });
}
