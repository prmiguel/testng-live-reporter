# 📦 reporter-json

Live JSON output for **TestNG Live Reporter**.

Each TestNG execution writes its own pair of files: an append-only live stream that can be
tailed while tests are still running, and a consolidated document assembled when the run
ends. Both are built from the same records, so they cannot disagree.

> 📌 **Standing rule — this module is a worked example, not a closed design.**
> Everything below the SPI seam (`EventRecordEncoder`, `EventFilter`, `RunTotals`,
> `JsonDocumentAssembler`) is backend-agnostic. A future `reporter-sqlite`,
> `reporter-cache-db` or `reporter-ctrf` should implement `ExecutionReporter` +
> `EventRecordEncoder` and reuse the lifecycle, filtering, dedupe and totals — not
> re-invent them. JSON is the first concrete stack, not the only one.

---

## Why it never blocks a test

`core` gives every reporter its own single-thread dispatcher (`ExecutionEventBus`), so
`onEvent` is called off TestNG worker threads. This reporter does its file I/O directly
in `onEvent`, on that dispatcher — no extra queue, no shared lock with the test threads.
An event is written as it arrives, so a slow disk delays only the reporter.

Every record is flushed by default (`flush-each-event=true`), which is what makes the
stream tailable mid-run.

---

## Output

Default location: `target/testng-live-reporter/<yyyyMMdd-HHmmss>-<runId>.ndjson` plus the
same basename with `.json`. One pair per execution; a new run never appends to an older
file.

### Live stream (`.ndjson`)

Newline-delimited JSON, one record per line, three record kinds.

```jsonl
{"type":"run.started","schemaVersion":1,"runId":"demo-run","startedAt":"2026-11-19T15:03:01.308Z","reporter":{"name":"testng-live-reporter-json","version":"1.0.0-SNAPSHOT"}}
{"type":"test.event","recordSchemaVersion":1,"eventSchemaVersion":1,"sequence":1,"runId":"demo-run","executionId":"demo-run-ba73a6c4faf73db5-0-0-27","state":"STARTED","status":"UNDETERMINED","timestamp":"2026-11-19T15:03:01.310Z","test":{"testId":"com.acme.LoginTest.login[\"bob\"]#a1b2c3d4e5f60718","hash":"a1b2c3d4e5f60718","className":"com.acme.LoginTest","methodName":"login","readableKey":"com.acme.LoginTest.login[\"bob\"]","parameters":[{"declaredType":"java.lang.String","runtimeType":"java.lang.String","value":"bob"}]},"suiteName":"smoke","testName":"LoginTest","threadId":21,"threadName":"TestNG-test-1","invocationIndex":0,"retryAttempt":0,"attributes":{}}
{"type":"test.event","recordSchemaVersion":1,"eventSchemaVersion":1,"sequence":23,"runId":"demo-run","executionId":"demo-run-ba73a6c4faf73db5-0-0-27","state":"FINISHED","status":"FAILED","timestamp":"2026-11-19T15:03:01.312Z","durationMs":6,"test":{"testId":"..."},"suiteName":"Command line suite","testName":"Command line test","threadId":27,"threadName":"TestNG-test-Command line test-2","invocationIndex":0,"retryAttempt":0,"failure":{"type":"java.lang.AssertionError","message":"expected [true] but found [false]","stackTrace":"java.lang.AssertionError: ...\n\tat org.testng.Assert.fail(Assert.java:111)"},"attributes":{}}
{"type":"run.summary","schemaVersion":1,"runId":"demo-run","startedAt":"2026-11-19T15:03:01.308Z","finishedAt":"2026-11-19T15:03:01.414Z","durationMs":106,"totals":{"events":24,"tests":8,"passed":4,"failed":1,"skipped":2,"ignored":0,"timeout":1,"interrupted":0,"error":0,"undetermined":16,"droppedDuplicates":8,"filtered":0,"writeErrors":0}}
```

Rules:
- `failure` and `durationMs` are **omitted** when absent — never `null`.
- `attributes` is always an object (possibly `{}`).
- Timestamps are ISO-8601 UTC truncated to milliseconds.
- One record is always exactly one line; stack traces are escaped, never embedded raw.
- Field naming is camelCase, matching `testng-execution-monitoring` and `ctrf-hub`.

