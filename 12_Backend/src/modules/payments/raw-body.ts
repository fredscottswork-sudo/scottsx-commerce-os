/**
 * ScottsTechX — raw request body capture.
 *
 * Webhook signature verification (Nylon Pay) must run against the RAW bytes of
 * the request, not re-serialized JSON. This installs a JSON content-type
 * parser that stashes the original buffer on the request while still parsing
 * normally, so every other route is unaffected.
 */
import type { FastifyInstance, FastifyRequest } from 'fastify';

const RAW_BODY = Symbol('rawBody');

/** Install the raw-body-stashing JSON parser. Call before registering routes. */
export function installRawBodyParser(app: FastifyInstance) {
  app.addContentTypeParser('application/json', { parseAs: 'buffer' }, (request, body: Buffer, done) => {
    (request as unknown as Record<symbol, Buffer | undefined>)[RAW_BODY] = body;

    // A POST/DELETE that declares application/json but sends no body is a
    // normal client pattern (e.g. `fetch(url, { method: 'POST', headers })`).
    // Fastify's built-in parser yields `undefined` for that; match it instead
    // of throwing "Unexpected end of JSON input".
    const text = body.toString('utf8').trim();
    if (text.length === 0) {
      done(null, undefined);
      return;
    }

    try {
      done(null, JSON.parse(text));
    } catch (err) {
      done(err as Error);
    }
  });
}

/** Raw bytes of the request body (available only after installRawBodyParser). */
export function rawBodyOf(request: FastifyRequest): Buffer | undefined {
  return (request as unknown as Record<symbol, Buffer | undefined>)[RAW_BODY];
}
