# Kotlin model parser check

Runs the app's **shipping** catalogue decoder over **real** captured backend
responses, using a genuine `org.json` implementation — the same library
Android ships. This catches the class of bug the syntax checker cannot see:
a parser that compiles fine but produces wrong values at runtime.

## What it validates today

`V2Client.jsonToProduct` (sliced straight out of the shipping V2Client.kt,
never a copy) plus the domain types it decodes into (`ProductCategory`,
`Brand`, `Seller`, `Product`, `ProductImage`, ...), run over the live
`/api/v1/products` envelope:

* **Envelope shape** — `/products` returns `{ products: [...], total, ... }`,
  a JSON *object*. The old client parsed the body as a bare `JSONArray`, so
  the home feed was silently empty against a healthy backend. The harness
  asserts the envelope so that bug can never come back quietly.
* **Real field mapping** — every value must come from the response:
  `priceMinor` (number, not string), the nested `seller` object, `rating`
  (not the long-removed `productTrustScore` ghost, which previously
  hard-coded 4.4 onto every card), `imageUrl`, `stockQuantity`,
  `oldPriceMinor` (JSON null on most rows — must not crash, never below
  the live price), `isFlashDeal`, `discountPercent`, `location`.

## History

Two earlier defects were found this way and fixed:

1. **`optString` returns the literal string `"null"`** for a JSON null
   (verified against org.json 1.8) — the inbox showed `null` as a timestamp
   on brand-new threads.
2. **`chatTimeLabel` crashed on blank input** — `substring(0, 5)` on an
   empty timestamp threw `StringIndexOutOfBoundsException` on every fresh
   thread.

The messaging models those two lived in have since been superseded (the
shipping chat client speaks `/chat/v2`, which the backend has not yet
implemented — see the known limits in STATUS.md), so the gate now covers
the catalogue feed, which the app actually ships with a matching backend.

## Running it

1. Start the backend (`cd 12_Backend && npm run dev`).
2. Capture live responses and run the harness:

```bash
export JAVA_HOME=/tmp/jdk/jdk4py/java-runtime
export PATH="$JAVA_HOME/bin:$PATH"
tools/parser-check/run.sh
```

The script slices the decoder out of `V2Client.kt` and the domain types out
of `MarketplaceModels.kt` (so it always tests the shipping code, never a
copy), compiles them against a real `org.json` jar, executes the harness,
and fails loudly on any parse anomaly.

No JDK/Kotlin/org.json handy? `tools/fetch-toolchain.sh` restores all three
without root — see the parent README.
