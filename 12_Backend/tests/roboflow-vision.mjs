/**
 * ScottsTechX — Roboflow vision integration test.
 *
 * Runs against the LIVE backend on :3001. A local stub workflow HTTP server
 * (127.0.0.1:9701) impersonates serverless.roboflow.com so no real API key or
 * model is needed. The stub decides based on a keyword in the image URL:
 *
 *   .../approved.jpg            -> approved (listing publishes)
 *   .../review.jpg              -> manual_review (admin queue)
 *   .../rejected.jpg            -> rejected + reasons (blocked)
 *   .../blurry.jpg              -> needs_better_image (blocked, new photo)
 *   .../embed.jpg               -> approved + visual_search_embedding
 *
 * Requires ROBOFLOW_API_KEY + ROBOFLOW_WORKFLOW_URL to point at the stub —
 * see scripts/roboflow-stub.mjs and the .env notes. When the live server is
 * started without Roboflow configured the whole suite is skipped (the
 * production code path without a key is covered by tests/e2e.mjs).
 */
import http from 'node:http';

const API = `${process.env.API_BASE || 'http://127.0.0.1:3001'}/api/v1`;
const STUB_PORT = 9701;

let failures = 0;
let checks = 0;
const check = (name, ok, detail = '') => {
  checks++;
  console.log(`  ${ok ? '\x1b[32m✓\x1b[0m' : '\x1b[31m✗\x1b[0m'} ${name}${ok ? '' : ` — ${detail}`}`);
  if (!ok) failures++;
};

/**
 * The stub needs to know what the REAL endpoint expects to receive so we can
 * assert the client sends the right shape. It records the last request body.
 */
let lastBody = null;
let lastAuth = null;

const stub = http.createServer((req, res) => {
  let body = '';
  req.on('data', (c) => (body += c));
  req.on('end', () => {
    lastAuth = req.headers.authorization ?? null;
    let parsed = null;
    try { parsed = JSON.parse(body || '{}'); } catch { /* ignore */ }
    lastBody = parsed;
    const imageUrl = String(parsed?.inputs?.image?.value ?? '');

    let out;
    if (/approved/i.test(imageUrl)) {
      out = {
        outputs: {
          decision: 'approved',
          category: 'Electronics',
          subcategory: 'Headphones',
          product_title: 'AirSound Pro Headphones',
          tags: ['wireless', 'headphones'],
          visual_search_embedding: [0.9, 0.1, 0.0, 0.2],
        },
      };
    } else if (/review/i.test(imageUrl)) {
      out = { outputs: { decision: 'manual_review', category: 'Home' } };
    } else if (/blurry/i.test(imageUrl)) {
      out = { outputs: { decision: 'needs_better_image', rejection_reasons: ['image is blurry'] } };
    } else if (/rejected/i.test(imageUrl)) {
      out = {
        outputs: {
          decision: 'rejected',
          category: 'Fashion',
          rejection_reasons: ['watermark detected', 'low resolution'],
        },
      };
    } else {
      out = { outputs: { decision: 'manual_review' } };
    }
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end(JSON.stringify(out));
  });
});

async function api(path, opts = {}) {
  const res = await fetch(`${API}${path}`, {
    method: opts.method || 'GET',
    headers: {
      'content-type': 'application/json',
      ...(opts.headers || {}),
    },
    body: opts.body ? JSON.stringify(opts.body) : undefined,
  });
  const data = await res.json().catch(() => ({}));
  return { status: res.status, data };
}

async function login(email, password) {
  const r = await api('/auth/login', { method: 'POST', body: { email, password } });
  if (r.status !== 200) throw new Error(`login failed: ${r.status} ${JSON.stringify(r.data)}`);
  return r.data;
}

