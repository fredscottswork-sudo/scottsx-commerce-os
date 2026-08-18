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
./verify.sh          # all nine gates

# Gates 8-9 need a Kotlin toolchain. It lives in /tmp, so if that has been
# cleared, restore it in one step (~15s, no root, no Android SDK):
scottsx-android/tools/fetch-toolchain.sh && source /tmp/stx-toolchain.env
```

| # | Gate | Checks | Status |
|---|------|--------|--------|
| 1 | Backend end-to-end | 343 | passing |
| 2 | Google Sign-In (local IdP, no egress) | 23 | passing |
| 2b | Firebase Authentication (local JWKS, no egress) | 39 | passing |
| 3 | Android ⇆ backend contract | 98 | passing |
| 4 | Web UI (real bundle in jsdom, real backend, no mocks) | 565 | passing |
| 5 | TypeScript (backend + web) | — | clean |
| 6 | Android wiring (routes, client calls, reachability) | — | clean |
| 7 | Android resources (icons, colours, themes) | 12 | clean |
| 7b | Android layout (edge-to-edge insets, overflow, brand artwork) | 46 | passing |
| 7c | Compose API contract (@Composable context, imports, call sites) | 7 | passing |
| 4b | Web viewport audit (resolved CSS cascade, 6 widths) | 6 | passing |
| 8 | Kotlin syntax (57 files) | — | clean |
| 9 | Kotlin parsers vs real API JSON | — | passing |

**1,121 checks.** Every suite cleans up after itself; the database returns to 7
users and 24 approved seeded products with zero residue — including the stock
that checkout consumes, so the suites are repeatable.

Gate 2 stands up a local JWKS server and mints its own RS256 tokens, so the
real verification path (signature, issuer, audience, expiry) is exercised
without reaching Google. Nothing about it is mocked except the key source.

---

## What works

**Commerce** — catalogue, search with facets, cart, COD checkout (one order per
product line), orders, ratings, refunds, addresses, payment methods.

Both platforms now buy the same way. Android previously had **no cart**: its
only purchase path was "Buy now" → `POST /orders/checkout`, which is a hard
**503** until Nylon Pay credentials exist, so buying on the phone always failed.
Product detail now says **Add to cart**, and a new cart screen does quantity
edits, per-line stock caps, removal and cash-on-delivery checkout against the
same endpoints the web cart uses. The cart refuses to oversell — both when
adding and again at checkout, where stock may have dropped in between — and a
listing suspended by moderation after it was added is shown as unavailable
rather than being silently sold.

**Moderation** — sellers cannot self-publish. New listings enter `pending`;
public reads are approved-only. Content edits revert `approved → pending`,
while price/stock-only edits stay live. Admins approve/reject/suspend with a
reason that reaches the seller.

**Messaging** — in-thread price offers (accept/decline/withdraw, where accepting
one offer voids every other pending offer), photo messages, per-message read
receipts, typing indicators, pin/archive/mute, message retraction, saved quick
replies, inbox filters with whole-inbox counts, and search.

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
| Notification small icon was the opaque logo PNG | Android draws small icons from **alpha only** — every push would have shown a solid white square |
| Launch theme was `Material.Light` under a dark UI | White flash before the first frame; invisible status-bar icons |
| Launcher icon was a 1.77 MB nodpi PNG, no adaptive icon | Oversized in the APK; no themed/adaptive icon on API 26+ |
| Web shipped a placeholder "S" tile and an inline-SVG favicon | Web and app did not share a brand |
| A price above int4 committed the INSERT, then 500'd on the read-back cast | Seller saw "integer out of range" while the product **silently existed** — and the raw Postgres message leaked to the client |
| Nothing in 56 Kotlin files referenced `WindowInsets`, at `targetSdk = 35` | Android 15 forces edge-to-edge, so headers rendered under the status bar and bottom bars under the gesture pill — the "doesn't fit on the screen" report, on every screen at once |
| The welcome screen crushed the full brand lockup into a 112dp square | The company wordmark became an illegible smudge, its black backdrop sat as a panel on the gradient, and "ScottsTechX" was then printed again underneath |
| A third of the web logo's height was near-invisible ghost alpha | The lockup rendered undersized and top-heavy on the sign-in pages |
| Four raw stat figures in one `SpaceBetween` row | An unabbreviated UGX revenue is 13 characters; the seller dashboard's numbers ran off a 360dp screen |
| Low stock was stated three times on the seller dashboard | Stat tile + warning banner + an "Inventory alerts" strip whose own comment called it a duplicate; the strip drew its heading even with nothing to show |
| `ProductCard`'s wishlist heart sat on an opaque white disc, state in a local `var` | The discs were the circles making the grid look messy, the heart appeared on sellers' own products, and taps never reached the backend |
| An unreachable backend made the cart render "Your cart is empty" | `Promise.allSettled` swallowed the failure, so a buyer whose basket was safe on the server was told it had been emptied — the worst possible moment to lose trust |

---

## Branding

Web and app render the same artwork. Everything derives from the single source
`scottsx-android/app/src/main/res/drawable-nodpi/logo.png`:

| Surface | Asset |
|---|---|
| Web topbars (both shells) | `web/public/brand/scottstechx-mark.png` |
| Web sign-in / register hero | `web/public/brand/scottstechx-logo-transparent.png` |
| Web favicon, PWA, apple-touch | `web/public/brand/favicon-{32,180,192,512}.png` |
| Android launcher | `mipmap-*/ic_launcher*.png` + adaptive icon (API 26+) |
| Android notifications | `drawable-*/ic_notification.png` |
| Android welcome screen | `R.drawable.logo` |

The source has its dark backdrop baked in, and that backdrop is a textured
vignette rather than flat black — it peaks near luminance 35 while the tagline
reaches 255. Transparent variants therefore key at a measured floor of 42
(ramping to 95), which clears the vignette without eroding the type.

Two real bugs surfaced while wiring this up, both invisible to a compiler:

* **the notification icon was the opaque logo.** Android discards the colour of
  a small icon and draws its *alpha* tinted white, so it would have shipped as a
  solid white square. It is now a flat silhouette (keyed on luminance *and*
  chroma, so the dark-navy `S` survives) with interior holes filled, legible
  down to 24 px.
* **the launch theme was `Material.Light`** under a dark-blue Compose UI, giving
  a white flash before the first frame and dark-on-dark status-bar icons. The
  window background is now the brand token `#05070D`.

