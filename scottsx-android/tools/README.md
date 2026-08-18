# Android tooling

## `kotlin-syntax-check.sh`

Runs the real Kotlin compiler frontend over all `app/src/main/java/**/*.kt` and
reports only genuine structural defects, filtering out the "unresolved
reference" noise you get without the Android SDK on the classpath.

This exists because the environment the app was written in had no JDK, no
Android SDK, and no access to `maven.google.com`, so `./gradlew assembleDebug`
could not run. It is a safety net, **not** a substitute for a real build.

### Verified capabilities

Checked by injecting each bug into a source file and confirming the script fails:

| Defect | Detected |
| --- | --- |
| Unbalanced braces / parens | ✅ |
| Missing closing paren in a signature | ✅ |
| Duplicate top-level declarations | ✅ |
| Reassigning a `val` | ✅ |
| Malformed literals / empty initialisers | ✅ |
| `private fun` in one file shadowing an import for sibling files | ✅ |
| Non-exhaustive `when` | ❌ suppressed (see below) |
| Return / argument type mismatches | ❌ suppressed (see below) |

The two blind spots are intentional. Without Compose on the classpath nearly
every expression has an unknown type, so the compiler emits the *same*
diagnostics for correct code as for broken code. Suppressing them keeps the
signal usable; the real Gradle build catches them immediately.

### Usage

```bash
# with kotlinc already on PATH
tools/kotlin-syntax-check.sh

# or point at a specific compiler
tools/kotlin-syntax-check.sh /path/to/kotlinc

# optional: supply a coroutines jar to remove `scope.launch { }` cascades
KOTLIN_EXTRA_CP=/path/to/kotlinx-coroutines-core-jvm.jar tools/kotlin-syntax-check.sh
```

### Getting a toolchain without apt/Android Studio

Both of these come from PyPI/npm and need no root:

```bash
# JDK (Temurin 25, bundled in a wheel)
pip download jdk4py -d /tmp/jdkdl --no-deps
cd /tmp/jdk && unzip -q /tmp/jdkdl/jdk4py-*.whl
chmod +x /tmp/jdk/jdk4py/java-runtime/bin/*
export JAVA_HOME=/tmp/jdk/jdk4py/java-runtime
export PATH="$JAVA_HOME/bin:$PATH"

# Kotlin compiler (2.4.x — older 2.0.x cannot parse the JDK 25 version string)
cd /tmp && npm pack kotlin-compiler@2.4.10 && tar xzf kotlin-compiler-2.4.10.tgz
chmod +x /tmp/package/bin/*

tools/kotlin-syntax-check.sh /tmp/package/bin/kotlinc
```

The parser check additionally needs an `org.json` implementation. There is no
standalone wheel for it, but PySpark ships one:

```bash
pip download pyspark -d /tmp/psdl --no-deps
mkdir -p /tmp/ps && cd /tmp/ps
tar xzf /tmp/psdl/pyspark-*.tar.gz pyspark-*/deps/jars/json-1.8.jar
export ORG_JSON_JAR="$(ls /tmp/ps/pyspark-*/deps/jars/json-1.8.jar)"

tools/parser-check/run.sh "$KOTLINC" "$ORG_JSON_JAR"
```

Or restore the whole toolchain in one step:

```bash
tools/fetch-toolchain.sh && source /tmp/stx-toolchain.env
```

## The real build

On any machine with Android Studio / the SDK:

```bash
./gradlew assembleDebug
```

Expect to fix genuine type errors there first — that is the authoritative check.

## `res-check.sh` — Android resource check

`aapt2` needs the Android SDK, which is not available here, so a reference to a
drawable, mipmap, colour or string that does not exist would survive until the
first Gradle build. This script resolves every such reference statically.

It also enforces two platform rules a compiler can never catch, because they are
runtime-visual bugs:

* **A notification small icon must have an alpha channel.** Android throws away
  the icon's colour and draws its *alpha* silhouette tinted white. An opaque PNG
  therefore renders as a solid white square. The check reads the PNG colour type
  straight from the IHDR (byte 25) and fails on types 0 and 2.
* **An adaptive-icon foreground must be transparent**, or the launcher mask
  shows an opaque square instead of the icon shape.

```bash
tools/res-check.sh      # from scottsx-android/, ~0.2s
```

It is gate 7 in `./verify.sh`.
