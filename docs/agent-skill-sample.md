# 🤖 AI Agent Skill & Progress Reporting for testng-live-reporter

This guide provides a ready-made **Agent Skill** for AI coding assistants (such as Antigravity, Claude Code, Gemini CLI, Cursor, CommandCode, GitHub Copilot CLI, etc.).

Because `testng-live-reporter` writes an append-only `.ndjson` stream that is flushed to disk on each lifecycle event and a consolidated `.json` document at run completion, an agent does **not** need to block on the test suite or re-run tests to inspect status.

---

## 📋 Required CLI Tools & Capabilities

The skill relies on two fast, lightweight CLI tools:

1. **`jq` CLI (Required)**:
   - Real-time streaming queries against `*.ndjson`.
   - Live progress dashboards with emoji indicators while tests are in flight.
   - Immediate inspection of newly completed or failed tests without waiting for the run to finish.

2. **`duckdb` CLI (Required for Historical & Multi-Run Analytics)**:
   - Serverless SQL engine that reads `.ndjson` and `.json` files directly from disk via `read_ndjson()` and `read_json_auto()`.
   - **Historical Execution Tracking**: Query trends across the last $N$ runs.
   - **Per-Test History**: Inspect the outcome history of a specific test across past executions.
   - **Stable & Fixed Tests Identification**: Detect tests that consistently pass or tests that failed in a previous run and are now passing (fixed/recovered).
   - **Cross-Run / Cross-Environment Comparison**: Compare runs across environments (e.g. `dev` vs. `uat` or `run-1` vs. `run-2`).
   - **Run Matrix with Emoji Icons**: Generate a test result matrix where columns are `runId`s, rows are test names, and cells show status icons (`✅`, `❌`, `⏭️`, `⏱️`).
   - **Test Result Analysis**: Synthesize high-level conclusions and explain root-cause failure reasons.

---

## 🛠️ Installing the Skill

Create the skill file inside your project or workspace:

```bash
# Antigravity CLI / Gemini:
mkdir -p .gemini/skills/testng-live-reporting
# or CommandCode:
mkdir -p .commandcode/skills/testng-live-reporting
# or Claude Code:
mkdir -p .claude/skills/testng-live-reporting
```

Save the definition below as `SKILL.md` inside that directory (e.g. `.commandcode/skills/testng-live-reporting/SKILL.md`). The directory name **must** match `name:` in the YAML frontmatter.

---

## 📄 Complete `SKILL.md` Definition

````markdown
---
name: testng-live-reporting
description: Install, run, and query testng-live-reporter so TestNG executions emit live JSON reports. Use when asked to add live test reporting, monitor live test progress with emojis, list failed tests, perform test result analysis with root-cause explanations, or query multi-run history, matrices, and run comparisons using DuckDB SQL.
argument-hint: "[install|check|status|progress|analysis|failures|history|compare|matrix] [project-dir]"
---

# TestNG live reporting

`testng-live-reporter` writes one append-only `.ndjson` stream plus one `.json` consolidated document per execution in `target/testng-live-reporter/`.

This skill guides installing the jars, launching suites, querying live progress, analyzing test results, and running multi-run historical analytics via SQL.

## 🧭 Core Agent Rules

- **Read the report files directly.** Never re-run the test suite just to answer a status, progress, or analysis question.
- **Provide clear, emoji-rich progress output.** Whenever the user asks for progress or status, present:
  1. A summary with emoji icons (`🏁`, `✅`, `❌`, `⏭️`, `🏃`, `⏳`).
  2. A dedicated section listing all tests that finished failed.
- **Provide automated root-cause analysis** when the user asks for `/analysis` or why tests failed.
- **Use DuckDB SQL for historical analysis, run comparisons, and matrices** across multiple execution files.
- **Quote real numbers only.** If no report file exists, state that clearly and offer the install step.
- **Do not block while tests run.** If tests run in the background, poll periodically using a non-blocking schedule (e.g. 5 minutes). Report only deltas (new completions or failures) and remain silent if state is unchanged.
- **Prefer the newest report file for single-run queries:**
  ```bash
  F=$(ls -t target/testng-live-reporter/*.ndjson 2>/dev/null | head -1)
  ```

