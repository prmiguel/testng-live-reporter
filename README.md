# TestNG Atlas

TestNG Atlas turns a TestNG (Java) checkout into a structured, diffable catalog of its tests.
It answers "what tests exist, and what are they made of?" without compiling the project, running
the suite, or generating a new script for every scan.

The scanner is declarative: ast-grep YAML rules select and capture structure, one fixed jq
program joins the captures into one record per test, and `yq` optionally reads `testng.xml`.
Output is deterministic — no timestamps, relative paths, stable ordering — so catalogs can be
committed, reviewed and diffed like code.

## Overview

| Layer | Tool | Responsibility |
| --- | --- | --- |
| Selection | ast-grep (YAML rules) | Find packages, classes, methods, annotations, attributes and calls |
| Join | jq (fixed programs) | Containment join, classification, normalisation, policy checks |
| Suite | yq (`-p xml`) | Read `testng.xml`: parameters, groups, classes, listeners |
| Output | jq | `catalog.json`, `violations.json`, `catalog.md` |

What a scan produces:

- **Structure** — packages, classes, superclass, methods and their parameters.
- **Test metadata** — description, groups (method, class and effective union), enabled, priority,
  data provider and provider class, `@Parameters` values, timeouts, invocation counts,
  `expectedExceptions`, dependencies, retry analyzer, custom annotations (including TMS ids).
- **Providers and hooks** — `@DataProvider` declarations and `@Before*`/`@After*` with scope.
- **Violations** — shape checks in YAML (missing assertion, disabled tests, `System.out`,
  `Thread.sleep`, class suffix, broad `expectedExceptions`, …) and policy checks in jq using
  project configuration (group vocabulary, TMS id format/uniqueness, exact description length,
  unresolved providers).
- **Suite configuration** — optional `testng.xml` extraction.

Everything is **declared** metadata. The catalog does not resolve constants, inherited
annotations or annotation transformers; see [docs/limits.md](docs/limits.md).

## Requirements

| Tool | Version used | Needed for |
| --- | --- | --- |
| `ast-grep` | 0.51.3 | every scan |
| `jq` | 1.7+ | every scan |
| `yq` (mikefarah) | 4.30+ | `--suite` / `poc/run.sh` |
| `bash` | — | scripts |

No JVM, Maven/Gradle, Python or Node is needed at runtime. The target project is parsed, never
compiled or executed.

## Quick start

```bash
# scan any TestNG source root
./poc/scan-project.sh /path/to/project/src/test

# include a suite file
./poc/scan-project.sh /path/to/project --suite src/test/resources/testng.xml

# apply the bundled policy config (replace poc/config/* with your own values first)
./poc/scan-project.sh /path/to/project/src/test --policy
```

Results land in `poc/tmp/scan/<name>/`: `catalog.json`, `violations.json`, `catalog.md`.

Self-test against the committed fixtures:

```bash
./poc/run.sh            # scans poc/fixtures and diffs against poc/expected
./poc/run.sh --update   # accept new snapshots
```

## Layout

```text
.
├── README.md
├── sgconfig.yml              ast-grep project: ruleDirs + utilDirs
├── rules/
│   ├── extract/              generic capture rules (structure, annotations, calls)
│   ├── violations/           shape checks (naming, metadata, assertions, execution)
│   └── utils/                shared `testMethod` matcher
├── poc/
│   ├── run.sh                fixture scan + snapshot diff
│   ├── scan-project.sh       scan an arbitrary path (optional suite, optional policy)
│   ├── jq/                   catalog.jq, policy.jq, violations.jq, summary.jq, suite.jq
│   ├── config/               known annotations, TMS annotations + regex, group vocabulary
│   ├── fixtures/             seven Java files + testng.xml (syntax-valid, not compiled)
│   ├── expected/             committed catalog/violations/markdown snapshots
│   └── alternatives/         the Semgrep comparison used to choose ast-grep
├── schema/                   TestNG surface, node kinds, JSON shape, suite mapping
├── examples/                 minimal source → catalog-record snippets
├── docs/
│   └── limits.md             what a syntactic catalog cannot see
└── assets/
    └── catalog-schema.json   JSON Schema for catalog.json
```

`poc/tmp/` is generated and ignored.

## Extending

| Change | Where |
| --- | --- |
| New structural record | add a rule under `rules/extract/`, tag `metadata.record`, extend jq |
| New syntactic violation | add a rule under `rules/violations/` |
| New project value (group, TMS format, length) | edit `poc/config/*`, not YAML |
| New output field | extend `poc/jq/catalog.jq` |

Rules carry a `catalogRule` metadata link (for example `TN-ASRT-001`) to the base TestNG rule
catalog they were designed against.

## Status

Proof of concept extracted from the `testng-ast-grep-catalog` research. The rules, output schema
and scripts are expected to evolve; pin the ast-grep version if you depend on the snapshots.
