package io.github.prmiguel.testng.live.core;

import io.github.prmiguel.testng.live.core.internal.CoreConfiguration;
import io.github.prmiguel.testng.live.core.model.ExecutionEvent;
import io.github.prmiguel.testng.live.core.model.ExecutionState;
import io.github.prmiguel.testng.live.core.model.ExecutionStatus;
import io.github.prmiguel.testng.live.core.model.TestIdentity;
import io.github.prmiguel.testng.live.core.spi.ExecutionReporter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionEventBusShutdownTest {

    private static ExecutionEvent event(long sequence) {
        TestIdentity identity = new TestIdentity("com.acme.Sample", "runs", List.of(),
                "com.acme.Sample.runs[]", "0123456789abcdef", "com.acme.Sample.runs[]#0123456789abcdef");
        return new ExecutionEvent(1, sequence, "run-1", "exec-" + sequence, identity,
                ExecutionState.FINISHED, ExecutionStatus.PASSED, Instant.now(), Duration.ofMillis(1),
                "suite", "test", 1, "main", 0, 0, null, Map.of());
    }

    @Test
    void deliversQueuedEventsBeforeClosing() {
        AtomicInteger delivered = new AtomicInteger();
        ExecutionReporter slowReporter = event -> {
            delivered.incrementAndGet();
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        ExecutionEventBus bus = new ExecutionEventBus(List.of(slowReporter),
                new CoreConfiguration("run-1", Duration.ofSeconds(5)));
        for (int i = 1; i <= 25; i++) {
            bus.publish(event(i));
        }
        bus.close();

        assertEquals(25, delivered.get(), "close() must drain every queued event");
    }

    @Test
    void progressRecordsAreNotDuplicated() {
        AtomicInteger seen = new AtomicInteger();
        ExecutionReporter reporter = event -> seen.incrementAndGet();

        ExecutionEventBus bus = new ExecutionEventBus(List.of(reporter),
                new CoreConfiguration("run-1", Duration.ofSeconds(5)));
        bus.publish(event(1));
        bus.publish(event(2));
        bus.close();

        assertEquals(2, seen.get());
        assertTrue(seen.get() == 2, "no event may be replayed by the shutdown path");
    }
}
