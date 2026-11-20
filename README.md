# 📡 TestNG Live Reporter

> **Live execution traceability and real-time observability for TestNG.**

[![CI](https://github.com/prmiguel/testng-live-reporter/actions/workflows/ci.yml/badge.svg)](https://github.com/prmiguel/testng-live-reporter/actions/workflows/ci.yml)
[![JitPack](https://jitpack.io/v/prmiguel/testng-live-reporter.svg)](https://jitpack.io/#prmiguel/testng-live-reporter)
[![Java 21](https://img.shields.io/badge/JDK-21%2B-blue.svg)](https://openjdk.org/projects/jdk/21/)
[![TestNG](https://img.shields.io/badge/TestNG-7.10%20%7C%207.11-brightgreen.svg)](https://testng.org)
[![License](https://img.shields.io/badge/License-Apache_2.0-orange.svg)](LICENSE)

When a TestNG suite runs—especially with parallel execution, retries, and data providers—most teams are left waiting until all tests finish before getting actionable insights.

**TestNG Live Reporter** attaches to TestNG's lifecycle to emit a deterministic, thread-safe, and non-blocking stream of test events in real time. Inspect what is running right now, tail progress live, or feed test data into local dashboards and AI agents.

---

## ✨ Features

- ⚡ **Real-Time Streaming** — Appends newline-delimited JSON (`.ndjson`) immediately as events happen; watch tests live with `tail -f`.
- 📊 **Consolidated Summary** — Generates a structured `.json` document with totals and detailed attempt records when execution ends.
- 🧵 **Thread-Safe & Non-Blocking** — Event bus dispatches asynchronously to dedicated reporter threads; worker test threads are never blocked.
- 🆔 **Deterministic Identity** — Canonical test identities with stable 16-hex SHA-256 hashes and unique execution IDs for data providers, retries, and parallel invocations.
- 🔌 **Zero-Code SPI Discovery** — Integrates seamlessly via Java `ServiceLoader`. Drop the JARs on the classpath and reporting begins automatically.
- 🤖 **AI-Agent Ready** — Machine-readable, deterministic output designed for instant status queries by coding assistants and CI bots.

---

## 📁 Repository Structure

```
testng-live-reporter/
├── pom.xml                                      # Parent POM: ${revision} SemVer & plugin management
│
├── core/                                        # artifactId: testng-live-reporter-core
│   └── src/main/java/.../live/core/
│       ├── LiveReportingListener.java           # TestNG lifecycle listener entry point
│       ├── ExecutionEventBus.java               # Asynchronous, thread-safe reporter dispatch
│       ├── TestRunIdGenerator.java              # Canonical identity & SHA-256 generator
│       ├── model/                               # Immutable event models (State, Status, Identity)
│       └── spi/ExecutionReporter.java           # Pluggable reporter SPI
│
├── reporter-json/                               # artifactId: testng-live-reporter-json
│   └── src/main/java/.../live/json/
│       ├── JsonExecutionReporter.java          # Real-time NDJSON streamer & JSON assembler
│       └── JsonReporterConfiguration.java      # Property, env var & file configuration
│
├── docs/                                        # Detailed guides & architecture specifications
│   ├── architecture.md                          # Event model, state transitions & identity hashing
│   ├── configuration.md                         # Full system properties & environment variables reference
│   ├── integration-guide.md                     # Maven, Gradle, CLI & programmatic usage
│   ├── consumer-usage.md                        # Linear walkthrough cookbook & consumer usage
│   ├── agent-skill-sample.md                    # Pre-built AI Agent Skill & progress reporting
│   ├── jitpack-usage.md                         # JitPack classpath injection guide
│   ├── testng-listeners.md                      # Comprehensive guide to all 17 TestNG listeners
│   └── ci-and-workflows.md                      # CI pipeline, dogfooding & validator details
│
├── examples/
│   └── jitpack-consumer/                        # Runnable consumer validating zero-POM jar injection
│
└── .github/workflows/                           # CI, compatibility matrix, CodeQL & dependency review
```

### 🧩 Module Status

| Module | Artifact | Status | Description |
|---|---|---|---|
| `core` | `testng-live-reporter-core` | ✅ **Active** | Listener engine, event bus, identity hashing, and SPI. |
| `reporter-json` | `testng-live-reporter-json` | ✅ **Active** | Append-only NDJSON stream and consolidated JSON document. |
| `reporter-sqlite` | `testng-live-reporter-sqlite` | ⏳ *Planned* | Queryable local SQLite test history. |
| `reporter-cache-db` | `testng-live-reporter-cache-db` | ⏳ *Planned* | Embedded cache (H2 / MapDB) for live queries. |
| `reporter-ctrf` | `testng-live-reporter-ctrf` | ⏳ *Planned* | Common Test Report Format output. |

---

## 🚀 Quick Start

### 1. Add Dependencies

Add the core listener and the JSON reporter to your `pom.xml`:

```xml
<dependencies>
    <dependency>
        <groupId>io.github.prmiguel</groupId>
        <artifactId>testng-live-reporter-core</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>io.github.prmiguel</groupId>
        <artifactId>testng-live-reporter-json</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

*(Or use on-demand JitPack artifacts: `com.github.prmiguel.testng-live-reporter` — see [JitPack Usage](docs/jitpack-usage.md)).*

### 2. Run Tests

Run your test suite normally:

```bash
mvn test
```

Because `core` and `reporter-json` register via Java SPI (`META-INF/services`), **no XML or code edits are required**.

### 3. Inspect Live Output

Output files are written to `target/testng-live-reporter/`:

- `<timestamp>-<runId>.ndjson` — Watch live events while tests execute:
  ```bash
  tail -f target/testng-live-reporter/*.ndjson
  ```
- `<timestamp>-<runId>.json` — Read the consolidated report when tests finish.

---

## 🏷️ Versioning & Compatibility

- **Project Versioning**: Uses unified **Semantic Versioning** (`MAJOR.MINOR.PATCH`) across all modules via Maven `${revision}` and `flatten-maven-plugin`.
  - **Patch (`1.0.x`)**: Bug fixes, performance improvements; no API or schema changes.
  - **Minor (`1.x.0`)**: Backwards-compatible additions and features.
  - **Major (`x.0.0`)**: Breaking API or schema changes.
- **Event Schema Versioning**: Event payloads include independent `schemaVersion` attributes (currently **v2**). Schema versions change only when breaking payload structure modifications occur.
- **Java Baseline**: Requires **JDK 21** or newer.
- **TestNG Compatibility**: Validated against **TestNG 7.10.x** and **7.11.x**.

---

## ⚙️ Configuration

Live reporting works out of the box with zero setup. You can customize output directories, filtering, and formats via Java system properties, environment variables, or properties files:

```bash
mvn test \
  -Dtestng.live.json.output-dir="custom-reports" \
  -Dtestng.live.json.include-statuses=FAILED,PASSED \
  -Dtestng.live.json.flush-each-event=true
```

👉 See the [Configuration Reference](docs/configuration.md) for the complete list of options.

---

## 📚 Documentation

Detailed documentation is available in the [`docs/`](docs/) directory:

- 🏗️ **[Architecture & Event Model](docs/architecture.md)** — Deep dive into lifecycle states, statuses, thread-safety, and canonical identity hashing.
- ⚙️ **[Configuration Reference](docs/configuration.md)** — Complete list of properties, environment variables, filtering, and precedence.
- 🔌 **[Integration & Usage Guide](docs/integration-guide.md)** — Maven, TestNG XML, programmatic setup, and zero-POM classpath injection.
- 🧪 **[Consumer Usage Guide](docs/consumer-usage.md)** — Step-by-step tutorial with copy-paste instructions for adding live reporting to an existing project.
- 🤖 **[AI Agent Skill Guide](docs/agent-skill-sample.md)** — Pre-built Agent Skill with emoji progress reporting, failure root-cause analysis, and multi-run matrix analytics using jq (with optional DuckDB SQL examples).
- 📦 **[JitPack Usage Guide](docs/jitpack-usage.md)** — Consuming releases and commit snapshots via JitPack.
- 👂 **[TestNG Listeners Guide](docs/testng-listeners.md)** — Breakdown of all 17 TestNG listener interfaces and common pitfalls.
- 🔄 **[CI & Workflows](docs/ci-and-workflows.md)** — Overview of automated pipelines, dogfood verification, and report validation.

---

## 🗺️ Roadmap

- [x] **Milestone 1**: Core listener, non-blocking event bus, canonical identity generator, and `reporter-json`.
- [x] **Milestone 2**: CI pipeline, dogfood validation script, and JitPack consumer integration.
- [ ] **Milestone 3**: `reporter-sqlite` — Persistent local execution database.
- [ ] **Milestone 4**: `reporter-cache-db` — Embedded in-memory / cache storage for instant querying.
- [ ] **Milestone 5**: `reporter-ctrf` — Common Test Report Format compatibility.

---

## 📄 License

Licensed under the [Apache License, Version 2.0](LICENSE).
