# 🔌 Using testng-live-reporter from JitPack

This guide shows how to get live JSON reports out of an existing TestNG project by
**injecting** the reporter's jars onto the classpath. You do not add a dependency, you do
not change your test code.

---

## Why injection is enough

Nothing needs to be registered manually. Both jars announce themselves through service
files, and TestNG plus the core listener pick them up automatically:

| Jar | Service file it ships | Effect |
|---|---|---|
| `testng-live-reporter-core` | `META-INF/services/org.testng.ITestNGListener` | TestNG loads `LiveReportingListener` |
| `testng-live-reporter-json` | `META-INF/services/io.github.prmiguel.testng.live.core.spi.ExecutionReporter` | the listener discovers `JsonExecutionReporter` |

So "setting it up" is literally: put both jars on the classpath your tests run with. You can
verify this yourself at any time:

```bash
unzip -p testng-live-reporter-json.jar \
  META-INF/services/io.github.prmiguel.testng.live.core.spi.ExecutionReporter
# io.github.prmiguel.testng.live.json.JsonExecutionReporter
```

## Prerequisites

- **JDK 21** — the project compiles with `maven.compiler.release=21`, and so do the published
  jars. JDK 17 cannot load them.
- **TestNG 7.10.x or 7.11.x** — both are covered by the compatibility workflow.

## 1. Pick a version

Any of these works as `VERSION` below:

| Kind | Example | Notes |
|---|---|---|
| Commit hash | `c266692` | Fastest to try; the commit must already contain `jitpack.yml` |
| Branch snapshot | `main-SNAPSHOT` | Rebuilt on demand; fine for development |
| Release tag | `1.0.0` | Only after you cut a tag |

