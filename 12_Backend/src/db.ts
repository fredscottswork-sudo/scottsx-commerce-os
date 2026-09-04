/**
 * ScottsTechX — database bootstrap.
 *
 * - If DATABASE_URL is set, uses that managed Postgres (required on Firebase
 *   Cloud Functions — embedded Postgres only exists for local dev).
 * - Otherwise boots an embedded PostgreSQL cluster on 127.0.0.1:5433
 *   (user `app` / password `app`, database `scottstechx`), then applies
 *   every SQL file in migrations/ in alphabetical order (tracked in the
 *   schema_migrations table), then seeds the marketplace if products are
 *   empty.
 */
import pg from 'pg';
import fs from 'node:fs';
import path from 'node:path';
import { execSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const { Pool } = pg;

const __dirname = path.dirname(fileURLToPath(import.meta.url));
/** Absolute path to the 12_Backend folder (parent of src/). */
export const BACKEND_ROOT = path.resolve(__dirname, '..');

export const PG_PORT = 5433;
export const PG_DB = 'scottstechx';
export const PG_USER = 'app';
export const PG_PASSWORD = 'app';

/** True inside Firebase Cloud Functions / Cloud Run / the functions emulator. */
function isServerlessRuntime(): boolean {
  return Boolean(
    process.env.FIREBASE_CONFIG ||
      process.env.K_SERVICE ||
      process.env.FUNCTION_TARGET ||
      process.env.FUNCTIONS_EMULATOR
  );
}

function defaultDbUrl(): string {
  return `postgresql://${PG_USER}:${PG_PASSWORD}@127.0.0.1:${PG_PORT}/${PG_DB}`;
}

let pool: pg.Pool | null = null;

/** Single shared pool — tuned for speed */
export function getPool(): pg.Pool {
  if (!pool) {
    const isServerless = Boolean(
      process.env.FIREBASE_CONFIG || process.env.K_SERVICE || process.env.FUNCTION_TARGET || process.env.FUNCTIONS_EMULATOR
    );
    pool = new Pool({
      connectionString: process.env.DATABASE_URL || defaultDbUrl(),
      max: isServerless ? 10 : 20,
      min: 2,
      idleTimeoutMillis: 30000,
      connectionTimeoutMillis: 5000,
      statement_timeout: 10000,
      query_timeout: 10000,
      keepAlive: true,
    });
    pool.on('error', (err) => console.error('[db] pool error', err));
  }
  return pool;
}

/**
 * Start the embedded Postgres cluster (local dev only).
 * The module is imported lazily so the ~50MB native binary never loads on
 * serverless runtimes.
 */
async function ensureEmbeddedPostgres(): Promise<void> {
  if (process.env.DATABASE_URL) return;
  if (isServerlessRuntime()) return; // never on Cloud Functions
  if (process.env.SKIP_EMBEDDED_PG === 'true') return;

  const { default: EmbeddedPostgres } = await import('embedded-postgres');
  const dataDir = path.resolve(BACKEND_ROOT, process.env.PG_DATA_DIR || '.pgdata');
  fs.mkdirSync(dataDir, { recursive: true });

  const embedded = new EmbeddedPostgres({
    databaseDir: dataDir,
    user: PG_USER,
    password: PG_PASSWORD,
    port: PG_PORT,
    persistent: true,
    authMethod: 'password',
    onLog: (msg) => console.log(`[pg] ${msg}`),
    onError: (err) => console.error(`[pg] ${String(err)}`),
  });

  const alreadyInitialised = fs.existsSync(path.join(dataDir, 'PG_VERSION'));
  if (!alreadyInitialised) {
    console.log('[db] initialising embedded PostgreSQL cluster…');
    await embedded.initialise();
  }

  try {
    await embedded.start();
  } catch (err) {
    // Cluster may already be running (e.g. a second dev process). Probe it.
    console.warn('[db] embedded start warning (will probe port):', String(err));
  }

  // Ensure the application database exists.
  const client = embedded.getPgClient('postgres');
  await client.connect();
  try {
    const res = await client.query('SELECT 1 FROM pg_database WHERE datname = $1', [PG_DB]);
    if ((res.rowCount ?? 0) === 0) {
      console.log(`[db] creating database "${PG_DB}"…`);
      await embedded.createDatabase(PG_DB);
    }
  } finally {
    await client.end();
  }
}

/** Apply every migrations/*.sql file in alphabetical order (idempotent). */
export async function runMigrations(): Promise<void> {
  const client = await getPool().connect();
  try {
    await client.query(
      `CREATE TABLE IF NOT EXISTS schema_migrations (
         name text PRIMARY KEY,
         applied_at timestamptz NOT NULL DEFAULT now()
       )`
    );
    const migrationsDir = path.join(BACKEND_ROOT, 'migrations');
    if (!fs.existsSync(migrationsDir)) return;
    const files = fs.readdirSync(migrationsDir).filter((f) => f.endsWith('.sql')).sort();

    for (const file of files) {
      const done = await client.query('SELECT 1 FROM schema_migrations WHERE name = $1', [file]);
      if ((done.rowCount ?? 0) > 0) continue;

      const sql = fs.readFileSync(path.join(migrationsDir, file), 'utf-8');
      await client.query('BEGIN');
      try {
        await client.query(sql);
        await client.query('INSERT INTO schema_migrations (name) VALUES ($1)', [file]);
        await client.query('COMMIT');
        console.log(`[db] applied migration ${file}`);
      } catch (err) {
        await client.query('ROLLBACK');
        throw new Error(`Migration ${file} failed: ${(err as Error).message}`);
      }
    }
  } finally {
    client.release();
  }
}

/** Seed the marketplace (6 sellers, 24 products) when the DB is empty. */
export async function seedIfEmpty(): Promise<void> {
  const { rows } = await getPool().query('SELECT COUNT(*)::int AS count FROM products');
  if (Number(rows[0].count) > 0) return;
  console.log('[db] products table empty — seeding marketplace…');
  execSync('node seed_marketplace.mjs', { cwd: BACKEND_ROOT, stdio: 'inherit' });
}

/** Ensure a platform admin account exists (for the web console). */
export async function ensureAdmin(): Promise<void> {
  const { rows } = await getPool().query(`SELECT 1 FROM users WHERE role = 'admin' LIMIT 1`);
  if (rows.length > 0) return;
  const email = process.env.ADMIN_EMAIL || 'admin@scottstechx.ug';
  const password = process.env.ADMIN_PASSWORD || 'Admin123!';
  const { hashPassword } = await import('./auth.js');
  const hash = await hashPassword(password);
  await getPool().query(
    `INSERT INTO users (email, password_hash, role, display_name, email_verified)
     VALUES ($1, $2, 'admin', $3, true)
     ON CONFLICT (email) DO UPDATE SET role = 'admin', password_hash = EXCLUDED.password_hash`,
    [email, hash, 'Platform Admin']
  );
  console.log(`[db] admin account ready (${email})`);
}

/**
 * Full bootstrap: embedded PG (if needed) → migrations → seed → admin.
 * On serverless runtimes DATABASE_URL is required (managed Postgres).
 */
export async function initDatabase(): Promise<void> {
  if (isServerlessRuntime() && !process.env.DATABASE_URL) {
    throw new Error(
      'DATABASE_URL is not set — Firebase Cloud Functions needs a managed Postgres ' +
        '(Supabase / Neon / Cloud SQL). Set it with: firebase functions:secrets:set DATABASE_URL'
    );
  }
  await ensureEmbeddedPostgres();
  if (process.env.RUN_MIGRATIONS !== 'false') await runMigrations();
  if (process.env.SEED_DATABASE !== 'false') await seedIfEmpty();
  if (process.env.CREATE_ADMIN !== 'false') await ensureAdmin();
}

/** Graceful shutdown: stop pool. (Embedded cluster stops with the process.) */
export async function closeDatabase(): Promise<void> {
  if (pool) {
    await pool.end().catch(() => undefined);
    pool = null;
  }
}