Gate 7 (`scottsx-android/tools/res-check.sh`) enforces all of this statically,
since aapt2 cannot run here: it resolves every `@drawable/@mipmap/@color/@string`
and `R.*` reference, and fails if a notification icon is opaque or an adaptive
foreground is not transparent.

## Not done / known limits

**The Android app has not been compiled.** There is no JDK-plus-Android-SDK
here and `maven.google.com` is unreachable, so `./gradlew assembleDebug` cannot
run. **CI now does this for you** — the `android` job in the CI workflow runs
`assembleDebug` on every push, so the first real compile happens automatically
once the workflow is activated (see `ci/README.md`).

Static cross-checks that reduce (but do not remove) the risk of that first
build failing — all currently clean:

| Check | Result |
|---|---|
| App-declared symbols that fail to resolve | 0 of 107 |
| `V2Client` methods called but never defined | 0 |
| `Routes.*` referenced vs defined | 30 / 30, no dead routes |
| Screens not reachable from `AppNavigation` | 0 of 30 |

Every remaining unresolved reference is an Android/Compose/`org.json` symbol
that only the SDK can supply, which is expected here.

Toolchain versions are internally consistent: Compose compiler `1.5.14` pairs
exactly with Kotlin `1.9.24` per Google's compatibility map, and Gradle `8.7`
satisfies AGP `8.5.2`. Note `compileSdk = 35` officially wants AGP `8.6+`;
`8.5.2` generally builds it but may warn. If CI complains, bump AGP to `8.6.0`
in `scottsx-android/build.gradle.kts` — Gradle 8.7 already meets its minimum.

Locally, the real Kotlin compiler frontend runs over all 57 files (catching
syntax errors, duplicate declarations, `val` reassignment and import shadowing)
and the real model parsers are executed against real captured API responses
using a real `org.json`. **Still expect to fix some type errors on the first
Gradle build** — see `scottsx-android/tools/README.md` for the two blind spots
(non-exhaustive `when`, type mismatches) and why they are suppressed.

**Android UI screens beyond messaging/nearby/add-product** still use the older
layouts. They compile and call valid endpoints, but have not been reworked.

**Layout is verified statically, not on a device.** `tools/layout-check.mjs`
(gate 7b) reads the source and proves every one of the 30 screens pads for the
status bar and navigation bar, that no fixed width exceeds a 360dp phone, that
the worst-case revenue figure fits its tile by arithmetic on Roboto advance
widths, and that the brand PNG's real aspect ratio matches the one the Kotlin
declares. That catches the whole class of bug that made the app render off
screen, but it is not a pixel oracle — final spacing still wants one look on a
real handset.

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

**Link previews need server-side rendering.** Every page now sets its own
`<title>`, description, canonical URL and Open Graph / Twitter card tags
(`web/src/hooks/useSeo.ts`), and `GET /sitemap.xml` + `/robots.txt` are served
from the backend. Because the web app is a single-page bundle, those tags are
written by JavaScript after the page boots. Googlebot renders JavaScript and
will see them; the scrapers behind WhatsApp, Facebook, X, Slack and iMessage do
not, so a shared product link falls back to the generic title and description
in `web/index.html`. Fixing that properly means prerendering the public routes
(`/`, `/product/:id`, `/seller/:id`, `/cms/:slug`) at build time or putting the
app behind an SSR host — a follow-up, not a bug in the tags themselves.
