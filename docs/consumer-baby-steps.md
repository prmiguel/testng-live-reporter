# 🧪 Consuming testng-live-reporter in another project — baby steps

A copy-paste cookbook for adding live JSON reporting to an **existing** TestNG project,
using the published JitPack artifacts. Each step is small, and each one tells you how to
check it worked before moving on.

If you want the full property reference instead, see
[`docs/jitpack-usage.md`](jitpack-usage.md). This document is the short, linear path.

---

## 0. What you get

One TestNG execution produces one pair of files:

| File | Contents |
|---|---|
| `<timestamp>-<runId>.ndjson` | live, append-only, one JSON record per line — readable *while tests run* |
| `<timestamp>-<runId>.json` | consolidated document, written when the run ends |

Both are built from the same records, so they cannot disagree.

---

## 1. Check your prerequisites

```bash
java -version    # must be 21 or newer
mvn -v           # any recent Maven
```

The published jars are compiled for **Java 21**. If `java -version` reports 17, nothing
below will work.

Optional but handy: `jq` for reading the output, and `node` for the bundled validator.

```bash
jq --version
node --version
```

---

## 2. Pick a version

```bash
VERSION=c266692          # a commit, a branch snapshot (main-SNAPSHOT), or a tag
```

> ⚠️ JitPack can only build commits that already contain `jitpack.yml` — that starts at
> `c266692`. Anything older fails to compile. See [§9](#9-troubleshooting).

---

## 3. Download the two jars

The URL is **path-based**: `…/com/github/user/repo/module/version/module-version.jar`.
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
# both files must be non-trivial
ls -l lib/*.jar
# expect ~33 KB each
```

JitPack builds on demand, so a cold commit may take a minute. Retry the `curl` rather than
assuming failure; `-f` makes `curl` exit non-zero on the 404 it returns while building.

---

## 4. Verify the jars (optional but recommended)

```bash
unzip -p lib/testng-live-reporter-core.jar META-INF/services/org.testng.ITestNGListener
# io.github.prmiguel.testng.live.core.LiveReportingListener

unzip -p lib/testng-live-reporter-json.jar \
  META-INF/services/io.github.prmiguel.testng.live.core.spi.ExecutionReporter
# io.github.prmiguel.testng.live.json.JsonExecutionReporter
```

These two service files are the entire "registration". You do not write any config.

**Check compatibility** — confirm the jars really target Java 21:

```bash
unzip -o -q lib/testng-live-reporter-core.jar \
  io/github/prmiguel/testng/live/core/LiveReportingListener.class -d /tmp/cls
od -An -tu1 -j6 -N2 /tmp/cls/io/github/prmiguel/testng/live/core/LiveReportingListener.class \
  | awk '{print "class file major="$2-0" (Java "$2-44")"}'
# class file major=65 (Java 21)
```

```bash
unzip -p lib/testng-live-reporter-json.jar META-INF/testng-live-reporter-json.properties
# reporter.name=testng-live-reporter-json
# reporter.version=1.0.0-SNAPSHOT
```

---

## 5. Copy the jars where your tests can see them

Any of these is fine — pick one.

```bash
# a) alongside your project
cp lib/testng-live-reporter-*.jar /path/to/your-project/lib/

# b) a shared folder referenced from your build
mkdir -p ~/.local/share/testng-live-reporter && cp lib/*.jar $_
```

The jars must end up on the classpath your **tests** run with, not just your compiler's.

---

## 6. Run a small example

### Plain `java`

```bash
mkdir -p out
javac -cp 'lib/*' -d out $(find src -name '*.java')
java  -cp 'out:lib/*' org.testng.TestNG -testclass your.Tests
```

### Explicit listener (equivalent, useful if SPI discovery is blocked)

```bash
java -cp 'out:lib/*' org.testng.TestNG \
  -listener io.github.prmiguel.testng.live.core.LiveReportingListener \
  -testclass your.Tests
```

### Maven Surefire

Add the jars to the forked classpath. This **must** be in the POM — and Surefire must have
a TestNG suite configured, or it runs nothing and writes nothing:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <version>3.5.2</version>
  <configuration>
    <suiteXmlFiles><suiteXmlFile>src/test/resources/testng.xml</suiteXmlFile></suiteXmlFiles>
    <additionalClasspathElements>
      <additionalClasspathElement>${project.basedir}/lib/testng-live-reporter-core.jar</additionalClasspathElement>
      <additionalClasspathElement>${project.basedir}/lib/testng-live-reporter-json.jar</additionalClasspathElement>
    </additionalClasspathElements>
  </configuration>
</plugin>
```

> `-Dsurefire.additionalClasspathElements=…` and `-Dmaven.test.additionalClasspath=…` on the
> command line both **silently do nothing**. Verified.

### Gradle

```groovy
test {
    classpath += files("lib/testng-live-reporter-core.jar", "lib/testng-live-reporter-json.jar")
}
```

---

## 7. Check the report appeared

```bash
ls target/testng-live-reporter/
# 20261119-163926-example-1795106365.json
# 20261119-163926-example-1795106365.ndjson
```

```bash
head -1 target/testng-live-reporter/*.ndjson
# {"type":"run.started","schemaVersion":1,"runId":"...","startedAt":"...","reporter":{...}}

tail -1 target/testng-live-reporter/*.ndjson
# {"type":"run.summary",...,"totals":{"events":12,"tests":4,"passed":3,"failed":1,...}}
```

Full validation — every line parses, counts match, no duplicate `(executionId, state)`, and
the document splices exactly the streamed events:

```bash
node .github/scripts/validate-reports.mjs target/testng-live-reporter
# reports in target/testng-live-reporter are valid
```

(That script lives in this repository; copy it, or use the `jq` recipes in §8.)

---

## 8. Understand the schema

Three record kinds, discriminated by `type`:

| `type` | When | Key fields |
|---|---|---|
| `run.started` | first line | `runId`, `startedAt`, `reporter` |
| `test.event` | per lifecycle event | `executionId`, `state`, `status`, `timestamp`, `test`, `threadId`, `invocationIndex` |
| `run.summary` | last line | `totals`, `durationMs` |

`state` is lifecycle — `DISCOVERED`, `QUEUED`, `STARTED`, `IN_PROGRESS`, `FINISHED`.
`status` is outcome — `UNDETERMINED` until `FINISHED`, then `PASSED`, `FAILED`, `SKIPPED`,
`IGNORED`, `TIMEOUT`, `INTERRUPTED`, `ERROR`.

One `test.event`, abridged:

```json
{"type":"test.event","recordSchemaVersion":1,"eventSchemaVersion":1,"sequence":1,
 "runId":"example-1795106365","executionId":"example-1795106365-594dfe9cd458a504-0-0-1",
 "state":"STARTED","status":"UNDETERMINED","timestamp":"2026-11-19T16:39:25.997Z",
 "test":{"testId":"demo.SmokeTest.dataProviderRow[1]#594dfe9cd458a504",
         "hash":"594dfe9cd458a504","className":"demo.SmokeTest","methodName":"dataProviderRow",
         "readableKey":"demo.SmokeTest.dataProviderRow[1]",
         "parameters":[{"declaredType":"int","runtimeType":"java.lang.Integer","value":"1"}]},
 "suiteName":"Command line suite","testName":"Command line test",
 "threadId":1,"threadName":"main","invocationIndex":0,"retryAttempt":0,"attributes":{}}
```

Notes that save time:

- `durationMs` appears **only** on `FINISHED` events.
- `failure` appears **only** when something failed.
- Absent values are omitted, never `null`.
- `executionId` identifies one attempt; two data-provider rows differ in `parameters` **and**
  in `hash`, so they never collide.

Inspect the shipped schema directly:

```bash
jq -r '.definitions.testEvent.properties | keys[]' \
  reporter-json/src/main/resources/schema/testng-live-report-v1.schema.json
jq -r '.definitions.testEvent.required[]' \
  reporter-json/src/main/resources/schema/testng-live-report-v1.schema.json
```

---

## 9. Read, filter, and get numbers

All of these run against a real file. `F` is the stream:

```bash
F=$(ls -t target/testng-live-reporter/*.ndjson | head -1)
```

### Numbers from a completed run

```bash
# outcome counts, straight from the events
jq -r 'select(.type=="test.event") | .status' "$F" | sort | uniq -c
#       1 FAILED
#       3 PASSED
#       8 UNDETERMINED

# the authoritative totals (last line)
jq -s 'map(select(.type=="run.summary")) | .[0].totals' "$F"
```

```json
{"events":12,"tests":4,"passed":3,"failed":1,"skipped":0,"ignored":0,"timeout":0,
 "interrupted":0,"error":0,"undetermined":8,"droppedDuplicates":4,"filtered":0,"writeErrors":0}
```

`tests` counts distinct attempts that reached a terminal status; `events` counts every
record. `undetermined` is non-zero simply because each test emits `STARTED`/`IN_PROGRESS`
before it finishes — that is normal, not a problem.

### Numbers while the run is still going

A run in progress has no `run.summary` yet, so derive from the events. Works against the
same file mid-run because each record is flushed as it is written:

```bash
jq -s -r '
  [.[] | select(.type=="test.event")] as $e
  | ($e | map(select(.state=="FINISHED") | .executionId) | unique) as $done
  | ($e | map(select((.state != "FINISHED") and ((.executionId as $id | $done | index($id)) | not))))
  | {stillRunning: (map(.executionId) | unique | length), openEvents: length}' "$F"
```

Against a truncated stream (one attempt had emitted `IN_PROGRESS` and no `FINISHED` yet) this
returned `{"stillRunning": 1, "openEvents": 2}`. Against a completed run it returns zeros,
which is the right answer rather than a bug.

### Filter: only failures, with the reason

```bash
jq -r 'select(.type=="test.event" and .status=="FAILED")
       | "\(.test.className).\(.test.methodName)  \(.failure.type): \(.failure.message)"' "$F"
```

### Filter: only terminal outcomes (one line per test)

```bash
jq -r 'select(.state=="FINISHED")
       | "\(.status)\t\(.test.readableKey)"' "$F" | sort
```

### Filter: slowest tests

```bash
jq -r 'select(.state=="FINISHED") | "\(.durationMs)\t\(.test.methodName)"' "$F" | sort -rn | head -5
```

### Filter: one test across data-provider rows

```bash
jq -r 'select(.test.methodName=="dataProviderRow")
       | "\(.test.readableKey)  \(.state)/\(.status)"' "$F"
```

### Follow a single attempt

```bash
EXEC=$(jq -r 'select(.type=="test.event") | .executionId' "$F" | head -1)
jq -r --arg e "$EXEC" 'select(.executionId==$e) | "\(.timestamp) \(.state)/\(.status)"' "$F"
```

### Watch it live

```bash
tail -f "$F" | jq -c '{t:.type, m:.test.methodName, s:.state, st:.status}'
```

### Poll every 5 minutes instead of blocking

When the suite runs in the background, don't sit in `tail -f` and don't wait for it. Print
one status line on a 5-minute cadence — `cron_create(cron: "*/5 * * * *", …)` in an agent
session, or `/loop 5m`, or plain `cron`/`sleep 300` outside one:

```bash
DIR=target/testng-live-reporter
F=$(ls -t "$DIR"/*.ndjson 2>/dev/null | head -1) || exit 0
jq -s -r '
  [.[] | select(.type=="test.event")] as $e
  | ($e | map(select(.state=="FINISHED"))) as $fin
  | ($fin | map(.executionId) | unique) as $done
  | ($e | map(select((.state != "FINISHED") and ((.executionId as $id | $done | index($id)) | not)))) as $live
  | ([.[] | select(.type=="run.summary")] | .[0]) as $s
  | "\($fin | length) done (\([$fin[]|select(.status=="PASSED")]|length) passed, \([$fin[]|select(.status=="FAILED")]|length) failed) | \($live|map(.executionId)|unique|length) running: \(($live|last//{}).test.methodName // "-") | \(if $s then "finished" else "in progress" end)"' "$F"
```

```
1 done (1 passed, 0 failed) | 1 running: dataProviderRow | in progress
4 done (3 passed, 1 failed) | 0 running: - | finished
```

Re-read `$F` on every tick — a new execution writes a new filename. Stop once `finished`
appears and report the final totals from `run.summary`.

### The consolidated document instead

```bash
jq '.summary.totals' target/testng-live-reporter/*.json
jq -r '.events[] | select(.status=="FAILED") | .test.readableKey' target/testng-live-reporter/*.json
```

---

## 10. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `curl` exits 22 / empty jar | that commit has not been built, or predates `jitpack.yml` | use `c266692` or later; read `…/<version>/build.log` |
| `error: release version 21 not supported` in the log | JitPack default image is JDK 17 | same — old commit |
| `no report was written` | jars not on the **test** classpath | use `lib/*` in `java -cp`; or the `-listener` flag |
| `Tests run:` never printed under Surefire | no TestNG suite configured | add `<suiteXmlFiles>` or `-Dtest=` |
| `UnsupportedClassVersionError` | JDK older than 21 | switch to JDK 21 |
| Two files per run | expected | one `.ndjson` + one `.json` |
| `undetermined` looks too high | it counts `STARTED`/`IN_PROGRESS` | use `tests`, or filter `state=="FINISHED"` |

---

## 11. Try it with zero setup

The repository contains a consumer that does all of the above and validates the result:

```bash
cd examples/jitpack-consumer
./run.sh c266692
```

Its `pom.xml` declares only TestNG — the reporter is injected, proving none of the steps
above require a dependency declaration.

---

## Appendix — a ready-made Agent Skill

The snippet below is a complete, self-contained skill you can drop into a project so an
agent can set this up, run it, and answer "how are the tests going?".

Create the file at:

```
.commandcode/skills/testng-live-reporting/SKILL.md
```

The directory name **must** match `name:` in the frontmatter, or the skill is skipped.

````markdown
---
name: testng-live-reporting
description: Install, run, and query testng-live-reporter so TestNG executions emit live JSON reports. Use when asked to add live test reporting to a TestNG project, check which tests are running now, report progress while tests run in the background, or summarise pass/fail counts from a run that is in progress or finished.
argument-hint: "[install|check|status|summary|failures|watch] [project-dir]"
---

# TestNG live reporting

Reporters write one `.ndjson` stream plus one `.json` document per execution. Both are
produced by the test JVM; this skill installs the jars, then reads those files.

## Rules

- Read the report files. Never re-run the suite just to answer a status question.
- Quote real numbers only; if no file exists, say so and show the install step.
- The stream is append-only, so a status query works mid-run. Do not wait for `run.summary`.
- Prefer the newest file: `ls -t <dir>/*.ndjson | head -1`.
- If the suite runs in the background, never block: schedule a 5-minute tick per §7 and
  report only deltas.

## 1. Install

Ask which version to pin, defaulting to `main-SNAPSHOT` for development.

```bash
VERSION="${1:-main-SNAPSHOT}"
BASE="https://jitpack.io/com/github/prmiguel/testng-live-reporter"
mkdir -p lib
for m in testng-live-reporter-core testng-live-reporter-json; do
  for i in $(seq 1 30); do
    curl -fsSL -o "lib/$m.jar" "$BASE/$m/$VERSION/$m-$VERSION.jar" && break
    sleep 10
  done
  [ -s "lib/$m.jar" ] || { echo "could not fetch $m@$VERSION"; exit 1; }
done
```

Then put `lib/*.jar` on the test classpath:

- plain java: `java -cp 'out:lib/*' org.testng.TestNG -testclass <Suite>`
- surefire: `<additionalClasspathElements>` in the POM **plus** `<suiteXmlFiles>`
- gradle: `test { classpath += files("lib/*.jar") }`

The command-line flag `-Dsurefire.additionalClasspathElements` does nothing; do not use it.

## 2. Verify the installation

```bash
unzip -p lib/testng-live-reporter-core.jar META-INF/services/org.testng.ITestNGListener
unzip -p lib/testng-live-reporter-json.jar META-INF/services/io.github.prmiguel.testng.live.core.spi.ExecutionReporter
od -An -tu1 -j6 -N2 <(unzip -p lib/testng-live-reporter-core.jar \
  io/github/prmiguel/testng/live/core/LiveReportingListener.class) \
  | awk '{print "class file major="$2-0" (Java "$2-44")"}'
```

Both service files must be present and the class file must report Java 21 (major 65).
Anything else means the jars will not be loaded.

## 3. Check compatibility

| Requirement | How to check | Expected |
|---|---|---|
| JDK | `java -version` | 21+ |
| TestNG | `mvn dependency:tree \| grep testng` | 7.10.x or 7.11.x |
| Jars built for Java 21 | step 2 | major 65 |

## 4. Run a small example

```bash
mkdir -p out && javac -cp 'lib/*' -d out $(find src -name '*.java')
java -cp 'out:lib/*' org.testng.TestNG -testclass <Suite>
ls -t target/testng-live-reporter/ | head -2     # expect a .ndjson and a .json
```

## 5. Identify the output

| File | Use |
|---|---|
| `*.ndjson` | live; one record per line; safe to read mid-run |
| `*.json` | final; `{schemaVersion, run, events[], summary}` |

Record kinds are `run.started`, `test.event`, `run.summary`.
`state` ∈ DISCOVERED, QUEUED, STARTED, IN_PROGRESS, FINISHED.
`status` ∈ UNDETERMINED (until FINISHED), PASSED, FAILED, SKIPPED, IGNORED, TIMEOUT,
INTERRUPTED, ERROR.
`durationMs` only on FINISHED; `failure` only on failures.

## 6. Answer status questions

Finished run — use the authoritative totals:

```bash
F=$(ls -t target/testng-live-reporter/*.ndjson | head -1)
jq -s 'map(select(.type=="run.summary")) | .[0].totals' "$F"
```

In progress — no summary exists yet, derive it:

```bash
jq -s -r '
  [.[] | select(.type=="test.event")] as $e
  | ($e | map(select(.state=="FINISHED") | .executionId) | unique) as $done
  | ($e | map(select((.state != "FINISHED") and ((.executionId as $id | $done | index($id)) | not))))
  | {stillRunning: (map(.executionId) | unique | length), openEvents: length}' "$F"
```

Counts by outcome:

```bash
jq -r 'select(.type=="test.event") | .status' "$F" | sort | uniq -c
```

Report as: "N tests: X passed, Y failed, Z in progress" plus the names of failures. If the
numbers look odd, explain that `undetermined` counts STARTED/IN_PROGRESS records, and that
`totals.tests` counts terminal attempts only.

If the suite is running in the background, do not block — schedule the tick in §7.

## 7. Report progress every 5 minutes while tests run in the background

A long suite runs detached, so the agent must poll instead of blocking. Use whichever
scheduling action this session provides — a fixed cron entry is simplest:

```
cron_create(cron: "*/5 * * * *", prompt: "Report testng-live-reporter progress for <project-dir>", recurring: true)
```

or, when the agent paces itself, the `loop` skill (`/loop 5m <task>`). Either way each tick
must be cheap: read the newest report file and print **one line**. Never re-run the suite.

The whole tick — copy it as-is, only `DIR` changes:

```bash
DIR=target/testng-live-reporter
F=$(ls -t "$DIR"/*.ndjson 2>/dev/null | head -1) || exit 0
jq -s -r '
  [.[] | select(.type=="test.event")] as $e
  | ($e | map(select(.state=="FINISHED"))) as $fin
  | ($fin | map(.executionId) | unique) as $done
  | ($e | map(select((.state != "FINISHED") and ((.executionId as $id | $done | index($id)) | not)))) as $live
  | ([.[] | select(.type=="run.summary")] | .[0]) as $s
  | "\($fin | length) done (\([$fin[]|select(.status=="PASSED")]|length) passed, \([$fin[]|select(.status=="FAILED")]|length) failed, \([$fin[]|select(.status=="SKIPPED")]|length) skipped) | \($live|map(.executionId)|unique|length) running: \(($live|last//{}).test.methodName // "-") | \(if $s then "finished in \($s.durationMs)ms" else "in progress" end)"' "$F"
```

Output, verbatim from real runs — mid-run while one test was still executing:

```
1 done (1 passed, 0 failed, 0 skipped) | 1 running: dataProviderRow | in progress
```

and again after the run completed:

```
4 done (3 passed, 1 failed, 0 skipped) | 0 running: - | finished in 10ms
```

Rules for the tick:

- **Say nothing new if nothing changed.** Repeating identical output every 5 minutes is noise;
  report deltas (a new failure, a status flip) and stay quiet otherwise.
- **Finish the loop** once `finished` appears, and report the final totals from
  `run.summary` in that same tick.
- **If the file is missing**, report "no report yet" once — do not loop on an error.
- **Read `$F` again each tick**; the path changes with every execution.

## 8. Filtering

```bash
# failures with reasons
jq -r 'select(.status=="FAILED") | "\(.test.readableKey): \(.failure.message)"' "$F"
# slowest
jq -r 'select(.state=="FINISHED") | "\(.durationMs)\t\(.test.methodName)"' "$F" | sort -rn | head -5
# one attempt end to end
jq -r --arg e "$(jq -r 'select(.type=="test.event")|.executionId' "$F"|head -1)" \
  'select(.executionId==$e) | "\(.timestamp) \(.state)/\(.status)"' "$F"
```

## 9. Validate the output

```bash
node .github/scripts/validate-reports.mjs target/testng-live-reporter
```

If that script is not present, check at minimum that every line parses:
`jq -c . "$F" > /dev/null` and that the last record is `run.summary`.

## 10. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| no report file | jars not on the test classpath | redo step 1; verify step 2 |
| `curl` exit 22 | commit not built / predates `jitpack.yml` | use `main-SNAPSHOT` or `c266692`+ |
| `UnsupportedClassVersionError` | JDK < 21 | install JDK 21 |
| Surefire prints no `Tests run:` | no suite configured | add `<suiteXmlFiles>` |
| totals look inflated | counting all events | use `totals.tests` or filter `state=="FINISHED"` |
````

### Using the skill

```bash
# it must appear with no warning
cmd skills list | grep testng-live-reporting

# then invoke it
/testng-live-reporting install
/testng-live-reporting status
```

Keep the frontmatter rules in mind if you rename it: lowercase, digits and hyphens only, and
the `name:` value must equal the directory name.
