/**
 * Android ⇆ backend contract test.
 *
 * The Kotlin app cannot be compiled or run in CI here (no Android SDK), so this
 * asserts the other half of the contract: that every endpoint `V2Client.kt`
 * calls exists, and that every JSON field the Kotlin data classes read is
 * actually present in the response.
 *
 * If you add a field to a Kotlin model, add it here too.
 *
 *   node tests/android-contract.mjs
 */
const API=`${process.env.API_BASE || 'http://127.0.0.1:3001'}/api/v1`;
let p=0,f=0; const ck=(n,c,x='')=>{c?(p++,console.log('  ✓',n)):(f++,console.log('  ✗',n,x));};
async function call(path,{method='GET',token,body}={}){const h={'content-type':'application/json'};if(token)h.authorization=`Bearer ${token}`;const r=await fetch(API+path,{method,headers:h,body:body?JSON.stringify(body):undefined});let d=null;try{d=await r.json()}catch{};return{status:r.status,data:d};}
const s=Date.now();
const buyer=await call('/auth/register',{method:'POST',body:{email:`andro_${s}@t.test`,password:'Passw0rd!',displayName:'Android Buyer',role:'buyer'}});
const bt=buyer.data.token;
{
  // Registration now issues an unverified account (the backend gates every
  // private route until the address is proven). In dev the code comes back in
  // the response, so the fixture verifies itself the same way the app's
  // VerifyEmailScreen does.
  const code=buyer.data?.verification?.devCode;
  const cf=await call('/auth/verify/confirm',{method:'POST',token:bt,body:{code}});
  if(cf.status!==200) throw new Error(`fixture could not verify buyer: ${cf.status} ${JSON.stringify(cf.data)}`);
}
const seller=await call('/auth/login',{method:'POST',body:{email:'techhub@scottstechx.ug',password:'Seller123!'}});
const st=seller.data.token, sid=seller.data.user.id;

console.log('\n[auth]');
ck('POST /auth/register -> token+user', !!bt && !!buyer.data.user?.id);
ck('user JSON has the fields CurrentUserPayload reads',
  ['id','email','displayName','phone','role','emailVerified','profilePhotoUrl','city'].every(k=>k in buyer.data.user),
  JSON.stringify(Object.keys(buyer.data.user)));
const me=await call('/auth/me',{token:bt});
ck('GET /auth/me -> {user}', me.status===200 && !!me.data.user);
ck('PATCH /auth/me with city+photo', (await call('/auth/me',{method:'PATCH',token:bt,body:{displayName:'A',phone:'+256700000000',city:'Jinja',profilePhotoUrl:'https://x.io/a.jpg'}})).status===200);

console.log('\n[products]');
const list=await call('/products');
ck('GET /products -> {products}', Array.isArray(list.data.products));
const prod=list.data.products[0];
ck('product JSON has fields the Kotlin model reads',
  ['id','title','priceMinor','imageUrl','stockQuantity','rating','category','seller'].every(k=>k in prod),
  JSON.stringify(Object.keys(prod)));
ck('GET /products/:id -> {product}', (await call(`/products/${prod.id}`)).data.product?.id===prod.id);

console.log('\n[messaging — the new surface]');
const conv=await call('/conversations',{method:'POST',token:bt,body:{sellerId:sid,productId:prod.id}});
const cid=conv.data.conversation.id;
ck('POST /conversations -> {conversation:{id}}', !!cid);
const inbox=await call('/conversations',{token:bt});
ck('GET /conversations -> {conversations,counts,totalUnread}',
  Array.isArray(inbox.data.conversations) && !!inbox.data.counts && typeof inbox.data.totalUnread==='number');
ck('counts has all/unread/pinned/archived/offers',
  ['all','unread','pinned','archived','offers'].every(k=>k in inbox.data.counts));
const c0=inbox.data.conversations.find(c=>c.id===cid);
ck('conversation JSON matches the Kotlin Conversation model',
  ['id','otherParty','lastMessage','lastTime','unread','productId','productTitle','productImageUrl','productPriceMinor','mySide','pinned','archived','muted','pendingOffers','messageCount','lastSenderId'].every(k=>k in c0),
  JSON.stringify(Object.keys(c0)));
ck('otherParty matches the Kotlin OtherParty model',
  ['id','name','role','photoUrl','verified'].every(k=>k in c0.otherParty), JSON.stringify(Object.keys(c0.otherParty)));

