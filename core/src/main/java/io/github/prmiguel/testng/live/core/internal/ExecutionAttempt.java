package io.github.prmiguel.testng.live.core.internal;

import io.github.prmiguel.testng.live.core.model.TestIdentity;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mutable internal tracker for a single execution attempt.
 * <p>
 * Every lifecycle transition is guarded by a compare-and-set so a duplicated TestNG
 * callback cannot publish the same state twice, and a late callback cannot resurrect a
 * finished attempt. The guards return {@code false} instead of throwing: a misbehaving
 * callback must never fail the test run.
 */
public final class ExecutionAttempt {

    private final TestIdentity testIdentity;
    private final String executionId;
    private final int invocationIndex;
    private final int retryAttempt;
    private final Instant startedAt;
    private final String suiteName;
    private final String testName;
    private final AtomicBoolean startedEmitted = new AtomicBoolean(false);
    private final AtomicBoolean inProgressEmitted = new AtomicBoolean(false);
    private final AtomicBoolean finished = new AtomicBoolean(false);

    public ExecutionAttempt(TestIdentity testIdentity, String executionId, int invocationIndex, int retryAttempt,
                            Instant startedAt, String suiteName, String testName) {
        this.testIdentity = testIdentity;
        this.executionId = executionId;
        this.invocationIndex = invocationIndex;
        this.retryAttempt = retryAttempt;
        this.startedAt = startedAt;
        this.suiteName = suiteName;
        this.testName = testName;
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

    public String suiteName() {
        return suiteName;
    }

    public String testName() {
        return testName;
    }

    /**
     * Marks the attempt as started. Succeeds at most once, and never after it finished.
     *
     * @return true if the STARTED state should be published
     */
    public boolean markStarted() {
        if (finished.get()) {
            return false;
        }
        return startedEmitted.compareAndSet(false, true);
    }

    /**
     * Marks the attempt as actively executing. Succeeds at most once, only after
     * {@link #markStarted()}, and never after it finished.
     *
     * @return true if the IN_PROGRESS state should be published
     */
    public boolean markInProgress() {
        if (finished.get() || !startedEmitted.get()) {
            return false;
        }
        return inProgressEmitted.compareAndSet(false, true);
    }

    /**
     * Marks the attempt as finished. Succeeds at most once.
     *
     * @return true if the FINISHED state should be published
     */
    public boolean markFinished() {
        return finished.compareAndSet(false, true);
    }

    public boolean isFinished() {
        return finished.get();
    }
}
