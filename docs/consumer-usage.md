# 🧪 Consuming testng-live-reporter in another project — Consumer Usage Guide

A comprehensive cookbook for adding live JSON reporting to an **existing** TestNG project,
using the published JitPack artifacts or local JARs. Each step is small, self-contained,
and explains how to verify everything worked before moving on.

- For the full system properties and environment variables reference, see [`docs/configuration.md`](configuration.md).
- For details on JitPack resolution, see [`docs/jitpack-usage.md`](jitpack-usage.md).
- For pre-built AI agent integrations (including emoji progress reporting, failure root-cause analysis, multi-run matrix analytics using `jq`, and optional DuckDB SQL examples), see [`docs/agent-skill-sample.md`](agent-skill-sample.md).

---

## 0. What you get

One TestNG execution produces one pair of files in `target/testng-live-reporter/` (configurable):

| File | Contents |
|---|---|
| `<timestamp>-<runId>.ndjson` | Live, append-only, one JSON record per line — readable *while tests run* |
| `<timestamp>-<runId>.json` | Consolidated document, written atomically when the run ends |

Both are built from the same internal events, so they cannot disagree.

---

## 1. Check your prerequisites

```bash
java -version    # must be 21 or newer
mvn -v           # any recent Maven (if using Maven)
```

The published jars are compiled for **Java 21**. If `java -version` reports 17, update your JDK before proceeding.

Optional utilities for parsing, analyzing, and validating output:

```bash
jq --version     # CLI JSON processor for streaming NDJSON
duckdb --version # optional SQL engine for multi-run analytics and matrices
node --version   # for the bundled validator script
```

---

## 2. Pick a version

```bash
VERSION=c266692          # a commit hash, a branch snapshot (main-SNAPSHOT), or a release tag
```

