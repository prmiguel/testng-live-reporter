# Hooks, data providers and parameters

## Configuration annotations

| Annotation | Scope | Extracted as |
| --- | --- | --- |
| `@BeforeSuite` / `@AfterSuite` | suite | method annotation → `hooks[]` (`scope: suite`) |
| `@BeforeTest` / `@AfterTest` | `<test>` tag, **not** per method | `hooks[]` (`scope: test`) |
| `@BeforeGroups` / `@AfterGroups` | group | `hooks[]` (`scope: groups`), positional group names |
| `@BeforeClass` / `@AfterClass` | class | `hooks[]` (`scope: class`) |
| `@BeforeMethod` / `@AfterMethod` | each method | `hooks[]` (`scope: method`) |

The rules do not encode scope; `catalog.jq` maps the annotation name to a scope. Adding a hook
annotation therefore needs no AST rule, only a name in the classification.

```java
@BeforeMethod(alwaysRun = true)
public void setUp() { tenant = "acme"; }

@BeforeGroups("payments")
public void beforePayments() { … }
```

## Data providers

`@DataProvider` is an annotation on a method, not on a test.

| Attribute | Type | Extracted as |
| --- | --- | --- |
| `name` | String | `providers[].name` (defaults to the method name) |
| `parallel` | boolean | `providers[].parallel` |

A provider is recognised by the annotation alone; the catalog records the declaring class, the
method, whether it is `static`, and its parameter names. Row arity is **not** inferred (the
returned array shape is a runtime fact).

Test references:

```java
@Test(dataProvider = "shared", dataProviderClass = SharedProviders.class)
public void sharedProvider(String value) { … }
```

`dataProvider` and `dataProviderClass` are ordinary attributes, so `tests[]` gets both. The
policy check resolves them against `providers[]` by `(dataProviderClass ?? className, name)` and
reports unresolved references:

```
policy.testng.unresolved-provider  dataProvider 'missing' not found for PaymentDataProviderTest.usesMissingProvider
```

That join is why the catalog needs a whole-project pass rather than per-file output.

## Parameters and `@Optional`

`@Parameters({"env", "browser"})` values are positional strings on the annotation; `@Optional`
supplies per-parameter defaults. Both are extracted as annotations + strings; the catalog keeps
the names and leaves default semantics to the code.
