package io.github.prmiguel.testng.live.core.model;

/**
 * Lifecycle state of a single test execution attempt.
 */
public enum ExecutionState {
    DISCOVERED,
    QUEUED,
    STARTED,
    IN_PROGRESS,
    FINISHED
}
