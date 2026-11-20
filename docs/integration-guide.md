# 🔌 Integration & Usage Guide

There are multiple ways to integrate **TestNG Live Reporter** into your projects, from zero-configuration dependency injection to programmatic usage and zero-POM classpath injection.

---

## 💡 Overview of Components

Understanding the difference between the two main JARs:

| Artifact | Role | Auto-discovered By |
|---|---|---|
| `testng-live-reporter-core` | TestNG Lifecycle Listener (`LiveReportingListener`) | TestNG via `META-INF/services/org.testng.ITestNGListener` |
| `testng-live-reporter-json` | Reporter Plugin (`JsonExecutionReporter`) | Core listener via `META-INF/services/io.github.prmiguel.testng.live.core.spi.ExecutionReporter` |

Because both modules declare Java `ServiceLoader` SPI files, **simply placing both JARs on the test classpath activates live reporting automatically**.

---

## 1. Maven Dependency (Recommended)

Add both artifacts to your `pom.xml` as test-scoped dependencies:

```xml
<dependencies>
    <!-- Core listener and event engine -->
    <dependency>
        <groupId>io.github.prmiguel</groupId>
        <artifactId>testng-live-reporter-core</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <scope>test</scope>
    </dependency>

    <!-- JSON live streaming & consolidated report backend -->
    <dependency>
        <groupId>io.github.prmiguel</groupId>
        <artifactId>testng-live-reporter-json</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

> 💡 **Using JitPack?** See [JitPack Usage Guide](jitpack-usage.md) for GitHub commit, branch, or tag coordinates.

---

## 2. Explicit TestNG XML Configuration

If you disable automatic SPI discovery in TestNG or prefer explicit XML declaration, register **only the core listener** in your suite XML:

```xml
<!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd" >
<suite name="TestSuite">
    <listeners>
        <!-- Register the core listener; reporters are attached via SPI -->
        <listener class-name="io.github.prmiguel.testng.live.core.LiveReportingListener"/>
    </listeners>

    <test name="SmokeTests">
        <classes>
            <class name="com.acme.SampleTest"/>
        </classes>
    </test>
</suite>
```

> ⚠️ **Important:** Do NOT add `JsonExecutionReporter` to `<listeners>`. It implements `ExecutionReporter` (an internal SPI), not `ITestNGListener`. The core listener discovers and wires it automatically.

---

## 3. External Classpath Injection (Zero POM Changes)

You can add live reporting to any existing project without editing its `pom.xml`:

### A. Via Maven Surefire `additionalClasspathElements`

Run Maven with injected JARs:

```bash
mvn test \
  -Dsurefire.additionalClasspathElements="/path/to/testng-live-reporter-core-1.0.0.jar,/path/to/testng-live-reporter-json-1.0.0.jar"
```

### B. Via Direct CLI Execution

```bash
java -cp "testng-7.11.0.jar:tests.jar:testng-live-reporter-core-1.0.0.jar:testng-live-reporter-json-1.0.0.jar" \
  org.testng.TestNG testng.xml
```

Both JARs will auto-wire via `ServiceLoader`.

---

## 4. Programmatic Execution

When executing TestNG programmatically (e.g. from custom test runners or tools):

```java
import io.github.prmiguel.testng.live.core.LiveReportingListener;
import org.testng.TestNG;

public class CustomRunner {
    public static void main(String[] args) {
        TestNG testNG = new TestNG();
        testNG.setTestClasses(new Class<?>[] { SmokeTest.class });

        // Add the core listener (reporters are loaded via SPI)
        testNG.addListener(new LiveReportingListener());

        testNG.run();
    }
}
```

---

## 📚 Related Guides

- [Consumer Usage Guide](consumer-usage.md) — Step-by-step linear tutorial with copy-paste commands.
- [AI Agent Skill Guide](agent-skill-sample.md) — Pre-built Agent Skill with emoji progress summaries and failed tests breakdown.
- [JitPack Integration Guide](jitpack-usage.md) — Detailed guide on fetching JARs from JitPack.
- [Configuration Reference](configuration.md) — All supported properties and environment variables.
