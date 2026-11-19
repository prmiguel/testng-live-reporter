package io.github.prmiguel.testng.live.core;

import io.github.prmiguel.testng.live.core.fixtures.ConfigurationFailureFixture;
import io.github.prmiguel.testng.live.core.internal.CoreConfiguration;
import io.github.prmiguel.testng.live.core.internal.ExecutionEventFactory;
import io.github.prmiguel.testng.live.core.model.ExecutionEvent;
import io.github.prmiguel.testng.live.core.model.ExecutionState;
import io.github.prmiguel.testng.live.core.model.ExecutionStatus;
import org.junit.jupiter.api.Test;
import org.testng.TestNG;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationFailureReportingTest {

    @Test
    void reportsEachConfigurationFailureSeparately() {
        List<ExecutionEvent> events = Collections.synchronizedList(new ArrayList<>());

        TestNG testNG = new TestNG();
        testNG.setTestClasses(new Class[]{ConfigurationFailureFixture.class});
        testNG.setUseDefaultListeners(false);
        // TestNG also auto-loads LiveReportingListener through
        // META-INF/services/org.testng.ITestNGListener, which would be a *second* instance
        // with its own event bus. Skip it so this test observes exactly one.
        testNG.setListenersToSkipFromBeingWiredInViaServiceLoaders(
                "io.github.prmiguel.testng.live.core.LiveReportingListener");
        testNG.addListener(new LiveReportingListener(
                new CoreConfiguration("config-run", Duration.ofSeconds(30)), List.of(), List.of(events::add)));
        testNG.run();

        List<ExecutionEvent> failures = events.stream()
                .filter(e -> ExecutionEventFactory.PHASE_CONFIGURATION.equals(
                        e.attributes().get(ExecutionEventFactory.PHASE_ATTRIBUTE)))
                .toList();

        assertEquals(1, failures.size(),
                "TestNG reports the failing @BeforeMethod once, not once per affected test");
        assertEquals(1, failures.stream().map(ExecutionEvent::executionId).distinct().count());

        failures.forEach(event -> {
            assertEquals(ExecutionState.FINISHED, event.state());
            assertEquals(ExecutionStatus.ERROR, event.status());
            assertNotNull(event.failure(), "the throwable is the whole point of the record");
            assertNotNull(event.duration());
            assertEquals("beforeMethod", event.attributes().get("configurationKind"));
            assertEquals("config-run", event.runId());
        });

        // The affected tests are still reported, and are not turned into ERROR records.
        assertEquals(2, events.stream()
                        .filter(e -> e.state() == ExecutionState.FINISHED && e.status() == ExecutionStatus.SKIPPED)
                        .count(),
                "both tests are skipped, and each explains itself through the config record");
        assertTrue(events.stream().noneMatch(e -> e.state() == ExecutionState.FINISHED
                        && e.status() == ExecutionStatus.ERROR
                        && !ExecutionEventFactory.PHASE_CONFIGURATION.equals(
                                e.attributes().get(ExecutionEventFactory.PHASE_ATTRIBUTE))),
                "a configuration failure must not be reported as a test ERROR too");
    }
}