### Consolidated document (`.json`)

Written at `close()`. The event objects are the live-stream records spliced verbatim.

```json
{"schemaVersion":1,
 "run":{"schemaVersion":1,"runId":"demo-run","startedAt":"2026-11-19T15:03:01.308Z","reporter":{"name":"testng-live-reporter-json","version":"1.0.0-SNAPSHOT"}},
 "events":[{"type":"test.event", "...":"..."}],
 "summary":{"type":"run.summary","schemaVersion":1,"finishedAt":"2026-11-19T15:03:01.414Z","durationMs":106,"totals":{"...":"..."}}}
```

Because it is assembled by re-reading the `.ndjson`, a run whose JVM died still yields a
valid document (without `summary`) as long as something called `flush()`. The document is
compact by design: pretty-printing would require a full re-parse of every record.

### Schema

`src/main/resources/schema/testng-live-report-v1.schema.json` (JSON Schema draft-07) covers
both the document and each NDJSON record. It is shipped for consumers to validate against;
it is not enforced at runtime.

---

## Configuration

`core` instantiates reporters through `ServiceLoader` with a no-arg constructor and has no
init hook, so the reporter resolves configuration in the constructor and opens its files
lazily on the first event (only then is the `runId` known).

Precedence, highest first: **system property → environment variable → properties file →
default**. The optional file is named by `-Dtestng.live.json.config=/path/file.properties`.
Unparsable values log a warning and fall back to the default — a bad property never breaks
a test run.

| Property | Environment variable | Default | Meaning |
|---|---|---|---|
| `testng.live.json.enabled` | `TESTNG_LIVE_JSON_ENABLED` | `true` | Master switch; `false` makes the reporter a no-op |
| `testng.live.json.output-dir` | `TESTNG_LIVE_JSON_OUTPUT_DIR` | `target/testng-live-reporter` | Relative paths resolve against the working directory |
| `testng.live.json.file-name` | `TESTNG_LIVE_JSON_FILE_NAME` | `<yyyyMMdd-HHmmss>-<runId>` (UTC) | Basename without extension |
| `testng.live.json.overwrite` | `TESTNG_LIVE_JSON_OVERWRITE` | `false` | `false` appends `-1`, `-2`, … instead of replacing |
| `testng.live.json.ndjson` | `TESTNG_LIVE_JSON_NDJSON` | `true` | Write the live stream |
| `testng.live.json.document` | `TESTNG_LIVE_JSON_DOCUMENT` | `true` | Write the consolidated document at close |
| `testng.live.json.flush-each-event` | `TESTNG_LIVE_JSON_FLUSH_EACH_EVENT` | `true` | Flush per record so the file is tailable |
| `testng.live.json.include-classes` | `TESTNG_LIVE_JSON_INCLUDE_CLASSES` | *(all)* | Comma-separated globs, e.g. `com.acme.*,*.Smoke*` |
| `testng.live.json.exclude-classes` | `TESTNG_LIVE_JSON_EXCLUDE_CLASSES` | *(none)* | Comma-separated globs |
| `testng.live.json.include-methods` | `TESTNG_LIVE_JSON_INCLUDE_METHODS` | *(all)* | Method-name globs |
| `testng.live.json.exclude-methods` | `TESTNG_LIVE_JSON_EXCLUDE_METHODS` | *(none)* | Method-name globs |
| `testng.live.json.include-statuses` | `TESTNG_LIVE_JSON_INCLUDE_STATUSES` | *(all)* | e.g. `FAILED,TIMEOUT`; non-terminal events are always kept |
| `testng.live.json.max-stack-trace-chars` | `TESTNG_LIVE_JSON_MAX_STACK_TRACE_CHARS` | `0` (unlimited) | Truncate and add `"truncated":true` |
| `testng.live.json.sort-events` | `TESTNG_LIVE_JSON_SORT_EVENTS` | `false` | Sort document events by `sequence` |
| `testng.live.json.include-attributes` | `TESTNG_LIVE_JSON_INCLUDE_ATTRIBUTES` | `true` | Emit `attributes` |
| `testng.live.json.shutdown-timeout-seconds` | `TESTNG_LIVE_JSON_SHUTDOWN_TIMEOUT_SECONDS` | `30` | Informational; the bus owns the real timeout |

