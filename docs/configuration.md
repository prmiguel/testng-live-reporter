# ⚙️ Configuration Reference

**TestNG Live Reporter** is designed to work with zero configuration out of the box, but provides comprehensive options for filtering, output directories, formats, and timeouts.

---

## 🧭 Precedence Order

Configuration is resolved without throwing exceptions (invalid values log warnings and fall back to safe defaults). Properties are evaluated in the following order of precedence (highest to lowest):

1. **Java System Properties** (e.g. `-Dtestng.live.json.output-dir=/path/to/reports`)
2. **Environment Variables** (e.g. `TESTNG_LIVE_JSON_OUTPUT_DIR=/path/to/reports`)
3. **Properties File** specified via `testng.live.json.config` (or default lookup)
4. **Built-in Defaults**

---

## 🧩 Core Listener Configuration

These properties configure the underlying TestNG lifecycle listener and event bus:

| System Property | Environment Variable | Default | Description |
|---|---|---|---|
| `testng.live.run-id` | `TESTNG_LIVE_RUN_ID` | Auto-generated UUID | Unique identifier assigned to the test run. |
| `testng.live.shutdown-timeout-seconds` | — | `30` | Maximum time (in seconds) the event bus waits for reporters to flush during shutdown. |

---

## 📦 JSON Reporter Configuration

The `reporter-json` module supports detailed tuning for both real-time stream (`.ndjson`) and consolidated report (`.json`) generation:

| System Property | Environment Variable | Default | Description |
|---|---|---|---|
| `testng.live.json.enabled` | `TESTNG_LIVE_JSON_ENABLED` | `true` | Enables or disables JSON reporting. |
| `testng.live.json.output-dir` | `TESTNG_LIVE_JSON_OUTPUT_DIR` | `target/testng-live-reporter` | Directory where JSON and NDJSON files are written. |
| `testng.live.json.file-name` | `TESTNG_LIVE_JSON_FILE_NAME` | `<timestamp>-<runId>` | Custom base name for output files (omitting extension). |
| `testng.live.json.overwrite` | `TESTNG_LIVE_JSON_OVERWRITE` | `false` | Overwrites existing files if identical file name exists. |
| `testng.live.json.ndjson` | `TESTNG_LIVE_JSON_NDJSON` | `true` | Writes the live newline-delimited stream (`.ndjson`). |
| `testng.live.json.document` | `TESTNG_LIVE_JSON_DOCUMENT` | `true` | Generates the consolidated report document (`.json`). |
| `testng.live.json.flush-each-event` | `TESTNG_LIVE_JSON_FLUSH_EACH_EVENT` | `true` | Immediately flushes each event line to disk to allow live tailing. |
| `testng.live.json.include-classes` | `TESTNG_LIVE_JSON_INCLUDE_CLASSES` | *(empty: all)* | Comma-separated class name patterns to include. |
| `testng.live.json.exclude-classes` | `TESTNG_LIVE_JSON_EXCLUDE_CLASSES` | *(empty: none)* | Comma-separated class name patterns to exclude. |
| `testng.live.json.include-methods` | `TESTNG_LIVE_JSON_INCLUDE_METHODS` | *(empty: all)* | Comma-separated method name patterns to include. |
| `testng.live.json.exclude-methods` | `TESTNG_LIVE_JSON_EXCLUDE_METHODS` | *(empty: none)* | Comma-separated method name patterns to exclude. |
| `testng.live.json.include-statuses` | `TESTNG_LIVE_JSON_INCLUDE_STATUSES` | *(empty: all)* | Comma-separated statuses to include (`PASSED`, `FAILED`, `SKIPPED`, etc.). |
| `testng.live.json.max-stack-trace-chars` | `TESTNG_LIVE_JSON_MAX_STACK_TRACE_CHARS` | `0` *(unlimited)* | Max character length for failure stack traces before truncation. |
| `testng.live.json.sort-events` | `TESTNG_LIVE_JSON_SORT_EVENTS` | `false` | Sorts events deterministically in the consolidated document. |
| `testng.live.json.include-attributes` | `TESTNG_LIVE_JSON_INCLUDE_ATTRIBUTES` | `true` | Includes metadata attributes (e.g., `phase=configuration`). |
| `testng.live.json.document-events` | `TESTNG_LIVE_JSON_DOCUMENT_EVENTS` | `true` | When `false`, omits the detailed `tests[]` array from consolidated JSON (totals only). |
| `testng.live.json.shutdown-timeout-seconds` | `TESTNG_LIVE_JSON_SHUTDOWN_TIMEOUT_SECONDS` | `30` | Max seconds to wait for file writing completion on test finish. |

---

## 🛠️ Usage Examples

### 1. Command-Line / Surefire Arguments

Pass system properties directly via Maven Surefire:

```bash
mvn clean test \
  -Dtestng.live.json.output-dir="build/reports/live" \
  -Dtestng.live.json.flush-each-event=true \
  -Dtestng.live.json.include-statuses=FAILED,ERROR
```

Or configure inside your `pom.xml`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.5.2</version>
    <configuration>
        <systemPropertyVariables>
            <testng.live.json.output-dir>${project.build.directory}/custom-reports</testng.live.json.output-dir>
            <testng.live.json.max-stack-trace-chars>2000</testng.live.json.max-stack-trace-chars>
        </systemPropertyVariables>
    </configuration>
</plugin>
```

### 2. Environment Variables in CI

```bash
export TESTNG_LIVE_RUN_ID="ci-build-${GITHUB_RUN_NUMBER}"
export TESTNG_LIVE_JSON_OUTPUT_DIR="/tmp/test-output"
mvn test
```

### 3. External Properties File

Specify an external configuration file:

```bash
mvn test -Dtestng.live.json.config="/path/to/reporter.properties"
```

Example `reporter.properties`:

```properties
testng.live.json.enabled=true
testng.live.json.output-dir=target/live-reports
testng.live.json.flush-each-event=true
testng.live.json.include-statuses=FAILED,PASSED,SKIPPED
testng.live.json.document-events=true
```
