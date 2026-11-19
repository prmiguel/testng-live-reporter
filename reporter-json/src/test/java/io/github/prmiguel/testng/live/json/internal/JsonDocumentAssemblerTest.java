package io.github.prmiguel.testng.live.json.internal;

import io.github.prmiguel.testng.live.json.TestEvents;
import io.github.prmiguel.testng.live.json.TestJsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonDocumentAssemblerTest {

    @TempDir
    Path directory;

    private static JsonDocumentAssembler assembler(AttemptIndex index) {
        return new JsonDocumentAssembler(index, 0);
    }

    private Map<String, Object> readDocument(Path document) throws IOException {
        return TestJsonParser.parseObject(Files.readString(document, StandardCharsets.UTF_8));
    }

    @Test
    void splicesHeaderEventsAndSummary() throws IOException {
        Path ndjson = directory.resolve("run.ndjson");
        Path document = directory.resolve("run.json");
        Files.writeString(ndjson, String.join("\n",
                "{\"type\":\"run.started\",\"schemaVersion\":2,\"runId\":\"r1\",\"startedAt\":\"2026-11-19T10:00:00.000Z\",\"reporter\":{\"name\":\"n\",\"version\":\"v\"}}",
                "{\"type\":\"test.event\",\"sequence\":1,\"state\":\"STARTED\"}",
                "{\"type\":\"test.event\",\"sequence\":2,\"state\":\"FINISHED\"}",
                "{\"type\":\"run.summary\",\"schemaVersion\":2,\"runId\":\"r1\"}"), StandardCharsets.UTF_8);

        assembler(new AttemptIndex()).assemble(ndjson, document, false, true);

        Map<String, Object> parsed = readDocument(document);
        assertEquals(2L, parsed.get("schemaVersion"));
        assertEquals("r1", ((Map<?, ?>) parsed.get("run")).get("runId"));
        assertEquals(2, ((List<?>) parsed.get("events")).size());
        assertNotNull(parsed.get("summary"));
        assertNotNull(parsed.get("tests"));
        assertNotNull(parsed.get("configurationFailures"));
    }

    @Test
    void omitsEventsWhenTheStreamIsNotEmbedded() throws IOException {
        Path ndjson = directory.resolve("compact.ndjson");
        Path document = directory.resolve("compact.json");
        Files.writeString(ndjson, String.join("\n",
                "{\"type\":\"run.started\",\"runId\":\"r1\"}",
                "{\"type\":\"test.event\",\"sequence\":1,\"state\":\"FINISHED\"}"), StandardCharsets.UTF_8);

        assembler(new AttemptIndex()).assemble(ndjson, document, false, false);

        Map<String, Object> parsed = readDocument(document);
        assertNull(parsed.get("events"), "document-events=none must not embed the stream");
        assertNotNull(parsed.get("tests"));
        assertEquals("r1", ((Map<?, ?>) parsed.get("run")).get("runId"));
    }

    @Test
    void buildsOneEntryPerFinishedAttempt() throws IOException {
        AttemptIndex index = new AttemptIndex();
        index.accept(TestEvents.started("r1", "exec-1", TestEvents.identity("com.acme.Sample", "runs")));
        index.accept(TestEvents.inProgress("r1", "exec-1", TestEvents.identity("com.acme.Sample", "runs")));
        index.accept(TestEvents.finished("r1", "exec-1", TestEvents.identity("com.acme.Sample", "runs"),
                io.github.prmiguel.testng.live.core.model.ExecutionStatus.PASSED, 12));

        Path document = directory.resolve("tests.json");
        assembler(index).assemble(directory.resolve("missing.ndjson"), document, false, false);

        List<?> tests = (List<?>) readDocument(document).get("tests");
        assertEquals(1, tests.size(), "three lifecycle records collapse into one entry");
        Map<?, ?> entry = (Map<?, ?>) tests.get(0);
        assertEquals("PASSED", entry.get("status"));
        assertEquals(12L, entry.get("durationMs"));
        assertNull(entry.get("failure"), "a passing attempt has no failure");
        assertEquals("com.acme.Sample.runs[]", entry.get("readableKey"));
    }

    @Test
    void keepsRetriesAsSeparateEntries() throws IOException {
        AttemptIndex index = new AttemptIndex();
        index.accept(TestEvents.started("r1", "exec-1", TestEvents.identity("com.acme.Sample", "runs")));
        index.accept(TestEvents.finished("r1", "exec-1", TestEvents.identity("com.acme.Sample", "runs"),
                io.github.prmiguel.testng.live.core.model.ExecutionStatus.FAILED, 5));
        index.accept(TestEvents.started("r1", "exec-2", TestEvents.identity("com.acme.Sample", "runs")));
        index.accept(TestEvents.finished("r1", "exec-2", TestEvents.identity("com.acme.Sample", "runs"),
                io.github.prmiguel.testng.live.core.model.ExecutionStatus.PASSED, 6));

        Path document = directory.resolve("retries.json");
        assembler(index).assemble(directory.resolve("missing.ndjson"), document, false, false);

        List<?> tests = (List<?>) readDocument(document).get("tests");
        assertEquals(2, tests.size(), "each attempt is its own entry, keyed by execution id");
        assertEquals("FAILED", ((Map<?, ?>) tests.get(0)).get("status"));
        assertEquals("PASSED", ((Map<?, ?>) tests.get(1)).get("status"));
    }

    @Test
    void separatesConfigurationFailuresFromTests() throws IOException {
        AttemptIndex index = new AttemptIndex();
        index.accept(TestEvents.configurationFailure("r1", "config-1",
                TestEvents.identity("com.acme.Sample", "brokenSetup"), "beforeMethod"));
        index.accept(TestEvents.finished("r1", "exec-1", TestEvents.identity("com.acme.Sample", "runs"),
                io.github.prmiguel.testng.live.core.model.ExecutionStatus.SKIPPED, 1));

        Path document = directory.resolve("config.json");
        assembler(index).assemble(directory.resolve("missing.ndjson"), document, false, false);

        Map<String, Object> parsed = readDocument(document);
        assertEquals(1, ((List<?>) parsed.get("tests")).size());
        List<?> failures = (List<?>) parsed.get("configurationFailures");
        assertEquals(1, failures.size());
        assertEquals("beforeMethod", ((Map<?, ?>) failures.get(0)).get("configurationKind"));
        assertNotNull(((Map<?, ?>) failures.get(0)).get("failure"));
    }

    @Test
    void sortsEventsBySequence() throws IOException {
        Path ndjson = directory.resolve("unordered.ndjson");
        Path document = directory.resolve("unordered.json");
        Files.writeString(ndjson, String.join("\n",
                "{\"type\":\"run.started\",\"runId\":\"r1\"}",
                "{\"type\":\"test.event\",\"sequence\":7,\"state\":\"FINISHED\"}",
                "{\"type\":\"test.event\",\"sequence\":2,\"state\":\"STARTED\"}",
                "{\"type\":\"test.event\",\"sequence\":5,\"state\":\"IN_PROGRESS\"}"), StandardCharsets.UTF_8);

        assembler(new AttemptIndex()).assemble(ndjson, document, true, true);

        List<?> events = (List<?>) readDocument(document).get("events");
        assertEquals(2L, ((Map<?, ?>) events.get(0)).get("sequence"));
        assertEquals(5L, ((Map<?, ?>) events.get(1)).get("sequence"));
        assertEquals(7L, ((Map<?, ?>) events.get(2)).get("sequence"));
    }

    @Test
    void keepsInsertionOrderWhenSortingIsDisabled() throws IOException {
        Path ndjson = directory.resolve("order.ndjson");
        Path document = directory.resolve("order.json");
        Files.writeString(ndjson, String.join("\n",
                "{\"type\":\"test.event\",\"sequence\":7}",
                "{\"type\":\"test.event\",\"sequence\":2}"), StandardCharsets.UTF_8);

        assembler(new AttemptIndex()).assemble(ndjson, document, false, true);

        List<?> events = (List<?>) readDocument(document).get("events");
        assertEquals(7L, ((Map<?, ?>) events.get(0)).get("sequence"));
    }

    @Test
    void producesAValidDocumentFromAFlushOnlyStream() throws IOException {
        Path ndjson = directory.resolve("crash.ndjson");
        Path document = directory.resolve("crash.json");
        Files.writeString(ndjson, String.join("\n",
                "{\"type\":\"run.started\",\"runId\":\"r1\"}",
                "{\"type\":\"test.event\",\"sequence\":1,\"state\":\"STARTED\"}"), StandardCharsets.UTF_8);

        assembler(new AttemptIndex()).assemble(ndjson, document, false, true);

        Map<String, Object> parsed = readDocument(document);
        assertEquals(1, ((List<?>) parsed.get("events")).size());
        assertNull(parsed.get("summary"), "no summary was written");
    }

    @Test
    void handlesACloselyEmptyStream() throws IOException {
        Path document = directory.resolve("empty.json");

        assembler(new AttemptIndex()).assemble(directory.resolve("missing.ndjson"), document, false, true);

        Map<String, Object> parsed = readDocument(document);
        assertEquals(0, ((List<?>) parsed.get("events")).size());
        assertEquals(0, ((List<?>) parsed.get("tests")).size());
    }

    @Test
    void writesAValidDocumentWhenTheStreamOnlyHoldsTheSummary() throws IOException {
        Path ndjson = directory.resolve("summary-only.ndjson");
        Path document = directory.resolve("summary-only.json");
        Files.writeString(ndjson,
                "{\"type\":\"run.summary\",\"schemaVersion\":2,\"runId\":\"r1\"}\n", StandardCharsets.UTF_8);

        assembler(new AttemptIndex()).assemble(ndjson, document, false, false);

        Map<String, Object> parsed = readDocument(document);
        assertNull(parsed.get("events"));
        assertNotNull(parsed.get("summary"));
        assertTrue(((List<?>) parsed.get("tests")).isEmpty());
    }
}
