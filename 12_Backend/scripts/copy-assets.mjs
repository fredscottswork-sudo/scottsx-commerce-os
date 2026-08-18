/**
 * Copy non-TypeScript runtime assets into dist/.
 *
 * `tsc` only emits .js, so the offline gazetteer binary (and anything else the
 * server reads from disk) has to be copied explicitly. Without this the geo
 * endpoints fall back to a stale path and quietly stop resolving place names
 * on a deployed host where only dist/ is shipped.
 */
import { copyFileSync, mkdirSync, existsSync } from 'node:fs';
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
console.log(`[copy-assets] ${copied} asset(s) copied`);