---

## 1. Prerequisites & Required Tools

Verify the necessary tools are installed on the system:

```bash
# 1. Java 21+ is required for the reporter
java -version

# 2. jq is required for streaming NDJSON parsing & live progress dashboards
jq --version

# 3. DuckDB is required for historical queries, test matrices, and cross-run SQL analytics
duckdb --version
```

If `jq` or `duckdb` is missing:
- Debian/Ubuntu: `apt-get install -y jq duckdb`
- macOS: `brew install jq duckdb`
- Or download standalone binaries into `~/.local/bin` / `usr/local/bin`.

---

## 2. Install the reporter jars

Fetch the published jars from JitPack into a local `lib/` directory:

```bash
VERSION="${1:-main-SNAPSHOT}"
BASE="https://jitpack.io/com/github/prmiguel/testng-live-reporter"
mkdir -p lib
for m in testng-live-reporter-core testng-live-reporter-json; do
  for i in $(seq 1 30); do
    curl -fsSL -o "lib/$m.jar" "$BASE/$m/$VERSION/$m-$VERSION.jar" && break
    sleep 10
  done
  [ -s "lib/$m.jar" ] || { echo "error: could not fetch $m@$VERSION"; exit 1; }
done
```

Add `lib/*.jar` to your test classpath:
- **Plain java**: `java -cp 'out:lib/*' org.testng.TestNG -testclass <Suite>`
- **Maven Surefire**: Declare `<additionalClasspathElements>` in `pom.xml` under `maven-surefire-plugin` (along with `<suiteXmlFiles>`).
- **Gradle**: `test { classpath += files("lib/testng-live-reporter-core.jar", "lib/testng-live-reporter-json.jar") }`

---

## 3. Verify installation & compatibility

```bash
# Verify SPI registration files exist
unzip -p lib/testng-live-reporter-core.jar META-INF/services/org.testng.ITestNGListener
unzip -p lib/testng-live-reporter-json.jar META-INF/services/io.github.prmiguel.testng.live.core.spi.ExecutionReporter

# Verify Java 21 bytecode (major version 65)
od -An -tu1 -j6 -N2 <(unzip -p lib/testng-live-reporter-core.jar \
  io/github/prmiguel/testng/live/core/LiveReportingListener.class) \
  | awk '{print "class file major="$2-0" (Java "$2-44")"}'
```

Prerequisites check:
- `java -version` must report 21 or newer.
- TestNG 7.10.x or 7.11.x on the classpath.

---

## 4. Progress & Status Reporting (Using `jq`)

When the user asks **"what's the progress?"**, **"how are tests going?"**, or on a background tick, execute the following script against the latest `.ndjson` stream:

