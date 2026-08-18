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
const API='http://127.0.0.1:3001/api/v1';
let p=0,f=0; const ck=(n,c,x='')=>{c?(p++,console.log('  ✓',n)):(f++,console.log('  ✗',n,x));};
async function call(path,{method='GET',token,body}={}){const h={'content-type':'application/json'};if(token)h.authorization=`Bearer ${token}`;const r=await fetch(API+path,{method,headers:h,body:body?JSON.stringify(body):undefined});let d=null;try{d=await r.json()}catch{};return{status:r.status,data:d};}
const s=Date.now();
const buyer=await call('/auth/register',{method:'POST',body:{email:`andro_${s}@t.test`,password:'Passw0rd!',displayName:'Android Buyer',role:'buyer'}});
const bt=buyer.data.token;
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

const admin=await call('/auth/login',{method:'POST',body:{email:'admin@scottstechx.ug',password:'Admin123!'}});
await call(`/admin/users/${buyer.data.user.id}`,{method:'DELETE',token:admin.data.token});
console.log(`\nResult: ${p} passed, ${f} failed`); process.exit(f?1:0);
