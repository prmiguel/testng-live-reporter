# TestNG listener interfaces — what each one is for

Every interface here extends `ITestNGListener`. That marker matters: `TestNG#addListener`
dispatches on the interfaces a listener implements, so one class can implement several and
receive all their callbacks.

All signatures below were read from `org.testng:testng:7.11.0`; samples are written against
that version.

## How to register a listener

Three ways, and they differ in one important respect:

```bash
# 1. ServiceLoader — zero config, but TestNG loads it itself, so a test that also injects
#    its own listener gets TWO instances (see the warning at the end).
#    File: src/test/resources/META-INF/services/org.testng.ITestNGListener
echo 'com.acme.MyListener' > src/test/resources/META-INF/services/org.testng.ITestNGListener
```

```xml
<!-- 2. suite XML -->
<suite name="s">
  <listeners><listener class-name="com.acme.MyListener"/></listeners>
</suite>
```

```java
// 3. Programmatic, and the only form that lets you pass constructor arguments.
TestNG testNG = new TestNG();
testNG.addListener(new MyListener(someDependency));
testNG.run();
```

`@Listeners(MyListener.class)` on a class or interface also works, but it can only instantiate
listeners with a no-arg constructor.

---

## Which one do I want?

| I want to… | Use |
|---|---|
| Report per-test outcomes | `ITestListener` |
| Report configuration (`@Before*`/`@After*`) failures | `IConfigurationListener` |
| Know when the whole run starts/ends | `IExecutionListener` |
| Time individual invocations precisely | `IInvokedMethodListener` |
| Group by test class | `IClassListener` |
| Work at suite level | `ISuiteListener` |
| Rewrite the plan before it runs | `IAlterSuiteListener`, `IMethodInterceptor`, `IAnnotationTransformer` |
| Wrap execution or provide fixtures | `IHookable`, `IConfigurable` |
| Observe data providers | `IDataProviderListener`, `IDataProviderInterceptor` |
| Write a custom report at the end | `IReporter` |
| Supply listener instances | `ITestNGListenerFactory` |
| Control ordering | `ListenerComparator` |

---

## 1. `ITestListener` — per-test outcomes

`onTestStart`, `onTestSuccess`, `onTestFailure`, `onTestSkipped`,
`onTestFailedWithTimeout`, `onTestFailedButWithinSuccessPercentage`,
`onStart(ITestContext)`, `onFinish(ITestContext)`.

**Good for:** anything that needs one row per test. Every CI report, dashboard and database
sink starts here. It is also the only place you learn a test *skipped* (dependency or
configuration) rather than failed.

```java
public class OutcomeListener implements ITestListener {
    @Override public void onTestSuccess(ITestResult r) {
        System.out.println("PASS " + r.getMethod().getQualifiedName());
    }
    @Override public void onTestFailure(ITestResult r) {
        System.out.println("FAIL " + r.getMethod().getQualifiedName()
                + " -> " + r.getThrowable().getMessage());
    }
    @Override public void onTestSkipped(ITestResult r) {
        // getThrowable() is usually null here: skip is not a failure
        System.out.println("SKIP " + r.getMethod().getQualifiedName());
    }
}
```

Caveat that bites everybody: `onTestStart` and `IInvokedMethodListener.beforeInvocation`
both fire per invocation. Publishing "started" from both is exactly the duplicate-`STARTED`
bug this repository had to fix — pick one.

## 2. `IConfigurationListener` — setup/teardown outcomes

`onConfigurationSuccess`, `onConfigurationFailure`, `onConfigurationSkip`, `beforeConfiguration`.

**Good for:** explaining why tests were skipped. Without it, a `@BeforeMethod` that throws
surfaces only as `SKIPPED` with the throwable discarded. Implement the **two-argument**
`onConfigurationFailure(ITestResult, ITestNGMethod)`: TestNG calls both overloads, so
implementing the one-argument form as well publishes every failure twice.

```java
public class ConfigListener implements IConfigurationListener {
    @Override
    public void onConfigurationFailure(ITestResult result, ITestNGMethod method) {
        // result.getMethod() is the configuration method that threw;
        // the second argument is the TEST method it was preparing.
        Throwable cause = result.getThrowable();
        System.out.println("SETUP FAILED " + result.getMethod().getMethodName()
                + " before " + method.getMethodName() + ": " + cause);
    }
}
```