const head=await call(`/conversations/${cid}`,{token:bt});
ck('GET /conversations/:id -> {conversation}', head.status===200 && head.data.conversation.id===cid);
ck('head includes otherTyping + location', 'otherTyping' in head.data.conversation && 'location' in head.data.conversation.otherParty);

await call(`/conversations/${cid}/messages`,{method:'POST',token:bt,body:{text:'hi'}});
const img=await call(`/conversations/${cid}/messages`,{method:'POST',token:st,body:{kind:'image',imageUrl:'https://x.io/p.jpg',attachmentName:'p.jpg'}});
ck('POST image message', img.data.message?.kind==='image');
const tr=await call(`/conversations/${cid}/messages`,{token:bt});
ck('GET messages -> {messages,otherTyping}', Array.isArray(tr.data.messages) && 'otherTyping' in tr.data);
const m0=tr.data.messages[0];
ck('message JSON matches the Kotlin ChatMessage model',
  ['id','senderId','text','imageUrl','attachmentName','kind','productId','offerMinor','offerStatus','offerQuantity','replyToId','deletedAt','readByOther','createdAt'].every(k=>k in m0),
  JSON.stringify(Object.keys(m0)));

const off=await call(`/conversations/${cid}/messages`,{method:'POST',token:bt,body:{kind:'offer',offerMinor:250000,offerQuantity:2}});
ck('POST offer -> pending', off.data.message?.offerStatus==='pending' && off.data.message?.offerQuantity===2);
// Kotlin reads these with optLong(); a bigint returned as a JSON string would
// silently parse to 0 and every offer would display as "UGX 0".
ck('offerMinor is a JSON number (Kotlin optLong)', typeof off.data.message?.offerMinor === 'number',
   `got ${typeof off.data.message?.offerMinor}`);
ck('productPriceMinor is a JSON number', head.data.conversation?.productPriceMinor === null ||
   typeof head.data.conversation?.productPriceMinor === 'number',
   `got ${typeof head.data.conversation?.productPriceMinor}`);
// A brand-new thread has lastTime: null. Kotlin's optString would turn that
// into the literal string "null", so the model uses optStringSafe and
// chatTimeLabel tolerates blanks. Assert the shape the parser must survive.
ck('conversation.lastTime is a string or null (both handled by the parser)',
   c0.lastTime === null || typeof c0.lastTime === 'string', `got ${typeof c0.lastTime}`);
const freshConv = await call('/conversations',{method:'POST',token:bt,body:{sellerId:(await call('/products?pageSize=30')).data.products.map(x=>x.seller.id).find(x=>x!==sid)}});
const freshList = await call('/conversations',{token:bt});
const freshRow = freshList.data.conversations.find(c=>c.id===freshConv.data.conversation.id);
ck('a message-less thread really does return a null lastTime',
   freshRow !== undefined && !freshRow.lastTime,
   `lastTime=${JSON.stringify(freshRow?.lastTime)}`);
ck('POST accept offer', (await call(`/conversations/${cid}/offers/${off.data.message.id}`,{method:'POST',token:st,body:{action:'accept'}})).data.ok===true);
ck('POST typing', (await call(`/conversations/${cid}/typing`,{method:'POST',token:bt,body:{typing:true}})).data.ok===true);
ck('PATCH state -> {state}', !!(await call(`/conversations/${cid}/state`,{method:'PATCH',token:bt,body:{pinned:true}})).data.state);
ck('DELETE message -> {ok}', (await call(`/conversations/${cid}/messages/${off.data.message.id}`,{method:'DELETE',token:bt})).data.ok===true);
ck('POST /conversations/:id/read -> {ok}', (await call(`/conversations/${cid}/read`,{method:'POST',token:bt})).data.ok===true);

console.log('\n[quick replies]');
const qr=await call('/me/quick-replies',{method:'POST',token:st,body:{text:'In stock!'}});
ck('POST /me/quick-replies -> 201 {quickReply}', qr.status===201 && !!qr.data.quickReply?.id);
ck('quickReply JSON matches Kotlin model', ['id','text','sortOrder'].every(k=>k in qr.data.quickReply));
ck('GET /me/quick-replies -> {quickReplies}', Array.isArray((await call('/me/quick-replies',{token:st})).data.quickReplies));
ck('DELETE /me/quick-replies/:id', (await call(`/me/quick-replies/${qr.data.quickReply.id}`,{method:'DELETE',token:st})).data.ok===true);

