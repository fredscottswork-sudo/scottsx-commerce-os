# ScottsTechX — build status

## Eighth pass (2026-08-29, late evening) — CI 33266437921 ✅ GREEN

Full auth + home + AI rebuild per the owner's directive:

- **Manual login back for everyone** — email/phone + password, calling
  `POST /auth/login` (the web's own credential store), so accounts made
  on the website sign in natively. Google one-tap kept. NO Apple.
- **Create form back** — name, email, phone, password, confirm (+ store
  name for sellers) → `POST /auth/register` with store seeding.
- **Password reset screen** — request the reset email, then redeem the
  emailed token natively with a new password (`/auth/forgot-password` +
  `/auth/reset-password`); the email's web link keeps working too.
- **Verification screen** — the 6-digit code the web mails, typed into
  six brand boxes → `/auth/verify/confirm`; resend with 60 s cooldown,
  auto-submit on the last digit.
- **Role mismatch AUTO-RESET** — a seller tapping "Buyer" is signed out
  and the Gmail account picker opens instantly for a switch.
- **Branding** — new `BrandedAuthScaffold` (navy→blue gradient, drifting
  glow orbs, sweeping beam, brand lockup, role pill, staggered field
  entrances) under login/sign-up/reset/verify; brand strip (mark +
  wordmark + tagline / Seller Center) pinned on both home screens.
- **Buyer home de-squeezed** — the analytics/personal rail removed
  entirely; ambient drifting orbs animate the background instead.
- **AI screen** — gradient avatar + online dot, per-message entrances,
  gradient send button, brand-tinted user bubbles, no raw model/provider
  leak; picker-cancel can no longer kill the sign-in coroutine.

Artifacts: both `scottsx-test-apk` and `scottsx-v2-test-apk` produced.

## Seventh pass (2026-08-29, evening) — CI 33263315249 & 33263525942 ✅ GREEN ×3 in a row

Deep parity hardening on top of the sixth pass:

- **Web-edited profiles appear in the app.** `AccountSettingsScreen` now
  re-reads `GET /api/v1/auth/me` on open and syncs both session caches —
  name/phone/city/photo/verified changed on the web show up in the app.
- **Notification settings are cross-device.** The web stores
  `notifyOrderUpdates / notifyMessages / notifyMarketing` server-side at
  `GET/PATCH /me/preferences`; the app's toggles now hydrate from the
  server on open and write back on every change — app and web never
  disagree. `V2Client.Settings` gained `notifyMarketing`.
- **Copy fix:** product-photo upload status no longer says "uploaded to
  Firebase Storage" (uploads flow through `V2Client.uploadImage` → the
  backend media route).
- **Parity audits (no code gaps found):** seller→buyer chat entry passes
  the counterparty uid like the web (`chat.open(otherPartyId)`); seller
  dashboard home renders the same sections as the web (stats/alerts/
  14-day revenue chart/top products/recent orders) from the same
  `GET /seller/dashboard/stats` contract; buyer Orders / Refunds /
  Returns / Disputes / ratings are all live V2Client calls — no stub or
  fake data anywhere in the swept screens.

Artifacts (run 33263525942): `scottsx-test-apk` 36.7 MB, `scottsx-v2-test-apk` 30.7 MB.

## Big-fix sweep (2026-08-29, sixth pass) — CI 33262479551 ✅ GREEN, both APKs

**The single root cause behind "dashboard blank / messaging broken / no notifications":** the app never stored a backend JWT. Firebase
sign-in succeeded but no client ever called `/auth/firebase/sign-in` to exchange
the Firebase ID token for a `/api/v1` bearer token, and even the code that
WAS meant to store it (`Session.jwt`) never ran. Every authenticated call
went out without `Authorization` and silently 401'd. Fixed:

- `AuthRepository.syncBackendSession()` runs on every sign-in (password,
  Google-via-Firebase, sign-up) → both session caches get the JWT + backend
  user id. Cold starts re-exchange automatically in `MainActivity`.
