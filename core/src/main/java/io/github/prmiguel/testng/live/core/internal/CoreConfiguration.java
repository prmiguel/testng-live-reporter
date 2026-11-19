package io.github.prmiguel.testng.live.core.internal;

import java.time.Duration;
import java.util.UUID;

/**
 * Core runtime configuration read from system properties.
 */
public final class CoreConfiguration {

    public static final String RUN_ID_PROPERTY = "testng.live.run-id";
    public static final String SHUTDOWN_TIMEOUT_PROPERTY = "testng.live.shutdown-timeout-seconds";

    private final String runId;
    private final Duration shutdownTimeout;

    public CoreConfiguration() {
        this.runId = resolveRunId();
        this.shutdownTimeout = resolveShutdownTimeout();
    }

    public CoreConfiguration(String runId, Duration shutdownTimeout) {
        this.runId = runId;
        this.shutdownTimeout = shutdownTimeout;
    }

    public String runId() {
        return runId;
    }

    public Duration shutdownTimeout() {
        return shutdownTimeout;
    }

    private static String resolveRunId() {
        String value = System.getProperty(RUN_ID_PROPERTY, System.getenv("TESTNG_LIVE_RUN_ID"));
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        return UUID.randomUUID().toString();
    }

    private static Duration resolveShutdownTimeout() {
        String value = System.getProperty(SHUTDOWN_TIMEOUT_PROPERTY, "30");
        try {
            int seconds = Integer.parseInt(value.trim());
            return Duration.ofSeconds(Math.max(1, seconds));
        } catch (NumberFormatException e) {
            return Duration.ofSeconds(30);
        }
    }
}