## 3. `IExecutionListener` — run boundaries

`onExecutionStart()`, `onExecutionFinish()`.

**Good for:** opening and closing a run-scoped resource exactly once — a file, a connection,
a run header. Runs outside any suite, so it is the right place to record a run ID.

```java
public class RunListener implements IExecutionListener {
    @Override public void onExecutionStart() { System.out.println("RUN START"); }
    @Override public void onExecutionFinish() { System.out.println("RUN END"); }
}
```

No arguments at all — there is no context here, which is why run-scoped *data* has to be
passed through a constructor rather than read from the callback.

## 4. `IInvokedMethodListener` — every invocation, including config

`beforeInvocation(IInvokedMethod, ITestResult)`, `afterInvocation(IInvokedMethod, ITestResult)`.

**Good for:** precise timing and separating configuration methods from test methods. This is
the only callback pair with reliable before/after around an individual invocation.

```java
public class TimingListener implements IInvokedMethodListener {
    @Override
    public void beforeInvocation(IInvokedMethod m, ITestResult r) {
        // isTestMethod() false => this is a @Before*/@After* invocation
        if (m.isTestMethod()) System.out.println("start " + m.getTestMethod().getMethodName());
    }
    @Override
    public void afterInvocation(IInvokedMethod m, ITestResult r) {
        // last a real duration, not a listener estimate
        System.out.println("took " + (r.getEndMillis() - r.getStartMillis()) + "ms");
    }
}
```

`afterInvocation` fires *before* `ITestListener`'s result callbacks, so do not emit a
terminal status here — you would race the outcome handlers.

## 5. `IClassListener` — test-class boundaries

`onBeforeClass(ITestClass)`, `onAfterClass(ITestClass)`.

**Good for:** grouping and per-class fixtures/durations. Derivable by grouping
`ITestListener` events by class name, so only worth adding when you want the boundary
explicitly (e.g. a class-level DB row) or need to time `@BeforeClass`/`@AfterClass`.

```java
public class ClassListener implements IClassListener {
    @Override public void onBeforeClass(ITestClass c) {
        System.out.println("class start " + c.getRealClass().getSimpleName());
    }
    @Override public void onAfterClass(ITestClass c) {
        System.out.println("class end " + c.getRealClass().getSimpleName());
    }
}
```

## 6. `ISuiteListener` — suite boundaries

`onStart(ISuite)`, `onFinish(ISuite)`.

**Good for:** per-suite totals. Note `onFinish` can attest `suite.getResults()`, which is the
only place you get TestNG's own aggregated counts without parsing anything.

```java
public class SuiteListener implements ISuiteListener {
    @Override public void onFinish(ISuite suite) {
        suite.getResults().forEach((name, result) ->
                System.out.println(name + " passed=" + result.getTestContext().getPassedTests().size()));
    }
}
```

Fires once per suite, so with `parallel="suites"` you get interleaved callbacks from several
suites — treat any shared state as concurrent.

## 7. `IAlterSuiteListener` — rewrite the plan before it runs

`alter(List<XmlSuite>)`.

**Good for:** injecting parameters, thread counts or extra suites based on environment. The
only listener that can change *what* runs, before anything is scheduled.

```java
public class EnvSuiteListener implements IAlterSuiteListener {
    @Override public void alter(List<XmlSuite> suites) {
        if (System.getenv("CI") != null) suites.forEach(s -> s.setThreadCount(4));
    }
}
```

## 8. `IMethodInterceptor` — filter/reorder the chosen tests

`intercept(List<IMethodInstance>, ITestContext) -> List<IMethodInstance>`.

**Good for:** running a subset (only tests changed in a PR, only one class), or reordering.
Works after selection, so it sees resolved instances and their data providers.

```java
public class OnlyChangedInterceptor implements IMethodInterceptor {
    @Override
    public List<IMethodInstance> intercept(List<IMethodInstance> methods, ITestContext context) {
        Set<String> changed = Set.of("com.acme.LoginTest");   // from your diff
        return methods.stream()
                .filter(m -> changed.contains(m.getMethod().getRealClass().getName()))
                .toList();
    }
}
```

Return the list you want run; returning an empty list runs nothing and reports nothing.

## 9. `IAnnotationTransformer` — rewrite annotations at runtime