console.log('\n[other endpoints V2Client calls]');
for (const [label, path, tok] of [
  ['GET /me/orders','/me/orders',bt],
  ['GET /me/addresses','/me/addresses',bt],
  ['GET /me/payment-methods','/me/payment-methods',bt],
  ['GET /me/bookmarks','/me/bookmarks',bt],
  ['GET /me/notifications','/me/notifications',bt],
  ['GET /me/refunds','/me/refunds',bt],
  ['GET /me/support/tickets','/me/support/tickets',bt],
  ['GET /me/faqs','/me/faqs',bt],
  ['GET /me/preferences','/me/preferences',bt],
  ['GET /seller/products','/seller/products',st],
  ['GET /seller/store-settings','/seller/store-settings',st],
  ['GET /seller/dashboard/stats','/seller/dashboard/stats',st],
  ['GET /sellers/nearby','/sellers/nearby?lat=0.3476&lng=32.5825',bt],
  ['GET /cms/about','/cms/about',null],
]) { const r=await call(path,{token:tok||undefined}); ck(label, r.status===200, `got ${r.status}`); }

// /seller/profile is referenced by the Kotlin client — does it exist?
const sp=await call('/seller/profile',{token:st});
ck('GET /seller/profile (referenced by V2Client)', sp.status===200, `got ${sp.status} — Kotlin calls this!`);

console.log('\n[nearby — NearbySeller model]');
const nb=await call('/sellers/nearby?lat=0.3476&lng=32.5825&radiusKm=1000');
ck('GET /sellers/nearby -> {sellers,count,liveCount}',
   Array.isArray(nb.data.sellers) && typeof nb.data.count==='number' && typeof nb.data.liveCount==='number');
const ns=nb.data.sellers[0];
ck('nearby seller JSON matches the Kotlin NearbySeller model',
   ['id','name','storeName','description','city','address','verified','rating','logoUrl','lat','lng',
    'live','locationSharing','locationAgeMinutes','isOpen','deliveryFeeUgx','freeAboveUgx','codEnabled',
    'serviceRadiusKm','productCount','newThisWeek','distanceKm','etaMinutes','withinServiceRadius'
   ].every(k=>k in ns), JSON.stringify(Object.keys(ns)));
ck('locationAgeMinutes is int|null (Kotlin optInt/isNull)',
   nb.data.sellers.every(x=>x.locationAgeMinutes===null||typeof x.locationAgeMinutes==='number'));
ck('a store with sharing off keeps a real pin (never 0,0)',
   nb.data.sellers.filter(x=>!x.locationSharing).every(x=>x.lat!==0||x.lng!==0),
   'a non-sharing store lost its position');
ck('nearby seller carries placeLabel (Kotlin NearbySeller.placeLabel)',
   'placeLabel' in ns, JSON.stringify(Object.keys(ns)));

// NearbyScreen sends no radiusKm at all: the marketplace is worldwide and a
// buyer with nothing inside an arbitrary radius must still see the closest
// stores instead of an empty screen.
const nbGlobal = await call('/sellers/nearby?lat=0.3476&lng=32.5825&sort=distance&limit=60');
ck('omitting radiusKm searches globally (what the app now sends)',
   nbGlobal.status === 200 && nbGlobal.data.sellers.length > 0, `got ${nbGlobal.status}`);
ck('response carries total + place (Kotlin NearbyResult)',
   typeof nbGlobal.data.total === 'number' && !!nbGlobal.data.place,
   JSON.stringify(Object.keys(nbGlobal.data)));
ck('place matches the Kotlin Place model',
   ['village','city','region','country','countryCode','accuracyKm','label','shortLabel']
     .every(k => k in nbGlobal.data.place), JSON.stringify(Object.keys(nbGlobal.data.place)));
ck('center.radiusKm is null when no radius was asked for',
   nbGlobal.data.center?.radiusKm === null, JSON.stringify(nbGlobal.data.center));
ck('results are sorted nearest-first',
   nbGlobal.data.sellers.every((x, i, a) => i === 0 || a[i - 1].distanceKm <= x.distanceKm));

