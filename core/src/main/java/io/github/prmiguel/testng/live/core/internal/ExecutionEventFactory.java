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
 */
public final class ExecutionEventFactory {

    private final String runId;
    private final String suiteName;
    private final String testName;
    private final ExecutionEventBus bus;

    public ExecutionEventFactory(String runId, String suiteName, String testName, ExecutionEventBus bus) {
        this.runId = runId;
        this.suiteName = suiteName;
        this.testName = testName;
        this.bus = bus;
    }

    public ExecutionEvent started(ExecutionAttempt attempt) {
        return create(attempt, ExecutionState.STARTED, ExecutionStatus.UNDETERMINED, null, null, Collections.emptyMap());
    }

    public ExecutionEvent inProgress(ExecutionAttempt attempt) {
        return create(attempt, ExecutionState.IN_PROGRESS, ExecutionStatus.UNDETERMINED, null, null, Collections.emptyMap());
    }

    public ExecutionEvent finished(ExecutionAttempt attempt, ExecutionStatus status, FailureInfo failure, Map<String, String> attributes) {
        Duration duration = Duration.between(attempt.startedAt(), Instant.now());
        return create(attempt, ExecutionState.FINISHED, status, duration, failure, attributes);
    }

    private ExecutionEvent create(ExecutionAttempt attempt, ExecutionState state, ExecutionStatus status,
                                  Duration duration, FailureInfo failure, Map<String, String> attributes) {
        TestIdentity identity = attempt.testIdentity();
        Thread thread = Thread.currentThread();
        return new ExecutionEvent(
                ExecutionEvent.CURRENT_SCHEMA_VERSION,
                bus.nextSequence(),
                runId,
                attempt.executionId(),
                identity,
                state,
                status,
                Instant.now(),
                duration,
                suiteName,
                testName,
                thread.getId(),
                thread.getName(),
                attempt.invocationIndex(),
                attempt.retryAttempt(),
                failure,
                attributes
        );
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
