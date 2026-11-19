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
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonExecutionReporterDedupeTest {

    @TempDir
    Path outputDirectory;

    @Test
    void keepsOneRecordPerExecutionStatePair() throws IOException {
        JsonExecutionReporter reporter = new JsonExecutionReporter(JsonReporterConfiguration.from(Map.of(
                JsonReporterConfiguration.OUTPUT_DIR_PROPERTY, outputDirectory.toString(),
                JsonReporterConfiguration.FILE_NAME_PROPERTY, "dedupe")));
        TestIdentity identity = TestEvents.identity("com.acme.Sample", "runs");

        reporter.onEvent(TestEvents.started("run-1", "exec-1", identity));
        reporter.onEvent(TestEvents.started("run-1", "exec-1", identity));
        reporter.onEvent(TestEvents.inProgress("run-1", "exec-1", identity));
        reporter.onEvent(TestEvents.finished("run-1", "exec-1", identity, ExecutionStatus.PASSED, 5));
        reporter.onEvent(TestEvents.finished("run-1", "exec-1", identity, ExecutionStatus.PASSED, 5));
        reporter.close();

        List<String> lines = Files.readAllLines(reporter.ndjsonPath(), StandardCharsets.UTF_8);
        assertEquals(5, lines.size(), "header, three distinct events, summary");

        Map<?, ?> summary = (Map<?, ?>) TestJsonParser.parseObject(lines.get(lines.size() - 1)).get("totals");
        assertEquals(2L, summary.get("droppedDuplicates"), "one duplicate STARTED and one duplicate FINISHED");
        assertEquals(3L, summary.get("events"));
        assertEquals(1L, summary.get("tests"));
        assertEquals(1L, summary.get("passed"));
    }

    @Test
    void countsFilteredEventsInsteadOfDroppingThemSilently() throws IOException {
        JsonExecutionReporter reporter = new JsonExecutionReporter(JsonReporterConfiguration.from(Map.of(
                JsonReporterConfiguration.OUTPUT_DIR_PROPERTY, outputDirectory.toString(),
                JsonReporterConfiguration.FILE_NAME_PROPERTY, "filtered",
                JsonReporterConfiguration.EXCLUDE_CLASSES_PROPERTY, "com.acme.Ignored*")));

        reporter.onEvent(TestEvents.started("run-1", "exec-1", TestEvents.identity("com.acme.IgnoredTests", "runs")));
        reporter.onEvent(TestEvents.started("run-1", "exec-2", TestEvents.identity("com.acme.Sample", "runs")));
        reporter.close();

        List<String> lines = Files.readAllLines(reporter.ndjsonPath(), StandardCharsets.UTF_8);
        Map<?, ?> summary = (Map<?, ?>) TestJsonParser.parseObject(lines.get(lines.size() - 1)).get("totals");
        assertEquals(1L, summary.get("filtered"));
        assertEquals(1L, summary.get("events"));
        assertTrue(lines.stream().noneMatch(line -> line.contains("IgnoredTests")));
    }
}
