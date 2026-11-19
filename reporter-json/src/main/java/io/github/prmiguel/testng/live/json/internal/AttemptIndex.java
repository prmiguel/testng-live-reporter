package io.github.prmiguel.testng.live.json.internal;

import io.github.prmiguel.testng.live.core.model.ExecutionEvent;
import io.github.prmiguel.testng.live.core.model.ExecutionState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-attempt view of a run, accumulated while the stream is written.
 * <p>
 * The consolidated document could re-read the NDJSON to build this, but that would need a
 * production JSON parser in a deliberately dependency-free module, only to re-read a file
 * this process just wrote. Collecting here instead keeps {@code events[]} and {@code tests[]}
 * fed by the same accepted-event path on the reporter's single dispatcher thread, so the two
 * cannot disagree.
 * <p>
 * Confined to that dispatcher, so no synchronization. Bounded by the number of attempts.
 */
public final class AttemptIndex {

    private final Map<String, ExecutionEvent> terminalByExecution = new LinkedHashMap<>();
    private final Map<String, Instant> startedAtByExecution = new HashMap<>();
    private final List<ExecutionEvent> configurationFailures = new ArrayList<>();

    /**
     * Records one accepted event.
     *
     * @param event event that was written to the stream
     */
    public void accept(ExecutionEvent event) {
        if (RunTotals.isConfigurationFailure(event)) {
            configurationFailures.add(event);
            return;
        }
        if (event.state() == ExecutionState.STARTED) {
            startedAtByExecution.putIfAbsent(event.executionId(), event.timestamp());
        }
        if (event.isTerminal()) {
            terminalByExecution.putIfAbsent(event.executionId(), event);
        }
    }

    /**
     * Returns one entry per attempt that reached a terminal state, in first-finished order.
     */
    public List<ExecutionEvent> tests() {
        return List.copyOf(terminalByExecution.values());
    }

    /**
     * Returns the configuration-method failures seen in this run.
     */
    public List<ExecutionEvent> configurationFailures() {
        return List.copyOf(configurationFailures);
    }

    /**
     * Returns when the given attempt was first seen starting, if it was.
     *
     * @param executionId attempt identifier
     * @return start timestamp, or null when the attempt never emitted STARTED
     */
    public Instant startedAt(String executionId) {
        return startedAtByExecution.get(executionId);
    }

    /**
     * Drops all collected state. Called when the report is closed so a reused reporter
     * instance starts clean.
     */
    public void clear() {
        terminalByExecution.clear();
        startedAtByExecution.clear();
        configurationFailures.clear();
    }
}
