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
| 1 | Backend end-to-end | 266 | passing |
| 2 | Google Sign-In (local IdP, no egress) | 23 | passing |
| 3 | Android ⇆ backend contract | 91 | passing |
| 4 | Web UI (real bundle in jsdom, real backend, no mocks) | 217 | passing |
| 5 | TypeScript (backend + web) | — | clean |
| 6 | Android wiring (routes, client calls, reachability) | — | clean |
| 7 | Android resources (icons, colours, themes) | 12 | clean |
| 8 | Kotlin syntax (56 files) | — | clean |
| 9 | Kotlin parsers vs real API JSON | — | passing |

**609 checks.** Every suite cleans up after itself; the database returns to 7
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

Locally, the real Kotlin compiler frontend runs over all 56 files (catching
syntax errors, duplicate declarations, `val` reassignment and import shadowing)
and the real model parsers are executed against real captured API responses
using a real `org.json`. **Still expect to fix some type errors on the first
Gradle build** — see `scottsx-android/tools/README.md` for the two blind spots
(non-exhaustive `when`, type mismatches) and why they are suppressed.

**Android UI screens beyond messaging/nearby/add-product** still use the older
layouts. They compile and call valid endpoints, but have not been reworked.

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
