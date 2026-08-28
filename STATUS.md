# ScottsTechX — build status

One backend, two clients. Web is built and verified; Android is written and
statically verified but **has never been compiled against the Android SDK**.

## Run it

```bash
cd 12_Backend && npm install && npm run dev     # API + embedded Postgres on :3001
cd web         && npm install && npm run dev    # website on :5173 (proxies /api)
```

Seed logins: `admin@scottstechx.ug` / `Admin123!` · `techhub@scottstechx.ug` / `Seller123!`

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
