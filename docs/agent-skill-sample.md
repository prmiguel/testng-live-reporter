# 🤖 AI Agent Skill & Progress Reporting for testng-live-reporter

This guide provides a ready-made **Agent Skill** for AI coding assistants (such as Antigravity, Claude Code, Gemini CLI, Cursor, CommandCode, GitHub Copilot CLI, etc.).

Because `testng-live-reporter` writes an append-only `.ndjson` stream that is flushed to disk on each lifecycle event, an agent does **not** need to block on the test suite or re-run tests to inspect status. It can monitor runs mid-flight and present clear, structured progress summaries to the user.

---

## 📋 What the Agent Skill Does

When an agent is asked **"how are tests going?"**, `/progress`, or `/status`:
1. **Emoji Progress Summary**: Emits a structured dashboard featuring status indicators, duration, counts for passed/failed/skipped/running tests, and current in-flight tests.
2. **Dedicated Failed Tests Section**: Lists every test attempt that finished with a `FAILED` or `ERROR` status (including `@BeforeMethod`/`@BeforeClass` configuration failures) with its execution time and root cause.
3. **Non-blocking Background Polling**: Outlines rules for polling detached test runs without blocking the conversation.

---

## 🛠️ Installing the Skill

Create the skill file inside your project or user workspace:

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
description: Install, run, and query testng-live-reporter so TestNG executions emit live JSON reports. Use when asked to add live test reporting to a TestNG project, check which tests are running now, report progress with emoji summaries while tests run, or list failed tests from a run in progress or finished.
argument-hint: "[install|check|status|progress|failures|summary|watch] [project-dir]"
---

# TestNG live reporting

`testng-live-reporter` writes one append-only `.ndjson` stream plus one `.json` consolidated document per execution in `target/testng-live-reporter/`.

This skill guides installing the jars, launching suites, querying live progress, and formatting status updates for users.

## 🧭 Core Agent Rules

- **Read the report files directly.** Never re-run the test suite just to answer a status or progress question.
- **Provide clear, emoji-rich progress output.** Whenever the user asks for progress or status, present:
  1. A summary with emoji icons (`🏁`, `✅`, `❌`, `⏭️`, `🏃`, `⏳`).
  2. A dedicated section listing all tests that finished failed.
- **Quote real numbers only.** If no report file exists, state that clearly and offer the install step.
- **Do not block while tests run.** If tests run in the background, poll periodically using a non-blocking schedule (e.g. 5 minutes). Report only deltas (new completions or failures) and remain silent if state is unchanged.
- **Prefer the newest report file:**
  ```bash
  F=$(ls -t target/testng-live-reporter/*.ndjson 2>/dev/null | head -1)
  ```

---

## 1. Install the reporter jars

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

## 2. Verify installation & compatibility

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

## 3. Identify report files

| File | Use |
|---|---|
| `*.ndjson` | Live append-only stream; safe to read while tests are running |
| `*.json` | Consolidated document; contains `tests[]`, `configurationFailures[]`, and `summary` |

Record types:
- `run.started`: Run metadata and starting timestamp.
- `test.event`: Lifecycle event. `state` ∈ `STARTED`, `IN_PROGRESS`, `FINISHED`. `status` ∈ `UNDETERMINED` (in-flight), `PASSED`, `FAILED`, `SKIPPED`, `TIMEOUT`, `ERROR`.
- `run.summary`: Final counts, emitted on the last line of `.ndjson`.

---

## 4. Progress & Status Reporting

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

## 5. Completed Run: Querying the consolidated document

When tests are completed, you can also query `target/testng-live-reporter/*.json` directly:

```bash
J=$(ls -t target/testng-live-reporter/*.json 2>/dev/null | head -1)

jq -r '
  .run as $r | .summary as $s
  | "## 🧪 Test Execution Results\n\n" +
    "**Status:** 🏁 FINISHED | **Run ID:** `\($r.runId)` | **Duration:** \($s.durationMs // 0)ms\n\n" +
    "### 📊 Summary\n" +
    "- 🏁 **Total Finished:** \($s.totals.tests // 0)\n" +
    "- ✅ **Passed:** \($s.totals.passed // 0)\n" +
    "- ❌ **Failed:** \($s.totals.failed // 0)\n" +
    "- ⏭️ **Skipped:** \($s.totals.skipped // 0)" +
    (if ($s.totals.error // 0) > 0 then "\n- 💥 **Errors:** \($s.totals.error)" else "" end) +
    (if ($s.totals.configurationFailures // 0) > 0 then "\n- ⚠️ **Configuration Failures:** \($s.totals.configurationFailures)" else "" end) +
    "\n\n### ❌ Failed Tests\n" +
    (
      ([.tests[]? | select(.status == "FAILED" or .status == "ERROR") | "- ❌ `\(.readableKey // .methodName)` (\(.durationMs // 0)ms)\n  - **Reason:** `\(.failure.type // "Error")`: \(.failure.message // "No message")"] +
       [.configurationFailures[]? | "- ⚠️ `\(.className // "")\(if .className then "." else "" end)\(.methodName)` [\(.configurationKind // "config")]\n  - **Reason:** `\(.failure.type // "ConfigError")`: \(.failure.message // "No message")"]) as $fails
      | if ($fails | length) == 0 then "✅ No failed tests." else ($fails | join("\n")) end
    )
' "$J"
```

---

## 6. Background Polling (Non-blocking)

When test execution runs asynchronously in the background, schedule periodic checks rather than blocking:

```
schedule(CronExpression: "*/5 * * * *", Prompt: "Check testng-live-reporter progress and report deltas")
```

Rules for background polling:
1. Re-evaluate `$F` on every tick (new executions generate new files).
2. Report **only deltas** (new completions or newly failed tests). If counts and states have not changed, do not post redundant messages.
3. Once `🏁 FINISHED` is detected, report the final totals and failed tests summary, then cancel or stop the schedule.

---

## 7. Additional Filtering Recipes

```bash
# Slowest 5 tests
jq -r 'select(.state=="FINISHED" and ((.attributes.phase // "test") == "test")) | "\(.durationMs)ms\t\(.test.readableKey)"' "$F" | sort -rn | head -5

# Follow lifecycle of a specific attempt
EXEC_ID="<id>"
jq -r --arg id "$EXEC_ID" 'select(.executionId==$id) | "\(.timestamp) [\(.state)] \(.status)"' "$F"
```

---

## 8. Troubleshooting

| Issue | Cause | Fix |
|---|---|---|
| No report written | Jars missing from classpath | Add `lib/*.jar` to execution classpath; verify SPI service files |
| `UnsupportedClassVersionError` | JDK < 21 | Ensure tests execute under JDK 21+ |
| Numbers mismatch | Intermediate events counted | Filter `state == "FINISHED"` or read `run.summary.totals.tests` |
| `@BeforeMethod` failure shows tests as SKIPPED | TestNG skips downstream tests on config failure | Check records where `attributes.phase == "configuration"` |
````

---

## 💡 Using the Skill

Once installed, invoke the skill through your AI agent's command interface:

```bash
/testng-live-reporting status
/testng-live-reporting progress
/testng-live-reporting failures
```