// The regression that removing the radius is meant to prevent.
const nbFar = await call('/sellers/nearby?lat=51.5074&lng=-0.1278&sort=distance&limit=5');
ck('a buyer far from every store still sees the nearest ones',
   nbFar.status === 200 && nbFar.data.sellers.length > 0,
   'a distant buyer got an empty list — a radius is still being applied');
ck('distance to a far store is reported honestly (>1000 km)',
   (nbFar.data.sellers[0]?.distanceKm ?? 0) > 1000, `${nbFar.data.sellers[0]?.distanceKm} km`);
ck('a foreign position is named correctly',
   nbFar.data.place?.country === 'United Kingdom', JSON.stringify(nbFar.data.place?.label));

console.log('\n[AI search — AiSearchResult model]');
const ai=await call('/ai/search',{method:'POST',body:{q:'phone'}});
ck('POST /ai/search uses key "q" and returns products',
   ai.status===200 && Array.isArray(ai.data.products) && ai.data.products.length>0, `status ${ai.status}`);
ck('AiSearchResult fields present', ['query','explanation','products'].every(k=>k in ai.data));
ck('AI search products carry a numeric priceMinor',
   ai.data.products.every(x=>typeof x.priceMinor==='number'));
const aiv=await call('/ai/voice-search',{method:'POST',body:{transcript:'show me cheap tvs'}});
ck('POST /ai/voice-search', aiv.status===200 && Array.isArray(aiv.data.products), `status ${aiv.status}`);
const aii=await call('/ai/image-search',{method:'POST',body:{imageUrl:'https://example.com/shoe.jpg'}});
ck('POST /ai/image-search returns detected + products',
   aii.status===200 && Array.isArray(aii.data.products) && 'detected' in aii.data, `status ${aii.status}`);

console.log('\n[cart — Cart / CartItem / CartCheckoutResult models]');
// The Kotlin cart screen leans on exact semantics for every one of these.
const cat = (await call('/products?pageSize=30')).data.products;
const pa = cat[0];
const pb = cat.find(x => x.seller.id !== pa.seller.id) || cat[1];

const empty0 = await call('/me/cart', { token: bt });
ck('GET /me/cart on a new account is empty, not 404',
   empty0.status === 200 && empty0.data.items.length === 0, `status ${empty0.status}`);
ck('an empty cart still carries the fields the model reads',
   ['items','subtotalMinor','itemCount','currency'].every(k => k in empty0.data),
   JSON.stringify(Object.keys(empty0.data)));

const add1 = await call('/me/cart', { method:'POST', token: bt, body:{ productId: pa.id, quantity: 1 } });
ck('POST /me/cart returns the whole recomputed cart, not just the line',
   add1.status === 200 && Array.isArray(add1.data.items) && 'subtotalMinor' in add1.data,
   `status ${add1.status} keys ${JSON.stringify(Object.keys(add1.data))}`);
ck('a cart line carries every field CartItem parses',
   ['productId','quantity','title','priceMinor','stockQuantity','imageUrl','status',
    'sellerId','sellerName','lineTotalMinor'].every(k => k in add1.data.items[0]),
   JSON.stringify(Object.keys(add1.data.items[0])));

// The screen's "+" button relies on POST *adding* to the existing quantity.
const add2 = await call('/me/cart', { method:'POST', token: bt, body:{ productId: pa.id, quantity: 2 } });
ck('POST /me/cart adds to the existing quantity rather than replacing it',
   add2.data.items[0].quantity === 3, `quantity became ${add2.data.items[0]?.quantity}`);
ck('itemCount counts units, not lines', add2.data.itemCount === 3, `itemCount ${add2.data.itemCount}`);

// ...while PATCH sets it absolutely. Mixing these up would silently multiply orders.
const patched = await call(`/me/cart/${pa.id}`, { method:'PATCH', token: bt, body:{ quantity: 2 } });
ck('PATCH /me/cart/:productId sets the quantity absolutely',
   patched.data.items[0].quantity === 2, `quantity became ${patched.data.items[0]?.quantity}`);
ck('lineTotalMinor is recomputed server-side',
   patched.data.items[0].lineTotalMinor === patched.data.items[0].priceMinor * 2);
ck('subtotalMinor matches the sum of the lines',
   patched.data.subtotalMinor === patched.data.items.reduce((t,x)=>t+x.lineTotalMinor,0));

