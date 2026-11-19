package io.github.prmiguel.testng.live.core.internal;

import io.github.prmiguel.testng.live.core.model.TestIdentity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionAttemptTransitionTest {

    private static ExecutionAttempt attempt() {
        TestIdentity identity = new TestIdentity("com.acme.Sample", "runs", List.of(),
                "com.acme.Sample.runs[]", "0123456789abcdef", "com.acme.Sample.runs[]#0123456789abcdef");
        return new ExecutionAttempt(identity, "exec-1", 0, 0, Instant.now(), "suite", "test");
    }

    @Test
    void startedIsPublishedAtMostOnce() {
        ExecutionAttempt attempt = attempt();
        assertTrue(attempt.markStarted());
        assertFalse(attempt.markStarted(), "a duplicated STARTED must be suppressed");
    }

    @Test
    void inProgressRequiresStarted() {
        ExecutionAttempt attempt = attempt();
        assertFalse(attempt.markInProgress(), "IN_PROGRESS before STARTED must be rejected");
        assertTrue(attempt.markStarted());
        assertTrue(attempt.markInProgress());
        assertFalse(attempt.markInProgress());
    }

    @Test
    void finishedIsPublishedAtMostOnce() {
        ExecutionAttempt attempt = attempt();
        assertTrue(attempt.markStarted());
        assertTrue(attempt.markFinished());
        assertFalse(attempt.markFinished());
        assertTrue(attempt.isFinished());
    }

    @Test
    void nothingIsPublishedAfterFinish() {
        ExecutionAttempt attempt = attempt();
        assertTrue(attempt.markStarted());
        assertTrue(attempt.markFinished());
        assertFalse(attempt.markStarted());
        assertFalse(attempt.markInProgress());
    }
}
