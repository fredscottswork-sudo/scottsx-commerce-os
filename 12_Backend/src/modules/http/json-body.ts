/**
 * ScottsTechX — JSON body parsing.
 *
 * A POST/DELETE that declares `application/json` but sends no body is a normal
 * client pattern (e.g. `fetch(url, { method: 'DELETE', headers })`). Fastify's
 * built-in parser throws "Body cannot be empty when content-type is set to
 * 'application/json'" in that case, so we install a tolerant JSON parser that
 * yields `undefined` for an empty body and parses everything else as JSON.
 */
import type { FastifyInstance } from 'fastify';

/** Install the empty-body-tolerant JSON parser. Call before registering routes. */
export function installJsonParser(app: FastifyInstance) {
  app.addContentTypeParser('application/json', { parseAs: 'buffer' }, (_request, body: Buffer, done) => {
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
