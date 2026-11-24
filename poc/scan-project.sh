#!/usr/bin/env bash
# Scan any TestNG checkout with this research's rule set.
#
# Usage:
#   poc/scan-project.sh <path> [--suite <testng.xml>] [--out <dir>] [--policy]
#
# --suite   also extract a TestNG suite file (requires yq)
# --out     output directory (default: poc/tmp/scan/<name>)
# --policy  run policy.jq with the bundled fixture config (group vocabulary and
#           TMS rules are project-specific; replace poc/config/* for real use)
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/.." && pwd)"
cfg="$root/sgconfig.yml"

target="${1:-}"
if [ -z "$target" ]; then
  sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
  exit 2
fi
shift

suite=""
out=""
policy=0
while [ $# -gt 0 ]; do
  case "$1" in
    --suite) suite="${2:-}"; shift 2 ;;
    --out) out="${2:-}"; shift 2 ;;
    --policy) policy=1; shift ;;
    *) echo "error: unknown option: $1" >&2; exit 2 ;;
  esac
done

[ -e "$target" ] || { echo "error: no such path: $target" >&2; exit 2; }
for tool in ast-grep jq; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "error: '$tool' is required. See poc/README.md." >&2
    exit 2
  }
done

if [ -d "$target" ]; then
  abs="$(cd "$target" && pwd)"
else
  abs="$(cd "$(dirname "$target")" && pwd)/$(basename "$target")"
fi
abs_dir="$(dirname "$abs")"
repo_root="$(git -C "$abs_dir" rev-parse --show-toplevel 2>/dev/null || echo "$abs_dir")"
rel="${abs#"$repo_root"/}"
[ "$rel" = "$abs" ] && rel="."

if [ -z "$out" ]; then
  slug="$(basename "$abs" | tr -cs 'A-Za-z0-9._-' '-')"
  out="$root/poc/tmp/scan/$slug"
fi
mkdir -p "$out"
out="$(cd "$out" && pwd)"

cd "$repo_root"
ast-grep scan -c "$cfg" --filter '^extract\.' \
  --json=stream --include-metadata "$rel" > "$out/extract.ndjson"

set +e
ast-grep scan -c "$cfg" --filter '^violate\.' \
  --json=stream --include-metadata "$rel" > "$out/violate.ndjson" 2> "$out/violate.err"
status=$?
set -e
if [ "$status" -ne 0 ] && [ "$status" -ne 1 ]; then
  echo "error: violation pass failed with exit $status" >&2
  cat "$out/violate.err" >&2
  exit "$status"
fi

if [ -n "$suite" ]; then
  command -v yq >/dev/null 2>&1 || {
    echo "error: yq is required for --suite (yq -p xml)" >&2
    exit 2
  }
  yq -p xml -o json "$suite" | jq -f "$root/poc/jq/suite.jq" --arg file "$suite" > "$out/suite.json"
else
  printf 'null\n' > "$out/suite.json"
fi

jq -s -f "$root/poc/jq/catalog.jq" \
  --slurpfile suite "$out/suite.json" \
  --slurpfile known "$root/poc/config/known-annotations.json" \
  --arg version "$(ast-grep --version | awk '{print $2}')" \
  "$out/extract.ndjson" > "$out/catalog.json"

if [ "$policy" -eq 1 ]; then
  jq -f "$root/poc/jq/policy.jq" \
    --rawfile vocab "$root/poc/config/group-vocabulary.txt" \
    --rawfile tmsPat "$root/poc/config/tms-id.regex" \
    --slurpfile tmsAnns "$root/poc/config/tms-annotations.json" \
    "$out/catalog.json" > "$out/policy.json"
else
  printf '[]\n' > "$out/policy.json"
fi

jq -n -f "$root/poc/jq/violations.jq" \
  --slurpfile ast "$out/violate.ndjson" \
  --slurpfile policy "$out/policy.json" > "$out/violations.json"

jq -rn -f "$root/poc/jq/summary.jq" \
  --slurpfile cat "$out/catalog.json" \
  --slurpfile vio "$out/violations.json" > "$out/catalog.md"

jq -r '"scanned \(.classes|length) classes: \(.tests|length) tests, \(.providers|length) providers, \(.hooks|length) hooks"' "$out/catalog.json"
jq -r '"violations: \(.violations|length) (\(.violations|map(.ruleId)|unique|length) distinct rules)"' "$out/violations.json"
echo "output: $out"
