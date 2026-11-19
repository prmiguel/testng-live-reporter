package io.github.prmiguel.testng.live.json.internal;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonDocumentAssemblerTest {

    @TempDir
    Path directory;

    @Test
    void splicesHeaderEventsAndSummary() throws IOException {
        Path ndjson = directory.resolve("run.ndjson");
        Path document = directory.resolve("run.json");
        Files.writeString(ndjson, String.join("\n",
                "{\"type\":\"run.started\",\"schemaVersion\":1,\"runId\":\"r1\",\"startedAt\":\"2026-11-19T10:00:00.000Z\",\"reporter\":{\"name\":\"n\",\"version\":\"v\"}}",
                "{\"type\":\"test.event\",\"sequence\":1,\"state\":\"STARTED\"}",
                "{\"type\":\"test.event\",\"sequence\":2,\"state\":\"FINISHED\"}",
                "{\"type\":\"run.summary\",\"schemaVersion\":1,\"runId\":\"r1\"}"), StandardCharsets.UTF_8);

        JsonDocumentAssembler.assemble(ndjson, document, false);

        Map<String, Object> parsed = TestJsonParser.parseObject(Files.readString(document, StandardCharsets.UTF_8));
        assertEquals(2L, parsed.get("schemaVersion"));
        assertEquals("r1", ((Map<?, ?>) parsed.get("run")).get("runId"));
        assertEquals(2, ((List<?>) parsed.get("events")).size());
        assertNotNull(parsed.get("summary"));
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

        JsonDocumentAssembler.assemble(ndjson, document, true);

        List<?> events = (List<?>) TestJsonParser.parseObject(
                Files.readString(document, StandardCharsets.UTF_8)).get("events");
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

        JsonDocumentAssembler.assemble(ndjson, document, false);

        List<?> events = (List<?>) TestJsonParser.parseObject(
                Files.readString(document, StandardCharsets.UTF_8)).get("events");
        assertEquals(7L, ((Map<?, ?>) events.get(0)).get("sequence"));
    }

    @Test
    void producesAValidDocumentFromAFiushOnlyStream() throws IOException {
        Path ndjson = directory.resolve("crash.ndjson");
        Path document = directory.resolve("crash.json");
        Files.writeString(ndjson, String.join("\n",
                "{\"type\":\"run.started\",\"runId\":\"r1\"}",
                "{\"type\":\"test.event\",\"sequence\":1,\"state\":\"STARTED\"}"), StandardCharsets.UTF_8);

        JsonDocumentAssembler.assemble(ndjson, document, false);

        Map<String, Object> parsed = TestJsonParser.parseObject(Files.readString(document, StandardCharsets.UTF_8));
        assertEquals(1, ((List<?>) parsed.get("events")).size());
        assertTrue(parsed.get("summary") == null, "no summary was written");
    }

    @Test
    void handlesACloselyEmptyStream() throws IOException {
        Path document = directory.resolve("empty.json");

        JsonDocumentAssembler.assemble(directory.resolve("missing.ndjson"), document, false);

        Map<String, Object> parsed = TestJsonParser.parseObject(Files.readString(document, StandardCharsets.UTF_8));
        assertEquals(0, ((List<?>) parsed.get("events")).size());
    }

    @Test
    void writesAValidDocumentWhenTheStreamOnlyHoldsTheSummary() throws IOException {
        Path ndjson = directory.resolve("summary-only.ndjson");
        Path document = directory.resolve("summary-only.json");
        Files.writeString(ndjson,
                "{\"type\":\"run.summary\",\"schemaVersion\":1,\"runId\":\"r1\"}\n", StandardCharsets.UTF_8);

        JsonDocumentAssembler.assemble(ndjson, document, false);

        Map<String, Object> parsed = TestJsonParser.parseObject(Files.readString(document, StandardCharsets.UTF_8));
        assertEquals(0, ((List<?>) parsed.get("events")).size());
        assertNotNull(parsed.get("summary"));
    }
}
