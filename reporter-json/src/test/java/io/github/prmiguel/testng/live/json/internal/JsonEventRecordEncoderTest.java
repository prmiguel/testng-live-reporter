package io.github.prmiguel.testng.live.json.internal;

import io.github.prmiguel.testng.live.core.model.ExecutionEvent;
import io.github.prmiguel.testng.live.core.model.ExecutionState;
import io.github.prmiguel.testng.live.core.model.ExecutionStatus;
import io.github.prmiguel.testng.live.core.model.FailureInfo;
import io.github.prmiguel.testng.live.core.model.TestIdentity;
import io.github.prmiguel.testng.live.json.TestEvents;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static io.github.prmiguel.testng.live.json.TestJsonParser.parseObject;
import static io.github.prmiguel.testng.live.json.TestJsonParser.readLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonEventRecordEncoderTest {

    private final JsonEventRecordEncoder encoder = new JsonEventRecordEncoder(0, true);

    @Test
    void encodesRunStarted() {
        String record = encoder.runStarted("run-1", Instant.parse("2026-11-19T10:00:00.412987Z"));
        Map<String, Object> parsed = parseObject(record);

        assertEquals("run.started", parsed.get("type"));
        assertEquals("run-1", parsed.get("runId"));
        assertEquals("2026-11-19T10:00:00.412Z", parsed.get("startedAt"));
        assertEquals("testng-live-reporter-json", ((Map<?, ?>) parsed.get("reporter")).get("name"));
    }

    @Test
    void encodesEveryStateAndStatus() {
        TestIdentity identity = TestEvents.identity("com.acme.Sample", "runs");
        for (ExecutionState state : ExecutionState.values()) {
            ExecutionEvent event = state == ExecutionState.FINISHED
                    ? TestEvents.finished("run-1", "exec-1", identity, ExecutionStatus.PASSED, 10)
                    : TestEvents.create("run-1", "exec-1", identity, state, ExecutionStatus.UNDETERMINED,
                    null, null, Map.of());
            Map<String, Object> parsed = parseObject(encoder.event(event));
            assertEquals(state.name(), parsed.get("state"));
            assertEquals("test.event", parsed.get("type"));
            assertEquals(1L, parsed.get("recordSchemaVersion"));
            assertEquals(1L, parsed.get("eventSchemaVersion"));
        }
        for (ExecutionStatus status : ExecutionStatus.values()) {
            if (status == ExecutionStatus.UNDETERMINED) {
                continue;
            }
            Map<String, Object> parsed = parseObject(
                    encoder.event(TestEvents.finished("run-1", "exec-1", identity, status, 1)));
            assertEquals(status.name(), parsed.get("status"));
        }
    }

    @Test
    void omitsDurationAndFailureUntilFinished() {
        TestIdentity identity = TestEvents.identity("com.acme.Sample", "runs");
        Map<String, Object> parsed = parseObject(
                encoder.event(TestEvents.started("run-1", "exec-1", identity)));

        assertNull(parsed.get("durationMs"));
        assertNull(parsed.get("failure"));
    }

    @Test
    void includesDurationOnFinished() {
        Map<String, Object> parsed = parseObject(encoder.event(
                TestEvents.finished("run-1", "exec-1", TestEvents.identity("com.acme.Sample", "runs"),
                        ExecutionStatus.PASSED, 1234)));

        assertEquals(1234L, parsed.get("durationMs"));
    }

    @Test
    void encodesFailureEscapedToASingleLine() {
        FailureInfo failure = new FailureInfo("java.lang.AssertionError", "expected true",
                "java.lang.AssertionError: expected true\n\tat com.acme.Sample.runs(Sample.java:12)\n");
        String record = encoder.event(TestEvents.finishedFailed("run-1", "exec-1",
                TestEvents.identity("com.acme.Sample", "runs"), failure));

        assertFalse(record.contains("\n"), "a record must be one line");
        Map<?, ?> parsedFailure = (Map<?, ?>) parseObject(record).get("failure");
        assertEquals("java.lang.AssertionError", parsedFailure.get("type"));
        assertTrue(parsedFailure.get("stackTrace").toString().contains("\tat com.acme.Sample.runs"));
        assertNull(parsedFailure.get("truncated"));
    }

    @Test
    void truncatesLongStackTrace() {
        JsonEventRecordEncoder truncating = new JsonEventRecordEncoder(10, true);
        FailureInfo failure = new FailureInfo("x", "m", "0123456789abcdef");
        Map<?, ?> parsedFailure = (Map<?, ?>) parseObject(truncating.event(
                TestEvents.finishedFailed("run-1", "exec-1", TestEvents.identity("a.B", "c"), failure)))
                .get("failure");

        assertEquals("0123456789", parsedFailure.get("stackTrace"));
        assertEquals(true, parsedFailure.get("truncated"));
    }

    @Test
    void omitsAttributesWhenDisabled() {
        JsonEventRecordEncoder withoutAttributes = new JsonEventRecordEncoder(0, false);
        Map<String, Object> parsed = parseObject(withoutAttributes.event(
                TestEvents.started("run-1", "exec-1", TestEvents.identity("a.B", "c"))));

        assertNull(parsed.get("attributes"));
    }

    @Test
    void writesEmptyAttributesObjectAndEmptyParametersArray() {
        Map<String, Object> parsed = parseObject(encoder.event(
                TestEvents.started("run-1", "exec-1", TestEvents.identity("a.B", "c"))));

        assertEquals(Map.of(), parsed.get("attributes"));
        assertEquals(java.util.List.of(), ((Map<?, ?>) parsed.get("test")).get("parameters"));
    }

    @Test
    void encodesParameters() {
        Map<?, ?> test = (Map<?, ?>) parseObject(encoder.event(TestEvents.started("run-1", "exec-1",
                TestEvents.identity("a.B", "c", "bob", "1")))).get("test");

        assertEquals(2, ((java.util.List<?>) test.get("parameters")).size());
    }

    @Test
    void encodesParametersWithEmptyValues() {
        TestIdentity identity = TestEvents.identity("a.B", "c");
        Map<?, ?> test = (Map<?, ?>) parseObject(encoder.event(
                TestEvents.started("run-1", "exec-1", identity))).get("test");
        assertEquals("0123456789abcdef", test.get("hash"));
    }

    @Test
    void encodesSummary() {
        RunTotals totals = new RunTotals();
        totals.accept(TestEvents.finished("run-1", "exec-1", TestEvents.identity("a.B", "c"),
                ExecutionStatus.PASSED, 5));
        totals.duplicateDropped();
        String record = encoder.summary("run-1", Instant.parse("2026-11-19T10:00:00.000Z"),
                Instant.parse("2026-11-19T10:00:01.500Z"), totals.snapshot());

        Map<String, Object> parsed = parseObject(record);
        assertEquals("run.summary", parsed.get("type"));
        assertEquals(1500L, parsed.get("durationMs"));
        Map<?, ?> serialized = (Map<?, ?>) parsed.get("totals");
        assertEquals(1L, serialized.get("events"));
        assertEquals(1L, serialized.get("tests"));
        assertEquals(1L, serialized.get("passed"));
        assertEquals(1L, serialized.get("droppedDuplicates"));
    }

    @Test
    void recordsTimestampToMillis() {
        String record = encoder.event(TestEvents.started("run-1", "exec-1", TestEvents.identity("a.B", "c")));
        assertEquals("2026-11-19T10:00:00.412Z", readLine(record, "timestamp"));
        assertNotNull(readLine(record, "executionId"));
    }
}
