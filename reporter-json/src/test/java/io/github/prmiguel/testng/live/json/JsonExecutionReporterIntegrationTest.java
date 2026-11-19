package io.github.prmiguel.testng.live.json;

import io.github.prmiguel.testng.live.core.LiveReportingListener;
import io.github.prmiguel.testng.live.core.internal.CoreConfiguration;
import io.github.prmiguel.testng.live.json.fixtures.ParallelSampleTests;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testng.TestNG;
import org.testng.xml.XmlSuite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonExecutionReporterIntegrationTest {

    @TempDir
    Path outputDirectory;

    @Test
    void reportsARealTestNgRun() throws IOException {
        JsonExecutionReporter reporter = new JsonExecutionReporter(JsonReporterConfiguration.from(Map.of(
                JsonReporterConfiguration.OUTPUT_DIR_PROPERTY, outputDirectory.toString(),
                JsonReporterConfiguration.FILE_NAME_PROPERTY, "integration")));

        TestNG testNG = new TestNG();
        testNG.setTestClasses(new Class[]{ParallelSampleTests.class});
        testNG.setParallel(XmlSuite.ParallelMode.METHODS);
        testNG.setThreadCount(4);
        testNG.setUseDefaultListeners(false);
        testNG.addListener(new LiveReportingListener(
                new CoreConfiguration("integration-run", Duration.ofSeconds(30)), List.of(), List.of(reporter)));
        testNG.run();

        assertTrue(Files.exists(reporter.ndjsonPath()), "live stream exists");
        assertTrue(Files.exists(reporter.documentPath()), "consolidated document exists");

        List<String> lines = Files.readAllLines(reporter.ndjsonPath(), StandardCharsets.UTF_8);
        assertEquals("run.started", TestJsonParser.parseObject(lines.get(0)).get("type"));
        assertEquals("run.summary", TestJsonParser.parseObject(lines.get(lines.size() - 1)).get("type"));

        List<Map<String, Object>> events = new ArrayList<>();
        Set<String> seenAttemptStates = new HashSet<>();
        List<String> duplicatedKeys = new ArrayList<>();
        for (String line : lines.subList(1, lines.size() - 1)) {
            Map<String, Object> event = TestJsonParser.parseObject(line);
            assertEquals("test.event", event.get("type"));
            String key = event.get("executionId") + "/" + event.get("state");
            if (!seenAttemptStates.add(key)) {
                duplicatedKeys.add(key);
            }
            events.add(event);
        }
        assertEquals(List.of(), duplicatedKeys, "no (executionId, state) pair may be published twice");

        assertFalse(events.isEmpty());
        events.forEach(event -> {
            Map<?, ?> test = (Map<?, ?>) event.get("test");
            assertNotNull(test, "every event must carry a test identity");
            assertNotNull(test.get("testId"));
            assertEquals(16, test.get("hash").toString().length());
            assertNotNull(event.get("suiteName"));
            assertNotNull(event.get("threadName"));
        });

        long terminals = events.stream().filter(event -> "FINISHED".equals(event.get("state"))).count();
        assertTrue(terminals >= 6, "every fixture outcome must be reported, got " + terminals);
        assertTrue(events.stream().anyMatch(event -> "TIMEOUT".equals(event.get("status"))));
        assertTrue(events.stream().anyMatch(event -> "SKIPPED".equals(event.get("status"))));
        assertTrue(events.stream().anyMatch(event -> "FAILED".equals(event.get("status"))));
        assertTrue(events.stream().anyMatch(event -> "PASSED".equals(event.get("status"))));

        Map<?, ?> totals = (Map<?, ?>) TestJsonParser.parseObject(lines.get(lines.size() - 1)).get("totals");
        assertEquals((long) events.size(), totals.get("events"));
        assertEquals(terminals, totals.get("tests"));
        assertEquals(0L, totals.get("writeErrors"));
        assertTrue(((Number) totals.get("passed")).longValue() >= 4, "4 passing fixture outcomes");

        // core no longer publishes a duplicated STARTED, so the reporter's defence must not
        // have to fire on a healthy run.
        assertEquals(0L, totals.get("droppedDuplicates"),
                "core must not emit duplicate (executionId, state) pairs: " + duplicatedKeys);

        // Discovery records are new in v2 and must still be reported.
        assertTrue(events.stream().anyMatch(event -> "DISCOVERED".equals(event.get("state"))),
                "methods must be reported as discovered");

        Map<String, Object> document = TestJsonParser.parseObject(
                Files.readString(reporter.documentPath(), StandardCharsets.UTF_8));
        assertEquals(events.size(), ((List<?>) document.get("events")).size());
        assertEquals("integration-run", ((Map<?, ?>) document.get("run")).get("runId"));
        assertNotNull(document.get("summary"));
    }
}
