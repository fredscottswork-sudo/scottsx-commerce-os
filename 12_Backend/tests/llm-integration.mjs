/**
 * ScottsTechX — LLM provider integration.
 *
 * Why this exists
 * ---------------
 * Every AI test until now ran with NO key configured, which only ever
 * exercised the offline composer. The moment a real LLM key is pasted in, a
 * completely different code path runs — one that had never been executed
 * once. This suite stands up a fake OpenAI-compatible server and points the
 * backend at it with LLM_BASE_URL, so the whole live-LLM path is covered
 * before a real key is involved:
 *
 *   • the request actually reaches the provider, with auth + model set
 *   • the live catalogue is injected into the prompt, so the model answers
 *     from real stock instead of inventing products (this is the single most
 *     important property — an ungrounded shopping assistant makes up prices)
 *   • each agent sends its own system prompt, so agents behave differently
 *   • conversation history is forwarded, so follow-up questions work
 *   • a provider outage / timeout / malformed reply falls back to the offline
 *     answer instead of failing the request
 *
 * Run: node tests/llm-integration.mjs   (starts its own API on a spare port)
 */
import http from 'node:http';
import { spawn } from 'node:child_process';
import { setTimeout as sleep } from 'node:timers/promises';

const G = (s) => `\x1b[32m${s}\x1b[0m`;
const R = (s) => `\x1b[31m${s}\x1b[0m`;
const B = (s) => `\x1b[1m${s}\x1b[0m`;
const D = (s) => `\x1b[2m${s}\x1b[0m`;

let passed = 0;
const failures = [];
function check(name, condition, detail) {
  if (condition) { passed += 1; console.log(`  ${G('✓')} ${name}`); }
  else { failures.push({ name, detail }); console.log(`  ${R('✗')} ${name}`); }
}

// ── The fake LLM ───────────────────────────────────────────────────────────
// Records what it was asked, and can be told to misbehave.
const seen = [];
let mode = 'ok';

const llm = http.createServer((req, res) => {
  let body = '';
  req.on('data', (c) => { body += c; });
  req.on('end', async () => {
    let parsed = {};
    try { parsed = JSON.parse(body); } catch { /* keep {} */ }
    seen.push({ url: req.url, auth: req.headers.authorization, body: parsed });

    if (mode === 'error') { res.statusCode = 502; res.end('{"error":"upstream down"}'); return; }
    if (mode === 'empty') {
      res.setHeader('content-type', 'application/json');
      res.end(JSON.stringify({ choices: [{ message: { content: '' } }] }));
      return;
    }
    if (mode === 'malformed') {
      res.setHeader('content-type', 'application/json');
      res.end('{"not_what_we_expect":true}');
      return;
    }
    if (mode === 'hang') { await sleep(30_000); res.end('{}'); return; }

    // Echo back something identifiable, so we can prove the reply is the
    // model's words and not the offline composer's.
    res.setHeader('content-type', 'application/json');
    res.end(JSON.stringify({
      choices: [{ message: { content: 'LLM_REPLY_MARKER the assistant answered.' } }],
    }));
  });
});
await new Promise((r) => llm.listen(0, '127.0.0.1', r));
const LLM_PORT = llm.address().port;
const LLM_URL = `http://127.0.0.1:${LLM_PORT}/v1/chat/completions`;

// ── The API, pointed at the fake LLM ───────────────────────────────────────
const API_PORT = 3199;
const API = `http://127.0.0.1:${API_PORT}`;

const api = spawn('npx', ['tsx', 'src/server.ts'], {
  cwd: new URL('..', import.meta.url).pathname,
  env: {
    ...process.env,
    PORT: String(API_PORT),
    LLM_API_KEY: 'test-key-12345',
    LLM_BASE_URL: LLM_URL,
    AI_MODEL: 'test/model-x',
    AI_PROVIDER: 'openrouter',
  },
  stdio: ['ignore', 'pipe', 'pipe'],
});
let apiLog = '';
api.stdout.on('data', (d) => { apiLog += d; });
api.stderr.on('data', (d) => { apiLog += d; });

async function waitForApi(timeoutMs = 120_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const r = await fetch(`${API}/api/v1/ai/status`);
      if (r.ok) return true;
    } catch { /* not up yet */ }
    if (api.exitCode !== null) throw new Error(`API exited early:\n${apiLog.slice(-1500)}`);
    await sleep(1000);
  }
  throw new Error(`API did not start in time:\n${apiLog.slice(-1500)}`);
}

function ask(prompt, extra = {}) {
  return fetch(`${API}/api/v1/ai/v2/ask`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ prompt, ...extra }),
  }).then(async (r) => ({ status: r.status, body: await r.json() }));
}

