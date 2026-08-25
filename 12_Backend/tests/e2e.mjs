#!/usr/bin/env node
/**
 * ScottsTechX — end-to-end API test suite.
 *
 * Exercises the real running server against the real database. No mocks.
 * Run the backend (`npm run dev`) then: `node tests/e2e.mjs`
 */
const BASE = process.env.API_BASE || 'http://127.0.0.1:3001';
const V1 = `${BASE}/api/v1`;

let passed = 0;
let failed = 0;
const failures = [];
let currentGroup = '';

function group(name) {
  currentGroup = name;
  console.log(`\n\x1b[1m\x1b[36m── ${name} ${'─'.repeat(Math.max(0, 60 - name.length))}\x1b[0m`);
}

function check(label, condition, detail = '') {
  if (condition) {
    passed++;
    console.log(`  \x1b[32m✓\x1b[0m ${label}`);
  } else {
    failed++;
    failures.push(`[${currentGroup}] ${label}${detail ? ` — ${detail}` : ''}`);
    console.log(`  \x1b[31m✗ ${label}\x1b[0m${detail ? ` — ${detail}` : ''}`);
  }
}

async function call(path, { method = 'GET', body, token, raw = false } = {}) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(`${path.startsWith('http') ? path : V1 + path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let data = null;
  try {
    data = JSON.parse(text);
  } catch {
    data = text;
  }
  return raw ? { status: res.status, data } : { status: res.status, data, ok: res.ok };
}

const uniq = Date.now().toString(36) + Math.random().toString(36).slice(2, 6);

const state = {};

async function main() {
  console.log('\x1b[1mScottsTechX end-to-end suite\x1b[0m');
  console.log(`target: ${BASE}`);

  // ── Health ────────────────────────────────────────────────────────────────
  group('Health');
  {
    const h = await call(`${BASE}/healthz`);
    check('GET /healthz → ok', h.status === 200 && h.data?.ok === true, JSON.stringify(h.data));
  }

  // ── Auth ──────────────────────────────────────────────────────────────────
  group('Auth & roles');
  {
    const admin = await call('/auth/login', {
      method: 'POST',
      body: { email: 'admin@scottstechx.ug', password: 'Admin123!' },
    });
    check('admin login', admin.status === 200 && !!admin.data?.token);
    check('admin has admin role', admin.data?.user?.role === 'admin', admin.data?.user?.role);
    state.adminToken = admin.data?.token;

    const seller = await call('/auth/login', {
      method: 'POST',
      body: { email: 'techhub@scottstechx.ug', password: 'Seller123!' },
    });
    check('seeded seller login', seller.status === 200 && !!seller.data?.token);
    check('seller has seller role', seller.data?.user?.role === 'seller', seller.data?.user?.role);
    state.sellerToken = seller.data?.token;
    state.sellerId = seller.data?.user?.id;

    const buyer = await call('/auth/register', {
      method: 'POST',
      body: {
        email: `buyer_${uniq}@test.ug`,
        password: 'Buyer123!',
        displayName: 'Test Buyer',
        phone: '+256700000000',
      },
    });
    check('buyer registration', (buyer.status === 200 || buyer.status === 201) && !!buyer.data?.token, JSON.stringify(buyer.data).slice(0, 120));
    state.buyerToken = buyer.data?.token;
    state.buyerId = buyer.data?.user?.id;

<<<<<<< HEAD
    // Registration now issues an UNVERIFIED account and the backend gates all
    // private routes until the address is proven. In dev the code comes back
    // in the response, so the fixture verifies itself before continuing.
    {
      const vc = await call('/auth/verify/confirm', {
        method: 'POST',
        token: state.buyerToken,
        body: { code: buyer.data?.verification?.devCode },
      });
      check('buyer verifies email with the dev code', vc.status === 200, JSON.stringify(vc.data).slice(0, 120));
    }

=======
>>>>>>> origin/master
    const badLogin = await call('/auth/login', {
      method: 'POST',
      body: { email: 'admin@scottstechx.ug', password: 'wrong-password' },
    });
    check('wrong password rejected', badLogin.status === 401, `got ${badLogin.status}`);

<<<<<<< HEAD
    // The app's single identifier field accepts a phone number too: the
    // buyer above registered one, so login by phone must reach the same
    // account — and a phone nobody registered must fail with the same
    // generic 401 as a wrong password.
    const phoneLogin = await call('/auth/login', {
      method: 'POST',
      body: { email: '+256 700 000 000', password: 'Buyer123!' },
    });
    check(
      'login by registered phone number',
      phoneLogin.status === 200 && phoneLogin.data?.user?.id === state.buyerId,
      `got ${phoneLogin.status}`,
    );
    const unknownPhone = await call('/auth/login', {
      method: 'POST',
      body: { email: '+256 999 888 777', password: 'Buyer123!' },
    });
    check('login by unknown phone number rejected (401)', unknownPhone.status === 401, `got ${unknownPhone.status}`);

=======
>>>>>>> origin/master
    const noAuth = await call('/admin/stats');
    check('admin route rejects anonymous', noAuth.status === 401, `got ${noAuth.status}`);

    const buyerOnAdmin = await call('/admin/stats', { token: state.buyerToken });
    check('admin route rejects buyer (403)', buyerOnAdmin.status === 403, `got ${buyerOnAdmin.status}`);

    const buyerOnSeller = await call('/seller/products', { token: state.buyerToken });
    check('seller route rejects buyer (403)', buyerOnSeller.status === 403, `got ${buyerOnSeller.status}`);
  }

  // ── Catalog & search ──────────────────────────────────────────────────────
  group('Catalog, search & facets');
  {
    const list = await call('/products');
    check('GET /products returns items', Array.isArray(list.data?.products) && list.data.products.length > 0);
    check('products include total count', typeof list.data?.total === 'number', String(list.data?.total));
    state.sampleProduct = list.data?.products?.[0];
    check(
      'every listed product is approved',
      list.data.products.every((p) => p.status === 'approved'),
      'found non-approved in public list'
    );
    check(
      'product images are real URLs',
      list.data.products.every((p) => /^https?:\/\//.test(p.imageUrl || '')),
      'some imageUrl not a URL'
    );

    const search = await call('/products/search?q=phone');
    check('text search works', search.status === 200 && Array.isArray(search.data?.products));

    const priceSort = await call('/products?sort=price_asc&pageSize=10');
    const prices = (priceSort.data?.products ?? []).map((p) => p.priceMinor);
    check(
      'sort=price_asc is ascending',
      prices.every((v, i) => i === 0 || prices[i - 1] <= v),
      prices.join(',')
    );

    const priceDesc = await call('/products?sort=price_desc&pageSize=10');
    const pd = (priceDesc.data?.products ?? []).map((p) => p.priceMinor);
    check('sort=price_desc is descending', pd.every((v, i) => i === 0 || pd[i - 1] >= v));

    const capped = await call('/products?maxPrice=200000');
    check(
      'maxPrice filter respected',
      (capped.data?.products ?? []).every((p) => p.priceMinor <= 200000)
    );

    const facets = await call('/products/facets');
    check('facets return categories', Array.isArray(facets.data?.categories) && facets.data.categories.length > 0);
    check('facets return price range', typeof facets.data?.priceRange?.maxPrice === 'number');
    state.category = facets.data?.categories?.[0]?.name;

    const byCat = await call(`/products?category=${encodeURIComponent(state.category)}`);
    check(
      `category filter (${state.category})`,
      (byCat.data?.products ?? []).every((p) => p.category === state.category)
    );

    // Synonym / plural expansion — "phones" must surface iPhone & Galaxy.
    const plural = await call('/products/search?q=phones');
    const titles = (plural.data?.products ?? []).map((p) => p.title).join(' | ');
    check('plural search finds singular products', /iPhone/i.test(titles), titles);
    check('synonym search finds branded phones', /Galaxy/i.test(titles), titles);

    const shoes = await call('/products/search?q=shoes');
    check('shoes → sneakers/footwear synonym', (shoes.data?.products ?? []).length > 0,
      `${shoes.data?.total} results`);

    const sug = await call('/products/suggest?q=ph');
    check('typeahead suggestions', Array.isArray(sug.data?.suggestions));

    const paged = await call('/products?page=1&pageSize=5');
    check('pagination caps page size', (paged.data?.products ?? []).length <= 5);

    if (state.sampleProduct) {
      const detail = await call(`/products/${state.sampleProduct.id}`);
      check('product detail', detail.status === 200 && detail.data?.product?.id === state.sampleProduct.id);
      const related = await call(`/products/${state.sampleProduct.id}/related`);
      check('related products', Array.isArray(related.data?.products));
    }
    const missing = await call('/products/00000000-0000-0000-0000-000000000000');
    check('unknown product → 404', missing.status === 404, `got ${missing.status}`);
  }

  // ── Product approval workflow (the core new rule) ─────────────────────────
  group('Product approval workflow');
  {
    const created = await call('/seller/products', {
      method: 'POST',
      token: state.sellerToken,
      body: {
        title: `E2E Test Product ${uniq}`,
        description: 'Created by the automated end-to-end suite.',
        category: 'Electronics',
        brand: 'TestBrand',
        priceMinor: 123456,
        stockQuantity: 7,
        imageUrl: 'https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=800',
      },
    });
    check('seller can create a product', created.status === 200, JSON.stringify(created.data).slice(0, 160));
    state.newProductId = created.data?.product?.id;
    check(
      'new product is PENDING (not auto-published)',
      created.data?.product?.status === 'pending',
      `status=${created.data?.product?.status}`
    );

    const publicList = await call('/products?pageSize=100');
    check(
      'pending product is NOT in the public catalog',
      !(publicList.data?.products ?? []).some((p) => p.id === state.newProductId)
    );

    const anonDetail = await call(`/products/${state.newProductId}`);
    check('anonymous cannot open a pending product (404)', anonDetail.status === 404, `got ${anonDetail.status}`);

    const ownerDetail = await call(`/products/${state.newProductId}`, { token: state.sellerToken });
    check('owner CAN see their pending product', ownerDetail.status === 200, `got ${ownerDetail.status}`);

    const sellerList = await call('/seller/products', { token: state.sellerToken });
    check(
      'seller list shows pending product',
      (sellerList.data?.products ?? []).some((p) => p.id === state.newProductId)
    );
    check('seller list has status counts', typeof sellerList.data?.counts?.pending === 'number');

    const queue = await call('/admin/products/queue', { token: state.adminToken });
    check(
      'product appears in the admin review queue',
      (queue.data?.products ?? []).some((p) => p.id === state.newProductId)
    );

    const sellerNotifs = await call('/me/notifications', { token: state.sellerToken });
    check(
      'seller notified of pending review',
      (sellerNotifs.data?.notifications ?? []).some((n) => n.type === 'product_pending')
    );

    // Reject → then resubmit → then approve.
    const rejected = await call(`/admin/products/${state.newProductId}/reject`, {
      method: 'POST',
      token: state.adminToken,
      body: { reason: 'Photo quality too low for the E2E test' },
    });
    check('admin can reject', rejected.status === 200, JSON.stringify(rejected.data).slice(0, 120));

    const afterReject = await call(`/products/${state.newProductId}`, { token: state.sellerToken });
    check('rejected product carries the reason', /Photo quality/.test(afterReject.data?.product?.rejectionReason ?? ''));

    const rejectNotif = await call('/me/notifications', { token: state.sellerToken });
    check(
      'seller notified of rejection',
      (rejectNotif.data?.notifications ?? []).some((n) => n.type === 'product_rejected')
    );

    const resubmit = await call(`/seller/products/${state.newProductId}/submit`, {
      method: 'POST',
      token: state.sellerToken,
    });
    check('seller can resubmit after rejection', resubmit.data?.product?.status === 'pending');

    const approved = await call(`/admin/products/${state.newProductId}/approve`, {
      method: 'POST',
      token: state.adminToken,
    });
    check('admin can approve', approved.status === 200 && approved.data?.ok === true);

    const nowPublic = await call('/products?pageSize=100');
    check(
      'approved product IS now in the public catalog',
      (nowPublic.data?.products ?? []).some((p) => p.id === state.newProductId)
    );

    const anonAfter = await call(`/products/${state.newProductId}`);
    check('anonymous can now open it', anonAfter.status === 200);

    const approveNotif = await call('/me/notifications', { token: state.sellerToken });
    check(
      'seller notified of approval',
      (approveNotif.data?.notifications ?? []).some((n) => n.type === 'product_approved')
    );

    // Editing content sends it back to review.
    const edited = await call(`/seller/products/${state.newProductId}`, {
      method: 'PATCH',
      token: state.sellerToken,
      body: { title: `E2E Test Product ${uniq} (edited)` },
    });
    check('content edit returns product to pending', edited.data?.product?.status === 'pending', edited.data?.product?.status);

    // Re-approve so later tests have a live product.
    await call(`/admin/products/${state.newProductId}/approve`, { method: 'POST', token: state.adminToken });

    // Price-only edit must NOT unpublish.
    const priceEdit = await call(`/seller/products/${state.newProductId}`, {
      method: 'PATCH',
      token: state.sellerToken,
      body: { priceMinor: 99999 },
    });
    check('price-only edit stays approved', priceEdit.data?.product?.status === 'approved', priceEdit.data?.product?.status);

    // A partial update must not resurrect schema defaults. Zod's .partial()
    // keeps a .default() alive, so PATCH {stockQuantity} used to silently blank
    // description/category/brand and knock the listing back into review.
    const beforePartial = await call(`/products/${state.newProductId}`);
    const bp = beforePartial.data?.product;
    const stockEdit = await call(`/seller/products/${state.newProductId}`, {
      method: 'PATCH',
      token: state.sellerToken,
      body: { stockQuantity: 7 },
    });
    const sp = stockEdit.data?.product;
    check('stock-only edit stays approved', sp?.status === 'approved', sp?.status);
    check('stock-only edit applied the new stock', sp?.stockQuantity === 7, `${sp?.stockQuantity}`);
    check('stock-only edit preserves the description', sp?.description === bp?.description,
      `${JSON.stringify(sp?.description)} vs ${JSON.stringify(bp?.description)}`);
    check('stock-only edit preserves the category', sp?.category === bp?.category,
      `${sp?.category} vs ${bp?.category}`);
    check('stock-only edit preserves the brand', sp?.brand === bp?.brand,
      `${JSON.stringify(sp?.brand)} vs ${JSON.stringify(bp?.brand)}`);
    check('stock-only edit preserves the flash-deal flag', sp?.isFlashDeal === bp?.isFlashDeal,
      `${sp?.isFlashDeal} vs ${bp?.isFlashDeal}`);
    check('stock-only edit preserves the discount', sp?.discountPercent === bp?.discountPercent,
      `${sp?.discountPercent} vs ${bp?.discountPercent}`);

    const history = await call(`/admin/products/${state.newProductId}/history`, { token: state.adminToken });
    check('moderation history recorded', (history.data?.history ?? []).length >= 3, `${history.data?.history?.length} entries`);

    // Listing quality gate — nothing imageless/priceless reaches the queue.
    const noImage = await call('/seller/products', {
      method: 'POST', token: state.sellerToken,
      body: { title: `No Image ${uniq}`, priceMinor: 5000, category: 'Other' },
    });
    check('listing without a photo is rejected (400)', noImage.status === 400, `got ${noImage.status}`);

    const zeroPrice = await call('/seller/products', {
      method: 'POST', token: state.sellerToken,
      body: { title: `Free ${uniq}`, priceMinor: 0, category: 'Other',
              imageUrl: 'https://images.unsplash.com/photo-1' },
    });
    check('listing with zero price is rejected (400)', zeroPrice.status === 400, `got ${zeroPrice.status}`);

    const draft = await call('/seller/products', {
      method: 'POST', token: state.sellerToken,
      body: { title: `Draft ${uniq}`, priceMinor: 0, category: 'Other', asDraft: true },
    });
    check('incomplete DRAFT is allowed', draft.status === 200 && draft.data?.product?.status === 'draft',
      `status=${draft.status}/${draft.data?.product?.status}`);
    state.draftId = draft.data?.product?.id;

    const badSubmit = await call(`/seller/products/${state.draftId}/submit`, {
      method: 'POST', token: state.sellerToken,
    });
    check('incomplete draft cannot be submitted', badSubmit.status === 400, `got ${badSubmit.status}`);

    // Cross-seller protection.
    const other = await call('/auth/login', {
      method: 'POST',
      body: { email: 'fashionhub@scottstechx.ug', password: 'Seller123!' },
    });
    if (other.data?.token) {
      const steal = await call(`/seller/products/${state.newProductId}`, {
        method: 'PATCH',
        token: other.data.token,
        body: { priceMinor: 1 },
      });
      check('another seller cannot edit my product', steal.status === 404, `got ${steal.status}`);
    }
  }

  // ── Favourites + push fan-out ─────────────────────────────────────────────
  group('Favourites & new-product notifications');
  {
    const follow = await call(`/me/favorites/${state.sellerId}`, { method: 'POST', token: state.buyerToken });
    check('buyer can follow a seller', follow.status === 200 && follow.data?.following === true);

    const favs = await call('/me/favorites', { token: state.buyerToken });
    check('favourites list includes the seller', (favs.data?.sellers ?? []).some((s) => s.id === state.sellerId));

    const feed = await call('/me/favorites/feed', { token: state.buyerToken });
    check('favourites feed returns products', Array.isArray(feed.data?.products) && feed.data.products.length > 0);

    // New product from a followed seller → approve → buyer must be notified.
    const p = await call('/seller/products', {
      method: 'POST',
      token: state.sellerToken,
      body: {
        title: `Followed Store Drop ${uniq}`,
        priceMinor: 250000,
        category: 'Electronics',
        imageUrl: 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=800',
      },
    });
    const dropId = p.data?.product?.id;
    state.dropId = dropId;
    const before = await call('/me/notifications', { token: state.buyerToken });
    const beforeCount = (before.data?.notifications ?? []).filter((n) => n.type === 'new_product').length;

    const appr = await call(`/admin/products/${dropId}/approve`, { method: 'POST', token: state.adminToken });
    check('approval reports follower fan-out', typeof appr.data?.followersNotified === 'number', String(appr.data?.followersNotified));

    const after = await call('/me/notifications', { token: state.buyerToken });
    const newProdNotifs = (after.data?.notifications ?? []).filter((n) => n.type === 'new_product');
    check('follower received a new-product notification', newProdNotifs.length > beforeCount,
      `${beforeCount} → ${newProdNotifs.length}`);
    check(
      'notification deep-links to the product',
      newProdNotifs.some((n) => n?.data?.id === dropId),
      JSON.stringify(newProdNotifs[0]?.data ?? {})
    );

    // Device registration for real phone push.
    const dev = await call('/me/devices', {
      method: 'POST',
      token: state.buyerToken,
      body: { token: `e2e-device-token-${uniq}`, platform: 'android' },
    });
    check('device token registers for push', dev.status === 200 && dev.data?.ok === true);

    // The Android client re-registers on every sign-in and on FCM token
    // rotation, so repeats must be idempotent rather than piling up rows.
    const devAgain = await call('/me/devices', {
      method: 'POST',
      token: state.buyerToken,
      body: { token: `e2e-device-token-${uniq}`, platform: 'android' },
    });
    check('re-registering the same device token is idempotent', devAgain.status === 200);

    const devShort = await call('/me/devices', {
      method: 'POST', token: state.buyerToken, body: { token: 'tiny', platform: 'android' },
    });
    check('an implausibly short device token is rejected', devShort.status === 400, `got ${devShort.status}`);

    const devAnon = await call('/me/devices', {
      method: 'POST', body: { token: `anon-token-${uniq}-padding`, platform: 'android' },
    });
    check('device registration requires auth', devAnon.status === 401, `got ${devAnon.status}`);

    const devGone = await call('/me/devices', {
      method: 'DELETE', token: state.buyerToken, body: { token: `e2e-device-token-${uniq}` },
    });
    check('device token can be dropped on sign-out', devGone.status === 200);

    // Every notification type the backend emits must map to a channel the
    // Android app actually declares, or the push is silently discarded.
    const ANDROID_CHANNELS = {
      order_update: 'orders',
      message: 'messages',
      new_product: 'new_products',
      price_drop: 'new_products',
    };
    const allNotifs = (await call('/me/notifications', { token: state.buyerToken })).data?.notifications ?? [];
    const emitted = [...new Set(allNotifs.map((n) => n.type))];
    check(
      'every emitted notification type maps to a declared Android channel',
      emitted.every((t) => ['orders', 'messages', 'new_products', 'general'].includes(ANDROID_CHANNELS[t] ?? 'general')),
      emitted.join(', ')
    );

    const unfollow = await call(`/me/favorites/${state.sellerId}`, { method: 'DELETE', token: state.buyerToken });
    check('buyer can unfollow', unfollow.data?.following === false);
    await call(`/me/favorites/${state.sellerId}`, { method: 'POST', token: state.buyerToken });
  }

  // ── Nearby / live location ────────────────────────────────────────────────
  group('Nearby & live seller location');
  {
    const kampala = { lat: 0.3476, lng: 32.5825 };
    const near = await call(`/sellers/nearby?lat=${kampala.lat}&lng=${kampala.lng}&radiusKm=100`);
    check('nearby returns sellers', (near.data?.sellers ?? []).length > 0);
    const ds = (near.data?.sellers ?? []).map((s) => s.distanceKm);
    check('nearby sorted by distance', ds.every((v, i) => i === 0 || ds[i - 1] <= v), ds.join(','));
    check('nearby exposes live flag', near.data?.sellers?.every((s) => typeof s.live === 'boolean'));
    check('nearby reports ETA', near.data?.sellers?.every((s) => typeof s.etaMinutes === 'number'));

    // Distances must change as the buyer moves.
    const gulu = { lat: 2.7746, lng: 32.2989 };
    const near2 = await call(`/sellers/nearby?lat=${gulu.lat}&lng=${gulu.lng}&radiusKm=1000`);
    const first1 = near.data?.sellers?.[0];
    const first2 = near2.data?.sellers?.[0];
    check(
      'moving the buyer re-orders / re-scores the list',
      first1 && first2 && (first1.id !== first2.id || first1.distanceKm !== first2.distanceKm),
      `${first1?.name}@${first1?.distanceKm}km vs ${first2?.name}@${first2?.distanceKm}km`
    );

    const radius = await call(`/sellers/nearby?lat=${kampala.lat}&lng=${kampala.lng}&radiusKm=5`);
    check(
      'radius filter respected',
      (radius.data?.sellers ?? []).every((s) => s.distanceKm <= 5)
    );

    // Seller shares live location → becomes live and moves.
    const share = await call('/seller/location', {
      method: 'POST',
      token: state.sellerToken,
      body: { lat: 0.36, lng: 32.6, sharing: true, city: 'Kampala' },
    });
    check('seller can publish a live location', share.status === 200 && share.data?.location?.sharing === true);

    const afterShare = await call(`/sellers/nearby?lat=${kampala.lat}&lng=${kampala.lng}&radiusKm=100`);
    const meLive = (afterShare.data?.sellers ?? []).find((s) => s.id === state.sellerId);
    check('sharing seller is marked live', meLive?.live === true, `live=${meLive?.live}`);
    const livePos = { lat: meLive?.lat, lng: meLive?.lng };
    check('live position matches the published fix', Math.abs(livePos.lat - 0.36) < 0.001);

    // Seller moves → position follows.
    await call('/seller/location', {
      method: 'POST',
      token: state.sellerToken,
      body: { lat: 0.40, lng: 32.62, sharing: true },
    });
    const moved = await call(`/sellers/nearby?lat=${kampala.lat}&lng=${kampala.lng}&radiusKm=100`);
    const meMoved = (moved.data?.sellers ?? []).find((s) => s.id === state.sellerId);
    check('seller pin follows the seller', Math.abs(meMoved.lat - 0.40) < 0.001, `lat=${meMoved?.lat}`);
    check('distance recomputed after seller moved', meMoved.distanceKm !== meLive.distanceKm);

    // Seller stops sharing → store STAYS at last known point (sticky pin).
    const stop = await call('/seller/location', { method: 'DELETE', token: state.sellerToken });
    check('seller can stop sharing', stop.status === 200 && stop.data?.location?.sharing === false);

    const afterStop = await call(`/sellers/nearby?lat=${kampala.lat}&lng=${kampala.lng}&radiusKm=100`);
    const meStopped = (afterStop.data?.sellers ?? []).find((s) => s.id === state.sellerId);
    check('store still visible after location off', !!meStopped);
    check('store is no longer marked live', meStopped?.live === false, `live=${meStopped?.live}`);
    check(
      'store stays pinned at LAST known position',
      Math.abs(meStopped.lat - 0.40) < 0.001 && Math.abs(meStopped.lng - 32.62) < 0.001,
      `${meStopped?.lat},${meStopped?.lng}`
    );

    const verifiedOnly = await call(`/sellers/nearby?lat=${kampala.lat}&lng=${kampala.lng}&radiusKm=500&verifiedOnly=true`);
    check('verifiedOnly filter', (verifiedOnly.data?.sellers ?? []).every((s) => s.verified === true));

    const byRating = await call(`/sellers/nearby?lat=${kampala.lat}&lng=${kampala.lng}&radiusKm=500&sort=rating`);
    const rs = (byRating.data?.sellers ?? []).map((s) => s.rating);
    check('sort=rating descending', rs.every((v, i) => i === 0 || rs[i - 1] >= v));

    // -- the buyer moves: stores must continuously re-sort by distance -------
    const fromKampala = await call(`/sellers/nearby?lat=0.3476&lng=32.5825&radiusKm=1000`);
    const fromGulu = await call(`/sellers/nearby?lat=2.7746&lng=32.2990&radiusKm=1000`);
    const kOrder = (fromKampala.data?.sellers ?? []).map((s) => s.id);
    const gOrder = (fromGulu.data?.sellers ?? []).map((s) => s.id);

    check(
      'distances ascend from the buyer position (Kampala)',
      (fromKampala.data?.sellers ?? []).every((s, i, a) => i === 0 || a[i - 1].distanceKm <= s.distanceKm)
    );
    check(
      'distances ascend from the buyer position (Gulu)',
      (fromGulu.data?.sellers ?? []).every((s, i, a) => i === 0 || a[i - 1].distanceKm <= s.distanceKm)
    );
    check(
      'moving the buyer re-orders the store list',
      JSON.stringify(kOrder) !== JSON.stringify(gOrder),
      'identical ordering from two cities 400km apart'
    );
    const sameStore = (fromGulu.data?.sellers ?? []).find((s) => s.id === kOrder[0]);
    check(
      'the same store reports a different distance as the buyer moves',
      sameStore && Math.abs(sameStore.distanceKm - fromKampala.data.sellers[0].distanceKm) > 1,
      `${fromKampala.data?.sellers?.[0]?.distanceKm} vs ${sameStore?.distanceKm}`
    );
    check(
      'every store carries an ETA for the Nearby screen',
      (fromKampala.data?.sellers ?? []).every((s) => typeof s.etaMinutes === 'number')
    );
    check(
      'locationAgeMinutes is a number or null (Kotlin optInt/isNull)',
      (fromKampala.data?.sellers ?? []).every(
        (s) => s.locationAgeMinutes === null || typeof s.locationAgeMinutes === 'number'
      )
    );
  }

  // ── AI ────────────────────────────────────────────────────────────────────
  group('AI assistant, agents & search');
  {
    const status = await call('/ai/status');
    check('AI status endpoint', status.status === 200 && typeof status.data?.configured === 'boolean');
    check('AI reports grounded capability', status.data?.grounded === true);
    console.log(`      provider=${status.data?.provider} model=${status.data?.model}`);

    const agents = await call('/ai/agents');
    check('agent roster exposed', (agents.data?.agents ?? []).length >= 6, `${agents.data?.agents?.length} agents`);

    const askQ = await call('/ai/v2/ask', {
      method: 'POST',
      body: { prompt: 'Show me the cheapest phones you have', screen: 'buyer_home' },
    });
    check('AI answers a shopping question', askQ.status === 200 && (askQ.data?.text ?? '').length > 20);
    check('AI answer is grounded with real products', (askQ.data?.products ?? []).length > 0,
      `${askQ.data?.products?.length} products`);
    check('AI routes to an agent', !!askQ.data?.agent?.id, askQ.data?.agent?.id);
    check(
      'grounded products are real catalog rows',
      (askQ.data?.products ?? []).every((p) => p.id && p.title && typeof p.priceMinor === 'number')
    );

    const supportQ = await call('/ai/v2/ask', {
      method: 'POST',
      body: { prompt: 'How do refunds work?', screen: 'support' },
    });
    check('support question routes to the support agent', supportQ.data?.agent?.id === 'support', supportQ.data?.agent?.id);

    const sellerQ = await call('/ai/v2/ask', {
      method: 'POST',
      token: state.sellerToken,
      body: { prompt: 'Write a listing for a used iPhone 13', screen: 'add_product' },
    });
    check('seller listing request routes to the listing agent', sellerQ.data?.agent?.id === 'listing', sellerQ.data?.agent?.id);

    // Natural-language search → structured filters.
    const nl = await call('/ai/search', { method: 'POST', body: { q: 'cheap electronics under 500000' } });
    check('AI search returns products', nl.status === 200 && Array.isArray(nl.data?.products));
    check('AI search extracts a max price', nl.data?.filters?.maxPriceMinor === 500000, String(nl.data?.filters?.maxPriceMinor));
    check('AI search sorts cheapest first', nl.data?.filters?.sort === 'price_asc', nl.data?.filters?.sort);
    check(
      'AI search respects the extracted budget',
      (nl.data?.products ?? []).every((p) => p.priceMinor <= 500000)
    );
    check('AI search explains itself', (nl.data?.explanation ?? '').length > 10, nl.data?.explanation);

    const aiPhones = await call('/ai/search', { method: 'POST', body: { q: 'cheapest phones' } });
    const aiTitles = (aiPhones.data?.products ?? []).map((p) => p.title).join(' | ');
    check('AI search expands "phones" to real phone models', /iPhone|Galaxy/i.test(aiTitles), aiTitles);

    const cityQ = await call('/ai/search', { method: 'POST', body: { q: 'laptops in Kampala' } });
    check('AI search extracts a city', cityQ.data?.filters?.city === 'Kampala', String(cityQ.data?.filters?.city));

    // -- relevance ranking --------------------------------------------------
    // Two defects lived here:
    //   1. keywords shorter than 3 chars were dropped, so "tv" produced an
    //      empty filter and the query returned the ENTIRE catalogue.
    //   2. results were ordered by rating only, so a highly-rated pair of
    //      "Headphones" outranked the iPhone for the query "phone" (a plain
    //      %phone% LIKE also matches "head-phone-s").
    const tvQ = await call('/ai/search', { method: 'POST', body: { q: 'tv' } });
    const tvTitles = (tvQ.data?.products ?? []).map((p) => p.title);
    check('short query "tv" is not discarded', tvTitles.length > 0 && tvTitles.length < 10,
      `${tvTitles.length} results`);
    check('"tv" actually returns a TV', /\bTV\b/i.test(tvTitles[0] ?? ''), tvTitles[0]);

    const phoneQ = await call('/ai/search', { method: 'POST', body: { q: 'phone' } });
    const phoneTitles = (phoneQ.data?.products ?? []).map((p) => p.title);
    check(
      '"phone" ranks a real phone above headphones',
      /iPhone|Galaxy/i.test(phoneTitles[0] ?? ''),
      phoneTitles.slice(0, 3).join(' | ')
    );

    const chargeQ = await call('/ai/search', { method: 'POST', body: { q: 'something to charge my phone' } });
    check(
      'synonym intent finds the power bank',
      /power bank/i.test((chargeQ.data?.products ?? [])[0]?.title ?? ''),
      (chargeQ.data?.products ?? []).slice(0, 2).map((p) => p.title).join(' | ')
    );

    const exactQ = await call('/ai/search', { method: 'POST', body: { q: 'headphones' } });
    check(
      '"headphones" still matches headphones',
      /headphone/i.test((exactQ.data?.products ?? [])[0]?.title ?? ''),
      (exactQ.data?.products ?? [])[0]?.title
    );

    const voice = await call('/ai/voice-search', {
      method: 'POST',
      body: { transcript: 'show me flash deals on fashion' },
    });
    check('voice search works', voice.status === 200 && Array.isArray(voice.data?.products));
    check('voice search echoes the transcript', voice.data?.transcript === 'show me flash deals on fashion');

    const img = await call('/ai/image-search', {
      method: 'POST',
      body: { hint: 'smartphone', imageUrl: 'https://example.com/photos/samsung-galaxy-phone.jpg' },
    });
    check('image search returns matches', img.status === 200 && Array.isArray(img.data?.products));
    check('image search reports what it detected', typeof img.data?.detected === 'string', img.data?.detected);

    const gen = await call('/ai/v2/generate-product', {
      method: 'POST',
      body: { hint: 'Samsung Galaxy A54 smartphone' },
    });
    check('AI generates a listing', !!gen.data?.title && !!gen.data?.description);
    check('AI suggests a price from comparables', typeof gen.data?.suggestedPriceMinor === 'number');
    check('AI returns comparable listings', Array.isArray(gen.data?.comparables));
  }

  // ── Cart ──────────────────────────────────────────────────────────────────
  group('Cart');
  {
    const pid = state.sampleProduct?.id;
    const add = await call('/me/cart', { method: 'POST', token: state.buyerToken, body: { productId: pid, quantity: 2 } });
    check('add to cart', add.status === 200);

    const cart = await call('/me/cart', { token: state.buyerToken });
    check('cart lists the item', (cart.data?.items ?? []).some((i) => i.productId === pid));
    check('cart computes a subtotal', cart.data?.subtotalMinor > 0, String(cart.data?.subtotalMinor));
    const item = (cart.data?.items ?? []).find((i) => i.productId === pid);
    check('line total = price × qty', item.lineTotalMinor === item.priceMinor * item.quantity);

    const upd = await call(`/me/cart/${pid}`, { method: 'PATCH', token: state.buyerToken, body: { quantity: 5 } });
    check('update quantity', upd.status === 200);
    const cart2 = await call('/me/cart', { token: state.buyerToken });
    check('quantity persisted', (cart2.data?.items ?? []).find((i) => i.productId === pid)?.quantity === 5);

    const overStock = await call('/me/cart', {
      method: 'POST',
      token: state.buyerToken,
      body: { productId: pid, quantity: 99999 },
    });
    check('cannot add more than stock', overStock.status === 409, `got ${overStock.status}`);

    const del = await call(`/me/cart/${pid}`, { method: 'DELETE', token: state.buyerToken });
    check('remove from cart', del.status === 200);
    const cart3 = await call('/me/cart', { token: state.buyerToken });
    check('cart empty after removal', (cart3.data?.items ?? []).length === 0);
  }

  // ── Ratings ───────────────────────────────────────────────────────────────
  group('Product ratings');
  {
    const pid = state.sampleProduct?.id;
    const rate = await call(`/products/${pid}/ratings`, {
      method: 'POST',
      token: state.buyerToken,
      body: { stars: 5, comment: 'Excellent, fast delivery!' },
    });
    check('buyer can rate a product', rate.status === 200 && rate.data?.ok === true);
    check('product rating recalculated', typeof rate.data?.rating === 'number', String(rate.data?.rating));

    const list = await call(`/products/${pid}/ratings`);
    check('ratings are listed', (list.data?.ratings ?? []).length > 0);
    check('rating summary present', typeof list.data?.summary?.average === 'number');

    const again = await call(`/products/${pid}/ratings`, {
      method: 'POST',
      token: state.buyerToken,
      body: { stars: 4, comment: 'Updated review' },
    });
    check('re-rating updates instead of duplicating', again.status === 200);
    const list2 = await call(`/products/${pid}/ratings`);
    check('no duplicate review from same user', list2.data.ratings.filter((r) => r.comment === 'Updated review').length === 1);

    const bad = await call(`/products/${pid}/ratings`, {
      method: 'POST',
      token: state.buyerToken,
      body: { stars: 9 },
    });
    check('invalid star count rejected', bad.status === 400, `got ${bad.status}`);
  }

  // ── Messaging ─────────────────────────────────────────────────────────────
  group('Messaging');
  {
    const conv = await call('/conversations', {
      method: 'POST',
      token: state.buyerToken,
      body: { sellerId: state.sellerId, productId: state.sampleProduct?.id },
    });
    check('buyer opens a conversation', conv.status === 200 && !!conv.data?.conversation?.id);
    state.convId = conv.data?.conversation?.id;

    const send = await call(`/conversations/${state.convId}/messages`, {
      method: 'POST',
      token: state.buyerToken,
      body: { text: 'Hello, is this still available?' },
    });
    check('buyer sends a message', send.status === 200 && !!send.data?.message?.id);

    const sellerInbox = await call('/conversations', { token: state.sellerToken });
    const thread = (sellerInbox.data?.conversations ?? []).find((c) => c.id === state.convId);
    check('thread appears in the seller inbox', !!thread);
    check('unread count for the seller', thread?.unread >= 1, `unread=${thread?.unread}`);

    const reply = await call(`/conversations/${state.convId}/messages`, {
      method: 'POST',
      token: state.sellerToken,
      body: { text: 'Yes, it is in stock!' },
    });
    check('seller replies', reply.status === 200);

    const msgs = await call(`/conversations/${state.convId}/messages`, { token: state.buyerToken });
    check('both messages in the thread', (msgs.data?.messages ?? []).length >= 2);
    check(
      'messages are chronological',
      (msgs.data?.messages ?? []).every(
        (m, i, arr) => i === 0 || new Date(arr[i - 1].createdAt) <= new Date(m.createdAt)
      )
    );

    const read = await call(`/conversations/${state.convId}/read`, { method: 'POST', token: state.sellerToken });
    check('mark-as-read works', read.status === 200);
    const inbox2 = await call('/conversations', { token: state.sellerToken });
    check('unread cleared after read', (inbox2.data?.conversations ?? []).find((c) => c.id === state.convId)?.unread === 0);

    const outsider = await call('/auth/register', {
      method: 'POST',
      body: { email: `nosy_${uniq}@test.ug`, password: 'Nosy1234!', displayName: 'Nosy' },
    });
<<<<<<< HEAD
    // Verify the outsider too — an unverified account would be refused with
    // 403 (the gate) before the ownership check could answer 404, and this
    // test is about ownership, not verification.
    await call('/auth/verify/confirm', {
      method: 'POST',
      token: outsider.data?.token,
      body: { code: outsider.data?.verification?.devCode },
    });
=======
>>>>>>> origin/master
    const peek = await call(`/conversations/${state.convId}/messages`, { token: outsider.data?.token });
    check('outsider cannot read the thread', peek.status === 404, `got ${peek.status}`);

    const empty = await call(`/conversations/${state.convId}/messages`, {
      method: 'POST',
      token: state.buyerToken,
      body: { text: '' },
    });
    check('empty message rejected', empty.status === 400, `got ${empty.status}`);

    state.outsiderToken = outsider.data?.token;
    state.outsiderId = outsider.data?.user?.id;
  }

  // ── Messaging: offers, receipts, typing, pin/archive ──────────────────────
  group('Messaging (advanced)');
  {
    const cid = state.convId;

    // -- thread header ------------------------------------------------------
    const head = await call(`/conversations/${cid}`, { token: state.buyerToken });
    check('thread header loads', head.status === 200 && head.data?.conversation?.id === cid);
    check(
      'header carries counterparty identity',
      !!head.data?.conversation?.otherParty?.name &&
        typeof head.data?.conversation?.otherParty?.verified === 'boolean'
    );

    // -- image message ------------------------------------------------------
    const img = await call(`/conversations/${cid}/messages`, {
      method: 'POST',
      token: state.sellerToken,
      body: { kind: 'image', imageUrl: 'https://example.com/unit.jpg', attachmentName: 'unit.jpg' },
    });
    check(
      'image message stores attachment metadata',
      img.status === 200 && img.data?.message?.kind === 'image' && img.data?.message?.attachmentName === 'unit.jpg'
    );

    // -- typing indicator ---------------------------------------------------
    await call(`/conversations/${cid}/typing`, {
      method: 'POST', token: state.sellerToken, body: { typing: true },
    });
    const typingOn = await call(`/conversations/${cid}/messages`, { token: state.buyerToken });
    check('buyer sees the seller typing', typingOn.data?.otherTyping === true);
    await call(`/conversations/${cid}/typing`, {
      method: 'POST', token: state.sellerToken, body: { typing: false },
    });
    const typingOff = await call(`/conversations/${cid}/messages`, { token: state.buyerToken });
    check('typing indicator clears', typingOff.data?.otherTyping === false);

    // -- read receipts ------------------------------------------------------
    await call(`/conversations/${cid}/read`, { method: 'POST', token: state.sellerToken });
    const receipts = await call(`/conversations/${cid}/messages`, { token: state.buyerToken });
    check(
      'buyer messages report readByOther',
      (receipts.data?.messages ?? []).some((m) => m.senderId === state.buyerId && m.readByOther === true)
    );

    // -- offers -------------------------------------------------------------
    const offer = await call(`/conversations/${cid}/messages`, {
      method: 'POST',
      token: state.buyerToken,
      body: { kind: 'offer', offerMinor: 500000, offerQuantity: 2 },
    });
    check(
      'buyer sends a price offer',
      offer.status === 200 && offer.data?.message?.offerStatus === 'pending' && offer.data?.message?.offerQuantity === 2
    );
    const offerId = offer.data?.message?.id;

    const noPrice = await call(`/conversations/${cid}/messages`, {
      method: 'POST', token: state.buyerToken, body: { kind: 'offer' },
    });
    check('offer without a price rejected', noPrice.status === 400, `got ${noPrice.status}`);

    const selfAccept = await call(`/conversations/${cid}/offers/${offerId}`, {
      method: 'POST', token: state.buyerToken, body: { action: 'accept' },
    });
    check('cannot accept your own offer', selfAccept.status === 403, `got ${selfAccept.status}`);

    const foreignWithdraw = await call(`/conversations/${cid}/offers/${offerId}`, {
      method: 'POST', token: state.sellerToken, body: { action: 'withdraw' },
    });
    check('only the sender may withdraw', foreignWithdraw.status === 403, `got ${foreignWithdraw.status}`);

    // A second pending offer must be voided when the first is accepted.
    const rival = await call(`/conversations/${cid}/messages`, {
      method: 'POST', token: state.buyerToken, body: { kind: 'offer', offerMinor: 400000 },
    });

    const accept = await call(`/conversations/${cid}/offers/${offerId}`, {
      method: 'POST', token: state.sellerToken, body: { action: 'accept' },
    });
    check('seller accepts the offer', accept.status === 200 && accept.data?.status === 'accepted');
    check('acceptance appends a system message', accept.data?.message?.kind === 'system');

    const settled = await call(`/conversations/${cid}/messages`, { token: state.buyerToken });
    const rivalRow = (settled.data?.messages ?? []).find((m) => m.id === rival.data?.message?.id);
    check('accepting one offer declines the other', rivalRow?.offerStatus === 'declined', `got ${rivalRow?.offerStatus}`);

    const reopen = await call(`/conversations/${cid}/offers/${offerId}`, {
      method: 'POST', token: state.sellerToken, body: { action: 'decline' },
    });
    check('a settled offer cannot be changed', reopen.status === 409, `got ${reopen.status}`);

    // -- retraction ---------------------------------------------------------
    const retract = await call(`/conversations/${cid}/messages/${img.data?.message?.id}`, {
      method: 'DELETE', token: state.sellerToken,
    });
    check('sender can retract a message', retract.status === 200);
    const foreignDelete = await call(`/conversations/${cid}/messages/${offerId}`, {
      method: 'DELETE', token: state.sellerToken,
    });
    check('cannot retract another user message', foreignDelete.status === 403, `got ${foreignDelete.status}`);
    const afterRetract = await call(`/conversations/${cid}/messages`, { token: state.buyerToken });
    const retracted = (afterRetract.data?.messages ?? []).find((m) => m.id === img.data?.message?.id);
    check('retracted message is blanked but preserved', retracted?.text === '' && !!retracted?.deletedAt);

    // -- pin / archive / mute ----------------------------------------------
    const pin = await call(`/conversations/${cid}/state`, {
      method: 'PATCH', token: state.buyerToken, body: { pinned: true },
    });
    check('thread can be pinned', pin.status === 200 && pin.data?.state?.pinned === true);
    const pinned = await call('/conversations?filter=pinned', { token: state.buyerToken });
    check('pinned filter finds it', (pinned.data?.conversations ?? []).some((c) => c.id === cid));

    await call(`/conversations/${cid}/state`, {
      method: 'PATCH', token: state.buyerToken, body: { archived: true },
    });
    const defaultInbox = await call('/conversations', { token: state.buyerToken });
    check('archived threads leave the default inbox', !(defaultInbox.data?.conversations ?? []).some((c) => c.id === cid));
    check(
      'inbox exposes filter counts',
      typeof defaultInbox.data?.counts?.all === 'number' && typeof defaultInbox.data?.totalUnread === 'number'
    );
    const archived = await call('/conversations?filter=archived', { token: state.buyerToken });
    check('archived filter finds it', (archived.data?.conversations ?? []).some((c) => c.id === cid));

    await call(`/conversations/${cid}/messages`, {
      method: 'POST', token: state.sellerToken, body: { text: 'Are you still interested?' },
    });
    const revived = await call('/conversations', { token: state.buyerToken });
    check('a new message un-archives the thread', (revived.data?.conversations ?? []).some((c) => c.id === cid));

    const noop = await call(`/conversations/${cid}/state`, {
      method: 'PATCH', token: state.buyerToken, body: {},
    });
    check('empty state patch rejected', noop.status === 400, `got ${noop.status}`);

    // -- access control on the new endpoints -------------------------------
    const outsiderHead = await call(`/conversations/${cid}`, { token: state.outsiderToken });
    check('outsider cannot read the thread header', outsiderHead.status === 404, `got ${outsiderHead.status}`);
    const outsiderTyping = await call(`/conversations/${cid}/typing`, {
      method: 'POST', token: state.outsiderToken, body: { typing: true },
    });
    check('outsider cannot broadcast typing', outsiderTyping.status === 404, `got ${outsiderTyping.status}`);

    // -- inbox search -------------------------------------------------------
    const search = await call('/conversations?q=zzzznomatch', { token: state.buyerToken });
    check('inbox search filters results', (search.data?.conversations ?? []).length === 0);

    // -- numeric types ------------------------------------------------------
    // Postgres returns bigint as a STRING through node-postgres. Money columns
    // must be cast (::int) or clients that expect numbers break: the Android
    // optLong parse and every JS price format silently produce garbage.
    const typed = await call(`/conversations/${cid}`, { token: state.buyerToken });
    check(
      'productPriceMinor is a number, not a string',
      typed.data?.conversation?.productPriceMinor === null ||
        typeof typed.data?.conversation?.productPriceMinor === 'number',
      `got ${typeof typed.data?.conversation?.productPriceMinor}`
    );

    const priced = await call(`/conversations/${cid}/messages`, {
      method: 'POST',
      token: state.buyerToken,
      body: { kind: 'offer', offerMinor: 12345600 },
    });
    check(
      'offerMinor is a number on create',
      typeof priced.data?.message?.offerMinor === 'number',
      `got ${typeof priced.data?.message?.offerMinor} ${JSON.stringify(priced.data?.message?.offerMinor)}`
    );
    const reread = await call(`/conversations/${cid}/messages`, { token: state.buyerToken });
    const offerRow = (reread.data?.messages ?? []).find((m) => m.id === priced.data?.message?.id);
    check(
      'offerMinor is a number on read',
      typeof offerRow?.offerMinor === 'number',
      `got ${typeof offerRow?.offerMinor}`
    );
    check(
      'offerMinor round-trips exactly',
      offerRow?.offerMinor === 12345600,
      String(offerRow?.offerMinor)
    );
    await call(`/conversations/${cid}/offers/${priced.data?.message?.id}`, {
      method: 'POST', token: state.sellerToken, body: { action: 'decline' },
    });
  }

  // ── Quick replies (canned seller responses) ───────────────────────────────
  group('Quick replies');
  {
    const created = await call('/me/quick-replies', {
      method: 'POST',
      token: state.sellerToken,
      body: { text: 'Yes — same-day delivery inside Kampala.' },
    });
    check('seller saves a quick reply', created.status === 201 && !!created.data?.quickReply?.id);
    const qrId = created.data?.quickReply?.id;

    const list = await call('/me/quick-replies', { token: state.sellerToken });
    check('quick replies list back', (list.data?.quickReplies ?? []).some((q) => q.id === qrId));

    const foreign = await call(`/me/quick-replies/${qrId}`, {
      method: 'DELETE', token: state.buyerToken,
    });
    check('quick replies are private per user', foreign.status === 404, `got ${foreign.status}`);

    const removed = await call(`/me/quick-replies/${qrId}`, {
      method: 'DELETE', token: state.sellerToken,
    });
    check('seller deletes own quick reply', removed.status === 200);
  }

  // ── Support (AI + admin modes) ────────────────────────────────────────────
  group('Support desk (AI + admin modes)');
  {
    const aiThread = await call('/me/support/threads', {
      method: 'POST',
      token: state.buyerToken,
      body: { subject: 'Refund question', message: 'How do I return a damaged item?', mode: 'ai' },
    });
    check('AI-mode ticket created', aiThread.status === 200 && !!aiThread.data?.thread?.id);
    check('AI replied immediately', (aiThread.data?.aiReply ?? '').length > 20);
    state.aiTicket = aiThread.data?.thread?.id;

    const detail = await call(`/me/support/threads/${state.aiTicket}`, { token: state.buyerToken });
    check('AI reply stored in the thread', (detail.data?.replies ?? []).some((r) => r.authorRole === 'ai'));

    const followUp = await call(`/me/support/threads/${state.aiTicket}/reply`, {
      method: 'POST',
      token: state.buyerToken,
      body: { body: 'The seller is not responding to me' },
    });
    check('AI follows up in-thread', (followUp.data?.aiReply ?? '').length > 10);

    const esc = await call(`/me/support/threads/${state.aiTicket}/escalate`, {
      method: 'POST',
      token: state.buyerToken,
    });
    check('AI thread escalates to a human', esc.data?.mode === 'admin');

    const adminTickets = await call('/admin/support/tickets', { token: state.adminToken });
    check('escalated ticket reaches the admin queue',
      (adminTickets.data?.tickets ?? []).some((t) => t.id === state.aiTicket));

    const adminReply = await call(`/admin/support/tickets/${state.aiTicket}/reply`, {
      method: 'POST',
      token: state.adminToken,
      body: { body: 'Hi, this is a human agent. We are on it.' },
    });
    check('admin can reply to a ticket', adminReply.status === 200);

    const userView = await call(`/me/support/threads/${state.aiTicket}`, { token: state.buyerToken });
    check('user sees the admin reply', (userView.data?.replies ?? []).some((r) => r.authorRole === 'admin'));

    const notif = await call('/me/notifications', { token: state.buyerToken });
    check('user notified of the support reply',
      (notif.data?.notifications ?? []).some((n) => n.type === 'support_reply'));

    const closed = await call(`/me/support/threads/${state.aiTicket}/close`, {
      method: 'POST',
      token: state.buyerToken,
    });
    check('user can close a ticket', closed.status === 200);
  }

  // ── Admin console ─────────────────────────────────────────────────────────
  group('Admin console');
  {
    const stats = await call('/admin/stats', { token: state.adminToken });
    check('admin stats load', stats.status === 200);
    check('user breakdown present', typeof stats.data?.stats?.users?.total === 'number');
    check('product status breakdown present', typeof stats.data?.stats?.products?.pending === 'number');
    check('review queue included', Array.isArray(stats.data?.reviewQueue));
    check('top sellers included', Array.isArray(stats.data?.topSellers));
    check('14-day sales series', (stats.data?.salesSeries ?? []).length === 14, `${stats.data?.salesSeries?.length} points`);

    const users = await call('/admin/users?pageSize=5', { token: state.adminToken });
    check('user list paginates', (users.data?.users ?? []).length <= 5 && typeof users.data?.total === 'number');

    const searchUsers = await call('/admin/users?search=techhub', { token: state.adminToken });
    check('user search works', (searchUsers.data?.users ?? []).some((u) => u.email.includes('techhub')));

    const roleFilter = await call('/admin/users?role=seller', { token: state.adminToken });
    check('role filter works', (roleFilter.data?.users ?? []).every((u) => u.role === 'seller'));

    const prods = await call('/admin/products?status=approved&pageSize=5', { token: state.adminToken });
    check('admin product filter by status', (prods.data?.products ?? []).every((p) => p.status === 'approved'));
    check('admin product counts', typeof prods.data?.counts?.approved === 'number');

    const verify = await call(`/admin/sellers/${state.sellerId}/verify`, {
      method: 'PATCH',
      token: state.adminToken,
      body: { verified: true },
    });
    check('admin can verify a store', verify.data?.store?.verified === true);

    // Guardrails.
    const selfDemote = await call(`/admin/users/${(await call('/auth/me', { token: state.adminToken })).data?.user?.id}/role`, {
      method: 'PATCH',
      token: state.adminToken,
      body: { role: 'buyer' },
    });
    check('admin cannot demote themselves', selfDemote.status === 403, `got ${selfDemote.status}`);

    // Bulk moderation.
    const b1 = await call('/seller/products', {
      method: 'POST', token: state.sellerToken,
      body: { title: `Bulk A ${uniq}`, priceMinor: 1000, category: 'Other',
              imageUrl: 'https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?w=800' },
    });
    const b2 = await call('/seller/products', {
      method: 'POST', token: state.sellerToken,
      body: { title: `Bulk B ${uniq}`, priceMinor: 2000, category: 'Other',
              imageUrl: 'https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?w=800' },
    });
    const bulk = await call('/admin/products/bulk', {
      method: 'POST',
      token: state.adminToken,
      body: { ids: [b1.data?.product?.id, b2.data?.product?.id], action: 'approve' },
    });
    check('bulk approve works', bulk.data?.affected === 2, `affected=${bulk.data?.affected}`);
    state.bulkIds = [b1.data?.product?.id, b2.data?.product?.id];
  }

  // ── Buyer account surfaces ────────────────────────────────────────────────
  group('Buyer account');
  {
    const addr = await call('/me/addresses', {
      method: 'POST',
      token: state.buyerToken,
      body: { label: 'Home', line1: 'Plot 12, Kampala Road', city: 'Kampala', isDefault: true },
    });
    check('add address', addr.status === 200);
    const addrs = await call('/me/addresses', { token: state.buyerToken });
    check('addresses listed', (addrs.data?.addresses ?? []).length > 0);

    const pay = await call('/me/payment-methods', {
      method: 'POST',
      token: state.buyerToken,
      body: { type: 'momo', label: 'MTN MoMo', phone: '+256700111222', isDefault: true },
    });
    check('add payment method', pay.status === 200);

    const bm = await call('/me/bookmarks/toggle', {
      method: 'POST',
      token: state.buyerToken,
      body: { productId: state.sampleProduct?.id },
    });
    check('bookmark toggle', bm.status === 200 && typeof bm.data?.bookmarked === 'boolean', JSON.stringify(bm.data));
    const bms = await call('/me/bookmarks', { token: state.buyerToken });
    check('bookmarks listed', Array.isArray(bms.data?.products ?? bms.data?.bookmarks));

    const prefs = await call('/me/preferences', {
      method: 'PATCH',
      token: state.buyerToken,
      body: { theme: 'dark', notifyMessages: true },
    });
    check('save preferences', prefs.status === 200);
    const gotPrefs = await call('/me/preferences', { token: state.buyerToken });
    check('theme preference persisted', gotPrefs.data?.preferences?.theme === 'dark', gotPrefs.data?.preferences?.theme);

    const notifs = await call('/me/notifications', { token: state.buyerToken });
    check('notifications load', Array.isArray(notifs.data?.notifications));
    const unreadOne = (notifs.data?.notifications ?? []).find((n) => !n.read);
    if (unreadOne) {
      const markRead = await call(`/me/notifications/${unreadOne.id}/read`, { method: 'PATCH', token: state.buyerToken });
      check('mark notification read', markRead.status === 200 && markRead.data?.ok === true, JSON.stringify(markRead.data));
      const markReadPost = await call(`/me/notifications/${unreadOne.id}/read`, { method: 'POST', token: state.buyerToken });
      check('mark-read also accepts POST', markReadPost.status === 200);
    }

    const hist = await call('/me/search-history', { token: state.buyerToken });
    check('search history endpoint', Array.isArray(hist.data?.history));

    const me = await call('/auth/me', { token: state.buyerToken });
    check('profile loads', me.data?.user?.id === state.buyerId);
    const upd = await call('/auth/me', {
      method: 'PATCH',
      token: state.buyerToken,
      body: { displayName: 'Renamed Buyer' },
    });
    check('profile update', upd.status === 200 && upd.data?.user?.displayName === 'Renamed Buyer');

    // Account settings writes city + avatar in the same request.
    const full = await call('/auth/me', {
      method: 'PATCH',
      token: state.buyerToken,
      body: {
        displayName: 'Renamed Buyer',
        phone: '+256700111222',
        city: 'Entebbe',
        profilePhotoUrl: 'https://example.com/avatar.jpg',
      },
    });
    check('profile accepts city and photo', full.status === 200, `got ${full.status}`);
    check('city persisted', full.data?.user?.city === 'Entebbe', full.data?.user?.city);
    check('photo persisted', full.data?.user?.profilePhotoUrl === 'https://example.com/avatar.jpg');
    check('createdAt exposed for the settings page', !!full.data?.user?.createdAt);

    const partial = await call('/auth/me', {
      method: 'PATCH', token: state.buyerToken, body: { phone: '+256788999000' },
    });
    check(
      'partial profile update preserves other fields',
      partial.data?.user?.city === 'Entebbe' && partial.data?.user?.displayName === 'Renamed Buyer'
    );

    const badPhoto = await call('/auth/me', {
      method: 'PATCH', token: state.buyerToken, body: { profilePhotoUrl: 'not-a-url' },
    });
    check('invalid photo URL rejected', badPhoto.status === 400, `got ${badPhoto.status}`);

    // Preference round-trip backing the Appearance/Notifications tabs.
    const settingsPrefs = await call('/me/preferences', {
      method: 'PATCH',
      token: state.buyerToken,
      body: { theme: 'light', language: 'sw', currency: 'KES', notifyMarketing: true },
    });
    check(
      'preferences save',
      settingsPrefs.status === 200 && settingsPrefs.data?.preferences?.theme === 'light' &&
        settingsPrefs.data?.preferences?.currency === 'KES' &&
        settingsPrefs.data?.preferences?.notifyMarketing === true
    );
    const prefPartial = await call('/me/preferences', {
      method: 'PATCH', token: state.buyerToken, body: { notifyMessages: false },
    });
    check(
      'partial preference update keeps the rest',
      prefPartial.data?.preferences?.theme === 'light' && prefPartial.data?.preferences?.notifyMessages === false
    );
  }

  // ── Seller dashboard ──────────────────────────────────────────────────────
  group('Seller dashboard');
  {
    const stats = await call('/seller/dashboard/stats', { token: state.sellerToken });
    check('dashboard stats load', stats.status === 200);
    check('revenue present', typeof stats.data?.stats?.revenueUgx === 'number');
    check('followers count', typeof stats.data?.stats?.followers === 'number', String(stats.data?.stats?.followers));
    check('unread messages count', typeof stats.data?.stats?.unreadMessages === 'number');
    check('pending-approval count', typeof stats.data?.stats?.pendingApproval === 'number');
    check('products by status', typeof stats.data?.stats?.productsByStatus?.approved === 'number');
    check('sales series (14d)', (stats.data?.salesSeries ?? []).length === 14);
    check('top products list', Array.isArray(stats.data?.topProducts));

    const settings = await call('/seller/store-settings', { token: state.sellerToken });
    check('store settings load', settings.status === 200);

    const patched = await call('/seller/store-settings', {
      method: 'PATCH',
      token: state.sellerToken,
      body: { storeDescription: `E2E updated ${uniq}` },
    });
    check('store settings save', patched.status === 200);

    const openState = await call('/seller/open-state', {
      method: 'PATCH',
      token: state.sellerToken,
      body: { isOpen: false },
    });
    check('open/closed toggle', openState.data?.isOpen === false);
    await call('/seller/open-state', { method: 'PATCH', token: state.sellerToken, body: { isOpen: true } });

    const orders = await call('/seller/orders', { token: state.sellerToken });
    check('seller orders list', Array.isArray(orders.data?.orders));
  }

  // ── CMS ───────────────────────────────────────────────────────────────────
  group('CMS');
  {
    for (const slug of ['about', 'terms', 'privacy', 'buyer-protection']) {
      const page = await call(`/cms/${slug}`);
      check(`cms/${slug} loads`, page.status === 200 && !!(page.data?.page?.body ?? page.data?.body));
    }
    const about = await call('/cms/about');
    const body = about.data?.page?.body ?? about.data?.body ?? '';
    check('about page names the founder', /Kato Fred/.test(body));
  }

  // ── Cleanup ───────────────────────────────────────────────────────────────
<<<<<<< HEAD
  // ── Gallery editing (PATCH carries the full photo set) ────────────────────
  group('Product gallery editing');
  {
    const imgA = 'https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=800';
    const imgB = 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=800';
    // The approval-workflow group left this product approved.

    const withGallery = await call(`/seller/products/${state.newProductId}`, {
      method: 'PATCH',
      token: state.sellerToken,
      body: { mediaUrls: [imgA, imgB] },
    });
    const gp = withGallery.data?.product;
    check('gallery edit is accepted', withGallery.status === 200, JSON.stringify(withGallery.data).slice(0, 120));
    check(
      'gallery stores the photos in order',
      Array.isArray(gp?.mediaUrls) && gp.mediaUrls.join() === [imgA, imgB].join(),
      JSON.stringify(gp?.mediaUrls),
    );
    check('gallery edit returns the listing to review', gp?.status === 'pending', `status=${gp?.status}`);

    // Re-approve, then check what a signed-out buyer actually sees.
    await call(`/admin/products/${state.newProductId}/approve`, { method: 'POST', token: state.adminToken });
    const publicOne = await call(`/products/${state.newProductId}`);
    check(
      'public product exposes the full gallery',
      publicOne.status === 200 && (publicOne.data?.product?.mediaUrls ?? []).length === 2,
      JSON.stringify(publicOne.data?.product?.mediaUrls),
    );
    check(
      'primary image matches the first gallery slot',
      publicOne.data?.product?.imageUrl === imgA,
      String(publicOne.data?.product?.imageUrl),
    );

    // Removing a photo is the same full-replace contract: the gallery
    // shrinks and the primary photo follows the remaining slot.
    const trimmed = await call(`/seller/products/${state.newProductId}`, {
      method: 'PATCH',
      token: state.sellerToken,
      body: { mediaUrls: [imgB] },
    });
    check(
      'removing a photo is supported',
      trimmed.data?.product?.mediaUrls?.length === 1,
      JSON.stringify(trimmed.data?.product?.mediaUrls),
    );
    check(
      'primary photo follows the first slot',
      trimmed.data?.product?.imageUrl === imgB,
      String(trimmed.data?.product?.imageUrl),
    );

    // Leave the fixture approved for the rest of the suite.
    await call(`/admin/products/${state.newProductId}/approve`, { method: 'POST', token: state.adminToken });

    // Full-form clients (Android edit, web inventory) send the UNCHANGED
    // gallery on every save — that must not unpublish a live listing.
    const noChange = await call(`/seller/products/${state.newProductId}`, {
      method: 'PATCH',
      token: state.sellerToken,
      body: { mediaUrls: [imgB], stockQuantity: 3 },
    });
    check(
      'unchanged gallery keeps an approved listing live',
      noChange.data?.product?.status === 'approved',
      `status=${noChange.data?.product?.status}`,
    );
    check(
      'the stock change from that same save applied',
      noChange.data?.product?.stockQuantity === 3,
      `${noChange.data?.product?.stockQuantity}`,
    );
  }

  group('Image upload & serving');
  {
    // A real 1x1 PNG.
    const png = Buffer.from(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==',
      'base64'
    );
    const form = new FormData();
    form.append('image', new Blob([png], { type: 'image/png' }), 'pixel.png');
    const upRes = await fetch(`${V1}/uploads/images`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${state.sellerToken}` },
      body: form,
    });
    const upData = await upRes.json().catch(() => ({}));
    check(
      'signed-in user can upload a product image',
      upRes.status === 200 && typeof upData.url === 'string',
      JSON.stringify(upData).slice(0, 120)
    );

    if (upData.url) {
      // No auth header: a signed-out buyer must be able to see product photos.
      const abs = upData.url.startsWith('http') ? upData.url : `${BASE}${upData.url}`;
      const got = await fetch(abs);
      const bytes = Buffer.from(await got.arrayBuffer());
      check('uploaded image is served publicly', got.status === 200, `got ${got.status}`);
      check('served bytes match the upload', bytes.equals(png), `${bytes.length} vs ${png.length} bytes`);
      check(
        'served with the original content type',
        String(got.headers.get('content-type') || '').includes('image/png'),
        got.headers.get('content-type') || ''
      );
    }
    const anonUp = await fetch(`${V1}/uploads/images`, { method: 'POST', body: new FormData() });
    check('anonymous image upload rejected (401)', anonUp.status === 401, `got ${anonUp.status}`);
  }

  group('Password reset');
  {
    const forgot = await call('/auth/forgot-password', {
      method: 'POST',
      body: { identifier: `buyer_${uniq}@test.ug` },
    });
    check(
      'forgot-password accepts an email identifier',
      forgot.status === 200 && forgot.data?.ok === true,
      JSON.stringify(forgot.data).slice(0, 120)
    );

    const forgotPhone = await call('/auth/forgot-password', {
      method: 'POST',
      body: { identifier: '+256 700 000 000' },
    });
    check(
      'forgot-password accepts the phone identifier too',
      forgotPhone.status === 200 && forgotPhone.data?.ok === true
    );

    // The response for an unknown identifier must be byte-identical to the
    // "ok" response minus the dev-only link, so a probe cannot learn whether
    // an account exists.
    const forgotUnknown = await call('/auth/forgot-password', {
      method: 'POST',
      body: { identifier: `nobody_${uniq}@nowhere.test` },
    });
    check(
      'unknown identifier gets a constant answer (no enumeration)',
      forgotUnknown.status === 200 && forgotUnknown.data?.ok === true && !forgotUnknown.data?.devLink,
      JSON.stringify(forgotUnknown.data)
    );

    const link = forgot.data?.devLink || forgotPhone.data?.devLink;
    check(
      'non-mailer build exposes the reset link to the test',
      typeof link === 'string' && link.includes('token='),
      String(link).slice(0, 80)
    );

    if (link) {
      const token = new URL(link).searchParams.get('token');

      const tampered = await call('/auth/reset-password', {
        method: 'POST',
        body: { token: `${token}x`, password: 'NewPass99!' },
      });
      check('tampered token rejected', tampered.status === 400, `got ${tampered.status}`);

      const reset = await call('/auth/reset-password', {
        method: 'POST',
        body: { token, password: 'NewPass99!' },
      });
      check(
        'valid token resets the password',
        reset.status === 200 && reset.data?.ok === true,
        JSON.stringify(reset.data).slice(0, 120)
      );

      const replay = await call('/auth/reset-password', {
        method: 'POST',
        body: { token, password: 'NewPass99!' },
      });
      check('reset token is single-use (replay rejected)', replay.status === 400, `got ${replay.status}`);

      const oldLogin = await call('/auth/login', {
        method: 'POST',
        body: { email: `buyer_${uniq}@test.ug`, password: 'Buyer123!' },
      });
      check('old password no longer works', oldLogin.status === 401, `got ${oldLogin.status}`);

      const newLogin = await call('/auth/login', {
        method: 'POST',
        body: { email: `buyer_${uniq}@test.ug`, password: 'NewPass99!' },
      });
      check(
        'new password works for the same account',
        newLogin.status === 200 && newLogin.data?.user?.id === state.buyerId,
        `got ${newLogin.status}`
      );
    }
  }

