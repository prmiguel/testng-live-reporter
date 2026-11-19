package io.github.prmiguel.testng.live.core.fixtures;

import org.testng.Assert;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * One method per {@code @Test} attribute shape the reporter captures.
 */
public class AnnotatedFixture {

    /**
     * Retry analyzer referenced by {@link #richlyAnnotated()}, so the captured name is real.
     */
    public static final class OnceAnalyzer implements IRetryAnalyzer {
        @Override
        public boolean retry(ITestResult result) {
            return false;
        }
    }

    @Test(groups = "base")
    public void baseGroupMember() {
        Assert.assertTrue(true);
    }

    @Test(groups = "slow", dependsOnGroups = "base", description = "covers the annotated fixture",
            priority = 2, timeOut = 500, invocationCount = 3, retryAnalyzer = OnceAnalyzer.class,
            dataProvider = "rows")
    public void richlyAnnotated(int value) {
        Assert.assertTrue(value >= 0);
    }

    @DataProvider(name = "rows")
    public Object[][] rows() {
        return new Object[][]{{1}, {2}, {3}};
    }

    @Test
    public void bare() {
        Assert.assertTrue(true);
    }

    @Test(enabled = false)
    public void disabled() {
        Assert.fail("must never run");
    }
}
