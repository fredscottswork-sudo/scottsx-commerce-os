/**
 * Copy non-TypeScript runtime assets into dist/.
 *
 * `tsc` only emits .js, so the offline gazetteer binary (and anything else the
 * server reads from disk) has to be copied explicitly. Without this the geo
 * endpoints fall back to a stale path and quietly stop resolving place names
 * on a deployed host where only dist/ is shipped.
 */
import { copyFileSync, mkdirSync, existsSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = [['src/geo/gazetteer.bin', 'dist/geo/gazetteer.bin']];

let copied = 0;
for (const [from, to] of assets) {
  const src = path.join(root, from);
  if (!existsSync(src)) {
    console.error(`[copy-assets] MISSING ${from} — the build is incomplete`);
    process.exit(1);
  }
  const dest = path.join(root, to);
  mkdirSync(path.dirname(dest), { recursive: true });
  copyFileSync(src, dest);
  copied++;
  console.log(`[copy-assets] ${from} -> ${to}`);
}

// Firebase runs the compiled entry from dist/. The database bootstrap resolves
// migrations relative to that directory, so leaving SQL files at the source
// root makes production silently run an old schema (and makes new additive
// migrations such as order delivery fields never apply).
const migrationsDir = path.join(root, 'migrations');
if (!existsSync(migrationsDir)) {
  console.error('[copy-assets] MISSING migrations/ — the build is incomplete');
  process.exit(1);
}
const distMigrations = path.join(root, 'dist', 'migrations');
mkdirSync(distMigrations, { recursive: true });
for (const file of readdirSync(migrationsDir).filter((name) => name.endsWith('.sql'))) {
  copyFileSync(path.join(migrationsDir, file), path.join(distMigrations, file));
  copied++;
  console.log(`[copy-assets] migrations/${file} -> dist/migrations/${file}`);
}
console.log(`[copy-assets] ${copied} asset(s) copied`);
