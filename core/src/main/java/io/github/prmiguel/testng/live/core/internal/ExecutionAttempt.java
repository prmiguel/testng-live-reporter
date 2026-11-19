package io.github.prmiguel.testng.live.core.internal;

import io.github.prmiguel.testng.live.core.model.TestIdentity;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mutable internal tracker for a single execution attempt.
 */
public final class ExecutionAttempt {

    private final TestIdentity testIdentity;
    private final String executionId;
    private final int invocationIndex;
    private final int retryAttempt;
    private final Instant startedAt;
    private final AtomicBoolean inProgressEmitted = new AtomicBoolean(false);
    private final AtomicBoolean finished = new AtomicBoolean(false);

    public ExecutionAttempt(TestIdentity testIdentity, String executionId, int invocationIndex, int retryAttempt, Instant startedAt) {
        this.testIdentity = testIdentity;
        this.executionId = executionId;
        this.invocationIndex = invocationIndex;
        this.retryAttempt = retryAttempt;
        this.startedAt = startedAt;
    }

    public TestIdentity testIdentity() {
        return testIdentity;
    }

    public String executionId() {
        return executionId;
    }

    public int invocationIndex() {
        return invocationIndex;
    }

    public int retryAttempt() {
        return retryAttempt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public boolean markInProgress() {
        return inProgressEmitted.compareAndSet(false, true);
    }

    public boolean markFinished() {
        return finished.compareAndSet(false, true);
    }
}
