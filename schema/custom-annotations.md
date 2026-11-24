# Custom annotations (including the TMS id)

Every annotation is extracted generically:

- `extract.testng.annotation` records the annotation name and range.
- `extract.testng.attr` records named attributes.
- `extract.testng.annotation-string` records string literals anywhere inside an annotation,
  which covers both `@TmsId("TMS-101")` and `@Parameters({"env", "browser"})`.

Classification happens in jq, not in YAML:

- Anything on a method whose name is not in `poc/config/known-annotations.json` becomes an entry
  in `tests[].customAnnotations` (`{name: value-or-null}`).
- The TMS annotations are listed separately in `poc/config/tms-annotations.json`; their value is
  checked against `poc/config/tms-id.regex`.

```java
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface TmsId {
    String value();
}
```

```java
@Test(description = "Valid credentials open the dashboard")
@TmsId("TMS-101")
public void validCredentialsOpenDashboard() { … }
```

Result:

```json
"customAnnotations": {"TmsId": "TMS-101"}
```

## Extending without code

| Change | Edit |
| --- | --- |
| Adopt a different TMS annotation (`@TmsLink`, `@TestRailCaseId`) | `poc/config/tms-annotations.json` |
| Change the id format | `poc/config/tms-id.regex` |
| Stop treating an annotation as custom | add it to `poc/config/known-annotations.json` |
| Extract a new attribute of an existing annotation | nothing: `attr` already captured it |

A new annotation **shape** (for example an annotation holding a nested annotation) would need a
rule, because the generic `attr`/string rules do not recurse into nested annotations. That is the
one case where the YAML changes.