> ⚠️ JitPack can only build commits that already contain `jitpack.yml` — that starts at
> commit `c266692`. Anything older fails to compile. See [§10 Troubleshooting](#10-troubleshooting).

---

## 3. Download the two jars

The JitPack URL structure is **path-based**: `…/com/github/user/repo/module/version/module-version.jar`.
Note the slash between user and repo — the Maven group id `com.github.prmiguel.testng-live-reporter`
does **not** work as a URL path.

```bash
BASE="https://jitpack.io/com/github/prmiguel/testng-live-reporter"
mkdir -p lib
for module in testng-live-reporter-core testng-live-reporter-json; do
  curl -fsSL -o "lib/$module.jar" "$BASE/$module/$VERSION/$module-$VERSION.jar"
done
ls -l lib/
```

**Check it worked:**

```bash
# Both files must be non-empty (~33 KB each)
ls -lh lib/*.jar
```

JitPack builds artifacts on demand upon the first request, so fetching a cold commit may take
up to a minute. Retry the `curl` if needed; `-f` makes `curl` return non-zero on the temporary HTTP 404
returned during building.

---

## 4. Verify the jars (optional but recommended)

Check that SPI service registrations are in place:

```bash
unzip -p lib/testng-live-reporter-core.jar META-INF/services/org.testng.ITestNGListener
# io.github.prmiguel.testng.live.core.LiveReportingListener

unzip -p lib/testng-live-reporter-json.jar \
  META-INF/services/io.github.prmiguel.testng.live.core.spi.ExecutionReporter
# io.github.prmiguel.testng.live.json.JsonExecutionReporter
```

These two service files provide automatic self-registration. You do not need to configure any listeners manually in XML or code unless you want to.

**Check bytecode version** — confirm the jars target Java 21:

```bash
unzip -o -q lib/testng-live-reporter-core.jar \
  io/github/prmiguel/testng/live/core/LiveReportingListener.class -d /tmp/cls
od -An -tu1 -j6 -N2 /tmp/cls/io/github/prmiguel/testng/live/core/LiveReportingListener.class \
  | awk '{print "class file major="$2-0" (Java "$2-44")"}'
# class file major=65 (Java 21)
```

Check reporter metadata:

```bash
unzip -p lib/testng-live-reporter-json.jar META-INF/testng-live-reporter-json.properties
# reporter.name=testng-live-reporter-json
# reporter.version=1.0.0-SNAPSHOT
```

---

## 5. Copy the jars where your tests can see them

Choose the approach that fits your workflow:

```bash
# Option A: In a local lib directory inside your project
cp lib/testng-live-reporter-*.jar /path/to/your-project/lib/

# Option B: A shared user-level folder referenced by build scripts
mkdir -p ~/.local/share/testng-live-reporter && cp lib/*.jar ~/.local/share/testng-live-reporter/
```

The jars must end up on the classpath that your **tests** run with, not just the compiler's.

---

## 6. Run an example

### Plain `java` CLI

Compile and run with the jars on the classpath:

```bash
mkdir -p out
javac -cp 'lib/*' -d out $(find src -name '*.java')
java  -cp 'out:lib/*' org.testng.TestNG -testclass your.package.Tests
```

### Explicit listener (optional fallback)

Useful if SPI automatic discovery is bypassed or disabled in your runner:

```bash
java -cp 'out:lib/*' org.testng.TestNG \
  -listener io.github.prmiguel.testng.live.core.LiveReportingListener \
  -testclass your.package.Tests
```

### Maven Surefire

Add the jars to the forked test execution classpath. This **must** be declared in the POM — and Surefire must have a TestNG suite configured (or `-Dtest=...`), otherwise no tests run:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <version>3.5.2</version>
  <configuration>
    <suiteXmlFiles>
      <suiteXmlFile>src/test/resources/testng.xml</suiteXmlFile>
    </suiteXmlFiles>
    <additionalClasspathElements>
      <additionalClasspathElement>${project.basedir}/lib/testng-live-reporter-core.jar</additionalClasspathElement>
      <additionalClasspathElement>${project.basedir}/lib/testng-live-reporter-json.jar</additionalClasspathElement>
    </additionalClasspathElements>
  </configuration>
</plugin>
```

> ⚠️ Note: Passing `-Dsurefire.additionalClasspathElements=…` or `-Dmaven.test.additionalClasspath=…` on the
> command line is ignored by Surefire. The configuration must be inside `pom.xml`.

### Gradle

```groovy
test {
    useTestNG()
    classpath += files("lib/testng-live-reporter-core.jar", "lib/testng-live-reporter-json.jar")
}
```

---

## 7. Check the report appeared

By default, output files are written to `target/testng-live-reporter/`:

```bash
ls target/testng-live-reporter/
# 20261119-163926-example-1795106365.json
# 20261119-163926-example-1795106365.ndjson
```

Inspect the stream:

```bash
# First record: run started
head -1 target/testng-live-reporter/*.ndjson
# {"type":"run.started","schemaVersion":2,"runId":"...","startedAt":"...","reporter":{...}}

# Last record: run summary
tail -1 target/testng-live-reporter/*.ndjson
# {"type":"run.summary",...,"totals":{"events":12,"tests":4,"passed":3,"failed":1,...}}
```

**Validate reports** against schema and invariant rules:

```bash
node .github/scripts/validate-reports.mjs target/testng-live-reporter
# reports in target/testng-live-reporter are valid
```

---

## 8. Understand the schema

Three record kinds, discriminated by `type`:

| `type` | When emitted | Key fields |
|---|---|---|
| `run.started` | first record | `runId`, `startedAt`, `reporter` |
| `test.event` | per lifecycle event | `executionId`, `state`, `status`, `timestamp`, `test`, `threadId`, `invocationIndex` |
| `run.summary` | last record | `totals`, `durationMs`, `startedAt`, `finishedAt` |

### Lifecycle States vs. Statuses

- **`state`** is lifecycle progress: `DISCOVERED` -> `STARTED` -> `IN_PROGRESS` -> `FINISHED`.
  (`QUEUED` is reserved for future schedulers and never emitted by TestNG).
- **`status`** is test outcome: `UNDETERMINED` until `FINISHED`, then `PASSED`, `FAILED`, `SKIPPED`,
  `IGNORED`, `TIMEOUT`, `INTERRUPTED`, or `ERROR`.

Example `test.event` record (abridged):

```json
{
  "type": "test.event",
  "recordSchemaVersion": 1,
  "eventSchemaVersion": 1,
  "sequence": 1,
  "runId": "example-1795106365",
  "executionId": "example-1795106365-594dfe9cd458a504-0-0-1",
  "state": "STARTED",
  "status": "UNDETERMINED",
  "timestamp": "2026-11-19T16:39:25.997Z",
  "test": {
    "testId": "demo.SmokeTest.dataProviderRow[1]#594dfe9cd458a504",
    "hash": "594dfe9cd458a504",
    "className": "demo.SmokeTest",
    "methodName": "dataProviderRow",
    "readableKey": "demo.SmokeTest.dataProviderRow[1]",
    "parameters": [{"declaredType": "int", "runtimeType": "java.lang.Integer", "value": "1"}]
  },
  "suiteName": "Command line suite",
  "testName": "Command line test",
  "threadId": 1,
  "threadName": "main",
  "invocationIndex": 0,
  "retryAttempt": 0,
  "attributes": {}
}
```

Key invariants:
- `durationMs` appears **only** on `FINISHED` events.
- `failure` appears **only** when a failure or error occurred.
- Absent values are omitted rather than serialized as `null`.
- `executionId` uniquely identifies each attempt. Two data-provider rows differ in `parameters` **and** in `hash`, preventing collisions.

### Configuration Failures

Failures in `@BeforeMethod`, `@BeforeClass`, `@After*`, etc. arrive as `test.event` records with:
- `state: FINISHED`
- `status: ERROR`
- `attributes.phase: "configuration"`
- `attributes.configurationKind: "beforeMethod"` (or `beforeClass`, etc.)

Downstream affected tests will emit `SKIPPED`. Configuration failures are accounted for in `totals.configurationFailures` rather than `totals.tests`.

```bash
jq -r 'select(.attributes.phase=="configuration")
       | "\(.test.methodName) \(.attributes.configurationKind): \(.failure.type)"' "$F"
```

---

## 9. Read, filter, and query with `jq`

Locate the latest stream file:

```bash
F=$(ls -t target/testng-live-reporter/*.ndjson 2>/dev/null | head -1)
```

### Totals from a completed run

```bash
# Event counts grouped by status
jq -r 'select(.type=="test.event") | .status' "$F" | sort | uniq -c

# Authoritative totals from run.summary (last line)
jq -s 'map(select(.type=="run.summary")) | .[0].totals' "$F"
```

```json
{
  "events": 12,
  "tests": 4,
  "passed": 3,
  "failed": 1,
  "skipped": 0,
  "ignored": 0,
  "timeout": 0,
  "interrupted": 0,
  "error": 0,
  "undetermined": 8,
  "droppedDuplicates": 4,
  "filtered": 0,
  "writeErrors": 0
}
```

### Querying while the run is still in progress

Because NDJSON is flushed on every event, you can inspect status mid-run before `run.summary` exists:

```bash
jq -s -r '
  [.[] | select(.type=="test.event" and (.attributes.phase // "test") == "test")] as $e
  | ($e | map(select(.state=="FINISHED" and ((.attributes.phase // "test") == "test")) | .executionId) | unique) as $done
  | ($e | map(select((.state != "FINISHED") and ((.executionId as $id | $done | index($id)) | not))))
  | {stillRunning: (map(.executionId) | unique | length), openEvents: length}' "$F"
```

### Failures with root cause

```bash
jq -r 'select(.type=="test.event" and .status=="FAILED")
       | "\(.test.className).\(.test.methodName)  \(.failure.type): \(.failure.message)"' "$F"
```

### Slowest tests

```bash
jq -r 'select(.state=="FINISHED" and ((.attributes.phase // "test") == "test")) | "\(.durationMs)\t\(.test.methodName)"' "$F" | sort -rn | head -5
```

### Querying the consolidated document (`*.json`)

The consolidated `.json` document is written when the run completes. Its `tests[]` array contains **one entry per attempt**:

```bash
J=$(ls -t target/testng-live-reporter/*.json 2>/dev/null | head -1)

# Totals summary
jq '.summary.totals' "$J"

# All test outcomes
jq -r '.tests[] | "\(.status)\t\(.readableKey)"' "$J" | sort

# Failed tests only
jq -r '.tests[] | select(.status=="FAILED") | "\(.readableKey): \(.failure.message)"' "$J"

# Configuration failures
jq -r '.configurationFailures[] | "\(.methodName) \(.configurationKind): \(.failure.type)"' "$J"
```

---

## 10. Troubleshooting

| Symptom | Cause | Solution |
|---|---|---|
| `curl` exits with error 22 / empty jar | Commit not yet built by JitPack or predates `jitpack.yml` | Use commit `c266692` or later; check `https://jitpack.io/.../<version>/build.log` |
| `UnsupportedClassVersionError` | Running under Java < 21 | Upgrade runtime JDK to Java 21+ (`java -version`) |
| No report files written | Reporter jars are missing from the test execution classpath | Verify `-cp` or Surefire `<additionalClasspathElements>` configuration |
| `Tests run: 0` in Maven Surefire | No TestNG suite XML or test pattern specified | Add `<suiteXmlFiles>` or `-Dtest=...` |
| `undetermined` count appears large | It counts intermediate `STARTED`/`IN_PROGRESS` events | Look at `totals.tests` or filter by `state == "FINISHED"` |
| Test marked `SKIPPED` without obvious reason | A `@BeforeMethod` or `@BeforeClass` failed | Inspect records with `attributes.phase == "configuration"` |

---

## 11. Zero-setup runnable example

To see the workflow in action without configuring a project from scratch:

```bash
cd examples/jitpack-consumer
./run.sh c266692
```

This downloads the jars from JitPack, compiles a sample fixture, runs TestNG, and validates the output reports automatically.

---

## 📚 Related Guides

- [AI Agent Skill Guide](agent-skill-sample.md) — Pre-built skill for AI coding agents with emoji progress reporting and background polling.
- [Configuration Reference](configuration.md) — Properties, environment variables, filtering, and destination paths.
- [JitPack Integration Guide](jitpack-usage.md) — Detailed explanation of JitPack URL layouts and build behavior.
- [Architecture & Event Model](architecture.md) — State machine transitions, canonical hashing, and concurrency design.
