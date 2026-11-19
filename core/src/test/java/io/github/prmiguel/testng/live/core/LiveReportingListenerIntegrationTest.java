package io.github.prmiguel.testng.live.core;

import io.github.prmiguel.testng.live.core.fixtures.RetriedFixture;
import io.github.prmiguel.testng.live.core.fixtures.SampleTests;
import io.github.prmiguel.testng.live.core.internal.CoreConfiguration;
import io.github.prmiguel.testng.live.core.model.ExecutionEvent;
import io.github.prmiguel.testng.live.core.model.ExecutionState;
import io.github.prmiguel.testng.live.core.model.ExecutionStatus;
import io.github.prmiguel.testng.live.core.model.RunInfo;
import io.github.prmiguel.testng.live.core.spi.ExecutionReporter;
import org.junit.jupiter.api.Test;
import org.testng.TestNG;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveReportingListenerIntegrationTest {

    private static final class RecordingReporter implements ExecutionReporter {
        final List<ExecutionEvent> events = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger runsStarted = new AtomicInteger();
        final AtomicInteger runsFinished = new AtomicInteger();

        @Override
        public void onEvent(ExecutionEvent event) {
            events.add(event);
        }

        @Override
        public void onRunStarted(RunInfo info) {
            runsStarted.incrementAndGet();
        }

        @Override
        public void onRunFinished(RunInfo info) {
            runsFinished.incrementAndGet();
        }
    }

    @Test
    void capturesExecutionEvents() {
        CoreConfiguration configuration = new CoreConfiguration("integration-run", Duration.ofSeconds(30));
        RecordingReporter reporter = new RecordingReporter();

        LiveReportingListener listener = new LiveReportingListener(configuration, List.of(), List.of(reporter));

        TestNG testNG = new TestNG();
        testNG.setTestClasses(new Class[]{SampleTests.class});
        testNG.setExcludedGroups("excluded");
        testNG.setListenersToSkipFromBeingWiredInViaServiceLoaders(
                "io.github.prmiguel.testng.live.core.LiveReportingListener");
        testNG.addListener(listener);
        testNG.run();

        List<ExecutionEvent> captured = reporter.events;

        // 1 passing + 1 failing + 1 skipped + 1 timeout + 2 data-provider = 6 executed methods,
        // each STARTED/IN_PROGRESS/FINISHED, plus 1 group-excluded method reported as IGNORED.
        long finished = captured.stream()
                .filter(e -> e.state() == ExecutionState.FINISHED)
                .count();
        assertEquals(7, finished);
        assertEquals(1, countByStatus(captured, ExecutionStatus.IGNORED));

        long passed = countByStatus(captured, ExecutionStatus.PASSED);
        long failed = countByStatus(captured, ExecutionStatus.FAILED);
        long skipped = countByStatus(captured, ExecutionStatus.SKIPPED);
        long timeout = countByStatus(captured, ExecutionStatus.TIMEOUT);

        assertEquals(3, passed);   // passingTest + 2 data-provider invocations
        assertEquals(1, failed);   // failingTest
        assertEquals(1, skipped);  // skippedTest
        assertEquals(1, timeout);  // timeoutTest

        captured.forEach(event -> {
            assertNotNull(event.testIdentity());
            assertNotNull(event.executionId());
            assertTrue(event.testIdentity().hash().length() == 16);
            assertEquals(2, event.schemaVersion());
        });

        long uniqueTestIds = captured.stream().map(e -> e.testIdentity().testId()).distinct().count();
        assertTrue(uniqueTestIds >= 5);

        // One execution id per attempt: discovery records use their own id namespace.
        long uniqueExecutionIds = captured.stream()
                .filter(e -> e.state() != ExecutionState.DISCOVERED && e.status() != ExecutionStatus.IGNORED)
                .map(ExecutionEvent::executionId)
                .distinct()
                .count();
        assertEquals(6, uniqueExecutionIds);
    }

    @Test
    void publishesStartedExactlyOncePerAttempt() {
        RecordingReporter reporter = runSampleTests("once-run");

        Map<String, Long> startedPerAttempt = reporter.events.stream()
                .filter(e -> e.state() == ExecutionState.STARTED)
                .collect(Collectors.groupingBy(ExecutionEvent::executionId, Collectors.counting()));

        assertTrue(!startedPerAttempt.isEmpty());
        startedPerAttempt.forEach((executionId, count) ->
                assertEquals(1L, count, "duplicate STARTED for " + executionId));
    }

    @Test
    void emitsDiscoveryAndIgnoredRecords() {
        RecordingReporter reporter = runSampleTests("discovery-run");

        List<ExecutionEvent> discovered = reporter.events.stream()
                .filter(e -> e.state() == ExecutionState.DISCOVERED)
                .toList();
        assertTrue(!discovered.isEmpty(), "methods must be reported as discovered");
        assertEquals(discovered.size(), discovered.stream().map(e -> e.testIdentity().testId()).distinct().count(),
                "each method must be discovered once");
        discovered.forEach(event -> assertEquals(ExecutionStatus.UNDETERMINED, event.status()));

        reporter.events.stream()
                .filter(e -> e.status() == ExecutionStatus.IGNORED)
                .forEach(event -> {
                    assertEquals(ExecutionState.FINISHED, event.state());
                    assertNotNull(event.duration(), "IGNORED is terminal and needs a duration");
                });
        assertTrue(reporter.events.stream().anyMatch(e -> e.status() == ExecutionStatus.IGNORED),
                "an excluded group must be reported as IGNORED");
    }

    @Test
    void publishesRunBoundariesOnce() {
        RecordingReporter reporter = runSampleTests("run-boundary");

        assertEquals(1, reporter.runsStarted.get(), "onRunStarted must fire once");
        assertEquals(1, reporter.runsFinished.get(), "onRunFinished must fire once");
    }

    @Test
    void numbersRetriedAttempts() {
        RecordingReporter reporter = new RecordingReporter();
        LiveReportingListener listener = new LiveReportingListener(
                new CoreConfiguration("retry-run", Duration.ofSeconds(30)), List.of(), List.of(reporter));

        TestNG testNG = new TestNG();
        testNG.setTestClasses(new Class[]{RetriedFixture.class});
        testNG.setUseDefaultListeners(false);
        testNG.setListenersToSkipFromBeingWiredInViaServiceLoaders(
                "io.github.prmiguel.testng.live.core.LiveReportingListener");
        testNG.addListener(listener);
        testNG.run();

        List<Integer> attempts = reporter.events.stream()
                .filter(e -> "flaky".equals(e.testIdentity().methodName()))
                .filter(e -> e.state() == ExecutionState.STARTED)
                .map(ExecutionEvent::retryAttempt)
                .sorted()
                .toList();

        assertEquals(List.of(0, 1), attempts, "a retried test must report increasing attempt numbers");
    }

    private static RecordingReporter runSampleTests(String runId) {
        CoreConfiguration configuration = new CoreConfiguration(runId, Duration.ofSeconds(30));
        RecordingReporter reporter = new RecordingReporter();
        LiveReportingListener listener = new LiveReportingListener(configuration, List.of(), List.of(reporter));

        TestNG testNG = new TestNG();
        testNG.setTestClasses(new Class[]{SampleTests.class});
        testNG.setExcludedGroups("excluded");
        testNG.setUseDefaultListeners(false);
        // Skip the ServiceLoader-wired twin so exactly one listener is observed.
        testNG.setListenersToSkipFromBeingWiredInViaServiceLoaders(
                "io.github.prmiguel.testng.live.core.LiveReportingListener");
        testNG.addListener(listener);
        testNG.run();
        return reporter;
    }

    private static long countByStatus(List<ExecutionEvent> events, ExecutionStatus status) {
        return events.stream()
                .filter(e -> e.state() == ExecutionState.FINISHED && e.status() == status)
                .count();
    }
}
