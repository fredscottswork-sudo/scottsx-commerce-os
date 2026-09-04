/**
 * ScottsTechX — AI routes.
 *
 *   GET  /api/v1/ai/status
 *   GET  /api/v1/ai/agents
 *   POST /api/v1/ai/v2/ask                { prompt, screen?, agent?, history? }
 *   POST /api/v1/ai/v2/generate-product   { imageUrl?, hint }
 *   POST /api/v1/ai/search                { q }              natural-language search
 *   POST /api/v1/ai/image-search          { imageUrl?, imageData?, hint?, labels? }
 *   POST /api/v1/ai/image-upload-search   multipart: image + hint?  (public — no login)
 *   POST /api/v1/ai/voice-search          { transcript }
 */
import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { getPool } from '../../db.js';
import { verifyJwt } from '../../auth.js';
import { ask, generateProduct, aiSearch, imageSearch, aiConfigured, AGENTS } from './assistant.service.js';

const askSchema = z.object({
  prompt: z.string().min(1),
  screen: z.string().optional().default('generic'),
  agent: z.string().optional(),
  history: z
    .array(z.object({ role: z.enum(['user', 'assistant']), content: z.string() }))
    .optional()
    .default([]),
});

const generateSchema = z.object({
  imageUrl: z.string().optional().default(''),
  hint: z.string().optional().default(''),
});

/** Soft auth: identify the caller when a token is present, never reject. */
async function softUser(request: any): Promise<{ id?: string; role: 'buyer' | 'seller' | 'admin' }> {
  const header = request.headers?.authorization;
  if (!header?.startsWith('Bearer ')) return { role: 'buyer' };
  try {
    const payload = await verifyJwt(header.slice(7));
    return { id: String(payload.sub), role: (payload.role as any) ?? 'buyer' };
  } catch {
    return { role: 'buyer' };
  }
}

export default async function registerAiRoute(app: FastifyInstance) {
  const pool = getPool();

  app.get('/api/v1/ai/status', async () => ({
    configured: aiConfigured(),
    provider: aiConfigured() ? process.env.AI_PROVIDER || 'openrouter' : 'scottstechx-local',
    model: aiConfigured()
      ? process.env.AI_MODEL || 'meta-llama/llama-3.3-70b-instruct'
      : 'catalog-grounded',
    grounded: true,
    capabilities: {
      chat: true,
      agents: true,
      search: true,
      imageSearch: true,
      voiceSearch: true,
      listingGeneration: true,
      vision: aiConfigured(),
    },
  }));

  app.get('/api/v1/ai/agents', async () => ({ agents: AGENTS }));

  app.post('/api/v1/ai/v2/ask', async (request) => {
    const body = askSchema.parse(request.body);
    const who = await softUser(request);
    const result = await ask({
      db: pool,
      prompt: body.prompt,
      screen: body.screen,
      agent: body.agent,
      role: who.role,
      userId: who.id,
      history: body.history,
    });
    return result;
  });

  app.post('/api/v1/ai/v2/generate-product', async (request) => {
    const body = generateSchema.parse(request.body);
    return generateProduct(pool, { imageUrl: body.imageUrl, hint: body.hint });
  });

  app.post('/api/v1/ai/search', async (request) => {
    const body = z
      .object({ q: z.string().min(1), limit: z.coerce.number().int().min(1).max(60).optional().default(24) })
      .parse(request.body);
    const who = await softUser(request);
    const result = await aiSearch(pool, body.q, body.limit);
    if (who.id) {
      pool
        .query('INSERT INTO search_history (user_id, query, mode, results) VALUES ($1,$2,$3,$4)', [
          who.id,
          body.q,
          'ai',
          result.products.length,
        ])
        .catch(() => undefined);
    }
    return result;
  });

  app.post('/api/v1/ai/image-search', async (request) => {
    const body = z
      .object({
        imageUrl: z.string().optional(),
        imageData: z.string().optional(),
        hint: z.string().optional(),
        labels: z.array(z.string()).optional(),
        limit: z.coerce.number().int().min(1).max(60).optional().default(24),
      })
      .parse(request.body);
    const who = await softUser(request);
    const result = await imageSearch(pool, body, body.limit);
    if (who.id) {
      pool
        .query('INSERT INTO search_history (user_id, query, mode, results) VALUES ($1,$2,$3,$4)', [
          who.id,
          result.detected || 'image',
          'image',
          result.products.length,
        ])
        .catch(() => undefined);
    }
    return result;
  });

  /**
   * Public image search with a real upload — no login required, so a guest
   * can photograph something in a shop and find it on the marketplace.
   *
   *   multipart/form-data:
   *     image  (file, jpeg/png/webp, ≤ 8 MB — the client compresses first)
   *     hint   (optional — "red Nike trainers" sharpens the match a lot)
   *
   * The buffer is passed to the vision model as a data URL when a key is
   * configured; without one we rely on the hint + filename-derived labels.
   * Nothing is persisted, so this is safe for anonymous visitors.
   */
  app.post('/api/v1/ai/image-upload-search', async (request, reply) => {
    const file = await request.file();
    if (!file) {
      return reply.code(400).send({ error: 'No image uploaded (field "image")' });
    }
    const ALLOWED = new Set(['image/jpeg', 'image/png', 'image/webp']);
    if (!ALLOWED.has(file.mimetype)) {
      return reply
        .code(400)
        .send({ error: `Unsupported type ${file.mimetype} — use JPEG, PNG or WEBP` });
    }
    const buffer = await file.toBuffer();
    if (buffer.length === 0) return reply.code(400).send({ error: 'The file is empty' });

    const fields = (file.fields ?? {}) as Record<string, { value?: unknown } | undefined>;
    const hint = String(fields.hint?.value ?? '').trim().slice(0, 200);
    // A filename like "nike-air-max.jpg" is free search signal, even without a
    // model: turn it into terms instead of throwing the photo away.
    const filenameTerms = (file.filename || '')
      .replace(/\.[a-z0-9]+$/i, '')
      .replace(/[-_+]+/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
    const labels = filenameTerms ? [filenameTerms] : [];

    const imageData = `data:${file.mimetype};base64,${buffer.toString('base64')}`;
    const result = await imageSearch(pool, { imageData, hint, labels }, 24);
    const who = await softUser(request);
    if (who.id) {
      pool
        .query('INSERT INTO search_history (user_id, query, mode, results) VALUES ($1,$2,$3,$4)', [
          who.id,
          result.detected || filenameTerms || 'image',
          'image',
          result.products.length,
        ])
        .catch(() => undefined);
    }
    return result;
  });

  app.post('/api/v1/ai/voice-search', async (request) => {
    // The client does speech-to-text on-device (Android SpeechRecognizer /
    // Web Speech API) and sends the transcript here.
    const body = z
      .object({ transcript: z.string().min(1), limit: z.coerce.number().int().min(1).max(60).optional().default(24) })
      .parse(request.body);
    const who = await softUser(request);
    const result = await aiSearch(pool, body.transcript, body.limit);
    if (who.id) {
      pool
        .query('INSERT INTO search_history (user_id, query, mode, results) VALUES ($1,$2,$3,$4)', [
          who.id,
          body.transcript,
          'voice',
          result.products.length,
        ])
        .catch(() => undefined);
    }
    return { ...result, transcript: body.transcript };
  });
}
