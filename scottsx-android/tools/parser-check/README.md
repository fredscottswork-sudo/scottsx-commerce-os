# Kotlin model parser check

Runs the app's **real** `fromJson` parsers over **real** captured backend
responses, using a genuine `org.json` implementation — the same library Android
ships. This catches the class of bug the syntax checker cannot see: a parser
that compiles fine but produces wrong values at runtime.

## Why it matters

Two defects were found this way and fixed:

1. **`optString` returns the literal string `"null"`** for a JSON null.
   Verified against org.json 1.8:

   ```
   {"lastTime": null}  ->  optString("lastTime") == "null"   // not "" !
   ```

   A brand-new conversation has `lastTime: null`, so the inbox would have shown
   the text `null` as a timestamp. Fixed with `optStringSafe`, which checks
   `isNull` first.

2. **`chatTimeLabel` crashed on blank input.** The old implementation was
   `lastTime.substringAfter("T").substring(0, 5)`, which throws
   `StringIndexOutOfBoundsException` when `lastTime` is empty — i.e. on every
   freshly created thread.

## Running it

1. Start the backend (`cd 12_Backend && npm run dev`).
2. Capture live responses and run the harness:

```bash
export JAVA_HOME=/tmp/jdk/jdk4py/java-runtime
export PATH="$JAVA_HOME/bin:$PATH"
tools/parser-check/run.sh
```

The script extracts the model classes straight out of `MarketplaceModels.kt`
(so it always tests the shipping code, never a copy), compiles them against
org.json, and asserts no field parses to `"null"` and that money values survive
the round trip.

## Obtaining org.json without Maven

`maven.google.com` and `repo1.maven.org` are unreachable from some sandboxes.
A real `org.json` jar ships inside the PySpark wheel:

```bash
pip download pyspark -d /tmp/ps --no-deps
tar xzf /tmp/ps/pyspark-*.tar.gz -C /tmp/ps --wildcards '*/deps/jars/json-1.8.jar'
# -> /tmp/ps/pyspark-*/deps/jars/json-1.8.jar
```
