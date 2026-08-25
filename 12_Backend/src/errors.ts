/**
 * ScottsTechX — shared error types.
 */
export class UnauthorizedError extends Error {
  constructor(message = 'Not authenticated') {
    super(message);
    this.name = 'UnauthorizedError';
  }
}

export class ForbiddenError extends Error {
  constructor(message = 'Forbidden') {
    super(message);
    this.name = 'ForbiddenError';
  }
}

export class NotFoundError extends Error {
  constructor(message = 'Not found') {
    super(message);
    this.name = 'NotFoundError';
  }
}

export class ConflictError extends Error {
  constructor(message = 'Conflict') {
    super(message);
    this.name = 'ConflictError';
  }
}

export class ServiceUnavailableError extends Error {
  constructor(message = 'Service unavailable') {
    super(message);
    this.name = 'ServiceUnavailableError';
  }
}

/**
 * 429 — the caller is asking too often.
 *
 * `retryAfterSec` is surfaced as a Retry-After header so clients can show a
 * real countdown instead of guessing.
 */
export class TooManyRequestsError extends Error {
  retryAfterSec: number;
  constructor(message = 'Too many requests', retryAfterSec = 60) {
    super(message);
    this.name = 'TooManyRequestsError';
    this.retryAfterSec = retryAfterSec;
  }
}

/** 400 — request understood but the payload fails a business rule. */
export class ValidationError extends Error {
  constructor(message = 'Validation error') {
    super(message);
    this.name = 'ValidationError';
  }
}
