/**
 * ScottsTechX — LOCAL dev server entry.
 *
 *   - loads .env
 *   - boots embedded PostgreSQL (or uses DATABASE_URL)
 *   - runs migrations + seeds the marketplace when empty
 *   - serves the app on 0.0.0.0:3001
 *
 * For Firebase Cloud Functions, see src/firebase-entry.ts.
 */
import 'dotenv/config';
import { buildApp } from './app.js';
import { initDatabase, closeDatabase, getPool } from './db.js';
import { mailConfigured, devCodesAllowed, mailSummary } from './mail.js';
import { roboflowConfigured } from './modules/vision/roboflow.service.js';
import { nvidiaVisionConfigured } from './modules/ai/assistant.service.js';

async function main() {
  await initDatabase();
  const app = await buildApp();
  const pool = getPool();

  const port = Number(process.env.PORT || 3001);
  await app.listen({ port, host: '0.0.0.0' });
  console.log(`[server] ScottsTechX API listening on http://0.0.0.0:${port}`);

  // Boot-time visibility for the vision stack. The key itself is NEVER logged;
  // only whether the workflow client is armed, so a misconfigured Render env
  // shows up in logs without exposing the secret.
  console.log(
    roboflowConfigured()
      ? '[server] Roboflow vision: ENABLED (workflow ready)'
      : '[server] Roboflow vision: disabled — no ROBOFLOW_API_KEY set'
  );
  console.log(
    nvidiaVisionConfigured()
      ? '[server] NVIDIA vision captions: ENABLED'
      : '[server] NVIDIA vision captions: disabled — no NVIDIA_API_KEY set'
  );

  // Say plainly which verification mode this server is in. A silent fallback
  // is how "anyone can verify any address" ships unnoticed.
  if (mailConfigured()) {
    console.log(`[server] email verification: ${mailSummary().transport} configured — codes are emailed`);
  } else if (devCodesAllowed()) {
    console.warn(
      '[server] email verification: NO MAILER — codes are returned in API responses.\n' +
        '         This is for local development only. Anyone who calls the API can\n' +
        '         verify any address. Set SMTP_HOST/SMTP_USER/SMTP_PASS before deploying.'
    );
  } else {
    console.error(
      '[server] email verification: NO MAILER in production — email/password sign-up is\n' +
        '         DISABLED (Google sign-in still works). Set SMTP_HOST/SMTP_USER/SMTP_PASS,\n' +
        '         or set ALLOW_DEV_VERIFICATION_CODES=true if this server is not public.'
    );
  }

  const shutdown = async (signal: string) => {
    console.log(`[server] ${signal} received — shutting down`);
    await app.close().catch(() => undefined);
    await closeDatabase();
    process.exit(0);
  };
  process.on('SIGINT', () => void shutdown('SIGINT'));
  process.on('SIGTERM', () => void shutdown('SIGTERM'));

  void pool;
}

main().catch((err) => {
  console.error('[server] fatal startup error:', err);
  process.exit(1);
});
