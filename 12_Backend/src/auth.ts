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

/** Fastify preHandler — attach `request.user` from the bearer token. */
export async function requireAuth(request: FastifyRequest, reply: FastifyReply) {
  const header = request.headers.authorization;
  if (!header || !header.startsWith('Bearer ')) {
    return reply.code(401).send({ error: 'Missing bearer token' });
  }
  try {
    const payload = await verifyJwt(header.slice('Bearer '.length));
    (request as unknown as { user: AuthUser }).user = {
      id: String(payload.sub),
      email: String(payload.email ?? ''),
      role: (payload.role as 'buyer' | 'seller') ?? 'buyer',
      name: String(payload.name ?? ''),
    };
  } catch {
    return reply.code(401).send({ error: 'Invalid or expired token' });
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
