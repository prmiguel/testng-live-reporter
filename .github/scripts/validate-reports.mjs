#!/usr/bin/env node
// Validates the reports emitted by reporter-json during CI dogfooding.
//
// Usage: node .github/scripts/validate-reports.mjs <output-dir>
//
// Exits non-zero listing every violation found. Kept dependency-free so it runs on a
// stock runner without an install step.

import { readdirSync, readFileSync, existsSync, statSync } from "node:fs";
import { join } from "node:path";

const failures = [];
const fail = (message) => failures.push(message);

const directory = process.argv[2];
if (!directory) {
  console.error("usage: node .github/scripts/validate-reports.mjs <output-dir>");
  process.exit(2);
}
if (!existsSync(directory) || !statSync(directory).isDirectory()) {
  console.error(`::error::report directory does not exist: ${directory}`);
  process.exit(1);
}

const files = readdirSync(directory);
const ndjsonFiles = files.filter((name) => name.endsWith(".ndjson"));
const documentFiles = files.filter((name) => name.endsWith(".json"));

if (ndjsonFiles.length === 0) {
  fail(`no .ndjson report found in ${directory}`);
}
if (documentFiles.length === 0) {
  fail(`no .json document found in ${directory}`);
}

for (const name of ndjsonFiles) {
  validateStream(join(directory, name), name.replace(/\.ndjson$/, ".json"));
}

for (const name of documentFiles) {
  validateDocument(join(directory, name), name);
}

validateSchemaArtifact();

function validateStream(path, documentName) {
  const raw = readFileSync(path, "utf8");
  if (!raw.endsWith("\n")) {
    fail(`${path}: stream does not end with a newline (a torn final record?)`);
  }

  const lines = raw.split("\n").filter((line) => line.length > 0);
  const records = [];
  for (const [index, line] of lines.entries()) {
    try {
      records.push(JSON.parse(line));
    } catch (error) {
      fail(`${path}:${index + 1}: not valid JSON (${error.message})`);
    }
  }

  const [first] = records;
  const last = records[records.length - 1];
  if (!first || first.type !== "run.started") {
    fail(`${path}: first record must be run.started, got ${first?.type}`);
  }
  if (!last || last.type !== "run.summary") {
    fail(`${path}: last record must be run.summary, got ${last?.type}`);
  }

  const headers = records.filter((record) => record.type === "run.started");
  const summaries = records.filter((record) => record.type === "run.summary");
  if (headers.length !== 1) fail(`${path}: expected 1 run.started, found ${headers.length}`);
  if (summaries.length !== 1) fail(`${path}: expected 1 run.summary, found ${summaries.length}`);

  const events = records.filter((record) => record.type === "test.event");
  if (events.length === 0) fail(`${path}: no test.event records`);
  if (records.length !== events.length + 2) {
    fail(`${path}: expected ${events.length + 2} records (header + events + summary), found ${records.length}`);
  }

  const seen = new Set();
  for (const event of events) {
    for (const field of ["executionId", "state", "status", "timestamp", "test", "suiteName", "testName"]) {
      if (event[field] === undefined) fail(`${path}: event ${event.executionId ?? "?"} is missing ${field}`);
    }
    if (!event.test || typeof event.test !== "object") {
      fail(`${path}: event ${event.executionId ?? "?"} has no test identity`);
    } else {
      if (!event.test.testId) fail(`${path}: event ${event.executionId ?? "?"} has no test.testId`);
      if (!/^[0-9a-f]{16}$/.test(event.test.hash ?? "")) {
        fail(`${path}: event ${event.executionId ?? "?"} has an unexpected test.hash`);
      }
      if (!Array.isArray(event.test.parameters)) {
        fail(`${path}: event ${event.executionId ?? "?"} has no test.parameters array`);
      }
    }
    if ("FINISHED" === event.state && typeof event.durationMs !== "number") {
      fail(`${path}: FINISHED event ${event.executionId} is missing durationMs`);
    }
    if ("FINISHED" !== event.state && event.durationMs !== undefined) {
      fail(`${path}: non-FINISHED event ${event.executionId} must not carry durationMs`);
    }

    const key = `${event.executionId}/${event.state}`;
    if (seen.has(key)) fail(`${path}: duplicate record for attempt ${key}`);
    seen.add(key);
  }

  if (last && last.totals) {
    if (last.totals.events !== events.length) {
      fail(`${path}: totals.events ${last.totals.events} != emitted ${events.length}`);
    }
    const terminals = events.filter((event) => event.state === "FINISHED").length;
    if (last.totals.tests !== terminals) {
      fail(`${path}: totals.tests ${last.totals.tests} != FINISHED events ${terminals}`);
    }
    if (last.totals.writeErrors !== 0) {
      fail(`${path}: totals.writeErrors is ${last.totals.writeErrors}, expected 0`);
    }
    const statuses = sumCounts(events);
    for (const status of ["passed", "failed", "skipped", "timeout"]) {
      if (last.totals[status] !== statuses[status]) {
        fail(`${path}: totals.${status} ${last.totals[status]} != counted ${statuses[status]}`);
      }
    }
  } else {
    fail(`${path}: summary has no totals`);
  }

  const documentPath = join(directory, documentName);
  if (!existsSync(documentPath)) {
    fail(`${path}: matching document ${documentName} was not written`);
    return;
  }
  const document = parseObject(documentPath);
  if (!document) return;
  if (document.schemaVersion !== 1) fail(`${documentPath}: schemaVersion is ${document.schemaVersion}`);
  if (!Array.isArray(document.events)) {
    fail(`${documentPath}: events is not an array`);
    return;
  }
  if (document.events.length !== events.length) {
    fail(`${documentPath}: holds ${document.events.length} events but the stream has ${events.length}`);
  }
  for (const [index, event] of document.events.entries()) {
    const streamed = events[index];
    if (streamed && event.sequence !== streamed.sequence) {
      fail(`${documentPath}: event ${index} sequence ${event.sequence} != streamed ${streamed.sequence}`);
    }
  }
  if (!document.run || !document.run.runId) fail(`${documentPath}: missing run.runId`);
  if (!document.summary || !document.summary.totals) fail(`${documentPath}: missing summary.totals`);
}

