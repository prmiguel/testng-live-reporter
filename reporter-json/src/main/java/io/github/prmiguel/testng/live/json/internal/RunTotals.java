package io.github.prmiguel.testng.live.json.internal;

import io.github.prmiguel.testng.live.core.model.ExecutionEvent;
import io.github.prmiguel.testng.live.core.model.ExecutionStatus;

/**
 * Counters accumulated while an execution stream is written.
 * <p>
 * Instances are confined to the reporter's single event dispatcher, so they need no
 * synchronization. Distinct executions are counted from terminal events only.
 */
public final class RunTotals {

    private long events;
    private long passed;
    private long failed;
    private long skipped;
    private long ignored;
    private long timeout;
    private long interrupted;
    private long error;
    private long undetermined;
    private long droppedDuplicates;
    private long filtered;
    private long writeErrors;

    /**
     * Records an accepted event.
     *
     * @param event accepted event
     */
    public void accept(ExecutionEvent event) {
        events++;
        switch (event.status()) {
            case PASSED -> passed++;
            case FAILED -> failed++;
            case SKIPPED -> skipped++;
            case IGNORED -> ignored++;
            case TIMEOUT -> timeout++;
            case INTERRUPTED -> interrupted++;
            case ERROR -> error++;
            case UNDETERMINED -> undetermined++;
        }
    }

    public void duplicateDropped() {
        droppedDuplicates++;
    }

    public void eventFiltered() {
        filtered++;
    }

    public void writeFailed() {
        writeErrors++;
    }

    /**
     * Returns the counted totals.
     */
    public Snapshot snapshot() {
        long tests = passed + failed + skipped + ignored + timeout + interrupted + error;
        return new Snapshot(events, tests, passed, failed, skipped, ignored, timeout, interrupted, error,
                undetermined, droppedDuplicates, filtered, writeErrors);
    }

    /**
     * Immutable view of the counted totals.
     */
    public record Snapshot(long events, long tests, long passed, long failed, long skipped, long ignored,
                           long timeout, long interrupted, long error, long undetermined,
                           long droppedDuplicates, long filtered, long writeErrors) {
    }
}
