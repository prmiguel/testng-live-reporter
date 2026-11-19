package io.github.prmiguel.testng.live.core.model;

/**
 * Outcome status of a single test execution attempt.
 */
public enum ExecutionStatus {
    UNDETERMINED,
    PASSED,
    FAILED,
    SKIPPED,
    IGNORED,
    TIMEOUT,
    INTERRUPTED,
    ERROR
}
