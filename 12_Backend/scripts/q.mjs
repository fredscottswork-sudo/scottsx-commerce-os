#!/usr/bin/env node
/** Tiny psql substitute: `node scripts/q.mjs "select 1"` (embedded PG has no psql binary). */
import pg from 'pg';
const pool = new pg.Pool({
  connectionString: process.env.DATABASE_URL || 'postgresql://app:app@127.0.0.1:5433/scottstechx',
});
const sql = process.argv.slice(2).join(' ');
if (!sql) {
  console.error('usage: node scripts/q.mjs "<sql>"');
  process.exit(1);
}
try {
  const res = await pool.query(sql);
  console.log(JSON.stringify(res.rows, null, 2));
} catch (err) {
  console.error('ERROR:', err.message);
  process.exitCode = 1;
} finally {
  await pool.end();
}
