package io.github.prmiguel.testng.live.core.internal;

import io.github.prmiguel.testng.live.core.ExecutionEventBus;
import io.github.prmiguel.testng.live.core.model.ExecutionEvent;
import io.github.prmiguel.testng.live.core.model.ExecutionState;
import io.github.prmiguel.testng.live.core.model.ExecutionStatus;
import io.github.prmiguel.testng.live.core.model.FailureInfo;
import io.github.prmiguel.testng.live.core.model.TestIdentity;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Internal factory for building valid {@link ExecutionEvent} instances.
 * <p>
 * Suite and test names are read from the attempt rather than held here: under
 * {@code parallel="tests"} several contexts interleave on the same listener, so a single
 * mutable field would attribute events to the wrong {@code <test>}.
 */
public final class ExecutionEventFactory {

    private final String runId;
    private final ExecutionEventBus bus;

    public ExecutionEventFactory(String runId, ExecutionEventBus bus) {
        this.runId = runId;
        this.bus = bus;
    }

    public ExecutionEvent discovered(TestIdentity identity, String suiteName, String testName) {
        return terminalOrPending(identity, null, ExecutionState.DISCOVERED, ExecutionStatus.UNDETERMINED,
                null, null, Collections.emptyMap(), suiteName, testName, 0, 0);
    }

    public ExecutionEvent ignored(TestIdentity identity, String suiteName, String testName) {
        return terminalOrPending(identity, null, ExecutionState.FINISHED, ExecutionStatus.IGNORED,
                Duration.ZERO, null, Collections.emptyMap(), suiteName, testName, 0, 0);
    }

    public ExecutionEvent started(ExecutionAttempt attempt) {
        return create(attempt, ExecutionState.STARTED, ExecutionStatus.UNDETERMINED, null, null, Collections.emptyMap());
    }

    public ExecutionEvent inProgress(ExecutionAttempt attempt) {
        return create(attempt, ExecutionState.IN_PROGRESS, ExecutionStatus.UNDETERMINED, null, null, Collections.emptyMap());
    }

    /**
     * Builds a terminal event from TestNG's own timing rather than the listener's, so
     * {@code durationMs} matches what TestNG reports for the invocation. Falls back to the
     * attempt's start time when TestNG does not expose timestamps.
     *
     * @param attempt     attempt being finished
     * @param status      outcome
     * @param failure     failure details, or null
     * @param attributes  contextual attributes
     * @param startMillis TestNG start timestamp, or a non-positive value when unavailable
     * @param endMillis   TestNG end timestamp, or a non-positive value when unavailable
     * @return the terminal event
     */
    public ExecutionEvent finished(ExecutionAttempt attempt, ExecutionStatus status, FailureInfo failure,
                                   Map<String, String> attributes, long startMillis, long endMillis) {
        Duration duration = testNgDuration(startMillis, endMillis);
        if (duration == null) {
            duration = Duration.between(attempt.startedAt(), Instant.now());
        }
        return create(attempt, ExecutionState.FINISHED, status, duration, failure, attributes);
    }

    private static Duration testNgDuration(long startMillis, long endMillis) {
        if (startMillis <= 0 || endMillis <= 0 || endMillis < startMillis) {
            return null;
        }
        return Duration.ofMillis(endMillis - startMillis);
    }

    private ExecutionEvent create(ExecutionAttempt attempt, ExecutionState state, ExecutionStatus status,
                                  Duration duration, FailureInfo failure, Map<String, String> attributes) {
        return terminalOrPending(attempt.testIdentity(), attempt, state, status, duration, failure, attributes,
                attempt.suiteName(), attempt.testName(), attempt.invocationIndex(), attempt.retryAttempt());
    }

    private ExecutionEvent terminalOrPending(TestIdentity identity, ExecutionAttempt attempt, ExecutionState state,
                                             ExecutionStatus status, Duration duration, FailureInfo failure,
                                             Map<String, String> attributes, String suiteName, String testName,
                                             int invocationIndex, int retryAttempt) {
        Thread thread = Thread.currentThread();
        return new ExecutionEvent(
                ExecutionEvent.CURRENT_SCHEMA_VERSION,
                bus.nextSequence(),
                runId,
                attempt != null ? attempt.executionId() : nonAttemptExecutionId(identity, state),
                identity,
                state,
                status,
                Instant.now(),
                duration,
                suiteName,
                testName,
                thread.getId(),
                thread.getName(),
                invocationIndex,
                retryAttempt,
                failure,
                attributes
        );
    }

    /**
     * Builds an identifier for a record that is not an execution attempt. Keeping it
     * distinct from attempt ids means "one id per attempt" still holds for consumers.
     */
    private String nonAttemptExecutionId(TestIdentity identity, ExecutionState state) {
        return runId + "-" + identity.hash() + "-" + state.name().toLowerCase();
    }

    public static FailureInfo failureInfo(Throwable throwable) {
        if (throwable == null) {
            return new FailureInfo("java.lang.Throwable", "<no throwable provided>", "");
        }
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        return new FailureInfo(throwable.getClass().getName(),
                throwable.getMessage() == null ? "" : throwable.getMessage(),
                sw.toString());
    }
}
