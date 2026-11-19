package io.github.prmiguel.testng.live.json;

import io.github.prmiguel.testng.live.core.ExecutionEventBus;
import io.github.prmiguel.testng.live.core.internal.CoreConfiguration;
import io.github.prmiguel.testng.live.core.model.CanonicalParameter;
import io.github.prmiguel.testng.live.core.model.ExecutionEvent;
import io.github.prmiguel.testng.live.core.model.ExecutionState;
import io.github.prmiguel.testng.live.core.model.ExecutionStatus;
import io.github.prmiguel.testng.live.core.model.TestIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonExecutionReporterConcurrencyTest {

    private static final int THREADS = 4;
    private static final int EVENTS_PER_THREAD = 250;

    @TempDir
    Path outputDirectory;

    @Test
    void writesEveryEventWhenExecutionsRunInParallel() throws Exception {
        JsonExecutionReporter reporter = new JsonExecutionReporter(JsonReporterConfiguration.from(Map.of(
                JsonReporterConfiguration.OUTPUT_DIR_PROPERTY, outputDirectory.toString(),
                JsonReporterConfiguration.FILE_NAME_PROPERTY, "parallel")));
        CoreConfiguration coreConfiguration = new CoreConfiguration("parallel-run", Duration.ofSeconds(30));

        int expectedEvents = THREADS * EVENTS_PER_THREAD;
        List<ExecutionEvent> produced = new ArrayList<>(expectedEvents);
        Set<String> expectedExecutionIds = new HashSet<>();
        synchronized (produced) {
            produceEvents(coreConfiguration, produced, expectedExecutionIds);
        }

        try (ExecutionEventBus bus = new ExecutionEventBus(List.of(reporter), coreConfiguration)) {
            publishInParallel(bus, produced);
        }

        List<String> lines = Files.readAllLines(reporter.ndjsonPath(), StandardCharsets.UTF_8);
        assertEquals(expectedEvents + 2, lines.size(), "header + every event + summary");

        Set<String> writtenExecutionIds = new HashSet<>();
        int eventLines = 0;
        for (String line : lines) {
            Map<String, Object> parsed = TestJsonParser.parseObject(line);
            if ("test.event".equals(parsed.get("type"))) {
                eventLines++;
                writtenExecutionIds.add((String) parsed.get("executionId"));
            }
        }
        assertEquals(expectedEvents, eventLines, "no event may be dropped");
        assertEquals(expectedExecutionIds, writtenExecutionIds, "every execution must be represented");

        Map<String, Object> document = TestJsonParser.parseObject(
                Files.readString(reporter.documentPath(), StandardCharsets.UTF_8));
        assertEquals(expectedEvents, ((List<?>) document.get("events")).size());

        Map<?, ?> totals = (Map<?, ?>) TestJsonParser.parseObject(lines.get(lines.size() - 1)).get("totals");
        assertEquals((long) expectedEvents, totals.get("events"));
        assertEquals((long) THREADS, totals.get("tests"), "one terminal event per thread");
        assertEquals(0L, totals.get("droppedDuplicates"));
        assertEquals(0L, totals.get("writeErrors"));
    }

    @Test
    void keepsEveryLineParseableUnderLoad() throws Exception {
        JsonExecutionReporter reporter = new JsonExecutionReporter(JsonReporterConfiguration.from(Map.of(
                JsonReporterConfiguration.OUTPUT_DIR_PROPERTY, outputDirectory.toString(),
                JsonReporterConfiguration.FILE_NAME_PROPERTY, "parseable",
                JsonReporterConfiguration.FLUSH_EACH_EVENT_PROPERTY, "false")));
        CoreConfiguration coreConfiguration = new CoreConfiguration("parseable-run", Duration.ofSeconds(30));

        List<ExecutionEvent> produced = new ArrayList<>();
        Set<String> expectedExecutionIds = new HashSet<>();
        produceEvents(coreConfiguration, produced, expectedExecutionIds);

        try (ExecutionEventBus bus = new ExecutionEventBus(List.of(reporter), coreConfiguration)) {
            publishInParallel(bus, produced);
        }

        List<String> lines = Files.readAllLines(reporter.ndjsonPath(), StandardCharsets.UTF_8);
        assertEquals(THREADS * EVENTS_PER_THREAD + 2, lines.size());
        for (String line : lines) {
            assertTrue(line.endsWith("}"), "each record must be a complete object: " + line);
            TestJsonParser.parseObject(line);
        }
    }

    private void produceEvents(CoreConfiguration configuration, List<ExecutionEvent> events,
                               Set<String> executionIds) {
        for (int thread = 0; thread < THREADS; thread++) {
            TestIdentity identity = identity("com.acme.ParallelTest", "case" + thread);
            for (int index = 0; index < EVENTS_PER_THREAD; index++) {
                String executionId = "exec-" + thread + "-" + index;
                executionIds.add(executionId);
                boolean last = index == EVENTS_PER_THREAD - 1;
                events.add(new ExecutionEvent(
                        ExecutionEvent.CURRENT_SCHEMA_VERSION,
                        index + 1L,
                        configuration.runId(),
                        executionId,
                        identity,
                        last ? ExecutionState.FINISHED : ExecutionState.STARTED,
                        last ? ExecutionStatus.PASSED : ExecutionStatus.UNDETERMINED,
                        Instant.parse("2026-11-19T10:00:00.000Z"),
                        last ? Duration.ofMillis(1000) : null,
                        "parallel-suite",
                        "parallel-test",
                        thread,
                        "worker-" + thread,
                        0,
                        0,
                        null,
                        Map.of("step", String.valueOf(index))));
            }
        }
    }

    private void publishInParallel(ExecutionEventBus bus, List<ExecutionEvent> events) throws InterruptedException {
        ExecutorService workers = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            int perThread = events.size() / THREADS;
            for (int thread = 0; thread < THREADS; thread++) {
                int from = thread * perThread;
                int to = from + perThread;
                workers.submit(() -> {
                    start.await();
                    for (int index = from; index < to; index++) {
                        bus.publish(events.get(index));
                    }
                    return null;
                });
            }
            start.countDown();
        } finally {
            workers.shutdown();
            assertTrue(workers.awaitTermination(30, TimeUnit.SECONDS), "workers finished");
        }
    }

    private static TestIdentity identity(String className, String methodName) {
        return new TestIdentity(className, methodName, List.<CanonicalParameter>of(),
                className + "." + methodName + "[]", "0123456789abcdef",
                className + "." + methodName + "[]#0123456789abcdef");
    }
}
