# AGENTS.md

## Build and test

- JDK 21 is required.
- Build and test everything:

```bash
mvn clean verify
```

- Run core tests only:

```bash
mvn -pl core test
```

- Run one test class:

```bash
mvn -pl core test -Dtest=LiveReportingListenerIntegrationTest
```

- TestNG writes `core/test-output/`; it is gitignored and should not be committed.

## CI

- `ci.yml` runs on every push to `main` and every pull request: `mvn -B -ntp clean verify`,
  then a dogfood run, then `node .github/scripts/validate-reports.mjs`.
- `compatibility.yml` is schedule + manual only and matrices TestNG 7.10.2 and 7.11.0 via
  `-Dtestng.version=<v>`.
- `codeql.yml` analyses `java-kotlin`; `dependency-review.yml` runs on pull requests only.
- Reproduce the dogfood step locally:

```bash
mvn -B -ntp -pl reporter-json -am test -Dtest=JsonOutputDogfoodTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Ddogfood.output-dir="$PWD/reporter-json/target/dogfood"
node .github/scripts/validate-reports.mjs reporter-json/target/dogfood
```

- `JsonOutputDogfoodTest` only runs when `-Ddogfood.output-dir` is set, so a plain
  `mvn verify` stays hermetic.
- `ci.yml`'s `jitpack-injection` job runs `examples/jitpack-consumer/run.sh` against the
  published JitPack jars for the pushed commit. It is skipped on pull requests because
  JitPack cannot build fork commits.

## JitPack example

- `examples/jitpack-consumer` is deliberately **not** a reactor module and declares only
  TestNG; the reporter is injected onto the classpath, which is the point of the example.
- Do not add the reporter as a dependency there — that would defeat the test.
- `docs/jitpack-usage.md` documents the whole flow; keep it in sync with `run.sh`.
- Run it with `cd examples/jitpack-consumer && ./run.sh <version>`; `--verify-only`
  re-validates existing output without downloading.

## Module layout

- Maven multi-module project; `core` and `reporter-json` exist today.
- `core` artifact: `io.github.prmiguel:testng-live-reporter-core`.
- `reporter-json` artifact: `io.github.prmiguel:testng-live-reporter-json`.
- Remaining reporters (`reporter-sqlite`, `reporter-cache-db`, `reporter-ctrf`) are planned but not implemented yet.
- `README.md` still documents some planned work; treat the code as the source of truth.

## Architecture notes

- TestNG listener entry point: `io.github.prmiguel.testng.live.core.LiveReportingListener`.
- Reporter plugins are loaded with `ServiceLoader` from `META-INF/services/io.github.prmiguel.testng.live.core.spi.ExecutionReporter`.
- The listener itself is registered through `META-INF/services/org.testng.ITestNGListener`.
- Event dispatch is asynchronous and one-thread-per-reporter; callers should never block on reporters.
- `ExecutionEvent` instances are immutable; only `ExecutionAttempt` is mutable internally.
- `ExecutionAttempt` guards every transition: `markStarted()`, `markInProgress()` and
  `markFinished()` each succeed at most once, so a duplicated TestNG callback cannot
  publish the same state twice. Do not remove these guards to "simplify" the listener.
- Suite and test names live on the `ExecutionAttempt`, captured from
  `result.getTestContext()`. Never hold them in a field on the listener: under
  `parallel="tests"` contexts interleave and a single field mis-attributes events.
- Run boundaries reach reporters through `ExecutionReporter.onRunStarted`/`onRunFinished`
  with a `RunInfo`, not through `ExecutionEvent`, whose `testIdentity` is mandatory.
- `ExecutionState.QUEUED` is reserved and never emitted; TestNG has no callback for it.
- `ExecutionTrackerRegistry` keeps only in-flight attempts; `complete()` must be called
  after the terminal event is published, never before.
- Test identity hashes are the first 16 lowercase hex characters of SHA-256.

## Testing quirks

- The single integration test launches TestNG programmatically from JUnit 5.
- `SampleTests.timeoutTest` uses a 100 ms TestNG timeout and sleeps 5 seconds; expect TestNG failures and a roughly 5-second run.
- `SampleTests.excludedTest` is in the `excluded` group so the suite produces a real `IGNORED` record.
- The integration test asserts 7 terminal events (6 executed + 1 ignored) and unique `executionId` values for attempts.
- Running `reporter-json` tests without `-am` resolves `core` from `~/.m2`, which silently validates against a **stale** jar and can fake a regression. Use `mvn -pl reporter-json -am test`.

## Conventions

- TestNG is a `provided` dependency; do not add it as compile/runtime scope.
- Keep reporter backend dependencies out of `core`.
- Use `System.Logger` rather than adding a logging framework.
- Preserve the existing package boundaries: public model/SPI in `model` and `spi`, implementation details in `internal`.