function validateDocument(path, name) {
  const document = parseObject(path);
  if (!document) return;
  if (!document.run) fail(`${path}: missing run section`);
  if (!document.summary) fail(`${path}: missing summary section`);
  if (!Array.isArray(document.events)) fail(`${path}: missing events array`);
}

function validateSchemaArtifact() {
  const schemaPath = "reporter-json/src/main/resources/schema/testng-live-report-v1.schema.json";
  if (!existsSync(schemaPath)) {
    fail(`${schemaPath}: shipped schema is missing`);
    return;
  }
  const schema = parseObject(schemaPath);
  if (schema && !schema.definitions) fail(`${schemaPath}: has no definitions`);
}

function sumCounts(events) {
  const counts = { passed: 0, failed: 0, skipped: 0, timeout: 0 };
  const mapping = { PASSED: "passed", FAILED: "failed", SKIPPED: "skipped", TIMEOUT: "timeout" };
  for (const event of events) {
    const key = mapping[event.status];
    if (key) counts[key]++;
  }
  return counts;
}

function parseObject(path) {
  try {
    const value = JSON.parse(readFileSync(path, "utf8"));
    if (typeof value !== "object" || value === null) {
      fail(`${path}: not a JSON object`);
      return null;
    }
    return value;
  } catch (error) {
    fail(`${path}: not valid JSON (${error.message})`);
    return null;
  }
}

if (failures.length > 0) {
  for (const failure of failures) {
    console.error(`::error::${failure}`);
  }
  console.error(`\n${failures.length} report violation(s) found`);
  process.exit(1);
}

console.log(`reports in ${directory} are valid`);
