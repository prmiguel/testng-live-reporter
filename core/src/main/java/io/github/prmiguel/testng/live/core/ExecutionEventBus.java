package io.github.prmiguel.testng.live.core;

import io.github.prmiguel.testng.live.core.internal.CoreConfiguration;
import io.github.prmiguel.testng.live.core.model.ExecutionEvent;
import io.github.prmiguel.testng.live.core.model.RunInfo;
import io.github.prmiguel.testng.live.core.spi.ExecutionReporter;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Thread-safe event dispatcher that fan-out execution events to registered reporters.
 * <p>
 * Each reporter receives events on its own dedicated virtual thread, so a slow or
 * failing reporter never blocks TestNG worker threads.
 */
public final class ExecutionEventBus implements AutoCloseable {

    private static final Logger LOGGER = System.getLogger(ExecutionEventBus.class.getName());

    private final List<ExecutionReporter> reporters;
    private final Map<ExecutionReporter, ExecutorService> dispatchers = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Duration shutdownTimeout;

    public ExecutionEventBus(List<ExecutionReporter> reporters, CoreConfiguration configuration) {
        this.reporters = List.copyOf(reporters);
        this.shutdownTimeout = configuration.shutdownTimeout();
        int counter = 0;
        for (ExecutionReporter reporter : reporters) {
            String name = reporter.getClass().getSimpleName() + "-" + counter++;
            ExecutorService executor = Executors.newSingleThreadExecutor(
                    Thread.ofVirtual().name("live-reporter-" + name + "-", 0).factory());
            dispatchers.put(reporter, executor);
        }
    }

    /**
     * Publishes an event to every registered reporter.
     *
     * @param event the event to publish
     */
    public void publish(ExecutionEvent event) {
        dispatch(reporter -> reporter.onEvent(event), "process event " + event.executionId());
    }

    /**
     * Tells every reporter that the run started. Queued ahead of any execution event.
     *
     * @param info run identity and start time
     */
    public void publishRunStarted(RunInfo info) {
        dispatch(reporter -> reporter.onRunStarted(info), "process run start");
    }

    /**
     * Tells every reporter that the run finished. Queued after the last execution event,
     * and before the terminal flush/close.
     *
     * @param info run identity and finish time
     */
    public void publishRunFinished(RunInfo info) {
        dispatch(reporter -> reporter.onRunFinished(info), "process run finish");
    }

    /**
     * Returns the next monotonic sequence number.
     *
     * @return sequence
     */
    public long nextSequence() {
        return sequence.incrementAndGet();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        for (ExecutionReporter reporter : reporters) {
            ExecutorService dispatcher = dispatchers.get(reporter);
            if (dispatcher == null || dispatcher.isShutdown()) {
                safeFlush(reporter);
                safeClose(reporter);
                continue;
            }
            try {
                dispatcher.submit(() -> {
                    safeFlush(reporter);
                    safeClose(reporter);
                });
            } catch (RejectedExecutionException e) {
                LOGGER.log(Level.WARNING, "Could not submit final flush/close task", e);
                safeFlush(reporter);
                safeClose(reporter);
            }
            dispatcher.shutdown();
        }

        for (Map.Entry<ExecutionReporter, ExecutorService> entry : dispatchers.entrySet()) {
            ExecutorService dispatcher = entry.getValue();
            String reporterName = entry.getKey().getClass().getName();
            try {
                if (dispatcher.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    continue;
                }
                LOGGER.log(Level.WARNING,
                        "Reporter dispatcher did not drain within {0}: {1}; waiting once more",
                        shutdownTimeout, reporterName);
                if (!dispatcher.awaitTermination(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS)) {
                    int pending = dispatcher.shutdownNow().size();
                    LOGGER.log(Level.WARNING,
                            "Reporter {0} did not finish in time; discarded {1} queued task(s)", reporterName, pending);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                int pending = dispatcher.shutdownNow().size();
                LOGGER.log(Level.WARNING,
                        "Interrupted while waiting for {0}; discarded {1} queued task(s)", reporterName, pending);
            }
        }
    }

    private void dispatch(Consumer<ExecutionReporter> action, String description) {
        if (closed.get()) {
            LOGGER.log(Level.WARNING, "Event published after event bus was closed: {0}", description);
            return;
        }
        for (ExecutionReporter reporter : reporters) {
            ExecutorService dispatcher = dispatchers.get(reporter);
            if (dispatcher == null || dispatcher.isShutdown()) {
                continue;
            }
            try {
                dispatcher.submit(() -> {
                    try {
                        action.accept(reporter);
                    } catch (RuntimeException e) {
                        LOGGER.log(Level.ERROR, "Reporter {0} failed to {1}: {2}",
                                reporter.getClass().getName(), description, e.getMessage());
                    }
                });
            } catch (RejectedExecutionException e) {
                LOGGER.log(Level.WARNING, "Reporter dispatcher rejected {0}", description);
            }
        }
    }

    private void safeFlush(ExecutionReporter reporter) {
        try {
            reporter.flush();
        } catch (RuntimeException e) {
            LOGGER.log(Level.ERROR, "Reporter {0} failed to flush: {1}",
                    reporter.getClass().getName(), e.getMessage());
        }
    }

    private void safeClose(ExecutionReporter reporter) {
        try {
            reporter.close();
        } catch (RuntimeException e) {
            LOGGER.log(Level.ERROR, "Reporter {0} failed to close: {1}",
                    reporter.getClass().getName(), e.getMessage());
        }
    }
}