```bash
DIR="${1:-target/testng-live-reporter}"
F=$(ls -t "$DIR"/*.ndjson 2>/dev/null | head -1)

if [ -z "$F" ]; then
  echo "⚠️ No test reports found in $DIR. Have tests been started with the reporter jars?"
  exit 0
fi

jq -s -r '
  ([.[] | select(.type=="run.started")] | .[0]) as $start
  | ([.[] | select(.type=="run.summary")] | .[0]) as $sum
  | [.[] | select(.type=="test.event" and (.attributes.phase // "test") == "test")] as $testEvents
  | [.[] | select(.type=="test.event" and .attributes.phase == "configuration")] as $cfgEvents
  | ($testEvents | map(select(.state=="FINISHED"))) as $fin
  | ($fin | map(.executionId) | unique) as $doneIds
  | ($testEvents | map(select(.state != "FINISHED" and ((.executionId as $id | $doneIds | index($id)) | not)))) as $runningEvents
  | ($runningEvents | map(.test.readableKey) | unique) as $runningNames
  | ($fin | map(select(.status=="PASSED")) | length) as $passed
  | ($fin | map(select(.status=="FAILED")) as $f | $f) as $failedList
  | ($failedList | length) as $failed
  | ($fin | map(select(.status=="SKIPPED")) | length) as $skipped
  | ($fin | map(select(.status=="ERROR" or .status=="TIMEOUT" or .status=="INTERRUPTED")) | length) as $errors
  | ($cfgEvents | map(select(.state=="FINISHED" and .status=="ERROR"))) as $cfgFailures
  | (if $sum then "🏁 FINISHED" else "⏳ IN PROGRESS" end) as $runState
  | (if $sum then "\($sum.durationMs)ms" else "running" end) as $duration
  | "## 🧪 Test Execution Progress\n",
    "**Status:** \($runState) | **Duration:** \($duration)",
    "",
    "### 📊 Summary",
    "- 🏁 **Total Finished:** \($fin | length)",
    "- ✅ **Passed:** \($passed)",
    "- ❌ **Failed:** \($failed)",
    "- ⏭️ **Skipped:** \($skipped)" + (if $errors > 0 then "\n- 💥 **Errors/Timeouts:** \($errors)" else "" end),
    "- 🏃 **Running Now:** \($runningNames | length)" + (if ($runningNames | length) > 0 then " (\($runningNames | join(", ")))" else "" end) + (if ($cfgFailures | length) > 0 then "\n- ⚠️ **Configuration Failures:** \($cfgFailures | length)" else "" end),
    "",
    "### ❌ Failed Tests",
    (if ($failedList | length) == 0 and ($cfgFailures | length) == 0 then
      "✅ No failed tests."
    else
      ([$failedList[] | "- ❌ `\(.test.readableKey)` (\(.durationMs // 0)ms)\n  - **Reason:** `\(.failure.type // "Error")`: \(.failure.message // "No message")"] +
       [$cfgFailures[] | "- ⚠️ `\(.test.className).\(.test.methodName)` [\(.attributes.configurationKind // "config")]\n  - **Reason:** `\(.failure.type // "ConfigError")`: \(.failure.message // "No message")"]) | join("\n")
    end)
' "$F"
```

### Example output while running mid-test:

```markdown
## 🧪 Test Execution Progress
**Status:** ⏳ IN PROGRESS | **Duration:** running

### 📊 Summary
- 🏁 **Total Finished:** 2
- ✅ **Passed:** 1
- ❌ **Failed:** 1
- ⏭️ **Skipped:** 0
- 🏃 **Running Now:** 1 (com.example.CheckoutTest.testCart)

### ❌ Failed Tests
- ❌ `com.example.PaymentTest.testCharge` (700ms)
  - **Reason:** `java.lang.AssertionError`: Expected status 200 but received 500
```

### Example output after run completion:

```markdown
## 🧪 Test Execution Progress
**Status:** 🏁 FINISHED | **Duration:** 4500ms

### 📊 Summary
- 🏁 **Total Finished:** 3
- ✅ **Passed:** 2
- ❌ **Failed:** 1
- ⏭️ **Skipped:** 0
- 🏃 **Running Now:** 0

### ❌ Failed Tests
- ❌ `com.example.PaymentTest.testCharge` (700ms)
  - **Reason:** `java.lang.AssertionError`: Expected status 200 but received 500
