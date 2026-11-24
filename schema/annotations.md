# `@Test` and metadata attributes

TestNG 7.x. Values below are literal-only; a constant or concatenation is not resolved by a
syntactic parser (see `approaches/limits.md`).

| Attribute | Type | Extracted as | Catalog field |
| --- | --- | --- | --- |
| `description` | String | `attr` key + value | `tests[].description` |
| `groups` | String[] | `attr` + strings inside the attr | `tests[].groups` |
| `enabled` | boolean | `attr` value | `tests[].enabled` |
| `priority` | int | `attr` value | `tests[].priority` |
| `dataProvider` | String | `attr` value | `tests[].dataProvider` |
| `dataProviderClass` | Class | `attr` value, `.class` stripped | `tests[].dataProviderClass` |
| `dependsOnMethods` | String[] | `attr` + strings | `tests[].dependsOn` |
| `dependsOnGroups` | String[] | `attr` + strings | `tests[].dependsOn` |
| `expectedExceptions` | Class[] | `attr` value, split and `.class` stripped | `tests[].expectedExceptions` |
| `expectedExceptionsMessageRegExp` | String | `attr` value | `tests[].expectedExceptionsMessageRegExp` |
| `timeOut` | int (ms) | `attr` value | `tests[].timeOut` |
| `invocationTimeOut` | int (ms) | `attr` value | `tests[].invocationTimeOut` |
| `invocationCount` | int | `attr` value | `tests[].invocationCount` |
| `threadPoolSize` | int | `attr` value | `tests[].threadPoolSize` |
| `successPercentage` | int | `attr` value | `tests[].successPercentage` |
| `alwaysRun` | boolean | `attr` value | `tests[].alwaysRun`, `hooks[].alwaysRun` |
| `singleThreaded` | boolean | `attr` value | `tests[].singleThreaded` |
| `retryAnalyzer` | Class | `attr` value, `.class` stripped | `tests[].retryAnalyzer` |

`@Parameters` is a **separate annotation** on the method, not a `@Test` attribute:

```java
@Test(description = "Parameters come from the suite", groups = {"payments"})
@Parameters({"env", "browser"})
public void readsSuiteParameters() { … }
```

Its values are positional strings in the annotation (no `element_value_pair`), collected into
`tests[].parameters`.

Class-level `@Test(groups = {"smoke", "ui"})` applies to every method; the catalog keeps
`classGroups`, `groups` and the union in `effectiveGroups`.

```java
@Test(groups = {"smoke", "ui"})
public class LoginTest extends BaseTest {

    @Test(description = "Valid credentials open the dashboard", groups = {"login"}, priority = 1)
    public void validCredentialsOpenDashboard() { … }
}
```

Extraction path: `rules/extract/annotations.yml` (`annotation`, `attr`, `annotation-string`) and
`poc/jq/catalog.jq` (classification). No rule is specific to `description` or `groups`, so a new
attribute needs no YAML change unless it changes the node shape.
