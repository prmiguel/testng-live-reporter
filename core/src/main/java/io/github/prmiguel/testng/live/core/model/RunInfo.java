package io.github.prmiguel.testng.live.core.model;

import java.time.Instant;

/**
 * Run-level information passed to reporters outside the per-test event stream.
 * <p>
 * {@link ExecutionEvent} always carries a test identity, so it cannot represent a
 * run boundary; reporters that want one receive it through
 * {@code ExecutionReporter.onRunStarted} / {@code onRunFinished} instead.
 *
 * @param runId      identifier for the current TestNG run
 * @param startedAt  when the run started, or null when the run has not started
 * @param finishedAt when the run finished, or null while it is still running
 */
public record RunInfo(String runId, Instant startedAt, Instant finishedAt) {
}
