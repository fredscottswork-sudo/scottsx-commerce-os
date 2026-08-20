#!/usr/bin/env node
/**
 * AI quality gate.
 *
 * The AI could return HTTP 200 with a fluent, confident, wrong answer, and
 * every existing test would pass. These assertions are about the CONTENT:
 * does it find what exists, stay quiet about what doesn't, and rank sensibly?
 *
 * Every case here is a bug that was actually shipped and is now fixed:
 *   - "cheapest phone" led with a pair of headphones (substring + price sort)
 *   - "do you sell cars" answered with skincare (%car% matched "SkinCARE")
 *   - "what deals are on today" found nothing while six deals were running
 *   - "hi" dumped eight random products at someone saying hello
 *   - "laptops under 2m" was a dead end instead of showing the nearest option
 *
 * Usage: node tests/ai-quality.mjs   (API must be running)
 */
const API = process.env.API_BASE || 'http://127.0.0.1:3001';
const G = (s) => `\x1b[32m${s}\x1b[0m`;
const R = (s) => `\x1b[31m${s}\x1b[0m`;
const B = (s) => `\x1b[1m${s}\x1b[0m`;
const D = (s) => `\x1b[2m${s}\x1b[0m`;

let passed = 0;
const failures = [];

async function ask(prompt, extra = {}) {
  const r = await fetch(`${API}/api/v1/ai/v2/ask`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ prompt, ...extra }),
  });
  const body = await r.json();
  if (!r.ok) throw new Error(`${r.status} ${JSON.stringify(body).slice(0, 200)}`);
  return body;
}

function check(name, condition, detail) {
  if (condition) { passed += 1; console.log(`  ${G('✓')} ${name}`); }
  else { failures.push({ name, detail }); console.log(`  ${R('✗')} ${name}`); if (detail) console.log(`      ${D(detail)}`); }
}

console.log(B('\nAI quality — is the answer actually right?\n'));

console.log(B('1. Ranking: "cheapest X" means the cheapest X'));
{
  const r = await ask('cheapest phone');
  const first = (r.products || [])[0];
  const titles = (r.products || []).map((p) => p.title).join(' | ');
  check('the first result is a phone, not a headphone',
    first && /iphone|galaxy|pixel|tecno|infinix|redmi/i.test(first.title),
    `got: ${titles}`);
  check('no headphones in a phone query',
    !/headphone/i.test(titles), `got: ${titles}`);
  const prices = (r.products || []).map((p) => p.priceMinor);
  check('results ascend by price', prices.every((v, i) => i === 0 || prices[i - 1] <= v), prices.join(', '));
}

console.log(B('\n2. Precision: no answers about things the store does not sell'));
{
  const r = await ask('do you sell cars');
  const titles = (r.products || []).map((p) => p.title).join(' | ');
  check('"cars" does not match "SkinCARE"', !/skincare|cleansing/i.test(titles), `got: ${titles}`);
  check('an honest "nothing found" rather than unrelated products',
    (r.products || []).length === 0, `got ${(r.products || []).length}: ${titles}`);
}

console.log(B('\n3. Recall: intent words must not be searched as product words'));
{
  const r = await ask('what deals are on today');
  check('flash deals are found', (r.products || []).length > 0, r.text?.slice(0, 120));
  check('every result really is a deal',
    (r.products || []).every((p) => p.isFlashDeal),
    (r.products || []).map((p) => `${p.title}:${p.isFlashDeal}`).join(', '));
}
{
  const r = await ask('show me the best rated electronics');
  check('"best rated electronics" returns electronics',
    (r.products || []).length > 0 && (r.products || []).every((p) => /electronic/i.test(p.category)),
    (r.products || []).map((p) => p.category).join(', '));
}

console.log(B('\n4. Conversation: a greeting is not a search'));
{
  const r = await ask('hi');
  check('no products dumped at a greeting', (r.products || []).length === 0, `${(r.products || []).length} products`);
  check('greets back', /hi!|hello/i.test(r.text || ''), r.text?.slice(0, 80));
  check('offers concrete examples to ask', /ask me|try/i.test(r.text || ''), r.text?.slice(0, 120));
}

console.log(B('\n5. Helpful failure: show the nearest option, not a dead end'));
{
  const r = await ask('laptops under 2 million shillings');
  check('says nothing matched exactly', /nothing matched|couldn't find/i.test(r.text || ''), r.text?.slice(0, 100));
  check('still shows the closest real product', /macbook/i.test(r.text || ''), r.text?.slice(0, 200));
  check('explains the budget constraint', /budget/i.test(r.text || ''), r.text?.slice(0, 200));
}

console.log(B('\n6. Grounding: never invent a product'));
{
  const r = await ask('asdkjhasd');
  check('nonsense returns no products', (r.products || []).length === 0);
  check('nonsense is admitted, not answered', /couldn't find|nothing matched/i.test(r.text || ''), r.text?.slice(0, 100));
}
{
  // Everything quoted back must exist in the catalogue.
  const live = await fetch(`${API}/api/v1/products?limit=100`).then((x) => x.json());
  const known = new Set((live.items || live.products || []).map((p) => p.title));
  const r = await ask('show me everything you have');
  const invented = (r.products || []).filter((p) => !known.has(p.title));
  check('every product returned exists in the catalogue', invented.length === 0,
    invented.map((p) => p.title).join(', '));
}

console.log(B('\n7. Support questions are answered, not searched'));
{
  const r = await ask('how do I return an order');
  check('routed to the support agent', r.agent?.id === 'support', `agent: ${r.agent?.id}`);
  check('gives actual return steps', /refund|return/i.test(r.text || ''), r.text?.slice(0, 100));
}

console.log(B('\n8. Approval gate: unapproved products stay invisible'));
{
  const r = await ask('show me everything you have');
  const bad = (r.products || []).filter((p) => p.status && p.status !== 'approved');
  check('no pending or rejected products surface', bad.length === 0, bad.map((p) => p.title).join(', '));
}

console.log(B('\nSummary'));
console.log(`  ${G(`${passed} passed`)}${failures.length ? `, ${R(`${failures.length} failed`)}` : ''}`);
if (failures.length) {
  console.log('');
  for (const f of failures) console.log(`  ${R('✗')} ${f.name}${f.detail ? `\n      ${D(f.detail)}` : ''}`);
  process.exit(1);
}
console.log(`\n${G(B('The AI answers correctly'))}\n`);