const over = await call('/me/cart', { method:'POST', token: bt, body:{ productId: pa.id, quantity: 9999 } });
ck('the cart refuses to exceed available stock (409)', over.status === 409, `status ${over.status}`);
ck('the stock refusal explains itself', /left in stock/i.test(over.data.error||''),
   JSON.stringify(over.data));

const ghost = await call(`/me/cart/${pb.id}`, { method:'PATCH', token: bt, body:{ quantity: 1 } });
ck('PATCH on an item that is not in the cart is 404', ghost.status === 404, `status ${ghost.status}`);

await call('/me/cart', { method:'POST', token: bt, body:{ productId: pb.id, quantity: 1 } });
const del = await call(`/me/cart/${pb.id}`, { method:'DELETE', token: bt });
ck('DELETE /me/cart/:productId removes just that line and returns the cart',
   del.status === 200 && del.data.items.length === 1 && del.data.items[0].productId === pa.id,
   `${del.data.items?.length} line(s) left`);

const zeroed = await call(`/me/cart/${pa.id}`, { method:'PATCH', token: bt, body:{ quantity: 0 } });
ck('PATCH to quantity 0 deletes the line', zeroed.data.items.length === 0, JSON.stringify(zeroed.data.items));

const noAuth = await call('/me/cart');
ck('the cart is private (401 without a token)', noAuth.status === 401, `status ${noAuth.status}`);

const emptyCheckout = await call('/me/cart/checkout', { method:'POST', token: bt, body:{ phone:'0770000000' } });
ck('checking out an empty cart is refused with 409', emptyCheckout.status === 409, `status ${emptyCheckout.status}`);
ck('the empty-cart refusal explains itself', /empty/i.test(emptyCheckout.data.error||''),
   JSON.stringify(emptyCheckout.data));

// Cash on delivery must work with no payment provider configured, because
// POST /orders/checkout (Nylon Pay) is a hard 503 in this deployment.
const nylon = await call('/orders/checkout', { method:'POST', token: bt, body:{ productId: pa.id, quantity: 1 } });
ck('the Nylon Pay route is still unavailable, so COD is the only buy path',
   nylon.status === 503, `status ${nylon.status} — if this changed, revisit ProductDetailScreen`);

// Stock can fall between adding to the cart and checking out. The cart must
// refuse rather than sell a unit that no longer exists.
{
  const dir = await call('/admin/users?role=seller&pageSize=100', {
    token: (await call('/auth/login',{method:'POST',body:{email:'admin@scottstechx.ug',password:'Admin123!'}})).data.token,
  });
  const email = (dir.data.users||[]).find(u => u.id === pa.seller.id)?.email;
  const sl = await call('/auth/login', { method:'POST', body:{ email, password:'Seller123!' } });
  const stok = sl.data?.token;
  const before = (await call(`/products/${pa.id}`)).data.product.stockQuantity;

  await call('/me/cart', { method:'POST', token: bt, body:{ productId: pa.id, quantity: before } });
  await call(`/seller/products/${pa.id}`, { method:'PATCH', token: stok, body:{ stockQuantity: 1 } });
  const race = await call('/me/cart/checkout', { method:'POST', token: bt, body:{ phone:'0770000000' } });
  ck('checkout refuses when stock dropped after the item was added (409)',
     race.status === 409, `status ${race.status} — this would oversell`);
  ck('the oversell refusal names the product', /left of/i.test(race.data.error||''),
     JSON.stringify(race.data));
  const after = (await call(`/products/${pa.id}`)).data.product.stockQuantity;
  ck('a refused checkout does not decrement stock', after === 1, `stock is ${after}`);
  ck('a refused checkout leaves the cart intact so the buyer can fix it',
     (await call('/me/cart',{token:bt})).data.items.length === 1);

  await call(`/seller/products/${pa.id}`, { method:'PATCH', token: stok, body:{ stockQuantity: before } });
  await call('/me/cart', { method:'DELETE', token: bt });
}

await call('/me/cart', { method:'POST', token: bt, body:{ productId: pa.id, quantity: 1 } });
const done = await call('/me/cart/checkout', { method:'POST', token: bt, body:{ phone:'0770000000' } });
ck('POST /me/cart/checkout succeeds with no payment provider (201)',
   done.status === 201, `status ${done.status}`);
