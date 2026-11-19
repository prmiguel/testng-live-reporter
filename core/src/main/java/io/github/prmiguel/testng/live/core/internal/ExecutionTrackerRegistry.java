package io.github.prmiguel.testng.live.core.internal;

import io.github.prmiguel.testng.live.core.TestRunIdGenerator;
import io.github.prmiguel.testng.live.core.model.TestIdentity;
import org.testng.ITestResult;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Tracks execution attempts across TestNG callbacks.
 */
public final class ExecutionTrackerRegistry {

    private static final String ATTRIBUTE_KEY = "testng.live.executionAttempt";

    private final ConcurrentMap<String, ExecutionAttempt> attempts = new ConcurrentHashMap<>();
    private final TestRunIdGenerator idGenerator;
    private final String runId;

    public ExecutionTrackerRegistry(TestRunIdGenerator idGenerator, String runId) {
        this.idGenerator = idGenerator;
        this.runId = runId;
    }

    /**
     * Registers a new execution attempt from a TestNG test-start callback.
     */
    public ExecutionAttempt begin(ITestResult result, int invocationIndex, int retryAttempt) {
        TestIdentity identity = idGenerator.createIdentity(result);
        String executionId = idGenerator.createExecutionId(runId, identity, invocationIndex, retryAttempt, Thread.currentThread().getId());

        ExecutionAttempt attempt = new ExecutionAttempt(identity, executionId, invocationIndex, retryAttempt, Instant.now());
        attempts.put(executionId, attempt);
        result.setAttribute(ATTRIBUTE_KEY, attempt);
        return attempt;
    }

    /**
     * Looks up the execution attempt associated with the given result.
     */
    public ExecutionAttempt get(ITestResult result) {
        Object attribute = result.getAttribute(ATTRIBUTE_KEY);
        if (attribute instanceof ExecutionAttempt attempt) {
            return attempt;
        }
        return null;
    }
}