- `Session.tokenOrNull()/userIdOrNull()` fall back to the root cache so no
  caller can write to the wrong store again. Chat bubbles now resolve
  "mine vs theirs" correctly.

**Notifications:** `POST_NOTIFICATIONS` permission + runtime prompt, FCM
service finally DECLARED in the manifest, device token registers on every
sign-in, a local welcome notification posts on every fresh login, and a
foreground `ChatWatcher` polls conversations so new messages notify even
while browsing. `windowSoftInputMode=adjustResize` + `imePadding` on the
composer fixes the "AI/chat screen doesn't fit" bug.

**Google-only auth:** Login + Sign-Up are now ONE authentic Google button
(drawn G, no asset). Silent sign-in auto-resume on open — returning users
land on the dashboard with zero taps. Email/password + Apple removed.
All colours fixed (theme-contrast invisible-text bugs impossible).

**Chat works exactly like the web:** ✓/✓✓ read receipts via
`otherLastReadAt`, "typing…" presence via the typing endpoint, read marked
on open and on every new inbound message, optimistic send + refresh.

**Buyer sidebar logout hidden:** the sidebar overlay rendered UNDER the
floating bottom bar (Compose z-order), so the bar covered the sticky
Log-out row. It now renders after the bar, same as the seller home.

**Store settings all real:** the form wrote keys the backend ignores
(`logoUrl`, `whatsapp`, `addressLine1`) so "saved" silently dropped data.
Now every section (profile, business, location, delivery, payments,
notifications, security w/ real `changePassword`, policies) PATCHes the
fields the backend maps and round-trips on reload.

**Cart photos:** product thumbnails now render via Coil AsyncImage.
**Seller-home resilience:** 4 parallel fetches run under `supervisorScope`
with per-fetch `runCatching` — one failure can never strand the tab on the
Loading skeleton again.

One backend, two clients. Web is built and verified; both Android modules
(v1 flagship + v2) compile in CI on every push and upload APK artifacts
(`scottsx-test-apk`, `scottsx-v2-test-apk`). Both apps default to the same
production API origin the website uses (`scottstechx-api.onrender.com`) —
a phone reaches the real backend out of the box; `setBaseUrl(...)` stays
as the local-dev escape hatch.

## Run it

```bash
cd 12_Backend && npm install && npm run dev     # API + embedded Postgres on :3001
cd web         && npm install && npm run dev    # website on :5173 (proxies /api)
```

Seed logins: `admin@scottstechx.ug` / `Admin123!` · `techhub@scottstechx.ug` / `Seller123!`

## Following feed + Support parity (2026-08-29, fifth pass)

* **Seller-side remained complete** — verified the app seller dashboard
  already covers every web SellerDashboard.tsx feature (attention chips,
  4 stat cards + hints, revenue sparkline, store controls, listing
  status badges, best sellers, recent orders) and that the seller
  sidebar mirrors the web's 10 nav destinations.
* **Buyer home "From sellers you follow" rail** — the web dashboard's
  fourth feed tab (``GET /api/v1/me/favorites/feed``). Rendered only
  when the signed-in buyer actually follows sellers with live listings.
* **Buyer home "Sellers you follow" strip** — real logos, ratings and
  product counts, deep-links to storefronts; "Manage" goes to the saved
  sellers screen — web's /buyer/saved equivalent.
* **Support/Help Center rebuilt to web parity**:
  FAQ accordion (``GET /api/v1/me/faqs`` — same public payload),
  "Your tickets" list with real status badges (open/pending/resolved),
  open-ticket dialog (subject + message) that posts to
  ``POST /api/v1/me/support/tickets`` and refreshes the list after send.
  The five dead topic rows (My Orders, Payments, Account & Security,
  Shipping, Selling) are now wired to the real screens instead of no-ops.

## CI green + artifacts (2026-08-29)

* Run **33254997531** (commit `104a4d6`) — all three jobs green:
  backend-and-web, android (compileDebugKotlin + APK), scottsx-android-v2.
  Artifacts: `scottsx-test-apk` (36.7 MB) + `scottsx-v2-test-apk` (30.7 MB).
