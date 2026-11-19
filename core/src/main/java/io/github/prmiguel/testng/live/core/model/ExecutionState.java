package io.github.prmiguel.testng.live.core.model;

/**
 * Lifecycle state of a single test execution attempt.
 * <p>
 * {@code QUEUED} is reserved and never emitted: TestNG exposes no callback between method
 * discovery and {@code onTestStart}, so there is no honest moment to publish it.
 */
public enum ExecutionState {
    DISCOVERED,
    QUEUED,
    STARTED,
    IN_PROGRESS,
    FINISHED
}
