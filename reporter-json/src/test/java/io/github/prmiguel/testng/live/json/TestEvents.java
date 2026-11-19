package io.github.prmiguel.testng.live.json;

import io.github.prmiguel.testng.live.core.model.CanonicalParameter;
import io.github.prmiguel.testng.live.core.model.ExecutionEvent;
import io.github.prmiguel.testng.live.core.model.ExecutionState;
import io.github.prmiguel.testng.live.core.model.ExecutionStatus;
import io.github.prmiguel.testng.live.core.model.FailureInfo;
import io.github.prmiguel.testng.live.core.model.TestIdentity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Builds valid {@link ExecutionEvent} instances for tests without needing a TestNG run.
 */
public final class TestEvents {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private TestEvents() {
    }

    public static TestIdentity identity(String className, String methodName, String... parameterValues) {
        List<CanonicalParameter> parameters = java.util.Arrays.stream(parameterValues)
                .map(value -> new CanonicalParameter("java.lang.String", "java.lang.String", value))
                .toList();
        String readableKey = className + "." + methodName + "[]";
        String hash = "0123456789abcdef";
        return new TestIdentity(className, methodName, parameters, readableKey, hash, readableKey + "#" + hash);
    }

    public static ExecutionEvent started(String runId, String executionId, TestIdentity identity) {
        return create(runId, executionId, identity, ExecutionState.STARTED, ExecutionStatus.UNDETERMINED,
                null, null, Map.of());
    }

    public static ExecutionEvent inProgress(String runId, String executionId, TestIdentity identity) {
        return create(runId, executionId, identity, ExecutionState.IN_PROGRESS, ExecutionStatus.UNDETERMINED,
                null, null, Map.of());
    }

    public static ExecutionEvent finished(String runId, String executionId, TestIdentity identity,
                                   ExecutionStatus status, long durationMillis) {
        return create(runId, executionId, identity, ExecutionState.FINISHED, status,
                Duration.ofMillis(durationMillis), null, Map.of());
    }

    public static ExecutionEvent finishedFailed(String runId, String executionId, TestIdentity identity,
                                        FailureInfo failure) {
        return create(runId, executionId, identity, ExecutionState.FINISHED, ExecutionStatus.FAILED,
                Duration.ofMillis(12), failure, Map.of());
    }

    public static ExecutionEvent create(String runId, String executionId, TestIdentity identity,
                                 ExecutionState state, ExecutionStatus status, Duration duration,
                                 FailureInfo failure, Map<String, String> attributes) {
        return new ExecutionEvent(
                ExecutionEvent.CURRENT_SCHEMA_VERSION,
                SEQUENCE.incrementAndGet(),
                runId,
                executionId,
                identity,
                state,
                status,
                Instant.parse("2026-11-19T10:00:00.412Z"),
                duration,
                "smoke-suite",
                "smoke-test",
                21L,
                "TestNG-test-1",
                0,
                0,
                failure,
                attributes
        );
    }
}
