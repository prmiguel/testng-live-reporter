# testng-live-reporter

Live reporting listeners for TestNG execution traceability and observability.

This project provides listeners that capture test execution progress in real time, tracking the flow and outcome of tests as they run.

---

## Proposal: one repository, multi-module Maven project

Status: **proposal / design document** — the modules listed below describe the planned layout, not yet implemented code.

The idea: the shared listener machinery (state/status model, unique test identifiers, thread-safe event dispatch) lives in a small **core** module, and each reporting approach is its own module with its own JAR. Consumers depend only on the reporter they need, so no one pulls a big JAR with unused dependencies.

### Project structure tree

```
testng-live-reporter/
├── pom.xml                                      # Parent POM: ${revision} versioning, plugin & dependency management, module list
│
├── core/                                        # artifactId: testng-live-reporter-core
│   └── src/
│       ├── main/java/io/github/prmiguel/testng/live/core/
│       │   ├── LiveReportingListener.java       # Base listener: TestNG entry point (onStart/onTestStart/onTestSuccess/... hooks)
│       │   ├── ExecutionEventBus.java           # Thread-safe fan-out of events to registered reporters
│       │   ├── TestRunIdGenerator.java          # Deterministic unique id: package.TestClass.testMethod[parameters]
│       │   ├── model/
│       │   │   ├── ExecutionState.java          # STARTED, IN_PROGRESS, FINISHED, ... (lifecycle)
│       │   │   ├── ExecutionStatus.java         # PASSED, FAILED, SKIPPED, IGNORED, TIMEOUT, ERROR, ... (outcome)
│       │   │   ├── ExecutionEvent.java          # Event record: id, state, status, timestamp, thread, duration
│       │   │   └── TestIdentity.java            # FQN + parameters identity model for a running test
│       │   └── spi/
│       │       └── ExecutionReporter.java       # SPI interface: onEvent(...), flush(), close()
│       ├── main/resources/META-INF/services/
│       │   └── org.testng.ITestNGListener       # SPI registration file (auto-registration when JAR is on classpath)
│       └── test/java/...                        # Unit tests for core
│
├── reporter-json/                               # artifactId: testng-live-reporter-json        (depends: core)
│   └── src/main/java/io/github/prmiguel/testng/live/json/
│       └── JsonExecutionReporter.java           # Streams events as JSON (one file per run / per test)
│
├── reporter-sqlite/                             # artifactId: testng-live-reporter-sqlite      (depends: core)
│   └── src/main/java/io/github/prmiguel/testng/live/sqlite/
│       └── SqliteExecutionReporter.java         # Persists events into a SQLite file (queryable history)
│
├── reporter-cache-db/                           # artifactId: testng-live-reporter-cache-db    (depends: core)
│   └── src/main/java/io/github/prmiguel/testng/live/cachedb/
│       └── CachedDbExecutionReporter.java       # Local cached DB (e.g. H2 in file mode / MapDB) with live reads
│
├── reporter-ctrf/                               # artifactId: testng-live-reporter-ctrf        (depends: core)
│   └── src/main/java/io/github/prmiguel/testng/live/ctrf/
│       └── CtrfExecutionReporter.java           # Common Test Report Format output (dashboard-friendly)
│
├── integration-tests/                           # artifactId: testng-live-reporter-integration-tests (test-only, not published)
│   └── src/test/
│       ├── java/...                             # End-to-end suites: parallel, data providers, listeners registered externally
│       └── resources/testng.xml                 # Suite definitions used by the integration tests
│
├── .github/workflows/
│   ├── ci.yml                                   # Build + unit + integration tests, JDK matrix (17/21)
│   └── release.yml                              # Publish modules to GitHub Packages on tag; JitPack works out of the box
│
└── README.md
```

### Modules

