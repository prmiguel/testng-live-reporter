package io.github.prmiguel.testng.live.core.fixtures;

import org.testng.Assert;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single retried test used to prove that consecutive attempts report increasing numbers.
 */
public class RetriedFixture {

    /**
     * Fails the first invocation and passes the retry.
     */
    public static final class OnceRetryAnalyzer implements IRetryAnalyzer {
        private static final AtomicBoolean RETRIED = new AtomicBoolean(false);

        @Override
        public boolean retry(ITestResult result) {
            return RETRIED.compareAndSet(false, true);
        }
    }

    @Test(retryAnalyzer = OnceRetryAnalyzer.class)
    public void flaky() {
        Assert.assertTrue(OnceRetryAnalyzer.RETRIED.get(), "should be retried once");
    }
}
