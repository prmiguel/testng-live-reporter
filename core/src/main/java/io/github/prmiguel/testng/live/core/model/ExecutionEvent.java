package io.github.prmiguel.testng.live.core.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable record of a test execution lifecycle event.
 *
 * @param schemaVersion schema version of the event structure
 * @param sequence      monotonically increasing publication sequence
 * @param runId         identifier for the current TestNG run
 * @param executionId   identifier for this single execution attempt
 * @param testIdentity  stable identity of the test method
 * @param state         lifecycle state
 * @param status        outcome status
 * @param timestamp     UTC timestamp of the event
 * @param duration      elapsed duration since the attempt started (null until finished)
 * @param suiteName     TestNG suite name
 * @param testName      TestNG &lt;test&gt; name
 * @param threadId      thread ID that produced the event
 * @param threadName    thread name that produced the event
 * @param invocationIndex data-provider invocation index (best effort)
 * @param retryAttempt  retry attempt number (0 for first attempt)
 * @param failure       failure details (null unless terminal failure)
 * @param attributes    immutable contextual attributes
 */
public record ExecutionEvent(
        int schemaVersion,
        long sequence,
        String runId,
        String executionId,
        TestIdentity testIdentity,
        ExecutionState state,
        ExecutionStatus status,
        Instant timestamp,
        Duration duration,
        String suiteName,
        String testName,
        long threadId,
        String threadName,
        int invocationIndex,
        int retryAttempt,
        FailureInfo failure,
        Map<String, String> attributes
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public ExecutionEvent {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(executionId, "executionId must not be null");
        Objects.requireNonNull(testIdentity, "testIdentity must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(suiteName, "suiteName must not be null");
        Objects.requireNonNull(testName, "testName must not be null");
        Objects.requireNonNull(threadName, "threadName must not be null");
        Objects.requireNonNull(attributes, "attributes must not be null");
        attributes = Collections.unmodifiableMap(Map.copyOf(attributes));

        if (state == ExecutionState.FINISHED) {
            if (status == ExecutionStatus.UNDETERMINED) {
                throw new IllegalArgumentException("A FINISHED event must have a terminal status");
            }
            Objects.requireNonNull(duration, "duration must not be null for FINISHED events");
        } else {
            if (status != ExecutionStatus.UNDETERMINED) {
                throw new IllegalArgumentException("Only FINISHED events may have a terminal status");
            }
            if (duration != null) {
                throw new IllegalArgumentException("duration must be null until the event is FINISHED");
            }
            if (failure != null) {
                throw new IllegalArgumentException("failure must be null until the event is FINISHED");
            }
        }
    }

    /**
     * Returns true if this event represents a terminal state.
     */
    public boolean isTerminal() {
        return state == ExecutionState.FINISHED;
    }
}
