#!/usr/bin/env bash
# Reproduce the Semgrep comparison on the same fixture.
# Docker-only here (no Python on the host). Bind mounts do not propagate in this
# environment, so files are copied into a stopped container instead.
set -euo pipefail
cd "$(dirname "$0")"

tmp=../../tmp
mkdir -p "$tmp"

command -v docker >/dev/null 2>&1 || { echo "error: docker required" >&2; exit 2; }
docker info >/dev/null 2>&1 || { echo "error: docker daemon unavailable" >&2; exit 2; }

cid=$(docker create --entrypoint /usr/bin/python3 semgrep/semgrep \
  /usr/bin/semgrep scan --config /rules.yml --json --metrics=off \
  --x-ignore-semgrepignore-files /src)
trap 'docker rm -f "$cid" >/dev/null 2>&1 || true' EXIT

docker cp rules.yml "$cid":/rules.yml
docker cp ../../fixtures/java "$cid":/src
docker start -a "$cid" > "$tmp/semgrep.raw.json"

jq '{
  scanned: (.paths.scanned | length),
  counts: (.results | group_by(.check_id) | map({key: .[0].check_id, value: length}) | from_entries),
  findings: ([.results[] | {rule: .check_id, file: (.path | sub("^/src/java/"; "")), line: .start.line}]
             | sort_by(.file, .line, .rule))
}' "$tmp/semgrep.raw.json" > result.json

echo "wrote $(pwd)/result.json"
