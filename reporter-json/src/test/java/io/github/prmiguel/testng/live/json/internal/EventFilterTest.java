package io.github.prmiguel.testng.live.json.internal;

import io.github.prmiguel.testng.live.core.model.ExecutionStatus;
import io.github.prmiguel.testng.live.json.JsonReporterConfiguration;
import io.github.prmiguel.testng.live.json.TestEvents;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventFilterTest {

    @Test
    void acceptsEverythingByDefault() {
        EventFilter filter = new EventFilter(JsonReporterConfiguration.from(Map.of()));

        assertTrue(filter.accepts(TestEvents.started("r", "e", TestEvents.identity("com.acme.Sample", "runs"))));
        assertTrue(filter.accepts(TestEvents.finished("r", "e", TestEvents.identity("com.acme.Sample", "runs"),
                ExecutionStatus.PASSED, 1)));
    }

    @Test
    void filtersByClassNameGlob() {
        EventFilter filter = new EventFilter(JsonReporterConfiguration.from(Map.of(
                JsonReporterConfiguration.INCLUDE_CLASSES_PROPERTY, "com.acme.*")));

        assertTrue(filter.accepts(TestEvents.started("r", "e", TestEvents.identity("com.acme.Sample", "runs"))));
        assertFalse(filter.accepts(TestEvents.started("r", "e", TestEvents.identity("org.other.Sample", "runs"))));
    }

    @Test
    void excludesByClassAndMethod() {
        EventFilter filter = new EventFilter(JsonReporterConfiguration.from(Map.of(
                JsonReporterConfiguration.EXCLUDE_CLASSES_PROPERTY, "com.acme.Legacy*",
                JsonReporterConfiguration.EXCLUDE_METHODS_PROPERTY, "*Slow")));

        assertFalse(filter.accepts(TestEvents.started("r", "e", TestEvents.identity("com.acme.LegacyTests", "runs"))));
        assertFalse(filter.accepts(TestEvents.started("r", "e", TestEvents.identity("com.acme.Sample", "runsSlow"))));
        assertTrue(filter.accepts(TestEvents.started("r", "e", TestEvents.identity("com.acme.Sample", "runs"))));
    }

    @Test
    void filtersTerminalStatusesOnly() {
        EventFilter filter = new EventFilter(JsonReporterConfiguration.from(Map.of(
                JsonReporterConfiguration.INCLUDE_STATUSES_PROPERTY, "FAILED")));

        assertFalse(filter.accepts(TestEvents.finished("r", "e", TestEvents.identity("a.B", "c"),
                ExecutionStatus.PASSED, 1)));
        assertTrue(filter.accepts(TestEvents.finished("r", "e", TestEvents.identity("a.B", "c"),
                ExecutionStatus.FAILED, 1)));
    }

    @Test
    void keepsNonTerminalEventsRegardlessOfStatusFilter() {
        EventFilter filter = new EventFilter(JsonReporterConfiguration.from(Map.of(
                JsonReporterConfiguration.INCLUDE_STATUSES_PROPERTY, "FAILED")));

        assertTrue(filter.accepts(TestEvents.started("r", "e", TestEvents.identity("a.B", "c"))));
        assertTrue(filter.accepts(TestEvents.inProgress("r", "e", TestEvents.identity("a.B", "c"))));
    }

    @Test
    void interpretsSingleCharacterWildcard() {
        EventFilter filter = new EventFilter(JsonReporterConfiguration.from(Map.of(
                JsonReporterConfiguration.INCLUDE_METHODS_PROPERTY, "tes?")));

        assertTrue(filter.accepts(TestEvents.started("r", "e", TestEvents.identity("a.B", "test"))));
        assertFalse(filter.accepts(TestEvents.started("r", "e", TestEvents.identity("a.B", "tests"))));
    }
}