```

---

## 5. Test Result Analysis (`/testng-live-reporting analysis`)

When the user asks **"analyze the test results"**, **"why did tests fail?"**, or `/analysis`, run this command to extract all failure diagnostics:

```bash
F=$(ls -t target/testng-live-reporter/*.ndjson 2>/dev/null | head -1)

jq -s -r '
  ([.[] | select(.type=="run.summary")] | .[0]) as $sum
  | [.[] | select(.type=="test.event" and .state=="FINISHED" and (.status=="FAILED" or .status=="ERROR" or .status=="TIMEOUT"))] as $fails
  | {
      durationMs: $sum.durationMs,
      totals: $sum.totals,
      failures: [
        $fails[] | {
          phase: (.attributes.phase // "test"),
          configKind: .attributes.configurationKind,
          test: .test.readableKey,
          className: .test.className,
          methodName: .test.methodName,
          status: .status,
          durationMs: .durationMs,
          errorType: .failure.type,
          errorMessage: .failure.message,
          stackSnippet: (.failure.stackTrace // "" | split("\n")[0:5] | join("\n"))
        }
      ]
    }
' "$F"
```

### Formatting the Agent's Analysis Response

The agent synthesizes the JSON above into an executive diagnostic report with the following sections:

1. **Executive Verdict & Summary**:
   - Total executions, overall pass percentage, and suite execution time.
   - High-level health evaluation (e.g. `STABLE`, `REGRESSION DETECTED`, `BLOCKED BY ENVIRONMENT`).
2. **Root Cause Analysis (Why Tests Failed)**:
   - Group failures by common root cause category:
     - 💥 **Assertion Discrepancy**: Business logic deviations (e.g. `AssertionError: expected [200] but found [500]`).
     - 🔌 **Environment / Network**: Connection refused, DNS timeouts, unreachable test databases.
     - ⏱️ **Timeouts**: Test exceeded execution threshold (e.g. TestNG `timeOut = 5000` exceeded).
     - ⚠️ **Configuration Failure**: `@BeforeMethod` / `@BeforeClass` failed, cascading downstream skips.
3. **Actionable Recommendations**:
   - Pinpoint the exact file and lines or API endpoints that require fixes or retries.

#### Example Analysis Output:

```markdown
## 🧐 Test Execution Analysis Report

### 🎯 Conclusion & Verdict
The test run completed with a **75% pass rate** (3 passed, 1 failed) in 4.5 seconds.
The overall test suite is functionally healthy, but **one critical regression was detected in the payment gateway integration**.

### 🔍 Root Cause Analysis (Why Tests Failed)

#### 1. ❌ `com.example.PaymentTest.testCharge`
- **Failure Category:** HTTP 500 Backend Discrepancy
- **Exception:** `java.lang.AssertionError`
- **Message:** `Expected HTTP status 200 but received 500 (Internal Server Error)`
- **Duration:** 700ms
- **Analysis:** The payment processing endpoint `/api/v1/charge` threw an unhandled server error during token authorization. This is an application-level regression introduced in the payment client mock.

### 💡 Recommendations
- Check the payment mock configuration in `src/test/resources/application-test.yml`.
- Verify the mock response payload contains a valid token before re-running `mvn test -Dtest=PaymentTest#testCharge`.
```

---

## 6. Multi-Run Historical Analytics with DuckDB SQL

`duckdb` allows querying all current and archived report files (`target/testng-live-reporter/*.ndjson`) using high-performance SQL.

### Define the DuckDB View

In any query, reference the globbed NDJSON files directly:

```sql
CREATE OR REPLACE VIEW test_events AS
SELECT 
  runId,
  test.readableKey AS test_name,
  test.className AS class_name,
  test.methodName AS method_name,
  status,
  durationMs,
  failure.type AS failure_type,
  failure.message AS failure_message,
  timestamp,
  attributes.phase AS phase
FROM read_ndjson('target/testng-live-reporter/*.ndjson')
WHERE type = 'test.event'
  AND state = 'FINISHED'
  AND (attributes.phase IS NULL OR attributes.phase = 'test');
```

---

### Query A: Historical Runs Overview

Shows the aggregate results of recent executions:

```bash
duckdb -box -c "
SELECT 
  runId,
  MIN(timestamp) AS started_at,
  COUNT(*) AS total_tests,
  COUNT(*) FILTER (WHERE status = 'PASSED') AS passed,
  COUNT(*) FILTER (WHERE status = 'FAILED') AS failed,
  COUNT(*) FILTER (WHERE status = 'SKIPPED') AS skipped,
  ROUND(COUNT(*) FILTER (WHERE status = 'PASSED') * 100.0 / COUNT(*), 1) || '%' AS pass_rate
FROM read_ndjson('target/testng-live-reporter/*.ndjson')
WHERE type = 'test.event' AND state = 'FINISHED'
GROUP BY runId
ORDER BY started_at DESC
LIMIT 10;
"
```

#### Output:
```
┌──────────────┬──────────────────────────┬─────────────┬────────┬────────┬─────────┬───────────┐
│    runId     │        started_at        │ total_tests │ passed │ failed │ skipped │ pass_rate │
├──────────────┼──────────────────────────┼─────────────┼────────┼────────┼─────────┼───────────┤
│ run-20261120 │ 2026-11-20T00:15:00.000Z │          10 │      9 │      1 │       0 │ 90.0%     │
│ run-20261119 │ 2026-11-19T22:30:00.000Z │          10 │      8 │      2 │       0 │ 80.0%     │
│ run-20261118 │ 2026-11-18T18:00:00.000Z │          10 │     10 │      0 │       0 │ 100.0%    │
└──────────────┴──────────────────────────┴─────────────┴────────┴────────┴─────────┴───────────┘
```

---

### Query B: History of a Given Test Across Last N Runs

Tracks the stability and duration of a specific test over time:

```bash
TEST_NAME="com.example.PaymentTest.testCharge"

duckdb -box -c "
SELECT 
  runId,
  timestamp,
  CASE status
    WHEN 'PASSED' THEN '✅ PASSED'
    WHEN 'FAILED' THEN '❌ FAILED'
    WHEN 'SKIPPED' THEN '⏭️ SKIPPED'
    ELSE status
  END AS outcome,
  COALESCE(durationMs || 'ms', '-') AS duration,
  COALESCE(failure.message, '-') AS failure_reason
FROM read_ndjson('target/testng-live-reporter/*.ndjson')
WHERE type = 'test.event' 
  AND state = 'FINISHED'
  AND test.readableKey = '$TEST_NAME'
ORDER BY timestamp DESC
LIMIT 5;
"
```

#### Output:
```
┌──────────────┬──────────────────────────┬───────────┬──────────┬─────────────────────────────┐
│    runId     │        timestamp         │  outcome  │ duration │       failure_reason        │
├──────────────┼──────────────────────────┼───────────┼──────────┼─────────────────────────────┤
│ run-20261120 │ 2026-11-20T00:15:03.200Z │ ✅ PASSED │ 680ms    │ -                           │
│ run-20261119 │ 2026-11-19T22:30:02.100Z │ ❌ FAILED │ 710ms    │ Expected 200 but received 500│
│ run-20261118 │ 2026-11-18T18:00:04.500Z │ ❌ FAILED │ 705ms    │ Expected 200 but received 500│
│ run-20261117 │ 2026-11-17T12:00:01.000Z │ ✅ PASSED │ 650ms    │ -                           │
└──────────────┴──────────────────────────┴───────────┴──────────┴─────────────────────────────┘
```

---

### Query C: Identify Tests That Passed Consistently

Finds completely reliable tests that have never failed across all historical runs:

```bash
duckdb -box -c "
SELECT 
  test.readableKey AS test_name,
  COUNT(*) AS total_executions,
  '✅ 100% Passed' AS status
FROM read_ndjson('target/testng-live-reporter/*.ndjson')
WHERE type = 'test.event' AND state = 'FINISHED'
GROUP BY test.readableKey
HAVING COUNT(*) FILTER (WHERE status != 'PASSED') = 0
ORDER BY test_name;
"
```

---

### Query D: Identify Fixed Tests (Failed Previously, Now Passed)

Detects test regressions that were successfully resolved in the latest run:

```bash
duckdb -box -c "
WITH ranked_runs AS (
  SELECT DISTINCT runId, MIN(timestamp) AS run_time,
         DENSE_RANK() OVER (ORDER BY MIN(timestamp) DESC) AS run_rank
  FROM read_ndjson('target/testng-live-reporter/*.ndjson')
  WHERE type = 'test.event'
  GROUP BY runId
),
test_outcomes AS (
  SELECT 
    e.test.readableKey AS test_name,
    rr.run_rank,
    e.status
  FROM read_ndjson('target/testng-live-reporter/*.ndjson') e
  JOIN ranked_runs rr ON e.runId = rr.runId
  WHERE e.type = 'test.event' AND e.state = 'FINISHED'
)
SELECT 
  t1.test_name,
  '❌ ' || t2.status AS previous_status,
  '✅ ' || t1.status AS current_status,
  '🎉 Fixed / Recovered' AS resolution
FROM (SELECT * FROM test_outcomes WHERE run_rank = 1) t1
JOIN (SELECT * FROM test_outcomes WHERE run_rank = 2) t2
  ON t1.test_name = t2.test_name
WHERE t2.status IN ('FAILED', 'ERROR')
  AND t1.status = 'PASSED';
"
```

#### Output:
```
┌──────────────────────────────────────┬─────────────────┬────────────────┬──────────────────────┐
│              test_name               │ previous_status │ current_status │      resolution      │
├──────────────────────────────────────┼─────────────────┼────────────────┼──────────────────────┤
│ com.example.PaymentTest.testCharge   │ ❌ FAILED        │ ✅ PASSED      │ 🎉 Fixed / Recovered │
└──────────────────────────────────────┴─────────────────┴────────────────┴──────────────────────┘
```

---

### Query E: Compare Runs (e.g. `dev` vs `uat`, or Run A vs Run B)

Compares two specific runs to detect regressions between environments:

```bash
RUN_DEV="run-dev-101"
RUN_UAT="run-uat-202"

duckdb -box -c "
WITH dev AS (
  SELECT test.readableKey AS test_name, status, durationMs
  FROM read_ndjson('target/testng-live-reporter/*.ndjson')
  WHERE type = 'test.event' AND state = 'FINISHED' AND runId = '$RUN_DEV'
),
uat AS (
  SELECT test.readableKey AS test_name, status, durationMs
  FROM read_ndjson('target/testng-live-reporter/*.ndjson')
  WHERE type = 'test.event' AND state = 'FINISHED' AND runId = '$RUN_UAT'
)
SELECT 
  COALESCE(dev.test_name, uat.test_name) AS test_name,
  COALESCE(dev.status, 'NOT_RUN') AS dev_status,
  COALESCE(uat.status, 'NOT_RUN') AS uat_status,
  CASE 
    WHEN dev.status = uat.status THEN '⚖️ Unchanged'
    WHEN dev.status = 'PASSED' AND uat.status = 'FAILED' THEN '🚨 Regression in UAT'
    WHEN dev.status = 'FAILED' AND uat.status = 'PASSED' THEN '✨ Passed in UAT'
    ELSE '⚠️ Diverged'
  END AS diff
FROM dev
FULL OUTER JOIN uat ON dev.test_name = uat.test_name
ORDER BY test_name;
"
```

#### Output:
```
┌─────────────────────────────────────┬────────────┬────────────┬─────────────────────┐
│              test_name              │ dev_status │ uat_status │        diff         │
├─────────────────────────────────────┼────────────┼────────────┼─────────────────────┤
│ com.example.AuthTest.testLogin      │ PASSED     │ PASSED     │ ⚖️ Unchanged        │
│ com.example.CheckoutTest.testCart   │ PASSED     │ FAILED     │ 🚨 Regression in UAT│
│ com.example.PaymentTest.testCharge  │ FAILED     │ PASSED     │ ✨ Passed in UAT    │
└─────────────────────────────────────┴────────────┴────────────┴─────────────────────┘
```

---

### Query F: Test Results Matrix with Status Icons

Generates a dynamic pivot matrix where columns are `runId`s, rows are test names, and cells display emoji icons:

```bash
duckdb -box -c "
WITH test_status AS (
  SELECT 
    test.readableKey AS test_name,
    runId,
    CASE status
      WHEN 'PASSED' THEN '✅'
      WHEN 'FAILED' THEN '❌'
      WHEN 'SKIPPED' THEN '⏭️'
      WHEN 'TIMEOUT' THEN '⏱️'
      ELSE '❓'
    END AS icon
  FROM read_ndjson('target/testng-live-reporter/*.ndjson')
  WHERE type = 'test.event' AND state = 'FINISHED'
)
PIVOT test_status
ON runId
USING FIRST(icon)
ORDER BY test_name;
"
```

#### Rendered Matrix Output:
```
┌─────────────────────────────────────────┬──────────────┬──────────────┬──────────────┐
│                test_name                │ run-20261118 │ run-20261119 │ run-20261120 │
├─────────────────────────────────────────┼──────────────┼──────────────┼──────────────┤
│ com.example.AuthTest.testLogin          │ ✅           │ ✅           │ ✅           │
│ com.example.AuthTest.testOAuth          │ ✅           │ ✅           │ ✅           │
│ com.example.CheckoutTest.testCart       │ ✅           │ ❌           │ ❌           │
│ com.example.PaymentTest.testCharge      │ ❌           │ ❌           │ ✅           │
│ com.example.ReportTest.exportCsv        │ ⏭️           │ ✅           │ ✅           │
└─────────────────────────────────────────┴──────────────┴──────────────┴──────────────┘
```

*Note from matrix:* `PaymentTest.testCharge` was failing in the first two runs and is now fixed in the latest run (`run-20261120`), whereas `CheckoutTest.testCart` regressed in `run-20261119` and remains broken.

---

## 7. Background Polling (Non-blocking)

When test execution runs asynchronously in the background, schedule periodic checks rather than blocking:

```
schedule(CronExpression: "*/5 * * * *", Prompt: "Check testng-live-reporter progress and report deltas")
```

Rules for background polling:
1. Re-evaluate `$F` on every tick (new executions generate new files).
2. Report **only deltas** (new completions or newly failed tests). If counts and states have not changed, do not post redundant messages.
3. Once `🏁 FINISHED` is detected, report the final totals and failed tests summary, then cancel or stop the schedule.

---

## 8. Troubleshooting

| Issue | Cause | Fix |
|---|---|---|
| `duckdb: command not found` | DuckDB CLI is not installed | Install via package manager or place binary in `~/.local/bin` |
| `jq: command not found` | jq CLI is not installed | Install `jq` via `apt install jq` or `brew install jq` |
| No report written | Jars missing from classpath | Add `lib/*.jar` to execution classpath; verify SPI service files |
| `UnsupportedClassVersionError` | JDK < 21 | Ensure tests execute under JDK 21+ |
| Numbers mismatch | Intermediate events counted | Filter `state == "FINISHED"` or read `run.summary.totals.tests` |
| `@BeforeMethod` failure shows tests as SKIPPED | TestNG skips downstream tests on config failure | Check records where `attributes.phase == "configuration"` |
````

---

## 💡 Using the Skill

Once installed, invoke the skill through your AI agent's command interface:

```bash
# Check live progress mid-flight
/testng-live-reporting progress

# Executive analysis & root-cause explanation for failures
/testng-live-reporting analysis

# Multi-run test matrix with emoji status icons
/testng-live-reporting matrix

# Compare runs across environments
/testng-live-reporting compare

# Track history of a given test
/testng-live-reporting history
```