Filtered events are counted in `totals.filtered`, never silently dropped.

### Declarative scoping

Everything that decides *what* gets written is configurable, so narrowing scope needs no
code change:

```bash
mvn test \
  -Dtestng.live.json.output-dir=target/reports/nightly \
  -Dtestng.live.json.file-name=nightly \
  -Dtestng.live.json.exclude-classes='com.acme.legacy.*' \
  -Dtestng.live.json.include-statuses=FAILED,TIMEOUT,ERROR \
  -Dtestng.live.json.max-stack-trace-chars=4000
```

---

## Usage

### Classpath only (recommended)

Add the JAR and the core JAR to the test classpath. `core` discovers reporters through
`META-INF/services/io.github.prmiguel.testng.live.core.spi.ExecutionReporter`, and the
core listener is itself discovered through `META-INF/services/org.testng.ITestNGListener`.

```xml
<dependency>
    <groupId>io.github.prmiguel</groupId>
    <artifactId>testng-live-reporter-core</artifactId>
    <version>1.0.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.github.prmiguel</groupId>
    <artifactId>testng-live-reporter-json</artifactId>
    <version>1.0.0</version>
    <scope>test</scope>
</dependency>
```

> ⚠️ `JsonExecutionReporter` is **not** a TestNG `ITestNGListener` and must not be listed in
> `<listeners>` in `testng.xml`. Register the *core* listener only if you need explicit
> registration at all; the reporter attaches to it through the reporter SPI.

### Explicit registration of the core listener

```xml
<listeners>
    <listener class-name="io.github.prmiguel.testng.live.core.LiveReportingListener"/>
</listeners>
```

### Programmatic

```java
JsonExecutionReporter reporter = new JsonExecutionReporter(JsonReporterConfiguration.from(Map.of(
        JsonReporterConfiguration.OUTPUT_DIR_PROPERTY, "target/reports",
        JsonReporterConfiguration.FILE_NAME_PROPERTY, "my-run")));

TestNG testNG = new TestNG();
testNG.setTestClasses(new Class<?>[]{MyTest.class});
testNG.addListener(new LiveReportingListener(
        new CoreConfiguration(System.getProperty("my.run.id", "run-1"), Duration.ofSeconds(30)),
        List.of(),
        List.of(reporter)));
testNG.run();
```

---

## Known `core` limitations

A reporter cannot compensate for these; they are recorded so consumers are not surprised.
Fixing them requires a `core` change.

| Symptom | Cause | Effect here |
|---|---|---|
| Every invoked test emits `STARTED` twice | `onTestStart` and `beforeInvocation` both publish it | Collapsed by per-`(executionId, state)` dedupe; counted in `totals.droppedDuplicates` |
| `suiteName` / `testName` can be wrong under `parallel="tests"`/`"suites"` | `core` swaps one global `ExecutionEventFactory` per context | Values are passed through verbatim |
| No run-level event | `onExecutionStart`/`onExecutionFinish` publish nothing | `run.started` and `run.summary` are synthesised by this reporter |
| `DISCOVERED`, `QUEUED`, `IGNORED` never occur | `core` has no factory method or callback for them | Only emitted if `core` starts emitting them |
| `close()` may discard queued events | `ExecutionEventBus` calls `shutdownNow()` on timeout | Mitigated by flushing every record; residual risk for very slow sinks |
| `duration` is the listener's timing | `result.getStartMillis()` is unused | `durationMs` is present only on `FINISHED` events |

---

## Tests

JUnit 5 drives both plain objects and real TestNG runs (`@TempDir`, programmatic `TestNG`).
Because the reporter itself must stay dependency-free, tests ship their own JSON reader
(`TestJsonParser`) to prove every emitted line is genuinely parseable.

```bash
mvn -pl reporter-json -am test
mvn -pl reporter-json test -Dtest=JsonExecutionReporterIntegrationTest
```

Coverage: writer escaping, encoder shape for every state/status, configuration precedence,
filter globs, lazy file opening and collision suffixes, dedupe accounting, document
splicing and sorting, and a 4-thread × 250-event run through a real `ExecutionEventBus`
asserting that no event is dropped or torn.
