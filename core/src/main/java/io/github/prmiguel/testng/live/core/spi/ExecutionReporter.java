package io.github.prmiguel.testng.live.core.spi;

import io.github.prmiguel.testng.live.core.model.ExecutionEvent;

/**
 * Pluggable reporter that consumes execution events emitted by the core listener.
 * <p>
 * Implementations are discovered through {@link java.util.ServiceLoader}. Each
 * discovered reporter receives events on its own dedicated dispatcher thread, so
 * implementations do not need to synchronize event handling internally.
 */
public interface ExecutionReporter extends AutoCloseable {

    /**
     * Process a single execution event.
     *
     * @param event the event
     */
    void onEvent(ExecutionEvent event);

    /**
     * Flush any buffered output.
     */
    default void flush() {
        // no-op by default
    }

    /**
     * Release resources. Called once after the event stream has ended.
     */
    @Override
    default void close() {
        // no-op by default
    }
}
