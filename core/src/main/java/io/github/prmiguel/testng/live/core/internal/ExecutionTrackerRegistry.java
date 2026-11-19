package io.github.prmiguel.testng.live.core.internal;

import io.github.prmiguel.testng.live.core.TestRunIdGenerator;
import io.github.prmiguel.testng.live.core.model.TestIdentity;
import org.testng.ITestContext;
import org.testng.ITestResult;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks execution attempts across TestNG callbacks.
 * <p>
 * Only in-flight attempts are retained: {@link #complete(ITestResult)} evicts an attempt
 * once its terminal event has been published, so long runs do not accumulate state.
 */
public final class ExecutionTrackerRegistry {

    private static final String ATTRIBUTE_KEY = "testng.live.executionAttempt";

    private final ConcurrentMap<String, ExecutionAttempt> attempts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> retries = new ConcurrentHashMap<>();
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
        ITestContext context = result.getTestContext();
        String suiteName = context != null && context.getSuite() != null ? context.getSuite().getName() : "unknown";
        String testName = context != null ? context.getName() : "unknown";

        ExecutionAttempt attempt = new ExecutionAttempt(identity, executionId, invocationIndex, retryAttempt,
                Instant.now(), suiteName, testName);
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

    /**
     * Forgets a finished attempt. Must be called only after its terminal event has been
     * published, so a retry reusing the same identity cannot observe a stale attempt.
     */
    public void complete(ITestResult result) {
        ExecutionAttempt attempt = get(result);
        if (attempt != null) {
            attempts.remove(attempt.executionId());
            result.removeAttribute(ATTRIBUTE_KEY);
        }
    }

    /**
     * Records that TestNG retried the given identity, so its next attempt reports a higher
     * retry number. Called from the finish callback, which is the only point where TestNG
     * has already decided to retry.
     */
    public void markRetried(TestIdentity identity) {
        retries.computeIfAbsent(identity.hash(), key -> new AtomicInteger(0)).incrementAndGet();
    }

    /**
     * Returns the retry attempt number for the next attempt of the given identity:
     * 0 for a first attempt, then 1, 2, ... as retries accumulate.
     */
    public int retryAttempt(TestIdentity identity) {
        AtomicInteger counter = retries.get(identity.hash());
        return counter == null ? 0 : counter.get();
    }

    /**
     * Number of attempts currently tracked. Exposed so leaks are observable in tests.
     */
    public int inFlight() {
        return attempts.size();
    }
}
