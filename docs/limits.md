# Limits of a syntactic catalog

ast-grep sees tokens and tree shapes. It does not resolve names, types or runtime configuration.
The catalog is accurate about **declared literals** and silent about everything else.

| Limit | Example | Consequence | Mitigation |
| --- | --- | --- | --- |
| Constant values | `@Test(groups = GROUPS.SMOKE)` | group appears as `GROUPS.SMOKE`, not `smoke`; policy flags it unknown | treat as unresolved and list it; resolve with JavaParser |
| String concatenation | `description = "a" + "b"` | value is not the final string | concatenate literals in jq, or mark unresolved |
| Inherited annotations | base class `@Test(groups=…)` | subclass methods do not show it | AST has `superclass`; full resolution needs JavaParser |
| Overridden fixtures | subclass `@BeforeMethod` hides the base one | hook list is per declaration, not effective | review; testng-conventions TN-LIFE-009 |
| `IAnnotationTransformer` | rewrite description/tmsId at runtime | declared value differs from effective value | runtime listener (`tools/testng-runtime-listener.md`) |
| `@Factory`-generated instances | dynamic test instances | some tests have no source declaration | runtime listener |
| Suite excludes / disabled class | `<exclude name="quarantine"/>` | catalog lists the test; it may never run | join with `suite` and annotate |
| Generated `testng.xml` | CI writes the suite | committed file can be stale | scan the generated artefact, not the repo copy |
| Helper named `assert*` | `myAssertions.check(...)` | `missing-assertion` may false-pass | name-based check is documented; use type resolution for certainty |
| Provider row arity | `return new Object[][]{…}` | row count/arity is runtime data | do not infer; report provider parameters only |
| Parallel safety | `@Test(threadPoolSize=…)` | static scan cannot prove isolation | runtime, repeated runs |
| Escaped/unicode literals | `description = "\u00e9"` | length and equality are lexical | jq length counts code points; document |
| Comments | `// @Test` | not matched (AST) | none needed — this is a strength |
| Multi-line annotations | one attribute per line | still matched by AST | none needed — this is a strength |

## What is exact

- Annotation names and presence, including marker vs argument form.
- Literal attribute values: strings, booleans, numbers, arrays of literals.
- Method/class names, packages, parameters, superclass names.
- Structural violations (missing assertion call, `System.out`, `Thread.sleep`, disabled tests).
- Class-level groups **as declared**, and the union with method groups.

## What to say in the output

The catalog should mark unresolved values rather than omit them, and the report should state that
"declared" is not "effective". This mirrors the limit list of the TestNG conventions research this tool was extracted from; the difference here is that the PoC proves
exactly where the line falls rather than describing it.