Five `transform` overloads: `ITestAnnotation`, `IConfigurationAnnotation`,
`IDataProviderAnnotation`, `IFactoryAnnotation`, `IListenersAnnotation`.

**Good for:** centrally applying a retry analyzer, timeout, invocation count or
`setEnabled(false)` without editing every test. The third and fourth arguments are the class
constructor and the method, so you can match on either.

```java
public class RetryTransformer implements IAnnotationTransformer {
    @Override
    public void transform(ITestAnnotation annotation, Class testClass,
                          Constructor constructor, Method method) {
        if (method != null && method.getName().startsWith("flaky")) {
            annotation.setRetryAnalyzer(OnceRetryAnalyzer.class);
            annotation.setTimeOut(30_000);
        }
        // setEnabled(boolean) lives on IParameterizable, two levels up the hierarchy
        if (method != null && method.getName().endsWith("Manual")) {
            annotation.setEnabled(false);
        }
    }
}
```

`transform` is called once per annotation during analysis, so keep it cheap and side-effect
free.

## 10. `IHookable` — wrap the test invocation

`run(IHookCallBack callBack, ITestResult testResult)`.

**Good for:** cross-cutting concerns around the test method itself — retry at the call site,
tenant/context injection, or short-circuiting a test. **You must call
`callBack.runTestMethod(testResult)` yourself**; if you don't, the test never runs but is not
reported as skipped either.

```java
public class TenantHook implements IHookable {
    @Override
    public void run(IHookCallBack callBack, ITestResult result) {
        TenantContext.set(System.getenv("TENANT"));
        try {
            callBack.runTestMethod(result);       // forgetting this silently skips the test
        } finally {
            TenantContext.clear();
        }
    }
}
```

`callBack.getParameters()` exposes the resolved arguments, so this is also the place to
rewrite a data-provider row before it runs.

## 11. `IConfigurable` — the same, for configuration methods

`run(IConfigureCallBack callBack, ITestResult testResult)`.

**Good for:** wrapping `@Before*`/`@After*` invocations specifically — e.g. skipping an
`@AfterClass` teardown unless the setup succeeded, or applying a per-suite credential.

```java
public class TeardownGuard implements IConfigurable {
    @Override
    public void run(IConfigureCallBack callBack, ITestResult result) {
        boolean isTeardown = result.getMethod().isAfterClassConfiguration();
        if (isTeardown && result.getTestClass() == null) return;   // nothing to tear down
        callBack.runConfigurationMethod(result);                   // omitting this skips it
    }
}
```

## 12. `IDataProviderListener` — observe provider execution

`beforeDataProviderExecution`, `afterDataProviderExecution(…, ITestNGMethod, ITestContext)`,
`onDataProviderFailure(ITestNGMethod, ITestContext, RuntimeException)`.

**Good for:** telling "the provider threw" apart from "the test failed". That distinction is
genuinely not available from ordinary test callbacks — the tests never run and can look
skipped.

```java
public class ProviderListener implements IDataProviderListener {
    @Override
    public void onDataProviderFailure(ITestNGMethod method, ITestContext ctx, RuntimeException cause) {
        System.out.println("PROVIDER FAILED " + method.getMethodName() + ": " + cause);
    }
}
```

Rare enough that adding it is a per-project decision rather than a default.

## 13. `IDataProviderInterceptor` — rewrite provider output

`intercept(Iterator<Object[]>, IDataProviderMethod, ITestNGMethod, ITestContext) ->
Iterator<Object[]>`.

**Good for:** trimming or transforming rows before they become invocations (sampling, adding
an environment column). This is the only place you can change the data itself.

```java
public class RowLimiter implements IDataProviderInterceptor {
    @Override
    public Iterator<Object[]> intercept(Iterator<Object[]> rows, IDataProviderMethod provider,
                                        ITestNGMethod method, ITestContext context) {
        List<Object[]> kept = new ArrayList<>();
        rows.forEachRemaining(kept::add);
        return kept.stream().limit(10).iterator();   // smoke mode
    }
}
```

## 14. `IReporter` — your own end-of-run report

`generateReport(List<XmlSuite>, List<ISuite>, String outputDirectory)`, plus
`getConfig()` for reporter-level properties.

