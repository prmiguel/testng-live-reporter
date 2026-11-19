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

## Module layout

- Maven multi-module project; only `core` exists today.
- `core` artifact: `io.github.prmiguel:testng-live-reporter-core`.
- Reporter modules (`reporter-json`, etc.) are planned but not implemented yet.
- `README.md` still documents some planned work; treat the code as the source of truth.

## Architecture notes

- TestNG listener entry point: `io.github.prmiguel.testng.live.core.LiveReportingListener`.
- Reporter plugins are loaded with `ServiceLoader` from `META-INF/services/io.github.prmiguel.testng.live.core.spi.ExecutionReporter`.
- The listener itself is registered through `META-INF/services/org.testng.ITestNGListener`.
- Event dispatch is asynchronous and one-thread-per-reporter; callers should never block on reporters.
- `ExecutionEvent` instances are immutable; only `ExecutionAttempt` is mutable internally.
- Test identity hashes are the first 16 lowercase hex characters of SHA-256.

## Testing quirks

- The single integration test launches TestNG programmatically from JUnit 5.
- `SampleTests.timeoutTest` uses a 100 ms TestNG timeout and sleeps 5 seconds; expect TestNG failures and a roughly 5-second run.
- The integration test asserts exactly 6 terminal events and unique `executionId` values.

## Conventions

- TestNG is a `provided` dependency; do not add it as compile/runtime scope.
- Keep reporter backend dependencies out of `core`.
- Use `System.Logger` rather than adding a logging framework.
- Preserve the existing package boundaries: public model/SPI in `model` and `spi`, implementation details in `internal`.