ck('checkout returns every field CartCheckoutResult parses',
   ['orders','orderCount','totalMinor','currency','paymentMode','message'].every(k => k in done.data),
   JSON.stringify(Object.keys(done.data)));
ck('an order line uses the key "amount" (Kotlin maps it to amountMinor)',
   'amount' in (done.data.orders[0]||{}), JSON.stringify(Object.keys(done.data.orders[0]||{})));
ck('checkout is cash on delivery', done.data.paymentMode === 'cod', done.data.paymentMode);
ck('the cart is empty after checking out',
   (await call('/me/cart', { token: bt })).data.itemCount === 0);

console.log('\n[dashboard — the payload the app dashboards render]');
{
  const d = await call('/seller/dashboard/stats', { token: st });
  ck('GET /seller/dashboard/stats -> {stats,topProducts,recentOrders,salesSeries}',
     d.status===200 && !!d.data.stats && Array.isArray(d.data.topProducts) &&
     Array.isArray(d.data.recentOrders) && Array.isArray(d.data.salesSeries));
  ck('stats has every field the Kotlin SellerDashboard model reads',
     ['revenueUgx','revenue30Ugx','orders','orders30','avgOrderValueUgx','totalProducts',
      'lowStock','outOfStock','topProduct','unreadMessages','followers','totalViews',
      'productsByStatus','pendingApproval'].every(k=>k in d.data.stats),
     JSON.stringify(Object.keys(d.data.stats)));
  ck('salesSeries rows match the Kotlin SalesPoint model (date/orders/revenue)',
     d.data.salesSeries.length>0 &&
     ['date','orders','revenue'].every(k=>k in d.data.salesSeries[0]));
  ck('topProducts rows match the Kotlin TopProduct model (title/sold)',
     d.data.topProducts.every(t=>'title' in t && 'sold' in t));
  ck('recentOrders rows match the Kotlin SellerRecentOrder model',
     d.data.recentOrders.every(o=>['id','buyerId','productTitle','amount','quantity','status','createdAt','buyerName'].every(k=>k in o)));

  const loc = await call('/seller/location', { token: st });
  ck('GET /seller/location -> {location} (Kotlin SellerLocationState)', loc.status===200 && 'location' in loc.data);
  ck('PATCH /seller/open-state', (await call('/seller/open-state',{method:'PATCH',token:st,body:{isOpen:true}})).status===200);
  const pub = await call('/seller/location',{method:'POST',token:st,body:{lat:0.3476,lng:32.5825,sharing:true}});
  ck('POST /seller/location publishes a live fix', pub.status===200 && !!pub.data.location);
  ck('DELETE /seller/location stops sharing', (await call('/seller/location',{method:'DELETE',token:st})).status===200);

  ck('GET /products?sort=popular (trending feed)', (await call('/products?sort=popular&pageSize=24')).status===200);
  ck('GET /products?flashOnly=true (flash feed)', (await call('/products?flashOnly=true&pageSize=24')).status===200);
  ck('GET /me/favorites/feed (following feed)', (await call('/me/favorites/feed?limit=24',{token:bt})).status===200);
  ck('GET /me/notifications/unread-count -> {unread}',
     typeof (await call('/me/notifications/unread-count',{token:bt})).data.unread==='number');
  ck('POST /me/notifications/read-all', (await call('/me/notifications/read-all',{method:'POST',token:bt})).status===200);
}

const admin=await call('/auth/login',{method:'POST',body:{email:'admin@scottstechx.ug',password:'Admin123!'}});

// That checkout really sold a unit — give it back so the suite stays repeatable.
{
  const dir = await call('/admin/users?role=seller&pageSize=100', { token: admin.data.token });
  const email = (dir.data.users||[]).find(u => u.id === pa.seller.id)?.email;
  if (email) {
    const sl = await call('/auth/login', { method:'POST', body:{ email, password:'Seller123!' } });
    const now = (await call(`/products/${pa.id}`)).data?.product?.stockQuantity;
    if (sl.data?.token && typeof now === 'number') {
      await call(`/seller/products/${pa.id}`, {
        method:'PATCH', token: sl.data.token, body:{ stockQuantity: now + 1 },
      });
    }
  }
}

await call(`/admin/users/${buyer.data.user.id}`,{method:'DELETE',token:admin.data.token});
console.log(`\nResult: ${p} passed, ${f} failed`); process.exit(f?1:0);