**Good for:** one artifact after everything finished, e.g. a summary HTML/JSON whose totals
come from TestNG's own `ISuiteResult` maps rather than your own counters.

```java
public class SummaryReporter implements IReporter {
    @Override
    public void generateReport(List<XmlSuite> suites, List<ISuite> results, String outputDir) {
        results.forEach(suite -> suite.getResults().forEach((name, result) -> {
            ITestContext ctx = result.getTestContext();
            System.out.println(name + " pass=" + ctx.getPassedTests().size()
                    + " fail=" + ctx.getFailedTests().size());
        }));
    }
}
```

Runs after `onExecutionFinish`, so anything you buffered in another listener is available by
then. Note this is a legacy reporter API: `IReporterConfig` exists in 7.11 but most of it is
still evolving, so keep it simple.

## 15. `ITestNGListenerFactory` — supply listener instances yourself

`createListener(Class<? extends ITestNGListener>) -> ITestNGListener`.

**Good for:** listeners that cannot be no-arg constructed. TestNG asks the factory for
instances instead of reflecting, so constructor injection of a repository, clock or config
becomes possible without a static holder.

```java
public class ListenerFactory implements ITestNGListenerFactory {
    private final HistoryRepository repository = new HistoryRepository();
    @Override
    public ITestNGListener createListener(Class<? extends ITestNGListener> type) {
        if (type == HistoryListener.class) return new HistoryListener(repository);
        return null;   // null => let TestNG construct it the usual way
    }
}
```

## 16. `IExecutionVisualiser` — graph debug output

`consumeDotDefinition(String)`.

**Good for:** debugging execution order. TestNG builds a Graphviz dot definition of the test
graph and hands it over; this is genuinely niche, and only fires through
`DynamicGraph.setVisualisers(...)`, not from a normal suite run.

```java
public class GraphDumper implements IExecutionVisualiser {
    @Override public void consumeDotDefinition(String dot) { System.out.println(dot); }
}
```

## 17. `ListenerComparator` — control callback order

`Comparator<ITestNGListener>`; static `sort(...)` helpers.

**Good for:** guaranteeing one listener runs before another, e.g. a masking listener before
your own security listener. Registered via
`testNG.setListenerComparator(...)`, so it is run-scoped rather than annotated.

```java
public class Ordering implements ListenerComparator {
    @Override
    public int compare(ITestNGListener a, ITestNGListener b) {
        boolean aFirst = a instanceof SecurityListener;
        boolean bFirst = b instanceof SecurityListener;
        return Boolean.compare(!aFirst, !bFirst);   // SecurityListener first
    }
}
```

## 18. `ITestNGMethod`/`ITestResult` are not listeners

Easy to mistake for one. They are the objects passed **to** listeners:
`ITestResult` carries status, throwable, timing and `getTestContext()`;
`ITestNGMethod` carries the annotation-derived metadata
(`isBeforeMethodConfiguration()`, `getGroups()`, `getInvocationCount()`).

---

## Pitfall: ServiceLoader gives you a second instance

`META-INF/services/org.testng.ITestNGListener` makes TestNG *also* instantiate your listener
itself. A test that both relies on that file and calls `addListener(new MyListener(...))`
ends up with **two instances and two independent states** — callbacks split between them, and
"why is my counter zero?" follows.

```java
testNG.setListenersToSkipFromBeingWiredInViaServiceLoaders("com.acme.MyListener");
```

This repository hit exactly that: a configuration failure went to the ServiceLoader-created
instance while the injected one had `eventFactory == null`.

## What this repository uses

`LiveReportingListener` implements five interfaces, and two of its methods are deliberate
no-ops:

| Interface | Emits |
|---|---|
| `IExecutionListener` | run boundaries (`onRunStarted`/`onRunFinished`) |
| `ITestListener` | `DISCOVERED`, `IGNORED`, terminal statuses |
| `IInvokedMethodListener` | `STARTED`, `IN_PROGRESS` |
| `IConfigurationListener` | `FINISHED`/`ERROR` with `phase=configuration` |
| `ISuiteListener` | **both methods no-op** — suite boundaries are derivable from test events |

`onTestStart` and `afterInvocation` are also no-ops: the first would double-publish
`STARTED`, the second would race the `ITestListener` outcome callbacks. `IServiceLoaderListener`
does not exist — ServiceLoader is a mechanism, not an interface.
