/**
 * Backwards compatibility shim — the canonical implementation now lives in
 * images.route.ts (merged old + new). This file re-exports it so either import
 * path works.
 */
export { default } from './images.route.js';
export * from './images.route.js';
