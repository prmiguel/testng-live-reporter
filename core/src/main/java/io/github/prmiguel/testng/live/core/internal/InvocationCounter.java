package io.github.prmiguel.testng.live.core.internal;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Assigns monotonic invocation indices per stable test identity hash.
 */
public final class InvocationCounter {

    private final ConcurrentMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    public int nextIndex(String identityHash) {
        return counters.computeIfAbsent(identityHash, k -> new AtomicInteger(0)).getAndIncrement();
    }
}
