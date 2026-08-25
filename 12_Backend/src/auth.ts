/**
 * ScottsTechX — auth helpers.
 *
 * bcrypt for password hashing, `jose` for HS256 JWTs (24h expiry,
 * issuer `scottstechx`, audience `scottstechx-api`), plus Fastify
 * preHandler helpers that read the `Authorization: Bearer <jwt>` header.
 */
import bcrypt from 'bcryptjs';
import { SignJWT, jwtVerify } from 'jose';
import type { FastifyReply, FastifyRequest } from 'fastify';
import { UnauthorizedError, ForbiddenError } from './errors.js';
import { getPool } from './db.js';

const JWT_SECRET = process.env.JWT_SECRET || 'dev-secret-change-me';
const JWT_ISSUER = 'scottstechx';
const JWT_AUDIENCE = 'scottstechx-api';
const JWT_TTL = process.env.JWT_EXPIRES_IN || '24h';

const secretKey = new TextEncoder().encode(JWT_SECRET);

export interface AuthUser {
  id: string;
  email: string;
  role: 'buyer' | 'seller' | 'admin';
  name: string;
}

export function hashPassword(plain: string): Promise<string> {
  return bcrypt.hash(plain, 10);
}

export function comparePassword(plain: string, hash: string): Promise<boolean> {
  return bcrypt.compare(plain, hash);
}

export async function signJwt(payload: Record<string, unknown>): Promise<string> {
  return new SignJWT(payload)
    .setProtectedHeader({ alg: 'HS256' })
    .setIssuer(JWT_ISSUER)
    .setAudience(JWT_AUDIENCE)
    .setIssuedAt()
    .setExpirationTime(JWT_TTL)
    .sign(secretKey);
}

export async function verifyJwt(token: string): Promise<Record<string, unknown>> {
  const { payload } = await jwtVerify(token, secretKey, {
    issuer: JWT_ISSUER,
    audience: JWT_AUDIENCE,
  });
  return payload;
}

/** Build a JWT for a user row. */
export async function tokenForUser(user: {
  id: string;
  email: string;
  role: string;
  display_name?: string | null;
}): Promise<string> {
  return signJwt({
    sub: user.id,
    email: user.email,
    role: user.role,
    name: user.display_name ?? '',
  });
}

/**
 * Routes an unverified account may still call.
 *
 * Everything needed to *become* verified, or to leave. Anything that reads or
 * writes real marketplace data is deliberately absent: an address nobody has
 * proven must not be able to list products, message sellers, or place orders.
 */
const UNVERIFIED_ALLOWED = new Set([
  'POST /api/v1/auth/verify/request',
  'POST /api/v1/auth/verify/confirm',
  'POST /api/v1/auth/firebase/send-verification-email',
  'POST /api/v1/auth/firebase/sign-in',
  'GET /api/v1/auth/me',
  'GET /api/v1/auth/firebase/me',
  'POST /api/v1/auth/logout',
]);

/**
 * Verified-account cache.
 *
 * Verification is monotonic — an address goes unverified -> verified and never
 * back — so a positive can be cached for the process lifetime without ever
 * going stale. Unverified users re-check on each call, which is the cheap
 * direction: they can only reach the handful of routes above.
 *
 * The flag is read from the database rather than the JWT on purpose. Tokens
 * live 24h, so a token minted at sign-up would still claim "unverified" long
 * after the user clicked the link, and we would be gating on stale data.
 */
const verifiedUsers = new Set<string>();

/** Called after a successful verification so the next request sees it at once. */
export function markVerified(userId: string) {
  verifiedUsers.add(userId);
}

async function isEmailVerified(userId: string): Promise<boolean> {
  if (verifiedUsers.has(userId)) return true;
  const { rows } = await getPool().query(
    'SELECT email_verified FROM users WHERE id = $1',
    [userId]
  );
  // No row means the account was deleted while holding a live token.
  if (!rows[0]) return false;
  if (rows[0].email_verified) {
    verifiedUsers.add(userId);
    return true;
  }
  return false;
}

/**
 * Fastify preHandler — attach `request.user` from the bearer token, and
 * refuse the request when the account has not verified its email.
 *
 * The check lives here, at the single choke point all authenticated routes
 * share, rather than being repeated per route. A route added tomorrow is
 * protected without anyone remembering to protect it — which is the only
 * version of this that survives contact with a growing codebase.
 */
export async function requireAuth(request: FastifyRequest, reply: FastifyReply) {
  const header = request.headers.authorization;
  if (!header || !header.startsWith('Bearer ')) {
    return reply.code(401).send({ error: 'Missing bearer token' });
  }
  let user: AuthUser;
  try {
    const payload = await verifyJwt(header.slice('Bearer '.length));
    user = {
      id: String(payload.sub),
      email: String(payload.email ?? ''),
      role: (payload.role as 'buyer' | 'seller') ?? 'buyer',
      name: String(payload.name ?? ''),
    };
    (request as unknown as { user: AuthUser }).user = user;
  } catch {
    return reply.code(401).send({ error: 'Invalid or expired token' });
  }

  const routeKey = `${request.method} ${request.routeOptions?.url ?? request.url}`;
  if (UNVERIFIED_ALLOWED.has(routeKey)) return undefined;

  if (!(await isEmailVerified(user.id))) {
    // 403, not 401: the token is perfectly valid, the account simply is not
    // allowed yet. A 401 would make clients throw the session away and bounce
    // the user to the login page, losing the session they need in order to
    // verify. The code lets the web and Android clients route to the gate.
    return reply.code(403).send({
      error: 'Please verify your email address to continue',
      code: 'EMAIL_NOT_VERIFIED',
      email: user.email,
    });
  }
  return undefined;
}

/** Read the authenticated user (throws if missing). */
export function authedUser(request: FastifyRequest): AuthUser {
  const user = (request as unknown as { user?: AuthUser }).user;
  if (!user) throw new UnauthorizedError('Not authenticated');
  return user;
}

/** Read the authenticated user and require the seller role. */
export function requireSeller(request: FastifyRequest): AuthUser {
  const user = authedUser(request);
  if (user.role !== 'seller') throw new ForbiddenError('Seller role required');
  return user;
}

/** Read the authenticated user and require the platform admin role. */
export function requireAdmin(request: FastifyRequest): AuthUser {
  const user = authedUser(request);
  if (user.role !== 'admin') throw new ForbiddenError('Admin role required');
  return user;
}
