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
    try {
      done(null, JSON.parse(body.toString('utf8')));
    } catch (err) {
      done(err as Error);
    }
  });
}

/** Raw bytes of the request body (available only after installRawBodyParser). */
export function rawBodyOf(request: FastifyRequest): Buffer | undefined {
  return (request as unknown as Record<symbol, Buffer | undefined>)[RAW_BODY];
}
