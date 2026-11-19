package io.github.prmiguel.testng.live.json.fixtures;

import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fails the first attempt only, so the fixture exercises the retry path.
 */
public class OnceRetryAnalyzer implements IRetryAnalyzer {

    private static final AtomicBoolean HAS_RETRIED = new AtomicBoolean(false);

    @Override
    public boolean retry(ITestResult result) {
        return HAS_RETRIED.compareAndSet(false, true);
    }

    public static boolean hasRetried() {
        return HAS_RETRIED.get();
    }
}
