package io.github.prmiguel.testng.live.core;

import io.github.prmiguel.testng.live.core.fixtures.SampleTests;
import io.github.prmiguel.testng.live.core.internal.CoreConfiguration;
import io.github.prmiguel.testng.live.core.model.ExecutionEvent;
import io.github.prmiguel.testng.live.core.model.ExecutionState;
import io.github.prmiguel.testng.live.core.model.ExecutionStatus;
import io.github.prmiguel.testng.live.core.spi.ExecutionReporter;
import org.junit.jupiter.api.Test;
import org.testng.TestNG;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveReportingListenerIntegrationTest {

    @Test
    void capturesExecutionEvents() {
        CoreConfiguration configuration = new CoreConfiguration("integration-run", Duration.ofSeconds(30));
        List<ExecutionEvent> captured = Collections.synchronizedList(new ArrayList<>());
        ExecutionReporter reporter = captured::add;

        LiveReportingListener listener = new LiveReportingListener(configuration, List.of(), List.of(reporter));

        TestNG testNG = new TestNG();
        testNG.setTestClasses(new Class[]{SampleTests.class});
        testNG.addListener(listener);
        testNG.run();

        // 1 passing + 1 failing + 1 skipped + 1 timeout + 2 data-provider = 6 methods, each STARTED/IN_PROGRESS/FINISHED.
        long finished = captured.stream().filter(ExecutionEvent::isTerminal).count();
        assertEquals(6, finished);

        long passed = captured.stream()
                .filter(e -> e.state() == ExecutionState.FINISHED && e.status() == ExecutionStatus.PASSED)
                .count();
        long failed = captured.stream()
                .filter(e -> e.state() == ExecutionState.FINISHED && e.status() == ExecutionStatus.FAILED)
                .count();
        long skipped = captured.stream()
                .filter(e -> e.state() == ExecutionState.FINISHED && e.status() == ExecutionStatus.SKIPPED)
                .count();
        long timeout = captured.stream()
                .filter(e -> e.state() == ExecutionState.FINISHED && e.status() == ExecutionStatus.TIMEOUT)
                .count();

        assertEquals(3, passed);   // passingTest + 2 data-provider invocations
        assertEquals(1, failed);    // failingTest
        assertEquals(1, skipped);  // skippedTest
        assertEquals(1, timeout);  // timeoutTest

        captured.forEach(event -> {
            assertNotNull(event.testIdentity());
            assertNotNull(event.executionId());
            assertTrue(event.testIdentity().hash().length() == 16);
            assertEquals(1, event.schemaVersion());
        });

        long uniqueTestIds = captured.stream().map(e -> e.testIdentity().testId()).distinct().count();
        assertTrue(uniqueTestIds >= 5);

        long uniqueExecutionIds = captured.stream().map(ExecutionEvent::executionId).distinct().count();
        assertEquals(6, uniqueExecutionIds);
    }
}
