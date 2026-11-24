# schema

The TestNG surface this research extracts, mapped to the rule that captures it.

## Contents

| Item | What it is |
| --- | --- |
| `node-kinds.md` | Pinned Java node kinds, fields and the traps (`@Test` vs `@Test(...)`, annotation-in-modifiers) |
| `json-output.md` | ast-grep `--json=stream` record shape, flags and exit codes |
| `annotations.md` | `@Test` attributes, `@Parameters`, class-level groups |
| `hooks-and-providers.md` | `@Before*`/`@After*`, `@DataProvider`, parameter semantics |
| `suite-xml.md` | `testng.xml` elements, yq normalisation, the XML gap |
| `custom-annotations.md` | Generic annotation capture, TMS model, extension points |

## Surface → rule

| Surface | Rule | Doc |
| --- | --- | --- |
| package, class, method, parameter | `extract.testng.*` in `rules/extract/structure.yml` | `node-kinds.md` |
| annotation name, attributes, strings | `rules/extract/annotations.yml` | `annotations.md`, `custom-annotations.md` |
| calls (assert/log/sleep) | `rules/extract/calls.yml` | `annotations.md` (verification) |
| suite XML | yq step in `poc/run.sh` | `suite-xml.md` |

Not extractable from source text: effective annotations after `IAnnotationTransformer`,
inherited/overridden annotations, constant group names, provider row arity, tests excluded by the
suite. See `../docs/limits.md`.
