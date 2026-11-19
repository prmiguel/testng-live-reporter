package io.github.prmiguel.testng.live.json.spi;

import io.github.prmiguel.testng.live.core.model.ExecutionEvent;
import io.github.prmiguel.testng.live.json.JsonReporterConfiguration;
import io.github.prmiguel.testng.live.json.internal.JsonEventRecordEncoder;
import io.github.prmiguel.testng.live.json.internal.RunTotals;

import java.time.Instant;

/**
 * Encodes reporter records into their on-disk representation.
 * <p>
 * Keeping serialization behind this seam lets sibling reporters (SQLite, cached DB, CTRF)
 * reuse the same lifecycle, filtering and buffering without re-implementing them.
 */
public interface EventRecordEncoder {

    /**
     * Encodes the run header record written before any event.
     *
     * @param runId     run identifier
     * @param startedAt time the reporter opened its output
     * @return a single-line record
     */
    String runStarted(String runId, Instant startedAt);

    /**
     * Encodes a single execution event.
     *
     * @param event the event
     * @return a single-line record
     */
    String event(ExecutionEvent event);

    /**
     * Encodes the run summary written when the stream ends.
     *
     * @param runId      run identifier
     * @param startedAt  time the reporter opened its output
     * @param finishedAt time the summary was produced
     * @param totals     counted totals
     * @return a single-line record
     */
    String summary(String runId, Instant startedAt, Instant finishedAt, RunTotals.Snapshot totals);

    /**
     * Builds an encoder from resolved configuration.
     *
     * @param configuration resolved configuration
     * @return encoder
     */
    static EventRecordEncoder create(JsonReporterConfiguration configuration) {
        return new JsonEventRecordEncoder(configuration.maxStackTraceChars(), configuration.includeAttributes());
    }
}
