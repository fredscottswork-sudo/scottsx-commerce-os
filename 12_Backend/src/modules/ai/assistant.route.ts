/**
 * ScottsTechX — AI assistant routes.
 *
 *   POST /api/v1/ai/v2/ask               { prompt, screen, history? }
 *   POST /api/v1/ai/v2/generate-product  { imageUrl?, hint }
 */
import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { askAi, offlineFallback, heuristicGenerateProduct, aiConfigured } from './assistant.service.js';

const askSchema = z.object({
  prompt: z.string().min(1),
  screen: z.string().optional().default('generic'),
  history: z
    .array(z.object({ role: z.enum(['user', 'assistant']), content: z.string() }))
    .optional()
    .default([]),
});

const generateSchema = z.object({
  imageUrl: z.string().optional().default(''),
  hint: z.string().optional().default(''),
});

export default async function registerAiRoute(app: FastifyInstance) {
  app.get('/api/v1/ai/status', async () => ({
    configured: aiConfigured(),
    provider: process.env.AI_PROVIDER || 'openrouter',
    model: process.env.AI_MODEL || 'meta-llama/llama-3.3-70b-instruct',
  }));

  app.post('/api/v1/ai/v2/ask', async (request) => {
    const body = askSchema.parse(request.body);
    try {
      const result = await askAi({ prompt: body.prompt, screen: body.screen, history: body.history });
      return result;
    } catch (err: any) {
      if (err?.name === 'ServiceUnavailableError' && !aiConfigured()) {
        // Deterministic offline fallback keeps the flow testable without a key.
        return offlineFallback(body.prompt, body.screen);
      }
      throw err;
    }
  });

  app.post('/api/v1/ai/v2/generate-product', async (request) => {
    const body = generateSchema.parse(request.body);
    return heuristicGenerateProduct(body.imageUrl, body.hint);
  });
}
