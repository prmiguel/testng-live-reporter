package io.github.prmiguel.testng.live.json;

import io.github.prmiguel.testng.live.core.LiveReportingListener;
import io.github.prmiguel.testng.live.core.internal.CoreConfiguration;
import io.github.prmiguel.testng.live.core.model.ExecutionState;
import io.github.prmiguel.testng.live.core.model.ExecutionStatus;
import io.github.prmiguel.testng.live.json.fixtures.ParallelSampleTests;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testng.TestNG;
import org.testng.xml.XmlSuite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writes a real report into a known directory so CI can validate the on-disk output.
 * <p>
 * Disabled unless {@code -Ddogfood.output-dir} is set, which keeps a plain
 * {@code mvn verify} hermetic.
 */
@EnabledIfSystemProperty(named = JsonOutputDogfoodTest.OUTPUT_DIR_PROPERTY, matches = ".+")
class JsonOutputDogfoodTest {

    static final String OUTPUT_DIR_PROPERTY = "dogfood.output-dir";

    @Test
    void writesReportsThatCiCanValidate() throws IOException {
        Path outputDirectory = Path.of(System.getProperty(OUTPUT_DIR_PROPERTY));
        JsonExecutionReporter reporter = new JsonExecutionReporter(JsonReporterConfiguration.from(Map.of(
                JsonReporterConfiguration.OUTPUT_DIR_PROPERTY, outputDirectory.toString(),
                JsonReporterConfiguration.FILE_NAME_PROPERTY, "dogfood")));

        TestNG testNG = new TestNG();
        testNG.setTestClasses(new Class[]{ParallelSampleTests.class});
        testNG.setParallel(XmlSuite.ParallelMode.METHODS);
        testNG.setThreadCount(4);
        testNG.setUseDefaultListeners(false);
        testNG.addListener(new LiveReportingListener(
                new CoreConfiguration("dogfood-run", Duration.ofSeconds(30)), List.of(), List.of(reporter)));
        testNG.run();

        Path ndjsonPath = outputDirectory.resolve("dogfood.ndjson");
        Path documentPath = outputDirectory.resolve("dogfood.json");
        assertTrue(Files.exists(ndjsonPath), "live stream was not written");
        assertTrue(Files.exists(documentPath), "document was not written");

        List<String> lines = Files.readAllLines(ndjsonPath, StandardCharsets.UTF_8);
        List<Map<String, Object>> events = new ArrayList<>();
        for (String line : lines.subList(1, lines.size() - 1)) {
            events.add(TestJsonParser.parseObject(line));
        }
        assertEquals(lines.size(), events.size() + 2, "header + events + summary");

        assertTrue(events.stream().anyMatch(event -> "PASSED".equals(event.get("status"))));
        assertTrue(events.stream().anyMatch(event -> "FAILED".equals(event.get("status"))));
        assertTrue(events.stream().anyMatch(event -> "TIMEOUT".equals(event.get("status"))));

        long terminals = events.stream().filter(event -> ExecutionState.FINISHED.name().equals(event.get("state")))
                .count();
        Map<?, ?> totals = (Map<?, ?>) TestJsonParser.parseObject(lines.get(lines.size() - 1)).get("totals");
        assertEquals(terminals, totals.get("tests"));
        assertEquals(0L, totals.get("writeErrors"));
    }
}
