#!/usr/bin/env bash
# Extract a TestNG catalog from the fixtures and diff it against the committed snapshots.
# Usage: poc/run.sh [--update]
set -euo pipefail

cd "$(dirname "$0")/.."

FIXTURES=poc/fixtures/java
SUITE_XML=poc/fixtures/testng.xml
OUT=poc/tmp
EXPECTED=poc/expected

for tool in ast-grep jq yq; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "error: '$tool' is required. See poc/README.md for install commands." >&2
    exit 2
  }
done

mkdir -p "$OUT" "$EXPECTED"

VERSION="$(ast-grep --version | awk '{print $2}')"

# Pass 1: extraction. hint severity, so a clean exit.
ast-grep scan -c sgconfig.yml --filter '^extract\.' \
  --json=stream --include-metadata "$FIXTURES" > "$OUT/extract.ndjson"

# Pass 2: violations. Exit 1 means matches were found, which is expected.
set +e
ast-grep scan -c sgconfig.yml --filter '^violate\.' \
  --json=stream --include-metadata "$FIXTURES" > "$OUT/violate.ndjson" 2> "$OUT/violate.err"
status=$?
set -e
if [ "$status" -ne 0 ] && [ "$status" -ne 1 ]; then
  echo "error: ast-grep violation pass failed with exit $status" >&2
  cat "$OUT/violate.err" >&2
  exit "$status"
fi

yq -p xml -o json "$SUITE_XML" \
  | jq -f poc/jq/suite.jq --arg file "$SUITE_XML" > "$OUT/suite.json"

jq -s -f poc/jq/catalog.jq \
  --slurpfile suite "$OUT/suite.json" \
  --slurpfile known poc/config/known-annotations.json \
  --arg version "$VERSION" \
  "$OUT/extract.ndjson" > "$OUT/catalog.json"

jq -f poc/jq/policy.jq \
  --rawfile vocab poc/config/group-vocabulary.txt \
  --rawfile tmsPat poc/config/tms-id.regex \
  --slurpfile tmsAnns poc/config/tms-annotations.json \
  "$OUT/catalog.json" > "$OUT/policy.json"

jq -n -f poc/jq/violations.jq \
  --slurpfile ast "$OUT/violate.ndjson" \
  --slurpfile policy "$OUT/policy.json" > "$OUT/violations.json"

jq -rn -f poc/jq/summary.jq \
  --slurpfile cat "$OUT/catalog.json" \
  --slurpfile vio "$OUT/violations.json" > "$OUT/catalog.md"

if [ "${1:-}" = "--update" ]; then
  cp "$OUT/catalog.json" "$OUT/violations.json" "$OUT/catalog.md" "$EXPECTED/"
  echo "updated $EXPECTED"
  exit 0
fi

failed=0
for f in catalog.json violations.json catalog.md; do
  if ! diff -u "$EXPECTED/$f" "$OUT/$f"; then
    failed=1
  fi
done

if [ "$failed" -ne 0 ]; then
  echo "error: output differs from $EXPECTED (run with --update to accept)" >&2
  exit 1
fi

echo "ok: catalog, violations and summary match $EXPECTED"
