# poc

The smallest thing that answers the hypothesis: YAML rules select and capture, one fixed jq
program joins the captures into a per-test catalog, and `yq` supplies the suite file.

## Contents

| Item | What it is |
| --- | --- |
| `run.sh` | extraction → violations → catalog → diff against `expected/` |
| `fixtures/` | 7 Java files and `testng.xml`; syntax-valid, deliberately not compiled |
| `config/` | known annotations, TMS annotations + regex, group vocabulary |
| `jq/` | `catalog.jq` (join), `policy.jq` (configured checks), `violations.jq`, `summary.jq`, `suite.jq` |
| `expected/` | committed `catalog.json`, `violations.json`, `catalog.md` |
| `alternatives/semgrep/` | the hands-on comparison: rules, runner, normalised result |
| `tmp/` | run output; ignored by the repo's `tmp/` rule |

## Prerequisites

```bash
npm install --global @ast-grep/cli    # or: cargo install ast-grep --locked / brew install ast-grep
# jq >= 1.7
# yq >= 4.30 (mikefarah) — `-p xml` support
# optional: docker (Semgrep comparison only)
```

Verified with ast-grep **0.51.3**, jq **1.7**, yq **4.54.3**, Docker **26.1.5**. The PoC refuses
to run without ast-grep, jq or yq and prints the install line above.

The fixtures are **not compiled** (there is no JVM in this environment). ast-grep only parses
syntax, so the test is valid Java but nothing proves it compiles. Do not read a green run as
compilation safety.

## Run it

```bash
bash poc/run.sh            # writes poc/tmp/* and diffs against poc/expected/
bash poc/run.sh --update   # accept new snapshots
```

## Scan another project

```bash
bash poc/scan-project.sh /path/to/repo/src/test
bash poc/scan-project.sh /path/to/repo --suite src/test/resources/testng.xml
bash poc/scan-project.sh /path/to/repo --policy    # bundled config; replace poc/config for real values
```

Outputs land in `poc/tmp/scan/<name>/`: `catalog.json`, `violations.json`, `catalog.md`, plus the
raw `extract.ndjson` / `violate.ndjson`. Paths are relative to the git root, the suite is
optional, and policy is **off by default** because the bundled group vocabulary and TMS rules are
fixture-specific.

Tried on `testng-live-reporter/reporter-json/src/test` (17 classes): 75 tests, 1 provider, 0
hooks, 79 findings — 75 missing descriptions (the project uses bare `@Test`), one class suffix
(`ParallelSampleTests`), two missing assertions and one `Thread.sleep`, all in an intentional
reporter fixture.

## Result

On this fixture: 3 packages, 6 classes, 11 tests, 2 providers, 5 hooks, 25 violations
(11 ast-grep + 14 policy). The output is deterministic — two consecutive runs hash identically
because no timestamp is emitted and paths are relative.

The catalog contains exactly what the hypothesis claims: class/package/method, description,
method and class groups with the union, data provider and provider class, `@Parameters` values,
hooks with scope, custom annotations with the TMS value, expected exceptions, timeouts, and
assertion calls.

## Why the pipeline has three stages

- **ast-grep** is the selector: literals, annotation names, structural predicates. It has no
  type resolution and no XML support.
- **jq** owns the join (range containment), project policy and output shape.
- **yq** owns `testng.xml`, which is not a language ast-grep parses.

## Limits

- Fixtures are not compiled; no type information.
- `poc/run.sh` requires yq. `xmllint` cannot produce JSON, so it is not a substitute here.
- Paths in the snapshots are relative to the repository root; run from the research root.
- Semgrep needs Docker here and its default ignore list skips any `tests/` directory; see
  `alternatives/semgrep/run.sh`.
