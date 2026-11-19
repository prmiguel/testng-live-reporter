package io.github.prmiguel.testng.live.core.model;

import java.util.Objects;

/**
 * Information about a failed or errored execution.
 *
 * @param type       fully qualified throwable class name
 * @param message    throwable message (may be empty)
 * @param stackTrace full stack trace as a single string
 */
public record FailureInfo(String type, String message, String stackTrace) {

    public FailureInfo {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(stackTrace, "stackTrace must not be null");
    }
}
