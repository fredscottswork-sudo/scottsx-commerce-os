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

Android previously had **no cart**: its only purchase path was "Buy now" →
`POST /orders/checkout`, which is a hard **503** until Nylon Pay credentials
exist, so buying on the phone always failed. Product detail now says
**Add to cart**, and a cart screen does quantity edits and per-line stock
caps. The Android cart is held **locally** (`CartStore`, in-memory) — the
backend `/me/cart` sync used by the web cart is follow-up work, so COD
checkout from the phone is not yet wired.

**Moderation** — sellers cannot self-publish. New listings enter `pending`;
public reads are approved-only. Content edits revert `approved → pending`,
while price/stock-only edits stay live. Admins approve/reject/suspend with a
reason that reaches the seller.

**Messaging** — in-thread price offers (accept/decline/withdraw, where accepting
one offer voids every other pending offer), photo messages, per-message read
receipts, typing indicators, pin/archive/mute, message retraction, saved quick
replies, inbox filters with whole-inbox counts, and search. **Web only today**:
the Android chat client speaks a `/chat/v2` dialect the backend never shipped
(see known limits), so threads render on the web and stay empty on the phone.

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

**v1 (`scottsx-android`) legacy endpoint dialect.** Much of the old
`V2Client` speaks a dialect the backend never implemented:
`/api/v1/chat/v2/*`, `/api/v1/user/{profile,addresses,payment-methods,…}`
(the real routes are `/api/v1/me/*`), `/api/v1/sellers/v2/*`,
`/api/v1/settings/v2`, `/api/v1/memory/v2/*`, `/api/v1/products/v2/*`,
`/api/v1/uploads/signed-url`, `/api/v1/reports`, `/api/v1/audit/me`,
`/api/v1/support/tickets` (real route: `/api/v1/me/support/tickets`).
Every one of those clients fails soft (null/empty), so the phone shows
empty screens instead of errors. Concretely, on v1: **messaging, nearby
sellers, the settings sub-screens and cloud cart are dead**; the home
feed, product detail, CMS pages, AI assistant, Google sign-in and the
local cart do work. The endpoint remap is tracked as follow-up work.

**v2 (`scottsx-android-v2`) shares the stale dialect for its legacy
surfaces** (same `/chat/v2`, `/user/*`, `/sellers/v2`, `settings/v2`,
`memory/v2`, `products/v2` paths). The rebuilt v2 dashboards do **not** use
them: buyer home, search and seller home run on the live `/api/v1/products`
and `/api/v1/seller/*` families, and `fetchProductsFeed` correctly reads
the `{products:[…]}` object envelope.

**The Android apps have not been compiled in this sandbox.** There is no
JDK-plus-Android-SDK here and `maven.google.com` is unreachable, so
`./gradlew assembleDebug` cannot run locally; CI (`.github/workflows/ci.yml`,
jobs `android` and `scottsx-android-v2`) is where APKs are built. Failures
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
for several clients).

**No live LLM test.** Outbound calls to OpenRouter are blocked from this
sandbox, so the LLM path is unverified; the grounded local engine is what has
been tested.

**Payments** are cash-on-delivery only. Nylon Pay is wired but unconfigured, so
`/orders/checkout` returns 503; the web cart deliberately uses the COD route.
