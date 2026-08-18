/**
 * ScottsTechX — CMS pages.
 *
 *   GET /api/v1/cms/:slug   (terms | privacy | buyer-protection | about)
 *
 * The "about" body is seeded to include the founder bio:
 *   "Kato Fred, Ugandan cybersecurity analyst, web dev and software dev."
 */
import type { FastifyInstance } from 'fastify';
import { getPool } from '../../db.js';
import { NotFoundError } from '../../errors.js';

const KNOWN_SLUGS = new Set(['terms', 'privacy', 'buyer-protection', 'about']);

export default async function registerCmsRoute(app: FastifyInstance) {
  const pool = getPool();

  app.get('/api/v1/cms/:slug', async (request) => {
    const { slug } = request.params as { slug: string };
    if (!KNOWN_SLUGS.has(slug)) throw new NotFoundError('Page not found');
    const { rows } = await pool.query(
      'SELECT slug, title, body, updated_at AS "updatedAt" FROM cms_pages WHERE slug = $1',
      [slug]
    );
    if (!rows[0]) throw new NotFoundError('Page not found');
    return { page: rows[0] };
  });
}
