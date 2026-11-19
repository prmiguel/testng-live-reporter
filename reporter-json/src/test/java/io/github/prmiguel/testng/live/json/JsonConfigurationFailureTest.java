package io.github.prmiguel.testng.live.json;

import io.github.prmiguel.testng.live.core.internal.ExecutionEventFactory;
import io.github.prmiguel.testng.live.core.model.FailureInfo;
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

class JsonConfigurationFailureTest {

    @TempDir
    Path outputDirectory;

    private io.github.prmiguel.testng.live.core.model.ExecutionEvent configurationFailure(String executionId) {
        return TestEvents.configurationFailure("run-1", executionId,
                TestEvents.identity("com.acme.Sample", "brokenSetup"), "beforeMethod");
    }

    @Test
    void countsConfigurationFailuresSeparatelyFromTests() throws IOException {
        JsonExecutionReporter reporter = new JsonExecutionReporter(JsonReporterConfiguration.from(Map.of(
                JsonReporterConfiguration.OUTPUT_DIR_PROPERTY, outputDirectory.toString(),
                JsonReporterConfiguration.FILE_NAME_PROPERTY, "config")));

        reporter.onEvent(TestEvents.started("run-1", "exec-1", TestEvents.identity("com.acme.Sample", "runs")));
        reporter.onEvent(TestEvents.finished("run-1", "exec-1", TestEvents.identity("com.acme.Sample", "runs"),
                io.github.prmiguel.testng.live.core.model.ExecutionStatus.SKIPPED, 3));
        reporter.onEvent(configurationFailure("config-1"));
        reporter.close();

        List<String> lines = Files.readAllLines(reporter.ndjsonPath(), StandardCharsets.UTF_8);
        Map<?, ?> totals = (Map<?, ?>) TestJsonParser.parseObject(lines.get(lines.size() - 1)).get("totals");

        assertEquals(1L, totals.get("configurationFailures"));
        assertEquals(1L, totals.get("tests"), "the config failure must not count as a test");
        assertEquals(1L, totals.get("skipped"));
        assertEquals(0L, totals.get("error"), "a config failure must not inflate the test error count");
        assertEquals(3L, totals.get("events"));
    }

    @Test
    void keepsPhaseAttributeWhenAttributesAreDisabled() throws IOException {
        JsonExecutionReporter reporter = new JsonExecutionReporter(JsonReporterConfiguration.from(Map.of(
                JsonReporterConfiguration.OUTPUT_DIR_PROPERTY, outputDirectory.toString(),
                JsonReporterConfiguration.FILE_NAME_PROPERTY, "no-attrs",
                JsonReporterConfiguration.INCLUDE_ATTRIBUTES_PROPERTY, "false")));

        reporter.onEvent(configurationFailure("config-1"));
        reporter.close();

        List<String> lines = Files.readAllLines(reporter.ndjsonPath(), StandardCharsets.UTF_8);
        Map<?, ?> event = TestJsonParser.parseObject(lines.get(1));
        Map<?, ?> attributes = (Map<?, ?>) event.get("attributes");
        assertNotNull(attributes, "the phase marker must survive include-attributes=false");
        assertEquals("configuration", attributes.get("phase"));
        assertEquals("beforeMethod", attributes.get("configurationKind"));
    }

    @Test
    void statusWhitelistCannotHideTheRootCause() throws IOException {
        JsonExecutionReporter reporter = new JsonExecutionReporter(JsonReporterConfiguration.from(Map.of(
                JsonReporterConfiguration.OUTPUT_DIR_PROPERTY, outputDirectory.toString(),
                JsonReporterConfiguration.FILE_NAME_PROPERTY, "whitelist",
                JsonReporterConfiguration.INCLUDE_STATUSES_PROPERTY, "FAILED")));

        reporter.onEvent(configurationFailure("config-1"));
        reporter.close();

        List<String> lines = Files.readAllLines(reporter.ndjsonPath(), StandardCharsets.UTF_8);
        assertEquals(3, lines.size(), "header, the config record, summary");
        assertTrue(lines.get(1).contains("\"status\":\"ERROR\""));
    }

    @Test
    void documentIncludesConfigurationRecords() throws IOException {
        JsonExecutionReporter reporter = new JsonExecutionReporter(JsonReporterConfiguration.from(Map.of(
                JsonReporterConfiguration.OUTPUT_DIR_PROPERTY, outputDirectory.toString(),
                JsonReporterConfiguration.FILE_NAME_PROPERTY, "document")));

        reporter.onEvent(configurationFailure("config-1"));
        reporter.close();

        Map<String, Object> document = TestJsonParser.parseObject(
                Files.readString(reporter.documentPath(), StandardCharsets.UTF_8));
        assertEquals(1, ((List<?>) document.get("events")).size());
        Map<?, ?> totals = (Map<?, ?>) ((Map<?, ?>) document.get("summary")).get("totals");
        assertEquals(1L, totals.get("configurationFailures"));
    }
}
