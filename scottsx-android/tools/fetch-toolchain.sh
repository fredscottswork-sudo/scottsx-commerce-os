#!/usr/bin/env bash
#
# Restores the offline Kotlin toolchain used by the checks in this directory.
#
# Everything lands in /tmp, which is wiped between sessions on hosted sandboxes,
# so this script exists to make that recovery a single command rather than
# archaeology through the README.
#
# Nothing here needs root, apt, or the Android SDK:
#   - JDK             Temurin 25, shipped inside the jdk4py wheel (PyPI)
#   - kotlinc 2.4.10  from npm (2.0.x cannot parse the JDK 25 version string)
#   - org.json        borrowed from the PySpark tarball; no standalone wheel
#
# Usage:
#   tools/fetch-toolchain.sh          # fetch what's missing
#   source /tmp/stx-toolchain.env     # export JAVA_HOME / KOTLINC / ORG_JSON_JAR
set -uo pipefail

KOTLIN_VERSION="${KOTLIN_VERSION:-2.4.10}"
JDK_HOME=/tmp/jdk/jdk4py/java-runtime
KOTLINC_BIN="/tmp/kotlindl/package/bin/kotlinc"

say() { printf '\033[1m%s\033[0m\n' "$1"; }

if [ ! -x "$JDK_HOME/bin/java" ]; then
  say "Fetching the JDK…"
  pip download jdk4py -d /tmp/jdkdl --no-deps -q || { echo "pip download failed" >&2; exit 1; }
  mkdir -p /tmp/jdk && (cd /tmp/jdk && unzip -q -o /tmp/jdkdl/jdk4py-*.whl)
  chmod +x "$JDK_HOME"/bin/* 2>/dev/null || true
fi
"$JDK_HOME/bin/java" -version >/dev/null 2>&1 || { echo "JDK is not runnable at $JDK_HOME" >&2; exit 1; }

if [ ! -x "$KOTLINC_BIN" ]; then
  say "Fetching kotlinc $KOTLIN_VERSION…"
  mkdir -p /tmp/kotlindl && (cd /tmp/kotlindl \
    && npm pack "kotlin-compiler@$KOTLIN_VERSION" >/dev/null 2>&1 \
    && tar xzf "kotlin-compiler-$KOTLIN_VERSION.tgz") || { echo "npm pack failed" >&2; exit 1; }
  chmod +x /tmp/kotlindl/package/bin/* 2>/dev/null || true
fi

ORG_JSON_JAR="$(ls /tmp/ps/pyspark-*/deps/jars/json-1.8.jar 2>/dev/null | head -1 || true)"
if [ -z "$ORG_JSON_JAR" ]; then
  say "Fetching org.json (via the PySpark tarball)…"
  pip download pyspark -d /tmp/psdl --no-deps -q || { echo "pip download failed" >&2; exit 1; }
  mkdir -p /tmp/ps
  (cd /tmp/ps && tar xzf /tmp/psdl/pyspark-*.tar.gz --wildcards '*/deps/jars/json-1.8.jar')
  ORG_JSON_JAR="$(ls /tmp/ps/pyspark-*/deps/jars/json-1.8.jar 2>/dev/null | head -1 || true)"
fi
[ -f "$ORG_JSON_JAR" ] || { echo "could not obtain an org.json jar" >&2; exit 1; }

cat > /tmp/stx-toolchain.env <<EOF
export JAVA_HOME=$JDK_HOME
export PATH="\$JAVA_HOME/bin:\$PATH"
export KOTLINC=$KOTLINC_BIN
export ORG_JSON_JAR=$ORG_JSON_JAR
EOF

say "Ready."
echo "  java    $("$JDK_HOME/bin/java" -version 2>&1 | head -1)"
echo "  kotlinc $KOTLINC_BIN"
echo "  orgjson $ORG_JSON_JAR"
echo
echo "Now run:  source /tmp/stx-toolchain.env && ./verify.sh"