| Module | ArtifactId | Purpose | Dependency scope |
|---|---|---|---|
| `core` | `testng-live-reporter-core` | Listener base, state/status model, unique-id generation, thread-safe event bus, SPI interface | TestNG (provided) |
| `reporter-json` | `testng-live-reporter-json` | JSON execution events | `core` |
| `reporter-sqlite` | `testng-live-reporter-sqlite` | SQLite persistence | `core` |
| `reporter-cache-db` | `testng-live-reporter-cache-db` | Local cached DB, live-read friendly | `core` |
| `reporter-ctrf` | `testng-live-reporter-ctrf` | CTRF output | `core` |
| `integration-tests` | `testng-live-reporter-integration-tests` | E2E validation, dogfooding | all modules (test scope) |

Key design points:

- **Thread safety / parallel tests:** the event bus uses a non-blocking queue and `ConcurrentHashMap` bookkeeping; reporters never block TestNG worker threads.
- **Unique identifier:** stable, deterministic id built from the fully qualified `package.TestClass.testMethod` plus serialized parameters, so parallel and data-provider runs are distinguishable.
- **Small JARs:** each reporter module declares only its own backend dependency (e.g. SQLite driver only in `reporter-sqlite`); TestNG is `provided` everywhere, since consumers already have it.

### Core concepts to expose

| Concern | Proposal |
|---|---|
| **State model** | `STARTED`, `IN_PROGRESS`, `FINISHED` + extensible lifecycle hooks |
| **Status model** | `PASSED`, `FAILED`, `SKIPPED`, `IGNORED`, `TIMEOUT`, `INTERRUPTED`, `ERROR` |
| **Unique ID** | `package.ClassName.methodName[params]` + stable deterministic serialization |
| **Thread safety** | Event queue per reporter + `ConcurrentHashMap`/`BlockingQueue`; never block TestNG threads |
| **Parallel / data providers** | Track `ITestResult`, `ITestContext`, `IInvokedMethod`; correlate by instance name + method + parameters |
| **External registration** | Support SPI (`META-INF/services/org.testng.ITestNGListener`) so a JAR dropped on the classpath auto-registers |

### Versioning and publishing

- Single version for all modules via the CI-friendly `${revision}` property + `flatten-maven-plugin`, e.g. `1.0.0`.
- Snapshot builds (`x.y.z-SNAPSHOT`) are published to **GitHub Packages** on every push to `main` via `release.yml`.
- Release builds are published on version tags (`v1.0.0`) to GitHub Packages; **JitPack** also serves any commit/tag automatically, so consumers can use either source.

Maven coordinates example:

```xml
<dependency>
    <groupId>io.github.prmiguel</groupId>
    <artifactId>testng-live-reporter-json</artifactId>
    <version>1.0.0</version>
</dependency>
```

### External usage (planned consumption modes)

1. **Maven dependency + `testng.xml`:** add the reporter module as a dependency and declare the listener class in the suite file.

   ```xml
   <listeners>
       <listener class-name="io.github.prmiguel.testng.live.json.JsonExecutionReporter"/>
   </listeners>
   ```

2. **SPI auto-registration:** when a reporter JAR is on the test classpath, its `META-INF/services/org.testng.ITestNGListener` entry makes TestNG pick it up without any configuration.

3. **External JAR injection from the command line:** drop the reporter JAR into the classpath and wire it via `mvn`, without touching project code.

   ```bash
   mvn test \
     -Dmaven.repo.local=... \
     -Dextra.jars=/path/to/testng-live-reporter-json-1.0.0.jar
   ```

   (or explicitly: `mvn dependency:copy ...` / `testng -Dlistener=...` depending on harness)

4. **Programmatic registration:** build the suite with `TestNG` API and call `testNG.addListener(new JsonExecutionReporter(...))`.

### GitHub Actions workflow (planned)

- `ci.yml`: JDK matrix (17, 21), `mvn verify` running unit and integration tests.
- `release.yml`: on `v*` tags — deploy all modules to GitHub Packages; JitPack serves releases transparently.
- The project dogfoods its own `reporter-json`/`reporter-ctrf` modules so the workflow produces execution reports of its own test runs.