> ⚠️ **JitPack can only build commits that contain `jitpack.yml`** (added in `c266692`).
> Requesting anything older fails during compile — see [Troubleshooting](#troubleshooting).

## 2. Get the jars

Multi-module coordinates matter here: the **group is the repository**
(`com.github.prmiguel.testng-live-reporter`) and the **artifact is the module**. But the
download URL is path-based — note the slash, not a dot, between the user and the repo.

### a. Plain download (what `examples/jitpack-consumer` does)

```bash
VERSION=c266692
BASE="https://jitpack.io/com/github/prmiguel/testng-live-reporter"
for module in testng-live-reporter-core testng-live-reporter-json; do
  curl -fsSL -o "$module.jar" "$BASE/$module/$VERSION/$module-$VERSION.jar"
done
```

JitPack builds on the first request, so a cold commit can take a while. Retry on failure;
the example script retries 20 times with a 10-second pause.

### b. Let Maven fetch them once (still no test-scope dependency)

```bash
mvn dependency:copy \
  -Dartifact=com.github.prmiguel.testng-live-reporter:testng-live-reporter-json:c266692 \
  -DoutputDirectory=lib
```

Requires `https://jitpack.io` as a `<repository>` in the POM you are running this against.
Note `-DremoteRepositories=jitpack::::https://jitpack.io` on the command line does **not**
work — the artifact resolves against Maven Central only and fails.

### Getting every module at once

`com.github.prmiguel.testng-live-reporter:testng-live-reporter` publishes an aggregating
artifact. JitPack rewrites module-internal dependencies to the requested version, so a single
version resolves the whole graph.

## 3. Inject into your run

### Plain `java -cp` (used by the example)

```bash
javac -cp 'lib/*' -d out $(find src -name '*.java')
java  -cp 'out:lib/*' org.testng.TestNG -testclass demo.SmokeTest
```

### Maven Surefire

Add the jars to the forked test classpath. This needs a TestNG suite configured — without
`<suiteXmlFiles>` (or `-Dtest=`), Surefire runs nothing at all and no report appears:

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

Verified: produces `target/testng-live-reporter/*.ndjson` with no reporter dependency
declared. Note that passing `-Dsurefire.additionalClasspathElements=...` on the command line
does **not** work — it must be in the POM.

### Gradle

```groovy
test {
    classpath += files("lib/testng-live-reporter-core.jar", "lib/testng-live-reporter-json.jar")
}
```

### IDE

IntelliJ: *Project Structure → Libraries → + → Java* and select both jars, keeping the
scope *Test*. Eclipse: *Java Build Path → Libraries → Add External JARs*.

## 4. Configure the output (optional)

Sensible defaults need no configuration. The settings worth knowing:

| Property | Default | Use |
|---|---|---|
| `testng.live.json.output-dir` | `target/testng-live-reporter` | where files are written |
| `testng.live.json.file-name` | `<timestamp>-<runId>` | fixed basename |
| `testng.live.json.include-statuses` | all | e.g. `FAILED,TIMEOUT` |
| `testng.live.json.exclude-classes` | none | glob, e.g. `com.acme.legacy.*` |

Pass them as system properties (`-Dtestng.live.json.output-dir=...`) or environment
variables (`TESTNG_LIVE_JSON_OUTPUT_DIR=...`). The full table is in
[`reporter-json/README.md`](../reporter-json/README.md).

## 5. Check it worked

Each execution writes **one pair** of files: `<name>.ndjson` (live, append-only) and
`<name>.json` (consolidated at the end).

```bash
ls target/testng-live-reporter/
# 20261119-163119-example-1795105878.json
# 20261119-163119-example-1795105878.ndjson

head -1 target/testng-live-reporter/*.ndjson | cut -c1-120
# {"type":"run.started","schemaVersion":1,"runId":"example-1795105878",...

tail -1 target/testng-live-reporter/*.ndjson
# {"type":"run.summary",...,"totals":{"events":12,"tests":4,"passed":3,"failed":1,...}}
```

Human-readable checks:

```bash
# one record per line, all valid JSON
jq -c . target/testng-live-reporter/*.ndjson > /dev/null && echo "all lines parse"

# the consolidated document
jq '.summary.totals' target/testng-live-reporter/*.json
```

Machine check — the repository ships a validator that asserts every NDJSON line parses, the
record count is `header + events + summary`, totals match the counted statuses, no
`(executionId, state)` pair repeats, and the document splices exactly the streamed events:

```bash
node .github/scripts/validate-reports.mjs target/testng-live-reporter
# reports in target/testng-live-reporter are valid
```

## 6. Run the bundled example

A complete, self-contained consumer lives in
[`examples/jitpack-consumer`](../examples/jitpack-consumer). Its `pom.xml` declares **only
TestNG** — a comment marks the reporter as deliberately absent.

```bash
cd examples/jitpack-consumer
./run.sh c266692        # or any commit/tag containing jitpack.yml
```

It resolves TestNG, downloads both jars, compiles the fixture, runs it, prints where the
reports went, and validates them. Re-validate without re-running:

```bash
./run.sh --verify-only
```

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `error: release version 21 not supported` in the JitPack log | the default JitPack image is JDK 17 | use a commit containing `jitpack.yml` (`c266692` or later) |
| `Maven Wrapper not found` | that commit predates the committed `mvnw` | same — use a newer commit |
| 404 or a 0-byte jar | the commit has not been built yet | retry; inspect `https://jitpack.io/com/github/prmiguel/testng-live-reporter/<version>/build.log` |
| `no report was written` | jars are not on the *test* classpath | confirm both jars are in `java -cp`; check the service files above |
| `Tests run:` never appears under Surefire | no TestNG suite configured, so Surefire ran nothing | add `<suiteXmlFiles>` or pass `-Dtest=` |
| Two files per run | expected | one `.ndjson` plus one `.json` per execution |
| `UnsupportedClassVersionError` | running under JDK < 21 | switch to JDK 21 |

## Cheat sheet

```bash
VERSION=c266692
BASE="https://jitpack.io/com/github/prmiguel/testng-live-reporter"
mkdir -p lib
for m in testng-live-reporter-core testng-live-reporter-json; do
  curl -fsSL -o "lib/$m.jar" "$BASE/$m/$VERSION/$m-$VERSION.jar"
done
java -cp 'out:lib/*' org.testng.TestNG -testclass your.Tests
node .github/scripts/validate-reports.mjs target/testng-live-reporter
```