* Two compile errors slipped through the local syntax gate (it suppresses
  reference-resolution diagnostics without the Android SDK) and were fixed
  from CI annotations: `List` → `MutableList` in BulkImport writeback,
  an invalid `Modifier…androidx.compose.foundation` qualified chain in the
  inventory edit sheet, wrong legacy `OrderStatus` members in
  SellerOrdersScreen, and an FQN-qualified `kotlinx.coroutines.launch` in
  BuyerPersonalRail (extensions can't resolve via FQN).

## Buyer personal rail + order payload parity (2026-08-29, fourth pass)

* New `BuyerPersonalRail` on the buyer home — mirrors the web buyer
  dashboard exactly: stat chips (active orders + lifetime hint, total
  spent, saved, following), the **On the way** strip (real product image,
  store name, amount, status chip) deep-linked to order tracking, and the
  **Sellers you follow** strip (logo avatar, rating, product count).
  Signed-out visitors never see it (token-gated like the rest of the
  personal surfaces).
* `V2Client.MyOrder` now maps `imageUrl` + `storeName` — the backend
  already returned them on `/me/orders`, the app just dropped them.
* Local gates green (kotlin-syntax 130 files, wiring 55 screens, layout
  62/62, compose-contract 18/18, res 9/9).

## Identical-twin theming + dashboard parity (2026-08-29, third pass)

| Area | What changed |
|------|--------------|
| **Dark theme = web dark theme** | New mutable tokens (`CardSurface`, `CardSurfaceAlt`, `OnCard`, `OnCardSecondary`, `OnCardTertiary`, `CardTintSelected`) that swap with `applyThemePalette` — the same mechanism the web's `[data-theme]` flip uses on `--surface`/`--text`. ~50 screens had hardcoded white cards + dark ink hardcodes swept to those tokens, so dark mode now renders the web's dark palette card-for-card and light mode renders pixel-identical to before. Auth/onboarding chrome is deliberately excluded (always-light/dark by design). |
| Seller dashboard | Added the web's attention-chip row (pending approval / out of stock / low stock / unread messages, deep-linked), the web's exact stat set (Revenue-all-time + 30-day hint, Orders + hint, **Avg order value**, Followers + views hint), and the full **Store controls** card: accepting-orders switch (`/seller/open-state`), **Live location** toggle (GPS → `/seller/location` POST/DELETE with last-fix timestamp + permission handling), and listing-status badges. Sidebar gains **Add product** and **Notifications** entries. |
| Seller orders screen | Rebuilt to web parity: stats strip (needs action + pending value, in transit, delivered, revenue), tab groups with counts, product/buyer search filter, refresh, and **Message the buyer** per row (opens the real thread). |
| Buyer orders | Per-row **Message seller**, plus the web's **Rate the product** flow (star dialog → `POST /products/:id/ratings`). Backend `/me/orders` now returns `productId` so ratings can actually link (previously always undefined, which is why the web warned "no linked product"). |
| Refunds | The app's refund dialog posted `amountMinor/transactionId` — a shape the backend rejects. Now it mirrors the web form: pick one of your real orders + reason → `POST /me/refunds { orderId, reason }`. |
| Product model | `Product.rejectionReason` is now mapped (backend `rejection_reason`); the inventory screen shows it on rejected rows like the web does. |

## Seller/buyer web-parity wave (2026-08-29, second pass)

| Area | What changed |
|------|--------------|
| **Critical fix** | The Android Add-Product flow posted to `/api/v1/products/v2/create` — a route that never existed, so every in-app publish was a masked 404. `V2Client.createProduct` now POSTs `/api/v1/seller/products` with the full backend schema (title/description/category/brand/priceMinor/oldPriceMinor/stockQuantity/imageUrl/mediaUrls/location/isFlashDeal/discountPercent/asDraft). |
| Seller inventory | New `SellerInventoryScreen` + route `seller/inventory`: status tabs (all/approved/pending/drafts/rejected) with live counts, inline stock +/- (→ sparse `PATCH`), edit sheet (title/description/price/old price/stock/location), submit-for-review on drafts/rejected rows, delete confirm, add-product shortcut. Sidebar "Products" finally opens it (it previously re-routed to Orders). |
| Seller bulk import | New `BulkImportScreen` + route `seller/bulk-import`: paste CSV (quoted-cell parser matching the web's), preview rows, "stage as drafts" toggle, then per-row `/api/v1/seller/products` creates exactly like the web loop (no bulk endpoint exists). Sidebar gets a "Bulk import" entry. |
| Buyer sidebar | Direct entries for Payments / Addresses / Refunds / Support — all four routes existed but were only reachable through Settings (web shows them in the sidebar). |
| Dark theme | Seller AI assistant "Quick tools" heading rendered `OnLight` ink on the dark panel (invisible in dark mode); now `OnPanel`. |
| Seller mutations | `V2Client` gains `updateSellerProduct` (sparse PATCH), `deleteSellerProduct`, `submitSellerProductForReview` against the real `products.route.ts` surface (`/submit` → status 'pending'). |

## Live-app bug-fix wave (2026-08-29)

| Area | What changed |
|------|--------------|
| Connectivity | v1 defaulted to `http://127.0.0.1:3001` — the "can't reach the marketplace" home error on real phones. Now the same Render origin as the web. |
| Nearby | Rebuilt to web parity: no radius/district fakes; auto GPS fix + `geo/reverse` place naming; `me/location` saved once; sort/search/verified/open filters; live + delivery + COD badges; distance/ETA; Follow-me tracking (250 m refetch); tap → real storefront. |
| Bottom nav | "Cart" tab lied (heart icon → wishlist). Bar now mirrors the web's mobile bottomnav: Home / Explore / Cart / Chats / Alerts, with live `9+`-capped badges (cart, unread chats, unread alerts). |
| AI screen | ChatGPT-style black rebuild on the REAL `/ai/v2/ask` contract (old client sent `message`/`context` and read `reply` — the endpoint never understood it). Grounded product cards open real products; suggestion chips come from `/ai/agents`. |
| Uploads | Product photo picker hit a nonexistent `uploads/signed-url`; now compresses + multipart-POSTs to the real `uploads/images`. |
| Pictures | API-relative image URLs (`/api/v1/uploads/images/…`) could never load in Coil — every ingestion point now absolutizes via `absoluteMediaUrl()`. |
| Avatars | Profile header + chat rows render REAL photos (they rendered initials forever). Account settings gains web-parity profile editing (photo/name/phone/city via `PATCH /auth/me`). |
| Speed | Splash 1500→1000 ms and warms catalog + cart during the beat; Coil respects cache headers again (50 MB disk cache reuse instead of re-downloading every image on cold start). |
| Sidebar ink | Root cause of the "black icons" complaint: `OnLight` ink painted directly on the mutable-dark `PanelLight` surface — now uses the swappable `OnPanel*` tokens in both sidebars + all page-level rows/state text. |
| Auth light-sheet | Login/Sign-up/Verify email/Wrong-role sheets forced to the always-light design (web parity) — they were rendering dark-navy with dark text in dark mode. |
| Bottom nav (seller) | Orders stared from the bar entirely; Messages/Analytics kept the bar; badge states corrected. |
| Blank states | `SettingsBlankHint` family + cart/wishlist/notifications/analytics/reviews empty or error text now legible in dark mode. |
| Compile regression | `ProductDetailScreen` "category vs CharCategory" — pending CI-lurker (would have been a fifth red run); fixed by typing the filter lambda. Verified by the local kotlinc-frontend gate over all 128 files. CI back to green at 36e008c. |
| Ink wave extended | Same dark-ink-on-dark-panel bug class swept through every `PanelInputLight` chip/field (36db114, b6ccd0d) and then through page-level residues in 16 more screens (99fbf8c): cart footer totals, notification rows, section headers, error/empty states in nearby/thread/reviews/storefront/PDP pages. Verified Baselines stayed put: white card rows, white search/composer pills, always-light auth sheets, amber banner, blue-gradient headers. |

## CI ledger (ink-wave completion)

- `92516ac` docs ledger — green
- `36db114` sidebar+chip ink — green
- `b6ccd0d` AuthKit/settings components — green
- `99fbf8c` page-level residues (16 screens) — green
- `2989d70` docs ledger — green
- `5c46cd3` BecomeSeller feature-row ink — green
- `076e74b` web: payment feature card removed, remaining benefits reorganized as an animated strip (staggered entrance, floating icons, hover sheen, gradient underline, live-ring on AI; reduced-motion honoured) — green; verified end-to-end with the real seeded backend (web UI suite 201/201)


## Verify it

```bash
./verify.sh          # all seven gates

# Gates 6-7 need a Kotlin toolchain. It lives in /tmp, so if that has been
# cleared, restore it in one step (~15s, no root, no Android SDK):
scottsx-android/tools/fetch-toolchain.sh && source /tmp/stx-toolchain.env
```

| # | Gate | Checks | Status |
|---|------|--------|--------|
| 1 | Backend end-to-end | 253 | passing |
| 2 | Google Sign-In (local IdP, no egress) | 23 | passing |
| 3 | Android ⇆ backend contract | 91 | passing |
| 4 | Web UI (real bundle in jsdom, real backend, no mocks) | 201 | passing |
| 5 | TypeScript (backend + web) | — | clean |
| 6 | Kotlin syntax (55 files) | — | clean |
| 7 | Kotlin parsers vs real API JSON | — | passing |

**568 checks.** Every suite cleans up after itself; the database returns to 7
users and 24 approved seeded products with zero residue — including the stock
that checkout consumes, so the suites are repeatable.

Gate 2 stands up a local JWKS server and mints its own RS256 tokens, so the
real verification path (signature, issuer, audience, expiry) is exercised
without reaching Google. Nothing about it is mocked except the key source.

---

## What works

**Commerce** — catalogue, search with facets, cart, COD checkout (one order per
product line), orders, ratings, refunds, addresses, payment methods.

**The Android dashboards are now web-parity live surfaces.** Buyer home and
the seller dashboard render only real backend data — the in-memory sample
catalogue (`MarketplaceDataSource` flash deals / recommended / hero benefits)
and the fabricated seller snapshot (`SellerDataSource`: 850k-UGX days, 42 fake
orders, "sales up 18%" insights) no longer feed either screen. Buyer home
mirrors the web hierarchy (live greeting badges from
`/me/notifications/unread-count`, real flash-deal spotlight, real category
row, live "flash deals" rail, rating-sorted feed); the seller dashboard reads
`GET /api/v1/seller/dashboard/stats` (revenue, orders, products, followers,
listing health, top products, real recent orders, 14-day sales series) with
the store open/closed state bound to `PATCH /seller/open-state`. Every
loading state is a shimmer skeleton, every failure is a retryable error
card, and empty catalogues show honest empty states — no demo content, no
feature/payment promotion cards. Theme tokens are now byte-equal with the
web design system (`web/src/styles/globals.css` dark + light).

Android previously had **no cart**: its only purchase path was "Buy now" →
`POST /orders/checkout`, which is a hard **503** until Nylon Pay credentials
exist, so buying on the phone always failed. The cart is now **server-backed
on both platforms**: `CartStore` syncs `GET /me/cart`, applies quantity edits
and removals through the API with an optimistic local overlay, and checkout
runs `POST /me/cart/checkout` (one order per line, stock decremented
atomically, cart emptied by the backend). A listing suspended by moderation
after it was added is shown as unavailable rather than being silently sold.

**Moderation** — sellers cannot self-publish. New listings enter `pending`;
public reads are approved-only. Content edits revert `approved → pending`,
while price/stock-only edits stay live. Admins approve/reject/suspend with a
reason that reaches the seller.

**Messaging** — in-thread price offers (accept/decline/withdraw, where accepting
one offer voids every other pending offer), photo messages, per-message read
receipts, typing indicators, pin/archive/mute, message retraction, saved quick
replies, inbox filters with whole-inbox counts, and search. **Android covers the
core live flow** now: inbox (`GET /conversations`), thread open/creation
(`POST /conversations`), message paging and send, unread counts and
mark-as-read — all over the canonical routes the web uses (a `ChatCache`
shares them with the AI context). Price offers, typing indicators and
quick-reply cabinets are still web-only extras on the roadmap.

**Nearby** — stores re-sort by distance as the buyer moves. A seller who has not
enabled location sharing keeps their last known pin and is labelled
"Last known position" rather than being dropped or shown as live.

Web and Android both search the **whole marketplace** — no radius control and no
city picker. The buyer's position is reverse-geocoded offline (no egress) and
shown as Village / City / Region / Country, so a buyer with no store inside an
arbitrary radius still sees the nearest ones instead of an empty screen.

**Notifications** — favourite a seller, and approval of their new listing fans a
notification out to followers. Web notification centre works today; Android FCM
activates when `12_Backend/secrets/firebase-admin-key.json` is added.

**AI** — catalogue-grounded assistant with six agents, plus text/image/voice
search. Works with zero API key (local engine); upgrades to an LLM when
`OPENROUTER_API_KEY` is set.

**Theming** — dark blue/black by default, black↔white switch, identical tokens
in `web/src/styles/globals.css` and `ScottsTechXColors.kt`.

---

## Bugs found and fixed by testing

These were all found by running things, not by reading code:

| Bug | Impact |
|-----|--------|
| `offerMinor` / `productPriceMinor` returned as JSON **strings** (uncast bigint) | Broke web price formatting; would corrupt Android parsing |
| `optString` returns the literal `"null"` for JSON null (verified vs org.json 1.8) | Inbox rendered `null` as a timestamp on new threads |
| `chatTimeLabel` crashed on a blank timestamp | `StringIndexOutOfBoundsException` on every brand-new thread |
| AI search dropped words shorter than 3 chars | `"tv"` returned the **entire catalogue** |
| AI search ordered by rating only | `"phone"` ranked headphones above the iPhone |
| `private fun QuickChip` shadowed an import | Genuine compile error in a sibling file |
| No runtime permission request | Location and push silently dead on Android 6+/13+ |
| No FCM receiver at all | Phone push could never arrive |
| AddProduct claimed "✅ Published!" | Untrue — the API returns `pending` |
| Profile save called `updateMe` twice, dropping photo and city | Avatar/location edits silently discarded |
| Raw-body parser 500'd on empty JSON bodies | Every bodyless POST/DELETE failed |
| Android had no cart; "Buy now" called a 503 payment route | **Buying anything on the phone was impossible** |

---

## Not done / known limits

**v1 (`scottsx-android`) canonical alignment.** The old `V2Client` dialect
(`/api/v1/chat/v2/*`, `/api/v1/user/*`, `/api/v1/sellers/v2/*`,
`/api/v1/settings/v2`, `/api/v1/memory/v2/*`, `/api/v1/products/v2/*`)
has been remapped onto the routes the backend actually serves
(`/api/v1/me/*`, `/api/v1/conversations`, `/api/v1/sellers/*`,
`/api/v1/seller/*`, `/api/v1/products`), including the chat trio
(open/messages/send), cart, addresses, payment methods, refunds, settings
and support tickets. The last two legacy call sites have been closed since:
the AI assistant relay was already the canonical `/api/v1/ai/v2/ask` (now
routed through the configured app backend instead of a hardcoded localhost),
and abuse/product reports are delivered as support tickets via
`POST /me/support/tickets` — the staff inbox that actually gets read.

**v2 (`scottsx-android-v2`) shares the stale dialect for its legacy
surfaces** (same `/chat/v2`, `/user/*`, `/sellers/v2`, `settings/v2`,
`memory/v2`, `products/v2` paths). The rebuilt v2 dashboards do **not** use
them: buyer home, search and seller home run on the live `/api/v1/products`
and `/api/v1/seller/*` families, and `fetchProductsFeed` correctly reads
the `{products:[…]}` object envelope.

**The Android apps have not been compiled in this sandbox.** There is no
JDK-plus-Android-SDK here and `maven.google.com` is unreachable, so
`./gradlew assembleDebug` cannot run locally; CI (`.github/workflows/ci.yml`,
jobs `android` and `scottsx-android-v2`) is where APKs are built. **CI is
green and both jobs upload APK artifacts** — `scottsx-test-apk` (v1) and
`scottsx-v2-test-apk` (v2), first confirmed on run 33176797805. Failures
that hide inside CI logs are surfaced through `::error` annotations (they
remain readable over the check-runs REST API when log downloads are
blocked). Locally, the real Kotlin compiler frontend runs over every file
(catching syntax errors, duplicate declarations, `val` reassignment and
import shadowing — it caught a genuine redeclared `ScottsTechXColors`,
a duplicate `CmsScreen`, and a `val`-delegate reassignment) and the
shipping catalogue parser is executed against real captured API responses
using a real `org.json`. **Type errors the local gates cannot see may still
surface in Gradle** — see `scottsx-android/tools/README.md` for the two
blind spots (non-exhaustive `when`, type mismatches) and why they are
suppressed.

**Android UI screens beyond messaging/nearby/add-product** still use the older
layouts. They compile, but — per the dialect note above — several no longer
reach a matching backend route.

**FCM is inert until credentials are added.** Drop the service-account JSON at
`12_Backend/secrets/firebase-admin-key.json` and `google-services.json` into
`scottsx-android/app/`. Until then notifications persist in-app only —
`pushToDevices()` returns `{sent:0, configured:false}` and never throws.

**Google Sign-In has not been exercised against the real Google.** All Google
endpoints are blocked from this sandbox, so the suite verifies tokens minted by
a local IdP instead. The production code path is unchanged — only the JWKS URL
differs — but before shipping, confirm in the Google Cloud console that the web
origin is authorised for OAuth client
`911393008938-f0an8p59rlkhimcnn9rdqbtbi1aa9hbk`, or the button will load and
then refuse with `origin_mismatch`. Override the client id with
`VITE_GOOGLE_CLIENT_ID` (web) and `GOOGLE_CLIENT_ID` (backend, comma-separated
for several clients). **On Android, one more registration is required:**
Google rejects sign-in from any APK whose (package `com.scottsx.app`, SHA-1)
pair is not a registered OAuth Android client, and the old code swallowed that
rejection as "Google sign-in cancelled" — the exact bug users hit. Debug
APKs are now pinned to a shared repo keystore
(`scottsx-android*/keystores/debug.keystore`), whose SHA-1/SHA-256 must be
registered **once** in the Google Cloud console — full steps in
`scottsx-android/keystores/README.md`. The Gradle build prints the
fingerprints (prefix `GOOGLE SIGN-IN`) into every build log, and the login
flow now reports the true Google status (developer error, network error,
internal error) instead of pretending the user cancelled.

**No live LLM test.** Outbound calls to OpenRouter are blocked from this
sandbox, so the LLM path is unverified; the grounded local engine is what has
been tested.

**Payments** are cash-on-delivery only. Nylon Pay is wired but unconfigured, so
`/orders/checkout` returns 503; the web cart deliberately uses the COD route.

## 2026-08-29 — Official STX logo installed + shaped
- User supplied the official ScottsTechX logo (blue ST + silver X monogram on
  near-black, "SCOTTSTECHX / ENTERPRISES (U) LTD / INNOVATE. INTEGRATE. ELEVATE.").
  Upload file wasn't on disk → art faithfully regenerated (two shapes) and
  post-processed with PIL.
- brand_mark.png: square center-crop, rounded-corner alpha tile (512px) →
  installed to `res/drawable-nodpi/brand_mark.png` + `logo.png` (nodpi + default).
- brand_lockup.png: content-cropped, edge-feathered transparent blend (1400×731)
  → `res/drawable-nodpi/brand_lockup.png` for auth top + splash (dissolves into navy).
- Buyer/Seller home strips + BrandedAuth card now use the pre-shaped logo tile
  directly at 36dp (rounded 11dp clip) — inner gradient box removed.
- Gates: wiring ✓ compose-contract ✓ layout ✓ resources ✓

## 2026-08-29 — Cinematic STX splash opening
- Official monogram pre-sliced into splash_s / splash_t / splash_x PNGs on ONE
  shared 1613x506 transparent canvas (letters generated in the official racing
  style: brushed royal-blue S & T, chrome silver X with spikes) so each letter
  animates independently and reassembles perfectly.
- New SplashScreen: frame-clock timeline — space-bloom → S slams in L (overshoot+
  un-rotate) → T drops from top → X slashes in R (stretch-spin) → impact flash +
  2 energy rings → double chrome shimmer sweep (offscreen layer + SrcIn so the
  beam lights only the letters) → breathing emblem + 26 twinkling sparks →
  4.2x zoom-through-camera exit. Tap skips to exit. Splash never waits on the
  network (LiveMarketplace.warm + CartStore.warm still race the window).
- Gates: wiring ✓ compose-contract ✓ layout ✓ resources ✓

## 2026-08-29 — STX splash shipped
- CI fix b963dc8 (KDoc brackets) — run 33276055595 SUCCESS, artifacts:
  scottsx-test-apk 35,101,367 B + scottsx-v2-test-apk 30,679,709 B.

## 2026-08-29 — Launch-window STX + loading-hold splash + login fixes
- STX visible from the icon TAP: Android 12+ windowSplashScreenAnimatedIcon
  (launch_stx.png) + pre-31 windowBackground layer-list (launch_center.png
  monogram on surface_dark), both theme variants updated. Compose splash frame
  0 is pixel-identical (letters assembled) -> zero seam at handover.
- Splash choreography v2: assembled hold -> burst scatter (S/T/X fly apart) ->
  spring fusion -> impact flash + rings -> double chrome shimmer -> IGNITION
  HOLD (breathing, sparks, ripple every 1.7s, shimmer every 1.9s) that SUSTAINS
  until LiveMarketplace.state leaves Idle/Loading (min 3.2s, hard cap 8.5s,
  tap skips) -> zoom-exit. Network never gates the handoff.
- Login bugfixes: V2Client warmPost/apiCallWarm (28-42s budgets + one retry)
  for auth/login, auth/register, auth/verify/request + Firebase hand-off —
  kills the "No connection" false alarm while the free-tier API wakes; honest
  cold-server copy; GoogleSignInHelper double-picker guard (orphaned
  continuation/spinner-stuck) with cancellation cleanup + describeFailure
  already maps DEVELOPER_ERROR (google-services.json has NO android OAuth
  client yet — one-tap Google needs SHA-1 registered in Firebase Console;
  email login is the reliable path until then).
- layout-check gate: fullscreen exemption for SplashScreen + assertion now
  matches the letter-slice design.

## 2026-08-29 — user-directed revert of welcome/selector/login changes
- Reverted d719f84 + 968f1dd: onboarding back to original ExoPlayer video
  slides 1 & 3 + photo mosaic slide 2; role selector back to the original
  nested-pill cards; KineticBackground removed.
- Reverted c3d5b90's login-side edits: V2Client, GoogleSignInHelper and
  LoginScreen restored byte-for-byte to b963dc8 (pre-fix state).
- KEPT: the STX opening (c3d5b90) — launch-window icon/layer-list, letter
  flight splash, hold-while-loading — untouched, per user instruction.

## 2026-08-29 — locked to the validated base (615ef16) + STX opening only
- User validated run #141 (615ef16 "official STX logo") as nearly-successful.
  Verified tree == 615ef16 EXCEPT the opening sequence (SplashScreen letter
  choreography + launch-window icon/layer-list + themes + gate) — nothing else.
- Bug sweep at this locked state: wiring ✓ (56 screens routed) contracts ✓
  layout ✓ (63 checks: insets, contrast, overflow, bars) resources ✓; zero
  TODO/TBD stubs; zero empty onClick handlers anywhere in screens/components.
