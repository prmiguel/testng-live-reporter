package io.github.prmiguel.testng.live.json.internal;

import io.github.prmiguel.testng.live.core.internal.ExecutionEventFactory;
import io.github.prmiguel.testng.live.core.model.ExecutionEvent;
import io.github.prmiguel.testng.live.core.model.ExecutionStatus;

/**
 * Counters accumulated while an execution stream is written.
 * <p>
 * Instances are confined to the reporter's single event dispatcher, so they need no
 * synchronization. Distinct executions are counted from terminal events only.
 * <p>
 * Configuration-method failures are reported as {@code test.event} records with a
 * {@code phase=configuration} attribute. They are counted separately and are deliberately
 * excluded from the status counters and from {@code tests}: the affected test already
 * contributes a {@code SKIPPED} record, so counting the cause as well would inflate the
 * suite totals.
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
    private long configurationFailures;
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
        if (isConfigurationFailure(event)) {
            configurationFailures++;
            return;
        }
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

    /**
     * Returns true when the event describes a configuration method failure rather than a
     * test outcome.
     *
     * @param event event to inspect
     * @return true for configuration failures
     */
    public static boolean isConfigurationFailure(ExecutionEvent event) {
        return ExecutionEventFactory.PHASE_CONFIGURATION.equals(
                event.attributes().get(ExecutionEventFactory.PHASE_ATTRIBUTE));
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
                undetermined, configurationFailures, droppedDuplicates, filtered, writeErrors);
    }

    /**
     * Immutable view of the counted totals.
     */
    public record Snapshot(long events, long tests, long passed, long failed, long skipped, long ignored,
                           long timeout, long interrupted, long error, long undetermined,
                           long configurationFailures, long droppedDuplicates, long filtered, long writeErrors) {
    }
}
