package io.github.prmiguel.testng.live.core;

import io.github.prmiguel.testng.live.core.internal.CoreConfiguration;
import io.github.prmiguel.testng.live.core.model.ExecutionEvent;
import io.github.prmiguel.testng.live.core.spi.ExecutionReporter;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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
        if (closed.get()) {
            LOGGER.log(Level.WARNING, "Event published after event bus was closed: {0}", event.executionId());
            return;
        }
        for (ExecutionReporter reporter : reporters) {
            ExecutorService dispatcher = dispatchers.get(reporter);
            if (dispatcher == null || dispatcher.isShutdown()) {
                continue;
            }
            try {
                dispatcher.submit(() -> safeOnEvent(reporter, event));
            } catch (RejectedExecutionException e) {
                LOGGER.log(Level.WARNING, "Reporter dispatcher rejected event", e);
            }
        }
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
                safeClose(reporter);
            }
            dispatcher.shutdown();
        }

        for (Map.Entry<ExecutionReporter, ExecutorService> entry : dispatchers.entrySet()) {
            ExecutorService dispatcher = entry.getValue();
            try {
                if (!dispatcher.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    LOGGER.log(Level.WARNING, "Reporter dispatcher did not shut down in time: {0}",
                            entry.getKey().getClass().getName());
                    dispatcher.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                dispatcher.shutdownNow();
            }
        }
    }

    private void safeOnEvent(ExecutionReporter reporter, ExecutionEvent event) {
        try {
            reporter.onEvent(event);
        } catch (RuntimeException e) {
            LOGGER.log(Level.ERROR, "Reporter {0} failed to process event {1}: {2}",
                    reporter.getClass().getName(), event.executionId(), e.getMessage());
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
