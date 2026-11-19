package io.github.prmiguel.testng.live.json;

import io.github.prmiguel.testng.live.core.model.ExecutionStatus;
import io.github.prmiguel.testng.live.core.model.TestIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonExecutionReporterLifecycleTest {

    @TempDir
    Path outputDirectory;

    @Test
    void writesNothingWhenDisabled() {
        JsonExecutionReporter reporter = reporter(Map.of(
                JsonReporterConfiguration.ENABLED_PROPERTY, "false",
                JsonReporterConfiguration.FILE_NAME_PROPERTY, "disabled"));

        reporter.onEvent(TestEvents.started("run-1", "exec-1", identity("a.B", "c")));
        reporter.flush();
        reporter.close();

        assertNull(reporter.ndjsonPath());
        assertTrue(streamedFiles().isEmpty(), "nothing may be written when disabled");
    }

    @Test
    void opensFilesLazilyOnTheFirstEvent() {
        JsonExecutionReporter reporter = reporter(Map.of(JsonReporterConfiguration.FILE_NAME_PROPERTY, "lazy"));

        assertNull(reporter.ndjsonPath());

        reporter.onEvent(TestEvents.started("run-1", "exec-1", identity("a.B", "c")));
        reporter.close();

        assertEquals(outputDirectory.resolve("lazy.ndjson"), reporter.ndjsonPath());
        assertEquals(outputDirectory.resolve("lazy.json"), reporter.documentPath());
        assertTrue(Files.exists(reporter.ndjsonPath()));
        assertTrue(Files.exists(reporter.documentPath()));
    }

    @Test
    void writesBothFilesForOneExecution() throws IOException {
        JsonExecutionReporter reporter = reporter(Map.of(JsonReporterConfiguration.FILE_NAME_PROPERTY, "run"));
        TestIdentity identity = identity("com.acme.Sample", "runs");

        reporter.onEvent(TestEvents.started("run-1", "exec-1", identity));
        reporter.onEvent(TestEvents.finished("run-1", "exec-1", identity, ExecutionStatus.PASSED, 12));
        reporter.close();

        List<String> lines = Files.readAllLines(reporter.ndjsonPath(), StandardCharsets.UTF_8);
        assertEquals(4, lines.size(), "header + two events + summary");
        assertEquals("run.started", TestJsonParser.parseObject(lines.get(0)).get("type"));
        assertEquals("test.event", TestJsonParser.parseObject(lines.get(1)).get("type"));
        assertEquals("test.event", TestJsonParser.parseObject(lines.get(2)).get("type"));
        assertEquals("run.summary", TestJsonParser.parseObject(lines.get(3)).get("type"));

        Map<String, Object> document = TestJsonParser.parseObject(
                Files.readString(reporter.documentPath(), StandardCharsets.UTF_8));
        assertEquals(2, ((List<?>) document.get("events")).size());
        assertNotNull(document.get("run"));
        assertNotNull(document.get("summary"));
    }

    @Test
    void honoursCustomOutputDirectory() {
        Path nested = outputDirectory.resolve("nested/dir");
        JsonExecutionReporter reporter = reporter(Map.of(
                JsonReporterConfiguration.OUTPUT_DIR_PROPERTY, nested.toString(),
                JsonReporterConfiguration.FILE_NAME_PROPERTY, "custom"));

        reporter.onEvent(TestEvents.started("run-1", "exec-1", identity("a.B", "c")));
        reporter.close();

        assertTrue(Files.exists(nested.resolve("custom.ndjson")));
        assertTrue(Files.exists(nested.resolve("custom.json")));
    }

    @Test
    void defaultsFileNameToTimestampAndRunId() {
        JsonExecutionReporter reporter = reporter(Map.of());

        reporter.onEvent(TestEvents.started("run-abc", "exec-1", identity("a.B", "c")));
        reporter.close();

        String name = reporter.ndjsonPath().getFileName().toString();
        assertTrue(name.endsWith("-run-abc.ndjson"), name);
        assertTrue(name.matches("\\d{8}-\\d{6}-run-abc\\.ndjson"), name);
    }

    @Test
    void doesNotOverwriteExistingFilesByDefault() {
        JsonExecutionReporter first = reporter(Map.of(JsonReporterConfiguration.FILE_NAME_PROPERTY, "same"));
        first.onEvent(TestEvents.started("run-1", "exec-1", identity("a.B", "c")));
        first.close();

        JsonExecutionReporter second = reporter(Map.of(JsonReporterConfiguration.FILE_NAME_PROPERTY, "same"));
        second.onEvent(TestEvents.started("run-2", "exec-2", identity("a.B", "d")));
        second.close();

        assertEquals(outputDirectory.resolve("same.ndjson"), first.ndjsonPath());
        assertEquals(outputDirectory.resolve("same-1.ndjson"), second.ndjsonPath());
        assertEquals(outputDirectory.resolve("same-1.json"), second.documentPath());
        assertTrue(Files.exists(outputDirectory.resolve("same.ndjson")));
    }

    @Test
    void overwritesWhenAsked() throws IOException {
        JsonExecutionReporter first = reporter(Map.of(
                JsonReporterConfiguration.FILE_NAME_PROPERTY, "same",
                JsonReporterConfiguration.OVERWRITE_PROPERTY, "true"));
        first.onEvent(TestEvents.started("run-1", "exec-1", identity("a.B", "c")));
        first.close();

        JsonExecutionReporter second = reporter(Map.of(
                JsonReporterConfiguration.FILE_NAME_PROPERTY, "same",
                JsonReporterConfiguration.OVERWRITE_PROPERTY, "true"));
        second.onEvent(TestEvents.started("run-2", "exec-2", identity("a.B", "d")));
        second.close();

        assertEquals(outputDirectory.resolve("same.ndjson"), second.ndjsonPath());
        String content = Files.readString(second.ndjsonPath(), StandardCharsets.UTF_8);
        assertFalse(content.contains("exec-1"), "the second run must have replaced the first");
        assertTrue(content.contains("exec-2"));
    }

    @Test
    void writesOnlyTheLiveStreamWhenDocumentIsDisabled() {
        JsonExecutionReporter reporter = reporter(Map.of(
                JsonReporterConfiguration.FILE_NAME_PROPERTY, "stream-only",
                JsonReporterConfiguration.DOCUMENT_PROPERTY, "false"));

        reporter.onEvent(TestEvents.started("run-1", "exec-1", identity("a.B", "c")));
        reporter.close();

        assertTrue(Files.exists(reporter.ndjsonPath()));
        assertFalse(Files.exists(outputDirectory.resolve("stream-only.json")));
    }

    @Test
    void writesNoLiveStreamButIsUsableWhenNdjsonIsDisabled() {
        JsonExecutionReporter reporter = reporter(Map.of(
                JsonReporterConfiguration.FILE_NAME_PROPERTY, "doc-only",
                JsonReporterConfiguration.NDJSON_PROPERTY, "false"));

        reporter.onEvent(TestEvents.started("run-1", "exec-1", identity("a.B", "c")));
        reporter.close();

        assertNull(reporter.ndjsonPath());
        assertFalse(Files.exists(outputDirectory.resolve("doc-only.ndjson")));
    }

    @Test
    void twoReporterInstancesWriteSeparateExecutions() {
        JsonExecutionReporter first = reporter(Map.of());
        first.onEvent(TestEvents.started("run-first", "exec-1", identity("a.B", "c")));
        first.close();

        JsonExecutionReporter second = reporter(Map.of());
        second.onEvent(TestEvents.started("run-second", "exec-2", identity("a.B", "c")));
        second.close();

        assertTrue(Files.exists(first.ndjsonPath()));
        assertTrue(Files.exists(second.ndjsonPath()));
        assertFalse(first.ndjsonPath().equals(second.ndjsonPath()));
    }

    @Test
    void ignoresEventsFromAnotherRun() {
        JsonExecutionReporter reporter = reporter(Map.of(JsonReporterConfiguration.FILE_NAME_PROPERTY, "one-run"));

        reporter.onEvent(TestEvents.started("run-1", "exec-1", identity("a.B", "c")));
        reporter.onEvent(TestEvents.finished("run-2", "exec-2", identity("a.B", "d"), ExecutionStatus.PASSED, 1));
        reporter.close();

        assertEquals(outputDirectory.resolve("one-run.ndjson"), reporter.ndjsonPath());
        assertFalse(Files.exists(outputDirectory.resolve("one-run-1.ndjson")));
    }

    private JsonExecutionReporter reporter(Map<String, String> overrides) {
        java.util.Map<String, String> values = new java.util.LinkedHashMap<>(overrides);
        values.putIfAbsent(JsonReporterConfiguration.OUTPUT_DIR_PROPERTY, outputDirectory.toString());
        return new JsonExecutionReporter(JsonReporterConfiguration.from(values));
    }

    private static TestIdentity identity(String className, String methodName) {
        return TestEvents.identity(className, methodName);
    }

    private List<Path> streamedFiles() {
        try (var files = Files.list(outputDirectory)) {
            return files.toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
