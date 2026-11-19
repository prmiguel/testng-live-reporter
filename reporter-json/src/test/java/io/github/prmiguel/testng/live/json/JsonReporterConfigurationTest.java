package io.github.prmiguel.testng.live.json;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonReporterConfigurationTest {

    @Test
    void usesDefaultsWhenNothingIsConfigured() {
        JsonReporterConfiguration configuration =
                JsonReporterConfiguration.from(new Properties(), Map.of());

        assertTrue(configuration.enabled());
        assertEquals(Path.of("target/testng-live-reporter"), configuration.outputDirectory());
        assertNull(configuration.fileName());
        assertFalse(configuration.overwrite());
        assertTrue(configuration.writeNdjson());
        assertTrue(configuration.writeDocument());
        assertTrue(configuration.flushEachEvent());
        assertEquals(List.of(), configuration.includeClasses());
        assertEquals(List.of(), configuration.includeStatuses());
        assertEquals(0, configuration.maxStackTraceChars());
        assertFalse(configuration.sortEvents());
        assertTrue(configuration.includeAttributes());
    }

    @Test
    void readsEveryProperty() {
        Map<String, String> values = Map.ofEntries(
                Map.entry(JsonReporterConfiguration.ENABLED_PROPERTY, "false"),
                Map.entry(JsonReporterConfiguration.OUTPUT_DIR_PROPERTY, "/tmp/out"),
                Map.entry(JsonReporterConfiguration.FILE_NAME_PROPERTY, "my-run"),
                Map.entry(JsonReporterConfiguration.OVERWRITE_PROPERTY, "true"),
                Map.entry(JsonReporterConfiguration.NDJSON_PROPERTY, "false"),
                Map.entry(JsonReporterConfiguration.DOCUMENT_PROPERTY, "false"),
                Map.entry(JsonReporterConfiguration.FLUSH_EACH_EVENT_PROPERTY, "false"),
                Map.entry(JsonReporterConfiguration.INCLUDE_CLASSES_PROPERTY, "com.acme.*, *.Smoke*"),
                Map.entry(JsonReporterConfiguration.EXCLUDE_CLASSES_PROPERTY, "com.acme.Legacy*"),
                Map.entry(JsonReporterConfiguration.INCLUDE_METHODS_PROPERTY, "test*"),
                Map.entry(JsonReporterConfiguration.EXCLUDE_METHODS_PROPERTY, "*Slow"),
                Map.entry(JsonReporterConfiguration.INCLUDE_STATUSES_PROPERTY, "failed, timeout"),
                Map.entry(JsonReporterConfiguration.MAX_STACK_TRACE_CHARS_PROPERTY, "64"),
                Map.entry(JsonReporterConfiguration.SORT_EVENTS_PROPERTY, "true"),
                Map.entry(JsonReporterConfiguration.INCLUDE_ATTRIBUTES_PROPERTY, "false"));

        JsonReporterConfiguration configuration = JsonReporterConfiguration.from(values);

        assertFalse(configuration.enabled());
        assertEquals(Path.of("/tmp/out"), configuration.outputDirectory());
        assertEquals("my-run", configuration.fileName());
        assertTrue(configuration.overwrite());
        assertFalse(configuration.writeNdjson());
        assertFalse(configuration.writeDocument());
        assertFalse(configuration.flushEachEvent());
        assertEquals(List.of("com.acme.*", "*.Smoke*"), configuration.includeClasses());
        assertEquals(List.of("com.acme.Legacy*"), configuration.excludeClasses());
        assertEquals(List.of("test*"), configuration.includeMethods());
        assertEquals(List.of("*Slow"), configuration.excludeMethods());
        assertEquals(List.of("FAILED", "TIMEOUT"), configuration.includeStatuses());
        assertEquals(64, configuration.maxStackTraceChars());
        assertTrue(configuration.sortEvents());
        assertFalse(configuration.includeAttributes());
    }

    @Test
    void systemPropertiesOverrideEnvironment() {
        Properties properties = new Properties();
        properties.setProperty(JsonReporterConfiguration.OUTPUT_DIR_PROPERTY, "/from/properties");

        JsonReporterConfiguration configuration = JsonReporterConfiguration.from(properties,
                Map.of("TESTNG_LIVE_JSON_OUTPUT_DIR", "/from/env"));

        assertEquals(Path.of("/from/properties"), configuration.outputDirectory());
    }

    @Test
    void readsFromEnvironmentWhenNoPropertyIsSet() {
        JsonReporterConfiguration configuration = JsonReporterConfiguration.from(new Properties(),
                Map.of("TESTNG_LIVE_JSON_OUTPUT_DIR", "/from/env",
                        "TESTNG_LIVE_JSON_SORT_EVENTS", "true"));

        assertEquals(Path.of("/from/env"), configuration.outputDirectory());
        assertTrue(configuration.sortEvents());
    }

    @Test
    void readsPropertiesFile() throws IOException {
        Path file = Files.createTempFile("reporter-json", ".properties");
        Files.writeString(file, "testng.live.json.output-dir=/from/file\ntestng.live.json.file-name=file-run\n");

        Properties properties = new Properties();
        properties.setProperty(JsonReporterConfiguration.CONFIG_FILE_PROPERTY, file.toString());
        JsonReporterConfiguration configuration = JsonReporterConfiguration.from(properties, Map.of());

        assertEquals(Path.of("/from/file"), configuration.outputDirectory());
        assertEquals("file-run", configuration.fileName());
    }

    @Test
    void invalidValuesFallBackToDefaults() {
        Map<String, String> values = Map.of(
                JsonReporterConfiguration.MAX_STACK_TRACE_CHARS_PROPERTY, "not-a-number",
                JsonReporterConfiguration.ENABLED_PROPERTY, "maybe");

        JsonReporterConfiguration configuration = JsonReporterConfiguration.from(values);

        assertTrue(configuration.enabled());
        assertEquals(0, configuration.maxStackTraceChars());
    }

    @Test
    void ignoresBlankValues() {
        Map<String, String> values = Map.of(
                JsonReporterConfiguration.FILE_NAME_PROPERTY, "  ",
                JsonReporterConfiguration.INCLUDE_CLASSES_PROPERTY, " ");

        JsonReporterConfiguration configuration = JsonReporterConfiguration.from(values);

        assertNull(configuration.fileName());
        assertEquals(List.of(), configuration.includeClasses());
    }
}