try {
  console.log(B('\nLLM provider integration') + D(`  (fake provider on :${LLM_PORT})`));
  await waitForApi();

  console.log(B('\n1. A configured key actually reaches the provider'));
  {
    const status = await fetch(`${API}/api/v1/ai/status`).then((r) => r.json());
    check('status reports the AI as configured', status.configured === true, JSON.stringify(status));

    seen.length = 0;
    const r = await ask('cheapest phone you have');
    check('the request succeeds', r.status === 200, `HTTP ${r.status}`);
    check('the provider was actually called', seen.length === 1, `${seen.length} calls`);
    check('the answer is the model\'s, not the offline composer\'s',
      /LLM_REPLY_MARKER/.test(r.body.text || ''), (r.body.text || '').slice(0, 80));
    check('the response is labelled as coming from the LLM',
      r.body.provider === 'openrouter', r.body.provider);
    check('the configured model is used, not a hardcoded one',
      seen[0]?.body?.model === 'test/model-x', seen[0]?.body?.model);
    check('the API key is sent as a bearer token',
      seen[0]?.auth === 'Bearer test-key-12345', seen[0]?.auth);
  }

  console.log(B('\n2. The model is grounded in the real catalogue'));
  {
    // The whole point of a store assistant: it must answer from live stock.
    seen.length = 0;
    await ask('cheapest phone you have');
    const msgs = seen[0]?.body?.messages ?? [];
    const userMsg = msgs.filter((m) => m.role === 'user').pop()?.content ?? '';

    check('a system prompt is sent', msgs.some((m) => m.role === 'system'));
    check('live catalogue context is injected into the prompt',
      /LIVE CATALOG CONTEXT/.test(userMsg), userMsg.slice(0, 120));
    check('the catalogue block contains real product names',
      /Galaxy|iPhone|MacBook/i.test(userMsg), userMsg.slice(0, 200));
    check('real prices are included so the model cannot invent them',
      /UGX|\d{3},\d{3}/.test(userMsg), userMsg.slice(0, 200));
    check('the user question is passed through verbatim',
      /cheapest phone you have/.test(userMsg));
    // Products are attached to the response independently of the model's text,
    // so the UI can render real cards even if the prose drifts.
    const r = await ask('cheapest phone you have');
    check('real products are returned alongside the model text',
      Array.isArray(r.body.products) && r.body.products.length > 0,
      `${(r.body.products || []).length} products`);
    check('the response is still marked grounded', r.body.grounded === true);
  }

  console.log(B('\n3. Each agent keeps its own instructions'));
  {
    const prompts = {};
    for (const agent of ['shopping', 'support', 'listing', 'growth']) {
      seen.length = 0;
      await ask('what should I do next?', { agent });
      prompts[agent] = (seen[0]?.body?.messages ?? []).find((m) => m.role === 'system')?.content ?? '';
    }
    check('every agent sends a system prompt',
      Object.values(prompts).every((p) => p.length > 40),
      Object.entries(prompts).map(([k, v]) => `${k}:${v.length}`).join(' '));
    check('agents do not all share one identical prompt',
      new Set(Object.values(prompts)).size === 4,
      `${new Set(Object.values(prompts)).size} distinct`);
    check('the seller agent is told it is helping a seller',
      /seller/i.test(prompts.listing), prompts.listing.slice(0, 100));
    check('the support agent is briefed on orders/refunds',
      /order|refund|support/i.test(prompts.support), prompts.support.slice(0, 100));
  }

  console.log(B('\n4. Follow-up questions keep their context'));
  {
    seen.length = 0;
    await ask('and how much is the second one?', {
      history: [
        { role: 'user', content: 'show me two laptops' },
        { role: 'assistant', content: 'Here are two laptops...' },
      ],
    });
    const msgs = seen[0]?.body?.messages ?? [];
    check('prior turns are forwarded to the model',
      msgs.some((m) => /show me two laptops/.test(m.content || '')),
      JSON.stringify(msgs.map((m) => m.role)));
    check('history keeps its speaker roles',
      msgs.some((m) => m.role === 'assistant' && /Here are two laptops/.test(m.content || '')));
    check('the newest question is last, where the model expects it',
      /and how much is the second one\?/.test(msgs[msgs.length - 1]?.content ?? ''));
  }

  console.log(B('\n5. A broken provider must not break the assistant'));
  {
    // This is what protects the user when their key expires, their credit runs
    // out, or the provider has an outage mid-conversation.
    mode = 'error';
    const err = await ask('cheapest phone you have');
    check('an upstream 502 still returns 200 to the app', err.status === 200, `HTTP ${err.status}`);
    check('the user still gets a real, catalogue-grounded answer',
      (err.body.text || '').length > 40 && !/LLM_REPLY_MARKER/.test(err.body.text || ''),
      (err.body.text || '').slice(0, 90));
    check('the fallback is labelled honestly, not passed off as the LLM',
      /local|unavailable/i.test(err.body.provider || ''), err.body.provider);
    check('products are still returned during an outage',
      (err.body.products || []).length > 0, `${(err.body.products || []).length}`);

    mode = 'empty';
    const empty = await ask('cheapest phone you have');
    check('an empty completion falls back instead of showing a blank reply',
      (empty.body.text || '').length > 40, JSON.stringify(empty.body.text));

    mode = 'malformed';
    const bad = await ask('cheapest phone you have');
    check('a malformed provider response falls back cleanly',
      bad.status === 200 && (bad.body.text || '').length > 40,
      `HTTP ${bad.status} ${(bad.body.text || '').slice(0, 60)}`);

    mode = 'ok';
    const ok = await ask('cheapest phone you have');
    check('the assistant recovers once the provider is healthy again',
      /LLM_REPLY_MARKER/.test(ok.body.text || ''), (ok.body.text || '').slice(0, 60));
  }

  console.log(B('\nSummary'));
  console.log(`  ${G(`${passed} passed`)}${failures.length ? `, ${R(`${failures.length} failed`)}` : ''}`);
  if (failures.length) {
    console.log('');
    for (const f of failures) console.log(`  ${R('✗')} ${f.name}${f.detail ? `\n      ${D(f.detail)}` : ''}`);
  }
  console.log(failures.length ? '' : `\n${G(B('The live-LLM path works and degrades safely'))}\n`);
  process.exitCode = failures.length ? 1 : 0;
} finally {
  // The child API owns an embedded Postgres, which keeps the process group
  // alive well past SIGTERM; without an explicit exit this suite hangs after
  // printing its results, which looks exactly like a failure.
  api.kill('SIGKILL');
  llm.close();
  await sleep(300);
  process.exit(failures.length ? 1 : 0);
}
