#!/usr/bin/env bash
# Inject the published testng-live-reporter jars onto a classpath and run a TestNG suite.
#
# Usage:
#   ./run.sh [VERSION]        # VERSION = commit hash, tag, or main-SNAPSHOT
#   ./run.sh --verify-only    # skip download/run, re-validate existing output
#
# Nothing here is declared in pom.xml: the reporter is only present on the classpath.
set -euo pipefail

cd "$(dirname "$0")"

# JitPack jar URLs are path-based: the group id keeps its dots, but the repository
# segment is separated by a slash, and the module becomes a path segment too.
REPO_PATH="com/github/prmiguel/testng-live-reporter"
MODULES=(testng-live-reporter-core testng-live-reporter-json)
VALIDATOR="../../.github/scripts/validate-reports.mjs"

if [ "${1:-}" = "--verify-only" ]; then
  node "$VALIDATOR" target/testng-live-reporter
  exit 0
fi

VERSION="${1:-${JITPACK_VERSION:-$(git rev-parse --short HEAD)}}"
echo "Reporter version: $VERSION"

if [ ! -f ../../jitpack.yml ]; then
  echo "error: run this from a checkout of testng-live-reporter" >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# 1. Dependencies of the *consumer* only (TestNG). The reporter is not here.
# ---------------------------------------------------------------------------
echo "==> Resolving consumer dependencies (TestNG only)"
mvn -B -ntp -q dependency:copy-dependencies -DoutputDirectory=lib -DincludeScope=runtime

# ---------------------------------------------------------------------------
# 2. Fetch the two published jars. JitPack builds on demand, so retry.
# ---------------------------------------------------------------------------
fetch() {
  local module="$1" target="lib/$1.jar" attempt
  local max_attempts="${JITPACK_MAX_ATTEMPTS:-20}"
  for attempt in $(seq 1 "$max_attempts"); do
    if curl -fsSL -o "$target" -m 120 \
        "https://jitpack.io/$REPO_PATH/$module/$VERSION/$module-$VERSION.jar" 2>/dev/null \
        && [ -s "$target" ]; then
      echo "==> Fetched $module ($(wc -c <"$target") bytes)"
      return 0
    fi
    rm -f "$target"
    echo "   $module not ready (attempt $attempt/$max_attempts), retrying in 10s"
    sleep 10
  done
  echo "error: could not fetch $module at version '$VERSION'." >&2
  echo "       Check https://jitpack.io/com/github/prmiguel/testng-live-reporter/$VERSION/build.log" >&2
  echo "       — the commit must already contain jitpack.yml, or the build fails." >&2
  return 1
}

mkdir -p lib
for module in "${MODULES[@]}"; do
  fetch "$module"
done

# ---------------------------------------------------------------------------
# 3. Plain javac + java. Both jars self-register through META-INF/services, so
#    no configuration is needed beyond being on the classpath.
# ---------------------------------------------------------------------------
echo "==> Compiling the fixture"
rm -rf out
mkdir -p out
javac -cp 'lib/*' -d out $(find src/main/java -name '*.java')

echo "==> Running TestNG (a failure is expected: SmokeTest.fails)"
export TESTNG_LIVE_RUN_ID="example-$(date +%s)"
java -cp 'out:lib/*' org.testng.TestNG -testclass demo.SmokeTest -usedefaultlisteners false || true

NDJSON=$(ls target/testng-live-reporter/*.ndjson 2>/dev/null | head -1 || true)
if [ -z "$NDJSON" ]; then
  echo "error: no report was written. The reporter jars are probably not on the classpath." >&2
  exit 1
fi

echo
echo "==> Reports"
ls -1 target/testng-live-reporter/
echo
echo "==> Validating"
node "$VALIDATOR" target/testng-live-reporter