const main = async () => {
  await new Promise((r) => stub.listen(STUB_PORT, '127.0.0.1', r));
  console.log(`[stub] Roboflow workflow listening on :${STUB_PORT}`);

  // 1. Confirm the live server was started with Roboflow configured.
  const status = await api('/ai/status');
  if (!status.data?.capabilities?.vision || status.data?.visionProvider !== 'roboflow') {
    console.log('\nSKIPPED — live server has no ROBOFLOW_API_KEY configured.');
    console.log('Start it with ROBOFLOW_WORKFLOW_URL=http://127.0.0.1:9701/... and rerun.');
    stub.close();
    process.exit(0);
  }
  check('status advertises the Roboflow vision provider', status.data.visionProvider === 'roboflow');

  // 2. Listings are routed by the workflow decision.
  const seller = await login('techhub@scottstechx.ug', 'Seller123!');
  const auth = { authorization: `Bearer ${seller.token}` };

  const mk = async (imageUrl, title) => {
    const r = await api('/seller/products', {
      method: 'POST',
      headers: auth,
      body: {
        title, description: 'Vision routing test listing.', category: 'Electronics', brand: '',
        priceMinor: 25000, stockQuantity: 1, imageUrl,
      },
    });
    return r;
  };

  const approved = await mk('https://example.com/photos/approved-headphones.jpg', 'Vision Approved Test');
  check('approved photo → listing published', approved.data?.product?.status === 'approved',
    JSON.stringify(approved.data?.product?.status));
  check('approved listing carries vision metadata',
    approved.data?.product?.visionCategory === 'Electronics' &&
    Array.isArray(approved.data?.product?.visionTags) &&
    approved.data?.product?.visionTags.includes('wireless'),
    JSON.stringify(approved.data?.product?.visionCategory));
  check('approved listing has a visual embedding stored',
    approved.status === 200, `status ${approved.status}`);

  const review = await mk('https://example.com/photos/review-me.jpg', 'Vision Review Test');
  check('manual_review photo → admin queue (pending)', review.data?.product?.status === 'pending',
    JSON.stringify(review.data?.product?.status));

  const rejected = await mk('https://example.com/photos/rejected-watermark.jpg', 'Vision Rejected Test');
  check('rejected photo → listing blocked', rejected.data?.product?.status === 'rejected',
    JSON.stringify(rejected.data?.product?.status));
  check('rejection reasons reach the seller',
    /watermark detected/.test(rejected.data?.product?.rejectionReason || ''),
    rejected.data?.product?.rejectionReason);

  const blurry = await mk('https://example.com/photos/blurry-photo.jpg', 'Vision Blurry Test');
  check('blurry photo → blocked with "better photo" reason',
    blurry.data?.product?.status === 'rejected' && /sharper/i.test(blurry.data?.product?.rejectionReason || ''),
    JSON.stringify([blurry.data?.product?.status, blurry.data?.product?.rejectionReason]));

  const createdIds = [approved, review, rejected, blurry]
    .map((r) => r.data?.product?.id)
    .filter(Boolean);

  // 3. The request shape matches the documented Roboflow contract. (The last
  //    request is the blurry one — assert the shape, not a specific URL.)
  check('stub received the image as { type: url }',
    lastBody?.inputs?.image?.type === 'url' && /^https:\/\//.test(lastBody?.inputs?.image?.value || ''),
    JSON.stringify(lastBody));
  check('API key arrives in the Authorization header (never the body)',
    lastAuth === `Bearer test-dev-key` && !JSON.stringify(lastBody).includes('test-dev-key'),
    `auth=${lastAuth}`);

  // 4. Uploaded (base64) photos also reach the workflow. Temporarily swap the
  //    handler so the response tags the search with "base64-test", then put
  //    the real workflow handler back for the remaining checks.
  let base64Seen = false;
  const mainListener = stub.listeners('request')[0];
  const trackBase64 = (req, res) => {
    let body = '';
    req.on('data', (c) => (body += c));
    req.on('end', () => {
      const p = JSON.parse(body || '{}');
      base64Seen = p?.inputs?.image?.type === 'base64' && /^iVBOR/.test(p?.inputs?.image?.value || '');
      res.writeHead(200, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ outputs: { decision: 'approved', tags: ['base64-test'] } }));
    });
  };
  stub.removeAllListeners('request');
  stub.on('request', trackBase64);
  const tinyPng = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
    'base64'
  );
  const form = new FormData();
  form.append('image', new Blob([tinyPng], { type: 'image/png' }), 'photo.png');
  const searchRes = await fetch(`${API}/ai/image-upload-search`, { method: 'POST', body: form });
  const searchData = await searchRes.json().catch(() => ({}));
  check('base64 upload is sent to the Roboflow workflow', base64Seen,
    `seen=${base64Seen} status=${searchRes.status}`);
  check('upload search returns matches with vision labels',
    searchRes.status === 200 && Array.isArray(searchData.products) &&
    /base64-test/.test(searchData.detected || ''),
    JSON.stringify(searchData.detected));

  stub.removeAllListeners('request');
  stub.on('request', mainListener);

  // 5. URL-typed searches (the /ai/image-search JSON path the client uses for
  //    pasted URLs) must also run through the workflow and rank the catalogue
  //    by embedding similarity. "/approved/" URLs make the stub emit the same
  //    embedding that was persisted on the Vision Approved Test listings.
  const jsonSearch = await fetch(`${API}/ai/image-search`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ imageUrl: 'https://example.com/photos/approved-headphones.jpg' }),
  });
  const jsonData = await jsonSearch.json().catch(() => ({}));
  check('URL image search returns the vision-labelled match',
    jsonSearch.status === 200 && /AirSound Pro Headphones/.test(jsonData.detected || ''),
    JSON.stringify(jsonData.detected));
  check('embedding-ranked product appears at the top of results',
    jsonData.products?.[0]?.title === 'Vision Approved Test',
    JSON.stringify((jsonData.products || []).slice(0, 3).map((p) => p.title)));

  // ── Cleanup: remove the test listings so reruns start from a bare DB. ────
  for (const id of createdIds) {
    await api(`/seller/products/${id}`, { method: 'DELETE', headers: auth }).catch(() => undefined);
  }

  console.log('\n[done] vision contract checks complete');
  stub.close();
  process.exit(failures ? 1 : 0);
};

main().catch((e) => {
  console.error(e);
  stub.close();
  process.exit(1);
});