=======
>>>>>>> origin/master
  group('Cleanup');
  {
    if (state.newProductId) {
      const del = await call(`/seller/products/${state.newProductId}`, {
        method: 'DELETE',
        token: state.sellerToken,
      });
      check('seller can delete own product', del.status === 200);
    }
    // Remove every other row this run created so the catalog stays clean.
    const leftovers = [state.draftId, state.dropId, ...(state.bulkIds ?? [])].filter(Boolean);
    let removed = 0;
    for (const id of leftovers) {
      const r = await call(`/seller/products/${id}`, { method: 'DELETE', token: state.sellerToken });
      if (r.status === 200) removed++;
    }
    check('test artefacts cleaned up', removed === leftovers.length, `${removed}/${leftovers.length}`);

    const stillThere = await call('/products?pageSize=100');
    check(
      'no test products left in the public catalog',
      !(stillThere.data?.products ?? []).some((p) => /E2E Test|Bulk [AB]|Followed Store Drop|No Image|Draft /.test(p.title)),
      (stillThere.data?.products ?? []).filter((p) => /E2E|Bulk|Drop/.test(p.title)).map((p) => p.title).join(', ')
    );
    check(
      'every public product still has a real image',
      (stillThere.data?.products ?? []).every((p) => /^https?:\/\//.test(p.imageUrl || ''))
    );

    // Throwaway accounts this run registered are removed so repeated runs do
    // not silt up the users table. The seller/admin are permanent seed rows.
    const throwaway = [state.buyerId, state.outsiderId].filter(Boolean);
    let purged = 0;
    for (const id of throwaway) {
      const r = await call(`/admin/users/${id}`, { method: 'DELETE', token: state.adminToken });
      if (r.status === 200) purged++;
    }
    check('test accounts removed', purged === throwaway.length, `${purged}/${throwaway.length}`);
  }

  // ── Summary ───────────────────────────────────────────────────────────────
  console.log(`\n${'═'.repeat(64)}`);
  console.log(`\x1b[1mTOTAL: ${passed + failed} checks — \x1b[32m${passed} passed\x1b[0m, ` +
    `${failed ? `\x1b[31m${failed} failed\x1b[0m` : '0 failed'}`);
  if (failures.length) {
    console.log('\n\x1b[31mFailures:\x1b[0m');
    failures.forEach((f) => console.log(`  • ${f}`));
  }
  console.log('═'.repeat(64));
  process.exit(failed ? 1 : 0);
}

main().catch((err) => {
  console.error('\n\x1b[31mSuite crashed:\x1b[0m', err);
  process.exit(1);
});
